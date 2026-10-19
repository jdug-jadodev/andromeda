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
    static volatile boolean visibleToUser;
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
    private final ExecutorService agendaWorker = Executors.newSingleThreadExecutor();
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
            if (CaptureProbeService.active) {
                refreshServiceState();
                wakeHint.setText(CaptureProbeService.status + " · quedan "
                    + Math.max(0, (CaptureProbeService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
            } else if (SilenceListener.recording()) wakeHint.setText("MICRÓFONO ACTIVO · silencio o Andrómeda, silencio · no abre otra charla");
            else if (VoiceService.active && VoiceService.expiresAt > 0) {
                long remaining = Math.max(0, (VoiceService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000);
                wakeHint.setText((VoiceService.wakeListening ? "Activación por voz en prueba" : "Prueba manual / en pausa")
                     + (VoiceService.assistantToolsActive ? " · agenda y web" : VoiceService.timerTrialActive ? " · timer por petición" : VoiceService.conversationTestActive ? " · LFM sin acciones" : VoiceService.wakeTestActive ? " · sin ejecutar órdenes" : "")
                    + " · quedan " + remaining + " s · doble toque para opciones");
            } else wakeHint.setText(CaptureProbeService.status.isEmpty() ? "Activación por voz apagada · doble toque para opciones"
                : CaptureProbeService.status + " · doble toque → Micrófono para resultados");
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
        LinearLayout first = new LinearLayout(this), second = new LinearLayout(this), third = new LinearLayout(this);
        button(first, "Vista previa", () -> { closeOptions(); togglePreview(); });
        button(first, "Color del brillo", this::chooseColor);
        button(first, "Contexto", this::contextOptions);
        button(first, "Fuentes web", () -> showDialog(new AlertDialog.Builder(this).setTitle("Última búsqueda · fuentes en RAM")
            .setMessage(WebSources.describe(SystemClock.elapsedRealtime())).setPositiveButton("Cerrar", null).create()));
        button(second, "Temporizadores", this::timerOptions);
        button(second, "Alarmas", () -> agendaOptions(AgendaEntry.Kind.ALARM));
        button(second, "Recordatorios", () -> agendaOptions(AgendaEntry.Kind.REMINDER));
        button(third, "Micrófono", this::voiceDiagnostics);
        button(third, "Silenciar", this::stopMicrophone);
        button(third, "Volver", this::closeOptions);
        panel.addView(first); panel.addView(second); panel.addView(third);
        ScrollView optionsScroll = new ScrollView(this);
        optionsScroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        params.setMargins(dp(42), dp(16), dp(42), dp(16)); optionsLayer.addView(optionsScroll, params);
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
                .setMessage("Se bloqueará su entrega y se borrará su contenido. Se retirará su programación y se intentará detener solo su aviso, si está activo. No toca otros eventos ni temporizadores.")
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
                    case REMOVED: message = "Evento eliminado del registro; programación retirada y sin aviso propio activo registrado."; break;
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
        present(VoiceService.uiState, SilenceListener.recording() ? "MICRÓFONO ACTIVO · silencio o Andrómeda, silencio"
            : RingService.ringing ? "Temporizador sonando · micrófono de interrupción todavía apagado" : VoiceService.lastStatus);
        transcript.setText(VoiceService.lastTranscript);
        transcript.setVisibility(VoiceService.lastTranscript.isEmpty() ? View.GONE : View.VISIBLE);
        updateMicrophoneStop();
    }
    private void updateMicrophoneStop() {
        if (microphoneStop == null) return;
        microphoneStop.setVisibility(CaptureProbeService.active || VoiceService.active || RingService.ringing || AgendaDeliveryService.active || SilenceListener.recording() ? View.VISIBLE : View.GONE);
        if (CaptureProbeService.active) microphoneStop.setText("Detener diagnóstico · "
            + Math.max(0, (CaptureProbeService.expiresAt - SystemClock.elapsedRealtime() + 999) / 1000) + " s");
        else if (VoiceService.active) microphoneStop.setText("Silenciar · "
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
    private void button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(14); view.setTextColor(Color.rgb(224, 234, 255));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(22, 34, 55)); background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(50, 66, 91));
        view.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(63, 83, 116)), background, null));
        view.setOnClickListener(value -> { noteOptionsInteraction(); action.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.setMargins(dp(4), dp(6), dp(4), dp(2)); parent.addView(view, params);
    }
    public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immersive(); }
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
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
        else super.onBackPressed();
    }
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU) { showOptions(); return true; }
        return super.onKeyUp(keyCode, event);
    }
    public void onResume() {
        super.onResume(); resumed = visibleToUser = true;
        IntentFilter filter = new IntentFilter(VoiceService.STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, filter);
        refreshServiceState(); face.setForeground(true); border.setForeground(true); handler.post(tick); handler.post(weatherRefresh);
    }
    public void onPause() {
        resumed = visibleToUser = false; weatherGeneration++;
        // One bounded in-flight public request may finish; its generation is now
        // invalid. Do not block the UI thread on HttpURLConnection.disconnect().
        closeOptions(); stopPreview(); refreshServiceState(); face.setForeground(false); border.setForeground(false);
        handler.removeCallbacks(tick); handler.removeCallbacks(weatherRefresh); unregisterReceiver(receiver); super.onPause();
    }
    public void onStop() {
        stopService(new Intent(this, VoiceService.class)); stopService(new Intent(this, CaptureProbeService.class)); super.onStop();
    }
    public void onDestroy() {
        if (weatherTask != null) weatherTask.cancel(true);
        weatherWorker.shutdownNow();
        agendaWorker.shutdown(); // Closing options/activity must not discard an accepted selected-ID deletion.
        super.onDestroy();
    }
}
