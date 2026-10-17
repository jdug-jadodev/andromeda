#!/system/bin/sh
# Explicit bounded wake/ASR only. Does not open mic, load an LLM or schedule actions.
set -eu
[ "$(id -u)" = 0 ] || exit 1
ROOT=/data/local/linux/debian-final
SECONDS_LIMIT=${1:-300}
case "$SECONDS_LIMIT" in 90|300) ;; *) exit 2 ;; esac
if ss -lnt | grep -qE ':(11435|11436)[[:space:]]'; then
  echo 'Voice/inference port busy; refusing replacement' >&2
  exit 1
fi
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 750000 ] && [ "$temp" -lt 380 ] || exit 1
exec chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
  /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin \
  /opt/doha-voice/bin/python /opt/doha-voice/pilot/doja_stream.py \
  --key /var/lib/doja-voice-pilot/token --seconds "$SECONDS_LIMIT" --test-wake --wake-only
