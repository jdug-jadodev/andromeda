#!/system/bin/sh
# Root coordinates temporary local inference + voice backend. Never opens mic.
set -eu
ROOT=/data/local/linux/debian-final
BASE=/data/local/doha-inference
[ "$(id -u)" = 0 ] || exit 1
MODE=${1:-pilot}
case "$MODE" in pilot|test) ;; *) exit 2 ;; esac
PROFILE=${2:-pilot}
MODEL_ID=lfm
KV_TYPE=f16
CACHE_POLICY=advanced
THINK_BUDGET=0
SAMPLING=greedy
RUNTIME_CONTROL=baseline
LOG_MODE=normal
case "$PROFILE" in
  pilot|diagnostic) CONTEXT=1024 ;;
  chat) CONTEXT=2048 ;;
  qwen3) CONTEXT=2048; MODEL_ID=qwen3 ;;
  qwen17) CONTEXT=2048; MODEL_ID=qwen17 ;;
  controlled) CONTEXT=2048; MODEL_ID=qwen17 ;;
  ablation) CONTEXT=2048; MODEL_ID=qwen17 ;;
  cache_isolation) CONTEXT=2048; MODEL_ID=qwen17 ;;
  cache_fp32) CONTEXT=2048; MODEL_ID=qwen17; KV_TYPE=f32 ;;
  cache_simple) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=simple ;;
  cache_tokenwise) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  controlled_tokenwise) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  controlled_small_tokenwise) CONTEXT=2048; MODEL_ID=qwen3; CACHE_POLICY=tokenwise ;;
  span_tokenwise) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  span_regression) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  sequential) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  sequential_lfm) CONTEXT=2048; MODEL_ID=lfm; CACHE_POLICY=tokenwise ;;
  sequential_contract_v2) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  sequential_think32) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; THINK_BUDGET=32 ;;
  sequential_recommended) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  memory) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise ;;
  conversation_only) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  conversation_only_lfm) CONTEXT=2048; MODEL_ID=lfm; CACHE_POLICY=tokenwise ;;
  conversation_only_q8) CONTEXT=2048; MODEL_ID=qwen17_q8; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  identity) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  identity_holdout) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  identity_baseline_holdout) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  identity_cache) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  qwen35_conversation) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_non_thinking ;;
  qwen35_holdout) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_non_thinking ;;
  qwen35_no_penalty) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty ;;
  qwen35_no_penalty_holdout) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty ;;
  route_qwen17) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking ;;
  route_qwen17_no_repack) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=no_repack ;;
  route_qwen17_no_flash) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=no_flash ;;
  route_qwen17_batch16) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=batch16 ;;
  route_qwen35) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty ;;
  route_qwen35_no_repack) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=no_repack ;;
  route_qwen35_no_flash) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=no_flash ;;
  route_qwen35_batch1) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=batch1 ;;
  route_qwen17_greedy) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=greedy ;;
  route_qwen35_greedy) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=greedy ;;
  route_qwen35_fp32) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; KV_TYPE=f32; RUNTIME_CONTROL=kv_f32 ;;
  route_qwen35_fp32_no_flash) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; KV_TYPE=f32; RUNTIME_CONTROL=no_flash ;;
  route_qwen17_stable_history) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=stable_history ;;
  route_qwen35_stable_history) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=stable_history ;;
  route_qwen17_language) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=language ;;
  route_qwen35_language) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=language ;;
  route_qwen17_generic) CONTEXT=2048; MODEL_ID=qwen17; CACHE_POLICY=tokenwise; SAMPLING=qwen_non_thinking; RUNTIME_CONTROL=generic ;;
  route_qwen35_generic) CONTEXT=2048; MODEL_ID=qwen35; CACHE_POLICY=hybrid; SAMPLING=qwen35_no_penalty; RUNTIME_CONTROL=generic ;;
  *) exit 2 ;;
esac
case "$PROFILE" in route_*) LOG_MODE=audit ;; esac
if [ "$MODE" != test ] && [ "$PROFILE" != pilot ]; then
  echo 'Experimental profile is test-only until quality and latency gates pass' >&2
  exit 2
