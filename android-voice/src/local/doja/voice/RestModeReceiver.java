package local.doja.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Exact-alarm entry point for rest mode. */
public final class RestModeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !RestModeScheduler.ACTION.equals(intent.getAction())) return;
        String action = intent.getStringExtra("action");
        if (!RestModeSchedule.Action.NIGHT.name().equals(action)
                && !RestModeSchedule.Action.MORNING.name().equals(action)) return;
        try {
            context.startForegroundService(new Intent(context, RestModeService.class)
                .setAction(RestModeScheduler.ACTION).putExtra("action", action));
        } catch (Exception unavailable) {
            // The next exact alarm remains authoritative; never retry a receiver.
        }
    }
}
