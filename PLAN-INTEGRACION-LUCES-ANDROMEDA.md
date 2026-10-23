# Plan de integración de luces con Andrómeda

## Objetivo

Permitir órdenes por voz y por interfaz para **Luz mueble** (LotusLamp X,
`MELK-OA10 17`) y **Luz del televisor** (MR Star, `GATT--DEMO`), con colores RGB hexadecimales, nombres, escenas,
patrones y colores personalizados guardados localmente.

Ejemplos previstos:

- `Pon las dos luces en rosa.`
- `Pon Lotus en #F2A3F6.`
- `Usa FFFFFF en MR Star.`
- `Activa cálido tranquilo.`
- `Guarda este color como atardecer.`
- `Pon patrón gamer.`

## Estado comprobado

- Lotus se identificó localmente por dirección sin exportar la dirección.
- GATT Lotus confirmado: servicio `FFF0`, escritura sin respuesta `FFF3`,
  notificación `FFF4`; una enumeración encontró un servicio y dos características.
- La comprobación no leyó valores, no escribió, no emparejó y cerró la conexión.
- Agenda, backend y volumen quedaron preservados.
- MR Star todavía requiere identificación GATT propia; las referencias públicas
  (`2022`, `FFF3`, tramas `BC...55`) son candidatas, no autorización.

## Arquitectura aprobada

1. **Contrato común:** destino (`LOTUS`, `MR_STAR`, `BOTH`), color RGB,
   preset/patrón, brillo y transición opcionales.
2. **Parser seguro:** aceptar `#RRGGBB` o `RRGGBB` de seis dígitos; nombres en
   español; rechazar valores ambiguos o hexadecimales inválidos.
3. **Adaptadores separados:** un protocolo para Lotus y otro para MR Star;
   nunca seleccionar adaptador solo por `FFF3` o por el nombre anunciado.
4. **Gestor BLE:** una conexión independiente por luz, operaciones serializadas
   por dispositivo, sin escaneo de periféricos no autorizados, sin reintentar
   escrituras de resultado incierto.
5. **Canal común de voz/interfaz:** ambos producen el mismo comando validado;
   el GATT no se ejecuta desde la UI ni desde el hilo de audio.
6. **Recibos honestos:** `ENVIADO`, `CONFIRMADO`, `INCIERTO` o `ERROR`; nunca
   declarar cambio visual sin confirmación GATT fiable o confirmación humana.

## Fases y puertas de seguridad

### Fase 1 — contrato sin radio (iniciada)

- Modelo de órdenes, destinos y colores.
- Parser de nombres, hex y presets.
- Tests locales, sin Android, micrófono, red, GATT ni mutaciones.

Resultado inicial: `LightColor`, `LightTarget`, `LightCommand` y
`LightCommandParser` añadidos; la suite local pasó 9 comprobaciones nuevas y
la suite de políticas existente también pasó. El APK47 candidato aún no se
instala en el teléfono. Compilación verificada en
`artifacts/android-voice/lights-command-contract-candidate-v1/build-v1/`.

### Fase 2 — interfaz simulada

- Selector Lotus/MR Star/ambas.
- Rueda/campo hexadecimal, brillo, presets y colores guardados en almacenamiento
  local no conversacional.
- Estado simulado y botón Detener/Cancelar.

Inicio implementado en APK48 candidato: opciones nativas con selector de
objetivo, colores, hexadecimal, presets/patrón y guardado local de colores.
La vista muestra explícitamente `SIMULACIÓN` y no abre Bluetooth ni escribe.

APK48 fue instalado con autorización explícita, conservando datos. Recibo:
`artifacts/android-voice/lights-ui-install-v1/installed.json`. Agenda, backend,
volumen y voz detenida quedaron preservados; no hubo conexiones BLE ni writes.

Se detectó en el teléfono que la lista de objetivos no era visible al combinar
mensaje y lista en el diálogo Android. APK49 corrige la ventana usando título y
lista directa; además, la voz continúa deliberadamente fuera de esta fase.

APK49 quedó instalado con autorización y la lista de objetivos ya puede
probarse. Recibo: `artifacts/android-voice/lights-ui-target-fix-install-v1/installed.json`.

La interfaz ahora también modela brillo de `0%` a `100%`, incluido valor
personalizado. APK50 candidato conserva el modo simulación; no conecta ni
escribe. La misma orden tipada será la entrada futura de voz y BLE.

APK50 quedó instalado con autorización. Recibo:
`artifacts/android-voice/lights-ui-brightness-install-v1/installed.json`.

La interfaz incorpora ahora `Encender` y `Apagar` para cada objetivo. `BOTH`
representa dos órdenes independientes, no un frame BLE combinado. La captura
de Lotus también debe incluir encendido y apagado antes de habilitar writes.

