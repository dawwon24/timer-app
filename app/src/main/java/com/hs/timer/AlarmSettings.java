package com.hs.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** 알람 소리·진동 설정. */
public final class AlarmSettings {
    public static final String[] PATTERN_NAMES = {"기본", "짧게 반복", "길게", "심장박동"};
    private static final long[][] PATTERNS = {
            {0, 800, 600},
            {0, 250, 250},
            {0, 1500, 500},
            {0, 150, 120, 150, 900},
    };
    public static final String[] SILENCE_NAMES = {"1분", "5분", "10분", "30분", "끄지 않음"};
    private static final int[] SILENCE_MIN = {1, 5, 10, 30, 0};

    private static final String PREFS = "settings";

    private AlarmSettings() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean sound(Context c) { return p(c).getBoolean("sound", true); }
    public static void setSound(Context c, boolean v) { p(c).edit().putBoolean("sound", v).apply(); }

    /** 0~100 (%) */
    public static int volume(Context c) { return p(c).getInt("volume", 70); }
    public static void setVolume(Context c, int v) { p(c).edit().putInt("volume", v).apply(); }

    public static boolean vibrate(Context c) { return p(c).getBoolean("vibrate", true); }
    public static void setVibrate(Context c, boolean v) { p(c).edit().putBoolean("vibrate", v).apply(); }

    public static int pattern(Context c) {
        return Math.min(PATTERNS.length - 1, Math.max(0, p(c).getInt("pattern", 0)));
    }
    public static void setPattern(Context c, int v) { p(c).edit().putInt("pattern", v).apply(); }

    public static int silenceIndex(Context c) {
        return Math.min(SILENCE_MIN.length - 1, Math.max(0, p(c).getInt("silence", 2)));
    }
    public static void setSilenceIndex(Context c, int v) { p(c).edit().putInt("silence", v).apply(); }

    /** 자동으로 소리를 끌 시간(ms). 0 = 끄지 않음. */
    public static long silenceMs(Context c) { return SILENCE_MIN[silenceIndex(c)] * 60_000L; }

    /** 선택한 알람음. 없으면 기기 기본 알람음. */
    public static Uri ringtone(Context c) {
        String s = p(c).getString("ringtone", null);
        if (s != null) return Uri.parse(s);
        Uri u = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return u;
    }
    public static void setRingtone(Context c, Uri u) {
        p(c).edit().putString("ringtone", u == null ? null : u.toString()).apply();
    }

    public static String ringtoneTitle(Context c) {
        try {
            Ringtone r = RingtoneManager.getRingtone(c, ringtone(c));
            if (r != null) return r.getTitle(c);
        } catch (Exception ignored) {}
        return "기본 알람음";
    }

    // ---------- 재생 도우미 ----------

    /** 알람 스트림 음량을 설정값으로 바꾸고, 원래 음량을 반환한다. */
    static int applyVolume(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int orig = am.getStreamVolume(AudioManager.STREAM_ALARM);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        int vol = volume(c);
        int target = vol <= 0 ? 0 : Math.max(1, Math.round(max * vol / 100f));
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
        } catch (SecurityException ignored) {}
        return orig;
    }

    static void restoreVolume(Context c, int orig) {
        try {
            c.getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_ALARM, orig, 0);
        } catch (SecurityException ignored) {}
    }

    static AudioAttributes alarmAudio() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    static Vibrator vibrator(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            return c.getSystemService(VibratorManager.class).getDefaultVibrator();
        }
        return c.getSystemService(Vibrator.class);
    }

    /** 진동 시작. repeat=false면 패턴 1회만(미리보기). */
    static void vibrate(Context c, Vibrator v, int pattern, boolean repeat) {
        if (v == null || !v.hasVibrator()) return;
        VibrationEffect effect = VibrationEffect.createWaveform(PATTERNS[pattern], repeat ? 1 : -1);
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } else {
            v.vibrate(effect, alarmAudio());
        }
    }
}
