package local.doja.voice;

import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Last public-source result only, bounded RAM. No query/history/files or arbitrary URL fetch. */
final class WebSources {
    private static String last = "";
    private static long expires;
    static String query(String human) {
        Matcher match = Pattern.compile("(?:busca|investiga|consulta) (?:en (?:internet|la web)|por internet) (.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            .matcher(AgendaRequestGuard.clean(human));
        if (!match.matches() || match.group(1).length() > 160) throw new IllegalArgumentException("Explicit web query required");
        return match.group(1);
    }
    static String validate(String human, JSONObject event) throws Exception {
        query(human);
        JSONArray rows = event.getJSONArray("sources");
        if (rows.length() > 3) throw new IllegalArgumentException("Too many web sources");
        StringBuilder display = new StringBuilder(); String firstHost = "", firstExcerpt = "";
        List<String> hosts = new ArrayList<>(), excerpts = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String url = row.getString("url"), title = row.getString("title"), excerpt = row.getString("excerpt");
            URI parsed = new URI(url); String host = parsed.getHost();
            if (host != null) host = host.toLowerCase(Locale.ROOT);
            if (!"https".equals(parsed.getScheme()) || host == null || host.indexOf('.') < 0 || parsed.getUserInfo() != null
                    || parsed.getPort() != -1 && parsed.getPort() != 443 || url.length() > 1000 || title.length() > 120 || excerpt.length() > 350
                    || host.matches("[0-9.]+") || host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal"))
                throw new IllegalArgumentException("Invalid source URL");
            if (i == 0) {
                firstHost = host; int end = Math.min(300, excerpt.length());
                if (end > 0 && Character.isHighSurrogate(excerpt.charAt(end - 1))) end--;
                firstExcerpt = excerpt.substring(0, end);
            }
            hosts.add(host); excerpts.add(excerpt);
            display.append(i + 1).append(". ").append(title).append("\n").append(url).append("\n\n");
        }
        String answer = event.getString("text");
        if (event.has("web_answer")) {
            JSONObject binding = event.getJSONObject("web_answer");
            if (binding.length() != 5 || !(binding.get("version") instanceof Integer) || binding.getInt("version") != 1
                    || !(binding.get("source_index") instanceof Integer) || !(binding.get("mode") instanceof String)
                    || !(binding.get("evidence") instanceof String) || !(binding.get("answer_es") instanceof String))
                throw new IllegalArgumentException("Invalid web answer binding");
            WebAnswerPolicy.validate(binding.getString("mode"), binding.getInt("source_index"), binding.getString("evidence"),
                binding.getString("answer_es"), hosts, excerpts, answer);
        } else if (rows.length() == 0) {
            if (!(answer.equals("No obtuve resultados web utilizables. No tengo una respuesta verificada con esa búsqueda. ¿Necesitas algo más?")
                    || answer.equals("La búsqueda web no estuvo disponible. No consulté una fuente utilizable ni puedo confirmar esos datos. ¿Necesitas algo más?")))
                throw new IllegalArgumentException("Unsupported web success claim");
        } else if (!answer.equals("Un extracto de " + firstHost + " dice: " + firstExcerpt + " Es un resultado de búsqueda, no una verificación completa de la página. ¿Necesitas algo más?"))
            throw new IllegalArgumentException("Web answer does not match source excerpt");
        return display.length() == 0 ? "No se obtuvo una fuente utilizable en la última consulta." : display.toString();
    }
    static synchronized void remember(String value, long now) { last = value; expires = now + 3600000L; }
    static synchronized String describe(long now) {
        if (now >= expires) last = "";
        return last.isEmpty() ? "No hay fuentes recientes en RAM. Se pierden al cerrar el proceso." : last;
    }
}
