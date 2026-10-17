import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from span_duration import seconds_from_span


class SpanDurationTests(unittest.TestCase):
    def test_exact_spans_and_compound_duration(self):
        for span, text, seconds in [('cinco minutos', 'Avísame en cinco minutos.', 300),
                                    ('dos horas', 'Quiero esperar dos horas', 7200),
                                    ('setenta segundos', 'Cuenta setenta segundos', 70),
                                    ('una hora y cinco minutos', 'Pon una hora y cinco minutos', 3900)]:
            self.assertEqual(seconds_from_span(span, text), seconds)

    def test_invented_or_converted_text_is_not_a_quote(self):
        for span, source in [('180 minutos', 'Avísame en tres minutos'), ('120 segundos', 'Pon dos minutos'),
                             ('30 segundos', 'Pon un temporizador'), ('dos minutos', 'Pon veintidos minutos')]:
            with self.assertRaises(ValueError): seconds_from_span(span, source)

    def test_partial_multiple_and_ambiguous_durations_rejected(self):
        for span, source in [('dos minutos', 'Pon dos minutos y treinta segundos'),
                             ('tres minutos', 'Primero dije dos minutos, ahora tres minutos'),
                             ('dos minutos', 'dos minutos y dos minutos'), ('un rato', 'Espera un rato')]:
            with self.assertRaises(ValueError): seconds_from_span(span, source)

    def test_out_of_range_and_unsupported_arithmetic_rejected(self):
        for span in ['0 segundos', '25 horas', '1.5 segundos', '-5 segundos', 'dos minutos y medio']:
            with self.assertRaises(ValueError): seconds_from_span(span, 'Pon ' + span)


if __name__ == '__main__': unittest.main()
