package local.doja.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Opens the real rest-mode dialog without audio/BLE and checks its editable actions. */
public final class RestModeUiInstrumentation extends Instrumentation {
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
                    Method method = MainActivity.class.getDeclaredMethod("restModeOptions");
                    method.setAccessible(true);
                    method.invoke(activity);
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
                    if (list == null || list.getAdapter() == null || list.getAdapter().getCount() < 3)
                        throw new IllegalStateException("edit_actions_missing");
                    String first = String.valueOf(list.getAdapter().getItem(0));
                    if (!first.contains("Cambiar hora de descanso")) throw new IllegalStateException("first_action_missing");
                    result.putString("result", "actions_visible count=" + list.getAdapter().getCount() + " first=" + first);
                    result.putBoolean("audio_opened", false);
                    result.putBoolean("ble_written", false);
                    finish(0, result);
                } catch (Exception failure) { finish(1, result); }
            });
        } catch (Exception failure) {
            result.putString("result", "rest_ui_launch_failed");
            finish(1, result);
        }
    }
}
