# Doha / Doja: servidor doméstico y asistente con IA

Fecha de revisión: 16 de octubre de 2026.

**Nombre actual del asistente: Andrómeda**, elegido por el usuario en lugar de Doja. Doha sigue siendo el nombre técnico del Moto/launcher existente; paquetes y rutas históricos se conservan sin migrar ni borrar datos. Identidad central en `config/assistant.json`. Se pausaron los ensayos Doja y la activación por nombre permanece deshabilitada hasta validar el nuevo detector español. Inspección sin micrófono: Vosk contiene `andrómeda` (ID 62224), pero no `andromeda` sin tilde; presencia en vocabulario no prueba reconocimiento acústico.

**Avance posterior:** activación española por Andrómeda + temporizador de diez segundos validada con voz real y confirmación audible del usuario, sin botón. Pico de backend ~151 MiB, sin LLM cargado. Sigue deshabilitada la escucha permanente; solo pilotos acotados. Estado y pruebas en `ACTIVACION-ANDROMEDA.md`.

Actualización posterior del usuario: ya reactivó el launcher Doha. Este cambio está informado por el usuario, todavía no verificado nuevamente por ADB.

Verificación posterior por ADB: HOME resuelve a `local.doha.dashboard/.MainActivity`. Respaldo inicial de configuración y APK guardado en `backups/2026-10-16/`, con SHA-256 verificado; no es una imagen completa del teléfono. Pantalla: timeout de 30 minutos y ajuste `stay_on_while_plugged_in=7` (permanecer encendida con alimentación); falta prueba visual de larga duración y de bloqueo.

**Inspección más reciente (cierre de evaluación conversacional):** HOME resuelve ahora a Motorola `com.motorola.launcher3/...CustomizationPanelLauncher`, distinto de la verificación histórica anterior. No se cambió HOME ni se desinstaló Doha durante esta evaluación. No restaurar automáticamente ni atribuir el cambio a una causa no comprobada; revisar cuando toque la interfaz futura.

**Confirmación del usuario:** él cambió el launcher porque Doha ya no se usará como interfaz final. Mantener su elección actual; retirar el APK antiguo solo en la fase acordada y sin tocar Debian/Nginx/SSH por compartir nombre.

## 1. Objetivo y alcance de esta revisión

**Diseño vigente tras reevaluación con el usuario:** `DISENO-ANDROMEDA.md`. Moto como único equipo por funcionalidad y valor sentimental, sin inferencia en nube por defecto. Máximo aceptable de **30 s desde fin de voz hasta respuesta útil**, no promedio ni primer token; objetivo rutinario 5–10 s. `config/acceptance.json` y evaluador offline expresan la puerta de aceptación; el servicio actual todavía NO aplica ese presupuesto. No seguir ampliando funciones ni abrir voz hasta corregir gestor de turnos/contexto y pasar pruebas automáticas de núcleo completo.

**Corrección de alcance más reciente:** conversación normal con hilo y herramientas elegidas por la IA, no un clasificador de órdenes ni consultas programadas una a una. Hay un núcleo conversacional nuevo y puente de resultados, pero pruebas con LFM real fallaron memoria/selección de herramientas y latencia; modo de voz conversacional no habilitado. Ver `CHAT-HERRAMIENTAS.md`. Mantener inferencia local como preferencia; no sustituirla por nube sin autorización.

**Avance posterior:** API nativa de chat/herramientas con `--jinja`, 45 pruebas unitarias y comparación local secuencial con Qwen3 1.7B Q4_K_M verificado por SHA-256. El 1.7B seleccionó creación/listado y consultó Wikipedia con respuesta posterior; ejecución Android simulada en ese ensayo, sin micrófono. Se corrigieron reloj/contexto y expulsión temprana de historial por mensajes de protocolo. Persisten recuerdo inconsistente y latencia alta; no dar el chat normal por completado ni abrir voz por pruebas parciales. Métricas y límites actualizados en `CHAT-HERRAMIENTAS.md`.

**Evaluación aislada más reciente:** doce casos congelados, mismo Qwen3 1.7B, sin agente/ASR/Internet/acciones: recordó nombre sin herramientas y falló el mismo historial con una herramienta; falló conversión de dos minutos, acertó treinta segundos e interpretó resultados conocidos. API 4.167–21.965 s; no equivale a latencia de voz. Revisión semántica detecta más problemas que el chequeo mecánico. 58 pruebas Python de soporte pasan; modelo no aprobado. Ver `EVALUACION-CONTROLADA.md`.

**Avance experimental posterior:** prompt inspeccionado; respuesta del mismo caso depende del cálculo/caché parcial. Microbatch 1 la estabiliza en cuatro repeticiones; no se identifica aún causa exacta. Nuevo contrato literal de duración evita conversiones inventadas, pasó seis ejemplos nuevos (~4.7–7.3 s calientes; preparación ~34 s), pero regresión muestra fallos semánticos pendientes. Variante cantidad/unidad descartada; Qwen 0.6B excluido como candidato actual de acciones por inventar duración. Parser/evaluador rechazan pseudo-llamadas impresas como texto. Todo solo en pruebas, sin micrófono ni timers reales; no aprobado para producción. Estado vigente en `EVALUACION-CONTROLADA.md`.

**Puerta conversacional completa, posterior:** `EVALUACION-CONVERSACIONAL.md` compara historial generado real, LFM, instrucciones v2 y Qwen con muestreo oficial. Persisten errores de identidad y solicitud ante negación; dos inferencias para un timer consumen ~27–29 s sin voz. Variante v2 rechazada y perfiles bloqueados para uso real. Simulador corregido para devolver errores de validación antes del siguiente turno. 82 pruebas de soporte pasan; no hay modelo aprobado ni autorización implícita de nube, otro hardware o cambios de interfaz.

**Prioridad vigente y primera entrega visual:** el usuario pidió empezar por asistente tipo Alexa sin dispositivos domésticos y cara fluida, no investigar TV/lavadora ahora. UI 0.1 horizontal instalada, cara Canvas y vista previa explícita sin audio; app sola ~63 MiB PSS, prueba breve ~30 dibujos/s activa. No es todavía MVP de voz aprobado. HOME Motorola intacto, modelo apagado y controles históricos de voz separados como pruebas. Ver `MVP-ANDROMEDA.md` y alcance actualizado en `DISENO-ANDROMEDA.md`.

**Correcciones visuales 0.2 posteriores:** reposo con reloj a pantalla completa/clima/«Di Andrómeda», doble toque para opciones y cierre a los 50 s, entrada de cara como carrera y brillo con cola por todo el borde al procesar (color personalizable, cara pensativa visible). Código implementado/compilado, 35 aserciones locales de política/clima pasan; pendiente instalar y ejecutar instrumentación porque el Moto no está en ADB. Clima público Open-Meteo sin datos privados; no se activó escucha ni modelo ni se cambió HOME. Detalles y límites en `MVP-ANDROMEDA.md`.

**Actualización tras reconectar:** 0.2 instalada sobre la app existente, APK previo respaldado, versión comprobada e instrumentación UI correcta (35 dibujos/1200 ms, sin audio/modelo ni cambios en timers). Reposo a pantalla completa inspeccionado y clima obtenido por HTTPS desde el Moto. App con clima ~69 MiB PSS; HOME Motorola intacto. ADB se desconectó de nuevo después, pero esta vez la actualización sí quedó instalada. Pendientes de inspección física y pruebas conjuntas en `MVP-ANDROMEDA.md`.

**Avance 0.3, autorizado por el usuario:** micrófono y despertar visual en paralelo al diagnóstico del LLM, no esperar a completar todas las funciones. Modo wake-only instalado: sesión manual 90 s/5 min, detector Vosk español, cara de escucha 8 s y transcripción sin órdenes/LLM/TTS; Android bloquea eventos de acciones. 88 pruebas Python, 48 aserciones JVM, UI en Moto y nueve casos sintéticos pasan, incluido retorno tras espera real de 8 s. No es aprobación de voz conversacional ni escucha permanente. Detalles y prueba humana pendiente en `MICROFONO-ANDROMEDA.md`.

