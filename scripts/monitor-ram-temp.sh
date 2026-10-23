#!/system/bin/sh
# Read-only RAM/temperature sampler for a bounded local observation window.
# Usage: monitor-ram-temp.sh [seconds] [interval_seconds] [output_csv]
set -u
DURATION=${1:-600}
INTERVAL=${2:-5}
OUT=${3:-/data/local/tmp/andromeda-ram-temp.csv}
END=$(( $(date +%s) + DURATION ))
printf '%s\n' 'epoch,mem_available_kib,mem_free_kib,swap_free_kib,app_rss_kib,app_processes,model_rss_kib,model_processes,backend_rss_kib,backend_processes,battery_temp_deci_c,battery_capacity,battery_status,battery_current_ua,usb_online,max_thermal_millic,load1' > "$OUT"
while [ "$(date +%s)" -lt "$END" ]; do
  epoch=$(date +%s)
  mem_available=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo)
  mem_free=$(awk '/^MemFree:/{print $2}' /proc/meminfo)
  swap_free=$(awk '/^SwapFree:/{print $2}' /proc/meminfo)
  processes=$(ps -A -o NAME,RSS | awk '
    $1 == "local.doja.voice" { app += $2; app_count++ }
    $1 == "llama-server" { model += $2; model_count++ }
    $1 == "python" { backend += $2; backend_count++ }
    END { printf "%d,%d,%d,%d,%d,%d", app, app_count, model, model_count, backend, backend_count }')
  temp=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)
  capacity=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
  status=$(cat /sys/class/power_supply/battery/status 2>/dev/null)
  current=$(cat /sys/class/power_supply/bms/current_now 2>/dev/null)
  usb=$(cat /sys/class/power_supply/usb/online 2>/dev/null)
  max_thermal=0
  for zone in /sys/class/thermal/thermal_zone*/temp; do
    value=$(cat "$zone" 2>/dev/null)
    case "$value" in
      ''|*[!0-9-]*) continue ;;
    esac
    [ "$value" -gt "$max_thermal" ] && max_thermal=$value
  done
  load=$(cut -d' ' -f1 /proc/loadavg)
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$epoch" "$mem_available" "$mem_free" "$swap_free" "$processes" \
    "${temp:--1}" "${capacity:--1}" "${status:-unknown}" "${current:--1}" "${usb:--1}" "$max_thermal" "$load" >> "$OUT"
  sleep "$INTERVAL"
done
