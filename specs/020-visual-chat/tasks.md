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

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US8).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

- [ ] T001 Línea base, antes de tocar nada:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug` en verde (1.285 pruebas JVM y 2 avisos);
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests` en verde.
- [ ] T002 [P] Textos del wireframe §9 y de `chat-pages-ui.md` en `app/src/main/res/values{,-en}/strings_visual_chat.xml`:
  - la fila y la pantalla «Detalle de las páginas»;
  - «Leyendo 1 página · unos %1$s s…»;
  - la tarjeta de página y su descripción para TalkBack;
  - la vista previa, el visor y sus acciones;
  - `document_citation_page_missing`;
  - `document_include_subtitle_needs_multimodal`.

  `StringsParityTest` pasa.
- [ ] T003 [P] Golden de la v5-visual (R4):
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

- [ ] T004 [P] `…/androidTest/…/data/rag/RagIndexPageSearchTest.kt` (P1–P3):
  - el orden por distancia y el límite `k`;
  - solo documentos indexados e incluidos;
  - el ámbito de carpetas, «General» y documentos de F17;
  - vacío con `pagesUsable = false`;
  - `ScoredPage.image` en un PNG;
  - `existingPages` tras borrar un documento y tras reindexarlo en Texto;
  - `pageFile`.
- [ ] T005 [P] Migración de `chat.db` 2 → 3:
  - `…/androidTest/…/data/chat/ChatMigrationTest.kt`, con un recurso nuevo `app/src/androidTest/assets/schema/chat_schema_v2.sql`: un `chat.db` v2 con turnos y fuentes migra con todo intacto, las fuentes como `chunk` y `page_tokens` nulo. Un fallo deja el fichero v2;
  - `…/test/…/data/chat/ChatSchemaTest.kt` (si existe, si no se crea): la versión 3 y la regla «termina donde `chat_schema.sql`».
- [ ] T006 [P] `…/test/…/rag/answer/VisualPromptGoldenTest.kt` y casos en `PromptBuilderTest` (B1–B3):
  - las partes de `buildVisual` coinciden con la golden de T003;
  - la página lleva el número `k + 1`;
  - con 0 bloques, la página es la `[1]`;
  - `estimatedTokens` solo sobre el texto;
  - `build` (la v4) no cambia (`AnswerGoldenTest` en verde).
- [ ] T007 [P] `…/test/…/data/chat/EngineAnswerEngineTest.kt` y `…/test/…/rag/runtime/EngineCoordinatorTest.kt` (G1, G2, G4 y G5), con una fábrica falsa:
  - con el modo apagado, la clave y la configuración de hoy;
  - con el modo `ON`, `VISION_GENERATOR` con `visionBackend = CPU`, 8.192 tokens y 1 imagen;
  - cambiar de modo carga la otra clave y cierra la anterior, sin dos en GPU;
  - `releaseGenerators()` y `releaseUnheld()` cierran también la de visión.

### Implementación

- [ ] T008 Tipos del dominio (data-model §3):
  - `PageDetail` y `StoredMultimodal.pageDetail` en `…/microrag/domain/multimodal/MultimodalModels.kt`;
  - `ScoredPage`, `PageKey` y las constantes de `RagParameters` en `…/microrag/domain/rag/{RagModels,RagParameters}.kt`;
  - `withPages`, `pages`, `lists` y `pagesMs` en `…/microrag/domain/retrieval/RetrievalModels.kt`;
  - `SourceKind`, `PAGE_GONE`, `AnswerPrompt`, `PromptPart`, `ActiveAnswer.pageTokens` y `ChatTurn.pageTokens` en `…/microrag/domain/chat/ChatModels.kt`;
  - `PageImageRepository` en `…/microrag/domain/library/PageImageRepository.kt`.

  Se actualizan los usos y los fakes (`FakeRagIndex`, `FakeAnswerEngine`, `InMemoryChatRepository`), sin cambiar el comportamiento: la batería JVM sigue en verde.
