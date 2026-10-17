package local.doja.voice;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
public class RingService extends Service {
    public static volatile boolean ringing = false;
    Ringtone tone;
    final Handler handler = new Handler(Looper.getMainLooper());
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("timer", "Temporizadores " + AssistantConfig.NAME, NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, RingService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        startForeground(11, new Notification.Builder(this, "timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Temporizador terminado").setContentText("El sonido se detiene solo a los 30 segundos.")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER SONIDO", stop).build());
        ringing = true;
        if (tone == null) {
            tone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (tone != null) {
                tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                tone.setLooping(true); tone.play();
            }
        }
        handler.removeCallbacksAndMessages(null); handler.postDelayed(() -> stopSelf(), 30000);
        return START_NOT_STICKY;
    }
    public void onDestroy() {
        ringing = false; handler.removeCallbacksAndMessages(null);
        if (tone != null) tone.stop(); stopForeground(true); super.onDestroy();
    }
}
