---

description: "Lista de tareas de F14 — Rendimiento en dispositivo y decisión INT8"
---

# Tasks: Rendimiento en dispositivo y decisión INT8 (F14)

**Input**: Documentos de diseño en `/specs/014-ondevice-performance/`:
- [plan.md](plan.md) y [spec.md](spec.md);
- [research.md](research.md) y [data-model.md](data-model.md);
- [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-010). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero. Los bancos son pruebas instrumentadas que solo corren en el teléfono.

**Organización**: por historia de usuario. US2 va antes que US1 porque US1 mide la ruta INT8 que construye US2. Todo lo que necesita el teléfono va en la **fase 11**, salvo la sesión corta de goldens de T005, aprobada en D-090.

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
  - teléfono `adb -s P31281001066`, **solo en T005 y en la fase 11, y con permiso**.
- **Con el teléfono conectado:**
  - nunca `connectedDebugAndroidTest`. Siempre se instalan los APK con `-s <dispositivo>` y se lanza `am instrument -r`, con `-e notAnnotation …RequiresNetwork`;
  - antes de una batería, `wm dismiss-keyguard`, comprobar `isKeyguardShowing=false` y `svc power stayon usb` (y al terminar, `stay_on` a 0).
- **En el teléfono** (memorias `f11-phone-phase`, `f16-phone-pending`, `f17-closing`, `f18-closing`, `f19-closing` y `f20-closing`):
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - el A063 está en la 440M con el modo activado. La 270M y E4B solo se copian a `dev-models`, las pruebas los cargan desde allí (D-090) y se borran al terminar;
  - al terminar se restauran los ajustes.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**Analyze** (2026-11-06): 33 hallazgos de una revisión independiente, con 2 CRITICAL, 12 HIGH, 14 MEDIUM y 5 LOW. Se recogen en spec, plan, research, contratos y estas tareas (D-090). En la parada, el usuario decidió:
- la enmienda PATCH 1.2.1;
- el criterio de MTP;
- la sesión corta de goldens;
- que el banco corto decida las palancas;
- la plantilla corta solo sin prefijo;
- `v4-cifras` también con páginas;
- las reglas que faltaban.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US8).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup (runtimes, R1)

**Purpose**: subir los runtimes al empezar y validarlos en el A063 antes de construir nada que dependa de ellos (D-088, D-090).

- [X] T001 Subir `litertlm` a 0.19.1 y `sqliteVector` a 1.2.0 en `gradle/libs.versions.toml`. Pasar `enableSpeculativeDecoding` (obsoleto) a `SpeculativeDecodingConfig(OFF)` en `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt`, y compilar sin avisos nuevos
- [X] T002 Comprobar con `javap` sobre el AAR 0.19.1 lo que R6 y R7 dejan «a verificar»:
  - si `Session` decodifica en streaming y se puede clonar tras el prefill;
  - cómo se leen los tokens de la plantilla de chat de Gemma 4 (`ModelInfo` o la plantilla del fichero, en el escritorio con `litert-lm-api`);
  - la función de `ModelInfo` que dice si hay drafter.

  Anotar el resultado y el camino elegido (1 o 2) en `research.md` R6 y R7
- [X] T003 [P] Escritorio:
  - subir `litert-lm-api` a 0.19.1 y `sqliteai-vector` a 1.2.0, y añadir `snowballstemmer==3.1.1` en `offline/requirements.txt`;
  - pasar `pytest`;
  - comprobar que la recuperación de `ablations` da las mismas cifras que en F20 (FR-038);
  - regenerar las respuestas de `v4` con 0.19.1 y juzgar a mano las que cambian (D-033). Es la base de las palancas (D-090).

  Si algo no cuadra, volver a 0.18.1 y 1.1.2 y anotarlo en `research.md` R1
