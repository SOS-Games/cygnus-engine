package io.github.cygnus_engine;

import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StarSystemDataIO {
    private StarSystemDataIO() {}

    public static StarSystemData loadFromJson(FileHandle jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            return null;
        }
        StarSystemData data = ModJson.newJson().fromJson(StarSystemData.class, jsonFile);
        if (data.id == null || data.id.isBlank()) {
            data.id = jsonFile.nameWithoutExtension();
        }
        data.normalize();
        return data;
    }

    public static void save(StarSystemData data, FileHandle targetJsonFile) {
        if (data == null) {
            throw new IllegalArgumentException("StarSystemData must not be null");
        }
        if (targetJsonFile == null) {
            throw new IllegalArgumentException("targetJsonFile must not be null");
        }
        data.normalize();
        ModJson.writePrettyFile(data, targetJsonFile, "StarSystemDataIO");
    }

    public static List<FileHandle> collectSystemJsonFiles() {
        List<FileHandle> out = new ArrayList<>();
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) {
            return out;
        }
        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) {
                continue;
            }
            FileHandle systemsDir = modFolder.child("systems");
            if (!systemsDir.exists() || !systemsDir.isDirectory()) {
                continue;
            }
            for (FileHandle f : systemsDir.list()) {
                if (f.isDirectory()) {
                    continue;
                }
                if (!"json".equalsIgnoreCase(f.extension())) {
                    continue;
                }
                out.add(f);
            }
        }
        out.sort(Comparator.comparing(FileHandle::path));
        return out;
    }

    /** Prefer {@code default.json}, else first discovered system. */
    public static StarSystemData loadPreferredForGameplay() {
        List<FileHandle> files = collectSystemJsonFiles();
        if (files.isEmpty()) {
            return null;
        }
        for (FileHandle f : files) {
            if ("default".equalsIgnoreCase(f.nameWithoutExtension())) {
                return loadFromJson(f);
            }
        }
        return loadFromJson(files.get(0));
    }

    public static StarSystemData createDefaultLayout(String id) {
        StarSystemData data = new StarSystemData();
        data.id = id;
        data.name = id;
        data.worldWidth = 800f;
        data.worldHeight = 600f;

        StarSystemBody planet = new StarSystemBody();
        planet.type = StarSystemBody.Kind.PLANET;
        planet.name = "Planet";
        planet.x = -data.worldWidth * 0.25f;
        planet.y = data.worldHeight * 0.5f;
        planet.size = 40f;
        data.bodies.add(planet);

        StarSystemBody station = new StarSystemBody();
        station.type = StarSystemBody.Kind.SPACE_STATION;
        station.stationKind = StationKind.TRADER;
        station.name = "Trader Station";
        station.x = data.worldWidth * 1.25f;
        station.y = data.worldHeight * 0.5f;
        station.size = 50f;
        data.bodies.add(station);

        StarSystemBody militiaStation = new StarSystemBody();
        militiaStation.type = StarSystemBody.Kind.SPACE_STATION;
        militiaStation.stationKind = StationKind.MILITIA;
        militiaStation.name = "Militia Outpost";
        militiaStation.x = data.worldWidth * 0.35f;
        militiaStation.y = data.worldHeight * 0.25f;
        militiaStation.size = 50f;
        data.bodies.add(militiaStation);

        StarSystemBody civilianStation = new StarSystemBody();
        civilianStation.type = StarSystemBody.Kind.SPACE_STATION;
        civilianStation.stationKind = StationKind.CIVILIAN;
        civilianStation.name = "Civilian Port";
        civilianStation.x = data.worldWidth * 0.65f;
        civilianStation.y = data.worldHeight * 0.75f;
        civilianStation.size = 50f;
        data.bodies.add(civilianStation);

        StarSystemAsteroidBelt belt = new StarSystemAsteroidBelt();
        belt.name = "Inner Belt";
        belt.x = data.worldWidth * 0.5f;
        belt.y = data.worldHeight * 0.5f;
        belt.width = data.worldWidth * 0.65f;
        belt.height = data.worldHeight * 0.4f;
        belt.beltThickness = 45f;
        belt.asteroidCount = 80;
        data.asteroidBelts.add(belt);

        StarSystemBody miningStation = new StarSystemBody();
        miningStation.type = StarSystemBody.Kind.SPACE_STATION;
        miningStation.stationKind = StationKind.MINING;
        miningStation.name = "Mining Depot";
        miningStation.x = belt.x;
        miningStation.y = belt.y;
        miningStation.size = 50f;
        data.bodies.add(miningStation);

        data.normalize();
        return data;
    }
}
