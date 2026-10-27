#!/system/bin/sh
# Magisk boot hook. Never blocks boot; starts the guard and its watchdog.
BASE=/data/adb/doha-battery
NODE=/sys/class/power_supply/battery/input_suspend
if [ -f "$BASE/CHARGE_GUARD_DISABLED" ]; then
  echo 0 > "$NODE" 2>/dev/null
  exit 0
fi
[ -x "$BASE/charge-guard.sh" ] || exit 0
"$BASE/charge-guard.sh" --loop >/dev/null 2>&1 &
sleep 2
[ -x "$BASE/charge-guard-watchdog.sh" ] && "$BASE/charge-guard-watchdog.sh" --loop >/dev/null 2>&1 &
exit 0
