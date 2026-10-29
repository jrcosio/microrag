---

description: "Lista de tareas de F10 — Recuperación híbrida"
---

# Tasks: Recuperación híbrida (F10)

**Input**: Documentos de diseño en `/specs/010-hybrid-retrieval/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-010). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario. El orden no es el de prioridad, por dos motivos del plan:
- **US6** (revisión del orden, P3) va **justo después de la base**. Es trabajo de escritorio, y si una variante llegara a +2 puntos cambiaría el resto del plan (riesgo 3).
- **US5** (medición en el teléfono, P2) se divide en dos:
  - las pruebas se escriben y se comprueban en el emulador (fase 9);
  - se ejecutan en el teléfono de referencia en la **última fase**, porque el teléfono no está disponible hasta más tarde (petición del usuario, 2026-10-29).

Historias de la spec:
- US1 — Contexto para una pregunta (P1);
- US2 — Preguntas de seguimiento (P1);
- US3 — Nunca contenido que no se debe usar (P1);
- US4 — Diagnóstico de búsqueda en depuración (P2);
- US5 — Calidad y latencia medidas en el teléfono (P2);
- US6 — Revisión del orden dentro del presupuesto (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emulador `Pixel_10`: `emulator-5554`;
  - teléfono de referencia: `adb -s P31281001066`, **solo en la fase 10**.

  `adb` está en `$HOME/Library/Android/sdk/platform-tools/adb`. **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument`.
- **En el teléfono:**
  - nunca se borran ni se vuelven a descargar los modelos;
  - los registros se filtran a la app;
  - las capturas recortan la barra de estado;
  - ningún documento del usuario aparece en capturas ni en registros;
  - los ficheros de prueba van a una carpeta propia que se borra al terminar.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US6).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/testDebug/`, `…/sharedTest/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup (parámetro compartido, golden y corpus de pruebas)

**Purpose**: los ficheros de escritorio que usan las pruebas de todas las historias.

- [X] T001 Añadir `fused_limit` (R2, data-model §1):
  - `"fused_limit": 30` en `offline/config/rag_parameters.json`, y `"D-050"` en su lista `decisions`;
  - `const val FUSED_LIMIT = 30` en `…/microrag/domain/rag/RagParameters.kt`;
  - la entrada en `…/test/…/domain/rag/RagParametersTest.kt`;
  - en `offline/eval/retrieval.py`, `FUSED_LIMIT` se lee del JSON;
  - `offline/eval/pipeline.py` usa `FUSED_LIMIT` en lugar del `[:30]` literal;
  - una prueba en `offline/tests/test_retrieval.py` comprueba que `FUSED_LIMIT == 30` y que coincide con el JSON.

  Después se ejecutan `RagParametersTest` y `unittest discover -s offline/tests`.
- [X] T002 [P] Crear `offline/tests/test_retrieval_golden.py` (regla G5), que falla mientras no existan los ficheros:
  - **`retrieval_golden.jsonl`:**
    - 426 filas ordenadas por `id`;
    - campos de data-model §6;
    - `fused` con ≤ 30 ids;
    - dos configuraciones de bloques;
  - **`retrieval_parity.jsonl`:**
    - 50 filas: 40 respondibles y 10 de seguimiento;
    - campos de data-model §7;
    - `previous` presente en las de seguimiento.
- [X] T003 Crear `offline/gen_retrieval_golden.py` (contrato `eval-retrieval.md`, reglas G1–G4), sobre `index.connect` de `ref`, la caché `build/query_embeddings.json`, `Retriever` y `ContextSelector` de escritorio:
  - **`retrieval_golden.jsonl`:**
    - las 426 preguntas vivas, con la configuración `d034` leída del JSON y la `report` (6, 3, 2200, 2);
    - para cada candidato fusionado, `[document_id, ordinal, token_estimate, next_id]`, y también para su siguiente;
  - **`retrieval_parity.jsonl`:**
    - semilla `20261029`;
    - acierto de escritorio con `metrics.blocks_hit` y los relevantes de `relevant_ids`;
  - **`--check`:** regenera en memoria y falla si difiere.
  - Si falta un texto en la caché, carga el modelo con la comprobación de huella de `gen_embedding_golden.py`.

  Genera los dos ficheros en `offline/golden/`, y T002 pasa. Se ejecuta `--check` dos veces sin cambios en `git diff` (G1).
