# Roadmap y estado de Andrómeda

Última actualización: **2026-10-24**

Base instalada: **APK v58** (`greetings-reactions`, versionCode 89) + backend de voz v3
+ monitor de batería y guard root 80/70 + modelo local sin cambios.
Andrómeda es el launcher; Play Services, Chrome y varios servicios están deshabilitados; modo avión
con Wi-Fi/Bluetooth activos. Luces, voz, horario, modo descanso, brillo/pantalla y
saludos/reacciones están cerrados en código; falta la prueba humana de saludos/reacciones.
El horario de luces actual es el que el usuario ajustó por UI: **18:00 encender / 22:30 apagar**.
El guard de batería está activo en 80/70; el ciclo 80→70→80 y el hook de reinicio están
validados, el microciclo 79↔80 está corregido y falta observar la reanudación a 70 con la lógica nueva.

Leyenda:

- ✅ Hecho y confirmado
- 🟡 En curso o instalado sin validación final
- 📝 Diseñado/aprobado, pendiente de implementación
- ⬜ Pendiente
- ⛔ Bloqueado / pausado
- ❓ Decisión pendiente

## Reglas del proyecto

1. **No cambiar el modelo.** El LFM actual es el que mejor calidad ha dado.
2. **Local primero.** Modelo, Voz/Vosk, TTS, BLE y luces permanecen locales. La nube solo para contenido opcional.
3. **No Redis en el teléfono.** Redis también vive en RAM; alarmas/recordatorios ya usan SQLite y timers usan SharedPreferences.
4. **No guardar audio, transcripciones ni conversaciones humanas.** Contenido público (chistes/cuentos) sí puede ir a SQLite.
5. **No reintentar después de un frame BLE aceptado.** Si el resultado es incierto, se informa y no se repite.
6. **No romper** voz, TTS, Bluetooth, audio, luces ni la agenda ya confirmados.
7. **Seguridad de batería primero:** el teléfono vivirá 24/7 conectado. Ningún cambio de carga sin prueba reversible y monitoreo.

## Estado actual

| Área | Estado | Nota |
|---|---|---|
| Luces por voz (color, guardados, on/off, brillo) | ✅ | v42 confirma color isa y ambas luces; reintento pre-escritura añadido. Negro/gris/presets de MR Star siguen fuera del protocolo capturado |
| Cancelación global de voz | ⛔ | **Bloqueada por acuerdo del usuario (2026-10-24)**: v57 no quedó fiable; no seguir probando hasta retomarla con más tiempo/diagnóstico |
| TTS largo (errores/instrucciones) | 🟡 | v23 instalado; falta confirmar el texto largo completo |
| Andrómeda como launcher | ✅ | Home/Back se quedan en la app; Motorola launcher deshabilitado |
| Memoria | 🟡 | GMS/Chrome/etc. deshabilitados; ~1.44 GB libres con motor; vigilar mínimo 1.2 GB |
| Luces programadas | ✅ | v40: UI editable, voz, escaneo con respaldo de dirección BLE local; ciclo encender/apagar aceptado para 2 destinos. Horario actual del usuario: **18:00/22:30** |
| Patrones/efectos de luces | ⬜ | Protocolo no capturado; no implementar a ciegas |
| Modo descanso 23:00/06:00 | ✅ | v41b/v42: UI, brillo/volumen y ejecución real verificados. Horario corregido a 23:00/06:00; voz opcional |
| Reloj/pantalla siempre visible | ✅ | v49 cerrado y confirmado por el usuario: voz sube a 70%, baja a 10/255 20 s después de hablar o 10 s tras toque; mínimo nocturno 23:00–06:00 |
| Seguro de batería 24/7 | 🟡 | Guard 80/70; ciclo 80→70→80 confirmado y hook de reinicio validado. Se corrigió un microciclo 79↔80; falta observar la reanudación a 70 con la nueva lógica |
| Chistes/adivinanzas | ⬜ | Esperando JSON; propuesta: SQLite local |
| Saludos/reacciones cálidas | 🟡 | v58 instalado; saludos una vez por periodo y caras locales; falta prueba humana |
| Cuentos con sonidos | ⬜ | TTS local + SFX; audio nube opcional después |
| YouTube Premium / música | ⬜ | Decidir API, R2 y/o app YouTube; RAM/red |
| Barra Samsung por Bluetooth | ⬜ | Probar pairing A2DP y conmutación óptico/BT |
| Correo de recordatorios | ⬜ | Nube/SMTP; privacidad y credenciales; al final |
| TV LG | ⛔ | Pausado; sin LG Connect Apps/IP Control; WOL solo enciende |

