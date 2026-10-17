import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sequential', ROOT / 'scripts' / 'evaluate-sequential-conversations.py')
sequential = importlib.util.module_from_spec(spec); spec.loader.exec_module(sequential)


def response(message):
    return {'choices': [{'finish_reason': 'stop', 'message': message}]}


def tool_call(span='seis minutos', call_id='call_1'):
    return {'role': 'assistant', 'content': None, 'tool_calls': [{'id': call_id, 'type': 'function',
            'function': {'name': 'create_timer', 'arguments': json.dumps({'duration_text': span})}}]}


class SequentialTests(unittest.TestCase):
    def test_actual_generated_reply_is_preserved_for_next_question(self):
        seen = []
        def model(payload):
            seen.append(payload)
            return response({'role': 'assistant', 'content': 'Respuesta generada distinta de la fixture.'})
        conversation = sequential.ShadowConversation('system', model)
        conversation.turn({'id': 'a', 'text': 'hola', 'expected': {'type': 'reply'}})
        conversation.turn({'id': 'b', 'text': 'seguimos', 'expected': {'type': 'reply'}})
        self.assertEqual(seen[-1]['messages'][2]['content'], 'Respuesta generada distinta de la fixture.')
        self.assertEqual(seen[-1]['messages'][-1]['content'], 'seguimos')

    def test_no_history_shared_between_conversations(self):
        model = lambda _: response({'role': 'assistant', 'content': 'hola'})
        first = sequential.ShadowConversation('system', model)
        first.turn({'id': 'a', 'text': 'Valeria', 'expected': {'type': 'reply'}})
        self.assertEqual(sequential.ShadowConversation('system', model).messages, [{'role': 'system', 'content': 'system'}])

    def test_simulator_returns_result_with_correlated_id_but_no_execution(self):
        seen = []
        def model(payload):
            seen.append(payload)
            return response(tool_call() if len(seen) == 1 else {'role': 'assistant', 'content': 'Activado.'})
        case = {'id': 'timer', 'text': 'Avísame en seis minutos', 'expected': {'type': 'tool', 'seconds': 360}, 'simulator': 'success'}
        result = sequential.ShadowConversation('system', model).turn(case)
        self.assertTrue(result['mechanical_pass']); self.assertEqual(result['android_actions_executed'], 0)
        self.assertEqual(seen[-1]['messages'][-1]['tool_call_id'], 'call_1')
        self.assertEqual(json.loads(seen[-1]['messages'][-1]['content'])['seconds'], 360)

    def test_request_on_negation_still_fails_when_simulator_blocks_it(self):
        outputs = iter([tool_call(), {'role': 'assistant', 'content': 'No se creó.'}])
        conversation = sequential.ShadowConversation('system', lambda _: response(next(outputs)))
        result = conversation.turn({'id': 'no', 'text': 'No pongas seis minutos', 'expected': {'type': 'reply'}})
        self.assertFalse(result['mechanical_pass']); self.assertFalse(result['simulated_result']['ok'])

    def test_invented_duration_is_not_simulated(self):
        outputs = iter([tool_call('30 segundos'), {'role': 'assistant', 'content': 'No se creó.'}])
        conversation = sequential.ShadowConversation('system', lambda _: response(next(outputs)))
        result = conversation.turn({'id': 'missing', 'text': 'Pon un temporizador', 'expected': {'type': 'reply'}})
        self.assertFalse(result['mechanical_pass']); self.assertNotIn('simulated_result', result)
        self.assertTrue(result['validation_feedback_added'])
        errors = [m for m in conversation.messages if m['role'] == 'tool']
        self.assertEqual(errors[0]['tool_call_id'], 'call_1')
        self.assertFalse(json.loads(errors[0]['content'])['ok'])

    def test_rejected_request_is_not_left_pending_in_next_turn(self):
        seen = []
        outputs = iter([tool_call('30 segundos'), {'role': 'assistant', 'content': 'No se creó.'},
                        {'role': 'assistant', 'content': 'Seguimos.'}])
        def model(payload):
            seen.append(payload); return response(next(outputs))
        conversation = sequential.ShadowConversation('system', model)
        conversation.turn({'id': 'missing', 'text': 'Pon un temporizador', 'expected': {'type': 'reply'}})
        conversation.turn({'id': 'next', 'text': '¿Qué pasó?', 'expected': {'type': 'reply'}})
        messages = seen[-1]['messages']
        call_index = next(i for i, m in enumerate(messages) if m.get('tool_calls'))
        self.assertEqual(messages[call_index + 1]['role'], 'tool')
        self.assertEqual(json.loads(messages[call_index + 1]['content'])['code'], 'validation_rejected')

    def test_failed_second_inference_does_not_duplicate_tool_result(self):
        outputs = iter([response(tool_call()), OSError('offline')])
        def model(_):
            value = next(outputs)
            if isinstance(value, Exception): raise value
            return value
        conversation = sequential.ShadowConversation('system', model)
        result = conversation.turn({'id': 'create', 'text': 'Pon seis minutos',
                                    'expected': {'type': 'tool', 'seconds': 360}, 'simulator': 'success'})
        self.assertFalse(result['mechanical_pass'])
        self.assertEqual(len([m for m in conversation.messages if m['role'] == 'tool']), 1)

    def test_facts_and_cross_conversation_checks(self):
        self.assertFalse(sequential.check_reply({'content': 'Valeria'}, {'type': 'reply', 'contains_all': ['valeria', 'azul']})[0])
        self.assertFalse(sequential.check_reply({'content': 'Andrés y Valeria'},
                                                {'type': 'reply', 'contains': 'andres', 'forbids': ['valeria']})[0])

    def test_bounded_reasoning_is_not_added_to_reply_or_history(self):
        seen = []
        def model(payload):
            seen.append(payload)
            return response({'role': 'assistant', 'content': 'Hola.', 'reasoning_content': 'synthetic internal text'})
        conversation = sequential.ShadowConversation('system', model, think_budget=32)
        result = conversation.turn({'id': 'hi', 'text': 'hola', 'expected': {'type': 'reply'}})
        self.assertTrue(seen[0]['chat_template_kwargs']['enable_thinking'])
        self.assertGreater(result['first_reasoning_chars'], 0)
        self.assertNotIn('reasoning_content', result['first_response'])
        self.assertNotIn('synthetic internal text', json.dumps(conversation.messages))

    def test_manufacturer_reference_sampling_parameters_reach_native_api(self):
        seen = []
        def model(payload):
            seen.append(payload); return response({'role': 'assistant', 'content': 'Hola.'})
        conversation = sequential.ShadowConversation('system', model, sampling='qwen_non_thinking', seed=7)
        conversation.turn({'id': 'hi', 'text': 'hola', 'expected': {'type': 'reply'}})
        payload = seen[0]
        self.assertEqual((payload['temperature'], payload['top_p'], payload['top_k'], payload['min_p']), (.7, .8, 20, 0))
        self.assertEqual(payload['seed'], 7)
        self.assertFalse(payload['chat_template_kwargs']['enable_thinking'])


if __name__ == '__main__': unittest.main()
