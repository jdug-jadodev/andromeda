package local.doja.voice;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic tests: no real sockets, Android, microphone or sleeps. */
public final class VoiceSocketCleanupTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(VoiceSocketCleanup.closeAsync(null) == null, "Absent connection created a worker");
        Thread caller = Thread.currentThread();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> closer = new AtomicReference<>();
        Socket blocked = new Socket() {
            public void close() throws IOException {
                closer.set(Thread.currentThread()); entered.countDown();
                try { if (!release.await(2, TimeUnit.SECONDS)) throw new IOException("Test release missing"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
        };
        Thread worker = VoiceSocketCleanup.closeAsync(blocked);
        try {
            check(worker != caller, "Connection closed on caller thread");
            check(entered.await(1, TimeUnit.SECONDS), "Close worker never ran");
            check(closer.get() == worker, "Close used another thread");
            check(worker.isAlive(), "Blocked close was not still running independently");
            check(worker.isDaemon(), "Cleanup must not keep the process alive");
        } finally { release.countDown(); worker.join(2000); }
        check(!worker.isAlive(), "Close worker leaked");
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.set(error));
        try {
            for (boolean runtime : new boolean[]{false, true}) {
                Socket failing = new Socket() {
                    public void close() throws IOException {
                        if (runtime) throw new IllegalStateException("Provider failure");
                        throw new IOException("Already disconnected");
                    }
                };
                Thread failed = VoiceSocketCleanup.closeAsync(failing); failed.join(2000);
                check(!failed.isAlive(), "Failed close worker leaked");
                check(uncaught.get() == null, "Close failure escaped into application");
            }
        } finally { Thread.setDefaultUncaughtExceptionHandler(before); }
        System.out.println("Voice socket cleanup: " + assertions + " assertions passed (no device/network/audio).");
    }
}
