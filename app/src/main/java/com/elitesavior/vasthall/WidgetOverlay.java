package com.elitesavior.vasthall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.elitesavior.vasthall.engine.TextWidget;
import com.elitesavior.vasthall.engine.Widget;
import com.elitesavior.vasthall.engine.WidgetHost;
import com.elitesavior.vasthall.engine.WidgetViewport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Android View host for {@link WidgetViewport}. Sibling of {@link PlayHud},
 * wrap-content, always returns false from touch dispatch so sticks keep
 * their existing router. Unreal analog: add-to-viewport onto the game
 * HUD without a UMG designer.
 */
final class WidgetOverlay extends LinearLayout implements WidgetHost {
    private final Map<Widget, View> views = new LinkedHashMap<>();
    private WidgetViewport viewport;

    WidgetOverlay(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setMotionEventSplittingEnabled(false);
    }

    void bind(WidgetViewport viewport) {
        if (this.viewport != null && this.viewport.host() == this) {
            this.viewport.setHost(null);
        }
        this.viewport = viewport;
        removeAllViews();
        views.clear();
        if (viewport == null) {
            return;
        }
        viewport.setHost(this);
        for (Widget widget : viewport.viewportWidgets()) {
            widgetAdded(widget);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public void widgetAdded(Widget widget) {
        if (widget == null || views.containsKey(widget)) {
            return;
        }
        View view = createView(widget);
        views.put(widget, view);
        addView(view);
        applyVisibility(widget, view);
    }

    @Override
    public void widgetRemoved(Widget widget) {
        View view = views.remove(widget);
        if (view != null) {
            removeView(view);
        }
    }

    @Override
    public void widgetChanged(Widget widget) {
        View view = views.get(widget);
        if (view == null) {
            if (widget != null && widget.isInViewport()) {
                widgetAdded(widget);
            }
            return;
        }
        if (view instanceof TextView && widget instanceof TextWidget) {
            ((TextView) view).setText(((TextWidget) widget).text());
        }
        applyVisibility(widget, view);
    }

    private View createView(Widget widget) {
        TextView label = new TextView(getContext());
        label.setText(widget instanceof TextWidget ? ((TextWidget) widget).text() : widget.name());
        label.setTextColor(0xeed4783a);
        label.setTextSize(18.0f);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setClickable(false);
        label.setFocusable(false);
        label.setFocusableInTouchMode(false);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad = (int) (8.0f * getResources().getDisplayMetrics().density);
        label.setPadding(pad, pad / 2, pad, pad / 2);
        return label;
    }

    private static void applyVisibility(Widget widget, View view) {
        view.setVisibility(widget.isVisible() ? VISIBLE : GONE);
    }
}