- [X] T004 [P] Añadir `offline/corpus` como carpeta de assets de `androidTest` en `app/build.gradle.kts`, junto a `offline/golden` (R11). Con `unzip -l` se comprueba que el APK de pruebas contiene `docs/*.pdf|md|txt` y `manifest.json`, y que el APK de la app no.
- [X] T005 [P] Crear `…/sharedTest/…/rag/retrieval/RetrievalGoldenFiles.kt`, que lee los dos golden con kotlinx-serialization para la JVM (`../offline/golden`) y para el dispositivo (assets de prueba), como `GoldenFiles` (F06). Tipos: `GoldenRetrievalCase` y `ParityQuestion`.

**Checkpoint**: los golden existen, son deterministas y se pueden leer desde las dos clases de pruebas.

---

## Phase 2: Foundational (bloquea todas las historias de la app)

**Purpose**: los contratos de dominio y los tres cambios en piezas existentes (índice, embedder y coordinador). El del coordinador es el más delicado (riesgo 2), así que va con sus pruebas antes de nada que lo use.

- [X] T006 [P] Crear los modelos y el contrato de dominio (data-model §2–§3, contrato `retrieval-api.md`):
  - `…/microrag/domain/retrieval/RetrievalModels.kt`: `RetrievalRequest`, `RetrievalResult`, `RetrievalOutcome`, `ContextBlock`, `CandidateScore` y `RetrievalTimings`;
  - `ContextRetriever.kt`;
  - `RetrieveContextUseCase.kt`;
  - `RetrieveContextUseCase` en `…/microrag/domain/di/DomainModule.kt`, junto con T025: sin el `ContextRetriever` de `DataModule`, `AppModulesTest` fallaría.
- [X] T007 [P] Crear `…/androidTest/…/data/rag/RagIndexSearchableLoadTest.kt` sobre SQLite real (R7):
  - documentos indexado e incluido, indexado y excluido, pendiente con fragmentos, con error, fuera del índice y sombra de reindexado;
  - `loadChunks(ids, searchableOnly = true)` y `neighbor(…, searchableOnly = true)` solo devuelven los del primero;
  - con `false`, todos los existentes, como hoy;
  - tras `setIncluded(false)`, `removeFromIndex` y `publishReindex`, el filtro sigue la regla.
- [X] T008 Implementar `searchableOnly` (R7):
  - en `…/microrag/domain/rag/RagIndex.kt` y en `…/microrag/data/rag/DefaultRagIndex.kt` (`loadBlock` y `neighbor` con `AND d.status = 'done' AND d.included = 1` cuando se pide);
  - en `…/test/…/testing/FakeRagIndex.kt`, la misma regla, más búsquedas programables (`vectorResults`, `textResults`, `searchError`) y un registro de llamadas (`calls`), para las pruebas del recuperador.

  T007 pasa en el emulador, y siguen en verde las pruebas de F04–F09 que usan el índice.
- [X] T009 [P] Crear `…/test/…/rag/runtime/QueryOverflowTest.kt` (R4):
  - se recorta primero la anterior con `EmbeddingOverflow.cut`;
  - si queda vacía, se quita;
  - después se recorta la actual;
  - el texto resultante siempre conserva la actual mientras haya anterior;
  - como mucho `MAX_ATTEMPTS` pasos.
- [X] T010 Implementar el recorte de la consulta (R4, contrato `retrieval-api.md`):
  - `…/microrag/rag/runtime/QueryOverflow.kt`;
  - `QueryEmbedding` y `embedQueryFitting` (con su implementación por defecto) en `…/microrag/rag/runtime/TextEmbedder.kt`;
  - `QueryOverflow.join(previous, question)`, la unión con un espacio que también usará `FollowUpRule.searchText`;
  - en `…/microrag/rag/runtime/litert/LiteRtTextEmbedder.kt`, el override con el bucle de recorte en `dispatcher`, el mensaje de entrada larga y las excepciones `EmbeddingInputTooLongException` y `EmbeddingEngineException`;
  - en `…/sharedTest/…/data/indexing/FakeTextEmbedder.kt`, el override con `maxInputChars`, `failEngineTimes` y `beforeEmbedding`, también para consultas.

  T009 pasa, y `ModelCompatibilityCheckerTest` y las pruebas de F02 siguen en verde.
