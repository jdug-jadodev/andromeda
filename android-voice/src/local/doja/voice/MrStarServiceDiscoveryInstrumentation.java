package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;
import java.util.UUID;

/** Read-only GATT inventory for the exact MR Star advertised name. */
public final class MrStarServiceDiscoveryInstrumentation extends Instrumentation {
    private static final String TARGET = "GATT--DEMO";
    private static final UUID CANDIDATE_SERVICE = UUID.fromString("00002022-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF3 = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF4 = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ScanCallback scan;
    private boolean finished, found, connected, discovered, service2022, fff3, fff4;
    private int scanResults, services, characteristics, status;
    private String serviceSummary = "";
    private String characteristicSummary = "";

    private void finishResult(boolean passed) {
        if (finished) return;
        finished = true;
        if (scanner != null && scan != null) scanner.stopScan(scan);
        if (gatt != null) { gatt.disconnect(); gatt.close(); }
        handler.removeCallbacksAndMessages(null);
        Bundle result = new Bundle();
        result.putBoolean("passed", passed);
        result.putBoolean("target_found", found);
        result.putBoolean("connected", connected);
        result.putBoolean("services_discovered", discovered);
        result.putBoolean("service_2022", service2022);
        result.putBoolean("characteristic_fff3", fff3);
        result.putBoolean("characteristic_fff4", fff4);
        result.putInt("scan_results_count", scanResults);
        result.putInt("service_count", services);
        result.putInt("characteristic_count", characteristics);
        result.putInt("gatt_status", status);
        result.putString("service_uuid_summary", serviceSummary);
        result.putString("characteristic_uuid_summary", characteristicSummary);
        result.putBoolean("characteristic_values_read", false);
        result.putBoolean("writes", false);
        result.putBoolean("pairing", false);
        result.putBoolean("addresses_exported", false);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        super.onStart();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        boolean permission = getTargetContext().checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (adapter == null || !adapter.isEnabled() || !permission) { finishResult(false); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { finishResult(false); return; }
        scan = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                scanResults++;
                BluetoothDevice device = result == null ? null : result.getDevice();
                String name = device == null ? null : device.getName();
                if (name == null && result != null && result.getScanRecord() != null)
                    name = result.getScanRecord().getDeviceName();
                if (!found && TARGET.equals(name)) {
                    found = true;
                    scanner.stopScan(this);
                    gatt = device.connectGatt(getTargetContext(), false, new BluetoothGattCallback() {
                        @Override public void onConnectionStateChange(BluetoothGatt value, int error, int state) {
                            status = error;
                            if (state == BluetoothGatt.STATE_CONNECTED) {
                                connected = true;
                                value.discoverServices();
                            } else if (!discovered) finishResult(false);
                        }

                        @Override public void onServicesDiscovered(BluetoothGatt value, int error) {
                            status = error;
                            if (error != BluetoothGatt.GATT_SUCCESS) { finishResult(false); return; }
                            discovered = true;
                            StringBuilder serviceText = new StringBuilder();
                            StringBuilder characteristicText = new StringBuilder();
                            services = value.getServices().size();
                            for (BluetoothGattService service : value.getServices()) {
                                String serviceUuid = service.getUuid().toString().toLowerCase(Locale.ROOT);
                                if (serviceText.length() > 0) serviceText.append(',');
                                serviceText.append(serviceUuid);
                                if (CANDIDATE_SERVICE.equals(service.getUuid())) service2022 = true;
                                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                                    characteristics++;
                                    String uuid = c.getUuid().toString().toLowerCase(Locale.ROOT);
                                    if (characteristicText.length() > 0) characteristicText.append(',');
                                    characteristicText.append(uuid).append(':')
                                        .append(String.format(Locale.ROOT, "%02x", c.getProperties()));
                                    if (FFF3.equals(c.getUuid())) fff3 = true;
                                    if (FFF4.equals(c.getUuid())) fff4 = true;
                                }
                            }
                            serviceSummary = serviceText.toString();
                            characteristicSummary = characteristicText.toString();
                            finishResult(true);
                        }
                    }, BluetoothDevice.TRANSPORT_LE);
                }
            }

            @Override public void onScanFailed(int error) { status = error; finishResult(false); }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scan);
        } catch (RuntimeException denied) { finishResult(false); return; }
        handler.postDelayed(() -> finishResult(found && discovered), 15000L);
    }
}
