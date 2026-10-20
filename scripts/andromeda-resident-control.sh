#!/system/bin/sh
# Local root operator only. No network endpoint, arbitrary command or voice trigger.
set -eu
[ "$(id -u)" = 0 ] && [ "$#" = 1 ] || exit 2
case "$1" in status|stop) ;; *) exit 2 ;; esac
LOCK=/data/local/tmp/andromeda-resident.lock
ROOT=/data/local/linux/debian-final
if [ ! -d "$LOCK" ]; then echo '{"state":"not_running"}'; exit 0; fi
[ ! -L "$LOCK" ] && [ "$(stat -c '%u %a' "$LOCK")" = '0 700' ] || exit 1
[ -f "$LOCK/work" ] && [ ! -L "$LOCK/work" ] || exit 1
WORK=$(cat "$LOCK/work")
echo "$WORK" | grep -qE '^/data/local/linux/debian-final/tmp/andromeda-resident\.[A-Za-z0-9]+$' || exit 1
[ -d "$WORK" ] && [ ! -L "$WORK" ] || exit 1
INNER=/tmp/${WORK##*/}
if [ "$1" = stop ]; then
  chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
    /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin /opt/doha-voice/bin/python -I -B -c \
    'import sys; sys.path.insert(0,"/opt/doha-voice/pilot"); from resident_runtime import request_stop; request_stop(sys.argv[1])' "$INNER"
  echo '{"state":"stop_requested","confirmed_stopped":false}'
  exit 0
fi
if [ ! -f "$WORK/metrics.json" ]; then echo '{"state":"preparing"}'; exit 0; fi
chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
  /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin /opt/doha-voice/bin/python -I -B -c \
  'import json,sys; sys.path.insert(0,"/opt/doha-voice/pilot"); from resident_runtime import read_report; print(json.dumps(read_report(sys.argv[1])))' \
  "$INNER/metrics.json"
