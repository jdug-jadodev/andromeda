package local.doja.voice;

import java.io.IOException;
import org.json.*;
import java.util.*;

/** Context transfer is wire data only. The model still receives native chat roles. */
final class ConversationChannel {
    final ConversationMemory memory;
    ConversationMemory.Lease lease;
    final String sessionId;
    String contextId, newId;
    boolean voiceEntry, cancelled;
    final Map<String, Integer> candidates = new HashMap<>();
    ConversationChannel(ConversationMemory memory, ConversationMemory.Lease lease, JSONObject ready) throws Exception {
        this.memory = memory; this.lease = lease; this.sessionId = ready.getString("session_id");
        if (!sessionId.matches("[0-9a-f]{32}") || !lease.conversationId.equals(ready.optString("context_id")))
            throw new IOException("Identidad de contexto incompatible");
        contextId = lease.conversationId;
    }
    ConversationChannel(ConversationMemory memory, ConversationMemory.Snapshot fresh,
            List<ConversationMemory.Snapshot> catalog, JSONObject ready) throws Exception {
        this.memory = memory; sessionId = ready.getString("session_id");
        contextId = newId = fresh.conversationId; voiceEntry = true;
        if (!sessionId.matches("[0-9a-f]{32}") || !contextId.equals(ready.optString("context_id")) || !ready.optBoolean("voice_entry"))
            throw new IOException("Entrada de voz incompatible");
        for (ConversationMemory.Snapshot item : catalog) candidates.put(item.conversationId, item.turns.size());
    }
    synchronized void select(JSONObject event, long now) throws Exception {
        if (cancelled || !voiceEntry || lease != null || !sessionId.equals(event.optString("session_id"))
                || !contextId.equals(event.optString("previous_context_id"))) throw new IOException("Selección de otra sesión o duplicada");
        String id = event.getString("context_id"), kind = event.getString("selection");
        boolean isNew = "new".equals(kind);
        int expected = isNew ? 0 : candidates.getOrDefault(id, -1);
        if (!(isNew && newId.equals(id) || "resume".equals(kind) && expected >= 0)
                || expected != event.optInt("restored_turns", -1)) throw new IOException("Charla no disponible en el catálogo enviado");
        ConversationMemory.Lease chosen = memory.acquireExact(id, isNew, now);
        if (memory.snapshot(chosen, now).turns.size() != expected) {
            memory.release(chosen); throw new IOException("El contexto cambió; vuelve a abrir la charla");
        }
        lease = chosen; contextId = id;
    }
    static JSONObject encode(ConversationMemory.Snapshot snapshot) throws Exception {
        JSONArray turns = new JSONArray();
        for (ConversationMemory.Turn turn : snapshot.turns) {
            JSONObject item = new JSONObject().put("id", turn.id).put("user", turn.user).put("human_ms", turn.humanMs);
            if (turn.assistant != null) item.put("assistant", turn.assistant);
            turns.put(item);
        }
        return new JSONObject().put("version", 1).put("id", snapshot.conversationId)
            .put("last_human_ms", snapshot.lastHumanMs).put("turns", turns);
    }
    synchronized void verify(JSONObject event) throws IOException {
        if (cancelled || !sessionId.equals(event.optString("session_id")) || !contextId.equals(event.optString("context_id")))
            throw new IOException("Evento de otra conversación o sesión");
    }
    void human(JSONObject event, long now) throws Exception {
        verify(event);
        if (event.optBoolean("administrative")) {
            if (!voiceEntry || lease != null) throw new IOException("Control de entrada fuera de estado");
            return;
        }
        if (lease == null) throw new IOException("No se seleccionó una conversación");
        if (!memory.human(lease, event.getString("turn_id"), event.getString("text"), event.getLong("speech_end_ms"), now))
            throw new IOException("Turno de contexto duplicado, cancelado o inválido");
    }
    synchronized boolean answer(String turnId, String text, long now) {
        return !cancelled && (lease == null ? voiceEntry : memory.answer(lease, turnId, text, now));
    }
    synchronized boolean end(String turnId, long now) { return !cancelled && (lease == null ? voiceEntry : memory.end(lease, turnId, now)); }
    synchronized void close() { cancelled = true; memory.release(lease); }
}
