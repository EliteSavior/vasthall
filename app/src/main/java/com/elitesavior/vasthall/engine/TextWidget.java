package com.elitesavior.vasthall.engine;

/**
 * Sample text label. Unreal analog: a {@code UTextBlock} wrapped in a
 * User Widget — the Stage 12 stand-in for a Widget Blueprint.
 */
public class TextWidget extends Widget {
    private String text = "";

    public String text() {
        return text;
    }

    public void setText(String text) {
        String next = text == null ? "" : text;
        if (this.text.equals(next)) {
            return;
        }
        this.text = next;
        WidgetViewport viewport = viewport();
        if (viewport != null) {
            viewport.notifyChanged(this);
        }
    }
}
