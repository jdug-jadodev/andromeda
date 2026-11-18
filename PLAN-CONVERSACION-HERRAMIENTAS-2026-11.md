# Plan P0 — Conversación de herramientas de Andrómeda (2026-11-13)

**Prioridad:** P0. Va **antes** que `PLAN-EXPANSION-2026-11.md` (TV, música, Xbox,
lavadora, identidad). La expansión queda en pausa hasta cerrar esta capa.

**Decisión del usuario (2026-11-13):** **confirmación antes de guardar**. Antes de
crear un recordatorio, una alarma o un horario persistente, Andrómeda resume en una
línea lo que entendió y pregunta «¿Lo guardo?». Solo guarda con un sí explícito.
Para acciones inmediatas y reversibles (temporizador, luz, volumen) se puede ejecutar
directo si el pedido es inequívoco; si hay ambigüedad, se confirma.

## 1. Objetivo

Que el usuario pueda pedir cualquier herramienta **como habla**, en una frase o por
partes, sin conocer formularios ni palabras exactas. Andrómeda debe:

1. Entender la intención y los datos que ya dijo.
2. Saber qué le falta y preguntar **solo eso**, de una cosa en una.
3. Resolver fechas/horas naturales («el martes», «a las 3», «el 24»).
4. Aceptar correcciones («no, el 24», «mejor a las 4») sin empezar de cero.
5. Confirmar antes de guardar y ejecutar.
6. No perder el estado si se cae la voz o hay un relevo de contexto.
7. Hablar casual, sin repetir «¿Necesitas algo más?» después de una pregunta.

## 2. Diálogo de referencia

> **Tú:** «Pon un recordatorio.»
> **Ella:** «¿Qué te recuerdo?»
> **Tú:** «Reunión para algo.»
> **Ella:** «¿Para cuándo?»
> **Tú:** «El martes.»
> **Ella:** «¿El martes 17 de noviembre a las 3:00 p.m.?»
> **Tú:** «Sí, a las 3 de la tarde.»
> **Ella:** «Listo: “Reunión para algo”, martes 17 a las 15:00. ¿Lo guardo?»
> **Tú:** «Sí.»
> **Ella:** «Guardado. Te aviso el martes a las 15:00.»

Variantes que debe aceptar:
- «Reunión para algo el martes a las 3» (todo de golpe) → solo confirma, no pregunta.
- «No, el martes 24» → cambia la fecha, conserva tema y hora.
- «Mejor a las 4» → cambia la hora.
- «El martes siguiente» / «el 24 de este mes» / «el 24 de noviembre» / «el próximo año».
- «Cancélalo» / «déjalo así» / «mejor no» → cancela el pendiente.
- «¿Qué recordatorios tengo?» → lista sin abrir formulario.

## 3. Estado actual y evidencia (2026-11-13)

- App `0.13.77-audio-altavoz`, commit `40effde`, residente vivo.
- Charla `b8ccb6b6`: 8 turnos, **todos los del asistente vacíos**.
- `agenda.db`: **no hay recordatorio nuevo**; solo el viejo «tomarse las pastas».
- `voice_stop_trace`: `SocketException` durante `THINKING` a las 15:33 y 15:36;
  el vigilante reinició `VoiceService`; la app reconectó con contexto nuevo
  (`sequence 1`, `history 5`). El formulario pendiente se perdió y las respuestas
  cayeron en un contexto nuevo.
- El parser de fechas: la hora/fecha sola («martes 17 de noviembre de 2026 a las
  15:55») se resuelve bien, pero al unirla al tema «ver el partido de champions»
  el separador confunde «el partido de champions» con una fecha y devuelve
  `invalid_request`.
- No hubo `Unrelated agenda question` hoy; el fallo de hoy fue la caída del canal
  y la pérdida del estado, no solo el texto del formulario.

## 4. Reglas funcionales

### 4.1 Estado pendiente, no formulario rígido
- Hay **un solo dueño del estado**: el backend. Guarda `pending_action` con
  `tool`, `slots`, `missing`, `question`, `origin_turn`, `nonce`, `expires`.
- La app **no** mantiene una copia del formulario ni compara textos exactos; solo
  recibe la pregunta o la propuesta final, la dice y ejecuta.
- El estado se persiste en SQLite (tabla propia o `conversation_store`) y se
  recupera tras un `SocketException`, un relevo de contexto o un reinicio de
  `VoiceService`.
- Caducidad acotada (por ejemplo, 10 minutos) y cancelación explícita; no dejar
  avisos pendientes eternos.

