package local.doja.voice;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.View;
import java.util.*;

/** UI-only tests. No VoiceService/TTS/model start, microphone request or timer mutation. */
public final class FaceInstrumentation extends Instrumentation {
    private MainActivity activity;
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        Map<String, ?> before = new HashMap<>(TimerStore.prefs(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing, "An active session must not be interrupted by UI tests");
            final org.json.JSONObject forbidden = new org.json.JSONObject().put("type", "timer")
                .put("id", "never-execute-wake-test").put("seconds", 10);
            runOnMainSync(() -> {
                VoiceService guarded = new VoiceService(); guarded.running = true; guarded.wakeOnly = true;
                try { guarded.apply(forbidden); check(guarded.tts == null, "Wake-only initialized TTS"); }
                finally { guarded.running = false; guarded.controls.shutdownNow(); }
                VoiceService conversation = new VoiceService(); conversation.running = true; conversation.conversationOnly = true;
                try { conversation.apply(forbidden); check(conversation.tts == null, "Conversation-only accepted an executor event"); }
                finally { conversation.running = false; conversation.controls.shutdownNow(); }
            });
            check(FaceState.parse(null) == FaceState.ERROR, "Missing state is not safe");
            check(FaceState.parse("unknown") == FaceState.ERROR, "Unknown state is not safe");
            for (FaceState value : FaceState.values()) check(FaceState.parse(value.name()) == value, "State roundtrip failed");
            runOnMainSync(() -> {
                FaceView sample = new FaceView(context); sample.setReducedMotion(true); sample.layout(0, 0, 720, 260);
                Bitmap bitmap = Bitmap.createBitmap(720, 260, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); int[] pixels = new int[720 * 260]; Set<Integer> hashes = new HashSet<>();
                try {
                    for (FaceState value : FaceState.values()) {
                        bitmap.eraseColor(Color.BLACK); sample.setState(value); sample.draw(canvas);
                        bitmap.getPixels(pixels, 0, 720, 0, 0, 720, 260);
                        check(hashes.add(Arrays.hashCode(pixels)), "States should have distinct visual presentation: " + value);
                        check(!sample.isAnimating(), "Detached/reduced-motion face must not schedule frames");
                    }
                } finally { bitmap.recycle(); }
            });
            result.putInt("distinct_rendered_states", FaceState.values().length);
            long weatherNow = 1760000000000L;
            String fixture = "{\"timezone\":\"America/Bogota\",\"current_units\":{\"temperature_2m\":\"°C\",\"time\":\"unixtime\"},"
                + "\"current\":{\"temperature_2m\":18,\"weather_code\":3,\"time\":1760000000}}";
            check(WeatherClient.parse(fixture, weatherNow).celsius == 18, "Weather fixture did not parse");
            try { WeatherClient.parse(fixture.replace("°C", "°F"), weatherNow); throw new IllegalStateException("Accepted Fahrenheit as Celsius"); }
            catch (java.io.IOException expected) {}
            try { WeatherClient.parse(fixture, weatherNow + WeatherData.MAX_AGE_MS + 1); throw new IllegalStateException("Accepted stale weather"); }
            catch (java.io.IOException expected) {}
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> {
                check(activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE, "UI is not landscape");
                check(activity.idleLayer.getVisibility() == View.VISIBLE && activity.faceLayer.getVisibility() == View.GONE, "Idle must show the clock, not the face");
                check(activity.clock.getHeight() >= activity.dp(80), "Clock is not dominant");
                check(!activity.border.isProcessing() && !activity.border.isAnimating(), "Idle must not animate a loader");
                check(!activity.face.isAnimating(), "Hidden face still schedules frames");
                check(!activity.policy.optionsVisible(), "Options started visible");
                check(activity.assistantStart != null && UnifiedVoicePolicy.START.contentEquals(activity.assistantStart.getText()), "Missing single voice entry");
                Intent voice = activity.unifiedVoiceIntent();
                check(voice.getBooleanExtra("assistant_tools", false) && voice.getBooleanExtra("conversation_only", false)
                    && voice.getIntExtra("seconds", 0) == 300 && !voice.getBooleanExtra("wake_only", false)
                    && !voice.getBooleanExtra("timer_trial", false), "Normal entry does not include all native tools");
                check(!containsButton(activity.optionsLayer, "Micrófono") && !containsButton(activity.optionsLayer, "Vista previa")
                    && !containsButton(activity.optionsLayer, "Diagnóstico de captura · 20 s")
                    && !containsButton(activity.optionsLayer, "Solo activación y transcripción (0.3)")
                    && !containsButton(activity.optionsLayer, "Agenda y búsqueda web · experimental"), "Technical mode leaked into normal options");
                long time = SystemClock.uptimeMillis();
                tap(activity, time); check(!activity.policy.optionsVisible(), "Single tap opened options");
                tap(activity, time + 120); check(activity.policy.optionsVisible(), "Double tap did not open options");
                long deadline = activity.policy.optionsDeadline();
                activity.expireOptionsAt(deadline - 1); check(activity.policy.optionsVisible(), "Options expired too early");
                activity.expireOptionsAt(deadline); check(!activity.policy.optionsVisible() && activity.optionsLayer.getVisibility() == View.GONE, "Options did not hide at 50 s");
                check(!activity.isPreviewing(), "Preview must not start automatically");
                activity.togglePreview();
                check(activity.isPreviewing() && activity.previewBadge.getVisibility() == View.VISIBLE, "Preview is not labelled");
                check(activity.state.getText().toString().contains("simulado"), "Preview could be mistaken for real operation");
                // Exercise the presentation event handler, without starting a service.
                activity.receiver.onReceive(context, new Intent(VoiceService.STATE));
                check(!activity.isPreviewing(), "Service update did not supersede preview");
                check(activity.face.getState() == VoiceService.uiState, "Service snapshot was not restored");
                check(activity.state.getText().toString().equals(VoiceService.lastStatus), "Status was not restored");
                activity.togglePreview();
                activity.present(FaceState.THINKING, "Vista previa · pensando (simulado)");
                check(activity.border.isProcessing() && activity.faceLayer.getVisibility() == View.VISIBLE, "Thinking must retain the face and show the border");
                int oldColor = activity.border.getColor();
                activity.border.setColor(UiPolicy.parseColor("#12AB34"));
                check(activity.border.getColor() == 0xff12ab34, "Loader color was ignored");
                activity.border.setColor(oldColor);
                activity.face.setReducedMotion(false); activity.border.setReducedMotion(false);
            });
            final long[] frames = new long[2];
            runOnMainSync(() -> frames[0] = activity.face.getDrawnFrames());
            SystemClock.sleep(1200); // Measure an animation interval; no asynchronous job is polled.
            runOnMainSync(() -> {
                frames[1] = activity.face.getDrawnFrames();
                check(frames[1] - frames[0] >= 8, "Animation did not advance");
                activity.present(FaceState.SPEAKING, "Vista previa · hablando (simulado)");
                check(!activity.border.isProcessing() && !activity.border.isAnimating(), "Border continued outside processing");
                activity.present(FaceState.READY, "Esperando activación");
                check(activity.idleLayer.getVisibility() == View.VISIBLE && activity.faceLayer.getVisibility() == View.GONE, "Ready did not return to clock");
                activity.stopPreview(); activity.refreshServiceState();
                activity.face.setForeground(false);
                check(!activity.face.isAnimating(), "Background face still schedules frames");
                activity.face.setForeground(true); activity.face.setReducedMotion(true);
                check(!activity.face.isAnimating(), "Reduced motion still schedules frames");
            });
            result.putLong("draws_in_1200ms", frames[1] - frames[0]);
            check(!VoiceService.active && !RingService.ringing, "Visual test started audio");
            check(before.equals(TimerStore.prefs(context).getAll()), "Visual test changed timer persistence");
            result.putBoolean("passed", true);
            result.putBoolean("microphone_opened", false);
            result.putBoolean("model_started", false);
            result.putBoolean("timers_unchanged", true);
            result.putBoolean("voice_acceptance", false);
            result.putBoolean("external_network", false);
        } catch (Exception error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private static void tap(MainActivity activity, long time) {
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, 100, 100, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 30, android.view.MotionEvent.ACTION_UP, 100, 100, 0);
        try { activity.dispatchTouchEvent(down); activity.dispatchTouchEvent(up); }
        finally { down.recycle(); up.recycle(); }
    }
    private static boolean containsButton(View view, String label) {
        if (view instanceof android.widget.Button && label.contentEquals(((android.widget.Button)view).getText())) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsButton(group.getChildAt(i), label)) return true;
        }
        return false;
    }
}
