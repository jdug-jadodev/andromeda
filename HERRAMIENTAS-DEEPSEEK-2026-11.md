# Herramientas de DeepSeek — registro y receta (2026-11-18)

**Qué es este documento.** El registro de cómo le vamos dando a DeepSeek el control
de los datos de cada herramienta (recordatorios, alarmas, temporizadores, luces,
volumen, TV…), para que Andrómeda deje de ser un bot de frases hechas. Cada vez que
se abra una herramienta nueva, se anota aquí: qué datos acepta, qué se valida, qué
falta y qué pruebas lo cubren.

**Estado (2026-11-18).** Paso 1 hecho y **apagado por defecto**: el modelo ya puede
aportar los datos de recordatorio y alarma, la casa los valida, y hay 13 pruebas
nuevas. **No está desplegado y no cambia nada en el teléfono** mientras la bandera
`ANDROMEDA_TOOL_ARGUMENTS` esté apagada (es el valor por defecto).

## 1. El principio: validar datos, no texto

Antes se validaba la **frase**: si no coincidía con el regex, se rechazaba. Eso es
lo que hacía rígido al bot, y además duplicaba el trabajo en el teléfono.

Ahora se valida el **dato**:

1. El modelo separa el tema del momento y devuelve datos.
2. La casa comprueba que esos datos **salieron de tu boca** (prueba de origen).
3. La casa calcula el instante con el parser cuando puede, o valida la fecha del
   modelo como dato (real, futura, dentro de un año, coherente con el día dicho).
4. Se resume con la fecha absoluta y se pregunta «¿Lo guardo?».
5. La app ejecuta el valor autorizado, no vuelve a interpretar la frase.

## 2. Los tres cerrojos que había

| Cerrojo | Dónde estaba | Estado |
|---|---|---|
| Herramientas sin datos | `assistant_tools_model.TOOLS` (`create_reminder`/`create_alarm` con `properties: {}`) | Abierto: esquema con `text`, `when_text`, `date`, `time`, `repeat` |
| Los datos se tiraban | `deterministic_router._dispatch()` | Abierto: usa los datos validados; el parser queda de respaldo |
| La app revalidaba la frase | `AgendaPermit.validatedDialogue`, `AgendaDialogue.claim` | **Pendiente**: es el paso 2 (APK 107) |

## 3. Cómo funciona el paso 1

Flujo real, con la frase de hoy:

```
tú:      «recuérdame tomar el antibiótico todos los días de la semana a las diez y treinta de la mañana»
modelo:  create_reminder(text="tomar el antibiótico",
                         when_text="todos los días de la semana a las diez y treinta de la mañana")
casa:    ¿existe ese texto en tu frase? sí → parser → serie diaria a las 10:30
         → «¿Lo guardo?» (la app actual todavía no sabe guardar series)
```

- **Archivo nuevo:** `backend/tool_arguments.py`. No hace E/S, no ejecuta, no
  guarda: devuelve propuesta o rechazo hablado.
- **Bandera:** `ANDROMEDA_TOOL_ARGUMENTS=1` la enciende. Apagada, `TOOLS` queda
  exactamente igual que antes y `_dispatch` no cambia de comportamiento.
- **Integración:** `assistant_tools_model` (esquema y `native_call`) y
  `deterministic_router._dispatch` (camino de DeepSeek).
- **Pruebas:** `tests/test_tool_arguments.py` (13). Demostración sin teléfono:
  `python tests/probe_tool_arguments.py`.

### Reglas de validación

| Regla | Qué evita |
|---|---|
| `text` y `when_text` deben aparecer literalmente en tu frase (sin tildes ni mayúsculas) | Que el modelo invente un tema o una fecha |
| Si el fragmento de tiempo se entiende, manda el parser | Fechas inventadas: el instante se calcula, no se copia |
| Fecha del modelo: real, futura, ≤ 1 año, hora válida | Fechas imposibles o pasadas |
| Si nombraste un día, la fecha debe ser ese día | «Martes» con fecha de miércoles |
| `repeat` no puede contradecir el fragmento | «Una vez» con «todos los días» |
| Serie sin soporte de la app → rechazo explícito | Guardar una serie y que suene una sola vez |
| Hora sin mañana/tarde → «¿10 de la mañana o de la tarde?» | Asumir 10:00 o 22:00 |

**El modelo nunca ejecuta.** Solo propone datos; la propuesta no toca Android.

## 4. Receta para la siguiente herramienta

1. **Esquema con datos.** Añadir en `tool_arguments.schemas()`:
   - campos obligatorios = prueba de origen (`text`/`target` + fragmento literal);
   - campos opcionales = valores ya resueltos;
   - `enum` para lo cerrado (repetición, objetivo, color…).
