# Mejoras pendientes y orden de pruebas de Andrómeda

## Prioridades actualizadas tras la última prueba humana

**Decisión posterior del usuario: conservar arquitectura/modelo locales y mejorar
flexibilidad e integración, una herramienta cada vez hasta validarla realmente.**
Crear, listar y ahora **borrar recordatorios** están aceptados en los casos
reportados por el humano. También reporta alta correcta a ocho horas; no extrapolar
a entrega/sonido ni a todas las formulaciones. Fechas con año/«pm» y días fallaban
en39; **ahora el usuario acepta el funcionamiento de fechas y hora faltante en su
prueba de40**. No confirma por separado cada formato ni los días relativos.
**APK40 instalada tras autorización nueva de detener/validar/actualizar:** mejora
fechas y pregunta la hora si solo se dice un día. Gate nativo20/20requeridos y
59checks Android aislados pasan; aceptación humana limitada a su caso reportado.
No borrar/programar avisos reales desde este chat ni modificar la nueva sesión.
**AL-01 queda aceptada en los casos humanos reportados:** la alarma semanal «los
martes a las 8 de la noche» y la alarma puntual «hoy en dos minutos» funcionaron.
El apagado de voz tras los intentos anteriores no se reprodujo en esta sesión, pero
no se declara resuelto para toda formulación. No repetir alarmas ni cambiar software
desde este reporte.
Primer ajuste local instalado: `hoy en/dentro de X minutos` conserva la cuenta atrás
explícita para alarmas, mientras `hoy a las HH:MM` sigue siendo hora fija. Backend y
guardia Java quedan alineados; 551 pruebas Python (4 skips Windows) y 511 aserciones
de calendario Java pasan. El caso humano exacto quedó aceptado en APK41.

La sesión39 (`andromeda-resident.MvPDHh`/inode119934) fue cerrada por permiso actual,
confirmado20:51:26UTC. Baseline fresco: **1REMINDER_SCHEDULED**, recibos2CREATED/
5DELIVERED,0timer/1recibo. Mismo UID/datos/agenda/HOME/SSH/web/swap/sistema y volumen
**MEDIA8/15** preservados. El aviso existente sigue registrado en AlarmManager,
verificado antes/después de instalar y tras la prueba aislada mediante conteos y
comparación de IDs solo en RAM del teléfono, sin exportar IDs/fechas/contenido/hash.
No se exigió vacío, restauró100%, ni se repitió el aviso de8h.

Referencia instalada40/65hashes: `artifacts/android-voice/calendar-date-live-v1/installed.json`;
transacción en `calendar-date-install-v1/`. Dueño actual
`andromeda-resident.qnchgT`/inode124693, activación explícita2591088587/20:58:28UTC.
Arranque público54,137s<55s, TLS listo y35s observados. Lectura20:59:30UTC:
GLOBALrunning/65hashes/1recorderAndroid, sin recibo terminal o fallo de protocolo
actual. Rechazó32PCM tardíos en1gap y se resincronizó (estado2), sin decodificarlos,
reintentar acciones o reiniciar. No afirmar cero retrasos de audio ni ASR perfecto.
STOP15/error0/ended2590661923 es el cierre39 autorizado; EOF697/volumen50→53% y
listado2588261899 son anteriores a40, no éxito/fallo/acción nuevos.
Sistema/agenda1SCHEDULED/registroAlarmManager/8/15 releídos y conservados20:59:31UTC.
Gate nativo20/20requeridos,20/21total:
el indirecto conocido sigue abierto y NO se oculta como aprobado. Android aislado:
59checks/4fixtures SQLite/wire, sin agenda humana ni planificador/audio reales.
Cero avisos reales, consultas Worker o cambios de volumen inyectados por el agente.
Las lecturas/recibos de sesiones38/39 son históricos: no confundir su cierre con un
fallo actual40. No imponer este snapshot/volumen a cambios humanos futuros.

La tabla distingue hechos reportados de causas aún no diagnosticadas. No guarda
grabaciones, transcripciones del teléfono ni huellas de contenido humano.

