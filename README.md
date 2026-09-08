# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.35-hall`
- Source version: `0.35.0` (motion-correlation debug HUD + MOTION_CORR flight recorder on v0.34 New pad tip; no stick-ownership change)
- File: `VastHall-v0-foss.apk` (504942 bytes, md5 `aedc258f6a416631acaeb330c2b7b48c`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.35-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.35-hall/VastHall-v0-foss.apk

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Controls (Menu → Settings): **Legacy touch**, **Legacy pad**, and **New pad**.

Debug sticky repro: Menu → Debug → **Debug ON**, reproduce circle latch, then Menu → Debug → **Share dump** (includes `[MOTION_CORR]` + `[LATCH_SUMMARY]`).
