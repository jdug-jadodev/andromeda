package local.doja.voice;

import android.content.Context;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Voice-only light gate. The model can request this capability, but the
 * current human phrase is parsed here with the same parser as the interface.
 * A fresh spoken confirmation is required before the shared BLE dispatcher is
 * called.
 */
final class LightVoiceChannel {
    static final String ASK = "¿Quieres que ejecute esa orden de luces? Di sí para confirmar o no para descartarla.";
    static final String CANCELLED = "He descartado la orden de luces. No cambié ninguna luz.";
    private static final String UNKNOWN = "No llegó la confirmación de las luces. El resultado es incierto; revísalas antes de pedir otra.";
    private static final Set<String> YES = new HashSet<>(Arrays.asList("si", "si por favor", "si gracias", "si hazlo", "si adelante",
        "si correcto", "correcto", "exacto", "adelante", "hazlo", "de acuerdo", "confirmo", "dale"));
    private static final Set<String> NO = new HashSet<>(Arrays.asList("no", "no gracias", "cancela", "cancelar", "dejalo", "olvidalo"));
    private final Context app;
    private final String session, context;
    private boolean closed, active, armed, confirmationSeen, inFlight;
    private LightCommand command;
    private LightScheduleVoiceCommand schedule;
    private String rejection = "";
    private String origin = "", question = "", confirmation = "";
    private long originSequence, questionSequence, confirmationSequence, heardAt;
    private String resultStatus = "";
    private int resultActions;

    static final class Execution {
        final LightCommand command;
        final LightScheduleVoiceCommand schedule;
        final String rejection;
        final String nonce;
        Execution(LightCommand command, LightScheduleVoiceCommand schedule, String rejection, String nonce) {
            this.command = command; this.schedule = schedule; this.rejection = rejection; this.nonce = nonce;
        }
    }

    LightVoiceChannel(Context context, String session, String contextId) {
        if (context == null || session == null || contextId == null)
            throw new IllegalArgumentException("Canal de luces incompleto");
        this.app = context.getApplicationContext();
        this.session = session; this.context = contextId;
    }

    /** Rebuilds a candidate from the current human phrase; it never writes. */
    synchronized void human(JSONObject event, long now) throws Exception {
        if (closed) throw new IOException("Revoked light channel");
        String turn = AgendaActionChannel.text(event, "turn_id");
        long sequence = AgendaActionChannel.integer(event, "turn_seq");
        long speechEnd = AgendaActionChannel.integer(event, "speech_end_ms");
        String text = AgendaActionChannel.text(event, "text");
        if (sequence < 1 || sequence > 64 || speechEnd <= 0 || speechEnd > now + 100 || now - speechEnd >= 60000
                || text.isEmpty() || text.length() > 250) throw new IOException("Invalid light human turn");
        if (active) {
            if (!armed || turn.equals(question) || sequence <= questionSequence || speechEnd < heardAt) {
                clear();
                return;
            }
            String answer = normalize(text);
            confirmation = turn; confirmationSequence = sequence;
            armed = false;
            int stage = NO.contains(answer) ? 4 : YES.contains(answer) ? 3 : 5;
            confirmationSeen = YES.contains(answer);
            LightVoiceHealth.record(app, stage, 0, 0, 0, 0, active, armed, confirmationSeen);
            return;
        }
        command = null; schedule = null; rejection = ""; confirmationSeen = false; inFlight = false; resultStatus = "";
        origin = turn; originSequence = sequence;
        try { schedule = LightScheduleVoiceParser.parse(text); }
        catch (IllegalArgumentException ignored) { /* Not necessarily a schedule phrase. */ }
        if (schedule == null) try {
            LightCommand candidate = LightCommandParser.parse(text, LightPresetStore.list(app));
            String invalid = LightCommandExecutionPolicy.rejection(candidate);
            if (invalid == null) command = candidate; else rejection = invalid;
        } catch (IllegalArgumentException ignored) {
            // This is only a candidate. A model tool selection or a transport
            // event is still required before anything can happen.
        }
        LightVoiceHealth.record(app, command == null && schedule == null ? 2 : 1, 0, 0, 0, 0,
            active, armed, confirmationSeen);
    }

    synchronized boolean speech(JSONObject event) throws Exception {
        String control = event.optString("timer_control");
        int stage = "light_question".equals(control) ? 6
            : "light_cancelled".equals(control) ? 7
            : "light_result".equals(control) ? 8 : 9;
        LightVoiceHealth.record(app, stage, 0, 0, 0, 0, active, armed, confirmationSeen);
        long count = AgendaActionChannel.integer(event, "actions_executed");
        String expectedTurn = "light_question".equals(control) ? origin : confirmation.isEmpty() ? origin : confirmation;
        if (count < 0 || count > 1 || !expectedTurn.equals(event.optString("turn_id"))) throw new IOException("Unrelated light speech");
        if ("light_question".equals(control)) {
            if (event.optBoolean("offered_more", true) || !event.optString("close_reason").isEmpty()
                    || count != 0 || !ASK.equals(AgendaActionChannel.text(event, "text"))) throw new IOException("Unverified light question");
            active = true;
            question = event.optString("turn_id"); questionSequence = event.optLong("turn_seq"); armed = false; return true;
        }
        if ("light_cancelled".equals(control)) {
            if (count != 0 || !event.optBoolean("offered_more") || !event.optString("close_reason").isEmpty()
                    || !CANCELLED.equals(AgendaActionChannel.text(event, "text"))) throw new IOException("Unverified light cancellation");
            clear(); return true;
        }
        if (!"light_result".equals(control)) {
            if (inFlight) throw new IOException("Missing light result");
            return false;
        }
        String text = AgendaActionChannel.text(event, "text");
        boolean unknown = text.equals(UNKNOWN) || text.equals(ActionCompletionPolicy.text(UNKNOWN, true));
        String expected = expectedText(resultStatus);
        boolean matchesExpected = !resultStatus.isEmpty() && (text.equals(expected)
            || text.equals(ActionCompletionPolicy.text(expected, true)));
        if (!unknown && (resultStatus.isEmpty() || count != resultActions || !matchesExpected))
            throw new IOException("Unverified light response");
        if (unknown && count != 0) throw new IOException("Unknown light result counted as success");
        clear(); return true;
    }

