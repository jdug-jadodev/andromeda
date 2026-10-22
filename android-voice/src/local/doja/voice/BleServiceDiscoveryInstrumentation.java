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

/** One ephemeral BLE connection solely for public GATT service/property discovery.
 * Never reads characteristic values, writes, pairs, subscribes or exports address/name. */
public final class BleServiceDiscoveryInstrumentation extends Instrumentation {
    private static final String TARGET = "MELK-OA10 17";
    private static final UUID FFF0=UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF3=UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF4=UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner; private BluetoothGatt gatt; private ScanCallback scan;
    private boolean finished,found,connected,discovered,serviceFFF0,writeFFF3,notifyFFF4;
    private int scanResults,services,characteristics,status;
    private void finishResult(boolean passed){
        if(finished)return;finished=true;if(scanner!=null&&scan!=null)scanner.stopScan(scan);if(gatt!=null){gatt.disconnect();gatt.close();}
        handler.removeCallbacksAndMessages(null);Bundle result=new Bundle();result.putBoolean("passed",passed);
        result.putBoolean("target_found",found);result.putBoolean("connected",connected);result.putBoolean("services_discovered",discovered);
        result.putBoolean("service_fff0",serviceFFF0);result.putBoolean("write_no_response_fff3",writeFFF3);
        result.putBoolean("notify_fff4",notifyFFF4);result.putInt("scan_results_count",scanResults);
        result.putInt("service_count",services);result.putInt("characteristic_count",characteristics);result.putInt("gatt_status",status);
        result.putBoolean("characteristic_values_read",false);result.putBoolean("writes",false);result.putBoolean("pairing",false);
        result.putBoolean("addresses_exported",false);finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){super.onStart();
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();boolean permission=getTargetContext().checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")==0;
        if(adapter==null||!adapter.isEnabled()||!permission){finishResult(false);return;}scanner=adapter.getBluetoothLeScanner();if(scanner==null){finishResult(false);return;}
        scan=new ScanCallback(){@Override public void onScanResult(int type,ScanResult result){scanResults++;BluetoothDevice device=result==null?null:result.getDevice();String name=device==null?null:device.getName();if(name==null&&result!=null&&result.getScanRecord()!=null)name=result.getScanRecord().getDeviceName();
            if(!found&&TARGET.equals(name)){found=true;scanner.stopScan(this);gatt=device.connectGatt(getTargetContext(),false,new BluetoothGattCallback(){
                @Override public void onConnectionStateChange(BluetoothGatt value,int error,int state){status=error;if(state==BluetoothGatt.STATE_CONNECTED){connected=true;value.discoverServices();}else if(!discovered)finishResult(false);}
                @Override public void onServicesDiscovered(BluetoothGatt value,int error){status=error;if(error!=BluetoothGatt.GATT_SUCCESS){finishResult(false);return;}discovered=true;services=value.getServices().size();
                    for(BluetoothGattService service:value.getServices()){if(FFF0.equals(service.getUuid()))serviceFFF0=true;for(BluetoothGattCharacteristic c:service.getCharacteristics()){characteristics++;if(FFF3.equals(c.getUuid()))writeFFF3=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0;if(FFF4.equals(c.getUuid()))notifyFFF4=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0;}}
                    finishResult(true);}
            },BluetoothDevice.TRANSPORT_LE);}}
            @Override public void onScanFailed(int error){status=error;finishResult(false);}};
        try{scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),scan);}catch(RuntimeException denied){finishResult(false);return;}
        handler.postDelayed(()->finishResult(found&&discovered),15000L);
    }
}
