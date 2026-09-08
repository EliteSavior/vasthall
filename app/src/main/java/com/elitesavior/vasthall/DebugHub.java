package com.elitesavior.vasthall;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.elitesavior.vasthall.engine.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-app debug dump. Does not change stick math or native look/move.
 *
 * Camera constants from libvasthall.so:
 * look-apply +0x3c51c is rate only (deadzone 0.12f @ +0x3c598, lookSens 2.35f
 * @ +0x3c60c, dt = max(dt,0) @ +0x3c5f8). nativeSetLookAxes clamps each axis
 * to [-1,1] at +0x3bd20. Pitch min/max live in camera integrate at +0x50b58
 * (v0.15 patch widens -0.28/+0.32 to ±1.5 rad). invertY=0.
 */
final class DebugHub {
    static final String PREF_ON = "vasthall.debug.on";
    static final String PREF_CONTROLS = "vasthall.debug.controls";
    static final String PREF_CAMERA = "vasthall.debug.camera";
    static final String PREF_INPUT = "vasthall.debug.input";
    static final String PREF_ENGINE = "vasthall.debug.engine";
    static final String PREF_LIFECYCLE = "vasthall.debug.lifecycle";

    static final float LOOK_DEADZONE = 0.12f;
    static final float LOOK_SENS = 2.35f;
    static final float AXIS_CLAMP_MIN = -1.0f;
    static final float AXIS_CLAMP_MAX = 1.0f;
    static final int INVERT_Y = 0;
    /** Must match libvasthall.so camera-integrate clamp at +0x50b58 (v0.15). */
    static final float PITCH_MIN = -1.5f;
    static final float PITCH_MAX = 1.5f;
    static final float DT_MIN = 0.001f;
    static final float DT_MAX = 0.033f;

    private static final int INPUT_CAP = 80;
    private static final long LOCKUP_WINDOW_MS = 10_000L;
    private static final long MOVE_LOG_MIN_MS = 50L;

    static final class ZeroEvent {
        final long tMs;
        final String who;
        final String why;
        final String zone;
        final float axisX;
        final float axisY;

        ZeroEvent(long tMs, String who, String why, String zone, float axisX, float axisY) {
            this.tMs = tMs;
            this.who = who;
            this.why = why;
            this.zone = zone;
            this.axisX = axisX;
            this.axisY = axisY;
        }
    }

    private final SharedPreferences prefs;
    private final ArrayDeque<String> inputLog = new ArrayDeque<>();
    private final ArrayDeque<ZeroEvent> zeros = new ArrayDeque<>();
    private final ArrayDeque<String> lifecycle = new ArrayDeque<>();
    private final long startedAt = SystemClock.elapsedRealtime();

    private boolean jumpDown;
    private boolean paused;
    private boolean capture;
    private int nativeW;
    private int nativeH;
    private int surfaceW;
    private int surfaceH;
    private String orient = "landscape";
    private float fps;
    private int frames;
    private long fpsWindowStart = SystemClock.elapsedRealtime();
    private float yaw;
    private float pitch;
    private long lastFrameMs = SystemClock.elapsedRealtime();
    private long lastMoveLogL = -MOVE_LOG_MIN_MS;
    private long lastMoveLogR = -MOVE_LOG_MIN_MS;
    private long jniLagMs;
    private long jniLagMaxMs;
    private final MotionCorr.Ring motionRing = new MotionCorr.Ring();
    private final MotionCorr.LatchWatch latchWatch = new MotionCorr.LatchWatch();
    private final MotionCorr.Spark sparkInput = new MotionCorr.Spark();
    private final MotionCorr.Spark sparkVel = new MotionCorr.Spark();
    private final MotionCorr.Spark sparkLook = new MotionCorr.Spark();
    private final List<String> motionLockups = new ArrayList<>();
    private long lastMotionMs = -1L;
    private long lastSparkMs = -1L;
    private float motionYaw;
    private float motionPitch;
    private float pawnX;
    private float pawnZ;
    private String lastWhoZeroed = "";
    private MotionCorr.Sample lastMotion;
    private volatile HudSnapshot hudSnapshot = HudSnapshot.empty();

    DebugHub(SharedPreferences prefs) {
        this.prefs = prefs;
        if (on()) {
            setOn(true);
        }
    }

