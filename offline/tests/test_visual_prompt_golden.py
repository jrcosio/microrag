"""The v5-visual golden (F20 T003, research R4): up to date, and shaped as VisualPromptGoldenTest reads it."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gen_visual_prompt_golden  # noqa: E402
from eval import visual_prompt  # noqa: E402

FIELDS = {"id", "question", "previous", "blocks", "page", "parts"}


class VisualPromptGoldenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = [json.loads(line) for line in gen_visual_prompt_golden.GOLDEN.read_text(encoding="utf-8").splitlines() if line]

    def test_the_file_is_what_the_generator_writes(self):
        self.assertEqual(gen_visual_prompt_golden.GOLDEN.read_text(encoding="utf-8"), gen_visual_prompt_golden.render())

    def test_fields_and_one_image_per_case(self):
        self.assertEqual(len(self.rows), 12)
        for row in self.rows:
            self.assertEqual(set(row), FIELDS, row["id"])
            images = [p for p in row["parts"] if "image" in p]
            self.assertEqual(images, [{"image": row["page"]["image"]}], row["id"])

    def test_the_page_rule_and_the_page_number_follow_the_blocks(self):
        for row in self.rows:
            text = "".join(p.get("text", "") for p in row["parts"])
            self.assertIn(visual_prompt.PAGE_RULE, text, row["id"])
            self.assertIn(f"[{len(row['blocks']) + 1}] {gen_visual_prompt_golden.page_label(row['page'])}\n", text, row["id"])
            self.assertTrue(text.endswith("PREGUNTA: " + row["question"].strip()), row["id"])


if __name__ == "__main__":
    unittest.main()
