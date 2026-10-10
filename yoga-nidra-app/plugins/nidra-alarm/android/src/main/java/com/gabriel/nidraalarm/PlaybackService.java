package com.gabriel.nidraalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

// Keeps the app running while a session plays with the screen off or the app in the
// background: a media foreground service so Android doesn't freeze or kill the
// process, plus wake and Wi-Fi locks so the stream keeps loading. Stops itself once
// the session should be over.
public class PlaybackService extends Service {
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_MS = "ms";
    private static final String CHANNEL_ID = "nidra_playback";
    private static final int NOTIFICATION_ID = 4343;
    private static final long DEFAULT_MS = 2 * 60 * 60 * 1000;
    private static final long GRACE_MS = 5 * 60 * 1000;

    static volatile boolean running;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context ctx, String title, long ms) {
        Intent i = new Intent(ctx, PlaybackService.class).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_MS, ms);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, PlaybackService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        long ms = intent != null ? intent.getLongExtra(EXTRA_MS, 0) : 0;
        if (ms <= 0) ms = DEFAULT_MS;

        ensureChannel();
        Notification n = buildNotification(title != null ? title : "Yoga Nidra");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        running = true;

        releaseLocks();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nidra:playback");
        wakeLock.acquire(ms + GRACE_MS);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nidra:playback");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, ms + GRACE_MS);
        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Playing session", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shown while a session keeps playing with the screen off.");
        ch.setSound(null, null);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String title) {
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent openPi = open == null ? null : PendingIntent.getActivity(this, 20, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Playing")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .build();
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        wakeLock = null;
        wifiLock = null;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
