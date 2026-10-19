package local.doja.voice;

import org.json.*;

/** Consumes transient ASR JSON. Partial/tail detections only count; never act. */
final class CaptureProbeRecognition {
    static void partial(CaptureProbeStats stats, String json) throws JSONException {
        stats.partial(CaptureProbeStats.wordCount(new JSONObject(json).optString("partial")));
    }
    static boolean result(CaptureProbeStats stats, String json, boolean pending) throws JSONException {
        JSONObject result = new JSONObject(json);
        double confidence = SilenceListener.commandConfidence(json);
        boolean match = confidence >= .8 && confidence <= 1;
        stats.result(CaptureProbeStats.wordCount(result.optString("text")), match, pending);
        return !pending && match; // A forced tail can never cut the next output.
    }
}
