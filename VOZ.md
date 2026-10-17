# Primera base de voz local — 2026-10-16

**Estado actual:** Andrómeda despertó por voz y creó un temporizador que el usuario confirmó audible en un piloto, sin botón ni LLM. Pico del backend ~151 MiB; escucha cerrada al finalizar. Ver `ACTIVACION-ANDROMEDA.md`. Las secciones siguientes conservan la cronología de los ensayos anteriores y sus limitaciones.

**Último ensayo:** usuario confirmó la respuesta sobre el Sol; luego se probó servidor temporal con caché ajustada y una captura de voz autorizada. Procesamiento posterior a captura: 13 s más 8 s de preparación inicial aparte; volvió a aparecer una respuesta factual incorrecta. No se dejó servidor ni micrófono activos. Ver `PRUEBA-VOZ-OPTIMIZADA.md`; velocidad y calidad aún no son suficientes para dar el asistente por terminado.

**Estado más reciente:** el usuario eligió continuar localmente. Candidato LFM2.5 1.2B QAD Q4_0 instalado y conectado al prototipo bajo demanda; primera captura completa ejecutada, pero respuestas todavía simplificadas y latencia elevada (18 s después de grabar hasta invocar TTS). Ver `PRUEBA-MODELO-LOCAL.md` para resultados, memoria y limitaciones; las siguientes secciones conservan la cronología de pruebas anteriores.

## Prioridad

Interfaz principal: voz en el Moto, con reloj/cara. Texto solo como opción remota desde PC en `IP-del-Moto:10000`, todavía no implementada ni expuesta. No se habilitó escucha permanente ni permiso de micrófono en esta fase.

**Actualización posterior:** usuario confirmó que escuchó la salida TTS después de ajustar volumen. Luego autorizó explícitamente una captura de 8 segundos mediante la pregunta de preparación. Se concedió `RECORD_AUDIO` a Termux:API y se probó micrófono local. No hay escucha continua; al terminar, `termux-microphone-record -i` confirmó `isRecording=false`.

## Primera prueba de captura y pipeline

- Instalado FFmpeg Debian para convertir AAC a WAV mono PCM 16 kHz. Su paquete arrastró dependencias multimedia: unos 400 MB adicionales **de almacenamiento**, no RAM residente permanente. No se inició un escritorio ni un servicio multimedia por ello. Revisar alternativa de conversión mínima si luego se optimiza espacio.
- Orquestador bajo demanda `/data/local/doha-voice/voice-once.sh`, fuente en `scripts/voice-once.sh`. `--check` no graba, `--once` avisa por voz y captura 8 segundos; `--sample` prueba una pregunta fija sin abrir el micrófono. No tiene hook de arranque ni bucle de escucha.
- Root solo coordina los pasos. FFmpeg, Vosk y el modelo se ejecutan como UID 10218; el modelo nunca ejecuta herramientas ni comandos generados.
- Archivos temporales privados y limpieza al terminar/error: captura Android, copia AAC, WAV, prompt y respuesta. Comprobado que no quedaban directorios/archivos de esa prueba. Esto es eliminación lógica, no borrado forense garantizado en flash/swap. Ante apagado abrupto o SIGKILL no se garantiza la ejecución del trap; falta limpieza de huérfanos al arrancar antes de uso permanente.
- Se rechaza iniciar con menos de 650000 KiB de RAM disponible o batería a 40 °C o más, y se vuelven a comprobar recursos entre tramos. **No es control térmico continuo ni corte de carga.** Inferencia limitada a 40 s y una captura a la vez con flock.
- Captura real autorizada: Vosk reconoció «porque el cielo es azul». Audio 8.06 s; transcripción completa (incluyendo carga de modelo) 2.56 s; pico RSS 146148 KiB (unos 143 MiB). Vosk termina antes de cargar el LLM para evitar superponer sus picos.
- Primer intento se detuvo tras transcribir: `--log-disable` de este binario también suprimía el texto generado. Corregido: sin esa opción, descartando stderr de inferencia y conservando stdout temporal. Cinco pruebas unitarias de normalización/control de tokens pasaron; prueba de sintaxis shell correcta.
- Replay de pregunta fija, sin segunda grabación, llegó a salida TTS y limpió temporales. Unas 7 s hasta invocar TTS en el replay Qwen2.5, **sin contar grabación ni transcripción**. Falta una prueba integrada completa satisfactoria y medición de latencia hasta sonido real.
- Temperaturas observadas en estos ensayos: aproximadamente 27.9–28.2 °C. RAM disponible al finalizar: 1312408 KiB, unos 1.25 GiB. No equivale a haber medido el mínimo bajo carga ni estabilidad sostenida.

