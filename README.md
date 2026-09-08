# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.36-hall`
- Source version: `0.36.0` (New pad stale-sample age-out while owners live, on the v0.35 motion-corr tip)
- File: `VastHall-v0-foss.apk` (509602 bytes, md5 `2e23eae476f735cb54f26fc6dc45cc83`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.36-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.36-hall/VastHall-v0-foss.apk

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Controls (Menu → Settings): **Legacy touch**, **Legacy pad**, and **New pad**. New pad ages out identical/stale samples while owners stay live (v0.35 dump class). Legacy touch and Legacy pad keep their existing backends.

Debug sticky repro: Menu → Debug → **Debug ON**, reproduce circle latch, then Menu → Debug → **Share dump** (includes `[MOTION_CORR]` + `[LATCH_SUMMARY]`; ring freezes on identical-sample + climbing `jniLag`).
