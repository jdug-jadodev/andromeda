from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'backend'))
from durations import duration_seconds


class DurationTests(unittest.TestCase):
    def test_exact_units_and_fractions(self):
        for quantity, unit, expected in [(3, 'minutes', 180), (90, 'seconds', 90), (1, 'hours', 3600),
                                         (1.5, 'hours', 5400), (2.5, 'minutes', 150), (.1, 'minutes', 6),
                                         (24, 'hours', 86400)]:
            self.assertEqual(duration_seconds(quantity, unit), expected)

    def test_invalid_quantities_rejected(self):
        for value in [True, False, '2', None, [], -1, 0, float('inf'), float('nan'), .001, 86401, 10 ** 1000]:
            with self.subTest(value=value), self.assertRaises(ValueError): duration_seconds(value, 'seconds')

    def test_unknown_units_and_overflow(self):
        for quantity, unit in [(25, 'hours'), (1, 'days'), (1, None), (1, []), (1e100, 'hours')]:
            with self.subTest(unit=unit), self.assertRaises(ValueError): duration_seconds(quantity, unit)


if __name__ == '__main__': unittest.main()
