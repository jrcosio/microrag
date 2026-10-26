# Contrato — Indexación: tanda, trabajo, índice y engines

Capas (constitución II):
- `feature/indexing` usa los casos de uso de `domain/indexing`;
- la implementación vive en `data/indexing` y amplía `data/ingest` (F06);
- las funciones puras viven en `rag/runtime` y `domain/rag`.

Los tipos están en data-model §3.

## Dominio (`domain/indexing`)

```kotlin
interface IndexingRepository {
    /** The Index screen state (data-model §3). Hot while collected; recomputed on index, work and thermal changes. */
    val state: Flow<IndexingState>

    /** «Iniciar indexación»: adds every non-indexed document (pending, parsed, embedding) to the run, resumes it and enqueues the work. Returns how many were added. */
    suspend fun start(): Int

    suspend fun pause()

    /** Clears a user, no-space or model-missing pause and enqueues the run's unfinished documents. */
    suspend fun resume()

    /** A retryable failed document goes back to pending, joins the run and is enqueued. */
    suspend fun retry(documentId: Long)

    /** On app start (MainActivity.onStart): re-enqueues a run left without work (research R12). */
    suspend fun reconcile()
}
```

**Casos de uso:** `ObserveIndexingUseCase`, `StartIndexingUseCase`, `PauseIndexingUseCase`, `ResumeIndexingUseCase`, `RetryIndexingUseCase` y `ReconcileIndexingUseCase`.

**Relación con F06:**
- **Iniciar:** el `StartIndexingUseCase` de F06 pasa a delegar en `IndexingRepository.start()`.
- **Reintentar:** `ImportQueueRepository.retry` de un documento delega en `IndexingRepository.retry`.
- **Quitar:** `ImportQueueRepository.remove` de un documento lo borra también de la tanda.

## Ampliación de `RagIndex` (contrato de F04)

```kotlin
/** Stored chunks per document, for [ids] (missing ids count 0). */
suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int>

/** Ordinal → content of the stored chunks of [documentId], in ordinal order (resume check, research R9). */
suspend fun storedChunkContents(documentId: Long): Map<Int, String>

/** Deletes the chunks of [documentId] and their FTS rows, keeping the document (research R9). */
suspend fun deleteChunks(documentId: Long)
```

**Cambios de comportamiento:**
- `vectorSearch` y `textSearch` devuelven solo fragmentos de documentos `done` e `included` (R7, regla N7).
- `insertChunks` calcula `title_norm` con `DocumentTitle.of(display_name)` (R11).

## Engines (`rag/runtime`, contrato de F02)

```kotlin
// EngineCoordinator
/** Like withEngine, but the engine stays held until [block] ends: memory callbacks do not release it. */
suspend fun <T : ManagedEngine, R> withHeldEngine(key: EngineKey, factory: suspend () -> T, block: suspend (T) -> R): R

/** Releases every engine that is not held (EngineMemoryCallbacks on UI_HIDDEN and low memory). */
suspend fun releaseUnheld()

/** Releases the generation engines (the LLM) before the ingest loads its embedder. */
suspend fun releaseGenerators()

// TextEmbedder
/** Embeds a chunk with DOCUMENT prefix and [header], applying the D-036 cuts (research R6). */
suspend fun embedDocumentFitting(content: String, header: String): DocumentEmbedding
```

## Datos

