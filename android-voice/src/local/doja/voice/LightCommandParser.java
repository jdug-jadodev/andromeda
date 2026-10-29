package local.doja.voice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spanish light-intent parser. It only creates data; it never connects or writes. */
public final class LightCommandParser {
    private static final Pattern HEX=Pattern.compile("(?i)(?<![0-9a-f])#?([0-9a-f]{6})(?![0-9a-f])");
    private static final Pattern BRIGHTNESS_VALUE;
    private static final Map<String,LightColor> COLORS;
    private static final Set<String> PRESET_STOP = new HashSet<>(Arrays.asList(
        "la","el","los","las","un","una","unos","unas","de","del","en","con","sin","y","o","u","a",
        "para","por","que","color","colores","luz","luces","pon","poner","cambia","cambiar","usa","usar",
        "enciende","encender","apaga","apagar","brillo","todas","todos","ambas","ambos",
        "mueble","televisor","television","lotus","andromeda"));
    static {
        String[] names = {"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve",
            "diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve",
            "veinte","veintiuno","veintidos","veintitres","veinticuatro","veinticinco","veintiseis",
            "veintisiete","veintiocho","veintinueve","treinta","cuarenta","cincuenta","sesenta",
            "setenta","ochenta","noventa","cien","ciento"};
        Arrays.sort(names, Comparator.comparingInt(String::length).reversed());
        BRIGHTNESS_VALUE = Pattern.compile("(?<![0-9])(\\d{1,3}|(?:" + String.join("|", names)
            + ")(?:\\s+y\\s+(?:uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve))?)(?![0-9])");
    }
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
        return parse(text, Collections.<String,LightColor>emptyMap());
    }

    /** Adds locally saved color names to the built-in Spanish color list. */
    public static LightCommand parse(String text,Map<String,LightColor> saved){
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Orden de luz vacía");
        String value=normalize(text);
        if(!value.matches(".*\\b(pon|poner|cambia|cambiar|usa|usar|activa|activar|enciende|encender|prende|prender|apaga|apagar|desactiva|desactivar|on|off|ajusta|ajustar|sube|subir|baja|bajar)\\b.*"))
            throw new IllegalArgumentException("Falta una acción de luz");
        LightTarget target=target(value);
        int requestedBrightness=brightness(value);
        if(value.matches(".*\\b(apaga|apagar|desactiva|desactivar|off)\\b.*"))return LightCommand.power(target,false);
        if(value.matches(".*\\b(enciende|encender|prende|prender|on)\\b.*")&&
            !value.matches(".*\\b(color|rosa|rojo|verde|azul|blanco|amarillo|cian|magenta|morado|purpura|naranja)\\b.*")&&
            !HEX.matcher(value).find()&&savedColor(value,saved)==null)return LightCommand.power(target,true);
        Matcher hex=HEX.matcher(value);
        if(hex.find())return LightCommand.color(target,LightColor.parseHex(hex.group(1)),requestedBrightness);
        LightColor savedValue=savedColor(value,saved);
        if(savedValue!=null)return LightCommand.color(target,savedValue,requestedBrightness);
        for(Map.Entry<String,LightColor> entry:COLORS.entrySet())
            if(value.matches(".*\\b"+entry.getKey()+"\\b.*"))return LightCommand.color(target,entry.getValue(),requestedBrightness);
        if(requestedBrightness>=0)return LightCommand.brightness(target,requestedBrightness);
        if(value.matches(".*\\b(calido|calido tranquilo|tranquilo|relajante)\\b.*"))
            return LightCommand.preset(target,"calido_tranquilo");
        if(value.matches(".*\\b(gamer|gaming|videojuegos)\\b.*"))return LightCommand.pattern(target,"gamer");
        String closest = closestSavedColorName(value, saved);
        if (closest != null) return LightCommand.color(target, saved.get(closest), requestedBrightness);
        throw new IllegalArgumentException("Color o preset de luz no reconocido");
    }

    private static LightColor savedColor(String value,Map<String,LightColor> saved){
        if(saved==null||saved.isEmpty())return null;
        List<String> names=new ArrayList<>(saved.keySet());
        names.sort(Comparator.comparingInt(String::length).reversed());
        for(String key:names){
            List<String> aliases=presetAliases(key);
            for(String name:aliases){
                if(name.isEmpty()||name.length()>32||PRESET_STOP.contains(name))continue;
                if(value.matches(".*\\b"+Pattern.quote(name)+"\\b.*"))return saved.get(key);
            }
        }
        return null;
    }

    private static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
        return d[a.length()][b.length()];
    }

    /** Nearest saved color name by edit distance, to propose a close match when the
     *  exact name was misheard (e.g. "hizo" for "isa"). Null if none is close. */
    static String closestSavedColorName(String value, Map<String, LightColor> saved) {
        if (saved == null || saved.isEmpty()) return null;
        String[] words = value.split("\\s+");
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String key : saved.keySet()) {
            for (String alias : presetAliases(key)) {
                if (alias.isEmpty() || alias.length() > 32 || PRESET_STOP.contains(alias)) continue;
                for (String word : words) {
                    int distance = levenshtein(phonetic(word), phonetic(alias));
                    if (distance < bestDistance) { bestDistance = distance; best = key; }
                }
            }
        }
        if (best == null || bestDistance > 2) return null;
        return best;
    }

    /** Spanish phonetic key: the leading 'h' is silent and 'z' sounds like 's'. */
    private static String phonetic(String s) {
        if (s == null || s.isEmpty()) return s;
        String value = s.charAt(0) == 'h' ? s.substring(1) : s;
        return value.replace('z', 's');
    }

    private static List<String> presetAliases(String key){
        List<String> aliases=new ArrayList<>();
        String name=key==null?"":key.replace('_',' ').trim();
        addAlias(aliases,name);
        String[] prefixes={"color de ","color del ","color ","de la ","de el ","de "};
        for(String prefix:prefixes)
            if(name.startsWith(prefix))addAlias(aliases,name.substring(prefix.length()).trim());
        aliases.sort(Comparator.comparingInt(String::length).reversed());
        return aliases;
    }

    private static void addAlias(List<String> aliases,String value){
        if(!value.isEmpty()&&!aliases.contains(value))aliases.add(value);
    }

    private static int brightness(String value){
        Matcher marker=Pattern.compile("\\b(brillo|luminosidad)\\b").matcher(value);
        if(!marker.find())return -1;
        Matcher number=BRIGHTNESS_VALUE.matcher(value.substring(marker.end()));
        if(!number.find())throw new IllegalArgumentException("Indica el porcentaje de brillo");
        String token=number.group(1);
        int percent;
        if(token.matches("[0-9]+")) percent=Integer.parseInt(token);
        else if("cien".equals(token)||"ciento".equals(token)) percent=100;
        else {
            try { percent=TimerRequestGuard.amount(token); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Porcentaje de brillo no reconocido"); }
        }
        if(percent<0||percent>100)throw new IllegalArgumentException("Brillo fuera de rango");
        return percent;
    }

    private static LightTarget target(String value){
        boolean lotus=value.matches(".*\\b(lotus|lotuslamp|melk|luz mueble|mueble)\\b.*");
        boolean star=value.matches(".*\\b(mr star|gatt[- ]+demo|luz del televisor|luz del tv|luz tv|televisor|television|tv|tele)\\b.*");
        if(value.matches(".*\\b(ambas|ambos|las dos|las luces)\\b.*")||(lotus&&star)||(!lotus&&!star))return LightTarget.BOTH;
        return lotus?LightTarget.LOTUS:LightTarget.MR_STAR;
    }

    private static String normalize(String text){
        String value=Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return value.replaceAll("[¿?!,.;]"," ").replaceAll("\\s+"," ").trim();
    }
}
