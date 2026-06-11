package io.github.cygnus_engine;

import com.badlogic.gdx.utils.Array;

public class GameUtils {
    private static final Array<GameObject> spaceShips = new Array<>();

    public static void clearSpaceShips() {
        spaceShips.clear();
    }

    public static void addSpaceShip(SpaceShip ship) {
        spaceShips.add(ship);
    }

    public static boolean isRegisteredShip(GameObject ship) {
        return ship != null && spaceShips.contains(ship, true);
    }

    public static int getRegisteredShipCount() {
        return spaceShips.size;
    }

    public static GameObject getClosestShipWithinRange(
        float rangeSquared,
        float currentX,
        float currentY,
        GameObject excludeShip
    ) {
        return getClosestShipWithinRange(rangeSquared, currentX, currentY, excludeShip, null);
    }

    public static GameObject getClosestShipWithinRange(
        float rangeSquared,
        float currentX,
        float currentY,
        GameObject excludeShip,
        ShipFilter filter
    ) {
        GameObject closestShip = null;
        float closestDistanceSquared = rangeSquared;

        for (GameObject ship : spaceShips) {
            if (ship == excludeShip) {
                continue;
            }
            if (!(ship instanceof SpaceShip spaceShip)) {
                continue;
            }
            if (!spaceShip.isVisible() || spaceShip.getCurrentBehavior() == SpaceShip.Behavior.WARPED_OUT) {
                continue;
            }
            if (filter != null && !filter.accept(spaceShip)) {
                continue;
            }

            float dx = ship.getX() - currentX;
            float dy = ship.getY() - currentY;

            float distanceSquared = dx * dx + dy * dy;

            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestShip = ship;
            }
        }
        return closestShip;
    }

    @FunctionalInterface
    public interface ShipFilter {
        boolean accept(SpaceShip ship);
    }
}
