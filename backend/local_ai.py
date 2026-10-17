"""Local LFM planner. Model output is data, never executable code.

Mutations require a separate, exact human confirmation within 60 seconds.
Only the latest turn and a pending proposal are held in memory; no tools/web.
"""
import json
from pathlib import Path
import re
import time
import urllib.request

from assistant_config import SETTINGS
from doja_intents import normalized

SCHEMA = {
    "type": "object", "additionalProperties": False,
    "properties": {
        "action": {"type": "string", "enum": ["say", "create_timer", "cancel_timer", "list_timers"]},
        "seconds": {"type": "integer", "minimum": 0, "maximum": 86400},
        "all": {"type": "boolean"},
        "text": {"type": "string"},
    },
    "required": ["action", "seconds", "all", "text"],
}


def prompt_for(text):
    text = re.sub(r"<\|[^>]*\|>", "", text).strip()[:500]
    if not text:
        raise ValueError("Empty question")
    instruction = (
        f'You are {SETTINGS["name"]}, a Spanish voice assistant. Output JSON only. '
        'Classify user intent: say (chat/questions), create_timer (request a countdown), '
        'cancel_timer (request removal), list_timers (ask current timers or remaining time). '
        'seconds: total integer duration, zero unless creating. all: false except explicit cancel-all. '
        'text: short Spanish reply only for say, empty for tools. '
        'Negation, quoted commands, dates, missing duration or ambiguity: say, ask clarification. '
        'Actions are proposals, never claim execution. No Internet or device control beyond timers. '
        'Admit uncertainty. Examples:\n'
        '"Hola" -> {"action":"say","seconds":0,"all":false,"text":"Hola, soy Andrómeda."}\n'
        '"Recuérdame en tres minutos" -> {"action":"create_timer","seconds":180,"all":false,"text":""}\n'
        '"Borra todos los temporizadores" -> {"action":"cancel_timer","seconds":0,"all":true,"text":""}\n'
        '"Qué tiempo les queda" -> {"action":"list_timers","seconds":0,"all":false,"text":""}\n'
        '"No borres nada" -> {"action":"say","seconds":0,"all":false,"text":"No haré cambios."}'
    )
    return ("<|im_start|>system\n" + instruction + "\n<|im_end|>\n"
            "<|im_start|>user\n" + text + "\n<|im_end|>\n<|im_start|>assistant\n")


def validate_plan(plan):
    if not isinstance(plan, dict) or set(plan) != {"action", "seconds", "all", "text"}:
        raise ValueError("Invalid plan keys")
    if plan["action"] not in SCHEMA["properties"]["action"]["enum"]:
        raise ValueError("Unsupported action")
    if type(plan["seconds"]) is not int or not 0 <= plan["seconds"] <= 86400:
        raise ValueError("Invalid duration")
    if type(plan["all"]) is not bool or not isinstance(plan["text"], str) or len(plan["text"]) > 700:
        raise ValueError("Invalid plan values")
    if plan["action"] == "create_timer" and plan["seconds"] == 0:
        raise ValueError("Missing duration")
    if plan["action"] != "create_timer" and plan["seconds"] != 0:
        raise ValueError("Unexpected duration")
    if plan["action"] != "cancel_timer" and plan["all"]:
        raise ValueError("Unexpected selection")
    if plan["action"] == "say" and not plan["text"].strip():
        raise ValueError("Empty answer")
    return plan


class LocalPlanner:
    def __init__(self, key_path):
        self.key = Path(key_path).read_text().strip()
        if len(self.key) < 32:
            raise ValueError("Missing inference credential")
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def __call__(self, text):
        payload = {"prompt": prompt_for(text), "n_predict": 160, "stream": False,
                   "cache_prompt": True, "temperature": 0, "seed": 42,
                   "json_schema": SCHEMA}
        request = urllib.request.Request(
            "http://127.0.0.1:11435/completion", data=json.dumps(payload).encode(),
            headers={"Authorization": "Bearer " + self.key, "Content-Type": "application/json"})
        with self.opener.open(request, timeout=35) as response:
            raw = response.read(32769)
        if len(raw) > 32768:
            raise ValueError("Oversized inference response")
        result = json.loads(raw)
        if result.get("stopped_limit") or result.get("error"):
            raise ValueError("Incomplete inference")
        plan = json.loads(result["content"])
        # Harmless surplus parameters must never turn a reply/query into a tool.
        # Some small models fill `all` even for greetings; ignore it only for
        # non-mutating actions. Mutating proposals still pass strict validation.
        if isinstance(plan, dict) and plan.get("action") in {"say", "list_timers"}:
            plan["seconds"], plan["all"] = 0, False
        return validate_plan(plan)


class AITurns:
    def __init__(self, planner, clock=time.monotonic):
        self.planner, self.clock = planner, clock
        self.pending = None
        self.expires = 0

    def __call__(self, text):
        answer = re.sub(r"[^\w\s]", " ", normalized(text))
        answer = re.sub(r"^" + re.escape(normalized(SETTINGS["wake_word"])) + r"\s+", "", answer)
        answer = " ".join(answer.split())
        if self.pending is not None:
            pending, expires = self.pending, self.expires
            self.pending = None  # Never reuse a confirmation or a stale proposal.
            if answer in {"si", "si confirma", "si confirmo", "confirmo", "confirmar"}:
                if self.clock() <= expires:
                    return pending
                return {"type": "say", "text": "La propuesta venció. No cambié nada; pide la acción de nuevo."}
            if answer in {"no", "no gracias", "cancelar", "olvidalo"}:
                return {"type": "say", "text": "Propuesta descartada. No cambié nada."}
        elif answer in {"si", "confirmo", "confirmar"}:
            return {"type": "say", "text": "No hay una acción pendiente de confirmar."}
        try:
            plan = validate_plan(self.planner(text))
        except (OSError, ValueError, KeyError, TypeError):
            return {"type": "say", "text": "La IA local no produjo una respuesta válida. No cambié nada."}
        if plan["action"] == "say":
            return {"type": "say", "text": plan["text"]}
        if plan["action"] == "list_timers":
            return {"type": "list_timers"}
        if plan["action"] == "create_timer":
            self.pending = {"type": "timer", "seconds": plan["seconds"]}
            question = f"La IA propone un temporizador de {plan['seconds']} segundos."
        else:
            self.pending = {"type": "cancel_timer", "all": plan["all"]}
            question = "La IA propone cancelar " + ("todos los temporizadores." if plan["all"] else "un temporizador, solo si hay uno activo.")
        self.expires = self.clock() + 60
        return {"type": "say", "text": question + f" Di {SETTINGS['name']}, sí, para confirmar; o {SETTINGS['name']}, no, para descartarlo."}
