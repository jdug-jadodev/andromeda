package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Local-only rest-mode schedule. No audio, transcripts or human content. */
final class RestModeStore {
    private static final String PREFS = "rest_mode_v1";
    private static final String KEY = "schedule";
    private RestModeStore() {}

    static RestModeSchedule load(Context context) {
        if (context == null) return null;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (raw.isEmpty()) return null;
        try {
            JSONObject item = new JSONObject(raw);
            return new RestModeSchedule(item.getInt("night"), item.getInt("morning"),
                item.getInt("days"), item.getBoolean("enabled"));
        } catch (Exception invalid) { return null; }
    }

    static void save(Context context, RestModeSchedule value) {
        if (context == null || value == null) throw new IllegalArgumentException("Modo descanso incompleto");
        try {
            String raw = new JSONObject().put("night", value.nightMinute)
                .put("morning", value.morningMinute).put("days", value.days)
                .put("enabled", value.enabled).toString();
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, raw).commit())
                throw new IllegalStateException("No se guardó el modo descanso");
        } catch (org.json.JSONException invalid) { throw new IllegalStateException("Modo descanso no serializable"); }
    }

    static RestModeSchedule ensureDefault(Context context) {
        RestModeSchedule value = load(context);
        if (value != null) return value;
        value = RestModeSchedule.defaultSchedule();
        save(context, value);
        return value;
    }
}
