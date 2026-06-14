package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/** Shared LibGDX JSON settings for mod data under {@code mods/}. */
public final class ModJson {
    private ModJson() {}

    public static Json newJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        return json;
    }

    public static void writePrettyFile(Object data, FileHandle targetJsonFile, String logTag) {
        targetJsonFile.parent().mkdirs();
        String text = newJson().prettyPrint(data);
        targetJsonFile.writeString(text, false, "UTF-8");
        Gdx.app.log(logTag, "Saved to " + targetJsonFile.path());
    }
}
