"""Bounded pinned-TLS/Android contract fixture. No inference, ASR, audio or real scheduling.

Uses the production TLS authentication/context receiver and agenda grants/receipts.
The session actor and native results are explicit public fixtures, NOT a voice test.
Run only in separate staging with an Android synthetic client; never a pilot.
"""
import json
import os
from pathlib import Path
import signal
import sys
import time
import uuid

sys.path.insert(0, '/opt/doha-voice/pilot')
import conversation_voice as wire
import assistant_tools_model as native
from agenda_requests import current_event
from assistant_tools_trial import AssistantToolsTrial
from doja_stream import frame, resource_ok
from web_search import speech


class FixtureModel:
    def __init__(self, unused):
        self.memory = wire.ShortMemory()
        self.owned = self

    def alive(self): return True
    def cancel(self): pass


CASES = [('alarm', 'ALARM', 'pon una alarma mañana a las ocho de la mañana'),
         ('reminder', 'REMINDER', 'recuérdame beber agua en dos minutos'),
         ('reminder_direct', 'REMINDER', 'crea un recordatorio en dos minutos para beber agua'),
         ('web', '', 'busca en Internet nebulosas')]
current = None


def fixture_session(conn, detector, model, limit, check, source, context_id, entry, timer_trial, assistant_tools):
    assert source == 'synthetic_pcm' and assistant_tools and not timer_trial and entry is None
    identity, kind, human = current
    session = uuid.uuid4().hex
    def emit(event): wire.send_event(conn, dict(event, session_id=session, context_id=context_id))
    emit(dict(type='ready', protocol=9, conversation_only=False, wake_only=False,
              wake_enabled=True, ai_enabled=True, actions_enabled=True, assistant_tools=True,
              timer_trial=False, allowed_tools=['create_timer', 'create_alarm', 'create_reminder', 'search_web'],
              clock='boottime_ms', server_time_ms=wire.now_ms(), seconds=90, response_start_ms=30000,
              input_source=source, context_version=1, restored_turns=0, continuous_chat=True,
              listen_idle_ms=50000, followup_offer=True, closing_farewell=True, voice_entry=False,
              test_fixture=True, speech_interrupt=False))
    now, wall = wire.now_ms(), int(time.time() * 1000)
    turn = dict(id=uuid.uuid4().hex, seq=1, speech_end=now, text=human, calendar_anchor_ms=wall, calendar_boot_ms=now)
    emit(dict(type='thinking', turn_id=turn['id'], turn_seq=1, speech_end_ms=now, deadline_ms=now+30000,
              text=human, calendar_anchor_ms=wall, calendar_boot_ms=now, administrative=False))
    trial = AssistantToolsTrial(wire.now_ms)
    try:
        if kind:
            proposal = dict(current_event(kind, human, wall), kind='agenda_proposal')
            response = trial.model_result(proposal, turn, session, context_id, now+90000)
            emit(response['event'])
            conn.settimeout(8)
            packet_kind, raw = frame(conn)
            assert packet_kind == 3
            response = trial.result(json.loads(raw), session, context_id)
            assert response['actions_executed'] == 1 and response['close_reason'] == 'action_result'
        else:
            sources = [dict(url='https://example.org/public',title='Fuente pública',excerpt='Extracto público.')]
            response = trial.model_result(dict(kind='web_result', text=speech(sources), sources=sources),
                turn,session,context_id,now+90000)
        emit(dict(type='say', turn_id=turn['id'], turn_seq=1, speech_end_ms=now, deadline_ms=now+30000,
                  text=response['text'], timer_control=response['timer_control'], sources=response.get('sources', []),
                  actions_executed=response.get('actions_executed',0), offered_more=response.get('offered_more',False),
                  close_reason=response.get('close_reason','')))
        conn.settimeout(8)
        packet_kind, raw = frame(conn)
        assert packet_kind == 3 and json.loads(raw) == {'action':'fixture_validated'}  # NOT TTS completion.
        print(json.dumps(dict(event='agenda_transport_case',case=identity,passed=True,session_actor_simulated=True,
                              native_result_simulated=True,real_scheduler=False,microphone_opened=False,audio_played=False)),flush=True)
    finally: trial.close()


def main():
    if len(sys.argv) != 1: raise ValueError('No arguments accepted')
    signal.alarm(100)
    print(json.dumps(dict(event='agenda_transport_fixture_started', pid=os.getpid())), flush=True)
    wire.session = fixture_session
    native.AssistantToolsModel = FixtureModel
    private = Path('/var/lib/andromeda-conversation')
    global current
    for current in CASES:
        wire.serve(private/'token', None, None, 90, private/'server-cert.pem',private/'server-key.pem',
                   'synthetic_pcm',resource_ok,assistant_tools=True)
    print(json.dumps(dict(event='agenda_transport_complete',cases=4,passed=True,llm_started=False,
                          asr_started=False,microphone_opened=False,audio_played=False,real_scheduler=False)),flush=True)


if __name__ == '__main__': main()
