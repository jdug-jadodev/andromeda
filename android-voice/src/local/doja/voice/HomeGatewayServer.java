package local.doja.voice;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * Gateway TLS de casa. Solo acepta orígenes del tailnet (100.64.0.0/10 o
 * fd7a:115c:a1e0::/48), nunca la Wi-Fi local. No habla con el residente todavía:
 * esta fase expone salud, capacidades y emparejamiento.
 */
final class HomeGatewayServer {
    interface PairingListener {
        void onPairing(String code);
        void onClosed();
    }

    static final int PORT = 11440;
    private static final int MAX_HEADER = 8192;
    private static final int MAX_BODY = 65536;
    private static final int MAX_WORKERS = 4;
    private static final int RATE_MAX = 30;
    private static final long RATE_WINDOW_MS = 60000L;

    private final Context context;
    private final PairingListener pairing;
    private final DeviceStore devices;
    private final PairingManager pairingManager;
    private final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_WORKERS);
    private volatile boolean running;
    private SSLServerSocket server;
    private Thread acceptThread;

    HomeGatewayServer(Context context, PairingListener pairing) {
        this.context = context.getApplicationContext();
        this.pairing = pairing;
        this.devices = new DeviceStore(this.context);
        this.pairingManager = new PairingManager(
            new java.io.File(this.context.getFilesDir(), "gateway-pin.txt"), pairing::onClosed);
    }

    void start() throws Exception {
        SSLContext tls = GatewayKeystore.context(context);
        SSLServerSocket socket = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", PORT), 8);
        server = socket;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "andromeda-gateway");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Log.i("AndromedaGateway", "listening on " + PORT + " pin " + GatewayKeystore.pin(context));
    }

    String openPairing() {
        String code = pairingManager.open();
        pairing.onPairing(code);
        return code;
    }

    void stop() {
        running = false;
        close(server);
        workers.shutdownNow();
        pairingManager.shutdown();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket accepted = server.accept();
                if (!(accepted instanceof SSLSocket) || !tailnet(accepted.getInetAddress())) {
                    close(accepted);
                    continue;
                }
                workers.execute(() -> handle((SSLSocket) accepted));
            } catch (SocketException stopped) {
                break;
            } catch (Exception error) {
                if (running) Log.w("AndromedaGateway", "accept " + error.getClass().getSimpleName());
            }
        }
    }

    private void handle(SSLSocket socket) {
        try (SSLSocket peer = socket) {
            peer.setSoTimeout(10000);
            peer.startHandshake();
            peer.setSoTimeout(10000);
            Request request = read(peer.getInputStream());
            write(peer.getOutputStream(), route(request));
        } catch (Exception error) {
            // Nunca se registran cuerpos, tokens ni códigos de emparejamiento.
        }
    }

    private Response route(Request request) {
        if (!"/v1/pair".equals(request.path)) {
            String contract = request.header("x-andromeda-contract");
            if (contract != null && !"1".equals(contract)) {
                return Response.error(426, "unsupported_version", "La app es mas nueva que la casa.");
            }
        }
        if ("POST".equals(request.method) && "/v1/pair".equals(request.path)) return pair(request);
        if ("GET".equals(request.method) && "/v1/health".equals(request.path)) return health(request);
        if ("GET".equals(request.method) && "/v1/capabilities".equals(request.path)) return capabilities(request);
        if ("POST".equals(request.method) && "/v1/command".equals(request.path)) return command(request);
        if ("GET".equals(request.method) && request.path.startsWith("/v1/speech/")
                && request.path.endsWith(".wav")) return speech(request);
        return Response.error(404, "not_found", "Ruta desconocida.");
    }

    private Response pair(Request request) {
        try {
            JSONObject body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
            if (body.optInt("contract", 0) != 1) {
                return Response.error(426, "unsupported_version", "Contrato no soportado.");
            }
            String name = body.optString("device_name", "").trim();
            if (name.isEmpty() || name.length() > 40) {
                return Response.error(400, "invalid_request", "Nombre invalido.");
            }
            if (!pairingManager.verify(body.optString("code", ""))) {
                return Response.error(403, "forbidden", "Codigo invalido o vencido.");
            }
            DeviceStore.Device device = devices.create(name, Arrays.asList("voice"));
            JSONObject answer = new JSONObject()
                .put("device_id", device.id)
                .put("token", device.token)
                .put("capabilities", new JSONArray(device.capabilities))
                .put("server_time_ms", System.currentTimeMillis());
            return Response.json(200, answer);
        } catch (Exception error) {
            return Response.error(400, "invalid_request", "Peticion invalida.");
        }
    }

    private Response health(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        try {
            return Response.json(200, new JSONObject()
                .put("status", "ok").put("contract", 1).put("gateway", "0.1.0")
                .put("resident", VoiceService.active ? "online" : "offline")
                .put("remote_voice", HomeGatewayConfig.remoteVoiceEnabled(context))
                .put("camera", "unavailable")
                .put("messages", 0).put("tailscale", true)
                .put("server_time_ms", System.currentTimeMillis()));
        } catch (Exception error) {
            return Response.error(500, "internal", "Error interno.");
        }
    }

    private Response capabilities(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        try {
            boolean voice = HomeGatewayConfig.remoteVoiceEnabled(context);
            return Response.json(200, new JSONObject()
                .put("contract", 1).put("voice", voice).put("messages", false).put("camera", false)
                .put("wav_replies", voice).put("max_command_chars", RemoteVoicePolicy.MAX_TEXT)
                .put("max_message_bytes", 2097152).put("max_message_ms", 60000)
                .put("camera_max_minutes", 10).put("camera_max_viewers", 1));
        } catch (Exception error) {
            return Response.error(500, "internal", "Error interno.");
        }
    }

    /** POST /v1/command: validated, authenticated, idempotent phone order. */
    private Response command(Request request) {
        DeviceStore.Device device = authenticate(request);
        if (device == null) return Response.error(401, "unauthenticated", "Token invalido.");
        if (!device.capabilities.contains("voice")) {
            return Response.error(403, "forbidden", "Este dispositivo no tiene voz.");
        }
        if (!HomeGatewayConfig.remoteVoiceEnabled(context)) {
            return Response.error(403, "forbidden", "Las ordenes remotas estan apagadas.");
        }
        if (!allow(device.id)) {
            return Response.error(429, "rate_limited", "Demasiadas ordenes; espera un momento.", true, 1000);
        }
        JSONObject body;
        try {
            body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
        } catch (Exception malformed) {
            return Response.error(400, "invalid_request", "Orden invalida.");
        }
        if (!validCommandBody(body)) return Response.error(400, "invalid_request", "Orden invalida.");
        String id = body.optString("id");
        String text = body.optString("text").trim();
        long clientTs = ((Number) body.opt("client_ts_ms")).longValue();
        boolean wantWav = !body.has("want_wav") || Boolean.TRUE.equals(body.opt("want_wav"));
        try {
            JSONObject frame = new JSONObject()
                .put("action", "remote_command").put("id", id).put("text", text)
                .put("client_ts_ms", clientTs).put("want_wav", wantWav);
            RemoteCommandBus.Reply reply = RemoteCommandBus.submit(frame, RemoteVoicePolicy.WAIT_MS);
            JSONObject payload;
            try {
                payload = new JSONObject(reply.body);
            } catch (Exception malformed) {
                return Response.error(500, "internal", "Respuesta remota invalida.");
            }
            if (reply.status != 200) return Response.json(reply.status, errorPayload(payload));
            JSONObject answer = new JSONObject()
                .put("id", payload.optString("id", id))
                .put("say", payload.optString("say"))
                .put("say_wav", JSONObject.NULL)
                .put("receipts", payload.optJSONArray("receipts") == null
                    ? new JSONArray() : payload.getJSONArray("receipts"))
                .put("origin", "remote")
                .put("server_time_ms", payload.optLong("server_time_ms", System.currentTimeMillis()));
            if (wantWav && awaitSpeech(id, 5000L)) {
                answer.put("say_wav", "/v1/speech/" + id + ".wav");
            }
            return Response.json(200, answer);
        } catch (Exception error) {
            return Response.error(500, "internal", "No se pudo preparar la orden.");
        }
    }

    /** GET /v1/speech/{command_id}.wav: the same voice the house just heard. */
    private Response speech(Request request) {
        if (authenticate(request) == null) {
            return Response.error(401, "unauthenticated", "Token invalido.");
        }
        String name = request.path.substring("/v1/speech/".length());
        if (!name.endsWith(".wav")) return Response.error(404, "not_found", "Audio no encontrado.");
        String id = name.substring(0, name.length() - 4);
        File file = RemoteSpeechCache.find(context, id);
        if (file == null) return Response.error(404, "not_found", "Ese audio ya no esta disponible.");
        try {
            return Response.binary(200, "audio/wav", Files.readAllBytes(file.toPath()));
        } catch (IOException error) {
            return Response.error(500, "internal", "No pude leer el audio.");
        }
    }

    private boolean awaitSpeech(String id, long maxMs) {
        long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            if (RemoteSpeechCache.find(context, id) != null) return true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return RemoteSpeechCache.find(context, id) != null;
    }

    private static boolean validCommandBody(JSONObject body) {
        java.util.Set<String> allowed = new java.util.HashSet<>(
            Arrays.asList("id", "text", "client_ts_ms", "want_wav"));
        for (java.util.Iterator<String> keys = body.keys(); keys.hasNext(); ) {
            if (!allowed.contains(keys.next())) return false;
        }
        Object id = body.opt("id");
        Object text = body.opt("text");
        Object stamp = body.opt("client_ts_ms");
        if (!(id instanceof String) || !(text instanceof String)) return false;
        if (!(stamp instanceof Integer || stamp instanceof Long)) return false;
        if (body.has("want_wav") && !(body.opt("want_wav") instanceof Boolean)) return false;
        long clientTs = ((Number) stamp).longValue();
        return clientTs > 0 && clientTs <= 9_999_999_999_999L
            && RemoteVoicePolicy.command((String) id, (String) text, clientTs);
    }

    private boolean allow(String deviceId) {
        long now = System.currentTimeMillis();
        long[] window = rateLimits.computeIfAbsent(deviceId, key -> new long[]{now, 0});
        synchronized (window) {
            if (now - window[0] >= RATE_WINDOW_MS) {
                window[0] = now;
                window[1] = 0;
            }
            if (window[1] >= RATE_MAX) return false;
            window[1]++;
            return true;
        }
    }

    private static JSONObject errorPayload(JSONObject payload) throws Exception {
        JSONObject answer = new JSONObject()
            .put("error", payload.optString("error", "internal"))
            .put("message", payload.optString("message", "No se pudo completar la orden."))
            .put("retryable", payload.optBoolean("retryable", false));
        if (payload.has("retry_after_ms")) answer.put("retry_after_ms", payload.optLong("retry_after_ms"));
        return answer;
    }

    private DeviceStore.Device authenticate(Request request) {
        String header = request.header("authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return devices.find(header.substring(7).trim());
    }

    private static boolean tailnet(InetAddress address) {
        byte[] value = address.getAddress();
        if (value.length == 4) {
            int first = value[0] & 0xff;
            int second = value[1] & 0xff;
            return first == 100 && second >= 64 && second <= 127;
        }
        if (value.length == 16) {
            return (value[0] & 0xff) == 0xfd && (value[1] & 0xff) == 0x7a
                && (value[2] & 0xff) == 0x11 && (value[3] & 0xff) == 0x5c
                && (value[4] & 0xff) == 0xa1 && (value[5] & 0xff) == 0xe0;
        }
        return false;
    }

    private static Request read(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (head.size() < MAX_HEADER) {
            int value = input.read();
            if (value < 0) throw new EOFException();
            head.write(value);
            if (value == '\r' && (state == 0 || state == 2)) state += 1;
            else if (value == '\n' && (state == 1 || state == 3)) state += 1;
            else state = 0;
            if (state == 4) break;
        }
        if (state != 4) throw new IOException("Header too large");

        String text = new String(head.toByteArray(), StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        String[] first = lines.length == 0 ? new String[0] : lines[0].split(" ");
        if (first.length < 2) throw new IOException("Bad request line");
        String method = first[0];
        String path = first[1];
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);

        Map<String, String> headers = new HashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[index].substring(0, colon).trim().toLowerCase(Locale.US),
                lines[index].substring(colon + 1).trim());
        }

        int length = 0;
        String contentLength = headers.get("content-length");
        if (contentLength != null) {
            try {
                length = Integer.parseInt(contentLength);
            } catch (NumberFormatException error) {
                throw new IOException("Bad content length");
            }
        }
        if (length < 0 || length > MAX_BODY) throw new IOException("Body too large");
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(body, offset, length - offset);
            if (count < 0) throw new EOFException();
            offset += count;
        }
        return new Request(method, path, headers, body);
    }

    private static void write(OutputStream output, Response response) throws IOException {
        byte[] body = response.data != null
            ? response.data : response.body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + response.status + " " + reason(response.status) + "\r\n"
            + "Content-Type: " + response.type + "\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Cache-Control: no-store\r\n"
            + "Connection: close\r\n\r\n";
        output.write(head.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 426: return "Upgrade Required";
            case 429: return "Too Many Requests";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "Internal Server Error";
        }
    }

    private static void close(Closeable value) {
        if (value != null) {
            try {
                value.close();
            } catch (Exception ignored) { }
        }
    }

    private static final class Request {
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }
    }

    private static final class Response {
        final int status;
        final String body;
        final byte[] data;
        final String type;

        Response(int status, String body) {
            this(status, body, null, "application/json; charset=utf-8");
        }

        Response(int status, String body, byte[] data, String type) {
            this.status = status;
            this.body = body;
            this.data = data;
            this.type = type;
        }

        static Response json(int status, JSONObject body) {
            return new Response(status, body.toString());
        }

        static Response binary(int status, String type, byte[] data) {
            return new Response(status, null, data, type);
        }

        static Response error(int status, String code, String message) {
            return error(status, code, message, false, 0);
        }

        static Response error(int status, String code, String message,
                boolean retryable, long retryAfterMs) {
            JSONObject body = new JSONObject();
            try {
                body.put("error", code).put("message", message).put("retryable", retryable);
                if (retryAfterMs > 0) body.put("retry_after_ms", retryAfterMs);
            } catch (Exception ignored) { }
            return new Response(status, body.toString());
        }
    }
}
