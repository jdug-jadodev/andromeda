# Cancelación global de Andrómeda

**Estado:** ⛔ **bloqueada temporalmente por acuerdo del usuario (2026-10-24)**. La v57 no quedó
fiable; no se harán más cambios ni pruebas hasta retomarla con más tiempo.  
**Prioridad:** P0, antes de chistes/cuentos y demás funciones nuevas.  
**Fecha:** 2026-10-24.  
**Modelo:** sin cambios. Todo el reconocimiento y la respuesta fija son locales.

## Objetivo

Poder cancelar una solicitud de Andrómeda **en cualquier fase**:

- mientras escucha;
- mientras piensa;
- mientras habla;
- mientras pregunta una confirmación;
- mientras tiene una petición pendiente.

Al decir **“cancélalo”**, Andrómeda debe responder exactamente:

> **“Está bien, aquí estaré cuando lo necesites.”**

Después termina la conversación actual y queda esperando que el usuario vuelva a decir
**“Andrómeda”** para una solicitud nueva.

## Comportamiento aprobado

| Frase / situación | Resultado |
|---|---|
| “Andrómeda” sin conversación activa | Inicia una solicitud nueva |
| “Andrómeda” con conversación activa | **Interrumpe** la voz/procesamiento y abre escucha; **no cancela** |
| “cancélalo”, “cancela”, “cancela eso”, “olvídalo” | Cancela la petición pendiente, dice la frase fija y termina la conversación |
| Acción ya enviada al dispositivo | No se puede deshacer; la cancelación solo evita lo pendiente |
| Después de cancelar | El servicio de wake sigue esperando “Andrómeda”; no hay que reactivar a mano |

La frase fija no pasa por el modelo ni por el backend; se sintetiza localmente.

## Por qué “Andrómeda” no choca entre inicio e interrupción

La palabra no se trata como un comando global. Se interpreta según el **estado de la conversación**:

| Estado | “Andrómeda” | “cancélalo” |
|---|---|---|
| Esperando despierta, sin conversación | Inicia solicitud | Ignorado |
| Escuchando | Interrumpe la escucha actual | Cancela |
| Pensando | Interrumpe el procesamiento | Cancela |
| Hablando / preguntando | Corta la voz | Cancela |
| Petición pendiente | Interrumpe la voz/pregunta | Descarta lo pendiente |

Reglas para evitar dobles interpretaciones:

1. **Un solo dueño por estado:** la activación solo escucha en estado de espera; la interrupción
   solo escucha cuando hay una conversación activa.
2. **Bloqueo corto de ~1,5 s** después de una interrupción para que la cola acústica de
   “Andrómeda” no se reinterprete como una activación nueva.
3. Para una solicitud nueva después de cancelar se vuelve a decir “Andrómeda” en estado de espera.
4. “Andrómeda” sola no descarta la petición; solo corta la salida y deja escuchar.
5. “cancélalo” es la única frase que descarta la petición y cierra la conversación.

## Detección por fase

### 1. Escuchando

- Revisar la transcripción actual antes de ejecutar o enviar una acción.
- Si coincide con una frase de cancelación, no se procesa la solicitud.
- Se dice la frase fija localmente y se vuelve a esperar la palabra clave.

### 2. Pensando

- El micrófono principal está en pausa; se arma un detector local de cancelación.
- Si se detecta “Andrómeda” o “cancélalo”, se corta el procesamiento pendiente.
- “Andrómeda” sola solo interrumpe; “cancélalo” descarta el turno y cierra.

### 3. Hablando o preguntando

- Extender el detector de interrupción actual (`SilenceListener` / `SilenceWindowPolicy`) para
  aceptar las frases de cancelación y la palabra clave sola.
- Si se detecta “Andrómeda”, se detiene la voz y se escucha.
- Si se detecta “cancélalo”, se detiene la voz, se descarta lo pendiente y se habla la frase fija.

### 4. Petición pendiente / ejecución

- Cancelar antes de enviar una orden al dispositivo.
- Si la orden ya fue aceptada por el dispositivo, no se puede deshacer; se informa sin reintentar.
- Nunca reintentar una escritura BLE/GATT ya aceptada por la cancelación.

## Comportamiento de sesión

- No se debe apagar el servicio de wake al cancelar; la conversación se cierra, pero la escucha de
  “Andrómeda” debe seguir activa.
- Se reutiliza el mecanismo de **cierre con escucha continua** (`keep_listening` / reset de contexto)
  del backend, o el mecanismo equivalente que mantenga el micrófono en espera.
- La cancelación descarta el turno y las confirmaciones pendientes; no deja formularios abiertos.
- La respuesta fija se reproduce con TTS local; no se envía audio ni texto a la nube.

## Archivos previstos

- `android-voice/src/local/doja/voice/SilenceWindowPolicy.java` — vocabulario y aceptación.
- `android-voice/src/local/doja/voice/SilenceListener.java` — callback con el texto reconocido.
- `android-voice/src/local/doja/voice/VoiceService.java` — estados, interrupción, cancelación,
  cierre con wake activo y TTS local.
- `android-voice/src/local/doja/voice/MainActivity.java` — estado/indicador si hace falta.
- `backend/conversation_voice.py` / `assistant_tools_trial.py` — cierre/reset de contexto y descarte
  de formularios pendientes, sin ejecutar acciones.
- `android-voice/test-policy.ps1` y pruebas puras — vocabulario, estados y no ejecución.

## Criterios de aceptación

