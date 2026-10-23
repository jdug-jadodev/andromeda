package local.doja.voice;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/** Creates the requested 17:30/22:00 daily schedule. It never writes BLE or opens audio. */
public final class LightScheduleInstrumentation extends Instrumentation {
    private Bundle arguments;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    private static int integer(Bundle arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return Integer.parseInt((String) value);
        throw new IllegalArgumentException("Falta " + key);
    }

    public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            LightSchedule schedule = LightScheduleStore.forTarget(context, LightTarget.BOTH);
            boolean created = schedule == null;
            if (created) schedule = LightSchedule.defaultFor(LightTarget.BOTH);
            if (arguments != null && arguments.containsKey("on") && arguments.containsKey("off"))
                schedule = new LightSchedule(schedule.id, LightTarget.BOTH,
                    integer(arguments, "on"), integer(arguments, "off"), schedule.days, true);
            LightScheduleStore.save(context, schedule);
            LightScheduleScheduler.schedule(context);
            result.putString("result", "on=" + LightSchedule.format(schedule.onMinute)
                + " off=" + LightSchedule.format(schedule.offMinute)
                + " days=" + LightSchedule.daysLabel(schedule.days)
                + " created=" + created);
            result.putBoolean("ble_written", false);
            finish(0, result);
        } catch (Exception failure) {
            result.putString("result", "schedule_setup_failed");
            finish(1, result);
        }
    }
}
