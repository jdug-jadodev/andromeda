"""Public cold initialization has its own bound; human-turn guards stay unchanged."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from conversation_voice import ConversationModel
from assistant_tools_model import AssistantToolsModel


class StartupPreparationTests(unittest.TestCase):
    def test_tools_public_cold_prepare_has_45s_without_history_or_action_execution(self):
        now=[100000]
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text('public-fixture-'*3)
            model=AssistantToolsModel(key,owned=Mock(),clock=lambda:now[0]); model.enable_alarm_lifecycle()
            model.memory.commit('private RAM history fixture','not startup input')
            def post(path,body,deadline):
                self.assertEqual(path,'/v1/chat/completions'); self.assertEqual(deadline,145000)
                self.assertEqual(body['max_tokens'],1); self.assertEqual(len(body['tools']),5); self.assertEqual(body['tool_choice'],'auto')
                self.assertEqual(body['messages'][1:], [{'role':'user','content':'Hola.'}])
                self.assertNotIn('private RAM',str(body)); now[0]+=43000
                self.assertLess(now[0],deadline)
                return {'choices':[{'message':{'content':'public output discarded'}}]}
            with patch.object(model,'post',side_effect=post) as request:
                self.assertIsNone(model.prepare()); request.assert_called_once()
            self.assertEqual(model.response_start_ms,60000); self.assertEqual(model.compute_ms,55000)
            self.assertEqual(model.turn_deadline(now[0],500000),198000)
            model.owned.stop.assert_not_called()

    def test_legacy_public_preparation_and_every_human_deadline_remain_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text('public-fixture-'*3)
            model=ConversationModel(key,owned=Mock(),clock=lambda:100000)
            with patch.object(model,'post') as post:
                model.prepare(); self.assertEqual(post.call_args.args[2],130000)
            self.assertEqual(model.response_start_ms,30000); self.assertEqual(model.compute_ms,22000)
            self.assertEqual(model.turn_deadline(100000,500000),122000)
            tools=AssistantToolsModel(key,owned=Mock(),clock=lambda:100000)
            self.assertEqual(tools.turn_deadline(100000,500000),155000)
            self.assertEqual(tools.turn_deadline(100000,120000),118500)
            with patch.object(tools,'post',side_effect=TimeoutError),self.assertRaises(TimeoutError): tools.prepare()
            self.assertIsNone(tools.thread)


if __name__=='__main__': unittest.main()
