package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Numeric last-execution receipt for rest mode. No audio, text or human content. */
final class RestModeReceiptStore {
    private static final String PREFS = "rest_mode_receipt_v1";
    private RestModeReceiptStore() {}

    static void record(Context context, RestModeController.Result result) {
        if (context == null || result == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("action", result.action.name())
            .putBoolean("brightness", result.brightnessApplied)
            .putBoolean("volume", result.volumeApplied)
            .putInt("brightness_percent", result.brightnessPercent())
            .putInt("volume_percent", result.volumePercent())
            .putLong("at_ms", System.currentTimeMillis()).commit();
    }

    static String describe(Context context) {
        if (context == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String action = prefs.getString("action", "");
        long at = prefs.getLong("at_ms", 0L);
        if (action.isEmpty() || at <= 0) return "";
        String when = new SimpleDateFormat("dd/MM HH:mm", new Locale("es", "CO")).format(new Date(at));
        String phase = "NIGHT".equals(action) ? "descanso 23:00" : "despertar 06:00";
        int brightness = prefs.getInt("brightness_percent", -1);
        int volume = prefs.getInt("volume_percent", -1);
        String brightnessText = prefs.getBoolean("brightness", false)
            ? ("NIGHT".equals(action) ? "brillo mínimo" : "brillo " + brightness + "%")
            : "brillo no aplicado";
        String volumeText = prefs.getBoolean("volume", false)
            ? "volumen " + volume + "%" : "volumen no aplicado";
        return "Última ejecución · " + when + " · " + phase + ": " + brightnessText + ", " + volumeText + ".";
    }
}