### 4.2 Extracción y fusión de datos
- El modelo (DeepSeek) devuelve **intención + datos** (`tool`, `slots`), nunca
  ejecuta. La casa valida fechas, horas, objetivos y seguridad.
- Si el modelo devuelve texto para una intención de acción, se cae al parser
  determinista y se abre el estado pendiente. Nunca se queda sin saber qué falta.
- Cada respuesta del humano **fusiona** los datos nuevos con los que ya había:
  «el martes» no borra «reunión para algo»; «a las 4» no borra la fecha.
- Si un dato es ambiguo o contradictorio, se pregunta; no se inventa.

### 4.3 Preguntas
- **Una pregunta por turno**, natural y breve.
- Preguntar solo lo que falta o es ambiguo; no repetir lo que ya está claro.
- Si el ASR es dudoso, repetir el valor entendido: «Entendí “ver el partido de
  champions”; ¿es correcto?».
- Si el humano cambia de tema, aparcar o cancelar el pendiente y responder al
  tema nuevo; no secuestrar la conversación.
- No añadir «¿Necesitas algo más?» después de una pregunta. Solo después de una
  respuesta o acción terminada, y con variantes («¿Algo más?», «¿Te ayudo con
  otra cosa?»).

### 4.4 Confirmación
- Recordatorio, alarma, serie recurrente u horario: resumen de una línea y
  «¿Lo guardo?». Sin sí explícito no se crea.
- Acción inmediata y reversible (temporizador, luz, volumen): ejecutar directo si
  es inequívoca; confirmar si hay ambigüedad o riesgo.
- Si la confirmación se rechaza, preguntar qué cambiar; no reiniciar.
- Resultado: decir qué se guardó y cuándo, sin inventar éxito si no se confirmó.

## 5. Fechas y horas naturales

- Reloj del teléfono, zona `America/Bogota` (UTC-5), sin DST.
- **Año omitido → año actual** (2026). Si la fecha ya pasó, preguntar si es el
  próximo año o la próxima ocurrencia; no asumir en silencio.
- «El martes» → próximo martes. Si hoy es martes o la hora ya pasó, preguntar
  «¿hoy o el próximo?» o resolver la próxima ocurrencia y confirmar.
- «El martes siguiente» / «el de la otra semana» → después del próximo.
- «El 24» → este mes si aún no pasó; si ya pasó, preguntar «¿este mes o el próximo?».
- «El 24 de noviembre» → año actual; «de 2027» → ese año.
- «A las 3» → preguntar «¿3 de la tarde o de la mañana?» si no hay contexto claro;
  «3 de la tarde» → 15:00; «3 de la mañana» → 03:00; formato 24 h respetado.
- «En 5 minutos», «mañana», «pasado mañana», «todos los martes», «cada día».
- Comprobar coherencia: si «martes 24» no es martes, decirlo y ofrecer las fechas
  correctas.
- En el resumen, mostrar siempre la fecha/hora absoluta resuelta.

## 6. Herramientas y campos

| Herramienta | Datos | Pregunta si falta | Confirmación |
|---|---|---|---|
| Recordatorio | qué, cuándo (fecha+hora), repetición opcional | qué / cuándo / hora | sí, antes de guardar |
| Alarma | cuándo, repetición/label | día / hora / repetición | sí, antes de guardar |
| Temporizador | duración | duración | directo si es claro |
| Luces | objetivo (TV/MR Star, mueble/Lotus, ambas), acción, color/hex/brillo, horario | cuál / qué / color / brillo | confirmar si ambiguo |
| Volumen | nivel | nivel | directo |
| Música | qué (canción/artista/lista), dónde (TV/barra/Moto), fuente (R2/local/YouTube cast) | qué / dónde | confirmar si abre app/cast |
| Xbox | encender/apagar/estado, app, YouTube, juego | acción / app/juego | confirmar acciones |
| Lavadora | estado, programa, tiempo, iniciar/retardo | programa / inicio | confirmar inicio y seguridad |

El asistente debe **saber qué herramienta corresponde** y qué campos necesita;
no preguntar cosas que no aplican.

## 7. Arquitectura propuesta

1. **Backend**
   - Nuevo gestor de diálogo de herramientas (`pending_action`) con slots por
     herramienta, persistencia y recuperación.
   - El modelo devuelve intención/slots; `deterministic_router`/validadores
     resuelven fecha/hora y verifican seguridad.
   - Si DeepSeek responde texto para una acción, fallback determinista.
   - Preguntas generadas por plantilla de slot o por el modelo, pero validadas
     por **tipo de dato**, no por texto exacto.
   - Confirmación y ejecución pasan por las guardas actuales (permisos, nonce,
     idempotencia, límites). El modelo no ejecuta.
