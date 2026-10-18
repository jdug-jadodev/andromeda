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
        check(VoiceSessionPolicy.isConversationReady(5, true, false, true, true, false), "Conversation peer rejected");
        check(!VoiceSessionPolicy.isConversationReady(1, true, false, true, true, false), "Old protocol accepted");
        check(!VoiceSessionPolicy.isConversationReady(2, true, false, true, true, false), "Old memoryless peer accepted");
        check(!VoiceSessionPolicy.isConversationReady(3, true, false, true, true, false), "Old single-query flow accepted");
        check(!VoiceSessionPolicy.isConversationReady(4, true, false, true, true, false), "Old farewell-less peer accepted");
        check(!VoiceSessionPolicy.isConversationReady(5, true, false, true, true, true), "Actions accepted");
        check(!VoiceSessionPolicy.isConversationReady(5, true, true, true, true, false), "Wake-only overlap accepted");
        check(!VoiceSessionPolicy.isConversationReady(5, true, false, true, false, false), "Missing AI accepted");
        check(VoiceSessionPolicy.conversationEventAllowed("conversation_closed"), "Conversation close rejected");
        for (String type : new String[]{"timer", "cancel_timer", "list_timers", "stop_alarm", "tool_result"})
            check(!VoiceSessionPolicy.conversationEventAllowed(type), "Conversation accepted " + type);
        ConversationTurnPolicy turn = new ConversationTurnPolicy();
        String id = "0123456789abcdef0123456789abcdef";
        check(!turn.begin(id, 1, 1000, 31001, 2000), "Changed deadline accepted");
        check(!turn.begin(id, 1, 3000, 33000, 2000), "Future audio accepted");
        check(!turn.begin(id, 1, 1000, 31000, 31000), "Expired turn accepted");
        check(turn.begin(id, 1, 1000, 31000, 2000), "Turn rejected");
        check(!turn.speechStarted(id), "Unstarted speech reported delivered");
        check(!turn.begin(id, 2, 1000, 31000, 2000), "Concurrent turn accepted");
        check(!turn.acceptStart(id, 2000), "Unscheduled TTS accepted");
        check(!turn.acceptClose(id, 1, 1000, 31000, 2000), "Closed before farewell");
        check(!turn.acceptClose("wrong", 1, 1000, 31000, 2000), "Wrong close ID accepted");
        check(!turn.acceptClose(id, 2, 1000, 31000, 2000), "Wrong close sequence accepted");
        check(!turn.acceptClose(id, 1, 1001, 31000, 2000), "Changed close timestamp accepted");
        check(!turn.acceptClose(id, 1, 1000, 31001, 2000), "Changed close deadline accepted");
        check(!turn.acceptClose(id, 1, 1000, 31000, 31000), "Late close accepted");
        check(!turn.acceptReply("wrong", 1, 1000, 31000, 2000), "Wrong turn reply accepted");
        check(!turn.acceptReply(id, 2, 1000, 31000, 2000), "Wrong sequence accepted");
        check(!turn.acceptReply(id, 1, 1001, 31000, 2000), "Rebased deadline accepted");
        check(turn.acceptReply(id, 1, 1000, 31000, 2000), "Reply rejected");
        check(!turn.acceptClose(id, 1, 1000, 31000, 2000), "Scheduled speech replaced by close");
        check(!turn.acceptReply(id, 1, 1000, 31000, 2000), "Duplicate TTS scheduled");
        check(!turn.acceptStart(id, 31001), "Late TTS started");
        check(turn.acceptStart(id, 30000), "In-time TTS rejected");
        check(turn.speechStarted(id), "Accepted speech start missing");
        check(!turn.acceptStart(id, 30000), "Duplicate TTS start accepted");
        check(!turn.expiredBeforeStart(32000), "Already-started TTS expired");
        turn.clear();
        check(!turn.speechStarted(id), "Cancelled speech still reported started");
        check(!turn.acceptReply(id, 1, 1000, 31000, 2000), "Cancelled response accepted");
        check(!turn.begin(id, 1, 2000, 32000, 3000), "Replayed sequence accepted");
        check(turn.begin(id, 2, 2000, 32000, 3000), "Next turn rejected");
        check(!turn.acceptReply(id, 2, 2000, 32000, 30500), "Insufficient TTS margin accepted");
        check(turn.expiredBeforeStart(32000), "Missing TTS deadline not detected");
        turn.clear();
        check(turn.begin(id, 3, 40000, 70000, 41000), "Closing turn rejected");
        check(!turn.acceptReply(id, 3, 40000, 70000, 41000, "arbitrary"), "Unknown close reason accepted");
        check(turn.acceptReply(id, 3, 40000, 70000, 41000, "declined_more"), "Farewell rejected");
        check(!turn.acceptClose(id, 3, 40000, 70000, 41000), "Closed before farewell completed");
        check(!turn.finishSpeech(id, true), "Unstarted farewell reported spoken");
        check(turn.acceptStart(id, 42000) && turn.finishSpeech(id, true), "Valid farewell completion rejected");
        check(turn.awaitingClose() && "declined_more".equals(turn.closeReason()), "Microphone can resume during closing");
        check(turn.acceptClose(id, 3, 40000, 70000, 72000), "Timely close after spoken farewell rejected");
        check(!turn.acceptClose(id, 3, 40000, 70000, 90001), "Unbounded closing wait accepted");
        check(!turn.acceptClose("wrong", 3, 40000, 70000, 72000), "Foreign completed close accepted");
        check(!turn.finishSpeech(id, true), "Duplicate farewell completion accepted");
        turn.clear();
        check(!turn.awaitingClose(), "Cancelled close still pending");
        check(turn.begin(id, 4, 50000, 80000, 51000), "Next closing turn rejected");
        check(turn.acceptReply(id, 4, 50000, 80000, 51000, "explicit_close") && turn.finishSpeech(id, false), "Failed farewell cannot close safely");
        check(turn.awaitingClose() && !turn.speechStarted(id), "Failed farewell was invented as spoken");
        check(!turn.acceptStart(id, 52000), "Failed farewell restarted");
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
