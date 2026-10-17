from pathlib import Path
import sentencepiece as spm

base = Path("/opt/doha-voice/models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01")
processor = spm.SentencePieceProcessor(model_file=str(base / "bpe.model"))
lines = [" ".join(processor.encode(word, out_type=str)) + " @DOJA" for word in ("DOJA", "DOHA")]
(base / "keywords-doja.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
print("\n".join(lines))

phonemes = Path("/opt/doha-voice/models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20")
if phonemes.exists():
    # DOHA is taken from this model's supplied English phone lexicon.
    # The second is a candidate for the English 'Doja' pronunciation, not Spanish training.
    candidates = ["D OW1 HH AA2 @DOJA", "D OW1 HH AA0 @DOJA", "D OW1 JH AH0 @DOJA",
                  "D OW1 Y AH0 @DOJA", "D AO1 HH AA0 @DOJA"]
    vocabulary = {line.rsplit(" ", 1)[0] for line in (phonemes / "tokens.txt").read_text().splitlines()}
    for candidate in candidates:
        assert set(candidate.split()[:-1]) <= vocabulary
    (phonemes / "keywords-doja.txt").write_text("\n".join(candidates) + "\n")
