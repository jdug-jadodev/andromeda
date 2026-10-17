# Evaluación controlada: capacidad antes de ampliar integración

Fecha: 17 de octubre de 2026. Modelo único: Qwen3 1.7B Q4_K_M ya instalado y verificado; no se descargó otro. CPU, cuatro hilos, llama.cpp con plantilla nativa/Jinja, contexto 2048, temperatura 0, semilla 42, máximo 64 tokens por respuesta. Sin Vosk, micrófono, TTS, Internet externo ni ejecutor de acciones.

## Método

`tests/controlled_model_cases.json` contiene doce casos públicos congelados; `scripts/evaluate-model-controlled.py` envía mensajes directamente a la API nativa, sin `ConversationAgent`, su memoria ni sus instrucciones largas. Se añade el mismo esquema de una sola herramienta únicamente en las fases correspondientes. Resultados conocidos se inyectan, no provienen de timers reales.

SHA-256 de las definiciones: `30c2564d1e113ec3ba3cb0f4fdfd419f30c08958aa87cd0407b5cfe91dc7ef4b`.

No se cambiaron instrucciones entre casos. Las respuestas de historial son fijadas por la prueba para aislar comprensión de un contexto conocido, no se confunden con conversación humana real. Una sola ejecución por caso: no mide robustez estadística ni garantiza repetibilidad con otros contextos.

## Resultados revisados

| Caso | Tiempo de API | Resultado |
|---|---:|---|
| Identidad propia, sin herramientas | 9.996 s | «Me llamo Andrómeda». Correcto. |
| Nombre del usuario, sin herramientas | 4.167 s | «Lucía». Correcto. |
| Referencia al gato del usuario | 5.157 s | Conserva «Nube», pero dice «Mi gato…»: perspectiva incorrecta. |
| Nombre tras cambio de tema | 7.476 s | Conserva «Lucía», pero dice «Tengo el nombre…»: perspectiva incorrecta. |
| **Mismo historial de nombre con una herramienta** | 21.965 s | **«Me llamo Andrómeda». Incorrecto.** |
| Crear dos minutos | 7.828 s | No llama herramienta; exige segundos aunque puede convertirse la unidad. Incorrecto. |
| Crear treinta segundos | 6.277 s | Solicita `create_timer(seconds=30)`. Argumentos exactos; no se ejecutó. |
| Resultado simulado exitoso | 9.854 s | Describe activación de dos minutos de acuerdo con el resultado inyectado. |
| Resultado simulado fallido | 6.460 s | Reconoce que no se creó. |
| Negación | 10.810 s | Sin llamada (correcto), explicación falsa sobre imposibilidad de usar minutos. No aprobar calidad. |
| Relato de una orden pasada | 6.855 s | Sin llamada (correcto), repite parte del mensaje como respuesta. Calidad insuficiente. |
| Falta duración | 5.605 s | Pide duración sin generar una acción; adecuado, aunque innecesariamente restrictivo a segundos. |

El evaluador mecánico detectó dos fallos explícitos: nombre con herramienta y dos minutos. **No interpretar los otros diez como diez éxitos semánticos**: la revisión adicional encontró errores de perspectiva y explicaciones incorrectas. El evaluador etiqueta expresamente revisión semántica pendiente y nunca aprueba voz/producción.

Todos los tiempos quedaron bajo el presupuesto orientativo de 22 s de cálculo. No incluyen fin de voz, reconocimiento, puente, síntesis, ni dos llamadas en un mismo turno: **no demuestran el techo de 30 s de extremo a extremo**. El caso con catálogo pagó un cambio de prefijo; distinguirlo de solicitudes con caché ya reutilizada.

Recursos de esta serie: mínimo MemAvailable 1281140 KiB (~1.22 GiB, incluye caché recuperable), RSS máximo servidor 1393868 KiB (~1.33 GiB), batería máxima 25.2 °C. El trabajador cerró y limpió temporales. No se programó ninguna alarma, cambió HOME ni instaló interfaz.

## Conclusión y decisión

