package local.doja.voice;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.util.*;
import org.json.*;

/** Synthetic wire/SQLite contract. ONLY agenda-isolated-test.db, fake schedule/output and TTS acknowledgements.
 * Never opens the human agenda, AudioRecord, TTS, AlarmManager, network or model. */
public final class ReminderDeleteInstrumentation extends Instrumentation {
    private static final String SESSION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",CONTEXT="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private int checks;
    private void check(boolean value) { checks++;if(!value)throw new IllegalStateException("Isolated reminder deletion contract"); }
    interface Work { void run() throws Exception; }
    private void reject(Work work)throws Exception {
        boolean rejected=false;try { work.run(); }catch(java.io.IOException|IllegalArgumentException expected){rejected=true;}
        check(rejected);
    }
    private static String token() { return UUID.randomUUID().toString().replace("-",""); }
    private JSONObject human(String text,int sequence)throws Exception {
        SystemClock.sleep(130); // A fixture turn after the previous synthetic playback acknowledgement, not real speech.
        long boot=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
        return new JSONObject().put("turn_id",String.format(Locale.ROOT,"%032x",sequence)).put("turn_seq",sequence)
            .put("text",text).put("speech_end_ms",boot-20).put("calendar_anchor_ms",wall).put("calendar_boot_ms",boot)
            .put("action_window_end_ms",boot-20+300000);
    }
    private JSONObject scope(JSONObject h)throws Exception {
        return new JSONObject().put("session_id",SESSION).put("context_id",CONTEXT).put("request_turn_id",h.getString("turn_id"))
            .put("request_sequence",h.getLong("turn_seq")).put("speech_end_ms",h.getLong("speech_end_ms"));
    }
    private JSONObject select(ReminderDeleteChannel channel,AgendaStore store,JSONObject h,String origin)throws Exception {
        channel.human(h,SystemClock.elapsedRealtime());
        JSONObject packet=scope(h).put("nonce",token()).put("origin_turn_id",origin).put("select_before_ms",SystemClock.elapsedRealtime()+3000);
        JSONObject result=channel.select(packet,store,SystemClock.elapsedRealtime());
        Set<String> keys=new HashSet<>(Arrays.asList("action","session_id","context_id","request_turn_id","request_sequence","nonce","status","stage","event_id","revision","due_ms"));
        for(Iterator<String> it=result.keys();it.hasNext();)check(keys.contains(it.next()));
        check(!result.has("text") && !result.has("title") && !result.has("candidates") && !result.has("content_hash"));
        return result;
    }
    private String question(ReminderDeleteChannel channel,JSONObject h,JSONObject selection)throws Exception {
        JSONObject meta=new JSONObject().put("nonce",selection.getString("nonce")).put("status","QUESTION").put("stage",selection.getString("stage"));
        JSONObject event=new JSONObject().put("turn_id",h.getString("turn_id")).put("timer_control","reminder_delete_question")
            .put("text",ReminderDeletion.QUESTION).put("actions_executed",0).put("offered_more",false).put("close_reason","").put("reminder_delete",meta);
        JSONObject tampered=new JSONObject(event.toString()).put("text","Recordatorio eliminado.");reject(()->channel.speech(tampered));
        String local=channel.speech(event);check(local.length()<=ReminderDeletion.MAX_SPOKEN);
        channel.spoken(h.getString("turn_id"),true,SystemClock.elapsedRealtime()); // Fixture acknowledgement, NEVER actual audio.
        return local;
    }
    private AgendaEntry create(AgendaStore store,String text)throws Exception {
        long boot=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();String id=UUID.randomUUID().toString();
        AgendaPermit permit=AgendaPermit.validated(id,AgendaEntry.Kind.REMINDER,"recuérdame "+text+" en dos minutos","en dos minutos",text,
            wall+120000,120000,boot+3000,wall,boot,boot,wall);
        check(store.create(permit)==AgendaStore.Status.CREATED);return store.find(id);
    }
    private ReminderDeletion.Permit permit(AgendaStore store,AgendaEntry entry,String operation)throws Exception {
        ReminderDeletion dialogue=new ReminderDeletion();JSONObject first=human("elimina el recordatorio de "+entry.text,20);
        AgendaDialogue.Human begin=new AgendaDialogue.Human(first.getString("turn_id"),20,first.getString("text"),first.getLong("speech_end_ms"),
            first.getLong("calendar_anchor_ms"),first.getLong("calendar_boot_ms"),SystemClock.elapsedRealtime(),64);
        AgendaStore.Page page=store.list(AgendaEntry.Kind.REMINDER);ReminderDeletion.Step selected=dialogue.step(begin,page.entries,page.total,SystemClock.elapsedRealtime());
        check(selected.stage.equals("CONFIRM") && selected.selected.id.equals(entry.id));dialogue.spoken(begin.id,true,SystemClock.elapsedRealtime());
        JSONObject last=human("sí",21);AgendaDialogue.Human yes=new AgendaDialogue.Human(last.getString("turn_id"),21,last.getString("text"),last.getLong("speech_end_ms"),
            last.getLong("calendar_anchor_ms"),last.getLong("calendar_boot_ms"),SystemClock.elapsedRealtime(),64);
        return dialogue.claim(operation,begin.id,entry.id,entry.revision,entry.due,yes,SystemClock.elapsedRealtime()+3000,last.getLong("action_window_end_ms"),SystemClock.elapsedRealtime());
    }
    public void onCreate(Bundle args) { super.onCreate(args);start(); }
    public void onStart() {
        Context context=getTargetContext();Bundle result=new Bundle();boolean owned=false;String stage="preflight";
        Map<String,?> timers=new HashMap<>(TimerStore.prefs(context).getAll()),timerReceipts=new HashMap<>(TimerStore.actionReceipts(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active && !SilenceListener.recording());
            check(!context.getDatabasePath("agenda-isolated-test.db").exists());
            final int[] scheduled={0},cancelled={0};final boolean[] failCancel={false};final List<String> cancelTargets=new ArrayList<>();
            AgendaStore.Scheduler fake=new AgendaStore.Scheduler() {
                public void schedule(AgendaEntry entry) { scheduled[0]++; }
                public void cancel(String id) { cancelled[0]++;cancelTargets.add(id);if(failCancel[0])throw new IllegalStateException("synthetic cancellation fault"); }
            };
            owned=true;stage="isolated_sqlite_wire";
            String removedId,operation;
            try(AgendaStore store=new AgendaStore(context,"agenda-isolated-test.db",fake)) {
                AgendaEntry first=create(store,"regar las plantas");SystemClock.sleep(20);
                AgendaEntry second=create(store,"regar las plantas"),other=create(store,"llamar al taller");
                long boot=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();String alarmId=UUID.randomUUID().toString();
                AgendaPermit alarm=AgendaPermit.validated(alarmId,AgendaEntry.Kind.ALARM,"pon una alarma en dos minutos","en dos minutos","",
                    wall+120000,120000,boot+3000,wall,boot,boot,wall);
                check(store.create(alarm)==AgendaStore.Status.CREATED);check(scheduled[0]==4);
                ReminderDeleteChannel channel=new ReminderDeleteChannel(SESSION,CONTEXT);
                JSONObject h1=human("quiero borrar un recordatorio",1);String origin=h1.getString("turn_id");
                JSONObject selected=select(channel,store,h1,origin);check(selected.getString("stage").equals("TARGET"));
                check(question(channel,h1,selected).equals(ReminderDeletion.TARGET));
                JSONObject h2=human("el de regar las plantas",2);selected=select(channel,store,h2,origin);check(selected.getString("stage").equals("CHOOSE"));
                String choices=question(channel,h2,selected);check(choices.contains("1. "+first.text) && choices.contains("2. "+second.text));check(!choices.contains(other.text));
                JSONObject h3=human("el segundo",3);selected=select(channel,store,h3,origin);check(selected.getString("stage").equals("CONFIRM"));
                check(selected.getString("event_id").equals(second.id));check(question(channel,h3,selected).contains(second.when()));
                JSONObject h4=human("te",4);selected=select(channel,store,h4,origin);check(selected.getString("stage").equals("CONFIRM"));
                check(question(channel,h4,selected).contains(second.text));check(cancelled[0]==0 && store.find(second.id)!=null);
                JSONObject h5=human("sí, por favor",5);channel.human(h5,SystemClock.elapsedRealtime());
                AgendaActionChannel actions=new AgendaActionChannel(SESSION,CONTEXT,0,true,true,true,true);
                actions.human(h5,SystemClock.elapsedRealtime(),System.currentTimeMillis());actions.alarmExecution();reject(()->actions.volumeExecution());
                operation=UUID.randomUUID().toString();removedId=second.id;
                JSONObject grant=scope(h5).put("operation_id",operation).put("nonce",token()).put("origin_turn_id",origin)
                    .put("event_id",second.id).put("revision",second.revision).put("due_ms",second.due).put("execute_before_ms",SystemClock.elapsedRealtime()+3000);
                ReminderDeletion.Permit permit=channel.execution(grant,SystemClock.elapsedRealtime());
                AgendaStore.AlarmReceipt receipt=store.deleteReminder(permit);check(receipt.status.equals("REMOVED"));check(store.find(second.id)==null);
                check(store.find(first.id)!=null && store.find(other.id)!=null && store.find(alarmId)!=null);
                check(cancelled[0]==1 && cancelTargets.get(0).equals(second.id));
                JSONObject ack=channel.result(permit,receipt);check(ack.getString("status").equals("REMOVED"));
                JSONObject say=new JSONObject().put("turn_id",h5.getString("turn_id")).put("timer_control","reminder_delete_result").put("close_reason","")
                    .put("offered_more",true).put("actions_executed",1).put("text",ActionCompletionPolicy.text(ReminderDeletion.resultText("REMOVED"),true));
                check(channel.speech(say).contains("Recordatorio eliminado"));
                JSONObject falseReceipt=new JSONObject(say.toString()).put("actions_executed",0);reject(()->channel.speech(falseReceipt));
                check(store.deleteReminder(permit).status.equals("REJECTED") && cancelled[0]==1);reject(()->channel.execution(grant,SystemClock.elapsedRealtime()));
                channel.close();actions.close();
                stage="isolated_sqlite_stale_and_faults";
                AgendaEntry changed=create(store,"revisar el buzón");ReminderDeletion.Permit stale=permit(store,changed,UUID.randomUUID().toString());
                try(SQLiteDatabase db=context.openOrCreateDatabase("agenda-isolated-test.db",Context.MODE_PRIVATE,null)) {
                    db.execSQL("UPDATE agenda SET revision=revision+1 WHERE id=?",new Object[]{changed.id});
                }
                check(store.deleteReminder(stale).status.equals("STALE"));check(store.find(changed.id)!=null && cancelled[0]==1);
                AgendaEntry failed=create(store,"revisar el correo");ReminderDeletion.Permit uncertain=permit(store,failed,UUID.randomUUID().toString());
                failCancel[0]=true;check(store.deleteReminder(uncertain).status.equals("UNKNOWN"));failCancel[0]=false;
                check(store.find(failed.id).state==AgendaEntry.State.CANCEL_PENDING && store.find(failed.id).text.isEmpty());
                check(cancelled[0]==2 && cancelTargets.get(1).equals(failed.id));check(store.deleteReminder(uncertain).status.equals("REJECTED") && cancelled[0]==2);
                ReminderDeletion.Permit reusedOperation=permit(store,other,operation);check(store.deleteReminder(reusedOperation).status.equals("UNKNOWN"));
                check(store.find(other.id)!=null && store.find(first.id)!=null && store.find(alarmId)!=null && cancelled[0]==2);
            }
            stage="durable_receipt_readback";
            try(SQLiteDatabase db=context.openOrCreateDatabase("agenda-isolated-test.db",Context.MODE_PRIVATE,null);
                    Cursor row=db.rawQuery("SELECT event_id,operation,status FROM agenda_actions WHERE id=?",new String[]{operation})) {
                check(row.moveToFirst() && row.getString(0).equals(removedId) && row.getString(1).equals("DELETE_REMINDER") && row.getString(2).equals("REMOVED"));
            }
            check(timers.equals(TimerStore.prefs(context).getAll()) && timerReceipts.equals(TimerStore.actionReceipts(context).getAll()));
            result.putBoolean("passed",true);result.putBoolean("android_sqlite",true);result.putBoolean("durable_receipt_verified",true);
            result.putBoolean("wire_target_choice_confirmation_repeat_verified",true);result.putBoolean("stale_fault_and_replay_verified",true);
        }catch(Exception failure) {
            result.putBoolean("passed",false);result.putString("error_kind",failure.getClass().getSimpleName());result.putString("stage",stage);
        }finally {
            boolean cleaned=!owned || !context.getDatabasePath("agenda-isolated-test.db").exists() || context.deleteDatabase("agenda-isolated-test.db");
            result.putBoolean("isolated_storage_cleaned",cleaned);if(!cleaned)result.putBoolean("passed",false);
        }
        result.putInt("checks",checks);result.putBoolean("real_agenda_opened",false);result.putBoolean("real_scheduler",false);
        result.putBoolean("microphone_opened",false);result.putBoolean("audio_played",false);result.putBoolean("llm_started",false);
        result.putBoolean("human_content_exported",false);finish(result.getBoolean("passed")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
