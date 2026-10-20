# Andrómeda: volumen, suspensión, modelo residente y casa

Actualizado: 20 de octubre de 2026. Orden solicitado por el usuario al retomar.

**Código iniciado, primer bloque local listo:**agenda guiada Python/Android,
protocolo11 y confirmación→otra herramienta con recibos. Base de modelo continuo
sin vencimiento/monitor numérico+watchdog codificada, NO micrófono continuo aún.
396 testsPython pasan/2 skips, suiteJava/95 nuevasaserciones y compilaciónAndroid
pasan. No nuevoAPK/dispositivo/modelo/micrófono usado oestado instalado cambiado.
`artifacts/android-voice/guided-agenda-v1/findings.md` detalla alcance/pendientes:
cicloAndroid continuo, alarmas semanales/borrado/posponer, silencio real y más
formatos/correcciones todavía faltan. No afirmar producto completo por estos tests.

**Último requisito explícito:**modelo siempre cargado y micrófono en escucha
local continua con monitoreo, además de agenda conversacional. Objetivo nuevo,
NO instalado:modalidad continua con indicador/Detener, activación por voz sin
botón, diálogo dinámico y vuelta aespera sin descargarLFM. Audio/contexto soloRAM
acotados/1h de inactividad humana; monitorRAM/CPU/temperatura/batería/carga y
guardias prevalecen sobre «siempre». No horario nocturno oboot/reinicios implícitos.
Ver sección0 de `CONVERSACION-Y-AGENDA-DINAMICA-ANDROMEDA.md`. Instalado sigue
piloto manual300s/residente900/1800s y actualmente apagado; no renovar ventanas
automáticamente ni reemplazarAPK sin consentimientoRAM informado.

**Vigente tras reporte humano v2:**ensayo cerrado16:22:18UTC, modelo apagado y
datos/obligaciones/volumen/sistema intactos.814 muestras/24,7°Cmáx/RAMmín1236120KiB.
Usuario confirmaWEB, rechaza alarmas/recordatorios y silencio solo intermitente.
Prioridad actual:recordatorios conversacionales con preguntas, confirmación real
y continuidad entre herramientas; alarmas semanales lunes-domingo, eliminación
confirmada y posponer5min. Especificación
`CONVERSACION-Y-AGENDA-DINAMICA-ANDROMEDA.md`, todavía NO implementada/instalada.
Instalado sigue exigiendo frase completa y cerrando agenda, alarma de una fecha.
No otra prueba de la misma versión ni APK/reinicio/captura automática. Casa aún
necesita integración real y consentimiento de pruebas; preservarWEB que funcionó.

**Vigente16:07UTC:**usuario autoriza segunda prueba900s con correcciónSSL ya
instalada, sin reinstalarAPK/cambios de fuentes. Listo16:07:41UTC/TLS+pin/health
verificados,0 hello/token/consulta/captura del agente. Modelo residente disponible
entre charlas manuales300s; INICIAR antes16:17:01UTC/11:17:01Bogotá, fin global
16:22:06UTC/11:22:06Bogotá. No renovar. Recolector único al plazo, evidencia
`artifacts/android-voice/human-resident-v2/`. Falta prueba humana desde botón;
no certificarSSL/voz end-to-end por handshake del agente sin hello.

**Estado vigente:**ensayo900s terminado/modelo apagado a16:03:32UTC.835 muestras,
RAM mínima1308600KiB/RSS pico736724KiB/batería máxima23,1°C; datos/recibos/sistema/
volumen8/15 preservados. ReporteSSL humano: corregido backend en reposo16:04UTC,
rechazo por plazo ahora después deTLS+autenticación; antes cerrabaTCP faltando
305s. No nueva escucha/renovación, otra ventana requiere autorización.12/12
regresionesPOSIX/56 hashes verificados/55 módulos restantes intactos. APK26 sin
reinstalar/pérdidaRAM; aviso español nuevo está solo en fuenteAndroid. Éxito humano
de conversaciones residentes pendiente; no considerar15min de monitoreo como
prueba de voz. Artefactos `resident-runtime-v1-install/after.json` y
`resident-window-ending-fix-v1/final-state.json`.

**Puntos1/2 desplegados con consentimiento:**supervisor/residente y monitor ya
instalados sin APK nuevo ni pérdida RAM. Un ensayo900s en marcha, listo a
15:48:59UTC, fin estimado16:03:21UTC. Micrófono manual300s, modelo permanece entre
sesiones durante el ensayo; recursos iniciales1432908KiB/RSS736636KiB/22,8°C.
Consumo final pendiente; no extender, autorrenovar, activar horarios o declarar
24x7. Recogida numérica única al terminar, sin contenido humano. Evidencia
`artifacts/android-voice/resident-runtime-v1-install/`. Luces yTV después; pedir
app/modelo del neón, sin pairing/redescubrimiento nuevo por este arranque.

