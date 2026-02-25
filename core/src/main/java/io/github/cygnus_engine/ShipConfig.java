package io.github.cygnus_engine;

/**
 * Configuration for ship behavior - allows different ships to have different speeds and maneuverability
 */
public class ShipConfig {
    public float orbitRadius;
    public float orbitSpeed; // Degrees per second
    public float travelSpeed; // Pixels per second
    public float rotationInterpolationSpeed; // How fast rotation interpolates
    public float targetOrbitLoops; // How many loops to do before moving
    
    public ShipConfig(float orbitRadius, float orbitSpeed, float travelSpeed, 
                     float rotationInterpolationSpeed, float targetOrbitLoops) {
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.travelSpeed = travelSpeed;
        this.rotationInterpolationSpeed = rotationInterpolationSpeed;
        this.targetOrbitLoops = targetOrbitLoops;
    }
    
    // Default config
    public static ShipConfig createDefault() {
        return new ShipConfig(80f, 60f, 100f, 2f, 1f);
    }
    
    // Fast, agile ship
    public static ShipConfig createFast() {
        return new ShipConfig(70f, 90f, 150f, 4f, 1f);
    }
    
    // Slow, heavy ship
    public static ShipConfig createSlow() {
        return new ShipConfig(100f, 40f, 60f, 1f, 1f);
    }
}
