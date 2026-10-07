package com.elitesavior.vasthall.iso;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * Opaque isometric grid overlay. Hall's native SurfaceView stays underneath
 * and is covered while Iso Sandbox is active.
 */
public final class IsoGridView extends View {
    private static final int GROUND = 0xff14161c;
    private static final int LINE = 0xff6aa88a;
    private static final int ORIGIN = 0xffffd54f;
    private static final int AXIS_X = 0xffef9a9a;
    private static final int AXIS_Z = 0xff90caf9;

    private IsoCamera camera;
    private IsoGrid grid;
    private IsoGestures gestures;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint originPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisXPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisZPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path originMark = new Path();

    public IsoGridView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
        setBackgroundColor(GROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.0f);
        linePaint.setColor(LINE);
        originPaint.setStyle(Paint.Style.FILL);
        originPaint.setColor(ORIGIN);
        axisXPaint.setStyle(Paint.Style.STROKE);
        axisXPaint.setStrokeWidth(3.0f);
        axisXPaint.setColor(AXIS_X);
        axisZPaint.setStyle(Paint.Style.STROKE);
        axisZPaint.setStrokeWidth(3.0f);
        axisZPaint.setColor(AXIS_Z);
    }

    public void bind(IsoCamera camera, IsoGrid grid, IsoGestures gestures) {
        this.camera = camera;
        this.grid = grid;
        this.gestures = gestures;
        syncViewport();
        invalidate();
    }

    public IsoCamera camera() {
        return camera;
    }

    public IsoGrid grid() {
        return grid;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        syncViewport();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (camera == null || grid == null) {
            return;
        }
        syncViewport();
        grid.forEachLine((x0, y0, z0, x1, y1, z1) -> canvas.drawLine(
                camera.screenX(x0, y0, z0),
                camera.screenY(x0, y0, z0),
                camera.screenX(x1, y1, z1),
                camera.screenY(x1, y1, z1),
                linePaint));
        drawOrigin(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestures == null || getVisibility() != VISIBLE) {
            return false;
        }
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                gestures.pointerDown(id, event.getX(index), event.getY(index));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    gestures.pointerMove(event.getPointerId(i), event.getX(i), event.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                gestures.pointerUp(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                gestures.cancel();
                break;
            default:
                return true;
        }
        invalidate();
        return true;
    }

    private void drawOrigin(Canvas canvas) {
        float ox = camera.screenX(0.0f, 0.0f, 0.0f);
        float oy = camera.screenY(0.0f, 0.0f, 0.0f);
        float x1 = camera.screenX(2.0f, 0.0f, 0.0f);
        float y1 = camera.screenY(2.0f, 0.0f, 0.0f);
        float z1x = camera.screenX(0.0f, 0.0f, 2.0f);
        float z1y = camera.screenY(0.0f, 0.0f, 2.0f);
        canvas.drawLine(ox, oy, x1, y1, axisXPaint);
        canvas.drawLine(ox, oy, z1x, z1y, axisZPaint);
        originMark.reset();
        originMark.moveTo(ox, oy - 8.0f);
        originMark.lineTo(ox + 8.0f, oy);
        originMark.lineTo(ox, oy + 8.0f);
        originMark.lineTo(ox - 8.0f, oy);
        originMark.close();
        canvas.drawPath(originMark, originPaint);
    }

    private void syncViewport() {
        if (camera == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            camera.setViewport(w, h);
        }
    }
}
