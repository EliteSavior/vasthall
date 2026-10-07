# Iso I — Camera & Grid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Vast Hall v0.37 with an Iso Sandbox GameMode that shows a true isometric orthographic ground grid with pan and zoom, without breaking Hall mode.

**Architecture:** New `com.elitesavior.vasthall.iso` package holds camera, grid, gestures, GameMode, and overlay view. Hall keeps the native SurfaceView. Iso Sandbox is an opaque Canvas overlay selected from the existing Menu via `openLevel("IsoSandbox")`. Pads/sticks are hidden while Iso is active; their math is not changed.

**Tech Stack:** Java 17, Android minSdk 28, JUnit 4 + Robolectric, existing GameInstance/GameMode/level JSON, Gradle foss flavor.

## Global Constraints

- Base commit `1df8a41131a433109ce1cb9a324c2fae695e4ed9` (v0.36). Do not spend time on sticky twin-stick.
- Package `com.elitesavior.vasthall.iso`; GameMode name `IsoSandboxGameMode`.
- Default grid 32×32, cell 1 world unit, origin (0,0,0), look-at (16, 0, 16).
- True isometric orthographic: `isoX=(x-z)*cos(30°)`, `isoY=(x+z)*sin(30°)-y`; Y-up.
- Zoom clamp `[0.35, 3.0]`, default `1.0`; pan look-at X/Z clamp `[-8, 40]`; `PIXELS_PER_UNIT=48`.
- No character, combat, dual viewport, town/dungeon, twin-stick. `defaultPawnClass()` is null.
- Do not modify sticky-control pipelines except disable pads while Iso is active.
- Version `0.37.0` / versionCode `37`. Release tag `v0.37-hall`, asset exactly `VastHall-v0-foss.apk`.
- Tests: `./gradlew :app:testFossDebugUnitTest`. Do not claim device playtest.

---

### Task 1: IsoCamera pan/zoom math

**Files:**
- Create: `app/src/test/java/com/elitesavior/vasthall/iso/IsoCameraTest.java`
- Create: `app/src/main/java/com/elitesavior/vasthall/iso/IsoCamera.java`

**Interfaces:**
- Consumes: engine Y-up world (no types)
- Produces: `IsoCamera` with `MIN_ZOOM=0.35f`, `MAX_ZOOM=3.0f`, `DEFAULT_ZOOM=1.0f`, `PIXELS_PER_UNIT=48f`, `COS30`, `SIN30`; look-at X/Z; `setViewport(w,h)`; `setPanBounds(minX,maxX,minZ,maxZ)`; `lookAt(x,z)`; `setZoom(z)`; `zoomBy(factor)`; `zoomBy(factor, focusSx, focusSy)`; `screenX/Y(wx,wy,wz)`; `groundX/Z(sx,sy)`; `panByScreen(prevSx, prevSy, sx, sy)`

- [ ] **Step 1: Write the failing tests**

