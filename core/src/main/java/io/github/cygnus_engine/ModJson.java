package io.github.cygnus_engine;

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
}
