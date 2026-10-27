"""Opt-in bounded model ownership and numeric monitoring. No startup or scheduler.

A session can revoke its lease, never kill the supervisor's verified model.
The supervisor alone stops that model, including any stuck cancellation.
"""
from collections import deque
from dataclasses import asdict, dataclass
import json
import os
from pathlib import Path
import stat
import threading


@dataclass(frozen=True)
class Sample:
    at_ms: int
    available_kib: int
    model_rss_kib: int
    backend_rss_kib: int
    model_cpu_ms: int
    backend_cpu_ms: int
    battery_deci_c: int
    battery_percent: int
    charge_state: str

    def validate(self):
        integers=(self.at_ms,self.available_kib,self.model_rss_kib,self.backend_rss_kib,self.model_cpu_ms,self.backend_cpu_ms,self.battery_deci_c,self.battery_percent)
        if (any(type(v) is not int for v in integers) or any(v<0 or v>10**12 for v in integers[:-2])
                or not 0<=self.battery_deci_c<=800 or not 0<=self.battery_percent<=100
                or type(self.charge_state) is not str or self.charge_state not in {'charging','discharging','full','not_charging','unknown'}):
            raise ValueError('Invalid numeric sample')


class ProcSampler:
    def __init__(self, model_pid, clock, proc=Path('/proc'), battery=Path('/sys/class/power_supply/battery')):
        self.model_pid,self.clock,self.proc,self.battery=model_pid,clock,proc,battery
        self.ticks=os.sysconf('SC_CLK_TCK')

    def process(self,pid):
        path=self.proc/str(pid)
        fields=(path/'stat').read_text().rsplit(') ',1)[1].split()
        rss=next(int(line.split()[1]) for line in (path/'status').read_text().splitlines() if line.startswith('VmRSS:'))
        return rss,(int(fields[11])+int(fields[12]))*1000//self.ticks

    def __call__(self):
        memory=next(int(line.split()[1]) for line in (self.proc/'meminfo').read_text().splitlines() if line.startswith('MemAvailable:'))
        model_rss,model_cpu=self.process(self.model_pid); backend_rss,backend_cpu=self.process(os.getpid())
        status=(self.battery/'status').read_text().strip().lower().replace(' ','_')
        if status not in {'charging','discharging','full','not_charging'}: status='unknown'
        value=Sample(self.clock(),memory,model_rss,backend_rss,model_cpu,backend_cpu,
            int((self.battery/'temp').read_text()),int((self.battery/'capacity').read_text()),status)
        value.validate(); return value


class Monitor:
    """Bounded RAM samples and fixed-schema reports; no human text or content hashes."""
    def __init__(self,clock,sampler,deadline,report=None,stop_requested=lambda:False,continuous_model=False):
        if type(continuous_model) is not bool or (deadline is None) != continuous_model or deadline is not None and type(deadline) is not int:
            raise ValueError('Explicit bounded or continuous model lifetime required')
        self.clock,self.sampler,self.deadline=clock,sampler,deadline
        self.continuous_model=continuous_model
        self.report,self.stop_requested=report,stop_requested
        self.samples=deque(maxlen=120)
        self.previous=None; self.last_report=0; self.reason='running'; self.count=0
        self.minimum_available=None; self.peak_model_rss=self.peak_backend_rss=self.max_temp=0

    def check(self):
        if self.reason!='running': return False
        now=self.clock()
        if self.stop_requested(): self.reason='manual_stop'
        elif self.deadline is not None and now>=self.deadline: self.reason='time_limit'
        elif self.previous is not None and now-self.previous.at_ms>5000: self.reason='stale_monitor'
        if self.reason!='running': return False
        if self.previous is not None and 0<=now-self.previous.at_ms<1000: return True
        try:
            sample=self.sampler(); sample.validate()
            if abs(now-sample.at_ms)>1000 or self.previous is not None and sample.at_ms<=self.previous.at_ms:
                raise ValueError('Stale sample')
            if sample.available_kib<500000: self.reason='low_memory'
            elif sample.battery_deci_c>=400: self.reason='battery_warm'
            delta=sample.at_ms-self.previous.at_ms if self.previous else 0
            cpu=0 if not delta else round((sample.model_cpu_ms-self.previous.model_cpu_ms)*100/delta,2)
            backend_cpu=0 if not delta else round((sample.backend_cpu_ms-self.previous.backend_cpu_ms)*100/delta,2)
            if cpu<0 or backend_cpu<0 or cpu>100000 or backend_cpu>100000: raise ValueError('Invalid CPU delta')
            self.count+=1; self.minimum_available=sample.available_kib if self.minimum_available is None else min(self.minimum_available,sample.available_kib)
            self.peak_model_rss=max(self.peak_model_rss,sample.model_rss_kib); self.peak_backend_rss=max(self.peak_backend_rss,sample.backend_rss_kib)
            self.max_temp=max(self.max_temp,sample.battery_deci_c)
            # Keep only a 10-second series, not every guard call.
            if not self.samples or sample.at_ms-self.samples[-1]['at_ms']>=10000:
                self.samples.append(dict(asdict(sample),model_cpu_percent_one_core=cpu,backend_cpu_percent_one_core=backend_cpu))
            self.previous=sample
            if self.report is not None and (not self.last_report or now-self.last_report>=10000):
                self.report(self.snapshot()); self.last_report=now
        except (OSError,ValueError,TypeError,StopIteration,IndexError,OverflowError): self.reason='sensor_or_report_error'
        return self.reason=='running'

    def snapshot(self):
        value = dict(version=2 if self.continuous_model else 1,state=self.reason,samples_observed=self.count,minimum_available_kib=self.minimum_available,
            peak_model_rss_kib=self.peak_model_rss,peak_backend_rss_kib=self.peak_backend_rss,max_battery_deci_c=self.max_temp,
            remaining_ms=None if self.continuous_model else max(0,self.deadline-self.clock()),samples=list(self.samples),human_content_recorded=False)
        if self.continuous_model: value['lifetime']='continuous_model_v1'
        return value

    def finish(self):
        if self.reason=='running': self.reason='stopped'
        if self.report is not None: self.report(self.snapshot())


