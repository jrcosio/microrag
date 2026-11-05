"""Desktop vision runtime: Gemma's visual budget through ctypes and interleaved contents (F18 T016, research R1)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import vision_runtime  # noqa: E402


class FakeFunction:
    def __init__(self):
        self.calls, self.argtypes, self.restype = [], None, "unset"

    def __call__(self, *args):
        self.calls.append(args)


class FakeLib:
    def __init__(self):
        self.litert_lm_conversation_optional_args_set_visual_token_budget = FakeFunction()


class FakeConversation:
    """Stands in for litert_lm.Conversation: only the private hook the runtime wraps."""

    def __init__(self):
        self._lib = FakeLib()

    def _create_optional_args(self, max_output_tokens=None):
        return 1234 if max_output_tokens is not None else None


class BudgetHookTest(unittest.TestCase):
    def setUp(self):
        vision_runtime.install_budget_hook(FakeConversation)

    def test_the_budget_is_set_only_inside_its_block(self):
        conversation = FakeConversation()
        setter = conversation._lib.litert_lm_conversation_optional_args_set_visual_token_budget
        conversation._create_optional_args(max_output_tokens=64)
        with vision_runtime.visual_budget(140):
            self.assertEqual(conversation._create_optional_args(max_output_tokens=64), 1234)
        conversation._create_optional_args(max_output_tokens=64)
        self.assertEqual(setter.calls, [(1234, 140)])
        self.assertIsNone(setter.restype)
        self.assertEqual(len(setter.argtypes), 2)

    def test_no_call_without_optional_args(self):
        conversation = FakeConversation()
        with vision_runtime.visual_budget(70):
            self.assertIsNone(conversation._create_optional_args())
        self.assertEqual(conversation._lib.litert_lm_conversation_optional_args_set_visual_token_budget.calls, [])

    def test_installing_twice_wraps_once(self):
        vision_runtime.install_budget_hook(FakeConversation)
        conversation = FakeConversation()
        with vision_runtime.visual_budget(280):
            conversation._create_optional_args(max_output_tokens=8)
        self.assertEqual(len(conversation._lib.litert_lm_conversation_optional_args_set_visual_token_budget.calls), 1)

    def test_a_budget_the_file_does_not_offer_is_refused(self):
        with self.assertRaises(ValueError):
            with vision_runtime.visual_budget(560):
                pass


class ContentsTest(unittest.TestCase):
    def test_parts_keep_their_order(self):
        parts = [vision_runtime.Text("[1] Doc, página 2"), vision_runtime.Image(b"\x89PNG"), vision_runtime.Text("¿Pregunta?")]
        built = vision_runtime.contents(parts)
        self.assertEqual([type(item).__name__ for item in built], ["Text", "ImageBytes", "Text"])

    def test_image_count(self):
        parts = [vision_runtime.Image(b"a"), vision_runtime.Text("x"), vision_runtime.Image(b"b")]
        self.assertEqual(vision_runtime.image_count(parts), 2)


if __name__ == "__main__":
    unittest.main()