| Orden propuesto | ID | Fallo prioritario y estado | Criterio para darlo por resuelto |
|---|---|---|---|
| 1 | ASR-01 / REM-01 | **Aclaración, alta y listado aceptados por el usuario en sus casos probados de APK38.** No generalizar a toda redacción ni declarar ASR perfecto; permanece la selección indirecta fallida del gate. Sin nueva lectura técnica de recibos/entregas. | Mantener preguntas ante ambigüedad, autorización actual y recibo Android, sin exigir una frase memorizada. Conservar el alta/listado aceptados mientras se completa el borrado. |
| 1b | REM-DEL-01 | **El usuario confirma que el borrado funcionó en su prueba de39.** Selector y93checks Android aislados previos pasan; no se leyó/exportó un recibo humano nuevo por este reporte ni se confirma todo caso posible. | Conservar identificación/aclaración/confirmación fresca, permiso único y recibo Android. No regresión de alta/listado/borrado, ni reintento incierto. |
| 1c | REM-FECHA-01 | **El usuario confirma que funcionó y lo valora favorablemente después de probar fechas/hora faltante en40.** Aceptación limitada al caso reportado, sin lectura nueva de recibos/entregas ni confirmación de cada formato/días relativos. Nativo20/20requeridos y59checks Android aislados previos pasan. | Conservar fecha/hora literales y preguntas sin asumir campos, minutos/horas/listado/borrado y aclaración. No declarar generalización total ni sonido/entrega verificados. |
| 2 | AL-01 | **Aceptada en los casos reportados:** martes20:00 y «hoy en dos minutos» funcionaron. No generalizar a toda formulación; el apagado anterior no se reprodujo, pero queda sin causa aislada. | Conservar cuenta atrás explícita, hora correcta, sonido y detención sin regresión. No repetir acciones inciertas. |
| 2, actual | VOZ-01 | Se corta una respuesta de orientación y vuelve a escuchar. **Reportado; causa sin aislar.** No afirmar que cayó el motor. | Diagnóstico numérico de límite de reproducción/interrupción/error; frase completa o aviso inequívoco, conservación correcta del estado y ninguna confirmación de acción inexistente. |
| 4 | TTS-01 | El sintetizador pronuncia el emoji del chiste y perjudica su comprensión. **Reportado; abierto.** | Separar presentación de texto y pronunciación; tratar adornos/emoji sin alterar nombres, números o significado ni deformar la lectura web. Chiste hablado comprensible. |
| 5 | CU-01 | Historias rechazadas por una supuesta falta de herramienta; otro ensayo público se truncó. **Reproducido; abierto.** | Narración española original con final, sin excusas de funciones, sin corte y con continuación conversacional. Cuentos no necesitan una herramienta de ejecución. |
| 6 | BG-01 | Cerrar/minimizar la interfaz detiene la voz y, al cerrar el canal, el motor. **Registrado; pendiente de implementación.** | Servicio independiente de la ventana con notificación/Detener y guardias, respetando cierre forzado y sin autoreinicio. |

### Conservar lo que el usuario considera útil

- **Aclaraciones ante errores de reconocimiento:** valoración humana muy positiva
  tras dos casos: apertura de recordatorio y confirmación de volumen. En volumen,
  una afirmación fue reconocida como otra palabra y Andrómeda volvió a preguntar.
  Aceptación limitada a la repregunta, no a corrección general del ASR ni a un
  porcentaje aplicado/recibo Android nuevo. Conservar este comportamiento sin
  inventar confirmaciones; no modificar la sesión ni guardar sus transcripciones.
- **Listar recordatorios:** aceptación humana provisional («bien por ahora»),
  limitada a lo observado, no a toda consulta posible ni al alta de avisos.
- **Alta y borrado:** aceptados en los casos reportados, incluido alta a ocho horas;
  después se aceptan fechas y pregunta de hora en una prueba de40. No extrapolar a
  toda fecha/formulación, días relativos no confirmados por separado, entrega o alarma.
- **Búsqueda en Internet:** valoración humana positiva en las pruebas actuales.
  Preservar el comportamiento instalado y la lectura fiel; traducción inglesa
  completa y búsqueda automática mantienen sus pendientes anteriores.
- **Chistes:** hay respuesta, pero su voz no está aceptada por el problema del emoji.
- **Volumen:** conservar el hito anterior100% sin extenderlo a alarmas u otras acciones.
  La prueba humana posterior acepta la repregunta ante confirmación mal reconocida;
  no confirma un ajuste nuevo ni su porcentaje. El nivel actual puede haber cambiado
  por uso humano: leerlo de nuevo cuando corresponda, nunca restaurar un valor histórico.

