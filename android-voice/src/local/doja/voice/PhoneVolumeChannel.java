package local.doja.voice;

import java.io.IOException;
import org.json.JSONObject;

/** Protocol10 typed grants/receipts; no model-supplied Android stream or shell. */
final class PhoneVolumeChannel {
    private final PhoneVolumePolicy policy;
    private final String session, context;
    private long sessionEnd;
    private String turn = "", human = "";
    private long sequence, speechEnd;
    private PhoneVolumePolicy.Permit accepted;
    private PhoneVolumePolicy.Receipt receipt;
    private boolean closed;
    private final boolean continuing;
    private final boolean continuous;
    PhoneVolumeChannel(String session, String context, long end) {
        this(session, context, end, false);
    }
    PhoneVolumeChannel(String session, String context, long end, boolean continuing) {
        this(session,context,end,continuing,false);
    }
    PhoneVolumeChannel(String session, String context, long end, boolean continuing, boolean continuous) {
        this.session = session; this.context = context; sessionEnd = end;
        this.continuing = continuing; this.continuous = continuous;
        if (continuous && (!continuing || end != 0)) throw new IllegalArgumentException("Continuous volume mode not negotiated");
        policy = new PhoneVolumePolicy(session, context);
    }
    synchronized void human(JSONObject event) throws Exception {
        human(event, android.os.SystemClock.elapsedRealtime());
    }
    synchronized void human(JSONObject event, long now) throws Exception {
        if (closed || accepted != null && receipt == null) throw new IOException("Pending/revoked volume turn");
        turn = AgendaActionChannel.text(event, "turn_id"); human = AgendaActionChannel.text(event, "text");
        sequence = AgendaActionChannel.integer(event, "turn_seq"); speechEnd = AgendaActionChannel.integer(event, "speech_end_ms");
        if (continuous) sessionEnd = ContinuousVoicePolicy.actionEnd(AgendaActionChannel.integer(event,"action_window_end_ms"),speechEnd,now);
        accepted = null; receipt = null;
    }
    synchronized PhoneVolumePolicy.Permit execution(JSONObject event, long now) throws Exception {
        if (closed || accepted != null || !"media".equals(AgendaActionChannel.text(event, "stream"))) throw new IOException("Volume grant revoked/consumed");
        long value = AgendaActionChannel.integer(event, "percent");
        if (value < 0 || value > 100) throw new IOException("Volume out of range");
        PhoneVolumePolicy.Grant grant = new PhoneVolumePolicy.Grant(AgendaActionChannel.text(event, "session_id"),
            AgendaActionChannel.text(event, "context_id"), AgendaActionChannel.text(event, "request_turn_id"),
            AgendaActionChannel.text(event, "nonce"), AgendaActionChannel.integer(event, "request_sequence"),
            AgendaActionChannel.integer(event, "speech_end_ms"), AgendaActionChannel.integer(event, "execute_before_ms"), (int)value);
        accepted = policy.claim(grant, human, turn, sequence, speechEnd, sessionEnd, now); return accepted;
    }
    synchronized JSONObject result(PhoneVolumePolicy.Permit permit, PhoneVolumePolicy.Receipt value) throws Exception {
        if (accepted != permit || receipt != null) throw new IOException("Unrelated volume receipt");
        receipt = value;
        return new JSONObject().put("action", "volume_result").put("session_id", session).put("context_id", context)
            .put("request_turn_id", turn).put("request_sequence", sequence).put("nonce", permit.grant.nonce)
            .put("percent", permit.grant.percent).put("stream", "media").put("status", value.status.name()).put("actual_percent", value.actual);
    }
    synchronized boolean speech(JSONObject event) throws Exception {
        if (!"volume_result".equals(event.optString("timer_control"))) {
            if (accepted != null) throw new IOException("Missing volume result");
            return false;
        }
        String text = AgendaActionChannel.text(event, "text"); long count = AgendaActionChannel.integer(event, "actions_executed");
        String unknown = ActionCompletionPolicy.text(PhoneVolumePolicy.resultText(new PhoneVolumePolicy.Receipt(PhoneVolumePolicy.Status.UNKNOWN, -1), 0), continuing);
        if (closed || !turn.equals(event.optString("turn_id")) || accepted == null ||
                !ActionCompletionPolicy.valid(continuing, event.optString("close_reason"), event.optBoolean("offered_more", true))
                || (text.equals(unknown) ? count != 0 : receipt == null || count != receipt.actions()
                    || !text.equals(ActionCompletionPolicy.text(PhoneVolumePolicy.resultText(receipt, accepted.grant.percent), continuing)))) throw new IOException("Unverified volume response");
        return true;
    }
    synchronized void close() { closed = true; policy.close(); }
}
