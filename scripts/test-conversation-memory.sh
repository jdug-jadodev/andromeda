#!/system/bin/sh
# One LFM-only public text experiment. No PCM endpoint, Vosk, TTS or microphone.
set -eu
[ "$(id -u)" = 0 ] || exit 1
MODE=${1:-test}
case "$MODE" in test) EVALUATOR=evaluate-conversation-memory.py ;; structured) EVALUATOR=evaluate-structured-context.py ;; *) exit 2 ;; esac
[ "$#" -le 1 ] || exit 2
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
if ss -lnt | grep -qE ':(11435|11436)[[:space:]]'; then
  echo 'Voice/inference port busy; refusing replacement' >&2; exit 1
fi
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1200000 ] && [ "$temp" -lt 380 ] || exit 1
umask 077
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/doha-warm-test.XXXXXX")
INNER=/tmp/${WORK##*/}
chown 10218:10218 "$WORK"
SUPERVISOR=''
debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin ANDROMEDA_MODEL=lfm \
    /opt/doha-voice/bin/python "$@"
}
cleanup() {
  touch "$WORK/DONE"
  if [ -n "$SUPERVISOR" ]; then wait "$SUPERVISOR" 2>/dev/null || true; fi
  cat "$WORK/resources.txt" 2>/dev/null || true
  rm -f "$WORK/key" "$WORK/DONE" "$WORK/server.pid" "$WORK/server.log" "$WORK/resources.txt"
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
su -g 10218 -G 3003 10218 -c "'$BASE/warm-server-worker.sh' '$WORK' 660 2048 lfm f16 advanced 0 voice" &
SUPERVISOR=$!
debian_user /opt/doha-voice/pilot/prepare-conversation.py "$INNER/key"
debian_user "/opt/doha-voice/pilot/$EVALUATOR" "$INNER/key"
touch "$WORK/DONE"
wait "$SUPERVISOR"
SUPERVISOR=''
