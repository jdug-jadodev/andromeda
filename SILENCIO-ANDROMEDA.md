# 0.10 · «Andrómeda, silencio»

**Prueba pospuesta expresamente por el usuario.** Priorizar ahora alarmas, recordatorios y búsqueda web; no repetir este diagnóstico ni bloquear aquel desarrollo con otra historia/ensayo acústico. La percepción de parada y la discrepancia con el registro se conservan sin resolver; no declarar certificada la interrupción. La aclaración de inmediatez pendiente debajo queda aplazada, no es el siguiente paso activo.

**Estado vigente: 0.10.3/código 15; el usuario percibe parada tras silencio, pero el registro marca fin de fase, no comando.** Ambas fuentes `PHASE_END`, cero coincidencias/cortes registrados; fuente 6 con señal/hipótesis sin endpoint normal y fuente 7 casi sin señal (-81,027 dBFS), sin palabras ni silencio informado por Android. El control quieto reconoció ambas. Aclarar inmediatez y fases sin pedir repetición: **no confirmar reparación ni fracaso audible de este control por los contadores; causa exacta/AEC no demostrados**. Captura/salidas apagadas según último dump; app/RAM preservadas, sin otra ventana/actualización ni lectura nueva del teléfono tras este feedback. `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`.

## Petición y alcance

El usuario reconfirma el cierre del timer y pide detener por voz a Andrómeda cuando habla o suena un timer, alarma o recordatorio. **0.10.0-silence-trial / código 12 instalada** sobre 0.9, sin borrar datos. El tono final del primer timer 0.9 no se deduce de «el timer cerró correctamente»: cierre aceptado; sonido todavía sin confirmación humana explícita.

- **Durante una respuesta:** corta el TTS actual, sin decir otra confirmación. Una respuesta ordinaria deja la misma charla disponible después de la pausa de 600 ms, sin repetir el nombre.
- **Durante anuncio de resultado de una acción/despedida:** corta voz, pero conserva el cierre ya decidido y espera el cierre TLS autenticado. No reejecuta, revierte ni cancela el timer aceptado.
- **Durante un timer que suena:** detiene el sonido/servicio actual, sin modificar otros timers pendientes ni los recibos de creación. Conserva su máximo de 30 s. Si coincide con una charla, el evento prevalece y detiene el piloto conversacional para no reconocer el tono como otra petición.
- **Alarmas/recordatorios:** listener y corte de salida reutilizables, pero esas funciones todavía no están implementadas. Sus salidas futuras deberán adoptar este contrato; no se añaden planificadores por esta petición.
- No es parada dura de todo el asistente. El control manual Silenciar/Detener ahora detiene voz/captura y RingService, sin cancelar timers pendientes. Cerrar una charla normal del piloto todavía apaga modelo/captura; el modo residente sigue siendo otro trabajo.

## Implementación y privacidad

La captura conversacional sigue pausada durante procesamiento/reproducción. **Excepción autorizada nueva:** durante reproducción se abre AudioRecord exclusivo para interrupción, con Vosk Android local; no envía ese audio al backend/LFM ni abre otra charla. `AudioInputGate` impide dos capturas activas de la app a la vez. Sin escucha de interrupción en reposo, arranque automático ni ampliación de pilotos 90/300 s, espera de cliente 300 s o supervisor 660 s.

`SilenceListener` usa los mismos pesos españoles `vosk-model-small-es-0.42` ya existentes en el Moto: copia privada Android de **14 archivos / 60286598 bytes**, verificados uno a uno contra el origen, no otro modelo descargado. Archivo de transferencia SHA-256 `d4d23942f4176827516ddd6b3c39eaad0ce6ed60a32f8946916df313bb2aece5`. SDKs Vosk 0.3.47/JNA 5.13.0 arm64 fijados por SHA-256 en `config/voice_interrupt_deps.json`; build sin descargas implícitas. Si faltan las bibliotecas: `python android-voice/prepare-speech-deps.py --download`. Licencias/orígenes: `android-voice/THIRD-PARTY-NOTICES.md`.

Reconocimiento general español, **sin gramática que fuerce ruido a la orden**. Acepta un segmento final completo de dos palabras, nombre + silencio, confianza mínima 0,8 por palabra y duración máxima de 4 s. No acepta «silencio» aislado, añadidos, negaciones ni relatos conservados en ese segmento. Cada salida tiene ID, vencimiento y consumo único; callback viejo/duplicado no corta otra salida. Ventana <=20 s TTS/<=30 s tono. Libera captura/recognizer/modelo al terminar el propietario. Sin audio/transcripciones humanas en disco/logs; reconocimiento y contexto en RAM, susceptible de swap.