## Registro de bloques ya trabajados

| Bloque | Estado | Versión / evidencia |
|---|---|---|
| Luces por voz (color, guardados, on/off, brillo) | ✅ | v22 confirmado; v42 reintento pre-escritura y color isa/ambas luces |
| Luces programadas | ✅ | v40; UI y voz; horario actual del usuario **18:00/22:30** |
| Modo descanso | ✅ | v41b/v42; 23:00/06:00, brillo, volumen y ejecución real |
| Brillo/pantalla/reloj | ✅ | v49; 70% activo, 10/255 en reposo, voz sube y baja a 20 s; mínimo nocturno |
| Andrómeda como launcher | ✅ | v24; Home/Back en la app, Motorola launcher deshabilitado |
| Batería: auditoría y guard 80/70 | 🟡 | Ciclo 80→70→80 y reinicio validados; microciclo 79↔80 corregido. Falta ver reanudación a 70 con la lógica nueva |
| Cancelación global de Andrómeda | ⛔ | Bloqueada temporalmente por acuerdo; v57 no fiable |
| TTS largo | 🟡 | v23 instalado; falta confirmar el texto largo completo |
| Patrones/efectos de luces | ⬜ | Protocolo no capturado; no implementar a ciegas |

## Prioridades

| Prioridad | Bloque | Por qué |
|---|---|---|
| **P0** | Arquitectura híbrida (reestructuración) | Separar lo determinista del modelo; "si dudas, pregunta"; evita mal enrutado y latencia |
| **P0** | Cancelación global de voz | ⛔ Bloqueada temporalmente por acuerdo; no seguir ahora |
| **P0** | Seguro de batería 24/7 | Seguridad física; el teléfono estará siempre conectado |
| **P1** | Modo descanso 23:00/06:00 | Cerrado en v41b/v42; ejecución real verificada |
| **P1** | Luces programadas 17:30–22:00 | Cerrado en v40; ciclo programado probado y horario restaurado |
| **P2** | Saludos, reacciones y chistes/adivinanzas | Mejora percibida inmediata, sin nube |
| **P3** | Cuentos con sonidos | TTS/modelo local; SFX locales |
| **P4** | YouTube/R2 + barra Samsung | Más dependencias, red, RAM y prueba física |
| **P5** | Correo de recordatorios | Nube, credenciales y contenido privado saliendo del teléfono |
| **—** | TV LG | Pausado por decisión del usuario |

---

## P0 — Arquitectura híbrida (reestructuración)

**Estado:** 📝 plan priorizado, sin implementar (2026-10-26). Documento:
`ARQUITECTURA-HIBRIDA-ANDROMEDA.md`.

El usuario probó la idea de "100 % modelo con herramientas" y la descartó: pedirle al
modelo cosas deterministas tarda más y hace que las cosas choquen entre sí. Se adopta
la arquitectura híbrida en capas, **manteniendo todo lo que ya funciona**:

1. Capa local determinista (hora/fecha/clima/aritmética/cumplidos/sinónimos).
2. Capa del modelo (solo lo complejo, con "si dudas, pregunta").
3. Capa de aclaración (pregunta concreta antes de actuar).
4. Fallback amable ("no te entendí, ¿puedes decirlo de otra forma?").

Orden propuesto: (1) "si dudas, pregunta" + fallback; (2) capa local de
cumplidos/saludos/agradecimientos; (3) sinónimos en gramáticas de luces/volumen.

---

## P0 — Cancelación global de Andrómeda

**Estado:** ⛔ **bloqueada temporalmente por acuerdo del usuario el 2026-10-24**. La v57 no quedó
fiable; no se harán más cambios ni pruebas hasta retomarla con más tiempo. Documento:
`CANCELACION-ANDROMEDA.md`.

Objetivo:

- Poder cancelar **en cualquier fase**: mientras escucha, piensa, habla, pregunta una confirmación
  o tiene una petición pendiente.
- Al decir **“cancélalo”**, Andrómeda debe responder exactamente:
  **“Está bien, aquí estaré cuando lo necesites.”**
