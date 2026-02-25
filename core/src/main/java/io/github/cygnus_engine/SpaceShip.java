package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;

public class SpaceShip extends GameObject {
    public enum Behavior {
        ORBITING_PLANET,
        FLYING_TO_STATION,
        ORBITING_STATION,
        FLYING_TO_PLANET,
        WARPING_OUT,
        WARPING_IN
    }
    
    private GameObject planet;
    private GameObject spaceStation;
    private Behavior currentBehavior;
    private float orbitAngle; // Current angle in orbit
    private float targetOrbitAngle; // initial orbit angle + 360
    private ShipConfig config;
    private float orbitLoops; // How many loops completed for current orbit
    private float prevX, prevY; // Previous position for calculating movement direction
    private float warpSpeed; // Current warp speed multiplier
    private float warpTimer; // Timer for warp effects
    private boolean isVisible = true;
    private GameObject warpTarget; // Target when warping in
    
    public SpaceShip(float x, float y, float size, String name, GameObject planet, GameObject spaceStation) {
        this(x, y, size, name, planet, spaceStation, ShipConfig.createDefault(), Behavior.ORBITING_PLANET);
    }
    
    public SpaceShip(float x, float y, float size, String name, GameObject planet, GameObject spaceStation, 
                    ShipConfig config, Behavior startBehavior) {
        super(Type.SPACE_SHIP, x, y, size, name);
        this.planet = planet;
        this.spaceStation = spaceStation;
        this.config = config;
        this.currentBehavior = startBehavior;
        this.orbitLoops = 0f;
        this.prevX = x;
        this.prevY = y;
        this.warpSpeed = 1f;
        this.warpTimer = 0f;
        
        // Calculate initial orbit angle based on start behavior
        GameObject initialTarget = (startBehavior == Behavior.ORBITING_PLANET) ? planet : spaceStation;
        float dx = getX() - initialTarget.getX();
        float dy = getY() - initialTarget.getY();
        this.orbitAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        this.targetOrbitAngle = orbitAngle + 360;
        
        // Set initial rotation based on orbit direction
        float orbitDirection = orbitAngle + 90f; // Tangent to orbit
        setRotation(orbitDirection);
    }
    
    @Override
    public void update(float deltaTime) {
        if (!isVisible && currentBehavior != Behavior.WARPING_IN) {
            return; // Don't update if warped out
        }
        
        // Store previous position before movement
        prevX = getX();
        prevY = getY();
        
        switch (currentBehavior) {
            case ORBITING_PLANET:
                orbitAround(planet, deltaTime);
                break;
            case FLYING_TO_STATION:
                flyTo(spaceStation, deltaTime, Behavior.ORBITING_STATION);
                break;
            case ORBITING_STATION:
                orbitAround(spaceStation, deltaTime);
                break;
            case FLYING_TO_PLANET:
                flyTo(planet, deltaTime, Behavior.ORBITING_PLANET);
                break;
            case WARPING_OUT:
                warpOut(deltaTime);
                break;
            case WARPING_IN:
                warpIn(deltaTime);
                break;
        }
        
        // Update rotation to face actual movement direction
        updateRotation(deltaTime);
    }
    
    private void orbitAround(GameObject target, float deltaTime) {
        float previousOrbitAngle = orbitAngle; // why do we need previousOrbitAngle???
        orbitAngle += config.orbitSpeed * deltaTime;
        
        if (currentBehavior == Behavior.ORBITING_STATION) {
            System.out.println(orbitAngle);
        }
        // if we are coming in from the left side, the orbit angle will be set to 180 initially
        // it'll rotate up to 360 and leave from the right side instead of making a full rotation, which should be 180 + 360 degrees

        // Track completed loops - check if we've passed targetOrbitAngle degrees
        // doesn't work correctly
        if (previousOrbitAngle < orbitAngle && orbitAngle >= targetOrbitAngle) {
            orbitLoops += 1f;
            orbitAngle -= 360f;
            
            // Check if we've completed enough loops
            if (orbitLoops >= config.targetOrbitLoops) {
                orbitLoops = 0f;
                // Switch to next behavior
                if (currentBehavior == Behavior.ORBITING_PLANET) {
                    currentBehavior = Behavior.FLYING_TO_STATION;
                } else {
                    currentBehavior = Behavior.FLYING_TO_PLANET;
                }
            }
        } else if (orbitAngle >= 360f) {
            orbitAngle -= 360f;
        }
        
        // Calculate position based on orbit
        float rad = (float) Math.toRadians(orbitAngle);
        setX(target.getX() + config.orbitRadius * (float) Math.cos(rad));
        setY(target.getY() + config.orbitRadius * (float) Math.sin(rad));
    }
    
