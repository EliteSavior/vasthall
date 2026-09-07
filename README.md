# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Source version: `0.30.0` (Input Action mapping + Collision/overlaps + UMG-lite Widget viewport + AudioManager + SaveGame slots + Gameplay events/delegates + TimerManager on GameInstance/World + GameMode + Scene/Actor/Level/Component/Asset/Console; see [ENGINE.md](ENGINE.md))
- Last published APK: tag `v0.16-hall`, file `VastHall-v0-foss.apk` (269007 bytes, md5 `2621ce8ad3f67372a687c594abae88c4`)

## Download

The last uploaded binary is still [v0.16-hall](https://github.com/EliteSavior/vasthall/releases/tag/v0.16-hall). This branch is the 0.30 source; it needs a signed `assembleFoss` build before a new release asset exists.

Direct (v0.16): https://github.com/EliteSavior/vasthall/releases/download/v0.16-hall/VastHall-v0-foss.apk

```bash
adb uninstall com.elitesavior.vasthall
adb install VastHall-v0-foss.apk
```

Signing note: this binary-only drop does not contain the private debug key used through v0.15. Android therefore requires a clean install when replacing v0.15 or older.

## Build this source

```bash
./gradlew :app:testFossDebugUnitTest
./gradlew :app:assembleFoss
adb uninstall com.elitesavior.vasthall
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Play start creates a `GameInstance` (owns World, Asset Registry, Console, TimerManager, EventDispatcher, AudioManager, WidgetViewport, CollisionWorld, InputSubsystem, SaveGameSystem), `init()`s it, then `openLevel("Hall")` from `levels/Hall.json` which installs `HallGameMode`: a `PlayerPawn` (Java handle for the native avatar) and a ticking `HallBeacon` that resolves `/Game/Textures/HallBeacon`. Both carry a `TagComponent` and a `CollisionComponent` box. HallGameMode also sets a 0.25s delayed-start timer, binds actor-spawn / level-unload events, adds a sample `HallTitle` text widget (`HALL`) to the viewport, and binds Jump / Move / Look on the local PlayerController. Save slots live under `filesDir/SaveGames`. `HallAmbience` is registered as an `AUDIO` asset; the AudioManager does not auto-play it. `DefaultMapping` is the Enhanced Input–lite map (`input/DefaultMapping.json`). The top-center `SCENE Hall mode=HallGameMode actors=… comps=… assets=… timers=… events=… saves=… audio=… widgets=… overlaps=… actions=…` line is the GameInstance/GameMode/TimerManager/Event/SaveGame/Audio/Widget/Collision/Input/Scene layer; it is not a stick-lockup change.

fossDebug builds show a `~` button on the play HUD (and Menu → Debug → Console). Type `help`, `actors`, `assets`, `settimer 1 once hello`, `timers`, `events`, `SaveGame Slot0`, `LoadGame Slot0`, `PlaySound HallAmbience`, `audio`, `StopSound HallAmbience`, `CreateWidget Text Hint Hello`, `AddToViewport Hint`, `widgets`, `ListOverlaps`, `input`, `load Hall`, `unload Hall`, or `open Hall`. fossRelease omits the console overlay (debug/release source-set gate).
