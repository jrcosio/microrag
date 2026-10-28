---

description: "Lista de tareas de F08 — Biblioteca y detalle de documento"
---

# Tasks: Biblioteca y detalle de documento (F08)

**Input**: Documentos de diseño en `/specs/008-library-detail/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-010). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en el orden de la spec:
- US1 — Encontrar cualquier documento y ver su estado (P1);
- US2 — Decidir qué documentos usa el asistente (P1);
- US3 — Ver el detalle real de un documento, también desde una cita (P1);
- US4 — Mantener la biblioteca: eliminar, quitar del índice y reindexar (P2);
- US5 — Pantallas fieles, accesibles y coherentes (P2).

**Entorno** (quickstart):
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Dispositivos:
  - teléfono de referencia: `adb -s P31281001066`;
  - emulador `Pixel_10`: `emulator-5554`;
  - AVD `MicroRAG_API33`: `emulator-5556`.

  **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument` (quickstart §3).
- Python: siempre `offline/.venv/bin/python -I`.
- En el teléfono: **nunca** se nombra, se borra ni se toca el documento propio del usuario (id 1); solo los de prueba de F07 (ids 2–9).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**UI pendiente de aprobación**: las propuestas B1–B8 y D1–D11 de contracts/library-ui.md se aprueban en la parada tras analyze. Las tareas que las implementan dan por hecha esa aprobación:
- T020: B1–B4;
- T026: B5 y B6;
- T034: D1–D3, D7, D9 y D11;
- T042: D4–D6, D8 y D10;
- T048: B7.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (esquema v3)

**Purpose**: la migración, primero y verificada en el teléfono (riesgo 1 del plan).

- [X] T001 Ampliar `…/test/…/data/rag/RagSchemaTest.kt`: la migración `from = 2` existe y añade `name_norm`, `replaces_id` e `idx_documents_created` (R1). `schema.sql` describe la v3 completa.
- [X] T002 Ampliar `…/androidTest/…/data/rag/RagIndexMigrationTest.kt` sobre SQLite real:
  - un índice v2 con documentos en todos los estados y fragmentos migra a v3;
  - `name_norm` queda relleno al abrir (plegado de R3);
  - los fragmentos, el FTS y las búsquedas siguen igual;
  - borrar un documento con una sombra borra la sombra en cascada.
- [X] T003 Implementar R1:
  - `app/src/main/assets/schema.sql` en v3;
  - `RagMigration(from = 2, …)` en `…/microrag/data/rag/RagSchema.kt`;
  - `SCHEMA_VERSION = 3` en `…/microrag/domain/rag/RagIndexConfig.kt`;
  - el relleno idempotente de `name_norm` al abrir, en `…/microrag/data/rag/DefaultRagIndex.kt`.

  Comprobar T001 y T002.
- [X] T004 [P] Crear `…/microrag/domain/rag/DocumentNames.kt` (`fold`: NFD, sin `\p{Mn}`, `Locale.ROOT`) con `…/test/…/domain/rag/DocumentNamesTest.kt`:
  - «Guía» → «guia», «CATÁLOGO» → «catalogo», «Ñandú» → «nandu»;
  - los comodines `%`, `_` y `\` se conservan para escaparlos después.

  Usarlo en `addDocument` y en el relleno de T003.
- [X] T005 [P] `offline/tests/test_appconfig.py` espera la v3; anotar en `offline/README.md` que hay que reconstruir los índices de evaluación. Ejecutar las pruebas Python.
- [ ] T006 **Verificación temprana en el teléfono** (quickstart §4.1):
  1. instalar sobre los datos de F07;
  2. el índice abre `Compatible`;
  3. copiar la base con `run-as` y comprobar `schema_version = 3` y `name_norm` relleno en todos los documentos, sin mostrar el nombre del id 1.

  **Si la migración falla, parar y consultar al usuario.**

**Checkpoint**: el índice del teléfono está en v3 sin perder nada.

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: operaciones del índice, tipos y contrato del repositorio.

### Tests

- [X] T007 [P] Crear `…/androidTest/…/data/rag/RagIndexLibraryTest.kt` sobre SQLite real (R2, R4, R5, R6):
  - **`libraryPage`:** del más reciente al más antiguo; filtros ALL, INDEXED y PENDING (este con `error` y `cancelled`); búsqueda sin acentos y con `%` y `_` literales; `limit`; sin sombras; estado de la sombra (en curso o en error);
  - **`libraryCount`:** cuenta con los mismos filtros;
  - **`addReindexShadow`:**
    - copia nombre, tipo, tamaño y copia privada, y guarda `replaces_id` y `<hash>#reindex`;
    - falla con una sombra viva o si el original no es `done`;
    - las búsquedas no la ven y la cuentan en *m*;
  - **`publishReindex`:** con búsquedas antes y después. El original termina con exactamente los fragmentos de la sombra (mismos `id`, FTS válido), conserva `included`, la sombra desaparece y `quant_dirty` = 1;
  - **`removeFromIndex`:** 0 fragmentos, 0 filas FTS, sin sombras, `cancelled`, `included` conservado, `quant_dirty` = 1;
  - **`deleteDocument`:** marca `quant_dirty`.