def validate_report(value):
    keys={'version','state','samples_observed','minimum_available_kib','peak_model_rss_kib','peak_backend_rss_kib',
        'max_battery_deci_c','remaining_ms','samples','human_content_recorded'}
    states={'running','manual_stop','time_limit','stale_monitor','low_memory','battery_warm','sensor_or_report_error','stopped','model_exit','cancellation_stuck'}
    if not isinstance(value,dict) or type(value.get('version')) is not int: raise ValueError('Invalid numeric report')
    continuous=value['version']==2
    if continuous:
        from continuous_voice import END_REASONS
        states=states|END_REASONS
    expected=keys|{'lifetime'} if continuous else keys
    if (set(value)!=expected or value['version'] not in (1,2)
            or continuous and (value['lifetime']!='continuous_model_v1' or value['remaining_ms'] is not None)
            or type(value['state']) is not str or value['state'] not in states or value['human_content_recorded'] is not False
            or not isinstance(value['samples'],list) or len(value['samples'])>120): raise ValueError('Invalid numeric report')
    for key in keys-{'version','state','samples','human_content_recorded'}:
        item=value[key]
        if continuous and key=='remaining_ms': continue
        if item is None and key=='minimum_available_kib': continue
        if type(item) is not int or not 0<=item<=10**12: raise ValueError('Invalid numeric report')
    fields=set(Sample.__dataclass_fields__)
    for sample in value['samples']:
        if not isinstance(sample,dict) or set(sample)!=fields|{'model_cpu_percent_one_core','backend_cpu_percent_one_core'}:
            raise ValueError('Invalid numeric series')
        Sample(**{k:sample[k] for k in fields}).validate()
        for key in ('model_cpu_percent_one_core','backend_cpu_percent_one_core'):
            if type(sample[key]) not in (int,float) or not 0<=sample[key]<=100000: raise ValueError('Invalid CPU sample')
    return value


def write_report(path,value):
    """Atomic numeric report in an explicitly supplied private, owned directory."""
    validate_report(value)
    path=Path(path)
    data=json.dumps(value,allow_nan=False).encode('utf-8')
    if len(data)>65536: raise ValueError('Oversized numeric report')
    directory=private_directory(path.parent)
    temporary=path.name+'.new'; inode=None
    try:
        try: info=os.stat(path.name,dir_fd=directory,follow_symlinks=False)
        except FileNotFoundError: pass
        else:
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o600:
                raise ValueError('Unknown report file')
        fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=directory); inode=os.fstat(fd).st_ino
        with os.fdopen(fd,'wb') as output: output.write(data); output.flush(); os.fsync(output.fileno())
        os.replace(temporary,path.name,src_dir_fd=directory,dst_dir_fd=directory)
        os.fsync(directory)
    finally:
        try:
            if inode is not None and os.stat(temporary,dir_fd=directory,follow_symlinks=False).st_ino==inode:
                os.unlink(temporary,dir_fd=directory)
        except FileNotFoundError: pass
        os.close(directory)


