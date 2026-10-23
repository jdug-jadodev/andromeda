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
        check(LightCommandParser.parse("Enciende la luz mueble").target==LightTarget.LOTUS);
        check(LightCommandParser.parse("Pon azul en la luz del televisor").target==LightTarget.MR_STAR);
        check(LightCommandParser.parse("Apaga las dos luces").target==LightTarget.BOTH);
        check(!LightCommandParser.parse("Apaga la del televisor").powerOn&&LightCommandParser.parse("Apaga la del televisor").target==LightTarget.MR_STAR);
        check(LightCommandParser.parse("Enciende la del mueble").powerOn&&LightCommandParser.parse("Enciende la del mueble").target==LightTarget.LOTUS);
        check(!LightCommandParser.parse("Apaga la tele").powerOn&&LightCommandParser.parse("Apaga la tele").target==LightTarget.MR_STAR);
        check(LightCommandParser.parse("Enciende las dos luces").target==LightTarget.BOTH);
        LightCommand brightness=LightCommandParser.parse("Sube el brillo de la luz del televisor al 25 por ciento");
        check(brightness.kind==LightCommand.Kind.BRIGHTNESS&&brightness.target==LightTarget.MR_STAR&&brightness.brightnessPercent==25);
        LightCommand colorBrightness=LightCommandParser.parse("Pon azul en la luz mueble con brillo 50%");
        check(colorBrightness.kind==LightCommand.Kind.COLOR&&colorBrightness.brightnessPercent==50);
        check(LightCommandParser.parse("Pon azul en la luz mueble").brightnessPercent==-1);
        check(LightCommandParser.parse("Sube el brillo de la luz del televisor al cincuenta por ciento").brightnessPercent==50);
        check(LightCommandParser.parse("Pon el brillo de la luz del televisor al setenta y cinco por ciento").brightnessPercent==75);
        check(LightCommandParser.parse("Pon el brillo de la luz mueble al cien por ciento").brightnessPercent==100);
        LightCommand saved=LightCommandParser.parse("Pon las luces de color isa",
            java.util.Collections.singletonMap("isa",LightColor.parseHex("#123456")));
        check(saved.target==LightTarget.BOTH&&saved.color.equals(LightColor.parseHex("#123456")));
        LightCommand savedStoredKey=LightCommandParser.parse("Pon el color isa",
            java.util.Collections.singletonMap("color_de_isa",LightColor.parseHex("#FF00E6")));
        check(savedStoredKey.target==LightTarget.BOTH&&savedStoredKey.color.equals(LightColor.parseHex("#FF00E6")));
        check(LightCommandExecutionPolicy.rejection(savedStoredKey)==null);
        LightCommand savedMulti=LightCommandParser.parse("Pon las dos luces de color azul marino",
            java.util.Collections.singletonMap("azul_marino",LightColor.parseHex("#001122")));
        check(savedMulti.target==LightTarget.BOTH&&savedMulti.color.equals(LightColor.parseHex("#001122")));
        LightCommand savedShort=LightCommandParser.parse("Pon las luces de color isa",
            java.util.Collections.singletonMap("color_de_isa",LightColor.parseHex("#FF00E6")));
        check(savedShort.target==LightTarget.BOTH&&savedShort.color.equals(LightColor.parseHex("#FF00E6")));
        LightCommand savedFull=LightCommandParser.parse("Pon las luces de color de isa",
            java.util.Collections.singletonMap("color_de_isa",LightColor.parseHex("#FF00E6")));
        check(savedFull.target==LightTarget.BOTH&&savedFull.color.equals(LightColor.parseHex("#FF00E6")));
        invalid("F2A3F6"); invalid("Pon luces en #FFF"); invalid("Dime qué color es F2A3F6"); invalid("Pon luces en chartreuse");
        System.out.println("Light commands: "+checks+" assertions passed (parser-only, no radio or mutation).");
    }
}
