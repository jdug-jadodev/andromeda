package local.doja.voice;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spanish light-intent parser. It only creates data; it never connects or writes. */
public final class LightCommandParser {
    private static final Pattern HEX=Pattern.compile("(?i)(?<![0-9a-f])#?([0-9a-f]{6})(?![0-9a-f])");
    private static final Map<String,LightColor> COLORS;
    static{
        Map<String,LightColor> colors=new LinkedHashMap<>();
        colors.put("rosa",LightColor.parseHex("#FF8FD0"));
        colors.put("rosado",LightColor.parseHex("#FF8FD0"));
        colors.put("rojo",LightColor.parseHex("#FF0000"));
        colors.put("verde",LightColor.parseHex("#00FF00"));
        colors.put("azul",LightColor.parseHex("#0000FF"));
        colors.put("blanco",LightColor.parseHex("#FFFFFF"));
        colors.put("negro",LightColor.parseHex("#000000"));
        colors.put("amarillo",LightColor.parseHex("#FFFF00"));
        colors.put("cian",LightColor.parseHex("#00FFFF"));
        colors.put("magenta",LightColor.parseHex("#FF00FF"));
        colors.put("morado",LightColor.parseHex("#800080"));
        colors.put("purpura",LightColor.parseHex("#800080"));
        colors.put("naranja",LightColor.parseHex("#FFA500"));
        COLORS=Collections.unmodifiableMap(colors);
    }
    private LightCommandParser(){}

    public static LightCommand parse(String text){
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Orden de luz vacía");
        String value=normalize(text);
        if(!value.matches(".*\\b(pon|poner|cambia|cambiar|usa|usar|activa|activar|enciende|encender|prende|prender|apaga|apagar|desactiva|desactivar|on|off|ajusta|ajustar)\\b.*"))
            throw new IllegalArgumentException("Falta una acción de luz");
        LightTarget target=target(value);
        if(value.matches(".*\\b(apaga|apagar|desactiva|desactivar|off)\\b.*"))return LightCommand.power(target,false);
        if(value.matches(".*\\b(enciende|encender|prende|prender|on)\\b.*")&&
            !value.matches(".*\\b(color|rosa|rojo|verde|azul|blanco|amarillo|cian|magenta|morado|purpura|naranja)\\b.*")&&
            !HEX.matcher(value).find())return LightCommand.power(target,true);
        Matcher hex=HEX.matcher(value);
        if(hex.find())return LightCommand.color(target,LightColor.parseHex(hex.group(1)));
        for(Map.Entry<String,LightColor> entry:COLORS.entrySet())
            if(value.matches(".*\\b"+entry.getKey()+"\\b.*"))return LightCommand.color(target,entry.getValue());
        if(value.matches(".*\\b(calido|calido tranquilo|tranquilo|relajante)\\b.*"))
            return LightCommand.preset(target,"calido_tranquilo");
        if(value.matches(".*\\b(gamer|gaming|videojuegos)\\b.*"))return LightCommand.pattern(target,"gamer");
        throw new IllegalArgumentException("Color o preset de luz no reconocido");
    }

    private static LightTarget target(String value){
        boolean lotus=value.matches(".*\\b(lotus|lotuslamp|melk)\\b.*");
        boolean star=value.matches(".*\\b(mr star|gatt[- ]+demo)\\b.*");
        if(value.matches(".*\\b(ambas|ambos|las dos|las luces)\\b.*")||(lotus&&star)||(!lotus&&!star))return LightTarget.BOTH;
        return lotus?LightTarget.LOTUS:LightTarget.MR_STAR;
    }

    private static String normalize(String text){
        String value=Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return value.replaceAll("[¿?!,.;]"," ").replaceAll("\\s+"," ").trim();
    }
}