    synchronized void spoken(String turnId, boolean completed, long now) {
        if (!active || !question.equals(turnId)) return;
        if (completed) { armed = true; heardAt = now; }
        else clear();
    }

    synchronized Execution execution(JSONObject event, long now) throws Exception {
        if (closed || !active || !confirmationSeen || !origin.equals(AgendaActionChannel.text(event, "origin_turn_id"))
                || originSequence != AgendaActionChannel.integer(event, "origin_sequence")
                || !confirmation.equals(AgendaActionChannel.text(event, "request_turn_id"))
                || confirmationSequence != AgendaActionChannel.integer(event, "request_sequence")
                || !session.equals(AgendaActionChannel.text(event, "session_id"))
                || !context.equals(AgendaActionChannel.text(event, "context_id")))
            throw new IOException("Light grant is not current");
        String nonce = AgendaActionChannel.text(event, "nonce");
        long before = AgendaActionChannel.integer(event, "execute_before_ms");
        if (!nonce.matches("[0-9a-f]{32}") || before < now || before > now + 60000) throw new IOException("Light grant outside deadline");
        if (inFlight) throw new IOException("Light operation already executing");
        confirmationSeen = false; inFlight = true;
        return new Execution(command, schedule, rejection, nonce);
    }

    synchronized void receipt(JSONObject event) throws Exception {
        if (!inFlight || !session.equals(AgendaActionChannel.text(event, "session_id"))
                || !context.equals(AgendaActionChannel.text(event, "context_id"))
                || !origin.equals(AgendaActionChannel.text(event, "origin_turn_id"))
                || originSequence != AgendaActionChannel.integer(event, "origin_sequence")
                || !confirmation.equals(AgendaActionChannel.text(event, "request_turn_id"))
                || confirmationSequence != AgendaActionChannel.integer(event, "request_sequence"))
            throw new IOException("Unrelated light receipt");
        String status = AgendaActionChannel.text(event, "status");
        if (!status.matches("COMPLETE_UNCONFIRMED|AUTHORIZATION_REQUIRED|UNSUPPORTED_ORDER|DEVICE_NOT_FOUND|TRANSPORT_ERROR|PARTIAL_OR_UNCERTAIN|BUSY|CLOSED|SCHEDULE_SAVED"))
            throw new IOException("Invalid light receipt status");
        resultStatus = status; resultActions = AgendaActionChannel.integer(event, "actions_executed") == 1 ? 1 : 0;
        if (resultActions < 0 || resultActions > 1) throw new IOException("Invalid light receipt count");
        inFlight = false;
    }

    void recordExecution(boolean granted,int status) {
        LightVoiceHealth.record(app, granted ? 10 : 11, status, 0, 0, 0,
            active, armed, confirmationSeen);
    }
    void recordReceipt(int status,int targetCount,int completed,int accepted) {
        LightVoiceHealth.record(app, 12, status, targetCount, completed, accepted,
            active, armed, confirmationSeen);
    }
    void recordFinish(boolean completed,String control) {
        int code = "light_question".equals(control) ? 1
            : "light_result".equals(control) ? 2
            : "light_cancelled".equals(control) ? 3 : 0;
        LightVoiceHealth.record(app, 13, code, 0, completed ? 1 : 0, 0,
            active, armed, confirmationSeen);
    }
    synchronized void cancel() { clear(); }
    synchronized void close() { closed = true; clear(); }

    private void clear() {
        active = armed = confirmationSeen = inFlight = false; command = null; schedule = null; rejection = "";
        origin = question = confirmation = ""; originSequence = questionSequence = confirmationSequence = heardAt = 0;
        resultStatus = ""; resultActions = 0;
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("[¿?!,.;]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String expectedText(String status) {
        if ("SCHEDULE_SAVED".equals(status)) return "Horario de luces actualizado. No cambié ninguna luz.";
        if ("COMPLETE_UNCONFIRMED".equals(status)) return "Orden de luces enviada al Bluetooth. No tengo confirmación visual; revisa las luces.";
        if ("PARTIAL_OR_UNCERTAIN".equals(status)) return "La orden de luces quedó parcial o incierta. No la repetiré; revisa las luces antes de pedir otra.";
        if ("TRANSPORT_ERROR".equals(status)) return "No pude conectar con las luces. No se confirmó ningún cambio.";
        if ("UNSUPPORTED_ORDER".equals(status)) return "Esa orden de luces todavía no está validada. No cambié ninguna luz.";
        return "No pude confirmar el resultado de las luces. Revísalas antes de pedir otra orden.";
    }
}
