package local.doja.voice;

/** Parser-only gate: no Android, BLE, microphone, model, network or writes. */
public final class LightCommandTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Light command check "+checks);}
    static void invalid(String text){try{LightCommandParser.parse(text);throw new AssertionError("Accepted invalid light command: "+text);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        LightCommand hex=LightCommandParser.parse("Pon las dos luces en #F2A3F6");
        check(hex.kind==LightCommand.Kind.COLOR&&hex.target==LightTarget.BOTH&&hex.color.equals(LightColor.parseHex("F2A3F6")));
        LightCommand white=LightCommandParser.parse("Pon Lotus en FFFFFF");
        check(white.target==LightTarget.LOTUS&&white.color.hex().equals("#FFFFFF"));
        LightCommand star=LightCommandParser.parse("Pon GATT--DEMO en rosa");
        check(star.target==LightTarget.MR_STAR&&star.color.hex().equals("#FF8FD0"));
        check(LightCommandParser.parse("Activa cálido tranquilo").kind==LightCommand.Kind.PRESET);
        check(LightCommandParser.parse("Pon patrón gamer").kind==LightCommand.Kind.PATTERN);
        check(LightCommand.color(LightTarget.BOTH,LightColor.parseHex("FFFFFF"),25).brightnessPercent==25);
        check(LightCommandParser.parse("Enciende las dos luces").powerOn);
        check(LightCommandParser.parse("Apaga Lotus").kind==LightCommand.Kind.POWER&&!LightCommandParser.parse("Apaga Lotus").powerOn);
        invalid("F2A3F6"); invalid("Pon luces en #FFF"); invalid("Dime qué color es F2A3F6"); invalid("Pon luces en chartreuse");
        System.out.println("Light commands: "+checks+" assertions passed (parser-only, no radio or mutation).");
    }
}
