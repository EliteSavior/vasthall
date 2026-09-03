# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.13-hall`
- File: `VastHall-v0-foss.apk` (240827 bytes, md5 `f4e7c4f7ddf03ca77558db89481cfc09`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.13-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.13-hall/VastHall-v0-foss.apk

```bash
adb uninstall com.elitesavior.vasthall
adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Signing note: this binary-only drop does not contain the private debug key used through v0.12. Android therefore requires a clean install when replacing v0.12 or older.
