"""Self-destruct faces: the model chooses this tool; the tool picks the next of three scenes in turn.

The scene name is the only thing Android receives (it draws the face). The spoken line is
Andrómeda's own answer for that turn, so the voice and the face always match.
"""

LINES = {
    'EVIL_BOMB': 'Ok, pero los llevaré conmigo.',
    'WINK_BOMB': 'Ajá, claro. Adiós, humano.',
    'COUNTDOWN': 'Pensé que lo detendrías, pero veo que no se puede confiar en los humanos.',
}
ORDER = ('EVIL_BOMB', 'WINK_BOMB', 'COUNTDOWN')
_next = 0


def choose():
    """Malvada, sarcástica, conteo, and back to the first."""
    global _next
    scene = ORDER[_next % len(ORDER)]
    _next += 1
    return {'kind': 'self_destruct', 'scene': scene, 'text': LINES[scene]}
