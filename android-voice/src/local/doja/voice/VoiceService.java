package local.doja.voice;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class VoiceService extends Service {
    public static final String STATE = "local.doja.voice.STATE";
    public static volatile boolean active = false;
    public static volatile boolean wakeListening = false;
    public static volatile boolean wakeTestActive = false;
    public static volatile long expiresAt = 0;
    // Diagnostic text stays only in process memory, never in preferences/logs.
    public static volatile String lastTranscript = "";
    public static volatile FaceState uiState = FaceState.IDLE;
    public static volatile String lastStatus = "Micrófono apagado · activa solo una prueba autorizada";
    boolean pilotExpired;
    volatile boolean running, speaking, ttsReady;
    volatile String activeSpeechId = "";
    volatile String activeSpeechText = "";
    int speechGeneration;
    volatile String lastError = "";
    volatile boolean wakeEnabled;
    volatile boolean aiEnabled;
    volatile boolean wakeOnly;
    final ExecutorService controls = Executors.newSingleThreadExecutor();
    volatile long resumeAfter;
    volatile Socket socket;
    volatile DataOutputStream output;
    AudioRecord recorder;
    TextToSpeech tts;
    final Handler main = new Handler(Looper.getMainLooper());
    public IBinder onBind(Intent intent) { return null; }
    public void onCreate() {
        super.onCreate();
    }
    void initializeTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = tts.setLanguage(new Locale("es", "US")) >= 0;
            }
        }, "com.google.android.tts");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {
                if (running && id.equals(activeSpeechId)) state(activeSpeechText, FaceState.SPEAKING, null);
            }
            public void onDone(String id) { if (id.equals(activeSpeechId)) finishSpeech(); }
            public void onError(String id) { if (running && id.equals(activeSpeechId)) { state("Error de síntesis; micrófono en pausa breve", FaceState.ERROR, null); finishSpeech(); } }
        });
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "STOP" : intent.getAction();
        if ("STOP".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if ("PUSH".equals(action)) { if (running) control("activate"); else stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        wakeOnly = intent != null && intent.getBooleanExtra("wake_only", false);
        wakeTestActive = wakeOnly;
        if (!wakeOnly) initializeTts();
        int requestedSeconds = intent == null ? 90 : intent.getIntExtra("seconds", 90);
        final int seconds = requestedSeconds == 300 ? 300 : 90;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("microphone", "Micrófono " + AssistantConfig.NAME + " — piloto", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, VoiceService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "microphone")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("MICRÓFONO ACTIVO · piloto de " + seconds + " s")
            .setContentText(wakeOnly ? "Solo activación y transcripción local; sin órdenes. Toca Detener." : "Audio local. Toca Detener para silenciar.").setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop).build();
        startForeground(10, notification);
        running = active = true;
        expiresAt = SystemClock.elapsedRealtime() + seconds * 1000L;
        state("Preparando canal local · micrófono aún apagado", FaceState.PREPARING, null);
        main.postDelayed(() -> { pilotExpired = true; stopSelf(); }, seconds * 1000L);
        new Thread(this::capture, "doja-pcm").start();
        return START_NOT_STICKY;
    }
    synchronized void frame(int kind, byte[] data, int length) throws IOException {
        if (output == null) throw new IOException("Sin conexión local");
        output.writeByte(kind); output.writeInt(length); output.write(data, 0, length); output.flush();
    }
    void control(String action) {
        if (!running || controls.isShutdown() || output == null) return;
        // Socket writes must never execute on Android's UI/TTS callback thread.
        try { controls.execute(() -> {
            try {
                byte[] data = new JSONObject().put("action", action).toString().getBytes(StandardCharsets.UTF_8);
                frame(3, data, data.length);
            } catch (Exception error) {
                if (running) { lastError = "Canal local: " + error.getClass().getSimpleName(); main.post(() -> stopSelf()); }
            }
        }); } catch (RejectedExecutionException ignored) { /* Service is stopping. */ }
    }
    void toolResult(String id, boolean ok, String message) {
        if (!running || controls.isShutdown() || output == null) return;
        try { controls.execute(() -> {
            try {
                byte[] data = new JSONObject().put("action", "tool_result").put("call_id", id)
                    .put("ok", ok).put("message", message).toString().getBytes(StandardCharsets.UTF_8);
                frame(3, data, data.length);
            } catch (Exception error) {
                if (running) { lastError = "No se pudo devolver el resultado de la herramienta"; main.post(() -> stopSelf()); }
            }
        }); } catch (RejectedExecutionException ignored) {}
    }
    void capture() {
        try {
            Socket connected = null;
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (running && SystemClock.elapsedRealtime() < deadline) {
                Socket attempt = new Socket();
                try {
                    attempt.connect(new InetSocketAddress("127.0.0.1", 11436), 1500);
                    connected = attempt; socket = connected; break;
                } catch (IOException error) { try { attempt.close(); } catch (IOException ignored) {} SystemClock.sleep(250); }
            }
            if (connected == null || !running) throw new IOException("No se pudo preparar el canal local");
            connected.setSoTimeout(5000); connected.setTcpNoDelay(true);
            output = new DataOutputStream(connected.getOutputStream());
            String token = new String(Files.readAllBytes(new File(getFilesDir(), "voice-token").toPath()), StandardCharsets.UTF_8).trim();
            byte[] hello = new JSONObject().put("token", token).toString().getBytes(StandardCharsets.UTF_8);
            frame(1, hello, hello.length);
            DataInputStream input = new DataInputStream(connected.getInputStream());
            JSONObject ready = readEvent(input);
            if (!"ready".equals(ready.optString("type"))) throw new IOException("Credencial o servicio no disponible");
            if (wakeOnly && !VoiceSessionPolicy.isWakeOnlyReady(ready.optBoolean("wake_only"),
                    ready.optBoolean("wake_enabled"), ready.optBoolean("ai_enabled")))
                throw new IOException("El backend no es la prueba aislada de activación");
            if (ready.optBoolean("wake_only") && !wakeOnly)
                throw new IOException("Selecciona la prueba de activación, no el piloto de órdenes");
            wakeEnabled = ready.optBoolean("wake_enabled", false);
            aiEnabled = ready.optBoolean("ai_enabled", false);
            int backendSeconds = ready.optInt("seconds", 90);
            if (backendSeconds < 10 || backendSeconds > 300) throw new IOException("Límite de sesión inválido");
            // The shorter limit wins, including time already spent connecting.
            expiresAt = Math.min(expiresAt, SystemClock.elapsedRealtime() + backendSeconds * 1000L);
            main.postDelayed(() -> { pilotExpired = true; stopSelf(); },
                Math.max(0, expiresAt - SystemClock.elapsedRealtime()));
            connected.setSoTimeout(0);
            state(wakeEnabled ? "MICRÓFONO ACTIVO · " + (wakeOnly ? "solo activación · " : aiEnabled ? "IA local · " : "reglas · ") + "di " + AssistantConfig.NAME : "MICRÓFONO ACTIVO · usa Hablar ahora (activación pendiente)", FaceState.READY, null);
            new Thread(() -> responses(input), "doja-events").start();
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("No se pudo iniciar PCM");
            byte[] data = new byte[640]; boolean recording = false, paused = false;
            long lastHeartbeat = 0;
            while (running) {
                if (speaking || RingService.ringing || SystemClock.elapsedRealtime() < resumeAfter) {
                    if (recording) { recorder.stop(); recording = false; wakeListening = false; }
                    if (!paused) { control("pause"); paused = true; }
                    if (SystemClock.elapsedRealtime() - lastHeartbeat >= 1000) {
                        control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime();
                    }
                    SystemClock.sleep(40); continue;
                }
                if (paused) { control("resume"); paused = false; }
                if (!recording) { recorder.startRecording(); recording = true; wakeListening = wakeEnabled; }
                int count = recorder.read(data, 0, data.length);
                if (count < 0) { if (running) throw new IOException("Error de captura PCM"); else break; }
                if (count > 0 && !speaking && !RingService.ringing) frame(2, data, count);
            }
        } catch (Exception error) {
            if (running) { lastError = error.getClass().getSimpleName() + ": " + error.getMessage(); state("Micrófono detenido: " + lastError, FaceState.ERROR, null); }
        } finally {
            running = active = wakeListening = false;
            if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            main.post(() -> stopSelf());
        }
    }
    JSONObject readEvent(DataInputStream input) throws Exception {
        int length = input.readInt();
        if (length < 1 || length > 16384) throw new IOException("Evento inválido");
        byte[] data = new byte[length]; input.readFully(data);
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }
    void responses(DataInputStream input) {
        try {
            while (running) {
                JSONObject event = readEvent(input);
                String type = event.optString("type");
                if (wakeOnly && !VoiceSessionPolicy.wakeOnlyEventAllowed(type))
                    throw new IOException("Evento no permitido en prueba de activación");
                if ("thinking".equals(type)) {
                    speaking = true;
                    state("IA local pensando · micrófono en pausa", FaceState.THINKING, null);
                } else if ("state".equals(type)) {
                    boolean command = "command".equals(event.optString("state"));
                    String listening = "voice".equals(event.optString("trigger")) ? AssistantConfig.NAME + " detectada · escuchando" : "Escuchando (activación manual)";
                    if (wakeOnly) listening += " · prueba sin ejecutar órdenes";
                    state(command ? listening : (wakeEnabled ? "MICRÓFONO ACTIVO · " + (wakeOnly ? "solo activación · " : aiEnabled ? "IA local · " : "reglas · ") + "esperando " + AssistantConfig.NAME : "MICRÓFONO ACTIVO · usa Hablar ahora"), command ? FaceState.LISTENING : FaceState.READY, null);
                } else if ("calibration".equals(type)) state(event.optString("text"), FaceState.LISTENING, "No se crean temporizadores en calibración.");
                else if ("transcript".equals(type)) state(wakeOnly ? "Escuchando · transcripción de prueba, sin acciones" : "Procesando orden local", wakeOnly ? FaceState.LISTENING : FaceState.THINKING, "Escuché: " + event.optString("text"));
                else {
                    speaking = true; // Pause capture immediately, before main-thread TTS scheduling.
                    main.post(() -> apply(event));
                }
            }
        } catch (Exception error) {
            if (running) { lastError = "Canal local cerrado: " + error.getClass().getSimpleName(); main.post(() -> stopSelf()); }
        }
    }
    void apply(JSONObject event) {
        if (!running || wakeOnly) return; // Also reject already queued work after stopping.
        String message;
        boolean ok = true;
        try {
            switch (event.optString("type")) {
                case "timer":
                    message = TimerStore.create(this, event.getString("id"), event.getInt("seconds"));
                    ok = TimerStore.prefs(this).contains(event.getString("id")); break;
                case "cancel_timer":
                    message = TimerStore.cancel(this, event.optBoolean("all"));
                    ok = TimerStore.prefs(this).getAll().isEmpty(); break;
                case "list_timers": message = TimerStore.describe(this); break;
                case "stop_alarm": stopService(new Intent(this, RingService.class)); message = "Sonido detenido."; break;
                case "say": message = event.getString("text"); break;
                default: ok = false; message = "Orden no compatible con esta prueba.";
            }
        } catch (Exception error) { ok = false; message = "No se pudo completar la acción. No la daré por hecha."; }
        if (event.has("tool_call_id")) {
            state(message + " · devolviendo resultado a la IA", FaceState.THINKING, null);
            toolResult(event.optString("tool_call_id"), ok, message);
            return;
        }
        if (!ttsReady) { state(message + " (voz no disponible)", FaceState.ERROR, null); finishSpeech(); return; }
        control("tts_started"); state("Preparando voz · " + message, FaceState.PREPARING, null);
        Bundle params = new Bundle(); params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        String id = UUID.randomUUID().toString();
        activeSpeechId = id; activeSpeechText = message; final int generation = ++speechGeneration;
        if (tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
            state(message + " (no se pudo iniciar la voz)", FaceState.ERROR, null); finishSpeech();
        }
        main.postDelayed(() -> { if (speaking && speechGeneration == generation) {
            tts.stop(); state("Voz detenida por límite de reproducción", FaceState.ERROR, null); finishSpeech();
        } }, 12000);
    }
    void finishSpeech() {
        resumeAfter = SystemClock.elapsedRealtime() + 600; speaking = false; activeSpeechId = "";
        if (running) {
            control("tts_done");
            if (uiState != FaceState.ERROR) state("MICRÓFONO ACTIVO · " + (wakeEnabled ? "esperando " + AssistantConfig.NAME : "usa Hablar ahora"), FaceState.READY, null);
        }
    }
    void state(String text, FaceState presentation, String words) {
        if (words != null) lastTranscript = words;
        uiState = presentation; lastStatus = text;
        Intent update = new Intent(STATE).setPackage(getPackageName()).putExtra("state", text).putExtra("ui_state", presentation.name());
        if (words != null) update.putExtra("transcript", words);
        sendBroadcast(update);
    }
    public void onDestroy() {
        running = active = wakeListening = wakeTestActive = false; main.removeCallbacksAndMessages(null);
        expiresAt = 0;
        controls.shutdownNow();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (recorder != null) try { recorder.stop(); } catch (Exception ignored) {}
        if (tts != null) { tts.stop(); tts.shutdown(); }
        state(lastError.isEmpty() ? (pilotExpired ? "Micrófono apagado · terminó el piloto" : "Micrófono apagado") : "Micrófono apagado · " + lastError, lastError.isEmpty() ? FaceState.MUTED : FaceState.ERROR, null); stopForeground(true); super.onDestroy();
    }
}
