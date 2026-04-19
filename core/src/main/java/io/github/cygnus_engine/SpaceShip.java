package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;

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
    private GameObject orbitTarget = null;
    private GameObject combatTarget = null;
    private float prevX, prevY = 0f;
    private float currentSpeed = 0f;
    private float maxNormalSpeed = 10f;
    private float targetAngle = 0f; // Desired direction in degrees
    private float directionChangeTimer = 0f;
    private float directionChangeInterval = 2f;
    private float maneuverability = 1f; // limits maximum rotationChange per frame
    private float seekCombatTargetTimer = 0f;
    private float seekCombatTargetInterval = 0.5f;

    private float combatBulletSpeed = 280f;
    private float combatAimToleranceDegrees = 6f;
    private float projectileRadius = 2.5f;
    private float projectileLifetime = 2f;
    private float fireCooldown = 0f;
    private float fireInterval = 0.35f;
    private float velocityX = 0f;
    private float velocityY = 0f;
    private boolean linedUpForShot = false; // todo - use this to fire bullets when I add the projectile system

    private float maxDistanceFromOrbitTarget = 100f;
    private float detectCombatDistance = 200f;
    private float combatMinDistance = 120f;
    private float combatMaxDistance = 240f;
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
        
        maxDistanceFromOrbitTargetSquared = maxDistanceFromOrbitTarget * maxDistanceFromOrbitTarget;
        detectCombatDistanceSquared = detectCombatDistance * detectCombatDistance;
        combatMinDistanceSquared = combatMinDistance * combatMinDistance;
        combatMaxDistanceSquared = combatMaxDistance * combatMaxDistance;
        combatFireRangeSquared = combatFireRange * combatFireRange;
        exitWarpWhenCloseToOrbitTargetDistanceSquared = exitWarpWhenCloseToOrbitTargetDistance * exitWarpWhenCloseToOrbitTargetDistance;
        enterWarpWhenFarFromOrbitTargetDistanceSquared = enterWarpWhenFarFromOrbitTargetDistance * enterWarpWhenFarFromOrbitTargetDistance;

        float currentAngle = MathUtils.random(0f, 360f);
        this.targetAngle = currentAngle;
        setRotation(currentAngle);
    }
    
    @Override
    public void update(float deltaTime) {
        update(deltaTime, null);
    }

    public void update(float deltaTime, ProjectileManager projectileManager) {
        fireCooldown = Math.max(0f, fireCooldown - deltaTime);

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
                    updateCombatBehavior();
                    tryFire(projectileManager);
                } else {
                    linedUpForShot = false;
                    currentSpeed = maxNormalSpeed;
                    changeDirectionPeriodically(deltaTime);
                }
        }
                
        updateRotation(deltaTime);
        updatePosition(deltaTime);
    }

    private void tryFire(ProjectileManager projectileManager) {
        if (projectileManager == null || !linedUpForShot || fireCooldown > 0f) {
            return;
        }

        float rad = (float) Math.toRadians(getRotation());
        float muzzleOffset = getSize() + projectileRadius + 2f;
        float spawnX = getX() + (float) Math.cos(rad) * muzzleOffset;
        float spawnY = getY() + (float) Math.sin(rad) * muzzleOffset;

        projectileManager.spawn(
            this,
            spawnX,
            spawnY,
            getRotation(),
            combatBulletSpeed,
            projectileLifetime,
            projectileRadius
        );
        fireCooldown = fireInterval;
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

            combatTarget = getClosestShipWithinRange(detectCombatDistanceSquared);
            if (combatTarget != null) {
                System.out.println("found target " + combatTarget.getName());
            }
        }
    }

    private GameObject getClosestShipWithinRange(float range) {
        return GameUtils.getClosestShipWithinRange(range, getX(), getY(), this);
    }

    private void updateCombatBehavior() {
        if (combatTarget == null) {
            linedUpForShot = false;
            return;
        }

        float distanceToTargetSquared = getDistanceToSquared(combatTarget);
        if (distanceToTargetSquared > detectCombatDistanceSquared) {
            combatTarget = null;
            linedUpForShot = false;
            return;
        }

        float interceptX = combatTarget.getX();
        float interceptY = combatTarget.getY();
        if (combatTarget instanceof SpaceShip) {
            SpaceShip targetShip = (SpaceShip) combatTarget;
            float distanceToTarget = (float) Math.sqrt(distanceToTargetSquared);
            float travelTime = distanceToTarget / combatBulletSpeed;
            interceptX += targetShip.getVelocityX() * travelTime;
            interceptY += targetShip.getVelocityY() * travelTime;
        }

        float desiredAngle = (float) Math.toDegrees(Math.atan2(interceptY - getY(), interceptX - getX()));
        targetAngle = normalizeAngle(desiredAngle);

        float angleDiff = Math.abs(getShortestAngleDifference(targetAngle, getRotation()));
        linedUpForShot = angleDiff <= combatAimToleranceDegrees && distanceToTargetSquared <= combatFireRangeSquared;

        if (distanceToTargetSquared > combatMaxDistanceSquared) {
            currentSpeed = maxNormalSpeed;
        } else if (distanceToTargetSquared < combatMinDistanceSquared) {
            currentSpeed = -maxNormalSpeed * 0.5f;
        } else {
            currentSpeed = 0f;
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
            _angle = _angle % 360f;
            if (_angle < 0) _angle += 360f;
            targetAngle = _angle;

            adjustCourseIfNeeded();
        }
    }
    
    private void adjustCourseIfNeeded() {
        // Check distance to planet and station, adjust course if too far
        // this overrides the random direction changes, but only if we are too far from both objects
        // todo - this needs a better way to determine maxDistanceFromOrbitTarget
        
        if (getDistanceToSquared(orbitTarget) > maxDistanceFromOrbitTargetSquared) {
            // Too far, turn towards nearest object
            float dx = orbitTarget.getX() - getX();
            float dy = orbitTarget.getY() - getY();
            float angleToClosest = (float) Math.toDegrees(Math.atan2(dy, dx));
            
            float _angle = getRotation();

            // set target angle directly towards the object
            _angle = angleToClosest;
            _angle = _angle % 360f;
            if (_angle < 0) _angle += 360f;

            targetAngle = _angle;
        }
    }

    private void updatePosition(float deltaTime) {
        // update previous position before moving. used for optimization, not for movement
        prevX = getX();
        prevY = getY();

        // Move in current direction
        float rad = (float) Math.toRadians(getRotation());
        float moveX = currentSpeed * (float) Math.cos(rad) * deltaTime;
        float moveY = currentSpeed * (float) Math.sin(rad) * deltaTime;
        
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
        // Calculate actual movement direction
        // why do we need previous x? it's used to avoid performing unnecessary computation
        //float moveDx = getX() - prevX;
        //float moveDy = getY() - prevY;
        float _angle = normalizeAngle(targetAngle);
        
        // difference between our target angle and our current rotation
        float angleDiff = getShortestAngleDifference(_angle, getRotation());
        
        float rotationChange = angleDiff * 1f * deltaTime;

        // don't change the rotation more than a certain amount (clamp rotationChange)
        // we want to lerp rotation over time
        rotationChange = Math.clamp(rotationChange, -maneuverability, maneuverability);

        setRotation(getRotation() + rotationChange);
    }

    private float getShortestAngleDifference(float target, float current) {
        float angleDiff = target - current;
        if (angleDiff > 180f) angleDiff -= 360f;
        if (angleDiff < -180f) angleDiff += 360f;
        return angleDiff;
    }

    private float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        if (normalized < 0) normalized += 360f;
        return normalized;
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
