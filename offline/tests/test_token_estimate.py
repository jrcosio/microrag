"""F14 token estimate (research R9): today's estimate as a special case, the fit, and the cases shared with Kotlin."""

import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import token_estimate  # noqa: E402

# The same texts and expectations as app/src/test/.../rag/answer/TokenEstimateTest.kt.
CASES = [
    ("El plazo es de 30 días.", (15, 2, 6)),
    ("Ñandú, pingüino y ñu: 1.234,56 €.", (16, 6, 11)),
    ("2020 2021 2022", (0, 12, 2)),
    ("", (0, 0, 0)),
]


class TokenEstimateTest(unittest.TestCase):

    def test_classes_count_code_points_as_kotlin(self):
        for text, expected in CASES:
            self.assertEqual(token_estimate.classes(text), expected, text)

    def test_with_one_ratio_it_is_todays_estimate(self):
        for text, _ in CASES:
            self.assertEqual(token_estimate.estimate(text, 4.554, 4.554, 4.554), math.ceil(len(text) / 4.554), text)

    def test_the_fit_recovers_known_costs(self):
        samples = [("a" * n + "1" * d + " " * r, n / 5 + d / 1.5 + r / 3) for n, d, r in [(100, 0, 20), (50, 40, 10), (10, 80, 30), (200, 5, 60)]]
        a, b, c = token_estimate.fit(samples)
        self.assertAlmostEqual(a, 5, places=3)
        self.assertAlmostEqual(b, 1.5, places=3)
        self.assertAlmostEqual(c, 3, places=3)

    def test_safe_lowers_the_digit_and_rest_ratios_until_the_p95_is_covered(self):
        samples = [("1" * 100, 90), ("1" * 50, 45), ("1,2" * 30, 80)]
        a, b, c = token_estimate.safe(samples, 5.0, 2.0, 3.0)
        ratios = [t / token_estimate.estimate(x, a, b, c) for x, t in samples]
        self.assertLessEqual(max(ratios), 1.0)
        self.assertEqual(a, 5.0)


if __name__ == "__main__":
    unittest.main()