APK51 quedó instalado con autorización. Recibo:
`artifacts/android-voice/lights-ui-power-install-v1/installed.json`.

Incidente nocturno identificado: `MainActivity.onStop()` detenía siempre
`VoiceService`, por lo que bloquear la pantalla lo apagaba. La corrección APK52
conserva el servicio foreground en modo continuo, mantiene un `PARTIAL_WAKE_LOCK`
solo durante esa autorización y sigue sin reinicio automático. El modo limitado
conserva su apagado al salir.

APK52 quedó instalado con autorización. Recibo:
`artifacts/android-voice/continuous-lock-persistence-install-v1/installed.json`.

Nueva corrección solicitada: el botón **Activar Andrómeda** ya no conecta la voz
contra un motor que debía estar preparado manualmente. APK53 candidato usa un
puente raíz fijo para iniciar el residente completo (`continuous-voice guided
alarms`), espera el estado `running` y solo después arranca `VoiceService`.
No acepta comandos shell arbitrarios ni inicia automáticamente al arrancar.

APK53 quedó instalado con autorización y se dejó detenido a propósito. Recibo:
`artifacts/android-voice/button-starts-engine-install-v1/installed.json`.

Durante la primera prueba del botón, el permiso Android podía dejar la
activación pendiente porque faltaba `onRequestPermissionsResult`. APK54
candidato reanuda automáticamente la secuencia tras **Conceder**; no se
instalará mientras una sesión continua esté activa.

En la prueba, el residente sí pasó a `running`, pero APK54 no reconoció el
JSON con espacios (`"state": "running"`) y mantuvo la interfaz en preparación.
APK55 candidato parsea el campo JSON real en vez de comparar texto literal;
queda pendiente de instalarse tras detener la sesión activa.

### Fase 3 — adaptador Lotus

- Construir el primer frame solo después de verificar el formato del modelo `17`.
- Una única acción reversible, con autorización humana actual.
- Validar resultado y conservar una instantánea de agenda/volumen/backend.

Captura local del A56, sin exportar el log bruto, confirmó para `MELK-OA10 17`
el servicio `FFF0`, `FFF3` con escritura/ escritura sin respuesta y `FFF4` con
notificación. El color solicitado produjo exactamente el Write Command GATT
`7E 07 05 03 F2 A3 F6 10 EF` en el handle de valor observado. Esto valida solo
el frame de color; todavía no autoriza escrituras desde Andrómeda. Brillo y
encendido/apagado requieren capturas separadas.

La captura aislada de brillo al 25 % produjo repetidamente el frame real
`7E 04 01 19 01 FF 02 01 EF` (`0x19` = 25). El formato difiere del candidato
público del modelo `5F`; queda validado solo para el modelo `17` observado y
todavía no se ha escrito desde Andrómeda.

Las capturas diferenciales de energía completaron el contrato Lotus `17`:

- encender: `7E 07 04 FF 00 01 02 01 EF`;
- apagar: `7E 07 04 00 00 00 02 01 EF`.

Ambos se observaron como Write Without Response en `FFF3`; aún no se han
reproducido correctamente desde Andrómeda. La primera prueba de apagado mostró
que el constructor omitía un `00`; la captura real y el test ahora exigen los
nueve bytes completos.

El contrato local quedó implementado en `LotusLampProtocol` y probado contra
los cuatro payloads capturados. `LotusLampBleAdapter` exige la dirección local,
`FFF0`, `FFF3` con Write Without Response y `FFF4` con Notify; usa una sola
conexión, no reintenta y devuelve `COMPLETE_UNCONFIRMED` porque el transporte
no ofrece confirmación visual ni respuesta ATT. La interfaz continúa en
`SIMULACIÓN`; el adaptador no se invoca desde UI ni desde voz. El APK de
compilación aislada está en
`artifacts/android-voice/lotus-adapter-contract-v2/` y aún no se instaló ni
escribió en Lotus.

Con autorización explícita posterior se instaló el APK aislado v3 sobre el
Moto sin borrar datos y se ejecutó una sola orden Lotus más oscura:
`#D81B60` al `25%`. El adaptador aceptó los dos writes (color y brillo), no
reintentó y no recibió confirmación del dispositivo; por tanto el recibo es
`LOTUS_DARKER_PINK_WRITE_ACCEPTED_UNCONFIRMED`, no una afirmación de cambio
visual. El recibo local está en
`artifacts/android-voice/lotus-darker-pink-v3/receipt.json`. La UI y la voz
siguen sin ejecutar GATT automáticamente. Después, la persona confirmó
visualmente que la lámpara quedó rosada; el recibo guarda solo ese booleano,
no el contenido humano.

### Fase 4 — MR Star

