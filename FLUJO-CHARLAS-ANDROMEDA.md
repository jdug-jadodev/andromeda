# Charlas continuas · petición aclarada por el usuario

## Estado: 0.6 instalada; validación sintética superada

**0.6.0-dialogue-trial / código 7 / protocolo 4 instalada**, tras consentimiento explícito del usuario y advertencia de que actualizar elimina las charlas de prueba en RAM. Backend y cliente desplegados juntos, sin borrar datos persistentes, cambiar permisos/HOME o exportar conversaciones. APK 0.5 respaldado en `artifacts/android-voice/pre-v06-*.apk` (SHA-256 `cab2518b39a03e811de45d1603e15b050b4e4000d79f118dd680d21182b47219`); backend anterior en el Moto bajo `/data/local/doha-voice/backups/pre-dialogue-v06-20261018`. No se inicia una sesión humana automáticamente.

## Experiencia solicitada

**Un turno es una intervención; una charla contiene muchos turnos.** No hace falta una forma especial de decir una pregunta de recuerdo para arreglar un fallo del sistema/modelo. El usuario solicita:

1. Decir «Andrómeda» abre la cara y una charla; no repetir el nombre para cada pregunta posterior.
2. Tras una respuesta puede seguir hablando con el mismo hilo. Micrófono pausado mientras piensa/habla, para evitar reconocerse a sí misma. Sin interrupción de TTS por voz en este bloque.
3. «Cierra la conversación» o «gracias» a solas cierran esa charla. «Gracias, pero tengo otra pregunta» no debe cerrarla por coincidencia de una palabra.
4. Si ella pregunta si necesita algo más, «sí» y lo que siga continúan; «no» cierra **solo en ese estado**, no como negación general. No cerrar ante «no enciendas las luces», ejemplos o citas.
5. Cerrar **no es borrar**: queda retomable durante una hora desde la última actividad humana de esa charla; otros chats, TTS y abrir el selector no renuevan esa hora. La siguiente charla normal tiene otra identidad. Silenciar/salir/fin de prueba son pausas y conservan la selección actual.
6. «Andrómeda, ¿te acuerdas de la conversación de las luces?» debe consultar las charlas aún disponibles y ofrecer retomarla con confirmación. Con varias coincidencias, preguntar cuál; si no existe, decirlo y recordar la caducidad por una hora de inactividad. No inventar que existe, que se borró por una causa específica o que recuerda contenido ausente.

## Primera parte implementada en código

- Backend `session`: después de `tts_started` + `tts_done` válidos crea la siguiente recepción ASR directamente, con el mismo ID de charla, sin volver al detector wake en cada respuesta. Mantiene plazo global de turno, rechazo de acuses tardíos/duplicados, guardas y cancelación del servidor dedicado. Descarta audio encolado durante la pausa de 600 ms.
- Controles explícitos de cierre separados de la generación conversacional. No reemplaza las respuestas del LFM por un clasificador/reglas, no habilita herramientas de casa/timers. La lista pequeña de controles se aplica a la **intervención completa**; citas, relatos y negaciones no disparan un substring. «No» solo cierra tras una oferta final directa reconocida, realmente completada por TTS en el turno anterior. Otras formulaciones siguen como conversación, no como cierre inferido. No se fuerza al LFM a ofrecer ayuda en cada respuesta ni se cambia su prompt.
- El cierre lleva IDs de sesión/charla/turno, secuencia y plazos. Android verifica coincidencia, ausencia de voz ya programada y lease activo antes de marcar cerrada. El cierre retiene el control humano sin fabricar una respuesta hablada; termina captura/modelo y vuelve al reloj. Un callback antiguo no puede cerrar otra charla ni revivir la anterior.
- RAM separa charla cerrada/retomable de charla seleccionada; selector muestra el estado. Reabrir una cerrada requiere seleccionarla explícitamente (por ahora, menú); su historial no se mezcla con el chat nuevo. Si las cuatro plazas están llenas, pide seleccionar u olvidar explícitamente una, sin expulsar otra ni provocar un crash al adquirir.
- **Más turnos cortos con el mismo techo de texto:** hasta 24 turnos por charla, pero máximo **5700 unidades UTF-16** entre preguntas y respuestas, igual al peor caso de seis turnos de 0.5. Una charla de respuestas largas puede seguir reteniendo solo seis. El recorte elimina turnos antiguos completos. Esto conserva más intercambios breves, **no una hora completa de historial sin límite**. Hay más objetos/IDs pequeños posibles y copias temporales; no es una medición de ahorro o igualdad del PSS total.
- La importación aplica el mismo límite de texto/objetos/unidades en Python y Java; hello sigue <=32 KiB. No persistencia en disco, embeddings ni otro modelo residente. La ventana del LFM sigue **2048 tokens**, entrada <=1536 y salida <=96; si el historial no cabe, el prompt usa lo reciente. Conservar más mensajes no demuestra que LFM los use bien.

