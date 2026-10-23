package local.doja.voice;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

/** Applies brightness and media volume only; no alarms, calls, ringer, DND or model changes. */
final class RestModeController {
    static final int NIGHT_BRIGHTNESS = 1;
    static final int MORNING_BRIGHTNESS = Math.round(255 * 0.70f);
    static final int NIGHT_VOLUME = 50;
    static final int MORNING_VOLUME = 100;

    static final class Result {
        final RestModeSchedule.Action action;
        final boolean brightnessApplied, volumeApplied;
        final int brightnessLevel, volumeIndex, volumeMaximum;
        Result(RestModeSchedule.Action action, boolean brightnessApplied, int brightnessLevel,
                boolean volumeApplied, int volumeIndex, int volumeMaximum) {
            this.action = action; this.brightnessApplied = brightnessApplied;
            this.brightnessLevel = brightnessLevel; this.volumeApplied = volumeApplied;
            this.volumeIndex = volumeIndex; this.volumeMaximum = volumeMaximum;
        }
        int brightnessPercent() {
            return brightnessLevel < 0 ? -1 : Math.round(brightnessLevel * 100f / 255f);
        }
        int volumePercent() {
            return volumeIndex < 0 || volumeMaximum <= 0 ? -1
                : Math.round(volumeIndex * 100f / volumeMaximum);
        }
    }

    static Result apply(Context context, RestModeSchedule.Action action) {
        if (context == null || action == null) throw new IllegalArgumentException("Modo descanso incompleto");
        boolean brightness = false;
        int brightnessLevel = -1;
        if (Settings.System.canWrite(context)) {
            try {
                int target = action == RestModeSchedule.Action.NIGHT ? NIGHT_BRIGHTNESS : MORNING_BRIGHTNESS;
                Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, target);
                int actual = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
                brightnessLevel = actual;
                brightness = actual == target;
            } catch (RuntimeException ignored) { brightness = false; brightnessLevel = -1; }
        }
        boolean volume = false;
        int volumeIndex = -1, volumeMaximum = -1;
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int percent = action == RestModeSchedule.Action.NIGHT ? NIGHT_VOLUME : MORNING_VOLUME;
                int target = Math.round(maximum * percent / 100f);
                if (maximum > 0 && target >= 0 && target <= maximum) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                    volumeMaximum = maximum;
                    volumeIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    volume = volumeIndex >= 0;
                }
            }
        } catch (RuntimeException ignored) { volume = false; }
        return new Result(action, brightness, brightnessLevel, volume, volumeIndex, volumeMaximum);
    }
}
