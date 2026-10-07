package com.elitesavior.vasthall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for play-time pressed state.
 *
 * A stick, jump button, or key is active only while this table still holds it.
 * MOVE of an unbound pointer never starts a gesture. Occupied exclusive
 * targets reject a second finger. {@link #releaseAll} is the pause/blur/menu
 * flush used so native axes/keys cannot stay held after the user has left.
 */
final class PlayInputMachine {
    enum Target {
        NONE,
        MOVE,
        LOOK,
        JUMP,
        LEGACY
    }

    interface Sink {
        void onBegin(Target target, int pointerId);

        void onEnd(Target target, int pointerId, String reason);

        void onKey(int keyCode, boolean down);
    }

    static final int INVALID_POINTER = -1;

    private final Map<Integer, Target> pointers = new LinkedHashMap<>();
    private final Map<Integer, Boolean> keys = new LinkedHashMap<>();
    private Sink sink;

    void setSink(Sink sink) {
        this.sink = sink;
    }

    boolean pointerDown(int pointerId, Target hit) {
        if (pointerId == INVALID_POINTER || hit == null || hit == Target.NONE) {
            return false;
        }
        if (pointers.containsKey(pointerId)) {
            return false;
        }
        if (hit != Target.LEGACY && ownerOf(hit) != INVALID_POINTER) {
            return false;
        }
        pointers.put(pointerId, hit);
        if (sink != null) {
            sink.onBegin(hit, pointerId);
        }
        return true;
    }

    Target pointerMove(int pointerId) {
        Target target = pointers.get(pointerId);
        return target == null ? Target.NONE : target;
    }

    Target pointerUp(int pointerId, String reason) {
        Target target = pointers.remove(pointerId);
        if (target == null) {
            return Target.NONE;
        }
        if (sink != null) {
            sink.onEnd(target, pointerId, reason);
        }
        return target;
    }

    boolean keyDown(int keyCode) {
        if (Boolean.TRUE.equals(keys.get(keyCode))) {
            return false;
        }
        keys.put(keyCode, true);
        if (sink != null) {
            sink.onKey(keyCode, true);
        }
        return true;
    }

    boolean keyUp(int keyCode) {
        if (!Boolean.TRUE.equals(keys.remove(keyCode))) {
            return false;
        }
        if (sink != null) {
            sink.onKey(keyCode, false);
        }
        return true;
    }

    void releaseAll(String reason) {
        List<Integer> pids = new ArrayList<>(pointers.keySet());
        for (int pid : pids) {
            pointerUp(pid, reason);
        }
        List<Integer> codes = new ArrayList<>(keys.keySet());
        for (int code : codes) {
            keyUp(code);
        }
    }

    int ownerOf(Target target) {
        for (Map.Entry<Integer, Target> entry : pointers.entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        return INVALID_POINTER;
    }

    boolean hasPointer(int pointerId) {
        return pointers.containsKey(pointerId);
    }

    Target targetOf(int pointerId) {
        Target target = pointers.get(pointerId);
        return target == null ? Target.NONE : target;
    }

    boolean isKeyDown(int keyCode) {
        return Boolean.TRUE.equals(keys.get(keyCode));
    }

    boolean anyPressed() {
        return !pointers.isEmpty() || !keys.isEmpty();
    }

    int[] pointerIds() {
        int[] ids = new int[pointers.size()];
        int i = 0;
        for (Integer pid : pointers.keySet()) {
            ids[i++] = pid;
        }
        return ids;
    }
}
