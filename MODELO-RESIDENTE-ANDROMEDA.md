# Modelo entre conversaciones y monitoreo

20 de octubre de 2026. Siguiente prioridad tras la prueba física de volumen.

## Estado vigente:30/13 instalado; servicio de voz y modelo continuos activados

Después del error humano se añadieron recibos numéricos y comprobaciones aisladas,
sin instrumentación/transcripciones. Preparación sin mic, captura/reloj con PCM
descartado y pre-arm real listo353ms/lag10ms pasan. Cierres confirmados antes de
un único dueño nuevo, activado por operador con consentimiento4 y Detener visible;
no reinicios/reconexiones/boot/renovaciones automáticos. Servicio+modelo observados
activos; no humano/acciones/web inyectados. APK30/UID/datos/agenda3 preservados.
La causa exacta del error anterior (peer_closed del cliente) sigue sin prueba;
diálogo/silencio/eco/recibos requieren petición hablada. Referencia actual
`artifacts/android-voice/voice-session-observed-v1/`; texto inferior es historia.

Diagnóstico acotado confirmó TimeoutError en prefijo público frío a30s. Nuevo
presupuesto de preparación45s solo en tools; respuesta humana60s/cómputo55s y
watchdog/guardias intactos. Dos módulos corregidos sin reinstalarAPK27 ni perder
UID/datos/recibos/volumen/sistema. Arranque real nuevo:35,5s y TLS/monitor2 continuo
listo, sin límite total ficticio, micrófono manual.443 Python:441 pasan/2skips;
24 POSIX pasan. `artifacts/android-voice/startup-preparation-fix-v1/` es referencia
vigente. Voz/eco/recibos/consumo con captura real todavía no aceptados. El fallo
cerrado inferior es historia; no describe el dueño nuevo.

Actualización coordinada con consentimientoRAM recibido y misma firma/UID/datos.
Prefijo nativo380/1536 y seis selecciones públicas pasan sin ejecutar tools.
Retirada b11669 corregida/verificada: máximo un token público descartado, erase
slot0 con recibo4 y comprobación posterior0; cache secundaria0/readonly/caps0.
No usar el supuesto histórico de cero tokens. MigraciónAndroid2→3 verificada tras
abrir Alarmas: vacía y recibos preservados. El único arranque real continuo falló
antes de listo/monitor (exit1); causa no confirmada. Dueño/servicios/puertos/lock
apagados, micrófono nunca activado. No relanzar igual ni ampliar plazos a ciegas;
diagnóstico/corrección y activación manual/Detener/consumo/acústica pendientes.
Plan/evidencia `artifacts/android-voice/joint-code27-deploy/`; apartados inferiores
son hitos históricos, no el estado operativo actual.

## Interrupción del candidato13 corregida solo en código

Pre-arm del micrófono antes de hablar, tickets por salida y continuidad más allá
de32 respuestas; PCM fresco y callback acotados. Fallo de interrupción/stop o mute
detiene de forma segura, sin restart; tono de aviso puede conservar sus botones
sin fingir ASR disponible. Temporizador ya no mata la voz continua y START no
extiende30s. Modelo/Worker/presupuestos y permisos nativos no cambian. Pruebas
locales/compilación, no consumo o acústica físicos. Modelo/Moto no se iniciaron.
Plan conjunto con preflight nativo y consentimiento RAM/escucha pendientes:
`artifacts/android-voice/silence-lifecycle-v1/`.

## Candidato local13: ciclo de alarmas integrado, no desplegado

Sobre la voz continua del12: política explícita `weekly_alarm_lifecycle_v1`, mismos
cinco native tools/auto y límites2048/1536/96. Prefijo de preparación y runtime
actualizados juntos; LFM real y presupuesto aún no medidos. Alarmas semanales,
borrado de serie con confirmación oída y posposición5min con recibos durables.
Audio de avisos conserva el dueño continuo al ceder micrófono, en lugar de parar
el servicio por estar activo. No autoarranque, reconexión o renovación de pruebas.

431 Python pasan/2 skips; Java161 alarmas/105 agenda y compilación/sintaxis pasan,
sin dispositivo o APK. Siguen pendientes silencio fiable y validación real de
audio/consumo/purga del motor; no equiparar compilación a funcionamiento físico.
Detalles actuales `artifacts/android-voice/weekly-alarm-v1/`. Instalado sigue26/10.

## Segundo bloque: modelo y voz continuos integrados solo en candidato local

