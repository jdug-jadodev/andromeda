# Cambios y acciones de Claude (para revisión de DeepSeek)

Solo lo que cambié o toqué. No incluye diagnóstico ni propuestas de reparación.

## 1. Archivos del repo modificados o creados

### backend/
- **`self_destruct.py`** (nuevo): diccionario `LINES` con cuatro escenas (`EVIL_BOMB`, `BEG_BOMB`, `COUNTDOWN`, `WINK_BOMB`) y función `choose()` que elige una al azar.
- **`assistant_tools_model.py`**:
  - `TOOLS`: se añadió `tool('self_destruct', 'Autodestrucción de Andrómeda si el humano dice autodestruyete, destruyete o muerete. Sin argumentos.')`.
  - `TOOL_INTENT`: se añadió la entrada `'self_destruct'` con regex `\b(?:auto ?destruy\w*|destruy(?:ete|e te|ecte|ase)|muer(?:ete|ecte)|morite)\b`.
  - `SYSTEM`: se cambió la frase de herramientas a «...y para autodestrucción (self_destruct)».
  - Despacho en el bloque de nombres de herramientas: `if name == 'self_destruct': from self_destruct import choose; return finish(choose(), 'accepted')`.
- **`assistant_tools_trial.py`**: en `model_result` se añadió el caso `kind == 'self_destruct'` que devuelve `{'text', 'timer_control': 'self_destruct', 'face_scene', 'offered_more': False}`.
- **`conversation_voice.py`**:
  - `TOOLS_TECHNICAL_ENUMS`: se añadió `'self_destruct'` a `'tool'` y a `'result'`.
  - `timer_response`: se añadió `turn['face_scene'] = response.get('face_scene', '')`.
  - Evento `say`: se añadió `**({'face_scene': turn['face_scene']} if turn.get('face_scene') else {})`.

### tests/
- **`test_assistant_tools.py`**: en la línea del conteo de herramientas, `len(payload['tools'])` pasó de 6 a 7.

### android-voice/src/local/doja/voice/ (sin confirmar en git)
- **`FaceReaction.java`**: se añadieron `EVIL_BOMB`, `BEG_BOMB`, `COUNTDOWN`, `WINK_BOMB` con bandera de escena y duración; `scene()`, `durationMs()` y `parseScene(String)`.
- **`FaceView.java`**: campo `reactionStarted`; `setReaction` lo fija; `onDraw` desvía a `drawScene` cuando `reaction.scene()`; se añadieron los métodos de dibujo de escenas (`sceneEvil`, `sceneBeg`, `sceneCountdown`, `sceneWink`, helpers `disc`, `arch`, `shut`, `stroke`, `horn`, `tears`, `bomb`, `boom`, `countdown`).
- **`MainActivity.java`**: `setReaction` usa `reaction.durationMs()` en dos lugares (antes 5000 fijo).
- **`VoiceService.java`**: en la ruta de habla, `noteReaction(FaceReaction.parseScene(event.optString("face_scene", "")))` antes de `forAssistant`.
- **`FaceReactionPolicy.java`**: sin cambios netos respecto a HEAD (se añadieron y luego quitaron reglas por palabra clave).

Los archivos de Android tienen además cambios de commits anteriores del usuario (`FaceView.java`, `MainActivity.java`, `VoiceService.java`); mis cambios son los descritos arriba y no están confirmados en git.

## 2. Archivos del teléfono (`/data/local/linux/debian-final/opt/doha-voice/pilot/`)

- Copiados desde el repo (con `chown 10218:10218`, `chmod 640`): `assistant_tools_model.py`, `assistant_tools_trial.py`, `conversation_voice.py`, `self_destruct.py`.
- **Estado actual del teléfono (desplegado después de la revisión de DeepSeek):**
  - `assistant_tools_model.py`, `assistant_tools_trial.py`, `conversation_voice.py` y `self_destruct.py` = versión del working tree del repo. Verificado por sha256 contra el repo.
  - Ruta de despliegue: `/data/local/tmp/doha-deploy/` → `cp` + `chown 10218:10218` + `chmod 640` + `chcon` + `mv` a `pilot/`.
