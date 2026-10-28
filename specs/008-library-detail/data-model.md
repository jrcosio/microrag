# Data model: Biblioteca y detalle de documento (F08)

## 1. Esquema v3 (R1)

Cambios sobre la v2 de F06. `schema.sql` describe la v3 completa; la migración `from = 2` lleva una v2 a la v3.

| Tabla | Cambio | Uso |
|---|---|---|
| `documents` | `name_norm TEXT` | Nombre plegado (minúsculas, sin acentos) para la búsqueda (R3). NULL solo en filas migradas hasta que se rellenan al abrir |
| `documents` | `replaces_id INTEGER REFERENCES documents(id) ON DELETE CASCADE` | Solo en las sombras de reindexado: el documento que sustituyen (R5). NULL en el resto |
| — | `idx_documents_created ON documents(created_at_ms, id)` | Orden de la Biblioteca |
| — | `idx_documents_replaces ON documents(replaces_id)` | Cascada al borrar y estado de la sombra en cada fila (añadido en T003) |

`rag_meta.schema_version` = 3. Sin cambios en `chunks`, `chunks_fts` ni los triggers.

## 2. Estados del documento

| Valor en SQL | Dominio | Significado | Entra en la cola de Ingesta | Entra en una tanda | Buscable |
|---|---|---|---|---|---|
| `pending` | `PENDING` | Importado, sin trocear | Sí | Sí | No |
| `parsed` | `PARSED` | Troceado, sin vectores | Sí | Sí | No |
| `embedding` | `EMBEDDING` | Indexándose | Sí | Sí | No |
| `done` | `DONE` | Indexado | No | No | Si `included = 1` |
| `error` | `ERROR` | Con causa (`error`) | Sí | Solo tras «Reintentar» | No |
| `cancelled` | `OUT_OF_INDEX` (antes `CANCELLED`) | «Fuera del índice» (D-043) | No | Solo tras «Reindexar» | No |
| `paused` | `PAUSED` | Sin uso (D-030) | — | — | — |

Las **sombras** (`replaces_id` no NULL) pasan por `pending → parsed → embedding` y, al cerrarse, desaparecen (R5). Nunca llegan a `done`, nunca aparecen en la Biblioteca ni en la cola de Ingesta, y sí en el Estado de indexación.

### Transiciones nuevas

```text
done ──Quitar del índice──▶ cancelled ──Reindexar──▶ pending ──(F07)──▶ … ──▶ done
done ──Reindexar──▶ done + sombra(pending) ──(F07)──▶ sombra cerrada ──publishReindex──▶ done (fragmentos nuevos)
                                           └──fallo──▶ sombra(error) ──Reintentar──▶ sombra(pending)
                                                                     └──Descartar──▶ (sombra borrada; done intacto)
cualquiera ──Eliminar──▶ (fila, sombra, fragmentos, vectores, FTS y copia privada borrados)
error (reintentable) ──Reintentar o Reindexar──▶ pending
```

## 3. Tipos de dominio (`domain/library`)

