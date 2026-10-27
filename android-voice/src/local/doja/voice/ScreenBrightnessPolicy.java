package local.doja.voice;

/** App-level clock brightness: active 70%, idle 50%, rest-mode night minimum. */
final class ScreenBrightnessPolicy {
    static final int ACTIVE_LEVEL = 179; // 70% of 255
    // 20/255 reads as roughly half brightness to the user; 10/255 is the
    // requested lower clock level while keeping the time readable.
    static final int IDLE_LEVEL = 10;
    static final int NIGHT_LEVEL = 1;    // same minimum as rest mode
    static final long IDLE_MS = 10000L;
    static final long VOICE_IDLE_MS = 20000L;

    private ScreenBrightnessPolicy() {}

    static int desired(RestModeSchedule rest, long nowWall, boolean idle) {
        if (rest != null && rest.enabled && rest.nightAt(nowWall)) return NIGHT_LEVEL;
        return idle ? IDLE_LEVEL : ACTIVE_LEVEL;
    }
}
