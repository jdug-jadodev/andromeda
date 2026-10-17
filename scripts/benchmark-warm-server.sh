#!/system/bin/sh
# Root coordinates a bounded non-root benchmark, then removes private temporaries.
set -eu
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
[ "$(id -u)" = 0 ] || exit 1
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1000000 ] && [ "$temp" -lt 380 ] || exit 1
# Refuse to replace an existing listener. A concurrent bind also fails safely.
if ss -lnt | grep -qE ':11435[[:space:]]'; then
  echo 'Port 11435 is already in use; benchmark cancelled' >&2; exit 1
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
  rm -f "$WORK/key" "$WORK/DONE" "$WORK/server.pid" "$WORK/server.log" \
    "$WORK/resources.txt" "$WORK/client-report.txt" "$WORK/prompt.txt" "$WORK/response.raw"
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
su -g 10218 -G 3003 10218 -c "'$BASE/warm-server-worker.sh' '$WORK'" &
SUPERVISOR=$!
rc=0
debian_user /opt/doha-voice/benchmark-warm-client.py run "$INNER" > "$WORK/client-report.txt" || rc=$?
if [ "$rc" = 0 ]; then
  debian_user /opt/doha-voice/voice-turn.py sample-prompt "$INNER/prompt.txt"
  debian_user /opt/doha-voice/local-inference-client.py infer "$INNER/key" \
    "$INNER/prompt.txt" "$INNER/response.raw" >> "$WORK/client-report.txt" || rc=$?
fi
touch "$WORK/DONE"
wait "$SUPERVISOR" || rc=1
SUPERVISOR=''
cat "$WORK/client-report.txt"
cat "$WORK/resources.txt" 2>/dev/null || true
if [ "$rc" -ne 0 ]; then tail -n 10 "$WORK/server.log"; fi
exit "$rc"
