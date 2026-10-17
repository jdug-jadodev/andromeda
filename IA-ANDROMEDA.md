# Andrómeda con IA local — piloto integrado

**Revisión de alcance:** este documento describe el piloto de un turno, no el chat normal con herramientas que solicita el usuario. El trabajo posterior está en `CHAT-HERRAMIENTAS.md`: base conversacional implementada, pruebas reales del modelo fallidas, modo nuevo sin habilitar escucha. No presentar el piloto clasificador como objetivo final.

Estado 2026-10-16: instalado, probado con texto y voz sintética; **pendiente voz real**. No hay micrófono ni modelo en arranque automático. Se conserva el piloto anterior de reglas como opción de diagnóstico, no como sustituto de la IA solicitada.

## Arquitectura

Vosk detecta el nombre y transcribe. `backend/local_ai.py` manda el turno al LFM2.5 1.2B Q4 local, vía llama.cpp autenticado en `127.0.0.1:11435`, sin proxy ni Internet. El modelo responde JSON restringido: conversación, proponer timer, proponer cancelación o consultar timers. No ejecuta shell ni recibe credenciales o herramientas arbitrarias.

Crear/cancelar **no depende ya de que la frase coincida con la gramática rígida anterior**. El resultado del modelo se valida y se transforma en una propuesta con texto del programa. Se descarta su texto libre para acciones: durante el ensayo el modelo dijo «He cancelado…», aunque no había ejecutado nada. Esa frase no se transmite como confirmación al usuario.

Toda mutación requiere una segunda activación y una confirmación exacta («Andrómeda, sí» o «Andrómeda, confirmo») dentro de 60 s. Un «no» descarta. La propuesta es de un solo uso y no se persiste. Android guarda/ejecuta y genera la confirmación final. Si hay varios timers, la cancelación singular sigue sin elegir uno arbitrariamente; pedir cancelar todos sí permite proponerlo. Listar consulta Android, no inventa estado desde el LLM.

La confirmación reduce acciones por clasificación errónea; no certifica la seguridad ni fiabilidad del reconocimiento. La conversación es de **un solo turno**, sin historial general, memoria persistente, Internet, control doméstico o datos fiables en tiempo real. La calidad factual limitada del modelo sigue pendiente. No usar sus respuestas para decisiones críticas.

## Iniciar cuando se autorice prueba de voz

```powershell
adb shell su -c '/data/local/doha-voice/start-andromeda-ai.sh pilot'
```

Esperar «IA local preparada», abrir Andrómeda y elegir cinco minutos. La preparación no abre el micrófono. El backend espera conexión hasta 60 s. UI indica **IA local**, no «reglas», y pausa captura durante inferencia/TTS/alarma. Silenciar, salir de pantalla o el límite temporal detienen captura. El menor límite de app/backend prevalece. Máximo 24 activaciones en este piloto IA.

Ejemplos:

- «Andrómeda, cómo te llamas» → respuesta del LLM.
- «Andrómeda, avísame dentro de dos minutos» → propuesta de 120 s. Esperar respuesta completa; «Andrómeda, sí» → creación y confirmación reales de Android.
- «Andrómeda, quita todas las cuentas regresivas» → propuesta. «Andrómeda, sí» → cancelación real.

El servidor vive solo en el ensayo. Worker independiente comprueba batería <40 °C y memoria disponible >=500000 KiB; preparación exige <38 °C y >=1000000 KiB. Tope de worker: 420 s. Son guardas experimentales, no protección de carga 24/7 ni monitor térmico certificado. Clave aleatoria privada y temporales se retiran al cerrar. No cambiar tokens de la app ni exponerlos.

## Validación realizada

- 30 pruebas Python: validación estricta de propuestas, límites, confirmación obligatoria y única, rechazo, vencimiento, conversación/listado y regresiones anteriores.
- APK compilado e instalado; instrumentación Android de temporizadores aislados sin micrófono/sonido.
- Cinco textos públicos procesados por el LFM real en el Moto: saludo, crear 120 s, cancelar todos, negación y consultar cantidad. Todos pasaron con el prompt revisado; la primera versión falló listado y fue corregida. Es una muestra pequeña, no una medida de fiabilidad general.
- Última serie: primer turno frío 25.024 s, con calentamiento 3.910–5.048 s por texto. La preparación se hace antes de abrir micrófono; no oculta ese coste inicial.
- Audio sintético español atravesó el detector, ASR, modelo real y confirmación: creación de 120 s, cancelar todos y conversación. Solo se verificaron intenciones JSON; no había cliente Android ejecutándolas ni se crearon alarmas reales. 6.564/6.271 s desde activación hasta propuesta de crear/cancelar; 1.179/1.067 s para confirmación y emisión del intento final. Esto no mide latencia real de TTS, voz humana ni ruido doméstico.
- MemAvailable mínimo 1192044 KiB, RSS máximo del servidor 734768 KiB, batería máxima 26.1 °C. El cierre retiró el servidor. No se abrió micrófono real en esta integración.

Repetición automática sin micrófono: generar frases con `FixtureInstrumentation` y ejecutar `start-andromeda-ai.sh test`. Las frases son públicas, no grabaciones del usuario. `probe_local_ai.py` y `probe_stream_ai.py` usan LLM real; `test_local_ai.py` usa dobles de prueba.

Siguiente: una sesión autorizada con voz real usando este coordinador IA, no `start-andromeda-pilot.sh`. Verificar creación/cancelación en el almacenamiento Android antes de declararlo exitoso.
