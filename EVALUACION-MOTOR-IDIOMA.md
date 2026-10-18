# Motor, representación e idioma: pruebas en el Moto

Fecha: 17 de octubre de 2026. Continuación de `INVESTIGACION-LLAMA-QWEN.md`. **Sin micrófono, TTS, herramientas ni acciones. No se descargaron nuevos pesos ni cambió la app.**

## Resultado hasta los controles de representación/idioma

No se encontró un arreglo aprobado. Se descartaron varias explicaciones simples en los casos probados: historial omitido, tokenización de español distinta de la oficial, ausencia de prefijo de asistente, falta de contexto y modificación de pronombres por nuestro parser. Cambiar algunas rutas numéricas o el muestreo cambia respuestas, pero no produce fiabilidad general.

**El idioma influye, pero no explica todo:** los modelos son multilingües y los errores también aparecen cuando sistema y pregunta están completamente en inglés. No recomendar una traducción automática adicional como solución demostrada.

## Método y reproducibilidad

- Llama.cpp Android b11669, commit `620d2225cc436ab0b001e3200e590e3b286ef9ab`, CPU, cuatro hilos, contexto 2048, mismos GGUF verificados previamente. Un modelo residente a la vez.
- Qwen3 1.7B Q4: microbatch 1, KV F16, muestreo no-thinking anterior (0.7/0.8/20/min-p 0), semilla 42.
- Qwen3.5 2B Q4: microbatch 16, KV F16, sin thinking, muestreo del control de presencia 0 (temperatura 1/top-p 1/top-k 20), semilla 42. No volver a adoptar presencia 2.
- Historial congelado reconstruido de las trazas anteriores: **respuestas reales, incluso erróneas**, no respuestas ideales. Se conserva fuente/hash y reparación reversible del transporte cp850 cuando corresponde.
- Cuatro generaciones por configuración: perro de Natalia, nombre/apodo de Camila y dos controles nuevos de Inés/Dátil y Diego/Pilar/taza. Estos últimos no contienen respuestas anteriores del asistente.
- Comparación chat/raw con **recomputación completa en ambas rutas**, máximo 96 tokens. No desactivar caché en producción; aquí sirve para mantener idéntica condición de cálculo.
- Referencias oficiales fijadas: Qwen3 1.7B `70d244cc86ccca08cf5af4e1e306ecf908b1ad5e`; Qwen3.5 2B `15852e8c16360a2fea060d615a32b45270f8a8fc`.
- El PC únicamente descarga metadatos/código, renderiza/tokeniza referencias y compila. La inferencia se ejecuta exclusivamente en el Moto.

Preparación local en entorno aislado: Jinja2 3.1.6 y tokenizers 0.22.1. Se compara su salida con el motor Jinja propio de llama.cpp y el tokenizador del Moto, no solo dos llamadas a la misma implementación.

Código: `scripts/prepare-inference-audit.py`, `scripts/evaluate-inference-routes.py`, `scripts/compare-inference-routes.py`. Manifiesto de parámetros y hashes: `config/inference_investigation.json`. Referencias y trazas privadas bajo `artifacts/evaluations/`.

## 1. Plantillas, UTF-8 y tokens

**25 historiales por modelo** (23 turnos anteriores y dos controles nuevos): todos los prompts renderizados y todos sus IDs de tokens coinciden exactamente con las referencias oficiales. Los sistemas y mensajes humanos están presentes, los roles se conservan y el prefijo final contiene el bloque vacío de no-thinking esperado.

Los archivos Jinja del GGUF y del repositorio oficial difieren en su código, pero **no en el texto renderizado de estos 25 casos**. Las diferencias encontradas incluyen adaptaciones antiguas de métodos de strings y del recorrido de argumentos de herramientas; no sustituir la plantilla solo porque su hash de archivo es distinto.

Máximo de las suites originales: 499 tokens con Qwen3 y 530 con Qwen3.5, dejando al menos 1422 tokens libres aun reservando 96 de salida. Aumentar contexto no resuelve estos fallos cortos. Esto no certifica el recorte de conversaciones largas del agente de producción, que es otra ruta.

## 2. Chat frente a generación directa

Los mismos cuatro prompts de cada modelo se enviaron a `/v1/chat/completions` y a `/completion`. En esta última se usan los **IDs de tokens auditados**, sin que el servidor vuelva a interpretar roles ni nuestro parser convierta la salida.

