# Plan de mejoras de Andrómeda — 2026-11-09

Documento maestro de la siguiente etapa. Aplica a **las dos superficies**:
la casa (Moto G8 Plus + Debian) y la **app móvil** de la familia, a través del
contrato versionado. Nada de esto puede quitar funciones que ya funcionan.

## Reglas irrenunciables

1. **Cero regresiones.** Voz local, palabra de activación, luces, TV, alarmas,
   recordatorios, temporizadores, volumen, mensajes de voz, cámara, chat remoto,
   batería y watchdog siguen funcionando igual.
2. **La casa manda.** El móvil es un mando; las herramientas viven y se ejecutan
   en el Moto. La app solo pide y muestra.
3. **Respaldo determinista siempre.** Si DeepSeek no responde, no hay crédito,
   hay timeout o error, el router y las herramientas deterministas actuales
   siguen resolviendo exactamente como hoy. La nube es una mejora, nunca un
   requisito.
4. **Ninguna herramienta se ejecuta por confianza en el modelo.** Toda llamada
   pasa por la capa de validación existente (esquema, guardas literales,
   confirmaciones, idempotencia, límites). El modelo elige; la casa valida.
5. **Privacidad local.** Audio, memoria y mensajes se quedan en el Moto. La nube
   solo recibe el texto mínimo necesario para planificar, sin credenciales ni
   datos sensibles.
6. **Contrato versionado.** Cada cambio de API sube versión, actualiza
   `limits.json`, y se vendoriza con `SHA256SUMS` en A y B.
7. **Prueba en real antes de declarar hecho.** Nada se anuncia como funcionando
   sin comprobación física (TV apagada de verdad, luz cambiada, mensaje oído).

## Resumen y prioridad

| # | Mejora | Superficie | Prioridad | Riesgo | Estado |
|---|---|---|---|---|---|
| 1 | IA con herramientas (DeepSeek) + respaldo determinista | Ambas | **P0** | Medio | ✅ Desplegado |
| 2 | Historial de mensajes de voz 24 h (repetir/listar/reproducir uno) | Ambas | **P0** | Bajo | ✅ Cerrado según prueba humana del usuario |
| 3 | Memoria temporal por sesión/día y contexto en DB | Ambas | **P1** | Medio | 🟡 Alcance acordado; política y diseño técnico pendientes |
| 4 | Color por hexadecimal y guardado con nombre | Ambas | **P1** | Bajo | 📝 Diseñado |
| 5 | Música local / YouTube Premium | Ambas | **P2** | Medio | 📝 Diseñado |
| 6 | Xbox: encender, abrir YouTube o un juego | Ambas | **P2** | Alto | 📝 Investigación |

Orden de implementación: **1 → 2 → 3 → 4 → 5 → 6**. Cada bloque entra con
bandera de función, pruebas y despliegue incremental; si algo falla, se apaga
la bandera y queda el comportamiento actual.

---

## 1. IA con herramientas (DeepSeek) y respaldo determinista

### Objetivo
Que el usuario hable natural y DeepSeek decida **qué herramienta usar**
(luces, TV, alarmas, recordatorios, temporizadores, volumen, mensajes, memoria,
música, Xbox), sin perder la velocidad ni la fiabilidad actuales. Si la API no
está disponible, todo sigue como hoy.

### Punto de partida real
- `backend/cloud_model.py` ya consulta DeepSeek como **último recurso** cuando el
  router determinista no reconoce la frase, con el mismo catálogo de herramientas
  y `tool_choice=auto`.
- `backend/assistant_tools_model.py` valida y ejecuta; Android sigue siendo el
  único ejecutor de eventos.
- La clave se lee de `DEEPSEEK_API_KEY` (archivo privado inyectado por el
  supervisor); nunca viaja al móvil ni se registra.

