# Diagnóstico de captura para «silencio» · 0.10.3 instalada

**Ensayo pospuesto por petición expresa del usuario; no iniciar ni ampliar otra prueba.** Siguiente bloque activo: alarmas, recordatorios y búsqueda web (`ACCIONES-ANDROMEDA.md`). Los datos y la discrepancia acústica de abajo se preservan, no se resuelven por cambiar de prioridad.

**Estado actual: el usuario oyó la voz y percibió que se calló tras pedir silencio; los contadores no registran corte por comando.** Primer control con voz fija 0.10.3: ambas fuentes `PHASE_END`, cero coincidencias/cortes registrados. No equiparar ausencia de callback con que el usuario la oyera seguir: falta aclarar inmediatez y alcance por fase, y distinguir corte por orden de final de fase u otra pérdida de salida audible. Fuente 6 con señal/hipótesis sin endpoint normal; fuente 7 casi sin señal/palabras, AEC habilitado y sin silenciamiento informado por Android. El control quieto 0.10.2 reconoció ambas. Causa exacta/AEC no demostrados, **ni reparación ni fracaso acústico de este control certificados**. Sin nueva ventana, modificación/reinstalación ni lectura del teléfono tras este feedback; detalles al final.

## Motivo

0.10 y 0.10.1 fallaron en uso humano al interrumpir TTS. En 0.10.1 se leyó PCM y el sistema no informó micrófono/cliente silenciado; no hubo palabras finales ni callback. Faltan señal detallada e hipótesis parciales. No atribuir una causa solo al estado de AEC, a «timer», al LFM o al usuario. Evidencia real: `SILENCIO-ANDROMEDA.md`.

## Control quieto instalado, solo inicio manual

- Un único ensayo manual de **hasta 20 s**, con fin automático y parada visible. Captura solo tras selección explícita del usuario en la app en primer plano; no activación por ADB ni background/autostart.
- Sin LFM, backend de voz, conversación, síntesis/reproducción ni acciones/timers. Usa únicamente Vosk/los pesos ya existentes; no otro modelo, descarga ni nube.
- Dos fases visualmente identificadas de unos 6 s: fuente 6 (`VOICE_RECOGNITION`) y fuente 7 (`VOICE_COMMUNICATION`). En cada una el usuario dice «silencio» una vez cuando indique captura activa. Margen de preparación/cambio dentro de los 20 s; si no cabe, detener con fallo, no ampliar. Cambio con captura cerrada durante 500 ms y reserva final de 500 ms. La segunda fuente intenta el AEC local de su sesión, como 0.10.1; la primera no lo habilita explícitamente.
- Detener y **liberar** por completo el primer AudioRecord antes de construir el segundo. En el código conversacional actual queda otro AudioRecord inicializado aunque parado durante TTS: no se ha probado que esto cause el fallo, pero el control debe eliminar ese factor. Ningún servicio conversacional/de tono puede coexistir con el control.
- Mantener guardas de permisos, memoria, temperatura, llamadas y salida de la app; no cambiar modo global de audio, volumen, altavoz/rutas, carga, HOME, swap o conectividad.
- Un reconocimiento positivo solo incrementa un contador diagnóstico: no envía `tts_interrupted`, crea conversación ni autoriza/ejecuta acciones. No retirar validación de segmento completo/confianza para fingir un acierto.

## Medición mínima sin voz/textos persistidos

Retener como máximo **dos resúmenes de fase en RAM**, sustituidos solo al solicitar otro control: fuente solicitada/real, frecuencia/canales si disponibles, inicio/fin de captura, frames, muestras no nulas, saturación, amplitud/RMS agregados y máximo retraso observado. RMS tiene suelo de -120 dBFS (`rms_mdbfs=-120000`), no una medición infinita. El retraso observado compara timestamps y frames leídos; no certifica pérdida de muestras. Estos son indicadores, no prueba de identidad o de que toda intención se oyó.

