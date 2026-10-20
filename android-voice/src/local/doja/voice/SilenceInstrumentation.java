package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.vosk.Recognizer;

/** Real Android Vosk, fixed public WAVs, NO AudioRecord/speaker/LLM or timer mutation. */
public final class SilenceInstrumentation extends Instrumentation {
    static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Context context = getTargetContext(); Bundle result = new Bundle();
        Map<String, ?> before = new HashMap<>(TimerStore.prefs(context).getAll());
        Map<String, ?> receiptsBefore = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        FaceState stateBefore = VoiceService.uiState; String statusBefore = VoiceService.lastStatus;
        VoiceService simulated = null;
        SilenceListener prepared = null;
        try {
            check(!VoiceService.active && !RingService.ringing && !SilenceListener.recording() && !AudioInputGate.occupied(), "Real audio is active");
            prepared = new SilenceListener(context, () -> {});
            check(prepared.awaitReady(SystemClock.elapsedRealtime() + 12000), "Production offline ASR preparation failed");
            check(SilenceListener.commandConfidence("{\"text\":\"silencio\",\"result\":[{\"word\":\"silencio\",\"conf\":0.95,\"start\":0,\"end\":0.8}]}") == .95,
                "Single-word recognition was rejected");
            check(SilenceListener.commandConfidence("{\"text\":\"silencio\",\"result\":[{\"word\":\"ruido\",\"conf\":1,\"start\":0,\"end\":0.8}]}") < 0,
                "Mismatched word evidence was accepted");
            JSONArray rows = new JSONArray();
            try (SilenceModels.Lease model = SilenceModels.acquire(context)) {
                for (String fixture : new String[]{"silence_command", "silence_bare", "silence_negated", "silence_reported", "timer_create"}) {
                    byte[] pcm = ConversationInstrumentation.readPcm(new File(context.getFilesDir(), "synthetic/andromeda_fixture_" + fixture + ".wav"));
                    boolean detected = false; JSONArray texts = new JSONArray();
                    SilenceWindowPolicy policy = new SilenceWindowPolicy(); String epoch = UUID.randomUUID().toString();
                    check(policy.begin(epoch, SystemClock.elapsedRealtime() + 30000, SystemClock.elapsedRealtime()), "Synthetic window rejected");
                    try (Recognizer recognizer = new Recognizer(model.value, 16000)) {
                        recognizer.setWords(true);
                        for (int cursor = 0; cursor < pcm.length + 64000; cursor += 640) {
                            byte[] frame = new byte[640];
                            if (cursor < pcm.length) System.arraycopy(pcm, cursor, frame, 0, Math.min(640, pcm.length - cursor));
                            if (recognizer.acceptWaveForm(frame, frame.length)) {
                                String raw = recognizer.getResult(); texts.put(new JSONObject(raw).optString("text"));
                                detected |= policy.accept(epoch, new JSONObject(raw).optString("text"), SilenceListener.commandConfidence(raw), SystemClock.elapsedRealtime());
                            }
                        }
                    }
                    rows.put(new JSONObject().put("fixture", fixture).put("public_results", texts).put("detected", detected));
                    check(detected == (fixture.equals("silence_command") || fixture.equals("silence_bare")), "Wrong interruption decision: " + fixture + ": " + texts);
                }
            }
            AtomicInteger stops = new AtomicInteger();
            List<String> acknowledgments = new ArrayList<>();
            simulated = new VoiceService() {
                boolean stopSpeechOutput() { stops.incrementAndGet(); return true; }
                void controlTurn(String action, String id, long at) { acknowledgments.add(action); }
                void state(String text, FaceState presentation, String words) { /* Detached UI, no real service. */ }
            };
            String id = UUID.randomUUID().toString().replace("-", ""); long now = SystemClock.elapsedRealtime();
            simulated.running = simulated.speaking = simulated.conversationOnly = true;
            simulated.activeSpeechId = id; simulated.activeSpeechText = "Respuesta pública que no se terminó de oír.";
            check(simulated.conversationTurn.begin(id, 1, now - 100, now + 29900, now), "Detached turn failed");
            check(simulated.conversationTurn.acceptReply(id, 1, now - 100, now + 29900, now), "Detached reply failed");
            check(simulated.conversationTurn.acceptStart(id, now), "Detached start failed");
            simulated.interruptSpeech("wrong"); check(stops.get() == 0, "Stale command stopped another reply");
            simulated.interruptSpeech(id); check(stops.get() == 1 && simulated.speechInterrupted && simulated.activeSpeechId.isEmpty(), "Current speech was not stopped");
            simulated.interruptSpeech(id); check(stops.get() == 1, "Duplicate interruption stopped again");
            check(!simulated.conversationTurn.hasActiveTurn(), "Interrupted speech stayed active");
            check(acknowledgments.equals(Collections.singletonList("tts_interrupted")) && !simulated.speaking && simulated.running,
                "Interruption was acknowledged as completion/failure or stopped the chat");
            id = UUID.randomUUID().toString().replace("-", ""); now = SystemClock.elapsedRealtime();
            simulated.speaking = true; simulated.speechInterrupted = false; simulated.activeSpeechId = id;
            simulated.activeTimerControl = "result";
            check(simulated.conversationTurn.begin(id, 2, now - 100, now + 29900, now), "Detached action turn failed");
            check(simulated.conversationTurn.acceptReply(id, 2, now - 100, now + 29900, now, "action_result", true), "Action speech rejected");
            check(simulated.conversationTurn.acceptStart(id, now), "Action start failed");
            simulated.interruptSpeech(id);
            check(simulated.conversationTurn.awaitingClose() && simulated.speaking && simulated.activeSpeechId.isEmpty(),
                "Interrupted action result reopened capture before authenticated close");
            AtomicInteger soundStops = new AtomicInteger(), serviceStops = new AtomicInteger();
            RingService ring = new RingService() {
                boolean stopSoundOutput() { soundStops.incrementAndGet(); return true; }
                void finishSoundService() { serviceStops.incrementAndGet(); }
            };
            ring.soundId = "public-output-epoch"; ring.outputActive = true;
            ring.interruptSound("stale"); check(soundStops.get() == 0, "Foreign command stopped another event");
            ring.interruptSound("public-output-epoch");
            ring.interruptSound("public-output-epoch");
            check(soundStops.get() == 1 && serviceStops.get() == 1 && !ring.outputActive, "Sound stop was not single use");
            check(!AudioInputGate.occupied() && !SilenceListener.recording(), "Synthetic recognition opened capture");
            check(before.equals(TimerStore.prefs(context).getAll()), "Interruption touched future timers");
            check(receiptsBefore.equals(TimerStore.actionReceipts(context).getAll()), "Interruption touched action receipts");
            result.putString("public_results", rows.toString()); result.putBoolean("passed", true);
        } catch (Exception | LinkageError failure) {
            result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            if (simulated != null) { simulated.running = false; simulated.controls.shutdownNow(); simulated.timerExecutor.shutdownNow(); simulated.main.removeCallbacksAndMessages(null); }
            if (prepared != null) try {
                prepared.close();
                if (!prepared.awaitClosed(8000) || SilenceModels.activeUsers() != 0) {
                    result.putBoolean("passed", false); result.putString("error", "Offline ASR model did not release");
                }
            } catch (Exception failure) { result.putBoolean("passed", false); result.putString("error", "ASR cleanup failed"); }
            VoiceService.uiState = stateBefore; VoiceService.lastStatus = statusBefore;
        }
        result.putString("test", "offline_android_silence"); result.putBoolean("microphone_opened", false);
        result.putBoolean("audio_played", false); result.putBoolean("llm_started", false);
        result.putBoolean("future_timers_unchanged", before.equals(TimerStore.prefs(context).getAll()));
        result.putBoolean("action_receipts_unchanged", receiptsBefore.equals(TimerStore.actionReceipts(context).getAll()));
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