### Diseño propuesto
1. **Planificador único** (`ToolPlanner`) con dos motores intercambiables:
   - **Primario:** DeepSeek con *function calling* sobre el catálogo completo.
   - **Respaldo:** router determinista actual, sin cambios de comportamiento.
2. **Cuándo entra la nube:**
   - Fase conservadora: solo frases que el router no resuelve con seguridad.
   - Fase ampliada (objetivo del usuario): DeepSeek propone herramienta también
     para frases naturales nuevas, pero el router determinista tiene **prioridad
     para comandos críticos** (autodestrucción, confirmaciones, cancelaciones,
     seguridad) y para frases exactas ya validadas.
   - La decisión final de fase se toma tras medir aciertos y latencia.
3. **Contrato del planificador:**
   - Entrada: frase actual + historial mínimo permitido + estado de la casa
     (qué está encendido, propuestas pendientes).
   - Salida: `{tool, arguments}` o `{reply}`.
   - El modelo **no** inventa éxito ni estado; los textos de confirmación los
     genera la casa con el resultado real.
4. **Guardas que no se tocan:**
   - Esquema estricto por herramienta y guardas literales (`tool_intent_matches`).
   - Confirmación de dos pasos donde ya existe.
   - Un "sí" remoto no confirma una propuesta local (y viceversa).
   - Idempotencia por `id`, rate limit 30/min, `command_wait_ms` 40 s.
   - TV síncrona y verificada; luces con su confirmación.
5. **Salud y presupuesto de la API:**
   - Timeout corto (≤ 20 s y siempre antes del deadline del turno).
   - Circuit breaker: N fallos consecutivos → se deja de llamar X minutos.
   - Detección explícita de `401/402/429/5xx`, DNS y timeout → respaldo.
   - Presupuesto diario/mensual configurable y aviso al usuario cuando la clave
     se quede sin crédito.
   - Métricas en log: `planner=deepseek|deterministic`, motivo del fallback,
     latencia, herramienta elegida, validación.
6. **Seguridad de la clave:**
   - Solo en la casa, en archivo privado; jamás en la app, el contrato ni el log.
   - Rotación documentada; el respaldo determinista no depende de ella.
7. **Memoria de contexto para el planificador:** solo lo necesario (últimos
   turnos permitidos + memoria recuperada, ver bloque 3), con tope de tokens.

### Pruebas
- Simulación de 401, 402, 429, 500, timeout y DNS caído → la frase se resuelve
  igual que hoy.
- Batería de frases en español: herramienta correcta, herramienta prohibida,
  frase ambigua, negación, relato ("mi vecino encendió la TV") y doble intención.
- No regresión de los tests actuales de intérpretes y política remota.
- Medición de latencia percibida: la nube no puede alargar el turno más de lo
  permitido.

### Estado real (2026-11-10)
- Implementado y desplegado: `tool_planner.py` decide motor y cortacircuitos;
  `cloud_model.reason_ex` clasifica cada fallo; `route_cloud_first` mantiene las
  guardas y ejecuta con `_dispatch`; `assistant_tools_model` registra motor,
  motivo y latencia sin texto ni credenciales.
- Decisión del usuario: **DeepSeek decide todas las órdenes** (más natural e
  interpreta mejor frases mal oídas), con el determinista siempre disponible
  como respaldo si no hay red, saldo o respuesta.
- Prioridad local fija: cancelar, autodestrucción y TV nunca esperan a la nube.
- Sin red o timeout, el cortacircuitos abre al primer fallo y la orden siguiente
  va directa al determinista; sin clave/saldo abre 1 h y avisa una sola vez.
- Probado en vivo: latencias 1,6–3,1 s; "cuéntame más" mantiene el hilo (incluido
  el chat del teléfono); con la clave renombrada, Navidad y temporizador siguen
  funcionando y una frase natural cae en el determinista.

### Criterios de aceptación
- Con clave válida, órdenes naturales nuevas se ejecutan y se confirman con el
  estado real.