**Posterior:** el usuario confirmó voz → carita en 0.3; backend corrobora activación por voz y terminó, sin escucha residual. Para respuestas conversacionales se probaron 15 turnos nuevos sin catálogo con Qwen Q4, Qwen Q8 (única descarga de control de precisión, mismo base/revisión) y LFM existente. Persiste confusión usuario/asistente y falsas capacidades; no se activa conversación por voz. Tiempos/recursos y límites en `EVALUACION-PRECISION.md`, 96 pruebas Python de soporte pasan. Q8 apagado y solo diagnóstico; la app no cambia. Candidato posterior investigado sin descargar, no adoptar solo por benchmarks publicados.

**Dirección más reciente del usuario:** priorizar eficiencia y contexto/identidad con el modelo pequeño existente, no pasar automáticamente a uno mayor. Se contrastaron dos reglas globales breves, preguntas congeladas y controles nuevos con Qwen Q4; no hubo mejora consistente. Dos historiales exactos recalculados completos conservaron los mismos errores y fueron más lentos que caché. Variante de identidad marcada rechazada y no conectada a voz, sin nuevas descargas. Evidencia en `EVALUACION-IDENTIDAD.md`; 100 pruebas Python de soporte pasan, app 0.3 intacta.

**Prueba posterior autorizada de Qwen3.5 2B Q4:** artefacto de tamaño comparable a Qwen3 Q4, SHA verificado, carga compatible en b11669, texto/no thinking/caché híbrida de cuatro checkpoints. Regresión intacta y controles con receta oficial y presencia 0: cabe (~1.39 GiB PSS), pero no supera comprensión/perspectiva; no reemplaza la voz. Se corrigieron evaluación de nombres/apodos por límites de palabra y Unicode archivado por PowerShell, sin tocar inferencia. Detalle `EVALUACION-QWEN35.md`, 106 pruebas de soporte pasan. Modelo apagado, app 0.3 intacta, sin micrófono, cambios de Android/swap/carga ni autorización de nube u otro servidor.

**Investigación posterior completada:** 17 perfiles/80 generaciones completas, tres resultados interrumpidos separados, 66 prompts/tokens únicos frente a referencias oficiales. Chat/raw coinciden; controles de CPU/precisión/representación e idioma no resuelven identidad/propiedad. También se compiló y ejecutó en Moto un backend genérico separado (118–129 s, errores restantes); motor original y app intactos. El inglés no es solución suficiente. `EVALUACION-MOTOR-IDIOMA.md`, 122 tests de soporte; sin inferencia/micrófono activo, web 8080 y SSH 2222 preservados. Sin commits, pushes, nube ni cambios de carga/swap/HOME.

**Decisión posterior vigente del usuario:** dejar **LFM2.5 1.2B Instruct QAD Q4_0** como base provisional, aceptar los fallos conversacionales conocidos para conversaciones casuales cortas y mejorarlo según el uso. Cerrar las comparativas por ahora; no exigir perfección de identidad como bloqueo absoluto de una prueba personal sin acciones. La decisión no convierte los ensayos en aprobados ni autoriza herramientas, escucha permanente o inferencia desatendida. Próximo bloque: integración conversacional acotada, respetando cancelación, recursos y 30 s. Registro local `config/model_selection.json`; no se cambió ejecución ni app al documentarlo.

**Entrega posterior iniciada por el usuario: 0.4 instalada** sin borrar datos/cambiar HOME. Conversación LFM manual 90 s/5 min, Vosk/TTS español offline y memoria breve tokenizada, **sin catálogo ni ejecutor**. Conserva wake-only 0.3. TLS con pin + token independiente, plazo global estimado, controles separados de inferencia y cierre de su servidor dedicado. 145 pruebas Python/78 aserciones JVM; UI y dos series de tres turnos sintéticos en Moto, sin micrófono/reproducción ni cambios de timers. Fin de WAV → primer PCM sintetizado 5.343–6.409 s; persiste confusión Menta/Luna y **no certifica voz audible útil <=30 s**. Cancelar durante cálculo cerró el servidor en 31 ms; ack tardío rechazado, cierre en 38 ms. Mínimo MemAvailable de los ensayos completos >=1349812 KiB, batería máxima <=23.6 °C; no es validación 24/7. Detalle, preparación de primera sesión humana acotada y límites en `CONVERSACION-ANDROMEDA.md`. Sin nuevas comparativas/descargas, nube, acciones, escucha permanente ni cambios de batería/swap/arranque.

**Corrección vigente 0.4.1 instalada:** el usuario confirmó respuesta hablada y parada al pulsar Silenciar, con cierre inesperado de la app. Crash buffer demuestra `NetworkOnMainThreadException` en cierre Conscrypt desde `VoiceService.onDestroy`; cierre trasladado a hilo independiente sin relajar StrictMode. 145 tests Python/89 aserciones JVM, UI y regresión TLS real + botón/reloj en Moto pasan sin abrir micrófono/TTS/modelo ni servicio real de captura. APK 0.4 respaldado; no se borraron datos ni se cambió HOME. Al terminar: Andrómeda en primer plano, micro/modelo apagados y puertos de voz cerrados. Repetición humana de Silenciar corregido y latencia útil <=30 s pendientes; no reabrir comparativas ni habilitar herramientas/escucha permanente.

**Uso personal y revisión de recuerdo posteriores:** respuestas «más o menos bien», pero niega recordar dos preguntas anteriores. Contadores en una repetición humana prueban dos pares enviados al tercer turno, cero recortes, 165 tokens; el usuario no hizo la cuarta por esa negativa. Sin textos humanos persistidos ni causa interna certificada. Una única comparación pública autorizada de aclaración de sesión, con el mismo LFM/configuración, no aportó mejora fiable y fue más lenta; descartada sin cambiar el prompt de voz. `EVALUACION-MEMORIA-LFM.md`, 157 tests Python/89 aserciones JVM. No pedir más ensayos humanos idénticos ni reabrir comparativas; mejorar claridad de inicio/preparación y parada antes de herramientas. Último cierre: modelo y micrófono apagados, app en primer plano, web/SSH conservados.

**Prioridad posterior explícita del usuario: también el contexto**, no relegarlo por el aviso de red. Segundo ensayo de formato, mismo LFM y diálogos públicos congelados: JSON con roles/mensaje actual mejora algunas recuperaciones pero regresa en ausencia, propiedad y capacidad; no activar. `EVALUACION-CONTEXTO-ESTRUCTURADO.md`, 165 tests Python/89 aserciones JVM, sin cambios en app/prompt de voz. Conservar historial, retenerlo entre pausas durante su hora de inactividad y lograr que el modelo lo use son objetivos distintos. Próximo bloque de contexto propuesto: desacoplar memoria breve del ciclo del modelo/micrófono; no presentarlo como reparación de comprensión ni habilitar escucha/inferencia permanente. Estado final sin modelo/voz activos, SSH/web intactos.

**Entrega posterior 0.5 instalada, contexto continuado a petición del usuario:** cuatro charlas/seis turnos en RAM Android, una hora de inactividad humana por charla, selección/nueva/olvido, retención al silenciar/reabrir y borrado al morir proceso/reiniciar. Protocolo 3 autentica y valida snapshot, preserva preguntas interrumpidas y rechaza callbacks viejos; LFM/prompt/límites nativos intactos y sin acciones. 171 tests Python/157 aserciones JVM, retención TLS con dobles, olvido confirmado, UI, compatibilidad real del motor, sintéticos Vosk/LFM/TTS y wake-only pasan; no es una reparación de recuerdo semántico ni certificación de voz humana. Datos en `CONTEXTO-ANDROMEDA.md`. Diseño de timers/alarmas/recordatorios independientes de la charla en `ACCIONES-ANDROMEDA.md`, sin scheduling nuevo ni herramientas activadas. Mantener Android/HOME/web/SSH/carga/swap; no iniciar otra sesión humana automáticamente.

