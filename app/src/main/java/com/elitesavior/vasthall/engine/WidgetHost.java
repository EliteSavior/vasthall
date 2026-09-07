package com.elitesavior.vasthall.engine;

/**
 * Optional platform surface for widgets already on the viewport.
 * Unreal analog: the game viewport / HUD host.
 *
 * <p>The default foss implementation is {@link SilentWidgetHost} so
 * {@link WidgetViewport} logic can be unit-tested without Android
 * Views. {@code VastHallActivity} attaches a passthrough overlay that
 * does not rewrite {@code PlayHud} stick routing.
 */
public interface WidgetHost {
    void widgetAdded(Widget widget);

    void widgetRemoved(Widget widget);

    void widgetChanged(Widget widget);
}
