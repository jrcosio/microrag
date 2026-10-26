---

description: "Lista de tareas de F07 — Indexación"
---

# Tasks: Indexación (F07)

**Input**: Documentos de diseño en `/specs/007-indexing/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios**. Los exigen la constitución (VI) y los criterios SC-001 a SC-010. En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec. El orden de ejecución es US1 → US3 → US2 → US4 → US5, porque la robustez (US2) necesita el repositorio de la tanda que construye US3:
- US1 — Indexar los documentos importados de principio a fin (P1);
- US3 — Ver el progreso real (P1);
- US2 — La indexación sobrevive a todo (P1);
- US4 — Pausar, reanudar, reintentar y pausas automáticas (P2);
- US5 — Pantalla fiel al mockup, accesible y coherente con la Ingesta (P2).

**Entorno** (quickstart):
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Dispositivos:
  - teléfono de referencia: `adb -s P31281001066`, con los modelos instalados;
  - emulador `Pixel_10`: `emulator-5554`; con modelos, indexa en CPU (research R4);
  - AVD `MicroRAG_API33`: `emulator-5556`.

  **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument` (quickstart §3).
- Python: siempre `offline/.venv/bin/python -I`.
- Antes de cada commit, las pruebas se ejecutan y se comprueba su código de salida por separado (lección de F06).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

**UI pendiente de aprobación**: las propuestas P1–P12 de contracts/index-ui.md se aprueban en la parada tras analyze. Las tareas que las implementan dan por hecha esa aprobación:
- T033: hero sin título;
- T035: P1, P2, P9 y P12;
- T041: P11;
- T051: P3–P5 y P7;
- T052: P10;
- T057: P6 y P8;
- T058: cambios en la Ingesta.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Las rutas son relativas a la raíz del repositorio. Abreviaturas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/sharedTest/` usan el mismo paquete bajo `app/src/test/java/`, `app/src/androidTest/java/` y `app/src/sharedTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: fijar la regla de D-036 desde Python y medir en el teléfono lo que puede cambiar el diseño (research R5 y R6).

- [X] T001 [P] Crear `offline/gen_overflow_cases.py`, que escribe `offline/golden/embedding_overflow.jsonl` (research R6). Para cada texto guarda la secuencia de recortes de la regla de `offline/eval/builds.py`, con longitudes en puntos de código:
  - `int(len(text) × 0,9)` y después hasta el último espacio;
  - hasta 12 intentos.

  Casos:
  - un texto español largo;
  - acentos y `ñ`;
  - emoji fuera del BMP;
  - un texto sin espacios;
  - un espacio justo en el corte;
  - un texto vacío.

  Extraer la regla a una función `overflow_cut(text)` que use también `builds.py`, para que haya una sola fuente. Opción `--check`. Prueba en `offline/tests/test_overflow_cases.py`: regenerar y comparar.
- [X] T002 [P] Crear `…/androidTest/data/indexing/EmbeddingOverflowDeviceTest.kt` (`@RequiresModels`, research R6). Con `LiteRtTextEmbedder` real en CPU y en GPU (la GPU se salta en un emulador):
  - embeber un texto de unos 700 tokens;
  - registrar con la etiqueta `overflow-probe` la clase y el mensaje de la excepción;
  - comprobar que tras los recortes de D-036 el modelo lo acepta.

  Ejecutarla en el teléfono. Anotar en research R6 la excepción real y si se distingue de otros fallos.
- [X] T003 [P] Crear `…/androidTest/data/indexing/EmbeddingBatchProbeTest.kt` (`@RequiresModels`, research R5). Con 64 fragmentos reales de `text_200_pages.pdf`, medir en GPU y en CPU:
  - `computeEmbedding` en serie;
  - `computeEmbeddingBatch` en lotes de 8 y de 16.

  Registrar fragmentos por segundo y el coseno mínimo entre los dos modos (`batch-probe`). Ejecutarla en el teléfono y anotar las cifras en research R5. **Si el lote acelera al menos un 30 % con coseno ≥ 0,9999, parar y consultar al usuario antes de la fase 3** (cambia FR-014).

**Checkpoint**: regla de recorte fijada y decisión de lotes tomada con cifras.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos, funciones puras, índice, engines y tanda que necesitan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase.

### Tests (primero, deben fallar)

- [X] T004 [P] `…/test/domain/rag/DocumentTitleTest.kt` (research R11):
  - «Manual técnico.pdf» → «Manual técnico»;
  - «notas» queda igual;
  - «.bashrc» queda igual;
  - «a.b.pdf» → «a.b»;
  - una extensión de 11 caracteres o no alfanumérica no se quita.
