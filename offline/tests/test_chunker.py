"""chunker.py is a literal port of the report's Kotlin Chunker (F05 T010, research R4)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.chunker import Chunker, chunk_params, kt_trim, utf16_len  # noqa: E402
from eval.extract import RawBlock  # noqa: E402


def block(text, path=(), page=None):
    return RawBlock(heading_path=tuple(path), text=text, page=page)


class ChunkerTest(unittest.TestCase):
    def test_heading_change_opens_a_new_chunk(self):
        chunks = Chunker(target_chars=1000, max_chars=2000).chunk(
            [block("Uno.", ["A"]), block("Dos.", ["A", "B"]), block("Tres.", ["A", "B"])]
        )
        self.assertEqual([(c.ordinal, c.heading_path, c.content) for c in chunks],
                         [(0, "A", "Uno."), (1, "A › B", "Dos.\n\nTres.")])

    def test_target_splits_between_paragraphs_with_overlap_of_short_last_paragraph(self):
        p1, p2, p3, p4 = "a" * 30, "b" * 30, "c" * 30, "d" * 30
        chunks = Chunker(target_chars=100, max_chars=200).chunk([block(f"{p1}\n\n{p2}\n\n{p3}\n\n{p4}")])
        # p3 (30) is shorter than target/3 (33), so it opens the next chunk too.
        self.assertEqual([c.content for c in chunks], [f"{p1}\n\n{p2}\n\n{p3}", f"{p3}\n\n{p4}"])

    def test_long_last_paragraph_is_not_carried(self):
        p1, p2 = "a" * 40, "b" * 70
        chunks = Chunker(target_chars=100, max_chars=200).chunk([block(f"{p1}\n\n{p2}\n\nfin")])
        # p1 (40) is not shorter than target/3 (33), so the next chunk starts clean.
        self.assertEqual([c.content for c in chunks], [f"{p1}", f"{p2}\n\nfin"])

    def test_paragraph_longer_than_max_is_split_by_sentence(self):
        sentences = ["Frase número %d con algo de texto." % i for i in range(10)]
        chunks = Chunker(target_chars=50, max_chars=80).chunk([block(" ".join(sentences))])
        self.assertTrue(all(utf16_len(c.content) <= 80 for c in chunks))
        self.assertEqual(" ".join(c.content for c in chunks).split(), " ".join(sentences).split())

    def test_page_is_the_one_where_the_chunk_starts(self):
        chunks = Chunker(target_chars=60, max_chars=200).chunk(
            [block("a" * 50, page=1), block("b" * 50, page=2), block("c" * 50, page=3)]
        )
        self.assertEqual([c.page for c in chunks], [1, 2, 3])

    def test_ordinals_are_consecutive_and_output_is_deterministic(self):
        blocks = [block("Párrafo %d. " % i * 20, ["Cap %d" % (i // 3)]) for i in range(12)]
        first = Chunker(target_chars=300, max_chars=600).chunk(blocks)
        self.assertEqual([c.ordinal for c in first], list(range(len(first))))
        self.assertEqual(first, Chunker(target_chars=300, max_chars=600).chunk(blocks))

    def test_kotlin_trim_and_lengths(self):
        self.assertEqual(kt_trim("  hola \t\n"), "hola")  # NBSP is whitespace for Kotlin
        self.assertEqual(kt_trim("\u0085hola"), "\u0085hola")   # NEL is not
        self.assertEqual(utf16_len("a\U0001d41a"), 3)

    def test_chunk_params(self):
        target, maximum = chunk_params(tokens=350, chars_per_token=4.5, max_input_chars=2100)
        self.assertEqual(target, 1575)
        self.assertEqual(maximum, 2100)  # round(1575 * 10 / 7) = 2250, capped by the model input
        self.assertEqual(chunk_params(tokens=250, chars_per_token=4.0, max_input_chars=5000), (1000, 1429))


if __name__ == "__main__":
    unittest.main()
