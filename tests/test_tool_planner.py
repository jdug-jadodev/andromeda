import json
import os
import sys
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

import cloud_model
import deterministic_router
import tool_planner
from assistant_tools_model import AssistantToolsModel
from conversation_voice import ShortMemory


def model_stub():
    return type('Model', (), {
        'work_deadline': 100000,
        'clock': staticmethod(lambda: 90000),
        'cancelled': threading.Event(),
    })()


class Response:
    def __init__(self, status, body=b''):
        self.status, self.body = status, body

    def read(self, limit=None):
        return self.body if limit is None else self.body[:limit]


class Connection:
    status, body, error = 200, b'{}', None

    def __init__(self, *args, **kwargs):
        pass

    def request(self, *args, **kwargs):
        if Connection.error is not None:
            raise Connection.error

    def getresponse(self):
        return Response(Connection.status, Connection.body)

    def close(self):
        pass


def reply_body(text):
    return json.dumps({'choices': [{'message': {'content': text}}]}).encode()


def tool_body(name):
    return json.dumps({'choices': [{'message': {'content': '',
        'tool_calls': [{'type': 'function', 'function': {'name': name, 'arguments': '{}'}}]}}]}).encode()


class CloudClassificationTests(unittest.TestCase):
    def setUp(self):
        os.environ['DEEPSEEK_API_KEY'] = 'test-key'
        Connection.status, Connection.body, Connection.error = 200, b'{}', None
        self.patch = patch('cloud_model.http.client.HTTPSConnection', Connection)
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        os.environ.pop('DEEPSEEK_API_KEY', None)

    def test_text_and_tool_are_parsed(self):
        Connection.body = reply_body('Hola, ¿en qué te ayudo?')
        self.assertEqual(cloud_model.reason_ex('hola', model_stub()), (
            {'kind': 'text', 'text': 'Hola, ¿en qué te ayudo?'}, 'ok'))
        Connection.body = tool_body('create_timer')
        self.assertEqual(cloud_model.reason_ex('pon cinco minutos', model_stub())[0],
                         {'kind': 'tool', 'name': 'create_timer'})

    def test_every_failure_has_its_own_code(self):
        cases = [(401, 'unauthorized'), (402, 'payment_required'), (403, 'unauthorized'),
                 (429, 'rate_limited'), (500, 'server_error'), (503, 'server_error'),
                 (400, 'invalid_response')]
        for status, code in cases:
            with self.subTest(status=status):
                Connection.status = status
                self.assertEqual(cloud_model.reason_ex('hola', model_stub()), (None, code))
        Connection.status = 200
        for error, code in [(TimeoutError(), 'timeout'), (OSError('dns'), 'network')]:
            with self.subTest(code=code):
                Connection.error = error
                self.assertEqual(cloud_model.reason_ex('hola', model_stub()), (None, code))
                Connection.error = None

    def test_without_key_no_network_is_attempted(self):
        os.environ.pop('DEEPSEEK_API_KEY', None)
        with patch('cloud_model.http.client.HTTPSConnection') as connection:
            self.assertEqual(cloud_model.reason_ex('hola', model_stub()), (None, 'disabled'))
            connection.assert_not_called()

    def test_history_is_bounded_and_never_breaks_the_request(self):
        Connection.body = reply_body('Seguimos con Romeo.')
        history = [{'role': 'user', 'content': 'háblame de romeo'},
                   {'role': 'assistant', 'content': 'Romeo y Julieta es una tragedia.'},
                   {'role': 'system', 'content': 'no debe viajar'},
                   {'role': 'user', 'content': 'x' * 5000}]
        captured = {}

        class Capture(Connection):
            def request(self, method, path, body=None, headers=None):
                captured['body'] = json.loads(body)

        with patch('cloud_model.http.client.HTTPSConnection', Capture):
            cloud_model.reason_ex('cuéntame más', model_stub(), history)
        messages = captured['body']['messages']
        self.assertEqual(messages[0]['role'], 'system')
        self.assertNotIn('no debe viajar', json.dumps(messages))
        self.assertLessEqual(len(messages), 6)
        self.assertEqual(messages[-1], {'role': 'user', 'content': 'cuéntame más'})


