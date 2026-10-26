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

    def test_refusal_sentence(self):
        self.assertEqual(REFUSAL, "No encuentro esa información en los documentos disponibles.")


if __name__ == "__main__":
    unittest.main()
