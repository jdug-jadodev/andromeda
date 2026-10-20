# Andrómeda: conversación dinámica, recordatorios y alarmas

## Estado vigente: APK30/protocolo13, sesión continua activada; aceptación hablada pendiente

Tras reporte humano de error, se confirmó cierre por el cliente, no guardia de
recursos; la etapa exacta anterior no estaba registrada y no se inventa causa.
Actualizaciones autorizadas28/29/30 preservan firma/UID/datos/agenda3/recibos y
añaden únicamente recibos numéricos y comprobaciones fijas: preparación sin mic,
captura/reloj con PCM descartado y pre-arm sin reproducción/decodificación pasan.
Pre-arm real listo353ms/lag10ms, sin rebajar guardias. Comprobaciones cerradas
verificadas antes de otro dueño. Sesión continua nueva activada explícitamente
por el operador con app visible/Detener y consentimiento4 previo; servicio+modelo
observados activos, sin frases/acciones/web de prueba inyectadas. No autoinicio,
autorestart, reconexión o renovación de pilotos.446 Python:444 pasan/2skips y Java
completo pasa. Referencia actual `artifacts/android-voice/voice-session-observed-v1/`.
El micrófono ya está habilitado. Todavía no se certifican diálogo, silencio/eco,
web o agenda por estas comprobaciones; el fallo anterior no se declara resuelto
sin una petición hablada. Apartados siguientes conservan estados históricos.

**Arranque corregido después:** el usuario autoriza diagnóstico/actualizaciones
autónomos sin nuevas preguntas y aclara que aún no usa Andrómeda para nada real.
Una frontera de diagnóstico sin mensajes/locales confirmó TimeoutError en el
prefijo público, antes de residente/ASR. Separado presupuesto frío:45s solo para
cinco herramientas; respuestas siguen≤60s/cómputo55s, guardias sin ampliar.
Dos módulos actualizados conservando APK27/UID/datos/volumen/sistema. Nuevo arranque
real pasó: preparación35,5s y TLS/monitor continuo listo, sin micrófono del agente.
443 Python:441 pasan/2skips;24 POSIX pasan. Referencia/manifiesto actual:
`artifacts/android-voice/startup-preparation-fix-v1/`. Falta aceptación humana de
voz/eco/agenda/recibos; no se prometen por el arranque. El fallo cerrado descrito
a continuación es el intento anterior, no el estado del dueño corregido.

Con consentimiento informado se verificó el Moto y se instaló coordinadamente
APK27/`0.13.0-continuous-agenda`, backend/helper y scripts; misma firma/UID10238,
datos/recibos/volumen8/15 y sistema preservados. Humano abrió **Alarmas**: lista
vacía y migraciónAndroid2→3 verificadas por lectura en RAM, mismos recibos.
El único arranque real continuo de esta instalación falló antes de quedar listo
(exit1, sin monitor); causa interna aún desconocida, sin transcripciones/log crudo.
Modelo/backend/worker/launcher/servicios/puertos/lock apagados, sin work reciente
restante ni cambios de obligaciones. Micrófono nunca activado. No relanzar igual
ni ampliar plazos sin evidencia: falta diagnosticar/corregir arranque antes de voz.

Preflight nativo público: prefijo380/1536, cinco herramientas `auto` y seis
selecciones correctas sin ejecutarlas. Retirada de caché de cero tokens falló;
el motor se apagó sin instalar entonces. b11669 muestrea antes de comprobar el
límite: se corrigió a **un token público máximo, salida descartada**, seguido de
erase exacto del slot0. Nueva verificación solo del cambio pasó:4 tokens borrados,
segundo erase0; readonly/capacidades0/cache secundaria0.441 testsPython:439 pasan/2skips;22 POSIX pasan.

No hay aceptación acústica ni de herramientasAndroid: silencio/eco, conversación,
web, recordatorio guiado, alarma semanal/borrado/posponer, recursos continuos y
parada completa siguen pendientes. Evidencia y plan vigente:
`artifacts/android-voice/joint-code27-deploy/`. Los bloques siguientes son hitos
históricos y sus estados «local/no instalado/sin consentimiento» no son actuales.

## Cuarto bloque local: interrupción y validación conjunta

