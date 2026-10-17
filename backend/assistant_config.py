"""Public assistant identity; never contains credentials."""
import json
from pathlib import Path


def load_settings():
    installed = Path(__file__).with_name("assistant.json")
    source = Path(__file__).resolve().parents[1] / "config" / "assistant.json"
    data = json.loads((installed if installed.exists() else source).read_text(encoding="utf-8"))
    if not isinstance(data.get("name"), str) or not 1 <= len(data["name"]) <= 64:
        raise ValueError("Invalid assistant name")
    if not isinstance(data.get("wake_word"), str) or not data["wake_word"].isalpha():
        raise ValueError("Expected a single wake word")
    if not isinstance(data.get("wake_enabled"), bool):
        raise ValueError("Explicit wake_enabled boolean required")
    return data


SETTINGS = load_settings()
