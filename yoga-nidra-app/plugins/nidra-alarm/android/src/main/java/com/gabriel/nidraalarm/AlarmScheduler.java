package com.gabriel.nidraalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

// Schedules the single pending wake-up alarm and remembers it, so the app can show
// it after a restart and BootReceiver can re-arm it after a reboot.
final class AlarmScheduler {
    private static final String PREFS = "nidra_alarm";
    private static final String KEY_AT = "at";
    private static final String KEY_LABEL = "label";
    private static final int REQ_FIRE = 1;
    private static final int REQ_SHOW = 2;

    private AlarmScheduler() {}

    private static SharedPreferences prefs(Context ctx) {
        // Device-protected storage is readable before first unlock (LOCKED_BOOT_COMPLETED).
        Context c = Build.VERSION.SDK_INT >= 24 ? ctx.createDeviceProtectedStorageContext() : ctx;
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void schedule(Context ctx, long at, String label) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        // setAlarmClock fires exactly, even in Doze, and shows the alarm icon in the status bar.
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(ctx)), fireIntent(ctx));
        prefs(ctx).edit().putLong(KEY_AT, at).putString(KEY_LABEL, label).apply();
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(fireIntent(ctx));
        clear(ctx);
    }

    static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    static long pendingAt(Context ctx) {
        return prefs(ctx).getLong(KEY_AT, 0);
    }

    static String label(Context ctx) {
        return prefs(ctx).getString(KEY_LABEL, "Yoga Nidra");
    }

    static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    private static PendingIntent fireIntent(Context ctx) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        return PendingIntent.getBroadcast(ctx, REQ_FIRE, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showIntent(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        return PendingIntent.getActivity(ctx, REQ_SHOW, launch,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
