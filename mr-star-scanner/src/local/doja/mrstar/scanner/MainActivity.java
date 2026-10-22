package local.doja.mrstar.scanner;

import android.Manifest;
import android.app.Activity;
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
import android.util.Log;
import android.widget.TextView;
import java.util.Locale;
import java.util.UUID;

/** Standalone MR Star inventory: scan/connect/enumerate only, never read or write. */
public final class MainActivity extends Activity {
    private static final String TAG = "MrStarScanner";
    private static final String TARGET = "GATT--DEMO";
    private static final UUID SERVICE_2022 = UUID.fromString("00002022-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF3 = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF4 = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final int LOCATION_REQUEST = 41;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView view;
    private BluetoothLeScanner scanner;
    private ScanCallback scan;
    private BluetoothGatt gatt;
    private boolean finished, found, connected;
    private int scanResults;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new TextView(this);
        view.setTextSize(16f);
        view.setPadding(32, 32, 32, 32);
        setContentView(view);
        show("MR Star · solo lectura\n\nPreparando…");
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
        } else startInventory();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LOCATION_REQUEST || results.length == 0
                || results[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            report("PERMISSION_DENIED");
        } else startInventory();
    }

    private void startInventory() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { report("BLUETOOTH_UNAVAILABLE"); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { report("SCANNER_UNAVAILABLE"); return; }
        show("Buscando exactamente: " + TARGET + "\nSolo lectura; sin writes…");
        scan = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                scanResults++;
                BluetoothDevice device = result == null ? null : result.getDevice();
                String name = device == null ? null : device.getName();
                if (name == null && result != null && result.getScanRecord() != null)
                    name = result.getScanRecord().getDeviceName();
                if (!found && TARGET.equals(name)) connect(device);
            }

            @Override public void onScanFailed(int errorCode) { report("SCAN_FAILED_" + errorCode); }
        };
        try {
            scanner.startScan(null, new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scan);
        } catch (RuntimeException error) { report("SCAN_DENIED"); return; }
        handler.postDelayed(() -> {
            if (!found) report("TARGET_NOT_FOUND scan_results=" + scanResults);
        }, 15000L);
    }

    private void connect(BluetoothDevice device) {
        found = true;
        if (scanner != null && scan != null) scanner.stopScan(scan);
        show("Encontrado GATT--DEMO\nConectando para inventario GATT…");
        try {
            gatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt value, int error, int state) {
                    if (finished) return;
                    if (state == BluetoothGatt.STATE_CONNECTED) {
                        connected = true;
                        if (!value.discoverServices()) report("SERVICE_DISCOVERY_START_FAILED");
                    } else if (!finished) report("CONNECTION_FAILED_" + error);
                }

                @Override public void onServicesDiscovered(BluetoothGatt value, int error) {
                    if (finished) return;
                    if (error != BluetoothGatt.GATT_SUCCESS) { report("SERVICE_DISCOVERY_FAILED_" + error); return; }
                    StringBuilder services = new StringBuilder();
                    StringBuilder characteristics = new StringBuilder();
                    boolean service2022 = false, fff3 = false, fff4 = false;
                    int serviceCount = value.getServices().size();
                    int characteristicCount = 0;
                    for (BluetoothGattService service : value.getServices()) {
                        String serviceUuid = service.getUuid().toString().toLowerCase(Locale.ROOT);
                        if (services.length() > 0) services.append(',');
                        services.append(serviceUuid);
                        if (SERVICE_2022.equals(service.getUuid())) service2022 = true;
                        for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                            characteristicCount++;
                            String uuid = c.getUuid().toString().toLowerCase(Locale.ROOT);
                            if (characteristics.length() > 0) characteristics.append(',');
                            characteristics.append(uuid).append(':')
                                .append(String.format(Locale.ROOT, "%02x", c.getProperties()));
                            if (FFF3.equals(c.getUuid())) fff3 = true;
                            if (FFF4.equals(c.getUuid())) fff4 = true;
                        }
                    }
                    String result = "GATT_INVENTORY_OK services=" + serviceCount
                        + " characteristics=" + characteristicCount
                        + " service_2022=" + service2022
                        + " fff3=" + fff3 + " fff4=" + fff4
                        + " service_uuids=" + services
                        + " characteristic_uuids_properties=" + characteristics
                        + " values_read=false writes=false pairing=false";
                    report(result);
                }
            }, BluetoothDevice.TRANSPORT_LE);
        } catch (RuntimeException error) { report("CONNECTION_START_FAILED"); }
    }

    private void report(String message) {
        if (finished) return;
        finished = true;
        if (scanner != null && scan != null) scanner.stopScan(scan);
        if (gatt != null) { gatt.disconnect(); gatt.close(); gatt = null; }
        handler.removeCallbacksAndMessages(null);
        String safe = "MR_STAR_RESULT " + message;
        Log.i(TAG, safe);
        show("MR Star · resultado\n\n" + message.replace(" ", "\n"));
    }

    private void show(String text) { if (view != null) view.setText(text); }
}
