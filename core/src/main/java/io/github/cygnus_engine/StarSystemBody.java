package io.github.cygnus_engine;

/** Planet or station placed on a star-system map. */
public class StarSystemBody {
    public enum Kind {
        PLANET,
        SPACE_STATION
    }

    public Kind type = Kind.PLANET;
    /** Used when {@link #type} is {@link Kind#SPACE_STATION}. */
    public StationKind stationKind = StationKind.TRADER;
    public String name = "Body";
    public float x;
    public float y;
    /** Planet: radius. Station: half side length (square). */
    public float size = 40f;

    public StarSystemBody copy() {
        StarSystemBody c = new StarSystemBody();
        c.type = type;
        c.stationKind = stationKind;
        c.name = name;
        c.x = x;
        c.y = y;
        c.size = size;
        return c;
    }

    public void normalize() {
        if (type == null) {
            type = Kind.PLANET;
        }
        if (type == Kind.SPACE_STATION && stationKind == null) {
            stationKind = StationKind.TRADER;
        }
        if (name == null || name.isBlank()) {
            name = type == Kind.SPACE_STATION ? "Space Station" : "Planet";
        }
        if (!(size > 0f)) {
            size = type == Kind.SPACE_STATION ? 50f : 40f;
        }
    }
}
