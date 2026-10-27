package local.doja.voice;

import android.content.Context;

/** Persists the one greeting already spoken for the current period. */
final class GreetingStore {
    private static final String PREFS = "greetings_v1";
    private static final String KEY = "last_period";
    private GreetingStore() {}

    /** Returns the greeting still owed for this period, without consuming it. */
    static synchronized String peek(Context context, long now) {
        if (context == null) return "";
        String wanted = GreetingPolicy.key(now);
        String previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        return wanted.equals(previous) ? "" : GreetingPolicy.greeting(now);
    }

    /** Marks a greeting as spoken only after TTS actually starts. */
    static synchronized void mark(Context context, String key) {
        if (context == null || key == null || key.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key).commit();
    }
}
