package local.doja.voice;

import android.app.Activity;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/** One direct, ephemeral BLE connection using a locally supplied address.
 * Enumerates only public GATT structure; never reads values, writes, pairs or exports the address. */
public final class BleAddressServiceDiscoveryInstrumentation extends Instrumentation {
    private static final UUID FFF0=UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF3=UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF4=UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String targetAddress;
    private BluetoothGatt gatt;
    private boolean finished,addressValid,attempted,connected,discovered,serviceFFF0,writeFFF3,notifyFFF4;
    private int services,characteristics,status;

    private void finishResult(boolean passed){
        if(finished)return;finished=true;
        if(gatt!=null){gatt.disconnect();gatt.close();}
        handler.removeCallbacksAndMessages(null);
        Bundle result=new Bundle();
        result.putBoolean("passed",passed);
        result.putBoolean("target_address_supplied",targetAddress!=null&&!targetAddress.isEmpty());
        result.putBoolean("target_address_valid",addressValid);
        result.putBoolean("connection_attempted",attempted);
        result.putBoolean("connected",connected);
        result.putBoolean("services_discovered",discovered);
        result.putBoolean("service_fff0",serviceFFF0);
        result.putBoolean("write_no_response_fff3",writeFFF3);
        result.putBoolean("notify_fff4",notifyFFF4);
        result.putInt("service_count",services);
        result.putInt("characteristic_count",characteristics);
        result.putInt("gatt_status",status);
        result.putBoolean("characteristic_values_read",false);
        result.putBoolean("writes",false);
        result.putBoolean("pairing_requested",false);
        result.putBoolean("address_exported",false);
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    @Override public void onCreate(Bundle arguments){
        super.onCreate(arguments);
        targetAddress=arguments==null?null:arguments.getString("target_address");
        start();
    }

    @Override public void onStart(){
        super.onStart();
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        boolean permission=getTargetContext().checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")==0;
        if(adapter==null||!adapter.isEnabled()||!permission||targetAddress==null||targetAddress.isEmpty()){
            finishResult(false);return;
        }
        BluetoothDevice device;
        try{
            device=adapter.getRemoteDevice(targetAddress);
            addressValid=true;
            attempted=true;
            gatt=device.connectGatt(getTargetContext(),false,new BluetoothGattCallback(){
                @Override public void onConnectionStateChange(BluetoothGatt value,int error,int state){
                    status=error;
                    if(state==BluetoothGatt.STATE_CONNECTED){connected=true;value.discoverServices();}
                    else if(!discovered)finishResult(false);
                }
                @Override public void onServicesDiscovered(BluetoothGatt value,int error){
                    status=error;
                    if(error!=BluetoothGatt.GATT_SUCCESS){finishResult(false);return;}
                    discovered=true;
                    services=value.getServices().size();
                    for(BluetoothGattService service:value.getServices()){
                        if(FFF0.equals(service.getUuid()))serviceFFF0=true;
                        for(BluetoothGattCharacteristic c:service.getCharacteristics()){
                            characteristics++;
                            if(FFF3.equals(c.getUuid()))writeFFF3=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0;
                            if(FFF4.equals(c.getUuid()))notifyFFF4=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0;
                        }
                    }
                    finishResult(true);
                }
            },BluetoothDevice.TRANSPORT_LE);
        }catch(IllegalArgumentException invalid){
            finishResult(false);return;
        }catch(SecurityException denied){
            finishResult(false);return;
        }
        handler.postDelayed(()->finishResult(false),20000L);
    }
}
