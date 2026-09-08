package com.elitesavior.vasthall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/**
 * Single full-screen touch consumer for New pad. Sticks and jump are drawn
 * here; hits are {@link FlatPadRouter} math rects, not nested View children.
 * HUD children do not steal UP. MOVE ownership is pointerId +
 * {@link MotionEvent#findPointerIndex(int)} only — never
 * {@link MotionEvent#getActionIndex()} (always 0 on MOVE).
 */
final class FlatPadOverlay extends View {
    private final FlatPadRouter router;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jumpFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jumpText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private StickView.Probe probe;
    private String jumpLabel = "Jump";
    private float jumpSize;
    private float jumpRightMargin;
    private float jumpBottomMargin;

    FlatPadOverlay(Context context, FlatPadRouter router) {
        super(context);
        this.router = router;
        basePaint.setColor(0x66ffffff);
        basePaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(0xe6d4783a);
        knobPaint.setStyle(Paint.Style.FILL);
        jumpFill.setColor(0xe6c45c28);
        jumpFill.setStyle(Paint.Style.FILL);
        jumpText.setColor(0xffffffff);
        jumpText.setTextAlign(Paint.Align.CENTER);
        jumpText.setTypeface(Typeface.DEFAULT_BOLD);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setVisibility(GONE);
    }

    FlatPadRouter router() {
        return router;
    }

    void setProbe(StickView.Probe probe) {
        this.probe = probe;
    }

    void setJumpChrome(float sizePx, float rightMarginPx, float bottomMarginPx, String label) {
        jumpSize = sizePx;
        jumpRightMargin = rightMarginPx;
        jumpBottomMargin = bottomMarginPx;
        if (label != null) {
            jumpLabel = label;
        }
        syncLayout();
    }

    void setPlayVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
        setClickable(visible);
        if (visible) {
            syncLayout();
        }
    }

    void syncLayout() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        FlatPadRouter.Layout layout = new FlatPadRouter.Layout();
        layout.width = width;
        layout.height = height;
        layout.stickRadius = dp(71.0f);
        layout.jumpRight = width - jumpRightMargin;
        layout.jumpBottom = height - jumpBottomMargin;
        layout.jumpLeft = layout.jumpRight - jumpSize;
        layout.jumpTop = layout.jumpBottom - jumpSize;
        router.setLayout(layout);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        syncLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getVisibility() != VISIBLE) {
            return false;
        }
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        int masked = event.getActionMasked();
        String whoZeroed = null;
        switch (masked) {
            case MotionEvent.ACTION_DOWN:
                requestUnbufferedDispatch(event);
                bindPointer(event, event.getActionIndex());
                notifyProbe(event, null);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                bindPointer(event, event.getActionIndex());
                notifyProbe(event, null);
                return true;
            case MotionEvent.ACTION_MOVE:
                whoZeroed = applyMove(event);
                invalidate();
                notifyProbe(event, whoZeroed);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                int lift = event.getActionIndex();
                int liftPid = event.getPointerId(lift);
                if (router.hasPointer(liftPid)) {
                    router.up(liftPid, "UP");
                    whoZeroed = "UP";
                }
                invalidate();
                notifyProbe(event, whoZeroed);
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                router.releaseAll("CANCEL");
                invalidate();
                notifyProbe(event, "CANCEL");
                return true;
            default:
                notifyProbe(event, null);
                return true;
        }
    }

    /**
     * Drain historical MOVE samples then the current sample. Ownership is
     * always pointerId + findPointerIndex — getActionIndex is not used.
     */
    private String applyMove(MotionEvent event) {
        String who = null;
        int[] live = new int[event.getPointerCount()];
        for (int i = 0; i < event.getPointerCount(); i++) {
            live[i] = event.getPointerId(i);
        }
        if (router.anyPressed()) {
            String before = router.lastWhoZeroed();
            router.noteLivePointers(live);
            if ("ORPHAN".equals(router.lastWhoZeroed()) && !before.equals("ORPHAN")) {
                who = "ORPHAN";
            }
        }
        int history = event.getHistorySize();
        for (int h = 0; h < history; h++) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pid = event.getPointerId(i);
                int index = event.findPointerIndex(pid);
                if (index < 0 || !router.hasPointer(pid)) {
                    continue;
                }
                router.move(pid, event.getHistoricalX(index, h), event.getHistoricalY(index, h));
            }
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pid = event.getPointerId(i);
            int index = event.findPointerIndex(pid);
            if (index < 0 || !router.hasPointer(pid)) {
                continue;
            }
            router.move(pid, event.getX(index), event.getY(index));
        }
        return who;
    }

    private void bindPointer(MotionEvent event, int index) {
        if (index < 0 || index >= event.getPointerCount()) {
            return;
        }
        router.down(event.getPointerId(index), event.getX(index), event.getY(index));
        invalidate();
    }

    private void notifyProbe(MotionEvent event, String whoZeroed) {
        if (probe == null) {
            return;
        }
        probe.onTouch(
                "pad",
                event,
                whoZeroed);
    }

    @Override
    protected void onDetachedFromWindow() {
        router.releaseAll("DETACH");
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != VISIBLE) {
            return;
        }
        FlatPadRouter.Layout layout = router.layout();
        float jumpCx = (layout.jumpLeft + layout.jumpRight) * 0.5f;
        float jumpCy = (layout.jumpTop + layout.jumpBottom) * 0.5f;
        float jumpR = Math.max(1.0f, (layout.jumpRight - layout.jumpLeft) * 0.5f);
        canvas.drawCircle(jumpCx, jumpCy, jumpR, jumpFill);
        jumpText.setTextSize(dp(14.0f));
        canvas.drawText(jumpLabel, jumpCx, jumpCy - (jumpText.ascent() + jumpText.descent()) * 0.5f, jumpText);

        drawStick(canvas, FlatPadRouter.Target.MOVE);
        drawStick(canvas, FlatPadRouter.Target.LOOK);
    }

    private void drawStick(Canvas canvas, FlatPadRouter.Target target) {
        int pid = router.ownerOf(target);
        if (pid == FlatPadRouter.INVALID_POINTER) {
            return;
        }
        float originX = router.originX(pid);
        float originY = router.originY(pid);
        float r = Math.max(router.layout().stickRadius, 1.0f);
        canvas.drawCircle(originX, originY, r, basePaint);
        canvas.drawCircle(
                originX + router.axisX(pid) * r,
                originY + router.axisY(pid) * r,
                0.38f * r,
                knobPaint);
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }
}