- **Cambios del repo que estaban presentes al desplegar y que no hice yo en esta sesión:** `preparation_ms = 75000` en `enable_reminder_clarification()` (`assistant_tools_model.py`, línea 245) y la omisión de `timer_control` para `self_destruct` en `model_result` (`assistant_tools_trial.py`, líneas 125–129). Los dos aparecen en `PARA-CLAUDE-AUTODESTRUCCION.md`.
- **Cambio nuevo en `conversation_voice.py`** (línea 768, en el bucle de respuesta del modelo): la condición para llamar `timer_response` pasó de `timer_control or executing` a `timer_control or executing or face_scene`. Motivo: sin esto, `self_destruct` (que no trae `timer_control`) se hablaba con `say(answer)` y perdía `face_scene`, por eso sonaba la línea y no salía la carita. Desplegado con sha256 `9a733b1a…` y motor reiniciado con `start-ia.ps1` (OK).
- **Cambio nuevo en `assistant_tools_model.py`** (en `interpret_result`, justo antes de `plain = normalized(human)`): si `tool_intent_matches('self_destruct', human)` coincide y no hubo herramienta elegida por el modelo, se devuelve `choose()` con motivo `self_destruct_phrase`. Motivo: el modelo respondía con texto libre («entendido», recordatorios) a «auto destruye te» y variantes. Desplegado con sha256 `2111b6cc…` y motor reiniciado (OK). Esto hace determinista la frase y contradice el diseño anterior de «que decida la IA», a petición del usuario.
- **Regex de `self_destruct`** (`assistant_tools_model.py`, `TOOL_INTENT`): ahora `auto ?destru\w*`, `destruy(?:ete|e te|e ti|ecte|ase)`, `muer(?:ete|ecte|e te|e ti)`, `morite`. Desplegado con sha256 `150d5843…`.
- **`android-voice/src/local/doja/voice/FaceView.java`**: se reescribió el bloque de escenas (`drawScene` y helpers `sceneEye`, `sceneTear`, `bomb`, `boom`, `countdown`) portando los trazos del HTML (`artifacts/andromeda-face-preview.html`: `sceneAt`, `drawEye`, `drawBomb`, `drawBoom`, `drawCountdown`). Se quitaron los cachos de la malvada, el texto y las funciones `disc`, `arch`, `shut`, `horn`, `tears`. Compila con `javac` contra `android.jar`; no verificado en el teléfono todavía.
- **`android-voice/build.ps1`**: en la línea de `javac` se añadieron `-nowarn -Xlint:-options` y se pone `$ErrorActionPreference = 'Continue'` solo durante la compilación, porque las notas de javac en stderr hacían fallar el script en PowerShell 5.1. Sin cambios en la lógica de build.
- APK recompilado (19:27) e instalado con `adb install -r` (Success). Motor reiniciado con `start-ia.ps1` (OK).
- **`FaceView.java`**: nuevo método `sceneRemainingMs()` (ms que quedan de una escena de autodestrucción). Quitada la escena `BEG_BOMB` (case, lágrimas, `sceneTear`) y el guiño de `WINK_BOMB` (`wink`; ahora los dos ojos quedan abiertos).
- **`FaceReaction.java`**: quitado `BEG_BOMB`.
- **`self_destruct.py`** (backend): quitada la entrada `BEG_BOMB`. Desplegado en el teléfono con sha256 `ff0f759f…`.
- **Orden de escenas** (`self_destruct.py`): `choose()` ya no es al azar; rota `EVIL_BOMB` → `WINK_BOMB` → `COUNTDOWN` y vuelve a empezar (contador en el módulo, se reinicia con el motor). Línea de `COUNTDOWN`: «Pensé que lo detendrías, pero veo que no se puede confiar en los humanos.» (sin «Está bien.»). Desplegado: `self_destruct.py` sha256 `7334980a…`, `assistant_tools_trial.py` sha256 `380c8f17…`.
- **`assistant_tools_trial.py`**: `model_result` de `self_destruct` devuelve además `close_reason: 'explicit_close'`, para que la charla cierre tras la línea como con «no necesito nada más».
- **`FaceReaction.java`**: constructor con `speechDelayMs`; `COUNTDOWN` dura 15000 ms y retrasa la voz 8600 ms; método `speechDelayMs()`.
- **`VoiceService.java`**: el cuerpo de `apply()` desde `speechDiagnostic = ...` se separó en `startSpeech(event, message)`; si la escena trae `speechDelayMs() > 0`, `startSpeech` se programa con `main.postDelayed` (guardas `running`, `!cancelling`, `speechGeneration`).
- **`MainActivity.java`**: ventana `faceHoldUntil` fijada al recibir una escena (ruta de sondeo de `faceReactionAt` y `showReaction`); en `present()` ignora estados sin cara mientras dura. Al terminar (`releaseHeldFace`) fija `SPEAKING` y presenta `READY`, para que corra la salida de «me despido» (`jumpExit`).
- **`FaceView.java`** / **`MainActivity.java`** (salida tras la autodestrucción): `FaceView` tiene `setBlank/isBlank` (con `blank` no dibuja nada). En `present()`, si la salida ocurre al final de una escena (`sceneEnd`, menos de 20 s), `selfDestructed` deja la cara en blanco y `startJumpExit(true)` no llama a `face.startJump()`: solo cae el reloj. `setBlank(false)` y `sceneEnd = 0` al volver a mostrarse una cara.
- (Texto anterior de `MainActivity.java`, ya sustituido por el punto previo) en `present()`, si la cara pasa a no mostrarse mientras corre una escena, se retiene hasta que la escena termina (`releaseHeldFace`, campo `heldText`). Al terminar la escena se presenta `FaceState.LISTENING` (vuelve la cara de Andrómeda, no el reloj). Motivo: la línea termina antes que la escena y la pantalla del reloj caía a mitad de la bomba.
- APK recompilado e instalado de nuevo; motor reiniciado (un primer intento falló por `andromeda-resident.lock` existente; se verificó que no había proceso del motor y el segundo arranque salió OK).
- Pruebas unitarias (`tests/test_assistant_tools.py`, `tests/test_tools_technical_receipt.py`): 11 fallos/errores, idénticos a los de HEAD (comparado con `git archive HEAD`).

