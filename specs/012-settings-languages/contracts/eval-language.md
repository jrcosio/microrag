# Contrato: evaluación de «Automático» y medida entre idiomas (escritorio, F12)

**Herramienta:** `offline/`, con Python 3.12 en `offline/.venv` y siempre `python -I`. La referencia es la de F05 y F11:
- plantilla en `eval/prompt.py`;
- juicio en `eval/judge.py`;
- generación en `eval/generate.py`;
- el patrón de `limit-review`.

Sin dependencias nuevas. Research R5–R9.

## 1. Comandos nuevos

```
offline/.venv/bin/python -I offline/run_eval.py language-review
offline/.venv/bin/python -I offline/run_eval.py fetch-corpus corpus=en [--record]
offline/.venv/bin/python -I offline/run_eval.py sources corpus=en
offline/.venv/bin/python -I offline/run_eval.py build en
offline/.venv/bin/python -I offline/run_eval.py validate-testset set=crosslang
offline/.venv/bin/python -I offline/run_eval.py crosslang-review english_answers=auto|es
offline/.venv/bin/python -I offline/run_eval.py report
```

`report` gana la sección «Idiomas (F12)». El README añade una fila por comando.

## 2. Reglas

**Lo que no cambia**
- **L1.** No cambian:
  - `testset/questions.jsonl`;
  - el corpus español y su manifiesto;
  - `build/index/ref.db`;
  - `golden/answer_golden.jsonl`;
  - `golden/retrieval_parity.jsonl`.

  Las huellas de las ejecuciones de `informe` (`06ef519f`) y `v4` (`a8c1902c`) siguen siendo las mismas, y una prueba lo comprueba.
- **L2.** `EvalConfig.answer_language` (`"es"` o `"auto"`, por defecto `"es"`) está en `LATE_FIELDS`. Con `"es"` no entra en el JSON de la huella.
- **L3.** `build_prompt(…, language="es")` da exactamente el texto de hoy. Las pruebas de `test_prompt.py` siguen igual, y hay un caso nuevo para `"auto"`, cuya única diferencia es la línea «- Responde en el idioma de la PREGUNTA, de forma clara y breve.». `PromptBuilderTest` tiene los mismos casos literales.
- **L4.** `judge.REFUSAL_EN = "I can't find that information in the available documents."` y `is_refusal` acepta las dos frases, con la misma `norm`. `test_judge.py` y `AnswerReadingTest` tienen los mismos casos, incluidos los que no deben contar: la frase seguida de más texto y otras redacciones.

**`language-review`: «Automático» con las preguntas en español**
- **L5. Generación y revisión:**
  - se generan las 426 preguntas con la configuración elegida, `v4` y `answer_language="auto"`;
  - los bloques tienen que ser idénticos a los de `v4` (G8); si no, se para;
  - las respuestas que cambian van a `reports/language-review.csv` (columnas en research R7);
  - las que no cambian heredan el juicio de `v4` (las filas mezcladas de `limit-review`);
  - los juicios ya escritos en el CSV se conservan entre ejecuciones;
  - una fila sin juicio deja el resultado como pendiente (`None`).
- **L6. Regla, fijada antes de generar:**
  - correctas ≥ 300 de 370;
  - negativas correctas ≥ 51 de 56;
  - citas correctas ≥ 94,3 %;
  - los umbrales del informe (negativas ≥ 85 % y citas ≥ 90 %).

  El resultado va a `build/language-review/result.json`, con `meets_rule`.

  **«Automático» se adopta** solo si se cumplen L6 y, además, la combinación ES·EN (L11) cumple los umbrales del informe con `english_answers=auto`. Son las preguntas en inglés que pide FR-024.

**Conjuntos y corpus en inglés**
- **L7. `testset/crosslang/es-docs.en.jsonl`:**
  - traducción al inglés de:
    - la muestra de F10 (`golden/retrieval_parity.jsonl`: 40 con respuesta y 10 seguimientos);
    - las 9 preguntas anteriores de esos seguimientos que faltan;
    - 15 sin respuesta elegidas con la semilla `20261101`;
  - cada fila conserva `doc`, `evidence`, `relevant_ref` y `expected_answer` en español, y lleva `source_id` con su id de origen;
  - los `previous` apuntan a ids del mismo fichero.
