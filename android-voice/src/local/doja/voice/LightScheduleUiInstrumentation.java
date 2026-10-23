package local.doja.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Opens the real schedule UI without audio/BLE and checks that edit actions are present. */
public final class LightScheduleUiInstrumentation extends Instrumentation {
    private Activity activity;

    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .putExtra("offline_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(() -> {
                try {
                    Method method = MainActivity.class.getDeclaredMethod("lightScheduleOptions", LightTarget.class);
                    method.setAccessible(true);
                    method.invoke(activity, LightTarget.BOTH);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                try {
                    Field field = MainActivity.class.getDeclaredField("optionsDialog");
                    field.setAccessible(true);
                    AlertDialog dialog = (AlertDialog) field.get(activity);
                    if (dialog == null || !dialog.isShowing()) throw new IllegalStateException("dialog_not_showing");
                    ListView list = dialog.getListView();
                    if (list == null || list.getAdapter() == null) throw new IllegalStateException("list_missing");
                    if (list.getAdapter().getCount() < 5) throw new IllegalStateException("edit_actions_missing");
                    String first = String.valueOf(list.getAdapter().getItem(0));
                    if (!first.contains("Cambiar encendido")) throw new IllegalStateException("first_action_missing");
                    list.performItemClick(null, 0, list.getAdapter().getItemId(0));
                    result.putString("result", "edit_actions_visible count=" + list.getAdapter().getCount()
                        + " first=" + first);
                    result.putBoolean("ble_written", false);
                    result.putBoolean("microphone_opened", false);
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            final boolean[] picker = {false};
                            runOnMainSync(() -> picker[0] = hasTimePicker());
                            result.putBoolean("time_picker_opened", picker[0]);
                            finish(picker[0] ? 0 : 1, result);
                        } catch (Exception failure) { finish(1, result); }
                    }, "schedule-ui-picker-check").start();
                } catch (Exception failure) {
                    result.putString("result", "schedule_ui_check_failed");
                    finish(1, result);
                }
            });
        } catch (Exception failure) {
            result.putString("result", "schedule_ui_launch_failed");
            finish(1, result);
        }
    }

    private static boolean hasTimePicker() {
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Object global = globalClass.getMethod("getInstance").invoke(null);
            Field views = globalClass.getDeclaredField("mViews"); views.setAccessible(true);
            Object value = views.get(global);
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value)
                    if (item instanceof View && containsTimePicker((View) item)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static boolean containsTimePicker(View view) {
        if (view.getClass().getName().contains("TimePicker")) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsTimePicker(group.getChildAt(i))) return true;
        }
        return false;
    }
}
