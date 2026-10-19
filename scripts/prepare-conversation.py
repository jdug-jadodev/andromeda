"""Preload the fixed LFM conversation prefix, without microphone or actions."""
import json
from pathlib import Path
import sys
import time
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from conversation_voice import ConversationModel

timer_trial = len(sys.argv) == 3 and sys.argv[2] == '--timer-trial'
assistant_tools = len(sys.argv) == 3 and sys.argv[2] == '--assistant-tools'
if len(sys.argv) not in (2, 3) or len(sys.argv) == 3 and not (timer_trial or assistant_tools): raise ValueError('Invalid preparation mode')
if assistant_tools:
    from assistant_tools_model import AssistantToolsModel
    model_type = AssistantToolsModel
elif timer_trial:
    from timer_trial_model import TimerTrialModel
    model_type = TimerTrialModel
else: model_type = ConversationModel
key_path = Path(sys.argv[1])
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
started = time.monotonic(); deadline = started + 30
while True:
    try:
        with opener.open('http://127.0.0.1:11435/health', timeout=2) as response:
            if response.status == 200 and key_path.with_name('server.pid').exists(): break
    except OSError:
        pass
    if time.monotonic() >= deadline: raise TimeoutError('Inference preparation limit')
    time.sleep(.25)
model = model_type(key_path)
if assistant_tools:
    from conversation_voice import now_ms, SYSTEM
    body = model.payload([{'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': 'Hola.'}], 1)
    deadline = now_ms() + 30000
    rendered = model.post('/apply-template', dict(body, add_generation_prompt=True), deadline)['prompt']
    tokens = model.post('/tokenize', {'content': rendered, 'add_special': True, 'parse_special': True}, deadline)['tokens']
    if len(tokens) > 1536: model.cancel(); raise ValueError('Tools prefix exceeds input budget')
    print(json.dumps({'event': 'tools_prefix_budget', 'input_tokens': len(tokens), 'input_token_budget': 1536}), flush=True)
try: model.prepare()
except Exception:
    model.cancel(); raise
print(json.dumps({'event': 'conversation_prepared', 'model': 'lfm',
                  'seconds': round(time.monotonic() - started, 3), 'microphone_opened': False,
                  'tools_present': timer_trial or assistant_tools, 'actions_executed': 0}), flush=True)
