package io.github.cygnus_engine;

/**
 * Hull hit volume in ship-local space (same frame as weapon mounts): offset from hull pivot
 * ({@link SpaceShip} world position). Rotation applies around that pivot at the ship yaw angle.
 */
public class ShipColliderCircle {
    public float x;
    public float y;
    public float radius;

    public ShipColliderCircle() {
    }

    public ShipColliderCircle(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public void set(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public ShipColliderCircle copy() {
        return new ShipColliderCircle(x, y, radius);
    }
}
