# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.35-hall`
- Source version: `0.35.0` (debug HUD + MOTION_CORR flight recorder on the v0.34 New pad tip; no stick-ownership change)
- File: `VastHall-v0-foss.apk` (504942 bytes, md5 `aedc258f6a416631acaeb330c2b7b48c`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.35-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.35-hall/VastHall-v0-foss.apk

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Controls (Menu → Settings): **Legacy touch**, **Legacy pad**, and **New pad**. New pad uses exclusive pointer-focus ownership, CANCEL-complete JNI zero flush, and tick sample timeout/decay. Legacy touch and Legacy pad keep their existing backends.

Debug: Menu → Debug → **Debug ON**, resume, reproduce sticky circles, then **Share dump**. Overlay correlates sticks vs published/consumed JNI vs proxy pawn/camera. Dump adds `[MOTION_CORR]` and `[LATCH_SUMMARY]`. This drop does not change stick ownership.
