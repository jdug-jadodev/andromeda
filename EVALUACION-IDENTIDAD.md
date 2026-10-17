# Identidad, contexto y eficiencia: mismo Qwen 1.7B Q4

## Petición y control

El usuario pidió priorizar eficiencia y comprobar contexto/identidad antes de cambiar a un modelo mayor. En este bloque **no se descargó ni cambió ningún modelo**, ni se habilitó voz o ejecución de acciones. Se mantuvo Qwen3 1.7B Q4_K_M, contexto 2048, microbatch 1, cuatro hilos, KV f16, sin thinking y muestreo/semilla de la comparación anterior.

Hipótesis: separar explícitamente asistente/usuario mediante **dos frases generales**, sin nombres de las fixtures ni respuestas por pregunta. `config/conversation_identity.json` conserva esa variante, ahora marcada como rechazada para voz. No se reescribieron sus instrucciones después de ver resultados.

Sistema base: 268 caracteres / 86 tokens en la petición de preparación. Sistema nuevo: 425 caracteres / 131 tokens. Es un incremento de **45 tokens de prefijo fijo**, no un perfil de memoria generado ni una corrección de respuestas. No se insertan hechos del examen en el sistema.

- Regresión: las mismas 15 intervenciones de `conversation_only_cases.json`, SHA-256 `d3cb1a9816ae57d1518dd9ecf5295b703b7737d6bb8c3b24b9046180b71ca63b`.
- Nuevos controles fijados antes de probar: ocho intervenciones en dos conversaciones de Camila/Cami/gata Menta y Hugo/hermana Laura/planta. `identity_holdout_cases.json`, SHA-256 `0e1c8975a9a9985206b20ac051ff0ec4764c7bb046030287b4dad4b7ba62145b`.
- Se ejecutaron **base y variante con los mismos ocho controles**, en procesos separados y secuenciales. Historial formado por respuestas reales; no sustituciones ni recuperación con respuestas ideales.
- `/apply-template` conserva el sistema y roles `system,user,assistant` en la preparación. Los perfiles nuevos están bloqueados para `pilot` desde el coordinador central.

## Resultados: instrucciones explícitas no bastaron

### Regresión

La variante no recordó Natalia al preguntarle su nombre; después se atribuyó el perro. Aceptó «Nati» inicialmente con formulación de segunda persona, pero al recordar datos dijo **«Puedes llamarme Nati, y mi perro se llama Roco»**.

Con Óscar respondió que no recordaba su nombre aunque acababa de recibirlo y usarlo. Más tarde **afirmó que se creó un temporizador**, pese a que la sesión carece de herramientas y no ejecutó nada. La explicación de lluvia mejoró respecto a la muestra Q4 base, pero el consejo de neutralizar sal con zumo siguió siendo poco fiable. No sumar mejoras puntuales como aprobación global.

### Controles nuevos, sin ajustar el prompt

| Tema | Base | Variante de identidad |
|---|---|---|
| Camila/Cami | Se presenta como Andrómeda pero se atribuye Cami; no da Camila | Se presenta como Andrómeda y ofrece Cami como nombre propio; no da Camila |
| Gata Menta | Dice «mi gata», perspectiva errónea | Responde «Menta», sin atribuírsela: mejora puntual |
| Hugo y Laura | Distingue correctamente Hugo/hermana Laura | También distingue correctamente esos papeles |
| Nombre propio | Conserva Andrómeda | Conserva Andrómeda |
| Propiedad de la planta regalada | Dice que sigue siendo de Laura: incorrecto | Dice «Mía»: incorrecto y cambia perspectiva |

Ambos tienen las mismas dos señales mecánicas fallidas de ocho; **los otros seis no equivalen a seis éxitos semánticos**. La variante no ofrece mejora consistente.

## Eficiencia medida

| Ocho controles nuevos | Base | Variante |
|---|---:|---:|
| Preparación fría | 13.191 s | 18.572 s |
| Petición mínima | 5.285 s | 6.217 s |
| Mediana API | **8.053 s** | **10.424 s** |
| Petición máxima | 12.279 s | 14.217 s |

