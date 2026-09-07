# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Source version: `0.20.0` (Actor Components on Scene/World + Actor + Level; see [ENGINE.md](ENGINE.md))
- Last published APK: tag `v0.16-hall`, file `VastHall-v0-foss.apk` (269007 bytes, md5 `2621ce8ad3f67372a687c594abae88c4`)

## Download

The last uploaded binary is still [v0.16-hall](https://github.com/EliteSavior/vasthall/releases/tag/v0.16-hall). This branch is the 0.20 source; it needs a signed `assembleFoss` build before a new release asset exists.

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

Play start `openLevel("Hall")` from `levels/Hall.json`: a `PlayerPawn` (Java handle for the native avatar) and a ticking `HallBeacon`. Both carry a `TagComponent`. The top-center `SCENE Hall actors=… comps=…` line is the Scene/Level/Component layer; it is not a control change.
