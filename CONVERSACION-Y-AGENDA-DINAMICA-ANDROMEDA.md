# Andrómeda: conversación dinámica, recordatorios y alarmas

## Instalación actual40: fechas/hora faltante aceptadas; siguen alarmas

Reporte humano posterior a la prueba de40 y consulta sobre fecha sin hora: confirma
que funcionó y quedó bien. Aceptación limitada a ese caso, no a todos los formatos,
días relativos, listado/recibos nuevos o entrega/sonido. Se registra sin consultar
teléfono, exportar transcripciones/contenido/audio/hash ni tocar la sesión activa.
AL-01 queda aceptado en los casos reportados: «los martes a las 8 de la noche» y
«hoy en dos minutos» funcionaron en APK41. El apagado tras intentos anteriores no
se reprodujo, pero no se declara resuelto para toda formulación. No repetir alarmas
ni tocar la sesión activa desde este reporte. Siguiente prioridad: VOZ-01, cortes
de respuesta/interrupción.

Nuevo reporte humano: borrado funcionó en su prueba; también alta a ocho horas y
minutos/horas, pero rechaza fecha puntual con año/AM-PM y días. Aceptación limitada a
esos casos, sin nueva inspección técnica de recibos/entregas. No repetir su aviso
de ocho horas, ni programar el ejemplo de fecha desde este chat. La agenda/volumen
pueden haber cambiado desde la última observación: conservarlos con lectura fresca.

REM-FECHA-01:40/`0.13.13-calendar-dates` INSTALADA tras permiso nuevo. Amplía
validación independiente Python/Android para AM/PM puntual, «del» antes del año,
año hablado y días con hora. Día sin hora pregunta la hora y conserva solo fecha
numérica en RAM anclada al turno; no toma AM/PM del historial ni asume72horas.
Campos posteriores exigen pregunta terminada/frescura; cancelación, expiración y
replay siguen protegidos. Mes mal reconocido no se sustituye a escondidas.
Payload7tools/auto idéntico a39; mismo modelo/límites/Worker/esquema. Evidencia:
`artifacts/android-voice/calendar-date-candidate-v1/` (`phone-release.json` y build-v2).
Gate real20/20requeridos,20/21total (indirecto anterior abierto),581tokens y prefijo
43,811s. Fechas2,014–5,086s, cache retirada4/segundo0; sin acciones Android/agenda/
micrófono/Worker. `calendar-date-native-v1/`. Payload/modelo/límites intactos.

Baseline fresco20:51UTC:1REMINDER_SCHEDULED,2CREATED/5DELIVERED,0timer/1recibo y8/15.
Dueño39 cerrado por petición actual, no para reiniciar automáticamente. APK40 +solo
3módulos calendario permitidos,65totales, mismafirma/UID/datos/esquema/agenda/HOME/
SSH/web/swap/volumen. El aviso existente sigue registrado en AlarmManager antes y
después de actualizar y tras el ensayo, con IDs comparados únicamente en RAM del
teléfono y salida numérica. No se repitió/retiró/recreó el aviso humano de8h.

Android real59checks en DBaislada con4fixtures/planificador/TTS ficticios: fechaPM,
año hablado, día relativo, aclaración de hora/repregunta, permiso/recibo durable,
rechazo de replay/campos alterados/origen/mes inválido/pregunta no escuchada. No
agenda humana abierta/AlarmManager/audio/modelo reales en esa instrumentación;
DBaislada retirada, contador humano y registro de programación preservados.
`calendar-date-install-v1/`; arranque/observación nuevos en `calendar-date-live-v1/`.
Dueño actual `/data/local/linux/debian-final/tmp/andromeda-resident.qnchgT`,inode124693,
activación2591088587/20:58:28UTC; preparación54,137s<55s/TLSlisto/35s observados.
Lectura20:59:30UTC:40/65hashes/1recorderAndroid/GLOBALrunning, sin fallo de protocolo
ni recibo terminal actual. Audio:32PCM tardíos rechazados/1gap recuperado estado2,
sin retimestamp/decodificación/reinicio/replay. STOP15/error0/ended2590661923 cierra39;
EOF697/volumen50→53%/listado2588261899 son históricos, no nuevos recibos40.
Preservación releída20:59:31UTC:agenda1SCHEDULED/AlarmManager1/volumen8/15/sistema.
En ese punto faltaba aceptación humana; después acepta fecha/hora faltante como se
indica arriba. No tocar la nueva sesión ni generar acciones desde ejemplos. Las
secciones39/38 siguientes son antecedentes.

### Instalación39 anterior al nuevo reporte (estado técnico histórico)

Usuario autoriza detener/validar/instalar. Se cerró el dueño38 por esa petición;
baseline fresco0agenda/5DELIVERED/0timer/1recibo, **MEDIA8/15** cambiado por el humano,
no15/15. No imponer vacío ni restaurar volumen. Se instalaron APK39/65módulos
(6actualizados/uno nuevo) con mismafirma/UID/datos/HOME/SSH/web/swap y agenda intactos.
WorkerEN512 y helper instalado distinto siguen EXCLUIDOS; sin replay de peticiones.

Gate real581/1536tokens,7tools/auto, prefijo43,752s:14/14requeridos pasan,14/15total
con indirecto conocido aún abierto. Borrado3variantes2,562–3,421s. No agenda/Android
actions/Worker/micrófono en ese gate; erase4→segundo0 y dueño público cerrado.
Instrumentation real Android:93checks de wire/SQLite en DBaislada, datos/planificador/
salida/TTS ficticios; eliminación/recibo durable/repregunta/ambiguos/stale/fallos/replay
verificados. Nunca abrió agenda humana ni AlarmManager; DBaislada retirada. No es
por sí sola aceptación humana del borrado ni prueba del sonido/cancelación física
de un aviso real; la aceptación humana se reportó después como se indica arriba.