- [X] T011 [P] Ampliar `…/test/…/rag/runtime/EngineCoordinatorTest.kt` con la regla Q13 (R5):
  - un bloque usa el motor de embeddings que la ingesta retiene (`withHeldEngine` bloqueado) y termina sin esperar a que acabe el bloque retenido;
  - `release` y `releaseUnheld` esperan a que terminen todos los usuarios;
  - `releaseUnheld` no cierra el motor retenido aunque termine el otro usuario;
  - dos usuarios del LLM siguen siendo exclusivos;
  - nunca dos motores en GPU, aunque uno se use compartido;
  - mientras una entrada se cierra no entran usuarios nuevos.
- [X] T012 Implementar el uso compartido en `…/microrag/rag/runtime/EngineCoordinator.kt` (data-model §5):
  - recuento de usuarios para los motores de embeddings y `Mutex` para el LLM;
  - recuento de retenciones;
  - `closeLocked` espera a que no haya usuarios;
  - `otherGpuInUse` según el recuento.
  - Se actualiza el KDoc de la clase con la regla nueva.

  T011 pasa, y siguen en verde `ChunkEmbeddingSessionTest`, `IngestRunnerTest`, `EngineMemoryCallbacksTest`, `DefaultIndexingRepositoryTest` y el resto de `EngineCoordinatorTest`. **Si una prueba de F07 falla por el cambio y no se puede explicar, se para y se consulta** (riesgo 2).
- [X] T013 Crear `…/test/…/data/retrieval/EngineQueryEmbedderTest.kt` (R4; después de T010, que trae `QueryEmbedding` y el `FakeTextEmbedder` de consultas), con `InMemoryModelFileRepository` (`testing/OnboardingFakes.kt`), un `EngineFactory` falso que da `FakeTextEmbedder` y un `EngineCoordinator` real:
  - **`ready()`:**
    - `Verified` y preparado → `true`;
    - `Unverified`, `Missing` o sin backend → `false`;
    - con `pathOf` nulo llama a `refresh()` una sola vez;
    - nunca llama a `verify`;
  - **`embed`:**
    - usa `EngineKey(EG2_270M, CPU)` con `withEngine`, nunca `withHeldEngine`;
    - carga una vez y reutiliza;
    - `loaded` refleja el estado;
    - tras `releaseUnheld` (UI oculta o poca memoria) el motor se cierra, y la siguiente consulta lo vuelve a cargar con `loaded = false` antes (FR-021);
    - un fallo de carga o de cálculo se propaga como excepción.
- [X] T014 Crear `…/microrag/data/retrieval/QueryEmbedder.kt` (interfaz) y `EngineQueryEmbedder.kt` (R4). T013 pasa.

**Checkpoint**: índice, embedder y coordinador listos. Las pruebas de F04–F09 siguen en verde.

---

## Phase 3: User Story 6 - Revisión del orden dentro del presupuesto (Priority: P3) — escritorio, adelantada por el riesgo 3

**Goal**: evaluar en el harness los trigramas como tercera lista y la reordenación, y documentar la decisión (FR-028, SC-010).

**Independent Test**: `run_eval.py order-review` da la tabla de variantes sobre las 370 preguntas, `eval-report.md` tiene su sección y DECISIONS la decisión.

- [X] T015 [P] [US6] Ampliar `offline/tests/test_retrieval.py` (G5):
  - **`trigram_query`:**
    - términos ≥ 3 caracteres;
    - sin tildes ni palabras vacías;
    - distintos;
    - como mucho 16;
    - entre comillas y unidos con `OR`;
    - vacío sin términos;
  - **`rerank`** con un caso a mano:
    - α = 0,5;
    - min-max por lista;
    - 0 para quien falta en una lista;
    - solo los `top` primeros;
    - el resto en su orden;
  - **`EvalConfig`:** con los campos nuevos en su valor por defecto, la huella de la configuración de D-034 no cambia (`LATE_FIELDS`).
- [X] T016 [US6] Implementar en el harness (contrato `eval-retrieval.md`):
  - `offline/eval/runs.py`: `third_list`, `rerank_alpha` y `rerank_top` en `LATE_FIELDS`;
  - `offline/eval/retrieval.py`: `trigram_query`, `Retriever.trigram` (tabla `trigram` en una base adjunta en memoria, regla G3), distancias y puntuaciones BM25 para la reordenación, y `rerank`;
  - `offline/eval/pipeline.py`: la tercera lista en la fusión y la reordenación antes de tomar `FUSED_LIMIT`;
  - `offline/run_eval.py`: el comando `order-review`, que escribe `offline/build/order_review.json`.

  T015 pasa.
