#!/system/bin/sh
# Arranca la API de la TV LG dentro del chroot (para prueba manual).
set -eu
ROOT=/data/local/linux/debian-final
TOKEN=$(cat "$ROOT/opt/doha-voice/tv/api_token.txt")
cd /data/local/doha-voice
setsid nohup chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
  /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin \
  TV_IP=192.168.1.9 TV_MAC=8C:83:94:0D:F9:B4 TV_API_TOKEN="$TOKEN" \
  TV_DEFAULT_OUTPUT=external_optical TV_WATCHDOG_SECONDS=120 \
  /opt/doha-voice/bin/python -B /opt/doha-voice/tv/tv_api.py serve \
  >/data/local/tmp/tvapi.log 2>&1 < /dev/null &
echo "API TV arrancada (pid=$!)"