Instalado `0.13.12-reminder-delete`, política `confirmed_reminder_delete_v1`, APKhash
`894ae22e49f7a49d857f48442587e8ada8f1bcd589b74f8888ebd05098ad40ec`.
Dueño histórico39 `/data/local/linux/debian-final/tmp/andromeda-resident.MvPDHh`,inode119934;
activación explícita2587140097/19:52:39UTC. Arranque público53,608s bajo55s, TLSlisto,
35s voz observada y lectura19:53:39UTC:GLOBALrunning/65hashescorrectos/1recorderAndroid,
sin fault ni reciboterminalACTUAL. EOF697/ended2586504814 corresponde al cierre38
solicitado, no a39. Volumen8/15 y sistema releídos/preservados19:54:34UTC.
Autoridad/evidencia: `artifacts/android-voice/reminder-delete-live-v1/` y
`reminder-delete-install-v1/`; selector en `reminder-delete-native-v1/`.

En ese punto faltaba la prueba humana; ahora acepta el borrado en su caso. No tocar
la sesión actual ni iniciar acciones reales desde este chat. Snapshotvacío puntual
anterior al uso humano: no imponerlo ni repetir acciones inciertas.
App visible: BG-01 sigue sin implementar. Los apartados siguientes son antecedentes.

### Antecedente38: alta/listado aceptados y falta inicial de borrado

Último reporte humano: confirma que el recordatorio lo hizo bien y que lista;
**alta/listado aceptados en esos casos**. Reporta que eliminarlo por voz no funciona.
APK38 no tiene herramienta/canal de borrado de recordatorios (sí borrado manual y
otra ruta para series de alarmas). REM-DEL-01 pasa a ser el siguiente bloque,
conservando alta/listado y aclaraciones; no reabrir diseño ni avanzar a alarmas aún.

Se preparó inicialmente LOCALMENTE APK39/`0.13.12-reminder-delete`: séptima
herramienta `delete_reminder` sin argumentos, política `confirmed_reminder_delete_v1`.
Android resuelve el destino, pregunta lo que falta o lee opciones de coincidencias,
lee contenido/horario exactos y exige confirmación humana fresca. Backend no recibe
la agenda ni los textos de esas preguntas; read-only nunca autoriza borrado. Permiso
de un uso y recibo Android; sin reintentos inciertos ni cambios de esquema.
540Python536pasan/4Windows skips; Java144aserciones nuevas y suite/build39v2 pasan.
Evidencia `artifacts/android-voice/reminder-delete-candidate-v1/`. El selector
LFM real, la transacción Android aislada y la instalación se completaron después como
se indica arriba; aceptación humana del borrado se reportó después. El snapshot y
volumen18:09 inferiores son históricos: el humano puede haberlos cambiado; no
imponer agenda vacía ni restaurar niveles antes de desplegar. Más detalle en
`MEJORAS-Y-PRUEBAS-ANDROMEDA.md`.

Primer reporte humano de esta versión: el usuario confirma que Andrómeda preguntó ante una
transcripción imperfecta y acepta esa parte. Registrar solo el resultado, sin
transcripción/audio del teléfono. Hito limitado a aclaración inicial: NO demuestra
reconocimiento general, datos/horario correctos, guardado, entrega o continuación.
Seguir con el mismo recordatorio y sus campos antes de pasar a otra herramienta;
no crear una acción desde el chat ni desplegar/reiniciar durante su prueba.

Segundo reporte humano favorable: en volumen, una afirmación fue mal reconocida y
Andrómeda volvió a preguntar. El usuario destaca la mejora de tolerancia a errores.
Aceptación limitada a repregunta de confirmación; no extrapolar a porcentaje
aplicado ni nuevo recibo Android, ni afirmar ASR reparado. Conservar el volumen
que el usuario tenga ahora, aunque difiera del último15/15 observado. No consultas
al teléfono, cambios, reinicios o nuevas acciones por este comentario.

El usuario autorizó perder el contexto RAM actual, detener por SSH e instalar el
candidato. Se detuvo el dueño37 sin borrar datos/agenda y se instalaron APK38 más
6módulos coordinados; después solo ajustes públicos de prompt/preparación del modelo.
UID10238,64módulos, sistema/HOME/SSH/web/swap y volumenMEDIA15/15 conservados;
WorkerEN512 sigue EXCLUIDO. No acciones antiguas repetidas ni avisos creados desde aquí.

Estado fresco18:09:25UTC21/10/2026: GLOBALrunning, APK38/64hashes coinciden,
servicioactivo/1recorderAndroid, monitorfresco2,6s y sin fault/recibo terminal actual.
Dueño `/data/local/linux/debian-final/tmp/andromeda-resident.V3jKz7`,inode124489;
activación explícita nueva2580873495/18:08:12UTC con consentimiento previo4/Detener
visible. Manifiesto/observación en `artifacts/android-voice/reminder-clarification-live-v3/`.
No tocar esta sesión: el humano probará recordatorios en sus propias palabras.

**Experimental, NO validada:** gatesnativosv1…v6 conservan sus fallos. Compactov6
selecciona8/9: una petición indirecta confunde listado, que se rechaza sin lectura
ni permiso; regresión añadida. No quitar el fallo ni afirmar alta/entrega humanas.
Tras dos timeouts de preparación a45s, se redujo prefijo a506tokens y se amplió SOLO
preparación pública a55s, no respuesta humana60s/cómputo55s/permiso26,5s. Pruebas
530Python526pasan/4Windows skips; suiteJava132nuevas+208alarmas+64volumen pasa.
Fase pública del arranque reparado46,735s y35s de voz observada; sin autorestart.
Snapshot puntual0agenda/3DELIVERED/0timer/1recibo, no condición de agenda vacía.
BG-01/cortes/emoji/cuentos/alarmas mantienen sus pendientes; app visible para esta prueba.

Los apartados siguientes conservan el desarrollo y los estados anteriores, no
sustituyen la versión/dueño/limitaciones actuales indicados arriba.

## Prioridad vigente: mismo diseño, flexibilidad y una herramienta cada vez