- Sin clave, sin crédito o sin red, **todo** lo que hoy funciona sigue igual.
- Ninguna acción crítica se ejecuta sin las confirmaciones actuales.

### Riesgos y mitigación
- Latencia/coste → router primero para lo conocido, caché de frases, límites.
- Alucinación de herramienta → validación estricta y respuesta de aclaración.
- Dependencia de la nube → respaldo obligatorio probado en cada release.

---

## 2. Historial de mensajes de voz (24 h)

### Objetivo
Poder decir: «repite el último mensaje», «¿qué mensajes tengo?», «reproduce el
mensaje de mamá», «reproduce el segundo», tanto en la casa como en la app.

### Punto de partida real
- `VoiceMessageStore` ya guarda notas de voz con TTL de 24 h y las reproduce con
  alarma + anuncio. Hoy prioriza la cola de no reproducidos; falta conservar el
  historial reproducido y exponerlo.

### Diseño
1. **Historial local** con metadatos: `id`, remitente (nombre del dispositivo),
   fecha/hora, duración, estado (nuevo/reproducido), tamaño.
2. **Retención:** 24 h desde la recepción, tope de 50 mensajes / 50 MB; purga al
   arrancar y cada hora. Sin nube.
3. **Endpoints nuevos** (contrato 1.5.0): listar historial, estado de uno,
   descargar audio y marcar reproducido. Reutilizar el audio existente.
4. **Órdenes de voz y chat** (casa y app):
   - «repite el último mensaje» / «reproduce el último».
   - «¿qué mensajes tengo?» → lista breve con remitente y hora.
   - «reproduce el mensaje de [persona]» / «el de las [hora]» / «el segundo».
   - «borra el último mensaje» (con confirmación) si se aprueba.
5. **App:** pestaña o sección **Mensajes** con lista, reproducción, quién lo
   envió, hora y borrado individual. Indicador de no reproducidos.
6. **Privacidad:** excepción explícita y acotada a la regla histórica de no
   guardar audio humano: solo notas dirigidas a Andrómeda, solo 24 h, solo en el
   Moto. Sin transcripción por defecto (opcional y configurable si se pide).
7. **No interferencia:** un mensaje nuevo sigue sonando con alarma fuerte +
   «ha llegado un mensaje nuevo» antes de reproducirse.

### Pruebas
- Enviar 3 mensajes de 2 remitentes, listar, repetir el último, reproducir uno
  concreto por nombre y por posición.
- Caducidad a las 24 h, tope de tamaño y purga.
- Reproducir mientras hay una orden en curso sin romper el turno.

### Criterios de aceptación
- Las tres órdenes funcionan en casa y en app, con el audio correcto.
- El historial no crece sin límite y desaparece solo a las 24 h.

---

## 3. Memoria temporal por sesión/día y contexto conversacional

### Objetivo
Mantener continuidad útil durante una sesión o un día, sin crear una memoria
permanente. El contexto de conversación que hoy vive en RAM de Android también
pasará a persistencia local en base de datos, con límites y caducidad.

### Diseño
1. **Persistencia:** SQLite en Debian del Moto G como fuente autoritativa y
   compartida por la voz local y la app. El backend conserva el texto; Android
   maneja solo IDs/contadores y solicita el chat seleccionado.
2. **Retención:** una charla vence solo tras 1 h sin actividad humana. No hay
   purga de medianoche: una conversación viva debe seguir recordando al cambiar
   de día. El catálogo conserva como máximo 4 charlas y, al abrir una quinta,
   desaloja la menos usada en vez de bloquear la voz. Al entrar por voz se
   continúa automáticamente la charla viva más reciente; «recuerdas …» sigue
   sirviendo para buscar un tema concreto.
3. **Contenido:** persistir solo el contexto textual acotado; no grabar audio ni
   generar resúmenes/recuerdos automáticos. Las notas de voz recibidas son otra
   función y conservan su retención independiente de 24 h.