Corregidos el límite de32 salidas y el arranque del detector después de TTS.
Ahora se prepara captura fresca antes de hablar, con tickets por salida y
rechazo de callbacks/PCM obsoletos. Un fallo de stop no reabre micrófono; mute,
client-silenced y captura atascada cierran de forma segura. Tono y conversación
continua ceden audio sin renovar límites. No se rebaja confianza ni se autorizan
acciones desde parciales. Prueba de5000 salidas simuladas y suite local completa;
todavía **no aceptación acústica en Moto**. No APK/dispositivo/modelo usados.
Plan conjunto con consentimiento y preflight nativo pendientes:
`artifacts/android-voice/silence-lifecycle-v1/`.

## Tercer bloque local: alarmas semanales y controles de ocurrencia

Candidato protocolo13 (`weekly_alarm_lifecycle_v1`), **no instalado**. Siete días,
hora inequívoca/AM-PM y confirmación explícita de repetición semanal. Migración
agenda1/2→3 conserva los eventos antiguos como una sola vez. Borrado por voz de
serie indicada por día/hora: lectura local, pregunta TTS completada, confirmación
humana posterior, permiso por revisión y recibo real antes de anunciar éxito.
Posponer5min y descartar esta vez afectan solo la ocurrencia; silencio no borra
la serie. Programación y recibos durables acotados, sin retries de incertidumbre.

433 pruebas Python:431 pasan/2 skips; Java incluye161 aserciones de alarmas y105
de agenda guiada; compilación Android y sintaxis de scripts correctas. Son pruebas
locales/simuladas, sin teléfono, APK, micrófono o modelo. Borrado por voz de fechas
únicas/duplicadas sigue manual; silencio y nuevo ciclo completo requieren validación
física. Detalle: `artifacts/android-voice/weekly-alarm-v1/findings.md`.

## Segundo bloque de código: canal continuo (local, no instalado)

Protocolo12 y capturaAndroidFG sin plazo total, cierre dediálogo→espera devoz en
mismaTLS, identidades frescas delcliente y checkpoint depermisos sin reconectar.
ContextoRAM1h/máximo4/LRU humano, aviso→cesión deaudio→reactivación sinapagar modelo,
Detener/EOF/guardias apagan eldueño sin autorestart. Colas/plazos/datos acotados y
permisos nuevos por cada turno. Vaciado verificado decachéKV/checkpoints/task_prev
añadido sin guardar slots, sujeto averificación delBINreal/FSreadonly antes deuso.
419 testsPython:417 pasan/2skipsWindows; suiteJava pasa(37continuo/105agenda),
fuentesAndroid y3scripts compilan/pasan sintaxis. Reloj/modelo/audio/recibos son
simulados: no hardware, instalación o escucha nueva. Evidencia
`artifacts/android-voice/continuous-voice-v1/`. Pendientes: alarmas semanales y
borrado/posponer, calendario/correcciones y silencio real; no producto completo.

## Primer bloque de código (posterior a los requisitos)

Implementado LOCALMENTE y no instalado: formulario tipado guiado Python/Android
con TTSonDone por pregunta, permiso final basado en campos humanos/procedencia,
recibo y confirmación con contenido/hora; protocolo11 explícito conserva catálogo
nativo/cinco herramientas. Agenda/timer/volumen pueden confirmar y ofrecer otra
función sin cerrar. Actor simulado verifica recordatorio→web sin reiniciar.
396 testsPython pasan/2 skipsWindows, suiteJava pasa/95 aserciones nuevas, todas
fuentesAndroid compilan. Artefactos `artifacts/android-voice/guided-agenda-v1/`.

Base de modelo continuo también codificada: dueño/leases sin vencimiento ficticio,
monitor serie acotada schema2 y watchdog nativo con lease de salud boottime para
no dejar modelo huérfano. No activada ni validada enel teléfono. Micrófono/canal
continuos y alarmas semanales/borrado/posponer todavía pendientes. Cliente sigue
90/300s y calendario literal limitado; silencio físico no corregido aún. El
documento inferior especifica la meta completa, no afirma que todo esté hecho.

## Petición humana vigente — 20 de octubre de 2026

Después del segundo ensayo residente, el usuario informa: búsqueda web bien;
«silencio» solo a veces; crear recordatorios y alarmas no funcionó. No declarar
esas funciones aceptadas por pruebas simuladas o por un monitor de recursos sano.
La hora/frase/causa técnica de cada fallo no se recogió: no atribuirlo todo a
ASR, selección nativa, parser o plazo sin evidencia. No exportar conversaciones.