```java
package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class IsoCameraTest {
    private IsoCamera camera;

    @Before
    public void setUp() {
        camera = new IsoCamera();
        camera.setViewport(800.0f, 480.0f);
        camera.lookAt(16.0f, 16.0f);
        camera.setZoom(1.0f);
        camera.setPanBounds(-8.0f, 40.0f, -8.0f, 40.0f);
    }

    @Test
    public void lookAtProjectsToViewportCenter() {
        assertEquals(400.0f, camera.screenX(16.0f, 0.0f, 16.0f), 0.05f);
        assertEquals(240.0f, camera.screenY(16.0f, 0.0f, 16.0f), 0.05f);
    }

    @Test
    public void groundRoundTripAtYZero() {
        float sx = camera.screenX(4.0f, 0.0f, 9.0f);
        float sy = camera.screenY(4.0f, 0.0f, 9.0f);
        assertEquals(4.0f, camera.groundX(sx, sy), 0.05f);
        assertEquals(9.0f, camera.groundZ(sx, sy), 0.05f);
    }

    @Test
    public void zoomIsClamped() {
        camera.setZoom(99.0f);
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
        camera.setZoom(0.01f);
        assertEquals(IsoCamera.MIN_ZOOM, camera.zoom(), 0.0001f);
        camera.zoomBy(0.01f);
        assertEquals(IsoCamera.MIN_ZOOM, camera.zoom(), 0.0001f);
        camera.setZoom(1.0f);
        camera.zoomBy(100.0f);
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
    }

    @Test
    public void panByScreenKeepsGrabbedGroundPoint() {
        float grabSx = 420.0f;
        float grabSy = 260.0f;
        float gx = camera.groundX(grabSx, grabSy);
        float gz = camera.groundZ(grabSx, grabSy);
        camera.panByScreen(grabSx, grabSy, grabSx + 40.0f, grabSy - 24.0f);
        assertEquals(gx, camera.groundX(grabSx + 40.0f, grabSy - 24.0f), 0.08f);
        assertEquals(gz, camera.groundZ(grabSx + 40.0f, grabSy - 24.0f), 0.08f);
    }

    @Test
    public void panClampsLookAt() {
        camera.lookAt(-100.0f, 99.0f);
        assertEquals(-8.0f, camera.lookAtX(), 0.0001f);
        assertEquals(40.0f, camera.lookAtZ(), 0.0001f);
    }

    @Test
    public void zoomAboutFocusKeepsGroundPoint() {
        float fx = 500.0f;
        float fy = 200.0f;
        float gx = camera.groundX(fx, fy);
        float gz = camera.groundZ(fx, fy);
        camera.zoomBy(1.4f, fx, fy);
        assertTrue(camera.zoom() > 1.0f);
        assertEquals(gx, camera.groundX(fx, fy), 0.12f);
        assertEquals(gz, camera.groundZ(fx, fy), 0.12f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFossDebugUnitTest --tests com.elitesavior.vasthall.iso.IsoCameraTest`

Expected: FAIL compile (package/class missing) or test not found.

- [ ] **Step 3: Write minimal IsoCamera**

Implement the projection, inverse at Y=0, pan-by-screen (`lookAt += groundBefore - groundAfter`), zoom-by with optional focus, and clamps. Default look-at `(16,16)`, zoom `1`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testFossDebugUnitTest --tests com.elitesavior.vasthall.iso.IsoCameraTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/elitesavior/vasthall/iso/IsoCameraTest.java \
        app/src/main/java/com/elitesavior/vasthall/iso/IsoCamera.java
git commit -m "Add isometric orthographic camera with pan and zoom clamps."
```

---

### Task 2: IsoGrid line math

**Files:**
- Create: `app/src/test/java/com/elitesavior/vasthall/iso/IsoGridTest.java`
- Create: `app/src/main/java/com/elitesavior/vasthall/iso/IsoGrid.java`

**Interfaces:**
- Consumes: none
- Produces: `IsoGrid.DEFAULT_SIZE=32`; `size()`; `lineCount()`; `forEachLine(LineVisitor)` where visitor is `void line(float x0, float y0, float z0, float x1, float y1, float z1)`; origin at (0,0,0)

- [ ] **Step 1: Write the failing test**

```java
package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class IsoGridTest {
    @Test
    public void defaultGridEmitsThirtyTwoByThirtyTwoGroundLines() {
        IsoGrid grid = new IsoGrid();
        assertEquals(32, grid.size());
        List<float[]> lines = new ArrayList<>();
        grid.forEachLine((x0, y0, z0, x1, y1, z1) ->
                lines.add(new float[] {x0, y0, z0, x1, y1, z1}));
        assertEquals(66, lines.size());
        assertEquals(66, grid.lineCount());
        boolean originX = false;
        boolean originZ = false;
        boolean farCorner = false;
        for (float[] line : lines) {
            assertEquals(0.0f, line[1], 0.0f);
            assertEquals(0.0f, line[4], 0.0f);
            if (line[0] == 0 && line[2] == 0 && line[3] == 32 && line[5] == 0) {
                originX = true;
            }
            if (line[0] == 0 && line[2] == 0 && line[3] == 0 && line[5] == 32) {
                originZ = true;
            }
            if (line[0] == 32 && line[2] == 32 && line[3] == 0 && line[5] == 32) {
                farCorner = true;
            }
            if (line[0] == 32 && line[2] == 32 && line[3] == 32 && line[5] == 0) {
                farCorner = true;
            }
        }
        assertTrue(originX);
        assertTrue(originZ);
        assertTrue(farCorner);
    }
}
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testFossDebugUnitTest --tests com.elitesavior.vasthall.iso.IsoGridTest`

Expected: FAIL (class missing)

- [ ] **Step 3: Implement IsoGrid**

33 X-axis lines at z=0..32 from x=0→32, 33 Z-axis lines at x=0..32 from z=0→32, all y=0.

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/elitesavior/vasthall/iso/IsoGridTest.java \
        app/src/main/java/com/elitesavior/vasthall/iso/IsoGrid.java
git commit -m "Add 32x32 isometric ground-grid line math."
```

