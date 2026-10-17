#!/system/bin/sh
# Read-only battery observations. This is NOT a safety cutoff.
BASE=/data/adb/doha-battery
BAT=/sys/class/power_supply/battery
BMS=/sys/class/power_supply/bms

read_value() {
  if [ -r "$1" ]; then
    cat "$1" 2>/dev/null
  else
    printf 'unknown\n'
  fi
}

snapshot() {
  temp=$(read_value "$BAT/temp")
  health=$(read_value "$BAT/health")
  advisory=normal
  case "$temp" in
    ''|*[!0-9-]*) advisory=sensor_unavailable ;;
    *) [ "$temp" -ge 400 ] && advisory=warm_review ;;
  esac
  [ "$health" = Good ] || advisory=health_review
  printf '%s epoch=%s capacity=%s temp_deci_c=%s health=%s status=%s current_bms_ua=%s usb_online=%s advisory=%s\n' \
    "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$(date +%s)" \
    "$(read_value "$BAT/capacity")" "$temp" "$health" \
    "$(read_value "$BAT/status")" "$(read_value "$BMS/current_now")" \
    "$(read_value /sys/class/power_supply/usb/online)" "$advisory"
}

case "${1:---once}" in
  --once) snapshot; exit 0 ;;
  --loop) ;;
  --locked) ;;
  *) echo 'Usage: battery-monitor.sh [--once|--loop]' >&2; exit 2 ;;
esac

umask 077
mkdir -p "$BASE" || exit 1
chmod 700 "$BASE" || exit 1
# Let flock open the file itself: Android shell descriptors may be close-on-exec.
# Kernel-held lock is automatically released when the process exits.
if [ "$1" = --loop ]; then
  exec /data/adb/magisk/busybox flock -n "$BASE/monitor.lock" /system/bin/sh "$0" --locked
fi
echo "$$" > "$BASE/monitor.pid"
trap 'rm -f "$BASE/monitor.pid"; exit 0' INT TERM

while :; do
  if [ -f "$BASE/DISABLED" ]; then
    rm -f "$BASE/monitor.pid"
    exit 0
  fi
  if [ -f "$BASE/observations.log" ]; then
    size=$(wc -c < "$BASE/observations.log")
    [ "$size" -lt 262144 ] || mv -f "$BASE/observations.log" "$BASE/observations.previous.log"
  fi
  snapshot > "$BASE/latest.tmp"
  cat "$BASE/latest.tmp" >> "$BASE/observations.log"
  mv -f "$BASE/latest.tmp" "$BASE/latest.txt"
  sleep 60
done
