package local.doja.voice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local phrase-to-face policy. It never sends, stores or authorizes content. */
final class FaceReactionPolicy {
    private static final Pattern SAD = Pattern.compile("\\b(triste|tristeza|llorar|lloro|llorando|"
        + "deprimido|deprimida|fallecio|murio|perdi|perdida|enfermo|enferma|dolor|duelo|"
        + "me siento mal|mal dia|estoy solo|estoy sola)\\b");
    private static final Pattern ACHIEVEMENT = Pattern.compile("\\b(lo logre|logre|consegui|"
        + "gane|aprobe|termine|ascenso|exito|orgullo|buena noticia|felicidades|feliz|"
        + "celebrar|celebracion)\\b");
    private static final Pattern NICE = Pattern.compile("\\b(te quiero|te amo|eres (hermosa|linda|bonita|"
        + "la mejor)|me gustas|te admiro|que (linda|hermosa|bonita) eres)\\b");
    private static final Pattern UGLY = Pattern.compile("\\b(feo|fea|horrible|asqueroso|"
        + "asquerosa|estupida|estupido|idiota|tonta|tonto|odio|maldita|maldito|desagradable)\\b");
    private static final Pattern THANKS = Pattern.compile("\\b(gracias|te agradezco|agradecido|agradecida)\\b");
    private static final Pattern JOKE = Pattern.compile("\\b(chiste|chistes|adivinanza|adivinanzas)\\b");
    private static final Pattern WEATHER = Pattern.compile("\\b(clima|tiempo|temperatura|grados|"
        + "frio|calor|lluvia|llover|niebla|nublado|sol|fresco|caluroso)\\b");
    private static final Pattern FAILURE = Pattern.compile("\\b(no pude|no encontre|no se encontro|no se pudo|"
        + "no se ejecuto|no se obtuvo|no se completo|no se realizo|no esta validada|"
        + "no lo encontre|no la dare por hecha|no tengo acceso)\\b");

    private FaceReactionPolicy() {}

    static FaceReaction forHuman(String text) {
        String value = normalize(text);
        if (SAD.matcher(value).find()) return FaceReaction.CRY;
        if (UGLY.matcher(value).find()) return FaceReaction.DISGUST;
        if (ACHIEVEMENT.matcher(value).find()) return FaceReaction.CELEBRATE;
        if (NICE.matcher(value).find()) return FaceReaction.LOVE;
        if (THANKS.matcher(value).find()) return FaceReaction.COOL;
        if (JOKE.matcher(value).find()) return FaceReaction.SILLY;
        return FaceReaction.NONE;
    }

    static FaceReaction forAssistant(String text) {
        String value = normalize(text);
        if (FAILURE.matcher(value).find()) return FaceReaction.SAD;
        if (JOKE.matcher(value).find() || value.contains("jaja") || value.contains("jeje")) return FaceReaction.SILLY;
        return FaceReaction.NONE;
    }

    static boolean mentionsWeather(String text) {
        return WEATHER.matcher(normalize(text)).find();
    }

    static FaceReaction forWeather(String text, WeatherData weather) {
        if (weather == null || !mentionsWeather(text)) return FaceReaction.NONE;
        String value = normalize(text);
        if (weather.code == 45 || weather.code == 48 || value.contains("niebla")) return FaceReaction.FOG;
        if (value.contains("frio") || weather.celsius <= 15) return FaceReaction.COLD;
        if (value.contains("calor") || value.contains("caluroso") || weather.celsius >= 23) return FaceReaction.HOT;
        return FaceReaction.NONE;
    }

    private static String normalize(String text) {
        if (text == null || text.isEmpty() || text.length() > 500) return "";
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }
}
