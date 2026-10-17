"""Read-only vocabulary inspection, without microphone or recognition."""
import json
import vosk

vosk.SetLogLevel(-1)
model = vosk.Model("/opt/doha-voice/models/vosk-model-small-es-0.42")
print(json.dumps({word: model.vosk_model_find_word(word) for word in
                  ["andromeda", "andrómeda", "doja", "doha", "oye", "roja", "hoja"]}))
