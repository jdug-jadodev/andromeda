package local.doja.voice;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "AndromedaBoot";

    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            TimerStore.restore(context);
            LightStateStore.clear(context);
            LightScheduleScheduler.schedule(context);
            RestModeScheduler.schedule(context);
            ChristmasNotice.schedule(context);
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            new Thread(() -> {
                try { AgendaStore.restore(app); }
                catch (RuntimeException failure) { Log.e(TAG, "Agenda restore failed", failure); }
                finally { pending.finish(); }
                // Root/backend startup can take tens of seconds. The gateway is
                // promoted to a foreground service during this worker, so it
                // keeps the app process alive without holding the boot result.
                if (continuousVoiceWasAuthorized(app)) startAndromeda(app);
            }, "andromeda-boot").start();
        }
    }

    private static boolean continuousVoiceWasAuthorized(Context context) {
        return AssistantConfig.CONTINUOUS_VOICE
            && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            && context.getSharedPreferences("ui", Context.MODE_PRIVATE)
                .getInt("continuous_voice_consent", 0) == ContinuousVoicePolicy.CONSENT_VERSION;
    }

    private static void startAndromeda(Context context) {
        try {
            // Keep the gateway available to the family while the resident starts.
            Intent gateway = new Intent(context, HomeGatewayService.class)
                .setAction(HomeGatewayService.ACTION_START)
                .putExtra(HomeGatewayService.EXTRA_ENABLED, true);
            context.startForegroundService(gateway);

            AndromedaEngine.Result result = AndromedaEngine.startAndWait();
            if (!result.ready) {
                Log.e(TAG, "Resident boot start failed: " + result.state);
                return;
            }

            Intent voice = new Intent(context, VoiceService.class).setAction("START")
                .putExtra("conversation_only", true)
                .putExtra("assistant_tools", true)
                .putExtra("continuous_voice", true)
                .putExtra("seconds", 300);
            context.startForegroundService(voice);
            Log.i(TAG, "Gateway, resident, watchdog and continuous voice started");
        } catch (Exception failure) {
            Log.e(TAG, "Automatic startup failed: " + failure.getClass().getSimpleName());
        }
    }
}
