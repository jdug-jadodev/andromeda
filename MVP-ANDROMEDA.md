# Andrómeda · primera versión, por entregas

## Objetivo vigente

Asistente local en el Moto, inspirado en las funciones cotidianas de Alexa, sin integrar todavía TV, luces o electrodomésticos. No se promete paridad con Alexa en esta primera versión. Diseño y requisitos: `DISENO-ANDROMEDA.md`.

## Avance posterior 0.3 — micrófono/activación aislados

Por petición explícita del usuario, avanzar en micrófono → nombre → cara mientras se evalúa la comprensión del modelo. **0.3 instalada**, botón Micrófono ofrece prueba limitada de 90 s/5 min contra backend wake-only: muestra cara de escucha 8 s y transcripción, sin LLM, TTS ni acciones. Botón visible de silenciar/cuenta atrás, parada al salir. Pasan 88 pruebas Python, 48 aserciones JVM, instrumentación UI y nueve casos sintéticos con Vosk real; pendiente confirmación humana de este flujo en la UI nueva. Preparación del detector no inicia captura; ver `MICROFONO-ANDROMEDA.md`. No se habilitó escucha permanente ni se aprobó el modelo por habilitar este ensayo.

**Confirmación posterior del usuario:** dijo «sí» al verificar voz → nombre → paso del reloj a la cara. Despertar visual humano confirmado en 0.3; transcripción, regreso, silenciado y conversación con IA no se deducen de esa respuesta. Alcance exacto en `MICROFONO-ANDROMEDA.md`.

**Siguiente bloque ejecutado, aún sin respuestas de voz:** comparación de conversación sin herramientas con Qwen 1.7B Q4, Q8 del mismo modelo y LFM existente. Persisten errores de identidad/capacidades incluso sin catálogo; Q8 no basta y LFM solo es más rápido. No se cambió la app 0.3 ni se conectó el LLM al micrófono por estos ensayos. Resultados, cuantización verificada, recursos y candidato posterior fundamentado (todavía no descargado) en `EVALUACION-PRECISION.md`. 96 pruebas Python pasan; modelo sin aprobación.

**Prioridad posterior explícita: eficiencia y contexto, no modelos mayores.** Se comparó el Qwen Q4 existente con dos reglas globales breves de separación usuario/asistente, regresión y ocho controles nuevos base/variante. La identidad sigue fallando y la variante no mejora eficiencia. Dos prompts idénticos con/sin caché dan iguales errores; recomputar solo añade tiempo. No se descargó otro modelo ni se activó conversación por voz. Ver `EVALUACION-IDENTIDAD.md`; 100 pruebas de soporte pasan. Mantener la app 0.3 y su activación ya confirmada.

**Candidato alternativo autorizado después:** Qwen3.5 2B Q4 descargado/verificado e instalado solo para pruebas. Motor carga; misma suite de 15 turnos y ocho controles con muestreo oficial y un control de penalización de presencia. RAM del servidor ~1.39 GiB PSS, pero persisten identidad/perspectiva/consejos incorrectos; no se conecta a voz. No hubo nueva instalación de app ni micrófono activo. Evidencias y correcciones de evaluación/transporte en `EVALUACION-QWEN35.md`; 106 pruebas de soporte pasan. No declarar éxito por cero fallos mecánicos del control.

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

1. **Comprensión fiable:** Qwen3 1.7B Q4_K_M sigue como candidato, apagado. Negaciones/identidad fallan en conversación completa; ver `EVALUACION-CONVERSACIONAL.md`. No usar la nueva cara para dar apariencia de aprobación a ese núcleo.
2. Presupuesto global de turno, cancelación real/rechazo de efectos tardíos y transporte autenticado mutuamente. Silenciar hoy detiene captura, **no garantiza cancelar una acción ya enviada al backend experimental**.
3. Conversación/memoria y temporizador real de extremo a extremo, después voz sintética y finalmente pruebas humanas acotadas. Máximo 30 s desde fin de voz a respuesta útil.
4. Alarmas programadas, recordatorios, cálculos e información con fuentes: objetivos de la entrega funcional, **no capacidades ya integradas por esta UI**. Clima en reposo validado en el Moto con 0.2; pendiente integrarlo a conversación. Música/proveedores y otras funciones amplias requieren concretar alcance.
5. Completar inspección de entrada/borde y espera física de opciones en 0.2; reloj y clima ya verificados. El usuario aclaró el significado del loader; no extenderlo a batería.
6. Medir LLM + ASR + TTS + UI simultáneos y validar protección de carga antes de operación 24/7. No se modificaron batería, swap, servicios Android ni arranque automático.

## Reproducir la prueba visual (APK ya instalado)

```powershell
.\android-voice\test-ui.ps1 -Serial ZY227BCQ85
```

El script exige `passed=true` y el código de instrumentación correcto: no acepta el exit code 0 de `am` como prueba de éxito. No ejecutar sobre una sesión de audio en curso. Para compilar: `android-voice/build.ps1`. No instalar ni iniciar pruebas de micrófono como consecuencia implícita de esta comprobación visual.

Pruebas locales de políticas sin Android: `.\android-voice\test-policy.ps1`.
