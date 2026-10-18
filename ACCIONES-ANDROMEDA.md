# Timers, alarmas y recordatorios · diseño separado del contexto

## Regla principal

**La IA interpreta y propone; Android guarda/programa y confirma hechos reales.** El modelo no se queda contando segundos y la memoria de conversación no es el registro de acciones. Olvidar una charla o caducar su contexto no borra obligaciones ya confirmadas. Silenciar detiene captura/voz/modelo, **no cancela timers**; detener un sonido y cancelar un evento son operaciones distintas.

La conversación instalada **0.7 sigue sin herramientas/ejecutor**. Esta nota describe el bloque siguiente; no habilita órdenes de voz, scheduling nuevo ni cambios de permisos/arranque. El usuario valora positivamente el ensayo de recuperación y aclara que su pregunta por Internet era curiosidad, **no una petición de añadir búsqueda web**.

## Lo existente y lo pendiente

- `TimerStore.java` ya conserva timers en preferencias privadas Android, IDs UUID idempotentes, hasta cinco timers y duración 1 s–24 h. Usa `AlarmManager.setExactAndAllowWhileIdle` con `ELAPSED_REALTIME_WAKEUP`; guarda vencimiento monotónico y de pared y tiene recuperación desde `BootReceiver`. UI permite ver/cancelar manualmente y detener sonido. No trasladar pruebas históricas aisladas a aprobación de herramientas conversacionales nuevas o de todos los casos de reinicio.
- Alarmas por fecha/hora, recurrencias y recordatorios con texto **todavía no están integrados**. No presentarlos como capacidades del LFM ni asumir que basta un «sí, programado».

## Datos y reloj según función

| Función | Registro duradero propuesto | Programación |
|---|---|---|
| Temporizador | ID, duración, inicio y vencimientos monotónico/pared, estado | Tiempo transcurrido; cambiar la hora del teléfono no mueve la cuenta activa |
| Alarma | ID, fecha/hora solicitadas, zona horaria, próxima ejecución y recurrencia si existe | Fecha de calendario explícita; calcular la siguiente ocurrencia y validar ambigüedad |
| Recordatorio | ID, texto privado, fecha/hora o intervalo, estado/notificación | Planificador Android como alarma; mostrar/anunciar texto sin inferencia activa |

Bogotá es la zona de referencia actual, no una ubicación obtenida por permiso. Si falta fecha/hora o hay ambigüedad, preguntar. Si cambia zona/hora, aplicar la semántica acordada, no alterar silenciosamente lo pedido. Tras reinicio, reconstruir desde datos persistidos con política explícita para vencidos y duplicados. **No prometer que suene con el teléfono apagado** ni certificar puntualidad absoluta/OEM sin pruebas reales. La restauración no debe arrancar modelo ni micrófono.

Silenciar/cerrar una charla no equivale a **forzar la detención del paquete Android**: esto último puede cancelar o suspender entregas del sistema. El registro persistente permite recuperar/revisar al reabrir, pero esa recuperación completa aún debe implementarse/validarse donde corresponda; no garantizar que sonarán tras force-stop por el mero hecho de guardar un JSON.

## Contrato de ejecución futura

1. La IA elige una herramienta tipada de un catálogo restringido, no una regla por frase. Nunca recibe shell/root ni un ejecutor de código libre.
2. Validar tipo, rango, fecha/zona, literal de duración y permisos; mantener barreras frente a negaciones, citas y duraciones inventadas. Si falta información, pedirla. El usuario acepta errores casuales de conversación, **no acciones incorrectas**.
3. En prototipo, propuesta legible y confirmación humana antes de persistir. Correlacionar autorización con propuesta/turno/ID, comprobar plazo justo antes de mutar y rechazar reintentos tardíos o de otra sesión.
   La confirmación de una acción es un estado distinto de «¿Necesitas algo más?» y «¿Quieres retomar esa charla?»: un «sí» dado a cualquiera de esas preguntas no autoriza un timer. No reutilizar sin corregir el piloto histórico con acciones: sus limitaciones de autenticación/plazo no quedan reparadas por la conversación actual.
4. Generar ID de acción independiente del ID de charla, guardar y programar de forma recuperable/idempotente. Confirmar solo tras resultado real del ejecutor; si falla, comunicarlo y no afirmar éxito ni inventar un registro.
5. Consultar/listar/cancelar desde el registro real, no desde lo que el modelo recuerda haber hecho. Cancelar un objetivo concreto; si hay varios y no está claro cuál, preguntar. No cancelar todos implícitamente.
6. Al vencimiento, notificación/sonido o TTS offline según capacidad validada, sin llamar al LLM. Evitar que su propia voz/alarma entre a ASR. El modo conversacional actual detiene su sesión ante un sonido de alarma y conserva contexto RAM.

Puede persistirse también el resultado confirmado para ayudar a conversar, pero el historial no es la fuente de verdad del planificador. Tampoco debe recrearse automáticamente una acción por restaurar un contexto antiguo.

## Orden de entrega

Tras la recuperación mecánica entre ventanas 0.7 y valoración positiva del ensayo por el usuario, el siguiente bloque propuesto es **una herramienta de creación de temporizador** con validación, confirmación, ID y resultado Android real, dentro del plazo global, sin abrir aún todas las acciones. Reutilizar el almacén/planificador Android existente, no el historial como registro ni un modelo contando segundos. Primero crear uno y comprobar que vence con conversación/modelo apagados; después consultar/cancelar por voz, alarmas por fecha y recordatorios; luego recurrencias y casos de reinicio/reloj/OEM. Cada capacidad requiere pruebas sintéticas y acotadas antes del uso humano correspondiente. La prueba de contexto no certifica memoria general ni seguridad de herramientas. Sin escuchar permanentemente, nube de inferencia, búsqueda web implícita ni requisitos nuevos de hardware.
