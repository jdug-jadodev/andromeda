package local.doja.voice;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Resolves authorized names and executes independent device orders off the UI
 * and audio threads. It retries only pre-write transport failures and never
 * persists addresses.
 *
 * Every accepted order is guaranteed to call back exactly once within
 * ORDER_BUDGET_MS, even if the Bluetooth stack never reports a connection,
 * discovery or write result. A voice turn must never wait on BLE past its own
 * deadline: an honest partial result is better than a stopped assistant.
 */
public final class LightCommandDispatcher implements AutoCloseable {
    public enum Status { COMPLETE_UNCONFIRMED, AUTHORIZATION_REQUIRED, UNSUPPORTED_ORDER,
        DEVICE_NOT_FOUND, TRANSPORT_ERROR, PARTIAL_OR_UNCERTAIN, BUSY, CLOSED }
    public interface Callback { void onComplete(Result result); }

    /** Whole-order budget, including name resolution and every retry. */
    private static final long ORDER_BUDGET_MS = 28000L;

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
    private final boolean preferCached;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler guard = new Handler(Looper.getMainLooper());
    private volatile boolean busy;
    private boolean closed;
    private Callback currentCallback;
    private volatile int targetCount;
    private volatile int currentIndex;
    private volatile int acceptedSoFar;
    private volatile int completedTargets;
    private volatile boolean partial;
    private volatile String failureDetail = "";

    public LightCommandDispatcher(Context context) {
        // The two authorised lamps keep a stable address in the local store.
        // Scanning first costs up to 15 s and races the connection; the store
        // is only bypassed when a target is still unknown.
        this(context, true);
    }

    public LightCommandDispatcher(Context context, boolean preferCached) {
        if (context == null) throw new IllegalArgumentException("Falta el contexto de luces");
        this.context = context.getApplicationContext();
        this.preferCached = preferCached;
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
        currentCallback = callback;
        targetCount = commands.size();
        currentIndex = 0;
        acceptedSoFar = 0;
        completedTargets = 0;
        partial = false;
        failureDetail = "";
        guard.postDelayed(this::expire, ORDER_BUDGET_MS);
        try {
            new LightDeviceResolver(context, preferCached).resolve(command, resolved -> {
                if (!resolved.isResolved()) {
                    finish(new Result(Status.DEVICE_NOT_FOUND, commands.size(), 0, 0, resolved.status.name()));
                    return;
                }
                execute(() -> writeNext(commands, 0, resolved.devices, 0));
            });
        } catch (RuntimeException failure) {
            finish(new Result(Status.TRANSPORT_ERROR, commands.size(), 0, 0, "resolver_unavailable"));
        }
    }

    private void writeNext(List<LightCommand> commands, int index, Map<LightTarget, BluetoothDevice> devices,
            int acceptedFrames) {
        if (!busy) return;
        if (closed) { finish(new Result(Status.PARTIAL_OR_UNCERTAIN, commands.size(), index, acceptedFrames, "dispatcher_closed")); return; }
        if (index >= commands.size()) {
            Status status = !partial ? Status.COMPLETE_UNCONFIRMED
                : completedTargets == 0 && acceptedFrames == 0 ? Status.TRANSPORT_ERROR
                : Status.PARTIAL_OR_UNCERTAIN;
            // Only a fully accepted power order updates the local best-effort state.
            if (!partial) {
                for (LightCommand command : commands)
                    if (command.kind == LightCommand.Kind.POWER)
                        LightStateStore.record(context, command.target, command.powerOn);
            }
            finish(new Result(status, commands.size(), completedTargets, acceptedFrames,
                partial ? failureDetail : "accepted_by_gatt_stack"));
            return;
        }
        currentIndex = index;
        acceptedSoFar = acceptedFrames;
        LightCommand command = commands.get(index);
        BluetoothDevice device = devices.get(command.target);
        if (device == null) {
            finish(new Result(Status.DEVICE_NOT_FOUND, commands.size(), index, acceptedFrames, "target_not_resolved"));
            return;
        }
        sendTargetAttempt(commands, index, devices, acceptedFrames, 1);
    }

    private void sendTargetAttempt(List<LightCommand> commands, int index,
            Map<LightTarget, BluetoothDevice> devices, int acceptedFrames, int attempt) {
        LightCommand command = commands.get(index);
        BluetoothDevice device = devices.get(command.target);
        if (command.target == LightTarget.LOTUS) {
            new LotusLampBleAdapter(context, device).send(command, result -> targetResult(
                commands, index, devices, acceptedFrames, attempt, result.status.name(),
                result.isSuccessfulSubmission(), result.acceptedFrameCount));
        } else {
            new MrStarBleAdapter(context, device).send(command, result -> targetResult(
                commands, index, devices, acceptedFrames, attempt, result.status.name(),
                result.isSuccessfulSubmission(), result.acceptedFrameCount));
        }
    }

    private void targetResult(List<LightCommand> commands, int index,
            Map<LightTarget, BluetoothDevice> devices, int acceptedFrames,
            int attempt, String status, boolean success, int acceptedThisAttempt) {
        int totalAccepted = acceptedFrames + acceptedThisAttempt;
        acceptedSoFar = totalAccepted;
        if (!success && LightBleRetryPolicy.mayRetry(status, acceptedThisAttempt, attempt)) {
            int nextAttempt = attempt + 1;
            execute(() -> {
                SystemClock.sleep(LightBleRetryPolicy.backoffMs(nextAttempt));
                if (!busy) return;
                sendTargetAttempt(commands, index, devices, acceptedFrames, nextAttempt);
            });
            return;
        }
        if (!success) {
            partial = true;
            failureDetail = (attempt > 1 ? "retry_exhausted_" : "") + status;
        } else {
            completedTargets++;
        }
        // A failed lamp must not cancel the other one: continue with the
        // remaining target and report the mixed result as partial.
        execute(() -> {
            if (index + 1 < commands.size()) SystemClock.sleep(LightBleRetryPolicy.BETWEEN_TARGETS_MS);
            writeNext(commands, index + 1, devices, totalAccepted);
        });
    }

    /** Last-resort safety net: never leave the caller waiting on a silent stack. */
    private synchronized void expire() {
        if (!busy) return;
        Status status = completedTargets == 0 && acceptedSoFar == 0
            ? Status.TRANSPORT_ERROR : Status.PARTIAL_OR_UNCERTAIN;
        finish(new Result(status, targetCount, completedTargets, acceptedSoFar, "order_deadline_exceeded"));
    }

    private void execute(Runnable task) {
        // No new BLE session may start once the order was settled (for example
        // by the deadline guard); a late retry would collide with the next order.
        if (!busy) return;
        try {
            worker.execute(task);
        } catch (RejectedExecutionException closed) {
            finish(new Result(Status.CLOSED, targetCount, currentIndex, acceptedSoFar, "worker_closed"));
        }
    }

    private synchronized void finish(Result result) {
        if (!busy) return;
        busy = false;
        guard.removeCallbacksAndMessages(null);
        Callback callback = currentCallback;
        currentCallback = null;
        if (callback != null) callback.onComplete(result);
    }

    @Override public synchronized void close() {
        closed = true;
        worker.shutdownNow();
        finish(new Result(Status.CLOSED, targetCount, currentIndex, acceptedSoFar, "dispatcher_closed"));
    }
}
