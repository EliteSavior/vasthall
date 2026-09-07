package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class MulticastDelegateTest {
    private MulticastDelegate<String> events;

    @Before
    public void setUp() {
        events = new MulticastDelegate<>();
    }

    @Test
    public void bindThenBroadcastInvokesListener() {
        List<String> heard = new ArrayList<>();
        DelegateHandle handle = events.bind(heard::add);

        assertTrue(handle.isValid());
        assertEquals(1, events.listenerCount());

        events.broadcast("ping");

        assertEquals(List.of("ping"), heard);
        assertTrue(handle.isValid());
    }

    @Test
    public void unbindStopsDeliveryAndDropsTheListener() {
        AtomicInteger calls = new AtomicInteger();
        DelegateHandle handle = events.bind(ignored -> calls.incrementAndGet());

        events.unbind(handle);
        events.broadcast("after");

        assertEquals(0, calls.get());
        assertFalse(handle.isValid());
        assertEquals(0, events.listenerCount());
    }

    @Test
    public void unbindDoesNotLeakAcrossLaterBroadcasts() {
        List<String> heard = new ArrayList<>();
        Consumer<String> listener = heard::add;
        DelegateHandle handle = events.bind(listener);

        events.broadcast("keep");
        events.unbind(handle);
        events.broadcast("drop");

        assertEquals(List.of("keep"), heard);
        assertEquals(0, events.listenerCount());
        assertFalse(events.isBound(handle));
    }

    @Test
    public void multicastFiresInBindOrder() {
        List<String> order = new ArrayList<>();
        events.bind(payload -> order.add("a:" + payload));
        events.bind(payload -> order.add("b:" + payload));

        events.broadcast("x");

        assertEquals(List.of("a:x", "b:x"), order);
        assertEquals(2, events.listenerCount());
    }

    @Test
    public void unbindNullOrInvalidIsNoOp() {
        events.unbind(null);
        events.unbind(new DelegateHandle());
        events.broadcast("noop");
        assertEquals(0, events.listenerCount());
    }

    @Test
    public void listenerMayUnbindSelfDuringBroadcast() {
        AtomicInteger calls = new AtomicInteger();
        DelegateHandle[] handle = new DelegateHandle[1];
        handle[0] = events.bind(payload -> {
            calls.incrementAndGet();
            events.unbind(handle[0]);
        });

        events.broadcast("once");
        events.broadcast("again");

        assertEquals(1, calls.get());
        assertEquals(0, events.listenerCount());
    }

    @Test
    public void bindDuringBroadcastDoesNotFireThisTime() {
        List<String> heard = new ArrayList<>();
        boolean[] added = {false};
        events.bind(payload -> {
            heard.add("first:" + payload);
            if (!added[0]) {
                added[0] = true;
                events.bind(inner -> heard.add("late:" + inner));
            }
        });

        events.broadcast("now");
        events.broadcast("next");

        assertEquals(List.of("first:now", "first:next", "late:next"), heard);
        assertEquals(2, events.listenerCount());
    }

    @Test
    public void clearInvalidatesEveryHandle() {
        AtomicInteger calls = new AtomicInteger();
        DelegateHandle a = events.bind(ignored -> calls.incrementAndGet());
        DelegateHandle b = events.bind(ignored -> calls.incrementAndGet());

        events.clear();
        events.broadcast("gone");

        assertEquals(0, calls.get());
        assertFalse(a.isValid());
        assertFalse(b.isValid());
        assertEquals(0, events.listenerCount());
    }

    @Test
    public void rejectsNullListener() {
        try {
            events.bind(null);
            fail("expected listener");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("listener"));
        }
        assertEquals(0, events.listenerCount());
    }
}