```kotlin
enum class LibraryFilter { ALL, INDEXED, PENDING }

/** Lo que pide la Biblioteca; se guarda en el SavedStateHandle (R9). */
data class LibraryQuery(val text: String = "", val filter: LibraryFilter = LibraryFilter.ALL, val limit: Int = PAGE)

data class LibraryPage(val documents: List<LibraryDocument>, val total: Int) {
    val hasMore: Boolean get() = documents.size < total
}

data class LibraryDocument(
    val id: Long,
    val displayName: String,
    val format: DocumentFormat?,          // PDF, TXT, MD (de mime_type)
    val state: LibraryDocumentState,
    val included: Boolean,
)

enum class ReindexRow { RUNNING, FAILED }          // sombra en curso o en error

sealed interface LibraryDocumentState {
    data class Indexed(val chunks: Int, val reindex: ReindexRow?) : LibraryDocumentState
    data object Pending : LibraryDocumentState
    data object Indexing : LibraryDocumentState
    data class Failed(val error: IngestError) : LibraryDocumentState
    data object OutOfIndex : LibraryDocumentState
}

data class DocumentDetail(
    val id: Long,
    val displayName: String,
    val format: DocumentFormat?,
    val state: DocumentDetailState,
    val included: Boolean,
    val pages: Int?,                      // null en TXT y MD → «—»
    val chunks: Int?,                     // null si no está indexado
    val sizeBytes: Long?,
    val importedAtMs: Long,
    val canReindex: Boolean,
    val copyMissing: Boolean,             // la copia privada no está: solo «Eliminar» (D10)
    val reindex: ReindexState?,           // null sin sombra
)

sealed interface DocumentDetailState {
    data object Indexed : DocumentDetailState
    data object Pending : DocumentDetailState
    data class Indexing(val done: Int, val total: Int?) : DocumentDetailState
    data class Failed(val error: IngestError) : DocumentDetailState
    data object OutOfIndex : DocumentDetailState
}

/** [shadowId] es lo que reintenta el «Reintentar» de F07 (D6). */
sealed interface ReindexState {
    val shadowId: Long
    data class Running(override val shadowId: Long, val done: Int, val total: Int?, val pause: PauseReason?) : ReindexState  // D5
    data class Failed(override val shadowId: Long, val error: IngestError) : ReindexState
}

/** La vista previa (R7). [highlighted] es true solo para el fragmento de una cita. */
data class DocumentPreview(
    val page: Int?,
    val before: String?,                  // última frase del fragmento anterior (solo con cita)
    val text: String,
    val after: String?,                   // primera frase del siguiente (solo con cita)
    val highlighted: Boolean,
    val citedChunkMissing: Boolean,       // la cita apuntaba a un fragmento que ya no existe
)
```

`PAGE` = 50 (R2). `DocumentFormat` es el de F06.

## 4. Operaciones nuevas del índice (`RagIndex`)

| Operación | Efecto | Transacción |
|---|---|---|
| `libraryPage(text, statuses, limit)` | `LibraryEntry`: documentos sin sombras, con un estado de `statuses` y el nombre buscado (R2, R3), del más reciente al más antiguo, con el estado de su sombra | Lectura |
| `libraryCount(text, statuses)` | Total con los mismos filtros | Lectura |
| `addReindexShadow(originalId)` | Inserta la sombra (R5). Falla si ya hay una o el original no es `done` | Sí |
| `shadowOf(originalId)` | La sombra viva, si existe | Lectura |
| `publishReindex(shadowId)` | Sustitución atómica (R5). Devuelve los fragmentos publicados, o `null` sin cambiar nada si la sombra o su original ya no existen | Sí |
| `removeFromIndex(id)` | Borra sombras y fragmentos, `status = cancelled` y `quant_dirty` (R6). Devuelve los fragmentos borrados, o `null` si no es un documento `done` | Sí |
| `deleteDocument(id)` | Ya existía; ahora marca también `quant_dirty` (R6) | Sí |
| `addDocument(…)` | Ya existía; ahora guarda `name_norm` | Sí |

El repositorio traduce el filtro a estados: ALL, todos; INDEXED, `done`; PENDING, el resto. Así `domain/rag` no depende de `domain/library`.

`StoredDocument` añade `replacesId: Long?`. `documents(statuses)` sigue devolviendo también las sombras: F07 las necesita en la tanda. La cola de Ingesta las filtra.

## 5. Tanda (F07)

Sin cambios de formato. F08 añade o quita ids:
- `reindex` añade la sombra o el documento y quita la pausa, como `retry`, con la operación nueva `IndexingRepository.add(id)`: `start()` añadiría también los pendientes que el usuario aún no mandó indexar;
- `delete` y `removeFromIndex` quitan el documento y su sombra.

## 6. Logs (constitución I)

Solo ids, recuentos, duraciones y códigos:
- `library reindex id=… shadow=…`;
- `rag reindex published id=… chunks=… in … ms`;
- `library delete id=… chunks=… in … ms`;
- `library out-of-index id=… chunks=… in … ms`;
- `library include id=… included=…`.
