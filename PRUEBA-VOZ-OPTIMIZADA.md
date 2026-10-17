# Prueba de inferencia residente temporal y voz

Fecha: 2026-10-16. El usuario confirmó que oyó la primera respuesta sobre el Sol y después autorizó explícitamente una prueba de voz optimizada.

## Qué se cambió

- Se añadió un servidor llama.cpp **temporal**, CPU, UID 10218, limitado a `127.0.0.1:11435`, con clave aleatoria por ensayo. No se publicó en la LAN, no se creó hook de arranque y no queda residente al terminar.
- Una sola solicitud/slot y 4 hilos; contexto 1024, caché adicional limitada a 16 MiB, sin interfaz web, endpoint de slots ni proxy MCP de la interfaz.
- `--poll 0 --poll-batch 0` evita espera activa. El servidor y su clave/log privados se eliminan al finalizar el ensayo; por sí solo el endpoint de inferencia no dispone de herramientas ni control de aparatos.
- Checkpoints de contexto acotados: inicialmente sin ajustes solo se reutilizaban preguntas idénticas; con `-b 256 -ub 16 --ctx-checkpoints 8 --checkpoint-min-step 8` se pudo reutilizar parte de las instrucciones comunes entre preguntas distintas.
- Servidor vigilado durante el ensayo: detención si faltan lecturas, `MemAvailable` cae de 500000 KiB, batería alcanza 40 °C o pasan 100 s. Son guardas experimentales, no protección certificada ni límite de carga.

Código añadido:

- `scripts/warm-server-worker.sh`: servidor temporal y vigilancia.
- `scripts/benchmark-warm-server.sh` y `scripts/benchmark-warm-client.py`: ensayo sintético sin micrófono ni TTS.
- `scripts/local-inference-client.py`: cliente autenticado y acotado, URL fija loopback, sin proxies, sin llamadas externas.
- `scripts/voice-warm-once.sh`: prepara el modelo y realiza una sola captura mediante `voice-once.sh --warm-once`.

## Resultados de caché

| Variante / pregunta | Hasta primer contenido de texto | Respuesta completa |
| --- | ---: | ---: |
| Sin checkpoints ajustados, primera pregunta | 5.11 s | 9.63 s |
| Sin ajustes, repetición exacta | 0.35 s | 5.04 s |
| Sin ajustes, pregunta diferente | 4.93 s | 5.49 s |
| Checkpoints ajustados, primera pregunta | 5.11–5.22 s | 9.80–9.88 s |
| Ajustados, repetición exacta | 0.34 s | 5.12–5.13 s |
| Ajustados, pregunta diferente corta | 1.36–1.39 s | 1.92–1.96 s |
| Cliente nuevo, otra pregunta distinta más larga | 1.51 s | 6.27 s |

La pregunta corta fue el nombre de Doja; la larga, el cielo azul. No son comparaciones de igual longitud de respuesta. Las repeticiones exactas son un caso favorable, no representan toda conversación.

La primera pregunta sigue pagando procesamiento completo del prompt. Con el modelo listo y checkpoints ajustados se reutilizaron 50 tokens de instrucciones en la pregunta diferente corta; antes del ajuste, cero. El servidor tardó unos 1.3 s en quedar sano, sin incluir el procesamiento inicial de instrucciones.

- RSS de servidor en reposo observado: alrededor de 709–716 MiB según ensayo.
- CPU acumulada del servidor durante ventanas de 5 s en reposo: 0–0.01 s. No es una prueba de autonomía ni de reposo prolongado.
- RSS máximo de servidor en la última serie sintética: 727820 KiB, unos 711 MiB.
- Temperatura de batería en esa serie: hasta 26.4 °C. `MemAvailable` mínimo muestreado: 1286136 KiB; sigue aplicando la advertencia de memoria recuperable/mmap en `PRUEBA-MODELO-LOCAL.md`.

## Captura real optimizada

El usuario eligió iniciar otra captura de 8 s. Se preparó el modelo **antes** de avisar que se abría el micrófono, sin TTS de la respuesta de calentamiento.

- Preparación inicial del modelo y sus instrucciones: **8 s**, que no deben ocultarse del coste de activación.
- Pregunta reconocida: «porque se ve estrellas como si cambiarán de colores».
- Captura: 8.06 s; transcripción: 3.18 s, RSS máximo de Vosk 153248 KiB (unos 150 MiB).
- Inferencia: primer contenido 2.735 s; completa 7.165 s. Reutilizó 48 tokens y procesó 27 nuevos.
- **13 s desde terminar de capturar hasta llamar TTS**, frente a 18 s en la prueba anterior, pero con preguntas y respuestas distintas: mejora parcial, no benchmark controlado de voz.
- Duración total de la ejecución, incluida preparación, aviso, captura, respuesta hablada y cierre: **60 s**. Todavía lejos de una experiencia de asistente instantáneo.
- Servidor RSS máximo: 723724 KiB (unos 707 MiB); `MemAvailable` mínimo muestreado durante toda la sesión: 1241736 KiB. En este modo Vosk y el servidor sí coinciden en memoria: no aplica la descarga secuencial de la variante fría.
- Batería máxima: 26.7 °C. Falta prueba térmica sostenida y observación del SoC, no solo batería.
- Al finalizar se comprobó `isRecording=false`, sin listener en 11435 ni directorios temporales de voz/servidor. SSH y Nginx siguieron escuchando en sus puertos originales.
- TTS fue invocado; audibilidad de esta nueva respuesta no fue confirmada aún por el usuario. La respuesta anterior sobre el Sol sí se confirmó audible.

## Bloqueo de calidad detectado

LFM contestó que podía tratarse de luz reflejada en un lago. **No es una explicación válida de la observación habitual descrita** y fue corregida inmediatamente en el diálogo del proyecto. El aparente parpadeo/cambio de color de estrellas suele corresponder a centelleo por turbulencia atmosférica; el contexto exacto puede requerir aclaración.

La mejora de caché **no mejora la exactitud factual del modelo**. No dejarlo como asistente general sin validar respuestas. No ejecutar acciones domésticas a partir de su texto libre.

Siguientes opciones a evaluar dentro de la prioridad local: respuestas basadas en fuentes consultadas por herramientas explícitas de Internet (el modelo seguiría procesando localmente), respuestas deterministas para comandos y consultas concretas, o un candidato local más capaz si los recursos lo permiten. No se implementó búsqueda web todavía. Cualquier consulta externa debe enviar solo lo necesario y tratar el contenido recibido como datos no confiables, nunca como instrucciones.

## Estado de operación

No se dejó escucha continua ni servidor de IA encendido permanentemente. Chat remoto 10000, memoria por conversaciones, timers/recordatorios, reloj/cara y Home Assistant siguen pendientes. El monitor de batería observa, pero la protección adicional de carga 24/7 todavía no está implementada.
