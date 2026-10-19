# 0.9 · petición de acción y cierre, consulta y continuidad

**Actualización posterior:** cierre humano reconfirmado («el timer cerró correctamente»); no inferir sonido de esa frase. Inspección posterior encontró el registro consumido y recibos intactos. A petición del usuario se instaló 0.10 con «Andrómeda, silencio»: `SILENCIO-ANDROMEDA.md`. Ese cambio corta salida actual, no cancela el timer ni reinicia su petición; sin nueva ventana humana preparada. Las versiones/hashes 0.9 que siguen son evidencia histórica.

## Decisión y alcance

El usuario pide que una acción clara y de bajo riesgo no necesite la frase «confirmo temporizador»: ejecutar, comunicar el resultado real y cerrar. Preguntas, chistes, historias y consultas deben poder continuar. Una consulta también puede usar herramientas; **el efecto de la herramienta, no la presencia de herramientas ni la forma interrogativa/imperativa, determina la política**. Además pide empezar a planear micrófono activo y modelo cargado durante periodos largos: `ENSAYO-PROLONGADO-ANDROMEDA.md`; no se habilitan todavía escucha permanente, arranque automático ni sesiones largas.

**Implementación 0.9.0-action-close-trial / código 11**, con control sin acciones/protocolo 6 conservado y ensayo de timer **protocolo 8**, incompatible con la confirmación antigua/protocolo 7. Solo `create_timer` está implementado. Luces, alarmas, recordatorios, consulta/cancelación de timers por voz e Internet siguen pendientes. No se reutiliza el ejecutor histórico ni se amplía el catálogo.

## Flujo implementado

- **«Pon un temporizador de dos minutos»**: LFM elige la llamada nativa, copia duración completa, código valida toda la petición actual y Android vuelve a validar contra la transcripción. Esa petición es la autorización. Un permiso de un solo uso, ID de timer/propuesta/nonce y sesión/charla/turno/secuencia; no hay oferta hablada ni turno extra de «sí»/«confirmo».
- Tras acuse Android real: **«Temporizador de 120 segundos activado.»** Sin «¿Necesitas algo más?», sin despedida adicional obligatoria y sin reactivar escucha de seguimiento. Después de TTS terminado (o de fallo verificado), llega cierre autenticado `action_result` y se conserva la charla como cerrada/retomable, no borrada. El registro Android del timer es independiente.
- Un resultado fallido/duplicado/incierto también termina ese intento tras explicar fielmente su estado, sin afirmar éxito ni reintentar automáticamente. Un acuse perdido se informa como incierto y se indica revisar la pantalla antes de repetir. Cerrar no cancela una obligación aceptada.
- **Duración ausente o ambigüedad:** no mutar ni cerrar automáticamente; aclaración administrativa y captura de seguimiento. En este prototipo se pide **repetir la petición completa con la duración**; aún no se juntan silenciosamente un «dos minutos» aislado y una orden anterior para autorizarlos. Negaciones, citas, relatos, fechas y duración inventada/parcial no pasan la validación.
- **Consulta/chiste/historia:** respuesta LFM y oferta de ayuda/continuidad como antes. «Sí» para continuar/retomar no autoriza un timer, ni una petición rescatada del historial vuelve a ejecutarse.
- **Petición mezclada con pregunta u otra orden:** la gramática conservadora actual la rechaza y pide una petición separada; mantiene la charla abierta. No recorta la frase para ejecutar una parte y perder la pregunta. Ejecutar + responder en una sola frase mixta queda para una ampliación posterior con contrato propio; no se presenta como resuelto.
- Acciones delicadas futuras requieren su propia política y confirmación, no heredan permiso automático por ser imperativas. `interaction_policy.py` contiene metadatos de efecto/riesgo y una política registrada únicamente para timer; sus casos read/high-risk son reglas probadas, **no herramientas habilitadas**.

**Cierre lógico y servicio aún no están desacoplados:** en estas ventanas manuales cerrar termina captura/modelo. No queda esperando el nombre después del cierre; hace falta nueva preparación manual. El cambio futuro para volver a espera con modelo residente está planificado por separado, sin confundirlo con Silenciar/Detener.

## Garantías mecánicas y límites