Se corrige la etiqueta anterior de «funciones estables»: liberar un subconjunto
para ensayo no fue aceptación humana de alarmas o recordatorios completos. No
volver a presentar el gate de propuestas como prueba de funcionamiento real.
Luces y TV conservan su inventario y orden futuro, pero no desplazan estos fallos.

### Decisión de arquitectura: conservar el diseño, mejoras incrementales

Tras la explicación de `ARQUITECTURA-ANDROMEDA.md`, el usuario elige conservar el
diseño y hacerlo más dinámico ante transcripciones imperfectas. No cambiar de
modelo/proveedor ni iniciar OAuth/API/billing; sigue sin consentimiento para
exportar conversaciones/audio. Mantener autorización y recibos independientes,
sin sustituir el modelo por una lista creciente de comandos memorizados.

### REM-01: aclaración, alta y listado aceptados en las pruebas reportadas

En el primer reporte, el usuario confirmó que, ante la transcripción imperfecta,
Andrómeda preguntó y consideró correcta esa parte. **Ese primer hito se limitaba a la aclaración**, no
a corrección general del ASR, toda formulación, contenido/horario, guardado, sonido
o continuación. No guardar la transcripción ni consultar/exportar contenido del
teléfono por este reporte. Sin cambios, despliegues o reinicios sobre su prueba.

Reporte humano posterior: el usuario confirma que el recordatorio lo hizo bien y
que también lista. Se acepta esa experiencia de alta/listado, sin convertirla en
éxito para toda redacción o en una lectura técnica nueva de recibos/entregas. La
eliminación se aceptó después en un caso humano de39; ahora se abordan fechas/días
antes de pasar a alarmas.

- Tras selección nativa `create_reminder`, un texto relacionado pero no interpretable
  como alta válida puede abrir «¿Quieres crear un recordatorio?», nunca un permiso.
  No hay corrección fonética del ASR ni ejecución desde argumentos inventados.
- Solo una respuesta afirmativa NUEVA, posterior al final de esa pregunta hablada,
  permite pasar al contenido y al momento, también pedidos en turnos nuevos. No
  se rescatan datos de la transcripción dudosa. Negaciones/citas/mezclas no crean
  autorización; cancelación/interrupción/caducidad revocan el formulario en RAM.
- Python y Android reconstruyen independientemente la misma secuencia. El horario
  se confirma solo tras recibo Android coincidente; permiso de un uso, sin reintento
  de un resultado incierto. No cambia alta directa inequívoca, listado ni web.
- Política coordinada `confirmed_reminder_intent_v1`; clientes/backend incompatibles
  se rechazan antes de restaurar contexto. No actualizar solo una mitad.
- Primera suite/APK en `artifacts/android-voice/reminder-clarification-candidate-v1/`;
  suite final530Python:526pasan/4skipsWindows, Java pasa con132aserciones nuevas y
  build/firmaAPK38 comprobados. Los fixtures son públicos y los efectos simulados.
- La prueba real del selector fue necesaria: varios prompts no pasaron y quedan
  conservados en `reminder-clarification-native-v1`…`v6`, nunca como gates aprobados.
  El instalado compacto usa una demostración pública de llamada nativa, NO reglas
  de sustitución ASR ni datos del humano. Selecciona aclaración para las variantes
  ambigua/ayuda/fragmento y mantiene las selecciones de alta completa/listado/volumen/web.
  **8/9: una solicitud indirecta sigue seleccionando listado indebidamente.** La
  validación la rechaza sin leer agenda ni crear permiso; regresión específica añadida.
  Se libera explícitamente solo como versión experimental para la prueba solicitada,
  sin quitar el caso fallido ni etiquetar REM-01 resuelta. Web aquí solo seleccionada,
  no se consultó el proveedor ni se probó una traducción nueva.
- Dos arranques públicos a45s agotaron preparación. Se acortó el prefijo a506tokens
  y se ajustó SOLO preparación pública a55s, por debajo del guardia nativo90s.
  Respuesta humana60s/cómputo55s y permisos de agenda26,5s/uso único NO ampliados.
  `reminder-public-preparation-fix-v1/` verifica la diferencia de una sola línea y
  las pruebas de separación de plazos. Arranque reparado: fase pública46,735s,
  TLS listo y luego35s de voz observada; no autorestart/reconexión/renovación de prueba.
  El reporte humano posterior acepta alta/listado en sus casos; no se ha leído un
  recibo nuevo del teléfono por ese comentario. No extrapolar a toda formulación ni
  a alarmas. Las pruebas del selector/políticas por sí solas no resuelven ASR-01/REM-01.

