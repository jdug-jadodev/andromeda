# Búsqueda web del Worker → Andrómeda

## Estado y alcance

Integración en el repositorio, **no desplegada en el Moto ni probada contra tu
Worker real**. No se ha recibido URL/token ni se han hecho consultas externas.
TV pausada. No se modifica ni se crea un backend Cloudflare/Tavily.

La aplicación instalada era código 22. **No actualizar únicamente Python:** la
nueva síntesis necesita también el validador Android del candidato código 23.
La instalación coordinada se acuerda aparte; reemplazar la app puede perder las
conversaciones/diagnósticos que estén solo en RAM. No se cambian ASR, TTS,
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
   **llama.cpp local**, sin historia, token, URLs ni herramientas. Pide una
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

- Éxito: `estado` string `ok`, `success`, `exito`, `éxito` o `completado`.
  Otros estados se consideran fallo del servicio; no se hablan mensajes crudos.
- `pregunta` opcional; si está presente debe ser idéntica a la enviada.
- `respuesta` y `contexto_para_ia`: strings, al menos uno no vacío. No se
  serializan objetos arbitrarios como si fueran instrucciones de contexto.
- `fuentes`: lista de URLs string o de objetos `url`, título opcional
  `title`/`titulo`, extracto opcional `excerpt`/`contenido`/`content`/`extracto`.
  No se inventan extractos a partir de `respuesta` para hacerlos pasar por citas.
- Sin fuentes válidas, no se anuncia una respuesta verificada. Campos adicionales
  se ignoran; claves JSON duplicadas, controles y delimitadores de roles se rechazan.

Los valores exactos de `estado` y las formas de `fuentes`/`contexto_para_ia` no
venían especificados por completo. Si tu contrato real usa otra forma, adaptar
**este cliente**, no tu Worker. Basta un JSON de ejemplo público/sin secretos
para contrastarlo; no hace falta compartir token, conversaciones ni consultas
privadas. Hasta ese contraste no se declara compatibilidad real validada.

## Dónde poner el `.env` en el Moto

**Tras desplegar el candidato completo**, el arranque existente seguirá pasando
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

## Pruebas y despliegue pendiente

```sh
python -m unittest discover -s tests
```

Incluye HTTP/TLS simulados, contrato POST, errores, procesos reales con DNS
simulado bloqueado/cancelación, aislamiento de inferencia, fuente distinta de
cita, presupuestos, lectura segura del `.env`, relay del actor y compatibilidad
Python → Java real con fixtures públicos. Sin servicio real, inferencia LFM,
voz ni dispositivo. Las comprobaciones POSIX de permisos requieren Linux; no
deben confundirse con una validación real del filesystem Android en Windows.

Para un despliegue posterior coordinado:

- Nuevos módulos `internet_search.py`, `internet_answers.py`, `web_search_env.py`,
  `with_web_search_env.py`, `check_web_search_config.py` junto al backend.
- Actualizar `assistant_tools_model.py`, `web_answers.py`, el script
  `start-andromeda-conversation.sh` y el APK candidato 23 con `WebAnswerPolicy`.
- Mantener los demás módulos/dependencias actuales y los permisos del backend.
  No copiar todo el repositorio encima ni instalar paquetes nuevos.
- Configurar el archivo privado, contrastar contrato y hacer una sola consulta
  pública real acordada, después validar selección nativa LFM y respuesta/fuentes.
  Voz/latencia audible y aceptación humana requieren una ventana aparte.
