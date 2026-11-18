# Plan de expansión de Andrómeda — 2026-11-11

Alcance: **TV confiable, música (R2/YouTube/barra), Xbox, lavadora e identidad por voz/rostro**.
No sustituye al plan de memoria/IA: lo complementa. Reglas: cero regresiones, local-first,
validación real antes de prometer, credenciales fuera de Git y privacidad de audio/rostro.

> **Bloqueado por:** `PLAN-CONVERSACION-HERRAMIENTAS-2026-11.md` (**P0**). No empezar
> música, Xbox, lavadora ni identidad hasta cerrar la conversación de herramientas
> (recordatorios/alarmas y confirmación antes de guardar). TV puede tratarse aparte si
> se decide, pero no compite con el P0.

## 0. Resumen y prioridad

| # | Línea | Prioridad | Estado | Depende de |
|---|---|---|---|---|
| A | TV: encendido tras apagado prolongado | **P0** | Diagnóstico | Ajustes TV, Ethernet/CEC/enchufe |
| B | Música: R2, YouTube Premium, barra | **P1** | Diseño | Cuenta R2, modelo barra, probar cast |
| C | Xbox: encender, YouTube, juego | **P1** | Investigación | Modelo, Instant-on, cuenta/HA |
| D | Lavadora por Wi-Fi | **P2** | Investigación | Marca/modelo/app y remote start |
| E | Identidad: voz y rostro | **P2** | Diseño | Usuarios, consentimiento, umbrales |

Orden recomendado: **A → B → C → D/E en paralelo**. Cada fase se prueba en el Moto y se
despliega con bandera; si falla, se apaga y queda el comportamiento actual.

---

## A. TV: encender después de un día apagada

### Síntoma
- Encender/apagar y volver a encender al rato: **funciona** (WoL).
- Tras un día apagada: **no enciende** por voz/WoL.

### Hipótesis
La TV (LG UR7300PSA, webOS 4.1.0, Wi-Fi, IP `192.168.1.9`, MAC Wi-Fi
`8C:83:94:0D:F9:B4`) entra en un standby profundo y **apaga o suspende la Wi-Fi**; el
magic packet no llega. Al encenderla a mano, la Wi-Fi vuelve y el WoL funciona otra vez.

### Opciones, de la más fiable a la menos
1. **Ethernet** en la TV: el NIC cableado suele mantener WoL en standby profundo. Usar la
   MAC cableada histórica `F8:01:B4:11:D6:32`. Es la prueba que más puede resolverlo.
2. **Quick Start+ / Mobile TV On / Wake on Wi-Fi/LAN** en ajustes de la TV: mantiene la red
   viva en standby; sube el consumo (hoy Quick Start+ está OFF, standby ~0.5 W).
3. **CEC desde la Xbox**: ya verificado; encender la Xbox enciende la TV y pone `HDMI_2`.
   Sirve como respaldo, pero enciende también la consola.
4. **Enchufe inteligente** (Tapo/Kasa/Sonoff): corta y devuelve corriente; la TV arranca al
   recibir energía. No sirve para WoL mientras está cortada; hay que encender el enchufe.
5. **Emisor IR externo** (Broadlink/ESP32): el Moto no tiene IR; requiere hardware aparte.
6. **Mantener la Wi-Fi asociada** con sondeos periódicos: no fiable si la radio duerme;
   puede despertar la TV sin querer.

### Plan de diagnóstico y arreglo
1. Tras 24 h apagada, comprobar desde el Moto: `ping`/ARP a `.9`, `ss -lnt` al 3001,
   `tv_api.py /status`, y enviar WoL (broadcast y dirigido, puerto 9 y 7).
2. Probar **Ethernet** y repetir la prueba de 24 h; si funciona, dejar cableado y usar la
   MAC cableada.
3. Si no hay cable: activar **Quick Start+** y **Mobile TV On/Wake on Wi-Fi** y repetir.
4. Añadir a `tv_api.py` una **cadena de respaldo**: WoL Wi-Fi → WoL Ethernet → CEC/Xbox o
   enchufe → mensaje honesto. No bloquear 45 s si la TV no está alcanzable.
5. Registrar el resultado después de 1 h, 6 h y 24 h apagada.

### Criterios de aceptación
- «Enciende la tele» funciona tras 24 h apagada, o Andrómeda dice exactamente qué fallback
  usar y por qué.
- No se afirma «encendida» sin confirmar SSAP/estado real.

### Decisiones
- ¿Hay cable Ethernet disponible en el mueble?
- ¿Aceptas Quick Start+ (más standby/telemetría) si el cable no es viable?
- ¿La Xbox puede quedar como despertador CEC de la TV?
- ¿Hay enchufe inteligente compatible?

