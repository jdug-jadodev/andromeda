package local.doja.voice;

/**
 * Presentation only: how the face leaves the screen when the session ends after she speaks. The face crouches,
 * springs up and falls out of the bottom while the clock screen falls in from above and settles with two bounces.
 * Same numbers as artifacts/andromeda-face-preview.html.
 */
final class FaceExitMotion {
    /** The clock starts falling while the face is already dropping; the face is off screen well before it ends. */
    static final long CLOCK_DELAY_MS = 550, CLOCK_DROP_MS = 1250, GONE_MS = 1600;
    private static final float CROUCH_S = .3f, SPRING_END_S = .65f;

    private FaceExitMotion() {}

    /** Clock screen offset as a percentage of its height at progress u (0 above the screen, 1 settled). */
    static float clockDropPercent(float u) {
        if (u >= 1) return 0;
        if (u <= 0) return -112;
        if (u < .56f) { float v = u / .56f; return -112 * (1 - v * v); }                         // accelerating fall
        if (u < .72f) { float v = (u - .56f) / .16f; return -7 * (1 - (1 - v) * (1 - v)); }      // first bounce up
        if (u < .86f) { float v = (u - .72f) / .14f; return -7 * (1 - v * v); }                  // and back down
        if (u < .93f) { float v = (u - .86f) / .07f; return -1.6f * (float)Math.sin(v * Math.PI); } // small bounce
        return 0;
    }

    /**
     * Face pose t seconds after the jump starts, written to out = {dy in face-size units (down is positive),
     * rotation in radians, vertical squash, growth}.
     */
    static void jumpPose(float t, float[] out) {
        if (t < CROUCH_S) {
            float e = ease(t / CROUCH_S);
            out[0] = .14f * e; out[1] = 0; out[2] = 1 - .25f * e; out[3] = 1;
        } else if (t < SPRING_END_S) {
            float u = (t - CROUCH_S) / (SPRING_END_S - CROUCH_S), e = 1 - (1 - u) * (1 - u);
            out[0] = .14f - .58f * e; out[1] = -.12f * e; out[2] = .75f + .4f * e; out[3] = 1 + .08f * e;
        } else {
            float u = t - SPRING_END_S;
            out[0] = -.44f + 7 * u * u; out[1] = -.12f - .35f * u; out[2] = 1.15f + .05f * Math.min(1, u * 2); out[3] = 1.08f;
        }
    }

    private static float ease(float u) { return u * u * (3 - 2 * u); }
}
