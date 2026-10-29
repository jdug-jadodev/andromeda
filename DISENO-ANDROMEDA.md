# Andrómeda: diseño revisado, Moto como único equipo

**Entrega vigente: 0.11.1/código 18 de agenda/web instalada con consentimiento y respaldo.** Conserva modelo/límites/privacidad y añade TLS 9, menús y obligaciones separadas. Controles Android sin audio de SQLite/listas vacías/TLS con fixtures pasan; no certifican AlarmManager/entrega/latencia acústica reales. No escucha/modelo residente ni ventana humana activa. «Silencio» aplazado, no reparado por estas pruebas. `AGENDA-Y-WEB-ANDROMEDA.md`; los avisos de entregas anteriores siguientes son históricos.

**Corrección vigente 0.10.1 instalada con consentimiento:** acepta «silencio» con/sin nombre durante salida y añade fuente AEC/diagnóstico numérico RAM. Pruebas sin captura/altavoz pasan, pero **el corte acústico aún debe validarse** tras el fallo humano 0.10. No habilita residencia ni otra ventana humana; `SILENCIO-ANDROMEDA.md`.

**Ampliación 0.10 solicitada:** «Andrómeda, silencio» corta voz/tono actual sin cancelar eventos futuros. Listener local de interrupción exclusivamente durante reproducción, separado del LFM/captura conversacional y de la parada dura. Instalada con pruebas sin captura/altavoz; falta aceptación humana sobre audio real. Alarmas/recordatorios todavía no implementados, deberán usar el mismo mecanismo. No habilita escucha permanente ni residencia: `SILENCIO-ANDROMEDA.md`.

**Decisión/implementación posterior 0.9:** conserva entrada 0.7 y control sin acciones; timer de bajo riesgo por petición completa, sin frase extra, y cierre tras resultado Android real. Consultas/aclaraciones continúan, mezclas se separan y no se habilitan acciones generales: `PETICIONES-Y-CIERRE-ANDROMEDA.md`. Usuario pide planear micrófono activo/modelo residente y ensayos largos, **todavía no activados**: `ENSAYO-PROLONGADO-ANDROMEDA.md`. Sintéticos no certifican plazo audible ni uso 24/7. Versiones anteriores quedan como historial.

## 1. Condiciones del usuario, no negociables sin volver a acordarlas

- El Moto G8 Plus es el corazón del proyecto: reutilización y valor sentimental de su primer teléfono comprado. No sustituirlo ni exigir un PC/mini-PC.
- Procesamiento de IA, voz, memoria e inventario del hogar locales. Sin inferencia en nube ni envío de datos domésticos por defecto.
- Conversación natural corta con hilo y herramientas elegidas por la IA. No programar respuestas para cada pregunta ni exigir una frase exacta para cada acción.
- **Uso cotidiano final 100 % por voz, aclarado explícitamente por el usuario:** decir «Andrómeda» y pedir un timer no debe requerir botón, menú, ADB/SSH ni preparación manual por interacción. Los controles actuales solo autorizan pilotos; conservar opciones/parada de emergencia no los convierte en pasos obligatorios del uso diario. Cerrar una charla en el futuro modo residente debe volver a espera del nombre, no apagar todo el asistente. Ese modo todavía no está implementado/activo; arranque automático y ensayos largos requieren su etapa propia.
- **Máximo 30 segundos desde terminar de hablar hasta empezar una respuesta útil.** Objetivo preferido para uso rutinario: 5–10 s. Todavía NO está cumplido.
- Reloj visible y cara durante interacción; no bloqueo automático. Mantener Android, teclado, cámaras, audio y conectividad.
- Memoria por conversaciones separadas, cada una caduca tras una hora de su propia inactividad. Temporizadores/recordatorios persistentes separados.
- **Corrección vigente de entrada:** el usuario descarta el selector manual propuesto. Al abrir, sin «recuerdas» se crea charla nueva; «Andrómeda, recuerdas…» busca una charla aún guardada y pide confirmación antes de retomarla. Dentro de una charla activa las preguntas siguientes permanecen en ella. 0.7/protocolo 6 instalada con búsqueda léxica acotada, sin otro modelo ni acciones; no equivale a memoria semántica fiable de LFM. `ENTRADA-VOZ-ANDROMEDA.md`.
- **Aclaración de flujo del usuario:** «Andrómeda» abre una charla de múltiples turnos, sin repetir wake por pregunta. Cierre por petición explícita/gracias a solas, o negativa a oferta de más ayuda contextual; cerrada no significa borrada. Solicitar por tema una charla aún disponible y confirmar antes de retomarla; ausencia/ambigüedad deben resolverse con el catálogo real, no un sí/no inventado por LFM. Primera parte **0.6 instalada con autorización**, sin búsqueda temática todavía. Sintéticos de continuidad y ambos cierres pasan sin micrófono/altavoz; falta aceptación humana. `FLUJO-CHARLAS-ANDROMEDA.md`.
- **Ajuste solicitado antes de ensayar:** 0.6.1 instalada añade oferta de más ayuda tras respuesta y despedida hablada antes del cierre confirmado, sin otra inferencia, sin promesa falsa de escucha tras apagar captura y sin ampliar memoria/modelo. Protocolo 5, 190 tests Python/266 aserciones JVM y sintéticos sí/continuación directa/no/despedida pasan sin micrófono/altavoz. Ventana humana de cinco minutos solicitada, aún sin aceptación humana; búsqueda por tema pendiente. `FLUJO-CHARLAS-ANDROMEDA.md`.
- Conservar SSH, web y archivos existentes. No root para modelo/agente; no comandos libres generados por IA.
- Protección reversible de carga y ensayo sostenido antes de uso desatendido 24/7.

