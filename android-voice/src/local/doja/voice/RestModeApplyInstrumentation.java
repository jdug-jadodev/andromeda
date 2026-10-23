package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;

/** Applies and verifies one rest action, then restores the previous system values. */
public final class RestModeApplyInstrumentation extends Instrumentation {
    private boolean finished;
    private Bundle arguments;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }

    public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int oldBrightness = -1, oldMode = -1, oldVolume = -1;
        try { oldBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1); }
        catch (Exception ignored) { }
        try { oldMode = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1); }
        catch (Exception ignored) { }
        try { oldVolume = audio == null ? -1 : audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (Exception ignored) { }
        try {
            String value = arguments == null ? null : arguments.getString("action");
            RestModeSchedule.Action action = value == null ? null : RestModeSchedule.Action.valueOf(value);
            if (action == null) throw new IllegalArgumentException("Falta acción");
            RestModeController.Result applied = RestModeController.apply(context, action);
            result.putString("action", action.name());
            result.putBoolean("brightness_applied", applied.brightnessApplied);
            result.putBoolean("volume_applied", applied.volumeApplied);
            result.putInt("brightness_percent", applied.brightnessPercent());
            result.putInt("volume_percent", applied.volumePercent());
            result.putBoolean("write_settings", Settings.System.canWrite(context));
            finishResult(0, result);
        } catch (Exception failure) {
            result.putString("result", "rest_mode_apply_failed");
            finishResult(1, result);
        } finally {
            try {
                if (oldMode >= 0) Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, oldMode);
                if (oldBrightness >= 0) Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, oldBrightness);
                if (oldVolume >= 0 && audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0);
            } catch (Exception ignored) { }
        }
    }

    private void finishResult(int code, Bundle result) {
        if (finished) return;
        finished = true;
        finish(code == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
