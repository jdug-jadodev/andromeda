# Agenda manual · base local 0.11.0, todavía no instalada

**Documento de la base histórica código 16.** La integración posterior **código 18/0.11.1 ya está instalada con respaldo y consentimiento**: controles Android de SQLite aislada, listas vacías/timeout y TLS con actor/resultados/planificador simulados pasan, sin audio/modelo ni programación real. Entrega humana todavía pendiente. Estado vigente en `AGENDA-Y-WEB-ANDROMEDA.md`; las limitaciones y versiones de abajo describen únicamente esta base histórica.

## Petición y límites

El usuario pide mantener **Alarmas y Recordatorios en las opciones manuales**, para revisar/eliminar los que fallen mientras Andrómeda está en pruebas. Es respaldo de mantenimiento; no reemplaza el objetivo de uso cotidiano por voz ni autoriza por sí solo a abrir captura, programar eventos de prueba o reemplazar la app/RAM actual. No se decide retirar estos controles automáticamente al terminar las pruebas.

**Preparado en código/APK local `0.11.0-agenda-manual-dev`, código 16. No instalado.** La última versión instalada conocida sigue siendo 0.10.3/código 15. En esta etapa no hay creación por voz, planificador/receiver de agenda, entrega hablada, borrado tras `onDone`, recurrencias ni búsqueda web nueva. No presentar esta base como esas capacidades completas. Tampoco se inicia otra prueba de «silencio», pospuesta por el usuario.

## Interfaz preparada

Doble toque → opciones → **Alarmas** o **Recordatorios**:

- Listas separadas desde la base local, ordenadas por fecha, hasta 50 entradas por lectura; si hay más se indica cuántas se muestran. Al eliminar las visibles pueden consultarse las siguientes. No cargar una lista ilimitada en RAM.
- Mostrar contenido del aviso, fecha/hora en Bogotá, estado y un identificador breve; detalle con ID completo para distinguir eventos iguales. Una alarma sin texto no inventa una etiqueta humana.
- Mostrar programado, pendiente, entrega iniciada sin finalización confirmada, fallo o eliminación pendiente. Una fecha vencida **no se presenta como entrega realizada**.
- Seleccionar uno → detalle → **Eliminar… → Eliminar**. Sin eliminación masiva implícita ni selección del primero de varios por aproximación. No toca timers ni otros tipos de eventos.
- Verificar la selección otra vez al mutar: ID, tipo, revisión, fecha, estado y contenido. Si cambió mientras se veía, pedir abrir/seleccionar de nuevo, no borrar una versión distinta.
- Error de lectura no equivale a lista vacía. En una agenda vacía la UI avisa que creación/entrega todavía están en desarrollo; no muestra fixtures como registros reales.

Las opciones conservan su cierre por 50 s sin interacción. Tres filas dentro de ScrollView para poder alcanzar los controles en horizontal con poco alto. Lectura/borrado SQLite fuera del hilo principal. Si el usuario cierra el diálogo, vence su timeout o sale de la actividad, no se reabre automáticamente por un resultado tardío; una eliminación ya aceptada no se cancela silenciosamente al destruir la actividad.

## Datos y borrado selectivo

`AgendaStore` es SQLite privada (`agenda.db`), separada de `timers.xml`, recibos y contexto. Solo columnas `id`, `kind`, `text`, `due_ms`, `zone`, `state`, `revision`. Nada de petición completa, audio, tema de charla, transcripción ni hash de contenido. Se crea al acceder al menú, no abre micrófono, red ni salida. `allowBackup=false` permanece. WAL no habilitado y `secure_delete=ON`: ayuda a limpiar páginas de SQLite, **no garantiza borrado forense de flash, swap ni acceso con root**.

La transacción manual:

1. Comprobar la selección vigente; guardar `CANCEL_PENDING`, borrar su texto y aumentar revisión antes de tocar los efectos externos.
2. Intentar detener solo el output registrado con ese ID y retirar solo su PendingIntent. Cada efecto se intenta aunque falle el otro. No usar RingService/Silenciar global como sustituto, porque podría detener otro evento.
3. Eliminar la fila solo tras ambos acuses y comprobando ID/tipo/revisión/estado bloqueado. Si falta un acuse o falla el borrado, conservar la entrada bloqueada **sin contenido**, visible como eliminación pendiente para otro intento manual. Si no se confirmó el bloqueo, no afirmar que se borró o quedó bloqueada.

