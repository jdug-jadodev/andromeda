package local.doja.voice;

/** One AudioRecord owner, shared by conversation and output-interrupt listeners. */
final class AudioInputGate {
    private static Object owner;
    static synchronized boolean acquire(Object candidate) {
        if (candidate == null || owner != null && owner != candidate) return false;
        owner = candidate; return true;
    }
    static synchronized void release(Object candidate) { if (owner == candidate) owner = null; }
    static synchronized boolean occupied() { return owner != null; }
}
