# Arquitectura híbrida de Andrómeda (reestructuración)

Fecha: 26 de octubre de 2026. Estado: **en progreso** — capa local de cumplidos/saludos implementada.

## Por qué híbrida y no "100 % modelo con herramientas"

La idea original era que el LFM local hiciera todo: entender, elegir herramienta y
conversar. Las pruebas mostraron dos problemas:

1. **Lento:** pedirle al modelo cosas que son deterministas (hora, fecha, clima,
   aritmética, cumplidos, "encender las luces") tarda más y consume más.
2. **Frágil:** cuanto más se le pide, más posibilidades de que **choque** con lo que
   ya funciona (p. ej. "eres muy bonita" mal enrutado a "¿quieres un recordatorio?",
   o "prende las luces" no reconocido).

La solución es **separar lo estable de lo flexible**: lo determinista vive en una
capa local (rápida y sin ambigüedad); el modelo se reserva para lo que de verdad
necesita razonar.

## Principio central

> **Si dudas, pregunta — nunca adivines.**

El modelo (o la capa local) nunca debe enrutar a una herramienta salvo que la
petición **encaje claramente**. Si hay ambigüedad, se pregunta algo concreto. Si
nada encaja, se responde de forma amable, no con un rechazo genérico ni un
mal enrutado silencioso.

## Capas

### 1. Capa local determinista (sin modelo) — lo estable

Ya existe y funciona para:
- Hora, fecha y clima (`local_knowledge.py`).
- Aritmética (`local_arithmetic.py`).
- Reacciones de la cara (`FaceReactionPolicy` + `FaceView`).

**Añadir** (alta frecuencia, sin ambigüedad):
- ~~Cumplidos ("eres bonita/linda/hermosa", "te quiero"), saludos, agradecimientos~~ ✅
  implementado en `local_smalltalk.py` (cumplidos + saludos; "gracias" ya cierra la charla
  por `conversation_flow.py`).
- Sinónimos de luces/volumen: "prende" = "enciende", "apaga", "sube el volumen", etc.

Ventaja: es **determinista y aparte del modelo**, así que no regresa ni choca con
nada ya probado.

### 2. Capa del modelo — lo flexible

Solo para lo complejo o de varias partes (agenda con detalles, búsqueda web).
Regla dura en el prompt: *solo llama una herramienta si encaja claramente; si no
estás seguro, pregunta*.

### 3. Capa de aclaración — el puente

Cuando el modelo no está seguro, pregunta algo concreto ("¿quieres que encienda las
luces?") y actúa tras la confirmación. Las luces ya lo hacen; **extender el patrón**
a recordatorios, alarmas y volumen.

### 4. Fallback amable

Si nada encaja: "No te entendí bien, ¿puedes decirlo de otra forma?" — acotado, sin
bucles infinitos.

## Reglas de no regresión

- No romper nada de lo que ya funciona (voz, TTS, BLE/luces, agenda, saludos,
  reacciones, clima/hora/fecha, guard de batería).
- Todo local; la nube sigue siendo opcional (solo búsqueda web explícita).
- Sin almacenar audio/transcripciones/conversaciones. Los logs de depuración que se
  añadieron son **temporales** y se retiran al terminar.

## Orden de trabajo propuesto

1. **"Si dudas, pregunta" + fallback amable** (mayor impacto; evita el mal enrutado).
2. **Capa local de cumplidos/saludos/agradecimientos** (saca lo común del modelo).
3. **Sinónimos en las gramáticas de luces/volumen**.

## Relación con los otros bloques

- El guard de batería, modo descanso y luces programadas son independientes de esta
  reestructuración y se conservan tal cual.
- La cancelación global sigue bloqueada; esta reestructuración no la reabre.

## Archivos afectados (al implementar)

- `backend/assistant_tools_model.py` (prompt: "si dudas, pregunta").
- `backend/local_*` (nueva capa de cumplidos/saludos; sinónimos).
- `android-voice/src/local/doja/voice/` (gramática de luces/volumen, diálogos).
- `tests/` (regresiones por cada capa).

## Pendientes recientes (fuera de esta reestructuración)

- **Recordatorio que sonó a las 12:00 y apagó el motor**: resuelto. La causa era
  `voice_cache_clear_failed`: la purga de caché (`_purge` → `purge_engine_cache`)
  fallaba en carrera con el modelo aún generando. Arreglado cancelando la generación y
  esperando antes de purgar, y ampliando la ventana de la purga. Verificado.

- **Clima apagó el motor (`EOFException`)**: resuelto. La causa era
  `Persistent invalid audio clock`: la consulta de clima hace una petición síncrona a
  Open-Meteo que bloquea el bucle de voz ~1–3 s, envejeciendo los cuadros de audio; esos
  cuadros viejos (drenados tras el fetch) marcaban una brecha de reloj que nunca se
  limpiaba y disparaba `expired()` a los 5 s. Arreglo definitivo: **descartar los cuadros
  de audio cuando el estado no es `wake`/`listen`** (micrófono apagado), antes de juzgar
  el reloj. Se conservan además, como defensa: `expired()` solo en `wake`/`listen`,
  `say()` limpia la brecha, y el tiempo de clima a 3 s. Pendiente de verificación con
  otra consulta de clima.
