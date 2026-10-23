package local.doja.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locally captured MR Star frames for the observed GATT--DEMO hardware.
 *
 * The controller represents chromatic colors as 16-bit hue plus saturation
 * fields, not Lotus-style RGB bytes. The field layout is validated by primary
 * colors and an observed orange selector position. White uses the exact
 * low-saturation frame captured from the original app; pure grayscale/black
 * remain rejected.
 */
public final class MrStarProtocol {
    public static final int PREFIX = 0xBC;
    public static final int SUFFIX = 0x55;

    private MrStarProtocol() {}

    public static byte[] color(LightColor color) {
        if (color == null) throw new IllegalArgumentException("Falta el color");
        if (color.red == 255 && color.green == 255 && color.blue == 255)
            return hsvFrame(105, 47); // Captured visual white; not pure S=0.
        if (color.red == 0 && color.green == 0 && color.blue == 0)
            throw new IllegalArgumentException("Negro MR Star aún no capturado");
        int max = Math.max(color.red, Math.max(color.green, color.blue));
        int min = Math.min(color.red, Math.min(color.green, color.blue));
        if (max == min) throw new IllegalArgumentException("Gris MR Star aún no capturado");
        double delta = max - min;
        double hue;
        if (max == color.red) hue = 60.0 * ((color.green - color.blue) / delta);
        else if (max == color.green) hue = 60.0 * (2.0 + (color.blue - color.red) / delta);
        else hue = 60.0 * (4.0 + (color.red - color.green) / delta);
        if (hue < 0) hue += 360.0;
        int hueDegrees = ((int) Math.round(hue)) % 360;
        int saturation = (int) Math.round((delta / max) * 1000.0);
        return hsvFrame(hueDegrees, saturation);
    }

    /** Builds the observed BC 04 hue/saturation command; values are not RGB bytes. */
    static byte[] hsvFrame(int hueDegrees, int saturation) {
        if (hueDegrees < 0 || hueDegrees > 359) throw new IllegalArgumentException("Tono MR Star fuera de rango");
        if (saturation < 0 || saturation > 1000) throw new IllegalArgumentException("Saturación MR Star fuera de rango");
        return command(0x04, (hueDegrees >> 8) & 0xFF, hueDegrees & 0xFF,
            (saturation >> 8) & 0xFF, saturation & 0xFF, 0x00, 0x00);
    }

    /** The only brightness point captured without ambiguity: the device's 25% UI position. */
    public static byte[] brightness25() {
        return command(0x05, 0x01, 0x0D, 0x00, 0x00, 0x00, 0x00);
    }

    public static byte[] brightness(int percent) {
        switch (percent) {
            case 25: return command(0x05, 0x01, 0x0D, 0x00, 0x00, 0x00, 0x00);
            case 50: return command(0x05, 0x02, 0x0B, 0x00, 0x00, 0x00, 0x00);
            case 75: return command(0x05, 0x03, 0x05, 0x00, 0x00, 0x00, 0x00);
            case 100: return command(0x05, 0x03, 0xE8, 0x00, 0x00, 0x00, 0x00);
            default: throw new IllegalArgumentException("Brillo MR Star no capturado: usa 25, 50, 75 o 100");
        }
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
            case BRIGHTNESS:
                frames.add(brightness(command.brightnessPercent));
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