**Aclaración posterior de UX por el usuario:** una charla contiene múltiples turnos hasta cerrar explícitamente; «no» solo cierra como respuesta a oferta de más ayuda, y se desea retomar por tema con existencia real/confirmación. Primera parte preparada **solo en código/APK 0.6**, protocolo 4, continuidad tras TTS, cierre retomable y hasta 24 turnos bajo el mismo máximo de texto de 0.5. 183 tests Python/249 aserciones JVM y build pasan, **sin desplegar ni tocar la RAM humana del Moto**. Búsqueda por tema no integrada. Prioridad: consentimiento de actualización con pérdida de RAM, pruebas sintéticas de flujo en Moto, luego integración validada del catálogo/confirmación de charla; no salto a timers por dar contexto por resuelto. `FLUJO-CHARLAS-ANDROMEDA.md`.

**Autorización posterior «sí, hazlo»: 0.6/protocolo 4 instalada**, con respaldo de APK/backend 0.5 y pérdida consentida de las charlas de prueba RAM, sin borrar los datos de timers. Transferencia TLS, olvido/aislamiento, UI y Silenciar pasan. Flujo sintético real Vosk/LFM/síntesis: una activación, tres turnos, los siguientes sin wake; cierres «cierra la conversación» y «gracias» pasan, chat cerrado retomable y nuevo vacío. No micrófono/altavoz ni herramientas; falta probar VoiceService/voz humana en una ventana manual acotada y sigue pendiente búsqueda por tema. Evidencia numérica y límites en `FLUJO-CHARLAS-ANDROMEDA.md`. No declarar reparada la semántica de LFM ni el objetivo audible por el acierto de una fixture.

**Petición posterior de preparar cinco minutos con más ayuda/despedida:** 0.6.1/protocolo 5 instalada con respaldo, misma RAM/modelo/límites. Pie de oferta sin nueva inferencia; sí/continuación mantiene hilo; no contextual o cierre explícito sintetiza despedida y cierra tras acuse, sin reabrir captura ni inventar TTS fallido. No decir «solo di Andrómeda» si el micro se apaga. 190 tests Python/266 aserciones JVM, dos sintéticos reales de flujo y TLS/contexto/Silenciar pasan; timers intactos, sin micrófono/altavoz. Preparar una sola ventana `pilot 300` pedida por el usuario, con selección de captura en Android, sin nueva instrumentación/reinstalación sobre la RAM de esa prueba. Búsqueda por tema sigue pendiente.

**Cierre de jornada solicitado después:** el usuario confirma que la oferta/no/despedida/reloj funcionó y pide una última prueba de contexto antes de dormir. Preparar solo `pilot 90`, seleccionar manualmente la misma charla cerrada (no una nueva), comprobar referencia concreta y terminar sin nuevas ventanas ni instrumentación. Primer ensayo humano: dos turnos LFM + despedida, tres commits; no trasladar ese éxito de flujo a aprobación de memoria. Se recuerda TTL de una hora por inactividad: mañana no se garantiza la charla RAM, sí el avance documentado. Tras este resultado, siguiente bloque sigue siendo catálogo/búsqueda y confirmación de chat por tema; no más cambios esta noche por iniciativa automática.

**Resultado y punto de continuación tras reinicio del servidor del agente:** el último `pilot 90` terminó con **cero turnos restaurados al entrar**, cuatro consultas con 0/1/2/3 pares enviados sin recorte, cuatro inicios TTS y tres commits. No valida la recuperación pretendida y no identifica la causa del snapshot vacío ni la utilidad semántica. Antes del catálogo, aclarar feedback humano y selección/identidad/caducidad; no culpar al usuario o al modelo por historial ausente. Solo se inspeccionó el Moto: Andrómeda abierta, sin servicios/puertos de voz/modelo, timers intactos, SSH/web escuchando. Todo queda en reposo para el descanso solicitado, sin reinstalar, reensayar ni iniciar otra ventana. Registro ampliado en `FLUJO-CHARLAS-ANDROMEDA.md`.

**Feedback posterior y pregunta «qué sigue»:** el usuario informa que LFM afirmó recordar, pero al pedir detalles negó acceso a memoria específica. No aceptar ese «sí» como éxito: aunque no se restauró la charla anterior, los mensajes de la sesión nueva sí se enviaron sin recortes. Separar entrada/selección de chat y uso semántico. Próximo bloque propuesto: evitar charla nueva vacía silenciosa al faltar selección, mostrar qué historial se carga, tomar disponibilidad del almacén real y validar respuestas sobre detalles presentes/ausentes con datos públicos. Mantener LFM/RAM/límites, sin otra repetición humana idéntica, instalaciones o preparación automática como resultado de esta revisión. Búsqueda por tema y herramientas quedan después; propuesta detallada en `FLUJO-CHARLAS-ANDROMEDA.md`.

**Usuario autoriza empezar y corrige la propuesta durante el trabajo:** no quiere selector manual; quiere nueva charla por defecto y búsqueda solo al abrir con «Andrómeda, recuerdas…». Se descarta aquella UI de entrada. 0.7/código 9/protocolo 6 instalada con catálogo de hasta cuatro charlas RAM, búsqueda léxica en turnos humanos y confirmación antes de seleccionar ID real; mensajes de la charla activa siguen juntos. No otro modelo ni ampliación de ventana; sí copia temporal acotada de catálogo, sin prometer PSS idéntico. 205 tests Python/279 aserciones JVM y UI pasan; sintéticos de nueva/retomar/ausencia antes de ensayo humano. `ENTRADA-VOZ-ANDROMEDA.md`. No equiparar encontrar una charla con que LFM aproveche bien sus detalles, ni habilitar herramientas por este bloque.

**Entrega 0.7 verificada:** 206 tests Python/279 aserciones JVM; pasan sintéticos reales de nueva, búsqueda con sí y recuperación de detalle público, y ausencia sin chat inventado; una activación por ensayo, cero acciones/captura/altavoz. Protocolo 6 TLS/contexto/Silenciar también pasa; timers intactos, HOME Motorola y web/SSH preservados. Modelos/micrófono apagados al finalizar, fixtures retiradas, ninguna nueva ventana humana preparada automáticamente. Falta ensayo humano del flujo por voz y sigue la limitación semántica general del LFM. Próxima comprobación manual debe abrir una charla normal, cerrarla y preparar otra ventana para pedir «recuerdas…» antes de que caduque la hora; no elegir la charla desde Contexto como requisito. No lanzar esa prueba sin nueva indicación. Datos y hashes en `ENTRADA-VOZ-ANDROMEDA.md`.

**Usuario pide «probemos», dos ventanas humanas completadas:** primera entrada `new`, un turno LFM y despedida, dos pares confirmados; el usuario dice «cerrada». Segunda entrada ofrece búsqueda y selecciona **`resume`, dos turnos restaurados**; la consulta LFM usa tres pares y 188 tokens sin recortes, TTS a 13.092 s estimados, luego despedida/cierre. Recuperación entre ventanas verificada por registros, no dar por correcto el contenido sin feedback: preguntar qué dato/color respondió. Sin transcripciones/audio guardados, reinstalación o modificación entre ventanas. Reposo final verificado: app abierta, modelo/micrófono apagados y timers intactos. No preparar otra prueba automáticamente. Detalle en `ENTRADA-VOZ-ANDROMEDA.md`.

Reutilizar el Moto G8 Plus como servidor doméstico permanente: automatización, conversación en español, memoria, consultas de Internet e integraciones con APIs. Mantener cámara, micrófono, altavoz, Bluetooth, Wi-Fi, teclado y pantalla. A futuro, sustituir el dashboard por un reloj y una cara expresiva blanca sobre negro cuando se active «Doha» / «Doja».

### Prioridades confirmadas del asistente

