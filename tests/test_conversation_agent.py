import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from conversation_agent import ConversationAgent, Conversations, Tool, object_schema, validate_call, parse_native_response, chat_messages


def reply(text): return {'kind': 'reply', 'text': text}
def call(name, **args): return {'kind': 'tool', 'name': name, 'arguments': args}


class ConversationTests(unittest.TestCase):
    def test_history_reaches_model_on_followup(self):
        seen = []
        def model(messages, tools):
            seen.append(messages)
            return reply('Hola')
        agent = ConversationAgent(model, tools=[])
        agent('Me llamo Ana'); agent('¿Cómo me llamo?')
        self.assertEqual([m['role'] for m in seen[-1]], ['user', 'assistant', 'user'])
        self.assertIn('Ana', seen[-1][0]['content'])

    def test_conversations_expire_independently(self):
        now = [0]
        store = Conversations(lambda: now[0])
        store.add('a', 'user', 'hola')
        now[0] = 100; store.add('b', 'user', 'hola')
        now[0] = 3500; store.get('b', touch=True)
        now[0] = 3600; store.prune()
        self.assertNotIn('a', store.items)
        self.assertIn('b', store.items)

    def test_tool_output_does_not_refresh_human_inactivity(self):
        now = [0]
        store = Conversations(lambda: now[0]); store.get('a', touch=True)
        now[0] = 200; store.add('a', 'tool', 'result')
        self.assertEqual(store.get('a')['activity'], 0)

    def test_read_tool_result_goes_back_to_model(self):
        tool = Tool('lookup', 'test', object_schema({'query': {'type': 'string'}}), lambda query: {'found': 'Verified'})
        def model(messages, tools):
            if messages[-1]['role'] == 'tool':
                self.assertIn('Verified', messages[-1]['content'])
                return reply('Resultado verificado')
            return call('lookup', query='test')
        agent = ConversationAgent(model, tools=[tool])
        self.assertEqual(agent('consulta')['text'], 'Resultado verificado')

    def test_android_roundtrip_and_result_correlation(self):
        tool = Tool('list_timers', 'list', object_schema({}), android='list_timers')
        def model(messages, tools):
            return reply('No quedan') if messages[-1]['role'] == 'tool' else call('list_timers')
        agent = ConversationAgent(model, tools=[tool])
        event = agent('lista')
        self.assertEqual(event['type'], 'list_timers')
        with self.assertRaises(ValueError): agent.complete('wrong-id', True, 'No hay temporizadores')
        self.assertEqual(agent.complete(event['tool_call_id'], True, 'No hay temporizadores')['text'], 'No quedan')
        with self.assertRaises(ValueError): agent.complete(event['tool_call_id'], True, 'duplicate')

    def test_mutation_is_confirmed_then_reports_actual_result(self):
        tool = Tool('create_timer', 'timer', object_schema({'seconds': {'type': 'integer', 'minimum': 1, 'maximum': 86400}}),
                    android='timer', mutation=True)
        agent = ConversationAgent(lambda *_: call('create_timer', seconds=120), tools=[tool])
        self.assertEqual(agent('avísame')['type'], 'say')
        event = agent('Andrómeda, sí')
        self.assertEqual(event['type'], 'timer')
        self.assertEqual(agent.complete(event['tool_call_id'], False, 'Fallo real')['text'], 'Fallo real')

    def test_unsupported_tools_and_parameters_rejected(self):
        with self.assertRaises(ValueError): validate_call(call('shell', command='id'), [])
        tool = Tool('list', 'list', object_schema({}))
        with self.assertRaises(ValueError): validate_call(call('list', url='http://private'), [tool])

    def test_bounded_tool_loop(self):
        hits = []
        tool = Tool('lookup', 'test', object_schema({}), lambda: hits.append(1))
        agent = ConversationAgent(lambda *_: call('lookup'), tools=[tool])
        self.assertIn('límite', agent('hola')['text'])
        self.assertEqual(len(hits), 3)

    def test_memory_bounded(self):
        store = Conversations()
        for _ in range(30): store.add('a', 'user', 'x' * 1000)
        self.assertLessEqual(sum(len(m['content']) for m in store.get('a')['messages']), 2600)

    def test_protocol_messages_do_not_evict_opening_after_one_action(self):
        store = Conversations()
        store.add('a', 'user', 'Me llamo Ana')
        for _ in range(13): store.add('a', 'assistant', 'short tool protocol entry')
        self.assertEqual(store.get('a')['messages'][0]['content'], 'Me llamo Ana')
        self.assertLessEqual(len(store.get('a')['messages']), 24)

    def test_native_lfm_tool_format_parsed_as_data(self):
        tool = Tool('lookup', 'test', object_schema({'query': {'type': 'string'}}))
        value = parse_native_response("<|tool_call_start|>[lookup(query='Colombia')]<|tool_call_end|>", [tool])
        self.assertEqual(value, call('lookup', query='Colombia'))
        self.assertEqual(parse_native_response('Hola Lucía.<|im_end|>', [tool]), reply('Hola Lucía.'))

    def test_native_parser_rejects_code_and_unregistered_tools(self):
        for expression in ["[__import__('os').system('id')]", '[shell()]', '[f(**data)]', '[f(query=1+2)]', '[f(), f()]']:
            with self.subTest(expression=expression), self.assertRaises(ValueError):
                parse_native_response('<|tool_call_start|>' + expression + '<|tool_call_end|>', [])

    def test_api_history_keeps_last_question_and_tool_pairing(self):
        tool = Tool('list', 'test', object_schema({}))
        history = [{'role': 'user', 'content': 'lista'},
                   {'role': 'assistant', 'content': __import__('json').dumps(call('list'))},
                   {'role': 'tool', 'content': '{"result":"verified"}'},
                   {'role': 'user', 'content': '¿cuál es el siguiente?'}]
        result = chat_messages(history, [tool])
        self.assertEqual(result[-1], history[-1])
        self.assertEqual(result[1]['tool_calls'][0]['id'], result[2]['tool_call_id'])
        self.assertIn('verified', result[2]['content'])

    def test_orphaned_tool_result_not_forged_into_user_message(self):
        result = chat_messages([{'role': 'tool', 'content': 'old'}, {'role': 'user', 'content': 'hola'}], [])
        self.assertEqual(result, [{'role': 'user', 'content': 'hola'}])

    def test_runtime_context_preserved_not_recomputed_each_request(self):
        history = [{'role': 'user', 'content': 'hola', 'runtime': {'local_datetime': '2026-10-16T12:00:00-05:00'}}]
        first = chat_messages(history, [])
        history.append({'role': 'assistant', 'content': 'hola'})
        history.append({'role': 'user', 'content': 'seguimos', 'runtime': {'local_datetime': '2026-10-16T12:01:00-05:00'}})
        second = chat_messages(history, [])
        self.assertEqual(first, second[:len(first)])


if __name__ == '__main__': unittest.main()
