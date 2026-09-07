package com.elitesavior.vasthall;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * UI-thread MOVE stores axes here. A daemon pump is the only caller of the
 * blocking native setters, so the Vulkan engine mutex cannot stall touch.
 * The pump applies every tick (no change-detection skip, no 20 Hz cap).
 */
final class HudAxes {
    interface NativeSink {
        void setMove(float x, float y);
        void setLook(float x, float y);
        void setJump(boolean down);
    }

    private final AtomicInteger moveX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger moveY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger lookX = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger lookY = new AtomicInteger(Float.floatToIntBits(0.0f));
    private final AtomicInteger jump = new AtomicInteger(0);
    private final NativeSink sink;
    private volatile boolean running;
    private volatile Thread pump;
    private volatile long lastNativeConsumeNs = System.nanoTime();

    HudAxes(NativeSink sink) {
        this.sink = sink;
    }

    void setMove(float x, float y) {
        moveX.set(Float.floatToIntBits(x));
        moveY.set(Float.floatToIntBits(y));
        pulse();
    }

    void setLook(float x, float y) {
        lookX.set(Float.floatToIntBits(x));
        lookY.set(Float.floatToIntBits(y));
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

    void start() {
        if (running) {
            return;
        }
        running = true;
        lastNativeConsumeNs = System.nanoTime();
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
            sink.setMove(mx, my);
            sink.setLook(lx, ly);
            sink.setJump(down);
            lastNativeConsumeNs = System.nanoTime();
            if (running) {
                // Vsync pulse() wakes this; 8ms is a fallback, not a 20 Hz cap.
                LockSupport.parkNanos(8_000_000L);
            }
        }
    }
}