Esta revisión no abre micrófono, no habilita inicio automático y no modifica carga, swap ni servicios Android.

### Decisión posterior del usuario sobre el modelo

**LFM2.5 1.2B Instruct QAD Q4_0 queda elegido como base provisional para conversación casual corta.** El usuario acepta los errores conversacionales observados y prefiere continuar con un MVP incremental y revisarlos con el uso, en lugar de seguir comparando modelos hasta eliminar todos los fallos. Registro en `config/model_selection.json`; no es una configuración de arranque.

Esta tolerancia de calidad permite preparar una prueba personal de conversación sin acciones; **no equivale a aprobar seguridad de herramientas, negaciones, duraciones o confirmaciones falsas de ejecución**. Conserva plazo de 30 s, parada/cancelación, control de recursos y permisos explícitos de micrófono. No activar escucha permanente, inferencia desatendida ni herramientas como consecuencia implícita. No reabrir comparativas/descargas por defecto ni atribuir aprobación semántica a resultados históricos fallidos.

**Entrega posterior autorizada con «iniciemos»: 0.4 instalada**, conversación LFM sin ejecutor, manual/acotada; mantiene la prueba wake-only anterior. Plazo global basado en fin de palabra estimado, controles no bloqueados por la inferencia, terminación de servidor dedicado, contexto contado por tokens y TLS con pin + token independiente. Tres turnos sintéticos ASR/LLM/TTS/UI en Moto: 5.357–6.409 s hasta primer PCM sintetizado, todavía error de perspectiva/recuerdo. No certifica voz humana ni inicio audible útil <=30 s. Evidencia y límites en `CONVERSACION-ANDROMEDA.md`; las observaciones del piloto histórico con herramientas que siguen abajo no describen garantías de la ruta nueva, ni quedan corregidas para herramientas por ella.

**Posterior: voz audible confirmada por el usuario**, junto a parada con Silenciar y salida inesperada al launcher. Se identificó crash de cierre TLS en hilo gráfico, corregido en **0.4.1 / código 5 instalada**: cierre asíncrono independiente, sin relajar StrictMode. Regresión de TLS real y botón/reloj pasa sin iniciar servicio de captura, micrófono, TTS ni modelo; 145 tests Python/89 aserciones JVM. Falta repetir la parada con voz humana en esa corrección; no atribuir latencia, corrección semántica, seguridad de herramientas ni aprobación 24/7 a la mera respuesta audible.

**Prioridad explícita posterior: también trabajar el contexto**, no sustituirlo por corregir la claridad del arranque. Un ensayo humano con contadores confirma historial de dos pares enviado sin recorte y negativa de recuerdo del modelo. Dos propuestas acotadas de presentación, mismo LFM, se probaron con texto público: aclaración de sesión y diálogo estructurado. La primera no aporta mejora fiable; la segunda recupera algunos temas, pero regresa en ausencia/propiedad/capacidades. Ninguna aplicada a voz. Evidencia en `EVALUACION-MEMORIA-LFM.md` y `EVALUACION-CONTEXTO-ESTRUCTURADO.md`, 165 tests de soporte. Contexto sigue siendo requisito: retener datos, usar referencias y respetar ausencia de información son objetivos distintos; ampliar almacenamiento no demuestra que el modelo los aproveche.

**Continuación explícita del usuario y preguntas sobre acciones: 0.5 instalada.** Memoria breve en RAM Android desacoplada del proceso de inferencia/micrófono, hasta cuatro charlas/seis turnos, caducidad por una hora de actividad humana independiente, selección/nueva/olvido explícitos. Silenciar conserva contexto; muerte del proceso/reinicio lo borra. Protocolo 3 validado restaura roles nativos y preguntas sin respuesta sin fabricar salida TTS; mismo LFM/prompt/límites, sin ejecutor. 171 tests Python/157 aserciones JVM y pruebas mecánicas/sintéticas en Moto; errores semánticos persisten y voz humana útil <=30 s no certificada. `CONTEXTO-ANDROMEDA.md`. Estado persistente de timers/alarmas/recordatorios separado de chat, confirmaciones solo tras ejecución Android: `ACCIONES-ANDROMEDA.md` es plan, no activación de herramientas.

## 2. Qué significa el límite de 30 segundos

Se mide desde el **último audio hablado**, no desde que Vosk termina ni desde el primer token del modelo. Fin: inicio audible de contenido útil. «Estoy pensando», un tono, la cara animada o una negativa incorrecta NO satisfacen el requisito.

