package com.hs.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 재부팅/업데이트 후 알람 재예약. 이미 지난 경우 1초 뒤 울리게 한다. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int s = Timer.state(c);
        if (s == Timer.RUNNING || s == Timer.RINGING) {
            Timer.schedule(c, Math.max(Timer.endAt(c), System.currentTimeMillis() + 1000));
            Timer.updateNotification(c);
        } else if (s == Timer.PAUSED) {
            Timer.updateNotification(c);
        }
    }
}