2. **App**
   - `AgendaDialogue`/`AgendaActionChannel`: dejar de duplicar el formulario y
     de comparar textos exactos. La app recibe `pending_question`/`proposal`,
     habla y ejecuta; el backend conserva el estado.
   - Si se cae el canal, al reconectar pide/recibe el pendiente y lo retoma.
   - La UI puede mostrar el resumen y el botón de confirmar, pero la lógica vive
     en el backend.
3. **Contrato**
   - Eventos de pregunta/propuesta con `tool`, `slot`, `nonce`, `summary`,
     `expires`; sin texto exacto obligatorio.
   - Subir versión cuando la app use los campos nuevos, con compatibilidad.
4. **Diagnóstico**
   - Log acotado y privado por turno: `intent`, `slot`, `missing`, `path`
     (`modelo`/`determinista`/`formulario`), `result` (`pregunta`/`propuesta`/
     `guardado`/`cancelado`), `error`. Sin texto del usuario.
   - Así se puede ver si un recordatorio murió en el modelo, el parser, la app o
     el canal, sin depender de ADB.

## 8. Fases

1. **P0 recordatorios y alarmas conversacionales**
   - Estado pendiente persistido y recuperable.
   - Extracción/fusión de qué/cuándo; fechas relativas; una pregunta por turno.
   - Confirmación antes de guardar.
   - Correcciones y cambio de tema.
   - Pruebas de caída/reconexión.
2. **P0 enrutado de acciones**
   - Prioridad determinista para recordatorio/alarma/temporizador; fallback si el
     modelo devuelve texto; no perder la intención.
3. **P1 luces/volumen**
   - Objetivo explícito, «pon» implícito, color/hex/brillo, confirmación si ambigua.
4. **P1 tono**
   - Preguntas naturales, variantes de cierre, sin «¿Necesitas algo más?» tras
     preguntas; desplegar app+backend juntos.
5. **P2 música/Xbox/lavadora/identidad**
   - Solo después de estabilizar P0/P1; siguen en `PLAN-EXPANSION-2026-11.md`.

## 9. Pruebas y criterios de aceptación

- **Frase completa:** «Recuérdame ver el partido de champions el martes a las 3»
  → confirma y guarda; aparece en `agenda.db`; suena a la hora.
- **Paso a paso:** «Pon un recordatorio» → pregunta qué → «Reunión para algo» →
  pregunta cuándo → «El martes» → resuelve/confirma → «a las 3 de la tarde» →
  resumen → «sí» → guarda.
- **Año omitido:** usa 2026; si la fecha pasó, pregunta.
- **Ambigüedad:** «el martes» con hoy martes, «a las 3», «el 24» → pregunta
  correcta, no inventa.
- **Corrección:** «no, el 24», «mejor a las 4» → actualiza sin perder el resto.
- **Cambio de tema:** «cuéntame un chiste» a mitad → responde al chiste y ofrece
  cancelar/retomar el aviso.
- **ASR malo:** «pepper obra min de champions» → repite lo entendido y permite
  corregir; no guarda basura sin confirmar.
- **Caída del canal:** forzar `SocketException` a mitad del formulario; al
  reconectar, el pendiente sigue y la respuesta no se pierde.
- **Confirmación:** «no» no guarda; «sí» guarda; «cancela» descarta.
- **Tono:** ninguna pregunta seguida de «¿Necesitas algo más?».
- **Herramientas:** luces «solo la del televisor» no toca la del mueble; música
  pregunta dónde; Xbox no promete abrir juego si no está verificado.

## 10. Archivos previstos

- Backend: `conversation_voice.py`, `conversation_store.py` (o tabla de
  pendientes), `assistant_tools_model.py`, `assistant_tools_trial.py`,
  `cloud_model.py`, `agenda_dialogue.py`, `agenda_requests.py`,
  `alarm_schedule.py`, `deterministic_router.py`, `tool_planner.py`.
- App: `VoiceService.java`, `VoiceConnection.java`, `ConversationChannel.java`,
  `AgendaDialogue.java`, `AgendaActionChannel.java`, `AgendaStore.java`,
  `AgendaPermit.java`, `TimerActionChannel.java`, `MainActivity.java`.
- Pruebas: `tests/test_reminder_clarification.py`, `test_agenda_dialogue.py`,
  `test_conversation_voice.py`, `test_continuous_voice.py`, nueva matriz
  conversacional; tests Java de `AgendaDialogue`/`AgendaGuard`.

## 11. Riesgos

