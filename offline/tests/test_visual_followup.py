"""F14 page follow-ups (research R10): the variants on a tiny index, and the rule that picks one."""

import sys
import unittest
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_followup  # noqa: E402


@dataclass(frozen=True)
class Row:
    doc_id: str
    page: int
    vector: tuple


# Two documents; the words of a text decide its vector, as a toy embedding.
ROWS = [Row("ipc", 3, (1.0, 0.0, 0.0)), Row("epa", 3, (0.0, 1.0, 0.0)), Row("ipc", 4, (0.0, 0.0, 1.0))]
WORDS = {"precios": (1.0, 0.0, 0.0), "paro": (0.0, 0.8, 0.0), "galicia": (0.0, 0.0, 0.6)}


def embed(text):
    v = [0.0, 0.0, 0.0]
    for word, w in WORDS.items():
        if word in text.lower():
            v = [a + b for a, b in zip(v, w)]
    norm = sum(x * x for x in v) ** 0.5 or 1.0
    return [x / norm for x in v]


SWITCH = {"id": "f1", "kind": "switch", "first": "¿Cuánto subieron los precios en septiembre?", "second": "¿Y el paro?",
          "first_page": {"doc": "ipc", "page": 3}, "expected_page": {"doc": "epa", "page": 3}}
FOLLOW = {"id": "f2", "kind": "follow", "first": "¿Cuánto subieron los precios en septiembre?", "second": "¿Y en Galicia?",
          "first_page": {"doc": "ipc", "page": 3}, "expected_page": {"doc": "ipc", "page": 3}}
RULE = {"min_switch_hit": 0.9, "max_follow_drop_points": 5}


class VisualFollowUpTest(unittest.TestCase):

    def test_the_joined_vector_drags_the_previous_page_and_the_new_question_finds_the_other(self):
        results = visual_followup.evaluate([SWITCH, FOLLOW], ROWS, embed)

        self.assertEqual(results["joined"]["pairs"][0]["page"], ["ipc", 3])
        self.assertEqual(results["new-only"]["pairs"][0]["page"], ["epa", 3])
        self.assertEqual(results["best-of-both"]["pairs"][0]["page"], ["epa", 3])

    def test_the_new_question_alone_loses_a_true_follow_up(self):
        results = visual_followup.evaluate([SWITCH, FOLLOW], ROWS, embed)

        self.assertTrue(results["joined"]["pairs"][1]["hit"])
        self.assertFalse(results["new-only"]["pairs"][1]["hit"])

    def test_the_rule_keeps_the_variants_that_switch_without_losing_follow_ups(self):
        results = {"joined": {"switch_hit": 0.5, "follow_hit": 0.95}, "new-only": {"switch_hit": 0.95, "follow_hit": 0.80},
                   "best-of-both": {"switch_hit": 0.92, "follow_hit": 0.91}}
        passing, winner = visual_followup.decide(results, RULE)

        self.assertEqual(passing, ["best-of-both"])
        self.assertEqual(winner, "best-of-both")

    def test_without_a_passing_variant_there_is_no_winner(self):
        results = {"joined": {"switch_hit": 0.5, "follow_hit": 1.0}, "new-only": {"switch_hit": 1.0, "follow_hit": 0.5},
                   "best-of-both": {"switch_hit": 0.8, "follow_hit": 1.0}}
        self.assertEqual(visual_followup.decide(results, RULE), ([], None))

    def test_also_pages_count_as_expected(self):
        pair = {**SWITCH, "also_pages": [{"doc": "ipc", "page": 3}]}
        self.assertIn(("ipc", 3), visual_followup.expected(pair))


if __name__ == "__main__":
    unittest.main()