La regresión con la variante dio 6.498–17.033 s, mediana 10.337 s. El prefijo fijo se reutiliza por caché; no se vuelve a evaluar íntegro en cada turno. Pero las respuestas generadas son distintas, algunas más largas/repetitivas, y por tanto también cambia el historial posterior. **No atribuir toda diferencia temporal a esos 45 tokens**, ni tratar esta muestra como benchmark estadístico estable. Sí basta para no justificar el nuevo prompt como optimización comprobada.

Todas estas llamadas calientes quedaron bajo 22 s de cálculo, **sin ASR/TTS**. La corrección semántica falla aun con tiempo disponible; no hay aceptación de voz.

## Control de caché: historial idéntico, recalculado por completo

Por el antecedente de dependencia numérica documentado en `EVALUACION-CONTROLADA.md`, se añadieron dos contrastes con el sistema nuevo: Camila/nombre-apodo y Óscar/nombre. Cada introducción generó una respuesta real **una sola vez**. Se congeló ese historial y se repitió exactamente la pregunta con caché parcial, sin caché y otra vez con caché.

No se añadieron al historial las respuestas de las repeticiones. Cada terna mantiene un hash de prompt idéntico; solo cambia `cache_prompt`. La API confirmó `cache_n=0` al recalcular completamente. Timeout de diagnóstico 90 s; presupuesto de aceptación sigue en 22 s, no se amplió para el producto.

| Prompt | Caché parcial | Recalcular completo | Repetir ya calculado |
|---|---:|---:|---:|
| Camila, 196 tokens | 12.116 s | **30.645 s** | 6.480 s |
| Óscar, 183 tokens | 9.181 s | **26.785 s** | 3.851 s |

En los tres caminos de cada terna la salida fue **idéntica y errónea**:

- Camila: Andrómeda adopta Cami como nombre propio y no responde Camila.
- Óscar: niega recordar el nombre recién recibido.

Hashes: Camila `8a447e73edb21c01e98637565c19ca69daf7e0300d0bc4c587f8eb25d12c6bb3`; Óscar `1c075f6e7304722d8af3dc147ea4636b815dcd6d325e344b01dfbad650dd9039`.

Esto **no apoya una pérdida de contexto causada por reutilización de caché en estos dos casos**. No demuestra que todo el motor sea correcto ni borra el resultado previo de otro caso con microbatch 16. Contexto disponible no equivale a contexto interpretado correctamente.

Desactivar caché empeoró tiempos sin corregir respuestas; la repetición idéntica favorable tampoco representa latencia de una pregunta nueva. No adoptar ni quitar caché como arreglo general.

## Decisión

- Rechazar esta variante de instrucciones, **no rechazar de forma universal cualquier modelo pequeño o cualquier trabajo de contexto**. No se demuestra que un modelo mayor sea necesario.
- Mantener caché y prompts estables/compactos; no seguir aumentando prohibiciones o desactivar caché a costa de latencia para ocultar fallos.
- No afirmar que el modelo ya está bien salvo su nombre: hubo fallos de propiedad, memoria y capacidades falsas. Hay respuestas correctas, pero no forman todavía una conversación fiable completa.
- Conservar la app 0.3 de activación/ASR, sin nueva instalación ni cambios de micrófono, launcher, batería, swap o Android. No convertir esta configuración en voz conversacional.
- Antes de otro experimento, fijar una hipótesis de representación de contexto o verificación del motor y medirla con controles nuevos, sin respuestas por frase ni perfiles ideales suministrados a mano. No volver a descargar modelos por este resultado ni elevar el techo de 30 s.

**100 pruebas Python** de soporte pasan. Perfil `pilot identity` rechaza con código 2. Cierre: sin servicios de voz/alarma ni listeners 11435/11436 ni temporales del worker; batería 28.3 °C, MemAvailable 1746652 KiB (~1.67 GiB), muestra posterior de reposo.