- [ ] T008 [P] Ampliar `…/test/…/architecture/LayeringTest.kt`: `feature/library` y `feature/document` no importan `data`, SQLite, WorkManager ni `java.io.File`.

### Implementation

- [X] T009 `…/microrag/domain/rag/RagModels.kt`:
  - `CANCELLED` pasa a `OUT_OF_INDEX("cancelled")`, con la explicación de R4;
  - `StoredDocument.replacesId: Long?`.

  Ajustar los usos (ninguno en lógica: comprobar con `grep`).
- [X] T010 Añadir a `…/microrag/domain/rag/RagIndex.kt` e implementar en `DefaultRagIndex.kt` las operaciones de data-model §4: `libraryPage`, `libraryCount`, `addReindexShadow`, `shadowOf`, `publishReindex`, `removeFromIndex`, más `quant_dirty` en `deleteDocument`. Comprobar T007 en el emulador.
- [X] T011 [P] Ampliar `…/test/…/testing/FakeRagIndex.kt` con las operaciones nuevas, con la misma semántica (sombras ocultas, cascada y sustitución). Añadir un contador de lecturas de fragmentos para L1.
- [X] T012 [P] Crear `…/microrag/domain/library/LibraryModels.kt` (data-model §3) y `LibraryRepository.kt` (contracts/library-api.md).
- [X] T013 [P] Crear `app/src/main/res/values{,-en}/strings_library.xml` con todos los textos de contracts/library-ui.md, en español e inglés, con plurales.
- [ ] T014 Registrar en Koin (`DataModule`, `DomainModule` y `FeatureModule`) el repositorio y los casos de uso, aún sin pantallas.

**Checkpoint**: el índice ofrece todo lo que necesitan las dos pantallas.

---

## Phase 3: User Story 1 - Encontrar cualquier documento y ver su estado (Priority: P1) 🎯 MVP

**Goal**: la Biblioteca lista, busca y filtra todos los documentos con su estado real (PNG 06).

**Independent Test**: con documentos en todos los estados, buscar «manual», filtrar «Indexados», abrir uno, volver y girar: búsqueda, filtro y posición se conservan. Con 1.000 documentos, fluida y sin leer fragmentos.

### Tests for User Story 1

- [ ] T015 [P] [US1] Crear `…/test/…/data/library/DefaultLibraryRepositoryTest.kt` (parte de la lista):
  - `page` emite `Loaded` con los estados de R10 y el total;
  - se recalcula con `documentChanges` y con cada consulta;
  - nunca lee fragmentos (L1, con el contador de T011);
  - `Unavailable` con el índice incompatible (L12);
  - la primera observación lanza la limpieza de huérfanos una sola vez (R6);
  - logs sin nombres (L11).
- [ ] T016 [P] [US1] Crear `…/test/…/feature/library/LibraryViewModelTest.kt`:
  - la consulta vive en el `SavedStateHandle` (texto, filtro y límite, R9);
  - el texto se pliega;
  - `loadMore` suma 50 solo si `hasMore`;
  - «Limpiar filtros» vuelve a ALL y vacía el texto;
  - estados de carga, vacío, sin resultados, error y no disponible.
