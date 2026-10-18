# Memoria LFM · una aclaración de sesión, descartada

## Motivo y alcance

Tras el ensayo humano 0.4.1, los contadores probaron que el tercer turno recibió dos pares previos sin recorte (165 tokens), pero el usuario confirmó que negó recordarlos. Autorizó una comparación pequeña de presentación del contexto, **manteniendo LFM**, sin audio y sin reabrir comparativas de modelos. No se reconstruyó ni guardó su conversación.

**Resultado: no aplicar la variante.** No aporta una mejora semántica clara y en esta serie fue más lenta. Se mantiene el prompt de producción, APK 0.4.1 y los límites existentes. No pedir otra repetición humana idéntica ni hacer del recuerdo perfecto un bloqueo absoluto del ensayo personal ya acordado.

## Método ejecutado en el Moto

- Mismo LFM2.5 1.2B Instruct QAD Q4_0, motor b11669 original, UID 10218, contexto 2048, entrada <=1536, salida <=96, temperatura 0, seed 42, caché advanced y microbatch 16 como la ruta de voz actual. Sin cambios de pesos, plantilla, muestreo, backend CPU o caché.
- Baseline: `SYSTEM` de `backend/conversation_voice.py`. Única variante, añadida al sistema **solo en el evaluador**:

  > El historial adjunto pertenece a esta misma conversacion. Usa esos mensajes para responder sobre lo anterior; si falta un dato, dilo.

- Cuatro temas públicos congelados: estrella/planeta, lluvia/arcoíris, raíces/abejas y selección de las dos preguntas más recientes tras volcán/hielo/peces. Nueve respuestas de preparación generadas realmente por el baseline, conservadas aunque fueran erróneas; no sustituidas por respuestas ideales.
- Ocho pares con **historial idéntico en ambas condiciones**: recuerdo directo de cuatro temas; reconocimiento y enumeración posterior del caso astronómico; controles sin historial y sin herramientas. La enumeración posterior usa la respuesta real de reconocimiento del baseline para ambos sistemas, no dos historias divergentes. Orden baseline/variante alternado entre pares.
- Cada llamada aplica plantilla nativa y tokenizador reales y comprueba presupuesto; sin recortar la historia compartida. Huella del condicionamiento no-sistema idéntica en cada par. Los textos y huellas corresponden **solo a fixtures públicas**.
- Nueve generaciones de preparación + 16 comparadas = 25; dos calentamientos de un token en el evaluador y uno adicional del coordinador: **28 llamadas de generación en total**, tres no puntuadas. Preparación del servidor/prefijo original **7.201 s**; calentamiento de ambos sistemas **4.377 s** separado de las comparaciones.
- Presupuesto global por llamada 22 s, experimento <=240 s y supervisor acotado. No se inicia servidor PCM :11436, Vosk, TTS, micrófono, ejecutor ni red externa. No modificó la app ni abrió una sesión humana.

Fixture: `tests/conversation_memory_cases.json`, SHA-256 **39e5f0adc55da55634e2e46a61fe6b3fa58738e7452ff20a3225993dcd0d8612**. Evaluador `scripts/evaluate-conversation-memory.py`; coordinador `scripts/test-conversation-memory.sh test`. Traza pública completa bajo el directorio privado ignorado `artifacts/android-voice/memory-context-comparison-*.jsonl`.

## Resultado mecánico y revisión humana del texto

| Prueba | Baseline | Aclaración |
|---|---|---|
| Enumerar estrella/planeta directamente | Niega acceso a conversación anterior | Niega acceso a conversación anterior |
| Decir si recuerda ambas | Dice que no | Dice que no |
| Enumerar después de la negativa | Sigue negando memoria | Niega memoria y ofrece hablar de estrellas/planetas; **no es una reparación** |
| Lluvia/arcoíris | Niega historial | Menciona ambos temas, pero invierte el orden y habla de «mi» pregunta/consulta |
| Raíces/abejas | Niega historial, pide documento | Niega historial, pide proporcionar conversación |
| Dos más recientes de tres | Niega historial | Niega historial |
| Sin historial real | Reconoce falta de anteriores | Reconoce falta de anteriores |
| Capacidad de temporizador | Niega capacidad | Niega capacidad |

El chequeo automático pasó de **2/8 a 4/8**, pero los dos «aciertos» añadidos son insuficientes al revisar el texto. En particular, coincidencia de temas **no equivale a recuerdo correcto**:

> No tengo memoria de las preguntas anteriores. ¿Quieres que te cuente sobre las estrellas o los planetas?

La variante de lluvia/arcoíris demuestra recuperación parcial de temas, no mejora general fiable ni separación correcta de interlocutor/orden:

> Mi última pregunta fue sobre por qué llueve, y mi anterior consulta fue sobre cómo se forma un arcoíris.

La pregunta más reciente era la del arcoíris, ambas fueron del usuario. El baseline también produjo un mal resultado de preparación ante «¿Por qué llueve?»; se conserva en el historial compartido y en la traza en vez de corregirlo artificialmente. No se ocultan esos errores ni se atribuyen todos a una sola causa interna del modelo.

## Tiempo y recursos

Tiempos de **texto/API + plantilla/tokenización**, no fin de voz → audio útil:

| Métrica de las ocho consultas comparadas | Baseline | Aclaración |
|---|---:|---:|
| Mediana | 5.476 s | 13.078 s |
| Máximo | 8.323 s | 21.612 s |
| Entrada máxima | 224 tokens | 257 tokens |
| Truncadas/rechazadas por guardia | 0 | 0 |

Todos los resultados terminaron dentro de 22 s y el presupuesto de entrada; **no certifica 30 s de audio útil**. Las salidas tienen longitudes diferentes y el contexto cambia el prefill/reutilización: no es un benchmark de salida idéntica ni demuestra una penalización universal por añadir 33 tokens. Sí muestra que **esta variante, en esta serie**, no justifica su coste frente al beneficio observado.

Mínimo MemAvailable **1442360 KiB (~1.38 GiB)**, pico RSS servidor **757500 KiB (~740 MiB)**, batería máxima **25.9 °C**. Es inferencia de texto aislada, no medición conjunta con ASR/TTS ni ensayo sostenido/24-7. No sumar RSS a memoria usada como si todo fuera privado.

Comprobación posterior separada: batería **26.2 °C** (por encima del máximo muestreado durante el supervisor), MemAvailable **1501388 KiB**. Sin listeners 11435/11436, VoiceService/RingService ni workdirs de inferencia; Andrómeda en primer plano, SSH :2222 activo y web :8080 HTTP 200. Modelo apagado y sin una nueva preparación de voz.

## Decisión

- **Variante descartada, no instalada como prompt de voz.** La decisión del usuario de seguir con LFM permanece; no se amplía el contexto ni se cambian modelos, thinking o cachés.
- La integración transporta los turnos bajo las condiciones verificadas; una negativa del modelo sobre «tener memoria» no es una lectura del estado real del historial. No preparar respuestas específicas para preguntas de recuerdo ni declarar una solución universal basada en esta prueba.
- Mantener contadores sin contenido para futuros usos personales. Próximo trabajo útil del MVP: claridad del inicio/preparación y experiencia de parada, no otra cadena automática de comparativas ni habilitación de acciones.
- Pruebas de infraestructura ampliadas: 157 Python; no aprobación semántica de LFM. Toda comprobación humana de Silenciar sigue pendiente por separado.
