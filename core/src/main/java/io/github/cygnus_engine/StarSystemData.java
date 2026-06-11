package io.github.cygnus_engine;

import java.util.ArrayList;
import java.util.List;

/** Star-system layout: map size and placed planets / stations. */
public class StarSystemData {
    public String id;
    public String name;

    /** Nominal play viewport (matches {@link GameWorld} defaults). */
    public float worldWidth = 800f;
    public float worldHeight = 600f;

    public List<StarSystemBody> bodies = new ArrayList<>();

    public void normalize() {
        if (id == null || id.isBlank()) {
            id = "system";
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (!(worldWidth > 0f)) {
            worldWidth = 800f;
        }
        if (!(worldHeight > 0f)) {
            worldHeight = 600f;
        }
        if (bodies == null) {
            bodies = new ArrayList<>();
        }
        bodies.removeIf(b -> b == null);
        for (StarSystemBody body : bodies) {
            body.normalize();
        }
    }

    /** Editor / map bounds that include default off-screen anchor placements. */
    public float mapMinX() {
        return -worldWidth * 0.5f;
    }

    public float mapMaxX() {
        return worldWidth * 1.5f;
    }

    public float mapMinY() {
        return 0f;
    }

    public float mapMaxY() {
        return worldHeight;
    }

    public float mapWidth() {
        return mapMaxX() - mapMinX();
    }

    public float mapHeight() {
        return mapMaxY() - mapMinY();
    }
}
