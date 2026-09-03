package com.elitesavior.vasthall;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;

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
            appendControls(out, scheme, left, right, jump);
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

        out.append("[LOCKUP]\n");
        List<ZeroEvent> recent = recentZeros();
        if (recent.isEmpty()) {
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
        }
        return out.toString();
    }

    private void appendControls(
            StringBuilder out,
            String scheme,
            StickView left,
            StickView right,
            boolean jump) {
        out.append("scheme=").append(scheme).append('\n');
        appendZone(out, "left", left);
        appendZone(out, "right", right);
        out.append("jump=").append(jump ? 1 : 0).append('\n');
        boolean stuck = stuck(left) || stuck(right);
        out.append("stuckHint=").append(stuck ? 1 : 0).append('\n');
        out.append("jniLagMs=").append(jniLagMs).append('\n');
    }

    private static boolean stuck(StickView zone) {
        return zone != null
                && !zone.hasActiveFinger()
                && (zone.axisX() != 0.0f || zone.axisY() != 0.0f);
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
        return "OTHER";
    }

    static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }

    static String isoNow() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                .format(new java.util.Date());
    }
}
