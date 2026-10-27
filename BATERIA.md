# Batería: observación y controles pendientes

## Confirmación del usuario

Abrió personalmente la tapa para inspeccionar la batería: no observó hinchazón ni calor anormal. Había usado el teléfono como servidor durante periodos de hasta 15 días conectado a alimentación. Esto no certifica estado interno ni elimina el riesgo por desgaste; cerrar/proteger el equipo sin presionar la batería, manteniendo ventilación.

## Monitor de solo lectura

Código en `scripts/battery-monitor.sh`; hook Magisk en `scripts/98-doha-battery-monitor.sh`.

Instalado e iniciado el 2026-10-16. Prueba de sintaxis correcta, primera lectura guardada (73 %, 29.7 °C, `Good`) y segundo arranque rechazado por bloqueo exclusivo conservando el PID original. Hook de arranque instalado; falta validar un reinicio real. No se reinició ni interrumpió Debian/SSH/Nginx.

- Observa una vez por minuto temperatura, nivel, salud, estado, corriente BMS y alimentación USB.
- Guarda datos privados acotados en `/data/adb/doha-battery/`: log actual de aproximadamente 256 KiB más una rotación, y última lectura.
- Un bloqueo del kernel evita instancias duplicadas. No instala paquetes ni modifica scripts existentes de Debian o swap.
- Una lectura de 40 °C o más se etiqueta como aviso orientativo de revisión, no umbral certificado de peligro o corte. Un sensor ausente o salud distinta de `Good` también queda marcado.
- **No detiene la carga, no apaga el teléfono, no manda alertas externas y no sustituye las protecciones originales.** Los avisos quedan en los archivos. Durante suspensión o caída del sistema puede dejar de observar: no es protección independiente.
- No adquiere un wake lock propio ni mantiene la pantalla encendida.

Comprobación:

```powershell
adb shell su -c 'cat /data/adb/doha-battery/latest.txt'
adb shell su -c '/data/adb/doha-battery/battery-monitor.sh --once'
```

Para deshabilitarlo, crear `/data/adb/doha-battery/DISABLED` como root. El proceso terminará al siguiente ciclo, como máximo unos 60 segundos después, y el hook no lo iniciará al arrancar. No afecta la carga. Conservar el hook deshabilitado permite revertir sin borrar archivos ajenos.

## Hallazgo del controlador Motorola

Firmware observado: `QPIS30.28-Q3-28-26-4-1-7`.

Nodo presente: `/sys/class/power_supply/battery/device/force_demo_mode`, valor inicial `0`.

Fuente consultada de una versión anterior de la misma familia QPI, **no verificada como idéntica al binario instalado**:

https://github.com/MotorolaMobilityLLC/motorola-kernel-modules/blob/android-10-release-qpi30.28-q3-28-26/drivers/power/qpnp-smbcharger-mmi/qpnp-smbcharger-mmi.c

En esa fuente:

- `force_demo_mode_store` acepta valores de 35 a 80; fuera de rango usa 35. **Escribir 0 no restaura el estado inicial.**
- El modo demo limita voltaje a 4000 mV y puede suspender la entrada USB; reanuda tras descenso de unos cinco puntos de carga respecto del corte. No equivale necesariamente a bypass de batería ni a un límite exacto del 80 %.
- No se modificó ese nodo, ni se instalaron ACC u otros módulos de carga.

Siguiente paso: confirmar mecanismo reversible de control, recuperación ante pérdida de ADB y efecto real en corriente/estado antes de persistir cualquier cambio. Evaluar corte externo certificado e independiente; nada de pruebas calentando la batería.

## Auditoría de solo lectura — 2026-10-24

No se escribió ningún nodo de carga.

- Capacidad actual: **84%**, temperatura **26.2 °C**, salud `Good`, estado `Charging`.
- `charge_full`: **3.274.000 µAh**; `charge_full_design`: **4.093.000 µAh**.
  Capacidad estimada: **~80.0%** de la original.
