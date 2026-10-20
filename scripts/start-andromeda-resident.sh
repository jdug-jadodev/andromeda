#!/system/bin/sh
# Explicit model supervisor: bounded trials or opt-in continuous-model. No mic start.
set -eu
[ "$(id -u)" = 0 ] && [ "$#" -ge 1 ] && [ "$#" -le 3 ] || exit 2
LIMIT=$1
VOICE=''
case "$LIMIT" in
  900|1800) WORKER_MODE=bounded; CONTINUOUS='' ;;
  continuous-model) WORKER_MODE=continuous; CONTINUOUS='--continuous-model' ;;
  continuous-voice) WORKER_MODE=continuous; CONTINUOUS='--continuous-model'; VOICE='--continuous-voice' ;;
  *) exit 2 ;;
esac
if [ -n "$VOICE" ]; then
  [ "${2:-guided}" = guided ] || exit 2
  GUIDED='--guided-agenda'
else
  case "${2:-classic}" in classic) GUIDED='' ;; guided) GUIDED='--guided-agenda' ;; *) exit 2 ;; esac
fi
ALARMS=''
case "${3:-classic}" in
  classic) ;;
  alarms) [ -n "$VOICE" ] && [ -n "$GUIDED" ] || exit 2; ALARMS='--alarm-lifecycle' ;;
  *) exit 2 ;;
esac
ROOT=/data/local/linux/debian-final
LOCK=/data/local/tmp/andromeda-resident.lock
debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin ANDROMEDA_MODEL=lfm \
    /opt/doha-voice/bin/python -B "$@"
}
if ss -lnt | grep -qE ':(11435|11436)[[:space:]]'; then exit 1; fi
if dumpsys activity services local.doja.voice | grep -q ServiceRecord; then exit 1; fi
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1200000 ] && [ "$temp" -lt 380 ] || exit 1
umask 077
mkdir -m 700 "$LOCK" # Existing/stale/unknown lock is NEVER removed or replaced.
WORK=''
INNER=''
WORK_INODE=''
SUPERVISOR=''
cleanup() {
  if [ -n "$WORK" ]; then
    debian_user -c 'import sys; sys.path.insert(0,"/opt/doha-voice/pilot"); from resident_runtime import request_stop; request_stop(sys.argv[1])' "$INNER" 2>/dev/null || true
    if [ -n "$SUPERVISOR" ]; then wait "$SUPERVISOR" 2>/dev/null || true; fi
    debian_user -c 'import sys; sys.path.insert(0,"/opt/doha-voice/pilot"); from resident_runtime import cleanup_credentials; cleanup_credentials(sys.argv[1],int(sys.argv[2]))' "$INNER" "$WORK_INODE" 2>/dev/null || true
    # Retain only the bounded, numeric, private metrics.json for later collection.
    rmdir "$WORK" 2>/dev/null || true
  fi
  rm -f "$LOCK/work" "$LOCK/pid"
  rmdir "$LOCK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/andromeda-resident.XXXXXX")
INNER=/tmp/${WORK##*/}
WORK_INODE=$(stat -c '%i' "$WORK")
echo "$WORK" > "$LOCK/work"
echo "$$" > "$LOCK/pid"
if [ "$WORKER_MODE" = bounded ]; then
  awk -v limit="$LIMIT" '{printf "%.0f\n", ($1+limit)*1000}' /proc/uptime > "$WORK/deadline_ms"
  chown 10218:10218 "$WORK/deadline_ms"
else
  printf 'continuous_model_v1\n' > "$WORK/mode"
  chown 10218:10218 "$WORK/mode"
fi
chown 10218:10218 "$WORK"
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
su -g 10218 -G 3003 10218 -c "'/system/bin/env' -i PATH=/system/bin:/system/xbin HOME=/data/local/tmp '/data/local/doha-inference/resident-model-worker.sh' '$WORK' '$WORKER_MODE'" &
SUPERVISOR=$!
debian_user /opt/doha-voice/pilot/prepare-conversation.py "$INNER/key" --assistant-tools $GUIDED $ALARMS >/dev/null 2>&1
# No raw backend/model output is saved. Numeric monitoring has its own schema.
status=0
debian_user /opt/doha-voice/pilot/resident_voice.py --work "$INNER" $GUIDED $CONTINUOUS $VOICE $ALARMS >/dev/null 2>&1 || status=$?
debian_user -c 'import sys; sys.path.insert(0,"/opt/doha-voice/pilot"); from resident_runtime import request_stop; request_stop(sys.argv[1])' "$INNER"
wait "$SUPERVISOR" || true # Backend already verified/stopped its owned model.
SUPERVISOR=''
exit "$status"
