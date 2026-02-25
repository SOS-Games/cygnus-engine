package io.github.cygnus_engine;

import java.util.HashMap;
import java.util.Map;

public class Cargo {
    private static final int MAX_CARGO_SIZE = 200;
    private Map<Commodity, Integer> commodities;
    private int usedSpace;

    public Cargo() {
        this.commodities = new HashMap<>();
        this.usedSpace = 0;
    }

    public boolean addCommodity(Commodity commodity, int quantity) {
        int spaceNeeded = quantity;
        if (usedSpace + spaceNeeded > MAX_CARGO_SIZE) {
            return false; // Not enough space
        }
        commodities.put(commodity, commodities.getOrDefault(commodity, 0) + quantity);
        usedSpace += spaceNeeded;
        return true;
    }

    public boolean removeCommodity(Commodity commodity, int quantity) {
        int currentAmount = commodities.getOrDefault(commodity, 0);
        if (currentAmount < quantity) {
            return false; // Not enough of this commodity
        }
        commodities.put(commodity, currentAmount - quantity);
        usedSpace -= quantity;
        if (commodities.get(commodity) == 0) {
            commodities.remove(commodity);
        }
        return true;
    }

    public int getQuantity(Commodity commodity) {
        return commodities.getOrDefault(commodity, 0);
    }

    public int getUsedSpace() {
        return usedSpace;
    }

    public int getMaxSpace() {
        return MAX_CARGO_SIZE;
    }

    public int getFreeSpace() {
        return MAX_CARGO_SIZE - usedSpace;
    }

    public Map<Commodity, Integer> getAllCommodities() {
        return new HashMap<>(commodities);
    }
}