- Cancelar descarta la petición pendiente; **no deshace una acción que ya se ejecutó** (por ejemplo,
  una luz que ya cambió).
- No cambiar el modelo ni usar nube para esta decisión.

Diseño recomendado:

- **Decidido:** “Andrómeda” sola **solo interrumpe** la voz/procesamiento y abre escucha; no cancela.
- **Decidido:** “cancélalo” cancela la petición, dice “Está bien, aquí estaré cuando lo necesites.”
  y termina la conversación, dejando el wake de “Andrómeda” activo.
- Detección local por fases:
  - Escuchando: comprobar la transcripción antes de ejecutar.
  - Pensando: detector local de cancelación con el micrófono libre.
  - Hablando/preguntando: extender el detector de interrupción actual.
  - Ejecutando: cancelar lo pendiente, sin prometer deshacer lo ya enviado.
- La frase fija se sintetiza localmente; no pasa por el modelo.
- La palabra clave se enruta por estado y hay un bloqueo corto (~1,5 s) para evitar que la cola
  acústica de “Andrómeda” inicie una solicitud nueva tras una interrupción.

Tareas:

- [x] Decidir que “Andrómeda” sola solo interrumpe y “cancélalo” cancela/cierra.
- [x] Documento de estados y criterios en `CANCELACION-ANDROMEDA.md`.
- [x] Vocabulario local, frase fija y `CancelSpeakerService` instalados.
- [x] Detección en escucha, pensamiento, habla y confirmaciones (código).
- [x] Reinicio del wake tras la frase y cooldown de 1,8 s.
- [x] v52: espera del decodificador Vosk y ventana de 5 s; fallback si el detector prearmado no arrancó.
- [x] v53: gramática cerrada de Vosk; el diagnóstico mostraba 6 endpoints y 6 rechazos.
- [x] v55: gramática validada en el dispositivo con `CancelGrammarInstrumentation` (`grammar_ok`).
- [x] v56: umbral 0.55 para comandos exactos y diagnóstico numérico de confianza.
- [x] v57: cancela con `audio_yield`/`audio_resume` y mantiene el servicio/modelo residente; no reinicia.
- [ ] ⛔ Pausado: repetir la prueba real y hacer dos cancelaciones seguidas sin reiniciar el motor.
- [ ] ⛔ Pausado: verificar que el motor sigue en 11435/11436 después de cancelar.
- [ ] Al retomar: usar los diagnósticos numéricos de confianza/endpoints antes de otro cambio.
- [ ] Confirmar que la acción pendiente no se ejecuta después de cancelar.
- [ ] Confirmar que la acción pendiente no se ejecuta después de cancelar.

---

## P0 — Seguro de batería para uso 24/7

**Estado:** 🟡 guard root **instalado y activo** el 2026-10-24 (80/70, pausa térmica, watchdog y rollback). `input_suspend=1`; el primer ciclo debe bajar a 70%, cargar a 80% y volver a pausar.

Datos actuales:

- Batería **84%**, **26.2 °C**, salud `Good`, cargando.
- **Capacidad real ~80.0%**: `charge_full=3.274.000 µAh` frente a diseño `4.093.000 µAh`.
- **1.552 ciclos**; ya existe desgaste acumulado.
- `input_suspend=1` (guard activo), `charge_control_limit=0/8`, `force_demo_mode=0`; no se tocó
  `force_demo_mode`.
- Monitor existente: `/data/adb/doha-battery/`; sigue siendo observación, no corte de seguridad.
- El teléfono está conectado permanentemente; el desgaste y el calor son el riesgo principal.

Tareas:

- [x] Auditoría de solo lectura de `force_demo_mode`, `charge_control_limit`, `charge_enabled`,
      `battery_charging_enabled`, `input_current_limit`, `constant_charge_current`, `step_charging`;
      hallazgos en `BATERIA.md`.
- [x] Probar `input_suspend` durante 10 s con restauración inmediata: pausa y reanudación verificadas;
      quedó en `0`.
