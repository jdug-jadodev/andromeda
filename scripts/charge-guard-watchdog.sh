#!/system/bin/sh
# Independent fail-open watchdog for charge-guard.sh. The heartbeat is the
# source of truth: while it is fresh the guard is alive, even if the PID file
# is missing. Only a missing or stale heartbeat restores charging and restarts
# the guard. Never resume charging just because a PID file vanished.
BASE=/data/adb/doha-battery
NODE=/sys/class/power_supply/battery/input_suspend
DISABLED=$BASE/CHARGE_GUARD_DISABLED
LOCK=$BASE/charge-guard-watchdog.lock
PID=$BASE/charge-guard.pid
HEARTBEAT=$BASE/charge-guard.heartbeat
REASON=$BASE/charge-guard.reason
LOG=$BASE/charge-guard.log
MAX_AGE=180

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
is_guard() {
  pid=$1
  [ "$pid" -gt 0 ] 2>/dev/null || return 1
  [ -r "/proc/$pid/cmdline" ] || return 1
  case "$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null)" in
    *charge-guard.sh*) return 0 ;;
  esac
  return 1
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
  numeric_hb=0
  case "$heartbeat" in ''|*[!0-9]*) numeric_hb=0 ;; *) numeric_hb=1 ;; esac
  healthy=0
  if [ "$numeric_hb" = 1 ] && [ $((now - heartbeat)) -le "$MAX_AGE" ]; then
    healthy=1
  fi
  if [ "$healthy" = 0 ]; then
    if is_guard "$guard_pid"; then
      kill "$guard_pid" 2>/dev/null
      sleep 1
    fi
    resume
    rm -f "$REASON"
    log WATCHDOG_RESUME
    start_guard
    sleep 60
  fi
  sleep 30
done
