package local.doja.voice;

import android.os.SystemClock;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/** Protocol 9 current-turn authorization and exact Android-receipt speech validation. */
final class AgendaActionChannel {
    private final String session, context;
    private final long sessionEnd;
    private String turn = "", human = "", nonce = "", eventId = "";
    private long sequence, speechEnd, wall, boot;
    private boolean closed, timerAccepted;
    private final Set<String> consumed = new HashSet<>();
    private AgendaPermit accepted;
    private AgendaStore.Status receipt;
    AgendaActionChannel(String session, String context, long end) { this.session = session; this.context = context; sessionEnd = end; }
    static long integer(JSONObject event, String key) throws Exception {
        Object value = event.get(key);
        if (!(value instanceof Integer || value instanceof Long)) throw new IOException("Integer required");
        return ((Number)value).longValue();
    }
    static String text(JSONObject event, String key) throws Exception {
        Object value = event.get(key); if (!(value instanceof String)) throw new IOException("String required"); return (String)value;
    }
    synchronized void human(JSONObject event, long now, long nowWall) throws Exception {
        if (closed) throw new IOException("Revoked tools channel");
        turn = text(event, "turn_id"); human = text(event, "text"); sequence = integer(event, "turn_seq"); speechEnd = integer(event, "speech_end_ms");
        wall = integer(event, "calendar_anchor_ms"); boot = integer(event, "calendar_boot_ms"); accepted = null; receipt = null;
        nonce = eventId = ""; timerAccepted = false;
        if (boot > now + 100 || now - boot > 1500 || Math.abs((nowWall - wall) - (now - boot)) > 1500) throw new IOException("Calendar clock mismatch");
    }
    synchronized void timerExecution() throws IOException {
        if (closed || accepted != null || !timerAccepted && consumed.contains(turn) || consumed.size() >= 24 && !timerAccepted)
            throw new IOException("Second mutation in current turn");
        timerAccepted = true; consumed.add(turn);
    }
    synchronized AgendaPermit execution(JSONObject event, long now, long nowWall) throws Exception {
        if (closed || !session.equals(text(event, "session_id")) || !context.equals(text(event, "context_id"))
                || !turn.equals(text(event, "request_turn_id")) || sequence != integer(event, "request_sequence")
                || speechEnd != integer(event, "speech_end_ms") || wall != integer(event, "calendar_anchor_ms") || boot != integer(event, "calendar_boot_ms"))
            throw new IOException("Agenda grant is not current");
        String id = text(event, "event_id"), token = text(event, "nonce");
        if (!token.matches("[0-9a-f]{32}")) throw new IOException("Invalid nonce");
        if (consumed.contains(turn)) {
            if (id.equals(eventId) && token.equals(nonce) && accepted != null && accepted.value.due == integer(event, "due_ms")) return null;
            throw new IOException("Second action in one turn");
        }
        long before = integer(event, "execute_before_ms");
        if (consumed.size() >= 24 || before > speechEnd + 26500 || before > sessionEnd - 5000 || !AgendaEntry.ZONE.equals(text(event, "zone")))
            throw new IOException("Agenda grant outside deadline");
        AgendaPermit permit = AgendaPermit.validated(id, AgendaEntry.Kind.valueOf(text(event, "event_kind")), human, text(event, "when_text"), text(event, "text"),
            integer(event, "due_ms"), integer(event, "delay_ms"), before, wall, boot, now, nowWall);
        consumed.add(turn); accepted = permit; nonce = token; eventId = id; return permit;
    }
    synchronized JSONObject result(AgendaPermit permit, AgendaStore.Status status) throws Exception {
        if (accepted != permit) throw new IOException("Unrelated agenda receipt"); receipt = status;
        return new JSONObject().put("action", "agenda_result").put("session_id", session).put("context_id", context)
            .put("event_id", eventId).put("nonce", nonce).put("event_kind", permit.value.kind.name()).put("due_ms", permit.value.due)
            .put("request_turn_id", turn).put("request_sequence", sequence).put("status", status.name());
    }
    synchronized boolean speech(JSONObject event) throws Exception {
        String control = event.optString("timer_control"); long count = integer(event, "actions_executed");
        if (closed || !turn.equals(event.optString("turn_id")) || count < 0 || count > 1) throw new IOException("Unrelated tools reply");
        if (control.equals("agenda_result")) {
            if (accepted == null || !"action_result".equals(event.optString("close_reason")) || event.optBoolean("offered_more", true)) throw new IOException("Unverified agenda closure");
            String value = text(event, "text");
            boolean uncertain = count == 0 && value.equals(resultText(AgendaStore.Status.UNKNOWN, accepted.value));
            if (!uncertain && (receipt == null || count != (receipt == AgendaStore.Status.CREATED ? 1 : 0) || !value.equals(resultText(receipt, accepted.value))))
                throw new IOException("No matching Android agenda result");
            return true;
        }
        if (control.equals("web")) {
            if (accepted != null || timerAccepted || count != 0 || !event.optString("close_reason").isEmpty() || !event.optBoolean("offered_more")) throw new IOException("Web cannot authorize/close an action");
            WebSources.remember(WebSources.validate(human, event), SystemClock.elapsedRealtime()); return true;
        }
        if (accepted != null) throw new IOException("Agenda result omitted");
        return false; // Existing timer channel validates timer/ordinary responses.
    }
    static String resultText(AgendaStore.Status status, AgendaRequestGuard.Value value) {
        if (status == AgendaStore.Status.CREATED) {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", new Locale("es", "CO")); format.setTimeZone(TimeZone.getTimeZone(AgendaEntry.ZONE));
            return (value.kind == AgendaEntry.Kind.ALARM ? "Alarma programada" : "Recordatorio programado") + " para el " + format.format(new Date(value.due)) + ", hora de Bogotá.";
        }
        switch (status) {
            case DUPLICATE: return "Esa orden ya tenía un registro. No la repetí; revisa la agenda manual.";
            case REJECTED: return "Android rechazó la programación. No he confirmado un aviso nuevo.";
            case FAILED: return "La programación falló y se retiró ese intento. Revisa la agenda manual.";
            default: return "No pude confirmar el resultado. Revisa Alarmas o Recordatorios antes de pedirlo otra vez.";
        }
    }
    synchronized void close() { closed = true; if (accepted != null) accepted.revoke(); }
}