1. El fallo no depende exclusivamente del almacén conversacional o de la captura: se reproduce sin ellos. La presencia/formato del catálogo afecta la respuesta del mismo historial. Eso no distingue todavía entre sensibilidad del modelo, plantilla y representación del contrato.
2. Contexto mínimo y caché permiten respuestas mucho más cortas que los ensayos anteriores. El rendimiento con pocas herramientas no es por sí solo el bloqueo principal de esta serie.
3. La conversión temporal y las referencias personales son frágiles. Validar JSON no basta: treinta segundos funciona, dos minutos no. No aprobar acciones autónomas ni conversación doméstica general.
4. Próximo contraste acotado: inspeccionar el prompt nativo realmente renderizado con/sin catálogo y aislar representación de argumentos (duración natural con cantidad/unidad frente a obligar cálculo al modelo). Sin añadir respuestas por pregunta ni ajustar a cada caso del examen. Antes de cambiar el contrato, definir casos de control no usados para ajustes.
5. No continuar a interfaz, escucha permanente o más integraciones por estos resultados. Modelo sigue siendo candidato diagnóstico.

Reproducción sin micrófono:

```powershell
adb shell su -c '/data/local/doha-voice/start-andromeda-ai.sh test controlled'
```

Código de salida 1 indica fallos mecánicos registrados, no necesariamente fallo de instalación. El supervisor conserva guardas de memoria/temperatura y duración. Las peticiones usan únicamente loopback; no hay búsqueda web ni datos personales reales en las frases.

58 pruebas Python de infraestructura/evaluadores pasaron; eso no cambia el resultado fallido del modelo.

## Contrastes posteriores: contrato, estado de inferencia y microbatch

Se continuó con el mismo modelo/pesos y parámetros, sin instalaciones de modelos, cambios de instrucciones entre casos, micrófono, Internet externo o acciones. Todos los perfiles nuevos son **solo de pruebas**, también bloqueados en el coordinador central para evitar saltarse la puerta del lanzador de chat.

### Prompt realmente renderizado

`evaluate-contract-ablation.py` consultó `/apply-template` con/sin herramienta. Roles en los tres casos: `system,user,assistant,user,assistant`. El nombre humano y la pregunta final permanecen en el prompt. La plantilla añade el bloque nativo `# Tools`, esquemas y formato de llamadas; no se encontró que borrara o reordenara el historial de este caso.

Esto reduce la probabilidad de un error de montaje de mensajes aquí, pero no demuestra corrección completa de la plantilla/servidor.

### Contrato cantidad/unidad: no adoptado

Se congelaron nuevos casos (`contract_ablation_cases.json`, SHA-256 `03ab17c95d7ef935ea5ea0d2e4c430b0dd8fddd1ec9915e8acdae32e42d926a3`) y se compararon segundos frente a cantidad/unidad. La variante cantidad/unidad devolvió `quantity=180, unit=minutes` para **tres minutos**: parámetros bien formados, intención incorrecta (10800 s). También falló el nombre humano. No se integró al agente/Android.

El conversor exacto `backend/durations.py` está probado, pero no puede corregir una extracción incorrecta del modelo. Se conserva como soporte experimental de reproducción, no como contrato aprobado.

### Reproducción dependiente del cálculo/reutilización

`evaluate-cache-isolation.py` repite las cuatro consultas sin herramientas anteriores y después el **mismo prompt** de nombre con herramienta. Hash del prompt renderizado: `f462b8b5bb66a91bf3f798b240180839cce293bf6335c2d212139f0811b0a194`. Temperatura 0, semilla 42, mismo modelo.

| Perfil | Caché parcial (65 tokens) | Recalcular completo | Repetir contexto completo ya calculado |
|---|---|---|---|
| KV f16, microbatch 16 | «Me llamo Andrómeda», incorrecto | «Lucía», correcto | «Lucía», correcto |
| KV f32, microbatch 16 | Mismo error | Correcto | Correcto |
| Sin RAM cache/checkpoints adicionales, microbatch 16 | Mismo error | Correcto | Correcto |
| **Microbatch 1, KV f16** | **«Te llamas Lucía»** | **«Te llamas Lucía»**, ambas repeticiones | **«Te llamas Lucía»** |

Con microbatch 1 las cuatro repeticiones coincidieron. No se puede atribuir todo a incapacidad del modelo ni a mensajes borrados: la trayectoria de cálculo influye. **No afirmar todavía un bug específico de llama.cpp o una corrección universal:** cambiar microbatch modifica rutas numéricas; faltan controles de logits/otra implementación para establecer causa exacta.

El ajuste es más lento en prefill: caso completo sin caché ~36 s, parcial ~28.5 s, repetición idéntica favorable ~1.4 s. No presentar esa repetición como latencia de preguntas nuevas. KV f32 no solucionó el problema y aumentó RSS a 1614996 KiB; no adoptarlo como solución.

