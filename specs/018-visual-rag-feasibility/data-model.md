# Data model: Viabilidad del RAG visual (F18)

F18 no cambia `rag.db`, `chat.db` ni ningún dato de la app (FR-031). Los datos de esta feature son ficheros de evaluación en `offline/` y el informe del diagnóstico en el teléfono.

## 1. Escritorio (`offline/`)

### 1.1 Documento del corpus visual

Se guarda en `offline/corpus_visual/manifest.json` y sigue los campos de F05 (`eval/corpus.py`, `FIELDS`), con estos cambios:

| Campo | Cambio |
|---|---|
| `format` | Admite también `png` y `jpg` |
| `kind` | Admite también `estadistica`, `informe`, `escaneado` e `imagen` |
| `license` | Admite las licencias nuevas del corpus visual (R8), cada una con su texto de atribución |
| `fetch.pages` | Como en F05: `[a, b]`, para recortar un PDF a un extracto con bytes deterministas |
| `simulated_from` | Nuevo, solo en fotos simuladas: `{doc_id, page, seed, recipe}` |

- **Reglas:**
  - `sha256` registrado y verificado en cada construcción, como en F05;
  - una imagen suelta es un documento de 1 página;
  - las fotos simuladas se generan con `run_eval.py visual-photos` y se guardan en el corpus con su `sha256`, igual que las demás.
- **Ficha:** `offline/corpus_visual/SOURCES.md`, generada (`run_eval.py sources corpus=visual`).
- **Tamaño:** como mucho unos 40 MB en git. Los PDF escaneados se recortan a extractos.
- **Carpeta:** `offline/corpus_visual/`, fuera de `offline/corpus/`, que se empaqueta en el APK de pruebas (research R8).

### 1.2 Página rasterizada

Caché en `offline/build/visual/pages/`, que no va a git.

| Campo | Tipo | Notas |
|---|---|---|
| `doc_sha` | texto | SHA-256 del documento |
| `page` | entero ≥ 1 | Empieza en 1 |
| `px` | 768 · 1024 · 1536 | Resolución por el lado largo |
| `format` | `png` · `webp` · `jpeg` | La PNG es la referencia; las otras salen de ella con calidad 85 |
| `bytes` | entero | Peso codificado |
| `sha256` | texto | De los bytes codificados |
| `tile` | `full` · `top` · `bottom` | Mitades con un 10 % de solape (R11) |

### 1.3 Pregunta visual

En `offline/testset/visual/questions.jsonl` (R9).

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `v0001`… | Único |
| `type` | `tabla` · `grafico` · `escaneado` · `imagen` · `mixta` · `control` · `sin_respuesta` | — |
| `question` | texto | Sin copiar frases literales de la página |
| `doc` | id del manifiesto, o `null` | `null` solo en `sin_respuesta` |
| `pages` | lista de `[doc_sha, page]` | Vacía solo en `sin_respuesta`; las páginas existen y al menos una es de `doc`. Las de otro documento con el mismo contenido también cuentan (la foto y su original; el folleto de la BNE y la Gaceta de 1931) |
| `expected_answer` | texto | — |
| `key_facts` | lista de textos | Vacía solo en `sin_respuesta`; cada una está en su página |
| `review` | `pendiente` · `ok` · `descartada` | — |
| `review_note` | texto | Veredicto de D-033 |

- **Validación (V1–V8):** campos, tipos, páginas existentes, mínimos por tipo, ≥ 100 aceptadas y ≥ 60 % visuales (SC-006).
- **Congelado:** el SHA-256 del fichero queda en `visual_rules.json` (R12).

### 1.4 Índices

| Base (en `offline/build/index/`) | Contenido |
|---|---|
| `visual-text.db` | El índice de texto de F05 con los parámetros de D-034, sobre los documentos del corpus visual que tienen texto |
| `visual-pages-<px>-<tokens>.db` | Tabla `pages(doc_id, page, tile, embedding BLOB(256 o 512), full BLOB(768))`, con SQLite-Vector |
| `visual-chunk-pages.json` | Para cada fragmento, sus páginas: `{chunk_id: [page, …]}` (R10) |

### 1.5 Ejecución y reglas

- **`offline/config/visual_rules.json`:**
  - `testset_sha256`;
  - umbrales: `visual_gain_points: 15`, `text_loss_points: 2` (sobre las 323 respondibles de F05), `e2e_p95_ms: 8000`, `min_pages: 1`, `golden_min_cosine: 0.99` e `identity_min_cosine: 0.9999`;
  - fórmula de extremo a extremo: `retrieval_p95_ms: 206.5` y `page_search_bound_ms: 53.0` (F17 en el A063), más la diferencia de la consulta 440M − 270M y el p95 del primer token de la etapa B (R4);
  - etapa B: `stage_b_questions: 20` y `stage_b_max_candidates: 3`;
  - juez: `decisive_judge: "manual"` (D-075) y `judge_check_sample: 30`;
  - reglas de elección de R12;
  - semillas.

  Se hace commit **antes** de ejecutar las ablaciones.
