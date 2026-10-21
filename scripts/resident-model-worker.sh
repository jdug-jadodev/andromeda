#!/system/bin/sh
# Fixed non-root LFM worker. No web credentials, logs, retry, or microphone.
set -eu
[ "$(id -u)" = 10218 ] && [ "$#" -ge 1 ] && [ "$#" -le 2 ] || exit 2
WORK=$1
MODE=${2:-bounded}
case "$MODE" in bounded) INPUT=deadline_ms ;; continuous) INPUT=mode ;; *) exit 2 ;; esac
echo "$WORK" | grep -qE '^/data/local/linux/debian-final/tmp/andromeda-resident\.[A-Za-z0-9]+$' || exit 2
[ -d "$WORK" ] && [ ! -L "$WORK" ] && [ "$(stat -c '%u %a' "$WORK")" = '10218 700' ] || exit 1
for name in key "$INPUT"; do
  [ -f "$WORK/$name" ] && [ ! -L "$WORK/$name" ] && [ "$(stat -c '%u %a' "$WORK/$name")" = '10218 600' ] || exit 1
done
if [ "$MODE" = continuous ]; then
  [ "$(cat "$WORK/mode")" = continuous_model_v1 ] && [ ! -e "$WORK/deadline_ms" ] && [ ! -L "$WORK/deadline_ms" ] || exit 1
else
  [ ! -e "$WORK/mode" ] && [ ! -L "$WORK/mode" ] || exit 1
fi
BEGIN_MS=$(awk '{printf "%.0f", $1 * 1000}' /proc/uptime)
BIN=/data/local/doha-inference/llama-b11669
CACHE_RAM=64
SLOT_OPTIONS=''
if [ "$MODE" = continuous ]; then
  # b11669 gates erase behind slot-save-path. This ROOT-owned non-writable
  # directory enables erase but prevents save from ever creating/overwriting a file.
  [ ! -L "$BIN" ] && [ "$(stat -c '%u' "$BIN")" = 0 ] && [ ! -w "$BIN" ] || exit 1
  [ "$(awk '/^CapEff:/ {print $2}' "/proc/$$/status")" = 0000000000000000 ] || exit 1
  [ ! -u "$BIN/llama-server" ] && [ ! -g "$BIN/llama-server" ] || exit 1
  for entry in "$BIN"/* "$BIN"/.[!.]* "$BIN"/..?*; do
    if [ -e "$entry" ] || [ -L "$entry" ]; then
      [ ! -L "$entry" ] && [ "$(stat -c '%u' "$entry")" = 0 ] && [ ! -w "$entry" ] || exit 1
    fi
  done
  CACHE_RAM=0
  SLOT_OPTIONS='--slot-save-path /data/local/doha-inference/llama-b11669'
fi
PID=''
START=''
spawned() {
  [ -n "$PID" ] && [ -r "/proc/$PID/stat" ] && [ -r "/proc/$PID/status" ] || return 1
  [ "$(awk '{print $22}' "/proc/$PID/stat")" = "$START" ] || return 1
  [ "$(awk '/^Uid:/ {print $2}' "/proc/$PID/status")" = 10218 ] || return 1
  [ "$(awk '/^CapEff:/ {print $2}' "/proc/$PID/status")" = 0000000000000000 ] || return 1
  [ "$(readlink "/proc/$PID/exe")" = "$BIN/llama-server" ]
}
owned() {
  spawned || return 1
  # Android tr can spin on an open proc cmdline after the process exits. Match
  # NUL-delimited whole arguments directly, with a deadline for each reader.
  /system/bin/timeout -s KILL 1 /system/bin/grep -Fzqx -- "$BIN/llama-server" "/proc/$PID/cmdline" || return 1
  /system/bin/timeout -s KILL 1 /system/bin/grep -Fzqx -- /data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf "/proc/$PID/cmdline" || return 1
  /system/bin/timeout -s KILL 1 /system/bin/grep -Fzqx -- "$WORK/key" "/proc/$PID/cmdline"
}
cleanup() {
  # Cleanup identifies OUR fixed exec child by UID/start/caps/exe, not by a
  # stream which may disappear during exit. It cannot signal a recycled PID.
  if spawned; then kill -TERM "$PID" 2>/dev/null || true; sleep 1; fi
  if spawned; then kill -KILL "$PID" 2>/dev/null || true; fi
  if [ -n "$PID" ]; then wait "$PID" 2>/dev/null || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export LD_LIBRARY_PATH=$BIN
"$BIN/llama-server" -m /data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf \
  -c 2048 -t 4 -tb 4 -ngl 0 -np 1 --cache-type-k f16 --cache-type-v f16 \
  --host 127.0.0.1 --port 11435 --api-key-file "$WORK/key" \
  --no-webui --no-slots --no-ui-mcp-proxy --threads-http 1 --cache-ram "$CACHE_RAM" $SLOT_OPTIONS \
  --no-cache-idle-slots --cache-prompt --poll 0 --poll-batch 0 -b 256 -ub 16 \
  --ctx-checkpoints 32 --checkpoint-min-step 16 -n 96 --reasoning off \
  --reasoning-budget 0 --reasoning-format deepseek --no-reasoning-preserve --jinja --log-disable \
  >/dev/null 2>&1 &
PID=$!
START=$(awk '{print $22}' "/proc/$PID/stat")
umask 077
(set -C; echo "$PID" > "$WORK/server.pid")
if [ "$MODE" = bounded ]; then
  DEADLINE=$(cat "$WORK/deadline_ms")
  case "$DEADLINE" in ''|*[!0-9]*) exit 1 ;; esac
  current=$(awk '{printf "%.0f", $1 * 1000}' /proc/uptime)
  [ "$DEADLINE" -gt "$current" ] && [ "$((DEADLINE-current))" -le 1800000 ] || exit 1
fi
while owned; do
  available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
  temp=$(cat /sys/class/power_supply/battery/temp)
  current=$(awk '{printf "%.0f", $1 * 1000}' /proc/uptime)
  case "$available:$temp:$current" in *[!0-9:]*|:*|*:) exit 1 ;; esac
  [ "$available" -ge 500000 ] && [ "$temp" -lt 400 ] || exit 1
  if [ -e "$WORK/STOP" ]; then exit 0; fi
  if [ "$MODE" = bounded ] && [ "$current" -ge "$DEADLINE" ]; then exit 0; fi
  if [ "$MODE" = continuous ]; then
    # Independent lease: a killed/frozen Python supervisor cannot leave a model
    # indefinitely alive. Reports use boottime, not wall-clock or credentials.
    if [ -e "$WORK/metrics.json" ] || [ -L "$WORK/metrics.json" ]; then
      [ -f "$WORK/metrics.json" ] && [ ! -L "$WORK/metrics.json" ] && [ "$(stat -c '%u %a' "$WORK/metrics.json")" = '10218 600' ] || exit 1
      [ "$(stat -c '%s' "$WORK/metrics.json")" -le 65536 ] || exit 1
      grep -Fq '"lifetime": "continuous_model_v1"' "$WORK/metrics.json" || exit 1
      grep -Fq '"state": "running"' "$WORK/metrics.json" || exit 1
      stamp=$(sed -n 's/.*"at_ms": \([0-9][0-9]*\).*/\1/p' "$WORK/metrics.json")
      case "$stamp" in ''|*[!0-9]*) exit 1 ;; esac
      [ "$stamp" -le "$((current+1000))" ] && [ "$((current-stamp))" -le 30000 ] || exit 1
    else
      [ "$((current-BEGIN_MS))" -le 90000 ] || exit 1
    fi
  fi
  sleep 1
done
exit 1
