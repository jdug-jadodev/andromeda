# Conversación normal con herramientas — prioridad corregida

El usuario aclaró que el objetivo **no es un clasificador de frases ni añadir consultas programadas pregunta por pregunta**. Quiere hablar normalmente con la IA, conservar el hilo y que la IA decida usar herramientas de Internet, temporizadores y las integraciones previstas en `PLAN-DOHA.md`.

Se retiraron los cambios de herramientas específicas `get_time` / `model_info` antes de instalarlos. Identidad del modelo y fecha/hora son contexto de ejecución, no respuestas emparejadas con preguntas. La zona del Moto es `America/Bogota`; el coordinador pasa la zona Android al proceso sin root.

## Avance posterior en bloque

- Se aisló plantilla/protocolo con controles cortos en español e inglés: `/v1/chat/completions` y `/completion` produjeron resultados equivalentes y pudieron incluir el nombre suministrado. Eso no demostró comprensión general de la conversación con catálogo de herramientas.
- El adaptador actual usa la API nativa de chat, mensajes y `tool_calls` con llama.cpp `--jinja`, no fuerza un JSON clasificador por pregunta. Convierte historial/herramientas/resultados con IDs emparejados y rechaza llamadas desconocidas. El parser AST nativo anterior queda para diagnóstico, no para ejecutar código.
- Comparados secuencialmente LFM2.5 1.2B, Qwen3 0.6B y **Qwen3 1.7B Q4_K_M**. Nunca se cargaron a la vez. El 0.6B no superó recuerdo/selección de herramientas, aunque tras corregir la caché contestó turnos cortos en ~6.7–10 s.
- Candidato 1.7B: `bartowski/Qwen_Qwen3-1.7B-GGUF`, revisión `dcb19155b962dbb6389f4691a982043a8e651022`, archivo `Qwen_Qwen3-1.7B-Q4_K_M.gguf` (1282439584 bytes). SHA-256 `72c5c3cb38fa32d5256e2fe30d03e7a64c6c79e668ad84057e3bd66e250b24fb`, verificado antes de instalar y otra vez en el Moto. Modelo base Qwen3-1.7B, licencia Apache-2.0; cuantización comunitaria, no archivo oficial de Qwen. Se conservan los modelos anteriores.
- El 1.7B **sí seleccionó crear 120 s, listar timers y consultar Wikipedia**. El resultado del listado volvió al modelo desde un ejecutor simulado. La consulta de biodiversidad salió realmente a Wikipedia y el modelo produjo respuesta tras recibir un extracto. Eso prueba recorrido técnico, **no exactitud de cada afirmación ni citas suficientes**; la atribución y algunos detalles de la respuesta requieren revisión. No se programaron timers Android reales ni se abrió micrófono.
- El recuerdo del nombre sigue siendo inconsistente: acertó «Lucía» en una serie y confundió interlocutor/asistente en otras. Añadir una instrucción sobre pronombres no lo corrigió y se retiró; no marcar memoria como validada solo porque el almacén pase tests.
- Dos costes de caché corregidos: (1) la hora se captura una sola vez por turno humano, después del prefijo estático, y no cambia el contexto de mensajes anteriores; (2) el máximo se amplió de 10 a **24 mensajes de protocolo**, manteniendo 2600 caracteres, porque una confirmación con resultados podía expulsar la apertura al siguiente turno. Son conversaciones breves, no historial ilimitado. En el último ensayo la caché pasó a reutilizar 570–1059 tokens.
- Última serie 1.7B: preparación fría 74.162 s; turno de recuerdo 14.853 s (respuesta incorrecta), propuesta de creación 16.824 s, confirmación local ~0.001 s, selección de listado 41.134 s y consulta+respuesta de Internet 66.565 s (antes ~143–175 s, con respuestas/ensayos distintos). **Todavía no cumple interacción rápida.** RSS máximo del servidor 1528896 KiB; MemAvailable mínimo 1331896 KiB, que incluye memoria recuperable; batería máxima 30.4 °C. No se midió aquí toda la presión de zram/SoC.
- 45 pruebas unitarias pasaron. Se reforzó el ensayo de integración: una herramienta llamada sin respuesta final válida ya no cuenta como éxito. La serie global sigue fallando recuerdo conversacional. El lanzador de chat permanece bloqueado para voz; la comparación automática se ejecuta con `start-andromeda-ai.sh test qwen17`.

No cambiar a producción ni iniciar otra voz por estos resultados parciales. Próxima prioridad: fiabilidad conversacional e interpretación de identidad, y reducir la latencia de historial/resultados antes de más funciones o sesiones humanas.

