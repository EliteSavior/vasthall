# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- Tag: `v0.16-hall`
- File: `VastHall-v0-foss.apk` (269007 bytes, md5 `2621ce8ad3f67372a687c594abae88c4`)
- Source in this repo matches the APK.

## Download

Get the APK from [Releases](https://github.com/EliteSavior/vasthall/releases/tag/v0.16-hall).

Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.16-hall/VastHall-v0-foss.apk

```bash
adb uninstall com.elitesavior.vasthall
adb install VastHall-v0-foss.apk
```

Signing note: this binary-only drop does not contain the private debug key used through v0.15. Android therefore requires a clean install when replacing v0.15 or older.
