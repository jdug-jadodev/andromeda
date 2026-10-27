package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;

/** Simulates the public voice state to verify brighten-on-speech and 20 s idle dim. */
public final class BrightnessVoiceInstrumentation extends Instrumentation {
    private Activity activity;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> {
                VoiceService.active = true;
                VoiceService.uiState = FaceState.SPEAKING;
                VoiceService.brightnessActivityAt = SystemClock.elapsedRealtime();
            });
            Thread.sleep(2500L);
            int during = Settings.System.getInt(getTargetContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
            runOnMainSync(() -> {
                VoiceService.active = false;
                VoiceService.uiState = FaceState.READY;
            });
            Thread.sleep(21000L);
            int after = Settings.System.getInt(getTargetContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
            result.putInt("during_voice", during);
            result.putInt("after_20s", after);
            result.putBoolean("audio_opened", false);
            result.putBoolean("microphone_opened", false);
            finish(during == ScreenBrightnessPolicy.ACTIVE_LEVEL
                && after == ScreenBrightnessPolicy.IDLE_LEVEL ? 0 : 1, result);
        } catch (Exception failure) {
            result.putString("result", "brightness_voice_test_failed");
            finish(1, result);
        }
    }
}
