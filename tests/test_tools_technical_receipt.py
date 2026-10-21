"""Diagnose native selection vs guard without persisting human content or authorizing work."""
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import conversation_voice as voice
from phone_volume import current_volume
import test_assistant_tools as tools_fixture
import test_conversation_voice as voice_fixture


class ToolsTechnicalReceiptTests(unittest.TestCase):
    def receipt(self, metrics=None, **kwargs):
        with patch.object(voice, 'now_ms', return_value=100000):
            return voice.tools_technical(metrics or {}, kwargs.pop('sequence', 1), kwargs.pop('phase', 1),
                                         kwargs.pop('elapsed_ms', 3000), **kwargs)

    def test_human_confirmed_exact_public_phrase_passes_current_guard_and_valid_native_selection(self):
        human = 'Andrómeda, baja el volumen al veinte por ciento'
        self.assertEqual(current_volume(human), 20)
        model = tools_fixture.AssistantToolsTests().model()
        result = model.interpret_result(tools_fixture.native('set_phone_volume'), 'tool_calls', human)
        self.assertEqual(result, {'kind': 'volume_proposal', 'percent': 20})
        self.assertEqual(model.context_metrics['tools_validation'], 'accepted')

    def test_same_generic_rejection_has_distinguishable_numeric_stages(self):
        human = 'Andrómeda, baja el volumen al veinte por ciento'
        cases = [({'content': 'No puedo.'}, 'none', 'missing_native_call'),
                 (tools_fixture.native('set_phone_volume', percent='20'), 'unvalidated', 'invalid_native_call'),
                 (tools_fixture.native('create_alarm'), 'create_alarm', 'agenda_request_rejected')]
        for message, selected, reason in cases:
            model = tools_fixture.AssistantToolsTests().model()
            result = model.interpret_result(message, 'tool_calls' if message.get('tool_calls') else 'stop', human)
            self.assertEqual(result, {'kind': 'tools_rejected'})
            receipt = self.receipt(model.context_metrics)
            self.assertEqual(receipt['tool'], voice.TOOLS_TECHNICAL_ENUMS['tool'].index(selected) + 1)
            self.assertEqual(receipt['validation'], voice.TOOLS_TECHNICAL_ENUMS['validation'].index(reason) + 1)
            self.assertTrue(all(type(value) is int for value in receipt.values()))

    def test_model_selected_volume_but_unrecognized_phrase_is_not_reported_as_android_rejection(self):
        model = tools_fixture.AssistantToolsTests().model()
        self.assertEqual(model.interpret_result(tools_fixture.native('set_phone_volume'), 'tool_calls',
            '¿Me puedes bajar el volumen al veinte por ciento?')['kind'], 'tools_rejected')
        receipt = self.receipt(model.context_metrics)
        self.assertEqual(receipt['validation'], voice.TOOLS_TECHNICAL_ENUMS['validation'].index('volume_request_rejected') + 1)
        self.assertNotIn('android', receipt)

    def test_unknown_or_private_fields_never_escape_and_numeric_bools_are_not_counts(self):
        receipt = self.receipt({'tools_selected_tool': 'PRIVATE_TOOL', 'tools_validation': 'PRIVATE_TEXT',
            'tools_model_kind': 'PRIVATE_RESULT', 'tools_model_choice': 'PRIVATE_CHOICE', 'input_tokens': True,
            'history_turns_available': 90000, 'tools_web_attempted': 'PRIVATE_QUERY', 'human': 'PRIVATE_HUMAN',
            'arguments': 'PRIVATE_ARGS', 'transcript_hash': 'PRIVATE_HASH'}, generation_error='PRIVATE_EXCEPTION')
        self.assertEqual(set(receipt), voice.TOOLS_TECHNICAL_KEYS)
        self.assertNotIn('PRIVATE', json.dumps(receipt))
        for key in ('tool', 'validation', 'result', 'choice', 'generation_error'): self.assertEqual(receipt[key], 0)
        for key in ('input_tokens', 'history_turns', 'web_attempted'): self.assertEqual(receipt[key], -1)
        for kwargs in ({'sequence': True}, {'sequence': 65}, {'phase': 3}, {'phase': True}):
            with self.assertRaises(ValueError): self.receipt(**kwargs)

    def test_disabled_observer_performs_no_disk_operations(self):
        with patch.object(voice, 'record_tools_technical') as write:
            voice.observe_tools_technical(Mock(), 1, 99000, 1)
        write.assert_not_called()

    def test_write_failure_does_not_cancel_or_change_model(self):
        model = Mock(technical_work=Path('public-fixture'), context_metrics={}, error=None)
        with patch.object(voice, 'record_tools_technical', side_effect=OSError), patch.object(voice, 'now_ms', return_value=100000):
            voice.observe_tools_technical(model, 1, 99000, 1)
        model.cancel_generation.assert_not_called(); model.start.assert_not_called()

    def test_actual_actor_records_before_rejection_response_without_dispatching_an_action(self):
        h = voice_fixture.ConversationVoiceTests()
        with patch.object(voice, 'observe_tools_technical') as observe:
            events, model = h.run_session(h.utterance() + [(101200, h.ack_last)], assistant_tools=True,
                guided_agenda=True, continuous_voice=True, transcripts=['baja el volumen al veinte por ciento'],
                answer={'kind': 'tools_rejected'})
        observe.assert_called_once(); self.assertEqual(observe.call_args.args[1:], (1, 99820, 1))
        self.assertFalse(any(event['type'] in {'volume_execute', 'agenda_execute', 'timer_execute'} for event in events))
        self.assertTrue(any(event.get('timer_control') == 'rejected' for event in events))

    def test_timeout_stage_is_recorded_before_recoverable_turn_release(self):
        h = voice_fixture.ConversationVoiceTests(); steps = h.utterance()
        steps += [(at, [h.control('heartbeat')]) for at in range(101000, 124000, 1000)]
        steps += [(124000, [])]
        with patch.object(voice, 'observe_tools_technical') as observe:
            events, model = h.run_session(steps, assistant_tools=True, guided_agenda=True, continuous_voice=True, ready=False)
        self.assertEqual(observe.call_args.args[1:], (1, 99820, 2))
        self.assertTrue(any(event['type'] == 'error' and event.get('recoverable') for event in events))

    @unittest.skipUnless(os.name == 'posix', 'private directory FD semantics require POSIX')
    def test_atomic_last_receipt_permissions_symlinks_stale_temp_and_extra_keys(self):
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder); work.chmod(0o700); target = work / 'voice_tools_health.json'
            first = self.receipt(); voice.record_tools_technical(work, first)
            self.assertEqual(json.loads(target.read_bytes()), first)
            self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o600)
            second = self.receipt(sequence=2); voice.record_tools_technical(work, second)
            self.assertEqual(json.loads(target.read_bytes()), second)
            self.assertEqual(len(list(work.iterdir())), 1)
            with self.assertRaises(ValueError): voice.record_tools_technical(work, dict(second, human='PRIVATE'))
            self.assertEqual(json.loads(target.read_bytes()), second)
            temporary = work / 'voice_tools_health.new'; temporary.write_text('OWN_OTHER_FILE')
            with self.assertRaises(FileExistsError): voice.record_tools_technical(work, first)
            self.assertEqual(temporary.read_text(), 'OWN_OTHER_FILE'); temporary.unlink()
            target.unlink(); other = work / 'other'; other.write_text('OWN_OTHER_FILE'); target.symlink_to(other)
            with self.assertRaises(ValueError): voice.record_tools_technical(work, first)
            self.assertEqual(other.read_text(), 'OWN_OTHER_FILE'); self.assertTrue(target.is_symlink())


if __name__ == '__main__': unittest.main()