`AgendaCancellation` expresa esta coreografía sin Android para pruebas con efectos falsos. `AgendaOutputs` conserva hasta 20 manejadores propios, sin abrir audio ni captura y sin sustituir un propietario activo. Es un contrato para la futura entrega, no prueba de que un altavoz real se detenga.

El adaptador de cancelación **solo busca** un PendingIntent existente (`FLAG_NO_CREATE`), sin programar ni crear ninguno. Reserva la identidad explícita `.AgendaReceiver`/`local.doja.voice.AGENDA.<uuid>` para la integración futura; **ese receiver aún no está implementado ni declarado**. No hay nueva ruta ejecutora desde backend, instrumentación, boot o UI de creación. El almacenamiento actual no tiene método público de inserción de eventos.

## Obligaciones antes de habilitar creación/entrega

- El ejecutor por voz debe usar este mismo almacén, no una copia decorativa para los menús. Propuesta nativa del LFM y validación literal completa, fechas/zonas no inventadas, autorización de un uso/turno y acuse Android real antes de anunciar éxito/cerrar.
- Toda entrega debe comprobar registro y revisión/estado antes de aceptar salida; registro bloqueado/eliminado o callbacks viejos no pueden reproducir ni recrear nada. Coordinar aceptación/registro de output con el bloqueo manual para evitar una salida que se registre después del intento de parada.
- Usar la identidad fija de PendingIntent del almacén. No consumir/borrar contenido al recibir el broadcast ni solo por iniciar TTS. Después de `onDone` satisfactorio, borrar contenido/registro activo sin confirmación humana extra; fallo/interrupción conserva estado recuperable. Recibo técnico sin contenido, si se integra, acotado a 48 h; todavía no implementado aquí.
- Recuperación de reinicio/reloj/force-stop, límites, coexistencia y entrega sin LFM requieren pruebas propias. Sin promesas de sonar apagado, repetición automática indefinida o puntualidad OEM absoluta. No habilitar recurrencias implícitas.
- Búsqueda web general con fuentes sigue como siguiente parte del bloque autorizado, sin Gemini/AI Overviews ni inferencia en nube. El prototipo de Wikipedia no equivale a búsqueda general instalada.

## Verificación local, no aceptación en el Moto

**258 tests Python, 710 aserciones JVM totales** (114 nuevas de gestión de agenda), build/firma y diff-check pasan. Casos: zona Bogotá independiente de zona por defecto, datos inválidos, fecha vencida no entregada, todos los estados eliminables, selección vieja/tipo ajeno, orden bloqueo→stop→cancel→remove, fallos parciales sin éxito inventado, reintento manual, revisión que cambia durante limpieza, solo parar output elegido, rechazo de reemplazo/excepciones y límite del registro de outputs. Python ejecuta el DDL extraído con SQLite de PC y verifica campos/restricciones/SQL selectivo y aislamiento de código.

**No se ejecutaron Android SQLite ni menús reales en el teléfono, AlarmManager real, TTS, micrófono o prueba acústica para esta base.** Las pruebas de fuente/esquema/efectos falsos no certifican esos comportamientos. No ADB, reinstalación/instrumentación, preparación de inferencia ni lectura de RAM humana durante este trabajo; no se modificaron timers/recibos/HOME/carga/swap/conectividad del teléfono.

Build separada: `android-voice/build.ps1 -ApkName agenda-manual-candidate.apk`. APK `artifacts/android-voice/agenda-manual-candidate.apk`, SHA-256 `3faf6af3c2871e515616f1bb0dbff858cb9f1113ea47f12372ef1c06e5728913`. La opción nueva de nombre mantiene el comportamiento predeterminado del build, pero permite no reemplazar la referencia local de 0.10.3: `doja-voice-pilot.apk` conserva SHA-256 `cd08ac7c33284e09a7b807d5705ffaf62b21715e50728bd309e0b8ecde68e6f2`.

No instalar esta base sola como si ya completara el bloque de alarmas/recordatorios/web. Antes de una actualización del Moto: respaldo, guardas/estado de acciones, consentimiento de pérdida posible de charlas/diagnóstico RAM e integración validada de lo que se entregue. Sin commits, pushes o merges.
