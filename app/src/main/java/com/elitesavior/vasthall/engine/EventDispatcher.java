package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * World-owned event bus. Unreal mental model: Event Dispatcher /
 * Gameplay Message Router — typed {@link EventType} keys, multicast
 * bind / unbind / broadcast.
 *
 * <p>Single-threaded: call from the world thread. Custom gameplay events
 * use {@link EventType#of(String, Class)} with a documented name.
 */
public final class EventDispatcher {
    private final Map<String, MulticastDelegate<?>> buses = new LinkedHashMap<>();
    private final Map<String, EventType<?>> types = new LinkedHashMap<>();
    private final Map<DelegateHandle, MulticastDelegate<?>> owners = new LinkedHashMap<>();

    public <T> MulticastDelegate<T> of(EventType<T> type) {
        EventType<T> key = requireType(type);
        @SuppressWarnings("unchecked")
        MulticastDelegate<T> existing = (MulticastDelegate<T>) buses.get(key.name());
        if (existing != null) {
            EventType<?> registered = types.get(key.name());
            if (registered != null && !registered.payloadType().equals(key.payloadType())) {
                throw new IllegalArgumentException("event type payload mismatch: " + key.name());
            }
            return existing;
        }
        MulticastDelegate<T> created = new MulticastDelegate<>();
        buses.put(key.name(), created);
        types.put(key.name(), key);
        return created;
    }

    public <T> DelegateHandle bind(EventType<T> type, Consumer<T> listener) {
        MulticastDelegate<T> bus = of(type);
        DelegateHandle handle = bus.bind(listener);
        owners.put(handle, bus);
        return handle;
    }

    public void unbind(DelegateHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        MulticastDelegate<?> owned = owners.remove(handle);
        if (owned != null) {
            owned.unbind(handle);
            return;
        }
        for (MulticastDelegate<?> bus : buses.values()) {
            if (bus.isBound(handle)) {
                bus.unbind(handle);
                return;
            }
        }
    }

    public <T> void broadcast(EventType<T> type, T payload) {
        of(type).broadcast(payload);
    }

    public int listenerCount() {
        int count = 0;
        for (MulticastDelegate<?> bus : buses.values()) {
            count += bus.listenerCount();
        }
        return count;
    }

    public int listenerCount(EventType<?> type) {
        if (type == null) {
            return 0;
        }
        MulticastDelegate<?> bus = buses.get(type.name());
        return bus == null ? 0 : bus.listenerCount();
    }

    public boolean isBound(DelegateHandle handle) {
        if (handle == null || !handle.isValid()) {
            return false;
        }
        MulticastDelegate<?> owned = owners.get(handle);
        if (owned != null) {
            return owned.isBound(handle);
        }
        for (MulticastDelegate<?> bus : buses.values()) {
            if (bus.isBound(handle)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        List<MulticastDelegate<?>> snapshot = new ArrayList<>(buses.values());
        for (MulticastDelegate<?> bus : snapshot) {
            bus.clear();
        }
        buses.clear();
        types.clear();
        owners.clear();
    }

    void appendDump(StringBuilder out) {
        out.append("world.events=").append(listenerCount()).append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, MulticastDelegate<?>> entry : buses.entrySet()) {
            int count = entry.getValue().listenerCount();
            if (count == 0) {
                continue;
            }
            lines.add(String.format(Locale.US, "%s listeners=%d", entry.getKey(), count));
        }
        return lines;
    }

    private static <T> EventType<T> requireType(EventType<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("event type");
        }
        return type;
    }
}
