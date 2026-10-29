"""PromptBuilder of the report, to the character (F05 T034, research R6)."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from eval.prompt import REFUSAL, build_prompt  # noqa: E402
from eval.selector import ContextBlock  # noqa: E402

RULES = (
    "Responde a la PREGUNTA usando únicamente las FUENTES.\n"
    "\n"
    "Reglas:\n"
    "- Responde en español, de forma clara y breve.\n"
    "- Después de cada afirmación, indica la fuente entre corchetes, por ejemplo [2].\n"
    "- Si las fuentes no contienen la respuesta, responde exactamente:\n"
    "  \"No encuentro esa información en los documentos disponibles.\"\n"
    "- No uses conocimiento propio ni inventes datos, cifras o plazos.\n"
    "\n"
)


class PromptTest(unittest.TestCase):
    def test_template_with_numbered_sources(self):
        blocks = [
            ContextBlock("Manual de garantías", "Devoluciones › Plazos", 12, 1, 3, 3, " El plazo es de 30 días. ", 10),
            ContextBlock("Condiciones generales", None, None, 2, 0, 1, "Texto de empresas.", 5),
        ]
        self.assertEqual(
            build_prompt(" ¿Cuánto tiempo tengo? ", blocks, None),
            RULES + "FUENTES:\n"
            "[1] Manual de garantías › Devoluciones › Plazos (p. 12)\nEl plazo es de 30 días.\n\n"
            "[2] Condiciones generales\nTexto de empresas.\n\n"
            "PREGUNTA: ¿Cuánto tiempo tengo?",
        )

    def test_previous_turn_is_truncated(self):
        prompt = build_prompt("¿Y para empresas?", [], ("p" * 400, "r" * 600))
        self.assertIn("CONVERSACIÓN PREVIA (solo como contexto de la pregunta):\n"
                      f"Usuario: {'p' * 300}\nAsistente: {'r' * 500}\n\nFUENTES:\n", prompt)

    def test_v2_adds_its_rules_after_the_report_rules(self):
        prompt = build_prompt("¿Plazo?", [], None, "v2")
        self.assertTrue(prompt.startswith(RULES[:-1]))
        self.assertIn("- Cita solo los números de las FUENTES, nunca números de artículos o apartados.\n- Si hay conversación previa, interpreta la PREGUNTA como continuación de ella.\n\nFUENTES:", prompt)

    def test_v3_adds_complete_and_exact_answers_after_the_report_rules(self):
        prompt = build_prompt("¿Plazo?", [], None, "v3")
        self.assertTrue(prompt.startswith(RULES[:-1]))
        self.assertIn(
            "- No uses conocimiento propio ni inventes datos, cifras o plazos.\n"
            "- Si la PREGUNTA pide varias cosas, responde a todas las que estén en las FUENTES.\n"
            "- Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.\n"
            "\nFUENTES:", prompt)

    def test_v4_adds_the_previous_conversation_rule_after_v3(self):
        v3 = build_prompt("¿Y para empresas?", [], ("¿Plazo?", "30 días [1]."), "v3")
        v4 = build_prompt("¿Y para empresas?", [], ("¿Plazo?", "30 días [1]."), "v4")
        rule = ("- Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa: búscala en las FUENTES con ese contexto "
                "antes de responder con la frase anterior.\n")
        self.assertEqual(v4, v3.replace("citas.\n\n", "citas.\n" + rule + "\n", 1))

    def test_the_variants_keep_the_rest_of_the_report_template(self):
        blocks = [ContextBlock("Manual", "Plazos", 3, 1, 0, 0, "Texto.", 2)]
        report = build_prompt("¿Plazo?", blocks, ("p", "r"))
        for template in ("v3", "v4"):
            variant = build_prompt("¿Plazo?", blocks, ("p", "r"), template)
            self.assertTrue(variant.endswith(report[report.index("\n\nCONVERSACIÓN PREVIA"):]))

    def test_refusal_sentence(self):
        self.assertEqual(REFUSAL, "No encuentro esa información en los documentos disponibles.")


if __name__ == "__main__":
    unittest.main()