Preparación: permiso de micrófono, MemAvailable >=1200000 KiB, batería <38 °C y ausencia de llamada/comunicación. Captura revalida cada segundo: >=500000 KiB y <40 °C. UI/notificación muestran cuándo está capturando, no solo que está preparado. Al terminar/expirar/fallar se cierra escucha; si falla durante un tono, este conserva límite y parada manual. Sin cambios de volumen/carga.

Backend conserva protocolos 6/8 y anuncia `speech_interrupt=true`; la captura conversacional nueva exige ese soporte antes de abrir el micrófono. Control TLS `tts_interrupted` exige turno actual y TTS iniciado. No guarda respuesta entera como escuchada ni oferta pendiente como autorización contextual para «no». Conserva el turno humano original; la interrupción no pasa por LFM. Callbacks TTS serializados en hilo principal; cierre de red asíncrono.

## Pruebas ejecutadas, sin micrófono ni altavoz

- **245 tests Python / 483 aserciones JVM**, build/firma y `git diff --check` pasan.
- `test-silence.ps1`: Vosk Android real sobre WAV públicos detecta «andrómeda silencio» y rechaza palabra suelta, negación, relato y petición de timer. Corte único de voz/sonido, ID ajeno rechazado, continuidad frente a espera de cierre `action_result`, preferencias/recibos intactos y liberación del modelo.
- UI final: ocho estados, 36 dibujos/1200 ms. Contrato Android de timers aislado también pasa; sin sonido/cambios en timers reales.
- `test-silence-conversation.ps1`: ASR/LFM/TTS a archivo reales, **control de interrupción simulado**, después «cierra la conversación» sin repetir wake. Un wake/un rearme; primer turno sin respuesta inventada, charla cerrada retomable/nueva vacía. ASR Android adicional cargado junto al LFM, después liberado. Primer PCM 7261 ms estimados, no latencia acústica.
- `test-timer-conversation.ps1 -Mode timer_create -InterruptFirst`: un turno crea timer **aislado** de 600 s con acuse Android; interrupción simulada del anuncio, cierre `action_result`, cero seguimientos. Timer aislado presente tras apagar modelo, luego limpiado antes de informar éxito; registros reales intactos. ASR Android adicional coexistente y liberado. Primer PCM 6298 ms estimados.
- En esas muestras: mínimos MemAvailable 1270768/1283624 KiB, RSS máximo de inferencia 736828/743360 KiB y batería máxima 22,4/22,6 °C. PSS de app al final 136072/125000 KiB. No sumar métricas solapadas, extrapolar captura/decodificación simultánea ni aprobar uso sostenido.

**Pendiente: ensayo humano supervisado de la frase mientras TTS/tono son realmente audibles.** No se abrió AudioRecord ni reprodujo sonido en estas pruebas. Se intenta AcousticEchoCanceler si el dispositivo lo permite; no se certificaron disponibilidad/eficacia, distancia, tiempo de corte, ruido ni ausencia de autoactivación por TTS/TV. ASR puede perder/separar palabras/negaciones; esto es corte de bajo riesgo, no autenticación de voz. WAV/control simulado no prueban interrupción acústica ni respuesta audible útil <=30 s. No hay nueva ventana humana preparada.

## Instalación, reversión y estado final

| Artefacto | SHA-256 |
|---|---|
| APK código 12 instalado | `c4e74ceca89f5dc1d04921d1fa838f2454768e01e40284ecfe4a4292a90f99e4` |
| `conversation_voice.py` instalado | `7362ad76d54cba5934bdbfeaac0260e8a392a301a548f115fbd31baa5f0ebb29` |
| Backup APK 0.9 | `3a7a05b2b8f7cb8c2d65c9b6be1b1352af230c6528d35930d6feef81b6eef6d9` |

Backup APK: `artifacts/android-voice/pre-silence-v010-installed-v09.apk`. Backend previo: `/data/local/doha-voice/backups/pre-silence-v010-20261018/conversation_voice.py`. Revertir solo con consentimiento, sin borrar datos y sin servicios/timers humanos pendientes; no restaurar XML ni eliminar pesos para revertir.

