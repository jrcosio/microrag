# Pipeline offline (Python)

Herramienta de escritorio de MicroRAG. Solo sirve para evaluar, ajustar parámetros y generar los ficheros *golden* que comparten el móvil y el escritorio (constitución III y VI, D-002). No construye ni distribuye índices para la app.

En F04 genera los dos golden de compatibilidad:
- `golden/normalizer_golden.jsonl`: el normalizador español, idéntico en Kotlin y en Python;
- `golden/embedding_golden.jsonl`: vectores de EmbeddingGemma 2 270M de referencia.

Las reglas están en `specs/004-rag-data-core/contracts/offline-golden.md`.

## Entorno

- Python 3.12, en un entorno virtual propio.
- Dependencias fijadas en `requirements.txt`, sin transitivas:
  - `litert-lm-api==0.18.1` (se importa como `litert_lm`): embeddings, generación y tokenizador;
  - `sqliteai-vector==1.1.2`: SQLite-Vector de escritorio, la misma versión que en la app (evaluación, F05);
  - `pypdf==6.20.1`: texto de los PDF del corpus (evaluación, F05).
- Se ejecuta siempre con `offline/.venv/bin/python -I`.

```bash
python3.12 -m venv offline/.venv
offline/.venv/bin/pip install -r offline/requirements.txt
```

El modelo no se versiona. Se enlaza o se copia en `offline/models/` desde la copia local de los modelos verificados:

```bash
mkdir -p offline/models
ln -sf "$HOME/MicroRAG-models/embeddinggemma-2-text-270m.litertlm" offline/models/
```

## Regenerar y comprobar