- [ ] T009 Índice (P1–P3) en `…/microrag/domain/rag/RagIndex.kt` y `…/microrag/data/rag/DefaultRagIndex.kt`:
  - `pageSearch`, con el SQL de R2 junto a `scopedVectorSearchSql`;
  - `existingPages`;
  - `pageFile`.

  `FakeRagIndex` los implementa. T004 pasa.
- [ ] T010 `chat.db` v3 (R9):
  - `app/src/main/assets/chat_schema.sql`;
  - `…/microrag/data/chat/ChatSchema.kt`, con `VERSION = 3` y `ChatMigration(from = 2, …)`;
  - `…/microrag/data/chat/SqliteChatRepository.kt`, que escribe y lee `kind` y `page_tokens` (H1, H2).

  T005 pasa.
- [ ] T011 Gemma con visión (R5, R6):
  - `GenerationConfig.visionBackend`/`maxNumImages`, `GenerationPrompt` y `EngineKind.VISION_GENERATOR` en `…/microrag/rag/runtime/{TextGenerator,ManagedEngine}.kt`;
  - `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt`: `EngineConfig` con visión cuando la hay, `Contents.of` con `Content.Text`/`Content.ImageBytes`, y `visualTokenBudget` fijado y restaurado (G3). La cancelación, como hoy (G6);
  - el diagnóstico de F18 pasa a usarlo: `…/debug/…/rag/runtime/litert/LiteRtVisionEngines.kt` se queda sin el generador, y se actualizan `…/debug/…/debug/feasibility/visual/{VisualContracts,VisualSuite,VisualFeasibilityModule}.kt`.

  `VisualSuiteTest` sigue en verde, y `LayeringTest` también.
- [ ] T012 `PromptBuilder.buildVisual` en `…/microrag/rag/answer/PromptBuilder.kt` (B2, B3), con `PAGE_RULE` y la etiqueta de R4. T006 pasa.
- [ ] T013 `AnswerEngine.generate(AnswerPrompt)` en `…/microrag/domain/chat/` y `…/microrag/data/chat/EngineAnswerEngine.kt` (G1, G2, G4):
  - la clave y la configuración salen del modo (`MultimodalStore.state`);
  - `warmUp()` carga la que toca;
  - Koin en `DataModule`.

  T007 pasa.
- [ ] T014 [P] `…/microrag/data/library/DefaultPageImageRepository.kt` (I1, I2), con `PageFileStore.read(name)` si no existe, y Koin. Prueba en `…/test/…/data/library/DefaultPageImageRepositoryTest.kt`: la fila o el fichero que faltan dan null, y el registro no lleva nombres.

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

- [ ] T015 [P] [US1] `…/test/…/data/retrieval/HybridRetrieverTest.kt` (Q1–Q5):
  - sin `withPages`, nada cambia y `pages` está vacío;
  - con `withPages`, `pageSearch` usa el mismo vector y el mismo ámbito;
  - las páginas no entran en la RRF;
  - con páginas y sin bloques, `Blocks([], pages)`;
  - `NoResults` sin nada;
  - `lists` lleva las dos listas.
- [ ] T016 [P] [US1] `…/test/…/data/chat/DefaultChatSessionTest.kt` (S1, S3–S7), con índice, motor e imágenes falsos y el modo `ON`:
  - una pregunta sin bloques y con página genera con la página como `[1]`;
  - la fuente citada es de tipo `PAGE`, con su número de página;
  - una imagen que falta o no es JPEG lleva a la negativa sin llamar a Gemma;
  - `[2]` fuera de rango se descarta;
  - la negativa no guarda fuentes;
  - el turno guarda `page_tokens`.
- [ ] T017 [P] [US1] `…/test/…/domain/chat/ObserveChatAvailabilityUseCaseTest.kt` (M3): con el modo `ON` y `pagesUsable`, una biblioteca solo con páginas está `Ready`. Con el modo apagado o `pagesUsable = false`, `NothingReady`, como hoy. Lo mismo con un ámbito.
- [ ] T018 [P] [US1] `…/test/…/feature/chat/ChatScreenTest.kt` y `…/test/…/core/designsystem/component/RowsTest.kt` (U4–U8):
  - la tarjeta con miniatura, «Página 4 · Imagen de la página» y la de una imagen suelta, «Imagen»;
  - sin `leading`, la tarjeta es la de hoy;
  - el icono de documento mientras carga;
  - la descripción para TalkBack;
  - la marca `[n]` de una página abre `Document(documentId, page = p)`;
  - la línea de copiar.
