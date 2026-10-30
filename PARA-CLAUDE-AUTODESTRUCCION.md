# Para Claude — la autodestrucción (actualizado tras diagnosticar el fallo)

## Qué falló realmente (y qué estabas haciendo mal)

Tu afirmación anterior de "la herramienta self_destruct no es la causa" era **incorrecta**. Tu propia prueba detallada lo demostró de forma reproducible:

- `start-ia.ps1` con archivos HEAD (sin el tool): **OK** (dos veces).
- `start-ia.ps1` con el tool en el catálogo (descripción larga, corta o mínima): **TimeoutError**.

El tool **sí es el disparador**. No era la RAM ni la memoria.

## Por qué falló (la causa raíz)

El modo `continuous-voice guided alarms` tiene un presupuesto de calentamiento de **55s** (`preparation_ms`, que `enable_reminder_clarification()` sube de 45s a 55s). Su prefijo ya estaba **al límite de esos 55s**:

| Modo | tokens de entrada | resultado |
|---|---|---|
| `continuous-model` (sin agenda) | 583 | arranca (46.2s) |
| `continuous-voice guided alarms` (HEAD, sin tool) | ~766 | arranca (al filo) |
| `continuous-voice guided alarms` + self_destruct | 776 | **timeout a los 55s** |

Al añadir el tool (+10 tokens) el prefijo se pasó de 55s y `prepare()` dio `TimeoutError`.

**No es un bug de tu código:** tu arquitectura (tool + dispatch + `face_scene` + escenas de Android) es correcta. El problema es que el presupuesto estaba demasiado justo, y añadir cualquier herramienta lo desbordaba. El error de diagnóstico fue concluir "no es la causa" sin ver que el prefijo estaba al filo.

## Qué hice yo (DeepSeek)

Subí el presupuesto del calentamiento de **55s → 75s** en `enable_reminder_clarification()` (línea 245 de `assistant_tools_model.py`). Ahora el prefijo tiene margen para el tool y para futuras adiciones de personalidad.

**Verificado:** `start-ia.ps1` con el tool + el fix → `OK: motor arriba`.

## Segundo fallo: "Confirmación de acción no permitida"

Tras arrancar, al decir "autodestruyete" la app respondía **"Confirmación de acción no permitida"**.

**Por qué:** en `model_result` devolviste `'timer_control': 'self_destruct'`. Ese campo lo usa la app para enrutar el resultado a un canal de **acción** (timers, agenda, luces, volumen, web). Como `self_destruct` no es ninguno, caía en el `TimerActionChannel`, que rechaza valores desconocidos con ese error.

**La regla:** `timer_control` es para **acciones** (timer/agenda/volumen/luces/web). `self_destruct` es solo **visual + voz** (la carita `face_scene` + la frase `text`), así que NO debe poner `timer_control`.

**Qué hice:** quité `timer_control` del resultado en `model_result` (`assistant_tools_trial.py`); ahora devuelve solo `{'text', 'face_scene', 'offered_more'}`. El `face_scene` ya viaja en el evento `say` y Android dibuja la carita.

## Qué debes saber para no volver a romperlo

1. **No cambies `preparation_ms`, `compute_ms` ni `response_start_ms`** — son los presupuestos de arranque/turno que gestiono yo.
2. **Cada herramienta que añadas suma tokens al prefijo.** Si un día `start-ia.ps1` vuelve a dar `TimeoutError` en `prepare()`, es que el prefijo creció y hay que subir el margen: avísame, no lo toques tú.
3. El límite duro es el guardián del modelo (~90s): carga del modelo + calentamiento deben quedar por debajo.
4. **`timer_control` es solo para acciones** (timer/agenda/volumen/luces/web). Para algo visual + voz (como self_destruct) usa solo `text` + `face_scene`, sin `timer_control`.

## Contrato de escenas (sin cambios)

Los nombres de `scene` (`EVIL_BOMB`, `BEG_BOMB`, `COUNTDOWN`, `WINK_BOMB`) deben coincidir exactamente con los identificadores de las caritas del HTML.

## Probar

`.\start-ia.ps1` → debe decir "OK: motor arriba (puertos 11435/11436)".