## 3. Otras acciones sobre el teléfono y el motor

- Instalé el APK `artifacts/android-voice/doja-voice-pilot.apk` varias veces (última compilación con los cambios de Android descritos arriba).
- Eliminé en `/data/local/tmp/` el directorio `andromeda-resident.lock` (y su `work`), tras confirmar que no había proceso del motor. El `pid` del bloqueo ya no existía.
- Detuve el motor varias veces con `andromeda-resident-control.sh stop` y con `kill -TERM` sobre el PID del bloqueo.
- Ejecuté `start-ia.ps1` varias veces con los distintos archivos descritos.
- Creé copias de depuración de los scripts de arranque en el teléfono:
  - `/data/local/tmp/doha-deploy/start-debug.sh`
  - `/data/local/tmp/doha-deploy/start-voice-debug.sh`
  - Escriben en `/data/local/tmp/resident-steps.log` y `/data/local/tmp/prepare-out.log`.
- Creé en `/data/local/tmp/doha-deploy/` archivos de prueba y de despliegue (`deploy-self-destruct.sh`, `import-check.sh`, copias `variant1-model.py`, `variant2-model.py`, `head-model.py`).
- Subí `assistant_tools_model.py` en dos variantes de prueba antes de restaurar la de HEAD:
  - Variante 1: HEAD + la entrada `self_destruct` con la descripción larga.
  - Variante 2: HEAD + la entrada `self_destruct` con descripción «Autodestrucción.».
- Ninguna variante de prueba quedó en el teléfono como versión final.

## 4. Resultados observados (sin interpretación)

- `start-ia.ps1` con los archivos de HEAD: `OK: motor arriba` (dos veces).
- `start-ia.ps1` con la herramienta `self_destruct` en el catálogo (descripción larga, corta y mínima): `No levantó a tiempo`, con `TimeoutError` en `prepare-conversation.py` → `model.prepare()` → `post` → `getresponse`.
- Modo `continuous-model` con la herramienta y descripción corta: arrancó (`conversation_prepared` en 46.2 s).
- Modo `continuous-voice guided alarms` sin la herramienta y con los archivos de HEAD: arrancó. Modo igual con la herramienta: falló.
- Evento de presupuesto del prefijo observado en `continuous-model` con la herramienta: `input_tokens: 583`, `input_token_budget: 1536`.
- Presupuesto de preparación: `preparation_ms = 45000` en `AssistantToolsModel` por defecto; `enable_reminder_clarification()` lo sube a `55000` (`assistant_tools_model.py`).
- Una ejecución con el motor en `preparing` y `server.pid` apuntando a un PID inexistente dejó el bloqueo `andromeda-resident.lock` en `/data/local/tmp`.
- Registro de decisiones del modelo en `/data/local/linux/debian-final/tmp/andromeda_debug.log` (formato `sel=... kind=... reason=... text=...`). Transcripciones de voz registradas: «auto destruyen», «auto destruye te», «auto destruye», «auto excluyente», «muerete». Herramientas seleccionadas en esos casos: `create_reminder`, `delete_reminder`, `set_phone_volume`, `search_web`, o `none`.
- En git, desde el último commit del usuario existen los commits `60e3256`, `0861372` y `2f6c305`. Los archivos `backend/*.py` del repo no tienen cambios sin confirmar en el momento de este documento (`git status`). Mis cambios de backend no estaban presentes en el repo cuando lo revisé.

## 5. Lo que no toqué

- `prepare()`, `preparation_ms` (valor por defecto), `compute_ms`, `response_start_ms` como valores; `resident-model-worker.sh`; `start-andromeda-resident.sh` del teléfono (solo lo ejecuté, no lo modifiqué).
- No hice commit ni push.
