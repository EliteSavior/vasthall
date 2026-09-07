package com.elitesavior.vasthall.engine;

/**
 * No-op host. Tests and headless worlds keep the widget catalog without
 * creating Android Views.
 */
public final class SilentWidgetHost implements WidgetHost {
    @Override
    public void widgetAdded(Widget widget) {
    }

    @Override
    public void widgetRemoved(Widget widget) {
    }

    @Override
    public void widgetChanged(Widget widget) {
    }
}
