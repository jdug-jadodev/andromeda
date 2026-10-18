import json
from pathlib import Path
import socket
import struct
import sys
import tempfile
import threading
import types
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
import conversation_voice as voice


class ConversationVoiceTests(unittest.TestCase):
    def test_memory_is_bounded_and_independent_from_bot_activity(self):
        clock = [100000]; memory = voice.ShortMemory(lambda: clock[0])
        memory.messages('hola')
        for n in range(9): memory.commit(str(n), 'respuesta')
        self.assertEqual(len(memory.pairs), 6)
        self.assertEqual(memory.pairs[0][0]['content'], '3')
        clock[0] += 3599999
        memory.commit('más', 'sí')
        clock[0] += 1
        self.assertEqual(len(memory.messages('otro')), 2)

    def test_memory_messages_are_copies(self):
        memory = voice.ShortMemory(lambda: 1000); memory.commit('dato', 'texto')
        messages = memory.messages('hola'); messages[1]['content'] = 'changed'
        self.assertEqual(memory.pairs[0][0]['content'], 'dato')

    def test_audio_alignment_maps_to_capture_clock_not_arrival(self):
        timeline = voice.AudioTimeline()
        timeline.add(100020, b'\0' * 640); timeline.add(100040, b'\0' * 640)
        self.assertEqual(timeline.speech_end({'result': [{'end': .03}]}), 99830)
        with self.assertRaises(ValueError): timeline.speech_end({})
        with self.assertRaises(ValueError): timeline.speech_end({'result': [{'end': 8}]})

    def test_audio_alignment_retains_capture_gaps(self):
        timeline = voice.AudioTimeline()
        timeline.add(100020, b'\0' * 640); timeline.add(102040, b'\0' * 640)
        self.assertEqual(timeline.speech_end({'result': [{'end': .04}]}), 101840)

    def test_frame_pump_preserves_partial_and_multiple_frames(self):
        connection = Mock(); first = struct.pack('!BI', 3, 2) + b'{}'
        connection.recv.side_effect = [first[:3], first[3:] + first, socket.timeout(), b'']
        pump = voice.FramePump(connection)
        self.assertEqual(pump.poll(), []); self.assertEqual(pump.poll(), [(3, b'{}')] * 2)
        self.assertEqual(pump.poll(), [])
        with self.assertRaises(EOFError): pump.poll()

    def test_frame_pump_rejects_unbounded_length(self):
        connection = Mock(); connection.recv.return_value = struct.pack('!BI', 4, 5000)
        with self.assertRaises(ValueError): voice.FramePump(connection).poll()

    def test_hello_deadline_cannot_be_renewed_with_partial_bytes(self):
        clock = [1000]; connection = Mock()
        def recv(length): clock[0] += 1000; return b'\x01'
        connection.recv.side_effect = recv
        with patch.object(voice, 'now_ms', side_effect=lambda: clock[0]):
            with self.assertRaises(TimeoutError): voice.read_hello(connection, 5000)
        self.assertEqual(connection.recv.call_count, 4)

    def test_hello_is_bounded_and_json_object_only(self):
        connection = Mock(); connection.recv.side_effect = [struct.pack('!BI', 1, 2), b'{}']
        with patch.object(voice, 'now_ms', return_value=1000): self.assertEqual(voice.read_hello(connection, 2000), {})
        for kind, size, body in [(1, 4097, b''), (3, 2, b'{}'), (1, 2, b'[]')]:
            connection.recv.side_effect = [struct.pack('!BI', kind, size), body]
            with self.subTest(kind=kind, size=size, body=body), patch.object(voice, 'now_ms', return_value=1000):
                with self.assertRaises(ValueError): voice.read_hello(connection, 2000)

    def test_output_guard_rejects_calls_truncation_and_obvious_false_claims(self):
        for message, reason in [({'content': 'Hola'}, 'length'),
                ({'content': 'Hola', 'tool_calls': [{}]}, 'stop'),
                ({'content': '{"name":"create_timer","arguments":{"seconds":10}}'}, 'stop'),
                ({'content': '<|tool_call_start|>create_timer(seconds=10)<|tool_call_end|>'}, 'stop'),
                ({'content': 'He creado un temporizador.'}, 'stop'),
                ({'content': 'Te avisaré en diez segundos.'}, 'stop'),
                ({'content': 'La alarma está programada y lista.'}, 'stop')]:
            with self.subTest(message=message), self.assertRaises(ValueError): voice.conversational_reply(message, reason)

    def test_normal_reply_is_not_rewritten_or_replaced(self):
        text = 'No puedo crear temporizadores en esta prueba; solo converso.'
        self.assertEqual(voice.conversational_reply({'content': text}, 'stop'), text)

    def make_model(self, directory, clock=lambda: 100000):
        path = Path(directory) / 'key'; path.write_text('x' * 32)
        return voice.ConversationModel(path, owned=Mock(), clock=clock)

    def test_real_token_budget_drops_whole_pairs_and_has_no_tools(self):
        with tempfile.TemporaryDirectory() as directory:
            model = self.make_model(directory)
            model.memory.commit('one', 'answer one'); model.memory.commit('two', 'answer two')
            requests = []
            def post(path, body, deadline):
                requests.append((path, body, deadline))
                if path == '/apply-template': return {'prompt': str(len(body['messages']))}
                if path == '/tokenize': return {'tokens': [1] * (1600 if body['content'] == '6' else 1500)}
                return {'choices': [{'message': {'content': 'Hola.'}, 'finish_reason': 'stop'}]}
            with patch.object(model, 'post', side_effect=post):
                deadline = model.start('question', 99000, 400000); model.thread.join(2)
            self.assertTrue(model.done.is_set()); self.assertIsNone(model.error); self.assertEqual(model.result, 'Hola.')
            self.assertEqual(deadline, 122000); self.assertTrue(all(row[2] == deadline for row in requests))
            final = requests[-1][1]
            self.assertEqual([m['role'] for m in final['messages']], ['system', 'user', 'assistant', 'user'])
            self.assertEqual(final['messages'][1]['content'], 'two')
            self.assertEqual(final['max_tokens'], 96); self.assertNotIn('tools', final)

    def test_current_utterance_cannot_overflow_context(self):
        with tempfile.TemporaryDirectory() as directory:
            model = self.make_model(directory)
            with patch.object(model, 'post', side_effect=[{'prompt': 'x'}, {'tokens': [1] * 1537}]) as post:
                model.start('question', 99000, 400000); model.thread.join(2)
            self.assertEqual(model.error, 'ValueError'); self.assertIsNone(model.result); self.assertEqual(post.call_count, 2)

    def test_global_deadline_is_not_reset_by_new_inference_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            model = self.make_model(directory)
            with patch.object(model, 'post', side_effect=TimeoutError):
                deadline = model.start('question', 80000, 400000); model.thread.join(2)
            self.assertEqual(deadline, 106500)
            with patch.object(model, 'post', side_effect=TimeoutError):
                deadline = model.start('question', 99000, 110000); model.thread.join(2)
            self.assertEqual(deadline, 108500)

    def test_cancel_stops_owned_process_and_discards_racing_reply(self):
        entered = threading.Event(); released = threading.Event()
        with tempfile.TemporaryDirectory() as directory:
            model = self.make_model(directory)
            def post(path, body, deadline):
                if path == '/apply-template': return {'prompt': 'x'}
                if path == '/tokenize': return {'tokens': [1]}
                entered.set(); released.wait(2)
                return {'choices': [{'message': {'content': 'Hola.'}, 'finish_reason': 'stop'}]}
            with patch.object(model, 'post', side_effect=post):
                model.start('question', 99000, 400000); self.assertTrue(entered.wait(1))
                model.owned.stop.side_effect = released.set
                model.cancel()
            model.owned.stop.assert_called_once(); self.assertIsNone(model.result); self.assertFalse(model.thread.is_alive())

    def run_session(self, steps, *, ready=True):
        clock = [100000]; events = []
        model = Mock(result='Hola.', error=None)
        model.memory = voice.ShortMemory(lambda: clock[0]); model.done = threading.Event()
        model.start.side_effect = lambda *args: (model.done.set() if ready else None) or 123000
        detector = Mock(model=object()); detector.accept_pcm.return_value = False
        recognizer = Mock(); recognizer.AcceptWaveform.return_value = True
        recognizer.Result.return_value = json.dumps({'text': 'hola', 'result': [{'end': .02}]})
        vosk = types.SimpleNamespace(KaldiRecognizer=Mock(return_value=recognizer), SetLogLevel=Mock())
        iterator = iter(steps)
        def poll():
            try: at, frames = next(iterator)
            except StopIteration: raise EOFError()
            clock[0] = at
            return frames(events) if callable(frames) else frames
        pump = Mock(); pump.poll.side_effect = poll
        with patch.dict(sys.modules, vosk=vosk), patch.object(voice, 'now_ms', side_effect=lambda: clock[0]), \
                patch.object(voice, 'FramePump', return_value=pump), patch.object(voice, 'send_event', side_effect=lambda c, e: events.append(e)):
            voice.session(Mock(), detector, model, 90, lambda: True, 'synthetic_pcm')
        return events, model

    @staticmethod
    def control(action, **kwargs):
        return 3, json.dumps(dict(action=action, **kwargs)).encode()

    @staticmethod
    def pcm(at): return 4, struct.pack('!Q', at) + b'\0' * 640

    def utterance(self):
        return [(100000, [self.control('activate')]), (100020, [self.pcm(100020)])]

    def test_session_has_no_executor_and_commits_only_acknowledged_spoken_pair(self):
        def acknowledge(events):
            event = next(e for e in events if e['type'] == 'say'); identity = event['turn_id']
            return [self.control('tts_done', turn_id='wrong'), self.control('tts_done', turn_id=identity),
                    self.control('tts_started', turn_id=identity, at_ms=101000),
                    self.control('tts_started', turn_id=identity, at_ms=101000),
                    self.control('tts_done', turn_id=identity), self.control('tts_done', turn_id=identity)]
        events, model = self.run_session(self.utterance() + [(101000, acknowledge)])
        self.assertEqual([e['type'] for e in events], ['ready', 'state', 'thinking', 'say', 'state'])
        self.assertEqual(events[0]['input_source'], 'synthetic_pcm'); self.assertFalse(events[0]['actions_enabled'])
        self.assertEqual(len(model.memory.pairs), 1); model.cancel.assert_called_once()

    def test_disconnect_during_inference_cancels_and_never_speaks(self):
        events, model = self.run_session(self.utterance(), ready=False)
        self.assertNotIn('say', [e['type'] for e in events]); model.cancel.assert_called_once()

    def test_explicit_stop_during_inference_cancels(self):
        events, model = self.run_session(self.utterance() + [(100100, [self.control('stop')])], ready=False)
        self.assertNotIn('say', [e['type'] for e in events]); model.cancel.assert_called_once()

    def test_compute_deadline_discards_even_a_ready_result(self):
        events, model = self.run_session(self.utterance() + [(124000, [])], ready=False)
        self.assertEqual(events[-1]['reason'], 'deadline'); model.cancel.assert_called_once()

    def test_reply_without_tts_ack_does_not_enter_memory(self):
        events, model = self.run_session(self.utterance())
        self.assertIn('say', [e['type'] for e in events]); self.assertEqual(model.memory.pairs, [])

    def test_late_tts_ack_terminates_without_memory(self):
        def late(events):
            event = next(e for e in events if e['type'] == 'say')
            return [self.control('tts_started', turn_id=event['turn_id'], at_ms=event['deadline_ms'] + 1)]
        events, model = self.run_session(self.utterance() + [(101000, late)])
        self.assertEqual(model.memory.pairs, []); model.cancel.assert_called_once()

    def test_stale_audio_fails_closed_and_cancels(self):
        with self.assertRaises(ValueError): self.run_session([(102000, [self.pcm(100000)])])

    def test_nonroot_owned_server_never_kills_a_reused_pid(self):
        owned = voice.OwnedInference.__new__(voice.OwnedInference); owned.start_ticks = '100'; owned.pid = 1234
        with patch.object(owned, 'identity', return_value='101'), patch.object(voice.os, 'kill') as kill:
            owned.stop(); kill.assert_not_called()

    def test_launcher_is_separate_and_does_not_open_microphone(self):
        root = Path(__file__).resolve().parents[1]
        script = (root / 'scripts/start-andromeda-conversation.sh').read_text()
        self.assertIn('--conversation-only', script); self.assertIn('--synthetic-input', script)
        self.assertNotIn('--chat', script); self.assertNotIn('am start', script)
        self.assertNotIn('ConversationAgent(', (root / 'backend/conversation_voice.py').read_text())


if __name__ == '__main__': unittest.main()
