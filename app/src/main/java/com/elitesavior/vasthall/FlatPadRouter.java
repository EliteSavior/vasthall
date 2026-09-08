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
 * Tick sample timeout and JNI lag with no live pointers also force zeros.
 * Identical/stale samples age out published axes while owners stay live.
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
        float lastX;
        float lastY;
        long lastSampleMs;
        long lastEventTimeMs;
        long lastFreshMs;
        long lastFreshEventTimeMs;
        boolean agedOut;

        Bind(Target target, float originX, float originY, long nowMs) {
            this.target = target;
            this.originX = originX;
            this.originY = originY;
            this.lastX = originX;
            this.lastY = originY;
            this.lastSampleMs = nowMs;
            this.lastEventTimeMs = nowMs;
            this.lastFreshMs = nowMs;
            this.lastFreshEventTimeMs = nowMs;
        }
    }

    static final int INVALID_POINTER = -1;
    /** Stick sample age that forces owner death on an empty live set (~80–120ms). */
    static final long SAMPLE_TIMEOUT_MS = 100L;
    /** Pump lag with no live pointers that forces {@link #releaseAll}. */
    static final long JNI_LAG_RELEASE_MS = 200L;
    /** Identical xy / eventTime while owners live (~50–80ms). */
    static final long IDENTICAL_STALE_MS = 64L;
    /** Live-owner JNI lag / freshness age-out (~80–150ms). */
    static final long JNI_LAG_AGEOUT_MS = 96L;
    /** Held-still (no MOVE) keep-last then soft decay (~120–200ms). */
    static final long HOLD_DECAY_MS = 160L;
    static final float XY_DEADBAND_PX = 0.75f;
    static final float HOLD_DECAY_FACTOR = 0.45f;
    static final float AXIS_ZERO_EPS = 0.02f;

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
    private long lastAnyFreshMs;

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
        return down(pointerId, x, y, now());
    }

    boolean down(int pointerId, float x, float y, long eventTimeMs) {
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
        bind.lastEventTimeMs = eventTimeMs;
        bind.lastFreshEventTimeMs = eventTimeMs;
        pointers.put(pointerId, bind);
        lastAnySampleMs = now;
        lastAnyFreshMs = now;
        lastWhoZeroed = "";
        if (hit == Target.JUMP) {
            jumpDown = true;
        }
        publish();
        return true;
    }

    Target move(int pointerId, float x, float y) {
        return move(pointerId, x, y, now(), 0L);
    }

    Target move(int pointerId, float x, float y, long eventTimeMs) {
        return move(pointerId, x, y, eventTimeMs, 0L);
    }

    Target move(int pointerId, float x, float y, long eventTimeMs, long jniLagMs) {
        Bind bind = pointers.get(pointerId);
        if (bind == null) {
            return Target.NONE;
        }
        long now = now();
        boolean fresh = isFresh(bind, x, y, eventTimeMs);
        bind.lastSampleMs = now;
        bind.lastX = x;
        bind.lastY = y;
        bind.lastEventTimeMs = eventTimeMs;
        lastAnySampleMs = now;
        if (fresh) {
            bind.lastFreshMs = now;
            bind.lastFreshEventTimeMs = eventTimeMs;
            bind.agedOut = false;
            lastAnyFreshMs = now;
            lastWhoZeroed = "";
            if (bind.target == Target.MOVE || bind.target == Target.LOOK) {
                applyStick(bind, x, y);
                writePublished(bind);
                publish();
            }
        } else if (bind.target == Target.MOVE || bind.target == Target.LOOK) {
            ageOutBind(bind, pointerId, now, jniLagMs);
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
     * Watchdog / pump tick. Empty live pointer set plus a stale sample still
     * TIMEOUT/LAG-releases owners (v0.34). A non-empty live set orphans missing
     * ids, then ages out stale published axes <em>without</em> requiring an
     * empty pointer set. {@code liveIds == null} is the vsync pump: do not
     * kill owners; still decay stale/identical samples.
     */
    void tick(long jniLagMs, int[] liveIds) {
        if (liveIds != null && liveIds.length > 0) {
            noteLivePointers(liveIds);
        } else if (liveIds != null && liveIds.length == 0 && !pointers.isEmpty()) {
            long now = now();
            long sampleAge = lastAnyFreshMs <= 0L ? Long.MAX_VALUE : now - lastAnyFreshMs;
            if (sampleAge <= SAMPLE_TIMEOUT_MS) {
                return;
            }
            if (jniLagMs >= JNI_LAG_RELEASE_MS) {
                releaseAll("LAG");
                return;
            }
            releaseAll("TIMEOUT");
            return;
        }
        if (pointers.isEmpty()) {
            return;
        }
        long now = now();
        List<Map.Entry<Integer, Bind>> owned = new ArrayList<>(pointers.entrySet());
        for (Map.Entry<Integer, Bind> entry : owned) {
            ageOutBind(entry.getValue(), entry.getKey(), now, jniLagMs);
        }
        publish();
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
        long age = now() - bind.lastFreshMs;
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

    private boolean isFresh(Bind bind, float x, float y, long eventTimeMs) {
        float dx = x - bind.lastX;
        float dy = y - bind.lastY;
        boolean xyChanged = Math.hypot(dx, dy) > XY_DEADBAND_PX;
        if (!xyChanged) {
            return false;
        }
        if (eventTimeMs > 0L && eventTimeMs < bind.lastFreshEventTimeMs) {
            return false;
        }
        return true;
    }

    private boolean ageOutBind(Bind bind, int pointerId, long now, long jniLagMs) {
        if (bind.target != Target.MOVE && bind.target != Target.LOOK) {
            return false;
        }
        long freshAge = now - bind.lastFreshMs;
        boolean hadIdenticalMove = bind.lastSampleMs > bind.lastFreshMs;
        if (jniLagMs >= JNI_LAG_AGEOUT_MS && freshAge >= JNI_LAG_AGEOUT_MS) {
            return forceRoleZero(bind, pointerId, "LAG");
        }
        if (hadIdenticalMove && freshAge >= IDENTICAL_STALE_MS) {
            return forceRoleZero(bind, pointerId, "IDENTICAL_SAMPLE");
        }
        if (!hadIdenticalMove && freshAge >= HOLD_DECAY_MS) {
            return softDecay(bind, pointerId);
        }
        return false;
    }

    private boolean forceRoleZero(Bind bind, int pointerId, String reason) {
        float beforeX = publishedX(bind.target);
        float beforeY = publishedY(bind.target);
        bind.axisX = 0.0f;
        bind.axisY = 0.0f;
        bind.agedOut = true;
        writePublished(bind);
        if (Math.abs(beforeX) > AXIS_ZERO_EPS || Math.abs(beforeY) > AXIS_ZERO_EPS
                || !reason.equals(lastWhoZeroed)) {
            lastWhoZeroed = reason;
            notifyZero(zoneName(bind.target), reason, pointerId, beforeX, beforeY);
            publish();
            return true;
        }
        lastWhoZeroed = reason;
        return false;
    }

    private boolean softDecay(Bind bind, int pointerId) {
        float beforeX = bind.axisX;
        float beforeY = bind.axisY;
        bind.axisX *= HOLD_DECAY_FACTOR;
        bind.axisY *= HOLD_DECAY_FACTOR;
        if (Math.abs(bind.axisX) < AXIS_ZERO_EPS) {
            bind.axisX = 0.0f;
        }
        if (Math.abs(bind.axisY) < AXIS_ZERO_EPS) {
            bind.axisY = 0.0f;
        }
        bind.agedOut = true;
        writePublished(bind);
        lastWhoZeroed = "STALE_SAMPLE";
        if (Math.abs(beforeX) > AXIS_ZERO_EPS || Math.abs(beforeY) > AXIS_ZERO_EPS) {
            notifyZero(zoneName(bind.target), "STALE_SAMPLE", pointerId, beforeX, beforeY);
            publish();
            return true;
        }
        return false;
    }

    private void writePublished(Bind bind) {
        if (bind.target == Target.MOVE) {
            moveX = bind.axisX;
            moveY = bind.axisY;
        } else if (bind.target == Target.LOOK) {
            lookX = bind.axisX;
            lookY = bind.axisY;
        }
    }

    private float publishedX(Target target) {
        return target == Target.MOVE ? moveX : lookX;
    }

    private float publishedY(Target target) {
        return target == Target.MOVE ? moveY : lookY;
    }

    private static String zoneName(Target target) {
        if (target == Target.MOVE) {
            return "left";
        }
        if (target == Target.LOOK) {
            return "right";
        }
        return "jump";
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
