package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;

/** Role served by a space station in gameplay and editor maps. */
public enum StationKind {
    TRADER(new Color(0.85f, 0.95f, 1f, 1f)),
    MILITIA(new Color(0.35f, 0.55f, 1f, 1f)),
    CIVILIAN(new Color(0.55f, 0.9f, 0.45f, 1f));

    public final Color displayColor;

    StationKind(Color displayColor) {
        this.displayColor = displayColor;
    }

    public static StationKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TRADER;
        }
        return switch (raw.trim().toUpperCase()) {
            case "MILITIA" -> MILITIA;
            case "CIVILIAN" -> CIVILIAN;
            default -> TRADER;
        };
    }
}
