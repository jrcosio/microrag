# Contrato — Pipeline de evaluación (`offline/run_eval.py`)

Se ejecuta siempre con `offline/.venv/bin/python -I offline/run_eval.py <comando>`. Cada comando es idempotente y sale con un código distinto de 0 si falla.

| Comando | Entrada | Salida | Regla |
|---|---|---|---|
| `fetch-corpus` | `corpus/manifest.json` con `source_url` | `corpus/docs/*` | Descarga solo lo que falta. Si la huella no coincide con el manifest, falla. Es el único comando con red |
| `sources` | manifest | `corpus/SOURCES.md` | Generado; nunca se edita a mano |
| `build <variante>` | corpus, modelos y `appconfig` | `build/index/<variante>.db` | E1–E4 |
| `validate-testset` | `testset/questions.jsonl` y `build/index/ref.db` | Informe de errores | contracts/testset.md |
| `review-sheet` / `review-apply` | conjunto y `review.csv` | conjunto actualizado | contracts/testset.md |
| `retrieve <config>` | índice y conjunto | `build/runs/<huella>/retrieval.jsonl` y `metrics.json` | E5 |
| `generate <config>` | `retrieval.jsonl` y el modelo LLM | `answers.jsonl` y `metrics.json` | E6–E7 |
| `ablations` | Las variantes de FR-012 | Todas las ejecuciones | Una variante cambia un solo parámetro respecto a la referencia |
| `report` | `build/runs/*` y `reports/manual-judgement.csv` | `reports/eval-report.md` | E8 |
| `chunker-golden` | `config/rag_parameters.json` | `golden/chunker_golden.jsonl` | Determinista |

## Reglas

| # | Regla |
|---|---|
| E1 | Antes de usar un modelo, se comprueba su SHA-256 contra el catálogo de la app (EmbeddingGemma 2 y Gemma 4 E2B) o contra research R7 (E4B) |
| E2 | `rag_meta` se escribe con los valores de `appconfig.py` (leídos del código Kotlin). Si `dims` difiere de la app (variantes `dim*`), solo cambian `embedding_dims` y `vector_init` |
| E3 | Determinista: el mismo corpus y la misma configuración dan los mismos fragmentos, ids y BLOB. `eval_build` guarda las huellas de entrada |
| E4 | La construcción informa de los documentos indexados y descartados (con el motivo), los fragmentos, la distribución de tamaños, `chars_per_token` y los fragmentos por encima del límite de entrada |
| E5 | La recuperación usa el mismo SQL que la app: `vector_full_scan(…, 50)`, `MATCH` con `bm25(chunks_fts, 3.0, 1.0) LIMIT 50` y RRF con k = 60, y el selector del informe |
| E6 | La generación usa el sampler determinista de la app, una conversación por pregunta, `max_num_tokens = 4096` y `max_output_tokens = 512`, en la GPU del Mac. Repite una muestra de 50 en CPU e informa de las diferencias |
| E7 | Reanudable: una respuesta ya guardada para la misma huella de configuración no se regenera |
| E8 | El informe compara cada métrica con su umbral, muestra cada ablación junto a la referencia y señala la configuración elegida. Las cifras de respuesta de la elegida usan el juicio manual |
