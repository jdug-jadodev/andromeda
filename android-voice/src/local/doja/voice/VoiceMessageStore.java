package local.doja.voice;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Mensajes de voz que los telefonos dejan para la casa.
 *
 * El audio se guarda en disco y el indice solo tiene metadatos: nunca se
 * mantiene un mensaje de 2 MB en memoria. Una entrada caduca a las 24 h y su
 * audio se borra en cuanto suena. Todo el estado vive en este proceso, asi que
 * el gateway y el servicio de voz comparten la misma instancia.
 */
final class VoiceMessageStore {
    static final class Fault extends IOException {
        final int http;
        final String code;
        final long retryAfterMs;

        Fault(int http, String code, String message, long retryAfterMs) {
            super(message);
            this.http = http;
            this.code = code;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static VoiceMessageStore instance;

    static synchronized VoiceMessageStore get(Context context) {
        if (instance == null) instance = new VoiceMessageStore(context.getApplicationContext());
        return instance;
    }

    private final File directory;
    private final File index;
    private final Object lock = new Object();
    private final java.util.LinkedHashMap<String, JSONObject> entries = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Long> lastUpload = new java.util.HashMap<>();
    private boolean loaded;

    private VoiceMessageStore(Context context) {
        this.directory = new File(context.getFilesDir(), "voice-messages");
        this.index = new File(directory, "index.json");
    }

    /** 201 body, or Fault with the HTTP status the gateway must return. */
    JSONObject put(String deviceId, String deviceName, String id, String type,
            long durationMs, byte[] audio) throws Fault {
        if (!VoiceMessagePolicy.id(id)) throw new Fault(400, "invalid_request", "Mensaje invalido.", 0);
        if (type == null) throw new Fault(415, "unsupported_media", "Formato de audio no soportado.", 0);
        if (!VoiceMessagePolicy.duration(durationMs)) {
            throw new Fault(400, "invalid_request", "Duracion fuera de rango.", 0);
        }
        if (audio == null || audio.length == 0) {
            throw new Fault(400, "invalid_request", "Mensaje vacio.", 0);
        }
        if (!VoiceMessagePolicy.bytes(audio.length)) {
            throw new Fault(413, "too_large", "El mensaje supera 2 MB.", 0);
        }
        synchronized (lock) {
            load();
            long now = System.currentTimeMillis();
            prune(now);
            JSONObject existing = entries.get(id);
            if (existing != null) {
                if (!deviceId.equals(existing.optString("device_id"))) {
                    throw new Fault(404, "not_found", "Mensaje desconocido.", 0);
                }
                return status(existing);
            }
            int pending = 0;
            for (JSONObject entry : entries.values()) {
                if (!deviceId.equals(entry.optString("device_id"))) continue;
                String state = entry.optString("status");
                if (VoiceMessagePolicy.QUEUED.equals(state) || VoiceMessagePolicy.PLAYING.equals(state)) pending++;
            }
            if (pending >= VoiceMessagePolicy.PENDING) {
                throw new Fault(429, "rate_limited", "Hay demasiados mensajes pendientes.", 60000L);
            }
            Long last = lastUpload.get(deviceId);
            if (last != null && now - last < VoiceMessagePolicy.MIN_INTERVAL_MS) {
                long wait = VoiceMessagePolicy.MIN_INTERVAL_MS - (now - last);
                throw new Fault(429, "rate_limited", "Espera antes de enviar otro mensaje.", wait);
            }
            String name = id + "." + VoiceMessagePolicy.extension(type);
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Sin directorio");
                Files.write(new File(directory, name).toPath(), audio);
            } catch (IOException failure) {
                throw new Fault(500, "internal", "No pude guardar el mensaje.", 0);
            }
            try {
                JSONObject entry = new JSONObject()
                    .put("id", id)
                    .put("device_id", deviceId)
                    .put("device_name", deviceName == null ? "" : deviceName)
                    .put("status", VoiceMessagePolicy.QUEUED)
                    .put("type", type)
                    .put("duration_ms", durationMs)
                    .put("file", name)
                    .put("created_ms", now)
                    .put("expires_at_ms", now + VoiceMessagePolicy.TTL_MS);
                entries.put(id, entry);
                lastUpload.put(deviceId, now);
                save();
                return status(entry);
            } catch (Exception impossible) {
                throw new Fault(500, "internal", "No pude registrar el mensaje.", 0);
            }
        }
    }

    /** Status for the owning device, or null when it does not exist. */
    JSONObject status(String deviceId, String id) {
        if (!VoiceMessagePolicy.id(id)) return null;
        synchronized (lock) {
            load();
            prune(System.currentTimeMillis());
            JSONObject entry = entries.get(id);
            if (entry == null || !deviceId.equals(entry.optString("device_id"))) return null;
            return status(entry);
        }
    }

