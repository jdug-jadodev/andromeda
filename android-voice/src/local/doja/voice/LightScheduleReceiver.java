package local.doja.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Exact-alarm entry point. It only starts the foreground worker; BLE stays there. */
public final class LightScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
            || !LightScheduleScheduler.ACTION.equals(intent.getAction())) return;
        String id = intent.getStringExtra("id");
        if (!LightSchedule.validId(id)) return;
        boolean on = intent.getBooleanExtra("on", false);
        try {
            context.startForegroundService(new Intent(context, LightScheduleService.class)
                .setAction(LightScheduleScheduler.ACTION).putExtra("id", id).putExtra("on", on));
        } catch (Exception unavailable) {
            // No retry: the next scheduled event remains authoritative.
        }
    }
}
