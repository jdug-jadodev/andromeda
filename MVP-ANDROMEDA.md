# Andrómeda · primera versión, por entregas

## Objetivo vigente

Asistente local en el Moto, inspirado en las funciones cotidianas de Alexa, sin integrar todavía TV, luces o electrodomésticos. No se promete paridad con Alexa en esta primera versión. Diseño y requisitos: `DISENO-ANDROMEDA.md`.

### Decisión vigente del usuario: continuar con LFM

El usuario acepta **LFM2.5 1.2B Instruct QAD Q4_0** como base provisional para conversación casual corta, con los errores de identidad/perspectiva ya conocidos, y prefiere observar su funcionamiento y mejorarlo con el tiempo. **Cerrar por ahora la comparación de modelos y no mantener esos errores conversacionales como bloqueo absoluto de una prueba personal acotada.** La elección no cambia los resultados históricos ni demuestra que el modelo haya pasado las pruebas semánticas.

Decisión registrada en `config/model_selection.json` (registro de intención, no perfil de ejecución). Registrar la elección no activó capacidades; después el usuario pidió iniciar la integración. Siguen vigentes el máximo de 30 s, las confirmaciones basadas en resultados reales y las barreras para acciones.

## Entrega vigente: 0.6.1 · oferta de ayuda y despedida instalada

**Feedback humano posterior:** el usuario confirma oferta de más ayuda, despedida tras «no» y vuelta al reloj. Primera sesión: dos turnos LFM y despedida, tres pares por acuse TTS; recuerdo contextual todavía no validado. Pide una última prueba breve antes de dormir: preparar `pilot 90`, **seleccionar la charla cerrada en Contexto antes de conectar**, probar una referencia concreta y parar. No reinstalar/instrumentar ni reiniciar ventanas automáticamente; advertir que la charla caduca a la hora de inactividad, aunque el desarrollo continúe mañana. Detalle en `FLUJO-CHARLAS-ANDROMEDA.md`.

A petición del usuario **antes** de preparar otra prueba humana: 0.6.1/código 8/protocolo 5 instalada con respaldo de 0.6, misma RAM/modelo/límites. El gestor añade «¿Necesitas algo más?» si no hay oferta final equivalente, sin otra inferencia. Sí/otra pregunta siguen; no contextual o cierre explícito sintetizan una despedida honesta y solo después confirman cierre, manteniendo captura pausada. Si falla TTS, no guarda una despedida inventada. No promete activación por voz tras apagar el micrófono. 190 tests Python/266 aserciones JVM pasan; sintéticos reales de sí + continuación + no + despedida y continuación directa pasan, sin micrófono/altavoz ni VoiceService real. TLS/contexto/Silenciar e integridad de timers verificados. Se prepara la ventana de cinco minutos solicitada, sin captura automática; falta feedback humano. Búsqueda por tema pendiente. Detalle/ensayos: `FLUJO-CHARLAS-ANDROMEDA.md`.

## Entrega anterior: flujo de chat 0.6 instalada

Usuario aclara que una charla debe contener múltiples turnos y poder retomarse por tema. **0.6/protocolo 4 instalada** tras autorización explícita de actualizar y perder la RAM de pruebas, con respaldo de APK/backend 0.5 y sin borrar datos de timers. Continuidad tras TTS sin wake repetido, cierre explícito/contextual y estado cerrado retomable; hasta 24 turnos con el mismo techo de texto de 5700 unidades UTF-16 por charla y ventana LFM sin ampliar. 183 tests Python/249 aserciones JVM pasan. En Moto pasan UI, transferencia TLS/aislamiento/olvido y cierre TLS/Silenciar sin audio/modelo. También pasan sintéticos reales Vosk/LFM/síntesis con una sola activación para tres turnos y cierres posteriores sin wake por «cierra la conversación»/«gracias», con retención y separación del chat nuevo. No captura ni reproducción y falta ensayo humano/VoiceService real de este flujo. Búsqueda/confirmación por tema sigue **sin integrar**; no atribuirla al LFM ni probarla como entregada. Contrato y evidencia: `FLUJO-CHARLAS-ANDROMEDA.md`.

## Entrega anterior 0.5 — contexto independiente de captura/modelo

