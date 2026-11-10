#!/system/bin/sh
# Magisk boot hook. Arranca el vigilante de Andromeda si no esta ya en marcha.
# Nunca bloquea el arranque; todo el trabajo va en segundo plano.
BASE=/data/local/doha-voice
[ -f "$BASE/WATCHDOG_DISABLED" ] && exit 0
[ -x "$BASE/watchdog.sh" ] || exit 0
(
  # Deja que arranquen los servicios y el servidor Debian antes de vigilar.
  sleep 45
  if ! ps -ef | grep '[d]oha-voice/watchdog\.sh' >/dev/null; then
    nohup sh "$BASE/watchdog.sh" >/dev/null 2>&1 &
  fi
) &
exit 0
