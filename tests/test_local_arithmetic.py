import itertools
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT/'backend'))
from local_arithmetic import answer,expression,evaluate
from assistant_tools_model import AssistantToolsModel


class LocalArithmeticTests(unittest.TestCase):
    def test_current_human_math_symbols_words_precedence_and_decimal(self):
        cases={'cuánto es 2+2':'El resultado es 4.','cuánto es dos más dos':'El resultado es 4.',
            'cuánto es siete por ocho':'El resultado es 56.','cuánto es diez entre dos':'El resultado es 5.',
            'Andrómeda, por favor calcula (3+4)*2':'El resultado es 14.',
            'calcula 3+4*2':'El resultado es 11.','cuánto es menos dos más cinco':'El resultado es 3.',
            'calcula 0,1+0,2':'El resultado es 0,3.','cuánto es ciento veinte más dos':'El resultado es 122.',
            'cuánto es mil doscientos más treinta y uno':'El resultado es 1231.',
            'calcula 2 elevado a 3':'El resultado es 8.','calcula 10-10':'El resultado es 0.'}
        cases.update({'divide diez por dos':'El resultado es 5.','multiplica siete por ocho':'El resultado es 56.'})
        for text,expected in cases.items():
            with self.subTest(text=text): self.assertEqual(answer(text),expected)

    def test_generated_cases_are_computed_not_memorized_answers(self):
        for left,right in itertools.product(range(12),range(12)):
            self.assertEqual(answer(f'{left}+{right}'),f'El resultado es {left+right}.')
            self.assertEqual(answer(f'{left}*{right}'),f'El resultado es {left*right}.')
            self.assertEqual(answer(f'{left}-{right}'),f'El resultado es {left-right}.')

    def test_non_math_mixed_negated_quoted_or_code_never_become_math_or_tools(self):
        for text in ('no calcules 2+2','si digo 2+2 qué pasa','di «2+2»','2+2 y apaga el TV',
            'pon el volumen al 20','recuérdame 2+2','qué es una nebulosa','lista mis recordatorios',
            '__import__("os").system("x")','open(2)','x+2','[2+2]','2;2','sum(2,2)','2+2 mañana','2+2=4','２+２',
            'multiplica diez entre dos','divide diez más dos'):
            with self.subTest(text=text): self.assertIsNone(answer(text))
        for code in ('__import__("os")','[1][0]','x+2','True+2','(1).__class__'):
            with self.assertRaises(ValueError): evaluate(code)

    def test_zero_division_limits_and_approximation_are_explicit(self):
        self.assertEqual(answer('10/0'),'No se puede dividir entre cero.')
        self.assertIn('aproximado',answer('1/3'))
        for text in ('2^999999999','999999999999*999999999999','1+('*14+'1'+')'*14):
            self.assertIn('límites',answer(text))

    def test_fast_reply_never_queries_model_worker_or_changes_human_deadline(self):
        with tempfile.TemporaryDirectory() as folder:
            key=Path(folder)/'key'; key.write_text('x'*32)
            model=AssistantToolsModel(key,owned=Mock(),clock=lambda:100000)
            model.enable_basic_assistant(); model.post=Mock(side_effect=AssertionError('No inference allowed'))
            before=model.turn_deadline(99900,400000); deadline=model.start('cuánto es dos más dos',99900,400000)
            self.assertEqual(deadline,before); self.assertTrue(model.done.is_set())
            self.assertEqual(model.result,{'kind':'reply','text':'El resultado es 4.'})
            self.assertEqual(model.context_metrics['tools_model_choice'],'local'); model.post.assert_not_called()
            self.assertEqual(model.memory.pairs,[]); self.assertEqual(model.memory.last_human,99900)


if __name__=='__main__': unittest.main()