La intención no es una colección de pruebas aisladas: es una asistente que pide
lo que falta, ejecuta con confirmación real y admite inmediatamente otra función.
Este documento fija requisitos y trabajo pendiente, NO implementación instalada.

## 0. Modo continuo solicitado explícitamente después

El usuario añade que el modelo permanezca encendido, el micrófono activo siempre
y exista monitoreo. Esto sustituye como OBJETIVO el modo de pilotos manuales300s/
residente900s; no autoriza reciclar sus ventanas ni afirmar que ya se instaló.

- LFM cargado entre conversaciones mientras el modo continuo esté habilitado y
  sea seguro. Finalizar un diálogo/herramienta no descarga el modelo.
- Escucha LOCAL de activación disponible sin pulsar botón: «Andrómeda» inicia
  diálogo; dentro del diálogo se puede continuar sin repetir el nombre. Tras
  despedida/inactividad vuelve a espera de activación, no micrófono desconectado
  por un límite de prueba. Interpretación explícita de escucha siempre: no
  transcribir ni ejecutar toda conversación ambiental como órdenes.
- ServicioAndroid en primer plano con notificación/indicador claros y Detener
  visible. Un solo dueño del audio: activación, conversación e interrupción de
  TTS/alarma se coordinan, no micrófonos simultáneos que se disputan el recurso.
  No escuchar la propiaTTS como autorización o nueva petición.
- DuranteTTS/aviso se interrumpe el procesamiento conversacional de fondo y se
  mantiene la ruta local apropiada para «silencio»/posposición contextual. No
  enviar vozambiente alLFM/Worker ni ocultar captura bajo un estado de pausa.
- Audio y conversación acotados enRAM, sin grabaciones/transcripciones/hashes de
  contenido humano persistidos; caducidad de contexto por1h de inactividad humana
  conservada. Persistir solo obligaciones y configuración operacional necesaria.
- Monitor permanenteRAM disponible/RSS de procesos/CPU/temperatura/batería/carga,
  muestras e informes numéricos acotados. Medir el coste REAL de micrófono+ASR+
  modelo continuos: el ensayo anterior con residente no demuestra ese consumo.
- «Siempre» NO anula guardias: sensores inválidos, memoria/temperatura inseguras
  detienen o suspenden de forma segura y muestran estado. Nada de bucles de
  reinicio o mutaciones repetidas para recuperarse. Política de recuperación
  automática/autoinicio tras reinicio del teléfono debe quedar explícita antes
  de habilitarla; no se deduce de esta petición un cambio deboot/SSH/Android.
- Detener/privacidad permite apagar micrófono y modelo. Con micrófono realmente
  apagado no se promete despertar por voz; reactivar requiere una acción posible
  y explícita. Suspensión manual no desaparece por reloj/horario.
- No aplicar el horario23:00–06:00 propuesto antiguamente y no confirmado: el
  objetivo actual es continuo mientras esté habilitado, con excepción manual o
  de seguridad. Se mantienen≤60s de inicio de respuesta y≤60s lecturaWorker.

Estado operativo al recibir este requisito: ensayo v2 terminado/modelo apagado;
APK26 instalado aún es manual/300s y residente instalado solo ofrece900/1800s.
El usuario solicita el comportamiento continuo, pero no se sustituyeAPK sin
advertencia/consentimiento informado sobre pérdidaRAM, preservandoUID/datos.

## 1. Recordatorios mediante preguntas

Ejemplo requerido:

1. Humano: «Andrómeda, pon un recordatorio».
2. Asistente: «¿Para qué es el recordatorio?».
3. Humano: «Lavar la loza».
4. Asistente: «¿Cuándo quieres que te lo recuerde?».
5. Humano: fecha/hora, o «en cinco minutos».
6. Android guarda/programa el evento y entrega recibo verificado.
7. Asistente confirma contenido y fecha/hora inequívoca, o duración solicitada
   y hora resultante: «Recordatorio guardado: lavar la loza, hoy a las…».
8. «¿Necesitas algo más?»; escucha la respuesta dentro de la misma sesión.

