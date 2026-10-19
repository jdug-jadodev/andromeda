package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Public synthesized speech through real Vosk/LFM/TLS and isolated SQLite; no microphone, playback or AlarmManager. */
public final class AlarmAsrInstrumentation extends Instrumentation {
    private final AtomicBoolean sending = new AtomicBoolean();
    private Thread sender;
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void sendFixture(VoiceConnection connection, File file) throws Exception {
        byte[] pcm = ConversationInstrumentation.readPcm(file); long start = SystemClock.elapsedRealtime(); sending.set(true);
        sender = new Thread(() -> {
            try {
                int total = pcm.length + 16000 * 2 * 2;
                for (int cursor = 0; cursor < total && sending.get(); cursor += 640) {
                    long target = start + (cursor / 2 + 320) * 1000L / 16000;
                    SystemClock.sleep(Math.max(0, target - SystemClock.elapsedRealtime()));
                    if (!sending.get()) break;
                    byte[] frame = new byte[648]; ByteBuffer.wrap(frame).putLong(SystemClock.elapsedRealtime());
                    if (cursor < pcm.length) System.arraycopy(pcm, cursor, frame, 8, Math.min(640, pcm.length - cursor));
                    VoiceConnection.writeFrame(connection.output, 4, frame);
                }
            } catch (Exception ignored) { /* The main socket assertions report any incomplete fixture. */ }
        }, "public-alarm-pcm");
        sender.start();
    }
    private void stopSender() throws Exception {
        sending.set(false); if (sender != null) sender.join(1500); check(sender == null || !sender.isAlive(), "Public PCM sender stayed active");
    }
    private static void control(VoiceConnection connection, String action, String turn) throws Exception {
        VoiceConnection.writeFrame(connection.output, 3, new JSONObject().put("action", action).put("turn_id", turn)
            .put("at_ms", SystemClock.elapsedRealtime()).toString().getBytes("UTF-8"));
    }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        VoiceConnection connection = null; ConversationChannel channel = null; AgendaActionChannel actions = null;
        ConversationMemory memory = new ConversationMemory(); ConversationMemory.Lease lease = null;
        AgendaStore isolated = null; boolean ownedDatabase = false; final int[] schedules = {0};
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        Map<String, ?> timerReceipts = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        Map<String, Long> agendaBefore = null;
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active
                && !SilenceListener.recording() && !AudioInputGate.occupied(), "Human audio/action service active");
            try (AgendaStore real = new AgendaStore(context)) { agendaBefore = new HashMap<>(real.counts()); }
            check(agendaBefore.get("ALARM") == 0 && agendaBefore.get("REMINDER") == 0, "Human agenda not empty");
            check(!context.getDatabasePath("agenda-isolated-test.db").exists(), "Prior isolated alarm database exists");
            File fixture = new File(context.getFilesDir(), "synthetic/andromeda_fixture_alarm_relative.wav");
            check(fixture.isFile() && fixture.length() > 44, "Public alarm fixture missing");
            AgendaStore.Scheduler fake = new AgendaStore.Scheduler() {
                public void schedule(AgendaEntry entry) { schedules[0]++; }
                public void cancel(String id) {}
            };
            // Reuse the one isolated name allowed by AgendaStore. Do not widen
            // its database/scheduler allowlist for this diagnostic.
            ownedDatabase = true; isolated = new AgendaStore(context, "agenda-isolated-test.db", fake);
            lease = memory.acquire(SystemClock.elapsedRealtime());
            connection = VoiceConnection.open(context, "synthetic_pcm", memory.snapshot(lease, SystemClock.elapsedRealtime()), null, false, true);
            connection.socket.setSoTimeout(35000); channel = new ConversationChannel(memory, lease, connection.ready);
            actions = new AgendaActionChannel(channel.sessionId, channel.contextId,
                connection.ready.getLong("server_time_ms") + connection.ready.getInt("seconds") * 1000L);
            sendFixture(connection, fixture);
            JSONObject thinking = null; AgendaPermit permit = null; boolean closed = false;
            while (!closed) {
                JSONObject event = VoiceConnection.readEvent(connection.input); String type = event.optString("type");
                check(VoiceSessionPolicy.toolsEventAllowed(type) && !event.has("tool_call_id"), "Unexpected tools event");
                channel.verify(event);
                if ("state".equals(type)) continue;
                if ("thinking".equals(type)) {
                    stopSender(); thinking = event; channel.human(event, SystemClock.elapsedRealtime());
                    actions.human(event, SystemClock.elapsedRealtime(), System.currentTimeMillis());
                    result.putString("public_fixture_asr", event.getString("text"));
                } else if ("agenda_execute".equals(type)) {
                    check(thinking != null && permit == null, "Alarm execution outside one public turn");
                    permit = actions.execution(event, SystemClock.elapsedRealtime(), System.currentTimeMillis());
                    check(permit != null && permit.value.kind == AgendaEntry.Kind.ALARM && permit.value.text.isEmpty()
                        && permit.value.delay == 120000, "Public alarm permit changed meaning");
                    AgendaStore.Status status = isolated.create(permit); check(status == AgendaStore.Status.CREATED, "Isolated alarm creation failed");
                    VoiceConnection.writeFrame(connection.output, 3, actions.result(permit, status).toString().getBytes("UTF-8"));
                } else if ("say".equals(type)) {
                    check(permit != null && actions.speech(event) && event.getInt("actions_executed") == 1
                        && "agenda_result".equals(event.optString("timer_control"))
                        && "action_result".equals(event.optString("close_reason")) && !event.optBoolean("offered_more", true),
                        "Alarm success was not tied to the isolated Android receipt");
                    control(connection, "tts_started", event.getString("turn_id"));
                    control(connection, "tts_done", event.getString("turn_id"));
                    result.putBoolean("tts_completion_simulated", true);
                } else if ("conversation_closed".equals(type)) {
                    check(permit != null && "action_result".equals(event.optString("reason")), "Alarm action did not close");
                    check(channel.end(event.getString("turn_id"), SystemClock.elapsedRealtime()), "Public action turn did not close");
                    closed = true;
                } else throw new IllegalStateException("Synthetic alarm failed: " + event.optString("reason"));
            }
            check(schedules[0] == 1 && isolated.counts().get("ALARM") == 1 && isolated.counts().get("REMINDER") == 0,
                "Wrong isolated alarm count");
            try (AgendaStore real = new AgendaStore(context)) { check(agendaBefore.equals(real.counts()), "Human agenda changed"); }
            check(timers.equals(TimerStore.prefs(context).getAll()) && timerReceipts.equals(TimerStore.actionReceipts(context).getAll()),
                "Human timers changed");
            result.putBoolean("passed", true); result.putBoolean("real_vosk", true); result.putBoolean("real_lfm", true);
            result.putBoolean("pinned_tls", true); result.putBoolean("isolated_alarm_created", true);
            result.putBoolean("android_receipt_acknowledged", true); result.putBoolean("direct_request_closed", true);
            result.putBoolean("real_agenda_unchanged", true); result.putBoolean("real_timers_unchanged", true);
        } catch (Exception failure) {
            result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            sending.set(false); if (sender != null) try { sender.join(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (actions != null) actions.close(); if (channel != null) channel.close();
            if (connection != null) try { connection.close(); } catch (IOException ignored) {}
            if (isolated != null) isolated.close(); if (lease != null) memory.release(lease);
            boolean cleaned = !ownedDatabase || !context.getDatabasePath("agenda-isolated-test.db").exists()
                || context.deleteDatabase("agenda-isolated-test.db");
            result.putBoolean("isolated_storage_cleaned", cleaned); if (!cleaned) result.putBoolean("passed", false);
        }
        result.putBoolean("microphone_opened", false); result.putBoolean("audio_played", false);
        result.putBoolean("real_scheduler", false); result.putBoolean("real_alarm_sounded", false);
        result.putBoolean("human_request_used", false);
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
