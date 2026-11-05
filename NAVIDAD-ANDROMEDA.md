# Modo Navidad de Andrómeda

Un solo parámetro en el teléfono enciende o apaga todo lo navideño. La app lo lee cada segundo, así que el cambio se ve solo en uno o dos segundos: no hay que reinstalar, reiniciar el motor ni tocar la app.

## Desactivar (apagar la Navidad)

Desde el PC, con el Moto G conectado por USB:

```
adb shell settings put global andromeda_navidad 0
```

## Activar

```
adb shell settings put global andromeda_navidad 1
```

## Ver cómo está

```
adb shell settings get global andromeda_navidad
```

`1` = encendido, `0` o `null` = apagado.

Si hay más de un dispositivo conectado, añade `-s ZY227BCQ85` después de `adb`.

## Qué controla

Con `1`:
- el fondo navideño (muñeco de nieve, regalos, trineo y nieve) antes de que salga Andrómeda;
- los ojos blancos;
- la corona con campanas y el reno en la pantalla del reloj;
- Papá Noel con la bolsa, el contador de días y el jingle cuando preguntan "¿cuántos días faltan para Navidad?";
- el 24 de diciembre: el resplandor, "¡Es hoy!" y la lluvia de regalos.

Con `0` no se muestra nada de eso. Si con la Navidad apagada preguntan cuántos días faltan, Andrómeda responde solo con la voz.

Código: `android-voice/src/local/doja/voice/ChristmasMode.java` (clave `andromeda_navidad`).
