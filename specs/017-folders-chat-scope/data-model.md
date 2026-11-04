# Data model: Carpetas y ámbito del chat (F17)

**Fecha:** 2026-11-04 · **Spec:** [spec.md](spec.md) · **Research:** [research.md](research.md)

## 1. `rag.db` v4 (R1)

| Tabla | Cambio |
|---|---|
| `folders` | **Nueva:**<br>• `id INTEGER PRIMARY KEY AUTOINCREMENT`;<br>• `name TEXT NOT NULL` (tal como lo escribió el usuario, recortado);<br>• `name_norm TEXT NOT NULL UNIQUE` (`DocumentNames.fold`);<br>• `created_at_ms` y `updated_at_ms` |
| `documents` | `folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL`, con `NULL` para «General» |
| Índices | `idx_documents_folder(folder_id, created_at_ms, id)` |
| `rag_meta.schema_version` | `4` |

**Migración 3 → 4:** `CREATE TABLE folders`, `ALTER TABLE documents ADD COLUMN folder_id`, `CREATE INDEX idx_documents_folder`, en una transacción. Todos los documentos quedan en «General».

## 2. `chat.db` v2 (R7)

| Tabla | Columnas | Significado |
|---|---|---|
| `conversation_scope` | `conversation_id` (FK `ON DELETE CASCADE`), `kind` (`folder`, `general` o `document`), `ref_id` (0 para `general`), `ref_created_at_ms` (carpetas y documentos); PK `(conversation_id, kind, ref_id)` | El ámbito de cada conversación. Sin filas, «Toda la biblioteca» |
| `last_scope` | `kind`, `ref_id`, `ref_created_at_ms`; PK `(kind, ref_id)` | El último ámbito usado (FR-020b). Sin filas, «Toda la biblioteca» |
| `chat_meta.schema_version` | `2` | |

**Migración 1 → 2:** crea las dos tablas. Las conversaciones existentes quedan con «Toda la biblioteca».

## 3. Dominio

```kotlin
/** domain/library */
data class Folder(val id: Long, val name: String, val documentCount: Int)
data class FolderList(val folders: List<Folder>, val unfiledCount: Int)

/** Which documents a Library list shows (R5). */
sealed interface LibraryFolderFilter { data object All; data object Unfiled; data class Folder(val id: Long) }

/** FR-003: the rules of a folder name; the database decides uniqueness. */
sealed interface FolderNameCheck { data class Valid(val name: String, val norm: String); data object Empty; data object TooLong; data object Reserved }
sealed interface FolderResult { data class Done(val id: Long); data object Duplicate; data object Invalid; data object Failed }

enum class FolderDeletion { MOVE_TO_UNFILED, DELETE_DOCUMENTS }

/** domain/chat */
data class DocumentRef(val id: Long, val createdAtMs: Long)
data class FolderRef(val id: Long, val createdAtMs: Long)
sealed interface ChatScope {
    data object WholeLibrary : ChatScope
    data class Some(val folders: Set<FolderRef>, val general: Boolean, val documents: Set<DocumentRef>) : ChatScope   // never all empty
}
/** What a saved scope means now (R8): what still exists, and what fell out, for the notices of K10. */
data class ResolvedScope(val scope: ChatScope, val droppedFolders: Int, val droppedDocuments: Int, val fellBackToWholeLibrary: Boolean)

/** domain/retrieval */
data class RetrievalRequest(val question: String, val previousQuestion: String? = null, val scope: SearchScope = SearchScope.All)
sealed interface SearchScope { data object All; data class Only(val folderIds: Set<Long>, val general: Boolean, val documentIds: Set<Long>) }
```

**Reglas:**
- **Nombres** (FR-003):
  - `trim()`, de 1 a 40 caracteres;
  - reservado, plegado: «general»;
  - únicos por `name_norm`.
- **`ChatScope.Some`:** nunca está vacío. Si al resolverlo no queda nada, pasa a `WholeLibrary` con `fellBackToWholeLibrary = true` (FR-021).
- **`SearchScope.Only`:** se construye en cada pregunta con los ids de las carpetas existentes y los documentos cuyo `(id, created_at_ms)` coincide (D-065). Así una carpeta incluye lo que contenga en ese momento (FR-016).
- **Excluidos y pendientes:** no se quitan del ámbito. Los quita el filtro `SEARCHABLE` de siempre.

## 4. Estado de las pantallas

```kotlin
/** feature/library */
data class FoldersUiState(val folders: FolderList? = null /* null: loading */, val failed: Boolean = false,
                          val dialog: FolderDialog? = null, val busy: Boolean = false, val message: FolderMessage? = null)
sealed interface FolderDialog { data object Create; data class Rename(val id: Long, val current: String)
                                data class Delete(val id: Long, val name: String, val documents: Int, val choice: FolderDeletion)
                                data class ConfirmDocuments(val id: Long, val documents: Int) }
// LibraryUiState (F08) gains: folder: LibraryFolderFilter, folderName: String? (top bar)

/** feature/chat */
// ChatUiState gains: scope: ScopeSummary, scopeNotice: ScopeNotice?
data class ScopeSummary(val whole: Boolean, val folderNames: List<String>, val looseDocuments: Int)
data class ScopeSelectorUiState(val folders: List<ScopeFolderRow>, val whole: Boolean, val expanded: Set<Long?>, val canApply: Boolean)
data class ScopeFolderRow(val id: Long? /* null: General */, val name: String?, val count: Int, val check: ToggleableState,
                          val documents: List<ScopeDocumentRow>?, val hasMore: Boolean)
data class ScopeDocumentRow(val ref: DocumentRef, val name: String, val checked: Boolean, val selectable: Boolean, val note: ScopeNote?)
enum class ScopeNote { NOT_INCLUDED, PENDING }
```

**Claves del `SavedStateHandle`:**
- Biblioteca: las de F08, por ruta.
- Diálogos de carpeta: `folders_dialog`, con el nombre en edición.
- Selector: `scope_selection` (carpetas y documentos marcados) y `scope_expanded`.
- Chat: `chat_pending_scope`, para un chat sin preguntas.

## 5. Correcciones del analyze (D-072)

- **«General»:**
  - el grupo de los documentos sin carpeta (`folder_id IS NULL`) se llama «General» (antes «Sin carpeta»), y el nombre está reservado;
  - en el ámbito es una fila `kind = 'general'`, que se comporta como una carpeta (H2).
- **`StoredDocument` y `DocumentDetail`** ganan `folderId: Long?` y `folderName: String?` (L6).
- **`Attempt`** (la cola de importación) gana `folderId: Long?`, que se fija al elegir los archivos (M3).
- **`RagIndex.folderDocumentRefs(folderId: Long?)`:** todos los `(id, created_at_ms)` de una carpeta, o de «General» con `null`. Lo usan el selector (M6) y el borrado (M4).
- **`ScopeFolderRow.id = null`** es «General». Una fila con todos sus documentos excluidos no se puede marcar.
