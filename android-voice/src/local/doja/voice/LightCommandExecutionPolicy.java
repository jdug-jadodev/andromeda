package local.doja.voice;

/** Preflight gate: rejects any target whose protocol is not locally validated. */
public final class LightCommandExecutionPolicy {
    private LightCommandExecutionPolicy() {}

    /** Returns null when every independent target can be executed safely. */
    public static String rejection(LightCommand command) {
        try {
            for (LightCommand independent : LightCommandRouting.independent(command)) {
                if (independent.target == LightTarget.LOTUS) LotusLampProtocol.framesFor(independent);
                else if (independent.target == LightTarget.MR_STAR) MrStarProtocol.framesFor(independent);
                else throw new IllegalArgumentException("Objetivo de luz no separado");
            }
            return null;
        } catch (IllegalArgumentException invalid) {
            return invalid.getMessage();
        }
    }

    public static boolean canExecute(LightCommand command) { return rejection(command) == null; }
}
