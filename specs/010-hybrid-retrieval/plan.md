# Implementation Plan: Recuperación híbrida (F10)

**Branch**: `010-hybrid-retrieval` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-hybrid-retrieval/spec.md`

## Summary

F10 da al chat (F11) el contexto de cada pregunta. Usa la recuperación evaluada en F05 (D-034) sobre el índice real de F04/F07.

- **Algoritmos:** port exacto del harness de escritorio a `rag/retrieval`, con la paridad comprobada en la JVM por los casos portados y por un golden de 426 preguntas reales:
  - la regla de seguimiento;
  - la fusión RRF con su desempate;
  - el selector de contexto con la fusión de consecutivos.
- **Orquestación:** `HybridRetriever` en `data/retrieval`.
  1. Comprueba la pregunta, el índice y el modelo.
  2. Lanza la búsqueda léxica a la vez que el embedding de la consulta en CPU.
  3. Hace la búsqueda vectorial.
  4. Carga solo fragmentos que siguen siendo buscables.
  5. Devuelve un resultado tipado, con tiempos y puntuaciones.
- **Concurrencia:** el coordinador de motores permite compartir los motores de embeddings. Una consulta durante una tanda en CPU espera al fragmento en curso, no al final de la tanda.
- **Diagnóstico:** entrada nueva en «MicroRAG Debug». No entra en release.
- **Medición:** en el teléfono de referencia, latencia, búsqueda durante la indexación y paridad con el escritorio sobre 50 preguntas, con el corpus de evaluación en un índice temporal. **Por petición del usuario, todas las pruebas en el teléfono van en la última fase.**
- **D-035:** revisión del orden en el harness. La medición previa da como mejor variante +1,4 puntos de Recall@bloques (research R12), por debajo de la regla de adopción. Se espera mantener D-034 sin cambios en la app.

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva (comprobado en `gradle/libs.versions.toml`). Se usan:
- coroutines 1.11.0;
- Koin 4.2.2;
- Compose BOM 2026.09.00 (diagnóstico);
- `litertlm-android` 0.18.1 (`EmbeddingEngine`);
- `androidx.sqlite:sqlite-bundled` 2.7.1 y `ai.sqlite:vector` 1.1.2;
- kotlinx-serialization 1.12.0, para leer los golden en las pruebas.

Python sin dependencias nuevas. El SQLite del entorno virtual admite FTS5 `trigram` (comprobado, research R12).

**Storage**: sin cambios de esquema (v3). Dos golden nuevos en `offline/golden/`. `offline/corpus` pasa a ser también carpeta de assets de `androidTest`.

**Testing**:
- **JVM:**
  - `FollowUpRuleTest`, `ReciprocalRankFusionTest`, `ContextSelectorTest` y `RetrievalGoldenTest`;
  - `QueryOverflowTest`;
  - `HybridRetrieverTest` y `QueryEmbedderTest`;
  - `EngineCoordinatorTest` ampliado;
  - `RagParametersTest`;
  - diagnóstico (ViewModel y pantalla con Robolectric);
  - `DebugModulesTest`;
  - `LayeringTest`.
- **Python:** `trigram_query`, `rerank`, `FUSED_LIMIT` y la forma de los golden.
- **Instrumentadas en el emulador:**
  - `RagIndexSearchableLoadTest`;
  - `HybridRetrieverVisibilityTest`;
  - las de F04–F09.
- **Instrumentadas con modelos:**
  - `RetrievalBenchmarkTest`, `RetrievalDuringIndexingTest` y `RetrievalParityTest`;
  - en el emulador solo para comprobar que funcionan (CPU, sin umbrales);
  - en el teléfono de referencia, en la última fase.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia A063 (API 35), en la última fase. Emulador `Pixel_10` (API 37).

**Project Type**: mobile-app, módulo único `:app` (D-009). Herramienta de escritorio en `offline/`.

**Performance Goals**:
- **SC-003:** total p95 ≤ 200 ms con 10k fragmentos y el motor cargado; embedding de consulta p95 < 150 ms.
- **SC-007:** p95 ≤ 1 s durante una tanda de indexación.
- **Estimación** con las cifras de F02 y F04: embedding 120 ms, con la léxica (26 ms) solapada; vectorial 23 ms; selección unos 5 ms. Total ≈ 150–170 ms.

**Constraints**:
- **Sin red** (I).
- **Embedding de consulta en CPU** (D-024), en `AppDispatchers.embeddings`; índice en `database`; una sola conexión (IV).
- **Nunca dos motores en GPU** (IV).
- **Logs** solo con cifras e ids (I).
- **Capas:** la interfaz de dominio no importa SQLite ni LiteRT (II).
- **Diagnóstico** solo en `src/debug`.

**Scale/Scope**:
- 3 algoritmos puros y 1 regla de recorte;
- 1 interfaz de dominio con su caso de uso;
- 2 clases de datos;
- 2 parámetros nuevos en `RagIndex`, 1 método nuevo en `TextEmbedder` y el uso compartido en `EngineCoordinator`;
- 1 pantalla de depuración;
- 1 parámetro compartido (`fused_limit`);
- 2 golden y 1 ablación en el harness.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F10 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red (`NetworkConfinementTest` sigue vigente). Los registros de la recuperación solo llevan cifras (Q11, SC-008). Las pruebas usan índices temporales en `cacheDir` y el corpus público del repositorio. Nunca leen la biblioteca del usuario (R14). El diagnóstico muestra texto en pantalla, solo en debug | ✅ |
| II. Stack y arquitectura | Quien llama → `RetrieveContextUseCase` → `ContextRetriever` → `HybridRetriever` (`data/retrieval`) → `RagIndex` y `QueryEmbedder`. LiteRT solo en `rag/runtime/litert` y SQLite solo en `data/rag` (`LayeringTest`). Sin dependencias nuevas; versiones verificadas | ✅ |
| III. RAG según el informe | Híbrida vector + FTS5 con RRF k = 60 y 50 + 50 candidatos. Contexto con D-034, la última evaluación (III enmendada en 1.1.1). Prefijo de consulta y 256D con L2. Sin búsqueda si `rag_meta` no coincide (FR-017). Bloques con ids de fragmentos reales (Q14) | ✅ |
| IV. Rendimiento | Embedding en CPU en su dispatcher. Índice en el suyo, con una sola conexión. Motor bajo demanda, liberado con `releaseUnheld`. Nunca dos motores en GPU (Q13). Léxica solapada con el embedding (Q7). Embedding < 150 ms y criterio INT8 medidos (R10, R13) | ✅ |
| V. Veracidad del estado | «Modelos no listos» con la regla real de F03 (hash verificado y backend preparado). Tiempos y puntuaciones reales. Nunca fragmentos de documentos a medias, excluidos o sombra (FR-015, SC-002) | ✅ |
| VI. Calidad y pruebas | Pruebas unitarias de RRF y selector, obligatorias por la constitución, con paridad con Python (casos y golden). Instrumentadas sobre SQLite-Vector real. Benchmarks con cifras medidas en el teléfono | ✅ |
| VII. UI fiel y accesible | Sin UI de producto. El diagnóstico es una herramienta interna de debug, con `Lisdata*` y tokens (V5–V6) | ✅ |
| VIII. Proceso | Documentación en español y código en inglés. D-049 (clarify) y D-050 (plan). Parada tras analyze. Pruebas en el teléfono al final (petición del usuario) | ✅ |

**Re-check post-diseño:** sin violaciones.
- **El uso compartido de los motores de embeddings** cambia una regla interna de F07 (R4), no un principio. «Nunca dos motores en GPU» y «nunca se cierra un motor en uso» se mantienen (Q13) y se prueban.
- **`fused_limit`** pasa a los parámetros compartidos sin cambiar su valor. No es un cambio de parámetro que pida evaluación (III).

## Project Structure

### Documentation (this feature)

```text
specs/010-hybrid-retrieval/
├── spec.md
├── plan.md                        # este fichero
├── research.md                    # R1–R14
├── data-model.md                  # parámetros, resultado, cambios de interfaces, golden
├── quickstart.md
├── contracts/
│   ├── retrieval-api.md           # dominio, algoritmos, datos, cambios en RagIndex/TextEmbedder/EngineCoordinator; reglas Q1–Q14
│   ├── retrieval-diagnostic.md    # pantalla de depuración; reglas V1–V6
│   └── eval-retrieval.md          # golden, muestra de paridad y revisión del orden en el harness; reglas G1–G6
├── checklists/requirements.md
└── tasks.md                       # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── rag/retrieval/
│   ├── FollowUpRule.kt                         # R2
│   ├── ReciprocalRankFusion.kt                 # R2 (FusedCandidate)
│   └── ContextSelector.kt                      # R2 (mergeWithoutOverlap)
├── rag/runtime/
│   ├── QueryOverflow.kt                        # R4: recorte de la anterior y después de la actual
│   ├── TextEmbedder.kt                         # embedQueryFitting, QueryEmbedding
│   ├── EngineCoordinator.kt                    # uso compartido de embeddings (R5)
│   └── litert/LiteRtTextEmbedder.kt            # embedQueryFitting con el mensaje de entrada larga
├── domain/rag/{RagIndex, RagParameters}.kt     # searchableOnly (R7); FUSED_LIMIT
├── domain/retrieval/
│   ├── RetrievalModels.kt                      # data-model §2–§3
│   ├── ContextRetriever.kt
│   └── RetrieveContextUseCase.kt
├── domain/di/DomainModule.kt                   # RetrieveContextUseCase
├── data/rag/DefaultRagIndex.kt                 # loadChunks/neighbor con searchableOnly
├── data/retrieval/
│   ├── QueryEmbedder.kt                        # R4
│   └── HybridRetriever.kt                      # R6, R8
└── data/di/DataModule.kt                       # QueryEmbedder, ContextRetriever
app/src/debug/java/com/lisdatasolutions/microrag/debug/
├── DebugEntries.kt, DebugModules.kt            # entrada y módulo nuevos
└── retrieval/
    ├── RetrievalDiagnosticModule.kt
    ├── RetrievalDiagnosticViewModel.kt, RetrievalDiagnosticUiState.kt
    └── RetrievalDiagnosticScreen.kt