- Pedir SOLO los datos que faltan; una petición completa puede resolverse de una.
- Poder corregir contenido/hora y cancelar un borrador sin crear ningún evento.
- Si la fecha/hora es ambigua o pasada, preguntar, no adivinar ni moverla al futuro.
- Admitir formatos humanos como día de semana, fecha, a. m./p. m. y hora24h con
  validación compartida Python/Android; zona vigente America/Bogota.
- Una duración se ancla al turno humano que la especifica; no renovarla durante
  preguntas/confirmaciones/reintentos. Confirmar la fecha realmente guardada.
- No decir «guardado» por una selección del modelo, una propuesta, TTS o un
  timeout. Solo después del recibo Android. Resultado incierto: no duplicar.
- Borrador tipado solo en RAM, con campos/límites/plazo, ligado a sesión/contexto
  y turnos humanos autenticados. No crear una frase humana sintética para saltar
  el actual validador de petición completa.
- Si cambia a web u otra herramienta antes de terminar el borrador, no interpretar
  esa orden como contenido/hora: preguntar si quiere cancelar o dejar pendiente.
  El borrador nunca se ejecuta por cambio de tema, «sí» suelto, historial o web.

## 2. Continuidad entre herramientas

Después de una creación confirmada no cerrar la charla por el tipo de herramienta.
Ejemplos de turnos sucesivos requeridos:

- Recordatorio confirmado → «Busca en Internet…» → búsqueda real.
- Alarma confirmada → recordatorio nuevo → preguntas solo sobre sus campos.
- Web → volumen → web, con permiso nuevo por cada petición humana.
- «Sí, enciende las luces/el televisor» después de una oferta: nueva petición,
  NO simple confirmación de la herramienta anterior. Ejecución doméstica solo
  cuando exista adaptador autenticado/pareado; no afirmar éxito de algo inexistente.

Conservar selección nativa y catálogo de herramientas habilitadas en cada turno;
sin router que bloquee otras funciones tras agenda. «Abiertas» significa que
puede elegir otra herramienta en el turno siguiente, NO permisos ilimitados ni
varias mutaciones automáticas. Una mutación por turno humano vigente, sin reintentos
de acciones inciertas. Texto de web/modelo/TTS nunca autoriza una acción.

Finalizar el DIÁLOGO por despedida/petición humana explícita, negativa a una oferta
efectivamente oída o inactividad: en modo continuo vuelve a activación local sin
descargarLFM. Detener/privacidad/guardia sí pueden detener captura/modelo. El modo
continuo será una modalidad nueva explícita, no renovaciones automáticas de los
pilotos300/900s que siguen instalados. Permisos/deadlines por acción permanecen
acotados y vigentes; un canal abierto no concede permisos para otro turno.

## 3. Alarmas semanales

- Soportar cualquiera de los siete días (lunes a domingo), no solo una fecha.
- «El lunes a las cinco de la tarde» corresponde, según el comportamiento pedido,
  a una alarma recurrente CADA lunes a las17:00, horaBogotá. Confirmar explícitamente
  «todos los lunes», para que no parezca una sola fecha.
- No interpretar «de lunes a domingo» como una orden actual de crear siete alarmas.
  Días sueltos/grupos/días de semana/todos los días requieren selección explícita.
- Una fecha concreta o «solo este lunes» es una ocurrencia única, no una serie.
- Persistir solo datos necesarios del evento (días/hora/zona/estado/revisión), no
  los turnos de conversación. Migrar esquema sin perder eventos/recibos actuales.
- Reprogramar siguiente ocurrencia tras disparo, reinicio y cambio de reloj, sin
  duplicados ni convertir una alarma vencida en una segunda entrega inesperada.
- «Elimina la alarma del lunes…»: resolver una alarma concreta; si hay varias,
  preguntar cuál. Confirmar eliminación solo tras cancelación/programación/DB
  verificadas. Una cancelación de serie quita sus próximas repeticiones.
- «Elimínalas»: usar únicamente una selección explícita vigente; si no está claro
  qué conjunto incluye, preguntar. No borrar eventos por memoria implícita.
- «Posponla»: cuando hay una alarma activa inequívoca, posponer su ocurrencia
  actual5min, confirmar nueva hora SOLO tras recibo de reprogramación y hacerla
  sonar5min después. Conservar intacta la serie semanal. Sin alarma identificable,
  preguntar; no elegir otra alarma a ciegas.
