"""Evidence coverage of the phone's PDF extraction (F06 T068)."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import builds, corpus  # noqa: E402
from eval.device_extraction import coverage  # noqa: E402


class DeviceExtractionTest(unittest.TestCase):
    def test_a_device_text_equal_to_pypdf_keeps_every_evidence(self):
        doc = next(d for d in corpus.load_manifest() if d.format == "pdf")
        lines = [json.dumps({"file": doc.file_name, "page": b.page, "block": b.text}, ensure_ascii=False)
                 for b in builds.extracted(doc)]
        lines.append(json.dumps({"file": doc.file_name, "page": 9999, "block": None}))
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "device.jsonl"
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
            rows, total = coverage(path)
        self.assertEqual([r["doc"] for r in rows], [doc.id])
        self.assertEqual(rows[0]["device"], rows[0]["pypdf"])
        self.assertEqual(rows[0]["device_chars"], rows[0]["pypdf_chars"])
        self.assertGreater(total["evidences"], 0)

    def test_a_device_without_text_finds_nothing(self):
        doc = next(d for d in corpus.load_manifest() if d.format == "pdf")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "device.jsonl"
            path.write_text(json.dumps({"file": doc.file_name, "page": 1, "block": None}) + "\n", encoding="utf-8")
            rows, _ = coverage(path)
        self.assertEqual(rows[0]["device"], 0)


if __name__ == "__main__":
    unittest.main()