### Regresión congelada con microbatch 1

- Qwen3 1.7B: mejora el nombre con herramientas, pero sigue fallando dos minutos con el contrato de segundos. Persisten errores de perspectiva y respuestas poco útiles; no aprobado.
- Qwen3 0.6B: 2.1–11.8 s en los doce casos, pero falla referencias y **solicita 30 s cuando no se dio duración**. Excluir como candidato actual para acciones domésticas, aunque sea rápido. RSS máximo 682004 KiB; batería máxima 29.2 °C en esa serie. No se ejecutó la solicitud incorrecta.

### Nuevo candidato experimental: duración literal verificada

En lugar de pedir al modelo aritmética o cantidad/unidad, el contrato `duration_text` exige copiar literalmente la duración. `backend/span_duration.py` comprueba que el fragmento exista una sola vez en el mensaje humano, que no omita otras duraciones reconocidas, que tenga sintaxis admitida y que el resultado sea 1–86400 segundos enteros. Después convierte por código. No interpreta la intención de crear/cancelar ni decide qué pregunta contestar.

Así «180 minutos» no puede aceptarse como copia de «tres minutos», ni un «30 segundos» inventado de una orden sin duración. **No es una protección completa de intención**: no resuelve negaciones/relatos, referencias a turnos anteriores, fechas ni toda expresión natural (no están soportadas todas las fracciones o centenas en palabras). Sigue siendo un candidato, no un ejecutor habilitado.

`span_contract_cases.json` fijó nuevos ejemplos (SHA-256 `c0423800e50b37b0582bd6cc5813058fcd1de9264cdbd5c110c046cdff2f7c2a`): nombre Mateo, cinco minutos, dos horas, setenta segundos, negación y falta de duración. Los seis pasaron chequeos mecánicos; respuestas revisadas en esa serie fueron coherentes y no hubo acción en negación/falta de duración. Preparación fría 34.106 s; cinco peticiones siguientes **4.691–7.238 s** de API. No son tiempos de voz ni garantía de fiabilidad fuera de esta muestra.

La regresión de los doce casos originales con este contrato corrigió la conversión de dos minutos y el nombre con herramienta, pero reveló problemas adicionales:

- Perspectiva: «Mi gato…» / «Tengo el nombre…» para datos del usuario.
- Tras un fallo de guardado inventó una recomendación sobre corregir duración; no conoce esa causa.
- En relato de orden pasada imprimió `{"name":"create_timer",...}` como texto en lugar de respuesta. No hubo `tool_calls` ni ejecución, pero **eso no es una respuesta segura/coherente aprobada**.

Se reforzaron evaluador y parser para rechazar pseudo-llamadas JSON/XML impresas como conversación; nunca convertirlas en acciones ni dar por pasado ese caso por ausencia de llamadas nativas. La salida original marcó cero fallos mecánicos antes de esta mejora: esa señal no supera la revisión semántica. No declarar 12/12 funcional.

Los perfiles `span_tokenwise` / `span_regression` siguen sin registrar este contrato en el ejecutor doméstico. La mejora prometedora es **microbatch 1 + contrato literal + contexto pequeño**, no un sistema terminado. Antes de ampliar núcleo/voz: corregir y validar conversación completa, relatos y respuestas basadas en resultados; luego medir una operación Android real con presupuesto global.

Última regresión: RSS servidor 1549728 KiB, MemAvailable mínimo 1413652 KiB (incluye recuperable), batería máxima 29.0 °C. Ningún ensayo valida todavía SoC/carga sostenida de voz+IA ni operación 24/7.

Cierre de este bloque: **72 pruebas Python pasan**, módulos compilan y scripts Android pasan `sh -n`. Se verificó que `pilot qwen17` rechaza arrancar (salida 2) desde el coordinador central. App sin servicios de voz/alarma, preferencias de timers vacías, sin puertos 11435/11436 ni directorios temporales del worker. Lectura posterior: batería 28.5 °C, MemAvailable 1690736 KiB (~1.61 GiB); no es un máximo de carga.

**Evaluación posterior más exigente:** `EVALUACION-CONVERSACIONAL.md` conserva respuestas generadas reales en el historial. La mejora de seis casos aislados no se sostuvo como calidad general: negación e identidad fallan también en referencia de muestreo oficial. No pasar a acciones reales por los resultados aislados; 82 pruebas de soporte no aprueban el modelo.