- Enumeración GATT de solo lectura por su dirección autorizada.
- Verificar servicio, característica y propiedades reales.
- No copiar tramas públicas hasta comprobar el hardware.

El inventario independiente del A56 encontró `GATT--DEMO`: 4 servicios y 8
características, con `FFF0`, `FFF3` (`0x0C`), `FFF4` (`0x10`), `FF12`, `FF15`
(`0x06`) y `FF14` (`0x10`); el candidato público `2022` no apareció. No se
leyeron valores ni se escribieron comandos. Siguiente puerta: capturar por
separado color, brillo y energía en MR Star, sin reutilizar frames Lotus solo
porque comparte `FFF3`.

La captura diferencial del único color rojo ejecutado en el controlador MR Star
produjo `BC 04 06 00 00 03 E8 00 00 55` mediante Write Without Response en el
handle `0x000E`. Es evidencia del frame real, no todavía un constructor
autorizado: falta comparar otro color y separar brillo/energía.

La comparación diferencial con azul produjo `BC 04 06 F0 03 E8 00 00 55`;
solo cambió el campo `00` a `F0` en la posición de tono observada. Aún falta
capturar brillo y energía antes de construir el adaptador MR Star.

La captura diferencial de verde produjo `BC 04 06 00 78 03 E8 00 00 55`.
Los tres primarios observados corresponden a tonos `0`, `120` y `240`, con
saturación `1000`; todavía falta validar blanco/saturación cero.

La tentativa de blanco generó varios eventos de tono/saturación y energía,
pero ningún frame inequívoco de saturación cero. Blanco queda fuera del
adaptador hasta una captura limpia; no se reutilizarán esos intentos.

La captura de brillo al 25% produjo `BC 05 06 01 0D 00 00 00 00 55` (269).
Con la app original en el A56 y el control movido por ADB se capturaron los
otros niveles: 50% `BC 05 06 02 0B 00 00 00 00 55` (523), 75%
`BC 05 06 03 05 00 00 00 00 55` (773) y 100%
`BC 05 06 03 E8 00 00 00 00 55` (1000). El campo es un entero de 16 bits
big-endian de 0 a 1000. Una segunda muestra al 50% dio 551 por la geometría
del control; se eligió 523. Recibo sanitizado:
`artifacts/android-voice/mr-star-brightness-capture-v1/sanitized-brightness-capture.json`.

El apagado MR Star produjo `BC 01 01 00 55`, un frame corto de 5 bytes. Falta
capturar el encendido diferencial antes de implementar energía.

El encendido diferencial produjo `BC 01 01 01 55`. Con ello quedaron
observados color rojo, verde y azul, brillo 25% y energía. `MrStarProtocol` y
`MrStarBleAdapter` quedaron implementados de forma conservadora: aceptan solo
rojo/verde/azul, brillo 25% y energía; rechazan colores, porcentajes, presets y
patrones no capturados. El APK de contrato se compiló en
`artifacts/android-voice/mr-star-adapter-contract-v2/`, no se instaló ni
escribió en MR Star.

`LightCommandRouting` ya expande `BOTH` en dos órdenes tipadas independientes
y fue probado sin I/O. El APK de contrato de routing se compiló en
`artifacts/android-voice/lights-routing-contract-v1/`; la UI conserva la
simulación por defecto y solo ofrece envío real mediante una puerta explícita.

Esa puerta ya quedó implementada en `lights-ui-confirmed-dispatch-v1`: el
resolver busca solo los nombres autorizados, no persiste direcciones, exige una
confirmación de un solo uso con caducidad de 60 segundos, serializa los destinos
de `BOTH` fuera del hilo UI/audio y no reintenta. El APK se compiló pero no se
instaló; voz todavía no invoca el dispatcher. Patrones y colores no capturados
se rechazan honestamente.

La variante `lights-color-ui-v2` se instaló sobre el Moto con actualización
conservadora (`-r`), preservando datos. La instalación no abrió BLE ni escribió;
la interfaz ahora prioriza color/energía, oculta presets/patrones y deja el
envío real detrás de `Enviar una vez`.

La corrección v3 resolvió el fallo de conexión GATT `133` al separar el fin del
escaneo del inicio de `connectGatt`. La interfaz logró controlar Lotus y el
azul al 25% quedó confirmado visualmente; recibo local:
`artifacts/android-voice/lights-color-ui-v3/receipt.json`. No hubo retries
automáticos ni se guardó contenido humano.

MR Star también quedó confirmado desde la interfaz: después de reiniciar el
Bluetooth del Moto y ampliar la guarda escaneo→GATT a 1,2 s en v4, verde al 25%
funcionó visualmente. La conexión y el descubrimiento de servicios terminaron
con estado 0; recibo: `artifacts/android-voice/lights-color-ui-v4/mr-star-green-receipt.json`.
El color queda confirmado solo para rojo, verde y azul; `#FF8000`, blanco y
otros colores siguen rechazados honestamente.

