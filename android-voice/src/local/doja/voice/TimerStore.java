package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

public final class TimerStore {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("timers", Context.MODE_PRIVATE); }
    static PendingIntent alarm(Context c, String id) {
        Intent intent = new Intent(c, TimerReceiver.class).setAction("local.doja.voice.TIMER." + id).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c, String id, long remaining) {
        c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + Math.max(1000, remaining), alarm(c, id));
    }
    static void cancelAlarm(Context c, String id) {
        PendingIntent operation = alarm(c, id);
        c.getSystemService(AlarmManager.class).cancel(operation);
        operation.cancel();
    }
    public static synchronized String create(Context c, String id, int seconds) throws Exception {
        UUID.fromString(id);
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException();
        if (prefs(c).contains(id)) return "Ese temporizador ya está programado.";
        if (prefs(c).getAll().size() >= 5) return "Ya hay cinco temporizadores. Cancela alguno primero.";
        long now = SystemClock.elapsedRealtime();
        JSONObject item = new JSONObject().put("wall_due", System.currentTimeMillis() + seconds * 1000L)
            .put("elapsed_due", now + seconds * 1000L).put("seconds", seconds);
        if (!prefs(c).edit().putString(id, item.toString()).commit()) throw new IllegalStateException("No se guardó");
        try { schedule(c, id, seconds * 1000L); }
        catch (Exception error) { prefs(c).edit().remove(id).commit(); throw error; }
        return "Temporizador de " + duration(seconds) + " activado.";
    }
    public static synchronized String cancel(Context c, boolean all) {
        Map<String, ?> items = prefs(c).getAll();
        if (items.isEmpty()) return "No hay temporizadores activos.";
        if (!all && items.size() > 1) return "Hay varios temporizadores. Puedes decir cancela todos los temporizadores.";
        android.content.SharedPreferences.Editor editor = prefs(c).edit();
        for (String id : items.keySet()) editor.remove(id);
        // Persist cancellation before touching scheduled delivery. If an already
        // queued broadcast races the cancel, consume() sees no active record.
        if (!editor.commit()) return "No se pudo confirmar el borrado. Revisa la pantalla.";
        for (String id : items.keySet()) {
            cancelAlarm(c, id);
        }
        return all ? "Temporizadores cancelados." : "Temporizador cancelado.";
    }
    public static synchronized String describe(Context c) {
        Map<String, ?> items = prefs(c).getAll();
        if (items.isEmpty()) return "No hay temporizadores activos.";
        List<Long> remaining = new ArrayList<>();
        for (Object value : items.values()) try {
            remaining.add(Math.max(0, new JSONObject((String)value).getLong("elapsed_due") - SystemClock.elapsedRealtime()));
        } catch (Exception ignored) {}
        Collections.sort(remaining);
        if (remaining.isEmpty()) return "No se pudo leer el estado de los temporizadores.";
        return "Hay " + remaining.size() + ". El próximo termina en " + duration((int)((remaining.get(0) + 999) / 1000)) + ".";
    }
    static String duration(int seconds) {
        if (seconds % 3600 == 0 && seconds > 0) return (seconds / 3600) + " horas";
        if (seconds % 60 == 0 && seconds > 0) return (seconds / 60) + " minutos";
        return seconds + " segundos";
    }
    public static synchronized boolean consume(Context c, String id) {
        if (id == null || !prefs(c).contains(id)) return false;
        return prefs(c).edit().remove(id).commit();
    }
    public static synchronized void restore(Context c) {
        for (Map.Entry<String, ?> entry : prefs(c).getAll().entrySet()) {
            try {
                JSONObject item = new JSONObject((String)entry.getValue());
                long remaining = Math.max(1000, item.getLong("wall_due") - System.currentTimeMillis());
                item.put("elapsed_due", SystemClock.elapsedRealtime() + remaining);
                if (prefs(c).edit().putString(entry.getKey(), item.toString()).commit()) schedule(c, entry.getKey(), remaining);
            } catch (Exception ignored) { /* Do not announce restoration if scheduling failed. */ }
        }
    }
}
