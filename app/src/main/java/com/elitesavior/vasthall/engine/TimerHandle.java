package com.elitesavior.vasthall.engine;

/**
 * Opaque timer id. Unreal mental model: {@code FTimerHandle}.
 *
 * <p>A handle is valid while the {@link TimerManager} still owns that timer.
 * One-shot fire and {@link TimerManager#clearTimer(TimerHandle)} invalidate it.
 */
public final class TimerHandle {
    private long id;

    public TimerHandle() {
        this(0L);
    }

    TimerHandle(long id) {
        this.id = id;
    }

    public boolean isValid() {
        return id != 0L;
    }

    public long id() {
        return id;
    }

    void invalidate() {
        id = 0L;
    }
}