- [X] T005 [P] `…/test/rag/runtime/EmbeddingOverflowTest.kt`: lee `offline/golden/embedding_overflow.jsonl` desde `sharedTest` y exige la misma secuencia de recortes (R6).
- [X] T006 [P] Ampliar `…/test/rag/runtime/EngineCoordinatorTest.kt` (research R4):
  - `withHeldEngine` mantiene el engine durante el bloque y solo acepta engines de embedding;
  - `releaseUnheld` cierra los no retenidos y respeta el retenido;
  - `releaseGenerators` cierra solo los de generación;
  - **otra petición de GPU mientras hay una retenida:** espera sin bloquear el `loadLock`, así que una petición de CPU en paralelo se atiende;
  - la regla de una sola GPU sigue vigente;
  - `EngineMemoryCallbacks` con `TRIM_MEMORY_UI_HIDDEN` ya no cierra un engine retenido.
- [X] T007 [P] `…/test/data/indexing/DataStoreIngestRunStoreTest.kt` (data-model §2):
  - altas sin duplicados y en orden;
  - bajas;
  - descarte de terminados y vaciado en una sola edición;
  - pausa con causa;
  - reanudar;
  - `gpu_check`;
  - persistencia entre instancias.
- [X] T008 [P] Ampliar `…/androidTest/data/rag/RagIndexWriteTest.kt`:
  - `storedChunkCounts`, donde los ids sin fragmentos dan 0;
  - `storedChunkContents` en orden;
  - `deleteChunks` borra los fragmentos y sus filas FTS y conserva el documento;
  - `insertChunks` calcula `title_norm` con `DocumentTitle` (R11).
- [X] T009 [P] Crear `…/androidTest/data/rag/RagIndexVisibilityTest.kt` (research R7, N7, SC-003). Sobre SQLite-Vector real, con documentos `done`, `embedding`, `parsed`, `error` y `done` excluido (`included = 0`):
  - `vectorSearch` y `textSearch` devuelven solo fragmentos de los `done` incluidos;
  - con *m* > 0 se devuelven exactamente los *k* mejores buscables, comparados con fuerza bruta en Kotlin;
  - con *m* = 0, el mismo resultado que la consulta de F04;
  - `EXPLAIN QUERY PLAN` muestra un solo escaneo vectorial (CTE `MATERIALIZED`).

### Implementation

- [X] T010 Ampliar `…/microrag/domain/ingest/IngestModels.kt` (data-model §3):
  - `IngestError.EMBEDDING_FAILED` e `INCOMPLETE`, reintentables;
  - `QueueItemState.Indexing` y `Paused`;
  - `IngestProgress` con las etapas `EMBEDDING` y `CLOSING`, `waiting: WaitReason?` aparte, `chunksDone`, `chunksTotal`, `chunksPerSecond` y `backend`.

  Ajustar los `when` exhaustivos de `feature/ingest` con textos provisionales; los definitivos van en T058.
- [X] T011 [P] Crear en `…/microrag/domain/indexing/`:
  - `IndexingModels.kt`: `IndexingState`, `RunProgress`, `CurrentDocument`, `PauseReason` y `FailedDocument`;
  - `IndexingRepository.kt` (contracts/indexing-api.md).
- [X] T012 [P] Crear `…/microrag/domain/rag/DocumentTitle.kt` (R11). Comprobar T004.
- [X] T013 [P] Crear en `…/microrag/rag/runtime/`:
  - `EmbeddingOverflow.kt` (R6) y `DocumentEmbedding`;
  - `EmbeddingInputTooLongException` y `EmbeddingEngineException`.

  Añadir `embedDocumentFitting(content, header)` a `TextEmbedder` y a `LiteRtTextEmbedder`: hasta 12 intentos y traducción de la excepción de LiteRT según T002. Ajustar los fakes de prueba existentes. Comprobar T005.
- [X] T014 Ampliar `…/microrag/rag/runtime/EngineCoordinator.kt` con `withHeldEngine`, `releaseUnheld` y `releaseGenerators`. Política: espera de GPU sin el `loadLock`. `EngineMemoryCallbacks.kt` usa `releaseUnheld` (R4). Comprobar T006.
- [X] T015 Ampliar `RagIndex` (`…/microrag/domain/rag/RagIndex.kt`, con `IndexStorageFullException`) y `DefaultRagIndex` (`…/microrag/data/rag/DefaultRagIndex.kt`):
  - `storedChunkCounts`, `storedChunkContents` y `deleteChunks` (con `notifyChanged`);
  - búsquedas con *k* + *m*, filtro `status = 'done' AND included = 1` y CTE `MATERIALIZED` (R7);
  - `title_norm` con `DocumentTitle`;
  - traducción de `SQLITE_FULL` a `IndexStorageFullException` (R10).

  **Adaptar las pruebas existentes que buscan sobre documentos no publicados:** `RagIndexSearchTest`, `RagIndexStrictModeTest` y `RagIndexBenchmarkTest` ponen sus documentos en `DONE`. Actualizar `FakeRagIndex` y la lista de operaciones rechazadas de `RagIndexCompatibilityTest`. Comprobar T008 y T009 en el emulador.
