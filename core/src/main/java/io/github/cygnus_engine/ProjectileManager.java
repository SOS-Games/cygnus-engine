package io.github.cygnus_engine;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public class ProjectileManager {
    private final Array<Projectile> activeProjectiles = new Array<>();
    private final Vector2 asteroidCenterScratch = new Vector2();
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
        spawn(owner, x, y, angleDeg, speed, lifetime, radius, null, 0f, false, 0f);
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
        spawn(owner, x, y, angleDeg, speed, lifetime, radius, homingTarget, homingTurnRateDegPerSec, false, 0f);
    }

    public void spawnMining(
        SpaceShip owner,
        float x,
        float y,
        float angleDeg,
        float speed,
        float lifetime,
        float radius,
        float miningDamage
    ) {
        spawn(owner, x, y, angleDeg, speed, lifetime, radius, null, 0f, true, miningDamage);
    }

    private void spawn(
        SpaceShip owner,
        float x,
        float y,
        float angleDeg,
        float speed,
        float lifetime,
        float radius,
        GameObject homingTarget,
        float homingTurnRateDegPerSec,
        boolean minesAsteroids,
        float miningDamage
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
        projectile.minesAsteroids = minesAsteroids;
        projectile.miningDamage = miningDamage;
        activeProjectiles.add(projectile);
    }

    public void update(float deltaTime, Array<SpaceShip> ships, Array<GameObject> asteroids, Array<GameObject> destroyedAsteroidsOut) {
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
                if (projectile.minesAsteroids) {
                    if (tryHitAsteroid(projectile, asteroids, destroyedAsteroidsOut)) {
                        projectile.alive = false;
                    }
                } else {
                    for (SpaceShip ship : ships) {
                        if (!ship.isVisible() || ship == projectile.owner) {
                            continue;
                        }

                        boolean hit = ship.projectileIntersectsHull(
                            projectile.previousPosition,
                            projectile.position,
                            projectile.radius
                        );
                        if (hit) {
                            projectile.alive = false;
                            break;
                        }
                    }
                }
            }

            if (!projectile.alive) {
                activeProjectiles.removeIndex(i);
                projectilePool.free(projectile);
            }
        }
    }

    private boolean tryHitAsteroid(
        Projectile projectile,
        Array<GameObject> asteroids,
        Array<GameObject> destroyedAsteroidsOut
    ) {
        if (asteroids == null) {
            return false;
        }

        for (GameObject asteroid : asteroids) {
            if (asteroid == null || !asteroid.isMineable()) {
                continue;
            }

            asteroidCenterScratch.set(asteroid.getX(), asteroid.getY());
            if (!Intersector.intersectSegmentCircle(
                projectile.previousPosition,
                projectile.position,
                asteroidCenterScratch,
                asteroid.getSize() + projectile.radius
            )) {
                continue;
            }

            if (asteroid.applyMiningDamage(projectile.miningDamage) && destroyedAsteroidsOut != null) {
                destroyedAsteroidsOut.add(asteroid);
            }
            return true;
        }
        return false;
    }

    private void applyHomingSteer(Projectile p, float deltaTime) {
        if (!p.homing) {
            return;
        }

        GameObject homingTarget = p.homingTarget;
        if (homingTarget == null) {
            return;
        }

        if (homingTarget instanceof SpaceShip ss) {
            if (!ss.isVisible() || ss.getCurrentBehavior() == SpaceShip.Behavior.WARPED_OUT || !GameUtils.isRegisteredShip(ss)) {
                p.homingTarget = null;
                return;
            }
        }

        float speed = p.velocity.len();
        if (speed < 1f) {
            return;
        }

        float angleToTarget = CustomMathUtils.getAngleBetweenPoints(p.position.x, p.position.y, homingTarget.getX(), homingTarget.getY());
        float currentMovementAngle = p.velocity.angleDeg();

        float diff = CustomMathUtils.deltaDeg(currentMovementAngle, angleToTarget);
        float direction = Math.signum(diff);
        float absDiff = Math.abs(diff);

        float maxRotationThisFrame = p.homingTurnRateDegPerSec * deltaTime;
        float turn = direction * Math.min(absDiff, maxRotationThisFrame);

        p.velocity.set(speed, 0f).setAngleDeg(currentMovementAngle + turn);
    }

    public void render(ShapeRenderer shapeRenderer) {
        for (Projectile projectile : activeProjectiles) {
            if (projectile.minesAsteroids) {
                shapeRenderer.setColor(0.55f, 0.95f, 1f, 1f);
            } else if (projectile.homing) {
                shapeRenderer.setColor(Color.ORANGE);
            } else {
                shapeRenderer.setColor(Color.YELLOW);
            }
            shapeRenderer.circle(projectile.position.x, projectile.position.y, projectile.radius);
        }
    }
}