Antes de instalar/instrumentar: inspección solo lectura, sin servicios de voz/tono y `timers.xml` vacío por consumo natural del timer anterior, no cancelación de este agente. Hash final `3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793`; recibos reales conservados, hash `f78347ba7be7de44da99dd0097cb4ae797e409c6c6cda8adeaa70b47218b0030`. Registro vacío no prueba que el humano oyera el tono. Sin VoiceService/RingService ni listeners 11435/11436 al terminar; app/reloj en primer plano, HOME Motorola, SELinux Enforcing, SSH 2222 y web 8080 preservados. Muestra final 1603624 KiB disponibles/22,8 °C. Sin cambios de swap/carga/arranque ni commits/pushes/merges del agente.

Se retiraron únicamente los 35 WAV públicos generados y los dos archivos propios de transferencia del teléfono; copia ASR privada y datos de timers conservados. Para repetir sintéticos, regenerar fixtures con `.FixtureInstrumentation` primero y no instrumentar sobre una sesión/alarma/timer humano pendiente. Evidencia privada ignorada: `silence-synthetic-device.txt`, `silence-conversation-backend-20261018-191445.txt` y `timer_create-backend-20261018-191516.txt` en `artifacts/android-voice/`.

## Primera preparación humana 0.10 · terminó sin uso

El usuario pide «iniciemos la prueba». Se autoriza una sola preparación **`pilot 90 timer`**, sin abrir captura desde ADB, reinstalar, instrumentar ni repetir ventanas automáticamente. El usuario selecciona el piloto en la app. Propuesta: pedir una historia, interrumpirla con «Andrómeda, silencio» y, en la misma charla, pedir un timer de veinte segundos; cuando suene y esté activa la escucha de interrupción, repetir el comando de silencio. Si no se confirma la creación, no repetir la petición sin inspeccionar primero.

Inspección previa solo lectura: código 12/app en primer plano, sin servicios ni listeners 11435/11436; MemAvailable 1614280 KiB y batería 22,7 °C. Registro de timers vacío y recibos con los hashes anteriores, backend 0.10 correcto, SELinux Enforcing y SSH/web escuchando. Solo estado/contadores/tiempos en `artifacts/android-voice/human-v010-silence-first-window.txt`, sin audio ni transcripciones humanas. Todavía no se afirma corte audible de voz/tono, creación ni resultado humano; preparación no equivale a captura.

Preparación verificada por señal del evento `conversation_ready`: protocolo 8/TLS, fuente `microphone`, acciones de timer habilitadas y `microphone_opened=false`. Una sola ventana disponible para activación manual en Android; no se abrió el micrófono desde ADB ni se preparó una segunda sesión. Espera de cliente/supervisor y guardas sin ampliar. Resultado humano pendiente.

**Preparación ya finalizada, sin turnos registrados:** backend/supervisor terminan con código 0 y `reason=completed`; no aparecen selección/restauración de contexto, inferencias de turno ni inicios TTS. Esto no certifica el ensayo ni demuestra que el usuario no intentara activarlo; falta su feedback sobre la pantalla/activación. Preparación fría 22,926 s, antes de captura; no latencia de respuesta. Mínimo MemAvailable 1402572 KiB, RSS máximo de inferencia 736468 KiB y batería máxima 22,6 °C, sin extrapolar a voz activa.

Inspección posterior solo lectura: sin VoiceService/RingService ni listeners 11435/11436, app en primer plano, SSH/web escuchando. Timers/recibos conservan exactamente sus hashes previos; muestra posterior 1570620 KiB/22,5 °C. No se creó manualmente un timer, repitió la preparación ni cambió el teléfono para sustituir el resultado. Esperar aclaración del usuario antes de otra ventana; interrupción audible de voz/tono continúa sin evaluar.

## Segunda ventana humana 0.10 · fallo de interrupción confirmado por el usuario

El usuario aclara «me distraje y me fui» y pide activar la prueba de nuevo: la primera ventana no usada no se diagnostica como fallo de captura/ASR. Autoriza ahora una sola nueva preparación `pilot 90 timer`, con selección manual de captura en la app, mismo ensayo de historia→silencio y timer de veinte segundos→silencio. No se reinicia automáticamente ni se amplían límites.

Inspección previa solo lectura: app/código 12 en primer plano, sin servicios de voz/tono ni listeners 11435/11436, MemAvailable 1598068 KiB, batería 22,4 °C; backend y hashes de timers/recibos intactos, SSH/web y SELinux Enforcing conservados. Evidencia separada, sin sobrescribir la primera: `artifacts/android-voice/human-v010-silence-second-window.txt`, solo estado/contadores/tiempos, sin audio/transcripciones humanas. Preparación y resultados se documentan por separado; todavía no se afirma interrupción audible ni creación.

