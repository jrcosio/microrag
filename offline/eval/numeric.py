"""F14 figures (research R8, contract H4): the figures of an answer that are not in its sources nor its question.

fp16 activations on a GPU can turn «2020» into «20000» or «36,4» into «366,4» in contexts full of numbers (D-077).
The detector reads figures with Spanish separators (9.453 and 36,4) and with plain digits, normalizes them to one
form, and flags an answer whose figures do not all appear in what Gemma was given. Citation markers such as [2] are
not figures. A flagged answer is reviewed by hand (D-033); the detector only finds candidates.
"""

import re

CITATION = re.compile(r"\[\d+(?:\s*[,–-]\s*\d+)*\]")
# A thousands-grouped number (9.453 or 1.234.567, optionally with decimals), or plain digits with optional decimals.
FIGURE = re.compile(r"(?<![\d.,])(\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?)(?![\d])")


def normalize(figure):
    """One form for a figure: thousands dots out, the decimal comma as a point, no trailing decimal zeros."""
    text = figure
    if re.fullmatch(r"\d{1,3}(?:\.\d{3})+(?:,\d+)?", text):
        text = text.replace(".", "").replace(",", ".")
    else:
        text = text.replace(",", ".")
    if "." in text:
        whole, _, decimals = text.partition(".")
        decimals = decimals.rstrip("0")
        text = whole.lstrip("0") or "0"
        if decimals:
            text += "." + decimals
    else:
        text = text.lstrip("0") or "0"
    return text


def figures(text):
    """The normalized figures of [text], without citation markers."""
    return {normalize(m.group(1)) for m in FIGURE.finditer(CITATION.sub(" ", text))}


def deformed(answer, sources, question=""):
    """The figures of [answer] that are neither in [sources] (texts) nor in [question], sorted."""
    given = set()
    for source in list(sources) + [question]:
        given |= figures(source)
    return sorted(figures(answer) - given)


def load(path=None):
    """The reviewed questions of testset/numeric (data-model §5)."""
    import json
    from pathlib import Path

    path = path or Path(__file__).resolve().parents[1] / "testset" / "numeric" / "questions.jsonl"
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def review(template, fp32, out_dir):
    """Generates the answer of every question with [template] on the Mac's GPU, fp16 by default or FP32, and the
    figures each answer deforms. Resumable: answers already in [out_dir] are kept."""
    import json
    from pathlib import Path
    from types import SimpleNamespace

    from litert_lm import ActivationDataType, Backend, Engine, SamplerConfig

    from eval import builds, models
    from eval.generate import GREEDY, answer_text
    from eval.prompt import build_prompt

    out = Path(out_dir) / f"{template}-{'fp32' if fp32 else 'default'}.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    done = {json.loads(l)["id"]: json.loads(l) for l in out.read_text(encoding="utf-8").splitlines() if l.strip()} if out.exists() else {}
    questions = load()
    pending = [q for q in questions if q["id"] not in done]
    if pending:
        engine = Engine(str(models.verify(models.LLMS["E2B"])), backend=Backend.GPU(), max_num_tokens=4096,
                        cache_dir=str(builds.BUILD / "litert-cache"),
                        activation_data_type=ActivationDataType.FLOAT32 if fp32 else None)
        try:
            with out.open("a", encoding="utf-8") as f:
                for q in pending:
                    blocks = [SimpleNamespace(**b) for b in q["blocks"]]
                    prompt = build_prompt(q["question"], blocks, None, template)
                    conversation = engine.create_conversation(sampler_config=SamplerConfig(**GREEDY), max_output_tokens=256)
                    try:
                        answer = answer_text(conversation.send_message(prompt))
                    finally:
                        conversation.close()
                    record = {"id": q["id"], "answer": answer,
                              "deformed": deformed(answer, [b["text"] for b in q["blocks"]], q["question"])}
                    f.write(json.dumps(record, ensure_ascii=False) + "\n")
                    done[q["id"]] = record
        finally:
            engine.close()
    flagged = [r for r in done.values() if r["deformed"]]
    return {"template": template, "precision": "fp32" if fp32 else "default", "answers": len(done),
            "flagged": len(flagged), "flagged_ids": sorted(r["id"] for r in flagged)}


def device_review(report_path):
    """The figures each phone answer deforms, from a NumericFidelityBenchmarkTest report (f14-numeric-*.json)."""
    import json
    from pathlib import Path

    report = json.loads(Path(report_path).read_text(encoding="utf-8"))
    variant = report["variants"][0]
    by_id = {q["id"]: q for q in load()}
    rows = []
    for item in variant["extra"]["answers"]:
        q = by_id[item["id"]]
        rows.append({"id": item["id"], "answer": item["answer"],
                     "deformed": deformed(item["answer"], [b["text"] for b in q["blocks"]], q["question"])})
    flagged = [r for r in rows if r["deformed"]]
    return {"template": variant["parameters"]["template"], "precision": variant["parameters"]["precision"], "device": report["device"],
            "answers": len(rows), "flagged": len(flagged), "flagged_ids": sorted(r["id"] for r in flagged), "rows": rows}
