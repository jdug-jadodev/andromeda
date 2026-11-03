# ============================================================
# Andrómeda — configurar la API key de DeepSeek (fallback nube)
#
# Uso:
#   .\set-deepseek-key.ps1                    # te pide la key
#   .\set-deepseek-key.ps1 -Key "sk-..."      # la pasas por parámetro
#   .\set-deepseek-key.ps1 -NoRestart         # no reinicia el motor
#   .\set-deepseek-key.ps1 -Remove            # quita la key (vuelve a solo-determinista)
#
# Escribe la key en el teléfono en:
#   /data/local/linux/debian-final/opt/doha-voice/deepseek/api_key.txt
# con permisos 600 y owner 10218 (el supervisor la inyecta como DEEPSEEK_API_KEY).
# La key viaja en base64 para no quedar en texto plano en el argv de adb.
# ============================================================

param(
    [string]$Key = "",
    [switch]$NoRestart,
    [switch]$Remove
)

$SERIAL = "ZY227BCQ85"  # Teléfono Andrómeda (Moto G8 Plus)
$DIR = "/data/local/linux/debian-final/opt/doha-voice/deepseek"
$FILE = "$DIR/api_key.txt"

if ($Remove) {
    Write-Host "Quitando la key de DeepSeek..." -ForegroundColor Yellow
    adb -s $SERIAL shell "su -c 'rm -f $FILE'" 2>&1 | Out-Null
    Write-Host "Key eliminada. Andrómeda vuelve a solo-determinista." -ForegroundColor Green
    if (-not $NoRestart) { & "$PSScriptRoot\start-ia.ps1" }
    exit 0
}

if (-not $Key) {
    $Key = Read-Host "Pega tu API key de DeepSeek (empieza por sk-)"
}
$Key = $Key.Trim()
if ($Key -notmatch '^sk-[A-Za-z0-9_-]{10,}$') {
    Write-Host "La key no parece válida: debe empezar por 'sk-' y tener al menos 10 caracteres más." -ForegroundColor Red
    exit 1
}

Write-Host "Escribiendo la key en el teléfono..." -ForegroundColor Cyan
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Key + "`n"))
adb -s $SERIAL shell "su -c 'mkdir -p $DIR && echo $b64 | /data/adb/magisk/busybox base64 -d > $FILE && chown 10218:10218 $FILE && chmod 600 $FILE'" 2>&1 | Out-Null

# Verificar que quedó bien (sin imprimir la key).
Write-Host "Verificando..." -ForegroundColor Cyan
$check = adb -s $SERIAL shell "su -c 'ls -la $FILE'" 2>&1
Write-Host $check

if ($check -match 'api_key\.txt') {
    Write-Host "Key configurada correctamente." -ForegroundColor Green
} else {
    Write-Host "No se pudo verificar el archivo. Revisa la conexión adb." -ForegroundColor Red
    exit 1
}

if (-not $NoRestart) {
    Write-Host "Reiniciando el motor..." -ForegroundColor Cyan
    & "$PSScriptRoot\start-ia.ps1"
}
Write-Host "Listo. DeepSeek queda activo como fallback del router determinista." -ForegroundColor Green
