package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;

// todo: needs warp out/in
// ships need cargo and trading
// ships should have limited cargo with random contents

// I need to separate ship movement from the ship class, I need a movement class. I need a single ship class.

// ships should have a smalltalk dialogue.

public class RandomFlyingShip extends GameObject {
    private GameObject planet;
    private GameObject spaceStation;
    private float prevX, prevY;
    private float currentSpeed;
    private float currentAngle; // Direction in degrees
    private float directionChangeTimer;
    private float directionChangeInterval;
    private float maxDistanceFromObjects;
    private float rotationInterpolationSpeed;
    
    public RandomFlyingShip(float x, float y, float size, String name, 
                           GameObject planet, GameObject spaceStation) {
        super(Type.SPACE_SHIP, x, y, size, name);
        this.planet = planet;
        this.spaceStation = spaceStation;
        this.prevX = x;
        this.prevY = y;
        this.currentSpeed = MathUtils.random(40f, 80f);
        this.currentAngle = MathUtils.random(0f, 360f);
        this.directionChangeTimer = 0f;
        this.directionChangeInterval = MathUtils.random(2f, 5f);
        this.maxDistanceFromObjects = 300f;
        this.rotationInterpolationSpeed = 3f;
        
        setRotation(currentAngle);
    }
    
    @Override
    public void update(float deltaTime) {
        prevX = getX();
        prevY = getY();
        
        directionChangeTimer += deltaTime;
        
        // Change direction periodically
        if (directionChangeTimer >= directionChangeInterval) {
            directionChangeTimer = 0f;
            directionChangeInterval = MathUtils.random(2f, 5f);
            // Change angle slightly (smooth turns)
            currentAngle += MathUtils.random(-45f, 45f);
            currentAngle = currentAngle % 360f;
            if (currentAngle < 0) currentAngle += 360f;
        }
        
        // Check distance to planet and station, adjust course if too far
        adjustCourseIfNeeded();
        
        // Move in current direction
        float rad = (float) Math.toRadians(currentAngle);
        float moveX = currentSpeed * (float) Math.cos(rad) * deltaTime;
        float moveY = currentSpeed * (float) Math.sin(rad) * deltaTime;
        
        setX(getX() + moveX);
        setY(getY() + moveY);
        
        // Update rotation smoothly
        updateRotation(deltaTime);
    }
    
    private void adjustCourseIfNeeded() {
        float distToPlanet = getDistanceTo(planet);
        float distToStation = getDistanceTo(spaceStation);
        float closestObjectDistance = Math.min(distToPlanet, distToStation);
        
        if (closestObjectDistance > maxDistanceFromObjects) {
            // Too far, turn towards nearest object
            GameObject nearest = (distToPlanet < distToStation) ? planet : spaceStation;
            float dx = nearest.getX() - getX();
            float dy = nearest.getY() - getY();
            float targetAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
            
            // Smoothly adjust angle towards target
            float angleDiff = targetAngle - currentAngle;
            if (angleDiff > 180f) angleDiff -= 360f;
            if (angleDiff < -180f) angleDiff += 360f;
            
            currentAngle += angleDiff * 0.1f; // Gradual turn
            currentAngle = currentAngle % 360f;
            if (currentAngle < 0) currentAngle += 360f;
        }
    }
    
    private float getDistanceTo(GameObject target) {
        float dx = getX() - target.getX();
        float dy = getY() - target.getY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    private void updateRotation(float deltaTime) {
        // Calculate actual movement direction
        float moveDx = getX() - prevX;
        float moveDy = getY() - prevY;
        float moveDistance = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
        
        if (moveDistance > 0.1f) {
            float targetAngle = (float) Math.toDegrees(Math.atan2(moveDy, moveDx));
            targetAngle = targetAngle % 360f;
            if (targetAngle < 0) targetAngle += 360f;
            
            float currentAngle = getRotation();
            float angleDiff = targetAngle - currentAngle;
            
            if (angleDiff > 180f) angleDiff -= 360f;
            if (angleDiff < -180f) angleDiff += 360f;
            
            float rotationChange = angleDiff * rotationInterpolationSpeed * deltaTime;
            setRotation(currentAngle + rotationChange);
        }
    }
}
