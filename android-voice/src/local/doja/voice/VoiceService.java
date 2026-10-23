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
    public static volatile boolean timerTrialActive = false;
    public static volatile boolean assistantToolsActive = false;
    public static volatile boolean continuousVoiceActive = false;
    public static volatile boolean microphoneRecording = false;
    public static volatile long expiresAt = 0;
    // Diagnostic text stays only in process memory, never in preferences/logs.
    public static volatile String lastTranscript = "";
    public static volatile FaceState uiState = FaceState.IDLE;
    public static volatile String lastStatus = "Micrófono apagado · activa solo una prueba autorizada";
    boolean pilotExpired, sessionStarted, conversationClosed;
    boolean preparationOnly, captureCheck, interruptionCheck, outputCheck, decoderCheck;
    volatile boolean prepared;
    volatile boolean outputWindowDone,outputWindowFailed,outputWindowStarted;
    int outputCompleted;
    long outputMaxDecode,outputMaxLag,outputMaxReady;
    static final String PUBLIC_OUTPUT_CHECK="Esta es una comprobación local de voz y captura. El detector debe seguir disponible durante esta frase.";
    final VoiceHealthPolicy health=new VoiceHealthPolicy();
    volatile boolean dialogueListening;
    volatile boolean running, speaking, ttsReady;
    volatile String activeSpeechId = "";
    volatile String activeSpeechText = "";
    String activeWebAnswer = "";
    int activePlaybackMs = 20000;
    int speechGeneration;
    SpeechDiagnostics.Window speechDiagnostic;
    volatile String lastError = "";
    volatile boolean wakeEnabled;
    volatile boolean aiEnabled;
    volatile boolean wakeOnly;
    volatile boolean conversationOnly;
    volatile boolean timerTrial;
    volatile boolean assistantTools;
    volatile boolean continuousVoice, awaitingContext, dialogueEnded;
    volatile boolean audioYielded, resumeRequested;
    PowerManager.WakeLock continuousWakeLock;
    volatile int contextGeneration;
    volatile AgendaActionChannel agendaChannel;
    volatile AlarmActionChannel alarmChannel;
    volatile PhoneVolumeChannel volumeChannel;
    volatile LightVoiceChannel lightChannel;
    LightCommandDispatcher lightDispatcher;
    volatile ReminderReadChannel reminderReadChannel;
    volatile ReminderDeleteChannel reminderDeleteChannel;
    volatile String activeTimerControl = "";
    volatile TimerActionChannel timerChannel;
    final ExecutorService timerExecutor = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(ContinuousVoicePolicy.ACTION_QUEUE));
    final ConversationTurnPolicy conversationTurn = new ConversationTurnPolicy();
    ConversationMemory.Lease contextLease;
    ConversationChannel contextChannel;
    final ExecutorService controls = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(ContinuousVoicePolicy.CONTROL_QUEUE));
    volatile long resumeAfter;
    volatile Socket socket;
    volatile DataOutputStream output;
    AudioRecord recorder;
    final Object micOwner = new Object();
    SilenceListener silenceListener;
    volatile SilenceListener.Handle activeInterruption;
    volatile boolean speechInterrupted;
    volatile boolean mutationRequested;
    TextToSpeech tts;
    final Handler main = new Handler(Looper.getMainLooper());
    public IBinder onBind(Intent intent) { return null; }
    public void onCreate() {
        super.onCreate();
        lightDispatcher = new LightCommandDispatcher(this);
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
                main.post(() -> {
                    if (running && id.equals(activeSpeechId)) {
                        if(outputCheck) {
                            if(silenceListener==null || !silenceListener.outputStarted(activeInterruption)) finishOutputReadiness(id,false,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE);
                            else outputWindowStarted=true;
                            return;
                        }
                        if (speechDiagnostic != null) speechDiagnostic.started(SystemClock.elapsedRealtime());
                        if (conversationOnly) {
                            long now = SystemClock.elapsedRealtime();
                            if (!conversationTurn.acceptStart(id, now)) {
                                if (speechDiagnostic != null) speechDiagnostic.finish(SpeechDiagnostics.End.LATE_START, now);
                                if (tts != null) tts.stop(); lastError = "Respuesta tardía descartada"; stopSelf(); return;
                            }
                        }
                        if(silenceListener==null || !silenceListener.outputStarted(activeInterruption)) {
                            interruptUnavailable(id,speechGeneration); return;
                        }
                        if(conversationOnly) controlTurn("tts_started",id,SystemClock.elapsedRealtime());
                        state(activeSpeechText + (conversationOnly && !timerTrial && !assistantTools ? " · sin acciones" : ""), FaceState.SPEAKING, null);
                    }
                });
            }
            public void onDone(String id) { main.post(() -> { if (running && id.equals(activeSpeechId)) { if(outputCheck) finishOutputReadiness(id,true,VoiceHealthPolicy.NONE); else finishSpeech(true); } }); }
            public void onError(String id) { main.post(() -> { if (running && id.equals(activeSpeechId)) { if(outputCheck) { finishOutputReadiness(id,false,VoiceHealthPolicy.STATE); return; } state("Error de síntesis; micrófono en pausa breve", FaceState.ERROR, null); finishSpeech(false, SpeechDiagnostics.End.ENGINE_ERROR); } }); }
            public void onStop(String id,boolean interrupted) { main.post(() -> {
                if(running && id.equals(activeSpeechId)) { if(outputCheck) finishOutputReadiness(id,false,VoiceHealthPolicy.STATE); else finishSpeech(false,SpeechDiagnostics.End.ENGINE_STOPPED); }
            }); }
        });
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "STOP" : intent.getAction();
        if ("STOP".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if ("PUSH".equals(action)) { if (running) control("activate"); else stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        if (AgendaDeliveryService.active) { lastError = "Termina primero el aviso de agenda"; stopSelf(); return START_NOT_STICKY; }
        if (CaptureProbeService.active) { lastError = "Termina primero el diagnóstico de captura"; stopSelf(); return START_NOT_STICKY; }
        if (sessionStarted) { lastError = "La sesión anterior se está cerrando; vuelve a elegir la prueba"; stopSelf(); return START_NOT_STICKY; }
        sessionStarted = true;
        health.begin(SystemClock.elapsedRealtime());
        preparationOnly="PREPARE".equals(action);
        captureCheck="CHECK_CAPTURE".equals(action);
        interruptionCheck="CHECK_INTERRUPT".equals(action);
        outputCheck="CHECK_OUTPUT".equals(action);
        decoderCheck="CHECK_DECODER".equals(action);
        wakeOnly = intent != null && intent.getBooleanExtra("wake_only", false);
        conversationOnly = intent != null && intent.getBooleanExtra("conversation_only", false);
        if(preparationOnly && !conversationOnly) { lastError="Preparación requiere canal conversacional"; stopSelf(); return START_NOT_STICKY; }
        if((captureCheck || interruptionCheck || outputCheck || decoderCheck) && (!conversationOnly || !MainActivity.visibleToUser
                || getSharedPreferences("ui",MODE_PRIVATE).getInt("continuous_voice_consent",0)!=ContinuousVoicePolicy.CONSENT_VERSION)) {
            lastError="Comprobación de captura requiere app visible y consentimiento continuo vigente"; stopSelf(); return START_NOT_STICKY;
        }
        timerTrial = intent != null && intent.getBooleanExtra("timer_trial", false);
        assistantTools = intent != null && intent.getBooleanExtra("assistant_tools", false);
        continuousVoice = intent != null && intent.getBooleanExtra("continuous_voice", false);
        if (continuousVoice && (!assistantTools || !AssistantConfig.GUIDED_AGENDA || !conversationOnly || timerTrial || wakeOnly)) {
            lastError = "Modo continuo incompatible"; stopSelf(); return START_NOT_STICKY;
        }
        if (assistantTools && (!conversationOnly || timerTrial)) { lastError = "Modo de herramientas incompatible"; stopSelf(); return START_NOT_STICKY; }
        if (timerTrial && !conversationOnly) { lastError = "Prueba de timer requiere el canal TLS conversacional"; stopSelf(); return START_NOT_STICKY; }
        if (wakeOnly && conversationOnly) { lastError = "Modo de sesión inválido"; stopSelf(); return START_NOT_STICKY; }
        wakeTestActive = wakeOnly;
        conversationTestActive = conversationOnly;
        timerTrialActive = timerTrial;
        assistantToolsActive = assistantTools;
        continuousVoiceActive = continuousVoice;
        if (VoiceLifecyclePolicy.holdCpuWhileScreenOff(continuousVoice)) {
            try {
                PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
                continuousWakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"andromeda:continuous-voice");
                continuousWakeLock.setReferenceCounted(false); continuousWakeLock.acquire();
            } catch (RuntimeException ignored) { continuousWakeLock=null; }
        }
        if (!wakeOnly && !conversationOnly) initializeTts();
        int requestedSeconds = intent == null ? 90 : intent.getIntExtra("seconds", 90);
        final int seconds = requestedSeconds == 300 ? 300 : 90;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("microphone", "Micrófono " + AssistantConfig.NAME + " — piloto", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, VoiceService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "microphone")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Preparando sesión · micrófono aún apagado")
            .setContentText(assistantTools ? "Agenda y web explícita · experimental. Toca Detener." : timerTrial ? "Timer por petición validada; resultado y cierre. Toca Detener." : conversationOnly ? "Conversación LFM sin acciones. Toca Detener." : wakeOnly ? "Solo activación y transcripción local; sin órdenes. Toca Detener." : "Audio local. Toca Detener para silenciar.").setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop).build();
        startForeground(10, notification);
        running = active = true;
        if (!wakeOnly && !decoderCheck) {
            silenceListener = new SilenceListener(this, () -> {
                if (running) { microphoneNotification(microphoneRecording); state(lastStatus, uiState, null); }
            }, SilenceDiagnostics.Owner.SPEECH);
            silenceListener.prepare();
        }
        if (conversationOnly) ConversationContexts.scheduleExpiry();
        lastTranscript = "";
        expiresAt = continuousVoice ? 0 : SystemClock.elapsedRealtime() + seconds * 1000L;
        state("Preparando canal local · micrófono aún apagado", FaceState.PREPARING, null);
        if (!continuousVoice) main.postDelayed(() -> { pilotExpired = true; stopSelf(); }, seconds * 1000L);
        new Thread(decoderCheck ? this::checkPublicDecoder : conversationOnly ? this::captureConversation : this::capture, "doja-pcm").start();
        return START_NOT_STICKY;
    }
    void checkPublicDecoder() {
        try {
            health.phase(VoiceHealthPolicy.SILENCE);
            main.postDelayed(() -> { if(running && !prepared) { lastError="Comprobación pública agotada"; stopSelf(); } },60000);
            if(!PublicDecoderCheck.run(this,main,() -> running && MainActivity.visibleToUser)) throw new IOException("Comprobación pública incompleta");
            health.phase(VoiceHealthPolicy.PREPARED); prepared=true;
            VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null);
        } catch(Exception | LinkageError failure) {
            VoiceHealthStore.finish(this,health,VoiceHealthStore.kind(failure),failure); lastError="Comprobación pública incompleta";
        } finally { main.post(this::stopSelf); }
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
        }); } catch (RejectedExecutionException ignored) { if (running) { lastError = "Canal local saturado; escucha detenida"; main.post(() -> stopSelf()); } }
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
        }); } catch (RejectedExecutionException ignored) { if (running) { lastError = "Canal local saturado; escucha detenida"; main.post(() -> stopSelf()); } }
    }
    void sendContinuousContext(JSONObject control) {
        controls.execute(() -> {
            try {
                byte[] data = control.toString().getBytes(StandardCharsets.UTF_8);
                if (!running || data.length > 131072) throw new IOException("Contexto continuo fuera de límite");
                frame(5,data,data.length);
            } catch (Exception error) { if (running) { lastError = "No se completó el relevo de contexto"; main.post(() -> stopSelf()); } }
        });
    }
    void awaitContextTimeout() {
        final int generation = ++contextGeneration;
        main.postDelayed(() -> {
            if (ContinuousVoicePolicy.resetTimeoutApplies(running,awaitingContext,contextGeneration,generation)) { lastError = "Relevo de contexto agotado"; stopSelf(); }
        },ContinuousVoicePolicy.RESET_MS);
    }
    long playbackDeadline(long duration) {
        long deadline = SystemClock.elapsedRealtime() + duration;
        return continuousVoice ? deadline : Math.min(expiresAt,deadline);
    }
    void resetActionChannels() {
        TimerActionChannel oldTimer = timerChannel;
        AgendaActionChannel oldAgenda = agendaChannel;
        PhoneVolumeChannel oldVolume = volumeChannel;
        LightVoiceChannel oldLight = lightChannel;
        ReminderReadChannel oldRead = reminderReadChannel;
        AlarmActionChannel oldAlarm=alarmChannel;
        ReminderDeleteChannel oldDelete=reminderDeleteChannel;
        if (timerTrial || assistantTools) timerChannel = new TimerActionChannel(contextChannel.sessionId,contextChannel.contextId,expiresAt,assistantTools && AssistantConfig.GUIDED_AGENDA,continuousVoice);
        if (assistantTools) agendaChannel = new AgendaActionChannel(contextChannel.sessionId,contextChannel.contextId,expiresAt,AssistantConfig.GUIDED_AGENDA,continuousVoice,continuousVoice && AssistantConfig.ALARM_LIFECYCLE,AssistantConfig.GUIDED_AGENDA);
        if(continuousVoice && AssistantConfig.ALARM_LIFECYCLE) alarmChannel=new AlarmActionChannel(contextChannel.sessionId,contextChannel.contextId);
        if (assistantTools) volumeChannel = new PhoneVolumeChannel(contextChannel.sessionId,contextChannel.contextId,expiresAt,AssistantConfig.GUIDED_AGENDA,continuousVoice);
        if (assistantTools) lightChannel = new LightVoiceChannel(contextChannel.sessionId,contextChannel.contextId);
        if(assistantTools && AssistantConfig.GUIDED_AGENDA) reminderReadChannel=new ReminderReadChannel(contextChannel.sessionId,contextChannel.contextId,expiresAt,continuousVoice);
        if(continuousVoice && AssistantConfig.ALARM_LIFECYCLE) reminderDeleteChannel=new ReminderDeleteChannel(contextChannel.sessionId,contextChannel.contextId);
        if (oldTimer != null) oldTimer.close();
        if (oldAgenda != null) oldAgenda.close();
        if (oldVolume != null) oldVolume.close();
        if (oldLight != null) oldLight.close();
        if (oldRead != null) oldRead.close();
        if(oldAlarm!=null) oldAlarm.close();
        if(oldDelete!=null) oldDelete.close();
    }
    void microphoneNotification(boolean recording) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, VoiceService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        manager.notify(10, new Notification.Builder(this, "microphone").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(SilenceListener.recording() ? "MICRÓFONO ACTIVO · solo interrupción"
                : recording ? continuousVoice ? "MICRÓFONO ACTIVO · modo continuo" : "MICRÓFONO ACTIVO · sesión limitada"
                    : continuousVoice ? "Micrófono en pausa · modo continuo" : "Micrófono en pausa · sesión limitada")
            .setContentText(SilenceListener.recording() ? "Di silencio o Andrómeda, silencio · no cancela eventos"
                 : assistantTools ? "Agenda y consultas web explícitas · prototipo" : timerTrial ? "LFM local · timer por petición · Detener para silenciar" : conversationOnly ? "LFM local · sin acciones · Detener para silenciar" : "Audio local · Detener para silenciar")
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop).build());
    }
    void captureConversation() {
        try {
            ConversationMemory.Snapshot fresh = ConversationMemory.emptySnapshot();
            List<ConversationMemory.Snapshot> catalog = continuousVoice ? ConversationContexts.MEMORY.catalogForContinuous(SystemClock.elapsedRealtime()) : ConversationContexts.MEMORY.catalog(SystemClock.elapsedRealtime());
            health.phase(VoiceHealthPolicy.CONNECT);
            VoiceConnection connection = VoiceConnection.open(this, "microphone", fresh, catalog, timerTrial, assistantTools,continuousVoice);
            socket = connection.socket; output = connection.output;
            health.phase(VoiceHealthPolicy.READY);
            if (!running) throw new IOException("Sesión detenida");
            if (!connection.ready.optBoolean("speech_interrupt")) throw new IOException("Falta soporte local de interrupción de voz");
            contextChannel = new ConversationChannel(ConversationContexts.MEMORY, fresh, catalog, connection.ready);
            if (!running) { contextChannel.close(); throw new IOException("Sesión detenida"); }
            wakeEnabled = aiEnabled = true;
            int seconds = connection.ready.optInt("seconds");
            if (continuousVoice ? seconds != 0 : seconds != 90 && seconds != 300) throw new IOException("Duración inválida");
            if (!continuousVoice) expiresAt = Math.min(expiresAt, SystemClock.elapsedRealtime() + seconds * 1000L);
            health.phase(VoiceHealthPolicy.CHANNELS); resetActionChannels();
            if (!continuousVoice) main.postDelayed(() -> { pilotExpired = true; stopSelf(); }, Math.max(0, expiresAt - SystemClock.elapsedRealtime()));
            health.phase(VoiceHealthPolicy.TTS); main.post(this::initializeTts);
            long ttsDeadline = playbackDeadline(12000);
            while (running && !ttsReady && lastError.isEmpty() && SystemClock.elapsedRealtime() < ttsDeadline) SystemClock.sleep(25);
            if (!ttsReady || !running) throw new IOException("No se preparó una voz española offline");
            health.phase(VoiceHealthPolicy.SILENCE);
            if (silenceListener == null || !silenceListener.awaitReady(ttsDeadline))
                throw new IOException("No se preparó el reconocimiento offline de silencio");
            if(preparationOnly) {
                health.phase(VoiceHealthPolicy.PREPARED); prepared=true;
                VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null); return; // No AudioRecord, PCM, speech or actions.
            }
            if(outputCheck) { checkPublicOutput(); return; }
            if(interruptionCheck) {
                health.phase(VoiceHealthPolicy.INTERRUPTION);
                long before=SystemClock.elapsedRealtime()+3000;
                activeInterruption=silenceListener.arm(UUID.randomUUID().toString(),before,before,() -> running && !prepared,
                    () -> {
                        if(!running || prepared) return;
                        if(VoiceHealthStore.interruption(this,activeInterruption)) health.microphoneStarted();
                        health.phase(VoiceHealthPolicy.PREPARED); prepared=true;
                        VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null); silenceListener.end(activeInterruption);
                    },() -> {
                        if(!running || prepared) return;
                        if(VoiceHealthStore.interruption(this,activeInterruption)) health.microphoneStarted();
                        VoiceHealthStore.finish(this,health,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE,null); lastError="Preparación de interrupción no disponible";
                    },() -> {});
                if(activeInterruption==null) { VoiceHealthStore.finish(this,health,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE,null); return; }
                while(running && !prepared && lastError.isEmpty() && SystemClock.elapsedRealtime()<before) SystemClock.sleep(10);
                if(!prepared && lastError.isEmpty()) { VoiceHealthStore.interruption(this,activeInterruption); VoiceHealthStore.finish(this,health,VoiceHealthPolicy.TIMEOUT,null); }
                return; // Detector never enters playing: PCM is not decoded, sent, stored or used as authorization.
            }
            connection.socket.setSoTimeout(0);
            if(!captureCheck) new Thread(() -> responses(connection.input), "doja-events").start();
            if(captureCheck) main.postDelayed(() -> {
                if(running) {
                    VoiceHealthStore.finish(this,health,VoiceHealthPolicy.TIMEOUT,null);
                    lastError="Comprobación local de captura agotada"; stopSelf();
                }
            },3000);
            health.phase(VoiceHealthPolicy.PCM_CREATE);
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 6400));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("No se pudo iniciar PCM");
            byte[] data = new byte[640], packet = new byte[648];
            java.nio.ByteBuffer stampBytes = java.nio.ByteBuffer.wrap(packet);
            AudioTimestamp timestamp = new AudioTimestamp();
            boolean recording = false, clockReady = false;
            long readFrames = 0, clockDeadline = 0, lastHeartbeat = 0, localHealth=0;
            while (running) {
                long healthNow=SystemClock.elapsedRealtime();
                if(healthNow-localHealth>=500) {
                    localHealth=healthNow; AudioManager audio=getSystemService(AudioManager.class);
                    if(audio.isMicrophoneMute() || audio.getMode()==AudioManager.MODE_IN_CALL || audio.getMode()==AudioManager.MODE_IN_COMMUNICATION)
                        throw new IOException("Captura bloqueada por privacidad o llamada");
                    if(recording) {
                        AudioRecordingConfiguration configuration=recorder.getActiveRecordingConfiguration();
                        if(configuration!=null && configuration.isClientSilenced()) throw new IOException("Captura silenciada por Android");
                    }
                }
                if (RingService.ringing || AgendaDeliveryService.active) {
                    if (!continuousVoice) { control("pause"); throw new IOException("Conversación detenida por aviso"); }
                    if (recording) {
                        recorder.stop(); AudioInputGate.release(micOwner); recording = false; microphoneRecording = wakeListening = false;
                    }
                    if (!audioYielded) {
                        audioYielded = true; resumeRequested = false; speaking = true; control("audio_yield");
                        main.post(() -> {
                            if (!running) return;
                            if (silenceListener != null) silenceListener.end();
                            if(!stopSpeechOutput()) { lastError="No se confirmó la parada de voz"; stopSelf(); return; }
                            activeInterruption=null; activeSpeechId = activeSpeechText = activeTimerControl = activeWebAnswer = "";
                            conversationTurn.clear();
                            state("Aviso en curso · conversación en pausa; no se completan peticiones pendientes",FaceState.PREPARING,null);
                            microphoneNotification(false);
                        });
                    }
                    if (SystemClock.elapsedRealtime()-lastHeartbeat >= 1000) { control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime(); }
                    SystemClock.sleep(25); continue;
                }
                if (continuousVoice && audioYielded && !resumeRequested && !awaitingContext) {
                    resumeRequested = true; awaitingContext = true; speaking = true; control("audio_resume"); awaitContextTimeout();
                }
                if (speaking || awaitingContext || SystemClock.elapsedRealtime() < resumeAfter || silenceListener.busy()) {
                    if (recording) {
                        recorder.stop(); AudioInputGate.release(micOwner); recording = false; microphoneRecording = wakeListening = false; microphoneNotification(false);
                        if (uiState == FaceState.THINKING) state("LFM pensando · micrófono apagado" + (assistantTools ? " · agenda/web experimental" : timerTrial ? " · timer experimental" : " · sin acciones"), FaceState.THINKING, null);
                    }
                    if (SystemClock.elapsedRealtime() - lastHeartbeat >= 1000) { control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime(); }
                    SystemClock.sleep(25); continue;
                }
                if (!recording) {
                    if (!AudioInputGate.acquire(micOwner)) { SystemClock.sleep(10); continue; }
                    health.phase(VoiceHealthPolicy.PCM_START);
                    recorder.startRecording(); recording = microphoneRecording = true; wakeListening = false; health.microphoneStarted();
                    readFrames = 0; clockReady = false; clockDeadline = SystemClock.elapsedRealtime() + 1000;
                    microphoneNotification(true);
                }
                int count = recorder.read(data, 0, data.length);
                if (count <= 0 || count % 2 != 0) throw new IOException("Error de captura PCM");
                readFrames += count / 2;
                health.phase(VoiceHealthPolicy.PCM_CLOCK);
                if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) != AudioRecord.SUCCESS) {
                    if (SystemClock.elapsedRealtime() >= clockDeadline) throw new IOException("El reloj del audio no está disponible");
                    continue;
                }
                long end = timestamp.nanoTime / 1000000L + (readFrames - timestamp.framePosition) * 1000L / 16000L;
                long now = SystemClock.elapsedRealtime();
                if (end > now + 100 || now - end > 1000) throw new IOException("El audio llegó atrasado o con otro reloj");
                if (!clockReady) {
                    clockReady = true; wakeListening = true;
                    if (!conversationTurn.hasActiveTurn()) state(dialogueListening
                        ? "MICRÓFONO ACTIVO · sigue hablando · sin repetir Andrómeda"
                         : "MICRÓFONO ACTIVO · " + (assistantTools ? "agenda/web experimental" : timerTrial ? "timer experimental" : "LFM sin acciones") + " · di " + AssistantConfig.NAME,
                        dialogueListening ? FaceState.LISTENING : FaceState.READY, null);
                }
                if(captureCheck) {
                    health.phase(VoiceHealthPolicy.PREPARED); prepared=true;
                    VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null); return; // Discard PCM, never send it or invoke ASR/tools.
                }
                if (running && !speaking) {
                    health.phase(VoiceHealthPolicy.STREAMING);
                    stampBytes.putLong(0, end); System.arraycopy(data, 0, packet, 8, count); frame(4, packet, count + 8);
                }
            }
        } catch (Exception error) {
            if (running) { VoiceHealthStore.finish(this,health,VoiceHealthStore.kind(error),error); lastError = error.getClass().getSimpleName() + ": " + error.getMessage(); state("Conversación detenida: " + lastError, FaceState.ERROR, null); }
        } finally {
            running = active = wakeListening = microphoneRecording = false;
            if (timerChannel != null) timerChannel.close();
            if (agendaChannel != null) agendaChannel.close();
            if(alarmChannel!=null) alarmChannel.close();
            if (volumeChannel != null) volumeChannel.close();
            if (reminderReadChannel != null) reminderReadChannel.close();
            if (reminderDeleteChannel != null) reminderDeleteChannel.close();
            if (contextChannel != null) contextChannel.close();
            if (contextLease != null) ConversationContexts.MEMORY.release(contextLease);
            ConversationContexts.scheduleExpiry();
            if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
            AudioInputGate.release(micOwner);
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            main.post(() -> stopSelf());
        }
    }
    void checkPublicOutput() throws Exception {
        final long end=SystemClock.elapsedRealtime()+25000;
        long heartbeat=0;
        for(int index=0;index<3 && running;index++) {
            control("heartbeat");
            if(!silenceListener.awaitReady(Math.min(end,SystemClock.elapsedRealtime()+4500))) throw new IOException("Decodificador público no preparado");
            health.phase(VoiceHealthPolicy.OUTPUT);
            String id=UUID.randomUUID().toString(); final int generation=++speechGeneration;
            activeSpeechId=id; activeSpeechText=PUBLIC_OUTPUT_CHECK; speaking=true;
            outputWindowDone=outputWindowFailed=outputWindowStarted=false;
            long windowEnd=Math.min(end,SystemClock.elapsedRealtime()+9000);
            Bundle params=new Bundle(); params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);
            activeInterruption=silenceListener.arm(id,windowEnd,Math.min(windowEnd,SystemClock.elapsedRealtime()+3000),
                () -> running && !outputWindowDone && id.equals(activeSpeechId) && generation==speechGeneration,
                () -> {
                    if(running && !outputWindowDone && id.equals(activeSpeechId) && generation==speechGeneration
                            && tts.speak(PUBLIC_OUTPUT_CHECK,TextToSpeech.QUEUE_FLUSH,params,id)==TextToSpeech.ERROR)
                        finishOutputReadiness(id,false,VoiceHealthPolicy.STATE);
                },() -> finishOutputReadiness(id,false,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE),
                () -> finishOutputReadiness(id,false,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE));
            if(activeInterruption==null) { finishOutputReadiness(id,false,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE); return; }
            state("Comprobación local de salida · voz pública fija · Detener disponible",FaceState.PREPARING,null);
            while(running && !outputWindowDone && SystemClock.elapsedRealtime()<windowEnd) {
                long now=SystemClock.elapsedRealtime(); if(now-heartbeat>=1000) { control("heartbeat"); heartbeat=now; }
                SystemClock.sleep(10);
            }
            if(!outputWindowDone) finishOutputReadiness(id,false,VoiceHealthPolicy.TIMEOUT);
            if(outputWindowFailed || !running) return;
        }
        if(running && outputCompleted==3) { health.phase(VoiceHealthPolicy.PREPARED); prepared=true; VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null); }
    }
    synchronized void finishOutputReadiness(String id,boolean complete,int cause) {
        if(!running || !outputCheck || outputWindowDone || !id.equals(activeSpeechId)) return;
        complete=complete && outputWindowStarted;
        if(activeInterruption!=null) {
            Map<String,Object> value=activeInterruption.diagnostic.snapshot();
            outputMaxDecode=Math.max(outputMaxDecode,((Number)value.get("max_decode_ms")).longValue());
            outputMaxLag=Math.max(outputMaxLag,((Number)value.get("max_pcm_lag_ms")).longValue());
            outputMaxReady=Math.max(outputMaxReady,((Number)value.get("ready_after_ms")).longValue());
            if(VoiceHealthStore.interruption(this,activeInterruption)) health.microphoneStarted();
            VoiceHealthStore.outputReadiness(this,complete?outputCompleted+1:outputCompleted,outputMaxDecode,outputMaxLag,outputMaxReady,
                SilenceDiagnostics.End.valueOf((String)value.get("end")).ordinal(),outputWindowStarted,complete);
        }
        if(!complete) {
            if(cause==VoiceHealthPolicy.INTERRUPT_UNAVAILABLE) health.phase(VoiceHealthPolicy.INTERRUPTION);
            VoiceHealthStore.finish(this,health,cause==VoiceHealthPolicy.NONE?VoiceHealthPolicy.STATE:cause,null);
            lastError="Comprobación de salida no completada";
        }
        if(silenceListener!=null) silenceListener.end(activeInterruption);
        activeInterruption=null;
        outputWindowFailed=!complete;
        if(complete) outputCompleted++;
        outputWindowDone=true; // Before stop(): the queued onStop cannot overwrite this immutable result.
        if(!complete) stopSpeechOutput();
    }
    void executeTimer(JSONObject event) throws Exception {
        if (!running || !(timerTrial || assistantTools) || timerChannel == null) throw new IOException("Ejecutor de timer no habilitado");
        if (assistantTools) agendaChannel.timerExecution();
        TimerActionChannel channel = timerChannel;
        TimerActionChannel.Execution execution = channel.execution(event, SystemClock.elapsedRealtime());
        if (execution == null) return;
        timerExecutor.execute(() -> {
            if (!running || channel != timerChannel) return;
            TimerCreation.Receipt receipt = continuousVoice && audioYielded
                ? new TimerCreation.Receipt(execution.grant.proposal.timerId,execution.grant.proposal.seconds,TimerCreation.Status.REJECTED)
                : TimerStore.createAuthorized(this, execution.permit);
            try {
                if (channel != timerChannel) return;
                JSONObject result = channel.result(execution, receipt);
                if (!running || controls.isShutdown()) return;
                controls.execute(() -> {
                    try {
                        byte[] data = result.toString().getBytes(StandardCharsets.UTF_8);
                        frame(3, data, data.length);
                    } catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
                });
            } catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
        });
    }
    void selectAlarm(JSONObject event) throws Exception {
        if(!running || alarmChannel==null) throw new IOException("Alarm lifecycle not negotiated");
        agendaChannel.alarmExecution(); AlarmActionChannel channel=alarmChannel;
        timerExecutor.execute(() -> {
            if(!running || channel!=alarmChannel || audioYielded) return;
            try(AgendaStore store=new AgendaStore(this)) {
                sendAlarmControl(channel.select(event,store,SystemClock.elapsedRealtime()),channel);
            } catch(Exception failure) { /* Read-only timeout gives no permission and no success announcement. */ }
        });
    }
    void deleteAlarm(JSONObject event) throws Exception {
        if(!running || alarmChannel==null) throw new IOException("Alarm lifecycle not negotiated");
        agendaChannel.alarmExecution(); AlarmActionChannel channel=alarmChannel;
        AlarmDeletion.Permit permit=channel.execution(event,SystemClock.elapsedRealtime());
        timerExecutor.execute(() -> {
            if(!running || channel!=alarmChannel || audioYielded) { permit.revoke(); return; }
            try(AgendaStore store=new AgendaStore(this)) { sendAlarmControl(channel.result(permit,store.deleteAlarm(permit)),channel); }
            catch(Exception failure) { /* Never retry an uncertain mutation. */ }
        });
    }
    void sendAlarmControl(JSONObject result,AlarmActionChannel channel) {
        if(!running || controls.isShutdown() || channel!=alarmChannel) return;
        controls.execute(() -> {
            if(!running || channel!=alarmChannel) return;
            try { byte[] data=result.toString().getBytes(StandardCharsets.UTF_8); frame(3,data,data.length); }
            catch(Exception failure) { if(running) { lastError="Resultado de alarma no confirmado"; main.post(() -> stopSelf()); } }
        });
    }
    void executeAgenda(JSONObject event) throws Exception {
        if (!running || !assistantTools || agendaChannel == null) throw new IOException("Agenda not enabled");
        AgendaActionChannel channel = agendaChannel;
        AgendaPermit permit = channel.execution(event, SystemClock.elapsedRealtime(), System.currentTimeMillis());
        if (permit == null) return;
        timerExecutor.execute(() -> {
            if (!running || channel != agendaChannel) return;
            AgendaStore.Status status;
            if (continuousVoice && audioYielded) { permit.revoke(); status = AgendaStore.Status.REJECTED; }
            else {
                try (AgendaStore store = new AgendaStore(this)) { status = store.create(permit); }
                catch (Exception failure) { status = AgendaStore.Status.UNKNOWN; }
            }
            try {
                if (channel != agendaChannel) return;
                JSONObject result = channel.result(permit, status);
                if (running && !controls.isShutdown()) controls.execute(() -> {
                    try { byte[] data = result.toString().getBytes(StandardCharsets.UTF_8); frame(3, data, data.length); }
                    catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
                });
            } catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
        });
    }
    void readReminders(JSONObject event) throws Exception {
        if(!running || reminderReadChannel==null) throw new IOException("Reminder reading not enabled");
        ReminderReadChannel channel=reminderReadChannel;
        ReminderListPolicy.Permit permit=channel.execution(event,SystemClock.elapsedRealtime());
        timerExecutor.execute(() -> {
            if(!running || channel!=reminderReadChannel) return;
            long began=SystemClock.elapsedRealtime(); ReminderListPolicy.Receipt receipt=ReminderListPolicy.unavailable();
            if(permit.begin(began) && !audioYielded) {
                try(AgendaStore store=new AgendaStore(this)) {
                    AgendaStore.Page page=store.list(AgendaEntry.Kind.REMINDER);
                    if(SystemClock.elapsedRealtime()<permit.grant.before) receipt=ReminderListPolicy.render(page.entries,page.total,System.currentTimeMillis());
                } catch(Exception ignored) { receipt=ReminderListPolicy.unavailable(); }
            }
            if(!running || channel!=reminderReadChannel) return;
            VoiceHealthStore.reminderRead(this,receipt,SystemClock.elapsedRealtime()-began);
            try {
                JSONObject result=channel.result(permit,receipt);
                if(result!=null && running && channel==reminderReadChannel) {
                    byte[] raw=result.toString().getBytes(StandardCharsets.UTF_8); frame(3,raw,raw.length);
                }
            } catch(Exception failure) { if(running) main.post(this::stopSelf); }
        });
    }
    void selectReminderDeletion(JSONObject event) throws Exception {
        if(!running || reminderDeleteChannel==null) throw new IOException("Reminder deletion not negotiated");
        ReminderDeleteChannel channel=reminderDeleteChannel;
        timerExecutor.execute(() -> {
            if(!running || channel!=reminderDeleteChannel || audioYielded) return;
            try(AgendaStore store=new AgendaStore(this)) { sendReminderDeleteControl(channel.select(event,store,SystemClock.elapsedRealtime()),channel); }
            catch(Exception failure) { /* Read-only timeout: no permission and no automatic retry. */ }
        });
    }
    void executeReminderDeletion(JSONObject event) throws Exception {
        if(!running || reminderDeleteChannel==null) throw new IOException("Reminder deletion not negotiated");
        agendaChannel.alarmExecution(); // Shared single-mutation turn reservation, not an alarm target.
        ReminderDeleteChannel channel=reminderDeleteChannel;
        ReminderDeletion.Permit permit=channel.execution(event,SystemClock.elapsedRealtime());
        timerExecutor.execute(() -> {
            if(!running || channel!=reminderDeleteChannel || audioYielded) { permit.revoke(); return; }
            try(AgendaStore store=new AgendaStore(this)) { sendReminderDeleteControl(channel.result(permit,store.deleteReminder(permit)),channel); }
            catch(Exception failure) { /* Never replay an uncertain deletion. */ }
        });
    }
    void sendReminderDeleteControl(JSONObject result,ReminderDeleteChannel channel) {
        if(!running || controls.isShutdown() || channel!=reminderDeleteChannel) return;
        controls.execute(() -> {
            if(!running || channel!=reminderDeleteChannel) return;
            try { byte[] data=result.toString().getBytes(StandardCharsets.UTF_8); frame(3,data,data.length); }
            catch(Exception failure) { if(running) main.post(this::stopSelf); }
        });
    }
    void executeVolume(JSONObject event) throws Exception {
        if (!running || !assistantTools || volumeChannel == null || agendaChannel == null) throw new IOException("Volume not enabled");
        agendaChannel.volumeExecution();
        PhoneVolumeChannel channel = volumeChannel;
        PhoneVolumePolicy.Permit permit = channel.execution(event, SystemClock.elapsedRealtime());
        timerExecutor.execute(() -> {
            if (!running || channel != volumeChannel) return;
            PhoneVolumePolicy.Receipt receipt = continuousVoice && audioYielded
                ? new PhoneVolumePolicy.Receipt(PhoneVolumePolicy.Status.REJECTED,-1) : PhoneVolumeControl.apply(this, permit);
            VoiceHealthStore.volume(this,permit.grant.percent,receipt);
            try {
                if (channel != volumeChannel) return;
                JSONObject result = channel.result(permit, receipt);
                if (running && !controls.isShutdown()) controls.execute(() -> {
                    try { byte[] data = result.toString().getBytes(StandardCharsets.UTF_8); frame(3, data, data.length); }
                    catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
                });
            } catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
        });
    }
    void executeLight(JSONObject event) throws Exception {
        if (!running || !assistantTools || lightChannel == null || agendaChannel == null || lightDispatcher == null)
            throw new IOException("Lights not enabled");
        agendaChannel.lightExecution();
        LightVoiceChannel channel = lightChannel;
        LightVoiceChannel.Execution execution = channel.execution(event, SystemClock.elapsedRealtime());
        timerExecutor.execute(() -> {
            if (!running || channel != lightChannel) return;
            if (continuousVoice && audioYielded) {
                sendLightReceipt(channel, event, execution, "CLOSED", 1, 0, 0, "audio_yield");
                return;
            }
            if (execution.command == null) {
                sendLightReceipt(channel, event, execution, "UNSUPPORTED_ORDER", 1, 0, 0, execution.rejection);
                return;
            }
            lightDispatcher.send(execution.command, LightWriteAuthorization.issueForUser(SystemClock.elapsedRealtime()), result ->
                sendLightReceipt(channel, event, execution, result.status.name(), result.targetCount,
                    result.completedTargetCount, result.acceptedFrameCount, result.detail));
        });
    }
    void sendLightReceipt(LightVoiceChannel channel, JSONObject event, LightVoiceChannel.Execution execution,
            String status, int targetCount, int completed, int accepted, String detail) {
        if (!running || controls.isShutdown() || channel != lightChannel) return;
        controls.execute(() -> {
            try {
                JSONObject result = new JSONObject().put("action", "light_result")
                    .put("session_id", contextChannel.sessionId).put("context_id", contextChannel.contextId)
                    .put("nonce", execution.nonce).put("origin_turn_id", event.optString("origin_turn_id"))
                    .put("origin_sequence", event.optLong("origin_sequence"))
                    .put("request_turn_id", event.optString("request_turn_id"))
                    .put("request_sequence", event.optLong("request_sequence"))
                    .put("status", status).put("target_count", targetCount == 0 ? 1 : targetCount)
                    .put("completed_target_count", completed).put("accepted_frame_count", accepted)
                    .put("actions_executed", "COMPLETE_UNCONFIRMED".equals(status) ? 1 : 0)
                    .put("detail", detail == null ? "" : detail);
                channel.receipt(result);
                byte[] data = result.toString().getBytes(StandardCharsets.UTF_8); frame(3, data, data.length);
            } catch (Exception failure) { if (running) main.post(() -> stopSelf()); }
        });
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
                if (speaking || RingService.ringing || SystemClock.elapsedRealtime() < resumeAfter
                        || silenceListener != null && silenceListener.busy()) {
                    if (recording) { recorder.stop(); AudioInputGate.release(micOwner); recording = false; microphoneRecording = wakeListening = false; microphoneNotification(false); }
                    if (!paused) { control("pause"); paused = true; }
                    if (SystemClock.elapsedRealtime() - lastHeartbeat >= 1000) {
                        control("heartbeat"); lastHeartbeat = SystemClock.elapsedRealtime();
                    }
                    SystemClock.sleep(40); continue;
                }
                if (paused) { control("resume"); paused = false; }
                if (!recording) {
                    if (!AudioInputGate.acquire(micOwner)) { SystemClock.sleep(10); continue; }
                    recorder.startRecording(); recording = microphoneRecording = true; wakeListening = wakeEnabled; microphoneNotification(true);
                }
                int count = recorder.read(data, 0, data.length);
                if (count < 0) { if (running) throw new IOException("Error de captura PCM"); else break; }
                if (count > 0 && !speaking && !RingService.ringing) frame(2, data, count);
            }
        } catch (Exception error) {
            if (running) { lastError = error.getClass().getSimpleName() + ": " + error.getMessage(); state("Micrófono detenido: " + lastError, FaceState.ERROR, null); }
        } finally {
            running = active = wakeListening = microphoneRecording = false;
            if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
            AudioInputGate.release(micOwner);
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            main.post(() -> stopSelf());
        }
    }
    JSONObject readEvent(DataInputStream input) throws Exception {
        int length = input.readInt();
        if (length < 1 || length > 65536) throw new IOException("Evento inválido");
        byte[] data = new byte[length]; input.readFully(data);
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }
    void responses(DataInputStream input) {
        try {
            while (running) {
                JSONObject event = readEvent(input);
                if (!running) return;
                String type = event.optString("type");
                if (wakeOnly && !VoiceSessionPolicy.wakeOnlyEventAllowed(type))
                    throw new IOException("Evento no permitido en prueba de activación");
                if (conversationOnly) {
                    if (!(alarmChannel!=null ? VoiceSessionPolicy.alarmEventAllowed(type) : continuousVoice ? VoiceSessionPolicy.continuousEventAllowed(type) : assistantTools ? VoiceSessionPolicy.toolsEventAllowed(type) : timerTrial ? VoiceSessionPolicy.timerEventAllowed(type) : VoiceSessionPolicy.conversationEventAllowed(type))
                            || event.has("tool_call_id")) throw new IOException("Evento de acción rechazado");
                    if (continuousVoice && "voice_context_request".equals(type)) {
                        if (conversationTurn.hasActiveTurn()) throw new IOException("Hay un turno activo durante el relevo");
                        awaitingContext = true; speaking = true;
                        sendContinuousContext(contextChannel.prepareContinuousReset(event,SystemClock.elapsedRealtime(),dialogueEnded));
                        awaitContextTimeout();
                        continue;
                    }
                    if (continuousVoice && "voice_context_ready".equals(type)) {
                        if (!awaitingContext || conversationTurn.hasActiveTurn()) throw new IOException("Relevo no solicitado");
                        contextChannel = contextChannel.finishContinuousReset(event,SystemClock.elapsedRealtime()); contextLease = contextChannel.lease;
                        conversationTurn.resetScope(); resetActionChannels(); ConversationContexts.scheduleExpiry();
                        activeSpeechId = activeSpeechText = activeTimerControl = activeWebAnswer = lastTranscript = "";
                        contextGeneration++; awaitingContext = dialogueEnded = resumeRequested = false;
                        audioYielded = speaking = event.getBoolean("audio_yielded"); resumeAfter = SystemClock.elapsedRealtime()+600;
                        continue;
                    }
                    if ("context_selected".equals(type)) {
                        contextChannel.select(event, SystemClock.elapsedRealtime()); contextLease = contextChannel.lease;
                        resetActionChannels();
                        ConversationContexts.scheduleExpiry(); continue;
                    }
                    contextChannel.verify(event);
                    if ("thinking".equals(type)) {
                        String id = event.optString("turn_id"); long deadline = event.optLong("deadline_ms");
                        if (!conversationTurn.begin(id, event.optLong("turn_seq"), event.optLong("speech_end_ms"), deadline, SystemClock.elapsedRealtime(), assistantTools ? 60000 : 30000))
                            throw new IOException("Turno duplicado o fuera de plazo");
                        mutationRequested=false;
                        contextChannel.human(event, SystemClock.elapsedRealtime());
                        if (timerTrial || assistantTools) timerChannel.human(event,SystemClock.elapsedRealtime());
                        if (assistantTools) agendaChannel.human(event, SystemClock.elapsedRealtime(), System.currentTimeMillis());
                        if (assistantTools) volumeChannel.human(event,SystemClock.elapsedRealtime());
                        if (assistantTools) lightChannel.human(event,SystemClock.elapsedRealtime());
                        if(reminderReadChannel!=null) reminderReadChannel.human(event,SystemClock.elapsedRealtime());
                        if(alarmChannel!=null) alarmChannel.human(event,SystemClock.elapsedRealtime());
                        if(reminderDeleteChannel!=null) reminderDeleteChannel.human(event,SystemClock.elapsedRealtime());
                        ConversationContexts.scheduleExpiry();
                        if (continuousVoice && audioYielded) { conversationTurn.clear(); continue; }
                        speaking = true;
                        state(event.optBoolean("administrative") ? "Consultando charlas guardadas · micrófono en pausa"
                             : "LFM pensando · pausando micrófono" + (assistantTools ? " · agenda/web experimental" : timerTrial ? " · timer experimental" : " · sin acciones"), FaceState.THINKING, "Escuché: " + event.optString("text"));
                        main.postDelayed(() -> {
                            if (running && conversationTurn.matches(id) && conversationTurn.expiredBeforeStart(SystemClock.elapsedRealtime())) {
                                lastError = "Se agotó el plazo; respuesta descartada"; stopSelf();
                            }
                        }, Math.max(0, deadline - SystemClock.elapsedRealtime()));
                    } else if ("say".equals(type)) {
                        speaking = true; main.post(() -> apply(event));
                    } else if ("timer_execute".equals(type) && (timerTrial || assistantTools)) {
                        mutationRequested=true;
                        executeTimer(event);
                    } else if ("agenda_execute".equals(type) && assistantTools) {
                        mutationRequested=true;
                        executeAgenda(event);
                    } else if ("volume_execute".equals(type) && assistantTools) {
                        mutationRequested=true;
                        executeVolume(event);
                    } else if ("light_execute".equals(type) && assistantTools) {
                        mutationRequested=true;
                        executeLight(event);
                    } else if("reminder_read".equals(type)) {
                        readReminders(event); // Read-only; never reserve or execute an agenda mutation.
                    } else if("reminder_delete_step".equals(type)) {
                        selectReminderDeletion(event);
                    } else if("reminder_delete_execute".equals(type)) {
                        mutationRequested=true;
                        executeReminderDeletion(event);
                    } else if("alarm_select".equals(type) && alarmChannel!=null) {
                        selectAlarm(event);
                    } else if("alarm_delete_execute".equals(type) && alarmChannel!=null) {
                        mutationRequested=true;
                        deleteAlarm(event);
                    } else if ("error".equals(type)) {
                        if(continuousVoice) {
                            if(!conversationTurn.matches(event.optString("turn_id"))) continue;
                            if(!event.optBoolean("recoverable") || event.optInt("actions_executed",-1)!=0 || mutationRequested
                                    || conversationTurn.speechStarted(event.optString("turn_id")) || !activeSpeechId.isEmpty())
                                throw new IOException("No se puede reabrir un turno con resultado incierto");
                            String reason=event.optString("reason");
                            if(!"deadline".equals(reason) && !"generation_failed".equals(reason) && !"invalid_request".equals(reason)) throw new IOException("Fallo recuperable no reconocido");
                            VoiceHealthStore.turnFailure(this,"deadline".equals(reason)?1:"generation_failed".equals(reason)?2:3);
                            conversationTurn.clear(); resumeAfter=SystemClock.elapsedRealtime()+100; speaking=false;
                            state("No se ejecutó esa petición · puedes seguir hablando",FaceState.LISTENING,null);
                            continue;
                        }
                        conversationTurn.clear(); speaking = false;
                        state(assistantTools ? "No hubo respuesta completa; revisa la agenda si había una acción pendiente" : timerTrial ? "No se obtuvo respuesta completa; revisa los timers si había una acción pendiente"
                            : "No se obtuvo una respuesta completa a tiempo · sin acciones", FaceState.ERROR, null);
                    } else if ("conversation_closed".equals(type)) {
                        String reason = event.optString("reason");
                        if (!("explicit_close".equals(reason) || "declined_more".equals(reason) || (timerTrial || assistantTools) && "action_result".equals(reason))
                                || !reason.equals(conversationTurn.closeReason())
                                || !conversationTurn.acceptClose(event.optString("turn_id"), event.optLong("turn_seq"),
                                    event.optLong("speech_end_ms"), event.optLong("deadline_ms"), SystemClock.elapsedRealtime())
                                || !contextChannel.end(event.optString("turn_id"), SystemClock.elapsedRealtime()))
                            throw new IOException("Cierre de otra charla, turno o sesión rechazado");
                        if (continuousVoice) {
                            if (!(event.opt("keep_listening") instanceof Boolean) || !event.getBoolean("keep_listening")) throw new IOException("Escucha continua no confirmada");
                            dialogueEnded = true; awaitingContext = true; speaking = true;
                            awaitContextTimeout();
                            conversationTurn.clear(); ConversationContexts.scheduleExpiry(); continue;
                        }
                        conversationClosed = true; running = false;
                        conversationTurn.clear(); ConversationContexts.scheduleExpiry();
                        main.post(() -> stopSelf());
                    } else if ("state".equals(type)) {
                        if("wake".equals(event.optString("state")) && "audio_clock_resync".equals(event.optString("reason"))) {
                            if(conversationTurn.hasActiveTurn()) throw new IOException("Audio resync during uncertain turn");
                            if(agendaChannel!=null) agendaChannel.cancelDialogue(); if(alarmChannel!=null) alarmChannel.cancelDialogue();
                             if(volumeChannel!=null) volumeChannel.cancelDialogue();
                             if(reminderDeleteChannel!=null) reminderDeleteChannel.cancelDialogue();
                             dialogueListening=false;
                            state("Audio fuera de tiempo descartado · vuelve a dirigirte a Andrómeda",FaceState.READY,null);
                            continue;
                        }
                        if (continuousVoice && "audio_yield".equals(event.optString("state"))) { audioYielded = speaking = true; resumeRequested = false; if(reminderDeleteChannel!=null)reminderDeleteChannel.cancelDialogue(); continue; }
                         if("wake".equals(event.optString("state")) && !conversationTurn.hasActiveTurn()) {
                              if(agendaChannel!=null) agendaChannel.cancelDialogue(); if(alarmChannel!=null) alarmChannel.cancelDialogue();
                              if(volumeChannel!=null) volumeChannel.cancelDialogue();
                              if(reminderDeleteChannel!=null) reminderDeleteChannel.cancelDialogue();
                        }
                        dialogueListening = "command".equals(event.optString("state"));
                        if (dialogueListening) state(microphoneRecording ? "MICRÓFONO ACTIVO · sigue hablando · sin repetir Andrómeda"
                            : "Reanudando micrófono · continúa sin repetir Andrómeda", FaceState.LISTENING, null);
                        else if (!conversationTurn.hasActiveTurn()) state(microphoneRecording ? "MICRÓFONO ACTIVO · LFM · di " + AssistantConfig.NAME : "Reanudando micrófono · LFM" + (assistantTools ? " · agenda/web experimental" : timerTrial ? " · timer experimental" : " sin acciones"), FaceState.READY, null);
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
            if (running) { health.phase(VoiceHealthPolicy.RESPONSES); VoiceHealthStore.finish(this,health,VoiceHealthStore.kind(error),error); lastError = "Canal local cerrado: " + error.getClass().getSimpleName(); main.post(() -> stopSelf()); }
        }
    }
    void apply(JSONObject event) {
        if (!running || wakeOnly) return; // Also reject already queued work after stopping.
        if (continuousVoice && audioYielded) return; // Never speak over the event that owns audio.
        String message;
        boolean ok = true;
        try {
            if (conversationOnly) {
                if (!"say".equals(event.optString("type")) || event.has("tool_call_id") || !(timerTrial || assistantTools) && event.optInt("actions_executed", -1) != 0
                        || !conversationTurn.acceptReply(event.optString("turn_id"), event.optLong("turn_seq"),
                            event.optLong("speech_end_ms"), event.optLong("deadline_ms"), SystemClock.elapsedRealtime(),
                             event.optString("close_reason"), (timerTrial || assistantTools) && ("result".equals(event.optString("timer_control")) || assistantTools && ("agenda_result".equals(event.optString("timer_control")) || "volume_result".equals(event.optString("timer_control")))))) return;
                message = event.getString("text");
                boolean workerRead = assistantTools && "web".equals(event.optString("timer_control"))
                    && event.optJSONObject("web_answer") != null && ("service_answer".equals(event.getJSONObject("web_answer").optString("mode"))
                        || "service_translation".equals(event.getJSONObject("web_answer").optString("mode"))
                        || "service_excerpt".equals(event.getJSONObject("web_answer").optString("mode")));
                if (message.isEmpty() || message.length() > (workerRead ? 2100 : 700)) throw new IOException("Respuesta inválida");
                if (assistantTools) {
                    String localRead=reminderReadChannel==null?null:reminderReadChannel.speech(event);
                    String localDelete=reminderDeleteChannel==null?null:reminderDeleteChannel.speech(event);
                    if(localRead!=null) message=localRead;
                    else if(localDelete!=null) message=localDelete;
                    else if (!(alarmChannel!=null && alarmChannel.speech(event)) && !lightChannel.speech(event) && !volumeChannel.speech(event) && !agendaChannel.speech(event)) timerChannel.validateSpeech(event);
                }
                else if (timerTrial) timerChannel.validateSpeech(event);
                activeWebAnswer = !workerRead ? "" : event.getJSONObject("web_answer").getString(
                    "service_excerpt".equals(event.getJSONObject("web_answer").optString("mode")) ? "evidence" : "answer_es");
                boolean listing="reminder_list".equals(event.optString("timer_control")) || "reminder_delete_question".equals(event.optString("timer_control"));
                if(listing && (message.isEmpty() || message.length()>ReminderListPolicy.MAX_SPOKEN+40)) throw new IOException("Reminder speech outside limit");
                activePlaybackMs = workerRead || listing ? 60000 : 20000;
                activeTimerControl = timerTrial || assistantTools ? event.optString("timer_control") : "";
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
        speechDiagnostic = SpeechDiagnostics.begin(SystemClock.elapsedRealtime(), message.length());
        if (!ttsReady) { state(message + " (voz no disponible)", FaceState.ERROR, null); finishSpeech(false, SpeechDiagnostics.End.NOT_READY); return; }
        if (!conversationOnly) control("tts_started"); state("Preparando voz · " + message, FaceState.PREPARING, null);
        Bundle params = new Bundle(); params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        String id = conversationOnly ? event.optString("turn_id") : UUID.randomUUID().toString();
        if(speechGeneration==Integer.MAX_VALUE) { lastError="Límite seguro de salidas alcanzado"; stopSelf(); return; }
        activeSpeechId = id; activeSpeechText = message; speechInterrupted = false; speaking=true; final int generation = ++speechGeneration;
        final String spokenMessage=message;
        final long playbackEnd=playbackDeadline(conversationOnly?activePlaybackMs:12000);
        final long startBefore=conversationOnly ? event.optLong("deadline_ms") : Math.min(playbackEnd,SystemClock.elapsedRealtime()+3000);
        if(silenceListener==null) { interruptUnavailable(id,generation); return; }
        activeInterruption=silenceListener.arm(id,playbackEnd,startBefore,
            () -> running && speaking && id.equals(activeSpeechId) && generation==speechGeneration && !audioYielded,
            () -> {
                if(!running || !id.equals(activeSpeechId) || generation!=speechGeneration || audioYielded) return;
                if(SystemClock.elapsedRealtime()>=startBefore || tts.speak(spokenMessage,TextToSpeech.QUEUE_FLUSH,params,id)==TextToSpeech.ERROR) {
                    state("No se pudo iniciar la voz a tiempo",FaceState.ERROR,null); finishSpeech(false,SpeechDiagnostics.End.START_REJECTED);
                }
            },() -> interruptUnavailable(id,generation),() -> interruptSpeech(id));
        if(activeInterruption==null) { interruptUnavailable(id,generation); return; }
        main.postDelayed(() -> { if (SpeechDiagnostics.timeoutApplies(speaking, speechGeneration, generation, !id.isEmpty() && id.equals(activeSpeechId))) {
            if(!stopSpeechOutput()) { lastError="Parada de voz no confirmada"; stopSelf(); return; }
            state("Voz detenida por límite de reproducción", FaceState.ERROR, null); finishSpeech(false, SpeechDiagnostics.End.PLAYBACK_LIMIT);
        } }, Math.max(0,playbackEnd-SystemClock.elapsedRealtime()));
    }
    void finishSpeech() {
        finishSpeech(false);
    }
    synchronized void interruptSpeech(String id) {
        if (!running || !speaking || !id.equals(activeSpeechId)) return;
        speechInterrupted = true;
        boolean stopped=stopSpeechOutput();
        if(activeInterruption!=null) activeInterruption.diagnostic.stopped(stopped,SystemClock.elapsedRealtime());
        VoiceHealthStore.interruption(this,activeInterruption);
        if(!stopped) {
            if(speechDiagnostic!=null) speechDiagnostic.finish(SpeechDiagnostics.End.STOP_FAILED,SystemClock.elapsedRealtime());
            lastError="No se confirmó la parada de voz; captura bloqueada"; stopSelf(); return;
        }
        finishSpeech(false, SpeechDiagnostics.End.INTERRUPTED);
    }
    void interruptUnavailable(String id,int generation) {
        if(!running || !id.equals(activeSpeechId) || speechGeneration!=generation) return;
        health.phase(VoiceHealthPolicy.INTERRUPTION); VoiceHealthStore.interruption(this,activeInterruption); VoiceHealthStore.finish(this,health,VoiceHealthPolicy.INTERRUPT_UNAVAILABLE,null);
        boolean stopped=stopSpeechOutput();
        if(activeInterruption!=null) activeInterruption.diagnostic.stopped(stopped,SystemClock.elapsedRealtime());
        lastError=stopped ? "Interrupción local no disponible; escucha detenida" : "Parada de voz no confirmada; escucha detenida";
        if(!stopped && speechDiagnostic!=null) speechDiagnostic.finish(SpeechDiagnostics.End.STOP_FAILED,SystemClock.elapsedRealtime());
        if(stopped) finishSpeech(false,SpeechDiagnostics.End.INTERRUPT_UNAVAILABLE);
        speaking=true; stopSelf(); // No fallback to uninterruptible speech or automatic mic restart.
    }
    boolean stopSpeechOutput() {
        try { return tts!=null && tts.stop()!=TextToSpeech.ERROR; }
        catch(RuntimeException failure) { return false; }
    }
    synchronized void finishSpeech(boolean completed) {
        finishSpeech(completed, completed ? SpeechDiagnostics.End.DONE : SpeechDiagnostics.End.OTHER);
    }
    synchronized void finishSpeech(boolean completed, SpeechDiagnostics.End reason) {
        if (silenceListener != null) silenceListener.end(activeInterruption);
        activeInterruption=null;
        completed = completed && !speechInterrupted;
        if (speechDiagnostic != null) speechDiagnostic.finish(speechInterrupted ? SpeechDiagnostics.End.INTERRUPTED : reason, SystemClock.elapsedRealtime());
        if (conversationOnly && !activeSpeechId.isEmpty()) {
            if (completed && running && conversationTurn.speechStarted(activeSpeechId) && contextChannel != null) {
                if (!activeWebAnswer.isEmpty()) contextChannel.webAnswer(activeSpeechId, activeWebAnswer, SystemClock.elapsedRealtime());
                else if ("web_followup".equals(activeTimerControl)) contextChannel.webFollowup(activeSpeechId, activeSpeechText, SystemClock.elapsedRealtime());
                else if (activeTimerControl.isEmpty()) contextChannel.answer(activeSpeechId, activeSpeechText, SystemClock.elapsedRealtime());
            }
            if (!conversationTurn.finishSpeech(activeSpeechId, completed)) {
                speaking = true; activeSpeechId = "";
                lastError = "Finalización de voz inválida; sesión detenida";
                main.post(() -> stopSelf()); return;
            }
            if (assistantTools && agendaChannel != null) agendaChannel.spoken(activeSpeechId, completed);
            if (assistantTools && volumeChannel != null) volumeChannel.spoken(activeSpeechId,completed,SystemClock.elapsedRealtime());
            if (assistantTools && lightChannel != null) lightChannel.spoken(activeSpeechId,completed,SystemClock.elapsedRealtime());
            if(reminderReadChannel!=null) reminderReadChannel.spoken(activeSpeechId);
            if(alarmChannel!=null) alarmChannel.spoken(activeSpeechId,completed);
            if(reminderDeleteChannel!=null) reminderDeleteChannel.spoken(activeSpeechId,completed,SystemClock.elapsedRealtime());
            controlTurn(speechInterrupted ? "tts_interrupted" : completed ? "tts_done" : "tts_failed", activeSpeechId, SystemClock.elapsedRealtime());
        }
        boolean closing = conversationOnly && conversationTurn.awaitingClose();
        if (closing) {
            activeSpeechId = ""; speaking = true; // Keep capture stopped until authenticated close.
            state("Cerrando charla · micrófono en pausa", FaceState.PREPARING, null);
            main.postDelayed(() -> { if (running && conversationTurn.awaitingClose()) {
                lastError = "No se confirmó el cierre de la charla"; stopSelf();
            } }, 2000);
            return;
        }
        resumeAfter = SystemClock.elapsedRealtime() + 600; speaking = false; activeSpeechId = activeSpeechText = ""; activeTimerControl = ""; activeWebAnswer = "";
        if (running) {
            if (!conversationOnly) control("tts_done");
            if (uiState != FaceState.ERROR) state(conversationOnly ? "Reanudando micrófono · continúa sin repetir Andrómeda" : "MICRÓFONO ACTIVO · " + (wakeEnabled ? "esperando " + AssistantConfig.NAME : "usa Hablar ahora"), conversationOnly ? FaceState.LISTENING : FaceState.READY, null);
        }
    }
    void state(String text, FaceState presentation, String words) {
        if (words != null) lastTranscript = words;
        uiState = presentation; lastStatus = text;
        Intent update = new Intent(STATE).setPackage(getPackageName()).putExtra("state", text).putExtra("ui_state", presentation.name());
        if (words != null) update.putExtra("transcript", words);
        sendBroadcast(update);
    }
    Thread closeConnectionAsync() {
        Socket connected = socket;
        socket = null; output = null;
        return VoiceSocketCleanup.closeAsync(connected);
    }
    public void onDestroy() {
        if(sessionStarted) { health.phase(VoiceHealthPolicy.STOP); VoiceHealthStore.finish(this,health,VoiceHealthPolicy.NONE,null); }
        if (speechDiagnostic != null) speechDiagnostic.finish(SpeechDiagnostics.End.SERVICE_STOP, SystemClock.elapsedRealtime());
        running = active = wakeListening = wakeTestActive = conversationTestActive = timerTrialActive = microphoneRecording = false; main.removeCallbacksAndMessages(null);
        assistantToolsActive = continuousVoiceActive = false;
        if (silenceListener != null) silenceListener.close();
        if (timerChannel != null) timerChannel.close();
        if (agendaChannel != null) agendaChannel.close();
        if(alarmChannel!=null) alarmChannel.close();
        if (volumeChannel != null) volumeChannel.close();
        if (lightChannel != null) lightChannel.close();
        if (reminderReadChannel != null) reminderReadChannel.close();
        if (reminderDeleteChannel != null) reminderDeleteChannel.close();
        timerExecutor.shutdown(); // Accepted durable work is not a timer cancellation.
        conversationTurn.clear();
        if (contextChannel != null) contextChannel.close();
        if (contextLease != null) ConversationContexts.MEMORY.release(contextLease);
        ConversationContexts.scheduleExpiry();
        lastTranscript = ""; activeSpeechId = activeSpeechText = activeWebAnswer = "";
        expiresAt = 0;
        controls.shutdownNow();
        if (lightDispatcher != null) lightDispatcher.close();
        closeConnectionAsync();
        if (recorder != null) try { recorder.stop(); } catch (Exception ignored) {}
        if (tts != null) { stopSpeechOutput(); try { tts.shutdown(); } catch(RuntimeException ignored) { /* Stop failure is not a successful playback receipt. */ } }
        if (continuousWakeLock != null && continuousWakeLock.isHeld()) continuousWakeLock.release();
        continuousWakeLock=null;
        state(lastError.isEmpty() ? (prepared ? "Preparación local completada · micrófono apagado" : conversationClosed ? (contextLease == null ? "Micrófono apagado · búsqueda cerrada" : "Micrófono apagado · charla cerrada; contexto retomable hasta 1 h sin actividad")
            : pilotExpired ? "Micrófono apagado · terminó el piloto" : "Micrófono apagado") : "Micrófono apagado · " + lastError, lastError.isEmpty() ? FaceState.MUTED : FaceState.ERROR, null); stopForeground(true); super.onDestroy();
    }
}