La captura diferencial de colores adicionales confirmó el modelo tono/saturación:
naranja elegido a ojo produjo tono `25` y saturación `917–921`; el blanco visual
elegido sin arrastre produjo tono `105` y saturación `47` con brillo de la app al
100%. El segundo es visualmente blanco, pero no saturación cero, así que queda
registrado como aproximación del selector y no como blanco RGB puro. Recibos
sanitizados en `artifacts/android-voice/mr-star-color-capture-v1/`.

Con esa evidencia, `MrStarProtocol` ya convierte colores cromáticos hexadecimales
RGB a los campos de tono/saturación observados; `#FFFFFF` usa el frame visual
capturado y negro/grises siguen rechazados. La UI MR Star incluye blanco visual y
hexadecimal dinámico. `lights-color-ui-v5` pasó la suite y se instaló sobre el
Moto sin abrir BLE ni escribir; voz sigue sin invocar el dispatcher. Recibo:
`artifacts/android-voice/lights-color-ui-v5/receipt.json`.

La inestabilidad `GATT 133/0x3E` de Android 10 se aisló como fallo de enlace
BLE previo a cualquier write, no como error de color. v7 inicia `connectGatt`
en el handler principal, espera 1,4 s entre destinos de `BOTH` y permite hasta
tres intentos solo para conexión/descubrimiento/timeout sin frames aceptados,
con backoff de 0,9/1,8 s. Nunca repite después de un frame aceptado o incierto.
La suite pasó y `lights-stable-ble-v7` quedó instalada sin write automático;
recibo: `artifacts/android-voice/lights-stable-ble-v7/receipt.json`.

La primera prueba real de `BOTH` con v7 encendió visualmente las dos luces:
Lotus completó primero; MR Star necesitó tres conexiones (dos fallos `133`
previos al write y una conexión final con estado 0). Recibo:
`artifacts/android-voice/lights-stable-ble-v7/both-power-on-receipt.json`.

La prueba de colores del usuario reveló un error en MR Star: el tono es un
campo de 16 bits big-endian (`00 78` verde, `00 F0` azul), no un byte. Los
tonos mayores de 255 se truncaban y producían colores incorrectos. v9 corrige
el constructor; por ejemplo `#FF00E6` ahora genera
`BC 04 06 01 32 03 E8 00 00 55` y `#F35CE3` genera
`BC 04 06 01 32 02 6D 00 00 55`. Pruebas y recibo:
`artifacts/android-voice/lights-color-hue16-v9/receipt.json`.

También se añadió `BRIGHTNESS` al contrato común y al parser de voz. Lotus
acepta el patrón local de 0–100%; MR Star tiene capturados y habilitados los
niveles 25, 50, 75 y 100. Otros porcentajes siguen bloqueados: no se ha
validado un mapeo arbitrario del control de la app original.

La prueba del usuario detectó una regresión: el brillo era una variable global
y se añadía a todos los colores. Tras subir Luz mueble a 50%, los colores de
Luz del televisor y de `BOTH` enviaban también brillo 50%, que MR Star rechaza.
v10 separa el brillo por objetivo y los colores ya no incluyen brillo; elegir
“Brillo” envía una orden `BRIGHTNESS` independiente. Recibo:
`artifacts/android-voice/lights-brightness-split-v10/receipt.json`.

v13 habilita en la UI y en la voz los cuatro niveles capturados de MR Star
(25/50/75/100), también para `BOTH`, y mantiene sincronizadas las etiquetas de
brillo cuando se elige `BOTH`. El parser acepta dígitos y números hablados en
español («cincuenta», «setenta y cinco», «cien»). Los colores siguen sin
arrastrar brillo. Recibo:
`artifacts/android-voice/lights-brightness-levels-v13/receipt.json`.

La prueba de brillo de MR Star mostró una conexión que descubrió servicios y
se cerró antes de cualquier `onCharacteristicWrite`; además el A56 seguía con
Bluetooth activo. v14b acepta escritura con o sin respuesta, espera 250 ms tras
el descubrimiento y acepta notificación o indicación. Se apagó el Bluetooth del
A56. Recibo: `artifacts/android-voice/lights-ble-robust-v14b/receipt.json`.

El backend del Moto no tenía `control_lights`. Con autorización del usuario se
respaldaron los tres módulos anteriores y se desplegaron
`assistant_tools_model.py`, `assistant_tools_trial.py`,
`conversation_voice.py` y el nuevo `light_trial.py`, con hashes verificados e
importación correcta dentro del chroot. No había motor de voz activo, no se
inició, no se abrió micrófono ni se cargó el modelo. Recibo:
`artifacts/android-voice/lights-voice-backend-v1/receipt.json`.

