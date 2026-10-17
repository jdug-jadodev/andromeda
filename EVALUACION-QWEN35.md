# Qwen3.5 2B Q4: compatibilidad, conversación y recursos

## Resultado

**Instalado y evaluado como candidato; no aprobado para conversación por voz.** Cabe en las pruebas de memoria y el motor lo carga, pero las dos configuraciones probadas conservan errores semánticos. La app 0.3, el reloj/clima y el despertar por nombre no se sustituyeron ni modificaron. No se abrió micrófono ni se ejecutaron acciones.

## Artefacto y preparación

- Base `Qwen/Qwen3.5-2B`, licencia Apache-2.0. Ficha revisada en revisión `15852e8c16360a2fea060d615a32b45270f8a8fc`.
- Cuantización comunitaria `unsloth/Qwen3.5-2B-GGUF`, revisión fijada `f6d5376be1edb4d416d56da11e5397a961aca8ae`.
- Archivo `Qwen3.5-2B-Q4_K_M.gguf`, **1280835840 bytes** (~1.19 GiB / 1.28 GB), SHA-256 **`aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`**, verificado en PC y Moto antes de cargar.
- Infiere solo el Moto; PC únicamente descarga/build/control. Texto únicamente, sin mmproj/visión ni servicios de nube. Un modelo residente a la vez.
- Binario Android ARM64 llama.cpp b11669 contiene implementación `qwen35`; después se comprobó carga e inferencia real, no solo presencia del nombre en el binario.
- Contexto 2048, 4 hilos CPU, microbatch 16, KV f16, thinking deshabilitado. Política `hybrid` con 4 checkpoints de estado y caché RAM de 64 MiB: no copiar los 32 checkpoints de los perfiles anteriores a esta arquitectura recurrente sin acotar memoria. No se asume que microbatch 1, usado como control con Qwen3, sea un arreglo universal para otra arquitectura.
- Guardas existentes de recursos/temperatura y duración conservadas. Perfiles nuevos solo `test`; `pilot qwen35_conversation` rechaza con código 2. Configuración reproducible en `config/qwen35_candidate.json`.

Estas diferencias necesarias de runtime y muestreo implican que no es un experimento donde cambie únicamente el archivo frente a Qwen3. Es una comparación de configuraciones candidatas para el producto.

## Pruebas intactas

1. Las mismas 15 intervenciones de `conversation_only_cases.json`: SHA-256 `d3cb1a9816ae57d1518dd9ecf5295b703b7737d6bb8c3b24b9046180b71ca63b`.
2. Los ocho controles de `identity_holdout_cases.json`: SHA-256 `0e1c8975a9a9985206b20ac051ff0ec4764c7bb046030287b4dad4b7ba62145b`.

Sistema base original (268 caracteres), no las reglas de identidad rechazadas. Historial generado real, conversaciones independientes, semilla 42, salida máxima 96 tokens. No cambios de preguntas, respuestas ideales, reparación de historial ni búsquedas externas del agente. `/apply-template` conserva el sistema y roles de preparación. Todo texto de prueba es público/ficticio.

### Referencia oficial sin thinking

Fuente: https://huggingface.co/Qwen/Qwen3.5-2B/blob/15852e8c16360a2fea060d615a32b45270f8a8fc/README.md#best-practices

Para texto sin thinking: temperatura 1.0, top-p 1.0, top-k 20, min-p 0, presence penalty 2.0, repetition penalty 1.0 (`repeat_penalty` en esta API). No usar automáticamente los parámetros de Qwen3 anterior.

Completó ambas suites, pero en conversación negó tener nombre, se apropió de Nati/Roco y rechazó explicar la lluvia por no tener acceso externo. Dio consejos de cocina poco fiables y desarrolló respuestas largas con contenido no aportado por el usuario. Cuatro llamadas de la serie principal excedieron 22 s de cálculo. Se mantiene rechazo aunque varias palabras esperadas aparezcan.

### Control acotado: solo penalización de presencia 2 → 0

La ficha oficial advierte degradación/mezcla de idiomas con penalizaciones altas. La guía del cuantizador también admite el intervalo 0–2 y las presenta como ajuste contra repetición:

https://unsloth.ai/docs/models/qwen3.5

Se fijó un único contraste: **cambiar solo `presence_penalty` a 0**, preservando pesos, sistema, casos, motor, caché, resto de muestreo y semilla. No se cambiaron instrucciones ni se probaron semillas hasta acertar. Mismos ocho controles y después mismas 15 intervenciones.

Mejoró algunas respuestas: conservó su nombre en un turno, negó capacidad de crear timers y resolvió la propiedad de la planta de Hugo en ese historial. No constituye solución:

- Ante el nombre de Natalia propone usarlo para la asistente, en vez de responder correctamente al usuario.
- Responde «mi perro se llama Roco» y después entiende Nati como nombre suyo.
- En los controles llama Camila a quien pidió Cami; la palabra más corta no se recordó de forma independiente.
- Se apropia de la gata Menta.
- La explicación de lluvia vuelve a hablar de nubes que se rompen; mezcla evaporación/condensación de forma poco fiable.
- Niega información personal de Óscar mientras lo llama Óscar: no prueba recuerdo coherente.
- Admite no disponer de clima actual, pero añade caracterización cálida/soleada de Bogotá sin fundamento en el contexto.

No se ejecutaron timers. Tampoco se programan respuestas por frase para ocultar estos fallos. Ambas configuraciones quedan sin aprobación; no afirmar que todo Qwen3.5 o todo motor/implementación fallará igual.

