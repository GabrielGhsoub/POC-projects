package com.gabriel.nidraalarm;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebView;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

// The app's own wake-up alarm: exact, rings on the alarm stream with a full-screen
// alarm, and can be set and cancelled silently.
//
// JS: Capacitor.nativePromise("NidraAlarm", <method>, <options>)
//   set({ at: epochMillis, label })  schedule (replaces any pending alarm)
//   keepPlaying({ title, ms })       keep the session playing with the screen off
//   stopPlaying()                    stop doing that
//   cancel()                         cancel the pending alarm, or stop it ringing
//   status()                         { at } of the pending alarm, 0 if none
//   checkSetup()                     which alarm permissions are granted
//   requestSetup({ item })           ask for one of them (see checkSetup keys)
@CapacitorPlugin(
    name = "NidraAlarm",
    permissions = @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
)
public class NidraAlarmPlugin extends Plugin {

    @PluginMethod
    public void set(PluginCall call) {
        Long at = call.getLong("at");
        if (at == null || at <= System.currentTimeMillis()) {
            call.reject("at must be a future time in milliseconds");
            return;
        }
        Context ctx = getContext();
        RingService.ensureChannel(ctx);
        allowAlarmsInDnd(ctx);
        AlarmScheduler.schedule(ctx, at, call.getString("label", "Yoga Nidra"));
        JSObject r = new JSObject();
        r.put("at", AlarmScheduler.pendingAt(ctx));
        r.put("exact", AlarmScheduler.canScheduleExact(ctx));
        call.resolve(r);
    }

    @PluginMethod
    public void keepPlaying(PluginCall call) {
        try {
            PlaybackService.start(getContext(), call.getString("title", "Yoga Nidra"), call.getLong("ms", 0L));
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't keep playing in the background: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopPlaying(PluginCall call) {
        PlaybackService.stop(getContext());
        call.resolve();
    }

    // Android WebView pauses video once its window is hidden (screen off, app in the
    // background). While a session plays, tell it the window is still visible so the
    // audio carries on; the system's hide notice arrives a moment after onStop.
    @Override
    protected void handleOnStop() {
        if (!PlaybackService.running) return;
        WebView wv = getBridge().getWebView();
        for (long delay : new long[] { 0, 300, 1000, 3000 }) {
            wv.postDelayed(() -> {
                if (!PlaybackService.running) return;
                wv.dispatchWindowVisibilityChanged(View.VISIBLE);
                wv.onResume();
                wv.resumeTimers();
            }, delay);
        }
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Context ctx = getContext();
        AlarmScheduler.cancel(ctx);
        ctx.stopService(new Intent(ctx, RingService.class));
        call.resolve();
    }

    @PluginMethod
    public void status(PluginCall call) {
        long at = AlarmScheduler.pendingAt(getContext());
        JSObject r = new JSObject();
        r.put("at", at > System.currentTimeMillis() ? at : 0);
        call.resolve(r);
    }

    @PluginMethod
    public void checkSetup(PluginCall call) {
        call.resolve(setupState());
    }

    @PluginMethod
    public void requestSetup(PluginCall call) {
        String item = call.getString("item", "");
        Context ctx = getContext();
        String pkg = ctx.getPackageName();
        Intent intent = null;
        switch (item) {
            case "notifications":
                if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
                    requestPermissionForAlias("notifications", call, "notificationsResult");
                    return;
                }
                break;
            case "exactAlarm":
                if (Build.VERSION.SDK_INT >= 31) {
                    intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg));
                }
                break;
            case "fullScreen":
                if (Build.VERSION.SDK_INT >= 34) {
                    intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkg));
                }
                break;
            case "battery":
                intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
                break;
            case "dnd":
                intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                break;
            default:
                call.reject("Unknown setup item: " + item);
                return;
        }
        if (intent != null) {
            try {
                getActivity().startActivity(intent);
            } catch (Exception e) {
                call.reject("Couldn't open the settings screen for " + item);
                return;
            }
        }
        call.resolve(setupState());
    }

    @PermissionCallback
    private void notificationsResult(PluginCall call) {
        call.resolve(setupState());
    }

    private JSObject setupState() {
        Context ctx = getContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        JSObject r = new JSObject();
        r.put("notifications", Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED);
        r.put("exactAlarm", AlarmScheduler.canScheduleExact(ctx));
        r.put("fullScreen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent());
        r.put("battery", Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(ctx.getPackageName()));
        r.put("dnd", Build.VERSION.SDK_INT < 23 || nm.isNotificationPolicyAccessGranted());
        return r;
    }

    // With Do Not Disturb access, make sure DND lets alarms through so the alarm rings.
    private void allowAlarmsInDnd(Context ctx) {
        if (Build.VERSION.SDK_INT < 28) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) return;
        NotificationManager.Policy p = nm.getNotificationPolicy();
        if ((p.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0) return;
        int cats = p.priorityCategories | NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                nm.setNotificationPolicy(new NotificationManager.Policy(cats, p.priorityCallSenders,
                    p.priorityMessageSenders, p.suppressedVisualEffects, p.priorityConversationSenders));
            } else {
                nm.setNotificationPolicy(new NotificationManager.Policy(cats, p.priorityCallSenders,
                    p.priorityMessageSenders, p.suppressedVisualEffects));
            }
        } catch (SecurityException ignored) {
        }
    }
}
