package local.doja.voice;

import android.content.Context;
import android.os.SystemClock;

/** Numeric light-voice sequence only. Never stores words, colors or addresses. */
final class LightVoiceHealth {
    private static final String PREFS = "voice_light_health";
    private LightVoiceHealth() {}

    static synchronized void record(Context context, int stage, int status, int targetCount,
            int completed, int accepted, boolean active, boolean armed, boolean confirmation) {
        if (context == null || stage < 1) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String history = prefs.getString("history", "");
        if (history.length() > 180) history = history.substring(history.length() - 180);
        history = history.isEmpty() ? Integer.toString(stage) : history + "," + stage;
        prefs.edit().clear()
            .putInt("version", 1).putInt("stage", stage).putInt("status", status)
            .putInt("target_count", targetCount).putInt("completed", completed)
            .putInt("accepted", accepted).putInt("active", active ? 1 : 0)
            .putInt("armed", armed ? 1 : 0).putInt("confirmation", confirmation ? 1 : 0)
            .putString("history", history).putLong("at_ms", SystemClock.elapsedRealtime()).apply();
    }
}