- Es un cambio de fondo en app+backend; debe desplegarse junto y con pruebas.
- Persistir el pendiente añade estado y TTL; hay que borrarlo al guardar,
  cancelar o vencer.
- El modelo puede extraer mal un dato; la confirmación y los validadores son la
  red de seguridad.
- No prometer herramientas que aún no existen (música/Xbox/lavadora/identidad).
- Mantener las guardas actuales: el modelo propone, la casa valida y ejecuta.

## 12. Decisiones

- **Confirmación antes de guardar:** sí, decidido.
- **Hora ambigua («a las 3»):** preguntar si no hay contexto claro (recomendado).
- **Martes ambiguo:** preguntar si hoy es martes o la hora ya pasó.
- **Repetición:** incluir «todos los martes»/«cada día» desde la primera versión.
- **Tono:** una pregunta por turno; oferta solo al cerrar.
- **Expansión:** bloqueada hasta cerrar P0/P1; TV puede ir aparte si se decide.

## 13. Anexo — estado pendiente y contrato v14 (preparado 2026-11-18)

**Estado:** `backend/pending_action.py` escrito y probado (`tests/test_pending_action.py`,
18 pruebas, todas verdes). **No está cableado ni desplegado**: nada lo importa
todavía, así que el teléfono sigue igual. La app actual (106) no cambia.

### 13.1 Qué hace ya el módulo

Un aviso pendiente es un dict serializable (JSON/SQLite) con `tool`, `slots`,
`origin_turn`, `nonce`, `created_ms`, `updated_ms` y `asked`.

- Herramientas P0: `REMINDER` (qué + cuándo), `ALARM` (cuándo, semanal o única) y
  `TIMER` (duración, sin confirmación por ser inmediato y reversible).
- `merge(pending, texto, reloj_ms, ahora_ms)` fusiona el turno y devuelve
  `pending_question`, `pending_ready`, `pending_other_topic`,
  `pending_cancelled`, `pending_stale` o `pending_invalid`.
- Una pregunta por turno y solo por lo que falta (`missing()`).
- «el martes», «para el martes», «el 24», «este sábado», «el próximo martes»,
  «mañana», «en cinco minutos», «todos los martes», «cada día».
- Correcciones: «no, el 24» cambia la fecha; «mejor a las 4» cambia la hora.
  Lo que no se dijo se conserva.
- Hora ambigua («a las 3») → «¿3 de la mañana o de la tarde?» y conserva el día;
  la respuesta puede ser solo «de la tarde».
- Fecha imposible o ya pasada, y día de la semana que no cuadra: se dice, no se
  disfraza de tema.
- El resumen siempre dice la fecha absoluta **con año** y termina en
  «¿Lo guardo?» / «¿La guardo?». Nada de «¿Necesitas algo más?» tras preguntas.
- Cambio de tema (`pending_other_topic`): el aviso sigue esperando; no secuestra.
- Caducidad de 10 minutos (`TTL_MS`) y `resolve_confirmation()` para sí/no/cancela.
- `load()` valida el aviso guardado antes de usarlo (esquema, tipo, relojes).

### 13.2 Contrato de eventos (protocolo 14)

Backend → app, por el mismo canal de conversación:

| `action` | Campos | Qué hace la app |
|---|---|---|
| `pending_question` | `tool`, `slot`, `text`, `expires_ms` | Dice `text` y espera el turno humano. No interpreta nada. |
| `pending_ready` | `tool`, `text`, `confirm`, `value` | Si `confirm`, dice `text` y espera sí/no. Si no, propone y ejecuta. |
| `pending_cancelled` | `text` | Dice `text`. No ejecuta. |
| `pending_stale` | `text` | Dice `text`. No ejecuta. |
| `pending_invalid` | `text` | Dice `text` y sigue esperando (el aviso no se pierde). |

`value` es la única fuente para ejecutar: `tool`, `due_ms` o `duration_ms`,
`weekly` (`weekday` 1-7, `minute_of_day`) y `text` (el tema literal). La app
**deja de comparar textos exactos** y deja de reconstruir el formulario; sigue
pasando por `AgendaPermit`/`AgendaActionChannel` para nonce, permiso,
idempotencia y límites. Si `due_ms` no pasa la guarda, se rechaza como hoy.

`pending` (el dict completo) **nunca** viaja a la app: vive en el backend.

### 13.3 Persistencia y reconexión

- Tabla nueva en `conversation_store` (misma base SQLite de memoria):
  `pending_action(conversation_id TEXT PRIMARY KEY, payload TEXT, expires_ms INTEGER)`.
- Se escribe en cada turno y se borra al guardar, cancelar o vencer.
- Al reconectar o tras un relevo de contexto, el backend busca por la identidad
  de la conversación (la misma que ya usa la memoria) y retoma el aviso.
