package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/** Read-only BLE discovery for the one user-selected LotusLamp target.
 * No connect, service discovery, characteristic read, pairing or write occurs here.
 * Device addresses and other private names never leave the phone. */
public final class BleDiscoveryInstrumentation extends Instrumentation {
    private static final String TARGET = "MELK-OA10 17";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private int seen, matches, failure = 0;
    private boolean finished;

    private void finishScan(boolean passed, boolean enabled, boolean permissions) {
        if (finished) return;
        finished = true;
        if (scanner != null && callback != null) scanner.stopScan(callback);
        handler.removeCallbacksAndMessages(null);
        Bundle result = new Bundle();
        result.putBoolean("passed", passed);
        result.putBoolean("bluetooth_enabled", enabled);
        result.putBoolean("permissions_granted", permissions);
        result.putBoolean("target_found", matches > 0);
        result.putInt("scan_results_count", seen);
        result.putInt("target_matches", matches);
        result.putInt("scan_failure_code", failure);
        result.putBoolean("connections", false);
        result.putBoolean("writes", false);
        result.putBoolean("pairing", false);
        result.putBoolean("addresses_exported", false);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        super.onStart();
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        final boolean permissions = getTargetContext().checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (adapter == null || !permissions || !adapter.isEnabled()) {
            finishScan(false, adapter != null && adapter.isEnabled(), permissions);
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { finishScan(false, true, true); return; }
        callback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                seen++;
                BluetoothDevice device = result == null ? null : result.getDevice();
                String name = device == null ? null : device.getName();
                if (name == null && result != null && result.getScanRecord() != null)
                    name = result.getScanRecord().getDeviceName();
                if (TARGET.equals(name)) matches++;
            }
            @Override public void onScanFailed(int errorCode) { failure = errorCode; }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), callback);
        } catch (RuntimeException denied) {
            finishScan(false, true, true); return;
        }
        handler.postDelayed(() -> finishScan(true, true, true), 8000L);
    }
}