- [ ] T017 [P] [US1] Crear `…/test/…/feature/library/LibraryScreenTest.kt`:
  - el estado del PNG 06 con todos sus textos;
  - las filas de R10;
  - «+» e «Importar documentos» llaman a abrir la Ingesta;
  - una fila abre el detalle;
  - B1–B4;
  - con `StateRestorationTester`, la búsqueda, el filtro y la posición de la lista se conservan al recrear la pantalla (FR-006, SC-002).

### Implementation for User Story 1

- [ ] T018 [US1] Crear `…/microrag/data/library/DefaultLibraryRepository.kt` con `page` (R2, L1, L2, L12) y la limpieza de huérfanos compartida, extraída de la Ingesta a `…/microrag/data/ingest/DocumentFiles.kt` (R6). Crear `ObserveLibraryUseCase`. Comprobar T015.
- [ ] T019 [US1] Crear `…/microrag/feature/library/LibraryUiState.kt`, `LibraryTexts.kt` y `LibraryViewModel.kt` (R9). Comprobar T016.
- [ ] T020 [US1] Crear `…/microrag/feature/library/LibraryScreen.kt` (`LibraryRoute` y `LibraryScreen` sin estado):
  - el PNG 06 con los componentes de contracts/library-ui.md;
  - `LazyColumn` con `rememberLazyListState` y carga al acercarse al final;
  - estados B1–B4.

  Comprobar T017.
- [ ] T021 [US1] En `…/microrag/core/navigation/MicroRagNavHost.kt`, `Library` pasa a `LibraryRoute`: «+» → `Ingest` y fila → `Document(id)`. Atrás → Inicio (G08). Ampliar `NavigationTest`.
- [ ] T022 [US1] Crear `…/androidTest/…/data/library/LibraryBenchmarkTest.kt` (R11): 1.000 documentos sintéticos, primera página y recuento (p50 y p95), cada bloque siguiente de 50, recorrido completo, y el mismo tiempo con 0 y con 50.000 fragmentos. Ejecutarla en el teléfono (SC-001: primera página en 1 s o menos y cada bloque en 100 ms o menos).

**Checkpoint**: la Biblioteca se usa de verdad (MVP).

---

## Phase 4: User Story 2 - Decidir qué documentos usa el asistente (Priority: P1)

**Goal**: el interruptor de inclusión, persistido y respetado por las búsquedas.

**Independent Test**: apagar un documento indexado; una búsqueda que lo devolvía ya no lo hace y sus fragmentos siguen ahí. Encenderlo; vuelve. Deshabilitado en los no indexados.

### Tests for User Story 2

- [ ] T023 [P] [US2] Ampliar `DefaultLibraryRepositoryTest`: `setIncluded` solo escribe en `done` (L3) y registra `library include id=… included=…`.
- [ ] T024 [P] [US2] Crear `…/androidTest/…/data/library/InclusionSearchTest.kt` sobre SQLite-Vector real: tras excluir, `vectorSearch` y `textSearch` no devuelven fragmentos del documento y sus fragmentos siguen guardados; tras incluir, vuelven (SC-003).
- [ ] T025 [P] [US2] Ampliar `LibraryViewModelTest` y `LibraryScreenTest`:
  - el interruptor llama al caso de uso;
  - en un no indexado está deshabilitado y TalkBack dice «Se podrá incluir cuando esté indexado» (B5);
  - excluido (B6);
  - varias pulsaciones seguidas: se guarda la última (caso límite).

### Implementation for User Story 2

- [ ] T026 [US2] `SetIncludedUseCase` y el interruptor de las filas, con B5 y B6. Comprobar T023–T025.

**Checkpoint**: el usuario controla las fuentes del RAG.

---

## Phase 5: User Story 3 - Ver el detalle real de un documento, también desde una cita (Priority: P1)

**Goal**: el detalle del PNG 07 con cifras reales, inclusión y vista previa, también desde una cita.

**Independent Test**: un PDF indexado muestra cifras reales y la página. Un TXT, sin chip. Con un `chunkId`, ese fragmento resaltado con las frases vecinas. Un documento inexistente avisa y vuelve, sin cierre.

### Tests for User Story 3

