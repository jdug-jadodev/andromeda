package local.doja.voice;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Tokens por dispositivo; solo se guarda su SHA-256, nunca el token en claro. */
final class DeviceStore {
    static final class Device {
        final String id;
        final String name;
        final String token;
        final List<String> capabilities;

        Device(String id, String name, String token, List<String> capabilities) {
            this.id = id;
            this.name = name;
            this.token = token;
            this.capabilities = capabilities;
        }
    }

    private final File file;
    private final Object lock = new Object();

    DeviceStore(Context context) {
        this.file = new File(context.getFilesDir(), "gateway_devices.json");
    }

    Device create(String name, List<String> capabilities) throws Exception {
        synchronized (lock) {
            JSONArray rows = read();
            String id = "dev-" + hex(random(8));
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random(32));
            JSONObject row = new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("token_sha256", hash(token))
                .put("capabilities", new JSONArray(capabilities))
                .put("created_ms", System.currentTimeMillis())
                .put("last_seen_ms", 0)
                .put("revoked", false);
            rows.put(row);
            write(rows);
            return new Device(id, name, token, capabilities);
        }
    }

    Device find(String token) {
        if (token == null || token.length() < 32) return null;
        byte[] digest = hash(token).getBytes(StandardCharsets.US_ASCII);
        synchronized (lock) {
            JSONArray rows = read();
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row == null || row.optBoolean("revoked", false)) continue;
                byte[] stored = row.optString("token_sha256").getBytes(StandardCharsets.US_ASCII);
                if (!MessageDigest.isEqual(digest, stored)) continue;
                try {
                    row.put("last_seen_ms", System.currentTimeMillis());
                    write(rows);
                } catch (Exception ignored) { }
                // Dispositivos emparejados antes de los mensajes/camara reciben
                // las tres capacidades. Revocar es borrar el dispositivo entero.
                List<String> capabilities = strings(row.optJSONArray("capabilities"));
                if (capabilities.contains("voice")
                        && (!capabilities.contains("messages") || !capabilities.contains("camera"))) {
                    capabilities = new ArrayList<>(capabilities);
                    if (!capabilities.contains("messages")) capabilities.add("messages");
                    if (!capabilities.contains("camera")) capabilities.add("camera");
                    try {
                        row.put("capabilities", new JSONArray(capabilities));
                        write(rows);
                    } catch (Exception ignored) { }
                }
                return new Device(row.optString("id"), row.optString("name"), null, capabilities);
            }
        }
        return null;
    }

    boolean revoke(String id) {
        synchronized (lock) {
            JSONArray rows = read();
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row == null || !id.equals(row.optString("id"))) continue;
                try {
                    row.put("revoked", true);
                    write(rows);
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private JSONArray read() {
        try {
            if (!file.isFile() || file.length() == 0) return new JSONArray();
            return new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    private void write(JSONArray rows) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(rows.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!temp.renameTo(file)) {
            Files.copy(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            temp.delete();
        }
    }

    private static List<String> strings(JSONArray values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.length(); index++) result.add(values.optString(index));
        }
        return result;
    }

    private static byte[] random(int size) {
        byte[] data = new byte[size];
        new SecureRandom().nextBytes(data);
        return data;
    }

    private static String hash(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String hex(byte[] data) {
        StringBuilder text = new StringBuilder(data.length * 2);
        for (byte item : data) text.append(String.format(Locale.US, "%02x", item));
        return text.toString();
    }
}
