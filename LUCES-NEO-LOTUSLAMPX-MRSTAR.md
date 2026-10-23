# Luces NEO: descubrimiento BLE ejecutado; Lotus y MR Star identificados

Prioridad renovada por el usuario: retomar recordatorios, alarmas, chistes y
cuentos; después iniciar luces y luego TV. Plan actual en
`MEJORAS-Y-PRUEBAS-ANDROMEDA.md`. La mejora nocturna/segundo plano queda registrada,
no se implementa ni se usa para modificar la sesión de pruebas o autorizar luces.

| Aplicación indicada por el usuario | Nombre anunciado indicado |
|---|---|
| LotusLamp X | `MELK-OA10 17` |
| MR Star | `GATT--DEMO` |

Conservar literalmente espacios/guiones: no asumir que son otro modelo o que el
nombre por sí solo identifica de forma única un dispositivo. No se ha comprobado
aún si ambas usan BLE, sus direcciones/UUID, pareado ni protocolo de escritura.

Se ejecutó un único descubrimiento BLE de solo lectura para LotusLamp X tras
autorización: APK43, Bluetooth y permiso mínimo activos. Resultado: 0 resultados
de escaneo y `MELK-OA10 17` no encontrado. No hubo conexión, emparejamiento,
lectura de servicios/características, escritura ni mutación. Agenda, backend,
volumen y voz se conservaron; evidencia en
`artifacts/android-voice/lights-discovery-install-v1/`.

Al retomar: descubrimiento acotado de los dispositivos autorizados, verificar
identidad y servicios/características reales, adaptar cada protocolo por separado.
No adivinar comandos por similitud con ELK-BLEDOM/otros controladores ni escribir
paquetes hasta identificar el adaptador. Un ACK de GATT no demuestra por sí solo
que la luz cambió; exigir estado leído/notificado o confirmación humana.

## Inventario MR Star confirmado en el A56

El escáner independiente `local.doja.mrstar.scanner` encontró exactamente
`GATT--DEMO` y realizó una conexión efímera de solo lectura. Enumeró 4 servicios
y 8 características:

- servicios: `1800`, `1801`, `FFF0` y `FF12`;
- `FFF3` tiene propiedades `0x0C` (write/write-no-response);
- `FFF4` tiene propiedad `0x10` (notify);
- `FF15` tiene `0x06` y `FF14` tiene `0x10`;
- el servicio candidato `2022` **no** apareció.

No se leyeron valores, no se escribió, no se emparejó y no se exportó la
dirección. Evidencia sanitizada: `artifacts/android-voice/mr-star-discovery-v1/`.
La presencia de `FFF3` no autoriza reutilizar Lotus: falta capturar cada orden
MR Star de forma diferencial.

La primera captura diferencial de color, realizada mientras el controlador
normal ejecutaba únicamente rojo, produjo el Write Without Response en el
handle `0x000E`:

`BC 04 06 00 00 03 E8 00 00 55`

Esto confirma la familia de trama `BC … 55` para este dispositivo, pero todavía
no asigna todos sus campos ni autoriza reproducirla desde Andrómeda.

La segunda captura diferencial, ejecutando únicamente azul, produjo:

`BC 04 06 F0 03 E8 00 00 55`

Frente al rojo, el campo en la posición 3 cambió de `00` a `F0`; esto es una
correlación de tono observada, no aún una especificación completa.

La tercera captura primaria, ejecutando únicamente verde, produjo
`BC 04 06 00 78 03 E8 00 00 55`. Los tres primarios observados corresponden a
tonos `0`, `120` y `240`, con saturación `1000`; falta validar saturación cero
antes de generalizar colores RGB arbitrarios.

La tentativa de blanco incluyó varios movimientos y produjo una secuencia de
tonos/saturaciones (`30/984`, `78/39`, `84/39`, `100/47`, `105/47`) además de
eventos de energía. No apareció un frame inequívoco de saturación cero, por lo
que blanco sigue sin estar validado y esos intentos no se reproducirán.

