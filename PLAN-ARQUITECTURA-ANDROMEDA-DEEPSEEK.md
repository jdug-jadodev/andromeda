# Plan: Andrómeda — Arquitectura determinista + DeepSeek (nube)

> **PRIORIDAD: ALTA** — cambio principal de arquitectura y diseño.
>
> **Estado: PLAN, no ejecutado.** Antes de ejecutar, hacer limpieza (§6).
> Objetivo del documento: dejar registrado por fases, para ejecutar luego sin
> perder lo que ya funciona.

---

## 1. Objetivo

Pasar Andrómeda de **IA local (LFM 1.2B)** a:

- **Modo determinista** como vía principal (sin modelo, instantáneo).
- **DeepSeek V4 Flash (nube, económico)** como capa de razonamiento, solo cuando
  el modo determinista no entiende.

Por qué: la gente es impredecible y dinámica, y una IA maneja mejor esas
respuestas que un bot manual. También permite historias/cuentos/chistes, razonar
para elegir herramientas (TV, lavadora, color de luces) sin configurarlas a mano,
y adaptar las personalidades de Andrómeda. Se puede retirar el servicio de
búsqueda de internet (`search_web`) porque DeepSeek lo cubre.

**Regla del flujo:**

```
wake → Vosk → texto → [router determinista]
                        ├─ reconoce → herramienta → canal Android / say
                        └─ no reconoce → DeepSeek (nube)
                                          ├─ entiende → texto (cuento/chiste/historia/pregunta)
                                          │            o herramienta (tool_call)
                                          └─ no entiende → "No te entendí"
```

---

## 2. Estado actual (para referencia al desmontar)

Flujo actual (con IA local):

```
wake → Vosk → texto → model.start() [llama-server local]
        → interpret_result() → native_call → gate (TOOL_INTENT) → dispatch
        → canal Android (timer/agenda/volume/light)  o  say(text)
```

Archivos clave:

| Archivo | Rol |
|---|---|
| `backend/conversation_voice.py` | Bucle de sesión, Vosk, TLS, `say`, flujo de turnos |
| `backend/assistant_tools_model.py` | `AssistantToolsModel`, `TOOLS`, `TV_TOOLS`, `TOOL_INTENT`, dispatch |
| `backend/assistant_tools_trial.py` | `AssistantToolsTrial`, `_tv_result`, `model_result` |
| `backend/conversation_flow.py` | Despedida, oferta de seguir ("¿algo más?") |
| `backend/tv_control.py` / `tv_intents.py` | Cliente y parser de TV (deterministas) |
| app Android | `FaceView` (carita), canales (`TimerActionChannel`, etc.) |
| `scripts/resident-model-worker.sh` | Worker que gestiona `llama-server` |
| `scripts/prepare-conversation.py` | Warm-up del prefijo del modelo |

Los **tools deterministas** que ya funcionan y se mantienen:
luces (`control_lights`), TV (`tv_status/inputs/power_on/power_off`), volumen del
teléfono (`set_phone_volume`), alarmas/recordatorios (`create_alarm`/`create_reminder`),
temporizador (`create_timer`), `list_reminders`/`delete_reminder`, `self_destruct`.

---

## 3. Idea central (por qué es viable sin romper nada)

El sistema **ya tiene las dos mitades separadas**:

1. **Parte determinista** (existe y funciona): gates `TOOL_INTENT`, parsers
   (`parse_volume`, `current_event`, `selected_timer_seconds`…), dispatch a canales
   Android, frases de confirmación.
2. **Parte "modelo"** (a quitar): llama-server generando `tool_call`, que el gate
   validaba contra el texto.

Hoy: *modelo elige herramienta → gate valida → dispatch*.

El cambio determinista es **saltarse el modelo** y que el gate **elija la
herramienta directamente** desde el texto. Es un refactor de la capa superior,
**no** una reescritura: los tools, parsers y canales Android quedan igual.

---

## 4. Qué queda intacto (no se toca)

- **Carita y reacciones** (`FaceView`): las maneja el estado (LISTENING, THINKING,
  hablando), no el modelo. Se quedan igual.
  - Determinista: responde **instantáneo** (sin cara de "pensando").
  - DeepSeek: cara de "pensando" durante la llamada a la nube.
- **Tools deterministas** (luces, TV, volumen, alarmas, temporizador).
- **Canales Android** (`TimerActionChannel`, `AgendaActionChannel`,
  `LightVoiceChannel`, `PhoneVolumeChannel`).
- **Vosk, TLS, bucle de sesión**.

---

## 5. Fases

### Fase 1 — Determinista puro (sin modelo local)

1. **Aislar el dispatch**: extraer a `backend/deterministic_router.py` la lógica
   "texto → herramienta + parámetros", reusando `TOOL_INTENT` y los parsers
   existentes.
2. **Conectarlo al bucle de sesión**: reemplazar `model.start()` por el router,
   manteniendo el mismo `say()`/canales. Verificar que luces/TV/volumen/alarmas
   siguen igual.
3. **Apagar el modelo**: dejar de arrancar `llama-server`, el worker y el warm-up.
   Verificar arranque más rápido y estable.
4. Si el router no reconoce → **"No te entendí"** (DeepSeek llega en Fase 2).

**Criterio de aceptación:** todo lo que hoy funciona (luces, TV, volumen,
alarmas, temporizador, carita, reacciones) sigue igual, sin modelo local.

### Fase 2 — DeepSeek como fallback (después)