- [X] T016 [P] Crear `…/microrag/data/indexing/DataStoreIngestRunStore.kt` con su interfaz `IngestRunStore` (data-model §2). Comprobar T007.
- [X] T017 [P] Crear `…/microrag/data/device/ThermalStatusSource.kt`: interfaz y `PowerManagerThermalStatusSource`, con `PowerManager.currentThermalStatus` y `addThermalStatusListener` como `Flow<ThermalLevel>` (`NORMAL`, `MODERATE`, `SEVERE_OR_WORSE`). Ampliar `LayeringTest`:
  - `android.os.PowerManager` solo en `data/device`;
  - `feature/indexing` no importa WorkManager, `androidx.sqlite`, LiteRT ni `PowerManager`, como `feature/ingest`.
- [X] T018 En `…/microrag/MicroRagApplication.kt`, añadir `setGreedySchedulerEnabled(false)` con `@OptIn(ExperimentalConfigurationApi::class)` y un comentario que remita a research R2. Comprobar que siguen en verde:
  - `WorkManagerIngestSchedulerTest` de F06;
  - las pruebas de WorkManager de F03, como la descarga en API 31–33;
  - `IngestFlowTest`.
- [X] T019 Registrar en Koin (`DataModule`, `DomainModule`) `IngestRunStore`, `ThermalStatusSource` y el resto de definiciones nuevas, y extender `AppModulesTest` (`verify()`).

**Checkpoint**: tipos, índice filtrado, engines retenidos, tanda y configuración listos. JVM, lint y las instrumentadas de F04 y F06 en verde.

---

## Phase 3: User Story 1 - Indexar los documentos importados de principio a fin (Priority: P1) 🎯 MVP

**Goal**: cada documento de la tanda acaba `done` con todos sus fragmentos y vectores, o con un error concreto, y no se puede buscar hasta entonces.

**Independent Test**: PDF, TXT y MD indexados. Fragmentos iguales al troceado, con ordinales 0..n-1 y vectores unitarios de 256. Ninguna búsqueda devuelve un documento a medias.

### Tests for User Story 1

- [X] T020 [P] [US1] Crear `…/sharedTest/data/indexing/FakeTextEmbedder.kt`, común a la JVM y a las instrumentadas: vector derivado del hash del texto embebido, normalizado, con fallo de longitud o de engine programable.
- [X] T021 [P] [US1] Ampliar `…/test/data/ingest/IngestRunnerTest.kt` con `FakeTextEmbedder` y `RecordingLogger`:
  - **fase 1:** los `pending` pasan a `parsed` antes de embeber nada;
  - **fase 2:** `embedding`, lotes de 64 y `done`;
  - **texto embebido:** el de N5 (prefijo y «título › ruta»); `content` sin cabecera;
  - **`token_estimate`:** con `CHARS_PER_TOKEN`;
  - **reanudación (N2):** con 130 guardados, solo se embeben los siguientes;
  - **desajuste:** se borran y se rehace desde 0;
  - **cierre:** si el recuento no cuadra, `error incomplete`;
  - **`EmbeddingEngineException` persistente:** `embedding_failed`, y el siguiente documento sigue (N11);
  - **documento quitado a mitad:** se salta sin escribir;
  - **cancelación:** el lote parcial se guarda (N4);
  - **N16:** un `pending` añadido a la tanda a mitad se trocea antes del siguiente documento;
  - **N17:** al terminar se vacía la tanda;
  - **logs:** sin nombres de archivo ni texto (SC-010).

  **Adaptar las expectativas de F06** que esperaban `PARSED` tras `process`. Ahora la fase 1 sola se prueba con la sesión de embeddings ausente o con el modelo no verificado.