La primera prueba de voz de luces cerró el micrófono. El recibo técnico fue
`phase=RESPONSES`, `error=IO`, `VoiceService.java:665`, con micrófono iniciado.
La causa era un cableado faltante: `VoiceService.finishSpeech` llamaba a los
canales de agenda y volumen, pero no a `lightChannel.spoken`. El backend sí
armaba la confirmación; Android la borraba, y al llegar `light_execute` el
permiso se rechazaba y se detenía todo el servicio. v15 añade esa llamada.
También se cambió el diálogo de brillo de MR Star/`BOTH` para mostrar
directamente 25/50/75/100, sin el texto que parecía un error. Recibo:
`artifacts/android-voice/lights-voice-fix-v15/receipt.json`.

La segunda prueba mostró otro cableado faltante: Android aceptaba resultados
de timer, agenda y volumen, pero no `light_result`. El backend sí seleccionó
`control_lights` y devolvió `light_proposal` con validación `accepted`; al
llegar el resultado de luces con `action_result`, Android descartaba el `say`,
dejaba el turno abierto y a los 60 s detenía el servicio (recibo limpio
`phase=STOP`, `error=NONE`, no una excepción). v16 añade `light_result` a la
puerta de finalización de acciones. La suite incluye ahora una comprobación de
que existen los avisos `spoken` de agenda/volumen/luces y la aceptación de
`light_result`. Recibo:
`artifacts/android-voice/lights-voice-result-v16/receipt.json`.

La tercera prueba confirmó que el color sí se escribía, pero el backend
ignoraba el paquete `light_result`: `conversation_voice.py` no lo tenía en la
lista de acciones que procesa. La app quedaba “pensando” y a los 60 s se
detenía. Se añadió `light_result` a las acciones normales y al relevo de
contexto, se desplegó `conversation_voice.py` con hash
`8aaa062c…`, y se añadió una prueba que exige esa ruta. Recibo:
`artifacts/android-voice/lights-voice-backend-v2/receipt.json`. Falta repetir
la prueba humana de color y brillo por voz.

v17 añade los nombres de color guardados al parser de voz. “Las luces”, “las
dos” o “ambas” producen `BOTH`; se aceptan colores fijos, hexadecimal y nombres
locales de `LightPresetStore`, recargados en cada turno humano. Los nombres
comunes de acción/objetivo se filtran para no confundir “luz” o “televisor” con
un color. Negro/grises siguen rechazados en MR Star. Recibo:
`artifacts/android-voice/lights-saved-colors-v17/receipt.json`.

v18 añade energía por voz para ambos o para una luz nombrada. “Apaga las dos
luces”, “enciende las luces” y “apaga ambas” producen `BOTH`; “la del mueble”
produce Lotus y “la del televisor”, “la del tv” o “la tele” producen MR Star.
Sigue siendo una orden por turno: no se deben mezclar dos acciones distintas en
una sola frase. Recibo:
`artifacts/android-voice/lights-power-aliases-v18/receipt.json`.

La prueba con el color guardado mostró que se había guardado como “color de
isa” (`color_color_de_isa`, `#FF00E6`), no como “isa”. v19 acepta tanto el
nombre completo como alias sin prefijos (“color de ”, “color ”, “de …”), así
que “pon las luces de color isa” y “pon las luces de color de isa” ejecutan el
mismo color guardado. Recibo:
`artifacts/android-voice/lights-saved-alias-v19/receipt.json`.

La siguiente prueba volvió a detenerse sin conexión BLE y sin error de
protocolo. Para no seguir adivinando, v20 añade un recibo numérico
`voice_light_health` que guarda solo la secuencia de etapas (orden
interpretada, pregunta hablada, “sí” capturado, permiso, envío, resultado y
cierre), estado y contadores; nunca palabras, colores ni direcciones. También
muestra “Di sí para confirmar la orden de luces” mientras espera. Recibo:
`artifacts/android-voice/lights-confirm-diag-v20/receipt.json`.

La prueba con v20 cambió el color y registró `1,6,13,3,10,12,8`: orden
interpretada, pregunta hablada, “sí” capturado, permiso concedido, recibo
enviado y resultado aceptado. El servicio se detuvo justo después porque el
backend en modo guiado añade “¿Necesitas algo más?” al texto del recibo y
Android comparaba solo el texto crudo. v21 acepta el texto con o sin esa
coletilla. Recibo:
`artifacts/android-voice/lights-result-offer-v21/receipt.json`.

