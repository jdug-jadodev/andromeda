import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from inference_privacy import purge_engine_cache
from continuous_voice import ContinuousVoiceFault


class InferencePrivacyTests(unittest.TestCase):
    def connection(self,value,status=200):
        connection=Mock(); response=connection.getresponse.return_value
        response.status=status; response.read.return_value=json.dumps(value).encode()
        return connection

    def test_only_public_capped_replacement_then_exact_slot_erase_without_save_or_human_data(self):
        first=self.connection({'content':'','tokens_predicted':0,'id_slot':0}); second=self.connection({'id_slot':0,'n_erased':3})
        with patch('inference_privacy.http.client.HTTPConnection',side_effect=[first,second]) as factory:
            self.assertEqual(purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True),3)
        self.assertEqual(factory.call_count,2)
        for call in factory.call_args_list: self.assertEqual(call.args,('127.0.0.1',11435))
        a=first.request.call_args; b=second.request.call_args
        self.assertEqual(a.args[:2],('POST','/completion')); self.assertEqual(b.args[:2],('POST','/slots/0?action=erase'))
        body=json.loads(a.args[2]); self.assertEqual(body['prompt'],'Hola.'); self.assertEqual(body['n_predict'],1); self.assertIs(body['cache_prompt'],False)
        self.assertEqual(json.loads(b.args[2]),{}); self.assertNotIn('filename',body)
        first.close.assert_called_once(); second.close.assert_called_once()

    def test_rejected_unexpected_generated_output_wrong_slot_or_bad_erasure_never_retries(self):
        for output in [{'content':'generated','tokens_predicted':2,'id_slot':0},{'content':'','tokens_predicted':False,'id_slot':0},
                {'content':'','tokens_predicted':0,'id_slot':1},{}]:
            connection=self.connection(output)
            with patch('inference_privacy.http.client.HTTPConnection',return_value=connection) as factory,self.assertRaises(ContinuousVoiceFault):
                purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True)
            self.assertEqual(factory.call_count,1); connection.close.assert_called_once()
        for output in [{'id_slot':0,'n_erased':True},{'id_slot':1,'n_erased':5},{'id_slot':0,'n_erased':2049},{'id_slot':0,'n_erased':1,'content':'private'}]:
            first=self.connection({'content':'','tokens_predicted':0,'id_slot':0}); second=self.connection(output)
            with patch('inference_privacy.http.client.HTTPConnection',side_effect=[first,second]) as factory,self.assertRaises(ContinuousVoiceFault):
                purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True)
            self.assertEqual(factory.call_count,2)

    def test_b11669_public_one_token_is_discarded_not_interpreted_or_returned(self):
        first=self.connection({'content':'public maintenance output','tokens_predicted':1,'id_slot':0})
        second=self.connection({'id_slot':0,'n_erased':5})
        with patch('inference_privacy.http.client.HTTPConnection',side_effect=[first,second]):
            self.assertEqual(purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True),5)
        for output in [{'content':'x'*257,'tokens_predicted':1,'id_slot':0},{'content':'unexpected','tokens_predicted':0,'id_slot':0}]:
            with patch('inference_privacy.http.client.HTTPConnection',return_value=self.connection(output)),self.assertRaises(ContinuousVoiceFault) as failure:
                purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True)
            self.assertEqual(failure.exception.detail,'public_receipt')

    def test_missing_owner_timeout_bad_status_and_oversized_data_fail_closed(self):
        with patch('inference_privacy.http.client.HTTPConnection') as factory,self.assertRaises(ContinuousVoiceFault):
            purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:False)
        factory.assert_not_called()
        for status,data in [(503,b'{}'),(200,b'x'*8193)]:
            connection=self.connection({},status); connection.getresponse.return_value.read.return_value=data
            with patch('inference_privacy.http.client.HTTPConnection',return_value=connection),self.assertRaises(ContinuousVoiceFault):
                purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True)
        with patch('inference_privacy.http.client.HTTPConnection',side_effect=TimeoutError()),self.assertRaises(ContinuousVoiceFault):
            purge_engine_cache('public-fixture-'*3,lambda:100000,lambda:True)


if __name__=='__main__': unittest.main()
