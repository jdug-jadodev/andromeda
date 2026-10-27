import sys
from pathlib import Path
import unittest
from datetime import datetime, timezone, timedelta

ROOT = Path(__file__).resolve().parents[1]; sys.path.insert(0, str(ROOT / 'backend'))
import local_knowledge as knowledge

BOGOTA = timezone(timedelta(hours=-5))


class LocalKnowledgeTests(unittest.TestCase):
    def test_time_detection_and_natural_format(self):
        now = datetime(2026, 10, 25, 14, 30, 0, tzinfo=BOGOTA)
        for text in ('qué hora es', 'qué horas son', 'dame la hora', 'la hora', 'hora es'):
            self.assertEqual(knowledge.answer(text, now=now), 'Son las 2 y media de la tarde.', text)
        self.assertEqual(knowledge.answer('qué hora es', now=datetime(2026, 10, 25, 23, 0, 0, tzinfo=BOGOTA)),
            'Son las 11 de la noche.')
        self.assertEqual(knowledge.answer('qué hora es', now=datetime(2026, 10, 25, 9, 15, 0, tzinfo=BOGOTA)),
            'Son las 9 y cuarto de la mañana.')
        self.assertEqual(knowledge.answer('qué hora es', now=datetime(2026, 10, 25, 12, 0, 0, tzinfo=BOGOTA)),
            'Son las 12 de la tarde.')

    def test_date_detection_and_format(self):
        now = datetime(2026, 10, 25, 12, 0, 0, tzinfo=BOGOTA)  # Sunday
        expected = 'Hoy es domingo 25 de octubre de 2026.'
        for text in ('qué fecha es', 'qué día es hoy', 'la fecha', 'dame la fecha'):
            self.assertEqual(knowledge.answer(text, now=now), expected, text)

    def test_weather_detection_and_format(self):
        now = datetime(2026, 10, 25, 12, 0, 0, tzinfo=BOGOTA)
        for text in ('cómo está el clima', 'qué clima hace', 'clima', 'qué tiempo hace', 'hace calor'):
            self.assertEqual(knowledge.answer(text, now=now, fetcher=lambda: (18.0, 3)),
                'En Bogotá hace 18 grados, nublado.', text)
        self.assertEqual(knowledge.answer('qué clima hace', now=now, fetcher=lambda: (5.0, 0)),
            'En Bogotá hace 5 grados, despejado.')
        self.assertEqual(knowledge.answer('clima', now=now, fetcher=lambda: (None, None)),
            'No pude consultar el clima ahora. Inténtalo en un momento.')

    def test_non_knowledge_returns_none(self):
        now = datetime(2026, 10, 25, 12, 0, 0, tzinfo=BOGOTA)
        for text in ('enciende la estufa', 'cuéntame un chiste', 'qué es una nebulosa',
                     'no calcules 2+2', 'recuérdame beber agua', 'pon una alarma a las ocho',
                     'programa las luces a las cinco', 'lista mis recordatorios'):
            self.assertIsNone(knowledge.answer(text, now=now, fetcher=lambda: (18.0, 0)), text)


if __name__ == '__main__':
    unittest.main()