- [X] T017 [US6] Ejecutar `order-review` con las variantes de R12 y comparar con la medición previa:
  - D-034 debe dar 0,889.
  - **Si alguna variante llega a ≥ +2 puntos de Recall@bloques, se para y se consulta** (riesgo 3, regla G6). Según el clarify, F10 la llevaría a la app; la consulta es para replanificar esa parte (los trigramas exigirían el esquema v4).
  - Si no, se sigue.
- [X] T018 [US6] Añadir a `offline/eval/report.py` la sección «Revisión del orden (D-035, F10)» (tabla, regla de adopción y decisión) y regenerar `offline/reports/eval-report.md` con `run_eval.py report`.
  - El `git diff` solo añade la sección y la fila de `fused_limit` en «Configuración elegida».
- [X] T019 [US6] Registrar D-051 en `docs/roadmap/DECISIONS.md` con la decisión:
  - las cifras formales;
  - se mantiene D-034 en la app;
  - la reordenación queda anotada como palanca para F14.

  Se actualiza la ficha de F10 en `docs/roadmap/FEATURES.md`.

**Checkpoint**: D-035 resuelta en el escritorio. La app sigue con D-034.

---

## Phase 4: User Story 1 - Contexto para una pregunta (Priority: P1) 🎯 MVP

**Goal**: dada una pregunta, devolver los bloques de contexto con la fusión y la selección del escritorio (FR-001–FR-006, FR-010–FR-014, FR-019).

**Independent Test**: en la JVM, `HybridRetrieverTest` (camino principal) y `RetrievalGoldenTest` reproducen el escritorio.

- [X] T020 [P] [US1] Crear `…/test/…/rag/retrieval/ReciprocalRankFusionTest.kt` con los casos de `test_retrieval.py`:
  - `[[a, b, c], [b, d, a]]` da `[b, a, d, c]`;
  - el empate `[x, y]`;
  - también, rangos desde 1, puntuación y rango por lista expuestos, y un id que solo está en una lista.
- [X] T021 [P] [US1] Crear `…/test/…/rag/retrieval/ContextSelectorTest.kt`:
  - **los 5 casos de `test_selector.py`:**
    - 3 por documento;
    - vecino y fusión;
    - párrafo repetido;
    - presupuesto y límite;
    - orden;
  - **casos propios:**
    - vecino ya elegido;
    - vecino que no cabe;
    - `expandTop = 0`;
    - un fragmento que no cabe no detiene el recorrido;
    - `chunkIds` en orden;
    - `mergeWithoutOverlap` con y sin prefijo y con texto vacío.
- [X] T022 [P] [US1] Crear `…/test/…/rag/retrieval/RetrievalGoldenTest.kt`. Para las 426 filas de `retrieval_golden.jsonl`:
  - `fuse([vector, fts], 60).take(30)` es igual a `fused`;
  - `ContextSelector` con `d034` y con `report` da los bloques esperados (documento, primer y último ordinal, tokens e ids). Los `ChunkView` se construyen desde `chunks`, con contenido de relleno, y el vecino desde `next_id`.
- [X] T023 [US1] Implementar `…/microrag/rag/retrieval/ReciprocalRankFusion.kt` (`FusedCandidate`) y `…/microrag/rag/retrieval/ContextSelector.kt` (R2). T020, T021 y T022 pasan. **Si el golden falla, se corrige el port, nunca el golden** (riesgo 1).
- [X] T024 [P] [US1] Crear `…/test/…/data/retrieval/HybridRetrieverTest.kt` (camino principal), con `FakeRagIndex` programable y un `QueryEmbedder` falso sobre `FakeTextEmbedder`:
  - 50 + 50 candidatos;
  - fusión;
  - los 30 primeros cargados con `searchableOnly = true`;
  - bloques con documento, ruta, página, ordinales, ids, texto y tokens (Q14);
  - `candidates` con rango de fusión, puntuación, rango y distancia vectorial, rango léxico y `selected`;
  - tiempos ≥ 0 y `coldEngine`;
  - la búsqueda léxica empieza antes de que termine el embedding (Q7, con `beforeEmbedding` bloqueado);
  - una pregunta solo con palabras vacías da léxica vacía, y la vectorial decide (escenario US1-6);
  - un índice sin documentos buscables da `NoResults` (US1-7).
