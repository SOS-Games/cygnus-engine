# cygnus-engine

A [LibGDX](https://libgdx.com/) space sandbox: AI ships orbit planets and stations, fight with moddable hulls and weapons, and support basic trading. Includes an in-game **Modding** screen to edit ship layouts and weapon definitions.

Built with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff); desktop target uses LWJGL3.

## What’s in the game

**Main menu**

- **Play** — spawns ships from mod templates; click objects for info, scroll to zoom, click a ship to follow it with the camera.
- **Modding** — ship editor (mounts, colliders, COM, textures) and weapon editor.
- **Exit**

**Play mode**

- Ships patrol between a planet and space station, acquire combat targets, orbit, and fire projectiles (ballistic and homing).
- Click a ship to open its debug/info window (behavior, combat target, trade).

See [MODDING.md](MODDING.md) for project layout, modules, and ship/weapon JSON format.

## Run

```bash
# Windows
gradlew.bat lwjgl3:run

# macOS / Linux
./gradlew lwjgl3:run
```

Other useful tasks:

| Task | Description |
|------|-------------|
| `compileJava` | Compile all modules |
| `lwjgl3:jar` | Build runnable JAR (`lwjgl3/build/libs/`) |
| `clean` | Remove build output |

## Development notes

- AI agent instructions: see [AGENTS.md](AGENTS.md).
- Modding and repo layout: see [MODDING.md](MODDING.md).
- LibGDX version and Gradle options are in `gradle.properties` and the wrapper scripts.
