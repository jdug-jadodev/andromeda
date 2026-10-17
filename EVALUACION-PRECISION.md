# Conversación sin herramientas y control de precisión

## Objetivo y alcance

Siguiente paso tras confirmar voz → cara: evaluar una conversación corta sin ejecutor antes de conectar respuestas del modelo a la voz. No se amplía permiso de acciones ni escucha permanente. Primero se contrasta el catálogo; después, una sola variante de precisión del mismo modelo. No son respuestas preparadas por pregunta.

## Conversación sin catálogo: Qwen3 1.7B Q4_K_M

- Fixture nueva `tests/conversation_only_cases.json`, SHA-256 `d3cb1a9816ae57d1518dd9ecf5295b703b7737d6bb8c3b24b9046180b71ca63b`.
- Dos conversaciones independientes, 15 intervenciones: Natalia/perro Roco/preferencia Nati/cambio de tema; Óscar/sopa/relato/acción no disponible/clima sin Internet/recuerdo.
- Instrucción única para toda la prueba: asistente de conversación en español, una o dos frases, sin herramientas/Internet/dispositivos ni afirmaciones de ejecución. No se añade catálogo, ni siquiera vacío. Es un alcance funcional menor, no un modo de arreglar la negación para acciones reales.
- Historial generado real. No se reemplazan fallos por respuestas ideales ni se reintenta hasta acertar. Una salida inválida/truncada aborta esa conversación en lugar de inventar reparación; errores semánticos conservan su texto para observar consecuencias posteriores.
- Mismo Moto y motor llama.cpp b11669; contexto 2048, 4 hilos, microbatch 1, KV f16, thinking apagado, muestreo Qwen publicado sin thinking (0.7/0.8/20/0), semilla 42, máximo 96 tokens.
- Preparación explícita con «Hola» y un token, independiente del historial evaluado: **21.350 s**. Las 15 peticiones siguientes: **4.637–20.716 s**. No incluyen ASR/TTS ni prueban 30 s con voz.
- Prueba sin micrófono, ejecutor, ASR ni acceso externo del agente. La app Android puede permanecer mostrando el reloj, pero no se carga Vosk ni se activa voz.

### Revisión semántica Q4

La ausencia de herramientas **no resolvió** la calidad:

- Se presenta correctamente como Andrómeda, pero vuelve a preguntar un nombre ya dicho.
- Al preguntar el nombre de Natalia, lo omite y solicita elegir otro.
- Recuerda Roco, pero pregunta si quiere darle un nombre pese a ya nombrarlo.
- Ante «prefiero que me llames Nati», responde **«Me llamaré Nati»**: perspectiva equivocada.
- Explica la lluvia como viento que rompe nubes: **explicación incorrecta**.
- Recuerdo conjunto posterior contiene Nati/Roco, pero añade de nuevo la pregunta contradictoria de nombrar al perro. Acertar palabras no certifica coherencia.
- «¿Recuerdas mi nombre?» de Óscar recibe «sí» sin decirlo. El chequeo exige el nombre explícito; **por sí solo no demuestra olvido**, porque la pregunta admite sí/no. No usar ese fallo mecánico como prueba semántica independiente. Mantener el caso congelado para comparar; exigir recuerdo directo en nuevos controles antes de aprobar.
- Consejo de sopa ofrece zumo de limón/aguacate como neutralización de sal: consejo poco fiable, no una respuesta aprobada por tener vocabulario culinario.
- Relato de orden: repite el mensaje en primera persona, confunde perspectiva.
- Petición de timer no disponible: emite metatexto en inglés sobre cómo responder y promete ayuda, sin aclarar bien que no puede crear timers en esta sesión. No se ejecutó nada.
- Sí reconoce después que no se creó un temporizador, admite no tener clima actual y recuerda la sopa correctamente.

Resumen automático: tres señales fallidas; la revisión anterior detecta más problemas. **Configuración rechazada para conversación por voz**, aunque cada llamada caliente esté debajo de 22 s.

Recursos Q4: RSS servidor máximo 1384132 KiB (~1.32 GiB), MemAvailable mínimo 1231544 KiB (~1.17 GiB, incluye recuperable), batería máxima 26.5 °C. No valida trabajo conjunto de voz/IA.