    boolean on() {
        return prefs.getBoolean(PREF_ON, false);
    }

    boolean controlsOn() {
        return on() && prefs.getBoolean(PREF_CONTROLS, true);
    }

    boolean cameraOn() {
        return on() && prefs.getBoolean(PREF_CAMERA, true);
    }

    boolean inputOn() {
        return on() && prefs.getBoolean(PREF_INPUT, true);
    }

    boolean engineOn() {
        return on() && prefs.getBoolean(PREF_ENGINE, true);
    }

    boolean lifecycleOn() {
        return on() && prefs.getBoolean(PREF_LIFECYCLE, true);
    }

    void setOn(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(PREF_ON, enabled);
        if (enabled) {
            // Master ON always re-enables subsystems. Empty INPUT_LOG on a
            // 72s session was PREF_INPUT=false while LOCKUP still recorded.
            editor.putBoolean(PREF_CONTROLS, true);
            editor.putBoolean(PREF_CAMERA, true);
            editor.putBoolean(PREF_INPUT, true);
            editor.putBoolean(PREF_ENGINE, true);
            editor.putBoolean(PREF_LIFECYCLE, true);
        }
        editor.apply();
    }

    void setSubsystem(String key, boolean enabled) {
        prefs.edit().putBoolean(key, enabled).apply();
    }

    void clearLogs() {
        inputLog.clear();
        zeros.clear();
        lifecycle.clear();
        motionRing.clear();
        latchWatch.clear();
        sparkInput.clear();
        sparkVel.clear();
        sparkLook.clear();
        motionLockups.clear();
        lastMotion = null;
        pawnX = 0.0f;
        pawnZ = 0.0f;
        motionYaw = 0.0f;
        motionPitch = 0.0f;
        hudSnapshot = HudSnapshot.empty();
    }

    void setJniLagMs(long lagMs) {
        jniLagMs = Math.max(0L, lagMs);
        if (jniLagMs > jniLagMaxMs) {
            jniLagMaxMs = jniLagMs;
        }
    }

    long jniLagMs() {
        return jniLagMs;
    }

    void setJump(boolean down) {
        jumpDown = down;
    }

    boolean jumpDown() {
        return jumpDown;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        latchWatch.onPause(paused);
    }

    void setCapture(boolean capture) {
        this.capture = capture;
        if (!capture) {
            lifecycle("captureLost");
        }
    }

    boolean capture() {
        return capture;
    }

    void setNativeSize(int w, int h) {
        nativeW = w;
        nativeH = h;
    }

    void setSurfaceSize(int w, int h) {
        surfaceW = w;
        surfaceH = h;
    }

    void setOrient(String orient) {
        this.orient = orient;
    }

    void lifecycle(String event) {
        if (!on()) {
            return;
        }
        lifecycle.addLast(tMs() + " " + event);
        while (lifecycle.size() > INPUT_CAP) {
            lifecycle.removeFirst();
        }
    }

    void onZero(String zone, String reason, int ptr, float ax, float ay) {
        if (!on()) {
            return;
        }
        lastWhoZeroed = reason == null ? "" : reason;
        ZeroEvent event = new ZeroEvent(tMs(), reason, reason, zone, ax, ay);
        zeros.addLast(event);
        pruneZeros();
        pushInput(tMs() + " action=ZERO zone=" + zoneLetter(zone)
                + " pid=" + ptr + " n=0 x,y=0,0"
                + " axisL= see CONTROLS axisR= see CONTROLS"
                + " capture=" + (capture ? 1 : 0)
                + " whoZeroed=" + reason
                + " jniLagMs=" + jniLagMs);
    }

    void onTouch(
            String zone,
            MotionEvent event,
            String whoZeroed,
            float axisLx,
            float axisLy,
            float axisRx,
            float axisRy) {
        if (!on()) {
            return;
        }
        int index = event.getActionIndex();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && !shouldLogMove(zone)) {
            return;
        }
        pushInput(tMs()
                + " action=" + actionName(action)
                + " zone=" + zoneLetter(zone)
                + " pid=" + event.getPointerId(index)
                + " n=" + event.getPointerCount()
                + " x,y=" + fmt(event.getX(index)) + "," + fmt(event.getY(index))
                + " axisL= " + fmt(axisLx) + "," + fmt(axisLy)
                + " axisR= " + fmt(axisRx) + "," + fmt(axisRy)
                + " capture=" + (capture ? 1 : 0)
                + " whoZeroed=" + (whoZeroed == null ? "" : whoZeroed)
                + " jniLagMs=" + jniLagMs);
    }

    void tickFrame(float lookX, float lookY) {
        long now = SystemClock.elapsedRealtime();
        float dt = clampDt((now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        frames++;
        if (now - fpsWindowStart >= 1000L) {
            fps = frames * 1000.0f / (now - fpsWindowStart);
            frames = 0;
            fpsWindowStart = now;
        }
        if (!cameraOn() || paused) {
            return;
        }
        float mag = (float) Math.hypot(lookX, lookY);
        float ax = 0.0f;
        float ay = 0.0f;
        if (mag > LOOK_DEADZONE) {
            float scaled = (mag - LOOK_DEADZONE) / (1.0f - LOOK_DEADZONE);
            ax = lookX / mag * scaled;
            ay = lookY / mag * scaled;
        }
        if (INVERT_Y != 0) {
            ay = -ay;
        }
        yaw += ax * LOOK_SENS * dt;
        pitch += ay * LOOK_SENS * dt;
        pitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, pitch));
    }

    void tickMotion(MotionFrame frame) {
        if (!on() || frame == null) {
            hudSnapshot = HudSnapshot.empty();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        float dt = lastMotionMs < 0L ? DT_MIN : clampDt((now - lastMotionMs) / 1000.0f);
        lastMotionMs = now;

        float[] look = lookDelta(frame.conLookX, frame.conLookY, dt);
        float dYaw = look[0];
        float dPitch = look[1];
        if (!paused) {
            motionYaw += dYaw;
            motionPitch += dPitch;
            motionPitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, motionPitch));
        }

        float sin = (float) Math.sin(motionYaw);
        float cos = (float) Math.cos(motionYaw);
        float velX = frame.conMoveX * cos + frame.conMoveY * sin;
        float velZ = -frame.conMoveX * sin + frame.conMoveY * cos;
        if (!paused) {
            pawnX += velX * dt;
            pawnZ += velZ * dt;
        }

        MotionCorr.Sample sample = new MotionCorr.Sample();
        sample.tMs = tMs();
        sample.scheme = frame.scheme == null ? "" : frame.scheme;
        sample.leftX = frame.leftX;
        sample.leftY = frame.leftY;
        sample.rightX = frame.rightX;
        sample.rightY = frame.rightY;
        sample.jump = frame.jump;
        sample.moveOwner = frame.moveOwner;
        sample.lookOwner = frame.lookOwner;
        sample.jumpOwner = frame.jumpOwner;
        sample.sampleAgeMs = frame.sampleAgeMs;
        sample.whoZeroed = frame.whoZeroed != null && !frame.whoZeroed.isEmpty()
                ? frame.whoZeroed : lastWhoZeroed;
        sample.pubMoveX = frame.pubMoveX;
        sample.pubMoveY = frame.pubMoveY;
        sample.pubLookX = frame.pubLookX;
        sample.pubLookY = frame.pubLookY;
        sample.conMoveX = frame.conMoveX;
        sample.conMoveY = frame.conMoveY;
        sample.conLookX = frame.conLookX;
        sample.conLookY = frame.conLookY;
        sample.jniLagMs = frame.jniLagMs;
        sample.pawnX = pawnX;
        sample.pawnY = 0.0f;
        sample.pawnZ = pawnZ;
        sample.velX = velX;
        sample.velZ = velZ;
        sample.yaw = motionYaw;
        sample.pitch = motionPitch;
        sample.dYaw = dYaw;
        sample.dPitch = dPitch;
        sample.dtSec = dt;
        sample.flags = MotionCorr.flags(sample);
        MotionCorr.fillHeadings(sample);
        float[] cmdLook = lookDelta(frame.rightX, frame.rightY, dt);
        sample.cmdLookRate = (Math.abs(cmdLook[0]) + Math.abs(cmdLook[1])) / dt;
        sample.actLookRate = (Math.abs(dYaw) + Math.abs(dPitch)) / dt;

        motionRing.push(sample);
        if (lastSparkMs < 0L || now - lastSparkMs >= 50L) {
            sparkInput.push(MotionCorr.mag(sample.leftX, sample.leftY));
            sparkVel.push(MotionCorr.velMag(sample));
            sparkLook.push(sample.actLookRate);
            lastSparkMs = now;
        }
        latchWatch.onSample(sample, paused);
        if (latchWatch.consumeStamp()) {
            motionRing.freeze();
            motionLockups.add(MotionCorr.lockupLine(
                    sample.tMs, latchWatch.why(), sample.whoZeroed,
                    sample.conMoveX, sample.conMoveY));
        }
        lastMotion = sample;
        hudSnapshot = HudSnapshot.from(sample, sparkInput.snapshot(), sparkVel.snapshot(),
                sparkLook.snapshot());
    }

    HudSnapshot hudSnapshot() {
        return hudSnapshot;
    }

    MotionCorr.Sample lastMotion() {
        return lastMotion;
    }

    private float[] lookDelta(float lookX, float lookY, float dt) {
        float mag = (float) Math.hypot(lookX, lookY);
        float ax = 0.0f;
        float ay = 0.0f;
        if (mag > LOOK_DEADZONE) {
            float scaled = (mag - LOOK_DEADZONE) / (1.0f - LOOK_DEADZONE);
            ax = lookX / mag * scaled;
            ay = lookY / mag * scaled;
        }
        if (INVERT_Y != 0) {
            ay = -ay;
        }
        return new float[] {ax * LOOK_SENS * dt, ay * LOOK_SENS * dt};
    }

    static float clampDt(float dt) {
        if (dt < DT_MIN) {
            return DT_MIN;
        }
        if (dt > DT_MAX) {
            return DT_MAX;
        }
        return dt;
    }

    String buildDump(
            String versionName,
            String scheme,
            StickView left,
            StickView right,
            boolean jump) {
        return buildDump(versionName, scheme, left, right, jump, null, null);
    }

    String buildDump(
            String versionName,
            String scheme,
            StickView left,
            StickView right,
            boolean jump,
            World world) {
        return buildDump(versionName, scheme, left, right, jump, world, null);
    }

    String buildDump(
            String versionName,
            String scheme,
            StickView left,
            StickView right,
            boolean jump,
            World world,
            FlatPadRouter flatPad) {
        pruneZeros();
        StringBuilder out = new StringBuilder(2048);
        out.append("VASTHALL_DEBUG v1\n");
        out.append("time=").append(isoNow()).append('\n');
        out.append("release=").append(versionName).append('\n');
        out.append("package=com.elitesavior.vasthall\n");
        out.append("debug=").append(on() ? "on" : "off").append('\n');
        out.append('\n');

        out.append("[CONTROLS]\n");
        if (controlsOn() || !on()) {
            appendControls(out, scheme, left, right, jump, flatPad);
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        out.append("[CAMERA]\n");
        if (cameraOn() || !on()) {
            appendCamera(out, right);
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        out.append("[INPUT_LOG]\n");
        if (inputOn() || !on()) {
            if (inputLog.isEmpty()) {
                out.append("(empty)\n");
            } else {
                for (String line : inputLog) {
                    out.append(line).append('\n');
                }
            }
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        out.append("[ENGINE]\n");
        if (engineOn() || !on()) {
            float aspect = nativeH > 0 ? (nativeW / (float) nativeH) : 0.0f;
            out.append("nativeResize=").append(nativeW).append(',').append(nativeH).append('\n');
            out.append("surface=").append(surfaceW).append(',').append(surfaceH).append('\n');
            out.append("aspect=").append(fmt(aspect)).append('\n');
            out.append("orient=").append(orient).append('\n');
            out.append("fps=").append(fmt(fps)).append('\n');
            out.append("paused=").append(paused ? 1 : 0).append('\n');
            out.append("jniLagMs=").append(jniLagMs).append('\n');
            out.append("jniLagMaxMs=").append(jniLagMaxMs).append('\n');
            if (world != null) {
                world.appendDump(out);
            }
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        out.append("[LIFECYCLE]\n");
        if (lifecycleOn() || !on()) {
            if (lifecycle.isEmpty()) {
                out.append("(empty)\n");
            } else {
                for (String line : lifecycle) {
                    out.append(line).append('\n');
                }
            }
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        out.append("[MOTION_CORR]\n");
        if (on()) {
            appendMotionCorr(out);
        } else {
            out.append("skipped=off\n");
        }
        out.append('\n');

        MotionCorr.LatchSummary summary = latchWatch.summary();
        summary.frozen = motionRing.frozen();
        out.append(MotionCorr.formatLatchSummary(summary));
        out.append('\n');

        out.append("[LOCKUP]\n");
        List<ZeroEvent> recent = recentZeros();
        if (recent.isEmpty() && motionLockups.isEmpty()) {
            out.append("none\n");
        } else {
            for (ZeroEvent event : recent) {
                out.append("t_ms=").append(event.tMs)
                        .append(" whoZeroed=").append(event.who)
                        .append(" why=").append(event.why)
                        .append(" zone=").append(event.zone)
                        .append(" axes=").append(fmt(event.axisX)).append(',')
                        .append(fmt(event.axisY))
                        .append('\n');
            }
            for (String line : motionLockups) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private void appendControls(
            StringBuilder out,
            String scheme,
            StickView left,
            StickView right,
            boolean jump,
            FlatPadRouter flatPad) {
        out.append("scheme=").append(scheme).append('\n');
        if (flatPad != null && "flat".equals(scheme)) {
            appendFocusZones(out, flatPad);
        } else {
            appendZone(out, "left", left);
            appendZone(out, "right", right);
        }
        out.append("jump=").append(jump ? 1 : 0).append('\n');
        boolean stuck = stuck(left) || stuck(right) || stuck(flatPad);
        out.append("stuckHint=").append(stuck ? 1 : 0).append('\n');
        out.append("jniLagMs=").append(jniLagMs).append('\n');
        if (lastMotion != null) {
            out.append("published.move=")
                    .append(fmt(lastMotion.pubMoveX)).append(',')
                    .append(fmt(lastMotion.pubMoveY)).append('\n');
            out.append("published.look=")
                    .append(fmt(lastMotion.pubLookX)).append(',')
                    .append(fmt(lastMotion.pubLookY)).append('\n');
            out.append("consumed.move=")
                    .append(fmt(lastMotion.conMoveX)).append(',')
                    .append(fmt(lastMotion.conMoveY)).append('\n');
            out.append("consumed.look=")
                    .append(fmt(lastMotion.conLookX)).append(',')
                    .append(fmt(lastMotion.conLookY)).append('\n');
        }
        if (flatPad != null) {
            appendFocus(out, flatPad);
        }
    }

    private static void appendFocusZones(StringBuilder out, FlatPadRouter flatPad) {
        appendFocusZone(out, "left", FlatPadRouter.Target.MOVE, flatPad);
        appendFocusZone(out, "right", FlatPadRouter.Target.LOOK, flatPad);
    }

    private static void appendFocusZone(
            StringBuilder out, String name, FlatPadRouter.Target target, FlatPadRouter flatPad) {
        int pid = flatPad.ownerOf(target);
        float axisX = target == FlatPadRouter.Target.MOVE ? flatPad.moveX() : flatPad.lookX();
        float axisY = target == FlatPadRouter.Target.MOVE ? flatPad.moveY() : flatPad.lookY();
        out.append(name).append(".axis=")
                .append(fmt(axisX)).append(',').append(fmt(axisY)).append('\n');
        out.append(name).append(".active=").append(pid == FlatPadRouter.INVALID_POINTER ? 0 : 1)
                .append('\n');
        out.append(name).append(".pointerId=").append(pid).append('\n');
        if (pid == FlatPadRouter.INVALID_POINTER) {
            out.append(name).append(".origin=0,0\n");
            out.append(name).append(".knob=0,0\n");
        } else {
            float r = Math.max(flatPad.layout().stickRadius, 1.0f);
            out.append(name).append(".origin=")
                    .append(fmt(flatPad.originX(pid))).append(',')
                    .append(fmt(flatPad.originY(pid))).append('\n');
            out.append(name).append(".knob=")
                    .append(fmt(flatPad.originX(pid) + flatPad.axisX(pid) * r)).append(',')
                    .append(fmt(flatPad.originY(pid) + flatPad.axisY(pid) * r)).append('\n');
        }
    }

    private static void appendFocus(StringBuilder out, FlatPadRouter flatPad) {
        out.append("move.ownerId=").append(flatPad.ownerOf(FlatPadRouter.Target.MOVE)).append('\n');
        out.append("move.axis=")
                .append(fmt(flatPad.moveX())).append(',').append(fmt(flatPad.moveY())).append('\n');
        out.append("move.sampleAgeMs=").append(flatPad.sampleAgeMs(FlatPadRouter.Target.MOVE)).append('\n');
        out.append("look.ownerId=").append(flatPad.ownerOf(FlatPadRouter.Target.LOOK)).append('\n');
        out.append("look.axis=")
                .append(fmt(flatPad.lookX())).append(',').append(fmt(flatPad.lookY())).append('\n');
        out.append("look.sampleAgeMs=").append(flatPad.sampleAgeMs(FlatPadRouter.Target.LOOK)).append('\n');
        out.append("jump.ownerId=").append(flatPad.ownerOf(FlatPadRouter.Target.JUMP)).append('\n');
        out.append("lastWhoZeroed=").append(flatPad.lastWhoZeroed()).append('\n');
    }

    private static boolean stuck(StickView zone) {
        return zone != null
                && !zone.hasActiveFinger()
                && (zone.axisX() != 0.0f || zone.axisY() != 0.0f);
    }

    private static boolean stuck(FlatPadRouter flatPad) {
        if (flatPad == null) {
            return false;
        }
        boolean moveStuck = flatPad.ownerOf(FlatPadRouter.Target.MOVE) == FlatPadRouter.INVALID_POINTER
                && (flatPad.moveX() != 0.0f || flatPad.moveY() != 0.0f);
        boolean lookStuck = flatPad.ownerOf(FlatPadRouter.Target.LOOK) == FlatPadRouter.INVALID_POINTER
                && (flatPad.lookX() != 0.0f || flatPad.lookY() != 0.0f);
        return moveStuck || lookStuck;
    }

    private static void appendZone(StringBuilder out, String name, StickView zone) {
        if (zone == null) {
            out.append(name).append(".axis=0,0\n");
            out.append(name).append(".active=0\n");
            out.append(name).append(".pointerId=-1\n");
            out.append(name).append(".origin=0,0\n");
            out.append(name).append(".knob=0,0\n");
            return;
        }
        out.append(name).append(".axis=")
                .append(fmt(zone.axisX())).append(',').append(fmt(zone.axisY())).append('\n');
        out.append(name).append(".active=").append(zone.hasActiveFinger() ? 1 : 0).append('\n');
        out.append(name).append(".pointerId=").append(zone.pointerId()).append('\n');
        out.append(name).append(".origin=")
                .append(fmt(zone.originX())).append(',').append(fmt(zone.originY())).append('\n');
        out.append(name).append(".knob=")
                .append(fmt(zone.knobX())).append(',').append(fmt(zone.knobY())).append('\n');
    }

    private void appendCamera(StringBuilder out, StickView right) {
        float lookX = right == null ? 0.0f : right.axisX();
        float lookY = right == null ? 0.0f : right.axisY();
        out.append("yaw=").append(fmt(yaw)).append('\n');
        out.append("pitch=").append(fmt(pitch)).append('\n');
        out.append("pitchMin=").append(fmt(PITCH_MIN)).append('\n');
        out.append("pitchMax=").append(fmt(PITCH_MAX)).append('\n');
        out.append("lookSens=").append(fmt(LOOK_SENS)).append('\n');
        out.append("invertY=").append(INVERT_Y).append('\n');
        out.append("lookAxis=").append(fmt(lookX)).append(',').append(fmt(lookY)).append('\n');
        if (lastMotion != null) {
            out.append("dYaw=").append(fmt(lastMotion.dYaw)).append('\n');
            out.append("dPitch=").append(fmt(lastMotion.dPitch)).append('\n');
            out.append("pawn.loc=")
                    .append(fmt(lastMotion.pawnX)).append(',')
                    .append(fmt(lastMotion.pawnY)).append(',')
                    .append(fmt(lastMotion.pawnZ)).append('\n');
            out.append("pawn.vel=")
                    .append(fmt(lastMotion.velX)).append(',')
                    .append(fmt(lastMotion.velZ)).append('\n');
            out.append("pawnSrc=consumed_integrate\n");
        }
        out.append("lookDeadzone=").append(fmt(LOOK_DEADZONE)).append('\n');
        out.append("lookAxisClamp=").append(fmt(AXIS_CLAMP_MIN)).append(',')
                .append(fmt(AXIS_CLAMP_MAX)).append('\n');
        out.append("note=Native pitch clamp is camera integrate at libvasthall.so +0x50b58 ")
                .append("(v0.15 widened -0.28/+0.32 to pitchMin/pitchMax ±1.5 rad). ")
                .append("look-apply +0x3c51c is rate only. ")
                .append("nativeSetLookAxes clamps each axis to [-1,1] at +0x3bd20. ")
                .append("lookSens=2.35f (0x40166666 at +0x3c60c)*dt; look-apply dt is max(dt,0) at +0x3c5f8. ")
                .append("Debug tick clamps dt to 0.001–0.033 so a hitch cannot slam look. ")
                .append("deadzone=0.12f (0x3df5c28f at +0x3c598) remaps magnitude. ")
                .append("No fneg on look Y so invertY=0. ")
                .append("yaw/pitch are Java debug integrates matching those constants, not engine memory. ")
                .append("jniLagMs is now-lastNativeConsumeMs on the hall-axis-pump (every vsync, not 20 Hz).\n");
    }

    private boolean shouldLogMove(String zone) {
        long now = tMs();
        if ("left".equals(zone)) {
            if (now - lastMoveLogL < MOVE_LOG_MIN_MS) {
                return false;
            }
            lastMoveLogL = now;
            return true;
        }
        if ("right".equals(zone)) {
            if (now - lastMoveLogR < MOVE_LOG_MIN_MS) {
                return false;
            }
            lastMoveLogR = now;
            return true;
        }
        return true;
    }

    private void pushInput(String line) {
        inputLog.addLast(line);
        while (inputLog.size() > INPUT_CAP) {
            inputLog.removeFirst();
        }
    }

    private void pruneZeros() {
        long cutoff = tMs() - LOCKUP_WINDOW_MS;
        while (!zeros.isEmpty() && zeros.peekFirst().tMs < cutoff) {
            zeros.removeFirst();
        }
    }

    private List<ZeroEvent> recentZeros() {
        pruneZeros();
        return new ArrayList<>(zeros);
    }

    private long tMs() {
        return SystemClock.elapsedRealtime() - startedAt;
    }

    static String actionName(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action);
        }
    }

    static String zoneLetter(String zone) {
        if ("left".equals(zone)) {
            return "L";
        }
        if ("right".equals(zone)) {
            return "R";
        }
        if ("jump".equals(zone)) {
            return "JUMP";
        }
        if ("menu".equals(zone)) {
            return "MENU";
        }
        if ("pad".equals(zone)) {
            return "PAD";
        }
        return "OTHER";
    }

    static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    static String isoNow() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(new java.util.Date());
    }

    private void appendMotionCorr(StringBuilder out) {
        out.append("pawnSrc=consumed_integrate\n");
        out.append("lookRateUnit=rad_s\n");
        if (lastMotion != null) {
            out.append("flags=").append(MotionCorr.flagNames(lastMotion.flags)).append('\n');
            out.append("cmdMoveHeading=").append(MotionCorr.f(lastMotion.cmdMoveHeading)).append('\n');
            out.append("actMoveHeading=").append(MotionCorr.f(lastMotion.actMoveHeading)).append('\n');
            out.append("headingErrDeg=").append(MotionCorr.f(lastMotion.headingErrDeg)).append('\n');
            out.append("cmdLookRate=").append(MotionCorr.f(lastMotion.cmdLookRate)).append('\n');
            out.append("actLookRate=").append(MotionCorr.f(lastMotion.actLookRate)).append('\n');
        }
        out.append("spark.inputMagL=").append(sparkInput.strip()).append('\n');
        out.append("spark.velMag=").append(sparkVel.strip()).append('\n');
        out.append("spark.lookRate=").append(sparkLook.strip()).append('\n');
        out.append(motionRing.frozen() ? motionRing.frozenCsv() : motionRing.toCsv());
    }

    static final class MotionFrame {
        String scheme = "";
        float leftX;
        float leftY;
        float rightX;
        float rightY;
        boolean jump;
        int moveOwner = -1;
        int lookOwner = -1;
        int jumpOwner = -1;
        long sampleAgeMs;
        String whoZeroed = "";
        float pubMoveX;
        float pubMoveY;
        float pubLookX;
        float pubLookY;
        float conMoveX;
        float conMoveY;
        float conLookX;
        float conLookY;
        long jniLagMs;
    }

    static final class HudSnapshot {
        final String text;
        final float cmdMoveX;
        final float cmdMoveY;
        final float actMoveX;
        final float actMoveY;
        final float cmdLookX;
        final float cmdLookY;
        final float actLookX;
        final float actLookY;
        final float[] sparkInput;
        final float[] sparkVel;
        final float[] sparkLook;
        final int flags;

        HudSnapshot(
                String text,
                float cmdMoveX,
                float cmdMoveY,
                float actMoveX,
                float actMoveY,
                float cmdLookX,
                float cmdLookY,
                float actLookX,
                float actLookY,
                float[] sparkInput,
                float[] sparkVel,
                float[] sparkLook,
                int flags) {
            this.text = text;
            this.cmdMoveX = cmdMoveX;
            this.cmdMoveY = cmdMoveY;
            this.actMoveX = actMoveX;
            this.actMoveY = actMoveY;
            this.cmdLookX = cmdLookX;
            this.cmdLookY = cmdLookY;
            this.actLookX = actLookX;
            this.actLookY = actLookY;
            this.sparkInput = sparkInput;
            this.sparkVel = sparkVel;
            this.sparkLook = sparkLook;
            this.flags = flags;
        }

        static HudSnapshot empty() {
            return new HudSnapshot(
                    "", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                    new float[0], new float[0], new float[0], 0);
        }

        static HudSnapshot from(
                MotionCorr.Sample sample, float[] sparkIn, float[] sparkV, float[] sparkL) {
            String text = formatHud(sample);
            return new HudSnapshot(
                    text,
                    sample.leftX,
                    sample.leftY,
                    sample.conMoveX,
                    sample.conMoveY,
                    sample.rightX,
                    sample.rightY,
                    sample.conLookX,
                    sample.conLookY,
                    sparkIn,
                    sparkV,
                    sparkL,
                    sample.flags);
        }

        private static String formatHud(MotionCorr.Sample sample) {
            StringBuilder out = new StringBuilder(512);
            out.append(sample.scheme)
                    .append(" own M").append(sample.moveOwner)
                    .append(" L").append(sample.lookOwner)
                    .append(" J").append(sample.jumpOwner).append('\n');
            out.append("L ").append(fmt(sample.leftX)).append(',').append(fmt(sample.leftY))
                    .append(" mag=").append(fmt(MotionCorr.mag(sample.leftX, sample.leftY)))
                    .append(" age=").append(sample.sampleAgeMs)
                    .append(" who=").append(sample.whoZeroed == null ? "" : sample.whoZeroed)
                    .append('\n');
            out.append("R ").append(fmt(sample.rightX)).append(',').append(fmt(sample.rightY))
                    .append(" mag=").append(fmt(MotionCorr.mag(sample.rightX, sample.rightY)))
                    .append('\n');
            out.append("pub ").append(fmt(sample.pubMoveX)).append(',').append(fmt(sample.pubMoveY))
                    .append(" / ").append(fmt(sample.pubLookX)).append(',').append(fmt(sample.pubLookY))
                    .append('\n');
            out.append("con ").append(fmt(sample.conMoveX)).append(',').append(fmt(sample.conMoveY))
                    .append(" / ").append(fmt(sample.conLookX)).append(',').append(fmt(sample.conLookY))
                    .append(" lag=").append(sample.jniLagMs).append('\n');
            out.append("pawn v=").append(fmt(MotionCorr.velMag(sample)))
                    .append(" dYaw=").append(fmt(sample.dYaw))
                    .append(" dPit=").append(fmt(sample.dPitch))
                    .append(" loc=").append(fmt(sample.pawnX)).append(',')
                    .append(fmt(sample.pawnZ))
                    .append(" src=con")
                    .append('\n');
            out.append("yaw=").append(fmt(sample.yaw))
                    .append(" pit=").append(fmt(sample.pitch))
                    .append(" cmdH=").append(MotionCorr.f(sample.cmdMoveHeading))
                    .append(" actH=").append(MotionCorr.f(sample.actMoveHeading))
                    .append(" err=").append(MotionCorr.f(sample.headingErrDeg))
                    .append('\n');
            out.append("FLAGS ").append(MotionCorr.flagNames(sample.flags));
            return out.toString();
        }
    }
}