### REM-FECHA-01 — instalada40; fechas/hora faltante aceptadas en su prueba

Reporte posterior: tras la prueba y aclaración sobre fecha sin hora, el usuario
confirma funcionamiento y valoración muy favorable. Se acepta ese caso y se pasa a
AL-01, no a cambios de arquitectura. No se ha consultado el teléfono por este
reporte, ni leído un recibo humano nuevo; sin transcripciones/audio/contenido/hash
exportados, sin alta/listado/borrado inyectados ni modificación de la sesión.
No afirmar aceptación independiente de días relativos, todos los formatos o entrega.

- Fuente congelada de39 verificada contra su manifiesto instalado, sin consultar
  teléfono: `calendar_due` no reconocía AM/PM en fechas puntuales, solo en el parser
  semanal; exigía «de» antes del año y rechazaba días al pasarlos al temporizador
  limitado a24h. Se corrige también el separador «contenido para el día»: antes podía
  conservar un «para» sobrante en el contenido. Fixtures públicas diferentes de la
  petición humana; no se guardó una transcripción ni se programó su ejemplo.
- Backend y Android independientes admiten fecha explícita + AM/PM (incluidas
  variantes de ASR), «de/del» y año numérico o «dos mil…», además de «en/dentro de N
  días a las…». Siempre zonaBogotá, futuro y máximo366días. Fechas imposibles,
  pasadas, recurrencias de recordatorios, múltiples acciones y cambios de argumentos
  siguen rechazados; no se corrigen meses mal reconocidos por similitud.
- Si una fecha válida carece de hora, se conserva SOLO la fecha numérica en RAM y
  se pregunta la hora con esa fecha leída. Una respuesta posterior inequívoca, tras
  terminar la pregunta, completa el formulario; «cinco» sin período repregunta sin
  asumir AM/PM. Fecha relativa congelada al turno que la dijo, no desplazada si
  cruza medianoche. Fecha completa nueva reemplaza la parcial de forma explícita;
  reemplazo inválido retira la parcial. Cancelar/interrumpir/caducar limpia el estado.
- No nueva herramienta, prompt, modelo, contexto, límite de salida/latencia,
  privilegio, esquema o Worker. Los7tools/auto y payload son idénticos a39, comparados
  con artefactos locales congelados; el gate nativo posterior se detalla debajo.
  Se conservan permiso de un uso/una mutación/recibo real.
- Evidencia local, build40 y hashes en
  `artifacts/android-voice/calendar-date-candidate-v1/`:550Python,546pasan/4skipsWindows;
  suiteJava pasa con507aserciones nuevas y34casos compartidos de fecha. Build/firma40
  y `git diff --check` pasan. Regresiones de aclaración132/borrado144/alarma208/
  volumen64 conservadas. `public-regression.json`
  sustituye la reproducción inicial por stdin que dañó tildes al pasar por PowerShell:
  esa ejecución inicial no prueba ASR. Regresión nueva usa UTF-8 verificado.
- Después del permiso nuevo: cierre seguro39, gate nativo con20/20requeridos y
  20/21total,581tokens y preparación43,811s<55s; las nuevas fechas2,014–5,086s bajo
  26,5s. Alta/listado/borrado/volumen/web/timer/selección semanal conservados;
  indirecto anterior falla igual. Cero acciones/agenda/micrófono/Worker en el gate,
  cache retirada4→segundo0 y dueño de ensayo cerrado sin reinicio. Evidencia
  `artifacts/android-voice/calendar-date-native-v1/`.
- Instalación coordinada de APK40build-v2 y SOLO3módulos calendario permitidos
  (65totales, ninguno nuevo), mismafirma/UID/datos y esquema3. El build-v2 añade solo
  `CalendarDateInstrumentation`, sin cambiar los núcleos ya probados del build-v1;
  `phone-release.json` congela el APKhash instalado
  `e3768872dfbf490e54aa82afc587d753b9ee2eec183b2381ad3655b1e7521dad`.