- [X] T004 JVM (`testDebugUnitTest`), lint (`lintDebug`: desaparecen los avisos de versión de LiteRT-LM y SQLite-Vector) y la batería instrumentada sin modelos en `MicroRAG_API33` y `Pixel_10`, con los runtimes nuevos
- [X] T005 **Sesión corta en el A063** (preguntar antes, D-090): instalar los APK con `adb -s` y pasar el `embedding_golden` y las goldens de respuesta con 0.19.1, sin abrir la app ni tocar datos del usuario (unos 15 minutos). Si fallan: volver a 0.18.1 y 1.1.2 (deshacer T001 y T003), anotarlo, y las palancas de la 0.19 pasan a `not-available`. Dejar el teléfono como estaba

---

## Phase 2: Foundational (kit de medida y bancos)

**Purpose**: lo que usan todos los bancos. Ninguna historia de medida empieza sin esto.

- [X] T006 Congelar las reglas: `offline/config/f14_rules.json`, exactamente el del contrato H3 (D-088 y D-090), en un **commit propio antes de cualquier evaluación**
- [X] T007 [P] Kit de medida en `…/androidTest/testing/bench/`: `DeviceConditions.kt`, `Percentiles.kt` y `BenchReport.kt` (contrato B1, data-model §4). `…/androidTest/testing/bench/BenchReportTest.kt`, sin modelos, comprueba en el emulador el JSON, los percentiles y las condiciones; se escribe antes y falla primero
- [X] T008 [P] `offline/eval/bench_bank.py`, con `bench-bank` y `bench-pages` en `offline/run_eval.py` (R2, R10, contrato H2 y H4). Además de lo de data-model §3, el banco lleva `queries.f32`: los vectores de las 370 preguntas, embebidos con la 270M del escritorio, para medir la búsqueda sin modelo. `offline/tests/test_bench_bank.py` va primero
- [X] T009 [P] En `…/androidTest/testing/bench/`:
  - `DevFiles.kt`: verificación por SHA-256 y carga directa de la 270M y E4B desde `dev-models` con `EngineFactory`, sin `installVerified` ni `ModelCatalog` (D-090);
  - `BenchBank.kt`: verificar el manifiesto, construir o reutilizar `cacheDir/bench/<nombre>.db` con `DefaultRagIndex` en su propio dispatcher, y dar las preguntas y sus vectores;
  - `BenchDirsTest.kt`: crea `dev-bench` y `dev-models` sin abrir la app.

  `…/androidTest/testing/bench/BenchBankTest.kt`, con un banco diminuto en los assets de `androidTest`, corre en el emulador
- [X] T010 [P] `offline/eval/device_bench.py` y `device-report` en `offline/run_eval.py` (contrato H2). `offline/tests/test_device_bench.py` va primero, con un JSON de ejemplo
- [X] T011 [P] `…/androidTest/testing/bench/BenchCleanupTest.kt` (contrato B2): borra `cacheDir/bench`, `dev-bench`, la 270M y E4B de `dev-models`, y los informes `f14-*` y `visual-chat-*`. Se prueba en el emulador con ficheros simulados

**Checkpoint**: kit y bancos listos; las historias pueden empezar.

---

## Phase 3: User Story 2 - INT8 y precarga en la app (Priority: P1) 🎯 MVP

**Goal**: la búsqueda vectorial usa INT8 precargado con reordenación exacta cuando el índice está al día y la biblioteca pasa el umbral, y la exacta en cualquier otro caso (R3, contrato `int8-index.md`).

**Independent Test**: `RagIndexInt8Test` en los emuladores (contrato I5) y las pruebas JVM del mantenedor.

### Tests for User Story 2

- [X] T012 [P] [US2] `…/androidTest/data/rag/RagIndexInt8Test.kt`, con los 9 casos del contrato I5 y SQLite-Vector real. Falla primero
- [X] T013 [P] [US2] `…/test/data/rag/QuantizedIndexMaintainerTest.kt`: coalescencia, nada con una tanda activa, el arranque precarga, un fallo no se propaga y nada bajo el umbral (contrato I4). Falla primero

