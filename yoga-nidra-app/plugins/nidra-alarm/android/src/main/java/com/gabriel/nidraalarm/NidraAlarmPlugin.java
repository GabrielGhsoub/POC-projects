package com.gabriel.nidraalarm;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.AlarmClock;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

// Hands the alarm to the phone's Clock app, which rings reliably even when the
// screen is off and this app is in the background.
@CapacitorPlugin(name = "NidraAlarm")
public class NidraAlarmPlugin extends Plugin {

    @PluginMethod
    public void set(PluginCall call) {
        Integer hour = call.getInt("hour");
        Integer minute = call.getInt("minute");
        if (hour == null || minute == null) {
            call.reject("hour and minute are required");
            return;
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, call.getString("label", "Yoga Nidra"))
            .putExtra(AlarmClock.EXTRA_VIBRATE, true)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getActivity().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("No clock app found to set the alarm");
        }
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Intent intent = new Intent(AlarmClock.ACTION_DISMISS_ALARM)
            .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            .putExtra(AlarmClock.EXTRA_MESSAGE, call.getString("label", "Yoga Nidra"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getActivity().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("No clock app found to cancel the alarm");
        }
    }
}
