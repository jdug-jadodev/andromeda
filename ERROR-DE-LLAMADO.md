# Error de llamado: «Andrómeda» se oye como «Romeo» / «Bromeo»

Estado: **pendiente de ejecutar** (solo analizado, no se ha modificado código).

## El error

Al decir «Andrómeda» para activarla, el reconocedor transcribe la propia palabra de activación como
«romeo» o «bromeo». Ese texto se manda al modelo como si fuera una petición del usuario, y Andrómeda
se queda «pensando» un buen rato en algo que nadie pidió. Mientras tanto no escucha la petición real.

## Evidencia (teléfono, `/data/local/linux/debian-final/tmp/andromeda_debug.log`)

- 13 turnos con texto `romeo` (11) o `bromeo` (2).
- Todos llegaron al modelo: eligió `create_reminder` (o una vez `set_phone_volume`) y el resultado fue
  `intent_mismatch` (respuesta de texto) o `agenda_request_rejected`.
- Ejemplo: `25888|sel=create_reminder|kind=reply|reason=intent_mismatch|text=bromeo`.

## Causa

Archivo `backend/conversation_voice.py`:

1. En estado `wake` el detector (`backend/spanish_wake.py`) usa una gramática restringida
   (`andrómeda`, `[unk]` y unas pocas palabras parecidas) y se activa bien.
2. `activate()` (líneas 635-645) crea un reconocedor de **vocabulario libre** y le reproduce el audio
   guardado (`preroll`, hasta 75 tramos de 20 ms = 1,5 s). Ese audio contiene la palabra de activación.
3. El reconocedor libre transcribe «andrómeda» como «romeo»/«bromeo» (palabras más frecuentes y de
   sonido parecido).
4. Solo se descarta la palabra de activación si es exactamente «andrómeda»
   (línea 933: `words(text) == words(SETTINGS['wake_word'])`; línea 936: primer token igual a la
   palabra de activación). «romeo» y «bromeo» no coinciden, así que pasan como petición.
5. Si hay una pausa corta tras la palabra, el reconocedor cierra la frase y el turno es solo «romeo».
   Si se sigue hablando, queda «romeo pon una alarma».

## Cómo lo solucionamos (opción 1: tratar las variantes como la palabra de activación)

No se toca el detector ni su gramática.

1. En `backend/spanish_wake.py` añadir una lista de variantes y una función, por ejemplo:
   `WAKE_ALIASES = {'romeo', 'bromeo'}` y `is_wake_word(token, target)` que devuelva verdadero si el
   token normalizado (con `words()`) es el objetivo o una variante. Las variantes salen del registro
   real; si aparecen otras («andro», «an romeo»), se añaden ahí.
2. En `backend/conversation_voice.py`, en los dos puntos ya existentes:
   - línea 933: si **todos** los tokens del texto son la palabra de activación o una variante,
     `continue` (seguir escuchando, sin crear turno ni llamar al modelo);
   - línea 936: si el primer token es la palabra de activación o una variante y hay más texto,
     quitarlo (`text = head[1]`).
3. Riesgo a vigilar: «romeo» es una palabra real («Romeo y Julieta»). Para no recortarla cuando es parte
   de la petición, quitar la variante al inicio solo si cae dentro del audio guardado (`preroll`), es
   decir, si el final de esa palabra (`result['result'][0]['end']`) es menor que la duración del
   `preroll` reproducido. Si no se puede medir con fiabilidad, dejar solo la regla de «texto entero».
4. Pruebas (en `tests/test_conversation_voice.py`, con `run_session(..., transcripts=[...])`):
   - `['romeo']` y `['bromeo']` no generan turno ni llamada al modelo;
   - `['bromeo pon una alarma']` genera el turno `pon una alarma`;
   - `['andrómeda']` sigue ignorado;
   - una petición normal sin variantes no cambia.
5. Despliegue: copiar `conversation_voice.py` y `spanish_wake.py` al teléfono
   (`/data/local/linux/debian-final/opt/doha-voice/pilot/`, dueño 10218:10218, modo 640, verificar sha256),
   reiniciar el motor con `start-ia.ps1` y comprobar en el registro que ya no aparecen líneas con
   `text=romeo` ni `text=bromeo`.

## Lo que no se toca

- Gramática y umbrales del detector de activación (`WakeStability`, `SpanishWakeDetector`).
- `prepare`, presupuestos de tiempo y el worker del modelo.

---

# Error 2: «autodestruyete» a veces se queda pensando mucho y se desactiva

Estado: **pendiente de ejecutar** (solo analizado). La causa exacta no está confirmada: el teléfono no
guarda tiempos ni el motivo de cierre (el proceso de voz manda su salida a `/dev/null` y
`voice_health.xml` solo guarda la última sesión: 107 s, `error=0`).

## Síntoma

Dices «autodestruyete», Andrómeda se queda «pensando» muchísimo y, al tardar, se desactiva: hay que
volver a pulsar «Activar Andrómeda».

## Causas probables (por lectura del código)

1. **Cada frase pasa primero por el modelo.** `AssistantToolsModel.start()` siempre lanza la
   generación y la regla de frases (`assistant_tools_model.py`, `interpret_result`, línea ~391) solo corre
   cuando el modelo ya terminó. El tope por turno es de unos 150 s (`compute_ms = response_start_ms = 150000`).
   Con la caché del modelo vacía, solo el prefijo tarda 55-75 s.
