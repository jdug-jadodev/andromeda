# Andrómeda: diseño revisado, Moto como único equipo

## 1. Condiciones del usuario, no negociables sin volver a acordarlas

- El Moto G8 Plus es el corazón del proyecto: reutilización y valor sentimental de su primer teléfono comprado. No sustituirlo ni exigir un PC/mini-PC.
- Procesamiento de IA, voz, memoria e inventario del hogar locales. Sin inferencia en nube ni envío de datos domésticos por defecto.
- Conversación natural corta con hilo y herramientas elegidas por la IA. No programar respuestas para cada pregunta ni exigir una frase exacta para cada acción.
- **Máximo 30 segundos desde terminar de hablar hasta empezar una respuesta útil.** Objetivo preferido para uso rutinario: 5–10 s. Todavía NO está cumplido.
- Reloj visible y cara durante interacción; no bloqueo automático. Mantener Android, teclado, cámaras, audio y conectividad.
- Memoria por conversaciones separadas, cada una caduca tras una hora de su propia inactividad. Temporizadores/recordatorios persistentes separados.
- Conservar SSH, web y archivos existentes. No root para modelo/agente; no comandos libres generados por IA.
- Protección reversible de carga y ensayo sostenido antes de uso desatendido 24/7.

Esta revisión no abre micrófono, no habilita inicio automático y no modifica carga, swap ni servicios Android.

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

Contrato legible por máquina: `config/acceptance.json`. Evaluador de evidencias: `backend/acceptance.py`, CLI `scripts/evaluate-acceptance.py`. **Es una puerta de pruebas, no enforcement en el servicio actual.** Solo evidencia de voz real puede certificar voz; pruebas sintéticas y dobles no la sustituyen. Las anotaciones de corrección requieren revisión, no se deducen de que el LLM haya generado texto.

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

Revisar un protocolo con desafío/respuesta mutuos HMAC y nonces, versiones, IDs y protección contra repetición; no enviar la clave reutilizable al primer proceso que escuche el puerto. Sigue pendiente, no afirmar que ya existe.

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

## 7. Fuentes de Internet e interfaz futura — requisitos añadidos, no implementar aún

Wikipedia **no es obligatoria ni fuente única**, y ni Wikipedia ni un resumen generado por Google son garantía de verdad o actualidad. La búsqueda debe permitir fuentes adecuadas, fechas y referencias; para clima usar una fuente meteorológica con ubicación y hora de actualización, no una frase de IA sin comprobar.

**Decisión posterior del usuario: descartada la IA de Google para consultas/resúmenes web.** No integrar Gemini ni extraer AI Overviews como fuente del asistente. Mantener búsqueda general y fuentes directas como capacidades futuras, con síntesis local y consultas públicas mínimas: nunca inventario, credenciales ni conversación doméstica. Esta decisión sobre IA de búsqueda no implica cambiar el motor TTS Android existente ni autoriza otro proveedor de inferencia en nube.

Interfaz se aborda **después** de que funcione el núcleo:

- Teléfono siempre **horizontal**; diseñar toda la interfaz para ese uso.
- Reposo sin activación: reloj digital y temperatura actual de **Bogotá**, mostrando antigüedad/falta de datos cuando corresponda. Distinguir observación de estimación meteorológica.
- Activación: cara dinámica de Andrómeda, animaciones, movimientos, gestos y emociones/estados expresivos. No presentar emoción animada como sentimientos reales del modelo.
- Recorrido por los bordes de pantalla durante carga, color personalizable. Antes de UI aclarar si «carga» se refiere a batería, preparación/procesamiento o ambos; no bloquear ahora el núcleo por esa decisión visual.
- Reemplazar y finalmente retirar el **APK launcher Doha** cuando la nueva interfaz funcione como HOME y haya recuperación probada. Mantener respaldo y posibilidad de volver atrás. No desinstalar ni cambiar HOME ahora; no confundir launcher con backend Debian/Nginx/SSH ni borrar sus datos por compartir el nombre Doha.
