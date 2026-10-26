---

description: "Lista de tareas de F07 — Indexación"
---

# Tasks: Indexación (F07)

**Input**: Documentos de diseño en `/specs/007-indexing/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios**. Los exigen la constitución (VI) y los criterios SC-001 a SC-010. En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec:
- US1 — Indexar los documentos importados de principio a fin (P1);
- US2 — La indexación sobrevive a todo (P1);
- US3 — Ver el progreso real (P1);
- US4 — Pausar, reanudar, reintentar y pausas automáticas (P2);
- US5 — Pantalla fiel al mockup, accesible y coherente con la Ingesta (P2).

**Entorno** (quickstart):
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Dispositivos:
  - teléfono de referencia: `adb -s P31281001066`, con los modelos instalados;
  - emulador `Pixel_10`: `emulator-5554`;
  - AVD `MicroRAG_API33`: `emulator-5556`.

  **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument` (quickstart §3).
- Python: siempre `offline/.venv/bin/python -I`.
- Antes de cada commit, las pruebas se ejecutan y se comprueba su código de salida por separado (lección de F06).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**UI pendiente de aprobación**: las propuestas P1–P11 de contracts/index-ui.md se aprueban en la parada tras analyze. Las tareas que las implementan dan por hecha esa aprobación: T030 (P11), T039 (hero sin título), T047 (P10), T048 (P3–P5 y P7), T053 (P1, P2, P6, P8 y P9) y T054 (cambios en la Ingesta).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Las rutas son relativas a la raíz del repositorio. Abreviaturas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: fijar la regla de D-036 desde Python y medir en el teléfono lo que puede cambiar el diseño (research R5 y R6).

- [ ] T001 [P] Crear `offline/gen_overflow_cases.py`, que escribe `offline/golden/embedding_overflow.jsonl` (research R6). Para cada texto, guarda la secuencia de recortes de la regla de `offline/eval/builds.py` (`int(len(text) × 0,9)` y después hasta el último espacio, con longitudes en puntos de código) hasta 12 pasos. Casos:
  - un texto español largo;
  - acentos y `ñ`;
  - emoji fuera del BMP;
  - un texto sin espacios;
  - un espacio justo en el corte;
  - un texto vacío.

  Extraer la regla a una función (`overflow_cut(text)`) que use también `builds.py`, para que haya una sola fuente. Opción `--check`. Prueba en `offline/tests/test_overflow_cases.py`: regenerar y comparar.
- [ ] T002 [P] Crear `…/androidTest/data/indexing/EmbeddingOverflowDeviceTest.kt` (`@RequiresModels`, research R6). Con `LiteRtTextEmbedder` real en CPU:
  - embeber un texto de unos 700 tokens;
  - registrar con la etiqueta `overflow-probe` la clase y el mensaje de la excepción;
  - comprobar que tras los recortes de D-036 el modelo lo acepta.

  Ejecutarla en el teléfono y anotar en research R6 la excepción real y si se distingue de otros fallos.
- [ ] T003 [P] Crear `…/androidTest/data/indexing/EmbeddingBatchProbeTest.kt` (`@RequiresModels`, research R5). Con 64 fragmentos reales de `text_200_pages.pdf`, medir en GPU y en CPU:
  - `computeEmbedding` en serie;
  - `computeEmbeddingBatch` en lotes de 8 y de 16.

  Registrar fragmentos por segundo y el coseno mínimo entre los dos modos (`batch-probe`). Ejecutarla en el teléfono y anotar las cifras en research R5. **Si el lote acelera al menos un 30 % con coseno ≥ 0,9999, parar y consultar al usuario antes de la fase 3** (cambia FR-014).

**Checkpoint**: regla de recorte fijada y decisión de lotes tomada con cifras.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos, funciones puras, índice, engines y tanda que necesitan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase.

### Tests (primero, deben fallar)

- [ ] T004 [P] `…/test/domain/rag/DocumentTitleTest.kt` (research R11):
  - «Manual técnico.pdf» → «Manual técnico»;
  - «notas» queda igual;
  - «.bashrc» queda igual;
  - «a.b.pdf» → «a.b»;
  - una extensión de 11 caracteres o no alfanumérica no se quita.