- Nueva negociación TLS/protocolo 8 y `timer_policy=request_authorized_close`; app antigua/protocolo 7 no puede recibir silenciosamente acciones sin su confirmación. El modo sin acciones rechaza eventos/razón de cierre de acciones.
- Native tool: `create_timer(duration_text: string)`. Comparar todos los componentes cantidad/unidad en el mismo orden contra la petición completa, permitiendo escritura de número con letras/dígitos, pero no traducción de unidades, sumas equivalentes distintas ni omitir componentes. Segundos finales calculados desde el literal humano y validados de nuevo por Android. No shell/root/model-generated code.
- Permiso ligado al **mismo turno** (`request_turn_id`, `request_sequence`), no a una confirmación posterior. Cada petición autoriza como máximo una propuesta/timer, aun si se cambia el ID en un reintento. Antes de mutar, verificar revocación y vencimiento. Ejecución como máximo 4 s desde validación y nunca después de fin estimado +26500 ms ni de fin de sesión −5000 ms; rechazar si no quedan al menos 500 ms. El plazo de respuesta de 30 s no se reinicia entre fases. Fallo de cálculo no da permiso para actuar tarde.
- Almacén/AlarmManager/recibos idempotentes 0.8 sin borrar/reprogramar timers existentes; `confirmed_action` es un nombre histórico del formato persistido y ahora indica autorización validada de petición, no que se haya pronunciado otra confirmación. Recibo y timer durables sobreviven a apagar conversación/modelo; fuerza de parada, teléfono apagado y puntualidad OEM no quedan garantizados.
- TTS de éxito solo tras resultado real y texto contrastado por Android con ese recibo. `action_result` solo se acepta en el canal de timer y para la respuesta correlacionada. No guarda un éxito administrativo como supuesto recuerdo generado de LFM; conserva el turno humano y el contexto según los límites RAM anteriores.
- Gramática actual restringida: no se entienden todas las formas naturales de pedir un timer y ASR puede perder información. No confundir validación del texto con prueba de que se oyó toda intención humana. Modelo/ASR pequeños siguen siendo imperfectos; una frase genérica de éxito del modelo nunca ejecuta ni confirma una petición de timer completa.
- Una transcripción de más de 250 unidades UTF-16 se rechaza **entera**; no se trunca antes de validar porque podría eliminar una negación o matiz final. Sin invocar modelo/ejecutor con un fragmento artificial.

## Validación y evidencia

Pruebas locales: **241 tests Python y 449 aserciones JVM** pasan; build correcto. Incluyen reglas read/write/riesgo, acción → resultado → cierre, consulta/chiste/historia → continuidad, falta de datos/mix → aclaración sin cierre/acción (también si LFM responde en prosa e ignora la parte de acción), permiso del mismo turno, unicidad incluso cambiando ID, fechas/plazos/negaciones/citas, rechazo de transcripción larga sin recortarla, resultado ajeno, TTS fallido y cierre solo tras resultado. Se conservan también regresiones históricas del núcleo de confirmación, no expuestas por protocolo 8.

Instalación respaldada, contrato real Android y preferencias aisladas pasan: tipos, sesiones, petición «sí» no válida, acuse falso, plazo ampliado y doble entrega rechazados; permiso de petición completa ejecuta una vez. Sin micrófono, TTS ni sonido en esa prueba de contrato. Fixtures TTS españolas offline preparadas sin altavoz.

**Cuatro sintéticos completos en Moto pasan, Vosk/LFM/síntesis offline a archivo, sin AudioRecord ni altavoz:**

| Caso | Resultado observado |
|---|---|
| `timer_create` | «pon un temporizador de diez minutos» → Android guarda/programa 600 s → «Temporizador de 600 segundos activado.» → `action_result` y cierre. **Un solo turno, cero reactivaciones de seguimiento**, sin oferta ni confirmación extra. Duplicado rechazado y registro aislado presente tras apagado del modelo. |
| `timer_missing` | El mismo ASR «pone un temporizador» pide duración/petición completa y sigue abierto; no crea ni afirma éxito. Cierra solo al recibir «cierra la conversación». |
| `timer_yes` | Pregunta de nombre → sí para continuar → conversación sigue, sin timer. Cierre explícito posterior. No es validación semántica: LFM respondió erróneamente que no tenía nombre; ese error casual se conserva como evidencia, no se sustituye por una respuesta ideal. |
| `timer_query` | «cuéntame un chiste corto» obtiene texto LFM y oferta de ayuda, sigue abierto y no ejecuta. Cierre explícito posterior; no certifica calidad humorística. |

