# Activación «Doja» y temporizadores: piloto Android

**Actualización: el usuario cambió el nombre a Andrómeda.** Este documento conserva las pruebas históricas de Doja. El nombre y wake word actuales están en `config/assistant.json`, con activación deshabilitada. App e instrucciones de IA se actualizaron sin cambiar el identificador `local.doja.voice`, sus datos, credenciales ni timers. El nuevo wake word aún no se probó con voz.

Avance posterior: el motor español para Andrómeda sí pasó un piloto con voz real y temporizador audible. Ver `ACTIVACION-ANDROMEDA.md`; el estado permanente sigue deshabilitado. Los fallos KWS de esta página no describen el nuevo detector Vosk.

Fecha de trabajo: 2026-10-16, hora local del dispositivo.

## Estado comprobado hasta ahora

- App nueva `local.doja.voice` instalada, fuente en `android-voice/`. **No se reemplazó ni modificó el launcher `local.doha.dashboard`**, que sigue siendo HOME.
- Captura PCM16 mono de 16 kHz en tiempo real mediante `AudioRecord`. No necesita Termux:API, FFmpeg ni grabar un archivo para cada petición.
- Botón «Hablar ahora»: usuario confirmó que reconoció «pon un temporizador de diez segundos», habló la confirmación y sonó la alarma. Esta ruta **no valida la palabra Doja**.
- El fin de frase lo determina el endpoint del reconocedor Vosk; ya no hay una captura fija de ocho segundos por orden. El máximo de orden en este piloto es 12 segundos.
- Activación por «Doja» **pendiente de validar**: el primer detector inglés BPE no la detectó; el segundo intento con fonemas tampoco quedó validado. Se preparó una calibración comparativa de aciertos y falsas activaciones.
- No se dejó escucha permanente ni arranque de micrófono al encender. Pilotos con consentimiento y duración acotada; app y backend tienen límites.

## Arquitectura

```text
App Android local.doja.voice (sin root)
  reloj + estados/cara + mute visible + notificación de micrófono
  AudioRecord → PCM local → socket autenticado 127.0.0.1:11436
                       ↕
Debian, UID 10218 (Termux), sin root
  detector KWS pequeño → Vosk español al activarse → gramática determinista
                       ↕
App Android
  persiste y programa temporizador con AlarmManager → confirma mediante TTS
  al vencer: RingService, sin dependencia del LLM ni del servidor Debian
```

La IA de conversación LFM no se carga en este piloto. Las preguntas generales reciben una explicación de que solo se prueban temporizadores; no se envían al modelo que ya mostró errores factuales.

## Captura, privacidad y controles

- Botón **SILENCIAR / detener micrófono**, acción DETENER en notificación, parada automática a los 90 s y parada al salir de la pantalla de la app.
- No hay grabaciones a disco ni registro de transcripciones. El backend mantiene un preroll de hasta 1.5 s en RAM para no perder el comienzo de la orden tras detectar el nombre. La transcripción solo se muestra temporalmente en la pantalla y se borra al parar.
- Durante TTS y mientras suena la alarma, la app detiene la captura; espera unos 600 ms antes de reanudar para reducir eco. No es cancelación de eco acústica completa.
- Por esa pausa, **detener una alarma que está sonando se hace con el botón/notificación**, no mediante voz en esta versión. El sonido también se detiene solo tras 30 s. No presentar «silencia» por voz como validado mientras el tono está activo.
- Clave aleatoria generada en datos privados de la app; provisionada en `/var/lib/doja-voice-pilot/token` dentro de Debian, UID 10218, modo 600. El valor no está en el código ni en este documento. La app no tiene acceso root.
- El socket no escucha en la LAN. Protocolo con límites de tamaño, autenticación, no acepta URL/órdenes de shell ni herramientas del LLM. Audio y comandos no salen del teléfono.
- Como en las pruebas previas, RAM puede terminar en zram/swap: no prometer borrado forense de voz solo por no crear archivos.
- Backend comprueba RAM disponible y temperatura de batería: mínimo 500000 KiB y menos de 40 °C; aborta si pierde esas lecturas. No sustituye control térmico original ni la protección de carga aún pendiente.

