# Contrato: índice INT8 (US2, R3)

## I1. `RagIndex` (dominio)

```kotlin
interface RagIndex {
    // … lo de hoy …

    /** F14 (R3): the INT8 copy of the chunk vectors, as `rag_meta` and this connection see it. */
    suspend fun quantizedState(): QuantizedState

    /**
     * Rebuilds the INT8 copy when it is stale and there are at least [minChunks] searchable chunks, then preloads it.
     * Clears the preloaded mark first, so a failed preload never leaves an older snapshot in use.
     * Preloads only, when it is current. Never throws for a failed rebuild: the index stays stale and the search
     * stays exact (constitution V). Returns the state after the attempt.
     */
    suspend fun refreshQuantized(minChunks: Int): QuantizedState
}

data class QuantizedState(val current: Boolean, val preloaded: Boolean, val searchableChunks: Int) {
    val usable: Boolean get() = current && preloaded
}
```

- `vectorSearch` y `pageSearch` mantienen su firma. La ruta (INT8 o exacta) es interna.

## I2. SQL (`DefaultRagIndex`, `RagConnection`)

| Uso | SQL |
|---|---|
| Reconstruir | `SELECT vector_quantize('chunks','embedding','qtype=INT8,max_memory=64MB')` y después `UPDATE rag_meta SET value='0' WHERE key='quant_dirty'`, en la misma transacción que lee el recuento |
| Precargar | `SELECT vector_quantize_preload('chunks','embedding')` |
| Top sin ámbito | `WITH v AS MATERIALIZED (SELECT rowid, distance FROM vector_quantize_scan('chunks','embedding',?,?)) …`, con el mismo JOIN y filtro `SEARCHABLE` que `VECTOR_SEARCH_SQL`, y `LIMIT INT8_CANDIDATES + m` |
| Con ámbito | streaming `vector_quantize_scan('chunks','embedding',?)` con el filtro de `scopeCondition` y `ORDER BY distance LIMIT INT8_CANDIDATES` |
| Reordenar | `SELECT id, embedding FROM chunks WHERE id IN (…)`, y en Kotlin `1 − dot(q, v)` sobre los blobs float32 (normalizados) |

- **Resultado:** los primeros k por la distancia exacta reordenada. El `VectorHit` lleva la distancia exacta, así que la RRF y el diagnóstico no cambian.
- **Desempate:** por distancia exacta y después por `id`, como hoy.

## I3. Desfasar (todas en la transacción que cambia los datos)

| Operación | Hoy | F14 |
|---|---|---|
| `insertChunks`, `deleteDocument`, `publishReindex`, `removeFromIndex`, `deleteChunks` | `markQuantDirty` | igual, y además `int8Preloaded = false` |
| `insertPage`, borrado de páginas | — | `markPagesQuantDirty`, solo si las páginas usan INT8 |
| `setIncluded`, `setIndexMode` | — | no cambian vectores: el filtro `SEARCHABLE` se aplica después del scan |

## I4. `QuantizedIndexMaintainer` (`data/rag`)

```kotlin
class QuantizedIndexMaintainer(
    private val index: RagIndex,
    private val ingest: IngestActivity,        // ¿hay una tanda activa? (interfaz sobre el estado de IngestRunner)
    private val scope: ApplicationScope,
    private val clock: …,                      // para la espera de coalescencia en las pruebas
) {
    /** Asks for a refresh; several requests within [RagParameters.QUANT_REBUILD_DEBOUNCE_MS] run once. */
    fun request(reason: Reason)
    enum class Reason { STARTUP, INGEST_FINISHED, LIBRARY_CHANGED }
}
```

- **Llamadores:**
  - `MicroRagApplication.onCreate` (`STARTUP`);
  - `IngestRunner.finishRun` (`INGEST_FINISHED`);
  - `DefaultLibraryRepository` en `delete`, `removeFromIndex`, `reindex` y `discardReindex` (`LIBRARY_CHANGED`). «Quitar de la cola» un documento ya pasa por `DeleteDocumentUseCase` → `delete`, así que no necesita disparador propio.
- **Con una tanda activa:** no hace nada. `INGEST_FINISHED` llega al final.
- **Umbral:** por debajo de `INT8_MIN_CHUNKS` fragmentos buscables no reconstruye. Así, en las pruebas instrumentadas sobre el teléfono (que arrancan la `Application`), la biblioteca del usuario no cambia (SC-010).
- **Espera de las búsquedas:** la reconstrucción y la precarga ocupan el hilo único de la base; su duración se registra en el log (solo números) y la miden los bancos (FR-009, FR-011).
- **Errores:** registra el tipo de error en el log (sin contenido) y no relanza.
- **Koin:** `single` en `DataModule`.

## I5. Pruebas

- **JVM, `QuantizedIndexMaintainerTest`:**
  - coalescencia;
  - sin trabajo con una tanda activa;
  - el arranque precarga;
  - un fallo no se propaga.
- **JVM, `DefaultRagIndex` con fakes:** elige INT8 solo con el índice al día y precargado.
- **Instrumentada, `RagIndexInt8Test`** (SQLite-Vector real, sin modelos; emuladores y teléfono):
  1. Cuantizar y buscar: el top-k coincide con el exacto en un banco pequeño con vectores conocidos.
  2. Cada operación de I3 deja `quant_dirty = 1`, y la búsqueda siguiente es exacta.
  3. Una fila nueva idéntica a la consulta aparece en la búsqueda tras la operación (nunca se usa el INT8 viejo).
  4. Reabrir la conexión: sin precargar, búsqueda exacta; tras `refreshQuantized`, INT8.
  5. Fallo simulado (por ejemplo, `max_memory` demasiado bajo): `quant_dirty` sigue a 1, la búsqueda es exacta y no hay excepción.
  6. Ámbito y exclusiones: el mismo resultado con INT8 que con la exacta.
  7. `tableNames()` y la detección de ficheros vacíos no cambian con las tablas `vector0_*`.
  8. Reconstrucción correcta seguida de una precarga que falla: `int8Preloaded` sigue a falso y la búsqueda es exacta (nunca usa una precarga anterior).
  9. Bajo `INT8_MIN_CHUNKS`: `refreshQuantized` no crea `vector0_*` ni cambia `quant_dirty`.
- **Banco (`SearchScaleBenchmarkTest`):** el Recall@20 con y sin reordenación frente a F32, los tiempos de cuantizar y precargar, y la memoria.