- Android real:59checks/4registros en `agenda-isolated-test.db`, planificador/salida/
  acuses TTS ficticios. Fechas PM/año hablado/días, pregunta de hora/repregunta,
  permiso único/recibo SQLite durable y rechazo de replay/origen/due alterados,
  mes inválido y pregunta no escuchada. No abre agenda humana ni AlarmManager;
  elimina solo DBaislada. Agenda humana1SCHEDULED y registro AlarmManager existentes
  conservados. Resultados en `calendar-date-install-v1/android-isolated-result.json`.
- **Aceptación posterior:** fechas/hora faltante funcionan según el humano en su
  prueba; listado/entrega y todos los formatos no se inspeccionaron de nuevo desde
  el chat. Se conserva ese hito y se pasa a alarmas. No repetir el aviso8h ni programar
  ejemplos desde el chat. Sin más despliegues
  sobre esta nueva sesión. El desarrollo inicial fue solo local; las operaciones
  posteriores se hicieron únicamente tras esta autorización actual.
- Sesión40 nueva arrancada una vez por esta petición, con app visible y Detener;
  preparación54,137s, TLS listo y35s observados, después1recorder y monitor frescos.
  No ampliar55/60/26,5s ni ocultar el margen estrecho de preparación. Un gapPCM
  recuperado sin cierre está registrado, no una caída de motor. No más instalaciones
  o reinicios durante la prueba humana; BG-01 aún sin implementar.

### REM-DEL-01 — instalada39; borrado aceptado en la prueba humana reportada

- Añadida séptima herramienta nativa `delete_reminder`, de cero argumentos y con
  `tool_choice=auto`. Requiere política coordinada `confirmed_reminder_delete_v1`;
  cliente/backend incompatibles se rechazan antes de restaurar contexto. La versión38
  fue detenida por autorización humana actual;39 incorpora la séptima herramienta.
- Android identifica por contenido literal normalizado, no por un nombre/ID
  inventado por el LFM. Si falta, pregunta; si hay coincidencias, lee hasta tres
  opciones numeradas de una instantánea nueva y pide cuál. No usa números de una
  lista antigua ni el historial como autorización. Si ninguna corresponde o la
  lista es incompleta, se cancela/revisa manualmente: no se adivina un destino.
- La pregunta final incluye contenido y horario reales, formados/hablados solo
  en Android. Al backend solo vuelven estado/fase y la identidad/revisión del
  destino individual, nunca contenidos, candidatos, fechas textuales ni sus hashes.
  Solo una afirmación fresca tras terminar esa pregunta permite un borrado de un
  uso; una confirmación mal reconocida repregunta sin corregirla silenciosamente.
- Borrado sellado separado de alarmas. Reutiliza bloqueo/redacción → detener solo
  esa salida → cancelar solo su programación → retirar esa fila, con recibo durable
  y revisión. Destino cambiado, cancelación incompleta o timeout no producen éxito
  ni reintento automático. No cambia esquema ni borra la base de datos.
- 540 pruebas Python:536pasan/4skipsWindows; suiteJava pasa,144aserciones nuevas de
  destino/ambigüedad/confirmación/fallos con almacenamiento y efectos ficticios.
  Build/firmaAPK39 y comprobación de whitespace pasan. Evidencia y hashes en
  `artifacts/android-voice/reminder-delete-candidate-v1/local-validation.json`;
  APK final instalado en `build-v3/doja-voice-reminder-delete.apk`; añade únicamente
  el contrato Android aislado al runtime ya validado de build-v2. Manifiesto/hashes
  actualizados en `phone-release.json` de esa carpeta.
- **Validación nativa real:** siete herramientas/auto,581/1536tokens, prefijo público
  43,752s<55s. Tres variantes de borrado llaman `delete_reminder`2,562–3,421s.
  Se conservan ocho casos anteriores y selección de alarma/timer/chiste:14requeridos
  pasan; el indirecto anterior mantiene el fallo,14/15total. Sin acciones Android,
  lecturas de agenda, micrófono o consultas Worker. Cache retirada4tokens y segundo
  erase0; dueño de ensayo cerrado sin reinicio automático. `reminder-delete-native-v1/`.
- **Android real, datos ficticios:** `ReminderDeleteInstrumentation` pasa93checks
  sobre `agenda-isolated-test.db` con planificador/salida/TTS ficticios. Comprueba
  selección, coincidencias, repregunta, permiso único, recibo durable, rechazo de
  revisiones cambiadas/operaciones repetidas y cancelación fallida→UNKNOWN/tombstone.
  No abre la agenda humana ni usa AlarmManager/audio/modelo; elimina solo su DB
  aislada. Resultados en `reminder-delete-install-v1/android-isolated-result.json`.
