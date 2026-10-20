# Búsqueda web del Worker → Andrómeda

## Estado y alcance

**Estado posterior más reciente:**mitad corregida en la descripción nativa y
comprobada con LFM antes de desplegar solo assistant_tools_model.py.8 casos pasan,
mitad3082ms. APK26/proceso/RAM/configuración/otros53 módulos intactos,0 Worker,
audio/micrófono/acciones físicas, modelo apagado al cierre. No reinstalación ni
horario/residente; volumen real intacto, prueba por voz pendiente. Evidencia
`phone-volume-half-selection-fix-v1/final-state.json`; fallo de mitad debajo histórico.

**Estado posterior vigente:**APK26+cuatro módulos de volumen instalados con
consentimiento. Contrato Android con volumen ficticio pasa, selección LFM de0/100
pasa pero «a la mitad» no llama herramienta y queda rechazada. Sin volumen físico,
micrófono/audio/Worker,0 servicios/modelos al cierre. Se conservan lectura media,
archivo privado/credenciales y resto de módulos;0 nuevas consultas o cambios del
Worker. Evidencia `phone-volume-code26-install/final-state.json`. Los estados
«26 no instalado»/«instalado25» siguientes son históricos.

**Actualización posterior:** APK25/interfaz única/lectura media y tres módulos ya
instalados con consentimiento; recibos en `unified-voice-code25-install/`.
Comprobaciones Android sin audio pasan; cierre final no verificado por el agente,
usuario informa que detuvo todo. «25 no instalado» debajo es histórico.
Nuevo desarrollo local26 añade volumen multimedia mediante herramienta nativa y
protocolo10, conservando `worker_first_v2_medium`; requiere despliegue coordinado,
no está instalado y no cambia Worker/configuración/Tavily. Ninguna consulta nueva.
Prioridades de energía/casa/remoto en `FUNCIONES-Y-ENERGIA-ANDROMEDA.md`.

**Feedback humano posterior:** respuesta y continuación correctas según el
usuario, pero primera lectura excesiva (48,4s de reproducción/`DONE`) y «silencio»
no la detiene. Sesión cerrada, sin nueva consulta/captura del agente. Total Worker4
consultas:2 previas+2 explícitas humanas. No se recogieron textos/fuentes humanos.

**Candidato25 todavía NO instalado:** interfaz con una sola activación de voz y
sin selectores técnicos. Lectura media como `service_excerpt`: extracto literal
declarado≤360 unidades, sin otra generación; respuesta completa≤2000 conservada
en contrato/RAM para continuar después de `onDone`. No es cita de una página ni
una síntesis local. Validación Python/Java y negociación `worker_first_v2_medium`
exigen APK25+tres módulos coordinados. El teléfono sigue24. No escucha permanente,
autoinicio de motor o «silencio» reparado; el motor aún se prepara manualmente y
la sesión sigue acotada a5min tras activación humana. Mantener Detener visible.
Evidencia `artifacts/android-voice/unified-voice-code25-v1/`.

**Continuación corregida e instalada con autorización, sin reinstalar la app.**
Solo un módulo backend cambia; APK24/UID/proceso/datos conservados. La continuación
conversacional entra al LFM local sin herramientas antes del selector; las posibles
órdenes nuevas conservan selección nativa/validadores. No búsqueda implícita ni
ejecución por clasificador, y los datos web/derivados no entran al prompt nativo.

Prueba fresca con respuesta pública guardada: primera respuesta completa en3113ms,
continuación local en25545ms/308 tokens, cero nuevas consultas.351 tests Python
aprobados/1 omitido Windows; ambos recorridos pasan con LFM real. Sin micrófono,
audio o acuse TTS real; no prueba lectura audible completa ni latencia con HTTP.
Teléfono cerrado y datos/recibos/SSH/web conservados. Estado definitivo:
`artifacts/android-voice/worker-followup-route-fix-v1/final-state.json`.
Las notas de continuación bloqueada que siguen describen el fallo anterior.

**Actualización instalada: APK24 y siete módulos, con respaldo y datos/UID
conservados.** Android real sin audio pasa contrato de lectura Worker y SQLite
aislada. LFM real selecciona `search_web()` y entrega completa la respuesta pública
guardada (602 unidades/tres fuentes) en3159ms, **sin segundo resumen local**.
No incluye búsqueda nueva o reproducción; todavía no certifica lectura audible.

