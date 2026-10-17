package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.*;

/** Uses isolated preferences and random test IDs. Never starts mic, TTS or ringing. */
public class TimerInstrumentation extends Instrumentation {
    static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        Context real = getTargetContext();
        String namespace = "timer_integration_" + UUID.randomUUID();
        Context isolated = new ContextWrapper(real) {
            public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                if (!"timers".equals(name)) throw new IllegalArgumentException("Unexpected test preferences");
                return real.getSharedPreferences(namespace, mode);
            }
        };
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(UUID.randomUUID().toString());
        try {
            String first = ids.get(0), second = ids.get(1), restore = ids.get(2), consume = ids.get(3);
            TimerStore.create(isolated, first, 600);
            String original = TimerStore.prefs(isolated).getString(first, "");
            check(new JSONObject(original).getInt("seconds") == 600, "Create did not persist");
            TimerStore.create(isolated, first, 1200);
            check(original.equals(TimerStore.prefs(isolated).getString(first, "")), "Duplicate changed deadline");
            try { TimerStore.create(isolated, ids.get(4), 0); throw new IllegalStateException("Accepted zero duration"); }
            catch (IllegalArgumentException expected) {}
            check(!TimerStore.prefs(isolated).contains(ids.get(4)), "Invalid timer persisted");
            TimerStore.cancel(isolated, false);
            check(!TimerStore.prefs(isolated).contains(first), "Single cancel did not persist");
            check(!TimerStore.consume(isolated, first), "Cancelled timer could ring");
            TimerStore.create(isolated, first, 600); TimerStore.create(isolated, second, 1200);
            TimerStore.cancel(isolated, false);
            check(TimerStore.prefs(isolated).getAll().size() == 2, "Ambiguous cancel touched timers");
            TimerStore.cancel(isolated, true);
            check(TimerStore.prefs(isolated).getAll().isEmpty(), "Cancel all left records");
            check(!TimerStore.consume(isolated, first) && !TimerStore.consume(isolated, second), "Cancelled delivery not ignored");
            TimerStore.create(isolated, restore, 600);
            JSONObject saved = new JSONObject(TimerStore.prefs(isolated).getString(restore, ""));
            saved.put("elapsed_due", 0);
            check(TimerStore.prefs(isolated).edit().putString(restore, saved.toString()).commit(), "Test setup write failed");
            TimerStore.restore(isolated);
            JSONObject restored = new JSONObject(TimerStore.prefs(isolated).getString(restore, ""));
            check(restored.getLong("elapsed_due") > SystemClock.elapsedRealtime(), "Restore did not recalculate deadline");
            TimerStore.cancel(isolated, true);
            TimerStore.create(isolated, consume, 600);
            check(TimerStore.consume(isolated, consume), "Active timer not consumable");
            check(!TimerStore.consume(isolated, consume), "Duplicate delivery could ring twice");
            result.putString("test", "isolated_android_timer_lifecycle");
            result.putBoolean("passed", true);
            result.putBoolean("microphone_opened", false);
            result.putBoolean("audible_alarm_started", false);
            result.putBoolean("isolated_preferences", true);
        } catch (Exception error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            for (String id : ids) try { TimerStore.cancelAlarm(isolated, id); } catch (Exception ignored) {}
            real.deleteSharedPreferences(namespace);
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
