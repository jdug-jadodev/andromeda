"""One public technical generation: restored native history including unanswered user.

No ASR, microphone, TTS or executor. Not a semantic-recall/voice acceptance test.
"""
import json
import sys
import uuid

sys.path.insert(0, '/opt/doha-voice/pilot')
from conversation_voice import ConversationModel, now_ms


def main():
    model = ConversationModel(sys.argv[1]); now = now_ms()
    try:
        model.memory.restore({'version': 1, 'id': uuid.uuid4().hex, 'last_human_ms': now - 1000, 'turns': [
            {'id': uuid.uuid4().hex, 'user': '¿Qué es una estrella?', 'human_ms': now - 3000,
             'assistant': 'Una estrella es una estrella, una estrella es una estrella brillante que emite luz y calor.'},
            {'id': uuid.uuid4().hex, 'user': '¿Qué es un planeta?', 'human_ms': now - 1000}]})
        model.start('Responde en una frase: ¿qué es una nebulosa?', now, now + 90000)
        model.thread.join(23)
        if not model.done.is_set() or model.error or not model.result:
            raise RuntimeError('Native restored context generation failed')
        if model.context_metrics['history_turns_used'] != 2 or model.context_metrics['history_pairs_used'] != 1:
            raise RuntimeError('Native source history was lost')
        print(json.dumps({'event': 'native_context_probe', 'native_context_accepted': True,
            **model.context_metrics, 'model': 'lfm', 'microphone_opened': False, 'tts_initialized': False,
            'semantic_recall_verified': False, 'actions_executed': 0}), flush=True)
    finally: model.cancel()


if __name__ == '__main__': main()
