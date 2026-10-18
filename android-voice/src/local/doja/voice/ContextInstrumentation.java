package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;
import org.json.*;

/** Real TLS/context transfer with mock speech receipts. Never starts audio/model/services. */
public final class ContextInstrumentation extends Instrumentation {
    private MainActivity activity;
    private VoiceConnection connection;
    private ConversationMemory.Lease lease;
    private final Set<String> owned = new HashSet<>();
    private final ConversationMemory memory = ConversationContexts.MEMORY;
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private AlertDialog dialog() throws Exception {
        Field field = MainActivity.class.getDeclaredField("optionsDialog"); field.setAccessible(true);
        return (AlertDialog)field.get(activity);
    }
    private void control(String action, String id) throws Exception {
        VoiceConnection.writeFrame(connection.output, 3, new JSONObject().put("action", action).put("turn_id", id)
            .put("at_ms", SystemClock.elapsedRealtime()).toString().getBytes("UTF-8"));
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        String original = null;
        try {
            check(!VoiceService.active && !VoiceService.microphoneRecording && !RingService.ringing, "Real audio is active");
            check(memory.list(SystemClock.elapsedRealtime()).isEmpty(), "Refusing to overwrite existing conversation RAM");
            for (int phase = 0; phase < 4; phase++) {
                if (phase == 3) check(memory.create(SystemClock.elapsedRealtime()) != null, "New conversation failed");
                lease = memory.acquire(SystemClock.elapsedRealtime()); owned.add(lease.conversationId);
                ConversationMemory.Snapshot snapshot = memory.snapshot(lease, SystemClock.elapsedRealtime());
                if (phase == 0) original = snapshot.conversationId;
                connection = VoiceConnection.open(context, "synthetic_pcm", snapshot);
                check(connection.ready.optBoolean("test_fixture"), "Requires dedicated no-model retention fixture");
                check(connection.ready.getInt("restored_turns") == new int[]{0, 1, 2, 0}[phase], "Resume count differs");
                ConversationChannel channel = new ConversationChannel(memory, lease, connection.ready);
                if (phase < 2) {
                    JSONObject thinking = VoiceConnection.readEvent(connection.input); channel.verify(thinking);
                    check("thinking".equals(thinking.getString("type")), "Expected public human turn");
                    ConversationTurnPolicy policy = new ConversationTurnPolicy();
                    String id = thinking.getString("turn_id");
                    check(policy.begin(id, thinking.getLong("turn_seq"), thinking.getLong("speech_end_ms"),
                        thinking.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Invalid scoped turn");
                    channel.human(thinking, SystemClock.elapsedRealtime());
                    if (phase == 0) {
                        JSONObject say = VoiceConnection.readEvent(connection.input); channel.verify(say);
                        check(policy.acceptReply(id, say.getLong("turn_seq"), say.getLong("speech_end_ms"),
                            say.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Reply rejected");
                        check(policy.acceptStart(id, SystemClock.elapsedRealtime()), "Mock start rejected");
                        // Deliberately mock receipts; no TTS object or AudioRecord exists.
                        control("tts_started", id);
                        check(channel.answer(id, say.getString("text"), SystemClock.elapsedRealtime()), "Completed answer not retained");
                        control("tts_done", id);
                    }
                    JSONObject foreign = new JSONObject(thinking.toString()).put("context_id", UUID.randomUUID().toString().replace("-", ""));
                    try { channel.verify(foreign); throw new IllegalStateException("Foreign context accepted"); }
                    catch (java.io.IOException expected) {}
                } else {
                    JSONObject state = VoiceConnection.readEvent(connection.input); channel.verify(state);
                    if (phase == 2) check(state.optBoolean("pending_human_restored"), "Interrupted question not restored");
                }
                connection.close(); connection = null; channel.close(); lease = null;
            }
            check(memory.select(original, SystemClock.elapsedRealtime()), "Original conversation disappeared");
            lease = memory.acquire(SystemClock.elapsedRealtime());
            ConversationMemory.Snapshot restored = memory.snapshot(lease, SystemClock.elapsedRealtime());
            check(restored.turns.size() == 2 && restored.turns.get(0).assistant != null && restored.turns.get(1).assistant == null,
                "Completed and interrupted turns not distinguished");
            memory.release(lease); lease = null;
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> { activity.showOptions(); activity.contextOptions(); });
            waitForIdleSync();
            final AlertDialog selection = dialog();
            check(selection != null && selection.getListView().getCount() == 4, "Context selector did not show two independent chats");
            runOnMainSync(() -> activity.expireOptionsAt(activity.policy.optionsDeadline()));
            waitForIdleSync();
            check(dialog() == null && !activity.policy.optionsVisible(), "Context dialog survived options inactivity timeout");
            check(!activity.isFinishing() && !activity.isDestroyed(), "Context controls exited the clock");
            runOnMainSync(() -> { activity.showOptions(); activity.contextOptions(); });
            waitForIdleSync();
            final AlertDialog forgetSelection = dialog();
            runOnMainSync(() -> forgetSelection.getListView().performItemClick(null, 3, 3));
            waitForIdleSync();
            final AlertDialog confirmation = dialog();
            check(confirmation != null && confirmation != forgetSelection, "Forget did not require confirmation");
            runOnMainSync(() -> confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            waitForIdleSync();
            check(!memory.select(original, SystemClock.elapsedRealtime()) && memory.list(SystemClock.elapsedRealtime()).size() == 1,
                "Forget did not remove only the selected conversation");
            check(timers.equals(TimerStore.prefs(context).getAll()), "Context test changed persistent timers");
            check(!VoiceService.active && !VoiceService.microphoneRecording, "Context controls opened microphone");
            result.putBoolean("passed", true); result.putBoolean("tls_resume", true);
            result.putBoolean("pending_human_retained", true); result.putBoolean("conversations_isolated", true);
            result.putBoolean("options_timeout", true); result.putBoolean("timers_unchanged", true);
            result.putBoolean("confirmed_forget", true);
            result.putBoolean("microphone_opened", false); result.putBoolean("model_started", false);
            result.putBoolean("tts_initialized", false); result.putBoolean("audio_played", false);
            result.putBoolean("mock_speech_receipts", true); result.putBoolean("semantic_memory_verified", false);
        } catch (Exception error) {
            result.putBoolean("passed", false); result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (connection != null) VoiceSocketCleanup.closeAsync(connection.socket);
            if (lease != null) memory.release(lease);
            for (String id : owned) memory.forget(id, SystemClock.elapsedRealtime());
            ConversationContexts.scheduleExpiry();
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