- [ ] T005 [P] `…/test/rag/runtime/EmbeddingOverflowTest.kt`: lee `offline/golden/embedding_overflow.jsonl` desde `sharedTest` y exige la misma secuencia de recortes (R6).
- [ ] T006 [P] Ampliar `…/test/rag/runtime/EngineCoordinatorTest.kt` (research R4):
  - `withHeldEngine` mantiene el engine durante el bloque;
  - `releaseUnheld` cierra los no retenidos y respeta el retenido;
  - `releaseGenerators` cierra solo los de generación;
  - la regla de una sola GPU sigue vigente;
  - `EngineMemoryCallbacks` con `TRIM_MEMORY_UI_HIDDEN` ya no cierra un engine retenido.
- [ ] T007 [P] `…/test/data/indexing/DataStoreIngestRunStoreTest.kt` (data-model §2):
  - altas sin duplicados y en orden;
  - bajas;
  - pausa con causa;
  - reanudar;
  - persistencia entre instancias;
  - vaciado.
- [ ] T008 [P] Ampliar `…/androidTest/data/rag/RagIndexWriteTest.kt`:
  - `storedChunkCounts` (ids sin fragmentos dan 0);
  - `storedChunkContents` en orden;
  - `deleteChunks` borra los fragmentos y sus filas FTS y conserva el documento;
  - `insertChunks` calcula `title_norm` con `DocumentTitle` (R11).
- [ ] T009 [P] Crear `…/androidTest/data/rag/RagIndexVisibilityTest.kt` (research R7, N7, SC-003). Sobre SQLite-Vector real, con documentos `done`, `embedding`, `parsed`, `error` y `done` excluido (`included = 0`):
  - `vectorSearch` y `textSearch` devuelven solo fragmentos de los `done` incluidos;
  - con *m* > 0 se devuelven exactamente los *k* mejores buscables (comparado con fuerza bruta en Kotlin);
  - con *m* = 0, el mismo resultado que la consulta de F04.

### Implementation

- [ ] T010 Ampliar `…/microrag/domain/ingest/IngestModels.kt` (data-model §3):
  - `IngestError.EMBEDDING_FAILED` e `INCOMPLETE`, reintentables;
  - `QueueItemState.Indexing` y `Paused`;
  - `IngestProgress.Stage.EMBEDDING`, `CLOSING` y `THERMAL_WAIT`, con `chunksDone`, `chunksTotal`, `chunksPerSecond` y `backend`.

  Ajustar los `when` exhaustivos de `feature/ingest` con textos provisionales; los definitivos van en US5.
- [ ] T011 [P] Crear `…/microrag/domain/indexing/IndexingModels.kt` (`IndexingState`, `RunProgress`, `CurrentDocument`, `PauseReason`, `FailedDocument`) y `IndexingRepository.kt` (contracts/indexing-api.md).
- [ ] T012 [P] Crear `…/microrag/domain/rag/DocumentTitle.kt` (R11). Comprobar T004.
- [ ] T013 [P] Crear `…/microrag/rag/runtime/EmbeddingOverflow.kt` (R6) y añadir `DocumentEmbedding`. Añadir `embedDocumentFitting(content, header)` a `TextEmbedder` y a `LiteRtTextEmbedder`: bucle de hasta 12 recortes con la excepción que fije T002. Ajustar los fakes de prueba existentes. Comprobar T005.
- [ ] T014 Ampliar `…/microrag/rag/runtime/EngineCoordinator.kt` con `withHeldEngine`, `releaseUnheld` y `releaseGenerators`, y `EngineMemoryCallbacks.kt` para que use `releaseUnheld` (R4). Comprobar T006.
- [ ] T015 Ampliar `RagIndex` (`…/microrag/domain/rag/RagIndex.kt`) y `DefaultRagIndex` (`…/microrag/data/rag/DefaultRagIndex.kt`):
  - `storedChunkCounts`, `storedChunkContents` y `deleteChunks` (con `notifyChanged`);
  - búsquedas con *k* + *m* y filtro `status = 'done' AND included = 1` (R7);
  - `title_norm` con `DocumentTitle`.

  Actualizar `FakeRagIndex` y la lista de operaciones rechazadas de `RagIndexCompatibilityTest`. Comprobar T008 y T009 en el emulador.
