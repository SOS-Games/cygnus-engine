package io.github.cygnus_engine;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool;

public class Projectile implements Pool.Poolable {
    public final Vector2 position = new Vector2();
    public final Vector2 previousPosition = new Vector2();
    public final Vector2 velocity = new Vector2();
    public SpaceShip owner;
    public float lifetime;
    public float radius;
    public boolean alive;

    @Override
    public void reset() {
        position.setZero();
        previousPosition.setZero();
        velocity.setZero();
        owner = null;
        lifetime = 0f;
        radius = 0f;
        alive = false;
    }
}
