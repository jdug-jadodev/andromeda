package local.doja.voice;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) TimerStore.restore(context);
        // Never start microphone or voice detection automatically in this pilot.
    }
}
