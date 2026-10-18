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
    public static volatile boolean conversationTestActive = false;
    public static volatile boolean microphoneRecording = false;
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
    volatile boolean conversationOnly;
    final ConversationTurnPolicy conversationTurn = new ConversationTurnPolicy();
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
            if (!running) { if (tts != null) tts.shutdown(); return; }
            if (status == TextToSpeech.SUCCESS) {
                if (conversationOnly) {
                    Set<Voice> available = tts.getVoices();
                    List<Voice> voices = new ArrayList<>(available == null ? Collections.emptySet() : available); voices.sort(Comparator.comparing(Voice::getName));
                    Voice selected = null;
                    for (Voice voice : voices) if (!voice.isNetworkConnectionRequired() && "es".equals(voice.getLocale().getLanguage())) {
                        if (selected == null) selected = voice;
                        if ("US".equals(voice.getLocale().getCountry())) { selected = voice; break; }
                    }
                    ttsReady = selected != null && tts.setVoice(selected) != TextToSpeech.ERROR;
                    if (!ttsReady) lastError = "No hay voz española offline disponible";
                } else ttsReady = tts.setLanguage(new Locale("es", "US")) >= 0;
            } else lastError = "No se pudo inicializar la voz offline";
        }, "com.google.android.tts");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) {
                if (running && id.equals(activeSpeechId)) {
                    if (conversationOnly) {
                        long now = SystemClock.elapsedRealtime();
                        if (!conversationTurn.acceptStart(id, now)) {
                            main.post(() -> { if (tts != null) tts.stop(); lastError = "Respuesta tardía descartada"; stopSelf(); }); return;
                        }
                        controlTurn("tts_started", id, now);
                    }
                    state(activeSpeechText + (conversationOnly ? " · sin acciones" : ""), FaceState.SPEAKING, null);
                }
            }
            public void onDone(String id) { if (id.equals(activeSpeechId)) finishSpeech(true); }
            public void onError(String id) { if (running && id.equals(activeSpeechId)) { state("Error de síntesis; micrófono en pausa breve", FaceState.ERROR, null); finishSpeech(false); } }
        });
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "STOP" : intent.getAction();
        if ("STOP".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if ("PUSH".equals(action)) { if (running) control("activate"); else stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        wakeOnly = intent != null && intent.getBooleanExtra("wake_only", false);
        conversationOnly = intent != null && intent.getBooleanExtra("conversation_only", false);
        if (wakeOnly && conversationOnly) { lastError = "Modo de sesión inválido"; stopSelf(); return START_NOT_STICKY; }
        wakeTestActive = wakeOnly;
        conversationTestActive = conversationOnly;
        if (!wakeOnly && !conversationOnly) initializeTts();
        int requestedSeconds = intent == null ? 90 : intent.getIntExtra("seconds", 90);
        final int seconds = requestedSeconds == 300 ? 300 : 90;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("microphone", "Micrófono " + AssistantConfig.NAME + " — piloto", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, VoiceService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "microphone")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Preparando sesión · micrófono aún apagado")
            .setContentText(conversationOnly ? "Conversación LFM sin acciones. Toca Detener." : wakeOnly ? "Solo activación y transcripción local; sin órdenes. Toca Detener." : "Audio local. Toca Detener para silenciar.").setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop).build();
        startForeground(10, notification);
        running = active = true;
        lastTranscript = "";
        expiresAt = SystemClock.elapsedRealtime() + seconds * 1000L;
        state("Preparando canal local · micrófono aún apagado", FaceState.PREPARING, null);
        main.postDelayed(() -> { pilotExpired = true; stopSelf(); }, seconds * 1000L);
        new Thread(conversationOnly ? this::captureConversation : this::capture, "doja-pcm").start();
        return START_NOT_STICKY;
    }
    synchronized void frame(int kind, byte[] data, int length) throws IOException {
        if (!running || output == null) throw new IOException("Sin conexión local");
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
    void controlTurn(String action, String id, long at) {
        if (!running || controls.isShutdown() || output == null) return;
        try { controls.execute(() -> {
            if (!running) return;
            try {
                byte[] data = new JSONObject().put("action", action).put("turn_id", id).put("at_ms", at)
                    .toString().getBytes(StandardCharsets.UTF_8);
                frame(3, data, data.length);
            } catch (Exception error) { if (running) main.post(() -> stopSelf()); }
        }); } catch (RejectedExecutionException ignored) {}
    }
    void microphoneNotification(boolean recording) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, VoiceService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        manager.notify(10, new Notification.Builder(this, "microphone").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(recording ? "MICRÓFONO ACTIVO · sesión limitada" : "Micrófono en pausa · sesión limitada")
            .setContentText(conversationOnly ? "LFM local · sin acciones · Detener para silenciar" : "Audio local · Detener para silenciar")
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop).build());
    }
    void captureConversation() {
        try {
            VoiceConnection connection = VoiceConnection.open(this, "microphone");
            socket = connection.socket; output = connection.output;
            if (!running) throw new IOException("Sesión detenida");
            wakeEnabled = aiEnabled = true;
            int seconds = connection.ready.optInt("seconds");
            if (seconds != 90 && seconds != 300) throw new IOException("Duración inválida");
            expiresAt = Math.min(expiresAt, SystemClock.elapsedRealtime() + seconds * 1000L);
            main.postDelayed(() -> { pilotExpired = true; stopSelf(); }, Math.max(0, expiresAt - SystemClock.elapsedRealtime()));
            main.post(this::initializeTts);
            long ttsDeadline = Math.min(expiresAt, SystemClock.elapsedRealtime() + 12000);
            while (running && !ttsReady && lastError.isEmpty() && SystemClock.elapsedRealtime() < ttsDeadline) SystemClock.sleep(25);
            if (!ttsReady || !running) throw new IOException("No se preparó una voz española offline");
            connection.socket.setSoTimeout(0);
            new Thread(() -> responses(connection.input), "doja-events").start();
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("No se pudo iniciar PCM");
            byte[] data = new byte[640], packet = new byte[648];
            java.nio.ByteBuffer stampBytes = java.nio.ByteBuffer.wrap(packet);
            AudioTimestamp timestamp = new AudioTimestamp();
            boolean recording = false, clockReady = false;
            long readFrames = 0, clockDeadline = 0, lastHeartbeat = 0;
            while (running) {
                if (RingService.ringing) { control("pause"); throw new IOException("Conversación detenida por alarma"); }
                if (speaking || SystemClock.elapsedRealtime() < resumeAfter) {
                    if (recording) {
                        recorder.stop(); recording = false; microphoneRecording = wakeListening = false; microphoneNotification(false);
                        if (uiState == FaceState.THINKING) state("LFM pensando · micrófono apagado · sin acciones", FaceState.THINKING, null);
                    }
                    if (SystemClock.elapsedRealtime() - lastHeartbeat >= 1000) { control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime(); }
                    SystemClock.sleep(25); continue;
                }
                if (!recording) {
                    recorder.startRecording(); recording = microphoneRecording = true; wakeListening = false;
                    readFrames = 0; clockReady = false; clockDeadline = SystemClock.elapsedRealtime() + 1000;
                    microphoneNotification(true);
                }
                int count = recorder.read(data, 0, data.length);
                if (count <= 0 || count % 2 != 0) throw new IOException("Error de captura PCM");
                readFrames += count / 2;
                if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) != AudioRecord.SUCCESS) {
                    if (SystemClock.elapsedRealtime() >= clockDeadline) throw new IOException("El reloj del audio no está disponible");
                    continue;
                }
                long end = timestamp.nanoTime / 1000000L + (readFrames - timestamp.framePosition) * 1000L / 16000L;
                long now = SystemClock.elapsedRealtime();
                if (end > now + 100 || now - end > 1000) throw new IOException("El audio llegó atrasado o con otro reloj");
                if (!clockReady) {
                    clockReady = true; wakeListening = true;
                    if (!conversationTurn.hasActiveTurn()) state("MICRÓFONO ACTIVO · LFM sin acciones · di " + AssistantConfig.NAME, FaceState.READY, null);
                }
                if (running && !speaking) {
                    stampBytes.putLong(0, end); System.arraycopy(data, 0, packet, 8, count); frame(4, packet, count + 8);
                }
            }
        } catch (Exception error) {
            if (running) { lastError = error.getClass().getSimpleName() + ": " + error.getMessage(); state("Conversación detenida: " + lastError, FaceState.ERROR, null); }
        } finally {
            running = active = wakeListening = microphoneRecording = false;
            if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            main.post(() -> stopSelf());
        }
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
                    if (recording) { recorder.stop(); recording = false; microphoneRecording = wakeListening = false; microphoneNotification(false); }
                    if (!paused) { control("pause"); paused = true; }
                    if (SystemClock.elapsedRealtime() - lastHeartbeat >= 1000) {
                        control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime();
                    }
                    SystemClock.sleep(40); continue;
                }
                if (paused) { control("resume"); paused = false; }
                if (!recording) { recorder.startRecording(); recording = microphoneRecording = true; wakeListening = wakeEnabled; microphoneNotification(true); }
                int count = recorder.read(data, 0, data.length);
                if (count < 0) { if (running) throw new IOException("Error de captura PCM"); else break; }
                if (count > 0 && !speaking && !RingService.ringing) frame(2, data, count);
            }
        } catch (Exception error) {
            if (running) { lastError = error.getClass().getSimpleName() + ": " + error.getMessage(); state("Micrófono detenido: " + lastError, FaceState.ERROR, null); }
        } finally {
            running = active = wakeListening = microphoneRecording = false;
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
                if (conversationOnly) {
                    if (!VoiceSessionPolicy.conversationEventAllowed(type) || event.has("tool_call_id")) throw new IOException("Evento de acción rechazado");
                    if ("thinking".equals(type)) {
                        String id = event.optString("turn_id"); long deadline = event.optLong("deadline_ms");
                        if (!conversationTurn.begin(id, event.optLong("turn_seq"), event.optLong("speech_end_ms"), deadline, SystemClock.elapsedRealtime()))
                            throw new IOException("Turno duplicado o fuera de plazo");
                        speaking = true;
                        state("LFM pensando · pausando micrófono · sin acciones", FaceState.THINKING, "Escuché: " + event.optString("text"));
                        main.postDelayed(() -> {
                            if (running && conversationTurn.matches(id) && conversationTurn.expiredBeforeStart(SystemClock.elapsedRealtime())) {
                                lastError = "Se agotó el plazo; respuesta descartada"; stopSelf();
                            }
                        }, Math.max(0, deadline - SystemClock.elapsedRealtime()));
                    } else if ("say".equals(type)) {
                        speaking = true; main.post(() -> apply(event));
                    } else if ("error".equals(type)) {
                        conversationTurn.clear(); speaking = false;
                        state("No se obtuvo una respuesta completa a tiempo · sin acciones", FaceState.ERROR, null);
                    } else if ("state".equals(type)) {
                        if ("command".equals(event.optString("state"))) state("Escuchando · conversación sin acciones", FaceState.LISTENING, null);
                        else if (!conversationTurn.hasActiveTurn()) state(microphoneRecording ? "MICRÓFONO ACTIVO · LFM · di " + AssistantConfig.NAME : "Reanudando micrófono · LFM sin acciones", FaceState.READY, null);
                    }
                    continue;
                }
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
            if (conversationOnly) {
                if (!"say".equals(event.optString("type")) || event.has("tool_call_id") || event.optInt("actions_executed", -1) != 0
                        || !conversationTurn.acceptReply(event.optString("turn_id"), event.optLong("turn_seq"),
                            event.optLong("speech_end_ms"), event.optLong("deadline_ms"), SystemClock.elapsedRealtime())) return;
                message = event.getString("text");
                if (message.isEmpty() || message.length() > 700) throw new IOException("Respuesta inválida");
            } else switch (event.optString("type")) {
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
        } catch (Exception error) {
            if (conversationOnly) { lastError = "Respuesta no válida descartada"; stopSelf(); return; }
            ok = false; message = "No se pudo completar la acción. No la daré por hecha.";
        }
        if (event.has("tool_call_id")) {
            state(message + " · devolviendo resultado a la IA", FaceState.THINKING, null);
            toolResult(event.optString("tool_call_id"), ok, message);
            return;
        }
        if (!ttsReady) { state(message + " (voz no disponible)", FaceState.ERROR, null); finishSpeech(); return; }
        if (!conversationOnly) control("tts_started"); state("Preparando voz · " + message, FaceState.PREPARING, null);
        Bundle params = new Bundle(); params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        String id = conversationOnly ? event.optString("turn_id") : UUID.randomUUID().toString();
        activeSpeechId = id; activeSpeechText = message; final int generation = ++speechGeneration;
        if (tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
            state(message + " (no se pudo iniciar la voz)", FaceState.ERROR, null); finishSpeech();
        }
        main.postDelayed(() -> { if (speaking && speechGeneration == generation) {
            tts.stop(); state("Voz detenida por límite de reproducción", FaceState.ERROR, null); finishSpeech();
        } }, conversationOnly ? 20000 : 12000);
    }
    void finishSpeech() {
        finishSpeech(false);
    }
    void finishSpeech(boolean completed) {
        if (conversationOnly && !activeSpeechId.isEmpty()) {
            controlTurn(completed ? "tts_done" : "tts_failed", activeSpeechId, SystemClock.elapsedRealtime());
            conversationTurn.clear();
        }
        resumeAfter = SystemClock.elapsedRealtime() + 600; speaking = false; activeSpeechId = "";
        if (running) {
            if (!conversationOnly) control("tts_done");
            if (uiState != FaceState.ERROR) state(conversationOnly ? "Reanudando micrófono · LFM sin acciones" : "MICRÓFONO ACTIVO · " + (wakeEnabled ? "esperando " + AssistantConfig.NAME : "usa Hablar ahora"), FaceState.READY, null);
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
        running = active = wakeListening = wakeTestActive = conversationTestActive = microphoneRecording = false; main.removeCallbacksAndMessages(null);
        conversationTurn.clear();
        expiresAt = 0;
        controls.shutdownNow();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (recorder != null) try { recorder.stop(); } catch (Exception ignored) {}
        if (tts != null) { tts.stop(); tts.shutdown(); }
        state(lastError.isEmpty() ? (pilotExpired ? "Micrófono apagado · terminó el piloto" : "Micrófono apagado") : "Micrófono apagado · " + lastError, lastError.isEmpty() ? FaceState.MUTED : FaceState.ERROR, null); stopForeground(true); super.onDestroy();
    }
}
