# ============================================================
# Andrómeda — arrancar el motor (IA local)
#
# Cómo usarlo: abre PowerShell en esta carpeta y ejecuta:
#     .\start-ia.ps1
#
# Qué hace: detiene el motor si está corriendo, lo arranca de
# nuevo y espera a que cargue el modelo (~60-70 s).
# ============================================================

$ErrorActionPreference = 'Continue'

Write-Host "== Andrómeda: arrancando el motor ==" -ForegroundColor Cyan

# 1) Si ya hay un motor corriendo, lo detenemos para arrancar limpio.
$procs = adb shell "su -c 'ps -A'" 2>$null
$running = ($procs | Select-String 'llama-server|start-andromeda-resident')
if ($running) {
    Write-Host "Motor activo detectado; deteniéndolo..." -ForegroundColor Yellow
    # Mata el supervisor (sh), que dispara la limpieza del modelo y el backend.
    $lockPid = (adb shell "su -c 'cat /data/local/tmp/andromeda-resident.lock/pid'").Trim()
    if ($lockPid -and $lockPid -match '^\d+$') {
        adb shell "su -c 'kill -TERM $lockPid'" | Out-Null
    }
    # Por si acaso, mata directamente los procesos del modelo y del backend.
    adb shell "su -c 'pkill -f llama-server'" | Out-Null
    adb shell "su -c 'pkill -f resident_voice'" | Out-Null
    adb shell "su -c 'pkill -f start-andromeda-resident'" | Out-Null
    Start-Sleep -Seconds 6
}

# 2) Arrancar el motor.
Write-Host "Arrancando el motor..." -ForegroundColor Cyan
adb shell "su -c 'cd /data/local/doha-voice && setsid nohup /system/bin/sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms >/data/local/tmp/andromeda-start.log 2>&1 < /dev/null &'" | Out-Null

# 3) Esperar a que cargue el modelo (tarda ~60-70 s).
Write-Host "Esperando a que cargue el modelo (~70 s)..." -ForegroundColor Cyan
$ok = $false
for ($i = 1; $i -le 18; $i++) {
    Start-Sleep -Seconds 5
    $p = adb shell "su -c 'cat /proc/net/tcp'" 2>$null
    if ($p -match '2CAB' -and $p -match '2CAC') { $ok = $true; break }
}

if ($ok) {
    Write-Host "OK: motor arriba (puertos 11435/11436). Toca 'Activar Andromeda' en la app." -ForegroundColor Green
} else {
    Write-Host "No levanto a tiempo. Revisa el log:" -ForegroundColor Red
    Write-Host "  adb shell su -c 'cat /data/local/tmp/andromeda-start.log'" -ForegroundColor Gray
}