| Clase | Responsabilidad |
|---|---|
| `DefaultIndexingRepository` (`data/indexing`) | Combina `IngestRunStore`, `RagIndex` (documentos, recuentos y `documentChanges`), `WorkInfo` de `ingest` y `ThermalStatusSource` en `IndexingState`. Ejecuta `start`, `pause`, `resume`, `retry` y `reconcile` |
| `DataStoreIngestRunStore` (`data/indexing`) | La tanda (data-model §2) |
| `ThermalStatusSource` (`data/device`) | `PowerManager.currentThermalStatus` y su listener como `Flow<ThermalLevel>` |
| `IngestRunner` (`data/ingest`, ampliado) | Fase 1 (troceado de los `pending`, como F06) y fase 2 (embeddings y escritura por lotes, R8–R10) |
| `ChunkEmbeddingSession` (`data/indexing`) | Abre el engine retenido (GPU, si no CPU), libera el LLM, embebe con la cabecera y lo cierra |
| `IngestWorker` (`data/ingest`, ampliado) | `setForeground` de tipo `dataSync`, notificación y delegación en el runner (R1, R3) |
| `IndexingNotifications`, `IndexingActionReceiver` (`data/indexing`) | Canal, notificación y acción «Pausar» (R3) |
| `WorkManagerIngestScheduler` (`data/ingest`, ampliado) | `cancel()` del trabajo único y lectura de `stopReason` |

## Trabajo en segundo plano (cambios sobre F06)

| Elemento | Valor |
|---|---|
| Configuración de WorkManager | `setGreedySchedulerEnabled(false)` (R2) |
| Primer plano | `setForeground(ForegroundInfo(id, notificación, FOREGROUND_SERVICE_TYPE_DATA_SYNC))` al empezar y en cada lote. Si falla, sigue como job normal (R1) |
| Progreso | Las claves de data-model §5, como mucho cada 250 ms |
| Resultado | `success` cuando termina la tanda o se pausa de forma persistente. `failure` si el índice no está disponible, como en F06: la pantalla lo explica y el usuario vuelve a empezar. Las paradas del sistema las reprograma WorkManager |
| Restricciones | Ninguna (como F06) |

## Reglas

| # | Regla |
|---|---|
| N1 | Un documento pasa a `done` solo si tiene exactamente `chunk_count` fragmentos guardados, con ordinales 0..n-1 |
| N2 | Cada fragmento se guarda una sola vez. Al reanudar, se saltan los guardados cuyo contenido coincide con el troceado actual. Si alguno no coincide, se borran los del documento y se empieza desde 0 (R9) |
| N3 | Los lotes son de 64 fragmentos por transacción (FR-007). Antes de cada lote se comprueba el espacio libre (≥ 64 MiB) y que el documento sigue en `embedding` |
| N4 | Al parar por cualquier causa, el lote parcial ya embebido se guarda antes de salir, con un tope de 5 s (R9) |
| N5 | El texto embebido es `DOCUMENT + DocumentTitle › heading_path + "\n" + content`, con la regla de `EmbeddingPrefixes.document`. El texto guardado es `content` |
| N6 | Durante la fase 2, el único engine cargado por la ingesta es el de embedding, retenido. Antes de cargarlo se liberan los de generación. Se libera al terminar, al pausar y al esperar por temperatura (FR-012) |
| N7 | Las búsquedas filtran `status = 'done' AND included = 1`, pidiendo *k* + *m* candidatos (R7). Un documento que no está en `done` nunca aparece |
| N8 | La pausa del usuario se guarda antes de cancelar el trabajo. Mientras la tanda esté pausada, ni `reconcile` ni `start` de otra pantalla programan trabajo, salvo que el usuario reanude |
| N9 | Con `THERMAL_STATUS_SEVERE` o superior, el runner espera dentro del worker con el engine liberado. Continúa al bajar a `MODERATE` o menos (D-039) |
| N10 | Si el modelo de embeddings no está `Verified`, la tanda se pausa con `model_missing` antes de cargar nada |
| N11 | Los errores son por documento. Un fallo nunca detiene los demás documentos de la tanda |
| N12 | Los logs llevan solo ids, recuentos, tiempos, backend y códigos. Nunca nombres, rutas ni texto (constitución I). La notificación no lleva nombres de archivo |
| N13 | El embedding va en `AppDispatchers.embeddings`, el índice en `AppDispatchers.database` y la E/S en `AppDispatchers.io`. Nada en el hilo principal (SC-009) |
| N14 | `reconcile` solo se llama con la app visible. Encola la tanda si no hay trabajo sin terminar, o la reemplaza si está esperando al sistema; nunca toca un trabajo `RUNNING` (R12) |
