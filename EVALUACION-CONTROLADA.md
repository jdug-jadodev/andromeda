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