4. **Recuperación eficiente:** cargar únicamente turnos recientes y datos
   pertinentes, con límites de texto/tokens; SQLite reduce la retención del
   catálogo en heap, pero el contexto activo todavía debe cargarse
   temporalmente para enviarlo al modelo.
5. **Gestión:** poder iniciar una charla limpia y borrar la memoria temporal.
   Si el almacén falla o una charla caduca entre el saludo y la primera frase,
   el turno sigue por voz con memoria vacía; nunca se cierra la sesión ni se
   apaga el residente por un fallo de memoria.
6. **Seguridad:** consultas parametrizadas, aislamiento por conversación y
   caducidad verificable; no incluir audio ni texto privado en métricas/logs.
7. **Protocolo:** subir la versión coordinadamente. El saludo autenticado no
   enviará el catálogo con texto: la selección pedirá el contexto al backend,
   que valida ID, estado y caducidad antes de restaurarlo.
8. **Pruebas:** límites, expiración, reinicio del proceso, aislamiento de chats,
   borrado y reconstrucción del contexto desde DB, además de regresión de voz.

### Criterios de aceptación
- El contexto autorizado puede restaurarse desde DB después de cerrar/reiniciar
  el proceso, sin restaurar entradas vencidas.
- El contenido expira según la política elegida y no crece sin límite.
- No se guarda audio; el alcance de guardado del texto se informa claramente.

### Secuencia de implementación
1. Crear SQLite y pruebas offline de límites, expiración por inactividad y
   desalojo de la charla menos usada; todavía sin tocar datos del teléfono.
2. Cambiar el protocolo coordinadamente: catálogo de metadatos, selección y
   carga del chat elegido desde Debian; no enviar todos los textos al abrir voz.
3. Persistir únicamente turnos aceptados y respuestas con TTS completado; al
   fallar/interrumpirse TTS, conservar la pregunta sin inventar respuesta. El
   turno activo se materializa en RAM del backend solo durante la inferencia.
4. Conservar el vencimiento de una hora inactiva y descartar la purga de
   medianoche; al entrar por voz, continuar la charla viva más reciente.
5. Ejecutar suites/fixtures y revisar respaldo, compatibilidad, gestión de
   listado/olvido autenticada y plan de reversa.
   El despliegue y reinicio se acuerdan aparte porque pueden perder el contexto
   temporal que exista en RAM.

---

## 4. Color por hexadecimal y guardado con nombre

### Objetivo
«Pon las luces en #FF8800» sin que ese color esté guardado, y luego decidir si
se guarda con un nombre («atardecer»).

### Diseño
1. **Parser de color:** acepta `#RRGGBB`, `RRGGBB`, «hexadecimal …» y variantes
   que produzca Vosk; valida rango y normaliza.
2. **Aplicación:** se reutiliza la conversión y el envío BLE ya existentes. Si el
   protocolo de la luz no admite RGB arbitrario, se aplica el color válido más
   cercano y **se dice la verdad** («lo más parecido disponible»).
3. **Guardado opcional:** tras aplicar, pregunta «¿Quieres guardarlo? ¿Con qué
   nombre?». Guarda en la lista de colores con nombre; si el nombre existe,
   pide confirmación para reemplazar.
4. **Gestión:** listar, renombrar y borrar colores guardados desde voz y app.
5. **Doble superficie:** la app muestra el selector y permite enviar la orden;
   la casa ejecuta y confirma.
6. **Pruebas:** hex válido, inválido, sin `#`, con letras, duplicado, cancelar
   guardado, dos luces a la vez.

### Criterios de aceptación
- El color pedido se aplica de verdad y se confirma el resultado real.
- Se puede guardar con nombre y reutilizar después.

---

## 5. Música (YouTube Premium / archivos locales)

### Realidad técnica (importante)
- YouTube y YouTube Music **no ofrecen una API pública local** para “poner esta
  canción” y controlar la reproducción desde otra app.