### Implementation for User Story 2

- [X] T014 [US2] Dominio:
  - `QuantizedState`, `quantizedState()` y `refreshQuantized(minChunks)` en `…/microrag/domain/rag/RagIndex.kt`;
  - `INT8_CANDIDATES` (100), `INT8_MIN_CHUNKS` (10.000), `INT8_MAX_MEMORY_MB` (64) y `QUANT_REBUILD_DEBOUNCE_MS` (2.000) en `…/microrag/domain/rag/RagParameters.kt` (data-model §2);
  - el fake en `…/test/testing/FakeRagIndex.kt`
- [X] T015 [US2] Datos:
  - `vector_quantize`, `vector_quantize_preload` y la lectura de blobs en `…/microrag/data/rag/RagConnection.kt`;
  - en `…/microrag/data/rag/DefaultRagIndex.kt`:
    - la ruta INT8 con reordenación exacta (sin ámbito, con ámbito y excluidos, contrato I2);
    - `int8Preloaded`, que pasa a falso en `markQuantDirty` y al empezar `refreshQuantized`;
    - `refreshQuantized`: reconstruye solo con `quant_dirty = 1`, sin tanda y con ≥ `INT8_MIN_CHUNKS` buscables, y pone `quant_dirty = 0` solo tras el éxito;
    - `quantizedState`;
    - el log con la duración de la reconstrucción y de la precarga (solo números)
- [X] T016 [US2] `…/microrag/data/rag/QuantizedIndexMaintainer.kt` y la interfaz `IngestActivity` (con su implementación sobre el estado de `IngestRunner`), y su `single` en `…/microrag/data/di/DataModule.kt`
- [X] T017 [US2] Disparadores (contrato I4):
  - `…/microrag/data/ingest/IngestRunner.kt` (`finishRun`);
  - `…/microrag/data/library/DefaultLibraryRepository.kt` (`delete`, `removeFromIndex`, `reindex` y `discardReindex`);
  - `…/microrag/MicroRagApplication.kt` (`STARTUP`).

  Se amplían las pruebas JVM que ya existen de esos componentes para comprobar cada petición
- [X] T018 [US2] Verde: JVM y `RagIndexInt8Test` en `MicroRAG_API33` y `Pixel_10`

**Checkpoint**: INT8 funciona, nunca se usa desfasado y no actúa bajo el umbral.

---

## Phase 4: User Story 1 - La búsqueda con 10k, 50k y 100k fragmentos (Priority: P1)

**Goal**: los percentiles de cada parte de la búsqueda, el coste de INT8 y su Recall@20 en tres tamaños, y el umbral de INT8 (R2, R3, contrato B2).

**Independent Test**: `SearchScaleBenchmarkTest` con el banco de 10k en el emulador (sin modelo: sin la fase de embedding), y con los tres bancos en el teléfono (T049).

- [ ] T019 [P] [US1] Construir en el escritorio los bancos de 10k, 50k y 100k (`run_eval.py bench-bank`, en segundo plano por su duración) y verificar sus manifiestos
- [X] T020 [US1] `…/androidTest/data/retrieval/SearchScaleBenchmarkTest.kt` (contrato B2):
  - variantes `f32`, `int8`, `int8-rerank` e `int8-nopreload`;
  - embedder: el instalado (440M) y la 270M desde `dev-models` (SC-003);
  - fases: embedding (solo y dentro de la búsqueda, si hay modelo), vectorial sola y dentro de la híbrida, léxica, híbrida, acotada y con excluidos;
  - la vectorial F32 sola también con subconjuntos de 1k, 2,5k y 5k del banco de 10k;
  - tiempos de cuantizar y precargar, la espera que causan a una búsqueda, memoria, tamaño del `.db` y Recall@20;
  - estado `no-space` si no cabe
- [X] T021 [US1] Prueba corta en el emulador con el banco de 10k, para validar el informe JSON y `device-report`

**Checkpoint**: banco de búsqueda listo para el teléfono.

