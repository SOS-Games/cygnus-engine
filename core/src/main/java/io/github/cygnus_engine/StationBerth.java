package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/** Cardinal berth slots on {@link GameObject.Type#SPACE_STATION} bodies. */
public final class StationBerth {
    /** Outward from station center in gameplay degrees (0° = +X, 90° = +Y). */
    public enum Cardinal {
        RIGHT(0f),
        UP(90f),
        LEFT(180f),
        DOWN(270f);

        public final float outwardAngleDeg;

        Cardinal(float outwardAngleDeg) {
            this.outwardAngleDeg = outwardAngleDeg;
        }
    }

    private static final float BERTH_CLEARANCE = 10f;
    private static final ObjectMap<GameObject, SpaceShip[]> occupantsByStation = new ObjectMap<>();

    private StationBerth() {}

    public static boolean canBerthAt(GameObject host) {
        return host != null && host.getType() == GameObject.Type.SPACE_STATION;
    }

    /**
     * Reserves a free cardinal slot for {@code ship}, or returns the slot it already holds
     * at this station. Returns null when every slot is occupied by an actively berthing ship.
     */
    public static Cardinal assignBerth(GameObject station, SpaceShip ship) {
        if (!canBerthAt(station) || ship == null) {
            return Cardinal.RIGHT;
        }

        SpaceShip[] slots = occupantsByStation.get(station);
        if (slots == null) {
            slots = new SpaceShip[Cardinal.values().length];
            occupantsByStation.put(station, slots);
        }

        pruneStaleOccupants(station, slots);

        for (Cardinal side : Cardinal.values()) {
            if (slots[side.ordinal()] == ship) {
                return side;
            }
        }

        Array<Cardinal> free = new Array<>(Cardinal.values().length);
        for (Cardinal side : Cardinal.values()) {
            if (slots[side.ordinal()] == null) {
                free.add(side);
            }
        }

        if (free.size == 0) {
            return null;
        }

        Cardinal pick = free.get(MathUtils.random(free.size - 1));
        slots[pick.ordinal()] = ship;
        return pick;
    }

    public static void releaseBerth(GameObject station, Cardinal side, SpaceShip ship) {
        releaseShip(station, ship);
    }

    /** Clears {@code ship} from every slot at {@code station}. */
    public static void releaseShip(GameObject station, SpaceShip ship) {
        if (station == null || ship == null) {
            return;
        }
        SpaceShip[] slots = occupantsByStation.get(station);
        if (slots == null) {
            return;
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == ship) {
                slots[i] = null;
            }
        }
    }

    private static void pruneStaleOccupants(GameObject station, SpaceShip[] slots) {
        for (int i = 0; i < slots.length; i++) {
            SpaceShip occupant = slots[i];
            if (occupant != null && !occupant.isBerthingAt(station)) {
                slots[i] = null;
            }
        }
    }

    /** World position where the ship center should rest when berthed. */
    public static void writeBerthWorldPosition(
        GameObject station,
        Cardinal side,
        float shipRadius,
        Vector2 out
    ) {
        float halfStation = station.getSize() * 0.5f;
        float dist = halfStation + shipRadius + BERTH_CLEARANCE;
        float rad = (float) Math.toRadians(side.outwardAngleDeg);
        out.set(
            station.getX() + dist * MathUtils.cos(rad),
            station.getY() + dist * MathUtils.sin(rad)
        );
    }

    /** Hull heading (gameplay degrees) with the nose toward the station center. */
    public static float berthFacingAngleDeg(GameObject station, Vector2 berthPosition) {
        return CustomMathUtils.getAngleBetweenPoints(
            berthPosition.x,
            berthPosition.y,
            station.getX(),
            station.getY()
        );
    }
}