- [ ] T019 [P] [US1] `…/test/…/feature/document/DocumentScreenTest.kt` (FR-006b, U11): con el modo activo, un documento solo Visual no tiene la nota y su interruptor dice «Disponible para consultas RAG». Con el modo apagado, «Se usará al activar el modo multimodal». Se sustituyen las pruebas de F19 que decían lo contrario.

### Implementación

- [ ] T020 [US1] `…/microrag/data/retrieval/HybridRetriever.kt` (Q1–Q5) y `RetrieveContextUseCase`. T015 pasa.
- [ ] T021 [US1] `…/microrag/data/chat/DefaultChatSession.kt` (S1, S3–S7):
  - leer el modo, `pagesUsable` y el detalle al empezar;
  - pedir páginas;
  - cargar y comprobar la imagen;
  - `AnswerPrompt.Visual`;
  - las fuentes con `kind`;
  - `page_tokens`.

  T016 pasa.
- [ ] T022 [US1] `…/microrag/domain/chat/ChatUseCases.kt` (M3), con `ObserveChatAvailabilityUseCase` combinando el modo y `pagesUsable`, y Koin. T017 pasa.
- [ ] T023 [US1] UI de la fuente de página:
  - `LisdataSourceCitation.leading` en `…/microrag/core/designsystem/component/Rows.kt`, con su preview;
  - `…/microrag/feature/document/PageImageLoader.kt` (R14: `BitmapFactory` con `inSampleSize`, LRU de 24, fuera del hilo principal);
  - `…/microrag/feature/chat/PageThumbnail.kt`;
  - `ChatScreen`, `ChatViewModel` y `ChatUiState`, con las filas, los chips y el texto de copiar;
  - Koin del cargador.

  T018 pasa.
- [ ] T024 [US1] Notas de F19 (R16) en `…/microrag/feature/document/DocumentScreen.kt`. Se borran `document_visual_only_note` y `document_include_subtitle_pages_only` de `strings_multimodal.xml` (es/en). T019 pasa y lint no avisa de textos sin usar.

**Checkpoint:** con el modo activo, un documento solo con páginas se pregunta, y su cita sale con miniatura.

---

## Phase 4: User Story 2 - Preguntar con texto y páginas a la vez (Priority: P1)

**Goal:** los bloques de siempre más la página, citados juntos, con el aviso de la espera (FR-004, FR-007, FR-008 y FR-013).

**Independent Test:** quickstart §5, paso 2 (`VisualChatBenchmarkTest` también lo recorre).

### Pruebas

- [ ] T025 [P] [US2] `DefaultChatSessionTest` (S4, S6, S7):
  - con bloques y página, los bloques son los de `withPages = false` y la página va la última;
  - un bloque de la misma página se mantiene (sin regla de duplicados);
  - el límite de 2.000 tokens se aplica solo al texto;
  - las citas mezclan los dos tipos en el orden en que se citan;
  - cancelar durante el prefill termina en `CANCELLED` con lo que llegó.
- [ ] T026 [P] [US2] `…/test/…/feature/chat/ChatViewModelTest.kt` y `ChatScreenTest` (U3, R13):
  - `READING_PAGE` mientras no hay texto y hay `pageTokens`;
  - «Leyendo 1 página · unos 11,5 s…» con Equilibrado, «11.5» en inglés;
  - «Generando respuesta…» sin página;
  - el contador «N fuentes» cuenta la página.

### Implementación

- [ ] T027 [US2] El estado de la respuesta: `TurnStatus.READING_PAGE` y su texto en `…/microrag/feature/chat/{ChatUiState,ChatViewModel,ChatScreen}.kt`, con la espera de `PageDetail` formateada con la configuración regional. T025 y T026 pasan.

**Checkpoint:** las respuestas con texto y página citan los dos y avisan de la espera.

---

