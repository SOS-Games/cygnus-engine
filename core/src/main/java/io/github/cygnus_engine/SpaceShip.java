package io.github.cygnus_engine;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.Array;

// todo:

// I need to separate ship movement from the ship class, I need a movement class. I need a single ship class.

// ships should have a smalltalk dialogue.

// long-term:
// ships should have systems like in FTL, which can take damage in battle and be repaired over time (even in battle)

// unlike FTL, there are no rooms or crew to manage

// ships should have crew
// crew will be lost over time in battle and need replenishment afterwards
// crew are treated almost like "hitpoints"

// the more crew you have, the faster you can repair system damage
// the more crew, the better you can resist hostile boarding, and vice versa
// you can send crew to board hostile ships too.

// ships should have shields in 4-8 directions. small ships have 4, medium have 6, large have 8.
// shields regenerate over time, even in battle.
// shields have layers, and each layer has a certain amount of hitpoints

// once shields are down, the ship takes hull damage, along with random system and crew damage
// some weapons do different hull vs system vs crew damage

// allow redirecting energy to different systems to boost their performance.
// damaged systems have reduced max-energy that you can put into them.
// low-energy systems have low performance

// once ships have taken sufficient system/crew/hull damage, they become "disabled"


public class SpaceShip extends GameObject {

    public enum CombatProfile {
        /** Orbit at weapon standoff; frequent random dodge strafe; hull aims at lead intercept. */
        FIGHTER,
        /** Orbit at weapon standoff; slower dodge; tangential hull drift; turrets and homing fire. */
        FRIGATE
    }

    private GameObject orbitTarget = null;
    private GameObject combatTarget = null;
    private float prevX, prevY = 0f;
    private float currentSpeed = 0f;
    private float strafeSpeed = 0f;
    private float targetForwardSpeed = 0f;
    private float targetStrafeSpeed = 0f;
    private float maxNormalSpeed = 10f;
    private float targetAngle = 0f; // Desired direction in degrees
    private float directionChangeTimer = 0f;
    private float directionChangeInterval = 2f;
    private float maneuverability = 200f; // limits maximum rotationChange per frame (deg/sec when driven from ShipData)
    private CombatProfile combatProfile = CombatProfile.FIGHTER;
    private float orbitCombatRadius = 220f;
    private float orbitCombatBand = 50f;
    private float orbitSign = 1f;
    private float seekCombatTargetTimer = 0f;
    private float seekCombatTargetInterval = 0.5f;
    private float dodgeTimer = 0f;
    private float dodgeDirection = 1f;
    private float dodgeStrafeSpeed = 0f;

    private static final float FIGHTER_STANDOFF_RANGE_FRACTION = 0.78f;
    private static final float FRIGATE_STANDOFF_RANGE_FRACTION = 0.9f;
    private static final float ORBIT_BAND_FRACTION_OF_RADIUS = 0.11f;
    private static final float MIN_ORBIT_BAND = 16f;
    private static final float FIGHTER_DODGE_INTERVAL_MIN = 0.35f;
    private static final float FIGHTER_DODGE_INTERVAL_MAX = 1.05f;
    private static final float FIGHTER_DODGE_STRENGTH = 0.52f;
    private static final float FRIGATE_DODGE_INTERVAL_MIN = 1.8f;
    private static final float FRIGATE_DODGE_INTERVAL_MAX = 3.6f;
    private static final float FRIGATE_DODGE_STRENGTH = 0.26f;
    private static final float MIN_HARDPOINT_HULL_TURN_DEG_PER_SEC = 90f;
    private static final float DEFAULT_WEAPON_RANGE = 260f;
    /** Forward/strafe acceleration in units/s² = fraction × reference speed (max or active target). */
    private static final float ACCELERATION_FRACTION_OF_MAX_SPEED = 0.75f;
    /** During combat, ships may roam up to this multiple of {@link #maxDistanceFromOrbitTarget}. */
    private static final float COMBAT_ORBIT_LEASH_MULTIPLIER = 2f;

    private float combatAimToleranceDegrees = 12f;
    private float velocityX = 0f;
    private float velocityY = 0f;
    private boolean linedUpForShot = false; // todo - use this to fire bullets when I add the projectile system

    private float maxDistanceFromOrbitTarget = 500f;
    private float detectCombatDistance = 200f;
    private float combatFireRange = 260f;
    private float exitWarpWhenCloseToOrbitTargetDistance = 1000f;
    private float enterWarpWhenFarFromOrbitTargetDistance = 2000f;

    // don't manually set these squared values!
    private float maxDistanceFromOrbitTargetSquared;
    private float maxCombatDistanceFromOrbitTargetSquared;
    private float detectCombatDistanceSquared;
    private float combatFireRangeSquared;
    private float exitWarpWhenCloseToOrbitTargetDistanceSquared; // unused due to buggy code
    private float enterWarpWhenFarFromOrbitTargetDistanceSquared;

    private Cargo cargo;

    private Behavior currentBehavior = Behavior.FLYING_AROUND_TARGET;