Para una acción, una confirmación de ejecución solo vale después del resultado real del ejecutor. Si se pide autorización, la propuesta tiene su propio turno; la ejecución confirmada se mide desde el fin del «sí» humano. Registrar también el total de ambos turnos y la pausa humana: no esconder que son dos interacciones.

Se registrarán latencia máxima y p95. **Un solo caso superior a 30 s falla la puerta de latencia**, aunque el promedio o p95 parezcan buenos. Un timeout anunciado antes de 30 s es un fallo contenido, no una respuesta satisfactoria. No prometer garantías absolutas del planificador de Android; los incumplimientos se detectan y no se contabilizan como éxito.

Presupuesto inicial (objetivos, no tiempos ya medidos):

| Parte | Presupuesto |
|---|---:|
| Fin de voz y ASR final | 2.5 s |
| IA + todas las herramientas de ese turno | 22 s |
| Inicio de síntesis audible | 1.5 s |
| Puente y margen | 4 s |
| **Total** | **30 s** |

Contrato legible por máquina: `config/acceptance.json`. Evaluador de evidencias: `backend/acceptance.py`, CLI `scripts/evaluate-acceptance.py`. **El evaluador es una puerta de pruebas, no un controlador del servicio.** El piloto histórico no aplica ese presupuesto; 0.4 sí aplica vencimientos de turno basados en alineación ASR estimada, sin convertirlos en certificación de voz. Solo evidencia de voz real puede certificar voz; pruebas sintéticas y dobles no la sustituyen. Las anotaciones de corrección requieren revisión, no se deducen de que el LLM haya generado texto.

Usar un reloj monotónico común del mismo kernel: Android `SystemClock.elapsedRealtime` y, tras verificar equivalencia, Debian `CLOCK_BOOTTIME`. No restar reloj de pared ni mezclar `CLOCK_MONOTONIC` con elapsedRealtime durante suspensiones. Instrumentar fin de voz y comienzo real de reproducción, no simplemente la llamada a `tts.speak`.

## 3. Auditoría: qué conservar y qué no aceptar como sistema final

### Conservar

- Android para hardware y UI; Debian para lógica, red e inferencia.
- llama.cpp y los modelos como material de prueba, cargando **uno solo**.
- Captura PCM local, Vosk compartido para detector/comando, pausas durante TTS/alarma, botón Silenciar y notificación.
- Temporizadores Android persistentes e identificadores idempotentes. Seguir verificando AlarmManager/recuperación real; la instrumentación aislada no sustituye reinicio autorizado.
- Catálogo de herramientas, validación de argumentos y devolución de resultados reales al agente.
- Almacén conversacional y sus pruebas, sin declarar validada la capacidad de recuerdo del modelo.

### Fallos de diseño/operación observados

1. `ChatModel` permite una espera de **100 s por solicitud**, y `ConversationAgent.run` hasta tres vueltas. Ninguno impone un presupuesto global del turno.
2. Inferencia síncrona dentro de `doja_stream.session`: mientras espera no procesa normalmente controles del cliente. «Silenciar» cierra captura, pero eso no equivale a cancelar a tiempo toda inferencia o impedir cualquier efecto tardío.
3. El catálogo y el historial añaden mucho prefill. La hora mutable invalidaba el prefijo; se corrigió capturándola por turno. Un máximo de diez mensajes expulsaba contexto por el tráfico interno de herramientas; se corrigió a 24, aún con límite de 2600 caracteres.
4. Guardar y reenviar propuestas, confirmaciones y protocolo completos consume tokens que no siempre aportan valor conversacional. Falta separar trazas de ejecución y contexto mínimo que necesita el modelo, conservando resultados ciertos.
5. Lecturas simples de Android pasan por una segunda generación incluso cuando el ejecutor ya ofrece una respuesta completa. Eso debe decidirse **por contrato de herramienta**, no por reglas de frases del usuario.
6. Las herramientas web pueden necesitar dos inferencias y red; el último flujo probado aún ronda 66.6 s. No cumple el producto aunque técnicamente funcione.
7. Modelo/servidor temporal por ensayo: permite pruebas seguras, pero repetir calentamiento de 70+ s no sirve como operación cotidiana. No confundir preparación con latencia de cada pregunta ni ocultarla al usuario.
8. Se priorizaron modelos y componentes aislados antes de cerrar criterios de aceptación de la experiencia completa. Hay muchas pruebas unitarias útiles, pero la serie conversacional real sigue fallando.
9. Autenticación actual del puente no prueba mutuamente identidad del servidor y cliente. No apta todavía para control doméstico permanente.
10. Protección adicional de carga pendiente; medir temperatura de batería no certifica temperatura del SoC ni salud física.

Qwen3 1.7B es **candidato diagnóstico, no modelo final aprobado**: eligió herramientas pero sigue confundiendo identidad/interlocutor y excediendo latencia. No descargar más variantes en serie sin hipótesis y comparación acotadas.

## 4. Arquitectura propuesta

