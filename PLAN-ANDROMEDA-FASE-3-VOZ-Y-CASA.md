# Plan Andrómeda — Fase 3: voz natural, cancelación, dispositivos y acceso remoto

> **Estado:** plan aprobado, en ejecución por fases.
> **Fecha:** 2026-11-03.
> **Reemplaza a:** `PLAN-ARQUITECTURA-ANDROMEDA-DEEPSEEK.md` (Fases 1 y 2 completadas; ese documento queda cerrado como historial).
> **Regla de oro:** nada se da por hecho sin recibo, medición o prueba por voz. No inventar endpoints, IDs, marcas ni protocolos.

---

## 1. Alcance

| # | Tema | Prioridad |
|---|---|---|
| A | Cancelación global ("cancélalo", "cancela eso", "detente") | **P0** |
| B | Confirmaciones cortas (recordatorios y alarmas) | **P0** |
| C | Voz más amena / humana (menos "bot") | P1 |
| D | Medir recursos del Moto **sin modelo de IA** | P1 |
| E | Luces sin configuración previa + guardar lo que guste | P2 |
| F | Registrar aspiradora robot y lavadora | P2 |
| G | App remota: cámara en vivo, fotos, alertas de voz, música | P3 |

**Fuera de alcance de esta fase (otra sesión, Claude):** caritas, reacciones, temas de Navidad y animaciones.

---

## 2. Línea base verificada (antes de tocar nada)

- **Motor:** backend determinista en `:11436`, sin llama-server ni modelo local. DeepSeek (`deepseek-flash`) solo como fallback de nube con key.
- **Voz:** TTS local de Android (`VoiceService.java`), voz offline en español (`es-US`), sin pitch/rate ajustados.
- **Confirmaciones actuales:** `AgendaSpeechPolicy.java` genera `dd/MM/yyyy 'a las' HH:mm:ss, hora de Bogotá` → por eso lee día, fecha, hora, minutos y segundos.
- **Luces:** `control_lights` ya existe, con presets y `LightPresetStore` (guardar color con nombre). Falta el caso "color arbitrario no configurado".
- **Cancelación:** hay `CancelSpeakerService`, `AgendaCancellation`, `CancelGrammarInstrumentation`, pero `CANCELACION-ANDROMEDA.md` quedó **bloqueada** (v57 no fiable) y el bug de volumen demuestra que no cubre todos los diálogos.
- **TV:** fase cerrada (`PLAN-INTEGRACION-TV-ANDROMEDA.md`, §18).

---

## 3. Fase A (P0) — Cancelación global

### 3.1 Bug reportado (reproducir primero)

```
Usuario: "baja el volumen"
Andrómeda: "¿Qué porcentaje?"
Usuario: "cancélalo"
Andrómeda: "¿Qué porcentaje?"   <-- INCORRECTO
```

Causa esperada: el diálogo de volumen (`VolumeDialogue`) no consume la intención de cancelación; el estado sigue esperando el porcentaje y vuelve a preguntar.

### 3.2 Comportamiento objetivo

| Momento | Frase | Resultado |
|---|---|---|
| Cualquier estado | "cancélalo", "cancela", "cancela eso", "detente", "para", "olvídalo" | Aborta la petición pendiente, dice la frase fija y termina la conversación |
| Mientras habla | cualquiera de las anteriores | Corta el TTS y cancela |
| Mientras escucha | cualquiera de las anteriores | Deja de escuchar y cancela |
| Acción ya ejecutada | — | No se deshace; se informa con honestidad |
| Después de cancelar | — | Vuelve a esperar "Andrómeda"; no hay que reactivar a mano |

Frase fija (local, sin modelo ni backend): **"Está bien, aquí estaré cuando lo necesites."**

### 3.3 Diseño

1. **Un solo punto de verdad:** una compuerta `CancelGate` en el estado conversacional que se evalúa **antes** que cualquier parser de diálogo (volumen, agenda, temporizador, luces, TV, nube).
2. **Gramática única:** lista cerrada de frases de cancelación, compartida Python/Java (hoy hay gramática duplicada y divergente).
3. **Ningún diálogo puede re-preguntar tras cancelar:** al cancelar se invalida el `pending` y se limpia el contexto; si llega un turno tardío, se descarta.
4. **Cobertura obligatoria:** volumen, agenda (recordatorios), alarmas, temporizador, confirmación de luces, TV, y fallback de nube.
5. **TTS:** `tts.stop()` + no encolar la respuesta pendiente.

