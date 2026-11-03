"""Tests unitarios del parser y el cliente de TV (Fase A, sin TV real)."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

from tv_intents import parse_volume, parse_input, parse_scene, parse_audio, parse_app
import tv_control


class TvIntentsTests(unittest.TestCase):
    def test_parse_volume(self):
        self.assertEqual(parse_volume('volumen al 20'), {'level': 20})
        self.assertEqual(parse_volume('sube el volumen'), {'delta': 5})
        self.assertEqual(parse_volume('baja el volumen'), {'delta': -5})
        self.assertEqual(parse_volume('silencia'), {'mute': True})
        self.assertEqual(parse_volume('quita el silencio'), {'mute': False})
        self.assertEqual(parse_volume('a la mitad'), {'level': 50})

    def test_parse_input(self):
        self.assertEqual(parse_input('hdmi dos'), {'id': 'HDMI_2'})
        self.assertEqual(parse_input('pon la tele'), {'id': 'TV'})
        self.assertEqual(parse_input('entrada uno'), {'id': 'HDMI_1'})
        self.assertIsNone(parse_input('entrada 5'))

    def test_parse_scene(self):
        self.assertEqual(parse_scene('modo cine'), {'name': 'cine'})
        self.assertEqual(parse_scene('modo noche'), {'name': 'noche'})
        self.assertEqual(parse_scene('buenos dias'), {'name': 'manana'})

    def test_parse_audio(self):
        self.assertEqual(parse_audio('pon la barra'), {'output': 'external_optical'})
        self.assertEqual(parse_audio('usa el altavoz'), {'output': 'tv_speaker'})
        self.assertEqual(parse_audio('arregla el audio'), {'enforce': True})

    def test_parse_app(self):
        self.assertEqual(parse_app('abre netflix'), {'name': 'netflix'})
        self.assertIsNone(parse_app('abre'))

    def test_tv_error_kind(self):
        self.assertEqual(tv_control.TVError('auth').kind, 'auth')
        self.assertEqual(tv_control.TVError('param').kind, 'param')
        self.assertEqual(tv_control.TVError('tv').kind, 'tv')
        self.assertEqual(tv_control.TVError('network').kind, 'network')


if __name__ == '__main__':
    unittest.main()
