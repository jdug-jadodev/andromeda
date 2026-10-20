package local.doja.voice;

import android.os.SystemClock;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Protocol9/10 literal requests, or explicit protocol11 typed forms and continuation. */
final class AgendaActionChannel {
    private final String session, context;
    private long sessionEnd;
    private String turn = "", human = "", nonce = "", eventId = "";
    private long sequence, speechEnd, wall, boot;
    private boolean closed, timerAccepted, volumeAccepted,alarmAccepted;
    private final Set<String> consumed = new HashSet<>();
    private AgendaPermit accepted;
    private AgendaStore.Status receipt;
    private final boolean guided;
    private final boolean continuous;
    private final boolean lifecycle;
    private final AgendaDialogue dialogue;
    private AgendaDialogue.Human latestHuman;
    private AgendaDialogue.Decision dialogueDecision;
    AgendaActionChannel(String session, String context, long end) { this(session, context, end, false); }
    AgendaActionChannel(String session, String context, long end, boolean guided) { this(session,context,end,guided,false); }
    AgendaActionChannel(String session, String context, long end, boolean guided, boolean continuous) {
        this(session,context,end,guided,continuous,false);
    }
    AgendaActionChannel(String session, String context, long end, boolean guided, boolean continuous,boolean lifecycle) {
        this.session = session; this.context = context; sessionEnd = end; this.guided = guided; this.continuous = continuous;
        if (continuous && (!guided || end != 0)) throw new IllegalArgumentException("Continuous agenda mode not negotiated");
        this.lifecycle=lifecycle; dialogue = new AgendaDialogue(continuous ? 64 : 24,lifecycle);
    }
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
        nonce = eventId = ""; timerAccepted = volumeAccepted = alarmAccepted = false;
        if (boot > now + 100 || now - boot > 1500 || Math.abs((nowWall - wall) - (now - boot)) > 1500) throw new IOException("Calendar clock mismatch");
        if (guided) {
            if (continuous) sessionEnd = ContinuousVoicePolicy.actionEnd(integer(event,"action_window_end_ms"),speechEnd,now);
            latestHuman = new AgendaDialogue.Human(turn, sequence, human, speechEnd, wall, boot, now,continuous ? 64 : 24);
            dialogueDecision = dialogue.answer(latestHuman, now);
        }
    }
    synchronized void timerExecution() throws IOException {
        if (closed || accepted != null || volumeAccepted || alarmAccepted || !timerAccepted && consumed.contains(turn) || consumed.size() >= 24 && !timerAccepted)
            throw new IOException("Second mutation in current turn");
        timerAccepted = true; consumed.add(turn);
    }
    synchronized void volumeExecution() throws IOException {
        if (closed || accepted != null || timerAccepted || volumeAccepted || alarmAccepted || consumed.contains(turn) || consumed.size() >= 24)
            throw new IOException("Second mutation in current turn");
        volumeAccepted = true; consumed.add(turn);
    }
    synchronized void alarmExecution() throws IOException {
        if(closed || accepted!=null || timerAccepted || volumeAccepted || alarmAccepted || consumed.contains(turn) || consumed.size()>=24) throw new IOException("Second action in current turn");
        alarmAccepted=true; consumed.add(turn);
    }
    synchronized AgendaPermit execution(JSONObject event, long now, long nowWall) throws Exception {
        if(alarmAccepted) throw new IOException("Alarm operation already reserved this turn");
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
        AgendaEntry.Kind kind = AgendaEntry.Kind.valueOf(text(event, "event_kind"));
        AgendaPermit permit;
        if (event.has("dialogue_origin_turn_id")) {
            if (!guided || dialogueDecision == null || !"proposal".equals(dialogueDecision.kind)) throw new IOException("No current guided agenda fields");
            permit = AgendaPermit.validatedDialogue(id, kind, dialogue, text(event, "dialogue_origin_turn_id"), turn, sequence,
                text(event, "when_text"), text(event, "text"), integer(event, "due_ms"), integer(event, "delay_ms"), before, wall, boot, now, nowWall);
        } else {
            if (guided && dialogueDecision != null) throw new IOException("Guided agenda provenance omitted");
            permit = AgendaPermit.validated(id, kind, human, text(event, "when_text"), text(event, "text"),
                integer(event, "due_ms"), integer(event, "delay_ms"), before, wall, boot, now, nowWall,lifecycle);
        }
        if(lifecycle && (integer(event,"weekday")!=permit.value.weekday || integer(event,"minute_of_day")!=permit.value.minute)) throw new IOException("Recurrence mismatch");
        consumed.add(turn); accepted = permit; nonce = token; eventId = id; return permit;
    }
    synchronized JSONObject result(AgendaPermit permit, AgendaStore.Status status) throws Exception {
        if (accepted != permit) throw new IOException("Unrelated agenda receipt"); receipt = status;
        JSONObject result=new JSONObject().put("action", "agenda_result").put("session_id", session).put("context_id", context)
            .put("event_id", eventId).put("nonce", nonce).put("event_kind", permit.value.kind.name()).put("due_ms", permit.value.due)
            .put("request_turn_id", turn).put("request_sequence", sequence).put("status", status.name());
        if(lifecycle) result.put("weekday",permit.value.weekday).put("minute_of_day",permit.value.minute);
        return result;
    }
    synchronized boolean speech(JSONObject event) throws Exception {
        String control = event.optString("timer_control"); long count = integer(event, "actions_executed");
        if (closed || !turn.equals(event.optString("turn_id")) || count < 0 || count > 1) throw new IOException("Unrelated tools reply");
        if (control.equals("agenda_question") || control.equals("agenda_cancelled")) {
            if (!guided || accepted != null || timerAccepted || volumeAccepted || count != 0 || !event.optString("close_reason").isEmpty())
                throw new IOException("Unverified agenda dialogue");
            if (control.equals("agenda_question")) {
                AgendaEntry.Kind kind = AgendaEntry.Kind.valueOf(text(event, "agenda_kind"));
                if (dialogueDecision == null) dialogueDecision = dialogue.begin(kind, latestHuman);
                if (!"question".equals(dialogueDecision.kind) || dialogueDecision.eventKind != kind || event.optBoolean("offered_more", true)
                        || !dialogueDecision.text.equals(text(event, "text"))) throw new IOException("Unrelated agenda question");
            } else if (dialogueDecision == null || !"cancelled".equals(dialogueDecision.kind) || !event.optBoolean("offered_more")
                    || !ActionCompletionPolicy.text(AgendaDialogue.CANCELLED, true).equals(text(event, "text")))
                throw new IOException("Unrelated agenda cancellation");
            return true;
        }
        if (control.equals("agenda_result")) {
            if (accepted == null || !ActionCompletionPolicy.valid(guided, event.optString("close_reason"), event.optBoolean("offered_more", true))) throw new IOException("Unverified agenda completion");
            String value = text(event, "text");
            boolean uncertain = count == 0 && value.equals(ActionCompletionPolicy.text(resultText(AgendaStore.Status.UNKNOWN, accepted.value, guided), guided));
            if (!uncertain && (receipt == null || count != (receipt == AgendaStore.Status.CREATED ? 1 : 0) || !value.equals(ActionCompletionPolicy.text(resultText(receipt, accepted.value, guided), guided))))
                throw new IOException("No matching Android agenda result");
            return true;
        }
        if (control.equals("web")) {
            if (accepted != null || timerAccepted || volumeAccepted || count != 0 || !event.optString("close_reason").isEmpty() || !event.optBoolean("offered_more")) throw new IOException("Web cannot authorize/close an action");
            WebSources.remember(WebSources.validate(human, event), SystemClock.elapsedRealtime()); return true;
        }
        if (control.equals("web_followup")) {
            if (accepted != null || timerAccepted || volumeAccepted || count != 0 || !event.optString("close_reason").isEmpty()
                    || !event.optBoolean("offered_more") || event.has("web_answer")) throw new IOException("Follow-up cannot authorize an action");
            return true;
        }
        if (accepted != null || volumeAccepted) throw new IOException("Action result omitted");
        return false; // Existing timer channel validates timer/ordinary responses.
    }
    static String resultText(AgendaStore.Status status, AgendaRequestGuard.Value value) {
        return resultText(status, value, false);
    }
    static String resultText(AgendaStore.Status status, AgendaRequestGuard.Value value, boolean detailed) {
        return AgendaSpeechPolicy.text(status.name(), value, detailed);
    }
    synchronized void spoken(String turn, boolean completed) { if (guided) dialogue.spoken(turn, completed); }
    synchronized void cancelDialogue() { dialogue.clear(); dialogueDecision=null; }
    synchronized void close() { closed = true; dialogue.clear(); latestHuman = null; dialogueDecision = null; if (accepted != null) accepted.revoke(); }
}
