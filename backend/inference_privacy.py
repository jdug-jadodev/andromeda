"""Fixed local engine cache retirement; no human input, executors or files.

Continuous worker MUST disable secondary prompt RAM cache and constrain slot
storage to a verified non-writable root directory. A public task with an explicit
ONE-token cap replaces task_prev; discard its output, then erase tokens/KV/checkpoints.
b11669 samples before checking its limit, so n_predict=0 is not a zero-token proof.
"""
import http.client
import json
from continuous_voice import ContinuousVoiceFault


def purge_engine_cache(key, clock, owner_alive):
    if not isinstance(key,str) or len(key)<32: raise ContinuousVoiceFault('voice_cache_clear_failed')
    deadline=clock()+3000
    def post(path,body):
        if not owner_alive() or clock()>=deadline: raise ValueError('Engine cache owner unavailable')
        connection=http.client.HTTPConnection('127.0.0.1',11435,timeout=max(.001,(deadline-clock())/1000))
        try:
            connection.request('POST',path,json.dumps(body).encode(),{'Authorization':'Bearer '+key,'Content-Type':'application/json'})
            response=connection.getresponse(); raw=response.read(8193)
            if response.status!=200 or len(raw)>8192 or clock()>=deadline or not owner_alive(): raise ValueError('Engine cache response invalid')
            return json.loads(raw)
        finally: connection.close()
    step='public_transport'
    try:
        public=post('/completion',{'prompt':'Hola.','id_slot':0,'n_predict':1,'stream':False,'cache_prompt':False,'temperature':0,'seed':42,
            'response_fields':['content','tokens_predicted','id_slot']})
        step='public_receipt'
        if (not isinstance(public,dict) or set(public)!={'content','tokens_predicted','id_slot'}
                or not isinstance(public['content'],str) or len(public['content'])>256
                or type(public['tokens_predicted']) is not int or not 0<=public['tokens_predicted']<=1
                or public['tokens_predicted']==0 and public['content']!=''
                or type(public['id_slot']) is not int or public['id_slot']!=0): raise ValueError('Public cache retirement not confirmed')
        public=None  # Never return, log, interpret or grant from generated maintenance content.
        step='erase_transport'
        erased=post('/slots/0?action=erase',{})
        step='erase_receipt'
        if (not isinstance(erased,dict) or set(erased)!={'id_slot','n_erased'} or type(erased['id_slot']) is not int or erased['id_slot']!=0
                or type(erased['n_erased']) is not int or not 0<=erased['n_erased']<=2048): raise ValueError('Slot cache erase not confirmed')
        return erased['n_erased']
    except (OSError,ValueError,TypeError,KeyError,OverflowError):
        fault=ContinuousVoiceFault('voice_cache_clear_failed'); fault.detail=step
        raise fault from None