- [x] Guard 80/70 implementado: pausa, reanudación, pausa térmica, hook de arranque y watchdog.
- [x] Rollback con `CHARGE_GUARD_DISABLED` y recuperación tras matar el guard verificados.
- [x] Primer ciclo completo observado por el usuario: 80→70→80; el guard funciona.
- [x] Hook de arranque validado con un reinicio real: guard y watchdog arrancaron solos.
- [x] Corregido el microciclo 79↔80: `charge-guard.reason` separa pausa normal de térmica.
- [ ] Observar la reanudación a 70 y nueva pausa a 80 con la lógica corregida.
- [ ] No escribir `force_demo_mode`; `BATERIA.md` advierte que escribir 0 no restaura el estado inicial.
- [ ] Evaluar alternativa externa: smart plug/temporizador de carga; ventilación; evitar 100% permanente.
- [ ] Definir política conservadora: guard 80/70 con pausa térmica; nunca dejar sin ventilación.
- [ ] Añadir aviso local si temperatura ≥ 40 °C o salud distinta de `Good`.
- [ ] No dejar el teléfono desatendido durante una prueba de carga nueva.
- [x] Pantalla siempre visible: **70% activo, 10/255 en reposo**; 10 s tras toque y 20 s tras
      terminar de hablar; mínimo nocturno 23:00–06:00. Voz verificada con estado simulado.
- [x] Llamada real “Andrómeda” y brillo confirmados por el usuario.
- [ ] Confirmar el brillo nocturno real a las 23:00 y el retorno a 10/255 tras las 06:00.

Decisión: guard root **80/70 instalado** (sin comprar hardware), con watchdog, pausa térmica y
rollback. La regleta inteligente queda opcional; solo monitoreo no previene. No usar
`force_demo_mode`. No es una protección certificada.

---

## P1 — Modo descanso 23:00 / 06:00

**Estado:** ✅ **cerrado en v41b/v42**; 23:00/06:00, UI, brillo, volumen, alarma y ejecución real verificados. La edición por voz es una mejora opcional.

Objetivo:

- **23:00:** brillo de pantalla al mínimo; volumen multimedia del teléfono al **50%**.
- **06:00:** brillo de pantalla al **70%**; volumen multimedia al **100%**.
- Andrómeda sigue funcionando: no se detiene el modelo, la voz, el micrófono ni las luces.
- Todos los días; editable por UI y voz; idempotente.

Notas técnicas:

- Brillo del sistema requiere `WRITE_SETTINGS` (permiso especial) o una pantalla/overlay propio.
  No confundir con el “Color del brillo” del loader de la app.
- Volumen multimedia usa `AudioManager.STREAM_MUSIC`; `MODIFY_AUDIO_SETTINGS` ya está en la app.
- Programar con `AlarmManager` exacto y restaurar tras reinicio.
- **Decidido:** el TTS de Andrómeda usa `STREAM_MUSIC`; se acepta que hable al 50% de noche.
  Si el usuario sube manualmente al 100% durante una fiesta, la tarea de las 23:00 del día siguiente
  vuelve a bajar a 50%. No es un bloqueo permanente ni requiere otro stream.
- La tarea programada debe ser idempotente y reafirmar el valor aunque el usuario lo haya cambiado
  manualmente; el cambio manual solo dura hasta la siguiente ejecución programada.

Tareas:

- [x] Permiso de brillo y prueba de cambio de brillo real (`WRITE_SETTINGS` concedido y verificado).
- [x] Cambio de volumen multimedia y restauración (50% solicitado; el paso más cercano fue 53%; 100% exacto).
- [x] Scheduler 23:00/06:00 + boot + UI; falta voz.
- [x] Decidir la voz de Andrómeda de noche: se acepta 50%.
- [x] Verificar que no afecta alarma, timers, TTS ni Bluetooth (prueba de aplicación sin audio/micrófono/BLE).
- [ ] Añadir edición por voz del modo descanso (nueva capacidad o ruta local).
- [ ] Observar la ejecución real de las 23:00 y 06:00.

---

## P1 — Luces programadas 17:30–22:00

**Estado:** ✅ **cerrado en v40**; horario diario 17:30/22:00, alarma exacta, UI verificada, ciclo programado aceptado y respaldo de dirección BLE local
armada para hoy 17:30. Falta observar la primera ejecución real y probar la edición por voz humana.

Diseño implementado:

- **17:30:** encender ambas luces (Lotus + MR Star); **22:00:** apagarlas; todos los días.
- `LightScheduleStore` local en `SharedPreferences` JSON; target, horas, días y enabled.
- `AlarmManager.setExactAndAllowWhileIdle` + `LightScheduleReceiver` + `LightScheduleService`
  en primer plano, wake lock acotado y timeout de 90 s. El siguiente evento se agenda antes de tocar
  BLE para no perderlo si el proceso muere.