Tras la explicación, el usuario decide mantener arquitectura/modelo actuales y
mejorar transcripciones imperfectas e integración, validando cada herramienta de
principio a fin antes de pasar a otra. Se retoman desarrollo/pruebas LOCALES de
REM-01: tras selección nativa, texto dudoso relacionado con recordatorios puede
preguntar intención; solo afirmación fresca tras pregunta oída y nuevos campos
pueden dar un permiso de un uso. Sin corregir ASR para fabricar una orden.

APK38/`0.13.11-reminder-clarification` y protocolo13 con política coordinada
`confirmed_reminder_intent_v1` son candidatos NO instalados. Python/Android
reconstruyen por separado intención/contenido/momento; un recibo coincidente
sigue siendo imprescindible. Falta selección del modelo real y aceptación de
voz/guardado/entrega/continuación. No se han reparado alarmas, TTS, cuentos o BG-01.
Detalles y límites en `MEJORAS-Y-PRUEBAS-ANDROMEDA.md`.

Lectura puntual17:15:59UTC confirma APK37/64hashes/dueño lSY23c/inode124964 y
GLOBALrunning. No detener/reinstalar/reiniciar sobre esa sesión; cerrar explícitamente
con Detener antes de medir el candidato o desplegar ambas mitades. No migración,
OAuth/cuentas/cargos ni permiso para exportar audio/transcripciones/conversaciones.

### Antecedente: pausa de explicación, sustituida por la decisión anterior

Nuevo informe: alta de recordatorio falla tras error ASR; respuesta de orientación
se corta y vuelve a escucha; TTS pronuncia emoji del chiste; historias siguen
negándose; alarmas por voz siguen sin funcionar. No adjudicar causas de corte o
alarma sin aislar la fase. Listar recordatorios recibe aceptación humana provisional;
búsqueda web sigue bien valorada, sin extenderlo a traducción EN o búsqueda automática.
Prioridades y criterios en `MEJORAS-Y-PRUEBAS-ANDROMEDA.md`: ASR-01/REM-01, AL-01,
VOZ-01, TTS-01, CU-01 y BG-01. No llamar estables a alarmas/altas por aprobar solo
propuestas nativas. Luces/TV quedan detrás de estos fallos.

El usuario solicitó entender arquitectura y viabilidad de un cerebro ChatGPT
antes de continuar. `ARQUITECTURA-ANDROMEDA.md` separa implementación de propuesta,
IA de gramática literal/ejecutores y API estándar de OAuth/uso de plan elegible.
Durante esa pausa no se autorizaron nuevos ensayos/despliegues/reinicios. La decisión
posterior permite mejoras locales, no OAuth/cuentas/costos ni envío de audio/
conversaciones a la nube. La conversación activa sigue protegida.

## Nueva sesión solicitada: pendientes de segundo plano y pruebas antes de luces/TV

**Activa para pruebas de funciones estables:** APK37/64módulos SIN cambios de
producción. Dueñoactual `/data/local/linux/debian-final/tmp/andromeda-resident.lSY23c`,
inode124964; activación explícita2571157549/15:26:16UTC, después de petición nueva
del usuario. Observación35s y lectura fresca15:26:59UTC:GLOBALrunning,64hashescorrectos,
1recorderAndroidactivo, monitorfresco386ms, sin falloterminalactual ni protocolofault.
No usar2HeaeF como dueño vigente. Manifiesto/sesión/evidencia actuales en
`artifacts/android-voice/resumed-tests-lights-plan-v1/`; no más cambios sobre esta voz.

El nuevo gate fue PARCIAL: recordatorio→propuesta, alarma martes20:00→propuesta,
chiste y consulta seleccionados; cuento negó capacidad. Candidato creativo público
posterior también falló (negativa y otra historia cortada por96tokens): NO instalado.
Se liberó explícitamente el subconjunto estable, NO se convirtió ese fallo en gate
completo aprobado. **CU-01 abierto** en `MEJORAS-Y-PRUEBAS-ANDROMEDA.md`; falta además
aceptación humana/recibosAndroid reales de agenda. Cero acciones antiguas repetidas,
0agenda/3DELIVERED/0timer/1recibo siguen como instantánea, nunca como requisito vacío.

El usuario pide REGISTRAR, no implementar ahora, que cerrar la ventana no debe
apagar la escucha continua. Mejora BG-01 y criterios de aceptación en
`MEJORAS-Y-PRUEBAS-ANDROMEDA.md`. `MainActivity.onStop()` sigue deteniendo la voz;
mantener la interfaz abierta durante las pruebas actuales. Respetar Detener y
cierre forzado, sin autoboot/restart/reconnect ni saltarse guardias.

Orden actual: recordatorios → alarmas → chistes/cuentos → luces → TV. No tratar
este plan como permiso para ejecutar ejemplos de agenda, alarmas o domótica.
Antes fresco15:13:26UTC: teléfono conectado, APK37/64hashes correctos, motor y voz
apagados; dueño anterior2HeaeF terminó `voice_peer_closed`, sin protocolofault ni
guardias de memoria/temperatura. Snapshot actual0agenda/3DELIVERED/0timer/1recibo
de timer, no imponer vacío si cambia por uso humano. La petición nueva permite
una activación explícita para probar, no reabrir por sí sola la sesión cerrada.
Base: `artifacts/android-voice/resumed-tests-lights-plan-v1/before.json`.

Luces: investigación pública iniciada y preparación local leída, sin scan/connect/
pair/writes. Dos familias candidatas diferentes, detalles y cautelas en
`LUCES-NEO-LOTUSLAMPX-MRSTAR.md`. MotoSDK29, radioBluetoothapagada y sin permisos
BLE/ubicación en Andrómeda; preparar cambio solo después de probar voz y en límite
seguro, sin rootBLEgenérico. TVLG65UR7300PSA después; pairing aún no resuelto.

## Actual37: motor y micrófono verificados después de reparar los fallos

