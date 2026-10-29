# Contrato: golden de respuestas y revisión de la plantilla (escritorio, F11)

Herramienta: `offline/` (Python 3.12 en `offline/.venv`, siempre con `python -I`). La referencia es F05: plantilla en `eval/prompt.py`, juicio en `eval/judge.py` y generación en `eval/generate.py`. Sin dependencias nuevas.

## 1. Golden de prompt y lectura (`gen_answer_golden.py`)

```
offline/.venv/bin/python -I offline/gen_answer_golden.py [--check]
```

**Entradas** (todas existentes; research R2):
- `testset/questions.jsonl`: las 426 vivas;
- la recuperación de la ejecución elegida de F05, `build/runs/<retrieval de 06ef519f>/retrieval.jsonl`, con los bloques y su texto;
- las respuestas de esa ejecución, `build/runs/06ef519f…/answers.jsonl`.

  Si la revisión de la plantilla (§2) adopta una variante, se usan las de su ejecución.

**Salida:** `golden/answer_golden.jsonl` (data-model §7). Las 10 filas con el prompt entero se eligen por id, de forma determinista.

**Reglas G1–G6:**
- **G1.** El prompt se construye con `eval.prompt.build_prompt` y la plantilla activa de `config/rag_parameters.json`, y el turno anterior es el de `pipeline.generate`: la pregunta anterior y la respuesta anterior **generada**. No hay segunda implementación.
- **G2.** `citations`, `refusal` y `norm` salen de `eval.judge`. `cited_order` se calcula aquí, con la regla de la app (research R3), y su prueba de Python lo fija.
- **G3.** `--check` reconstruye en memoria. Si difiere, termina con «out of date: …», como `gen_retrieval_golden.py`.
- **G4.** Escritura determinista: ordenado por id, UTF-8 sin escapar y `\n`.
- **G5.** `tests/test_answer_golden.py` comprueba la forma:
  - 426 filas en orden;
  - campos;
  - `prompt_sha256` coherente con `prompt` cuando hay texto;
  - `cited_order` ⊆ `citations` y dentro del rango de bloques.
- **G6.** El golden vive una sola vez en `offline/golden`, como el de F10:
  - las pruebas JVM lo leen de `../offline/golden`;
  - las instrumentadas, de los assets del APK de pruebas (ya montados);
  - el lector va en `src/sharedTest` (`AnswerGoldenFiles`).

## 2. Revisión de la plantilla (`run_eval.py template-review`)

```
offline/.venv/bin/python -I offline/run_eval.py template-review [variants=v3,v4]
```

**Variantes** (`eval/prompt.py`; texto exacto en research R4):

| Variante | Reglas extra, tras «No uses conocimiento propio…» |
|---|---|
| `v3` | «Si la PREGUNTA pide varias cosas…» y «Copia cifras, plazos…» |
| `v4` | las de `v3` y «Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa…» |

`TEMPLATES` pasa a `("informe", "v2", "v3", "v4")`.

**Pasos:**
1. **Configuración elegida (D-034):** E2B, 4 bloques, sin vecinos, 1.719 tokens, con `prompt_template=<variante>`. Se genera con el comando existente; las ejecuciones son reanudables. Cada una cuesta ≈ 5,5 min en la GPU del escritorio.
2. **Métricas automáticas** (`judge.summarize`) por variante, y respuestas que cambian frente a «informe» (`06ef519f`).
3. **Criba** (R4): pasa a la revisión manual una variante que sube ≥ 1 punto las correctas automáticas y mantiene negativas ≥ 85 % y citas ≥ 90 %.
4. **Revisión manual** (método D-033, por el asistente):
   - se juzgan solo las respuestas que cambian, en `reports/template-review.csv` (data-model §8);
   - las que no cambian heredan el veredicto de `manual-judgement.csv`.
5. **Adopción:** manual ≥ 80,4 % correctas, negativas ≥ 85 % y citas ≥ 90 %. Si las dos cumplen, gana la de más correctas, y a igualdad la más corta.
6. **Resumen** en `build/template_review.json` y sección «Revisión de la plantilla (D-035, F11)» en `reports/eval-report.md` (`eval/report.py`), con la tabla:

   | Variante | Cambiadas | Correctas (regla) | Negativas | Citas | Criba | Correctas (manual) | Adoptada |

**Reglas G7–G12:**
- **G7.** Las variantes no se cambian después de ver resultados (R4). Una variante nueva sería otra decisión.
- **G8.** Misma recuperación que la configuración elegida. El comando comprueba que los bloques coinciden 426/426 con la ejecución de referencia y se para si no.
- **G9.** Las respuestas de seguimiento usan la respuesta anterior generada en la misma ejecución (`pipeline.generate`).
- **G10.** La revisión manual sigue los criterios de `manual-judgement.csv` (correcta, parcial o incorrecta; citas correctas sobre el total). Cada veredicto lleva un comentario cuando no es «correcta».
- **G11.** Si se adopta una variante:
  - cambia `prompt_template` en `rag_parameters.json` y en `RagParameters`;
  - se regenera el golden (§1) con sus respuestas;
  - se registra una decisión nueva.

  Si no, se registra que se mantiene «informe», con la tabla.
- **G12.** Pruebas de Python:
  - `test_prompt.py`, ampliada con `v3` y `v4`: reglas en su sitio y el resto igual a «informe»;
  - `test_runs.py`: la huella con `prompt_template`;
  - `test_report.py`: la sección.

## 3. Parámetro compartido

`config/rag_parameters.json` gana `llm_max_num_tokens: 4096`. `eval/generate.py` lo lee en lugar de su constante. `RagParametersTest` (app) comprueba que coincide con `RagParameters.LLM_MAX_NUM_TOKENS`.
