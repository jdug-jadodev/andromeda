import os
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'backend'))
import web_search_env as env
import with_web_search_env as launcher
import check_web_search_config as checker

PUBLIC = 'WEB_SEARCH_URL=https://search.example.org\nWEB_SEARCH_TOKEN=public-dummy-token\n'


class WebSearchEnvTests(unittest.TestCase):
    def test_minimal_dotenv_blank_comments_crlf_quotes_and_no_shell_execution(self):
        self.assertEqual(env.parse_env('# private file\r\n\r\nWEB_SEARCH_URL="https://search.example.org"\r\nWEB_SEARCH_TOKEN=\'public-dummy-token\'\r\n'),
            {'WEB_SEARCH_URL': 'https://search.example.org', 'WEB_SEARCH_TOKEN': 'public-dummy-token'})
        for raw in [PUBLIC + 'TAVILY_API_KEY=forbidden', PUBLIC + 'WEB_SEARCH_TOKEN=duplicate',
                    'export ' + PUBLIC, PUBLIC.replace('public-dummy-token', '$(id)'),
                    PUBLIC.replace('public-dummy-token', '`id`'), PUBLIC.replace('public-dummy-token', '"unclosed'),
                    'WEB_SEARCH_URL=https://search.example.org', PUBLIC.replace('public-dummy-token', ''), 'x' * 8193]:
            with self.subTest(raw=raw), self.assertRaises(ValueError) as error: env.parse_env(raw)
            self.assertNotIn(raw, str(error.exception))

    def test_config_reads_only_allowed_names_environment_wins_no_tavily_key(self):
        values = {'WEB_SEARCH_URL': 'https://env.example.org', 'OTHER': 'preserve'}
        with patch('web_search_env.load_private_env', return_value=env.parse_env(PUBLIC)):
            self.assertTrue(env.configure(values))
        self.assertEqual(values, {'WEB_SEARCH_URL': 'https://env.example.org', 'WEB_SEARCH_TOKEN': 'public-dummy-token', 'OTHER': 'preserve'})

    def test_missing_config_does_not_break_voice_or_nonweb_tools(self):
        values = {'OTHER': 'preserve'}
        with patch('web_search_env.load_private_env', return_value={}): self.assertTrue(env.configure(values))
        self.assertEqual(values, {'OTHER': 'preserve'})
        for error in (OSError('private path and token'), ValueError('private content'), UnicodeError('private content')):
            values = {'OTHER': 'preserve', 'WEB_SEARCH_TOKEN': 'private'}
            with patch('web_search_env.load_private_env', side_effect=error): self.assertFalse(env.configure(values))
            self.assertEqual(values, {'OTHER': 'preserve'})

    def test_private_file_security_checks_and_no_implicit_project_dotenv_loading(self):
        self.assertEqual(env.ENV_PATH.as_posix(), '/var/lib/andromeda-web-search/.env')
        self.assertNotIn('TAVILY', str(env.NAMES))

    @unittest.skipUnless(os.name == 'posix', 'Real POSIX ownership/symlink checks require Linux')
    def test_real_private_directory_file_permissions_owner_and_symlink_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory); folder.chmod(0o700); path = folder/'.env'
            self.assertEqual(env.load_private_env(path), {})
            path.write_text(PUBLIC, encoding='utf-8'); path.chmod(0o600)
            self.assertEqual(env.load_private_env(path), env.parse_env(PUBLIC))
            path.chmod(0o644)
            with self.assertRaises(ValueError): env.load_private_env(path)
            path.chmod(0o600); folder.chmod(0o755)
            with self.assertRaises(ValueError): env.load_private_env(path)
            folder.chmod(0o700); link = folder/'link'; link.symlink_to(path)
            with self.assertRaises(OSError): env.load_private_env(link)
            with patch('web_search_env.os.geteuid', return_value=os.geteuid() + 1), self.assertRaises(ValueError): env.load_private_env(path)

    def test_launch_target_is_fixed_and_token_never_appears_in_argv(self):
        target = str(ROOT/'backend/doja_stream.py')
        argv = ['with_web_search_env.py', target, '--assistant-tools']
        with patch.object(sys, 'argv', argv), patch('with_web_search_env.configure', return_value=True) as configure, \
                patch('with_web_search_env.runpy.run_path') as run:
            launcher.main(); configure.assert_called_once_with(os.environ)
            self.assertEqual(sys.argv, [target, '--assistant-tools'])
            run.assert_called_once_with(str(Path(target).resolve()), run_name='__main__')
        with patch.object(sys, 'argv', ['with_web_search_env.py', 'arbitrary.py']), self.assertRaises(SystemExit): launcher.main()

    def test_android_launcher_reads_after_clean_env_only_for_tool_backend(self):
        script = (ROOT/'scripts/start-andromeda-conversation.sh').read_text(encoding='utf-8')
        self.assertIn('/usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin ANDROMEDA_MODEL=lfm', script)
        self.assertIn('debian_user /opt/doha-voice/pilot/with_web_search_env.py "$@"', script)
        self.assertEqual(script.count('voice_user /opt/doha-voice/pilot/doja_stream.py'), 2)
        self.assertNotIn('WEB_SEARCH_TOKEN=', script)

    def test_offline_checker_reports_only_status_not_values_and_never_calls_network(self):
        for ok, exception, expected in [(True, None, 'ready'), (False, None, 'configuration_invalid'),
                (True, checker.InternetSearchError('configuration_missing'), 'configuration_missing')]:
            output = io.StringIO()
            with patch('check_web_search_config.configure', return_value=ok), \
                    patch('check_web_search_config.configuration', side_effect=exception) as validate, contextlib.redirect_stdout(output):
                result = checker.main()
            self.assertEqual(result, 0 if expected == 'ready' else 1)
            self.assertEqual(json.loads(output.getvalue()), {'web_search_configuration': expected, 'network_requests': 0})
            if not ok: validate.assert_not_called()


if __name__ == '__main__': unittest.main()
