"""F14 phone reports as the tables of benchmarks.md (research R4, contract H2)."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import device_bench  # noqa: E402

REPORT = {
    "bench": "search-scale", "started": "2026-11-07T10:00:00Z", "device": "Nothing A063", "android": 35,
    "runtimes": {"litertlm": "0.19.1", "sqliteVector": "1.2.0"},
    "models": [{"id": "EG2_440M", "backend": "CPU"}],
    "conditions": {"start": {"thermal": "NONE", "battery": 80, "charging": False},
                   "end": {"thermal": "LIGHT", "battery": 76, "charging": False, "rssPeakMb": 812}},
    "variants": [{"name": "int8-rerank", "parameters": {"bank": "10000"},
                  "summary": {"vector": {"p50": 4.25, "p95": 6.5, "p99": 9.0, "n": 740.0},
                              "hybrid": {"p50": 150.0, "p95": 1180.5, "p99": 1300.0, "n": 740.0}},
                  "extra": {"recallAt20": 0.981}}],
    "status": "completed",
}


class DeviceBenchTest(unittest.TestCase):

    def test_a_report_becomes_a_table_with_its_conditions(self):
        table = device_bench.table(REPORT)

        self.assertIn("### search-scale · 2026-11-07 10:00 UTC", table)
        self.assertIn("LiteRT-LM 0.19.1 · SQLite-Vector 1.2.0 · EG2_440M (CPU, por defecto)", table)
        self.assertIn("Térmico NONE → LIGHT · batería 80 → 76 % · sin cargar · pico de RSS 812 MB · estado **completed**", table)
        self.assertIn("| int8-rerank (bank=10000) | vector | 4,2 | 6,5 | 9,0 | 740 | recallAt20=0.981 |", table)
        self.assertIn("| int8-rerank (bank=10000) | hybrid | 150,0 | 1.180,5 | 1.300,0 | 740 |  |", table)

    def test_the_generated_part_is_replaced_and_the_rest_kept(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "device"
            folder.mkdir()
            (folder / "f14-search-scale-1.json").write_text(json.dumps(REPORT), encoding="utf-8")
            benchmarks = Path(tmp) / "benchmarks.md"
            benchmarks.write_text(f"# Cifras\n\nTexto a mano.\n\n{device_bench.START}\nviejo\n{device_bench.END}\n\nFinal.\n")

            device_bench.write(folder, benchmarks)
            text = benchmarks.read_text(encoding="utf-8")

        self.assertIn("Texto a mano.", text)
        self.assertIn("Final.", text)
        self.assertNotIn("viejo", text)
        self.assertIn("| int8-rerank (bank=10000) | vector |", text)

    def test_the_flat_reports_of_f19_and_f20_become_a_table_of_figures(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder / "f14-search-scale-1.json").write_text(json.dumps(REPORT), encoding="utf-8")
            # 11:00 UTC for the chat and 09:00 for the ingest: they sort around the bench of 10:00.
            (folder / "visual-chat-1794049200000.json").write_text(
                json.dumps({"model": "A063", "sdk": 35, "backend": "GPU", "balanced_ttft_p95_ms": 9583, "rss_peak_mb": 4678}),
                encoding="utf-8")
            (folder / "f19-visual-ingest-1794042000000.json").write_text(
                json.dumps({"pages": 200, "pages_per_minute": 13.985642635253466}), encoding="utf-8")

            reports = device_bench.load(folder)
            text = device_bench.render(reports)

        self.assertEqual(["f19-visual-ingest", "search-scale", "visual-chat"], [r["bench"] for r in reports])
        self.assertIn("### visual-chat · 2026-11-07 11:00 UTC", text)
        self.assertIn("| Medida | Valor |", text)
        self.assertIn("| balanced_ttft_p95_ms | 9583 |", text)
        self.assertIn("| backend | GPU |", text)
        self.assertIn("| pages_per_minute | 14.0 |", text)
        self.assertNotIn("Variante | Fase", text.split("### visual-chat")[1])


if __name__ == "__main__":
    unittest.main()
