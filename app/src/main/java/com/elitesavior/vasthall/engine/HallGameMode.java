package com.elitesavior.vasthall.engine;

/**
 * Demo session rules for the Hall map. Default pawn is {@link PlayerPawn}
 * (already placed by {@code levels/Hall.json}; this mode will spawn one
 * only if a map omitted it).
 *
 * <p>{@link #startPlay()} schedules a one-shot {@link TimerManager} hook
 * after {@link #DELAYED_START_SECONDS} as the sample delayed-start use.
 */
public class HallGameMode extends GameMode {
    public static final float DELAYED_START_SECONDS = 0.25f;

    private TimerHandle delayedStart;
    private int delayedStartCount;

    @Override
    public void startPlay() {
        super.startPlay();
        TimerManager timers = timerManager();
        if (timers == null) {
            return;
        }
        delayedStart = timers.setTimer(this::onDelayedStart, DELAYED_START_SECONDS, false);
    }

    @Override
    public void endPlay() {
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

    private void onDelayedStart() {
        delayedStartCount++;
    }
}