## Phase 5: User Story 3 - Sin modo o sin páginas, todo igual (Priority: P1)

**Goal:** con el modo apagado o sin páginas en el ámbito, el chat es el de hoy (FR-001, FR-005, FR-009 y FR-023; SC-001).

**Independent Test:** quickstart §1 y §2.

### Pruebas

- [ ] T028 [P] [US3] `DefaultChatSessionTest` (S2):
  - con el modo apagado y documentos con páginas, el prompt es `AnswerPrompt.Text`, idéntico byte a byte al de antes de F20;
  - con el modo `ON` y un ámbito sin páginas, lo mismo;
  - con `pagesUsable = false`, lo mismo;
  - el registro no cambia salvo `page=0`.
- [ ] T029 [P] [US3] Regresión del escritorio (FR-023): `run_eval.py ablations` y `order-review`, comparados con los de F19 (quickstart §2). Si cambian, se para y se investiga.

### Implementación

- [ ] T030 [US3] Lo que falte para que T028 pase en `DefaultChatSession`. Si ya pasa con T021, se marca sin cambios.

**Checkpoint:** el chat de texto queda intacto.

---

## Phase 6: User Story 4 - Elegir el detalle de las páginas (Priority: P2)

**Goal:** «Detalle de las páginas» en Ajustes, con Equilibrado por defecto (FR-011 y FR-012).

**Independent Test:** quickstart §4, paso 1.

### Pruebas

- [ ] T031 [P] [US4] `…/test/…/data/multimodal/DataStoreMultimodalStoreTest.kt` (M1): `page_detail` por defecto en `BALANCED`, un valor desconocido se lee como `BALANCED`, y la escritura que falla conserva el anterior.
- [ ] T032 [P] [US4] `…/test/…/feature/settings/PageDetailViewModelTest.kt`, `PageDetailScreenTest.kt` y `SettingsScreenTest` (U1, U2, M2):
  - la fila solo con `ON`, con su subtítulo;
  - tres radios con su espera;
  - guardar;
  - el fallo con `PreferencesSaveFailure`;
  - con el modo apagado, la ruta vuelve atrás.

### Implementación

- [ ] T033 [US4] Almacén y caso de uso: `page_detail` en `…/microrag/data/multimodal/DataStoreMultimodalStore.kt`, y `SetPageDetailUseCase` en `…/microrag/domain/multimodal/MultimodalUseCases.kt`, con Koin. T031 pasa.
- [ ] T034 [US4] Pantalla y fila:
  - `…/microrag/feature/settings/{PageDetailScreen,PageDetailViewModel}.kt`;
  - la fila en `MultimodalSection.kt`;
  - `PageDetailRoute` en `…/microrag/core/navigation/{Destinations,MicroRagNavHost}.kt`;
  - `NavigationTest` y `AppModulesTest` se actualizan.

  T032 pasa.

**Checkpoint:** el detalle se elige y lo usa la siguiente respuesta.

---

## Phase 7: User Story 5 - Ver la página citada (Priority: P2)

**Goal:** la página en el detalle y el visor con zoom (FR-016).

**Independent Test:** quickstart §4, paso 2.

### Pruebas

- [ ] T035 [P] [US5] `…/test/…/feature/document/ZoomStateTest.kt` (U13, U14):
  - la escala entre 1× y 4×;
  - el doble toque entre 1× y 2×;
  - el desplazamiento limitado a los bordes;
  - «Ampliar», «Reducir» y «Restablecer»;
  - volver a 1× al girar.
- [ ] T036 [P] [US5] `…/test/…/feature/document/DocumentViewModelTest.kt` y `DocumentScreenTest` (U9, U10):
  - con `page`, la vista previa es la página, con su chip y la pista;
  - pulsarla abre `PageViewer`;
  - la descripción para TalkBack;
  - con `PAGE_GONE`, el aviso y la vista previa normal.
- [ ] T037 [P] [US5] `…/test/…/feature/document/PageViewerScreenTest.kt` (U12–U15): el título, «Cerrar», las acciones de accesibilidad, la página que falta y la ruta en `NavigationTest`. Desde una cita, volver deja al usuario en el chat.

### Implementación