- **Actualización de prioridad:** voz primero; no construir un chat táctil en el teléfono como interfaz principal. Chat de texto opcional/remoto desde el PC en `http://IP-del-Moto:10000`, protegido con autenticación y restringido a LAN/VPN. No exponerlo a Internet ni asumir acceso seguro al micrófono del navegador por HTTP desde otra máquina.
- **Home Assistant como capa de automatización deseada:** la IA propone acciones y Home Assistant integra dispositivos/escenas. Su ubicación e instalación están pendientes de validar; no dar por hecho compatibilidad Docker/HA OS sobre Android. La voz y la inferencia pueden probarse antes de que HA esté disponible.
- **Velocidad antes que razonamiento profundo:** asistente cotidiano para preguntas casuales, música, TV y hogar; respuestas cortas por defecto, sin modo de razonamiento extendido. No se busca un agente investigador que encadene muchas consultas.
- **Pantalla permanente y sin bloqueo automático:** reloj visible, preferiblemente digital; al hablar con la IA, carita animada; al terminar, volver al reloj. «Reposo» significa reloj visible, no bloquear o apagar la pantalla. No impedir el bloqueo/apagado manual de emergencia ni la salida de mantenimiento.
- **Interfaz futura confirmada (17 de octubre):** uso horizontal, reloj digital con temperatura de Bogotá en reposo, cara dinámica con gestos/emociones al activarse y recorrido de carga por bordes con color personalizable. Primero núcleo funcional; launcher Doha se sustituirá/retirará después de validar nuevo HOME y recuperación, no ahora. Detalle y ambigüedad pendiente de «carga» en `DISENO-ANDROMEDA.md`.
- **Información actual por Internet:** clima y preguntas generales aleatorias, por ejemplo clima en China o biodiversidad de Colombia. Consultar fuentes cuando corresponda, no inventar datos actuales ni aceptar premisas incorrectas. Si falta ubicación, pedir ciudad: China no tiene un único clima.
- Wikipedia no será fuente única. **IA de Google para búsquedas/resúmenes descartada expresamente por el usuario**: no integrar Gemini ni extraer AI Overviews. Evaluar búsqueda general/fuentes directas con interpretación local, fechas, privacidad y referencias comprobadas. No implica modificar TTS Android existente.
- **Memoria conversacional pequeña:** mantener el hilo de conversaciones casuales cortas, no un historial largo por defecto. Recordatorios, alarmas y preferencias confirmadas son datos persistentes separados de esa memoria breve.
- **Funciones básicas prioritarias:** música, televisor, alarmas, recordatorios y temporizadores, además de las automatizaciones ya previstas.

**Esta intervención solo inspeccionó el teléfono y creó este documento en el PC.** No instaló software, no cambió el launcher, no deshabilitó paquetes, no reinició ni modificó scripts del teléfono. Las siguientes fases son trabajo pendiente, no funciones ya implementadas.

## 2. Diagnóstico comprobado por ADB

| Elemento | Estado observado |
| --- | --- |
| Equipo | Motorola Moto G8 Plus, nombre de dispositivo/producto `doha` |
| Android | Android 10; parche de seguridad 2021-10-01 |
| Arquitectura | ARM64 / aarch64, plataforma `trinket`, 8 núcleos |
| Kernel | `4.14.117-perf+` |
| Root | Funciona mediante Magisk `30.7:MAGISK:R` |
| SELinux | Enforcing; conservarlo así |
| RAM física | 3.59 GiB utilizables aproximadamente; equipo de clase 4 GB |
| RAM disponible | Aproximadamente 1.5–1.6 GiB durante la revisión; variable |
| Swap | Aproximadamente 3 GiB de zram y 8 GiB de swap en almacenamiento |
| Almacenamiento `/data` | 50 GB reportados, 19 GB usados, 31 GB libres |
| microSD | Montada dentro de Debian en `/mnt/sdcard`, unos 15 GB libres |
| Debian | Debian GNU/Linux 12 Bookworm, arm64, chroot real con root |
| Raíz Debian en Android | `/data/local/linux/debian-final` |
| Servidor web | Nginx activo, TCP 8080, HTTP local devuelve 200 |
| Dashboard | `/srv/dashboard` dentro de Debian; telemetría activa |
| SSH Debian | Activo en TCP 2222 |
| Autenticación SSH efectiva | Clave pública habilitada, contraseña deshabilitada, acceso root deshabilitado |
| Wi-Fi | Activo; dirección observada `192.168.1.87` |
| Bluetooth | Ajuste apagado; servicios Android presentes. No se probó emparejamiento |
| Teclado | Gboard seleccionado y proceso activo |
| Cámara/audio | Servicios presentes; no se probaron captura ni reproducción |
| Batería | 54–55 %, conectada a corriente, unos 30.4–30.5 °C; sin prueba de capacidad real |
| Tiempo encendido | Cerca de 25 días |
| Termux | Instalados `com.termux`, `com.termux.api`, `com.termux.boot` |
| Termux:Boot | No existe `~/.termux/boot`; no es el mecanismo de arranque encontrado |
| App Doha | `local.doha.dashboard`, versión 1.0, actividad `.MainActivity` |
| Launcher predeterminado | Motorola: `com.motorola.launcher3/com.android.launcher3.CustomizationPanelLauncher` |
| IA actual | No apareció `ollama` en el PATH probado de Debian ni proceso Ollama activo |

La búsqueda con `command -v` tampoco encontró Python 3 ni Node en el PATH probado; confirmar paquetes antes de elegir el backend. No se inspeccionaron credenciales, historial de shell ni mensajes personales. No se verificó la ausencia de cuentas de Android.

### Conclusión inmediata

**No hay que reinstalar Debian para recuperar el servidor: ya está funcionando.** Tras la revisión, el usuario informó que ya reactivó Doha como launcher; no volver a cambiarlo innecesariamente. El arranque automático del backend sigue configurado; falta validar el conjunto mediante un reinicio controlado, después de respaldar.

Android y Debian comparten el kernel y los recursos. No es una máquina virtual ni un arranque Debian independiente. El PID 1 sigue siendo Android `init`: no asumir que `systemctl enable` funcione en este chroot.

## 3. Cómo está organizado el arranque existente

```text
Android + Magisk
  /data/adb/service.d/99-debian-server.sh
    /data/local/linux/scripts/start-debian-server.sh
      espera sys.boot_completed (hasta unos 240 segundos)
      mount-debian.sh: /dev, /proc, /sys, /run y microSD
      mount-sdcard-wait.sh en segundo plano
      valida sshd
      start-dashboard-host.sh
        chroot → /srv/dashboard/start-dashboard-services.sh
          Nginx :8080 + telemetry-loop.sh
      SSH :2222
```

Archivos importantes:

- `/data/adb/service.d/99-debian-server.sh`: hook activo y ejecutable.
- `/data/adb/service.d/99-debian-server.sh.new`: candidato antiguo; **no está activo**. No copiarlo sin revisar: el script actual ya invoca el dashboard.
- `/data/adb/service.d/swap.sh`: configura zram y swap de disco; requiere auditoría antes de volverlo a ejecutar manualmente, pues contiene `swapoff`, reasignación de loops y `mkswap`.
- `/data/local/linux/logs/`: logs de montaje y arranque.
- `/data/local/linux/debian-final/srv/dashboard/`: web, scripts y telemetría actuales.
- `/data/local/linux/debian.img`: archivo de tamaño lógico 32 GiB; no asumir que ocupa eso físicamente ni que sea el sistema activo. Los procesos Nginx y SSH tienen su raíz en `debian-final`.
- Existen `debian`, `debian-incompleto`, backups y variantes de scripts: **no borrar** hasta determinar uso y contar con copia externa.

### Recuperación de la pantalla Doha — realizada por el usuario; comandos de referencia

Primero respaldar APK, datos y configuración actual del launcher. Después:

```powershell
# Abrir Doha una vez para comprobar su pantalla y conexión al backend:
adb shell am start -n local.doha.dashboard/.MainActivity

# Convertirlo en pantalla de inicio:
adb shell cmd package set-home-activity --user 0 local.doha.dashboard/.MainActivity
adb shell input keyevent KEYCODE_HOME

# Verificar el launcher resuelto:
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Reversión al launcher observado actualmente:

```powershell
adb shell cmd package set-home-activity --user 0 com.motorola.launcher3/com.android.launcher3.CustomizationPanelLauncher
adb shell input keyevent KEYCODE_HOME
```

Ser HOME predeterminado no es lo mismo que un kiosco bloqueado. Probar encendido, desbloqueo, botón Inicio y recuperación tras cierre. Si el arranque no muestra Doha, revisar keyguard y comportamiento de la actividad antes de agregar otro mecanismo. Conservar Ajustes y una salida de mantenimiento; no implementar un bucle agresivo que impida usar el teléfono.

Desde el propio teléfono el backend está en `http://127.0.0.1:8080/`; desde la LAN, actualmente `http://192.168.1.87:8080/`. La IP puede cambiar: reservarla por DHCP en el router. No se verificó la URL interna que utiliza el APK.

## 4. Arquitectura recomendada

```text
Android: pantalla/cara/reloj + micrófono/voz + cámara + Bluetooth/Wi-Fi
                         ↕ puente local autenticado y limitado
Debian: backend Doha + memoria SQLite + automatizaciones + adaptadores API
                         ↕
       Ollama local pequeño / inferencia remota opcional
                         ↕ herramientas autorizadas
     Dispositivos LAN, MQTT, Home Assistant si está disponible, APIs externas
```

### Responsabilidades

- **Android mínimo, no eliminado:** drivers y APIs de hardware, interfaz táctil, audio y conectividad. No detener `system_server`, SurfaceFlinger, SystemUI, servicios de cámara/audio, Wi-Fi, Bluetooth, teclado, gestión térmica ni componentes de permisos.
- **Debian:** lógica del asistente, base de datos, automatizaciones, cola de tareas y conectores. Elegir un backend ligero tras confirmar runtime instalable, por ejemplo Python + FastAPI.
- **Supervisor:** reutilizar un único hook Magisk y añadir supervisión ligera, reinicio con espera progresiva, bloqueo contra duplicados, health checks y rotación de logs. No lanzar cada servicio desde varios sitios a la vez.
- **Puente Android:** empezar validando Termux:API; para audio continuo/Bluetooth puede hacer falta una app Android propia con permisos y servicio en primer plano. Instalar Termux:API no concede automáticamente control completo del Bluetooth ni del hardware desde Debian.
- **Separación de privilegios:** IA y backend sin root. Puente con acciones concretas permitidas; nada de ejecutar comandos arbitrarios generados por el modelo.

Advertencia encontrada: telemetría ejecutándose con UID 1000, que corresponde a `system` en Android. En un chroot los UID son compartidos: diseñar usuarios dedicados sin colisiones con identidades privilegiadas Android. Un chroot no es una frontera de seguridad fuerte.

### Home Assistant y automatización

Home Assistant sería una buena capa de integración si existe o se incorpora otro host compatible. **No basar el plan en instalar Home Assistant Core con pip en Debian 12:** las opciones oficiales actuales son OS y Container. Tampoco asumir que Docker funciona sobre este kernel Android; faltan pruebas de namespaces, cgroups y capacidades.

Para mantener todo inicialmente en el Moto: backend pequeño + MQTT opcional + adaptadores para los primeros dispositivos. Añadir Node-RED solo si justifica su consumo; no instalar toda la pila simultáneamente. Un Home Assistant externo futuro puede ampliar integraciones sin sustituir al teléfono como asistente.

## 5. IA local: expectativas y prueba de rendimiento

El equipo es viable para automatización y una IA pequeña, **no para garantizar una experiencia equivalente a un modelo grande con conversación y reconocimiento de voz pesado simultáneos**. La swap evita algunos cierres, pero no transforma 4 GB en 15 GB de RAM rápida; el intercambio a almacenamiento causa lentitud y desgaste.

1. Comprobar compatibilidad del binario Linux ARM64 de Ollama con este kernel/chroot. Instalación manual versionada, sin depender de systemd ni ejecutar scripts remotos a ciegas.
2. Empezar con un modelo instruct multilingüe cuantizado Q4 de aproximadamente 0.5–0.6B, por ejemplo Qwen2.5 0.5B o Qwen3 0.6B, verificando etiqueta/disponibilidad antes de descargar. En modelos con razonamiento configurable, desactivarlo para órdenes breves.
3. Evaluar después un 1–1.5B si queda margen de memoria y temperatura. No comenzar con 7B/8B.
4. Un modelo cargado, una solicitud de inferencia a la vez y contexto inicial de 1024–2048 tokens. Probar 2 y 4 hilos; más hilos no siempre dan mejor rendimiento sostenido.
5. Objetivo inicial de planificación: mantener aproximadamente 600–800 MiB de RAM disponible bajo carga, ajustándolo con mediciones reales. No confundir tamaño del archivo del modelo con RAM total de ejecución.
6. Medir carga en frío/caliente, primer token, tokens/s, latencia completa de una orden, RAM, swap y temperatura durante al menos 20–30 minutos. No hay benchmark realizado todavía.
7. Si Ollama no funciona o su coste es excesivo, probar `llama.cpp`/`llama-server` ARM64 con GGUF pequeño, incluso compilado para el entorno apropiado.
8. Inferencia en otro PC o API será opcional para tareas complejas. Definir proveedor, presupuesto y datos que pueden salir antes de activarla.

Las órdenes frecuentes («apaga sala», escenas, temporizadores) deben tener una ruta determinista local sin LLM. El modelo interpreta lenguaje flexible y propone llamadas estructuradas; un validador decide si son legales. No depender de que un modelo de 0.5B haga tool calling perfecto.

### Ruta rápida de atención

- Clasificar primero: orden local → ejecución directa; dato actual → API específica; pregunta general → respuesta breve o búsqueda acotada; conversación → modelo pequeño con contexto corto.
- Clima por API meteorológica y ciudad/coordenadas; música y TV por sus conectores, no mediante navegación web genérica. Definir proveedor de música y dispositivo de reproducción; no asumir que puede reproducir cualquier catálogo sin cuenta/licencia.
- Búsqueda web de una pasada como punto de partida: pocas fuentes, límites de tiempo y respuesta resumida; ampliar solo si el usuario lo pide o la respuesta necesita verificación adicional.
- Limitar salida y desactivar razonamiento extendido cuando el modelo lo permita. Esta configuración favorece latencia, pero no garantiza precisión ni velocidad suficientes en 4 GB.
- Mostrar «escuchando», «consultando» o «procesando» inmediatamente y transmitir texto/audio por fragmentos si la pila lo admite. Una confirmación visual rápida no equivale a haber completado la acción.
- Comparar local frente a API rápida opcional usando la misma batería de pruebas. Si el modelo local no da la experiencia buscada, proponer modo híbrido con permiso, coste y privacidad explícitos; no aumentar tamaño de modelo por defecto.
- Pruebas prioritarias: «pon música», «apaga la TV», «temporizador de cinco minutos», «recuérdame mañana…», pregunta de clima y conversación breve con una pregunta de seguimiento. Medir latencia de extremo a extremo, no solo tokens/s.

Ollama no navega por Internet ni controla aparatos por sí solo: esas capacidades las implementa el backend mediante herramientas.

## 6. Dispositivos del hogar: inventario antes de integrar