- Instalación coordinada APK39 +6módulos permitidos (uno nuevo,65totales), misma
  firma/UID/datos y agenda/volumen actual preservados. Arranque reparado único de39:
  fase pública53,608s, TLS listo,35s observados y lectura fresca con1recorder; guardias
  públicas55s/respuesta60s/ejecución26,5s intactas. No autoboot/reconnect/renovación.
- **Reporte humano posterior:** confirma que el borrado funcionó. Aceptación limitada
  a esa experiencia; no se leyó un recibo humano nuevo ni se exportó contenido para
  verificarlo desde el chat. El siguiente fallo humano son fechas/días, descrito
  arriba. No más ensayos/despliegues sobre esta sesión activa ni volver a usar
  ejemplos públicos como permisos reales.
  Conservar Worker instalado: no sincronizar el candidato EN ni helpers distintos
  solo por igualar fuentes. No modificar alarmas, luces, TV o BG-01 en este bloque.

Antecedente: lectura21/10/2026 17:15:59UTC:GLOBALrunning, APK37/64hashes correctos,
dueño `andromeda-resident.lSY23c`/inode124964. Sin detener, reinstalar o reiniciar.
No usar los recibosCONNECT70/EOF697 anteriores a esta activación como fallos actuales.
Ese dueño37 fue detenido tras consentimiento actual del usuario y no se reabrió.
La sesión38 se detuvo después por consentimiento actual para instalar39. No tocar
la sesión39 vigente. No exigir agenda vacía ni restaurar niveles antiguos: el8/15
actual fue leído y conservado, no impuesto. Mantener interfaz visible: BG-01 sin implementar.

## BG-01 — Escucha independiente de la ventana

**Estado: registrada y pendiente. No implementarla durante las pruebas actuales.**

El usuario cerró la app al acostarse y esperaba que Andrómeda siguiera disponible
toda la noche. En la implementación actual `MainActivity.onStop()` detiene
`VoiceService` (`android-voice/src/local/doja/voice/MainActivity.java`). Al cerrarse
el canal, el backend apaga el motor y no lo reinicia automáticamente.

Comprobación posterior del teléfono: última sesión `voice_peer_closed`, sin fallo
de protocolo ni guardia de memoria/temperatura. Es consistente con el cierre de
la app; no demuestra por sí sola cada paso ocurrido durante la noche.

### Comportamiento deseado

- Cerrar/minimizar la ventana o apagar la pantalla no equivale a **Detener** cuando
  el usuario ha activado explícitamente la escucha continua en segundo plano.
- Servicio en primer plano con notificación visible, estado del micrófono claro
  y **Detener** siempre accesible. Sin ocultar grabación ni ignorar privacidad.
- **Detener**, el cierre forzado de Android y las guardias de seguridad siguen
  apagando la sesión; sin reanimación, reconexión, autoboot ni renovación de prueba.
- Mantener límites de RAM, caducidad de una hora sin actividad humana, plazos de
  respuesta, exclusión de llamadas/avisos y preservación de datos/agenda/sistema.
- No prometer disponibilidad nocturna si Android o una guardia detienen el proceso.

### Aceptación pendiente

Ventana visible → Inicio/otra app → pantalla apagada → volver a la interfaz,
comprobando el mismo dueño y una sola captura; después **Detener** y cierre
forzado, verificando que no se reinicia. Prueba prolongada con métricas técnicas,
sin guardar audio, transcripciones ni huellas de contenido humano.

Mientras no se implemente: mantener abierta la interfaz durante estas pruebas.
No aflojar las guardias ni modificar `onStop()` como parte de las luces.

## CU-01 — Cuentos originales completos, sin excusas de herramientas

**Estado: fallo reproducido; abierto. Candidato nuevo NO instalado.**

En la nueva prueba pública un cuento de tres frases recibió una negativa ficticia
de falta de herramientas. Una instrucción creativa candidata no lo resolvió: otra
formulación sí empezó una historia, pero llegó al límite de96tokens sin terminar.
No se acepta una oferta de escribirlo, una negativa ni un relato truncado como
cuento correcto. Mantener criterios semánticos: historia original acorde al pedido,
breve y con desenlace, en español; continuar luego con otra petición.