---

## B. Música: YouTube Premium, barra y R2

### Realidad
- YouTube/YouTube Music **no tienen API pública** para «pon esta canción y controla la
  reproducción» en TV/Xbox/Moto. Se puede **abrir** la app y, si el televisor/consola
  soporta **DIAL/Cast**, enviar el video desde el teléfono; no se garantiza el play exacto.
- La barra Samsung está **conectada al TV por óptico**; el Bluetooth existe, pero hay que
  cambiar la entrada de la barra y devolverla a óptico después.
- R2 es para **música propia o licenciada**, no para extraer audio de YouTube.

### Diseño
1. **Biblioteca R2 (recomendada para tu música):**
   - Bucket privado `andromeda-music`; credenciales en archivo privado del Moto/Debian.
   - Índice JSON (`title`, `artist`, `album`, `duration`, `cover`, `key`) y URLs firmadas o
     Worker; caché local para favoritos; borrado/rotación.
   - Reproductor en la app (ExoPlayer/Media3) y/o Debian; endpoints `/v1/music`
     (`list/search/play/pause/next/volume`), foco de audio y ducking con la voz.
2. **Música local del Moto:** `/sdcard/Music/Andromeda` como respaldo sin red.
3. **YouTube Premium en TV:** abrir la app YouTube por `tv_api.py`; probar **DIAL/Cast**
   desde el teléfono para lanzar un video concreto. Si el TV no acepta cast, abrir la
   búsqueda y que el usuario elija; no afirmar que sonó.
4. **Barra Samsung por Bluetooth:** con el TV apagado, emparejar el Moto por A2DP, cambiar
   la barra a Bluetooth y reproducir; al encender el TV, volver a óptico. Necesitamos modelo
   y si la barra cambia sola de entrada.
5. **Reproducir en TV vs barra vs Moto:** decidir destino por orden: barra Bluetooth (TV
   apagado), TV por cast/YouTube (TV encendido), Moto como último recurso.

### Criterios de aceptación
- Una canción de R2 o local suena y se pausa/cambia.
- «Pon música de X» abre YouTube/cast y, si no puede verificar, lo dice.
- La barra cambia a Bluetooth y vuelve a óptico sin dejar audio cruzado.

### Decisiones
- ¿Cuenta Cloudflare R2/bucket? ¿Qué música propia/licenciada hay?
- Modelo exacto de la barra Samsung y si acepta A2DP.
- ¿La TV acepta cast/DIAL de YouTube? ¿O solo abrir la app?
- ¿La música debe sonar en la barra, en la TV o en el Moto por defecto?

---

## C. Xbox: encender, abrir YouTube o un juego

### Realidad
- **Encendido:** WoL si Instant-on y «encendido por red» están activos; verificar presencia.
- **Apagado/estado:** no hay API local oficial fiable; Home Assistant tiene integración Xbox
  (cuenta Microsoft/Xbox Live) para estado, encendido/apagado y fuentes/apps. SmartGlass es
  no oficial.
- **Abrir YouTube o un juego:** no hay API pública oficial para lanzar apps/juegos desde la
  red local. HA/SmartGlass pueden listar apps y algunas fuentes; los juegos no están
  garantizados. El **CEC** solo enciende la TV/cambia entrada, no abre una app.
- **YouTube en Xbox:** si la app YouTube de Xbox acepta **DIAL/Cast**, el teléfono puede
  enviar un video concreto; si no, se abre la app y se busca.

### Plan por fases
1. **A — presencia y encendido:** WoL + verificación; «enciende la xbox», «¿está encendida?».
2. **B — estado/apagado:** HA Xbox o SmartGlass; si no es fiable, no implementar.
3. **C — apps/YouTube:** POC de `select_source`/SmartGlass para abrir YouTube; probar cast
   de un video; si funciona, «abre YouTube y pon X» con confirmación real.
4. **D — juegos:** probar lanzar un título instalado por HA/SmartGlass; solo si se verifica
   que abrió el juego. Si no, responder que no se puede y ofrecer encender/abrir YouTube.

### Decisiones
- Modelo exacto (¿Xbox Series X?), Instant-on, misma red y cuenta Microsoft.
- ¿Aceptas Home Assistant/Xbox Live para estado y apps? ¿O solo WoL?
- ¿Qué juegos/apps quieres poder abrir?
- ¿La TV tiene CEC activo para el encendido en cadena?

---

## D. Lavadora por Wi-Fi

### Realidad
- Depende de marca/modelo/app (LG ThinQ, Samsung SmartThings, etc.). El **inicio remoto**
  suele exigir activarlo físicamente, puerta cerrada y condiciones de seguridad; no se debe
  saltar. La lectura de estado/tiempo restante es más viable que el arranque.
