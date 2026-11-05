package local.doja.voice;

/** The Christmas backdrop never delays an answer and never acts while navidad is off. */
public final class ChristmasFacePolicyTest {
    public static void main(String[] args) {
        expect(ChristmasFacePolicy.holdsFace(true, true, FaceState.LISTENING), "called: she waits for the backdrop");
        expect(!ChristmasFacePolicy.holdsFace(false, true, FaceState.LISTENING), "off: unchanged");
        expect(!ChristmasFacePolicy.holdsFace(true, false, FaceState.LISTENING), "no animations: no wait");
        for (FaceState state : FaceState.values()) {
            if (state == FaceState.LISTENING) continue;
            expect(!ChristmasFacePolicy.holdsFace(true, true, state), "only listening waits: " + state);
            expect(ChristmasFacePolicy.releasesHeldFace(state), "any other state shows her at once: " + state);
        }
        expect(!ChristmasFacePolicy.releasesHeldFace(FaceState.LISTENING), "listening keeps waiting");
        System.out.println("Christmas face policy tests passed.");
    }

    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
