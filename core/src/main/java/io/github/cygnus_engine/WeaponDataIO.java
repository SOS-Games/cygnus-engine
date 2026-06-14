package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeaponDataIO {
    private WeaponDataIO() {}

    /** Load a weapon from an explicit JSON file path. */
    public static WeaponData loadFromJsonFile(FileHandle jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            return null;
        }
        WeaponData data = ModJson.newJson().fromJson(WeaponData.class, jsonFile);
        if (data.id == null || data.id.isBlank()) {
            data.id = jsonFile.nameWithoutExtension();
        }
        if (data.name == null || data.name.isBlank()) {
            data.name = data.id;
        }
        return data;
    }

    public static WeaponData loadById(String weaponId) {
        if (weaponId == null || weaponId.isBlank()) return null;

        FileHandle f = findWeaponJson(weaponId);

        if (f == null || !f.exists()) return null;

        WeaponData data = ModJson.newJson().fromJson(WeaponData.class, f);

        if (data.id == null || data.id.isBlank()) data.id = weaponId;
        if (data.name == null || data.name.isBlank()) data.name = data.id;

        return data;
    }

    /** All weapon definitions, order stable by mod folder then file name. */
    public static List<WeaponData> listAllWeapons() {
        Map<String, WeaponData> byId = new LinkedHashMap<>();
        FileHandle modsDir = ModPaths.modsRoot();

        if (!modsDir.exists()) return new ArrayList<>(byId.values());

        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            
            FileHandle weaponsDir = modFolder.child("weapons");

            if (!weaponsDir.exists() || !weaponsDir.isDirectory()) continue;

            for (FileHandle f : weaponsDir.list()) {
                if (!"json".equalsIgnoreCase(f.extension())) continue;

                try {
                    WeaponData w = ModJson.newJson().fromJson(WeaponData.class, f);

                    if (w.id == null || w.id.isBlank()) w.id = f.nameWithoutExtension();
                    if (w.name == null || w.name.isBlank()) w.name = w.id;

                    byId.putIfAbsent(w.id, w);
                } catch (Exception e) {
                    Gdx.app.error("WeaponDataIO", "Failed to load weapon " + f.path(), e);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    public static FileHandle findWeaponJson(String weaponId) {
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) return null;
        
        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;

            FileHandle weaponsDir = modFolder.child("weapons");

            if (!weaponsDir.exists()) continue;

            FileHandle direct = weaponsDir.child(weaponId + ".json");

            if (direct.exists()) return direct;

            for (FileHandle f : weaponsDir.list()) {
                if (!"json".equalsIgnoreCase(f.extension())) continue;

                try {
                    WeaponData w = ModJson.newJson().fromJson(WeaponData.class, f);

                    if (weaponId.equals(w.id)) return f;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static void save(WeaponData data, FileHandle targetJsonFile) {
        if (data == null) throw new IllegalArgumentException("WeaponData must not be null");
        if (targetJsonFile == null) throw new IllegalArgumentException("targetJsonFile must not be null");
        ModJson.writePrettyFile(data, targetJsonFile, "WeaponDataIO");
    }

    public static FileHandle resolveTextureFile(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) return null;
        
        FileHandle local = ModPaths.resolveLocal(texturePath);
        if (local.exists()) return local;

        FileHandle internal = Gdx.files.internal(texturePath);
        if (internal.exists()) return internal;

        return local;
    }
}