2. **El cierre de charla provoca un reinicio de contexto cada vez.** `assistant_tools_trial.py`
   devuelve `close_reason: 'explicit_close'` para `self_destruct` (para que caiga el reloj). Tras la
   línea, `conversation_voice.py` (líneas 880-883) llama a `request_context('dialogue_closed')`:
   - borrado de caché: una petición «Hola.» + `slots/0?action=erase`, límite de 15 s
     (`inference_privacy.py`);
   - trámite con Android: límite de 4 s (`RESET_TIMEOUT_MS`); si no responde,
     `voice_context_timeout` y la sesión termina;
   - recarga del prefijo en segundo plano (`warm_public_prefix`, 45-75 s) en el único hueco del modelo.
   Si se dice «autodestruyete» otra vez durante esa recarga, `finish_public_prefix` tiene que
   cancelarla (espera 0,5 s; si no frena, `voice_cancellation_stuck` y la sesión termina) y después
   el turno nuevo carga el prefijo en frío.
3. **Fallo aparte (sin cara):** si el modelo elige otra herramienta para la frase, el código devuelve
   la respuesta de texto antes de llegar a la regla de frases (`assistant_tools_model.py`, líneas 321-322:
   `if not tool_intent_matches(name, human): return finish(reply, 'intent_mismatch')`). Visto en el registro:
   `sel=delete_reminder … text=auto destruye ten` y `sel=self_destruct … intent_mismatch text=autor destruye`.
   Además «autor destruye» no está cubierta por la regla.

## Cómo lo solucionamos

**Paso 1: detectar la frase antes de llamar al modelo.**

1. En `AssistantToolsModel.start()` (`assistant_tools_model.py`, ~línea 198), justo después de
   `self.finish_public_prefix()` y la comprobación de turno concurrente, y antes de la rama
   `basic_assistant`: si `tool_intent_matches('self_destruct', text)`, seguir el mismo patrón que la
   rama local de aritmética: `self.cancelled.clear(); self.done.clear(); self.result = self.error = None`;
   `self.memory.messages(text, activity_ms=speech_end)`; `from self_destruct import choose`;
   `self.result = choose()`; rellenar `self.context_metrics` (`tools_model_choice='local'`,
   `tools_selected_tool='self_destruct'`, `tools_model_kind='self_destruct'`,
   `tools_validation='self_destruct_phrase'`, `input_tokens=0`, `input_token_budget=1536`,
   `history_turns_available`); `_debug(text, 'local', 'self_destruct', 'self_destruct_phrase')`;
   `self.done.set(); return self.work_deadline`. Si `clock() >= work_deadline`, `self.error = 'TimeoutError'`
   como hace la rama local.
2. Ampliar `TOOL_INTENT['self_destruct']` para cubrir lo visto en el registro: «autor destruye»,
   «auto destruyen», «auto destruyendo», «auto destruye ten», «autodestruir», «autodestru…»,
   «muérete» y variantes. Probar que no active frases normales (p. ej. «el autor destruye su obra» es
   un caso a decidir: aceptar o exigir que la frase sea corta).
3. Mantener el respaldo de `interpret_result` (línea ~391) pero moverlo **antes** del
   `intent_mismatch` (línea 321) para que, si el modelo elige otra herramienta, la frase gane.
4. Pruebas en `tests/test_assistant_tools.py` / `tests/test_conversation_voice.py`: cada variante
   produce `kind == 'self_destruct'` sin llamar al modelo; las frases normales siguen igual.
5. Despliegue: `assistant_tools_model.py` al teléfono (dueño 10218:10218, modo 640, sha256),
   reiniciar con `start-ia.ps1` y comprobar en el registro líneas `sel=self_destruct|kind=self_destruct|reason=self_destruct_phrase`
   sin la fase de generación.

**Paso 2: quitar el reinicio de contexto del final de la escena** (decisión de diseño, toca Android).

- Quitar `close_reason: 'explicit_close'` de `assistant_tools_trial.py` (queda `offered_more: False`).
  Sin cierre, la sesión sigue en modo seguimiento y el backend pasa sola a «wake» tras `LISTEN_IDLE_MS`
  (30 s) sin voz; hasta entonces Android seguiría mostrando la cara.
- Para que el reloj caiga al terminar la escena sin cierre de charla, `MainActivity` tiene que forzar
  la salida en `releaseHeldFace` (cara en blanco + caída del reloj, ya implementadas con `setBlank` y
  `startJumpExit(true)`) y **ignorar los estados de cara del servicio** hasta que haya una activación
  real (voz o botón), en lugar de dejar que el siguiente `LISTENING` traiga la cara de vuelta
  (`present()` llama a `cancelClosing()` cuando llega un estado con cara). Hay que decidir qué señal
  cuenta como activación real; hoy `ListeningSleepPolicy.startles(...)` marca el despertar con voz.
- Si el reloj no puede resolverse así, la alternativa es dejar el cierre de charla y quedarnos solo con
  el Paso 1, que ya evita llamar al modelo en cada autodestrucción.

**Paso 3: medir.** Escribir en `/tmp/andromeda_debug.log` (o en un archivo nuevo) el motivo de cierre de
sesión y la duración de cada turno, para confirmar cuál de las causas fue la real. Sin esa medida no se
puede asegurar cuál de los fallos del punto 2 de «Causas probables» ocurrió en cada caso.

## Orden sugerido

1. Paso 1 (solo backend, bajo riesgo, el motor se reinicia).
2. Probar con «autodestruyete» varias veces seguidas.
3. Si todavía se desactiva, Paso 3 para medir y luego Paso 2.
