package local.doja.voice;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded numeric receipt for the last scheduled light execution. No content. */
final class LightScheduleReceiptStore {
    private static final String PREFS = "light_schedule_receipt_v1";
    private LightScheduleReceiptStore() {}

    static void record(Context context, String scheduleId, boolean on, String status,
            int completed, int accepted) {
        if (context == null || !LightSchedule.validId(scheduleId) || status == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("schedule_id", scheduleId).putBoolean("on", on).putString("status", status)
            .putInt("completed", Math.max(0, completed)).putInt("accepted", Math.max(0, accepted))
            .putLong("at_ms", System.currentTimeMillis()).apply();
    }

    static String describe(Context context, LightSchedule schedule) {
        if (context == null || schedule == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!schedule.id.equals(prefs.getString("schedule_id", ""))) return "";
        String status = prefs.getString("status", "");
        long at = prefs.getLong("at_ms", 0L);
        if (status.isEmpty() || at <= 0) return "";
        String when = new SimpleDateFormat("dd/MM HH:mm", new Locale("es", "CO")).format(new Date(at));
        String state = prefs.getBoolean("on", false) ? "encender" : "apagar";
        String result;
        switch (status) {
            case "COMPLETE_UNCONFIRMED": result = "enviado al Bluetooth, sin confirmación visual"; break;
            case "SKIPPED": result = "ya estaban en ese estado; no se envió BLE"; break;
            case "PARTIAL_OR_UNCERTAIN": result = "parcial o incierto; no se reintentó"; break;
            case "TRANSPORT_ERROR": result = "sin conexión; no se confirmó ningún cambio"; break;
            case "DEVICE_NOT_FOUND": result = "no se encontró la luz; no se envió"; break;
            case "UNSUPPORTED_ORDER": result = "orden no validada; no se envió"; break;
            case "TIMEOUT": result = "sin recibo dentro del tiempo; revisar las luces"; break;
            default: result = "sin confirmación; revisar las luces";
        }
        return "Última ejecución · " + when + " · " + state + ": " + result + ".";
    }
}
