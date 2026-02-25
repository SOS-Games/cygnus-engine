package io.github.cygnus_engine;

public class GameObject {
    public enum Type {
        PLANET, SPACE_STATION, SPACE_SHIP, DEBUG_INDICATOR
    }
    
    private Type type;
    private float x, y;
    private float rotation; // in degrees
    private float rotationSpeed; // degrees per second
    private float speedX, speedY; // pixels per second
    private float size; // radius for circle, side length for square/triangle
    private String name;
    
    public GameObject(Type type, float x, float y, float size, String name) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.size = size;
        this.name = name;
        this.rotation = 0f;
        this.rotationSpeed = 0f;
        this.speedX = 0f;
        this.speedY = 0f;
    }
    
    public void setMovement(float speedX, float speedY) {
        this.speedX = speedX;
        this.speedY = speedY;
    }
    
    public void setRotationSpeed(float rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }
    
    public void update(float deltaTime) {
        x += speedX * deltaTime;
        y += speedY * deltaTime;
        rotation += rotationSpeed * deltaTime;
        
        // Normalize rotation to 0-360 range
        rotation = rotation % 360f;
        if (rotation < 0) rotation += 360f;
    }
    
    public boolean containsPoint(float pointX, float pointY) {
        switch (type) {
            case PLANET:
                float dx = x - pointX;
                float dy = y - pointY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                return distance < size;
                /*
                float dx = pointX - x;
                float dy = pointY - y;
                
                return (dx * dx + dy * dy) <= size * size;
                 */
            case SPACE_STATION:
                float halfSize = size / 2f;
                boolean xInRange = pointX >= x - halfSize && pointX <= x + halfSize;
                boolean yInRange = pointY >= y - halfSize && pointY <= y + halfSize;
                return xInRange && yInRange;
            case SPACE_SHIP:
                // For triangle, check if point is inside the rotated triangle
                // Using a simple bounding circle check for now (can be improved)
                float distX = pointX - x;
                float distY = pointY - y;
                return (distX * distX + distY * distY) <= size * size;
            default:
                return false;
        }
    }
    
    public void wrapAroundScreen(float screenWidth, float screenHeight) {
        if (x - size > screenWidth) {
            x = -size;
        } else if (x + size < 0) {
            x = screenWidth + size;
        }
        
        if (y - size > screenHeight) {
            y = -size;
        } else if (y + size < 0) {
            y = screenHeight + size;
        }
    }
    
    // Getters
    public Type getType() { return type; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getRotation() { return rotation; }
    public float getSize() { return size; }
    public String getName() { return name; }
    
    // Setters for position (useful for wrapping)
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setRotation(float rotation) { 
        this.rotation = rotation;
        // Normalize rotation to 0-360 range
        this.rotation = this.rotation % 360f;
        if (this.rotation < 0) this.rotation += 360f;
    }
}