Protocolo12/seconds0/continuous_voice_v1: foregroundmic habilitado por humano y
consentimientonuevo3, sinplazo total/noautorenovar/noautoboot. Cerrar charla→nonce/
contextofresco deAndroid→esperavoz, mismaTLS/mismoLFM; checkpoints≥20turnos mantienen
historia pero revocan ámbitos anteriores. Plazos/colas/datos acotados, contexto1h/
máximo4LRU, heartbeat, cesión audio aavisos y vuelta tras acabar. Detener/EOF/
guardias paran ambos, no autoreinicio. nativotools2048/1536/96/Worker-first intactos.

Privacidad motor añadida: prompt público0tokens reemplaza task_prev, erase slot0
conrecibo elimina KV/checkpoints, cachéRAMsecundaria deshabilitada solo continuo.
slot-save-path b11669 exige directorio: solo BINroot verificado no writable,
sin symlinks/caps/setuid; no guardar archivos o cambiarpermisos. API/FS/consumo real
todavía sincomprobar: no instalar hasta hacerlo. Fallo depurga→apagado verificable.
417testsPython pasan/2skips, Java37continuo/105agenda, compilaciónAndroid/sintaxis
pasan. No dispositivo o modelo iniciado. Detalles `continuous-voice-v1/findings.md`.
Último físico APK26/proto10 apagado; pendientes semana/posponer/borrar ysilencio.

## Implementación local posterior: modelo continuo explícito, voz aún acotada

ContinuousResidentRuntime y monitor schema2/lifetime=continuous_model_v1 sin
deadline falso o renovación, dueño yleases/contextos separados y guardias
manual/RAM/temperatura/sensor. Worker exige salud reciente boottime≤30s tras
gracia inicial90s para no dejar modelo huérfano; sin reiniciar. Entrada/supervisor
candidato acepta continuous-model, NO desplegado/activado ni probado end-to-end.
El cliente sigue90/300s:micrófono/canal continuo es el próximo bloque, no resuelto
por este modo delmodelo. Pruebas relojfalso>1800s/30 leases pasan dentro desuite
396Python/2 skips, sin ensayos reales. No presentar consumo de captura continua
como medido. Código junto conagenda guiada/continuación en
`artifacts/android-voice/guided-agenda-v1/`. Último estado físico sigueapagado v2.

## Objetivo posterior: modo continuo con escucha local y monitor

Usuario pide modelo encendido y micrófono activo siempre, supervisando recursos.
El residente900/1800s instaladoNO cumple ese modo:está apagado tras ensayo v2.
Modalidad continua nueva requerida, no prórrogas depilotos:espera local devoz,
diálogo dinámico/vuelta aespera, servicioAndroid visible/Detener y micrófono con
dueño único. GuardiasRAM/temperatura/sensor/manual prevalecen; no transcripciones
o audio persistidos, contextoRAM acotado/1h de inactividad humana. No prometer
consumo o despertar con micrófono apagado, ni añadirboot/horario/reinicios sin
política explícita. Requisitos completos sección0 de
`CONVERSACION-Y-AGENDA-DINAMICA-ANDROMEDA.md`. No instalado/activado, futuroAPK
con consentimientoRAM informado. Descripciones manuales deabajo son estado
instalado/histórico, no objetivo actual.

## Resultado final v2 y siguiente prioridad

V2 cerrado16:22:18UTC/modelo apagado sin renovar;814 muestras, mínimo disponible
1236120KiB/RSS pico740712KiB/temperatura máxima24,7°C.0 servicios/procesos/puertos/
lock y credenciales propias retiradas.56 hashes/APK26/datos/obligaciones/volumen/
sistema preservados. Usuario informaWEB bien, silencio intermitente, agenda falla.
No aceptar funcionalidad de voz por monitoreo sano. Cambiar ahora diseño de
agenda/conversación según `CONVERSACION-Y-AGENDA-DINAMICA-ANDROMEDA.md`; no otra
prueba/reinstalación/escucha automática. Recordatorios guiados y alarmas semanales
con cancelación/posposición aún no implementados ni instalados.

## Segunda prueba autorizada en curso (estado vigente)

Nuevo ensayo900s, usuario autoriza tras reparaciónSSL. Listo16:07:41UTC, motor/
TLS/pin verificados sin token/hello humano, captura o consulta del agente. No
reinstalar/reiniciar app ni tocar56 módulos instalados. Inicial1418988KiB
disponibles/RSS738772KiB/batería23,2°C. Fin estimado16:22:06UTC/11:22:06Bogotá;
último INICIO de charla300s16:17:01UTC/11:17:01Bogotá. Micrófono manual, sin
autorrenovar; aceptación humana de corrección/conversaciones aún pendiente.
`artifacts/android-voice/human-resident-v2/` contiene baseline/lanzamiento/estado.
Recolector único al plazo original, no polling/reintentos. No introducirAPK,
horarios o suspensión por voz por este consentimiento.