| Equipo | Ruta posible | Pendiente / límite |
| --- | --- | --- |
| Cintas LED | WLED, ESPHome, MQTT, API local o fabricante | Marca, controlador y protocolo |
| Luces normales | Bombilla inteligente, relé o enchufe certificado | No se vuelven controlables solo por tener Wi-Fi en el teléfono; instalación eléctrica por personal competente |
| TV | Android TV, webOS, Tizen, Cast o API LAN | Modelo, emparejamiento y encendido desde espera; IR requeriría un emisor externo compatible |
| Xbox | Integración disponible según modelo, cuenta y modo de energía | Probar estado, encendido/apagado y controles permitidos; no prometer control total |
| Lavadora | API del fabricante o integración documentada | Algunas exigen habilitar inicio remoto físicamente; nunca simular seguridad con un enchufe |
| Aspiradora | API local, integración de fabricante o nube | Modelo, región, token y funciones compatibles; no flashear sin autorización |
| Bluetooth/BLE | Puente Android o gateway compatible | Emparejamiento y protocolo de cada aparato; el móvil no reemplaza cualquier hub |
| Zigbee / Thread / Z-Wave | Coordinador/hub específico | El teléfono por sí solo no incluye estos radios; Matter tampoco garantiza compatibilidad universal |

Para cada dispositivo registrar marca/modelo, app actual, red/protocolo, capacidades, dependencia de nube, autenticación y acciones de riesgo. Empezar con **una sola luz**. No escanear agresivamente toda la red ni operar aparatos durante la revisión.

## 7. Conversación, memoria, Internet y APIs

### Voz

- Etapa 1: conversación por texto y botón para hablar; voz en español con salida breve.
- Etapa 2: evaluar reconocimiento local ligero (Vosk español o Whisper tiny cuantizado), sin mantener dos modelos pesados simultáneamente. Medir precisión y tiempo real en este equipo.
- Etapa 3: palabra de activación «Doha» o «Doja», con detector compatible y licencia revisada. Puede requerir entrenamiento específico; no asumir que cualquier motor acepta la palabra sin más.
- TTS: Google TTS está deshabilitado actualmente. Elegir explícitamente entre reactivarlo con voz offline o un motor local compatible como Piper, según rendimiento/licencia.
- Implementar cancelación de eco o pausa de escucha durante TTS, interrupción del usuario, indicador visible de micrófono y mute accesible. No grabar ni guardar audio continuo por defecto.
- La cámara se conserva para usos futuros explícitos; no se activa vigilancia o reconocimiento visual permanente.

### Memoria

- SQLite para preferencias, recuerdos confirmados, tareas, dispositivos y gastos; búsqueda de texto antes de añadir embeddings.
- Guardar información estable con consentimiento, no todo el audio ni secretos en el historial. Permitir «qué recuerdas», editar, olvidar y exportar.
- Ventana corta: conservar inicialmente unos 4–6 turnos recientes por conversación, recortados por presupuesto de tokens; ajustar según rendimiento. Un resumen mínimo es opcional, no una tarea costosa obligatoria después de cada mensaje.
- **Caducidad confirmada: cada conversación se elimina tras una hora sin actividad en esa conversación.** Un mensaje en otro tema no renueva su plazo. Registrar `conversation_id`, tema, `last_activity_at` y `expires_at`; excluir contenido caducado de toda recuperación inmediatamente, con limpieza al arrancar, periódica y antes de cada consulta. La caducidad continúa aunque el backend esté apagado.
- **Varias conversaciones simultáneas:** mantener temas separados y permitir retomar uno por nombre o referencia («lo de la TV», «volviendo al viaje»). Combinar selección explícita, tema y contexto reciente, sin enviar todas las conversaciones completas al modelo. Si hay ambigüedad, preguntar; si ya caducó, explicar que se borró y pedir el contexto nuevamente. No confundir separación por tema con identificar personas por voz: eso no está implementado ni autorizado implícitamente.
- Eliminar mensajes, resúmenes, índices y cachés asociados al caducar. No incluir conversaciones temporales en backups, telemetría ni logs de prompts/audio. Para simplificar la garantía, evaluar un almacén efímero en RAM con TTL (se pierde al reiniciar) y dejar SQLite persistente para recordatorios/preferencias. Si se exige sobrevivir a reinicios, diseñar almacenamiento cifrado separado y política de expiración antes de persistir.
- La microSD puede alojar datos no sensibles o archivos persistentes adecuados y copias cifradas, pero no es obligatoria para estas charlas pequeñas. No almacenar conversaciones en texto plano allí por defecto. La zram y swap son memoria virtual, no un lugar donde guardar conversaciones: incluso datos en RAM pueden terminar en swap, por lo que el borrado lógico no equivale a borrado forense. Cualquier garantía de borrado seguro requiere revisar también swap, cifrado y almacenamiento flash.
- No guardar indefinidamente charlas casuales por defecto. Persistir por separado recordatorios y preferencias que el usuario pida recordar.
- Alarmas, recordatorios y temporizadores con zona horaria, política para eventos perdidos y comportamiento tras reinicio; ejecución determinista, independiente de que el LLM esté cargado.
- Temporizadores con reloj monotónico mientras el proceso está activo y fecha de vencimiento persistida para recuperación; alarmas por fecha/hora local con tratamiento de cambios de zona y reloj. Confirmar ambigüedades («a las ocho», «mañana») antes de programar.
- Sonido/vibración, aviso en pantalla y acciones de detener/posponer/cancelar/listar. Evaluar AlarmManager y servicio Android para avisos fiables frente a suspensión o caída del backend, con prevención de duplicados entre Android y Debian. No prometer que suenen con el teléfono apagado.
- Copias cifradas fuera del teléfono; microSD como copia adicional, no única.

### Internet y APIs

- Herramientas separadas para búsqueda, lectura de páginas y conectores API con límites de tiempo/tamaño, cuota, caché y registro sin secretos.
- Tratar páginas, mensajes de Slack y respuestas externas como datos no confiables: sus instrucciones no pueden cambiar permisos ni activar herramientas.
- Bloquear acceso web arbitrario a endpoints internos/metadata; acceso a dispositivos LAN solo por conectores explícitos. No ofrecer proxy abierto.
- Slack: app/bot con scopes mínimos y autenticación elegida tras definir si debe recibir eventos o solo publicar.
- «dot»: **servicio por identificar**; solicitar nombre completo o URL antes de diseñar integración. No asumir que significa Notion, Echo Dot u otro producto.
- Gastos: importe, moneda, categoría, fecha, origen e identificador idempotente; confirmar datos ambiguos y evitar duplicados al reintentar. Registrar no significa autorizar pagos.
- Confirmación para pagos, compras, borrados y operaciones físicas de riesgo. Estado real del dispositivo antes de anunciar éxito.

## 8. Interfaz de reloj y carita

Reutilizar el APK/dashboard si se recupera el código fuente; no asumir que está en este PC, cuyo directorio estaba vacío al comenzar.

- Reposo: reloj digital grande siempre visible, brillo bajo, fondo negro y estado mínimo de red/micrófono; no entrar en bloqueo automático.
- Activación: cara blanca sobre negro, expresiones `:)`, `:o`, `-_-`, `:(` o equivalentes gráficos.
- Estados explícitos: reposo, escucha, procesamiento, hablando, confirmación, error y micrófono silenciado.
- Movimiento de boca sencillo sincronizado con reproducción/amplitud de audio; no necesita un modelo de visión o animación generativa.
- Backend → interfaz mediante WebSocket/eventos; reconexión y aviso cuando Debian no responda.
- Acceso de mantenimiento a Ajustes, Wi-Fi, Bluetooth, teclado y cámara. Reloj funcional aunque la IA no esté cargada.
- El panel del teléfono es LCD: un fondo negro no ahorra energía como en OLED. Reducir brillo y animación; modo nocturno tenue sin apagar la pantalla por defecto, conforme al requisito de reloj permanente.
- Mantener pantalla encendida desde la actividad visible (por ejemplo, `FLAG_KEEP_SCREEN_ON`) y revisar bloqueo/keyguard y retorno tras reinicio. No basta con un wake lock de CPU ni con activar «permanecer despierto al cargar». No quitar PIN/credenciales existentes sin autorización; si exigen primer desbloqueo, documentarlo y acordar solución.
- El reloj debe actualizarse localmente sin depender de IA, Internet o WebSocket. Probar estabilidad de pantalla y retorno reloj → cara → reloj, incluyendo errores del backend. Un fallo térmico grave o apagado de seguridad tiene prioridad sobre mantener el reloj visible.

