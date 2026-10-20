package local.doja.voice;

import java.io.IOException;
import org.json.*;

/** Typed timer-trial wire adapter. Never accepts legacy tool events or model code. */
final class TimerActionChannel {
    static final class Execution {
        final TimerActionPolicy.Grant grant;
        final TimerActionPolicy.Permit permit;
        Execution(TimerActionPolicy.Grant grant, TimerActionPolicy.Permit permit) { this.grant = grant; this.permit = permit; }
    }
    private final TimerActionPolicy policy;
    private final String session, context;
    private long sessionEnd;
    private String turn = "", human = "", executedTurn = "";
    private long sequence, speechEnd;
    private Execution accepted;
    private TimerCreation.Receipt receipt;
    private final boolean continuing;
    private final boolean continuous;
    TimerActionChannel(String session, String context, long sessionEnd) {
        this(session, context, sessionEnd, false);
    }
    TimerActionChannel(String session, String context, long sessionEnd, boolean continuing) {
        this(session,context,sessionEnd,continuing,false);
    }
    TimerActionChannel(String session, String context, long sessionEnd, boolean continuing, boolean continuous) {
        this.session = session; this.context = context; this.sessionEnd = sessionEnd;
        this.continuing = continuing; this.continuous = continuous;
        if (continuous && (!continuing || sessionEnd != 0)) throw new IllegalArgumentException("Continuous timer mode not negotiated");
        policy = new TimerActionPolicy(session, context);
    }
    private static long integer(JSONObject data, String key) throws Exception {
        Object value = data.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IOException("Campo entero requerido");
        return ((Number)value).longValue();
    }
    private static String text(JSONObject data, String key) throws Exception {
        Object value = data.get(key);
        if (!(value instanceof String)) throw new IOException("Campo de texto requerido");
        return (String)value;
    }
    private static TimerActionPolicy.Proposal proposal(JSONObject data) throws Exception {
        long seconds = integer(data, "seconds");
        if (seconds < 1 || seconds > 86400) throw new IOException("Duración fuera de rango");
        return new TimerActionPolicy.Proposal(text(data, "proposal_id"), text(data, "timer_id"), text(data, "nonce"),
            text(data, "session_id"), text(data, "context_id"), text(data, "origin_turn_id"), integer(data, "origin_sequence"),
            (int)seconds, integer(data, "expires_ms"));
    }
    synchronized void human(JSONObject event) throws Exception {
        human(event, android.os.SystemClock.elapsedRealtime());
    }
    synchronized void human(JSONObject event, long now) throws Exception {
        turn = text(event, "turn_id"); sequence = integer(event, "turn_seq"); human = text(event, "text");
        speechEnd = integer(event, "speech_end_ms"); receipt = null; executedTurn = "";
        if (continuous) sessionEnd = ContinuousVoicePolicy.actionEnd(integer(event,"action_window_end_ms"),speechEnd,now);
        policy.cancelProposal(); accepted = null;
    }
    synchronized Execution execution(JSONObject event, long now) throws Exception {
        TimerActionPolicy.Proposal p = proposal(event.getJSONObject("proposal"));
        String request = text(event, "request_turn_id");
        long seq = integer(event, "request_sequence"), end = integer(event, "speech_end_ms"), before = integer(event, "execute_before_ms");
        if (turn.equals(executedTurn)) {
            if (accepted != null && TimerActionPolicy.same(accepted.grant.proposal, p) && turn.equals(request)
                    && accepted.grant.sequence == seq && accepted.grant.speechEnd == end && accepted.grant.executeBefore == before) return null;
            throw new IOException("Otra ejecución en el mismo turno");
        }
        if (!session.equals(event.optString("session_id")) || !context.equals(event.optString("context_id"))
                || end != speechEnd || before > sessionEnd - 5000) throw new IOException("Autorización de otra sesión/plazo");
        TimerActionPolicy.Grant g = new TimerActionPolicy.Grant(p, request, seq, end, before);
        TimerActionPolicy.Permit permit = policy.claimRequest(g, human, turn, sequence, now);
        if (permit == null) throw new IOException("No hay petición actual completa y válida de ese temporizador");
        executedTurn = turn;
        accepted = new Execution(g, permit); return accepted;
    }
    synchronized JSONObject result(Execution execution, TimerCreation.Receipt value) throws Exception {
        TimerActionPolicy.Proposal p = execution.grant.proposal;
        if (!executedTurn.equals(execution.grant.turn) || !p.timerId.equals(value.id) || p.seconds != value.seconds)
            throw new IOException("Resultado de otro timer");
        receipt = value;
        return new JSONObject().put("action", "timer_result").put("session_id", session).put("context_id", context)
            .put("proposal_id", p.id).put("timer_id", p.timerId).put("nonce", p.nonce).put("seconds", p.seconds)
            .put("request_turn_id", execution.grant.turn).put("request_sequence", execution.grant.sequence)
            .put("status", value.status.name());
    }
    synchronized void validateSpeech(JSONObject event) throws Exception {
        String control = event.optString("timer_control"), value = text(event, "text");
        long count = integer(event, "actions_executed");
        if (count < 0 || count > 1 || !turn.equals(event.optString("turn_id"))) throw new IOException("Respuesta de otro turno");
        if ("result".equals(control)) {
            boolean timeout = value.equals(ActionCompletionPolicy.text("No llegó la confirmación de Android. El resultado es incierto; revisa los temporizadores en la pantalla antes de pedir otro.", continuing));
            if (!executedTurn.equals(turn) || (timeout ? count != 0 : receipt == null
                    || count != (receipt.status == TimerCreation.Status.CREATED ? 1 : 0) || !ActionCompletionPolicy.text(resultText(receipt), continuing).equals(value)))
                throw new IOException("Confirmación sin resultado Android real");
            if (!ActionCompletionPolicy.valid(continuing, event.optString("close_reason"), event.optBoolean("offered_more", true)))
                throw new IOException("Finalización de acción no negociada");
        } else if (count != 0 || !executedTurn.isEmpty()
                || "action_result".equals(event.optString("close_reason")) || !(control.isEmpty() || "rejected".equals(control)))
            throw new IOException("Confirmación de acción no permitida");
    }
    static String resultText(TimerCreation.Receipt receipt) {
        switch (receipt.status) {
            case CREATED: return "Temporizador de " + receipt.seconds + " segundos activado.";
            case DUPLICATE: return "Esa orden ya tenía un registro; no la repetí. Revisa los temporizadores en la pantalla.";
            case REJECTED: return "Android rechazó la creación; no he confirmado ningún temporizador nuevo.";
            case FAILED: return "Android informó que la creación falló y retiró ese intento.";
            default: return "No pude confirmar el resultado. Revisa los temporizadores en la pantalla antes de pedir otro.";
        }
    }
    synchronized void close() { policy.revoke(); accepted = null; }
}
