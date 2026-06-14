package io.github.cygnus_engine;

/** Elliptical asteroid ring placed on a star-system map. */
public class StarSystemAsteroidBelt {
    public String name = "Asteroid Belt";
    /** Ellipse center in world space. */
    public float x;
    public float y;
    /** Full outer width and height of the belt ellipse. */
    public float width = 400f;
    public float height = 280f;
    /** Radial thickness of the ring (world units). */
    public float beltThickness = 40f;
    public int asteroidCount = 80;
    public float asteroidSizeMin = 2f;
    public float asteroidSizeMax = 7f;

    public StarSystemAsteroidBelt copy() {
        StarSystemAsteroidBelt c = new StarSystemAsteroidBelt();
        c.name = name;
        c.x = x;
        c.y = y;
        c.width = width;
        c.height = height;
        c.beltThickness = beltThickness;
        c.asteroidCount = asteroidCount;
        c.asteroidSizeMin = asteroidSizeMin;
        c.asteroidSizeMax = asteroidSizeMax;
        return c;
    }

    public void normalize() {
        if (name == null || name.isBlank()) {
            name = "Asteroid Belt";
        }
        if (!(width > 0f)) {
            width = 400f;
        }
        if (!(height > 0f)) {
            height = 280f;
        }
        if (!(beltThickness > 0f)) {
            beltThickness = 40f;
        }
        if (asteroidCount < 1) {
            asteroidCount = 80;
        }
        if (!(asteroidSizeMin > 0f)) {
            asteroidSizeMin = 2f;
        }
        if (!(asteroidSizeMax >= asteroidSizeMin)) {
            asteroidSizeMax = Math.max(asteroidSizeMin, 7f);
        }
    }

    public float semiMajorAxis() {
        return width * 0.5f;
    }

    public float semiMinorAxis() {
        return height * 0.5f;
    }
}
