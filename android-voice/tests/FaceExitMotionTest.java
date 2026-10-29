package local.doja.voice;

public final class FaceExitMotionTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("Face exit assertion " + checks); }
    static boolean near(float a, float b) { return Math.abs(a - b) < .02f; }
    public static void main(String[] args) {
        // The clock starts above the screen, falls, bounces twice and settles at its resting place.
        check(FaceExitMotion.clockDropPercent(-1) == -112 && FaceExitMotion.clockDropPercent(0) == -112);
        check(near(FaceExitMotion.clockDropPercent(.56f), 0));
        check(near(FaceExitMotion.clockDropPercent(.72f), -7));
        check(near(FaceExitMotion.clockDropPercent(.86f), 0));
        check(FaceExitMotion.clockDropPercent(.895f) < -1.5f);
        check(FaceExitMotion.clockDropPercent(1) == 0 && FaceExitMotion.clockDropPercent(5) == 0);
        float previous = FaceExitMotion.clockDropPercent(0);
        for (float u = .01f; u < .56f; u += .01f) {   // the first fall only accelerates downward
            float now = FaceExitMotion.clockDropPercent(u);
            check(now >= previous); previous = now;
        }
        for (float u = 0; u <= 1; u += .01f) check(FaceExitMotion.clockDropPercent(u) <= 0);
        // The face crouches, springs up and then falls off the bottom well before the face is hidden.
        float[] crouch = new float[4], top = new float[4], fall = new float[4], gone = new float[4];
        FaceExitMotion.jumpPose(.29f, crouch); FaceExitMotion.jumpPose(.65f, top);
        FaceExitMotion.jumpPose(.9f, fall); FaceExitMotion.jumpPose(FaceExitMotion.GONE_MS / 1000f, gone);
        check(crouch[0] > .1f && crouch[2] < .8f);              // lower and squashed
        check(top[0] < -.4f && top[2] > 1.1f);                  // high and stretched
        check(fall[0] > top[0] && fall[1] < 0);                 // falling and tilting
        check(gone[0] > 2.5f);                                   // far below the face's resting place
        float[] a = new float[4], b = new float[4];            // no jump between phases
        FaceExitMotion.jumpPose(.2999f, a); FaceExitMotion.jumpPose(.3001f, b); check(near(a[0], b[0]) && near(a[2], b[2]));
        FaceExitMotion.jumpPose(.6499f, a); FaceExitMotion.jumpPose(.6501f, b); check(near(a[0], b[0]) && near(a[2], b[2]));
        check(FaceExitMotion.CLOCK_DELAY_MS + FaceExitMotion.CLOCK_DROP_MS >= FaceExitMotion.GONE_MS);
        System.out.println("FaceExitMotionTest ok checks=" + checks);
    }
}
