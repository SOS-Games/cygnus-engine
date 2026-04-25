package io.github.cygnus_engine;

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
        /** Point hull at target, strafe dodge, kinetic aim. */
        FIGHTER,
        /** Orbit at standoff radius; no dodge strafe; hull tangential drift; turrets and homing do damage. */
        FRIGATE
    }

    private GameObject orbitTarget = null;
    private GameObject combatTarget = null;
    private float prevX, prevY = 0f;
    private float currentSpeed = 0f;
    private float strafeSpeed = 0f;
    private float maxNormalSpeed = 10f;
    private float targetAngle = 0f; // Desired direction in degrees
    private float directionChangeTimer = 0f;
    private float directionChangeInterval = 2f;
    private float maneuverability = 200f; // limits maximum rotationChange per frame (deg/sec when driven from ShipData)
    private CombatProfile combatProfile = CombatProfile.FIGHTER;
    private float orbitCombatRadius = 220f;
    private float orbitCombatBand = 50f;
    private float frigateOrbitSign = 1f;
    private float seekCombatTargetTimer = 0f;
    private float seekCombatTargetInterval = 0.5f;
    private float dodgeTimer = 0f;
    private float dodgeDirection = 1f;
    private float dodgeInterval = 1.5f;

    private float combatAimToleranceDegrees = 12f;
    private float velocityX = 0f;
    private float velocityY = 0f;
    private boolean linedUpForShot = false; // todo - use this to fire bullets when I add the projectile system

    private float maxDistanceFromOrbitTarget = 100f;
    private float detectCombatDistance = 200f;
    private float combatMinDistance = 100f;
    private float combatMaxDistance = 300f;
    private float combatFireRange = 260f;
    private float exitWarpWhenCloseToOrbitTargetDistance = 1000f;
    private float enterWarpWhenFarFromOrbitTargetDistance = 2000f;

    // don't manually set these squared values!
    private float maxDistanceFromOrbitTargetSquared;
    private float detectCombatDistanceSquared;
    private float combatMinDistanceSquared;
    private float combatMaxDistanceSquared;
    private float combatFireRangeSquared;
    private float exitWarpWhenCloseToOrbitTargetDistanceSquared;
    private float enterWarpWhenFarFromOrbitTargetDistanceSquared;

    private Cargo cargo;

    private Behavior currentBehavior = Behavior.FLYING_AROUND_TARGET;

    private float warpSpeed = 600f; // max warp speed
    private float warpTimer = 0f; // Timer for warp effects
    private boolean isVisible = true;

    private final Array<ShipWeaponInstance> weaponInstances = new Array<>();
    private final Affine2 worldTransform = new Affine2();
    private float legacyFireCooldown = 0f;
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
        this.currentSpeed = maxNormalSpeed;

        this.cargo = new Cargo(true);

        maxDistanceFromOrbitTargetSquared = (float) Math.pow(maxDistanceFromOrbitTarget, 2);
        detectCombatDistanceSquared = (float) Math.pow(detectCombatDistance, 2);
        combatMinDistanceSquared = (float) Math.pow(combatMinDistance, 2);
        combatMaxDistanceSquared = (float) Math.pow(combatMaxDistance, 2);
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
        combatProfile = parseCombatProfile(data.combatProfile);

        // todo - these should be computed at runtime, not read from the json
        orbitCombatRadius = data.orbitCombatRadius;
        orbitCombatBand = data.orbitCombatBand;
        if (data.hullTurnDegPerSec > 0f) {
            maneuverability = data.hullTurnDegPerSec;
        }
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

    public Array<ShipWeaponInstance> getWeaponInstances() {
        return weaponInstances;
    }

    public void update(float deltaTime, ProjectileManager projectileManager) {
        legacyFireCooldown = Math.max(0f, legacyFireCooldown - deltaTime);
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
                seekCombatTarget(deltaTime);
                if (combatTarget != null) {
                    updateCombatBehavior(deltaTime);
                    updateWeaponAiming(deltaTime, combatTarget);
                    tryFire(projectileManager);
                } else {
                    linedUpForShot = false;
                    currentSpeed = maxNormalSpeed;
                    strafeSpeed = 0f;
                    updateWeaponAiming(deltaTime, null);
                    changeDirectionPeriodically(deltaTime);
                }
        }

        updateRotation(deltaTime);
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
            if (!linedUpForShot || legacyFireCooldown > 0f) {
                return;
            }
            tryFireLegacyForward(projectileManager);
            legacyFireCooldown = 0.35f;
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

    /** Fallback when a ship has no configured mounts (e.g. legacy data). */
    private void tryFireLegacyForward(ProjectileManager projectileManager) {
        float bulletSpeed = 280f;
        float projectileLifetime = 2f;
        float projectileRadius = 2.5f;
        float rad = (float) Math.toRadians(getRotation());
        float muzzleOffset = getSize() + projectileRadius + 2f;
        float spawnX = getX() + (float) Math.cos(rad) * muzzleOffset;
        float spawnY = getY() + (float) Math.sin(rad) * muzzleOffset;
        projectileManager.spawn(
            this,
            spawnX,
            spawnY,
            getRotation(),
            bulletSpeed,
            projectileLifetime,
            projectileRadius
        );
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

    public Affine2 getWorldTransform() {
        return worldTransform;
    }

    private void refreshWorldTransformAndMountCaches() {
        worldTransform.setToTrnRotScl(getX(), getY(), getRotation(), 1f, 1f);
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
        currentSpeed = MathUtils.lerp(maxNormalSpeed, warpSpeed, _warpTimer / 2f);
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp out in
        
        // Disappear after getting away from the planet or station
        Boolean farFromOrbitTarget = getDistanceToSquared(orbitTarget) > enterWarpWhenFarFromOrbitTargetDistanceSquared;
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
        currentSpeed = MathUtils.lerp(warpSpeed, maxNormalSpeed, _warpTimer / 2f);
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp in from
        //adjustCourseIfNeeded();

        // switch to normal flying if we are close to the planet or station
        Boolean closeToOrbitTarget = getDistanceToSquared(orbitTarget) < exitWarpWhenCloseToOrbitTargetDistanceSquared;
        if (closeToOrbitTarget && warpTimer > 2f) {
            warpTimer = 0f;
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
        }
    }

    private void seekCombatTarget(float deltaTime) {
        seekCombatTargetTimer += deltaTime;
        if (seekCombatTargetTimer >= seekCombatTargetInterval) {
            seekCombatTargetTimer = 0f;

            GameObject closestShipWithinRange = getClosestShipWithinRange(detectCombatDistanceSquared);
            if (closestShipWithinRange != combatTarget && closestShipWithinRange != null && combatProfile == CombatProfile.FRIGATE) {
                frigateOrbitSign = MathUtils.randomBoolean() ? 1f : -1f;
            }
            combatTarget = closestShipWithinRange;
        }
    }

    private GameObject getClosestShipWithinRange(float range) {
        return GameUtils.getClosestShipWithinRange(range, getX(), getY(), this);
    }

    private void updateCombatBehavior(float deltaTime) {
        if (combatTarget == null) {
            linedUpForShot = false;
            strafeSpeed = 0f;
            return;
        }

        float distanceToTargetSquared = getDistanceToSquared(combatTarget);
        if (distanceToTargetSquared > detectCombatDistanceSquared) {
            combatTarget = null;
            linedUpForShot = false;
            strafeSpeed = 0f;
            return;
        }

        if (combatProfile == CombatProfile.FRIGATE) {
            updateFrigateCombatBehavior(distanceToTargetSquared);
            return;
        }

        float bulletInterceptX = combatTarget.getX();
        float bulletInterceptY = combatTarget.getY();

        if (combatTarget instanceof SpaceShip) {
            SpaceShip targetShip = (SpaceShip) combatTarget;

            float distanceToTarget = (float) Math.sqrt(distanceToTargetSquared);
            float bulletSpeed = representativeProjectileSpeed();
            float bulletTravelTime = distanceToTarget / bulletSpeed;

            bulletInterceptX += targetShip.getVelocityX() * bulletTravelTime;
            bulletInterceptY += targetShip.getVelocityY() * bulletTravelTime;
        }

        float desiredAngle = getAngleToObject(bulletInterceptX, bulletInterceptY);
        targetAngle = CustomMathUtils.normalizeAngle360(desiredAngle);

        float angleDiff = Math.abs(CustomMathUtils.angleDifference(targetAngle, getRotation()));
        linedUpForShot = angleDiff <= combatAimToleranceDegrees && distanceToTargetSquared <= combatFireRangeSquared;

        if (distanceToTargetSquared > combatMaxDistanceSquared) {
            currentSpeed = maxNormalSpeed;
        } else if (distanceToTargetSquared < combatMinDistanceSquared) {
            currentSpeed = -maxNormalSpeed * 0.4f;
        } else {
            // Keep slight forward drift so ships do not "freeze" at ideal range.
            currentSpeed = maxNormalSpeed * 0.005f;
        }

        dodgeTimer -= deltaTime;
        if (dodgeTimer <= 0f) {
            dodgeDirection = MathUtils.randomBoolean() ? 1f : -1f;
            float dodgeStrength = 0.4f;
            strafeSpeed = maxNormalSpeed * dodgeStrength * dodgeDirection;
            dodgeTimer = MathUtils.random(0.4f, dodgeInterval);
        }
    }

    private void updateFrigateCombatBehavior(float distanceToTargetSquared) {
        linedUpForShot = false;
        float dist = (float) Math.sqrt(distanceToTargetSquared);
        float r = orbitCombatRadius;
        float band = orbitCombatBand;

        float angleToTarget = getAngleToObject(combatTarget.getX(), combatTarget.getY());
        float tangent = CustomMathUtils.normalizeAngle360(angleToTarget + 90f * frigateOrbitSign);

        if (dist > r + band) {
            targetAngle = CustomMathUtils.normalizeAngle360(angleToTarget);
            currentSpeed = maxNormalSpeed * 0.85f;
            strafeSpeed = maxNormalSpeed * 0.18f * frigateOrbitSign;
        } else if (dist < r - band) {
            targetAngle = CustomMathUtils.normalizeAngle360(angleToTarget + 180f);
            currentSpeed = -maxNormalSpeed * 0.48f;
            strafeSpeed = maxNormalSpeed * 0.42f * frigateOrbitSign;
        } else {
            targetAngle = tangent;
            currentSpeed = maxNormalSpeed * 0.1f;
            strafeSpeed = maxNormalSpeed * 0.92f * frigateOrbitSign;
        }
    }

    private void changeDirectionPeriodically(float deltaTime) {
        directionChangeTimer += deltaTime;

        Float _angle = getRotation();

        // Change direction periodically
        if (directionChangeTimer >= directionChangeInterval) {
            directionChangeTimer = 0f;
            directionChangeInterval = 2f;
            // Change angle slightly (smooth turns)
            _angle += MathUtils.random(-45f, 45f);
            _angle = CustomMathUtils.normalizeAngle360(_angle);
            targetAngle = _angle;

            adjustCourseIfNeeded();
        }
    }
    
    private void adjustCourseIfNeeded() {
        // Check distance to planet and station, adjust course if too far
        // this overrides the random direction changes, but only if we are too far from both objects

        // todo - this needs a better way to determine if we are too far away
        
        if (getDistanceToSquared(orbitTarget) > maxDistanceFromOrbitTargetSquared) {
            // Too far, turn towards nearest object
            // set target angle directly towards the object
            float angleToClosest = getAngleToObject(orbitTarget.getX(), orbitTarget.getY());
            
            angleToClosest = CustomMathUtils.normalizeAngle360(angleToClosest);

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
        
        // TODO: ships never accelerate - they always travel at max speed!
        //lastMovementSpeed = currentSpeed;
        // I could just keep track of the last "currentSpeed" and use it for the optimization check
        // instead of doing a distance check in updateRotation()
        // if (lastMovementSpeed > 0.1f) {do movement ...}

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
    
    private float getAngleToObject(float objectX, float objectY) {
        return CustomMathUtils.getAngleBetweenPoints(objectX, objectY, getX(), getY());
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
}