- [ ] T027 [P] [US3] Crear `…/test/…/data/library/PreviewTextTest.kt` (R7): última frase del anterior y primera del siguiente, con abreviaturas comunes («pág.», «art.»), sin puntuación final y con un fragmento de una sola frase.
- [ ] T028 [P] [US3] Ampliar `DefaultLibraryRepositoryTest`:
  - `detail` con los estados de R10 y el progreso guardado;
  - `null` al desaparecer;
  - `preview` sin cita (ordinal 0 y su página);
  - con cita válida: fragmento resaltado y vecinos;
  - con cita de otro documento o inexistente: `citedChunkMissing` (L9);
  - no indexado: `null`;
  - índice incompatible: `DetailResult.Unavailable`, distinto de `NotFound` (L12, FR-023);
  - copia privada ausente: `copyMissing` (D10).
- [ ] T029 [P] [US3] Crear `…/test/…/feature/document/DocumentViewModelTest.kt`: carga, `NotFound` una sola vez (R8), la inclusión, y la vista previa recargada al cambiar el documento.
- [ ] T030 [P] [US3] Crear `…/test/…/feature/document/DocumentScreenTest.kt`:
  - el estado del PNG 07 con sus textos;
  - formatos de tamaño, fecha y «—» con su lectura;
  - chip de página solo con página;
  - resaltado leído como «Fragmento citado: …» (A3);
  - D1–D3, D7, D9 y D11.

### Implementation for User Story 3

- [ ] T031 [US3] Crear `…/microrag/data/library/PreviewText.kt` (`BreakIterator` en español). Comprobar T027.
- [ ] T032 [US3] Implementar en `DefaultLibraryRepository` `detail` y `preview` (R7, R10, L9). Crear `ObserveDocumentUseCase` y `LoadPreviewUseCase`. Comprobar T028.
- [ ] T033 [US3] Crear `…/microrag/feature/document/DocumentUiState.kt`, `DocumentTexts.kt` y `DocumentViewModel.kt`, con la ruta `Document(documentId, chunkId)`. Comprobar T029.
- [ ] T034 [US3] Crear `…/microrag/feature/document/DocumentScreen.kt`: el PNG 07 con los componentes de contracts/library-ui.md, la vista previa con `citationHighlight` y los estados D1–D3, D7, D9 y D11. Comprobar T030.
- [ ] T035 [US3] Navegación (R8):
  - `Document` pasa a `DocumentRoute`;
  - al desaparecer el documento: atrás y resultado en el `SavedStateHandle` de la entrada anterior;
  - la Biblioteca muestra el *snackbar* (B8);
  - «Ver estado de la indexación» → `Index`.

  `EXTRA_CHUNK_ID` en `app/src/debug/…/DebugRouting.kt`. Ampliar `NavigationTest`: fila → detalle → Atrás, desaparecido y ruta con `chunkId`.

**Checkpoint**: trazabilidad completa; F11 tiene su destino.

---

## Phase 6: User Story 4 - Mantener la biblioteca: eliminar, quitar del índice y reindexar (Priority: P2)

**Goal**: las tres acciones, sin dejar nunca el índice a medias y coherentes con F06 y F07.

**Independent Test**:
- eliminar no deja restos;
- quitar del índice deja la copia y «Fuera del índice», fuera de la cola;
- reindexar mantiene la versión anterior hasta sustituirla, también tras matar el proceso.

### Tests for User Story 4

- [ ] T036 [P] [US4] Ampliar `DefaultLibraryRepositoryTest`:
  - **`delete`** (L4): índice, tanda sin el documento ni su sombra, copia borrada; la sombra no borra la copia; si el borrado de la copia falla, queda para la limpieza de huérfanos;
  - **`removeFromIndex`** (L5);
  - **`reindex`** en cada estado (L6): sombra, tanda, quitar la pausa y encolar; sin efecto con una sombra viva ni sin la copia privada;
  - **`discardReindex`** (L8).
- [ ] T037 [P] [US4] Ampliar `…/test/…/data/ingest/IngestRunnerTest.kt` (L7):
  - una sombra se trocea, se embebe y, al cerrarla, se llama a `publishReindex` y nunca se pasa a `DONE`;
  - si el original desaparece a mitad, la sombra se trata como desaparecida;
  - la reanudación tras una interrupción funciona sobre la sombra.
