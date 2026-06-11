# Modding & project layout

## Project layout

```
core/          Shared game logic (Java)
lwjgl3/        Desktop launcher (LWJGL3)
assets/        UI skin, fonts
mods/core/     Ship PNGs, ship JSON, weapons/*.json
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

Weapons live in `mods/core/weapons/*.json` (projectile speed, lifetime, homing, turret/hardpoint compatibility).

The game requires at least one ship JSON under `mods/` before Play will start.

Use the in-game **Modding** screen to edit hull layouts and weapons, or edit the JSON/PNG files directly.