- [ ] T016 [P] Crear `…/microrag/data/indexing/DataStoreIngestRunStore.kt` con su interfaz `IngestRunStore` (data-model §2). Comprobar T007.
- [ ] T017 [P] Crear `…/microrag/data/device/ThermalStatusSource.kt`: interfaz y `PowerManagerThermalStatusSource`, con `PowerManager.currentThermalStatus` y `addThermalStatusListener` como `Flow<ThermalLevel>` (`NORMAL`, `MODERATE`, `SEVERE_OR_WORSE`). Añadir a `LayeringTest` la regla «`android.os.PowerManager` solo en `data/device`».
- [ ] T018 En `…/microrag/MicroRagApplication.kt`, añadir `setGreedySchedulerEnabled(false)` con `@OptIn(ExperimentalConfigurationApi::class)` y un comentario que remita a research R2. Comprobar que las pruebas de F03 (descarga en API 31–33) y de F06 (`WorkManagerIngestSchedulerTest`, `IngestFlowTest`) siguen en verde.
- [ ] T019 Registrar en Koin (`DataModule`, `DomainModule`): `IngestRunStore`, `ThermalStatusSource` y el resto de definiciones nuevas, y extender `AppModulesTest` (`verify()`).

**Checkpoint**: tipos, índice filtrado, engines retenidos, tanda y configuración listos. JVM, lint y las instrumentadas de F04 y F06 en verde.

---

## Phase 3: User Story 1 - Indexar los documentos importados de principio a fin (Priority: P1) 🎯 MVP

**Goal**: cada documento de la tanda acaba `done` con todos sus fragmentos y vectores, o con un error concreto, y no se puede buscar hasta entonces.

**Independent Test**: PDF, TXT y MD indexados. Fragmentos iguales al troceado, con ordinales 0..n-1 y vectores unitarios de 256. Ninguna búsqueda devuelve un documento a medias.

### Tests for User Story 1

- [ ] T020 [P] [US1] Ampliar `…/test/data/ingest/IngestRunnerTest.kt` con un `TextEmbedder` determinista (`…/test/data/indexing/FakeTextEmbedder.kt`: vector derivado del hash del texto embebido, normalizado, con fallo programable). Casos:
  - **fase 1:** los `pending` pasan a `parsed` antes de embeber nada;
  - **fase 2:** `embedding`, lotes de 64 y `done`;
  - **texto embebido:** el de N5 (prefijo y «título › ruta»); `content` sin cabecera;
  - **`token_estimate`:** con `CHARS_PER_TOKEN`;
  - **reanudación (N2):** con 130 guardados, solo se embeben los siguientes;
  - **desajuste:** se borran y se rehace desde 0;
  - **cierre:** con un recuento que no cuadra, `error incomplete`;
  - **fallo del engine dos veces:** `embedding_failed`, y el siguiente documento sigue (N11);
  - **documento quitado a mitad:** se salta sin escribir;
  - **cancelación:** el lote parcial se guarda (N4).
- [ ] T021 [P] [US1] `…/test/data/indexing/ChunkEmbeddingSessionTest.kt` (R4):
  - libera los generadores antes de cargar;
  - pide la GPU y, si falla al cargar, usa la CPU y registra el motivo;
  - retiene el engine durante la sesión y lo libera al cerrarla;
  - un fallo a mitad recarga una vez.
- [ ] T022 [P] [US1] Crear `…/androidTest/data/indexing/IndexingFlowTest.kt` con SQLite-Vector real, el importador y el runner de F06, y el embedder determinista:
  - TXT, MD y `text_3_pages.pdf` acaban `done`, con los fragmentos del troceado;
  - durante la fase 2, una búsqueda no devuelve fragmentos del documento en curso (SC-003);
  - cancelación determinista a mitad de `text_200_pages` y reanudación con los mismos 430 fragmentos, sin duplicados (SC-002, parte automática).

### Implementation for User Story 1

- [ ] T023 [US1] Crear `…/microrag/data/indexing/ChunkEmbeddingSession.kt` (R4): sesión que libera los generadores, abre el engine retenido de `EmbeddingGemma` en GPU (CPU si falla), expone `embed(content, header)` y recarga una vez si el engine falla a mitad. Usa `EngineFactory` y `ModelFileRepository.pathOf`. Comprobar T021.
- [ ] T024 [US1] Ampliar `…/microrag/data/ingest/IngestRunner.kt` con las fases 1 y 2 (research R8–R9, reglas N1–N5, N11 y N13):
  - comparación con lo guardado;
  - lotes de 64;
  - lote parcial en `NonCancellable` (tope 5 s);
  - comprobación de estado antes de cada lote;
  - cierre con recuento;
  - publicación del progreso `EMBEDDING`/`CLOSING`, con el ritmo de los dos últimos lotes.

  Mantener intacta la lógica de F06 para la fase 1. Comprobar T020.