### 3.4 Criterios de aceptación

- El bug de 3.1 deja de reproducirse (probado por voz, 3 veces).
- "detente" funciona en los 7 flujos listados.
- Cancelar durante el TTS no deja audio huérfano.
- Tests Python + Java del mismo caso pasan; app recompilada e instalada.

### 3.5 Riesgo

`CANCELACION-ANDROMEDA.md` documenta que la v57 falló. **No se reutiliza ese diseño tal cual**: se implementa la compuerta mínima y se prueba flujo por flujo, sin refactor grande.

---

## 4. Fase B (P0) — Confirmaciones cortas

### 4.1 Problema

Hoy un recordatorio se confirma leyendo **día, fecha, hora, minutos y segundos**:

> "Recordatorio guardado: … Para el 03/11/2026 a las 19:45:00, hora de Bogotá."

Suena a robot y es innecesariamente largo.

### 4.2 Formato objetivo

| Caso | Antes | Después |
|---|---|---|
| Recordatorio | "…03/11/2026 a las 19:45:00, hora de Bogotá." | **"Listo, te aviso a las 7:45."** |
| Recordatorio con día | "…Para el 04/11/2026 a las 07:00:00…" | **"Listo, mañana a las 7."** |
| Alarma | "Alarma programada para el …" | **"Alarma puesta para las 7."** |
| Alarma semanal | "Alarma semanal guardada: miércoles 07:00. Próxima: …" | **"Alarma puesta para los miércoles a las 7."** |

Reglas:

- Sin segundos, sin año, sin "hora de Bogotá" (salvo que el usuario pregunte la zona).
- "Mañana"/"hoy"/"el miércoles" en vez de fecha numérica cuando aplique.
- Si el usuario pide detalle ("¿a qué hora queda?", "dime la fecha"), se responde completo.
- La frase se genera en **Python y Java a la vez** (misma lógica, mismos tests). Si divergen, la app rechaza el evento y el motor se cae (`voice_peer_closed`).

### 4.3 Criterios de aceptación

- 10 confirmaciones seguidas por voz: ninguna dice segundos ni año.
- "¿a qué hora era?" devuelve el detalle completo.
- Tests de `AgendaSpeechPolicy` actualizados y verdes.

---

## 5. Fase C (P1) — Voz más amena

### 5.1 Diagnóstico honesto

No es solo el texto: hoy hay **una sola voz**, **velocidad fija**, **plantillas repetidas** y frases largas. Eso es lo que suena a bot.

### 5.2 Palancas, de barata a cara

| # | Palanca | Costo | Efecto |
|---|---|---|---|
| 1 | Elegir la mejor voz offline del motor instalado (probar todas las `es-*` y quedarse con la más natural) | 0 | Alto |
| 2 | Ajustar rate (~0.95–1.05) y pitch por tipo de respuesta | 0 | Medio |
| 3 | Variar plantillas (3–5 formas por respuesta) para no repetir la misma frase | 0 | Alto |
| 4 | Frases más cortas, sin enumeraciones ni tecnicismos | 0 | Alto |
| 5 | Muletillas naturales ("Listo", "Ya está", "Ojo", "Claro") con moderación | 0 | Medio |
| 6 | Pausas con puntuación y frases separadas (no una sola frase larga) | 0 | Medio |
| 7 | TTS neuronal local (Piper u otro) en el chroot | CPU/RAM + latencia | Muy alto, **solo si D demuestra que hay recursos** |

**Decisión:** hacer 1–6 primero y evaluar por voz. La 7 es un gate aparte que depende de la Fase D.

### 5.3 Criterios de aceptación

- Prueba ciega con el usuario: 10 respuestas, decir cuáles suenan "robot" y cuáles no.
- Objetivo: ≤2 de 10 marcadas como robot.
- Sin aumento perceptible de latencia (medido en D).

---

## 6. Fase D (P1) — Recursos del Moto sin modelo

### 6.1 Qué medir

