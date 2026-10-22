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

### Fase 3 — adaptador Lotus

- Construir el primer frame solo después de verificar el formato del modelo `17`.
- Una única acción reversible, con autorización humana actual.
- Validar resultado y conservar una instantánea de agenda/volumen/backend.

### Fase 4 — MR Star

- Enumeración GATT de solo lectura por su dirección autorizada.
- Verificar servicio, característica y propiedades reales.
- No copiar tramas públicas hasta comprobar el hardware.

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
