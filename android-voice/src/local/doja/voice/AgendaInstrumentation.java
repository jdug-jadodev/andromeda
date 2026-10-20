package local.doja.voice;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.os.*;
import java.util.*;
import org.json.*;

/** Actual Android SQLite and wire guards; fake scheduler/ack only, no output or capture. */
public final class AgendaInstrumentation extends Instrumentation {
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Context context = getTargetContext(); Bundle result = new Bundle(); boolean ownedDatabase = false;
        String stage = "preflight";
        Map<String, ?> timers = new HashMap<>(TimerStore.prefs(context).getAll()), receipts = new HashMap<>(TimerStore.actionReceipts(context).getAll());
        try {
            check(!VoiceService.active && !RingService.ringing && !AgendaDeliveryService.active && !CaptureProbeService.active && !SilenceListener.recording(), "Active human service; refusing instrumentation");
            check(!context.getDatabasePath("agenda-isolated-test.db").exists(), "Unknown existing isolated database; inspect first");
            final int[] scheduled = {0}; final boolean[] failSchedule = {false};
            AgendaStore.Scheduler fake = new AgendaStore.Scheduler() {
                public void schedule(AgendaEntry entry) { if (failSchedule[0]) throw new IllegalStateException("synthetic"); scheduled[0]++; }
                public void cancel(String id) {}
            };
            ownedDatabase = true;
            stage = "sqlite_open";
            try (AgendaStore store = new AgendaStore(context, "agenda-isolated-test.db", fake)) {
                check(store.counts().get("REMINDER") == 0, "Isolated database not empty");
                try (SQLiteDatabase db = SQLiteDatabase.create(null)) {
                    try (Cursor configured = db.rawQuery("PRAGMA secure_delete=ON", null)) {
                        check(configured.moveToFirst() && configured.getInt(0) == 1, "Secure deletion was not enabled");
                    }
                    boolean rejected = false;
                    try { db.execSQL("PRAGMA secure_delete=ON"); }
                    catch (android.database.sqlite.SQLiteException expected) { rejected = true; }
                    result.putBoolean("legacy_pragma_execsql_rejected", rejected);
                }
                stage = "create_complete_fail_delete";
                for (boolean delivered : new boolean[]{true, false}) {
                    long boot = SystemClock.elapsedRealtime(), wall = System.currentTimeMillis(); String id = UUID.randomUUID().toString();
                    AgendaPermit permit = AgendaPermit.validated(id, AgendaEntry.Kind.REMINDER, "recuérdame beber agua en dos minutos", "en dos minutos", "beber agua",
                        wall + 120000, 120000, boot + 4000, wall, boot, boot, wall);
                    check(store.create(permit) == AgendaStore.Status.CREATED, "SQLite creation not acknowledged");
                    check(store.find(id).text.equals("beber agua"), "Only reminder payload was not stored");
                    AgendaPermit duplicate = AgendaPermit.validated(id, AgendaEntry.Kind.REMINDER, "recuérdame beber agua en dos minutos", "en dos minutos", "beber agua",
                        wall + 120000, 120000, boot + 4000, wall, boot, SystemClock.elapsedRealtime(), System.currentTimeMillis());
                    check(store.create(duplicate) == AgendaStore.Status.DUPLICATE, "Durable duplicate not rejected");
                    // Simulate the delivery ownership transition, NOT speaker/TTS success.
                    try (SQLiteDatabase db = context.openOrCreateDatabase("agenda-isolated-test.db", Context.MODE_PRIVATE, null)) {
                        db.execSQL("UPDATE agenda SET state='DELIVERING',revision=revision+1 WHERE id=?", new Object[]{id});
                    }
                    AgendaEntry selected = store.find(id);
                    check(store.finish(selected, delivered), "Completion/failure transition not persisted");
                    if (delivered) check(store.find(id) == null, "Successful synthetic completion retained reminder content");
                    else {
                        check(store.find(id).text.equals("beber agua") && store.find(id).state == AgendaEntry.State.FAILED, "Failed completion discarded content");
                        AgendaCancellation.Effects effects = new AgendaCancellation.Effects() {
                            public boolean stopOutput(String ignored) { return true; }
                            public void cancelSchedule(String ignored) {}
                        };
                        check(AgendaCancellation.remove(store.find(id), store, effects) == AgendaCancellation.Result.REMOVED, "Manual real-SQLite deletion failed");
                    }
                }
                check(scheduled[0] == 2, "Duplicate order scheduled twice");
                long boot = SystemClock.elapsedRealtime(), wall = System.currentTimeMillis(); String id = UUID.randomUUID().toString();
                failSchedule[0] = true;
                AgendaPermit failed = AgendaPermit.validated(id, AgendaEntry.Kind.REMINDER, "recuérdame beber agua en dos minutos", "en dos minutos", "beber agua",
                    wall + 120000, 120000, boot + 4000, wall, boot, boot, wall);
                check(store.create(failed) == AgendaStore.Status.FAILED && store.find(id) == null, "Scheduling failure falsely succeeded/retained payload");
            }
            stage = "web_guard";
            JSONObject web = new JSONObject().put("sources", new JSONArray().put(new JSONObject().put("url", "https://example.org/public").put("title", "Fuente pública").put("excerpt", "Extracto público.")))
                .put("text", "Un extracto de example.org dice: Extracto público. Es un resultado de búsqueda, no una verificación completa de la página. ¿Necesitas algo más?");
            check(WebSources.validate("busca en Internet nebulosas", web).contains("https://example.org/public"), "Public source guard rejected fixture");
            String evidence = "A nebula is a cloud of gas and dust in space.";
            String translated = "Una nebulosa es una nube de gas y polvo en el espacio.";
            JSONObject binding = new JSONObject().put("version", 1).put("mode", "translated").put("source_index", 0)
                .put("evidence", evidence).put("answer_es", translated);
            JSONObject qualified = new JSONObject().put("sources", new JSONArray().put(new JSONObject().put("url", "https://example.org/public")
                .put("title", "Nebula").put("excerpt", evidence))).put("web_answer", binding)
                .put("text", "Según el extracto de example.org: " + translated + " ¿Necesitas algo más?");
            check(WebSources.validate("busca en Internet nebulosas", qualified).contains("https://example.org/public"), "Typed translated source rejected");
            for (Object invalid : new Object[]{"1", true, 2}) {
                JSONObject corrupt = new JSONObject(qualified.toString()); corrupt.getJSONObject("web_answer").put("version", invalid);
                boolean rejected = false;
                try { WebSources.validate("busca en Internet nebulosas", corrupt); } catch (Exception expected) { rejected = true; }
                check(rejected, "Invalid typed version accepted");
            }
            for (String field : new String[]{"evidence", "answer_es"}) {
                JSONObject corrupt = new JSONObject(qualified.toString()); corrupt.getJSONObject("web_answer").put(field, "Otro texto que no corresponde.");
                boolean rejected = false;
                try { WebSources.validate("busca en Internet nebulosas", corrupt); } catch (Exception expected) { rejected = true; }
                check(rejected, "Altered source-bound payload accepted");
            }
            JSONObject extra = new JSONObject(qualified.toString()); extra.getJSONObject("web_answer").put("query", "invented");
            boolean extraRejected = false;
            try { WebSources.validate("busca en Internet nebulosas", extra); } catch (Exception expected) { extraRejected = true; }
            check(extraRejected, "Extra source-binding field accepted");
            result.putBoolean("typed_web_answer_guard", true);
            JSONObject synthesis = new JSONObject(qualified.toString());
            synthesis.getJSONObject("web_answer").put("mode", "service_summary").put("source_index", -1).put("evidence", "");
            synthesis.getJSONArray("sources").getJSONObject(0).put("excerpt", "");
            synthesis.put("text", "Según la síntesis web: " + translated + " Fuentes en Opciones. ¿Necesitas algo más?");
            check(WebSources.validate("busca en Internet nebulosas", synthesis).contains("https://example.org/public"), "Service synthesis rejected");
            JSONObject falseQuote = new JSONObject(synthesis.toString()); falseQuote.getJSONObject("web_answer").put("mode", "quoted");
            boolean falseQuoteRejected = false;
            try { WebSources.validate("busca en Internet nebulosas", falseQuote); } catch (Exception expected) { falseQuoteRejected = true; }
            check(falseQuoteRejected, "Service synthesis accepted as a literal page quote");
            result.putBoolean("service_summary_web_guard", true);
            JSONObject worker = new JSONObject(synthesis.toString());
            worker.getJSONObject("web_answer").put("mode", "service_answer");
            worker.put("text", "El servicio web responde: " + translated + " Fuentes en Opciones. ¿Necesitas algo más?");
            check(WebSources.validate("busca en Internet nebulosas", worker).contains("https://example.org/public"), "Direct Worker answer rejected");
            JSONObject forged = new JSONObject(worker.toString()); forged.getJSONObject("web_answer").put("mode", "quoted");
            boolean forgedRejected = false;
            try { WebSources.validate("busca en Internet nebulosas", forged); } catch (Exception expected) { forgedRejected = true; }
            check(forgedRejected, "Worker answer accepted as page quotation");
            result.putBoolean("worker_first_web_guard", true);
            check(timers.equals(TimerStore.prefs(context).getAll()) && receipts.equals(TimerStore.actionReceipts(context).getAll()), "Real timer data changed");
            result.putBoolean("passed", true); result.putBoolean("android_sqlite", true); result.putBoolean("real_scheduler", false);
            result.putBoolean("microphone_opened", false); result.putBoolean("audio_played", false); result.putBoolean("llm_started", false);
        } catch (Exception failure) {
            result.putBoolean("passed", false); result.putString("error", failure.getClass().getSimpleName()); result.putString("failure_stage", stage);
        } finally {
            boolean cleaned = !ownedDatabase || !context.getDatabasePath("agenda-isolated-test.db").exists()
                || context.deleteDatabase("agenda-isolated-test.db");
            result.putBoolean("isolated_storage_cleaned", cleaned);
            if (!cleaned) result.putBoolean("passed", false);
        }
        finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
