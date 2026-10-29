# Contrato: golden de recuperación y revisión del orden en el harness (F10)

Cambios en `offline/` (herramienta de escritorio de F04/F05). Todo se ejecuta con `offline/.venv/bin/python -I`.

## Generador `offline/gen_retrieval_golden.py`

```bash
offline/.venv/bin/python -I offline/gen_retrieval_golden.py          # writes both files
offline/.venv/bin/python -I offline/gen_retrieval_golden.py --check  # regenerates in memory, fails on any difference
```

- **Entrada:**
  - el índice `ref` (`offline/build/index/ref.db`);
  - el conjunto `offline/testset/questions.jsonl`;
  - la caché `offline/build/query_embeddings.json`;
  - `offline/config/rag_parameters.json`.
- **Salida:**
  - `offline/golden/retrieval_golden.jsonl` (data-model §6);
  - `offline/golden/retrieval_parity.jsonl` (data-model §7).
- **Modelo:** si a la caché le falta un texto de búsqueda, carga EmbeddingGemma 2 con la comprobación de huella de `gen_embedding_golden.py`.

## Ablación «revisión del orden» (D-035)

- **`EvalConfig`**, con campos nuevos declarados en `LATE_FIELDS` para que las ejecuciones de F05 conserven su huella:
  - `third_list: str | None = None`, con valores `None` o `"trigram"`;
  - `rerank_alpha: float | None = None`;
  - `rerank_top: int = 20`.
- **`offline/eval/retrieval.py`:**
  - **`trigram_query(text)`:**
    - términos de ≥ 3 caracteres;
    - minúsculas y sin tildes;
    - sin palabras vacías;
    - distintos y como mucho 16;
    - entre comillas y unidos con `OR`.
  - **`Retriever.trigram(text, k)`:**
    - tabla FTS5 `trigram case_sensitive 0 remove_diacritics 1` sobre `content`;
    - creada una vez por conexión en una base adjunta en memoria;
    - BM25.
  - **`rerank(fused, vector_scores, text_scores, alpha, top)`:**
    - α · coseno normalizado + (1 − α) · BM25 normalizado (min-max por lista; 0 si falta);
    - solo los `top` primeros;
    - el resto conserva su orden.
- **`run_eval.py order-review`:** ejecuta, sobre las 370 preguntas y con la configuración de D-034:
  - D-034;
  - trigramas;
  - reordenación con α ∈ {0,3; 0,5; 0,7};
  - trigramas + reordenación con α = 0,5.

  Escribe la tabla en `offline/build/order_review.json`.
- **`run_eval.py report`:** añade a `offline/reports/eval-report.md` la sección «Revisión del orden (D-035, F10)», con:
  - la tabla de Recall@bloques, R@20 y MRR;
  - la regla de adopción (≥ +2 puntos de Recall@bloques sin pasar el presupuesto);
  - la decisión.

## Reglas

- **G1 — Deterministas:** volver a ejecutar el generador no cambia `git diff`. `--check` falla si un fichero difiere.
- **G2 — Misma referencia que la app:**
  - el golden usa `retrieval.py` y `selector.py` sin cambios de lógica;
  - las dos configuraciones del golden se leen de `rag_parameters.json` (D-034) y de los valores del informe.
- **G3 — Sin cambios en el índice:** los trigramas viven en una base en memoria. `schema.sql` y los índices de `build/` no cambian.
- **G4 — Muestra fija:**
  - semilla `20261029`;
  - 40 respondibles y 10 de seguimiento entre las puntuables;
  - el acierto de escritorio con D-034 en cada fila.
- **G5 — Pruebas Python** (sin modelos):
  - `trigram_query` y `rerank` con casos a mano;
  - `FUSED_LIMIT` igual a `fused_limit` del JSON;
  - forma de los dos golden: campos, recuentos y orden por id.
- **G6 — Adopción:** solo con ≥ +2 puntos de Recall@bloques sobre las 370 preguntas (clarify de F10). Si alguna variante lo cumple, se para y se consulta antes de llevarla a la app (plan, riesgo 3).
