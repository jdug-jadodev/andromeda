package local.doja.voice;

/** Limits for a spoken order, mirrored with contract/home-api-v1/limits.json. */
final class RemoteAudioPolicy {
    static final String POLICY = "remote_voice_audio_v1";
    static final int MAX_BYTES = 2097152;
    static final int MAX_MS = 30000;

    static boolean id(String value) {
        return RemoteVoicePolicy.id(value);
    }

    static boolean duration(long ms) {
        return ms > 0 && ms <= MAX_MS;
    }

    private RemoteAudioPolicy() { }
}
