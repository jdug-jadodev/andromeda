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
    private static final long CONNECT_GUARD_MS = 1200L;

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
    private final boolean preferCached;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private EnumSet<LightTarget> wanted;
    private EnumMap<LightTarget, BluetoothDevice> found;
    private int scanResults;
    private boolean finished;
    private Callback resultCallback;

    public LightDeviceResolver(Context context) {
        this(context, false);
    }

    public LightDeviceResolver(Context context, boolean preferCached) {
        if (context == null) throw new IllegalArgumentException("Falta el contexto BLE");
        this.context = context.getApplicationContext();
        this.preferCached = preferCached;
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
        if (preferCached) {
            EnumMap<LightTarget, BluetoothDevice> cached = cached(adapter);
            if (cached.keySet().containsAll(wanted)) {
                finish(new Result(Status.RESOLVED, 0, Collections.unmodifiableMap(cached)));
                return;
            }
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
                    try { LightAddressStore.save(context, target, device.getAddress()); }
                    catch (SecurityException ignored) { /* The next scan can try again. */ }
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
        handler.postDelayed(() -> {
            if (finished) return;
            // A lamp that is on may stop advertising under its public name.
            // Try the last locally learned address before declaring it absent.
            EnumMap<LightTarget, BluetoothDevice> fallback = cached(adapter);
            if (fallback.keySet().containsAll(wanted))
                finish(new Result(Status.RESOLVED, scanResults, Collections.unmodifiableMap(fallback)));
            else finish(new Result(Status.TARGET_NOT_FOUND, scanResults,
                Collections.unmodifiableMap(new EnumMap<>(found))));
        }, TIMEOUT_MS);
    }

    private EnumMap<LightTarget, BluetoothDevice> cached(BluetoothAdapter adapter) {
        EnumMap<LightTarget, BluetoothDevice> result = new EnumMap<>(LightTarget.class);
        for (LightTarget target : wanted) {
            String address = LightAddressStore.get(context, target);
            if (address == null) continue;
            try { result.put(target, adapter.getRemoteDevice(address)); }
            catch (IllegalArgumentException | SecurityException invalid) { /* Scan will resolve it.   */ }
        }
        return result;
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
        // Some Android 10 stacks return GATT 133 when connectGatt starts too
        // soon after stopScan. This is a scheduling guard, not a retry: the
        // resolver still connects at most once.
        if (result.status == Status.RESOLVED)
            handler.postDelayed(() -> resultCallback.onResolved(result), CONNECT_GUARD_MS);
        else resultCallback.onResolved(result);
    }
}
