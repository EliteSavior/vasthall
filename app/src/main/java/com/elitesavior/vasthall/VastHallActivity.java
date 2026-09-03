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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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
    private static final String TAG = "VastHall";

    private TextView jump;
    private StickView leftZone;
    private View menuButton;
    private View menuPanel;
    private StickView rightZone;
    private View settingsPanel;
    private View debugPanel;
    private TextView dbgMark;
    private SurfaceView surface;
    private LinearLayout zonesRow;
    private boolean dual = true;
    private boolean menuOpen;
    private boolean watchdogRunning;
    private HudAxes hudAxes;
    private DebugHub debugHub;

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
            if (debugHub != null) {
                if (hudAxes != null) {
                    debugHub.setJniLagMs(hudAxes.jniLagMs());
                }
                float lookX = rightZone == null ? 0.0f : rightZone.axisX();
                float lookY = rightZone == null ? 0.0f : rightZone.axisY();
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

        dual = !SCHEME_LEGACY.equals(
                prefs().getString(PREF_CONTROLS_SCHEME, SCHEME_DUAL));
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

        zonesRow = buildZonesRow();
        root.addView(zonesRow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        jump = circleButton(getString(R.string.jump));
        jump.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            String who = null;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    debugHub.setJump(true);
                    hudAxes.setJump(true);
                    break;
                case MotionEvent.ACTION_UP:
                    who = "UP";
                    debugHub.setJump(false);
                    hudAxes.setJump(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    who = "CANCEL";
                    debugHub.setJump(false);
                    hudAxes.setJump(false);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    who = "POINTER_UP";
                    debugHub.setJump(false);
                    hudAxes.setJump(false);
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
        jumpLp.gravity = 8388693;
        jumpLp.rightMargin = dp(186);
        jumpLp.bottomMargin = dp(48);
        root.addView(jump, jumpLp);

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

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            if (insets.getDisplayCutout() != null) {
                top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
            }
            menuLp.topMargin = dp(8) + top;
            menuButton.setLayoutParams(menuLp);
            dbgLp.topMargin = dp(8) + top;
            dbgMark.setLayoutParams(dbgLp);
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

        setContentView(root);
        nativeInit();
        hudAxes.start();
        applyScheme(dual, false);
        applyDbgMark();
        debugHub.lifecycle("onCreate");
        root.requestApplyInsets();
        surface.requestFocus();
    }

    private LinearLayout buildZonesRow() {
        SplitStickRow row = new SplitStickRow(this);

        leftZone = new StickView(this, true);
        leftZone.setListener(hudAxes::setMove);
        leftZone.setProbe(this);
        row.addView(leftZone, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));

        rightZone = new StickView(this, false);
        rightZone.setListener(hudAxes::setLook);
        rightZone.setProbe(this);
        row.addView(rightZone, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
        return row;
    }

    @Override
    public void onTouch(String zone, MotionEvent event, String whoZeroed) {
        if (debugHub == null) {
            return;
        }
        debugHub.onTouch(
                zone,
                event,
                whoZeroed,
                leftZone == null ? 0.0f : leftZone.axisX(),
                leftZone == null ? 0.0f : leftZone.axisY(),
                rightZone == null ? 0.0f : rightZone.axisX(),
                rightZone == null ? 0.0f : rightZone.axisY());
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

    private void zeroAllControls(String reason) {
        Log.i(TAG, "controls INPUT_ZERO reason=" + reason);
        StickView.TRACE.add("controls INPUT_ZERO reason=" + reason);
        if (debugHub != null) {
            debugHub.onZero("both", reason, -1, 0.0f, 0.0f);
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
        RadioButton dualButton = new RadioButton(this);
        dualButton.setId(View.generateViewId());
        dualButton.setText(R.string.dual_joysticks);
        dualButton.setTextColor(0xffffffff);
        dualButton.setTextSize(18.0f);
        RadioButton legacyButton = new RadioButton(this);
        legacyButton.setId(View.generateViewId());
        legacyButton.setText(R.string.legacy_touch);
        legacyButton.setTextColor(0xffffffff);
        legacyButton.setTextSize(18.0f);
        group.addView(dualButton);
        group.addView(legacyButton);
        group.check(dual ? dualButton.getId() : legacyButton.getId());
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            boolean dualOn = checkedId == dualButton.getId();
            prefs().edit().putString(
                    PREF_CONTROLS_SCHEME, dualOn ? SCHEME_DUAL : SCHEME_LEGACY).apply();
            applyScheme(dualOn, true);
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

    private void applyScheme(boolean dualOn, boolean notifyJump) {
        dual = dualOn;
        nativeSetControlScheme(dualOn ? 0 : 1);
        surface.setOnTouchListener(dualOn ? null : this);
        surface.setClickable(false);
        if (zonesRow != null) {
            zonesRow.setVisibility(dualOn ? View.VISIBLE : View.GONE);
        }
        if (jump != null) {
            jump.setVisibility(dualOn ? View.VISIBLE : View.GONE);
        }
        zeroAllControls("APPLY_SCHEME");
        if (notifyJump && hudAxes != null) {
            hudAxes.setJump(false);
        }
    }

    private void openMenu() {
        menuOpen = true;
        menuPanel.setVisibility(View.VISIBLE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.GONE);
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
        zeroAllControls("OPEN_MENU");
    }

    private void openSettings() {
        menuOpen = true;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.VISIBLE);
        debugPanel.setVisibility(View.GONE);
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
    }

    private void openDebug() {
        menuOpen = true;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.VISIBLE);
        nativeSetUiPaused(true);
        debugHub.setPaused(true);
        debugHub.lifecycle("openDebug");
    }

    private void closeOverlays() {
        menuOpen = false;
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        debugPanel.setVisibility(View.GONE);
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

    private String currentDump() {
        String scheme = dual ? SCHEME_DUAL : SCHEME_LEGACY;
        String version = "0.16.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return debugHub.buildDump(version, scheme, leftZone, rightZone, debugHub.jumpDown());
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
        nativeStop();
        nativeShutdown();
        super.onDestroy();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (menuOpen || dual) {
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
                nativeTouch(MotionEvent.ACTION_MOVE, event.getPointerId(i),
                        event.getX(i), event.getY(i));
            }
            return true;
        }
        int index = event.getActionIndex();
        nativeTouch(action, event.getPointerId(index),
                event.getX(index), event.getY(index));
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
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
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
        if (event.getRepeatCount() > 0) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            nativeKey(keyCode, true);
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            nativeKey(keyCode, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return onKey(surface, keyCode, event) || super.onKeyDown(keyCode, event);
        }
        nativeKey(keyCode, true);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return onKey(surface, keyCode, event) || super.onKeyUp(keyCode, event);
        }
        nativeKey(keyCode, false);
        return true;
    }
}