- No confundir posponer, silenciar sonido, descartar ocurrencia y eliminar serie.

## 4. Silencio fiable

Pendiente por reporte humano; Detener sigue visible. «Silencio» interrumpe TTS/
aviso actual, no elimina recordatorios/series ni ejecuta herramientas. Diagnosticar
detector local/audio/eco y tiempos con eventos técnicos y métricas acotadas; no
depender del LFM ni instalar/reiniciar para obtener transcripciones. Posponer por
voz mientras suena requiere una ruta local explícita/contextual del mismo dueño
del micrófono autorizado, no otra captura paralela o encubierta. Seguridad/aviso
visible del micrófono preservados.

## 5. Brecha confirmada en el código actual

| Ubicación | Comportamiento instalado | Cambio coordinado necesario |
|---|---|---|
| `backend/agenda_requests.py` | `current_event` exige datos completos del turno actual; calendario sin recurrencia semanal | Campos guiados/procedencia y semántica horaria compartida |
| `backend/assistant_tools_model.py` | Cinco herramientas nativas; no estado tipado de borrador | Selección nativa conserva alcance; preguntas no conceden ejecución |
| `backend/assistant_tools_trial.py` | Resultado agenda obliga `close_reason=action_result`, sin oferta | Confirmación verificada seguida de oferta/continuidad |
| `android-voice/.../AgendaActionChannel.java` | Rechaza como inválida una confirmación de agenda sin cierre | Nuevo contrato coordinado, no rebajar comprobación de recibos |
| `android-voice/.../AgendaEntry.java` y `AgendaStore.java` | Evento de una ocurrencia, sin serie semanal | Esquema/migración/scheduler/identidad de serie y ocurrencia |
| `backend/conversation_voice.py` y canalesAndroid | Permisos basados en turno humano actual y presupuesto acotado | Borrador multi-turno validado independientemente; continuidad sin renovar plazos |

Por eso no es suficiente añadir frases alSYSTEM, quitar un `close_reason` solo
enPython o dar por creada una alarma porque el modelo lo dijo. Python yAndroid
deben acordar el protocolo, la procedencia de campos y el recibo de ejecución.
No desplegar una mitad incompatible ni aceptar protocolos viejos silenciosamente.

## 6. Orden de implementación y aceptación

0. Modalidad continua explícitaAndroid/backend/supervisor, arbitraje del micrófono
   y monitoreo permanente acotado; pruebas deparada/seguridad/inactividad humana.
1. Contrato de diálogo/borradores tipados y confirmación→continuación, pruebas
   coordinadas Python/Java sin dispositivo. Preservar webWorker-first y volumen.
2. Recordatorio guiado end-to-end, correcciones/cancelación y transición a web.
3. Alarmas semanales: esquema/scheduling y cancelación/posposición con recibos.
4. Interrupción «silencio» fiable; pruebas de audio solo con consentimiento.
5. Integración real de luces/TV identificadas/pareadas y transiciones entre ellas.

Pruebas mínimas: campos de varios turnos sin permiso falso; «cancelar» durante
borrador; fecha pasada/hora ambigua/semana siguiente; recibo ausente/duplicado/
rechazado; recordatorio→web sin volver a activar; cambio de herramienta con
borrador pendiente; cerrar/dejar de hablar/Detener sin mutación; alarma lunes
17:00 repetida/pospuesta5min/eliminada sin reaparición; reboot y fallos parciales;
texto de web/TTS/historia incapaz de completar o autorizar un evento.
Además: más de300s/900s en el NUEVO modo sin desconexión de ensayo, espera tras
despedida y reactivación por voz sin botón,1h sin actividad humana elimina contexto
sin apagar espera local, Detener apaga captura/modelo, guarda manual/sensor inseguro
no se autorreanuda, costeRAM/CPU/temperatura/batería con escucha real medido sin
exportar audio o transcripciones. No usar límite largo como sustituto de ciclo
de vida separado y un servicio de producción supervisado.

No otra ventana humana para repetir la misma versión. Cambios locales y pruebas
no equivalen a funcionalidad instalada. ReemplazarAPK requiere consentimiento
informado por pérdida deRAM; preservarUID/datos/obligaciones. No probar alarmas,
borrados, posposición, luces,TV o consultas reales desde el agente sin permiso.
