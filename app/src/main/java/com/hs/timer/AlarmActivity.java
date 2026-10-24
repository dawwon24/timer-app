package com.hs.timer;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

/** 타이머 종료 화면 (잠금화면 위에도 표시). */
public class AlarmActivity extends Activity {
    private TextView elapsed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ObjectAnimator pulse;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (Timer.state(AlarmActivity.this) != Timer.RINGING) {
                finish();
                return;
            }
            long sec = Math.max(0, System.currentTimeMillis() - Timer.endAt(AlarmActivity.this)) / 1000;
            elapsed.setText("+" + Timer.formatSec(sec));
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);

        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });

        elapsed = findViewById(R.id.elapsed);
        ((TextView) findViewById(R.id.setTime)).setText("설정 " + Timer.format(Timer.total(this)));
        RingView ring = findViewById(R.id.ring);
        ring.setFraction(1f);
        ring.setArcColor(getColor(R.color.alert));

        View p = findViewById(R.id.pulse);
        pulse = ObjectAnimator.ofPropertyValuesHolder(p,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.3f));
        pulse.setDuration(1000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);

        findViewById(R.id.dismiss).setOnClickListener(v -> {
            Timer.dismiss(this);
            close();
        });
        findViewById(R.id.restart).setOnClickListener(v -> {
            Timer.dismiss(this);
            Timer.start(this, 60_000L);
            close();
        });
    }

    private void close() {
        // 잠금 상태가 아니면 메인 화면으로, 잠금 상태면 그냥 닫기
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (!km.isKeyguardLocked()) {
            startActivity(new android.content.Intent(this, MainActivity.class));
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        pulse.start();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        pulse.cancel();
        super.onPause();
    }
}