**Volumen físico ya confirmado en v2:**15/15→8/15 y acción Android/TTS DONE;
usuario confirma. Dos rechazos anteriores sin transcripción recogida, causa
desconocida. Sesión cerrada/datos y sistema preservados, modelo apagado.
La navegación normal con offline_preview=false permitió activar sin reinstalar;
no se confirma retrospectivamente de dónde vino la marca de prueba. Puntos1/2
ahora en candidato separado, no activados: `MODELO-RESIDENTE-ANDROMEDA.md` y
`artifacts/android-voice/resident-runtime-v1/`. Estados «prueba física pendiente»
inferiores son históricos, salvo porcentajes/formulaciones aún no probados.

**Resultado posterior de la primera ventana física:**caducó sin sesión humana
autenticada porque el usuario estuvo ocupado. Cierre verificado,0 turnos/acciones,
volumen15/15 y datos intactos, modelo/micrófono/servicios apagados; no otra ventana
abierta. No es fallo de la función ni prueba física aprobada. Nueva prueba solo
cuando el usuario esté disponible y la acuerde. Evidencia
`artifacts/android-voice/human-phone-volume-v1/after-user-reported-expiry.json`.

**Orden confirmado posteriormente:**prueba física de volumen primero; luego
separar modelo/sesiones y añadir supervisión de consumo (puntos1/2); después
luces y TV. Priorizar funciones útiles en ese orden. Una sola ventana manual
preparada en `artifacts/android-voice/human-phone-volume-v1/`, ajuste de mitad
solo después de petición humana; no inicio de micrófono/acción/consulta por el
agente. No cambiar el backend mientras la prueba esté activa ni repetirla si
vence. Aceptación física/audible y cierre todavía por recoger.

## Estado real

- **Vigente:** selección nativa de «a la mitad» corregida con una sola descripción
  y desplegada después de pasar8 controles LFM en candidato aislado. Mitad3082ms,
  con Andrómeda2980ms, cincuenta/cero/cien y timer pasan; negaciones/mezclas se
  bloquean en validador.372 tests locales pasan/1 omitido Windows. No se fuerza
  herramienta, repara argumentos ni sustituye el nativo por parser.
  Solo assistant_tools_model.py actualizado, APK26/UID/proceso/RAM/datos y otros53
  módulos intactos; no reinstalación. Evidencia
  `artifacts/android-voice/phone-volume-half-selection-fix-v1/final-state.json`.
  Volumen real15/15 sin cambiar, micrófono/audio/servicios/modelo apagados al cierre.
  **Ajuste físico por voz aún no probado**, sin nueva ventana automática o
  residente/horario activados. Los fallos de mitad inferiores son históricos.
- **Vigente posterior:**APK26/0.12.1-phone-volume+cuatro módulos instalados una
  vez con consentimiento/firma/UID10238/datos conservados y comprobados.
  Android real pasa contrato de volumen con ejecutor ficticio y UI sin audio.
  LFM real selecciona correctamente0%/100%, pero **«a la mitad» no hace llamada
  nativa** (`missing_native_call`,5346ms) y queda bloqueado sin ejecución.
  No aceptación física/humana o0–100 general; siguiente corregir selección sin
  saltarse nativo ni reintentar automáticamente. Evidencia vigente
  `artifacts/android-voice/phone-volume-code26-install/final-state.json`.
  Al cierre modelo/micrófono/servicios/puertos apagados, volumen15/15 intacto,
  agenda/recibos/HOME/SELinux/swap/SSH/web preservados. Sin horario/residente.
- Los estados «25 último instalado»/«26 no instalado» inferiores describen la
  preparación anterior a este consentimiento; no prevalecen sobre el recibo26.
- Última instalación confirmada: **APK25 / 0.12.0-unified-voice**, UID10238,
  interfaz única y lectura web media, más los tres módulos coordinados.
  Recibos y comprobaciones Android sin audio en
  `artifacts/android-voice/unified-voice-code25-install/`.
- El cierre automático de ese instalador se interrumpió después de instalar y
  pasar ambas comprobaciones Android. No se repite la instalación. El usuario
  informó que él detuvo todo; no existe comprobación técnica final de apagado.
