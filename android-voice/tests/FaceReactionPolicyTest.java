package local.doja.voice;

/** Local-only reaction and weather-face policy checks. */
public final class FaceReactionPolicyTest {
    public static void main(String[] args) {
        expect(FaceReaction.CRY, FaceReactionPolicy.forHuman("Estoy muy triste hoy"), "sad");
        expect(FaceReaction.DISGUST, FaceReactionPolicy.forHuman("Qué asco, eres horrible"), "ugly");
        expect(FaceReaction.CELEBRATE, FaceReactionPolicy.forHuman("Gané el premio"), "achievement");
        expect(FaceReaction.LOVE, FaceReactionPolicy.forHuman("Eres hermosa"), "nice");
        expect(FaceReaction.LOVE, FaceReactionPolicy.forHuman("Qué linda eres"), "nice word order");
        expect(FaceReaction.COOL, FaceReactionPolicy.forHuman("Muchas gracias"), "thanks");
        expect(FaceReaction.SILLY, FaceReactionPolicy.forHuman("Cuéntame un chiste"), "joke request");
        expect(FaceReaction.NONE, FaceReactionPolicy.forHuman("¿Cómo está el clima?"), "weather is not sentiment");

        expect(FaceReaction.SAD, FaceReactionPolicy.forAssistant("No pude encontrar eso"), "assistant failure");
        expect(FaceReaction.SILLY, FaceReactionPolicy.forAssistant("Te cuento un chiste: ..."), "assistant joke");
        expect(FaceReaction.NONE, FaceReactionPolicy.forAssistant("No hay problema"), "friendly negative");

        long now = System.currentTimeMillis();
        expect(FaceReaction.COLD, FaceReactionPolicy.forWeather("¿Cómo está el clima?", new WeatherData(5, 0, now)), "cold");
        expect(FaceReaction.HOT, FaceReactionPolicy.forWeather("Hace mucho calor", new WeatherData(31, 0, now)), "hot");
        expect(FaceReaction.FOG, FaceReactionPolicy.forWeather("¿Hay niebla?", new WeatherData(14, 45, now)), "fog");
        expect(FaceReaction.HOT, FaceReactionPolicy.forWeather("Qué calor", new WeatherData(20, 0, now)), "spoken heat");
        expect(FaceReaction.COLD, FaceReactionPolicy.forWeather("Qué frío", new WeatherData(20, 0, now)), "spoken cold");
        expect(FaceReaction.COLD, FaceReactionPolicy.forWeather("Clima", new WeatherData(15, 0, now)), "cold threshold");
        expect(FaceReaction.HOT, FaceReactionPolicy.forWeather("Clima", new WeatherData(23, 0, now)), "hot threshold");
        expect(FaceReaction.NONE, FaceReactionPolicy.forWeather("Hola", new WeatherData(5, 0, now)), "weather topic required");
        expect(FaceReaction.NONE, FaceReactionPolicy.forWeather("Clima", new WeatherData(20, 0, now)), "neutral weather");
        System.out.println("Face reaction policy tests passed.");
    }

    private static void expect(FaceReaction wanted, FaceReaction actual, String label) {
        if (wanted != actual) throw new AssertionError(label + ": expected " + wanted + " but was " + actual);
    }
}
