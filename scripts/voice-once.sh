#!/system/bin/sh
# Root orchestration only. Decoder, recognizer, LLM and Android APIs use UID 10218.
# One explicit 8-second capture. No boot hook, wake word, network server or history.
set -eu
ROOT=/data/local/linux/debian-final
BIN=/data/local/doha-inference/llama-b11669
MODEL=/data/local/doha-inference/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
TERMUX=/data/data/com.termux/files/usr
BB=/data/adb/magisk/busybox

as_termux() {
  su -g 10218 -G 3003 10218 -c "export HOME=/data/data/com.termux/files/home; export PREFIX=$TERMUX; export TMPDIR=$TERMUX/tmp; export PATH=$TERMUX/bin:/system/bin; $1"
}

debian_user() {
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin "$@"
}

check_resources() {
  available=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
  temperature=$(cat /sys/class/power_supply/battery/temp)
  case "$available:$temperature" in
    *[!0-9:]*) echo 'No se pudieron validar sensores/recursos' >&2; exit 1 ;;
  esac
  [ -n "$available" ] && [ "$available" -ge 650000 ] || {
    echo 'Prueba pausada: poca RAM disponible (minimo 650000 KiB)' >&2; exit 1;
  }
  [ -n "$temperature" ] && [ "$temperature" -lt 400 ] || {
    echo 'Prueba pausada: bateria a 40 C o mas, o sensor ausente' >&2; exit 1;
  }
  echo "resources: available_kib=$available battery_deci_c=$temperature"
}

[ "$(id -u)" = 0 ] || { echo 'Este orquestador requiere root' >&2; exit 1; }
case "${1:---check}" in
  --check|--once|--sample|--warm-once) ;;
  *) echo 'Usage: voice-once.sh [--check|--once|--sample|--warm-once KEY_FILE]' >&2; exit 2 ;;
esac
if [ "${1:---check}" = --warm-once ]; then
  case "${2:-}" in "$ROOT"/tmp/doha-warm-test.*/key) ;; *) exit 2 ;; esac
fi
for dependency in "$ROOT/usr/bin/ffmpeg" "$ROOT/opt/doha-voice/pyvenv.cfg" "$ROOT/usr/bin/python3.11" \
  "$ROOT/opt/doha-voice/voice-turn.py" "$BIN/llama-completion" "$MODEL" /data/local/doha-inference/check-local-model.sh \
  "$TERMUX/bin/termux-microphone-record" "$TERMUX/bin/termux-tts-speak"; do
  [ -r "$dependency" ] || { echo "Falta: $dependency" >&2; exit 1; }
done
check_resources
[ "${1:---check}" != --check ] || exit 0

# flock owns the lock across exec; released automatically if the process exits.
if [ "${DOHA_VOICE_LOCKED:-0}" != 1 ]; then
  exec env DOHA_VOICE_LOCKED=1 "$BB" flock -n /data/local/doha-voice/turn.lock \
    /system/bin/sh "$0" "$@"
fi
umask 077
WORK=$("$BB" mktemp -d "$ROOT/tmp/doha-voice.XXXXXX")
chown 10218:10218 "$WORK"
INNER=/tmp/${WORK##*/}
AUDIO="$TERMUX/tmp/${WORK##*/}.m4a"
RECORDING=0
cleanup() {
  if [ "$RECORDING" = 1 ]; then
    active=$(as_termux 'timeout 5 termux-microphone-record -i' 2>/dev/null) || active=''
    if echo "$active" | grep -qF "$AUDIO"; then
      as_termux 'timeout 5 termux-microphone-record -q' >/dev/null 2>&1 || true
    fi
  fi
  # Delete only the specifically created temporary files, never broad directories.
  rm -f "$AUDIO" "$WORK/capture.m4a" "$WORK/capture.wav" \
    "$WORK/prompt.txt" "$WORK/response.raw" "$WORK/response.txt" "$WORK/guard-metrics.txt"
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ "$1" = --once ] || [ "$1" = --warm-once ]; then
info=$(as_termux 'timeout 8 termux-microphone-record -i')
echo "$info" | grep -qE '"isRecording"[[:space:]]*:[[:space:]]*false' || {
  echo 'El microfono ya se esta usando o no responde; prueba cancelada' >&2; exit 1;
}
as_termux 'timeout 15 termux-tts-speak -e com.google.android.tts -l es -n US -s MUSIC "Te escucho despues de esta frase, durante ocho segundos."'
echo 'MICROFONO: inicio de captura de 8 segundos'
RECORDING=1
result=$(as_termux "timeout 10 termux-microphone-record -f '$AUDIO' -l 8 -e aac -r 16000 -c 1")
echo "$result" | grep -q 'Recording started:' || {
  echo 'No se pudo iniciar la captura; revise permiso de microfono' >&2; exit 1;
}
sleep 9
as_termux 'timeout 5 termux-microphone-record -q' >/dev/null
RECORDING=0
echo 'MICROFONO: captura terminada'
[ -s "$AUDIO" ] || { echo 'No se genero audio' >&2; exit 1; }
cp "$AUDIO" "$WORK/capture.m4a"
chown 10218:10218 "$WORK/capture.m4a"
rm -f "$AUDIO"
started=$(date +%s)
debian_user /usr/bin/timeout 15 /usr/bin/ffmpeg -nostdin -v error -threads 1 \
  -i "$INNER/capture.m4a" -t 10 -ac 1 -ar 16000 -c:a pcm_s16le "$INNER/capture.wav"
debian_user /usr/bin/timeout 25 /opt/doha-voice/bin/python /opt/doha-voice/voice-turn.py \
  transcribe "$INNER/capture.wav" "$INNER/prompt.txt"
else
  started=$(date +%s)
  debian_user /opt/doha-voice/bin/python /opt/doha-voice/voice-turn.py \
    sample-prompt "$INNER/prompt.txt"
fi
# Vosk has exited before loading the language model: their peaks do not overlap.
check_resources
if [ "$1" = --warm-once ]; then
  key_inner=${2#"$ROOT"}
  if ! debian_user /usr/bin/timeout 45 /usr/bin/python3 /opt/doha-voice/local-inference-client.py \
      infer "$key_inner" "$INNER/prompt.txt" "$INNER/response.raw" > "$WORK/guard-metrics.txt"; then
    echo 'Inferencia local incompleta; no se reproduce respuesta parcial' >&2
    exit 1
  fi
elif ! as_termux "/data/local/doha-inference/check-local-model.sh voice 4 '$WORK/prompt.txt'" \
    > "$WORK/response.raw" 2> "$WORK/guard-metrics.txt"; then
  cat "$WORK/guard-metrics.txt" >&2
  echo 'Inferencia cancelada o fallida; no se reproduce respuesta parcial' >&2
  exit 1
fi
cat "$WORK/guard-metrics.txt"
chown 10218:10218 "$WORK/response.raw"
debian_user /opt/doha-voice/bin/python /opt/doha-voice/voice-turn.py \
  clean "$INNER/response.raw" "$INNER/response.txt"
echo "processing_until_tts_seconds=$(($(date +%s) - started))"
echo 'RESPUESTA:'
cat "$WORK/response.txt"
as_termux 'timeout 30 termux-tts-speak -e com.google.android.tts -l es -n US -s MUSIC' < "$WORK/response.txt"
check_resources
echo 'Prueba terminada; audio, prompt y respuesta temporales se eliminan al salir'
