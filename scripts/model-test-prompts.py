"""Generate public synthetic model tests; no microphone or user history."""
import importlib.util
from pathlib import Path
import sys

source = Path(__file__).with_name("voice-turn.py")
spec = importlib.util.spec_from_file_location("voice_turn", source)
voice = importlib.util.module_from_spec(spec)
spec.loader.exec_module(voice)

destination = Path(sys.argv[1])
destination.mkdir(parents=True, exist_ok=True)
questions = {
    "sky": "¿Por qué el cielo es azul?",
    "blue_red": "¿Qué luz se dispersa más en el aire, la azul o la roja?",
    "identity": "¿Cómo te llamas?",
    "weather": "¿Qué temperatura hace ahora en China?",
    "timer": "Programa un temporizador de cinco minutos.",
    "biodiversity": "¿Es Colombia el país más biodiverso del mundo en número total de especies?",
}
for name, question in questions.items():
    (destination / f"{name}.txt").write_text(voice.prompt_for(question, family="lfm2"), encoding="utf-8")
