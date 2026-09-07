package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Type-safe multicast. Unreal mental model: {@code FMulticastDelegate} —
 * {@code Add} / {@code Remove} / {@code Broadcast}.
 *
 * <p>Single-threaded: bind / unbind / broadcast from the world thread.
 * Broadcast snapshots listeners so a callback may bind or unbind safely.
 * A listener bound during broadcast is not invoked until the next broadcast.
 */
public final class MulticastDelegate<T> {
    private final Map<Long, Consumer<T>> listeners = new LinkedHashMap<>();
    private final Map<Long, DelegateHandle> handles = new LinkedHashMap<>();
    private long nextId = 1L;

    /** Unreal {@code Add}. Returns a handle for {@link #unbind(DelegateHandle)}. */
    public DelegateHandle bind(Consumer<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        DelegateHandle handle = new DelegateHandle(nextId++);
        listeners.put(handle.id(), listener);
        handles.put(handle.id(), handle);
        return handle;
    }

    /** Unreal {@code Remove}. Null or already-invalid handles are ignored. */
    public void unbind(DelegateHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        listeners.remove(handle.id());
        DelegateHandle owned = handles.remove(handle.id());
        handle.invalidate();
        if (owned != null && owned != handle) {
            owned.invalidate();
        }
    }

    /** Unreal {@code Broadcast}. Listeners run in bind order. */
    public void broadcast(T payload) {
        if (listeners.isEmpty()) {
            return;
        }
        List<Consumer<T>> snapshot = new ArrayList<>(listeners.values());
        for (Consumer<T> listener : snapshot) {
            listener.accept(payload);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }

    public boolean isBound(DelegateHandle handle) {
        return handle != null && handle.isValid() && listeners.containsKey(handle.id());
    }

    public void clear() {
        List<DelegateHandle> snapshot = new ArrayList<>(handles.values());
        listeners.clear();
        handles.clear();
        for (DelegateHandle handle : snapshot) {
            handle.invalidate();
        }
    }
}
