package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/** Resolves repo-local paths regardless of process working directory (root, assets/, or lwjgl3/). */
public final class ModPaths {
    private static FileHandle cachedModsRoot;

    private ModPaths() {}

    public static FileHandle modsRoot() {
        if (cachedModsRoot != null && cachedModsRoot.exists()) {
            return cachedModsRoot;
        }

        FileHandle direct = Gdx.files.local("mods");
        if (direct.exists()) {
            cachedModsRoot = direct;
            return direct;
        }

        FileHandle fromParent = Gdx.files.local("../mods");
        if (fromParent.exists()) {
            cachedModsRoot = fromParent;
            return fromParent;
        }

        cachedModsRoot = direct;
        return direct;
    }

    public static FileHandle resolveLocal(String path) {
        if (path == null || path.isBlank()) {
            return Gdx.files.local("");
        }

        FileHandle direct = Gdx.files.local(path);
        if (direct.exists()) {
            return direct;
        }

        FileHandle fromParent = Gdx.files.local("../" + path);
        if (fromParent.exists()) {
            return fromParent;
        }

        if (path.startsWith("mods/")) {
            FileHandle viaModsRoot = modsRoot().child(path.substring("mods/".length()));
            if (viaModsRoot.exists()) {
                return viaModsRoot;
            }
        }

        return direct;
    }
}