## 9. Seguridad, estabilidad y uso permanente

- Android tiene un parche de 2021: riesgo relevante aunque no tenga cuentas. No publicar SSH, dashboard, Ollama ni APIs directamente en Internet. Conservar ADB por USB autorizado; revisar y desactivar ADB TCP si estuviera habilitado.
- Actualmente SSH y dashboard escuchan en todas las interfaces observadas. Restringir acceso según necesidad; IA/puente en loopback y autenticados. Para acceso remoto, VPN compatible, no abrir puertos del router.
- Mantener acceso SSH por clave y sin root; revisar usuarios/permisos sin copiar claves privadas al repositorio.
- Secretos fuera del webroot y del Markdown, con permisos restrictivos. Autenticación, límites de solicitud y auditoría en endpoints de control; sin shell root expuesto al modelo.
- El Nginx actual sirve `/srv/dashboard`, donde también hay scripts y logs. Revisar qué archivos pueden descargarse y separar web pública de código/logs/secretos antes de ampliar el servicio.
- Excluir de ahorro de batería solo las apps necesarias; mantener un wake lock de CPU si las pruebas muestran suspensión del servicio, sin obligar a mantener siempre la pantalla encendida.
- Auditar consumo por proceso antes de deshabilitar más Android. Conservar WebView para la interfaz y no quitar servicios compartidos por intuición.
- Revisar fecha y sincronización: hay logs antiguos con fechas de 1971; TLS, recordatorios y APIs necesitan hora correcta. La telemetría actual sí indica fecha de 2026.
- **Requisito confirmado: el teléfono estará conectado al cargador permanentemente, 24/7. La protección de batería es obligatoria antes de dejarlo funcionando sin supervisión.** Estar conectado no debe significar cargar continuamente al 100 %.
- Batería antigua: ventilación, inspección física y control térmico. Si se hincha o calienta anormalmente, dejar de usarla y revisarla. Evaluar límites de carga solo si el hardware los admite y se prueban de forma reversible; no manipular nodos de carga a ciegas.
- Medir temperatura sostenida y aplicar reducción de carga; nunca desactivar protecciones térmicas. No realizar swapoff ni pruebas de estrés sin margen y recuperación.
- Actualizar Debian y componentes por etapas con respaldo. Mantener logs acotados, límites de almacenamiento y restauración probada.

### Protección de batería para alimentación permanente — requisito de diseño

Implementar defensa en capas; el software del propio teléfono no es una protección suficiente si Android se bloquea o falla un sensor.

1. **Revisión física previa:** comprobar estado y antigüedad de batería, conector, cable y cargador. Sustituir una batería deteriorada mediante servicio técnico; no perforar, modificar ni puentear la batería o su circuito de protección. No asumir que el equipo admite funcionar sin batería.
2. **Control interno, si es compatible:** investigar las interfaces reales de carga del Moto/Magisk y evaluar un controlador mantenido, por ejemplo ACC, solo después de verificar compatibilidad. Buscar una ventana de carga moderada (por ejemplo, iniciar cerca del 50 % y detener cerca del 75–80 %), configurable y con histéresis. Son valores iniciales orientativos, no una capacidad comprobada del teléfono. Verificar con corriente/estado real que la carga se detiene; no confundir una lectura simulada de batería con control eléctrico.
3. **Monitor interno:** vigilar temperatura de batería, nivel, corriente cuando sea accesible y estado del cargador. Ante anomalías, detener inferencia, reducir carga de trabajo y suspender carga si existe un mecanismo validado; enviar alerta. Definir umbrales y recuperación con las especificaciones de la batería y pruebas, sin desactivar protecciones originales. Una alarma crítica debe requerir revisión, no reconexión cíclica automática.
4. **Corte externo independiente — opción preferida para mayor seguridad:** cargador/enchufe o controlador certificado con corte térmico local y sensor externo colocado de forma segura cerca de la zona de batería, sin abrir ni presionar la celda. Debe poder cortar la alimentación del cargador aunque el teléfono, Wi-Fi o backend fallen. Un enchufe inteligente que solo recibe órdenes desde Doha no constituye una protección independiente. La temperatura superficial es complementaria: no sustituye al sensor interno ni detecta todas las fallas.
5. **Límites del corte:** desconectar el cargador no desconecta la batería interna ni garantiza detener una falla térmica ya iniciada. No presentar esta medida como protección absoluta; ante hinchazón, olor extraño o calor anormal, dejar de usar el equipo y solicitar revisión técnica.
6. **Instalación permanente:** superficie estable y no inflamable, ventilada, sin sol directo, sin textiles que cubran el equipo y con acceso fácil para desconectarlo. No montar relés caseros en la red eléctrica; usar equipos certificados y personal competente cuando corresponda.
7. **Pruebas y aceptación:** registrar temperaturas en reposo e inferencia, comprobar límite de carga y recuperación tras reinicio, simular fallos del monitor mediante pruebas no peligrosas y verificar el corte externo sin calentar deliberadamente la batería. Documentar mantenimiento, alertas y procedimiento de desconexión. Registrar como pendiente cualquier capa que no pueda validarse.

Decisión pendiente: qué control interno admite realmente este Moto y qué solución externa certificada se usará. No se ha instalado ni probado protección adicional durante esta revisión.

Lectura posterior sin modificar carga: batería al 69 %, 29.7 °C, salud `Good`; BMS reporta 1552 ciclos, `charge_full=3274000` µAh frente a diseño `4093000` µAh y `soh=86`. Son estimaciones del controlador, no diagnóstico físico ni porcentajes necesariamente equivalentes. Priorizar inspección/revisión de batería por desgaste. Existen nodos `input_suspend` y `charge_control_limit`; este último reporta máximo 8, **no es un porcentaje de carga**. No escribir valores hasta verificar semántica, compatibilidad y reversión. No se ha aplicado ningún límite todavía.

Avance: usuario confirma inspección visual sin hinchazón ni calentamiento anormal, tapa abierta por él para inspección. Instalado monitor de solo lectura cada minuto con logs acotados y hook Magisk, probado sin duplicación de procesos. Detalles y limitaciones en `BATERIA.md`. **No hay corte de carga adicional activo todavía**; el modo demo Motorola consultado no se desactiva simplemente escribiendo cero y no se modificó. Arranque tras reinicio pendiente de comprobar.

## 10. Fases y criterios de aceptación

### Fase 0 — Respaldo y recuperación del servidor visible

- [x] Copiar al PC scripts activos, configuración SSH/Nginx, dashboard y APK; SHA-256 verificado y permisos preservados en el archivo tar.
- [ ] Localizar fuente del APK y datos necesarios; hacer copia consistente de datos antes de tocar servicios.
- [x] Usuario informa que ya reactivó el launcher Doha.
- [x] Verificar por ADB HOME actual: Doha confirmado; sin reconfigurarlo.
- [ ] Verificar visualmente conexión al backend y retorno con botón Inicio.
- [ ] Comprobar cámara, micrófono, altavoz, teclado, Wi-Fi y Bluetooth antes y después.
- [ ] Reinicio controlado autorizado: Debian, SSH, web y launcher vuelven sin abrir Termux manualmente; documentar si requiere primer desbloqueo.
- [ ] Recuperación y reversión del launcher comprobadas; no borrar backups ni instalaciones antiguas.

### Fase 1 — Base estable y segura

- [ ] Protección para cargador permanente: revisión física, monitor térmico, límites de carga compatibles y evaluación de corte externo independiente; probar y documentar antes de operación sin supervisión.
- [ ] Reserva DHCP, reloj correcto, copia externa y reducción de exposición del dashboard.
- [ ] Supervisor único, health checks, usuarios sin colisiones, logs rotados y tokens del puente.
- [ ] 24–48 horas de estabilidad, incluyendo pantalla apagada, pérdida de Wi-Fi y reconexión.