Contar endpoints, cantidad máxima de palabras parciales/finales y detección exacta del comando; el texto ASR solo transitorio en el proceso. Hipótesis parciales **no ejecutan el comando**: pueden omitir negaciones o relato posterior. Al finalizar captura, puede inspeccionarse el resultado pendiente únicamente como diagnóstico, nunca como orden tardía para otro audio.

No WAV, transcripciones, tema, respuestas TTS ni hashes de contenido humano; no volcar vistas/contexto ni una base de conversaciones. Lectura posterior mediante ruta específica numérica, si el proceso sigue vivo. No reinstalar/instrumentar sobre la RAM de esa prueba antes de recoger resultados.

## Interpretación antes de otra historia

- Si ninguna fuente obtiene señal/palabra en silencio, investigar captura/ruta/formato/ciclo de vida, no cambiar el LFM ni pedir otra historia.
- Si 6 funciona y 7 no, hay una diferencia de la fuente/procesamiento a controlar; no afirmar automáticamente fallo de hardware o eficacia AEC.
- Si ambos controles funcionan, acordar un ensayo aparte con reproducción pública fija; comparar señal/endpoints/parciales y retorno del corte. No declarar probado barge-in por el control silencioso.
- Si hay hipótesis/comando pendiente pero no endpoint durante sonido, aislar cierre de reconocimiento/latencia; no ejecutar cualquier parcial ni aceptar fragmentos de negaciones/citas.

## Uso de este diagnóstico de mantenimiento

1. Doble toque → **Micrófono → Diagnóstico de captura · 20 s → Iniciar diagnóstico**.
2. No habrá voz ni tonos. Al aparecer **MICRÓFONO ACTIVO · FASE 1/2**, decir «silencio» una vez y hacer pausa.
3. Esperar el cambio visual a **FASE 2/2** y decirlo una vez de nuevo. Termina solo; Detener/Salir también cancelan.
4. Avisar al asistente al terminar. **No reiniciar, instrumentar o reinstalar** antes de recoger la instantánea numérica; se pierde con el proceso.

No hace falta backend ni preparación ADB. El menú es de mantenimiento/piloto, no un requisito del uso cotidiano final 100 % por voz. Se puede ver el resumen en Micrófono → Ver diagnóstico numérico. Lectura técnica solo de datos tipados:

```sh
adb -s ZY227BCQ85 shell dumpsys activity local.doja.voice/.MainActivity capture_probe_metrics
```

Devuelve `active`, `recording`, número de intento, estado y como máximo dos fases; nunca vistas, charla, texto ASR o audio. `attempt=0`, `active=false`, `recording=false`, `phases=[]` indican que aún no comenzó ningún control en ese proceso (el estado inicial es `PREPARING`, no prueba de preparación activa). Registrar solo esa línea tras el ensayo; no tomar un dump genérico de UI/transcripciones.

## Implementación y validación

`CaptureProbeService` es servicio no exportado, en primer plano, no sticky, sin arranque desde boot ni endpoint. Solo se acepta inicio con actividad visible y sin VoiceService/RingService/listener/captura ajena de la app. Mantiene `AudioInputGate` todo el control y cierra/libera un AudioRecord antes de construir el siguiente. Repetir START mientras está activo no renueva el reloj ni inicia otro hilo. Inicio exige permiso, MemAvailable >=1200000 KiB y batería <38 °C; ejecución revalida >=500000 KiB, batería <40 °C, llamadas y visibilidad. Watchdog a 20 s solicita cierre del recorder desde otro hilo incluso si el decodificador está ocupado; captura y limpieza final se distinguen en el estado. Tiempos físicos y comportamiento del driver todavía deben medirse, no se certifican por las políticas simuladas.

El servicio no construye TTS, sockets, inferencia ni ejecutor/almacén de timers. Una alarma aceptada que venza prevalece y cancela el control; no se cancela el timer. Se usa el Vosk Android/modelo español ya existente. Audio/JSON de ASR solo transitorios dentro del proceso y susceptibles de swap; no se guardan WAV, textos, hashes de contenido ni conversación. Parciales se consultan aproximadamente cada 200 ms; el final pendiente se inspecciona solo después de cerrar captura. UI/notificación indican entonces «Micrófono apagado · analizando fase», no mantienen el aviso de captura durante el cálculo final. Solo cuentan, nunca actúan ni cortan otra salida.

