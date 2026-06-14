package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/** Fills an elliptical ring with {@link GameObject.Type#ASTEROID} instances. */
public final class AsteroidBeltGenerator {
    private AsteroidBeltGenerator() {}

    public static void populate(StarSystemAsteroidBelt belt, Array<GameObject> out) {
        if (belt == null) {
            return;
        }
        belt.normalize();

        float cx = belt.x;
        float cy = belt.y;
        float a = belt.semiMajorAxis();
        float b = belt.semiMinorAxis();
        float thickness = belt.beltThickness;

        long seed = seedFor(belt);
        MathUtils.random.setSeed(seed);

        for (int i = 0; i < belt.asteroidCount; i++) {
            float t = MathUtils.random() * MathUtils.PI2;
            float cosT = MathUtils.cos(t);
            float sinT = MathUtils.sin(t);

            float nx = b * cosT;
            float ny = a * sinT;
            float nLen = (float) Math.sqrt(nx * nx + ny * ny);
            if (nLen < 1e-4f) {
                continue;
            }
            nx /= nLen;
            ny /= nLen;

            float offset = (MathUtils.random() - 0.5f) * thickness;
            float px = cx + a * cosT + nx * offset;
            float py = cy + b * sinT + ny * offset;
            float size = MathUtils.lerp(belt.asteroidSizeMin, belt.asteroidSizeMax, MathUtils.random());

            out.add(new GameObject(GameObject.Type.ASTEROID, px, py, size, belt.name));
        }
    }

    /** Stable layout for the same belt definition across loads. */
    private static long seedFor(StarSystemAsteroidBelt belt) {
        long seed = 1469598103934665603L;
        seed = hashString(seed, belt.name);
        seed = hashFloat(seed, belt.x);
        seed = hashFloat(seed, belt.y);
        seed = hashFloat(seed, belt.width);
        seed = hashFloat(seed, belt.height);
        seed = hashFloat(seed, belt.beltThickness);
        return seed;
    }

    private static long hashString(long seed, String value) {
        if (value == null) {
            return seed;
        }
        long h = seed;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= 1099511628211L;
        }
        return h;
    }

    private static long hashFloat(long seed, float value) {
        return seed ^ Float.floatToIntBits(value) * 1099511628211L;
    }

    /** Normalized ellipse radius at world point (1 = on the ellipse edge). */
    public static float normalizedEllipseRadius(float wx, float wy, StarSystemAsteroidBelt belt) {
        float dx = wx - belt.x;
        float dy = wy - belt.y;
        float a = belt.semiMajorAxis();
        float b = belt.semiMinorAxis();
        if (a < 1e-4f || b < 1e-4f) {
            return Float.MAX_VALUE;
        }
        float nx = dx / a;
        float ny = dy / b;
        return (float) Math.sqrt(nx * nx + ny * ny);
    }
}
