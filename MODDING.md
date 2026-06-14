# Modding & project layout

## Project layout

```
core/          Shared game logic (Java)
lwjgl3/        Desktop launcher (LWJGL3)
assets/        UI skin, fonts
mods/core/     Ship PNGs, ship JSON, weapons/*.json, systems/*.json
AGENTS.md      Instructions for AI coding agents
```

## Modules

- **`core`** — `ApplicationAdapter`, game world, combat AI, modding UI, data I/O
- **`lwjgl3`** — desktop entry point (`Lwjgl3Launcher`)

**Coordinate conventions:** hull PNGs are authored with the nose toward **+Y**; gameplay heading uses **0° = +X** (see `ShipSpriteOrientation`).

## Modding ships

Each ship is a JSON file in `mods/<pack>/` (e.g. `mods/core/fighter.json`) plus a texture PNG referenced by `texturePath`. At runtime, mods are loaded from the local `mods/` folder next to the working directory.

Typical ship JSON fields:

- `speed`, `hullTurnDegPerSec`, `combatProfile`
- `weaponSlots` — mount position, `type`, `equippedWeaponId`
- `colliders` — projectile hit circles (hull-local)
- `outerBounds` — click-pick circle (hull-local)
- `centerOfMass` — sprite origin offset

Weapons live in `mods/core/weapons/*.json`:

- `fireInterval`, `projectileSpeed`, `projectileLifetime`, `projectileRadius`
- `turretCompatible` / `hardpointCompatible`, `turnRateDegPerSec`
- `homing` + `homingTurnRateDegPerSec` — steer toward a ship target
- `minesAsteroids` + `miningDamage` — damage asteroids instead of ships (mining laser)

The game requires at least one ship JSON under `mods/` before Play will start.

## Modding star systems

System layouts live in `mods/<pack>/systems/*.json` (e.g. `mods/core/systems/default.json`).

**Bodies** (`bodies` array):

- Planets: `{ "name", "x", "y", "size" }` — background scenery only
- Stations: `{ "type": "SPACE_STATION", "name", "x", "y", "size", "stationKind" }`
- `stationKind`: `TRADER`, `MILITIA`, `CIVILIAN`, or `MINING`

**Asteroid belts** (`asteroidBelts` array):

- `x`, `y` — belt center
- `width`, `height` — elliptical ring outer size
- `beltThickness` — ring width
- `asteroidCount` — number of mineable asteroids (seeded layout)

Place a `MINING` station near a belt so the game spawns miner ships (`mods/core/miner.json`) that harvest asteroids and return to dock.

Use the in-game **Modding** screen to edit hull layouts, weapons, and star systems, or edit the JSON/PNG files directly.
