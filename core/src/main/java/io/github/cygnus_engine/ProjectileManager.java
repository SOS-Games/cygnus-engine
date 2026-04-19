package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public class ProjectileManager {
    private final Array<Projectile> activeProjectiles = new Array<>();
    private final Vector2 collisionCenter = new Vector2();
    private final Pool<Projectile> projectilePool = new Pool<>() {
        @Override
        protected Projectile newObject() {
            return new Projectile();
        }
    };

    public void spawn(
        SpaceShip owner,
        float x,
        float y,
        float angleDeg,
        float speed,
        float lifetime,
        float radius
    ) {
        Projectile projectile = projectilePool.obtain();
        projectile.owner = owner;
        projectile.position.set(x, y);
        projectile.previousPosition.set(x, y);
        projectile.velocity.set(speed, 0f).setAngleDeg(angleDeg);
        projectile.lifetime = lifetime;
        projectile.radius = radius;
        projectile.alive = true;
        activeProjectiles.add(projectile);
    }

    public void update(float deltaTime, Array<SpaceShip> ships) {
        for (int i = activeProjectiles.size - 1; i >= 0; i--) {
            Projectile projectile = activeProjectiles.get(i);

            projectile.previousPosition.set(projectile.position);
            projectile.position.mulAdd(projectile.velocity, deltaTime);
            projectile.lifetime -= deltaTime;

            if (projectile.lifetime <= 0f) {
                projectile.alive = false;
            }

            if (projectile.alive) {
                for (SpaceShip ship : ships) {
                    if (!ship.isVisible() || ship == projectile.owner) {
                        continue;
                    }

                    float shipRadius = ship.getSize();
                    boolean hit = Intersector.intersectSegmentCircle(
                        projectile.previousPosition,
                        projectile.position,
                        collisionCenter.set(ship.getX(), ship.getY()),
                        shipRadius * shipRadius
                    );
                    if (hit) {
                        projectile.alive = false;
                        break;
                    }
                }
            }

            if (!projectile.alive) {
                activeProjectiles.removeIndex(i);
                projectilePool.free(projectile);
            }
        }
    }

    public void render(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(Color.YELLOW);
        for (Projectile projectile : activeProjectiles) {
            shapeRenderer.circle(projectile.position.x, projectile.position.y, projectile.radius);
        }
    }
}