| Métrica | Fuente |
|---|---|
| RAM del backend y de la API TV (RSS) | `/proc/<pid>/status`, `dumpsys meminfo` |
| RAM libre del sistema | `/proc/meminfo` |
| CPU por proceso | `top -b -n1`, `/proc/<pid>/stat` |
| Batería (nivel, %/h, temperatura) | `dumpsys battery`, `dumpsys batterystats` |
| Temperatura | zonas térmicas `/sys/class/thermal/thermal_zone*` |
| Almacenamiento | `df -h` (chroot y Android) |
| Latencia por comando | `metrics.json` del motor (ya existe) |

### 6.2 Entregable

- Script reproducible `scripts/medir-recursos.ps1` (desde el PC, vía adb) que saque un informe de 5 minutos en reposo y 5 minutos con uso.
- Línea base documentada: reposo / escuchando / ejecutando un comando / TTS.
- Umbrales de alerta (ej.: RAM libre < 400 MB, batería > 40 °C, CPU sostenida > 60 %).
- Sin modelo local: el informe debe demostrar que el motor es liviano.

### 6.3 Criterios de aceptación

- Dos ejecuciones seguidas dan cifras comparables (±10 %).
- Se identifican los 3 procesos que más consumen y su costo real.
- Informe guardado con fecha en este documento (§12).

---

## 7. Fase E (P2) — Luces sin configuración previa

### 7.1 Lo que ya existe

`control_lights` (determinista), `LightCommandParser`, adaptadores LotusLamp X y MR Star, presets y guardado de color con nombre (`LightPresetStore`).

### 7.2 Lo que falta

1. **Color arbitrario sin preset:** "pon las luces en verde menta", "azul cielo", "cálido suave", "#7FFFD4". El parser resuelve el nombre contra una tabla de colores; si no lo conoce, **pregunta una vez** ("¿más claro u oscuro?") en vez de fallar.
2. **Ajuste fino por voz:** brillo, saturación y temperatura si el dispositivo lo permite.
3. **Guardar lo que gustó:** "guarda esta luz como atardecer" persiste el estado actual (color + brillo) como preset. "Pon la luz de atardecer" lo recupera.
4. **Sin reescribir:** se agrega al catálogo de tools; nada de tocar las tools existentes.

### 7.3 Criterios de aceptación

- 10 colores dichos por voz, incluidos 3 no configurados, aplicados correctamente o con una sola repregunta.
- "guarda esta luz como X" → apagar/encender → "pon X" recupera el estado guardado.
- No se rompen los presets actuales.

---

## 8. Fase F (P2) — Aspiradora robot y lavadora

### 8.1 Diseño: registro de dispositivos

Un **registro aditivo** (no reescribir el router):

```
backend/devices/registry.py      -> lista de dispositivos disponibles
backend/devices/<marca>.py       -> adaptador (protocolo, estado, comandos)
```

Cada dispositivo declara: `id`, `nombre`, `capacidades`, `estado()`, `ejecutar(accion, params)`. El router solo enruta si el dispositivo está registrado y disponible.

### 8.2 Herramientas previstas

| Dispositivo | Tools |
|---|---|
| Aspiradora | `vacuum_start`, `vacuum_pause`, `vacuum_dock`, `vacuum_status` |
| Lavadora | `washer_start`, `washer_status`, `washer_stop` |

Reglas: nunca afirmar éxito sin recibo del dispositivo; si está offline, decirlo.

### 8.3 Datos que faltan (preguntar antes de implementar)

- Marca y modelo exactos de aspiradora y lavadora.
- App oficial de cada una (Tuya/Smart Life, Xiaomi, LG ThinQ, Samsung SmartThings…).
- ¿Están en la misma red WiFi que el Moto? ¿Aceptan control local o solo nube?
- ¿Se puede/quieres usar la nube del fabricante o solo local?

**No se implementa nada hasta tener estos datos.** Sin ellos, cualquier adaptador sería inventado.

### 8.4 Criterios de aceptación

- "Aspira la sala" / "manda la aspiradora a su base" con recibo real.
- "¿Cómo va la lavadora?" con estado real.
- Si el equipo está apagado/offline: respuesta honesta, sin fingir.

---

## 9. Fase G (P3) — App remota y capacidades nuevas

> Esta fase es la más grande. Se divide en 4 entregas independientes, en este orden.

### G1. App remota (base)

