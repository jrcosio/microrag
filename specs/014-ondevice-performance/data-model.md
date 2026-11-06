# Data Model: Rendimiento en dispositivo y decisión INT8 (F14)

## 1. Estado del índice INT8 (app, `rag.db` v5)

| Elemento | Dónde | Valores | Regla |
|---|---|---|---|
| `quant_dirty` | `rag_meta` (ya existe, F04) | `'0'` al día · `'1'` desfasado | Se pone a 1 en toda operación que cambia fragmentos. Pasa a 0 solo tras un `vector_quantize` completo de `chunks` |
| `pages_quant_dirty` | `rag_meta` (nueva, solo si las páginas usan INT8, R10) | igual | Igual para `pages`: `insertPage`, borrado de páginas. Si falta, se lee como `'1'` |
| `vector0_chunks_embedding` | tabla de SQLite-Vector | datos INT8 | La crea y la sustituye `vector_quantize`. Es reconstruible (constitución III). No la escribe la app |
| `vector0_pages_embedding` | igual | igual | Solo si las páginas usan INT8 |
| `int8Preloaded` | memoria de `DefaultRagIndex` | sí / no | Se pone a sí tras `vector_quantize_preload` en la conexión abierta; se pierde al cerrarla o al morir el proceso |

**Cuándo la búsqueda usa INT8:** `quant_dirty = '0'` **y** `int8Preloaded`. Si no, usa la exacta (F32), como hoy.

**Transiciones:**

```text
            insert/delete/reindex/removeFromIndex/deleteChunks
   ┌──────────────────────────────────────────────────────────────┐
   ▼                                                              │
DESFASADO ──(mantenedor: sin tanda activa, ≥ INT8_MIN_CHUNKS)──► RECONSTRUYENDO
   ▲                                                              │
   │ fallo / muerte del proceso                     vector_quantize OK
   └──────────────────────────────────────────────────────────────┤
                                                                   ▼
                                       AL DÍA (quant_dirty = 0) ──preload──► AL DÍA Y PRECARGADO
                                                 ▲                                │
                                                 └──── cierre de la conexión ─────┘
```

- **Bajo `INT8_MIN_CHUNKS`:** no se reconstruye; la búsqueda sigue en F32 y `quant_dirty` queda como esté.
- **Biblioteca vacía o todo excluido:** igual que bajo el umbral. Se cuentan los fragmentos buscables (`SEARCHABLE`).
- **Rowid reutilizado:** imposible de usar, porque cualquier inserción deja el índice desfasado.

## 2. Parámetros nuevos (`RagParameters` ↔ `offline/config/rag_parameters.json`)

| Parámetro | Valor inicial | Se decide en |
|---|---|---|
| `INT8_CANDIDATES` | 100 | banco de búsqueda (Recall@20 con reordenación ≥ 0,97) |
| `INT8_MIN_CHUNKS` | 0 | banco de búsqueda (tamaño donde F32 pasa de 20 ms) |
| `INT8_MAX_MEMORY_MB` | 64 | fijo: 100k × 264 B = 26,4 MB, con margen |
| `QUANT_REBUILD_DEBOUNCE_MS` | 2.000 | fijo |
| `TOKEN_ESTIMATE` (a, b, c) | (4,554; 4,554; 4,554): equivale al de hoy | `token-review` (R9) |
| `PREFIX_CACHE` | `false` | fase del teléfono (R6, D-088) |
| `SPECULATIVE_DECODING` | `OFF` | fase del teléfono (R7) |
| `ACTIVATION_PRECISION` | `null` (la del fichero) | fase del teléfono (R8) |
| `PROMPT_TEMPLATE` | `v4` | `v4-cifras` solo si se adopta (R8) |
| `MAX_BLOCKS` | 4 | R13 |
| `PAGE_FOLLOW_UP` | `JOINED` (hoy) | `visual-followup-review` (R10) |
| `PREWARM_BY_SCOPE` | `false` | fase del teléfono (R10) |

La prueba de paridad que ya existe (Kotlin ↔ JSON) cubre los parámetros que tienen pareja en el harness (estimador, plantilla, bloques y regla de páginas).

## 3. Banco de prueba (escritorio → teléfono)