### Fase 2 — IA y primera conversación por voz (prioridad actual)

- [x] Alternativa ARM64 funcionando sin root y modelo pequeño medido: llama.cpp + Qwen2.5 0.5B Q4_K_M, aproximadamente 15 tokens/s con 4 hilos en prueba corta. Detalles en `VOZ.md`; Ollama no instalado.
- [x] Reconocimiento local Vosk español instalado y carga/decodificación de silencio probada sin root; precisión con voz real pendiente.
- [x] Comandos Termux:API instalados y motor TTS Android reactivado/detectado; prueba audible pendiente de confirmación.
- [x] Usuario confirmó salida TTS audible tras ajustar volumen; captura breve autorizada, Vosk reconoció pregunta en español (2.56 s; pico aproximado 143 MiB).
- [ ] Ensayo explícito de micrófono → transcripción → respuesta → voz, con medición de latencia completa, antes de construir chat de texto.
- [ ] Resolver calidad del LLM: Qwen2.5 0.5B y candidato Qwen3 0.6B respondieron con errores factuales en prueba básica. La cadena técnica avanzó, pero no hay asistente general validado; ver `VOZ.md` antes de ampliar automatización.
- [x] Probar candidato local más capaz por elección del usuario: LFM2.5 1.2B QAD Q4_0, unos 714 MiB de RSS máximo muestreado, 9.14 tokens/s con 4 hilos, sin aumento de swap de disco en los ensayos cortos.
- [x] Ejecutar primera captura completa con LFM: pregunta reconocida, respuesta generada y enviada a TTS; micrófono detenido y temporales eliminados. Guardas durante inferencia activas. Resultados y límites en `PRUEBA-MODELO-LOCAL.md`.
- [ ] Confirmar audibilidad de esa respuesta, reducir demora actual (18 s después de grabar hasta llamar TTS) y validar calidad/memoria/temperatura de forma sostenida antes de ampliar funciones.
- [x] Usuario confirmó audible la respuesta sobre el Sol. Probada caché de instrucciones con servidor local temporal: pregunta diferente empezó a generar texto en unos 1.4 s en ensayo sintético; nuevo turno de voz autorizado tardó 13 s después de grabar hasta invocar TTS, con 8 s adicionales de preparación inicial. Servidor cerrado al acabar.
- [ ] Resolver errores factuales persistentes y latencia total antes de operación cotidiana: respuesta sobre estrellas fue incorrecta aun con el candidato nuevo; ver `PRUEBA-VOZ-OPTIMIZADA.md`. Valorar herramientas de fuentes verificables manteniendo inferencia local; todavía no hay búsqueda web implementada.
- [ ] Chat español rápido y sin razonamiento extendido, memoria casual corta y datos persistentes SQLite separados.
- [ ] Clima por API, búsqueda web acotada y alarmas/recordatorios/temporizadores persistentes con confirmación, cancelación y pruebas tras reinicio.
- [ ] Medir latencia/RAM/calor; elegir modo local puro o híbrido a partir de resultados, no promesas.
- [ ] Chat de texto remoto opcional en puerto 10000, solo LAN/VPN y autenticado; no prioritario frente a voz.

### Fase 3 — Automatización real

- [ ] Definir host compatible para Home Assistant: el kernel Android actual carece de PID namespaces, veth y otras funciones necesarias para Docker estándar; no se instalará una versión antigua no soportada ni se cambiará kernel sin acordarlo.
- [ ] Inventario de marcas/modelos y primera luz controlada por texto y reglas deterministas.
- [ ] Verificar estado posterior, fallos, permisos e idempotencia; después TV y aspiradora.
- [ ] Xbox y lavadora solo tras validar compatibilidad y límites de seguridad.
- [ ] Slack y registro de gastos; identificar «dot» antes de integrarlo.

### Fase 4 — Voz e interfaz

- [ ] Pulsar para hablar, reconocimiento español y TTS; luego detector Doha/Doja.
- [x] App piloto Android separada instalada: captura PCM en tiempo real, mute visible, límite de 90 s, reloj/cara básica sin sustituir HOME. Usuario confirmó temporizador de 10 s por botón y alarma audible; sin LLM cargado.
- [ ] Validar activación por Doja: los primeros ensayos no la validaron. Hay calibración comparativa con palabras parecidas; ver `ACTIVACION-DOJA.md`. No confundir éxito del botón con éxito del wake word ni habilitar escucha permanente todavía.
- [x] Sustituir el objetivo por Andrómeda y validar un piloto real: detección por nombre, orden, temporizador de 10 s y alarma audible confirmados sin botón. Detector/modelo español compartido, pico de backend ~151 MiB; no es todavía validación 24/7.
- [ ] Evaluar falsos positivos/omisiones reales, reanudación tras alarma y funcionamiento sostenido antes de habilitar arranque o escucha permanente.
- [x] Reforzar cancelación y probar en Android con almacenamiento aislado: duplicados, cancelación simple/múltiple, entregas tardías y recuperación de vencimiento; 22 pruebas Python y pruebas sintéticas ampliadas también pasaron, sin micrófono ni sonido en esos ensayos.
- [ ] Validar cancelación con voz real mediante pasos separados. El usuario confirmó que pidió el timer de diez segundos registrado; no hubo petición de cancelación identificada y no quedaron timers activos. No marcar como cancelación exitosa por el simple hecho de que haya vencido.
- [x] Instalar opción explícita de piloto de cinco minutos (90 s por defecto en coordinador), sin inicio automático de micrófono; 23 pruebas Python e instrumentación Android aislada pasaron. Duración real de cinco minutos pendiente. Un ensayo posterior reconoció `cancel_timer` con varios timers, pero no confirmó borrado; tras reconexión USB no había timers ni servicios activos. Detalles en `ACTIVACION-ANDROMEDA.md`.
- [x] Integrar LFM2.5 local en ruta de voz opcional (`start-andromeda-ai.sh`): conversación de un turno e interpretación flexible de acciones, JSON validado y confirmación humana antes de mutar timers. 30 pruebas Python, cinco casos de texto con modelo real y tres rutas de voz sintética con modelo real pasaron. Ver `IA-ANDROMEDA.md`; no sustituir esos ensayos por una afirmación de éxito con voz humana.
- [ ] Validar voz humana en nuevo modo IA y calidad/latencia sostenida; permanecen pendientes memoria conversacional, Internet, integraciones domésticas y protección de carga.
- [ ] Reloj digital permanente sin bloqueo automático y cara al conversar; probar retorno al reloj, mute visible, reconexión y salida de mantenimiento.
- [ ] Probar conversación mientras hay automatizaciones, sin audio duplicado ni bloqueos por memoria.

### Fase 5 — Operación doméstica permanente

- [ ] Recordatorios persistentes, rutinas, copias automáticas y alertas de espacio/temperatura.
- [ ] Probar reinicio, caída del backend, pérdida de Internet y restauración de copia.
- [ ] Una semana de prueba estable antes de confiar tareas importantes al asistente.

## 11. Información pendiente del usuario

1. Marcas y modelos de luces/controladores, TV, Xbox, lavadora y aspiradora; aplicaciones que usan hoy.
2. Qué servicio es «dot» y qué flujo concreto se quiere en Slack/gastos.
3. Preferencia entre todo local y permitir API/otro PC para conversación compleja; presupuesto y privacidad.
4. Palabra preferida: Doha o Doja; voz y ubicación física del equipo.
5. Ubicación del código fuente del APK y respaldos anteriores, si existen.
6. Confirmar siguientes cambios y momento apropiado para una prueba de reinicio.

## 12. Referencias consultadas

- Ollama Linux y distribución ARM64: https://docs.ollama.com/linux
- Métodos oficiales actuales de Home Assistant: https://www.home-assistant.io/installation/

Estas referencias no certifican compatibilidad con este kernel Android: instalación, rendimiento, voz y control de dispositivos siguen pendientes de pruebas.
