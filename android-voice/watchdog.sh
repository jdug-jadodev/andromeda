#!/system/bin/sh
# Vigilante: si el motor muere, lo reinicia solo (mata lo viejo, arranca en orden).
set -eu
[ "$(id -u)" = 0 ] || exit 2
LOG=/data/local/tmp/andromeda-watchdog.log
echo "watchdog iniciado $(date)" >> "$LOG"
while true; do
  if ! ps -ef | grep '[r]esident_voice.py' >/dev/null; then
    echo "motor caido, reiniciando $(date)" >> "$LOG"
    sh /data/local/doha-voice/restart-all.sh >> "$LOG" 2>&1 || true
  fi
  sleep 15
done
