package local.doja.voice;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Carga el certificado TLS del gateway desde el almacenamiento privado de la app. */
final class GatewayKeystore {
    private static final String STORE = "gateway.p12";
    private static final String PASSWORD = "gateway.pass";

    static SSLContext context(Context context) throws Exception {
        File store = new File(context.getFilesDir(), STORE);
        File password = new File(context.getFilesDir(), PASSWORD);
        if (!store.isFile() || !password.isFile()) {
            throw new IOException("Falta el certificado del gateway");
        }
        char[] secret = read(password).toCharArray();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = new FileInputStream(store)) {
            keys.load(input, secret);
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, secret);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(managers.getKeyManagers(), null, new SecureRandom());
        return tls;
    }

    /** Huella SHA-256 del certificado; es pública y se usa para el pin del cliente. */
    static String pin(Context context) {
        try {
            File store = new File(context.getFilesDir(), STORE);
            File password = new File(context.getFilesDir(), PASSWORD);
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (InputStream input = new FileInputStream(store)) {
                keys.load(input, read(password).toCharArray());
            }
            String alias = keys.aliases().nextElement();
            Certificate certificate = keys.getCertificate(alias);
            return hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (Exception error) {
            return "";
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    private static String hex(byte[] data) {
        StringBuilder text = new StringBuilder(data.length * 2);
        for (byte item : data) text.append(String.format(Locale.US, "%02x", item));
        return text.toString();
    }

    private GatewayKeystore() { }
}
