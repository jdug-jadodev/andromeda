package local.doja.voice;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.speech.tts.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Generates only fixed public test phrases. No AudioRecord, playback or timers. */
public class FixtureInstrumentation extends Instrumentation {
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        final TextToSpeech[] holder = new TextToSpeech[1];
        try {
            Context context = getTargetContext();
            CountDownLatch initialized = new CountDownLatch(1);
            AtomicInteger status = new AtomicInteger(TextToSpeech.ERROR);
            runOnMainSync(() -> holder[0] = new TextToSpeech(context, value -> {
                status.set(value); initialized.countDown();
            }, "com.google.android.tts"));
            if (!initialized.await(20, TimeUnit.SECONDS) || status.get() != TextToSpeech.SUCCESS)
                throw new IllegalStateException("TTS local no inicializado");
            TextToSpeech tts = holder[0];
            List<Voice> voices = new ArrayList<>(tts.getVoices());
            voices.sort(Comparator.comparing(Voice::getName));
            Voice selected = null;
            for (Voice voice : voices) {
                if (!voice.isNetworkConnectionRequired() && "es".equals(voice.getLocale().getLanguage())) {
                    if (selected == null) selected = voice;
                    if ("US".equals(voice.getLocale().getCountry())) { selected = voice; break; }
                }
            }
            if (selected == null || tts.setVoice(selected) == TextToSpeech.ERROR)
                throw new IllegalStateException("No hay voz española offline");
            AtomicReference<CountDownLatch> completed = new AtomicReference<>();
            AtomicBoolean failed = new AtomicBoolean(false);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) {}
                public void onDone(String id) { completed.get().countDown(); }
                public void onError(String id) { failed.set(true); completed.get().countDown(); }
            });
            File directory = new File(context.getFilesDir(), "synthetic");
            if (!directory.isDirectory() && !directory.mkdir()) throw new IllegalStateException("Sin directorio de prueba");
            String[][] cases = {
                {"positive", "Andrómeda."},
                {"command", "Andrómeda, pon un temporizador de diez segundos."},
                {"timer_create", "Andrómeda, pon un temporizador de diez minutos."},
                {"timer_confirm", "Confirmo temporizador."},
                {"timer_missing", "Andrómeda, pon un temporizador."},
                {"alarm_relative", "Andrómeda, pon una alarma en dos minutos."},
                {"dialogue_joke", "Andrómeda, cuéntame un chiste corto."},
                {"silence_command", "Andrómeda, silencio."},
                {"silence_bare", "Silencio."},
                {"silence_negated", "Andrómeda, no guardes silencio."},
                {"silence_reported", "Ayer dije Andrómeda, silencio."},
                {"ai_create", "Andrómeda, avísame dentro de dos minutos."},
                {"ai_cancel", "Andrómeda, quita todas las cuentas regresivas."},
                {"ai_confirm", "Andrómeda, sí."},
                {"ai_chat", "Andrómeda, cómo te llamas."},
                {"conversation_intro", "Andrómeda, me llamo Luis y mi gata se llama Menta."},
                {"conversation_followup", "Andrómeda, cómo se llama mi gata."},
                {"dialogue_intro", "Me llamo Luis y mi gata se llama Menta."},
                {"dialogue_followup", "Cómo se llama mi gata."},
                {"dialogue_close", "Cierra la conversación."},
                {"dialogue_thanks", "Gracias."},
                {"dialogue_no", "No."},
                {"dialogue_yes", "Sí, cuéntame qué es una estrella."},
                {"entry_search", "Andrómeda, recuerdas la conversación de las luces."},
                {"entry_missing", "Andrómeda, recuerdas la conversación de las cámaras."},
                {"entry_yes", "Sí."},
                {"entry_followup", "De qué color son las luces del patio."},
                {"cancel", "Andrómeda, cancela el temporizador."},
                {"delete", "Andrómeda, elimina el temporizador."},
                {"negated", "Andrómeda, no pongas un temporizador de diez segundos."},
                {"quoted", "Andrómeda, ayer dije cancela el temporizador."},
                {"androide", "El androide está dormido."},
                {"andres", "Andrés tiene una hoja roja."},
                {"adormecida", "La persona está adormecida."},
                {"andamio", "Hay un andamio junto a la ventana."},
                {"ordinary", "Vamos a preparar la comida y cerrar la puerta."}
            };
            for (String[] item : cases) {
                completed.set(new CountDownLatch(1));
                File output = new File(directory, "andromeda_fixture_" + item[0] + ".wav");
                if (tts.synthesizeToFile(item[1], new Bundle(), output, item[0]) == TextToSpeech.ERROR ||
                    !completed.get().await(20, TimeUnit.SECONDS) || failed.get() || output.length() <= 44)
                    throw new IllegalStateException("No se sintetizó " + item[0]);
            }
            result.putString("test", "offline_spanish_fixed_fixtures");
            result.putString("voice", selected.getName());
            result.putInt("fixtures", cases.length);
            result.putBoolean("microphone_opened", false);
            result.putBoolean("audio_played", false);
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (holder[0] != null) { holder[0].stop(); holder[0].shutdown(); }
        }
    }
}
