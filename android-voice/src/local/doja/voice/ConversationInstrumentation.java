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
    private boolean interruptFirst;
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
    private TimerActionChannel timers;
    private Context timerStorage;
    private final Set<String> timerIds = new HashSet<>();
    private SilenceListener interruptionAsr;
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments); mode = arguments == null ? "normal" : arguments.getString("mode", "normal");
        interruptFirst = arguments != null && "true".equals(arguments.getString("interrupt_first")); start();
    }
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
    static byte[] readPcm(File file) throws Exception {
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
        Map<String, ?> receiptsBefore = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        String timerNamespace = "timer_conversation_" + UUID.randomUUID();
        boolean timerMode = mode.startsWith("timer_");
        JSONArray rows = new JSONArray();
        timerStorage = new ContextWrapper(context) {
            public SharedPreferences getSharedPreferences(String name, int mode) {
                if (!"timers".equals(name) && !"timer_action_receipts".equals(name)) throw new IllegalArgumentException("Unexpected test storage");
                return context.getSharedPreferences(timerNamespace + "_" + name, mode);
            }
        };
        try {
            check(Arrays.asList("normal", "cancel_inference", "late_start", "close", "thanks", "decline", "yes", "entry_new", "entry_resume", "entry_missing", "timer_create", "timer_yes", "timer_missing", "timer_query").contains(mode), "Invalid synthetic mode");
            check(!VoiceService.active && !RingService.ringing, "A real session/alarm is active");
            if (interruptFirst) {
                interruptionAsr = new SilenceListener(context, () -> {});
                check(interruptionAsr.awaitReady(SystemClock.elapsedRealtime() + 12000), "Local interrupt ASR failed while LFM was loaded");
                result.putBoolean("interrupt_asr_loaded_with_lfm", true);
            }
            initializeVoice();
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class).putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // A synthetic backend must refuse the normal microphone handshake,
            // before any caller would construct AudioRecord.
            boolean wrongSourceRejected = false;
            try (VoiceConnection wrong = VoiceConnection.open(context, "microphone", ConversationMemory.emptySnapshot(),
                    Collections.emptyList(), timerMode)) {}
            catch (IOException expected) { wrongSourceRejected = true; }
            check(wrongSourceRejected, "Synthetic backend accepted microphone source");
            result.putBoolean("wrong_source_rejected", true);
            boolean voiceEntry = mode.startsWith("entry_") || timerMode;
            String seededId = "";
            if (voiceEntry) {
                long at = SystemClock.elapsedRealtime();
                if (!timerMode) {
                    ConversationMemory.Lease seed = memory.acquire(at - 2000); seededId = seed.conversationId;
                    String turn = UUID.randomUUID().toString().replace("-", "");
                    check(memory.human(seed, turn, "Las luces del patio son azules.", at - 1900, at - 1800), "Public seed failed");
                    check(memory.answer(seed, turn, "Hablamos de las luces del patio.", at - 1700) && memory.end(seed, turn, at - 1600), "Public seed close failed");
                }
                ConversationMemory.Snapshot fresh = ConversationMemory.emptySnapshot();
                List<ConversationMemory.Snapshot> catalog = memory.catalog(at);
                connection = VoiceConnection.open(context, "synthetic_pcm", fresh, catalog, timerMode);
                channel = new ConversationChannel(memory, fresh, catalog, connection.ready);
            } else {
                lease = memory.acquire(SystemClock.elapsedRealtime());
                connection = VoiceConnection.open(context, "synthetic_pcm", memory.snapshot(lease, SystemClock.elapsedRealtime()));
                channel = new ConversationChannel(memory, lease, connection.ready);
            }
            if (interruptFirst) check(connection.ready.optBoolean("speech_interrupt"), "Backend has no interruption support");
            if (timerMode) timers = new TimerActionChannel(channel.sessionId, channel.contextId,
                connection.ready.getLong("server_time_ms") + connection.ready.getInt("seconds") * 1000L);
            connection.socket.setSoTimeout(35000);
            boolean closingMode = voiceEntry || Arrays.asList("close", "thanks", "decline", "yes").contains(mode);
            String closingReason = "timer_create".equals(mode) ? "action_result"
                : !timerMode && (voiceEntry || Arrays.asList("decline", "yes").contains(mode)) ? "declined_more" : "explicit_close";
            int wakeActivations = 0, followupRearms = 0;
            String[] fixtures = "timer_create".equals(mode) ? new String[]{"timer_create"}
                : "timer_yes".equals(mode) ? new String[]{"ai_chat", "entry_yes", "dialogue_close"}
                : "timer_missing".equals(mode) ? new String[]{"timer_missing", "dialogue_close"}
                : "timer_query".equals(mode) ? new String[]{"dialogue_joke", "dialogue_close"}
                : "normal".equals(mode) ? new String[]{"ai_chat", "dialogue_intro", "dialogue_followup"}
                : "entry_resume".equals(mode) ? new String[]{"entry_search", "entry_yes", "entry_followup", "dialogue_no"}
                : "entry_missing".equals(mode) ? new String[]{"entry_missing", "dialogue_no"}
                : "entry_new".equals(mode) ? new String[]{"ai_chat", "dialogue_no"}
                : "yes".equals(mode) ? new String[]{"ai_chat", "dialogue_yes", "dialogue_no"}
                : closingMode ? new String[]{"ai_chat", "close".equals(mode) ? "dialogue_close" : "thanks".equals(mode) ? "dialogue_thanks" : "dialogue_no"}
                : new String[]{"ai_chat"};
            for (String fixture : fixtures) {
                sendFixture(fixture); JSONObject thinking = null;
                while (true) {
                    JSONObject event = VoiceConnection.readEvent(connection.input); String type = event.optString("type");
                    check((timerMode ? VoiceSessionPolicy.timerEventAllowed(type) : VoiceSessionPolicy.conversationEventAllowed(type))
                        && !event.has("tool_call_id"), "Unexpected executor event");
                    if ("context_selected".equals(type)) {
                        check(voiceEntry, "Unexpected selection in direct context test");
                         channel.select(event, SystemClock.elapsedRealtime()); lease = channel.lease;
                        if (timerMode) {
                            timers.close(); timers = new TimerActionChannel(channel.sessionId, channel.contextId,
                                connection.ready.getLong("server_time_ms") + connection.ready.getInt("seconds") * 1000L);
                        }
                        boolean resumed = "entry_resume".equals(mode);
                        check(event.getString("selection").equals(resumed ? "resume" : "new")
                            && event.getInt("restored_turns") == (resumed ? 1 : 0)
                            && (resumed == seededId.equals(lease.conversationId)), "Wrong entry history selected");
                        result.putString("entry_selection", event.getString("selection")); continue;
                    }
                    channel.verify(event);
                    if ("state".equals(type)) {
                        if ("command".equals(event.optString("state")) && "voice".equals(event.optString("trigger"))) wakeActivations++;
                        if ("command".equals(event.optString("state"))) present(FaceState.LISTENING, "ASR con audio público");
                    } else if ("thinking".equals(type)) {
                        stopSender(); thinking = event;
                        check(turns.begin(event.getString("turn_id"), event.getLong("turn_seq"), event.getLong("speech_end_ms"), event.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Invalid global turn deadline");
                        channel.human(event, SystemClock.elapsedRealtime());
                        if (timerMode) timers.human(event);
                        present(FaceState.THINKING, timerMode ? "LFM real · timer solo si petición válida" : "LFM real · sin acciones");
                        if ("cancel_inference".equals(mode)) {
                            long stopped = SystemClock.elapsedRealtime(); connection.close();
                            result.putLong("cancel_to_inference_down_ms", waitInferenceDown(stopped));
                            break;
                        }
                    } else if ("timer_execute".equals(type) && timerMode) {
                        check("timer_create".equals(mode), "A generic yes/missing duration authorized an action");
                        TimerActionChannel.Execution execution = timers.execution(event, SystemClock.elapsedRealtime());
                        check(execution != null, "Execution duplicated");
                        check(!timersBefore.containsKey(execution.grant.proposal.timerId), "Isolated ID overlaps a real timer");
                        timerIds.add(execution.grant.proposal.timerId);
                        TimerCreation.Receipt receipt = TimerStore.createAuthorized(timerStorage, execution.permit);
                        check(receipt.status == TimerCreation.Status.CREATED, "Android rejected the isolated timer");
                        check(timers.execution(event, SystemClock.elapsedRealtime()) == null, "Duplicate execution accepted");
                        JSONObject packet = timers.result(execution, receipt);
                        VoiceConnection.writeFrame(connection.output, 3, packet.toString().getBytes("UTF-8"));
                        result.putBoolean("android_creation_acknowledged", true);
                    } else if ("say".equals(type)) {
                        check(thinking != null && (timerMode || event.getInt("actions_executed") == 0), "Reply without a turn");
                        if (timerMode) timers.validateSpeech(event);
                        if (fixture.equals("timer_missing")) check("rejected".equals(event.optString("timer_control"))
                            && event.getString("text").contains("¿De cuánto tiempo"), "Missing duration was not clarified safely");
                        String reason = event.optString("close_reason");
                        boolean farewell = !reason.isEmpty();
                        boolean closingFixture = timerMode ? fixture.equals("dialogue_close") || "timer_create".equals(mode)
                            : Arrays.asList("dialogue_close", "dialogue_thanks", "dialogue_no").contains(fixture);
                        check(farewell == closingFixture && (!farewell || reason.equals(closingReason)), "Wrong farewell/offer scope");
                        boolean notice = thinking.optBoolean("administrative") || fixture.equals("entry_yes") || !event.optString("timer_control").isEmpty();
                        check(notice || event.optBoolean("offered_more") != farewell, "Missing follow-up offer or offer on farewell");
                        String id = event.getString("turn_id"); long end = event.getLong("speech_end_ms"), deadline = event.getLong("deadline_ms");
                        check(turns.acceptReply(id, event.getLong("turn_seq"), end, deadline, SystemClock.elapsedRealtime(), reason,
                            timerMode && "result".equals(event.optString("timer_control"))), "Late/duplicate reply");
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
                        boolean interrupted = interruptFirst && rows.length() == 0;
                        rows.put(new JSONObject().put("fixture", fixture).put("transcript", thinking.getString("text"))
                            .put("reply", event.getString("text")).put("estimated_end_to_first_synthetic_pcm_ms", firstPcm.get() - end)
                            .put("waveform_end_to_first_synthetic_pcm_ms", firstPcm.get() - waveformEnd)
                            .put("asr_end_estimate_minus_waveform_end_ms", end - waveformEnd).put("interrupted_control_simulated", interrupted));
                        if (!interrupted && event.optString("timer_control").isEmpty())
                            check(channel.answer(id, event.getString("text"), SystemClock.elapsedRealtime()), "Completed synthetic response not retained");
                        check(turns.finishSpeech(id, !interrupted), "Speech completion rejected");
                        control(interrupted ? "tts_interrupted" : "tts_done", id, SystemClock.elapsedRealtime()); speechId = "";
                        if (interrupted) {
                            check(memory.snapshot(lease, SystemClock.elapsedRealtime()).turns.get(0).assistant == null, "Unheard answer entered Android RAM");
                            result.putBoolean("first_speech_interrupted", true); result.putBoolean("unheard_answer_not_retained", true);
                        }
                        check(output.delete(), "Synthetic TTS output cleanup failed"); output = null;
                        if (farewell) {
                            result.putBoolean("action_result".equals(reason) ? "action_result_synthesized" : "farewell_synthesized", true);
                            continue;
                        }
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
                        check(closingMode && thinking != null && closingReason.equals(event.optString("reason"))
                            && closingReason.equals(turns.closeReason()), "Unexpected chat close");
                        String id = event.getString("turn_id");
                        check(turns.acceptClose(id, event.getLong("turn_seq"), event.getLong("speech_end_ms"),
                            event.getLong("deadline_ms"), SystemClock.elapsedRealtime()), "Foreign or late chat close");
                        check(channel.end(id, SystemClock.elapsedRealtime()), "Closed chat was not retained"); turns.clear();
                        if (lease == null) {
                            check("entry_missing".equals(mode) && memory.list(SystemClock.elapsedRealtime()).size() == 1,
                                "Missing lookup invented a new chat");
                            connection.close(); result.putLong("close_to_inference_down_ms", waitInferenceDown(SystemClock.elapsedRealtime()));
                            result.putBoolean("lookup_missing", true); result.putBoolean("no_chat_invented", true); break;
                        }
                        ConversationMemory.Info info = null;
                        for (ConversationMemory.Info item : memory.list(SystemClock.elapsedRealtime())) if (item.id.equals(lease.conversationId)) info = item;
                        check(info != null, "Selected chat disappeared");
                        check(info.closed && !info.current && info.turns == fixtures.length, "Closing did not release selected chat");
                        String closedId = lease.conversationId;
                        long stopped = SystemClock.elapsedRealtime(); connection.close();
                        result.putLong("close_to_inference_down_ms", waitInferenceDown(stopped));
                        ConversationMemory.Lease fresh = memory.acquire(SystemClock.elapsedRealtime());
                        check(!fresh.conversationId.equals(closedId) && memory.snapshot(fresh, SystemClock.elapsedRealtime()).turns.isEmpty(), "New chat inherited old context");
                        memory.release(fresh);
                        check(memory.select(closedId, SystemClock.elapsedRealtime()), "Closed chat cannot be resumed");
                        lease = memory.acquire(SystemClock.elapsedRealtime());
                        ConversationMemory.Snapshot resumed = memory.snapshot(lease, SystemClock.elapsedRealtime());
                          boolean administrativeFirst = interruptFirst || "timer_create".equals(mode) || "timer_missing".equals(mode);
                         check(resumed.turns.size() == fixtures.length && (administrativeFirst ? resumed.turns.get(0).assistant == null : resumed.turns.get(0).assistant != null)
                             && ("timer_create".equals(mode) ? resumed.turns.get(resumed.turns.size() - 1).assistant == null
                                 : resumed.turns.get(resumed.turns.size() - 1).assistant != null),
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
            check(receiptsBefore.equals(TimerStore.actionReceipts(context).getAll()), "Synthetic probe changed real action receipts");
            if (timerMode) {
                int expected = "timer_create".equals(mode) ? 1 : 0;
                check(timerIds.size() == expected && TimerStore.prefs(timerStorage).getAll().size() == expected,
                    "Wrong isolated timer count after conversation/model shutdown");
                result.putInt("isolated_timers_created", expected); result.putBoolean("real_timers_unchanged", true);
                result.putBoolean("isolated_timer_survived_model_shutdown", expected == 1);
                if (expected == 1) {
                    check(rows.length() == 1 && followupRearms == 0, "Standalone action required another turn or left conversation open");
                    result.putBoolean("direct_request_closed", true);
                }
            }
            android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(memory);
            result.putInt("app_pss_kib_at_end", memory.getTotalPss()); result.putString("turns", rows.toString());
            result.putString("test", "synthetic_conversation_" + mode); result.putBoolean("microphone_opened", false);
            result.putBoolean("audio_played", false); result.putBoolean("real_voice_acceptance", false);
            result.putBoolean("real_service_started", false);
            if ("normal".equals(mode) || closingMode)
                check(wakeActivations == 1 && followupRearms == rows.length() - (closingMode ? 1 : 0), "Extra wake activation or missing follow-up ASR");
            result.putInt("wake_activations", wakeActivations); result.putInt("followup_rearms", followupRearms);
            result.putBoolean("one_wake_only", "normal".equals(mode) || closingMode);
            result.putInt("actions_executed", timerIds.size()); result.putBoolean("passed", true);
        } catch (Exception error) {
            result.putString("turns", rows.toString());
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            sending.set(false); if (connection != null) try { connection.close(); } catch (IOException ignored) {}
            if (channel != null) channel.close();
            if (timers != null) timers.close();
            for (String id : timerIds) try { TimerStore.cancelAlarm(timerStorage, id); }
            catch (Exception cleanup) { result.putBoolean("passed", false); result.putString("error", "Isolated alarm cleanup failed"); }
            if (timerMode) {
                context.deleteSharedPreferences(timerNamespace + "_timers");
                context.deleteSharedPreferences(timerNamespace + "_timer_action_receipts");
                boolean cleaned = TimerStore.prefs(timerStorage).getAll().isEmpty() && TimerStore.actionReceipts(timerStorage).getAll().isEmpty();
                result.putBoolean("isolated_storage_cleaned", cleaned);
                if (!cleaned) { result.putBoolean("passed", false); result.putString("error", "Isolated storage cleanup failed"); }
            }
            if (lease != null) memory.release(lease);
            if (tts != null) { tts.stop(); tts.shutdown(); }
            if (output != null) output.delete();
            if (interruptionAsr != null) try {
                interruptionAsr.close();
                boolean released = interruptionAsr.awaitClosed(8000) && SilenceModels.activeUsers() == 0;
                result.putBoolean("interrupt_asr_released", released);
                if (!released) throw new IllegalStateException("Local interrupt ASR stayed loaded");
            } catch (Exception cleanup) { result.putBoolean("passed", false); result.putString("error", "Local ASR cleanup failed"); }
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