**0.5.0-context-trial / código 6 instalada**, con respaldo de 0.4.1 y sin borrar datos/HOME. Hasta cuatro charlas en RAM de Android, seis turnos cada una, caducidad independiente de una hora de inactividad humana; Silenciar/salir detiene voz/modelo sin borrar automáticamente el hilo. No sobrevive a cierre del proceso/reinicio. Menú **Contexto** permite elegir, crear u olvidar una charla con confirmación; temporizadores no se tocan.

Protocolo 3 TLS/pin/token transfiere snapshot acotado/validado y verifica IDs de charla/sesión; LFM sigue recibiendo roles nativos, no el formato experimental rechazado. Preguntas interrumpidas se conservan sin respuesta inventada, callbacks antiguos no pueden escribir tras reabrir/olvidar. Límite 2048/1536/96 y plazo de turno intactos; sin herramientas, daemon ni micrófono automático. Conexión rechazada explica motor local no preparado.

**171 tests Python/157 aserciones JVM**, UI, retención TLS real con dobles de voz, olvido confirmado, compatibilidad nativa LFM y flujo sintético ASR/LLM/TTS pasan sin captura/reproducción humana ni cambios de timers. Sigue el error semántico Menta/Luna: no afirmar que conservar datos arregla su uso por LFM. Pendiente prueba humana de silenciar/reabrir y latencia audible útil <=30 s. Evidencia/límites: `CONTEXTO-ANDROMEDA.md`. Timers ya tienen almacenamiento Android separado; alarmas/recordatorios y herramientas conversacionales siguen pendientes según `ACCIONES-ANDROMEDA.md`.

## Entrega anterior 0.4 — conversación LFM sin acciones

**0.4.1-conversation-trial / código 5 instalada** (corrección posterior a 0.4.0), con respaldos previos y sin borrar datos/cambiar HOME. Menú Micrófono separa conversación LFM y prueba original solo activación. Sesiones manuales 90 s/5 min, Vosk + memoria corta tokenizada + LFM + voz española offline, sin catálogo/ejecutor. Canal TLS fijado + token de cliente independiente, turno con plazo compartido, cancelación del servidor dedicado y rechazo de TTS tardío/duplicado. No abre micrófono al preparar ni al abrir la app.

**Ensayos iniciales 0.4.0: 145 tests Python y 78 aserciones JVM pasan**; UI y dos series de tres turnos sintéticos completos ejecutados en Moto, sin captura/reproducción ni cambios de timers. Fin de WAV → primer PCM sintetizado **5.343–6.409 s**; no es inicio audible útil ni certifica 30 s. LFM aún confunde Menta/Luna; error aceptado para ensayo personal, no declarado corregido. Cancelar durante cálculo cerró inferencia en 31 ms y una confirmación TTS tardía se rechazó; pulsación humana de Silenciar/driver de captura y voz real pendientes entonces. Recursos, seguridad, reproducción y preparación: **`CONVERSACION-ANDROMEDA.md`**. Sin acciones, escucha permanente, daemon ni cambios de carga/swap.

**Ensayo humano posterior:** el usuario confirma que respondió con voz y Silenciar la detuvo, pero salió al buscador/inicio Motorola. Crash identificado: `NetworkOnMainThreadException` al cerrar TLS en `VoiceService.onDestroy`. Corregido en **0.4.1 instalada**, cierre en hilo independiente sin desactivar StrictMode. Pasan ahora **145 tests Python/89 aserciones JVM**, UI y regresión TLS real + botón con UI simulada en Moto, sin micrófono/TTS/modelo ni arranque del servicio de captura. Reloj permanece visible en la prueba; pendiente repetir la pulsación durante conversación humana, no se certifican latencia ni comprensión por la confirmación de voz. App abierta y micro/modelo apagados al terminar; no nueva sesión preparada automáticamente.

**Reintento humano en 0.4.1:** tras explicar que `ECONNREFUSED` era motor local apagado (no Internet) y preparar otra ventana manual, se registran cuatro inicios TTS de fuente micrófono, **5.142–7.641 s** desde fin estimado ASR, sin verificación acústica/semántica de utilidad ni certificación de 30 s. App en primer plano al terminar; voz/modelo cerrados, cero acciones, batería máxima 24.2 °C y MemAvailable mínimo 1358784 KiB. Pendiente confirmar con el usuario si pulsó Silenciar y conservó el reloj o la sesión venció sola; no preparar otra automáticamente.

