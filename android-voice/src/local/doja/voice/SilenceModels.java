package local.doja.voice;

import android.content.Context;
import java.io.*;
import org.vosk.*;

/** Same Spanish Vosk weights already on the Moto; private Android copy, no network. */
final class SilenceModels {
    private static Model model;
    private static int users;
    static synchronized int activeUsers() { return users; }
    static final class Lease implements AutoCloseable {
        final Model value;
        private boolean released;
        Lease(Model value) { this.value = value; }
        public void close() {
            synchronized (SilenceModels.class) {
                if (released) return;
                released = true;
                if (--users == 0) { model.close(); model = null; }
            }
        }
    }
    static synchronized Lease acquire(Context context) throws IOException {
        if (model == null) {
            File directory = new File(context.getFilesDir(), "voice-interrupt/vosk-model-small-es-0.42");
            if (!new File(directory, "am/final.mdl").isFile() || !new File(directory, "graph/HCLr.fst").isFile())
                throw new IOException("Falta el ASR local de interrupción");
            LibVosk.vosk_set_log_level(-1); // Never log recognition/audio or model internals.
            try { model = new Model(InterruptionModelProfile.directory(directory).getAbsolutePath()); }
            catch(IOException failure) { throw failure; }
            catch(Exception failure) { throw new IOException("No se preparó el perfil de ASR local"); }
        }
        users++; return new Lease(model);
    }
}
