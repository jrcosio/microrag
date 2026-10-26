# Data model — F05 Harness offline de evaluación

Todo vive en `offline/`.
- **Versionado:** el corpus, el conjunto de prueba, la configuración decidida, los informes y el golden del troceado.
- **Fuera de git (`offline/build/`):** índices, ejecuciones y cachés. Se regeneran con un comando.

## 1. Corpus (`offline/corpus/`)

```text
offline/corpus/
├── manifest.json        # lista de documentos (abajo)
├── SOURCES.md           # atribuciones y licencias legibles, generadas desde el manifest
└── docs/                # ficheros del corpus: <id>.pdf | <id>.md | <id>.txt
```

`manifest.json` es una lista de objetos:

| Campo | Ejemplo | Notas |
|---|---|---|
| `id` | `boe-lau` | Único y estable; nombra el fichero |
| `title` | `Ley 29/1994, de Arrendamientos Urbanos` | Se usa como `display_name` |
| `format` | `md` \| `pdf` \| `txt` | Formatos del MVP |
| `kind` | `normativa` \| `guia` \| `manual` | Para estratificar |
| `source_url` | URL de origen | — |
| `publisher` | `Agencia Estatal Boletín Oficial del Estado` | — |
| `license` | `BOE: reutilización con atribución` \| `AEAT: RD 1495/2011` \| `CC BY-SA 4.0` | — |
| `attribution` | La frase de atribución exigida | — |
| `source_updated` | `2024-05-23` | Fecha de actualización del original |
| `retrieved` | `2026-10-27` | — |
| `transformations` | `XML del BOE → Markdown; sin notas de modificación` | Exigido por BOE y CC BY-SA |
| `sha256` | Huella del fichero en `docs/` | Es el `content_hash` del índice |

## 2. Bloques y fragmentos

```text
RawBlock(heading_path: [str], text: str, page: int | None)      # salida de la extracción (R3)
Chunk(ordinal: int, heading_path: str, content: str, page: int | None,
      start: int, end: int)                                      # salida del troceado (R4)
```

`start` y `end` son las posiciones en caracteres en el texto extraído del documento. Sirven para localizar la evidencia; no se escriben en `schema.sql`.

**`token_estimate`** = `ceil(len(content) / chars_per_token)`.

## 3. Índice de evaluación (`offline/build/index/<variante>.db`)

- **`schema.sql` de la app, sin cambios.**
  - `documents`: `source = 'user'`, `status = 'done'`, `display_name = title`, `content_hash = sha256`, `mime_type` según el formato y `page_count` en los PDF.
  - `chunks`: `title_norm`, `body_norm`, `token_estimate` y `embedding` calculados como la app (F04 data-model §4).
  - `rag_meta`: los valores que espera la app (research R5).
- **Tablas solo de evaluación:**

```sql
CREATE TABLE eval_full_embeddings (chunk_id INTEGER PRIMARY KEY, v768 BLOB NOT NULL);  -- float32 LE, sin normalizar
CREATE TABLE eval_chunk_spans (chunk_id INTEGER PRIMARY KEY, start INTEGER NOT NULL, "end" INTEGER NOT NULL);
CREATE TABLE eval_build (key TEXT PRIMARY KEY, value TEXT NOT NULL);  -- config, huellas, recuentos, chars_per_token
```

**Variantes:**

| Nombre | Se construye | Cambia |
|---|---|---|
| `ref` | Desde el corpus | Referencia: 350 tokens, con cabecera, 256 dims, normalizador v1 |
| `chunk250`, `chunk500` | Desde el corpus | Tamaño de fragmento |
| `nohdr` | Desde el corpus | Sin cabecera contextual en el texto embebido |
| `dim128`, `dim512`, `dim768` | Copia de `ref` | `embedding` y `vector_init` |
| `fts_raw`, `fts_stop` | Copia de `ref` | `title_norm` y `body_norm` (y la consulta) |

## 4. Conjunto de prueba (`offline/testset/`)

`questions.jsonl` lleva una pregunta por línea. El contrato está en `contracts/testset.md`.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `q0001` … | Estable |
| `type` | `answerable` \| `unanswerable` \| `followup` | — |
| `question` | str | — |
| `previous` | id \| null | Solo `followup`: la pregunta anterior |
| `doc` | id del manifest \| null | Documento de la evidencia (null en `unanswerable`) |
| `content_type` | `texto` \| `lista` \| `tabla` \| `procedimiento` \| `dato` | Para estratificar |
| `expected_answer` | str \| null | Null en `unanswerable` |
| `key_facts` | [str] | De 1 a 4 (R9); vacío en `unanswerable` |
| `evidence` | str \| null | Tramo literal del texto extraído |
| `relevant_ref` | [[sha256, ordinal]] | Relevantes en `ref` (derivados de la evidencia) |
| `review` | `pending` \| `ok` \| `fixed` \| `discarded` | Las `discarded` no cuentan |
| `review_note` | str \| null | Comentario del usuario |

`review.csv` es la hoja de revisión, con las columnas `id, documento, pregunta, respuesta_esperada, evidencia, veredicto, comentario`. El veredicto puede ser `ok`, `corregir`, `descartar` o vacío.

## 5. Configuración de una ejecución

```text
EvalConfig(
  build: "ref" | "chunk250" | ...,       # índice
  mode: "vector" | "fts" | "hybrid",
  follow_up_rule: bool,
  candidates: 50, rrf_k: 60,
  max_blocks: 4 | 6 | 8, neighbors: bool (expand_top 2 | 0),
  max_per_document: 3, budget_tokens: 2200,
  llm: "E2B" | "E4B" | None,             # None: solo recuperación
  max_output_tokens: 512,
)
```

Su **huella** es el SHA-256 del JSON canónico de la configuración más:
- las huellas del índice (`eval_build`);
- la del conjunto de prueba;
- la de los modelos.

Identifica la ejecución (FR-019).

## 6. Resultados (`offline/build/runs/<huella>/`)

| Fichero | Contenido |
|---|---|
| `config.json` | `EvalConfig` y las huellas |
| `retrieval.jsonl` | Por pregunta: búsqueda usada, ids de vector, FTS y RRF (top-50) y bloques seleccionados (documento, ordinales y tokens) |
| `answers.jsonl` | Por pregunta: prompt (tokens reales), respuesta, citas, juicio automático y tiempos. Se añade línea a línea (reanudable) |
| `metrics.json` | Las métricas agregadas de R10 |

## 7. Informe y decisiones (versionados)

- **`offline/reports/eval-report.md`:**
  - corpus (con licencias) y conjunto de prueba;
  - tabla de la referencia y de cada ablación frente a los umbrales;
  - tiempos;
  - acuerdo entre el juicio automático y el manual;
  - configuración elegida.
- **`offline/reports/manual-judgement.csv`:** juicio manual de la configuración elegida (respuesta y citas) y muestra revisada por el usuario.
- **`offline/config/rag_parameters.json`:** los parámetros decididos (contracts/rag-parameters.md).
- **`offline/golden/chunker_golden.jsonl`:** una línea por caso, `{"blocks": [RawBlock…], "target_chars", "max_chars", "chunks": [{ordinal, heading_path, content, page}…]}`. Lo genera `chunker.py` con los parámetros elegidos y casos sintéticos y del corpus.
