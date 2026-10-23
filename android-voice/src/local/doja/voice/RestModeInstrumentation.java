package local.doja.voice;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/** Creates or updates the 23:00/06:00 rest-mode schedule without audio or BLE. */
public final class RestModeInstrumentation extends Instrumentation {
    private Bundle arguments;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            RestModeSchedule schedule = RestModeStore.ensureDefault(context);
            if (arguments != null && arguments.containsKey("night") && arguments.containsKey("morning"))
                schedule = new RestModeSchedule(integer(arguments, "night"), integer(arguments, "morning"),
                    schedule.days, true);
            RestModeStore.save(context, schedule);
            RestModeScheduler.schedule(context);
            result.putString("result", "night=" + LightSchedule.format(schedule.nightMinute)
                + " morning=" + LightSchedule.format(schedule.morningMinute)
                + " enabled=" + schedule.enabled);
            result.putBoolean("audio_opened", false);
            result.putBoolean("ble_written", false);
            finish(0, result);
        } catch (Exception failure) {
            result.putString("result", "rest_mode_setup_failed");
            finish(1, result);
        }
    }
    private static int integer(Bundle arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        throw new IllegalArgumentException("Falta " + key);
    }
}