- Ventana de tolerancia de 15 min para alarmas exactas retrasadas por Doze; una alarma vieja no
  ejecuta si ya no coincide con el horario guardado.
- Autorización interna de un solo uso, separada de la confirmación humana; no hay reintento tras un
  frame aceptado. El dispatcher BLE existente no cambió.
- No se salta una ejecución por el último estado guardado: un interruptor físico o un reinicio puede
  dejar la lámpara distinta. Repetir on/off es seguro; cada evento se envía una vez. El recibo sigue
  sin afirmar confirmación visual.
- Las direcciones BLE de las dos lámparas se guardan **solo localmente** (`light_addresses_v1`) y el
  horario las usa primero, sin depender de que el nombre aparezca en un escaneo. No salen del teléfono
  ni se escriben en logs/recibos. Se actualizan al resolverlas por escaneo en UI/voz.
- UI en “Luces → objetivo → Horario”: crear 17:30–22:00, cambiar horas, días, activar/desactivar,
  borrar. El horario se ejecuta sin abrir micrófono.
- Voz: el modelo sigue seleccionando `control_lights` (sin una herramienta nueva); Android parsea
  horas, “encender/apagar”, objetivo y días, pide la confirmación existente y guarda el horario.
  Nuevo estado de recibo `SCHEDULE_SAVED`, sin afirmar que se envió Bluetooth.
- Reinicio/actualización: `BootReceiver` reprograma (`BOOT_COMPLETED` y `MY_PACKAGE_REPLACED`).
  El APK v42 y el backend v3 están desplegados; el motor de voz quedó apagado, como antes.

Tareas:

- [x] `LightScheduleStore` local con target, hora, días y enabled.
- [x] `AlarmManager` + receptor + servicio acotado.
- [x] Autorización interna separada de la orden de voz.
- [x] UI: crear/editar/borrar/activar; por defecto 17:30/22:00 diario.
- [x] Voz: parser de horas/días + confirmación existente.
- [x] Direcciones BLE locales en caché; el horario no depende del escaneo y no se salta por estado guardado.
- [x] Recibo de ejecución programada; sin reintento tras write.
- [x] Ciclo programado probado con horario temporal: encendido 14:35 y apagado 14:50 aceptados para
      ambos destinos. Después el usuario ajustó el horario por UI a **18:00/22:30**; se dejó intacto.
- [ ] Confirmar si la MR Star conserva BLE con la luz apagada; si no, el horario solo podrá apagarla,
      no encenderla desde ese estado.
- [ ] Probar por voz: “programa las luces a las 5:30 y a las 10 de la noche”, “desactiva el horario…”,
      “activa el horario…”, “borra el horario…”.
- [ ] Probar edición de un solo extremo (“cambia el encendido a las 6”) y días.

Patrones/efectos:

- [ ] Capturar frames reales de patrones; no inventarlos.
- [ ] Solo después de estabilizar on/off/color/brillo.
- [ ] MR Star: brillo solo 25/50/75/100 validados; negro/grises rechazados.

---

## P2 — Saludos, reacciones, chistes y adivinanzas

**Estado:** 🟡 saludos/reacciones instalados en v58; falta prueba humana. Chistes/adivinanzas
esperan el JSON del usuario.

Arquitectura recomendada:

- **SQLite local `fun.db`**, separado de `agenda.db`.
- Tablas `jokes` y `riddles`: id, texto, categoría, respuesta, pistas, idioma.
- Importar el JSON una vez; actualizaciones manuales; sin servicio random en nube.
- Aleatorio con `ORDER BY RANDOM()` y bolsa de no-repetición.
- El modelo solo selecciona la herramienta; el contenido sale de SQLite.
- Sin confirmación para leer un chiste/adivinanza; no es una acción de dispositivo.
- Voz: “cuéntame un chiste”, “ponme una adivinanza”, “dame una pista”, “me rindo”.

Saludos/personalidad:

- [x] Política local de saludo por hora: “buenos días/tardes/noches”, una vez por periodo.
- [x] Saludo nocturno que cruza medianoche sin repetirse; marcador persistente.
- [x] Reacciones con la misma cara: tristeza, llanto, frío, calor, niebla, logro,
  cariño, chiste, asco y agradecimiento.