### Calidad: bloqueo antes de considerar funcional el asistente

El modelo Qwen2.5 pequeño dio una explicación falsa sobre la Luna. Se probó además `unsloth/Qwen3-0.6B-GGUF`, revisión `50968a4468ef4233ed78cd7c3de230dd1d61a56b`, archivo `Qwen3-0.6B-Q4_K_M.gguf`, 396705472 bytes (SHA-256 verificado: `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a`). Este segundo archivo es más pequeño que el anterior; no se cargaron ambos simultáneamente.

Qwen3 se probó con prefijo de plantilla sin razonamiento (`<think>\n\n</think>`) y límite de salida; las pruebas también produjeron errores factuales («oído» y explicación por refracción). Cambiar a un prompt más corto no resolvió la calidad. **Ninguno queda validado como asistente fiable de preguntas generales.** El orquestador apunta actualmente a Qwen3 solo como prototipo experimental; no presentarlo como solución terminada ni asumir corrección por generar una frase fluida.

Explicación correcta de la pregunta de prueba: las moléculas de la atmósfera dispersan la luz solar de longitud de onda corta más intensamente (dispersión de Rayleigh), haciendo que veamos el cielo azul. La respuesta errónea enviada en el replay fue identificada y corregida en la conversación del proyecto.

Siguiente decisión: probar un modelo local algo más capaz con medición estricta de pico de RAM/latencia, o acordar inferencia híbrida para preguntas generales. Las herramientas verificadas y fuentes de Internet también serán necesarias; no se implementaron todavía. Alarmas y domótica seguirán siendo deterministas, no decisiones libres de un modelo pequeño.

## Instalado y probado

### Inferencia

- `llama.cpp` oficial para Android ARM64, build `b11669`, commit `620d2225c`, en `/data/local/doha-inference/llama-b11669`.
- Elegido como alternativa CPU ligera: el paquete actual Ollama ARM64 completo pesa aproximadamente 1.56 GB; el de llama.cpp probado ocupa unos 75 MB comprimido. **Ollama no está instalado**. Se puede reconsiderar si aporta ventajas; la API del asistente no debe depender de un motor específico.
- Modelo oficial `Qwen/Qwen2.5-0.5B-Instruct-GGUF`, archivo `qwen2.5-0.5b-instruct-q4_k_m.gguf`, revisión `9217f5db79a29953eb74d5343926648285ec7e67`, licencia Apache-2.0. El archivo tiene 491400032 bytes; el benchmark describe internamente unos 630 M parámetros. Etiquetar por nombre de publicación, sin ocultar esa diferencia.
- Binarios/modelo públicos en `/data/local/doha-inference`, propiedad de root y no modificables por el proceso de inferencia. Las pruebas de inferencia se ejecutaron con UID Termux 10218, **sin root**. No se guardan conversaciones en ese directorio.
- Una ejecución corta, CPU, sin GPU: generación de 32 tokens con 2 hilos, **11.07 tokens/s**; con 4 hilos, **15.07 tokens/s**. Procesamiento de prompt de 64 tokens: 17.17 y 33.69 tokens/s respectivamente.
- Respuesta real de saludo en español con contexto 1024 y máximo 48 tokens: aproximadamente **5.5 s en el proceso completo en frío**, generación 16.20 tokens/s; no es latencia de voz completa. Falta medir servidor caliente, historial corto y concurrencia con reconocimiento.
- Batería alrededor de 29.3 °C en el benchmark corto; no es validación térmica sostenida.
- No hay daemon de inferencia ni puerto de IA expuesto/autoiniciado todavía.

