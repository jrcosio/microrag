---

description: "Lista de tareas de F18 — Viabilidad del RAG visual"
---

# Tasks: Viabilidad del RAG visual (F18)

**Input**: documentos de diseño en `/specs/018-visual-rag-feasibility/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización:**
- por historia de usuario;
- las medidas del teléfono van todas en la **última fase**, con el permiso del usuario;
- la evaluación del escritorio (US5) va antes, porque produce el conjunto del dispositivo.

**Historias de la spec:**
- US1 — Gemma responde mirando páginas en el teléfono (P1);
- US2 — La 440M sustituye a la 270M sin reindexar (P1);
- US3 — Embeber páginas e imágenes en el teléfono (P1);
- US4 — Convertir páginas e imágenes en el teléfono (P2);
- US5 — Fijar los parámetros con una evaluación en el escritorio (P1);
- US6 — Decidir con cifras y preparar F19 y F20 (P2);
- US7 — Medir sin poner en riesgo el teléfono ni la app (P2).

**Entorno** (quickstart):
- **Gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`. Sin él, Gradle no arranca.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Emuladores:** `MicroRAG_API33` y `Pixel_10`, sin ventana, que se cierran al terminar cada fase.
- **Teléfono** (`adb -s P31281001066`), **solo en la última fase:**
  - nunca `connectedDebugAndroidTest`: se instalan los APK con `-s` y se lanza `am instrument -r` con la salida a `/data/local/tmp`;
  - antes, `wm dismiss-keyguard`;
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - al terminar se restauran los ajustes y el idioma por app.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat`.
- **Subagentes:** nunca reciben ni envían el correo del usuario. Si piden una cabecera de contacto (Wikimedia), se usa un identificador genérico del proyecto.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US7).
- **Abreviaturas de rutas:**
  - `…/debug/` = `app/src/debug/java/com/lisdatasolutions/microrag/`;
  - `…/testDebug/`, `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/<conjunto>/java/`;
  - `offline/` es el harness.

---

## Phase 1: Setup

- [X] T001 Dependencias del escritorio:
  - `pypdfium2==5.14.0` y `pillow==12.3.0` en `offline/requirements.txt`;
  - instalarlas en `offline/.venv`;
  - registrar `EG2_440M` en `offline/eval/models.py`, con su SHA-256 y tamaño de `ModelCatalog.kt`;
  - enlazar `offline/models/embeddinggemma-2-text-vision-440m.litertlm`.
- [X] T002 [P] En `offline/tests/test_appconfig.py`, comprobar que `models.verify("EG2_440M")` resuelve el fichero enlazado con la huella de `ModelCatalog.kt`. `models.py` ya lee el catálogo, así que no se duplica nada. Ejecutar toda la batería de Python como línea base (181 pruebas).
- [X] T003 [P] Línea base de la app: `./gradlew :app:testDebugUnitTest :app:lintDebug` en verde (1.128 pruebas JVM) antes de tocar nada.

---

## Phase 2: Foundational (bloquea las historias)

**Teléfono: el armazón del diagnóstico.**

- [X] T004 Pruebas de capas en `…/test/architecture/LayeringTest.kt` (contrato del diagnóstico §6, R3), que deben fallar:
  - LiteRT-LM admitido también en `src/debug/java/…/rag/runtime/litert`, y `android.graphics.pdf` también en `src/debug/java/…/data/ingest`;
  - una regla nueva: ni `src/main` ni `src/release` contienen `debug.feasibility.visual`, y `VisualFeasibilityScreen.kt` existe en debug.
- [X] T005 [P] Interfaces y tipos en `…/debug/debug/feasibility/visual/VisualContracts.kt`: `VisionGenerator`, `VisionGeneration`, `ImageEmbedder`, `VisionModelInfo`, `VisionEngineFactory`, `PageRasterizer`, `ImageFormat` y `EncodedImage` (contrato del diagnóstico §1). Sin imports de LiteRT-LM ni de la API de PDF.
- [X] T006 [P] Protocolo en `…/debug/debug/feasibility/visual/VisualProtocol.kt`:
  - la etapa A de R4, con 25 combinaciones: páginas solas y mezcla con el contexto de D-034, 1–3 páginas × 70/140/280, trozos a 280 y la base sin imagen, con 3 repeticiones;
  - la etapa B: candidatas recibidas como parámetro, con las 20 preguntas de `contexts.json`;
  - repeticiones: generación 1 + 3 con 64 tokens de salida; embeddings y conversión 3 + 30;
  - resoluciones 768/1.024/1.536, calidad 85 y la tanda de 50 páginas de R14.
- [X] T007 [P] Pruebas JVM del informe en `…/testDebug/debug/feasibility/visual/VisualReportTest.kt`:
  - esquema v1;
  - escritura atómica en JSON y Markdown;
  - el texto de las respuestas (`GEMMA_PAGES` y `READING`) y los vectores, solo en el JSON, nunca en el Markdown;
  - `charging` en cada escenario.
- [X] T008 Informe en `…/debug/debug/feasibility/visual/report/VisualReport.kt` y `VisualReportWriter.kt` (data-model §2.2), siguiendo `ReportWriter` de F02 (`filesDir/reports/visual-<ts>.{json,md}`). Hace pasar T007.
- [X] T009 [P] Pruebas JVM del conjunto del dispositivo en `…/testDebug/debug/feasibility/visual/DeviceSetLoaderTest.kt`:
  - manifiesto válido;
  - un fichero con huella distinta queda fuera y se informa;
  - falta el manifiesto;
  - la carpeta no existe.
- [X] T010 Cargador en `…/debug/debug/feasibility/visual/DeviceSetLoader.kt`, que lee `getExternalFilesDir(null)/dev-visual/manifest.json` y verifica SHA-256 (data-model §2.1). Hace pasar T009.
- [X] T011 Pruebas JVM del núcleo de la suite en `…/testDebug/debug/feasibility/visual/VisualSuiteTest.kt`, con fakes de `VisionEngineFactory`, `PageRasterizer` y `EngineFactory` como `SuiteFixture` de F02:
  - `releaseAll()` antes y después de cada escenario;
  - espera térmica y confirmación si el estado es «severo»;
  - un error en GPU da `NOT_AVAILABLE` y cualquier otro `FAILED`;
  - cancelar da `CANCELLED` y libera;
  - el informe se reescribe tras cada resultado;
  - un bloque sin sus ficheros verificados no corre y queda con su motivo;
  - cada escenario usa una sola configuración de engine, entre dos `releaseAll()` (hallazgo M6).

  Además, `…/testDebug/debug/feasibility/visual/VisualFeasibilityViewModelTest.kt`, antes de T015 (hallazgo M8):
  - estado de los modelos y del conjunto;
  - lanzar y cancelar;
  - `onStop` cancela, espera y libera.
- [X] T012 Núcleo de la suite en `…/debug/debug/feasibility/visual/VisualSuite.kt`: `run(blocks, confirmHot)`, el ejecutor de escenarios y el mapa de estados (contrato §2). Hace pasar T011. Los bloques se rellenan en sus historias.
- [X] T013 [P] Adaptadores de LiteRT-LM en `…/debug/rag/runtime/litert/LiteRtVisionEngines.kt` (R1, contrato §1):
  - `LiteRtVisionGenerator`: `EngineConfig` con `visionBackend = CPU` y `maxNumImages`; fija y restaura `ExperimentalFlags.visualTokenBudget` en la cola del LLM; `sendMessageAsync(Contents.of(ImageBytes…, Text))`, con el primer token y `BenchmarkInfo`;
  - `LiteRtImageEmbedder`: `EmbeddingEngineConfig` con `visionBackend = CPU` y `visionTokensPerImage`; `computeEmbedding(listOf(InputData.Image(bytes)), EmbeddingOptions(normalize = false, visionTokensPerImage = …))`;
  - `LiteRtVisionEngineFactory.modelInfo(path)`, con `ModelInfo.from(path)`.

  Sin texto en logcat.
- [X] T014 [P] Rasterizador en `…/debug/data/ingest/DebugPageRasterizer.kt` (R6):
  - `PdfRenderer` sobre un `ARGB_8888` en blanco, escalado por el lado largo;
  - `ImageDecoder` con `setTargetSize`, EXIF y `ALLOCATOR_SOFTWARE`, y transparencias sobre blanco;
  - `Bitmap.compress` en `WEBP_LOSSY`, `JPEG` o `PNG`.
- [X] T015 Pantalla, ViewModel, estado y Koin:
  - `…/debug/debug/feasibility/visual/VisualFeasibility{Screen,ViewModel,UiState,Module}.kt`, con componentes `Lisdata*`, un botón por bloque, el estado de los modelos y del conjunto del dispositivo, el progreso y «Cancelar». Al pasar a segundo plano, cancela y libera, como F02;
  - textos en `app/src/debug/res/values/strings_feasibility.xml`;
  - entrada «Viabilidad visual» en `…/debug/debug/DebugEntries.kt` y el módulo en `DebugModules.kt`;
  - ampliar `app/src/testDebug/java/com/lisdatasolutions/microrag/debug/DebugModulesTest.kt` (verificación de Koin).

  Hace pasar T004.

**Escritorio: lo común.**

- [X] T016 [P] Pruebas de Python en `offline/tests/test_vision_runtime.py`, `test_raster.py` y `test_photos.py`:
  - **presupuesto por `ctypes`:** con una librería falsa, se llama a `…set_visual_token_budget` con el valor y solo cuando se pide;
  - **`Contents` intercalado:** etiqueta, imagen y texto en orden;
  - **rasterizado:** píxeles por lado largo, fondo blanco, formatos, trozos con un 10 % de solape y caché;
  - **fotos simuladas:** misma semilla, mismos bytes, y recetas dentro de límites.
- [X] T017 Módulos `offline/eval/vision_runtime.py`, `raster.py` y `photos.py` (contrato del harness §2, R1, R7). Hacen pasar T016.
- [X] T018 Regla congelada, parte 1: `offline/config/visual_rules.json`, con `testset_sha256: null` y todas las claves de data-model §1.5:
  - los umbrales de D-073 y D-075;
  - la fórmula compuesta y sus constantes (206,5 y 53,0 ms);
  - la etapa B;
  - el juez decisivo manual;
  - las reglas de elección de R12;
  - las semillas.

  Más `offline/eval/visual_rules.py` y `offline/tests/test_visual_rules.py`:
  - las ablaciones se niegan sin huella o si el fichero difiere de `HEAD`;
  - cada ejecución guarda el hash git;
  - la fórmula compuesta;
  - la regla de sí o no;
  - las tres etapas de `visual-decide`;
  - las reglas de elección con empates.

  **Commit propio antes de cualquier ablación** (FR-025).

**Checkpoint:** el diagnóstico abre en el emulador con sus bloques vacíos, y el harness rasteriza y llama a los modelos con imágenes.

---

## Phase 3: User Story 5 - Fijar los parámetros con una evaluación en el escritorio (Priority: P1)

**Goal**: corpus visual, conjunto de prueba congelado, ablaciones e informe (FR-019 a FR-026).

**Independent Test**: `run_eval.py visual-report` da las tablas por modo, variante y tipo, y con «Texto» las cifras de F05 no cambian.

- [X] T019 [P] [US5] Pruebas en `offline/tests/test_visual_corpus.py`:
  - el corpus `visual` registrado en `CORPORA`, con los formatos `png` y `jpg`, los tipos nuevos y las licencias nuevas;
  - `simulated_from`;
  - la reconstrucción de los escaneados como PDF solo de imagen, con bytes deterministas;
  - `SOURCES.md` generado.
- [X] T020 [US5] Corpus en `offline/eval/visual_corpus.py` y `offline/eval/corpus.py`, con el manifiesto `offline/corpus_visual/manifest.json`, fuera de `offline/corpus` (R8) de los 18 documentos y 15 imágenes de R8, con su URL, licencia, atribución y `fetch.pages`.
  - Ejecutar `fetch-corpus corpus=visual --record`, `visual-photos` y `sources corpus=visual`.
  - Comprobar que el total queda por debajo de 40 MB y que hay al menos 300 páginas. *Hecho: 302 páginas y 51 MB; la desviación sobre los 40 MB va en el informe.*
  - Las imágenes dudosas de R8 se revisan, y se sustituyen si hace falta.
  - Commit del corpus con su `SOURCES.md`.
- [X] T021 [P] [US5] Pruebas en `offline/tests/test_visual_index.py`:
  - de fragmento a página con solape y con páginas sin texto;
  - vectores de página truncados a 256 y 512 y normalizados (`fake_embedding`);
  - el índice de texto del corpus visual usa los parámetros de D-034.
- [X] T022 [US5] Índices en `offline/eval/visual_index.py` y el subcomando `visual-build` (R10), registrando la variante en `offline/eval/builds.py` si hace falta:
  - `visual-text.db`, `visual-chunk-pages.json` y `visual-pages-<px>-<tokens>.db`;
  - también el índice de páginas de los 6 PDF de F05, para la pérdida en texto (R11). *Pasa a `visual-text-loss` (T059), que lo construye con la resolución y los tokens elegidos.*

  Ejecutar `visual-raster` y `visual-build` para 768/1.024/1.536 × 70/140.
- [X] T023 [P] [US5] Pruebas en `offline/tests/test_visual_testset.py`: reglas V1–V8, hoja de revisión, aplicación de veredictos y `visual-freeze`, que se niega a cambiar una huella ya escrita.
- [X] T024 [US5] Conjunto de prueba en `offline/eval/visual_testset.py`, con los subcomandos `visual-validate`, `visual-review-sheet`, `visual-review-apply` y `visual-freeze`.
- [X] T025 [US5] Redacción del conjunto (R9, D-073), en `offline/testset/visual/questions.jsonl` con `review: pendiente`:
  - **agentes separados,** que no revisan, redactan las preguntas por documento a partir de las imágenes de página;
  - **reparto:** 20 de tabla, 15 de gráfico, 15 de escaneado, 15 de imagen (unas 8 de fotos simuladas), 5 mixtas, 20 de control y 10 sin respuesta, más unas 15 de reserva;
  - cada una con sus páginas, su respuesta esperada y sus `key_facts`.
- [X] T026 [US5] Revisión por la sesión principal (D-033, D-073), mirando cada página con los criterios de R9; los veredictos van a `offline/testset/visual/review.csv`.
  - Ejecutar `visual-review-apply` y `visual-validate`: ≥ 100 aceptadas y ≥ 60 % visuales.
  - Ejecutar `visual-freeze` y hacer **commit** del conjunto y de `visual_rules.json` con su huella, **antes** de T028 y T032.
- [X] T027 [P] [US5] Pruebas en `offline/tests/test_visual_retrieval.py`: listas por página con la mejor posición, RRF de 2 y 3 listas con k = 60, empates, y Recall@1/3/5/10, MRR y nDCG@10 por página y por tipo.
- [ ] T028 [US5] Recuperación en `offline/eval/visual_retrieval.py` y el subcomando `visual-retrieve`. Ejecutar las ablaciones de R10 (modo × px × tokens × dimensión), y después `visual-decide --stage retrieval`, que fija la resolución, los tokens y la dimensión antes de las respuestas (hallazgo M9). *Con D-076, la elección usa «Visual» y las páginas de la respuesta salen de su lista.*
- [X] T029 [P] [US5] Pruebas en `offline/tests/test_visual_prompt.py` y `test_visual_judge.py`:
  - plantilla v5-visual: numeración, etiqueta antes de cada imagen y trozos;
  - mezcla con la regla de duplicados y relleno con los bloques siguientes;
  - recuento de tokens de texto y visuales;
  - juez: citas a página y a bloque con páginas, y negativas.
- [X] T030 [US5] Plantilla en `offline/eval/visual_prompt.py`, y juez en `offline/eval/visual_judge.py` (R11). Hacen pasar T029.
- [X] T031 [US5] Generación con imágenes: el subcomando `visual-generate`, que se reanuda, guarda la huella por ejecución (`LATE_FIELDS`) y marca las tandas parciales.
- [ ] T032 [US5] Ejecutar las ablaciones de respuesta de R11 con los parámetros de recuperación de T028:
  - «Texto»;
  - «Páginas», con 1–3 páginas × 70/140/280 y con trozos a 280;
  - «Mezcla», con las mismas variantes.

  Anotar la duración de cada tanda.
- [ ] T033 [US5] Comprobación del juez (`visual-manual`): 30 respuestas de cada variante principal («Texto» y la mejor de «Páginas» y de «Mezcla» según el juez), revisadas según D-033, en `offline/reports/visual-manual-judgement.csv`. La revisión decisiva va en T058.
- [ ] T034 [US5] Regresión del texto (FR-024, SC-010): `run_eval.py ablations` con la configuración de D-034 da las mismas cifras que el informe de F05 y F10.
- [X] T035 [P] [US5] Pruebas en `offline/tests/test_visual_parity.py`: el manifiesto del conjunto del dispositivo, los cosenos por imagen y el juez de la lectura del teléfono.
- [ ] T036 [US5] Conjunto del dispositivo en `offline/eval/visual_parity.py` y el subcomando `visual-device-set` (R13, data-model §1.6):
  - 20 imágenes y sus vectores a 70 y 140;
  - los 10 PDF de origen;
  - las 10–15 páginas de lectura, con su pregunta y sus `key_facts`;
  - `contexts.json`, con 20 preguntas: el contexto de texto de D-034, sus páginas y sus trozos, en el orden de la plantilla v5-visual;
  - `decode/`: las dos fotos de 12 MP, un PNG con transparencia, un JPEG girado por EXIF y un JPEG en CMYK (hallazgo M5);
  - `manifest.json`.
- [ ] T037 [US5] Informe en `offline/eval/visual_report.py` y el subcomando `visual-report`, que genera `offline/reports/visual-eval-report.md` con sus secciones 1–3 y 6. La 4 y la 5 llegan con el teléfono.

**Checkpoint:** el escritorio ha fijado sus parámetros con la regla congelada y tiene el conjunto del dispositivo listo.

---

## Phase 4: User Story 1 - Gemma responde mirando páginas en el teléfono (Priority: P1)

**Goal**: bloques `GEMMA_INFO`, `GEMMA_PAGES` y `READING` (FR-004 a FR-007).

**Independent Test**: `VisualSuiteTest` cubre la matriz con fakes, y en el teléfono el informe trae las cifras de cada combinación.

- [X] T038 [P] [US1] Pruebas JVM en `VisualSuiteTest`:
  - `GEMMA_INFO` informa las modalidades y los backends;
  - **`GEMMA_PAGES`:**
    - recorre las 25 combinaciones de la etapa A, en el orden de v5-visual (etiqueta, imagen y texto);
    - con `candidates`, corre la etapa B con las 20 preguntas;
    - la visión en GPU se pide una vez y queda `NOT_AVAILABLE` si el fake la rechaza (FR-007);
  - un presupuesto mayor que el máximo del fichero da `NOT_SUPPORTED` sin cargar nada;
  - `GEMMA_PAGES` y `READING` guardan las respuestas solo en el JSON del informe, y nunca en el registro.
- [X] T039 [US1] Bloques `GEMMA_INFO`, `GEMMA_PAGES` y `READING` en `VisualSuite.kt`, con las páginas y el contexto del conjunto del dispositivo. Hacen pasar T038.

---

## Phase 5: User Story 2 - La 440M sustituye a la 270M sin reindexar (Priority: P1)

**Goal**: golden, identidad y consulta de la 440M (FR-008 a FR-010).

**Independent Test**: `EmbeddingVariantGoldenTest` en el teléfono da ≥ 0,99 frente al golden y ≥ 0,9999 frente a la 270M.

- [X] T040 [P] [US2] Prueba instrumentada `…/androidTest/rag/runtime/EmbeddingVariantGoldenTest.kt` (`@RequiresModels`, R5):
  - la 440M frente a `embedding_golden.jsonl` en CPU y en GPU (la GPU, omitida en el emulador), sin comprobar la huella del golden;
  - la identidad con la 270M en CPU;
  - antes de medir, las huellas de las dos variantes contra `ModelCatalog` (hallazgo M2);
  - sus cifras, en `filesDir/reports/variant-golden-<ts>.json` (hallazgo M3).

  Ampliar `testing/InstalledModels` si hace falta para la 440M.
- [X] T041 [P] [US2] Pruebas JVM en `VisualSuiteTest` de `EMBED_TEXT`: la carga en frío, la consulta y el fragmento con las dos variantes, con 3 + 30, los p50 y p95 de cada una, y el pico de memoria (FR-010).
- [X] T042 [US2] Bloque `EMBED_TEXT` en `VisualSuite.kt`, con el `EngineFactory` de `main` (`LiteRtTextEmbedder`) y el fichero de cada variante. Hace pasar T041.

---

## Phase 6: User Story 3 - Embeber páginas e imágenes en el teléfono (Priority: P1)

**Goal**: bloques `EMBED_IMAGE` y `PARITY` (FR-011, FR-012).

**Independent Test**: con fakes, la suite produce los vectores y los cosenos. En el teléfono, `visual-parity` da ≥ 0,99 con los mismos bytes.

- [X] T043 [P] [US3] Pruebas JVM en `VisualSuiteTest`:
  - **`EMBED_IMAGE`:** 70 y 140 tokens en CPU; la GPU pedida da `NOT_AVAILABLE` si el fake la rechaza; dimensión y norma a 256 y 512 con `EmbeddingMath`;
  - **`PARITY`:** vectores de 768 de cada imagen del manifiesto y de las páginas rasterizadas, solo en el JSON.
- [X] T044 [US3] Bloques `EMBED_IMAGE` y `PARITY` en `VisualSuite.kt`. Hacen pasar T043.

---

## Phase 7: User Story 4 - Convertir páginas e imágenes en el teléfono (Priority: P2)

**Goal**: bloques `RASTER`, `IMAGE_DECODE` y `PROJECTION` (FR-013 a FR-015).

**Independent Test**: `DebugPageRasterizerTest` pasa en los emuladores, y con fakes la suite da sus cifras.

- [X] T045 [P] [US4] Prueba instrumentada `…/androidTest/data/ingest/DebugPageRasterizerTest.kt`, sin modelos:
  - con los PDF de fixtures de F06 (`androidTest/assets/pdf`): páginas a 768 px por el lado largo, fondo blanco y WebP o JPEG decodificables;
  - con una imagen JPEG con EXIF girado y un PNG con transparencia, generados con `offline/gen_pdf_fixtures.py` (o un script hermano): quedan derechos y sobre blanco;
  - con `restricted.pdf` de las fixtures de F06, cifrado solo con contraseña de propietario: se abre o queda registrado (R8).
- [X] T046 [P] [US4] Pruebas JVM en `VisualSuiteTest`:
  - `RASTER` cubre resolución × formato × orientación;
  - `IMAGE_DECODE` cubre las tres resoluciones;
  - `PROJECTION` informa por página, el estado térmico al principio, a la mitad y al final, y la proyección a 200 marcada como tal.
- [X] T047 [US4] Bloques `RASTER`, `IMAGE_DECODE` y `PROJECTION` en `VisualSuite.kt`. Hacen pasar T045 y T046.

---

## Phase 8: User Story 7 - Medir sin poner en riesgo el teléfono ni la app (Priority: P2)

**Goal**: un solo engine en GPU, cancelación, segundo plano y release limpia (FR-016 a FR-018, SC-009).

**Independent Test**: las pruebas JVM y `LayeringTest` en verde, y `apkanalyzer` sin rastro de F18 en release.

- [X] T048 [P] [US7] Pruebas JVM en `VisualSuiteTest`:
  - `EMBED_IMAGE` seguido de `GEMMA_PAGES` libera el embedder antes de cargar Gemma (`EngineCoordinator` real con fakes);
  - la cancelación llega en menos de 1 s fuera de la carga.
- [X] T049 [US7] Prueba instrumentada `…/androidTest/debug/feasibility/visual/VisualFeasibilityBenchmarkTest.kt` (`@RequiresModels`, contrato §4):
  - lee `-e blocks`, responde `confirmHot = { false }` y escribe el informe;
  - en logcat, solo una línea de cifras por escenario.
- [ ] T050 [US7] Verificación de release, como el checklist de F02:
  - `assembleRelease`;
  - `apkanalyzer dex packages` sin `feasibility.visual`, `LiteRtVision` ni `DebugPageRasterizer`;
  - el manifiesto de release igual que en F17;
  - `NetworkConfinementTest` y `ManifestInvariantsTest` en verde.

  Se anota en `specs/018-visual-rag-feasibility/checklists/release-verification.md`.

---

## Phase 9: Polish sin teléfono

- [ ] T051 Batería JVM, lint y Python:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`, solo con los avisos de siempre;
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests`.
- [ ] T052 Batería instrumentada completa en `Pixel_10` y en `MicroRAG_API33`, siempre con `-s`, sin modelos. En API 33 siguen los 4 fallos previos de Atrás desde la raíz.
- [ ] T053 Comprobar el diagnóstico en el emulador:
  - la pantalla «Viabilidad visual» abre, al 200 % y en oscuro;
  - con el conjunto del dispositivo copiado, lo marca como verificado;
  - los bloques que necesitan modelos quedan desactivados con su motivo;
  - `RASTER` e `IMAGE_DECODE` corren y escriben el informe.
- [ ] T054 Borrador de `specs/018-visual-rag-feasibility/report.md`:
  - lo implementado;
  - build, lint y pruebas;
  - la evaluación del escritorio;
  - las desviaciones del plan.

  Avance de tareas en este fichero.

---

## Phase 10: Última fase — teléfono de referencia, decisión y cierre

- [ ] T055 **Pedir permiso al usuario** para la fase del teléfono, con el plan: ajustes que se anotan, la 440M que se copia, el conjunto del dispositivo, las pruebas, las dos etapas y la limpieza.
- [ ] T056 Fase del teléfono, parte 1 (quickstart §5), con la salida en el teléfono y sin pantalla de bloqueo:
  - importar la 440M con el procedimiento de F02;
  - copiar el conjunto del dispositivo;
  - `EmbeddingVariantGoldenTest`;
  - `VisualFeasibilityBenchmarkTest` con todos los bloques, con `GEMMA_PAGES` en la etapa A;
  - la batería completa;
  - copiar los informes a `offline/build/visual/`.
- [ ] T057 [US6] Etapa B:
  - `visual-decide --stage candidates` con el informe de la etapa A;
  - en el teléfono, `GEMMA_PAGES` con `-e candidates`;
  - copiar el informe y volcar las cifras en `specs/018-visual-rag-feasibility/benchmarks.md`, con la cifra de extremo a extremo marcada como compuesta;
  - limpieza y ajustes restaurados.
- [ ] T058 [US6] Revisión decisiva y regla:
  - `visual-parity`;
  - `visual-manual --decisive`: revisión a mano, según D-033, de «Texto» y de la configuración elegida en el conjunto visual;
  - `visual-text-loss`: la configuración elegida sobre las 323 respondibles de F05, juzgando a mano solo las que cambian;
  - `visual-decide`, y las secciones 4 y 5 de `visual-eval-report.md`.

  **Si el primer token con 1 página pasa de 8 s de p95, o la 440M no cumple SC-002, se para y se pregunta al usuario** (D-073).
- [ ] T059 [US6] Decisiones en `docs/roadmap/DECISIONS.md` (FR-028):
  - los parámetros con su cifra;
  - el cambio de la 270M a la 440M (sustituye a D-025, o se explica por qué no);
  - el sí o el no a F19–F20.

  Si sale el sí, se redacta la propuesta de enmienda de la constitución (FR-029): página citable, 440M, dimensión de página, formatos visuales y el presupuesto de primer token de las respuestas con páginas (IV). Se aplica en F19.
- [ ] T060 [US6] Cierre:
  - `report.md` cerrado, con las cifras del teléfono y del escritorio;
  - `docs/roadmap/FEATURES.md` con F18 «implementada; pendiente de la revisión de cierre» y F19–F20 con los parámetros decididos, o descartadas (FR-030).

  **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **Foundational (fase 2):**
  - T004–T015 para el teléfono y T016–T018 para el escritorio. Los dos grupos van en paralelo.
  - T018 tiene su commit antes de cualquier ablación.
  - Bloquea todo lo demás.
- **US5 (fase 3):** depende de T016–T018.
  - T026, con la huella congelada y el commit, va antes de T028 y T032.
  - T036 produce el conjunto del dispositivo que usan US1, US3 y US4 en el teléfono.
- **US1, US2, US3, US4 (fases 4–7):** dependen de T012–T015. El código y sus pruebas JVM no necesitan a US5; solo sus medidas en el teléfono.
- **US7 (fase 8):** depende de las fases 4–7.
- **Polish (fase 9):** depende de todo lo anterior.
- **Última fase (10):** depende de todo y del permiso del usuario (T055). US6 (T057–T060) depende de las cifras del teléfono.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Contratos antes que la suite; la suite antes que la pantalla.
- **Paradas de consulta:**
  - la del analyze (hecha: pérdida en texto, juez y primer token, D-075);
  - T055, el permiso para el teléfono;
  - T058, si el primer token o la 440M no cumplen;
  - T060, la revisión de cierre.

### Parallel Opportunities

- **Fase 1:** T002 y T003.
- **Fase 2:** T005, T006, T007, T009, T013, T014 y T016. `VisualFeasibilityViewModelTest` (T011) antes que T015.
- **US5:** T019, T021, T023, T027, T029 y T035.
- **US1–US4:** T038, T040, T041, T043, T045 y T046.
- **US7:** T048.

---

## Implementation Strategy

### MVP

Fases 1 y 2, y US5 hasta T028: el corpus visual, el conjunto congelado y la recuperación por página ya dicen si las páginas se encuentran mejor que su texto.

### Incremental

1. **US5:** las respuestas, la regresión y el conjunto del dispositivo.
2. **US1–US4:** los bloques del teléfono, probados en la JVM y en el emulador.
3. **US7:** la seguridad del diagnóstico y la release.
4. **Teléfono:** las medidas y, con ellas, US6: la decisión.

### Notes

- La app de producto no cambia (FR-031): `main` solo cambia en pruebas y en `LayeringTest`.
- La regla de `visual_rules.json` y la huella del conjunto no cambian después de ver resultados (FR-025).
- Los textos de respuestas y páginas solo van al informe del dispositivo, nunca a logcat (FR-018).
