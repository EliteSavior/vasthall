package com.elitesavior.vasthall;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Play-time HUD router. Every pointer is bound at DOWN by hit-test, updated
 * only while bound, and released on UP/CANCEL. Children receive synthesized
 * one-pointer events so a shared n=2 stream cannot leak into a stick zone.
 */
final class PlayHud extends FrameLayout {
    private final PlayInputMachine machine;
    private final StickView left;
    private final StickView right;
    private final View jump;
    private final SplitStickRow row;

    PlayHud(
            Context context,
            PlayInputMachine machine,
            StickView left,
            StickView right,
            View jump) {
        super(context);
        this.machine = machine;
        this.left = left;
        this.right = right;
        this.jump = jump;
        setClickable(false);
        setFocusable(false);
        setMotionEventSplittingEnabled(false);

        row = new SplitStickRow(context);
        row.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
        row.addView(right, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
        addView(row, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (jump != null) {
            LayoutParams jumpLp = jump.getLayoutParams();
            if (jumpLp instanceof FrameLayout.LayoutParams) {
                addView(jump, jumpLp);
            } else {
                addView(jump);
            }
        }
    }

    PlayInputMachine machine() {
        return machine;
    }

    StickView leftZone() {
        return left;
    }

    StickView rightZone() {
        return right;
    }

    View jump() {
        return jump;
    }

    void setPlayVisible(boolean visible) {
        int vis = visible ? VISIBLE : GONE;
        setVisibility(vis);
        row.setVisibility(vis);
        if (jump != null) {
            jump.setVisibility(vis);
        }
    }

    void cancelAll(String reason) {
        int[] pids = machine.pointerIds();
        for (int pid : pids) {
            PlayInputMachine.Target target = machine.targetOf(pid);
            View child = viewFor(target);
            if (child != null) {
                deliverCancel(child, pid);
            }
        }
        machine.releaseAll(reason);
        left.recenter(reason);
        right.recenter(reason);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getVisibility() != VISIBLE || left == null || right == null) {
            return false;
        }
        requestDisallowInterceptTouchEvent(true);
        int masked = ev.getActionMasked();
        switch (masked) {
            case MotionEvent.ACTION_DOWN:
                requestUnbufferedDispatch(ev);
                bindNewPointer(ev, ev.getActionIndex());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                bindNewPointer(ev, ev.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int pid = ev.getPointerId(i);
                    PlayInputMachine.Target target = machine.pointerMove(pid);
                    if (target != PlayInputMachine.Target.NONE) {
                        deliver(ev, i, MotionEvent.ACTION_MOVE, target);
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                int liftIndex = ev.getActionIndex();
                int liftPid = ev.getPointerId(liftIndex);
                PlayInputMachine.Target liftTarget = machine.targetOf(liftPid);
                if (liftTarget != PlayInputMachine.Target.NONE) {
                    deliver(ev, liftIndex, MotionEvent.ACTION_UP, liftTarget);
                    machine.pointerUp(liftPid, "UP");
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelEvent(ev);
                return true;
            default:
                return true;
        }
    }

    private void bindNewPointer(MotionEvent ev, int index) {
        int pid = ev.getPointerId(index);
        if (machine.hasPointer(pid)) {
            PlayInputMachine.Target existing = machine.targetOf(pid);
            deliver(ev, index, MotionEvent.ACTION_DOWN, existing);
            return;
        }
        PlayInputMachine.Target hit = hit(ev.getX(index), ev.getY(index));
        if (!machine.pointerDown(pid, hit)) {
            return;
        }
        deliver(ev, index, MotionEvent.ACTION_DOWN, hit);
    }

    private void cancelEvent(MotionEvent ev) {
        if (ev.getPointerCount() <= 0) {
            cancelAll("CANCEL");
            return;
        }
        for (int i = 0; i < ev.getPointerCount(); i++) {
            int pid = ev.getPointerId(i);
            PlayInputMachine.Target target = machine.targetOf(pid);
            if (target == PlayInputMachine.Target.NONE) {
                continue;
            }
            deliver(ev, i, MotionEvent.ACTION_CANCEL, target);
            machine.pointerUp(pid, "CANCEL");
        }
    }

    PlayInputMachine.Target hit(float x, float y) {
        if (jump != null
                && jump.getVisibility() == VISIBLE
                && pointInView(jump, x, y)) {
            return PlayInputMachine.Target.JUMP;
        }
        if (x < rowCenterX()) {
            return PlayInputMachine.Target.MOVE;
        }
        return PlayInputMachine.Target.LOOK;
    }

    private float rowCenterX() {
        int width = getWidth();
        if (width <= 0) {
            width = row.getWidth();
        }
        return width * 0.5f;
    }

    private boolean pointInView(View view, float x, float y) {
        float[] origin = originInHud(view);
        return x >= origin[0]
                && y >= origin[1]
                && x < origin[0] + view.getWidth()
                && y < origin[1] + view.getHeight();
    }

    private float[] originInHud(View view) {
        float x = 0.0f;
        float y = 0.0f;
        View current = view;
        while (current != null && current != this) {
            x += current.getLeft();
            y += current.getTop();
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        return new float[]{x, y};
    }

    private View viewFor(PlayInputMachine.Target target) {
        switch (target) {
            case MOVE:
                return left;
            case LOOK:
                return right;
            case JUMP:
                return jump;
            default:
                return null;
        }
    }

    private void deliver(MotionEvent src, int pointerIndex, int action,
            PlayInputMachine.Target target) {
        View child = viewFor(target);
        if (child == null) {
            return;
        }
        MotionEvent one = singlePointer(src, pointerIndex, action, child);
        try {
            child.dispatchTouchEvent(one);
        } finally {
            one.recycle();
        }
    }

    private void deliverCancel(View child, int pointerId) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = pointerId;
        coords[0] = new MotionEvent.PointerCoords();
        MotionEvent cancel = MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                MotionEvent.ACTION_CANCEL,
                1,
                properties,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                0,
                0);
        try {
            child.dispatchTouchEvent(cancel);
        } finally {
            cancel.recycle();
        }
    }

    private MotionEvent singlePointer(
            MotionEvent src, int pointerIndex, int action, View zone) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        properties[0] = new MotionEvent.PointerProperties();
        src.getPointerProperties(pointerIndex, properties[0]);
        coords[0] = new MotionEvent.PointerCoords();
        src.getPointerCoords(pointerIndex, coords[0]);
        float[] origin = originInHud(zone);
        coords[0].x = src.getX(pointerIndex) - origin[0] + getScrollX();
        coords[0].y = src.getY(pointerIndex) - origin[1] + getScrollY();
        return MotionEvent.obtain(
                src.getDownTime(),
                src.getEventTime(),
                action,
                1,
                properties,
                coords,
                src.getMetaState(),
                src.getButtonState(),
                src.getXPrecision(),
                src.getYPrecision(),
                src.getDeviceId(),
                src.getEdgeFlags(),
                src.getSource(),
                src.getFlags());
    }
}