**251 tests Python / 570 aserciones JVM, build/firma y diff-check pasan.** Incluyen techo 20 s, orden 6→7, rechazo de solapamiento/repetición/falta de margen, fase incompleta no aceptada, PCM16/signo/RMS, parciales frente a finales/pendientes y copias numéricas sin voz/textos. Pruebas de aislamiento de fuente no sustituyen medir hardware.

En el Moto pasan `test-capture-probe.ps1`: menú de cinco entradas, elegir diagnóstico solo muestra confirmación y **no abre captura**; dump específico sin vistas/transcripciones; Vosk real en WAV públicos detecta silencio con/sin nombre y rechaza negaciones/relatos, distinguiendo hipótesis parciales y finales. `source_actual=-1` en esos fixtures: **no son prueba física de las fuentes 6/7**. Sin AudioRecord/altavoz/LFM/servicio de diagnóstico real. UI también pasa (ocho estados, 35 dibujos/1200 ms en la compilación final), y contrato de timers aislado sin sonido; timers/recibos reales intactos. Modelo ASR liberado antes de informar éxito.

## Instalación y estado antes del primer ensayo

APK final instalado SHA-256 `efb2b84ee08d5ded600c63a995e37ea448f03565325a8e3ca8ff20b186757f29`; backup 0.10.1 `artifacts/android-voice/pre-capture-diagnostic-v0102-installed-v0101.apk`, SHA-256 `fac24e9ebcf8200576522fcdab4891c6344a92e6ff4ec04071de1739e73f14cd`. Backend no modificado: adaptador de timer `9ed27501ba85e9aa8c3ab02aa7ddec76a289b190a621414eb05c335249b7fcdc`. Instalación `-r`, sin borrar datos ni cambiar HOME/SELinux/carga/swap/conectividad. Pérdida de RAM autorizada, no exportada. El ajuste final del indicador de micrófono se instaló y revalidó antes de entregar, verificando aún intento 0/sin fase humana para no sobrescribir un ensayo en curso.

Preflight 1462660 KiB disponibles/22,8 °C, sin servicios/puertos de voz ni timers pendientes; muestra final 1634916 KiB/22,3 °C, app/reloj visible y HOME Motorola/SELinux Enforcing/SSH 2222/web 8080 preservados. Baseline del dump específico: `active=false`, `recording=false`, `attempt=0`, `end=PREPARING`, `phases=[]`. Timers/recibos conservan hashes `3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793` / `f78347ba7be7de44da99dd0097cb4ae797e409c6c6cda8adeaa70b47218b0030`.

Evidencia ignorada: `artifacts/android-voice/capture-probe-synthetic-device.txt`. Los 35 WAV públicos generados se retiraron del teléfono; pesos privados ASR y datos persistentes intactos. No hubo inferencia LFM, captura/reproducción, commits/pushes/merges ni activación del control desde ADB. **Primer ensayo humano pendiente del botón explícito; no afirmar reparación ni comparar acústicamente las fuentes todavía.**

## Primer control humano 0.10.2 · ambas fuentes reconocen sin reproducción

El usuario confirma «listo» después de la selección manual. Lectura posterior exclusivamente de `capture_probe_metrics`: **intento 1, COMPLETE, active=false, recording=false**, dos fases completas. No reiniciar/instrumentar/reinstalar, cambiar configuración ni repetir el control para recoger estos resultados.

