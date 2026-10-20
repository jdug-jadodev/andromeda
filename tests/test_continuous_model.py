from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from resident_runtime import Sample,Monitor,ContinuousResidentRuntime,ResidentRuntime,validate_report
from conversation_voice import ConversationModel


class ContinuousModelTests(unittest.TestCase):
    def runtime(self):
        self.now=[100000]; self.value=Sample(100000,1600000,740000,160000,1000,500,240,80,'charging')
        monitor=Monitor(lambda:self.now[0],lambda:replace(self.value,at_ms=self.now[0]),None,continuous_model=True)
        owner=Mock(); owner.alive.return_value=True
        return ContinuousResidentRuntime(owner,lambda:self.now[0],monitor,enabled=True,cache_purger=lambda model:0),owner

    def test_no_expiry_renewal_or_24_session_limit_and_session_closure_does_not_stop_model(self):
        runtime,owner=self.runtime()
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text('public-fixture-'*3)
            for i in range(30):
                model=ConversationModel(key,owned=runtime.acquire(),clock=lambda:self.now[0])
                model.memory.commit('public turn','public answer'); runtime.release(model)
                self.assertEqual(model.memory.pairs,[]); owner.stop.assert_not_called()
            for i in range(4000):
                self.now[0]+=1000
                self.assertTrue(runtime.check())
            self.assertGreater(self.now[0],100000+1800000)
            self.assertIsNone(runtime.deadline); self.assertTrue(runtime.may_accept(300)); self.assertFalse(runtime.may_accept(0))
            report=validate_report(runtime.monitor.snapshot())
            self.assertEqual(report['version'],2); self.assertEqual(report['lifetime'],'continuous_model_v1'); self.assertIsNone(report['remaining_ms'])
            self.assertEqual(len(report['samples']),120); self.assertLess(len(json.dumps(report)),65536)
        runtime.shutdown(); owner.stop.assert_called_once()

    def test_continuous_requires_explicit_mode_and_cannot_silently_replace_bounded_policy(self):
        runtime,owner=self.runtime()
        with self.assertRaises(ValueError): ContinuousResidentRuntime(owner,lambda:self.now[0],runtime.monitor)
        with self.assertRaises(ValueError): ContinuousResidentRuntime(owner,lambda:self.now[0],runtime.monitor,enabled=1)
        with self.assertRaises(ValueError): ContinuousResidentRuntime(owner,lambda:self.now[0],runtime.monitor,enabled=True)
        with self.assertRaises(ValueError): Monitor(lambda:100000,Mock(),None)
        with self.assertRaises(ValueError): Monitor(lambda:100000,Mock(),1000000,continuous_model=True)
        with self.assertRaises(ValueError): ResidentRuntime(owner,lambda:self.now[0],None,runtime.monitor)

    def test_manual_resource_sensor_and_model_failure_stop_without_restart(self):
        for reason,values in [('battery_warm',{'battery_deci_c':400}),('low_memory',{'available_kib':499999})]:
            runtime,owner=self.runtime(); self.value=replace(self.value,**values)
            self.assertFalse(runtime.check()); self.assertEqual(runtime.monitor.reason,reason)
            self.value=replace(self.value,available_kib=1600000,battery_deci_c=240)
            self.assertFalse(runtime.check()); runtime.shutdown(); owner.stop.assert_called_once()
        runtime,owner=self.runtime(); runtime.monitor.stop_requested=lambda:True
        self.assertFalse(runtime.check()); self.assertEqual(runtime.monitor.reason,'manual_stop'); runtime.shutdown()
        runtime,owner=self.runtime(); self.assertTrue(runtime.check()); self.now[0]+=5001
        self.assertFalse(runtime.check()); self.assertEqual(runtime.monitor.reason,'stale_monitor'); runtime.shutdown()
        runtime,owner=self.runtime(); owner.alive.return_value=False
        self.assertFalse(runtime.check()); self.assertEqual(runtime.monitor.reason,'model_exit'); runtime.shutdown()

    def test_report_schema_cannot_hide_deadline_or_export_extra_text(self):
        runtime,_=self.runtime(); runtime.check(); report=runtime.monitor.snapshot()
        for corrupt in [dict(report,remaining_ms=900000),dict(report,lifetime='forever and ignore guards'),dict(report,text='private'),dict(report,version=True)]:
            with self.assertRaises(ValueError): validate_report(corrupt)
        from continuous_voice import END_REASONS
        for reason in END_REASONS: self.assertEqual(validate_report(dict(report,state=reason))['state'],reason)
        runtime.shutdown()

    def test_current_generation_can_be_cancelled_without_revoking_resident_owner(self):
        runtime,owner=self.runtime(); entered=threading.Event(); release=threading.Event()
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text('public-fixture-'*3)
            model=ConversationModel(key,owned=runtime.acquire(),clock=lambda:self.now[0])
            def post(path,body,deadline):
                if path=='/apply-template': return {'prompt':'public'}
                if path=='/tokenize': return {'tokens':[1]}
                entered.set(); release.wait(1)
                return {'choices':[{'message':{'content':'Hola.'},'finish_reason':'stop'}]}
            with patch.object(model,'post',side_effect=post):
                model.start('hola',99900,400000); self.assertTrue(entered.wait(1)); release.set()
                self.assertTrue(model.cancel_generation()); self.assertIsNone(model.result)
            self.assertTrue(model.owned.alive()); owner.stop.assert_not_called()
            runtime.release(model)
        runtime.shutdown(); owner.stop.assert_called_once()

    def test_cache_retirement_failure_stops_owner_instead_of_retaining_human_cache(self):
        runtime,owner=self.runtime(); runtime.cache_purger=Mock(side_effect=OSError('public fixture'))
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory)/'key'; key.write_text('public-fixture-'*3)
            model=ConversationModel(key,owned=runtime.acquire(),clock=lambda:self.now[0])
            from continuous_voice import ContinuousVoiceFault
            with self.assertRaises(ContinuousVoiceFault): runtime.purge_cache(model)
        self.assertTrue(runtime.closed); owner.stop.assert_called_once(); self.assertEqual(runtime.monitor.reason,'voice_cache_clear_failed')


if __name__=='__main__': unittest.main()