def private_directory(path):
    directory=os.open(path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    info=os.fstat(directory)
    if info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o700:
        os.close(directory); raise ValueError('Private owned directory required')
    return directory


def read_report(path):
    path=Path(path); directory=private_directory(path.parent)
    try:
        fd=os.open(path.name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=directory)
        with os.fdopen(fd,'rb') as stream:
            info=os.fstat(stream.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o600 or info.st_size>65536:
                raise ValueError('Invalid private report')
            raw=stream.read(65537)
        if len(raw)>65536: raise ValueError('Oversized private report')
        return validate_report(json.loads(raw))
    finally: os.close(directory)


def request_stop(work):
    """Fixed sentinel via an owned directory FD; never follow a STOP symlink."""
    directory=os.open(work,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    try:
        info=os.fstat(directory)
        if info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o700: raise ValueError('Private work directory required')
        try: fd=os.open('STOP',os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=directory)
        except FileExistsError:
            info=os.stat('STOP',dir_fd=directory,follow_symlinks=False)
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o600:
                raise ValueError('Unknown stop sentinel')
        else: os.close(fd)
    finally: os.close(directory)


def cleanup_credentials(work,inode):
    """Only this run's fixed private files; retain numeric metrics, refuse changes."""
    directory=os.open(work,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    try:
        info=os.fstat(directory)
        if info.st_ino!=inode or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o700:
            raise ValueError('Work ownership changed')
        for name in ('key','server.pid','deadline_ms','mode','STOP'):
            try: info=os.stat(name,dir_fd=directory,follow_symlinks=False)
            except FileNotFoundError: continue
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=0o600:
                raise ValueError('Unknown private work file')
            os.unlink(name,dir_fd=directory)
    finally: os.close(directory)


class ResidentRuntime:
    """One verified owner, one lease at a time. Explicit lifetime, no renewal/restart."""
    session_limit=24
    continuous_model=False
    class Lease:
        def __init__(self,runtime): self.runtime,self.closed=runtime,False
        def alive(self): return not self.closed and self.runtime.alive()
        def stop(self): self.closed=True # Never signal/stop the shared model here.

    def __init__(self,owner,clock,deadline,monitor):
        if type(deadline) is not int or not 0<deadline-clock()<=1800000 or monitor.deadline!=deadline:
            raise ValueError('Invalid resident deadline')
        self.owner,self.clock,self.deadline,self.monitor=owner,clock,deadline,monitor
        self.active=None; self.closed=False; self.sessions=0; self.lock=threading.RLock()

    def alive(self): return not self.closed and self.clock()<self.deadline and self.owner.alive()
    def check(self):
        if self.closed: return False
        if not self.owner.alive(): self.monitor.reason='model_exit'; return False
        return self.monitor.check()
    def may_accept(self,seconds): return seconds in (90,300) and self.deadline-self.clock()>=seconds*1000+5000

    def acquire(self):
        with self.lock:
            if self.active is not None or not self.check() or self.session_limit is not None and self.sessions>=self.session_limit:
                raise ValueError('Resident model unavailable/busy')
            self.active=self.Lease(self); self.sessions+=1; return self.active

    def release(self,model):
        with self.lock:
            if model.owned is not self.active: raise ValueError('Foreign resident lease')
            model.cancel()
            if model.thread is not None and model.thread.is_alive():
                self.monitor.reason='cancellation_stuck'; self.shutdown(); raise RuntimeError('Resident generation did not stop')
            model.memory.pairs.clear(); model.memory.last_human=0
            model.result=model.error=None; model.context_metrics.clear()
            self.active=None

    def shutdown(self):
        with self.lock:
            if self.closed: return
            self.closed=True
            if self.active is not None: self.active.stop()
            self.owner.stop()


class ContinuousResidentRuntime(ResidentRuntime):
    """Explicit indefinite MODEL ownership, not automatic microphone activation.

    No fake far-future deadline or renewals. Guards/manual stop still latch. A
    separate voice-lifecycle contract is required before a microphone can persist.
    """
    session_limit=None
    continuous_model=True

    def __init__(self,owner,clock,monitor,*,enabled=False,cache_purger=None):
        if enabled is not True or not monitor.continuous_model or monitor.deadline is not None or not callable(cache_purger):
            raise ValueError('Continuous model mode not explicitly enabled')
        self.owner,self.clock,self.deadline,self.monitor=owner,clock,None,monitor
        self.cache_purger=cache_purger
        self.active=None; self.closed=False; self.sessions=0; self.lock=threading.RLock()

    def alive(self): return not self.closed and self.owner.alive()
    def may_accept(self,seconds): return seconds in (90,300) and self.alive()

    def _purge(self,model):
        from continuous_voice import ContinuousVoiceFault
        try:
            # A context reset can overlap a lingering warm_public_prefix/generation
            # thread; cancel it and wait instead of failing immediately, so the
            # cache erase never races a busy engine slot.
            if model.thread is not None and model.thread.is_alive() and not model.cancel_generation():
                raise ValueError('Engine generation did not stop before cache retirement')
            if not self.alive(): raise ValueError('Engine owner unavailable')
            count=self.cache_purger(model)
            if type(count) is not int or not 0<=count<=2048: raise ValueError('Unconfirmed engine cache retirement')
        except Exception:
            self.monitor.reason='voice_cache_clear_failed'; self.shutdown()
            raise ContinuousVoiceFault('voice_cache_clear_failed') from None
        return True

    def purge_cache(self,model):
        if model.owned is not self.active: raise ValueError('Foreign engine cache lease')
        return self._purge(model)

    def release(self,model):
        super().release(model)
        if self.closed: return
        if self.monitor.reason != 'running': self.shutdown(); return
        if not self.owner.alive(): self.monitor.reason='model_exit'; self.shutdown(); return
        self._purge(model)
