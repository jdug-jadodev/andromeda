"""Single-turn helpers. Inputs are local files; no tools, network or shell execution."""
import argparse
import json
from pathlib import Path
import re
import time
import wave


def assistant_name():
    installed = Path(__file__).with_name("assistant.json")
    source = Path(__file__).resolve().parents[1] / "config" / "assistant.json"
    name = json.loads((installed if installed.exists() else source).read_text(encoding="utf-8"))["name"]
    if not isinstance(name, str) or not name or "<|" in name:
        raise ValueError("Invalid assistant name")
    return name


def prompt_for(text, family="lfm2"):
    # Do not allow recognized text to inject model control-token boundaries.
    text = re.sub(r"<\|[^>]*\|>", "", text).strip()[:1200]
    if not text:
        raise ValueError("No se reconocio una pregunta")
    return (
        "<|im_start|>system\n"
        "Eres " + assistant_name() + ". Responde en español en una frase breve y clara. "
        "Si no sabes algo, dilo. En esta prueba no tienes acceso a Internet "
        "ni control de aparatos, alarmas o recordatorios.\n<|im_end|>\n"
        "<|im_start|>user\n" + text + "\n<|im_end|>\n"
        "<|im_start|>assistant\n"
        # Only Qwen3 needs this non-thinking prefix; do not add it to LFM2.
        + ("<think>\n\n</think>\n\n" if family == "qwen3" else "")
    )


def clean_answer(text):
    text = text.split("<|im_end|>", 1)[0].split("<|im_start|>", 1)[0]
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.DOTALL)
    if "<think>" in text:
        raise ValueError("Se descarta razonamiento incompleto, no una respuesta final")
    # A closing tag can remain if a model repeats the prefilled boundary.
    text = text.replace("</think>", "")
    text = text.replace("[end of text]", "")
    text = re.sub(r"\x1b\[[0-9;]*[A-Za-z]", "", text)
    text = re.sub(r"<\|[^>]*\|>", "", text)
    text = " ".join(text.split())[:700]
    if not text:
        raise ValueError("La IA no produjo respuesta")
    return text + "\n"


def transcribe(audio, prompt_path):
    import resource
    from vosk import KaldiRecognizer, Model, SetLogLevel

    SetLogLevel(-1)
    started = time.monotonic()
    with wave.open(str(audio), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (1, 2, 16000):
            raise ValueError("Se requiere WAV PCM mono de 16 bits a 16000 Hz")
        duration = source.getnframes() / source.getframerate()
        if not 0 < duration <= 12:
            raise ValueError("Duracion fuera del limite de prueba")
        model = Model("/opt/doha-voice/models/vosk-model-small-es-0.42")
        recognizer = KaldiRecognizer(model, 16000)
        chunks = []
        while data := source.readframes(4000):
            if recognizer.AcceptWaveform(data):
                chunks.append(json.loads(recognizer.Result()).get("text", ""))
        chunks.append(json.loads(recognizer.FinalResult()).get("text", ""))
    text = " ".join(filter(None, chunks)).strip()
    Path(prompt_path).write_text(prompt_for(text), encoding="utf-8")
    # Only prints to the requesting terminal. The wrapper keeps no transcript log.
    print(json.dumps({
        "recognized": text,
        "audio_seconds": round(duration, 2),
        "transcription_seconds": round(time.monotonic() - started, 2),
        "transcription_peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
    }, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    decode = commands.add_parser("transcribe")
    decode.add_argument("audio")
    decode.add_argument("prompt")
    clean = commands.add_parser("clean")
    clean.add_argument("source")
    clean.add_argument("destination")
    sample = commands.add_parser("sample-prompt")
    sample.add_argument("destination")
    args = parser.parse_args()
    if args.command == "transcribe":
        transcribe(args.audio, args.prompt)
    elif args.command == "sample-prompt":
        Path(args.destination).write_text(prompt_for("¿Por qué el cielo es azul?"), encoding="utf-8")
    else:
        text = Path(args.source).read_text(encoding="utf-8", errors="replace")
        Path(args.destination).write_text(clean_answer(text), encoding="utf-8")


if __name__ == "__main__":
    main()
