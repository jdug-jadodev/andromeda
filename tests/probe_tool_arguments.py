"""Demostración offline: el modelo aporta los datos y la casa los valida.

No abre micrófono, no toca Android, no guarda nada. Uso:
    python tests/probe_tool_arguments.py
"""
from datetime import datetime
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
import tool_arguments
from agenda_requests import BOGOTA

ANCHOR = int(datetime(2026, 11, 18, 14, 35, tzinfo=BOGOTA).timestamp() * 1000)


def show(title, kind, arguments, transcript, **options):
    result = tool_arguments.propose(kind, arguments, transcript, ANCHOR, **options)
    print(f'\n{title}')
    print(f'  dijo:    {transcript}')
    print(f'  modelo:  {json.dumps(arguments, ensure_ascii=False)}')
    if result['ok']:
        value = result['value']
        when = datetime.fromtimestamp(value['due_ms'] / 1000, BOGOTA).strftime('%Y-%m-%d %H:%M')
        print(f'  entendido: {kind} «{value["text"] or "(sin etiqueta)"}» '
              f'{value["repeat"]} -> {when} (fuente: {value["source"]})')
        print('  ejecutable: sí (cuando la app sepa ejecutar el valor autorizado)')
    else:
        print(f'  rechazado: {result["reason"]} -> {result["message"]}')


show('1. Tu frase de hoy, con la app ya preparada',
     'REMINDER',
     {'text': 'tomar el antibiótico',
      'when_text': 'todos los días de la semana a las diez y treinta de la mañana'},
     'recuérdame tomar el antibiótico todos los días de la semana a las diez y treinta de la mañana',
     series=True)

show('2. La misma frase con la app actual (sin series)',
     'REMINDER',
     {'text': 'tomar el antibiótico',
      'when_text': 'todos los días de la semana a las diez y treinta de la mañana'},
     'recuérdame tomar el antibiótico todos los días de la semana a las diez y treinta de la mañana')

show('3. Una frase que el parser no entiende, pero el modelo sí',
     'REMINDER',
     {'text': 'llamar al taller', 'when_text': 'el día martes a las tres y diez de la tarde',
      'date': '2026-11-24', 'time': '15:10'},
     'recuérdame llamar al taller el día martes a las tres y diez de la tarde')

show('4. El modelo inventa un tema que nadie dijo',
     'REMINDER',
     {'text': 'comprar pan', 'when_text': 'mañana a las once'},
     'recuérdame llamar al taller mañana a las once')

show('5. Alarma semanal (requiere el ciclo de vida de alarmas)',
     'ALARM',
     {'when_text': 'todos los martes a las siete de la mañana'},
     'pon una alarma todos los martes a las siete de la mañana', lifecycle=True)
