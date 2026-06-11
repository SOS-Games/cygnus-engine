# AGENTS.md

Instructions for AI coding agents working in **cygnus-engine** (LibGDX Java game with moddable ships under `mods/`).

## Communication

- **Summaries must be concise.** Prefer short paragraphs or tight bullet lists. State what changed and why; skip recap of the full conversation, long file lists, and optional follow-ups unless the user asks.
- Match detail to task size: a one-line fix gets a one-line summary.
- Use code citations sparingly in summaries; link paths only when they help the user act.

## Project

- **Core logic:** `core/src/main/java/io/github/cygnus_engine/`
- **Ship/weapon data:** `mods/core/*.json` — see [MODDING.md](MODDING.md)
- **Run:** `./gradlew lwjgl3:run` (Windows: `gradlew.bat lwjgl3:run`)
- **Compile:** `./gradlew compileJava`

## Code changes

- Keep diffs focused; match existing style in nearby code.
- Do not commit unless the user asks.
- Hull art uses nose toward **+Y**; gameplay angles use **0° = +X** (`ShipSpriteOrientation`).
