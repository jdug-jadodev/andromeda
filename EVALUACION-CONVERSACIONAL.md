# Conversación completa: resultados y decisión

Fecha: 17 de octubre de 2026. **No aprobado para voz o acciones domésticas.**

Esta serie sucede a `EVALUACION-CONTROLADA.md`: ya no se insertan respuestas ideales del asistente en cada caso. El modelo genera cada respuesta y su salida final pasa al historial del turno siguiente. Se usan dos conversaciones nuevas e independientes. Todo es texto público ficticio; no hay grabación, TTS, Internet desde el agente ni ejecución Android.

## Método congelado

- `tests/sequential_conversation_cases.json`: 14 intervenciones en dos conversaciones. SHA-256 `dc3687e0aac8c123d6e28371b3af947ca83407e1532ddac4e4953cb71eefd602`.
- Conversación A: Valeria/color azul, recuerdo, cambio de tema, creación de seis minutos, recuerdo de duración.
- Conversación B: Andrés, ausencia de mezcla entre conversaciones, negación, relato de orden pasada, falta de duración, ocho minutos con guardado fallido y recuerdo del fallo.
- Una herramienta con argumento `duration_text`, validado contra el mensaje humano mediante `span_duration.py`.
- Modelo único residente en cada ensayo, contexto 2048, cuatro hilos, microbatch 1, KV f16. Se mantiene un solo modelo a la vez.
- El simulador devuelve éxito o fallo fijado por la fixture; **no programa timers ni demuestra persistencia real**. Una solicitud inesperada también falla la evaluación aunque el simulador la bloquee.
- `scripts/evaluate-sequential-conversations.py` conserva salidas reales, correlaciona resultados e informa tiempos de primera inferencia y turno total con segunda inferencia si la hay.
- Los chequeos mecánicos no certifican semántica: todos los textos requieren revisión humana. Las trazas públicas quedan privadas/ignoradas en `artifacts/evaluations/`.

## Comparaciones realizadas

| Perfil | Resultado principal | Decisión |
|---|---|---|
| Qwen3 1.7B Q4, contrato corto, sin thinking, temperatura 0 | Mantiene nombre/color de Valeria y duración; confunde nombre en conversación B y solicita timer pese a negación. Relato produce texto interno en inglés. | No aprobado. |
| LFM2.5 1.2B Q4, mismo contrato/escenarios | Más rápido, pero niega conocer el nombre, promete avisar sin llamar herramienta y solicita acción negada. | No aprobado; ver limitación del simulador abajo. |
| Qwen, instrucciones generales más explícitas (`conversation_contract.json` v2) | Algunas explicaciones de fallo mejoran, pero empeoran recuerdo y extracción literal; persiste solicitud en negación. | Variante rechazada; no activar por parecer más completa. |
| Qwen con thinking limitado a 32 tokens | Añade coste, solicitudes sin duración presente y truncamientos; no completa los 14 casos dentro del tiempo de evaluación. | Configuración no viable. No representa evaluar razonamiento completo ni parámetros oficiales. |
| Qwen sin thinking, muestreo publicado por fabricante | Sigue confundiendo datos del usuario y solicita acción ante negación; hay truncamiento en relato y sobrecoste de dos inferencias. | No aprobado. |

### Qué funcionó y cuánto costó

En el primer ensayo Qwen:

- Nombre, color y recuerdo conjunto tras cambio de tema: respuestas correctas en A, ~9.1–11.7 s calientes.
- Crear seis minutos: solicitud correcta en **11.410 s**, después respuesta a resultado simulado; **26.990 s** de cómputo total. No incluye ASR/TTS ni autorización real.
- Recuerdo de duración: correcto, ~13.960 s.
- Negación: solicita 540 s pese al «No»; simulador bloquea. **Fallo de interpretación**, no un timer realmente creado.
- Guardado fallido: no afirma éxito, pero atribuye erróneamente la causa a duración inválida. No aceptar un texto con «no» como respuesta plenamente correcta.
- Primera petición fría: ~34.847 s. Hay que medir preparación aparte, sin ocultar que aún no existe gestor de residencia/precalentamiento de producto.

