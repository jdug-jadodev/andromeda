"""Small dialogue controls, not a classifier for conversational answers or tools.

Only whole, explicit closing utterances end a chat. An isolated 'no' applies
only to a follow-up offer actually spoken in the immediately preceding turn.
"""
from doja_intents import normalized


class ConversationFlow:
    CLOSE = {'cierra la conversacion', 'cierra esta conversacion', 'cierra el chat',
             'termina la conversacion', 'gracias', 'muchas gracias', 'gracias andromeda'}
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
        if self.offered_more and plain in {'no', 'no gracias', 'no, gracias'}: return 'declined_more'
        return None

    def spoken(self, answer):
        # No substring matching of arbitrary 'no' or questions such as '¿No?'.
        # Other formulations remain ordinary conversation, never forced closure.
        final_question = answer.rsplit('¿', 1)
        self.offered_more = (len(final_question) == 2 and answer.rstrip().endswith('?')
                             and not any(mark in answer for mark in ['"', '«', '»'])
                             and (not final_question[0].strip() or final_question[0].rstrip().endswith(('.', '!')))
                             and self.control_text(final_question[-1]) in self.OFFERS)

    def reset(self):
        self.offered_more = False
