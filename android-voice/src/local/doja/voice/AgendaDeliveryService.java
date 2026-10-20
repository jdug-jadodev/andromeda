package local.doja.voice;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded offline delivery, independent of conversation/model. Never starts inference. */
public final class AgendaDeliveryService extends Service {
    static volatile boolean active;
    static volatile long expiresAt;
    static volatile String lastControlStatus="";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object outputLock = new Object();
    private String id = ""; private long revision;
    private volatile boolean ended, started;
    private volatile boolean outputStopped = true;
    private volatile boolean lostVoiceStop;
    private PowerManager.WakeLock wakeLock;
    private AgendaEntry entry;
    private AlarmOccurrencePolicy occurrence;
    private final String controlNonce=UUID.randomUUID().toString();
    private volatile boolean silenced;
    private TextToSpeech tts;
    private Ringtone tone;
    private SilenceListener listener;
    private SilenceListener.Handle interruption;
    private boolean outputRequested,interruptionStarted;
    private final AgendaOutputs.Output ownedOutput = () -> end(false);
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) { end(false); stopSelf(); return START_NOT_STICKY; }
        if("SNOOZE".equals(intent.getAction()) || "DISMISS".equals(intent.getAction()) || "SILENCE".equals(intent.getAction())) {
            if(!ended && entry!=null && entry.kind==AgendaEntry.Kind.ALARM && id.equals(intent.getStringExtra("id"))
                    && revision==intent.getLongExtra("revision",-1) && controlNonce.equals(intent.getStringExtra("control_nonce")))
                controlOccurrence(AlarmOccurrencePolicy.Action.valueOf(intent.getAction()));
            else if(!active) stopSelf();
            return START_NOT_STICKY;
        }
        String requested = intent.getStringExtra("id"); long version = intent.getLongExtra("revision", -1);
        if (!AgendaEntry.validId(requested) || version <= 0 || !"DELIVER".equals(intent.getAction())) { if (!active) stopSelf(); return START_NOT_STICKY; }
        if (!id.isEmpty()) {
            if (!id.equals(requested) || revision != version) worker.execute(() -> failBusy(requested, version));
            return START_NOT_STICKY; // Do not replace a sounding event or extend its deadline.
        }
        id = requested; revision = version; active = true; expiresAt = SystemClock.elapsedRealtime() + 30000;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("agenda", "Alarmas y recordatorios", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 31, new Intent(this, AgendaDeliveryService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        startForeground(13, new Notification.Builder(this, "agenda").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Aviso de agenda · prototipo").setContentText("Máximo 30 s. Detener no cancela otros avisos.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER AVISO", stop).build());
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "andromeda:agenda-delivery");
        wakeLock.acquire(35000); // Due-event delivery only; never resident or indefinite.
        stopService(new Intent(this, CaptureProbeService.class));
        if(!VoiceService.continuousVoiceActive) stopService(new Intent(this, VoiceService.class));
        main.postDelayed(() -> end(entry != null && entry.kind == AgendaEntry.Kind.ALARM && started), 30000);
        worker.execute(() -> {
            try (AgendaStore store = new AgendaStore(this)) {
                AgendaEntry found = store.find(id);
                if (found == null || found.revision != revision || found.state != AgendaEntry.State.DELIVERING) { main.post(() -> end(false)); return; }
                entry = found;
                if(found.kind==AgendaEntry.Kind.ALARM) occurrence=new AlarmOccurrencePolicy(found,expiresAt,SystemClock.elapsedRealtime());
                main.post(() -> waitForCapture(SystemClock.elapsedRealtime() + 2000));
            } catch (Exception failure) { main.post(() -> end(false)); }
        });
        update(); return START_NOT_STICKY;
    }
    private void failBusy(String id, long revision) {
        try (AgendaStore store = new AgendaStore(this)) {
            AgendaEntry found = store.find(id);
            if (found != null && found.revision == revision && found.state == AgendaEntry.State.DELIVERING) store.finish(found, false);
        } catch (Exception ignored) {}
    }
    private void waitForCapture(long deadline) {
        if (ended) return;
        if (VoiceService.active && !VoiceService.continuousVoiceActive || VoiceService.microphoneRecording || SilenceListener.recording() || AudioInputGate.occupied()) {
            if (SystemClock.elapsedRealtime() >= deadline) { end(false); return; }
            main.postDelayed(() -> waitForCapture(deadline), 25); return;
        }
        if (RingService.ringing || !safe()) { end(false); return; }
        listener = new SilenceListener(this, this::update, SilenceDiagnostics.Owner.TONE); listener.prepare();
        if (entry.kind == AgendaEntry.Kind.ALARM) {
            interruption=listener.armAlarm(id,expiresAt,Math.min(expiresAt,SystemClock.elapsedRealtime()+3000),() -> !ended,
                () -> requestAlarm(true),() -> requestAlarm(false),text -> {
                    AlarmOccurrencePolicy.Action action=AlarmOccurrencePolicy.command(text); if(action!=null) controlOccurrence(action);
                });
            if(interruption==null) requestAlarm(false);
            main.postDelayed(() -> requestAlarm(false),SilenceWindowPolicy.ARM_MS);
        } else initializeVoice();
    }
    private void requestAlarm(boolean prepared) {
        if(ended || outputRequested) return;
        outputRequested=true;
        if(!prepared) { if(listener!=null) listener.end(interruption); interruption=null; }
        worker.execute(this::startAlarm);
    }
    private boolean safe() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo(); getSystemService(ActivityManager.class).getMemoryInfo(info);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temperature = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int mode = getSystemService(AudioManager.class).getMode();
        AudioManager audio = getSystemService(AudioManager.class);
        int stream = entry != null && entry.kind == AgendaEntry.Kind.ALARM ? AudioManager.STREAM_ALARM : AudioManager.STREAM_MUSIC;
        return info.availMem / 1024 >= 500000 && temperature >= 0 && temperature < 400
            && audio.getStreamVolume(stream) > 0 && !audio.isStreamMute(stream)
            && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }
    private boolean acceptOutput(Runnable playback) {
        synchronized (AgendaStore.COORDINATOR) {
            synchronized (outputLock) {
                if (ended || !safe() || SystemClock.elapsedRealtime() >= expiresAt) return false;
                try (AgendaStore store = new AgendaStore(this)) {
                    AgendaEntry current = store.find(id);
                    if (current == null || current.revision != revision || current.state != AgendaEntry.State.DELIVERING || !AgendaOutputs.begin(id, ownedOutput)) return false;
                    outputStopped = false; playback.run(); started = true; return true;
                } catch (Exception failure) { return false; }
            }
        }
    }
    private void startAlarm() {
        if (!acceptOutput(() -> {
            tone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (tone == null) throw new IllegalStateException("Alarm output unavailable");
            tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            tone.setLooping(true); tone.play();
            if (!tone.isPlaying()) throw new IllegalStateException("Alarm did not play");
        })) { end(false); return; }
        beginInterruption(); guardOutput();
    }
    private void initializeVoice() {
        tts = new TextToSpeech(this, status -> main.post(() -> {
            if (ended) return;
            if (status != TextToSpeech.SUCCESS) { end(false); return; }
            Set<Voice> available = tts.getVoices(); Voice selected = null;
            List<Voice> voices = new ArrayList<>(available == null ? Collections.emptySet() : available); voices.sort(Comparator.comparing(Voice::getName));
            for (Voice voice : voices) if (!voice.isNetworkConnectionRequired() && "es".equals(voice.getLocale().getLanguage())) {
                if (selected == null) selected = voice;
                if ("US".equals(voice.getLocale().getCountry())) { selected = voice; break; }
            }
            AudioManager audio = getSystemService(AudioManager.class);
            if (selected == null || audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || tts.setVoice(selected) == TextToSpeech.ERROR) { end(false); return; }
            interruption=listener.arm(id+":"+revision,expiresAt,Math.min(expiresAt,SystemClock.elapsedRealtime()+3000),() -> !ended,
                () -> worker.execute(() -> { if (!acceptOutput(() -> {
                Bundle params = new Bundle(); params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
                if (tts.speak("Recordatorio: " + entry.text, TextToSpeech.QUEUE_FLUSH, params, id + ":" + revision) == TextToSpeech.ERROR)
                    throw new IllegalStateException("Reminder voice failed");
            })) end(false);
            else guardOutput(); }),() -> end(false),() -> end(false));
            final SilenceListener.Handle expected=interruption;
            if(expected==null) { end(false); return; }
            main.postDelayed(() -> { if(!ended && !started && interruption==expected) end(false); },SilenceWindowPolicy.ARM_MS);
        }), "com.google.android.tts");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String utterance) { main.post(() -> { if (!ended && utterance.equals(id + ":" + revision)) beginInterruption(); }); }
            public void onDone(String utterance) { if (!ended && utterance.equals(id + ":" + revision)) end(true); }
            public void onError(String utterance) { if (!ended && utterance.equals(id + ":" + revision)) end(false); }
            public void onStop(String utterance, boolean interrupted) { if (!ended && utterance.equals(id + ":" + revision)) end(false); }
        });
    }
    private void beginInterruption() {
        if (!ended && listener != null && interruption!=null && !interruptionStarted) {
            interruptionStarted=listener.outputStarted(interruption);
            if(!interruptionStarted) {
                listener.end(interruption); interruption=null;
                if(entry.kind==AgendaEntry.Kind.REMINDER) { end(false); return; }
            }
        }
        update();
    }
    private void guardOutput() {
        if (ended) return;
        if (!safe() || RingService.ringing) { end(false); return; }
        main.postDelayed(this::guardOutput, 500);
    }
    private boolean end(boolean completed) {
        boolean first;
        synchronized (outputLock) {
            if (ended && outputStopped) return true;
            if(ended && lostVoiceStop) return false; // A detached TTS handle cannot prove an uncertain output stopped.
            first = !ended;
            ended = true;
            completed = completed && started && safe();
            boolean stopped = true;
            try { if (tone != null) { completed = completed && (silenced || tone.isPlaying()); tone.stop(); stopped = !tone.isPlaying(); } }
            catch (RuntimeException failure) { completed = false; stopped = false; }
            if (tts != null) try { stopped &= tts.stop() != TextToSpeech.ERROR; } catch (RuntimeException failure) { stopped = false; }
            if (listener != null) listener.end();
            outputStopped = stopped;
            if (stopped) AgendaOutputs.end(id, ownedOutput);
        }
        if (!first) return outputStopped;
        final boolean delivered = completed; final AgendaEntry finished = entry;
        if (finished != null && !worker.isShutdown()) worker.execute(() -> {
            try (AgendaStore store = new AgendaStore(this)) { store.finish(finished, delivered); } catch (Exception ignored) {}
            main.post(() -> stopSelf());
        });
        else main.post(() -> stopSelf());
        return outputStopped;
    }
    private void controlOccurrence(AlarmOccurrencePolicy.Action action) {
        if(ended || !started || entry==null || entry.kind!=AgendaEntry.Kind.ALARM || occurrence==null || SystemClock.elapsedRealtime()>=expiresAt) return;
        if(action==AlarmOccurrencePolicy.Action.SILENCE) {
            synchronized(outputLock) {
                if(ended) return;
                try { if(tone!=null) { tone.stop(); if(tone.isPlaying()) throw new IllegalStateException("Output still active"); } silenced=true; if(listener!=null) listener.end(); }
                catch(RuntimeException failure) { end(false); return; }
            }
            lastControlStatus="Alarma silenciada · esta vez sigue activa; no se ha eliminado la serie"; update(); return;
        }
        AlarmOccurrencePolicy.Permit permit=occurrence.claim(action,UUID.randomUUID().toString(),SystemClock.elapsedRealtime(),System.currentTimeMillis());
        if(permit==null) return;
        synchronized(outputLock) {
            if(ended) return;
            try { if(tone!=null) { tone.stop(); if(tone.isPlaying()) { end(false); return; } } }
            catch(RuntimeException failure) { end(false); return; }
            ended=true; outputStopped=true; if(listener!=null) listener.end(); AgendaOutputs.end(id,ownedOutput);
        }
        worker.execute(() -> {
            AgendaStore.AlarmReceipt receipt;
            try(AgendaStore store=new AgendaStore(this)) { receipt=store.applyOccurrence(permit); }
            catch(Exception failure) { receipt=new AgendaStore.AlarmReceipt(permit.operation,id,"UNKNOWN",0); }
            final String status=receipt.status; final long actualDue=receipt.due;
            lastControlStatus="SNOOZED".equals(status) ? "Alarma pospuesta cinco minutos"+(entry.weekday==0?" · una sola vez":" · repetición semanal conservada")
                : "DISMISSED".equals(status) ? (entry.weekday==0?"Alarma de una sola vez descartada":"Ocurrencia descartada · la serie no se ha eliminado")
                : "Resultado no confirmado · revisa Alarmas antes de repetir";
            main.post(() -> {
                String details=lastControlStatus;
                if("SNOOZED".equals(status)) {
                    java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss",new Locale("es","CO"));
                    format.setTimeZone(TimeZone.getTimeZone(AgendaEntry.ZONE)); details+=" · "+format.format(new Date(actualDue))+" Bogotá";
                }
                getSystemService(NotificationManager.class).notify(14,new Notification.Builder(this,"agenda").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Resultado del aviso").setContentText(details).setStyle(new Notification.BigTextStyle().bigText(details))
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build());
                update(); stopSelf();
            });
        });
    }
    private PendingIntent occurrenceIntent(String action,int request) {
        return PendingIntent.getService(this,request,new Intent(this,AgendaDeliveryService.class).setAction(action).putExtra("id",id)
            .setData(android.net.Uri.parse("andromeda://alarm/"+id+"/"+revision+"/"+controlNonce+"/"+action))
            .putExtra("revision",revision).putExtra("control_nonce",controlNonce),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void update() {
        if (active && !ended) {
            PendingIntent stop = PendingIntent.getService(this, 31, new Intent(this, AgendaDeliveryService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder notice=new Notification.Builder(this, "agenda").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(SilenceListener.recording() ? "MICRÓFONO ACTIVO · solo interrupción" : "Aviso de agenda · micrófono apagado")
                .setContentText(silenced ? "Silenciada · micrófono apagado; serie conservada" : "Hasta 30 s · solo esta ocurrencia").setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE);
            if(entry!=null && entry.kind==AgendaEntry.Kind.ALARM) {
                notice.addAction(android.R.drawable.ic_lock_idle_alarm,"Posponer 5 min",occurrenceIntent("SNOOZE",32));
                notice.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Descartar esta vez",occurrenceIntent("DISMISS",33));
                notice.addAction(android.R.drawable.ic_lock_silent_mode,"Silenciar",occurrenceIntent("SILENCE",34));
            } else notice.addAction(android.R.drawable.ic_menu_close_clear_cancel,"DETENER AVISO",stop);
            getSystemService(NotificationManager.class).notify(13,notice.build());
        }
        sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
    }
    public void onDestroy() {
        end(false); active = false; expiresAt = 0; main.removeCallbacksAndMessages(null);
        if (listener != null) listener.close();
        synchronized (outputLock) {
            if (tts != null) {
                try { tts.shutdown(); } catch(RuntimeException ignored) { outputStopped=false; }
                lostVoiceStop=!outputStopped; tts = null;
            }
            try {
                if (outputStopped && (tone == null || !tone.isPlaying())) AgendaOutputs.end(id, ownedOutput);
            } catch (RuntimeException ignored) { /* A later selected-ID cleanup may retry its own output. */ }
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        worker.shutdown(); stopForeground(true); update(); super.onDestroy();
    }
}
