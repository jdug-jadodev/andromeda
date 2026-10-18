package local.doja.voice;

import java.io.IOException;
import org.json.*;

/** Context transfer is wire data only. The model still receives native chat roles. */
final class ConversationChannel {
    final ConversationMemory memory;
    final ConversationMemory.Lease lease;
    final String sessionId;
    ConversationChannel(ConversationMemory memory, ConversationMemory.Lease lease, JSONObject ready) throws Exception {
        this.memory = memory; this.lease = lease; this.sessionId = ready.getString("session_id");
        if (!sessionId.matches("[0-9a-f]{32}") || !lease.conversationId.equals(ready.optString("context_id")))
            throw new IOException("Identidad de contexto incompatible");
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
    void verify(JSONObject event) throws IOException {
        if (!sessionId.equals(event.optString("session_id")) || !lease.conversationId.equals(event.optString("context_id")))
            throw new IOException("Evento de otra conversación o sesión");
    }
    void human(JSONObject event, long now) throws Exception {
        verify(event);
        if (!memory.human(lease, event.getString("turn_id"), event.getString("text"), event.getLong("speech_end_ms"), now))
            throw new IOException("Turno de contexto duplicado, cancelado o inválido");
    }
    boolean answer(String turnId, String text, long now) { return memory.answer(lease, turnId, text, now); }
    boolean end(String turnId, long now) { return memory.end(lease, turnId, now); }
    void close() { memory.release(lease); }
}
