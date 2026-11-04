---

description: "Lista de tareas de F17 — Carpetas y ámbito del chat"
---

# Tasks: Carpetas y ámbito del chat (F17)

**Input**: Documentos de diseño en `/specs/017-folders-chat-scope/`: [plan.md](plan.md), [spec.md](spec.md), [wireframe.md](wireframe.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-011). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en orden de prioridad. Las pruebas en el teléfono van en la **última fase** (petición del usuario).

**Historias de la spec:**
- US1 — Ordenar los documentos en carpetas (P1);
- US2 — Mover documentos y elegir el destino al importar (P1);
- US3 — Preguntar solo a unas carpetas o documentos (P1);
- US4 — El ámbito se guarda con la conversación (P2);
- US5 — Ámbitos que ya no existen o que no tienen nada (P2);
- US6 — Pantallas aprobadas, accesibles y traducidas (P2);
- US7 — La búsqueda acotada sigue siendo rápida (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` y `Pixel_10`, sin ventana, que se cierran al terminar cada fase;
  - teléfono `adb -s P31281001066`, **solo en la última fase**.
- **Con el teléfono conectado:**
  - nunca `connectedDebugAndroidTest`. Siempre se instalan los APK con `-s <emulador>` y se lanza `am instrument -r`, con la salida a un fichero del dispositivo (`/data/local/tmp`);
  - antes de una batería, `wm dismiss-keyguard` y comprobar `isKeyguardShowing=false`.
- **En el teléfono** (memoria `f11-phone-phase` y `f16-phone-pending`):
  - la conversación y el documento del usuario no se abren, no se capturan y no se vuelcan;
  - se usan carpetas, documentos y conversaciones de prueba propios, que se borran al terminar;
  - al terminar se restauran los ajustes y el idioma por app.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US7).
- **Abreviaturas de rutas:**
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

- [ ] T001 [P] Textos de `folders-scope-ui.md` §4 y K14 en español y en inglés:
  - en `app/src/main/res/values{,-en}/strings_library.xml`: carpetas, diálogos y avisos;
  - en `…/strings_chat.xml`: ficha, selector, K9 y K10;
  - en `…/strings_ingest.xml`: destino.

  Incluye los plurales «%d documentos» y «+ %d documentos». `StringsParityTest` pasa.
- [ ] T002 [P] Reglas de capas en `…/test/…/architecture/LayeringTest.kt` (U6): `feature/folders` y `feature/chat/scope` no importan `data.`, `androidx.sqlite`, `androidx.work`, LiteRT, `java.io.File` ni `android.app.*Manager`. Fallan hasta que existen los paquetes, y pasan con T030 y T047.

---

## Phase 2: Foundational (esquemas, modelos y búsqueda acotada)

**Propósito:** las dos migraciones, los tipos del dominio y la búsqueda acotada, verificada. **Bloquea todas las historias.**

### Pruebas (antes, y deben fallar)

- [ ] T003 [P] Esquema de `rag.db` v4:
  - `…/test/…/data/rag/RagSchemaTest.kt`: el número de sentencias y su orden; `everyMigration_endsWhereSchemaSqlIs`, que ahora admite `CREATE TABLE IF NOT EXISTS folders`; versión 4;
  - `offline/tests/test_appconfig.py`: `schema_version == 4`.
- [ ] T004 [P] `…/androidTest/…/data/rag/RagIndexMigrationTest.kt`: un índice v3 con documentos, fragmentos e inclusiones variadas migra a v4 con todo intacto y `folder_id` a `NULL` (SC-001). Un fallo de migración deja el fichero v3 intacto.
- [ ] T005 [P] `chat.db` v2 en `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt` (o en la JVM si `SqliteChatRepository` corre con Robolectric):
  - un fichero v1 con conversaciones migra a v2 sin perder nada, y sin filas de ámbito;
  - el ámbito se escribe, se lee y se borra en cascada con su conversación;
  - «Borrar historial» vacía también `conversation_scope` y `last_scope`.
- [ ] T006 [P] **Puerta de R3:** `…/androidTest/…/data/rag/ScopedSearchTest.kt`, sobre `TempRagIndex` con unos 300 fragmentos sintéticos en 3 carpetas y documentos sueltos, algunos excluidos y uno pendiente.
  - `vector_full_scan('chunks', 'embedding', ?)` sin `k` funciona en 1.1.2.
  - Con `SearchScope.Only`, el top-k por significado y por palabras coincide exactamente con el calculado a mano: todas las distancias o `bm25`, filtrar y ordenar.
  - Con `SearchScope.All`, los resultados son idénticos byte a byte a los del SQL de F10 (SC-003).
  - Ningún resultado está fuera del ámbito, excluido o sin indexar (SC-002).
  - `loadChunks` y `neighbor` respetan el ámbito.

### Implementación

- [ ] T007 Esquema y migración de `rag.db`:
  - `app/src/main/assets/schema.sql` (v4: `folders`, `documents.folder_id` y el índice);
  - `…/microrag/data/rag/RagSchema.kt` (`RagMigration(from = 3, …)`);
  - `…/microrag/domain/rag/RagIndexConfig.kt` (`SCHEMA_VERSION = 4`).

  T003 y T004 pasan; el harness de Python sigue pasando.
- [ ] T008 Esquema y migración de `chat.db`: `app/src/main/assets/chat_schema.sql` (v2), `…/microrag/data/chat/ChatSchema.kt` (`VERSION = 2` y el primer paso) y `SqliteChatRepository`, con la lectura y escritura del ámbito, de `last_scope` y del borrado. T005 pasa.
- [ ] T009 Tipos del dominio (data-model §3):
  - `…/microrag/domain/library/FolderModels.kt`;
  - `…/microrag/domain/chat/ChatScope.kt`;
  - `SearchScope` y el `RetrievalRequest` ampliado en `…/microrag/domain/retrieval/RetrievalModels.kt`.
- [ ] T010 Búsqueda acotada en `…/microrag/data/rag/DefaultRagIndex.kt` y `…/microrag/domain/rag/RagIndex.kt` (F7): `vectorSearch`, `textSearch`, `loadChunks` y `neighbor` con `scope`. El SQL de `SearchScope.All` no cambia. `FakeRagIndex` acepta el parámetro.
- [ ] T011 **Puerta:** T006 en `Pixel_10` y en `MicroRAG_API33`. Si el modo en streaming no existe o el resultado no coincide, **se para y se consulta**. La alternativa a evaluar es top-(k + m) también con ámbito.

**Checkpoint:** esquemas v4 y v2 migrados y probados, y búsqueda acotada exacta verificada en los dos emuladores.

---

## Phase 3: User Story 1 - Ordenar los documentos en carpetas (Priority: P1) 🎯 MVP

**Goal:** K1–K4: la Biblioteca por carpetas, una carpeta por dentro, y crear, renombrar y eliminar.

**Independent Test:** quickstart §3, pasos 1, 2 y 4.

### Pruebas

- [ ] T012 [P] [US1] `…/test/…/domain/library/FolderNamesTest.kt`: plegado, recorte, vacío, 40 caracteres y los reservados «Sin carpeta» y «No folder» en cualquier forma.
- [ ] T013 [P] [US1] `…/androidTest/…/data/rag/RagIndexFoldersTest.kt` (F1–F3, F5):
  - crear, con un duplicado plegado que da `null`;
  - renombrar, también solo cambiando mayúsculas;
  - recuentos agregados, sin contar las copias temporales;
  - página por carpeta y por «Sin carpeta»;
  - `folderName` en los resultados;
  - eliminar moviendo los documentos (siguen todos, con `folder_id` a `NULL`);
  - el id de una carpeta borrada no se reutiliza.
- [ ] T014 [P] [US1] `…/test/…/domain/library/FolderUseCasesTest.kt`, con fakes:
  - `CreateFolderUseCase` y `RenameFolderUseCase` con resultados `Done`, `Duplicate`, `Invalid` y `Failed`;
  - `DeleteFolderUseCase` con `MOVE_TO_UNFILED` y con `DELETE_DOCUMENTS`: cada documento por `LibraryRepository.delete`, y la carpeta se borra solo si queda vacía; con un fallo a medias, devuelve los que fallaron (FR-005, SC-006);
  - `RecordingLogger` sin nombres (FR-025).
- [ ] T015 [P] [US1] `…/test/…/feature/library/FoldersViewModelTest.kt` y `…/test/…/feature/library/LibraryViewModelTest.kt` (ampliado):
  - las carpetas;
  - los diálogos en el `SavedStateHandle`;
  - un doble toque que no repite nada;
  - los avisos;
  - el filtro de carpeta por la ruta;
  - la búsqueda de la raíz en todas las carpetas.
- [ ] T016 [P] [US1] `…/test/…/feature/library/LibraryScreenTest.kt` (ampliado) y `…/test/…/feature/library/FolderDialogsTest.kt` (Robolectric, wireframe §1–§3):
  - la raíz con las carpetas, «Sin carpeta» al final y «Nueva carpeta»;
  - con búsqueda, los documentos con su carpeta;
  - la carpeta por dentro, con «⋯»;
  - crear y renombrar, con sus avisos;
  - eliminar con las dos opciones y el segundo aviso.
- [ ] T017 [P] [US1] `…/test/…/core/navigation/NavigationTest.kt`: Biblioteca → carpeta → detalle → Atrás → carpeta → Atrás → raíz; sin barra inferior en `LibraryFolder`; `settingsViewModels()` y los demás constructores con fakes.

### Implementación

- [ ] T018 [US1] `LisdataTextField` en `…/microrag/core/designsystem/component/Inputs.kt` (R9), con vistas previas y entrada en el catálogo de depuración.
- [ ] T019 [US1] Índice F1–F3 y F5 en `DefaultRagIndex.kt`/`RagIndex.kt`, y `DefaultLibraryRepository`/`LibraryRepository` (página por carpeta y operaciones de carpeta). T013 pasa.
- [ ] T020 [US1] `FolderNames.kt` y `FolderUseCases.kt` en `…/microrag/domain/library/`, con su inyección en `DomainModule`. T012 y T014 pasan.
- [ ] T021 [US1] Pantallas (contrato UI §1):
  - `FoldersViewModel`, `FoldersSection` y `FolderDialogs` en `…/microrag/feature/library/`;
  - el filtro de carpeta en `LibraryViewModel` y `LibraryScreen`;
  - vistas previas;
  - inyección en `FeatureModule`.

  T015 y T016 pasan.
- [ ] T022 [US1] Navegación: `LibraryFolder(folderId: Long?)` en `Destinations.kt`, su `composable` en `MicroRagNavHost.kt` y la ruta de depuración. T017 pasa.

**Checkpoint:** carpetas completas en la Biblioteca.

---

## Phase 4: User Story 2 - Mover documentos y elegir el destino al importar (Priority: P1)

**Goal:** K5 y K6.

**Independent Test:** quickstart §3, paso 3.

### Pruebas

- [ ] T023 [P] [US2] `RagIndexFoldersTest`, ampliado con F4 y F6: mover no cambia el estado, la inclusión ni los fragmentos; un documento nuevo se registra en su carpeta; un duplicado no cambia de carpeta.
- [ ] T024 [P] [US2] `…/test/…/feature/document/DocumentViewModelTest.kt` y `DocumentScreenTest.kt`: el dato «Carpeta», «Mover a carpeta» en el «⋯», la hoja, «Nueva carpeta…» y el aviso «Movido a …».
- [ ] T025 [P] [US2] `…/test/…/feature/ingest/IngestViewModelTest.kt` y `IngestScreenTest.kt`:
  - la fila de destino, con el valor por defecto según la ruta;
  - la hoja;
  - los documentos elegidos con el destino vigente;
  - la carpeta en la línea de datos de la cola.
- [ ] T026 [P] [US2] `…/test/…/data/ingest/DocumentImporterTest.kt` y `DefaultImportQueueRepositoryTest.kt`: el destino llega a `NewDocument.folderId`.

### Implementación

- [ ] T027 [US2] F4 y F6 en el índice; `MoveDocumentUseCase`; el destino en `ImportDocumentsUseCase`, `DefaultImportQueueRepository` y `DocumentImporter`. T023 y T026 pasan.
- [ ] T028 [US2] `…/microrag/feature/folders/FolderChoiceSheet.kt`, con vistas previas.
- [ ] T029 [US2] Detalle (`DocumentScreen`, `DocumentViewModel`) e Ingesta (`IngestScreen`, `IngestViewModel`, `Ingest(folderId)` y el «＋» de la carpeta). T024 y T025 pasan.
- [ ] T030 [US2] Inyección y `LayeringTest`. T002 pasa en su parte de `feature/folders`.

---

## Phase 5: User Story 3 - Preguntar solo a unas carpetas o documentos (Priority: P1)

**Goal:** K7 y K8, y la recuperación acotada en el chat.

**Independent Test:** quickstart §3, pasos 5 y 6.

### Pruebas

- [ ] T031 [P] [US3] `…/test/…/domain/chat/ScopeUseCasesTest.kt`:
  - `ResolveScopeUseCase`: lo que existe, la guarda de D-065 para documentos y las carpetas por id;
  - la conversión a `SearchScope`;
  - FR-016: una carpeta incluye lo que contiene en cada pregunta.
- [ ] T032 [P] [US3] `…/test/…/data/retrieval/HybridRetrieverTest.kt` y `…/androidTest/…/data/retrieval/HybridRetrieverVisibilityTest.kt`, ampliadas: el ámbito llega a las dos búsquedas y a la selección, y «Toda la biblioteca» da lo mismo que antes.
- [ ] T033 [P] [US3] `…/test/…/data/chat/DefaultChatSessionTest.kt`:
  - `ask(…, scope)` recupera con el ámbito de ese momento;
  - una respuesta en curso no cambia de ámbito;
  - `setScope` se aplica a la pregunta siguiente (FR-019).
- [ ] T034 [P] [US3] `…/test/…/feature/chat/scope/ScopeSelectorViewModelTest.kt`:
  - carpetas no vacías con sus recuentos;
  - desplegar por tandas;
  - marcar una carpeta, documentos sueltos, y el estado intermedio;
  - «Toda la biblioteca» excluyente;
  - los excluidos no se pueden marcar;
  - «Aplicar» sin nada marcado no se puede;
  - la selección sobrevive en el `SavedStateHandle`.
- [ ] T035 [P] [US3] `…/test/…/feature/chat/scope/ScopeSheetTest.kt` y `…/test/…/feature/chat/ChatScreenTest.kt` (ampliado), con Robolectric (wireframe §6):
  - la ficha con cada resumen de FR-014 y su semántica;
  - la hoja con radio, casillas de tres estados, desplegables, notas y botones.
- [ ] T036 [P] [US3] `…/test/…/feature/chat/ChatViewModelTest.kt`: la ficha refleja el ámbito; aplicarlo desde el selector llega a la sesión; un chat sin preguntas lo guarda en el `SavedStateHandle`.

### Implementación

- [ ] T037 [US3] `LisdataCheckRow`, `LisdataExpandableRow` y `LisdataScopeChip` en `…/microrag/core/designsystem/component/` (R9), con vistas previas y entradas en el catálogo.
- [ ] T038 [US3] `ScopeUseCases.kt` en `…/microrag/domain/chat/`; `RetrieveContextUseCase` con el ámbito; `HybridRetriever` pasa el ámbito. T031 y T032 pasan.
- [ ] T039 [US3] `ChatSession`/`DefaultChatSession`: `ask(…, scope)`, `setScope` y `scopeOf`. T033 pasa.
- [ ] T040 [US3] `…/microrag/feature/chat/scope/ScopeSelectorViewModel.kt` y `ScopeSheet.kt`; la ficha y la hoja en `ChatScreen`; el ámbito en `ChatViewModel` y `ChatUiState`; inyección. T034–T036 pasan.

---

## Phase 6: User Story 4 - El ámbito se guarda con la conversación (Priority: P2)

**Goal:** FR-020 y FR-020b.

**Independent Test:** quickstart §3, paso 7.

- [ ] T041 [P] [US4] Pruebas:
  - `ChatViewModelTest`: abrir desde Historial restaura el ámbito; girar y restaurar el proceso lo conservan; «Nuevo chat» (desde el chat, desde Historial y desde Inicio) usa el último ámbito, y la primera vez «Toda la biblioteca»; las conversaciones antiguas tienen «Toda la biblioteca»;
  - `DefaultChatSessionTest`: `last_scope` se actualiza al aplicar y al preguntar.
- [ ] T042 [US4] Implementación en `DefaultChatSession`, `ChatViewModel` y los casos de uso del último ámbito. T041 pasa.

---

## Phase 7: User Story 5 - Ámbitos que ya no existen o que no tienen nada (Priority: P2)

**Goal:** FR-021 y FR-022; K9 y K10.

**Independent Test:** quickstart §3, paso 8.

### Pruebas

- [ ] T043 [P] [US5] `ScopeUseCasesTest`: `ResolvedScope` con lo que cae y la vuelta a «Toda la biblioteca». `…/androidTest/…/data/rag/RagIndexChatStateTest.kt`: `chatLibraryState(scope)` y la cobertura parcial dentro del ámbito.
- [ ] T044 [P] [US5] `ChatViewModelTest` y `ChatScreenTest`:
  - `ChatAvailability.NothingInScope`, con «Cambiar ámbito» y el compositor desactivado;
  - los avisos de K10 una vez;
  - la cobertura parcial del ámbito.

### Implementación

- [ ] T045 [US5] `chatLibraryState(scope)` en el índice; `ObserveChatAvailabilityUseCase` con el ámbito; el estado K9 y los avisos K10 en el chat. T043 y T044 pasan.

---

## Phase 8: User Story 6 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

- [ ] T046 [P] [US6] Pruebas ATF en `…/androidTest/…/feature/`, parametrizadas como las de Tema: la raíz de la Biblioteca, la carpeta, los diálogos K3 y K4, la hoja de carpetas, la Ingesta con destino, el detalle, la ficha y el selector del ámbito, y el estado K9. En claro y en oscuro, al 200 % y en horizontal (SC-008). Incluye las piezas nuevas en `CatalogAccessibilityTest`.
- [ ] T047 [US6] Ajustar hasta que T046 pase. Si ATF contradice el wireframe o una propuesta aprobada, **se para y se consulta**. `LayeringTest` completo (T002).
- [ ] T048 [P] [US6] `StringsParityTest` y la revisión del inglés de los textos nuevos. Las correcciones van al informe.

---

## Phase 9: User Story 7 - La búsqueda acotada sigue siendo rápida (Priority: P3)

- [ ] T049 [US7] `…/androidTest/…/data/retrieval/RetrievalBenchmarkTest.kt`, ampliada (R11): «Toda la biblioteca», una carpeta de unos 1.000 fragmentos y 5 documentos sueltos, sobre 10.000 fragmentos sintéticos, con el p50 y el p95 de la búsqueda completa. Se ejecuta en `Pixel_10`, y el teléfono queda para la última fase.
- [ ] T050 [P] [US7] `…/androidTest/…/data/rag/RagIndexLibraryTest.kt`, ampliada: 1.000 documentos en 20 carpetas; el tiempo hasta la lista de carpetas (SC-007) y las tandas por carpeta.

---

## Phase 10: Polish sin teléfono

- [ ] T051 Toda la batería JVM, lint y Python:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`, solo con los avisos previos;
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests`.
- [ ] T052 Batería instrumentada completa en `Pixel_10` y en `MicroRAG_API33`, siempre con `-s`. En API 33 siguen los 4 fallos previos de Atrás desde la raíz.
- [ ] T053 Recorrido en el emulador (quickstart §3, pasos 1–9), con documentos de prueba y capturas frente al wireframe en español e inglés, en claro y en oscuro, al 200 % y en horizontal.
- [ ] T054 Borrador de `specs/017-folders-chat-scope/report.md`:
  - resumen;
  - build, lint y pruebas;
  - las cifras de T049 y T050;
  - las diferencias con los PNG 04–07 y con el wireframe;
  - las correcciones del inglés.

  Avance de tareas en este fichero.

---

## Phase 11: Última fase — teléfono de referencia y cierre

- [ ] T055 Batería instrumentada completa en el A063, con la salida en el teléfono y sin pantalla de bloqueo. Incluye `RetrievalBenchmarkTest` con ámbitos (SC-004).
- [ ] T056 Recorrido **acordado con el usuario antes de empezar** (quickstart §5):
  - su documento queda en «Sin carpeta» y su conversación con «Toda la biblioteca», sin abrirlos;
  - carpetas, documentos y conversaciones de prueba;
  - mover, importar a una carpeta y eliminar con las dos opciones;
  - el ámbito en el chat y en Historial;
  - la red y la limpieza;
  - ajustes e idioma por app restaurados.
- [ ] T057 Cerrar `report.md` y poner F17 en `docs/roadmap/FEATURES.md` como «implementada; pendiente de la revisión de cierre». **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **Foundational (fase 2):** T003–T006 antes que T007–T010; T011 después de T010. **Bloquea todo lo demás.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2 (fase 4):** depende de US1, por el índice de carpetas y los diálogos.
- **US3 (fase 5):** depende de la fase 2 y de T019 (las carpetas). Puede ir en paralelo con US2, salvo en `DefaultRagIndex.kt`.
- **US4 (fase 6):** depende de US3.
- **US5 (fase 7):** depende de US3 y US4.
- **US6 (fase 8):** depende de US1–US5.
- **US7 (fase 9):** depende de la fase 2 y de US3. Puede ir en paralelo con US4–US6.
- **Polish (fase 10):** depende de todo lo anterior.
- **Última fase (11):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Datos antes que los casos de uso; los casos de uso antes que los ViewModels; los ViewModels antes que las pantallas.
- **Paradas de consulta:**
  - la del analyze, si deja decisiones (D-070);
  - T011, si el modo en streaming no se comporta como en R3;
  - T047, si ATF contradice el wireframe;
  - T056: el recorrido del teléfono se acuerda antes con el usuario;
  - T057: la revisión de cierre.

### Parallel Opportunities

- **Fase 1:** T001 y T002.
- **Fase 2:** T003–T006.
- **US1:** T012–T017.
- **US2:** T023–T026.
- **US3:** T031–T036.
- **US5:** T043 y T044.
- **US6:** T046 y T048.
- **US7:** T049 y T050.

---

## Implementation Strategy

### MVP (US1)

Fases 1 y 2, la puerta T011 y US1: carpetas funcionando en la Biblioteca, sin cambiar todavía el chat.

### Incremental

1. **US2:** mover e importar.
2. **US3:** el ámbito en el chat.
3. **US4:** el ámbito guardado y el último usado.
4. **US5:** los ámbitos rotos o vacíos.
5. **US6:** accesibilidad y textos.
6. **US7:** las medidas.
7. **Polish y teléfono.**

### Notes

- El SQL de «Toda la biblioteca» no se toca. Cualquier cambio en él rompe SC-003 y T006.
- Los nombres de carpeta y de documento nunca van a los registros (FR-025).
- Las tareas marcadas «se para y se consulta» detienen el ciclo automático.
