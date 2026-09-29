package com.fm1.midibridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Keeps the bridge running while other apps are in front or the screen is
 * off: a foreground service (with its "MIDI Bridge is running" notification)
 * so Android doesn't kill the app, plus a partial wake lock so the CPU keeps
 * forwarding notes with the screen off. Started/stopped by Bridge.setOn().
 */
public class BridgeService extends Service {

    static final String ACTION_STOP = "com.fm1.midibridge.STOP";
    private static final String CHANNEL = "bridge";
    private static final int NOTIFICATION_ID = 1;

    private Bridge bridge;
    private PowerManager.WakeLock wakeLock;
    private String shownText;
    private final Runnable onChange = this::updateNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        bridge = Bridge.get(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "MIDI Bridge running", NotificationManager.IMPORTANCE_LOW));
        goForeground(buildNotification());
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MidiBridge:forwarding");
        wakeLock.acquire();
        bridge.addListener(onChange);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            bridge.setOn(false);  // stops this service too
            return START_NOT_STICKY;
        }
        // Restarted by Android after being killed (intent == null): pick up where we left off.
        if (!bridge.isOn()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        bridge.removeListener(onChange);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void goForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            // "connected device" fits (USB MIDI gear). Android checks extra
            // conditions for it; if this build refuses, fall back to media playback.
            try {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } catch (SecurityException e) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            }
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private Notification buildNotification() {
        shownText = bridge.shortStatus();
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BridgeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("MIDI Bridge is on")
                .setContentText(shownText)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        if (bridge.shortStatus().equals(shownText)) return;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
    }
}
