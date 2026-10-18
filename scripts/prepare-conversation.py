"""Preload the fixed LFM conversation prefix, without microphone or actions."""
import json
from pathlib import Path
import sys
import time
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from conversation_voice import ConversationModel

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
model = ConversationModel(key_path)
try: model.prepare()
except Exception:
    model.cancel(); raise
print(json.dumps({'event': 'conversation_prepared', 'model': 'lfm',
                  'seconds': round(time.monotonic() - started, 3), 'microphone_opened': False,
                  'tools_present': False, 'actions_executed': 0}), flush=True)
