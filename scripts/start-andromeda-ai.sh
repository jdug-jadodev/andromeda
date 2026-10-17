#!/system/bin/sh
# Root coordinates temporary local inference + voice backend. Never opens mic.
set -eu
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
[ "$(id -u)" = 0 ] || exit 1
MODE=${1:-pilot}
case "$MODE" in pilot|test) ;; *) exit 2 ;; esac
PROFILE=${2:-pilot}
MODEL_ID=lfm
KV_TYPE=f16
case "$PROFILE" in
  pilot|diagnostic) CONTEXT=1024 ;;
  chat) CONTEXT=2048 ;;
  qwen3) CONTEXT=2048; MODEL_ID=qwen3 ;;
  qwen17) CONTEXT=2048; MODEL_ID=qwen17 ;;
  controlled) CONTEXT=2048; MODEL_ID=qwen17 ;;
  ablation) CONTEXT=2048; MODEL_ID=qwen17 ;;
  cache_isolation) CONTEXT=2048; MODEL_ID=qwen17 ;;
  cache_fp32) CONTEXT=2048; MODEL_ID=qwen17; KV_TYPE=f32 ;;
  *) exit 2 ;;
esac
if [ "$MODE" != test ]; then
  case "$PROFILE" in controlled|ablation|cache_isolation|cache_fp32) exit 2 ;; esac
fi
ZONE=$(getprop persist.sys.timezone)
case "$ZONE" in ''|*[!a-zA-Z0-9_+/-]*) echo 'Invalid Android timezone' >&2; exit 1 ;; esac
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1000000 ] && [ "$temp" -lt 380 ] || exit 1
if ss -lnt | grep -qE ':(11435|11436)[[:space:]]'; then
  echo 'Local voice/inference port busy; refusing replacement' >&2; exit 1
fi
umask 077
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/doha-warm-test.XXXXXX")
INNER=/tmp/${WORK##*/}
chown 10218:10218 "$WORK"
SUPERVISOR=''
debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin TZ="$ZONE" ANDROMEDA_MODEL="$MODEL_ID" ANDROMEDA_KV_TYPE="$KV_TYPE" /opt/doha-voice/bin/python "$@"
}
cleanup() {
  touch "$WORK/DONE"
  if [ -n "$SUPERVISOR" ]; then wait "$SUPERVISOR" 2>/dev/null || true; fi
  cat "$WORK/resources.txt" 2>/dev/null || true
  rm -f "$WORK/key" "$WORK/DONE" "$WORK/server.pid" "$WORK/server.log" "$WORK/resources.txt"
  for name in ai_create ai_cancel ai_confirm ai_chat; do
    rm -f "$WORK/andromeda_fixture_$name.wav"
    if [ "$MODE" = test ]; then
      rm -f "/data/user/0/local.doja.voice/files/synthetic/andromeda_fixture_$name.wav"
    fi
  done
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
su -g 10218 -G 3003 10218 -c "'$BASE/warm-server-worker.sh' '$WORK' 420 '$CONTEXT' '$MODEL_ID' '$KV_TYPE'" &
SUPERVISOR=$!
if [ "$PROFILE" = cache_isolation ] || [ "$PROFILE" = cache_fp32 ]; then
  debian_user /opt/doha-voice/pilot/evaluate-cache-isolation.py "$INNER/key"
elif [ "$PROFILE" = ablation ]; then
  debian_user /opt/doha-voice/pilot/evaluate-contract-ablation.py "$INNER/key"
elif [ "$PROFILE" = controlled ]; then
  debian_user /opt/doha-voice/pilot/evaluate-model-controlled.py "$INNER/key"
elif [ "$PROFILE" = diagnostic ]; then
  debian_user /opt/doha-voice/pilot/probe_inference_protocol.py "$INNER/key"
elif [ "$PROFILE" = chat ] || [ "$PROFILE" = qwen3 ] || [ "$PROFILE" = qwen17 ]; then
  debian_user /opt/doha-voice/pilot/probe_chat_agent.py "$INNER/key" "$MODE"
else
  debian_user /opt/doha-voice/pilot/probe_local_ai.py "$INNER/key" "$MODE"
fi
if [ "$MODE" = test ] && [ "$PROFILE" = pilot ]; then
  for name in ai_create ai_cancel ai_confirm ai_chat; do
    cp "/data/user/0/local.doja.voice/files/synthetic/andromeda_fixture_$name.wav" "$WORK/"
    chown 10218:10218 "$WORK/andromeda_fixture_$name.wav"
    chmod 600 "$WORK/andromeda_fixture_$name.wav"
  done
  debian_user /opt/doha-voice/pilot/probe_stream_ai.py "$INNER/key" "$INNER"
fi
rc=0
if [ "$MODE" = pilot ]; then
  echo 'IA local preparada. Elegir cinco minutos en la app. Micrófono aún apagado.'
  if [ "$PROFILE" = chat ] || [ "$PROFILE" = qwen3 ] || [ "$PROFILE" = qwen17 ]; then
    debian_user /opt/doha-voice/pilot/doja_stream.py \
      --key /var/lib/doja-voice-pilot/token --seconds 300 --test-wake --ai-key "$INNER/key" --chat || rc=$?
  else
    debian_user /opt/doha-voice/pilot/doja_stream.py \
      --key /var/lib/doja-voice-pilot/token --seconds 300 --test-wake --ai-key "$INNER/key" || rc=$?
  fi
fi
touch "$WORK/DONE"
wait "$SUPERVISOR" || rc=1
SUPERVISOR=''
exit "$rc"