- [X] T025 [US1] Implementar `…/microrag/data/retrieval/HybridRetriever.kt`, camino principal (R6), y registrar en `…/microrag/data/di/DataModule.kt` `QueryEmbedder` → `EngineQueryEmbedder` y `ContextRetriever` → `HybridRetriever`. T024 pasa, y también `AppModulesTest` y `DebugModulesTest`.

**Checkpoint**: la recuperación funciona en la JVM con el algoritmo del escritorio.

---

## Phase 5: User Story 2 - Preguntas de seguimiento (Priority: P1)

**Goal**: buscar con la pregunta anterior cuando la actual es un seguimiento (FR-007–FR-009).

**Independent Test**: `FollowUpRuleTest`, las 426 filas del golden y `HybridRetrieverTest` (seguimiento).

- [X] T026 [P] [US2] Crear `…/test/…/rag/retrieval/FollowUpRuleTest.kt`:
  - **casos de `test_retrieval.py`:**
    - corta;
    - con conector;
    - larga sin conector;
    - «¿Cada cuánto se revisa la bomba? ¿Y el aceite?»;
  - **casos propios:**
    - cada conector sin distinguir mayúsculas y con espacios iniciales;
    - anterior nula o en blanco;
    - regla desactivada;
    - 7 frente a 8 términos.
- [X] T027 [P] [US2] Ampliar `…/test/…/rag/retrieval/RetrievalGoldenTest.kt`: `follow_up` y `search_text` de las 426 filas.
- [X] T028 [P] [US2] Ampliar `HybridRetrieverTest.kt`:
  - un seguimiento busca con «anterior + espacio + actual» en la léxica, y el embedder recibe la anterior y la actual;
  - una larga sin conector se busca sola;
  - un seguimiento sin anterior se busca solo;
  - con `maxInputChars`, se recorta antes la anterior, la léxica recibe el texto completo y `embeddingCuts` lo informa (Q8).
- [X] T029 [US2] Completar `…/microrag/rag/retrieval/FollowUpRule.kt` (`isFollowUp` y `searchText`) e integrarlo en `HybridRetriever`. T026, T027 y T028 pasan.

**Checkpoint**: las preguntas de seguimiento se buscan como en el escritorio.

---

## Phase 6: User Story 3 - Nunca contenido que no se debe usar (Priority: P1)

**Goal**: solo fragmentos buscables; sin búsqueda con el índice incompatible o los modelos sin preparar; resultado tipado; búsqueda durante la indexación (FR-015–FR-022, FR-029, FR-030).

**Independent Test**: `HybridRetrieverTest` (estados), `RetrievalDuringIngestTest` en la JVM y `HybridRetrieverVisibilityTest` en el emulador.

- [X] T030 [P] [US3] Ampliar `HybridRetrieverTest.kt` (reglas Q1, Q9–Q11):
  - **orden de las comprobaciones:**
    - pregunta vacía o en blanco → `EmptyQuestion`, sin abrir el índice ni llamar al embedder;
    - índice `Incompatible` o `Unreadable` → `IndexUnavailable` con el estado, sin `ready()` ni `embed`;
    - modelo no listo → `ModelsNotReady`, sin `embed`;
  - **errores:**
    - `IndexUnavailableException` durante la búsqueda → `IndexUnavailable`;
    - otra excepción del índice → `IndexUnavailable(Unreadable("search: …"))`;
    - fallo de carga o de cálculo del embedder → `EmbeddingFailed`;
    - `CancellationException` se propaga;
  - **datos y concurrencia:**
    - candidatos que dejan de ser buscables entre la búsqueda y la carga no llegan a los bloques (US3-3);
    - dos llamadas simultáneas con preguntas distintas reciben cada una lo suyo (FR-022);
  - **registro:**
    - una línea `retrieval …` por llamada;
    - el `CapturingLogger` no contiene la pregunta, la anterior, el texto de búsqueda, el texto de los fragmentos ni los nombres de documento (SC-008).
- [X] T031 [US3] Completar `HybridRetriever` con las comprobaciones, los errores y el registro de R6 y R8. T030 pasa.
- [X] T032 [P] [US3] Crear `…/test/…/data/retrieval/RetrievalDuringIngestTest.kt` (FR-020, regla Q6):
  - `EngineCoordinator` real;
  - la ingesta retiene `(EG2_270M, CPU)` con `withHeldEngine` y un `FakeTextEmbedder` cuyo `beforeEmbedding` bloquea un «fragmento» cada vez;
  - una recuperación con `EngineQueryEmbedder` comparte el motor y termina mientras el bloque retenido sigue activo, esperando como mucho al fragmento bloqueado.