```text
offline/build/bench/bank-<N>/          N ∈ {10000, 50000, 100000}
├── manifest.json     { n, seed, dims: 256, model: "embeddinggemma-2-text-270m", model_sha256,
│                       documents, folders, excluded_fraction: 0.3, files: { name: sha256 } }
├── chunks.jsonl.gz   { doc, folder, excluded, ordinal, heading_path, page, text }   (uno por fragmento)
└── vectors.f32       N × 256 float32 little-endian, L2-normalizados, en el orden de chunks.jsonl
offline/build/bench/pages-<M>/         M ∈ {1000, 5000, 10000}
├── manifest.json     { m, seed, sigma, source: "corpus_visual", model: "…-440m", files }
└── vectors.f32       M × 256 float32, perturbados de las páginas reales y renormalizados
```

- **En el teléfono:** `getExternalFilesDir("dev-bench")/<nombre>/` → `cacheDir/bench/<nombre>.db`, construido con `DefaultRagIndex` en su propio dispatcher. Se reutiliza si su `manifest.json` coincide.
- **Documento del banco:** `id` sintético, nombre `bench-<n>.txt`, carpeta `Bench <k>`, `status = done`, `included = 0` en el 30 % de los documentos.
- **Preguntas:** `offline/testset/` de F05 (370 puntuables), copiadas a `dev-bench/questions.jsonl`.

## 4. Medida (`filesDir/reports/f14-<banco>-<fecha>.json`)

```json
{
  "bench": "search-scale | generation | sustained | numeric | embedders | pages | visual-sustained | e4b",
  "started": "2026-11-…T…", "device": "Nothing A063", "android": 35, "app": "0.1.0",
  "runtimes": { "litertlm": "0.19.1", "sqliteVector": "1.2.0" },
  "models": [{ "id": "GEMMA4_E2B", "sha256": "…", "backend": "GPU", "precision": "default" }],
  "conditions": { "start": { "thermal": "NONE", "headroom": 0.54, "battery": 81, "batteryTempC": 33.0,
                             "charging": false, "rssMb": 0, "rssPeakMb": 0, "heapMb": 0, "dbBytes": 0 },
                  "end": { … } },
  "variants": [{ "name": "f32 | int8 | int8-rerank | prefix | mtp | fp32 | blocks-6 | …",
                 "parameters": { … },
                 "samples": [{ "i": 0, "q": "q0123", "phase": "embed|vector|fts|hybrid|scoped|ttft|total",
                               "ms": 12.3, "thermal": "NONE", "tokens": 1630, "estimated": 1612 }],
                 "summary": { "p50": 0, "p95": 0, "p99": 0, "n": 0 },
                 "extra": { "recallAt20": 0.98, "quantizeMs": 0, "preloadMs": 0, "preloadMb": 0 } }],
  "status": "completed | not-available | does-not-fit | no-space | aborted-thermal",
  "reason": null
}
```

- Las respuestas generadas solo van en los bancos de cifras y de goldens. Se copian a `offline/build/device/`, fuera de git.
- Nunca hay datos del usuario.

## 5. Conjuntos nuevos del escritorio

| Conjunto | Fichero | Campos |
|---|---|---|
| Cifras | `offline/testset/numeric/questions.jsonl` | `id`, `question`, `doc`, `chunk_ids`, `expected_figures` (lista), `source` (`f05` o `f18-page-text`) |
| Revisión de cifras | `offline/testset/numeric/review.csv` | `id`, `variant`, `precision`, `device`, `deformed` (sí/no), `figures`, `note` |
| Seguimientos con páginas | `offline/testset/visual/followups.jsonl` | `id`, `kind` (`switch` o `follow`), `first`, `second`, `first_page` (doc, página), `expected_page` (doc, página) |
| Reglas de F14 | `offline/config/f14_rules.json` | las reglas de D-088 y SC-007, en git **antes** de evaluar (commit propio) |

## 6. Palanca (DECISIONS)

| Campo | Contenido |
|---|---|
| Nombre | prefijo, MTP, FP32, `v4-cifras`, estimador, bloques, precarga por ámbito, regla de páginas, reordenación |
| Regla | la de D-088 que le toca, o la de su R, escrita antes de medir |
| Cifras | el p95 sostenido con y sin ella, la calidad en el harness si aplica, y la memoria |
| Resultado | adoptada · descartada · no disponible (con la versión) |
| Cambio | el valor del interruptor en `RagParameters` |