El flujo de dos inferencias consume más que la llamada inicial. La decisión de diseño `executor_final` para resultados simples puede ahorrar esa segunda generación; **no resolvería la negación ni la confusión de identidad**, por lo que no justifica saltar la puerta de calidad.

## Corrección del instrumento de prueba

Se detectó que un argumento rechazado (por ejemplo copiar `8 minutos` cuando el humano dijo `ocho minutos`) dejaba la llamada nativa sin resultado antes de continuar con la siguiente pregunta. Esto es un defecto del simulador: **todo rechazo debe devolverse como resultado explícito**. Fue corregido y cubierto por pruebas, incluyendo no duplicar resultados si falla la segunda inferencia.

Por ello, las respuestas tardías de la primera serie LFM tras llamadas rechazadas no deben usarse como prueba independiente de incapacidad del modelo: se produjeron con una secuencia de protocolo incompleta. Sí existen fallos anteriores a ese defecto (recuerdo y promesa sin herramienta) que bloquean esa configuración. No se falsea una comparación reparando sus respuestas en el historial.

Cambiar palabras por dígitos equivalentes no es necesariamente inventar duración, aunque incumpla nuestro contrato literal. Es una incompatibilidad del contrato, distinta de extraer una duración equivocada. Se conserva el rechazo seguro mientras se decide si admitir normalización cuantitativa verificable; no relajar a texto arbitrario.

## Referencia del fabricante y límites de las conclusiones

Se revisó la ficha oficial Qwen3-1.7B, revisión `70d244cc86ccca08cf5af4e1e306ecf908b1ad5e`:

https://huggingface.co/Qwen/Qwen3-1.7B/blob/70d244cc86ccca08cf5af4e1e306ecf908b1ad5e/README.md

- Recomienda sin thinking: temperatura 0.7, top-p 0.8, top-k 20, min-p 0.
- Para thinking recomienda otros parámetros y advierte contra greedy; una prueba a temperatura cero con cierre forzado a 32 tokens **no permite concluir que su razonamiento completo sea inútil**.
- Nuestros experimentos greedy favorecían comparación controlada, pero no eran la referencia recomendada. Se añadió un perfil explícito, sin cambiar instrucciones, preguntas ni pesos, con semilla 42 y los valores oficiales del modo sin thinking.
- Ese perfil completó los 14 turnos, pero falló nombre, recuerdo conjunto, nombre de Andrés, negación y un relato truncado. La creación simulada consumió 29.350 s de cómputo total; el fallo simulado 26.367 s. No cumple el presupuesto de 22 s para dejar margen a voz.
- El límite breve de salida es una restricción práctica del proyecto, no la configuración de benchmark de salida larga publicada por Qwen. Los truncamientos se cuentan como fallos de esta configuración, no como prueba de imposibilidad general.
- Una semilla o una muestra favorable no valida uso cotidiano. No buscar semillas hasta que pase ni sumar aciertos de perfiles diferentes como si fueran una sola configuración.

También se consultó documentación pública de alternativas recientes, sin descargar ni instalar pesos: no se eligió automáticamente un modelo por ser más nuevo. La comparación futura debe justificar memoria, herramientas, idioma y tiempo, no solo número de parámetros o publicidad.

## Estado al terminar este bloque

- `conversation_contract.json` v2 marcado como rechazado; no es la configuración de la voz.
- Thinking 32 y muestreo de referencia son perfiles de pruebas; el modo normal conserva thinking apagado y los perfiles nuevos siguen bloqueados para piloto desde el coordinador.
- **Qwen3 1.7B Q4 sigue instalado como candidato diagnóstico, no modelo definitivo aprobado.** LFM tampoco sustituye al candidato por ser más rápido.
- No se descargaron nuevos modelos, abrió micrófono, programaron timers, cambió HOME ni modificaron batería/swap.
- 82 pruebas Python de infraestructura/evaluación pasan. No confundirlas con aprobación del modelo.
- Cierre comprobado: sin VoiceService/RingService ni listeners 11435/11436, timers vacíos y temporales del servidor retirados. Batería posterior 28.9 °C, MemAvailable 1708484 KiB (~1.63 GiB), muestra de reposo.
- **Discrepancia de inventario:** la resolución de HOME al cerrar devuelve el launcher Motorola (`com.motorola.launcher3/...CustomizationPanelLauncher`), no Doha como la verificación histórica. No se cambió HOME ni se desinstaló nada en este bloque; conservar este estado ajeno y volver a inspeccionar en la futura fase de interfaz, sin restaurar Doha automáticamente.