- [X] T022 [P] [US1] `…/test/data/indexing/ChunkEmbeddingSessionTest.kt` (R4):
  - libera los generadores antes de cargar;
  - en un emulador (`Build.HARDWARE` falso inyectable), nunca GPU;
  - sin `gpu_check`, valida la GPU con 3 textos fijos frente a la CPU y guarda el resultado;
  - con coseno < 0,99 o fallo de carga, usa la CPU y lo registra;
  - un `gpu_check` de otro modelo o de otra versión se repite;
  - retiene el engine durante la sesión y lo libera al cerrarla;
  - un fallo a mitad recarga una vez, después pasa de GPU a CPU, y después lanza.
- [X] T023 [P] [US1] Crear `…/androidTest/data/indexing/IndexingFlowTest.kt` con SQLite-Vector real, el importador y el runner de F06, `FakeTextEmbedder` y un `ThermalStatusSource` controlable:
  - TXT, MD y `text_3_pages.pdf` acaban `done`, con los fragmentos del troceado;
  - durante la fase 2, una búsqueda no devuelve fragmentos del documento en curso (SC-003);
  - **interrupción determinista, en tres puntos:** durante la fase 1, a mitad de los embeddings de `text_200_pages` y en el cierre. La reanudación da los mismos 430 fragmentos, sin duplicados (SC-002, parte automática);
  - quitar un documento a mitad de la fase 2: se borran sus fragmentos y el resto sigue;
  - temperatura `SEVERE` simulada: espera, y al bajar continúa.

### Implementation for User Story 1

- [X] T024 [US1] Crear `…/microrag/data/indexing/ChunkEmbeddingSession.kt` (R4, N15):
  - libera los generadores;
  - elige el backend: GPU validada, nunca en un emulador;
  - abre el engine retenido de `EmbeddingGemma`;
  - expone `embed(content, header)`;
  - recarga una vez y después pasa a CPU.

  Usa `EngineFactory`, `ModelFileRepository.pathOf` y el SHA-256 del modelo verificado. Comprobar T022.
- [X] T025 [US1] Ampliar `…/microrag/data/ingest/IngestRunner.kt` con las fases 1 y 2 (research R8–R9, reglas N1–N5, N11, N13, N16 y N17):
  - comparación con lo guardado;
  - lotes de 64;
  - lote parcial en `NonCancellable`, con un tope de 5 s;
  - comprobación de estado antes de cada lote;
  - relectura de la tanda entre documentos;
  - cierre con recuento;
  - progreso `EMBEDDING`/`CLOSING`, con el ritmo de los dos últimos lotes;
  - vaciado de la tanda al terminar.

  Mantener intacta la lógica de F06 para la fase 1. Comprobar T021.
- [X] T026 [US1] Ampliar `…/microrag/data/ingest/IngestWorker.kt` e `IngestScheduler.kt`, sin el primer plano (US2):
  - claves de progreso de data-model §5;
  - `cancel()`;
  - `replace(ids)`;
  - `stopReason` y `runAttemptCount`.

  Actualizar `WorkManagerIngestSchedulerTest`, y también `IngestFlowTest`, `IngestStrictModeTest` e `IngestBenchmarkTest` de F06 (el runner necesita ahora su sesión de embeddings; se inyecta `FakeTextEmbedder` y sus expectativas pasan a `done`). Comprobar T023 en el emulador.
- [ ] T027 [US1] En el teléfono, indexar con la app real un PDF, un TXT y un MD desde la Ingesta. Comprobar:
  - en el log, `ingest done` y el backend elegido tras la validación de la GPU;
  - en la base, que los fragmentos tienen vector y los documentos están `done`.

  > Estado (2026-10-27): hecho en el emulador API 37 con la app real (CPU, porque es emulador): 6 documentos `done`, un engine cargado una vez y liberado al final, ordinales 0..n−1, vectores de 256 con norma 1 y `title_norm` sin extensión. El trabajo arrancó 12 ms después de encolarse. **Pendiente en el teléfono** (validación de la GPU), que no estaba conectado.

**Checkpoint**: MVP funcional: la app indexa y publica documentos completos.

---

## Phase 4: User Story 3 - Ver el progreso real (Priority: P1)

**Goal**: la pantalla Estado de indexación muestra, como el PNG 08, el estado en curso, el total desconocido, «Todo actualizado» y el tiempo restante, con cifras reales que sobreviven al cierre de la app.

**Independent Test**: con 3 documentos en marcha, N y M cuadran con lo guardado y con los troceados. Al reabrir, el mismo progreso.

### Tests for User Story 3

