#!/system/bin/sh
# Explicit single voice trial. Server exists only during this bounded session.
set -eu
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
[ "$(id -u)" = 0 ] || exit 1
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1000000 ] && [ "$temp" -lt 380 ] || exit 1
if ss -lnt | grep -qE ':11435[[:space:]]'; then
  echo 'Port 11435 already in use; trial cancelled' >&2; exit 1
fi
umask 077
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/doha-warm-test.XXXXXX")
INNER=/tmp/${WORK##*/}
chown 10218:10218 "$WORK"
SUPERVISOR=''
debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin /usr/bin/python3 "$@"
}
cleanup() {
  touch "$WORK/DONE"
  if [ -n "$SUPERVISOR" ]; then wait "$SUPERVISOR" 2>/dev/null || true; fi
  rm -f "$WORK/key" "$WORK/DONE" "$WORK/server.pid" "$WORK/server.log" "$WORK/resources.txt"
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
started=$(date +%s)
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
su -g 10218 -G 3003 10218 -c "'$BASE/warm-server-worker.sh' '$WORK'" &
SUPERVISOR=$!
debian_user /opt/doha-voice/local-inference-client.py warmup "$INNER/key"
echo "model_preparation_seconds=$(($(date +%s) - started))"
rc=0
/data/local/doha-voice/voice-once.sh --warm-once "$WORK/key" || rc=$?
touch "$WORK/DONE"
wait "$SUPERVISOR" || rc=1
SUPERVISOR=''
cat "$WORK/resources.txt" 2>/dev/null || true
echo "trial_total_seconds=$(($(date +%s) - started))"
exit "$rc"
