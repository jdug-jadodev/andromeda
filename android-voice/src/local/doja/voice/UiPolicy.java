package local.doja.voice;

/** Clock-independent UI policy: only human interaction renews the options timeout. */
final class UiPolicy {
    static final long OPTIONS_TIMEOUT_MS = 50000;
    private boolean options;
    private long deadline;

    void showOptions(long now) { options = true; deadline = now + OPTIONS_TIMEOUT_MS; }
    void interact(long now) { if (options) deadline = now + OPTIONS_TIMEOUT_MS; }
    void hideOptions() { options = false; deadline = 0; }
    boolean optionsVisible() { return options; }
    long optionsDeadline() { return deadline; }
    boolean expireOptions(long now) {
        if (!options || now < deadline) return false;
        hideOptions(); return true;
    }
    static boolean showsFace(FaceState state) {
        return state != FaceState.IDLE && state != FaceState.READY && state != FaceState.MUTED;
    }
    static FaceState serviceState(boolean voice,boolean otherAudio,FaceState state) {
        return !voice && !otherAudio ? FaceState.MUTED : state;
    }
    static int parseColor(String text) {
        if (text == null || !text.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Usa #RRGGBB, por ejemplo #BFA3FF");
        return (int)(0xff000000L | Long.parseLong(text.substring(1), 16));
    }
}