### Reconocimiento de voz

- Debian: Python 3.11, venv y `libatomic1`; no se actualizó todo el sistema.
- Entorno aislado `/opt/doha-voice` dentro de Debian con Vosk 0.3.45.
- Modelo español `vosk-model-small-es-0.42` de https://alphacephei.com/vosk/models/ en `/opt/doha-voice/models/`.
- Prueba `scripts/check-voice-readiness.py` ejecutada sin root: carga en **0.84 s**, reconocimiento de un segundo de silencio en **0.381 s**, sin texto espurio.
- Esto NO mide precisión con voz ni velocidad con habla real; no abrió micrófono ni realizó grabaciones.
- El servicio Android de reconocimiento de voz no está disponible; se evita depender de él.

### Puente Android y salida de voz

- Instalados comandos `termux-api` 0.60.0 usando repositorio oficial y firmas APT.
- Se utilizó una lista temporal para la instalación, sin sobrescribir las listas originales de Termux. Los comandos ejecutados como su UID necesitaron grupo suplementario `3003` (`inet`); sin él fallaba DNS. No atribuir esos fallos por sí solos a una avería del repositorio original.
- App Termux:API existente: 0.53.0. Compatibilidad confirmada para enumerar motores; el resto de APIs se valida individualmente.
- Reactivado paquete existente `com.google.android.tts` (antes deshabilitado); detectado como motor predeterminado. Para revertir esa reactivación: `adb shell pm disable-user --user 0 com.google.android.tts`.
- Enviada prueba TTS «Hola, soy Doha. Esta es una prueba de voz». **Audibilidad, idioma y calidad pendientes de confirmación del usuario.** No asumir que las voces offline en español están descargadas ni que cada llamada TTS fue exitosa solo por no emitir salida en consola.
- Verificación posterior: la primera repetición agotó timeout de 15 s. Se abrió la pantalla de datos TTS, que muestra español de España y Estados Unidos en «Tus idiomas»; no se descargaron otros idiomas. Tras inicializar esa pantalla, prueba `termux-tts-speak -l es -n ES -s MUSIC "Hola, soy Doha."` terminó con código **0** dentro del límite de 12 s. Se devolvió el teléfono al launcher con Inicio. Audibilidad y funcionamiento completamente offline aún por confirmar; no atribuir el fallo inicial a ausencia de idioma como hecho comprobado.
- El usuario confirmó que **no oyó la prueba anterior**: la síntesis audible sigue sin validar. Diagnóstico de audio: notificaciones silenciadas, multimedia no silenciado y enrutado al altavoz, Bluetooth apagado. Nueva prueba con motor explícito `com.google.android.tts`, idioma `es`, región `US` y canal `MUSIC`; volumen multimedia ajustado de 6 a 7 (según lectura por dispositivo), sin cambiar silencio de llamadas/notificaciones. Comando terminó con código 0; pendiente que el usuario confirme «Hola. Soy Doja. Esta es una prueba del altavoz». Volvió a HOME. Un código 0 no basta como criterio de éxito audible.
- `RECORD_AUDIO` seguía sin conceder en la inspección; no se cambió. El próximo ensayo debe ser explícito y breve, con aviso antes de grabar, borrado del audio y sin escucha continua.

## Integración de voz pendiente

1. Confirmar prueba audible; elegir voz local en español o resolver datos offline del motor.
2. Conceder únicamente permiso de micrófono a Termux:API y probar captura breve por acción explícita. Convertir a PCM mono 16 kHz si el formato de captura lo requiere.
3. Pasar audio a Vosk, inferencia corta y TTS, midiendo cada tramo. No conservar audio/transcripciones en logs por defecto.
4. Exponer puente solo en loopback con autenticación y acciones permitidas; no ejecutar comandos del modelo como shell. La IA no necesita acceso root.
5. Mantener modelo caliente si la RAM lo permite; serializar inferencia. Incorporar memoria por conversación con TTL de una hora, sin bloquear la primera prueba de voz.
6. Empezar con pulsar para hablar, luego VAD/palabra Doha o Doja. Evitar escuchar la propia salida TTS; añadir mute visible.
7. Home Assistant y herramientas de Internet se integran después de verificar esta ruta; todavía no hay control de aparatos ni consultas web desde el modelo.

