package local.doja.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Applies one due rest-mode event. It never opens audio capture or changes the model. */
public final class RestModeService extends Service {
    private static final String CHANNEL = "rest_mode";
    private static final int NOTIFICATION_ID = 16;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private boolean completed;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(
            new NotificationChannel(CHANNEL, "Modo descanso", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Modo descanso")
            .setContentText("Aplicando brillo y volumen programados")
            .setOngoing(true).build());
        RestModeSchedule.Action action = action(intent);
        RestModeSchedule schedule = RestModeStore.load(this);
        if (action == null || schedule == null || !schedule.enabled
                || !schedule.matchesNow(action, System.currentTimeMillis(), 15)) {
            complete();
            return START_NOT_STICKY;
        }
        // Preserve the next event before the system writes.
        try { RestModeScheduler.schedule(this); } catch (Exception ignored) { }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Andromeda:restMode");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(15000L);
        }
        try { RestModeReceiptStore.record(this, RestModeController.apply(this, action)); }
        catch (RuntimeException ignored) { /* Receipt remains previous; no retry. */ }
        complete();
        return START_NOT_STICKY;
    }

    private static RestModeSchedule.Action action(Intent intent) {
        if (intent == null) return null;
        String value = intent.getStringExtra("action");
        try { return value == null ? null : RestModeSchedule.Action.valueOf(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private void complete() {
        if (completed) return;
        completed = true;
        try { RestModeScheduler.schedule(this); } catch (Exception ignored) { }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
