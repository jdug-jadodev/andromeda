"""Fixed text, real local model. No microphone or real Android actions."""
import json
import sys
import time
import urllib.request
from conversation_agent import ChatModel, ConversationAgent, default_tools

model = ChatModel(sys.argv[1])
deadline = time.monotonic() + 30
while True:
    try:
        request = urllib.request.Request('http://127.0.0.1:11435/health', headers={'Authorization': 'Bearer ' + model.key})
        with model.opener.open(request, timeout=2): break
    except OSError:
        if time.monotonic() >= deadline: raise
        time.sleep(.25)

agent = ConversationAgent(model)
def turn(text):
    began = time.monotonic()
    result = agent(text)
    print(json.dumps({'test': 'conversational_agent', 'input': text, 'result': result,
                      'metrics': getattr(model, 'last_metrics', {}),
                      'seconds': round(time.monotonic() - began, 3),
                      'microphone_opened': False, 'android_action_executed': False}, ensure_ascii=False), flush=True)
    return result

first = turn('Hola, me llamo Lucía.')
assert first['type'] == 'say' and not first.get('error'), first
if sys.argv[2] == 'test':
    failures = []
    answer = turn('¿Cómo me llamo?')['text'].lower()
    if 'luc' not in answer or 'no tengo' in answer or 'no sé' in answer:
        failures.append('conversation_recall')
    result = turn('Avísame dentro de dos minutos')
    if result['type'] == 'say' and agent.store.get(agent.id)['confirmation'] is not None:
        result = turn('Andrómeda, sí')
        assert result['type'] == 'timer' and result['seconds'] == 120
        # Simulated Android result, explicitly not a real scheduled timer.
        assert agent.complete(result['tool_call_id'], True, 'Temporizador de prueba simulado: creado.')['type'] == 'say'
    else:
        failures.append('create_timer_tool_selection')
    result = turn('¿Cuántos temporizadores quedan?')
    if result['type'] == 'list_timers':
        result = agent.complete(result['tool_call_id'], True, 'Hay un temporizador de prueba simulado, quedan 100 segundos.')
        print(json.dumps({'test': 'model_after_android_result', 'result': result, 'simulated_executor': True}, ensure_ascii=False), flush=True)
        if result['type'] != 'say' or result.get('error'):
            failures.append('answer_after_android_result')
    else:
        failures.append('list_timers_tool_selection')
    hits = []
    source = next(t for t in agent.tools if t.name == 'search_encyclopedia')
    original_handler = source.handler
    def tracked_search(query):
        hits.append(query)
        return original_handler(query)
    source.handler = tracked_search
    result = turn('Consulta Wikipedia sobre biodiversidad de Colombia y resume lo encontrado.')
    print(json.dumps({'test': 'model_selected_internet_tool', 'queries': hits, 'passed': bool(hits)}), flush=True)
    if not hits:
        failures.append('internet_tool_selection')
    if result.get('error'):
        failures.append('answer_after_internet_result')
    from internet_tools import search_encyclopedia
    try:
        found = search_encyclopedia('Colombia')
        print(json.dumps({'test': 'real_encyclopedia_connection', 'results': len(found['results']), 'passed': bool(found['results'])}), flush=True)
        if not found['results']: failures.append('internet_source_empty')
    except (OSError, ValueError) as error:
        failures.append('internet_source_unavailable')
        print(json.dumps({'test': 'real_encyclopedia_connection', 'passed': False, 'error': str(error)}), flush=True)
    print(json.dumps({'test': 'chat_agent_summary', 'failures': failures, 'passed': not failures}), flush=True)
    assert not failures, failures
