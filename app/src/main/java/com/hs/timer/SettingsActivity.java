package com.hs.timer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowInsets;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 알람 소리·진동 설정 화면. */
public class SettingsActivity extends Activity {
    private static final int REQ_RINGTONE = 1;
    private static final long PREVIEW_MS = 2500;

    private Switch swSound, swVibrate;
    private TextView ringtoneName, volumeText, patternName, silenceName;
    private View rowRingtone, rowVolume, rowPattern;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer preview;
    private int previewOrigVolume = -1;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
        findViewById(R.id.back).setOnClickListener(v -> finish());

        swSound = findViewById(R.id.swSound);
        swVibrate = findViewById(R.id.swVibrate);
        ringtoneName = findViewById(R.id.ringtoneName);
        volumeText = findViewById(R.id.volumeText);
        patternName = findViewById(R.id.patternName);
        silenceName = findViewById(R.id.silenceName);
        rowRingtone = findViewById(R.id.rowRingtone);
        rowVolume = findViewById(R.id.rowVolume);
        rowPattern = findViewById(R.id.rowPattern);
        vibrator = AlarmSettings.vibrator(this);

        findViewById(R.id.rowSound).setOnClickListener(v -> {
            AlarmSettings.setSound(this, !AlarmSettings.sound(this));
            if (!AlarmSettings.sound(this)) stopPreview();
            refresh();
        });
        findViewById(R.id.rowVibrate).setOnClickListener(v -> {
            AlarmSettings.setVibrate(this, !AlarmSettings.vibrate(this));
            refresh();
        });

        rowRingtone.setOnClickListener(v -> {
            stopPreview();
            Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음")
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AlarmSettings.ringtone(this));
            try {
                startActivityForResult(i, REQ_RINGTONE);
            } catch (Exception ignored) {}
        });

        SeekBar volume = findViewById(R.id.volume);
        volume.setProgress(AlarmSettings.volume(this));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                volumeText.setText(progress + "%");
                if (fromUser) AlarmSettings.setVolume(SettingsActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { stopPreview(); }
            @Override public void onStopTrackingTouch(SeekBar s) { startPreview(); }
        });

        rowPattern.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("진동 패턴")
                .setSingleChoiceItems(AlarmSettings.PATTERN_NAMES, AlarmSettings.pattern(this), (d, which) -> {
                    AlarmSettings.setPattern(this, which);
                    AlarmSettings.vibrate(this, vibrator, which, false);
                    refresh();
                })
                .setPositiveButton("확인", null)
                .show());

        findViewById(R.id.rowSilence).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("자동으로 알람 끄기")
                .setSingleChoiceItems(AlarmSettings.SILENCE_NAMES, AlarmSettings.silenceIndex(this), (d, which) -> {
                    AlarmSettings.setSilenceIndex(this, which);
                    refresh();
                    d.dismiss();
                })
                .show());

        refresh();
    }

    private void refresh() {
        boolean sound = AlarmSettings.sound(this);
        boolean vib = AlarmSettings.vibrate(this);
        swSound.setChecked(sound);
        swVibrate.setChecked(vib);
        ringtoneName.setText(AlarmSettings.ringtoneTitle(this));
        volumeText.setText(AlarmSettings.volume(this) + "%");
        patternName.setText(AlarmSettings.PATTERN_NAMES[AlarmSettings.pattern(this)]);
        silenceName.setText(AlarmSettings.SILENCE_NAMES[AlarmSettings.silenceIndex(this)]);
        setRowEnabled(rowRingtone, sound);
        setRowEnabled(rowVolume, sound);
        setRowEnabled(rowPattern, vib);
    }

    private void setRowEnabled(View row, boolean enabled) {
        row.setAlpha(enabled ? 1f : 0.4f);
        row.setEnabled(enabled);
        if (row == rowVolume) findViewById(R.id.volume).setEnabled(enabled);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RINGTONE && resultCode == RESULT_OK && data != null) {
            @SuppressWarnings("deprecation")
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            // "기본" 선택 시 기기 기본 알람음을 따르도록 저장하지 않음
            if (uri != null && RingtoneManager.isDefault(uri)) uri = null;
            AlarmSettings.setRingtone(this, uri);
            refresh();
        }
    }

    // ---------- 음량 미리듣기 ----------

    private void startPreview() {
        stopPreview();
        if (!AlarmSettings.sound(this) || Timer.state(this) == Timer.RINGING) return;
        previewOrigVolume = AlarmSettings.applyVolume(this);
        try {
            preview = new MediaPlayer();
            preview.setAudioAttributes(AlarmSettings.alarmAudio());
            preview.setDataSource(this, AlarmSettings.ringtone(this));
            preview.setLooping(true);
            preview.prepare();
            preview.start();
        } catch (Exception e) {
            if (preview != null) preview.release();
            preview = null;
        }
        handler.postDelayed(this::stopPreview, PREVIEW_MS);
    }

    private void stopPreview() {
        handler.removeCallbacksAndMessages(null);
        if (preview != null) {
            try { preview.stop(); } catch (Exception ignored) {}
            preview.release();
            preview = null;
        }
        if (previewOrigVolume >= 0) {
            AlarmSettings.restoreVolume(this, previewOrigVolume);
            previewOrigVolume = -1;
        }
    }

    @Override
    protected void onPause() {
        stopPreview();
        if (vibrator != null && Timer.state(this) != Timer.RINGING) vibrator.cancel();
        super.onPause();
    }
}
