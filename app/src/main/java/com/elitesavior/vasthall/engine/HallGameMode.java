package com.elitesavior.vasthall.engine;

/**
 * Demo session rules for the Hall map. Default pawn is {@link PlayerPawn}
 * (already placed by {@code levels/Hall.json}; this mode will spawn one
 * only if a map omitted it).
 *
 * <p>{@link #startPlay()} schedules a one-shot {@link TimerManager} hook
 * after {@link #DELAYED_START_SECONDS} as the sample delayed-start use,
 * binds multicast listeners for later actor-spawn / level-unload
 * engine events, adds a sample {@link TextWidget} to the viewport
 * ({@code CreateWidget} + {@code AddToViewport}), and binds Jump / Move /
 * Look on the local {@link PlayerController}.
 */
public class HallGameMode extends GameMode {
    public static final float DELAYED_START_SECONDS = 0.25f;
    public static final String SAMPLE_WIDGET_NAME = "HallTitle";
    public static final String SAMPLE_WIDGET_TEXT = "HALL";

    private TimerHandle delayedStart;
    private DelegateHandle actorSpawned;
    private DelegateHandle levelUnloaded;
    private DelegateHandle jumpStarted;
    private DelegateHandle jumpCompleted;
    private DelegateHandle moveTriggered;
    private DelegateHandle lookTriggered;
    private TextWidget sampleWidget;
    private InputActionValue lastMove = InputActionValue.axis2D(0.0f, 0.0f);
    private InputActionValue lastLook = InputActionValue.axis2D(0.0f, 0.0f);
    private int delayedStartCount;
    private int actorSpawnedCount;
    private int levelUnloadedCount;
    private int jumpStartedCount;
    private int jumpCompletedCount;

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
        bindSampleActions();
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
        unbindSampleActions();
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

    public int jumpStartedCount() {
        return jumpStartedCount;
    }

    public int jumpCompletedCount() {
        return jumpCompletedCount;
    }

    public InputActionValue lastMove() {
        return lastMove;
    }

    public InputActionValue lastLook() {
        return lastLook;
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

    private void bindSampleActions() {
        PlayerController controller = playerController();
        if (controller == null) {
            return;
        }
        jumpStarted = controller.bindAction(
                InputAction.JUMP, InputTrigger.STARTED, value -> jumpStartedCount++);
        jumpCompleted = controller.bindAction(
                InputAction.JUMP, InputTrigger.COMPLETED, value -> jumpCompletedCount++);
        moveTriggered = controller.bindAxis(InputAction.MOVE, value -> lastMove = value);
        lookTriggered = controller.bindAxis(InputAction.LOOK, value -> lastLook = value);
    }

    private void unbindSampleActions() {
        PlayerController controller = playerController();
        if (controller != null) {
            controller.unbind(jumpStarted);
            controller.unbind(jumpCompleted);
            controller.unbind(moveTriggered);
            controller.unbind(lookTriggered);
        }
        jumpStarted = null;
        jumpCompleted = null;
        moveTriggered = null;
        lookTriggered = null;
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
