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
    private boolean remoteStore;
    final Map<String, Integer> candidates = new HashMap<>();
    private ContinuousVoicePolicy.Reset pendingReset;
    private ConversationMemory.Snapshot pendingSnapshot;
    private List<ConversationMemory.Snapshot> pendingCatalog;
    ConversationChannel(ConversationMemory memory, ConversationMemory.Lease lease, JSONObject ready) throws Exception {
        this.memory = memory; this.lease = lease; this.sessionId = ready.getString("session_id");
        if (!sessionId.matches("[0-9a-f]{32}") || !lease.conversationId.equals(ready.optString("context_id")))
            throw new IOException("Identidad de contexto incompatible");
        contextId = lease.conversationId; remoteStore = "sqlite_debian_v1".equals(ready.optString("memory_store"));
    }
    ConversationChannel(ConversationMemory memory, ConversationMemory.Snapshot fresh,
            List<ConversationMemory.Snapshot> catalog, JSONObject ready) throws Exception {
        this.memory = memory; sessionId = ready.getString("session_id");
        contextId = newId = fresh.conversationId; voiceEntry = true;
        if (!sessionId.matches("[0-9a-f]{32}") || !contextId.equals(ready.optString("context_id")) || !ready.optBoolean("voice_entry"))
            throw new IOException("Entrada de voz incompatible");
        remoteStore = "sqlite_debian_v1".equals(ready.optString("memory_store"));
        if (remoteStore) {
            memory.clearAll();
            JSONArray metadata = ready.optJSONArray("catalog");
            if (metadata == null || metadata.length() > ConversationMemory.MAX_CONVERSATIONS)
                throw new IOException("Catálogo de memoria inválido");
            for (int i = 0; i < metadata.length(); i++) {
                JSONObject item = metadata.getJSONObject(i); String id = item.optString("id");
                int turns = item.optInt("turns", -1);
                if (item.length()!=4 || !(item.opt("closed") instanceof Boolean)
                        || !id.matches("[0-9a-f]{32}") || turns < 0 || turns > ConversationMemory.MAX_TURNS
                        || candidates.put(id, turns) != null) throw new IOException("Metadatos de charla inválidos");
                long remaining = item.optLong("remaining_ms", -1);
                if (remaining < 0 || remaining > ConversationMemory.INACTIVITY_MS)
                    throw new IOException("Caducidad de metadatos inválida");
                memory.registerMetadata(id, turns, remaining, android.os.SystemClock.elapsedRealtime());
            }
        } else for (ConversationMemory.Snapshot item : catalog) candidates.put(item.conversationId, item.turns.size());
    }
    synchronized void select(JSONObject event, long now) throws Exception {
        if (cancelled || !voiceEntry || lease != null || !sessionId.equals(event.optString("session_id"))
                || !contextId.equals(event.optString("previous_context_id"))) throw new IOException("Selección de otra sesión o duplicada");
        String id = event.getString("context_id"), kind = event.getString("selection");
        boolean isNew = "new".equals(kind);
        int expected = isNew ? 0 : candidates.getOrDefault(id, -1);
        if (!(isNew && newId.equals(id) || "resume".equals(kind) && expected >= 0)
                || expected != event.optInt("restored_turns", -1)) throw new IOException("Charla no disponible en el catálogo enviado");
        if (remoteStore) {
            try { lease = memory.acquireExact(id, isNew, now); }
            catch (IllegalStateException invalid) { throw new IOException("No se pudo actualizar el índice local", invalid); }
            contextId = id;
            return;
        }
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
            if (turn.assistant != null) item.put(turn.webFollowup ? "web_followup" : "assistant", turn.assistant);
            if (turn.webAnswer != null) item.put("web_answer", turn.webAnswer);
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
        if (remoteStore) return;
        if (lease == null) throw new IOException("No se seleccionó una conversación");
        if (!memory.human(lease, event.getString("turn_id"), event.getString("text"), event.getLong("speech_end_ms"), now))
            throw new IOException("Turno de contexto duplicado, cancelado o inválido");
    }
    synchronized boolean answer(String turnId, String text, long now) {
        if (cancelled) return false;
        if (remoteStore) return text != null && !text.trim().isEmpty() && text.length() <= 700;
        return lease == null ? voiceEntry : memory.answer(lease, turnId, text, now);
    }
    synchronized boolean webAnswer(String turnId, String text, long now) {
        return !cancelled && (remoteStore ? text != null && !text.trim().isEmpty() && text.length() <= 700
                : lease != null && memory.webAnswer(lease, turnId, text, now));
    }
    synchronized boolean webFollowup(String turnId, String text, long now) {
        return !cancelled && (remoteStore ? text != null && !text.trim().isEmpty() && text.length() <= 700
                : lease != null && memory.answer(lease, turnId, text, now, true));
    }
    synchronized boolean end(String turnId, long now) { return !cancelled && (remoteStore || (lease == null ? voiceEntry : memory.end(lease, turnId, now))); }
    synchronized JSONObject prepareContinuousReset(JSONObject event, long now, boolean dialogueClosed) throws Exception {
        verify(event);
        if (pendingReset != null) throw new IOException("Cambio de contexto ya pendiente");
        String reason = AgendaActionChannel.text(event,"reason");
        if ("dialogue_closed".equals(reason) != dialogueClosed) throw new IOException("Cierre de diálogo no verificado");
        ContinuousVoicePolicy.Reset reset = new ContinuousVoicePolicy.Reset(sessionId,contextId,AgendaActionChannel.text(event,"reset_nonce"),reason,now);
        boolean checkpoint = "turn_checkpoint".equals(reason);
        ConversationMemory.Snapshot snapshot;
        if (checkpoint) {
            if (lease == null) throw new IOException("No hay charla para conservar");
            if (remoteStore) {
                ConversationMemory.Info info = null;
                for (ConversationMemory.Info item : memory.list(now)) if (item.id.equals(contextId)) info = item;
                long retained = AgendaActionChannel.integer(event,"retained_turns");
                if (info == null || retained < 0 || retained > ConversationMemory.MAX_TURNS)
                    throw new IOException("Metadatos de checkpoint incompatibles");
                snapshot = new ConversationMemory.Snapshot(contextId, event.getLong("last_human_ms"), Collections.emptyList());
            } else {
                snapshot = memory.snapshot(lease,now);
                if (AgendaActionChannel.integer(event,"retained_turns") != snapshot.turns.size()
                        || AgendaActionChannel.integer(event,"last_human_ms") != snapshot.lastHumanMs) throw new IOException("El contexto difiere del checkpoint");
            }
            memory.release(lease); lease = null;
            pendingCatalog = Collections.emptyList();
        } else {
            if ("human_inactivity".equals(reason) && lease != null && !memory.snapshot(lease,now).turns.isEmpty())
                throw new IOException("El contexto humano no ha caducado");
            memory.release(lease); lease = null;
            pendingCatalog = remoteStore ? Collections.emptyList() : memory.catalogForContinuous(now);
            snapshot = ConversationMemory.emptySnapshot();
        }
        pendingReset = reset; pendingSnapshot = snapshot;
        if (remoteStore) return new JSONObject().put("action","voice_context").put("reset_nonce",reset.nonce)
            .put("previous_session_id",sessionId).put("previous_context_id",contextId)
            .put("context_id",snapshot.conversationId).put("voice_entry",!checkpoint);
        JSONArray catalog = new JSONArray(); for (ConversationMemory.Snapshot item : pendingCatalog) catalog.put(encode(item));
        return new JSONObject().put("action","voice_context").put("reset_nonce",reset.nonce)
            .put("previous_session_id",sessionId).put("previous_context_id",contextId)
            .put("context",encode(snapshot)).put("catalog",catalog).put("voice_entry",!checkpoint);
    }
    synchronized ConversationChannel finishContinuousReset(JSONObject event, long now) throws Exception {
        if (cancelled || pendingReset == null || pendingSnapshot == null) throw new IOException("Cambio de contexto no solicitado");
        boolean voiceEntry = !"turn_checkpoint".equals(pendingReset.reason);
        if (!(event.get("voice_entry") instanceof Boolean) || !(event.get("audio_yielded") instanceof Boolean) || event.getBoolean("voice_entry") != voiceEntry
                || !remoteStore && AgendaActionChannel.integer(event,"restored_turns") != pendingSnapshot.turns.size()
                || remoteStore && !"sqlite_debian_v1".equals(event.optString("memory_store"))
                || !ContinuousVoicePolicy.VOICE.equals(event.optString("voice_lifetime_policy"))
                || !ContinuousVoicePolicy.RESET.equals(event.optString("dialogue_reset_policy"))) throw new IOException("Cambio de contexto incompatible");
        pendingReset.claim(AgendaActionChannel.text(event,"previous_session_id"),AgendaActionChannel.text(event,"previous_context_id"),
            AgendaActionChannel.text(event,"reset_nonce"),AgendaActionChannel.text(event,"session_id"),AgendaActionChannel.text(event,"context_id"),pendingSnapshot.conversationId,now);
        ConversationChannel next;
        if (remoteStore && voiceEntry) memory.clearAll();
        if (voiceEntry) next = new ConversationChannel(memory,pendingSnapshot,pendingCatalog,event);
        else {
            ConversationMemory.Lease nextLease = memory.acquireExact(pendingSnapshot.conversationId,false,now);
            if (!remoteStore && memory.snapshot(nextLease,now).turns.size() != pendingSnapshot.turns.size()) {
                memory.release(nextLease); throw new IOException("El contexto cambió durante el relevo");
            }
            next = new ConversationChannel(memory,nextLease,event);
        }
        cancelled = true; pendingReset = null; pendingSnapshot = null; pendingCatalog = null;
        return next;
    }
    synchronized void close() { cancelled = true; pendingReset = null; pendingSnapshot = null; pendingCatalog = null; memory.release(lease); }
}
