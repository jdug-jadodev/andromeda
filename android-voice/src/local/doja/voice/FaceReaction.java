package local.doja.voice;

/** Visual reactions drawn with Andrómeda's own face; never text emoji. */
public enum FaceReaction {
    NONE, SAD, CRY, COLD, HOT, FOG, CELEBRATE, LOVE, SILLY, DISGUST, COOL,
    // Self-destruct scenes: timed drawings (bomb, countdown) that replace the eyes while they play.
    // COUNTDOWN is silent for its first 8.6 s (1.6 s of eyes + ten digits at .7 s, see FaceView); her line follows.
    EVIL_BOMB(true, 7000, 0), COUNTDOWN(true, 15000, 8600), WINK_BOMB(true, 4200, 0);

    private final boolean scene;
    private final long durationMs, speechDelayMs;

    FaceReaction() { this(false, 5000, 0); }
    FaceReaction(boolean scene, long durationMs, long speechDelayMs) {
        this.scene = scene; this.durationMs = durationMs; this.speechDelayMs = speechDelayMs;
    }

    public boolean scene() { return scene; }
    public long durationMs() { return durationMs; }
    /** How long the scene plays before she starts speaking. */
    public long speechDelayMs() { return speechDelayMs; }

    /** Scene chosen by the backend's self-destruct tool; anything else is NONE. */
    public static FaceReaction parseScene(String name) {
        for (FaceReaction value : values()) if (value.scene && value.name().equals(name)) return value;
        return NONE;
    }
}
