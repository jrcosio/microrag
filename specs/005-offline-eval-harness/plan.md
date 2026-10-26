# Implementation Plan: Harness offline de evaluación (F05)

**Branch**: `005-offline-eval-harness` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-offline-eval-harness/spec.md`

## Summary

F05 construye en `offline/` el pipeline de escritorio que fija con medidas los parámetros de recuperación y contexto:

- **Corpus público versionado** (D-032): entre 25 y 40 documentos en español, de tres fuentes con la reutilización verificada (R2):
  - legislación consolidada del BOE, convertida a Markdown con su estructura;
  - guías en PDF de la Agencia Tributaria;
  - manuales de Wikilibros (CC BY-SA 4.0).

  Cada documento lleva su URL, su licencia, su atribución y su huella.
- **Índices de evaluación**, con el mismo `schema.sql`, normalizador, prefijos, cabecera y truncado que la app, y la misma SQLite-Vector 1.1.2, ahora en su paquete de escritorio (R1). Uno por variante de construcción; las variantes de dimensión y normalización se derivan sin volver a embeber.
- **Recuperación, selector y prompt:** ports literales del informe. Serán la referencia de F10 y F11.
- **Generación con Gemma 4 E2B** en la GPU del Mac. El experimento midió 0,6 s por respuesta con un prompt de 2.000 tokens (R7). E4B se usa una vez, en la configuración elegida.
- **Conjunto de prueba:** lo redacto yo con evidencia literal y lo revisa el usuario (≥ 100), con una hoja de cálculo.
- **Juicio:** automático en todas las variantes y manual en la elegida.
- **Informe** con todas las ablaciones frente a los umbrales.
- **Decisiones:**
  - en DECISIONS;
  - en `offline/config/rag_parameters.json`, que se propaga a un nuevo `RagParameters.kt` comprobado por una prueba JVM;
  - un golden del troceado para que F06 compruebe su `Chunker` en Kotlin.

## Technical Context

**Language/Version**:
- Python 3.12 (`offline/.venv`).
- Kotlin 2.4.21 solo para `RagParameters` y su prueba.

**Primary Dependencies** (verificadas el 2026-10-26, research R1):
- `litert-lm-api==0.18.1` (ya en F04);
- `sqliteai-vector==1.1.2` (Apache-2.0, `vector.dylib` para macOS ARM64);
- `pypdf==6.20.1` (BSD-3, sin dependencias);
- el resto, biblioteca estándar (`sqlite3` 3.50.4 con FTS5, `json`, `csv`, `hashlib`, `urllib`).
- Sin numpy.

**Storage**:
- Versionado: `offline/corpus/` (≤ 30 MB), `offline/testset/`, `offline/config/`, `offline/reports/` y `offline/golden/chunker_golden.jsonl`.
- En `offline/build/` (ignorado): índices SQLite por variante y ejecuciones por huella de configuración.
- Modelos en `$HOME/MicroRAG-models`, enlazados desde `offline/models/`.

**Testing**:
- **Python (`unittest`):**
  - troceado: casos del informe, solape, fronteras y división por frases;
  - extracción (MD, TXT y PDF de prueba);
  - RRF, regla de seguimiento, selector (límite por documento, vecinos, fusión sin solape, presupuesto, orden) y prompt;
  - juicio (datos clave, negativa, citas);
  - métricas con casos calculados a mano;
  - validación del conjunto;
  - `appconfig` frente al código Kotlin.
- **JVM:** `RagParametersTest`.
- **Ejecución real:** construcción reproducible (SC-001), validación del conjunto y las ablaciones.

**Target Platform**:
- Mac del proyecto (M5 Pro, 48 GB, macOS ARM64).
- La app no cambia de comportamiento: solo recibe constantes.

**Project Type**: herramienta Python de escritorio (`offline/`) más un fichero Kotlin de constantes.

**Performance Goals**:
- SC-006: una variante de recuperación tarda < 10 min (estimado < 1 min).
- Generación de ≈ 390 preguntas por variante: ≈ 7 min en la GPU (R7).

**Constraints**:
- Las únicas descargas son el corpus (una vez, con huella) y el modelo E4B. Las dos son de desarrollo; la app sigue sin red (constitución I).
- Mismos artefactos que la app (constitución III).
- Prompt de 2.000 tokens o menos en el p95 (constitución IV, D-026); el presupuesto de contexto del informe se ajusta a ese límite (research R6).
- Atribuciones y licencias del corpus (R2).

**Scale/Scope**:
- 25–40 documentos y 1.500–3.000 fragmentos por variante.
- ≈ 390 preguntas.
- 10 variantes de índice y ≈ 15 ejecuciones de recuperación.
- 6 de generación con E2B y 1 con E4B.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F05 | Estado |
|---|---|---|
| I. On-device y privacidad | Nada cambia en la app. Las descargas del corpus y de E4B son herramientas de desarrollo en el Mac, no tráfico de la app. Corpus público: sin datos privados (D-032) | ✅ |
| II. Stack y arquitectura | Solo se añade `RagParameters` (dominio, constantes puras). Las versiones están verificadas (R1) | ✅ |
| III. RAG según el informe | Es la evaluación documentada que el principio exige para fijar parámetros. Usa el mismo esquema, normalizador, prefijos, cabecera, truncado y SQLite-Vector que la app, y RRF k = 60 con el selector del informe. Las decisiones van a DECISIONS (P1) | ✅ |
| IV. Rendimiento | El número de bloques se elige con un prompt de 2.000 tokens o menos en el p95 (D-026), medido con el tokenizador real. La latencia en el teléfono sigue siendo de F14 | ✅ |
| V. Veracidad del estado | El juicio de citas exige que el bloque citado contenga la evidencia. Las cifras finales usan el juicio manual, y el informe da el acuerdo con las reglas | ✅ |
| VI. Calidad y pruebas | Pruebas unitarias del pipeline y una prueba JVM de las constantes. Construcción reproducible. Si cambia `dims`, se repiten el golden de embeddings y su prueba en el teléfono (P3) | ✅ |
| VII. UI | Sin UI | N/A |
| VIII. Proceso | Documentación en español y código en inglés. D-032 y las decisiones nuevas. Parada tras analyze y antes de cerrar si fallan umbrales (FR-022) | ✅ |

**Re-check post-diseño:** sin violaciones.
- `eval_full_embeddings`, `eval_chunk_spans` y `eval_build` son tablas solo de evaluación, fuera de `schema.sql`. El esquema de la app no cambia.
- La herramienta Python es la prevista por la constitución («solo para evaluación, ajuste y generación de golden»).

## Project Structure

### Documentation (this feature)

```text
specs/005-offline-eval-harness/
├── spec.md
├── plan.md                  # este fichero
├── research.md              # R1–R13
├── data-model.md            # corpus, índices, conjunto, ejecuciones, informe
├── quickstart.md
├── contracts/
│   ├── testset.md           # reglas T1–T9 y flujo de revisión
│   ├── eval-cli.md          # comandos y reglas E1–E8
│   └── rag-parameters.md    # JSON compartido y reglas P1–P5
├── checklists/requirements.md
└── tasks.md                 # /speckit-tasks
```

### Source Code (repository root)

```text
offline/
├── requirements.txt              # + sqliteai-vector==1.1.2, pypdf==6.20.1 y transitivas fijadas
├── README.md                     # + sección «Evaluación»
├── run_eval.py                   # CLI (contracts/eval-cli.md)
├── eval/
│   ├── __init__.py
│   ├── appconfig.py              # valores esperados leídos del código Kotlin (research R5)
│   ├── models.py                 # rutas y huellas de los modelos; verificación
│   ├── corpus.py                 # manifest, descarga verificada, SOURCES.md
│   ├── boe.py                    # XML consolidado del BOE → Markdown
│   ├── extract.py                # MD, TXT y PDF → RawBlock
│   ├── chunker.py                # port del Chunker del informe, con posiciones
│   ├── index.py                  # construcción de índices y variantes derivadas
│   ├── fts_variants.py           # normalización sin stemmer y sin normalizar (solo evaluación)
│   ├── retrieval.py              # vector, FTS, RRF y regla de seguimiento
│   ├── selector.py               # ContextSelector del informe
│   ├── prompt.py                 # PromptBuilder del informe
│   ├── generate.py               # Gemma 4 (E2B/E4B) con LiteRT-LM, reanudable
│   ├── judge.py                  # juicio automático (research R9)
│   ├── metrics.py                # métricas (research R10)
│   ├── testset.py                # carga, validación (T1–T9), relevantes, hojas de revisión
│   ├── runs.py                   # EvalConfig, huellas y resultados
│   └── report.py                 # informe Markdown
├── tests/                        # + test_chunker, test_extract, test_retrieval, test_selector, test_prompt,
│                                 #   test_judge, test_metrics, test_testset, test_appconfig
├── corpus/{manifest.json, SOURCES.md, docs/}
├── testset/{questions.jsonl, review.csv}
├── config/rag_parameters.json
├── reports/{eval-report.md, manual-judgement.csv}
└── golden/chunker_golden.jsonl

