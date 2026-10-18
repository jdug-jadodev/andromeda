# Conversación local 0.4 · prueba personal acotada

## Alcance vigente

Tras elegir LFM, el usuario pidió iniciar la integración. APK **0.4.0-conversation-trial / código 4**, instalado en el Moto sin borrar datos. APK 0.3 respaldado en `artifacts/android-voice/pre-v04-*.apk`; backend/worker previos también respaldados en el Moto. HOME sigue Motorola; SSH :2222, web :8080, datos, carga y swap sin cambios.

- Conversación española corta: Vosk → **LFM2.5 1.2B Instruct QAD Q4_0** → voz española **offline** Android. No otro modelo ni inferencia remota.
- **Sin registro ni ejecutor de herramientas** en esta ruta. Ni timers conversacionales, alarmas, recordatorios, búsqueda, clima hablado ni dispositivos. Clima visual Open-Meteo y gestión manual de timers siguen separados.
- Activación de sesión exclusivamente manual, 90 s o 5 min. Abrir la app o preparar el backend **no abre el micrófono**. No arranque automático ni escucha permanente.
- Di el nombre, espera la cara y habla brevemente; ventana ASR de 8 s. Para el siguiente turno vuelve a decir el nombre o usa «Hablar ahora». Durante cálculo/TTS se detiene AudioRecord; después vuelve a la espera del nombre.
- Sigue disponible **Solo activación y transcripción (0.3)**, con su protocolo restringido original, sin LLM/TTS. No se habilitó IA dentro de wake-only.
- Memoria en RAM: hasta seis pares recientes completos, presupuesto real de entrada <=1536 tokens mediante plantilla/tokenizador del servidor, salida <=96 y contexto 2048. Expira a la hora de inactividad humana o antes al cerrar la sesión/proceso. No hay selector ni recuperación entre sesiones; temporizadores persistentes separados.
- Errores conocidos de identidad/recuerdo siguen presentes y aceptados para este ensayo; no se afirman corregidos. Un filtro conservador de **salida**, no de intenciones del usuario, rechaza llamadas estructuradas y algunas confirmaciones falsas evidentes. No verifica toda la semántica ni permite habilitar acciones.

## Plazo, cancelación y canal

- Protocolo 2 exclusivo `conversation_only`: **TLS con certificado fijado en la app**, más credencial de cliente distinta de la del piloto histórico. El token se envía solo tras verificar el certificado; fuente sintética/micrófono separadas y ambas autenticadas. Escucha solo en 127.0.0.1:11436; inferencia en :11435. No endpoint LAN nuevo.
- Claves/certificado privados bajo `/var/lib/andromeda-conversation` (directorio 700, archivos 600, UID 10218); copia del token de cliente en archivos privados de la app, sin imprimirlo. El pin público está en `config/conversation_voice.json`; no se acepta cualquier certificado por ser loopback. Ese JSON describe el contrato fijo (solo el pin alimenta el build); no activa un modo ni permite ampliar presupuestos editándolo. Caducidad del certificado: un año; renovar y actualizar pin antes de vencer. No es el HMAC con desafío propuesto para el protocolo histórico. Handshake de autenticación con plazo absoluto de cuatro segundos, no renovable por bytes parciales.
- Android `elapsedRealtime` y Debian `CLOCK_BOOTTIME`, comprobados en handshake. PCM lleva marca de captura obtenida de AudioTimestamp BOOTTIME; si el driver no la proporciona o llega audio atrasado se detiene la prueba, no se inventa una marca de captura. **La captura real y ese driver aún requieren ensayo humano.**
- Inicio del plazo: alineación de la última palabra Vosk trasladada al reloj de captura, con margen conservador de 200 ms. Es una **estimación**, no una medición independiente del último audio humano. En los WAV públicos fue ~502–512 ms anterior al final de señal medido por umbral.
- Un único vencimiento de respuesta `fin_estimado + 30000 ms`. Cálculo máximo 22 s y nunca después de `fin_estimado + 26500 ms`; no se reinicia el plazo entre plantilla, tokenización y generación. Se reserva margen para TTS y se rechazan resultados truncados/tardíos.
- ID y secuencia por turno, un solo turno activo, rechazo de TTS duplicado/cancelado/tardío. Android comprueba antes de encolar y en `onStart`; watchdog para TTS. El callback **no certifica el inicio audible útil** ni ofrece garantía absoluta del planificador Android.
- El actor mantiene lectura de controles durante la inferencia en otro hilo. Silenciar/salir cierra el canal y detiene captura/TTS; al desconectarse o vencer el plazo el backend termina **solo su servidor dedicado** (verifica UID, ejecutable, modelo, clave de workdir y PID/starttime). No toca timers ni mata procesos globalmente por nombre.
- Guardia cada segundo: batería <40 °C y MemAvailable >=500000 KiB; preparación exige <38 °C y >=1200000 KiB. Supervisor de inferencia acotado a 660 s y espera de cliente <=300 s. No sustituir puertos ocupados. Estas guardas no certifican SoC, carga protegida ni 24/7.
- No se persiste audio humano/historial en esta implementación. Última transcripción/respuesta en memoria; buffers pueden entrar en swap. Diagnóstico backend sin textos humanos ni tokens. WAV de prueba contienen solo frases públicas; salida TTS sintética temporal eliminada.

## Evidencia ejecutada en el Moto

Las pruebas de infraestructura **no son aceptación de voz real ni corrección semántica del modelo**.