**Continuación integrada NO aprobada:** el modelo selecciona búsqueda otra vez y
la guardia rechaza por faltar petición explícita (`web_explicit_request_required`);
no consulta al Worker ni llega a la generación local de continuación. Se detiene
sin reintento automático. Próximo: corregir ese enrutamiento con autorización,
manteniendo selección/guardias para nuevas órdenes; no permitir búsquedas implícitas.
Teléfono quedó sin servicios/modelo/puertos de voz, datos/recibos/SSH/web intactos.
Cero audio/micrófono/consultas nuevas. Evidencia definitiva:
`artifacts/android-voice/worker-first-code24-install/final-state.json` y `findings.md`.

### Preparación histórica del candidato24 (posteriormente instalado arriba)

**Cambio solicitado: Worker primero, modelo local al continuar.** Candidato
APK24/`0.11.7-worker-first` preparado, **no instalado**. El Moto sigue en23 según
la última verificación. Sustituye la reformulación inicial lenta por lectura de
`respuesta` del Worker; no lee `contexto_para_ia` ni añade otra generación local.
Después de lectura completa, una continuación sí usa el LFM local aislado, sin
herramientas ni otra consulta salvo petición explícita nueva validada nativamente.

Espera máxima de inicio **60s absolutos**, cómputo≤55s/red≤55s compartidos, reserva
1500ms: no se suman plazos por fase. Lectura de la respuesta Worker hasta60s o fin
de sesión; resto de salidas/obligaciones conserva sus límites. Respuesta completa
normalizada hasta2000 unidades UTF-16, sin el recorte anterior a1000. Si excede el
límite, avisa en vez de presentar un fragmento como completo. No puede prometerse
que cualquier texto termine en60s; una lectura cortada no se confirma como completa.

Datos Worker y respuestas derivadas en RAM tipada/≤1h, nunca instrucciones al
selector de acciones; siguen presupuesto5700/24 y validación de petición actual.
Modo nuevo `service_answer`: «El servicio web responde: … Fuentes en Opciones.».
No es cita literal de páginas ni respuesta generada por LFM. Sin respuesta directa
se informa, no se rellena automáticamente a partir de contexto.

347 tests Python/1 omitido Windows,897 aserciones JVM y build APK24 pasan sin
teléfono/modelo/audio/proveedor real. Necesita actualización coordinada APK+siete
módulos, negociación `worker_first_v1`, respaldo/preflight y consentimiento de
pérdida RAM antes de reinstalar. No cambiar únicamente Python sobre cliente23.
Evidencia/manifiesto `artifacts/android-voice/worker-first-v1/`. El resto de esta
sección describe la integración23 instalada; la ruta antigua de resumen inicial
que aparece después es histórica, no el flujo del candidato24.

Integración **desplegada en el Moto con APK23/backend/launcher coordinados**,
con autorización de pérdida de memoria RAM, respaldo y sin borrar datos/UID.
**Aún no da una respuesta útil dentro del plazo en el control local:** el LFM
selecciona `search_web()` correctamente, pero la síntesis de la respuesta guardada
termina `deadline` a20,524s; síntesis aislada también a20,525s. Entrada728 tokens,
sin ampliar límites ni intentar otra consulta. Configuración/guardia JSON Android
y regresión SQLite aislada pasan; no es aceptación de voz/UI. Evidencia definitiva:
`artifacts/android-voice/cloudflare-code23-install/final-state.json` y `findings.md`.

Configuración privada
creada allí por el usuario, sin copiarla al PC. Tras un primer401, se compara
con consentimiento únicamente ACCESS_TOKEN del `.env` del proyecto: coincide
con el token del Moto. La URL oficial confirmada también coincide; no se cambia.

**Autenticación resuelta:** con autorización posterior explícita se sincroniza
solo ACCESS_TOKEN del Worker existente `andromeda-web` en la cuenta verificada
del subdominio `jhonnathan-dev`. No se modifica/sube código ni Tavily, ni se
crean servicios. Una sola consulta posterior devuelve **HTTP200 en3044ms y tres
fuentes**, con TLS/esquema/normalización aprobados. Total consultas reales: dos
(401 y200), sin retry automático. Se conserva fixture público privado en el
Moto para no repetir proveedor durante pruebas locales. El control LFM posterior
sí alcanzó generación, pero agotó su presupuesto sin respuesta útil. Falta resolver
esa latencia antes de aceptación de voz/UI. TV sigue pausada.