Segunda preparación verificada por señal `conversation_ready`, protocolo 8/TLS, `input_source=microphone`, `actions_enabled=true`, `microphone_opened=false`. Disponible para la activación manual del usuario; no se abrió captura desde ADB, reinstaló/instrumentó ni creó un timer sustituto. Resultado/feedback pendientes.

**Segunda sesión ya terminada con actividad humana real:** entrada `new`, cero turnos restaurados, cuatro turnos procesados y cuatro inicios TTS registrados, a 9418/6480/9568/6170 ms desde fin estimado ASR. Se registran tres finalizaciones TTS/retenciones, no la finalización de la cuarta respuesta. Historial enviado de 0/1/2/3 turnos previos, cero recortes, entradas de 301/372/382/436 tokens. No se guardan los textos humanos ni se certifica utilidad audible o latencia acústica por esos callbacks.

No aparece `tts_interrupted` ni acuse `timer_executor_result`; **no hay interrupción confirmada ni creación de timer registrada**. Eso no explica qué intentó decir el usuario ni cuál fue la causa: pedir feedback antes de diagnosticar o modificar. Backend/supervisor terminan con código 0/`reason=completed`, sin otra ventana automática. Preparación fría previa 23,012 s, no latencia de respuesta. Recursos: mínimo MemAvailable 1222116 KiB, RSS máximo de inferencia 743568 KiB, batería máxima 23,3 °C.

Verificación posterior solo lectura: sin VoiceService/RingService ni puertos 11435/11436, app en primer plano y SSH/web escuchando. Timers/recibos mantienen sus hashes previos, sin crear/cancelar un timer sustituto; muestra posterior 1530268 KiB/23,3 °C. No reinstalar/instrumentar, repetir pedidos ni preparar otra ventana hasta aclarar el resultado con el usuario. Corte audible de voz y tono siguen pendientes de aceptación.

### Feedback y preparación local 0.10.1 · fase previa al consentimiento

El usuario informa que no se calló al pedir silencio y que, al pedir **diez segundos** (no los veinte sugeridos), respondió que no podía crear el timer. Es reporte humano, no transcripción ASR conservada. Se acepta el fallo acústico; no se repite la prueba para sustituirlo por un sintético exitoso. El tono no se evaluó porque no hubo timer creado/recibo nuevo.

Hechos encontrados sin captura, reinstalación ni instrumentación:

- La política 0.10 exigía exactamente «Andrómeda, silencio» y **rechazaba «silencio» solo**. El reporte no permite inferir que el usuario omitiera el nombre en todas sus tentativas.
- `SilenceListener` usaba `VOICE_RECOGNITION`; el archivo existente `/vendor/etc/audio_effects.xml` del Moto configura AEC/NS en `preprocess/voice_communication`. Eso motiva cambiar únicamente la fuente de captura de interrupción, **no demuestra por sí solo la causa del fallo ni eficacia real del eco**.
- Python y Android ya aceptan «pon un timer de diez segundos» y su variante con `10`. Sin transcripción o diagnóstico de selección/rechazo no se sabe si falló ASR, selección nativa del LFM o validación. No exigir cambiar a «temporizador» como explicación probada.
- La versión anterior no conservaba contadores del listener: ausencia de `tts_interrupted` no distingue falta de captura, mezcla de eco, baja confianza, segmento distinto o llegada tardía.

Preparado en código/APK **0.10.1-silence-fix-trial / código 13**, todavía solo en PC:

