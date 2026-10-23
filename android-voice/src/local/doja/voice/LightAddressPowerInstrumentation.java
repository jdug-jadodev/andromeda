package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** One explicit direct-address power write for diagnostics; no scan, retry or address export. */
public final class LightAddressPowerInstrumentation extends Instrumentation {
    private static final String AUTHORIZATION = "LIGHT_ADDRESS_POWER_ONCE";
    private boolean finished;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || !AUTHORIZATION.equals(arguments.getString("authorize_write"))) {
            finishResult(false, "AUTHORIZATION_REQUIRED", false);
            return;
        }
        String target = arguments.getString("target");
        String address = arguments.getString("address");
        boolean on = bool(arguments, "on");
        if (address == null || address.trim().isEmpty() || !LightAddressStore.valid(address)) {
            finishResult(false, "ADDRESS_REQUIRED", false);
            return;
        }
        final LightTarget lightTarget;
        try { lightTarget = LightTarget.valueOf(target); }
        catch (Exception invalid) { finishResult(false, "TARGET_REQUIRED", false); return; }
        LightCommand command;
        try { command = LightCommand.power(lightTarget, on); }
        catch (Exception invalid) { finishResult(false, "INVALID_ORDER", false); return; }
        // Seed the local cache even if this particular connection is unavailable.
        LightAddressStore.save(getTargetContext(), lightTarget, address);
        try {
            if (lightTarget == LightTarget.LOTUS) {
                new LotusLampBleAdapter(getTargetContext(), address).send(command,
                    result -> rememberAndFinish(lightTarget, address, on, result));
            } else if (lightTarget == LightTarget.MR_STAR) {
                new MrStarBleAdapter(getTargetContext(), address).send(command,
                    result -> rememberAndFinish(lightTarget, address, on, result));
            } else finishResult(false, "ONE_TARGET_REQUIRED", false);
        } catch (Exception invalid) { finishResult(false, "ADAPTER_SETUP_FAILED", false); }
    }

    private void rememberAndFinish(LightTarget target, String address, boolean on,
            LotusLampBleAdapter.Result result) {
        if (result.isSuccessfulSubmission()) {
            LightAddressStore.save(getTargetContext(), target, address);
            LightStateStore.record(getTargetContext(), target, on);
        }
        finishResult(result.isSuccessfulSubmission(), result.status.name(), true);
    }

    private void rememberAndFinish(LightTarget target, String address, boolean on,
            MrStarBleAdapter.Result result) {
        if (result.isSuccessfulSubmission()) {
            LightAddressStore.save(getTargetContext(), target, address);
            LightStateStore.record(getTargetContext(), target, on);
        }
        finishResult(result.isSuccessfulSubmission(), result.status.name(), true);
    }

    private static boolean bool(Bundle arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Boolean ? (Boolean) value
            : value instanceof String && Boolean.parseBoolean((String) value);
    }

    private void finishResult(boolean passed, String status, boolean attempted) {
        if (finished) return;
        finished = true;
        Bundle result = new Bundle();
        result.putBoolean("passed", passed);
        result.putString("status", status);
        result.putBoolean("connection_attempted", attempted);
        result.putBoolean("address_exported", false);
        result.putBoolean("scan_used", false);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