GateFINALINSTALADO9/9pasa (`explicit-tools-live-v1/public-gate.json`): chisteESreal,
recordatoriocompleto traschiste3,231s, alarmasemanal3formas2,546–3,199s con
weekday2/minute1200; incompleto,lectura,cuenta,volumen siguen bien seleccionados.
PropuestasSOLO, no ejecuciónAndroid/guardadohumano. Prefijofrío43,914s<45s;
retiro4tokens/segundoerase0/preparaciónpública36,912s antesdeactivar.

Dueñoactual `/data/local/linux/debian-final/tmp/andromeda-resident.2HeaeF`,inode121587,
activaciónboottime2527463155 (03:18:03UTC). Manifiestoautoridad
`artifacts/android-voice/explicit-tools-live-v1/installed.json`: APK37/64módulos,
capabilitiesENvalidado/respuestasES. Observación35s y fresca03:18:59UTC:GLOBALrunning,
servicioactivo/1recorderAndroid/64hashescorrectos, sin protocolofault ni recibo
terminalACTUAL; CONNECT70/ended2526130772 es del intento fallido36, no cierre37.
Sin recibonuevodeclock/lectura; no hubo otra incidenciaobservada en esta ventana.
SnapshotACTUAL0agenda/3DELIVERED/0timer/1timerreceipt, sin replay de peticiones ni
alarmas delchat; conservar cualquier cambio posterior delhumano.

Se deja funcionando SIN más cambios/reinicios. Falta aceptaciónhumana de
estabilidad/recordatorio/alarma guardada cada martes20:00 y sonido real; comprobar
recibosAndroid actuales antesde decircreado. Volumen100%35mantienehito sin inferir
ejecución37. Silencio/ENWorker/luces siguenpendientes. No exigir frases exactas ni
convertir ejemplos web/historial/TTS/chat en autorización paraejecutar.

## Reparación37: cierre real por reloj de audio; alarma semanal con hora primero

Humano confirma que el chiste respondió, pero al intentar recordatorio se cerró;
nuevoActivar muestra motorapagado. Lectura fresca02:58:18UTC:GLOBALnot_running,
64hashes/APK36correctos. Monitor `voice_protocol_error`, sin calentamiento ni falta
de memoria (25,6°Cmáx/1,34GBmín). `voice_fault`error1/source1/line817 corresponde
al guardiaPCM que agrupaba edad>1000ms/futuro>100ms/retroceso. **No se registró cuál
de las tres condiciones ocurrió; no atribuir exactamentelatencia deldecoder**.
AndroidEOF línea697 es posterior al fallo backend; IOExceptionconnectlínea70 es
todavía posterior. Última decisión2:reply9,565s/historial1, no selección de agenda.
SnapshotACTUAL0agenda/3DELIVERED/0timer/1timerreceipt, sin nuevos eventos; no replay.
Evidencia `artifacts/android-voice/joke-reminder-session-failure-v1/before.json`.

Reproducciónpública con respuesta→audio inválido dispara cierre36para las3causas.
37mantiene EXACTOS límites de frescura: nunca decodifica/retimestamp/reproduce
PCM rechazado. Una incidencia aislada descarta peticiónparcial/formularios y vuelve
aWake con aviso visible; necesita activaciónnueva, no usa el resto de una frase
sin su posible negación. Durante ejecución/voz no cancela/reintenta ni atribuye
éxito a una acción pendiente. Reloj inválido persistente≥5s cierra como antes;
se mantienen guardiasde recursos/flujo/deadlines y no hay restart/reconnect.
Recibo nuevo `voice_audio_health.json`, estrictamente numérico/acotado/privado,
distingue atraso/futuro/retroceso y recuperación, sin audio/texto/huellas.

Defecto semanal reproducido: solo aceptaba día→hora; rechazaba hora→día y8pm
sin espacio. Python/Android independientes ahora aceptan ambos órdenes y AM/PM
pegado/separado/hablado; cada martes20:00 se conserva como weekday2/minute1200,
próximafecha futura y recurrenciasemanal, no 08:00 ni alarma única. Headerpositivo
«una alarma…» también se valida tras selecciónnativa; negaciones/citas/mezclas/
múltiplesdías se siguen rechazando. No se crean alarmas a partir de este chat.

520Python516pasan/4Windows skips; Java208alarmas+64volumenconfirmado y suite/build37v2
pasan. 7/7pruebas públicas del fallo/recovery/reciboPOSIX pasan TAMBIÉN en teléfono,
sin micrófono/modelo/accionesAndroid. Instalado37+3móduloscoordinados (siguen64),
UID/datos/sistema/volumenactual preservados. Manifiestoautoridad
`artifacts/android-voice/audio-weekly-install-v1/installed.json`; gateLFM/restauración
explícita en `artifacts/android-voice/audio-weekly-live-v1/`. No aceptación humana
de estabilidad/alarma todavía; no inferirlas de pruebas simuladas/instalación.
WorkerEN512sigueEXCLUIDO; luces sin tocar; no mezclar hito volumen35 con alarma37.

GateLFM37inicial NO pasó: tras chiste negó recordatorio y en una forma«ponunaalarma
alas8pmlosmartes» no llamó herramienta. Forma habladaochodelanoche y«unaalarma…»
sí produjeronweekday2/minute1200. Además respuesta alchisteera«notengoaccesoachistes»:
el gate débildekindreply la marcópass, pero NO es un chiste aceptado. No se activó
micrófono (`audio-weekly-live-v1/public-gate.json` conserva fallo). Se comparan
dos instruccionespúblicas decapacidades sobre el MISMOdueño, sin instalar ni ejecutar,
exigiendo también ausencia de negativa humorística. No repetir peticioneshumanas
ni esconder elgatefallido. Resultado en `audio-weekly-live-v1/prompt-probe.json`.

