package local.doja.voice;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Resolves only the two authorized advertised names; it never reads or writes. */
public final class LightDeviceResolver {
    public static final String LOTUS_NAME = "MELK-OA10 17";
    public static final String MR_STAR_NAME = "GATT--DEMO";
    private static final long TIMEOUT_MS = 15000L;

    public enum Status { RESOLVED, INVALID_ORDER, BLUETOOTH_UNAVAILABLE, PERMISSION_DENIED, SCAN_UNAVAILABLE, SCAN_FAILED, TARGET_NOT_FOUND }
    public interface Callback { void onResolved(Result result); }

    public static final class Result {
        public final Status status;
        public final int scanResults;
        final Map<LightTarget, BluetoothDevice> devices;
        private Result(Status status, int scanResults, Map<LightTarget, BluetoothDevice> devices) {
            this.status = status;
            this.scanResults = scanResults;
            this.devices = devices;
        }
        public boolean isResolved() { return status == Status.RESOLVED; }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private EnumSet<LightTarget> wanted;
    private EnumMap<LightTarget, BluetoothDevice> found;
    private int scanResults;
    private boolean finished;
    private Callback resultCallback;

    public LightDeviceResolver(Context context) {
        if (context == null) throw new IllegalArgumentException("Falta el contexto BLE");
        this.context = context.getApplicationContext();
    }

    public void resolve(LightCommand command, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Falta el recibo de resolución");
        resultCallback = callback;
        try {
            wanted = EnumSet.noneOf(LightTarget.class);
            for (LightCommand independent : LightCommandRouting.independent(command)) wanted.add(independent.target);
        } catch (IllegalArgumentException invalid) {
            finish(new Result(Status.INVALID_ORDER, 0, Collections.emptyMap()));
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            finish(new Result(Status.BLUETOOTH_UNAVAILABLE, 0, Collections.emptyMap()));
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finish(new Result(Status.PERMISSION_DENIED, 0, Collections.emptyMap()));
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            finish(new Result(Status.SCAN_UNAVAILABLE, 0, Collections.emptyMap()));
            return;
        }
        found = new EnumMap<>(LightTarget.class);
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                scanResults++;
                BluetoothDevice device = result == null ? null : result.getDevice();
                String name = device == null ? null : device.getName();
                if (name == null && result != null && result.getScanRecord() != null)
                    name = result.getScanRecord().getDeviceName();
                LightTarget target = targetForName(name);
                if (target != null && wanted.contains(target) && !found.containsKey(target)) {
                    found.put(target, device);
                    if (found.keySet().containsAll(wanted))
                        finish(new Result(Status.RESOLVED, scanResults, Collections.unmodifiableMap(new EnumMap<>(found))));
                }
            }
            @Override public void onScanFailed(int errorCode) { finish(new Result(Status.SCAN_FAILED, scanResults, Collections.emptyMap())); }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scanCallback);
        } catch (RuntimeException denied) {
            finish(new Result(Status.SCAN_FAILED, scanResults, Collections.emptyMap()));
            return;
        }
        handler.postDelayed(() -> finish(new Result(Status.TARGET_NOT_FOUND, scanResults,
            Collections.unmodifiableMap(new EnumMap<>(found)))), TIMEOUT_MS);
    }

    private static LightTarget targetForName(String name) {
        if (name == null) return null;
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (LOTUS_NAME.equals(normalized)) return LightTarget.LOTUS;
        if (MR_STAR_NAME.equals(normalized)) return LightTarget.MR_STAR;
        return null;
    }

    private void finish(Result result) {
        if (finished) return;
        finished = true;
        if (scanner != null && scanCallback != null) scanner.stopScan(scanCallback);
        handler.removeCallbacksAndMessages(null);
        if (resultCallback == null) return;
        // Some Android 10 stacks return GATT 133 when connectGatt starts in
        // the same callback turn as stopScan. This is a scheduling guard, not
        // a retry: the resolver still connects at most once.
        if (result.status == Status.RESOLVED) handler.postDelayed(() -> resultCallback.onResolved(result), 350L);
        else resultCallback.onResolved(result);
    }
}
