# Plan de integración de luces con Andrómeda

## Objetivo

Permitir órdenes por voz y por interfaz para LotusLamp X (`MELK-OA10 17`) y
MR Star (`GATT--DEMO`), con colores RGB hexadecimales, nombres, escenas,
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
- apagar: `7E 07 04 00 00 02 01 EF`.

Ambos se observaron como Write Without Response en `FFF3`; aún no se han
reproducido desde Andrómeda.

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

La captura diferencial de brillo al 25% produjo `BC 05 06 01 0D 00 00 00 00 55`.
`0D` es el código observado para ese ajuste; se requieren más puntos antes de
mapear porcentajes arbitrarios.

La muestra aproximada de mitad, realizada con dos movimientos del control,
produjo `BC 05 06 01 E6 00 00 00 00 55` y `BC 05 06 02 4A 00 00 00 00 55`.
Se conserva como dos eventos, no como un 50% exacto.

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

### Fase 5 — voz y ambas luces

- Añadir intención tipada al canal local de herramientas.
- Ejecutar Lotus/MR Star mediante adaptadores independientes.
- Probar primero cada luz por separado y luego la orden `BOTH`.
- Mantener Andrómeda, agenda, alarmas, volumen y audio sin cambios no aprobados.

## Reglas de operación

- La app GATT-DEMO no puede conservar simultáneamente la conexión de una luz
  que Andrómeda necesite controlar; una de las dos puede desplazar a la otra.
- Andrómeda puede mantener dos conexiones si el firmware de cada periférico lo
  permite; si no, usará conexión breve y secuencial por luz.
- `gamer` solo será un efecto si el adaptador lo soporta; no se fingirá.
- No se instalará Home Assistant, nube, OAuth, credenciales ni Wi-Fi.
- No se reiniciará la voz automáticamente durante pruebas de instalación.
