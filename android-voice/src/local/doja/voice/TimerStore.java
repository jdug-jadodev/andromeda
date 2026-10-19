package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

public final class TimerStore {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("timers", Context.MODE_PRIVATE); }
    static android.content.SharedPreferences actionReceipts(Context c) { return c.getSharedPreferences("timer_action_receipts", Context.MODE_PRIVATE); }
    static TimerCreation.Receipt actionReceipt(Context c, String id) throws Exception {
        if (!actionReceipts(c).contains(id)) return null;
        JSONObject item = new JSONObject(actionReceipts(c).getString(id, ""));
        int seconds = item.getInt("seconds");
        if (seconds < 1 || seconds > 86400) throw new IllegalStateException("Recibo inválido");
        return new TimerCreation.Receipt(id, seconds, TimerCreation.Status.valueOf(item.getString("status")));
    }
    // The explicit TLS trial must possess a single-use, request-authorized
    // permit; old create() is never used for this path.
    static synchronized TimerCreation.Receipt createAuthorized(Context c, TimerActionPolicy.Permit permit) {
        return TimerCreation.create(permit, new TimerCreation.Store() {
            public TimerCreation.Receipt receipt(String id) throws Exception {
                return actionReceipt(c, id);
            }
            public int activeCount() { return prefs(c).getAll().size(); }
            public boolean exists(String id) { return prefs(c).contains(id); }
            public int receiptCount() {
                // Bounded, independent metadata, never a conversation transcript.
                long now = System.currentTimeMillis();
                android.content.SharedPreferences.Editor cleanup = actionReceipts(c).edit(); boolean changed = false;
                for (Map.Entry<String, ?> entry : actionReceipts(c).getAll().entrySet()) try {
                    long at = new JSONObject((String)entry.getValue()).getLong("wall_created");
                    if (at > 0 && now >= at && now - at >= 48 * 3600000L && !prefs(c).contains(entry.getKey())) {
                        cleanup.remove(entry.getKey()); changed = true;
                    }
                } catch (Exception invalid) { /* Do not discard unknown records. */ }
                if (changed && !cleanup.commit()) throw new IllegalStateException("No se confirmó la limpieza de recibos");
                return actionReceipts(c).getAll().size();
            }
            public void receipt(TimerCreation.Receipt value) throws Exception {
                JSONObject item = new JSONObject().put("seconds", value.seconds).put("status", value.status.name())
                    .put("wall_created", System.currentTimeMillis());
                if (!actionReceipts(c).edit().putString(value.id, item.toString()).commit())
                    throw new IllegalStateException("No se guardó el recibo");
            }
            public void persist(String id, int seconds) throws Exception {
                if (prefs(c).contains(id)) throw new IllegalStateException("ID ocupado");
                JSONObject item = new JSONObject().put("wall_due", System.currentTimeMillis() + seconds * 1000L)
                    .put("elapsed_due", SystemClock.elapsedRealtime() + seconds * 1000L)
                    .put("seconds", seconds).put("confirmed_action", true);
                if (!prefs(c).edit().putString(id, item.toString()).commit()) throw new IllegalStateException("No se guardó el timer");
            }
            public void schedule(String id, int seconds) throws Exception {
                long due = new JSONObject(prefs(c).getString(id, "")).getLong("elapsed_due");
                TimerStore.schedule(c, id, due - SystemClock.elapsedRealtime());
            }
            public void remove(String id) {
                if (!prefs(c).edit().remove(id).commit()) throw new IllegalStateException("No se confirmó el borrado");
            }
            public void cancel(String id) { cancelAlarm(c, id); }
        }, SystemClock::elapsedRealtime);
    }
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
        try {
            JSONObject timer = new JSONObject(prefs(c).getString(id, ""));
            if (timer.optBoolean("confirmed_action") && !TimerCreation.recoverable(actionReceipt(c, id), timer.getInt("seconds"))) return false;
        } catch (Exception invalid) { return false; }
        return prefs(c).edit().remove(id).commit();
    }
    public static synchronized void restore(Context c) {
        for (Map.Entry<String, ?> entry : prefs(c).getAll().entrySet()) {
            try {
                JSONObject item = new JSONObject((String)entry.getValue());
                if (item.optBoolean("confirmed_action")) {
                    if (!TimerCreation.recoverable(actionReceipt(c, entry.getKey()), item.getInt("seconds"))) continue;
                }
                long remaining = Math.max(1000, item.getLong("wall_due") - System.currentTimeMillis());
                item.put("elapsed_due", SystemClock.elapsedRealtime() + remaining);
                if (prefs(c).edit().putString(entry.getKey(), item.toString()).commit()) schedule(c, entry.getKey(), remaining);
            } catch (Exception ignored) { /* Do not announce restoration if scheduling failed. */ }
        }
    }
}
