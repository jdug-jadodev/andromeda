package local.doja.voice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Santa's daily notice at 13:00 and 22:00. The alarm is always kept so turning Christmas mode
 * on needs nothing else; it only does something while the mode is on and the voice is running.
 */
public final class ChristmasNotice extends BroadcastReceiver {
    static final String ACTION = "local.doja.voice.CHRISTMAS_NOTICE";
    /** If she is busy at the hour, the notice waits up to this long for her to be back on the clock. */
    static final long RETRY_FOR_MS = 10 * 60_000L;

    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long due = ChristmasNoticeTimes.next(System.currentTimeMillis());
        PendingIntent pending = PendingIntent.getBroadcast(context, 0,
            new Intent(context, ChristmasNotice.class).setAction(ACTION).putExtra("due", due),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending); }
        catch (SecurityException denied) { manager.cancel(pending); }
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        long due = intent.getLongExtra("due", 0);
        try { schedule(context); } catch (RuntimeException ignored) { }
        if (!ChristmasMode.enabled(context) || !ChristmasNoticeTimes.onTime(due, System.currentTimeMillis())) return;
        try { context.startService(new Intent(context, VoiceService.class).setAction(ACTION)); }
        catch (RuntimeException notRunning) { /* No voice service: nothing to say it with. */ }
    }
}
