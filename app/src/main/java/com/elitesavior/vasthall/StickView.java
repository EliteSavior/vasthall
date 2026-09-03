package com.elitesavior.vasthall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

final class StickView extends View {
    static final int INVALID_POINTER_ID = -1;
    private static final String TAG = "VastHall";

    interface Listener {
        void onStick(float x, float y);
    }

    interface Probe {
        void onTouch(String zone, MotionEvent event, String whoZeroed);
        void onZero(String zone, String reason, int ptr, float ax, float ay);
        void onCapture(String zone, boolean hasCapture);
    }

    static boolean recordEvents;
    static final List<String> TRACE = new ArrayList<>();

    static synchronized void resetTrace() {
        TRACE.clear();
    }

    static synchronized List<String> copyTrace() {
        return new ArrayList<>(TRACE);
    }

    private static synchronized void trace(String line) {
        TRACE.add(line);
    }

    private int activePointerId = INVALID_POINTER_ID;
    private float axisX;
    private float axisY;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean drawStick;
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private Probe probe;
    private float originX;
    private float originY;
    private final String zoneName;

    StickView(Context context, boolean leftZone) {
        super(context);
        zoneName = leftZone ? "left" : "right";
        basePaint.setColor(0x66ffffff);
        basePaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(0xe6d4783a);
        knobPaint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setProbe(Probe probe) {
        this.probe = probe;
    }

    boolean hasActiveFinger() {
        return activePointerId != INVALID_POINTER_ID;
    }

    int pointerId() {
        return activePointerId;
    }

    float axisX() {
        return axisX;
    }

    float axisY() {
        return axisY;
    }

    float originX() {
        return originX;
    }

    float originY() {
        return originY;
    }

    float knobX() {
        return originX + axisX * radius();
    }

    float knobY() {
        return originY + axisY * radius();
    }

    String zoneName() {
        return zoneName;
    }

    void recenter() {
        recenter("API");
    }

    void recenter(String reason) {
        if (hasActiveFinger() || axisX != 0.0f || axisY != 0.0f || drawStick) {
            String line = zoneName + " INPUT_ZERO reason=" + reason
                    + " ptr=" + activePointerId
                    + " axes=" + axisX + "," + axisY;
            Log.i(TAG, line);
            trace(line);
            if (probe != null) {
                probe.onZero(zoneName, reason, activePointerId, axisX, axisY);
            }
        }
        activePointerId = INVALID_POINTER_ID;
        drawStick = false;
        setAxes(0.0f, 0.0f);
    }

    void enforceIdle() {
        if (!hasActiveFinger()) {
            if (axisX != 0.0f || axisY != 0.0f || drawStick) {
                recenter("WATCHDOG_NO_FINGER");
            }
        }
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }

    private float radius() {
        return dp(71.0f);
    }

    private void setAxes(float x, float y) {
        axisX = Math.max(-1.0f, Math.min(1.0f, x));
        axisY = Math.max(-1.0f, Math.min(1.0f, y));
        if (listener != null) {
            listener.onStick(axisX, axisY);
        }
        invalidate();
    }

    private void updateFrom(float x, float y) {
        float r = Math.max(radius(), 1.0f);
        float dx = x - originX;
        float dy = y - originY;
        float magnitude = (float) Math.hypot(dx, dy);
        if (magnitude > r) {
            dx = dx * r / magnitude;
            dy = dy * r / magnitude;
        }
        setAxes(dx / r, dy / r);
    }

    private void disallowIntercept() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void beginTracking(MotionEvent event) {
        int index = event.getActionIndex();
        activePointerId = event.getPointerId(index);
        originX = event.getX(index);
        originY = event.getY(index);
        drawStick = true;
        disallowIntercept();
        requestUnbufferedDispatch(event);
        Log.i(TAG, zoneName + " zone down ptr=" + activePointerId
                + " origin=" + originX + "," + originY
                + " fixedBounds=" + getLeft() + "," + getTop()
                + " " + getWidth() + "x" + getHeight());
        setAxes(0.0f, 0.0f);
    }

    private void reclaim(MotionEvent event, int index, String reason) {
        int pointerId = event.getPointerId(index);
        trace(zoneName + " INPUT_RECLAIM reason=" + reason
                + " ptr=" + pointerId
                + " prev=" + activePointerId
                + " count=" + event.getPointerCount());
        activePointerId = pointerId;
        drawStick = true;
        disallowIntercept();
        updateFrom(event.getX(index), event.getY(index));
    }

    private int trackedIndex(MotionEvent event) {
        if (!hasActiveFinger()) {
            return -1;
        }
        return event.findPointerIndex(activePointerId);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (recordEvents) {
            trace(zoneName + " EVENT action=" + action
                    + " ptr=" + event.getPointerId(event.getActionIndex())
                    + " count=" + event.getPointerCount()
                    + " x=" + event.getX()
                    + " y=" + event.getY()
                    + " t=" + event.getEventTime()
                    + " tracked=" + activePointerId);
        }
        String whoZeroed = null;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (hasActiveFinger()) {
                    whoZeroed = "DOWN_REPLACE";
                    recenter("DOWN_REPLACE");
                }
                beginTracking(event);
                notifyProbe(event, whoZeroed);
                return true;

            case MotionEvent.ACTION_MOVE:
                disallowIntercept();
                if (hasActiveFinger()) {
                    int index = trackedIndex(event);
                    if (index >= 0) {
                        updateFrom(event.getX(index), event.getY(index));
                    } else if (event.getPointerCount() > 0) {
                        reclaim(event, 0, "MISSING_POINTER");
                    } else {
                        whoZeroed = "MISSING_POINTER";
                        recenter("MISSING_POINTER");
                    }
                } else if (event.getPointerCount() > 0) {
                    reclaim(event, 0, "MOVE_WITHOUT_DOWN");
                } else {
                    enforceIdle();
                }
                notifyProbe(event, whoZeroed);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (isTrackedPointerLifting(event)) {
                    whoZeroed = "POINTER_UP";
                    recenter("POINTER_UP");
                }
                notifyProbe(event, whoZeroed);
                return true;

            case MotionEvent.ACTION_UP:
                // A shared/unsplit ACTION_UP must not zero the other zone.
                // Only the pointer this view is tracking may recenter.
                if (isTrackedPointerLifting(event)) {
                    whoZeroed = "UP";
                    recenter("UP");
                }
                notifyProbe(event, whoZeroed);
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (shouldZeroFromCancel(event)) {
                    whoZeroed = "CANCEL";
                    recenter("CANCEL");
                }
                notifyProbe(event, whoZeroed);
                return true;

            default:
                notifyProbe(event, null);
                return true;
        }
    }

