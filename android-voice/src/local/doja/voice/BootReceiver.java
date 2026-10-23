package local.doja.voice;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            TimerStore.restore(context);
            LightStateStore.clear(context);
            LightScheduleScheduler.schedule(context);
            RestModeScheduler.schedule(context);
            PendingResult pending = goAsync();
            new Thread(() -> { try { AgendaStore.restore(context); } finally { pending.finish(); } }, "agenda-restore").start();
        }
        // Never start microphone or voice detection automatically in this pilot.
    }
}