- Esta nueva fase es **solo desarrollo local**: no instala, reinicia, toca volumen,
  abre micrófono, consulta Worker, empareja TV ni deja el LFM cargado en el Moto.
- `silencio` sigue fallando. No confundir bajar volumen o suspender el modelo con
  corregir la interrupción TTS. Detener permanece visible.

## 1. Volumen del teléfono — candidato26, no instalado

Ejemplos de petición completa tras activación:

- «Andrómeda, baja el volumen a la mitad» → 50 % del máximo, **no** mitad del actual.
- «Pon el volumen al cero por ciento» → 0 %.
- «Sube el volumen al cien por ciento» → 100 % solicitado.
- «Ajusta el volumen del teléfono al treinta y siete por ciento» → 37 % solicitado.

Se controla **multimedia / STREAM_MUSIC**, incluida la voz TTS actual de Andrómeda.
No alarmas, llamadas, timbre, DND, volumen de TV ni periféricos por inferencia.
Android tiene índices discretos: p. ej., 50 % con máximo15 se aproxima a8/15=53 %.
La respuesta confirma el nivel leído de Android, no un porcentaje ficticio.
Límites de volumen seguro o dispositivo fijo no se evaden: una escritura,
lectura del resultado y estado limitado/incierto si corresponde, sin reintento.
A 0 % no se sube volumen para pronunciar confirmación; el texto queda en pantalla.

Herramienta nativa `set_phone_volume()` **sin argumentos**, `tool_choice=auto`.
Solo después de selección válida se valida la petición actual completa. Se
rechazan negaciones, citas, fechas, mezclas, rango inválido, cambios relativos
ambiguos, valor inventado por el modelo e instrucciones de web/historial.
Android revalida texto/porcentaje/sesión/contexto/turno/plazo/nonce; solo acepta una
mutación por turno compartida con agenda/timer. Recibo numérico real antes de
afirmar éxito, sin añadir la confirmación como conocimiento conversacional.

Nuevo permiso Android normal `MODIFY_AUDIO_SETTINGS`, no root/ajustes seguros.
APK26 + backend requieren despliegue coordinado: protocolo10 y negociación
`absolute_media_v1`, conservando `worker_first_v2_medium`. APK25 se rechaza, no
acepta silenciosamente una herramienta nueva. Modelo/2048/1536/96 sin cambios;
catálogo crece de4 a5 herramientas. Selección LFM/ASR/Android real de este nuevo
catálogo y porcentaje audible **todavía no medidos**; no declararlos aprobados por
pasar pruebas con modelo/audio simulados o compilación.

## 2. Suspensión y horario — política ejecutable en simulación, NO servicio

| Situación | Modelo LFM deseado | Escucha de activación |
|---|---|---|
| Modo residente habilitado, de día | Cargado entre peticiones | Solo con consentimiento |
| Horario nocturno | Descargado | Solo con consentimiento |
| «Andrómeda, suspensión» | Descargado hasta activación explícita | Solo con consentimiento |
| «Andrómeda, buenos días» / «Andrómeda, actívate» | Arranca si es seguro | No depende del LFM |
| Temperatura/RAM/sensor inseguros | Detenido y bloqueo de seguridad | Apagada en esta política |
| Todo deshabilitado / privacidad total | Descargado | Apagada; no puede despertarse por voz |

**Horario propuesto, no aplicado: 23:00–06:00 America/Bogota.** Confirmar horario
y zona antes de activarlo. Suspensión manual de viaje domina el horario:
no arrancará sola a las06:00. La activación explícita dentro del horario nocturno
exime solo ese descanso hasta su fin, no desactiva permanentemente el horario.
Un salto de reloj hacia atrás anula esa excepción, no prolonga escucha/modelo.
Bloqueo de seguridad no se levanta por decir actívate: requiere revisión/rearme.

Código: `backend/assistant_power.py`. Política pura con pruebas de medianoche,
límites06/23, suspensión manual/restauración, excepción nocturna y recursos.
Vocabulario local estricto, con wake obligatorio y sin citas/mezclas. Ese parser
solo no autoriza nada: el supervisor futuro debe recibir ASR actual autenticado,
descartar eco TTS, web, historial y texto remoto; no es identificación de hablante.
No se exportan audio/frases; los ajustes técnicos no incluyen transcripciones.

