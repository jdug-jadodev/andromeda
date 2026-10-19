package local.doja.voice;

import android.Manifest;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.vosk.Recognizer;

/** Output-only offline interruption. No LFM, network, persisted audio or general actions. */
final class SilenceListener implements AutoCloseable {
    private static final AtomicInteger microphones = new AtomicInteger();
    private final Context context;
    private final Runnable changed;
    private final SilenceDiagnostics.Owner owner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SilenceWindowPolicy policy = new SilenceWindowPolicy();
    private final AtomicInteger jobs = new AtomicInteger();
    private final Object micOwner = new Object();
    private SilenceModels.Lease lease;
    private Future<?> preparation;
    private volatile boolean closed, ready;
    volatile String error = "";
    volatile boolean echoCancellation;
    SilenceListener(Context context, Runnable changed) { this(context, changed, SilenceDiagnostics.Owner.SYNTHETIC); }
    SilenceListener(Context context, Runnable changed, SilenceDiagnostics.Owner owner) {
        this.context = context.getApplicationContext(); this.changed = changed; this.owner = owner;
    }
    static boolean recording() { return microphones.get() > 0; }
    boolean busy() { return jobs.get() > 0; }
    boolean ready() { return ready && !closed; }
    private void notifyState() {
        main.post(() -> {
            changed.run();
            context.sendBroadcast(new Intent(VoiceService.STATE).setPackage(context.getPackageName()));
        });
    }
    private boolean safe(long availableKiB, int maxTemperature) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int mode = context.getSystemService(AudioManager.class).getMode();
        return memory.availMem / 1024 >= availableKiB && temperature >= 0 && temperature < maxTemperature
            && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }
    synchronized void prepare() {
        if (closed || preparation != null) return;
        preparation = worker.submit(() -> {
            try {
                if (!safe(1200000, 380)) throw new IllegalStateException("Guardia de recursos/permisos");
                lease = SilenceModels.acquire(context); ready = !closed;
            } catch (Exception | LinkageError failure) { error = "Interrupción local no disponible"; }
            notifyState();
        });
    }
    boolean awaitReady(long deadline) {
        prepare();
        try { preparation.get(Math.max(1, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS); }
        catch (Exception failure) { return false; }
        return ready();
    }
    void begin(String id, long deadline, BooleanSupplier stillPlaying, Runnable command) {
        if (closed || !policy.begin(id, deadline, SystemClock.elapsedRealtime())) return;
        SilenceDiagnostics.Window diagnostic = SilenceDiagnostics.begin(owner, SystemClock.elapsedRealtime());
        prepare(); jobs.incrementAndGet();
        try { worker.execute(() -> listen(id, deadline, stillPlaying, command, diagnostic)); }
        catch (RejectedExecutionException stopping) {
            jobs.decrementAndGet(); policy.end(); diagnostic.finish(SilenceDiagnostics.End.CLOSED, SystemClock.elapsedRealtime());
        }
    }
    static double commandConfidence(String raw) throws JSONException {
        JSONObject result = new JSONObject(raw); JSONArray words = result.optJSONArray("result");
        String text = result.optString("text");
        if (!SilenceWindowPolicy.isCommand(text) || words == null || (words.length() != 1 && words.length() != 2)) return -1;
        StringBuilder recognized = new StringBuilder(); double confidence = 1;
        for (int i = 0; i < words.length(); i++) {
            if (i > 0) recognized.append(' ');
            recognized.append(words.getJSONObject(i).getString("word"));
            double value = words.getJSONObject(i).getDouble("conf");
            if (!Double.isFinite(value) || value < 0 || value > 1) return -1;
            confidence = Math.min(confidence, value);
        }
        if (!SilenceWindowPolicy.isCommand(recognized.toString())
                || text.trim().split("\\s+").length != words.length()
                || recognized.toString().trim().split("\\s+").length != words.length()) return -1;
        double start = words.getJSONObject(0).getDouble("start"), end = words.getJSONObject(words.length() - 1).getDouble("end");
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start || end - start > 4) return -1;
        return confidence;
    }
    private void listen(String id, long deadline, BooleanSupplier stillPlaying, Runnable command, SilenceDiagnostics.Window diagnostic) {
        AudioRecord recorder = null; AcousticEchoCanceler echo = null; boolean started = false, acquired = false;
        SilenceDiagnostics.End reason = SilenceDiagnostics.End.OUTPUT_ENDED;
        try {
            if (!ready()) { reason = SilenceDiagnostics.End.NOT_READY; return; }
            if (!safe(500000, 400)) { reason = SilenceDiagnostics.End.GUARD; return; }
            while (!closed && policy.active(id, SystemClock.elapsedRealtime()) && stillPlaying.getAsBoolean()) {
                if (AudioInputGate.acquire(micOwner)) { acquired = true; break; }
                SystemClock.sleep(10);
            }
            if (!acquired || closed || !policy.active(id, SystemClock.elapsedRealtime()) || !stillPlaying.getAsBoolean()) return;
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            // The Moto config applies its AEC/NS preprocessing to this source.
            // Do not change global AudioManager mode, speaker route or volume.
            int source = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            recorder = new AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord no disponible");
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echo = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                    echoCancellation = echo != null && echo.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS && echo.getEnabled();
                } catch (RuntimeException unsupported) { echoCancellation = false; }
            }
            try (Recognizer recognizer = new Recognizer(lease.value, 16000)) {
                recognizer.setWords(true); recorder.startRecording(); started = true;
                diagnostic.capture(SystemClock.elapsedRealtime(), source, AcousticEchoCanceler.isAvailable(), echoCancellation,
                    context.getSystemService(AudioManager.class).isMicrophoneMute());
                microphones.incrementAndGet(); notifyState(); byte[] pcm = new byte[640]; long checked = 0;
                while (!closed && policy.active(id, SystemClock.elapsedRealtime()) && stillPlaying.getAsBoolean()) {
                    long now = SystemClock.elapsedRealtime();
                    if (now >= deadline) { reason = SilenceDiagnostics.End.EXPIRED; break; }
                    if (now - checked >= 1000) {
                        checked = now;
                        if (!safe(500000, 400)) { reason = SilenceDiagnostics.End.GUARD; break; }
                        try {
                            AudioRecordingConfiguration configuration = recorder.getActiveRecordingConfiguration();
                            diagnostic.silenced(configuration == null ? -1 : configuration.isClientSilenced() ? 1 : 0);
                        } catch (RuntimeException unavailable) { diagnostic.silenced(-1); } // Diagnosis must not disable the command.
                    }
                    int count = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_NON_BLOCKING);
                    if (count < 0 || count % 2 != 0) throw new IllegalStateException("Captura de interrupción fallida");
                    if (count == 0) { SystemClock.sleep(10); continue; }
                    diagnostic.frame(pcm, count);
                    if (recognizer.acceptWaveForm(pcm, count)) {
                        String result = recognizer.getResult();
                        double confidence = commandConfidence(result);
                        JSONObject parsed = new JSONObject(result);
                        boolean accepted = policy.accept(id, parsed.optString("text"), confidence, SystemClock.elapsedRealtime());
                        diagnostic.endpoint(parsed.optJSONArray("result") == null ? 0 : parsed.getJSONArray("result").length(), accepted);
                        if (accepted) {
                            reason = SilenceDiagnostics.End.DETECTED;
                            main.post(() -> {
                                if (!closed && SystemClock.elapsedRealtime() < deadline && stillPlaying.getAsBoolean()) {
                                    diagnostic.callback(); command.run();
                                }
                            });
                            break;
                        }
                    }
                }
            }
        } catch (Exception | LinkageError failure) { reason = SilenceDiagnostics.End.ERROR; error = "Escucha de silencio detenida"; }
        finally {
            if (recorder != null) {
                try { if (started) recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
            }
            if (echo != null) try { echo.release(); } catch (Exception ignored) {}
            if (started) microphones.decrementAndGet();
            if (acquired) AudioInputGate.release(micOwner);
            if (reason == SilenceDiagnostics.End.OUTPUT_ENDED) {
                if (closed) reason = SilenceDiagnostics.End.CLOSED;
                else if (SystemClock.elapsedRealtime() >= deadline) reason = SilenceDiagnostics.End.EXPIRED;
            }
            diagnostic.finish(reason, SystemClock.elapsedRealtime());
            jobs.decrementAndGet(); notifyState();
        }
    }
    void end() { policy.end(); }
    public synchronized void close() {
        if (closed) return;
        closed = true; ready = false; policy.close();
        worker.execute(() -> { if (lease != null) { lease.close(); lease = null; } });
        worker.shutdown();
    }
    boolean awaitClosed(long milliseconds) throws InterruptedException { return closed && worker.awaitTermination(milliseconds, TimeUnit.MILLISECONDS); }
}
