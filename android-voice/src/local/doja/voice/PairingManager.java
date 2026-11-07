package local.doja.voice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** PIN de seis dígitos, ventana de dos minutos y cinco intentos. */
final class PairingManager {
    private static final long WINDOW_MS = 120000L;
    private static final int MAX_ATTEMPTS = 5;

    private final File file;
    private final Runnable onClosed;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "andromeda-pairing");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    private String code;
    private long expires;
    private int attempts;
    private ScheduledFuture<?> closing;

    PairingManager(File file, Runnable onClosed) {
        this.file = file;
        this.onClosed = onClosed;
    }

    String open() {
        synchronized (lock) {
            code = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
            expires = System.currentTimeMillis() + WINDOW_MS;
            attempts = 0;
            try {
                Files.write(file.toPath(), code.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
            if (closing != null) closing.cancel(false);
            closing = timer.schedule(this::close, WINDOW_MS, TimeUnit.MILLISECONDS);
            return code;
        }
    }

    boolean verify(String supplied) {
        synchronized (lock) {
            if (code == null || System.currentTimeMillis() >= expires) {
                close();
                return false;
            }
            if (supplied == null || !MessageDigest.isEqual(
                    code.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                attempts += 1;
                if (attempts >= MAX_ATTEMPTS) close();
                return false;
            }
            close();
            return true;
        }
    }

    void close() {
        synchronized (lock) {
            code = null;
            expires = 0;
            attempts = 0;
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException ignored) { }
            if (closing != null) {
                closing.cancel(false);
                closing = null;
            }
        }
        onClosed.run();
    }

    void shutdown() {
        synchronized (lock) {
            if (closing != null) {
                closing.cancel(false);
                closing = null;
            }
            code = null;
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException ignored) { }
        }
        timer.shutdownNow();
    }
}
