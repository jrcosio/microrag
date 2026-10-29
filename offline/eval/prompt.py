"""PromptBuilder of the report (§Plantilla de prompt), F05 research R6. F11 uses the same template."""

REFUSAL = "No encuentro esa información en los documentos disponibles."
PREVIOUS_QUESTION_CHARS = 300
PREVIOUS_ANSWER_CHARS = 500
TEMPLATES = ("informe", "v2", "v3", "v4")
# FR-022 lever: against unjustified refusals and article numbers taken as citations (manual judgement of F05).
V2_RULES = [
    "- Si alguna fuente contiene la respuesta, aunque use otras palabras, respóndela; usa la frase anterior solo si ninguna fuente la contiene.",
    "- Cita solo los números de las FUENTES, nunca números de artículos o apartados.",
    "- Si hay conversación previa, interpreta la PREGUNTA como continuación de ella.",
]
# F11 template review (D-035, D-053; research R4): fixed before running, never tuned on the results.
# v3 targets incomplete answers and neighbouring figures; v4 adds the follow-ups refused with the source in context.
V3_RULES = [
    "- Si la PREGUNTA pide varias cosas, responde a todas las que estén en las FUENTES.",
    "- Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.",
]
V4_RULES = V3_RULES + [
    "- Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa: búscala en las FUENTES con ese contexto antes de responder "
    "con la frase anterior.",
]
EXTRA_RULES = {"informe": [], "v2": V2_RULES, "v3": V3_RULES, "v4": V4_RULES}


def build_prompt(question, blocks, previous, template="informe"):
    """[previous] is the (question, answer) of the previous turn for a follow-up, or None."""
    if template not in TEMPLATES:
        raise ValueError(f"Unknown prompt template {template}")
    lines = [
        "Responde a la PREGUNTA usando únicamente las FUENTES.",
        "",
        "Reglas:",
        "- Responde en español, de forma clara y breve.",
        "- Después de cada afirmación, indica la fuente entre corchetes, por ejemplo [2].",
        "- Si las fuentes no contienen la respuesta, responde exactamente:",
        f'  "{REFUSAL}"',
        "- No uses conocimiento propio ni inventes datos, cifras o plazos.",
        *EXTRA_RULES[template],
        "",
    ]
    if previous is not None:
        lines += [
            "CONVERSACIÓN PREVIA (solo como contexto de la pregunta):",
            f"Usuario: {previous[0][:PREVIOUS_QUESTION_CHARS]}",
            f"Asistente: {previous[1][:PREVIOUS_ANSWER_CHARS]}",
            "",
        ]
    lines.append("FUENTES:")
    for number, block in enumerate(blocks, start=1):
        where = " › ".join(x for x in (block.document_name, block.heading_path) if x is not None)
        page = f" (p. {block.page})" if block.page is not None else ""
        lines += [f"[{number}] {where}{page}", block.text.strip(), ""]
    return "\n".join(lines) + "\nPREGUNTA: " + question.strip()
