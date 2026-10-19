#!/system/bin/sh
# Dedicated manual, bounded conversation-only trial. Never opens a microphone.
set -eu
[ "$(id -u)" = 0 ] || exit 1
MODE=${1:-pilot}
case "$MODE" in pilot|test) ;; *) exit 2 ;; esac
SECONDS_LIMIT=${2:-300}
case "$SECONDS_LIMIT" in 90|300) ;; *) exit 2 ;; esac
TRIAL=${3:-conversation}
case "$TRIAL" in conversation) TIMER_FLAG='' ;; timer) TIMER_FLAG='--timer-trial' ;; tools) TIMER_FLAG='--assistant-tools' ;; *) exit 2 ;; esac
[ "$#" -le 3 ] || exit 2
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
PRIVATE=/var/lib/andromeda-conversation
for name in server-cert.pem server-key.pem token; do
  [ -s "$ROOT$PRIVATE/$name" ] || { echo 'Missing private conversation credential/TLS setup' >&2; exit 1; }
done
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
debian_user /opt/doha-voice/pilot/prepare-conversation.py "$INNER/key" $TIMER_FLAG
if [ "$TRIAL" = tools ]; then
  echo 'Canal TLS de agenda y consultas web explicitas preparado; el microfono sigue apagado.'
elif [ "$TRIAL" = timer ]; then
  echo 'Prueba TLS de timer preparada: peticion completa autoriza; resultado Android y cierre. El microfono sigue apagado.'
else
  echo 'Conversacion LFM preparada, sin acciones. El microfono sigue apagado; elegir prueba en la app.'
fi
if [ "$MODE" = test ]; then
  debian_user /opt/doha-voice/pilot/doja_stream.py --key "$PRIVATE/token" \
    --seconds "$SECONDS_LIMIT" --test-wake --conversation-only --ai-key "$INNER/key" \
    --tls-cert "$PRIVATE/server-cert.pem" --tls-key "$PRIVATE/server-key.pem" --synthetic-input $TIMER_FLAG
else
  debian_user /opt/doha-voice/pilot/doja_stream.py --key "$PRIVATE/token" \
    --seconds "$SECONDS_LIMIT" --test-wake --conversation-only --ai-key "$INNER/key" \
    --tls-cert "$PRIVATE/server-cert.pem" --tls-key "$PRIVATE/server-key.pem" $TIMER_FLAG
fi
touch "$WORK/DONE"
wait "$SUPERVISOR"
SUPERVISOR=''
