package io.github.cygnus_engine;

import com.badlogic.gdx.utils.Array;

public class GameUtils {
    private static Array<GameObject> spaceShips = new Array();

    public static void addSpaceShip(SpaceShip ship) {
        spaceShips.add(ship);
    }

    public static GameObject getClosestShipWithinRange(float rangeSquared, float currentX, float currentY) {
        for (GameObject ship : spaceShips) {
            float dx = ship.getX() - currentX;
            float dy = ship.getY() - currentY;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < rangeSquared) {
                return ship;
            }
        }
        return null;
    }
}