**Devolución posterior:** no pulsó Silenciar (sigue pendiente esa comprobación); las respuestas fueron «más o menos bien», pero afirmó recordar las dos preguntas previas y luego no pudo decir cuáles eran. Fallo de recuerdo sin causa retrospectiva demostrada: no se conservaron sus textos. Tres nuevas regresiones de integración con cuatro turnos públicos y dobles pasan en PC/Moto (148 tests Python total); no son una reparación de LFM. Backend ahora registra solo cantidades de turnos/tokens y commits/omisiones TTS, sin contenido humano, para comprobar qué historial se envía en el próximo ensayo. Misma app/modelo/prompt/límites, sin sesión preparada automáticamente ni nuevas comparativas. Detalle en `CONVERSACION-ANDROMEDA.md`.

**Repetición posterior con contadores:** tres turnos con 0/1/2 pares previos enviados, cero recortes, 83/125/165 tokens. El usuario confirma que no hizo la cuarta porque en la tercera ya negó recordar ambas preguntas: los dos pares sí estaban disponibles en ese punto, no se borraron por presupuesto/commit. Apunta a uso incorrecto del contexto por LFM, sin poder auditar la transcripción exacta ni su causa interna. No declarar arreglado el recuerdo ni pedir otra repetición humana idéntica; mantener elección de LFM y evaluar futuras mejoras con ejemplos públicos. Datos completos en `CONVERSACION-ANDROMEDA.md`; Silenciar humano corregido aún sin confirmar.

**Comparación acotada posterior, mismo LFM:** una aclaración general sobre pertenencia del historial a la sesión, historias públicas idénticas en ocho pares. No mejora fiable y mediana de texto/API 5.476→13.078 s; variante descartada sin modificar la app/prompt de voz. Los «aciertos» extra del matcher no equivalen a recuerdo correcto. Detalle `EVALUACION-MEMORIA-LFM.md`; 157 tests Python/89 aserciones JVM. No abrir otra cadena de modelos ni exigir perfección casual como bloqueo absoluto; siguiente mejora útil del MVP: inicio/preparación comprensibles y parada, sin habilitar acciones. Equipo queda sin modelo/micrófono activos.

**El usuario mantiene interés prioritario en el contexto**, junto al inicio comprensible. Segundo ensayo acotado: el mismo historial público como JSON explícito, diez pares, mismo LFM/2048 tokens. Algunas recuperaciones mejoran, pero inventa datos sin historial, afirma capacidades inexistentes y falla propiedad; no activar. Medianas de texto/API 8.029 s nativo / 12.951 s JSON, no latencia de voz. App y prompt actual intactos, 165 tests Python; `EVALUACION-CONTEXTO-ESTRUCTURADO.md`. Seguir el hilo, conservar turnos y retener conversaciones por inactividad siguen pendientes: no abandonarlos ni confundir almacenamiento correcto con comprensión correcta. No repetir pruebas humanas idénticas, ampliar modelos/contexto o habilitar acciones por estos resultados.

## Avance posterior 0.3 — micrófono/activación aislados

Por petición explícita del usuario, avanzar en micrófono → nombre → cara mientras se evalúa la comprensión del modelo. **0.3 instalada**, botón Micrófono ofrece prueba limitada de 90 s/5 min contra backend wake-only: muestra cara de escucha 8 s y transcripción, sin LLM, TTS ni acciones. Botón visible de silenciar/cuenta atrás, parada al salir. Pasan 88 pruebas Python, 48 aserciones JVM, instrumentación UI y nueve casos sintéticos con Vosk real; pendiente confirmación humana de este flujo en la UI nueva. Preparación del detector no inicia captura; ver `MICROFONO-ANDROMEDA.md`. No se habilitó escucha permanente ni se aprobó el modelo por habilitar este ensayo.

**Confirmación posterior del usuario:** dijo «sí» al verificar voz → nombre → paso del reloj a la cara. Despertar visual humano confirmado en 0.3; transcripción, regreso, silenciado y conversación con IA no se deducen de esa respuesta. Alcance exacto en `MICROFONO-ANDROMEDA.md`.

