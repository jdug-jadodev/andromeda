package local.doja.voice;

import java.io.IOException;
import org.json.*;

/** Authenticated read/confirm/delete channel. Only AgendaStore executes sealed permits. */
final class AlarmActionChannel {
    private final String session,context;
    private final AlarmDeletion deletion=new AlarmDeletion();
    private AgendaDialogue.Human human;
    private String selectionStatus="",nonce="",operation="",resultStatus="";
    private boolean cancelled,closed,confirmed;
    private long sessionEnd;
    private AlarmDeletion.Permit permit;
    AlarmActionChannel(String session,String context) { this.session=session; this.context=context; }
    private static String text(JSONObject event,String key) throws Exception { return AgendaActionChannel.text(event,key); }
    private static long number(JSONObject event,String key) throws Exception { return AgendaActionChannel.integer(event,key); }
    synchronized void human(JSONObject event,long now) throws Exception {
        if(closed) throw new IOException("Revoked alarm scope");
        human=new AgendaDialogue.Human(text(event,"turn_id"),number(event,"turn_seq"),text(event,"text"),number(event,"speech_end_ms"),number(event,"calendar_anchor_ms"),number(event,"calendar_boot_ms"),now,64);
        sessionEnd=ContinuousVoicePolicy.actionEnd(number(event,"action_window_end_ms"),human.speechEnd,now);
        boolean active=deletion.active(); confirmed=active && deletion.human(human,now); cancelled=active && !confirmed;
        selectionStatus=resultStatus=operation=nonce=""; permit=null;
    }
    private void current(JSONObject event) throws Exception {
        if(closed || human==null || !session.equals(text(event,"session_id")) || !context.equals(text(event,"context_id"))
                || !human.id.equals(text(event,"request_turn_id")) || human.sequence!=number(event,"request_sequence")) throw new IOException("Unrelated alarm event");
    }
    synchronized JSONObject select(JSONObject event,AgendaStore store,long now) throws Exception {
        current(event); int[] target=AlarmSchedule.deletionTarget(human.text);
        long before=number(event,"select_before_ms");
        if(!selectionStatus.isEmpty() || deletion.active() || before<=now || before-now>4100 || before>human.speechEnd+26500
                || number(event,"speech_end_ms")!=human.speechEnd || target[0]!=number(event,"weekday") || target[1]!=number(event,"minute_of_day")) throw new IOException("Invalid alarm selection");
        nonce=text(event,"nonce"); if(!ContinuousVoicePolicy.id(nonce)) throw new IOException("Invalid selection nonce");
        AgendaEntry selected=null; int count=0; AgendaStore.Page page=store.list(AgendaEntry.Kind.ALARM);
        for(AgendaEntry candidate:page.entries) {
            if(candidate.weekday==target[0] && candidate.minute==target[1] && candidate.state!=AgendaEntry.State.CANCEL_PENDING) { selected=candidate; count++; }
        }
        if(page.total>page.entries.size()) count=-1; // Never select from a truncated catalogue.
        selectionStatus=count<0 ? "UNKNOWN" : count==0 ? "NOT_FOUND" : count==1 ? "FOUND" : "AMBIGUOUS";
        JSONObject result=base("alarm_selection").put("nonce",nonce).put("status",selectionStatus);
        if(count==1) {
            deletion.select(human,selected,now);
            result.put("event_id",selected.id).put("revision",selected.revision).put("due_ms",selected.due).put("weekday",selected.weekday).put("minute_of_day",selected.minute);
        }
        return result;
    }
    private JSONObject base(String action) throws Exception {
        return new JSONObject().put("action",action).put("session_id",session).put("context_id",context).put("request_turn_id",human.id).put("request_sequence",human.sequence);
    }
    synchronized AlarmDeletion.Permit execution(JSONObject event,long now) throws Exception {
        current(event);
        if(!confirmed || permit!=null || human.speechEnd!=number(event,"speech_end_ms")) throw new IOException("No human deletion confirmation");
        nonce=text(event,"nonce"); if(!ContinuousVoicePolicy.id(nonce)) throw new IOException("Invalid delete nonce");
        operation=text(event,"operation_id");
        permit=deletion.claim(operation,text(event,"origin_turn_id"),text(event,"event_id"),number(event,"revision"),number(event,"due_ms"),
            Math.toIntExact(number(event,"weekday")),Math.toIntExact(number(event,"minute_of_day")),human,number(event,"execute_before_ms"),sessionEnd,now);
        return permit;
    }
    synchronized JSONObject result(AlarmDeletion.Permit expected,AgendaStore.AlarmReceipt receipt) throws Exception {
        if(permit!=expected || !receipt.operation.equals(operation) || !receipt.event.equals(permit.selected.id)) throw new IOException("Unrelated alarm receipt");
        resultStatus=receipt.status;
        return base("alarm_delete_result").put("operation_id",operation).put("nonce",nonce).put("event_id",permit.selected.id).put("revision",permit.selected.revision).put("status",resultStatus);
    }
    synchronized boolean speech(JSONObject event) throws Exception {
        String control=event.optString("timer_control");
        if(!control.startsWith("alarm_")) {
            if(control.isEmpty() && AlarmDeletion.unsupportedClaim(text(event,"text"))) throw new IOException("Alarm claim without receipt");
            return false;
        }
        if(closed || human==null || !human.id.equals(text(event,"turn_id")) || !event.optString("close_reason").isEmpty()) throw new IOException("Unrelated alarm speech");
        long count=number(event,"actions_executed"); String expected; boolean offer=true;
        switch(control) {
            case "alarm_delete_question":
                if(!"FOUND".equals(selectionStatus) || count!=0 || !deletion.active()) throw new IOException("Unproved selection");
                expected=deletion.question(); offer=false; break;
            case "alarm_selection":
                String status=selectionStatus;
                if(status.isEmpty() || "FOUND".equals(status)) status="UNKNOWN";
                expected=ActionCompletionPolicy.text(AlarmDeletion.selectionText(status),true); if(count!=0) throw new IOException("Selection is read-only"); deletion.clear(); break;
            case "alarm_delete_cancelled":
                if(!cancelled || count!=0) throw new IOException("Unrelated cancellation"); expected=ActionCompletionPolicy.text(AlarmDeletion.CANCELLED,true); break;
            case "alarm_delete_result":
                if(permit==null) throw new IOException("No authorized deletion");
                boolean uncertain=count==0 && ActionCompletionPolicy.text(AlarmDeletion.UNKNOWN,true).equals(text(event,"text"));
                if(!uncertain && (resultStatus.isEmpty() || count!=("REMOVED".equals(resultStatus)?1:0))) throw new IOException("No matching deletion receipt");
                expected=ActionCompletionPolicy.text(uncertain?AlarmDeletion.UNKNOWN:AlarmDeletion.resultText(resultStatus),true); break;
            default: throw new IOException("Unknown alarm speech");
        }
        if(event.optBoolean("offered_more",!offer)!=offer || !expected.equals(text(event,"text"))) throw new IOException("Unverified alarm confirmation");
        return true;
    }
    synchronized void spoken(String turn,boolean completed) { deletion.spoken(turn,completed); }
    synchronized void cancelDialogue() { deletion.clear(); }
    synchronized void close() { closed=true; deletion.clear(); human=null; if(permit!=null) permit.revoke(); }
}
