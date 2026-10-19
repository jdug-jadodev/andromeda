package local.doja.voice;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.util.*;
import org.json.*;

/** Actual pinned TLS + production agenda channels; explicit public session fixtures, no audio/LLM/real scheduling. */
public final class AgendaTransportInstrumentation extends Instrumentation {
    private static void check(boolean value) { if (!value) throw new IllegalStateException("Agenda transport contract"); }
    private interface Unsafe { void run() throws Exception; }
    private static void rejected(Unsafe operation) throws Exception {
        boolean rejected = false; try { operation.run(); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected);
    }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Context context = getTargetContext(); Bundle result = new Bundle(); boolean ownedDatabase = false;
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        Map<String, ?> receipts = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        String stage = "preflight";
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active && !SilenceListener.recording());
            check(!context.getDatabasePath("agenda-isolated-test.db").exists());
            rejected(() -> { try (VoiceConnection wrong = VoiceConnection.open(context,"microphone",ConversationMemory.emptySnapshot(),null,false,true)) {} });
            rejected(() -> { try (VoiceConnection wrong = VoiceConnection.open(context,"synthetic_pcm")) {} });
            result.putBoolean("wrong_source_rejected", true); result.putBoolean("wrong_protocol_rejected", true);
            final int[] schedules = {0};
            AgendaStore.Scheduler fake = new AgendaStore.Scheduler() {
                public void schedule(AgendaEntry ignored) { schedules[0]++; }
                public void cancel(String ignored) {}
            };
            ownedDatabase = true;
            try (AgendaStore store = new AgendaStore(context,"agenda-isolated-test.db",fake)) {
                for (String identity : new String[]{"alarm","reminder","reminder_direct","web"}) {
                    stage = identity;
                    ConversationMemory memory = new ConversationMemory();
                    ConversationMemory.Lease lease = memory.acquire(SystemClock.elapsedRealtime());
                    VoiceConnection opened = null;
                    long connectUntil = SystemClock.elapsedRealtime()+2000;
                    while (opened == null) {
                        try { opened = VoiceConnection.open(context,"synthetic_pcm",memory.snapshot(lease,SystemClock.elapsedRealtime()),null,false,true); }
                        catch (java.net.ConnectException retry) { if (SystemClock.elapsedRealtime() >= connectUntil) throw retry; SystemClock.sleep(30); }
                        catch (java.io.IOException retry) {
                            if (!(retry.getCause() instanceof java.net.ConnectException) || SystemClock.elapsedRealtime() >= connectUntil) throw retry;
                            SystemClock.sleep(30);
                        }
                    }
                    try (VoiceConnection connection = opened) {
                        connection.socket.setSoTimeout(10000);
                        ConversationChannel channel = new ConversationChannel(memory,lease,connection.ready);
                        AgendaActionChannel actions = new AgendaActionChannel(channel.sessionId,channel.contextId,
                            connection.ready.getLong("server_time_ms")+90000);
                        JSONObject thinking = VoiceConnection.readEvent(connection.input); channel.human(thinking,SystemClock.elapsedRealtime());
                        actions.human(thinking,SystemClock.elapsedRealtime(),System.currentTimeMillis());
                        JSONObject event = VoiceConnection.readEvent(connection.input); channel.verify(event);
                        AgendaEntry created = null;
                        if (!identity.equals("web")) {
                            check("agenda_execute".equals(event.optString("type")));
                            JSONObject corrupt = new JSONObject(event.toString()).put("nonce","invalid");
                            rejected(() -> actions.execution(corrupt,SystemClock.elapsedRealtime(),System.currentTimeMillis()));
                            AgendaPermit permit = actions.execution(event,SystemClock.elapsedRealtime(),System.currentTimeMillis()); check(permit != null);
                            check(actions.execution(event,SystemClock.elapsedRealtime(),System.currentTimeMillis()) == null);
                            rejected(() -> actions.timerExecution());
                            check(store.create(permit) == AgendaStore.Status.CREATED); created = store.find(permit.id); check(created != null);
                            JSONObject packet = actions.result(permit,AgendaStore.Status.CREATED);
                            VoiceConnection.writeFrame(connection.output,3,packet.toString().getBytes("UTF-8"));
                            event = VoiceConnection.readEvent(connection.input); channel.verify(event);
                        }
                        check("say".equals(event.optString("type")) && actions.speech(event));
                        final JSONObject spoken = event;
                        rejected(() -> actions.speech(new JSONObject(spoken.toString()).put("actions_executed",2)));
                        if (created != null) {
                            check("action_result".equals(event.optString("close_reason")) && event.getInt("actions_executed") == 1 && !event.optBoolean("offered_more",true));
                            // Explicit software completion simulation, never a TTS callback or heard delivery.
                            try (SQLiteDatabase db = context.openOrCreateDatabase("agenda-isolated-test.db",Context.MODE_PRIVATE,null)) {
                                db.execSQL("UPDATE agenda SET state='DELIVERING',revision=revision+1 WHERE id=?",new Object[]{created.id});
                            }
                            check(store.finish(store.find(created.id),true) && store.find(created.id) == null);
                        } else check(event.getInt("actions_executed") == 0 && event.optString("close_reason").isEmpty() && event.optBoolean("offered_more"));
                        VoiceConnection.writeFrame(connection.output,3,"{\"action\":\"fixture_validated\"}".getBytes("UTF-8"));
                        actions.close(); channel.close();
                    } finally { memory.release(lease); }
                }
                check(schedules[0] == 3); for (long count : store.counts().values()) check(count == 0);
            }
            check(timers.equals(TimerStore.prefs(context).getAll()) && receipts.equals(TimerStore.actionReceipts(context).getAll()));
            result.putBoolean("passed",true); result.putInt("cases",4); result.putBoolean("pinned_tls",true);
            result.putBoolean("session_actor_simulated",true); result.putBoolean("native_result_simulated",true);
            result.putBoolean("real_timers_unchanged",true);
        } catch (Exception failure) {
            result.putBoolean("passed",false); result.putString("error",failure.getClass().getSimpleName()); result.putString("failure_stage",stage);
        } finally {
            boolean cleaned = !ownedDatabase || !context.getDatabasePath("agenda-isolated-test.db").exists() || context.deleteDatabase("agenda-isolated-test.db");
            result.putBoolean("isolated_storage_cleaned",cleaned); if (!cleaned) result.putBoolean("passed",false);
        }
        result.putBoolean("microphone_opened",false); result.putBoolean("audio_played",false);
        result.putBoolean("llm_started",false); result.putBoolean("asr_started",false); result.putBoolean("real_scheduler",false);
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
}
