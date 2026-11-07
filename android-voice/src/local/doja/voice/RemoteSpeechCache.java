package local.doja.voice;

import android.content.Context;

import java.io.File;
import java.util.Locale;

/**
 * Copia temporal de la voz de Andromeda para que el telefono la reproduzca.
 *
 * El WAV lo sintetiza el mismo motor del Moto; aqui solo se guarda una copia
 * con el id de la orden y se borra sola al caducar. Nunca se sirve un archivo
 * fuera de este directorio ni con un id que no cumpla la politica remota.
 */
final class RemoteSpeechCache {
    private static final String DIRECTORY = "remote-speech";

    static File target(Context context, String id) {
        if (!RemoteVoicePolicy.id(id)) return null;
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) return null;
        prune(directory);
        return new File(directory, id + ".wav");
    }

    static File find(Context context, String id) {
        File file = target(context, id);
        if (file == null || !file.isFile() || file.length() == 0) return null;
        return file;
    }

    private static void prune(File directory) {
        long now = System.currentTimeMillis();
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.US);
            if (!name.endsWith(".wav")) continue;
            if (now - file.lastModified() >= RemoteVoicePolicy.REPLY_CACHE_MS) file.delete();
        }
    }

    private RemoteSpeechCache() { }
}
