package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
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
        spawn(owner, x, y, angleDeg, speed, lifetime, radius, null, 0f);
    }

    /**
     * @param homingTarget if non-null, projectile turns toward this object each frame (see {@link WeaponData#homing}).
     */
    public void spawn(
        SpaceShip owner,
        float x,
        float y,
        float angleDeg,
        float speed,
        float lifetime,
        float radius,
        GameObject homingTarget,
        float homingTurnRateDegPerSec
    ) {
        Projectile projectile = projectilePool.obtain();
        projectile.owner = owner;
        projectile.position.set(x, y);
        projectile.previousPosition.set(x, y);
        projectile.velocity.set(speed, 0f).setAngleDeg(angleDeg);
        projectile.lifetime = lifetime;
        projectile.radius = radius;
        projectile.alive = true;
        projectile.homingTarget = homingTarget;
        projectile.homingTurnRateDegPerSec = homingTurnRateDegPerSec > 0f ? homingTurnRateDegPerSec : 120f;
        projectile.homing = homingTarget != null;
        activeProjectiles.add(projectile);
    }

    public void update(float deltaTime, Array<SpaceShip> ships) {
        for (int i = activeProjectiles.size - 1; i >= 0; i--) {
            Projectile projectile = activeProjectiles.get(i);

            projectile.previousPosition.set(projectile.position);
            if (projectile.homing) {
                applyHomingSteer(projectile, deltaTime);
            }
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

    private void applyHomingSteer(Projectile p, float deltaTime) {
        if (!p.homing) {
            return;
        }
        GameObject t = p.homingTarget;
        if (t == null) {
            return;
        }
        if (t instanceof SpaceShip ss && !ss.isVisible()) {
            p.homingTarget = null;
            return;
        }
        float speed = p.velocity.len();
        if (speed < 1f) {
            return;
        }
        float targetAng = MathUtils.atan2(t.getY() - p.position.y, t.getX() - p.position.x) * MathUtils.radiansToDegrees;
        float curAng = p.velocity.angleDeg();
        float diff = shortestSignedAngleDeg(curAng, targetAng);
        float step = p.homingTurnRateDegPerSec * deltaTime;
        float turn = Math.signum(diff) * Math.min(Math.abs(diff), step);
        p.velocity.set(speed, 0f).setAngleDeg(curAng + turn);
    }

    private static float shortestSignedAngleDeg(float fromDeg, float toDeg) {
        float d = toDeg - fromDeg;
        while (d > 180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    public void render(ShapeRenderer shapeRenderer) {
        for (Projectile projectile : activeProjectiles) {
            shapeRenderer.setColor(projectile.homing ? Color.ORANGE : Color.YELLOW);
            shapeRenderer.circle(projectile.position.x, projectile.position.y, projectile.radius);
        }
    }
}
