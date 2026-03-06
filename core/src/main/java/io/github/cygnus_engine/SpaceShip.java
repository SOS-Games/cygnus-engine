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
    private GameObject planet;
    private GameObject spaceStation;
    private float prevX, prevY;
    private float currentSpeed = 0f;
    private float maxNormalSpeed;
    private float targetAngle; // Desired direction in degrees
    private float directionChangeTimer;
    private float directionChangeInterval;
    private float maxDistanceFromObjects;

    private Cargo cargo;

    private Behavior currentBehavior = Behavior.FLYING_AROUND_TARGET;

    private float warpSpeed; // max warp speed
    private float warpTimer; // Timer for warp effects
    private boolean isVisible = true;
    
    public enum Behavior {
        FLYING_TO_TARGET,
        FLYING_AROUND_TARGET,
        WARPING_OUT,
        WARPING_IN
    }
    
    public SpaceShip(float x, float y, float size, String name, 
                           GameObject planet, GameObject spaceStation) {
        super(Type.SPACE_SHIP, x, y, size, name);
        this.planet = planet;
        this.spaceStation = spaceStation;
        this.prevX = x;
        this.prevY = y;
        this.maxNormalSpeed = MathUtils.random(40f, 80f);
        this.currentSpeed = maxNormalSpeed;
        this.directionChangeTimer = 0f;
        this.directionChangeInterval = MathUtils.random(2f, 5f);
        this.maxDistanceFromObjects = 60f;

        this.cargo = new Cargo(true);
        
        this.warpSpeed = 200f;
        this.warpTimer = 0f;

        float currentAngle = MathUtils.random(0f, 360f);
        this.targetAngle = currentAngle;
        setRotation(currentAngle);
    }
    
    @Override
    public void update(float deltaTime) {

        switch (currentBehavior) {
            case WARPING_OUT:
                warpOut(deltaTime);
                break;
            case WARPING_IN:
                warpIn(deltaTime);
                break;
            case FLYING_AROUND_TARGET:
            case FLYING_TO_TARGET:
                changeDirectionPeriodically(deltaTime);
        }
                
        updateRotation(deltaTime);
        updatePosition(deltaTime);
    }

    private void warpOut(float deltaTime) {
        warpTimer += deltaTime;

        // we should go from maxNormalSpeed to warpSpeed over the course of 2 seconds, then disappear
        currentSpeed = MathUtils.lerp(maxNormalSpeed, warpSpeed, warpTimer / 2f);
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp out in
        
        // Disappear after 3 seconds
        if (warpTimer > 3f) {
            System.out.println("Warp out ... ship disappeared");
            isVisible = false;
            // Schedule warp in after a delay (2-4 seconds)
            warpTimer = -MathUtils.random(2f, 4f); // Negative to count up to 0
            currentBehavior = Behavior.WARPING_IN;
        }
    }

    private void warpIn(float deltaTime) {
        warpTimer += deltaTime;

        // we should go from warpSpeed to maxNormalSpeed over the course of 2 seconds, then switch to normal flying
        currentSpeed = MathUtils.lerp(warpSpeed, maxNormalSpeed, warpTimer / 2f);
        
        // note: let updatePostion handle the actual movement based on currentSpeed and rotation, we just need to set the rotation towards the direction we want to warp in from
        adjustCourseIfNeeded();

        // Become visible after 1 seconds
        if (warpTimer > 1f) {
            System.out.println("Warp in ... ship reappeared");
            isVisible = true;
            currentBehavior = Behavior.FLYING_AROUND_TARGET; // or FLYING_TO_TARGET based on your design
        }
    }

    private void changeDirectionPeriodically(float deltaTime) {
        directionChangeTimer += deltaTime;

        Float _angle = getRotation();

        // Change direction periodically
        if (directionChangeTimer >= directionChangeInterval) {
            directionChangeTimer = 0f;
            directionChangeInterval = MathUtils.random(2f, 5f);
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
        
        float distToPlanet = getDistanceTo(planet);
        float distToStation = getDistanceTo(spaceStation);
        float closestObjectDistance = Math.min(distToPlanet, distToStation);
        
        if (closestObjectDistance > maxDistanceFromObjects) {
            // Too far, turn towards nearest object
            GameObject nearest = (distToPlanet < distToStation) ? planet : spaceStation;
            float dx = nearest.getX() - getX();
            float dy = nearest.getY() - getY();
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
        // update previous position before moving
        prevX = getX();
        prevY = getY();

        // Move in current direction
        float rad = (float) Math.toRadians(getRotation());
        float moveX = currentSpeed * (float) Math.cos(rad) * deltaTime;
        float moveY = currentSpeed * (float) Math.sin(rad) * deltaTime;
        
        setX(getX() + moveX);
        setY(getY() + moveY);
    }

    private void updateRotation(float deltaTime) {
        // Calculate actual movement direction
        float moveDx = getX() - prevX;
        float moveDy = getY() - prevY;
        float moveDistance = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
        
        if (moveDistance > 0.1f) {
            float _angle = targetAngle;

            // clip angle to 0-360 degrees
            _angle = _angle % 360f;
            if (_angle < 0) _angle += 360f;
            
            float angleDiff = _angle - getRotation();
            
            // clip angle difference to -180 to 180 degrees
            if (angleDiff > 180f) angleDiff -= 360f;
            if (angleDiff < -180f) angleDiff += 360f;
            
            float rotationChange = angleDiff * 1f * deltaTime;
            setRotation(getRotation() + rotationChange);
        }
    }

    private float getDistanceTo(GameObject target) {
        float dx = getX() - target.getX();
        float dy = getY() - target.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public void triggerWarpOut() {
        if (currentBehavior != Behavior.WARPING_OUT && currentBehavior != Behavior.WARPING_IN) {
            currentBehavior = Behavior.WARPING_OUT;
            warpTimer = 0f;
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public Cargo getCargo() {
        return cargo;
    }
}