- **L8. Corpus en inglés:**
  - 3 libros de en.wikibooks con `revid` fijo y licencia CC BY-SA 4.0, en `offline/corpus/en/` con su manifiesto (los 13 campos de `corpus.FIELDS`) y un `SOURCES.md` generado;
  - `corpus.py` acepta el sitio de Wikibooks como parámetro; `es` sigue por defecto;
  - las descargas solo ocurren al construir el corpus y verifican el SHA-256 registrado.
- **L9. Índice `en`:**
  - `build/index/en.db`, con el esquema de la app y el mismo modelo de embeddings;
  - parámetros de troceado de `rag_parameters.json` (1594, 2113 y 4,554), sin recalcular la proporción del tokenizador ni tocar `build/chars_per_token.json`.
- **L10. Preguntas sobre los documentos en inglés:**
  - redactadas en inglés con el método de F05 (fragmentos de muestra, evidencia literal y de 1 a 4 datos clave contenidos en la respuesta esperada): 40 con respuesta, 10 seguimientos y 15 sin respuesta, en `en-docs.en.jsonl`;
  - su traducción al español, con la misma evidencia en inglés, va en `en-docs.es.jsonl`;
  - `validate-testset set=crosslang` aplica las reglas T1–T9, con mínimos proporcionales: al menos 40, 15 y 10 por tipo, y ningún documento por encima del 50 %.

**`crosslang-review`: la medida entre idiomas**
- **L11. Combinaciones** (research R9):

  | Combinación | Documentos | Preguntas | Respuestas |
  |---|---|---|---|
  | ES·EN | `ref` | `es-docs.en` | `english_answers` |
  | EN·EN | `en` | `en-docs.en` | `english_answers` |
  | EN·ES | `en` | `en-docs.es` | `es` |

  - `english_answers=auto` es el valor normal.
  - `english_answers=es` solo se usa si «Automático» no se adopta.
  - Cada combinación es una ejecución con su huella; se reanuda y se guarda en caché como las demás.
- **L12. Juicio:**
  - todas las respuestas van a `reports/crosslang-review.csv`, con las columnas de research R9;
  - método D-033, sin juicios heredados: correcta, parcial o incorrecta, más las citas como ok/total;
  - un comentario cuando no es «correcta»;
  - con el veredicto se anota si el fallo es de la búsqueda (sin bloque relevante en el contexto) o de la generación.
- **L13. Cifras por combinación:**
  - correctas, negativas, citas y negativas indebidas, con `report.manual_metrics`;
  - acierto de la búsqueda: preguntas con un bloque relevante en el contexto.

  Resultado en `build/crosslang-review/result.json`, con `meets_thresholds` por combinación.
- **L14. Regla de la tarjeta** (FR-032, fijada antes de generar):
  - si ES·EN, EN·EN y EN·ES cumplen los umbrales del informe, se queda el texto del PNG;
  - si no, se para con las cifras y una propuesta de texto (S10).

**Registro**
- **L15. Informe y decisión:**
  - la sección «Idiomas (F12)» de `reports/eval-report.md` recoge las dos revisiones con sus cifras y la decisión sobre «Automático»;
  - una entrada nueva en DECISIONS cierra el pendiente «Documentos en otro idioma».
- **L16. Pruebas de Python:**
  - `test_prompt` (idioma);
  - `test_judge` (negativa en inglés);
  - `test_runs`: huellas sin cambios con `"es"` y distintas con `"auto"`;
  - `test_corpus`: sitio y manifiesto `en`;
  - `test_testset`: conjuntos `crosslang` y su validación;
  - `test_pipeline`: filas de `language-review` heredadas o cambiadas, combinaciones de `crosslang-review` y cálculo de la regla.

  Toda la batería pasa (152 pruebas hoy, más las nuevas).

## 3. Esfuerzo y orden

1. L2–L4 con sus pruebas.
2. `language-review`: unos 6 minutos de generación y unas 220 respuestas a juzgar, por lotes.
3. Traducción de `es-docs.en` y `crosslang-review` de ES·EN con `english_answers=auto` (74 respuestas).
4. **Decisión sobre «Automático»** (L6 y ES·EN). Si no cumple, **se para** y el usuario decide (FR-024).
5. Corpus e índice `en`, preguntas `en-docs.*`, y EN·EN y EN·ES (130 respuestas).
6. Informe y decisión. **Se para** si el texto de la tarjeta tiene que cambiar (S10).
