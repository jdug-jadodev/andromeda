#!/system/bin/sh
# Magisk boot hook. Never writes to charging controls or blocks boot.
BASE=/data/adb/doha-battery
[ -f "$BASE/DISABLED" ] && exit 0
[ -x "$BASE/battery-monitor.sh" ] || exit 0
"$BASE/battery-monitor.sh" --loop >/dev/null 2>&1 &
exit 0