- [ ] T025 [US1] Ampliar `…/microrag/data/ingest/IngestWorker.kt` e `IngestScheduler.kt` (claves de progreso de data-model §5, `cancel()` y `stopReason`) sin el primer plano (US2). Actualizar `WorkManagerIngestSchedulerTest`. Comprobar T022 en el emulador.
- [ ] T026 [US1] En el teléfono, indexar con la app real un PDF, un TXT y un MD desde la Ingesta. Comprobar en el log `ingest done` y en la base que los fragmentos tienen vector y que los documentos están `done`.

**Checkpoint**: MVP funcional: la app indexa y publica documentos completos.

---

## Phase 4: User Story 2 - La indexación sobrevive a todo (Priority: P1)

**Goal**: el trabajo sigue con la app cerrada, con notificación, y se reanuda solo tras una muerte del proceso, un reinicio o una parada del sistema.

**Independent Test**: matar el proceso en tres momentos sin abrir la app. El documento acaba `done` con los 430 fragmentos, sin duplicados ni huecos.

### Tests for User Story 2

- [ ] T027 [P] [US2] `…/test/data/indexing/IndexingNotificationsTest.kt` (Robolectric, R3):
  - canal `indexing` de importancia baja;
  - textos sin nombres de archivo;
  - progreso determinado e indeterminado;
  - acción «Pausar» con `PendingIntent` explícito e inmutable;
  - toque → `MainActivity` con `ACTION_OPEN_INDEXING`.
- [ ] T028 [P] [US2] Crear `…/test/data/ingest/IngestWorkerTest.kt` (`work-testing`, R1): si `setForeground` lanza `ForegroundServiceStartNotAllowedException`, el worker sigue, publica la notificación normal y lo reintenta en el siguiente lote.
- [ ] T029 [P] [US2] Tests de `reconcile` en `…/test/data/indexing/DefaultIndexingRepositoryTest.kt` (R12, N14):
  - tanda sin trabajo → encola;
  - trabajo `ENQUEUED` con causa del sistema → `REPLACE`;
  - `RUNNING` → no toca;
  - tanda pausada → nada.

### Implementation for User Story 2

- [ ] T030 [US2] Crear `…/microrag/data/indexing/IndexingNotifications.kt` e `IndexingActionReceiver.kt` (no exportado, en el manifiesto), con los textos de P11 en `strings_indexing.xml` (es y en). Comprobar T027.
- [ ] T031 [US2] Añadir a `IngestWorker` el primer plano `dataSync` (`setForeground` al empezar y en cada lote) y la notificación normal si falla (R1). Comprobar T028.
- [ ] T032 [US2] Implementar `reconcile` en `…/microrag/data/indexing/DefaultIndexingRepository.kt` (puede empezar como esqueleto de US3) y `ReconcileIndexingUseCase`. Llamarlo desde `MainActivity.onStart` en el *scope* de la aplicación. Comprobar T029.
- [ ] T033 [US2] **Verificación temprana en los dispositivos** (quickstart §4, riesgo 1). Con la app real en el teléfono y en el emulador:
  - anotar la latencia de arranque sin planificador en proceso;
  - comprobar que el proceso es servicio en primer plano y que la notificación avanza;
  - matar con `kill -9` durante la fase 1, a mitad de los embeddings y en el cierre, **sin abrir la app**: el trabajo termina solo, el proceso no queda congelado y el documento tiene 430 fragmentos sin duplicados ni huecos (SC-002);
  - en el emulador API 37, `OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS` y `data_sync_fgs_timeout_duration` de 60 s: el trabajo continúa en tramos;
  - reiniciar el teléfono a mitad: continúa tras desbloquear.

  Anotar los resultados en research R1–R2. **Si sigue congelándose o la latencia pasa de unos 10 s, parar y consultar al usuario.**

**Checkpoint**: la indexación sobrevive a muertes, reinicios y límites del sistema.

---

## Phase 5: User Story 3 - Ver el progreso real (Priority: P1)