app/src/main/java/com/lisdatasolutions/microrag/domain/rag/RagParameters.kt    # constantes decididas (P2)
app/src/test/java/com/lisdatasolutions/microrag/domain/rag/RagParametersTest.kt
```

**Structure Decision:**
- El pipeline vive en `offline/eval/` como paquete, con una única CLI (`run_eval.py`). Los generadores de F04 siguen en la raíz de `offline/` y no cambian.
- Las tablas de evaluación nunca entran en `schema.sql`.

## Diseño de ejecución

1. **Corpus:** elijo los documentos y compruebo cada aviso legal. `manifest.json` guarda URL, licencia y atribución. `fetch-corpus` descarga y verifica; los documentos del BOE pasan por `boe.py` (XML → Markdown).
2. **Construcción de `ref`:**
   1. extracción;
   2. troceado (350 tokens con la relación medida);
   3. normalización;
   4. embeddings con EmbeddingGemma 2 en CPU, guardando los 768 completos;
   5. `rag_meta` desde `appconfig`;
   6. `eval_build` con huellas y recuentos.

   Las demás variantes se construyen igual o se derivan de `ref` (data-model §3).
3. **Conjunto de prueba:**
   1. muestreo estratificado con semilla;
   2. redacción;
   3. `validate-testset`;
   4. `review-sheet` → el usuario revisa → `review-apply`, hasta tener ≥ 100 revisadas.

   **Parada intermedia:** la revisión del usuario es un paso de la implementación; mientras espera, se avanza en el código de ablaciones y juicio.
4. **Ablaciones de recuperación**, que fijan dimensión, normalización, cabecera, tamaño y regla de seguimiento. Después, las de bloques y vecinos con generación E2B. Por último, E4B en la configuración elegida.
5. **Juicio manual** de la configuración elegida (yo) y una muestra revisada por el usuario. Después, el informe.
6. **Umbrales:**
   - si se alcanzan, se toman las decisiones y se propagan;
   - si no, palancas de FR-022 (trigramas como tercera lista, troceado, cabecera, candidatos, plantilla);
   - si aun así falta alguno, **se para y se consulta al usuario** (D-032).
7. **Propagación:**
   - `rag_parameters.json`, `RagParameters.kt` y `RagParametersTest`;
   - si cambia `dims`, el golden de embeddings y la prueba en el teléfono;
   - `chunker-golden` para F06.

## Riesgos y verificaciones tempranas

Ver research R13. Además:

1. **Primera tarea técnica:** instalar las dependencias nuevas y repetir en el entorno real el experimento de SQLite-Vector de escritorio (carga y distancias) y el de generación. Así se confirma el entorno antes de escribir el pipeline.
2. **Extracción de PDF:** se prueba con los PDF elegidos antes de cerrar el corpus. Un PDF que extraiga mal se sustituye.
3. **La revisión del usuario es un bloqueo externo:** se pide en cuanto el conjunto pasa la validación automática.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