## Límites conservados

**No escucha permanente.** La prueba se inicia manualmente en Android y continúa acotada a 90 s/5 min; el backend se prepara manualmente. Cerrar explícitamente detiene la prueba actual, no arranca otra automáticamente. Tras **50 s sin hablar** vuelve a esperar el nombre, sin borrar/cerrar lógicamente la charla; se puede reactivar dentro del tiempo restante. Silenciar, salir, alarma, recurso insuficiente o vencimiento siguen deteniendo el servicio. El reloj/estado debe distinguir captura activa de reanudación/pausa.

Una hora es por **inactividad humana de cada charla**, no borrado cada hora fija desde su creación. Muerte del proceso/reinicio puede perder la RAM antes. Timers/recordatorios no se almacenan aquí y no se alteran al cerrar/olvidar chats.

## Pendiente: buscar y retomar por tema

**El punto 6 no está integrado en voz.** No pedir al usuario otra repetición de «¿recuerdas?» como si ya fuera un buscador. Hoy LFM solo recibe la charla seleccionada y puede dar un sí/no incorrecto; su texto no es la fuente de verdad del catálogo.

Siguiente bloque: catálogo local acotado de chats no caducados con identificadores y evidencia del tema; consulta local sin cargar las cuatro conversaciones completas al prompt ni otro modelo. Resolver ambigüedad, confirmar la elección y transferir el snapshot validado de **esa** charla en una frontera sin turno/voz en curso. La selección debe validar ID existente/caducidad y correlación de la confirmación, sin aceptar un ID inventado por LFM. Cancelar/rechazar tarde no cambia charla ni renueva su hora. No reejecutar acciones históricas. El texto «sí, la tengo» debe basarse en una coincidencia real; si solo queda un fragmento, no afirmar que conserva todo. No se implementa una regla especial para «luces»: es un ejemplo de tema, no una intención doméstica habilitada.

## Verificación local de esta parte

- **183 tests Python** pasan, incluidos cuatro turnos con una sola activación, continuidad sin wake, cierre sin inferencia, «no» contextual y oferta no hablada, «sí» con continuación, cola de audio, pausa por inactividad, presupuestos de texto y restauración de 24 turnos.
- **249 aserciones JVM**: 91 política/UI, 11 cierre TLS y 147 memoria; cierre/lease, chat nuevo frente a retomado, límites, caducidad, callbacks viejos y control por presupuesto de texto. No usan teléfono, captura, modelo ni red.
- APK compila/firma/instala correctamente; advertencias Java 8/deprecaciones existentes. **No probado con voz humana todavía**. Las fixtures actuales usan protocolo 4 y necesitan este backend/cliente coincidentes.

Consentimiento, respaldo y despliegue coordinado completados. UI en Moto pasa: ocho estados, 34 dibujos/1200 ms y temporizadores intactos. Transferencia TLS real de contexto (0 → 1 → 2 → 0), separación, olvido con confirmación y pregunta pendiente pasan con dobles explícitos de voz, sin modelo/captura/audio. Silenciar y cierre TLS/StrictMode vuelven a pasar, sin arrancar el servicio de captura. Reposo previo al despliegue: MemAvailable 1592856 KiB, batería 23.2 °C, sin voz/inferencia activas y HOME Motorola.

Se añaden fixtures públicas **sin wake** para turnos siguientes y cierres; no modificar fixtures históricas para fingir continuidad. El ensayo real ASR/LFM/síntesis sin altavoz debe contar una sola activación de voz y las reaperturas ASR tras TTS. Cierres «cierra la conversación»/«gracias» deben terminar el servidor dedicado, conservar el par anterior y el control de cierre sin respuesta inventada, permitir seleccionar el chat cerrado y no transferir su historial al chat nuevo. El reloj comprobado por este cliente de prueba es una presentación de instrumentación, no un arranque del VoiceService de captura.

### Primeros resultados sintéticos en el Moto

