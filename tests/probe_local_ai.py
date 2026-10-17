"""Device-only local model probe. Fixed public text; no mic, TTS or actions."""
import json
import sys
import time
import urllib.error
import urllib.request
from local_ai import LocalPlanner, AITurns

planner = LocalPlanner(sys.argv[1])
deadline = time.monotonic() + 30
while True:
    try:
        request = urllib.request.Request(
            'http://127.0.0.1:11435/health', headers={'Authorization': 'Bearer ' + planner.key})
        with planner.opener.open(request, timeout=2):
            break
    except (OSError, urllib.error.URLError):
        if time.monotonic() > deadline:
            raise RuntimeError('Local model unavailable')
        time.sleep(0.25)

cases = [('Hola, ¿cómo te llamas?', 'say')]
if sys.argv[2] == 'test':
    cases += [('Avísame dentro de dos minutos', 'create_timer'),
              ('Quita todas las cuentas regresivas', 'cancel_timer'),
              ('No canceles los temporizadores', 'say'),
              ('¿Cuántos temporizadores quedan?', 'list_timers')]
for text, expected in cases:
    began = time.monotonic()
    plan = planner(text)
    print(json.dumps({'test': 'local_llm_plan', 'input': text, 'plan': plan,
                      'expected': expected, 'passed': plan['action'] == expected,
                      'seconds': round(time.monotonic() - began, 3),
                      'microphone_opened': False, 'action_executed': False}, ensure_ascii=False), flush=True)
    assert plan['action'] == expected
    if expected == 'create_timer':
        assert plan['seconds'] == 120
    if expected == 'cancel_timer':
        assert plan['all'] is True