## Home Assistant: bloqueo comprobado

El kernel reporta deshabilitados `CONFIG_PID_NS`, `CONFIG_VETH`, `CONFIG_OVERLAY_FS`, `CONFIG_CGROUP_DEVICE` y `CONFIG_CGROUP_PIDS`. Además, `CONFIG_USER_NS` está deshabilitado. Esto impide asumir Docker estándar o rootless funcional; no basta con instalar su paquete dentro de Debian.

No se instaló Home Assistant. Elegir un host compatible externo (PC, mini-PC, Raspberry Pi u otro), conectándolo al asistente mediante API y permisos mínimos. Si el usuario exige que todo resida en el teléfono, discutir explícitamente una instalación experimental no soportada y sus riesgos antes de intentarla; no cambiar kernel/ROM ni degradar versiones por iniciativa propia.

## Artefactos e integridad

Descargas en `artifacts/` del PC, excluidas de publicación. Para llama.cpp y Qwen se comparó SHA-256 con los metadatos del distribuidor. Para Vosk solo se calculó el hash local sobre descarga HTTPS del sitio oficial; no hubo checksum independiente publicado verificado.

```text
640f870f795b3b2939ed40d1a31ce529ecc4865abfc5d0f5153168bc4b82e5c8  llama-b11669-bin-android-arm64.tar.gz
74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db  qwen2.5-0.5b-instruct-q4_k_m.gguf
09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f  vosk-model-small-es-0.42.zip
```

Fuentes de binario/modelo:

- https://github.com/ggml-org/llama.cpp/releases/tag/b11669
- https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/tree/9217f5db79a29953eb74d5343926648285ec7e67

El control adicional de carga sigue pendiente; el monitor de batería no constituye un corte de seguridad. No se alteró la swap, zram, microSD, kernel ni arranque Debian durante estas pruebas.

## RAM y temperatura: política de integración

Usuario prioriza consumo reducido y evitar calentamiento. Android debe permanecer para hardware e interfaz; no puede entregar toda la RAM a Debian conservando esos servicios. No se borrarán cachés ni deshabilitarán servicios esenciales para simular más memoria libre.

- Última lectura en reposo: `MemAvailable=1368472` KiB (aproximadamente 1.3 GiB), batería 29.5 °C. Es una instantánea, no una garantía de margen con todo el pipeline cargado.
- Zram observada: unos 668 MiB lógicos usados; swap de disco: 908 KiB usados. No se aumentó ninguna. Zram consume RAM física comprimida y CPU; swap de disco puede degradar latencia y desgastar almacenamiento.
- Priorizar TTS del sistema ya existente, Vosk pequeño y un único modelo de lenguaje pequeño. No añadir Whisper/Piper/modelos grandes simultáneamente por defecto. Falta medir el consumo real del motor TTS, no asumir que es despreciable.
- Medir pico de RAM/PSS, `MemAvailable`, variación de swap, carga y temperatura por tramo y durante una prueba sostenida. Probar conservar LLM caliente frente a descargarlo; decidir por latencia y margen medido.
- Serializar solicitudes y mantener contexto/respuestas cortos; no inferencia especulativa ni tareas LLM en segundo plano.
- Diseñar límite de admisión por memoria (objetivo inicial 600–800 MiB disponibles, ajustable), pausas por presión sostenida y reducción de hilos/carga con temperatura creciente. Estas medidas **todavía no están implementadas** en un supervisor; el monitor actual solo observa.
- Evitar espera activa y ciclos de reintentos continuos. Las alarmas/temporizadores no necesitan un LLM cargado.
- Si la ruta completa no cabe con margen o no alcanza la latencia requerida, presentar la alternativa híbrida antes de forzar uso intensivo de swap. No prometer ausencia de calor: parte del trabajo de la prueba es verificarlo.
