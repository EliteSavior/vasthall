package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventDispatcherTest {
    private EventDispatcher events;

    @Before
    public void setUp() {
        events = new EventDispatcher();
    }

    @Test
    public void bindBroadcastUnbindIsTypeSafe() {
        List<String> names = new ArrayList<>();
        DelegateHandle handle = events.bind(EventType.LEVEL_LOADED, event -> names.add(event.levelName()));

        events.broadcast(EventType.LEVEL_LOADED, new LevelEvent(null, "Hall"));
        events.broadcast(EventType.LEVEL_UNLOADED, new LevelEvent(null, "Hall"));

        assertEquals(List.of("Hall"), names);
        assertEquals(1, events.listenerCount());
        assertEquals(1, events.listenerCount(EventType.LEVEL_LOADED));
        assertEquals(0, events.listenerCount(EventType.LEVEL_UNLOADED));
        assertTrue(handle.isValid());

        events.unbind(handle);
        events.broadcast(EventType.LEVEL_LOADED, new LevelEvent(null, "Side"));

        assertEquals(List.of("Hall"), names);
        assertFalse(handle.isValid());
        assertEquals(0, events.listenerCount());
    }

    @Test
    public void customEventTypeSharesTheBusByName() {
        EventType<String> declared = EventType.of("Ping", String.class);
        EventType<String> same = EventType.of("Ping", String.class);
        List<String> heard = new ArrayList<>();
        events.bind(declared, heard::add);
        events.broadcast(same, "hi");
        assertEquals(List.of("hi"), heard);
    }

    @Test
    public void payloadMismatchOnSameNameIsRejected() {
        events.bind(EventType.of("Ping", String.class), ignored -> { });
        try {
            events.bind(EventType.of("Ping", Integer.class), ignored -> { });
            fail("expected payload mismatch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("payload"));
        }
    }

    @Test
    public void unbindLeavesNoListenerForLaterBroadcast() {
        AtomicInteger calls = new AtomicInteger();
        DelegateHandle handle = events.bind(EventType.ACTOR_SPAWNED, ignored -> calls.incrementAndGet());
        events.unbind(handle);
        events.broadcast(EventType.ACTOR_SPAWNED, new ActorEvent(null, null));
        assertEquals(0, calls.get());
        assertEquals(0, events.listenerCount(EventType.ACTOR_SPAWNED));
        assertFalse(events.isBound(handle));
    }

    @Test
    public void unbindDoesNotRemoveADifferentEventType() {
        AtomicInteger loaded = new AtomicInteger();
        AtomicInteger spawned = new AtomicInteger();
        events.bind(EventType.LEVEL_LOADED, ignored -> loaded.incrementAndGet());
        DelegateHandle later = events.bind(EventType.ACTOR_SPAWNED, ignored -> spawned.incrementAndGet());

        events.unbind(later);
        events.broadcast(EventType.LEVEL_LOADED, new LevelEvent(null, "Hall"));
        events.broadcast(EventType.ACTOR_SPAWNED, new ActorEvent(null, null));

        assertEquals(1, loaded.get());
        assertEquals(0, spawned.get());
        assertEquals(1, events.listenerCount());
        assertFalse(later.isValid());
    }

    @Test
    public void ofUnbindDoesNotInvalidateAForeignHandle() {
        AtomicInteger loaded = new AtomicInteger();
        DelegateHandle handle = events.bind(EventType.LEVEL_LOADED, ignored -> loaded.incrementAndGet());

        events.of(EventType.ACTOR_SPAWNED).unbind(handle);
        events.broadcast(EventType.LEVEL_LOADED, new LevelEvent(null, "Hall"));

        assertTrue(handle.isValid());
        assertEquals(1, loaded.get());
    }

    @Test
    public void ofReturnsSameDelegateForAType() {
        MulticastDelegate<LevelEvent> first = events.of(EventType.LEVEL_LOADED);
        MulticastDelegate<LevelEvent> second = events.of(EventType.LEVEL_LOADED);
        assertSame(first, second);
    }
}
