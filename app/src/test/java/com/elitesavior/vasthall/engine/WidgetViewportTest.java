package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class WidgetViewportTest {
    private GameInstance game;
    private WidgetViewport viewport;

    @Before
    public void setUp() {
        game = GameInstance.withDemoAssets();
        game.init();
        viewport = game.viewport();
    }

    @Test
    public void createWidgetIsNotOnViewportUntilAdded() {
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        label.setText("Hello");

        assertEquals("Hint", label.name());
        assertSame(viewport, label.viewport());
        assertFalse(label.isInViewport());
        assertFalse(label.isVisible());
        assertEquals(WidgetVisibility.VISIBLE, label.visibility());
        assertEquals(0, viewport.viewportCount());
        assertEquals(0, viewport.visibleCount());
        assertSame(label, viewport.find("Hint"));
        assertEquals("Hello", label.text());
    }

    @Test
    public void addToViewportShowsAndHideKeepsSlot() {
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        label.setText("Hello");

        assertTrue(label.addToViewport());
        assertTrue(label.isInViewport());
        assertTrue(label.isVisible());
        assertEquals(1, viewport.viewportCount());
        assertEquals(1, viewport.visibleCount());

        label.hide();
        assertTrue(label.isInViewport());
        assertFalse(label.isVisible());
        assertEquals(WidgetVisibility.HIDDEN, label.visibility());
        assertEquals(1, viewport.viewportCount());
        assertEquals(0, viewport.visibleCount());

        label.show();
        assertTrue(label.isVisible());
        assertEquals(WidgetVisibility.VISIBLE, label.visibility());
        assertEquals(1, viewport.visibleCount());
    }

    @Test
    public void removeFromParentDropsTheViewportSlot() {
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        assertTrue(label.addToViewport());
        assertTrue(label.removeFromParent());

        assertFalse(label.isInViewport());
        assertFalse(label.isVisible());
        assertEquals(0, viewport.viewportCount());
        assertFalse(label.removeFromParent());
        assertSame(label, viewport.find("Hint"));
    }

    @Test
    public void destroyWidgetForgetsTheName() {
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        label.addToViewport();
        assertTrue(viewport.destroyWidget(label));
        assertNull(viewport.find("Hint"));
        assertNull(label.viewport());
        assertFalse(label.isInViewport());
        assertFalse(viewport.destroyWidget(label));
    }

    @Test
    public void duplicateNameIsRejected() {
        viewport.createWidget(TextWidget.class, "Hint");
        try {
            viewport.createWidget(TextWidget.class, "Hint");
            fail("expected duplicate widget name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Hint"));
        }
    }

    @Test
    public void gameplayStaticsReachViewportThroughWorld() {
        assertSame(viewport, GameplayStatics.getWidgetViewport(game.world()));
        TextWidget label = GameplayStatics.createWidget(
                game.world(), TextWidget.class, "Hint");
        label.setText("Hello");
        assertTrue(GameplayStatics.addToViewport(game.world(), label));
        assertSame(label, GameplayStatics.findWidget(game.world(), "Hint"));
        assertTrue(GameplayStatics.hideWidget(game.world(), "Hint"));
        assertFalse(label.isVisible());
        assertTrue(GameplayStatics.showWidget(game.world(), "Hint"));
        assertTrue(label.isVisible());
        assertTrue(GameplayStatics.removeFromParent(game.world(), "Hint"));
        assertFalse(label.isInViewport());
    }

    @Test
    public void hallGameModeAddsSampleTextWidget() {
        game.openLevel("Hall");
        HallGameMode mode = (HallGameMode) game.gameMode();
        TextWidget sample = mode.sampleWidget();
        assertNotNull(sample);
        assertEquals(HallGameMode.SAMPLE_WIDGET_NAME, sample.name());
        assertEquals(HallGameMode.SAMPLE_WIDGET_TEXT, sample.text());
        assertTrue(sample.isInViewport());
        assertTrue(sample.isVisible());
        assertEquals(1, game.viewport().visibleCount());

        game.openLevel("Hall");
        HallGameMode next = (HallGameMode) game.gameMode();
        assertNotSame(sample, next.sampleWidget());
        assertNull(sample.viewport());
        assertEquals(1, game.viewport().visibleCount());
        assertEquals(HallGameMode.SAMPLE_WIDGET_NAME, next.sampleWidget().name());
    }

    @Test
    public void consoleCreateShowHideAndRemove() {
        DeveloperConsole console = game.console();

        String created = console.exec("CreateWidget Text Hint Hello Hall");
        assertTrue(created.contains("created Hint"));
        TextWidget hint = (TextWidget) viewport.find("Hint");
        assertNotNull(hint);
        assertEquals("Hello Hall", hint.text());
        assertFalse(hint.isInViewport());

        String added = console.exec("AddToViewport Hint");
        assertTrue(added.contains("viewport Hint"));
        assertTrue(hint.isVisible());

        String hidden = console.exec("HideWidget Hint");
        assertTrue(hidden.contains("hidden Hint"));
        assertTrue(hint.isInViewport());
        assertFalse(hint.isVisible());

        String shown = console.exec("ShowWidget Hint");
        assertTrue(shown.contains("shown Hint"));
        assertTrue(hint.isVisible());

        String listed = console.exec("widgets");
        assertTrue(listed.contains("widgets=1"));
        assertTrue(listed.contains("Hint"));
        assertTrue(listed.contains("Hello Hall"));

        String removed = console.exec("RemoveFromParent Hint");
        assertTrue(removed.contains("removed Hint"));
        assertFalse(hint.isInViewport());

        String missing = console.exec("hidewidget Missing");
        assertTrue(missing.startsWith("error:"));
        assertTrue(missing.toLowerCase().contains("widget"));
        assertTrue(console.exec("createwidget").startsWith("error:"));
        assertTrue(console.exec("addtoviewport").startsWith("error:"));

        String help = console.exec("help");
        assertTrue(help.contains("createwidget"));
        assertTrue(help.contains("addtoviewport"));
        assertTrue(help.contains("hidewidget"));
        assertTrue(help.contains("showwidget"));
        assertTrue(help.contains("removefromparent"));
        assertTrue(help.contains("widgets"));
    }

    @Test
    public void dumpAndStatIncludeViewportCount() {
        game.openLevel("Hall");
        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        assertTrue(out.toString().contains("world.widgets=1"));

        viewport.find(HallGameMode.SAMPLE_WIDGET_NAME).hide();
        out.setLength(0);
        game.world().appendDump(out);
        assertTrue(out.toString().contains("world.widgets=1"));

        String stat = game.console().exec("stat");
        assertTrue(stat.contains("widgets=1"));
    }

    @Test
    public void worldAndGameInstanceShareTheSameViewport() {
        assertSame(game.viewport(), game.world().viewport());
        assertSame(game.viewport(), GameplayStatics.getWidgetViewport(game.world()));
        World standalone = new World();
        assertNotNull(standalone.viewport());
        TextWidget label = standalone.viewport().createWidget(TextWidget.class, "Solo");
        assertTrue(label.addToViewport());
        assertEquals(1, standalone.viewport().viewportCount());
        standalone.destroyAll();
        assertEquals(0, standalone.viewport().widgetCount());
    }

    @Test
    public void hostIsNotifiedWithoutAndroid() {
        RecordingWidgetHost host = new RecordingWidgetHost();
        viewport.setHost(host);
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        label.setText("Hello");
        label.addToViewport();
        label.hide();
        label.setText("Bye");
        label.removeFromParent();

        assertEquals(1, host.added);
        assertEquals(1, host.removed);
        assertTrue(host.changed >= 2);
        assertEquals("Hint", host.lastName);
        assertTrue(host.log.contains("add Hint"));
        assertTrue(host.log.contains("remove Hint"));
    }

    static final class RecordingWidgetHost implements WidgetHost {
        int added;
        int removed;
        int changed;
        String lastName;
        final List<String> log = new ArrayList<>();

        @Override
        public void widgetAdded(Widget widget) {
            added++;
            lastName = widget.name();
            log.add("add " + widget.name());
        }

        @Override
        public void widgetRemoved(Widget widget) {
            removed++;
            lastName = widget.name();
            log.add("remove " + widget.name());
        }

        @Override
        public void widgetChanged(Widget widget) {
            changed++;
            lastName = widget.name();
            log.add("change " + widget.name());
        }
    }
}