1. Añadir `backend/cloud_model.py` (cliente DeepSeek V4 Flash).
2. Hook: cuando el router determinista **falla** → llamar a DeepSeek con
   `[sistema + frase + lista de tools]`.
3. DeepSeek devuelve: **texto** (cuento/chiste/pregunta/historia) o **tool_call**
   (herramienta que identificó).
4. Si DeepSeek tampoco entiende → **"No te entendí"**.
5. Retirar `search_web` local (DeepSeek cubre búsqueda).
6. Selección dinámica de herramientas por DeepSeek (TV, lavadora, color de luces)
   sin configuración manual.

---

## 6. Limpieza previa (antes de ejecutar)

> Hacerla ANTES de la Fase 1. Inventariar y confirmar qué se borra para no perder
> nada que aún se use.

Candidatos a revisar/eliminar:

- **Modelos descargados** (pesados): `LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf` (~695 MB)
  y cualquier otro GGUF descargado.
- **Binarios/libs de inferencia**: `/data/local/doha-inference/llama-b11669/`
  (`llama-server`, `.so`).
- **Worker y warm-up**: `scripts/resident-model-worker.sh`,
  `scripts/prepare-conversation.py`.
- **Backups/artefactos temporales** de iteraciones anteriores en
  `artifacts/` y `/data/local/tmp` (solo los propios, conservando evidencia
  histórica que el repo documenta).

**Regla:** conservar lo determinista (tools, parsers, canales, app), borrar lo que
es solo de la IA local. Confirmar el inventario antes de borrar.

---

## 7. Decisiones de diseño (registradas)

1. **Carita/reacciones**: se mantienen iguales por ahora (no se ha decidido una
   reacción distinta para "razonando en la nube").
2. **Tools de la Fase 1**: solo los que ya existen (luces, TV, volumen, alarmas,
   temporizador). Al empezar a funcionar, se van agregando (lavadora, color de
   luces) — **todo debe quedar escalable fácil** (el router determinista y la
   lista de tools deben poder crecer sin reescribir).
3. **Memoria en DeepSeek**: por ahora **NO se le da memoria**. Solo se le manda
   el sistema + la frase actual. La memoria/conversación de sesión se decide
   más adelante.

---

## 8. Criterios de aceptación globales

- Nada de lo que hoy funciona deja de funcionar en cada fase.
- El arranque no depende de cargar un modelo local.
- El modo determinista es instantáneo.
- DeepSeek solo interviene cuando lo determinista no entiende.
- La carita y las reacciones se conservan.
- Añadir una herramienta nueva es **aditiva** (no reescribir el router ni los
  canales existentes).

---

## 9. Historial de cambios

- (fecha) — Creado el plan por fases. PRIORIDAD ALTA. Pendiente: limpieza previa.
- (fecha) — Limpieza previa ejecutada (~11 GB liberados: modelos Qwen candidato en
  teléfono y PC, `llama-reference-generic`/`src`, `inference-audit-venv`, `test-prompts`).
- (fecha) — **Fase 1 COMPLETADA**: router determinista (`deterministic_router.py`)
  desplegado y en uso; `AssistantToolsModel.start()` usa el router; llama-server
  apagado (worker + warm-up fuera del supervisor; `_NoModelOwner` + no-op
  `prepare()`/cache-purger en el backend); `start-ia.ps1` detecta por lock y espera
  solo 11436. Arranque sin modelo local.
- (fecha) — **Fase 2 (cliente DeepSeek) implementado y desplegado, inactivo hasta
  tener key**: `cloud_model.py` (cliente HTTPS a `api.deepseek.com`, sin memoria:
  sistema + frase, devuelve texto o tool_call); `deterministic_router.route_cloud`
  + `cloud_enabled`; `AssistantToolsModel.start()` dispara el fallback asíncrono
  solo si el determinista devuelve FALLBACK y hay key. Key inyectada por el
  supervisor desde `/opt/doha-voice/deepseek/api_key.txt` (`DEEPSEEK_API_KEY`).
  Sin key: comportamiento idéntico a Fase 1.
- (fecha) — Pendiente: probar Fase 2 con una key real; limpieza de código muerto
  (`interpret_result`, `continuation`, `latest_web`, `OwnedInference`, `search_web`); 
  añadir al catálogo de nube las 5 tools de TV restantes (tv_volume/tv_audio/
  tv_input/tv_open_app/tv_scene) que el router determinista ya despacha.
- (fecha) — Limpieza post-Fase 1 (segura, ejecutada): borrado el directorio del
  modelo local en el teléfono `/data/local/doha-inference/` (~942 MB: LFM 1.2B,
  binarios llama-b11669, worker/warm-up) — ya sin uso tras apagar llama-server.
  `prepare-conversation.py` retirado de `deploy-backend.sh` (ya no se despliega).
  **Pendiente de código muerto (NO tocado, enredado con tests + app Android):**
  `interpret_result`/`continuation`/`native_call`/`latest_web` y la cadena de
  búsqueda web (`internet_search`, `web_search`, `web_answers`, `worker_answers`),
  `search_web` en `TOOLS`/`TOOL_INTENT` y en el contrato `allowed_tools` de
  `VoiceConnection.java`, la clase `OwnedInference` de `conversation_voice.py`, y
  los scripts `resident-model-worker.sh`/`warm-server-worker.sh`/`prepare-conversation.py`.
  Sacarlos requiere actualizar la suite de tests y el contrato de la app; se hará
  en una pasada coordinada aparte.