**Ocho pares: texto exactamente igual en ambos caminos y mismos ocho primeros candidatos con los mismos logprobs en la primera posición.** En los ocho resultados de chat, nuestro parser tampoco cambió el texto.

Ejemplos conservados en crudo:

- Qwen3: «Mi nombre es Andrómeda y prefiero que me llamés Cami», respondiendo al nombre/apodo humano.
- Qwen3.5: «Mi perro se llama Roco…».
- Qwen3.5, control nuevo sin historial anterior: «Tu nombre es Inés y tu gato se llama Dátil; ambos compartieron mi nombre».
- Qwen3: atribuye a Pilar la taza que regaló a Diego.

No es un defecto introducido exclusivamente por `ConversationAgent`, almacenamiento, voz o presentación. Las dos rutas sí comparten la implementación del modelo y backend; su coincidencia no demuestra corrección universal del motor.

## 3. Controles de CPU, precisión y muestreo

| Control aislado | Resultado observado |
|---|---|
| Quitar repacking, ambos modelos | Mismos textos e IDs de salida en los cuatro casos de cada modelo; mismos logprobs iniciales medidos. Sin arreglo. |
| Qwen3: microbatch 1 a 16 | Mismos textos/IDs y candidatos iniciales en estos cuatro prompts recalculados. Más rápido, pero no corrige semántica. No invalida el antecedente de otro prompt/caché con diferencias numéricas. |
| Qwen3.5: microbatch 16 a 1 | Mismos textos/IDs y candidatos iniciales; bastante más lento. Sin arreglo. |
| Qwen3.5: Flash Attention desactivado | Cambian las cuatro respuestas; siguen equivocadas. Máxima diferencia de logprob entre candidatos iniciales comunes: aproximadamente 0.238–0.327. No es medida de KL ni del vocabulario completo. |
| Qwen3.5: KV F32 con atención auto | Los cuatro textos siguen iguales a F16; sin mejora semántica. El log verifica caché KV de 48 MiB frente a 24 MiB de F16. |
| Qwen3.5: KV F32, atención desactivada | Conserva confusión de dueño/nombre: «Tú me llamas Inés…», «Mi vecina Pilar…». No arregla los errores de la ruta sin Flash Attention. |
| Temperatura 0, resto intacto | Qwen3 sigue apropiándose de Cami y asigna la taza a Pilar. Qwen3.5 mejora perro y taza, pero dice «Tu nombre es Camila y prefiero que me digas Cami». No aprobar por esas mejoras puntuales. |

Los logs de ejecución confirman CPU con NEON/ARM_FMA, sin FP16 vectorial, DOTPROD o KleidiAI anunciados; se conserva el backend Android compatible con este dispositivo. `REPACK=1` en `system_info` indica soporte compilado, no demuestra que todos los tensores se repaqueten ni que `--no-repack` cambie el kernel usado para estas matrices.

Las diferencias entre atención auto/desactivada justifican investigar numeración, pero **no establecen cuál de las dos rutas es más correcta**. La variante desactivada no recupera calidad. Ninguna se adoptó por esas diferencias.

## 4. Propuesta del foro: bloques vacíos en el historial

Se probaron en ambos modelos prompts crudos que conservan `<think>\n\n</think>\n\n` también en los encabezados de respuestas históricas. No cambian palabras del usuario/asistente ni se agregan hechos. El prefijo de generación final no se modifica.

- Natalia: añade 12 tokens; Camila: cuatro tokens.
- Los dos controles de un solo turno son **idénticos** antes/después y conservan exactamente las respuestas.
- Qwen3 mantiene los mismos errores.
- Qwen3.5 cambia la respuesta sobre Roco, pero para Camila dice «Eres Camila y prefiero que me llames Cami».

**No adoptar esa representación como corrección semántica.** La auditoría de prefijos sí encuentra en 19 transiciones por modelo que el bloque vacío anterior desaparece al convertirse en historial: la coincidencia llega hasta cuatro tokens antes del final del prompt previo. Puede penalizar caché, especialmente en un modelo recurrente; no equivale a borrar todos los mensajes ni a perder todo el prefijo. El contraste semántico se hizo sin caché para no confundir ambos efectos.

## 5. Control bilingüe solicitado por el usuario

