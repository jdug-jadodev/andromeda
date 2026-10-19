# Micrófono activo y modelo residente · plan autorizado, no activación

## Decisión del usuario

El usuario pide empezar a planear ensayos largos con micrófono activo y LFM cargado para medir su comportamiento. **No es una orden de dejarlo escuchando hoy ni de activar inicio automático.** No esperar perfección conversacional para medir residencia; sí conservar límites de privacidad, acciones y recursos. Moto como único equipo, mismo LFM/QAD/Q4_0 y contexto 2048; sin nube, otros modelos/hardware ni cambios implícitos de carga/swap/HOME.

Este documento no inicia servicios, abre captura ni cambia ningún límite. En la entrega 0.9 siguen las ventanas manuales **90 s/300 s**, espera de cliente <=300 s y supervisor <=660 s. No basta aumentar esos números para tener un servicio permanente seguro.

**Aclaración posterior del usuario durante el primer timer real:** Andrómeda debe manejarse **100 % por voz en el uso cotidiano final**, incluido iniciar cada petición de timer. Botón/menú y preparación ADB/SSH son limitaciones de estos pilotos, no un requisito aceptado para el producto. Una vez habilitado el futuro modo residente por el usuario, se dice el nombre y la petición; tras ejecutarla se cierra la charla y vuelve a esperar por voz. Configuración inicial y parada de seguridad pueden seguir teniendo controles manuales, pero no serán obligatorios por petición. Esto fija la meta; no activa hoy captura indefinida ni arranque automático.

## Tres vidas distintas

| Vida | Comportamiento deseado para el futuro ensayo prolongado |
|---|---|
| Conversación | Nueva por defecto; recuerdo explícito ofrece retomar. Acción independiente termina tras su resultado; consulta/chiste/historia continúa. Cada charla caduca por 1 h de su propia inactividad humana. Cerrar no es borrar ni cancelar timers. |
| Captura/detector | Durante ensayo autorizado, vuelve a esperar el nombre al cerrar una charla, sin conservarla como abierta. Indicador/aviso veraz de **MICRÓFONO ACTIVO**. Pausa captura conversacional durante inferencia/TTS/alarma. Excepción 0.10: durante audio, listener local exclusivo para «Andrómeda, silencio», acotado y sin LFM; no implica captura permanente. |
| Inferencia residente | Mantiene pesos cargados, **no genera continuamente**. Un turno a la vez con plazos y cancelación. Cambiar de charla no reutiliza accidentalmente el historial/KV de otra. Detener el ensayo termina solo el servidor propio. |

**Hoy cerrar una charla todavía termina la prueba y apaga captura/modelo.** Separar esos cierres requiere un modo explícito nuevo, no cambiar silenciosamente el significado de Silenciar. En el ensayo futuro, Silenciar/Detener siempre debe parar de verdad la captura, TTS, inferencia y listener propio; los timers aceptados siguen en Android.

La interrupción de **salida** por voz pedida después es distinta de esa parada dura: `SILENCIO-ANDROMEDA.md`. 0.10 instalada y probada sin micrófono/altavoz; falta prueba acústica humana. El coordinador prolongado tendrá que conservar esta distinción, medir ASR adicional/eco y recuperar espera tras sonido sin volver a ejecutar obligaciones.

## Antes de abrir una ventana larga

1. Coordinador con estados separados: preparación → espera de nombre → charla → procesamiento → voz → cierre lógico → espera. Parada dura, sonido de alarma, error de autenticación o límite de recursos prevalecen. No reproducir promesas falsas de escucha tras una parada dura.
2. Separar propiedad del modelo de la vida de un cliente/charla. Hoy `ConversationModel.cancel()` termina inferencia y `serve()` acepta una sola sesión; hay que conservar el cierre/cancelación de un turno sin matar o duplicar el modelo residente, y una operación distinta para finalizar todo el ensayo. Watchdog independiente y plazo máximo explícito, incluso si se pierde ADB/SSH, muere la app o se rompe TLS.
3. Mantener TLS/pin/token, loopback, autenticación absoluta de 4 s, catálogo acotado, leases/IDs, un solo cliente y plazos por turno. Reconectar no repite acciones ni restaura permisos de ejecución viejos. Nueva charla no hereda contexto por quedarse cargados los pesos. No resetear el reloj de inactividad con heartbeats, clima o inferencia.
4. Captura solo tras acción visible del usuario; servicio Android en primer plano, notificación persistente con Detener y estado real de AudioRecord. Primero medir con la app/reloj visible y salida de la app como parada. Captura en segundo plano y cambios de excepciones de batería requieren ensayo y consentimiento aparte; no saltarse force-stop ni arrancar desde BOOT_COMPLETED.
5. Las primeras ventanas largas serán **sin acciones ejecutables**: nombre/ASR/respuestas y contadores de propuestas en seco. Quitar la confirmación de un timer aumenta la importancia de medir activaciones falsas; TV, voces de fondo y pérdida de una negación en ASR no se vuelven seguras por validar el texto resultante. Tras esa prueba, autorizar por separado solo timers de bajo riesgo, antes de otras acciones. Luces, alarmas, red o dispositivos todavía no están implementados.
6. Confirmar sensores disponibles de forma solo lectura, espacio de almacenamiento y guardas fiables. No escribir `force_demo_mode`: el examen anterior mostró que se limita a 35 y no es una protección fácilmente reversible. La protección de carga reversible y la validación térmica/eléctrica sostenida siguen pendientes; no aprobar 24/7 desatendido por una muestra fría.

