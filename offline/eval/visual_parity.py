"""The device set and the phone ↔ desktop parity of F18 (research R13, data-model §1.6).

The device set carries the exact bytes the phone embeds and reads, with the desktop's vectors and answers for
them; `visual-parity` compares the phone's report against them: the same bytes must give a cosine ≥ 0.99 (the
golden of images), pages rendered on each side are compared too, and the reading answers are judged by F05's rules.
"""

import hashlib
import json
import math
from pathlib import Path

from eval.judge import has_key_facts, is_refusal
from eval.vision_runtime import Image, Text


class DeviceSetWriter:
    def __init__(self, root):
        self.root = Path(root)
        self.files = {}

    def add(self, path, data, kind, role=None, pages=None):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        entry = {"path": path, "sha256": hashlib.sha256(data).hexdigest(), "kind": kind}
        if role:
            entry["role"] = role
        if pages:
            entry["pages"] = list(pages)
        self.files[path] = entry
        return entry

    def parts(self, parts):
        """Raw parts for reading.json and contexts.json: texts inline, images as device-set files."""
        raw = []
        for part in parts:
            if isinstance(part, Text):
                raw.append({"text": part.text})
            else:
                name = f"ctx/{hashlib.sha256(part.data).hexdigest()[:16]}.jpg"
                if name not in self.files:
                    self.add(name, part.data, "context-image")
                raw.append({"image": name})
        return raw

    def finish(self, parity_px, tokens):
        manifest = {"version": 1, "parity_px": parity_px, "tokens": tokens, "files": list(self.files.values())}
        (self.root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
        return manifest


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


def compare(desktop, phone_scenarios, threshold=0.99):
    """Cosines between the phone's PARITY vectors and the desktop's: same bytes, and pages rendered on each side."""
    same, rendered, below = [], [], []
    for scenario in phone_scenarios:
        if scenario.get("kind") != "PARITY":
            continue
        for vector in scenario.get("vectors") or []:
            tokens = str(vector["tokensPerImage"])
            if vector["id"].startswith("pdf:"):
                reference = desktop["pages"].get(vector["id"], {}).get(tokens)
                if reference is not None:
                    rendered.append(cosine(vector["values"], reference))
                continue
            reference = desktop["images"].get(vector["sha256"], {}).get(tokens)
            if reference is None:
                continue
            value = cosine(vector["values"], reference)
            same.append(value)
            if value < threshold:
                below.append(f"{vector['id']}@{tokens}")

    def stats(values):
        return {"n": len(values), "min": min(values) if values else None, "mean": sum(values) / len(values) if values else None}

    return {"same_bytes": {**stats(same), "below": below, "threshold": threshold}, "rendered_pages": stats(rendered)}


def compare_reading(reading, phone_scenarios, desktop_answers):
    """The phone's and the desktop's answers to the reading pages, judged by key facts (F05's rule)."""
    phone = {a["id"]: a["text"] for s in phone_scenarios if s.get("kind") == "READING" for a in (s.get("answers") or [])}

    def verdict(answer, item):
        return answer is not None and not is_refusal(answer) and has_key_facts(answer, item["key_facts"])

    rows = [{"id": item["id"], "phone": verdict(phone.get(item["id"]), item), "desktop": verdict(desktop_answers.get(item["id"]), item)}
            for item in reading]
    return {"n": len(rows), "phone_correct": sum(r["phone"] for r in rows), "desktop_correct": sum(r["desktop"] for r in rows),
            "same_verdict": sum(r["phone"] == r["desktop"] for r in rows), "rows": rows}
