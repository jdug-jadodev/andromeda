package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Real ASR + LFM + offline TTS using only fixed public WAVs. Never records or plays audio. */
public final class ConversationInstrumentation extends Instrumentation {
    private String mode;
    private TextToSpeech tts;
    private VoiceConnection connection;
    private MainActivity activity;
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final AtomicLong firstPcm = new AtomicLong();
    private final AtomicReference<CountDownLatch> synthesized = new AtomicReference<>();
    private final ConversationTurnPolicy turns = new ConversationTurnPolicy();
    private volatile String speechId = "";
    private Thread sender;
    private long waveformEnd;
    private final ConversationMemory memory = new ConversationMemory();
    private ConversationMemory.Lease lease;
    private ConversationChannel channel;
    public void onCreate(Bundle arguments) { super.onCreate(arguments); mode = arguments == null ? "normal" : arguments.getString("mode", "normal"); start(); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void control(String action, String id, long at) throws Exception {
        VoiceConnection.writeFrame(connection.output, 3, new JSONObject().put("action", action)
            .put("turn_id", id).put("at_ms", at).toString().getBytes("UTF-8"));
    }
    private void present(FaceState state, String text) {
        runOnMainSync(() -> activity.present(state, "PRUEBA SINTÉTICA · " + text));
    }
    private void initializeVoice() throws Exception {
        CountDownLatch initialized = new CountDownLatch(1); AtomicInteger status = new AtomicInteger(-1);
        runOnMainSync(() -> tts = new TextToSpeech(getTargetContext(), value -> { status.set(value); initialized.countDown(); }, "com.google.android.tts"));
        check(initialized.await(12, TimeUnit.SECONDS) && status.get() == TextToSpeech.SUCCESS, "Offline TTS initialization failed");
        List<Voice> voices = new ArrayList<>(tts.getVoices()); voices.sort(Comparator.comparing(Voice::getName));
        Voice selected = null;
        for (Voice voice : voices) if (!voice.isNetworkConnectionRequired() && "es".equals(voice.getLocale().getLanguage())) {
            if (selected == null) selected = voice;
            if ("US".equals(voice.getLocale().getCountry())) { selected = voice; break; }
        }
        check(selected != null && tts.setVoice(selected) != TextToSpeech.ERROR, "No offline Spanish voice");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {}
            public void onAudioAvailable(String id, byte[] audio) {
                if (!id.equals(speechId) || firstPcm.get() != 0) return;
                boolean nonzero = false;
                for (byte value : audio) if (value != 0) { nonzero = true; break; }
                if (!nonzero) return;
                long at = SystemClock.elapsedRealtime();
                if (!firstPcm.compareAndSet(0, at)) return;
                try {
                    check(turns.acceptStart(id, at), "Synthetic PCM missed deadline");
                    control("tts_started", id, at);
                } catch (Exception error) { failure.set(error.getMessage()); }
            }
            public void onDone(String id) { if (id.equals(speechId)) synthesized.get().countDown(); }
            public void onError(String id) { failure.set("Offline synthesis failed"); if (synthesized.get() != null) synthesized.get().countDown(); }
        });
    }
    private static byte[] readPcm(File file) throws Exception {
        byte[] wav = Files.readAllBytes(file.toPath()); ByteBuffer bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        check(wav.length > 44 && bytes.getInt(0) == 0x46464952 && bytes.getInt(8) == 0x45564157, "Invalid fixed WAV");
        int rate = 0, offset = 0, length = 0;
        for (int cursor = 12; cursor + 8 <= wav.length;) {
            int kind = bytes.getInt(cursor), size = bytes.getInt(cursor + 4), body = cursor + 8;
            check(size >= 0 && body + (long)size <= wav.length, "Invalid WAV chunk");
            if (kind == 0x20746d66) {
                check(size >= 16 && bytes.getShort(body) == 1 && bytes.getShort(body + 2) == 1 && bytes.getShort(body + 14) == 16, "Expected mono PCM16");
                rate = bytes.getInt(body + 4);
            } else if (kind == 0x61746164) { offset = body; length = size; }
            cursor = body + size + (size % 2);
        }
        check(rate >= 16000 && rate <= 48000 && length > 0, "Missing PCM in fixture");
        int samples = (int)((long)(length / 2) * 16000 / rate);
        check(samples <= 16000 * 12, "Fixture too long");
        ByteBuffer out = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double position = (double)i * rate / 16000; int left = (int)position, right = Math.min(left + 1, length / 2 - 1);
            short a = bytes.getShort(offset + left * 2), b = bytes.getShort(offset + right * 2);
            out.putShort((short)Math.round(a + (b - a) * (position - left)));
        }
        return out.array();
    }
    private void sendFixture(String name) throws Exception {
        byte[] pcm = readPcm(new File(getTargetContext().getFilesDir(), "synthetic/andromeda_fixture_" + name + ".wav"));
        ByteBuffer data = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int last = 0;
        for (int i = 0; i < pcm.length / 2; i++) if (Math.abs((int)data.getShort(i * 2)) > 32) last = i;
        long start = SystemClock.elapsedRealtime(); waveformEnd = start + last * 1000L / 16000;
        sending.set(true);
        sender = new Thread(() -> {
            try {
                // Trailing silence triggers real Vosk endpointing. No AudioRecord.
                int total = pcm.length + 16000 * 2 * 2;
                for (int cursor = 0; cursor < total && sending.get(); cursor += 640) {
                    long target = start + (cursor / 2 + 320) * 1000L / 16000;
                    SystemClock.sleep(Math.max(0, target - SystemClock.elapsedRealtime()));
                    if (!sending.get()) break;
                    byte[] frame = new byte[648]; ByteBuffer.wrap(frame).putLong(SystemClock.elapsedRealtime());
                    if (cursor < pcm.length) System.arraycopy(pcm, cursor, frame, 8, Math.min(640, pcm.length - cursor));
                    VoiceConnection.writeFrame(connection.output, 4, frame);
                }
            } catch (Exception error) { if (sending.get()) failure.set("Synthetic PCM transport failed"); }
        }, "synthetic-pcm");
        sender.start();
    }
    private void stopSender() throws Exception { sending.set(false); if (sender != null) sender.join(1500); check(sender == null || !sender.isAlive(), "Synthetic sender did not stop"); }
    private static long waitInferenceDown(long started) throws Exception {
        while (SystemClock.elapsedRealtime() - started < 2500) {
            try (Socket probe = new Socket()) { probe.connect(new InetSocketAddress("127.0.0.1", 11435), 100); }
            catch (IOException closed) { return SystemClock.elapsedRealtime() - started; }
            SystemClock.sleep(25);
        }
        throw new IllegalStateException("Dedicated inference survived cancellation");
    }
    public void onStart() {
        Bundle result = new Bundle(); File output = null;
        Context context = getTargetContext(); Map<String, ?> timersBefore = new HashMap<>(TimerStore.prefs(context).getAll());
        try {
            check(Arrays.asList("normal", "cancel_inference", "late_start", "close", "thanks").contains(mode), "Invalid synthetic mode");
            check(!VoiceService.active && !RingService.ringing, "A real session/alarm is active");
            initializeVoice();
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class).putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // A synthetic backend must refuse the normal microphone handshake,
            // before any caller would construct AudioRecord.
            boolean wrongSourceRejected = false;
            try (VoiceConnection wrong = VoiceConnection.open(context, "microphone")) {}
            catch (IOException expected) { wrongSourceRejected = true; }
            check(wrongSourceRejected, "Synthetic backend accepted microphone source");
            result.putBoolean("wrong_source_rejected", true);
            lease = memory.acquire(SystemClock.elapsedRealtime());
            connection = VoiceConnection.open(context, "synthetic_pcm", memory.snapshot(lease, SystemClock.elapsedRealtime()));
            channel = new ConversationChannel(memory, lease, connection.ready);
            connection.socket.setSoTimeout(35000);
            JSONArray rows = new JSONArray();
            boolean closingMode = "close".equals(mode) || "thanks".equals(mode);
            int wakeActivations = 0, followupRearms = 0;
            String[] fixtures = "normal".equals(mode) ? new String[]{"ai_chat", "dialogue_intro", "dialogue_followup"}
                : closingMode ? new String[]{"ai_chat", "close".equals(mode) ? "dialogue_close" : "dialogue_thanks"} : new String[]{"ai_chat"};
            for (String fixture : fixtures) {
                sendFixture(fixture); JSONObject thinking = null;
                while (true) {
                    JSONObject event = VoiceConnection.readEvent(connection.input); String type = event.optString("type");
                    check(VoiceSessionPolicy.conversationEventAllowed(type) && !event.has("tool_call_id"), "Unexpected executor event");
                    channel.verify(event);
                    if ("state".equals(type)) {
                        if ("command".equals(event.optString("state")) && "voice".equals(event.optString("trigger"))) wakeActivations++;
                        if ("command".equals(event.optString("state"))) present(FaceState.LISTENING, "ASR con audio público");
                    } else if ("thinking".equals(type)) {
                        stopSender(); thinking = event;
                        check(turns.begin(event.getString("turn_id"), event.getLong("turn_seq"), event.getLong("speech_end_ms"), event.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Invalid global turn deadline");
                        channel.human(event, SystemClock.elapsedRealtime());
                        present(FaceState.THINKING, "LFM real · sin acciones");
                        if ("cancel_inference".equals(mode)) {
                            long stopped = SystemClock.elapsedRealtime(); connection.close();
                            result.putLong("cancel_to_inference_down_ms", waitInferenceDown(stopped));
                            break;
                        }
                    } else if ("say".equals(type)) {
                        check(!fixture.equals("dialogue_close") && !fixture.equals("dialogue_thanks"), "Closing control was sent to LFM instead of closing");
                        check(thinking != null && event.getInt("actions_executed") == 0, "Reply without a turn");
                        String id = event.getString("turn_id"); long end = event.getLong("speech_end_ms"), deadline = event.getLong("deadline_ms");
                        check(turns.acceptReply(id, event.getLong("turn_seq"), end, deadline, SystemClock.elapsedRealtime()), "Late/duplicate reply");
                        check(!turns.acceptReply(id, event.getLong("turn_seq"), end, deadline, SystemClock.elapsedRealtime()), "Duplicate reply was accepted");
                        if ("late_start".equals(mode)) {
                            long rejected = SystemClock.elapsedRealtime(); control("tts_started", id, deadline + 1);
                            result.putLong("late_ack_to_inference_down_ms", waitInferenceDown(rejected));
                            break;
                        }
                        present(FaceState.PREPARING, "Síntesis offline · sin altavoz");
                        firstPcm.set(0); synthesized.set(new CountDownLatch(1)); speechId = id;
                        output = new File(context.getCacheDir(), "conversation-synthetic.wav");
                        check(tts.synthesizeToFile(event.getString("text"), new Bundle(), output, id) != TextToSpeech.ERROR, "Synthesis rejected");
                        check(synthesized.get().await(20, TimeUnit.SECONDS) && failure.get() == null && firstPcm.get() > 0 && firstPcm.get() <= deadline, "Synthesis/deadline failed: " + failure.get());
                        rows.put(new JSONObject().put("fixture", fixture).put("transcript", thinking.getString("text"))
                            .put("reply", event.getString("text")).put("estimated_end_to_first_synthetic_pcm_ms", firstPcm.get() - end)
                            .put("waveform_end_to_first_synthetic_pcm_ms", firstPcm.get() - waveformEnd)
                            .put("asr_end_estimate_minus_waveform_end_ms", end - waveformEnd));
                        check(channel.answer(id, event.getString("text"), SystemClock.elapsedRealtime()), "Completed synthetic response not retained");
                        control("tts_done", id, SystemClock.elapsedRealtime()); turns.clear(); speechId = "";
                        check(output.delete(), "Synthetic TTS output cleanup failed"); output = null;
                        while (true) {
                            JSONObject reset = VoiceConnection.readEvent(connection.input);
                            channel.verify(reset);
                            check("state".equals(reset.optString("type")), "Unexpected post-TTS event");
                            if ("command".equals(reset.optString("state")) && "followup".equals(reset.optString("trigger"))) { followupRearms++; break; }
                        }
                        // Follow-up ASR deliberately rejects the final 600 ms of
                        // queued speech-tail audio, just like real capture pause.
                        SystemClock.sleep(700);
                        present(FaceState.READY, "Turno sintético completado"); break;
                    } else if ("conversation_closed".equals(type)) {
                        check(closingMode && thinking != null && "explicit_close".equals(event.optString("reason")), "Unexpected chat close");
                        String id = event.getString("turn_id");
                        check(turns.acceptClose(id, event.getLong("turn_seq"), event.getLong("speech_end_ms"),
                            event.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Foreign or late chat close");
                        check(channel.end(id, SystemClock.elapsedRealtime()), "Closed chat was not retained"); turns.clear();
                        ConversationMemory.Info info = memory.list(SystemClock.elapsedRealtime()).get(0);
                        check(info.closed && !info.current && info.turns == 2, "Closing did not release selected chat");
                        String closedId = lease.conversationId;
                        long stopped = SystemClock.elapsedRealtime(); connection.close();
                        result.putLong("close_to_inference_down_ms", waitInferenceDown(stopped));
                        ConversationMemory.Lease fresh = memory.acquire(SystemClock.elapsedRealtime());
                        check(!fresh.conversationId.equals(closedId) && memory.snapshot(fresh, SystemClock.elapsedRealtime()).turns.isEmpty(), "New chat inherited old context");
                        memory.release(fresh);
                        check(memory.select(closedId, SystemClock.elapsedRealtime()), "Closed chat cannot be resumed");
                        lease = memory.acquire(SystemClock.elapsedRealtime());
                        ConversationMemory.Snapshot resumed = memory.snapshot(lease, SystemClock.elapsedRealtime());
                        check(resumed.turns.size() == 2 && resumed.turns.get(0).assistant != null && resumed.turns.get(1).assistant == null,
                            "Closed chat lost spoken history or fabricated a closing reply");
                        result.putBoolean("chat_closed", true); result.putBoolean("new_chat_isolated", true);
                        result.putBoolean("closed_chat_resumable", true);
                        result.putString("close_transcript", thinking.getString("text"));
                        present(FaceState.MUTED, "Charla cerrada · contexto retomable");
                        waitForIdleSync();
                        check(activity.idleLayer.getVisibility() == android.view.View.VISIBLE && !activity.isFinishing(), "Closing preview did not retain clock");
                        result.putBoolean("clock_after_close_preview", true); break;
                    } else throw new IllegalStateException("Synthetic turn failed: " + event.optString("reason"));
                }
                check(failure.get() == null, "PCM failed");
            }
            if ("normal".equals(mode)) {
                long stopped = SystemClock.elapsedRealtime(); connection.close();
                result.putLong("disconnect_to_inference_down_ms", waitInferenceDown(stopped));
            }
            check(!VoiceService.active && !VoiceService.microphoneRecording && !VoiceService.wakeListening, "Synthetic probe activated the real microphone");
            check(timersBefore.equals(TimerStore.prefs(context).getAll()), "Synthetic probe changed persistent timers");
            android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(memory);
            result.putInt("app_pss_kib_at_end", memory.getTotalPss()); result.putString("turns", rows.toString());
            result.putString("test", "synthetic_conversation_" + mode); result.putBoolean("microphone_opened", false);
            result.putBoolean("audio_played", false); result.putBoolean("real_voice_acceptance", false);
            result.putBoolean("real_service_started", false);
            if ("normal".equals(mode) || closingMode)
                check(wakeActivations == 1 && followupRearms == rows.length(), "Extra wake activation or missing follow-up ASR");
            result.putInt("wake_activations", wakeActivations); result.putInt("followup_rearms", followupRearms);
            result.putBoolean("one_wake_only", "normal".equals(mode) || closingMode);
            result.putInt("actions_executed", 0); finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage()); finish(Activity.RESULT_CANCELED, result);
        } finally {
            sending.set(false); if (connection != null) try { connection.close(); } catch (IOException ignored) {}
            if (channel != null) channel.close();
            if (lease != null) memory.release(lease);
            if (tts != null) { tts.stop(); tts.shutdown(); }
            if (output != null) output.delete();
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
    }
}
