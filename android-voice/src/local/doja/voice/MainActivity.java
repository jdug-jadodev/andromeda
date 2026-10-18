package local.doja.voice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    FaceView face;
    ProcessingBorderView border;
    FrameLayout root, faceLayer, optionsLayer;
    LinearLayout idleLayer;
    TextView clock, date, weather, weatherDetail, wakeHint, state, transcript, previewBadge, optionsCountdown;
    Button microphoneStop;
    final UiPolicy policy = new UiPolicy();
    final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", new Locale("es", "CO"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CO"));
    private final SimpleDateFormat weatherTimeFormat = new SimpleDateFormat("HH:mm", new Locale("es", "CO"));
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final WeatherClient weatherClient = new WeatherClient();
    private Future<?> weatherTask;
    private WeatherData weatherData;
    private volatile boolean weatherBusy;
    private boolean weatherFailed, resumed, preview, showingFace, ambientGesture;
    private int weatherGeneration, previewIndex;
    private long previewEnds, lastWeatherFetch;
    private AlertDialog optionsDialog;
    private GestureDetector gestures;
    private static final FaceState[] PREVIEW = {FaceState.LISTENING, FaceState.THINKING,
        FaceState.SPEAKING, FaceState.READY};

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) { stopPreview(); refreshServiceState(); }
    };
    final Runnable tick = new Runnable() {
        public void run() {
            Date now = new Date(); clock.setText(clockFormat.format(now)); date.setText(dateFormat.format(now));
            if (VoiceService.active && VoiceService.expiresAt > 0) {
                long remaining = Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000);
                wakeHint.setText((VoiceService.wakeListening ? "Activación por voz en prueba" : "Prueba manual / en pausa")
                    + (VoiceService.conversationTestActive ? " · LFM sin acciones" : VoiceService.wakeTestActive ? " · sin ejecutar órdenes" : "")
                    + " · quedan " + remaining + " s · doble toque para opciones");
            } else wakeHint.setText("Activación por voz apagada · doble toque para opciones");
            updateMicrophoneStop();
            renderWeather();
            if (policy.optionsVisible()) optionsCountdown.setText("Se ocultan en "
                + Math.max(0, (policy.optionsDeadline() - SystemClock.uptimeMillis() + 999) / 1000) + " s sin interacción");
            handler.postDelayed(this, 1000);
        }
    };
    final Runnable optionsExpiry = () -> expireOptionsAt(SystemClock.uptimeMillis());
    final Runnable weatherRefresh = new Runnable() {
        public void run() {
            if (!resumed || getIntent().getBooleanExtra("offline_preview", false)) return;
            if (!weatherBusy && (weatherData == null || System.currentTimeMillis() - lastWeatherFetch >= 15 * 60000L
                    || System.currentTimeMillis() < lastWeatherFetch)) fetchWeather();
            handler.postDelayed(this, weatherBusy ? 30000 : 15 * 60000L);
        }
    };
    final Runnable previewStep = new Runnable() {
        public void run() {
            if (!preview) return;
            if (VoiceService.active || RingService.ringing || SystemClock.uptimeMillis() >= previewEnds) {
                stopPreview(); refreshServiceState(); return;
            }
            FaceState value = PREVIEW[previewIndex++ % PREVIEW.length];
            present(value, "Vista previa · " + value.label + " (simulado)");
            handler.postDelayed(this, 2600);
        }
    };

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        for (SimpleDateFormat format : new SimpleDateFormat[]{clockFormat, dateFormat, weatherTimeFormat})
            format.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
        ensureCredential(); readWeatherCache();
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(10, 17, 33), Color.rgb(5, 8, 19)}));
        buildIdle(); buildFace();
        border = new ProcessingBorderView(this);
        border.setColor(getSharedPreferences("ui", MODE_PRIVATE).getInt("loader_color", Color.rgb(191, 163, 255)));
        root.addView(border, new FrameLayout.LayoutParams(-1, -1));
        previewBadge = new TextView(this); previewBadge.setText("VISTA PREVIA · SIN AUDIO NI ACCIONES");
        previewBadge.setTextSize(12); previewBadge.setTextColor(Color.rgb(247, 193, 119));
        previewBadge.setGravity(Gravity.CENTER); previewBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(-1, dp(36), Gravity.TOP);
        badge.topMargin = dp(10); root.addView(previewBadge, badge);
        microphoneStop = new Button(this); microphoneStop.setAllCaps(false); microphoneStop.setTextSize(13);
        microphoneStop.setVisibility(View.GONE); microphoneStop.setOnClickListener(value -> stopMicrophone());
        FrameLayout.LayoutParams stop = new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.END);
        stop.topMargin = dp(8); stop.rightMargin = dp(12); root.addView(microphoneStop, stop);
        buildOptions();
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            public boolean onDown(android.view.MotionEvent event) { return true; }
            public boolean onDoubleTap(android.view.MotionEvent event) { showOptions(); return true; }
        });
        root.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Mostrar opciones"));
            }
            public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { showOptions(); return true; }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
        setContentView(root); refreshServiceState(); immersive();
    }
    private void buildIdle() {
        idleLayer = new LinearLayout(this); idleLayer.setOrientation(LinearLayout.VERTICAL);
        idleLayer.setGravity(Gravity.CENTER); idleLayer.setPadding(dp(48), dp(22), dp(48), dp(22));
        date = label(idleLayer, "", 19, Color.rgb(151, 166, 195));
        clock = label(idleLayer, "", 120, Color.rgb(236, 241, 255));
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL)); clock.setIncludeFontPadding(false);
        clock.setSingleLine(true); clock.setAutoSizeTextTypeUniformWithConfiguration(60, 180, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        clock.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        weather = label(idleLayer, "Bogotá · clima sin datos", 25, Color.rgb(210, 224, 247));
        weatherDetail = label(idleLayer, "Sin temperatura inventada · fuente: Open-Meteo", 12, Color.rgb(151, 166, 195));
        TextView cue = label(idleLayer, "Di " + AssistantConfig.NAME, 24, Color.rgb(191, 183, 255));
        cue.setPadding(0, dp(13), 0, dp(3));
        wakeHint = label(idleLayer, "Activación por voz apagada · doble toque para opciones", 12, Color.rgb(151, 166, 195));
        root.addView(idleLayer, new FrameLayout.LayoutParams(-1, -1));
    }
    private void buildFace() {
        faceLayer = new FrameLayout(this); face = new FaceView(this);
        faceLayer.addView(face, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout caption = new LinearLayout(this); caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(42), 0, dp(42), dp(20));
        state = label(caption, "", 18, Color.rgb(221, 232, 251)); state.setMaxLines(2);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        transcript = label(caption, "", 14, Color.rgb(154, 180, 202)); transcript.setMaxLines(2);
        faceLayer.addView(caption, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        faceLayer.setVisibility(View.GONE); root.addView(faceLayer, new FrameLayout.LayoutParams(-1, -1));
    }
    private void buildOptions() {
        optionsLayer = new FrameLayout(this); optionsLayer.setBackgroundColor(0xcc050813); optionsLayer.setClickable(true);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(22), dp(15), dp(22), dp(15));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(15, 24, 42)); background.setCornerRadius(dp(22)); panel.setBackground(background);
        label(panel, "Andrómeda · opciones", 23, Color.WHITE);
        optionsCountdown = label(panel, "Se ocultan en 50 s sin interacción", 12, Color.rgb(151, 166, 195));
        LinearLayout first = new LinearLayout(this), second = new LinearLayout(this);
        button(first, "Vista previa", () -> { closeOptions(); togglePreview(); });
        button(first, "Color del brillo", this::chooseColor);
        button(first, "Temporizadores", this::timerOptions);
        button(second, "Micrófono", this::voiceDiagnostics);
        button(second, "Silenciar", this::stopMicrophone);
        button(second, "Volver", this::closeOptions);
        panel.addView(first); panel.addView(second);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        params.setMargins(dp(42), dp(16), dp(42), dp(16)); optionsLayer.addView(panel, params);
        optionsLayer.setVisibility(View.GONE); root.addView(optionsLayer, new FrameLayout.LayoutParams(-1, -1));
    }
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        // An active mic always has a one-tap stop, even with ambient gestures.
        if (microphoneStop != null && microphoneStop.getVisibility() == View.VISIBLE && !policy.optionsVisible()) {
            float x = event.getX(), y = event.getY();
            if (x >= microphoneStop.getLeft() && x <= microphoneStop.getRight()
                    && y >= microphoneStop.getTop() && y <= microphoneStop.getBottom())
                return super.dispatchTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            ambientGesture = !policy.optionsVisible();
            if (policy.optionsVisible()) noteOptionsInteraction();
        }
        if (ambientGesture && gestures != null) {
            gestures.onTouchEvent(event);
            if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) ambientGesture = false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    void showOptions() {
        policy.showOptions(SystemClock.uptimeMillis()); optionsLayer.setVisibility(View.VISIBLE);
        optionsCountdown.setText("Se ocultan en 50 s sin interacción"); scheduleOptionsExpiry();
    }
    private void noteOptionsInteraction() { policy.interact(SystemClock.uptimeMillis()); scheduleOptionsExpiry(); }
    private void scheduleOptionsExpiry() {
        handler.removeCallbacks(optionsExpiry);
        if (policy.optionsVisible()) handler.postAtTime(optionsExpiry, policy.optionsDeadline());
    }
    void closeOptions() {
        policy.hideOptions(); handler.removeCallbacks(optionsExpiry); optionsLayer.setVisibility(View.GONE);
        if (optionsDialog != null) { AlertDialog dialog = optionsDialog; optionsDialog = null; dialog.dismiss(); }
    }
    void expireOptionsAt(long now) { if (policy.expireOptions(now)) closeOptions(); else scheduleOptionsExpiry(); }
    private void showDialog(AlertDialog dialog) {
        if (optionsDialog != null) optionsDialog.dismiss();
        optionsDialog = dialog;
        dialog.setOnDismissListener(value -> { if (optionsDialog == dialog) optionsDialog = null; });
        dialog.show();
        dialog.setOnKeyListener((value, keyCode, event) -> { noteOptionsInteraction(); return false; });
        if (dialog.getListView() != null) {
            AdapterView.OnItemClickListener selected = dialog.getListView().getOnItemClickListener();
            dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
                noteOptionsInteraction();
                if (selected != null) selected.onItemClick(parent, view, position, id);
            });
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) button.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) noteOptionsInteraction(); return false;
            });
        }
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) noteOptionsInteraction(); return false;
        });
        noteOptionsInteraction();
    }
    private void chooseColor() {
        showDialog(new AlertDialog.Builder(this).setTitle("Color del brillo al procesar")
            .setItems(new String[]{"Lavanda", "Cian", "Verde", "Ámbar", "Rosa", "Personalizado #RRGGBB…"}, (dialog, index) -> {
                if (index < 5) saveLoaderColor(new String[]{"#BFA3FF", "#6FE7F8", "#7CEBCD", "#F7C177", "#FF8FD0"}[index]);
                else {
                    EditText input = new EditText(this); input.setSingleLine(true);
                    input.setText(String.format(Locale.ROOT, "#%06X", border.getColor() & 0xffffff));
                    input.addTextChangedListener(new android.text.TextWatcher() {
                        public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                        public void onTextChanged(CharSequence text, int start, int before, int count) { noteOptionsInteraction(); }
                        public void afterTextChanged(android.text.Editable text) {}
                    });
                    AlertDialog custom = new AlertDialog.Builder(this).setTitle("Color hexadecimal").setView(input)
                        .setNegativeButton("Volver", null).setPositiveButton("Guardar", null).create();
                    showDialog(custom); custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                        noteOptionsInteraction();
                        try { saveLoaderColor(input.getText().toString().trim()); custom.dismiss(); }
                        catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
                    });
                }
            }).setNegativeButton("Volver", null).create());
    }
    private void saveLoaderColor(String text) {
        int color = UiPolicy.parseColor(text); border.setColor(color);
        getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("loader_color", color).apply(); noteOptionsInteraction();
    }
    private void timerOptions() {
        showDialog(new AlertDialog.Builder(this).setTitle("Temporizadores")
            .setItems(new String[]{"Ver temporizadores", "Cancelar todos…", "Detener sonido"}, (dialog, index) -> {
                if (index == 0) showDialog(new AlertDialog.Builder(this).setTitle("Temporizadores").setMessage(TimerStore.describe(this)).setPositiveButton("Cerrar", null).create());
                else if (index == 1) showDialog(new AlertDialog.Builder(this).setTitle("Cancelar todos los temporizadores")
                    .setMessage(TimerStore.describe(this) + " ¿Quieres cancelarlos todos?").setNegativeButton("Volver", null)
                    .setPositiveButton("Cancelar todos", (confirmation, which) -> {
                        String message;
                        try { message = TimerStore.cancel(this, true); }
                        catch (Exception error) { message = "No se pudo confirmar la cancelación. Revisa el estado."; }
                        showDialog(new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Cerrar", null).create());
                    }).create());
                else stopService(new Intent(this, RingService.class));
            }).setNegativeButton("Volver", null).create());
    }
    void voiceDiagnostics() {
        if (VoiceService.active) {
            showDialog(new AlertDialog.Builder(this).setTitle("Sesión de prueba activa")
                .setItems(new String[]{"Hablar ahora", "Detener micrófono"}, (dialog, which) -> {
                    closeOptions();
                    if (which == 0) startService(new Intent(this, VoiceService.class).setAction("PUSH"));
                    else stopService(new Intent(this, VoiceService.class));
                }).setNegativeButton("Volver", null).create()); return;
        }
        showDialog(new AlertDialog.Builder(this).setTitle("Micrófono · pruebas limitadas")
            .setItems(new String[]{"Conversación LFM · sin acciones", "Solo activación y transcripción (0.3)"},
                (dialog, which) -> explainVoiceTrial(which == 0)).setNegativeButton("Volver", null).create());
    }
    private void explainVoiceTrial(boolean conversation) {
        showDialog(new AlertDialog.Builder(this).setTitle(conversation ? "Conversación local · experimental" : "Micrófono · prueba de activación")
            .setMessage(conversation
                ? "Di Andrómeda, espera la cara y habla brevemente. LFM responde con voz española offline y memoria corta. Vuelve a decir el nombre para otro turno. Puede equivocarse. No ejecuta órdenes ni consulta Internet.\n\nDurante el cálculo y la respuesta pausa el micrófono. Descarta respuestas tardías; 30 s todavía no están certificados con voz humana. El backend debe estar preparado. Silenciar o salir cierra la sesión."
                : "Di Andrómeda: aparece la cara y escucha durante 8 segundos; después vuelve al reloj. Muestra lo reconocido, sin ejecutar órdenes, hablar ni cargar la IA.\n\nEl detector local debe estar preparado. La sesión termina sola; puedes silenciar o salir de la app.")
            .setNegativeButton("Volver", null).setPositiveButton("Elegir prueba limitada", (dialog, which) ->
                showDialog(new AlertDialog.Builder(this).setTitle("Micrófono local · duración máxima")
                    .setItems(new String[]{"90 segundos", "5 minutos"}, (selection, index) -> {
                        closeOptions();
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                        else { stopPreview(); startForegroundService(new Intent(this, VoiceService.class).setAction("START")
                            .putExtra("wake_only", !conversation).putExtra("conversation_only", conversation)
                            .putExtra("seconds", index == 1 ? 300 : 90)); }
                    }).setNegativeButton("Volver", null).create())).create());
    }
    void present(FaceState value, String text) {
        boolean nextFace = UiPolicy.showsFace(value);
        face.setState(value); state.setText(text);
        if (nextFace != showingFace) {
            showingFace = nextFace; idleLayer.animate().cancel(); idleLayer.animate().withEndAction(null);
            if (nextFace) {
                faceLayer.setVisibility(View.VISIBLE); face.enterRunning();
                if (resumed && !reducedMotion()) idleLayer.animate().alpha(0).setDuration(180).withEndAction(() -> {
                    if (showingFace) idleLayer.setVisibility(View.GONE);
                }).start();
                else idleLayer.setVisibility(View.GONE);
            } else {
                faceLayer.setVisibility(View.GONE); idleLayer.setVisibility(View.VISIBLE); idleLayer.setAlpha(1);
            }
        }
        border.setProcessing(value == FaceState.THINKING);
    }
    void refreshServiceState() {
        present(VoiceService.uiState, VoiceService.lastStatus); transcript.setText(VoiceService.lastTranscript);
        transcript.setVisibility(VoiceService.lastTranscript.isEmpty() ? View.GONE : View.VISIBLE);
        updateMicrophoneStop();
    }
    private void updateMicrophoneStop() {
        if (microphoneStop == null) return;
        microphoneStop.setVisibility(VoiceService.active ? View.VISIBLE : View.GONE);
        if (VoiceService.active) microphoneStop.setText("Silenciar · "
            + Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
    }
    private void stopMicrophone() {
        closeOptions(); stopPreview(); stopService(new Intent(this, VoiceService.class));
        present(FaceState.MUTED, "Micrófono apagado · los temporizadores se conservan");
    }
    void togglePreview() {
        if (preview) { stopPreview(); refreshServiceState(); return; }
        if (VoiceService.active || RingService.ringing) return;
        preview = true; previewIndex = 0; previewEnds = SystemClock.uptimeMillis() + 30000;
        previewBadge.setVisibility(View.VISIBLE); transcript.setVisibility(View.GONE); previewStep.run();
    }
    void stopPreview() { preview = false; handler.removeCallbacks(previewStep); previewBadge.setVisibility(View.GONE); }
    boolean isPreviewing() { return preview; }

    private void readWeatherCache() {
        try {
            String raw = getSharedPreferences("weather", MODE_PRIVATE).getString("bogota", "");
            if (raw.length() > 1024) return;
            JSONObject data = new JSONObject(raw);
            weatherData = new WeatherData(data.getDouble("celsius"), data.getInt("code"), data.getLong("time"));
            lastWeatherFetch = data.getLong("fetched");
        } catch (Exception ignored) { weatherData = null; }
    }
    private void fetchWeather() {
        weatherBusy = true; final int generation = ++weatherGeneration;
        weatherTask = weatherWorker.submit(() -> {
            WeatherData value = null;
            try { value = weatherClient.fetch(); } catch (Exception ignored) { /* No conversation/credential logs. */ }
            finally { weatherBusy = false; }
            final WeatherData received = value;
            handler.post(() -> {
                if (!resumed || weatherGeneration != generation) return;
                weatherFailed = received == null;
                if (received != null) {
                    weatherData = received; lastWeatherFetch = System.currentTimeMillis();
                    try {
                        String cache = new JSONObject().put("celsius", received.celsius).put("code", received.code)
                            .put("time", received.time).put("fetched", lastWeatherFetch).toString();
                        getSharedPreferences("weather", MODE_PRIVATE).edit().putString("bogota", cache).apply();
                    } catch (Exception ignored) {}
                }
                renderWeather();
            });
        });
    }
    private void renderWeather() {
        if (weatherData != null && weatherData.isFresh(System.currentTimeMillis())) {
            weather.setText(String.format(new Locale("es", "CO"), "Bogotá · %.0f °C · %s", weatherData.celsius, weatherData.condition()));
            weatherDetail.setText("Estimación " + weatherTimeFormat.format(new Date(weatherData.time)) + " · Open-Meteo"
                + (weatherFailed ? " · sin actualizar" : ""));
        } else {
            weather.setText("Bogotá · " + (weatherBusy ? "consultando clima…" : "clima sin datos recientes"));
            weatherDetail.setText(weatherFailed ? "No se pudo actualizar · Open-Meteo" : "Fuente: Open-Meteo · requiere Internet");
        }
    }
    private void ensureCredential() {
        try {
            File key = new File(getFilesDir(), "voice-token");
            if (!key.exists()) {
                byte[] data = new byte[32]; new SecureRandom().nextBytes(data);
                try (FileOutputStream out = new FileOutputStream(key)) { out.write(Base64.encode(data, Base64.NO_WRAP)); }
            }
        } catch (IOException error) { throw new RuntimeException("No se pudo crear credencial local", error); }
    }
    private boolean reducedMotion() { return android.provider.Settings.Global.getFloat(getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0; }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    TextView label(LinearLayout parent, String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(color); view.setTextSize(size); view.setGravity(Gravity.CENTER);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(14); view.setTextColor(Color.rgb(224, 234, 255));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(22, 34, 55)); background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(50, 66, 91));
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(63, 83, 116)), background, null));
        view.setOnClickListener(value -> { noteOptionsInteraction(); action.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.setMargins(dp(4), dp(6), dp(4), dp(2)); parent.addView(view, params);
    }
    public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive(); }
    public void onBackPressed() {
        if (policy.optionsVisible()) closeOptions();
        else if (preview) { stopPreview(); refreshServiceState(); }
        else super.onBackPressed();
    }
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) { showOptions(); return true; }
        return super.onKeyUp(keyCode, event);
    }
    public void onResume() {
        super.onResume(); resumed = true;
        IntentFilter filter = new IntentFilter(VoiceService.STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, filter);
        refreshServiceState(); face.setForeground(true); border.setForeground(true); handler.post(tick); handler.post(weatherRefresh);
    }
    public void onPause() {
        resumed = false; weatherGeneration++;
        // One bounded in-flight public request may finish; its generation is now
        // invalid. Do not block the UI thread on HttpURLConnection.disconnect().
        closeOptions(); stopPreview(); refreshServiceState(); face.setForeground(false); border.setForeground(false);
        handler.removeCallbacks(tick); handler.removeCallbacks(weatherRefresh); unregisterReceiver(receiver); super.onPause();
    }
    public void onStop() { stopService(new Intent(this, VoiceService.class)); super.onStop(); }
    public void onDestroy() {
        if (weatherTask != null) weatherTask.cancel(true);
        weatherWorker.shutdownNow(); super.onDestroy();
    }
}
