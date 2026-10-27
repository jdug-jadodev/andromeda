#!/system/bin/sh
# Root charge guard: pause at 80%, resume at 70%, thermal pause at 40 C.
# Reversible: create /data/adb/doha-battery/CHARGE_GUARD_DISABLED and wait one cycle.
# This is not a certified safety cutoff; it only toggles input_suspend.
BASE=/data/adb/doha-battery
BAT=/sys/class/power_supply/battery
NODE=$BAT/input_suspend
DISABLED=$BASE/CHARGE_GUARD_DISABLED
LOCK=$BASE/charge-guard.lock
PID=$BASE/charge-guard.pid
HEARTBEAT=$BASE/charge-guard.heartbeat
REASON=$BASE/charge-guard.reason
LOG=$BASE/charge-guard.log
MAX=80
MIN=70
HOT=400
COOL=380

read_value() {
  if [ -r "$1" ]; then cat "$1" 2>/dev/null; else printf 'unknown\n'; fi
}
numeric() {
  case "$1" in ''|*[!0-9-]*) return 1 ;; *) return 0 ;; esac
}
rotate_log() {
  if [ -f "$LOG" ]; then
    size=$(wc -c < "$LOG" 2>/dev/null || echo 0)
    [ "$size" -lt 262144 ] || mv -f "$LOG" "$BASE/charge-guard.previous.log"
  fi
}
write_action() {
  action=$1
  cap=$(read_value "$BAT/capacity")
  temp=$(read_value "$BAT/temp")
  status=$(read_value "$BAT/status")
  suspended=$(read_value "$NODE")
  printf '%s epoch=%s capacity=%s temp_deci_c=%s status=%s input_suspend=%s action=%s\n' \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$(date +%s)" "$cap" "$temp" "$status" "$suspended" "$action" >> "$LOG"
}
restore() {
  echo 0 > "$NODE" 2>/dev/null
  write_action EXIT_RESTORE 2>/dev/null
  rm -f "$PID" "$HEARTBEAT" "$REASON"
}

if [ "${1:---loop}" = --loop ]; then
  exec /data/adb/magisk/busybox flock -n "$LOCK" /system/bin/sh "$0" --locked
fi
[ "${1:---loop}" = --locked ] || exit 2
umask 077
mkdir -p "$BASE" || exit 1
chmod 700 "$BASE" || exit 1
[ -w "$NODE" ] || { write_action NODE_UNAVAILABLE; exit 1; }
echo $$ > "$PID"
trap restore EXIT INT TERM HUP

while :; do
  if [ -f "$DISABLED" ]; then
    write_action DISABLED
    restore
    exit 0
  fi
  cap=$(read_value "$BAT/capacity")
  temp=$(read_value "$BAT/temp")
  suspended=$(read_value "$NODE")
  action=NONE
  if numeric "$temp" && numeric "$cap" && { [ "$suspended" = 0 ] || [ "$suspended" = 1 ]; }; then
    reason=$(cat "$REASON" 2>/dev/null || echo '')
    if [ "$temp" -ge "$HOT" ] && [ "$suspended" = 0 ]; then
      echo 1 > "$NODE" 2>/dev/null && { echo thermal > "$REASON"; action=THERMAL_PAUSE; }
    elif [ "$cap" -ge "$MAX" ] && [ "$suspended" = 0 ]; then
      echo 1 > "$NODE" 2>/dev/null && { echo normal > "$REASON"; action=PAUSE; }
    elif [ "$cap" -le "$MIN" ] && [ "$suspended" = 1 ]; then
      echo 0 > "$NODE" 2>/dev/null && { rm -f "$REASON"; action=RESUME; }
    elif [ "$suspended" = 1 ] && [ "$temp" -le "$COOL" ] && [ "$cap" -lt "$MAX" ] \
            && [ "$reason" = thermal ]; then
      # Resume only from a real thermal pause, never from the normal 80% pause.
      echo 0 > "$NODE" 2>/dev/null && { rm -f "$REASON"; action=THERMAL_RESUME; }
    fi
  else
    action=INVALID_SENSOR
  fi
  rotate_log
  write_action "$action"
  date +%s > "$HEARTBEAT"
  sleep 60
done
