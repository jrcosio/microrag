"""F14 search between languages (research R12, D-062, D-088): a screen before generating and judging.

The lexical search of a document in another language finds next to nothing, and the variant that normalizes it by
the document's language only helps when question and document share it. What can bring a question to a document in
another language is the search by meaning, so the most weight it can get, the vector search alone, bounds what any
reweighting of the fusion can do. If even that bound leaves the search well short of the same-language one, the rule
of 10 points (D-062) cannot be met, and no answer is generated nor judged.
"""

import dataclasses
import json

from eval import builds
from eval import crosslang as cl

RESULT = builds.BUILD / "f14" / "crosslang-screen.json"
SAME_LANGUAGE = {"ES·EN": None, "EN·ES": "EN·EN"}


def run(pipeline):
    base = dataclasses.replace(pipeline._active_template_base()[0], llm=None)
    out = {}
    for combo in cl.COMBINATIONS:
        path = cl.set_path(combo.set_name)
        ctx = pipeline.load_context(combo.build)
        figures = {}
        for mode in ("hybrid", "vector"):
            config = dataclasses.replace(base, build=combo.build, mode=mode)
            r = pipeline.retrieve(config, ctx, questions_path=path)
            figures[mode] = {k: round(r[k], 3) for k in ("blocks_recall", "hybrid_recall@20", "vector_recall@20") if k in r}
        out[combo.key] = figures
    # Spanish questions on Spanish documents: the reference of ES·EN's search (its questions translate these).
    ref = pipeline.retrieve(dataclasses.replace(base, mode="hybrid"))
    out["ES·ES"] = {"hybrid": {k: round(ref[k], 3) for k in ("blocks_recall", "hybrid_recall@20", "vector_recall@20")}}
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RESULT.write_text(json.dumps(out, ensure_ascii=False, indent=1))
    return out
