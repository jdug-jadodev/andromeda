# Andrómeda: activación española y temporizador por voz

## Resultado

**El usuario confirmó en el Moto:** dijo el nombre y la orden sin pulsar «Hablar ahora», se confirmó el temporizador y sonó la alarma. Es una validación de un piloto controlado, no una garantía de detección infalible ni una configuración 24/7.

La app conserva `local.doja.voice` como identificador interno para no perder datos/credenciales. Su nombre visible es Andrómeda. HOME continúa siendo el launcher Doha original.

## Cómo funciona

- Identidad: `config/assistant.json`, palabra exacta `andrómeda` (con tilde en el vocabulario de Vosk).
- `backend/spanish_wake.py` usa el modelo español ya instalado, gramática pequeña con `[unk]` y alternativas conocidas para no obligar al reconocedor a elegir siempre el nombre.
- Comprueba palabra completa, normalización de mayúsculas/acentos y estabilidad de parciales durante 0.16 s. Usa umbral heurístico de confianza 0.85 cuando Vosk aporta ese dato. **No es una probabilidad calibrada de que el usuario se dirija al asistente.**
- Al activarse, reutiliza el mismo `vosk.Model` para la orden completa. Un preroll de hasta 1.5 s evita perder el comienzo de «pon un temporizador» cuando se habla seguido.
- Captura PCM y final de frase en tiempo real; la orden tiene límite de 12 s. No usa ventana fija de ocho segundos ni el LLM para temporizadores.
- Android guarda/programa la alarma antes de hablar la confirmación. Mute, pausa durante TTS/alarma, cierre al salir de la pantalla y límite de sesión siguen activos.
- La UI y los eventos distinguen activación real por voz (`source=voice`) de activación manual (`source=button`).

## Pruebas sin micrófono

- 21 pruebas unitarias pasaron: gramática, protocolo, identidad y estabilidad de la palabra.
- `FixtureInstrumentation` generó cinco frases públicas prefijadas con el TTS Android, sin reproducirlas, abrir micrófono ni crear alarmas. Seleccionó voz española declarada sin requisito de red (`es-US-language`). Esto no sustituye una auditoría de tráfico del motor Google ni la prueba de funcionamiento con la red desconectada.
- Frases positivas: «Andrómeda» y «Andrómeda, pon un temporizador de diez segundos».
- Controles negativos: frase con «androide», frase con «Andrés» y otra frase cotidiana. Ninguna activación en esos tres archivos.
- Detectó las positivas aproximadamente a los 1.36 y 1.60 s desde el comienzo de **esos archivos de audio**. No son latencias universales medidas desde el fin de una palabra.
- Prueba del protocolo/estado real con el audio sintético: una activación por voz y una intención `timer=10`; no había cliente Android para programar una alarma. Se comprobó también que un heartbeat no crea acciones.
- Se detectó y corrigió una variante de ASR («pone» en vez de «pon»), añadiendo imperativos explícitos, no un LLM ni coincidencia difusa. Relatos/ejemplos y duraciones ambiguas se rechazan.
- Los WAV sintéticos y conversiones temporales se borraron tras probarlos. No eran grabaciones del usuario.

Código de pruebas: `tests/test_spanish_wake.py`, `tests/device_spanish_wake.py`, `scripts/test-spanish-wake.sh` y `android-voice/src/local/doja/voice/FixtureInstrumentation.java`.

## Piloto real autorizado

Registro del backend:

- Dos activaciones, ambas `source=voice`, motor `vosk_es`.
- Primer turno: respuesta tipo `say`, sin ejecutar acción; no se retuvo transcripción para diagnosticarlo retrospectivamente.
- Segundo turno: intención `timer`, duración 10 s, unos 3.99 s entre activación y envío de la intención (incluye hablar la orden y detectar su final; no es solo tiempo de cómputo).
- Usuario confirmó activación, confirmación hablada y alarma audible sin usar el botón.
- Pico RSS backend: **155072 KiB, unos 151 MiB**. No incluye Android, app ni motor TTS; no representa consumo total del teléfono.
- CPU acumulada: 10.05 s en 58.98 s de ejecución incluyendo espera, habla y pausas. No es un benchmark de reposo continuo.
- No se cargó el modelo LFM de conversación ni se hicieron consultas web.

