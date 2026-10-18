package local.doja.voice;

import java.io.IOException;
import java.net.Socket;

/** TLS close may send close_notify. Never do it in an Android lifecycle callback. */
final class VoiceSocketCleanup {
    static Thread closeAsync(Socket socket) {
        if (socket == null) return null;
        // Independent of the control executor: shutdownNow() must not discard
        // the very task that disconnects/cancels the backend.
        Thread worker = new Thread(() -> {
            try { socket.close(); }
            catch (IOException | RuntimeException ignored) { /* Best effort; peer/session deadline remains bounded. */ }
        }, "doja-socket-close");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }
}
