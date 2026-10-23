package local.doja.voice;

/** Pure playback-window policy: no Android, audio, network or storage. */
public final class SpeechPlaybackPolicyTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Playback policy check " + checks); }

    public static void main(String[] args) {
        check(SpeechPlaybackPolicy.windowMs(false, false, false, 10) == 20000);
        check(SpeechPlaybackPolicy.windowMs(true, false, false, 10) == 20000);
        check(SpeechPlaybackPolicy.windowMs(true, false, false, 300) == 39000);
        check(SpeechPlaybackPolicy.windowMs(true, false, false, 700) == 45000);
        check(SpeechPlaybackPolicy.windowMs(true, false, false, 5000) == 45000);
        check(SpeechPlaybackPolicy.windowMs(true, true, false, 10) == 60000);
        check(SpeechPlaybackPolicy.windowMs(true, false, true, 10) == 60000);
        System.out.println("Speech playback: " + checks + " assertions passed (bounded, length-aware).");
    }
}