- [ ] T038 [P] [US4] Ampliar `…/test/…/data/ingest/DefaultImportQueueRepositoryTest.kt`:
  - la cola excluye sombras y documentos `cancelled`;
  - `remove` de un documento usa `DeleteDocumentUseCase`;
  - «Iniciar indexación» no toma los `cancelled`.
- [ ] T039 [P] [US4] Crear `…/androidTest/…/data/library/ReindexFlowTest.kt` con el runner real, `FakeTextEmbedder` y SQLite-Vector real (SC-006):
  - búsquedas en bucle durante todo el reindexado de un documento de 430 fragmentos: nunca vacías ni mezcladas;
  - al terminar, los mismos textos por ordinal;
  - una interrupción a mitad deja el original intacto y la reanudación termina bien;
  - quitar del índice y después «Reindexar» (desde `cancelled`) deja los mismos textos, páginas y encabezados por ordinal que antes (SC-005).

### Implementation for User Story 4

- [ ] T040 [US4] Implementar en `DefaultLibraryRepository` y en sus casos de uso `delete`, `removeFromIndex`, `reindex` y `discardReindex` (L4–L6, L8). Mover a `DeleteDocumentUseCase` el borrado de `DefaultImportQueueRepository.remove`. La cola excluye las sombras (L2). Comprobar T036 y T038.
- [ ] T041 [US4] En `…/microrag/data/ingest/IngestRunner.kt`, el cierre de las sombras con `publishReindex` (L7). Comprobar T037 y T039 en el emulador.
- [ ] T042 [US4] En `DocumentScreen` y `DocumentViewModel`:
  - acciones y diálogos D4–D6, D8 y D10 («Eliminando…» hasta terminar y vuelta a la Biblioteca);
  - en D5, la causa de la pausa de la tanda si la hay;
  - menú «⋯» → «Quitar del índice» solo en `done`;
  - «Reintentar» (D3, D6) con `IndexingRepository.retry`;
  - anuncios A7.

  Ampliar `DocumentViewModelTest` y `DocumentScreenTest` antes.
- [ ] T043 [US4] Crear `…/androidTest/…/data/library/DocumentDeleteBenchmarkTest.kt` (R11): 4.300 fragmentos de 256D; medir `deleteDocument`, `removeFromIndex` y `publishReindex`. Ejecutarla en el teléfono. **Si pasa de 5 s, anotarlo para el informe** (SC-009).
- [ ] T044 [US4] **Verificación en el teléfono** (quickstart §4.3):
  1. reindexar un documento de prueba de 430 fragmentos;
  2. durante el reindexado, buscar (prueba instrumentada o diagnóstico) y obtener siempre la versión anterior;
  3. matar el proceso a mitad, sin abrir la app;
  4. al terminar, mismos fragmentos.

  **Si la sustitución no es atómica, parar y consultar al usuario.**

**Checkpoint**: mantenimiento seguro.

---

## Phase 7: User Story 5 - Pantallas fieles, accesibles y coherentes con el resto de la app (Priority: P2)

**Goal**: estados restantes, accesibilidad completa y capturas frente a los PNG.

**Independent Test**:
- capturas del teléfono frente a los PNG 06 y 07;
- todos los estados pasan las comprobaciones automáticas en claro y en oscuro con la fuente al 200 %;
- TalkBack lee cada fila como una frase.

### Tests for User Story 5

- [ ] T045 [P] [US5] Crear `…/androidTest/…/feature/library/LibraryScreenAccessibilityTest.kt`, parametrizada como las de F06 y F07: todos los estados de `LibraryPreviewData`, en claro y en oscuro, con la fuente al 200 % (SC-008, A1, A4 y A6).
- [ ] T046 [P] [US5] Crear `…/androidTest/…/feature/document/DocumentScreenAccessibilityTest.kt`, igual, con todos los estados de `DocumentPreviewData` (A2, A3, A5 y A6).

### Implementation for User Story 5

