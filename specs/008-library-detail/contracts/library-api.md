# Contrato: datos y casos de uso de la Biblioteca y el detalle (F08)

Capas (constitución II): `feature/library` y `feature/document` → casos de uso (`domain/library`) → `LibraryRepository` → `data/library` → `RagIndex` (F04), tanda e `IngestScheduler` (F07). La UI no importa SQLite, WorkManager ni `java.io.File`.

## LibraryRepository (`domain/library`)

```kotlin
interface LibraryRepository {
    /** Página de la Biblioteca; se recalcula con documentChanges y con cada consulta (R2). */
    fun page(query: LibraryQuery): Flow<LibraryResult>

    /** Detalle; `NotFound` cuando el documento no existe o desaparece (R8). */
    fun detail(documentId: Long): Flow<DetailResult>

    /** Vista previa del primer fragmento o de una cita (R7); null si el documento no está indexado. */
    suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview?

    suspend fun setIncluded(documentId: Long, included: Boolean)
    suspend fun delete(documentId: Long)
    suspend fun removeFromIndex(documentId: Long)
    suspend fun reindex(documentId: Long)
    suspend fun discardReindex(documentId: Long)
}

sealed interface LibraryResult {
    data class Loaded(val page: LibraryPage) : LibraryResult
    data object Unavailable : LibraryResult      // índice incompatible o ilegible (F04)
}

sealed interface DetailResult {
    data class Loaded(val detail: DocumentDetail) : DetailResult
    data object NotFound : DetailResult
    data object Unavailable : DetailResult
}
```

**Casos de uso** (uno por acción, como F07): `ObserveLibraryUseCase`, `ObserveDocumentUseCase`, `LoadPreviewUseCase`, `SetIncludedUseCase`, `DeleteDocumentUseCase`, `RemoveFromIndexUseCase`, `ReindexDocumentUseCase` y `DiscardReindexUseCase`.

`DeleteDocumentUseCase` sustituye la lógica de `ImportQueueRepository.remove(QueueKey.Document)` de F06: la Ingesta pasa a llamarlo, para que haya un solo camino de borrado.

## Reglas

- **L1 — Sin fragmentos en la lista:** `page` nunca lee la tabla `chunks`. Los indicadores «Reindexando» y «Reindexado fallido» salen de dos `EXISTS` sobre `documents` (sombra en curso o sombra en `error`).
- **L2 — Sin sombras visibles:** la Biblioteca, el recuento y la cola de Ingesta excluyen `replaces_id IS NOT NULL`. El Estado de indexación sí muestra la sombra, con el nombre del original.
- **L3 — Inclusión:** `setIncluded` solo escribe si el documento es `done`; en otro estado no hace nada. Las búsquedas de F07 la respetan en la siguiente llamada.
- **L4 — Eliminar:**
  1. `RagIndex.deleteDocument(id)` en una transacción: cascada a la sombra, los fragmentos, los vectores y el FTS; `quant_dirty`;
  2. quita de la tanda el id y el de su sombra;
  3. borra la copia privada bajo el `fileLock` de F06, salvo si es una sombra, que comparte la copia del original.

  Si el paso 3 falla o el proceso muere, la limpieza de huérfanos la borra después (R6).
- **L5 — Quitar del índice:** `RagIndex.removeFromIndex(id)` en una transacción (R6) y después quitar el id y el de la sombra de la tanda. Solo desde `done`.
- **L6 — Reindexar:**
  - si la copia privada ya no existe, no hace nada: el detalle lo muestra como D10 y solo ofrece «Eliminar» (spec, casos límite);
  - `done` sin sombra: `addReindexShadow`, añadir la sombra a la tanda, quitar la pausa y encolar (`IndexingRepository`);
  - `done` con sombra: no hace nada;
  - `error` reintentable: `IndexingRepository.retry`;
  - `cancelled`: `pending`, añadir a la tanda, quitar la pausa y encolar;
  - cualquier otro estado: no hace nada.
- **L7 — Publicar la sombra:** el runner, al cerrar un documento con `replacesId`, llama a `publishReindex(shadowId)` en lugar de `updateStatus(DONE)`. Si el original ya no existe, la cascada ya borró la sombra y el runner la trata como desaparecida (`DocumentGone`).
- **L8 — Descartar un reindexado fallido:** `discardReindex(id)` borra la sombra (cascada a sus fragmentos), sin tocar la copia ni el original, y la quita de la tanda.
- **L9 — Cita:** `preview(id, chunkId)` comprueba que el fragmento es de ese documento. Si no lo es o no existe, devuelve la vista previa normal con `citedChunkMissing = true`.
- **L10 — Fuera del hilo principal:** todo en `AppDispatchers.database` (índice) e `io` (archivos). `LibraryStrictModeTest` lo comprueba con control negativo, como F06 y F07.
- **L11 — Logs:** solo ids, recuentos, duraciones y códigos (data-model §6). `logsCarryNoNamesOrText` en las pruebas del repositorio.
- **L12 — Biblioteca no disponible:** con el índice incompatible, `page` emite `Unavailable`, `detail` emite `DetailResult.Unavailable` (distinto de «no existe») y las acciones que escriben no hacen nada (FR-023).

## Cambios en F06 y F07

| Pieza | Cambio |
|---|---|
| `IngestRunner.close` | L7: `publishReindex` para las sombras |
| `DefaultImportQueueRepository` | La cola excluye las sombras (L2); `remove` de un documento usa `DeleteDocumentUseCase` (L4) |
| Limpieza de huérfanos | También la primera vez que se observa la Biblioteca (R6) |
| `DocumentStatus` | `CANCELLED` pasa a `OUT_OF_INDEX("cancelled")` (R4) |
| `RagIndexConfig.SCHEMA_VERSION` | 3, con la migración `from = 2` y el relleno de `name_norm` (R1) |
| `offline/` | `test_appconfig` espera la v3; README: reconstruir los índices de evaluación |
