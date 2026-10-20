package local.doja.voice;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
public class RingService extends Service {
    public static volatile boolean ringing = false;
    Ringtone tone;
    SilenceListener silenceListener;
    SilenceListener.Handle interruption;
    boolean sessionStarted,outputRequested;
    volatile String soundId = "";
    volatile boolean outputActive;
    public static volatile long soundExpiresAt;
    final Handler handler = new Handler(Looper.getMainLooper());
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(sessionStarted) return START_NOT_STICKY;
        sessionStarted=true;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("timer", "Temporizadores " + AssistantConfig.NAME, NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, RingService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        startForeground(11, new Notification.Builder(this, "timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Temporizador terminado").setContentText("El sonido se detiene solo a los 30 segundos.")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER SONIDO", stop).build());
        ringing = true;
        stopService(new Intent(this, AgendaDeliveryService.class));
        stopService(new Intent(this, CaptureProbeService.class));
        if(!VoiceService.continuousVoiceActive) stopService(new Intent(this, VoiceService.class)); // Continuous mode yields its capture instead.
        soundId = java.util.UUID.randomUUID().toString();
        soundExpiresAt = SystemClock.elapsedRealtime() + 30000;
        if (silenceListener == null) silenceListener = new SilenceListener(this, () -> {
            if (!ringing) return;
            sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
            getSystemService(NotificationManager.class).notify(11,
                new Notification.Builder(this, "timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Temporizador terminado")
                .setContentText(SilenceListener.recording() ? "MICRÓFONO ACTIVO · di silencio o Andrómeda, silencio"
                    : "Micrófono apagado · interrupción no activa; sonido máximo 30 s")
                .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER SONIDO", stop).build());
        }, SilenceDiagnostics.Owner.TONE);
        String id = soundId;
        silenceListener.prepare();
        interruption=silenceListener.arm(id,soundExpiresAt,Math.min(soundExpiresAt,SystemClock.elapsedRealtime()+3000),
            () -> ringing && id.equals(soundId),() -> startSound(id,true),() -> startSound(id,false),() -> interruptSound(id));
        if(interruption==null) startSound(id,false);
        handler.postDelayed(() -> startSound(id,false),SilenceWindowPolicy.ARM_MS);
        handler.postDelayed(() -> stopSelf(),Math.max(0,soundExpiresAt-SystemClock.elapsedRealtime()));
        return START_NOT_STICKY;
    }
    void startSound(String id,boolean prepared) {
        if(!ringing || outputRequested || !id.equals(soundId) || SystemClock.elapsedRealtime()>=soundExpiresAt) return;
        outputRequested=true;
        if(!prepared) { silenceListener.end(interruption); interruption=null; }
        try {
            tone=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if(tone==null) { stopSelf(); return; }
            tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            tone.setLooping(true); tone.play(); outputActive=tone.isPlaying();
            if(interruption!=null && !silenceListener.outputStarted(interruption)) { silenceListener.end(interruption); interruption=null; }
            if(!outputActive) stopSelf();
        } catch(RuntimeException failure) { stopSelf(); }
    }
    void interruptSound(String id) {
        if (!outputActive || id == null || id.isEmpty() || !id.equals(soundId)) return;
        outputActive = false; soundId = "";
        if (silenceListener != null) silenceListener.end();
        boolean stopped=stopSoundOutput();
        if(interruption!=null) interruption.diagnostic.stopped(stopped,SystemClock.elapsedRealtime());
        finishSoundService();
    }
    boolean stopSoundOutput() { try { if(tone!=null) tone.stop(); return tone==null || !tone.isPlaying(); } catch(RuntimeException failure) { return false; } }
    void finishSoundService() { stopSelf(); }
    public void onDestroy() {
        ringing = false; outputActive = false; soundId = ""; soundExpiresAt = 0; handler.removeCallbacksAndMessages(null);
        if (silenceListener != null) silenceListener.close();
        stopSoundOutput(); stopForeground(true); super.onDestroy();
        sendBroadcast(new Intent(VoiceService.STATE).setPackage(getPackageName()));
    }
}
