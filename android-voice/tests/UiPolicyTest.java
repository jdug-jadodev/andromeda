package local.doja.voice;

/** Runs on the desktop JVM; no Android stubs, sleep, network or device required. */
public final class UiPolicyTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UiPolicy policy = new UiPolicy();
        check(!policy.optionsVisible(), "Options must start hidden");
        policy.showOptions(1000);
        check(policy.optionsDeadline() == 51000, "Timeout must be 50 seconds");
        check(!policy.expireOptions(50999), "Expired before deadline");
        check(policy.expireOptions(51000), "Did not expire at deadline");
        check(!policy.optionsVisible(), "Options still visible");
        policy.showOptions(100000); policy.interact(120000);
        check(policy.optionsDeadline() == 170000, "Human interaction did not renew timeout");
        check(!policy.expireOptions(150000), "Old deadline was not replaced");
        check(policy.expireOptions(170000), "New deadline did not expire");
        policy.interact(200000); check(!policy.optionsVisible(), "Interaction reopened hidden options");
        check(policy.optionsDeadline() == 0, "Hidden options retained a timer");
        check(!UiPolicy.showsFace(FaceState.IDLE), "Idle should show clock");
        check(!UiPolicy.showsFace(FaceState.MUTED), "Mute should show clock");
        check(!UiPolicy.showsFace(FaceState.READY), "Waiting for wake should show clock");
        check(UiPolicy.showsFace(FaceState.LISTENING), "Listening should show face");
        check(UiPolicy.showsFace(FaceState.THINKING), "Thinking should retain face");
        check(UiPolicy.showsFace(FaceState.SPEAKING), "Speaking should retain face");
        check(UiPolicy.parseColor("#12aB34") == 0xff12ab34, "RGB color mismatch");
        check(UiPolicy.parseColor("#000000") == 0xff000000, "Black must stay opaque");
        for (String value : new String[]{null, "red", "#FFF", "#00123456", "#GG0011", " #123456"}) {
            try { UiPolicy.parseColor(value); throw new AssertionError("Invalid color accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        check(FaceState.parse(null) == FaceState.ERROR, "Missing state must not imply listening");
        check(VoiceSessionPolicy.isWakeOnlyReady(true, true, false), "Valid wake-only peer rejected");
        check(!VoiceSessionPolicy.isWakeOnlyReady(false, true, false), "Legacy peer accepted for wake-only test");
        check(!VoiceSessionPolicy.isWakeOnlyReady(true, true, true), "AI peer accepted for wake-only test");
        check(!VoiceSessionPolicy.isWakeOnlyReady(true, false, false), "No wake detector accepted");
        check(VoiceSessionPolicy.wakeOnlyEventAllowed("state"), "State event rejected");
        check(VoiceSessionPolicy.wakeOnlyEventAllowed("transcript"), "Transcript event rejected");
        for (String type : new String[]{null, "timer", "cancel_timer", "list_timers", "stop_alarm", "say", "thinking"})
            check(!VoiceSessionPolicy.wakeOnlyEventAllowed(type), "Restricted mode accepted " + type);
        long now = 1760000000000L;
        WeatherData weather = new WeatherData(18, 3, now);
        check(weather.isFresh(now), "Fresh weather rejected");
        check(weather.isFresh(now + WeatherData.MAX_AGE_MS), "Freshness boundary rejected");
        check(!weather.isFresh(now + WeatherData.MAX_AGE_MS + 1), "Stale weather accepted");
        check(!weather.isFresh(now - 10 * 60000L - 1), "Future weather accepted after clock rollback");
        check("Nublado".equals(weather.condition()), "WMO mapping incorrect");
        check("Condición no disponible".equals(new WeatherData(18, -1, now).condition()), "Unknown WMO code invented condition");
        for (double temperature : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -51, 61}) {
            try { new WeatherData(temperature, 3, now); throw new AssertionError("Invalid temperature accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        System.out.println("UI/weather policy: " + assertions + " assertions passed (no device/network/audio).");
    }
}