Al cerrar se comprobó: sin servicios VoiceService/RingService activos, sin listeners 11435/11436 y HOME original intacto. Lectura posterior, no temperatura máxima de todo el ensayo: batería 24.4 °C, 100 %, `MemAvailable=1261688` KiB (~1.20 GiB). Swap de disco: 908 KiB usados, sin aumento respecto de las lecturas anteriores. Zram no fue desactivada ni aumentada.

## Estado y cómo repetir un piloto

`wake_enabled=false` sigue en la configuración: **no hay escucha permanente ni arranque automático del micrófono**. El helper usa 90 s por defecto; acepta `300` como argumento para cinco minutos. La app ofrece elegir explícitamente 90 segundos o cinco minutos antes de abrir el micrófono. Elegir la misma duración en ambos: el primer límite alcanzado cierra la sesión.

Desde PC, iniciar el backend del piloto (sin abrir micrófono):

```powershell
adb shell su -c /data/local/doha-voice/start-andromeda-pilot.sh
```

Luego abrir la app Andrómeda y pulsar «Iniciar prueba». El helper rehúsa sustituir un servicio existente en 11436; espera conexión hasta unos 180 s y solo escucha en loopback. Salir de la app o pulsar SILENCIAR detiene la captura. La palabra por sí sola no funciona fuera de una sesión iniciada.

Se añadió heartbeat mientras la captura se pausa durante TTS/alarma, para evitar que un tono de 30 s provoque el timeout de conexión de 15 s. Compilación y protocolo sintético pasaron después de ese cambio; **reanudar detección tras una alarma larga todavía requiere un ensayo físico adicional**.

## Antes de dejarlo permanente

### Avance de robustez y cancelación

**Cambio de dirección solicitado por el usuario:** el piloto de reglas no satisfacía el objetivo de usar IA conversacional. Se añadió un coordinador separado con LFM2.5 local para conversación e interpretación flexible, manteniendo ejecución comprobada por Android y confirmación humana previa. Ver `IA-ANDROMEDA.md` para pruebas, límites e instrucciones. Esta integración pasó texto/voz sintética con modelo real; la cancelación con voz humana sigue pendiente y no se afirma corregida por esos ensayos.

Ensayo posterior con límite de 300 s: se preparó un único timer de 180 s y se eligió cinco minutos con autorización. Hubo seis activaciones: cinco `say` y un timeout sin palabras pendientes; no hubo `cancel_timer`. El usuario informó la respuesta «aún no selecciono temporizadores» y que tampoco funcionó la frase alternativa. No se conoce la transcripción exacta: no se registraba y la UI la borraba al activarse de nuevo. No atribuir el fallo a una palabra concreta ni declarar solucionada la cancelación. Se detuvo por UI a los 161.72 s totales del coordinador (RSS máximo 155336 KiB, CPU 23.76 s), se canceló el timer por botón y se verificó `timers.xml` vacío y sin servicios activos. Esto tampoco prueba cinco minutos completos de funcionamiento.

Se corrigió la pérdida del diagnóstico: la última transcripción se conserva en memoria del proceso al detectar otra activación, silenciar y terminar; la actividad puede recuperarla mientras viva el proceso. No se añade audio ni texto a logs/preferencias. APK recompilado e instalado; 23 pruebas Python e instrumentación aislada pasaron. Es una mejora diagnóstica, **no una corrección demostrada del reconocimiento**. No se inició otra escucha.

Actualización posterior (2026-10-16): la creación real de 180 s se verificó en la app (168 s restantes), pero la cancelación de esa sesión no llegó a producir intención y se retiró por UI. En otra sesión se preparó un timer con consentimiento y el usuario creó otro: el backend reconoció `cancel_timer`, pero eso no prueba borrado; con varios timers una cancelación singular no selecciona uno. Sigue pendiente validar por voz «cancela todos los temporizadores» o cancelar uno cuando solo exista uno. Tras reconectar USB se comprobó que `timers.xml` estaba vacío y no había servicios activos.

