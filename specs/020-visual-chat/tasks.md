---

description: "Lista de tareas de F20 — Chat con páginas, modo «Multimodal» opcional"
---

# Tasks: Chat con páginas, modo «Multimodal» opcional (F20)

**Input**: Documentos de diseño en `/specs/020-visual-chat/`:
- [plan.md](plan.md) y [spec.md](spec.md);
- [wireframe.md](wireframe.md);
- [research.md](research.md) y [data-model.md](data-model.md);
- [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-008). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en orden de prioridad. Las pruebas en el teléfono van en la **última fase**.

**Historias de la spec:**
- US1 — Preguntar sobre un escaneado o una imagen (P1);
- US2 — Preguntar con texto y páginas a la vez (P1);
- US3 — Sin modo o sin páginas, todo igual (P1);
- US4 — Elegir el detalle de las páginas (P2);
- US5 — Ver la página citada (P2);
- US6 — Recuperar del Historial una respuesta con páginas (P2);
- US7 — Pantallas aprobadas, accesibles y traducidas (P2);
- US8 — Diagnóstico de búsqueda con las páginas (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` y `Pixel_10`;
  - teléfono `adb -s P31281001066`, **solo en la última fase**.
- **Con el teléfono conectado:**
  - nunca `connectedDebugAndroidTest`. Siempre se instalan los APK con `-s <emulador>` y se lanza `am instrument -r`, con `-e notAnnotation …RequiresNetwork`;
  - antes de una batería, `wm dismiss-keyguard` y comprobar `isKeyguardShowing=false`.
- **En el teléfono** (memorias `f11-phone-phase`, `f16-phone-pending`, `f17-closing`, `f18-closing` y `f19-closing`):
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - el A063 está en la 440M con el modo activado, y **no tiene la 270M**. Una prueba que la necesite se pregunta antes;
  - al terminar se restauran los ajustes.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**Analyze** (2026-11-05): 30 hallazgos de una revisión independiente, con 1 CRITICAL, 4 HIGH, 11 MEDIUM y 14 LOW. Se recogen en estas tareas (D-085). El CRITICAL (C1) era la parada tras el analyze. En ella, el usuario decidió:
- I1: Gemma se carga con la visión solo cuando entra una página;
- aprobar el anexo §10 del wireframe.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US8).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

- [X] T001 Línea base, antes de tocar nada:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug` en verde (1.285 pruebas JVM y 2 avisos);
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests` en verde.
- [X] T002 [P] Textos del wireframe §9 y de `chat-pages-ui.md` en `app/src/main/res/values{,-en}/strings_visual_chat.xml`:
  - la fila y la pantalla «Detalle de las páginas»;
  - «Leyendo 1 página · unos %1$s s…»;
  - la tarjeta de página y su descripción para TalkBack;
  - la vista previa, el visor y sus acciones;
  - `document_citation_page_missing`;
  - `document_include_subtitle_needs_multimodal`;
  - las variantes de las imágenes sueltas y el anuncio «Leyendo 1 página» del wireframe §10.

  `StringsParityTest` pasa.
- [X] T003 [P] Golden de la v5-visual (R4):
  - `offline/gen_visual_prompt_golden.py` (`--check` como `gen_answer_golden.py`) escribe `offline/golden/visual_prompt_golden.jsonl` con `visual_prompt.build_parts`. Son unos 12 casos: con y sin turno anterior, de 0 a 4 bloques, una página de PDF y una imagen suelta. Cada imagen va como el marcador `{"image": "<id>"}`;
  - `offline/tests/test_visual_prompt_golden.py` comprueba que el fichero está al día.

---

## Phase 2: Foundational (tipos, índice, `chat.db` v3, Gemma con visión y la plantilla)

**Propósito:** todo lo que necesitan las historias:
- los tipos del data-model §3;
- `pageSearch` y sus compañeros;
- la migración de `chat.db`;
- el generador con imágenes en `main`;
- `buildVisual`.

**Bloquea todas las historias.**

### Pruebas (antes, y deben fallar)

- [X] T004 [P] `…/androidTest/…/data/rag/RagIndexPageSearchTest.kt` (P1–P3):
  - el orden por distancia y el límite `k`;
  - solo documentos indexados e incluidos;
  - el ámbito de carpetas, «General» y documentos de F17;
  - vacío con `pagesUsable = false`;
  - `ScoredPage.image` en un PNG;
  - `existingPages` tras borrar un documento y tras reindexarlo en Texto;
  - `pageFile`;
  - un documento que se reindexa con una sombra con páginas: salen las de la versión publicada hasta que se publica la nueva (analyze G2);
  - `chatLibraryState.withPages`.
- [X] T005 [P] Migración de `chat.db` 2 → 3:
  - en `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt`, donde ya se prueban las migraciones de `chat.db` (analyze P2): un `chat.db` v2 con turnos y fuentes migra con todo intacto, las fuentes como `chunk` y `page_tokens` nulo. Un fallo deja el fichero v2;
  - en `…/test/…/data/chat/ChatSchemaTest.kt`: la versión 3, y la regla «termina donde `chat_schema.sql`» cambia a la de `RagSchemaTest`. Un `ALTER TABLE t ADD COLUMN <def>` aparece idéntico como columna del `CREATE TABLE t`, y un `CREATE`, entero (analyze I4).
- [X] T006 [P] `…/test/…/rag/answer/VisualPromptGoldenTest.kt` y casos en `PromptBuilderTest` (B1–B3):
  - las partes de `buildVisual` coinciden con la golden de T003;
  - la página lleva el número `k + 1`;
  - con 0 bloques, la página es la `[1]`;
  - `estimatedTokens` solo sobre el texto;
  - `build` (la v4) no cambia (`AnswerGoldenTest` en verde).
- [X] T007 [P] `…/test/…/data/chat/EngineAnswerEngineTest.kt` (nuevo, si no existe) y `…/test/…/rag/runtime/EngineCoordinatorTest.kt` (JVM; G1–G5 y M4), con una fábrica falsa:
  - un `AnswerPrompt.Text` usa la clave y la configuración de hoy, con el modo activo o no;
  - un `AnswerPrompt.Visual` usa `VISION_GENERATOR` con `visionBackend = CPU`, 8.192 tokens y 1 imagen, y pasa sus partes y sus tokens a `GenerationPrompt.Parts` (analyze G1);
  - alternar los dos tipos cierra la Gemma anterior, en GPU **y en CPU** («Modo CPU»): nunca hay dos Gemma cargadas (analyze I2);
  - `warmUp(visual)` carga la que toca;
  - `releaseGenerators()` y `releaseUnheld()` cierran también la de visión.

### Implementación

- [X] T008 Tipos del dominio (data-model §3):
  - `PageDetail` y `StoredMultimodal.pageDetail` en `…/microrag/domain/multimodal/MultimodalModels.kt`;
  - `ScoredPage`, `PageKey` y las constantes de `RagParameters` en `…/microrag/domain/rag/{RagModels,RagParameters}.kt`;
  - `withPages`, `pages`, `lists` y `pagesMs` en `…/microrag/domain/retrieval/RetrievalModels.kt`;
  - `SourceKind` (`FRAGMENT`, `PAGE` e `IMAGE`), `PAGE_GONE`, `AnswerPrompt`, `PromptPart`, `ActiveAnswer.pageTokens`, `ChatTurn.pageTokens` y `ChatLibraryState.withPages` en `…/microrag/domain/chat/ChatModels.kt` y `…/microrag/domain/rag/RagModels.kt`;
  - `PageImageRepository` en `…/microrag/domain/library/PageImageRepository.kt`.

  Se actualizan los usos y los fakes (`FakeRagIndex`, `FakeAnswerEngine`, `InMemoryChatRepository`), sin cambiar el comportamiento: la batería JVM sigue en verde.
- [X] T009 Índice (P1–P3) en `…/microrag/domain/rag/RagIndex.kt` y `…/microrag/data/rag/DefaultRagIndex.kt`:
  - `pageSearch`, con el SQL de R2 junto a `scopedVectorSearchSql`;
  - `existingPages`;
  - `pageFile`;
  - `withPages` en `chatLibraryState`.

  `FakeRagIndex` los implementa. T004 pasa.
- [X] T010 `chat.db` v3 (R9):
  - `app/src/main/assets/chat_schema.sql`;
  - `…/microrag/data/chat/ChatSchema.kt`, con `VERSION = 3` y `ChatMigration(from = 2, …)`;
  - `…/microrag/data/chat/SqliteChatRepository.kt`, que escribe y lee `kind` y `page_tokens` (H1, H2).

  T005 pasa.
- [X] T011 Gemma con visión (R5, R6):
  - `GenerationConfig.visionBackend`/`maxNumImages`, `GenerationPrompt` (con `visualTokens: Int?`, analyze I7) y `EngineKind.VISION_GENERATOR` en `…/microrag/rag/runtime/{TextGenerator,ManagedEngine}.kt`;
  - `…/microrag/rag/runtime/EngineCoordinator.kt`: al cargar un engine de la familia LLM, cierra antes cualquier otro de esa familia, sea cual sea su backend (analyze I2);
  - `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt`: `EngineConfig` con visión cuando la hay, `Contents.of` con `Content.Text`/`Content.ImageBytes`, y `visualTokenBudget` fijado y restaurado (G3). La cancelación, como hoy (G6);
  - el diagnóstico de F18 pasa a usarlo: `…/debug/…/rag/runtime/litert/LiteRtVisionEngines.kt` se queda sin el generador, y se actualizan `…/debug/…/debug/feasibility/visual/{VisualContracts,VisualSuite,VisualFeasibilityModule}.kt`.

  `VisualSuiteTest` sigue en verde, y `LayeringTest` también.
- [X] T012 `PromptBuilder.buildVisual` en `…/microrag/rag/answer/PromptBuilder.kt` (B2, B3), con `PAGE_RULE` y la etiqueta de R4. T006 pasa.
- [X] T013 `AnswerEngine.generate(AnswerPrompt)` y `warmUp(visual)` en `…/microrag/domain/chat/` y `…/microrag/data/chat/EngineAnswerEngine.kt` (G1–G4):
  - la clave y la configuración salen del tipo de prompt, nunca del modo leído aparte (analyze I3);
  - Koin en `DataModule`.

  T007 pasa.
- [X] T014 `…/microrag/data/library/DefaultPageImageRepository.kt` (I1, I2), después de T009 porque usa `pageFile` (analyze I6). Lee con `PageFileStore.file(name)` (no hay `read`, analyze P3), y va en Koin. Prueba en `…/test/…/data/library/DefaultPageImageRepositoryTest.kt`: la fila o el fichero que faltan dan null, y el registro no lleva nombres.

**Checkpoint:**
- el índice busca páginas;
- `chat.db` está en la v3;
- Gemma lee imágenes con el modo activo;
- la v5-visual coincide con el escritorio.

---

## Phase 3: User Story 1 - Preguntar sobre un escaneado o una imagen (Priority: P1) 🎯 MVP

**Goal:** una pregunta sobre un documento solo con páginas se responde con su página, y la cita la muestra (FR-002 a FR-006b, FR-014, FR-015 y FR-020).

**Independent Test:** quickstart §5, paso 3.

### Pruebas

- [X] T015 [P] [US1] `…/test/…/data/retrieval/HybridRetrieverTest.kt` (Q1–Q5):
  - sin `withPages`, nada cambia y `pages` está vacío;
  - con `withPages`, `pageSearch` usa el mismo vector y el mismo ámbito;
  - las páginas no entran en la RRF;
  - con páginas y sin bloques, `Blocks([], pages)`;
  - `NoResults` sin nada;
  - `lists` lleva las dos listas;
  - en un seguimiento, la página se busca con el mismo vector que el texto, que lleva la pregunta anterior (analyze G4).
- [X] T016 [P] [US1] `…/test/…/data/chat/DefaultChatSessionTest.kt` (S1, S3–S7), con índice, motor e imágenes falsos y el modo `ON`:
  - una pregunta sin bloques y con página genera con la página como `[1]`;
  - la fuente citada es de tipo `PAGE`, con su número de página;
  - una imagen que falta o no es JPEG lleva a la negativa sin llamar a Gemma;
  - `[2]` fuera de rango se descarta;
  - la negativa no guarda fuentes;
  - el turno guarda `page_tokens`;
  - `AnswerPrompt.Visual.visualTokens` es 70, 140 o 280 según el detalle (analyze G1);
  - una imagen suelta da una fuente `IMAGE`.
- [X] T017 [P] [US1] `…/test/…/domain/chat/ObserveChatAvailabilityUseCaseTest.kt` (M3): con el modo `ON` y `pagesUsable`, una biblioteca solo con páginas está `Ready`. Con el modo apagado o `pagesUsable = false`, `NothingReady`, como hoy. Lo mismo con un ámbito.
- [X] T018 [P] [US1] `…/test/…/feature/chat/ChatScreenTest.kt` y un fichero nuevo, `…/test/…/core/designsystem/component/SourceCitationTest.kt` (U4–U8):
  - la tarjeta con miniatura, «Página 4 · Imagen de la página» y la de una imagen suelta, «Imagen»;
  - sin `leading`, la tarjeta es la de hoy;
  - el icono de documento mientras carga;
  - la descripción para TalkBack;
  - la marca `[n]` de una página abre `Document(documentId, page = p)`;
  - la línea de copiar.
- [X] T019 [P] [US1] `…/test/…/feature/document/DocumentScreenTest.kt` (FR-006b, U11): con el modo activo, un documento solo Visual no tiene la nota y su interruptor dice «Disponible para consultas RAG». Con el modo apagado, «Se usará al activar el modo multimodal». Se sustituyen las pruebas de F19 que decían lo contrario.

### Implementación

- [X] T020 [US1] `…/microrag/data/retrieval/HybridRetriever.kt` (Q1–Q5) y `RetrieveContextUseCase`. T015 pasa.
- [X] T021 [US1] `…/microrag/data/chat/DefaultChatSession.kt` (S1, S3–S7):
  - leer el modo, `pagesUsable` y el detalle al empezar;
  - pedir páginas;
  - cargar y comprobar la imagen;
  - `AnswerPrompt.Visual`;
  - las fuentes con `kind`;
  - `page_tokens`.

  T016 pasa.
- [X] T022 [US1] `…/microrag/domain/chat/ChatUseCases.kt` (M3 y M4), con Koin:
  - `ObserveChatAvailabilityUseCase` combina el modo y `pagesUsable`;
  - `WarmUpAnswerEngineUseCase` precarga la Gemma con visión cuando tocará.

  T017 pasa, con un caso de M4.
- [X] T023 [US1] UI de la fuente de página:
  - en `…/microrag/core/designsystem/component/Rows.kt`, con sus previews: `LisdataSourceCitation.leading`, que se ignora si la fuente no está disponible, y `LisdataPageThumbnail` (analyze C4);
  - `CitationUi.kind` en `…/microrag/core/designsystem/model/CitationUi.kt` (analyze U4);
  - `…/microrag/feature/document/PageImageLoader.kt` (R14: `BitmapFactory` con `inSampleSize`, LRU de 24, fuera del hilo principal);
  - `Document.page` en `…/microrag/core/navigation/{Destinations,MicroRagNavHost}.kt`. Va aquí y no en US5, porque la tarjeta y la marca `[n]` ya navegan con ella (analyze I6). Hasta T039, el detalle la ignora;
  - `ChatScreen`, `ChatViewModel` y `ChatUiState`, con las filas, los chips y el texto de copiar;
  - Koin del cargador.

  T018 pasa.
- [X] T024 [US1] Notas de F19 (R16) en `…/microrag/feature/document/DocumentScreen.kt`. Se borran `document_visual_only_note` y `document_include_subtitle_pages_only` de `strings_multimodal.xml` (es/en). T019 pasa y lint no avisa de textos sin usar.

**Checkpoint:** con el modo activo, un documento solo con páginas se pregunta, y su cita sale con miniatura.

---

## Phase 4: User Story 2 - Preguntar con texto y páginas a la vez (Priority: P1)

**Goal:** los bloques de siempre más la página, citados juntos, con el aviso de la espera (FR-004, FR-007, FR-008 y FR-013).

**Independent Test:** quickstart §5, paso 2 (`VisualChatBenchmarkTest` también lo recorre).

### Pruebas

- [X] T025 [P] [US2] `DefaultChatSessionTest` (S4, S6, S7):
  - con bloques y página, los bloques son los de `withPages = false` y la página va la última;
  - un bloque de la misma página se mantiene (sin regla de duplicados);
  - el límite de 2.000 tokens se aplica solo al texto;
  - las citas mezclan los dos tipos en el orden en que se citan;
  - cancelar durante el prefill termina en `CANCELLED` con lo que llegó;
  - el bucle del límite corre sobre la v4, y con un prompt justo en el límite entran los mismos bloques con página que sin ella (analyze A1);
  - con bloques y la imagen que falta, sigue con la v4 y `page_dropped=1` (analyze G3).
- [X] T026 [P] [US2] `…/test/…/feature/chat/ChatViewModelTest.kt` y `ChatScreenTest` (U3, R13):
  - `READING_PAGE` mientras no hay texto y hay `pageTokens`;
  - «Leyendo 1 página · unos 11,5 s…» con Equilibrado, «11.5» en inglés;
  - «Generando respuesta…» sin página;
  - el contador «N fuentes» cuenta la página;
  - TalkBack anuncia «Leyendo 1 página» (`ChatAnnouncement.READING_PAGE`) en lugar de «Generando respuesta» (analyze U3).

### Implementación

- [X] T027 [US2] El estado de la respuesta: `TurnStatus.READING_PAGE`, `ChatAnnouncement.READING_PAGE` y sus textos en `…/microrag/feature/chat/{ChatUiState,ChatViewModel,ChatScreen}.kt`, con la espera de `PageDetail` formateada con la configuración regional. T025 y T026 pasan.

**Checkpoint:** las respuestas con texto y página citan los dos y avisan de la espera.

---

## Phase 5: User Story 3 - Sin modo o sin páginas, todo igual (Priority: P1)

**Goal:** con el modo apagado o sin páginas en el ámbito, el chat es el de hoy (FR-001, FR-005, FR-009 y FR-023; SC-001).

**Independent Test:** quickstart §1 y §2.

### Pruebas

- [X] T028 [P] [US3] `DefaultChatSessionTest` (S2):
  - con el modo apagado y documentos con páginas, el prompt es `AnswerPrompt.Text`, idéntico byte a byte al de antes de F20;
  - con el modo `ON` y un ámbito sin páginas, lo mismo;
  - con `pagesUsable = false`, lo mismo;
  - con el modo `ON` y sin páginas, el motor recibe un `AnswerPrompt.Text` y usa la clave de hoy (I1);
  - la línea del registro solo gana los campos de S8 (`page=0`, `page_tokens`, `page_dropped` y `pages_ms`), y los demás no cambian (analyze I8).
- [X] T029 [P] [US3] Regresión del escritorio (FR-023): `run_eval.py ablations` y `order-review`, comparados con los de F19 (quickstart §2). Si cambian, se para y se investiga. (Hecho: idénticos salvo los tiempos.)

### Implementación

- [X] T030 [US3] Lo que falte para que T028 pase en `DefaultChatSession`. Si ya pasa con T021, se marca sin cambios. (Sin cambios: T021 ya lo cumple.)

**Checkpoint:** el chat de texto queda intacto.

---

## Phase 6: User Story 4 - Elegir el detalle de las páginas (Priority: P2)

**Goal:** «Detalle de las páginas» en Ajustes, con Equilibrado por defecto (FR-011 y FR-012).

**Independent Test:** quickstart §4, paso 1.

### Pruebas

- [X] T031 [P] [US4] `…/test/…/data/multimodal/DataStoreMultimodalStoreTest.kt` (M1): `page_detail` por defecto en `BALANCED`, un valor desconocido se lee como `BALANCED`, y la escritura que falla conserva el anterior.
- [X] T032 [P] [US4] `…/test/…/feature/settings/PageDetailViewModelTest.kt`, `PageDetailScreenTest.kt` y `SettingsScreenTest` (U1, U2, M2):
  - la fila solo con `ON`, con su subtítulo;
  - tres radios con su espera;
  - guardar;
  - el fallo con `PreferencesSaveFailure`;
  - con el modo apagado, el destino vuelve atrás.

### Implementación

- [X] T033 [US4] Almacén y caso de uso: `page_detail` en `…/microrag/data/multimodal/DataStoreMultimodalStore.kt`, y `SetPageDetailUseCase` en `…/microrag/domain/multimodal/MultimodalUseCases.kt`, con Koin. T031 pasa.
- [X] T034 [US4] Pantalla y fila:
  - `…/microrag/feature/settings/{PageDetailScreen,PageDetailViewModel}.kt`;
  - la fila en `MultimodalSection.kt`;
  - el destino `PageDetailSetting` en `…/microrag/core/navigation/{Destinations,MicroRagNavHost}.kt`, con `PageDetailRoute` como su composable, igual que `ImportMode` e `ImportModeRoute` (analyze I11);
  - `NavigationTest` y `AppModulesTest` se actualizan.

  T032 pasa.

**Checkpoint:** el detalle se elige y lo usa la siguiente respuesta.

---

## Phase 7: User Story 5 - Ver la página citada (Priority: P2)

**Goal:** la página en el detalle y el visor con zoom (FR-016).

**Independent Test:** quickstart §4, paso 2.

### Pruebas

- [X] T035 [P] [US5] `…/test/…/feature/document/ZoomStateTest.kt` (U13, U14):
  - la escala entre 1× y 4×;
  - el doble toque entre 1× y 2×;
  - el desplazamiento limitado a los bordes;
  - «Ampliar», «Reducir» y «Restablecer»;
  - volver a 1× al girar.
- [X] T036 [P] [US5] `…/test/…/feature/document/DocumentViewModelTest.kt` y `DocumentScreenTest` (U9, U10):
  - con `page`, la vista previa es la página, con su chip y la pista, y «Imagen» en una imagen suelta (wireframe §10);
  - el hueco con el anillo mientras carga (analyze C3);
  - pulsarla abre `PageViewer`;
  - la descripción para TalkBack;
  - con `PAGE_GONE`, el aviso y la vista previa normal.
- [X] T037 [P] [US5] `…/test/…/feature/document/PageViewerScreenTest.kt` (U12–U15): el título (solo el nombre en una imagen suelta), «Cerrar», el anillo mientras carga, las acciones de accesibilidad, la página que falta y la ruta en `NavigationTest`. Desde una cita, volver deja al usuario en el chat.

### Implementación

- [X] T038 [US5] `PageViewer` en `…/microrag/core/navigation/{Destinations,MicroRagNavHost}.kt`. `Document.page` ya viene de T023.
- [X] T039 [US5] Vista previa de página: `PreviewContent.Page` en `…/microrag/feature/document/{DocumentUiState,DocumentViewModel,DocumentScreen}.kt`, con `PageImageLoader` y el desplazamiento a la vista una vez. T036 pasa.
- [X] T040 [US5] `…/microrag/feature/document/{ZoomState,PageViewerScreen}.kt` (R15). T035 y T037 pasan.
- [X] T041 [P] [US5] `EXTRA_PAGE` en `…/debug/…/debug/DebugRouting.kt`, para la ruta `document` del recorrido en el emulador.

**Checkpoint:** la página citada se abre, se amplía y se vuelve al chat.

---

## Phase 8: User Story 6 - Recuperar del Historial una respuesta con páginas (Priority: P2)

**Goal:** el Historial restaura las citas a página y dice cuándo ya no están (FR-017 a FR-019).

**Independent Test:** las pruebas instrumentadas de esta fase y quickstart §5, paso 3.

### Pruebas

- [X] T042 [P] [US6] `…/test/…/domain/chat/ResolveSourcesUseCaseTest.kt` (H3, H4):
  - una página o una imagen de un documento borrado da `DOCUMENT_GONE`;
  - un documento creado después de la respuesta, también;
  - una página que ya no está da `PAGE_GONE`;
  - con el modo apagado, una página existente sigue `AVAILABLE`.
- [X] T043 [P] [US6] `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt` (H1, H2): ida y vuelta de las fuentes de página y de `page_tokens`, el orden mezclado y las conversaciones de antes de la v3.
- [X] T044 [P] [US6] `ChatScreenTest` (U5): una tarjeta de página con `DOCUMENT_GONE` en gris y sin poder pulsarse; con `PAGE_GONE`, se puede pulsar.

### Implementación

- [X] T045 [US6] `ResolveSourcesUseCase` con `existingPages` en `…/microrag/domain/chat/ChatUseCases.kt`, y `PAGE_GONE` en la UI del chat y en la navegación al detalle. T042 a T044 pasan.

**Checkpoint:** el Historial conserva las citas a página y dice si ya no están.

---

## Phase 9: User Story 7 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

**Goal:** wireframe, TalkBack, letra al 200 % y los dos idiomas (FR-020, FR-024; SC-008).

**Independent Test:** quickstart §4, paso 4.

- [X] T046 [P] [US7] `…/androidTest/…/feature/chat/VisualChatAccessibilityTest.kt` (U17), parametrizada en claro y en oscuro, como `MultimodalAccessibilityTest`. Recorre `PageDetailRoute`, la conversación con una tarjeta de página, la vista previa de página y el visor:
  - ATF;
  - 48 dp;
  - grupo de radios;
  - al 200 %, nada se sale del ancho.
- [X] T047 [US7] Revisión del wireframe frente a las pantallas, con los PNG de las pruebas de pantalla. Las diferencias se anotan para el informe.

---

## Phase 10: User Story 8 - Diagnóstico de búsqueda con las páginas (Priority: P3)

**Goal:** las tres listas y el contexto en debug (FR-022).

- [X] T048 [P] [US8] `app/src/testDebug/java/com/lisdatasolutions/microrag/debug/retrieval/RetrievalDiagnosticViewModelTest.kt`, donde ya existe (analyze P1): las cinco secciones de R17 y la de páginas solo con el modo activo.
- [X] T049 [US8] `…/debug/…/debug/retrieval/{RetrievalDiagnosticScreen,RetrievalDiagnosticViewModel}.kt`, con `RetrievalResult.lists` y las páginas. La verificación de release comprueba que no está en el APK. T048 pasa.

---

## Phase 11: Polish, medición y cierre

- [X] T050 [P] Registros (R18, FR-021): `theLogHasOneLineOfNumbers_andNoText` y `oneLogLinePerRetrieval_withNumbersOnly` con una página y marcadores «SECRETO» en el nombre, la etiqueta y la imagen; `AppLoggerTest` sin cambios.
- [X] T051 [P] `…/androidTest/…/rag/runtime/litert/GemmaVisionSmokeTest.kt` (`@RequiresModels`, R19): una página generada con `PdfDocument` que dice un número. Gemma, con la v5-visual y Equilibrado, lo lee y cita `[1]`. Se salta sin Gemma.
- [X] T052 [P] `…/androidTest/…/data/chat/VisualChatBenchmarkTest.kt` (`@RequiresModels`, R19, SC-003):
  - un índice temporal con la 440M;
  - un PDF generado de 12 páginas en Ambos;
  - 20 preguntas por detalle, más 20 con el modo activo y sin páginas, que usan la Gemma de hoy;
  - el coste de la primera respuesta con página tras una sin ella, que recarga Gemma (R5);
  - el p50 y el p95 del primer token, el prefill, la decodificación y el pico de RSS, en `visual-chat-benchmark` y en `filesDir/reports/visual-chat-*.json`;
  - sin umbral.
- [X] T053 Batería sin dispositivo (quickstart §1): JVM, lint y Python en verde. Las cifras van al informe. (JVM 1.354/1.354, lint 0 errores y 2 avisos de versiones, Python 288 y golden al día.)
- [X] T054 Instrumentadas en `Pixel_10` y `MicroRAG_API33` (quickstart §3), con `adb -s` y sin `@RequiresNetwork`. Se esperan los 4 fallos previos de Atrás en API 33.
- [X] T055 Recorrido en el emulador (quickstart §4), con capturas solo de datos de prueba. Se deja el emulador como estaba.
- [X] T056 [P] Verificación de release en `specs/020-visual-chat/checklists/release-verification.md`, como la de F19:
  - los mismos 9 permisos;
  - sin el diagnóstico de búsqueda ni el de F18;
  - `LiteRtTextGenerator` con visión en el APK;
  - el manifiesto y el build sin cambios.
- [X] T057 Borrador del informe `specs/020-visual-chat/report.md`, con la estructura de F19: implementado, build y tests, validación por criterio, cifras, diferencias frente al wireframe, desviaciones, fallos y pendiente.
- [ ] T058 **Fase del teléfono, con permiso del usuario** (quickstart §5):
  - se anotan los ajustes;
  - la batería completa con `GemmaVisionSmokeTest`;
  - `VisualChatBenchmarkTest`;
  - SC-004 con un escaneado y una imagen de prueba;
  - limpieza y ajustes restaurados.

  Si las esperas medidas difieren de las de F18, se corrigen `PageDetail` y los textos, con su entrada en DECISIONS. La nota «Las respuestas sin páginas no cambian» se comprueba con la cifra sin páginas (analyze C2).
- [ ] T059 Cierre, con la revisión del usuario:
  - el informe final;
  - la siguiente decisión libre de DECISIONS, con el resultado (analyze I14);
  - FEATURES en ✅;
  - la memoria del cierre;
  - integración en `main` y push, solo con su aprobación.

---

## Dependencies & Execution Order

- **Setup (T001–T003)** → **Foundational (T004–T014)**, que bloquea todo.
- **US1 (T015–T024)** es el MVP. **US2 (T025–T027)** y **US3 (T028–T030)** dependen de T021.
- **US4 (T031–T034)** depende de T008. T027 usa `PageDetail`, pero no la pantalla.
- **US5 (T035–T041)** depende de T023 (el cargador y `Document.page`) y de T038.
- **US6 (T042–T045)** depende de T010 y de T021.
- **US7 (T046–T047)** va después de US1, US4 y US5.
- **US8 (T048–T049)** depende de T020.
- **Polish (T050–T059)** va al final. T058 y T059 se preguntan siempre.

## Parallel Execution Examples

- **Foundational:** T004, T005, T006 y T007 a la vez. Después, T009, T010 y T012 a la vez (ficheros distintos), T014 después de T009, y T011 antes de T013.
- **US1:** T015 a T019 a la vez. Después, T020 → T021, con T022, T023 y T024 en paralelo.
- **US4 y US5** en paralelo, una vez cerrada US1.

## Implementation Strategy

1. **MVP:** Setup, Foundational y US1. Con eso, el chat ya responde sobre escaneados e imágenes y cita su página.
2. **Después:** US2 y US3 (P1), que cierran el comportamiento del chat.
3. **Luego:** US4, US5 y US6 (P2), y US7 al final de las pantallas.
4. **Por último:** US8, el polish, el teléfono (con permiso) y el cierre (con revisión).