- [X] T033 [P] [US3] Crear `…/androidTest/…/data/retrieval/HybridRetrieverVisibilityTest.kt` sobre `TempRagIndex` (SQLite-Vector real), con un `QueryEmbedder` determinista (`FakeTextEmbedder`):
  - **todos los estados con el mismo texto:** documentos excluido, pendiente con fragmentos, con error, en pausa, fuera del índice, sombra de reindexado y uno a medias con 4.300 fragmentos ocultos, todos con fragmentos que coinciden con la pregunta. Los bloques solo contienen fragmentos de los indexados e incluidos (SC-002, US3-1);
  - **con los ocultos:** cada búsqueda sigue dando hasta 50 candidatos (US3-2);
  - **exclusión:** al excluir el que aparecía y repetir, desaparece;
  - **índice incompatible:** con `rag_meta` alterado (como `RagIndexCompatibilityTest`), `IndexUnavailable` sin llamadas al embedder (SC-006).
- [X] T034 [US3] Ejecutar en el emulador las instrumentadas sin modelos (quickstart §3). T007 y T033 pasan, y las de F04–F09 siguen en verde.

**Checkpoint**: la recuperación es segura y tipada. US1–US3 completas sin el teléfono.

---

## Phase 7: User Story 4 - Diagnóstico de búsqueda en depuración (Priority: P2)

**Goal**: la herramienta de diagnóstico en «MicroRAG Debug», fuera de release (FR-023, FR-024).

**Independent Test**: las pruebas del ViewModel y de la pantalla, `LayeringTest` y la búsqueda manual en el emulador.

- [X] T035 [P] [US4] Crear `…/testDebug/…/debug/retrieval/RetrievalDiagnosticViewModelTest.kt`:
  - `Idle` → `Searching` → `Done`;
  - una segunda pulsación durante la búsqueda se ignora;
  - una anterior en blanco se pasa como `null`;
  - cada `RetrievalOutcome` queda en `Done`;
  - el ViewModel no registra nada (V3).
- [X] T036 [P] [US4] Crear `…/testDebug/…/debug/retrieval/RetrievalDiagnosticScreenTest.kt` (Robolectric), con el contenido del contrato `retrieval-diagnostic.md`:
  - un estado por cada resultado (bloques, sin resultados, vacía, índice no disponible, modelos no listos y fallo del embedding);
  - candidatos con «—» cuando no están en una lista;
  - la fila de recortes solo con N > 0;
  - tiempos con un decimal;
  - botón desactivado e indicador anunciado como «Buscando» durante la búsqueda;
  - campos con etiqueta y objetivos ≥ 48 dp (V6).
- [X] T037 [US4] Implementar `…/debug/…/debug/retrieval/`:
  - `RetrievalDiagnosticUiState.kt`;
  - `RetrievalDiagnosticViewModel.kt`;
  - `RetrievalDiagnosticScreen.kt` (`LisdataTopBar`, dos `LisdataSearchField`, `LisdataGradientButton` con `loading`, `LisdataCard` y tokens, V5);
  - `RetrievalDiagnosticModule.kt`.

  Además, la entrada «Diagnóstico de búsqueda» en `…/debug/…/debug/DebugEntries.kt` y el módulo en `DebugModules.kt`. T035, T036 y `DebugModulesTest` pasan.
- [X] T038 [P] [US4] Ampliar `…/test/…/architecture/LayeringTest.kt` (V1): ningún fichero de `src/main` ni de `src/release` contiene `debug.retrieval`.
- [X] T039 [US4] Verificación manual en el emulador (quickstart §4):
  - pregunta normal;
  - seguimiento «¿Y el aceite?»;
  - pregunta vacía;
  - `logcat` de la app con solo cifras.

  Los documentos de prueba del emulador van a una carpeta propia y se borran al terminar.

**Checkpoint**: el diagnóstico funciona en el emulador y no está en release.

---

## Phase 8: User Story 5 - Pruebas con modelos, preparadas en el emulador (Priority: P2)

**Goal**: dejar escritas y comprobadas las tres pruebas del teléfono, para que la última fase solo sea medir (plan, riesgo 7).

**Independent Test**: las tres pruebas compilan y, si el emulador tiene los modelos, terminan en CPU y registran sus cifras sin umbrales. Sin modelos, se saltan limpiamente.