Corrección adicional validada ANTES de desplegar: instrucción explícita sobre
capacidades y llamada defunción, respuestasSIEMPREespañol. CandidatoEN9/9pasa en
MISMOdueño; no hizo falta probar el segundoES. Chiste real enespañol→siguiente
recordatorio selecciona`create_reminder`3,288s; alarmasemanal3formas seleccionan
`create_alarm`2,566–3,243s y camposmartes20:00. Consulta, incompleto y volumen
siguen seleccionadosnativamente contool_choiceauto; no router por frase ni forcetool.
No atribuir la mejora soloalidioma de instrucciones: cambió también suclaridad.

Se instaló después SOLOconstant`BASIC_SYSTEM` de `assistant_tools_model.py`, igualdad
ASTsalvoesaconstante y textoEXACTOdel9/9 comprobados. APK37no reinstalado de nuevo,
64módulos, UID/datos/sistema/volumenpreservados;520Python516pasan/4Windows skips.
Manifiestoautoridadahora `artifacts/android-voice/explicit-tools-install-v1/installed.json`;
gatefinalinstalado/activación/estadoen `artifacts/android-voice/explicit-tools-live-v1/`.
No usar el manifiesto37anterior sin esta constante ni darporactiva la sesión hasta
su recibonuevo. Todavía no aceptaciónhumana de alarma real/estabilidad/lectura.

## Hito anterior36: voz activa antes del fallo reportado; no aceptación general

Gate público corregido **13/13** en el teléfono: cuentas simbólicas/habladas,
7×8/10÷2/decimal/división0/trasweb;3formas de consulta;nebulosa y selección de
volumen/recordatorio incompleto. Cálculos0–6ms SINLFM/Worker; selecciónlectura
2,576–3,285s. Conocimiento5,133s. No son tiemposASR→audio ni lecturaAndroidreal.
Prefijopúblico35,127s traseraseverificado; consultaposterior2,849s y cálculo1ms.
Retiro4tokens/segundoerase0/preparaciónpública35,132s antes de activar; memoria
humana vacía, mismo modelo y sin acciones/consultasWorker/dispositivosde luces.

Dueñoactual `/data/local/linux/debian-final/tmp/andromeda-resident.6ltYYU`,inode149765;
identificar SIEMPRE porpath+inode+identidadreal, no reutilizar identidadiuWmit.
Manifiesto `basic-arithmetic-live-v1/installed.json`, activaciónboottime2525287683,
observación35s y lectura fresca02:42:59UTC:GLOBALrunning,
monitorfresco1,036s/64hashescorrectos,1recorderAndroidactivo, sin falloterminalactual
ni protocolofault. El STOP/ended2523836690 es del cierre35previo, no falloactual.

**No hay reciboAndroid de lectura aún ni aceptación humana nueva**: no decir que
ya pronunció el listado o que todas las formulaciones/cálculos estánresueltos. Se
deja escucha activa sin más despliegues/reinicios; humano prueba espontáneamente
cuenta/consulta. No tocarvolumen100% ni mezclarhito35 con aceptación36. Alarmas,
silencio y traducciónEN mantienen sus límitesanteriores; lucesaplazadas/sin tocar.

## Nueva prioridad antes de luces: consulta real de recordatorios, cálculo básico y demora

Humano aporta lucesNEO LotusLampX `MELK-OA10 17` y MRStar `GATT--DEMO`, pero pide
ANTES resolver consulta de recordatorios/2+2 y lentitud; inventario en
`LUCES-NEO-LOTUSLAMPX-MRSTAR.md`, sin escaneo/pareado/escritura/permisosBluetooth.

Defecto confirmado: APK35/catálogo5 carecía de herramienta para LISTAR; crear no
implica acceso de consulta. ElSYSTEM estaba centrado en programar y la continuación
web limitaba el conocimiento a evidencia adjunta. No atribuir retrospectivamente
la negativa humana de2+2 a un motivo exacto sin su recibo. Se compara código35
contra36 con entradas públicas/mismoLFM, incluida matemática después deweb.

Instalado candidatoAPK36/`android_reminder_read_v1`,6herramientas: nueva
`list_reminders()`0args, permiso de solo lectura del turno humano comprobado otra
vez porAndroid. ConsultaAgendaStore real; CONTENIDOS se formatean/hablan SOLO en
Android, no pasan alLFM/TLS/backend ni al historial. Recibo contiene solo estado/
counts/nonce; cero mutaciones y vacío se distingue de error. Lectura acotada a3
entradas completas porfecha y anuncia cuántas más quedan sihay, sin fingir listado
completo. No paginación vocal en esta versión. Sin migraciónDB ni lecturaGoogle/
otrasapps. Contenido de snapshot se olvida tras reproducción/fallo.

Comparativa real35/36 reprodujo la negativa: código35 decía no tener función aun
dando4;36soloPrompt resolvió2+2 simbólico, pero reformuló dosmásdos sin responder y
rechazó siete×ocho/diez÷dos. **Gate inicial falló:6/10pasan,4fallan; no se activó micrófono ni se
declaró reparado.** Evidencia pública `basic-assistant-live-v1/public-basics-benchmark.json`.

Se instaló después `local_arithmetic.py`: motor determinista de solo lectura para
pregunta humana ENTERA de aritmética, no respuestas prefijadas ni clasificación de
acciones. Palabras/números/decimales/preferencia de operadores; ASTallowlistDecimal,
sin eval/calls/names/IO/red,250caracteres/64nodos/12niveles/resultados≤10^12. Distingue
aproximación/división0/límites. No procesa contenidoWeb/modelo/TTS ni peticiones
mixtas/negadas/citadas. Fallo de reconocimiento deja conversaciónLFM normal; no
ejecuta ni autoriza nada. Matemática local no invocaLFM/Worker. Promptgeneral sirve
para conocimiento; continuaciónweb sigue sin herramientas y con datos no confiables.
TraductorEN512LOCAL sigueEXCLUIDO del despliegue.

