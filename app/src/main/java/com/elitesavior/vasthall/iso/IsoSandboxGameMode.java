package com.elitesavior.vasthall.iso;

import com.elitesavior.vasthall.engine.Actor;
import com.elitesavior.vasthall.engine.GameMode;
import com.elitesavior.vasthall.engine.TextWidget;
import com.elitesavior.vasthall.engine.WidgetViewport;

/**
 * Iso I session: orthographic isometric grid, pan/zoom, no pawn.
 * Hall mode is a different GameMode and stays the default play start.
 */
public final class IsoSandboxGameMode extends GameMode {
    public static final String SAMPLE_WIDGET_NAME = "IsoTitle";
    public static final String SAMPLE_WIDGET_TEXT = "ISO";

    private IsoCamera camera;
    private IsoGrid grid;
    private IsoGestures gestures;
    private TextWidget sampleWidget;

    @Override
    public Class<? extends Actor> defaultPawnClass() {
        return null;
    }

    @Override
    public void initGame(String options) {
        super.initGame(options);
        camera = new IsoCamera();
        grid = new IsoGrid();
        camera.lookAt(grid.size() * 0.5f, grid.size() * 0.5f);
        camera.setPanBounds(-8.0f, grid.size() + 8.0f, -8.0f, grid.size() + 8.0f);
        gestures = new IsoGestures(camera);
    }

    @Override
    public void startPlay() {
        super.startPlay();
        addSampleWidget();
    }

    @Override
    public void endPlay() {
        destroySampleWidget();
        super.endPlay();
    }

    public IsoCamera camera() {
        return camera;
    }

    public IsoGrid grid() {
        return grid;
    }

    public IsoGestures gestures() {
        return gestures;
    }

    public TextWidget sampleWidget() {
        return sampleWidget;
    }

    private void addSampleWidget() {
        WidgetViewport host = viewport();
        if (host == null) {
            return;
        }
        if (host.find(SAMPLE_WIDGET_NAME) != null) {
            host.destroyWidget(host.find(SAMPLE_WIDGET_NAME));
        }
        TextWidget label = host.createWidget(TextWidget.class, SAMPLE_WIDGET_NAME);
        label.setText(SAMPLE_WIDGET_TEXT);
        label.addToViewport();
        sampleWidget = label;
    }

    private void destroySampleWidget() {
        WidgetViewport host = viewport();
        if (host != null && sampleWidget != null) {
            host.destroyWidget(sampleWidget);
        }
        sampleWidget = null;
    }
}
