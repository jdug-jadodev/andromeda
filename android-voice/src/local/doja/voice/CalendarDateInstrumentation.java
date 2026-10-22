package local.doja.voice;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** ONLY isolated SQLite and public wire turns. Fake scheduling/output/TTS acknowledgements.
 * Never opens agenda.db, AlarmManager, AudioRecord, TTS, network, or the model. */
public final class CalendarDateInstrumentation extends Instrumentation {
    private static final String SESSION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",CONTEXT="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private int checks;
    private void check(boolean value) { checks++;if(!value)throw new IllegalStateException("Isolated calendar date contract"); }
    interface Work { void run()throws Exception; }
    private void reject(Work work)throws Exception {
        boolean rejected=false;try { work.run(); }catch(java.io.IOException|IllegalArgumentException expected){rejected=true;}
        check(rejected);
    }
    private JSONObject human(String text,int sequence)throws Exception {
        SystemClock.sleep(130); // A new fixture after the previous fake onDone, not recorded human speech.
        long boot=SystemClock.elapsedRealtime();
        return new JSONObject().put("turn_id",String.format(Locale.ROOT,"%032x",sequence)).put("turn_seq",sequence).put("text",text)
            .put("speech_end_ms",boot-20).put("calendar_anchor_ms",System.currentTimeMillis()).put("calendar_boot_ms",boot)
            .put("action_window_end_ms",boot-20+300000);
    }
    private JSONObject grant(JSONObject h,String when,String text,long due)throws Exception {
        return new JSONObject().put("session_id",SESSION).put("context_id",CONTEXT).put("request_turn_id",h.getString("turn_id"))
            .put("request_sequence",h.getLong("turn_seq")).put("speech_end_ms",h.getLong("speech_end_ms"))
            .put("calendar_anchor_ms",h.getLong("calendar_anchor_ms")).put("calendar_boot_ms",h.getLong("calendar_boot_ms"))
            .put("event_id",UUID.randomUUID().toString()).put("nonce",UUID.randomUUID().toString().replace("-",""))
            .put("event_kind","REMINDER").put("zone",AgendaEntry.ZONE).put("when_text",when).put("text",text)
            .put("due_ms",due).put("delay_ms",0).put("weekday",0).put("minute_of_day",-1).put("execute_before_ms",SystemClock.elapsedRealtime()+3000);
    }
    private AgendaActionChannel channel() { return new AgendaActionChannel(SESSION,CONTEXT,0,true,true,true,true); }
    private void question(AgendaActionChannel channel,JSONObject h,String wanted)throws Exception {
        JSONObject speech=new JSONObject().put("turn_id",h.getString("turn_id")).put("timer_control","agenda_question").put("agenda_kind","REMINDER")
            .put("actions_executed",0).put("offered_more",false).put("close_reason","").put("text",wanted);
        JSONObject tampered=new JSONObject(speech.toString()).put("text","Recordatorio guardado.");reject(()->channel.speech(tampered));
        check(channel.speech(speech));channel.spoken(h.getString("turn_id"),true); // Fake only: no playback or capture.
    }
    private String persist(AgendaActionChannel channel,JSONObject packet,AgendaStore store,List<String> ids)throws Exception {
        JSONObject changed=new JSONObject(packet.toString()).put("due_ms",packet.getLong("due_ms")+3600000);
        reject(()->channel.execution(changed,SystemClock.elapsedRealtime(),System.currentTimeMillis()));
        AgendaPermit permit=channel.execution(packet,SystemClock.elapsedRealtime(),System.currentTimeMillis());check(permit!=null);
        AgendaStore.Status status=store.create(permit);check(status==AgendaStore.Status.CREATED);
        AgendaEntry row=store.find(permit.id);check(row!=null && row.kind==AgendaEntry.Kind.REMINDER && row.text.equals(packet.getString("text"))
            && row.due==packet.getLong("due_ms") && row.elapsedDue==0 && row.state==AgendaEntry.State.SCHEDULED);
        JSONObject receipt=channel.result(permit,status);check(receipt.getString("status").equals("CREATED") && receipt.getLong("due_ms")==row.due);
        JSONObject say=new JSONObject().put("turn_id",packet.getString("request_turn_id")).put("timer_control","agenda_result")
            .put("actions_executed",1).put("offered_more",true).put("close_reason","")
            .put("text",ActionCompletionPolicy.text(AgendaActionChannel.resultText(status,permit.value,true),true));
        check(channel.speech(say));JSONObject falseReceipt=new JSONObject(say.toString()).put("actions_executed",0);reject(()->channel.speech(falseReceipt));
        check(channel.execution(packet,SystemClock.elapsedRealtime(),System.currentTimeMillis())==null);check(store.create(permit)==AgendaStore.Status.REJECTED);
        JSONObject second=new JSONObject(packet.toString()).put("event_id",UUID.randomUUID().toString());reject(()->channel.execution(second,SystemClock.elapsedRealtime(),System.currentTimeMillis()));
        ids.add(permit.id);channel.close();return permit.id;
    }
    public void onCreate(Bundle args) { super.onCreate(args);start(); }
    public void onStart() {
        Context context=getTargetContext();Bundle result=new Bundle();boolean owned=false;String stage="preflight";
        Map<String,?> timers=new HashMap<>(TimerStore.prefs(context).getAll()),timerReceipts=new HashMap<>(TimerStore.actionReceipts(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active && !SilenceListener.recording());
            check(!context.getDatabasePath("agenda-isolated-test.db").exists());
            final int[] scheduled={0},cancelled={0};final List<String> ids=new ArrayList<>(),scheduleIds=new ArrayList<>();
            AgendaStore.Scheduler fake=new AgendaStore.Scheduler() {
                public void schedule(AgendaEntry entry) { scheduled[0]++;scheduleIds.add(entry.id); }
                public void cancel(String id) { cancelled[0]++; }
            };
            owned=true;stage="isolated_dates_sqlite_wire";
            try(AgendaStore store=new AgendaStore(context,"agenda-isolated-test.db",fake)) {
                String when="el dos de noviembre del 2026 a las 5pm",body="regar el jardín";
                AgendaActionChannel direct=channel();JSONObject first=human("recuérdame "+body+" para "+when,1);
                direct.human(first,SystemClock.elapsedRealtime(),System.currentTimeMillis());persist(direct,grant(first,when,body,1793656800000L),store,ids);
                when="el dos de noviembre del dos mil veintiséis a las cinco pe eme";body="revisar el buzón";
                AgendaActionChannel spoken=channel();first=human("crea un recordatorio para "+when+" para "+body,1);
                spoken.human(first,SystemClock.elapsedRealtime(),System.currentTimeMillis());persist(spoken,grant(first,when,body,1793656800000L),store,ids);
                AgendaActionChannel relative=channel();when="en tres días a las cinco de la tarde";body="regar el jardín";
                first=human("recuérdame "+body+" "+when,1);relative.human(first,SystemClock.elapsedRealtime(),System.currentTimeMillis());
                LocalDate day=Instant.ofEpochMilli(first.getLong("calendar_anchor_ms")).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate().plusDays(3);
                long expected=day.atTime(17,0).atZone(ZoneId.of(AgendaEntry.ZONE)).toInstant().toEpochMilli();
                persist(relative,grant(first,when,body,expected),store,ids);
                stage="day_without_hour_question";
                AgendaActionChannel form=channel();first=human("recuérdame regar el jardín en tres días",1);
                form.human(first,SystemClock.elapsedRealtime(),System.currentTimeMillis());String origin=first.getString("turn_id");
                day=Instant.ofEpochMilli(first.getLong("calendar_anchor_ms")).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate().plusDays(3);
                expected=day.atTime(17,0).atZone(ZoneId.of(AgendaEntry.ZONE)).toInstant().toEpochMilli();
                question(form,first,AgendaDialogue.hourQuestion(day));check(scheduled[0]==3 && store.counts().get("REMINDER")==3);
                JSONObject ambiguous=human("a las cinco",2);form.human(ambiguous,SystemClock.elapsedRealtime(),System.currentTimeMillis());
                question(form,ambiguous,AgendaDialogue.hourQuestion(day));check(scheduled[0]==3);
                JSONObject last=human("a las cinco pm",3);form.human(last,SystemClock.elapsedRealtime(),System.currentTimeMillis());
                JSONObject packet=grant(last,last.getString("text"),"regar el jardín",expected).put("dialogue_origin_turn_id",origin);
                JSONObject changedOrigin=new JSONObject(packet.toString()).put("dialogue_origin_turn_id","ffffffffffffffffffffffffffffffff");
                reject(()->form.execution(changedOrigin,SystemClock.elapsedRealtime(),System.currentTimeMillis()));
                persist(form,packet,store,ids);
                stage="negative_wire_and_effect_counts";
                AgendaActionChannel unheard=channel();JSONObject start=human("recuérdame regar el jardín en tres días",1);
                unheard.human(start,SystemClock.elapsedRealtime(),System.currentTimeMillis());
                // Validate the question without acknowledging it: the next field must fail.
                day=Instant.ofEpochMilli(start.getLong("calendar_anchor_ms")).atZone(ZoneId.of(AgendaEntry.ZONE)).toLocalDate().plusDays(3);
                check(unheard.speech(new JSONObject().put("turn_id",start.getString("turn_id")).put("timer_control","agenda_question").put("agenda_kind","REMINDER")
                    .put("actions_executed",0).put("offered_more",false).put("close_reason","").put("text",AgendaDialogue.hourQuestion(day))));
                JSONObject premature=human("5pm",2);reject(()->unheard.human(premature,SystemClock.elapsedRealtime(),System.currentTimeMillis()));unheard.close();
                AgendaActionChannel bad=channel();JSONObject badHuman=human("recuérdame regar el jardín para el dos de nobiembre del 2026 a las 5pm",1);
                bad.human(badHuman,SystemClock.elapsedRealtime(),System.currentTimeMillis());JSONObject badGrant=grant(badHuman,"el dos de nobiembre del 2026 a las 5pm","regar el jardín",1793656800000L);
                reject(()->bad.execution(badGrant,SystemClock.elapsedRealtime(),System.currentTimeMillis()));bad.close();
                check(scheduled[0]==4 && cancelled[0]==0 && scheduleIds.equals(ids));check(store.counts().get("REMINDER")==4 && store.counts().get("ALARM")==0);
            }
            stage="durable_receipt_readback";
            try(SQLiteDatabase db=context.openOrCreateDatabase("agenda-isolated-test.db",Context.MODE_PRIVATE,null)) {
                for(String id:ids)try(Cursor row=db.rawQuery("SELECT a.due_ms,r.status FROM agenda a JOIN agenda_receipts r ON a.id=r.id WHERE a.id=?",new String[]{id})) {
                    check(row.moveToFirst() && row.getLong(0)>System.currentTimeMillis() && row.getString(1).equals("CREATED"));
                }
            }
            check(timers.equals(TimerStore.prefs(context).getAll()) && timerReceipts.equals(TimerStore.actionReceipts(context).getAll()));
            result.putBoolean("passed",true);result.putBoolean("android_sqlite",true);result.putBoolean("durable_receipts_verified",true);
            result.putBoolean("date_pm_spoken_year_relative_day_verified",true);result.putBoolean("fresh_heard_hour_and_repeat_verified",true);
            result.putBoolean("replay_foreign_date_origin_and_bad_month_rejected",true);result.putInt("isolated_rows",ids.size());
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
