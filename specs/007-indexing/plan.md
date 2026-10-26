# Implementation Plan: Indexación (F07)

**Branch**: `007-indexing` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-indexing/spec.md`

## Summary

F07 completa la ingesta en el teléfono. Los documentos que F06 importa y trocea pasan a fragmentos con vector guardados en el índice, y solo se pueden buscar cuando están completos. Además entrega la pantalla Estado de indexación (PNG 08).

- **Trabajo:**
  - el `IngestWorker` de F06 pasa a ser un worker largo en primer plano (`dataSync`, «procesamiento de archivos locales»);
  - el runner hace dos fases:
    1. trocear todos los documentos pendientes, para conocer el total;
    2. por documento, volver a trocear en *streaming*, embeber en la GPU (si está validada en el dispositivo; si no, y siempre en un emulador, en la CPU) y guardar en lotes de 64.

  Se reanuda donde se quedó comparando con lo guardado, y publica el documento al final.
- **Robustez:**
  - se desactiva el planificador en proceso de WorkManager, que causó el proceso congelado de F06;
  - si el sistema no deja arrancar el primer plano (proceso relanzado en segundo plano, límite de 6 h, cuotas de Android 16), el trabajo sigue en tramos y recupera el primer plano al abrir la app.
- **Visibilidad:** las búsquedas piden *k* + *m* candidatos y filtran `done` e `included`. Es exacto y no cuesta nada cuando no hay documentos a medias. F10 lo hereda.
- **Control:**
  - pausa y reanudación de la tanda con un botón (D-039);
  - pausa térmica automática;
  - parada segura sin espacio;
  - aviso de modelo ausente;
  - reintento por documento.
- **Pantalla:** PNG 08 con 4 etapas (G29), «Procesando ahora», tiempo restante medido y las propuestas P1–P12 para los estados sin mockup. La cola de Ingesta muestra «Indexando».

**Investigación previa** (research):
- límites de Android 15/16 para servicios en primer plano y jobs, verificados en las fuentes oficiales (R1);
- el fallo de F06, reconstruido desde los logs, y la API de WorkManager 2.12.0 que lo evita, comprobada en el JAR (R2);
- `computeEmbeddingBatch` en LiteRT-LM 0.18.1 (R5);
- el modo *streaming* de SQLite-Vector 1.1.2 (R7);
- la simulación térmica por `adb` en los dos dispositivos (R10).

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios). Python 3.12 en `offline/` (un caso de prueba nuevo).

**Primary Dependencies**: ninguna nueva. Se usan:
- `work-runtime-ktx` 2.12.0: `setForeground`, `setGreedySchedulerEnabled` (experimental) y `WorkInfo.stopReason`;
- `litertlm-android` 0.18.1: `EmbeddingEngine`;
- `ai.sqlite:vector` 1.1.2 y `androidx.sqlite:sqlite-bundled` 2.7.1;
- `datastore-preferences` 1.2.1;
- Koin 4.2.2;
- Compose BOM 2026.09.00.

**Storage**:
- **Índice:** `noBackupFilesDir/index/rag.db`, esquema v2 sin cambios.
- **Tanda:** `noBackupFilesDir/datastore/ingest_run.preferences_pb`.
- **Trabajo:** WorkManager, que persiste por sí mismo.

**Testing**:
- **JVM:**
  - runner con un embedder determinista y `FakeRagIndex`;
  - repositorio de indexación, con `work-testing`;
  - `EmbeddingOverflow` contra casos de Python;
  - `DocumentTitle`;
  - coordinador con engines retenidos;
  - ViewModels y pantalla (Robolectric);
  - `LayeringTest`.
- **Instrumentadas** (emulador API 37, AVD API 33 y teléfono API 35):
  - visibilidad de las búsquedas sobre SQLite-Vector real;
  - flujo de la tanda con embedder determinista;
  - accesibilidad de todos los estados;
  - modo estricto.
- **Instrumentadas con modelos** (teléfono): recorte de D-036, lote frente a serie y mediciones (SC-001).

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia Nothing Phone (1), API 35. Emulador `Pixel_10`, API 37, para las cuotas de Android 16 y el límite de `dataSync`. AVD API 33.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- **SC-001:** 200 páginas (430 fragmentos) indexadas en 5 min o menos en GPU en el teléfono.
- **SC-005:** pausa efectiva en 5 s o menos.
- **SC-006:** pausa térmica en 5 s o menos.
- **Búsquedas con el filtro:** p95 con *m* = 0 como mucho 2 ms por encima de F04 (R7).

**Constraints**:
- Sin red (I).
- Embedding en `AppDispatchers.embeddings`, índice en `database` y E/S en `io` (IV).
- Nunca dos engines en GPU, y el LLM liberado durante la ingesta (IV).
- La tanda nunca publica un documento a medias (V).
- Logs y notificación sin nombres ni texto (I).
- La UI no importa WorkManager, SQLite, LiteRT ni `PowerManager` (II).

**Scale/Scope**:
- 1 pantalla nueva y cambios en la Ingesta.
- 1 repositorio nuevo y 6 casos de uso.
- El runner y el worker ampliados.
- 3 métodos nuevos de `RagIndex` y 2 búsquedas filtradas.
- 2 funciones puras (`EmbeddingOverflow`, `DocumentTitle`).
- 1 canal de notificación con un receptor.
- Sin migración de esquema.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F07 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red: el worker no tiene restricciones de red y `NetworkConfinementTest` sigue vigente. Datos en `noBackupFilesDir` (índice y DataStore). Logs con ids, recuentos y códigos (N12). La notificación no lleva nombres de archivo (R3). La acción interna de la notificación no es un deep link | ✅ |
| II. Stack y arquitectura | Screen → ViewModel → casos de uso → `IndexingRepository` → `data/indexing` y `data/ingest`. WorkManager para el trabajo largo. LiteRT solo en `rag/runtime`, y `PowerManager` solo en `data/device`, con reglas en `LayeringTest`. Sin dependencias nuevas; versiones verificadas | ✅ |
| III. RAG según el informe | Prefijo de documento, cabecera contextual «título › ruta», 256D con L2 tras truncar, lotes de 64 con `INSERT OR IGNORE` por `(document_id, ordinal)` y publicación al final (informe, Ingesta en el dispositivo). D-036 portado con el golden de Python. INT8 aplazado (D-030) | ✅ |
| IV. Rendimiento | Dispatchers dedicados. Engine de embedding retenido y el LLM liberado (R4). Fase 1 en segundos y fase 2 en serie (R5 verifica los lotes). Pausa térmica. Mediciones en el teléfono (R13). Las búsquedas filtradas no empeoran con *m* = 0 (R7) | ✅ |
| V. Veracidad del estado | N/M sale de lo guardado y del troceado real. Sin total, indeterminado. Tiempo restante solo con ritmo medido estable. Documento visible solo en `done` (N1, N7). Ninguna cifra de ejemplo fuera de las previews | ✅ |
| VI. Calidad y pruebas | Pruebas unitarias del runner, del repositorio y de las funciones puras. Instrumentadas de visibilidad, flujo, accesibilidad y modo estricto. Mediciones con modelos. Muertes del proceso en el teléfono (SC-002) | ✅ |
| VII. UI fiel y accesible | PNG 08 literal, con G29. Los estados sin mockup son las propuestas P1–P12, que se aprueban en la parada tras analyze. `Lisdata*`, tokens, `strings.xml` en español e inglés, 48 dp, TalkBack y fuente al 200 % | ✅ (P1–P12 pendientes de aprobación) |
| VIII. Proceso | Documentación en español y código en inglés. D-039 (clarify) y D-040 (plan). Parada tras analyze | ✅ |

**Re-check post-diseño:** sin violaciones.
- **`setGreedySchedulerEnabled(false)`** es una API experimental de una biblioteca ya adoptada. No es una dependencia nueva ni una excepción: se documenta en D-040 y se verifica en el dispositivo (R2).
- **El filtro de las búsquedas** cambia el comportamiento del contrato de F04 en la línea que pedía el propio informe («la exclusión obliga a filtrar o sobre-recuperar»).

## Project Structure

### Documentation (this feature)

```text
specs/007-indexing/
├── spec.md
├── plan.md                     # este fichero
├── research.md                 # R1–R14
├── data-model.md               # uso del esquema, tanda, tipos, estados, progreso y notificación
├── quickstart.md
├── contracts/
│   ├── indexing-api.md         # repositorio, casos de uso, RagIndex, engines, worker; reglas N1–N14
│   └── index-ui.md             # pantalla, etapas, propuestas P1–P12, cambios en la Ingesta; reglas U1–U6
├── checklists/requirements.md
└── tasks.md                    # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── MicroRagApplication.kt                           # setGreedySchedulerEnabled(false) (R2)
├── MainActivity.kt                                  # ACTION_OPEN_INDEXING → Index; reconcile en onStart (R12)
├── domain/indexing/
│   ├── IndexingModels.kt                            # IndexingState, RunProgress, CurrentDocument, PauseReason, FailedDocument
│   ├── IndexingRepository.kt
│   └── IndexingUseCases.kt                          # Observe, Start, Pause, Resume, Retry, Reconcile
├── domain/ingest/IngestModels.kt                    # IngestError (+2), QueueItemState (Indexing, Paused), IngestProgress (EMBEDDING)
├── domain/rag/{RagIndex, DocumentTitle}.kt          # storedChunkCounts, storedChunkContents, deleteChunks; DocumentTitle
├── rag/runtime/
│   ├── EngineCoordinator.kt                         # withHeldEngine, releaseUnheld, releaseGenerators
│   ├── EngineMemoryCallbacks.kt                     # releaseUnheld
│   ├── EmbeddingOverflow.kt                         # D-036 (R6)
│   ├── TextEmbedder.kt, litert/LiteRtTextEmbedder.kt  # embedDocumentFitting
├── data/rag/DefaultRagIndex.kt                      # búsquedas filtradas (R7), métodos nuevos, title_norm (R11)
├── data/device/ThermalStatusSource.kt               # PowerManager (R10)
├── data/indexing/
│   ├── DefaultIndexingRepository.kt
│   ├── DataStoreIngestRunStore.kt
│   ├── ChunkEmbeddingSession.kt                     # engine retenido, GPU → CPU (R4)
│   ├── IndexingNotifications.kt, IndexingActionReceiver.kt   # (R3)
├── data/ingest/
│   ├── IngestRunner.kt                              # fases 1 y 2 (R8–R10)
│   ├── IngestWorker.kt                              # primer plano dataSync (R1)
│   ├── IngestScheduler.kt                           # cancel, stopReason, claves de progreso nuevas
│   └── DefaultImportQueueRepository.kt              # estados nuevos; retry y remove con la tanda
├── data/di/DataModule.kt, domain/di/DomainModule.kt, feature/di/FeatureModule.kt
├── feature/indexing/
│   ├── IndexScreen.kt                               # IndexRoute + IndexScreen (sin estado)
│   ├── IndexViewModel.kt, IndexUiState.kt, IndexTexts.kt
│   └── IndexPreviews.kt
├── feature/ingest/{IngestTexts, IngestUiState, IngestViewModel}.kt   # estados nuevos (index-ui §Ingesta)
├── core/designsystem/component/Cards.kt             # LisdataHeroCard con título opcional
└── core/navigation/{MicroRagNavHost, PendingDestination}.kt   # Index real; P10
app/src/main/AndroidManifest.xml                     # IndexingActionReceiver (no exportado)
app/src/main/res/values{,-en}/strings_indexing.xml

