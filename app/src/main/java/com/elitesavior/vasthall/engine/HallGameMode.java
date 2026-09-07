package com.elitesavior.vasthall.engine;

/**
 * Demo session rules for the Hall map. Default pawn is {@link PlayerPawn}
 * (already placed by {@code levels/Hall.json}; this mode will spawn one
 * only if a map omitted it).
 *
 * <p>{@link #startPlay()} schedules a one-shot {@link TimerManager} hook
 * after {@link #DELAYED_START_SECONDS} as the sample delayed-start use,
 * and binds multicast listeners for later actor-spawn / level-unload
 * engine events.
 */
public class HallGameMode extends GameMode {
    public static final float DELAYED_START_SECONDS = 0.25f;

    private TimerHandle delayedStart;
    private DelegateHandle actorSpawned;
    private DelegateHandle levelUnloaded;
    private int delayedStartCount;
    private int actorSpawnedCount;
    private int levelUnloadedCount;

    @Override
    public void startPlay() {
        super.startPlay();
        TimerManager timers = timerManager();
        if (timers != null) {
            delayedStart = timers.setTimer(this::onDelayedStart, DELAYED_START_SECONDS, false);
        }
        EventDispatcher events = events();
        if (events != null) {
            actorSpawned = events.bind(EventType.ACTOR_SPAWNED, this::onActorSpawned);
            levelUnloaded = events.bind(EventType.LEVEL_UNLOADED, this::onLevelUnloaded);
        }
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