---

### Task 3: IsoGestures (drag pan, pinch zoom, +/-)

**Files:**
- Create: `app/src/test/java/com/elitesavior/vasthall/iso/IsoGesturesTest.java`
- Create: `app/src/main/java/com/elitesavior/vasthall/iso/IsoGestures.java`

**Interfaces:**
- Consumes: `IsoCamera` from Task 1
- Produces: `IsoGestures(IsoCamera)`; `pointerDown(id,x,y)`; `pointerMove(id,x,y)`; `pointerUp(id)`; `cancel()`; `zoomIn()`; `zoomOut()` (factor 1.15 about viewport center)

- [ ] **Step 1: Write the failing tests**

One-finger drag changes look-at; two-finger increasing span increases zoom and clamps; `zoomIn`/`zoomOut` change zoom inside clamps; lifting the second finger does not jump look-at (reset pan anchor).

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement IsoGestures**

Track up to two pointers. One pointer: `camera.panByScreen`. Two pointers: zoom by span ratio about midpoint via `camera.zoomBy(factor, midX, midY)`.

- [ ] **Step 4: Run to verify pass**

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/elitesavior/vasthall/iso/IsoGesturesTest.java \
        app/src/main/java/com/elitesavior/vasthall/iso/IsoGestures.java
git commit -m "Add iso pan/pinch gesture state machine."
```

---

### Task 4: IsoSandboxGameMode + empty level

**Files:**
- Create: `app/src/main/resources/levels/IsoSandbox.json`
- Create: `app/src/main/java/com/elitesavior/vasthall/iso/IsoSandboxGameMode.java`
- Create: `app/src/test/java/com/elitesavior/vasthall/iso/IsoSandboxGameModeTest.java`
- Modify: `app/src/main/java/com/elitesavior/vasthall/engine/GameModeTypes.java` (register `IsoSandboxGameMode`)
- Modify: `app/src/main/java/com/elitesavior/vasthall/engine/LevelDefinition.java` (add `ISO_RESOURCE` / `isoSandbox()`)
- Modify: `app/src/main/java/com/elitesavior/vasthall/engine/AssetRegistry.java` (register IsoSandbox level; bump demo catalog)
- Modify tests that hardcode demo asset count `6` → `7`: `GameInstanceTest`, `DataAssetTest`

**Interfaces:**
- Consumes: `GameMode`, `IsoCamera`, `IsoGrid`
- Produces: `IsoSandboxGameMode.camera()`, `.grid()`, `.gestures()`; `defaultPawnClass()==null`; level name `IsoSandbox`

IsoSandbox.json:

```json
{
  "name": "IsoSandbox",
  "gameMode": "IsoSandboxGameMode",
  "actors": []
}
```

- [ ] **Step 1: Write IsoSandboxGameModeTest** (failing)

`openLevel("IsoSandbox")` → mode is `IsoSandboxGameMode`, `actorCount()==0`, `findDefaultPawn()==null`, camera look-at 16/16, grid size 32. Then `openLevel("Hall")` → `HallGameMode`, PlayerPawn present, previous iso `endCount()==1`.

- [ ] **Step 2: Run to verify fail**

- [ ] **Step 3: Implement mode, JSON, registration, asset-count test updates**

- [ ] **Step 4: Run `IsoSandboxGameModeTest` and the updated catalog tests — PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/resources/levels/IsoSandbox.json \
        app/src/main/java/com/elitesavior/vasthall/iso/IsoSandboxGameMode.java \
        app/src/test/java/com/elitesavior/vasthall/iso/IsoSandboxGameModeTest.java \
        app/src/main/java/com/elitesavior/vasthall/engine/GameModeTypes.java \
        app/src/main/java/com/elitesavior/vasthall/engine/LevelDefinition.java \
        app/src/main/java/com/elitesavior/vasthall/engine/AssetRegistry.java \
        app/src/test/java/com/elitesavior/vasthall/engine/GameInstanceTest.java \
        app/src/test/java/com/elitesavior/vasthall/engine/DataAssetTest.java
git commit -m "Add IsoSandboxGameMode and empty IsoSandbox level."
```

