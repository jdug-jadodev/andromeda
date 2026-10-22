# Andrómeda: arquitectura actual, límites y alternativas

Documento de explicación, no un despliegue ni una decisión de migración.
Referencia actual: APK40/65módulos del manifiesto de
`artifacts/android-voice/calendar-date-live-v1/installed.json`;
esta explicación partió de APK37, cuyo estado sigue descrito como antecedente.
La última prueba humana acepta alta/listado/borrado en sus casos, incluido alta a8h;
fechas puntuales/días fallaban. Corrección40 instalada tras permiso nuevo:
`artifacts/android-voice/calendar-date-candidate-v1/phone-release.json`.
Nativo20/20requeridos,20/21total (indirecto conocido abierto);59checks Android
SQLite/wire aislados pasan. No altera arquitectura/modelo/7tools/auto/límites/Worker.
Mismafirma/UID/datos/agenda/sistema/8/15 y el aviso pendiente aún registrado en
AlarmManager; no se exige vacío ni se repite aviso8h. El humano acepta después
fecha/hora faltante en su caso40, sin lectura nueva de recibos/entregas ni validar
todos los formatos/días relativos. Sigue AL-01 (alarmas), sin más cambios sobre la sesión;
dueño `andromeda-resident.qnchgT`/inode124693, activación20:58:28UTC; lectura20:59:30UTC
running/65hashes/1recorderAndroid. Preparación54,137s bajo55s, margen estrecho no
ampliado.1gapPCM recuperado/32descartados, no cierre ni nueva ejecución;
alarmas/voz/cuentos mantienen sus pendientes;
prioridades y aceptación parcial en `MEJORAS-Y-PRUEBAS-ANDROMEDA.md`.

Reporte posterior: el usuario acepta alta/listado en sus pruebas y señala la falta
de borrado por voz de recordatorios. La versión39 añadió `delete_reminder` como
séptima herramienta con selección/pregunta/confirmación local Android y recibos
tipados; agenda y candidatos no pasan al backend. Política coordinada
`confirmed_reminder_delete_v1`. Selector real14/14requeridos (14/15total, indirecto
anterior aún abierto) y93checks Android/SQLite aislados pasan; el humano aceptó
después borrado en su prueba, no se leyó un recibo nuevo desde el chat. Sesión
observada19:53:39UTC, sin cambiar diseño/modelo/proveedor. Último volumen8/15
conservado, no100% histórico: no restaurar niveles ni snapshots anteriores al nuevo
uso humano. Los estados38 siguientes son antecedentes.

**Decisión posterior:** el usuario conserva la arquitectura y el modelo local.
Se retoman mejoras incrementales de flexibilidad, una herramienta cada vez;
no migración ni servicios externos nuevos. Primero aclarar intención de recordatorio
ante texto dudoso, sin inventar autorización. APK38/política
`confirmed_reminder_intent_v1` ya instalada EXPERIMENTAL por petición posterior:
selector8/9 (un pedido indirecto aún se rechaza), sin aceptación humana de alta/entrega.
Prefijo compacto con un ejemplo público de selección, ningún humano ficticio llega
al ejecutor. Preparación pública55s tras agotar45s; respuesta humana60s y guardias
de ejecución intactas. Sesión nueva activa observada18:09:25UTC, sin más despliegues.

## 1. Un teléfono, varios componentes

Android y Debian no son dos máquinas remotas. Debian vive en el mismo Moto,
mediante chroot sobre el kernel Android. Sus procesos se comunican por loopback.
El modelo no tiene privilegios root ni acceso general a las funciones del móvil.

```text
Persona
  │ voz
  ▼
App Android: AudioRecord, indicador de micrófono, Detener
  │ PCM16/16kHz + tiempos, por TLS autenticado a127.0.0.1:11436
  ▼
Debian/Python: Vosk → texto reconocido → coordinador de conversación
  │ historial breve + instrucciones + catálogo de herramientas
  │ HTTP local a127.0.0.1:11435
  ▼
llama.cpp / LFM2.5-1.2B-Instruct-QAD-Q4_0
  ├─ texto de respuesta
  └─ propuesta de llamada a una herramienta
       │
       ▼
Python: validaciones, formularios y permisos de un turno
  │ eventos tipados por el mismo canal TLS
  ▼
Android: valida otra vez → ejecuta con APIs del sistema → devuelve recibo
  │
  └─ síntesis de voz Android y acuses de inicio/fin/interrupción
```