- [ ] T047 [P] [US5] Crear `…/microrag/feature/library/LibraryPreviews.kt` y `…/microrag/feature/document/DocumentPreviews.kt`, con datos de ejemplo etiquetados (constitución V) para todos los estados, en claro y en oscuro. Comprobar T045 y T046 en el emulador.
- [ ] T048 [US5] B7 en la Biblioteca («Reindexando» y «Reindexado fallido» en la fila), con sus pruebas en `LibraryScreenTest`. B8 ya está en T035.
- [ ] T049 [US5] Capturar en el teléfono, en claro y en oscuro, sin la barra de estado:
  - la Biblioteca como el PNG 06, vacía y sin resultados;
  - el detalle como el PNG 07, desde una cita, pendiente, con error, fuera del índice y reindexando.

  Compararlas con los PNG 06 y 07 y listar las diferencias para el informe (constitución VII). Ningún nombre del documento del usuario en las capturas.

**Checkpoint**: UI cerrada y accesible.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T050 [P] Crear `…/androidTest/…/data/library/LibraryStrictModeTest.kt`: la observación de la página y del detalle, `setIncluded`, `delete`, `removeFromIndex` y `reindex` llamados desde el hilo principal, sin violaciones de disco. Con control negativo (L10).
- [ ] T051 [P] Actualizar `docs/roadmap/INVENTARIO.md`:
  - **G16:** resuelto, sin filtro «Errores»;
  - **G17:** resuelto con «Fuera del índice» (D-043);
  - **fichas P06 y P07:** estados B1–B8 y D1–D11.
- [ ] T052 Puertas de calidad:
  - pruebas Python;
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`;
  - suite instrumentada (sin `@RequiresModels` ni `@RequiresNetwork`) en el emulador API 37, en la AVD API 33 y en el teléfono;
  - `@RequiresModels` en el teléfono, porque el runner cambia;
  - `NetworkConfinementTest` en verde con los paquetes nuevos (SC-010).

  Todo en verde, salvo las 4 de navegación conocidas de la API 33 (F06 §7).
- [ ] T053 Recorrido manual en el teléfono (quickstart §5), incluido **eliminar desde la app los documentos de prueba de F07 (ids 2–9)** y medir el de 4.300 fragmentos. Nunca el id 1. Revisar que los logs de la app no contienen nombres, rutas ni texto (SC-010). TalkBack, a cargo del usuario.
- [ ] T054 Documentación de cierre:
  - `specs/008-library-detail/report.md`: resumen y puertas, validación por historia y criterio, cifras (R11 y borrados reales), diferencias frente a los PNG, desviaciones y pendientes;
  - estado de F08 en `docs/roadmap/FEATURES.md`;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias. **T006 puede parar el trabajo** (riesgo 1).
- **Foundational (fase 2):** depende de la fase 1. **Bloquea todas las historias.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2 (fase 4):** depende de US1 (las filas).
- **US3 (fase 5):** depende de la fase 2; puede ir en paralelo a US2. El interruptor del detalle reutiliza T026.
- **US4 (fase 6):** depende de US3 (las acciones viven en el detalle). **T044 puede parar el trabajo** (riesgo 2).
- **US5 (fase 7):** depende de US1–US4.
- **Polish (fase 8):** depende de todo.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Repositorio antes que ViewModel; ViewModel antes que pantalla.
- Las verificaciones en el dispositivo cierran su historia: T006, T022, T044 y T049.

### Parallel Opportunities

- **Fase 1:** T004 y T005.
- **Fase 2:** T007 y T008; después, T011, T012 y T013.
- **US1:** T015, T016 y T017.
- **US2:** T023, T024 y T025.
- **US3:** T027–T030.
- **US4:** T036–T039.
- **US5:** T045, T046 y T047.
- **Polish:** T050 y T051.

## Parallel Example: User Story 4 tests

```text
T036 DefaultLibraryRepositoryTest (acciones)   T037 IngestRunnerTest (sombras)
T038 DefaultImportQueueRepositoryTest          T039 ReindexFlowTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1, con la migración verificada en el teléfono (T006).
2. Fase 2.
3. US1: la Biblioteca real. **Parar y validar** con T022.

### Incremental Delivery

1. **US2:** inclusión.
2. **US3:** detalle y citas.
3. **US4:** acciones, con la verificación T044 en el teléfono.
4. **US5:** estados restantes, accesibilidad y capturas.
5. **Polish:** modo estricto, inventario, puertas, recorrido (con el borrado de los documentos de prueba) e informe.
