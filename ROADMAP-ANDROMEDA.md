# Roadmap y estado de Andrómeda

Última actualización: **2026-10-23**

Base instalada: **APK v40** (`lights-schedule-voice-v40`, versionCode 71) + backend de voz v3
(`assistant_tools_model.py`, `light_trial.py` actualizados) + modelo local sin cambios.
Andrómeda es el launcher; Play Services, Chrome y varios servicios están deshabilitados; modo avión con
Wi-Fi/Bluetooth activos. La voz y las luces por voz están confirmadas. El horario 17:30/22:00 está
creado y armado; falta observar la primera ejecución real a las 17:30 y la voz humana de programación.

Leyenda:

- ✅ Hecho y confirmado
- 🟡 En curso o instalado sin validación final
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
| Luces por voz (color, guardados, on/off, brillo) | ✅ | Confirmado en v22; MR Star y Lotus |
| TTS largo (errores/instrucciones) | 🟡 | v23 instalado; falta confirmar el texto largo completo |
| Andrómeda como launcher | ✅ | Home/Back se quedan en la app; Motorola launcher deshabilitado |
| Memoria | 🟡 | GMS/Chrome/etc. deshabilitados; ~1.44 GB libres con motor; vigilar mínimo 1.2 GB |
| Luces programadas 17:30–22:00 | ✅ | v40: UI editable, voz, escaneo con respaldo de dirección BLE local; ciclo temporal encender/apagar aceptado para 2 destinos. Horario 17:30/22:00 restaurado. Salvedad: MR Star si su BLE se apaga con la luz |
| Patrones/efectos de luces | ⬜ | Protocolo no capturado; no implementar a ciegas |
| Modo descanso 23:00/06:00 | ⬜ | Diseño en este documento |
| Seguro de batería 24/7 | ⬜ | Auditoría de carga pendiente; no escribir nodos sin validar |
| Chistes/adivinanzas | ⬜ | Esperando JSON; propuesta: SQLite local |
| Saludos/reacciones cálidas | ⬜ | Política local; sin cambiar modelo |
| Cuentos con sonidos | ⬜ | TTS local + SFX; audio nube opcional después |
| YouTube Premium / música | ⬜ | Decidir API, R2 y/o app YouTube; RAM/red |
| Barra Samsung por Bluetooth | ⬜ | Probar pairing A2DP y conmutación óptico/BT |
| Correo de recordatorios | ⬜ | Nube/SMTP; privacidad y credenciales; al final |
| TV LG | ⛔ | Pausado; sin LG Connect Apps/IP Control; WOL solo enciende |

## Prioridades

| Prioridad | Bloque | Por qué |
|---|---|---|
| **P0** | Seguro de batería 24/7 | Seguridad física; el teléfono estará siempre conectado |
| **P1** | Modo descanso 23:00/06:00 | Rutina diaria sencilla y local |
| **P1** | Luces programadas 17:30–22:00 | Cerrado en v40; ciclo programado probado y horario restaurado |
| **P2** | Saludos, reacciones y chistes/adivinanzas | Mejora percibida inmediata, sin nube |
| **P3** | Cuentos con sonidos | TTS/modelo local; SFX locales |
| **P4** | YouTube/R2 + barra Samsung | Más dependencias, red, RAM y prueba física |
| **P5** | Correo de recordatorios | Nube, credenciales y contenido privado saliendo del teléfono |
| **—** | TV LG | Pausado por decisión del usuario |

---

## P0 — Seguro de batería para uso 24/7

**Estado:** ⬜ auditoría pendiente. No se ha cambiado ningún parámetro de carga.

Datos actuales:

- Batería 100%, salud `Good`, ~22.9–24.4 °C en el monitor.
- Monitor existente: `/data/adb/doha-battery/` (temperatura, capacidad, corriente, USB).
- El teléfono está conectado permanentemente; el desgaste y el calor son el riesgo principal.

Tareas:

- [ ] Auditoría **solo lectura** de nodos: `force_demo_mode`, `charge_control_limit`, `charge_enabled`,
      `battery_charging_enabled`, `input_current_limit`, `constant_charge_current`, `step_charging`.
- [ ] Confirmar si el hardware admite límite de carga reversible (por ejemplo 80%).
- [ ] No escribir `force_demo_mode` sin probar recuperación; el documento `BATERIA.md` advierte que
      escribir 0 no restaura el estado inicial.
- [ ] Evaluar alternativa externa: smart plug/temporizador de carga; ventilación; evitar 100% permanente.
- [ ] Definir política conservadora (p. ej. mantener 60–80%, nunca dejar sin ventilación).
- [ ] Añadir aviso local si temperatura ≥ 40 °C o salud distinta de `Good`.
- [ ] No dejar el teléfono desatendido durante una prueba de carga nueva.

