package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/**
 * Explicit, one-shot adapter harness. It is not part of the app UI or voice
 * path. Without the exact authorization argument it cannot open GATT or write.
 */
public final class LotusWriteInstrumentation extends Instrumentation {
    private static final String AUTHORIZATION = "LOTUS_D81B60_25_ONCE";
    private boolean finished;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || !AUTHORIZATION.equals(arguments.getString("authorize_write"))) {
            finishResult(false, "AUTHORIZATION_REQUIRED", 0, 0, false);
            return;
        }
        String address = arguments.getString("target_address");
        if (address == null || address.trim().isEmpty()) {
            finishResult(false, "ADDRESS_REQUIRED", 0, 0, false);
            return;
        }
        LotusLampBleAdapter adapter;
        try {
            adapter = new LotusLampBleAdapter(getTargetContext(), address);
        } catch (IllegalArgumentException invalid) {
            finishResult(false, "INVALID_SETUP", 0, 0, false);
            return;
        }
        LightCommand command = LightCommand.color(
            LightTarget.LOTUS, LightColor.parseHex("#D81B60"), 25);
        adapter.send(command, result -> finishResult(result.isSuccessfulSubmission(),
            result.status.name(), result.frameCount, result.acceptedFrameCount,
            result.deviceAcknowledged));
    }

    private void finishResult(boolean passed, String status, int frameCount,
            int acceptedFrameCount, boolean deviceAcknowledged) {
        if (finished) return;
        finished = true;
        Bundle result = new Bundle();
        result.putBoolean("passed", passed);
        result.putString("status", status);
        result.putInt("frame_count", frameCount);
        result.putInt("accepted_frame_count", acceptedFrameCount);
        result.putBoolean("device_acknowledged", deviceAcknowledged);
        result.putBoolean("writes_retried", false);
        result.putBoolean("address_exported", false);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
