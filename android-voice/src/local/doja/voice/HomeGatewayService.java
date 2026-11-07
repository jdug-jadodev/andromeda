package local.doja.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Servicio del gateway de casa. Se enciende explícitamente (por ahora con
 * `enabled=true`); por defecto está apagado. No abre micrófono ni toca el
 * residente en esta fase.
 */
public final class HomeGatewayService extends Service {
    static final String ACTION_START = "START";
    static final String ACTION_STOP = "STOP";
    static final String ACTION_PAIR = "PAIR";
    static final String EXTRA_ENABLED = "enabled";

    private static final String CHANNEL = "home_gateway";
    private static final int NOTIFICATION_ID = 20;

    private HomeGatewayServer server;
    static volatile String lastPairingCode = "";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Casa · " + AssistantConfig.NAME, NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean enabled = intent != null && intent.getBooleanExtra(EXTRA_ENABLED, false);
        if (!enabled && !HomeGatewayConfig.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, notification(
            "Casa · puerto " + HomeGatewayServer.PORT, "Sin emparejar"));

        if (server == null) {
            server = new HomeGatewayServer(this, new HomeGatewayServer.PairingListener() {
                @Override
                public void onPairing(String code) {
                    lastPairingCode = code;
                    updateNotification("Emparejar teléfono", "Código: " + code + " · caduca en 2 min");
                }

                @Override
                public void onClosed() {
                    lastPairingCode = "";
                    updateNotification("Casa · puerto " + HomeGatewayServer.PORT, "Emparejamiento cerrado");
                }
            });
            try {
                server.start();
                server.openPairing();
            } catch (Exception error) {
                Log.e("AndromedaGateway", "No se pudo iniciar: " + error.getClass().getSimpleName()
                    + " " + error.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            }
        } else if (ACTION_PAIR.equals(action)) {
            server.openPairing();
        }
        return START_NOT_STICKY;
    }

    private void updateNotification(String title, String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, notification(title, text));
    }

    private Notification notification(String title, String text) {
        PendingIntent open = PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(
            this, 0, new Intent(this, HomeGatewayService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DETENER", stop)
            .build();
    }

    @Override
    public void onDestroy() {
        lastPairingCode = "";
        if (server != null) server.stop();
        server = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