- Sin identidad no se inventa: se responde «no sé de qué aviso hablas».

### 13.4 Cableado backend (siguiente paso)

1. `assistant_tools_model.interpret_result` / `deterministic_router._dispatch`:
   cuando la herramienta elegida es recordatorio/alarma y `current_event` falla,
   abrir `pending_action.new()` en vez de `agenda_dialogue.start`.
2. `conversation_voice`: si hay aviso pendiente para esa conversación, el turno
   humano va a `merge()` **antes** del router; si el resultado es
   `pending_other_topic`, se responde al tema nuevo con el router y el aviso se
   conserva.
3. `pending_ready` → `agenda_proposal` (o `timer_proposal`) con `value`, para que
   los canales Android actuales sigan recibiendo la misma forma.
4. La confirmación («sí») se resuelve con `resolve_confirmation()` y ejecuta por
   el camino actual de `AgendaPermit`.
5. `agenda_dialogue` queda solo como respaldo para clientes con protocolo < 14.

### 13.5 App (APK 107, junto con el backend)

- `AgendaDialogue.java` deja de reconstruir el formulario y de exigir textos
  exactos; acepta `pending_question`/`pending_ready` y habla `text`.
- `AgendaActionChannel` ejecuta `value` (mismos nonce/idempotencia).
- Al reconectar, pide el pendiente al backend y lo retoma.
- Sin cambios en memoria, wake, audio, vigilante ni ciclo de vida de alarmas.

### 13.6 Compatibilidad y despliegue

- El backend anuncia `protocol: 14`; si la app reporta menos, usa el camino
  actual (texto exacto). Así un despliegue parcial no rompe: la app vieja ignora
  los eventos nuevos y el backend sigue hablando su idioma.
- Despliegue conjunto backend + APK cuando se apruebe, con la misma secuencia ya
  usada: copiar archivos, `chown 10218:10218`, `chmod 640`, `adb install -r`,
  `restart-all.sh`, y verificación en el teléfono de los cuatro diálogos.
- Respaldo: los `.bak-20261118` de `agenda_requests.py`, `agenda_dialogue.py` y
  `conversation_flow.py`, y el APK 106 anterior.

### 13.7 Lo que sigue sin decidirse

- Si la app debe mostrar el resumen en pantalla además de decirlo.
- Si «el martes» con hoy martes pregunta «¿hoy o el próximo?» o resuelve y
  confirma (hoy: resuelve y confirma, porque el resumen dice la fecha absoluta).
- Si el aviso pendiente sobrevive a un cambio de chat (hoy: solo dentro de la
  misma conversación).

> **Registro por herramienta:** ver `HERRAMIENTAS-DEEPSEEK-2026-11.md`, con la
> receta repetible, el estado de cada herramienta y el changelog.

### 13.8 Un fallo de protocolo ya no apaga el motor

Un `ValueError` de protocolo durante un turno hacía `resident.shutdown()`: la app
mandaba un mensaje que la casa no aceptaba y **Andrómeda quedaba apagada** hasta
que alguien la encendiera a mano. Eso explica el «se apagó».

Ahora `serve()` tolera los fallos sueltos: cierra esa conexión, suelta el modelo y
**sigue escuchando** para que la app reconecte. Solo una racha de 3 fallos en 10
minutos se trata como cliente incompatible y apaga el motor (`ProtocolWindow` en
`backend/continuous_voice.py`). Pruebas: `tests/test_protocol_window.py`.

Sigue pendiente el auto-reinicio del residente si sale por otra causa (hoy, si el
proceso termina, nadie lo levanta hasta un `restart-all.sh` o un reinicio con
`BootReceiver` exitoso).

### 13.9 El tema ya no se tira cuando la respuesta trae la hora

Reproducido con el código desplegado: «recuérdame» → «¿Para qué es el
recordatorio?» → «comprar crema dental para mañana» volvía a preguntar «¿Para qué
es el recordatorio?». `subject()` rechaza cualquier frase con «mañana», «hoy» o
«no», así que se perdía el tema entero.

Ahora la respuesta se separa con el mismo parser (`bare_parts`): se guardan tema y
día, y se pregunta solo la hora que falta. «sí, comprar crema dental» y «no,
comprar crema dental» también conservan el tema; un «sí y borra la alarma» o un
«por favor» no se convierten en tema. Python y Java comparten las pruebas en
`tests/agenda_dialogue_cases.tsv` (18 aserciones nuevas).

Pendiente: backend + APK juntos, porque la app revalida la frase.
