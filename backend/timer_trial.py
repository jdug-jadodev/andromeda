"""Restricted timer conversation controller. Android alone owns execution.

No free-form tool results, shell, scheduling or persistent conversation data.
Only the separate, explicitly negotiated timer-trial transport may construct it.
"""
import json

from timer_proposals import TimerProposals
from timer_trial_model import rejection_reply
from interaction_policy import TIMER_POLICY


class TimerTrial:
    def __init__(self, clock):
        self.clock, self.proposals = clock, TimerProposals(clock)
        self.inflight = None

    def model_result(self, result, turn, session, context, session_end):
        if result['kind'] == 'reply': return {'text': result['text']}
        if result['kind'] == 'timer_rejected':
            return {'text': rejection_reply(result), 'timer_control': 'rejected'}
        if result['kind'] != 'timer_proposal': raise ValueError('Unknown timer model result')
        if self.inflight is not None: raise ValueError('Timer executor is already active')
        # The literal guard accepts only a complete, standalone current request.
        # A mixed/ambiguous request is rejected, never silently truncated.
        if TIMER_POLICY.route(validated=True, standalone=True) != 'execute_close':
            raise ValueError('Timer policy does not authorize a direct action')
        self.inflight = self.proposals.authorize_request(result['seconds'], turn['text'], session, context,
                                                        turn['id'], turn['seq'], turn['speech_end'], session_end)
        return {'event': dict(self.inflight, type='timer_execute'), 'executing': True}

    def result(self, packet, session, context):
        grant = self.inflight
        if grant is None: return None  # A duplicate result never creates a new action.
        p = grant['proposal']
        expected = {'session_id': session, 'context_id': context, 'proposal_id': p['proposal_id'],
                    'timer_id': p['timer_id'], 'nonce': p['nonce'], 'seconds': p['seconds'],
                    'request_turn_id': grant['request_turn_id'], 'request_sequence': grant['request_sequence']}
        if (any(type(packet.get(k)) is not type(v) or packet[k] != v for k, v in expected.items())
                or packet.get('status') not in {'CREATED', 'DUPLICATE', 'REJECTED', 'FAILED', 'UNKNOWN'}):
            raise ValueError('Executor result does not belong to the authorized timer')
        self.inflight = None
        status = packet['status']
        print(json.dumps({'event': 'timer_executor_result', 'status': status,
                          'android_creations_acknowledged': int(status == 'CREATED')}), flush=True)
        text = {
            'CREATED': f'Temporizador de {p["seconds"]} segundos activado.',
            'DUPLICATE': 'Esa orden ya tenía un registro; no la repetí. Revisa los temporizadores en la pantalla.',
            'REJECTED': 'Android rechazó la creación; no he confirmado ningún temporizador nuevo.',
            'FAILED': 'Android informó que la creación falló y retiró ese intento.',
            'UNKNOWN': 'No pude confirmar el resultado. Revisa los temporizadores en la pantalla antes de pedir otro.',
        }[status]
        return {'text': text, 'timer_control': 'result', 'actions_executed': int(status == 'CREATED'),
                'close_reason': 'action_result', 'offered_more': False}

    def timed_out(self):
        if self.inflight is None: return None
        if self.clock() < self.inflight['execute_before_ms'] + 500: return None
        self.inflight = None
        return {'text': 'No llegó la confirmación de Android. El resultado es incierto; revisa los temporizadores en la pantalla antes de pedir otro.',
                'timer_control': 'result', 'close_reason': 'action_result', 'offered_more': False}

    def cancel(self): self.proposals.cancel()
    def close(self): self.proposals.close(); self.inflight = None
