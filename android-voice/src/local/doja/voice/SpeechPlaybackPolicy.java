package local.doja.voice;

/** Bounded TTS window: long tool explanations must not be cut mid-sentence. */
final class SpeechPlaybackPolicy {
    static final int MIN_MS = 20000;
    static final int MAX_MS = 90000;
    static final int LISTING_MS = 60000;
    private SpeechPlaybackPolicy() {}

    static int windowMs(boolean assistantTools, boolean workerRead, boolean listing, int characters) {
        if (workerRead || listing) return LISTING_MS;
        if (!assistantTools) return MIN_MS;
        if (characters < 0) return MIN_MS;
        long estimated = 12000L + characters * 90L;
        return (int) Math.min(MAX_MS, Math.max(MIN_MS, estimated));
    }
}
