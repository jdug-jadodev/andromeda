#!/system/bin/sh
# New conversational architecture. No microphone until explicitly started in UI.
set -eu
MODE=${1:-pilot}
case "$MODE" in pilot|test) ;; *) exit 2 ;; esac
if [ "$MODE" = pilot ]; then
  echo 'Conversational mode is not validated: real-model recall/tool-selection tests failed. Use test; microphone stays off.' >&2
  exit 1
fi
exec /data/local/doha-voice/start-andromeda-ai.sh "$MODE" chat
