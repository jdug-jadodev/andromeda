"""Small dialogue controls, not a classifier for conversational answers or tools.

Only whole, explicit closing utterances end a chat. An isolated 'no' applies
only to a follow-up offer actually spoken in the immediately preceding turn.
"""
from doja_intents import normalized
from affirmation import is_negative


class ConversationFlow:
    QUESTION = '¿Necesitas algo más?'
    FAREWELL = 'Está bien, aquí estaré cuando me necesites.'
    CLOSE = {'cierra la conversacion', 'cierra esta conversacion', 'cierra el chat',
             'termina la conversacion', 'gracias', 'muchas gracias', 'gracias andromeda',
             'eso era todo', 'eso es todo', 'ya con eso', 'eso era todo gracias',
             'eso es todo gracias', 'no necesito nada mas', 'no necesito nada',
             'era todo', 'solo era eso', 'es todo', 'ya esta', 'con eso basta'}
    OFFERS = {'necesitas algo mas', 'necesitas alguna cosa mas',
              'te puedo ayudar en algo mas', 'puedo ayudarte en algo mas'}

    def __init__(self):
        self.offered_more = False

    @staticmethod
    def control_text(text):
        # Keep quotes and internal punctuation: quoting a command is not using it.
        return normalized(text).strip(' .,!¡?¿')

    def closing_reason(self, text):
        plain = self.control_text(text)
        if plain in self.CLOSE: return 'explicit_close'
        if self.offered_more and is_negative(text): return 'declined_more'
        return None

    @classmethod
    def direct_offer(cls, answer):
        # No substring matching of arbitrary 'no' or questions such as '¿No?'.
        # Other formulations remain ordinary conversation, never forced closure.
        final_question = answer.rsplit('¿', 1)
        return (len(final_question) == 2 and answer.rstrip().endswith('?')
                             and not any(mark in answer for mark in ['"', '«', '»'])
                             and (not final_question[0].strip() or final_question[0].rstrip().endswith(('.', '!')))
                             and cls.control_text(final_question[-1]) in cls.OFFERS)

    @classmethod
    def with_offer(cls, answer, maximum=700):
        """Solo valida el tamaño: nunca pega una muletilla fija.

        Antes la casa añadía «¿Necesitas algo más?» al final de casi cualquier
        respuesta y sonaba a formulario. Ahora cierra el modelo con sus propias
        palabras; `direct_offer` sigue reconociendo una oferta que él diga, para
        que un «no» después cierre la charla.
        """
        if len(answer.encode('utf-16-le')) // 2 > maximum: raise ValueError('Oversized speech')
        return answer

    def spoken(self, answer, offered_more=None):
        self.offered_more = self.direct_offer(answer) if offered_more is None else bool(offered_more)

    def reset(self):
        self.offered_more = False