**Siguiente bloque ejecutado, aún sin respuestas de voz:** comparación de conversación sin herramientas con Qwen 1.7B Q4, Q8 del mismo modelo y LFM existente. Persisten errores de identidad/capacidades incluso sin catálogo; Q8 no basta y LFM solo es más rápido. No se cambió la app 0.3 ni se conectó el LLM al micrófono por estos ensayos. Resultados, cuantización verificada, recursos y candidato posterior fundamentado (todavía no descargado) en `EVALUACION-PRECISION.md`. 96 pruebas Python pasan; modelo sin aprobación.

**Prioridad posterior explícita: eficiencia y contexto, no modelos mayores.** Se comparó el Qwen Q4 existente con dos reglas globales breves de separación usuario/asistente, regresión y ocho controles nuevos base/variante. La identidad sigue fallando y la variante no mejora eficiencia. Dos prompts idénticos con/sin caché dan iguales errores; recomputar solo añade tiempo. No se descargó otro modelo ni se activó conversación por voz. Ver `EVALUACION-IDENTIDAD.md`; 100 pruebas de soporte pasan. Mantener la app 0.3 y su activación ya confirmada.

**Candidato alternativo autorizado después:** Qwen3.5 2B Q4 descargado/verificado e instalado solo para pruebas. Motor carga; misma suite de 15 turnos y ocho controles con muestreo oficial y un control de penalización de presencia. RAM del servidor ~1.39 GiB PSS, pero persisten identidad/perspectiva/consejos incorrectos; no se conecta a voz. No hubo nueva instalación de app ni micrófono activo. Evidencias y correcciones de evaluación/transporte en `EVALUACION-QWEN35.md`; 106 pruebas de soporte pasan. No declarar éxito por cero fallos mecánicos del control.

**Investigación posterior de motor/idioma:** 80 generaciones completas y 66 prompts/tokens únicos auditados contra referencias oficiales; chat/raw coinciden y los errores persisten con controles de CPU, representación y también en inglés. Backend genérico compilado aparte y ejecutado en Moto: 118–129 s, todavía con errores; no reemplaza el original. Ninguna configuración aprobada, app 0.3 intacta, todo apagado y 122 tests de soporte. Detalle en `EVALUACION-MOTOR-IDIOMA.md`.

## Correcciones 0.2 — instaladas y probadas en el Moto

Tras reconectar ADB se respaldó el APK 0.1 y se instaló 0.2 sin borrar datos. Android confirma `versionName=0.2.0-visual`, `versionCode=2`. HOME sigue Motorola. No trasladar las métricas históricas de 0.1 a este cambio.

- Reposo inmersivo: reloj grande, fecha, clima de Bogotá y «Di Andrómeda». Sin cara ni controles permanentes. La invitación se acompaña del estado real: activación apagada o sesión de prueba limitada con cuenta atrás. Deslizar desde el borde permite recuperar las barras de Android; no es modo kiosco ni nuevo HOME.
- Doble toque abre las opciones. Se cierran después de **50 s sin interacción**, también los diálogos derivados. Seleccionar opciones/editar el color renueva el plazo; cambios del reloj, clima o estados de IA no lo renuevan. Back cierra opciones o vista previa; se conserva salida de mantenimiento. Acción de accesibilidad y tecla Menú también permiten abrir opciones.
- LISTENING hace aparecer la cara con entrada lateral, pequeños saltos e inclinación (~680 ms) mientras se desvanece el reloj. READY vuelve al reloj; no se interpreta como conversación activa. La activación acústica real depende del detector/servicio existente, no se habilitó escucha permanente por cambiar el dibujo.
- THINKING conserva la cara y añade cejas asimétricas/mirada lateral, además de un loader de todo el perímetro: cabeza brillante y cola que se desvanece, vuelta ~3.8 s. Desaparece al salir de procesamiento. No representa porcentaje de avance ni carga de batería.
- Color guardado en preferencias exclusivas de UI: paleta o `#RRGGBB`. Reducir animaciones en Android evita carrera y recorrido; loader estático en ese modo. Cara oculta y loader inactivo no siguen programando frames.
- Clima: HTTPS directo a `api.open-meteo.com`, coordenadas públicas fijas de Bogotá, sin permiso de ubicación ni envío de voz/historial. Refresco aproximado cada 15 min mientras la pantalla está activa, caché solo de datos meteorológicos públicos. Solicitud fuera del hilo gráfico, sin redirecciones, respuesta <=32 KiB, timeouts de conexión/lectura y comprobación de tiempo transcurrido.
- Se valida zona horaria/unidades/fecha y se indica **estimación, fuente y hora**. Tras 90 min sin dato válido se oculta la temperatura y se anuncia falta de datos recientes. Si falla una actualización, no se reemplaza un dato todavía reciente por un valor inventado. Al pausar no se hacen nuevas consultas: una petición pública ya en vuelo puede terminar, pero su resultado tardío se descarta por generación; no se bloquea el hilo gráfico al cerrar una conexión.

