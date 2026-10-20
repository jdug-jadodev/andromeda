"""Fixed public WAV -> real wake/Vosk/LFM/TLS, with a simulated web provider.

Runs only in separate staging. No Android client/executor, instrumentation,
microphone, playback, human context, external search, or operational deployment.
"""
import hashlib
import json
from pathlib import Path
import signal
import socket
import ssl
import struct
import sys
import threading
import time
import uuid
import wave

sys.path.insert(0, str(Path(__file__).resolve().parent))
import assistant_tools_model as adapter
import conversation_voice as wire
from doja_intents import normalized
from doja_stream import resource_ok, MODEL_DIR
from spanish_wake import SpanishWakeDetector

FIXTURE_NAME = 'web-public-nebulosa-sabina.wav'
FIXTURE_TEXT = 'Andrómeda, busca en Internet qué es una nebulosa.'
REQUEST_TEXT = 'busca en Internet qué es una nebulosa'
PUBLIC_SOURCE = {'url': 'https://example.org/public', 'title': 'Fuente pública simulada',
                 'excerpt': 'Extracto ficticio para validar el canal, no información consultada.'}
REGRESSIONS = [
    ('alarm_relative', 'pon una alarma en dos minutos', {'kind': 'agenda_proposal', 'event_kind': 'ALARM', 'delay_ms': 120000}),
    ('reminder_relative', 'recuérdame beber agua en dos minutos', {'kind': 'agenda_proposal', 'event_kind': 'REMINDER', 'delay_ms': 120000}),
    ('timer', 'pon un temporizador de dos minutos', {'kind': 'timer_proposal', 'seconds': 120}),
    ('web_asr_spelling', 'busca en internet que es una nebulosa', {'kind': 'web_result'}),
    ('web_alternate', 'consulta en la web nebulosas', {'kind': 'web_result'}),
    ('web_negated', 'no busques en Internet nebulosas', {'kind': 'no_action'}),
    ('web_reported', 'ayer dije busca en Internet nebulosas', {'kind': 'no_action'}),
    ('web_mixed', 'busca en Internet nebulosas y crea una alarma', {'kind': 'no_action'}),
    ('web_incomplete', 'busca en Internet', {'kind': 'no_action'}),
]


def correct_result(result, expected):
    if not isinstance(result, dict): return False
    if expected['kind'] == 'no_action': return result.get('kind') in {'reply', 'tools_rejected'}
    return all(type(result.get(key)) is type(value) and result[key] == value for key, value in expected.items())


def public_pcm(path):
    path = Path(path)
    if path.name != FIXTURE_NAME: raise ValueError('Unexpected public fixture')
    manifest = json.loads(path.with_suffix('.json').read_text())
    if (manifest.get('fixture_text') != FIXTURE_TEXT or manifest.get('offline_synthesis') is not True
            or hashlib.sha256(path.read_bytes()).hexdigest() != manifest.get('wav_sha256')):
        raise ValueError('Public fixture identity mismatch')
    with wave.open(str(path), 'rb') as audio:
        if (audio.getparams()[:3] != (1, 2, 16000) or audio.getcomptype() != 'NONE'
                or not 0 < audio.getnframes() <= 16000 * 12):
            raise ValueError('Expected bounded mono PCM16 at 16 kHz')
        return audio.readframes(audio.getnframes())


def frame(conn, kind, data):
    conn.sendall(struct.pack('!BI', kind, len(data)) + data)


def control(conn, action):
    if action != 'stop': raise ValueError('This diagnostic never acknowledges execution or TTS')
    frame(conn, 3, json.dumps({'action': action}).encode())


def read_event(conn):
    def take(length):
        raw = bytearray()
        while len(raw) < length:
            chunk = conn.recv(length - len(raw))
            if not chunk: raise EOFError('Public transport ended')
            raw.extend(chunk)
        return bytes(raw)
    length = struct.unpack('!I', take(4))[0]
    if not 0 < length <= 16384: raise ValueError('Invalid event size')
    return json.loads(take(length))