## Correcciones del instrumento de evaluación

### Nombres/apodos deben comprobarse como hechos separados

Se detectó que `contains_all=["camila","cami"]` aceptaba «Camila» como prueba de ambos por coincidencia de subcadena. Se endurecieron `contains_all` y `forbids` con límites de palabra; añadido test de Camila/Cami y de un nombre no coincidente dentro de otra palabra. **No se cambiaron las fixtures ni los textos generados**. Se recalcularon los controles de ambas configuraciones con el mismo evaluador corregido, sin repetir inferencias.

La revisión semántica sigue siendo necesaria: incluso un conteo por palabras exactas no distingue «mi gata» de «tu gata» ni una promesa falsa de ejecución. La serie principal sin penalización marcó cero fallos mecánicos, pero no supera la revisión anterior.

### Unicode al archivar stdout por PowerShell

En el archivo local, `adb` había pasado UTF-8 por la consola OEM **cp850**, después guardada por Tee en UTF-16. Esto convirtió «Andrómeda» en caracteres de caja y produjo fallos adicionales falsos al recalcular la traza local.

- El HTTP local y el historial en Python del Moto **no usan ese stdout**: la respuesta se decodifica desde JSON y se añade a mensajes antes de imprimir. Este defecto de archivo no explica ni corrige los errores de perspectiva observados durante inferencia.
- Los originales se conservan. Se verificó reparación **reversible**, no reescritura de respuestas: `texto.encode('cp850').decode('utf-8')`, con comprobación de vuelta exacta al texto archivado.
- Se recalcularon ambas configuraciones tras reparar solo el transporte. El primer recálculo sin reparar queda invalidado; el correcto está en `artifacts/evaluations/qwen35-offline-rescore-corrected.jsonl`.
- Para próximas trazas, el evaluador emite JSON con escapes ASCII, manteniendo Unicode sin pérdida en cualquier consola. Prueba de roundtrip añadida. No se altera entrada/salida del modelo.

## Latencia API

| Configuración / suite | Mínima | Mediana | Máxima | Casos >22 s |
|---|---:|---:|---:|---:|
| Oficial, 15 turnos | 6.672 s | 15.051 s | 26.844 s | 4 |
| Oficial, 8 controles | 9.637 s | 14.155 s | 16.715 s | 0 |
| Presencia 0, 15 turnos | 8.548 s | 16.799 s | 20.684 s | 0 |
| Presencia 0, 8 controles | 8.296 s | 9.646 s | 15.776 s | 0 |

Preparación fría de estas series ~12.13–12.15 s, con una petición independiente de un token no puntuable. La primera petición de la segunda conversación recalculó prefijo completo: se incluye, no se oculta. Distintas respuestas alteran los historiales posteriores y su longitud; no atribuir toda diferencia temporal exclusivamente a la penalización. No es un benchmark pareado de salidas iguales ni una garantía estadística.

Los valores excluyen ASR/TTS y no certifican 30 s de voz. Ni el archivo de tamaño similar al anterior ni su arquitectura más reciente garantizan menor latencia.

## RAM y temperatura

Muestra final de la última serie, KiB:

| Estado | MemAvailable | Modelo PSS |
|---|---:|---:|
| Antes, apagado | 1766152 (~1.68 GiB) | — |
| Cargado, tras conversaciones | 1538616 (~1.47 GiB) | 1453175 (~1.39 GiB) |
| Después de cerrar | 1748688 (~1.67 GiB) | — |

Servidor: RSS máximo 1459616 KiB (~1.39 GiB); muestra final Private_Clean 1247628 KiB (~1.19 GiB), Private_Dirty 205492 KiB (~201 MiB), SwapPss 0. MemAvailable incluye páginas recuperables; no sumar PSS al uso calculado como total menos disponible ni interpretarlo todo como RAM privada fijada.

En las cuatro series, mínimo MemAvailable observado 1500072 KiB (~1.43 GiB), máximo de batería **29.3 °C**. No hubo corte por guardas ni se cambió swap/zram. Esto es LLM + app visible, **no** LLM + ASR + TTS simultáneos ni certificación de SoC/batería 24/7.

## Estado al cerrar y siguiente decisión

Investigación documental posterior, sin nuevos cambios del Moto: `INVESTIGACION-LLAMA-QWEN.md`. Hay reportes de plantillas multivuelta y fallos CPU/Android, pero ninguna reproducción exacta de este caso. Priorizar auditoría del turno completo y comparación de rutas antes de descargar otro modelo; algunos arreglos citados ya están presentes o no aplican a nuestra configuración.

- La app y micrófono continúan en 0.3, separados del candidato. No hay nueva conversación de voz habilitada.
- **106 pruebas Python** de soporte pasan; no aprobar el modelo por ellas.
- Modelo apagado; sin servicios de voz/alarma, listeners 11435/11436 ni temporales del worker. Batería posterior 29.3 °C, MemAvailable 1752848 KiB (~1.67 GiB), muestra de reposo.
- No se modificaron launcher, servicios Android, Debian existente, carga, swap ni arranque; sin commits/pushes.
- No recomendar otro salto de tamaño o una cadena de descargas por este resultado. Antes de atribuirlo universalmente a capacidad del modelo, conviene contrastar una ejecución de referencia/otra ruta de cómputo **en el Moto**, manteniendo pesos y casos. Compatibilidad de carga no demuestra por sí sola corrección numérica o de integración. Tampoco afirmar sin prueba que ese contraste vaya a corregirlo.
