package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.*;

/** Isolated durable-store/AlarmManager check. No microphone, TTS or audible alarm. */
public final class TimerAuthorizationInstrumentation extends Instrumentation {
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static String identity() { return UUID.randomUUID().toString().replace("-", ""); }
    interface InvalidOperation { void run() throws Exception; }
    private static void rejected(InvalidOperation operation) throws Exception {
        try { operation.run(); } catch (Exception expected) { return; }
        throw new IllegalStateException("Malformed/unauthorized timer wire data accepted");
    }
    private static void checkWire() throws Exception {
        long now = SystemClock.elapsedRealtime();
        String session = identity(), chat = identity(), origin = identity(), timer = UUID.randomUUID().toString();
        TimerActionChannel wire = new TimerActionChannel(session, chat, now + 90000);
        wire.human(new JSONObject().put("turn_id", origin).put("turn_seq", 1).put("speech_end_ms", now)
            .put("text", "sí"));
        JSONObject p = new JSONObject().put("proposal_id", identity()).put("timer_id", timer).put("nonce", identity())
            .put("session_id", session).put("context_id", chat).put("origin_turn_id", origin).put("origin_sequence", 1)
            .put("seconds", 600).put("expires_ms", now + 4000);
        JSONObject grant = new JSONObject().put("session_id", session).put("context_id", chat).put("proposal", p)
            .put("request_turn_id", origin).put("request_sequence", 1).put("speech_end_ms", now).put("execute_before_ms", now + 4000);
        rejected(() -> wire.execution(grant, now));
        wire.human(new JSONObject().put("turn_id", origin).put("turn_seq", 1).put("speech_end_ms", now)
            .put("text", "pon un temporizador de diez minutos"));
        rejected(() -> wire.execution(new JSONObject(grant.toString()).put("proposal", new JSONObject(p.toString()).put("seconds", "600")), now));
        rejected(() -> wire.execution(new JSONObject(grant.toString()).put("proposal", new JSONObject(p.toString()).put("seconds", true)), now));
        rejected(() -> wire.execution(new JSONObject(grant.toString()).put("proposal", new JSONObject(p.toString()).put("context_id", identity())), now));
        rejected(() -> wire.execution(new JSONObject(grant.toString()).put("execute_before_ms", now + 4001), now));
        TimerActionChannel.Execution execution = wire.execution(grant, now);
        check(execution != null && wire.execution(grant, now) == null, "Duplicate execution accepted");
        rejected(() -> wire.validateSpeech(new JSONObject().put("turn_id", origin).put("timer_control", "result")
            .put("actions_executed", 1).put("text", "Temporizador de 600 segundos activado.").put("close_reason", "action_result").put("offered_more", false)));
        TimerCreation.Receipt fake = new TimerCreation.Receipt(timer, 600, TimerCreation.Status.CREATED);
        JSONObject packet = wire.result(execution, fake);
        check(packet.getInt("seconds") == 600 && packet.getString("nonce").equals(p.getString("nonce")), "Wire result identity lost");
        rejected(() -> wire.validateSpeech(new JSONObject().put("turn_id", origin).put("timer_control", "result")
            .put("actions_executed", 1).put("text", TimerActionChannel.resultText(fake)).put("offered_more", true)));
        wire.validateSpeech(new JSONObject().put("turn_id", origin).put("timer_control", "result")
            .put("actions_executed", 1).put("text", TimerActionChannel.resultText(fake)).put("close_reason", "action_result").put("offered_more", false));
        wire.close();
        check(!execution.permit.begin(now), "Revoked wire permit began execution");
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static TimerActionPolicy.Permit permit(String timerId) {
        String session = identity(), chat = identity(), origin = identity();
        TimerActionPolicy policy = new TimerActionPolicy(session, chat); long now = SystemClock.elapsedRealtime();
        TimerActionPolicy.Proposal proposal = new TimerActionPolicy.Proposal(identity(), timerId, identity(), session, chat, origin, 1, 600, now + 4000);
        TimerActionPolicy.Permit permit = policy.claimRequest(new TimerActionPolicy.Grant(proposal, origin, 1, now, now + 4000),
            "pon un temporizador de diez minutos", origin, 1, now);
        check(permit != null, "Complete request authorization rejected"); return permit;
    }
    public void onStart() {
        Context real = getTargetContext(); Bundle result = new Bundle();
        Map<String, ?> before = new HashMap<>(TimerStore.prefs(real).getAll());
        Map<String, ?> receiptsBefore = new HashMap<>(TimerStore.actionReceipts(real).getAll());
        String namespace = "timer_authorization_" + UUID.randomUUID();
        Context isolated = new ContextWrapper(real) {
            public SharedPreferences getSharedPreferences(String name, int mode) {
                if (!"timers".equals(name) && !"timer_action_receipts".equals(name)) throw new IllegalArgumentException("Unexpected test storage");
                return real.getSharedPreferences(namespace + "_" + name, mode);
            }
        };
        String id = UUID.randomUUID().toString();
        while (before.containsKey(id)) id = UUID.randomUUID().toString();
        try {
            check(!VoiceService.active && !RingService.ringing, "A real voice/alarm session is active");
            checkWire(); result.putBoolean("wire_rejections_passed", true);
            TimerActionPolicy.Permit authorization = permit(id);
            check(TimerStore.createAuthorized(isolated, authorization).status == TimerCreation.Status.CREATED, "Real scheduler/store did not acknowledge");
            String saved = TimerStore.prefs(isolated).getString(id, "");
            JSONObject timer = new JSONObject(saved);
            check(timer.getInt("seconds") == 600 && timer.getBoolean("confirmed_action"), "Wrong durable timer");
            check("CREATED".equals(new JSONObject(TimerStore.actionReceipts(isolated).getString(id, "")).getString("status")), "Missing durable receipt");
            check(TimerStore.createAuthorized(isolated, authorization).status == TimerCreation.Status.REJECTED, "Permit executed twice");
            check(TimerStore.createAuthorized(isolated, permit(id)).status == TimerCreation.Status.DUPLICATE, "Timer ID executed twice");
            check(saved.equals(TimerStore.prefs(isolated).getString(id, "")), "Duplicate moved deadline");
            TimerStore.actionReceipts(isolated).edit().putString(id, new JSONObject().put("seconds", 600)
                .put("status", "PREPARING").put("wall_created", System.currentTimeMillis()).toString()).commit();
            timer.put("elapsed_due", 0);
            check(TimerStore.prefs(isolated).edit().putString(id, timer.toString()).commit(), "Crash fixture failed");
            TimerStore.restore(isolated);
            check(new JSONObject(TimerStore.prefs(isolated).getString(id, "")).getLong("elapsed_due") > SystemClock.elapsedRealtime(), "Authorized PREPARING timer not recovered");
            check(TimerStore.consume(isolated, id) && !TimerStore.consume(isolated, id), "Duplicate delivery consumed twice");
            check(TimerStore.createAuthorized(isolated, permit(id)).status == TimerCreation.Status.DUPLICATE
                && !TimerStore.prefs(isolated).contains(id), "Consumed timer resurrected");
            result.putBoolean("passed", true);
        } catch (Exception failure) {
            result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            try { TimerStore.cancelAlarm(isolated, id); }
            catch (Exception failure) { result.putBoolean("passed", false); result.putString("error", "Could not cancel isolated test alarm"); }
            if (!real.deleteSharedPreferences(namespace + "_timers") || !real.deleteSharedPreferences(namespace + "_timer_action_receipts"))
                result.putBoolean("passed", false);
            boolean preserved = before.equals(TimerStore.prefs(real).getAll()) && receiptsBefore.equals(TimerStore.actionReceipts(real).getAll());
            result.putBoolean("real_timers_unchanged", preserved);
            if (!preserved) result.putBoolean("passed", false);
        }
        result.putString("test", "isolated_timer_authorization");
        result.putBoolean("microphone_opened", false); result.putBoolean("audio_played", false);
        result.putBoolean("audible_alarm_started", false); result.putBoolean("isolated_preferences", true);
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
