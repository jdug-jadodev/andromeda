#!/system/bin/sh
# Synthetic fixtures only. No microphone APIs, TTS playback or alarm scheduling.
set -eu
[ "$(id -u)" = 0 ] || exit 1
ROOT=/data/local/linux/debian-final
SOURCE=/data/user/0/local.doja.voice/files/synthetic
WORK=$(/data/adb/magisk/busybox mktemp -d "$ROOT/tmp/andromeda-fixtures.XXXXXX")
INNER=/tmp/${WORK##*/}
cleanup() {
  for name in positive command cancel delete negated quoted androide andres adormecida andamio ordinary ai_create ai_cancel ai_confirm ai_chat; do
    rm -f "$WORK/andromeda_fixture_$name.wav" "$SOURCE/andromeda_fixture_$name.wav"
  done
  rmdir "$WORK" 2>/dev/null || true
  rmdir "$SOURCE" 2>/dev/null || true
}
trap cleanup EXIT
for name in positive command cancel delete negated quoted androide andres adormecida andamio ordinary; do
  test -s "$SOURCE/andromeda_fixture_$name.wav"
  cp "$SOURCE/andromeda_fixture_$name.wav" "$WORK/"
  chown 10218:10218 "$WORK/andromeda_fixture_$name.wav"
  chmod 600 "$WORK/andromeda_fixture_$name.wav"
done
chown 10218:10218 "$WORK"
chroot "$ROOT" /usr/bin/setpriv --reuid=10218 --regid=10218 --clear-groups \
  /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin /opt/doha-voice/bin/python \
  /opt/doha-voice/pilot/device_spanish_wake.py "$INNER"