```bash
offline/.venv/bin/python -I offline/gen_normalizer_golden.py   # → golden/normalizer_golden.jsonl (≥ 300 pares)
offline/.venv/bin/python -I offline/gen_embedding_golden.py    # → golden/embedding_golden.jsonl (50 vectores)
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

- Los dos generadores son deterministas: volver a ejecutarlos no cambia `git diff`.
- `gen_embedding_golden.py` comprueba antes el SHA-256 del modelo y falla si no es el del catálogo de la app.
- Las pruebas Python no necesitan el modelo.

La app comprueba los mismos ficheros:
- `NormalizerGoldenTest` (JVM) y `NormalizerGoldenInstrumentedTest` (dispositivo): coincidencia exacta del normalizador;
- `EmbeddingGoldenFileTest` (JVM): modelo, entradas y normas del golden de embeddings;
- `EmbeddingGoldenTest` (teléfono, CPU y GPU): coseno ≥ 0,99 por par.

## Cambiar el normalizador

`normalizer.py` es un port exacto de `SpanishText.kt`. Un cambio que altere la salida obliga a:
1. cambiar los dos lados y subir `VERSION` en ambos (`SpanishText.VERSION` y `normalizer.VERSION`);
2. regenerar `golden/normalizer_golden.jsonl`;
3. fijar su nueva huella en `NormalizerGoldenTest` y en `tests/test_normalizer.py` (regla O6).

Los índices construidos con la versión anterior quedan incompatibles hasta reindexar (`rag_meta.normalizer_version`).

## Cambiar el modelo de embeddings o LiteRT-LM

Se regenera `golden/embedding_golden.jsonl` con el nuevo modelo o la nueva versión de `litert-lm-api`, y se vuelve a ejecutar `EmbeddingGoldenTest` en cada teléfono de prueba (constitución VI).

## Evaluación (F05)

El pipeline de `offline/eval/` mide en el escritorio la recuperación y las respuestas con los mismos artefactos que la app. El índice usa:
- `schema.sql`, el normalizador, los prefijos y el truncado;
- SQLite-Vector 1.1.2 y Gemma 4 E2B.

Contratos en `specs/005-offline-eval-harness/contracts/`. Todo se ejecuta con `offline/.venv/bin/python -I offline/run_eval.py <comando>`.

| Paso | Comando |
|---|---|
| Corpus (solo descarga lo que falta y verifica huellas) | `fetch-corpus` y `sources` |
| Índices (`ref`, `chunk250`, `chunk500`, `nohdr`, `dim128/512/768`, `fts_raw`, `fts_stop`) | `build all` |
| Muestra para redactar preguntas | `sample` |
| Validación del conjunto (reglas T1–T9) | `validate-testset` (`--pre-review` antes de la revisión) |
| Revisión del usuario (hoja `testset/review.csv`) | `review-sheet` y, tras rellenarla, `review-apply` |
| Una ejecución de recuperación o de generación | `retrieve key=valor…` y `generate llm=E2B key=valor…` |
| Ablaciones | `ablations` (recuperación) y `ablations --generation --base …` |
| Informe | `report` → `reports/eval-report.md` |

**Corpus:** documentos públicos (BOE, Agencia Tributaria y Wikilibros). Se versionan con su origen, licencia, atribución y transformaciones en `corpus/manifest.json` y `corpus/SOURCES.md` (D-032). Un documento nuevo se añade al manifiesto con `"sha256": "pending"` y se registra con `fetch-corpus --record`.

**Fuera de git** (`offline/build/`): índices, extracciones, cachés y ejecuciones. Cada ejecución vive en `build/runs/<huella>/`. La huella identifica la configuración, el índice, el conjunto de prueba y los modelos, y una generación interrumpida se reanuda donde se quedó.

**Modelos:** `offline/models/` enlaza a `$HOME/MicroRAG-models`, con EmbeddingGemma 2 270M, Gemma 4 E2B y, para la comparación, Gemma 4 E4B. Cada modelo se verifica por SHA-256 antes de usarse.

## Importación y parsing (F06)

La app extrae TXT, Markdown y las líneas de PDF exactamente como `eval/extract.py`, que es lo que evaluó F05. Para que no se separen, todo esto se genera desde Python y se comprueba en la app (contratos en `specs/006-import-parsing/contracts/extraction-golden.md`).

| Fichero | Generador | Lo comprueba |
|---|---|---|
| `golden/extraction_golden.jsonl`: decodificación, TXT, Markdown y páginas de PDF, a mano y del corpus | `gen_extraction_golden.py` (`--check` para verificar) | `ExtractionGoldenTest` (JVM) e `IngestGoldenInstrumentedTest` (dispositivo) |
| `golden/chunker_golden.jsonl` (F05) | `run_eval.py chunker-golden` | `ChunkerGoldenTest` e `IngestGoldenInstrumentedTest` |
| `app/src/main/java/.../rag/ingest/PyTables.kt`: espacios, saltos de línea y dígitos de Python | `gen_pytext_tables.py` | `tests/test_pytext_tables.py` |
| `app/src/androidTest/assets/pdf/*.pdf`: fixtures deterministas (texto, escaneado, cifrado, restringido, truncado, falso y 200 páginas) | `gen_pdf_fixtures.py` | `tests/test_pdf_fixtures.py` y las pruebas instrumentadas |

**Lectura de texto:** `extract.py` lee con `decode_text`: UTF-8 (sin BOM si lo hay) o, si no es UTF-8 válido, Windows-1252 con sustitución, con los saltos `\r\n` y `\r` convertidos a `\n`. Cambiar `extract.py` obliga a regenerar el golden en el mismo commit; si no, fallan las pruebas Python y las de la app.

**Comparación con el teléfono:** la extracción de PDF del teléfono (PDFium) frente a pypdf, medida con las evidencias del conjunto de prueba:

```bash
ADB_DIR=/sdcard/Android/data/com.lisdatasolutions.microrag/files/corpus
adb shell mkdir -p $ADB_DIR && adb push offline/corpus/docs/*.pdf $ADB_DIR/
adb shell am instrument -w -e class com.lisdatasolutions.microrag.data.ingest.CorpusPdfExtractionTest \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
adb pull $ADB_DIR/device-extraction.jsonl offline/build/
offline/.venv/bin/python -I offline/run_eval.py device-extraction offline/build/device-extraction.jsonl
```

**Esquema v2:** F06 añade `documents.skipped_pages` (D-038). Los índices de evaluación ya construidos son v1: antes de volver a evaluar, reconstrúyelos con `run_eval.py build ref` y las variantes que se necesiten.