- [X] T040 [P] [US5] Crear `…/androidTest/…/data/retrieval/RetrievalBenchmarkTest.kt` (`@RequiresModels`, R10, FR-025):
  - 10.000 fragmentos sintéticos (como `RagIndexBenchmarkTest`);
  - las 50 preguntas de `retrieval_parity.jsonl`;
  - `LiteRtEngineFactory` real en CPU con `EngineQueryEmbedder` y `HybridRetriever`;
  - **mediciones:**
    - la primera recuperación con el motor sin cargar;
    - 2 vueltas con p50 y p95 de embedding, vector, léxica, selección y total (`Percentiles`);
    - lo mismo con 4.300 ocultos;
  - registro `retrieval-benchmark`;
  - **umbrales solo si `Build.MODEL == "A063"`:** total p95 ≤ 200 ms y embedding p95 < 150 ms (SC-003).
- [X] T041 [P] [US5] Crear `…/androidTest/…/data/retrieval/RetrievalDuringIndexingTest.kt` (`@RequiresModels`, R10, FR-020):
  - 1.000 fragmentos sintéticos publicados;
  - `IngestRunner` con `ChunkEmbeddingSession` real indexando `pdf/text_200_pages.pdf` en paralelo;
  - una recuperación cada 500 ms durante la fase de embedding;
  - cada una termina antes que la tanda;
  - registro `retrieval-during-indexing` con el backend;
  - **variante de tanda en CPU:** `isEmulator = true`;
  - **umbral solo en el A063 con la tanda en GPU:** p95 ≤ 1 s (SC-007).
- [X] T042 [P] [US5] Crear `…/androidTest/…/data/retrieval/RetrievalParityTest.kt` (`@RequiresModels`, R11, FR-027):
  1. corpus de los assets a `cacheDir/parity-<uuid>`;
  2. documentos registrados con `«título del manifiesto».<ext>`;
  3. `IngestRunner` con `ChunkEmbeddingSession` real sobre `TempRagIndex`;
  4. las 50 preguntas con el `HybridRetriever` real;
  5. relevantes con el port de prueba de `derive_relevant`/`norm_ws` (espacios de `PyText`);
  6. `blocks_hit`;
  7. no extraídas aparte;
  8. |teléfono − escritorio| ≤ 0,05 sobre las mismas preguntas;
  9. registro `retrieval-parity` solo con cifras e ids;
  10. el `CapturingLogger` no contiene el texto de ninguna pregunta (SC-008);
  11. borra la copia y el índice al terminar (R14).
- [X] T043 [US5] Ejecutar T040–T042 en el emulador (quickstart §3, «Con modelos en el emulador»):
  - si tiene los modelos, terminan en CPU y registran cifras (sin umbrales; la paridad en CPU es informativa);
  - si no, se comprueba que se saltan con su `assumeTrue`.

**Checkpoint**: las pruebas del teléfono están listas para la última fase.

---

## Phase 9: Polish sin teléfono

**Purpose**: dejar todo cerrado salvo las medidas del teléfono.

- [X] T044 [P] Documentación sin teléfono:
  - **`docs/roadmap/INVENTARIO.md`:** G28 ya dice que F10 hereda las búsquedas filtradas. Se añade que la carga de candidatos también filtra (R7). G25 (FTS solo en español) se mantiene;
  - **`offline/README.md`:** el golden de recuperación y la muestra de paridad (`gen_retrieval_golden.py`, con `--check`), su prueba en la app (`RetrievalGoldenTest`, `RetrievalParityTest`) y el comando `order-review` en la tabla de evaluación.
- [ ] T045 Puertas de calidad (quickstart §1–3, 5):
  - `gen_retrieval_golden.py --check`;
  - pruebas Python;
  - `assembleDebug`, `assembleRelease`, `lintDebug` y `lintRelease` («No issues found»);
  - `testDebugUnitTest`;
  - instrumentadas sin modelos en el emulador;
  - `apkanalyzer dex packages` del APK de release sin `debug.retrieval` (SC-009).
- [ ] T046 Borrador de `specs/010-hybrid-retrieval/report.md`:
  - resumen;
  - build, lint y pruebas;
  - D-035 con sus cifras;
  - diferencias frente al mockup: ninguna (sin UI de producto);
  - un hueco para las cifras del teléfono, marcado como pendiente.

**Checkpoint**: todo lo que no necesita el teléfono está hecho. **Se avisa al usuario de que F10 espera al teléfono.**

---

## Phase 10: Última fase — teléfono de referencia (US5) y cierre

