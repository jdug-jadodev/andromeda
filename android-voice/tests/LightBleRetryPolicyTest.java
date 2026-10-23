package local.doja.voice;

/** Pure retry policy test: no Android, BLE, writes, or I/O. */
public final class LightBleRetryPolicyTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("BLE retry check " + checks); }

    public static void main(String[] args) {
        check(LightBleRetryPolicy.mayRetry("CONNECTION_FAILED", 0, 1));
        check(LightBleRetryPolicy.mayRetry("SERVICE_DISCOVERY_FAILED", 0, 2));
        check(LightBleRetryPolicy.mayRetry("TIMEOUT", 0, 1));
        check(LightBleRetryPolicy.mayRetry("WRITE_REJECTED", 0, 1));
        check(LightBleRetryPolicy.mayRetry("CHARACTERISTIC_UNAVAILABLE", 0, 1));
        check(!LightBleRetryPolicy.mayRetry("CONNECTION_FAILED", 1, 1));
        check(!LightBleRetryPolicy.mayRetry("PARTIAL_OR_UNCERTAIN", 0, 1));
        check(!LightBleRetryPolicy.mayRetry("CONNECTION_FAILED", 0, LightBleRetryPolicy.MAX_ATTEMPTS));
        check(LightBleRetryPolicy.backoffMs(2) > LightBleRetryPolicy.backoffMs(1));
        System.out.println("BLE retry policy: " + checks + " assertions passed (pre-write only, bounded attempts).");
    }
}
