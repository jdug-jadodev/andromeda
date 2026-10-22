package local.doja.voice;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One-shot MR Star writer using only the locally observed GATT contract. */
public final class MrStarBleAdapter {
    /** Actual service observed on GATT--DEMO; public 2022 was absent. */
    public static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    public static final UUID NOTIFY_UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final long TIMEOUT_MS = 20000L;

    public enum Status {
        COMPLETE_UNCONFIRMED, INVALID_ORDER, BLUETOOTH_UNAVAILABLE, PERMISSION_DENIED,
        ADDRESS_INVALID, CONNECTION_FAILED, SERVICE_DISCOVERY_FAILED,
        CHARACTERISTIC_UNAVAILABLE, WRITE_REJECTED, WRITE_FAILED,
        PARTIAL_OR_UNCERTAIN, TIMEOUT
    }

    public interface Callback { void onComplete(Result result); }

    public static final class Result {
        public final Status status;
        public final int frameCount;
        public final int acceptedFrameCount;
        public final boolean deviceAcknowledged;

        private Result(Status status, int frameCount, int acceptedFrameCount) {
            this.status = status;
            this.frameCount = frameCount;
            this.acceptedFrameCount = acceptedFrameCount;
            this.deviceAcknowledged = false;
        }

        public boolean isSuccessfulSubmission() { return status == Status.COMPLETE_UNCONFIRMED; }
    }

    private final Context context;
    private final String address;
    private final BluetoothDevice providedDevice;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private List<byte[]> frames;
    private Callback callback;
    private int nextFrame;
    private int acceptedFrames;
    private boolean started;
    private boolean finished;

    public MrStarBleAdapter(Context context, String address) {
        if (context == null) throw new IllegalArgumentException("Falta el contexto BLE");
        this.context = context.getApplicationContext();
        this.address = address == null ? "" : address.trim();
        this.providedDevice = null;
    }

    public MrStarBleAdapter(Context context, BluetoothDevice device) {
        if (context == null || device == null) throw new IllegalArgumentException("Falta el dispositivo BLE");
        this.context = context.getApplicationContext();
        this.address = "";
        this.providedDevice = device;
    }

    /** One connection, one validated order, no retry. */
    public void send(LightCommand command, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Falta el recibo BLE");
        if (started) throw new IllegalStateException("El adaptador MR Star es de un solo uso");
        started = true;
        this.callback = callback;
        try { frames = new ArrayList<>(MrStarProtocol.framesFor(command)); }
        catch (IllegalArgumentException invalid) {
            finish(new Result(Status.INVALID_ORDER, 0, 0));
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            finish(new Result(Status.BLUETOOTH_UNAVAILABLE, frames.size(), 0));
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            finish(new Result(Status.PERMISSION_DENIED, frames.size(), 0));
            return;
        }
        final BluetoothDevice device;
        if (providedDevice != null) {
            device = providedDevice;
        } else {
            try { device = adapter.getRemoteDevice(address); }
            catch (IllegalArgumentException invalidAddress) {
                finish(new Result(Status.ADDRESS_INVALID, frames.size(), 0));
                return;
            } catch (SecurityException denied) {
                finish(new Result(Status.PERMISSION_DENIED, frames.size(), 0));
                return;
            }
        }
        try {
            gatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt value, int status, int state) {
                    if (finished) return;
                    if (state == BluetoothGatt.STATE_CONNECTED) {
                        if (!value.discoverServices()) finish(new Result(Status.SERVICE_DISCOVERY_FAILED, frames.size(), 0));
                    } else if (acceptedFrames > 0) {
                        finish(new Result(Status.PARTIAL_OR_UNCERTAIN, frames.size(), acceptedFrames));
                    } else finish(new Result(Status.CONNECTION_FAILED, frames.size(), 0));
                }

                @Override public void onServicesDiscovered(BluetoothGatt value, int status) {
                    if (finished) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(new Result(Status.SERVICE_DISCOVERY_FAILED, frames.size(), 0));
                        return;
                    }
                    BluetoothGattService service = value.getService(SERVICE_UUID);
                    writeCharacteristic = service == null ? null : service.getCharacteristic(WRITE_UUID);
                    notifyCharacteristic = service == null ? null : service.getCharacteristic(NOTIFY_UUID);
                    if (writeCharacteristic == null
                            || (writeCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0
                            || notifyCharacteristic == null
                            || (notifyCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                        finish(new Result(Status.CHARACTERISTIC_UNAVAILABLE, frames.size(), 0));
                        return;
                    }
                    nextFrame = 0;
                    acceptedFrames = 0;
                    writeNext();
                }

                @Override public void onCharacteristicWrite(BluetoothGatt value,
                        BluetoothGattCharacteristic characteristic, int status) {
                    if (finished || characteristic != writeCharacteristic) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(new Result(acceptedFrames == 0 ? Status.WRITE_FAILED : Status.PARTIAL_OR_UNCERTAIN,
                                frames.size(), acceptedFrames));
                        return;
                    }
                    writeNext();
                }
            }, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException denied) {
            finish(new Result(Status.PERMISSION_DENIED, frames.size(), 0));
            return;
        } catch (RuntimeException failure) {
            finish(new Result(Status.CONNECTION_FAILED, frames.size(), 0));
            return;
        }
        handler.postDelayed(() -> finish(new Result(
                acceptedFrames == 0 ? Status.TIMEOUT : Status.PARTIAL_OR_UNCERTAIN,
                frames.size(), acceptedFrames)), TIMEOUT_MS);
    }

    private void writeNext() {
        if (finished) return;
        if (nextFrame >= frames.size()) {
            finish(new Result(Status.COMPLETE_UNCONFIRMED, frames.size(), acceptedFrames));
            return;
        }
        byte[] frame = frames.get(nextFrame++);
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        writeCharacteristic.setValue(frame);
        boolean accepted;
        try { accepted = gatt != null && gatt.writeCharacteristic(writeCharacteristic); }
        catch (RuntimeException failure) { accepted = false; }
        if (!accepted) {
            finish(new Result(acceptedFrames == 0 ? Status.WRITE_REJECTED : Status.PARTIAL_OR_UNCERTAIN,
                    frames.size(), acceptedFrames));
            return;
        }
        acceptedFrames++;
    }

    private void finish(Result result) {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (gatt != null) {
            try { gatt.disconnect(); } catch (RuntimeException ignored) {}
            try { gatt.close(); } catch (RuntimeException ignored) {}
            gatt = null;
        }
        if (callback != null) callback.onComplete(result);
    }
}
