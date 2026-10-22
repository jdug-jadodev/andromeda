package local.doja.voice;

import java.util.List;

/** Routing-only gate: BOTH creates two independent orders and no I/O. */
public final class LightCommandRoutingTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Light routing check " + checks); }

    public static void main(String[] args) {
        List<LightCommand> color = LightCommandRouting.independent(
            LightCommand.color(LightTarget.BOTH, LightColor.parseHex("#FF0000"), 25));
        check(color.size() == 2);
        check(color.get(0).target == LightTarget.LOTUS);
        check(color.get(1).target == LightTarget.MR_STAR);
        check(color.get(0).color.equals(color.get(1).color));
        check(color.get(0).brightnessPercent == 25 && color.get(1).brightnessPercent == 25);

        List<LightCommand> power = LightCommandRouting.independent(LightCommand.power(LightTarget.BOTH, false));
        check(power.size() == 2 && !power.get(0).powerOn && !power.get(1).powerOn);

        List<LightCommand> lotus = LightCommandRouting.independent(LightCommand.power(LightTarget.LOTUS, true));
        check(lotus.size() == 1 && lotus.get(0).target == LightTarget.LOTUS);
        System.out.println("Light routing: " + checks + " assertions passed (independent BOTH orders, no I/O).");
    }
}