Todos: una activación por nombre, leases/contexto cerrado retomable sin respuestas administrativas inventadas, memoria nueva aislada, limpieza comprobada **antes** de informar éxito, timers/recibos reales intactos. UI también pasa: 8 estados y 36 dibujos/1200 ms, sin modelo/micrófono/audio. Regresión `entry_new` del modo sin acciones/protocolo 6 pasa de nuevo, con oferta/no/despedida y cero acciones.

Primera serie de cuatro casos: timer directo, fin estimado ASR → primer PCM sintetizado **6140 ms**; fin de señal pública WAV → PCM 5638 ms. No es inicio acústico ni certificación de voz útil. Sondeo de caída de inferencia 32–35 ms en esa serie; recursos: MemAvailable mínimo **1374400 KiB**, RSS máximo del servidor **744852 KiB**, batería máxima **23,6 °C**. No sumar RSS/PSS, atribuir ahorro de RAM ni extrapolar a catálogo máximo/uso continuo.

Repetición final tras endurecer la prueba de fuente (misma versión/protocolo que el cliente correcto, entrada de voz válida, pero origen micrófono frente al backend sintético): contrato Android, creación directa y UI pasan de nuevo con el APK final. Un turno, cero seguimientos, timer aislado persistente después del cierre, limpieza verificada; PCM sintetizado a 7832 ms estimados/7330 ms desde fin de WAV, caída de puerto a 39 ms. MemAvailable mínimo 1401660 KiB, RSS máximo 743452 KiB, batería máxima 24,0 °C en esa repetición. No captura ni altavoz.

**Falta ensayo humano:** captura real, utilidad audible y que el timer venza y suene después de cerrar la charla/modelo. Los timers aislados se cancelaron antes de vencer deliberadamente; no hubo sonido ni se creó un timer del usuario. **Ningún ensayo largo quedó iniciado.**

No atribuir a esta revisión aprobación 24/7, sonido real tras cierre ni inicio audible útil <=30 s. No se activó captura humana ni se preparó automáticamente una ventana con micrófono durante la instalación.

## Uso manual y reversión

```sh
adb -s ZY227BCQ85 shell "su -c '/data/local/doha-voice/start-andromeda-conversation.sh pilot 300 timer'"
```

Preparar no abre el micro. Después, doble toque → Micrófono → Crear timer por voz → duración. Para sintéticos sin captura/reproducción: `android-voice/test-timer-conversation.ps1 -Mode timer_create|timer_missing|timer_yes|timer_query`; preferencias aisladas, IDs comprobados contra timers reales y limpieza antes de informar éxito. No usar esos sintéticos como aceptación humana.

Backup APK 0.8: `artifacts/android-voice/pre-action-close-v09-installed-v08.apk`, SHA-256 `d7599917be07690ad6f1e992b1327256ac487e38b993c3c36603e629115644d9`. Backend/script previos: `/data/local/doha-voice/backups/pre-action-close-v09-20261018`. Se actualizó con `install -r`, sin desinstalar/borrar datos ni cambiar HOME/SELinux/SSH/web/swap/carga. La RAM de conversaciones puede perderse al reemplazar el proceso; no se exporta a disco. Historial y evidencia 0.8: `TEMPORIZADOR-VOZ-ANDROMEDA.md`.

## Huellas y estado final

SHA-256 de artefacto/fuentes locales contrastados con lo instalado:

| Archivo | SHA-256 |
|---|---|
| APK código 11 | `3a7a05b2b8f7cb8c2d65c9b6be1b1352af230c6528d35930d6feef81b6eef6d9` |
| `conversation_voice.py` | `97333fa8f840cc857c626c0a77591819ea3657c6d5503965d59e62b9e641c905` |
| `timer_proposals.py` | `189c885c42b57caabf51ef91f92a50358fa7ce3811a029b001ce9592d68180d0` |
| `timer_trial_model.py` | `8a9110959a59002aabd74a30744f23404885b158dddd56f3c4c36cbc4bf03447` |
| `timer_trial.py` | `98a8e152e91fd09a03acfcc8d30ccbde688335927158cae5019a882594a44100` |
| `interaction_policy.py` | `7f4e4757bff0fa58ea2619720e0eea3a4fb7af6a1d3bf028bc7517e34f07bc36` |

