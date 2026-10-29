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

# 1) Si ya hay un motor corriendo, lo detenemos para arrancar limpio.
$procs = adb shell "su -c 'ps -A'" 2>$null
if ($procs -match 'llama-server') {
    Write-Host "Motor activo detectado; deteniendolo..." -ForegroundColor Yellow
    $lockPid = (adb shell "su -c 'cat /data/local/tmp/andromeda-resident.lock/pid'" 2>$null).Trim()
    if ($lockPid -match '^\d+$') { adb shell "su -c 'kill -TERM $lockPid'" 2>&1 | Out-Null }
    adb shell "su -c 'pkill -f llama-server; pkill -f resident_voice; pkill -f start-andromeda-resident'" 2>&1 | Out-Null
    Start-Sleep -Seconds 6
}

# 2) Arrancar el motor. Usamos Start-Process (retorna al instante) para
#    no quedarnos colgados esperando al proceso de fondo.
Write-Host "Arrancando el motor..." -ForegroundColor Cyan
$suCmd = "su -c 'cd /data/local/doha-voice && setsid nohup /system/bin/sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms >/data/local/tmp/andromeda-start.log 2>&1 < /dev/null &'"
Start-Process -FilePath "adb" -ArgumentList @("shell", $suCmd) -WindowStyle Hidden | Out-Null

# 3) Esperar a que cargue el modelo (~60-70 s).
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