- **Continuidad pasa:** una activación por voz, tres turnos con ASR/LFM y tres reaperturas ASR tras síntesis offline, los últimos dos audios **sin decir Andrómeda**. Primer PCM desde fin de señal pública: **6.019 / 6.374 / 4.076 s**; desde fin estimado ASR: **6.521 / 6.888 / 4.591 s**. Sin micrófono, altavoz ni VoiceService real. En esta fixture LFM contestó correctamente «Tu gata se llama Menta.»; **un acierto no valida recuerdo general ni corrige los fallos anteriores**. 83/125/168 tokens de entrada sin recortes; cancelación de inferencia por desconexión en 34 ms. MemAvailable mínimo 1282884 KiB, pico servidor 737776 KiB, backend/Vosk 148876 KiB, PSS app de instrumentación 34672 KiB y batería máxima 23.9 °C. Arranque separado 7.963 s.
- **Cierre explícito pasa:** una activación, respuesta sintetizada y luego ASR «cierra la conversación» sin wake. Cierra sin otra generación, conserva el par y el control humano, crea chat nuevo vacío y permite seleccionar/retomar el cerrado sin respuesta ficticia. Inferencia abajo en 4 ms desde recepción/cierre del cliente. Presentación de reloj pasa (no servicio real). MemAvailable mínimo 1283356 KiB, pico servidor 736268 KiB, backend/Vosk 149492 KiB y batería máxima 24.0 °C; arranque 7.231 s.
- **«Gracias» pasa** con el mismo flujo y una sola activación: ASR reconoce el cierre a solas, no lo manda al LFM; retención, chat nuevo aislado, selección del cerrado y presentación del reloj pasan. Puerto de inferencia abajo en 5 ms desde el cierre del cliente. Arranque 7.188 s; MemAvailable mínimo 1284796 KiB, pico servidor 736424 KiB, backend/Vosk 150776 KiB y batería máxima 24.1 °C.
- **Cancelación durante cálculo y acuse TTS tardío pasan** con protocolo 4: puerto de inferencia abajo a los 41 y 37 ms respectivamente, sin sintetizar/reproducir respuesta. Arranques separados 7.215/7.213 s. Cero acciones y sin micrófono en los cinco ensayos. El «no» contextual tiene cobertura unitaria con dobles; **todavía no un ensayo humano de reconocimiento y oferta realmente audible**.
- Wake-only anterior: nueve fixtures pasan de nuevo, sin LFM/TTS/captura/acciones, incluido retorno tras silencio >8 s. Fixtures públicas retiradas al terminar, sin exportar o guardar audio humano.

Los PSS corresponden al cliente de instrumentación, no a una sesión con AudioRecord/voz audible; no compararlos como ahorro de RAM frente a una charla humana. Ningún tiempo de síntesis certifica audio audible útil ni uso sostenido/24-7.

### Verificación final instalada

APK local/instalado SHA-256 `a378fbf0907736f2f5b7c0d3cab7f104ccf0943bdc8680e3416b023c2891cfb3`; backend `conversation_voice.py` `7742a3e2a6f7f136638119daf7364e02facf9fde88c3b890db2c6f157c54d7df`; módulo `conversation_flow.py` `c18570552c9436bbd27b8a13d5e9ee225bab9cae26e4711b477020d8cd2a46a4`. Hash del archivo privado de timers **idéntico antes/después**, sin copiar ni imprimir sus registros. 183 tests Python/249 aserciones JVM siguen pasando y `git diff --check` sin errores.

App Andrómeda abierta, **sin VoiceService/RingService ni listeners 11435/11436 o workdirs de inferencia**. HOME Motorola intacto, SSH :2222 activo y web :8080 HTTP 200. Muestra de reposo final: MemAvailable **1508564 KiB (~1.44 GiB)**, batería **24.3 °C**. Mínimo de las muestras de estos sintéticos **1282884 KiB (~1.22 GiB)**; no es una reserva sostenida con captura/funciones futuras. No se deja preparada otra sesión humana ni modelo/escucha permanente. Próximo ensayo humano necesita una ventana `pilot` nueva y selección manual en la app; búsqueda por tema sigue siendo trabajo pendiente.

Después de completar los sintéticos se podrá realizar una prueba humana acotada; no prometer búsqueda por tema hasta integrar y validar ese bloque. El objetivo rutinario 5–10 s y máximo de respuesta audible útil 30 s siguen pendientes de aceptación; este cambio de flujo no elimina por sí solo las demoras 21–23 s observadas en 0.5.