### Comprobado localmente

- Build Java/D8, empaquetado y firma correctos (`versionName=0.2.0-visual`, `versionCode=2`).
- **35 aserciones JVM**: plazo exacto de 50 s con reloj simulado, renovación/cierre, modo reloj/cara, validación de colores, caducidad y condiciones meteorológicas. No necesitan dispositivo, audio, Internet ni esperas reales de 50 s.
- **82 pruebas Python** existentes pasan. No prueban voz ni corrección del modelo.
- Se consultó el endpoint público desde el entorno de desarrollo: HTTP 200, zona `America/Bogota`, tiempo `unixtime`, temperatura `°C` y código WMO. Posteriormente el Moto obtuvo clima real por HTTPS: captura de reposo con estimación de 19 °C/llovizna a las 15:00, mostrando fuente y hora (muestra del ensayo, no dato permanente).
- Instrumentación Android ampliada **ejecutada correctamente en el Moto**: gesto simple/doble inyectado al dispatcher, reloj inicial, cierre en el límite de tiempo simulado, cara durante procesamiento, color del loader, vuelta al reloj, suspensión de dibujo y parser de clima con fixtures. Usa `offline_preview=true` para no consultar Internet durante la prueba. Ocho estados distintos, 35 dibujos en 1200 ms; sin micrófono/modelo y con timers intactos. No confundir el límite simulado con una espera física completa de 50 s.

Muestra con app visible después de obtener clima: PSS total **70603 KiB (~68.9 MiB)**, sin LLM/ASR/TTS. Captura de reposo revisada en `artifacts/android-voice/andromeda-v02-idle.png`; reloj, clima e invitación visibles a pantalla completa, cara y opciones ocultas. APK instalado SHA-256 `61d775199d53b5b27e0c878119197f4161004af5dbcaf3cc83625cfaf1e2c39d`.

Pendiente: inspección completa de transiciones/perímetro en ambos sentidos horizontales, cierre por inactividad con espera real y fallo de red real; prueba sostenida conjunta. ADB volvió a desconectarse después de instalar y verificar el reposo/clima; **la actualización sí quedó instalada**, no queda solo en el PC. No pedir voz al usuario ni arrancar el modelo para verificar estos cambios visuales. Última comprobación conectada: sin servicios de voz/alarma.

## Histórico comprobado en el Moto: interfaz visual 0.1

- APK `local.doja.voice` actualizado **sin borrar datos**, con copia del APK anterior en `artifacts/android-voice/previous-ui-*.apk`. No se cambió HOME: sigue Motorola.
- Pantalla horizontal en ambos sentidos, fondo oscuro, reloj/fecha de Bogotá y cara nativa dibujada con Canvas. No hay WebView, motor 3D, modelo adicional ni librerías gráficas nuevas.
- Ojos con parpadeo/mirada, movimiento suave, boca animada y transición de forma/color. Estados explícitos: reposo, preparación, sesión preparada, escucha, procesamiento, habla, silenciado y error.
- Animación independiente de red/inferencia: hasta 30 fps activa y 15 fps ambiental. Se detiene al pasar a segundo plano y respeta la reducción de animaciones de Android.
- Vista previa **rotulada como simulación**, sin micrófono, TTS, modelo ni ejecución; termina sola aproximadamente a los 30 s y se puede cerrar antes. Un evento del servicio restaura la presentación real y termina la vista previa.
- Silenciar visible; temporizadores existentes y sonido se gestionan desde Herramientas, con confirmación antes de cancelar todos. Las pruebas de voz históricas están separadas y advertidas; no se inicia modelo ni se graba por abrir la app.
- `VoiceService` publica estados tipados y una instantánea solo en RAM para restaurar al volver. Estado de habla activado por callback TTS `onStart`, no solo por encolar texto. **No se ha revalidado voz real con estos cambios.**