**Purpose**: las medidas de US5 en el A063, la decisión de INT8 y el cierre. Solo cuando el usuario diga que el teléfono está disponible.

- [ ] T047 [US5] En el teléfono: instalar los dos APK y ejecutar las instrumentadas sin modelos (quickstart §6). Deben quedar en verde.
- [ ] T048 [US5] `RetrievalBenchmarkTest` en el teléfono (SC-003, SC-004):
  - anotar p50 y p95 de cada paso con y sin ocultos, y la primera búsqueda en frío;
  - si el total p95 pasa de 200 ms o el embedding de 150 ms, se para y se consulta.
- [ ] T049 [US5] `RetrievalDuringIndexingTest` en el teléfono (SC-007): p95 con la tanda en GPU y la cifra con la tanda en CPU. **Si en GPU pasa de 1 s, o el teléfono indexa en CPU, se para y se consulta** (riesgo 4).
- [ ] T050 [US5] `RetrievalParityTest` en el teléfono (SC-005): Recall@bloques del teléfono y del escritorio, no extraídas y preguntas que cambian. **Si la diferencia pasa de 5 puntos, se listan los cambios (PDF frente a TXT/MD) y se consulta** (riesgo 5).
- [ ] T051 [US5] Diagnóstico en el teléfono (quickstart §6), sin mostrar documentos del usuario:
  - pregunta sin resultados, o un documento de prueba en una carpeta propia que se borra al terminar;
  - capturas sin la barra de estado.
- [ ] T052 Cierre (Definición de terminado):
  - `report.md` completo con las cifras del teléfono, el dispositivo, la versión de Android y las de LiteRT-LM, SQLite-Vector y la app (constitución VI);
  - `FEATURES.md` con F10 pendiente de aprobación;
  - la propuesta de INT8 para F14 con las cifras de T048 (FR-026, R13), que se presenta al usuario.

  **Parada para la revisión de cierre.** La decisión de INT8 se registra en DECISIONS con la respuesta del usuario.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **Foundational (fase 2):** depende de T001 (`FUSED_LIMIT`). **Bloquea US1–US5.** T012 puede parar el trabajo (riesgo 2).
- **US6 (fase 3):** depende de T001. Es independiente de la app y va antes porque **T017 puede parar el trabajo** (riesgo 3).
- **US1 (fase 4):** depende de la fase 2 y de T003/T005 (golden). Es el MVP.
- **US2 (fase 5):** depende de US1 (`HybridRetriever`, `RetrievalGoldenTest`).
- **US3 (fase 6):** depende de US1. Puede ir antes o después de US2; las dos tocan `HybridRetriever` y su prueba, así que una detrás de otra.
- **US4 (fase 7):** depende de US1–US3, porque muestra todos los resultados.
- **US5 en el emulador (fase 8):** depende de US1–US3 y de T004/T005.
- **Polish sin teléfono (fase 9):** depende de US1–US4, US6 y la fase 8, para que las puertas de calidad cubran también las pruebas nuevas.
- **Última fase (10):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Algoritmos puros antes que el orquestador; el orquestador antes que el diagnóstico.
- Las paradas de consulta son T012, T017, T048, T049 y T050.

### Parallel Opportunities

- **Setup:** T002, T004 y T005 (T003 después de T002).
- **Foundational:** T006, T007, T009 y T011 (pruebas y contratos en ficheros distintos). T013 va después de T010.
- **US6:** T015 en paralelo con la fase 2.
- **US1:** T020, T021, T022 y T024.
- **US2:** T026, T027 y T028.
- **US3:** T030, T032 y T033.
- **US4:** T035, T036 y T038.
- **US5 en el emulador:** T040, T041 y T042.

## Parallel Example: User Story 1

```text
T020 ReciprocalRankFusionTest   T021 ContextSelectorTest   T022 RetrievalGoldenTest   T024 HybridRetrieverTest (camino principal)
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1 y 2, con el coordinador verificado (T012).
2. Fase 3, con D-035 resuelta en el escritorio (T017).
3. US1: la recuperación reproduce el escritorio en la JVM. **Parar y validar** con `RetrievalGoldenTest`.

### Incremental Delivery

1. **US2:** seguimiento.
2. **US3:** estados, visibilidad y búsqueda durante la ingesta.
3. **US4:** diagnóstico.
4. **US5 preparada en el emulador** y **polish sin teléfono**. Se avisa al usuario.
5. **Última fase en el teléfono:** medidas, INT8 y cierre.
