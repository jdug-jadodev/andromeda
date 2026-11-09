package local.doja.voice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/**
 * Salida de audio de la casa.
 *
 * Si hay audifonos conectados (cable, USB, Bluetooth o audifono auditivo) se
 * usa esa salida, porque es lo que la persona eligio. Si no hay ninguno se usa
 * el altavoz integrado, nunca el auricular de llamadas.
 */
final class AudioRoute {
    private AudioRoute() { }

    static AudioDeviceInfo preferred(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return null;
        AudioDeviceInfo speaker = null;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return device;
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    speaker = device;
                    break;
                default:
                    break;
            }
        }
        return speaker;
    }
}