- Se puede **abrir** YouTube/YouTube Music con una búsqueda mediante *intent*
  de Android, pero no garantizar que empiece a sonar la canción exacta.
- Descargar audio de YouTube incumple sus términos; **no se hará scraping ni
  descarga desde YouTube**.
- Para música fiable y sin depender de la nube: **biblioteca local** de archivos
  que el usuario aporte legalmente (MP3/M4A/OGG) en el Moto.

### Diseño
1. **Biblioteca local (recomendada):**
   - Carpeta `/sdcard/Music/Andromeda`; escaneo e índice con título, artista,
     álbum y duración.
   - Reproductor en la app Android (MediaPlayer/ExoPlayer) con foco de audio,
     pausa, siguiente, anterior, aleatorio y volumen.
   - Órdenes: «pon música», «pon [canción/artista]», «siguiente», «pausa»,
     «reanuda», «sube/baja el volumen», «¿qué está sonando?».
2. **YouTube / YouTube Music (opcional, limitado):**
   - Abrir la app con búsqueda o con el video pedido; el usuario pulsa play si
     hace falta.
   - Premium es necesario para segundo plano; no se promete control total.
   - Se documenta el límite y no se afirma que Andrómeda “puso” la canción si no
     pudo verificarlo.
3. **Concurrencia con la voz:** ducking (bajar música) mientras Andrómeda habla y
   restaurar después; pausa en llamadas y alarmas.
4. **App y casa:** la casa reproduce en su altavoz/Bluetooth; la app puede
   controlar la reproducción de la casa y, si se aprueba, reproducir en el propio
   teléfono.
5. **Contrato:** endpoints `/v1/music` (estado, play/pause/next, biblioteca).

### Criterios de aceptación
- Con archivos locales, una canción pedida suena y se puede pausar/cambiar.
- Sin archivos, Andrómeda lo dice claro y ofrece abrir YouTube, sin inventar.

---

## 6. Xbox: encender, abrir YouTube o un juego

### Realidad técnica
- **Encendido:** viable por Wake-on-LAN si la consola tiene “Instant-on” y
  “Encendido por red” activos. Se puede verificar por presencia en red.
- **Apagado:** no hay API local oficial y fiable; hay protocolos no oficiales
  (SmartGlass/HA) que deben validarse antes de prometer nada.
- **Abrir YouTube o un juego:** no existe API pública oficial para lanzar
  aplicaciones/juegos en Xbox desde la red local. Las vías posibles son no
  oficiales o por HDMI-CEC desde la TV, y no garantizan seleccionar una app
  concreta. **No se promete control total.**
- El Moto no tiene emisor infrarrojo.

### Plan por fases
1. **Fase A — presencia y encendido:** detectar la Xbox en red, WoL y verificar
   que despertó. Orden: «enciende la xbox», «¿está encendida la xbox?».
2. **Fase B — apagado:** investigar el protocolo que use la consola; si no es
   fiable, no implementarlo y decirlo con claridad.
3. **Fase C — abrir YouTube/juego:** investigar SmartGlass/Home Assistant/CEC
   con pruebas reales. Solo se implementa si se puede **verificar** que abrió lo
   pedido; si no, Andrómeda responde que no puede y ofrece la alternativa.
4. **Requisitos del usuario:** confirmar modelo exacto, si está en la misma red,
   si tiene Instant-on y si la TV permite CEC.

### Criterios de aceptación
- Encendido y estado verificados de verdad (no solo “enviado”).
- Ninguna promesa de abrir juegos si la vía no está validada.

---

## 7. App móvil: cambios de interfaz

- Mantener las pestañas actuales (Chat, Voz, Cámara, Enlace) y su diseño.
- Añadir secciones nuevas sin saturar: **Mensajes**, **Memoria**, **Música** y
  **Colores**; Xbox solo cuando exista algo real que controlar.