- [X] T028 [P] [US3] Crear `…/test/data/indexing/DefaultIndexingRepositoryTest.kt` con `FakeRagIndex`, `work-testing`, un `IngestRunStore` en memoria y temperatura fija:
  - `Idle` sin tanda;
  - `Active` con M desconocido mientras queda un `pending` (FR-021/022);
  - N/M al guardar lotes y al cerrar documentos;
  - documentos con error fuera de N y M;
  - `CurrentDocument` y su etapa (data-model §3, con `waiting` aparte);
  - documentos en cola;
  - tiempo restante solo con ritmo estable (R8, FR-023);
  - `IndexUnavailable`.
- [X] T029 [P] [US3] Tests de `start` en el mismo fichero:
  - descarta los terminados;
  - añade `pending`, `parsed` y `embedding` sin duplicar;
  - quita la pausa;
  - encola;
  - devuelve el número añadido.
- [X] T030 [P] [US3] `…/test/feature/indexing/IndexViewModelTest.kt`:
  - mapea `IndexingState` a `IndexUiState`: porcentaje, «N / M», etapas, documento actual, tiempo restante y acción principal;
  - expone `onPause`, `onResume` y `onRetry`.
- [X] T031 [P] [US3] `…/test/feature/indexing/IndexScreenTest.kt` (Robolectric, qualifiers `es` y `en`):
  - **estado en curso del PNG 08:**
    - hero con eyebrow;
    - anillo con porcentaje;
    - «N / M fragmentos» en formato local;
    - 4 etapas con sus estados;
    - «Procesando ahora»;
    - «Pausar indexación»;
  - **P1:** «Todo actualizado»;
  - **P2:** anillo indeterminado, sin porcentaje;
  - **P9:** tiempo restante y «y %d documentos más en cola»;
  - **P12:** carga.

### Implementation for User Story 3

- [X] T032 [US3] Implementar en `…/microrag/data/indexing/DefaultIndexingRepository.kt` `state` y `start` (R8, data-model §3 y §7), con los casos de uso `Observe` y `Start`. El `StartIndexingUseCase` de F06 delega en `start()`. Comprobar T028 y T029.
- [X] T033 [US3] Ampliar `LisdataHeroCard` (`…/microrag/core/designsystem/component/Cards.kt`) con el título opcional. Actualizar su preview, el catálogo y `CatalogAccessibilityTest` si cambia.
- [X] T034 [US3] Crear `…/microrag/feature/indexing/IndexUiState.kt`, `IndexTexts.kt` e `IndexViewModel.kt`. Comprobar T030.
- [X] T035 [US3] Crear `…/microrag/feature/indexing/IndexScreen.kt` (`IndexRoute` + `IndexScreen` sin estado) con la estructura de contracts/index-ui.md. Crear `strings_indexing.xml` (es y en) con los textos del PNG y de P1, P2, P9 y P12; las fases siguientes lo amplían. Comprobar T031.
- [X] T036 [US3] En `…/microrag/core/navigation/MicroRagNavHost.kt`, sustituir el placeholder de `Index` por `IndexRoute`. Actualizar `NavigationTest` y `AppModulesTest`.

**Checkpoint**: la pantalla muestra el progreso real de la indexación.

---

## Phase 5: User Story 2 - La indexación sobrevive a todo (Priority: P1)

**Goal**: el trabajo sigue con la app cerrada, con notificación, y se reanuda solo tras una muerte del proceso, un reinicio o una parada del sistema.

**Independent Test**: matar el proceso en tres momentos sin abrir la app. El documento acaba `done` con los 430 fragmentos, sin duplicados ni huecos.

### Tests for User Story 2

- [X] T037 [P] [US2] `…/test/data/indexing/IndexingNotificationsTest.kt` (Robolectric, R3):
  - canal `indexing` de importancia baja;
  - textos sin nombres de archivo;
  - progreso determinado e indeterminado;
  - acción «Pausar» con `PendingIntent` explícito e inmutable;
  - toque → `MainActivity` con `ACTION_OPEN_INDEXING`.
- [X] T038 [P] [US2] Crear `…/test/data/ingest/IngestWorkerTest.kt` (`work-testing`, R1): si `setForeground` lanza `ForegroundServiceStartNotAllowedException`, el worker sigue, publica la notificación normal y lo reintenta en el siguiente lote.
- [X] T039 [P] [US2] Tests de `reconcile` en `DefaultIndexingRepositoryTest` (R12, N14):
  - tanda sin trabajo → encola;
  - trabajo `ENQUEUED` con causa del sistema → `replace`;
  - `RUNNING` → no toca;
  - tanda pausada → nada;
  - índice no disponible → nada.

  Añadir también la tabla de data-model §7: `SYSTEM_LIMIT` desde `WorkInfo` y `THERMAL` con temperatura grave aunque el sistema haya parado el trabajo.