## Estado vigente tras el ensayo

**Modelo apagado; primera prueba terminada sin renovación.** Cierre verificado
16:03:32UTC,0 procesos/servicios/puertos/lock y credenciales efímeras retiradas.
835 muestras, RAM disponible mínima1308600KiB, RSS pico736724KiB, batería máxima
23,1°C. Datos/recibos/volumen8/15/sistema intactos. No certifica conversación
humana funcional: usuario reportóSSL y el reintento todavía no se realizó.

CorrecciónPython instalada16:04:09UTC sin reinstalar/reiniciar app o modelo:
TLS+token primero y aviso de rechazo después si no cabe300s en el plazo.12/12
regresionesPOSIX y56 hashes verificados,55 módulos restantes intactos. Mensaje
claro español del cliente solo en fuente, NO enAPK26 instalado (que mostrará
rechazo genérico, noSSL por este camino). No mover ventana/cron/boot/certificado
o guardias; siguiente ensayo requiere autorización aparte. Recibos vigentes
`resident-runtime-v1-install/after.json` y
`resident-window-ending-fix-v1/final-state.json`.

## Error de conexión reportado durante el ensayo

Se detectó rechazo por plazo ANTES deTLS: con menos de305s restantes el servidor
cierraTCP y puede aparecerSSLHandshakeException «connection closed by peer».
Inspección16:00:04UTC confirma motor/TLS/monitor sanos y196520ms restantes; no
hora exacta del intento ni textos humanos recogidos. El aviso de disponibilidad
anterior debió aclarar que no se pueden INICIAR charlas300s en los últimos305s.
El límite de15min incluye preparación y no se renueva por este fallo.

Corrección de fuente preparada/pruebas: autenticarTLS+token y responder con
`error/resident_window_ending` antes de contexto/captura/acciones. Cliente fuente
mapea código fijo a aviso español; APK26 instalado aún no incluye ese mapeo y
necesita consentimiento RAM si se sustituye en el futuro.383 testsPython pasan/
2 skips Windows; políticasJava y compilaciónAndroid pasan. Sin reinstalar,
reiniciar, ampliar el ensayo o abrir micrófono. Artefactos
`artifacts/android-voice/resident-window-ending-fix-v1/`; estado de despliegue
de corrección separado del ensayo original.

## Estado operativo posterior

**Instalado con consentimiento y ensayo900s en curso.** APK26/proceso/RAM/datos y
volumen8/15 no cambiaron; tres fuentes backend y tres scripts desplegados con
respaldo/56 hashes/owners/modos verificados. Inicio completo con ASR y canalTLS
listo, sin micrófono del agente. Un solo arranque, no renovación. Listo a
15:48:59UTC, fin estimado original16:03:21UTC (900s incluye preparación). Recursos
iniciales1432908KiB disponibles, RSS modelo736636KiB, batería22,8°C. El consumo
final y cierre todavía pendientes, no certificar15min por el arranque correcto.

Registro vigente `artifacts/android-voice/resident-runtime-v1-install/`. Recolector
programado para una única lectura al final; no se abre otra sesión o prueba. El
modelo está disponible entre charlas mientras dure este ensayo, pero la escucha
sigue requiriendo pulsación humana y termina como máximo a300s. No se aceptan
sesiones nuevas que no quepan completas antes del plazo global. Nada de boot,
horario nocturno o despertar/suspender por voz instalado aún. Las secciones de
«candidato no desplegado» inferiores son historial previo al consentimiento.

## Qué cambia este candidato

- Un supervisor es dueño del llama-server verificado. Cada conversación recibe
  un permiso temporal que solo puede cancelar su generación/conexión, no matar
  el modelo compartido. Si la cancelación queda atascada se detiene el modelo;
  no se reutiliza una generación incierta.
- El servidor TLS puede recibir conversaciones sucesivas. Al terminar se libera
  contexto/respuesta del backend y se crea otra memoria vacía. Retomar contexto
  sigue requiriendo snapshot Android autenticado; sin arrastre implícito.
- Se conserva protocolo10, selección nativa/guardias/recibos, modelo LFM
  2048/1536/96, reloj y límites por turno. La escucha Android sigue manual y con
  máximo300s; finalizar una acción sigue cerrando su sesión. No nueva escucha
  automática ni acceso root de la app.
