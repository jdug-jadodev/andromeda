package local.doja.voice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
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
    static volatile boolean visibleToUser;
    FaceView face;
    ProcessingBorderView border;
    FrameLayout root, faceLayer, optionsLayer;
    LinearLayout idleLayer;
    TextView clock, date, weather, weatherDetail, wakeHint, state, transcript, previewBadge, optionsCountdown;
    Button microphoneStop, assistantStart, assistantAction;
    final UiPolicy policy = new UiPolicy();
    private final ListeningSleepPolicy sleep = new ListeningSleepPolicy();
    private boolean sleepWatching, closingFace;
    private android.animation.ValueAnimator clockDrop;
    private final Runnable closingDone = new Runnable() { public void run() { finishClosing(); } };
    /** While listening, re-evaluates the face a few times per second so drowsy/asleep start on time. */
    private final Runnable sleepWatch = new Runnable() {
        public void run() {
            sleepWatching = false;
            if (resumed && VoiceService.active && VoiceService.uiState == FaceState.LISTENING) refreshServiceState();
        }
    };
    final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", new Locale("es", "CO"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CO"));
    private final SimpleDateFormat weatherTimeFormat = new SimpleDateFormat("HH:mm", new Locale("es", "CO"));
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService agendaWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService engineWorker = Executors.newSingleThreadExecutor();
    private final WeatherClient weatherClient = new WeatherClient();
    private LightCommandDispatcher lightDispatcher;
    private Future<?> weatherTask;
    private WeatherData weatherData;
    private volatile boolean weatherBusy;
    private boolean weatherFailed, resumed, preview, showingFace, ambientGesture;
    private final java.util.EnumMap<LightTarget,Integer> lightBrightness = new java.util.EnumMap<>(LightTarget.class);
    private boolean pendingVoiceActivation;
    private int weatherGeneration, previewIndex;
    private long previewEnds, lastWeatherFetch;
    private AlertDialog optionsDialog;
    private GestureDetector gestures;
    private static final FaceState[] PREVIEW = {FaceState.LISTENING, FaceState.THINKING,
        FaceState.SPEAKING, FaceState.READY};

    final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
            stopPreview();
            String words = intent == null ? null : intent.getStringExtra("transcript");
            if (words != null && !words.isEmpty() && weatherData != null
                    && weatherData.isFresh(System.currentTimeMillis())) {
                FaceReaction weatherReaction = FaceReactionPolicy.forWeather(words, weatherData);
                if (weatherReaction != FaceReaction.NONE) showReaction(weatherReaction);
            }
            refreshServiceState();
        }
    };
    final Runnable tick = new Runnable() {
        public void run() {
            Date now = new Date(); clock.setText(clockFormat.format(now)); date.setText(dateFormat.format(now));
            long voiceAt = VoiceService.brightnessActivityAt;
            long elapsed = SystemClock.elapsedRealtime();
            if (voiceAt > lastVoiceBrightnessAt && elapsed - voiceAt < 5000L) {
                lastVoiceBrightnessAt = voiceAt;
                noteVoiceInteraction();
            }
            if (VoiceService.active && (VoiceService.uiState == FaceState.SPEAKING
                    || VoiceService.uiState == FaceState.THINKING))
                noteVoiceInteraction();
            long faceAt = VoiceService.faceReactionAt;
            if (faceAt > lastFaceReactionAt) {
                lastFaceReactionAt = faceAt;
                if (face != null && VoiceService.faceReaction != FaceReaction.NONE)
                    face.setReaction(VoiceService.faceReaction, 5000);
            }
            if (CaptureProbeService.active) {
                refreshServiceState();
                wakeHint.setText(CaptureProbeService.status + " · quedan "
                    + Math.max(0, (CaptureProbeService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
            } else if (SilenceListener.recording()) wakeHint.setText("MICRÓFONO ACTIVO · silencio o Andrómeda, silencio · no abre otra charla");
            else if (VoiceService.active && VoiceService.continuousVoiceActive) wakeHint.setText(VoiceService.microphoneRecording
                ? "Modo continuo · escucha local activa · di Andrómeda" : "Modo continuo · preparando o micrófono en pausa");
            else if (VoiceService.active && VoiceService.expiresAt > 0) {
                long remaining = Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000);
                wakeHint.setText("Andrómeda activa · quedan " + remaining + " s · habla para pedir lo que necesites");
            } else wakeHint.setText(VoiceService.uiState==FaceState.ERROR?VoiceService.lastStatus:"Voz apagada · toca Activar Andrómeda");
            updateMicrophoneStop();
            renderWeather();
            if (policy.optionsVisible()) optionsCountdown.setText("Se ocultan en "
                + Math.max(0, (policy.optionsDeadline() - SystemClock.uptimeMillis() + 999) / 1000) + " s sin interacción");
            handler.postDelayed(this, 1000);
        }
    };
    final Runnable optionsExpiry = () -> expireOptionsAt(SystemClock.uptimeMillis());
    final Runnable screenIdle = new Runnable() {
        public void run() {
            if (!resumed) return;
            applyScreenBrightness(true);
            handler.postDelayed(this, screenIdleDelay);
        }
    };
    private long lastScreenInteraction, lastVoiceBrightnessAt;
    private long lastFaceReactionAt;
    private long screenIdleDelay = ScreenBrightnessPolicy.IDLE_MS;
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
            if (VoiceService.active || RingService.ringing || CaptureProbeService.active || SystemClock.uptimeMillis() >= previewEnds) {
                stopPreview(); refreshServiceState(); return;
            }
            FaceState value = PREVIEW[previewIndex++ % PREVIEW.length];
            present(value, "Vista previa · " + value.label + " (simulado)");
            handler.postDelayed(this, 2600);
        }
    };

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        lightDispatcher = new LightCommandDispatcher(this);
        try { LightScheduleScheduler.schedule(this); }
        catch (Exception ignored) { /* The boot receiver and the next UI save repair alarms. */ }
        try { RestModeScheduler.schedule(this); }
        catch (Exception ignored) { /* The boot receiver and the next UI save repair alarms. */ }
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
        wakeHint = label(idleLayer, "Voz apagada · toca Activar Andrómeda", 12, Color.rgb(151, 166, 195));
        assistantStart = new Button(this); assistantStart.setText(UnifiedVoicePolicy.START); assistantStart.setAllCaps(false);
        assistantStart.setOnClickListener(value -> assistantVoice());
        idleLayer.addView(assistantStart, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(idleLayer, new FrameLayout.LayoutParams(-1, -1));
    }
    private void buildFace() {
        faceLayer = new FrameLayout(this); face = new FaceView(this);
        faceLayer.addView(face, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout caption = new LinearLayout(this); caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(42), 0, dp(42), dp(20));
        // The status line is hidden on screen but kept in the tree so TalkBack still announces it.
        state = label(caption, "", 18, Color.rgb(221, 232, 251)); state.setMaxLines(2);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        state.setAlpha(0);
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
        LinearLayout first = new LinearLayout(this), second = new LinearLayout(this), third = new LinearLayout(this), fourth = new LinearLayout(this), fifth = new LinearLayout(this);
        button(first, "Color del brillo", this::chooseColor);
        button(first, "Contexto", this::contextOptions);
        button(first, "Fuentes web", () -> showDialog(new AlertDialog.Builder(this).setTitle("Última búsqueda · fuentes en RAM")
            .setMessage(WebSources.describe(SystemClock.elapsedRealtime())).setPositiveButton("Cerrar", null).create()));
        button(second, "Temporizadores", this::timerOptions);
        button(second, "Alarmas", () -> agendaOptions(AgendaEntry.Kind.ALARM));
        button(second, "Recordatorios", () -> agendaOptions(AgendaEntry.Kind.REMINDER));
        assistantAction = button(third, UnifiedVoicePolicy.START, this::assistantVoice);
        button(third, "Volver", this::closeOptions);
        button(fourth, "Luces", this::lightOptions);
        button(fourth, "Horario de luces", () -> lightScheduleOptions(LightTarget.BOTH));
        button(fifth, "Modo descanso", this::restModeOptions);
        panel.addView(first); panel.addView(second); panel.addView(third); panel.addView(fourth); panel.addView(fifth);
        ScrollView optionsScroll = new ScrollView(this);
        optionsScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        params.setMargins(dp(42), dp(16), dp(42), dp(16)); optionsLayer.addView(optionsScroll, params);
        optionsLayer.setVisibility(View.GONE); root.addView(optionsLayer, new FrameLayout.LayoutParams(-1, -1));
    }
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) noteScreenInteraction();
        if (!policy.optionsVisible() && assistantStart != null && assistantStart.isShown()) {
            int[] location = new int[2]; assistantStart.getLocationOnScreen(location);
            if (event.getRawX() >= location[0] && event.getRawX() <= location[0] + assistantStart.getWidth()
                    && event.getRawY() >= location[1] && event.getRawY() <= location[1] + assistantStart.getHeight())
                return super.dispatchTouchEvent(event);
        }
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
    private void lightOptions() {
        showDialog(new AlertDialog.Builder(this).setTitle("Luces · elige objetivo")
            .setItems(new String[]{"Ambas luces", "Luz mueble", "Luz del televisor"}, (dialog, index) ->
                lightCommandOptions(index == 0 ? LightTarget.BOTH : index == 1 ? LightTarget.LOTUS : LightTarget.MR_STAR))
            .setNegativeButton("Volver", null).create());
    }
    private void lightCommandOptions(LightTarget target) {
        if (!lightBrightness.containsKey(target)) lightBrightness.put(target, 25);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        labels.add("Encender"); actions.add(() -> simulateLightCommand(LightCommand.power(target,true)));
        labels.add("Apagar"); actions.add(() -> simulateLightCommand(LightCommand.power(target,false)));
        if (target == LightTarget.LOTUS) {
            addLightColor(labels, actions, target, "Rosa", "#FF8FD0");
            addLightColor(labels, actions, target, "Blanco", "#FFFFFF");
            addLightColor(labels, actions, target, "Azul", "#0000FF");
            addLightColor(labels, actions, target, "Ámbar", "#FFBF00");
        } else {
            addLightColor(labels, actions, target, "Rojo", "#FF0000");
            addLightColor(labels, actions, target, "Verde", "#00FF00");
            addLightColor(labels, actions, target, "Azul", "#0000FF");
            addLightColor(labels, actions, target, "Blanco visual · capturado", "#FFFFFF");
        }
        labels.add("Brillo · "+lightBrightness.get(target)+"%"); actions.add(() -> lightBrightnessOptions(target));
        labels.add(LightScheduleStore.forTarget(this,target)==null?"Programar horario de luces…":"Cambiar horario de luces…");
        actions.add(() -> lightScheduleOptions(target));
        labels.add("Usar hexadecimal…"); actions.add(() -> lightHexDialog(target,false));
        labels.add("Guardar hexadecimal…"); actions.add(() -> lightHexDialog(target,true));
        for (java.util.Map.Entry<String,LightColor> entry:LightPresetStore.list(this).entrySet()) {
            final LightColor color=entry.getValue(); final String name=entry.getKey();
            labels.add("Guardado · "+name+" "+color.hex()); actions.add(() -> simulateLightCommand(LightCommand.color(target,color)));
        }
        showDialog(new AlertDialog.Builder(this).setTitle("Orden de luces · "+lightTargetLabel(target))
            .setItems(labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run())
            .setNegativeButton("Cambiar objetivo", (dialog, which) -> lightOptions()).create());
    }
    private void lightScheduleOptions(LightTarget target) {
        LightSchedule schedule = LightScheduleStore.forTarget(this, target);
        String title = "Horario · " + lightTargetLabel(target);
        if (schedule == null) {
            showDialog(new AlertDialog.Builder(this).setTitle(title)
                .setMessage("Sin horario guardado.\n\nPredeterminado: 17:30 encender y 22:00 apagar, todos los días.")
                .setPositiveButton("Crear 17:30–22:00", (dialog, which) -> {
                    try {
                        LightSchedule created = LightScheduleStore.ensureDefault(this, target);
                        LightScheduleScheduler.schedule(this);
                        lightScheduleOptions(created.target);
                    } catch (Exception failure) {
                        showDialog(new AlertDialog.Builder(this).setTitle("Horario")
                            .setMessage("No se pudo guardar el horario en este teléfono.")
                            .setPositiveButton("Cerrar", null).create());
                    }
                }).setNegativeButton("Volver", (dialog, which) -> lightCommandOptions(target)).create());
            return;
        }
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        labels.add("Cambiar encendido (" + LightSchedule.format(schedule.onMinute) + ")");
        actions.add(() -> lightScheduleTime(schedule, true));
        labels.add("Cambiar apagado (" + LightSchedule.format(schedule.offMinute) + ")");
        actions.add(() -> lightScheduleTime(schedule, false));
        labels.add("Días · " + LightSchedule.daysLabel(schedule.days));
        actions.add(() -> lightScheduleDays(schedule));
        labels.add(schedule.enabled ? "Desactivar horario" : "Activar horario");
        actions.add(() -> saveLightSchedule(schedule.withEnabled(!schedule.enabled)));
        labels.add("Borrar horario");
        actions.add(() -> lightScheduleDelete(schedule));
        String receipt = LightScheduleReceiptStore.describe(this, schedule);
        TextView header = new TextView(this);
        header.setText(title + "\n"
            + (schedule.enabled ? "Activo" : "Desactivado") + " · "
            + LightSchedule.daysLabel(schedule.days) + "\n"
            + LightSchedule.format(schedule.onMinute) + " encender · "
            + LightSchedule.format(schedule.offMinute) + " apagar"
            + (receipt.isEmpty() ? "" : "\n" + receipt));
        header.setTextSize(18);
        header.setTextColor(Color.WHITE);
        header.setPadding(dp(20), dp(12), dp(20), dp(8));
        header.setLineSpacing(0, 1.12f);
        // A message would replace the item list in this Android theme; use a
        // custom title so the editable actions remain visible.
        showDialog(new AlertDialog.Builder(this).setCustomTitle(header)
            .setItems(labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run())
            .setNegativeButton("Volver", (dialog, which) -> lightCommandOptions(target)).create());
    }

    private void lightScheduleTime(LightSchedule schedule, boolean on) {
        noteOptionsInteraction();
        int minute = on ? schedule.onMinute : schedule.offMinute;
        TimePickerDialog picker = new TimePickerDialog(this, (view, hour, selectedMinute) -> {
            try {
                int value = hour * 60 + selectedMinute;
                saveLightSchedule(on ? schedule.withOn(value) : schedule.withOff(value));
            } catch (IllegalArgumentException error) {
                showDialog(new AlertDialog.Builder(this).setTitle("Horario")
                    .setMessage("La hora de encendido y la de apagado no pueden coincidir.")
                    .setPositiveButton("Cerrar", (dialog, which) -> lightScheduleOptions(schedule.target)).create());
            }
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(this));
        picker.setOnCancelListener(dialog -> lightScheduleOptions(schedule.target));
        picker.show();
    }

    private void lightScheduleDays(LightSchedule schedule) {
        String[] values = {"Todos los días", "Lunes a viernes", "Fin de semana"};
        int[] masks = {LightSchedule.ALL_DAYS, LightSchedule.WEEKDAYS, LightSchedule.WEEKEND};
        showDialog(new AlertDialog.Builder(this).setTitle("Días del horario")
            .setItems(values, (dialog, index) -> saveLightSchedule(schedule.withDays(masks[index])))
            .setNegativeButton("Volver", (dialog, which) -> lightScheduleOptions(schedule.target)).create());
    }

    private void saveLightSchedule(LightSchedule schedule) {
        try {
            LightScheduleStore.save(this, schedule);
            LightScheduleScheduler.schedule(this);
            lightScheduleOptions(schedule.target);
        } catch (Exception failure) {
            showDialog(new AlertDialog.Builder(this).setTitle("Horario")
                .setMessage("No se pudo guardar el horario. No se envió ninguna orden de luces.")
                .setPositiveButton("Cerrar", null).create());
        }
    }

    private void lightScheduleDelete(LightSchedule schedule) {
        showDialog(new AlertDialog.Builder(this).setTitle("Borrar horario")
            .setMessage("Se borrará " + LightSchedule.format(schedule.onMinute) + "–"
                + LightSchedule.format(schedule.offMinute) + " de " + lightTargetLabel(schedule.target)
                + ".\n\nNo se enviará ninguna orden ahora.")
            .setNegativeButton("Volver", (dialog, which) -> lightScheduleOptions(schedule.target))
            .setPositiveButton("Borrar", (dialog, which) -> {
                LightScheduleScheduler.cancel(this, schedule.id);
                try { LightScheduleStore.delete(this, schedule.id); }
                catch (Exception ignored) { /* The UI verifies by reopening the store. */ }
                lightScheduleOptions(schedule.target);
            }).create());
    }

    private void restModeOptions() {
        RestModeSchedule schedule = RestModeStore.load(this);
        if (schedule == null) {
            showDialog(new AlertDialog.Builder(this).setTitle("Modo descanso")
                .setMessage("Sin horario guardado.\n\n23:00: brillo mínimo y volumen 50%.\n06:00: brillo 70% y volumen 100%.\nTodos los días.")
                .setPositiveButton("Activar 23:00/06:00", (dialog, which) -> {
                    try {
                        RestModeStore.save(this, RestModeSchedule.defaultSchedule());
                        RestModeScheduler.schedule(this);
                        restModeOptions();
                    } catch (Exception failure) {
                        showDialog(new AlertDialog.Builder(this).setTitle("Modo descanso")
                            .setMessage("No se pudo guardar el modo descanso en este teléfono.")
                            .setPositiveButton("Cerrar", null).create());
                    }
                }).setNegativeButton("Volver", null).create());
            return;
        }
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        labels.add("Cambiar hora de descanso (" + LightSchedule.format(schedule.nightMinute) + ")");
        actions.add(() -> restModeTime(schedule, true));
        labels.add("Cambiar hora de despertar (" + LightSchedule.format(schedule.morningMinute) + ")");
        actions.add(() -> restModeTime(schedule, false));
        labels.add(schedule.enabled ? "Desactivar modo descanso" : "Activar modo descanso");
        actions.add(() -> saveRestModeSchedule(schedule.withEnabled(!schedule.enabled)));
        if (!Settings.System.canWrite(this)) {
            labels.add("Permitir brillo (necesario)");
            actions.add(this::requestWriteSettings);
        }
        String receipt = RestModeReceiptStore.describe(this);
        TextView header = new TextView(this);
        header.setText("Modo descanso\n"
            + (schedule.enabled ? "Activo" : "Desactivado") + " · todos los días\n"
            + LightSchedule.format(schedule.nightMinute) + " descanso: brillo mínimo · volumen 50%\n"
            + LightSchedule.format(schedule.morningMinute) + " despertar: brillo 70% · volumen 100%"
            + (Settings.System.canWrite(this) ? "" : "\nFalta permiso de brillo; el volumen sí se aplicará.")
            + (receipt.isEmpty() ? "" : "\n" + receipt));
        header.setTextSize(18); header.setTextColor(Color.WHITE);
        header.setPadding(dp(20), dp(12), dp(20), dp(8)); header.setLineSpacing(0, 1.12f);
        showDialog(new AlertDialog.Builder(this).setCustomTitle(header)
            .setItems(labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run())
            .setNegativeButton("Volver", null).create());
    }

    private void restModeTime(RestModeSchedule schedule, boolean night) {
        noteOptionsInteraction();
        int minute = night ? schedule.nightMinute : schedule.morningMinute;
        TimePickerDialog picker = new TimePickerDialog(this, (view, hour, selectedMinute) -> {
            try {
                int value = hour * 60 + selectedMinute;
                saveRestModeSchedule(night ? schedule.withNight(value) : schedule.withMorning(value));
            } catch (IllegalArgumentException error) {
                showDialog(new AlertDialog.Builder(this).setTitle("Modo descanso")
                    .setMessage("La hora de descanso y la de despertar no pueden coincidir.")
                    .setPositiveButton("Cerrar", (dialog, which) -> restModeOptions()).create());
            }
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(this));
        picker.setOnCancelListener(dialog -> restModeOptions());
        picker.show();
    }

    private void saveRestModeSchedule(RestModeSchedule schedule) {
        try {
            RestModeStore.save(this, schedule);
            RestModeScheduler.schedule(this);
            restModeOptions();
        } catch (Exception failure) {
            showDialog(new AlertDialog.Builder(this).setTitle("Modo descanso")
                .setMessage("No se pudo guardar el modo descanso. No se cambió brillo ni volumen.")
                .setPositiveButton("Cerrar", null).create());
        }
    }

    private void requestWriteSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            showDialog(new AlertDialog.Builder(this).setTitle("Permiso de brillo")
                .setMessage("Abre Ajustes → Aplicaciones → Andrómeda → Permisos especiales → Modificar ajustes del sistema.")
                .setPositiveButton("Cerrar", null).create());
        }
    }

    private void addLightColor(java.util.ArrayList<String> labels,java.util.ArrayList<Runnable> actions,LightTarget target,String name,String hex) {
        labels.add(name+" "+hex); actions.add(() -> simulateLightCommand(LightCommand.color(target,LightColor.parseHex(hex))));
    }
    private void lightBrightnessOptions(LightTarget target) {
        if (target == LightTarget.MR_STAR || target == LightTarget.BOTH) {
            String[] captured={"100% · máximo","75%","50%","25%"};
            showDialog(new AlertDialog.Builder(this).setTitle("Brillo · "+lightTargetLabel(target)+" · elige un nivel")
                .setItems(captured,(dialog,index) -> chooseBrightness(target,new int[]{100,75,50,25}[index]))
                .setNegativeButton("Volver", (dialog, which) -> lightCommandOptions(target)).create());
            return;
        }
        String[] values={"100% · máximo","75%","50%","25%","0% · apagado","Personalizado…"};
        showDialog(new AlertDialog.Builder(this).setTitle("Brillo · "+lightTargetLabel(target))
            .setItems(values,(dialog,index) -> {
                if(index<5){chooseBrightness(target,new int[]{100,75,50,25,0}[index]);}
                else lightCustomBrightness(target);
            }).setNegativeButton("Volver",(dialog,which)->lightCommandOptions(target)).create());
    }
    private void lightCustomBrightness(LightTarget target) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("0 a 100"); input.setText(String.valueOf(lightBrightness.get(target)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Brillo personalizado")
            .setMessage("Escribe un porcentaje entero entre 0 y 100.").setView(input)
            .setNegativeButton("Volver",(d,w)->lightBrightnessOptions(target)).setPositiveButton("Usar",null).create();
        showDialog(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try{int value=Integer.parseInt(input.getText().toString().trim());if(value<0||value>100)throw new NumberFormatException();dialog.dismiss();chooseBrightness(target,value);}
            catch(NumberFormatException error){input.setError("Usa un entero de 0 a 100");}
        });
    }
    private void chooseBrightness(LightTarget target,int percent) {
        lightBrightness.put(target,percent);
        if (target == LightTarget.BOTH) {
            lightBrightness.put(LightTarget.LOTUS,percent);
            lightBrightness.put(LightTarget.MR_STAR,percent);
        }
        simulateLightCommand(LightCommand.brightness(target,percent));
    }
    private void lightHexDialog(LightTarget target,boolean save) {
        LinearLayout fields=new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(10),dp(4),dp(10),0);
        EditText hex=new EditText(this); hex.setSingleLine(true); hex.setHint("#F2A3F6"); hex.setText("#F2A3F6"); fields.addView(hex);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Nombre, por ejemplo atardecer");
        if(save)fields.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(save?"Guardar color personalizado":"Color hexadecimal")
             .setMessage(save?"Se guardará solo en este teléfono. El envío requiere una confirmación separada.":"Acepta #RRGGBB o RRGGBB; solo se enviará si el protocolo está validado.")
             .setView(fields).setNegativeButton("Volver", (d,w) -> lightCommandOptions(target)).setPositiveButton(save?"Guardar y revisar":"Revisar", null).create();
        showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                LightColor color=LightColor.parseHex(hex.getText().toString());
                if(save)LightPresetStore.save(this,name.getText().toString(),color);
                dialog.dismiss(); simulateLightCommand(LightCommand.color(target,color));
            } catch (IllegalArgumentException error) {
                if(save&&name.getText().toString().trim().isEmpty())name.setError("Escribe un nombre"); else hex.setError(error.getMessage());
            }
        });
    }
    private String lightTargetLabel(LightTarget target) {
        switch(target){case LOTUS:return "Luz mueble";case MR_STAR:return "Luz del televisor";default:return "ambas luces";}
    }
    private String lightCommandLabel(LightCommand command) {
        String value=command.kind==LightCommand.Kind.POWER?(command.powerOn?"encender":"apagar")
            :(command.kind==LightCommand.Kind.COLOR?"color "+command.color.hex()
            :(command.kind==LightCommand.Kind.BRIGHTNESS?"brillo "+command.brightnessPercent+"%"
            :(command.kind==LightCommand.Kind.PATTERN?"patrón "+command.name:"preset "+command.name)));
        
        return command.brightnessPercent>=0&&command.kind!=LightCommand.Kind.BRIGHTNESS?value+" · brillo "+command.brightnessPercent+"%":value;
    }
    private void simulateLightCommand(LightCommand command) {
        noteOptionsInteraction();
        String rejection = LightCommandExecutionPolicy.rejection(command);
        String message = "SIMULACIÓN · no se envía hasta confirmarlo\n\nObjetivo: "
            + lightTargetLabel(command.target) + "\nOrden: " + lightCommandLabel(command);
        if (rejection != null) {
            message += "\n\nNo disponible todavía: " + rejection
                + "\n\nLa interfaz conserva la orden, pero no fingirá un frame no validado.";
            showDialog(new AlertDialog.Builder(this).setTitle("Vista previa de luces")
                .setMessage(message).setPositiveButton("Cerrar", null).create());
            return;
        }
        AlertDialog previewDialog = new AlertDialog.Builder(this).setTitle("Vista previa de luces")
            .setMessage(message + "\n\nEnviar una vez abrirá una conexión breve por dispositivo, con reintentos acotados solo antes de aceptar un frame."
                + " El resultado no confirma por sí solo el cambio visual.")
            .setNegativeButton("Cerrar", null).setPositiveButton("Enviar una vez", null).create();
        showDialog(previewDialog);
        previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            previewDialog.dismiss(); sendLightCommand(command);
        });
    }
    private void sendLightCommand(LightCommand command) {
        AlertDialog loading = new AlertDialog.Builder(this).setTitle("Luces")
            .setMessage("Resolviendo Luz mueble/Luz del televisor autorizadas…").setCancelable(false).create();
        showDialog(loading);
        LightWriteAuthorization authorization = LightWriteAuthorization.issueForUser(SystemClock.elapsedRealtime());
        lightDispatcher.send(command, authorization, result -> handler.post(() -> {
            if (loading.isShowing()) loading.dismiss();
            String message;
            if (result.status == LightCommandDispatcher.Status.COMPLETE_UNCONFIRMED) {
                message = "ENVIADO al stack GATT, sin confirmación del dispositivo."
                    + " Confirma visualmente si cambió.";
            } else if (result.status == LightCommandDispatcher.Status.PARTIAL_OR_UNCERTAIN) {
                message = "INCIERTO: una orden independiente pudo quedar parcial. No se reintentó.";
            } else if (result.status == LightCommandDispatcher.Status.UNSUPPORTED_ORDER) {
                message = "NO EJECUTADO: " + result.detail;
            } else message = "ERROR: " + result.detail;
            showDialog(new AlertDialog.Builder(this).setTitle("Recibo de luces")
                .setMessage(message + "\n\nObjetivos completos: " + result.completedTargetCount + "/" + result.targetCount
                    + "\nFrames aceptados por GATT: " + result.acceptedFrameCount)
                .setPositiveButton("Cerrar", null).create());
        }));
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
    void agendaOptions(AgendaEntry.Kind kind) {
        String title = kind == AgendaEntry.Kind.ALARM ? "Alarmas" : "Recordatorios";
        AlertDialog loading = new AlertDialog.Builder(this).setTitle(title).setMessage("Leyendo el registro local…")
            .setNegativeButton("Volver", null).create();
        showDialog(loading);
        agendaWorker.execute(() -> {
            AgendaStore.Page page = null;
            try (AgendaStore store = new AgendaStore(this)) { page = store.list(kind); }
            catch (Exception failure) { /* No reminder content or exception text in diagnostics. */ }
            final AgendaStore.Page result = page;
            handler.post(() -> {
                if (!resumed || isFinishing() || optionsDialog != loading) return;
                if (result == null) {
                    showDialog(new AlertDialog.Builder(this).setTitle(title).setMessage("No se pudo leer el registro. No se ha borrado nada.")
                        .setPositiveButton("Cerrar", null).create()); return;
                }
                if (result.entries.isEmpty()) {
                    showDialog(new AlertDialog.Builder(this).setTitle(title).setMessage("No hay " + title.toLowerCase(new Locale("es", "CO"))
                        + " guardados. El piloto de agenda por voz se elige en Micrófono; requiere preparación local.")
                        .setPositiveButton("Cerrar", null).create()); return;
                }
                String[] labels = new String[result.entries.size()]; long now = System.currentTimeMillis();
                for (int i = 0; i < labels.length; i++) labels[i] = result.entries.get(i).label(now);
                showDialog(new AlertDialog.Builder(this).setTitle(title + (result.total > labels.length ? " · primeros " + labels.length + " de " + result.total : ""))
                    .setItems(labels, (dialog, index) -> agendaItem(result.entries.get(index)))
                    .setNegativeButton("Volver", null).create());
            });
        });
    }
    private void agendaItem(AgendaEntry entry) {
        String name = entry.kind == AgendaEntry.Kind.ALARM ? "esta alarma" : "este recordatorio";
        showDialog(new AlertDialog.Builder(this).setTitle(entry.kind == AgendaEntry.Kind.ALARM ? "Alarma" : "Recordatorio")
            .setMessage(entry.title() + "\n\n" + entry.when() + "\n" + entry.status(System.currentTimeMillis()) + "\nID: " + entry.id)
            .setNegativeButton("Volver", (dialog, which) -> agendaOptions(entry.kind))
            .setPositiveButton("Eliminar…", (dialog, which) -> showDialog(new AlertDialog.Builder(this).setTitle("Eliminar solo " + name)
                .setMessage((!entry.recurs() ? "" : "Se eliminará TODA esta serie de repetición, no solo la ocurrencia de hoy. No es silenciar ni posponer.\n\n")
                    + "Se bloqueará su entrega y se borrará su contenido. Se retirará su programación y se intentará detener solo su aviso, si está activo. No toca otros eventos ni temporizadores.")
                .setNegativeButton("Volver", (confirmation, button) -> agendaItem(entry))
                .setPositiveButton("Eliminar", (confirmation, button) -> removeAgendaItem(entry)).create())).create());
    }
    private void removeAgendaItem(AgendaEntry entry) {
        AlertDialog loading = new AlertDialog.Builder(this).setTitle("Eliminando solo el evento elegido")
            .setMessage("Esperando el resultado del registro local…").setNegativeButton("Cerrar", null).create();
        showDialog(loading);
        agendaWorker.execute(() -> {
            AgendaCancellation.Result outcome;
            try (AgendaStore store = new AgendaStore(this)) {
                outcome = AgendaCancellation.remove(entry, store, AgendaStore.effects(this));
            } catch (Exception failure) { outcome = AgendaCancellation.Result.UNCONFIRMED; }
            final AgendaCancellation.Result result = outcome;
            handler.post(() -> {
                if (!resumed || isFinishing() || optionsDialog != loading) return;
                String message;
                switch (result) {
                    case REMOVED: message = (!entry.recurs() ? "" : "Serie de repetición completa eliminada. ")+"Evento eliminado del registro; programación retirada y sin aviso propio activo registrado."; break;
                    case NOT_FOUND: message = "Ese evento ya no está en el registro. No se ha tocado ningún otro."; break;
                    case STALE: message = "El evento cambió mientras lo revisabas. Abre la lista y selecciónalo de nuevo; no se ha eliminado."; break;
                    case CLEANUP_PENDING: message = "Contenido borrado y entrega bloqueada, pero no se pudo confirmar toda la limpieza. Revisa el evento marcado como eliminación pendiente."; break;
                    default: message = "No se pudo confirmar la eliminación. Revisa el registro antes de intentarlo otra vez.";
                }
                showDialog(new AlertDialog.Builder(this).setTitle("Resultado de la eliminación").setMessage(message)
                    .setPositiveButton("Volver a la lista", (dialog, which) -> agendaOptions(entry.kind)).create());
            });
        });
    }
    void contextOptions() {
        long now = SystemClock.elapsedRealtime();
        List<ConversationMemory.Info> items = ConversationContexts.MEMORY.list(now);
        if (VoiceService.active) {
            showDialog(new AlertDialog.Builder(this).setTitle("Contexto local en RAM")
                .setMessage("Silencia primero para gestionar u olvidar una charla. Para retomar por voz abre con «Andrómeda, recuerdas…» y confirma la coincidencia. Una apertura normal crea otra charla.\n\nLFM todavía puede usar mal el historial recibido.")
                .setPositiveButton("Cerrar", null).create()); return;
        }
        List<String> labels = new ArrayList<>();
        for (ConversationMemory.Info info : items) labels.add((info.current ? "✓ " : "") + info.label
            + (info.closed ? " · cerrada, retomable" : "") + " · " + info.turns + " turnos · " + Math.max(1, (info.remainingMs + 59999) / 60000) + " min restantes");
        labels.add("Nueva conversación"); labels.add("Olvidar la conversación seleccionada…");
        showDialog(new AlertDialog.Builder(this).setTitle("Gestionar RAM · retoma por voz con «recuerdas»")
            .setItems(labels.toArray(new String[0]), (dialog, index) -> {
                if (VoiceService.active) return;
                long at = SystemClock.elapsedRealtime();
                if (index < items.size()) ConversationContexts.MEMORY.select(items.get(index).id, at);
                else if (index == items.size()) {
                    if (ConversationContexts.MEMORY.create(at) == null) {
                        showDialog(new AlertDialog.Builder(this).setMessage("Hay cuatro charlas. Olvida una antes de crear otra.")
                            .setPositiveButton("Cerrar", null).create()); return;
                    }
                } else {
                    ConversationMemory.Info selected = null;
                    for (ConversationMemory.Info info : items) if (info.current) selected = info;
                    if (selected == null) return;
                    final String id = selected.id;
                    showDialog(new AlertDialog.Builder(this).setTitle("Olvidar solo este contexto")
                        .setMessage("Los mensajes de esta charla se borran. No cancela temporizadores, alarmas ni recordatorios. Cerrar el proceso o reiniciar también borra la memoria RAM.")
                        .setNegativeButton("Volver", null).setPositiveButton("Olvidar", (confirmation, which) -> {
                            if (!VoiceService.active) {
                                ConversationContexts.MEMORY.forget(id, SystemClock.elapsedRealtime());
                                VoiceService.lastTranscript = ""; VoiceService.lastStatus = "Micrófono apagado · contexto olvidado";
                                VoiceService.uiState = FaceState.MUTED; refreshServiceState();
                            }
                            ConversationContexts.scheduleExpiry();
                        }).create()); return;
                }
                ConversationContexts.scheduleExpiry();
            }).setNegativeButton("Volver", null).create());
    }
    Intent unifiedVoiceIntent() {
        return new Intent(this, VoiceService.class).setAction("START")
            .putExtra("conversation_only", true).putExtra("assistant_tools", true)
            .putExtra("continuous_voice", AssistantConfig.CONTINUOUS_VOICE)
            .putExtra("seconds", UnifiedVoicePolicy.SESSION_SECONDS);
    }
    void assistantVoice() {
        if (VoiceService.active) { stopMicrophone(); return; }
        if (!UnifiedVoicePolicy.mayStart(VoiceService.active, getIntent().getBooleanExtra("offline_preview", false),
                AgendaDeliveryService.active || RingService.ringing, CaptureProbeService.active)) {
            showDialog(new AlertDialog.Builder(this).setTitle("Andrómeda")
                .setMessage("No se activa voz en una vista de prueba ni durante un aviso o diagnóstico activo.")
                .setPositiveButton("Cerrar", null).create()); return;
        }
        if (AssistantConfig.CONTINUOUS_VOICE && getSharedPreferences("ui",MODE_PRIVATE).getInt("continuous_voice_consent",0) != ContinuousVoicePolicy.CONSENT_VERSION) {
            showDialog(new AlertDialog.Builder(this).setTitle("Activar escucha local continua")
                .setMessage("El micrófono quedará disponible para detectar Andrómeda sin límite de cinco minutos, con indicador y botón Detener. Al cerrar una charla vuelve a esperar tu voz; el modelo permanece cargado mientras sea seguro. No se activa al abrir la app ni al reiniciar el teléfono. El supervisor local debe estar preparado.\n\nLa captura conversacional se pausa durante pensamiento, reproducción y cambios de contexto; durante mi voz se usa el detector local de silencio. Los avisos tienen prioridad de audio y la charla vuelve después. Los fallos detienen el modo y no se reinicia solo. La voz todavía necesita comprobación física: conserva Detener.\n\nNo guardo grabaciones ni transcripciones. Contexto solo en RAM, hasta una hora sin actividad humana y como máximo cuatro charlas: al llenarse se descarta la más antigua. Solo tus búsquedas web explícitas salen al Worker. Guardar o borrar un aviso requiere un recibo real de Android.\n\nEn el nuevo modo de alarmas, decir un día semanal y una hora crea repetición cada semana; las alarmas antiguas no se convierten. Borrar una serie requiere oír su horario y confirmar. Mientras suena una alarma, la escucha local solo admite silencio, pospón cinco minutos o descarta esta alarma. Posponer conserva la serie; silenciar no la borra. Tras silencio el micrófono del aviso se apaga: usa sus botones.\n\nDetener cierra la escucha y el canal; el supervisor apaga su modelo al cerrar una conexión continua autenticada. No elimina avisos guardados.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Activar",(dialog,which) -> {
                    getSharedPreferences("ui",MODE_PRIVATE).edit().putInt("continuous_voice_consent",ContinuousVoicePolicy.CONSENT_VERSION).apply(); startUnifiedVoice();
                }).create()); return;
        }
        if (!AssistantConfig.CONTINUOUS_VOICE && getSharedPreferences("ui", MODE_PRIVATE).getInt("voice_consent", 0) != UnifiedVoicePolicy.CONSENT_VERSION) {
            showDialog(new AlertDialog.Builder(this).setTitle(UnifiedVoicePolicy.START)
                .setMessage("Di Andrómeda y pide lo que necesites: conversación, temporizadores, alarmas, recordatorios, volumen del teléfono o una búsqueda explícita en Internet. No tienes que elegir una herramienta.\n\nEl volumen es multimedia (incluye mi voz), de 0 a 100 %. No modifica alarmas ni llamadas. Android tiene escalones y puede limitarlo: confirmaré el nivel real. A 0 % mi confirmación queda en pantalla, sin subirlo para hablar.\n\nEl micrófono estará activo hasta 5 minutos y puedes detenerlo en cualquier momento. No se activa solo al abrir la app ni al reiniciar el teléfono. El motor local debe estar preparado.\n\nSolo la consulta web explícita va a tu Worker; no la charla ni el audio. Las fuentes y conversaciones quedan en RAM hasta 1 h sin actividad. Los avisos de agenda son independientes.\n\n«Silencio» todavía no es fiable: conserva el botón Detener.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Activar", (dialog, which) -> {
                    getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("voice_consent", UnifiedVoicePolicy.CONSENT_VERSION).apply();
                    startUnifiedVoice();
                }).create()); return;
        }
        startUnifiedVoice();
    }
    private void startUnifiedVoice() {
        if (!resumed || !UnifiedVoicePolicy.mayStart(VoiceService.active, getIntent().getBooleanExtra("offline_preview", false),
                AgendaDeliveryService.active || RingService.ringing, CaptureProbeService.active)) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceActivation = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); return;
        }
        pendingVoiceActivation = false;
        closeOptions(); stopPreview();
        present(FaceState.PREPARING, "Preparando motor local de Andrómeda…");
        if (assistantStart != null) assistantStart.setEnabled(false);
        if (assistantAction != null) assistantAction.setEnabled(false);
        engineWorker.execute(() -> {
            AndromedaEngine.Result result=AndromedaEngine.startAndWait();
            handler.post(() -> {
                if (assistantStart != null) assistantStart.setEnabled(true);
                if (assistantAction != null) assistantAction.setEnabled(true);
                if (!resumed || isFinishing()) return;
                if (!result.ready) {
                    present(FaceState.ERROR, "Motor local no listo · "+result.state);
                    showDialog(new AlertDialog.Builder(this).setTitle("Andrómeda")
                        .setMessage("No se inició el motor completo ("+result.state+"). No se activó el micrófono ni se ejecutaron órdenes.")
                        .setPositiveButton("Cerrar", null).create());
                    return;
                }
                startForegroundService(unifiedVoiceIntent());
            });
        });
    }
    // Retained only for controlled diagnostics/instrumentation, not normal navigation.
    void voiceDiagnostics() {
        if (AgendaDeliveryService.active) {
            showDialog(new AlertDialog.Builder(this).setTitle("Aviso de agenda activo")
                .setMessage("No se abre conversación mientras suena un aviso. Puedes detenerlo o eliminar un evento concreto en su lista.")
                .setNegativeButton("Volver", null).setPositiveButton("Detener aviso", (dialog, which) -> stopService(new Intent(this, AgendaDeliveryService.class))).create()); return;
        }
        if (CaptureProbeService.active) {
            showDialog(new AlertDialog.Builder(this).setTitle("Diagnóstico de captura activo")
                .setMessage(CaptureProbeService.status).setNegativeButton("Volver", null)
                .setPositiveButton("Detener diagnóstico", (dialog, which) -> stopMicrophone()).create()); return;
        }
        if (VoiceService.active) {
            showDialog(new AlertDialog.Builder(this).setTitle("Sesión de prueba activa")
                .setItems(new String[]{"Hablar ahora", "Detener micrófono"}, (dialog, which) -> {
                    closeOptions();
                    if (which == 0) startService(new Intent(this, VoiceService.class).setAction("PUSH"));
                    else stopService(new Intent(this, VoiceService.class));
                }).setNegativeButton("Volver", null).create()); return;
        }
        showDialog(new AlertDialog.Builder(this).setTitle("Micrófono · pruebas limitadas")
            .setItems(new String[]{"Conversación LFM · sin acciones", "Crear timer por voz · experimental", "Solo activación y transcripción (0.3)",
                 "Diagnóstico de captura · 20 s", "Diagnóstico con voz fija · 20 s", "Ver diagnóstico numérico", "Agenda y búsqueda web · experimental"},
                (dialog, which) -> {
                    if (which == 3) explainCaptureProbe();
                    else if (which == 4) explainCaptureProbe(true);
                     else if (which == 5) showCaptureProbeResults();
                     else if (which == 6) explainAssistantTools();
                    else explainVoiceTrial(which != 2, which == 1);
                }).setNegativeButton("Volver", null).create());
    }
    private void explainAssistantTools() {
        showDialog(new AlertDialog.Builder(this).setTitle("Agenda y web local · prototipo")
            .setMessage("Piloto de 90 s/5 min. Ejemplos: «Andrómeda, pon una alarma mañana a las ocho de la mañana»; «recuérdame beber agua en dos minutos»; «busca en Internet qué es una nebulosa».\n\nLa IA propone, Android valida toda la petición y guarda/programa antes de anunciar éxito y cerrar. Una sola vez, sin recurrencias; fechas hasta 366 días, intervalos hasta 24 h. Si hay ambigüedad, no programa.\n\nAlarmas/recordatorios persisten fuera de la charla. El recordatorio se borra tras terminar la voz, no al llegar la hora; fallos conservados para revisar/eliminar manualmente. No garantiza que lo hayas oído ni entrega apagado/tras force-stop. Avisos hasta 30 s; «silencio» sigue sin certificar, conserva Detener.\n\nWeb envía solo la consulta explícita a Bing clásico/RSS, no charla/audio. Lee un extracto con fuente; no verifica toda la página, no usa Copilot, Gemini ni AI Overviews ni inferencia en nube. Fuentes recientes en opciones. No escucha permanente ni arranque automático de conversación.")
            .setNegativeButton("Volver", null).setPositiveButton("Elegir duración", (dialog, which) -> showDialog(new AlertDialog.Builder(this)
                .setTitle("Agenda/web · máximo por sesión").setItems(new String[]{"90 segundos", "5 minutos"}, (selection, index) -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); return; }
                    closeOptions(); stopPreview(); startForegroundService(new Intent(this, VoiceService.class).setAction("START")
                        .putExtra("conversation_only", true).putExtra("assistant_tools", true).putExtra("seconds", index == 1 ? 300 : 90));
                }).setNegativeButton("Volver", null).create())).create());
    }
    void explainCaptureProbe() { explainCaptureProbe(false); }
    void explainCaptureProbe(boolean playback) {
        String introduction = playback
            ? "Oirás una voz pública fija: «Vamos a preparar la comida y cerrar la puerta», repetida en cada fase. No es conversación ni LFM. Se probarán dos entradas, una tras cerrar la otra.\n\nMientras suena y veas MICRÓFONO ACTIVO · FASE 1, di «silencio» una vez. Repite solo cuando empiece la voz con MICRÓFONO ACTIVO · FASE 2. Si reconoce el comando a tiempo, corta únicamente esa voz fija; la captura sigue hasta completar la fase. Si no, el sonido acaba al terminar la fase o al pulsar Detener.\n\n"
            : "No habrá voz, tonos, LFM, conversación ni temporizadores. Se prueban dos entradas de micrófono, una después de cerrar completamente la otra.\n\nCuando veas MICRÓFONO ACTIVO · FASE 1, di «silencio» una vez y haz una pausa. Cuando aparezca FASE 2, repítelo una vez. Termina solo y puedes detenerlo o salir.\n\n";
        showDialog(new AlertDialog.Builder(this).setTitle(playback ? "Voz fija · máximo 20 segundos" : "Captura aislada · máximo 20 segundos")
            .setMessage(introduction + "Solo quedan contadores numéricos en RAM: no se guardan audio o transcripciones humanas. No cambia volumen, rutas ni modo global de audio. No hace falta preparar un backend. Si el audio multimedia está silenciado, no empezará el modo con voz fija.")
            .setNegativeButton("Volver", null).setPositiveButton(playback ? "Iniciar prueba con voz fija" : "Iniciar diagnóstico", (dialog, which) -> {
                if (!resumed || VoiceService.active || RingService.ringing || AgendaDeliveryService.active || SilenceListener.recording() || AudioInputGate.occupied()) return;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); return;
                }
                closeOptions(); stopPreview();
                startForegroundService(new Intent(this, CaptureProbeService.class).setAction("START").putExtra("with_playback", playback));
            }).create());
    }
    private void showCaptureProbeResults() {
        String message;
        try { message = new JSONObject().put("playback_mode", CaptureProbeService.playbackMode).put("phases", new org.json.JSONArray(CaptureProbeService.snapshot())).toString(2); }
        catch (org.json.JSONException failure) { message = "No se pudo leer el resumen numérico"; }
        showDialog(new AlertDialog.Builder(this).setTitle("Diagnóstico · solo números, sin voz/textos")
            .setMessage(message).setPositiveButton("Cerrar", null).create());
    }
    private void explainVoiceTrial(boolean conversation, boolean timerTrial) {
        showDialog(new AlertDialog.Builder(this).setTitle(conversation ? "Conversación local · experimental" : "Micrófono · prueba de activación")
            .setMessage(timerTrial
                ? "Prueba limitada: solo crear un temporizador. Di «Andrómeda, pon un temporizador de dos minutos». Una petición completa y validada autoriza la creación sin otra frase de confirmación. Android guarda y programa; anuncia el resultado real y cierra la charla, sin ofrecer más ayuda.\n\nSi falta duración o hay ambigüedad, no ejecuta y pide repetir la petición completa; sigue abierta. Preguntas/chistes/historias también continúan. No se recortan órdenes mezcladas con preguntas: pide separarlas. LFM puede equivocarse; no se acepta una duración inventada.\n\nCerrar esta prueba apaga micrófono/modelo, no cancela timers aceptados. Para otra charla hace falta nueva preparación manual. Consultar/cancelar timers por voz, luces, alarmas y búsqueda web aún no están habilitados. Máximo 90 s/5 min; sin escucha permanente ni arranque automático. No garantiza sonido tras force-stop o con teléfono apagado."
                : conversation
                ? "Abre con «Andrómeda» y una pregunta para una charla nueva. Para buscar una anterior di «Andrómeda, recuerdas…» y el tema; confirma «sí» para retomarla. La búsqueda usa palabras del tema, no recuerdos inventados por LFM. Luego conversa sin repetir el nombre.\n\nTras responder ofrece más ayuda; «no», «gracias» o «cierra la conversación» cierran con despedida y apagan micrófono/modelo. Nueva prueba: preparación manual, máximo 90 s/5 min. RAM: 1 h sin actividad; se pierde al cerrar proceso/reiniciar. No acciones ni Internet; LFM aún puede usar mal el historial. 30 s de audio útil sin certificar."
                : "Di Andrómeda: aparece la cara y escucha durante 8 segundos; después vuelve al reloj. Muestra lo reconocido, sin ejecutar órdenes, hablar ni cargar la IA.\n\nEl detector local debe estar preparado. La sesión termina sola; puedes silenciar o salir de la app.")
            .setNegativeButton("Volver", null).setPositiveButton("Elegir prueba limitada", (dialog, which) ->
                showDialog(new AlertDialog.Builder(this).setTitle("Micrófono local · duración máxima")
                    .setItems(new String[]{"90 segundos", "5 minutos"}, (selection, index) -> {
                        closeOptions();
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                        else { stopPreview(); startForegroundService(new Intent(this, VoiceService.class).setAction("START")
                             .putExtra("wake_only", !conversation).putExtra("conversation_only", conversation)
                            .putExtra("timer_trial", timerTrial)
                            .putExtra("seconds", index == 1 ? 300 : 90)); }
                    }).setNegativeButton("Volver", null).create())).create());
    }
    boolean isClosingFace() { return closingFace; }
    /** Finishes the exit animation: the face layer goes away and everything is back to normal. */
    private void finishClosing() {
        if (!closingFace) return;
        closingFace = false;
        handler.removeCallbacks(closingDone);
        if (!showingFace) faceLayer.setVisibility(View.GONE);
        faceLayer.setAlpha(1); transcript.setAlpha(1); idleLayer.setTranslationY(0); face.endJump();
    }
    /** Someone woke her while she was leaving: stop the exit and let the normal face path take over. */
    private void cancelClosing() {
        handler.removeCallbacks(closingDone);
        if (clockDrop != null) { clockDrop.removeAllListeners(); clockDrop.cancel(); clockDrop = null; }
        transcript.animate().cancel(); transcript.setAlpha(1);
        faceLayer.animate().cancel(); faceLayer.setAlpha(1);
        idleLayer.animate().cancel(); idleLayer.setTranslationY(0);
        face.endJump(); closingFace = false;
    }
    /** She said goodbye: the face jumps into the void while the clock screen falls in with a bounce. */
    private void startJumpExit() {
        face.startJump();
        idleLayer.setVisibility(View.VISIBLE); idleLayer.setAlpha(1);
        final float height = Math.max(1, root.getHeight());
        idleLayer.setTranslationY(-height * 1.12f);
        clockDrop = android.animation.ValueAnimator.ofFloat(0f, 1f);
        clockDrop.setDuration(FaceExitMotion.CLOCK_DROP_MS); clockDrop.setStartDelay(FaceExitMotion.CLOCK_DELAY_MS);
        clockDrop.setInterpolator(new android.view.animation.LinearInterpolator());
        clockDrop.addUpdateListener(a ->
            idleLayer.setTranslationY(FaceExitMotion.clockDropPercent((float)a.getAnimatedValue()) / 100f * height));
        clockDrop.start();
        transcript.animate().alpha(0).setStartDelay(FaceExitMotion.CLOCK_DELAY_MS).setDuration(800).start();
        handler.postDelayed(closingDone, FaceExitMotion.GONE_MS);
    }
    void present(FaceState value, String text) {
        boolean nextFace = UiPolicy.showsFace(value);
        if (closingFace) {
            if (!nextFace) { state.setText(text); border.setProcessing(false); return; } // Never change the face under its exit.
            cancelClosing();
        }
        FaceState before = face.getState();
        boolean animateExit = !nextFace && showingFace && resumed && !reducedMotion();
        boolean sleepExit = animateExit && before == FaceState.ASLEEP;   // fell asleep and the session closed
        // Said goodbye and the session closed. At that point the face reads "Cerrando charla" (PREPARING), not SPEAKING.
        boolean jumpExit = animateExit && (before == FaceState.SPEAKING
            || VoiceService.closingAfterSpeech && before == FaceState.PREPARING);
        if (!sleepExit && !jumpExit) face.setState(value);               // while asleep her eyes stay closed until gone
        state.setText(text);
        if (nextFace != showingFace) {
            showingFace = nextFace; idleLayer.animate().cancel(); idleLayer.animate().withEndAction(null);
            faceLayer.animate().cancel(); faceLayer.setAlpha(1); idleLayer.setTranslationY(0);
            if (sleepExit) {
                // The face fades out with its eyes still closed while the clock fades in.
                closingFace = true;
                idleLayer.setVisibility(View.VISIBLE); idleLayer.setAlpha(0); idleLayer.animate().alpha(1).setDuration(1200).start();
                faceLayer.animate().alpha(0).setDuration(1200).withEndAction(this::finishClosing).start();
            } else if (jumpExit) {
                closingFace = true; startJumpExit();
            } else if (nextFace) {
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
    void showReaction(FaceReaction reaction) {
        if (face == null || reaction == null || reaction == FaceReaction.NONE) return;
        lastFaceReactionAt = VoiceService.faceReactionAt;
        face.setReaction(reaction, 5000);
    }
    void refreshServiceState() {
        if (AgendaDeliveryService.active) {
            present(SilenceListener.recording() ? FaceState.LISTENING : FaceState.SPEAKING,
                SilenceListener.recording() ? "MICRÓFONO ACTIVO · solo interrupción del aviso" : "Aviso de agenda · micrófono apagado");
            transcript.setText(""); transcript.setVisibility(View.GONE); updateMicrophoneStop(); return;
        }
        if (CaptureProbeService.active) {
            present(CaptureProbeService.recording ? FaceState.LISTENING : FaceState.PREPARING, CaptureProbeService.status);
            transcript.setText(CaptureProbeService.playbackMode ? "Voz fija autorizada · di silencio una vez mientras suena en cada fase"
                : "Di silencio una vez en cada fase · no habrá voz ni tonos"); transcript.setVisibility(View.VISIBLE);
            updateMicrophoneStop(); return;
        }
        FaceState service = UiPolicy.serviceState(VoiceService.active,RingService.ringing || SilenceListener.recording(),VoiceService.uiState);
        // Dialogue listening only: after 15 s of silence she gets drowsy, at 25 s she sleeps, at 30 s the backend closes.
        boolean dialogue = VoiceService.active && !RingService.ringing && !SilenceListener.recording();
        long quietNow = SystemClock.uptimeMillis();
        if (!dialogue) sleep.present(FaceState.IDLE, quietNow); // Any other audio restarts the silence clock.
        FaceState shown = dialogue ? sleep.present(service, quietNow) : service;
        if (ListeningSleepPolicy.startles(face.getState(), service)) face.startle();
        present(shown, SilenceListener.recording() ? "MICRÓFONO ACTIVO · silencio o Andrómeda, silencio"
            : RingService.ringing ? "Temporizador sonando · micrófono de interrupción todavía apagado" : VoiceService.lastStatus);
        if (dialogue && service == FaceState.LISTENING && !sleepWatching) { sleepWatching = true; handler.postDelayed(sleepWatch, 250); }
        transcript.setText(VoiceService.lastTranscript);
        transcript.setVisibility(VoiceService.lastTranscript.isEmpty() ? View.GONE : View.VISIBLE);
        updateMicrophoneStop();
    }
    private void updateMicrophoneStop() {
        if (microphoneStop == null) return;
        if (assistantStart != null) assistantStart.setVisibility(VoiceService.active ? View.GONE : View.VISIBLE);
        if (assistantAction != null) assistantAction.setText(UnifiedVoicePolicy.action(VoiceService.active));
        microphoneStop.setVisibility(CaptureProbeService.active || VoiceService.active || RingService.ringing || AgendaDeliveryService.active || SilenceListener.recording() ? View.VISIBLE : View.GONE);
        if (CaptureProbeService.active) microphoneStop.setText("Detener diagnóstico · "
            + Math.max(0, (CaptureProbeService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
        else if (VoiceService.active) microphoneStop.setText(VoiceService.continuousVoiceActive ? "Detener · modo continuo" : "Detener · "
            + Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
        else if (RingService.ringing || AgendaDeliveryService.active || SilenceListener.recording()) microphoneStop.setText("Detener sonido y escucha");
    }
    private void stopMicrophone() {
        closeOptions(); stopPreview(); stopService(new Intent(this, VoiceService.class));
        stopService(new Intent(this, RingService.class));
        stopService(new Intent(this, CaptureProbeService.class));
        stopService(new Intent(this, AgendaDeliveryService.class));
        present(FaceState.MUTED, (AudioInputGate.occupied() ? "Deteniendo sonido y escucha" : "Micrófono apagado")
             + " · los eventos pendientes se conservan");
    }
    void togglePreview() {
        if (preview) { stopPreview(); refreshServiceState(); return; }
        if (VoiceService.active || RingService.ringing || CaptureProbeService.active || AgendaDeliveryService.active) return;
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
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(14); view.setTextColor(Color.rgb(224, 234, 255));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(22, 34, 55)); background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(50, 66, 91));
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(63, 83, 116)), background, null));
        view.setOnClickListener(value -> { noteOptionsInteraction(); action.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.setMargins(dp(4), dp(6), dp(4), dp(2)); parent.addView(view, params);
        return view;
    }
    public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive(); }
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if (requestCode!=1||!pendingVoiceActivation)return;
        pendingVoiceActivation=false;
        if (grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) startUnifiedVoice();
        else showDialog(new AlertDialog.Builder(this).setTitle("Andrómeda")
            .setMessage("El micrófono es necesario para activar la voz. El motor no se inició por esta autorización incompleta.")
            .setPositiveButton("Cerrar",null).create());
    }
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length == 1 && "speech_metrics".equals(args[0])) {
            try { writer.println("speech_metrics=" + new JSONObject().put("attempts", new org.json.JSONArray(SpeechDiagnostics.snapshot(SystemClock.elapsedRealtime())))); }
            catch (org.json.JSONException failure) { writer.println("speech_metrics_unavailable"); }
            return; // Fixed numeric/enumerated TTS state only; never views/transcripts/context.
        }
        if (args != null && args.length == 1 && "agenda_counts".equals(args[0])) {
            try (AgendaStore store = new AgendaStore(this)) {
                writer.println("agenda_counts=" + new JSONObject(store.counts()).put("delivery_active", AgendaDeliveryService.active));
            } catch (Exception failure) { writer.println("agenda_counts_unavailable=" + failure.getClass().getSimpleName()); }
            return; // Never fall through to views/reminder content.
        }
        if (args != null && args.length == 1 && "capture_probe_metrics".equals(args[0])) {
            try { writer.println("capture_probe_metrics=" + new JSONObject().put("active", CaptureProbeService.active)
                .put("recording", CaptureProbeService.recording).put("attempt", CaptureProbeService.attempt).put("end", CaptureProbeService.runEnd.name())
                .put("playback_mode", CaptureProbeService.playbackMode).put("phases", new org.json.JSONArray(CaptureProbeService.snapshot()))); }
            catch (org.json.JSONException failure) { writer.println("capture_probe_metrics_unavailable"); }
            return;
        }
        if (args != null && args.length == 1 && "silence_metrics".equals(args[0])) {
            try { writer.println("silence_metrics=" + new JSONObject().put("windows", new org.json.JSONArray(SilenceDiagnostics.snapshot()))); }
            catch (org.json.JSONException failure) { writer.println("silence_metrics_unavailable"); }
            return; // Never dump view/transcript/context state through this diagnostic path.
        }
        super.dump(prefix, fd, writer, args);
    }
    public void onBackPressed() {
        if (policy.optionsVisible()) closeOptions();
        else if (preview) { stopPreview(); refreshServiceState(); }
        else if (isDefaultHome()) closeOptions();
        else super.onBackPressed();
    }
    private boolean isDefaultHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo value = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return value != null && value.activityInfo != null && getPackageName().equals(value.activityInfo.packageName);
        } catch (RuntimeException ignored) { return false; }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); refreshServiceState();
    }
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) { showOptions(); return true; }
        return super.onKeyUp(keyCode, event);
    }
    private void noteScreenInteraction() {
        long now = SystemClock.uptimeMillis();
        screenIdleDelay = ScreenBrightnessPolicy.IDLE_MS;
        handler.removeCallbacks(screenIdle);
        if (now - lastScreenInteraction > 1000L) applyScreenBrightness(false);
        lastScreenInteraction = now;
        handler.postDelayed(screenIdle, screenIdleDelay);
    }
    private void noteVoiceInteraction() {
        screenIdleDelay = ScreenBrightnessPolicy.VOICE_IDLE_MS;
        handler.removeCallbacks(screenIdle);
        applyScreenBrightness(false);
        handler.postDelayed(screenIdle, screenIdleDelay);
    }
    private void applyScreenBrightness(boolean idle) {
        if (!Settings.System.canWrite(this)) return;
        RestModeSchedule rest = RestModeStore.load(this);
        int level = ScreenBrightnessPolicy.desired(rest, System.currentTimeMillis(), idle);
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int current = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
            if (current != level) Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, level);
        } catch (RuntimeException ignored) { /* The rest-mode UI exposes the permission. */ }
    }
    public void onResume() {
        super.onResume(); resumed = visibleToUser = true;
        IntentFilter filter = new IntentFilter(VoiceService.STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, filter);
        refreshServiceState(); face.setForeground(true); border.setForeground(true); handler.post(tick); handler.post(weatherRefresh);
        screenIdleDelay = ScreenBrightnessPolicy.IDLE_MS;
        handler.removeCallbacks(screenIdle);
        handler.postDelayed(screenIdle, screenIdleDelay);
    }
    public void onPause() {
        resumed = visibleToUser = false; weatherGeneration++;
        handler.removeCallbacks(screenIdle);
        // One bounded in-flight public request may finish; its generation is now
        // invalid. Do not block the UI thread on HttpURLConnection.disconnect().
        closeOptions(); stopPreview(); refreshServiceState(); face.setForeground(false); border.setForeground(false);
        handler.removeCallbacks(tick); handler.removeCallbacks(weatherRefresh); unregisterReceiver(receiver); super.onPause();
    }
    public void onStop() {
        if (VoiceLifecyclePolicy.stopVoiceWhenActivityStops(VoiceService.continuousVoiceActive))
            stopService(new Intent(this, VoiceService.class));
        stopService(new Intent(this, CaptureProbeService.class)); super.onStop();
    }
    public void onDestroy() {
        if (weatherTask != null) weatherTask.cancel(true);
        weatherWorker.shutdownNow();
        engineWorker.shutdownNow();
        agendaWorker.shutdown(); // Closing options/activity must not discard an accepted selected-ID deletion.
        if (lightDispatcher != null) lightDispatcher.close();
        super.onDestroy();
    }
}
