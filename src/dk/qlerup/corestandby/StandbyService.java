package dk.qlerup.corestandby;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Presence presence = new Presence();
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefs = SettingsState.preferences(StandbyService.this);
            if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || destroyed
                    || !prefs.getBoolean("enabled", false)) return;
            try {
                sleepIfOffline(prefs);
            } catch (SecurityException error) {
                fail("Monitoring paused. Core denied a display operation.");
            }
        }
    };
    private PowerManager power;
    private PowerManager.WakeLock cpu;
    private Probe probe;
    private boolean slept;
    private boolean destroyed;

    static boolean pause(Context context) {
        boolean saved = SettingsState.preferences(context).edit().putBoolean("enabled", false)
                .putBoolean("studioConnected", false).remove("studioConfirmed").remove("studioSlept").commit();
        context.stopService(new Intent(context, StandbyService.class));
        return SettingsState.restoreTimeout(context) && saved;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notifications().createNotificationChannel(new NotificationChannel(
                CHANNEL, "PC Standby", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION, notification("Waiting for the paired PC"));
        power = getSystemService(PowerManager.class);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
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
                probe = StudioProbe.config(this).isFile()
                        ? new StudioProbe(this)
                        : new PcProbe(prefs.getString("ip", ""), prefs.getString("code", ""));
                if (probe instanceof StudioProbe && prefs.getBoolean("studioConfirmed", false)) {
                    slept = prefs.getBoolean("studioSlept", false);
                    presence.restore(prefs.getBoolean("studioConnected", false));
                }
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
        long next = SystemClock.uptimeMillis() + Presence.INTERVAL_MS;
        worker.execute(() -> {
            boolean reply;
            try {
                reply = probe.ping();
            } catch (IOException unavailable) {
                reply = false;
            }
            final boolean authenticated = reply;
            handler.post(() -> update(authenticated, next));
        });
    }

    private void update(boolean authenticated, long next) {
        SharedPreferences prefs = SettingsState.preferences(this);
        if (destroyed || !prefs.getBoolean("enabled", false)) return;
        if (!SettingsState.ready(this)) {
            fail("Monitoring paused. Restore the settings and device admin permissions.");
            return;
        }
        try {
            if (authenticated && !SettingsState.takeTimeout(this)) {
                fail("Monitoring paused. Core did not accept the screen timeout setting.");
                return;
            }
            switch (presence.sample(authenticated)) {
                case CONNECTED:
                    if (slept && !power.isInteractive()) wakeScreen();
                    slept = false;
                    if (probe instanceof StudioProbe) {
                        prefs.edit().putBoolean("studioConnected", true)
                                .putBoolean("studioConfirmed", true).remove("studioSlept").commit();
                    }
                    notifications().notify(NOTIFICATION, notification(
                            probe instanceof StudioProbe ? "Studio connected" : "Paired PC connected"));
                    break;
                case LOST:
                    if (probe instanceof StudioProbe) {
                        prefs.edit().putBoolean("studioConnected", false).commit();
                    }
                    notifications().notify(NOTIFICATION, notification(
                            probe instanceof StudioProbe ? "Studio unreachable" : "Paired PC unreachable"));
                    break;
                default:
                    break;
            }
            sleepIfOffline(prefs);
        } catch (SecurityException error) {
            fail("Monitoring paused. Core denied a display operation.");
            return;
        }
        handler.postAtTime(this::poll, next);
    }

    private void sleepIfOffline(SharedPreferences prefs) {
        if (!presence.shouldSleep(power.isInteractive())) return;
        getSystemService(DevicePolicyManager.class).lockNow();
        if (!slept && probe instanceof StudioProbe) {
            prefs.edit().putBoolean("studioSlept", true).commit();
        }
        slept = true;
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
        unregisterReceiver(screenOn);
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
