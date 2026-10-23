package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local-only light schedules. No BLE, network or human content. */
final class LightScheduleStore {
    private static final String PREFS = "light_schedules_v1";
    private static final String KEY = "schedules";
    private static final int MAX = 8;
    private LightScheduleStore() {}

    static List<LightSchedule> list(Context context) {
        if (context == null) return Collections.emptyList();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "");
        if (raw.isEmpty()) return Collections.emptyList();
        List<LightSchedule> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(raw);
            for (int i = 0; i < values.length() && result.size() < MAX; i++) {
                JSONObject item = values.optJSONObject(i);
                if (item == null) continue;
                try {
                    LightTarget target = LightTarget.valueOf(item.getString("target"));
                    LightSchedule value = new LightSchedule(item.getString("id"), target,
                        item.getInt("on"), item.getInt("off"), item.getInt("days"), item.getBoolean("enabled"));
                    result.add(value);
                } catch (Exception invalid) { /* Skip one malformed record, never reinterpret it. */ }
            }
        } catch (Exception invalid) { return Collections.emptyList(); }
        return Collections.unmodifiableList(result);
    }

    static LightSchedule find(Context context, String id) {
        if (!LightSchedule.validId(id)) return null;
        for (LightSchedule value : list(context)) if (value.id.equals(id)) return value;
        return null;
    }

    static LightSchedule forTarget(Context context, LightTarget target) {
        for (LightSchedule value : list(context)) if (value.target == target) return value;
        return null;
    }

    static void save(Context context, LightSchedule value) {
        if (context == null || value == null) throw new IllegalArgumentException("Horario incompleto");
        List<LightSchedule> values = new ArrayList<>(list(context));
        boolean replaced = false;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).id.equals(value.id)) { values.set(i, value); replaced = true; break; }
        }
        if (!replaced) {
            if (values.size() >= MAX) throw new IllegalStateException("Máximo de horarios alcanzado");
            values.add(value);
        }
        write(context, values);
    }

    static void delete(Context context, String id) {
        if (!LightSchedule.validId(id)) return;
        List<LightSchedule> values = new ArrayList<>(list(context));
        for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).id.equals(id)) values.remove(i);
        write(context, values);
    }

    static LightSchedule ensureDefault(Context context, LightTarget target) {
        LightSchedule existing = forTarget(context, target);
        if (existing != null) return existing;
        LightSchedule value = LightSchedule.defaultFor(target);
        save(context, value);
        return value;
    }

    private static void write(Context context, List<LightSchedule> values) {
        JSONArray array = new JSONArray();
        for (LightSchedule value : values) {
            try {
                array.put(new JSONObject().put("id", value.id).put("target", value.target.name())
                    .put("on", value.onMinute).put("off", value.offMinute)
                    .put("days", value.days).put("enabled", value.enabled));
            } catch (Exception impossible) { throw new IllegalStateException("Horario no serializable"); }
        }
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, array.toString()).commit())
            throw new IllegalStateException("No se guardó el horario");
    }
}
