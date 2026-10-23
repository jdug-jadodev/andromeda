package local.doja.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Expands BOTH into independent typed orders; it never performs I/O. */
public final class LightCommandRouting {
    private LightCommandRouting() {}

    public static List<LightCommand> independent(LightCommand command) {
        if (command == null) throw new IllegalArgumentException("Orden de luz incompleta");
        List<LightCommand> result = new ArrayList<>();
        if (command.target == LightTarget.BOTH) {
            result.add(copyFor(command, LightTarget.LOTUS));
            result.add(copyFor(command, LightTarget.MR_STAR));
        } else result.add(command);
        return Collections.unmodifiableList(result);
    }

    private static LightCommand copyFor(LightCommand command, LightTarget target) {
        switch (command.kind) {
            case POWER: return LightCommand.power(target, command.powerOn);
            case COLOR: return LightCommand.color(target, command.color, command.brightnessPercent);
            case BRIGHTNESS: return LightCommand.brightness(target, command.brightnessPercent);
            case PRESET: return LightCommand.preset(target, command.name, command.brightnessPercent);
            case PATTERN: return LightCommand.pattern(target, command.name, command.brightnessPercent);
            default: throw new IllegalArgumentException("Tipo de orden desconocido");
        }
    }
}
