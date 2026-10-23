package local.doja.voice;

import java.util.List;

/** Pure MR Star protocol gate: captured frames only, no Android or BLE. */
public final class MrStarProtocolTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("MR Star protocol check " + checks); }
    static void checkHex(String expected, byte[] actual) { check(expected.equals(MrStarProtocol.hex(actual))); }
    static void invalid(LightCommand command) {
        try { MrStarProtocol.framesFor(command); throw new AssertionError("Accepted unvalidated MR Star order"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    public static void main(String[] args) {
        checkHex("BC 04 06 00 00 03 E8 00 00 55", MrStarProtocol.color(LightColor.parseHex("#FF0000")));
        checkHex("BC 04 06 00 78 03 E8 00 00 55", MrStarProtocol.color(LightColor.parseHex("#00FF00")));
        checkHex("BC 04 06 00 F0 03 E8 00 00 55", MrStarProtocol.color(LightColor.parseHex("#0000FF")));
        checkHex("BC 04 06 00 1E 03 E8 00 00 55", MrStarProtocol.color(LightColor.parseHex("#FF8000")));
        checkHex("BC 04 06 01 32 03 E8 00 00 55", MrStarProtocol.color(LightColor.parseHex("#FF00E6")));
        checkHex("BC 04 06 01 32 02 6D 00 00 55", MrStarProtocol.color(LightColor.parseHex("#F35CE3")));
        checkHex("BC 04 06 00 69 00 2F 00 00 55", MrStarProtocol.color(LightColor.parseHex("#FFFFFF")));
        checkHex("BC 05 06 01 0D 00 00 00 00 55", MrStarProtocol.brightness25());
        checkHex("BC 05 06 02 0B 00 00 00 00 55", MrStarProtocol.brightness(50));
        checkHex("BC 05 06 03 05 00 00 00 00 55", MrStarProtocol.brightness(75));
        checkHex("BC 05 06 03 E8 00 00 00 00 55", MrStarProtocol.brightness(100));
        checkHex("BC 01 01 01 55", MrStarProtocol.power(true));
        checkHex("BC 01 01 00 55", MrStarProtocol.power(false));
        List<byte[]> frames = MrStarProtocol.framesFor(
            LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#FF0000"), 25));
        check(frames.size() == 2);
        checkHex("BC 05 06 01 0D 00 00 00 00 55", frames.get(1));
        checkHex("BC 05 06 01 0D 00 00 00 00 55",
            MrStarProtocol.framesFor(LightCommand.brightness(LightTarget.MR_STAR, 25)).get(0));
        invalid(LightCommand.brightness(LightTarget.MR_STAR, 10));
        invalid(LightCommand.brightness(LightTarget.MR_STAR, 0));
        invalid(LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#000000")));
        invalid(LightCommand.color(LightTarget.MR_STAR, LightColor.parseHex("#808080")));
        invalid(LightCommand.color(LightTarget.LOTUS, LightColor.parseHex("#FF0000")));
        invalid(LightCommand.preset(LightTarget.MR_STAR, "calido_tranquilo"));
        System.out.println("MR Star protocol: " + checks + " assertions passed (captured frames, no radio or mutation).");
    }
}