---

### Task 5: IsoGridView overlay + Menu wiring

**Files:**
- Create: `app/src/main/java/com/elitesavior/vasthall/iso/IsoGridView.java`
- Create: `app/src/test/java/com/elitesavior/vasthall/iso/IsoGridViewTest.java`
- Modify: `app/src/main/java/com/elitesavior/vasthall/VastHallActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `IsoCamera`, `IsoGrid`, `IsoGestures` from the live `IsoSandboxGameMode`
- Produces: visible grid + origin marker; drag/pinch on the view; Activity Menu **Iso Sandbox** / **Hall**; pads hidden while iso active

`IsoGridView`:

- `bind(IsoCamera, IsoGrid, IsoGestures)`
- Opaque dark fill, grid lines, origin marker at (0,0,0)
- `onSizeChanged` → `camera.setViewport`
- `onTouchEvent` → gestures (return true when bound)
- GONE when unbound / Hall mode

Activity:

- Add view above surface/pads, below Menu button.
- Menu actions: Iso Sandbox → `game.openLevel("IsoSandbox")`; Hall → `game.openLevel("Hall")`.
- Each frame (and after menu/console travel): if mode is `IsoSandboxGameMode`, show overlay, hide PlayHud + FlatPadOverlay + jump, `zeroAllControls("ISO_MODE")` once on enter; else restore `applyScheme`.
- `handleGameKey`: when iso, `+`/`=`/numpad add → `zoomIn()`, `-`/numpad sub → `zoomOut()`.
- Rebind `widgetOverlay` after travel.

IsoGridViewTest (Robolectric): bind + layout draws without throw; `onTouchEvent` DOWN returns true when bound, false when unbound.

- [ ] **Step 1: Write IsoGridViewTest (failing)**
- [ ] **Step 2: Run to verify fail**
- [ ] **Step 3: Implement view + Activity + strings**
- [ ] **Step 4: Run IsoGridViewTest + full unit suite subset — PASS**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/elitesavior/vasthall/iso/IsoGridView.java \
        app/src/test/java/com/elitesavior/vasthall/iso/IsoGridViewTest.java \
        app/src/main/java/com/elitesavior/vasthall/VastHallActivity.java \
        app/src/main/res/values/strings.xml
git commit -m "Show Iso Sandbox grid overlay from Menu with pad HUDs off."
```

---

### Task 6: Version, docs, tests, APK, release

**Files:**
- Modify: `app/build.gradle` (`versionCode 37`, `versionName "0.37.0"`)
- Modify: `README.md` (Download/Direct → `v0.37-hall` only; menu path; pan/zoom; file size/md5 after build)
- Modify: `ENGINE.md` (one GameMode table row + IsoSandbox sample note; do not rewrite the doc)

- [ ] **Step 1: Bump version**
- [ ] **Step 2: Run `./gradlew :app:testFossDebugUnitTest` — all green**
- [ ] **Step 3: `./gradlew :app:assembleFoss`; copy fossDebug APK to `VastHall-v0-foss.apk`; record md5 + size**
- [ ] **Step 4: Update README with those numbers and Iso how-to**
- [ ] **Step 5: Commit, push, PR, GitHub Release `v0.37-hall` with exactly `VastHall-v0-foss.apk`**

Release notes must include md5, size, commit, clean-install:

```bash
adb uninstall com.elitesavior.vasthall && adb install VastHall-v0-foss.apk
```

Do not claim device playtest. PR body: Menu → Iso Sandbox; drag pan; pinch or +/- zoom; Menu → Hall to return.
