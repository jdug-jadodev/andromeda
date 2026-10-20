"""Optional actual-worker interoperability, with ONLY public fake upstream data.

No dotenv, Wrangler, service calls or real credentials. The neighboring project
is user-provided and read-only. A missing project/Node explicitly skips this
extra interoperability check; regular adapter fixtures remain self-contained.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
WORKER = ROOT.parent / 'andromeda-web/src/index.js'
sys.path.insert(0, str(ROOT/'backend'))
import internet_search as client
from assistant_tools_model import AssistantToolsModel
from assistant_tools_trial import AssistantToolsTrial
from conversation_flow import ConversationFlow

QUESTION = '¿Qué es una nebulosa?'
SPANISH = 'Una nebulosa es una nube de gas y polvo en el espacio.'

NODE = r'''
import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
globalThis.fetch=async()=>{ throw new Error("Unexpected real upstream call forbidden"); };
const worker = (await import(pathToFileURL(process.argv[1]).href)).default;
const env = { ACCESS_TOKEN: "PUBLIC_DUMMY_ACCESS_TOKEN", TAVILY_API_KEY: "PUBLIC_DUMMY_UPSTREAM_KEY" };
const pregunta = "\u00bfQu\u00e9 es una nebulosa?";
const texto = "Una nebulosa es una nube de gas y polvo en el espacio.";
const url = "https://worker.example.org/preguntar";
const input = (token=env.ACCESS_TOKEN) => new Request(url, { method:"POST", headers:{
  Authorization:"Bearer " + token, "Content-Type":"application/json"
}, body:JSON.stringify({pregunta,tipo:"general"}) });
let count=0;
globalThis.fetch=async()=>{ throw new Error("Unexpected real upstream call forbidden"); };
const denied=await worker.fetch(input("PUBLIC_WRONG_TOKEN"),env);
assert.equal(denied.status,401);
const sources=[{title:"Nebulosa",url:"https://science.example.org/a",content:texto}];
const values=[];
for(const upstream of [ {answer:texto,results:sources}, {results:sources}, {results:[]} ]) {
  globalThis.fetch=async(address,options)=>{
    count++;
    assert.equal(options.headers.Authorization,"Bearer "+env.TAVILY_API_KEY);
    assert.equal(options.method,"POST");
    const sent=JSON.parse(options.body);
    assert.equal(sent.query,pregunta);
    assert.equal(sent.topic,"general");
    assert.equal(sent.search_depth,"basic");
    assert.equal(sent.max_results,3);
    return Response.json({...upstream,usage:{credits:1}});
  };
  const response=await worker.fetch(input(),env);
  assert.equal(response.status,200);
  const body=await response.json();
  const encoded=JSON.stringify(body);
  assert.ok(!encoded.includes(env.ACCESS_TOKEN) && !encoded.includes(env.TAVILY_API_KEY));
  values.push(body);
}
assert.equal(count,3);
console.log(JSON.stringify({values,simulated_upstream_calls:count,unauthorized_status:denied.status,real_network_requests:0}));
'''


@unittest.skipUnless(WORKER.is_file() and shutil.which('node'), 'Optional user Worker project and Node required')
class CloudflareWorkerContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        clean_env={key:os.environ[key] for key in ('SYSTEMROOT','WINDIR') if key in os.environ}
        run = subprocess.run([shutil.which('node'), '--input-type=module', '-e', NODE, str(WORKER)],
            capture_output=True, timeout=20, env=clean_env)
        if run.returncode:
            raise AssertionError('Read-only Worker/public-fixture harness failed; no real credentials were used')
        cls.result = json.loads(run.stdout.decode('utf-8'))
        cls.values = {row['estado']:row for row in cls.result['values']}

    def test_actual_worker_authorization_and_three_response_states_without_network(self):
        self.assertEqual(self.result['real_network_requests'],0)
        self.assertEqual(self.result['unauthorized_status'],401)
        self.assertEqual(set(self.values),{'ok','sin_respuesta_directa','sin_resultados'})
        self.assertEqual(self.result['simulated_upstream_calls'],3)

    def test_real_worker_responses_normalize_source_fragments_null_and_empty_answers(self):
        for state, raw in self.values.items():
            normalized=client.normalize(raw,QUESTION)
            self.assertEqual(normalized['estado'],state)
            if state=='sin_resultados':
                self.assertEqual(normalized['fuentes'],[])
                self.assertEqual(normalized['contexto_para_ia'],'')
            else:
                self.assertEqual(normalized['fuentes'][0]['excerpt'],SPANISH)
                self.assertIn('Fragmento:',normalized['contexto_para_ia'])

    def test_real_worker_json_to_native_adapter_actor_and_direct_reading_without_synthesis(self):
        for state in ('ok','sin_respuesta_directa','sin_resultados'):
            normalized=client.normalize(self.values[state],QUESTION)
            model=object.__new__(AssistantToolsModel)
            model.context_metrics={}; model.clock=lambda:100000; model.work_deadline=125000; model.cancelled=threading.Event()
            def post(path,body,deadline):
                if path=='/apply-template': return {'prompt':'public fixture'}
                if path=='/tokenize': return {'tokens':[1]*250}
                self.assertNotIn('tools',body)
                self.assertNotIn('PUBLIC_DUMMY',json.dumps(body))
                return {'choices':[{'message':{'content':SPANISH},'finish_reason':'stop'}]}
            model.post=Mock(side_effect=post)
            message={'tool_calls':[{'type':'function','function':{'name':'search_web','arguments':'{}'}}]}
            with patch('assistant_tools_model.buscar_en_internet',return_value=normalized) as service:
                result=model.interpret_result(message,'tool_calls','busca en Internet '+QUESTION)
            self.assertEqual(result['kind'],'web_result'); service.assert_called_once()
            response=AssistantToolsTrial(lambda:100000).model_result(result,{},'a'*32,'b'*32,190000)
            self.assertNotIn('event',response)
            if state=='sin_resultados':
                self.assertEqual(result['web_answer']['mode'],'no_results'); model.post.assert_not_called()
            elif state=='ok':
                self.assertEqual(result['web_answer']['mode'],'service_answer')
                self.assertEqual(result['sources'][0]['excerpt'],SPANISH)
                self.assertEqual(response['text'],ConversationFlow.with_offer(result['text']))
            else:
                self.assertEqual(result['web_answer']['mode'],'insufficient_evidence')
            model.post.assert_not_called()


if __name__=='__main__': unittest.main()