class BreakerTests(unittest.TestCase):
    def setUp(self):
        tool_planner.reset()

    def tearDown(self):
        tool_planner.reset()

    def test_three_transient_failures_open_the_breaker(self):
        for _ in range(2):
            tool_planner.failure('server_error')
        self.assertTrue(tool_planner.available())
        tool_planner.failure('server_error')
        self.assertFalse(tool_planner.available())
        self.assertEqual(tool_planner.status()['last_code'], 'server_error')
        tool_planner.success()
        self.assertTrue(tool_planner.available())

    def test_quota_failure_opens_at_once_and_warns_only_once(self):
        tool_planner.failure('payment_required')
        self.assertFalse(tool_planner.available())
        self.assertIn('saldo', tool_planner.take_notice())
        self.assertEqual(tool_planner.take_notice(), '')

    def test_invalid_key_opens_and_warns(self):
        tool_planner.failure('unauthorized')
        self.assertFalse(tool_planner.available())
        self.assertIn('clave', tool_planner.take_notice())

    def test_network_failure_opens_at_once(self):
        for code in ('network', 'timeout'):
            with self.subTest(code=code):
                tool_planner.reset()
                tool_planner.failure(code)
                self.assertFalse(tool_planner.available())

    def test_breaker_survives_until_the_window_passes(self):
        tool_planner.failure('timeout')
        tool_planner.failure('timeout')
        tool_planner.failure('timeout')
        self.assertFalse(tool_planner.available())
        self.assertTrue(tool_planner.available(time.monotonic() + tool_planner.OPEN_SECONDS + 1))


class CloudFirstTests(unittest.TestCase):
    def setUp(self):
        tool_planner.reset()
        os.environ['DEEPSEEK_API_KEY'] = 'test-key'
        os.environ['ANDROMEDA_PLANNER'] = 'deepseek_first'
        self.model = model_stub()
        for name, value in {'alarm_lifecycle': False, 'guided_agenda': True,
                            'reminder_clarification': False, 'confirmed_volume': False,
                            'tv_enabled': False, 'basic_assistant': False,
                            'reminder_deletion': False, 'anchor_wall': 1792328400000}.items():
            setattr(self.model, name, value)

    def tearDown(self):
        tool_planner.reset()
        os.environ.pop('DEEPSEEK_API_KEY', None)
        os.environ.pop('ANDROMEDA_PLANNER', None)

    def test_text_goes_through_the_plain_reply_guard(self):
        with patch('cloud_model.reason_ex', return_value=({'kind': 'text', 'text': 'Hola.'}, 'ok')):
            result, code, tool = deterministic_router.route_cloud_first('hola', self.model)
        self.assertEqual((result, code, tool), ({'kind': 'reply', 'text': 'Hola.'}, 'ok', 'none'))
        with patch('cloud_model.reason_ex', return_value=({'kind': 'text', 'text': 'He creado una alarma.'}, 'ok')):
            result, code, _ = deterministic_router.route_cloud_first('hola', self.model)
        self.assertIsNone(result)
        self.assertEqual(code, 'invalid_text')

    def test_cloud_tool_still_passes_the_deterministic_guards(self):
        with patch('cloud_model.reason_ex', return_value=({'kind': 'tool', 'name': 'create_timer'}, 'ok')):
            result, code, tool = deterministic_router.route_cloud_first(
                'pon un temporizador de cinco minutos', self.model)
        self.assertEqual((result['kind'], code, tool), ('timer_proposal', 'ok', 'create_timer'))
        with patch('cloud_model.reason_ex', return_value=({'kind': 'tool', 'name': 'delete_reminder'}, 'ok')):
            result, code, tool = deterministic_router.route_cloud_first('borra el recordatorio', self.model)
        self.assertIsNone(result)
        self.assertEqual((code, tool), ('tool_rejected', 'delete_reminder'))

    def test_a_network_timeout_opens_the_breaker_immediately(self):
        with patch('cloud_model.reason_ex', return_value=(None, 'timeout')) as call:
            self.assertEqual(deterministic_router.route_cloud_first('hola', self.model), (None, 'timeout', 'none'))
            self.assertEqual(deterministic_router.route_cloud_first('hola', self.model), (None, 'breaker_open', 'none'))
        self.assertEqual(call.call_count, 1)

    def test_three_server_failures_open_the_breaker(self):
        with patch('cloud_model.reason_ex', return_value=(None, 'server_error')) as call:
            for _ in range(3):
                self.assertEqual(deterministic_router.route_cloud_first('hola', self.model),
                                 (None, 'server_error', 'none'))
            self.assertEqual(deterministic_router.route_cloud_first('hola', self.model),
                             (None, 'breaker_open', 'none'))
        self.assertEqual(call.call_count, 3)

    def test_without_key_it_is_disabled_and_no_network_happens(self):
        os.environ.pop('DEEPSEEK_API_KEY', None)
        with patch('cloud_model.reason_ex') as call:
            self.assertEqual(deterministic_router.route_cloud_first('hola', self.model), (None, 'disabled', 'none'))
            call.assert_not_called()

    def test_cancel_self_destruct_and_tv_keep_local_priority(self):
        self.assertTrue(deterministic_router.deterministic_priority('cancélalo'))
        self.assertTrue(deterministic_router.deterministic_priority('autodestruyete'))
        self.assertFalse(deterministic_router.deterministic_priority('cuéntame más de romeo'))