App Andrómeda en primer plano, UID 10238, código 11; sin VoiceService/RingService ni listeners 11435/11436. HOME Motorola, SELinux Enforcing, SSH 2222 y web 8080 intactos. `timers.xml` conserva el hash previo `3325d2a819fdd8062c2cdc48a09b995c9b012915bcdf88b1cf9742a7f057c793`. Muestra final MemAvailable 1624744 KiB y batería 24,1 °C, no prueba sostenida. No se dejó captura/modelo activos, no se preparó otra ventana humana y no se hicieron commits/pushes/merges por este agente.

## Primera ventana humana 0.9 · creación real verificada, sonido pendiente de feedback

Petición posterior del usuario: «ok, iniciemos la prueba». Se prepara **una sola ventana `pilot 90 timer`**, fuente micrófono/protocolo 8, sin reinstalar ni instrumentar/reiniciar la app. Preparación no abre captura: el usuario debe elegir Micrófono → Crear timer por voz → 90 segundos. Espera de cliente <=300 s y supervisor <=660 s intactos; no otra ventana automática ni prueba prolongada.

Propuesta de ensayo al usuario: «Andrómeda, pon un temporizador de dos minutos»; comprobar anuncio y cierre sin confirmación extra, luego sonido tras cerrar/modelo apagado. **Todavía no se afirma creación, duración reconocida, cierre ni sonido:** requieren acuse real y feedback. Si el resultado no queda claro, no repetir para evitar duplicados; revisar primero el registro real.

Antes de preparar: app en primer plano/código 11, sin servicios de voz/alarma ni puertos 11435/11436; SSH/web escuchando. MemAvailable 1654408 KiB y batería 23,5 °C; hash de timers igual al anterior. Solo contadores/estado/tiempos en `artifacts/android-voice/human-v09-timer-first-window.txt`, sin audio/transcripción humana. No arranque permanente, carga/HOME/swap modificados ni creación manual de un timer para sustituir el resultado de voz.

Preparación verificada por evento `conversation_ready`: TLS/protocolo 8, fuente `microphone`, `actions_enabled=true`, `microphone_opened=false`. El backend queda disponible para un cliente manual, no se abrió captura desde ADB. Creación/cierre/sonido y aceptación humana siguen pendientes; no repetir ni prolongar automáticamente la ventana.

**Ventana terminada después de un turno real:** selección `new`, cero turnos restaurados, entrada LFM 301 tokens y cero recortes. Android devuelve `CREATED`/una creación reconocida; callback TTS a **6510 ms desde fin estimado ASR**, acuse de respuesta terminada y una intervención humana retenida sin respuesta administrativa inventada. Backend/supervisor finalizan normalmente, sin otra ventana. Preparación fría previa 22,966 s, antes de abrir captura: no confundirla con respuesta desde fin de voz ni con prueba de residencia.

Verificación posterior **solo lectura**, sin instrumentar/reinstalar/parar la app ni tocar alarmas: hay **un timer autorizado de 120 s**, todavía pendiente (75 s restantes en esa muestra). Sin VoiceService/RingService ni listeners 11435/11436; app Andrómeda en primer plano, SSH/web escuchando. No se creó manualmente, repitió ni canceló el timer. Recursos de ventana: MemAvailable mínimo 1332604 KiB, RSS máximo de inferencia 743592 KiB, batería máxima 23,7 °C; muestra posterior 1552856 KiB/23,5 °C.

**Feedback humano posterior: «se cerró correctamente».** Se acepta el cierre como confirmado por el usuario, sin pedir otra repetición idéntica. No confirma por sí solo contenido/duración del anuncio audible ni sonido final. El acuse TTS y los 6,510 s estimados no certifican audio acústico útil <=30 s. La desaparición futura del registro puede demostrar consumo por Android, no que el usuario oyera el tono. Esperar feedback del sonido; no iniciar ni repetir otra sesión o sustituir el timer por una prueba manual.

El usuario además aclara que la operación final será **100 % por voz**, sin activar el timer por botón. Se registra como requisito, no como característica ya instalada: el menú actual autoriza únicamente pilotos, y la preparación manual no debe ser necesaria por interacción en el producto final. El futuro cierre lógico vuelve a espera del nombre con el modelo residente; el apagado duro sigue siendo una operación distinta. Plan en `ENSAYO-PROLONGADO-ANDROMEDA.md`; no se habilitó escucha indefinida por esta aclaración.