## Hipótesis siguiente: Q8_0, no otro tamaño de modelo

Conservar sistema, fixture, historial real, parámetros y motor; cambiar solo el archivo cuantizado a Q8_0 del mismo repositorio/revisión que Q4. Esto comprueba sensibilidad a precisión **en esta configuración**; no establece por sí solo causa universal entre modelo, cuantizador y kernels del motor. No se suma una serie de descargas de candidatos sin hipótesis.

Metadatos congelados: `config/model_precision_control.json`.

- Repositorio `bartowski/Qwen_Qwen3-1.7B-GGUF`, revisión `dcb19155b962dbb6389f4691a982043a8e651022`, base `Qwen/Qwen3-1.7B`.
- Archivo `Qwen_Qwen3-1.7B-Q8_0.gguf`, **2165039520 bytes** (~2.02 GiB), SHA-256 publicado `74bb7c53538ab2cc81b93f0c64da14a503159de68cff3c6770428d3850479db3`.
- ~842 MiB más de archivo que Q4, **no una predicción de RAM incremental**. La prueba medirá recursos reales; no cambiar swap, Android ni cachés del sistema para hacerla caber.
- Archivo descargado/verificado en el PC antes de transferir; verificación también en el Moto antes de cargar. Infiere únicamente el Moto, un solo modelo residente a la vez, sin ASR/micrófono.
- Perfil `conversation_only_q8` solo admite modo `test` desde el coordinador central. Mantiene guardas de recursos/duración; no se activa como modelo de voz por quedar instalado.

### Resultado Q8: mejora parcial, no aprobación

Descarga y tamaño/SHA-256 verificados en PC y Moto. Solo se cargó Q8 tras cerrar Q4; misma fixture y parámetros.

- Explica correctamente condensación/caída de gotas, a diferencia de Q4; acepta Nati inicialmente sin cambiar su propia identidad.
- En el recuerdo conjunto dice **«Puedes llamarme Nati»**, apropiándose del nombre del usuario. Que contenga Nati/Roco no convierte ese turno en correcto.
- Preguntado por el nombre de Óscar responde «soy Andrómeda»: confunde interlocutores, no una mera omisión de nombre ante pregunta sí/no.
- Promete estar listo para comenzar un temporizador aunque no hay herramientas; no aclara la limitación de esta sesión. La pregunta posterior sí recibe que no se creó ninguno.
- El consejo de neutralizar la salinidad con zumo de limón/manzana sigue siendo poco fiable.
- Admite no disponer de clima actual y recuerda la sopa. No emite el metatexto inglés que apareció en Q4 en esta muestra, pero no demuestra eliminación general del problema.

El aumento de precisión cambia respuestas y mejora algunos ejemplos, pero **no soluciona conversación/identidad/veracidad de capacidades**. No atribuir todos los fallos a cuatro bits ni elevar Q8 a modelo activo de voz.

### Control adicional con LFM ya instalado

Sin otra descarga ni modificaciones de preguntas, se ejecutó el perfil `conversation_only_lfm` previsto desde el inicio del evaluador. Mismo sistema/contexto/microbatch; muestreo greedy explícito (no afirmar equivalencia de muestreo entre familias).

Es más rápido y reconoce que no puede crear temporizadores, pero al preguntar por Natalia dice **«Me llamo Natalia y soy tu asistente»**. Tras cambio de tema se atribuye tanto Nati como el perro. Ante Óscar responde su identidad propia; ante un relato contesta que no puede acceder a esa información, sin comprender la finalidad conversacional. Rechazado también para la entrega de conversación fiable. Una sola señal mecánica fallida no equivale a catorce respuestas semánticamente correctas.

### Recursos y tiempos comparados

| Configuración | Preparación fría | Peticiones de prueba (mín–mediana–máx) | RSS máximo servidor | MemAvailable mínimo | Batería máx. |
|---|---:|---:|---:|---:|---:|
| Qwen 1.7B Q4 | 21.350 s | 4.637–9.314–20.716 s | 1384132 KiB (~1.32 GiB) | 1231544 KiB | 26.5 °C |
| Qwen 1.7B Q8 | 43.414 s | 9.064–13.309–21.710 s | 2007616 KiB (~1.91 GiB) | 1363976 KiB | 28.0 °C |
| LFM 1.2B Q4 | 10.818 s | 2.712–4.944–10.713 s | 744572 KiB (~727 MiB) | 1790772 KiB | 28.6 °C |

