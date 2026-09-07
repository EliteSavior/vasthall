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
                    } else {
                        whoZeroed = "MISSING_POINTER";
                        recenter("MISSING_POINTER");
                    }
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
 * Weighted left/right stick row. Pointer routing lives in {@link PlayHud};
 * this layout does not own pressed state and does not split MotionEvents.
 */
final class SplitStickRow extends LinearLayout {
    SplitStickRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setClickable(false);
        setFocusable(false);
        setMotionEventSplittingEnabled(false);
        setWeightSum(1.0f);
    }
}
