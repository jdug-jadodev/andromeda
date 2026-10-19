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
import threading
import time
import uuid

sys.path.insert(0, '/opt/doha-voice/pilot')
import conversation_voice as wire
import assistant_tools_model as native
from agenda_requests import current_event
from assistant_tools_trial import AssistantToolsTrial
from doja_stream import frame, resource_ok
from web_answers import answer as web_answer, contract as web_contract


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
_fixture_listener = None
_socket_type = wire.socket.socket


class FixtureSocket(_socket_type):
    def listen(self, backlog):
        global _fixture_listener
        super().listen(backlog)
        _fixture_listener = self


def close_fixture_listener():
    global _fixture_listener
    if _fixture_listener is None: raise RuntimeError('Missing fixture listener')
    _fixture_listener.close(); _fixture_listener = None


def fixture_session(conn, detector, model, limit, check, source, context_id, entry, timer_trial, assistant_tools):
    assert source == 'synthetic_pcm' and assistant_tools and not timer_trial and entry is None
    # Test-only sequencing barrier: once this synthetic client is authenticated,
    # stop accepting TCP peers BEFORE it receives ready/finishes the case. The
    # next case can otherwise queue a TLS handshake on the about-to-close old
    # listener instead of seeing the already-supported connection-refused path.
    # No production transport, pin, deadline or Android retry policy is changed.
    close_fixture_listener()
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
            sources = [dict(url='https://example.org/public',title='Nebulosas',excerpt='Una nebulosa es una nube de gas y polvo en el espacio.')]
            def forbidden_inference(*args): raise AssertionError('Transport fixture cannot start inference')
            rendered = web_answer('nebulosas', sources, forbidden_inference, wire.now_ms, now + 22000, threading.Event())
            assert rendered['status'] == 'quoted'  # Actual installed renderer; literal Spanish requires no LFM.
            response = trial.model_result(dict(kind='web_result', text=rendered['text'], sources=sources, web_answer=web_contract(rendered)),
                turn,session,context_id,now+90000)
        emit(dict(type='say', turn_id=turn['id'], turn_seq=1, speech_end_ms=now, deadline_ms=now+30000,
                  text=response['text'], timer_control=response['timer_control'], sources=response.get('sources', []),
                  actions_executed=response.get('actions_executed',0), offered_more=response.get('offered_more',False),
                  close_reason=response.get('close_reason',''),
                  **({'web_answer': response['web_answer']} if 'web_answer' in response else {})))
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
    wire.socket.socket = FixtureSocket
    native.AssistantToolsModel = FixtureModel
    private = Path('/var/lib/andromeda-conversation')
    global current
    for current in CASES:
        wire.serve(private/'token', None, None, 90, private/'server-cert.pem',private/'server-key.pem',
                   'synthetic_pcm',resource_ok,assistant_tools=True)
    print(json.dumps(dict(event='agenda_transport_complete',cases=4,passed=True,llm_started=False,
                          asr_started=False,microphone_opened=False,audio_played=False,real_scheduler=False)),flush=True)


if __name__ == '__main__': main()
