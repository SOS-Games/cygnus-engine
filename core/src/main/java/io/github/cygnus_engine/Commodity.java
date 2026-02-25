package io.github.cygnus_engine;

public enum Commodity {
    FOOD("Food", 10),
    WATER("Water", 5),
    FUEL("Fuel", 15),
    METAL("Metal", 20),
    ELECTRONICS("Electronics", 50),
    MEDICINE("Medicine", 30),
    TEXTILES("Textiles", 8),
    LUXURY_GOODS("Luxury Goods", 100);

    private final String name;
    private final int basePrice;

    Commodity(String name, int basePrice) {
        this.name = name;
        this.basePrice = basePrice;
    }

    public String getName() {
        return name;
    }

    public int getBasePrice() {
        return basePrice;
    }
}
