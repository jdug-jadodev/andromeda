package local.doja.voice;

import android.content.Context;

/** Interruptor local del gateway de casa. Apagado por defecto. */
final class HomeGatewayConfig {
    private static final String PREFS = "home_gateway";

    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false);
    }

    static void setEnabled(Context context, boolean value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply();
    }

    private HomeGatewayConfig() { }
}