- **Formato:** PWA primero (web) — más rápida de iterar que una app nativa y sirve en Android/iOS/PC.
- **Acceso:** túnel (Cloudflare Tunnel o similar) + autenticación por token; **nunca** abrir puertos del router.
- **Funciones mínimas:** estado de Andrómeda, enviar texto/orden, ver últimas respuestas, ver métricas de D, y las entregas G2–G4.

### G2. Cámara en vivo y fotos

| Opción | Ventaja | Costo/riesgo |
|---|---|---|
| Fotos bajo demanda (`termux-camera-photo` o CameraX) | Simple, bajo consumo | No es "en vivo" |
| Video en vivo (WebRTC o RTSP con ffmpeg) | Tiempo real | CPU/temperatura/batería altos; NAT/túnel |
| Fotos periódicas + alerta | Equilibrio | No es continuo |

Reglas: indicador visible cuando la cámara está activa; el usuario decide cuándo; medir consumo antes de dejarlo continuo.

### G3. Alertas de voz ("oigan, laven la losa")

Flujo:

1. Desde la app remota, grabas un audio.
2. El backend lo guarda como alerta pendiente.
3. El Moto la detecta (sondeo ligero o WebSocket con reconexión) y la reproduce **apenas** la recibe.
4. La app confirma "reproducido" (acuse real, no supuesto).

Requisitos: funcionar con la pantalla apagada, respetar el volumen actual, no pisar una conversación en curso (esperar a que termine o pedir prioridad).

### G4. Música por voz (YouTube Premium)

**Realidad técnica:** YouTube Music no tiene API pública de reproducción. Opciones honestas:

1. Abrir YouTube Music con búsqueda (`intent`/URI) y que el usuario elija.
2. Controlar reproducción con teclas multimedia (`input keyevent` play/pause/next) — funciona con root.
3. "Pon música de X" → abre la búsqueda; "pausa", "siguiente", "sube el volumen" → media keys.

No prometer "reproduce automáticamente la canción exacta" sin verificarlo en el dispositivo.

### Criterios de aceptación G

- G1: abrir la app desde fuera de casa con token válido; sin token, denegado.
- G2: foto bajo demanda en <10 s y video en vivo estable 60 s (si se elige).
- G3: grabar "oigan, laven la losa" → suena en el Moto en <15 s, con acuse.
- G4: "pon música de X" abre YouTube Music; "pausa/siguiente" funciona.

---

## 10. Orden de ejecución

| Orden | Fase | Depende de |
|---|---|---|
| 1 | A — Cancelación global | — |
| 2 | B — Confirmaciones cortas | — |
| 3 | C — Voz amena (palancas 1–6) | — |
| 4 | D — Recursos | — |
| 5 | E — Luces sin preset | datos de luces |
| 6 | F — Aspiradora y lavadora | datos de dispositivos |
| 7 | G1 → G2 → G3 → G4 | D (mediciones) y decisiones de hosting |

A y B son bugs de uso diario: van primero y se prueban por voz antes de seguir.

---

## 11. Decisiones pendientes (preguntas abiertas)

1. **Aspiradora y lavadora:** marca, modelo y app oficial.
2. **Luces:** ¿los dos modelos actuales (LotusLamp X y MR Star) son los definitivos? ¿Habrá más?
3. **App remota:** ¿PWA o app nativa Android? ¿Hosting en Render, Cloudflare Tunnel en el PC, u otro?
4. **Cámara:** ¿fotos bajo demanda o video en vivo? (el video en vivo consume batería/temperatura).
5. **Alertas de voz:** ¿prioridad sobre conversación en curso o esperar?
6. **Música:** ¿YouTube Music instalado en el Moto? ¿Sesión Premium activa?

---

## 12. Registro de mediciones

| Fecha | Estado | RAM libre | RSS backend | CPU | Batería | Temp | Notas |
|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | pendiente Fase D |

---

## 13. Criterios globales

- Todo cambio de parser o frase se hace **Python y Java a la vez**, con tests en ambos.
- Toda acción reporta recibo; nunca se afirma éxito sin confirmación.
- Las tools nuevas son aditivas: no se reescriben las existentes.
- Cada fase se valida **por voz**, no solo con tests.
- Sin secretos en el repo (keys, tokens).

---

## 14. Historial

- **2026-11-03** — Plan creado. Cierra la fase de TV y el plan de arquitectura determinista + DeepSeek. Fase A/B (cancelación y confirmaciones) declaradas P0 por ser bugs de uso diario.
