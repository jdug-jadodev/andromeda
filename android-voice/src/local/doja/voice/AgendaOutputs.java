package local.doja.voice;

import java.util.*;

/** Owned output handles only. Future delivery must register before playback. No capture. */
final class AgendaOutputs {
    interface Output { boolean stop(); }
    private static final Map<String, Output> active = new HashMap<>();
    static synchronized boolean begin(String id, Output output) {
        if (!AgendaEntry.validId(id) || output == null || active.containsKey(id) || active.size() >= 20) return false;
        active.put(id, output); return true;
    }
    static synchronized void end(String id, Output output) { if (active.get(id) == output) active.remove(id); }
    static boolean stop(String id) {
        if (!AgendaEntry.validId(id)) return false;
        Output output;
        synchronized (AgendaOutputs.class) { output = active.get(id); }
        if (output == null) return true; // A previous process's output cannot survive in this process.
        try {
            if (!output.stop()) return false;
            end(id, output); return true;
        } catch (RuntimeException failure) { return false; }
    }
}
