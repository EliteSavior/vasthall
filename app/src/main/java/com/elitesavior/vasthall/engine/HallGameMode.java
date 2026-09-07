package com.elitesavior.vasthall.engine;

/**
 * Demo session rules for the Hall map. Default pawn is {@link PlayerPawn}
 * (already placed by {@code levels/Hall.json}; this mode will spawn one
 * only if a map omitted it).
 *
 * <p>{@link #startPlay()} schedules a one-shot {@link TimerManager} hook
 * after {@link #DELAYED_START_SECONDS} as the sample delayed-start use,
 * binds multicast listeners for later actor-spawn / level-unload
 * engine events, and adds a sample {@link TextWidget} to the viewport
 * ({@code CreateWidget} + {@code AddToViewport}).
 */
public class HallGameMode extends GameMode {
    public static final float DELAYED_START_SECONDS = 0.25f;
    public static final String SAMPLE_WIDGET_NAME = "HallTitle";
    public static final String SAMPLE_WIDGET_TEXT = "HALL";

    private TimerHandle delayedStart;
    private DelegateHandle actorSpawned;
    private DelegateHandle levelUnloaded;
    private TextWidget sampleWidget;
    private int delayedStartCount;
    private int actorSpawnedCount;
    private int levelUnloadedCount;

    @Override
    public void startPlay() {
        EventDispatcher events = events();
        if (events != null) {
            actorSpawned = events.bind(EventType.ACTOR_SPAWNED, this::onActorSpawned);
            levelUnloaded = events.bind(EventType.LEVEL_UNLOADED, this::onLevelUnloaded);
        }
        super.startPlay();
        TimerManager timers = timerManager();
        if (timers != null) {
            delayedStart = timers.setTimer(this::onDelayedStart, DELAYED_START_SECONDS, false);
        }
        addSampleWidget();
    }

    @Override
    public void endPlay() {
        EventDispatcher events = events();
        if (events != null) {
            events.unbind(actorSpawned);
            events.unbind(levelUnloaded);
        }
        actorSpawned = null;
        levelUnloaded = null;
        TimerManager timers = timerManager();
        if (timers != null) {
            timers.clearTimer(delayedStart);
        }
        delayedStart = null;
        destroySampleWidget();
        super.endPlay();
    }

    public int delayedStartCount() {
        return delayedStartCount;
    }

    public int actorSpawnedCount() {
        return actorSpawnedCount;
    }

    public int levelUnloadedCount() {
        return levelUnloadedCount;
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

    private void onDelayedStart() {
        delayedStartCount++;
    }

    private void onActorSpawned(ActorEvent event) {
        actorSpawnedCount++;
    }

    private void onLevelUnloaded(LevelEvent event) {
        levelUnloadedCount++;
    }
}