**Comprobación local conjunta:**370 pruebas Python aprobadas/1 omitida por
permisos POSIX en Windows;1073 aserciones JVM y compilación APK26 pasan. Fixtures
públicos, audio/modelo/ejecutor simulados, incluidas respuestas cuantizadas y
recepción antes de hablar. No son pruebas LFM fresco, Android26 instalado,
micrófono real, nivel físico ni consumo residente. Resultados/manifiesto en
`artifacts/android-voice/phone-volume-code26-v1/`. Primera compilación de un test
Java falló por captura lambda de variable reasignada; corregido el test, sin
relajar autorización. La instrumentación Android de volumen usa audio ficticio
y está compilada, no ejecutada en el Moto durante esta fase.

Simulador **sin efectos**, no es un comando para manejar la instalación:

```powershell
python scripts/andromeda-power-plan.py --enabled --listener --at 2026-10-20T23:00:00-05:00 --available-kib 1500000 --battery-deci-c 250
```

Devuelve `plan_only=true`, `executed=false`, `NIGHT_SLEEP`; no mide el teléfono,
abre micrófono, configura horario ni arranca/mata procesos. Recursos de ejemplo
son sintéticos. Configuración deshabilitada por defecto.

### Falta implementar antes de dejarlo residente

1. Separar **supervisor del modelo**, sesiones TLS de conversación y escucha
   local de activación. Ahora `ConversationModel.cancel()` termina su servidor
   dedicado y el launcher limita sesión300s/supervisor660s. No basta ampliar esos
   números: cerrar una charla debe liberar su contexto sin descargar el residente.
2. Un solo supervisor con exclusión mutua, arranque/parada confirmados, identidad
   PID+inicio+UID+binario verificada, puerto solo loopback, credenciales privadas
   fuera de argv/logs y ninguna ejecución shell elegida por el modelo. No `killall`,
   root genérico en app, remount, daemon oculto o bucles de reinicio infinitos.
3. Puente Android↔supervisor autenticado, de vocabulario cerrado; estado manual de
   viaje durable, sin chats/audio. No restablecer la suspensión al reiniciar.
4. Entregar CPU/micrófono a los avisos existentes y preservar timers/agenda y
   recibos. Dormir el LFM no cancela eventos ni corta una entrega pendiente.
5. Observar RAM disponible/RSS, CPU, batería, temperatura y estado de carga;
   solo métricas numéricas acotadas. Comparar reposo con/sin modelo, y carga
   sostenida, sin prometer menor consumo antes de medirlo. No modificar carga/swap.
   Guardias iniciales heredadas: arranque RAM≥1200000KiB y batería<38°C;
   detener ante RAM<500000KiB o batería≥40°C o sensores inválidos. No se aumentan
   límites para mantenerlo vivo; protocolo real debe añadir watchdog de muestras
   atrasadas y Rearmar explícito. Política probada no es supervisor implementado.
6. Ensayo supervisado y acotado primero; no instalar autoarranque/24x7 ni
   reemplazar micrófono300s hasta acordar privacidad, horario y comprobar recursos.

## 3. Casa — retomado en el plan, sin comandos ni conexiones nuevas

- **TV LG65UR7300PSA**, objetivo conocido192.168.1.9: TCP/TLS funcionaron, upgrade
  WebSocket expiró antes del pairing. No hay clave ni adaptador autenticado.
  Siguiente diagnóstico acordado: comparar con mando móvil funcional/cliente
  de referencia; no repetir pairing a ciegas ni rebajar TLS. Luego lectura de
  estado y una acción reversible consentida; encendido desde reposo por separado.
- **Neón**: falta marca/modelo del controlador, app actual y transporte
  Wi‑Fi/Bluetooth/IR. Una foto del controlador o nombre de app sirve; no claves.
  Elegir una sola integración inicial según compatibilidad local verificable.
- No descubrimiento de red indiscriminado, compras/reset, cuentas nuevas ni
  mandos arbitrarios. Herramientas de teléfono y TV se separan explícitamente.

## 4. Pendientes posteriores

- **Fuera de casa**: activar/suspender por conexión autenticada privada, evaluar
  VPN antes que exponer ADB, root, llama-server o11436 al router. Sin puertos
  abiertos, instalaciones/cuentas ni acceso externo por este encargo. Permisos
  mínimos, protección de repetición/revocación y recibo de estado confirmado.
- **Caritas y personalidades**: más expresiones/transiciones/estilos opcionales,
  conservando estados reales/errores/indicador de micrófono y movimiento reducido.
  No cambiar permisos, autorizar acciones, crear memoria permanente o hablar más
  por cambiar personalidad. Menor prioridad que volumen/energía/casa.
- **Silencio**: reparación separada con criterio de aceptación de interrupción
  real; sin reducir umbral o confundir volumen0 con cancelación de TTS.
