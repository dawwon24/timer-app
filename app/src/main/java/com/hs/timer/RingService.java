package com.hs.timer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;

/** 타이머 종료 시 알람음 + 진동을 울리는 포그라운드 서비스. */
public class RingService extends Service {
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private int origVolume = -1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(Timer.NOTIF_RING, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (Timer.state(this) != Timer.RINGING) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            showAlarmScreen();
            long silenceMs = AlarmSettings.silenceMs(this);
            wakeLock = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timer:ring");
            wakeLock.acquire((silenceMs > 0 ? silenceMs : 60 * 60_000L) + 5000);
            if (AlarmSettings.sound(this)) startSound();
            if (AlarmSettings.vibrate(this)) {
                vibrator = AlarmSettings.vibrator(this);
                AlarmSettings.vibrate(this, vibrator, AlarmSettings.pattern(this), true);
            }
            if (silenceMs > 0) handler.postDelayed(this::silence, silenceMs);
        }
        return START_STICKY;
    }

    /** '다른 앱 위에 표시' 권한이 있으면 종료 화면을 직접 띄운다.
     *  전체 화면 알림만으로는 폰 사용 중일 때 작은 팝업으로만 표시되기 때문. */
    private void showAlarmScreen() {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            startActivity(new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // 실패하면 전체 화면 알림에 맡김
        }
    }

    private Notification buildNotification() {
        Intent full = new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent fullPi = PendingIntent.getActivity(this, 20, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Timer.nm(this);
        return new Notification.Builder(this, Timer.CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("타이머 종료")
                .setContentText("설정 시간 " + Timer.format(Timer.total(this)))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(Timer.endAt(this))
                .setUsesChronometer(true)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .addAction(new Notification.Action.Builder(null, "해제",
                        Timer.action(this, ActionReceiver.DISMISS, 4)).build())
                .addAction(new Notification.Action.Builder(null, "+1분",
                        Timer.action(this, ActionReceiver.RESTART_1M, 5)).build())
                .build();
    }

    private void startSound() {
        origVolume = AlarmSettings.applyVolume(this);
        if (!play(AlarmSettings.ringtone(this))
                && !play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) {
            play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }
    }

    private boolean play(Uri uri) {
        if (uri == null) return false;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(AlarmSettings.alarmAudio());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
            return true;
        } catch (Exception e) {
            releasePlayer();
            return false;
        }
    }

    private void silence() {
        releasePlayer();
        if (vibrator != null) vibrator.cancel();
        if (origVolume >= 0) {
            AlarmSettings.restoreVolume(this, origVolume);
            origVolume = -1;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        silence();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
