# NeoForge 1.21.1 port

Target verified on 2026-11-07 against the official Modrinth release metadata:
[Create Aeronautics](https://modrinth.com/mod/create-aeronautics) 1.3.2+mc1.21.1
supports Minecraft 1.21.1 / NeoForge. This is a separate native build; the Fabric
1.21.11 and 26.3 source trees remain intact.

## Build and run

Use Java 21 or newer. This build has its own Gradle 9.2.1 wrapper, matching the
NeoForge 1.21.1 ModDevGradle template. From this directory:

```sh
./gradlew build
./gradlew runClient
```

From the repository root, use `./start.sh 1.21.1 build` or Windows `start.bat`
version 3. Output: `build/libs/justaclient-0.12.5+1.21.1-neoforge.jar` relative
to this directory. Install into a NeoForge 1.21.1 instance, not a Fabric instance.

## Implementation

- Native NeoForge entrypoint, lifecycle, GUI, input, chat, command, tooltip, HUD,
  world-rendering and configuration-screen integrations.
- Backported OpenGL shader programs for rounded shapes, MSDF text, glass, blur,
  colour grading, motion blur, hand effects and spatial menus.
- Native 1.21.1 recipe, skin/cape, hitbox, sky, fog, lightmap and item hooks.
- Modrinth searches/installations select NeoForge. The Mods panel reads NeoForge
  and Forge TOML metadata, including bundled mods and required dependencies.
- Plain Java comes from `../common/`; four loader-specific classes have native
  copies. Feature/UI code has its own versioned tree, like the existing 26.3 port.
- Firefly and dry-grass ambience use original procedural equivalents, because
  those vanilla sound events do not exist in 1.21.1. The six bundled Vorbis
  files are MIT-licensed and regenerated with `python tools/generate_ambience.py`
  (requires ffmpeg). No later-version Minecraft audio is redistributed.

## Validation checkpoint

- Gradle 9.2.1 build succeeds; all 256 JUnit tests pass.
- Live client loads Create 6.0.10, Sable 2.0.6 and Create Aeronautics 1.3.2,
  including their bundled Flywheel, Ponder, Veil and companion modules.
- Title, theme, HUD-settings and Mods screens have been opened in that instance.
- **World and final UI validation is still in progress.** Do not treat this
  checkpoint as a finished release. The previous test exposed a hitbox buffer
  lifetime issue; the corrected world test is running.

Developer checks (all opt-in; normal users do not run these):

```sh
./gradlew runClient -PjacSelfTest -PjacUiTour=/tmp/jac-tour -PjacUiScales=2,4
./gradlew runClient -PjacSmokeWorld -PjacSelfTest -PjacUiTour=/tmp/jac-world-tour
```

`jacSmokeWorld` creates/opens only `Justaclient-Port-Smoke` under this build's
`run/saves/` and enables a set of visual effects in that test instance. UI tours
save screenshots and quit when finished. Additional filters: `-PjacUiOnly=mods,hud`
and `-PjacUiWait=3500` (milliseconds).
