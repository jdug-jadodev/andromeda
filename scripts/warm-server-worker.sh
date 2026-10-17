#!/system/bin/sh
# Temporary, guarded server for a benchmark only. Run as UID 10218.
set -u
WORK=$1
LIMIT=${2:-100}
case "$LIMIT" in 100|420) ;; *) exit 2 ;; esac
CONTEXT=${3:-1024}
case "$CONTEXT" in 1024|2048) ;; *) exit 2 ;; esac
if [ "$CONTEXT" = 2048 ]; then
  CACHE=64
  CHECKPOINTS=32
  STEP=16
else
  CACHE=16
  CHECKPOINTS=8
  STEP=8
fi
case "$WORK" in /data/local/linux/debian-final/tmp/doha-warm-test.*) ;; *) exit 2 ;; esac
[ "$(id -u)" = 10218 ] || exit 1
BIN=/data/local/doha-inference/llama-b11669
MODEL_ID=${4:-lfm}
case "$MODEL_ID" in
  lfm) MODEL=/data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf ;;
  qwen3) MODEL=/data/local/doha-inference/Qwen3-0.6B-Q4_K_M.gguf ;;
  qwen17) MODEL=/data/local/doha-inference/Qwen_Qwen3-1.7B-Q4_K_M.gguf ;;
  *) exit 2 ;;
esac
PID=''
KV_TYPE=${5:-f16}
case "$KV_TYPE" in f16|f32) ;; *) exit 2 ;; esac
cleanup() {
  if [ -n "$PID" ]; then
    kill -TERM "$PID" 2>/dev/null || true
    sleep 1
    kill -KILL "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export LD_LIBRARY_PATH=$BIN
"$BIN/llama-server" -m "$MODEL" -c "$CONTEXT" -t 4 -tb 4 -ngl 0 -np 1 \
  --cache-type-k "$KV_TYPE" --cache-type-v "$KV_TYPE" \
  --host 127.0.0.1 --port 11435 --api-key-file "$WORK/key" \
  --no-webui --no-slots --no-ui-mcp-proxy --threads-http 1 \
  --cache-ram "$CACHE" --no-cache-idle-slots --cache-prompt --poll 0 --poll-batch 0 \
  -b 256 -ub 16 --ctx-checkpoints "$CHECKPOINTS" --checkpoint-min-step "$STEP" \
  -n 96 --reasoning off --jinja > "$WORK/server.log" 2>&1 &
PID=$!
echo "$PID" > "$WORK/server.pid"
started=$(date +%s)
min_available=999999999
max_temp=0
peak_rss=0
reason=completed
while kill -0 "$PID" 2>/dev/null; do
  available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
  temp=$(cat /sys/class/power_supply/battery/temp)
  rss=$(awk '/^VmHWM:/ {print $2}' "/proc/$PID/status" 2>/dev/null)
  case "$available:$temp" in *[!0-9:]*|:|:*|*:) reason=sensor_unavailable ;; esac
  if [ "$reason" = completed ]; then
    [ "$available" -ge "$min_available" ] || min_available=$available
    [ "$temp" -le "$max_temp" ] || max_temp=$temp
    if [ "$available" -lt 500000 ]; then reason=low_memory;
    elif [ "$temp" -ge 400 ]; then reason=battery_warm;
    elif [ "$(($(date +%s) - started))" -ge "$LIMIT" ]; then reason=timeout;
    fi
  fi
  [ "${rss:-0}" -le "$peak_rss" ] || peak_rss=$rss
  if [ -f "$WORK/DONE" ] || [ "$reason" != completed ]; then break; fi
  sleep 1
done
echo "reason=$reason min_available_kib=$min_available peak_server_rss_kib=$peak_rss max_battery_deci_c=$max_temp" > "$WORK/resources.txt"
[ "$reason" = completed ]