fi
ZONE=$(getprop persist.sys.timezone)
case "$ZONE" in ''|*[!a-zA-Z0-9_+/-]*) echo 'Invalid Android timezone' >&2; exit 1 ;; esac
available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
temp=$(cat /sys/class/power_supply/battery/temp)
[ "$available" -ge 1000000 ] && [ "$temp" -lt 380 ] || exit 1
if ss -lnt | grep -qE ':(11435|11436)[[:space:]]'; then
  echo 'Local voice/inference port busy; refusing replacement' >&2; exit 1
fi
umask 077
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/doha-warm-test.XXXXXX")
INNER=/tmp/${WORK##*/}
chown 10218:10218 "$WORK"
SUPERVISOR=''
debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin TZ="$ZONE" ANDROMEDA_MODEL="$MODEL_ID" ANDROMEDA_KV_TYPE="$KV_TYPE" ANDROMEDA_CACHE_POLICY="$CACHE_POLICY" ANDROMEDA_THINK_BUDGET="$THINK_BUDGET" ANDROMEDA_SAMPLING="$SAMPLING" ANDROMEDA_RUNTIME_CONTROL="$RUNTIME_CONTROL" /opt/doha-voice/bin/python "$@"
}
cleanup() {
  touch "$WORK/DONE"
  if [ -n "$SUPERVISOR" ]; then wait "$SUPERVISOR" 2>/dev/null || true; fi
  if [ "$MODEL_ID" = qwen35 ]; then
    debian_user /opt/doha-voice/pilot/measure-model-memory.py snapshot model_off_after || true
  fi
  cat "$WORK/resources.txt" 2>/dev/null || true
  rm -f "$WORK/key" "$WORK/DONE" "$WORK/server.pid" "$WORK/server.log" "$WORK/resources.txt"
  for name in ai_create ai_cancel ai_confirm ai_chat; do
    rm -f "$WORK/andromeda_fixture_$name.wav"
    if [ "$MODE" = test ]; then
      rm -f "/data/user/0/local.doja.voice/files/synthetic/andromeda_fixture_$name.wav"
    fi
  done
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
debian_user /opt/doha-voice/benchmark-warm-client.py init "$INNER"
if [ "$PROFILE" = memory ]; then
  debian_user /opt/doha-voice/pilot/measure-model-memory.py snapshot model_off_before
fi
if [ "$MODEL_ID" = qwen35 ]; then
  debian_user /opt/doha-voice/pilot/measure-model-memory.py snapshot model_off_before
fi
su -g 10218 -G 3003 10218 -c "'$BASE/warm-server-worker.sh' '$WORK' 420 '$CONTEXT' '$MODEL_ID' '$KV_TYPE' '$CACHE_POLICY' '$THINK_BUDGET' '$RUNTIME_CONTROL' '$LOG_MODE'" &
SUPERVISOR=$!
case "$PROFILE" in route_*)
  debian_user /opt/doha-voice/pilot/evaluate-inference-routes.py "$INNER/key"
  touch "$WORK/DONE"
  wait "$SUPERVISOR"
  SUPERVISOR=''
  exit 0
