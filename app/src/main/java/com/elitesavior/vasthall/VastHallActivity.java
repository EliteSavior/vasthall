package com.elitesavior.vasthall;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.elitesavior.vasthall.engine.Actor;
import com.elitesavior.vasthall.engine.DeveloperConsole;
import com.elitesavior.vasthall.engine.GameInstance;
import com.elitesavior.vasthall.engine.GameMode;
import com.elitesavior.vasthall.engine.HallBeaconActor;
import com.elitesavior.vasthall.engine.InputKeys;
import com.elitesavior.vasthall.engine.Level;
import com.elitesavior.vasthall.engine.World;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class VastHallActivity extends Activity implements
        SurfaceHolder.Callback,
        View.OnTouchListener,
        View.OnGenericMotionListener,
        View.OnKeyListener,
        StickView.Probe {

    static final String PREFS_NAME = "vasthall_prefs";
    static final String PREF_CONTROLS_SCHEME = "vasthall.controls.scheme";
    static final String SCHEME_DUAL = "dual";
    static final String SCHEME_LEGACY = "legacy";
    static final String SCHEME_FLAT = "flat";
    private static final String TAG = "VastHall";

    private TextView jump;
    private StickView leftZone;
    private View menuButton;
    private View menuPanel;
    private StickView rightZone;
    private View settingsPanel;
    private View debugPanel;
    private View consolePanel;
    private View consoleButton;
    private TextView consoleOutput;
    private EditText consoleInput;
    private TextView dbgMark;
    private SurfaceView surface;
    private PlayHud playHud;
    private PlayInputMachine playInput;
    private FlatPadRouter flatPad;
    private FlatPadOverlay flatOverlay;
    private ControlSchemeGate schemeGate;
    private boolean dual = true;
    private boolean menuOpen;
    private boolean watchdogRunning;
    private HudAxes hudAxes;
    private DebugHub debugHub;
    private GameInstance game;
    private World world;
    private DeveloperConsole console;
    private TextView engineMark;
    private WidgetOverlay widgetOverlay;
    private long lastWorldTickNs;
    private boolean consoleOpen;

    private final Choreographer.FrameCallback watchdog = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (leftZone != null && !leftZone.hasActiveFinger()) {
                leftZone.enforceIdle();
            }
            if (rightZone != null && !rightZone.hasActiveFinger()) {
                rightZone.enforceIdle();
            }
            if (hudAxes != null) {
                hudAxes.pulse();
            }
            if (schemeGate != null && schemeGate.newPadEnabled() && flatPad != null) {
                long lag = hudAxes == null ? 0L : hudAxes.jniLagMs();
                flatPad.tick(lag);
                flatPad.publish();
            }
            tickWorld(frameTimeNanos);
            if (debugHub != null) {
                if (hudAxes != null) {
                    debugHub.setJniLagMs(hudAxes.jniLagMs());
                }
                float lookX;
                float lookY;
                if (schemeGate != null && schemeGate.newPadEnabled() && flatPad != null) {
                    lookX = flatPad.lookX();
                    lookY = flatPad.lookY();
                } else {
                    lookX = rightZone == null ? 0.0f : rightZone.axisX();
                    lookY = rightZone == null ? 0.0f : rightZone.axisY();
                }
                debugHub.tickFrame(lookX, lookY);
            }
            if (watchdogRunning) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private native void nativeInit();
    private native void nativeJumpButton(boolean down);
    private native void nativeKey(int keyCode, boolean down);
    private native void nativeMouseDelta(float dx, float dy);
    private native void nativeResize(int width, int height);
    private native void nativeSetControlScheme(int scheme);
    private native void nativeSetLookAxes(float x, float y);
    private native void nativeSetMoveAxes(float x, float y);
    private native void nativeSetSurface(Surface surface);
    private native void nativeSetUiPaused(boolean paused);
    private native void nativeShutdown();
    private native void nativeStart();
    private native void nativeStop();
    private native void nativeTouch(int action, int pointerId, float x, float y);

    static {
        System.loadLibrary("vasthall");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(6);
        requestWindowFeature(1);
        getWindow().addFlags(1152);
        hideSystemUi();

        ControlScheme savedScheme = ControlScheme.fromPref(
                prefs().getString(PREF_CONTROLS_SCHEME, SCHEME_DUAL));
        schemeGate = new ControlSchemeGate(savedScheme);
        dual = schemeGate.legacyPadEnabled();
        debugHub = new DebugHub(prefs());

        hudAxes = new HudAxes(new HudAxes.NativeSink() {
            @Override
            public void setMove(float x, float y) {
                nativeSetMoveAxes(x, y);
            }

            @Override
            public void setLook(float x, float y) {
                nativeSetLookAxes(x, y);
            }

            @Override
            public void setJump(boolean down) {
                nativeJumpButton(down);
            }
        });

        playInput = new PlayInputMachine();
        playInput.setSink(new PlayInputMachine.Sink() {
            @Override
            public void onBegin(PlayInputMachine.Target target, int pointerId) {
            }

            @Override
            public void onEnd(
                    PlayInputMachine.Target target, int pointerId, String reason) {
                if (target == PlayInputMachine.Target.LEGACY) {
                    nativeTouch(MotionEvent.ACTION_CANCEL, pointerId, 0.0f, 0.0f);
                }
            }

            @Override
            public void onKey(int keyCode, boolean down) {
                nativeKey(keyCode, down);
            }
        });

        FrameLayout root = new FrameLayout(this);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        surface.setOnGenericMotionListener(this);
        surface.setOnKeyListener(this);
        surface.setClickable(false);
        surface.setFocusable(true);
        surface.setFocusableInTouchMode(true);
        surface.setZOrderOnTop(false);

        Point real = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(real);
        int landscapeW = Math.max(real.x, real.y);
        int landscapeH = Math.min(real.x, real.y);
        if (landscapeW > landscapeH) {
            surface.getHolder().setFixedSize(landscapeW, landscapeH);
        }
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        jump = circleButton(getString(R.string.jump));
        jump.setClickable(false);
        jump.setFocusable(false);
        jump.setFocusableInTouchMode(false);
        jump.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            String who = null;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    debugHub.setJump(true);
                    hudAxes.setJump(true);
                    PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, true);
                    break;
                case MotionEvent.ACTION_UP:
                    who = "UP";
                    debugHub.setJump(false);
                    hudAxes.setJump(false);
                    PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    who = "CANCEL";
                    debugHub.setJump(false);
                    hudAxes.setJump(false);
                    PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, false);
                    break;
                default:
                    break;
            }
            debugHub.onTouch(
                    "jump",
                    event,
                    who,
                    leftZone == null ? 0.0f : leftZone.axisX(),
                    leftZone == null ? 0.0f : leftZone.axisY(),
                    rightZone == null ? 0.0f : rightZone.axisX(),
                    rightZone == null ? 0.0f : rightZone.axisY());
            return true;
        });
        FrameLayout.LayoutParams jumpLp =
                new FrameLayout.LayoutParams(dp(72), dp(72));
        jumpLp.gravity = Gravity.BOTTOM | Gravity.END;
        jumpLp.rightMargin = dp(186);
        jumpLp.bottomMargin = dp(48);
        jump.setLayoutParams(jumpLp);

        playHud = buildPlayHud();
        root.addView(playHud, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        flatPad = new FlatPadRouter();
        flatPad.setProbe((zone, reason, ptr, ax, ay) -> {
            if (debugHub != null) {
                debugHub.onZero(zone, reason, ptr, ax, ay);
            }
        });
        flatPad.setSink(new FlatPadRouter.Sink() {
            @Override
            public void setMove(float x, float y) {
                hudAxes.setMove(x, y);
                PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_MOVE, x, y);
            }

            @Override
            public void setLook(float x, float y) {
                hudAxes.setLook(x, y);
                PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_LOOK, x, y);
            }

            @Override
            public void setJump(boolean down) {
                if (debugHub != null) {
                    debugHub.setJump(down);
                }
                hudAxes.setJump(down);
                PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, down);
            }
        });
        flatOverlay = new FlatPadOverlay(this, flatPad);
        flatOverlay.setProbe(this);
        flatOverlay.setJumpChrome(dp(72), dp(186), dp(48), getString(R.string.jump));
        root.addView(flatOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        menuButton = buildMenuButton();
        FrameLayout.LayoutParams menuLp =
                new FrameLayout.LayoutParams(dp(88), dp(40));
        menuLp.gravity = 8388659;
        menuLp.leftMargin = dp(12);
        menuLp.topMargin = dp(10);
        root.addView(menuButton, menuLp);

        dbgMark = new TextView(this);
        dbgMark.setText(R.string.dbg_mark);
        dbgMark.setTextColor(0x99d4783a);
        dbgMark.setTextSize(11.0f);
        dbgMark.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        dbgMark.setClickable(false);
        dbgMark.setFocusable(false);
        dbgMark.setFocusableInTouchMode(false);
        dbgMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dbgMark.setPadding(dp(6), dp(2), dp(6), dp(2));
        dbgMark.setVisibility(View.GONE);
        FrameLayout.LayoutParams dbgLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
        dbgLp.gravity = Gravity.TOP | Gravity.END;
        dbgLp.topMargin = dp(10);
        dbgLp.rightMargin = dp(12);
        root.addView(dbgMark, dbgLp);

        final FrameLayout.LayoutParams consoleBtnLp;
        if (consoleUiEnabled()) {
            consoleButton = buildConsoleButton();
            consoleBtnLp = new FrameLayout.LayoutParams(dp(40), dp(40));
            consoleBtnLp.gravity = Gravity.TOP | Gravity.END;
            consoleBtnLp.topMargin = dp(10);
            consoleBtnLp.rightMargin = dp(56);
            root.addView(consoleButton, consoleBtnLp);
        } else {
            consoleBtnLp = null;
        }

        engineMark = new TextView(this);
        engineMark.setTextColor(0xccd4783a);
        engineMark.setTextSize(11.0f);
        engineMark.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        engineMark.setClickable(false);
        engineMark.setFocusable(false);
        engineMark.setFocusableInTouchMode(false);
        engineMark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        engineMark.setPadding(dp(6), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams engineLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
        engineLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        engineLp.topMargin = dp(10);
        root.addView(engineMark, engineLp);

        widgetOverlay = new WidgetOverlay(this);
        FrameLayout.LayoutParams widgetLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
        widgetLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        widgetLp.topMargin = dp(36);
        root.addView(widgetOverlay, widgetLp);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            if (insets.getDisplayCutout() != null) {
                top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
            }
            menuLp.topMargin = dp(8) + top;
            menuButton.setLayoutParams(menuLp);
            dbgLp.topMargin = dp(8) + top;
            dbgMark.setLayoutParams(dbgLp);
            engineLp.topMargin = dp(8) + top;
            engineMark.setLayoutParams(engineLp);
            widgetLp.topMargin = dp(34) + top;
            widgetOverlay.setLayoutParams(widgetLp);
            if (consoleButton != null && consoleBtnLp != null) {
                consoleBtnLp.topMargin = dp(8) + top;
                consoleButton.setLayoutParams(consoleBtnLp);
            }
            return insets;
        });

        menuPanel = buildMenuPanel();
        root.addView(menuPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        settingsPanel = buildSettingsPanel();
        root.addView(settingsPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        debugPanel = buildDebugPanel();
        root.addView(debugPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (consoleUiEnabled()) {
            consolePanel = buildConsolePanel();
            root.addView(consolePanel, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        setContentView(root);
        nativeInit();
        beginPlayWorld();
        hudAxes.start();
        applyScheme(schemeGate.current(), false);
        applyDbgMark();
        updateEngineHud();
        debugHub.lifecycle("onCreate");
        root.requestApplyInsets();
        surface.requestFocus();
    }

    private PlayHud buildPlayHud() {
        leftZone = new StickView(this, true);
        leftZone.setListener((x, y) -> {
            hudAxes.setMove(x, y);
            PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_MOVE, x, y);
        });
        leftZone.setProbe(this);

        rightZone = new StickView(this, false);
        rightZone.setListener((x, y) -> {
            hudAxes.setLook(x, y);
            PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_LOOK, x, y);
        });
        rightZone.setProbe(this);

        return new PlayHud(this, playInput, leftZone, rightZone, jump);
    }

    @Override
    public void onTouch(String zone, MotionEvent event, String whoZeroed) {
        if (debugHub == null) {
            return;
        }
        float axisLx;
        float axisLy;
        float axisRx;
        float axisRy;
        if (schemeGate != null && schemeGate.newPadEnabled() && flatPad != null) {
            axisLx = flatPad.moveX();
            axisLy = flatPad.moveY();
            axisRx = flatPad.lookX();
            axisRy = flatPad.lookY();
        } else {
            axisLx = leftZone == null ? 0.0f : leftZone.axisX();
            axisLy = leftZone == null ? 0.0f : leftZone.axisY();
            axisRx = rightZone == null ? 0.0f : rightZone.axisX();
            axisRy = rightZone == null ? 0.0f : rightZone.axisY();
        }
        debugHub.onTouch(zone, event, whoZeroed, axisLx, axisLy, axisRx, axisRy);
    }

    @Override
    public void onZero(String zone, String reason, int ptr, float ax, float ay) {
        if (debugHub != null) {
            debugHub.onZero(zone, reason, ptr, ax, ay);
        }
    }

    @Override
    public void onCapture(String zone, boolean hasCapture) {
        if (debugHub != null) {
            debugHub.setCapture(hasCapture);
            debugHub.lifecycle(zone + (hasCapture ? " captureGrant" : " captureLost"));
        }
    }

    private Button buildMenuButton() {
        Button menu = textButton(getString(R.string.menu), 0xcc1a1c22);
        menu.setOnClickListener(view -> openMenu());
        return menu;
    }

    static boolean consoleUiEnabled() {
        return DeveloperConsoleGate.UI_ENABLED;
    }

    private Button buildConsoleButton() {
        Button button = textButton(getString(R.string.console_tilde), 0xcc1a1c22);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setOnClickListener(view -> toggleConsole());
        return button;
    }

    private View buildConsolePanel() {
        LinearLayout column = overlayColumn();
        column.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        column.addView(title(getString(R.string.console)));

        consoleOutput = new TextView(this);
        consoleOutput.setTextColor(0xffe6e6e6);
        consoleOutput.setTextSize(13.0f);
        consoleOutput.setTypeface(Typeface.MONOSPACE);
        consoleOutput.setText("Type help");

        ScrollView outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setBackgroundColor(0xff111218);
        outputScroll.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams outputLp =
                new LinearLayout.LayoutParams(dp(320), dp(140));
        outputScroll.setLayoutParams(outputLp);
        outputScroll.addView(consoleOutput);
        column.addView(outputScroll);

        consoleInput = new EditText(this);
        consoleInput.setHint(R.string.console_hint);
        consoleInput.setTextColor(0xffffffff);
        consoleInput.setHintTextColor(0x88ffffff);
        consoleInput.setSingleLine(true);
        consoleInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(10);
        consoleInput.setLayoutParams(inputLp);
        consoleInput.setOnEditorActionListener((view, actionId, event) -> {
            runConsoleLine();
            return true;
        });
        column.addView(consoleInput);

        column.addView(menuAction(getString(R.string.console_run), this::runConsoleLine));
        column.addView(menuAction(getString(R.string.resume), this::closeOverlays));

        ScrollView scroll = new ScrollView(this);
        scroll.setVisibility(View.GONE);
        scroll.setFillViewport(true);
        scroll.setClickable(true);
        scroll.setBackgroundColor(0xe6111216);
        scroll.addView(column);
        return scroll;
    }

    private void runConsoleLine() {
        if (console == null || consoleInput == null) {
            return;
        }
        String line = consoleInput.getText() == null ? "" : consoleInput.getText().toString();
        if (line.trim().isEmpty()) {
            return;
        }
        console.exec(line);
        consoleInput.setText("");
        refreshConsoleOutput();
        updateEngineHud();
    }

    private void refreshConsoleOutput() {
        if (consoleOutput == null || console == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (String entry : console.log()) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(entry);
        }
        if (text.length() == 0) {
            text.append("Type help");
        }
        consoleOutput.setText(text.toString());
    }

    private void zeroAllControls(String reason) {
        Log.i(TAG, "controls INPUT_ZERO reason=" + reason);
        StickView.TRACE.add("controls INPUT_ZERO reason=" + reason);
        if (debugHub != null) {
            debugHub.onZero("both", reason, -1, 0.0f, 0.0f);
        }
        if (flatPad != null) {
            flatPad.releaseAll(reason);
        }
        if (flatOverlay != null) {
            flatOverlay.invalidate();
        }
        if (playHud != null) {
            playHud.cancelAll(reason);
        } else if (playInput != null) {
            playInput.releaseAll(reason);
        }
        if (leftZone != null) {
            leftZone.recenter(reason);
        }
        if (rightZone != null) {
            rightZone.recenter(reason);
        }
        if (hudAxes != null) {
            hudAxes.setMove(0.0f, 0.0f);
            hudAxes.setLook(0.0f, 0.0f);
            hudAxes.setJump(false);
        }
        PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_MOVE, 0.0f, 0.0f);
        PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_LOOK, 0.0f, 0.0f);
        PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, false);
        if (debugHub != null) {
            debugHub.setJump(false);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View buildMenuPanel() {
        LinearLayout panel = overlayColumn();
        panel.setVisibility(View.GONE);
        panel.addView(title(getString(R.string.menu)));
        panel.addView(menuAction(getString(R.string.resume), this::closeOverlays));
        panel.addView(menuAction(getString(R.string.settings), this::openSettings));
        panel.addView(menuAction(getString(R.string.debug), this::openDebug));
        return panel;
    }

    private View buildSettingsPanel() {
        LinearLayout panel = overlayColumn();
        panel.setVisibility(View.GONE);
        panel.addView(title(getString(R.string.controls)));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        ControlScheme selected = schemeGate == null
                ? ControlScheme.LEGACY_PAD
                : schemeGate.current();
        int checkedId = View.NO_ID;
        for (ControlScheme option : ControlScheme.settingsOrder()) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setTag(option);
            button.setText(option.label());
            button.setTextColor(0xffffffff);
            button.setTextSize(18.0f);
            group.addView(button);
            if (option == selected) {
                checkedId = button.getId();
            }
        }
        if (checkedId != View.NO_ID) {
            group.check(checkedId);
        }
        group.setOnCheckedChangeListener((radioGroup, id) -> {
            View checked = radioGroup.findViewById(id);
            if (!(checked instanceof RadioButton)) {
                return;
            }
            Object tag = checked.getTag();
            if (!(tag instanceof ControlScheme)) {
                return;
            }
            ControlScheme next = (ControlScheme) tag;
            prefs().edit().putString(PREF_CONTROLS_SCHEME, next.prefValue()).apply();
            applyScheme(next, true);
        });
        panel.addView(group);
        panel.addView(menuAction(getString(R.string.back_to_menu), this::openMenu));
        return panel;
    }

    private View buildDebugPanel() {
        LinearLayout column = overlayColumn();
        column.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        column.addView(title(getString(R.string.debug)));

        CheckBox master = debugCheck(getString(R.string.debug_master), debugHub.on());
        master.setOnCheckedChangeListener((button, checked) -> {
            debugHub.setOn(checked);
            applyDbgMark();
        });
        column.addView(master);

        column.addView(menuAction(getString(R.string.copy_dump), this::copyDump));
        column.addView(menuAction(getString(R.string.share_dump), this::shareDump));
        column.addView(menuAction(getString(R.string.clear_log), () -> {
            debugHub.clearLogs();
            Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show();
        }));

        column.addView(debugCheckBound(getString(R.string.debug_controls), DebugHub.PREF_CONTROLS));
        column.addView(debugCheckBound(getString(R.string.debug_camera), DebugHub.PREF_CAMERA));
        column.addView(debugCheckBound(getString(R.string.debug_input), DebugHub.PREF_INPUT));
        column.addView(debugCheckBound(getString(R.string.debug_engine), DebugHub.PREF_ENGINE));
        column.addView(debugCheckBound(getString(R.string.debug_lifecycle), DebugHub.PREF_LIFECYCLE));
        if (consoleUiEnabled()) {
            column.addView(menuAction(getString(R.string.console), this::openConsole));
        }
        column.addView(menuAction(getString(R.string.back_to_menu), this::openMenu));

        ScrollView scroll = new ScrollView(this);
        scroll.setVisibility(View.GONE);
        scroll.setFillViewport(true);
        scroll.setClickable(true);
        scroll.setBackgroundColor(0xe6111216);
        scroll.addView(column);
        return scroll;
    }

    private CheckBox debugCheckBound(String label, String prefKey) {
        CheckBox box = debugCheck(label, prefs().getBoolean(prefKey, true));
        box.setOnCheckedChangeListener((button, checked) -> debugHub.setSubsystem(prefKey, checked));
        return box;
    }

    private CheckBox debugCheck(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setId(View.generateViewId());
        box.setSaveEnabled(false);
        box.setText(label);
        box.setTextColor(0xffffffff);
        box.setTextSize(16.0f);
        box.setChecked(checked);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = dp(8);
        box.setLayoutParams(layoutParams);
        return box;
    }

    private LinearLayout overlayColumn() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(17);
        panel.setBackgroundColor(0xe6111216);
        panel.setClickable(true);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        return panel;
    }

    private TextView title(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(0xffffffff);
        title.setTextSize(22.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(18));
        title.setGravity(17);
        return title;
    }

    private Button menuAction(String label, Runnable action) {
        Button button = textButton(label, 0xff2a2d36);
        LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(dp(260), dp(48));
        layoutParams.topMargin = dp(10);
        button.setLayoutParams(layoutParams);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private Button textButton(String label, int fill) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xffffffff);
        button.setAllCaps(false);
        button.setBackgroundColor(fill);
        return button;
    }

    private TextView circleButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(0xffffffff);
        button.setGravity(17);
        button.setTextSize(14.0f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundColor(0xe6c45c28);
        button.setClickable(true);
        return button;
    }

    private void applyScheme(ControlScheme next, boolean notifyJump) {
        if (schemeGate == null) {
            schemeGate = new ControlSchemeGate(next);
        }
        schemeGate.select(next, () -> zeroAllControls("APPLY_SCHEME"));
        ControlScheme scheme = schemeGate.current();
        dual = scheme == ControlScheme.LEGACY_PAD;
        nativeSetControlScheme(scheme == ControlScheme.LEGACY_TOUCH ? 1 : 0);
        surface.setOnTouchListener(scheme == ControlScheme.LEGACY_TOUCH ? this : null);
        surface.setClickable(false);
        if (playHud != null) {
            playHud.setPlayVisible(scheme == ControlScheme.LEGACY_PAD);
        }
        if (jump != null) {
            jump.setVisibility(scheme == ControlScheme.LEGACY_PAD ? View.VISIBLE : View.GONE);
        }
        if (flatOverlay != null) {
            flatOverlay.setPlayVisible(scheme == ControlScheme.NEW_PAD);
        }
        if (notifyJump && hudAxes != null) {
            hudAxes.setJump(false);
        }
    }

    private void openMenu() {
        menuOpen = true;
        consoleOpen = false;
        menuPanel.setVisibility(View.VISIBLE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.GONE);
        hideConsolePanel();
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
        zeroAllControls("OPEN_MENU");
    }

    private void openSettings() {
        menuOpen = true;
        consoleOpen = false;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.VISIBLE);
        debugPanel.setVisibility(View.GONE);
        hideConsolePanel();
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
    }

    private void openDebug() {
        menuOpen = true;
        consoleOpen = false;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.VISIBLE);
        hideConsolePanel();
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
        debugHub.lifecycle("openDebug");
    }

    private void openConsole() {
        if (!consoleUiEnabled() || consolePanel == null) {
            return;
        }
        menuOpen = true;
        consoleOpen = true;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.GONE);
        consolePanel.setVisibility(View.VISIBLE);
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
        zeroAllControls("OPEN_CONSOLE");
        refreshConsoleOutput();
        if (consoleInput != null) {
            consoleInput.requestFocus();
        }
        if (debugHub != null) {
            debugHub.lifecycle("openConsole");
        }
    }

    private void toggleConsole() {
        if (consoleOpen) {
            closeOverlays();
        } else {
            openConsole();
        }
    }

    private void hideConsolePanel() {
        if (consolePanel != null) {
            consolePanel.setVisibility(View.GONE);
        }
    }

    private void closeOverlays() {
        menuOpen = false;
        consoleOpen = false;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.GONE);
        hideConsolePanel();
        nativeSetUiPaused(false);
        debugHub.setPaused(false);
        if (hudAxes != null) {
            hudAxes.setJump(false);
        }
        applyDbgMark();
    }

    private void applyDbgMark() {
        if (dbgMark != null) {
            dbgMark.setVisibility(debugHub != null && debugHub.on() ? View.VISIBLE : View.GONE);
        }
    }

    private void beginPlayWorld() {
        game = GameInstance.withDemoAssets();
        game.setSaveDirectory(new File(getFilesDir(), "SaveGames"));
        game.init();
        game.openLevel("Hall");
        world = game.world();
        console = game.console();
        if (widgetOverlay != null) {
            widgetOverlay.bind(world.viewport());
        }
        lastWorldTickNs = 0L;
    }

    private void tickWorld(long frameTimeNanos) {
        if (world == null) {
            return;
        }
        if (!menuOpen) {
            float dt;
            if (lastWorldTickNs == 0L) {
                dt = 0.016f;
            } else {
                dt = (frameTimeNanos - lastWorldTickNs) / 1_000_000_000.0f;
            }
            lastWorldTickNs = frameTimeNanos;
            world.tick(DebugHub.clampDt(dt));
        }
        updateEngineHud();
    }

    private void updateEngineHud() {
        if (engineMark == null || world == null) {
            return;
        }
        List<HallBeaconActor> beacons = world.actorsOf(HallBeaconActor.class);
        String beaconBit = "-";
        if (!beacons.isEmpty()) {
            HallBeaconActor beacon = beacons.get(0);
            beaconBit = String.format(
                    Locale.US,
                    "HallBeacon y=%.2f yaw=%.0f",
                    beacon.transform().location.y,
                    beacon.transform().rotation.yaw);
        }
        String levelBit = "";
        List<Level> levels = world.loadedLevels();
        if (levels.size() == 1) {
            levelBit = levels.get(0).name() + " ";
        } else if (levels.size() > 1) {
            levelBit = levels.size() + "lv ";
        }
        int componentCount = 0;
        for (Actor actor : world.actors()) {
            componentCount += actor.componentCount();
        }
        GameMode mode = world.gameMode();
        String modeBit = mode == null ? "" : "mode=" + mode.getClass().getSimpleName() + " ";
        engineMark.setText(String.format(
                Locale.US,
                "SCENE %s%sactors=%d comps=%d assets=%d timers=%d events=%d saves=%d audio=%d widgets=%d overlaps=%d actions=%d  %s",
                levelBit,
                modeBit,
                world.actorCount(),
                componentCount,
                world.assets().size(),
                world.timerManager().timerCount(),
                world.events().listenerCount(),
                game == null ? 0 : game.saveSlots().size(),
                world.audio().playingCount(),
                world.viewport().viewportCount(),
                world.collision().overlapCount(),
                world.input().actionCount(),
                beaconBit));
    }

    private String currentDump() {
        String scheme = schemeGate == null
                ? (dual ? SCHEME_DUAL : SCHEME_LEGACY)
                : schemeGate.current().prefValue();
        String version = "0.34.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return debugHub.buildDump(
                version, scheme, leftZone, rightZone, debugHub.jumpDown(), world, flatPad);
    }

    private void copyDump() {
        String dump = currentDump();
        writeDumpFile(dump);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("vasthall-debug", dump));
        }
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void shareDump() {
        String dump = currentDump();
        writeDumpFile(dump);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, dump);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_dump)));
        } catch (ActivityNotFoundException missing) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeDumpFile(String dump) {
        File file = new File(getFilesDir(), "last-debug.txt");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(dump.getBytes(StandardCharsets.UTF_8));
        } catch (Exception io) {
            Log.w(TAG, "debug file write failed", io);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        watchdogRunning = true;
        Choreographer.getInstance().postFrameCallback(watchdog);
        if (debugHub != null) {
            debugHub.lifecycle("onResume");
            if (!menuOpen) {
                debugHub.setPaused(false);
            }
        }
        applyDbgMark();
    }

    @Override
    protected void onPause() {
        watchdogRunning = false;
        if (debugHub != null) {
            debugHub.lifecycle("onPause");
            debugHub.setPaused(true);
        }
        zeroAllControls("PAUSE");
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (debugHub != null) {
            debugHub.lifecycle(hasFocus ? "focusGained" : "focusLost");
        }
        if (hasFocus) {
            hideSystemUi();
        } else {
            zeroAllControls("FOCUS_LOST");
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (debugHub != null) {
            debugHub.lifecycle("surfaceCreated");
        }
        nativeSetSurface(holder.getSurface());
        nativeStart();
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged before " + width + "x" + height);
        int w = width;
        int h = height;
        if (w > 0 && h > 0 && w < h) {
            int tmp = w;
            w = h;
            h = tmp;
        }
        Point real = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(real);
        int landscapeW = Math.max(real.x, real.y);
        int landscapeH = Math.min(real.x, real.y);
        if (landscapeW > landscapeH) {
            w = landscapeW;
            h = landscapeH;
            if (holder.getSurfaceFrame().width() != w
                    || holder.getSurfaceFrame().height() != h) {
                holder.setFixedSize(w, h);
            }
        }
        Log.i(TAG, "surfaceChanged after " + w + "x" + h);
        if (debugHub != null) {
            debugHub.setNativeSize(w, h);
            debugHub.setSurfaceSize(
                    holder.getSurfaceFrame().width(),
                    holder.getSurfaceFrame().height());
            debugHub.setOrient("landscape");
            debugHub.lifecycle("surfaceChanged " + w + "x" + h);
        }
        nativeResize(w, h);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (debugHub != null) {
            debugHub.lifecycle("surfaceDestroyed");
        }
        nativeStop();
        nativeSetSurface(null);
    }

    @Override
    protected void onDestroy() {
        if (debugHub != null) {
            debugHub.lifecycle("onDestroy");
        }
        if (hudAxes != null) {
            hudAxes.stop();
        }
        if (widgetOverlay != null) {
            widgetOverlay.bind(null);
        }
        if (game != null) {
            game.shutdown();
        } else if (world != null) {
            world.destroyAll();
        }
        nativeStop();
        nativeShutdown();
        super.onDestroy();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (menuOpen || schemeGate == null || !schemeGate.legacyTouchEnabled()) {
            return true;
        }
        int action = event.getActionMasked();
        if (debugHub != null) {
            debugHub.onTouch(
                    "other",
                    event,
                    null,
                    leftZone == null ? 0.0f : leftZone.axisX(),
                    leftZone == null ? 0.0f : leftZone.axisY(),
                    rightZone == null ? 0.0f : rightZone.axisX(),
                    rightZone == null ? 0.0f : rightZone.axisY());
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pid = event.getPointerId(i);
                if (playInput.pointerMove(pid) == PlayInputMachine.Target.NONE) {
                    continue;
                }
                nativeTouch(MotionEvent.ACTION_MOVE, pid,
                        event.getX(i), event.getY(i));
            }
            return true;
        }
        int index = event.getActionIndex();
        int pid = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (!playInput.pointerDown(pid, PlayInputMachine.Target.LEGACY)) {
                return true;
            }
        }
        nativeTouch(action, pid, event.getX(index), event.getY(index));
        if (action == MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int cancelPid = event.getPointerId(i);
                if (i != index) {
                    nativeTouch(MotionEvent.ACTION_CANCEL, cancelPid,
                            event.getX(i), event.getY(i));
                }
                playInput.pointerUp(cancelPid, "CANCEL");
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP) {
            playInput.pointerUp(pid, "UP");
        }
        return true;
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        if ((event.getSource() & 8194) != 0) {
            float dx = event.getAxisValue(27);
            float dy = event.getAxisValue(28);
            if (dx != 0.0f || dy != 0.0f) {
                nativeMouseDelta(dx, dy);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (consoleUiEnabled() && keyCode == KeyEvent.KEYCODE_GRAVE) {
            if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
                toggleConsole();
            }
            return true;
        }
        if (consoleOpen) {
            return super.dispatchKeyEvent(event);
        }
        return handleGameKey(keyCode, event);
    }

    private boolean handleGameKey(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            playInput.keyDown(keyCode);
            PlayInputRouter.feedKey(world, keyCode, true);
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            playInput.keyUp(keyCode);
            PlayInputRouter.feedKey(world, keyCode, false);
            return true;
        }
        return true;
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (consoleOpen) {
                closeOverlays();
                return true;
            }
            if (menuOpen && debugPanel.getVisibility() == View.VISIBLE) {
                openMenu();
                return true;
            }
            if (menuOpen && settingsPanel.getVisibility() == View.VISIBLE) {
                openMenu();
                return true;
            }
            if (!menuOpen) {
                return false;
            }
            closeOverlays();
            return true;
        }
        return handleGameKey(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return onKey(surface, keyCode, event) || super.onKeyDown(keyCode, event);
        }
        return handleGameKey(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return onKey(surface, keyCode, event) || super.onKeyUp(keyCode, event);
        }
        return handleGameKey(keyCode, event);
    }
}
