# ============================================================
# Andrómeda — arrancar el motor (IA local)
#
# Uso: abre PowerShell en esta carpeta y ejecuta:
#     .\start-ia.ps1
#
# Detiene el motor si está corriendo, lo arranca de nuevo y
# espera a que cargue el modelo (~60-70 s).
# ============================================================

Write-Host "== Andromeda: arrancando el motor ==" -ForegroundColor Cyan
$SERIAL = "ZY227BCQ85"  # Teléfono Andrómeda (Moto G8 Plus)

# 0) Montar /proc, /sys, /dev y /run del chroot. Se pierden al reiniciar el
#    teléfono y sin ellos el backend no puede verificar el proceso del modelo.
Write-Host "Montando /proc, /sys, /dev del chroot..." -ForegroundColor Cyan
adb -s $SERIAL shell "su -c 'sh /data/local/linux/scripts/mount-debian.sh'" 2>&1 | Out-Null

# 0b) Reiniciar el guard de batería (pausa a 80%, reanuda a 70%) si no está
#     corriendo. El hook de Magisk lo arranca al boot, pero conviene reasegurarlo.
Write-Host "Reasegurando guard de batería..." -ForegroundColor Cyan
$guardJob = Start-Job -ScriptBlock {
    adb -s $using:SERIAL shell "su -c 'sh /data/adb/service.d/99-doha-charge-guard.sh >/dev/null 2>&1'"
}
Start-Sleep -Seconds 4
Remove-Job -Job $guardJob -Force -ErrorAction SilentlyContinue

# 1) Si ya hay un motor corriendo, lo detenemos para arrancar limpio.
#    La señal fiable es el lock del supervisor (el backend corre como `python`).
$lockOut = adb -s $SERIAL shell "su -c 'cat /data/local/tmp/andromeda-resident.lock/pid'" 2>$null
$lockPid = if ($lockOut) { $lockOut.Trim() } else { $null }
if ($lockPid -match '^\d+$') {
    Write-Host "Motor activo detectado; deteniendolo..." -ForegroundColor Yellow
    adb -s $SERIAL shell "su -c 'kill -TERM $lockPid 2>/dev/null'" 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    # El supervisor queda bloqueado en el comando foreground resident_voice.py, así
    # que su trap de TERM no corre hasta que el backend muere. Matamos el backend a
    # fondo (SIGKILL) y limpiamos el lock obsoleto antes de arrancar limpio.
    adb -s $SERIAL shell "su -c 'pkill -9 -f resident_voice; pkill -9 -f tv_api.py; pkill -9 -f start-andromeda-resident; pkill -9 -f llama-server'" 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    adb -s $SERIAL shell "su -c 'rm -rf /data/local/tmp/andromeda-resident.lock'" 2>&1 | Out-Null
}

# 2) Arrancar el motor en un job de fondo. El `adb -s $SERIAL shell` se queda "colgado"
#    porque el proceso de fondo retiene su stdout, pero el job corre aparte y
#    no nos bloquea; el motor arranca igual (setsid nohup lo desacopla).
Write-Host "Arrancando el motor..." -ForegroundColor Cyan
$job = Start-Job -ScriptBlock {
    adb -s $using:SERIAL shell "su -c 'cd /data/local/doha-voice && setsid nohup /system/bin/sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms >/data/local/tmp/andromeda-start.log 2>&1 < /dev/null &'"
}

# 3) Esperar a que arranque el backend (ya no se carga un modelo local).
Write-Host "Esperando a que arranque el backend..." -ForegroundColor Cyan
$ok = $false
for ($i = 1; $i -le 20; $i++) {
    Start-Sleep -Seconds 3
    $p = adb -s $SERIAL shell "su -c 'cat /proc/net/tcp'" 2>$null
    if ($p -match '2CAC') { $ok = $true; break }
}

# Limpiar el job de arranque (su `adb -s $SERIAL shell` queda colgado; el motor ya está
# desacoplado con setsid, así que matar el cliente adb no lo afecta).
Remove-Job -Job $job -Force -ErrorAction SilentlyContinue

if ($ok) {
    Write-Host "OK: motor arriba (puerto 11436). Toca 'Activar Andromeda' en la app." -ForegroundColor Green
} else {
    Write-Host "No levanto a tiempo. Revisa el log:" -ForegroundColor Red
    Write-Host "  adb -s $SERIAL shell su -c 'cat /data/local/tmp/andromeda-start.log'" -ForegroundColor Gray
}
