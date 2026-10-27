# Saludos y reacciones de Andrómeda

Estado: **instalado v58** (`0.13.61-greetings-reactions`, versionCode 89), pendiente
de prueba humana audible/visual. Sin cambios de modelo, Vosk, TTS ni nube.

## Decisión

- No se usan emojis de texto: las reacciones se dibujan con **la misma cara de
  Andrómeda** (`FaceView`), cambiando ojos, boca, color y pequeños efectos.
- La política es local y solo recibe el texto que ya usa la conversación. No
  guarda audio, transcripciones ni contenido humano.
- No cambia el modelo ni el prompt remoto.

## Saludos

- **Buenos días:** 06:00–11:59 (hora de Bogotá).
- **Buenas tardes:** 12:00–18:59.
- **Buenas noches:** 19:00–05:59.
- Se dice **una sola vez por periodo y día**. El periodo nocturno cruza la
  medianoche: 23:00 y 00:30 comparten marcador; a las 06:00 empieza el saludo
  de la mañana.
- El marcador se guarda en `SharedPreferences` (`greetings_v1`) y solo se marca
  cuando el TTS **realmente empieza**. Si la salida falla antes, el saludo queda
  pendiente para la siguiente respuesta.
- El saludo se antepone a la primera respuesta hablada del periodo. El texto
  original sigue siendo el que usa el contexto; el saludo no se envía al modelo.

## Reacciones

| Situación | Reacción visual |
|---|---|
| No encuentra o no puede hacer algo | Cara triste, cejas caídas y color azul grisáceo |
| El usuario cuenta algo triste | Llanto: ojos bajos, lágrimas azules |
| Clima frío o el usuario dice que tiene frío | Temblor/snow lines y tono azul |
| Clima caluroso o el usuario dice que tiene calor | Gota de sudor y tono naranja |
| Niebla | Nubes grises translúcidas |
| Logro del usuario | Confeti, sonrisa amplia, tono dorado |
| Algo bonito | Corazones y tono rosa |
| Chiste | Lengua fuera, tono amarillo |
| Algo feo | Boca zigzag, tono verde |
| Agradecimiento | Gafas de sol, tono violeta |
| Pensando | Los tres puntos animados ya existentes (sin nube añadida) |

- La reacción dura unos **5 segundos** y no sustituye el estado de servicio.
- El clima usa la temperatura local: frío con ≤15 °C, calor con ≥23 °C; niebla
  por código 45/48 o por la palabra «niebla». Si el usuario dice «frío» o
  «calor», esa palabra tiene prioridad sobre el umbral.
- Las frases se clasifican localmente: tristeza, logro, halago, feo,
  agradecimiento, chiste y fallo de Andrómeda. No se ejecutan acciones por una
  reacción.
- La reacción de error también aparece cuando el backend informa un fallo.

## Archivos

- `FaceReaction.java`: estados visuales.
- `FaceReactionPolicy.java`: detección local de frases y clima.
- `GreetingPolicy.java`: periodos, texto y clave que cruza medianoche.
- `GreetingStore.java`: marcador persistente, marcado al iniciar TTS.
- `FaceView.java`: dibujo de reacciones (sin nube de pensamiento).
- `VoiceService.java`: reacción desde el turno y saludo en la primera salida.
- `MainActivity.java`: aplica reacciones a la cara y combina el clima.
- `android-voice/tests/FaceReactionPolicyTest.java`
- `android-voice/tests/GreetingPolicyTest.java`

## Pruebas

- `FaceReactionPolicyTest`: 20 aserciones (frases, fallo, chiste, clima).
- `GreetingPolicyTest`: 14 aserciones (límites 06/12/19, medianoche, textos).
- `test-policy.ps1` completo: pasa.
- `build.ps1`: compila y firma.
- `FaceInstrumentation` en el teléfono: `passed=true`, 8 estados distintos,
  36 fotogramas de cara en 1,2 s y **barrido del borde animándose** (la prueba
  comprueba además que la cara programa fotogramas y que el borde queda animado
  mientras procesa, para que una escala de animación del sistema en 0 no vuelva
  a apagarlos en silencio).
- APK instalado: versionCode 89, `0.13.61-greetings-reactions`.

## Pendiente de prueba humana

1. Decir «Andrómeda» en la mañana/tarde/noche y confirmar el saludo una sola vez.
2. Volver a hablar en el mismo periodo y confirmar que **no** repite el saludo.
3. Probar una frase triste, un logro, un halago, un agradecimiento, algo feo y
   un chiste, y confirmar la cara correspondiente.
4. Preguntar por el clima y comprobar frío/calor/niebla.
5. Provocar un fallo (por ejemplo, una acción no válida) y confirmar la cara
   triste.
6. Confirmar que el saludo y las reacciones no añaden audio ni texto al almacenamiento.
