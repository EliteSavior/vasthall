package com.elitesavior.vasthall;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * UI-thread MOVE stores axes here. A daemon pump is the only caller of the
 * blocking native setters, so the Vulkan engine mutex cannot stall touch.
 * The pump applies every tick (no change-detection skip, no 20 Hz cap).
 * After a slow native consume, the next tick applies zeros instead of
 * republishing a frozen full-deflection vector. {@link #setMove}/{@link #setLook}/{@link #setJump}
 * unpark the pump immediately so CANCEL zeros do not wait for the next vsync frame.
 */
final class HudAxes {
    interface NativeSink {
        void setMove(float x, float y);
        void setLook(float x, float y);
        void setJump(boolean down);
    }

    /** Live-owner lag gate; must match {@link FlatPadRouter#JNI_LAG_AGEOUT_MS}. */
    static final long STALE_CONSUME_MS = 96L;
    static final float AXIS_EPS = 0.04f;

    private final AtomicInteger moveX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger moveY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger lookX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger lookY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger jump = new AtomicInteger(0);
    private final AtomicInteger consumedMoveX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger consumedMoveY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger consumedLookX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger consumedLookY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger consumedJump = new AtomicInteger(0);
    private final NativeSink sink;
    private volatile boolean running;
    private volatile Thread pump;
    private volatile long lastNativeConsumeNs = System.nanoTime();
    private volatile long lastChangeNs = System.nanoTime();
    private volatile long lastConsumeDurationMs;
    private int lastMoveBits = Float.floatToIntBits(0.0f);
    private int lastMoveBitsY = Float.floatToIntBits(0.0f);
    private int lastLookBits = Float.floatToIntBits(0.0f);
    private int lastLookBitsY = Float.floatToIntBits(0.0f);

    HudAxes(NativeSink sink) {
        this.sink = sink;
    }

    void setMove(float x, float y) {
        int bx = Float.floatToIntBits(x);
        int by = Float.floatToIntBits(y);
        if (bx != lastMoveBits || by != lastMoveBitsY) {
            lastMoveBits = bx;
            lastMoveBitsY = by;
            lastChangeNs = System.nanoTime();
        }
        moveX.set(bx);
        moveY.set(by);
        pulse();
    }

    void setLook(float x, float y) {
        int bx = Float.floatToIntBits(x);
        int by = Float.floatToIntBits(y);
        if (bx != lastLookBits || by != lastLookBitsY) {
            lastLookBits = bx;
            lastLookBitsY = by;
            lastChangeNs = System.nanoTime();
        }
        lookX.set(bx);
        lookY.set(by);
        pulse();
    }

    void setJump(boolean down) {
        jump.set(down ? 1 : 0);
        pulse();
    }

    void pulse() {
        LockSupport.unpark(pump);
    }

    long jniLagMs() {
        long lag = (System.nanoTime() - lastNativeConsumeNs) / 1_000_000L;
        return Math.max(0L, lag);
    }

    float publishedMoveX() {
        return bits(moveX);
    }

    float publishedMoveY() {
        return bits(moveY);
    }

    float publishedLookX() {
        return bits(lookX);
    }

    float publishedLookY() {
        return bits(lookY);
    }

    boolean publishedJump() {
        return jump.get() != 0;
    }

    float consumedMoveX() {
        return bits(consumedMoveX);
    }

    float consumedMoveY() {
        return bits(consumedMoveY);
    }

    float consumedLookX() {
        return bits(consumedLookX);
    }

    float consumedLookY() {
        return bits(consumedLookY);
    }

    boolean consumedJump() {
        return consumedJump.get() != 0;
    }

    private static float bits(AtomicInteger cell) {
        return Float.intBitsToFloat(cell.get());
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        lastNativeConsumeNs = System.nanoTime();
        lastChangeNs = System.nanoTime();
        lastConsumeDurationMs = 0L;
        Thread thread = new Thread(this::loop, "hall-axis-pump");
        thread.setDaemon(true);
        pump = thread;
        thread.start();
    }

    void stop() {
        running = false;
        Thread thread = pump;
        LockSupport.unpark(thread);
        if (thread != null) {
            try {
                thread.join(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        pump = null;
    }

    private void loop() {
        while (running) {
            float mx = Float.intBitsToFloat(moveX.get());
            float my = Float.intBitsToFloat(moveY.get());
            float lx = Float.intBitsToFloat(lookX.get());
            float ly = Float.intBitsToFloat(lookY.get());
            boolean down = jump.get() != 0;
            long nowNs = System.nanoTime();
            long staleMs = Math.max(0L, (nowNs - lastChangeNs) / 1_000_000L);
            if (lastConsumeDurationMs >= STALE_CONSUME_MS && staleMs >= STALE_CONSUME_MS) {
                if (Math.hypot(mx, my) > AXIS_EPS || Math.hypot(lx, ly) > AXIS_EPS) {
                    mx = 0.0f;
                    my = 0.0f;
                    lx = 0.0f;
                    ly = 0.0f;
                }
            }
            long consumeStart = System.nanoTime();
            sink.setMove(mx, my);
            sink.setLook(lx, ly);
            sink.setJump(down);
            lastConsumeDurationMs = Math.max(0L, (System.nanoTime() - consumeStart) / 1_000_000L);
            consumedMoveX.set(Float.floatToIntBits(mx));
            consumedMoveY.set(Float.floatToIntBits(my));
            consumedLookX.set(Float.floatToIntBits(lx));
            consumedLookY.set(Float.floatToIntBits(ly));
            consumedJump.set(down ? 1 : 0);
            lastNativeConsumeNs = System.nanoTime();
            if (running) {
                // Vsync pulse() wakes this; 8ms is a fallback, not a 20 Hz cap.
                LockSupport.parkNanos(8_000_000L);
            }
        }
    }
}
