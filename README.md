# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.14-hall`
- File: `VastHall-v0-foss.apk` (263299 bytes, md5 `ea48ac9135d72df82562010c9a467a6f`)

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.14-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.14-hall/VastHall-v0-foss.apk

```bash
adb uninstall com.elitesavior.vasthall
adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

Signing note: this binary-only drop does not contain the private debug key used through v0.13. Android therefore requires a clean install when replacing v0.13 or older.