1. Aceptar «silencio» o «Andrómeda, silencio» **solo con salida actual/ventana vigente**, manteniendo rechazo de negaciones/relatos/añadidos en el segmento completo, confianza mínima, IDs, vencimiento y consumo único. Validación de evidencia de una/dos palabras, sin parciales que omitan una negación posterior.
2. Fuente de interrupción `VOICE_COMMUNICATION`, intento AEC y consulta de estado habilitado; no cambiar modo global, rutas, volumen ni fuente conversacional. Es un ajuste para probar, no una reparación acústica certificada. Continúa la limitación de necesitar un segmento final reconocible mientras hay reproducción.
3. `SilenceDiagnostics`: últimas **cuatro ventanas en RAM**, solo contadores/estados: arranque, fuente, frames, buffers con señal, endpoints/rechazos, callback enviado, AEC y silenciamiento Android. No PCM, palabras, textos, IDs de charla/turno ni preferencias/archivos. Un callback tampoco prueba que se oyó el corte. Ruta de mantenimiento propuesta `dumpsys activity local.doja.voice/.MainActivity silence_metrics`, con dump propio que no vuelca vistas/transcripciones; pendiente verificar en Android tras instalar.
4. Contrato del modelo refuerza equivalencia timer/temporizador/cuenta atrás, sin cambiar LFM, `tool_choice=auto`, presupuestos ni ejecutor. Si no selecciona una propuesta válida, no se ejecuta en su lugar ni se anuncia éxito; aclaración administrativa explica herramienta disponible/propuesta no validada. Métricas de enums para distinguir selección textual/nativa, tipo y rechazo, sin petición/respuesta humanas.
5. Ocho regresiones compartidas nuevas para «timer de diez/10 segundos», negación, relato y mezcla. También alias de silencio sin nombre, límites, diagnóstico acotado y copias sin alias mutable. `SilenceInstrumentation` adaptada pero **no ejecutada sobre el proceso humano actual**.

**247 tests Python / 513 aserciones JVM, build/firma y `git diff --check` pasan en PC.** No micrófono/altavoz, inferencia nueva, instrumentación, instalación ni otra ventana. No declarar que esas pruebas repararon la acústica, selección real del LFM o uso humano del timer. Antes de aplicar, acordar la actualización: reemplazar/instrumentar la app puede perder las conversaciones RAM; timers persistentes no deben borrarse.

APK local código 13 SHA-256 `fac24e9ebcf8200576522fcdab4891c6344a92e6ff4ec04071de1739e73f14cd`; `timer_trial_model.py` local `5c8afd610fef45d52d7923d5b79b79e09e94b11cc14bf64df5a8fecc15118cd5`. Backup solo lectura de la app instalada: `artifacts/android-voice/pre-silence-fix-v0101-installed-v010.apk`, hash `c4e74ceca89f5dc1d04921d1fa838f2454768e01e40284ecfe4a4292a90f99e4`.

Verificación final: teléfono aún en código 12/0.10.0; adaptador instalado sigue `8a9110959a59002aabd74a30744f23404885b158dddd56f3c4c36cbc4bf03447`, distinto del local nuevo. Sin servicios/puertos de voz, app visible, SSH/web y SELinux conservados, timers/recibos con sus hashes anteriores. No borrar/exportar RAM humana, abrir micrófono, crear otro timer, cambiar carga/HOME/swap o preparar una ventana mientras se espera autorización de actualización.

## Instalación consentida 0.10.1 · verificada sin captura ni altavoz

El usuario responde **«sí, hazlo»** a actualizar con pérdida posible de charlas RAM y conservación de timers. Se instala con `install -r`, sin desinstalar/borrar datos. No es autorización para otra ventana humana, escucha prolongada ni inicio automático. Inspección previa: sin servicios/puertos de voz ni timers pendientes, MemAvailable 1440844 KiB, batería 22,7 °C, HOME Motorola y SELinux Enforcing. Backup APK 0.10 ya verificado; backend previo guardado en `/data/local/doha-voice/backups/pre-silence-fix-v0101-20261018/timer_trial_model.py` (hash `8a9110959a59002aabd74a30744f23404885b158dddd56f3c4c36cbc4bf03447`). Los 14 archivos privados de ASR se verifican íntegros, sin sobrescribirlos/descargarlos.

**Primer intento sintético del timer se detuvo antes de readiness:** con el prefijo ampliado `5c8afd...`, `model.prepare()` agotó el timeout HTTP; no se abrió sesión, micrófono ni ejecutor. Evidencia preservada `artifacts/android-voice/timer_create-backend-20261018-195918.txt`; no ocultarlo como prueba exitosa ni aumentar límites para pasar. Se compactó el contrato a **572 caracteres**, conservando disponibilidad/alias, duración literal, no negaciones/citas/hipótesis y resultado real. Se añadió regresión de tamaño (no garantía de latencia). No cambiar contexto 2048/entrada 1536/salida 96, `tool_choice=auto`, timeout HTTP máximo 22 s, preparación de modelo 30 s, pilotos 90/300 s, espera de cliente 300 s ni supervisor 660 s. Versión intermedia preservada en el mismo backup como `timer_trial_model.expanded-timeout.py`.