- Chips/atajos nuevos: «repite el último mensaje», «¿qué mensajes tengo?»,
  «recuerda que…», «pon la luz en #…», «pon música», «enciende la xbox».
- Mostrar siempre lo que Vosk entendió antes de la respuesta (ya existe).
- Errores claros y accionables (por ejemplo, si el nombre de la casa no resuelve,
  sugerir la IP).

## 8. Contrato y compatibilidad

- Subir a **1.5.0** al añadir historial de mensajes, memoria y música.
- Actualizar `limits.json` y los tests de ambas implementaciones (Python y
  Java/Kotlin) a la vez.
- Copia vendorizada con `SHA256SUMS` en A y B; un test falla si divergen.
- La app vieja debe seguir funcionando contra la casa nueva (campos nuevos
  opcionales; nunca romper los existentes).

## 9. Estabilidad y no regresión

- Cada bloque detrás de bandera de configuración, con rollback inmediato.
- Watchdog del motor y del guard de batería se mantienen.
- Pruebas antes de cada despliegue: suites actuales + nuevas de fallo.
- Canario en la casa y luego en un móvil de la familia; después el resto.
- Ningún cambio toca las imágenes borradas del repo ni archivos ajenos.

## 10. Decisiones pendientes del usuario

1. ✅ **Resuelto:** DeepSeek decide **todas** las órdenes; el determinista sigue
   como respaldo permanente (probado quitando la clave).
2. ✅ **Resuelto:** mensajes de voz: solo audio 24 h, sin transcripción.
3. Música: ¿la familia aportará archivos locales? ¿Qué formatos y dónde?
4. Xbox: modelo exacto, red, Instant-on y si la TV tiene CEC activo.
5. ✅ **Resuelto:** memoria temporal en SQLite de Debian del Moto G; máximo 4
   charlas, 24 turnos por charla y 5.700 unidades UTF-16; vence tras una hora
   sin actividad humana. Sin purga de medianoche: la charla viva se continúa al
   entrar por voz y, al abrir una quinta, se desaloja la menos usada.
6. Contrato 1.5.0: el usuario no lo ve necesario todavía; se sube solo cuando la
   app móvil vaya a usar los campos nuevos (serán opcionales y compatibles).

## 11. Registro de avance

| Fecha | Bloque | Estado | Nota |
|---|---|---|---|
| 2026-11-09 | Plan creado | ✅ | Registro de las seis mejoras y reglas |
| 2026-11-10 | 1. IA con herramientas | ✅ | DeepSeek primero (`ANDROMEDA_PLANNER=deepseek_first`), determinista de respaldo; probado en vivo con y sin clave (`bf17587`, `f913d0f`) |
| 2026-11-10 | Autoarranque y recuperación | ✅ | Usuario confirmó reinicio real después de quitar el bloqueo: motor activo y micrófono abierto. Gateway `/v1/health` también informó residente/voz remota/Tailscale activos y respondió una orden. APK `andromeda-autostart-v3.apk`; lock obsoleto y caída simulada recuperados. |
| 2026-11-10 | 2. Mensajes 24 h | ✅ | Usuario confirma que repitió/reprodujo y funcionó; bloque cerrado según su prueba. |
| 2026-11-10 | 3. Memoria temporal | 🟡 | Desplegado al Moto G8 Plus: backend SQLite y APK actualizados. Validado en el dispositivo: permisos DB 0700/0600, integridad SQLite, handshake TLS de voz sin abrir micrófono, catálogo autenticado solo con metadatos y borrado confirmado de un chat de prueba; DB quedó vacía. Residente y servicios restaurados. Suites Python y build APK aislado pasan. Pendiente prueba de una conversación real con voz/TTS; memoria textual sin audio/resúmenes y notas de voz TTL separado 24 h. |
| 2026-11-11 | 3. Memoria temporal | 🟠 | Corregido y desplegado: sin purga de medianoche, la charla viva sobrevive al cambio de día; al abrir una quinta charla se desaloja la menos usada; la entrada por voz continúa la charla viva más reciente y los fallos de memoria ya no cierran sesión ni apagan el residente. DB vacía (la purga de 00:00 había borrado todo); residente y servicios reiniciados. Pendiente prueba humana de voz y de un temporizador de 1 minuto. |
| | 4. Color hex | 📝 | Pendiente |
| | 5. Música | 📝 | Pendiente |
| | 6. Xbox | 📝 | Pendiente de datos de la consola |

