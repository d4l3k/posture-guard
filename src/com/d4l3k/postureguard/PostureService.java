package com.d4l3k.postureguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Foreground service that watches the accelerometer while the screen is on and
 * buzzes when the phone is held but tilted toward flat for too long.
 */
public final class PostureService extends Service implements SensorEventListener {
    private static final String CH_STATUS = "status";
    private static final String CH_WARN = "warn";
    private static final int ID_STATUS = 1;
    private static final int ID_WARN = 2;
    private static final long REPEAT_MS = 20_000;
    private static final long STATUS_UPDATE_MS = 3_000;
    static final String ACTION_STOP = "com.d4l3k.postureguard.STOP";

    private final PostureDetector detector = new PostureDetector();
    private SensorManager sensors;
    private Sensor accel;
    private NotificationManager nm;
    private Vibrator vibrator;
    private boolean listening;
    private long badSinceMs = -1;
    private long lastWarnMs = -1;
    private long lastStatusMs = -1;
    private int warnings;
    private int threshold;
    private int delaySec;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) stopListening();
            else startListening();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (p, key) -> loadPrefs();

    static void start(Context c) {
        c.startForegroundService(new Intent(c, PostureService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, PostureService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensors = getSystemService(SensorManager.class);
        accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        nm = getSystemService(NotificationManager.class);
        vibrator = getSystemService(Vibrator.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH_STATUS, "Monitoring", NotificationManager.IMPORTANCE_MIN));
        NotificationChannel warn = new NotificationChannel(
                CH_WARN, "Posture warnings", NotificationManager.IMPORTANCE_HIGH);
        warn.enableVibration(false); // vibrated directly so it works when muted too
        warn.setSound(null, null);
        nm.createNotificationChannel(warn);
        loadPrefs();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.get(this).edit().putBoolean(Prefs.ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(ID_STATUS, status("Watching your phone angle"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (getSystemService(PowerManager.class).isInteractive()) startListening();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListening();
        unregisterReceiver(screen);
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        nm.cancel(ID_WARN);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadPrefs() {
        threshold = Prefs.threshold(this);
        delaySec = Prefs.delay(this);
        detector.setHeldRms(Prefs.heldRms(this));
    }

    private void startListening() {
        if (listening || accel == null) return;
        detector.reset();
        badSinceMs = -1;
        sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        listening = true;
    }

    private void stopListening() {
        if (!listening) return;
        sensors.unregisterListener(this);
        listening = false;
        badSinceMs = -1;
        nm.cancel(ID_WARN);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        detector.add(e.values[0], e.values[1], e.values[2], e.timestamp);
        if (!detector.ready()) return;
        long now = SystemClock.elapsedRealtime();
        double tilt = detector.tiltDegrees();
        boolean bad = detector.held() && tilt < threshold;
        if (bad) {
            if (badSinceMs < 0) badSinceMs = now;
            boolean due = lastWarnMs < 0 || now - lastWarnMs >= REPEAT_MS;
            if (now - badSinceMs >= delaySec * 1000L && due) warn(tilt, now);
        } else if (badSinceMs >= 0) {
            badSinceMs = -1;
            lastWarnMs = -1;
            nm.cancel(ID_WARN);
        }
        if (now - lastStatusMs >= STATUS_UPDATE_MS) {
            lastStatusMs = now;
            String held = detector.held() ? "in hand" : "resting";
            nm.notify(ID_STATUS, status(String.format("%.0f° · %s · %d warnings today",
                    tilt, held, warnings)));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void warn(double tilt, long now) {
        lastWarnMs = now;
        warnings++;
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[] {0, 120, 90, 120}, -1));
        }
        Notification n = new Notification.Builder(this, CH_WARN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Chin up — raise your phone")
                .setContentText(String.format("Phone is at %.0f°; aim for %d° or more.", tilt, threshold))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setTimeoutAfter(REPEAT_MS)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        nm.notify(ID_WARN, n);
    }

    private Notification status(String text) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PostureService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Posture Guard")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
    }
}
