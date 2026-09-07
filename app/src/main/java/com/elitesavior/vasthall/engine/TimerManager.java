package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tick-driven timers. Unreal mental model: {@code FTimerManager} / {@code SetTimer}.
 *
 * <p>No background threads. Advance remaining time with {@link #tick(float)}
 * from {@link World#tick(float)} (the activity frame callback).
 *
 * <p>Single-threaded: set / clear / pause / tick from the world thread.
 */
public final class TimerManager {
    private static final int MAX_CATCH_UP = 8;

    private static final class Entry {
        final TimerHandle handle;
        final Runnable callback;
        final float rate;
        final boolean looping;
        float remaining;
        boolean paused;

        Entry(TimerHandle handle, Runnable callback, float rate, boolean looping) {
            this.handle = handle;
            this.callback = callback;
            this.rate = rate;
            this.looping = looping;
            this.remaining = rate;
        }
    }

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private long nextId = 1L;

    /**
     * Unreal {@code SetTimer}: fire {@code callback} after {@code rateSeconds}.
     * Looping timers restart at the same rate after each fire.
     */
    public TimerHandle setTimer(Runnable callback, float rateSeconds, boolean looping) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        if (rateSeconds <= 0.0f || Float.isNaN(rateSeconds) || Float.isInfinite(rateSeconds)) {
            throw new IllegalArgumentException("timer rate");
        }
        TimerHandle handle = new TimerHandle(nextId++);
        entries.put(handle.id(), new Entry(handle, callback, rateSeconds, looping));
        return handle;
    }

    /** Unreal {@code ClearTimer}. Null or already-invalid handles are ignored. */
    public void clearTimer(TimerHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        Entry removed = entries.remove(handle.id());
        handle.invalidate();
        if (removed != null && removed.handle != handle) {
            removed.handle.invalidate();
        }
    }

    public void pauseTimer(TimerHandle handle) {
        Entry entry = find(handle);
        if (entry != null) {
            entry.paused = true;
        }
    }

    public void unPauseTimer(TimerHandle handle) {
        Entry entry = find(handle);
        if (entry != null) {
            entry.paused = false;
        }
    }

    /** Active means present and not paused. */
    public boolean isTimerActive(TimerHandle handle) {
        Entry entry = find(handle);
        return entry != null && !entry.paused;
    }

    public boolean isTimerPaused(TimerHandle handle) {
        Entry entry = find(handle);
        return entry != null && entry.paused;
    }

    /** Remaining seconds, or {@code -1} when the handle is not live. */
    public float getTimerRemaining(TimerHandle handle) {
        Entry entry = find(handle);
        return entry == null ? -1.0f : entry.remaining;
    }

    /** Rate in seconds, or {@code -1} when the handle is not live. */
    public float getTimerRate(TimerHandle handle) {
        Entry entry = find(handle);
        return entry == null ? -1.0f : entry.rate;
    }

    public int timerCount() {
        return entries.size();
    }

    public void clearAll() {
        List<Entry> snapshot = new ArrayList<>(entries.values());
        entries.clear();
        for (Entry entry : snapshot) {
            entry.handle.invalidate();
        }
    }

    public TimerHandle findTimer(long id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.handle;
    }

    public void tick(float deltaSeconds) {
        if (entries.isEmpty() || deltaSeconds <= 0.0f || Float.isNaN(deltaSeconds)) {
            return;
        }
        List<Entry> due = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.paused) {
                continue;
            }
            entry.remaining -= deltaSeconds;
            if (entry.remaining <= 0.0f) {
                due.add(entry);
            }
        }
        for (Entry entry : due) {
            fireDue(entry);
        }
    }

    void appendDump(StringBuilder out) {
        out.append("world.timers=").append(entries.size()).append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            lines.add(String.format(
                    Locale.US,
                    "%d remaining=%.2f rate=%.2f loop=%d paused=%d",
                    entry.handle.id(),
                    entry.remaining,
                    entry.rate,
                    entry.looping ? 1 : 0,
                    entry.paused ? 1 : 0));
        }
        return lines;
    }

    private void fireDue(Entry entry) {
        int fires = 0;
        while (entry.remaining <= 0.0f && fires < MAX_CATCH_UP) {
            if (!entries.containsKey(entry.handle.id())) {
                return;
            }
            if (entry.looping) {
                entry.remaining += entry.rate;
            } else {
                entries.remove(entry.handle.id());
                entry.handle.invalidate();
            }
            fires++;
            entry.callback.run();
            if (!entry.looping) {
                return;
            }
            if (!entries.containsKey(entry.handle.id()) || entry.paused) {
                return;
            }
        }
        if (entry.looping && entry.remaining <= 0.0f && entries.containsKey(entry.handle.id())) {
            entry.remaining = entry.rate;
        }
    }

    private Entry find(TimerHandle handle) {
        if (handle == null || !handle.isValid()) {
            return null;
        }
        return entries.get(handle.id());
    }
}
