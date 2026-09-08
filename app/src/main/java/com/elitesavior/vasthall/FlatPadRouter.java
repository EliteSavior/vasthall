package com.elitesavior.vasthall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * New pad backend: one table of {@code pointerId → Move | Look | Jump | None}.
 * DOWN binds by math hit-test. MOVE updates only bound ids. Unbound MOVE is
 * ignored (no revive). UP clears that id. CANCEL / pause / scheme switch
 * call {@link #releaseAll}.
 */
final class FlatPadRouter {
    enum Target {
        NONE,
        MOVE,
        LOOK,
        JUMP
    }

    interface Sink {
        void setMove(float x, float y);

        void setLook(float x, float y);

        void setJump(boolean down);
    }

    static final class Layout {
        float width = 1.0f;
        float height = 1.0f;
        float stickRadius = 71.0f;
        float jumpLeft;
        float jumpTop;
        float jumpRight;
        float jumpBottom;

        Target hit(float x, float y) {
            if (x >= jumpLeft && x < jumpRight && y >= jumpTop && y < jumpBottom) {
                return Target.JUMP;
            }
            if (x < width * 0.5f) {
                return Target.MOVE;
            }
            return Target.LOOK;
        }
    }

    private static final class Bind {
        final Target target;
        final float originX;
        final float originY;
        float axisX;
        float axisY;

        Bind(Target target, float originX, float originY) {
            this.target = target;
            this.originX = originX;
            this.originY = originY;
        }
    }

    static final int INVALID_POINTER = -1;

    private final Map<Integer, Bind> pointers = new LinkedHashMap<>();
    private Layout layout = new Layout();
    private Sink sink;
    private float moveX;
    private float moveY;
    private float lookX;
    private float lookY;
    private boolean jumpDown;

    void setLayout(Layout layout) {
        this.layout = layout == null ? new Layout() : layout;
    }

    Layout layout() {
        return layout;
    }

    void setSink(Sink sink) {
        this.sink = sink;
    }

    boolean down(int pointerId, float x, float y) {
        if (pointerId == INVALID_POINTER || layout == null) {
            return false;
        }
        if (pointers.containsKey(pointerId)) {
            return false;
        }
        Target hit = layout.hit(x, y);
        if (hit == Target.NONE) {
            return false;
        }
        if (ownerOf(hit) != INVALID_POINTER) {
            return false;
        }
        Bind bind = new Bind(hit, x, y);
        pointers.put(pointerId, bind);
        if (hit == Target.JUMP) {
            jumpDown = true;
        }
        publish();
        return true;
    }

    Target move(int pointerId, float x, float y) {
        Bind bind = pointers.get(pointerId);
        if (bind == null) {
            return Target.NONE;
        }
        if (bind.target == Target.MOVE || bind.target == Target.LOOK) {
            applyStick(bind, x, y);
            if (bind.target == Target.MOVE) {
                moveX = bind.axisX;
                moveY = bind.axisY;
            } else {
                lookX = bind.axisX;
                lookY = bind.axisY;
            }
            publish();
        }
        return bind.target;
    }

    Target up(int pointerId, String reason) {
        Bind bind = pointers.remove(pointerId);
        if (bind == null) {
            return Target.NONE;
        }
        zeroTarget(bind.target);
        publish();
        return bind.target;
    }

    void releaseAll(String reason) {
        List<Integer> pids = new ArrayList<>(pointers.keySet());
        for (int pid : pids) {
            up(pid, reason);
        }
        moveX = 0.0f;
        moveY = 0.0f;
        lookX = 0.0f;
        lookY = 0.0f;
        jumpDown = false;
        publish();
    }

    void publish() {
        if (sink == null) {
            return;
        }
        sink.setMove(moveX, moveY);
        sink.setLook(lookX, lookY);
        sink.setJump(jumpDown);
    }

    int ownerOf(Target target) {
        for (Map.Entry<Integer, Bind> entry : pointers.entrySet()) {
            if (entry.getValue().target == target) {
                return entry.getKey();
            }
        }
        return INVALID_POINTER;
    }

    boolean hasPointer(int pointerId) {
        return pointers.containsKey(pointerId);
    }

    Target targetOf(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? Target.NONE : bind.target;
    }

    boolean anyPressed() {
        return !pointers.isEmpty();
    }

    float moveX() {
        return moveX;
    }

    float moveY() {
        return moveY;
    }

    float lookX() {
        return lookX;
    }

    float lookY() {
        return lookY;
    }

    boolean jumpDown() {
        return jumpDown;
    }

    float originX(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.originX;
    }

    float originY(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.originY;
    }

    float axisX(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.axisX;
    }

    float axisY(int pointerId) {
        Bind bind = pointers.get(pointerId);
        return bind == null ? 0.0f : bind.axisY;
    }

    int[] pointerIds() {
        int[] ids = new int[pointers.size()];
        int i = 0;
        for (Integer pid : pointers.keySet()) {
            ids[i++] = pid;
        }
        return ids;
    }

    private void applyStick(Bind bind, float x, float y) {
        float r = Math.max(layout.stickRadius, 1.0f);
        float dx = x - bind.originX;
        float dy = y - bind.originY;
        float magnitude = (float) Math.hypot(dx, dy);
        if (magnitude > r) {
            dx = dx * r / magnitude;
            dy = dy * r / magnitude;
        }
        bind.axisX = clamp(dx / r);
        bind.axisY = clamp(dy / r);
    }

    private void zeroTarget(Target target) {
        switch (target) {
            case MOVE:
                moveX = 0.0f;
                moveY = 0.0f;
                break;
            case LOOK:
                lookX = 0.0f;
                lookY = 0.0f;
                break;
            case JUMP:
                jumpDown = false;
                break;
            default:
                break;
        }
    }

    private static float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }
}
