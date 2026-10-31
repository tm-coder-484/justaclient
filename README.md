# Justaclient

A visuals-only Fabric client for Minecraft Java **1.21.11** and **26.3**. Smooth animated UI, frosted-glass panels, crisp fonts and a
look you can fully re-theme. No cheats: nothing that reveals hidden information or automates play.

Requires **Fabric API** and **Sodium**.

![The Justaclient menu over the world, default dark glass look](docs/screenshots/menu.png)

## Quick start (Windows)

1. Install Java (Temurin): **Java 25** runs both versions: https://adoptium.net/temurin/releases/?version=25
   (Java 21 is enough if you only want 1.21.11.)
2. Double-click **`start.bat`**, pick the Minecraft version (**1** = 1.21.11, **2** = 26.3), then:
   - **1 Play**: launches Minecraft with Justaclient, Fabric API and Sodium already loaded. The first launch downloads
     Minecraft and takes a few minutes; later launches are quick. It runs offline as a dev player, so use singleplayer.
   - **2 Build**: makes the mod jar for your normal launcher (`build\libs\` for 1.21.11, `mc26.3\build\libs\` for 26.3).
3. In game (or on the title screen), press **Right Shift** to open the Justaclient menu. Hold **C** to zoom.

macOS / Linux: `./start.sh` plays 1.21.11, `./start.sh 26.3` plays 26.3; add `build` to build instead
(`./start.sh 26.3 build`).

## Install into your own launcher

1. Build with `start.bat` option 2 (or `gradlew build` for 1.21.11, `gradlew -p mc26.3 build` for 26.3).
2. Copy `justaclient-<version>+<minecraft version>.jar` (not the `-sources` jar) into your `.minecraft/mods` folder.
3. Also put in `mods`: [Fabric API](https://modrinth.com/mod/fabric-api) and [Sodium](https://modrinth.com/mod/sodium)
   for the same Minecraft version, and run that version's Fabric profile.

## Using it

| Where | What to do |
| --- | --- |
| **Right Shift** | Opens the menu: categories on the left, features in the middle, settings on the right. |
| Typing in the menu | Searches every feature and setting. Esc clears the search, Esc again closes. |
| Feature cards | Click to see settings, click the switch (or right click the card) to turn it on or off. |
| **Toggle key** row | Click it and press any key to toggle that feature in game. Esc or Backspace unbinds. |
| **Profile** (bottom left) | Switch profiles, copy the current one, copy it to the clipboard to share, paste one a friend sent, delete it, or open the profiles folder. |
| **Edit HUD layout** | Drag widgets; they snap to edges, the centre and each other (hold Shift to place freely). Scroll to resize, right click for settings. |
| **Theme** | Accent, panel opacity, corners, blur, outline, font and motion. Everything follows these. |
| **Performance** (menu, or click the frame cost in the footer) | What each feature costs per frame, against the 5% budget. |
| **C** (hold) | Zoom. Scroll while zooming to go further. The key is in Camera > Zoom. |
| **I** | Inspect the held item (First person > Hand). |
| **M** | Open the map. Drag (or arrow keys) to move, scroll to zoom, Space to find yourself, right-click to drop a waypoint, and click one to rename, move, recolour, track, hide or delete it. |
| **Stats** (menu sidebar) | Lifetime play time, kills, deaths, blocks and records. |
| **T** (chat) over the Spotify card | Click its previous, play and next buttons. With the chat open you can also grab and throw the physics trinket. |
| **B** | Save a waypoint where you stand. `/jwp list`, `/jwp gps <name>`, `/jwp remove <name>` manage them too. |
| **Middle click** a player | Add or remove a friend. `/jfriend add <name>` and `/jfriend list` work too. |

![HUD widgets in game](docs/screenshots/hud.png)

![HUD editor with a snap guide](docs/screenshots/hud-editor.png)

## What is in this build

### Fixes (v0.7.1 and v0.7.2)

- **Spotify web player**: on Windows the Spotify card now finds the web player playing in a browser (Chrome, Edge,
  Firefox, Brave, Opera and others) with "Spotify only" on, not just the desktop app.
- **Arrow hits**: hits from your own arrows, tridents and other projectiles now trigger hit particles, bubbles, sounds,
  the crosshair pulse, the target HUD and kill effects, the same as a melee hit.
- **Map memory**: the minimap and the M map remember terrain from every chunk you have had loaded, per world and
  dimension, and keep it between sessions (`config/justaclient/maps/`). Places you have left stay on the map.
- **Players and mobs on the maps**: the minimap and the M map show mobs around you (red hostile, amber neutral, green
  passive) and other players (as their face) while they are in your line of sight. Point at one on the M map, or on
  the minimap with the chat open, for its name, health and distance.
- **Sky lyrics and album art**: lyrics are fetched again when you switch them on mid-song, and a failed lrclib.net
  lookup is retried instead of being remembered as "no lyrics" for a day. Players that report no timeline (some
  browsers) get a clock started at the track change. The Windows helper keeps trying for the cover when a player
  sends it late, which the web player does.
- **Sky-only mode**: Spotify > "Show the card" off hides the HUD card while the sky keeps the lyrics, plus the album
  cover and song name ("Cover and song name in the sky").

### M6: the rest of the feature list (v0.7.0)

![Spotify card with the album cover, and the current lyric drawn in the sky](docs/screenshots/spotify.png)

- **Spotify card**: album cover (rounded, with a blurred copy of the cover behind the card), title and artist, a
  progress bar, previous / play / next buttons, an equaliser while playing, and time-synced lyrics from
  [lrclib.net](https://lrclib.net) that fill in as they are sung. The card takes its colours from the cover. Open the
  chat to click the buttons, or bind media keys in its settings. Works with the Spotify app on Windows (system media
  controls, which also covers the Spotify web player in a browser), macOS and Linux (`playerctl`); turn off "Spotify
  only" to follow any player, such as a YouTube tab.
- **Lyrics in the sky**: the current line (or drifting music notes) drawn high in the sky where you were looking when
  the song started (Spotify > In the sky).
- **World**: time of day (presets or any time, plus moon phase), light colour (tint block light and sky light), water
  colour presets, clean world (hide falling blocks, fire on entities, rain and snow), end crystal size, spin, bob and a
  glow ring, and restyled F3 + B hitboxes (same information as vanilla).
- **Combat**: hit bubbles and a slash trail on swings. Four more hand styles: glass, smoke, snow and stripes.
- **Cosmetics**: hats (crown, top hat, cone, beanie) in your accent colour, and a client-side skin swap from a 64 x 64
  PNG in `config/justaclient/skins/`. Only you see them.

![Title screen with an animated GIF background](docs/screenshots/title-gif.png)

- **Your own title background**: put PNG, JPG or animated GIF files in `config/justaclient/backgrounds/` and pick
  "Your picture or GIF" under Utility > Title screen. Optional soft blur and darkening.
- **Stats page** (menu sidebar): lifetime play time with a two-week chart, kills, deaths, K/D, blocks mined, distance,
  diamonds, ancient debris and a few records, saved in `config/justaclient/stats.json`.

![Stats page](docs/screenshots/stats.png)

- **Block-found toasts**: a toast when you mine (or look at) diamonds, ancient debris, emeralds, spawners and more.
  Only blocks already on your screen; nothing is scanned through walls.
- **Physics trinket** (HUD): an item that falls, bounces, swings as you turn, and can be grabbed and thrown with the
  chat open.

![Grabbing the trinket, then throwing it](docs/screenshots/trinket.png)

- **Performance mode** (Utility): no blur behind HUD widgets, lighter menu blur, a quarter of the effect particles,
  smaller background pictures, optional pause of screen shaders, and blur buffers freed when nothing uses them.
- **Tier badges** (Utility): PvP tiers from the public MC Tiers list (for example `HT3`) in name tags and the tab list,
  best tier or one game mode. The API address is a setting, since MC Tiers has moved API versions before.
- **Map polish**: the map glides when you zoom or jump to a waypoint, shows chunk lines up close, draws a dotted route
  to the selected waypoint with its distance, and has a find-me button (or Space). Arrow keys pan.

![Map with a route to the selected waypoint](docs/screenshots/map.png)

On 26.3 everything above works the same; light colour and time of day hook 26.3's new lightmap and world clocks.

![26.3: crown and custom skin in third person](docs/screenshots/hats-26.3.png)

### M5: utility and cosmetics

![Minimap, GPS and session stats](docs/screenshots/minimap.png)

- **Waypoints**: light beams and labels with distance, an automatic death point, saved per world or server in
  `config/justaclient/waypoints.json`. Press B or use `/jwp add <name>`; `/jwp set <name> <x> <y> <z>` for coordinates.
- **GPS**: an arrow, name and distance for the waypoint you track (`/jwp gps <name>`, or "track nearest").
- **Minimap**: terrain only, coloured like vanilla maps, north-up or rotating, with waypoint dots. No players, mobs or
  caves; in the Nether it maps the floor you stand on.
- **Map (M)**: a full-screen version of the minimap over every loaded chunk, with this world's waypoints beside it.
  Right-click the map to add one; pick one to edit its name, coordinates and colour, track it, hide it or delete it.

- **Friends**: friends' name tags and tab list entries in your friend colour.
- **Stream mode**: one switch that hides your name, friends' names, coordinates and the server address on your screen.
- **Toggle sprint**, **auto reconnect** (a countdown button on the disconnect screen) and a **session stats** widget
  (play time, kills, deaths, K/D, blocks mined, distance).
- **Cosmetics**: a halo and orbs on springs around you in third person. Only you see them.

![Halo and orbs in third person](docs/screenshots/cosmetics.png)
- **Discord status**: shows singleplayer, server and dimension on your profile. Create an application at
  discord.com/developers (its name is what Discord shows as the game) and paste its Application ID into the setting.

### M4: HUD and UI polish

![Glass boss bar, scoreboard, consumables and fireflies](docs/screenshots/hud-m4.png)

- **New widgets**: watermark, module list (sliding, gradient), keybinds, inventory and consumables (totems, pearls,
  gapples, food, arrows, wind charges).
- **Low health glow** at the screen edges, pulsing below a threshold you pick.
- **Boss bars and scoreboard** on glass panels with smooth bars.
- **Loading screen**: a glass card with a spinner and progress bar over the blurred panorama.
- **Ambient particles**: fireflies, drifting motes or linked dots around you.
- **Camera**: aspect ratio (4:3, 5:4, 16:10 or custom) and a separate hand FOV.
- **Weather toasts** and a **borderless window** option.

### M3: extras

![Hand glow, graded colours and name protect in chat](docs/screenshots/hand-glow.png)

- **Color grading**: exposure, contrast, saturation, vibrance, temperature and tint with presets, plus a vignette.
- **Motion blur**, frame-rate independent.
- **Hand**: position, rotation, scale, swing style and speed, no equip dip, and an item **inspect** animation (I).
- **Hand glow**: a soft glow and gradient, chroma, outline or invert styles on your hand only. Off while a shader pack
  is on.
- **Enchant glint colour and speed**, separately for items and armour.
- **Overlays**: hide the pumpkin blur, powder snow and nausea wobble.
- **Name protect**: show another name in place of yours everywhere on your screen.

### M2: release

![Title screen](docs/screenshots/title.png)

![Kill effect, target HUD and the new hotbar](docs/screenshots/combat.png)

- **Title screen**: wordmark, a glass menu with keyboard navigation, your account and skin, and a blurred panorama,
  plain panorama or animated gradient behind it (Utility > Title screen). Turn it off for vanilla's.
- **Hotbar**: a glass hotbar with a sliding selection, offhand slot and attack indicator, plus smooth health (with a
  damage ghost, absorption and low-health pulse), hunger, armor, air and XP bars. Spectator mode keeps vanilla's.
- **Item overlay**: smooth or accent durability bars, cooldown rings, and sharp stack counts in every inventory.
- **Target HUD**: face, name and health of whatever you hit, with a damage ghost on the bar.
- **Combat feel** (all cosmetic, driven only by what vanilla already shows): hit particles, hit sounds, kill effects
  (ring burst, rising soul or shatter), totem pop effects with a pop counter, and a ring or orbit marker under your
  target.
- **Sky**: twinkling extra stars, an aurora over the north and shooting stars at night, plus an optional day sky tint.
  Overworld only, and drawn behind everything like vanilla's sky.
- **Fog**: push air fog further out and recolour it per dimension. Water, lava, blindness and darkness are untouched.
- **Trails and jump circles**: a ribbon or dotted trail behind you (and optionally other players), and rings when you
  jump or land.
- **Profiles**: copy a profile to the clipboard and paste one from a friend; it arrives as a new profile.
- **Performance page**: CPU time per feature (average, peak and share of the frame) against a 5% budget. GPU time is
  not included; compare FPS with a feature on and off for that.

The world effects (sky, fog, trails, jump circles, hit sounds) start off; turn them on under World and Combat feel.

![Aurora, stars and a trail at night](docs/screenshots/night.png)

![Performance page](docs/screenshots/performance.png)

### M1: first playable

- **Click GUI** with categories, live search, animated switches, sliders, choices, colour pickers and key binders.
  Every feature has a toggle key.
- **Profiles**: each one is a JSON file in `config/justaclient/profiles/` (features, settings, keys and HUD layout), so
  they can be shared by copying the file. The theme is separate and stays in `config/justaclient.json`.
- **HUD editor** with snapping, guides, per-widget scale, and a tray to switch hidden widgets back on.
- **HUD widgets**: player info (FPS, ping, CPS, speed), coordinates (nether conversion, facing, biome, clock), armor
  durability (bar, percent or number), potion effects with timer rings, item cooldown dials, and toggle notifications.
- **Custom crosshair**: cross, dot, circle or plus; size, gap, thickness, colour, outline, hit pulse, target colour and
  dynamic spread. Follows vanilla's attack indicator setting.
- **Zoom**: smooth, scroll to adjust, slower mouse while zoomed, optional cinematic camera.
- **Block highlight**: an outline and fill that glide between blocks and show mining progress. Depth tested, never
  see-through.
- **Fire overlay**: lower it or hide it.
- **Copy coordinates** to the clipboard with one key.
- **Icons**: a Lucide icon set drawn with the same SDF text renderer, tinted by the accent.
- **Frame cost** in the menu footer: CPU time spent in Justaclient's HUD and world hooks as a share of the frame.

### M0: foundation

- **Render kit**: anti-aliased rounded rectangles, outlines, gradients, soft shadows, glow and ring arcs, drawn with
  signed distance field shaders and batched by Minecraft's GUI renderer.
- **Frosted glass**: a dual Kawase blur of the world (or title panorama) behind panels, done at half resolution and
  only on frames that show a glass panel.
- **Sharp text**: Inter rendered from multi-channel signed distance field atlases, so it stays crisp at every GUI scale.
- **Animation library**: easing curves, timed animations and springs, all scaled by the Motion setting.
- **Style tokens**: accent (solid, gradient or rainbow), surface colour and opacity, blur, corner radius, outline,
  font and motion. Default look is dark glass: blue accent `#4C8DFF`, 70% surface, 6 px corners.

## Project layout

| Path | What it holds |
| --- | --- |
| `src/main/java/dev/justaclient/render/` | Shape kit (`Draw`), blur (`BlurEngine`), pipelines, world effects (`WorldFx`) |
| `src/main/java/dev/justaclient/render/text/` | MSDF font loader and text renderer (`JacText`) |
| `src/main/java/dev/justaclient/anim/` | `Easing`, `Animation`, `Spring`, `Colors` |
| `src/main/java/dev/justaclient/config/` | Style tokens and JSON config |
| `src/main/java/dev/justaclient/feature/` | Feature and setting framework, the feature list, non-HUD features (`impl/`) |
| `src/main/java/dev/justaclient/hud/` | HUD renderer, widget base classes and the widgets (`widgets/`), session and lifetime stats |
| `src/main/java/dev/justaclient/media/` | Now playing: Spotify / media sources per OS, album art, synced lyrics |
| `src/main/java/dev/justaclient/profile/` | Profile files |
| `src/main/java/dev/justaclient/waypoint/` | Waypoint storage |
| `src/main/java/dev/justaclient/command/` | Client commands (`/jwp`, `/jfriend`) |
| `src/main/java/dev/justaclient/ui/` | Click GUI, HUD editor, theme and performance pages, title screen, controls |
| `src/main/java/dev/justaclient/mixin/` | Small hooks: zoom FOV and mouse, CPS, fire overlay, hit pulse, blur capture, combat events, item decorations, sky, fog, title screen |
| `src/main/resources/assets/justaclient/shaders/` | GLSL for shapes, glass, text, blur, world effects and the aurora |
| `tools/fonts/` | Script that regenerates the font and icon atlases (`icons.txt` lists the icons) |

## Development

- `gradlew runClient` launches the dev client; `gradlew build` makes the jar.
- `gradlew genSources` decompiles Minecraft (official Mojang names) for reading.
- 1.21.11: Java 21+, Fabric Loom 1.16, official Mojang mappings.

### The 26.3 build (`mc26.3/`)

Minecraft 26.x is unobfuscated and replaced the renderer (the new `renderpearl` GPU API, with Vulkan or OpenGL),
windowing and input (SDL instead of GLFW) and the GUI (`GuiGraphicsExtractor`). Rather than bend one source tree
around both, `mc26.3/` is its own Gradle build with a ported copy of the Java sources:

- `gradlew -p mc26.3 runClient` / `gradlew -p mc26.3 build`. Needs Java 25 (Loom 1.19, Fabric API 0.165, Sodium 0.9.2).
- Fonts, icons, textures and lang files come from the root `src/main/resources`. Files in `mc26.3/src/main/resources`
  win over the root ones: `fabric.mod.json`, the mixin config and the shaders (26.x needs `layout(location)` on every
  shader input and output, and `#include` instead of `#moj_import`).
- A change to a feature usually needs making in both trees. The render code differs most: `render/` (pipelines,
  post FX, hand glow, blur, world effects) and the mixins are rewritten for 26.3, most of the rest is the same with
  renamed Minecraft classes.
- Release jars are named `justaclient-<version>+<minecraft version>.jar`. CI builds both.

## Credits

- [Inter](https://rsms.me/inter/) by Rasmus Andersson, SIL Open Font License 1.1 (`assets/justaclient/msdf/Inter-LICENSE.txt`).
- [Lucide](https://lucide.dev/) icons, ISC License (`assets/justaclient/msdf/Lucide-LICENSE.txt`).
- Dual Kawase blur after Marius Bjørge, "Bandwidth-Efficient Rendering", SIGGRAPH 2015.

## License

MIT, see `LICENSE`.