- [x] Sin emojis de texto; sin audio/transcripciones guardadas; sin cambiar el modelo.
- [ ] Prueba humana audible/visual de saludos, reacciones y clima.

Tareas:

- [ ] Recibir JSON de chistes/adivinanzas y definir esquema.
- [ ] Crear `fun.db` e importador.
- [ ] Herramientas de voz y UI “Juegos”.
- [ ] Juego de adivinanza con pistas/rendición.
- [ ] Pruebas de no-repetición y contenido vacío.

---

## P3 — Cuentos con sonidos

**Estado:** ⬜ pendiente.

- [ ] Cuentos generados por el modelo local o biblioteca pública local.
- [ ] TTS local; efectos de sonido desde assets locales.
- [ ] Sin audio ni transcripciones humanas guardadas.
- [ ] Audio de nube (TTS/estudio) solo como opción posterior, con consentimiento y coste.
- [ ] No bloquear voz/micrófono mientras suena el cuento.

---

## P4 — YouTube Premium, R2 y música

**Estado:** ⬜ decisión pendiente. YouTube está deshabilitado para ahorrar RAM.

Opciones:

1. **Abrir búsqueda de YouTube** por intent; sin API, pero el usuario elige.
2. **YouTube Data API** (clave/cuota) para buscar y lanzar un video; requiere cuenta/API y app YouTube.
3. **R2 con música propia/licenciada** para reproducción local/streaming; después fallback a YouTube.
4. Música local del teléfono (MediaStore), sin red.

Restricciones:

- No descargar/extraer audio de YouTube; usar R2 solo con música propia o licenciada.
- YouTube Premium requiere la app y cuenta; Andrómeda no reproduce audio de YouTube fuera de la app.
- Rehabilitar YouTube añade RAM; valorar.
- Claves/tokens fuera de Git y en almacenamiento privado.

Tareas:

- [ ] Elegir A, B, C o combinación.
- [ ] Decidir si se rehabilita YouTube y qué coste de RAM se acepta.
- [ ] Diseñar búsqueda local primero, YouTube como fallback.
- [ ] R2: bucket, credenciales privadas, caché y borrado.
- [ ] Prueba de reproducción y audio focus.

---

## P4 — Barra Samsung por Bluetooth

**Estado:** ⬜ pendiente de prueba física.

- La barra está conectada al TV por **óptico**; el Bluetooth puede estar activo.
- Objetivo: con el TV apagado, Andrómeda se conecta por **A2DP** y reproduce música.
- [ ] Confirmar nombre/modelo de la barra y que acepte pairing.
- [ ] Probar conexión A2DP y reproducción.
- [ ] Ver si la barra cambia sola a Bluetooth o hay que cambiar entrada.
- [ ] Al encender el TV, desconectar/volver a óptico para no dejar audio cruzado.
- [ ] No usar la barra para TTS si compite con la voz de Andrómeda.

---

## P5 — Recordatorios por correo

**Estado:** ⬜ al final. Requiere nube/credenciales; el contenido del recordatorio sale del teléfono.

Opciones:

- Relay SMTP local en Debian (sigue saliendo a Internet).
- Worker/API de correo (Resend/MailChannels/etc.) con clave y cuota.
- Abrir la app de correo para envío manual (no automático; Gmail está deshabilitado).

Tareas:

- [ ] Definir destinatario/proveedor y consentimiento.
- [ ] Evaluar privacidad: texto, fecha, hora.
- [ ] Implementar reintentos solo antes de aceptar el envío; recibo honesto.
- [ ] No bloquear recordatorios locales actuales.

---

## TV LG — Pausado

- El TV es webOS 4.1.0; responde UPnP, pero no expone LG Connect Apps/IP Control.
- 3000/3001 no completan SSAP; 3001 devuelve “Hello world”.
- “Encender con dispositivo móvil/Wi-Fi” es WOL: solo enciende.
- El usuario decidió manejarlo de otra forma.
- No se envió ningún comando ni se guardó clave.
- No continuar sin una decisión explícita (IR, smart plug, ThinQ, otro).

---

## Decisiones abiertas

- [ ] Brillo de descanso: permiso `WRITE_SETTINGS` y prueba real.
- [x] Voz de Andrómeda de noche: se acepta 50%; el cambio manual se reafirma en la siguiente tarea.
- [ ] Batería: el usuario no ha planeado nada todavía; límite por kernel reversible, smart plug o
      solo monitoreo. No implementar sin decisión.