- [ ] T038 [US5] `Document.page` y `PageViewer` en `…/microrag/core/navigation/{Destinations,MicroRagNavHost}.kt`. La cita a página navega con `page`.
- [ ] T039 [US5] Vista previa de página: `PreviewContent.Page` en `…/microrag/feature/document/{DocumentUiState,DocumentViewModel,DocumentScreen}.kt`, con `PageImageLoader` y el desplazamiento a la vista una vez. T036 pasa.
- [ ] T040 [US5] `…/microrag/feature/document/{ZoomState,PageViewerScreen}.kt` (R15). T035 y T037 pasan.
- [ ] T041 [P] [US5] `EXTRA_PAGE` en `…/debug/…/debug/DebugRouting.kt`, para la ruta `document` del recorrido en el emulador.

**Checkpoint:** la página citada se abre, se amplía y se vuelve al chat.

---

## Phase 8: User Story 6 - Recuperar del Historial una respuesta con páginas (Priority: P2)

**Goal:** el Historial restaura las citas a página y dice cuándo ya no están (FR-017 a FR-019).

**Independent Test:** las pruebas instrumentadas de esta fase y quickstart §5, paso 3.

### Pruebas

- [ ] T042 [P] [US6] `…/test/…/domain/chat/ResolveSourcesUseCaseTest.kt` (H3, H4):
  - una página de un documento borrado da `DOCUMENT_GONE`;
  - un documento creado después de la respuesta, también;
  - una página que ya no está da `PAGE_GONE`;
  - con el modo apagado, una página existente sigue `AVAILABLE`.
- [ ] T043 [P] [US6] `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt` (H1, H2): ida y vuelta de las fuentes de página y de `page_tokens`, el orden mezclado y las conversaciones de antes de la v3.
- [ ] T044 [P] [US6] `ChatScreenTest` (U5): una tarjeta de página con `DOCUMENT_GONE` en gris y sin poder pulsarse; con `PAGE_GONE`, se puede pulsar.

### Implementación

- [ ] T045 [US6] `ResolveSourcesUseCase` con `existingPages` en `…/microrag/domain/chat/ChatUseCases.kt`, y `PAGE_GONE` en la UI del chat y en la navegación al detalle. T042 a T044 pasan.

**Checkpoint:** el Historial conserva las citas a página y dice si ya no están.

---

## Phase 9: User Story 7 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

**Goal:** wireframe, TalkBack, letra al 200 % y los dos idiomas (FR-020, FR-024; SC-008).

**Independent Test:** quickstart §4, paso 4.

- [ ] T046 [P] [US7] `…/androidTest/…/feature/chat/VisualChatAccessibilityTest.kt` (U17), parametrizada en claro y en oscuro, como `MultimodalAccessibilityTest`. Recorre `PageDetailRoute`, la conversación con una tarjeta de página, la vista previa de página y el visor:
  - ATF;
  - 48 dp;
  - grupo de radios;
  - al 200 %, nada se sale del ancho.
- [ ] T047 [US7] Revisión del wireframe frente a las pantallas, con los PNG de las pruebas de pantalla. Las diferencias se anotan para el informe.

---

## Phase 10: User Story 8 - Diagnóstico de búsqueda con las páginas (Priority: P3)

**Goal:** las tres listas y el contexto en debug (FR-022).

- [ ] T048 [P] [US8] `…/test/…/debug/retrieval/RetrievalDiagnosticViewModelTest.kt`: las cinco secciones de R17 y la de páginas solo con el modo activo.
- [ ] T049 [US8] `…/debug/…/debug/retrieval/{RetrievalDiagnosticScreen,RetrievalDiagnosticViewModel}.kt`, con `RetrievalResult.lists` y las páginas. La verificación de release comprueba que no está en el APK. T048 pasa.

---

## Phase 11: Polish, medición y cierre