    private float warpSpeed = 600f; // max warp speed
    private float warpTimer = 0f; // Timer for warp effects
    private boolean isVisible = true;

    private final Array<ShipWeaponInstance> weaponInstances = new Array<>();
    /** Copy of hull {@link ShipData#colliders} for projectile hits. */
    private final Array<ShipColliderCircle> collisionCircles = new Array<>();
    /** Copy of hull {@link ShipData#outerBounds} for mouse picking only. */
    private ShipColliderCircle clickBounds;
    /** Hull-local engine mount points for VFX. */
    private final Array<Vector2> engineLocalPositions = new Array<>();
    private final Affine2 worldTransform = new Affine2();
    private final Vector2 collisionScratch = new Vector2();
    private Sprite hullSprite;

    public enum Behavior {
        FLYING_TO_TARGET,
        FLYING_AROUND_TARGET,
        WARPING_OUT,
        WARPED_OUT,
        WARPING_IN,
    }

    public SpaceShip(float x, float y, float size, String name,
                           GameObject orbitTarget, float configuredMaxSpeed) {
        super(Type.SPACE_SHIP, x, y, size, name);
        this.orbitTarget = orbitTarget;
        this.prevX = x;
        this.prevY = y;
        this.maxNormalSpeed = configuredMaxSpeed;
        this.targetForwardSpeed = maxNormalSpeed;

        this.cargo = new Cargo(true);

        maxDistanceFromOrbitTargetSquared = (float) Math.pow(maxDistanceFromOrbitTarget, 2);
        maxCombatDistanceFromOrbitTargetSquared = maxDistanceFromOrbitTargetSquared
            * (float) Math.pow(COMBAT_ORBIT_LEASH_MULTIPLIER, 2);
        detectCombatDistanceSquared = (float) Math.pow(detectCombatDistance, 2);
        combatFireRangeSquared = (float) Math.pow(combatFireRange, 2);
        exitWarpWhenCloseToOrbitTargetDistanceSquared = (float) Math.pow(exitWarpWhenCloseToOrbitTargetDistance, 2);
        enterWarpWhenFarFromOrbitTargetDistanceSquared = (float) Math.pow(enterWarpWhenFarFromOrbitTargetDistance, 2);

        float currentAngle = MathUtils.random(0f, 360f);
        this.targetAngle = currentAngle;
        setRotation(currentAngle);
        refreshWorldTransformAndMountCaches();
    }
    
    @Override
    public void update(float deltaTime) {
        update(deltaTime, null);
    }

    public void configureWeaponInstances(Array<ShipWeaponInstance> instances) {
        weaponInstances.clear();
        if (instances != null) {
            weaponInstances.addAll(instances);
        }
        recomputeCombatRangesFromWeapons();
        if (hasEquippedHardpoints() && maneuverability < MIN_HARDPOINT_HULL_TURN_DEG_PER_SEC) {
            maneuverability = MIN_HARDPOINT_HULL_TURN_DEG_PER_SEC;
        }
        refreshWorldTransformAndMountCaches();
    }

    public void setHullSprite(Sprite hullSprite) {
        this.hullSprite = hullSprite;
    }

    public Sprite getHullSprite() {
        return hullSprite;
    }

    /** Apply hull AI tuning and combat movement style from mod ship JSON. */
    public void configureFromShipData(ShipData data) {
        if (data == null) {
            return;
        }
        data.normalizeCombatProfile();
        data.normalizeColliders();
        data.normalizeOuterBounds();
        combatProfile = parseCombatProfile(data.combatProfile);

        collisionCircles.clear();
        if (data.colliders != null) {
            for (ShipColliderCircle c : data.colliders) {
                collisionCircles.add(new ShipColliderCircle(c.x, c.y, c.radius));
            }
        }

        if (data.outerBounds != null && data.outerBounds.radius > 0f) {
            clickBounds = data.outerBounds.copy();
        } else {
            clickBounds = null;
        }

        if (data.hullTurnDegPerSec > 0f) {
            maneuverability = data.hullTurnDegPerSec;
        }

        engineLocalPositions.clear();
        if (data.enginePositions != null && !data.enginePositions.isEmpty()) {
            for (Vector2 engine : data.enginePositions) {
                if (engine == null) continue;
                engineLocalPositions.add(new Vector2(engine.x, engine.y));
            }
        }
        if (engineLocalPositions.isEmpty()) {
            engineLocalPositions.add(new Vector2(0f, -10f));
        }

        recomputeCombatRangesFromWeapons();
    }