```text
Android
  reloj/cara + captura + TTS + temporizadores + controles manuales
          ↕ puente local autenticado, mensajes versionados y con ID
Debian
  gestor de sesión y presupuesto de turno, siempre atento a cancelar
       ├─ ASR/detector compartido, PCM efímero
       ├─ memoria breve por conversación
       ├─ trabajador de inferencia local, un modelo preparado
       └─ ejecutor de herramientas permitidas
             ├─ Android / planificación persistente
             ├─ adaptadores de hogar autorizados
             └─ Internet público mínimo cuando corresponda
```

### Un modelo preparado, no una escucha permanente implícita

Preparación manual y visible antes de aceptar conversación: cargar modelo, validar plantilla/catálogo, calentar instrucciones y comprobar recursos. Si no está listo, mostrar «Preparando» y no aceptar una orden que quedará esperando indefinidamente. Medir por separado arranque frío.

Mantener modelo durante una ventana de prueba evita recargarlo por pregunta. No habilitar aún daemon de arranque, pantalla kiosco ni escucha permanente. Su duración/residencia futura depende de CPU en reposo, convivencia con Vosk y protección de batería.

### Gestor de turnos no bloqueante

Cada turno tiene `conversation_id`, `turn_id`, generación/cancelación y vencimiento. Gestor de canal y controles separado del trabajador de inferencia; colas acotadas, sin acumular órdenes viejas. Interfaz puede silenciar/cancelar durante el cálculo.

Límite compartido para ASR, IA, red y síntesis; no reiniciar 30 s al entrar en cada herramienta. El ejecutor verifica validez e ID justo antes de mutar estado. Rechazar respuestas tardías y reintentos con efectos duplicados. Cancelar espera del cliente no basta: se debe comprobar que llama.cpp dejó de generar; si no, detener solo el trabajador/servidor de inferencia correspondiente, sin tocar timers ni Android.

Antes del vencimiento reservar tiempo para una explicación breve. Eso contiene un fallo; no lo convierte en un acierto de aceptación. Nada de ejecutar una orden atrasada tras anunciar fallo o después de silenciar.

### Herramientas por capacidad, no por pregunta

Registro extensible con nombre, esquema, permisos, efecto de lectura/escritura, límite de tiempo, idempotencia y modo de presentación:

- `executor_final`: el ejecutor devuelve resultado suficiente; confirmación hablada directamente desde datos reales, registrada para próximos turnos. No requiere otra inferencia solo para reformular «creado» o un listado básico.
- `model_summary`: devuelve datos/fuentes que requieren interpretación; el modelo responde dentro del presupuesto restante. No iniciar esa vuelta si el presupuesto estimado no alcanza.

La IA conserva elección de herramienta y argumentos. Esto no añade un intérprete de frases ni un guion por pregunta. Adaptadores existentes de dispositivos/proveedores deben reutilizarse cuando sea posible; un modelo local no sustituye sus protocolos ni credenciales.

No shell/root ni herramientas universales sin restricciones. Autorizaciones por riesgo; mantener confirmación explícita en prototipo hasta validar efectos. No asumir que Home Assistant puede instalarse en este kernel: automatización inicial puede usar adaptadores locales compatibles sin exigir otro equipo.

### Memoria y contexto

Separar diálogo breve de protocolo y estado persistente. Conservar turnos completos y resultados relevantes; no cortar JSON de herramientas a ciegas ni presentar datos viejos como actuales. Ajustar contexto por tokens, no solo caracteres. No introducir un segundo LLM para resumir cada turno: añadiría coste y posibles errores.

Cada conversación tiene su última actividad humana y caduca individualmente a la hora; herramienta/telemetría no la renuevan. El cierre del proceso puede borrar antes la memoria en RAM, pero nunca los timers persistidos. Implementación actual aún no ofrece selector de conversaciones ni recuperación de contexto entre sesiones.

### Seguridad y privacidad

Para el protocolo histórico, revisar desafío/respuesta mutuos HMAC y nonces, versiones, IDs y protección contra repetición; sigue pendiente. La ruta **0.4 conversation_only** usa otra solución: TLS con certificado fijado verifica el servidor **antes** de enviar una credencial de cliente independiente, más IDs/secuencias de turnos y fuente de audio autenticada. No afirmar que se implementó el HMAC propuesto ni extender esta protección al piloto histórico o al control doméstico.

Datos domésticos e inferencia locales. Internet público envía solo consulta necesaria, no historial/inventario/credenciales; dominios permitidos y resultados tratados como datos no confiables. Consultas que revelarían información privada requieren autorización específica. Ningún endpoint público de control ni cambio automático a nube.

## 5. Recursos y seguridad del Moto

Lectura de reposo auditada, 2026-10-17 04:49 UTC / 16 de octubre 23:49 Bogotá, sin voz/modelo:

- MemAvailable 1560544 KiB (~1.49 GiB), RAM física utilizable ~3.59 GiB.
- Zram con 946884 KiB intercambiados; disco loop9 con 908 KiB usados. No equivalen a RAM física adicional.
- Batería 24.9 °C, 100 %, `Good`, alimentación USB. Esto no certifica salud ni protección de carga.
- Sensores `cpuss-*-usr` / `cpu-1-*-usr` observados aproximadamente 33–36.2 °C; PMIC ~37 °C. Solo muestra de reposo, no pico bajo inferencia.
- Algunas zonas exponen valores de corriente/voltaje/niveles y `lmh-dcvs-00` devuelve 75000. **No interpretar el máximo de todos los archivos `temp` como temperatura física del CPU** sin validar significado y unidades de cada zona. Elegir sensores concretos y conservar controles térmicos OEM.

Objetivo de reserva: >=750000 KiB disponibles sostenidos; guarda de emergencia existente: 500000 KiB. MemAvailable incluye caché recuperable y RSS del modelo no es RAM privada íntegra. Medir PSS/presión real, fallos de página, crecimiento de swap y procesos Android, no sumar RSS sin más.

Las guardas actuales de ensayos exigen batería <38 °C al iniciar y paran a 40 °C. Son límites conservadores de carga de IA, no temperaturas certificadas de riesgo ni protección independiente. Validar monitor de SoC antes de fijar sus límites; no tratar sensores desconocidos como termómetros.

Para aceptación sostenida: modelo elegido + Vosk + TTS + pantalla, primero sin micrófono real (audio sintético), luego con sesiones autorizadas. Sin reinicio espontáneo, sin afectar SSH/web, sin presión sostenida de swap ni degradación térmica. No habilitar 24/7 antes de carga protegida y mecanismo de recuperación validado; no escribir `force_demo_mode` como atajo.

## 6. Orden de trabajo y puertas de salida

### Alcance MVP acordado como dirección de producto

**Cambio de prioridad posterior del usuario (vigente):** sacar primero una versión de Andrómeda como asistente tipo Alexa **sin conexión a dispositivos domésticos**, con cara más fluida y dinámica. El inventario de TV/electrodomésticos/luces queda registrado para después; no pedir modelos ni investigar integraciones ahora. «Todo lo que hace Alexa» expresa la dirección del producto, no una certificación de paridad ni compatibilidad automática con servicios de Amazon.

La primera versión debe organizarse alrededor de voz/conversación y memoria breve, temporizadores, alarmas/recordatorios, consultas habituales (hora/fecha, cálculos, información y clima de Bogotá) y una interfaz Android horizontal expresiva. Varias capacidades aún no están implementadas: distinguirlas de las existentes y validar cada una antes de anunciarla. Música/servicios con cuentas y otras capacidades amplias requieren concretar proveedor y viabilidad; no presumir autorización de suscripciones, nube de inferencia ni otro hardware.

**Primera entrega implementada:** interfaz visual 0.1 instalada sobre `local.doja.voice`, con cara nativa animada, estados tipados, reloj/fecha de Bogotá, controles separados y vista previa sin audio/acciones. Pruebas UI y timers aislados pasan; muestra de app sola ~63 MiB PSS y animación activa ~30 dibujos/s. Modelo y voz siguen sin aprobación, no cambió HOME ni se habilitó escucha. Evidencias, límites y pendientes en `MVP-ANDROMEDA.md`.

**Corrección visual posterior del usuario — vigente, versión 0.2:** reposo con reloj a pantalla completa, clima de Bogotá y «Di Andrómeda»; si el micrófono/activación están apagados, indicarlo junto a la invitación. Doble toque revela opciones; tras 50 s sin interacción/selección se ocultan y se cierran sus diálogos, sin apagar la pantalla ni detener timers. Al activarse entra la cara desde un lado con movimiento de carrera/rebote y transición suave; permanece visible al escuchar, pensar y hablar. Durante procesamiento un brillo con cola recorre todo el perímetro, con color personalizable, mientras la cara muestra expresión pensativa. **Este loader significa procesamiento, no carga de batería.** Tras reconectar ADB, 0.2 instalada sin borrar datos: instrumentación UI pasa, reposo inspeccionado y clima HTTPS funciona en el Moto. Muestra de app con clima ~69 MiB PSS; no es medición conjunta con IA. Pendientes visuales/ensayos físicos detallados en `MVP-ANDROMEDA.md`. No habilita escucha permanente ni aprueba la comprensión del modelo.

La cara deja de ser solo un retoque final: puede desarrollarse como parte de esta entrega, con estados reales (reposo, escucha, procesamiento, habla, error/silencio), transiciones fluidas y animación ligera independiente de la inferencia. No bloquear el hilo gráfico por red/modelo ni simular comprensión o ejecución mediante animaciones. Su desarrollo visual no habilita micrófono permanente, acciones no validadas, reemplazo de HOME ni uso 24/7. Persisten las puertas de calidad y latencia del núcleo; las recomendaciones anteriores de posponer toda UI se interpretan conforme a esta nueva prioridad explícita.

