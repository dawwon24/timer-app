package com.hs.timer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Locale;

/** 타이머 상태(SharedPreferences 저장), 알람 예약, 진행 알림을 관리한다. */
public final class Timer {
    public static final int IDLE = 0, RUNNING = 1, PAUSED = 2, RINGING = 3;
    public static final long MAX_MS = (99 * 3600L + 59 * 60L + 59) * 1000L;

    static final String CH_TIMER = "timer_progress";
    static final String CH_ALARM = "timer_alarm";
    static final int NOTIF_TIMER = 1;
    static final int NOTIF_RING = 2;

    private static final String PREFS = "timer";
    private static final String K_STATE = "state", K_END = "end", K_REMAIN = "remain",
            K_TOTAL = "total", K_LAST = "last";

    private Timer() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int state(Context c) { return p(c).getInt(K_STATE, IDLE); }
    public static long endAt(Context c) { return p(c).getLong(K_END, 0); }
    public static long total(Context c) { return p(c).getLong(K_TOTAL, 0); }
    public static long lastSet(Context c) { return p(c).getLong(K_LAST, 5 * 60_000L); }

    public static void setLastSet(Context c, long ms) {
        p(c).edit().putLong(K_LAST, ms).apply();
    }

    /** 남은 시간(ms). 실행 중이면 종료 시각 기준으로 계산. */
    public static long remaining(Context c) {
        switch (state(c)) {
            case RUNNING: return Math.max(0, endAt(c) - System.currentTimeMillis());
            case PAUSED: return p(c).getLong(K_REMAIN, 0);
            default: return 0;
        }
    }

    public static void start(Context c, long ms) {
        if (ms <= 0) return;
        ms = Math.min(ms, MAX_MS);
        long end = System.currentTimeMillis() + ms;
        p(c).edit().putInt(K_STATE, RUNNING).putLong(K_END, end).putLong(K_TOTAL, ms).commit();
        schedule(c, end);
        updateNotification(c);
    }

    public static void pause(Context c) {
        if (state(c) != RUNNING) return;
        long rem = remaining(c);
        p(c).edit().putInt(K_STATE, PAUSED).putLong(K_REMAIN, rem).commit();
        cancelAlarm(c);
        updateNotification(c);
    }

    public static void resume(Context c) {
        if (state(c) != PAUSED) return;
        long end = System.currentTimeMillis() + p(c).getLong(K_REMAIN, 0);
        p(c).edit().putInt(K_STATE, RUNNING).putLong(K_END, end).commit();
        schedule(c, end);
        updateNotification(c);
    }

    public static void cancel(Context c) {
        p(c).edit().putInt(K_STATE, IDLE).commit();
        cancelAlarm(c);
        nm(c).cancel(NOTIF_TIMER);
    }

    /** 실행/일시정지 중인 타이머에 시간을 더한다. */
    public static void add(Context c, long ms) {
        int s = state(c);
        SharedPreferences.Editor e = p(c).edit();
        long total = total(c);
        if (s == RUNNING) {
            long rem = remaining(c);
            long add = Math.min(ms, MAX_MS - rem);
            long end = endAt(c) + add;
            e.putLong(K_END, end).putLong(K_TOTAL, Math.min(MAX_MS, Math.max(total, rem) + add)).commit();
            schedule(c, end);
        } else if (s == PAUSED) {
            long rem = p(c).getLong(K_REMAIN, 0);
            long add = Math.min(ms, MAX_MS - rem);
            e.putLong(K_REMAIN, rem + add).putLong(K_TOTAL, Math.min(MAX_MS, Math.max(total, rem) + add)).commit();
        } else {
            return;
        }
        updateNotification(c);
    }

    /** 알람 시각 도달 → 울림 상태로 전환하고 울림 서비스 시작. */
    public static void onFinished(Context c) {
        int s = state(c);
        if (s != RUNNING && s != RINGING) return;
        p(c).edit().putInt(K_STATE, RINGING).commit();
        nm(c).cancel(NOTIF_TIMER);
        c.startForegroundService(new Intent(c, RingService.class));
    }

    /** 울림 해제. */
    public static void dismiss(Context c) {
        p(c).edit().putInt(K_STATE, IDLE).commit();
        c.stopService(new Intent(c, RingService.class));
        nm(c).cancel(NOTIF_RING);
        nm(c).cancel(NOTIF_TIMER);
    }

    // ---------- 알람 ----------

    static void schedule(Context c, long end) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent op = alarmIntent(c);
        PendingIntent show = PendingIntent.getActivity(c, 10,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(end, show), op);
        } catch (SecurityException ex) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, op);
        }
    }

    static void cancelAlarm(Context c) {
        c.getSystemService(AlarmManager.class).cancel(alarmIntent(c));
    }

    private static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------- 알림 ----------

    static NotificationManager nm(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_TIMER) == null) {
            NotificationChannel t = new NotificationChannel(CH_TIMER, "타이머 진행", NotificationManager.IMPORTANCE_LOW);
            t.setShowBadge(false);
            nm.createNotificationChannel(t);
        }
        if (nm.getNotificationChannel(CH_ALARM) == null) {
            NotificationChannel a = new NotificationChannel(CH_ALARM, "타이머 종료 알람", NotificationManager.IMPORTANCE_HIGH);
            a.setSound(null, null);          // 소리·진동은 RingService가 직접 재생
            a.enableVibration(false);
            a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            a.setBypassDnd(true);
            nm.createNotificationChannel(a);
        }
        return nm;
    }

    static PendingIntent action(Context c, String action, int req) {
        Intent i = new Intent(c, ActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateNotification(Context c) {
        int s = state(c);
        NotificationManager nm = nm(c);
        if (s != RUNNING && s != PAUSED) {
            nm.cancel(NOTIF_TIMER);
            return;
        }
        PendingIntent open = PendingIntent.getActivity(c, 11,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("타이머")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (s == RUNNING) {
            b.setShowWhen(true).setWhen(endAt(c))
                    .setUsesChronometer(true).setChronometerCountDown(true)
                    .setContentText("실행 중")
                    .addAction(new Notification.Action.Builder(null, "일시정지", action(c, ActionReceiver.PAUSE, 1)).build());
        } else {
            b.setShowWhen(false)
                    .setContentText("일시정지됨 · " + format(remaining(c)))
                    .addAction(new Notification.Action.Builder(null, "계속", action(c, ActionReceiver.RESUME, 2)).build());
        }
        b.addAction(new Notification.Action.Builder(null, "취소", action(c, ActionReceiver.CANCEL, 3)).build());
        try {
            nm.notify(NOTIF_TIMER, b.build());
        } catch (SecurityException ignored) {
            // 알림 권한 없음
        }
    }

    // ---------- 형식 ----------

    /** ms → "HH:MM:SS" (초 단위 올림) */
    public static String format(long ms) {
        long sec = (ms + 999) / 1000;
        return formatSec(sec);
    }

    public static String formatSec(long sec) {
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
    }
}