- **Ejecución:** `offline/build/visual/runs/<huella>/`, con `config.json`, `retrieval.jsonl`, `metrics.json`, `answers.jsonl` y `metrics_answers.json`. La huella sigue el criterio de F05: configuración, índice, conjunto de prueba y modelos.
- **Informe:** `offline/reports/visual-eval-report.md`, generado.
- **Revisión manual:**
  - `offline/reports/visual-manual-judgement.csv`: la muestra de comprobación del juez y la revisión decisiva del conjunto visual;
  - `offline/reports/visual-text-loss.csv`: las respuestas de F05 que cambian con la configuración elegida, con su veredicto, más las que conservan el de `manual-judgement.csv`.
- **Hash de la regla:** cada ejecución guarda el hash git de `visual_rules.json`, y las ablaciones se niegan a correr si el fichero difiere de `HEAD` (R12).

### 1.6 Conjunto del dispositivo

En `offline/build/visual/device-set/` (R13).

```text
manifest.json        {"version": 1, "files": [{"path", "sha256", "kind": "image|pdf|reading"}], "vectors": "vectors.json"}
images/*.png|jpg|webp
pdfs/*.pdf
reading.json         [{"id", "image", "question", "key_facts", "expected_answer"}]
contexts.json        [{"id", "question", "blocks": ["[1] …", …], "pages": ["…"], "tiles": ["…"]}]   # 20 preguntas: contexto de D-034 + páginas, en el orden de v5-visual (R4)
decode/*.jpg|png     # IMAGE_DECODE: 2 fotos de 12 MP, PNG con transparencia, JPEG con EXIF girado y JPEG en CMYK (R13)
vectors.json         {"<image sha>": {"70": [768 floats], "140": [768 floats]}}
```

## 2. Teléfono (solo en debug)

### 2.1 Conjunto de prueba en el teléfono

- **Origen:** el conjunto del dispositivo se copia a `getExternalFilesDir(null)/dev-visual/`.
- **Verificación:** el diagnóstico comprueba cada fichero contra `manifest.json` antes de usarlo, y un fichero que no coincide no se usa.
- **Contenido:** nada del usuario; solo el corpus público.

### 2.2 Informe visual

En `filesDir/reports/visual-<yyyyMMdd-HHmmss>.{json,md}`. Se escribe de forma atómica tras cada resultado, como `ReportWriter` de F02.

- **`schemaVersion`:** 1.
- **`device`:** el `DeviceSnapshot` de F02, con el modelo, Android, SoC, RAM y el estado térmico al principio y al final.
- **`versions`:** de la app, de LiteRT-LM y la huella de cada modelo.
- **`scenarios[]`**, cada uno con:
  - `kind`: `GEMMA_INFO`, `GEMMA_PAGES`, `READING`, `EMBED_TEXT`, `EMBED_IMAGE`, `PARITY`, `RASTER`, `IMAGE_DECODE` o `PROJECTION`;
  - `model`, `backend` y `params` (páginas, presupuesto, trozos, tokens por imagen, px y formato);
  - `status`: `COMPLETED`, `CANCELLED`, `NOT_SUPPORTED`, `NOT_AVAILABLE` o `FAILED`, con su `reason`;
  - `metrics`: carga en frío, p50 y p95, primer token, tokens y velocidad del prefill, decodificación, pico de memoria, bytes, dimensión y norma;
  - `thermalStart`, `thermalEnd` y `charging`;
  - en `GEMMA_PAGES` y `READING`: `answers` (id y texto), solo en el JSON;
  - solo en `PARITY`: `vectors` (sha y vector de 768).
- **`gemmaInfo`:** modalidades, presupuesto máximo, firmas y backends por modalidad.

El texto de las respuestas y los vectores van **solo** en el JSON de este informe, en almacenamiento privado. Nunca van en el Markdown ni en logcat (FR-018).

### 2.3 Informe de la prueba de variantes

`EmbeddingVariantGoldenTest` escribe `filesDir/reports/variant-golden-<yyyyMMdd-HHmmss>.json` con:
- las huellas de la 270M y la 440M, comprobadas contra `ModelCatalog`;
- el coseno mínimo y el medio frente al golden, por backend;
- el mínimo y el medio de la identidad con la 270M (R5).

### 2.4 Estados de una prueba

```text
PENDIENTE → EN CURSO → COMPLETADA
                     ↘ CANCELADA (cancelar, o la app pasa a segundo plano)
                     ↘ NO ADMITIDA (p. ej., presupuesto > máximo del fichero)
                     ↘ NO DISPONIBLE (backend rechazado por el runtime)
                     ↘ FALLIDA (error capturado, con su motivo)
```
