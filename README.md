# Vast Hall

FOSS sideload APK for Vast Hall (`com.elitesavior.vasthall`).

- Flavor: `foss` (no Play / GMS / Firebase, no `INTERNET`)
- File name (every release): `VastHall-v0-foss.apk`

## Download

### Latest tested: v0.35-hall

Last build device-tested on the owner's phone. Known issue: sticky-latch on New pad; debug HUD build.

- Release: [https://github.com/EliteSavior/vasthall/releases/tag/v0.35-hall](https://github.com/EliteSavior/vasthall/releases/tag/v0.35-hall)
- Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.35-hall/VastHall-v0-foss.apk (504942 bytes, md5 `aedc258f6a416631acaeb330c2b7b48c`)

### Newest untested: v0.37-hall (and v0.36-hall)

Pre-releases. Not yet device-tested.

- v0.37-hall (Iso I: Iso Sandbox camera + grid): [https://github.com/EliteSavior/vasthall/releases/tag/v0.37-hall](https://github.com/EliteSavior/vasthall/releases/tag/v0.37-hall)
  - Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.37-hall/VastHall-v0-foss.apk (526374 bytes, md5 `ed062713cee933b986566552e1dfeaa3`)
- v0.36-hall (New pad stale-sample age-out): [https://github.com/EliteSavior/vasthall/releases/tag/v0.36-hall](https://github.com/EliteSavior/vasthall/releases/tag/v0.36-hall)
  - Direct: https://github.com/EliteSavior/vasthall/releases/download/v0.36-hall/VastHall-v0-foss.apk (509602 bytes, md5 `2e23eae476f735cb54f26fc6dc45cc83`)

Clean install (signing may differ from older drops):

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

This repo is the public sideload drop.

**Iso Sandbox:** Menu → **Iso Sandbox** — drag to pan, pinch or +/- to zoom. Menu → **Hall** returns.

Controls (Hall): Menu → Settings — Legacy touch / Legacy pad / New pad. Sticky twin-stick work is tabled.
