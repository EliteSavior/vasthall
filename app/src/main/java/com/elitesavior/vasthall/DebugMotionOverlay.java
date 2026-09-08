package com.elitesavior.vasthall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Live debug HUD: stick vs pawn motion, JNI publish/consume, divergence
 * flags, and a short sparkline. Does not consume touches.
 */
final class DebugMotionOverlay extends View {
    private static final long MIN_DRAW_NS = 50_000_000L;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cmdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private DebugHub.HudSnapshot snapshot = DebugHub.HudSnapshot.empty();
    private long lastDrawNs;

    DebugMotionOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        textPaint.setColor(0xe6ffffff);
        textPaint.setTextSize(dp(11));
        textPaint.setTypeface(Typeface.MONOSPACE);
        panelPaint.setColor(0xc0111216);
        cmdPaint.setColor(0xff5ec8ff);
        cmdPaint.setStrokeWidth(dp(3));
        cmdPaint.setStyle(Paint.Style.STROKE);
        actPaint.setColor(0xfff0a030);
        actPaint.setStrokeWidth(dp(3));
        actPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStyle(Paint.Style.STROKE);
        sparkPaint.setStrokeWidth(dp(1.5f));
        flagPaint.setColor(0xffff6b4a);
        flagPaint.setTextSize(dp(11));
        flagPaint.setTypeface(Typeface.MONOSPACE);
        flagPaint.setStyle(Paint.Style.FILL);
        setVisibility(GONE);
    }

    void setSnapshot(DebugHub.HudSnapshot snapshot) {
        this.snapshot = snapshot == null ? DebugHub.HudSnapshot.empty() : snapshot;
        long now = System.nanoTime();
        if (now - lastDrawNs >= MIN_DRAW_NS) {
            lastDrawNs = now;
            postInvalidateOnAnimation();
        }
    }

    DebugHub.HudSnapshot snapshot() {
        return snapshot;
    }

    void setDebugVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        DebugHub.HudSnapshot snap = snapshot;
        if (snap == null || snap.text == null || snap.text.isEmpty()) {
            return;
        }
        float pad = dp(8);
        float left = pad;
        float top = pad + dp(36);
        float width = Math.min(getWidth() * 0.52f, dp(420));
        float line = textPaint.getFontSpacing();
        String[] lines = snap.text.split("\n");
        float textH = line * lines.length + pad;
        float arrowBox = dp(72);
        float sparkH = dp(28);
        float height = textH + arrowBox + sparkH + pad;
        canvas.drawRoundRect(left, top, left + width, top + height, dp(6), dp(6), panelPaint);

        float y = top + pad + textPaint.getTextSize();
        for (String row : lines) {
            Paint paint = row.startsWith("FLAGS") && snap.flags != 0 ? flagPaint : textPaint;
            canvas.drawText(row, left + pad, y, paint);
            y += line;
        }

        float originX = left + pad + arrowBox * 0.5f;
        float originY = y + arrowBox * 0.45f;
        drawArrow(canvas, originX, originY, snap.cmdMoveX, -snap.cmdMoveY, arrowBox * 0.42f, cmdPaint);
        drawArrow(canvas, originX, originY, snap.actMoveX, -snap.actMoveY, arrowBox * 0.42f, actPaint);
        canvas.drawText("cmd", originX + arrowBox * 0.4f, originY - dp(8), flagPaint);
        canvas.drawText("act", originX + arrowBox * 0.4f, originY + dp(10), flagPaint);

        float lookOriginX = left + width - arrowBox;
        drawArrow(canvas, lookOriginX, originY, snap.cmdLookX, -snap.cmdLookY, arrowBox * 0.35f, cmdPaint);
        drawArrow(canvas, lookOriginX, originY, snap.actLookX * 8.0f, -snap.actLookY * 8.0f,
                arrowBox * 0.35f, actPaint);
        canvas.drawText("look", lookOriginX - dp(8), originY + arrowBox * 0.48f, textPaint);

        float sparkTop = originY + arrowBox * 0.55f;
        drawSpark(canvas, left + pad, sparkTop, width - pad * 2.0f, sparkH * 0.28f,
                snap.sparkInput, 0xff5ec8ff);
        drawSpark(canvas, left + pad, sparkTop + sparkH * 0.32f, width - pad * 2.0f, sparkH * 0.28f,
                snap.sparkVel, 0xfff0a030);
        drawSpark(canvas, left + pad, sparkTop + sparkH * 0.64f, width - pad * 2.0f, sparkH * 0.28f,
                snap.sparkLook, 0xffc070ff);
    }

    private void drawArrow(Canvas canvas, float ox, float oy, float vx, float vy, float scale, Paint paint) {
        float mag = (float) Math.hypot(vx, vy);
        float tx;
        float ty;
        if (mag < 0.02f) {
            canvas.drawCircle(ox, oy, dp(3), paint);
            return;
        }
        tx = ox + vx / mag * scale * Math.min(1.0f, mag);
        ty = oy + vy / mag * scale * Math.min(1.0f, mag);
        canvas.drawLine(ox, oy, tx, ty, paint);
        float ang = (float) Math.atan2(ty - oy, tx - ox);
        float ah = dp(7);
        arrow.reset();
        arrow.moveTo(tx, ty);
        arrow.lineTo(
                (float) (tx - ah * Math.cos(ang - 0.4)),
                (float) (ty - ah * Math.sin(ang - 0.4)));
        arrow.lineTo(
                (float) (tx - ah * Math.cos(ang + 0.4)),
                (float) (ty - ah * Math.sin(ang + 0.4)));
        arrow.close();
        Paint fill = new Paint(paint);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrow, fill);
    }

    private void drawSpark(Canvas canvas, float x, float y, float w, float h, float[] values, int color) {
        sparkPaint.setColor(color);
        canvas.drawRect(x, y, x + w, y + h, sparkPaint);
        if (values == null || values.length < 2) {
            return;
        }
        float max = 1.0f;
        for (float v : values) {
            if (v > max) {
                max = v;
            }
        }
        float dx = w / (values.length - 1);
        Path path = new Path();
        for (int i = 0; i < values.length; i++) {
            float px = x + dx * i;
            float py = y + h - (values[i] / max) * h;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        canvas.drawPath(path, sparkPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