- `cycle_count`: **1552** ciclos; el teléfono ya tiene desgaste acumulado.
- `input_suspend=0`; `charge_control_limit=0` con máximo 8; `force_demo_mode=0`;
  `step_charging_enabled=0`; `sw_jeita_enabled=0`; `recharge_soc=99`.
- Monitor existente: lectura de las 17:39 UTC con 83%, 26.1 °C, `Good`, cargando.
- El nodo `input_suspend` es el candidato más claro para **pausar y reanudar la entrada**
  de carga; no se ha probado todavía.
- `force_demo_mode` sigue sin tocarse: no hay prueba de restauración fiable y no se usará
  a ciegas.

Recomendación conservadora:

1. Probar `input_suspend` durante **8 segundos** con restauración inmediata y el usuario
   delante; verificar que pasa a descarga y vuelve a carga.
2. Si la prueba es reversible y estable, implementar un guard root con **pausa a 80%** y
   **reanudación a 70%**, con pausa por temperatura y hook de arranque. No es un corte
   certificado de seguridad; es una medida de desgaste.
3. Alternativa sin kernel: regleta/temporizador inteligente certificado, con histéresis
   basada en nivel; no usar un temporizador ciego que pueda descargar el teléfono.
4. Físico: cargador y cable certificados, regleta con protección, superficie dura y
   ventilada, nada que cubra el teléfono, revisar hinchazón/calor. La pantalla permanece
   siempre encendida por el launcher; valorar apagarla en reposo para reducir calor y desgaste.
5. Ante hinchazón, olor, calor anormal o salud distinta de `Good`: dejar de cargar y retirar
   el equipo de uso, sin esperar a una prueba de software.

## Prueba reversible de `input_suspend` — 2026-10-24

Con autorización explícita del usuario, se ejecutó una prueba de **10 segundos** con restauración
inmediata:

- Antes: `input_suspend=0`, 89%, `Charging`, corriente +452.881 µA, 26.3 °C.
- Durante: `input_suspend=1`, 89%, `Not charging`, corriente **-451.660 µA**, 4.142.848 µV, 26.1 °C.
- Después: `input_suspend=0`, 89%, `Charging`, corriente +487.671 µA, 26.2 °C.
- Comprobación final: `input_suspend=0`, `Charging`, 89%, 26.3 °C.

La pausa y reanudación de entrada funcionan y se restauraron los valores. **No se dejó ningún
cambio persistente.** `force_demo_mode` sigue sin tocarse.

Opciones:

1. **Guard root local (recomendado, sin comprar hardware):** pausa a 80%, reanuda a 70%, pausa
   térmica a 40 °C, hook de arranque y rollback con `DISABLED`. No es una protección certificada;
   es una medida de desgaste. Requiere autorización antes de instalarlo.
2. **Regleta/temporizador inteligente certificado:** más independiente, pero cuesta dinero y
   necesita histéresis basada en nivel para no descargar el teléfono. El usuario indicó que casi no
   tiene recursos para comprarlo.
3. **Solo monitoreo:** el monitor actual no corta la carga ni evita el desgaste.

Recibo: `artifacts/android-voice/battery-audit-20261024/receipt.json`.

## Guard de carga instalado — 2026-10-24

Con autorización del usuario se instaló un guard root local, **sin comprar hardware**:

- `/data/adb/doha-battery/charge-guard.sh`
- `/data/adb/doha-battery/charge-guard-watchdog.sh`
- `/data/adb/service.d/99-doha-charge-guard.sh` (arranque Magisk)

Política:

- **Pausa** la entrada al **80%**.
- **Reanuda** al **70%**.
- Pausa si la batería llega a **40 °C**; reanuda al enfriar por debajo de **38 °C**.
- Histéresis y ciclo de comprobación de 60 s; no escribe `force_demo_mode`.
- Watchdog independiente: si el guard muere o su latido queda viejo, pone `input_suspend=0`,
  registra `WATCHDOG_RESUME` y reinicia el guard.
