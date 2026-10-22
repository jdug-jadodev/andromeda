package local.doja.voice;

import java.io.IOException;
import org.json.JSONObject;

/** Authenticated step/confirm/delete. Only sealed permits reach AgendaStore. */
final class ReminderDeleteChannel {
    private final String session,context;
    private final ReminderDeletion dialogue=new ReminderDeletion();
    private AgendaDialogue.Human human;
    private long sessionEnd;
    private String stepNonce="",deleteNonce="",receiptStatus="";
    private boolean stepUsed,closed;
    private ReminderDeletion.Step step;
    private ReminderDeletion.Permit permit;
    ReminderDeleteChannel(String session,String context) { this.session=session;this.context=context; }
    private static String text(JSONObject e,String key)throws Exception{return AgendaActionChannel.text(e,key);}
    private static long number(JSONObject e,String key)throws Exception{return AgendaActionChannel.integer(e,key);}
    synchronized void human(JSONObject e,long now)throws Exception {
        if(closed)throw new IOException("Revoked reminder deletion channel");
        human=new AgendaDialogue.Human(text(e,"turn_id"),number(e,"turn_seq"),text(e,"text"),number(e,"speech_end_ms"),number(e,"calendar_anchor_ms"),number(e,"calendar_boot_ms"),now,64);
        sessionEnd=ContinuousVoicePolicy.actionEnd(number(e,"action_window_end_ms"),human.speechEnd,now);
        step=null;stepNonce=deleteNonce=receiptStatus="";stepUsed=false;permit=null;
    }
    private void current(JSONObject e)throws Exception {
        if(closed || human==null || !session.equals(text(e,"session_id")) || !context.equals(text(e,"context_id"))
                || !human.id.equals(text(e,"request_turn_id")) || human.sequence!=number(e,"request_sequence")
                || human.speechEnd!=number(e,"speech_end_ms"))throw new IOException("Unrelated reminder deletion turn");
    }
    private JSONObject base(String action)throws Exception {
        return new JSONObject().put("action",action).put("session_id",session).put("context_id",context)
            .put("request_turn_id",human.id).put("request_sequence",human.sequence);
    }
    synchronized JSONObject select(JSONObject e,AgendaStore store,long now)throws Exception {
        current(e);long before=number(e,"select_before_ms");
        if(stepUsed || permit!=null || before<=now || before-now>4100 || before>human.speechEnd+56500 || before>sessionEnd-5000)
            throw new IOException("Invalid reminder deletion selection window");
        stepNonce=text(e,"nonce");if(!ContinuousVoicePolicy.id(stepNonce))throw new IOException("Invalid selection nonce");
        if(!(dialogue.active()?dialogue.origin():human.id).equals(text(e,"origin_turn_id")))throw new IOException("Invalid deletion origin");
        stepUsed=true;AgendaStore.Page page=store.list(AgendaEntry.Kind.REMINDER);
        if(android.os.SystemClock.elapsedRealtime()>=before)throw new IOException("Expired reminder read");
        step=dialogue.step(human,page.entries,page.total,now);
        JSONObject result=base("reminder_delete_selection").put("nonce",stepNonce).put("status",step.status);
        if(step.status.equals("QUESTION")) {
            result.put("stage",step.stage);
            if(step.selected!=null)result.put("event_id",step.selected.id).put("revision",step.selected.revision).put("due_ms",step.selected.due);
        }
        return result; // Never text, title, date strings, candidates or content hashes.
    }
    synchronized ReminderDeletion.Permit execution(JSONObject e,long now)throws Exception {
        current(e);
        if(permit!=null || stepUsed)throw new IOException("Deletion already consumed this turn");
        deleteNonce=text(e,"nonce");if(!ContinuousVoicePolicy.id(deleteNonce))throw new IOException("Invalid deletion nonce");
        permit=dialogue.claim(text(e,"operation_id"),text(e,"origin_turn_id"),text(e,"event_id"),number(e,"revision"),number(e,"due_ms"),
            human,number(e,"execute_before_ms"),sessionEnd,now);
        return permit;
    }
    synchronized JSONObject result(ReminderDeletion.Permit expected,AgendaStore.AlarmReceipt receipt)throws Exception {
        if(permit!=expected || !receipt.operation.equals(permit.operation) || !receipt.event.equals(permit.selected.id))throw new IOException("Unrelated reminder receipt");
        receiptStatus=receipt.status;
        return base("reminder_delete_result").put("operation_id",permit.operation).put("nonce",deleteNonce)
            .put("event_id",permit.selected.id).put("revision",permit.selected.revision).put("status",receiptStatus);
    }
    synchronized String speech(JSONObject e)throws Exception {
        String control=e.optString("timer_control");if(!control.startsWith("reminder_delete_"))return null;
        if(closed || human==null || !human.id.equals(text(e,"turn_id")) || !e.optString("close_reason").isEmpty())throw new IOException("Unrelated reminder deletion speech");
        long count=number(e,"actions_executed");String wire=text(e,"text");boolean offer=e.optBoolean("offered_more");
        if(control.equals("reminder_delete_question") || control.equals("reminder_delete_selection")) {
            JSONObject meta=e.getJSONObject("reminder_delete");String status=text(meta,"status");
            if(count!=0 || !stepNonce.equals(text(meta,"nonce")))throw new IOException("Unverified reminder selection");
            if(status.equals("UNAVAILABLE")) {
                if(!offer || !wire.equals(ActionCompletionPolicy.text(ReminderDeletion.UNAVAILABLE,true)))throw new IOException("Invalid selection timeout");
                dialogue.clear();return wire;
            }
            if(step==null || !step.status.equals(status))throw new IOException("Missing local selection receipt");
            if(status.equals("QUESTION")) {
                if(!control.equals("reminder_delete_question") || offer || !wire.equals(ReminderDeletion.QUESTION)
                        || !step.stage.equals(text(meta,"stage")))throw new IOException("Invalid local question");
                return step.text;
            }
            if(!control.equals("reminder_delete_selection") || !offer || !wire.equals(ActionCompletionPolicy.text(step.text,true)))throw new IOException("Invalid selection completion");
            return wire;
        }
        if(control.equals("reminder_delete_cancelled")) {
            // Bounded dialogue exhaustion is never an authorization.
            if(permit!=null || count!=0 || !offer || !wire.equals(ActionCompletionPolicy.text(ReminderDeletion.CANCELLED,true)))throw new IOException("Invalid deletion cancellation");
            dialogue.clear();return wire;
        }
        if(!control.equals("reminder_delete_result") || permit==null || !offer)throw new IOException("No deletion permit");
        boolean uncertain=count==0 && wire.equals(ActionCompletionPolicy.text(ReminderDeletion.UNKNOWN,true));
        if(!uncertain && (receiptStatus.isEmpty() || count!=(receiptStatus.equals("REMOVED")?1:0)
                || !wire.equals(ActionCompletionPolicy.text(ReminderDeletion.resultText(receiptStatus),true))))throw new IOException("No matching Android deletion result");
        return wire;
    }
    synchronized void spoken(String turn,boolean completed,long now) { dialogue.spoken(turn,completed,now);step=null; }
    synchronized void cancelDialogue() { dialogue.clear();step=null;if(permit!=null)permit.revoke(); }
    synchronized void close() { closed=true;dialogue.clear();human=null;step=null;if(permit!=null)permit.revoke(); }
}
