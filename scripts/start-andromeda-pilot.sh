#!/system/bin/sh
# Explicit bounded pilot only. Does not start microphone, boot hooks or a permanent daemon.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run this coordinator through su' >&2; exit 1; }
ROOT=/data/local/linux/debian-final
SECONDS_LIMIT=${1:-90}
case "$SECONDS_LIMIT" in
  90|300) ;;
  *) echo 'Choose 90 or 300 seconds' >&2; exit 1 ;;
esac
if ss -lnt | grep -qE ':11436[[:space:]]'; then
  echo 'Port 11436 is already in use; not replacing its service' >&2
  exit 1
fi
exec chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
  /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin \
  /opt/doha-voice/bin/python /opt/doha-voice/pilot/doja_stream.py \
   --key /var/lib/doja-voice-pilot/token --seconds "$SECONDS_LIMIT" --test-wake