def connect(pin, deadline):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False; context.verify_mode = ssl.CERT_NONE
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    while True:
        try: raw = socket.create_connection(('127.0.0.1', 11436), timeout=1)
        except ConnectionRefusedError:
            if time.monotonic() >= deadline: raise TimeoutError('Public listener readiness')
            time.sleep(.05); continue
        try:
            conn = context.wrap_socket(raw, server_hostname='localhost')
            if hashlib.sha256(conn.getpeercert(binary_form=True)).hexdigest() != pin:
                conn.close(); raise ValueError('TLS pin mismatch')
            conn.settimeout(35)
            return conn
        except Exception:
            raw.close(); raise


def main():
    if len(sys.argv) not in (3, 4) or len(sys.argv) == 4 and sys.argv[3] != '--regressions':
        raise ValueError('Inference key, fixed public WAV and optional --regressions required')
    regressions = REGRESSIONS if len(sys.argv) == 4 else []
    signal.alarm(115)
    pcm = public_pcm(sys.argv[2])
    pin = json.loads(Path(__file__).with_name('conversation_voice.json').read_text())['tls_certificate_sha256']
    private = Path('/var/lib/andromeda-conversation')
    # The source is authenticated as synthetic_pcm. No Android service/app RAM
    # is opened or replaced; this client never accepts a microphone source.
    detector = SpanishWakeDetector('andrómeda', MODEL_DIR / 'vosk-model-small-es-0.42')
    model = adapter.AssistantToolsModel(sys.argv[1])
    phase = 'text_control'; calls = []; captured = {}
    def simulated_search(query, *_):
        calls.append({'phase': phase, 'query': query})
        return {'respuesta': PUBLIC_SOURCE['excerpt'], 'contexto_para_ia': '', 'fuentes': [dict(PUBLIC_SOURCE)]}
    adapter.buscar_en_internet = simulated_search  # Patch the actual current adapter, never contact a provider.
    original = model.interpret_result
    def capture(message, finish, request):
        result = original(message, finish, request)
        captured[phase] = {'kind': result['kind'], 'metrics': dict(model.context_metrics)}
        print(json.dumps({'event': 'public_web_native', 'phase': phase,
            'public_asr_or_fixture_request': request, 'raw_public_response': message,
            'finish_reason': finish, **captured[phase]}), flush=True)
        return result
    model.interpret_result = capture
    conn = None; sender = None; server = None; sending = threading.Event(); errors = []
    result = {'event': 'public_web_asr_summary', 'passed': False, 'real_vosk': True, 'real_lfm': False,
        'microphone_opened': False, 'audio_played': False, 'android_executor_present': False,
        'android_actions_executed': 0, 'web_provider_simulated': True, 'external_requests': 0,
        'human_context_used': False, 'human_request_used': False, 'app_instrumented': False,
        'operational_backend_modified': False, 'human_web_accepted': False, 'automatic_retry': False}
    try:
        if not resource_ok(): raise RuntimeError('Resource guard')
        began = wire.now_ms(); deadline = model.start(REQUEST_TEXT, began, began + 90000)
        if not model.done.wait(max(0, deadline - wire.now_ms()) / 1000 + .5): raise TimeoutError('Text control deadline')
        result['text_control_error'] = model.error
        result['text_control_kind'] = model.result.get('kind') if isinstance(model.result, dict) else None
        result['text_control_diagnostic'] = captured.get(phase, {}).get('metrics', {})
        result['regressions'] = []
        for identity, request, expected in regressions:
            phase = identity; before = len(calls)
            model.memory = wire.ShortMemory()
            began = wire.now_ms(); model.anchor_wall, model.anchor_boot = int(time.time() * 1000), began
            deadline = model.start(request, began, began + 90000)
            if not model.done.wait(max(0, deadline - wire.now_ms()) / 1000 + .5): raise TimeoutError('Public regression deadline')
            passed = model.error is None and correct_result(model.result, expected)
            if expected['kind'] == 'no_action': passed = passed and len(calls) == before
            result['regressions'].append({'case': identity, 'passed': passed, 'error': model.error,
                'kind': model.result.get('kind') if isinstance(model.result, dict) else None,
                'diagnostic': captured.get(phase, {}).get('metrics', {})})
        model.memory = wire.ShortMemory()  # Discard only this diagnostic's public context.
        phase = 'asr_pipeline'
        adapter.AssistantToolsModel = lambda _: model
        def serve():
            try:
                wire.serve(private / 'token', sys.argv[1], detector, 90, private / 'server-cert.pem',
                    private / 'server-key.pem', 'synthetic_pcm', resource_ok, assistant_tools=True)
            except Exception as error: errors.append(type(error).__name__)
        server = threading.Thread(target=serve, daemon=True, name='public-web-tls'); server.start()
        conn = connect(pin, time.monotonic() + 5)
        identity = uuid.uuid4().hex
        hello = {'protocol': 9, 'mode': 'assistant_tools', 'source': 'synthetic_pcm',
            'token': (private / 'token').read_text().strip(), 'voice_entry': True, 'catalog': [],
            'context': {'version': 1, 'id': identity, 'last_human_ms': 0, 'turns': []}}
        frame(conn, 1, json.dumps(hello).encode())
        ready = read_event(conn)
        if (ready.get('type') != 'ready' or ready.get('protocol') != 9
                or ready.get('input_source') != 'synthetic_pcm' or ready.get('restored_turns') != 0
                or ready.get('voice_entry') is not True or ready.get('assistant_tools') is not True):
            raise ValueError('Wrong public session')
        result['pinned_tls'] = True; result['wake_activations'] = 0
        sending.set()
        def send_pcm():
            began = time.monotonic()
            try:
                for cursor in range(0, len(pcm) + 64000, 640):
                    if not sending.is_set(): return
                    time.sleep(max(0, began + (cursor + 640) / 32000 - time.monotonic()))
                    if not sending.is_set(): return
                    chunk = pcm[cursor:cursor + 640].ljust(640, b'\0')
                    frame(conn, 4, struct.pack('!Q', wire.now_ms()) + chunk)
            except Exception as error:
                if sending.is_set(): errors.append(type(error).__name__)
        sender = threading.Thread(target=send_pcm, daemon=True, name='fixed-public-pcm'); sender.start()
        while True:
            event = read_event(conn); kind = event.get('type')
            if event.get('session_id') != ready['session_id']: raise ValueError('Unrelated session')
            if kind == 'context_selected':
                result['context_selection'] = event.get('selection'); continue
            if kind == 'state':
                if event.get('state') == 'command' and event.get('trigger') == 'voice': result['wake_activations'] += 1
                continue
            if kind == 'thinking':
                sending.clear(); sender.join(2)
                result['public_asr_text'] = event.get('text')
                result['asr_matches_public_request'] = normalized(event.get('text', '')) == normalized(REQUEST_TEXT)
                continue
            if kind == 'say':
                result['response_control'] = event.get('timer_control')
                result['sources_count'] = len(event.get('sources', []))
                result['response_actions_executed'] = event.get('actions_executed')
                result['offered_more'] = event.get('offered_more'); break
            if kind in {'agenda_execute', 'timer_execute'}:
                result['unexpected_mutation_proposal_refused'] = True; break
            if kind == 'error': result['backend_error'] = event.get('reason'); break
            raise ValueError('Unexpected public event')
        sending.clear()
        if sender is not None: sender.join(2)
        control(conn, 'stop')  # No execution or TTS-completion receipt is fabricated.
        result['pipeline_diagnostic'] = captured.get(phase, {}).get('metrics', {})
        result['simulated_searches'] = calls
        result['passed'] = (result.get('text_control_kind') == 'web_result'
            and result.get('asr_matches_public_request') is True and result.get('wake_activations') == 1
            and result.get('response_control') == 'web' and result.get('sources_count') == 1
            and result.get('response_actions_executed') == 0 and result.get('offered_more') is True
            and result['pipeline_diagnostic'].get('tools_validation') == 'web_results'
            and all(row['passed'] for row in result['regressions'])
            and len(calls) == (4 if regressions else 2)
            and all(normalized(call['query']) == normalized('nebulosas' if call['phase'] == 'web_alternate'
                else 'qué es una nebulosa') for call in calls))
    except Exception as error:
        result['error_kind'] = type(error).__name__  # No credentials or arbitrary exception strings.
    finally:
        sending.clear()
        if conn is not None:
            try: conn.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            conn.close()
        if sender is not None: sender.join(2)
        model.cancel()
        if server is not None: server.join(3)
        result['real_lfm'] = bool(captured)
        result['test_threads_stopped'] = not any(thread is not None and thread.is_alive() for thread in (sender, server))
        result['transport_errors'] = errors
        result['passed'] = result['passed'] and result['test_threads_stopped'] and not errors
        print(json.dumps(result), flush=True)
    return 0 if result['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