2. **Validador por tipo de dato**, no por frase: rango, existencia, coherencia,
   límites. Si falta un dato, devolver `ok=False` con `reason` y `message`.
3. **Pruebas mínimas** (las mismas seis de aquí):
   - caso real de casa (frase del usuario);
   - texto inventado → rechazo;
   - valor fuera de rango o pasado → rechazo;
   - contradicción entre fragmento y valor → rechazo;
   - ambigüedad → pregunta concreta;
   - bandera apagada → comportamiento idéntico al de hoy.
4. **Apagado hasta que la app lo sepa ejecutar.** El valor validado tiene que
   viajar como valor, no como frase.
5. **Despliegue conjunto** backend + APK cuando la app acepte el valor autorizado.
6. **Anotarlo en la tabla de abajo** y en el changelog.

## 5. Registro de herramientas

| Herramienta | Datos que aportará el modelo | Estado | Qué falta |
|---|---|---|---|
| `create_reminder` | tema, fragmento de tiempo, fecha/hora, repetición | **Paso 1 hecho, apagado** | Series en la app; quitar re-parseo |
| `create_alarm` | fragmento de tiempo, fecha/hora, repetición, etiqueta | **Paso 1 hecho, apagado** | Quitar re-parseo; confirmar semanal/diaria |
| `create_timer` | duración (ya usa `duration_text`) | Parcial (esquema viejo) | Migrar a la misma validación de datos |
| `set_phone_volume` | porcentaje o «la mitad» | Pendiente | Esquema + rango + confirmación si dudoso |
| `control_lights` | objetivo, acción, color/hex, brillo, horario | Pendiente (P1) | Esquema + validación de color y objetivo |
| TV (`tv_*`) | entrada, app, volumen, escena | Pendiente | Esquema + estado real de la TV |
| Música / Xbox / lavadora | qué/dónde, acción, programa | Bloqueado por el plan de expansión | Decidir modelos y fuentes |

## 6. Seguridad: qué se conserva y qué cambia

**Se conserva** (no se toca):

- Nonce de un solo uso y concesión ligada al turno.
- Idempotencia y límites de acciones.
- El modelo no ejecuta ni guarda.
- Confirmación con la fecha absoluta antes de guardar.
- Límite de un año y rechazo del pasado.

**Cambia, cuando se encienda:**

- La app deja de interpretar la frase; ejecuta el valor autorizado.
- El texto deja de ser el contrato entre backend y app; lo es el valor + nonce.

## 7. Cómo verlo sin desplegar

```powershell
# Demostración con cinco casos (tu frase incluida)
python tests\probe_tool_arguments.py

# Pruebas
cd tests; python -m unittest test_tool_arguments
```

Salida esperada del caso 1: `REMINDER «tomar el antibiótico» daily -> 2026-11-19 10:30
(fuente: span)`. El caso 2 (app actual) responde: «Puedo guardarlo una sola vez.
Las series llegan con la próxima actualización del teléfono».

## 8. Qué falta para encenderlo

1. **APK 107:** que la app acepte `pending_question`/`pending_ready` y ejecute el
   `value` autorizado (nonce, permiso, límites), sin re-parsear la frase.
2. **Series de recordatorio en la app:** guardar la repetición y reprogramar.
3. **Protocolo 14** anunciado por el backend; con app < 14, camino actual intacto.
4. **Un despliegue conjunto** backend + APK, con verificación en el teléfono.

## 9. Residuo LFM (deuda a retirar)

El modelo local de 1.2B **ya no corre**: no hay nada en el puerto 11435 y no hay
proceso de llama. `ANDROMEDA_MODEL=lfm` solo etiqueta métricas en
`conversation_agent.py`. Pero su diseño sigue mandando:

- herramientas sin datos (el 1.2B no rellenaba argumentos),
- `preparation_ms`, «prefijo público» y sus pruebas de 55-85 s,
- el presupuesto de 1536 tokens, que no aplica a DeepSeek.

Retirarlo es parte del trabajo: al quitarlo, el esquema con datos y la conversación
libre dejan de arrastrar límites que ya no existen. No se hace en este paso para no
mezclar la retirada con el cambio de validación.

## 10. Changelog

- **2026-11-18 — paso 1.** `tool_arguments.py` con esquema y validación de datos
  para recordatorio y alarma; `native_call` acepta campos opcionales cuando el
  esquema los declara; `_dispatch` y `interpret_result` usan los datos validados
  con la bandera encendida. 13 pruebas nuevas. Apagado y sin desplegar. Suites
  completas sin regresiones (45 fallos preexistentes idénticos al baseline).
