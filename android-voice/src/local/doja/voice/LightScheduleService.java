package local.doja.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/** Executes one due schedule, idempotently, without asking for a new human tap. */
public final class LightScheduleService extends Service {
    private static final String CHANNEL = "lights_schedule";
    private static final int NOTIFICATION_ID = 15;
    private static final long TIMEOUT_MS = 90000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LightCommandDispatcher dispatcher;
    private PowerManager.WakeLock wakeLock;
    private String scheduleId;
    private boolean scheduleOn;
    private boolean completed;

    private final Runnable timeout = () -> {
        // No retry after a possible write. The next daily event is independent.
        if (!completed && scheduleId != null)
            LightScheduleReceiptStore.record(this, scheduleId, scheduleOn, "TIMEOUT", 0, 0);
        complete();
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(
            new NotificationChannel(CHANNEL, "Luces programadas", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("Comprobando la programación de luces"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        if (intent == null) { complete(); return START_NOT_STICKY; }
        String id = intent.getStringExtra("id");
        boolean on = intent.getBooleanExtra("on", false);
        LightSchedule schedule = LightScheduleStore.find(this, id);
        if (schedule == null || !schedule.enabled
                || !schedule.matchesNow(on, System.currentTimeMillis(), 15)) {
            complete();
            return START_NOT_STICKY;
        }
        scheduleId = schedule.id;
        scheduleOn = on;
        // Do not skip from a stored state: a physical switch/reboot can leave the
        // lamp different from the last command. Repeating on/off is harmless;
        // the local address cache makes the scheduled connection direct.
        // Persist the next occurrence before touching BLE, so a process death
        // cannot silently remove tomorrow's schedule.
        try { LightScheduleScheduler.schedule(this); }
        catch (Exception ignored) { /* complete() retries this locally. */ }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Andromeda:lightSchedule");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(TIMEOUT_MS);
        }
        // Scan first so a changed address can be refreshed; the resolver falls
        // back to the locally cached address if the name is not advertised.
        dispatcher = new LightCommandDispatcher(this);
        dispatcher.send(LightCommand.power(schedule.target, on),
            LightWriteAuthorization.issueForSchedule(SystemClock.elapsedRealtime()),
            result -> {
                LightScheduleReceiptStore.record(this, scheduleId, scheduleOn, result.status.name(),
                    result.completedTargetCount, result.acceptedFrameCount);
                handler.post(this::complete);
            });
        handler.postDelayed(timeout, TIMEOUT_MS);
        return START_NOT_STICKY;
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Luces programadas")
            .setContentText(text)
            .setOngoing(true)
            .build();
    }

    private void complete() {
        if (completed) return;
        completed = true;
        handler.removeCallbacks(timeout);
        try { LightScheduleScheduler.schedule(this); }
        catch (Exception ignored) { /* The next boot also repairs alarms. */ }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        if (dispatcher != null) { dispatcher.close(); dispatcher = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