Otra causa estructural de demora: después de borrar caché al cerrar/caducar charla
faltaba preparar prefijoPÚBLICO otra vez. Ahora tras erase verificado y relevo ya
confirmado se prepara asynchronously en1hilo, Hola fijo, sin memoria humana; no
bloquea relevo4s/escucha, cancela preparación ante NUEVO pedido humano y conserva
su plazo original. No cambia guardias, pesos, slots/cacheRAM0, modelo/transporte,
autoboot/reconnect/restart ni renovación de pruebas. MediciónLFMreal inicial:
preparaciónpública37,334s;2+2simbólico trasella3,218s vs35negativa6,923s. Prefijo
frío de consulta35sin herramienta34,993s;36selección2,496–3,044s (no comparar como
si ambas consultas hubiesen leídoAndroid). Esto NO es latencia completaASR/TTS.

512Python509pasan/3skipsWindows;432combinaciones aritméticas generadas dentro de
un test, Java33lectura+64volumenconfirmado y suite/build36v2 pasan. Manifiestoactual
`artifacts/android-voice/basic-arithmetic-install-v1/installed.json` (APK36/64módulos);
gate de candidato corregido/activación en `artifacts/android-voice/basic-arithmetic-live-v1/`.
Instalación no equivale a aceptación humana ni a lecturaAndroid pronunciada.
UID/datos/media100%/sistema preservados; última base actual agenda3vacía con
3DELIVERED/timerreceipt1 (no imponer el viejo2DELIVERED). Volumen100% aceptado
antes sigue regresión obligatoria, no borrar su hito ni certificar alarmas/silencio.

## Aceptación humana REAL: volumen100% confirmado en Android

Humano confirma «por fin funcionó». Lectura fresca01:38:05UTC, sin mutaciones ni
reinicios del agente: APK35/62hashes correctos, reciboAndroidAPPLIED de pedido100%
y resultado100%, posterior a activación, lecturaMEDIA independiente15/15. Modelo
GLOBALrunning y sin recibo terminal actual. **El caso de volumen100% sí está
aceptado por humano+recibo+estado físico**, no solo elección/compilación. Evidencia:
`artifacts/android-voice/confirmed-volume-human-acceptance-v1/result.json`.
No inferir de un último recibo100% que se verificaron20%, todas las formas de
pedirlo, respuesta ordinaria posterior, silencio, alarmas o traducciónEN. No tocar
ni reiniciar la sesión que por fin funcionó; siguiente prioridad solo al pedirla.

## Vigente: fallo real localizado; APK35/volumen confirmado instalado

El último pedido humano sí generó `set_phone_volume` nativo válido (5,044s,
409tokens, historial0), pero `current_volume` devolvió rechazo literal: código
tool7/result2/validation4. Android nunca recibió permiso/acción y siguió8/15.
Ésta es evidencia del filtro Python, no falta de herramientas ni permisoAndroid.
No se conserva ni se infiere la transcripción exacta. Referencia numérica:
`artifacts/android-voice/dynamic-tools-diagnosis-live-v1/human-decision.json`.

Se instaló coordinadamente APK35/`confirmed_media_v3` y7 archivos Python (6previos
modificados+`volume_dialogue`nuevo;62totales), misma firma/UID10238/datos/sistema/
media. La selección nativa de volumen ahora abre propuesta, no pasa por el viejo
patrón de oración completa. Campos numéricos humanos reconstruidos Python/Android;
si falta/ambigua cifra, pregunta. Siempre confirma recurso multimedia+porcentaje:
solo un «sí» humano posterior a pregunta efectivamente terminada puede ejecutar.
Corrección numérica exige pregunta nueva, no inventar porcentaje ni interpretar
web/modelo/TTS como permiso. Una mutación por turno de confirmación, grant4s,
caducidad5min/1hRAM, scopes/nonce/recibos/Android independiente conservados.
La gramática antigua queda SOLO en modalidadlegacy no negociada por APK35 guiada.

494Python491pasan/3skipsWindows; Java64nuevoformulario+175volumen y suitecompleta;
build35v2 pasa. Son pruebas públicas/simuladas, NO aceptación de voz o Androidreal.
Manifiestoactual `artifacts/android-voice/confirmed-volume-install-v1/installed.json`;
nuevo dueño/matriz nativa/activación en `artifacts/android-voice/confirmed-volume-live-v1/`.
Silencio/alarmas/traducciónEN aún no aceptados;512traductorLOCAL no instalado.
GateLFMreal9/9 pasó: petición normal/cortés/otroorden/porciento/100/mitad/sincifra/
desdeweb/trasrechazos,2,1–6s/≤476tokens y SIEMPRE pregunta correcta sin permisoAndroid.
Prefijo39,2s<45s; erase4/segundoerase0 y preparaciónpública31,3s antes de activar.
Activación explícita35/13 observada35s; a00:37:23UTC GLOBALrunning/monitorfresco,
62hashes correctos/1recorderAndroid, sin recibo terminal nuevo. Volumen humano
todavía no aceptado: prueba pide porcentaje→preguntaoída→sí→Android→otroturno.
Observador3min final00:41:21UTC: sin decisión nueva/reciboAndroid, media8/15 y
modelo+servicio activos. No inferir si hubo intento hablado; no declarar éxito
ni reiniciar por ausencia de recibo. Evidencia `confirmed-volume-live-v1/human-volume-result.json`.
Tras «¿y entonces?» se comprobó mismo35 activo/62hashes/1recorder/TLS; proceso
backend avanza lecturas/CPU, sin decisión humana nueva ni recibo. No demuestra
reconocimiento ni atasco de activación. Falta saber si nueva35 preguntó confirmación
o no respondió. Sin otro despliegue/reinicio/acción; evidencia
`artifacts/android-voice/confirmed-volume-route-check-v1/`.

## Instrucción posterior: se autoriza cerrar la prueba; diagnóstico instalado