| Dato observado | Fuente 6 | Fuente 7 |
|---|---|---|
| Fuente solicitada/real | 6 / 6 | 7 / 7 |
| PCM cliente/dispositivo, canales | 16000 Hz / 16000 Hz, mono | 16000 Hz / 16000 Hz, mono |
| Inicio desde comienzo del control | 1015 ms | 7861 ms |
| Captura hasta cierre | 6087 ms | 6128 ms |
| Frames / muestras no nulas | 94400 / 91663 | 93440 / 49852 |
| Pico absoluto / saturaciones | 20692 / 0 | 12936 / 0 |
| RMS agregado | -26,133 dBFS | -41,934 dBFS |
| Endpoints finales / palabras máximas | 1 / 1 | 1 / 1 |
| Coincidencias finales del comando | **1** | **1** |
| Palabras/comandos del final forzado pendiente | 0 / 0 | 0 / 0 |
| Consultas parciales / máximo de palabras | 27 / 0 | 27 / 0 |
| Máxima llamada de decodificación | 176 ms | 192 ms |
| Máximo retraso observado | 2560 frames | 2880 frames |
| AEC disponible/habilitado | sí / no | sí / sí |
| Micrófono/cliente silenciados | no / no | no / no |

La última captura cerró aproximadamente a los **13989 ms** desde inicio (7861+6128), no una medición del tiempo total de limpieza. No se agotó el presupuesto ni se precisó convertir un resultado pendiente en acción; no hubo acción. No se guardaron palabras ASR, audio, tema, respuestas ni hashes de contenido. Datos tipados preservados en `artifacts/android-voice/human-v0102-capture-first-window.json`, ignorado por Git.

**Conclusión limitada:** ambos caminos básicos capturan una señal que permite reconocer el comando y generar endpoint en silencio, incluida la fuente 7 con AEC marcado activo. No hay evidencia en este control de un micrófono físicamente incapaz, palabra inaceptable o fallo general del formato PCM. Esto no descarta errores intermitentes ni con otros estados. Los niveles RMS son de dos intervenciones secuenciales, no una señal acústica calibrada idéntica; no atribuir los ~15,8 dB de diferencia exclusivamente a AEC, hardware o distancia. El retraso observado tampoco demuestra pérdida de muestras.

El fallo previo ocurre en un contexto diferente: reproducción y otro capturador conversacional inicializado/parado, además de carga combinada. Este control retiró todos esos factores a la vez, por lo que **todavía no identifica cuál causó el fallo ni certifica barge-in**. Siguiente propuesta: una prueba aparte con reproducción pública fija y sin LFM, manteniendo un solo capturador; si funciona, después aislar conmutación/carga. Eso requiere ampliar/instalar el diagnóstico y autorización de sonido/ensayo, **no está implementado ni activado**. No tocar volumen/modo/ruta global, aceptar cualquier parcial, cambiar el modelo ni pedir otra historia idéntica como supuesto arreglo.

Reposo posterior solo lectura: sin servicios/puertos de voz, app visible, SSH/web escuchando, timers/recibos con los hashes de antes. Muestra 1633720 KiB disponibles/22,2 °C. Captura y ASR del control terminados; no quedó una segunda ventana ni inferencia preparada.

## Ampliación 0.10.3 autorizada · reproducción fija sin LFM

El usuario responde «sí» a ampliar e instalar la comparación con sonido fijo. Actualización código 15 con backup de 0.10.2; números del control quieto ya preservados, no audio/transcripciones. **El permiso no abre captura ni reproduce sonido desde ADB**: inicio solo al elegir la nueva opción y confirmar en la app. El modo quieto permanece separado y por defecto `with_playback=false`.

Se mantienen el techo total de **20 s**, dos fuentes 6→7 de unos 6 s, cierre/liberación entre ellas, guardas de recursos/visibilidad/llamadas y parada manual/salida. Sin LFM ni backend. Un solo capturador de la app, sin el AudioRecord conversacional inicializado/parado; esto aísla más variables, no certifica que reproduzca todos los detalles del fallo original.

### Voz pública empaquetada, no síntesis durante la prueba