    private void flyTo(GameObject target, float deltaTime, Behavior nextBehavior) {
        float dx = getX() - target.getX();
        float dy = getY() - target.getY();
        float distanceToCenter = (float) Math.sqrt(dx * dx + dy * dy);
        
        // Check if we've reached the orbit radius
        float distanceToOrbit = Math.abs(distanceToCenter - config.orbitRadius);
        
        if (distanceToOrbit < 2f) {
            // We're at the orbit radius, smoothly transition to orbiting
            // Calculate the current angle from target
            float currentAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
            
            // Set orbit angle to match current position
            orbitAngle = currentAngle;
            targetOrbitAngle = orbitAngle + 360;

            
            // Start orbiting (will be handled in next frame by normal update cycle)
            currentBehavior = nextBehavior;
            orbitLoops = 0f; // Reset loop counter for new orbit
        } else {
            // Fly towards the orbit radius
            // Calculate desired position on orbit circle
            float angleToTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
            
            // Calculate target position on orbit
            float targetX = target.getX() + config.orbitRadius * (float) Math.cos(Math.toRadians(angleToTarget));
            float targetY = target.getY() + config.orbitRadius * (float) Math.sin(Math.toRadians(angleToTarget));
            
            // Move towards that position
            float moveDx = targetX - getX();
            float moveDy = targetY - getY();
            float moveDistance = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
            
            if (moveDistance > 0.1f) {
                float moveSpeed = config.travelSpeed * deltaTime;
                if (moveSpeed > moveDistance) {
                    moveSpeed = moveDistance;
                }
                
                float ratio = moveSpeed / moveDistance;
                setX(getX() + moveDx * ratio);
                setY(getY() + moveDy * ratio);
            }
        }
    }
    
    private void warpOut(float deltaTime) {
        warpTimer += deltaTime;
        warpSpeed += deltaTime * 3f; // Accelerate
        
        // Determine which object we're currently near
        GameObject currentTarget = (getDistanceTo(planet) < getDistanceTo(spaceStation)) ? planet : spaceStation;
        
        // Move away from current target
        float dx = getX() - currentTarget.getX();
        float dy = getY() - currentTarget.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (distance > 0.1f) {
            float moveSpeed = config.travelSpeed * warpSpeed * deltaTime;
            float ratio = moveSpeed / distance;
            setX(getX() + dx * ratio);
            setY(getY() + dy * ratio);
        }
        
        // Disappear after 1 second
        if (warpTimer > 1f) {
            //System.out.println("Warp out ... ship disappeared");
            isVisible = false;
            // Schedule warp in after a delay (2-4 seconds)
            warpTimer = -MathUtils.random(2f, 4f); // Negative to count up to 0
            warpSpeed = 5f; // Start fast when warping in
            // Choose random target
            warpTarget = MathUtils.randomBoolean() ? planet : spaceStation;
            currentBehavior = Behavior.WARPING_IN;
        }
    }
    
