"""Generation run with a fake engine: one conversation per question, deterministic sampler, resume (F05 T035)."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import generate  # noqa: E402


class FakeConversation:
    def __init__(self, engine, sampler, max_output_tokens):
        self.engine, self.sampler, self.max_output_tokens = engine, sampler, max_output_tokens

    def send_message(self, prompt):
        self.engine.prompts.append(prompt)
        return {"role": "model", "content": f"respuesta {len(self.engine.prompts)}"}

    def close(self):
        self.engine.closed += 1


class FakeEngine:
    def __init__(self):
        self.prompts, self.conversations, self.closed = [], [], 0

    def tokenize(self, text):
        return text.split()

    def create_conversation(self, sampler_config=None, max_output_tokens=None):
        conversation = FakeConversation(self, sampler_config, max_output_tokens)
        self.conversations.append(conversation)
        return conversation


class GenerateTest(unittest.TestCase):
    def test_one_conversation_per_question_and_resume(self):
        engine = FakeEngine()
        items = [generate.Item("q1", "uno dos"), generate.Item("q2", "tres")]
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp, "answers.jsonl")
            first = generate.run(engine, items[:1], out, max_output_tokens=512)
            again = generate.run(engine, items, out, max_output_tokens=512)
            lines = [json.loads(line) for line in out.read_text().splitlines()]
        self.assertEqual((first, again), (1, 1))
        self.assertEqual([line["id"] for line in lines], ["q1", "q2"])
        self.assertEqual(lines[0]["prompt_tokens"], 2)
        self.assertEqual(lines[1]["answer"], "respuesta 2")
        self.assertEqual(len(engine.conversations), 2)
        self.assertEqual(engine.closed, 2)
        sampler = engine.conversations[0].sampler
        self.assertEqual((sampler.top_k, sampler.top_p, sampler.temperature, sampler.seed), (1, 1.0, 0.0, 0))
        self.assertEqual(engine.conversations[0].max_output_tokens, 512)

    def test_answer_text_from_message_shapes(self):
        self.assertEqual(generate.answer_text({"content": "hola"}), "hola")
        self.assertEqual(generate.answer_text({"content": [{"type": "text", "text": "ho"}, {"type": "text", "text": "la"}]}), "hola")
        self.assertEqual(generate.answer_text("hola"), "hola")


if __name__ == "__main__":
    unittest.main()