---

## Phase 5: User Story 3 - El primer token, medido de verdad (Priority: P1)

**Goal**: el primer token con 4, 6 y 8 bloques, el extremo a extremo con su desglose, la sesión sostenida y el arranque (R5, R14).

**Independent Test**: los bancos en el teléfono (T048, T050 y T052). En los emuladores se saltan, porque no hay modelos.

- [X] T022 [US3] Desglose de tiempos (contrato G9) en el `log` de `…/microrag/data/chat/DefaultChatSession.kt`: embedding, búsqueda, prompt y primer token, solo números. Se amplía `…/test/data/chat/DefaultChatSessionTest.kt` antes
- [X] T023 [US3] `…/androidTest/data/chat/GenerationBenchmarkTest.kt`, variante `base`:
  - 4, 6 y 8 bloques con el selector de D-034 ampliado solo en la prueba;
  - tokens reales (`BenchmarkInfo`, con `enableBenchmark` solo aquí) y estimados;
  - primer token, prefill, decodificación y RSS pico;
  - 20 preguntas;
  - un modo que alterna la base y una variante pregunta a pregunta, para decidir las palancas (D-090)
- [X] T024 [US3] `…/androidTest/data/chat/SustainedChatBenchmarkTest.kt`:
  - `sin-reposo` y `con-reposo`, y los minutos por argumento;
  - respuestas completas, con el desglose, el estado térmico y la batería por pregunta;
  - corte con `SEVERE` (`aborted-thermal`);
  - la configuración de generación por argumento, para repetirla con las palancas
- [X] T025 [P] [US3] `…/androidTest/rag/runtime/StartupBenchmarkTest.kt` (contrato B2): carga en frío (con una caché de LiteRT-LM propia de la prueba, vacía) y con caché de Gemma (texto y visión), la 440M y la 270M (desde `dev-models`)

---

## Phase 6: User Story 4 - Palancas para bajar el primer token (Priority: P2)

**Goal**: el prefijo reutilizado, la especulativa y los bloques, con interruptor, listos para decidir con las cifras (R6, R7, R13, contrato `generation-levers.md`).

**Independent Test**: `PromptBuilderTest` (prefijo + resto = `build()`) en la JVM; `PrefixCacheGoldenTest` y las variantes del banco de generación en el teléfono.

### Tests for User Story 4

- [X] T026 [P] [US4] En `…/test/rag/answer/PromptBuilderTest.kt`: `buildSplit(...).whole == build(...)` para cada plantilla, idioma, con y sin turno previo, y en las goldens del prompt. Falla primero
- [X] T027 [P] [US4] `…/androidTest/data/chat/PrefixCacheGoldenTest.kt`: las respuestas greedy de las goldens con y sin `prefixCache`. Informa las diferencias en lugar de fallar (contrato G3). Solo en el teléfono

### Implementation for User Story 4

- [X] T028 [US4] Interruptores y prompt:
  - `buildSplit` y `SplitPrompt` en `…/microrag/rag/answer/PromptBuilder.kt`;
  - el prefijo opcional de `AnswerPrompt.Text` en `…/microrag/domain/chat/ChatModels.kt`;
  - `GenerationPrompt.Split`, `prefixCache`, `speculative` y `SpeculativeMode` en `…/microrag/rag/runtime/TextGenerator.kt`;
  - `PREFIX_CACHE`, `SPECULATIVE_DECODING` y `ACTIVATION_PRECISION` en `RagParameters.kt`, con los valores de hoy;
  - que `…/microrag/data/chat/EngineAnswerEngine.kt` y `DefaultChatSession.kt` los usen.

  Con los valores por defecto, el comportamiento es el de hoy, y las pruebas JVM que ya existen lo comprueban