- [ ] Chistes/adivinanzas: SQLite local (recomendado) vs nube; falta JSON.
- [ ] YouTube: API/clave, app, R2, música local; RAM/red.
- [ ] Barra Samsung: pairing A2DP y conmutación óptico/BT.
- [ ] Correo: proveedor/destinatario/privacidad.
- [x] Lista final de reacciones recibida e implementada localmente.
- [ ] Confirmar visualmente las reacciones y el saludo único en el teléfono.

## Próximo paso recomendado

1. **Arquitectura híbrida** (P0): "si dudas, pregunta" + fallback amable; luego capa
   local de cumplidos/saludos y sinónimos de luces/volumen. Mantener todo lo que ya funciona.
2. **Verificar el arreglo del apagado del motor** (causa: `voice_cache_clear_failed` por la
   purga de caché en carrera con el modelo aún ocupado; arreglado cancelando y esperando la
   generación antes de purgar, en `resident_runtime.py` y `inference_privacy.py`).
3. **Cerrar el guard de batería** observando el ciclo 76→70→80 con la lógica corregida; no requiere
   cambios de código.
4. **Chistes/adivinanzas** cuando llegue el JSON del usuario.
5. **Confirmar visualmente** el brillo nocturno a las 23:00 y el retorno a 10/255 tras las 06:00.
6. **Cuentos con sonidos**.
7. **YouTube/R2 + barra Samsung**.
8. **Correo**.
9. **TV** solo si se decide una alternativa.

## Referencias

- `ARQUITECTURA-HIBRIDA-ANDROMEDA.md` — plan de reestructuración a capas (determinista + modelo + aclaración).
- `PLAN-INTEGRACION-LUCES-ANDROMEDA.md` — estado detallado de luces y TV.
- `BATERIA.md` — monitor y advertencias de carga.
- `artifacts/android-voice/lights-voice-confirmed-v22/receipt.json` — voz/luces confirmadas.
- `artifacts/android-voice/lights-cancel-tolerance-v22/receipt.json` — cierre de luces.
- `artifacts/android-voice/lights-long-speech-v23/receipt.json` — ventana de TTS largo.
- `artifacts/android-voice/launcher-home-v24/receipt.json` — Andrómeda launcher.
- `artifacts/android-voice/rest-mode-v42/rest-mode-v42.apk` — APK instalado con modo descanso,
  scheduler, UI, pruebas de brillo/volumen y reintento pre-escritura de luces (versionCode 73).
- `artifacts/android-voice/battery-audit-20261024/receipt.json` — auditoría de batería de solo lectura.
- `artifacts/android-voice/battery-guard-v1/receipt.json` — guard 80/70, watchdog, rollback y estado.
- `artifacts/android-voice/voice-brightness-v49/receipt.json` — voz sube a 179 y baja a 10 tras 20 s.
- `CANCELACION-ANDROMEDA.md` — implementación, estados, frases y criterios de cancelación global.
- `artifacts/android-voice/cancel-global-v57/receipt.json` — cancelación que conserva el modelo residente.
- `SALUDOS-REACCIONES-ANDROMEDA.md` — saludos por periodo y reacciones dibujadas en la cara.
- `artifacts/android-voice/greetings-reactions-v58/receipt.json` — APK v58, pruebas y estado
  pendiente de prueba humana.
- `scripts/charge-guard.sh`, `scripts/charge-guard-watchdog.sh`, `scripts/99-doha-charge-guard.sh`.
- `artifacts/android-voice/lights-voice-retry-v42/receipt.json` — diagnóstico del color “isa” y
  reintento `WRITE_REJECTED`; no se escribió BLE en la prueba.
- `artifacts/android-voice/rest-mode-v41b/receipt.json` — verificación de 23:00/06:00 y valores reales.
- `artifacts/android-voice/lights-schedule-voice-backend-v3/` — respaldo y hashes del backend previo;
  módulos desplegados `assistant_tools_model.py` y `light_trial.py`.
- `artifacts/android-voice/memory-cleanup-safe-v1/` — limpieza de RAM.
- `artifacts/android-voice/lg-pairing-v1/receipt.json` — TV pausado, sin pairing.
