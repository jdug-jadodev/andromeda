import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
spec = importlib.util.spec_from_file_location('agenda_transport_fixture', ROOT / 'scripts/test-agenda-transport.py')
fixture = importlib.util.module_from_spec(spec); spec.loader.exec_module(fixture)


class AgendaTransportFixtureTests(unittest.TestCase):
    def test_old_listener_is_closed_before_client_can_observe_ready(self):
        listener = Mock(); observed = []
        class StopFixture(Exception): pass
        def emit(conn, event):
            listener.close.assert_called_once()
            self.assertIsNone(fixture._fixture_listener)
            observed.append(event['type'])
            raise StopFixture()
        with patch.object(fixture, '_fixture_listener', listener), \
                patch.object(fixture, 'current', ('web', '', 'busca en Internet nebulosas')), \
                patch.object(fixture.wire, 'now_ms', return_value=100000), \
                patch.object(fixture.wire, 'send_event', side_effect=emit):
            with self.assertRaises(StopFixture):
                fixture.fixture_session(Mock(), None, None, 90, Mock(), 'synthetic_pcm', 'a' * 32, None, False, True)
        self.assertEqual(observed, ['ready'])

    def test_missing_listener_is_not_silently_accepted_or_replaced(self):
        with patch.object(fixture, '_fixture_listener', None):
            with self.assertRaises(RuntimeError): fixture.close_fixture_listener()


if __name__ == '__main__': unittest.main()