**Goal**: la pantalla Estado de indexación muestra el estado en curso del PNG 08 con cifras reales que sobreviven al cierre de la app.

**Independent Test**: con 3 documentos en marcha, N y M cuadran con lo guardado y con los troceados. Al reabrir, el mismo progreso.

### Tests for User Story 3

- [ ] T034 [P] [US3] Ampliar `…/test/data/indexing/DefaultIndexingRepositoryTest.kt` con `FakeRagIndex`, `work-testing`, un `IngestRunStore` en memoria y temperatura fija:
  - `Idle` sin tanda;
  - `Active` con M desconocido mientras queda un `pending` (FR-021/022);
  - N/M al guardar lotes y al cerrar documentos;
  - documentos con error fuera de N y M;
  - `CurrentDocument` y su etapa;
  - documentos en cola;
  - tiempo restante solo con ritmo estable (R8, FR-023);
  - la tanda se vacía al terminar;
  - `IndexUnavailable`.
- [ ] T035 [P] [US3] Tests de `start` en el mismo fichero:
  - añade `pending`, `parsed` y `embedding` sin duplicar;
  - quita la pausa;
  - encola;
  - devuelve el número añadido.
- [ ] T036 [P] [US3] `…/test/feature/indexing/IndexViewModelTest.kt`: mapea `IndexingState` a `IndexUiState` (porcentaje, «N / M», etapas, documento actual, acción principal) y expone `onPause`, `onResume` y `onRetry`.
- [ ] T037 [P] [US3] `…/test/feature/indexing/IndexScreenTest.kt` (Robolectric, qualifiers `es` y `en`), con el estado en curso del PNG 08:
  - hero con eyebrow;
  - anillo con porcentaje;
  - «N / M fragmentos» en formato local;
  - 4 etapas con sus estados;
  - «Procesando ahora»;
  - «Pausar indexación».

### Implementation for User Story 3

- [ ] T038 [US3] Completar `DefaultIndexingRepository.state` y `start` (R8, data-model §3), con los casos de uso `Observe` y `Start`. El `StartIndexingUseCase` de F06 delega en `start()`. Comprobar T034 y T035.
- [ ] T039 [US3] Ampliar `LisdataHeroCard` (`…/microrag/core/designsystem/component/Cards.kt`) con el título opcional. Actualizar su preview, el catálogo y `CatalogAccessibilityTest` si cambia.
- [ ] T040 [US3] Crear `…/microrag/feature/indexing/IndexUiState.kt`, `IndexTexts.kt` e `IndexViewModel.kt`. Comprobar T036.
- [ ] T041 [US3] Crear `…/microrag/feature/indexing/IndexScreen.kt` (`IndexRoute` + `IndexScreen` sin estado) con la estructura de contracts/index-ui.md para el estado en curso, y `strings_indexing.xml` (es y en) con los textos del PNG. Comprobar T037.
- [ ] T042 [US3] En `…/microrag/core/navigation/MicroRagNavHost.kt`, sustituir el placeholder de `Index` por `IndexRoute`. Abrir `Index` desde `ACTION_OPEN_INDEXING` en `MainActivity` (Atrás → Inicio si es la raíz). Actualizar `NavigationTest` y `AppModulesTest`.

**Checkpoint**: la pantalla muestra el progreso real de la indexación.

---

## Phase 6: User Story 4 - Pausar, reanudar, reintentar y pausas automáticas (Priority: P2)

**Goal**: control del usuario y pausas automáticas coherentes con el trabajo.

**Independent Test**: pausar y reanudar sin repetir. Temperatura simulada: pausa y sigue sola. Sin espacio: para sin publicar y avisa. Modelo ausente: espera y lo explica.

### Tests for User Story 4

- [ ] T043 [P] [US4] Ampliar `IngestRunnerTest` (R10):
  - **temperatura `SEVERE`:** guarda el lote, libera el engine, publica `THERMAL_WAIT` y espera; al bajar a `MODERATE`, recarga y sigue (N9);
  - **espacio por debajo del margen antes de un lote, o error `SQLITE_FULL`:** no publica, pausa la tanda con `no_space` y termina;
  - **modelo no `Verified`:** pausa con `model_missing` antes de cargar nada (N10).