La siguiente prueba confirmó el flujo completo dos veces, pero al pedir “algo
más” se detuvo: `...8,13,2,7`. El backend dejaba activa la confirmación de
luces después de un resultado correcto; el siguiente turno no relacionado se
trataba como cancelación de luces. Android ya había cerrado su canal local y
rechazaba la cancelación, deteniendo el servicio. v22 hace que `LightTrial` se
limpie después del resultado y del timeout, y que Android acepte una
cancelación válida aunque su formulario local ya esté cerrado, sin escribir
nada. Recibo:
`artifacts/android-voice/lights-cancel-tolerance-v22/receipt.json`.

El usuario confirmó finalmente el flujo completo por voz: color guardado,
confirmación hablada, ejecución BLE, lectura del resultado y continuación de la
escucha. Recibo:
`artifacts/android-voice/lights-voice-confirmed-v22/receipt.json`.

Estabilidad nocturna: el usuario dejó el teléfono encendido con el modelo
cargado y por la mañana “Andrómeda” respondió bien. Verificación de solo
lectura: servicio, modelo y backend llevaban ~10 h 50 min vivos; puertos
11435/11436 activos; ventana móvil de 120 muestras con RAM disponible
1.278–1.354 GB, modelo ~704 MB, backend ~164 MB; monitor de batería con 246
muestras, 22.9–24.4 °C, 100% y salud Good. Recibo:
`artifacts/android-voice/overnight-stability-v22/receipt.json`. La RAM sigue
cerca del mínimo de arranque de 1.2 GB.

Auditoría de memoria: 3.76 GB utilizables; con el motor cargado, MemAvailable
~1.26 GB, modelo ~714 MB, backend ~179 MB, app ~162 MB, TTS ~149 MB, GMS
~270 MB, servicios Motorola/Qualcomm ~400 MB. La cámara en reposo usa ~29 MB;
la reserva CMA de ~204 MB es de hardware y no se libera desactivando la app. Se
aplicó la limpieza segura: Mensajes, Motocare, Setup Wizard, Turbo, reporte
nativo, datos de vida útil y Chrome deshabilitados; se detuvo `loc_launcher`
(localización Qualcomm, que incluye `lowi-server` y `xtra-daemon`). El escaneo
BLE siguió funcionando (90 dispositivos, sin writes); TTS y Bluetooth siguen
vivos. La prueba de BLE reinició el proceso de la app y dejó el motor apagado,
así que la medición posterior (1.69 GB disponibles) no es comparable. Rollback:
`artifacts/android-voice/memory-cleanup-safe-v1/rollback.txt`. La limpieza
media (GMS/pila celular) queda pendiente de medir con el motor cargado y probar
voz/TTS/BLE. Recibo:
`artifacts/android-voice/memory-cleanup-safe-v1/receipt.json`.

Medición con el motor cargado después de la limpieza segura: MemAvailable
1.44 GB (antes 1.26 GB, +173 MB), RAM usada 3.13 GB (antes 3.39 GB, −250 MB),
swap en uso 639 MB (antes 880 MB, −241 MB), ZRAM físico 158 MB (antes 199 MB).
Modelo 736 MB, app 168 MB, backend 138 MB, GMS 165 MB, TTS 118 MB. El margen
sobre el mínimo de 1.2 GB subió a ~239 MB. `loc_launcher`/`lowi-server` siguen
detenidos; Chrome y los paquetes seguros siguen deshabilitados. Falta probar
voz/TTS/BLE después de la limpieza y decidir la limpieza media. Recibo:
`artifacts/android-voice/memory-cleanup-safe-v1/after-engine-on.json`.

El usuario reportó que la voz se cortaba al explicar un fallo largo (por
ejemplo, las formas válidas de pedir alarma/recordatorio). La ventana de TTS de
herramientas estaba fija en 20 s. v23 usa una ventana estimada por longitud:
20–45 s para mensajes normales y 60 s para listas/web, con prueba pura
`SpeechPlaybackPolicyTest`. No cambia acciones, confirmaciones ni reintentos.
Recibo: `artifacts/android-voice/lights-long-speech-v23/receipt.json`.

Limpieza media, primer paso: `com.google.android.gms` deshabilitado; app, TTS
y Bluetooth siguen vivos. Medición con el motor apagado: +30 MB disponibles
(1.741 → 1.772 GB), no comparable con el motor cargado. Queda probar TTS, BLE y
la explicación larga antes de continuar con la pila celular/Motorola/launcher.
Rollback: `pm enable --user 0 com.google.android.gms`. Recibo:
`artifacts/android-voice/memory-cleanup-medium-v1/receipt.json`.

