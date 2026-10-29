"""Evaluation configurations and where their results live (F05 data-model §5–6, FR-019)."""

import dataclasses
import hashlib
import json
from pathlib import Path

OFFLINE = Path(__file__).resolve().parents[1]
RUNS_DIR = OFFLINE / "build" / "runs"

MODES = ("vector", "fts", "hybrid")
LLM_CHOICES = (None, "E2B", "E4B")
THIRD_LISTS = (None, "trigram")
LATE_FIELDS = {"prompt_template": "informe", "third_list": None, "rerank_alpha": None, "rerank_top": 20}


@dataclasses.dataclass(frozen=True)
class EvalConfig:
    """One evaluated variant. The defaults are the reference configuration of the spec."""

    build: str = "ref"
    mode: str = "hybrid"
    follow_up_rule: bool = True
    candidates: int = 50
    rrf_k: int = 60
    max_blocks: int = 6
    neighbors: bool = True
    max_per_document: int = 3
    # Context budget of the selector; None derives it so the whole prompt fits prompt_limit_tokens (research R6).
    budget_tokens: int | None = None
    prompt_limit_tokens: int = 2000
    llm: str | None = None
    max_output_tokens: int = 512
    prompt_template: str = "informe"
    # F10 order review (D-035): a third RRF list, and a rerank of the first rerank_top fused ids.
    third_list: str | None = None
    rerank_alpha: float | None = None
    rerank_top: int = 20

    def __post_init__(self):
        if self.mode not in MODES:
            raise ValueError(f"Unknown retrieval mode {self.mode}")
        if self.llm not in LLM_CHOICES:
            raise ValueError(f"Unknown LLM {self.llm}")
        if self.third_list not in THIRD_LISTS:
            raise ValueError(f"Unknown third list {self.third_list}")

    def to_json(self):
        values = dataclasses.asdict(self)
        # Fields added after the first runs stay out of the JSON at their default, so those runs keep their fingerprint.
        for name, default in LATE_FIELDS.items():
            if values[name] == default:
                del values[name]
        return json.dumps(values, sort_keys=True, separators=(",", ":"))

    def fingerprint(self, index, testset, models):
        """SHA-256 of the configuration plus the fingerprints of its index, test set and models."""
        text = json.dumps(
            {"config": json.loads(self.to_json()), "index": index, "testset": testset, "models": models},
            sort_keys=True, separators=(",", ":"),
        )
        return hashlib.sha256(text.encode("utf-8")).hexdigest()


def run_dir(fingerprint):
    return RUNS_DIR / fingerprint[:16]
