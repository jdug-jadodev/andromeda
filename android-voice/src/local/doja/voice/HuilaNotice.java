package local.doja.voice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The trip countdown at 10:00, 12:00, 17:00 and 20:00 (Bogotá) up to the day of the trip, then never again.
 * Like ChristmasNotice: the alarm is always kept, and only plays with andromeda_huila on and the voice running.
 */
public final class HuilaNotice extends BroadcastReceiver {
    static final String ACTION = "local.doja.voice.HUILA_NOTICE";
    static final int[] HOURS = {10, 12, 17, 20};

    /** The next notice, or 0 once it would fall after the trip. */
    static long nextDue(long nowMs) {
        return ChristmasNoticeTimes.nextUntil(nowMs, HOURS, HuilaMode.TRIP);
    }

    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long due = nextDue(System.currentTimeMillis());
        PendingIntent pending = PendingIntent.getBroadcast(context, 0,
            new Intent(context, HuilaNotice.class).setAction(ACTION).putExtra("due", due),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (due == 0) { manager.cancel(pending); return; }
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending); }
        catch (SecurityException denied) { manager.cancel(pending); }
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        long due = intent.getLongExtra("due", 0);
        try { schedule(context); } catch (RuntimeException ignored) { }
        if (!HuilaMode.enabled(context) || !ChristmasNoticeTimes.onTime(due, System.currentTimeMillis())) return;
        try { context.startService(new Intent(context, VoiceService.class).setAction(ACTION)); }
        catch (RuntimeException notRunning) { /* No voice service: nothing to say it with. */ }
    }
}
