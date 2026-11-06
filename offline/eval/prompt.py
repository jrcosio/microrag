"""PromptBuilder of the report (§Plantilla de prompt), F05 research R6. F11 uses the same template."""

import math

REFUSAL = "No encuentro esa información en los documentos disponibles."
PREVIOUS_QUESTION_CHARS = 300
PREVIOUS_ANSWER_CHARS = 500
TEMPLATES = ("informe", "v2", "v3", "v4", "v4-cifras")
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
# F14 (research R8, D-088, D-090): v4 with a line just before the PREGUNTA, against the figures fp16 deforms on a GPU.
# The rules stay v4's, so its prompt starts as v4's and shares its prefilled prefix.
CLOSING = {"v4-cifras": "Antes de responder: copia cada cifra, fecha, importe y porcentaje dígito a dígito, tal como aparece en la fuente."}
EXTRA_RULES = {"informe": [], "v2": V2_RULES, "v3": V3_RULES, "v4": V4_RULES, "v4-cifras": V4_RULES}
# Answer language (F12, D-058, research R5): «Automático» changes only the language line, with no detector.
LANGUAGES = {"es": "español", "auto": "el idioma de la PREGUNTA"}


def build_prompt(question, blocks, previous, template="informe", language="es"):
    """[previous] is the (question, answer) of the previous turn for a follow-up, or None."""
    if template not in TEMPLATES:
        raise ValueError(f"Unknown prompt template {template}")
    if language not in LANGUAGES:
        raise ValueError(f"Unknown answer language {language}")
    lines = [
        "Responde a la PREGUNTA usando únicamente las FUENTES.",
        "",
        "Reglas:",
        f"- Responde en {LANGUAGES[language]}, de forma clara y breve.",
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
    if template in CLOSING:
        lines.append(CLOSING[template])
    return "\n".join(lines) + "\nPREGUNTA: " + question.strip()


def estimated_tokens(prompt, chars_per_token):
    """⌈code points / chars_per_token⌉, as PromptBuilder.estimatedTokens of the app (F11 research R10)."""
    return math.ceil(len(prompt) / chars_per_token)


def within_limit(question, blocks, ranks, previous, template, limit, chars_per_token, estimate=None):
    """The app's prompt limit (F11 rule A6, DefaultChatSession.prompt): while the prompt is over [limit]
    estimated tokens, the worst ranked block goes first, then the previous turn; the last block stays.

    [ranks] holds each block's best fused rank, and the first of two equal ranks is the one dropped.
    Returns the prompt with the blocks and previous turn it kept. F14: [estimate] (a function of the prompt) replaces
    ⌈code points / chars_per_token⌉ with the app's estimate by classes (token_estimate, T036).
    """
    count = estimate or (lambda text: estimated_tokens(text, chars_per_token))
    blocks, ranks = list(blocks), list(ranks)
    prompt = build_prompt(question, blocks, previous, template)
    while count(prompt) > limit:
        if len(blocks) > 1:
            worst = ranks.index(max(ranks))
            del blocks[worst], ranks[worst]
        elif previous is not None:
            previous = None
        else:
            break
        prompt = build_prompt(question, blocks, previous, template)
    return prompt, blocks, previous
