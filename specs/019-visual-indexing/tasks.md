---

description: "Lista de tareas de F19 — Indexación visual, modo «Multimodal» opcional"
---

# Tasks: Indexación visual, modo «Multimodal» opcional (F19)

**Input**: Documentos de diseño en `/specs/019-visual-indexing/`: [plan.md](plan.md), [spec.md](spec.md), [wireframe.md](wireframe.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-008). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en orden de prioridad. Las pruebas en el teléfono van en la **última fase**.

**Historias de la spec:**
- US1 — Activar el modo multimodal (P1);
- US2 — Importar un PDF como imágenes de página (P1);
- US3 — Escaneados e imágenes sueltas (P1);
- US4 — Ver y cambiar el modo de un documento (P2);
- US5 — Indexar mucho sin vigilarlo (P2);
- US6 — Desactivar el modo (P3);
- US7 — Pantallas aprobadas, accesibles y traducidas (P2).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` y `Pixel_10`;
  - teléfono `adb -s P31281001066`, **solo en la última fase**.
- **Con el teléfono conectado:**
  - nunca `connectedDebugAndroidTest`. Siempre se instalan los APK con `-s <emulador>` y se lanza `am instrument -r`, con `-e notAnnotation …RequiresNetwork`;
  - antes de una batería, `wm dismiss-keyguard` y comprobar `isKeyguardShowing=false`.
- **En el teléfono** (memorias `f11-phone-phase`, `f16-phone-pending`, `f17-closing` y `f18-closing`):
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - **activar el modo borra la 270M del teléfono:** se pregunta antes;
  - al terminar se restauran los ajustes.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US7).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

- [ ] T001 Línea base: `./gradlew :app:testDebugUnitTest :app:lintDebug` en verde (1.163 pruebas JVM, 2 avisos) y `offline/.venv/bin/python -I -m unittest discover -s offline/tests` (285 pruebas), antes de tocar nada.
- [ ] T002 [P] Textos de `multimodal-ui.md` §4 y del wireframe §7 en `app/src/main/res/values{,-en}/strings_multimodal.xml`: Ajustes, diálogos, etapas, errores, «Modo al importar PDF», Ingesta, cola, Biblioteca y detalle. Con plurales para páginas y fragmentos. `StringsParityTest` pasa.
- [ ] T003 [P] Muestra de identidad: `app/src/main/assets/embedding/identity_texts.jsonl`, con 20 líneas `{"id","text"}` tomadas de `app/src/androidTest/assets/embedding_texts.jsonl` (R3), párrafos y consultas en español.

---

## Phase 2: Foundational (adaptadores, esquema v5, embedder activo y requisitos)

**Propósito:**
- los adaptadores de imagen en `main`;
- `rag.db` v5;
- el índice que dice su embedder;
- la lista viva de modelos obligatorios.

**Bloquea todas las historias.**

### Pruebas (antes, y deben fallar)

- [ ] T004 [P] Esquema de `rag.db` v5:
  - `…/test/…/data/rag/RagSchemaTest.kt`: 15 sentencias, la migración `from = 4` y la versión 5;
  - `offline/tests/test_appconfig.py`: `schema_version == 5`.
- [ ] T005 [P] `…/androidTest/…/data/rag/RagIndexMigrationTest.kt`, con `app/src/androidTest/assets/schema/schema_v4.sql`:
  - un índice v4 con documentos, fragmentos, carpetas y sombras migra a v5 con todo intacto;
  - `index_mode = 'text'` y la tabla `pages` vacía;
  - un fallo de migración deja el fichero v4 intacto.
- [ ] T006 [P] `…/test/…/domain/rag/IndexCompatibilityCheckerTest.kt`:
  - acepta la huella de la 270M y la de la 440M (R2);
  - rechaza cualquier otra;
  - el resto de claves se valida como hoy;
  - las claves `page_*` no deciden si se abre.
- [ ] T007 [P] `…/androidTest/…/data/rag/RagIndexPagesTest.kt` (P1–P8 y E1–E3):
  - `insertPage` reanudable (`storedPages`);
  - la primera página escribe las claves `page_*`;
  - `pagesUsable` es falso con claves distintas;
  - `textEmbedder` refleja `rag_meta`;
  - `switchTextEmbedder` es transaccional, comprueba el origen y es idempotente;
  - `removeFromIndex` y `deleteDocument` devuelven los ficheros de página;
  - `publishReindex` mueve las páginas de la sombra y devuelve las del original;
  - `allPageFiles`;
  - `chatLibraryState` cuenta aparte los documentos solo Visual (P9);
  - `vector_init` de `pages` en cada conexión.
- [ ] T008 [P] `…/test/…/domain/multimodal/ModelRequirementsTest.kt` (M6): la 270M o la 440M según el índice, Gemma siempre, y la 440M añadida mientras la etapa está en marcha.
- [ ] T009 [P] `…/test/…/data/models/DefaultModelFileRepositoryTest.kt` (D1): `delete(id)` borra el fichero, su entrada de `verification.json` y su backend, y publica `Missing`. Borrar un modelo que no existe da `false`.
- [ ] T010 [P] `…/androidTest/…/data/ingest/AndroidPageRasterizerTest.kt`: la prueba `DebugPageRasterizerTest` de F18, movida al conversor de `main`:
  - páginas a 768 px sobre blanco;
  - PDF con contraseña de propietario;
  - PNG con transparencia, EXIF y CMYK;
  - JPEG de calidad 85.

### Implementación

- [ ] T011 Adaptadores en `main` (R7):
  - `…/microrag/rag/runtime/ImageEmbedder.kt`;
  - `…/microrag/rag/ingest/PageRasterizer.kt`, con `EncodedImage` e `ImageFormat`;
  - `…/microrag/rag/runtime/litert/LiteRtImageEmbedder.kt`, con `LiteRtEngineFactory.imageEmbedder(file, path, tokens)`;
  - `…/microrag/data/ingest/AndroidPageRasterizer.kt`.

  El diagnóstico de debug (`…/debug/…/feasibility/visual/VisualContracts.kt`, `LiteRtVisionEngines.kt`, `VisualSuite.kt` y `VisualFeasibilityModule.kt`) pasa a usarlos, y se borran `DebugPageRasterizer.kt` y la copia del embedder de debug. `LayeringTest` se actualiza. T010 pasa, y `VisualSuiteTest` sigue en verde.
- [ ] T012 Esquema v5:
  - `app/src/main/assets/schema.sql`;
  - `…/microrag/data/rag/RagSchema.kt`, con `RagMigration(from = 4, …)`;
  - `…/microrag/domain/rag/RagIndexConfig.kt`: `SCHEMA_VERSION = 5`, `acceptedTextEmbedders`, `PageSpace` y las claves `page_*`;
  - `…/microrag/domain/rag/IndexCompatibilityChecker.kt`;
  - `…/microrag/data/rag/RagConnection.kt`, con el `vector_init` de `pages`.

  T004 a T006 pasan.
- [ ] T013 Tipos del dominio (data-model §4):
  - `IndexMode` y `StoredDocument.indexMode`/`pagesStored` en `…/microrag/domain/rag/RagModels.kt`;
  - `StoredPage` y `PageInput`;
  - `Stage.PAGES` y los campos de páginas en `…/microrag/domain/ingest/IngestModels.kt`;
  - `DocumentFormat.JPEG`/`PNG`;
  - `IngestError.IMAGE_NEEDS_MULTIMODAL`/`IMAGE_UNREADABLE`;
  - `RunPauseCause.MODEL_SWITCH`.
- [ ] T014 Índice (contrato §1) en `…/microrag/domain/rag/RagIndex.kt` y `…/microrag/data/rag/DefaultRagIndex.kt`: E1–E3 y P1–P9. `FakeRagIndex` los implementa. T007 pasa.
- [ ] T015 `ModelFileRepository.delete(id)` en `…/microrag/data/models/DefaultModelFileRepository.kt` y en la interfaz (D1). T009 pasa.
- [ ] T016 `…/microrag/domain/multimodal/ModelRequirements.kt` (M6). Pasan a usarlo:
  - `ModelDownloadRunner`, `DefaultModelDownloadController.deleteOrphanPartials` y `ModelCatalog.allReady`;
  - los casos de uso de `domain/onboarding`, generalizados a listas de modelos (R6);
  - `SetupViewModel`, `ObserveLocalAiUseCase` y `ObserveModelOverviewUseCase`.

  T008 pasa. La preparación del primer arranque no cambia: lo comprueban sus pruebas.
- [ ] T017 Embedder activo (R2):
  - `ChunkEmbeddingSession` y `EngineQueryEmbedder` leen `RagIndex.textEmbedder`, en vez de `EG2_270M`;
  - `DataModule` resuelve la ruta del modelo activo;
  - con la 270M, todo da lo mismo que antes, comprobado con sus pruebas.

**Checkpoint:**
- esquema v5 migrado y probado;
- el índice dice su embedder;
- los requisitos de modelos dependen de él;
- los adaptadores de imagen están en `main`.

---

## Phase 3: User Story 1 - Activar el modo multimodal (Priority: P1) 🎯 MVP

**Goal:** descargar, validar y cambiar de modelo sin perder la búsqueda (FR-001 a FR-006, FR-008, FR-023).

**Independent Test:** quickstart §4, pasos 1 y 2.

### Pruebas

- [ ] T018 [P] [US1] `…/test/…/data/multimodal/DataStoreMultimodalStoreTest.kt`: los valores por defecto (`OFF`, sin error, `BOTH`), la escritura, el fichero corrupto y `write()` que falla.
- [ ] T019 [P] [US1] `…/test/…/data/multimodal/DefaultMultimodalControllerTest.kt` (M2–M4), con descarga, índice y validador falsos:
  - cada transición de R1;
  - el fallo de identidad borra la 440M y deja la 270M;
  - sin espacio, `NO_SPACE`;
  - cancelar borra el fichero a medias;
  - un corte en `SWITCHING` antes o después de la transacción se reconcilia al arrancar;
  - reactivar es directo si el índice ya está en la 440M;
  - la ingesta se pausa con `MODEL_SWITCH` y se reanuda.
- [ ] T020 [P] [US1] `…/test/…/data/multimodal/IdentityValidatorTest.kt` (M5): con embedders falsos, 0,99999 pasa y 0,9998 no; los dos modelos se usan en CPU y uno detrás de otro; el log solo lleva la cifra.
- [ ] T021 [P] [US1] `…/test/…/feature/settings/MultimodalSettingsViewModelTest.kt` y `SettingsScreenTest` (U1–U4, U7):
  - el interruptor en cada etapa;
  - los diálogos;
  - los botones de pausar, reanudar, cancelar y reintentar;
  - las filas de modelos con la 270M o la 440M;
  - el aviso «Modo multimodal activado» una sola vez.

### Implementación

- [ ] T022 [US1] Almacén y modelos: `…/microrag/domain/multimodal/MultimodalModels.kt` y `…/microrag/data/multimodal/DataStoreMultimodalStore.kt` (`multimodal.preferences_pb`), cableados en `DataModule`. T018 pasa.
- [ ] T023 [US1] `…/microrag/data/multimodal/IdentityValidator.kt`, con la muestra de T003, y `ValidateIdentityUseCase`. T020 pasa.
- [ ] T024 [US1] Controlador y casos de uso:
  - `…/microrag/data/multimodal/DefaultMultimodalController.kt`, con `start()` llamado desde `MicroRagApplication.onCreate`;
  - `…/microrag/domain/multimodal/MultimodalUseCases.kt`: `Observe`, `Enable`, `Disable`, `Pause`, `Resume`, `CancelDownload` y `SetDefaultImportMode`;
  - Koin en `DomainModule` y `DataModule`;
  - `AppModulesTest` se actualiza.

  T019 pasa.
- [ ] T025 [US1] Ajustes, según el wireframe §1–§3:
  - `…/microrag/feature/settings/MultimodalSection.kt` y `MultimodalSettingsViewModel.kt`;
  - `SettingsScreen`, con la sección y las filas de modelos del embedder activo;
  - los diálogos.

  T021 pasa.
- [ ] T026 [US1] Prueba instrumentada `…/androidTest/…/data/multimodal/MultimodalSwitchTest.kt`:
  - con un `TempRagIndex` de la 270M y los ficheros de modelo falsos, el cambio de `rag_meta` y el borrado de la 270M;
  - un proceso matado entre los pasos 2 y 4 (simulado) deja el índice abriéndose y buscando;
  - las búsquedas dan los mismos ids antes y después (SC-002).

**Checkpoint:** el modo se activa y se desactiva con todos sus estados. La búsqueda de texto nunca falla.

---

## Phase 4: User Story 2 - Importar un PDF como imágenes de página (Priority: P1)

**Goal:** Texto, Visual o Ambos al importar, y la fase de páginas (FR-009, FR-012 a FR-017).

**Independent Test:** quickstart §4, paso 3, con un PDF.

### Pruebas

- [ ] T027 [P] [US2] `…/test/…/data/ingest/IngestRunnerPagesTest.kt`, con un rasterizador y un embedder de imagen falsos:
  - Visual: sin fragmentos, todas las páginas y `done`;
  - Ambos: fragmentos y páginas, y `done` solo con los dos completos;
  - Texto: exactamente como hoy;
  - progreso `PAGES` con páginas hechas, totales y ritmo;
  - ficheros escritos con `.part` y renombrados;
  - los vectores truncados a 256 y normalizados;
  - el LLM liberado y nunca dos engines a la vez.
- [ ] T028 [P] [US2] `…/test/…/data/ingest/DocumentImporterTest.kt` (I1): el modo de la selección se registra en cada PDF solo con `ON`; sin `ON`, todo en `TEXT`.
- [ ] T029 [P] [US2] `…/test/…/feature/ingest/IngestViewModelTest.kt` e `IngestScreenTest` (U8–U10):
  - la fila «Modo de los PDF» solo con `ON`;
  - la hoja;
  - el modo por defecto de Ajustes;
  - el modo conservado al girar;
  - la cola con «Indexando páginas · 12 / 40».

### Implementación

- [ ] T030 [US2] `…/microrag/data/ingest/PageFileStore.kt` (I6) y `…/microrag/data/indexing/PageEmbeddingSession.kt` (I3), cableados en `DataModule`.
- [ ] T031 [US2] Fase de páginas en `…/microrag/data/ingest/IngestRunner.kt` (I2, R9). T027 pasa.
- [ ] T032 [US2] Modo en la importación: `DocumentImporter`, `ImportRequest.indexMode`, `DefaultImportQueueRepository` y `NewDocument.indexMode`. T028 pasa.
- [ ] T033 [US2] Ingesta, según el wireframe §5: `…/microrag/feature/ingest/IndexModeSheet.kt`, `IngestScreen` e `IngestViewModel`, y la cola con progreso en páginas. T029 pasa.

**Checkpoint:** un PDF en Visual o Ambos queda indexado con sus páginas, con progreso por páginas.

---

## Phase 5: User Story 3 - Escaneados e imágenes sueltas (Priority: P1)

**Goal:** JPG y PNG con el modo activo, y escaneados en Visual o en Ambos (FR-010, FR-011).

**Independent Test:** quickstart §4, paso 3, con un escaneado y un PNG.

### Pruebas

- [ ] T034 [P] [US3] `DocumentImporterTest`:
  - `jpg`, `jpeg` y `png` aceptados con `ON`, siempre en `VISUAL` y con `page_count = 1`;
  - rechazados sin `ON`, con `IMAGE_NEEDS_MULTIMODAL`;
  - duplicados por huella;
  - el límite de tamaño.
- [ ] T035 [P] [US3] `IngestRunnerPagesTest`:
  - un PDF sin texto en Ambos queda solo con páginas, sin error;
  - en Texto sigue en `NO_TEXT`;
  - una imagen ilegible da `IMAGE_UNREADABLE`.

### Implementación

- [ ] T036 [US3] Formatos e imágenes en `DocumentImporter`, `DocumentFormat`, `IngestRunner` y en los tipos MIME del selector de la Ingesta (U9). Errores y textos en `IngestTexts`. T034 y T035 pasan.

**Checkpoint:** un escaneado, un JPG y un PNG se indexan con el modo activo, y con el modo apagado se rechazan como dice la spec.

---

## Phase 6: User Story 4 - Ver y cambiar el modo de un documento (Priority: P2)

**Goal:** el modo en la Biblioteca y en el detalle; reindexar en otro modo, quitar y eliminar con limpieza (FR-019 a FR-021).

**Independent Test:** quickstart §4, paso 4.

### Pruebas

- [ ] T037 [P] [US4] `…/test/…/data/library/DefaultLibraryRepositoryTest.kt` (R11):
  - reindexar con modo crea la sombra con ese modo;
  - al publicar se borran los ficheros de páginas del original;
  - quitar y eliminar borran los ficheros;
  - el barrido de huérfanos.
- [ ] T038 [P] [US4] `LibraryScreenTest`, `DocumentViewModelTest` y `DocumentScreenTest` (U11–U14):
  - el subtítulo con el modo y las páginas;
  - los datos del detalle;
  - la nota de solo Visual;
  - la hoja de modo en «Reindexar»;
  - «Reindexar como páginas» solo con `ON` y `NO_TEXT`.

### Implementación

- [ ] T039 [US4] `…/microrag/data/library/DefaultLibraryRepository.kt`: reindexar con modo, la limpieza de ficheros de página (P4–P6) y el barrido de huérfanos al arrancar (I7). T037 pasa.
- [ ] T040 [US4] Biblioteca y detalle, según el wireframe §6: `LibraryScreen`, `DocumentScreen` y `DocumentViewModel`, que reutilizan `IndexModeSheet`. T038 pasa.

**Checkpoint:** el modo se ve y se cambia, y no quedan páginas huérfanas.

---

## Phase 7: User Story 5 - Indexar mucho sin vigilarlo (Priority: P2)

**Goal:** reanudar por página, y pausar por calor y por espacio (FR-014, FR-016, FR-018).

**Independent Test:** matar la app a mitad de un PDF largo, y quedarse sin espacio.

### Pruebas

- [ ] T041 [P] [US5] `IngestRunnerPagesTest`:
  - reanudar tras un corte en la página 7 de 20 sigue por la 8, sin duplicar;
  - un `.part` suelto se ignora y se rehace;
  - sin espacio antes de una página, pausa `NO_SPACE`;
  - el calor pausa entre páginas y libera el engine;
  - la falta de espacio previa da un aviso antes de empezar.

### Implementación

- [ ] T042 [US5] La comprobación de espacio previa (250 KB por página más el margen, R14) y las pausas por página en `IngestRunner`, y el aviso de la Ingesta. T041 pasa.
- [ ] T043 [US5] Prueba instrumentada `…/androidTest/…/data/ingest/VisualIngestBenchmarkTest.kt` (`@RequiresModels`):
  - un PDF de 200 páginas en Visual, con la 440M real;
  - mide las páginas por minuto y el espacio por página, y escribe un JSON en `files/reports`;
  - un segundo paso reanuda a mitad.

  Se ejecuta solo en el teléfono (T055).

**Checkpoint:** la ingesta visual se reanuda y se pausa como la de texto.

---

## Phase 8: User Story 6 - Desactivar el modo (Priority: P3)

**Goal:** apagar conservando todo, y reactivar al momento (FR-007, D-079).

**Independent Test:** quickstart §4, paso 5.

### Pruebas

- [ ] T044 [P] [US6] `DefaultMultimodalControllerTest` y `MultimodalSettingsViewModelTest`:
  - desactivar en `ON` no borra páginas ni la 440M;
  - desactivar en `DOWNLOADING` cancela la descarga;
  - reactivar es directo;
  - una indexación visual en curso se detiene al desactivar y se reanuda al reactivar;
  - la Ingesta vuelve a solo Texto.
- [ ] T045 [P] [US6] `DocumentScreenTest`: con el modo apagado y páginas guardadas, «Sus páginas necesitan el modo multimodal».

### Implementación

- [ ] T046 [US6] Desactivar y reactivar en el controlador, en los casos de uso y en Ajustes, con el diálogo del wireframe §3, y la nota del detalle. T044 y T045 pasan.

**Checkpoint:** el modo es una opción de verdad: se apaga y se enciende sin perder nada.

---

## Phase 9: User Story 7 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

**Goal:** el wireframe, TalkBack, la letra al 200 % y los textos en español y en inglés (FR-027, SC-008).

### Pruebas e implementación

- [ ] T047 [P] [US7] «Modo al importar PDF» (U5, U6):
  - `…/microrag/feature/settings/ImportModeScreen.kt` e `ImportModeViewModel.kt`;
  - la ruta `ImportModeRoute` en `core/navigation`;
  - sus pruebas: `ImportModeViewModelTest`, `ImportModeScreenTest` y `NavigationTest`.

  Con el patrón de Tema y `PreferencesSaveFailure`.
- [ ] T048 [P] [US7] Accesibilidad instrumentada (ATF) en `…/androidTest/…/feature/settings/MultimodalAccessibilityTest.kt`: Ajustes con el modo en varias etapas, «Modo al importar PDF», la hoja de la Ingesta y el detalle con páginas. Al 200 % (`fontScale = 2f`) y en oscuro.
- [ ] T049 [US7] Revisión del wireframe frente a las pantallas, en el emulador: las diferencias se anotan en `specs/019-visual-indexing/report.md` §6.

**Checkpoint:** pantallas fieles al wireframe, accesibles y traducidas.

---

## Phase 10: Polish sin teléfono

- [ ] T050 Chat (R13): `chatLibraryState` y la disponibilidad del chat no cuentan los documentos solo Visual como buscables. Lo prueban `ObserveChatAvailabilityUseCaseTest` y `ChatViewModelTest`.
- [ ] T051 Regresión del texto (FR-025, SC-001): `run_eval.py ablations` y `order-review` dan las mismas cifras que en F18. `test_appconfig.py` sigue en verde.
- [ ] T052 Release:
  - `assembleRelease`;
  - `apkanalyzer` con `JAVA_HOME`: los mismos 9 permisos y sin `feasibility`;
  - `ManifestInvariantsTest` y `NetworkConfinementTest` en verde.

  Se anota en `specs/019-visual-indexing/checklists/release-verification.md`.
- [ ] T053 Batería JVM, lint y Python (quickstart §1), y la instrumentada en `Pixel_10` y `MicroRAG_API33` (quickstart §3), sin `@RequiresNetwork`.
- [ ] T054 Recorrido en el emulador (quickstart §4) y borrador de `specs/019-visual-indexing/report.md`:
  - lo implementado;
  - build y pruebas;
  - las desviaciones;
  - los fallos corregidos.

---

## Phase 11: Última fase — teléfono de referencia y cierre

- [ ] T055 **Pedir permiso al usuario** para la fase del teléfono, con el recorrido: los ajustes que se anotan, la instalación, la activación real (**que borra la 270M del teléfono**), los documentos de prueba, la batería y la limpieza.
- [ ] T056 Fase del teléfono (quickstart §5):
  - activar el modo y medir cada paso;
  - comprobar la búsqueda antes y después, solo con etiquetas;
  - `VisualIngestBenchmarkTest` (T043);
  - un escaneado, un JPG y un PNG de prueba;
  - la batería completa;
  - las cifras, a `report.md` §4;
  - la limpieza y los ajustes restaurados.
- [ ] T057 Cierre:
  - `report.md` cerrado;
  - DECISIONS con las cifras medidas;
  - FEATURES con F19 «implementada; pendiente de la revisión de cierre».

  **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1):** sin dependencias.
- **Foundational (2):** depende de Setup y **bloquea todas las historias**. Dentro de la fase:
  - T011 va antes de T030;
  - T012 → T013 → T014;
  - T016 depende de T014 (el embedder del índice), y T017 de T014.
- **US1 (3):** depende de la fase 2. Es el MVP: sin el modo activo no hay nada visual.
- **US2 (4)** y **US3 (5):** dependen de US1, porque la importación visual exige `ON`. US3 depende de T031.
- **US4 (6):** depende de US2.
- **US5 (7):** depende de US2.
- **US6 (8):** depende de US1.
- **US7 (9):** T047 puede ir tras US1. T048 y T049, tras US4.
- **Polish (10):** tras todas las historias.
- **Teléfono (11):** al final, con permiso (T055).

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Dominio → datos → ViewModel → pantalla.
- **Paradas de consulta:**
  - la del analyze, solo si deja decisiones;
  - T055, el permiso para el teléfono;
  - T057, la revisión de cierre.

### Parallel Opportunities

- T002 y T003.
- Las pruebas T004 a T010.
- Dentro de cada historia, las pruebas marcadas [P].

---

## Implementation Strategy

### MVP (US1)

Las fases 1 a 3: el modo se activa, valida y cambia de modelo sin perder la búsqueda. Es lo que más riesgo tiene.

### Incremental

US2 y US3, después US4 y US5, después US6 y US7. Cada una se prueba sola antes de pasar a la siguiente.

### Notes

- La visión de la 440M solo corre en CPU (F18): no hay variante en GPU que probar.
- Sin pruebas comparativas ni regla de sí o no (D-078). Las cifras del teléfono son informativas.
