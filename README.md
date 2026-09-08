# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.35-hall`
- Source version: `0.35.0` (debug HUD + MOTION_CORR flight recorder on the v0.34 New pad tip; no stick-ownership change; see [ENGINE.md](ENGINE.md))
- File: `VastHall-v0-foss.apk` (504494 bytes, md5 `6124f7635edc886ef4c719502c8f2710`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.35-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.35-hall/VastHall-v0-foss.apk

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Controls (Menu → Settings): **Legacy touch**, **Legacy pad**, and **New pad**. New pad is the Flat router with exclusive pointer-focus ownership (one `ownerPointerId` per Move/Look/Jump), CANCEL-complete JNI zero flush, and tick sample timeout/decay. Legacy touch and Legacy pad keep their existing backends. v0.35 does not change those algorithms; it adds input↔motion debug so a sticky-circle repro can be shared as evidence.

## Debug overlay and sticky repro dump

Menu → **Debug** → enable **Debug ON** (master). Leave Controls / Camera / Input / Engine / Lifecycle checked. Resume play. A landscape HUD appears:

- `own M/L/J` — live `ownerPointerId` for move / look / jump (`-1` = none)
- `L` / `R` — stick vectors, magnitudes, `sampleAgeMs`, last `whoZeroed`
- `pub` vs `con` — last HudAxes publish vs last native consume, plus `jniLagMs`
- `pawn v` / `dYaw` / `loc` — consumed-axis integrate (native loc is not JNI-exported; `pawnSrc=consumed_integrate`)
- `cmdH` / `actH` / `err` — commanded move heading vs actual (consumed) heading, degrees
- **FLAGS** — `INPUT_NONZERO_MOTION_ZERO`, `INPUT_ZERO_MOTION_NONZERO` (classic latch class), `PUBLISH_NE_CONSUME`, `STALE_SAMPLE`
- Cyan vs amber arrows: commanded move vs consumed move; look stick vs dYaw/dPitch
- Sparklines: inputMagL / velMag / lookRate (~2–3 s)

To capture a sticky-circle dump: turn Debug ON, reproduce the circular multitouch latch, then Menu → Debug → **Share dump** (or Copy dump). If `INPUT_ZERO_MOTION_NONZERO` holds >200 ms (or stale axes >150 ms) the ring is frozen automatically and `[LOCKUP]` gets `why=MOTION_WITHOUT_INPUT` or `STALE_AXIS`. The share text keeps `[CONTROLS]/[CAMERA]/[INPUT_LOG]` and adds `[MOTION_CORR]` (CSV ring + headings) and `[LATCH_SUMMARY]`.

## Build this source

```bash
./gradlew :app:testFossDebugUnitTest
./gradlew :app:assembleFoss
adb uninstall com.elitesavior.vasthall
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Play start creates a `GameInstance` (owns World, Asset Registry, Console, TimerManager, EventDispatcher, AudioManager, WidgetViewport, CollisionWorld, InputSubsystem, SaveGameSystem), `init()`s it, then `openLevel("Hall")` from `levels/Hall.json` which installs `HallGameMode`: a `PlayerPawn` (Java handle for the native avatar) and a ticking `HallBeacon` that resolves `/Game/Textures/HallBeacon`. Both carry a `TagComponent`, a `CollisionComponent` box, and a `GameplayTagContainer` (`Character.Player` / `World.Landmark.Beacon`). HallGameMode also sets a 0.25s delayed-start timer, binds actor-spawn / level-unload events, adds a sample `HallTitle` text widget (`HALL`) to the viewport, and binds Jump / Move / Look on the local PlayerController. Save slots live under `filesDir/SaveGames`. `HallAmbience` is registered as an `AUDIO` asset; the AudioManager does not auto-play it. `DefaultMapping` is the Enhanced Input–lite map (`input/DefaultMapping.json`) and a DataAsset. `HallBlade` is the sample `WeaponDataAsset` at `/Game/Data/HallBlade` (`Item.Weapon.Melee`). The top-center `SCENE Hall mode=HallGameMode actors=… comps=… assets=… timers=… events=… saves=… audio=… widgets=… overlaps=… actions=…` line is the GameInstance/GameMode/TimerManager/Event/SaveGame/Audio/Widget/Collision/Input/Scene layer; it is not a stick-lockup change.

fossDebug builds show a `~` button on the play HUD (and Menu → Debug → Console). Type `help`, `actors`, `assets`, `settimer 1 once hello`, `timers`, `events`, `SaveGame Slot0`, `LoadGame Slot0`, `PlaySound HallAmbience`, `audio`, `StopSound HallAmbience`, `CreateWidget Text Hint Hello`, `AddToViewport Hint`, `widgets`, `ListOverlaps`, `input`, `load Hall`, `unload Hall`, or `open Hall`. fossRelease omits the console overlay (debug/release source-set gate).
