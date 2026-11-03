"""Parser determinista de las frases de TV. Entrada: texto humano. Salida:
(accion, parametros) o None si no se puede determinar. Nunca inventa valores."""
import re

from doja_intents import normalized


def _clamp(v):
    return max(0, min(100, v))


def parse_volume(text):
    t = normalized(text)
    if re.search(r'\b(quita|saca|desactiva|sin)\b.*\b(silencio|mute)\b', t):
        return {'mute': False}
    if re.search(r'\b(silencia|silenciar|mutea|mute|silencio)\b', t):
        return {'mute': True}
    if 'a la mitad' in t:
        return {'level': 50}
    m = (re.search(r'\b(?:volumen|sonido)\b[^\d]{0,12}(\d{1,3})\b', t)
         or re.search(r'\b(\d{1,3})\s*(?:%|por ?ciento)\b', t))
    if m:
        return {'level': _clamp(int(m.group(1)))}
    if re.search(r'\b(sube|subir|subele|mas|aumenta)\b', t):
        return {'delta': 5}
    if re.search(r'\b(baja|bajar|bajale|menos|disminuye)\b', t):
        return {'delta': -5}
    return None


NUM = {'uno': 1, 'un': 1, 'dos': 2, 'tres': 3, 'cuatro': 4}


def parse_input(text):
    t = normalized(text)
    if re.search(r'\b(tele|antena|canales|tv en vivo|en vivo)\b', t):
        return {'id': 'TV'}
    m = re.search(r'\b(?:hdmi|entrada|fuente|puerto)\s*(?:numero\s*)?([1-4]|uno|dos|tres|cuatro)\b', t)
    if m:
        n = m.group(1)
        return {'id': 'HDMI_%d' % (int(n) if n.isdigit() else NUM[n])}
    return None


def parse_scene(text):
    t = normalized(text)
    if re.search(r'\b(cine|pelicula|peli)\b', t):
        return {'name': 'cine'}
    if re.search(r'\b(noche|dormir|a dormir)\b', t):
        return {'name': 'noche'}
    if re.search(r'\b(manana|buenos dias|despertar)\b', t):
        return {'name': 'manana'}
    return None


def parse_audio(text):
    t = normalized(text)
    if re.search(r'\b(barra|soundbar|optica|optical|lgsync|sound sync)\b', t):
        return {'output': 'external_optical'}
    if re.search(r'\b(altavoz|parlante|bocina)\b', t):
        return {'output': 'tv_speaker'}
    if re.search(r'\b(arregla|corrige|restaura|por defecto)\b', t):
        return {'enforce': True}
    return None


def parse_app(text):
    t = normalized(text)
    m = re.search(r'\b(?:abre|abrir|lanza|lanzar|pon)\b\s+(?:la\s+|el\s+)?(?:app\s+|aplicacion\s+)?(.+)$', t)
    if not m:
        return None
    name = m.group(1).strip(' .,!¡?¿')
    # el servidor resuelve por id o título; si no existe, devolverá error
    return {'name': name} if 1 < len(name) <= 40 else None