`tests/language_control_cases.json`: dos tareas nuevas (Nora/Nori/gato Tilo y Martín/Eva/taza), cruzando independientemente idioma del sistema (es/en) e idioma de la pregunta (es/en). **Ocho generaciones por modelo, 16 en total**, con nombres/hechos equivalentes y sin respuestas ideales en el historial.

Otros 16 prompts/tokenizaciones coinciden con sus referencias oficiales. Son **66 prompts únicos auditados** junto con los 50 anteriores; las repeticiones por perfil no cuentan como ejemplos nuevos.

| Modelo | Hallazgo |
|---|---|
| Qwen3 1.7B | Sistema inglés mejora la tarea de Nora incluso con pregunta española. Sin embargo, íntegramente en inglés responde «The mug is not yours; it is Eva's», pese a que Eva la regaló al humano. También hay atribuciones erróneas de vecina en las otras condiciones. |
| Qwen3.5 2B | Continúa confundiendo identidades también íntegramente en inglés: «My name is Nori… I'll always refer to you as Nora» y «You are the neighbor Eva…». Una combinación cruzada mejora la taza, sin mejora consistente global. |

Por tanto, no afirmar que son modelos solo ingleses ni que el problema sea únicamente traducir palabras. Tampoco concluir que español e inglés tengan la misma calidad general: son solo dos tareas, una semilla y muestreo/configuración concretos. Los ejemplos no son un benchmark lingüístico representativo.

## Límites y cierre de la investigación

- Antes del contraste con el binario genérico: **15 perfiles completos, 76 generaciones**, más tres resultados de una tanda interrumpida conservada aparte. No sumar auditorías repetidas como casos independientes. Mínimo MemAvailable observado 1363940 KiB (~1.30 GiB), máximo RSS 1575412 KiB (~1.50 GiB), batería máxima 30.3 °C. Todas las tandas completas cerraron con `reason=completed`.
- **122 pruebas Python de soporte** pasan en este punto. No son pruebas de comprensión del modelo ni sustituyen revisión semántica.
- Estos tiempos son API con recomputación deliberada, no latencia doméstica caliente ni medición ASR+LLM+TTS. Hubo respuestas de diagnóstico superiores a 30 s; **no se amplió el contrato de voz**.
- Una tanda KV F32/sin Flash Attention quedó interrumpida al llegar la pregunta del idioma: se conservaron tres resultados parciales, se verificó cierre del servidor y después se repitió la tanda completa. No tratar el archivo incompleto como aceptación ni mezclarlo silenciosamente con la muestra completa.
- El comparador omite archivos incompletos de sus comparaciones agregadas y los etiqueta explícitamente. Los textos originales siguen guardados.
- La compilación genérica de referencia se registra por separado al finalizar; compilar no equivale a haberla ejecutado ni a validar su calidad.
- No hay corrección habilitada para voz, acciones, escucha permanente, carga o swap. App 0.3 intacta; sin commits/pushes.

## Binario genérico de referencia: diseño del contraste

Se prepara una compilación privada del **mismo commit** que b11669, en directorio separado. No se reemplaza `/data/local/doha-inference/llama-b11669`.

- NDK instalado 27.1.12297006 / Clang 18.0.2, Android API 29, arm64-v8a, librerías C++ estáticas.
- Parche local mínimo de selección, `config/reference_cpu_source.patch`: selecciona el fallback `GGML_CPU_GENERIC` existente, sin cambiar pesos, arquitectura, plantilla ni fórmulas del modelo.
- Para `ggml-cpu` y `ggml-base`: desactivar macros de NEON/FMA, vectorización automática y contracción FMA. Desactivar LLAMAFILE, repacking, KleidiAI, OpenMP y backends GPU. No desactivar globalmente NEON en dependencias ajenas al cálculo, como miniaudio.
- La primera tentativa de compilación falló por aplicar esos macros globalmente a miniaudio; **no fue un fallo de inferencia ni se instaló ese intento**. Se acotaron las opciones a los dos targets matemáticos.
- El build upstream aprovisiona assets web precompilados, aunque `LLAMA_BUILD_UI=OFF`; cayó de su revisión `b1` a `latest` y verificó el archivo. Esos assets no se sirven (`--no-webui`). El hash final del binario identifica este artefacto; no afirmar reproducibilidad bit a bit solo por fijar el commit.
- Este contraste también cambia compilador/enlace frente al binario distribuido (Clang 21). No atribuir causalmente una diferencia a una sola optimización sin control adicional con compilador equivalente.
- Prueba planificada: los dos controles nuevos de un solo turno por modelo, mismos tokens auditados, muestreo y pesos, con **32 tokens máximos de salida**, sin warmup sintético para acotar el coste escalar. Comparar candidatos iniciales y prefijo generado; una salida cortada no constituye respuesta semántica aprobada.
- Timeout diagnóstico por petición 180 s y worker global 420 s. **No son nuevos presupuestos para voz**. Si no completa dentro del límite, registrar el resultado incompleto y cerrar, sin ampliar indefinidamente la prueba.

