import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))

from affirmation import is_negative, is_affirmative


class AffirmationTests(unittest.TestCase):
    def test_negative_variants(self):
        for phrase in ('no', 'no no', 'no nada', 'no nada mas', 'no gracias',
                       'no, nada más', 'no, gracias', 'ya esta', 'eso es todo',
                       'no necesito nada mas', 'asi esta bien', 'nada mas'):
            self.assertTrue(is_negative(phrase), phrase)

    def test_redirection_is_not_negative(self):
        for phrase in ('no quiero hablar de otra cosa', 'no, dime un chiste',
                       'no, pon una alarma', 'no, busca el clima', 'no me gusta',
                       'no, sube el volumen'):
            self.assertFalse(is_negative(phrase), phrase)

    def test_affirmative_variants(self):
        for phrase in ('si', 'si si', 'dale', 'de una', 'listo', 'claro', 'vale',
                       'si por favor', 'confirmo', 'de acuerdo', 'ok', 'perfecto'):
            self.assertTrue(is_affirmative(phrase), phrase)


if __name__ == '__main__':
    unittest.main()
