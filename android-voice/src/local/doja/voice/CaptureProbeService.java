package local.doja.voice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.vosk.Recognizer;

/** User-started capture control; optional pinned public audio, no LLM/network/human storage. */
public final class CaptureProbeService extends Service {
    static volatile boolean active, recording;
    static volatile long expiresAt;
    static volatile String status = "";
    static volatile CaptureProbeStats.End runEnd = CaptureProbeStats.End.PREPARING;
    static volatile long attempt;
    static volatile boolean playbackMode;
    private static final List<CaptureProbeStats> results = new ArrayList<>(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object micOwner = new Object(), audioLock = new Object(), playbackLock = new Object();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private volatile CaptureProbeStats.End stopReason;
    private volatile boolean running, ownsRun, destroyed;
    private volatile long captureStartedAt = -1, captureStoppedAt = -1;
    private AudioRecord current;
    private CaptureProbePlayback currentPlayback;
    private CaptureProbePolicy policy;
    static synchronized List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (CaptureProbeStats result : results) copy.add(result.snapshot());
        return copy;
    }
    private static synchronized void clearResults() { results.clear(); }
    private static synchronized void addResult(CaptureProbeStats result) {
        if (results.size() >= 2) throw new IllegalStateException("Probe phase bound"); results.add(result);
    }
    public IBinder onBind(Intent intent) { return null; }
    private boolean alone() { return !VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !SilenceListener.recording(); }
    private boolean safe(boolean preparing) {
        if (!MainActivity.visibleToUser || !alone() || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo(); getSystemService(ActivityManager.class).getMemoryInfo(memory);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int mode = getSystemService(AudioManager.class).getMode();
        AudioManager audio = getSystemService(AudioManager.class);
        if (playbackMode && (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC))) return false;
        return memory.availMem / 1024 >= (preparing ? 1200000 : 500000) && temperature >= 0 && temperature < (preparing ? 380 : 400)
            && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !"START".equals(intent.getAction())) {
            if (ownsRun) requestStop(CaptureProbeStats.End.USER_STOP); else stopSelf();
            return START_NOT_STICKY;
        }
        if (ownsRun) return START_NOT_STICKY; // Repeated START cannot restart or kill this run.
        if (active) { stopSelf(); return START_NOT_STICKY; }
        attempt++; clearResults(); runEnd = CaptureProbeStats.End.PREPARING;
        playbackMode = intent.getBooleanExtra("with_playback", false);
        if (!safe(true) || !AudioInputGate.acquire(micOwner)) {
            runEnd = CaptureProbeStats.End.GUARD; status = "Diagnóstico no iniciado · permisos, recursos o captura ocupada · micrófono apagado";
            sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName())); stopSelf(); return START_NOT_STICKY;
        }
        ownsRun = running = active = true; recording = false;
        policy = new CaptureProbePolicy(SystemClock.elapsedRealtime()); expiresAt = policy.deadline();
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("capture_probe", "Diagnóstico de micrófono", NotificationManager.IMPORTANCE_LOW));
            startForeground(12, notification("Preparando ASR local · micrófono aún apagado"));
            state("Preparando ASR local · micrófono aún apagado");
            main.postDelayed(() -> requestStop(CaptureProbeStats.End.DEADLINE), CaptureProbePolicy.TOTAL_MS);
            new Thread(this::runProbe, "capture-probe").start();
        } catch (RuntimeException failure) {
            main.removeCallbacksAndMessages(null); policy.close(); AudioInputGate.release(micOwner);
            ownsRun = running = active = recording = false; expiresAt = 0;
            runEnd = CaptureProbeStats.End.ERROR;
            status = "No se pudo iniciar el diagnóstico · micrófono apagado"; stopSelf();
        }
        return START_NOT_STICKY;
    }
    private Notification notification(String text) {
        PendingIntent stop = PendingIntent.getService(this, 14, new Intent(this, CaptureProbeService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "capture_probe").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(text).setContentText(playbackMode ? "Máximo 20 s · voz fija autorizada · sin LFM ni temporizadores"
                : "Máximo 20 s · sin voz de salida, LFM ni temporizadores")
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER DIAGNÓSTICO", stop).build();
    }
    private void state(String text) {
        status = text;
        if (ownsRun && active && !destroyed) getSystemService(NotificationManager.class).notify(12, notification(text));
        sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
    }
    private AudioRecord open(int source) {
        int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        synchronized (audioLock) {
            if (!running || SystemClock.elapsedRealtime() > expiresAt - CaptureProbePolicy.PHASE_MS - CaptureProbePolicy.FINAL_RESERVE_MS) return null;
            if (current != null) throw new IllegalStateException("Two probe recorders");
            AudioRecord value = new AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(6400, minimum * 2));
            if (!running || SystemClock.elapsedRealtime() > expiresAt - CaptureProbePolicy.PHASE_MS - CaptureProbePolicy.FINAL_RESERVE_MS) { value.release(); return null; }
            if (value.getState() != AudioRecord.STATE_INITIALIZED) { value.release(); throw new IllegalStateException("Probe capture unavailable"); }
            current = value;
            try {
                value.startRecording();
                if (value.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Probe capture did not start");
                captureStartedAt = SystemClock.elapsedRealtime(); captureStoppedAt = -1;
                recording = true; return value;
            } catch (RuntimeException failure) { current = null; value.release(); throw failure; }
        }
    }
    private void closeCapture() {
        boolean closedRecorder = false;
        synchronized (audioLock) {
            if (current != null) {
                try { current.stop(); } catch (RuntimeException ignored) {}
                captureStoppedAt = SystemClock.elapsedRealtime();
                try { current.release(); } catch (RuntimeException ignored) {}
                current = null;
                closedRecorder = true;
            }
            if (ownsRun) recording = false;
        }
        if (closedRecorder && ownsRun && active) {
            if (running && !stopping.get()) state("Micrófono apagado · analizando fase");
            else sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
        }
    }
    private void requestStop(CaptureProbeStats.End reason) {
        if (!ownsRun || !stopping.compareAndSet(false, true)) return;
        stopReason = reason; running = false; if (policy != null) policy.close();
        state("Deteniendo diagnóstico y captura…");
        new Thread(() -> {
            closePlayback(playbackReason()); closeCapture(); sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
        }, "capture-probe-stop").start();
    }
    private CaptureProbeStats.PlaybackEnd playbackReason() {
        if (stopReason == null) return CaptureProbeStats.PlaybackEnd.PHASE_END;
        if (stopReason == CaptureProbeStats.End.DEADLINE) return CaptureProbeStats.PlaybackEnd.DEADLINE;
        if (stopReason == CaptureProbeStats.End.USER_STOP) return CaptureProbeStats.PlaybackEnd.USER_STOP;
        if (stopReason == CaptureProbeStats.End.GUARD) return CaptureProbeStats.PlaybackEnd.GUARD;
        return CaptureProbeStats.PlaybackEnd.ERROR;
    }
    private void closePlayback(CaptureProbeStats.PlaybackEnd reason) {
        synchronized (playbackLock) { if (currentPlayback != null) { currentPlayback.close(reason); currentPlayback = null; } }
    }
    private void runProbe() {
        try (SilenceModels.Lease model = SilenceModels.acquire(this)) {
            byte[] publicPcm = null;
            if (playbackMode) try (InputStream input = getAssets().open("capture-probe-public.pcm")) {
                publicPcm = CaptureProbeAudio.read(input, AssistantConfig.PROBE_AUDIO_BYTES, AssistantConfig.PROBE_AUDIO_SHA256);
            }
            for (int source : new int[]{6, 7}) {
                if (!running || !policy.begin(source, SystemClock.elapsedRealtime(), safe(false), alone())) {
                    if (running) stopReason = SystemClock.elapsedRealtime() >= expiresAt - 6500 ? CaptureProbeStats.End.DEADLINE : CaptureProbeStats.End.GUARD;
                    break;
                }
                CaptureProbeStats stats = new CaptureProbeStats(source, playbackMode); addResult(stats);
                try { phase(model, source, stats, publicPcm); }
                finally { closePlayback(playbackReason()); closeCapture(); policy.released(stats.completed()); }
                if (!running || stopReason != null) break;
                if (source == 6) {
                    state("Cambiando entrada · micrófono apagado · espera FASE 2");
                    SystemClock.sleep(500);
                }
            }
        } catch (Exception | LinkageError failure) {
            if (stopReason == null) stopReason = CaptureProbeStats.End.ERROR; // No exception text/ASR/audio logs.
        } finally {
            running = false; closePlayback(playbackReason()); closeCapture();
            boolean complete = policy != null && policy.complete() && stopReason == null;
            runEnd = complete ? CaptureProbeStats.End.COMPLETE : stopReason == null ? CaptureProbeStats.End.ERROR : stopReason;
            if (policy != null) policy.close(); AudioInputGate.release(micOwner);
            active = recording = false; expiresAt = 0; main.removeCallbacksAndMessages(null);
            status = complete ? "Diagnóstico terminado · micrófono y sonido apagados · sin acciones"
                : "Diagnóstico incompleto · micrófono apagado · " + (stopReason == null ? "ERROR" : stopReason.name());
            sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
            main.post(() -> stopSelf());
        }
    }
    private void phase(SilenceModels.Lease model, int source, CaptureProbeStats stats, byte[] publicPcm) throws Exception {
        AcousticEchoCanceler echo = null; long capturedAt = -1;
        try (Recognizer recognizer = new Recognizer(model.value, 16000)) {
            recognizer.setWords(true); recognizer.setPartialWords(true);
            AudioRecord recorder = open(source);
            if (recorder == null) {
                if (stopReason == null) stopReason = CaptureProbeStats.End.DEADLINE;
                stats.finish(stopReason); return;
            }
            boolean enabled = false;
            if (source == 7 && AcousticEchoCanceler.isAvailable()) try {
                echo = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                enabled = echo != null && echo.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS && echo.getEnabled();
            } catch (RuntimeException unsupported) { enabled = false; }
            capturedAt = captureStartedAt;
            stats.started(capturedAt - (expiresAt - CaptureProbePolicy.TOTAL_MS), recorder.getAudioSource(), recorder.getSampleRate(), recorder.getChannelCount(),
                AcousticEchoCanceler.isAvailable(), enabled, getSystemService(AudioManager.class).isMicrophoneMute());
            long phaseEnd = Math.min(expiresAt, capturedAt + CaptureProbePolicy.PHASE_MS), checked = 0, partialAt = 0;
            if (publicPcm != null) synchronized (playbackLock) {
                if (!running || SystemClock.elapsedRealtime() >= phaseEnd) throw new IllegalStateException("Playback window expired");
                currentPlayback = new CaptureProbePlayback(this, publicPcm, source, phaseEnd, stats);
                if (!running || SystemClock.elapsedRealtime() >= phaseEnd || !currentPlayback.start()) throw new IllegalStateException("Playback start expired");
            }
            state("MICRÓFONO ACTIVO · FASE " + (source == 6 ? "1/2" : "2/2") + " · entrada " + source
                + (publicPcm == null ? " · di silencio una vez" : " · voz fija · di silencio una vez"));
            byte[] pcm = new byte[640]; AudioTimestamp timestamp = new AudioTimestamp();
            while (running && SystemClock.elapsedRealtime() < phaseEnd) {
                long now = SystemClock.elapsedRealtime();
                if (now - checked >= 500) {
                    checked = now;
                    if (!safe(false)) { stopReason = CaptureProbeStats.End.GUARD; break; }
                    try {
                        AudioRecordingConfiguration config = recorder.getActiveRecordingConfiguration();
                        stats.configuration(config == null ? -1 : config.getFormat().getSampleRate(), config == null ? -1 : config.isClientSilenced() ? 1 : 0);
                    } catch (RuntimeException ignored) { stats.configuration(-1, -1); }
                    synchronized (playbackLock) { if (currentPlayback != null) currentPlayback.sample(); }
                }
                int count = recorder.read(pcm, 0, pcm.length, AudioRecord.READ_NON_BLOCKING);
                if (count < 0 || count % 2 != 0) throw new IllegalStateException("Probe PCM failed");
                if (count == 0) { SystemClock.sleep(10); continue; }
                stats.pcm(pcm, count);
                try { if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS) stats.lag(timestamp.framePosition); }
                catch (RuntimeException ignored) {}
                long decodeAt = SystemClock.elapsedRealtime();
                boolean endpoint = recognizer.acceptWaveForm(pcm, count);
                stats.decoded(SystemClock.elapsedRealtime() - decodeAt);
                if (endpoint) {
                    boolean command = CaptureProbeRecognition.result(stats, recognizer.getResult(), false);
                    if (command && publicPcm != null && running) synchronized (playbackLock) {
                        if (currentPlayback != null && currentPlayback.command(source, true))
                            state("MICRÓFONO ACTIVO · FASE " + (source == 6 ? "1/2" : "2/2") + " · voz fija detenida · espera la siguiente fase");
                    }
                }
                else if (now - partialAt >= 200) { partialAt = now; CaptureProbeRecognition.partial(stats, recognizer.getPartialResult()); }
            }
            closePlayback(playbackReason()); closeCapture(); stats.captureEnded(captureStoppedAt - capturedAt);
            // Forced tail is diagnosis only, after closing the microphone; no late action.
            if (stopReason == null && running && SystemClock.elapsedRealtime() < expiresAt)
                CaptureProbeRecognition.result(stats, recognizer.getFinalResult(), true);
            stats.finish(stopReason == null ? CaptureProbeStats.End.COMPLETE : stopReason);
        } catch (Exception | LinkageError failure) {
            if (stopReason == null) stopReason = CaptureProbeStats.End.ERROR;
            stats.finish(stopReason); throw failure;
        } finally {
            closePlayback(playbackReason()); closeCapture();
            if (capturedAt >= 0 && captureStoppedAt >= capturedAt) stats.captureEnded(captureStoppedAt - capturedAt);
            if (echo != null) try { echo.release(); } catch (RuntimeException ignored) {}
        }
    }
    public void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null);
        if (ownsRun && running) requestStop(CaptureProbeStats.End.USER_STOP);
        stopForeground(true); super.onDestroy();
    }
}
