"""F14 benchmark banks (research R2, data-model §3): deterministic, inside the chunk size, nested, verifiable."""

import gzip
import json
import math
import random
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import bench_bank  # noqa: E402

SOURCES = [
    ("Ley A", ["Título I", "Título II"], [f"El artículo {n} regula el plazo de {n * 3} días hábiles para recurrir." for n in range(40)]),
    ("Guía B", [], [f"La sección {n} explica cómo revisar el motor cada {n + 10} horas de uso continuado." for n in range(30)]),
]
QUESTIONS = [("q1", "¿Cuál es el plazo para recurrir?", None), ("q2", "¿Y en el título II?", "¿Cuál es el plazo para recurrir?")]


def fake_embed(text):
    rng = random.Random(text)
    return [rng.uniform(-1, 1) for _ in range(768)]


def read_bank(out):
    with gzip.open(out / "chunks.jsonl.gz", "rt", encoding="utf-8") as f:
        chunks = [json.loads(line) for line in f]
    raw = (out / "vectors.f32").read_bytes()
    vectors = [struct.unpack_from("<256f", raw, i * 1024) for i in range(len(raw) // 1024)]
    return chunks, vectors


class BenchBankTest(unittest.TestCase):

    def build(self, n, folder):
        return bench_bank.build_bank(n, SOURCES, fake_embed, QUESTIONS, folder, "fake", "0" * 64)

    def test_the_same_seed_gives_the_same_bank(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.build(100, a)
            self.build(100, b)
            self.assertEqual(json.loads((Path(a) / "manifest.json").read_text())["files"],
                             json.loads((Path(b) / "manifest.json").read_text())["files"])

    def test_chunks_stay_within_the_chunk_size_and_vectors_are_unit(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = self.build(100, tmp)
            chunks, vectors = read_bank(Path(tmp))
        self.assertEqual(manifest["documents"], 4)
        self.assertEqual(len(chunks), 100)
        self.assertEqual(len(vectors), 100)
        self.assertTrue(all(len(c["text"]) <= bench_bank.MAX_CHARS for c in chunks))
        self.assertGreater(sum(len(c["text"]) for c in chunks) / len(chunks), 1000)
        self.assertTrue(all(abs(math.sqrt(sum(x * x for x in v)) - 1) < 1e-5 for v in vectors))

    def test_a_small_bank_is_the_start_of_a_bigger_one(self):
        with tempfile.TemporaryDirectory() as small, tempfile.TemporaryDirectory() as big:
            self.build(50, small)
            self.build(100, big)
            small_chunks, small_vectors = read_bank(Path(small))
            big_chunks, big_vectors = read_bank(Path(big))
        self.assertEqual(small_chunks, big_chunks[:50])
        self.assertEqual(small_vectors, big_vectors[:50])

    def test_folders_and_exclusions_are_spread(self):
        docs = [bench_bank.document(i, SOURCES) for i in range(1000)]
        excluded = sum(d["excluded"] for d in docs) / len(docs)
        self.assertAlmostEqual(excluded, bench_bank.EXCLUDED_FRACTION, delta=0.05)
        self.assertEqual({d["folder"] for d in docs}, set(range(bench_bank.FOLDERS)))

    def test_verify_finds_a_changed_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.build(50, tmp)
            bench_bank.verify(tmp)
            (Path(tmp) / "queries.f32").write_bytes(b"changed")
            with self.assertRaises(ValueError):
                bench_bank.verify(tmp)

    def test_the_bank_keeps_the_questions_and_their_follow_ups(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.build(25, tmp)
            lines = [json.loads(line) for line in (Path(tmp) / "questions.jsonl").read_text().splitlines()]
            self.assertEqual(len((Path(tmp) / "queries.f32").read_bytes()), 2 * 1024)
        self.assertEqual(lines[1]["previous"], "¿Cuál es el plazo para recurrir?")

    def test_page_banks_keep_the_real_pages_and_perturb_the_rest(self):
        real = [[1.0] + [0.0] * 255, [0.0, 1.0] + [0.0] * 254]
        with tempfile.TemporaryDirectory() as tmp:
            bench_bank.build_pages(5, real, tmp, sigma=0.01)
            raw = (Path(tmp) / "vectors.f32").read_bytes()
        vectors = [struct.unpack_from("<256f", raw, i * 1024) for i in range(5)]
        self.assertEqual(list(vectors[0]), real[0])
        self.assertGreater(sum(a * b for a, b in zip(vectors[2], real[0])), 0.9)
        self.assertNotEqual(list(vectors[2]), real[0])


if __name__ == "__main__":
    unittest.main()