- [ ] T050 [P] Registros (R18, FR-021): `theLogHasOneLineOfNumbers_andNoText` y `oneLogLinePerRetrieval_withNumbersOnly` con una página y marcadores «SECRETO» en el nombre, la etiqueta y la imagen; `AppLoggerTest` sin cambios.
- [ ] T051 [P] `…/androidTest/…/rag/runtime/litert/GemmaVisionSmokeTest.kt` (`@RequiresModels`, R19): una página generada con `PdfDocument` que dice un número. Gemma, con la v5-visual y Equilibrado, lo lee y cita `[1]`. Se salta sin Gemma.
- [ ] T052 [P] `…/androidTest/…/data/chat/VisualChatBenchmarkTest.kt` (`@RequiresModels`, R19, SC-003):
  - un índice temporal con la 440M;
  - un PDF generado de 12 páginas en Ambos;
  - 20 preguntas por detalle, más 20 con el modo activo y sin páginas;
  - el p50 y el p95 del primer token, el prefill, la decodificación y el pico de RSS, en `visual-chat-benchmark` y en `filesDir/reports/visual-chat-*.json`;
  - sin umbral.
- [ ] T053 Batería sin dispositivo (quickstart §1): JVM, lint y Python en verde. Las cifras van al informe.
- [ ] T054 Instrumentadas en `Pixel_10` y `MicroRAG_API33` (quickstart §3), con `adb -s` y sin `@RequiresNetwork`. Se esperan los 4 fallos previos de Atrás en API 33.
- [ ] T055 Recorrido en el emulador (quickstart §4), con capturas solo de datos de prueba. Se deja el emulador como estaba.
- [ ] T056 [P] Verificación de release en `specs/020-visual-chat/checklists/release-verification.md`, como la de F19:
  - los mismos 9 permisos;
  - sin el diagnóstico de búsqueda ni el de F18;
  - `LiteRtTextGenerator` con visión en el APK;
  - el manifiesto y el build sin cambios.
- [ ] T057 Borrador del informe `specs/020-visual-chat/report.md`, con la estructura de F19: implementado, build y tests, validación por criterio, cifras, diferencias frente al wireframe, desviaciones, fallos y pendiente.
- [ ] T058 **Fase del teléfono, con permiso del usuario** (quickstart §5):
  - se anotan los ajustes;
  - la batería completa con `GemmaVisionSmokeTest`;
  - `VisualChatBenchmarkTest`;
  - SC-004 con un escaneado y una imagen de prueba;
  - limpieza y ajustes restaurados.

  Si las esperas medidas difieren de las de F18, se corrigen `PageDetail` y los textos, con su entrada en DECISIONS.
- [ ] T059 Cierre, con la revisión del usuario:
  - el informe final;
  - D-085 con el resultado;
  - FEATURES en ✅;
  - la memoria del cierre;
  - integración en `main` y push, solo con su aprobación.

---

## Dependencies & Execution Order

- **Setup (T001–T003)** → **Foundational (T004–T014)**, que bloquea todo.
- **US1 (T015–T024)** es el MVP. **US2 (T025–T027)** y **US3 (T028–T030)** dependen de T021.
- **US4 (T031–T034)** depende de T008. T027 usa `PageDetail`, pero no la pantalla.
- **US5 (T035–T041)** depende de T023 (el cargador) y de T038.
- **US6 (T042–T045)** depende de T010 y de T021.
- **US7 (T046–T047)** va después de US1, US4 y US5.
- **US8 (T048–T049)** depende de T020.
- **Polish (T050–T059)** va al final. T058 y T059 se preguntan siempre.

## Parallel Execution Examples

- **Foundational:** T004, T005, T006 y T007 a la vez. Después, T009, T010, T012 y T014 a la vez (ficheros distintos), y T011 antes de T013.
- **US1:** T015 a T019 a la vez. Después, T020 → T021, con T022, T023 y T024 en paralelo.
- **US4 y US5** en paralelo, una vez cerrada US1.

## Implementation Strategy

1. **MVP:** Setup, Foundational y US1. Con eso, el chat ya responde sobre escaneados e imágenes y cita su página.
2. **Después:** US2 y US3 (P1), que cierran el comportamiento del chat.
3. **Luego:** US4, US5 y US6 (P2), y US7 al final de las pantallas.
4. **Por último:** US8, el polish, el teléfono (con permiso) y el cierre (con revisión).