Para reducir pruebas apresuradas se compiló e instaló la selección explícita 90 s/5 min, conservando apagado al salir y el botón Silenciar. Coordinador instalado acepta únicamente `90` o `300`; backend admite como máximo 300 s. Pasaron 23 pruebas Python y la instrumentación aislada de temporizadores. No se abrió micrófono ni se validó todavía la duración real de cinco minutos. Para próximo ensayo: `adb shell su -c '/data/local/doha-voice/start-andromeda-pilot.sh 300'`, después elegir cinco minutos en la app, únicamente con consentimiento.

- 22 pruebas unitarias Python pasaron. La gramática rechaza cancelaciones citadas («ayer dije cancela…»), diferidas, con selección de un temporizador por nombre/número/duración aún no soportada, o con datos ambiguos. No adivina cuál cancelar.
- Android ahora persiste el borrado antes de retirar la entrega programada. Una entrega ya en cola no debe reproducir sonido si `consume()` no encuentra un registro activo. También invalida el PendingIntent cancelado.
- Se añadió un botón «Cancelar temporizadores…» con confirmación explícita para poder administrarlos aun sin backend o micrófono. Afecta solo a los timers de esta app.
- `TimerInstrumentation` pasó en el Moto usando preferencias aisladas y UUID aleatorios: crear, persistir, no reprogramar un ID duplicado, rechazar duración cero, cancelar uno, no modificar varios ante petición ambigua, cancelar todos, ignorar entregas canceladas/duplicadas y recalcular un vencimiento de recuperación. Programó y retiró alarmas de prueba mediante las APIs reales, pero no arrancó micrófono, TTS ni sonido; los registros de esos UUID no existen en las preferencias reales que consulta el receptor. Se comprobó que no quedaron archivos `timer_integration_*`.
- Esa prueba **no es un reinicio físico** ni una prueba de recuperación bajo fallo de disco o corte de alimentación. Esos casos siguen pendientes.
- Se ampliaron las frases sintéticas a diez. Cinco controles sin el nombre no activaron el detector. Las frases con «Andrómeda» sí activaron; se verificaron intenciones de creación, cancelación y respuesta sin acción para una negación y una cita. No hubo cliente Android ejecutando esas intenciones sintéticas, por lo que no se crearon/cancelaron timers reales en ese ensayo.
- `scripts/test-spanish-wake.sh` elimina los diez WAV públicos de prueba y sus conversiones al terminar. No eran grabaciones del usuario.
- Piloto posterior: dos activaciones por voz; primer turno `say`, segundo `timer=10`. Pico RSS 155052 KiB; CPU 18.87 s en 97.75 s incluyendo preparación/espera. El usuario aclaró que pidió diez segundos, no los dos minutos sugeridos para el ensayo: **la creación corresponde a lo pedido, pero no hubo una cancelación por voz registrada**. No se da esa ruta real por validada ni se usan esos turnos para calcular una tasa de falsos positivos.
- Al finalizar se detuvo la escucha y se consultó la app: «No hay temporizadores activos». La siguiente comprobación con voz será crear uno más largo, verificarlo y pedir cancelar en un paso separado con consentimiento.

1. Pruebas con voz real de palabras cercanas, televisión, música, distancias y periodos de silencio; medir falsos positivos y omisiones durante más tiempo. Tres negativos sintéticos y un turno correcto no bastan.
2. Confirmar cancelación real, múltiples temporizadores, mute, eco y recuperación tras alarma. Restauración por boot está implementada, pero no se reinició el teléfono para probarla.
3. Medir CPU/RAM/temperatura sostenidas, también SoC; conservar protecciones térmicas originales.
4. Endurecer el canal antes de uso permanente: actualmente autentica al cliente con clave privada en loopback; falta autenticación mutua o una restricción equivalente contra suplantación por otras apps locales. No publicar el socket en LAN/Internet.
5. Fijar y comprobar TTS offline en el uso real si se requiere esa garantía; el motor Google es un componente distinto del reconocimiento local.
6. Completar la protección de carga 24/7: el monitor existente solo observa.
7. Integrar launcher/arranque y conversación general únicamente después de estabilidad y calidad. Internet, Home Assistant, memoria por conversaciones y recordatorios de calendario no están conectados a este piloto.
