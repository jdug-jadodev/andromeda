# Micrófono y carita: prueba aislada 0.3

**Actualización vigente:** instalada 0.4, que conserva esta prueba como «Solo activación y transcripción (0.3)» y añade **otra ruta** de conversación LFM sin acciones. Este documento conserva la evidencia de activación; preparación/guardas/ensayos de conversación en `CONVERSACION-ANDROMEDA.md`. No se habilitó IA dentro del protocolo wake-only.

## Alcance autorizado

El usuario pidió avanzar con el micrófono y comprobar «Andrómeda» → entrada de la cara, sin esperar a resolver toda la conversación del LLM. Se permite esta **prueba acotada de activación/ASR**, no escucha permanente ni control de dispositivos. No confundirla con la aprobación del núcleo conversacional.

## Implementado e instalado

- APK `0.3.0-wake-test` / código 3, paquete `local.doja.voice`, actualizado sin borrar datos; APK 0.2 respaldado en `artifacts/android-voice/pre-v03-*.apk`. HOME y configuración de arranque intactos.
- Doble toque → **Micrófono** → explicación de la prueba → elegir **90 s o 5 min**. Abrir la app no inicia captura. Con permiso pendiente hay que concederlo y volver a elegir duración; no se arranca automáticamente tras el permiso.
- Con detector preparado, el reloj sigue visible esperando el nombre. Un evento de activación **por voz** muestra la cara con la entrada de carrera y estado de escucha. Tras **8 s** vuelve al reloj. Puede mostrar la última transcripción en memoria mientras la cara está visible.
- Botón **Silenciar + cuenta atrás** visible durante la sesión, además de la acción Detener de la notificación y la opción del menú. Salir de la app detiene captura. El vencimiento apaga la sesión; no bloquea la pantalla ni cancela timers.
- Backend independiente `wake_only_session`: detector español/Vosk existente, sin LLM, sin intérprete de intenciones y sin eventos de acciones/TTS. No se simula pensamiento: la transcripción de esta prueba conserva cara de escucha y no activa el loader.
- Android exige que el backend anuncie modo restringido, detector activo y ausencia de IA **antes de abrir AudioRecord**. En ese modo solo acepta eventos de estado/transcripción; rechaza timers, cancelaciones, `say`, `thinking` y otros eventos. Guardia adicional en el ejecutor impide aplicar herramientas cuando la sesión está detenida o en modo wake-only.
- TTS Android no se inicializa para esta sesión. Audio y texto humano no se añaden a logs/archivos; se conservan buffers y última transcripción en RAM del proceso, con la limitación ya conocida de que pueden entrar en swap.

## Pruebas realizadas antes de pedir voz humana

- **88 pruebas Python** pasan, incluyendo bloqueo del intérprete de órdenes, control de recursos, pausa/reanudación, rechazo de PCM inválido, separación entre activación manual/voz y regreso tras la ventana de escucha.
- **48 aserciones JVM** de políticas UI/clima/sesión pasan; prueban rechazo de pares/eventos incompatibles con wake-only.
- Build/firma/instalación e instrumentación UI correctos en el Moto: ocho estados dibujados, 35 dibujos/1200 ms, temporizadores sin cambios, sin micrófono ni modelo. Se probó la defensa del ejecutor contra un timer inyectado en modo restringido, sin iniciar un servicio real de captura.
- `FixtureInstrumentation` generó archivos de frases públicas con TTS sin reproducirlos ni abrir el micrófono. Después se probaron con Vosk real en el Moto por el mismo protocolo PCM, usando socketpair sin cliente ejecutor Android.
- Nueve casos sintéticos pasan: positivos con nombre, nombre + orden, negación y cita detectan activación; cinco controles sin nombre (`androide`, `Andrés`, `adormecida`, `andamio`, frase cotidiana) no la detectan. Una negación/cita con el nombre puede despertar la cara; **no se ejecuta ninguna orden**.
- El caso de orden produce transcripción con «temporizador», sin intención/acción. El caso positivo se mantuvo con PCM de silencio a cadencia real durante >8 s y devolvió estado de espera, no por timeout de socket.
- Pico RSS de ese ensayo Vosk: **153844 KiB (~150.2 MiB)**, no suma total del teléfono ni medición conjunta con LLM. Los WAV y conversiones sintéticos se retiran al terminar.

Esto **no** mide acierto con voz humana en la nueva UI, ruido/TV/distancia, ni latencia voz → respuesta del LLM. No se declara detector infalible, ni reconocimiento de órdenes aprobado.

### Confirmación humana posterior

El usuario respondió «sí» a la comprobación de si, al decir el nombre, pasaba del reloj a la carita. Queda confirmado **un despertar visual con voz humana en la UI 0.3**. No implica confirmación de transcripción correcta, regreso a los 8 s, cinco minutos completos, tasa de aciertos, silenciado físico o respuesta del modelo. No se guardó audio ni se atribuyen tiempos que no se midieron. El límite de sesión sigue siendo el elegido en pantalla; esta confirmación no autoriza escucha permanente.

El backend corrobora una activación `source=voice`. Terminó por desconexión del cliente a los 91.12 s totales (no demuestra duración completa de cinco minutos ni causa de desconexión), pico RSS 150464 KiB (~146.9 MiB), CPU acumulada 7.24 s. Comprobación ADB posterior: sin servicios de voz/alarma ni listeners 11435/11436. Micrófono y backend de prueba detenidos; no se mantuvo una escucha tras la comprobación.

## Preparar una sesión de prueba

```powershell
adb shell su -c '/data/local/doha-voice/start-andromeda-wake-test.sh 300'
```

El coordinador solo prepara el detector: **no abre el micrófono**. Corre Python/ASR como UID 10218, rechaza sustituir puertos 11435/11436 ocupados, comprueba memoria/temperatura y escucha solo en loopback. Espera al cliente como máximo 300 s; tras una sesión termina. La sesión dura como máximo lo elegido en Android o lo anunciado por el backend (el menor). No hay daemon de arranque ni reinicio automático; si caduca o se silencia, hay que preparar otra sesión desde el coordinador para repetir.

Prueba sintética reproducible (requiere generar las fixtures primero, nunca captura humana): `test-spanish-wake.sh wake_only`. La ruta histórica por defecto sigue siendo un piloto diferente, con intenciones; no usarla para esta prueba de solo activación.

## Siguiente paso

Despertar visual humano confirmado; regreso al reloj y silenciado aún no confirmados por el usuario. El usuario eligió LFM aceptando sus limitaciones casuales y pidió iniciar conversación sin acciones. Ruta 0.4 separada con plazo/cancelación/TLS y pruebas sintéticas; siguiente ensayo humano acotado según `CONVERSACION-ANDROMEDA.md`. No reabrir comparaciones por defecto, abrir control doméstico ni habilitar 24/7.
