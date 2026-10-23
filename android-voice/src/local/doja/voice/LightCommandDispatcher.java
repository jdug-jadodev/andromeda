package local.doja.voice;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.SystemClock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves authorized names and executes independent device orders off the UI
 * and audio threads. It retries only pre-write transport failures and never
 * persists addresses.
 */
public final class LightCommandDispatcher implements AutoCloseable {
    public enum Status { COMPLETE_UNCONFIRMED, AUTHORIZATION_REQUIRED, UNSUPPORTED_ORDER,
        DEVICE_NOT_FOUND, TRANSPORT_ERROR, PARTIAL_OR_UNCERTAIN, BUSY, CLOSED }
    public interface Callback { void onComplete(Result result); }

    public static final class Result {
        public final Status status;
        public final int targetCount;
        public final int completedTargetCount;
        public final int acceptedFrameCount;
        public final boolean deviceAcknowledged;
        public final String detail;
        private Result(Status status, int targetCount, int completedTargetCount,
                int acceptedFrameCount, String detail) {
            this.status = status;
            this.targetCount = targetCount;
            this.completedTargetCount = completedTargetCount;
            this.acceptedFrameCount = acceptedFrameCount;
            this.deviceAcknowledged = false;
            this.detail = detail;
        }
        public boolean isSuccessfulSubmission() { return status == Status.COMPLETE_UNCONFIRMED; }
    }

    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;
    private boolean closed;

    public LightCommandDispatcher(Context context) {
        if (context == null) throw new IllegalArgumentException("Falta el contexto de luces");
        this.context = context.getApplicationContext();
    }

    public synchronized void send(LightCommand command, LightWriteAuthorization authorization, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Falta el recibo de luces");
        if (closed) { callback.onComplete(new Result(Status.CLOSED, 0, 0, 0, "dispatcher_closed")); return; }
        if (busy) { callback.onComplete(new Result(Status.BUSY, 0, 0, 0, "another_order_in_flight")); return; }
        String rejection = LightCommandExecutionPolicy.rejection(command);
        if (rejection != null) {
            callback.onComplete(new Result(Status.UNSUPPORTED_ORDER, 0, 0, 0, rejection));
            return;
        }
        if (authorization == null || !authorization.consume(SystemClock.elapsedRealtime())) {
            callback.onComplete(new Result(Status.AUTHORIZATION_REQUIRED, 0, 0, 0, "fresh_user_confirmation_required"));
            return;
        }
        final List<LightCommand> commands = LightCommandRouting.independent(command);
        busy = true;
        new LightDeviceResolver(context).resolve(command, resolved -> {
            if (!resolved.isResolved()) {
                finish(callback, new Result(Status.DEVICE_NOT_FOUND, commands.size(), 0, 0, resolved.status.name()));
                return;
            }
            worker.execute(() -> writeNext(commands, 0, resolved.devices, 0, callback));
        });
    }

    private void writeNext(List<LightCommand> commands, int index, Map<LightTarget, BluetoothDevice> devices,
            int acceptedFrames, Callback callback) {
        if (closed) { finish(callback, new Result(Status.PARTIAL_OR_UNCERTAIN, commands.size(), index, acceptedFrames, "dispatcher_closed")); return; }
        if (index >= commands.size()) {
            finish(callback, new Result(Status.COMPLETE_UNCONFIRMED, commands.size(), index, acceptedFrames, "accepted_by_gatt_stack"));
            return;
        }
        LightCommand command = commands.get(index);
        BluetoothDevice device = devices.get(command.target);
        if (device == null) {
            finish(callback, new Result(Status.DEVICE_NOT_FOUND, commands.size(), index, acceptedFrames, "target_not_resolved"));
            return;
        }
        sendTargetAttempt(commands, index, devices, acceptedFrames, callback, 1);
    }

    private void sendTargetAttempt(List<LightCommand> commands, int index,
            Map<LightTarget, BluetoothDevice> devices, int acceptedFrames,
            Callback callback, int attempt) {
        LightCommand command = commands.get(index);
        BluetoothDevice device = devices.get(command.target);
        if (command.target == LightTarget.LOTUS) {
            new LotusLampBleAdapter(context, device).send(command, result -> targetResult(
                commands, index, devices, acceptedFrames, callback, attempt, result.status.name(),
                result.isSuccessfulSubmission(), result.acceptedFrameCount));
        } else {
            new MrStarBleAdapter(context, device).send(command, result -> targetResult(
                commands, index, devices, acceptedFrames, callback, attempt, result.status.name(),
                result.isSuccessfulSubmission(), result.acceptedFrameCount));
        }
    }

    private void targetResult(List<LightCommand> commands, int index,
            Map<LightTarget, BluetoothDevice> devices, int acceptedFrames,
            Callback callback, int attempt, String status, boolean success,
            int acceptedThisAttempt) {
        int totalAccepted = acceptedFrames + acceptedThisAttempt;
        if (!success && LightBleRetryPolicy.mayRetry(status, acceptedThisAttempt, attempt)) {
            int nextAttempt = attempt + 1;
            worker.execute(() -> {
                SystemClock.sleep(LightBleRetryPolicy.backoffMs(nextAttempt));
                sendTargetAttempt(commands, index, devices, acceptedFrames, callback, nextAttempt);
            });
            return;
        }
        if (!success) {
            Status finalStatus = index == 0 ? Status.TRANSPORT_ERROR : Status.PARTIAL_OR_UNCERTAIN;
            String detail = (attempt > 1 ? "retry_exhausted_" : "") + status;
            finish(callback, new Result(finalStatus, commands.size(), index, totalAccepted, detail));
            return;
        }
        worker.execute(() -> {
            if (index + 1 < commands.size()) SystemClock.sleep(LightBleRetryPolicy.BETWEEN_TARGETS_MS);
            writeNext(commands, index + 1, devices, totalAccepted, callback);
        });
    }

    private synchronized void finish(Callback callback, Result result) {
        if (!busy) return;
        busy = false;
        callback.onComplete(result);
    }

    @Override public synchronized void close() {
        closed = true;
        worker.shutdownNow();
    }
}