**Autorización posterior de micrófono acotado:** el usuario pidió poder probar «Andrómeda» → cara mientras avanzamos en el modelo. Se implementó/instaló 0.3 con modo **solo activación/ASR**, duración explícita 90 s/5 min, escucha visual 8 s por activación y vuelta al reloj, control visible de silenciar y parada al salir. No interpreta ni ejecuta órdenes, no carga LLM ni inicializa TTS; Android verifica modo y bloquea acciones aun si el backend las enviara. Se permite este ensayo humano tras pasar sus propias pruebas automáticas de detector/UI, **sin exigir aprobación previa del LLM porque no participa**. No levantar la puerta conversacional/acciones, ni activar escucha permanente. Evidencia y preparación manual en `MICROFONO-ANDROMEDA.md`.

**Evaluación posterior para habilitar respuestas:** `EVALUACION-PRECISION.md` contrasta conversación real sin catálogo y una sola variante menos comprimida, Qwen 1.7B Q8, contra Q4; LFM existente como control adicional. Los tres completan las 15 intervenciones, pero fallan semántica de identidad/perspectiva; Q8 no resuelve el bloqueo y aumenta coste. No integrar respuestas de voz todavía ni modificar la UI de activación ya confirmada. Siguiente candidato documentado con reservas, sin nuevas descargas en serie. 96 pruebas Python de soporte pasan, no aprobación del modelo.

**Prioridad posterior del usuario: eficiencia con el modelo existente.** Antes de aumentar tamaño o descargar alternativas, controlar representación de contexto/identidad. Contraste breve ya realizado con Qwen Q4, sin cambios de muestreo ni pesos: reglas generales explícitas no corrigen consistentemente nombre/propiedad/capacidades en regresión y controles nuevos. También se verificó mismo historial/cache parcial/recomputación completa: salidas idénticas erróneas, más latencia sin caché. No adoptar esa variante ni afirmar pérdida de mensajes como causa demostrada; no concluir tampoco que otro tamaño sea obligatorio. Evidencias en `EVALUACION-IDENTIDAD.md`; 100 pruebas de soporte pasan. La UI/micrófono 0.3 permanece separada, sin nuevas activaciones.

**Alternativa autorizada y evaluada posteriormente:** Qwen3.5 2B Q4, archivo ~1.19 GiB verificado en Moto/PC, caché híbrida acotada a cuatro checkpoints, contexto 2048 y thinking apagado. Misma regresión/controles, muestreo oficial de texto y control aislado de presencia 2→0. Se mide ~1.39 GiB PSS del modelo, con memoria disponible >1.4 GiB en las series, pero ambas configuraciones siguen fallando semántica; no aprobado ni conectado a micrófono. Se corrigieron coincidencias parciales de nombres y archivo de Unicode por consola, sin cambiar respuestas del modelo. Ver `EVALUACION-QWEN35.md`; 106 pruebas de soporte pasan. Conservar app 0.3; no inferir aprobación de carga/latencia por poder cargar GGUF.

**Auditoría posterior motor/idioma:** referencias oficiales de plantilla/tokenizador coinciden exactamente en 66 prompts únicos. Ocho pares chat/raw dan los mismos textos; controles de optimización, precisión, muestreo e historial no aportan fiabilidad suficiente. La hipótesis de idioma se contrastó con sistema/pregunta es/en: el idioma influye, pero los errores persisten íntegramente en inglés. Backend CPU genérico separado, ejecutado en el Moto, también conserva errores y tarda 118–129 s; no usarlo para voz ni ampliar el techo de 30 s. Ver `EVALUACION-MOTOR-IDIOMA.md`: 80 generaciones completas, 122 tests de soporte, ninguna configuración aprobada. No atribuir universalmente el fallo a tamaño de modelo ni declarar validado todo el motor por estos controles.

El usuario quiere empezar con un MVP e irlo mejorando, incluyendo automatización del hogar, sin esperar a completar todas las funciones previstas. Esto reduce alcance, no rebaja las puertas de comprensión, ejecución segura o latencia.

- Una sola app Android ligera como cara de Andrómeda: reloj, estados visibles, activación manual y silenciar/cancelar. Reutilizar el piloto; no otra IA ni navegador pesado para dibujar la cara. Animación avanzada y reemplazo de HOME quedan para después del núcleo.
- Debian mantiene modelo local, memoria y herramientas. La app presenta estados reales y usa el puente local; no afirma ejecución por una animación.
- Primera entrega funcional: conversación breve y un temporizador persistente de extremo a extremo, dentro del límite de 30 s, con negaciones y fallos correctamente tratados. Activación manual para pruebas, sin escucha permanente implícita.
- Primera ampliación doméstica: un único dispositivo autorizado, de bajo riesgo, con consulta de estado y acción reversible. Elegirlo tras conocer marca/modelo/protocolo; no asumir compatibilidad ni comprar otro servidor.
- Home Assistant es una opción de integración, no un requisito para dibujar la cara ni para el MVP. Docker sigue bloqueado por este kernel; no prometer instalación operativa. Evaluar una vía compatible con el Moto o un adaptador directo acotado cuando se identifique el dispositivo.
- No instalar una plataforma doméstica amplia ni servicios adicionales antes de justificar su coste y medir convivencia con LLM/ASR/TTS. Automatizaciones persistentes deben funcionar sin mantener al LLM generando y conservar sus propios permisos/estado.
- MVP de pruebas supervisadas no equivale a despliegue 24/7: protección de carga y validación sostenida siguen siendo requisitos de este último.

