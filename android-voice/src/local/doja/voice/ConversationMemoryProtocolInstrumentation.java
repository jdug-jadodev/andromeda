package local.doja.voice;

import android.app.Instrumentation;
import android.os.Bundle;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated SQLite protocol smoke test; never opens Android audio or starts TTS. */
public final class ConversationMemoryProtocolInstrumentation extends Instrumentation {
    private Bundle arguments;
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        VoiceConnection connection = null;
        try {
            String id = arguments.getString("context_id", "");
            int expectedTurns = Integer.parseInt(arguments.getString("expected_turns", "-1"));
            check(id.matches("[0-9a-f]{32}") && expectedTurns >= 1 && expectedTurns <= ConversationMemory.MAX_TURNS,
                "Missing seeded context arguments");

            ConversationMemory.Snapshot fresh = ConversationMemory.emptySnapshot();
            connection = VoiceConnection.open(getTargetContext(), "microphone", fresh, Collections.emptyList(),
                false, true, AssistantConfig.CONTINUOUS_VOICE);
            check(connection.ready.optInt("restored_turns", -1) == 0 && connection.ready.optBoolean("voice_entry"),
                "SQLite voice-entry handshake differs");
            connection.close(); connection = null;

            JSONObject catalogResponse = VoiceConnection.memoryAdmin(getTargetContext(), "list", null);
            JSONArray catalog = catalogResponse.getJSONArray("catalog");
            JSONObject found = null;
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject item = catalog.getJSONObject(i);
                check(item.length() == 4 && !item.has("text") && !item.has("turns_text"),
                    "Catalog included conversation text");
                if (id.equals(item.optString("id"))) found = item;
            }
            check(found != null && found.optInt("turns", -1) == expectedTurns,
                "Authenticated SQLite catalog omitted the seeded context");

            JSONObject forgotten = VoiceConnection.memoryAdmin(getTargetContext(), "forget", id);
            check(forgotten.optBoolean("removed") && id.equals(forgotten.optString("id")),
                "SQLite forget did not confirm the exact seeded context");
            JSONArray after = VoiceConnection.memoryAdmin(getTargetContext(), "list", null).getJSONArray("catalog");
            for (int i = 0; i < after.length(); i++)
                check(!id.equals(after.getJSONObject(i).optString("id")), "Forgotten context remains in SQLite catalog");

            result.putBoolean("passed", true);
            result.putBoolean("sqlite_voice_entry_handshake", true);
            result.putBoolean("metadata_only_catalog", true);
            result.putBoolean("authenticated_forget", true);
            result.putBoolean("microphone_opened", false);
            result.putBoolean("tts_started", false);
        } catch (Exception error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (connection != null) try { connection.close(); } catch (Exception ignored) {}
        }
        finish(result.getBoolean("passed") ? android.app.Activity.RESULT_OK : android.app.Activity.RESULT_CANCELED, result);
    }
}