app/src/test/java/com/lisdatasolutions/microrag/
├── data/ingest/IngestRunnerTest.kt                  # ampliado
├── data/indexing/{DefaultIndexingRepositoryTest, DataStoreIngestRunStoreTest, ChunkEmbeddingSessionTest}.kt
├── rag/runtime/{EmbeddingOverflowTest, EngineCoordinatorTest}.kt
├── domain/rag/DocumentTitleTest.kt
├── feature/indexing/{IndexViewModelTest, IndexScreenTest}.kt
├── feature/ingest/IngestViewModelTest.kt            # estados nuevos
└── architecture/LayeringTest.kt                     # + PowerManager solo en data/device
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/rag/{RagIndexVisibilityTest, RagIndexBenchmarkTest}.kt
├── data/indexing/{IndexingFlowTest, IndexingStrictModeTest}.kt
├── data/indexing/{EmbeddingOverflowDeviceTest, EmbeddingBatchProbeTest, IndexingBenchmarkTest}.kt   # @RequiresModels
└── feature/indexing/IndexScreenAccessibilityTest.kt

offline/
├── gen_overflow_cases.py                            # casos de EmbeddingOverflow desde Python (--check)
├── golden/embedding_overflow.jsonl
└── tests/test_overflow_cases.py
```

**Structure Decision**: módulo único (D-009).
- **`data/indexing`:** la tanda y el estado de la pantalla.
- **`data/ingest`:** el trabajo, que sigue siendo uno solo (el de F06 ampliado).
- **`rag/runtime`:** lo que toca LiteRT.
- **`domain/indexing`:** el contrato que ve la UI.

## Diseño de ejecución

1. **Iniciar.** «Iniciar indexación» (Ingesta) → `IndexingRepository.start()`:
   1. añade a la tanda los documentos `pending`, `parsed` y `embedding`;
   2. quita la pausa;
   3. encola su id (`APPEND_OR_REPLACE`, como F06);
   4. navega a `Index`.
2. **Worker:**
   1. `setForeground(dataSync)`; si falla, notificación normal (R1);
   2. comprueba que el índice está disponible;
   3. **fase 1:** troceado de los `pending` (F06);
   4. comprueba que el modelo está `Verified`; si no, pausa `model_missing`;
   5. **fase 2:** `ChunkEmbeddingSession` (libera el LLM y retiene GPU o CPU). Por documento `parsed` o `embedding`:
      1. `updateStatus(EMBEDDING)`;
      2. lee los guardados;
      3. trocea en *streaming*; compara o embebe;
      4. lotes de 64 con la comprobación de espacio y de estado;
      5. comprueba la temperatura entre fragmentos;
      6. cierre: comprueba el recuento y pasa a `DONE`.
   6. libera el engine; `success`.
3. **Pausar.**
   - **Usuario:** guarda la pausa y cancela `ingest`. El worker guarda el lote parcial y libera el engine.
   - **Temperatura:** espera dentro del worker.
   - **Sin espacio y modelo ausente:** guardan la pausa y terminan el worker.
4. **Estado.** `DefaultIndexingRepository.state` combina la tanda, el índice (recuentos y documentos con `documentChanges`), `WorkInfo` (progreso y `stopReason`) y la temperatura → `IndexingState`.
5. **Reanudar o reconciliar.** Borra la pausa y encola los pendientes de la tanda. En `onStart`, encola si no hay trabajo, o reemplaza si está esperando al sistema (R12).
6. **Visibilidad.** `vectorSearch` y `textSearch` calculan *m* y filtran (R7).

## Riesgos y verificaciones tempranas

1. **Ejecución tras matar el proceso** (R1, R2). Es lo primero que se implementa y se verifica en el teléfono y el emulador (quickstart §4):
   - el worker en primer plano;
   - `setGreedySchedulerEnabled(false)`.

   **Si el trabajo sigue congelándose o la latencia de arranque pasa de 5 s, se para y se consulta** (alternativa: job propio de JobScheduler).
2. **Excepción de entrada larga** (R6). Prueba con modelos en el teléfono antes de cerrar la regla de recorte. Si la excepción no se distingue, se recorta ante cualquier `LiteRtLmJniException`, como el escritorio.
3. **Lotes del engine** (R5). Medición temprana. **Si acelera al menos un 30 % con coseno ≥ 0,9999, se para y se consulta**, porque cambia FR-014.
4. **Coste del filtro de búsqueda** (R7). `RagIndexBenchmarkTest` en el teléfono. Si con *m* = 0 sube más de 2 ms, se revisa la consulta antes de seguir.
5. **Engine liberado al salir de la app.** `EngineMemoryCallbacks` hoy lo cerraría (R4). Se cambia y se prueba antes de la fase 2.
6. **SC-001 en GPU.** 430 fragmentos a unos 0,34 s son unos 2,5 min, con margen sobre los 5 min. Si la GPU no carga o no pasa la validación (coseno ≥ 0,99 frente a la CPU, R4) en el teléfono, se mide en CPU (unos 6,3 min) y se consulta si SC-001 debe aplicarse a la CPU.
7. **GPU del emulador.** LiteRT se bloquea en código nativo al cargar en GPU en el emulador (F04). La sesión nunca usa la GPU en un emulador (R4), así que las pruebas con modelos del emulador indexan en CPU.
8. **Propuestas P1–P12 sin aprobar.** Las tareas de UI de esos estados van después de la parada tras analyze.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
