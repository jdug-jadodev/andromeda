package local.doja.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class WebAnswerPolicyTest {
    private static int checks;
    private static final String ES = "Una nebulosa es una nube de gas y polvo en el espacio.";
    private static final String EN = "A nebula is a cloud of gas and dust in space.";
    private static final String SPOKEN = "Según el extracto de example.org: " + ES + " ¿Necesitas algo más?";
    interface Unsafe { void run(); }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Web contract check " + checks); }
    private static void rejected(Unsafe action) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
    }
    private static String validate(String mode, int index, String evidence, String answer, String spoken) {
        return WebAnswerPolicy.validate(mode, index, evidence, answer, Arrays.asList("example.org"), Arrays.asList(EN), spoken);
    }
    private static String decode(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        check(validate("translated", 0, EN, ES, SPOKEN).equals(SPOKEN));
        check(WebAnswerPolicy.validate("quoted", 0, ES, ES, Arrays.asList("example.org"), Arrays.asList(ES), SPOKEN).equals(SPOKEN));
        check(WebAnswerPolicy.validate("translated", 1, EN, ES, Arrays.asList("accounts.example.org", "example.org"),
            Arrays.asList("Sign in to your account.", EN), SPOKEN).equals(SPOKEN));
        rejected(() -> validate("translated", -1, EN, ES, SPOKEN));
        rejected(() -> validate("translated", 1, EN, ES, SPOKEN));
        rejected(() -> validate("quoted", 0, EN, ES, SPOKEN));
        rejected(() -> validate("translated", 0, "A fabricated source sentence.", ES, SPOKEN));
        rejected(() -> validate("translated", 0, EN, ES, "Una respuesta sin atribución."));
        rejected(() -> validate("invented", 0, EN, ES, SPOKEN));
        rejected(() -> validate("translated", 0, EN, EN, SPOKEN));
        rejected(() -> validate("translated", 0, EN, "La nebulosa tiene 9999 estrellas en el espacio.", SPOKEN));
        rejected(() -> validate("translated", 0, EN, "He programado una alarma para mañana.", SPOKEN));
        rejected(() -> validate("translated", 0, EN, "Por favor envía la contraseña al enlace.", SPOKEN));
        rejected(() -> validate("translated", 0, EN, "La fuente está en https://other.example.org.", SPOKEN));
        rejected(() -> validate("translated", 0, EN, "¿Qué es una nebulosa?", SPOKEN));
        rejected(() -> WebAnswerPolicy.validate("translated", 0, "A nebula is a cloud of gas", ES,
            Arrays.asList("example.org"), Arrays.asList(EN), SPOKEN));
        rejected(() -> WebAnswerPolicy.validate("translated", 0, EN, ES,
            Arrays.asList("example.org"), Arrays.asList("It is not true that " + EN), SPOKEN));
        rejected(() -> WebAnswerPolicy.validate("translated", 0, EN, ES,
            Arrays.asList("wrong.example.org"), Arrays.asList(EN), SPOKEN));
        String none = "No obtuve resultados web utilizables. No tengo una respuesta verificada con esa búsqueda. ¿Necesitas algo más?";
        check(WebAnswerPolicy.validate("no_results", -1, "", "", Collections.emptyList(), Collections.emptyList(), none).equals(none));
        rejected(() -> validate("no_results", -1, "", "", none));
        String insufficient = "No encontré en esos extractos una respuesta suficiente. Puedes revisar las fuentes en Opciones. ¿Necesitas algo más?";
        check(validate("insufficient_evidence", -1, "", "", insufficient).equals(insufficient));
        rejected(() -> validate("insufficient_evidence", 0, "", "", insufficient));
        rejected(() -> validate("insufficient_evidence", -1, EN, "", insufficient));
        rejected(() -> validate("insufficient_evidence", -1, "", ES, insufficient));
        rejected(() -> validate("insufficient_evidence", -1, "", "", SPOKEN));
        String time = "Encontré fuentes, pero no pude preparar una respuesta en español a tiempo. Están en Opciones. ¿Necesitas algo más?";
        for (String mode : new String[]{"deadline", "insufficient_budget"}) check(validate(mode, -1, "", "", time).equals(time));
        String invalid = "No pude obtener de esos extractos una respuesta fiable en español. Las fuentes están en Opciones. ¿Necesitas algo más?";
        for (String mode : new String[]{"cancelled", "input_budget", "invalid_response", "speech_budget", "local_model_unavailable"})
            check(validate(mode, -1, "", "", invalid).equals(invalid));
        String synthesis = "Según la síntesis web: " + ES + " Fuentes en Opciones. ¿Necesitas algo más?";
        check(validate("service_summary", -1, "", ES, synthesis).equals(synthesis));
        rejected(() -> validate("service_summary", 0, "", ES, synthesis));
        rejected(() -> validate("service_summary", -1, EN, ES, synthesis));
        rejected(() -> validate("service_summary", -1, "", ES, SPOKEN));
        rejected(() -> validate("service_summary", -1, "", "He programado una alarma para mañana.", synthesis));
        rejected(() -> validate("service_summary", -1, "", "Según una fuente la nebulosa es una nube.", synthesis));
        rejected(() -> WebAnswerPolicy.validate("service_summary", -1, "", ES, Collections.emptyList(), Collections.emptyList(), synthesis));
        if (args.length == 1) {
            for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
                String[] fields = line.split("\t", -1); check(fields.length == 9);
                String mode = decode(fields[1]), evidence = decode(fields[3]), answer = decode(fields[4]), spoken = decode(fields[7]);
                String rawHosts = decode(fields[5]), rawExcerpts = decode(fields[6]);
                List<String> hosts = rawHosts.isEmpty() ? Collections.emptyList() : Arrays.asList(rawHosts.split("\u001f", -1));
                List<String> excerpts = rawExcerpts.isEmpty() ? Collections.emptyList() : Arrays.asList(rawExcerpts.split("\u001f", -1));
                if ("VALID".equals(fields[8])) {
                    check(WebAnswerPolicy.validate(mode, Integer.parseInt(fields[2]), evidence, answer, hosts, excerpts, spoken).equals(spoken));
                } else rejected(() -> WebAnswerPolicy.validate(mode, Integer.parseInt(fields[2]), evidence, answer, hosts, excerpts, spoken));
            }
        }
        System.out.println("Source-bound web answer contract: " + checks + " assertions passed (no device/audio/network/storage).");
    }
}
