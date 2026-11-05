"""The v5-visual template, the mix and its duplicate rule, and the judge of page citations (F18 T029, research R11)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_judge, visual_prompt  # noqa: E402
from eval.prompt import REFUSAL  # noqa: E402
from eval.vision_runtime import Image, Text  # noqa: E402


def text_source(label, text, pages):
    return visual_prompt.Source("text", label, frozenset(pages), text=text)


def page_source(label, pages, images):
    return visual_prompt.Source("page", label, frozenset(pages), images=tuple(images))


class PartsTest(unittest.TestCase):
    def test_sources_are_numbered_and_each_label_comes_before_its_image(self):
        sources = [text_source("Nota del IPC (p. 1)", "El IPC sube un 2,9 %.", [("a", 1)]),
                   page_source("Nota del IPC, página 2", [("a", 2)], [b"top", b"bottom"])]
        parts = visual_prompt.build_parts("¿Cuánto sube el IPC?", sources)
        kinds = [type(p).__name__ for p in parts]
        self.assertEqual(kinds, ["Text", "Image", "Image", "Text"])
        self.assertIn("[1] Nota del IPC (p. 1)\nEl IPC sube un 2,9 %.", parts[0].text)
        self.assertTrue(parts[0].text.rstrip().endswith("[2] Nota del IPC, página 2"))
        self.assertTrue(parts[-1].text.strip().endswith("PREGUNTA: ¿Cuánto sube el IPC?"))
        self.assertIn(REFUSAL, parts[0].text)
        self.assertIn(visual_prompt.PAGE_RULE, parts[0].text)

    def test_text_only_has_no_image_rule_difference_in_citing(self):
        parts = visual_prompt.build_parts("¿Pregunta?", [text_source("Doc", "texto", [("a", 1)])])
        self.assertEqual(len(parts), 1)
        self.assertIn("indica la fuente entre corchetes", parts[0].text)

    def test_token_accounting(self):
        sources = [text_source("Doc", "x" * 455, [("a", 1)]), page_source("Doc, página 2", [("a", 2)], [b"i"])]
        parts = visual_prompt.build_parts("¿Pregunta?", sources)
        tokens = visual_prompt.tokens(parts, budget=280, chars_per_token=4.554)
        self.assertEqual(tokens["visual"], 280)
        self.assertGreater(tokens["text"], 100)
        self.assertEqual(tokens["total"], tokens["text"] + 280)


class DuplicateRuleTest(unittest.TestCase):
    def test_chunks_on_a_chosen_page_leave_the_text(self):
        ranked = [10, 11, 12, 13]
        chunk_pages = {10: [1], 11: [2, 3], 12: [5], 13: [1]}
        chunk_doc = {10: "a", 11: "a", 12: "a", 13: "b"}
        kept = visual_prompt.without_pages(ranked, chunk_pages, chunk_doc, {("a", 3)})
        self.assertEqual(kept, [10, 12, 13])


class JudgeTest(unittest.TestCase):
    sources = [text_source("Doc", "t", [("a", 1), ("a", 2)]), page_source("Doc, página 4", [("a", 4)], [b"i"])]

    def test_a_correct_answer_citing_the_relevant_page(self):
        j = visual_judge.judge("tabla", "La tasa es 12,5 % [2].", ["12,5"], self.sources, {("a", 4)})
        self.assertTrue(j.correct)
        self.assertEqual((j.citations_ok, j.citations_total), (1, 1))

    def test_a_text_block_covering_the_relevant_page_counts(self):
        j = visual_judge.judge("control", "Es 12,5 [1].", ["12,5"], self.sources, {("a", 2)})
        self.assertEqual(j.citations_ok, 1)

    def test_a_citation_out_of_range_is_wrong(self):
        j = visual_judge.judge("tabla", "Es 12,5 [7].", ["12,5"], self.sources, {("a", 4)})
        self.assertEqual((j.citations_ok, j.citations_total), (0, 1))

    def test_refusals(self):
        self.assertTrue(visual_judge.judge("sin_respuesta", REFUSAL, [], self.sources, set()).correct)
        self.assertFalse(visual_judge.judge("tabla", REFUSAL, ["12,5"], self.sources, {("a", 4)}).correct)

    def test_summary_groups_the_visual_types(self):
        judgements = [visual_judge.judge("tabla", "12,5 [2]", ["12,5"], self.sources, {("a", 4)}),
                      visual_judge.judge("grafico", "nada [2]", ["8"], self.sources, {("a", 4)}),
                      visual_judge.judge("control", "12,5 [1]", ["12,5"], self.sources, {("a", 1)}),
                      visual_judge.judge("sin_respuesta", REFUSAL, [], self.sources, set())]
        summary = visual_judge.summarize(["tabla", "grafico", "control", "sin_respuesta"], judgements)
        self.assertEqual(summary["visual"]["correct"], 0.5)
        self.assertEqual(summary["control"]["correct"], 1.0)
        self.assertEqual(summary["sin_respuesta"]["refusal_correct"], 1.0)


if __name__ == "__main__":
    unittest.main()