#### Inventario doméstico inicial aportado por el usuario

- TV LG **65UR7300PSA**, modelo identificado en la imagen aportada; ficha original de LG indica webOS 23. Versión instalada, red/IP, emparejamiento y controles locales efectivos aún pendientes. No registrar el número de serie de la imagen.
- Lavadora LG WashTower (torre); modelo exacto y capacidades de integración pendientes. No asumir que permite iniciar ciclos remotamente ni habilitar acciones de riesgo.
- Aspiradora identificada por el usuario como Xiaomi «Vacuum 5»; confirmar modelo exacto antes de seleccionar protocolo o integración.
- Xbox Series X; capacidades de consulta/control y requisitos de autenticación pendientes.
- Luces de neón del mueble del TV y detrás del TV; marca/controlador desconocidos, Bluetooth o Wi-Fi sin confirmar. No asumir que ambos conjuntos usan el mismo controlador.

Prioridad propuesta, aún no validada: evaluar primero el TV LG webOS, empezando por lectura de estado y una acción reversible como volumen. Confirmar modelo/conectividad y emparejamiento autorizado antes de probar; encendido desde apagado puede tener requisitos distintos al control con TV encendido. No iniciar descubrimiento de red, emparejamientos ni acciones por registrar este inventario. Luces como alternativa si se identifica un controlador compatible; lavadora y automatizaciones de mayor riesgo quedan para fases posteriores.

1. **Esta revisión:** fijar contrato, auditoría y arquitectura. Hecho documentalmente; evaluador offline añadido. No implica ejecución operativa.
2. Implementar gestor de presupuesto/cancelación y contrato de herramientas, con dobles lentos, respuestas tardías y fallos. Verificar transporte seguro antes de uso doméstico continuo.
3. Reducir contexto y hacer comparación acotada con modelos existentes. Primero texto con esquema idéntico al de producción; excluir modelos que prometen acciones sin llamadas o fallan recuerdo. No comprar hardware ni activar nube.
4. Una versión mínima completa: conversación corta + memoria + un timer real, comprobado de extremo a extremo. Después audio sintético con TTS real medido. La acción requiere persistencia real, no solo intención JSON.
5. Solo si pasan puertas anteriores: pruebas humanas cortas autorizadas de frases naturales, referencias, negaciones y cancelación. El contrato requiere dos muestras por cada uno de doce casos centrales; no pedir esas repeticiones mientras el modelo falle las automáticas. Añadir ruido/distancia/más voces con consentimiento después.
6. Validar recursos y batería; solo entonces operación prolongada. Completar Internet general/clima, música/TV y dispositivos en adaptadores separados; chat remoto autenticado :10000 después de estabilizar núcleo de voz.

Una puerta fallida bloquea la siguiente: no declarar «listo» por más pruebas unitarias ni trasladar al usuario el diagnóstico del modelo. Las funciones amplias del proyecto siguen en `PLAN-DOHA.md`, pero este documento manda sobre decisiones técnicas de los pilotos históricos.

### Corrección de prioridad tras análisis crítico

Antes de ampliar el gestor de turnos, evaluar **capacidad del modelo e integración por separado**: conversación mínima sin herramientas, mismo contexto con una sola herramienta, interpretación de un resultado conocido, negación/relato/ambigüedad. Mismo modelo, parámetros y protocolo; casos públicos congelados. El corte temporal contiene fallos, pero no convierte un modelo incorrecto en competente. No continuar desarrollo amplio si esta puerta falla. Resultados mecánicos de nombres/JSON necesitan revisión semántica, no certifican por sí solos calidad.

**Primera serie aislada realizada:** ver `EVALUACION-CONTROLADA.md`. Doce casos con Qwen3 1.7B, 4.167–21.965 s de API: nombre correcto sin catálogo y erróneo con una sola herramienta, conversión de minutos fallida y problemas de perspectiva. Interpretó resultados simulados de éxito/fallo. No aprobó calidad ni voz; no abrió micrófono o ejecutó timers.

**Contrastes posteriores:** el prompt conserva roles e historial. Se reprodujo una respuesta distinta para el mismo prompt según reutilización parcial; KV f32 y quitar caché adicional no lo corrigieron. Microbatch 1 estabilizó cuatro repeticiones de ese caso, sin probar una corrección universal. Cantidad/unidad se descartó por extraer 180 minutos de tres minutos. Contrato literal verificado dio seis respuestas coherentes en nuevos ejemplos y peticiones calientes ~4.7–7.3 s, pero la regresión encontró pseudo-llamadas en relatos y problemas de perspectiva/causas inventadas. Qwen 0.6B no se acepta para acciones: inventó duración. Detalles y límites en `EVALUACION-CONTROLADA.md`; ningún perfil nuevo puede abrir piloto de voz desde el coordinador central.

