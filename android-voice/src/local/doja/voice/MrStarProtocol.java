package local.doja.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locally captured MR Star frames for the observed GATT--DEMO hardware.
 *
 * Only red, green, blue, brightness 25%, and power are currently reproducible. The
 * remaining colors/brightness values stay rejected until captured rather than
 * being guessed from a public library.
 */
public final class MrStarProtocol {
    public static final int PREFIX = 0xBC;
    public static final int SUFFIX = 0x55;

    private MrStarProtocol() {}

    public static byte[] color(LightColor color) {
        if (color == null) throw new IllegalArgumentException("Falta el color");
        if (color.equals(LightColor.parseHex("#FF0000"))) {
            return command(0x04, 0x00, 0x00, 0x03, 0xE8, 0x00, 0x00);
        }
        if (color.equals(LightColor.parseHex("#00FF00"))) {
            return command(0x04, 0x00, 0x78, 0x03, 0xE8, 0x00, 0x00);
        }
        if (color.equals(LightColor.parseHex("#0000FF"))) {
            return command(0x04, 0x00, 0xF0, 0x03, 0xE8, 0x00, 0x00);
        }
        throw new IllegalArgumentException("Color MR Star aún no capturado");
    }

    /** The only brightness point captured without ambiguity: the device's 25% UI position. */
    public static byte[] brightness25() {
        return command(0x05, 0x01, 0x0D, 0x00, 0x00, 0x00, 0x00);
    }

    public static byte[] brightness(int percent) {
        if (percent != 25) throw new IllegalArgumentException("Solo brillo 25% MR Star está validado");
        return brightness25();
    }

    public static byte[] power(boolean on) {
        return command(0x01, on ? 0x01 : 0x00);
    }

    public static List<byte[]> framesFor(LightCommand command) {
        if (command == null) throw new IllegalArgumentException("Orden de luz incompleta");
        if (command.target != LightTarget.MR_STAR) {
            throw new IllegalArgumentException("El transporte MR Star solo acepta objetivo MR Star");
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
                throw new IllegalArgumentException("Preset/patrón MR Star aún no capturado");
            default:
                throw new IllegalArgumentException("Tipo de orden MR Star no reconocido");
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

    private static byte[] command(int type, int... args) {
        if (args.length == 0 || args.length > 255) throw new IllegalArgumentException("Argumentos MR Star vacíos");
        byte[] result = new byte[args.length + 4];
        result[0] = (byte) PREFIX;
        result[1] = (byte) type;
        result[2] = (byte) args.length;
        for (int i = 0; i < args.length; i++) result[i + 3] = (byte) args[i];
        result[result.length - 1] = (byte) SUFFIX;
        return result;
    }
}