Con el prefijo compacto y el APK final, las comprobaciones ejecutadas pasan:

- **248 tests Python / 513 aserciones JVM**, build/firma y diff-check. Sin ampliar permisos de ejecución ni convertir prosa en acciones.
- Vosk Android real sobre WAV públicos: detecta comando con/sin nombre; rechaza negación, relato y petición de timer. Parser de evidencia de una palabra, corte único de voz/tono y espera de cierre autenticado pasan sin AudioRecord/altavoz/LFM; timers/recibos intactos.
- UI: ocho estados, 35 dibujos/1200 ms; contrato Android de timers aislado pasa sin sonido ni datos reales alterados.
- Flujo sin acciones: control de interrupción **simulado**, un wake/un rearme; continúa sin guardar respuesta no escuchada, luego cierre explícito y contexto retomable/nueva vacía. ASR adicional cargado con LFM y liberado. Primer PCM sintetizado 6611 ms estimados, no acústico.
- Timer sintético: LFM selecciona llamada **nativa**, validación `none`, 296 tokens de entrada; Android crea **un timer aislado de 600 s**, acuse real, síntesis a archivo, control de interrupción **simulado**, cierre `action_result` y cero seguimientos. Registro aislado persiste tras apagar inferencia, luego se limpia antes del éxito; preferencias/recibos reales intactos. Preparación previa 22,592 s, antes de captura; primer PCM a 5796 ms estimados. No se probó por esto ASR humano de «timer de diez segundos» ni sonido real.
- En los dos sintéticos completos: mínimos MemAvailable 1244228/1264648 KiB, máximos RSS de inferencia 736204/743776 KiB, batería máxima 22,9/23,3 °C y PSS app al final 137106/130891 KiB. No sumar métricas solapadas ni extrapolar decodificación/captura simultánea o uso prolongado.
- Ruta de diagnóstico Android verificada desde la app abierta, sin instrumentación adicional: `dumpsys activity local.doja.voice/.MainActivity silence_metrics` devuelve `silence_metrics={"windows":[]}`. Vacía porque no se abrió captura. Confirma el acceso sin volcar vistas/transcripciones; **todavía no prueba contadores físicos, silenciamiento Android ni eficacia AEC durante audio real**.

Evidencia privada/ignorada: `silence-synthetic-device.txt`, `silence-conversation-backend-20261018-195846.txt` y `timer_create-backend-20261018-200306.txt`, con salidas previas respaldadas antes de reemplazar runners. Solo WAV/textos de fixtures públicos en sintéticos. Se retiraron los 35 WAV generados y los dos archivos propios de transferencia del teléfono; ASR privado, backups, timers y recibos conservados.

| Componente final instalado | SHA-256 |
|---|---|
| APK código 13 / 0.10.1-silence-fix-trial | `fac24e9ebcf8200576522fcdab4891c6344a92e6ff4ec04071de1739e73f14cd` |
| `timer_trial_model.py` compacto | `9ed27501ba85e9aa8c3ab02aa7ddec76a289b190a621414eb05c335249b7fcdc` |
| `conversation_voice.py`, sin cambio en esta instalación | `7362ad76d54cba5934bdbfeaac0260e8a392a301a548f115fbd31baa5f0ebb29` |

Verificación posterior: app/reloj visible, código 13; sin VoiceService/RingService ni listeners 11435/11436, SSH 2222/web 8080 preservados y SELinux Enforcing. Timers/recibos mantienen hashes `3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793` / `f78347ba7be7de44da99dd0097cb4ae797e409c6c6cda8adeaa70b47218b0030`. Muestra posterior 1550936 KiB disponibles/23,4 °C. No hubo cambios de carga/swap/HOME, commits/pushes/merges, timer sustituto ni preparación humana. **Pendiente otro ensayo humano autorizado, primero corte de voz con diagnóstico numérico; no afirmar que ya se corrigió acústicamente ni activar otra ventana por iniciativa propia.**

## Primera ventana humana 0.10.1 · solo corte de voz

El usuario pide «hagamos la prueba». Se prepara **una sola `pilot 90` sin acciones/protocolo 6**, no el canal de timer, para aislar el corte de voz. El usuario abre Conversación LFM · sin acciones → 90 segundos; pide una historia y dice «silencio» durante TTS cuando esté activa la escucha de interrupción. No se crean timers ni se abre captura desde ADB; no reinstalar/instrumentar ni repetir automáticamente.