## Temporizadores

- Crear: «pon un temporizador de diez segundos», «temporizador de cinco minutos» o duración compuesta de horas/minutos/segundos enteros.
- Cancelar: «cancela el temporizador»; si hay varios, pide aclarar o cancelar todos. No adivina qué temporizador se quiso cancelar.
- Listar/consultar: «lista los temporizadores» o «cuánto falta».
- Límites: 1 s a 24 h; máximo cinco activos. Se rechazan negaciones, alternativas ambiguas, decimales, unidades repetidas y números sobrantes según la gramática implementada.
- Android guarda el estado antes de confirmar. UUID por petición para evitar reprogramar el mismo ID. Vencimiento por reloj monotónico y fecha de recuperación persistida.
- `BootReceiver` implementa restauración de timers al reiniciar, sin abrir micrófono. **Restauración tras reinicio, cancelación real y múltiples timers todavía no se probaron en el teléfono.** No se reinició el equipo.
- No son todavía recordatorios de calendario ni alarmas por fecha/hora. Un force-stop/desinstalación de Android puede cancelar alarmas; no usar esos mecanismos como sustituto de silenciar el micrófono.

## Resultados y fallo corregido

1. Primer KWS (inglés BPE): usuario reportó que no detectó Doja. Ejecución total 98.78 s incluyendo espera/preparación, pico RSS 97524 KiB (unos 95 MiB), CPU acumulada 21.57 s. No es una medición de reposo prolongado.
2. Candidato por fonemas chino/inglés: no validado; usuario reportó que tampoco funcionó el botón. Se encontró una escritura de red desde el hilo principal de Android en los controles, que podía causar `NetworkOnMainThreadException` y cerrar el servicio. Se movieron los controles a un executor propio y se conservaron mensajes de error sin secretos.
3. Botón corregido: usuario confirmó temporizador y alarma. Backend registró una activación manual y una intención `timer` de 10 s; pico RSS 214472 KiB (unos **209 MiB**) durante la sesión completa con Vosk, CPU acumulada 11.64 s en 48.66 s incluyendo esperas y sonido. No se cargó el LLM.
4. PSS de la app observado después de detener los servicios: 72195 KiB (unos 71 MiB). **No es el pico durante captura**, y PSS/RSS de instantes distintos no se deben sumar como consumo total exacto.
5. Se confirmó que no quedaban servicios VoiceService/RingService activos al cerrar esa prueba. Intentar detener el servicio no exportado desde `am stopservice` de shell fue rechazado; se usaron los controles de la propia UI, sin hacer el servicio público.
6. Primer intento de calibración cerró antes de generar bloques válidos: inicializar tres detectores superó la espera del handshake. No se cuentan sus datos como positivos/negativos. Corregido precargando detectores antes de aceptar conexión y haciendo que la app reintente el canal hasta 30 s sin capturar todavía.
7. Prueba de integración en Debian `tests/device_calibration_silence.py`: recorrió los seis bloques y tres configuraciones con PCM sintético de ceros, sin micrófono; protocolo completo y cero detecciones verificados. No sustituye calibración de voz real.

16 pruebas unitarias Python pasaron (gramática, protocolo y helpers de voz). Compilación Java/D8, firma y verificación APK pasaron. Estas pruebas no sustituyen los ensayos acústicos ni prueban todas las rutas Android.

## Detector y calibración experimental

