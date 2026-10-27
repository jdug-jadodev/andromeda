package local.doja.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** Speaks the fixed local cancellation phrase, then reopens the wake service. */
public final class CancelSpeakerService extends Service {
    static final String TEXT = "Está bien, aquí estaré cuando lo necesites.";
    private static final String CHANNEL = "cancel_voice";
    private static final int NOTIFICATION_ID = 18;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean finished;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(
            new NotificationChannel(CHANNEL, "Cancelar solicitud", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Andrómeda")
            .setContentText("Cerrando la solicitud")
            .setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final boolean speak = intent == null || intent.getBooleanExtra("speak", true);
        if (!speak) { finish(); return START_NOT_STICKY; }
        tts = new TextToSpeech(this, status -> {
            if (finished) return;
            if (status != TextToSpeech.SUCCESS || tts == null) { finish(); return; }
            tts.setLanguage(new Locale("es", "US"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) { }
                public void onDone(String id) { handler.post(CancelSpeakerService.this::finish); }
                public void onError(String id) { handler.post(CancelSpeakerService.this::finish); }
            });
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            if (tts.speak(TEXT, TextToSpeech.QUEUE_FLUSH, params, "cancel") == TextToSpeech.ERROR)
                finish();
        }, "com.google.android.tts");
        handler.postDelayed(this::finish, 7000L);
        return START_NOT_STICKY;
    }

    private synchronized void finish() {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (RuntimeException ignored) { }
        tts = null;
        try {
            startService(new Intent(this, VoiceService.class).setAction("CANCEL_DONE"));
        } catch (RuntimeException unavailable) { }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (RuntimeException ignored) { }
        tts = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