- [X] T040 [P] [US2] `…/test/MainActivityRoutingTest.kt` (Robolectric):
  - con el onboarding completado, `ACTION_OPEN_INDEXING` abre `Index` al crear y en `onNewIntent` sin otra instancia (`singleTop`);
  - sin onboarding, la acción se ignora.

  *Hecho en `NavigationTest`*: la apertura de `Index` sobre Inicio y el filtro del onboarding (`MainActivity.indexingRequestsFor`). La instancia única por `singleTop` se comprueba en el dispositivo (T045).

### Implementation for User Story 2

- [X] T041 [US2] Crear `…/microrag/data/indexing/IndexingNotifications.kt` e `IndexingActionReceiver.kt` (no exportado, en el manifiesto), con los textos de P11 en `strings_indexing.xml` (es y en). Comprobar T037.
- [X] T042 [US2] Añadir a `IngestWorker` el primer plano `dataSync` (`setForeground` al empezar y en cada lote) y la notificación normal si falla (R1). Comprobar T038.
- [X] T043 [US2] Implementar `reconcile` en `DefaultIndexingRepository` y `ReconcileIndexingUseCase`, y la causa `SYSTEM_LIMIT`/`THERMAL` en `state`. Llamar a `reconcile` desde `MainActivity.onStart` en el *scope* de la aplicación. Comprobar T039.
- [X] T044 [US2] `MainActivity` en `launchMode="singleTop"`, con `ACTION_OPEN_INDEXING` atendida en `onCreate` y `onNewIntent` solo con el onboarding completado (R3). Atrás desde esa entrada → Inicio. Comprobar T040.
- [ ] T045 [US2] **Verificación temprana en los dispositivos** (quickstart §4, riesgo 1). Con la app real en el teléfono y en el emulador:
  - anotar la latencia de arranque sin planificador en proceso (umbral: 5 s), también pulsando Inicio justo después de «Iniciar indexación»;
  - comprobar que el proceso es servicio en primer plano y que la notificación avanza;
  - matar con `kill -9`, **sin abrir la app**, en tres momentos: durante el troceado de un PDF de 2.000 páginas, a mitad de los embeddings y entre dos documentos. Cada vez:
    - el trabajo termina solo;
    - el proceso no queda congelado;
    - los fragmentos cuadran, sin duplicados ni huecos (SC-002);
  - en el emulador API 37, `OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS` y `data_sync_fgs_timeout_duration` de 60 s: el trabajo continúa en tramos;
  - en la AVD API 33, `ModelDownloadSmokeTest` (con red) sigue funcionando sin el planificador en proceso;
  - reiniciar el teléfono a mitad: continúa tras desbloquear.

  Anotar los resultados en research R1–R2. **Si sigue congelándose o la latencia pasa de 5 s, parar y consultar al usuario.**

**Checkpoint**: la indexación sobrevive a muertes, reinicios y límites del sistema.

---

## Phase 6: User Story 4 - Pausar, reanudar, reintentar y pausas automáticas (Priority: P2)

**Goal**: control del usuario y pausas automáticas coherentes con el trabajo.

**Independent Test**:
- pausar y reanudar sin repetir;
- temperatura simulada: pausa y sigue sola;
- sin espacio: para sin publicar y avisa;
- modelo ausente: espera y lo explica.

### Tests for User Story 4

- [X] T046 [P] [US4] Ampliar `IngestRunnerTest` (R10):
  - **temperatura `SEVERE`:** guarda el lote, libera el engine, publica `waiting = THERMAL` con la etapa real y espera. Al bajar a `MODERATE`, recarga y sigue (N9);
  - **espacio por debajo del margen antes de un lote, o `IndexStorageFullException`:** no publica, pausa la tanda con `no_space` y termina;
  - **modelo no `Verified`:** tras la fase 1, pausa con `model_missing` antes de cargar nada (N10).
- [X] T047 [P] [US4] Ampliar `DefaultIndexingRepositoryTest`:
  - `pause` guarda antes de cancelar (N8);
  - `resume` borra la pausa y encola los pendientes;
  - `retry` pone `pending`, añade a la tanda y encola;
  - `PauseReason` desde la tanda (usuario, sin espacio, modelo ausente).
- [X] T048 [P] [US4] Ampliar `IndexViewModelTest` e `IndexScreenTest` con su acción en cada estado:
  - en pausa (P3);
  - pausa automática por temperatura y por el sistema (P4);
  - sin espacio (P5);
  - modelo ausente (P7).

### Implementation for User Story 4