- Runtime: `sherpa-onnx==1.13.8`, `numpy==2.4.6`; preparación de tokens con `sentencepiece==0.2.2`, dentro de `/opt/doha-voice`.
- Primer modelo: GigaSpeech KWS 3.3M, inglés, 2024-01-01. Hash local calculado de archivo descargado por HTTPS: `f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a` (no checksum independiente validado).
- Segundo: `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`, **chino/inglés, no español**. SHA-256 comparado con metadata GitHub: `68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6`.
- Encoder y joiner INT8; decoder FP32; un hilo por detector, chunk 16. Son representaciones fonéticas candidatas de Doja/Doha, **no un modelo acústico entrenado para el español del usuario**.
- Prueba de silencio de un segundo: sin detección, unos 79–81 MiB RSS de backend. No demuestra una tasa baja de falsas activaciones en una casa.
- Calibración preparada: seis bloques de 7 s (silencio, Doja, roja, hoja, ducha, Doja). Solo compara conteos; no graba audio, no guarda perfil de voz y no crea alarmas. Compara score/umbral 1.0/0.35, 1.0/0.25 y 1.5/0.25.
- Unos pocos ensayos no permiten garantizar tasa de falsas activaciones. **No habilitar escucha permanente solo por un acierto aislado.** Si ninguna variante distingue bien Doja de las palabras cercanas, entrenar/calibrar un detector apropiado o acordar otro motor; no ampliar alias ni bajar umbrales indiscriminadamente.

Resultado de la calibración real corregida: los seis bloques se completaron y todas las variantes dieron cero detecciones, incluso en los dos bloques Doja. El usuario confirmó que vio y repitió las palabras. Se ejecutó además `tests/device_kws_positive.py`: el mismo motor detectó `LIGHT_UP` en el WAV de referencia oficial, sin micrófono. Por tanto esos candidatos no quedaron aceptados para la pronunciación probada; no se interpreta silencio como precisión. RSS máximo durante calibración: 128684 KiB, CPU acumulada 26.21 s en 51.58 s incluyendo inicialización. No se guardó audio ni perfil.

Tras cambiar el nombre se inspeccionó Vosk español sin micrófono: `andrómeda=62224`, `andromeda=-1`. Se preparará la alternativa española con la forma acentuada y pruebas de confusión; **no se ha implementado todavía esa gramática de escucha ni se dio por válida la activación**. El backend actual permite únicamente activación manual en la configuración nueva y rechaza arrancar la calibración antigua de Doja accidentalmente.

Fuentes: https://k2-fsa.github.io/sherpa/onnx/kws/ y https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/ . La licencia del runtime es Apache-2.0; revisar además las condiciones de los modelos/datos antes de redistribuirlos comercialmente, sin asumir que todo el conjunto comparte automáticamente esa licencia.

## Construcción y archivos

- App: `android-voice/src/local/doja/voice/`; build local `powershell -ExecutionPolicy Bypass -File android-voice/build.ps1`.
- Usa JDK del PC y Android SDK/build-tools existentes, sin Gradle ni dependencias Android externas. APK, clases y keystore de desarrollo quedan en `artifacts/android-voice/`, excluidos del repositorio. Firma de desarrollo, no preparada para publicación.
- Backend: `backend/doja_stream.py`, `doja_intents.py`, `prepare_kws.py`; instalado en `/opt/doha-voice/pilot` dentro de Debian.
- No editar datos privados ni desinstalar para «limpiar» sin revisar temporizadores pendientes y credenciales. No se crearon hooks Magisk para este piloto.

## Pendientes

1. Completar y revisar la calibración de Doja; validación positiva y negativa antes de más automatización.
2. Probar cancelación, timers múltiples, mute durante habla y restauración tras reinicio autorizado.
3. Medir CPU, RAM y temperatura sostenidas con detector ya seleccionado.
4. Integración con la interfaz final de reloj/cara, arranque y modo permanente solo tras resolver estabilidad, privacidad y protección de carga.
5. Conectar después herramientas de Internet, memoria efímera por conversación y Home Assistant en host compatible; no están implementados en este piloto.
