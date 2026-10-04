package com.gabriel.nidraalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Alarms are wiped on reboot; re-arm one that is still in the future.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long at = AlarmScheduler.pendingAt(ctx);
        if (at == 0) return;
        if (at > System.currentTimeMillis()) {
            AlarmScheduler.schedule(ctx, at, AlarmScheduler.label(ctx));
        } else {
            AlarmScheduler.clear(ctx);
        }
    }
}
