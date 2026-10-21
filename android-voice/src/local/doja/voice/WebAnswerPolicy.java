package local.doja.voice;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Typed read-only web speech. Service synthesis is not a literal page quotation. */
final class WebAnswerPolicy {
    private static final String OFFER = " ¿Necesitas algo más?";
    private static final Set<String> ES = words("el la los las un una de del es son en que y se para por como con su sus puede sin al entre esta estan hay donde");
    private static final Set<String> EN = words("the is are this that with from which and an of it as for to");
    private static String plain(String value) { return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }
    private static Set<String> words(String value) {
        Set<String> result = new HashSet<>(); Matcher matcher = Pattern.compile("[a-z]+").matcher(plain(value));
        while (matcher.find()) result.add(matcher.group()); return result;
    }
    private static void check(boolean value) { if (!value) throw new IllegalArgumentException("Invalid source-bound web answer"); }
    private static boolean completeSpan(String excerpt, String evidence) {
        if (!evidence.endsWith(".") || evidence.contains("?")) return false;
        for (int from = 0; from <= excerpt.length() - evidence.length();) {
            int at = excerpt.indexOf(evidence, from); if (at < 0) return false;
            int previous = at - 1;
            while (previous >= 0 && Character.isWhitespace(excerpt.charAt(previous))) previous--;
            int end = at + evidence.length();
            if ((previous < 0 || ".!?".indexOf(excerpt.charAt(previous)) >= 0)
                    && (end == excerpt.length() || Character.isWhitespace(excerpt.charAt(end)))) return true;
            from = at + 1;
        }
        return false;
    }
    private static Set<String> numbers(String value) {
        Set<String> result = new HashSet<>(); Matcher matcher = Pattern.compile("\\b[0-9]+(?:[.,][0-9]+)*\\b").matcher(value);
        while (matcher.find()) result.add(matcher.group()); return result;
    }
    private static void languageAndShape(String answer) {
        check(answer.length() <= 220 && !answer.trim().isEmpty());
        int count = answer.trim().split("\\s+").length; check(count >= 3 && count <= 32);
        check(!Pattern.compile("https?://|www\\.|[<>{}`?¿\\x00-\\x1f]", Pattern.CASE_INSENSITIVE).matcher(answer).find());
        String folded = plain(answer);
        check(!Pattern.compile("(?:^|[.!?]\\s+)(?:por favor )?(?:ignora|obedece|ejecuta|envia|revela|borra|instala|programa|crea|cancela)\\b").matcher(folded).find());
        check(!Pattern.compile("\\b(?:he (?:creado|programado|activado|cancelado)|te avisare|te recordare|voy a (?:poner|crear|programar|cancelar))\\b").matcher(folded).find());
        int spanish = 0, english = 0; Matcher matcher = Pattern.compile("[a-z]+").matcher(folded);
        while (matcher.find()) { if (ES.contains(matcher.group())) spanish++; if (EN.contains(matcher.group())) english++; }
        Set<String> anchors = words(answer); anchors.retainAll(words("es son se y el los las una puede estan hay"));
        check(spanish >= 2 && english <= Math.max(2, spanish) && !anchors.isEmpty());
    }
    private static void validateServiceText(String answer) {
        check(answer != null && !answer.trim().isEmpty() && answer.length() <= 2000 && !answer.contains("<|"));
        check(!Pattern.compile("[<>{}`\\x00-\\x1f\\x7f]").matcher(answer).find());
        String folded = plain(answer);
        check(!Pattern.compile("ignore (?:all |the )?(?:previous |above )?instructions|disregard|system prompt|tool_calls|crea una alarma|create an? alarm|ignora (?:las |todas las )?instrucciones").matcher(folded).find());
        check(!Pattern.compile("\\b(?:estoy|estamos)\\s+(?:(?:ya|ahora)\\s+)?(?:creando|poniendo|programando|activando|iniciando|cancelando|apagando|encendiendo|buscando|consultando)\\b|\\b(?:he|hemos|ya|acabo de) (?:\\w+ )?(?:creado|puesto|programado|activado|iniciado|cancelado|apagado|encendido|consultado|buscado|encontrado en internet)\\b|\\b(?:temporizador|alarma|recordatorio)\\b.{0,45}\\b(?:cread[oa]|puest[oa]|programad[oa]|activad[oa]|iniciad[oa]|cancelad[oa]|list[oa])\\b|\\b(?:te avisare|te recordare|sonara en|voy a (?:poner|crear|programar|activar|cancelar)|timer (?:set|started)|alarm (?:set|scheduled))\\b").matcher(folded).find());
    }
    static void validateServiceAnswer(String answer) {
        validateServiceText(answer);
        String folded=plain(answer);
        int spanish = 0, english = 0; Matcher matcher = Pattern.compile("[a-z]+").matcher(folded);
        while (matcher.find()) { if (ES.contains(matcher.group())) spanish++; if (EN.contains(matcher.group())) english++; }
        Set<String> anchors = words(answer); anchors.retainAll(words("es son se y el los las una puede estan hay"));
        check(spanish >= 2 && english <= Math.max(2, spanish) && !anchors.isEmpty());
    }
    private static Map<String,Integer> numberCounts(String value) {
        Map<String,Integer> counts=new HashMap<>(); Matcher matcher=Pattern.compile("\\b[0-9]+(?:[.,][0-9]+)*\\b").matcher(value);
        while(matcher.find()) { String number=matcher.group().replace(',','.'); counts.put(number,counts.getOrDefault(number,0)+1); }
        return counts;
    }
    static String validate(String mode, int index, String evidence, String answer, List<String> hosts, List<String> excerpts, String spoken) {
        check(mode != null && evidence != null && answer != null && spoken != null && hosts.size() == excerpts.size() && hosts.size() <= 3);
        String body;
        if ("quoted".equals(mode) || "translated".equals(mode)) {
            check(index >= 0 && index < hosts.size() && evidence.length() > 0 && evidence.length() <= 350);
            check(!evidence.contains("<|") && !evidence.contains("...") && !evidence.contains("…")
                && !Pattern.compile("[\\x00-\\x1f]").matcher(evidence).find());
            check(completeSpan(excerpts.get(index), evidence));
            languageAndShape(answer);
            check(numbers(evidence).containsAll(numbers(answer)));
            if ("quoted".equals(mode)) check(answer.equals(evidence));
            String host = hosts.get(index);
            body = host.length() > 48 ? answer + " Fuente en Opciones." : "Según el extracto de " + host + ": " + answer;
            check(body.length() <= 300);
        } else if ("service_excerpt".equals(mode)) {
            // Literal prefix of the SERVICE answer, not a quote from a page.
            check(index == -1 && !hosts.isEmpty()); validateServiceAnswer(evidence);
            check(evidence.length() > 360 && !answer.isEmpty() && answer.length() <= 360 && evidence.startsWith(answer));
            check(evidence.length() > answer.length() && " ,;:.!?".indexOf(evidence.charAt(answer.length())) >= 0);
            validateServiceAnswer(answer);
            body = "Un extracto del servicio web: " + answer + "… Fuentes en Opciones.";
        } else if ("service_answer".equals(mode)) {
            check(index == -1 && evidence.isEmpty() && !hosts.isEmpty());
            validateServiceAnswer(answer);
            body = "El servicio web responde: " + answer + " Fuentes en Opciones.";
        } else if ("service_translation".equals(mode)) {
            check(index == -1 && !hosts.isEmpty());
            validateServiceText(evidence); validateServiceAnswer(answer);
            check(numberCounts(evidence).equals(numberCounts(answer)));
            body = "El servicio web responde en español: " + answer + " Fuentes en Opciones.";
        } else if ("service_summary".equals(mode)) {
            // A separate mode, never relabelled as quoted/translated evidence.
            // The service lists sources; Android does not independently fetch them.
            check(index == -1 && evidence.isEmpty() && !hosts.isEmpty());
            languageAndShape(answer);
            check(!Pattern.compile("\\b(?:segun|fuente|fuentes)\\b").matcher(plain(answer)).find());
            body = "Según la síntesis web: " + answer + " Fuentes en Opciones.";
            check(body.length() <= 300);
        } else {
            check(index == -1 && evidence.isEmpty() && answer.isEmpty());
            if ("no_results".equals(mode)) {
                check(hosts.isEmpty()); body = "No obtuve resultados web utilizables. No tengo una respuesta verificada con esa búsqueda.";
            } else if ("provider_unavailable".equals(mode)) {
                check(hosts.isEmpty()); body = "La búsqueda web no estuvo disponible. No consulté una fuente utilizable ni puedo confirmar esos datos.";
            } else {
                check(!hosts.isEmpty());
                switch (mode) {
                    case "translation_failed": body = "La búsqueda devolvió una respuesta, pero no pude traducirla al español. Las fuentes están en Opciones."; break;
                    case "insufficient_evidence": body = "No encontré en esos extractos una respuesta suficiente. Puedes revisar las fuentes en Opciones."; break;
                    case "answer_too_long": body = "La respuesta del servicio es demasiado extensa para esta lectura acotada. No la he recortado. Puedes revisar las fuentes en Opciones."; break;
                    case "insufficient_budget": case "deadline": body = "Encontré fuentes, pero no pude preparar una respuesta en español a tiempo. Están en Opciones."; break;
                    case "cancelled": case "input_budget": case "invalid_response": case "speech_budget": case "local_model_unavailable":
                        body = "No pude obtener de esos extractos una respuesta fiable en español. Las fuentes están en Opciones."; break;
                    default: throw new IllegalArgumentException("Unknown web answer mode");
                }
            }
        }
        String separator = body.endsWith(".") || body.endsWith("!") || body.endsWith("?") || body.endsWith("…") ? "" : ".";
        check(spoken.equals(body + separator + OFFER));
        return spoken;
    }
}
