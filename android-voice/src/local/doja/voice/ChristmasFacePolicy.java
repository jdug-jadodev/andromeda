package local.doja.voice;

/** Presentation only. The face waits for the backdrop just while she listens; she is never late for an answer. */
final class ChristmasFacePolicy {
    private ChristmasFacePolicy() {}

    static boolean holdsFace(boolean christmas, boolean animated, FaceState state) {
        return christmas && animated && state == FaceState.LISTENING;
    }

    static boolean releasesHeldFace(FaceState state) {
        return state != FaceState.LISTENING;
    }
}
