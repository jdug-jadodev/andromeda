package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local-only BLE address cache for the two authorized lamps. It avoids relying
 * on an advertisement while the lamp is already on; addresses never leave the
 * phone and are never written to logs or receipts.
 */
final class LightAddressStore {
    private static final String PREFS = "light_addresses_v1";
    private LightAddressStore() {}

    static void save(Context context, LightTarget target, String address) {
        if (context == null || target == null || target == LightTarget.BOTH || !valid(address)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(target), address.toUpperCase(java.util.Locale.ROOT)).commit();
    }

    static String get(Context context, LightTarget target) {
        if (context == null || target == null || target == LightTarget.BOTH) return null;
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(target), "");
        return valid(value) ? value : null;
    }

    static void clear(Context context) {
        if (context != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static boolean valid(String address) {
        return address != null && address.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}");
    }

    private static String key(LightTarget target) {
        return target == LightTarget.LOTUS ? "lotus" : "mr_star";
    }
}
