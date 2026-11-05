"""The device set and the phone ↔ desktop parity (F18 T035, research R13)."""

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import visual_parity  # noqa: E402


class ManifestTest(unittest.TestCase):
    def test_every_file_is_listed_with_its_hash_kind_and_role(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            writer = visual_parity.DeviceSetWriter(root)
            writer.add("images/a.jpg", b"aaa", "image")
            writer.add("pdfs/t.pdf", b"ppp", "pdf", role="text", pages=[1, 2])
            writer.add("images/a.jpg", b"aaa", "image")  # the same file twice is kept once
            manifest = writer.finish(parity_px=1024, tokens=140)
            files = json.loads((root / "manifest.json").read_text())["files"]
            self.assertEqual(manifest["parity_px"], 1024)
            self.assertEqual(manifest["tokens"], 140)
            self.assertEqual([f["path"] for f in files], ["images/a.jpg", "pdfs/t.pdf"])
            self.assertEqual(files[0]["sha256"], hashlib.sha256(b"aaa").hexdigest())
            self.assertEqual(files[1]["role"], "text")
            self.assertEqual(files[1]["pages"], [1, 2])
            self.assertEqual((root / "pdfs/t.pdf").read_bytes(), b"ppp")

    def test_parts_reference_device_set_files(self):
        from eval.vision_runtime import Image, Text

        with tempfile.TemporaryDirectory() as tmp:
            writer = visual_parity.DeviceSetWriter(Path(tmp))
            raw = writer.parts([Text("[1] Doc, página 2\n"), Image(b"img"), Text("PREGUNTA: ¿Algo?")])
            self.assertEqual(raw[0], {"text": "[1] Doc, página 2\n"})
            self.assertTrue(raw[1]["image"].startswith("ctx/") and raw[1]["image"].endswith(".jpg"))
            self.assertEqual(raw[2], {"text": "PREGUNTA: ¿Algo?"})


class ParityTest(unittest.TestCase):
    def test_cosines_for_the_same_bytes_and_for_rendered_pages(self):
        desktop = {"images": {"sha-a": {"70": [1.0, 0.0, 0.0], "140": [0.0, 1.0, 0.0]}},
                   "pages": {"pdf:pdfs/t.pdf:1:1024": {"70": [1.0, 0.0, 0.0], "140": [1.0, 0.0, 0.0]}}}
        phone = [
            {"kind": "PARITY", "params": {"tokensPerImage": 70}, "vectors": [
                {"id": "images/a.jpg", "sha256": "sha-a", "tokensPerImage": 70, "values": [0.8, 0.6, 0.0]},
                {"id": "pdf:pdfs/t.pdf:1:1024", "sha256": None, "tokensPerImage": 70, "values": [0.0, 1.0, 0.0]}]},
        ]
        result = visual_parity.compare(desktop, phone, threshold=0.99)
        self.assertEqual(result["same_bytes"]["n"], 1)
        self.assertLess(result["same_bytes"]["min"], 0.99)
        self.assertEqual(result["same_bytes"]["below"], ["images/a.jpg@70"])
        self.assertAlmostEqual(result["rendered_pages"]["min"], 0.0)

    def test_reading_is_judged_like_the_desktop(self):
        reading = [{"id": "r01", "key_facts": ["12,5"], "type": "tabla"}]
        phone = [{"kind": "READING", "answers": [{"id": "r01", "text": "Es 12,5 %.", "firstTokenMs": 900, "prefillTokens": 300}]}]
        desktop = {"r01": "No encuentro esa información en los documentos disponibles."}
        result = visual_parity.compare_reading(reading, phone, desktop)
        self.assertEqual(result["phone_correct"], 1)
        self.assertEqual(result["desktop_correct"], 0)
        self.assertEqual(result["same_verdict"], 0)


if __name__ == "__main__":
    unittest.main()