Cada uno completó 15 turnos. Las conversaciones ya generadas difieren al variar el modelo, por lo que también difieren tokens y contexto posterior: no interpretar estas medianas como benchmark pareado con idéntica salida. La primera petición real de cada conversación LFM reevalúa el prefijo completo aun después de preparar; la tabla incluye ese coste. No mezclar arranque con turno ni ocultarlo.

MemAvailable incluye páginas recuperables y depende de caché/zram/estado de Android entre ensayos. Su mínimo mayor con Q8 **no significa que Q8 use menos RAM que Q4**. RSS tampoco es memoria privada/pinned; no se probó LLM+ASR+TTS. Los tiempos son API, no voz ni certificación de 30 s de extremo a extremo.

## Decisión y siguiente hipótesis

No conectar estas configuraciones a respuestas de voz. Se preserva la app 0.3 de activación/ASR, con su éxito humano ya confirmado, sin regresarla ni sustituirla por respuestas prefabricadas. Q8 queda instalado solo para reproducción y no se descargan otras cuantizaciones en este bloque.

Se revisó, **sin descargar pesos**, Qwen3.5-2B como candidato posterior: arquitectura híbrida distinta y GGUF Q4 de tamaño comparable a nuestro Qwen3 Q4 (1280835840 bytes, comunidad Unsloth). La ficha oficial informa mejora multilingüe agregada MMMLU 56.9 vs 46.7 de Qwen3-1.7B en no-thinking, pero **IFEval no-thinking es inferior (61.2 vs 68.2)**. No presentarlo como superior en todo ni como solución garantizada; métricas del fabricante no miden conversación española en este Moto.

Fuentes revisadas:

- https://huggingface.co/Qwen/Qwen3.5-2B
- https://huggingface.co/unsloth/Qwen3.5-2B-GGUF (revisión consultada `f6d5376be1edb4d416d56da11e5397a961aca8ae`; base Apache-2.0; Q4 SHA-256 publicado `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`).

Antes de adoptarlo: verificar compatibilidad/plantilla/estado recurrente del motor, fijar muestreo oficial sin thinking, ejecutar esta fixture intacta y controles nuevos de recuerdo directo, pronombres, negación y capacidades; después validar tiempos conjuntos y permisos de una sesión conversacional solo lectura. Alternativamente, una segunda implementación del mismo modelo serviría para aislar motor frente a pesos: **las pruebas realizadas aún no establecen causalidad universal**. No cambiar a nube ni a otro servidor por estos resultados.

Cierre de este bloque: **96 pruebas Python de soporte pasan**; perfil `pilot conversation_only_q8` rechazado con código 2. Sin servicios VoiceService/RingService, sin listeners 11435/11436 ni temporales del worker. Lectura de reposo posterior: batería 28.7 °C y MemAvailable 1800428 KiB (~1.72 GiB). No se modificaron Android, swap, batería ni launcher; no se grabó audio ni ejecutaron acciones. No hay nueva conversación de voz que pedirle al usuario probar todavía.

**Petición posterior de priorizar contexto/identidad sin aumentar modelo:** ver `EVALUACION-IDENTIDAD.md`. Mismo Qwen Q4, dos reglas generales adicionales y controles nuevos frente a base; no mejoran consistentemente y aumentan la mediana de los ocho controles. Recalcular íntegro dos historiales produce las mismas respuestas erróneas y añade latencia. Variante rechazada, no se cambia de modelo ni se habilita voz; 100 pruebas Python de soporte pasan.

## Reproducir

```powershell
adb shell su -c '/data/local/doha-voice/start-andromeda-ai.sh test conversation_only'
adb shell su -c '/data/local/doha-voice/start-andromeda-ai.sh test conversation_only_q8'
```

No ejecutarlos en paralelo ni sobre una sesión de micrófono. Traces públicas ficticias en `artifacts/evaluations/`, ignoradas por Git. Estos perfiles no son la voz ya instalada, que sigue ofreciendo únicamente activación/ASR.
