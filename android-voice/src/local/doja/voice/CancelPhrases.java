package local.doja.voice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Frase de cancelación compartida por todos los diálogos (espejo de backend/cancel_phrases.py). */
final class CancelPhrases {
    private static final Set<String> PHRASES = new HashSet<>(Arrays.asList(
        "cancela", "cancelar", "cancelalo", "cancelala", "cancelame",
        "cancela eso", "cancela esto", "cancela lo",
        "cancela el aviso", "cancela el recordatorio", "cancela la alarma",
        "cancela el temporizador", "cancela el ajuste", "cancela el volumen",
        "cancela la orden", "dejalo", "dejala", "dejalo asi",
        "olvidalo", "olvidala", "olvidalo ya", "detente", "deten", "detente ya",
        "parate", "para ya", "no sigas", "aborta"));

    private CancelPhrases() { }

    static boolean isCancel(String text) {
        if (text == null) return false;
        String plain = TimerRequestGuard.normalize(text)
            .replaceAll("^(?:andromeda[ ,]+)?(?:por favor[ ,]+)?", "")
            .replaceAll("^[ .,!\\u00a1\\u00bf?]+|[ .,!\\u00a1\\u00bf?]+$", "");
        if (PHRASES.contains(plain)) return true;
        // "cancélalo ya" / "cancelar por favor"; se comprueba la lista antes
        // para no romper "para ya".
        return PHRASES.contains(plain.replaceAll("\\s+(?:ya|por favor)$", ""));
    }
}