- Rollback: crear `/data/adb/doha-battery/CHARGE_GUARD_DISABLED`. En el siguiente ciclo pone
  `input_suspend=0`, registra `DISABLED_RESTORE` y termina guard/watchdog. Para reactivar,
  borrar el archivo y ejecutar el hook de arranque o reiniciar.
- Log privado root: `/data/adb/doha-battery/charge-guard.log`.

Pruebas realizadas:

- El guard arrancó con 98% y puso `input_suspend=1`, `Not charging`, corriente negativa.
- Rollback con `CHARGE_GUARD_DISABLED`: volvió a `input_suspend=0`, `Charging`.
- Se mató el guard con `kill -9`; el watchdog puso `input_suspend=0`, registró
  `WATCHDOG_RESUME` y relanzó el guard, que volvió a pausar.
- Quedó activo en `input_suspend=1` a 98%; bajará hasta 70% y entonces reanudará la carga.
- A las 13:33 del 2026-10-24: `input_suspend=1`, `status=Not charging`, 91%, corriente
  **-476 mA**, 25 °C. El sistema puede seguir mostrando el icono de “conectado/cargando” porque
  el cargador está presente, pero el porcentaje está bajando: **no está cargando**. Es el guard
  actuando por encima del 80%.
- **Primer ciclo observado por el usuario:** bajó hasta 70%, reanudó la carga y volvió a 80%.
  El guard funciona.

### Reinicio real y corrección de microciclos — 2026-10-24

- El usuario reinició el teléfono. El hook de Magisk arrancó guard y watchdog automáticamente.
- Versión instalada: APK v49 (versionCode 80).
- Al arrancar: batería 80%, `input_suspend=1`, `Not charging`; el guard volvió a pausar.
- Alarmas vueltas a programar: luces **22:30** y descanso **23:00**.
- Se detectó un fallo: el guard reanudaba a 79% por la rama `THERMAL_RESUME`, produciendo
  microciclos 79↔80. Se corrigió con un archivo `charge-guard.reason`: solo reanuda por térmica
  si la pausa fue realmente térmica; una pausa normal solo reanuda a 70%.
- Verificación posterior: con 79%, 78%, 77% y 76% siguió `input_suspend=1`, `Not charging`,
  sin microciclos. Debe reanudar al llegar a 70%.
- Hook de arranque validado; no hace falta otro reinicio solo por el guard.
- No se probó todavía un reinicio real del teléfono con el hook de arranque; el hook se ejecutó
  manualmente y funciona.

Esta medida reduce el tiempo a 100% y el calor, pero **no es una protección certificada** ni
sustituye cargador/cable/regleta en buen estado. Ante hinchazón, olor o calor anormal, detener
la carga y retirar el equipo.

## Pantalla del reloj — 2026-10-24

La pantalla sigue siempre encendida para mostrar la hora:

- **70%** mientras se interactúa con el teléfono.
- **10/255** después de **10 segundos** sin interacción. Se probaron 128, 80, 40 y 20; el usuario
  percibió 20 como ~50%, así que 10 es el nivel bajo solicitado.
- Al **llamar a Andrómeda** (wake, escucha, pensamiento o voz) sube a **179/255** y se mantiene
  mientras ella está activa; **20 segundos después** de terminar la voz vuelve a 10.
- **Mínimo** entre 23:00 y 06:00, respetando el modo descanso.
- La pantalla no se apaga; la voz y los servicios siguen funcionando.
- Verificado con estado de voz simulado: durante voz 179, a los 20 s 10; toque real 179→10.
- Confirmado por el usuario: el comportamiento funciona bien; bloque de pantalla cerrado.

Esto reduce consumo y calor, pero no sustituye la ventilación ni el guard de carga.