    private boolean isTrackedPointerLifting(MotionEvent event) {
        if (!hasActiveFinger() || event.getPointerCount() <= 0) {
            return false;
        }
        return event.getPointerId(event.getActionIndex()) == activePointerId;
    }

    private boolean shouldZeroFromCancel(MotionEvent event) {
        if (!hasActiveFinger()) {
            return false;
        }
        if (event.getPointerCount() == 0) {
            return true;
        }
        return event.findPointerIndex(activePointerId) >= 0;
    }

    private void notifyProbe(MotionEvent event, String whoZeroed) {
        if (probe != null) {
            probe.onTouch(zoneName, event, whoZeroed);
        }
    }

    @Override
    public void onPointerCaptureChange(boolean hasCapture) {
        super.onPointerCaptureChange(hasCapture);
        if (probe != null) {
            probe.onCapture(zoneName, hasCapture);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        recenter("DETACH");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!drawStick) {
            return;
        }
        float r = radius();
        canvas.drawCircle(originX, originY, r, basePaint);
        canvas.drawCircle(originX + axisX * r, originY + axisY * r, 0.38f * r, knobPaint);
    }
}

/**
 * Half-screen stick row. LinearLayout / ViewGroup splitting still delivered
 * the shared MotionEvent (n=2) into LeftZone — that is the 0.15 leak
 * (`POINTER_UP zone=L pid=0 n=2`). This parent never calls
 * super.dispatchTouchEvent for sticks. Each child gets a synthesized
 * one-pointer event only. Not an Activity-wide multiplexer.
 */
final class SplitStickRow extends LinearLayout {
    private int leftPid = StickView.INVALID_POINTER_ID;
    private int rightPid = StickView.INVALID_POINTER_ID;

    SplitStickRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setClickable(false);
        setFocusable(false);
        setMotionEventSplittingEnabled(false);
        setWeightSum(1.0f);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getChildCount() < 2) {
            return super.dispatchTouchEvent(ev);
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
                    deliver(ev, i, MotionEvent.ACTION_MOVE);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                int liftIndex = ev.getActionIndex();
                int liftPid = ev.getPointerId(liftIndex);
                deliver(ev, liftIndex, MotionEvent.ACTION_UP);
                unbind(liftPid);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelRelevant(ev);
                return true;
            default:
                return true;
        }
    }

    private void bindNewPointer(MotionEvent ev, int index) {
        int pid = ev.getPointerId(index);
        if (ownerOf(pid) != null) {
            deliver(ev, index, MotionEvent.ACTION_DOWN);
            return;
        }
        StickView zone = freeZoneAt(ev.getX(index));
        if (zone == null) {
            return;
        }
        if (zone == leftChild()) {
            leftPid = pid;
        } else {
            rightPid = pid;
        }
        deliver(ev, index, MotionEvent.ACTION_DOWN);
    }

    private void cancelRelevant(MotionEvent ev) {
        if (ev.getPointerCount() == 0) {
            cancelZone(leftChild(), leftPid);
            cancelZone(rightChild(), rightPid);
            leftPid = StickView.INVALID_POINTER_ID;
            rightPid = StickView.INVALID_POINTER_ID;
            return;
        }
        for (int i = 0; i < ev.getPointerCount(); i++) {
            int pid = ev.getPointerId(i);
            StickView zone = ownerOf(pid);
            if (zone != null) {
                deliver(ev, i, MotionEvent.ACTION_CANCEL);
                unbind(pid);
            }
        }
    }

    private void cancelZone(StickView zone, int pid) {
        if (zone == null || pid == StickView.INVALID_POINTER_ID) {
            return;
        }
        MotionEvent cancel = MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                MotionEvent.ACTION_CANCEL,
                0.0f,
                0.0f,
                0);
        try {
            zone.dispatchTouchEvent(cancel);
        } finally {
            cancel.recycle();
        }
    }

    private void deliver(MotionEvent src, int pointerIndex, int action) {
        int pid = src.getPointerId(pointerIndex);
        StickView zone = ownerOf(pid);
        if (zone == null) {
            return;
        }
        MotionEvent one = singlePointer(src, pointerIndex, action, zone);
        try {
            zone.dispatchTouchEvent(one);
        } finally {
            one.recycle();
        }
    }

    private MotionEvent singlePointer(
            MotionEvent src, int pointerIndex, int action, StickView zone) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        properties[0] = new MotionEvent.PointerProperties();
        src.getPointerProperties(pointerIndex, properties[0]);
        coords[0] = new MotionEvent.PointerCoords();
        src.getPointerCoords(pointerIndex, coords[0]);
        coords[0].x = src.getX(pointerIndex) - zone.getLeft() + getScrollX();
        coords[0].y = src.getY(pointerIndex) - zone.getTop() + getScrollY();
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

    private StickView ownerOf(int pid) {
        if (pid == StickView.INVALID_POINTER_ID) {
            return null;
        }
        if (leftPid == pid) {
            return leftChild();
        }
        if (rightPid == pid) {
            return rightChild();
        }
        StickView left = leftChild();
        if (left != null && left.pointerId() == pid) {
            return left;
        }
        StickView right = rightChild();
        if (right != null && right.pointerId() == pid) {
            return right;
        }
        return null;
    }

    private void unbind(int pid) {
        if (leftPid == pid) {
            leftPid = StickView.INVALID_POINTER_ID;
        }
        if (rightPid == pid) {
            rightPid = StickView.INVALID_POINTER_ID;
        }
    }

    private StickView freeZoneAt(float x) {
        StickView hit = hit(x);
        if (hit == null) {
            return null;
        }
        if (boundPid(hit) != StickView.INVALID_POINTER_ID || hit.hasActiveFinger()) {
            return null;
        }
        return hit;
    }

    private int boundPid(StickView zone) {
        if (zone == leftChild()) {
            return leftPid;
        }
        if (zone == rightChild()) {
            return rightPid;
        }
        return StickView.INVALID_POINTER_ID;
    }

    private StickView hit(float x) {
        StickView left = leftChild();
        StickView right = rightChild();
        if (left != null && x < left.getRight()) {
            return left;
        }
        return right;
    }

    private StickView leftChild() {
        if (getChildCount() < 1) {
            return null;
        }
        View child = getChildAt(0);
        return child instanceof StickView ? (StickView) child : null;
    }

    private StickView rightChild() {
        if (getChildCount() < 2) {
            return null;
        }
        View child = getChildAt(1);
        return child instanceof StickView ? (StickView) child : null;
    }
}
