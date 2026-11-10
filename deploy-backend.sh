#!/system/bin/sh
set -eu
P=/data/local/linux/debian-final/opt/doha-voice/pilot
for f in assistant_tools_model.py assistant_tools_trial.py conversation_voice.py resident_voice.py tv_intents.py tv_control.py deterministic_router.py cloud_model.py tool_planner.py christmas_days.py cancel_phrases.py volume_dialogue.py agenda_dialogue.py light_trial.py reminder_deletion.py alarm_deletion.py timer_proposals.py; do
  cp /data/local/tmp/$f "$P/$f"
  chown 10218:10218 "$P/$f"
  chmod 640 "$P/$f"
done
cp /data/local/tmp/start-andromeda-resident.sh /data/local/doha-voice/start-andromeda-resident.sh
chown root:root /data/local/doha-voice/start-andromeda-resident.sh
chmod 750 /data/local/doha-voice/start-andromeda-resident.sh
echo "OK desplegado"
