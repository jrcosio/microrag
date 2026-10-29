"""EvalConfig identity (F05 T005, FR-019)."""

import dataclasses
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.runs import EvalConfig  # noqa: E402

FINGERPRINTS = {"index": "i" * 64, "testset": "t" * 64, "models": "m" * 64}


class EvalConfigTest(unittest.TestCase):
    def test_fingerprint_is_stable(self):
        self.assertEqual(EvalConfig().fingerprint(**FINGERPRINTS), EvalConfig().fingerprint(**FINGERPRINTS))

    def test_fingerprint_changes_with_any_parameter_or_input(self):
        base = EvalConfig().fingerprint(**FINGERPRINTS)
        changes = {
            "build": "chunk250", "mode": "vector", "follow_up_rule": False, "candidates": 40, "rrf_k": 30,
            "max_blocks": 4, "neighbors": False, "max_per_document": 2, "budget_tokens": 1500,
            "prompt_limit_tokens": 1800, "llm": "E2B", "max_output_tokens": 256, "prompt_template": "v2",
            "third_list": "trigram", "rerank_alpha": 0.5, "rerank_top": 10,
        }
        self.assertEqual(set(changes), {f.name for f in dataclasses.fields(EvalConfig)})
        for name, value in changes.items():
            self.assertNotEqual(dataclasses.replace(EvalConfig(), **{name: value}).fingerprint(**FINGERPRINTS), base, name)
        for name in FINGERPRINTS:
            self.assertNotEqual(EvalConfig().fingerprint(**{**FINGERPRINTS, name: "x" * 64}), base, name)

    def test_late_fields_at_default_keep_old_fingerprints(self):
        self.assertNotIn("prompt_template", EvalConfig().to_json())
        self.assertIn("prompt_template", EvalConfig(prompt_template="v2").to_json())
        # F10 D-035: the order review options leave the D-034 runs where they were.
        d034 = EvalConfig(max_blocks=4, neighbors=False, budget_tokens=1719)
        self.assertEqual(json.loads(d034.to_json()), {
            "build": "ref", "budget_tokens": 1719, "candidates": 50, "follow_up_rule": True, "llm": None,
            "max_blocks": 4, "max_output_tokens": 512, "max_per_document": 3, "mode": "hybrid", "neighbors": False,
            "prompt_limit_tokens": 2000, "rrf_k": 60,
        })
        for name, value in {"third_list": "trigram", "rerank_alpha": 0.5, "rerank_top": 10}.items():
            self.assertIn(name, dataclasses.replace(d034, **{name: value}).to_json())

    def test_json_is_canonical(self):
        text = EvalConfig().to_json()
        self.assertEqual(text, json.dumps(json.loads(text), sort_keys=True, separators=(",", ":")))

    def test_unknown_values_are_rejected(self):
        with self.assertRaises(ValueError):
            EvalConfig(mode="bm25")
        with self.assertRaises(ValueError):
            EvalConfig(llm="E8B")
        with self.assertRaises(ValueError):
            EvalConfig(third_list="ngram")


if __name__ == "__main__":
    unittest.main()
