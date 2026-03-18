package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

public final class ShipDataIO {
    private ShipDataIO() {}

    private static Json newJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        // Keep defaults; Vector2/Rectangle serialize fine as plain objects.
        return json;
    }

    public static FileHandle toJsonFileForTexture(FileHandle textureFile) {
        return textureFile.sibling(textureFile.nameWithoutExtension() + ".json");
    }

    public static ShipData loadOrCreateDefault(FileHandle textureFile) {
        FileHandle jsonFile = toJsonFileForTexture(textureFile);
        ShipData data;
        if (jsonFile.exists()) {
            data = newJson().fromJson(ShipData.class, jsonFile);
        } else {
            data = new ShipData();
        }

        String id = textureFile.nameWithoutExtension();
        if (data.id == null || data.id.isBlank()) data.id = id;
        if (data.name == null || data.name.isBlank()) data.name = id;
        if (data.texturePath == null || data.texturePath.isBlank()) data.texturePath = textureFile.path();

        return data;
    }

    public static void save(ShipData data, FileHandle targetJsonFile) {
        if (data == null) throw new IllegalArgumentException("ShipData must not be null");
        if (targetJsonFile == null) throw new IllegalArgumentException("targetJsonFile must not be null");

        targetJsonFile.parent().mkdirs();
        String text = newJson().prettyPrint(data);
        targetJsonFile.writeString(text, false, "UTF-8");
        Gdx.app.log("ShipDataIO", "Saved ship data to " + targetJsonFile.path());
    }
}

