# ============================================================
# Despliegue de la API de la TV LG al chroot del móvil (opción 2)
#
# Solo DESPLIEGA el bundle al chroot. NO arranca la API todavía
# (eso va en start-andromeda-resident.sh, ver PLAN §10.2).
#
# Uso: .\deploy-tv-api.ps1
# ============================================================

$bundle = "C:\Users\Usuario\Documents\tv\tools\phone_bundle"
$chroot = "/data/local/linux/debian-final"

Write-Host "== Desplegando bundle de la API TV ==" -ForegroundColor Cyan

# 1) Empujar los 4 archivos esenciales del bundle al móvil
foreach ($f in @("tv_api.py", "slopbro.py", "tvapi.key", "api_token.txt")) {
    adb push "$bundle\$f" "/data/local/tmp/$f" 2>&1 | Out-Null
}

# 2) Copiar al chroot (/opt/doha-voice/tv) + permisos
adb shell "su -c 'mkdir -p $chroot/opt/doha-voice/tv && cp /data/local/tmp/tv_api.py /data/local/tmp/slopbro.py /data/local/tmp/tvapi.key /data/local/tmp/api_token.txt $chroot/opt/doha-voice/tv/ && chown -R 10218:10218 $chroot/opt/doha-voice/tv && chmod 600 $chroot/opt/doha-voice/tv/tvapi.key $chroot/opt/doha-voice/tv/api_token.txt && chmod 640 $chroot/opt/doha-voice/tv/tv_api.py $chroot/opt/doha-voice/tv/slopbro.py'"

Write-Host "== Archivos en el chroot ==" -ForegroundColor Cyan
adb shell "su -c 'ls -la $chroot/opt/doha-voice/tv/'"

Write-Host "== Despliegue listo. Falta (cuando digas): ==" -ForegroundColor Yellow
Write-Host "  1) arrancar la API (PLAN §10.2) dentro del supervisor"
Write-Host "  2) verificar:  curl http://127.0.0.1:8770/health"
Write-Host "  3) verificar:  curl -H 'Authorization: Bearer <token>' http://127.0.0.1:8770/status"