- [X] T029 [US4] `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt` (contrato G3 y G4):
  - el prefijo reutilizado por el camino que fijó T002: la base por motor, un clon por respuesta, cancelar solo el clon, y rehacer y liberar la base con el motor;
  - `SpeculativeDecodingConfig(ADAPTIVE)` con `speculative`;
  - pruebas JVM del generador con un fake de motor si la API lo permite; si no, se cubre con T027
- [X] T030 [US4] Variantes `prefix`, `mtp` y `fp32` en `GenerationBenchmarkTest` (con el modo alterno de T023) y en la configuración por argumento de `SustainedChatBenchmarkTest`. `not-available` si no hay drafter o falla el clon
- [X] T031 [P] [US4] Escritorio:
  - `run_eval.py ablations --base max_blocks=3` (R13), con las respuestas que cambian juzgadas a mano (contrato H6), frente a la regla de `f14_rules.json`, y la sección en `offline/reports/eval-report.md`. Hecho con `f14-review variant=blocks-3`: 224 respuestas juzgadas; 79,7 % frente a 81,1 %, no cumple y se quedan 4 bloques;
  - si T002 eligió el camino 2 (rol de sistema), también `template-review` con el rol de sistema;
  - la `v4-corta` solo si el prefijo reutilizado queda `not-available` (si no se adopta tras T055, se evalúa entonces; D-090)

---

## Phase 7: User Story 5 - Cifras fiables: fp16 y estimación de tokens (Priority: P2)

**Goal**: la tasa de cifras deformadas con cada precisión y la plantilla `v4-cifras` medidas, y el estimador corregido si se queda corto (R8, R9).

**Independent Test**: `numeric-review` y `token-review` en el escritorio; `NumericFidelityBenchmarkTest` en el teléfono (T051).

- [X] T032 [P] [US5] `offline/eval/numeric.py` (extracción, normalización y detector), con `offline/tests/test_numeric.py` primero. Después, `numeric-set` y la revisión de las ~60 preguntas en `offline/testset/numeric/questions.jsonl` (data-model §5)
- [X] T033 [US5] Plantilla `v4-cifras`, para texto y páginas (D-090):
  - en el harness (`offline/eval/`, incluido `visual_prompt.py`);
  - en `EXTRA_RULES` de `…/microrag/rag/answer/PromptBuilder.kt`, con la paridad de la golden del prompt;
  - `…/androidTest/rag/answer/VisualPromptGoldenTest.kt` con las dos plantillas;
  - `template-review variants=v4,v4-cifras` con la regla de D-088;
  - `numeric-review` en el Mac con `v4` y `v4-cifras`, en fp16 y FP32.

  Las respuestas que cambian se juzgan a mano (H6)
- [X] T034 [US5] `…/androidTest/data/chat/NumericFidelityBenchmarkTest.kt`: las 60 preguntas desde `dev-bench`, con las variantes `default`, `fp32` y `v4-cifras`. Las respuestas van al JSON para la revisión en el escritorio
- [X] T035 [US5] Estimador (R9, contrato G6):
  - pruebas primero: `…/test/rag/answer/TokenEstimateTest.kt` y `offline/tests/test_token_estimate.py`, con la paridad de textos fijos y con a = b = c = 4,554 igual al de hoy;
  - después:
    - `offline/eval/token_estimate.py` y `token-review` (ajuste e informe);
    - el estimador en `PromptBuilder.estimatedTokens`, que solo usa el límite de 2.000 (alcance de D-090);
    - `TOKEN_ESTIMATE` en `RagParameters.kt` y en `offline/config/rag_parameters.json`, con los valores de hoy hasta decidir
- [X] T036 [US5] Decidir el estimador con su regla (p95 real/estimado ≤ 1,0 en texto con cifras y la regla de calidad): juzgar a mano las respuestas que cambian en `token-review` (H6). Si se adopta, fijar a, b y c en los dos lados; si no, se quedan los de hoy. Anotarlo en `eval-report.md`

---

## Phase 8: User Story 6 - El modo multimodal, medido y ajustado (Priority: P2)

