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
 */
final class FlatPadOverlay extends View {
    private final FlatPadRouter router;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jumpFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jumpText = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        switch (masked) {
            case MotionEvent.ACTION_DOWN:
                requestUnbufferedDispatch(event);
                bindPointer(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                bindPointer(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    if (router.hasPointer(pid)) {
                        router.move(pid, event.getX(i), event.getY(i));
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                int lift = event.getActionIndex();
                router.up(event.getPointerId(lift), "UP");
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                router.releaseAll("CANCEL");
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void bindPointer(MotionEvent event, int index) {
        router.down(event.getPointerId(index), event.getX(index), event.getY(index));
        invalidate();
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