- Frase fija: **«Vamos a preparar la comida y cerrar la puerta.»** No contiene el comando de silencio. Generada con la voz española **offline ya existente**, a archivo/sin altavoz ni micrófono, desde el fixture público `ordinary`.
- PCM16 little-endian, 16 kHz mono, **89350 bytes / 2,7921875 s**; SHA-256 `c1013c5ab999b12561a7c461aa91063806207ec6c0cd510c170f21d63a5e826a`. Es contenido público fijo, no un hash de voz humana.
- `config/capture_probe_audio.json` fija contenido/formato/tamaño/huella. Build verifica la copia local y la empaqueta en el APK firmado como `assets/capture-probe-public.pcm`; el runtime vuelve a verificar longitud/huella y rechaza sustituciones. No acepta rutas/media arbitrarios ni descargas.
- El ensayo reproduce ese PCM mediante AudioTrack estático en el flujo multimedia, repitiéndolo solo dentro de cada fase. No usa TTS en vivo, de modo que no añade su generación a la captura. No cambia volumen, modo, ruta o audio focus; lee volumen y tipo de salida para interpretar resultados. Si multimedia está silenciada/volumen 0, rechaza el inicio con guardia en vez de subirlo.
- Reproducción reproducible en PC, sin activar nada en Android: `python android-voice/prepare-probe-audio.py --from-fixed-wav artifacts/android-voice/public-playback/capture-probe-public.wav`. Solo escribe si el PCM coincide con la huella pública fija; no actualizar el lock automáticamente ni sustituir audio humano. Build usa `verify-probe-audio.py`, sin generación/descarga implícita. WAV/PCM locales de referencia permanecen en `artifacts/` ignorado.

### Qué se detiene y qué se mide

Si aparece un **resultado final completo** de «silencio»/«Andrómeda, silencio», validado con la misma confianza mínima 0,8 y mientras la salida de esa fuente/ventana sigue vigente, se pausa/detiene/libera **solo ese AudioTrack de prueba**. Política de un uso, fuente/ventana/vencimiento: un final viejo o ajeno no corta la siguiente fase. Hipótesis parciales y final forzado tras cerrar captura solo cuentan; nunca detienen una salida.

La captura continúa hasta completar la fase para medir sus resultados; la fase 2 comienza su propia voz fija de nuevo, dentro de la misma autorización de dos fases. Si no se reconoce el comando, la salida termina por fin de fase, guardia, Detener/Salir o watchdog, no por supuesto reconocimiento. Al cancelar, se solicita cierre de voz y captura sin depender de que termine la decodificación. El control no toca timers, otra app ni conversación.

Nuevos campos numéricos/enums: modo solicitado/iniciado/detenido, motivo `COMMAND`/`PHASE_END`/guardia/error, duración de salida, número de cortes de comando, máximo contador de reproducción observado, tipo de dispositivo y volumen actual/máximo. **`COMPLETE` solo significa fases de captura terminadas, no que se haya cortado por voz.** `playback_command_stops`/`playback_end=COMMAND` son acuses software, no verificación acústica; fin de fase no es éxito de interrupción. El máximo playback head no se presenta como duración acústica ni total de voz escuchada. No se guardan PCM/ASR humanos ni identificadores del dispositivo de audio; solo su tipo numérico.

### Uso del modo con voz fija

1. Doble toque → **Micrófono → Diagnóstico con voz fija · 20 s → Iniciar prueba con voz fija**.
2. Cuando suene la voz y veas **MICRÓFONO ACTIVO · FASE 1/2**, decir «silencio» una vez y hacer pausa.
3. Esperar que comience la voz con **MICRÓFONO ACTIVO · FASE 2/2** y repetir una vez. No confundir «espera FASE 2» del cambio con captura activa.
4. Termina solo; también se puede Detener/Salir. Avisar «listo» e indicar si se oyó la voz y se cortó en cada fase. Recoger después solo `capture_probe_metrics`, sin reinstalar/instrumentar.

### Verificación de instalación, no de acústica

**252 tests Python / 596 aserciones JVM**, build/firma y diff-check pasan. Cubren asset modificado/truncado/sobredimensionado, control sin salida por defecto, entrada explícita de reproducción, fuente/vencimiento/consumo único, parciales y tail sin actuación, integridad numérica y no cambiar ajustes globales. Android: menú de seis opciones, confirmaciones separadas sin iniciar servicio/audio, PCM firmado verificado, ASR de la frase pública no detecta el comando por sí sola; regresiones de silencio/negaciones/relatos, UI (ocho estados, 34 dibujos/1200 ms) y contrato de timers aislado pasan. **Sin AudioRecord, AudioTrack audible ni LFM en estas pruebas**; hardware, reproducción y cortes reales pendientes del usuario.

