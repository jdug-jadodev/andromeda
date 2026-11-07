package local.doja.voice;

import org.json.JSONObject;

/**
 * Puente en proceso entre el gateway HTTP y la sesion de voz viva.
 *
 * Una orden remota solo se acepta si el residente esta escuchando y no hay una
 * conversacion local en curso. El id es idempotente: repetir la misma orden no
 * la ejecuta dos veces y devuelve la misma respuesta mientras siga en cache.
 *
 * No conoce Android ni red: el gateway lo llama desde su worker y VoiceService
 * completa la respuesta desde su hilo de eventos.
 */
final class RemoteCommandBus {
    interface Session {
        boolean ready();
        boolean busy();
        void send(JSONObject frame) throws Exception;
    }

    static final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final int MAX_ENTRIES = 64;
    private static final long UNANSWERED_MS = 60000L;

    private static final class Entry {
        final Object lock = new Object();
        final long created = System.currentTimeMillis();
        String body;
        long at;
        boolean sent;
    }

    private static final java.util.LinkedHashMap<String, Entry> entries = new java.util.LinkedHashMap<>();
    private static final Object lock = new Object();
    private static volatile Session session;

    static void attach(Session value) {
        synchronized (lock) {
            session = value;
            entries.clear();
        }
    }

    static void detach(Session value) {
        synchronized (lock) {
            if (session == value) session = null;
            entries.clear();
        }
    }

    static boolean available() {
        Session value = session;
        return value != null && value.ready();
    }

    static Reply submit(JSONObject frame, long waitMs) {
        String id = frame.optString("id", "");
        if (!RemoteVoicePolicy.id(id)) {
            return new Reply(400, error("invalid_request", "Orden remota invalida.", false, 0));
        }
        Entry entry;
        synchronized (lock) {
            prune();
            entry = entries.get(id);
            if (entry != null && entry.body != null) return reply(entry.body);
            Session value = session;
            if (value == null || !value.ready()) {
                return new Reply(503, error("offline", "Andromeda no esta escuchando.", true, 3000));
            }
            if (value.busy()) {
                return new Reply(409, error("busy", "Hay una conversacion en casa; reintenta.", true, 3000));
            }
            if (entry == null) {
                entry = new Entry();
                entries.put(id, entry);
            }
            if (!entry.sent) {
                entry.sent = true;
                try {
                    value.send(frame);
                } catch (Exception failure) {
                    entries.remove(id);
                    return new Reply(503, error("offline", "Andromeda no esta escuchando.", true, 3000));
                }
            }
        }
        return waitFor(entry, waitMs);
    }

    /**
     * Waits for a spoken order without sending anything: the audio path already
     * owns the turn. The id is the same the resident will answer with.
     */
    static Reply await(String id, long waitMs) {
        if (!RemoteVoicePolicy.id(id)) {
            return new Reply(400, error("invalid_request", "Orden remota invalida.", false, 0));
        }
        Entry entry;
        synchronized (lock) {
            prune();
            entry = entries.get(id);
            if (entry != null && entry.body != null) return reply(entry.body);
            if (entry == null) {
                entry = new Entry();
                entries.put(id, entry);
            }
            entry.sent = true;
        }
        return waitFor(entry, waitMs);
    }

    private static Reply waitFor(Entry entry, long waitMs) {
        synchronized (entry.lock) {
            long deadline = System.currentTimeMillis() + Math.max(1000L, waitMs);
            while (entry.body == null) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0L) break;
                try {
                    entry.lock.wait(left);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (entry.body != null) return reply(entry.body);
        }
        return new Reply(504, error("timeout", "La casa no respondio a tiempo.", true, 3000));
    }

    /** VoiceService entrega aqui el say remoto o el error del residente. */
    static void complete(String id, String body) {
        if (!RemoteVoicePolicy.id(id) || body == null) return;
        synchronized (lock) {
            prune();
            Entry entry = entries.get(id);
            if (entry == null) {
                entry = new Entry();
                entries.put(id, entry);
            }
            synchronized (entry.lock) {
                entry.body = body;
                entry.at = System.currentTimeMillis();
                entry.lock.notifyAll();
            }
        }
    }

    static void fail(String id, int status, String code, String message) {
        complete(id, error(code, message, true, 3000));
    }

    private static Reply reply(String body) {
        try {
            JSONObject payload = new JSONObject(body);
            return new Reply(status(payload.optString("error", "")), body);
        } catch (Exception malformed) {
            return new Reply(500, error("internal", "Respuesta remota invalida.", false, 0));
        }
    }

    private static int status(String code) {
        if (code == null || code.isEmpty()) return 200;
        if ("busy".equals(code)) return 409;
        if ("forbidden".equals(code)) return 403;
        if ("invalid_request".equals(code)) return 400;
        if ("offline".equals(code)) return 503;
        if ("timeout".equals(code)) return 504;
        return 500;
    }

    private static String error(String code, String message, boolean retryable, long retryAfterMs) {
        try {
            JSONObject body = new JSONObject().put("error", code).put("message", message)
                .put("retryable", retryable);
            if (retryAfterMs > 0) body.put("retry_after_ms", retryAfterMs);
            return body.toString();
        } catch (Exception impossible) {
            return "{\"error\":\"internal\",\"message\":\"Error interno.\",\"retryable\":false}";
        }
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        java.util.Iterator<java.util.Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            boolean expired = entry.body != null
                ? now - entry.at >= RemoteVoicePolicy.REPLY_CACHE_MS
                : now - entry.created >= UNANSWERED_MS;
            if (expired) iterator.remove();
        }
        while (entries.size() > MAX_ENTRIES) {
            java.util.Iterator<String> first = entries.keySet().iterator();
            first.next();
            first.remove();
        }
    }

    private RemoteCommandBus() { }
}
