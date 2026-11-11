#!/system/bin/sh
# Vigilante: motor y servicios. Reinicia solo lo que falte, sin duplicar un
# arranque coordinado que ya está en curso.
set -eu
[ "$(id -u)" = 0 ] || exit 2
LOG=/data/local/tmp/andromeda-watchdog.log
echo "watchdog iniciado $(date)" >> "$LOG"
# Deja que el arranque autorizado termine de crear su supervisor.
sleep 5
while true; do
  # Un reinicio coordinado ya está en marcha: no compitas con él.
  if ps -ef | grep '[r]estart-all\.sh' >/dev/null; then
    sleep 5
    continue
  fi
  if ! ps -ef | grep '[r]esident_voice.py' >/dev/null; then
    # El supervisor puede seguir arrancando el motor; no lo dupliques.
    if ! ps -ef | grep '[s]tart-andromeda-resident\.sh' >/dev/null; then
      echo "motor caido, reiniciando $(date)" >> "$LOG"
      sh /data/local/doha-voice/restart-all.sh >> "$LOG" 2>&1 || true
    fi
  else
    # El residente puede seguir vivo aunque VoiceService haya cerrado por un
    # error. Sin este chequeo la casa quedaba muda y el vigilante no hacía nada.
    if ! dumpsys activity services local.doja.voice/.VoiceService 2>/dev/null | grep -q 'ServiceRecord'; then
      echo "voz caida, reiniciando servicio $(date)" >> "$LOG"
      am start-foreground-service -n local.doja.voice/.VoiceService -a START \
        --ez assistant_tools true --ez conversation_only true --ez continuous_voice true --ei seconds 300 \
        >> "$LOG" 2>&1 || true
    fi
    if ! dumpsys activity services local.doja.voice/.HomeGatewayService 2>/dev/null | grep -q 'ServiceRecord'; then
      echo "gateway caido, reiniciando servicio $(date)" >> "$LOG"
      am start-foreground-service -n local.doja.voice/.HomeGatewayService -a START --ez enabled true \
        >> "$LOG" 2>&1 || true
    fi
  fi
  # Reduce el peor retraso de detección de 15 s a 5 s sin sondeo agresivo.
  sleep 5
done
