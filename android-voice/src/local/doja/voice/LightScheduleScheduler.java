package local.doja.voice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules only the next on/off event; each event schedules the following one. */
final class LightScheduleScheduler {
    static final String ACTION = "local.doja.voice.LIGHT_SCHEDULE";
    private LightScheduleScheduler() {}

    static void schedule(Context context) {
        if (context == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long now = System.currentTimeMillis();
        for (LightSchedule value : LightScheduleStore.list(context)) {
            PendingIntent current = pending(context, value.id, true);
            if (!value.enabled) {
                manager.cancel(current);
                current.cancel();
                continue;
            }
            LightSchedule.Event event = value.nextEvent(now);
            if (event == null) {
                manager.cancel(current);
                current.cancel();
                continue;
            }
            PendingIntent next = pending(context, value.id, event.on);
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.wallMs, next);
            } catch (SecurityException denied) {
                // A missing exact-alarm permission must never be retried in a loop.
                manager.cancel(next);
            }
        }
    }

    static void cancel(Context context, String id) {
        if (context == null || !LightSchedule.validId(id)) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pending(context, id, true);
        manager.cancel(pending);
        pending.cancel();
    }

    private static PendingIntent pending(Context context, String id, boolean on) {
        Intent intent = new Intent(context, LightScheduleReceiver.class).setAction(ACTION)
            .putExtra("id", id).putExtra("on", on);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags);
    }
}
