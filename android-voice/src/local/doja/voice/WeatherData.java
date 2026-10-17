package local.doja.voice;

/** Public fixed-city weather data, with explicit age checks independent of the UI. */
final class WeatherData {
    static final long MAX_AGE_MS = 90 * 60000L;
    final double celsius;
    final int code;
    final long time;
    WeatherData(double celsius, int code, long time) {
        if (Double.isNaN(celsius) || Double.isInfinite(celsius) || celsius < -50 || celsius > 60 || time <= 0)
            throw new IllegalArgumentException("Invalid weather data");
        this.celsius = celsius; this.code = code; this.time = time;
    }
    boolean isFresh(long now) { return time - now <= 10 * 60000L && now - time <= MAX_AGE_MS; }
    String condition() {
        switch (code) {
            case 0: return "Despejado";
            case 1: return "Mayormente despejado";
            case 2: return "Parcialmente nublado";
            case 3: return "Nublado";
            case 45: case 48: return "Niebla";
            case 51: case 53: case 55: case 56: case 57: return "Llovizna";
            case 61: case 63: case 65: case 66: case 67: return "Lluvia";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Nieve";
            case 80: case 81: case 82: return "Chubascos";
            case 95: case 96: case 99: return "Tormenta";
            default: return "Condición no disponible";
        }
    }
}