**Correspondencia de autenticación confirmada en código:**

```text
Moto: WEB_SEARCH_TOKEN = mismo valor que ACCESS_TOKEN del Worker
HTTP: Authorization: Bearer <ese valor>
Cloudflare: ACCESS_TOKEN (no renombrar para adaptarlo al cliente)
```

No es un token administrativo de Cloudflare ni `TAVILY_API_KEY`. La comparación
del Worker es exacta. El valor anterior de producción no se pudo ni intentó leer;
la sincronización posterior fue una sola PUT autorizada, confirmada por API y
por el200 del ensayo. Otros clientes con el secreto anterior podrían necesitar
actualizarlo. No volver a cambiar secretos bajo la autorización ya consumida.

La aplicación instalada ahora es código23. **No actualizar únicamente Python
sobre un cliente22:** la síntesis necesita también el validador Android23.
La instalación coordinada ya se autorizó/ejecutó una vez; no repetirla. Cualquier
nuevo reemplazo debe advertir pérdida de conversaciones/diagnósticos solo RAM.
No se cambian ASR, TTS,
temporizadores, agenda, control de dispositivos, modelo o límites de voz.

## Ruta de ejecución

1. En modo **Agenda y búsqueda web**, el LFM selecciona `search_web()` nativamente,
   sin argumentos. Se conserva ese nombre/esquema/catálogo para no romper el
   protocolo 9 ni la selección existente.
2. `assistant_tools_model.py` valida la petición explícita del turno actual y
   llama a la herramienta Python reutilizable
   `internet_search.buscar_en_internet(pregunta, deadline, cancelled)`.
3. El adaptador lee `WEB_SEARCH_URL` y `WEB_SEARCH_TOKEN` del entorno y envía solo
   `POST <base>/preguntar`, JSON `{"pregunta": "…", "tipo": "general"}`, con
   `Authorization: Bearer …`. No envía historial, datos de agenda, audio ni
   argumentos redactados por el modelo. **`TAVILY_API_KEY` no se lee ni se usa.**
4. `internet_answers.py` entrega `respuesta` y `contexto_para_ia` acotados a
   **llama.cpp local**, sin historia, token ni herramientas. Las URLs incluidas
   dentro del contexto son texto inerte: no se visitan ni autorizan acciones. Pide una
   respuesta española breve (≤64 tokens/32 palabras). Verifica el presupuesto
   real de entrada ≤1536 tokens y conserva el plazo original de cómputo de 22 s,
   con 1500 ms reservados para el actor. No amplía contextos/plazos.
5. El actor devuelve fuentes HTTPS (hasta tres) al panel Opciones y el contrato
   `service_summary`: «Según la síntesis web: … Fuentes en Opciones.». El cliente
   Android valida forma/idioma/longitudes y texto exacto. **No es una cita literal
   ni una verificación independiente de esas páginas.** La comprobación numérica
   local y los filtros de idioma/inyección tampoco prueban fidelidad semántica.

Para consultar, por ejemplo: **«busca en Internet qué es una nebulosa»** o
**«consulta en la web las noticias de astronomía de hoy»** (consulta ≤160 caracteres).
Se conserva el requisito de solicitud explícita antes de sacar datos del Moto.
No se activa búsqueda silenciosa sobre conversaciones privadas ni se infieren
consultas desde «eso»/«hazlo». Una necesidad de información externa se atiende por
esta ruta; una pregunta sin autorización explícita no se manda automáticamente.

El Worker puede devolver una explicación ya preparada: se trata como datos del
servicio externo, no como inferencia local ni como texto original de una página.
La formulación final de Andrómeda sí se genera localmente. La política interna de
Cloudflare/Tavily depende de tu backend, que esta integración no cambia.

## Contrato de respuesta admitido

Ejemplo público de fixture, **no respuesta capturada de tu servicio**:

```json
{
  "estado": "ok",
  "pregunta": "¿Qué es una nebulosa?",
  "respuesta": "Una nebulosa es una nube de gas y polvo en el espacio.",
  "contexto_para_ia": "Datos de la búsqueda sobre las nebulosas.",
  "fuentes": [
    {
      "url": "https://science.example.org/nebulosas",
      "titulo": "Nebulosas",
      "contenido": "Una nebulosa es una nube de gas y polvo en el espacio."
    }
  ]
}
```