- [ ] “Andrómeda” durante la espera inicia una solicitud.
- [ ] “Andrómeda” mientras habla detiene la voz y no inicia otra solicitud con la misma palabra.
- [ ] “Andrómeda” mientras piensa interrumpe sin ejecutar una acción pendiente.
- [ ] “cancélalo” mientras escucha, piensa, habla o pregunta cancela y dice la frase exacta.
- [ ] Después de la frase, la conversación termina y el wake de “Andrómeda” sigue activo.
- [ ] Una acción ya enviada no se reintenta ni se presenta como cancelada.
- [ ] La prueba no guarda audio, transcripción ni conversación humana.
- [ ] No se cambia el modelo ni se usa nube para la decisión.

## Implementación v51

- Vocabulario local ampliado en `SilenceWindowPolicy`: `silencio`, `andromeda`, `cancelalo`,
  `cancela`, `cancela eso`, `olvidalo`, `andromeda cancelalo`.
- `SilenceListener` entrega el texto reconocido; el detector de pensamiento y el de voz usan la
  misma ruta.
- Al cancelar: se detiene la captura/servicio, `CancelSpeakerService` dice la frase local y
  reabre el servicio continuo para esperar “Andrómeda”.
- Se añadió cooldown de 1,8 s tras una interrupción para no reutilizar la cola de la palabra.
- El reinicio del wake espera a que la instancia anterior termine antes de abrir la nueva.
- Compilado e instalado; la suite de vocabulario/ventana de silencio pasa.

## Corrección v52

La primera prueba real (v51) no detuvo una búsqueda web. La causa más probable es que el detector de
pensamiento se armaba con una ventana de 1,5 s mientras el micrófono/decodificador Vosk todavía se
estaba liberando o preparando, por lo que nunca llegaba a escuchar.

En v52:

- `SilenceListener` espera el decodificador preparado hasta que termina la ventana, en vez de fallar
  de inmediato.
- El detector de pensamiento usa una ventana de **5 s**.
- TTS solo reutiliza el detector de pensamiento si realmente está reproduciendo; si no, arma uno
  nuevo con 5 s.
- Se mantiene el reinicio del wake tras la frase y el cooldown de 1,8 s.

Falta repetir la prueba real en escucha, pensamiento, habla y confirmación.

## Corrección v53: gramática de comandos

El diagnóstico de la prueba v51/v52 mostró que el detector **sí capturaba audio**, pero terminó con
`endpoints=6` y `rejected_segments=6`: el modelo Vosk general no estaba produciendo el texto/confianza
exactos de las órdenes.

En v53 el decodificador usa una gramática cerrada:

- `silencio`
- `andromeda` / `andrómeda`
- `cancelalo` / `cancélalo`
- `cancela`, `cancela eso`, `cancela lo`
- `olvidalo` / `olvídalo`
- `andromeda cancelalo`

Se mantiene `[unk]` para no forzar audio ajeno. Es local, sin cambio de modelo ni nube.
La gramática se cargó correctamente en el dispositivo con `CancelGrammarInstrumentation` (`grammar_ok`).
La última prueba mostró `endpoints=3, rejected_segments=3`: el detector oye pero rechaza. En v56
los comandos exactos de cancelar/interrumpir aceptan confianza **0.55** y el detalle numérico guarda
`max_confidence_permille` para distinguir baja confianza, texto no coincidente o supresión por AEC.

### Corrección v57: no apagar el modelo

La primera cancelación de la v56 funcionó, pero al reiniciar `VoiceService` se cerró la conexión
continua y el **modelo residente se detuvo**; después hubo que arrancarlo a mano y la segunda
cancelación falló.

En v57 la cancelación **no detiene `VoiceService`**:

1. Envía `audio_yield` al backend: cancela la generación y la acción pendiente, pero mantiene la
   conexión y el modelo residente.
2. `CancelSpeakerService` dice la frase local.
3. Al terminar, envía `CANCEL_DONE`; `VoiceService` espera el `audio_yield` confirmado y envía
   `audio_resume`.
4. El backend reinicia el contexto y vuelve a `wake` sin cerrar el modelo.

Así se pueden hacer cancelaciones repetidas sin reiniciar el motor.

## Bloqueo temporal

El usuario decidió pausar esta función el 2026-10-24: la primera cancelación funcionó una vez, pero
el flujo no quedó fiable en intentos posteriores. No se seguirán instalando variantes ni haciendo
pruebas de micrófono por ahora. Al retomarlo, empezar por los diagnósticos numéricos ya instalados
(`max_confidence_permille`, `endpoints`, `callbacks_sent`) y por el estado del motor residente.

## Pruebas reales pendientes

1. Iniciar una solicitud y decir “cancélalo” mientras escucha.
2. Decir “cancélalo” mientras Andrómeda piensa.
3. Decir “cancélalo” mientras habla.
4. Decir “cancélalo” mientras pregunta una confirmación de luces/agenda.
5. Decir “Andrómeda” sola durante la voz y comprobar que solo interrumpe.
6. Después de cancelar, volver a decir “Andrómeda” y comprobar que inicia una solicitud nueva.
7. Repetir sin guardar audio/transcripciones y sin ejecutar acciones ya enviadas.

## Decisiones abiertas

- Ninguna de comportamiento: el usuario eligió **interrumpir con el nombre** y **cancelar con
  “cancélalo”**, terminando la conversación y esperando la palabra clave.
- Pendiente técnico: confirmar el mecanismo exacto de reset/cierre que mantiene el wake activo y
  no deja formularios pendientes en el backend.
