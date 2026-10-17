# Candidato local: LFM2.5 1.2B

Fecha: 2026-10-16. El usuario eligió seguir probando IA local, no inferencia externa.

## Selección e instalación

- Modelo oficial: `LiquidAI/LFM2.5-1.2B-Instruct-GGUF`.
- Archivo: `LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf`, 695755488 bytes (663.5 MiB aproximadamente).
- Revisión: `8ed288026e23958ad9dfa92d53ed773a8eee7125`.
- SHA-256 comparado con metadatos Hugging Face: `bb741ebb106d543e9de114b843a3d3d73d51c74b5801e69da2abde821a0cb3e1`.
- Ruta Android: `/data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf`.
- Licencia **LFM Open License v1.0**, no Apache/MIT; copia conservada junto al modelo y en los artefactos del PC. Tiene condiciones adicionales para uso comercial por encima del umbral de ingresos especificado. Uso previsto aquí: asistente doméstico personal.
- Se utiliza llama.cpp Android ARM64 ya instalado, CPU y UID 10218, sin root para inferencia. No se ejecutan herramientas a partir del texto del modelo.
- Se corrigió la propiedad de los artefactos de inferencia creados en esta sesión a root:root; el archivo tar original preservaba otros UID numéricos. El proceso de IA puede leer/ejecutar, no modificar esos binarios/modelos.
- Se conservan los anteriores Qwen para comparación, **sin cargarlos simultáneamente**. No se aumentó swap ni zram.

## Evaluación sintética breve, no benchmark exhaustivo de calidad

Preguntas de `scripts/model-test-prompts.py`, plantilla ChatML de LFM2 sin prefijo `<think>`. Configuración: contexto 1024, máximo 96 tokens de salida, 4 hilos, CPU, temperatura 0.1, top-k 50, penalización de repetición 1.05, semilla 42.

| Prueba | Resultado revisado | Segundos del proceso |
| --- | --- | ---: |
| Cielo azul | Atribuye a dispersión de luz solar, sin explicación lunar falsa; poco precisa en la primera formulación | 13 |
| Luz azul frente a roja | Indica correctamente que la azul se dispersa más | 9 |
| Nombre del asistente | Responde Doja | 7 |
| Clima actual en China | Reconoce que no tiene datos en tiempo real; falta pedir ciudad | 11 |
| Programar temporizador | No afirma haberlo creado; menciona Internet innecesariamente, lo cual debe resolverse por ruta determinista local | 9 |
| Biodiversidad de Colombia | No acepta sin matices el supuesto de ser primera por número total de especies; respuesta vaga y llama al interlocutor Doja | 15 |

Mejora respecto de los modelos diminutos anteriores, pero **no garantiza exactitud factual, uso de herramientas, memoria o comportamiento de producción**. El nombre propio y las respuestas vagas siguen necesitando evaluación. No se considera que superar estas seis preguntas habilite acciones domésticas libres.

### Rendimiento corto

`llama-bench`, una repetición por caso, prompt 64 tokens y generación 32:

| Hilos | Procesamiento de prompt | Generación |
| --- | ---: | ---: |
| 2 | 7.00 tokens/s | 5.26 tokens/s |
| 4 | 13.88 tokens/s | 9.14 tokens/s |

- Pico máximo de RSS muestreado de proceso en los ensayos de respuestas: 730808 KiB, unos **714 MiB**. El muestreo es cada segundo; no equivale a haber capturado todos los picos del sistema.
- `MemAvailable` mínimo muestreado alrededor de 1.20 GiB en esta serie. Incluye memoria recuperable/caché, incluida parte de archivos mapeados: **no significa 1.20 GiB extra completamente libre después de fijar el modelo en RAM**. Se debe seguir observando presión real, fallos de página y latencia.
- Swap de disco: 908 KiB antes y después de las pruebas. Eso no certifica ausencia de movimiento en zram; no se hizo una traza completa de intercambio.
- Temperatura de batería observada en ensayos sintéticos: unos 27–27.8 °C. No se midió temperatura de cada núcleo/SoC y falta ensayo sostenido; conservar gestión térmica original.

## Primera captura completa con el candidato

Usuario confirmó estar listo para una nueva captura de 8 s. La secuencia completa ejecutó micrófono, conversión, Vosk, LFM y llamada TTS.

- Pregunta reconocida: «porque el sol nunca se apaga».
- Audio: 8.06 s. Transcripción: 2.8 s, pico RSS 149180 KiB (unos 146 MiB).
- Inferencia: 12 s; pico RSS 728880 KiB, unos 712 MiB; `MemAvailable` mínimo muestreado 1294948 KiB.
- **18 s desde fin de captura hasta invocar TTS**, incluyendo conversión/transcripción/inferencia y comprobaciones. Además hay unos 8 s de ventana fija de grabación y el inicio de síntesis. No presentar tokens/s como latencia completa ni como experiencia Alexa ya conseguida.
- Respuesta: «La razón es que el sol es una estrella que emite luz y calor constante, aunque su energía proviene del núcleo». Es demasiado simplificada: falta explicar fusión nuclear y corregir que el Sol no brillará eternamente. Requiere mejora/grounding para preguntas generales; no confundir fluidez con exactitud suficiente.
- Batería: aproximadamente 26.3–26.7 °C en esa prueba. No aumentó el uso reportado de swap de disco.
- Final: `isRecording=false` y no quedaron archivos de audio/directorios temporales de esa ejecución. Audibilidad de **esta** respuesta pendiente de confirmación del usuario; TTS ya había sido confirmado audible en una prueba anterior.

## Guardas del prototipo

`scripts/check-local-model.sh`, usado también por `voice-once.sh`, observa recursos una vez por segundo durante la inferencia y termina su proceso si:

- no puede validar lecturas;
- `MemAvailable` baja de 500000 KiB (unos 488 MiB);
- batería llega a 40 °C;
- transcurren 45 s.

Requiere iniciar la inferencia con al menos 1000000 KiB disponibles y batería por debajo de 38 °C. Son límites conservadores de prueba y una reserva mínima de emergencia, **no una certificación de seguridad ni garantía de conservar siempre el objetivo de 600–800 MiB**. No modifica carga ni protecciones térmicas; puede haber retraso de un ciclo y el sensor de batería no representa todos los puntos calientes. No reproduce una respuesta parcial si se cancela la inferencia.

El monitor de batería permanente sigue siendo solo observación. El límite adicional de carga para uso 24/7 sigue pendiente.

## Siguiente prioridad: bajar latencia antes de añadir funciones

1. Confirmar con el usuario audibilidad y calidad de esta prueba.
2. Comparar servidor de inferencia caliente con caché de prompt frente al proceso por pregunta; medir coste de RAM en reposo y solapamiento con Vosk antes de decidir dejarlo residente. Solo loopback, sin publicar API de inferencia abierta.
3. Sustituir ventana fija de 8 s por captura PCM/VAD adecuada cuando se implemente el puente Android; no iniciar escucha continua de forma implícita.
4. Medir tiempo hasta primer audio y turno completo, repetir con varias preguntas y vigilar temperatura/recursos durante una prueba más larga.
5. Herramientas verificadas para consultas actuales y temporizadores; memoria por conversación con TTL de una hora. Ninguna de estas funciones está disponible aún.

Fuentes:

- https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/tree/8ed288026e23958ad9dfa92d53ed773a8eee7125
- https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct
