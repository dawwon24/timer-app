package com.hs.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 알림 버튼 처리. */
public class ActionReceiver extends BroadcastReceiver {
    static final String PAUSE = "com.hs.timer.PAUSE";
    static final String RESUME = "com.hs.timer.RESUME";
    static final String CANCEL = "com.hs.timer.CANCEL";
    static final String DISMISS = "com.hs.timer.DISMISS";
    static final String RESTART_1M = "com.hs.timer.RESTART_1M";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        switch (a) {
            case PAUSE: Timer.pause(c); break;
            case RESUME: Timer.resume(c); break;
            case CANCEL: Timer.cancel(c); break;
            case DISMISS: Timer.dismiss(c); break;
            case RESTART_1M:
                Timer.dismiss(c);
                Timer.start(c, 60_000L);
                break;
        }
    }
}