El servicio de inferencia llama.cpp está compilado para Android y es lanzado por
el trabajador nativo; Python en Debian lo consulta. No es un modelo ejecutándose
en este ordenador ni una consulta oculta a ChatGPT. Ambos servicios escuchan solo
en el teléfono, no en puertos abiertos al router.

### Quién hace cada cosa

| Pieza | Responsabilidad | Código principal |
|---|---|---|
| App Android | Captura y reproducción, interfaz, notificación, permisos y ejecución real | `android-voice/src/local/doja/voice/VoiceService.java`, `VoiceConnection.java` |
| Vosk | Reconocimiento principal de voz y palabra de activación en Debian; también hay un detector local Android para interrupciones | `backend/spanish_wake.py`, `backend/conversation_voice.py`, `SilenceListener.java` |
| Coordinador Python | Estados/turnos/contexto, preguntas de formularios, llamadas locales y transporte | `backend/resident_voice.py`, `backend/conversation_voice.py`, `backend/assistant_tools_trial.py` |
| Modelo LFM | Generar texto y seleccionar llamadas nativas; no ejecutar Android | `backend/assistant_tools_model.py`, `scripts/resident-model-worker.sh` |
| Agenda Android | SQLite, programación con AlarmManager, entrega y recibos | `AgendaStore.java`, `AgendaPermit.java`, `AgendaDeliveryService.java` |
| Servicio web externo | Recibir una consulta web explícita y devolver respuesta/fuentes | `backend/internet_search.py`, contrato Worker descrito en `BUSQUEDA-WEB-CLOUDFLARE.md` |

La agenda guardada pertenece a Android: una vez programada correctamente no
necesita que el LFM siga razonando hasta la hora del aviso. Eso es diseño del
ejecutor, no prueba de que una alarma del ensayo haya llegado a guardarse/sonar.
Listar recordatorios tiene una ruta especial: Android forma y lee el contenido;
al backend le devuelve metadatos/conteos, no el contenido de toda la agenda.

## 2. Qué significa «IA local» aquí

LFM sí es una red neuronal de lenguaje, con aproximadamente1.200millones de
parámetros y pesos cuantizados a4bits en esta variante. llama.cpp realiza la
inferencia en CPU. No es una tabla escrita a mano que contenga las respuestas.

Pero no tiene la capacidad, el contexto o el margen de cálculo de un modelo
remoto grande. La selección respondió a memoria/velocidad del Moto, no a una
equivalencia demostrada con ChatGPT. La cuantización es un compromiso; no explica
por sí sola todos los fallos. Cambiar a un modelo mayor en el mismo teléfono puede
empeorar memoria, temperatura y latencia.

Configuración instalada relevante:

- Contexto total2048tokens e entrada acotada1536, incluyendo instrucciones,
  definiciones de herramientas e historial. Tokens no equivalen a palabras.
- Respuesta ordinaria/nativa96tokens; un relato puede quedar sin final.
- Inferencia con `stream:false`, temperatura0 y razonamiento explícito desactivado
  (`--reasoning off`, presupuesto0; plantilla `enable_thinking:false`). Esto no
  significa ausencia de toda capacidad inferencial, ni que activar una bandera
  vaya a convertirlo en un planificador fiable.
- Historial acotado en RAM, caducidad tras una hora sin actividad humana, sin
  entrenamiento permanente con lo que diga el usuario.
- En este hardware el prefijo frío de herramientas ha costado decenas de segundos.
  El presupuesto de respuesta y las guardias de recursos siguen vigentes.

El modelo ve principalmente **el texto que Vosk reconoció**, no la voz original.
Si una palabra se reconoce mal, necesita aclaración, no una corrección silenciosa
que convierta otra frase en permiso para ejecutar algo.

## 3. Cuánto es IA y cuánto es código manual

En la versión39 se ofrecen siete herramientas:

```text
create_timer, create_alarm, create_reminder,
search_web, set_phone_volume, list_reminders, delete_reminder
```