Evidencia pública en `resumed-tests-lights-plan-v1/public-gate.json` y
`creative-probe.json`. No son grabaciones/transcripciones humanas. No se cambian
presupuestos, APK, prompts instalados o validadores para ocultar el fallo.
Se retoman recordatorios/alarmas/chistes/consulta con la versión estable sin
declarar resueltos los cuentos; el fallo creativo no autoriza acciones ni impide
probar esas funciones. No seguir modificando una conversación activa.

## Orden solicitado para esta sesión

1. **Recordatorios:** crear por petición humana nueva, preguntar datos faltantes,
   confirmar el guardado Android y el horario real; consultar la lista sin inventar
   datos; comprobar entrega y continuación de la conversación.
2. **Alarmas:** validar recurrencia —por ejemplo martes a las 20:00— y próxima fecha,
   comprobar recibo Android y sonido real solo con autorización actual. No repetir
   intentos antiguos ni crear desde aquí el ejemplo mencionado en el chat.
3. **Chistes y cuentos:** respuestas españolas reales, cuentos breves completos,
   sin negativas ficticias de falta de funciones ni cortes o cierres de sesión;
   otra petición después debe seguir funcionando. Un cuento largo sigue limitado
   por los presupuestos instalados; no ampliar esos límites sin medirlo.
4. **Luces:** LotusLamp X `MELK-OA10 17` y MR Star `GATT--DEMO`. Identificar
   dispositivos y protocolos reales antes de escribir; cada integración se valida
   por separado. Inventario y límites en `LUCES-NEO-LOTUSLAMPX-MRSTAR.md`.
5. **TV:** después de las luces; confirmar modelo, transporte y autorización de
   emparejamiento. No dar por resueltas autenticación/pairing ni encender/apagar
   por un ejemplo, una respuesta web o un resultado incierto.

### Reglas de las pruebas

No exigir frases memorizadas. Mantener selección nativa, autorización humana
vigente, una mutación por turno, validación Android independiente y recibos.
Separar pruebas públicas/simuladas de aceptación humana y de ejecución real.
No borrar obligaciones para dejar una agenda vacía; tomar una instantánea actual.
No conservar/exportar grabaciones, transcripciones o hashes de contenido humano.
No desplegar sobre una conversación activa ni volver a ejecutar acciones inciertas.

## Punto de partida comprobado

Teléfono reconectado; APK37 y los 64 hashes instalados coinciden con
`artifacts/android-voice/explicit-tools-live-v1/installed.json`.
Motor y voz estaban apagados tras el cierre de la sesión anterior. Instantánea
actual: 0 entradas, 3 recibos `DELIVERED`, 0 timers y 1 recibo de timer; es una
lectura puntual, no una condición que deba seguir siendo cierta.

Evidencia técnica: `artifacts/android-voice/resumed-tests-lights-plan-v1/before.json`.
El usuario autoriza ahora una nueva sesión de pruebas; no es reconexión automática
ni una prolongación de la sesión que cerró al dormir.

La nueva prueba pública reprodujo una negativa ficticia para cuentos; recordatorio,
alarma, chiste y consulta seleccionaron la respuesta/herramienta esperada. No se
activó voz tras ese gate fallido. Se valida una instrucción creativa general antes
de instalar cambios, conservando modelo, catálogo y presupuestos; no respuestas
prefijadas para cuentos. Resultados en la misma carpeta de evidencia.

Luces: revisión pública de dos familias BLE y lectura local de preparación; SDK29,
radio apagada y sin permisos BLE/ubicación en Andrómeda. No escaneo ni conexión o
escritura. No se abrirá la radio ni instalará un APK sobre la conversación activa.

## Sesión disponible para pruebas humanas

Se activó explícitamente la versión estable SIN instalar el candidato creativo:
APK37/64módulos, dueño `andromeda-resident.lSY23c`/inode124964. Lectura fresca
15:26:59UTC confirma motor/servicio/una captura Android activos y sin fallo actual.
Se mantienen abiertos BG-01 y CU-01. El subconjunto de recordatorio/alarma/chiste/
consulta pasó el gate público, pero no sustituye el guardado/sonido humano real.
No se reintentó ninguna petición antigua ni se crearon eventos desde este plan.

Próxima interacción: el usuario pide un recordatorio con sus propias palabras;
se comprueban preguntas, confirmación Android, lista y continuación. Después alarma
y resto de pruebas. Mantener interfaz visible; no cambiar/desplegar sobre la sesión.
