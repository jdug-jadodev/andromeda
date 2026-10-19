package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.ListView;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import org.json.*;
import org.vosk.Recognizer;

/** Public WAVs and manual UI gate only. Never starts the service/AudioRecord/TTS/LLM. */
public final class CaptureProbeInstrumentation extends Instrumentation {
    private MainActivity activity;
    static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Context context = getTargetContext(); Bundle result = new Bundle(); JSONArray rows = new JSONArray();
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        Map<String, ?> receipts = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing && !CaptureProbeService.active && !SilenceListener.recording()
                && !AudioInputGate.occupied(), "A real capture/event is active");
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class).putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field dialogField = MainActivity.class.getDeclaredField("optionsDialog"); dialogField.setAccessible(true);
            runOnMainSync(() -> {
                try {
                    activity.voiceDiagnostics();
                    AlertDialog menu = (AlertDialog)dialogField.get(activity); ListView list = menu.getListView();
                    check(list.getAdapter().getCount() == 7, "Diagnostic menu missing");
                    check(list.getAdapter().getItem(3).toString().contains("20 s"), "Diagnostic ceiling hidden");
                    list.performItemClick(list.getChildAt(3), 3, list.getAdapter().getItemId(3));
                    AlertDialog confirm = (AlertDialog)dialogField.get(activity);
                    check(confirm != null && "Iniciar diagnóstico".contentEquals(confirm.getButton(AlertDialog.BUTTON_POSITIVE).getText()), "Manual start confirmation missing");
                    check(!CaptureProbeService.active && !CaptureProbeService.recording && !AudioInputGate.occupied(), "Choosing diagnostic opened capture");
                    activity.closeOptions();
                    activity.voiceDiagnostics();
                    menu = (AlertDialog)dialogField.get(activity); list = menu.getListView();
                    check(list.getAdapter().getItem(4).toString().contains("voz fija"), "Explicit playback option missing");
                    list.performItemClick(list.getChildAt(4), 4, list.getAdapter().getItemId(4));
                    confirm = (AlertDialog)dialogField.get(activity);
                    check("Iniciar prueba con voz fija".contentEquals(confirm.getButton(AlertDialog.BUTTON_POSITIVE).getText()), "Separate playback consent missing");
                    check(!CaptureProbeService.active && !CaptureProbeService.recording && !AudioInputGate.occupied(), "Playback menu started capture/output");
                    activity.closeOptions();
                    StringWriter output = new StringWriter();
                    activity.dump("", null, new PrintWriter(output), new String[]{"capture_probe_metrics"});
                    String text = output.toString();
                    check(text.startsWith("capture_probe_metrics=") && !text.contains("transcript") && !text.contains("ViewRoot"), "Diagnostic dumped views/text");
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            CaptureProbeStats partial = new CaptureProbeStats(6);
            CaptureProbeRecognition.partial(partial, "{\"partial\":\"silencio\"}");
            check(partial.snapshot().get("max_partial_words").equals(1L) && partial.snapshot().get("final_command_matches").equals(0L), "Partial became a final command");
            check(!CaptureProbeRecognition.result(partial, "{\"text\":\"silencio\",\"result\":[{\"word\":\"silencio\",\"conf\":1,\"start\":0,\"end\":0.6}]}", true), "Forced pending result can stop playback");
            byte[] publicClip;
            try (InputStream input = context.getAssets().open("capture-probe-public.pcm")) {
                publicClip = CaptureProbeAudio.read(input, AssistantConfig.PROBE_AUDIO_BYTES, AssistantConfig.PROBE_AUDIO_SHA256);
            }
            check(publicClip.length == 89350, "Pinned public asset changed");
            try (SilenceModels.Lease model = SilenceModels.acquire(context)) {
                for (String name : new String[]{"silence_bare", "silence_command", "silence_negated", "silence_reported"}) {
                    CaptureProbeStats stats = new CaptureProbeStats(6); // Requested source label only, no physical capture.
                    byte[] pcm = ConversationInstrumentation.readPcm(new File(context.getFilesDir(), "synthetic/andromeda_fixture_" + name + ".wav"));
                    try (Recognizer recognizer = new Recognizer(model.value, 16000)) {
                        recognizer.setWords(true); recognizer.setPartialWords(true);
                        for (int cursor = 0; cursor < pcm.length + 64000; cursor += 640) {
                            byte[] frame = new byte[640];
                            if (cursor < pcm.length) System.arraycopy(pcm, cursor, frame, 0, Math.min(640, pcm.length - cursor));
                            stats.pcm(frame, frame.length);
                            if (recognizer.acceptWaveForm(frame, frame.length)) CaptureProbeRecognition.result(stats, recognizer.getResult(), false);
                            else if (cursor % 6400 == 0) CaptureProbeRecognition.partial(stats, recognizer.getPartialResult());
                        }
                        CaptureProbeRecognition.result(stats, recognizer.getFinalResult(), true);
                    }
                    Map<String, Object> values = stats.snapshot();
                    long matches = (Long)values.get("final_command_matches") + (Long)values.get("pending_command_matches");
                    check((matches > 0) == (name.equals("silence_bare") || name.equals("silence_command")), "Wrong public ASR decision: " + name);
                    check(values.get("source_actual").equals(-1), "Synthetic evidence claimed physical capture");
                    rows.put(new JSONObject(values).put("public_fixture", name).put("synthetic", true));
                }
                CaptureProbeStats echoOnly = new CaptureProbeStats(6, true);
                try (Recognizer recognizer = new Recognizer(model.value, 16000)) {
                    recognizer.setWords(true);
                    for (int cursor = 0; cursor < publicClip.length + 64000; cursor += 640) {
                        byte[] frame = new byte[640];
                        if (cursor < publicClip.length) System.arraycopy(publicClip, cursor, frame, 0, Math.min(640, publicClip.length - cursor));
                        if (recognizer.acceptWaveForm(frame, frame.length)) CaptureProbeRecognition.result(echoOnly, recognizer.getResult(), false);
                    }
                    CaptureProbeRecognition.result(echoOnly, recognizer.getFinalResult(), true);
                }
                check(echoOnly.snapshot().get("final_command_matches").equals(0L) && echoOnly.snapshot().get("pending_command_matches").equals(0L), "Public playback phrase itself matched the command");
            }
            check(SilenceModels.activeUsers() == 0, "ASR model lease leaked");
            check(!CaptureProbeService.active && !CaptureProbeService.recording && !AudioInputGate.occupied(), "Test opened microphone");
            check(timers.equals(TimerStore.prefs(context).getAll()) && receipts.equals(TimerStore.actionReceipts(context).getAll()), "Test altered timers/receipts");
            result.putBoolean("passed", true);
        } catch (Exception | LinkageError failure) {
            result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            if (activity != null) runOnMainSync(() -> { activity.closeOptions(); activity.finish(); });
        }
        result.putString("test", "quiet_capture_probe_no_microphone"); result.putString("public_fixtures", rows.toString());
        result.putBoolean("microphone_opened", false); result.putBoolean("audio_played", false); result.putBoolean("llm_started", false);
        result.putBoolean("public_pcm_verified", true); result.putBoolean("playback_start_gate", true);
        result.putBoolean("manual_start_gate", true); result.putBoolean("timers_unchanged", timers.equals(TimerStore.prefs(context).getAll()));
        result.putBoolean("receipts_unchanged", receipts.equals(TimerStore.actionReceipts(context).getAll()));
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