- 145 tests Python y 78 aserciones JVM pasan; incluyen límites, cancelación concurrente, respuestas fuera de plazo, duplicados, contexto por tokens, filtro de salida, autenticación acotada y separación wake-only/conversación.
- Build, firma e instalación verificados; instrumentación UI: ocho estados, 35 dibujos/1200 ms, timers intactos, sin audio/modelo iniciados por la prueba visual.
- Regresión final del modo wake-only original: **nueve casos sintéticos pasan** con Vosk real (incluido retorno tras >8 s de silencio), sin micrófono/TTS/acciones. Fixtures públicas retiradas al cerrar los ensayos.
- TTS offline generó 17 fixtures públicas sin reproducirlas. `ConversationInstrumentation` usó tres WAV a cadencia real a través del **TLS/protocolo 2 real**, con Vosk, servidor LFM, síntesis offline y presentación UI. No abrió AudioRecord ni reprodujo audio; timers sin cambios.

| WAV público | Fin de señal → primer PCM sintetizado | Fin estimado ASR → primer PCM sintetizado |
|---|---:|---:|
| Cómo te llamas | 6.409 s | 6.911 s |
| Presentación Luis/gata Menta | 5.477 s | 5.982 s |
| Cómo se llama mi gata | 5.357 s | 5.869 s |

Preparación separada de servidor/prefijo: **9.976 s**. Respuestas reales: nombre Andrómeda correcto; ante el dato de Menta luego respondió **«Mi gata se llama Luna.»**, error conservado, no sustituido por respuesta esperada. Esta respuesta incorrecta **no cuenta como éxito útil de aceptación** aunque la síntesis sea rápida.

Ensayo conjunto breve: mínimo MemAvailable **1349812 KiB (~1.29 GiB)**, pico RSS servidor **738800 KiB (~721.5 MiB)**, pico RSS backend/Vosk **158292 KiB (~154.6 MiB)**, PSS app al final **49603 KiB (~48.4 MiB)**, batería máxima **23.0 °C**. MemAvailable refleja carga simultánea del teléfono, incluido TTS; no se midió PSS individual del motor TTS ni pico de todos los procesos. No sumar estas métricas como si fueran memoria privada disjunta. No es ensayo sostenido.

Cancelación sintética **durante inferencia**: desconectar el canal dejó de admitir conexiones al servidor dedicado en **31 ms**, sin voz ni commit de memoria. Desconexión al final del ensayo normal: **14 ms**. El supervisor también terminó; ninguna de estas mediciones es todavía pulsación humana del botón Silenciar.

Confirmación TTS sintética con marca posterior al vencimiento: rechazada; inferencia cerrada en **38 ms**, sin síntesis ni commit. Repetición con el APK/backend finales y runner estricto: handshake de fuente micrófono rechazado por el backend sintético, tres turnos completados con las mismas respuestas (incluido error de Luna), **5.343–6.375 s** fin de señal → primer PCM y cierre en **33 ms**. Preparación **7.758 s**, MemAvailable mínimo **1374624 KiB**, pico servidor **740328 KiB**, backend **157168 KiB**, batería máxima **23.6 °C**; PSS app al final **43513 KiB**. UI final: ocho estados y **34 dibujos/1200 ms**, timers intactos. No convertir la repetición en evidencia de voz humana.

APK final instalado coincide por SHA-256 con el compilado: `6c9bae7e6d0b0cdc347f391eef9503f447f6c1ee2683cd3ce4b51340f548f52c`. Antes de preparar el ensayo humano: sin listeners 11435/11436, sin VoiceService/RingService ni workdirs temporales de inferencia; web :8080 devuelve HTTP 200, SSH :2222 escucha y HOME sigue Motorola. Reposo: MemAvailable 1601716 KiB, batería 23.6 °C. Después se prepara manualmente una sola ventana `pilot 90`; **no abre captura** y se cierra si no llega cliente en cinco minutos. No atribuir voz humana a esta preparación.

Artefactos privados: `artifacts/android-voice/conversation-synthetic-{normal,cancel,late}-{android,backend}.txt`. Las trazas sintéticas contienen únicamente frases públicas. Los resultados de instrumentación requieren `INSTRUMENTATION_CODE: -1` y el nombre esperado del test, no solo exit code 0 de adb.

## Preparar la primera prueba humana

```powershell
adb -s ZY227BCQ85 shell su -c '/data/local/doha-voice/start-andromeda-conversation.sh pilot 90'
```

Espera a `conversation_ready` con `input_source=microphone`; la preparación **mantiene apagado el micrófono**. En el Moto: **Andrómeda → doble toque → Micrófono → Conversación LFM · sin acciones → Elegir prueba limitada → 90 segundos**. Si pide permiso, conceder y volver a seleccionar; no inicia automáticamente al concederlo. Di «Andrómeda», espera la cara y haz una pregunta corta. **Silenciar** debe apagar captura y voz, conservando timers. Salir también termina la sesión. Tras terminar o esperar cinco minutos sin cliente, hay que preparar de nuevo; no se reconecta ni reinicia el modelo solo.

Para repetir diagnóstico sin micrófono: preparar `start-andromeda-conversation.sh test 90` y ejecutar `android-voice/test-conversation.ps1 -Mode normal` (runner estricto, probado en Moto); otros modos: `cancel_inference` / `late_start`, cada uno requiere nueva preparación. Equivale a `am instrument -w -e mode ... local.doja.voice/.ConversationInstrumentation`. Generar fixtures con `FixtureInstrumentation` antes, retirarlas al acabar. No usar el piloto histórico con ejecutor.

Pendiente: respuesta **audible útil** con voz humana, driver de timestamps, retorno a espera, botón Silenciar real, ruido/distancia y recursos sostenidos. Máximo 30 s **no certificado**. No habilitar herramientas, escucha permanente ni 24/7 por estos ensayos.