El usuario activó modo avión, mantuvo Bluetooth y la voz/BLE funcionaron. El
aviso de “Servicios de Google Play” venía de `com.motorola.config.wifi`, no de
una reactivación: GMS sigue `disabled-user`. Google TTS registra
`SERVICE_DISABLED` pero usa la voz offline. Con el motor cargado y modo avión:
MemAvailable 1.49 GB, usados 2.98 GB, swap 549 MB. Android Auto y CCC/OTA se
deshabilitaron (+40 MB) manteniendo app/modelo/backend/TTS/BT.

v24 añade a Andrómeda como launcher: filtro HOME/DEFAULT, resolución por
defecto `local.doja.voice/.MainActivity`, y Home/Back se quedan en la app. El
launcher de Motorola fue deshabilitado por decisión explícita del usuario, sin
fallback. Home/Back verificados; el proceso del launcher ya no existe. No hay
cajón de apps hasta que Andrómeda añada accesos a apps/Ajustes; se puede
recuperar el launcher con `pm enable --user 0 com.motorola.launcher3`. Recibos:
`artifacts/android-voice/launcher-home-v24/receipt.json` y
`artifacts/android-voice/launcher-disabled-v1/receipt.json`.

Datos y RAM: las alarmas/recordatorios se guardan en SQLite `agenda.db` y se
programan con `AlarmManager.setExactAndAllowWhileIdle`; los timers en
`SharedPreferences` `timers.xml` + `AlarmManager`; las conversaciones y fuentes
web viven solo en RAM acotada (fuente web 1 h, sin transcripción). No se usa
Redis: en el mismo teléfono también consume RAM; en la nube añade red,
credenciales y persistencia de contenido humano. La cámara se mantiene: ~23 MB
de servicio en reposo y consumo temporal solo al abrirla; la reserva CMA de
~204 MB es de hardware. La limpieza de datos no es la palanca; modelo, backend,
TTS y sistema dominan el uso.

TV LG: el Moto está en Wi-Fi local; tras encender el TV, SSDP lo encontró
como webOS 4.1.0 (192.168.1.9) y el UPnP de 1176 confirmó el dispositivo. Los
puertos 3000/3001 están abiertos, pero el pairing no completó: 3000 reinicia en
claro y cierra TLS; 3001 responde HTTP 200 “Hello world”, no el WebSocket de
webOS. El menú del TV **no ofrece LG Connect Apps ni IP Control**; solo
“encender con dispositivo móvil/Wi-Fi”, que es Wake-on-LAN y solo enciende.
Los puertos 3000/3001 aceptan conexión pero no responden al handshake SSAP:
síntoma conocido de daemon de control webOS colgado o modo hotel. Un apagado
normal del mando no reinicia webOS; hay que desenchufar el TV de la corriente
unos 60 s. Antes de ThinQ/nube/IR/smart plug, probar ese arranque en frío y
reintentar el pairing local; si vuelve, apagado por SSAP + encendido por WOL
(con MAC y “TV On With Mobile”). No se envió ningún comando ni se guardó clave.
ThinQ cloud es posible en teoría pero usa API de TV no oficial, cuenta/token e
Internet, y el encendido en standby/ahorro sigue siendo incierto; no se adopta
todavía. Recibos: `artifacts/android-voice/lg-discovery-v1/receipt.json` y
`artifacts/android-voice/lg-pairing-v1/receipt.json`.

Requisitos confirmados por el usuario: luces encendidas 17:30 y apagadas 22:00
todos los días, editables por UI y voz; TV LG físico apagado una sola vez
dentro de 15 minutos; no cambiar los controles de voz existentes. Idempotencia
deseada: si el último estado ordenado ya coincide, no reenviar; sin lectura del
dispositivo, un cambio externo puede dejar el estado desconocido, pero on/off
es seguro de repetir. El TV necesita emparejamiento webOS local y, para
encenderlo apagado, “Mobile TV On”/WOL. Implementación propuesta: almacén local
de horarios + `AlarmManager` + servicio acotado para BLE; UI de lista/edición;
luego herramienta de voz `schedule_lights`; TV después del pairing. No Redis,
no nube para el modelo.

### Observación de RAM y temperatura

La ventana de muestreo recogió 13 muestras durante 84 s: RAM disponible entre
1.203 y 1.462 GB, app 173–232 MB, modelo hasta 737 MB, backend hasta 155 MB,
batería 25.4–25.5 °C y zona térmica máxima estable en 75 °C. El mínimo de RAM
quedó apenas por encima del guardia de arranque de 1.2 GB. El monitor temporal
terminó antes de los 15 min; no se modificó el monitor de batería existente.

### Fase 5 — voz y ambas luces

- `control_lights` ya está negociada como herramienta local sin argumentos.
- Android reconstruye la frase con `LightCommandParser`, conserva el contrato
  común y ejecuta el `LightCommandDispatcher` existente; el modelo no decide
  objetivos, colores, frames ni direcciones.