APK instalado SHA-256 `cd08ac7c33284e09a7b807d5705ffaf62b21715e50728bd309e0b8ecde68e6f2`; backup de 0.10.2 `artifacts/android-voice/pre-playback-diagnostic-v0103-installed-v0102.apk`, SHA-256 `efb2b84ee08d5ded600c63a995e37ea448f03565325a8e3ca8ff20b186757f29`. Backend/ASR/pesos generativos/configuración de carga/swap/HOME/conectividad no modificados. Datos de timers/recibos conservan las huellas anteriores.

Baseline final del dump: `active=false`, `recording=false`, `attempt=0`, `end=PREPARING`, `playback_mode=false`, `phases=[]`. Sin servicios/puertos de voz, app/reloj visible, HOME Motorola/SELinux Enforcing/SSH 2222/web 8080 intactos; muestra 1653196 KiB disponibles/22,1 °C. Se retiraron los 35 WAV públicos temporales del teléfono, no el asset público firmado ni los pesos ASR. No hay otra ventana humana/backend preparado, captura ni sonido iniciados, ni commits/pushes/merges. **El control quieto pasa, el fallo durante TTS sigue abierto y este nuevo control no se da por aprobado hasta ensayarlo.**

## Primer control humano 0.10.3 · con reproducción, cero cortes registrados

Tras «listo», se recoge **solo** `capture_probe_metrics`, sin instrumentación/reinstalación, apertura de otra captura ni dump genérico. **Intento 1, COMPLETE, playback_mode=true, active=false, recording=false**. Son dos fases finalizadas, **no éxito de interrupción por sí solo**. `playback_started=true` y `playback_stopped=true` en ambas; el motivo registrado fue `PHASE_END`, nunca `COMMAND`. Al recogerlos todavía faltaba feedback; posteriormente el usuario informa salida audible y que dejó de hablar tras pedir silencio, sin precisar latencia ni distinguir fases. Preservar ambos datos, no deducir que siguió hablando solo por ausencia de callback.

| Dato observado | Fuente 6 | Fuente 7 |
|---|---|---|
| Fuente solicitada/real | 6 / 6 | 7 / 7 |
| PCM cliente/dispositivo, canales | 16000 Hz / 16000 Hz, mono | 16000 Hz / 16000 Hz, mono |
| Inicio desde comienzo del control | 993 ms | 7888 ms |
| Captura hasta cierre | 6125 ms | 6606 ms |
| Frames / muestras no nulas | 94400 / 91786 | 89280 / 1156 |
| Pico absoluto / saturaciones | 32767 / 1 | 246 / 0 |
| RMS agregado | -13,535 dBFS | **-81,027 dBFS** |
| Consultas parciales / máximo de palabras | 26 / 12 | 26 / 0 |
| Endpoints finales / palabras máximas | 0 / 0 | 1 / **0** |
| Palabras del final forzado pendiente | 18 | 0 |
| Comandos finales / pendientes | **0 / 0** | **0 / 0** |
| Motivo de parada de reproducción | **PHASE_END** | **PHASE_END** |
| Cortes por comando | **0** | **0** |
| Duración de salida registrada | 5967 ms | 5723 ms |
| Máximo playback head observado | 94080 frames | 91520 frames |
| Tipo de salida / volumen multimedia | 2 (altavoz) / 15 de 15 | 2 (altavoz) / 15 de 15 |
| Máxima llamada de decodificación | 182 ms | 164 ms |
| Máximo retraso observado | 2560 frames | 2240 frames |
| AEC disponible/habilitado | sí / no | sí / sí |
| Micrófono/cliente silenciados | no / no | no / no |

La última captura cerró aproximadamente a los **14494 ms** desde inicio (7888+6606); no es una medición de la limpieza total. Las fases no duraron exactamente 6000 ms: el bucle y la liberación registran 6125/6606 ms, y las salidas 5967/5723 ms. Conservar esos valores, sin presentar la política simulada como garantía de corte físico exacto a los 6 s. Ninguna captura alcanzó el techo total de 20 s; esto no valida tiempos de watchdog en otros estados. El playback head es una observación software, no confirmación de audibilidad ni reloj acústico.

