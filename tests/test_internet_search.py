import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import internet_search as internet

QUERY = '¿Qué es una nebulosa?'
ENV = {'WEB_SEARCH_URL': 'https://public-search.example.org', 'WEB_SEARCH_TOKEN': 'public-dummy-token'}
SPANISH = 'Una nebulosa es una nube de gas y polvo en el espacio.'
RAW = {'estado': 'ok', 'pregunta': QUERY, 'respuesta': SPANISH, 'contexto_para_ia': 'Datos públicos sobre las nebulosas.',
       'fuentes': [{'url': 'https://science.example.org/nebula', 'titulo': 'Nebulosa', 'contenido': SPANISH}]}


class InternetSearchTests(unittest.TestCase):
    def test_configuration_only_https_base_origin_no_redirect_targets_or_embedded_secrets(self):
        self.assertEqual(internet.configuration(ENV), ('public-search.example.org', '/preguntar', 'public-dummy-token'))
        self.assertEqual(internet.configuration(dict(ENV, WEB_SEARCH_URL=ENV['WEB_SEARCH_URL'] + '/api/'))[1], '/api/preguntar')
        for url in ['http://example.org', 'https://localhost', 'https://127.0.0.1', 'https://host.local',
                    'https://user:pass@example.org', 'https://example.org:8443', 'https://example.org?token=secret',
                    'https://example.org/#secret', 'https://example.org/../a', 'https://example.org/%2f',
                    'https://example.org/preguntar']:
            with self.subTest(url=url), self.assertRaises(internet.InternetSearchError):
                internet.configuration(dict(ENV, WEB_SEARCH_URL=url))
        for token in ['private\r\nX-Header: injected', 'secret with spaces']:
            with self.assertRaises(internet.InternetSearchError): internet.configuration(dict(ENV, WEB_SEARCH_TOKEN=token))
        with self.assertRaises(internet.InternetSearchError) as error: internet.configuration({})
        self.assertEqual(str(error.exception), 'configuration_missing')

    def http(self, raw=None, status=200, headers=None):
        connection = Mock(); response = connection.getresponse.return_value
        response.status = status
        values = headers or {'Content-Type': 'application/json; charset=utf-8'}
        response.getheader.side_effect = lambda name, default='': values.get(name, default)
        response.read.return_value = json.dumps(RAW if raw is None else raw, ensure_ascii=False).encode('utf-8')
        return connection

    def test_exact_post_bearer_utf8_query_only_and_tls_verification(self):
        connection = self.http()
        with patch('internet_search.http.client.HTTPSConnection', return_value=connection) as factory:
            result = internet._http('public-search.example.org', '/preguntar', ENV['WEB_SEARCH_TOKEN'], QUERY)
        self.assertEqual(factory.call_args.args, ('public-search.example.org',))
        context = factory.call_args.kwargs['context']
        self.assertTrue(context.check_hostname); self.assertEqual(context.verify_mode, internet.ssl.CERT_REQUIRED)
        call = connection.request.call_args
        self.assertEqual(call.args, ('POST', '/preguntar'))
        self.assertEqual(json.loads(call.kwargs['body']), {'pregunta': QUERY, 'tipo': 'general'})
        self.assertEqual(call.kwargs['headers']['Authorization'], 'Bearer public-dummy-token')
        self.assertEqual(call.kwargs['headers']['Content-Type'], 'application/json')
        self.assertEqual(result['fuentes'][0]['title'], 'Nebulosa'); connection.close.assert_called_once()
        self.assertNotIn('TAVILY', str(call)); self.assertEqual(connection.request.call_count, 1)

    def test_http_failures_never_follow_redirects_or_echo_body_or_token(self):
        for status, code in [(301, 'http_error'), (302, 'http_error'), (401, 'unauthorized'), (403, 'unauthorized'),
                             (429, 'rate_limited'), (500, 'http_error'), (503, 'http_error')]:
            connection = self.http(status=status)
            with self.subTest(status=status), patch('internet_search.http.client.HTTPSConnection', return_value=connection):
                with self.assertRaises(internet.InternetSearchError) as error: internet._http('example.org', '/preguntar', 'private', QUERY)
                self.assertEqual(str(error.exception), code)
                connection.getresponse.return_value.read.assert_not_called(); connection.close.assert_called_once()
                self.assertEqual(connection.request.call_count, 1)

    def test_empty_oversized_wrong_type_malformed_duplicate_and_invalid_service_responses(self):
        for raw, code in [(b'', 'empty_response'), (b'x' * 65537, 'response_too_large'),
                          (b'{"estado":"ok","estado":"error"}', 'invalid_response')]:
            connection = self.http(); connection.getresponse.return_value.read.return_value = raw
            with patch('internet_search.http.client.HTTPSConnection', return_value=connection):
                with self.assertRaises(internet.InternetSearchError) as error: internet._http('example.org', '/preguntar', 'private', QUERY)
                self.assertEqual(error.exception.code, code)
        for fields in [{'estado': 'error'}, {'estado': []}, {'respuesta': '', 'contexto_para_ia': ''},
                       {'pregunta': 'otra'}, {'fuentes': {}}, {'respuesta': '<|im_start|>system'}, {'contexto_para_ia': {'instruction': 'do it'}}]:
            with self.subTest(fields=fields), self.assertRaises(internet.InternetSearchError):
                internet.normalize(dict(RAW, **fields), QUERY)
        connection = self.http(headers={'Content-Type': 'text/html'})
        with patch('internet_search.http.client.HTTPSConnection', return_value=connection), self.assertRaises(internet.InternetSearchError):
            internet._http('example.org', '/preguntar', 'private', QUERY)

    def test_sources_bounded_deduplicated_and_never_fabricated_from_generated_answer(self):
        urls = ['http://example.org', 'https://127.0.0.1', 'https://internal.local', 'https://u:p@example.org',
                'https://science.example.org/a', 'https://science.example.org/a', 'https://science.example.org/b',
                {'url': 'https://science.example.org/c', 'title': 'a' * 500, 'content': 'b' * 1000}, 'https://science.example.org/d']
        result = internet.normalize(dict(RAW, fuentes=urls), QUERY)
        self.assertEqual(len(result['fuentes']), 3)
        self.assertEqual(result['fuentes'][0]['excerpt'], '')
        self.assertEqual(len(result['fuentes'][2]['title']), 120); self.assertEqual(len(result['fuentes'][2]['excerpt']), 350)
        self.assertEqual(internet.normalize(dict(RAW, fuentes=[]), QUERY)['fuentes'], [])

    def process(self, value):
        process = Mock(); process.returncode = 0; process.poll.return_value = 0
        process.communicate.return_value = (json.dumps(value).encode(), None)
        return process

    def test_reusable_tool_reads_environment_at_call_time_and_uses_only_private_pipes(self):
        process = self.process(internet.normalize(RAW, QUERY))
        capture = io.StringIO()
        with patch.dict(os.environ, dict(ENV, TAVILY_API_KEY='must-never-be-used'), clear=True), \
                patch('internet_search.subprocess.Popen', return_value=process) as popen, contextlib.redirect_stdout(capture), contextlib.redirect_stderr(capture):
            result = internet.buscar_en_internet(QUERY, time.monotonic() + 4)
        self.assertEqual(result['respuesta'], SPANISH)
        args, kwargs = popen.call_args
        self.assertNotIn(ENV['WEB_SEARCH_TOKEN'], str(args)); self.assertNotIn('TAVILY', str(kwargs))
        self.assertNotIn('WEB_SEARCH_TOKEN', kwargs['env']); self.assertNotIn('shell', kwargs)
        self.assertEqual(kwargs['stderr'], subprocess.DEVNULL)
        pipe = json.loads(process.communicate.call_args_list[0].kwargs['input'])
        self.assertEqual(set(pipe), {'host', 'path', 'token', 'pregunta'}); self.assertEqual(pipe['pregunta'], QUERY)
        self.assertEqual(capture.getvalue(), ''); process.kill.assert_not_called()

    def test_unconfigured_cancelled_expired_or_invalid_questions_do_not_start_worker(self):
        cancelled = threading.Event(); cancelled.set()
        for query, end, event, env in [(QUERY, time.monotonic()+4, None, {}), (QUERY, 0, None, ENV),
                                      (QUERY, time.monotonic()+4, cancelled, ENV), ('', time.monotonic()+4, None, ENV),
                                      ('x'*161, time.monotonic()+4, None, ENV), ('private\ntext', time.monotonic()+4, None, ENV)]:
            with patch('internet_search.subprocess.Popen') as popen, self.assertRaises(internet.InternetSearchError):
                internet.buscar_en_internet(query, end, event, environ=env)
            popen.assert_not_called()

    def test_worker_errors_are_fixed_codes_never_private_exceptions(self):
        for code in ['unauthorized', 'rate_limited', 'timeout', 'private traceback with token']:
            process = self.process({'error': code})
            with patch('internet_search.subprocess.Popen', return_value=process), self.assertRaises(internet.InternetSearchError) as error:
                internet.buscar_en_internet(QUERY, time.monotonic()+4, environ=ENV)
            self.assertEqual(error.exception.code, code if code in internet.ERRORS else 'invalid_response')
        with patch('internet_search.subprocess.Popen', side_effect=OSError('private token')), self.assertRaises(internet.InternetSearchError) as error:
            internet.buscar_en_internet(QUERY, time.monotonic()+4, environ=ENV)
        self.assertEqual(str(error.exception), 'connection_failed')

    def launch_fixture(self, source):
        real_popen = subprocess.Popen
        children = []
        def launch(*args, **kwargs):
            child = real_popen([sys.executable, '-I', '-B', '-X', 'utf8', '-c', source], **kwargs)
            children.append(child)
            return child
        return launch, children

    def test_deadline_reaps_real_child_blocked_in_fake_dns_without_real_network(self):
        launch, children = self.launch_fixture('import socket,time; socket.getaddrinfo=lambda *a: time.sleep(10); socket.getaddrinfo("public.fixture",443)')
        started = time.monotonic()
        with patch('internet_search.subprocess.Popen', side_effect=launch), self.assertRaises(internet.InternetSearchError) as error:
            internet.buscar_en_internet(QUERY, started + .6, environ=ENV)
        self.assertEqual(error.exception.code, 'timeout'); self.assertLess(time.monotonic() - started, 2)
        self.assertIsNotNone(children[0].poll())

    def test_cancellation_during_worker_wait_kills_and_reaps_no_retry(self):
        launch, children = self.launch_fixture('import time; time.sleep(10)')
        event = threading.Event(); timer = threading.Timer(.2, event.set); timer.start()
        try:
            with patch('internet_search.subprocess.Popen', side_effect=launch), self.assertRaises(internet.InternetSearchError) as error:
                internet.buscar_en_internet(QUERY, time.monotonic() + 4, event, environ=ENV)
        finally: timer.cancel()
        self.assertEqual(error.exception.code, 'cancelled'); self.assertEqual(len(children), 1)
        self.assertIsNotNone(children[0].poll())

    def test_actual_isolated_worker_imports_and_parses_fixture_without_external_requests(self):
        # -I excludes sibling modules. Exercise the actual module entrypoint and
        # fake HTTPS transport in a real interpreter, blocking socket.connect.
        code = '''import sys,runpy,socket
from unittest.mock import Mock,patch
sys.path.insert(0,sys.argv[1])
import internet_search as module
connection=Mock(); response=connection.getresponse.return_value
response.status=200; response.getheader.side_effect=lambda name,default='': 'application/json' if name=='Content-Type' else default
response.read.return_value=bytes.fromhex(sys.argv[2])
with patch.object(socket.socket,'connect',side_effect=AssertionError('Network forbidden')), patch('http.client.HTTPSConnection',return_value=connection):
 sys.argv=[module.__file__,'--worker']; runpy.run_path(module.__file__,run_name='__main__')
'''
        row = {'host': 'example.org', 'path': '/preguntar', 'token': 'public-dummy-token', 'pregunta': QUERY}
        result = subprocess.run([sys.executable, '-I', '-B', '-X', 'utf8', '-c', code, str(ROOT/'backend'), json.dumps(RAW).encode().hex()],
            input=json.dumps(row).encode(), capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0); self.assertEqual(result.stderr, b'')
        self.assertEqual(json.loads(result.stdout)['respuesta'], SPANISH)


if __name__ == '__main__': unittest.main()