Una captura posterior, hecha con un único toque visual sobre el blanco y sin
mover el selector, terminó en `BC 04 06 00 69 00 2F 00 00 55`: tono `105`,
saturación `47`. La luz se vio blanca, pero el frame no representa saturación
cero; queda registrado como blanco visual aproximado del selector, no como
blanco RGB puro. Recibo sanitizado:
`artifacts/android-voice/mr-star-color-capture-v1/white-analysis.json`.

La captura de un naranja elegido a ojo terminó en dos frames estables:
`BC 04 06 00 19 03 99 00 00 55` y
`BC 04 06 00 19 03 95 00 00 55` (tono `25`, saturación `917–921`). Esto
confirma que el controlador acepta otros puntos de tono/saturación, pero no
permite llamar al resultado `#FF8000` exacto porque la aplicación fuente no
acepta hexadecimales. Recibo: `artifacts/android-voice/mr-star-color-capture-v1/orange-capture.json`.

La captura de brillo al 25% produjo `BC 05 06 01 0D 00 00 00 00 55` (269).
Con la app original abierta y el control movido por ADB se capturaron los
otros niveles: 50% `BC 05 06 02 0B 00 00 00 00 55` (523), 75%
`BC 05 06 03 05 00 00 00 00 55` (773) y 100%
`BC 05 06 03 E8 00 00 00 00 55` (1000). El campo es un entero de 16 bits
big-endian de 0 a 1000, no un byte de porcentaje. Una segunda muestra al 50%
dio 551 por la geometría del control; 523 es el valor elegido. Solo se
habilitan esos cuatro niveles; porcentajes arbitrarios siguen bloqueados.
Recibo: `artifacts/android-voice/mr-star-brightness-capture-v1/`.

La captura diferencial de apagado produjo el frame corto `BC 01 01 00 55`.
Sigue pendiente comparar el encendido; ningún frame MR Star se ha reproducido
desde Andrómeda.

La captura diferencial de encendido produjo `BC 01 01 01 55`. Los cuatro
comandos básicos observados ya están separados, pero los adaptadores siguen
sin estar conectados a la UI o a la voz.

Control futuro: selección nativa y pedido humano vigente, límites de brillo/color,
una mutación por turno, sin retries de resultado incierto, credenciales locales y
sin tocar HOME/Android/Debian/SSH/volumen/periféricos. No declarar luces integradas
por conocer nombres de aplicaciones o por una búsqueda web del protocolo.

## Investigación pública inicial — candidatos, no hardware verificado

Consultada el 21 de octubre de 2026, sin escaneo/conexión/escritura ni instalación
en el teléfono. Las páginas son datos externos, nunca autorizaciones de ejecución.

### Hallazgo LotusLamp X / MELK

