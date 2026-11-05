"""The frozen go rule and the parameter choice (F18 T018, D-073, D-075, research R12)."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_rules  # noqa: E402

RULES = visual_rules.load()


def git(root, *args):
    subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True)


class FrozenTest(unittest.TestCase):
    def repo(self, tmp):
        root = Path(tmp)
        git(root, "init", "-q")
        git(root, "config", "user.email", "test@example.invalid")
        git(root, "config", "user.name", "Test")
        rules = root / "visual_rules.json"
        testset = root / "questions.jsonl"
        testset.write_text('{"id": "v0001"}\n')
        rules.write_text(json.dumps({**RULES, "testset_sha256": None}))
        return root, rules, testset

    def commit(self, root):
        git(root, "add", "-A")
        git(root, "commit", "-q", "-m", "rules")

    def test_without_the_hash_the_ablations_refuse(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, rules, testset = self.repo(tmp)
            self.commit(root)
            with self.assertRaises(SystemExit):
                visual_rules.require_frozen(rules, testset)

    def test_frozen_and_committed_passes_and_gives_the_git_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, rules, testset = self.repo(tmp)
            visual_rules.freeze(rules, testset)
            self.commit(root)
            stamp = visual_rules.require_frozen(rules, testset)
            self.assertEqual(stamp["testset_sha256"], visual_rules.file_sha256(testset))
            self.assertEqual(len(stamp["rules_git_hash"]), 40)

    def test_an_edit_after_the_commit_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, rules, testset = self.repo(tmp)
            visual_rules.freeze(rules, testset)
            self.commit(root)
            data = json.loads(rules.read_text())
            data["go_rule"]["visual_gain_points"] = 10
            rules.write_text(json.dumps(data))
            with self.assertRaises(SystemExit):
                visual_rules.require_frozen(rules, testset)

    def test_a_changed_test_set_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root, rules, testset = self.repo(tmp)
            visual_rules.freeze(rules, testset)
            self.commit(root)
            testset.write_text('{"id": "v0002"}\n')
            with self.assertRaises(SystemExit):
                visual_rules.require_frozen(rules, testset)

    def test_freezing_twice_with_another_set_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            _, rules, testset = self.repo(tmp)
            visual_rules.freeze(rules, testset)
            visual_rules.freeze(rules, testset)  # same set: nothing changes
            testset.write_text('{"id": "v0003"}\n')
            with self.assertRaises(SystemExit):
                visual_rules.freeze(rules, testset)


class EndToEndTest(unittest.TestCase):
    def test_the_composed_figure(self):
        self.assertAlmostEqual(visual_rules.e2e_ms(RULES, ttft_p95_ms=7000, query_440_p95_ms=130, query_270_p95_ms=125),
                               206.5 + 53.0 + 5 + 7000)

    def test_a_faster_440m_does_not_lower_the_figure(self):
        self.assertAlmostEqual(visual_rules.e2e_ms(RULES, 7000, 120, 125), 206.5 + 53.0 + 7000)


class RetrievalChoiceTest(unittest.TestCase):
    mode = RULES["selection"]["retrieval"]["mode"]

    def rows(self, values, mode=None):
        return [{"mode": mode or self.mode, "px": px, "tokens": tokens, "dim": dim, "visual_recall@5": recall}
                for (px, tokens, dim), recall in values.items()]

    def test_the_choice_reads_the_page_list(self):
        self.assertEqual(self.mode, "visual")
        self.assertEqual(RULES["answer_pages"], "visual")

    def test_a_cheaper_configuration_close_to_the_best_wins(self):
        rows = self.rows({(1536, 140, 256): 0.80, (768, 70, 256): 0.79, (1024, 140, 256): 0.70,
                          (1536, 140, 512): 0.81, (768, 70, 512): 0.80, (1024, 140, 512): 0.71})
        self.assertEqual(visual_rules.choose_retrieval(rows, RULES), {"px": 768, "tokens": 70, "dim": 256})

    def test_512_only_with_its_minimum_gain(self):
        rows = self.rows({(768, 70, 256): 0.60, (768, 70, 512): 0.64})
        self.assertEqual(visual_rules.choose_retrieval(rows, RULES)["dim"], 512)
        rows = self.rows({(768, 70, 256): 0.60, (768, 70, 512): 0.62})
        self.assertEqual(visual_rules.choose_retrieval(rows, RULES)["dim"], 256)

    def test_other_modes_are_ignored(self):
        rows = self.rows({(768, 70, 256): 0.5}) + self.rows({(1536, 140, 256): 0.99}, mode="ambos")
        self.assertEqual(visual_rules.choose_retrieval(rows, RULES)["px"], 768)


class CandidatesTest(unittest.TestCase):
    answers = [
        {"variant": "mezcla-p1-b140-t0", "answer": "mezcla", "visual_correct": 60.0},
        {"variant": "mezcla-p2-b280-t0", "answer": "mezcla", "visual_correct": 70.0},
        {"variant": "mezcla-p3-b280-t1", "answer": "mezcla", "visual_correct": 75.0},
        {"variant": "mezcla-p1-b280-t0", "answer": "mezcla", "visual_correct": 66.0},
        {"variant": "paginas-p1-b280-t0", "answer": "paginas", "visual_correct": 90.0},
    ]

    def test_only_the_mix_and_only_what_fits_stage_a(self):
        stage_a = {"mezcla-p1-b140-t0": 5500, "mezcla-p2-b280-t0": 7600, "mezcla-p3-b280-t1": 11000,
                   "mezcla-p1-b280-t0": 6200}
        chosen = visual_rules.candidates(self.answers, stage_a, (130, 125), RULES)
        self.assertEqual(chosen, ["mezcla-p2-b280-t0", "mezcla-p1-b280-t0", "mezcla-p1-b140-t0"])

    def test_the_final_choice_needs_stage_b(self):
        stage_b = {"mezcla-p2-b280-t0": 7800, "mezcla-p1-b280-t0": 6500, "mezcla-p1-b140-t0": 5800}
        final = visual_rules.choose_final(self.answers, stage_b, (130, 125), RULES)
        self.assertEqual(final["variant"], "mezcla-p1-b280-t0")  # p2 does not fit 8 s once composed

    def test_close_quality_prefers_the_faster(self):
        answers = [{"variant": "a", "answer": "mezcla", "visual_correct": 70.0},
                   {"variant": "b", "answer": "mezcla", "visual_correct": 69.5}]
        final = visual_rules.choose_final(answers, {"a": 7000, "b": 5000}, (125, 125), RULES)
        self.assertEqual(final["variant"], "b")

    def test_nothing_fits(self):
        self.assertIsNone(visual_rules.choose_final(self.answers, {"mezcla-p1-b140-t0": 9000}, (125, 125), RULES))


class DecisionTest(unittest.TestCase):
    final = {"variant": "mezcla-p1-b280-t0", "e2e_ms": 6900.0}
    golden = {"golden_min": 0.995, "identity_min": 0.99999}

    def test_go_when_the_three_conditions_hold(self):
        decisive = {"visual": {"texto": 40.0, "ambos": 58.0}, "f05": {"texto": 80.0, "ambos": 79.0}}
        result = visual_rules.decide(RULES, self.final, decisive, self.golden)
        self.assertTrue(result["go"])
        self.assertFalse(result["needs_user"])

    def test_no_when_the_gain_is_short_or_text_loses(self):
        short = {"visual": {"texto": 40.0, "ambos": 54.0}, "f05": {"texto": 80.0, "ambos": 80.0}}
        self.assertFalse(visual_rules.decide(RULES, self.final, short, self.golden)["go"])
        loses = {"visual": {"texto": 40.0, "ambos": 60.0}, "f05": {"texto": 80.0, "ambos": 77.5}}
        self.assertFalse(visual_rules.decide(RULES, self.final, loses, self.golden)["go"])

    def test_the_user_decides_when_no_page_fits_or_the_440m_fails(self):
        decisive = {"visual": {"texto": 40.0, "ambos": 60.0}, "f05": {"texto": 80.0, "ambos": 80.0}}
        self.assertTrue(visual_rules.decide(RULES, None, decisive, self.golden)["needs_user"])
        bad = {"golden_min": 0.995, "identity_min": 0.998}
        self.assertTrue(visual_rules.decide(RULES, self.final, decisive, bad)["needs_user"])

    def test_no_decisive_figures_when_nothing_fits(self):
        result = visual_rules.decide(RULES, None, None, self.golden)
        self.assertFalse(result["go"])
        self.assertTrue(result["needs_user"])
        self.assertIsNone(result["conditions"]["visual_gain"]["points"])


class PhoneInputsTest(unittest.TestCase):
    report = {"scenarios": [
        {"kind": "GEMMA_PAGES", "status": "COMPLETED", "params": {"stage": "A", "combination": "mezcla-p1-b280-t0"}, "metrics": {"p95Ms": 6100.0}},
        {"kind": "GEMMA_PAGES", "status": "FAILED", "params": {"stage": "A", "combination": "mezcla-p3-b280-t1"}, "metrics": {}},
        {"kind": "GEMMA_PAGES", "status": "COMPLETED", "params": {"stage": "B", "combination": "mezcla-p1-b280-t0"}, "metrics": {"p95Ms": 6400.0}},
        {"kind": "EMBED_TEXT", "model": "EG2_440M", "status": "COMPLETED", "params": {}, "metrics": {"p95Ms": 130.0}},
        {"kind": "EMBED_TEXT", "model": "EG2_270M", "status": "COMPLETED", "params": {}, "metrics": {"p95Ms": 125.0}},
    ]}

    def test_stages_take_only_completed_scenarios(self):
        self.assertEqual(visual_rules.phone_stage(self.report, "A"), {"mezcla-p1-b280-t0": 6100.0})
        self.assertEqual(visual_rules.phone_stage(self.report, "B"), {"mezcla-p1-b280-t0": 6400.0})
        self.assertEqual(visual_rules.phone_queries(self.report), (130.0, 125.0))

    def test_queries_need_both_models(self):
        with self.assertRaises(SystemExit):
            visual_rules.phone_queries({"scenarios": self.report["scenarios"][:4]})

    def test_golden_takes_the_lowest_backend(self):
        data = {"golden": {"CPU": {"min": 0.997, "mean": 0.999}, "GPU": {"min": 0.993, "mean": 0.998}},
                "identity": {"min": 0.99995, "mean": 0.99999}}
        golden = visual_rules.golden_from(data)
        self.assertEqual((golden["golden_min"], golden["identity_min"]), (0.993, 0.99995))

    def test_desktop_rows_are_complete_runs_in_points(self):
        metrics = {"texto": {"complete": True, "summary": {"visual": {"correct": 0.4}}},
                   "mezcla-p1-b280-t0": {"complete": True, "summary": {"visual": {"correct": 0.6}}},
                   "mezcla-p2-b280-t0": {"complete": False, "summary": {"visual": {"correct": 0.7}}}}
        rows = visual_rules.desktop_answers(metrics)
        self.assertEqual([(r["variant"], r["answer"], r["visual_correct"]) for r in rows],
                         [("texto", "texto", 40.0), ("mezcla-p1-b280-t0", "mezcla", 60.0)])


if __name__ == "__main__":
    unittest.main()
