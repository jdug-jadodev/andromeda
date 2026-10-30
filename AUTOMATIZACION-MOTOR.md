# Automatización de Andrómeda (motor)

## Problema que resolvemos

Con el teléfono 24/7, la RAM se degrada con los días: el modelo (LFM 1.2B, ~740 MB) se "sale" de la RAM — los pesos están mapeados desde el `.gguf` y el kernel puede evacuarlos. Al releerlos del flash, el calentamiento (prefill) se vuelve lento y el arranque da `TimeoutError`.

**Confirmado:** un reinicio del teléfono lo arregla (limpia RAM + procesos de fondo). El código no cambió; era el estado del teléfono.

## Decisión

- **Recarga del motor cada noche** (~4 AM): matar + arrancar el motor. Libera la RAM del modelo.
- **Reinicio completo del teléfono cada semana**: limpia RAM + procesos de fondo.
- **Auto-arranque del motor al encender** (boot hook), para que tras el reinicio el motor quede corriendo solo.

## Estado

**Pendiente de implementar** (esperando luz verde).

## Qué falta hacer

1. Script que mata + arranca el motor (recarga diaria).
2. Boot hook de Magisk (`service.d`) para auto-arrancar el motor al encender.
3. `crond` (busybox Magisk) con dos jobs: diario (recargar) y semanal (reboot).
4. Probar el ciclo completo.

## Voz (pendiente, no urgente)

La app NO reconecta sola (`VoiceConnection.java`: *"no repetiré la conexión automáticamente"*). Tras un reinicio la voz queda "apagada" hasta tocar "Activar Andrómeda".

Se puede automatizar (que la app reconecte sola), pero es un cambio en la app (área visual de Claude). **Diferido por decisión del usuario.**

## Datos técnicos

- `crond`/`crontab`: disponibles en `/data/adb/magisk/busybox`.
- El chroot (`/data/local/linux/debian-final`) NO tiene cron.
- Motor: `start-andromeda-resident.sh continuous-voice guided alarms`.
- Arranque manual desde PC: `.\start-ia.ps1`.
- Límites de arranque: `preparation_ms = 45000` (por defecto), pero `enable_reminder_clarification()` lo sube a `55000` para el modo agenda+alarmas.
- Guardián del modelo: `resident-model-worker.sh` mata el `llama-server` si no hay `metrics.json` en 90s (constraint para subir el presupuesto).