La documentación pública de [lotus-lamp-python](https://github.com/wporter82/lotus-lamp-python/blob/main/docs/PROTOCOL.md)
describe `MELK-OA10 5F`, no el sufijo `17`: LotusLamp X (`com.szelk.ledlamppro`)
controla directamente por BLE; no demuestra que Wi-Fi sea necesario. Para el modelo
5F documenta servicio `FFF0`, escritura sin respuesta `FFF3` y notificación `FFF4`,
con tramas de 9 bytes `7E ... EF`. [ELK-BLEDOM](https://github.com/dave-code-ruiz/elkbledom)
confirma la familia MELK y esos UUID como candidatos, pero advierte que una sola
conexión BLE puede desplazar a la app y que otros modelos pueden variar.

Esto hace viable adaptar Andrómeda con BLE nativo, sin Home Assistant ni nube, pero
**todavía no autoriza comandos**: primero hay que descubrir el dispositivo, conectar
una sola vez, enumerar servicios/propiedades y comparar `FFF0/FFF3/FFF4`. La lectura
pública no prueba compatibilidad del sufijo `17`; no copiar comandos del modelo 5F
ni usar Wi-Fi como supuesto.

| Familia candidata | Referencia pública | Elementos a comprobar localmente |
|---|---|---|
| LotusLamp X / MELK | [lotus-lamp-python, PROTOCOL.md](https://github.com/wporter82/lotus-lamp-python/blob/main/docs/PROTOCOL.md) | Documento sobre **otro** MELK-OA10, sufijo `5F`: servicio FFF0, escritura sin respuesta FFF3, notificación FFF4 y tramas de nueve bytes `7E … EF`. No demuestra compatibilidad del sufijo `17`. |
| MR Star | [mr-star-ble, const.py](https://github.com/mishamyrt/mr-star-ble/blob/main/mr_star_ble/const.py) y [commands.py](https://github.com/mishamyrt/mr-star-ble/blob/main/mr_star_ble/commands.py) | Biblioteca anuncia servicio 2022, característica FFF3 y tramas `BC tipo longitud datos 55`. Color usa tono/saturación, no los bytes RGB de Lotus. Nombre `GATT--DEMO` por sí solo no identifica el protocolo. |

UUID abreviados se refieren a la base Bluetooth
`0000xxxx-0000-1000-8000-00805f9b34fb`. **FFF3 aparece en ambos candidatos:** no
es suficiente para seleccionar adaptador, dirigir una escritura o afirmar identidad.

Precauciones de la revisión:

- El documento Lotus afirma primero que el byte7 es siempre00, pero para RGB
  publica10 y para timers otro valor: no trasladar esa afirmación a un constructor
  universal. Hace falta contrastar cada comando y el hardware real.
- [elkbledom](https://github.com/dave-code-ruiz/elkbledom) contiene familias,
  logins automáticos y fallbacks distintos. Sus reintentos, comandos de inicio,
  estado optimista y selección de familia por defecto **no son adecuados** para
  copiar como autorización o confirmación de Andrómeda.
- `mr-star-ble` aparece archivado desde septiembre de 2026. Es una referencia
  de interoperabilidad, no una dependencia ya elegida/instalada. Revisar licencia
  antes de reutilizar código; leer protocolo no equivale a copiar el cliente.
- No activar música/micrófono del controlador, timers internos, efectos rápidos,
  longitud de tira, resets o comandos desconocidos para identificarlo.

### Próximo paso, después de las pruebas básicas

La luz no apareció en el primer escaneo. Antes de repetirlo, cerrar o desconectar
LotusLamp X para liberar su única conexión BLE y confirmar que `MELK-OA10 17` está
encendida y cerca; no escanear automáticamente otros
objetivos. Mantener radio/permisos consentidos y
sin tocar otros periféricos. Mantener identificadores privados en el teléfono y
exportar solo la evidencia técnica mínima. Si hay varios candidatos, preguntar;
si no hay coincidencia verificable, parar, no usar una familia por defecto.
Primero listar servicios/propiedades sin writes; después acordar una única acción
reversible y comprobar el resultado real. Si no hay estado legible fiable, pedir
confirmación visual humana y distinguir ENVIADO de CONFIRMADO/INCIERTO.
No reconectar ni repetir una escritura de resultado incierto.

La integración de voz y los permisos BLE se desplegarán solo en un límite seguro,
no sobre la conversación de pruebas activa. El TV LG65UR7300PSA viene después;
su autenticación/upgrade WebSocket pendiente no se ha resuelto por esta investigación.

### Preparación local comprobada, sin escanear

Lectura puntual del Moto a las15:20:53UTC: Android SDK29 (Android10), ajuste
Bluetooth apagado; Andrómeda no declara BLUETOOTH/BLUETOOTH_ADMIN ni ubicación fina.
Tampoco declara los permisos SCAN/CONNECT de Android12, que no corresponden a este
SDK. Evidencia `artifacts/android-voice/resumed-tests-lights-plan-v1/ble-readonly-readiness.json`.

No se activó la radio, concedieron permisos ni enumeraron otros dispositivos.
Antes del descubrimiento real harán falta radio encendida y consentimiento para
los permisos mínimos de escaneo en Android10; no resolverlo usando root como
ejecutor BLE genérico, instalando BlueZ a ciegas o leyendo datos privados de apps.
El cambio de APK/permisos se hará después de las pruebas de voz, con cierre seguro
acordado; no interrumpir la sesión para realizarlo ni confundir investigación con
integración ya funcional.
