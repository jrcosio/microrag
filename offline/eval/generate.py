"""Answer generation with Gemma 4 on the Mac GPU (F05 research R7, contracts/eval-cli.md E6–E7).

One conversation per question with the app's deterministic sampler. Answers are appended to
answers.jsonl as they come, so an interrupted run resumes where it stopped.
"""

import json
import time
from dataclasses import dataclass

GREEDY = {"top_k": 1, "top_p": 1.0, "temperature": 0.0, "seed": 0}
MAX_NUM_TOKENS = 4096


@dataclass(frozen=True)
class Item:
    id: str
    prompt: str


def answer_text(message):
    """Text of a LiteRT-LM reply, whatever its shape (Message, dict or str)."""
    if isinstance(message, str):
        return message
    contents = getattr(message, "contents", None)
    if contents is not None and not isinstance(message, dict):
        return str(contents)
    content = message.get("content", "") if isinstance(message, dict) else str(message)
    if isinstance(content, list):
        return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return str(content)


def open_engine(model_path, backend="gpu", cache_dir=None):
    from litert_lm import Backend, Engine

    chosen = Backend.GPU() if backend == "gpu" else Backend.CPU()
    return Engine(str(model_path), backend=chosen, max_num_tokens=MAX_NUM_TOKENS,
                  cache_dir=str(cache_dir) if cache_dir else None)


def done_ids(path):
    if not path.exists():
        return set()
    return {json.loads(line)["id"] for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}


def run(engine, items, out_path, max_output_tokens, on_answer=None):
    """Generates the answers not yet in [out_path]; returns how many were generated."""
    from litert_lm import SamplerConfig

    out_path.parent.mkdir(parents=True, exist_ok=True)
    seen = done_ids(out_path)
    generated = 0
    with out_path.open("a", encoding="utf-8") as out:
        for item in items:
            if item.id in seen:
                continue
            started = time.time()
            conversation = engine.create_conversation(sampler_config=SamplerConfig(**GREEDY), max_output_tokens=max_output_tokens)
            try:
                answer = answer_text(conversation.send_message(item.prompt))
            finally:
                conversation.close()
            record = {"id": item.id, "prompt_tokens": len(engine.tokenize(item.prompt)), "answer": answer,
                      "seconds": round(time.time() - started, 3)}
            if on_answer is not None:
                record.update(on_answer(item, answer))
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            generated += 1
    return generated
