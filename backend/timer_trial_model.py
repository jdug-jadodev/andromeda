"""Proposal-only native model adapter for the explicit TLS timer trial.

No handlers or Android commands are dispatched here. The default conversation
still constructs ConversationModel and continues to reject all tools.
"""
import copy

from conversation_voice import ConversationModel, conversational_reply
from timer_proposals import TIMER_TOOL, TimerValidationError, direct_timer_prefix, literal_seconds, selected_timer_seconds

TIMER_SYSTEM = ('Eres Andrómeda. Responde en español en una o dos frases breves. '
                'create_timer está disponible para PROPONER un timer, temporizador o cuenta atrás con duración explícita en la petición actual. '
                'Copia toda la duración literal en duration_text; no conviertas unidades ni inventes datos. '
                'No propongas peticiones negadas, citadas ni hipotéticas. Si faltan datos, pregunta. '
                'El programa valida, ejecuta una vez y comunica el resultado real de Android. '
                'No afirmes haber ejecutado o programado nada. Tras una acción se cierra; consultas continúan. '
                'Sin Internet ni otras herramientas.')


class TimerTrialModel(ConversationModel):
    @staticmethod
    def payload(messages, maximum=96):
        messages = copy.deepcopy(messages)
        if not messages or messages[0].get('role') != 'system': raise ValueError('Missing system contract')
        messages[0]['content'] = TIMER_SYSTEM
        result = ConversationModel.payload(messages, maximum)
        result.update(tools=[copy.deepcopy(TIMER_TOOL)], tool_choice='auto', parallel_tool_calls=False)
        return result

    def interpret_result(self, message, finish_reason, request):
        def finish(result):
            metrics = getattr(self, 'context_metrics', None)
            if isinstance(metrics, dict):
                metrics.update(timer_model_choice='native' if message.get('tool_calls') else 'text',
                               timer_model_kind=result['kind'], timer_validation=result.get('reason', 'none'))
            return result  # Metrics contain fixed enums only, never request/model text.
        if message.get('tool_calls'):
            try: return finish({'kind': 'timer_proposal', 'seconds': selected_timer_seconds(message, finish_reason, request)})
            except TimerValidationError as invalid:
                # Do NOT repair the model's duration, execute, or ask for a yes
                # that would authorize its incorrect arguments. Start over.
                return finish({'kind': 'timer_rejected', 'reason': invalid.reason})
            except ValueError:
                return finish({'kind': 'timer_rejected', 'reason': 'invalid_call'})
        # A complete timer request cannot be fulfilled by model prose, even
        # "listo" with no recognizable action verb. Only a native, validated
        # proposal and the later Android receipt may enter that success path.
        try: literal_seconds(request)
        except TimerValidationError as invalid:
            if invalid.reason in {'missing_duration', 'out_of_range'} or direct_timer_prefix(request):
                return finish({'kind': 'timer_rejected', 'reason': invalid.reason})
        except ValueError: pass
        else: return finish({'kind': 'timer_rejected', 'reason': 'invalid_call'})
        return finish({'kind': 'reply', 'text': conversational_reply(message, finish_reason)})


def rejection_reply(result):
    """Deterministic action-validation UX, not a generated conversation answer."""
    reason = result.get('reason') if isinstance(result, dict) else None
    prompts = {
        'missing_duration': 'No he creado ningún temporizador con esa petición. ¿De cuánto tiempo lo quieres? Repite la petición completa con la duración.',
        'duration_mismatch': 'No he creado ningún temporizador con esa petición: no pude validar la duración. Repite una sola petición con la duración completa.',
        'invalid_request': 'No he creado ningún temporizador con esa petición. Repite una petición directa y completa; separa otras órdenes o preguntas, sin negaciones ni citas.',
        'out_of_range': 'No he creado ningún temporizador con esa petición. La duración permitida va de un segundo a veinticuatro horas.',
        'invalid_call': 'No he creado ningún temporizador con esa petición. La herramienta está disponible, pero no obtuve una propuesta válida; repite la petición completa con la duración.',
    }
    if not isinstance(result, dict) or result.get('kind') != 'timer_rejected' or reason not in prompts:
        raise ValueError('Unknown timer validation result')
    return prompts[reason]