    private void warpIn(float deltaTime) {
        warpTimer += deltaTime;
        
        // Wait for warp delay
        if (warpTimer < 0f) {
            return; // Still waiting
        }
        
        // If not visible yet, appear at target position
        if (!isVisible) {
            //System.out.println("Warp in ... ship appeared");

            // TODO: this should spawn at the edge of the screen and fly fast in, not appear at orbit!

            // get edge of screen at 0,0
            float edgeX = 0f;
            float edgeY = 0f;

            // fly towards center of screen
            float dx = edgeX;
            float dy = edgeY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float moveSpeed = config.travelSpeed * warpSpeed * deltaTime;
            if (moveSpeed > distance) moveSpeed = distance;
            float ratio = moveSpeed / distance;
            setX(edgeX + dx * ratio);
            setY(edgeY + dy * ratio);

            /*
            // Calculate position near target at orbit radius
            float angle = MathUtils.random(0f, 360f);
            float targetX = warpTarget.getX() + config.orbitRadius * (float) Math.cos(Math.toRadians(angle));
            float targetY = warpTarget.getY() + config.orbitRadius * (float) Math.sin(Math.toRadians(angle));
            
            setX(targetX);
            setY(targetY);
            isVisible = true;
            orbitAngle = angle;
            orbitLoops = 0f;
            warpSpeed = 5f; // Start fast
            */
        }
        
        // Decelerate and move towards orbit
        warpSpeed = Math.max(1f, warpSpeed - deltaTime * 2f);
        
        // Move towards proper orbit position
        float dx = getX() - warpTarget.getX();
        float dy = getY() - warpTarget.getY();
        float distanceToCenter = (float) Math.sqrt(dx * dx + dy * dy);
        float distanceToOrbit = Math.abs(distanceToCenter - config.orbitRadius);
        
        if (distanceToOrbit > 2f) {
            // Still adjusting to orbit
            float angleToTarget = (float) Math.toDegrees(Math.atan2(dy, dx));
            float targetX = warpTarget.getX() + config.orbitRadius * (float) Math.cos(Math.toRadians(angleToTarget));
            float targetY = warpTarget.getY() + config.orbitRadius * (float) Math.sin(Math.toRadians(angleToTarget));
            
            float moveDx = targetX - getX();
            float moveDy = targetY - getY();
            float moveDistance = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
            
            if (moveDistance > 0.1f) {
                float moveSpeed = config.travelSpeed * warpSpeed * deltaTime;
                if (moveSpeed > moveDistance) moveSpeed = moveDistance;
                float ratio = moveSpeed / moveDistance;
                setX(getX() + moveDx * ratio);
                setY(getY() + moveDy * ratio);
            }
        } else {
            // Reached orbit, start orbiting
            if (warpTarget == planet) {
                currentBehavior = Behavior.ORBITING_PLANET;
            } else {
                currentBehavior = Behavior.ORBITING_STATION;
            }
            warpSpeed = 1f;
            warpTimer = 0f;
        }
    }
    
    private float getDistanceTo(GameObject target) {
        float dx = getX() - target.getX();
        float dy = getY() - target.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    public void triggerWarpOut() {
        if (currentBehavior == Behavior.ORBITING_PLANET || currentBehavior == Behavior.ORBITING_STATION) {
            currentBehavior = Behavior.WARPING_OUT;
            warpTimer = 0f;
            warpSpeed = 1f;
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    private void updateRotation(float deltaTime) {
        // Calculate actual movement direction from previous to current position
        float moveDx = getX() - prevX;
        float moveDy = getY() - prevY;
        
        // If we're not moving (or barely moving), use target direction
        float moveDistance = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
        float targetAngle;
        
        if (moveDistance > 0.1f) {
            // Use actual movement direction
            targetAngle = (float) Math.toDegrees(Math.atan2(moveDy, moveDx));
        } else {
            // Fall back to target direction if not moving
            float dx, dy;
            switch (currentBehavior) {
                case ORBITING_PLANET:
                case ORBITING_STATION:
                    // Face tangent to orbit (perpendicular to radius)
                    GameObject target = (currentBehavior == Behavior.ORBITING_PLANET) ? planet : spaceStation;
                    float toTargetX = target.getX() - getX();
                    float toTargetY = target.getY() - getY();
                    // Perpendicular vector (rotate 90 degrees)
                    dx = -toTargetY;
                    dy = toTargetX;
                    break;
                case FLYING_TO_STATION:
                    dx = spaceStation.getX() - getX();
                    dy = spaceStation.getY() - getY();
                    break;
                case FLYING_TO_PLANET:
                    dx = planet.getX() - getX();
                    dy = planet.getY() - getY();
                    break;
                default:
                    return;
            }
            targetAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        
        // Normalize target angle to 0-360
        targetAngle = targetAngle % 360f;
        if (targetAngle < 0) targetAngle += 360f;
        
        // Get current rotation and normalize
        float currentAngle = getRotation();
        
        // Calculate shortest rotation path
        float angleDiff = targetAngle - currentAngle;
        
        // Normalize to -180 to 180 range for shortest path
        if (angleDiff > 180f) {
            angleDiff -= 360f;
        } else if (angleDiff < -180f) {
            angleDiff += 360f;
        }
        
        // Smoothly interpolate rotation
        float rotationChange = angleDiff * config.rotationInterpolationSpeed * deltaTime;
        float newAngle = currentAngle + rotationChange;
        
        setRotation(newAngle);
    }
}
