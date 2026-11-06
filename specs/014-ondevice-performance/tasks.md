---

description: "Lista de tareas de F14 — Rendimiento en dispositivo y decisión INT8"
---

# Tasks: Rendimiento en dispositivo y decisión INT8 (F14)

**Input**: Documentos de diseño en `/specs/014-ondevice-performance/`:
- [plan.md](plan.md) y [spec.md](spec.md);
- [research.md](research.md) y [data-model.md](data-model.md);
- [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-010). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero. Los bancos son pruebas instrumentadas que solo corren en el teléfono.

**Organización**: por historia de usuario. US2 va antes que US1 porque US1 mide la ruta INT8 que construye US2. Todo lo que necesita el teléfono va en la **fase 11**.

**Historias de la spec:**
- US1 — La búsqueda con 10k, 50k y 100k fragmentos (P1);
- US2 — INT8 y precarga en la app (P1);
- US3 — El primer token, medido de verdad (P1);
- US4 — Palancas para bajar el primer token (P2);
- US5 — Cifras fiables: fp16 y estimación de tokens (P2);
- US6 — El modo multimodal, medido y ajustado (P2);
- US7 — E4B frente a E2B en el teléfono (P3);
- US8 — Palancas de calidad baratas (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33`, `MicroRAG_API32` y `Pixel_10`;
  - teléfono `adb -s P31281001066`, **solo en la fase 11 y con permiso**.
- **Con el teléfono conectado:**
  - nunca `connectedDebugAndroidTest`. Siempre se instalan los APK con `-s <dispositivo>` y se lanza `am instrument -r`, con `-e notAnnotation …RequiresNetwork`;
  - antes de una batería, `wm dismiss-keyguard`, comprobar `isKeyguardShowing=false` y `svc power stayon usb` (y al terminar, `stay_on` a 0).
- **En el teléfono** (memorias `f11-phone-phase`, `f16-phone-pending`, `f17-closing`, `f18-closing`, `f19-closing` y `f20-closing`):
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - el A063 está en la 440M con el modo activado. La 270M y E4B solo se copian a `dev-models` para medir (D-088) y se borran al terminar;
  - al terminar se restauran los ajustes.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US8).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup (runtimes, R1)

**Purpose**: subir los runtimes al empezar (D-088), para que todo F14 se mida con ellos.

- [ ] T001 Subir `litertlm` a 0.19.1 y `sqliteVector` a 1.2.0 en `gradle/libs.versions.toml`. Pasar `enableSpeculativeDecoding` (obsoleto) a `SpeculativeDecodingConfig(OFF)` en `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt`, y compilar sin avisos nuevos
- [ ] T002 Comprobar con `javap` sobre el AAR 0.19.1 lo que R6 y R7 dejan «a verificar»: si `Session` decodifica en streaming y se puede clonar tras el prefill, cómo se leen los tokens de la plantilla de chat de Gemma 4 (`ModelInfo` o la plantilla del fichero, en el escritorio con `litert-lm-api`) y la función de `ModelInfo` que dice si hay drafter. Anotar el resultado y el camino elegido (1 o 2) en `research.md` R6 y R7
- [ ] T003 [P] Subir `litert-lm-api` a 0.19.1 y añadir `snowballstemmer` en `offline/requirements.txt`. Pasar `pytest` y `run_eval.py ablations`, y comprobar que da las mismas cifras que en F20 (FR-038). Si no, volver a 0.18.1 y anotarlo en `research.md` R1
- [ ] T004 JVM (`testDebugUnitTest`), lint (`lintDebug`: desaparecen los avisos de versión de LiteRT-LM y SQLite-Vector) y la batería instrumentada sin modelos en `MicroRAG_API33` y `Pixel_10`, con los runtimes nuevos. Las goldens con modelos quedan para T046

---

## Phase 2: Foundational (kit de medida y bancos)

**Purpose**: lo que usan todos los bancos. Ninguna historia de medida empieza sin esto.

- [ ] T005 Congelar las reglas: `offline/config/f14_rules.json` (contrato H3), con la regla de la especulativa que decida la parada del analyze, en un **commit propio antes de cualquier evaluación**
- [ ] T006 [P] Kit de medida en `…/androidTest/testing/bench/`: `DeviceConditions.kt`, `Percentiles.kt` y `BenchReport.kt` (contrato B1, data-model §4). `…/androidTest/testing/bench/BenchReportTest.kt`, sin modelos, comprueba en el emulador el JSON, los percentiles y las condiciones; se escribe antes y falla primero
- [ ] T007 [P] `offline/eval/bench_bank.py`, con `bench-bank` y `bench-pages` en `offline/run_eval.py` (R2, R10, contrato H2 y H4). Además de lo de data-model §3, el banco lleva `queries.f32`: los vectores de las 370 preguntas, embebidos con la 270M del escritorio, para medir la búsqueda sin modelo. `offline/tests/test_bench_bank.py` va primero
- [ ] T008 [P] `…/androidTest/testing/bench/DevFiles.kt` y `BenchBank.kt`: verificar el manifiesto, construir o reutilizar `cacheDir/bench/<nombre>.db` con `DefaultRagIndex` en su propio dispatcher, y dar las preguntas y sus vectores. `…/androidTest/testing/bench/BenchBankTest.kt`, con un banco diminuto en los assets de `androidTest`, corre en el emulador
- [ ] T009 [P] `offline/eval/device_bench.py` y `device-report` en `offline/run_eval.py` (contrato H2). `offline/tests/test_device_bench.py` va primero, con un JSON de ejemplo
- [ ] T010 [P] `…/androidTest/testing/bench/BenchCleanupTest.kt` (contrato B2): borra `cacheDir/bench`, `dev-bench`, la 270M y E4B de `dev-models` con sus entradas de verificación, y los informes `f14-*`. Se prueba en el emulador con ficheros simulados

**Checkpoint**: kit y bancos listos; las historias pueden empezar.

---

## Phase 3: User Story 2 - INT8 y precarga en la app (Priority: P1) 🎯 MVP

**Goal**: la búsqueda vectorial usa INT8 precargado con reordenación exacta cuando el índice está al día, y la exacta en cualquier otro caso (R3, contrato `int8-index.md`).

**Independent Test**: `RagIndexInt8Test` en los emuladores (contrato I5) y las pruebas JVM del mantenedor.

### Tests for User Story 2

- [ ] T011 [P] [US2] `…/androidTest/data/rag/RagIndexInt8Test.kt`, con los 7 casos del contrato I5 y SQLite-Vector real. Falla primero
- [ ] T012 [P] [US2] `…/test/data/rag/QuantizedIndexMaintainerTest.kt`: coalescencia, nada con una tanda activa, el arranque precarga y un fallo no se propaga (contrato I4). Falla primero

### Implementation for User Story 2

- [ ] T013 [US2] Dominio:
  - `QuantizedState`, `quantizedState()` y `refreshQuantized(minChunks)` en `…/microrag/domain/rag/RagIndex.kt`;
  - `INT8_CANDIDATES`, `INT8_MIN_CHUNKS`, `INT8_MAX_MEMORY_MB` y `QUANT_REBUILD_DEBOUNCE_MS` en `…/microrag/domain/rag/RagParameters.kt` (data-model §2);
  - el fake en `…/test/testing/FakeRagIndex.kt`
- [ ] T014 [US2] Datos:
  - `vector_quantize`, `vector_quantize_preload` y la lectura de blobs en `…/microrag/data/rag/RagConnection.kt`;
  - en `…/microrag/data/rag/DefaultRagIndex.kt`: la ruta INT8 con reordenación exacta (sin ámbito, con ámbito y excluidos, contrato I2), la marca `int8Preloaded`, `refreshQuantized` (reconstruir solo con `quant_dirty = 1`, sin tanda y con ≥ `INT8_MIN_CHUNKS` buscables; poner `quant_dirty = 0` solo tras el éxito) y `quantizedState`
- [ ] T015 [US2] `…/microrag/data/rag/QuantizedIndexMaintainer.kt` y la interfaz `IngestActivity` (con su implementación sobre el estado de `IngestRunner`), y su `single` en `…/microrag/data/di/DataModule.kt`
- [ ] T016 [US2] Disparadores (contrato I4):
  - `…/microrag/data/ingest/IngestRunner.kt` (`finishRun`);
  - `…/microrag/data/library/DefaultLibraryRepository.kt` (`delete`, `removeFromIndex`, `reindex` y `discardReindex`);
  - `…/microrag/data/ingest/DefaultImportQueueRepository.kt`;
  - `…/microrag/MicroRagApplication.kt` (`STARTUP`).

  Se amplían las pruebas JVM que ya existen de esos componentes para comprobar cada petición
- [ ] T017 [US2] Verde: JVM y `RagIndexInt8Test` en `MicroRAG_API33` y `Pixel_10`

**Checkpoint**: INT8 funciona y nunca se usa desfasado.

---

## Phase 4: User Story 1 - La búsqueda con 10k, 50k y 100k fragmentos (Priority: P1)

**Goal**: los percentiles de cada parte de la búsqueda, el coste de INT8 y su Recall@20 en tres tamaños (R2, contrato B2).

**Independent Test**: `SearchScaleBenchmarkTest` con el banco de 10k en el emulador (sin modelo: sin la fase de embedding), y con los tres bancos en el teléfono (T047).

- [ ] T018 [P] [US1] Construir en el escritorio los bancos de 10k, 50k y 100k (`run_eval.py bench-bank`, en segundo plano por su duración) y verificar sus manifiestos
- [ ] T019 [US1] `…/androidTest/data/retrieval/SearchScaleBenchmarkTest.kt` (contrato B2):
  - variantes `f32`, `int8`, `int8-rerank` e `int8-nopreload`;
  - fases de embedding (solo y dentro de la búsqueda, si hay modelo), vectorial, léxica, híbrida, acotada y con excluidos;
  - tiempos de cuantizar y precargar, memoria, tamaño del `.db` y Recall@20;
  - estado `no-space` si no cabe
- [ ] T020 [US1] Prueba corta en el emulador con el banco de 10k, para validar el informe JSON y `device-report`

**Checkpoint**: banco de búsqueda listo para el teléfono.

---

## Phase 5: User Story 3 - El primer token, medido de verdad (Priority: P1)

**Goal**: el primer token con 4, 6 y 8 bloques, el extremo a extremo con su desglose y la sesión sostenida (R5).

**Independent Test**: los bancos en el teléfono (T049 y T051). En los emuladores se saltan, porque no hay modelos.

- [ ] T021 [US3] Desglose de tiempos (contrato G9) en el `log` de `…/microrag/data/chat/DefaultChatSession.kt`: embedding, búsqueda, prompt y primer token, solo números. Se amplía `…/test/data/chat/DefaultChatSessionTest.kt` antes
- [ ] T022 [US3] `…/androidTest/data/chat/GenerationBenchmarkTest.kt`, variante `base`: 4, 6 y 8 bloques con el selector de D-034 ampliado solo en la prueba; tokens reales (`BenchmarkInfo`, con `enableBenchmark` solo aquí) y estimados; primer token, prefill, decodificación y RSS pico; 20 preguntas
- [ ] T023 [US3] `…/androidTest/data/chat/SustainedChatBenchmarkTest.kt`:
  - `sin-reposo` y `con-reposo`, y los minutos por argumento;
  - respuestas completas, con el desglose, el estado térmico y la batería por pregunta;
  - corte con `SEVERE` (`aborted-thermal`);
  - la configuración de generación por argumento, para repetirla con las palancas

---

## Phase 6: User Story 4 - Palancas para bajar el primer token (Priority: P2)

**Goal**: el prefijo reutilizado, la especulativa y los bloques, con interruptor, listos para decidir con las cifras (R6, R7, R13, contrato `generation-levers.md`).

**Independent Test**: `PromptBuilderTest` (prefijo + resto = `build()`) en la JVM; `PrefixCacheGoldenTest` y las variantes del banco de generación en el teléfono.

### Tests for User Story 4

- [ ] T024 [P] [US4] En `…/test/rag/answer/PromptBuilderTest.kt`: `buildSplit(...).whole == build(...)` para cada plantilla, idioma, con y sin turno previo, y en las goldens del prompt. Falla primero
- [ ] T025 [P] [US4] `…/androidTest/data/chat/PrefixCacheGoldenTest.kt`: respuestas greedy idénticas con y sin `prefixCache` sobre las goldens de respuesta (solo en el teléfono)

### Implementation for User Story 4

- [ ] T026 [US4] Interruptores y prompt:
  - `buildSplit` y `SplitPrompt` en `…/microrag/rag/answer/PromptBuilder.kt`;
  - `GenerationPrompt.Split`, `prefixCache`, `speculative` y `SpeculativeMode` en `…/microrag/rag/runtime/TextGenerator.kt`;
  - `PREFIX_CACHE`, `SPECULATIVE_DECODING` y `ACTIVATION_PRECISION` en `RagParameters.kt`, con los valores de hoy;
  - que `…/microrag/data/chat/EngineAnswerEngine.kt` y `DefaultChatSession.kt` los usen.

  Con los valores por defecto, el comportamiento es el de hoy, y las pruebas JVM que ya existen lo comprueban
- [ ] T027 [US4] `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt` (contrato G3 y G4):
  - el prefijo reutilizado por el camino que fijó T002: la base por motor, un clon por respuesta, cancelar solo el clon, y rehacer y liberar la base con el motor;
  - `SpeculativeDecodingConfig(ADAPTIVE)` con `speculative`;
  - pruebas JVM del generador con un fake de motor si la API lo permite; si no, se cubre con T025
- [ ] T028 [US4] Variantes `prefix`, `mtp` y `fp32` en `GenerationBenchmarkTest` y en la configuración por argumento de `SustainedChatBenchmarkTest`. `not-available` si no hay drafter o falla el clon
- [ ] T029 [P] [US4] Escritorio: `run_eval.py ablations max_blocks=3` (R13), con las respuestas que cambian juzgadas a mano (contrato H6), y la sección en `offline/reports/eval-report.md` frente a la regla de `f14_rules.json`. Si T002 eligió el camino 2 (rol de sistema), también `template-review` con el rol de sistema

---

## Phase 7: User Story 5 - Cifras fiables: fp16 y estimación de tokens (Priority: P2)

**Goal**: la tasa de cifras deformadas con cada precisión y la plantilla `v4-cifras` medidas, y el estimador corregido si se queda corto (R8, R9).

**Independent Test**: `numeric-review` y `token-review` en el escritorio; `NumericFidelityBenchmarkTest` en el teléfono (T050).

- [ ] T030 [P] [US5] `offline/eval/numeric.py` (extracción, normalización y detector), con `offline/tests/test_numeric.py` primero. Después, `numeric-set` y la revisión de las ~60 preguntas en `offline/testset/numeric/questions.jsonl` (data-model §5)
- [ ] T031 [US5] Plantilla `v4-cifras`:
  - en `offline/eval/` (la del harness), y en `EXTRA_RULES` de `…/microrag/rag/answer/PromptBuilder.kt`, con la paridad de la golden del prompt;
  - `template-review template=v4-cifras` con la regla de D-088;
  - `numeric-review` en el Mac con `v4` y `v4-cifras`, en fp16 y FP32.

  Las respuestas que cambian se juzgan a mano (H6)
- [ ] T032 [US5] `…/androidTest/data/chat/NumericFidelityBenchmarkTest.kt`: las 60 preguntas desde `dev-bench`, con las variantes `default`, `fp32` y `v4-cifras`. Las respuestas van al JSON para la revisión en el escritorio
- [ ] T033 [P] [US5] Estimador (R9, contrato G6):
  - pruebas primero: `…/test/rag/answer/TokenEstimateTest.kt` y `offline/tests/test_token_estimate.py`, con la paridad de textos fijos y con a = b = c = 4,554 igual al de hoy;
  - después `offline/eval/token_estimate.py`, `token-review` (ajuste e informe), el estimador en `PromptBuilder.estimatedTokens` y `TOKEN_ESTIMATE` en `RagParameters.kt` y en `offline/config/rag_parameters.json`, con los valores de hoy hasta decidir
- [ ] T034 [US5] Decidir el estimador con la regla: juzgar a mano las respuestas que cambian en `token-review` (H6). Si se adopta, fijar a, b y c en los dos lados; si no, se quedan los de hoy. Anotarlo en `eval-report.md`

---

## Phase 8: User Story 6 - El modo multimodal, medido y ajustado (Priority: P2)

**Goal**: páginas en F32 frente a INT8, la consulta con la 440M frente a la 270M, el primer token con páginas en sesión sostenida, la precarga por ámbito y la regla de seguimiento con páginas (R10).

**Independent Test**: `visual-followup-review` en el escritorio; `ChatViewModelTest` y `HybridRetrieverTest` en la JVM; los bancos en el teléfono (T048 y T052).

- [ ] T035 [P] [US6] Bancos de páginas (`bench-pages m=1000/5000/10000`) y `…/androidTest/data/retrieval/PageSearchBenchmarkTest.kt` (`f32`, `int8-rerank`). La ruta INT8 de páginas, con `pages_quant_dirty` (contrato I3) y su caso en `RagIndexInt8Test`, se implementa solo si T048 lo pide
- [ ] T036 [P] [US6] `…/androidTest/rag/runtime/EmbedderComparisonBenchmarkTest.kt`: la 440M y la 270M (desde `dev-models`), consulta p50 y p95 y RSS, en orden alterno
- [ ] T037 [US6] `…/androidTest/data/chat/VisualChatBenchmarkTest.kt`: modos nuevos `sostenido` (25 min con Equilibrado) y `recarga` (primera respuesta tras cambiar de ámbito, con y sin precarga)
- [ ] T038 [US6] Precarga por ámbito (contrato G7):
  - pruebas primero en `…/test/feature/chat/ChatViewModelTest.kt`;
  - `searchablePageCount(scope)` en `RagIndex`, `DefaultRagIndex` y `FakeRagIndex`;
  - `PREWARM_BY_SCOPE` en `RagParameters.kt` (en `false`);
  - en `…/microrag/feature/chat/ChatViewModel.kt`, la llamada a `warmUp(visual = …)` al cambiar el ámbito
- [ ] T039 [P] [US6] Escritorio:
  - `offline/testset/visual/followups.jsonl`, con ~40 pares revisados (`switch` y `follow`);
  - las variantes de `visual-followup-review` en `offline/eval/visual_retrieval.py`, con `offline/tests/test_visual_followup.py` primero;
  - la ejecución frente a la regla de `f14_rules.json`, y la sección en `eval-report.md`
- [ ] T040 [US6] App:
  - `PAGE_FOLLOW_UP` en `RagParameters.kt` y en `offline/config/rag_parameters.json`, con el valor que gane T039;
  - las variantes en `…/microrag/data/retrieval/HybridRetriever.kt`, con `…/test/data/retrieval/HybridRetrieverTest.kt` primero (contrato G8)

---

## Phase 9: User Story 7 - E4B frente a E2B en el teléfono (Priority: P3)

**Goal**: E4B medido con el mismo método que E2B (R11).

- [ ] T041 [US7] Variante `e4b` en `GenerationBenchmarkTest`: instala E4B verificado desde `dev-models` con `DevFiles`, mide con el embedder cargado, y da `does-not-fit` o `not-available` con el error si no carga

---

## Phase 10: User Story 8 - Palancas de calidad baratas (Priority: P3)

**Goal**: la búsqueda entre idiomas evaluada (solo en el escritorio, D-088) y la reordenación α = 0,5 repetida (R12).

- [ ] T042 [P] [US8]
  - `offline/eval/english_text.py`, con `offline/tests/test_english_text.py` primero;
  - las variantes `lang-normalizer` y `lang-normalizer-weighted` en `crosslang-review`;
  - las respuestas que cambian juzgadas a mano (H6);
  - el resultado frente a la regla de 10 puntos, en `eval-report.md`
- [ ] T043 [US8] Repetir `order-review` después de T029 y T034 con lo adoptado, frente a su regla. Corregir FR-030 en la spec si el analyze lo aprueba (R12: el coste en el teléfono solo se mide si se adopta)

---

## Phase 11: Fase del teléfono (con permiso del usuario; R15)

**Purpose**: todas las medidas en el A063 y las decisiones con las reglas.

- [ ] T044 **Preguntar al usuario** antes de empezar, con la duración estimada. Después:
  - anotar los ajustes del teléfono;
  - `svc power stayon usb`, `wm dismiss-keyguard`;
  - instalar los APK de depuración y de pruebas con `adb -s`;
  - abrir la app para que cree `dev-bench`
- [ ] T045 Copiar con `adb push` los bancos, las preguntas y los vectores de consulta a `…/files/dev-bench/`, y la 270M y E4B a `…/files/dev-models/`, y verificar los SHA-256
- [ ] T046 Goldens con los runtimes nuevos: `embedding_golden`, las goldens de respuesta y `PrefixCacheGoldenTest` (T025). Si fallan las dos primeras: volver a 0.18.1 y 1.1.2 (deshacer T001 y T003), reinstalar y anotarlo (R1)
- [ ] T047 `SearchScaleBenchmarkTest` con 10k, 50k y 100k. Fijar `INT8_MIN_CHUNKS` e `INT8_CANDIDATES` (Recall@20 con reordenación ≥ 0,97) en `RagParameters.kt`; si INT8 no lo cumple, decidir con las cifras (FR-008)
- [ ] T048 `PageSearchBenchmarkTest` (y la ruta INT8 de páginas de T035 si pasa de 20 ms) y `EmbedderComparisonBenchmarkTest`
- [ ] T049 `GenerationBenchmarkTest`: `base` con 4, 6 y 8 bloques, y después `prefix`, `mtp`, `fp32` y `v4-cifras`
- [ ] T050 `NumericFidelityBenchmarkTest` (`default`, `fp32` y `v4-cifras`), con la revisión de las respuestas marcadas en el escritorio (`offline/testset/numeric/review.csv`)
- [ ] T051 `SustainedChatBenchmarkTest` base: `sin-reposo` y `con-reposo`
- [ ] T052 `VisualChatBenchmarkTest`: `detalles`, `sostenido` y `recarga`
- [ ] T053 Variante `e4b` de `GenerationBenchmarkTest` (T041)
- [ ] T054 **Decidir** con `f14_rules.json`:
  - prefijo, especulativa, bloques, FP32 o `v4-cifras` (con la sesión sostenida con FP32 si hace falta), precarga por ámbito e INT8 de páginas;
  - poner los interruptores adoptados en `RagParameters.kt`;
  - repetir `SustainedChatBenchmarkTest` `sin-reposo` con lo adoptado (SC-004);
  - si cambia una espera anunciada (D-086), ajustar `PageDetail` y sus pruebas
- [ ] T055 Batería instrumentada completa en el teléfono con lo adoptado. En el escritorio, `ablations` con el modo apagado (FR-038, SC-009)
- [ ] T056 Limpieza:
  - `BenchCleanupTest`;
  - comprobar sin capturas que no quedan `dev-bench`, `cacheDir/bench`, la 270M, E4B ni informes `f14-*`;
  - restaurar los ajustes y `stay_on = 0`;
  - el modo multimodal sigue activado (SC-010)

---

## Phase 12: Polish & Cross-Cutting Concerns

- [ ] T057 [P] `run_eval.py device-report` → `specs/014-ondevice-performance/benchmarks.md` (SC-001), con las secciones de R14, y la nota que remite a él en `docs/RAG_Movil/informe-rag-movil-v2.md` (FR-034)
- [ ] T058 DECISIONS:
  - INT8, bloques, perfil de modelo, primer token (cerrar o actualizar D-057), fp16, estimador, seguimiento con páginas, recarga y cada palanca, con sus cifras (FR-035);
  - si se adoptaron los runtimes, la enmienda 1.2.1 con `/speckit-constitution` (R1).

  `docs/roadmap/FEATURES.md` al día
- [ ] T059 Verificación final:
  - JVM, lint y la batería instrumentada sin modelos en los emuladores;
  - `assembleRelease` sin los bancos (están en `androidTest`) ni texto en logcat (SC-010)
- [ ] T060 `specs/014-ondevice-performance/report.md` (informe de cierre: implementado, verificación, cifras, decisiones y lo que queda) y la **revisión de cierre con el usuario**. La integración y el push van solo con su aprobación

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → historias.
- **T005:** después de la parada del analyze (la regla de la especulativa) y antes de cualquier evaluación del escritorio (T029, T031, T034, T039, T042 y T043).
- **US2 (3)** antes que **US1 (4):** el banco mide la ruta INT8.
- **US3 (5)** antes que **US4 (6):** las variantes de palancas amplían los bancos de US3.
- **US5 (7), US6 (8), US7 (9) y US8 (10):** después de Foundational. T043 espera a T029 y T034.
- **Teléfono (11):** cuando estén todas las historias. Dentro de la fase, en orden: T046 abre y T054 decide.
- **Polish (12):** después del teléfono.

### Parallel Opportunities

- **Fase 2:** T006, T007, T008, T009 y T010 son ficheros distintos.
- **US2:** T011 y T012 en paralelo.
- **Escritorio mientras se programa la app:** T018 (bancos, larga), T029, T030, T033, T035 (bancos de páginas), T039 y T042.

## Parallel Example: escritorio y app a la vez

```text
En segundo plano: T018 run_eval.py bench-bank n=100000            (≈ 1–2 h)
En paralelo:      T011 RagIndexInt8Test + T012 QuantizedIndexMaintainerTest
Después:          T013 → T014 → T015 → T016 → T017
```

## Implementation Strategy

### MVP

US2 (INT8 en la app) es lo que exigen la constitución IV y D-052. Con el banco de US1 cumple los criterios 1 y 2 de F14.

### Entrega incremental

1. Runtimes y kit (fases 1–2).
2. INT8 (US2) y su banco (US1).
3. Bancos de generación (US3) y palancas con interruptor (US4–US6).
4. E4B y calidad (US7–US8).
5. Teléfono: medir, decidir con las reglas, repetir con lo adoptado.
6. Cierre.

### Notas

- Ninguna palanca se activa por defecto sin cumplir su regla en las cifras del teléfono.
- Lo que no se pueda medir se registra con su estado (`not-available`, `does-not-fit`, `no-space` o `aborted-thermal`), nunca con cifras inventadas (constitución V).