- HA tiene integraciones para algunas marcas; otras usan nube del fabricante.

### Plan
1. Identificar marca/modelo, app y si permite «inicio remoto».
2. Fase 1 solo lectura: estado, programa, tiempo restante, aviso al terminar.
3. Fase 2 arranque/retardo solo si el fabricante lo soporta y la máquina está preparada;
   confirmación explícita y comprobaciones de seguridad.
4. Nunca simular seguridad con un enchufe ni saltar el bloqueo de puerta.

### Decisiones
- Marca/modelo exacto y app que usas.
- ¿Quieres solo estado/avisos o también iniciar?
- ¿Aceptas nube del fabricante o exiges integración local?

---

## E. Identidad: reconocimiento de voz y rostro

### Objetivo
Que Andrómeda sepa **quién habla** y **quién está delante**, solo para personalizar y
autorizar acciones; no para vigilancia ni para seguridad biométrica fuerte.

### Voz (speaker ID)
- Embeddings locales (ECAPA-TDNN/WeSpeaker/Resemblyzer) sobre el audio del micrófono;
  inscripción de 3-5 frases por persona; se guardan **embeddings**, no audio.
- Umbral de aceptación, margen entre hablantes, modo invitado si no hay coincidencia.
- Limitaciones reales: micrófono lejano, TV/música de fondo, niños, acentos, replay/spoofing.
  No usar como única barrera para cerraduras/pagos; para eso, PIN/confirmación en el móvil.
- Procesamiento en el Moto/Debian, sin nube; borrado y consentimiento por persona.

### Rostro (face ID)
- Detección + embeddings ligeros (MobileFaceNet/TFLite) o modelo equivalente; inscripción
  de varias poses; comparación local; luz/ángulo; **prueba de vida** para no aceptar fotos.
- Solo se activa cuando hay alguien delante y la cámara está permitida; indicador visible;
  no se guardan imágenes, solo embeddings.
- No es seguridad biometrica fuerte en un Moto G8; es personalización/experiencia.

### Fusión y permisos
- Voz + rostro + nombre de activación; conocido → saludo/personalización; desconocido →
  invitado; acciones sensibles → confirmación adicional.
- Registro local de decisiones (sin audio/rostro), umbrales ajustables y borrado.
- Nada de esto sustituye las guardas actuales de herramientas.

### Decisiones
- ¿Cuántas personas y con qué consentimiento?
- ¿Solo personalizar o también autorizar acciones? ¿Cuáles?
- ¿Cámara del Moto o cámara de la app? ¿Aceptas el consumo/latencia?
- ¿Qué hacemos con invitados y con voces desconocidas?

---

## F. Arquitectura, contrato y pruebas

- **Dónde corre:** modelos de identidad y música en el Moto/Debian; la app solo pide y
  muestra. Credenciales R2/Xbox/Lavadora en archivos privados, nunca en Git.
- **Contrato:** nuevas rutas versionadas (`/v1/music`, `/v1/xbox`, `/v1/appliance`,
  `/v1/identity`) y `limits.json`/vectores; subir versión solo cuando la app use los campos.
- **App:** secciones Música, Xbox, Casa/Electrodomésticos e Identidad; inscripción y borrado;
  estados claros y errores accionables.
- **Pruebas obligatorias:** TV tras 24 h; WoL/fallbacks; R2/local/cast/barra; Xbox
  encendido/app/YouTube/juego; lavadora estado/arranque; voz/rostro con known/unknown/
  spoofing; RAM/batería/latencia; no bloquear voz ni avisos.

## G. Fases de entrega

0. **Diagnóstico y datos:** TV (Ethernet/Quick Start+), barra, Xbox, lavadora, R2, usuarios.
1. **Rápido y seguro:** TV con fallback + música local/R2 + Xbox WoL/estado + lavadora
   solo lectura + inscripción de voz.
2. **Control real:** YouTube cast en TV/Xbox, barra Bluetooth, apps/juegos Xbox, arranque
   lavadora, identificación de voz activa, rostro.
3. **Fusión y pulido:** permisos, invitados, umbrales, pruebas prolongadas, despliegue.

## H. Decisiones pendientes (resumen)

1. TV: ¿Ethernet disponible? ¿Quick Start+? ¿CEC desde Xbox o enchufe?
2. Música: ¿R2/bucket? ¿Qué música? ¿Modelo de barra? ¿Cast de YouTube funciona?
3. Xbox: modelo, Instant-on, cuenta/HA, juegos/apps permitidos.
4. Lavadora: marca/modelo/app y si aceptas inicio remoto.
5. Identidad: personas/consentimiento, personalizar vs autorizar, cámara y retención.
