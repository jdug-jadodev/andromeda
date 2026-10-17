# Reportes externos: Qwen, identidad, plantillas y CPU ARM

Consulta: 17 de octubre de 2026. Investigación documental; **no se ejecutó nueva inferencia, instaló software ni cambió configuración del Moto** durante este bloque.

## Conclusión

Hay reportes parecidos y correcciones reales de integración. **No se encontró una reproducción externa exacta de nuestros errores de perspectiva/propiedad en el Moto G8 Plus con b11669, F16 de KV y estos GGUF.** No prometer que una receta de foro los resuelva, ni atribuirlos exclusivamente al tamaño del modelo.

## Fuentes primarias y aplicabilidad

### 1. Qwen3 1.7B: plantilla multivuelta sin thinking

https://huggingface.co/Qwen/Qwen3-1.7B/discussions/9

El autor muestra que la plantilla añade un bloque vacío `<think>...</think>` a la última respuesta histórica, pero no a otra anterior. Propone corregir la condición; un segundo usuario informa que aplicar la plantilla le funcionó. La discusión permite consultar el cambio de una línea.

**Límite importante:** el ejemplo termina en mensaje `assistant` y usa `add_generation_prompt=False`. Nuestros turnos de evaluación terminan en `user` y la API de b11669 activa el prefijo de generación por defecto. No se ha demostrado que el defecto del ejemplo se active en nuestra ruta. No descargar su copia de pesos ni sustituir la plantilla a ciegas.

Otro reporte sobre el mismo modelo:
https://github.com/QwenLM/Qwen3/issues/1807

El usuario describe dificultad para entrar en el papel de asistente si omite `add_generation_prompt=True`. Es un reporte de usuario, no una corrección del motor confirmada. Nuestra auditoría de preparación ya muestra rol final `assistant`; el código de b11669 establece ese parámetro en `true` por defecto.

### 2. Historial y caché cambian al retirar los bloques de thinking

https://github.com/QwenLM/Qwen3/issues/1826

Usuarios describen que el prefijo de generación de un turno incluye un bloque vacío de thinking que desaparece al serializarlo como historial en el siguiente. La coincidencia de prefijos se interrumpe. Reportan mejorar reutilización/latencia haciendo estable la plantilla; sus cifras son de MLX/Mac y modelos mayores, no del Moto.

**Aplicación plausible:** auditar la secuencia exacta de tokens entre turnos, particularmente con la memoria recurrente de Qwen3.5. **No demuestra pérdida de datos humanos ni explica por sí solo «mi gata»**. Con historial completo, recalcular lo recibido no equivale a olvidarlo. Los controles locales con Qwen3 ya conservaron los mismos errores al recalcular sin caché. No repetir desactivación de caché como supuesto arreglo semántico.

### 3. Qwen3 1.7B, Android ARM64 CPU: caché cuantizada produce basura

https://github.com/ggml-org/llama.cpp/issues/23044

Reporte en Pixel 10a, CPU + KleidiAI: KV Q4_0/Q8_0 produce respuestas vacías/incoherentes; KV F16 funciona según el autor. Cargar el modelo y ejecutar llama-bench no detectaba el problema. El hilo terminó cerrado por inactividad; **no hay confirmación de mantenedor ni parche verificado en ese hilo**.

**Para nosotros:** ya usamos `--cache-type-k f16 --cache-type-v f16`. Es evidencia de que existen fallos reales en Android, no una solución nueva que nos falte. La precisión Q8 de los **pesos** ensayada antes no es Q8 de la **caché KV**; no confundirlas.

### 4. Error numérico real de normalización GDN

https://github.com/ggml-org/llama.cpp/pull/28068

Cambio integrado el 6 de septiembre de 2026: ajustar la normalización GDN a la implementación de referencia; afecta, entre otras, a la arquitectura `qwen35`. Los comentarios incluyen resultados semánticos de Metal, con mejoras parciales y fallos restantes: no es una cura universal.

Se comprobó por la comparación de commits de GitHub que el tag **b11669 ya contiene el merge `5fdfa6282936576d2f352d4b97f397a109f207a6`** (840 commits por delante, cero por detrás). Por tanto no proponer actualizar para obtener este parche como si faltara en nuestra versión. Tampoco aplica al Qwen3 1.7B denso anterior como explicación común.

### 5. Síntomas parecidos de GPU no equivalen a nuestro fallo

https://github.com/ggml-org/llama.cpp/issues/21239
https://github.com/ggml-org/llama.cpp/issues/22391
https://github.com/ggml-org/llama.cpp/pull/22489

Salida de barras repetidas y paralelismo tensorial con varias GPU; parche de AllReduce y segmentos de tamaño cero confirmado por usuarios. Nuestro worker usa CPU y `-ngl 0`, no esa ruta. No recomendar sus flags como arreglo del Moto.

https://github.com/ggml-org/llama.cpp/issues/28211

Reporte con reproducción de logits incorrectos al dividir el prompt en microbatches sobre HIP/AMD; comentarios verifican corrección al revertir acceso directo a host buffers. Útil como ejemplo de metodología, **no como prueba de un bug ARM**. Diferencias pequeñas de coma flotante entre microbatches no bastan para demostrar corrupción.

## Próxima prueba recomendada, todavía no ejecutada

Sin nuevos modelos, sin micrófono, nube, otro servidor, acciones ni cambios de identidad:

1. **Auditar los turnos exactos que fallan**, no solamente el saludo de preparación: mensajes, roles, UTF-8, prompt renderizado, prefijo final de asistente/thinking, IDs de tokens y margen de contexto. Comparar con la plantilla oficial fijada del modelo y explicar cualquier diferencia antes de editarla. Los ensayos existentes conservaron historial, pero la auditoría de preparación no verifica por sí sola toda serialización multivuelta.
2. Congelar el mismo prompt y comparar la ruta `/v1/chat/completions` con `/completion` usando el prompt nativo/tokenización equivalente, mismo muestreo y semilla. Comparar texto/tokens crudos antes de nuestros parsers. Esto aísla presentación/parseo; **no valida por sí solo las operaciones del motor**, compartidas entre rutas.
3. Si no aparece defecto de plantilla/parseo, contrastar la ruta CPU actual con controles individuales de optimización en el Moto. b11669 documenta `--no-repack` y `--flash-attn off`; verificar primero qué rutas están efectivamente activas. No cambiar ambos flags a la vez ni llamarlos referencia numérica completa. Si hace falta, preparar binario de referencia separado, sin reemplazar el instalado. Comparar distribuciones/logits sobre tokens fijos, no exigir igualdad literal de generaciones libres como criterio universal de corrección.
4. Adoptar únicamente un cambio que muestre mejora semántica repetible en regresión y controles nuevos, sin sobrepasar presupuesto/recursos. Los controles lentos pueden servir para diagnóstico, no para producción. Si no mejoran, conservar el resultado negativo y no encadenar ajustes hasta acertar la fixture.

## Código fuente comprobado

- Prefijo de generación por defecto y combinación de kwargs de thinking:
  https://github.com/ggml-org/llama.cpp/blob/b11669/tools/server/server-common.cpp
- Opciones de repacking/Flash Attention:
  https://github.com/ggml-org/llama.cpp/blob/b11669/common/arg.cpp
- Presencia del parche GDN en la versión utilizada:
  https://github.com/ggml-org/llama.cpp/compare/5fdfa6282936576d2f352d4b97f397a109f207a6...b11669
