from dataclasses import replace
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import copy
import unittest
from unittest.mock import Mock,patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'))
from resident_runtime import Sample,ProcSampler,Monitor,ResidentRuntime,validate_report,write_report,read_report,request_stop,cleanup_credentials
from conversation_voice import ConversationModel
import conversation_voice as voice


class ResidentRuntimeTests(unittest.TestCase):
    def sample(self,at=100000,**values):
        return replace(Sample(at,1600000,740000,160000,1000,500,220,80,'charging'),**values)

    def monitor(self):
        self.clock=[100000]; self.latest=self.sample()
        return Monitor(lambda:self.clock[0],lambda:replace(self.latest,at_ms=self.clock[0]),1000000)

    def runtime(self):
        monitor=self.monitor(); owner=Mock(); owner.alive.return_value=True
        return ResidentRuntime(owner,lambda:self.clock[0],1000000,monitor),owner

    def test_two_sessions_revoke_only_their_leases_keep_owner_and_drop_all_context(self):
        runtime,owner=self.runtime()
        with tempfile.TemporaryDirectory() as folder:
            key=Path(folder)/'key'; key.write_text('public-test-key-'*3)
            for i in range(2):
                model=ConversationModel(key,owned=runtime.acquire(),clock=lambda:self.clock[0])
                self.assertEqual(model.memory.pairs,[])
                model.memory.commit('public marker','public answer'); model.result='public result'
                model.context_metrics={'input_tokens':1}; model.cancel()
                self.assertFalse(model.owned.alive()); owner.stop.assert_not_called()
                runtime.release(model)
                self.assertEqual(model.memory.pairs,[]); self.assertIsNone(model.result); self.assertEqual(model.context_metrics,{})
                self.assertTrue(runtime.alive())
        runtime.shutdown(); runtime.shutdown(); owner.stop.assert_called_once()

    def test_single_lease_foreign_lease_revocation_and_maximum_session_count(self):
        runtime,owner=self.runtime(); lease=runtime.acquire()
        with self.assertRaises(ValueError): runtime.acquire()
        with self.assertRaises(ValueError): runtime.release(Mock(owned=object()))
        runtime.shutdown(); self.assertFalse(lease.alive()); owner.stop.assert_called_once()
        with self.assertRaises(ValueError): runtime.acquire()
        runtime,_=self.runtime(); runtime.sessions=24
        with self.assertRaises(ValueError): runtime.acquire()

    def test_incomplete_generation_cancellation_stops_verified_owner_not_reused(self):
        runtime,owner=self.runtime(); lease=runtime.acquire()
        model=Mock(owned=lease); model.thread.is_alive.return_value=True
        with self.assertRaises(RuntimeError): runtime.release(model)
        owner.stop.assert_called_once(); self.assertTrue(runtime.closed); self.assertEqual(runtime.monitor.reason,'cancellation_stuck')
        with self.assertRaises(ValueError): runtime.acquire()

    def test_cancelling_inflight_http_does_not_kill_shared_owner_or_reuse_racing_text(self):
        runtime,owner=self.runtime(); entered=threading.Event(); released=threading.Event()
        with tempfile.TemporaryDirectory() as folder:
            key=Path(folder)/'key'; key.write_text('public-test-key-'*3)
            model=ConversationModel(key,owned=runtime.acquire(),clock=lambda:self.clock[0])
            def post(path,body,deadline):
                if path=='/apply-template': return {'prompt':'public fixture'}
                if path=='/tokenize': return {'tokens':[1]}
                entered.set(); released.wait(1)
                return {'choices':[{'message':{'content':'Respuesta pública.'},'finish_reason':'stop'}]}
            with patch.object(model,'post',side_effect=post):
                model.start('hola',99900,400000); self.assertTrue(entered.wait(1))
                released.set(); runtime.release(model)
            owner.stop.assert_not_called(); self.assertIsNone(model.result); self.assertFalse(model.thread.is_alive())
        runtime.shutdown()

    def test_monitor_numeric_cpu_samples_bounded_ring_and_report_privacy(self):
        monitor=self.monitor()
        for i in range(1500):
            self.clock[0]=100000+i*1000
            self.latest=self.sample(model_cpu_ms=1000+i*50,backend_cpu_ms=500+i*10)
            monitor.deadline=2000000
            self.assertTrue(monitor.check())
        result=validate_report(monitor.snapshot())
        self.assertEqual(len(result['samples']),120); self.assertEqual(result['minimum_available_kib'],1600000)
        self.assertEqual(result['samples'][-1]['model_cpu_percent_one_core'],5.0)
        self.assertEqual(result['samples'][-1]['backend_cpu_percent_one_core'],1.0)
        self.assertLess(len(json.dumps(result).encode()),65536)
        self.assertFalse(result['human_content_recorded']); self.assertNotIn('key',result)

    def test_monitor_stop_temperature_ram_stale_clock_sensor_failure_and_no_restart(self):
        for values,reason in [({'available_kib':499999},'low_memory'),({'battery_deci_c':400},'battery_warm')]:
            monitor=self.monitor(); self.latest=self.sample(**values)
            self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,reason)
            self.latest=self.sample(); self.assertFalse(monitor.check())
        monitor=self.monitor(); self.assertTrue(monitor.check()); self.clock[0]+=5001
        self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,'stale_monitor')
        monitor=self.monitor(); monitor.sampler=Mock(side_effect=OSError('public synthetic'))
        self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,'sensor_or_report_error')
        monitor=self.monitor(); self.assertTrue(monitor.check()); self.clock[0]-=100
        self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,'sensor_or_report_error')
        monitor=self.monitor(); monitor.stop_requested=lambda:True
        self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,'manual_stop')
        monitor=self.monitor(); self.clock[0]=monitor.deadline
        self.assertFalse(monitor.check()); self.assertEqual(monitor.reason,'time_limit')

    def test_model_exit_is_fail_closed_and_full_session_must_fit_remaining_lifetime(self):
        runtime,owner=self.runtime()
        self.assertTrue(runtime.may_accept(300)); self.assertFalse(runtime.may_accept(301))
        self.clock[0]=runtime.deadline-304999; self.assertFalse(runtime.may_accept(300))
        owner.alive.return_value=False; self.assertFalse(runtime.check()); self.assertEqual(runtime.monitor.reason,'model_exit')
        with self.assertRaises(ValueError): ResidentRuntime(owner,lambda:100000,1900001,runtime.monitor)

    def test_report_rejects_text_identifiers_extra_fields_invalid_types_and_nonfinite_values(self):
        monitor=self.monitor(); monitor.check(); valid=monitor.snapshot()
        for key,val in [('text','DO_NOT_PERSIST'),('session_id','DO_NOT_PERSIST'),('version',True),('state',[]),('state','private words'),('human_content_recorded',True)]:
            with self.assertRaises(ValueError): validate_report(dict(valid,**{key:val}))
        for key,val in [('battery_percent',101),('available_kib',True),('charge_state','private words'),('charge_state',[])]:
            with self.assertRaises(ValueError): replace(self.sample(),**{key:val}).validate()
        for val in (float('nan'),float('inf'),True,'private words'):
            corrupt=json.loads(json.dumps(valid)); corrupt['samples'][0]['model_cpu_percent_one_core']=val
            with self.assertRaises(ValueError): validate_report(corrupt)

    def test_proc_sampler_uses_only_numeric_files_and_cpu_ticks(self):
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder); proc=base/'proc'; battery=base/'battery'; proc.mkdir(); battery.mkdir()
            (proc/'meminfo').write_text('MemAvailable: 1600000 kB\n')
            for pid in (123,456):
                path=proc/str(pid); path.mkdir(); fields=['0']*30; fields[0]='S'; fields[11]='200'; fields[12]='100'
                (path/'stat').write_text(str(pid)+' (public process name) '+' '.join(fields))
                (path/'status').write_text('VmRSS: 740000 kB\n')
            for name,value in [('temp','220'),('capacity','80'),('status','Not charging')]: (battery/name).write_text(value)
            with patch('resident_runtime.os.sysconf',return_value=100,create=True),patch('resident_runtime.os.getpid',return_value=456):
                sample=ProcSampler(123,lambda:100000,proc,battery)()
            self.assertEqual(sample.model_cpu_ms,3000); self.assertEqual(sample.backend_cpu_ms,3000)
            self.assertEqual(sample.charge_state,'not_charging'); self.assertEqual(sample.model_rss_kib,740000)

    def test_production_serve_accepts_two_sessions_fresh_context_without_killing_resident(self):
        runtime,owner=self.runtime(); stop=threading.Event(); runtime.monitor.stop_requested=stop.is_set
        server=Mock(); server.__enter__=Mock(return_value=server); server.__exit__=Mock(return_value=False)
        context=Mock(); raw=Mock(); raw.__enter__=Mock(return_value=raw); raw.__exit__=Mock(return_value=False)
        conn=Mock(); conn.__enter__=Mock(return_value=conn); conn.__exit__=Mock(return_value=False)
        context.wrap_socket.return_value=conn
        server.accept.side_effect=[(raw,('127.0.0.1',1)),(raw,('127.0.0.1',2))]
        hello=dict(token='public-key-'*4,protocol=10,mode='assistant_tools',source='synthetic_pcm',
            web_response_mode='worker_first_v3_spanish',phone_volume_policy='absolute_media_v1',
            context=dict(version=1,id='a'*32,last_human_ms=0,turns=[]))
        models=[]
        def session(conn,detector,model,*args):
            self.assertEqual(model.memory.pairs,[])
            model.memory.commit('public fixture','public answer'); models.append(model)
            model.cancel(); self.assertTrue(owner.alive())
            if len(models)==2: stop.set()
        with tempfile.TemporaryDirectory() as folder:
            key=Path(folder)/'key'; key.write_text(hello['token'])
            with patch.object(voice.ssl,'SSLContext',return_value=context),patch.object(voice.socket,'socket',return_value=server), \
                    patch.object(voice,'read_hello',side_effect=[copy.deepcopy(hello),copy.deepcopy(hello)]), \
                    patch.object(voice.time,'CLOCK_BOOTTIME',7,create=True), \
                    patch.object(voice.time,'clock_gettime',side_effect=lambda _:self.clock[0]/1000,create=True), \
                    patch.object(voice,'now_ms',side_effect=lambda:self.clock[0]),patch.object(voice,'session',side_effect=session),patch.object(voice,'print'):
                voice.serve(key,key,Mock(),300,'public-cert','public-key','synthetic_pcm',runtime.check,assistant_tools=True,resident=runtime)
        self.assertEqual(len(models),2); self.assertIsNot(models[0],models[1]); self.assertEqual(models[0].memory.pairs,[])
        self.assertEqual(models[1].memory.pairs,[]); owner.stop.assert_not_called(); runtime.shutdown(); owner.stop.assert_called_once()

    def test_short_resident_window_completes_tls_then_rejects_only_authenticated_client_without_context_or_session(self):
        for authenticated in (True,False):
            with self.subTest(authenticated=authenticated):
                runtime,owner=self.runtime(); self.clock[0]=runtime.deadline-200000
                stopping=threading.Event(); runtime.monitor.stop_requested=stopping.is_set
                server=Mock(); server.__enter__=Mock(return_value=server); server.__exit__=Mock(return_value=False)
                raw=Mock(); raw.__enter__=Mock(return_value=raw); raw.__exit__=Mock(return_value=False)
                conn=Mock(); conn.__enter__=Mock(return_value=conn); conn.__exit__=Mock(return_value=False)
                context=Mock(); context.wrap_socket.return_value=conn
                def accept():
                    if stopping.is_set(): raise AssertionError('Unexpected accept after stop')
                    return raw,('127.0.0.1',1)
                server.accept.side_effect=accept
                token='public-key-'*4
                hello=dict(token=token if authenticated else 'wrong-public-key',protocol=10,mode='assistant_tools',source='synthetic_pcm',
                    web_response_mode='worker_first_v3_spanish',phone_volume_policy='absolute_media_v1',
                    context='invalid context must not be read')
                order=[]
                def wrap(*args,**kwargs): order.append('tls'); return conn
                def read(*args,**kwargs): order.append('authentication'); stopping.set(); return hello
                def send(*args,**kwargs): order.append('rejection')
                context.wrap_socket.side_effect=wrap
                deadline=runtime.deadline
                with tempfile.TemporaryDirectory() as folder:
                    key=Path(folder)/'key'; key.write_text(token)
                    with patch.object(voice.ssl,'SSLContext',return_value=context),patch.object(voice.socket,'socket',return_value=server), \
                            patch.object(voice,'read_hello',side_effect=read),patch.object(voice,'send_event',side_effect=send) as sent, \
                            patch.object(voice.ShortMemory,'restore') as restore,patch.object(voice,'session') as session, \
                            patch.object(voice.time,'CLOCK_BOOTTIME',7,create=True), \
                            patch.object(voice.time,'clock_gettime',side_effect=lambda _:self.clock[0]/1000,create=True), \
                            patch.object(voice,'now_ms',side_effect=lambda:self.clock[0]),patch.object(voice,'print'):
                        with self.assertRaisesRegex(RuntimeError,'readiness guard'):
                            voice.serve(key,key,Mock(),300,'public-cert','public-key','synthetic_pcm',runtime.check,assistant_tools=True,resident=runtime)
                self.assertEqual(order,['tls','authentication','rejection'] if authenticated else ['tls','authentication'])
                if authenticated: sent.assert_called_once_with(conn,{'type':'error','reason':'resident_window_ending'})
                else: sent.assert_not_called()
                restore.assert_not_called(); session.assert_not_called(); owner.stop.assert_not_called()
                self.assertEqual(runtime.deadline,deadline); self.assertIsNone(runtime.active); runtime.shutdown()

    @unittest.skipUnless(os.name=='posix','Private directory FD/mode checks need POSIX')
    def test_private_atomic_report_stop_and_cleanup_never_follow_symlinks_or_unknown_work(self):
        with tempfile.TemporaryDirectory() as folder:
            work=Path(folder); work.chmod(0o700); path=work/'metrics.json'
            monitor=self.monitor(); monitor.check(); write_report(path,monitor.snapshot())
            self.assertEqual(read_report(path),monitor.snapshot()); self.assertEqual(path.stat().st_mode&0o777,0o600)
            secret=work/'private-test'; secret.write_text('public sentinel'); secret.chmod(0o600)
            (work/'STOP').symlink_to(secret)
            with self.assertRaises(ValueError): request_stop(work)
            self.assertEqual(secret.read_text(),'public sentinel'); (work/'STOP').unlink()
            request_stop(work); request_stop(work)
            for name in ('key','server.pid','deadline_ms'):
                (work/name).write_text('public'); (work/name).chmod(0o600)
            with self.assertRaises(ValueError): cleanup_credentials(work,work.stat().st_ino+1)
            cleanup_credentials(work,work.stat().st_ino)
            self.assertTrue(path.exists()); self.assertFalse((work/'key').exists()); self.assertFalse((work/'STOP').exists())
            path.unlink(); path.symlink_to(secret)
            with self.assertRaises((OSError,ValueError)): read_report(path)
            with self.assertRaises(ValueError): write_report(path,monitor.snapshot())
            self.assertEqual(secret.read_text(),'public sentinel')


if __name__=='__main__': unittest.main()
