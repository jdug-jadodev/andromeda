"""Short fixed-text controls isolating native chat template vs manual prompt."""
import json
import sys
import time
import urllib.request
from local_ai import LocalPlanner

client = LocalPlanner(sys.argv[1])
def request(path, payload=None):
    query = urllib.request.Request('http://127.0.0.1:11435' + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={'Authorization': 'Bearer ' + client.key, 'Content-Type': 'application/json'})
    with client.opener.open(query, timeout=45) as response:
        return json.loads(response.read(65536))

deadline = time.monotonic() + 30
while True:
    try:
        request('/health'); break
    except OSError:
        if time.monotonic() >= deadline: raise
        time.sleep(.25)
props = request('/props')
template = props.get('chat_template', '')
print(json.dumps({'test': 'installed_template', 'template': template[:2500],
                  'bos_token': props.get('bos_token'), 'eos_token': props.get('eos_token')}), flush=True)
for language, name, first, question in [('es', 'Lucía', 'Me llamo Lucía.', '¿Cómo me llamo?'),
                                        ('en', 'Alice', 'My name is Alice.', 'What is my name?')]:
    messages = [{'role': 'user', 'content': first}, {'role': 'assistant', 'content': 'Hola.' if language == 'es' else 'Hello.'},
                {'role': 'user', 'content': question}]
    for mode in ['native_chat', 'manual_prompt']:
        payload = {'temperature': 0, 'seed': 42, 'stream': False}
        if mode == 'native_chat':
            payload.update(messages=messages, max_tokens=48)
            path = '/v1/chat/completions'
        else:
            prompt = ''.join('<|im_start|>' + m['role'] + '\n' + m['content'] + '<|im_end|>\n' for m in messages)
            payload.update(prompt=prompt + '<|im_start|>assistant\n', n_predict=48, cache_prompt=True)
            path = '/completion'
        began = time.monotonic()
        result = request(path, payload)
        content = result.get('content') if mode == 'manual_prompt' else result['choices'][0]['message'].get('content')
        print(json.dumps({'test': 'protocol_control', 'language': language, 'mode': mode, 'answer': content,
                          'seconds': round(time.monotonic() - began, 3), 'timings': result.get('timings'),
                          'usage': result.get('usage'), 'contains_name': name.lower() in str(content).lower()}, ensure_ascii=False), flush=True)
