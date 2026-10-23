package local.doja.voice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** One exact alarm for the next 23:00/06:00 rest-mode event. */
final class RestModeScheduler {
    static final String ACTION = "local.doja.voice.REST_MODE";
    private RestModeScheduler() {}

    static void schedule(Context context) {
        if (context == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        RestModeSchedule schedule = RestModeStore.load(context);
        PendingIntent current = pending(context, RestModeSchedule.Action.NIGHT);
        if (schedule == null || !schedule.enabled) { manager.cancel(current); current.cancel(); return; }
        RestModeSchedule.Event event = schedule.nextEvent(System.currentTimeMillis());
        if (event == null) { manager.cancel(current); current.cancel(); return; }
        PendingIntent next = pending(context, event.action);
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.wallMs, next); }
        catch (SecurityException denied) { manager.cancel(next); }
    }

    static void cancel(Context context) {
        if (context == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pending(context, RestModeSchedule.Action.NIGHT);
        manager.cancel(pending);
        pending.cancel();
    }

    private static PendingIntent pending(Context context, RestModeSchedule.Action action) {
        Intent intent = new Intent(context, RestModeReceiver.class).setAction(ACTION)
            .putExtra("action", action.name());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }
}
