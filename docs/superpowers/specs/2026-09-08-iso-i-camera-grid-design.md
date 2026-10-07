# Iso I — Camera & Grid

Date: 2026-09-08
Status: accepted (authoritative product spec)
Base: Vast Hall `1df8a41131a433109ce1cb9a324c2fae695e4ed9` (v0.36 New pad freshness)

## Goal

Developer FOSS APK: open **Iso Sandbox** GameMode, see a true isometric ground **grid**, **pan** (one-finger drag) and **zoom** (pinch and/or +/-). No character, combat, dual viewport, town/dungeon, or twin-stick.

Owner device-tests. This version must not claim a device playtest.

## Out of scope (later roadmap — do not implement)

- Iso II: character
- Iso III: select-command
- Iso IV: dual viewport
- Thin C
- Sticky twin-stick work (tabled). Do not modify those pipelines except to **disable** pads/sticks while Iso Sandbox is active.

## Success criteria

1. Enter Iso Sandbox from the existing in-app Menu.
2. Iso grid is visible; pan and zoom are usable.
3. No character, combat, or split viewport in this mode.
4. Hall mode still works after travel back (and as the default play start).
5. APK + README shipped: GitHub Release `v0.37-hall` with exactly `VastHall-v0-foss.apk`; main README Download/Direct point only at that release.
6. `./gradlew :app:testFossDebugUnitTest` green.
7. Version bumped past 0.36 (0.37.0 / versionCode 37).

## Architecture

### 1. GameMode isolation

New GameMode `IsoSandboxGameMode` in package `com.elitesavior.vasthall.iso` (clean `iso/` folder). Selectable from the existing Menu. Must not break Hall mode.

- New level resource `levels/IsoSandbox.json` with `"gameMode": "IsoSandboxGameMode"` and **no actors**.
- Register the short name in `GameModeTypes` and the level in `AssetRegistry.registerDemoAssets()`.
- `defaultPawnClass()` returns `null` so `GameMode.startPlay` does not spawn a `PlayerPawn`.
- Do not bind Jump / Move / Look.
- Optional origin marker is drawn on the grid overlay (not an actor / character).
- Play start remains `openLevel("Hall")`. Menu **Iso Sandbox** calls `openLevel("IsoSandbox")`; Menu **Hall** returns via `openLevel("Hall")`. Console `open IsoSandbox` / `open Hall` must also switch presentation (sync from current GameMode each frame).

### 2. Fixed isometric camera (orthographic)

Pure-Java `IsoCamera` (unit-tested). The closed native `libvasthall.so` has no isometric / orthographic JNI (Hall is a Vulkan first-person-style view). Iso I therefore:

- Keeps the existing GL/native `SurfaceView` pipeline alive (Hall reuse; no native rewrite).
- Presents Iso Sandbox as an **opaque orthographic overlay** composited in the same Activity over that surface so the Hall avatar/world is not visible in this mode.
- Camera is fixed isometric (yaw 45°, pitch `arcsin(1/√2)` ≈ 35.264°), **orthographic** (no perspective).
- Pan moves **look-at on the ground plane** (Y=0). Zoom is a scale clamp.

World axes match the engine: **Y up**, X/Z on the ground. Projection (true isometric):

```
isoX = (x - z) * cos(30°)
isoY = (x + z) * sin(30°) - y
screenX = viewportW/2 + (isoX - lookIsoX) * PIXELS_PER_UNIT * zoom
screenY = viewportH/2 - (isoY - lookIsoY) * PIXELS_PER_UNIT * zoom
```

`lookIso*` is the isometric projection of look-at `(lookAtX, 0, lookAtZ)`.

Defaults:

| Parameter | Value |
| --- | --- |
| Grid | 32 × 32 tiles, cell size 1 world unit, origin at (0,0,0), extents X/Z `[0, 32]` |
| Look-at | grid center `(16, 0, 16)` |
| Zoom | default `1.0`, clamp `[0.35, 3.0]` |
| Pan clamp | look-at X/Z in `[-8, 40]` (grid plus margin) |
| Pixels per unit at zoom 1 | `48` |

Pan: grab-the-map. Finger drag keeps the ground point under the pointer. Pinch and +/- zoom about the pinch midpoint or viewport center, then re-clamp.

### 3. Visible tile grid

`IsoGrid` (32×32 default) emits ground-plane line segments: 33 lines along X and 33 along Z. `IsoGridView` draws them (Canvas overlay, minimal art: dark ground, light grid lines, distinct origin marker at (0,0,0) — diamond/cross, not a character).

### 4. Mode-local touch

`IsoGestures` is a pure-Java pointer state machine. **No** StickView / Flat pad / New pad requirement.

| Input | Action |
| --- | --- |
| One-finger drag | pan look-at on ground |
| Two-finger pinch | zoom (clamped) about midpoint |
| Hardware `+` / `=` / numpad add | zoom in |
| Hardware `-` / numpad sub | zoom out |

While Iso Sandbox is active:

- Hide PlayHud (legacy sticks) and FlatPadOverlay.
- Zero and do not feed Hall JNI axes from those pads.
- `IsoGridView` consumes play-surface touches (Menu / Settings / Debug / Console overlays stay above it).
- Do not change sticky-control math; only disable those HUDs while this mode is live.

### 5. Renderer reuse

Reuse the existing Activity compositor: native `SurfaceView` + Java overlays. Iso I does not add a second game engine, a second native library, or a dual viewport. Hall keeps `libvasthall.so`. Iso Sandbox is the overlay camera/grid described above.

## Tests

Unit tests (no device):

- Grid math: world↔screen round-trip on Y=0; line count; origin at a known pixel when look-at and viewport are fixed.
- Pan/zoom clamps: zoom cannot leave `[MIN, MAX]`; look-at cannot leave pan bounds; pan by screen delta moves look-at on the ground plane.
- GameMode: `openLevel("IsoSandbox")` installs `IsoSandboxGameMode`, zero actors / no pawn; `openLevel("Hall")` still installs `HallGameMode` with `PlayerPawn`.
- Gestures: one-finger drag pans; pinch scale zooms; +/- helpers clamp.

Command: `./gradlew :app:testFossDebugUnitTest`

## Ship

1. Bump `versionName` to `0.37.0`, `versionCode` to `37`.
2. `./gradlew :app:assembleFoss` (ship fossDebug as `VastHall-v0-foss.apk`, same approach as v0.36-hall).
3. GitHub Release `v0.37-hall` with **exactly** that filename; notes include md5, size, commit, clean-install cmd. Do not claim device playtest.
4. Point **main** README Download/Direct only at that release. Document Menu path and pan/zoom how-to.
5. PR body repeats: how to open Iso Sandbox; pan/zoom how-to.

## How to open the mode (player-facing)

1. Launch Vast Hall (starts in Hall).
2. Tap **Menu** (top-left).
3. Tap **Iso Sandbox**.
4. Drag one finger to pan; pinch to zoom; `+`/`-` also zoom.
5. **Menu → Hall** returns to Hall mode.
