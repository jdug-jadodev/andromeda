package local.doja.voice;

import java.util.List;

/** Pure protocol gate: exact captured frames, no Android, BLE or writes. */
public final class LotusLampProtocolTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Lotus protocol check " + checks); }
    static void checkHex(String expected, byte[] actual) { check(expected.equals(LotusLampProtocol.hex(actual))); }
    static void invalid(LightCommand command) {
        try { LotusLampProtocol.framesFor(command); throw new AssertionError("Accepted unsupported Lotus order"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    public static void main(String[] args) {
        checkHex("7E 07 05 03 F2 A3 F6 10 EF", LotusLampProtocol.color(LightColor.parseHex("#F2A3F6")));
        checkHex("7E 04 01 19 01 FF 02 01 EF", LotusLampProtocol.brightness(25));
        checkHex("7E 04 01 32 01 FF 02 01 EF", LotusLampProtocol.brightness(50));
        checkHex("7E 07 04 FF 00 01 02 01 EF", LotusLampProtocol.power(true));
        checkHex("7E 07 04 00 00 00 02 01 EF", LotusLampProtocol.power(false));

        List<byte[]> frames = LotusLampProtocol.framesFor(
            LightCommand.color(LightTarget.LOTUS, LightColor.parseHex("F2A3F6"), 25));
        check(frames.size() == 2);
        checkHex("7E 07 05 03 F2 A3 F6 10 EF", frames.get(0));
        checkHex("7E 04 01 19 01 FF 02 01 EF", frames.get(1));
        checkHex("7E 07 04 FF 00 01 02 01 EF", 
            LotusLampProtocol.framesFor(LightCommand.power(LightTarget.LOTUS, true)).get(0));
        checkHex("7E 04 01 32 01 FF 02 01 EF",
            LotusLampProtocol.framesFor(LightCommand.brightness(LightTarget.LOTUS, 50)).get(0));

        invalid(LightCommand.preset(LightTarget.LOTUS, "calido_tranquilo"));
        invalid(LightCommand.pattern(LightTarget.LOTUS, "gamer"));
        invalid(LightCommand.power(LightTarget.BOTH, true));
        System.out.println("Lotus protocol: " + checks + " assertions passed (captured frames, no radio or mutation).");
    }
}