## Escalera propuesta, cada paso manual y con fin automático

| Etapa | Condición y objetivo |
|---|---|
| Base actual, 5 min | Repetir una muestra corta sin acciones: reloj/clima, detector, LFM y TTS. Registrar reposo y varios turnos; comprobar Detener y ausencia de listener/captura/modelo residual. Cabe en los límites actuales, pero **aún no es un ensayo largo ni quedó iniciado**. |
| 15 → 30 min | Solo después del coordinador prolongado y sus pruebas sintéticas. Modelo cargado en reposo, luego micro/detector, luego combinación; observar diferencias sin tratar swap como RAM física. Una ventana por autorización, no cadena automática. |
| 1 → 2 h | Varias charlas nuevas/cerradas/retomadas, expiración humana real de 1 h, catálogo lleno y restauración sin contaminación. Verificar que el reposo vuelva al detector y no a una charla abierta indefinida. |
| 4 → 8 h | Periodos reales de silencio/ruido doméstico, cambios térmicos y ráfagas de preguntas. Comprobar fatiga de latencia, recursos, desconexión y parada física. |
| 24 h | Solo con las etapas anteriores revisadas, protección de carga y condiciones de supervisión acordadas. No habilitar arranque automático ni dar por probado uso cotidiano permanente por completar un único día. |

No ampliar simultáneamente duración, catálogo, herramientas, hardware y parámetros de inferencia: mantener una variable principal por comparación. No reabrir comparativas de modelos por defecto.

## Qué medir sin guardar conversaciones

- Muestras numéricas cada 30 s: tiempo monotónico, estado (espera/charla/TTS), MemAvailable, memoria física total, zram/swap, temperatura de batería, nivel/voltaje/estado de carga y CPU por proceso. Sensores inaccesibles se registran como ausentes, no como cero normal.
- PSS/RSS de app, ASR/backend e inferencia con sus identidades verificadas; PSS cada pocos minutos para no perturbar cada turno. No sumar métricas solapadas ni presentar RSS como PSS o swap como RAM extra. Descriptores/hilos y CPU del detector en reposo para detectar fugas/actividad involuntaria.
- Contadores de wake real/falso, rechazos, reconexiones, propuestas en seco, interrupciones y acciones aceptadas. Sin WAV humano, transcripción, tema, texto TTS ni hash de contenido humano; últimos textos solo en RAM. Logs locales acotados y sin credenciales, con limpieza explícita de la evidencia propia.
- Por turno: cantidad de historial/tokens, recortes, fin estimado ASR, tiempo de inferencia y callback TTS. **Inicio audible útil <=30 s** sigue requiriendo revisión acústica/humana: filler, errores y acuses administrativos no cuentan como aceptación. Objetivo rutinario 5–10 s, no sustituir máximo por promedio.
- Clima/reloj/pantalla se mantienen como están para la primera base; cualquier ensayo con otra pantalla, conectividad, brillo o cargador debe anotarse y acordarse, no cambiarlo silenciosamente.

## Guardas y revisión de cada ventana

- Conservar inicialmente preparación MemAvailable >=1200000 KiB y batería <38 °C; parar por MemAvailable <500000 KiB, batería >=40 °C, sensores críticos ausentes, fallo de credenciales/canal o watchdog vencido. La temperatura de batería no certifica por sí sola temperatura del SoC ni seguridad de carga.
- Límites de conversación (4 charlas, 24 turnos/5700 unidades UTF-16 cada una, entrada 1536/salida 96), un turno activo y deadlines no se eliminan por residencia. No repetir una acción por reconnect/timeout ni usar el historial como registro de obligaciones.
- Validar cancelación durante generación, retorno a espera tras acción y consulta, alarmas independientes, TTS fallido, pérdida de canal y corte de app. No hacer reboot/force-stop/desenchufado o cambios de volumen/carga como sorpresa: pruebas aparte y explícitas.
- Revisar gráficas/trend de memoria/CPU/temperatura, despertares falsos y latencias, no solo un pico o un promedio. Toda parada de seguridad invalida la aprobación de esa duración; no reiniciar automáticamente para ocultarla. Sin criterio numérico medido todavía para fuga/potencia en reposo: fijarlo desde la base, no inventarlo como resultado.
- Al terminar: micrófono/TTS apagados, sin servicio/listener/modelo propio, app/reloj recuperado, timers y datos intactos, HOME Motorola/SSH/web/SELinux conservados. Registrar duración real, motivo de fin y límites pendientes.

## Próxima decisión, no una tarea ya activa

Primero verificar con el usuario el timer 0.9 por petición y cierre; después construir y probar el coordinador prolongado sin abrir captura, y elegir juntos la primera ventana de 15 minutos supervisada. El permiso para **planear** no se convierte en permiso para escuchar horas, retirar guardas o dejar inferencia arrancada sin fecha de fin.