- [ ] T044 [P] [US4] Ampliar `DefaultIndexingRepositoryTest`:
  - `pause` guarda antes de cancelar (N8);
  - `resume` borra la pausa y encola los pendientes;
  - `retry` pone `pending`, añade a la tanda y encola;
  - `PauseReason` en `state`: usuario, sin espacio y modelo ausente desde la tanda; temperatura desde el progreso; límite del sistema desde `WorkInfo`.
- [ ] T045 [P] [US4] Ampliar `IndexViewModelTest` e `IndexScreenTest`: en pausa (P3), pausa automática (P4), sin espacio (P5) y modelo ausente (P7), con su acción.

### Implementation for User Story 4

- [ ] T046 [US4] Implementar en `IngestRunner` la espera térmica, la comprobación de espacio (con `DeviceResources::freeBytes`) y el mapeo de `SQLITE_FULL` (código 13), y el modelo ausente (R10). Comprobar T043.
- [ ] T047 [US4] Implementar en `DefaultIndexingRepository` `pause`, `resume` y `retry` con sus casos de uso. `ImportQueueRepository.retry` y `remove` de un documento pasan por la tanda (contracts/indexing-api.md). Conectar `IndexingActionReceiver` a `pause`. Añadir P10 (acceso provisional en Inicio, `PendingDestination`). Comprobar T044.
- [ ] T048 [US4] Completar en `IndexScreen` los estados P3, P4, P5 y P7, con `Setup` como destino de P7. Comprobar T045.
- [ ] T049 [US4] En el teléfono, simular la temperatura (`cmd thermalservice override-status 3` y `reset`) y comprobar la pausa en 5 s o menos y la reanudación sola (SC-006). Pausar y reanudar desde la pantalla y desde la notificación (SC-005).

**Checkpoint**: control completo y pausas automáticas explicadas.

---

## Phase 7: User Story 5 - Pantalla fiel al mockup, accesible y coherente con la Ingesta (Priority: P2)

**Goal**: todos los estados de la pantalla, la Ingesta actualizada y accesibilidad completa.

**Independent Test**: capturas del teléfono frente al PNG 08. Todos los estados pasan las comprobaciones de accesibilidad en claro y oscuro con la fuente al 200 %. La cola de Ingesta muestra «Indexando» y pierde los `done`.

### Tests for User Story 5

- [ ] T050 [P] [US5] Ampliar `IndexScreenTest`:
  - P1 «Todo actualizado» con y sin errores;
  - P2 indeterminado sin porcentaje;
  - P6 documentos con error y «Reintentar»;
  - P8 biblioteca no disponible;
  - P9 tiempo restante y «y %d documentos más en cola»;
  - anuncios U3 en el resumen `liveRegion`.
- [ ] T051 [P] [US5] Ampliar `…/test/feature/ingest/IngestViewModelTest.kt` e `IngestScreenTest.kt`:
  - «Indexando · N de M fragmentos» con barra;
  - «En pausa»;
  - los chips y frases de `embedding_failed` e `incomplete`;
  - un `done` sale de la cola;
  - la CTA se habilita con `parsed`/`embedding` sin trabajo y con la tanda en pausa.
- [ ] T052 [P] [US5] Crear `…/androidTest/feature/indexing/IndexScreenAccessibilityTest.kt`, parametrizada como la de F06: todos los estados de `IndexPreviewData`, en claro y en oscuro, con la fuente al 200 % (SC-008).

### Implementation for User Story 5

- [ ] T053 [US5] Completar en `IndexScreen` P1, P2, P6, P8 y P9, y la semántica U1–U3 (`liveRegion = Polite` en un resumen invisible). Comprobar T050.
- [ ] T054 [US5] Ampliar `DefaultImportQueueRepository`, `IngestTexts` e `IngestScreen` con los estados de index-ui §Ingesta: documentos `embedding` en la cola, `Indexing`, `Paused` y los motivos nuevos en es y en. Comprobar T051.
- [ ] T055 [P] [US5] Crear `…/microrag/feature/indexing/IndexPreviews.kt` con `IndexPreviewData` (datos de ejemplo etiquetados, constitución V) para todos los estados, en claro y en oscuro. Ampliar `IngestPreviews` con «Indexando» y «En pausa». Comprobar T052 en el emulador.
- [ ] T056 [US5] Capturar en el teléfono los estados en curso (como el PNG), en pausa, pausa térmica, sin tareas y con error, en claro y en oscuro. Compararlos con el PNG 08 y listar las diferencias para el informe (constitución VII).

