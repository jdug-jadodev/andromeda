package local.doja.voice;

import java.io.IOException;
import java.util.*;
import org.json.JSONObject;

/** Current-turn read grant and RAM-only speech. Only numeric receipts cross TLS. */
final class ReminderReadChannel {
    final String session,context; final boolean continuous;
    private long sessionEnd,sequence,speechEnd;
    private String turn="",human="";
    private boolean closed,completed;
    private final Set<String> consumed=new HashSet<>();
    private ReminderListPolicy.Permit accepted;
    private ReminderListPolicy.Receipt receipt;
    ReminderReadChannel(String session,String context,long end,boolean continuous) {
        this.session=session; this.context=context; sessionEnd=end; this.continuous=continuous;
    }
    synchronized void human(JSONObject event,long now) throws Exception {
        if(closed || accepted!=null && !completed) throw new IOException("Pending reminder read");
        turn=AgendaActionChannel.text(event,"turn_id"); human=AgendaActionChannel.text(event,"text");
        sequence=AgendaActionChannel.integer(event,"turn_seq"); speechEnd=AgendaActionChannel.integer(event,"speech_end_ms");
        if(continuous) sessionEnd=ContinuousVoicePolicy.actionEnd(AgendaActionChannel.integer(event,"action_window_end_ms"),speechEnd,now);
        accepted=null; receipt=null; completed=false;
    }
    synchronized ReminderListPolicy.Permit execution(JSONObject event,long now) throws Exception {
        if(closed || accepted!=null || consumed.contains(turn) || consumed.size()>=24) throw new IOException("Read revoked/consumed");
        ReminderListPolicy.Grant grant=new ReminderListPolicy.Grant(AgendaActionChannel.text(event,"session_id"),
            AgendaActionChannel.text(event,"context_id"),AgendaActionChannel.text(event,"request_turn_id"),
            AgendaActionChannel.text(event,"nonce"),AgendaActionChannel.integer(event,"request_sequence"),
            AgendaActionChannel.integer(event,"speech_end_ms"),AgendaActionChannel.integer(event,"read_before_ms"));
        accepted=ReminderListPolicy.claim(grant,session,context,turn,human,sequence,speechEnd,sessionEnd,now);
        consumed.add(turn); return accepted;
    }
    synchronized JSONObject result(ReminderListPolicy.Permit permit,ReminderListPolicy.Receipt value) throws Exception {
        if(closed || completed) return null;
        if(accepted!=permit) throw new IOException("Unrelated reminder read");
        receipt=value; completed=true;
        return new JSONObject().put("action","reminder_read_result").put("session_id",session).put("context_id",context)
            .put("nonce",permit.grant.nonce).put("request_turn_id",turn).put("request_sequence",sequence)
            .put("status",value.status).put("total",value.total).put("shown",value.shown);
    }
    synchronized String speech(JSONObject event) throws Exception {
        if(!"reminder_list".equals(event.optString("timer_control"))) return null;
        JSONObject meta=event.getJSONObject("reminder_read"); String status=AgendaActionChannel.text(meta,"status");
        long total=AgendaActionChannel.integer(meta,"total"),shown=AgendaActionChannel.integer(meta,"shown");
        if(closed || accepted==null || !turn.equals(event.optString("turn_id"))
                || !accepted.grant.nonce.equals(AgendaActionChannel.text(meta,"nonce"))
                || AgendaActionChannel.integer(event,"actions_executed")!=0 || !ActionCompletionPolicy.valid(true,event.optString("close_reason"),event.optBoolean("offered_more")))
            throw new IOException("Unverified reminder read speech");
        String wire=AgendaActionChannel.text(event,"text");
        if(status.equals("TIMEOUT")) {
            if(total!=0 || shown!=0 || !wire.equals(ReminderListPolicy.UNAVAILABLE)) throw new IOException("Invalid read timeout");
            completed=true; receipt=null; return ActionCompletionPolicy.text(ReminderListPolicy.UNAVAILABLE,true);
        }
        if(receipt==null || !receipt.status.equals(status) || receipt.total!=total || receipt.shown!=shown
                || !wire.equals(status.equals("READ")?ReminderListPolicy.ACK:ReminderListPolicy.UNAVAILABLE))
            throw new IOException("No matching Android reminder snapshot");
        return ActionCompletionPolicy.text(receipt.text,true);
    }
    synchronized void spoken(String id) { if(turn.equals(id)) receipt=null; } // No reminder content retained after playback/failure.
    synchronized void close() { closed=true; accepted=null; receipt=null; human=""; }
}