La elección es nativa del modelo (`tool_choice:auto`), no un menú que el usuario
tenga que pronunciar. Sin embargo, las herramientas actuales de alarma y
recordatorio son **de cero argumentos**. El modelo elige el nombre y después
Python/Android vuelven a extraer contenido, fecha y hora del texto reconocido
mediante gramáticas, expresiones regulares y formularios programados.

Por tanto, el sistema actual es un **híbrido de LLM y autómatas bastante rígidos**.
La percepción del usuario tiene fundamento: exponer una herramienta no bastó
porque después de la elección hay mucho reconocimiento literal manual. Por
ejemplo, `backend/agenda_dialogue.py:opening` acepta determinadas formas de abrir
un recordatorio. Una variante o un error ASR puede terminar en el rechazo genérico
de `backend/assistant_tools_trial.py`, aunque el modelo proponga la herramienta.

Además, `backend/local_arithmetic.py` resuelve algunas cuentas directamente con un
evaluador decimal limitado, sin inferencia del LFM. Es un componente determinista
real, añadido por rapidez/corrección; no demuestra razonamiento general del modelo.

### Código que siempre hará falta

Ningún modelo, incluido ChatGPT, crea por sí solo una alarma Android ni conoce
automáticamente el protocolo de una lámpara. Hace falta implementar el adaptador
que llama a AlarmManager/Bluetooth/API del TV. Lo que **no debería** escribirse
como una lista interminable de frases es la comprensión del lenguaje cotidiano.

Una IA más competente puede interpretar intención y campos y proponer el plan;
el código debe verificar tipos, límites, destino, permisos y resultado. Son tareas
distintas: quitar los ejecutores/guardias no vuelve más inteligente al modelo.

## 4. Cómo encajan los fallos observados

- **Recordatorio y ASR:** hay un cambio de palabra reportado y una barrera literal;
  corresponde pedir aclaración sin autorizar nada, no culpar al modo de hablar.
- **Alarmas:** la creación real sigue sin aceptación humana. Un test que produjo
  `create_alarm` no recorre necesariamente autorización, transporte, escritura,
  AlarmManager, voz y entrega. Hay que identificar la fase, no prometer otra vez
  que está arreglada por pasar un selector.
- **Presupuestos temporales:** el modelo de herramientas dispone de55s de cómputo
  y60s para inicio de respuesta, pero propuestas directas de agenda tienen guardas
  de26,5s desde fin del habla (`assistant_tools_trial.py`, `AgendaPermit.java`).
  Es un punto a revisar como contrato temporal; no es una causa demostrada de la
  última alarma ni permiso para ampliar/reducir límites sin medirlos.
- **Voz cortada:** la salida ordinaria tiene un máximo de20s; web/lista tienen60s.
  También existe detección de interrupción y errores TTS. El informe humano no
  basta para elegir una de esas causas. Volver a escuchar no prueba que muriera
  todo el motor. No hacer pasar voz incompleta por pregunta oída/acción confirmada.
- **Emoji:** `VoiceService` entrega el mensaje al TTS sin una capa de presentación
  hablada específica que resuelva este problema. Otro modelo también puede
  generar emoji: cambiar el cerebro no corrige por sí solo la pronunciación.
- **Cuentos:** negativas de capacidad son respuestas incorrectas del modelo,
  condicionadas por el contexto de herramientas. La segunda prueba candidata
  también alcanzó el límite de salida. Una historia no necesita herramienta de
  ejecución, pero sí una ruta de generación y un presupuesto adecuados.
- **Web mejor valorada:** esa ruta obtiene `respuesta` de un servicio externo y
  la lee fielmente. Su éxito no demuestra que el LFM haya generado por sí solo
  una respuesta equivalente. Conservar la aceptación provisional y sus límites.
- **Cierre de ventana:** `MainActivity.onStop()` detiene voz; cierre del canal
  puede terminar el modelo. BG-01 registra la separación pendiente.

## 5. Alternativa explicada — NO elegida ni implementada

```text
Voz local → reconocimiento/clarificación
         → coordinador con proveedor de IA intercambiable
             ├─ conversación/creatividad: texto, sin permisos de ejecución
             └─ propuesta de herramienta con campos estructurados
                    → validación de esquema/política/destino
                    → aclaración o confirmación humana cuando corresponda
                    → ejecutor Android de alcance cerrado
                    → recibo y comprobación del resultado
         → representación hablada adecuada, separada de la UI
```

