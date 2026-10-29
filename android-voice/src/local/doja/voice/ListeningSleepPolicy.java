package local.doja.voice;

/**
 * Presentation only. When the dialogue is listening and nobody speaks, the face gets drowsy, falls asleep
 * and the backend closes the listening window. It never records, sends or authorizes anything.
 */
final class ListeningSleepPolicy {
    static final long DROWSY_AFTER_MS = 15000, ASLEEP_AFTER_MS = 25000;
    /** Total listening window. The backend (conversation_voice.LISTEN_IDLE_MS) must announce the same value. */
    static final long CLOSE_AFTER_MS = 30000;
    private boolean listening;
    private long since;

    /** Face state to show for the service state; listening time is measured from the first LISTENING. */
    FaceState present(FaceState service, long now) {
        if (service != FaceState.LISTENING) { listening = false; return service; }
        if (!listening || now < since) { listening = true; since = now; }
        long quiet = now - since;
        return quiet >= ASLEEP_AFTER_MS ? FaceState.ASLEEP : quiet >= DROWSY_AFTER_MS ? FaceState.DROWSY : FaceState.LISTENING;
    }

    /** Someone spoke while she was drowsy or asleep: wake up startled. */
    static boolean startles(FaceState shownBefore, FaceState service) {
        return (shownBefore == FaceState.DROWSY || shownBefore == FaceState.ASLEEP)
            && (service == FaceState.THINKING || service == FaceState.SPEAKING);
    }
}