- [X] T049 [US4] Implementar en `IngestRunner` la espera térmica y la comprobación de espacio (con `DeviceResources::freeBytes` e `IndexStorageFullException`), y el modelo ausente (R10). Comprobar T046.
- [X] T050 [US4] Implementar en `DefaultIndexingRepository` `pause`, `resume` y `retry` con sus casos de uso. `ImportQueueRepository.retry` y `remove` de un documento pasan por la tanda (contracts/indexing-api.md). Conectar `IndexingActionReceiver` a `pause`. Comprobar T047.
- [X] T051 [US4] Completar en `IndexScreen` los estados P3, P4, P5 y P7, con `Setup` como destino de P7. Comprobar T048.
- [X] T052 [US4] Añadir P10 (acceso provisional en Inicio, `PendingDestination`) con su prueba en `NavigationTest`.
- [ ] T053 [US4] En el teléfono:
  - **temperatura:** simularla con `cmd thermalservice override-status 3` y `reset`, con la app visible y también con el trabajo sin primer plano (tras matar el proceso). Comprobar la pausa en 5 s o menos con la causa «Pausa por temperatura» y la reanudación sola (SC-006);
  - **pausa:** pausar y reanudar desde la pantalla y desde la notificación. Medir el tiempo hasta la pausa (≤ 5 s) y los fragmentos recalculados al reanudar (≤ 1), en el log (SC-005).

**Checkpoint**: control completo y pausas automáticas explicadas.

---

## Phase 7: User Story 5 - Pantalla fiel al mockup, accesible y coherente con la Ingesta (Priority: P2)

**Goal**: los estados restantes de la pantalla, la Ingesta actualizada y accesibilidad completa.

**Independent Test**:
- capturas del teléfono frente al PNG 08;
- todos los estados pasan las comprobaciones de accesibilidad en claro y oscuro con la fuente al 200 %;
- la cola de Ingesta muestra «Indexando» y pierde los `done`.

### Tests for User Story 5

- [X] T054 [P] [US5] Ampliar `IndexScreenTest`:
  - P6: documentos con error y «Reintentar»;
  - P8: biblioteca no disponible;
  - anuncios U3 en el resumen `liveRegion`.
- [X] T055 [P] [US5] Ampliar `…/test/feature/ingest/IngestViewModelTest.kt` e `IngestScreenTest.kt`:
  - «Indexando · N de M fragmentos» con barra;
  - «En pausa»;
  - los chips y frases de `embedding_failed` e `incomplete`;
  - un `done` sale de la cola;
  - la CTA se habilita con `parsed` o `embedding` sin trabajo y con la tanda en pausa.
- [X] T056 [P] [US5] Crear `…/androidTest/feature/indexing/IndexScreenAccessibilityTest.kt`, parametrizada como la de F06: todos los estados de `IndexPreviewData`, en claro y en oscuro, con la fuente al 200 % (SC-008).

### Implementation for User Story 5

- [X] T057 [US5] Completar en `IndexScreen` P6 y P8, y la semántica U1–U3 (`liveRegion = Polite` en un resumen invisible). Comprobar T054.
- [X] T058 [US5] Ampliar `DefaultImportQueueRepository`, `IngestTexts` e `IngestScreen` con los estados de index-ui §Ingesta: documentos `embedding` en la cola, `Indexing`, `Paused` y los motivos nuevos en es y en. Comprobar T055.
- [X] T059 [P] [US5] Crear `…/microrag/feature/indexing/IndexPreviews.kt` con `IndexPreviewData` (datos de ejemplo etiquetados, constitución V) para todos los estados, en claro y en oscuro. Ampliar `IngestPreviews` con «Indexando» y «En pausa». Comprobar T056 en el emulador.
- [ ] T060 [US5] Capturar en el teléfono, en claro y en oscuro:
  - en curso (como el PNG);
  - en pausa;
  - pausa térmica;
  - sin tareas;
  - con error.

  Compararlas con el PNG 08 y listar las diferencias para el informe (constitución VII).

**Checkpoint**: UI cerrada y accesible.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T061 [P] Crear `…/androidTest/data/indexing/IndexingStrictModeTest.kt`: `start`, la observación del estado, `pause` y `resume` llamados desde el hilo principal, con el runner y `FakeTextEmbedder`, sin violaciones de disco. Con control negativo, como F06 (SC-009).
- [ ] T062 [P] Ampliar `…/androidTest/data/rag/RagIndexBenchmarkTest.kt` (R7):
  - **10k fragmentos, *m* = 0:** p95 vectorial y BM25 comparados con F04 (umbral: +2 ms en el teléfono);
  - ***m* = 4.300:** registrar. Si, sumado al embedding de consulta (≈ 120 ms), supera 150 ms, consultar.

  Ejecutarla en el teléfono. **Si el p95 con *m* = 0 sube más de 2 ms, revisar la consulta antes de seguir.**

  *Código hecho. Emulador API 37, orientativo (2026-10-27):* con *m* = 0, p95 vectorial 20,8 ms y BM25 21,2 ms; con *m* = 4.300, 46,1 ms y 45,7 ms. Falta la ejecución en el teléfono.
