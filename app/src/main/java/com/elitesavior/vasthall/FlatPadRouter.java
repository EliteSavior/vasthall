package com.elitesavior.vasthall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * New pad backend with exclusive pointer-focus ownership. DOWN binds
 * {@code ownerPointerId} for Move|Look|Jump only if that role is free.
 * Only that pointerId may update the role. Unbound MOVE is ignored (no
 * revive, no nearest-stick rebind). UP clears that owner and zeros that
 * role. CANCEL / OUTSIDE / pause / focus-lost / scheme switch call
 * {@link #releaseAll}, which publishes zeros through the sink immediately.
 * Tick sample timeout and JNI lag with no fresh sample also force zeros.
 */
final class FlatPadRouter {
    enum Target {
        NONE,
        MOVE,
        LOOK,
        JUMP
    }

    interface Sink {
        void setMove(float x, float y);

        void setLook(float x, float y);

        void setJump(boolean down);
    }

    interface Probe {
        void onZero(String zone, String reason, int pointerId, float axisX, float axisY);
    }

    interface NowMs {
        long nowMs();
    }

    static final class Layout {
        float width = 1.0f;
        float height = 1.0f;
        float stickRadius = 71.0f;
        float jumpLeft;
        float jumpTop;
        float jumpRight;
        float jumpBottom;

        Target hit(float x, float y) {
            if (x >= jumpLeft && x < jumpRight && y >= jumpTop && y < jumpBottom) {
                return Target.JUMP;
            }
            if (x < width * 0.5f) {
                return Target.MOVE;
            }
            return Target.LOOK;
        }
    }

    private static final class Bind {
        final Target target;
        final float originX;
        final float originY;
        float axisX;
        float axisY;
        long lastSampleMs;

        Bind(Target target, float originX, float originY, long lastSampleMs) {
            this.target = target;
            this.originX = originX;
            this.originY = originY;
            this.lastSampleMs = lastSampleMs;
        }
    }

    static final int INVALID_POINTER = -1;
    /** Stick sample age that forces owner death (~80–120ms band). */
    static final long SAMPLE_TIMEOUT_MS = 100L;
    /** Pump lag with no fresh sample that forces {@link #releaseAll}. */
    static final long JNI_LAG_RELEASE_MS = 200L;

    private final Map<Integer, Bind> pointers = new LinkedHashMap<>();
    private Layout layout = new Layout();
    private Sink sink;
    private Probe probe;
    private NowMs nowMs = new SystemNow();
    private float moveX;
    private float moveY;
    private float lookX;
    private float lookY;
    private boolean jumpDown;
    private String lastWhoZeroed = "";
    private long lastAnySampleMs;

    void setLayout(Layout layout) {
        this.layout = layout == null ? new Layout() : layout;
    }

    Layout layout() {
        return layout;
    }

    void setSink(Sink sink) {
        this.sink = sink;
    }

    void setProbe(Probe probe) {
        this.probe = probe;
    }

    void setNowMs(NowMs nowMs) {
        this.nowMs = nowMs == null ? new SystemNow() : nowMs;
    }

    boolean down(int pointerId, float x, float y) {
        if (pointerId == INVALID_POINTER || layout == null) {
            return false;
        }
        if (pointers.containsKey(pointerId)) {
            return false;
        }
        Target hit = layout.hit(x, y);
        if (hit == Target.NONE) {
            return false;
        }
        if (ownerOf(hit) != INVALID_POINTER) {
            return false;
        }
        long now = now();
        Bind bind = new Bind(hit, x, y, now);
        pointers.put(pointerId, bind);
        lastAnySampleMs = now;
        if (hit == Target.JUMP) {
            jumpDown = true;
        }
        publish();
        return true;
    }

    Target move(int pointerId, float x, float y) {
        Bind bind = pointers.get(pointerId);
        if (bind == null) {
            return Target.NONE;
        }
        long now = now();
        bind.lastSampleMs = now;
        lastAnySampleMs = now;
        if (bind.target == Target.MOVE || bind.target == Target.LOOK) {
            applyStick(bind, x, y);
            if (bind.target == Target.MOVE) {
                moveX = bind.axisX;
                moveY = bind.axisY;
            } else {
                lookX = bind.axisX;
                lookY = bind.axisY;
            }
            publish();
        }
        return bind.target;
    }

    Target up(int pointerId, String reason) {
        Bind bind = pointers.remove(pointerId);
        if (bind == null) {
            return Target.NONE;
        }
        zeroTarget(bind.target, pointerId, reason, bind.axisX, bind.axisY);
        publish();
        return bind.target;
    }

    /**
     * Treat as all fingers up. Clears every owner and publishes zeros through
     * the sink immediately (does not wait for the next vsync / MOVE).
     */
    void releaseAll(String reason) {
        String who = reason == null || reason.isEmpty() ? "CANCEL" : reason;
        List<Integer> pids = new ArrayList<>(pointers.keySet());
        for (int pid : pids) {
            Bind bind = pointers.remove(pid);
            if (bind != null) {
                zeroTarget(bind.target, pid, who, bind.axisX, bind.axisY);
            }
        }
        moveX = 0.0f;
        moveY = 0.0f;
        lookX = 0.0f;
        lookY = 0.0f;
        jumpDown = false;
        lastWhoZeroed = who;
        publish();
    }

    /**
     * Orphan any owner whose pointerId is missing from the live MotionEvent
     * ({@code findPointerIndex == -1} equivalent).
     */
    void noteLivePointers(int[] liveIds) {
        List<Integer> owned = new ArrayList<>(pointers.keySet());
        for (int pid : owned) {
            if (!containsId(liveIds, pid)) {
                up(pid, "ORPHAN");
            }
        }
    }

    void tick(long jniLagMs) {
        tick(jniLagMs, null);
    }

    /**
     * Latch-free sample. A watchdog tick with {@code liveIds == null} does
     * not kill a held-still stick (Android does not send MOVE while a finger
     * is stationary). Timeout / LAG require an empty live pointer set — the
     * stream reported no fingers — plus a stale sample. A non-empty live set
     * orphans owners missing from it immediately.
     */
    void tick(long jniLagMs, int[] liveIds) {
        if (liveIds != null && liveIds.length > 0) {
            noteLivePointers(liveIds);
            return;
        }
        if (pointers.isEmpty() || liveIds == null) {
            return;
        }
        long now = now();
        long sampleAge = lastAnySampleMs <= 0L ? Long.MAX_VALUE : now - lastAnySampleMs;
        if (sampleAge <= SAMPLE_TIMEOUT_MS) {
            return;
        }
        if (jniLagMs >= JNI_LAG_RELEASE_MS) {
            releaseAll("LAG");
            return;
        }
        releaseAll("TIMEOUT");
    }

    void publish() {
        if (sink == null) {
            return;
        }
        sink.setMove(moveX, moveY);
        sink.setLook(lookX, lookY);
        sink.setJump(jumpDown);
    }

    int ownerOf(Target target) {
        for (Map.Entry<Integer, Bind> entry : pointers.entrySet()) {
            if (entry.getValue().target == target) {
                return entry.getKey();
            }
        }
        return INVALID_POINTER;
    }

    boolean hasPointer(int pointerId) {
        return pointers.containsKey(pointerId);
    }

    Target targetOf(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? Target.NONE : bind.target;
    }

    boolean anyPressed() {
        return !pointers.isEmpty();
    }

    float moveX() {
        return moveX;
    }

    float moveY() {
        return moveY;
    }

    float lookX() {
        return lookX;
    }

    float lookY() {
        return lookY;
    }

    boolean jumpDown() {
        return jumpDown;
    }

    float originX(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.originX;
    }

    float originY(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.originY;
    }

    float axisX(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.axisX;
    }

    float axisY(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.axisY;
    }

    long sampleAgeMs(Target target) {
        int pid = ownerOf(target);
        if (pid == INVALID_POINTER) {
            return 0L;
        }
        Bind bind = pointers.get(pid);
        if (bind == null) {
            return 0L;
        }
        long age = now() - bind.lastSampleMs;
        return Math.max(0L, age);
    }

    String lastWhoZeroed() {
        return lastWhoZeroed;
    }

    int[] pointerIds() {
        int[] ids = new int[pointers.size()];
        int i = 0;
        for (Integer pid : pointers.keySet()) {
            ids[i++] = pid;
        }
        return ids;
    }

    private void applyStick(Bind bind, float x, float y) {
        float r = Math.max(layout.stickRadius, 1.0f);
        float dx = x - bind.originX;
        float dy = y - bind.originY;
        float magnitude = (float) Math.hypot(dx, dy);
        if (magnitude > r) {
            dx = dx * r / magnitude;
            dy = dy * r / magnitude;
        }
        bind.axisX = clamp(dx / r);
        bind.axisY = clamp(dy / r);
    }

    private void zeroTarget(Target target, int pointerId, String reason, float axisX, float axisY) {
        lastWhoZeroed = reason == null ? "" : reason;
        switch (target) {
            case MOVE:
                moveX = 0.0f;
                moveY = 0.0f;
                notifyZero("left", lastWhoZeroed, pointerId, axisX, axisY);
                break;
            case LOOK:
                lookX = 0.0f;
                lookY = 0.0f;
                notifyZero("right", lastWhoZeroed, pointerId, axisX, axisY);
                break;
            case JUMP:
                jumpDown = false;
                notifyZero("jump", lastWhoZeroed, pointerId, 0.0f, 0.0f);
                break;
            default:
                break;
        }
    }

    private void notifyZero(String zone, String reason, int pointerId, float axisX, float axisY) {
        if (probe != null) {
            probe.onZero(zone, reason, pointerId, axisX, axisY);
        }
    }

    private long now() {
        return nowMs.nowMs();
    }

    private static boolean containsId(int[] ids, int pointerId) {
        if (ids == null) {
            return false;
        }
        for (int id : ids) {
            if (id == pointerId) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static final class SystemNow implements NowMs {
        @Override
        public long nowMs() {
            return System.nanoTime() / 1_000_000L;
        }
    }
}
