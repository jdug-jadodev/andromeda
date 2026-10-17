import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from doja_intents import parse_command


class TimerGrammarTests(unittest.TestCase):
    def test_simple(self):
        self.assertEqual(parse_command("Doja, pon un temporizador de cinco minutos"), {"type": "timer", "seconds": 300})
        self.assertEqual(parse_command("temporizador de 10 segundos")["seconds"], 10)
        self.assertEqual(parse_command("Andrómeda, temporizador de diez segundos")["seconds"], 10)
        self.assertEqual(parse_command("andrómeda pone un temporizador de diez segundos")["seconds"], 10)

    def test_compound(self):
        self.assertEqual(parse_command("pon temporizador de una hora y treinta y cinco minutos")["seconds"], 5700)

    def test_limits(self):
        for text in ("temporizador de cero segundos", "temporizador de 25 horas", "temporizador de cinco dias"):
            self.assertEqual(parse_command(text)["type"], "say")

    def test_ambiguity_and_negation(self):
        for text in ("temporizador de cinco o diez minutos", "no pongas temporizador de diez minutos",
                     "temporizador de -5 segundos",
                     "temporizador de 1.5 minutos", "temporizador de dos minutos y medio",
                     "temporizador de cinco minutos y cinco minutos", "temporizador de cinco minutos y 10"):
            self.assertEqual(parse_command(text)["type"], "say", text)

    def test_mentions_are_not_orders(self):
        self.assertEqual(parse_command("ayer vi un temporizador de cinco minutos")["type"], "say")
        self.assertEqual(parse_command("ayer Andrómeda pone un temporizador de cinco minutos")["type"], "say")
        self.assertEqual(parse_command("pon un temporizador de cinco minutos para mañana")["type"], "say")

    def test_cancel(self):
        self.assertEqual(parse_command("cancela todos los temporizadores"), {"type": "cancel_timer", "all": True})
        self.assertEqual(parse_command("cancela el temporizador"), {"type": "cancel_timer", "all": False})
        self.assertEqual(parse_command("Andrómeda, cancela el temporizador por favor")["type"], "cancel_timer")
        for verb in ("cancele", "borra", "elimina", "borre", "elimine"):
            intent = parse_command(f"Andrómeda, {verb} el temporizador")
            self.assertEqual(intent["type"], "cancel_timer")
            self.assertFalse(intent.get("needs_duration", False))

    def test_only_incomplete_creation_waits_for_duration(self):
        self.assertTrue(parse_command("pon un temporizador de cinco").get("needs_duration"))
        for text in ("borra el temporizador", "lista los temporizadores", "no", "cancele el temporizador"):
            self.assertFalse(parse_command(text).get("needs_duration", False), text)

    def test_cancel_does_not_guess_or_execute_quotes(self):
        for text in ("ayer dije cancela el temporizador", "por ejemplo cancela todos los temporizadores",
                     "cancela el temporizador mañana", "cancela el primer temporizador",
                     "cancela el temporizador de cinco minutos", "cancela dos temporizadores"):
            self.assertEqual(parse_command(text)["type"], "say", text)

    def test_unsupported_question_never_creates_timer(self):
        self.assertEqual(parse_command("por que el cielo es azul")["type"], "say")
        self.assertEqual(parse_command("cuanto falta")["type"], "list_timers")


if __name__ == "__main__":
    unittest.main()