Decisión pendiente: límite por software del kernel, smart plug, o solo monitoreo. **No implementar hasta
tener una forma reversible y medida.**

---

## P1 — Modo descanso 23:00 / 06:00

**Estado:** ⬜ pendiente.

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

- [ ] Permiso de brillo y prueba de cambio de brillo real.
- [ ] Cambio de volumen multimedia y restauración.
- [ ] Scheduler 23:00/06:00 + boot + UI/voz.
- [x] Decidir la voz de Andrómeda de noche: se acepta 50%.
- [ ] Verificar que no afecta alarma, timers, TTS ni Bluetooth.

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
  El APK v40 y el backend v3 están desplegados; el motor de voz quedó apagado, como antes.

Tareas:

- [x] `LightScheduleStore` local con target, hora, días y enabled.
- [x] `AlarmManager` + receptor + servicio acotado.
- [x] Autorización interna separada de la orden de voz.
- [x] UI: crear/editar/borrar/activar; por defecto 17:30/22:00 diario.
- [x] Voz: parser de horas/días + confirmación existente.
- [x] Direcciones BLE locales en caché; el horario no depende del escaneo y no se salta por estado guardado.
- [x] Recibo de ejecución programada; sin reintento tras write.
- [x] Ciclo programado probado con horario temporal: encendido 14:35 y apagado 14:50 aceptados para
      ambos destinos; horario 17:30/22:00 restaurado.
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

**Estado:** ⬜ pendiente. Chistes/adivinanzas esperan el JSON del usuario.

Arquitectura recomendada:

- **SQLite local `fun.db`**, separado de `agenda.db`.
- Tablas `jokes` y `riddles`: id, texto, categoría, respuesta, pistas, idioma.
- Importar el JSON una vez; actualizaciones manuales; sin servicio random en nube.
- Aleatorio con `ORDER BY RANDOM()` y bolsa de no-repetición.
- El modelo solo selecciona la herramienta; el contenido sale de SQLite.
- Sin confirmación para leer un chiste/adivinanza; no es una acción de dispositivo.
- Voz: “cuéntame un chiste”, “ponme una adivinanza”, “dame una pista”, “me rindo”.

Saludos/personalidad:

- [ ] Política local de saludo por hora: “buenos días/tardes/noches”, cálida y breve.
- [ ] Evitar el cierre seco “¿necesitas algo?” en saludos; ofrecer conversación o un chiste corto.
- [ ] No cambiar el modelo; solo prompt/política local.
- [ ] Reacciones: esperar la lista del usuario; implementar como plantillas locales por intención/tono.

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
- [ ] Lista final de reacciones.

## Próximo paso recomendado

1. **Modo descanso 23:00/06:00**: brillo mínimo y volumen 50% por la noche; 70% y 100% a las 06:00.
   Reutiliza el patrón del scheduler y la decisión ya tomada de aceptar la voz al 50% de noche.
2. **Batería**: definir primero el enfoque (auditoría solo lectura, smart plug o límite reversible).
3. **Chistes/adivinanzas + saludos** cuando llegue el JSON.
4. **Cuentos/reacciones**.
5. **YouTube/R2 + barra Samsung**.
6. **Correo**.
7. **TV** solo si se decide una alternativa.

## Referencias

- `PLAN-INTEGRACION-LUCES-ANDROMEDA.md` — estado detallado de luces y TV.
- `BATERIA.md` — monitor y advertencias de carga.
- `artifacts/android-voice/lights-voice-confirmed-v22/receipt.json` — voz/luces confirmadas.
- `artifacts/android-voice/lights-cancel-tolerance-v22/receipt.json` — cierre de luces.
- `artifacts/android-voice/lights-long-speech-v23/receipt.json` — ventana de TTS largo.
- `artifacts/android-voice/launcher-home-v24/receipt.json` — Andrómeda launcher.
- `artifacts/android-voice/lights-schedule-voice-v40/lights-schedule-voice-v40.apk` — APK instalado
  con scheduler/UI editable verificada/voz/recibo numérico, acceso directo “Horario de luces” y
  direcciones BLE locales como respaldo (versionCode 71).
- `artifacts/android-voice/lights-schedule-voice-backend-v3/` — respaldo y hashes del backend previo;
  módulos desplegados `assistant_tools_model.py` y `light_trial.py`.
- `artifacts/android-voice/memory-cleanup-safe-v1/` — limpieza de RAM.
- `artifacts/android-voice/lg-pairing-v1/receipt.json` — TV pausado, sin pairing.
