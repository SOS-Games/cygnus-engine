package io.github.cygnus_engine;

import com.badlogic.gdx.files.FileHandle;

public final class ShipDataIO {
    private ShipDataIO() {}

    public static FileHandle toJsonFileForTexture(FileHandle textureFile) {
        return textureFile.sibling(textureFile.nameWithoutExtension() + ".json");
    }

    public static ShipData loadOrCreateDefault(FileHandle textureFile) {
        FileHandle jsonFile = toJsonFileForTexture(textureFile);
        ShipData data;
        if (jsonFile.exists()) {
            data = ModJson.newJson().fromJson(ShipData.class, jsonFile);
        } else {
            data = new ShipData();
        }

        String id = textureFile.nameWithoutExtension();
        if (data.id == null || data.id.isBlank()) data.id = id;
        if (data.name == null || data.name.isBlank()) data.name = id;
        if (data.texturePath == null || data.texturePath.isBlank()) data.texturePath = textureFile.path();

        data.normalizeWeaponSlots();
        data.normalizeCombatProfile();
        data.normalizeColliders();
        data.normalizeOuterBounds();

        return data;
    }

    /** Load ship data from a standalone JSON file (e.g. {@code mods/core/frigate.json}). */
    public static ShipData loadFromJson(FileHandle jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            return null;
        }
        ShipData data = ModJson.newJson().fromJson(ShipData.class, jsonFile);
        if (data.id == null || data.id.isBlank()) {
            data.id = jsonFile.nameWithoutExtension();
        }
        if (data.name == null || data.name.isBlank()) {
            data.name = data.id;
        }
        data.normalizeWeaponSlots();
        data.normalizeCombatProfile();
        data.normalizeColliders();
        data.normalizeOuterBounds();
        return data;
    }

    public static void save(ShipData data, FileHandle targetJsonFile) {
        if (data == null) throw new IllegalArgumentException("ShipData must not be null");
        if (targetJsonFile == null) throw new IllegalArgumentException("targetJsonFile must not be null");

        data.normalizeOuterBounds();
        ModJson.writePrettyFile(data, targetJsonFile, "ShipDataIO");
    }
}