### Evidencia en el Moto

- Build Java/D8, firma e instalación incremental correctos. Un primer ensayo detectó un literal de fecha sin escapar que impedía abrir la actividad; se corrigió antes de aprobar y repetir las pruebas.
- Instrumentación de UI: ocho estados dibujados distintos; horizontal; controles dejan área útil a la cara; vista previa identificada; restauración de estado; parada de animación en segundo plano/reducción de movimiento; temporizadores sin cambios; no inicia micrófono/modelo.
- Muestra inicial: **36 dibujos en 1200 ms** (~30 fps). Es conteo de dibujo de una prueba breve, no certificación de fluidez bajo inferencia/ASR simultáneos.
- `dumpsys gfxinfo`: muestra de 350 frames, p95 8 ms y 3 frames con jank (0.86 %, incluye arranque). No representa carga sostenida de todo el asistente.
- `dumpsys meminfo`: PSS total **64972 KiB (~63.4 MiB)** con app visible, incluyendo ~29 MiB gráficos. No sumar esta muestra a otra medición de memoria como si fueran simultáneas.
- Prueba aislada del ciclo de timers Android pasa; no genera sonido ni modifica preferencias reales. **82 pruebas Python** pasan; no aprueban comprensión del modelo.
- Captura local de revisión: `artifacts/android-voice/andromeda-ui.png`, no añadida a Git.

**Cierre de esta entrega:** el Moto dejó de aparecer por ADB antes de transferir la última compilación. La versión visual descrita arriba sí quedó instalada y pasó la instrumentación en el equipo. Quedan locales un ajuste menor (restaurar transcripción al silenciar una vista previa), una aserción adicional de restauración de estado y el runner `test-ui.ps1`; compilación correcta, pendientes de transferir/repetir en el Moto al reconectar. El runner se comprobó con salidas sintéticas: acepta éxito y rechaza tanto crash con exit code 0 como código de resultado incorrecto; esto prueba el runner, **no es otra ejecución de UI en el teléfono**. Última comprobación conectada: sin servicios de voz/alarma activos.

## Lo que falta para el MVP funcional

1. **Conversación personal incremental con LFM:** modelo base elegido por el usuario, apagado; observar los fallos conversacionales aceptados sin volver a abrir comparaciones por defecto. Los errores de intención/negación siguen bloqueando acciones autónomas. Las evaluaciones históricas no se convierten en aprobadas por esta decisión.
2. Validar con captura humana plazo/cancelación y Silenciar de la ruta 0.4. Las garantías del backend **histórico con herramientas** no se heredan de la ruta nueva sin ejecutor; acciones continúan bloqueadas.
3. Primer ensayo personal de conversación 0.4 con voz real tras sintéticos; después abordar herramientas por separado. Máximo 30 s desde fin de voz a respuesta audible útil todavía sin certificar.
4. Alarmas programadas, recordatorios, cálculos e información con fuentes: objetivos de la entrega funcional, **no capacidades ya integradas por esta UI**. Clima en reposo validado en el Moto con 0.2; pendiente integrarlo a conversación. Música/proveedores y otras funciones amplias requieren concretar alcance.
5. Completar inspección de entrada/borde y espera física de opciones en 0.2; reloj y clima ya verificados. El usuario aclaró el significado del loader; no extenderlo a batería.
6. Ampliar medición breve conjunta 0.4 a uso sostenido y validar protección de carga antes de operación 24/7. No se modificaron batería, swap, servicios Android ni arranque automático.

## Reproducir la prueba visual (APK ya instalado)

```powershell
.\android-voice\test-ui.ps1 -Serial ZY227BCQ85
```

El script exige `passed=true` y el código de instrumentación correcto: no acepta el exit code 0 de `am` como prueba de éxito. No ejecutar sobre una sesión de audio en curso. Para compilar: `android-voice/build.ps1`. No instalar ni iniciar pruebas de micrófono como consecuencia implícita de esta comprobación visual.

Pruebas locales de políticas sin Android: `.\android-voice\test-policy.ps1`.
