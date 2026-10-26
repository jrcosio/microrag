# Contrato — API del índice RAG

Interfaz de dominio que usarán F06/F07 (ingesta), F10 (recuperación) y F11 (citas y vecinos). Los nombres y la semántica son vinculantes; las firmas, orientativas.

```kotlin
// domain/rag
interface RagIndex {
    /** Estado observable; `Closed` hasta la primera operación. */
    val compatibility: StateFlow<IndexCompatibility>

    /** Abre (o crea) el índice y lo valida. Idempotente. */
    suspend fun open(): IndexCompatibility

    suspend fun meta(): Map<String, String>

    // Documentos
    suspend fun addDocument(document: NewDocument): Long            // source = 'user', status = PENDING; A11
    suspend fun document(id: Long): StoredDocument?
    /** `error` se escribe siempre (null lo borra); `chunkCount` solo si no es null. */
    suspend fun updateStatus(id: Long, status: DocumentStatus, error: String? = null, chunkCount: Int? = null)
    suspend fun setIncluded(id: Long, included: Boolean)
    suspend fun deleteDocument(id: Long)                              // cascada: fragmentos + FTS

    // Fragmentos
    /** Inserta el lote en una transacción; los (documento, ordinal) existentes se ignoran. Devuelve los insertados. */
    suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int

    // Lectura
    suspend fun vectorSearch(query: FloatArray, k: Int = 50): List<ScoredChunk>
    /** Normaliza [userQuery] con SpanishText.ftsQuery; sin términos → lista vacía. */
    suspend fun textSearch(userQuery: String, k: Int = 50): List<Long>
    suspend fun loadChunks(ids: Collection<Long>): Map<Long, ChunkView>
    suspend fun neighbor(documentId: Long, ordinal: Int): ChunkView?
    suspend fun counts(): IndexCounts

    /** Cierra la conexión y vuelve a `Closed`; la siguiente operación reabre. */
    suspend fun close()
}

class IndexUnavailableException(val state: IndexCompatibility) : IllegalStateException()
class DuplicateDocumentException(val existingId: Long) : IllegalStateException()
```

## Reglas

| # | Regla | Verificación |
|---|---|---|
| A1 | Todas las operaciones se ejecutan en `AppDispatchers.database`, con una sola conexión que se abre de forma perezosa | Prueba de modo estricto (SC-007) |
| A2 | Con un estado distinto de `Compatible`, cualquier operación salvo `compatibility`, `open` y `close` lanza `IndexUnavailableException` **sin tocar la base** | `IndexCompatibilityTest` (instrumentada) |
| A3 | `insertChunks` valida antes de abrir la transacción: todas las dimensiones 256, norma L2 unitaria (\|‖v‖ − 1\| ≤ 1e-3, porque `vector_init` declara `normalized=1`), el documento existe y ordinales distintos dentro del lote. Un lote inválido se rechaza entero con `IllegalArgumentException` | Instrumentada |
| A4 | `insertChunks` usa `INSERT OR IGNORE` dentro de `BEGIN IMMEDIATE … COMMIT`, con `quant_dirty = 1` en la misma transacción. Con cualquier error, `ROLLBACK` | Instrumentada (incluido un fallo provocado a mitad) |
| A5 | `vectorSearch`: `SELECT rowid, distance FROM vector_full_scan('chunks','embedding', ?, ?)` con BLOB float32 LE. Se rechaza con `IllegalArgumentException` un vector que no tenga 256 componentes o que no sea unitario (como en A3) | Instrumentada |
| A6 | `textSearch`: `SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts, 3.0, 1.0) LIMIT ?` con `SpanishText.ftsQuery` | Instrumentada (peso del título y escapado) |
| A7 | `loadChunks` une `chunks` con `documents` para obtener `documentName`. Los ids inexistentes no aparecen en el mapa | Instrumentada |
| A8 | `deleteDocument` borra en cascada. Los triggers `chunks_ad` limpian el FTS. `counts().ftsRows` cuenta las filas de `chunks_fts_docsize`, que reflejan el índice real: en una tabla FTS5 de contenido externo, `count(*)` sobre `chunks_fts` lee `chunks` y no prueba nada. Tras borrar, `ftsRows` coincide con `chunks` y `integrity-check` (con `rank = 1`) no falla | Instrumentada |
| A9 | La apertura aplica, en orden, los pragmas, el esquema y `vector_init`, en cada conexión. Si `vector_init` o la carga de la extensión fallan, el estado es `Unreadable(motivo)` | Instrumentada (extensión ausente simulada con una ruta errónea) |
| A10 | Ninguna operación registra textos de documentos, consultas ni nombres de documento en los logs (constitución I). Solo se registran ids, recuentos y tiempos | Revisión de código y prueba sobre el `AppLogger` |
| A11 | `addDocument` con un `content_hash` que ya existe lanza `DuplicateDocumentException(existingId)` y no modifica nada. Qué hacer con el duplicado lo decide F06 | Instrumentada |

## Configuración e inyección

- **Fábrica:** `DefaultRagIndex(context, dirs.index/"rag.db", schema = { RagSchema.load(assets) } (se lee dentro de la apertura, en el dispatcher de base de datos), extensionPath = nativeLibraryDir + "/vector.so", config = RagIndexConfig.current(), dispatcher = AppDispatchers.database, logger)`, registrada como `single<RagIndex>` en `dataModule`.
- **Rutas de prueba:** las pruebas crean instancias con un fichero temporal en `cacheDir` y no tocan el índice real.