Preflight solo lectura: código 13/app visible, sin servicios/puertos de voz, MemAvailable 1609764 KiB, batería 22,7 °C, timers/recibos con sus hashes anteriores, backend de conversación correcto, SSH/web y SELinux Enforcing conservados. Baseline `silence_metrics={"windows":[]}`. Evidencia separada `artifacts/android-voice/human-v0101-silence-first-window.txt`: solo contadores/estado/tiempos. Al terminar se leerá una instantánea numérica de hasta cuatro ventanas mediante el dump específico, sin volcar vistas, voz ni transcripciones. Preparación y resultado humano pendientes de verificación; no equivalen a aceptar el corte acústico.

Readiness verificado por señal del evento `conversation_ready`: TLS/protocolo 6, fuente `microphone`, `actions_enabled=false`, `microphone_opened=false`. Una ventana lista para la selección manual del usuario; no se abrió captura desde ADB ni se preparó otra. Resultado de corte/feedback todavía pendientes; el recolector solo conservará campos numéricos/booleanos y enums conocidos de hasta cuatro ventanas, sin fallback a dump de vistas/transcripciones.

### Resultado humano y diagnóstico 0.10.1 · corte fallido

El usuario confirma **«no se detiene cuando le digo silencio»**. Se acepta fallo real de esta versión, no comprensión incorrecta del usuario ni una prueba pendiente de repetir idéntica. El ensayo terminó normalmente; no se preparó otro, reinstaló/instrumentó la app ni tocó la RAM humana.

Sesión: entrada `new`, cero restauraciones, dos turnos LFM con 85/132 tokens y cero recortes, más una tercera respuesta de cierre; tres inicios/finalizaciones TTS y tres pares retenidos. Callbacks a 10169/5115/2139 ms estimados, sin certificar inicio acústico útil. No aparece `tts_interrupted`, cero acciones. Preparación previa 7,207 s. Mínimo MemAvailable 1248720 KiB, RSS máximo de inferencia 737376 KiB y batería máxima 23,2 °C.

Instantánea numérica real recogida sin voz/transcripción:

| Ventana | Arranque captura | Duración ventana | Frames leídos a 16 kHz | Buffers con amplitud >64 | Endpoints / palabras máximas finales | Callback de corte |
|---|---|---|---|---|---|---|
| 1 | 231 ms | 8856 ms | 121280 | 51 | 1 / 0 | 0 |
| 2 | 174 ms | 5103 ms | 63360 | 0 | 0 / 0 | 0 |
| 3 | 179 ms | 3857 ms | 43520 | 0 | 0 / 0 | 0 |

Las tres usan fuente 7 (`VOICE_COMMUNICATION`), AEC disponible/habilitado, `microphone_muted=false`, `client_silenced=0`; finalizan `OUTPUT_ENDED`, `OUTPUT_ENDED`, `CLOSED`, no por excepción/guardia. **Leer frames no prueba que contengan la voz del usuario; cero buffers sobre el umbral no significa PCM exactamente nulo.** El contador de palabras solo observa resultados finales de Vosk: no se midieron hipótesis parciales ni final pendiente al cerrar. No afirmar por ello que toda hipótesis fue vacía, que Android necesariamente bloqueó el micrófono, que AEC funciona acústicamente o que el usuario no dijo la orden. Tampoco hubo palabra reconocida de baja confianza que justifique simplemente bajar el umbral 0,8.

Inspección posterior solo lectura confirma los mismos tres resúmenes en RAM, sin servicios/puertos de voz, app visible, SSH/web y hashes de timers/recibos intactos. Muestra posterior 1460988 KiB/23,1 °C. El comando de corte es local; el LFM no decide detener TTS y no recibió un callback de interrupción aceptada. Causa exacta aún sin demostrar.

**Siguiente comprobación propuesta, no implementada/activada:** control de captura aislado de hasta 20 s, sin LFM, TTS ni timers, comparando las fuentes 6/7 secuencialmente y sin dos AudioRecord abiertos a la vez. Medir señal numérica y palabras finales/parciales como cantidades, no exportar texto/audio. Así separar ruta/fuente/recursos de interferencia del propio sonido antes de pedir otra conversación completa. Si el control aislado pasa, recién después acordar una comparación con reproducción. Instalar ese diagnóstico puede perder RAM; pedir permiso y no abrir captura desde ADB, cambiar modo/volumen global, extender límites ni iniciar otra ventana mientras se decide. Plan: `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`.