- [ ] T063 [P] Crear `…/androidTest/data/indexing/IndexingBenchmarkTest.kt` (`@RequiresModels`, R13). Con `text_200_pages.pdf`, el runner real, `ChunkEmbeddingSession` y un índice temporal:
  - tiempo total y fragmentos por segundo en GPU y en CPU (backend forzado);
  - muestreo de `EngineCoordinator.loaded` durante la tanda, que nunca contiene el LLM (SC-007);
  - etiqueta `indexing-benchmark`;
  - SC-001 (≤ 5 min en GPU), solo en el A063.

  Ejecutarla en el teléfono.

  *Código hecho. Emulador API 37, CPU (2026-10-27):* 430 fragmentos en 103 s, 4,21 fragmentos/s y ningún LLM durante la tanda (sin Gemma instalado; la GPU se salta en el emulador). Falta la ejecución en el teléfono, donde además se precarga Gemma.
- [X] T064 Actualizar `docs/roadmap/INVENTARIO.md`:
  - **G28** (exclusión en la búsqueda vectorial): resuelto en F07 con *k* + *m* y filtro (D-040);
  - **G29:** etapas resueltas (D-040);
  - **ficha P08:** estados implementados con P1–P12.
- [ ] T065 Puertas de calidad:
  - pruebas Python;
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`;
  - suite instrumentada (sin `@RequiresModels` ni `@RequiresNetwork`) en el emulador API 37, en la AVD API 33 y en el teléfono;
  - las de `@RequiresModels` en el teléfono.

  Todo en verde, salvo las 4 de navegación de la API 33 ya conocidas (F06 §7).
- [ ] T066 Recorrido manual completo en el teléfono (quickstart §6), incluido TalkBack en la pantalla de estado.
- [ ] T067 Documentación de cierre:
  - `specs/007-indexing/report.md`:
    - resumen y puertas;
    - validación por historia y criterio;
    - cifras: SC-001, ritmos en GPU y CPU, validación de la GPU, búsquedas con filtro, latencia de arranque y lotes;
    - diferencias frente al PNG;
    - desviaciones del plan y pendientes;
  - estado de F07 en `docs/roadmap/FEATURES.md`;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
  - **T003 puede parar el trabajo** si el lote acelera (cambia FR-014).
  - T002 fija la excepción que usa T013.
- **Foundational (fase 2):** depende de la fase 1 (T001 para T005; T002 para T013). **Bloquea todas las historias.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US3 (fase 4):** depende de US1. Construye el repositorio de la tanda (`state` y `start`).
- **US2 (fase 5):** depende de US1 y de US3 (`reconcile` y la causa de pausa usan el repositorio). **T045 puede parar el trabajo** (riesgo 1).
- **US4 (fase 6):** depende de US3 y de US2 (receptor de la notificación).
- **US5 (fase 7):** depende de US3 y US4.
- **Polish (fase 8):** depende de todo.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Modelos y repositorio antes que ViewModel; ViewModel antes que pantalla.
- Las verificaciones en el dispositivo cierran su historia: T027, T045, T053 y T060.

### Parallel Opportunities

- **Fase 1:** T001, T002 y T003.
- **Fase 2:**
  - todas las pruebas, T004–T009;
  - después, T011, T012, T013, T016 y T017.
- **US1:** T020, T021, T022 y T023.
- **US2:** T037–T040.
- **Polish:** T061, T062 y T063.

## Parallel Example: Foundational tests

```text
T004 DocumentTitleTest          T005 EmbeddingOverflowTest     T006 EngineCoordinatorTest
T007 DataStoreIngestRunStoreTest   T008 RagIndexWriteTest      T009 RagIndexVisibilityTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1, con las mediciones tempranas T002 y T003 en el teléfono.
2. Fase 2.
3. US1: el runner indexa y publica. **Parar y validar** con T027.

### Incremental Delivery

1. Tras el MVP, **US3:** pantalla con progreso real.
2. **US2:** robustez, con la verificación temprana T045 en el teléfono.
3. **US4:** control y pausas.
4. **US5:** estados restantes, Ingesta y accesibilidad.
5. **Polish:** mediciones, puertas, recorrido e informe.