Humano reitera que no hay uso real/obligaciones fiables y prioriza que funcione;
esto permite parar la sesión de pruebas para corregirla, sin usarlo como permiso
para borrar datos/agenda. Cierre normal del componenteAndroid confirmado con
GLOBALnot_running,0procesos/servicios/puertos antes de instalar. El comandoam
devolvió nonzero poradb pero sí cerró; se reconcilió por lecturas independientes
SIN repetir la parada ni forzar procesos. Se aplicó SOLO `conversation_voice.py`
diagnóstico, sin APK/protocolo nuevo, UID/datos/volumen/sistema y61hashes verificados.
Manifiesto instalado actual: `artifacts/android-voice/dynamic-tools-diagnosis-install-v1/installed.json`.
Nuevo dueño y matriz nativa multi-turno/activación en
`artifacts/android-voice/dynamic-tools-diagnosis-live-v1/`. No declarar función
arreglada por instalar diagnóstico; aceptación exige petición real→reciboAndroid
y siguiente turno sin atasco. TraducciónEN pendiente no incluida. Nada de garantía
universal basada en pruebas sintéticas ni restauración automática tras fallos.
Matriz REAL vía `model.start()`8/8 pasó: frases20 en palabras/cifras y con nombre,
saludo previo,3rechazos, recordatorio, web y mezcla de acciones;2,7–6,4s, inputs
≤453tokens. Decisiones privadas reales verificadas durante prueba pública; sin
ejecutarAndroid/Worker/micrófono. Erase4/segundoerase0, prefijo público preparado
de nuevo29,6s; activación explícita con consentimiento4/Detener. A00:16:15UTC:
GLOBALrunning/monitorfresco, servicio+1recorderAndroid,61hashes correctos y sin
error terminal actual. Dueño actual en `dynamic-tools-diagnosis-live-v1/work.json`.
Observador acotado de próximo recibo técnico habilitado; no confundir los8recibos
públicos anteriores a activación con pedidos humanos.

## Aclaración humana vigente: IA dinámica, no otra lista de comandos

Humano confirma que usó EXACTAMENTE «Andrómeda, baja el volumen al veinte por
ciento» y también exige poder expresarlo de otras maneras. Informa que búsqueda
web y crear recordatorios sí funcionan; preservar esas rutas y sus obligaciones.
La meta es pedir acciones como en una conversación, no memorizar palabras clave.
No hacer otra ronda de ampliar frases/decir que pasó una compilación como solución.

La frase confirmada pasa el validador Python actual y con llamada nativa correcta
produce `volume_proposal`20 en regresión. Por tanto gramática rígida es un problema
arquitectónico, pero NO prueba la causa de este fallo concreto. El motivo del turno
real sigue sin guardarse; no atribuirlo a ASR/modelo/parser ni a Android por descarte.

Preparado LOCALMENTE un diagnóstico acotado de decisión ANTES del ejecutor:
`voice_tools_health.json`, solo números/enum, última decisión ≤4KiB, sin audios,
transcripciones, argumentos, IDs de contenido ni hashes. Distingue falta de llamada,
schema inválido, herramienta seleccionada/guardia rechazada y timeout; no ejecuta,
reintenta ni sustituye autorización.484Python481pasan/3skipsWindows; casoPOSIX
aislado13aserciones pasa en59ms, sin acceder al modelo/voz/historial ni modificar
los61módulos instalados. Candidato1módulo `conversation_voice.py`, sin APK/protocolo
nuevo, **NO instalado** para no cortar la sesión. TraducciónEN sigue otro pendiente.
Referencia `artifacts/android-voice/dynamic-tools-diagnosis-candidate-v1/`.

Dirección funcional: selección semántica nativa→propuesta tipada de acción/datos→
validación de permiso/alcance/límites/procedencia→Android→recibo real. Pedir lo que
falta o aclarar ambigüedad, no dar el rechazo genérico de otra función. Los controles
deben comprobar QUÉ se autorizó, no exigir una oración literal. Una propuesta del
modelo no es permiso: si no se puede fundamentar un dato, pedir aclaración humana
ligada al turno/propuesta; web/historial/TTS no autorizan. Mantener1mutación/turno,
plazos, identidad de recursos, verificación Android y prohibición de reintentos
inciertos. No prometer capacidad general de este chat para el modelo1.2B sin medir.

## Vigente:34/13 activo; prioridad volumen y desbloqueo anterior a ejecución

Humano informa100%/20%, alarma y silencio fallando y turno atascado. El dueño33
acabó `voice_protocol_error`; CONNECT posterior no conserva su causa exacta.
APK34 coordina ventana de volumen56,5s con cómputo55s/respuesta60s (antes26,5s),
manteniendo grant4s/no-retry/selección nativa y autorizaciónAndroid independiente.
Fallos de generación/plazo/formulario ANTES de despachar mutación liberan turno
en la mismaTLS; resultado incierto/cancelación atascada siguen parada segura.
Servicio cerrado ya no retiene caraERROR; recibo no-CONNECT conserva origen técnico.

La primera prueba del LFM real rechazó100% con la descripción vieja: no se activó
micrófono ni se maquilló como éxito. Descripción corregida con porcentajes pasó
8/8 elecciones nativas públicas, sin ejecutar volumen; solo se actualizó después
`assistant_tools_model` además de los3 módulos de recuperación.475Python:473pasan/
2skipsWindows, Java175volumen/113UI y suite/build34 pasan. Firma/UID10238/datos,
agenda3/recibos actuales, sistema y volumen preservados. Manifiesto definitivo:
`artifacts/android-voice/volume-selector-v1/installed.json`; nuevo dueño y activación
explícita en `artifacts/android-voice/volume-selector-live-v1/`. Aceptación real por
voz/reciboAndroid aún requerida, no confundir selección nativa con cambio físico.
Selector INSTALADO4/4 pasó (2,6–3,3s), prefijo frío37s, cacheerase0 confirmado y
prefijo público preparado de nuevo29,6s. Servicio/modelo35s observados activos,
comprobaciónGLOBAL posterior running/fresco y1recorderAndroid; sin recibo de
volumen humano todavía. No repetirActivar ni arrancar otro dueño para observar.
Observador3min finalizó23:48:22UTC sin recibos de volumen, media8/15 y modelo
running; no prueba que el humano haya intentado o no el pedido, ni acepta volumen.
Humano posteriormente confirma rechazo genérico en cada intento. Observación
23:53:04UTC mantiene mismo dueño/servicio/61hashes sin recibo volumen. Constante
REJECTION localizada antes de Android (`tools_rejected`), catálogo sí enviado;
no se conserva motivo concreto del rechazo porque stdout técnico va /dev/null.
No atribuir retrospectivamente a ASR/gramática/modelo ni declarar volumen arreglado.
Evidencia `artifacts/android-voice/volume-rejection-diagnosis-v1/`; sesión intacta.
No se certifican alarma ni silencio. TraducciónEN de una pasada512 sigueLOCAL:
no se instaló aprovechando este arreglo, lecturaES33 sigue intacta. Sin autorestart.

