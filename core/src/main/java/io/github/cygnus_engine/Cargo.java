package io.github.cygnus_engine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Cargo {
    private static final int MAX_CARGO_SIZE = 200;
    private Map<Commodity, Double> commodities;
    private Map<Commodity, Double> priceModifiers; // stores a random modifier of up to +/- 40% on each individual commodity
    private Double usedSpace;

    public Cargo(Boolean isRandomShip) {
        this.commodities = new HashMap<>();
        this.priceModifiers = new HashMap<>();

        double _usedSpace = 0;
        if (isRandomShip) {
            int numCommodities = Commodity.values().length;
            // generate modifiers for all commodities on initialization
            for (Commodity c : Commodity.values()) {
                double modifier = ThreadLocalRandom.current().nextDouble(-0.4, 0.4);
                this.priceModifiers.put(c, modifier);

                // also generate cargo contents
                float maxQtyForCommodity = MAX_CARGO_SIZE / numCommodities;
                double _commodityQty = Math.floor(Math.random() * maxQtyForCommodity);
                _usedSpace += _commodityQty;
                this.commodities.put(c, _commodityQty);
            }
        }
        
        this.usedSpace = _usedSpace;
    }

    public boolean addCommodity(Commodity commodity, Double quantity) {
        Double spaceNeeded = quantity;
        if (usedSpace + spaceNeeded > MAX_CARGO_SIZE) {
            return false; // Not enough space
        }
        commodities.put(commodity, commodities.getOrDefault(commodity, Double.valueOf(0)) + quantity);
        usedSpace += spaceNeeded;
        return true;
    }

    public boolean removeCommodity(Commodity commodity, Double quantity) {
        Double currentAmount = commodities.getOrDefault(commodity, Double.valueOf(0));
        if (currentAmount < quantity) {
            return false; // Not enough of this commodity
        }
        commodities.put(commodity, currentAmount - quantity);
        usedSpace -= quantity;
        if (commodities.get(commodity) == 0) {
            commodities.remove(commodity);
            // keep the price modifier; modifiers are generated at init and persist
        }
        return true;
    }

    public Double getQuantity(Commodity commodity) {
        return commodities.getOrDefault(commodity, Double.valueOf(0));
    }

    public Double getUsedSpace() {
        return usedSpace;
    }

    public int getMaxSpace() {
        return MAX_CARGO_SIZE;
    }

    public Double getFreeSpace() {
        return MAX_CARGO_SIZE - usedSpace;
    }

    public Map<Commodity, Double> getAllCommodities() {
        return new HashMap<>(commodities);
    }

    /**
     * Returns the random price modifier for the given commodity in this cargo.
     * Modifier is in the range [-0.4, +0.4]. If the commodity isn't present,
     * returns 0.0.
     */
    public double getModifier(Commodity commodity) {
        return priceModifiers.getOrDefault(commodity, 0.0);
    }

    /**
     * Returns the commodity's price after applying the modifier (rounded to int).
     */
    public int getModifiedPrice(Commodity commodity) {
        double modifier = getModifier(commodity);
        double price = commodity.getBasePrice() * (1.0 + modifier);
        return (int) Math.round(price);
    }

    /**
     * Returns a copy of the modifiers map for inspection.
     */
    public Map<Commodity, Double> getAllModifiers() {
        return new HashMap<>(priceModifiers);
    }
}