### Ejecución real completada en el Moto

Binario de **187581192 bytes**, SHA-256 `cce7658b7fba22b437993e87868ac534db9e9c5d43305aedd22365f9ac47316b`, verificado en PC y Moto. Instalado solo en `/data/local/doha-inference/llama-reference-generic/llama-server`. La etiqueta de build es 1 por el clon superficial, pero el commit coincide con b11669; la selección genérica es una modificación privada registrada, no un release upstream nuevo.

Los logs del backend genérico ya no anuncian NEON, ARM_FMA, LLAMAFILE o REPACK. Ambas tandas completaron sus dos peticiones y devolvieron fin de secuencia (**no cortes al límite de 32 tokens**):

| Modelo / control nuevo | Tiempo | Resultado |
|---|---:|---|
| Qwen3 / Inés y gato Dátil | 128.201 s | Conserva correctamente nombre, dueño y gato; mismo texto/IDs que el binario original. |
| Qwen3 / taza de Diego | 118.301 s | «La taza es de Pilar, ya que ella te la regaló»: vuelve a confundir la transferencia del regalo. |
| Qwen3.5 / Inés y gato Dátil | 129.422 s | «Tú me llamas Inés… en el universo de esta conversación»: vuelve a invertir la perspectiva. |
| Qwen3.5 / taza de Diego | 124.671 s | Identifica la taza como del humano y a Pilar como su vecina, con gramática deficiente. Mejora puntual, no solución global. |

Entre los candidatos iniciales comunes medidos, las diferencias máximas de logprob frente al binario original son 0.425/1.115 para Qwen3 y 0.243/0.366 para Qwen3.5. **No son KL ni una comparación completa de logits**. El caso de Inés con Qwen3 produce idéntico texto pese a que esos números difieren: igualdad textual tampoco prueba igualdad numérica.

Este contraste reduce la hipótesis de un fallo causado **exclusivamente** por los kernels ARM optimizados. No certifica toda la implementación compartida de llama.cpp, la conversión GGUF o la capacidad de los modelos. Tampoco permite elegir cuál de los dos motores es universalmente más correcto. No instalar un modelo mayor por inferencia de este resultado.

### Estado final

- **17 perfiles completos, 80 generaciones**, más una traza interrumpida de tres generaciones separada. **66 prompts únicos** comparados exactamente con plantilla/tokenizador oficiales. **122 tests Python de soporte** pasan.
- Mínimo MemAvailable de las tandas completas: **1363940 KiB (~1.30 GiB)**; máximo RSS: **1575412 KiB (~1.50 GiB)**; batería máxima **30.3 °C**. No sumar RSS a total menos disponible; hay pesos mapeados recuperables. No es prueba conjunta de ASR/LLM/TTS ni certificación térmica 24/7.
- Sin servicios de voz/alarma ni puertos 11435/11436, sin temporales de workers al cerrar. SSH 2222 y web 8080 continúan escuchando. Última muestra de reposo: batería **29.6 °C**, MemAvailable **1787248 KiB (~1.70 GiB)**.
- El binario de referencia queda instalado **apagado, solo para diagnóstico**; `pilot route_qwen17_generic` rechaza con código 2. No se sustituyó motor original ni APK 0.3, cambió HOME, activó micrófono, programó una acción, modificó swap/carga o publicó nada.
- **Ninguna configuración aprobada para voz.** No aplicar automáticamente inglés, bloques de thinking, KV F32, microbatch 1, Flash Attention desactivado o backend genérico como arreglo. No reparar respuestas erróneas con frases preparadas.
