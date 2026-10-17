"""Read-only RAM samples around a bounded model warmup. No mic/TTS/actions."""
import json
from pathlib import Path
import sys
import time
import urllib.request


def fields(path):
    result = {}
    for line in Path(path).read_text().splitlines():
        name, _, value = line.partition(':')
        words = value.split()
        if words and words[0].isdigit(): result[name] = int(words[0])
    return result


def sample(label, pid=None):
    memory = fields('/proc/meminfo')
    record = {'event': 'memory_sample', 'label': label, 'units': 'KiB',
              'mem_total': memory['MemTotal'], 'mem_available': memory['MemAvailable'],
              'mem_free': memory['MemFree'], 'cached': memory['Cached'],
              'sreclaimable': memory.get('SReclaimable'),
              'swap_used': memory['SwapTotal'] - memory['SwapFree'],
              'battery_deci_c': int(Path('/sys/class/power_supply/battery/temp').read_text()),
              'microphone_opened': False}
    if pid is not None:
        if Path(f'/proc/{pid}/comm').read_text().strip() != 'llama-server':
            raise ValueError('Unexpected process')
        status = fields(f'/proc/{pid}/status')
        record.update(server_rss=status.get('VmRSS'), server_peak_rss=status.get('VmHWM'))
        try:
            rollup = Path(f'/proc/{pid}/smaps_rollup')
            if rollup.exists():
                maps = fields(rollup)
            else:
                maps = {}
                for line in Path(f'/proc/{pid}/smaps').read_text().splitlines():
                    key, _, value = line.partition(':')
                    if key in {'Pss', 'Private_Clean', 'Private_Dirty', 'Shared_Clean', 'Shared_Dirty', 'SwapPss'}:
                        maps[key] = maps.get(key, 0) + int(value.split()[0])
            record.update(server_pss=maps.get('Pss'), server_private_clean=maps.get('Private_Clean'),
                          server_private_dirty=maps.get('Private_Dirty'), server_swap_pss=maps.get('SwapPss'))
        except OSError as error:
            record['pss_unavailable'] = type(error).__name__
    print(json.dumps(record), flush=True)


def main():
    if sys.argv[1] == 'snapshot':
        sample(sys.argv[2]); return
    if sys.argv[1] != 'warm': raise ValueError('Unknown measurement mode')
    key = Path(sys.argv[2]).read_text().strip()
    if len(key) < 32: raise ValueError('Missing credential')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def request(path, payload=None, timeout=30):
        query = urllib.request.Request('http://127.0.0.1:11435' + path,
            data=json.dumps(payload).encode() if payload is not None else None,
            headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
        with opener.open(query, timeout=timeout) as response:
            raw = response.read(65537)
        if len(raw) > 65536: raise ValueError('Oversized response')
        return json.loads(raw)
    deadline = time.monotonic() + 30
    while True:
        try: request('/health', timeout=2); break
        except OSError:
            if time.monotonic() >= deadline: raise
            time.sleep(.25)
    pid_text = Path(sys.argv[3]).read_text().strip()
    if not pid_text.isdigit() or int(pid_text) <= 1: raise ValueError('Invalid PID')
    pid = int(pid_text)
    sample('loaded_before_warmup', pid)
    request('/v1/chat/completions', {'messages': [{'role': 'user', 'content': 'Hola.'}],
        'max_tokens': 8, 'temperature': .7, 'top_p': .8, 'top_k': 20, 'min_p': 0,
        'seed': 42, 'stream': False, 'chat_template_kwargs': {'enable_thinking': False}})
    sample('loaded_after_warmup', pid)
    time.sleep(2)  # Explicit idle sample, not waiting for another task's progress.
    sample('loaded_idle', pid)


if __name__ == '__main__': main()
