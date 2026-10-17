package local.doja.voice;
import android.content.*;
public class TimerReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (TimerStore.consume(context, intent.getStringExtra("id")))
            context.startForegroundService(new Intent(context, RingService.class));
    }
}
