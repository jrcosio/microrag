"""PromptBuilder of the report (§Plantilla de prompt), F05 research R6. F11 uses the same template."""

REFUSAL = "No encuentro esa información en los documentos disponibles."
PREVIOUS_QUESTION_CHARS = 300
PREVIOUS_ANSWER_CHARS = 500


def build_prompt(question, blocks, previous):
    """[previous] is the (question, answer) of the previous turn for a follow-up, or None."""
    lines = [
        "Responde a la PREGUNTA usando únicamente las FUENTES.",
        "",
        "Reglas:",
        "- Responde en español, de forma clara y breve.",
        "- Después de cada afirmación, indica la fuente entre corchetes, por ejemplo [2].",
        "- Si las fuentes no contienen la respuesta, responde exactamente:",
        f'  "{REFUSAL}"',
        "- No uses conocimiento propio ni inventes datos, cifras o plazos.",
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