### Interpretación limitada y siguiente decisión

- **No hay corte registrado durante reproducción.** El comando tampoco aparece como coincidencia en el final pendiente; no es un comando detectado que solo haya llegado tarde. No hay palabra final válida de confianza baja que justifique bajar el umbral.
- La fuente 6 contiene señal y cantidades de hipótesis, pero no genera endpoint normal dentro de la fase. Las 18 palabras al forzar cierre **no identifican** voz humana frente a reproducción/eco ni el contenido; no se exportó texto y no se deben convertir esos parciales/tail en órdenes.
- La fuente 7 tiene señal **casi nula, no exactamente cero**: 1156 de 89280 muestras no nulas, RMS -81,027 dBFS y pico 246. El único endpoint está vacío; no es una orden reconocida. Android no informa silencio, lo cual no demuestra que la voz humana se capture bien ni descarta procesamiento de la ruta.
- La ausencia de reconocimiento/callback en este control aparece **sin LFM y sin otro AudioRecord conversacional inicializado**. Esos factores no son necesarios para observar este resultado numérico; podrían contribuir en el piloto original y no se declaran descartados universalmente. Con el feedback posterior de parada percibida, no convertirlo en fallo acústico confirmado. Revisar discrepancia y captura/procesamiento antes de pedir otra historia o cambiar modelo.
- La diferencia con el control quieto y entre fuentes es una pista para investigar fuente/ruta/preprocesado, **no prueba causal de AEC, NS, hardware ni volumen**. AEC figura habilitado en fuente 7, no se midió su efecto aislado; las voces secuenciales no son una entrada calibrada idéntica. Volumen 15/15 es lectura, no cambio del agente; no bajar/subir ajustes globales automáticamente.
- Primero aclarar si la parada percibida fue inmediata y en qué fases, sin repetir el ensayo. Si se acuerda otro control, deberá resolver la discrepancia o separar explícitamente un factor del preprocesado/ruta de fuente 7, con un solo recorder y reproducción fija, conservando límites y resultados anteriores. **No está implementado, instalado ni autorizado un nuevo control**, ni un cambio de AEC/fuente/umbral/volumen; no repetir automáticamente este ensayo.

Evidencia tipada ignorada: `artifacts/android-voice/human-v0103-playback-first-window.json`. Solo números, booleanos y etiquetas fijas; no audio, textos ASR ni hashes de contenido humano. La app 0.10.3 y sus resúmenes permanecen sin reemplazar. En esta recogida no se inspeccionaron recursos/servicios/timers/HOME ni se recalcularon sus huellas: no atribuirles las comprobaciones históricas como una lectura posterior nueva. El dump confirma fin de captura y parada software de ambas salidas; no se preparó otro modelo/ensayo, tocó configuración, ejecutó timers ni realizó commits/pushes/merges.

### Feedback posterior · parada percibida, atribución pendiente

El usuario confirma que oyó la voz y que **no siguió hablando después de pedir silencio**. Se registra como percepción humana de parada, no como ausencia de sonido ni persistencia audible. No precisó si fue inmediata o unos segundos después ni el resultado separado de cada fase. El registro conserva sin alterar sus contadores: cero coincidencias/cortes, `PHASE_END` y salida software de 5967/5723 ms. Ambas fuentes de evidencia se mantienen: no descartar la percepción porque falte callback, ni presentar la coincidencia temporal como reconocimiento demostrado, porque cada fase también termina sola. No hay audio guardado que resuelva retrospectivamente el instante audible. Etiquetas fijas del artefacto: `PERCEIVED_STOP_AFTER_COMMAND`, fases `UNSPECIFIED`, atribución `UNCONFIRMED`. Siguiente pregunta sobre inmediatez, **sin nueva captura, reinstalación o prueba**. Los fallos humanos anteriores durante TTS siguen documentados y este feedback no los revalida.
