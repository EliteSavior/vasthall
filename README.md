# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.32-hall`
- Source version: `0.32.0` (GameplayTags + DataAssets + Input Action mapping + Collision/overlaps + UMG-lite Widget viewport + AudioManager + SaveGame slots + Gameplay events/delegates + TimerManager on GameInstance/World + GameMode + Scene/Actor/Level/Component/Asset/Console; see [ENGINE.md](ENGINE.md))
- File: `VastHall-v0-foss.apk` (461570 bytes, md5 `606d930dc0d9a4e8382b6870fd2a132d`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.32-hall). This binary is `assembleFoss` from the gameplay-tags / PR #22 tip (engine stages through DataAssets / Tags).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.32-hall/VastHall-v0-foss.apk

```bash
adb uninstall com.elitesavior.vasthall
adb install VastHall-v0-foss.apk
```

Signing note: this binary-only drop does not contain the private debug key used through v0.16. Android therefore requires a clean install when replacing v0.16 or older.

## Build this source

```bash
./gradlew :app:testFossDebugUnitTest
./gradlew :app:assembleFoss
adb uninstall com.elitesavior.vasthall
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Play start creates a `GameInstance` (owns World, Asset Registry, Console, TimerManager, EventDispatcher, AudioManager, WidgetViewport, CollisionWorld, InputSubsystem, SaveGameSystem), `init()`s it, then `openLevel("Hall")` from `levels/Hall.json` which installs `HallGameMode`: a `PlayerPawn` (Java handle for the native avatar) and a ticking `HallBeacon` that resolves `/Game/Textures/HallBeacon`. Both carry a `TagComponent`, a `CollisionComponent` box, and a `GameplayTagContainer` (`Character.Player` / `World.Landmark.Beacon`). HallGameMode also sets a 0.25s delayed-start timer, binds actor-spawn / level-unload events, adds a sample `HallTitle` text widget (`HALL`) to the viewport, and binds Jump / Move / Look on the local PlayerController. Save slots live under `filesDir/SaveGames`. `HallAmbience` is registered as an `AUDIO` asset; the AudioManager does not auto-play it. `DefaultMapping` is the Enhanced Input–lite map (`input/DefaultMapping.json`) and a DataAsset. `HallBlade` is the sample `WeaponDataAsset` at `/Game/Data/HallBlade` (`Item.Weapon.Melee`). The top-center `SCENE Hall mode=HallGameMode actors=… comps=… assets=… timers=… events=… saves=… audio=… widgets=… overlaps=… actions=…` line is the GameInstance/GameMode/TimerManager/Event/SaveGame/Audio/Widget/Collision/Input/Scene layer; it is not a stick-lockup change.

fossDebug builds show a `~` button on the play HUD (and Menu → Debug → Console). Type `help`, `actors`, `assets`, `settimer 1 once hello`, `timers`, `events`, `SaveGame Slot0`, `LoadGame Slot0`, `PlaySound HallAmbience`, `audio`, `StopSound HallAmbience`, `CreateWidget Text Hint Hello`, `AddToViewport Hint`, `widgets`, `ListOverlaps`, `input`, `load Hall`, `unload Hall`, or `open Hall`. fossRelease omits the console overlay (debug/release source-set gate).