    /** Standoff orbit radius/band and fire range from equipped weapon projectile travel distance. */
    private void recomputeCombatRangesFromWeapons() {
        float maxWeaponRange = 0f;
        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null) continue;
            float range = w.data.projectileSpeed * w.data.projectileLifetime;
            maxWeaponRange = Math.max(maxWeaponRange, range);
        }
        if (maxWeaponRange <= 0f) {
            maxWeaponRange = DEFAULT_WEAPON_RANGE;
        }

        float standoffFraction = combatProfile == CombatProfile.FRIGATE
            ? FRIGATE_STANDOFF_RANGE_FRACTION
            : FIGHTER_STANDOFF_RANGE_FRACTION;
        orbitCombatRadius = maxWeaponRange * standoffFraction;
        orbitCombatBand = Math.max(orbitCombatRadius * ORBIT_BAND_FRACTION_OF_RADIUS, MIN_ORBIT_BAND);

        combatFireRange = maxWeaponRange * 0.98f;
        combatFireRangeSquared = combatFireRange * combatFireRange;
        detectCombatDistance = Math.max(maxWeaponRange * 1.2f, orbitCombatRadius + orbitCombatBand + 40f);
        detectCombatDistanceSquared = detectCombatDistance * detectCombatDistance;
    }

    /**
     * Sweep test for a projectile capsule along {@code segStart}→{@code segEnd}.
     * Uses {@link #clickBounds} as a broad-phase reject, then tests {@link #collisionCircles}
     * (radii inflated by {@code projectileRadius}).
     */
    public boolean projectileIntersectsHull(Vector2 segStart, Vector2 segEnd, float projectileRadius) {
        refreshWorldTransformAndMountCaches();
        float pr = projectileRadius;

        if (clickBounds != null) {
            collisionScratch.set(clickBounds.x, clickBounds.y);
            worldTransform.applyTo(collisionScratch);
            float outerR = clickBounds.radius + pr;
            if (!Intersector.intersectSegmentCircle(segStart, segEnd, collisionScratch, outerR * outerR)) {
                return false;
            }
        }

        if (collisionCircles.size > 0) {
            for (int i = 0; i < collisionCircles.size; i++) {
                ShipColliderCircle c = collisionCircles.get(i);
                collisionScratch.set(c.x, c.y);
                worldTransform.applyTo(collisionScratch);
                float rr = c.radius + pr;
                if (Intersector.intersectSegmentCircle(segStart, segEnd, collisionScratch, rr * rr)) {
                    return true;
                }
            }
            return false;
        }

        if (clickBounds != null) {
            return false;
        }

        float shipRadius = getSize();
        collisionScratch.set(getX(), getY());
        float rr = shipRadius + pr;
        return Intersector.intersectSegmentCircle(segStart, segEnd, collisionScratch, rr * rr);
    }

    @Override
    public boolean containsPoint(float pointX, float pointY) {
        refreshWorldTransformAndMountCaches();

        if (clickBounds != null) {
            collisionScratch.set(clickBounds.x, clickBounds.y);
            worldTransform.applyTo(collisionScratch);
            float dx = pointX - collisionScratch.x;
            float dy = pointY - collisionScratch.y;
            float r = clickBounds.radius;
            return dx * dx + dy * dy <= (double) r * r;
        }

        return super.containsPoint(pointX, pointY);
    }

    private static CombatProfile parseCombatProfile(String raw) {
        if (raw == null) {
            return CombatProfile.FIGHTER;
        }
        return switch (raw.trim().toUpperCase()) {
            case "FRIGATE" -> CombatProfile.FRIGATE;
            default -> CombatProfile.FIGHTER;
        };
    }

    public CombatProfile getCombatProfile() {
        return combatProfile;
    }

    public GameObject getCombatTarget() {
        return combatTarget;
    }

    public GameObject getOrbitTarget() {
        return orbitTarget;
    }

    public Array<ShipWeaponInstance> getWeaponInstances() {
        return weaponInstances;
    }

    public void update(float deltaTime, ProjectileManager projectileManager) {
        refreshWorldTransformAndMountCaches();

        switch (currentBehavior) {
            case WARPING_OUT:
                warpOut(deltaTime);
                break;
            case WARPED_OUT:
                warpedOut(deltaTime);
                return; // do not update position or rotation, ship should not be moving
            case WARPING_IN:
                warpIn(deltaTime);
                break;
            case FLYING_AROUND_TARGET:
            case FLYING_TO_TARGET:
                //seekCombatTarget(deltaTime);
                if (combatTarget != null) {
                    updateCombatBehavior(deltaTime);
                    updateWeaponAiming(deltaTime, combatTarget);
                    tryFire(projectileManager);
                } else {
                    linedUpForShot = false;
                    targetForwardSpeed = maxNormalSpeed;
                    targetStrafeSpeed = 0f;
                    updateWeaponAiming(deltaTime, null);
                    changeDirectionPeriodically(deltaTime);
                }
        }

        updateRotation(deltaTime);
        accelerateTowardTargets(deltaTime);
        updatePosition(deltaTime);
        refreshWorldTransformAndMountCaches();
    }

    private void updateWeaponAiming(float deltaTime, GameObject target) {
        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null) continue;
            w.fireCooldown = Math.max(0f, w.fireCooldown - deltaTime);

            if (w.slot.type == WeaponSlot.SlotType.TURRET && target != null) {
                float wx = w.worldPosCache.x;
                float wy = w.worldPosCache.y;
                float interceptX = target.getX();
                float interceptY = target.getY();
                if (target instanceof SpaceShip ts) {
                    float ddx = target.getX() - getX();
                    float ddy = target.getY() - getY();
                    float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                    float travel = dist / Math.max(1f, w.data.projectileSpeed);
                    interceptX += ts.getVelocityX() * travel;
                    interceptY += ts.getVelocityY() * travel;
                }
                float desired = CustomMathUtils.getAngleBetweenPoints(wx, wy, interceptX, interceptY);
                w.aimAngleDeg = rotateTowardDeg(w.aimAngleDeg, desired, w.data.turnRateDegPerSec * deltaTime);
            } else {
                w.aimAngleDeg = getRotation();
            }
        }
    }

    private void tryFire(ProjectileManager projectileManager) {
        if (projectileManager == null || combatTarget == null) {
            return;
        }

        float distanceToTargetSquared = getDistanceToSquared(combatTarget);
        if (distanceToTargetSquared > combatFireRangeSquared) {
            return;
        }

        if (weaponInstances.size == 0) {
            return;
        }

        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null || w.fireCooldown > 0f) continue;

            boolean homing = w.data.homing && combatTarget != null;
            float aimTolerance = homing ? 55f : combatAimToleranceDegrees;
            float aimDiff = Math.abs(CustomMathUtils.deltaDeg(w.aimAngleDeg, aimAngleForIntercept(w)));
            if (aimDiff > aimTolerance) continue;

            float cos = MathUtils.cosDeg(w.aimAngleDeg);
            float sin = MathUtils.sinDeg(w.aimAngleDeg);
            float back = w.data.projectileRadius + 2f;
            float spawnX = w.worldPosCache.x + cos * back;
            float spawnY = w.worldPosCache.y + sin * back;

            if (homing) {
                projectileManager.spawn(
                    this,
                    spawnX,
                    spawnY,
                    w.aimAngleDeg,
                    w.data.projectileSpeed,
                    w.data.projectileLifetime,
                    w.data.projectileRadius,
                    combatTarget,
                    w.data.homingTurnRateDegPerSec
                );
            } else {
                projectileManager.spawn(
                    this,
                    spawnX,
                    spawnY,
                    w.aimAngleDeg,
                    w.data.projectileSpeed,
                    w.data.projectileLifetime,
                    w.data.projectileRadius
                );
            }
            w.fireCooldown = w.data.fireInterval;
        }
    }

    private float aimAngleForIntercept(ShipWeaponInstance w) {
        if (combatTarget == null || w.data == null) return getRotation();
        float wx = w.worldPosCache.x;
        float wy = w.worldPosCache.y;
        float ix = combatTarget.getX();
        float iy = combatTarget.getY();
        if (combatTarget instanceof SpaceShip ts) {
            float ddx = combatTarget.getX() - getX();
            float ddy = combatTarget.getY() - getY();
            float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            float travel = dist / Math.max(1f, w.data.projectileSpeed);
            ix += ts.getVelocityX() * travel;
            iy += ts.getVelocityY() * travel;
        }
        return CustomMathUtils.getAngleBetweenPoints(wx, wy, ix, iy);
    }

    /** World position of a mount point for this frame (ship center + rotated slot offset). */
    public void writeMountWorldPosition(WeaponSlot slot, Vector2 out) {
        out.set(slot.x, slot.y);
        worldTransform.applyTo(out);
    }

    public int getEngineCount() {
        return engineLocalPositions.size;
    }

    public void writeEngineWorldPosition(int index, Vector2 out) {
        Vector2 local = engineLocalPositions.get(index);
        out.set(local.x, local.y);
        worldTransform.applyTo(out);
    }

    /** Plume direction: opposite world motion when moving, else hull aft (+Y nose → -Y local). */
    public void writeEngineExhaustDirection(int engineIndex, Vector2 out) {
        float speed = getWorldSpeed();
        if (speed > 2f) {
            out.set(-velocityX / speed, -velocityY / speed);
            return;
        }

        out.set(0f, -1f);
        out.rotate(ShipSpriteOrientation.gameAngleToLocalTransformRotation(getRotation()));
        if (out.len2() > 0.0001f) {
            out.nor();
        }
    }

    public boolean isEngineTrailActive() {
        if (!isVisible || currentBehavior == Behavior.WARPED_OUT) {
            return false;
        }
        if (currentBehavior == Behavior.WARPING_OUT || currentBehavior == Behavior.WARPING_IN) {
            return true;
        }
        return getWorldSpeed() > 2f;
    }

    public float getEngineTrailIntensity() {
        float speedFactor = MathUtils.clamp(getWorldSpeed() / Math.max(maxNormalSpeed, 1f), 0f, 1.25f);
        if (currentBehavior == Behavior.WARPING_OUT || currentBehavior == Behavior.WARPING_IN) {
            speedFactor = Math.max(speedFactor, 1f);
        } else if (isEngineTrailActive()) {
            speedFactor = Math.max(speedFactor, 0.45f);
        }
        return speedFactor;
    }

    public float getWorldSpeed() {
        return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
    }

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public Affine2 getWorldTransform() {
        return worldTransform;
    }

    private void refreshWorldTransformAndMountCaches() {
        worldTransform.setToTrnRotScl(
            getX(),
            getY(),
            ShipSpriteOrientation.gameAngleToLocalTransformRotation(getRotation()),
            1f,
            1f
        );
        for (ShipWeaponInstance weaponInstance : weaponInstances) {
            weaponInstance.updateWorldPosition(worldTransform);
        }
    }

    private static float rotateTowardDeg(float fromDeg, float toDeg, float maxStepDeg) {
        float diff = CustomMathUtils.deltaDeg(fromDeg, toDeg);
        if (Math.abs(diff) <= maxStepDeg) {
            return CustomMathUtils.normalizeAngle360(toDeg);
        }
        return CustomMathUtils.normalizeAngle360(fromDeg + Math.signum(diff) * maxStepDeg);
    }

    private void warpOut(float deltaTime) {
        warpTimer += deltaTime;

        // we should go from maxNormalSpeed to warpSpeed over the course of 2 seconds, then disappear
        float _warpTimer = MathUtils.clamp(warpTimer, 0f, 2f);
        targetForwardSpeed = MathUtils.lerp(maxNormalSpeed, warpSpeed, _warpTimer / 2f);
        targetStrafeSpeed = 0f;
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp out in
        
        // Disappear after getting away from the planet or station
        Boolean farFromOrbitTarget = getDistanceToOrbitTargetSquared() > enterWarpWhenFarFromOrbitTargetDistanceSquared;
        if (farFromOrbitTarget && warpTimer > 2f) {
            //System.out.println("Warp out ... ship disappeared");
            isVisible = false;
            // Schedule warp in after a delay
            warpTimer = 0f;
            currentBehavior = Behavior.WARPED_OUT;
        }
    }

    private void warpedOut(float deltaTime) {
        // ship should not be moving during this time
        warpTimer += deltaTime;
        if (warpTimer > 2f) {
            currentBehavior = Behavior.WARPING_IN;
            warpTimer = 0f;

            // move ship 1000px away from the planet at a random offset position and point it towards the planet
            
            float angleToPlanet = MathUtils.random(0f, 360f);
            setRotation(angleToPlanet);

            float rad = (float) Math.toRadians(angleToPlanet);
            float dx = 1000f * (float) Math.cos(rad);
            float dy = 1000f * (float) Math.sin(rad);

            setX(orbitTarget.getX() - dx);
            setY(orbitTarget.getY() - dy);
        }
    }

    private void warpIn(float deltaTime) {
        //System.out.println("Warp in ... ship reappeared");
        isVisible = true;

        warpTimer += deltaTime;

        // we should go from warpSpeed to maxNormalSpeed over the course of 2 seconds, then switch to normal flying
        float _warpTimer = MathUtils.clamp(warpTimer, 0f, 2f);
        targetForwardSpeed = MathUtils.lerp(warpSpeed, maxNormalSpeed, _warpTimer / 2f);
        targetStrafeSpeed = 0f;
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp in from

        // switch to normal flying if we have slowed down
        if (warpTimer > 2f) {
            warpTimer = 0f;
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
        }
    }

    private void seekCombatTarget(float deltaTime) {
        seekCombatTargetTimer += deltaTime;
        if (seekCombatTargetTimer >= seekCombatTargetInterval) {
            seekCombatTargetTimer = 0f;

            GameObject closestShipWithinRange = getClosestShipWithinRange(detectCombatDistanceSquared);
            if (closestShipWithinRange != combatTarget && closestShipWithinRange != null) {
                orbitSign = MathUtils.randomBoolean() ? 1f : -1f;
            }
            if (isValidCombatTarget(closestShipWithinRange)) {
                combatTarget = closestShipWithinRange;
            } else {
                combatTarget = null;
            }
        }
    }

    private static boolean isValidCombatTarget(GameObject target) {
        if (target == null || !(target instanceof SpaceShip targetShip)) {
            return false;
        }
        if (!GameUtils.isRegisteredShip(targetShip)) {
            return false;
        }
        if (!targetShip.isVisible()) {
            return false;
        }
        return targetShip.getCurrentBehavior() != Behavior.WARPED_OUT;
    }

    private GameObject getClosestShipWithinRange(float range) {
        return GameUtils.getClosestShipWithinRange(range, getX(), getY(), this);
    }

    private void updateCombatBehavior(float deltaTime) {
        if (!isValidCombatTarget(combatTarget)) {
            combatTarget = null;
        }

        if (combatTarget == null) {
            linedUpForShot = false;
            targetStrafeSpeed = 0f;
            targetForwardSpeed = maxNormalSpeed;
            return;
        }

        float distanceToTargetSquared = getDistanceToSquared(combatTarget);
        if (distanceToTargetSquared > detectCombatDistanceSquared) {
            combatTarget = null;
            linedUpForShot = false;
            targetStrafeSpeed = 0f;
            targetForwardSpeed = maxNormalSpeed;
            return;
        }

        recomputeCombatRangesFromWeapons();
        updateOrbitCombatBehavior(deltaTime, distanceToTargetSquared);
    }

    private void updateOrbitCombatBehavior(float deltaTime, float distanceToTargetSquared) {
        float dist = (float) Math.sqrt(distanceToTargetSquared);
        float r = orbitCombatRadius;
        float band = orbitCombatBand;

        float angleToTarget = getAngleToTarget(combatTarget.getX(), combatTarget.getY());
        float tangent = CustomMathUtils.normalizeAngle360(angleToTarget + 90f * orbitSign);
        boolean keepNoseOnTarget = shouldKeepHullOnTarget();

        float orbitForward;
        float orbitStrafe;

        if (dist > r + band) {
            targetAngle = keepNoseOnTarget
                ? computeInterceptAngle(dist)
                : CustomMathUtils.normalizeAngle360(angleToTarget);
            orbitForward = maxNormalSpeed * (combatProfile == CombatProfile.FRIGATE ? 0.85f : 0.92f);
            orbitStrafe = maxNormalSpeed * 0.16f * orbitSign;
        } else if (dist < r - band) {
            targetAngle = keepNoseOnTarget
                ? computeInterceptAngle(dist)
                : CustomMathUtils.normalizeAngle360(angleToTarget + 180f);
            orbitForward = -maxNormalSpeed * (combatProfile == CombatProfile.FRIGATE ? 0.48f : 0.55f);
            orbitStrafe = maxNormalSpeed * 0.38f * orbitSign;
        } else if (keepNoseOnTarget) {
            targetAngle = computeInterceptAngle(dist);
            orbitForward = maxNormalSpeed * 0.1f;
            orbitStrafe = maxNormalSpeed * (combatProfile == CombatProfile.FRIGATE ? 0.55f : 0.68f) * orbitSign;
        } else {
            targetAngle = tangent;
            orbitForward = maxNormalSpeed * 0.1f;
            orbitStrafe = maxNormalSpeed * 0.9f * orbitSign;
        }

        targetForwardSpeed = orbitForward;
        applyRandomDodgeStrafe(deltaTime, orbitStrafe);
        applyCombatOrbitLeash();

        if (keepNoseOnTarget) {
            float angleDiff = Math.abs(CustomMathUtils.angleDifference(targetAngle, getRotation()));
            linedUpForShot = angleDiff <= combatAimToleranceDegrees
                && distanceToTargetSquared <= combatFireRangeSquared;
        } else {
            linedUpForShot = distanceToTargetSquared <= combatFireRangeSquared;
        }
    }

    private boolean hasEquippedHardpoints() {
        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data != null && w.slot.type == WeaponSlot.SlotType.HARDPOINT) {
                return true;
            }
        }
        return false;
    }

    /** Hull must track the target when fixed hardpoints are equipped, or for fighter combat profile. */
    private boolean shouldKeepHullOnTarget() {
        return hasEquippedHardpoints() || combatProfile == CombatProfile.FIGHTER;
    }

    private float computeInterceptAngle(float distToTarget) {
        float leadX = combatTarget.getX();
        float leadY = combatTarget.getY();
        if (combatTarget instanceof SpaceShip targetShip) {
            float bulletSpeed = representativeProjectileSpeed();
            float travel = distToTarget / Math.max(1f, bulletSpeed);
            leadX += targetShip.getVelocityX() * travel;
            leadY += targetShip.getVelocityY() * travel;
        }
        return CustomMathUtils.normalizeAngle360(getAngleToTarget(leadX, leadY));
    }

    /** Random lateral burst layered on orbit strafe; not tied to incoming projectiles. */
    private void applyRandomDodgeStrafe(float deltaTime, float orbitStrafe) {
        dodgeTimer -= deltaTime;
        if (dodgeTimer <= 0f) {
            dodgeDirection = MathUtils.randomBoolean() ? 1f : -1f;
            float strength = combatProfile == CombatProfile.FRIGATE
                ? FRIGATE_DODGE_STRENGTH
                : FIGHTER_DODGE_STRENGTH;
            dodgeStrafeSpeed = maxNormalSpeed * strength * dodgeDirection;
            float minInterval = combatProfile == CombatProfile.FRIGATE
                ? FRIGATE_DODGE_INTERVAL_MIN
                : FIGHTER_DODGE_INTERVAL_MIN;
            float maxInterval = combatProfile == CombatProfile.FRIGATE
                ? FRIGATE_DODGE_INTERVAL_MAX
                : FIGHTER_DODGE_INTERVAL_MAX;
            dodgeTimer = MathUtils.random(minInterval, maxInterval);
        }
        targetStrafeSpeed = orbitStrafe + dodgeStrafeSpeed;
    }

    /** Pull ships back toward their patrol anchor if combat drift exceeds 2× the normal orbit leash. */
    private void applyCombatOrbitLeash() {
        if (orbitTarget == null) {
            return;
        }

        float distSq = getDistanceToOrbitTargetSquared();
        if (distSq <= maxCombatDistanceFromOrbitTargetSquared) {
            return;
        }

        float dist = (float) Math.sqrt(distSq);
        float maxDist = maxDistanceFromOrbitTarget * COMBAT_ORBIT_LEASH_MULTIPLIER;
        float overshootFraction = Math.max(0f, (dist - maxDist) / maxDist);

        targetAngle = CustomMathUtils.normalizeAngle360(
            getAngleToTarget(orbitTarget.getX(), orbitTarget.getY())
        );
        targetForwardSpeed = maxNormalSpeed * MathUtils.clamp(0.55f + overshootFraction * 0.35f, 0.55f, 0.95f);
        targetStrafeSpeed *= 0.2f;
    }

    private void accelerateTowardTargets(float deltaTime) {
        float referenceSpeed = Math.max(
            maxNormalSpeed,
            Math.max(Math.abs(targetForwardSpeed), Math.abs(targetStrafeSpeed))
        );
        if (currentBehavior == Behavior.WARPING_OUT || currentBehavior == Behavior.WARPING_IN) {
            referenceSpeed = Math.max(referenceSpeed, warpSpeed);
        }
        float maxDelta = referenceSpeed * ACCELERATION_FRACTION_OF_MAX_SPEED * deltaTime;
        currentSpeed = moveToward(currentSpeed, targetForwardSpeed, maxDelta);
        strafeSpeed = moveToward(strafeSpeed, targetStrafeSpeed, maxDelta);
    }

    private static float moveToward(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        if (current > target) {
            return Math.max(current - maxDelta, target);
        }
        return current;
    }

    private void changeDirectionPeriodically(float deltaTime) {
        directionChangeTimer += deltaTime;

        Float _angle = getRotation();

        // Change direction periodically
        if (directionChangeTimer >= directionChangeInterval) {
            directionChangeTimer = 0f;
            directionChangeInterval = 2f;
            // Change angle slightly (smooth turns)
            _angle += MathUtils.random(-180f, 180f);
            _angle = CustomMathUtils.normalizeAngle360(_angle);
            targetAngle = _angle;

            adjustCourseIfNeeded();
        }
    }
    
    private void adjustCourseIfNeeded() {
        // Check distance to planet and station, adjust course if too far
        // this overrides the random direction changes, but only if we are too far from both objects

        // todo - this needs a better way to determine if we are too far away
        
        if (getDistanceToOrbitTargetSquared() > maxDistanceFromOrbitTargetSquared) {
            // Too far, turn towards nearest object
            // set target angle directly towards the object
            float angleToClosest = getAngleToTarget(orbitTarget.getX(), orbitTarget.getY());
            
            angleToClosest = CustomMathUtils.normalizeAngle360(angleToClosest);

            //System.out.println("adjusting course for ship " + getName() + " to target angle " + String.format("%.0f", angleToClosest));

            targetAngle = angleToClosest;
        }
    }

    private void updatePosition(float deltaTime) {
        // update previous position before moving. used for optimization, not for movement
        prevX = getX();
        prevY = getY();

        // Move in current direction
        float rad = (float) Math.toRadians(getRotation());
        float forwardX = currentSpeed * (float) Math.cos(rad);
        float forwardY = currentSpeed * (float) Math.sin(rad);

        float strafeRad = rad + MathUtils.HALF_PI;
        float lateralX = strafeSpeed * (float) Math.cos(strafeRad);
        float lateralY = strafeSpeed * (float) Math.sin(strafeRad);

        float moveX = (forwardX + lateralX) * deltaTime;
        float moveY = (forwardY + lateralY) * deltaTime;

        setX(getX() + moveX);
        setY(getY() + moveY);

        if (deltaTime > 0f) {
            velocityX = (getX() - prevX) / deltaTime;
            velocityY = (getY() - prevY) / deltaTime;
        }
    }

    private void updateRotation(float deltaTime) {
        targetAngle = CustomMathUtils.normalizeAngle360(targetAngle);
        
        float currentRotation = getRotation();

        float angleDiff = CustomMathUtils.angleDifference(targetAngle, currentRotation);

        // Calculate the maximum amount we CAN rotate this frame
        float maxRotationThisFrame = maneuverability * deltaTime;

        // Check if we are already close enough to "snap" 
        if (Math.abs(angleDiff) <= maxRotationThisFrame) {
            // This prevents the ship from vibrating back and forth over the target line
            setRotation(targetAngle);
        } else {
            // Otherwise, rotate at FULL speed in the correct direction
            float direction = Math.signum(angleDiff);
            setRotation(currentRotation + (direction * maxRotationThisFrame));
        }
    }
    
    private float getAngleToTarget(float targetX, float targetY) {
        return CustomMathUtils.getAngleBetweenPoints(getX(), getY(), targetX, targetY);
    }

    private float representativeProjectileSpeed() {
        if (weaponInstances.size > 0 && weaponInstances.first().data != null) {
            return weaponInstances.first().data.projectileSpeed;
        }
        return 280f;
    }

    private float getDistanceToSquared(GameObject target) {
        float dx = getX() - target.getX();
        float dy = getY() - target.getY();
        return dx * dx + dy * dy;
    }

    public void triggerWarpOut() {
        if (currentBehavior != Behavior.WARPING_OUT && currentBehavior != Behavior.WARPED_OUT && currentBehavior != Behavior.WARPING_IN) {
            currentBehavior = Behavior.WARPING_OUT;
            warpTimer = 0f;
            //System.out.println("Warping out, maxNormalSpeed , warpSpeed: " + maxNormalSpeed + " , " + warpSpeed);
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }

    public Behavior getCurrentBehavior() {
        return currentBehavior;
    }

    public float getWarpTimer() {
        return warpTimer;
    }
    
    public Cargo getCargo() {
        return cargo;
    }

    public float getVelocityX() {
        return velocityX;
    }

    public float getVelocityY() {
        return velocityY;
    }

    public boolean isLinedUpForShot() {
        return linedUpForShot;
    }

    public float getDirectionChangeTimer() {
        return directionChangeTimer;
    }

    public float getDistanceToOrbitTargetSquared() {
        return getDistanceToSquared(orbitTarget);
    }

    public float getTargetAngle() {
        return targetAngle;
    }

    public float getDetectCombatDistance() {
        return detectCombatDistance;
    }

    /** Multi-line debug snapshot of the current combat target for the info window. */
    public String buildCombatTargetDebugText() {
        GameObject seekPick = getClosestShipWithinRange(detectCombatDistanceSquared);
        StringBuilder sb = new StringBuilder();

        if (combatTarget == null) {
            sb.append("combatTarget: (none)\n");
            sb.append("  id@-\n");
            sb.append("  type: -\n");
            sb.append("  pos: (-, -)\n");
            sb.append("  distance: - / detect ").append(String.format("%.0f", detectCombatDistance)).append('\n');
            sb.append("  visible: -\n");
            sb.append("  behavior: -\n");
            sb.append("  warpTimer: -\n");
            sb.append("  red ring: -\n");
            sb.append("  seek picks: ")
                .append(seekPick == null ? "(none)" : seekPick.getName())
                .append(seekPick == null ? "" : " id@" + System.identityHashCode(seekPick))
                .append('\n');
            sb.append("  ship registry: ").append(GameUtils.getRegisteredShipCount()).append(" active\n");
            sb.append("  target status: -\n");
            return sb.toString();
        }

        float dist = (float) Math.sqrt(getDistanceToSquared(combatTarget));
        sb.append("combatTarget: ").append(combatTarget.getName()).append('\n');
        sb.append("  id@").append(System.identityHashCode(combatTarget)).append('\n');
        sb.append("  type: ").append(combatTarget.getType()).append('\n');
        sb.append("  pos: (")
            .append(String.format("%.1f", combatTarget.getX()))
            .append(", ")
            .append(String.format("%.1f", combatTarget.getY()))
            .append(")\n");
        sb.append("  distance: ")
            .append(String.format("%.0f", dist))
            .append(" / detect ")
            .append(String.format("%.0f", detectCombatDistance))
            .append('\n');

        if (combatTarget instanceof SpaceShip targetShip) {
            sb.append("  visible: ").append(targetShip.isVisible()).append('\n');
            sb.append("  behavior: ").append(targetShip.getCurrentBehavior()).append('\n');
            sb.append("  warpTimer: ").append(String.format("%.2f", targetShip.getWarpTimer())).append('\n');
            sb.append("  red ring: ").append(targetShip.isVisible() ? "yes" : "hidden").append('\n');
        } else {
            sb.append("  visible: n/a\n");
            sb.append("  behavior: n/a\n");
            sb.append("  warpTimer: n/a\n");
            sb.append("  red ring: n/a\n");
        }

        sb.append("  seek picks: ")
            .append(seekPick == null ? "(none)" : seekPick.getName())
            .append(seekPick == null ? "" : " id@" + System.identityHashCode(seekPick))
            .append('\n');
        sb.append("  ship registry: ").append(GameUtils.getRegisteredShipCount()).append(" active\n");
        sb.append("  target status: ").append(formatCombatTargetStatus(seekPick)).append('\n');

        return sb.toString();
    }

    private String formatCombatTargetStatus(GameObject seekPick) {
        if (combatTarget == null) {
            return "-";
        }

        StringBuilder status = new StringBuilder();
        if (combatTarget instanceof SpaceShip targetShip && !GameUtils.isRegisteredShip(targetShip)) {
            status.append("phantom");
        }
        if (combatTarget != seekPick) {
            if (status.length() > 0) {
                status.append(", ");
            }
            status.append("stale seek");
        }
        if (combatTarget instanceof SpaceShip targetShip && !targetShip.isVisible()) {
            if (status.length() > 0) {
                status.append(", ");
            }
            status.append("invisible");
        }
        return status.length() == 0 ? "ok" : status.toString();
    }
}
