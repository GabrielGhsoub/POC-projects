package com.gabriel.nidraalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

// Fired by AlarmManager at the wake-up time; hands off to the ringing service.
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent ring = new Intent(ctx, RingService.class)
            .putExtra(RingService.EXTRA_LABEL, AlarmScheduler.label(ctx));
        AlarmScheduler.clear(ctx);
        ContextCompat.startForegroundService(ctx, ring);
    }
}
