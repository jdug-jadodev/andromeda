package local.doja.voice;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** Empty real agenda menus only. Never seeds the human database or starts output. */
public final class AgendaUiInstrumentation extends Instrumentation {
    private MainActivity activity;
    private static void check(boolean value) { if (!value) throw new IllegalStateException("Agenda UI contract"); }
    private Object field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
    }
    private Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) for (int index = 0; index < ((ViewGroup)view).getChildCount(); index++) {
            Button match = button(((ViewGroup)view).getChildAt(index), label); if (match != null) return match;
        }
        return null;
    }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll());
        Map<String, ?> receipts = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active && !SilenceListener.recording());
            try (AgendaStore store = new AgendaStore(context)) { for (long count : store.counts().values()) check(count == 0); }
            activity = (MainActivity)startActivitySync(new Intent(context, MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (String label : new String[]{"Alarmas", "Recordatorios"}) {
                runOnMainSync(() -> {
                    activity.showOptions(); Button selected = button(activity.optionsLayer, label); check(selected != null); selected.performClick();
                });
                // Await completion of the same bounded worker, then drain posted UI work.
                ((ExecutorService)field("agendaWorker")).submit(() -> {}).get(5, TimeUnit.SECONDS);
                waitForIdleSync();
                runOnMainSync(() -> {
                    try {
                        AlertDialog dialog = (AlertDialog)field("optionsDialog"); check(dialog != null && dialog.isShowing());
                        TextView message = dialog.findViewById(android.R.id.message);
                        check(message != null && message.getText().toString().startsWith("No hay " + label.toLowerCase(new Locale("es", "CO"))));
                        activity.expireOptionsAt(activity.policy.optionsDeadline());
                        check(!dialog.isShowing() && !activity.policy.optionsVisible() && field("optionsDialog") == null);
                    } catch (Exception failure) { throw new IllegalStateException("Agenda UI contract", failure); }
                });
            }
            try (AgendaStore store = new AgendaStore(context)) { for (long count : store.counts().values()) check(count == 0); }
            check(timers.equals(TimerStore.prefs(context).getAll()) && receipts.equals(TimerStore.actionReceipts(context).getAll()));
            result.putBoolean("passed", true); result.putBoolean("separate_empty_lists", true); result.putBoolean("timeout_closes_dialogs", true);
            result.putBoolean("real_agenda_unchanged", true); result.putBoolean("real_timers_unchanged", true);
        } catch (Exception failure) { result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName()); }
        finally { if (activity != null) runOnMainSync(() -> activity.finish()); }
        result.putBoolean("microphone_opened", false); result.putBoolean("audio_played", false); result.putBoolean("llm_started", false);
        result.putBoolean("real_scheduler", false);
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
