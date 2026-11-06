"""F14 figures detector (research R8): the deformations fp16 produced on the Mac and on the phone are caught."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval import numeric  # noqa: E402

SOURCE = "En 2020 se recaudaron 9.453 millones; la tasa fue del 36,4 % y el plazo de 30 días (artículo 12)."


class NumericTest(unittest.TestCase):

    def test_the_same_figures_with_their_separators_are_not_deformed(self):
        answer = "Se recaudaron 9.453 millones en 2020, con una tasa del 36,4 % [1]."
        self.assertEqual(numeric.deformed(answer, [SOURCE]), [])

    def test_a_year_turned_into_20000_is_caught(self):
        self.assertEqual(numeric.deformed("En 20000 se recaudaron 9.453 millones [1].", [SOURCE]), ["20000"])

    def test_a_decimal_with_an_extra_digit_is_caught(self):
        self.assertEqual(numeric.deformed("La tasa fue del 366,4 % [2].", [SOURCE]), ["366.4"])

    def test_citation_markers_and_figures_of_the_question_are_not_figures_of_the_answer(self):
        answer = "El plazo del artículo 12 es de 30 días [1, 3] para 2021."
        self.assertEqual(numeric.deformed(answer, [SOURCE], question="¿Y en 2021?"), [])

    def test_normalization_reads_spanish_and_plain_forms_alike(self):
        self.assertEqual(numeric.normalize("9.453"), "9453")
        self.assertEqual(numeric.normalize("1.234.567,50"), "1234567.5")
        self.assertEqual(numeric.normalize("36,40"), "36.4")
        self.assertEqual(numeric.normalize("0,5"), "0.5")
        self.assertEqual(numeric.figures("9453 y 9.453"), {"9453"})


if __name__ == "__main__":
    unittest.main()
