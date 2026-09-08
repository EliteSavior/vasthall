# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.34-hall`
- Source version: `0.34.0` (New pad focus ownership + CANCEL JNI zero flush + tick sample timeout on the v0.33 Flat router / three control schemes)
- File: `VastHall-v0-foss.apk` (479598 bytes, md5 `e2d721ad54e185200f94793a1da7e717`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.34-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.34-hall/VastHall-v0-foss.apk

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Controls (Menu → Settings): **Legacy touch**, **Legacy pad**, and **New pad**. New pad uses exclusive pointer-focus ownership, CANCEL-complete JNI zero flush, and tick sample timeout/decay. Legacy touch and Legacy pad keep their existing backends.
