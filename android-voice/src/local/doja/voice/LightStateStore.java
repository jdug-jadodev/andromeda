package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;

/** Best-effort last commanded power state. Never claims to read the physical lamp. */
final class LightStateStore {
    private static final String PREFS = "light_state_v1";
    private LightStateStore() {}

    static void record(Context context, LightTarget target, boolean on) {
        if (context == null || target == null || target == LightTarget.BOTH) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(key(target), on ? 1 : 0).commit();
    }

    static Boolean last(Context context, LightTarget target) {
        if (context == null || target == null || target == LightTarget.BOTH) return null;
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(target), -1);
        return value == 0 ? Boolean.FALSE : value == 1 ? Boolean.TRUE : null;
    }

    /** True when at least one target is unknown or differs from the requested state. */
    static boolean needs(Context context, LightTarget target, boolean on) {
        if (target != LightTarget.BOTH) {
            Boolean value = last(context, target);
            return value == null || value != on;
        }
        Boolean lotus = last(context, LightTarget.LOTUS);
        Boolean star = last(context, LightTarget.MR_STAR);
        return lotus == null || star == null || lotus != on || star != on;
    }

    /** A reboot can leave the physical lamp in a different state; the next schedule must send again. */
    static void clear(Context context) {
        if (context != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String key(LightTarget target) {
        return target == LightTarget.LOTUS ? "lotus" : "mr_star";
    }
}
