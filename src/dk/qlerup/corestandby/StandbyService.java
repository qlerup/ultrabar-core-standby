package dk.qlerup.corestandby;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StandbyService extends Service {
    private static final String CHANNEL = "pc-standby";
    private static final int NOTIFICATION = 1;
    private static final int INTERVAL_MS = 5_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Presence presence = new Presence();
    private PowerManager power;
    private PowerManager.WakeLock cpu;
    private PcProbe probe;
    private boolean slept;
    private boolean destroyed;

    static void pause(Context context) {
        SettingsState.preferences(context).edit().putBoolean("enabled", false).commit();
        context.stopService(new Intent(context, StandbyService.class));
        SettingsState.restoreTimeout(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notifications().createNotificationChannel(new NotificationChannel(
                CHANNEL, "PC Standby", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION, notification("Waiting for the paired PC"));
        power = getSystemService(PowerManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = SettingsState.preferences(this);
        if (!prefs.getBoolean("enabled", false) || !SettingsState.ready(this)) {
            fail("Monitoring paused. Open PC Standby to check its permissions.");
            return START_NOT_STICKY;
        }
        if (probe == null) {
            try {
                probe = new PcProbe(prefs.getString("ip", ""), prefs.getString("code", ""));
                cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PCStandby:Monitor");
                cpu.acquire();
                poll();
            } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
                fail("Monitoring paused. Open PC Standby to check pairing.");
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void poll() {
        if (destroyed) return;
        worker.execute(() -> {
            boolean reply;
            try {
                reply = probe.ping();
            } catch (IOException unavailable) {
                reply = false;
            }
            final boolean authenticated = reply;
            handler.post(() -> update(authenticated));
        });
    }

    private void update(boolean authenticated) {
        if (destroyed || !SettingsState.preferences(this).getBoolean("enabled", false)) return;
        if (!SettingsState.ready(this)) {
            fail("Monitoring paused. Restore the settings and device admin permissions.");
            return;
        }
        try {
            if (authenticated && !SettingsState.takeTimeout(this)) {
                fail("Monitoring paused. Core did not accept the screen timeout setting.");
                return;
            }
            switch (presence.sample(authenticated, SystemClock.elapsedRealtime())) {
                case CONNECTED:
                    if (slept && !power.isInteractive()) wakeScreen();
                    slept = false;
                    notifications().notify(NOTIFICATION, notification("Paired PC connected"));
                    break;
                case LOST:
                    if (power.isInteractive()) {
                        getSystemService(DevicePolicyManager.class).lockNow();
                        slept = true;
                    }
                    notifications().notify(NOTIFICATION, notification("Paired PC unreachable"));
                    break;
                default:
                    break;
            }
        } catch (SecurityException error) {
            fail("Monitoring paused. Core denied a display operation.");
            return;
        }
        handler.postDelayed(this::poll, INTERVAL_MS);
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        PowerManager.WakeLock screen = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, "PCStandby:Reconnect");
        screen.acquire(2_000);
    }

    private NotificationManager notifications() {
        return getSystemService(NotificationManager.class);
    }

    private Notification notification(String message) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, SetupActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("PC Standby").setContentText(message)
                .setContentIntent(open).setOnlyAlertOnce(true).build();
    }

    private void fail(String message) {
        Log.w("PCStandby", message);
        stopForeground(true);
        pause(this);
        notifications().notify(NOTIFICATION, notification(message));
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (cpu != null && cpu.isHeld()) cpu.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