app/src/test/java/com/lisdatasolutions/microrag/
├── rag/retrieval/{FollowUpRuleTest, ReciprocalRankFusionTest, ContextSelectorTest, RetrievalGoldenTest}.kt
├── rag/runtime/{QueryOverflowTest, EngineCoordinatorTest}.kt
├── data/retrieval/{HybridRetrieverTest, QueryEmbedderTest}.kt
├── domain/rag/RagParametersTest.kt
├── testing/FakeRagIndex.kt                     # búsquedas programables y searchableOnly
└── architecture/LayeringTest.kt                # V1
app/src/testDebug/java/com/lisdatasolutions/microrag/debug/retrieval/
└── {RetrievalDiagnosticViewModelTest, RetrievalDiagnosticScreenTest}.kt
app/src/sharedTest/java/com/lisdatasolutions/microrag/data/indexing/FakeTextEmbedder.kt   # embedQueryFitting
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/rag/RagIndexSearchableLoadTest.kt
├── data/retrieval/HybridRetrieverVisibilityTest.kt
└── data/retrieval/{RetrievalBenchmarkTest, RetrievalDuringIndexingTest, RetrievalParityTest}.kt   # @RequiresModels
app/build.gradle.kts                            # androidTest assets += offline/corpus

offline/
├── config/rag_parameters.json                  # fused_limit
├── eval/{retrieval, runs, pipeline, report}.py # FUSED_LIMIT del JSON, trigramas, reordenación, sección del informe
├── run_eval.py                                 # order-review
├── gen_retrieval_golden.py                     # retrieval_golden.jsonl y retrieval_parity.jsonl (--check)
├── golden/{retrieval_golden, retrieval_parity}.jsonl
├── reports/eval-report.md                      # «Revisión del orden (D-035, F10)»
└── tests/{test_retrieval, test_retrieval_golden}.py
```

`run_eval.py report` compone el informe en `eval/report.py` (verificado), y la sección nueva se añade allí.

**Structure Decision**: módulo único (D-009).
- **`rag/retrieval`:** los algoritmos puros portados, como `rag/ingest` y `rag/text`.
- **`domain/retrieval`:** el contrato.
- **`data/retrieval`:** la orquestación con índice, motor y modelos.
- **`debug/retrieval`:** el diagnóstico.

## Diseño de ejecución

1. **Llamada.** `RetrieveContextUseCase(question, previous)` → `HybridRetriever.retrieve`.
2. **Comprobaciones (Q1):**
   1. pregunta vacía;
   2. seguimiento y texto de búsqueda;
   3. `index.open()`;
   4. `QueryEmbedder.ready()`.
3. **Búsquedas (Q7):**
   - a la vez:
     - `textSearch(searchText, 50)` en `database`;
     - `embed(question, previous si es seguimiento)` en `embeddings`, con el motor CPU (compartido si la ingesta lo tiene);
   - después, `vectorSearch(vector, 50)`.
4. **Selección:**
   1. `ReciprocalRankFusion.fuse([vector, léxica], 60)`;
   2. los 30 primeros;
   3. `loadChunks(ids, searchableOnly = true)`;
   4. `ContextSelector.select(…, neighbor searchableOnly)`.
5. **Resultado:**
   - `RetrievalResult` con el texto de búsqueda, el seguimiento, los recortes, los tiempos y el `RetrievalOutcome`;
   - una línea de registro sin texto.

## Orden de implementación

Por petición del usuario, el teléfono va al final.

1. **Escritorio:** `fused_limit`, golden y muestra de paridad, ablación de la revisión del orden e informe. La decisión de D-035 se registra con las cifras formales.
2. **Algoritmos puros y su paridad en la JVM** (US1, US2).
3. **Índice** (`searchableOnly`), **embedding de la consulta** (recorte) y **coordinador** (uso compartido).
4. **`HybridRetriever`, dominio y DI** (US1–US3), con sus pruebas JVM y en el emulador.
5. **Diagnóstico de depuración** (US4) y prueba de release.
6. **Pruebas con modelos** escritas y comprobadas en el emulador (CPU, sin umbrales).
7. **Última fase, en el teléfono de referencia** (US5):
   - instrumentadas;
   - latencia;
   - búsqueda durante la indexación;
   - paridad;
   - diagnóstico;
   - decisión de INT8;
   - cierre.

## Riesgos y verificaciones tempranas

1. **El port diverge del escritorio** (R2, R3). `RetrievalGoldenTest` se escribe antes que `HybridRetriever`. Un fallo se corrige en Kotlin; el harness es la referencia.
2. **Uso compartido de los motores** (R5). Es el cambio más delicado.
   - Primero las pruebas JVM del coordinador:
     - compartir;
     - cerrar con usuarios;
     - retención;
     - GPU exclusiva;
     - LLM exclusivo.
   - Después, el resto de pruebas de F07 que lo usan (`ChunkEmbeddingSessionTest`, `IngestRunnerTest`, `EngineMemoryCallbacksTest`).
3. **Una variante de D-035 llega a +2 puntos en la ejecución formal** (R12). La medición previa no lo prevé (+1,4). Si ocurre, se para y se consulta: llevarla a la app cambiaría el plan (los trigramas exigirían el esquema v4).
4. **SC-007 con la tanda en CPU** (R5). Con un fragmento de ≈ 0,9 s, el total puede pasar de 1 s. SC-007 se comprueba con la tanda en GPU, que es la del teléfono de referencia. Si en el teléfono la tanda va en CPU, o el p95 en GPU pasa de 1 s, se para y se consulta. El plan B es un dispatcher propio para la consulta (R5).
5. **Paridad ±5 puntos con 50 preguntas** (R11). Cada pregunta vale unos 2 puntos, y los PDF se extraen distinto en el teléfono. Si la diferencia pasa de 5 puntos, se listan las preguntas que cambian, separando PDF de TXT/MD, y se consulta antes de cerrar.
6. **SC-003 con fragmentos ocultos.** Con 4.300 ocultos, la vectorial sube a ≈ 45 ms (F07) y el total se acerca a 200 ms. SC-003 se define sin ocultos; con ellos se mide y se informa (SC-004).
7. **El teléfono no está disponible hasta el final.** Todo lo demás se cierra antes, con las pruebas con modelos comprobadas en el emulador, para que la última fase solo sea medir.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
