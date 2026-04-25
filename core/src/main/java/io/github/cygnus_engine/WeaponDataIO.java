package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeaponDataIO {
    private WeaponDataIO() {}

    private static Json newJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        return json;
    }

    public static WeaponData loadById(String weaponId) {
        if (weaponId == null || weaponId.isBlank()) return null;

        FileHandle f = findWeaponJson(weaponId);

        if (f == null || !f.exists()) return null;

        WeaponData data = newJson().fromJson(WeaponData.class, f);

        if (data.id == null || data.id.isBlank()) data.id = weaponId;
        if (data.name == null || data.name.isBlank()) data.name = data.id;

        return data;
    }

    /** All weapon definitions, order stable by mod folder then file name. */
    public static List<WeaponData> listAllWeapons() {
        Map<String, WeaponData> byId = new LinkedHashMap<>();
        FileHandle modsDir = Gdx.files.local("mods");

        if (!modsDir.exists()) return new ArrayList<>(byId.values());

        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            
            FileHandle weaponsDir = modFolder.child("weapons");

            if (!weaponsDir.exists() || !weaponsDir.isDirectory()) continue;

            for (FileHandle f : weaponsDir.list()) {
                if (!"json".equalsIgnoreCase(f.extension())) continue;

                try {
                    WeaponData w = newJson().fromJson(WeaponData.class, f);

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
        FileHandle modsDir = Gdx.files.local("mods");
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
                    WeaponData w = newJson().fromJson(WeaponData.class, f);

                    if (weaponId.equals(w.id)) return f;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static FileHandle resolveTextureFile(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) return null;
        
        FileHandle local = Gdx.files.local(texturePath);
        if (local.exists()) return local;

        FileHandle internal = Gdx.files.internal(texturePath);
        if (internal.exists()) return internal;

        return local;
    }
}