---

## 12. Estado actual y siguiente orden (2026-11-11)

### Cerrado o desplegado en esta etapa
- **IA con herramientas:** DeepSeek primero, respaldo determinista, cortacircuitos corregido (cancelaciones y rechazos ya no lo abren), memoria de acciones completadas.
- **Memoria temporal:** SQLite en Debian, sin purga de medianoche, 4 charlas con desalojo LRU, 24 turnos / 5.700 unidades, 1 h de inactividad, entrada por voz que continúa la charla viva. Contexto del modelo acotado: 12 mensajes / 6.000 caracteres en la nube y 1.536 tokens en el modelo local; checkpoint cada 20 turnos y época máxima de 64.
- **Mensajes de voz:** el último audio enviado por la app se reprodujo correctamente (`status=played`); el fallo era la memoria del turno, ya corregido.
- **Audio:** voz y recordatorio por `SpeakerSpeech`/AudioTrack con altavoz preferido; recordatorio con uso de alarma y foco; jingle con uso de alarma verificado (`usecase RINGTONE`, `Speaker ON`).
- **Vigilante:** ahora también levanta `VoiceService`/`HomeGatewayService` si faltan, no solo el residente.
- **Luces:** el objetivo explícito manda sobre el plural genérico: «televisor/tv» -> MR Star, «mueble/lotus» -> Lotus, «las dos/ambas» -> ambas (0.13.77).

### Siguiente, por prioridad
1. **P0 - Voz y avisos conviviendo:** que `VoiceService` espere al aviso de agenda en vez de detenerse por el micrófono; el turno no se debe perder. Requiere APK + backend coordinados.
2. **P0 - Palabra de activación:** hoy `wake_enabled=false` y la voz continua acepta cualquier frase; además el modelo Vosk no contiene «andrómeda». Opciones: activar la compuerta con una palabra que el modelo reconozca («Doha»/«Doja») o entrenar «Andrómeda» como palabra clave; exigirla al iniciar/retomar, no en cada turno de una charla activa.
3. **P1 - Luces naturales:** aceptar «mueble en rojo» o «luz del mueble en rojo» con un «pon» implícito y que «sí, por favor + la orden» cuente como confirmación. Actualizar la descripción de `control_lights` para que el modelo sepa que «televisor» y «mueble» son objetivos de luz, no electrodomésticos.
4. **P1 - Alarmas de solo lectura:** añadir `list_alarms`/lectura de `agenda.db` para responder «tengo alarmas pendientes»; hoy solo existe `list_reminders`.
5. **P2 - Repetición de voz:** mantener la memoria del turno ya corregida y, si se quiere certificar, enviar un recibo `voice_message_played` desde la app al backend.
6. **P2 - Memoria compacta de largo plazo (nueva):** guardar hechos/preferencias breves («la luz del televisor es MR Star», «café sin azúcar») que sobrevivan al recorte de turnos. No es resumen generado de la charla; sería una tabla explícita y acotada.
7. **Plan original pendiente:** 4) color por hexadecimal y guardado con nombre (revisar qué falta en la app/backend), 5) música local/YouTube Premium, 6) Xbox.

La regla sigue siendo la misma: cada bloque se prueba en real y se despliega app + backend juntos; si algo falla, se apaga la bandera y se conserva el comportamiento anterior.