## Base implementada, no declarada operativa

- `backend/conversation_agent.py`: registro de herramientas, conversación con mensajes de usuario/asistente/herramienta, llamadas validadas, devolución de resultados reales al modelo y bucle máximo de tres consultas por turno.
- Memoria reciente en RAM: hasta 24 mensajes de protocolo / 2600 caracteres (inicialmente diez, corregido tras pruebas); conversaciones identificadas por UUID, expiración independiente después de una hora desde la última actividad humana. Resultados de herramientas no reinician ese contador. La sesión piloto tiene un UUID nuevo; salir del proceso pierde esa memoria antes del máximo de una hora. No implementa todavía selección de conversaciones desde UI/chat remoto ni persistencia entre reinicios.
- Memoria y propuestas separadas de los temporizadores persistentes Android. Crear/cancelar requiere confirmación de un solo uso en 60 s. La confirmación final de mutaciones la produce el ejecutor, no una promesa del modelo.
- Puente Android devuelve `tool_result` con ID correlacionado, resultado y estado; las consultas pueden volver al modelo para construir su respuesta con ese resultado. No se acepta un resultado con ID desconocido o repetido.
- `backend/internet_tools.py`: primera herramienta real, búsqueda enciclopédica en Wikipedia español. Envía solo la consulta, sin historial/credenciales; origen HTTPS fijo, redirecciones externas bloqueadas antes de seguirlas, límite de tamaño/tiempo y sin proxy. Devuelve tres extractos con URLs. **No es un buscador general, clima ni noticias.** API probada desde el Moto con «Colombia»: tres resultados. No se validó todavía que el modelo la seleccione y cite correctamente.
- Catálogo extensible, sin ejecutar shell ni código generado. Home Assistant, música, TV, dispositivos, APIs, recordatorios y búsqueda general siguen pendientes de sus adaptadores/credenciales/host compatible ya documentados.
- Adaptador LFM contrastado con plantilla oficial: `https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct/blob/main/chat_template.jinja`. Utiliza `List of tools`, mensajes ChatML y marcadores nativos de llamadas. Parser AST solo permite una llamada registrada y valores literales; **nunca eval/exec**.

## Resultado real: bloqueado por calidad y latencia

41 pruebas unitarias pasaron y APK compilado/instalado. Los dobles verifican historial, caducidad independiente, límites, resultados correlacionados, confirmación y parser seguro. Eso **no prueba la capacidad del LLM**.

Las pruebas con LFM2.5-1.2B QAD Q4_0 real fallaron:

- Tras «me llamo Lucía», negó conocer el nombre en el turno siguiente, aunque el historial sí se le enviaba.
- «Avísame dentro de dos minutos» produjo una promesa de avisar, **sin llamar a la herramienta**, por lo que no se creó timer.
- La consulta de timers no seleccionó la herramienta de listado.
- Primera implementación con JSON propio: latencias ~24–49 s; prompt largo llegó a timeout.
- Adaptación nativa y contexto 2048: ~34.8 / 42.4 / 44.2 / 49.6 s en cuatro turnos. Métricas: `cache_n=0`, prefill ~11.8–13.2 tokens/s y generación ~7.4–8.1 tokens/s. Aumentar checkpoints/caché no solucionó reutilización en esta serie. No atribuir toda la lentitud a generación ni prometer caché efectiva.

No se ejecutaron acciones Android en esos ensayos: el ejecutor de la prueba es simulado, indicado en sus resultados. No se abrió micrófono. `start-andromeda-chat.sh pilot` **rechaza iniciar** este modo hasta corregir y validar los fallos. `start-andromeda-chat.sh test` permite repetir pruebas sin micrófono. El piloto anterior de un turno sigue siendo diagnóstico, no la funcionalidad final solicitada.

## Siguiente trabajo

1. Aislar el comportamiento del adaptador/plantilla y llama.cpp frente al modelo/quantización, con referencias de conversación y uso nativo de herramientas. No culpar únicamente al tamaño del modelo sin comprobarlo.
2. Evaluar candidato local apto para herramientas si esta combinación no supera las pruebas. Se mantiene la preferencia local del usuario; no activar inferencia externa sin su autorización.
3. Exigir pruebas de memoria, selección de herramientas y respuestas basadas en resultados antes de otra sesión con voz. Luego completar búsqueda general/clima y adaptadores pendientes.
4. Validar eliminación por inactividad sostenida, transporte/autenticación y recursos antes de servicio permanente; proteger carga 24/7 sigue pendiente.
