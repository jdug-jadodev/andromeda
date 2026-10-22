package local.doja.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validated command frames for LotusLamp X MELK-OA10 17.
 *
 * This class has no Android or Bluetooth dependency. It only builds frames
 * observed in the local A56 capture; it never sends, retries, or logs them.
 * The model's captured OFF command is the one observed 8-byte variant; the
 * other validated commands are 9 bytes.
 */
public final class LotusLampProtocol {
    public static final int MAX_FRAME_LENGTH = 9;

    private LotusLampProtocol() {}

    public static byte[] color(LightColor color) {
        if (color == null) throw new IllegalArgumentException("Falta el color");
        return frame(0x7E, 0x07, 0x05, 0x03, color.red, color.green, color.blue, 0x10, 0xEF);
    }

    public static byte[] brightness(int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Brillo fuera de rango");
        return frame(0x7E, 0x04, 0x01, percent, 0x01, 0xFF, 0x02, 0x01, 0xEF);
    }

    public static byte[] power(boolean on) {
        return on
            ? frame(0x7E, 0x07, 0x04, 0xFF, 0x00, 0x01, 0x02, 0x01, 0xEF)
            : frame(0x7E, 0x07, 0x04, 0x00, 0x00, 0x02, 0x01, 0xEF);
    }

    /**
     * Converts one already parsed order into one or two writes. Presets and
     * patterns remain deliberately unsupported until their protocol is seen.
     */
    public static List<byte[]> framesFor(LightCommand command) {
        if (command == null) throw new IllegalArgumentException("Orden de luz incompleta");
        if (command.target != LightTarget.LOTUS) {
            throw new IllegalArgumentException("El transporte Lotus solo acepta objetivo Lotus");
        }
        List<byte[]> frames = new ArrayList<>();
        switch (command.kind) {
            case POWER:
                frames.add(power(command.powerOn));
                break;
            case COLOR:
                frames.add(color(command.color));
                if (command.brightnessPercent >= 0) frames.add(brightness(command.brightnessPercent));
                break;
            case PRESET:
            case PATTERN:
                throw new IllegalArgumentException("Preset/patrón Lotus aún no validado");
            default:
                throw new IllegalArgumentException("Tipo de orden Lotus no reconocido");
        }
        return Collections.unmodifiableList(frames);
    }

    public static String hex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder value = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) value.append(' ');
            value.append(String.format(java.util.Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return value.toString();
    }

    private static byte[] frame(int... values) {
        if (values.length < 8 || values.length > MAX_FRAME_LENGTH) throw new AssertionError("Lotus frame length");
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