- Éxito con explicación: `estado` string `ok` (`success`, `exito`, `éxito` o
  `completado` también se admiten para compatibilidad).
- El Worker real también devuelve `sin_respuesta_directa`, `respuesta: null` y
  fuentes: se conservan los fragmentos/contexto para formulación local. Si los
  fragmentos están vacíos, no se usa la pregunta/URL como evidencia inventada.
- `sin_resultados` con respuesta nula y fuentes vacías es ausencia de resultados,
  no fallo de proveedor: no se invoca el modelo para rellenarlos de memoria.
  Estados contradictorios o desconocidos se rechazan.
- `pregunta` opcional; si está presente debe ser idéntica a la enviada.
- `respuesta` y `contexto_para_ia`: strings, al menos uno no vacío salvo el caso
  explícito sin resultados; `respuesta` puede ser null en los dos estados
  anteriores. No se serializan objetos arbitrarios como instrucciones de contexto.
- `fuentes`: lista de URLs string o de objetos `url`, título opcional
  `title`/`titulo`, extracto opcional `fragmento` (Worker real),
  `excerpt`/`contenido`/`content`/`extracto`.
  No se inventan extractos a partir de `respuesta` para hacerlos pasar por citas.
- Sin fuentes válidas, no se anuncia una respuesta verificada. Campos adicionales
  se ignoran; claves JSON duplicadas, controles y delimitadores de roles se rechazan.

El código real de `andromeda-web/src/index.js` se contrastó con el cliente. Las
pruebas opcionales `tests/test_cloudflare_worker_contract.py` ejecutan ese Worker
local con upstream simulado, claves ficticias y sin entorno privado heredado.
Sus tres estados reales recorren normalización → adapter nativo → síntesis
simulada → actor. No se usa Wrangler, no se lee `.env`, no se consulta Tavily ni
se modifica ese proyecto. Esto prueba contrato de código, **no secretos ni
compatibilidad efectiva con el despliegue remoto**. La consulta200 posterior sí
comprueba autenticación/contrato HTTP, pero no la formulación LFM o voz.

El Worker permite hasta 18 s para su upstream; el cliente sigue limitado a 4 s
de red y al cómputo total original. No se amplían tiempos sin medir/acordar la
latencia real una vez resuelta la autenticación.

## Dónde poner el `.env` en el Moto

**Con el candidato completo ya instalado**, el arranque existente sigue pasando
por `env -i`. El wrapper `with_web_search_env.py` lee el archivo privado después,
dentro de Debian y solo para `doja_stream.py` en modo `tools`. No carga secretos
para el calentamiento ni para el proceso llama-server.

Ruta que debes editar:

| Desde dónde miras | Archivo |
| --- | --- |
| Dentro del Debian donde ejecutamos Python | `/var/lib/andromeda-web-search/.env` |
| Android/Termux con acceso root, fuera del chroot | `/data/local/linux/debian-final/var/lib/andromeda-web-search/.env` |

**No** es un `.env` en el HOME de Termux ni junto al APK. La copia de la raíz del
repositorio no se carga automáticamente. `.env.example` solo es una plantilla.

Como root **dentro de Debian**, crea el directorio si aún no existe:

```sh
install -d -m 700 -o 10218 -g 10218 /var/lib/andromeda-web-search
```

Abre `/var/lib/andromeda-web-search/.env` con tu editor disponible, sin pegar
secretos en comandos de consola ni en el chat. Escribe estas dos líneas con tus
valores reales (URL base **sin** `/preguntar`; token del Worker, no de Tavily):

```dotenv
WEB_SEARCH_URL=https://tu-worker.tu-subdominio.workers.dev
WEB_SEARCH_TOKEN=tu_token_privado_del_worker
```

Guarda y fija permisos, también dentro de Debian como root:

```sh
chown 10218:10218 /var/lib/andromeda-web-search/.env
chmod 600 /var/lib/andromeda-web-search/.env
```

El directorio debe quedar 0700 y el archivo 0600, ambos de UID/GID 10218 (usuario
del backend Debian, **no** UID 10238 de la app Android). No sobrescribir otro
archivo privado sin revisarlo. Si el editor conserva backups, deben quedar
privados también.