**Conversación secuencial posterior:** ver `EVALUACION-CONVERSACIONAL.md`. Con historial generado real, Qwen 1.7B falla identidad/negación; LFM no supera recuerdo/uso de herramientas; instrucciones v2 rechazadas. Se corrigió devolución de errores de validación del simulador y se contrastó Qwen con muestreo oficial (0.7/0.8/20/0, sin thinking): persisten fallos. No continuar a voz/acciones por los aciertos aislados ni confundir thinking de 32 tokens con razonamiento completo de referencia. 82 pruebas unitarias de soporte pasan, configuración del asistente no aprobada.

## 7. Fuentes de Internet e interfaz futura — requisitos añadidos, no implementar aún

Wikipedia **no es obligatoria ni fuente única**, y ni Wikipedia ni un resumen generado por Google son garantía de verdad o actualidad. La búsqueda debe permitir fuentes adecuadas, fechas y referencias; para clima usar una fuente meteorológica con ubicación y hora de actualización, no una frase de IA sin comprobar.

**Decisión posterior del usuario: descartada la IA de Google para consultas/resúmenes web.** No integrar Gemini ni extraer AI Overviews como fuente del asistente. Mantener búsqueda general y fuentes directas como capacidades futuras, con síntesis local y consultas públicas mínimas: nunca inventario, credenciales ni conversación doméstica. Esta decisión sobre IA de búsqueda no implica cambiar el motor TTS Android existente ni autoriza otro proveedor de inferencia en nube.

Interfaz se aborda **después** de que funcione el núcleo:

- Teléfono siempre **horizontal**; diseñar toda la interfaz para ese uso.
- Reposo sin activación: reloj digital dominante a pantalla completa y temperatura de **Bogotá**, mostrando antigüedad/falta de datos cuando corresponda. Distinguir observación de estimación meteorológica. Invitación «Di Andrómeda» con estado real de activación; opciones ocultas, reveladas por doble toque y cerradas a los 50 s sin interacción.
- Activación: cara dinámica de Andrómeda, entrada suave desde un lado como si llegara corriendo, animaciones, movimientos, gestos y estados expresivos. Mantener la cara al pensar, con expresión pensativa. No presentar emoción animada como sentimientos reales del modelo.
- Brillo con cola que recorre todo el borde **durante procesamiento**, color personalizable; no ocultar la cara. El usuario ya concretó este loader como indicador de pensamiento. No añadir efectos de batería bajo esta autorización.
- Reemplazar y finalmente retirar el **APK launcher Doha** cuando la nueva interfaz funcione como HOME y haya recuperación probada. Mantener respaldo y posibilidad de volver atrás. No desinstalar ni cambiar HOME ahora; no confundir launcher con backend Debian/Nginx/SSH ni borrar sus datos por compartir el nombre Doha.

## Cara solo con ojos y sueño por silencio (2026-10-28)

- `FaceView` dibuja solo ojos, sin boca, y todo valor visible se acerca despacio a su objetivo (sin saltos). La referencia visual es `artifacts/andromeda-face-preview.html`, con las mismas fórmulas. El único cambio de color de los ojos es el fallo (`ERROR` y la reacción `SAD`).
- Estados nuevos `DROWSY` (con sueño) y `ASLEEP` (dormida: ojos como paréntesis acostado y «Z» a la derecha). Reacciones dibujadas: tristeza, llorar, cariño y chiste (ojos de luna). Frío, calor, niebla, celebrar, asco y genial no se dibujan por ahora.
- `ListeningSleepPolicy`: mientras el diálogo escucha y nadie habla, 15 s despierta, 10 s con sueño, 5 s dormida. Si una voz llega con sueño o dormida (pasa a pensando o hablando), se despierta asustada. A los 30 s el backend cierra la escucha y la cara se desvanece hacia el reloj.
- La ventana de escucha era de 50 s. Ahora es 30 s en `backend/conversation_voice.py` (`LISTEN_IDLE_MS`) y en `ListeningSleepPolicy.CLOSE_AFTER_MS`; la app rechaza el `ready` del backend si los dos números no coinciden, así que se cambian juntos.
- Límite conocido: el susto llega cuando el backend entrega la transcripción (al terminar de hablar), no al empezar la voz.

### Salidas de la cara (2026-10-28)

- **Despedida** (la cara pasaba de «hablando» a la pantalla del reloj): `MainActivity.present` detecta `SPEAKING` → cara oculta y llama a `FaceView.startJump()`. La cara se agacha, salta y cae por abajo; a los 0,55 s el reloj cae desde arriba con dos rebotes (`FaceExitMotion`, mismos números que el HTML). A los 1,6 s termina y se oculta la capa de la cara. Con animaciones desactivadas en el sistema no hay salto.
- **Dormida que se cierra**: antes el estado pasaba a «sesión preparada» durante el desvanecimiento y los ojos se abrían un instante. Ahora la cara conserva `ASLEEP` (ojos cerrados) hasta desaparecer.
- Mientras una salida está en curso, los refrescos del servicio no cambian la cara; si alguien llama a Andrómeda a mitad de la salida, se cancela y vuelve la cara normal.
- `FaceInstrumentation` comprueba el salto, los ojos cerrados al dormirse y la cancelación.
