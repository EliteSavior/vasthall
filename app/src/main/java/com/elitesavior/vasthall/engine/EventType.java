package com.elitesavior.vasthall.engine;

import java.util.Objects;

/**
 * Named typed event key. Unreal mental model: a declared multicast
 * ({@code DECLARE_MULTICAST_DELEGATE_OneParam}) or Gameplay Tag on the
 * message router.
 *
 * <p>Engine hooks use the constants below. Gameplay code may add its own
 * with {@link #of(String, Class)} — same name must keep the same payload
 * class. Kotlin: {@code events.bind(EventType.LEVEL_LOADED) { … }}.
 */
public final class EventType<T> {
    public static final EventType<LevelEvent> LEVEL_LOADED =
            of("LevelLoaded", LevelEvent.class);
    public static final EventType<LevelEvent> LEVEL_UNLOADED =
            of("LevelUnloaded", LevelEvent.class);
    public static final EventType<ActorEvent> ACTOR_SPAWNED =
            of("ActorSpawned", ActorEvent.class);
    public static final EventType<ActorEvent> ACTOR_DESTROYED =
            of("ActorDestroyed", ActorEvent.class);
    public static final EventType<OverlapEvent> BEGIN_OVERLAP =
            of("BeginOverlap", OverlapEvent.class);
    public static final EventType<OverlapEvent> END_OVERLAP =
            of("EndOverlap", OverlapEvent.class);

    private final String name;
    private final Class<T> payloadType;

    private EventType(String name, Class<T> payloadType) {
        this.name = name;
        this.payloadType = payloadType;
    }

    public static <T> EventType<T> of(String name, Class<T> payloadType) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("event name");
        }
        if (payloadType == null) {
            throw new IllegalArgumentException("payload type");
        }
        return new EventType<>(name.trim(), payloadType);
    }

    public String name() {
        return name;
    }

    public Class<T> payloadType() {
        return payloadType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventType<?> that)) {
            return false;
        }
        return name.equals(that.name) && payloadType.equals(that.payloadType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, payloadType);
    }

    @Override
    public String toString() {
        return name;
    }
}
