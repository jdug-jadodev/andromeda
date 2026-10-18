package local.doja.voice;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Post-tool-call literal check only. Never routes conversation or executes actions. */
final class TimerRequestGuard {
    private static final Map<String, Integer> NUMBERS = new HashMap<>();
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
        "pon", "ponme", "crea", "inicia", "programa", "un", "una", "el", "la", "temporizador",
        "timer", "cuenta", "atras", "de", "en", "y", "por", "favor", "ahora"));
    private static final Pattern DURATION;
    static {
        String[] names = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
            "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve"};
        for (int n = 0; n < names.length; n++) NUMBERS.put(names[n], n);
        NUMBERS.put("un", 1); NUMBERS.put("una", 1); NUMBERS.put("veintiun", 21);
        String[] tens = {"treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
        for (int n = 0; n < tens.length; n++) NUMBERS.put(tens[n], 30 + 10 * n);
        List<String> alternatives = new ArrayList<>(NUMBERS.keySet());
        alternatives.sort(Comparator.comparingInt(String::length).reversed());
        String words = String.join("|", alternatives);
        DURATION = Pattern.compile("\\b([0-9]+|(?:" + words + ")(?:\\s+(?:" + words + "|y)){0,3})\\s+(segundos?|minutos?|horas?)\\b");
    }
    static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ");
    }
    private static int amount(String text) {
        if (text.matches("[0-9]+")) return Integer.parseInt(text);
        if (NUMBERS.containsKey(text)) return NUMBERS.get(text);
        String[] parts = text.split(" ");
        if (parts.length == 3 && "y".equals(parts[1]) && NUMBERS.containsKey(parts[0]) && NUMBERS.containsKey(parts[2])) {
            int ten = NUMBERS.get(parts[0]), one = NUMBERS.get(parts[2]);
            if (ten >= 30 && ten % 10 == 0 && one >= 1 && one <= 9) return ten + one;
        }
        throw new IllegalArgumentException("Duración ambigua");
    }
    static int seconds(String text) {
        if (text == null || text.isEmpty() || text.length() > 250 || text.contains("<|")
                || Pattern.compile("[\\\"'«»<>]|[0-9][.,][0-9]|-\\s*[0-9]").matcher(text).find())
            throw new IllegalArgumentException("Solicitud no válida");
        String plain = normalize(text).replaceAll("^[ .,!¡?¿]+|[ .,!¡?¿]+$", "")
            .replaceFirst("^andromeda[, ]+", "").replaceFirst("^por favor\\s+", "");
        if (!plain.matches("^(?:(?:pon|ponme|crea|inicia|programa)\\s+|(?:un |una )?(?:temporizador|timer|cuenta atras)\\b).*")
                || !Pattern.compile("\\b(?:temporizador|timer|cuenta atras)\\b").matcher(plain).find())
            throw new IllegalArgumentException("Falta petición explícita");
        Matcher found = DURATION.matcher(plain); StringBuffer remainder = new StringBuffer();
        Set<Character> units = new HashSet<>(); long total = 0; int count = 0;
        while (found.find()) {
            char unit = found.group(2).charAt(0); int value = amount(found.group(1));
            if (!units.add(unit) || value < 1 || value > 86400) throw new IllegalArgumentException("Duración no válida");
            total += value * (unit == 'h' ? 3600L : unit == 'm' ? 60L : 1L); count++;
            found.appendReplacement(remainder, " ");
        }
        found.appendTail(remainder);
        Matcher tokens = Pattern.compile("[\\p{L}\\p{N}_]+").matcher(remainder);
        while (tokens.find()) if (!ALLOWED.contains(tokens.group())) throw new IllegalArgumentException("Solicitud ambigua/negada");
        if (count == 0 || total < 1 || total > 86400) throw new IllegalArgumentException("Fuera de rango");
        return (int)total;
    }
}