    JSONArray list(String deviceId) {
        synchronized (lock) {
            load();
            prune(System.currentTimeMillis());
            JSONArray list = new JSONArray();
            for (JSONObject entry : entries.values()) {
                if (!deviceId.equals(entry.optString("device_id"))) continue;
                list.put(status(entry));
            }
            // Most recent first.
            JSONArray ordered = new JSONArray();
            for (int index = list.length() - 1; index >= 0; index--) ordered.put(list.optJSONObject(index));
            return ordered;
        }
    }

    int queuedCount() {
        synchronized (lock) {
            load();
            prune(System.currentTimeMillis());
            int count = 0;
            for (JSONObject entry : entries.values()) {
                if (VoiceMessagePolicy.QUEUED.equals(entry.optString("status"))) count++;
            }
            return count;
        }
    }

    /**
     * Oldest playable message, already marked as playing. Returns null when
     * there is nothing to say. The caller must finish it with played/failed or
     * put it back with requeue().
     */
    JSONObject beginNext() {
        synchronized (lock) {
            load();
            long now = System.currentTimeMillis();
            prune(now);
            JSONObject chosen = null;
            for (JSONObject entry : entries.values()) {
                if (!VoiceMessagePolicy.QUEUED.equals(entry.optString("status"))) continue;
                if (chosen == null || entry.optLong("created_ms") < chosen.optLong("created_ms")) chosen = entry;
            }
            if (chosen == null) return null;
            try {
                chosen.put("status", VoiceMessagePolicy.PLAYING);
                save();
            } catch (Exception impossible) {
                return null;
            }
            return chosen;
        }
    }

    File file(JSONObject entry) {
        String name = entry.optString("file");
        if (name.isEmpty() || name.contains("/") || name.contains("\\")) return null;
        return new File(directory, name);
    }

    void markPlayed(String id) {
        finish(id, true);
    }

    void markFailed(String id) {
        finish(id, false);
    }

    private void finish(String id, boolean completed) {
        synchronized (lock) {
            load();
            JSONObject entry = entries.get(id);
            if (entry == null) return;
            try {
                entry.put("status", completed ? VoiceMessagePolicy.PLAYED : VoiceMessagePolicy.FAILED);
                if (completed) entry.put("played_at_ms", System.currentTimeMillis());
                else entry.put("error", "playback_failed");
                File audio = file(entry);
                if (audio != null) audio.delete();
                save();
            } catch (Exception ignored) { }
        }
    }

    /** Interrupted by a conversation: it stays queued and plays later. */
    void requeue(String id) {
        synchronized (lock) {
            load();
            JSONObject entry = entries.get(id);
            if (entry == null || !VoiceMessagePolicy.PLAYING.equals(entry.optString("status"))) return;
            try {
                entry.put("status", VoiceMessagePolicy.QUEUED);
                save();
            } catch (Exception ignored) { }
        }
    }

    private JSONObject status(JSONObject entry) {
        JSONObject answer = new JSONObject();
        try {
            answer.put("id", entry.optString("id"))
                .put("status", entry.optString("status"))
                .put("expires_at_ms", entry.optLong("expires_at_ms"));
            if (entry.has("played_at_ms")) answer.put("played_at_ms", entry.optLong("played_at_ms"));
            if (entry.has("error")) answer.put("error", entry.optString("error"));
        } catch (Exception ignored) { }
        return answer;
    }

    private void prune(long now) {
        boolean changed = false;
        java.util.Iterator<JSONObject> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            JSONObject entry = iterator.next();
            String state = entry.optString("status");
            long created = entry.optLong("created_ms");
            long expires = entry.optLong("expires_at_ms");
            boolean pending = VoiceMessagePolicy.QUEUED.equals(state) || VoiceMessagePolicy.PLAYING.equals(state);
            if (pending && now >= expires) {
                try {
                    entry.put("status", VoiceMessagePolicy.EXPIRED);
                    File audio = file(entry);
                    if (audio != null) audio.delete();
                    changed = true;
                } catch (Exception ignored) { }
            } else if (!pending && now - created > VoiceMessagePolicy.TTL_MS + 86400000L) {
                iterator.remove();
                changed = true;
            }
        }
        while (entries.size() > 200) {
            java.util.Iterator<String> first = entries.keySet().iterator();
            first.next();
            first.remove();
            changed = true;
        }
        if (changed) {
            try {
                save();
            } catch (Exception ignored) { }
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!index.isFile()) return;
            JSONArray rows = new JSONArray(new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8));
            for (int position = 0; position < rows.length(); position++) {
                JSONObject row = rows.optJSONObject(position);
                if (row == null || !VoiceMessagePolicy.id(row.optString("id"))) continue;
                entries.put(row.optString("id"), row);
            }
        } catch (Exception failure) {
            Log.w("AndromedaMessages", "indice ilegible; se descarta");
            entries.clear();
        }
    }

    private void save() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Sin directorio");
        JSONArray rows = new JSONArray();
        for (JSONObject entry : entries.values()) rows.put(entry);
        File temporary = new File(directory, "index.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(rows.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(index)) {
            Files.write(index.toPath(), rows.toString().getBytes(StandardCharsets.UTF_8));
            temporary.delete();
        }
    }
}
