#!/system/bin/sh
# Independent fail-open watchdog for charge-guard.sh. If the guard dies or its
# heartbeat is stale, restore charging and try to restart the guard.
BASE=/data/adb/doha-battery
NODE=/sys/class/power_supply/battery/input_suspend
DISABLED=$BASE/CHARGE_GUARD_DISABLED
LOCK=$BASE/charge-guard-watchdog.lock
PID=$BASE/charge-guard.pid
HEARTBEAT=$BASE/charge-guard.heartbeat
LOG=$BASE/charge-guard.log
MAX_AGE=300

log() {
  printf '%s epoch=%s action=%s input_suspend=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    "$(date +%s)" "$1" "$(cat "$NODE" 2>/dev/null || echo unknown)" >> "$LOG"
}
resume() {
  echo 0 > "$NODE" 2>/dev/null
}
start_guard() {
  if [ -x "$BASE/charge-guard.sh" ]; then
    "$BASE/charge-guard.sh" --loop >/dev/null 2>&1 &
  fi
}

if [ "${1:---loop}" = --loop ]; then
  exec /data/adb/magisk/busybox flock -n "$LOCK" /system/bin/sh "$0" --locked
fi
[ "${1:---loop}" = --locked ] || exit 2
umask 077
sleep 5

while :; do
  if [ -f "$DISABLED" ]; then
    resume
    log DISABLED_RESTORE
    exit 0
  fi
  now=$(date +%s)
  heartbeat=$(cat "$HEARTBEAT" 2>/dev/null || echo 0)
  guard_pid=$(cat "$PID" 2>/dev/null || echo 0)
  alive=0
  if [ "$guard_pid" -gt 0 ] 2>/dev/null && kill -0 "$guard_pid" 2>/dev/null; then
    alive=1
  fi
  stale=0
  if [ "$heartbeat" -gt 0 ] 2>/dev/null && [ $((now - heartbeat)) -gt "$MAX_AGE" ]; then
    stale=1
  fi
  if [ "$alive" = 0 ] || [ "$stale" = 1 ]; then
    if [ "$guard_pid" -gt 0 ] 2>/dev/null && [ "$alive" = 1 ]; then
      kill "$guard_pid" 2>/dev/null
      sleep 1
    fi
    resume
    log WATCHDOG_RESUME
    start_guard
    sleep 60
  fi
  sleep 60
done
