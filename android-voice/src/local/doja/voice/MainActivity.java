package local.doja.voice;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    TextView clock, face, state, transcript, session;
    final Handler handler = new Handler(Looper.getMainLooper());
    final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
            state.setText(intent.getStringExtra("state"));
            face.setText(intent.getStringExtra("face"));
            if (intent.hasExtra("transcript")) transcript.setText(intent.getStringExtra("transcript"));
        }
    };
    final Runnable tick = new Runnable() {
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
            if (VoiceService.active && VoiceService.expiresAt > 0) {
                long remaining = Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000);
                session.setText("Piloto: quedan " + remaining + " segundos de sesión");
            } else session.setText("Silenciar no cancela temporizadores. Se gestionan aparte.");
            handler.postDelayed(this, 1000);
        }
    };
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            File key = new File(getFilesDir(), "voice-token");
            if (!key.exists()) {
                byte[] data = new byte[32]; new SecureRandom().nextBytes(data);
                try (FileOutputStream out = new FileOutputStream(key)) {
                    out.write(Base64.encode(data, Base64.NO_WRAP));
                }
            }
        } catch (IOException error) { throw new RuntimeException("No se pudo crear credencial local", error); }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setGravity(Gravity.CENTER);
        layout.setPadding(30, 20, 30, 20); layout.setBackgroundColor(Color.BLACK);
        clock = label(layout, "", 52); face = label(layout, "-_-", 64);
        state = label(layout, "Micrófono apagado", 21);
        session = label(layout, "Silenciar no cancela temporizadores. Se gestionan aparte.", 15);
        label(layout, AssistantConfig.NAME + " · voz local experimental. IA según backend; sin escucha permanente.", 16);
        transcript = label(layout, VoiceService.lastTranscript, 18);
        button(layout, "Elegir e iniciar prueba…", () -> {
            if (VoiceService.active) return;
            new android.app.AlertDialog.Builder(this).setTitle("Escucha local limitada")
                .setItems(new String[]{"90 segundos", "5 minutos"}, (dialog, which) -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                    else startForegroundService(new Intent(this, VoiceService.class).setAction("START")
                        .putExtra("seconds", which == 1 ? 300 : 90));
                }).setNegativeButton("Volver", null).show();
        });
        button(layout, "SILENCIAR / detener micrófono", () -> {
            stopService(new Intent(this, VoiceService.class));
            state.setText("Micrófono apagado"); face.setText("-_-");
        });
        button(layout, "Hablar ahora (prueba sin palabra)", () -> {
            if (VoiceService.active) startService(new Intent(this, VoiceService.class).setAction("PUSH"));
        });
        button(layout, "Ver temporizadores", () -> {
            state.setText(TimerStore.describe(this));
        });
        button(layout, "Cancelar temporizadores…", () -> {
            new android.app.AlertDialog.Builder(this).setTitle("Cancelar todos los temporizadores")
                .setMessage(TimerStore.describe(this) + " ¿Quieres cancelarlos todos?")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Cancelar todos", (dialog, which) -> {
                    try { state.setText(TimerStore.cancel(this, true)); }
                    catch (Exception error) { state.setText("No se pudo confirmar la cancelación. Revisa el estado."); }
                }).show();
        });
        button(layout, "Detener sonido de alarma", () -> stopService(new Intent(this, RingService.class)));
        setContentView(layout);
    }
    TextView label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(Color.WHITE);
        view.setTextSize(size); view.setGravity(Gravity.CENTER); parent.addView(view); return view;
    }
    void button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setOnClickListener(v -> action.run());
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(VoiceService.STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        handler.post(tick);
    }
    public void onPause() {
        handler.removeCallbacks(tick); unregisterReceiver(receiver); super.onPause();
    }
    public void onStop() {
        // This pilot may not continue recording invisibly after leaving the screen.
        stopService(new Intent(this, VoiceService.class));
        super.onStop();
    }
}
