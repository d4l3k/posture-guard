package com.d4l3k.postureguard;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class MainActivity extends Activity implements SensorEventListener {
    private final PostureDetector detector = new PostureDetector();
    private SensorManager sensors;
    private TextView angle, state, thresholdLabel, delayLabel, heldLabel;
    private SeekBar heldBar;
    private Button toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensors = getSystemService(SensorManager.class);
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        angle = new TextView(this);
        angle.setTextSize(72);
        angle.setTypeface(Typeface.DEFAULT_BOLD);
        angle.setGravity(Gravity.CENTER);
        root.addView(angle, match());

        state = new TextView(this);
        state.setTextSize(18);
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, 0, 0, pad);
        root.addView(state, match());

        TextView help = new TextView(this);
        help.setText("0° = phone flat (looking straight down), 90° = upright at eye level. "
                + "While the screen is on and the phone is in your hand, it buzzes if the "
                + "angle stays below the threshold.");
        help.setPadding(0, 0, 0, pad);
        root.addView(help, match());

        thresholdLabel = new TextView(this);
        root.addView(thresholdLabel, match());
        SeekBar threshold = new SeekBar(this);
        threshold.setMax(85 - 20);
        threshold.setProgress(Prefs.threshold(this) - 20);
        threshold.setOnSeekBarChangeListener(new Changed(v -> {
            Prefs.get(this).edit().putInt(Prefs.THRESHOLD, v + 20).apply();
            updateLabels();
        }));
        root.addView(threshold, match());

        delayLabel = new TextView(this);
        delayLabel.setPadding(0, pad, 0, 0);
        root.addView(delayLabel, match());
        SeekBar delay = new SeekBar(this);
        delay.setMax(30 - 1);
        delay.setProgress(Prefs.delay(this) - 1);
        delay.setOnSeekBarChangeListener(new Changed(v -> {
            Prefs.get(this).edit().putInt(Prefs.DELAY, v + 1).apply();
            updateLabels();
        }));
        root.addView(delay, match());

        heldLabel = new TextView(this);
        heldLabel.setPadding(0, pad, 0, 0);
        root.addView(heldLabel, match());
        heldBar = new SeekBar(this);
        heldBar.setMax(Prefs.MAX_HELD_MILLI - Prefs.MIN_HELD_MILLI);
        heldBar.setOnSeekBarChangeListener(new Changed(v -> setHeldMilli(v + Prefs.MIN_HELD_MILLI)));
        root.addView(heldBar, match());
        Button calibrate = new Button(this);
        calibrate.setText("Calibrate: phone is resting now");
        calibrate.setOnClickListener(v -> {
            // Twice the resting noise floor, so stands and tables never count as held.
            int milli = (int) Math.round(detector.jitter() * 2000);
            setHeldMilli(Math.max(milli, Prefs.MIN_HELD_MILLI));
        });
        root.addView(calibrate, match());
        TextView calHelp = new TextView(this);
        calHelp.setText("Put the phone on its stand or a table, wait a few seconds, then tap "
                + "Calibrate. Or drag the slider until the screen says Resting on the stand "
                + "and In hand when you pick it up.");
        root.addView(calHelp, match());

        toggle = new Button(this);
        LinearLayout.LayoutParams lp = match();
        lp.topMargin = pad;
        toggle.setOnClickListener(v -> setEnabled(!Prefs.enabled(this)));
        root.addView(toggle, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        updateLabels();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (Prefs.enabled(this)) PostureService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        detector.reset();
        Sensor accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel == null) {
            state.setText("This device has no accelerometer");
            return;
        }
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        updateToggle();
    }

    @Override
    protected void onPause() {
        sensors.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        detector.add(e.values[0], e.values[1], e.values[2], e.timestamp);
        if (!detector.ready()) return;
        double tilt = detector.tiltDegrees();
        boolean held = detector.held();
        boolean low = tilt < Prefs.threshold(this);
        angle.setText(String.format("%.0f°", tilt));
        angle.setTextColor(low ? Color.rgb(0xE5, 0x39, 0x35) : Color.rgb(0x43, 0xA0, 0x47));
        state.setText((held ? "In hand" : "Resting") + " · "
                + (low ? (held ? "Looking down" : "Low, but not held") : "Good angle")
                + String.format(" · motion %.3f", detector.jitter()));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void setEnabled(boolean on) {
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, on).apply();
        if (on) PostureService.start(this);
        else PostureService.stop(this);
        updateToggle();
    }

    private void updateToggle() {
        toggle.setText(Prefs.enabled(this) ? "Stop monitoring" : "Start monitoring");
    }

    private void setHeldMilli(int milli) {
        milli = Math.max(Prefs.MIN_HELD_MILLI, Math.min(Prefs.MAX_HELD_MILLI, milli));
        Prefs.get(this).edit().putInt(Prefs.HELD_MILLI, milli).apply();
        updateLabels();
    }

    private void updateLabels() {
        double held = Prefs.heldRms(this);
        detector.setHeldRms(held);
        heldBar.setProgress((int) Math.round(held * 1000) - Prefs.MIN_HELD_MILLI);
        heldLabel.setText(String.format("In-hand motion threshold: %.3f", held));
        thresholdLabel.setText("Warn below: " + Prefs.threshold(this) + "°");
        delayLabel.setText("After: " + Prefs.delay(this) + " s of looking down");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private interface IntConsumer {
        void accept(int v);
    }

    private static final class Changed implements SeekBar.OnSeekBarChangeListener {
        private final IntConsumer f;

        Changed(IntConsumer f) {
            this.f = f;
        }

        @Override
        public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
            if (fromUser) f.accept(v);
        }

        @Override
        public void onStartTrackingTouch(SeekBar s) {}

        @Override
        public void onStopTrackingTouch(SeekBar s) {}
    }
}