El lector admite líneas `CLAVE=valor`, comentarios en líneas aparte y comillas
simples/dobles opcionales; **no** `export`, expansión `$`, comandos, escapes ni
otras claves. No ejecuta el archivo con `source`/`.`. No añadas `TAVILY_API_KEY`:
pertenece exclusivamente a Cloudflare. Sin configuración o con permisos
incorrectos falla solo web; las otras herramientas siguen disponibles.

Puedes comprobar la configuración **sin red, modelo, micrófono o reinicio**,
como root dentro de Debian y después de desplegar los módulos:

```sh
/usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin /opt/doha-voice/bin/python /opt/doha-voice/pilot/check_web_search_config.py
```

Debe mostrar solamente:

```json
{"web_search_configuration": "ready", "network_requests": 0}
```

Esto **no** prueba autenticación ni respuesta real del Worker. No uses `cat`,
`printenv`, `env`, `set -x` o `curl -v` para compartir diagnósticos del token.
Los valores se leen al iniciar el backend de herramientas; editar el `.env` no
reinicia nada. Se aplican en el próximo arranque manual acordado. No reiniciar
una sesión activa sin avisar de la pérdida de memoria RAM.

Si ejecutas directamente el módulo Python en otra máquina, puedes proporcionar
las dos variables en su entorno (sin instalar python-dotenv). El adaptador las
lee en cada llamada; no busca archivos por su cuenta. La carga del `.env` es
responsabilidad del wrapper de arranque, no del modelo.

## Seguridad y errores

- Solo HTTPS con certificados/hostname verificados; sin proxy del entorno,
  redirecciones, sustitución por Bing u otro proveedor, ni reintentos automáticos.
- Proceso Python fijo (sin shell) con pipes privados, entorno mínimo y stderr
  descartado. Token fuera de argv. Plazo de red máximo 4 s, incluyendo DNS;
  cancelación mata y recoge ese hijo, no otros procesos. Respuesta HTTP ≤64 KiB.
- Errores de conexión/TLS/HTTP, 401/403, 429, timeout, JSON inválido/vacío y fallo
  del servicio producen códigos fijos sin URL, consulta, cuerpo ni credenciales.
  La voz no afirma éxito. No se pide al modelo interpretar excepciones.
- Contenido web/contexto no es permiso de acción. La síntesis no dispone de
  herramientas; las llamadas estructuradas y confirmaciones de acciones se
  rechazan. Filtros de inyección son defensa adicional, no garantía semántica.
- No se guardan consultas/respuestas/conversaciones en archivos ni hashes.
  Fuentes y conversación siguen en RAM con sus límites/expiración existentes.
  El código no controla los logs/retención de tu Worker o Tavily.

## Pruebas y despliegue

```sh
python -m unittest discover -s tests
```

Incluye HTTP/TLS simulados, contrato POST, errores, procesos reales con DNS
simulado bloqueado/cancelación, aislamiento de inferencia, fuente distinta de
cita, presupuestos, lectura segura del `.env`, relay del actor y compatibilidad
Python → Java real con fixtures públicos. Sin servicio real, inferencia LFM,
voz ni dispositivo. Las comprobaciones POSIX de permisos requieren Linux; no
deben confundirse con una validación real del filesystem Android en Windows.

La instalación ya realizada comprendió estos destinos; no es una orden de repetirla:

- Nuevos módulos `internet_search.py`, `internet_answers.py`, `web_search_env.py`,
  `with_web_search_env.py`, `check_web_search_config.py` junto al backend.
- Actualizar `assistant_tools_model.py`, `web_answers.py`, el script
  `start-andromeda-conversation.sh` y el APK candidato 23 con `WebAnswerPolicy`.
- Mantener los demás módulos/dependencias actuales y los permisos del backend.
  No copiar todo el repositorio encima ni instalar paquetes nuevos.
- Configuración privada/contrato/consulta pública200 ya comprobados en la fase
  anterior. La selección nativa LFM posterior pasa, pero la síntesis agota el
  plazo aun reutilizando la respuesta guardada. No otra consulta automática.
  Próximo trabajo: medir/reducir el coste de síntesis sin ampliar límites; voz,
  fuentes visibles y latencia audible útil siguen pendientes y se acuerdan aparte.