class ConversationContinuityTests(unittest.TestCase):
    """El caso Romeo: la nube debe recibir el hilo y no preguntar de qué hablaba."""

    def setUp(self):
        tool_planner.reset()
        os.environ['DEEPSEEK_API_KEY'] = 'test-key'
        os.environ['ANDROMEDA_PLANNER'] = 'deepseek_first'
        self.model = object.__new__(AssistantToolsModel)
        self.model.thread = None
        self.model.cancelled = threading.Event()
        self.model.done = threading.Event()
        self.model.result = self.model.error = None
        self.model.context_metrics = {}
        self.model.clock = lambda: 100000
        self.model.anchor_wall = 1792328400000
        self.model.anchor_boot = 100000
        self.model.tv_enabled = False
        self.model.basic_assistant = False
        self.model.guided_agenda = True
        self.model.alarm_lifecycle = False
        self.model.reminder_clarification = False
        self.model.reminder_deletion = False
        self.model.confirmed_volume = False
        self.model.memory = ShortMemory(lambda: 100000)
        self.model.memory.pairs = [
            [{'role': 'user', 'content': 'háblame de romeo'},
             {'role': 'assistant', 'content': 'Romeo y Julieta es una tragedia de Shakespeare.'}]]

    def tearDown(self):
        tool_planner.reset()
        os.environ.pop('DEEPSEEK_API_KEY', None)
        os.environ.pop('ANDROMEDA_PLANNER', None)

    def test_history_reaches_the_cloud_planner(self):
        captured = {}
        def plan(text, model, history=None):
            captured['text'], captured['history'] = text, history
            return {'kind': 'reply', 'text': 'Claro, sigo con Romeo y Julieta.'}, 'ok', 'none'
        with patch('deterministic_router.route_cloud_first', side_effect=plan):
            self.model.start('cuéntame más', 99000, 400000)
            self.model.thread.join(2)
        self.assertTrue(self.model.done.is_set())
        self.assertEqual(self.model.result['text'], 'Claro, sigo con Romeo y Julieta.')
        self.assertEqual(captured['text'], 'cuéntame más')
        self.assertIn('romeo', json.dumps(captured['history']).lower())
        self.assertEqual(self.model.context_metrics['planner_engine'], 'deepseek')
        self.assertEqual(self.model.context_metrics['tools_model_choice'], 'cloud')

    def test_when_the_cloud_fails_the_deterministic_answers_the_same_phrase(self):
        with patch('deterministic_router.route_cloud_first', return_value=(None, 'timeout', 'none')):
            self.model.start('cuéntame más', 99000, 400000)
            self.model.thread.join(2)
        self.assertEqual(self.model.result['kind'], 'reply')
        self.assertEqual(self.model.context_metrics['planner_engine'], 'deterministic')
        self.assertEqual(self.model.context_metrics['planner_reason'], 'timeout')

    def test_quota_fallback_tells_the_truth_once(self):
        tool_planner.failure('payment_required')
        self.model.start('cuéntame más', 99000, 400000)
        self.assertIsNone(self.model.thread)
        self.assertIn('saldo', self.model.result['text'])
        self.assertEqual(self.model.context_metrics['planner_reason'], 'breaker_open')
        tool_planner.reset()
        self.model.done.clear(); self.model.thread = None
        with patch('deterministic_router.route_cloud_first',
                   return_value=({'kind': 'reply', 'text': 'Sigo con Romeo.'}, 'ok', 'none')):
            self.model.start('cuéntame más', 99000, 400000)
            self.model.thread.join(2)
        self.assertNotIn('saldo', self.model.result['text'])

    def test_tv_and_cancel_never_call_the_cloud(self):
        self.model.tv_enabled = True
        with patch('deterministic_router.route_cloud_first') as cloud:
            self.model.start('apaga la tele', 99000, 400000)
            self.assertIsNone(self.model.thread)
        cloud.assert_not_called()
        with patch('deterministic_router.route_cloud_first') as cloud:
            self.model.start('cancélalo', 99000, 400000)
        cloud.assert_not_called()


if __name__ == '__main__':
    unittest.main()
