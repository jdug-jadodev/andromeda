package local.doja.voice;

import android.content.*;

/** Scheduled ID only, never text or commands from an Intent. No LFM or capture here. */
public final class AgendaReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String id = intent == null ? null : intent.getStringExtra("id");
        if (!AgendaEntry.validId(id) || !("local.doja.voice.AGENDA." + id).equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            AgendaEntry entry = null;
            try (AgendaStore store = new AgendaStore(context)) {
                entry = store.claim(id);
                if (entry != null) context.startForegroundService(new Intent(context, AgendaDeliveryService.class)
                    .setAction("DELIVER").putExtra("id", id).putExtra("revision", entry.revision));
            } catch (Exception failure) {
                if (entry != null) try (AgendaStore store = new AgendaStore(context)) { store.finish(entry, false); } catch (Exception ignored) {}
            } finally { pending.finish(); }
        }, "agenda-receiver").start();
    }
}