## Siguiente decisión

No ampliar interfaz, hogar ni voz sobre esta configuración. Antes de otro cambio, formular una hipótesis acotada: cuantización/motor frente a capacidad del candidato, o un candidato alternativo con fundamento verificable. Usar esta misma prueba secuencial, más casos de control nuevos, y conservar revisión semántica y techo temporal. No seguir añadiendo instrucciones al contrato v2 ni fabricar memoria sustituyendo las respuestas del modelo por las deseadas.

La protección independiente de carga, autenticación mutua y presupuesto global siguen pendientes para cualquier despliegue final. Estos resultados no obligan a abandonar el Moto ni autorizan nube u otro hardware: delimitan lo que **no se ha conseguido todavía** con las combinaciones probadas.

**Control posterior sin catálogo y de precisión:** `EVALUACION-PRECISION.md`. Conversaciones nuevas con historial generado: Qwen Q4, mismo Qwen Q8 verificado y LFM sin herramientas. Q8 mejora hechos puntuales pero persisten perspectiva/capacidades falsas; LFM es más rápido, pero tampoco distingue consistentemente usuario/asistente. Ninguno aprobado para respuestas de voz. 96 pruebas Python de soporte pasan; no equivale a aprobación del modelo. La UI y activación/ASR 0.3 ya confirmadas por el usuario permanecen separadas de este diagnóstico.

## Medición posterior solicitada: modelo apagado frente a cargado

Se midió de nuevo Qwen3 1.7B Q4_K_M, contexto 2048, microbatch 1, KV f16, caché de prompt hasta 64 MiB, sin thinking. `measure-model-memory.py` toma muestras antes de arrancar, después de una inferencia pública muy corta para tocar los pesos, en reposo cargado y después de cerrar. No abre micrófono, TTS ni ejecutor. Perfil reproducible: `start-andromeda-ai.sh test memory`.

RAM utilizable total: 3764188 KiB = 3.59 GiB.

| Estado | MemAvailable | Total menos disponible (aproximación, no suma de procesos) |
|---|---:|---:|
| Apagado, antes | 1583052 KiB / 1.51 GiB | 2.08 GiB |
| Cargado y calentado | 1392336 KiB / 1.33 GiB | 2.26 GiB |
| Apagado, después | 1672192 KiB / 1.59 GiB | 2.00 GiB |

Servidor calentado: RSS 1459184 KiB (1.39 GiB), PSS 1457882 KiB (1.39 GiB). Private_Clean 1183480 KiB (~1.13 GiB), en gran parte pesos mapeados de archivo; Private_Dirty 274344 KiB (~268 MiB); SwapPss del servidor 0. No sumar ese RSS al total menos disponible: muchas páginas de pesos ya estaban en caché y Linux puede recuperar páginas limpias de archivo, lo que influye en MemAvailable.

La swap total usada subió de 800408 a 921884 KiB durante la carga (~119 MiB). Después se comprobó swap de disco loop9 todavía en 748 KiB: el crecimiento observado corresponde principalmente a zram de otros procesos; el servidor reportó SwapPss 0. El aumento de disponible al cerrar refleja también esa reorganización del sistema, no solo «liberar el modelo». No se modificó configuración de swap ni se limpiaron cachés para fabricar una comparación.

Batería 24.9–25.0 °C en este ensayo breve. Es una muestra cargada/idle, no pico con conversación larga ni medición conjunta LLM+ASR+TTS. Al terminar no quedaron servidor 11435, backend 11436 ni servicios de voz activos. El usuario confirmó que el cambio a launcher Motorola fue suyo; no se restaura Doha.