- El modo anterior sigue siendo el predeterminado: un modelo dedicado por prueba.
  El residente solo se habilita por el nuevo punto de entrada explícito.

## Recursos y seguridad

RAM disponible, RSS del modelo/backend, CPU de ambos (porcentaje de un núcleo),
temperatura, porcentaje de batería y estado de carga. Guardias cada segundo,
serie cada10s/máximo120 muestras y resumen agregado, JSON privado≤64KiB. No texto,
audio, conversaciones, URLs, tokens o hashes de contenido humano. El KV/prompt
del motor sigue solo en RAM y dura como máximo el ensayo residente (<1h).

Arranque heredado: RAM≥1200000KiB, batería<38°C. Parada por RAM<500000KiB,
batería≥40°C, sensor inválido/muestras atrasadas, caída del modelo o petición de
parada. Sin reinicio automático, remount, ajuste de swap/carga, apertura de router
o compra/instalación de dependencias. Solo PID+inicio+UID+binario+key-path propios
verificados se pueden señalar. Modelo sin credenciales web en su entorno.

Ensayos explícitos de15/30min, plazo absoluto desde antes del arranque. No24x7,
cron, horario nocturno o comandos «suspensión/actívate» instalados por este cambio.
Una sesión nueva debe caber entera en el tiempo restante. Al parar se retiran
credenciales efímeras; queda solo el informe técnico privado para recoger.

## Archivos y comandos (solo después de desplegar)

- `backend/resident_runtime.py`: permisos, monitor, schema numérico y archivos
  privados sin seguir enlaces simbólicos.
- `backend/conversation_voice.py`: modo residente opcional, sin cambiar el modo
  dedicado instalado hasta aprobar el candidato.
- `backend/resident_voice.py`: entrada restringida sin micrófono Android.
- `scripts/start-andromeda-resident.sh`: supervisor manual en primer plano.
- `scripts/resident-model-worker.sh`: trabajador nativo UID10218 y vigilancia
  independiente, sin logs crudos o credenciales en argv.
- `scripts/andromeda-resident-control.sh`: estado/parada local del operador root,
  no servidor de comandos ni herramienta ofrecida al modelo.

```sh
# Operador existente ADB/Magisk; no ejecutar aún si no está desplegado.
/system/bin/sh /data/local/doha-voice/start-andromeda-resident.sh 900
/system/bin/sh /data/local/doha-voice/andromeda-resident-control.sh status
/system/bin/sh /data/local/doha-voice/andromeda-resident-control.sh stop
```

`stop_requested` no se presenta como parada confirmada. Comprobar después estado,
puertos/procesos y métricas. Un lock existente se rechaza, nunca se borra a ciegas.
Las alarmas/timers/recordatorios Android no dependen de que siga cargado el LFM.

## Validación y estado

**Candidato comprobado, aún no desplegado/activado.** Suite Windows y
verificación separada POSIX/TLS/LFM real se registran en
`artifacts/android-voice/resident-runtime-v1/`. En la comprobación silenciosa el
actor se simula expresamente: dos conexiones TLS reales y dos respuestas LFM,
verificando mismo proceso entre sesiones/contextos vacíos y reposo, sin ASR,
micrófono, TTS, acciones Android o proveedor externo. No confundirla con uso
prolongado humano ni con la preparación de otra ventana de voz.

La primera prueba nueva de `serve` en Windows encontró que su reloj por defecto
seguía apuntando a CLOCK_BOOTTIME real; se corrige únicamente el reloj del
fixture. No se cambia el reloj de producción ni se rebajan las guardias.

Resultados:382 tests Python aprobados/2 omitidos Windows; en el Moto pasan las11
pruebas específicas POSIX, incluidas protección de symlinks/permisos y borrado de
credenciales propias. Dos conexiones TLS reales y respuestas LFM pasan1628/1621ms
manteniendo el mismo PID/inicio entre sesiones y12s de reposo. Contextos separados
comprobados; actor sustituido por fixture, no voz/acciones reales.15 muestras de
recursos: RAM mínima1441520KiB/RSS modelo pico738828KiB/batería máxima22,6°C.
El modelo se apaga al terminar. Backend54 archivos/APK26/proceso/datos/volumen8/15
actuales intactos. No nuevos paquetes o credenciales permanentes.

El punto de entrada completo con ASR y un ensayo15min todavía requieren despliegue
y aprobación de activación. Su monitor se inicia después de cargar ASR para no
confundir la carga inicial con un atasco; durante esa carga sigue vigente el
trabajador nativo independiente con las mismas guardias y plazo absoluto.
