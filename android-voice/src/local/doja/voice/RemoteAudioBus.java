package local.doja.voice;

/** In-process bridge for spoken orders coming from an authenticated phone. */
final class RemoteAudioBus {
    interface Session {
        boolean ready();
        boolean busy();
        boolean play(String id, byte[] pcm, long durationMs);
    }

    private static volatile Session session;

    static void attach(Session value) {
        session = value;
    }

    static void detach(Session value) {
        if (session == value) session = null;
    }

    static Session session() {
        return session;
    }

    private RemoteAudioBus() { }
}