**Goal**: páginas en F32 frente a INT8, la consulta con la 440M frente a la 270M, el primer token con páginas en sesión sostenida, la precarga por ámbito y la regla de seguimiento con páginas (R10).

**Independent Test**: `visual-followup-review` en el escritorio; `ChatViewModelTest`, `ChatUseCasesTest` y `HybridRetrieverTest` en la JVM; los bancos en el teléfono (T049 y T053).

- [X] T037 [P] [US6] Bancos de páginas (`bench-pages m=1000/5000/10000`) y `…/androidTest/data/retrieval/PageSearchBenchmarkTest.kt` (`f32`, e `int8-rerank` con SQL directo sobre la base del banco, contrato B2)
- [X] T038 [P] [US6] `…/androidTest/rag/runtime/EmbedderComparisonBenchmarkTest.kt`: la 440M y la 270M (desde `dev-models`), consulta p50 y p95 y RSS, en orden alterno
- [X] T039 [US6] `…/androidTest/data/chat/VisualChatBenchmarkTest.kt`: modos nuevos `sostenido` (25 min con Equilibrado) y `recarga` (primera respuesta tras cambiar de ámbito, con y sin precarga, con el pico de memoria)
- [X] T040 [US6] Precarga por ámbito (contrato G7):
  - pruebas primero en `…/test/feature/chat/ChatViewModelTest.kt` y en las de `WarmUpAnswerEngineUseCase`;
  - en `…/microrag/domain/chat/ChatUseCases.kt`, que `WarmUpAnswerEngineUseCase` reciba el ámbito y use el predicado de hoy (modo, `pagesUsable` y `withPages` del ámbito);
  - `PREWARM_BY_SCOPE` en `RagParameters.kt` (en `false`);
  - en `…/microrag/feature/chat/ChatViewModel.kt`, la llamada al cambiar el ámbito
- [X] T041 [P] [US6] Escritorio:
  - `offline/testset/visual/followups.jsonl`, con ~40 pares revisados (`switch` y `follow`);
  - las variantes de `visual-followup-review` en `offline/eval/visual_retrieval.py`, con `offline/tests/test_visual_followup.py` primero;
  - la ejecución frente a la regla de `f14_rules.json` y la sección en `eval-report.md`
- [X] T042 [US6] App:
  - `PAGE_FOLLOW_UP` en `RagParameters.kt` y en `offline/config/rag_parameters.json`, con el valor que gane T041;
  - las variantes en `…/microrag/data/retrieval/HybridRetriever.kt`, con el embedding extra solo con `request.withPages`;
  - `…/test/data/retrieval/HybridRetrieverTest.kt` primero (contrato G8), con casos con el modo apagado y sin páginas

---

## Phase 9: User Story 7 - E4B frente a E2B en el teléfono (Priority: P3)

**Goal**: E4B medido con el mismo método que E2B (R11).

- [X] T043 [US7] Variante `e4b` en `GenerationBenchmarkTest`: carga E4B desde `dev-models` con `DevFiles` (SHA-256 en la prueba, sin catálogo), mide con el embedder cargado, y da `does-not-fit` o `not-available` con el error si no carga

---

## Phase 10: User Story 8 - Palancas de calidad baratas (Priority: P3)

**Goal**: la búsqueda entre idiomas evaluada, solo en el escritorio (D-088). La reordenación α = 0,5 se repite después del teléfono (T057).

- [X] T044 [P] [US8]
  - ~~`offline/eval/english_text.py` y las variantes `lang-normalizer` y `lang-normalizer-weighted`~~: el filtro previo (`crosslang-screen`, research R12) las sustituye por su techo, `crosslang-review mode=vector`;
  - las respuestas que cambian juzgadas a mano (H6): 81 en `reports/crosslang-review-vector.csv`;
  - el resultado frente a la regla de 10 puntos, en `eval-report.md`: ES·EN 63,2 % (referencia 82,5 %) y EN·ES 66,0 % (80,0 %), no cumple

---

