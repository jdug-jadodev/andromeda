#!/system/bin/sh
# Reinicia Andromeda en el orden correcto: motor primero, servicios despues.
set -eu
[ "$(id -u)" = 0 ] || exit 2
am force-stop local.doja.voice
sleep 3
nohup sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms \
  >/data/local/tmp/resident-supervisor.log 2>&1 &
i=0
while [ "$i" -lt 30 ]; do
  sleep 1
  if grep -q conversation_ready /data/local/tmp/resident.log 2>/dev/null; then break; fi
  i=$((i+1))
done
am start-foreground-service -n local.doja.voice/.HomeGatewayService -a START --ez enabled true
am start-foreground-service -n local.doja.voice/.VoiceService -a START --ez assistant_tools true --ez conversation_only true --ez continuous_voice true --ei seconds 300
echo "andromeda lista"