El cambio no es «confiar ciegamente en la IA». Es permitirle comprender y proponer
sin obligarla a recrear una oración canónica, manteniendo autorización humana
vigente, validación Android independiente, una mutación por turno y cero retries
de resultado incierto. Una propuesta ambigua/no reconocida nunca se ejecuta.
La confirmación debe estar ligada a los datos que el usuario realmente recibió,
al recurso y al turno actual; web, historial, modelo y TTS no crean autorización.

Para comparar cerebros, usar los mismos casos de voz y contratos de herramientas:
LFM en el Moto, otro servidor local más potente, o un proveedor remoto autorizado.
Cambiar un solo componente y medir. No confundir reparar ASR/TTS/transporte con
elegir un LLM, ni seguir agregando funciones domésticas sobre altas de agenda rotas.

## 6. Usar ChatGPT: API, cuenta y OAuth no son lo mismo

Documentación oficial consultada el21deoctubrede2026:

1. **API estándar con clave:** facturación por uso separada del producto ChatGPT.
   Pagar Pro no aporta por sí solo saldo API ni vuelve ilimitadas sus llamadas.
2. **Sign in with ChatGPT / uso del plan:** existe una vía oficial para solicitudes
   elegibles desde apps open-source/locales, con OAuth/PKCE y consentimiento. El
   quickstart menciona usuarios elegibles Plus/Pro; no se puede prometer que una
   cuenta Free o este proyecto privado concreto tenga derecho a ese flujo.
   Clientes comerciales/privados tienen condiciones distintas; hay que verificar
   elegibilidad y límites, no copiar un token de otro programa.
3. **OpenCode/Codex:** admiten integración con proveedores y métodos OAuth/clave
   según cliente/plan. Ese entorno coordina herramientas y permisos del agente;
   la suscripción no es la que ejecuta el código. No se ha inspeccionado aquí la
   facturación ni la autenticación de la cuenta del usuario.

La vía oficial de uso del plan permite texto y function/custom tools bajo su
contrato. Actualmente requiere Responses con `store:false`, `stream:true` y
contexto explícito; no es sustituir la URL del cliente llama.cpp de chat. Hay
campos y herramientas no soportados. Audio/vídeo, upload/transcripción de audio
no están soportados por ese flujo: una opción coherente es conservar voz/ASR/TTS
locales y enviar solo texto autorizado a un modelo remoto.

Un error de plan/cupo puede llegar incluso después de comenzar el stream; no
ejecutar llamadas parciales ni cambiar silenciosamente a API de pago. Ese diseño
necesita gestión propia de cuenta/scopes/modelos disponibles, completitud, límites
y datos. El login no da acceso al historial de ChatGPT ni entrena el modelo con
Andrómeda. No prometer capacidad/calidad o acceso concreto antes de probarlo.

### Privacidad y alcance

Usar otro cerebro remoto puede mejorar comprensión/razonamiento, pero no arregla
por sí solo ASR, TTS, regex o permisos. Implica Internet, límites y salida de los
textos que se autoricen. La política actual solo permite consultas web explícitas
fuera del teléfono: no hay consentimiento nuevo para enviar conversaciones/audio,
ni se iniciaron OAuth, cuentas, claves, compras, cambios de proveedor o instalaciones.
Nunca pedir contraseñas/cookies/tokens en el chat ni reutilizar las credenciales
del asistente de desarrollo como credenciales de Andrómeda.

### Fuentes oficiales

- [Facturación ChatGPT/API](https://help.openai.com/en/articles/9039756-billing-settings-in-chatgpt-vs-platform)
- [Sign in with ChatGPT: quickstart y elegibilidad](https://developers.openai.com/siwc/quickstart)
- [Uso del plan en aplicaciones open-source](https://developers.openai.com/siwc/token-sharing-open-source)
- [Modelos e inferencia](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Limitaciones de la preview](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [OpenCode V2: proveedores](https://opencode.ai/v2/docs/providers) y [métodos de conexión](https://opencode.ai/v2/docs/cli/providers)