## Phase 11: Fase del teléfono (con permiso del usuario; R15)

**Purpose**: todas las medidas en el A063 y las decisiones con las reglas.

- [X] T045 **Preguntar al usuario** antes de empezar, con la duración estimada. Después:
  - anotar los ajustes del teléfono;
  - `svc power stayon usb` y `wm dismiss-keyguard`;
  - instalar los APK de depuración y de pruebas con `adb -s`;
  - `BenchDirsTest`
- [X] T046 Copiar con `adb push` los bancos, las preguntas y los vectores de consulta a `…/files/dev-bench/`, y la 270M y E4B a `…/files/dev-models/`, y verificar los SHA-256
- [X] T047 `PrefixCacheGoldenTest` (T027). Las respuestas que cambien se juzgan con la regla de calidad antes de decidir el prefijo. Primera pasada: en la GPU, 4 de 20 idénticas; en la CPU, 2 de 5. La prueba guarda dónde se separan las respuestas (solo números), y la calidad se juzga solo si el prefijo da la ganancia de T050. **Hecho:** T050 da +1,10 s de p95 con el prefijo, así que no se adopta y no hay calidad que juzgar. La prueba compara también MTP (`-e f14.compare mtp`) y el control `repeat` (research R6, R7)
- [X] T048 `StartupBenchmarkTest`, `IndexingBenchmarkTest` y `VisualIngestBenchmarkTest` (arranque e ingesta, FR-033). `IndexingBenchmarkTest` usa la 270M de `dev-models` si no está instalada y deja su informe; la limpieza borra también `f19-visual-ingest-*`
- [ ] T049 `SearchScaleBenchmarkTest` con 10k, 50k y 100k (y los subconjuntos), `PageSearchBenchmarkTest` y `EmbedderComparisonBenchmarkTest`:
  - fijar `INT8_MIN_CHUNKS` e `INT8_CANDIDATES` (Recall@20 con reordenación ≥ 0,97) en `RagParameters.kt`, y si INT8 no lo cumple, decidir con las cifras (FR-008);
  - si las páginas pasan de 20 ms, implementar su ruta INT8 (`pages_quant_dirty`, contrato I3) con su caso en `RagIndexInt8Test`
- [X] T050 `GenerationBenchmarkTest`: `base` con 4, 6 y 8 bloques; después `prefix`, `mtp`, `fp32` y `v4-cifras` en modo alterno. **Hecho:** con 4 bloques, p95 de 5,49 s. El prefijo da +1,10 s y no se adopta; `v4-corta` se descarta por cota (0,19 s como mucho). MTP sube la decodificación un 49,8 % sin empeorar el primer token (falta la igualdad greedy). FP32 da +2,21 s y no se adopta. `v4-cifras` no cuesta primer token (research R5–R8)
- [ ] T051 `NumericFidelityBenchmarkTest` (`default`, `fp32` y `v4-cifras`), con la revisión de las respuestas marcadas en el escritorio (`offline/testset/numeric/review.csv`)
- [ ] T052 `SustainedChatBenchmarkTest` base: `sin-reposo` y `con-reposo`
- [ ] T053 `VisualChatBenchmarkTest`: `detalles`, `sostenido` y `recarga`
- [ ] T054 Variante `e4b` de `GenerationBenchmarkTest` (T043)
- [ ] T055 **Decidir** con `f14_rules.json`:
  - prefijo, especulativa, bloques, FP32 o `v4-cifras`, precarga por ámbito e INT8 de páginas;
  - poner los interruptores adoptados en `RagParameters.kt`;
  - repetir `SustainedChatBenchmarkTest` `sin-reposo` con lo adoptado (SC-004);
  - si el p95 sigue por encima de 5 s, **preguntar al usuario** si se actualiza la excepción D-057;
  - si cambia una espera anunciada (D-086), ajustar `PageDetail` y sus pruebas