**Autorización posterior «sí»:** diagnóstico implementado e instalado como **0.10.2/código 14**, con consentimiento de pérdida de charlas RAM y backup de APK 0.10.1. 251 tests Python/570 aserciones JVM, build, UI, contrato aislado de timers y parser/ASR/UI del diagnóstico pasan sin captura/altavoz/LFM. Datos reales de las fuentes todavía no obtenidos; dump inicial `attempt=0`, `active=false`, `recording=false`, `phases=[]`. El usuario debe iniciar manualmente desde Micrófono → Diagnóstico de captura · 20 s → Iniciar diagnóstico; no necesita backend/ADB. Recoger después solo `capture_probe_metrics`, sin instrumentar/reinstalar ni pedir otra historia. Detalle, hashes y límites en `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`; voz/modelo/servicios apagados al entregar.

**Primer resultado humano del diagnóstico, tras «listo»:** intento 1 completo, ambas fuentes reales 6/7 a 16 kHz mono detectaron una coincidencia final del comando cada una, sin reproducción/LFM. Capturas 6087/6128 ms; ninguna coincide solo como final forzado pendiente. Sin silenciamiento Android/saturaciones; fuente 7 marca AEC habilitado. Datos numéricos recogidos sin matar proceso/RAM ni exportar audio/ASR. Reposo y timers/recibos intactos. El control básico pasa en esas condiciones; **no identifica aún reproducción vs conmutación/carga ni aprueba corte durante TTS**. Próxima propuesta con sonido público fijo, sin LFM, solo con autorización aparte; no implementada/activada. Evidencia y límites en `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`.

**Comparación con sonido autorizada después con «sí»:** 0.10.3/código 15 instalada con respaldo 0.10.2, control quieto conservado y nueva confirmación explícita de voz fija. Asset público offline de 2,792 s repetido dentro de cada fase, sin LFM/TTS en vivo ni cambios de volumen/ruta/modo. Dos fuentes secuenciales, <=20 s; final válido a tiempo corta solo AudioTrack de esa fase, nunca parciales/tail. Fase completada no equivale a corte reconocido; datos de reproducción son acuses software, falta feedback acústico. 252 tests Python/596 aserciones JVM, build, Android UI/ASR/asset y contrato de timers pasan sin captura/altavoz. Dump inicial vacío, servicios/modelo apagados y timers/recibos intactos. El usuario debe iniciar Diagnóstico con voz fija · 20 s; no se arrancó desde ADB. Uso y huellas en `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`.

**Resultado tras «listo», 0.10.3:** lectura exclusiva de `capture_probe_metrics`, intento 1/COMPLETE, reproducción solicitada/iniciada y parada por `PHASE_END` en ambas fases, **cero cortes y cero comandos finales/pendientes**. Fuente 6: RMS -13,535 dBFS, máximo 12 palabras parciales/18 pendientes, ningún endpoint normal; contenido desconocido, no convertir tail/parciales en órdenes. Fuente 7: RMS -81,027 dBFS, 1156/89280 muestras no nulas, pico 246 y cero palabras; AEC habilitado/sin silencio Android no implica captura humana eficaz ni prueba causal del AEC. Ambas a 16 kHz mono, salida tipo 2/volumen leído 15 de 15; capturas 6125/6606 ms, última cerrada a ~14494 ms del inicio. Se observa fallo aun sin LFM/capturador conversacional previo, no reparación. Evidencia numérica sin audio/ASR en `artifacts/android-voice/human-v0103-playback-first-window.json`; app/RAM sin reinstalar/instrumentar. Falta percepción acústica por fase antes de acordar otro control de preprocesado/ruta. No nueva captura, cambios de ajustes, timer sustituto ni repetición automática. Tabla/límites en `DIAGNOSTICO-CAPTURA-ANDROMEDA.md`.

**Feedback posterior corrige la interpretación acústica:** el usuario oyó la voz y percibió que se calló tras pedir silencio. No consta aún inmediatez ni resultado separado por fase. Cero cortes registrados no demuestra que la voz siguiera sonando; `PHASE_END` tampoco demuestra corte causado por la orden. Mantener datos originales y percepción como evidencias distintas, atribución pendiente; ni aprobar reparación ni certificar fallo audible de 0.10.3. Preguntar por inmediatez sin repetir ensayo/abrir captura ni modificar app/teléfono. Etiquetas de feedback añadidas al artefacto tipado, sin texto/voz humanos. Los fallos humanos anteriores durante TTS no se borran ni quedan revalidados por este control.
