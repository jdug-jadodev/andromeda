package local.doja.voice;

import android.os.SystemClock;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Fixed public Bogotá coordinates only; no location permission, history, audio or credentials. */
final class WeatherClient {
    static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast?latitude=4.711&longitude=-74.0721"
        + "&current=temperature_2m,weather_code&temperature_unit=celsius&timeformat=unixtime&timezone=America%2FBogota";
    WeatherData fetch() throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        HttpURLConnection current = (HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            current.setInstanceFollowRedirects(false); current.setConnectTimeout(4000); current.setReadTimeout(4000);
            current.setRequestProperty("Accept", "application/json");
            long deadline = SystemClock.elapsedRealtime() + 8000;
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (current.getResponseCode() != 200) throw new IOException("Weather unavailable");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = current.getInputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() > deadline)
                        throw new InterruptedIOException();
                    if (bytes.size() + count > 32768) throw new IOException("Weather response too large");
                    bytes.write(buffer, 0, count);
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) throw new InterruptedIOException();
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8), System.currentTimeMillis());
        } finally { current.disconnect(); }
    }

    static WeatherData parse(String text, long now) throws Exception {
        JSONObject response = new JSONObject(text), units = response.getJSONObject("current_units");
        if (!"America/Bogota".equals(response.getString("timezone")) ||
            !"°C".equals(units.getString("temperature_2m")) || !"unixtime".equals(units.getString("time")))
            throw new IOException("Unexpected weather units/location");
        JSONObject data = response.getJSONObject("current");
        long seconds = data.getLong("time");
        if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000) throw new IOException("Invalid weather timestamp");
        WeatherData value = new WeatherData(data.getDouble("temperature_2m"), data.getInt("weather_code"), seconds * 1000);
        if (!value.isFresh(now)) throw new IOException("Stale weather response");
        return value;
    }
}
