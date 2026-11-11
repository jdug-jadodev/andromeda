#!/system/bin/sh
# Reinicia Andromeda en el orden correcto: motor primero, servicios despues.
set -eu
[ "$(id -u)" = 0 ] || exit 2
am force-stop local.doja.voice
# Un residente anterior no debe quedarse con el canal: el nuevo no arrancaria
# y seguiriamos usando codigo viejo.
for p in $(ps -ef | grep -E 'resident_voice\.py|tv_api\.py serve' | grep -v grep | awk '{print $2}'); do
  kill -TERM "$p" 2>/dev/null || true
done
# Espera solo lo necesario a que se liberen el proceso y el socket, con tope.
i=0
while [ "$i" -lt 5 ]; do
  if ! ps -ef | grep '[r]esident_voice.py' >/dev/null \
      && ! ss -lnt | grep -qE '127\.0\.0\.1:11436[[:space:]]'; then
    break
  fi
  sleep 1
  i=$((i+1))
done
if ps -ef | grep '[r]esident_voice.py' >/dev/null \
    || ss -lnt | grep -qE '127\.0\.0\.1:11436[[:space:]]'; then
  echo "residente anterior no libero el canal en 5 s; no se duplica $(date)"
  exit 1
fi
nohup sh /data/local/doha-voice/start-andromeda-resident.sh continuous-voice guided alarms \
  >/data/local/tmp/resident-supervisor.log 2>&1 &
# No esperes una línea que ya no se genera: el supervisor redirige la salida del
# backend a /dev/null. Usa estado sano y socket listo, con un límite explícito.
i=0
ready=0
while [ "$i" -lt 60 ]; do
  if ! ps -ef | grep '[r]esident_voice.py' >/dev/null \
      && ! ps -ef | grep '[s]tart-andromeda-resident.sh' >/dev/null; then
    echo "residente termino antes de quedar listo $(date)"
    exit 1
  fi
  status=$(/system/bin/sh /data/local/doha-voice/andromeda-resident-control.sh status 2>/dev/null || true)
  if echo "$status" | grep -q '"state": "running"' \
      && ss -lnt | grep -qE '127\.0\.0\.1:11436[[:space:]]'; then
    ready=1
    break
  fi
  sleep 1
  i=$((i+1))
done
if [ "$ready" -ne 1 ]; then
  echo "residente no quedo listo en 60 s; se cancela este intento $(date)"
  for p in $(ps -ef | grep '[r]esident_voice.py' | grep -v grep | awk '{print $2}'); do
    kill -TERM "$p" 2>/dev/null || true
  done
  exit 1
fi
am start-foreground-service -n local.doja.voice/.HomeGatewayService -a START --ez enabled true
am start-foreground-service -n local.doja.voice/.VoiceService -a START --ez assistant_tools true --ez conversation_only true --ez continuous_voice true --ei seconds 300
echo "andromeda lista; residente listo en ${i}s"
