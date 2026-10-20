"""Explicit resident TLS entry, bounded by default. Never opens an Android microphone."""
import argparse
import os
from pathlib import Path
import signal
import stat

from assistant_config import SETTINGS
from conversation_voice import OwnedInference, now_ms, serve
from resident_runtime import Monitor, ProcSampler, ResidentRuntime, ContinuousResidentRuntime, write_report
from spanish_wake import SpanishWakeDetector
from web_search_env import configure
from inference_privacy import purge_engine_cache


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work',required=True)
    parser.add_argument('--synthetic-input',action='store_true')
    parser.add_argument('--guided-agenda',action='store_true')
    parser.add_argument('--continuous-model',action='store_true',help='Explicit continuous model ownership; does not enable continuous Android capture')
    parser.add_argument('--continuous-voice',action='store_true',help='Explicit protocol12 continuous local voice, no bounded trial renewal')
    parser.add_argument('--alarm-lifecycle',action='store_true',help='Coordinated protocol13 weekly alarm/deletion policy')
    args=parser.parse_args()
    if args.continuous_voice and (not args.continuous_model or not args.guided_agenda): raise ValueError('Continuous voice requires continuous model and guided tools')
    if args.alarm_lifecycle and not args.continuous_voice: raise ValueError('Alarm lifecycle requires continuous voice')
    work=Path(args.work)
    if (work.parent!=Path('/tmp') or not work.name.startswith('andromeda-resident.') or work.is_symlink()
            or not work.is_dir() or work.stat().st_uid!=os.geteuid() or stat.S_IMODE(work.stat().st_mode)!=0o700):
        raise ValueError('Private resident work directory required')
    if os.geteuid()!=10218: raise ValueError('Resident backend must be unprivileged')
    for name in ('key','mode' if args.continuous_model else 'deadline_ms'):
        info=(work/name).lstat()
        if not stat.S_ISREG(info.st_mode) or info.st_uid!=10218 or stat.S_IMODE(info.st_mode)!=0o600:
            raise ValueError('Private resident inputs required')
    if args.continuous_model:
        if (work/'mode').read_text()!='continuous_model_v1\n' or (work/'deadline_ms').exists() or (work/'deadline_ms').is_symlink(): raise ValueError('Explicit continuous model mode required')
        deadline=None
    else:
        deadline=int((work/'deadline_ms').read_text())
        if not 0<deadline-now_ms()<=1800000 or (work/'mode').exists() or (work/'mode').is_symlink(): raise ValueError('Invalid resident lifetime')
    owner=OwnedInference(work/'key'); runtime=None; monitor=None
    stopping={'requested':False}
    def stop(*_): stopping['requested']=True
    signal.signal(signal.SIGTERM,stop); signal.signal(signal.SIGINT,stop)
    try:
        # Initialization is already covered by the independent native worker.
        # Start the 1-second sampler only after loading ASR, so a normal cold
        # load cannot be mistaken for a stalled running monitor.
        configure(os.environ)
        detector=SpanishWakeDetector(SETTINGS['wake_word'],Path('/opt/doha-voice/models/vosk-model-small-es-0.42'))
        monitor=Monitor(now_ms,ProcSampler(owner.pid,now_ms),deadline,
            report=lambda value:write_report(work/'metrics.json',value),stop_requested=lambda:stopping['requested'] or (work/'STOP').exists(),continuous_model=args.continuous_model)
        runtime=ContinuousResidentRuntime(owner,now_ms,monitor,enabled=True,cache_purger=lambda model:purge_engine_cache(model.key,now_ms,owner.alive)) if args.continuous_model else ResidentRuntime(owner,now_ms,deadline,monitor)
        if not runtime.check(): raise RuntimeError('Resident resource guard')
        try:
            serve('/var/lib/andromeda-conversation/token',work/'key',detector,0 if args.continuous_voice else 300,
                '/var/lib/andromeda-conversation/server-cert.pem','/var/lib/andromeda-conversation/server-key.pem',
                'synthetic_pcm' if args.synthetic_input else 'microphone',runtime.check,assistant_tools=True,resident=runtime,guided_agenda=args.guided_agenda,
                continuous_voice=args.continuous_voice,alarm_lifecycle=args.alarm_lifecycle)
        except RuntimeError:
            if monitor.reason not in {'manual_stop','time_limit','low_memory','battery_warm','stale_monitor','sensor_or_report_error','model_exit'}: raise
    finally:
        if runtime is not None: runtime.shutdown()
        else: owner.stop()
        if monitor is not None: monitor.finish()


if __name__=='__main__': main()
