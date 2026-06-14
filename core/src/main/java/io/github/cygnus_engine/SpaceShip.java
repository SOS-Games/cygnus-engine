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
    private static final float COMBAT_ORBIT_LEASH_MULTIPLIER = 3f;

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

    private float warpSpeed = 1200f; // max warp speed
    private float warpTimer = 0f; // Timer for warp effects
    private static final float WARP_OUT_RAMP_SECONDS = 2f;
    private static final float WARP_OUT_CRUISE_SECONDS = 2f;
    private static final float TRADER_ARRIVAL_PADDING = 20f;
    private static final float TRADER_ROUTE_SPEED_FRACTION = 0.9f;
    private static final float DEFAULT_BERTH_SECONDS = 4f;
    private static final float BERTH_APPROACH_SPEED_FRACTION = 0.9f;
    private static final float BERTH_SLOW_ZONE_RADIUS = 55f;
    private static final float BERTH_AUTO_DOCK_RADIUS = 42f;
    private static final float BERTH_AUTO_DOCK_PULL_GAIN = 2.4f;
    private static final float BERTH_AUTO_DOCK_MIN_SPEED = 8f;
    private static final float BERTH_AUTO_DOCK_MAX_SPEED = 48f;
    private static final float BERTH_AUTO_DOCK_ROTATION_MULTIPLIER = 1.5f;
    private static final float BERTH_ARRIVED_DISTANCE = 2.5f;
    private static final float BERTH_FINAL_SPEED_FRACTION = 0.15f;
    private static final float BERTH_ALIGN_TOLERANCE_DEG = 10f;
    private static final float MILITIA_RELOCATE_MIN_SECONDS = 10f;
    private static final float MILITIA_RELOCATE_MAX_SECONDS = 30f;
    private static final float MILITIA_TRANSIT_SPEED_FRACTION = 0.85f;
    private static final float MILITIA_ARRIVAL_PADDING = 25f;
    private boolean isVisible = true;

    private boolean trader = false;
    private boolean militiaPatrol = false;
    private boolean pirate = false;
    private boolean civilian = false;
    private boolean playerControlled = false;
    private float playerForwardInput = 0f;
    private float playerStrafeInput = 0f;
    private float playerAimWorldX = 0f;
    private float playerAimWorldY = 0f;
    private boolean playerFireHeld = false;
    private GameObject playerTarget = null;
    private final Array<GameObject> tradeRoute = new Array<>();
    private final Array<GameObject> patrolAnchors = new Array<>();
    private GameObject tradeDestination;
    private GameObject berthHost;
    private StationBerth.Cardinal berthSide;
    private final Vector2 berthPoint = new Vector2();
    private float berthTimer = 0f;
    private float berthDurationSeconds = DEFAULT_BERTH_SECONDS;
    private float berthApproachSpeedFraction = BERTH_APPROACH_SPEED_FRACTION;
    private Runnable berthCompleteCallback;
    private float patrolRelocateTimer = 0f;
    private float patrolRelocateInterval = 45f;

    private final Array<ShipWeaponInstance> weaponInstances = new Array<>();
    /** Copy of hull {@link ShipData#colliders} for projectile hits. */
    private final Array<ShipColliderCircle> collisionCircles = new Array<>();
    /** Copy of hull {@link ShipData#outerBounds} for mouse picking only. */
    private ShipColliderCircle clickBounds;
    /** Hull-local engine mount points for VFX. */
    private final Array<Vector2> engineLocalPositions = new Array<>();
    private final Affine2 worldTransform = new Affine2();
    private final Vector2 collisionScratch = new Vector2();
    private final Vector2 aimScratch = new Vector2();
    private Sprite hullSprite;

    public enum Behavior {
        FLYING_TO_TARGET,
        FLYING_AROUND_TARGET,
        TRADER_TRANSIT,
        DOCKING_APPROACH,
        DOCKING_AUTO,
        DOCKED,
        MILITIA_TRANSIT,
        PLAYER_CONTROLLED,
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

    /** Traders cycle all stops and dock at stations. */
    public void configureAsTrader(Array<GameObject> stops) {
        trader = true;
        militiaPatrol = false;
        pirate = false;
        civilian = false;
        playerControlled = false;
        tradeRoute.clear();
        if (stops != null) {
            for (GameObject stop : stops) {
                if (stop != null && stop.isInteractable()) {
                    tradeRoute.add(stop);
                }
            }
        }
        combatTarget = null;
        pickNextTradeDestination();
        if (!isBerthing()) {
            currentBehavior = Behavior.TRADER_TRANSIT;
        }
    }

    /** Militia patrol one anchor at a time and occasionally relocate to another body. */
    public void configureAsMilitiaPatrol(Array<GameObject> anchors) {
        trader = false;
        militiaPatrol = true;
        pirate = false;
        civilian = false;
        playerControlled = false;
        patrolAnchors.clear();
        if (anchors != null) {
            for (GameObject anchor : anchors) {
                if (anchor != null && anchor.isInteractable()) {
                    patrolAnchors.add(anchor);
                }
            }
        }
        combatTarget = null;
        resetPatrolRelocateTimer();
        currentBehavior = Behavior.FLYING_AROUND_TARGET;
    }

    /** Pirates arrive via warp-in only; they patrol locally after decelerating. */
    public void configureAsPirate(GameObject anchor) {
        trader = false;
        militiaPatrol = false;
        pirate = true;
        civilian = false;
        playerControlled = false;
        if (anchor != null) {
            orbitTarget = anchor;
        }
        combatTarget = null;
    }

    /** Civilians orbit one anchor locally and engage pirates that enter range. */
    public void configureAsCivilian(GameObject anchor) {
        trader = false;
        militiaPatrol = false;
        pirate = false;
        civilian = true;
        playerControlled = false;
        if (anchor != null) {
            orbitTarget = anchor;
        }
        combatTarget = null;
        currentBehavior = Behavior.FLYING_AROUND_TARGET;
    }

    public void configureAsPlayer(GameObject anchor) {
        trader = false;
        militiaPatrol = false;
        pirate = false;
        civilian = false;
        playerControlled = true;
        if (anchor != null) {
            orbitTarget = anchor;
        }
        combatTarget = null;
        currentBehavior = Behavior.PLAYER_CONTROLLED;
    }

    public boolean isPlayerControlled() {
        return playerControlled;
    }

    /** Hull aim (deg), normalized forward/strafe axes in [-1, 1], and mouse world position for turrets. */
    public void applyPlayerInput(
        float aimAngleDeg,
        float forwardAxis,
        float strafeAxis,
        float aimWorldX,
        float aimWorldY
    ) {
        if (!playerControlled) {
            return;
        }
        targetAngle = CustomMathUtils.normalizeAngle360(aimAngleDeg);
        playerForwardInput = MathUtils.clamp(forwardAxis, -1f, 1f);
        playerStrafeInput = MathUtils.clamp(strafeAxis, -1f, 1f);
        playerAimWorldX = aimWorldX;
        playerAimWorldY = aimWorldY;
    }

    public void setPlayerFireHeld(boolean fireHeld) {
        if (playerControlled) {
            playerFireHeld = fireHeld;
        }
    }

    /** Hover-selected target for player UI and homing weapons (not AI {@link #combatTarget}). */
    public void setPlayerTarget(GameObject target) {
        if (!playerControlled) {
            return;
        }
        if (target == this) {
            playerTarget = null;
            return;
        }
        if (target != null && !target.isInteractable()) {
            playerTarget = null;
            return;
        }
        playerTarget = target;
    }

    public GameObject getPlayerTarget() {
        return playerTarget;
    }

    /** Drop the lock when the target is destroyed, warps out, or otherwise gone. */
    public void validatePlayerTarget() {
        if (playerTarget == null || playerTarget == this) {
            playerTarget = null;
            return;
        }
        if (playerTarget instanceof SpaceShip targetShip) {
            if (!targetShip.isVisible() || !GameUtils.isRegisteredShip(targetShip)) {
                playerTarget = null;
            }
        } else if (!playerTarget.isInteractable()) {
            playerTarget = null;
        }
    }

    public boolean isTrader() {
        return trader;
    }

    public boolean isMilitiaPatrol() {
        return militiaPatrol;
    }

    public boolean isPirate() {
        return pirate;
    }

    public boolean isCivilian() {
        return civilian;
    }

    /** Place off-screen relative to {@code anchor} and begin the warp-in deceleration pass. */
    public void beginWarpInNear(GameObject anchor) {
        beginWarpInNear(anchor, 1000f);
    }

    public void beginWarpInNear(GameObject anchor, float distance) {
        if (anchor != null) {
            orbitTarget = anchor;
        }
        if (orbitTarget == null) {
            return;
        }

        float angleDeg = MathUtils.random(0f, 360f);
        setRotation(angleDeg);
        targetAngle = angleDeg;

        float rad = (float) Math.toRadians(angleDeg);
        float dx = distance * (float) Math.cos(rad);
        float dy = distance * (float) Math.sin(rad);
        setX(orbitTarget.getX() - dx);
        setY(orbitTarget.getY() - dy);

        currentSpeed = warpSpeed;
        targetForwardSpeed = warpSpeed;
        targetStrafeSpeed = 0f;
        isVisible = true;
        warpTimer = 0f;
        currentBehavior = Behavior.WARPING_IN;
        refreshWorldTransformAndMountCaches();
    }

    public GameObject getTradeDestination() {
        return tradeDestination;
    }

    /** Begin approach to a cardinal berth on a station; {@code onComplete} runs after the stay duration. */
    public void beginBerthAt(GameObject station, float staySeconds, Runnable onComplete) {
        beginBerthAt(station, staySeconds, BERTH_APPROACH_SPEED_FRACTION, onComplete);
    }

    public void beginBerthAt(
        GameObject station,
        float staySeconds,
        float approachSpeedFraction,
        Runnable onComplete
    ) {
        if (!StationBerth.canBerthAt(station)) {
            return;
        }

        releaseBerth();
        berthHost = station;
        berthDurationSeconds = staySeconds;
        berthApproachSpeedFraction = approachSpeedFraction;
        berthCompleteCallback = onComplete;
        berthSide = StationBerth.assignBerth(station, this);
        if (berthSide == null) {
            berthHost = null;
            berthCompleteCallback = null;
            return;
        }
        StationBerth.writeBerthWorldPosition(station, berthSide, getSize(), berthPoint);
        berthTimer = 0f;
        currentBehavior = Behavior.DOCKING_APPROACH;
    }

    public void releaseBerth() {
        if (berthHost != null) {
            StationBerth.releaseShip(berthHost, this);
        }
        berthHost = null;
        berthSide = null;
        berthCompleteCallback = null;
    }

    public boolean isBerthingAt(GameObject station) {
        return station != null && station == berthHost && isBerthing();
    }

    public GameObject getBerthHost() {
        return berthHost;
    }

    public boolean isBerthing() {
        return currentBehavior == Behavior.DOCKING_APPROACH
            || currentBehavior == Behavior.DOCKING_AUTO
            || currentBehavior == Behavior.DOCKED;
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

    /** Hull-local pick radius from {@link #clickBounds}, or {@link #getSize()} when unset. */
    public float getClickBoundsRadius() {
        if (clickBounds != null && clickBounds.radius > 0f) {
            return clickBounds.radius;
        }
        return getSize();
    }

    /** Transforms {@link #clickBounds} center to world space. Returns false when using ship center fallback. */
    public boolean writeClickBoundsWorldCenter(Vector2 out) {
        refreshWorldTransformAndMountCaches();
        if (clickBounds != null && clickBounds.radius > 0f) {
            out.set(clickBounds.x, clickBounds.y);
            worldTransform.applyTo(out);
            return true;
        }
        out.set(getX(), getY());
        return false;
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
            case TRADER_TRANSIT:
                updateTraderTransit();
                updateWeaponAiming(deltaTime, null);
                break;
            case DOCKING_APPROACH:
                updateBerthApproach();
                updateWeaponAiming(deltaTime, null);
                break;
            case DOCKING_AUTO:
                updateAutoDock(deltaTime);
                updateWeaponAiming(deltaTime, null);
                break;
            case DOCKED:
                updateBerthed(deltaTime);
                updateWeaponAiming(deltaTime, null);
                break;
            case MILITIA_TRANSIT:
                updateMilitiaTransit();
                updatePatrolOrCombat(deltaTime, projectileManager, null);
                break;
            case PLAYER_CONTROLLED:
                updatePlayerControl(deltaTime, projectileManager);
                break;
            case FLYING_AROUND_TARGET:
            case FLYING_TO_TARGET:
                updatePatrolOrCombat(deltaTime, projectileManager, () -> {
                    changeDirectionPeriodically(deltaTime);
                    if (militiaPatrol) {
                        updateMilitiaPatrol(deltaTime);
                    }
                });
                break;
        }

        if (currentBehavior != Behavior.DOCKING_AUTO) {
            updateRotation(deltaTime);
            accelerateTowardTargets(deltaTime);
            updatePosition(deltaTime);
        }
        refreshWorldTransformAndMountCaches();
    }

    private void updatePlayerControl(float deltaTime, ProjectileManager projectileManager) {
        targetForwardSpeed = maxNormalSpeed * playerForwardInput;
        targetStrafeSpeed = maxNormalSpeed * playerStrafeInput;
        updatePlayerWeaponAiming(deltaTime);
        if (playerFireHeld) {
            tryFireAtAimPoint(projectileManager);
        }
    }

    private void updatePlayerWeaponAiming(float deltaTime) {
        boolean homingTargetActive = isValidPlayerHomingTarget(playerTarget);

        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null) {
                continue;
            }
            w.fireCooldown = Math.max(0f, w.fireCooldown - deltaTime);

            float aimWorldX = playerAimWorldX;
            float aimWorldY = playerAimWorldY;
            if (w.data.homing && homingTargetActive) {
                writeLeadInterceptPoint(playerTarget, w, aimScratch);
                aimWorldX = aimScratch.x;
                aimWorldY = aimScratch.y;
            }

            if (w.slot.type == WeaponSlot.SlotType.TURRET) {
                float wx = w.worldPosCache.x;
                float wy = w.worldPosCache.y;
                float desired = CustomMathUtils.getAngleBetweenPoints(wx, wy, aimWorldX, aimWorldY);
                w.aimAngleDeg = rotateTowardDeg(w.aimAngleDeg, desired, w.data.turnRateDegPerSec * deltaTime);
            } else {
                w.aimAngleDeg = getRotation();
            }
        }
    }

    private boolean isValidPlayerHomingTarget(GameObject target) {
        if (target == null || !(target instanceof SpaceShip targetShip)) {
            return false;
        }
        if (targetShip == this || !targetShip.isVisible()) {
            return false;
        }
        return GameUtils.isRegisteredShip(targetShip);
    }

    private void writeLeadInterceptPoint(GameObject target, ShipWeaponInstance w, Vector2 out) {
        out.set(target.getX(), target.getY());
        if (target instanceof SpaceShip targetShip && w.data != null) {
            float ddx = target.getX() - getX();
            float ddy = target.getY() - getY();
            float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            float travel = dist / Math.max(1f, w.data.projectileSpeed);
            out.x += targetShip.getVelocityX() * travel;
            out.y += targetShip.getVelocityY() * travel;
        }
    }

    private float weaponAimAngleToward(ShipWeaponInstance w, float worldX, float worldY) {
        return CustomMathUtils.getAngleBetweenPoints(
            w.worldPosCache.x,
            w.worldPosCache.y,
            worldX,
            worldY
        );
    }

    private void updateWeaponAiming(float deltaTime, GameObject target) {
        if (target == null) {
            for (ShipWeaponInstance w : weaponInstances) {
                if (w.data == null) continue;
                w.fireCooldown = Math.max(0f, w.fireCooldown - deltaTime);
                w.aimAngleDeg = getRotation();
            }
            return;
        }

        float interceptX = target.getX();
        float interceptY = target.getY();
        if (target instanceof SpaceShip ts) {
            float ddx = target.getX() - getX();
            float ddy = target.getY() - getY();
            float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            float travel = dist / Math.max(1f, representativeProjectileSpeed());
            interceptX += ts.getVelocityX() * travel;
            interceptY += ts.getVelocityY() * travel;
        }
        updateWeaponAimingAtWorldPoint(deltaTime, interceptX, interceptY);
    }

    private void updateWeaponAimingAtWorldPoint(float deltaTime, float worldX, float worldY) {
        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null) continue;
            w.fireCooldown = Math.max(0f, w.fireCooldown - deltaTime);

            if (w.slot.type == WeaponSlot.SlotType.TURRET) {
                float wx = w.worldPosCache.x;
                float wy = w.worldPosCache.y;
                float desired = CustomMathUtils.getAngleBetweenPoints(wx, wy, worldX, worldY);
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

            spawnWeaponProjectile(projectileManager, w, homing ? combatTarget : null);
            w.fireCooldown = w.data.fireInterval;
        }
    }

    private void tryFireAtAimPoint(ProjectileManager projectileManager) {
        if (projectileManager == null || weaponInstances.size == 0) {
            return;
        }

        boolean homingTargetActive = isValidPlayerHomingTarget(playerTarget);

        for (ShipWeaponInstance w : weaponInstances) {
            if (w.data == null || w.fireCooldown > 0f) {
                continue;
            }

            GameObject homingTarget = w.data.homing && homingTargetActive ? playerTarget : null;
            float aimWorldX = playerAimWorldX;
            float aimWorldY = playerAimWorldY;
            if (homingTarget != null) {
                writeLeadInterceptPoint(homingTarget, w, aimScratch);
                aimWorldX = aimScratch.x;
                aimWorldY = aimScratch.y;
            }

            float aimTolerance = w.data.homing && homingTarget != null ? 55f : combatAimToleranceDegrees;
            float aimDiff = Math.abs(CustomMathUtils.deltaDeg(
                w.aimAngleDeg,
                weaponAimAngleToward(w, aimWorldX, aimWorldY)
            ));
            if (aimDiff > aimTolerance) {
                continue;
            }

            spawnWeaponProjectile(projectileManager, w, homingTarget);
            w.fireCooldown = w.data.fireInterval;
        }
    }

    private void spawnWeaponProjectile(
        ProjectileManager projectileManager,
        ShipWeaponInstance w,
        GameObject homingTarget
    ) {
        float cos = MathUtils.cosDeg(w.aimAngleDeg);
        float sin = MathUtils.sinDeg(w.aimAngleDeg);
        float back = w.data.projectileRadius + 2f;
        float spawnX = w.worldPosCache.x + cos * back;
        float spawnY = w.worldPosCache.y + sin * back;

        if (w.data.homing && homingTarget != null) {
            projectileManager.spawn(
                this,
                spawnX,
                spawnY,
                w.aimAngleDeg,
                w.data.projectileSpeed,
                w.data.projectileLifetime,
                w.data.projectileRadius,
                homingTarget,
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

    private void updateTraderTransit() {
        if (tradeDestination == null) {
            pickNextTradeDestination();
            if (tradeDestination == null) {
                return;
            }
        }

        if (tradeDestination.getType() == GameObject.Type.SPACE_STATION) {
            if (!isBerthing()) {
                beginBerthAt(tradeDestination, DEFAULT_BERTH_SECONDS, this::pickNextTradeDestination);
            }
            return;
        }

        targetAngle = getAngleToTarget(tradeDestination.getX(), tradeDestination.getY());
        targetForwardSpeed = maxNormalSpeed * TRADER_ROUTE_SPEED_FRACTION;
        targetStrafeSpeed = 0f;

        float arriveDist = tradeDestination.getSize() + getSize() + TRADER_ARRIVAL_PADDING;
        if (getDistanceToSquared(tradeDestination) <= arriveDist * arriveDist) {
            pickNextTradeDestination();
        }
    }

    private void updateBerthApproach() {
        if (!StationBerth.canBerthAt(berthHost)) {
            endBerthAndResume();
            return;
        }

        if (berthSide == null) {
            berthSide = StationBerth.assignBerth(berthHost, this);
            if (berthSide == null) {
                endBerthAndResume();
                return;
            }
        }
        StationBerth.writeBerthWorldPosition(berthHost, berthSide, getSize(), berthPoint);

        float dx = berthPoint.x - getX();
        float dy = berthPoint.y - getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        targetStrafeSpeed = 0f;

        if (dist <= BERTH_AUTO_DOCK_RADIUS) {
            currentBehavior = Behavior.DOCKING_AUTO;
            return;
        }

        targetAngle = getAngleToTarget(berthPoint.x, berthPoint.y);
        if (dist <= BERTH_SLOW_ZONE_RADIUS) {
            float zoneT = dist / BERTH_SLOW_ZONE_RADIUS;
            targetForwardSpeed = maxNormalSpeed * BERTH_FINAL_SPEED_FRACTION * MathUtils.clamp(zoneT, 0.25f, 1f);
        } else {
            targetForwardSpeed = maxNormalSpeed * berthApproachSpeedFraction;
        }
    }

    /** Magnetic pull into berth; bypasses forward/strafe flight so ships cannot overshoot. */
    private void updateAutoDock(float deltaTime) {
        if (!StationBerth.canBerthAt(berthHost)) {
            endBerthAndResume();
            return;
        }

        if (berthSide == null) {
            berthSide = StationBerth.assignBerth(berthHost, this);
            if (berthSide == null) {
                endBerthAndResume();
                return;
            }
        }
        StationBerth.writeBerthWorldPosition(berthHost, berthSide, getSize(), berthPoint);

        float berthFacing = StationBerth.berthFacingAngleDeg(berthHost, berthPoint);
        targetAngle = berthFacing;
        updateRotationTowardTarget(deltaTime, BERTH_AUTO_DOCK_ROTATION_MULTIPLIER);

        float dx = berthPoint.x - getX();
        float dy = berthPoint.y - getY();
        float distSq = dx * dx + dy * dy;
        float angleDiff = Math.abs(CustomMathUtils.angleDifference(getRotation(), berthFacing));

        if (distSq <= BERTH_ARRIVED_DISTANCE * BERTH_ARRIVED_DISTANCE
            && angleDiff <= BERTH_ALIGN_TOLERANCE_DEG) {
            snapToBerth(berthFacing);
            return;
        }

        if (distSq <= 0.0001f) {
            snapToBerth(berthFacing);
            return;
        }

        float dist = (float) Math.sqrt(distSq);
        float pullSpeed = MathUtils.clamp(
            dist * BERTH_AUTO_DOCK_PULL_GAIN,
            BERTH_AUTO_DOCK_MIN_SPEED,
            BERTH_AUTO_DOCK_MAX_SPEED
        );
        float invDist = 1f / dist;
        float pullX = dx * invDist * pullSpeed;
        float pullY = dy * invDist * pullSpeed;

        prevX = getX();
        prevY = getY();
        setX(getX() + pullX * deltaTime);
        setY(getY() + pullY * deltaTime);

        currentSpeed = 0f;
        strafeSpeed = 0f;
        targetForwardSpeed = 0f;
        targetStrafeSpeed = 0f;
        if (deltaTime > 0f) {
            velocityX = pullX;
            velocityY = pullY;
        }
    }

    private void snapToBerth(float berthFacing) {
        setX(berthPoint.x);
        setY(berthPoint.y);
        setRotation(berthFacing);
        targetAngle = berthFacing;
        currentSpeed = 0f;
        strafeSpeed = 0f;
        targetForwardSpeed = 0f;
        targetStrafeSpeed = 0f;
        velocityX = 0f;
        velocityY = 0f;
        currentBehavior = Behavior.DOCKED;
        berthTimer = 0f;
    }

    private void updateBerthed(float deltaTime) {
        if (StationBerth.canBerthAt(berthHost) && berthSide != null) {
            StationBerth.writeBerthWorldPosition(berthHost, berthSide, getSize(), berthPoint);
            setX(berthPoint.x);
            setY(berthPoint.y);
            targetAngle = StationBerth.berthFacingAngleDeg(berthHost, berthPoint);
            setRotation(targetAngle);
        }
        currentSpeed = 0f;
        strafeSpeed = 0f;
        targetForwardSpeed = 0f;
        targetStrafeSpeed = 0f;
        velocityX = 0f;
        velocityY = 0f;
        berthTimer += deltaTime;
        if (berthTimer >= berthDurationSeconds) {
            completeBerthStay();
        }
    }

    private void completeBerthStay() {
        Runnable callback = berthCompleteCallback;
        releaseBerth();
        if (callback != null) {
            callback.run();
        } else {
            endBerthAndResume();
        }
    }

    private void endBerthAndResume() {
        releaseBerth();
        if (trader) {
            currentBehavior = Behavior.TRADER_TRANSIT;
        } else if (militiaPatrol) {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
        } else if (playerControlled) {
            currentBehavior = Behavior.PLAYER_CONTROLLED;
        } else {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
        }
    }

    private void pickNextTradeDestination() {
        releaseBerth();

        if (tradeRoute.size == 0) {
            tradeDestination = null;
            return;
        }

        GameObject next;
        if (tradeRoute.size == 1) {
            next = tradeRoute.first();
        } else {
            next = tradeRoute.get(MathUtils.random(tradeRoute.size - 1));
            int guard = 0;
            while (next == tradeDestination && guard++ < 16) {
                next = tradeRoute.get(MathUtils.random(tradeRoute.size - 1));
            }
        }

        tradeDestination = next;
        orbitTarget = tradeDestination;
        targetAngle = getAngleToTarget(tradeDestination.getX(), tradeDestination.getY());

        if (StationBerth.canBerthAt(tradeDestination)) {
            beginBerthAt(tradeDestination, DEFAULT_BERTH_SECONDS, this::pickNextTradeDestination);
        } else {
            currentBehavior = Behavior.TRADER_TRANSIT;
        }
    }

    private void resetPatrolRelocateTimer() {
        patrolRelocateTimer = 0f;
        patrolRelocateInterval = MathUtils.random(
            MILITIA_RELOCATE_MIN_SECONDS,
            MILITIA_RELOCATE_MAX_SECONDS
        );
    }

    private void updateMilitiaPatrol(float deltaTime) {
        if (patrolAnchors.size < 2) {
            return;
        }
        patrolRelocateTimer += deltaTime;
        if (patrolRelocateTimer >= patrolRelocateInterval) {
            pickNextPatrolAnchor();
        }
    }

    private void pickNextPatrolAnchor() {
        if (patrolAnchors.size == 0) {
            return;
        }

        GameObject next;
        if (patrolAnchors.size == 1) {
            next = patrolAnchors.first();
        } else {
            next = patrolAnchors.get(MathUtils.random(patrolAnchors.size - 1));
            int guard = 0;
            while (next == orbitTarget && guard++ < 16) {
                next = patrolAnchors.get(MathUtils.random(patrolAnchors.size - 1));
            }
        }

        orbitTarget = next;
        targetAngle = getAngleToTarget(orbitTarget.getX(), orbitTarget.getY());
        currentBehavior = Behavior.MILITIA_TRANSIT;
        resetPatrolRelocateTimer();
    }

    private void updateMilitiaTransit() {
        if (orbitTarget == null) {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
            return;
        }

        targetAngle = getAngleToTarget(orbitTarget.getX(), orbitTarget.getY());
        targetForwardSpeed = maxNormalSpeed * MILITIA_TRANSIT_SPEED_FRACTION;
        targetStrafeSpeed = 0f;

        float arriveDist = orbitTarget.getSize() + getSize() + MILITIA_ARRIVAL_PADDING;
        if (getDistanceToOrbitTargetSquared() <= arriveDist * arriveDist) {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
            resetPatrolRelocateTimer();
            directionChangeTimer = 0f;
        }
    }

    private void warpOut(float deltaTime) {
        warpTimer += deltaTime;

        if (warpTimer <= WARP_OUT_RAMP_SECONDS) {
            targetForwardSpeed = MathUtils.lerp(
                maxNormalSpeed,
                warpSpeed,
                warpTimer / WARP_OUT_RAMP_SECONDS
            );
        } else {
            targetForwardSpeed = warpSpeed;
        }
        targetStrafeSpeed = 0f;

        if (warpTimer >= WARP_OUT_RAMP_SECONDS + WARP_OUT_CRUISE_SECONDS) {
            isVisible = false;
            warpTimer = 0f;
            currentBehavior = Behavior.WARPED_OUT;
        }
    }

    private void warpedOut(float deltaTime) {
        // ship should not be moving during this time
        warpTimer += deltaTime;
        if (warpTimer > 5f) {
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
            finishWarpIn();
        }
    }

    private void finishWarpIn() {
        warpTimer = 0f;
        if (trader) {
            currentBehavior = Behavior.TRADER_TRANSIT;
        } else if (militiaPatrol) {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
            resetPatrolRelocateTimer();
        } else {
            currentBehavior = Behavior.FLYING_AROUND_TARGET;
        }
    }

    private void updatePatrolOrCombat(float deltaTime, ProjectileManager projectileManager, Runnable idlePatrol) {
        if (trader || (!militiaPatrol && !civilian && !pirate)) {
            combatTarget = null;
            linedUpForShot = false;
            targetForwardSpeed = maxNormalSpeed;
            targetStrafeSpeed = 0f;
            updateWeaponAiming(deltaTime, null);
            if (idlePatrol != null) {
                idlePatrol.run();
            }
            return;
        }

        seekCombatTarget(deltaTime);
        if (combatTarget != null) {
            updateCombatBehavior(deltaTime);
            updateWeaponAiming(deltaTime, combatTarget);
            tryFire(projectileManager);
        } else {
            linedUpForShot = false;
            targetForwardSpeed = maxNormalSpeed;
            targetStrafeSpeed = 0f;
            updateWeaponAiming(deltaTime, null);
            if (idlePatrol != null) {
                idlePatrol.run();
            }
        }
    }

    private void seekCombatTarget(float deltaTime) {
        seekCombatTargetTimer += deltaTime;
        if (seekCombatTargetTimer >= seekCombatTargetInterval) {
            seekCombatTargetTimer = 0f;

            GameObject preferredTarget = findPreferredCombatTarget();
            if (preferredTarget != combatTarget && preferredTarget != null) {
                orbitSign = MathUtils.randomBoolean() ? 1f : -1f;
            }
            if (isAllowedCombatTarget(preferredTarget)) {
                combatTarget = preferredTarget;
            } else {
                combatTarget = null;
            }
        }
    }

    private GameObject findPreferredCombatTarget() {
        if (militiaPatrol || civilian) {
            return GameUtils.getClosestShipWithinRange(
                detectCombatDistanceSquared,
                getX(),
                getY(),
                this,
                SpaceShip::isPirate
            );
        }
        if (pirate) {
            GameObject traderTarget = GameUtils.getClosestShipWithinRange(
                detectCombatDistanceSquared,
                getX(),
                getY(),
                this,
                SpaceShip::isTrader
            );
            if (traderTarget != null) {
                return traderTarget;
            }
            return GameUtils.getClosestShipWithinRange(
                detectCombatDistanceSquared,
                getX(),
                getY(),
                this,
                ship -> ship.isMilitiaPatrol() || ship.isCivilian()
            );
        }
        return null;
    }

    private boolean isAllowedCombatTarget(GameObject target) {
        if (!isValidCombatTarget(target)) {
            return false;
        }
        SpaceShip targetShip = (SpaceShip) target;
        if (trader) {
            return false;
        }
        if (militiaPatrol || civilian) {
            return targetShip.isPirate();
        }
        if (pirate) {
            return targetShip.isTrader() || targetShip.isMilitiaPatrol() || targetShip.isCivilian();
        }
        return false;
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

    private void updateCombatBehavior(float deltaTime) {
        if (!isAllowedCombatTarget(combatTarget)) {
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
        updateRotationTowardTarget(deltaTime, 1f);
    }

    private void updateRotationTowardTarget(float deltaTime, float speedMultiplier) {
        targetAngle = CustomMathUtils.normalizeAngle360(targetAngle);

        float currentRotation = getRotation();
        float angleDiff = CustomMathUtils.angleDifference(targetAngle, currentRotation);
        float maxRotationThisFrame = maneuverability * speedMultiplier * deltaTime;

        if (Math.abs(angleDiff) <= maxRotationThisFrame) {
            setRotation(targetAngle);
        } else {
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
            releaseBerth();
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

    public void setCargo(Cargo cargo) {
        if (cargo != null) {
            this.cargo = cargo;
        }
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
        GameObject seekPick = findPreferredCombatTarget();
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
