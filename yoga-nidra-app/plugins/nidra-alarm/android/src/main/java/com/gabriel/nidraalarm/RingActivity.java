package com.gabriel.nidraalarm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

// Full-screen alarm shown over the lock screen while RingService is ringing.
public class RingActivity extends Activity {
    private static WeakReference<RingActivity> showing = new WeakReference<>(null);

    static void finishIfShowing() {
        RingActivity a = showing.get();
        if (a != null) a.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showing = new WeakReference<>(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String label = getIntent().getStringExtra(RingService.EXTRA_LABEL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0f1117"));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);

        TextView time = text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()), 56, "#ecebf3", true);
        TextView title = text("Time to wake up", 24, "#ecebf3", true);
        TextView sub = text(label != null ? label : "Yoga Nidra", 16, "#9a9bb0", false);
        root.addView(time);
        root.addView(title);
        root.addView(sub);

        Button awake = button("I'm awake", "#7e57c2");
        awake.setOnClickListener(v -> send(RingService.ACTION_STOP, label));
        Button snooze = button("Snooze 5 min", "#20242f");
        snooze.setOnClickListener(v -> send(RingService.ACTION_SNOOZE, label));
        root.addView(awake);
        root.addView(snooze);

        setContentView(root);
    }

    private void send(String action, String label) {
        startService(new android.content.Intent(this, RingService.class)
            .setAction(action).putExtra(RingService.EXTRA_LABEL, label));
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back shouldn't silently leave the alarm ringing behind a closed screen.
    }

    @Override
    protected void onDestroy() {
        if (showing.get() == this) showing.clear();
        super.onDestroy();
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private Button button(String s, String bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(Color.parseColor("#ecebf3"));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(bg));
        shape.setCornerRadius(dp(14));
        b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = dp(16);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
