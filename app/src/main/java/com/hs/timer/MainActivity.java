package com.hs.timer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private NumberPicker pickH, pickM, pickS;
    private View idleGroup, runGroup, reset;
    private RingView ring;
    private TextView timeText, subText, btnLeft, btnRight;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int shownState = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });

        idleGroup = findViewById(R.id.idleGroup);
        runGroup = findViewById(R.id.runGroup);
        reset = findViewById(R.id.reset);
        ring = findViewById(R.id.ring);
        timeText = findViewById(R.id.timeText);
        subText = findViewById(R.id.subText);
        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);

        pickH = findViewById(R.id.pickH);
        pickM = findViewById(R.id.pickM);
        pickS = findViewById(R.id.pickS);
        setupPicker(pickH, 99);
        setupPicker(pickM, 59);
        setupPicker(pickS, 59);
        setPickers(Timer.lastSet(this));

        NumberPicker.OnValueChangeListener changed = (p, o, n) -> updateIdleButtons();
        pickH.setOnValueChangedListener(changed);
        pickM.setOnValueChangedListener(changed);
        pickS.setOnValueChangedListener(changed);

        findViewById(R.id.add1m).setOnClickListener(v -> addTime(60_000L));
        findViewById(R.id.add10m).setOnClickListener(v -> addTime(600_000L));
        findViewById(R.id.add15s).setOnClickListener(v -> addTime(15_000L));
        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        reset.setOnClickListener(v -> { setPickers(0); updateIdleButtons(); });

        btnRight.setOnClickListener(v -> {
            switch (Timer.state(this)) {
                case Timer.IDLE:
                    long ms = pickerMs();
                    if (ms <= 0) return;
                    Timer.setLastSet(this, ms);
                    Timer.start(this, ms);
                    ensurePermissions();
                    break;
                case Timer.RUNNING: Timer.pause(this); break;
                case Timer.PAUSED: Timer.resume(this); break;
            }
            render();
        });
        btnLeft.setOnClickListener(v -> {
            Timer.cancel(this);
            setPickers(Timer.lastSet(this));
            render();
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void setupPicker(NumberPicker p, int max) {
        String[] labels = new String[max + 1];
        for (int i = 0; i <= max; i++) labels[i] = String.format("%02d", i);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setDisplayedValues(labels);
        p.setWrapSelectorWheel(true);
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 34,
                    getResources().getDisplayMetrics()));
            p.setTextColor(getColor(R.color.text));
        }
    }

    private long pickerMs() {
        return (pickH.getValue() * 3600L + pickM.getValue() * 60L + pickS.getValue()) * 1000L;
    }

    private void setPickers(long ms) {
        long sec = Math.min(ms, Timer.MAX_MS) / 1000;
        pickH.setValue((int) (sec / 3600));
        pickM.setValue((int) ((sec / 60) % 60));
        pickS.setValue((int) (sec % 60));
    }

    private void addTime(long ms) {
        if (Timer.state(this) == Timer.IDLE) {
            setPickers(Math.min(Timer.MAX_MS, pickerMs() + ms));
            updateIdleButtons();
        } else {
            Timer.add(this, ms);
            render();
        }
    }

    private void updateIdleButtons() {
        boolean has = pickerMs() > 0;
        btnRight.setEnabled(has);
        btnRight.setAlpha(has ? 1f : 0.4f);
        reset.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        shownState = -1;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void render() {
        int s = Timer.state(this);
        if (s == Timer.RINGING) {
            if (shownState != Timer.RINGING) {
                shownState = Timer.RINGING;
                startActivity(new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return;
        }
        if (s != shownState) {
            if (shownState == Timer.RINGING || (s == Timer.IDLE && shownState != -1)) {
                setPickers(Timer.lastSet(this));
            }
            shownState = s;
            boolean idle = s == Timer.IDLE;
            idleGroup.setVisibility(idle ? View.VISIBLE : View.GONE);
            runGroup.setVisibility(idle ? View.GONE : View.VISIBLE);
            btnLeft.setVisibility(idle ? View.GONE : View.VISIBLE);
            if (idle) {
                btnRight.setText("시작");
                updateIdleButtons();
            } else {
                reset.setVisibility(View.INVISIBLE);
                btnRight.setEnabled(true);
                btnRight.setAlpha(1f);
                btnRight.setText(s == Timer.RUNNING ? "일시정지" : "계속");
            }
        }
        if (s == Timer.IDLE) return;

        long rem = Timer.remaining(this);
        long total = Math.max(1, Timer.total(this));
        ring.setFraction(rem / (float) total);
        timeText.setText(Timer.format(rem));
        if (s == Timer.RUNNING) {
            timeText.setAlpha(1f);
            subText.setText("종료 " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(Timer.endAt(this))));
        } else {
            // 일시정지 중에는 시간 깜빡임
            timeText.setAlpha((System.currentTimeMillis() / 600) % 2 == 0 ? 1f : 0.25f);
            subText.setText("일시정지됨");
        }
    }

    // ---------- 백그라운드 동작을 위한 권한 안내 (최초 1회) ----------

    private void ensurePermissions() {
        SharedPreferences sp = getSharedPreferences("setup", MODE_PRIVATE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() && !sp.getBoolean("fsi", false)) {
            sp.edit().putBoolean("fsi", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("전체 화면 알림 허용")
                    .setMessage("화면이 꺼져 있거나 잠겨 있을 때 타이머 종료 화면을 띄우려면 '전체 화면 알림'을 허용해 주세요.")
                    .setPositiveButton("설정", (d, w) -> startActivity(new Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("나중에", null)
                    .show();
            return;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName()) && !sp.getBoolean("battery", false)) {
            sp.edit().putBoolean("battery", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("배터리 최적화 제외")
                    .setMessage("다른 앱을 쓰거나 화면이 꺼져 있어도 정확히 울리도록 배터리 최적화에서 이 앱을 제외해 주세요.")
                    .setPositiveButton("허용", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("나중에", null)
                    .show();
        }
    }
}
