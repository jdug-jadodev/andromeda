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
