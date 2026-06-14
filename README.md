# cygnus-engine

A [LibGDX](https://libgdx.com/) space sandbox: AI ships trade, patrol, fight, and mine around moddable star systems. Hulls, weapons, and system layouts are JSON-driven. Includes an in-game **Modding** screen to edit ships, weapons, and star systems.

Built with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff); desktop target uses LWJGL3.

## What’s in the game

**Main menu**

- **Play** — spawns ships from mod templates; click objects for info, scroll to zoom, click a ship to follow it with the camera.
- **Modding** — ship editor (mounts, colliders, COM, textures), weapon editor, and star-system editor (stations, asteroid belts).
- **Exit**

**Play mode**

- Star systems load from `mods/core/systems/*.json`: space stations, background planets, and procedural asteroid belts.
- **Station kinds:** trader (trade routes), militia/civilian (patrol + optional docking), mining (home base for miners).
- **Miners** fly from a mining station, mine several asteroids with a mining laser, return to dock, and repeat.
- Traders, militia, civilians, and pirates use combat AI (ballistic and homing weapons), orbit behavior, and station berthing.
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