## Hito anterior:33/13 activo tras Detener accidental; traducción no aceptada

Humano aclaró parada manual. Se actualizó APK33+5 módulos web juntos con base
ACTUAL (agenda3/2DELIVERED/timerreceipt1), firma/UID/datos/sistema/volumen intactos.
Una activación explícita nueva con Detener/consentimiento4 deja modelo/voz activos,
sin autorestart. LecturaES602caracteres íntegra/0inferencias comprobada, ya no360.
Prueba nativaEN43,3s pasó cifras/forma pero OMITIÓ tarifa semestral y añadió comentario:
**fidelidadEN no aceptada**, no declarar búsqueda completa corregida por ese recibo.
Se mantiene33 activo para pruebas humanas de agenda. AjusteLOCAL de traducción en
una pasada512tokens SOLO traductor (selector96 intacto), sin trocear listas, parser
≤2000 y rechazo de elipsis/comentarios:469 Python467 pasan/2skips, NO instalado ni
probado nativamente. Referencias actual `artifacts/android-voice/web-response-spanish-live-v1/`
y pendiente `artifacts/android-voice/web-translation-whole-candidate-v1/`. Silencio y
búsqueda automática pospuestos. Todo estado apagado/candidato33 inferior es historia.

## Instrucción posterior: no apagar; candidato web33 NO instalado

**Comprobación final:** dueño cerró por `voice_peer_closed`; controlGLOBAL
`not_running`, modelo/servicios/puertos0. Agente no envió stop/kill/restart/install
ni escribió en el teléfono. Último reciboAndroid es un CONNECT posterior, no causa
del cierre original. **Modelo actualmente apagado, no reactivado a ciegas.** Aclarar
si humano pulsó Detener o vio error antes de otra activación. Candidato sigueLOCAL.

Humano confirma búsqueda a veces responde «sin fuente fiable» con JSONok y
silencio sigue fallando; deja silencio pendiente y prueba alarmas/recordatorios.
**Mantener APK32/modelo/servicio actuales activos, sin reiniciar/instalar para aplicar
web ahora.** CandidatoLOCAL33 corrige respuestaES completa (ya no prefijo360),
traduce únicamente respuestaEN de forma local/tool-free, y diferencia fallo de
traducción de falta de fuentes. Mantiene2000unidades/60s/privacidad/grants.463 Python:
461 pasan/2skips, Java65web y build33 pasan; NO aceptación nativa de traducción ni
despliegue. Capacidad nueva `worker_first_v3_spanish` requiere actualización
coordinada posterior; backendactivo no recarga. Referencia candidata
`artifacts/android-voice/web-response-spanish-candidate-v1/`. Instalado/dueño siguen
en `artifacts/android-voice/spoken-silence-final-v1/`. Búsqueda automática pospuesta.

## Estado vigente: APK32/protocolo13; fallo humano de «silencio» aún por aceptar

El humano confirma voz funcionando con31, pero «silencio» repite el error. Recibo
actual fase12/error8, decode235ms/lag182ms; motivo terminal quedó RECORDING por
carrera del diagnóstico, por lo que la causa exacta no está probada. Corregida
publicación final ANTES del callback de fallo, más recibo de11 números sin contenido.
Mismos pesos/beam/umbral0,8; perfilNN en bloques6 (60ms), sin reescribir originales,
frescura250ms y callbacks500ms intactos. Nueva prueba Vosk real con4 frases públicas
sin mic/altavoz/TLS/LFM/tools/instrumentación:2 órdenes reconocidas, negación/cita
rechazadas, edad máxima165ms; NO prueba acústica humana.454 Python:452 pasan/2skips,
Java pasa, firma/UID/datos/agenda3/sistema y61 módulosbackend preservados. Referencia
actual `artifacts/android-voice/spoken-silence-final-v1/`; activación explícita tras
corrección con Detener/consentimiento4, sin autorestart. Falta silencio real→siguiente
turno y aceptación agenda/alarmas. Apartados inferiores son historia.

### Hito anterior: APK31 y tres salidas sin fallo, sin orden humana verificada

Recibo real confirmó `STALE_AUDIO`: decode283ms con entrada fresca22ms superaba
el guard250ms. El decodificador ahora se prepara con2s de ceros públicos antes de
captura/TTS, sin parciales; cada salida usa uno nuevo y cierra el que vio al humano.
Sin bajar guardias/0,8, grammar forcing, cambios de fuente/volumen o permisos.
Tres salidas públicas reales completaron: decode máximo147ms, pre-arm376ms,
guard posterior250ms conservado. No consultaWeb/acciones falsas; eco/«silencio» y
web/agenda humanas completas siguen pendientes. Un lector nativo `tr` colgado al
cerrar se retiró por identidad; worker ahora usa lectoresNUL acotados1s y UID/start/
caps/exe.9 casosAndroid pasan y cierre nativo real sin mic confirma0 lectores/claves.
451 Python:449 pasan/2skips y suiteJava pasan. APK31/mismoUID/datos/agenda3/sistema;
modo continuo activado otra vez explícitamente con consentimiento4/Detener visible,
sin autorestart/boot/renovación. Referencia vigente:
`artifacts/android-voice/decoder-warmup-final-v1/`. Hitos inferiores son historia.

### Hito anterior: APK30 y diagnóstico sin reproducción completa

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
