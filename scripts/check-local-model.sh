#!/system/bin/sh
# Guarded CPU inference and synthetic tests, run as UID 10218. No mic/network/TTS.
set -u
BASE=/data/local/doha-inference
BIN=$BASE/llama-b11669
MODEL=$BASE/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
NAME=${1:-sky}
THREADS=${2:-4}
case "$NAME" in sky|identity|weather|timer|biodiversity|blue_red|throughput|voice) ;; *) exit 2 ;; esac
case "$THREADS" in 2|4) ;; *) exit 2 ;; esac
PROMPT=$BASE/test-prompts/$NAME.txt
if [ "$NAME" = voice ]; then
  PROMPT=${3:-}
  case "$PROMPT" in
    /data/local/linux/debian-final/tmp/doha-voice.*/prompt.txt) ;;
    *) echo 'Unexpected voice prompt path' >&2; exit 2 ;;
  esac
fi
[ "$(id -u)" = 10218 ] || { echo 'Run as Termux UID 10218' >&2; exit 1; }
umask 077
WORK=$(/data/data/com.termux/files/usr/bin/mktemp -d /data/data/com.termux/files/usr/tmp/doha-model-test.XXXXXX) || exit 1
PID=''
cleanup() {
  if [ -n "$PID" ]; then
    kill -TERM "$PID" 2>/dev/null || true
    sleep 1
    kill -KILL "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -f "$WORK/output" "$WORK/error"
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
valid_metrics() {
  case "$available" in ''|*[!0-9]*) return 1 ;; esac
  case "$temp" in ''|*[!0-9]*) return 1 ;; esac
}
valid_metrics || { echo 'Missing/invalid resource readings' >&2; exit 1; }
[ "$available" -ge 1000000 ] && [ "$temp" -lt 380 ] || {
  echo "Preflight refused: available_kib=$available battery_deci_c=$temp"; exit 1;
}
min_available=$available
max_temp=$temp
peak_rss=0
start=$(date +%s)
disk_swap_before=$(awk '/loop9/ {print $4}' /proc/swaps)
export LD_LIBRARY_PATH=$BIN
if [ "$NAME" = throughput ]; then
  "$BIN/llama-bench" -m "$MODEL" -p 64 -n 32 -r 1 -t 2,4 -ngl 0 \
    > "$WORK/output" 2> "$WORK/error" &
else
  "$BIN/llama-completion" -m "$MODEL" -f "$PROMPT" \
    -n 96 -c 1024 -t "$THREADS" -ngl 0 --temp 0.1 --top-k 50 \
    --repeat-penalty 1.05 --seed 42 -no-cnv --no-display-prompt --no-perf \
    > "$WORK/output" 2> "$WORK/error" &
fi
PID=$!
reason=completed
while kill -0 "$PID" 2>/dev/null; do
  available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
  temp=$(cat /sys/class/power_supply/battery/temp)
  rss=$(awk '/^VmHWM:/ {print $2}' "/proc/$PID/status" 2>/dev/null)
  if valid_metrics; then
    [ "$available" -ge "$min_available" ] || min_available=$available
    [ "$temp" -le "$max_temp" ] || max_temp=$temp
  else
    reason=sensor_unavailable
  fi
  [ "${rss:-0}" -le "$peak_rss" ] || peak_rss=$rss
  elapsed=$(($(date +%s) - start))
  if [ "$reason" != completed ]; then :
  elif [ "$available" -lt 500000 ]; then reason=low_memory;
  elif [ "$temp" -ge 400 ]; then reason=battery_warm;
  elif [ "$elapsed" -ge 45 ]; then reason=timeout;
  fi
  if [ "$reason" != completed ]; then
    kill -TERM "$PID" 2>/dev/null || true
    sleep 1
    kill -KILL "$PID" 2>/dev/null || true
    break
  fi
  sleep 1
done
wait "$PID"
rc=$?
PID=''
elapsed=$(($(date +%s) - start))
disk_swap_after=$(awk '/loop9/ {print $4}' /proc/swaps)
report() {
printf 'case=%s threads=%s exit=%s reason=%s elapsed_seconds=%s min_available_kib=%s peak_process_rss_kib=%s max_battery_deci_c=%s disk_swap_before_kib=%s disk_swap_after_kib=%s\n' \
  "$NAME" "$THREADS" "$rc" "$reason" "$elapsed" "$min_available" "$peak_rss" "$max_temp" "$disk_swap_before" "$disk_swap_after"
}
if [ "$NAME" = voice ]; then report >&2; else report; fi
cat "$WORK/output"
if [ "$rc" -ne 0 ]; then tail -n 8 "$WORK/error"; fi
[ "$reason" = completed ] && [ "$rc" = 0 ]