**Checkpoint**: UI cerrada y accesible.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T057 [P] Crear `…/androidTest/data/indexing/IndexingStrictModeTest.kt`: `start`, la observación del estado, `pause` y `resume` llamados desde el hilo principal, con el runner y el embedder determinista, sin violaciones de disco. Con control negativo, como F06 (SC-009).
- [ ] T058 [P] Ampliar `…/androidTest/data/rag/RagIndexBenchmarkTest.kt` (R7):
  - p95 vectorial y BM25 con 10k fragmentos y *m* = 0, comparados con F04 (umbral: +2 ms en el teléfono);
  - con *m* = 4.300, solo registrar.

  Ejecutarla en el teléfono. **Si el p95 con *m* = 0 sube más de 2 ms, revisar la consulta antes de seguir.**
- [ ] T059 [P] Crear `…/androidTest/data/indexing/IndexingBenchmarkTest.kt` (`@RequiresModels`, R13). Con `text_200_pages.pdf`, runner real, `ChunkEmbeddingSession` y un índice temporal:
  - tiempo total y fragmentos por segundo en GPU y en CPU (backend forzado);
  - etiqueta `indexing-benchmark`;
  - SC-001 (≤ 5 min en GPU) solo en el A063.

  Ejecutarla en el teléfono.
- [ ] T060 Actualizar `docs/roadmap/INVENTARIO.md`:
  - G29: etapas resueltas (D-040);
  - ficha P08: estados implementados con P1–P11;
  - el hueco 11 (filtro de búsquedas): resuelto en F07.
- [ ] T061 Puertas de calidad:
  - pruebas Python;
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`;
  - suite instrumentada (sin `@RequiresModels` ni `@RequiresNetwork`) en el emulador API 37, en la AVD API 33 y en el teléfono;
  - las de `@RequiresModels` en el teléfono.

  Todo en verde, salvo las 4 de navegación de la API 33 ya conocidas (F06 §7).
- [ ] T062 Recorrido manual completo en el teléfono (quickstart §6), incluido TalkBack en la pantalla de estado.
- [ ] T063 Documentación de cierre:
  - `specs/007-indexing/report.md`, con:
    - resumen y puertas;
    - validación por historia y criterio;
    - cifras: SC-001, ritmos de GPU y CPU, búsquedas con filtro, latencia de arranque y lotes;
    - diferencias frente al PNG;
    - desviaciones del plan y pendientes;
  - estado de F07 en `docs/roadmap/FEATURES.md`;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias. **T003 puede parar el trabajo** si el lote acelera (cambia FR-014). T002 fija la excepción que usa T013.
- **Foundational (fase 2):** depende de la fase 1 (T001 para T005; T002 para T013). **Bloquea todas las historias.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2 (fase 4):** depende de US1 (el worker indexa). **T033 puede parar el trabajo** (riesgo 1).
- **US3 (fase 5):** depende de US1. Puede ir en paralelo con US2.
- **US4 (fase 6):** depende de US3 (pantalla y repositorio) y de US1 (runner).
- **US5 (fase 7):** depende de US3 y US4.
- **Polish (fase 8):** depende de todo.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Modelos y repositorio antes que ViewModel; ViewModel antes que pantalla.
- Las verificaciones en el dispositivo (T026, T033, T049 y T056) cierran su historia.

### Parallel Opportunities

- **Fase 1:** T001, T002 y T003.
- **Fase 2:** todas las pruebas (T004–T009); después T011, T012, T013, T016 y T017.
- **US1:** T020, T021 y T022.
- **US2 y US3** en paralelo tras US1.
- **Polish:** T057, T058 y T059.

## Parallel Example: Foundational tests

```text
T004 DocumentTitleTest          T005 EmbeddingOverflowTest     T006 EngineCoordinatorTest
T007 DataStoreIngestRunStoreTest   T008 RagIndexWriteTest      T009 RagIndexVisibilityTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1, con las mediciones tempranas T002 y T003 en el teléfono.
2. Fase 2.
3. US1: el runner indexa y publica. **Parar y validar** con T026.

### Incremental Delivery

1. Tras el MVP, US2: robustez, con la verificación temprana T033 en el teléfono.
2. US3: pantalla con progreso real.
3. US4: control y pausas.
4. US5: estados restantes, Ingesta y accesibilidad.
5. Polish: mediciones, puertas, recorrido e informe.