- Toda orden de voz pide confirmación hablada; MR Star no puede escribir sin esa
  confirmación. El resultado devuelve `COMPLETE_UNCONFIRMED`, parcial, error o
  no validado, sin afirmar cambio visual.
- Falta probar en el Moto: una luz por separado, después `BOTH`, y confirmar
  que la pregunta/confirmación hablada no interfiere con agenda, alarmas,
  volumen ni audio.
- Mantener Andrómeda, agenda, alarmas, volumen y audio sin cambios no aprobados.

### Fase 6 — horario diario y edición por voz (v25–v40)

- APK v40 (`lights-schedule-voice-v40`, versionCode 71) instalado; backend v3 desplegado con
  `assistant_tools_model.py` y `light_trial.py` actualizados.
- Horario real creado: `BOTH`, 17:30 encender, 22:00 apagar, todos los días, activo. La alarma
  `LIGHT_SCHEDULE` quedó armada para hoy a las 17:30; no se escribió BLE durante la instalación.
- `LightScheduleStore` usa `SharedPreferences` JSON local. `LightScheduleScheduler` agenda solo el
  siguiente evento con `AlarmManager.setExactAndAllowWhileIdle`; `LightScheduleReceiver` arranca
  `LightScheduleService` en primer plano, con wake lock y timeout de 90 s. El siguiente evento se
  agenda antes de tocar BLE para que un cierre del proceso no borre el horario de mañana.
- La alarma se acepta hasta 15 min tarde para tolerar Doze; una alarma que ya no coincide con el
  horario editado se descarta.
- El horario **no se salta** por el último estado guardado: un interruptor físico o un reinicio puede
  dejar la lámpara distinta. Repetir on/off es seguro y cada evento se envía una vez. El recibo no
  afirma confirmación visual.
- Direcciones BLE locales (`light_addresses_v1`) para Lotus y MR Star: primero se escanea (y se
  actualizan si aparecen); si el nombre no aparece, el resolver usa la dirección guardada como
  respaldo antes de tocar BLE. No salen del teléfono ni se registran en logs/recibos.
- La conexión directa por dirección apagó la Lotus; la MR Star aceptó la escritura cuando estaba
  encendida y falló al intentar conectarse después de apagada. Si su BLE se alimenta solo con la luz,
  el horario no podrá encenderla desde apagada; falta confirmarlo físicamente.
- Prueba programada del apagado a las 14:50 con el horario temporal: el servicio escaneó, usó el
  respaldo local y recibió `COMPLETE_UNCONFIRMED` con 2 destinos aceptados. Después se restauró
  17:30/22:00 diario; la alarma quedó armada para hoy 17:30.
- UI: el botón directo “Horario de luces” (y “Luces → objetivo → Cambiar horario”) permite crear
  17:30–22:00, cambiar encendido/apagado, elegir todos los días/lunes a viernes/fin de semana,
  activar/desactivar y borrar. Un fallo de Android hacía que `setMessage` ocultara la lista de
  acciones; v36 usa un título personalizado y la lista editable sí aparece. Verificado sin BLE ni
  micrófono: 5 acciones visibles y el selector de hora abre. La ejecución programada no abre
  micrófono ni pide una confirmación nueva.
- Voz: no se añadió otra herramienta; el modelo sigue seleccionando `control_lights`. El parser local
  `LightScheduleVoiceParser` reconoce dos horas, objetivo, días y acciones de activar/desactivar/borrar.
  Android pide la confirmación hablada existente y guarda el horario; el recibo es `SCHEDULE_SAVED`,
  que no afirma haber enviado Bluetooth. El backend traduce ese estado a “Horario de luces
  actualizado. No cambié ninguna luz.”
- Ejemplos probados en parser puro: “Programa las luces a las 5:30 y a las 10 de la noche” produce
  17:30/22:00; “Programa las luces de lunes a viernes a las 18:00 y 23:00” produce días de semana;
  “Desactiva/Borra el horario de la luz mueble” produce la acción y el objetivo correctos.
- Pendiente: observar la ejecución real de 17:30/22:00 y probar la edición por voz humana. El motor
  de voz está apagado; el scheduler no lo necesita. Patrones/efectos siguen bloqueados hasta capturar
  frames reales.

## Reglas de operación

- La app GATT-DEMO no puede conservar simultáneamente la conexión de una luz
  que Andrómeda necesite controlar; una de las dos puede desplazar a la otra.
- Andrómeda puede mantener dos conexiones si el firmware de cada periférico lo
  permite; si no, usará conexión breve y secuencial por luz.
- `gamer` solo será un efecto si el adaptador lo soporta; no se fingirá.
- No se instalará Home Assistant, nube, OAuth, credenciales ni Wi-Fi.
- No se reiniciará la voz automáticamente durante pruebas de instalación.