- [ ] T056 Batería instrumentada completa en el teléfono con lo adoptado. En el escritorio, `ablations` con el modo apagado (FR-038, SC-009)
- [ ] T057 [US8] Repetir `order-review` en el escritorio con lo adoptado en T055 (estimador y bloques), frente a su regla (R12, FR-030). Si se adopta, medir su coste en el teléfono antes de T058
- [ ] T058 Limpieza:
  - `BenchCleanupTest`;
  - comprobar sin capturas que no quedan `dev-bench`, `cacheDir/bench`, la 270M, E4B ni informes (`f14-*` y `visual-chat-*`);
  - restaurar los ajustes y `stay_on = 0`;
  - el modo multimodal sigue activado (SC-010)

---

## Phase 12: Polish & Cross-Cutting Concerns

- [ ] T059 [P] `run_eval.py device-report` → `specs/014-ondevice-performance/benchmarks.md` (SC-001), con las secciones de R14, y la nota que remite a él en `docs/RAG_Movil/informe-rag-movil-v2.md` (FR-034)
- [ ] T060 DECISIONS:
  - INT8, bloques, perfil de modelo, primer token (cerrar o actualizar D-057), fp16, estimador, seguimiento con páginas, recarga y cada palanca, con sus cifras (FR-035);
  - si se adoptaron los runtimes, la enmienda PATCH 1.2.1 con `/speckit-constitution` (R1, D-090).

  `docs/roadmap/FEATURES.md` al día
- [ ] T061 Verificación final:
  - JVM, lint y la batería instrumentada sin modelos en los emuladores;
  - `assembleRelease` sin los bancos (están en `androidTest`) ni texto en logcat (SC-010)
- [ ] T062 `specs/014-ondevice-performance/report.md` (informe de cierre: implementado, verificación, cifras, decisiones y lo que queda) y la **revisión de cierre con el usuario**. La integración y el push van solo con su aprobación

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → historias. T005 (sesión corta) va antes de T028 y T029, que usan la API de 0.19.
- **T006:** antes de cualquier evaluación del escritorio (T031, T033, T036, T041, T044 y T057).
- **US2 (3)** antes que **US1 (4):** el banco mide la ruta INT8.
- **US3 (5)** antes que **US4 (6):** las variantes de palancas amplían los bancos de US3.
- **US5 (7), US6 (8), US7 (9) y US8 (10):** después de Foundational.
- **Teléfono (11):** cuando estén todas las historias. Dentro de la fase, en orden: T055 decide, y T057 va después de T055.
- **Polish (12):** después del teléfono.

### Parallel Opportunities

- **Fase 2:** T007, T008, T009, T010 y T011 son ficheros distintos.
- **US2:** T012 y T013 en paralelo.
- **Escritorio mientras se programa la app:** T003 (regenerar `v4`), T019 (bancos, larga), T031, T032, T037 (bancos de páginas), T041 y T044.

## Parallel Example: escritorio y app a la vez

```text
En segundo plano: T019 run_eval.py bench-bank n=100000            (≈ 1–2 h)
En paralelo:      T012 RagIndexInt8Test + T013 QuantizedIndexMaintainerTest
Después:          T014 → T015 → T016 → T017 → T018
```

## Implementation Strategy

### MVP

US2 (INT8 en la app) es lo que exigen la constitución IV y D-052. Con el banco de US1 cumple los criterios 1 y 2 de F14.

### Entrega incremental

1. Runtimes, sesión corta de goldens y kit (fases 1–2).
2. INT8 (US2) y su banco (US1).
3. Bancos de generación (US3) y palancas con interruptor (US4–US6).
4. E4B y calidad (US7–US8).
5. Teléfono: medir, decidir con las reglas, repetir con lo adoptado.
6. Cierre.

### Notas

- Ninguna palanca se activa por defecto sin cumplir su regla en las cifras del teléfono.
- Lo que no se pueda medir se registra con su estado (`not-available`, `does-not-fit`, `no-space` o `aborted-thermal`), nunca con cifras inventadas (constitución V).
