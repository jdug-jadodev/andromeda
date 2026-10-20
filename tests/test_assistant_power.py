from datetime import datetime
from pathlib import Path
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from assistant_power import PowerPolicy, Resources, Settings, power_phrase


class PowerPolicyTests(unittest.TestCase):
    def at(self, text): return datetime.fromisoformat(text)
    def now(self, text): return self.at('2026-10-20T'+text+':00-05:00')
    def policy(self): return PowerPolicy(Settings(enabled=True,activation_listener=True))
    def safe(self): return Resources(1500000,250)

    def test_disabled_by_default_never_auto_listens_or_loads(self):
        for event in ('tick','resume','rearm_safety'):
            decision=PowerPolicy().decide(self.now('10:00'),self.safe(),event)
            self.assertEqual(decision.state,'DISABLED'); self.assertFalse(decision.model_desired)
            self.assertFalse(decision.activation_listener_desired); self.assertTrue(decision.plan_only)

    def test_night_crosses_midnight_exact_boundaries_and_bogota(self):
        policy=self.policy()
        for time,state in [('22:59','RESIDENT'),('23:00','NIGHT_SLEEP'),('00:00','NIGHT_SLEEP'),('05:59','NIGHT_SLEEP'),('06:00','RESIDENT')]:
            self.assertEqual(policy.decide(self.now(time),self.safe()).state,state)
        self.assertEqual(policy.decide(self.at('2026-10-20T04:00:00+00:00'),self.safe()).state,'NIGHT_SLEEP')
        with self.assertRaises(ValueError): policy.decide(datetime(2026,10,20),self.safe())

    def test_trip_hold_survives_morning_and_restoration_until_explicit_wake(self):
        policy=self.policy(); self.assertEqual(policy.decide(self.now('12:00'),self.safe(),'suspend').state,'MANUAL_SUSPEND')
        for date in ('2026-10-21T06:00:00-05:00','2026-12-01T12:00:00-05:00'):
            self.assertEqual(policy.decide(self.at(date),self.safe()).state,'MANUAL_SUSPEND')
        restored=PowerPolicy(policy.settings,manual_hold=policy.manual_hold)
        self.assertEqual(restored.decide(self.now('10:00'),self.safe()).state,'MANUAL_SUSPEND')
        self.assertEqual(restored.decide(self.now('10:00'),self.safe(),'resume').state,'RESIDENT')

    def test_night_wake_override_expires_not_a_permanent_schedule_bypass(self):
        policy=self.policy(); self.assertEqual(policy.decide(self.now('01:00'),self.safe(),'resume').state,'RESIDENT')
        self.assertEqual(policy.decide(self.now('05:59'),self.safe()).state,'RESIDENT')
        self.assertEqual(policy.decide(self.now('06:00'),self.safe()).state,'RESIDENT')
        self.assertEqual(policy.decide(self.now('23:00'),self.safe()).state,'NIGHT_SLEEP')

    def test_clock_rollback_cannot_extend_a_night_wake_override(self):
        policy=self.policy(); policy.decide(self.now('02:00'),self.safe(),'resume')
        self.assertEqual(policy.decide(self.now('01:00'),self.safe()).state,'NIGHT_SLEEP')

    def test_resources_stop_and_latch_no_auto_restart_or_voice_override(self):
        for sample in (None,Resources(499999,250),Resources(1500000,400),Resources(1500000,999),Resources(True,250)):
            policy=self.policy()
            self.assertEqual(policy.decide(self.now('10:00'),sample,model_running=True).state,'SAFETY_HOLD')
            self.assertEqual(policy.decide(self.now('10:01'),self.safe(),'resume').state,'SAFETY_HOLD')
            self.assertEqual(policy.decide(self.now('10:02'),self.safe(),'rearm_safety').state,'RESIDENT')

    def test_start_and_remain_guards_differ_and_safety_rearm_does_not_cancel_trip(self):
        policy=self.policy(); sample=Resources(900000,390)
        self.assertEqual(policy.decide(self.now('10:00'),sample,model_running=True).state,'RESIDENT')
        self.assertEqual(policy.decide(self.now('10:00'),sample,model_running=False).state,'SAFETY_HOLD')
        policy.decide(self.now('10:00'),self.safe(),'suspend')
        self.assertEqual(policy.decide(self.now('10:00'),self.safe(),'rearm_safety').state,'MANUAL_SUSPEND')

    def test_exact_fresh_phrase_vocabulary_only_no_quoted_web_or_chained_commands(self):
        for human,event in [('Andrómeda, suspensión','suspend'),('andromeda suspención','suspend'),
                ('Andrómeda, buenos días','resume'),('Andrómeda actívate','resume')]: self.assertEqual(power_phrase(human),event)
        for human in ('buenos días','actívate','no Andrómeda suspensión','ayer dije Andrómeda suspensión',
                'Andrómeda, suspensión y apaga el TV','Andrómeda, di «actívate»','Andrómeda suspensión\x00',None):
            self.assertIsNone(power_phrase(human))

    def test_plan_cli_has_no_process_or_microphone_side_effects(self):
        import json
        command=[sys.executable,str(ROOT/'scripts/andromeda-power-plan.py'),'--enabled','--listener','--at','2026-10-20T23:00:00-05:00',
            '--available-kib','1500000','--battery-deci-c','250']
        run=subprocess.run(command,capture_output=True,text=True,timeout=10)
        self.assertEqual(run.returncode,0,run.stderr); result=json.loads(run.stdout)
        self.assertEqual(result['state'],'NIGHT_SLEEP'); self.assertTrue(result['plan_only'])
        self.assertFalse(result['executed']); self.assertFalse(result['microphone_opened']); self.assertFalse(result['schedule_applied'])


if __name__=='__main__': unittest.main()