esac
if [ "$PROFILE" = qwen35_conversation ] || [ "$PROFILE" = qwen35_no_penalty ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key" --memory-samples
elif [ "$PROFILE" = qwen35_holdout ] || [ "$PROFILE" = qwen35_no_penalty_holdout ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key" --holdouts --memory-samples
elif [ "$PROFILE" = identity_cache ]; then
  debian_user /opt/doha-voice/pilot/evaluate-identity-cache.py "$INNER/key"
elif [ "$PROFILE" = identity ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key" --identity
elif [ "$PROFILE" = identity_holdout ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key" --identity --holdouts
elif [ "$PROFILE" = identity_baseline_holdout ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key" --holdouts
elif [ "$PROFILE" = conversation_only ] || [ "$PROFILE" = conversation_only_lfm ] || [ "$PROFILE" = conversation_only_q8 ]; then
  debian_user /opt/doha-voice/pilot/evaluate-conversation-only.py "$INNER/key"
elif [ "$PROFILE" = memory ]; then
  debian_user /opt/doha-voice/pilot/measure-model-memory.py warm "$INNER/key" "$INNER/server.pid"
elif [ "$PROFILE" = sequential_contract_v2 ]; then
  debian_user /opt/doha-voice/pilot/evaluate-sequential-conversations.py "$INNER/key" --contract-v2
elif [ "$PROFILE" = sequential ] || [ "$PROFILE" = sequential_lfm ] || [ "$PROFILE" = sequential_think32 ] || [ "$PROFILE" = sequential_recommended ]; then
  debian_user /opt/doha-voice/pilot/evaluate-sequential-conversations.py "$INNER/key"
elif [ "$PROFILE" = span_regression ]; then
  debian_user /opt/doha-voice/pilot/evaluate-span-contract.py "$INNER/key" --regression
elif [ "$PROFILE" = span_tokenwise ]; then
  debian_user /opt/doha-voice/pilot/evaluate-span-contract.py "$INNER/key"
elif [ "$PROFILE" = cache_isolation ] || [ "$PROFILE" = cache_fp32 ] || [ "$PROFILE" = cache_simple ] || [ "$PROFILE" = cache_tokenwise ]; then
  debian_user /opt/doha-voice/pilot/evaluate-cache-isolation.py "$INNER/key"
elif [ "$PROFILE" = ablation ]; then
  debian_user /opt/doha-voice/pilot/evaluate-contract-ablation.py "$INNER/key"
elif [ "$PROFILE" = controlled ] || [ "$PROFILE" = controlled_tokenwise ] || [ "$PROFILE" = controlled_small_tokenwise ]; then
  debian_user /opt/doha-voice/pilot/evaluate-model-controlled.py "$INNER/key"
elif [ "$PROFILE" = diagnostic ]; then
  debian_user /opt/doha-voice/pilot/probe_inference_protocol.py "$INNER/key"
elif [ "$PROFILE" = chat ] || [ "$PROFILE" = qwen3 ] || [ "$PROFILE" = qwen17 ]; then
  debian_user /opt/doha-voice/pilot/probe_chat_agent.py "$INNER/key" "$MODE"
else
  debian_user /opt/doha-voice/pilot/probe_local_ai.py "$INNER/key" "$MODE"
fi
if [ "$MODE" = test ] && [ "$PROFILE" = pilot ]; then
  for name in ai_create ai_cancel ai_confirm ai_chat; do
    cp "/data/user/0/local.doja.voice/files/synthetic/andromeda_fixture_$name.wav" "$WORK/"
    chown 10218:10218 "$WORK/andromeda_fixture_$name.wav"
    chmod 600 "$WORK/andromeda_fixture_$name.wav"
  done
  debian_user /opt/doha-voice/pilot/probe_stream_ai.py "$INNER/key" "$INNER"
fi
rc=0
if [ "$MODE" = pilot ]; then
  echo 'IA local preparada. Elegir cinco minutos en la app. Micrófono aún apagado.'
  if [ "$PROFILE" = chat ] || [ "$PROFILE" = qwen3 ] || [ "$PROFILE" = qwen17 ]; then
    debian_user /opt/doha-voice/pilot/doja_stream.py \
      --key /var/lib/doja-voice-pilot/token --seconds 300 --test-wake --ai-key "$INNER/key" --chat || rc=$?
  else
    debian_user /opt/doha-voice/pilot/doja_stream.py \
      --key /var/lib/doja-voice-pilot/token --seconds 300 --test-wake --ai-key "$INNER/key" || rc=$?
  fi
fi
touch "$WORK/DONE"
wait "$SUPERVISOR" || rc=1
SUPERVISOR=''
if [ "$PROFILE" = memory ]; then
  debian_user /opt/doha-voice/pilot/measure-model-memory.py snapshot model_off_after
fi
exit "$rc"
