# Contrato: carpetas, ámbito y búsqueda acotada (F17)

Capas (constitución II): `feature/library`, `feature/document`, `feature/ingest`, `feature/folders` y `feature/chat` → casos de uso (`domain/library`, `domain/chat`, `domain/retrieval`) → `RagIndex`, `LibraryRepository`, `ChatRepository` y `ChatSession`. Tipos en data-model §3.

## 1. Índice (`RagIndex`, `DefaultRagIndex`)

| Método | Regla |
|---|---|
| `folders(): FolderList` | **F1:** agregado `LEFT JOIN … GROUP BY`, ordenado por `name_norm`. Cuenta solo `replaces_id IS NULL` |
| `createFolder(name, norm): Long?` | **F2:** `INSERT`; `null` si viola `UNIQUE(name_norm)` |
| `renameFolder(id, name, norm): Boolean?` | **F2:** `UPDATE`; `null` si el nombre ya existe; `false` si la carpeta no existe |
| `deleteFolder(id, moveDocuments): Boolean` | **F3:** con `moveDocuments`, `UPDATE documents SET folder_id = NULL` y `DELETE` en una transacción. Sin él, solo `DELETE` si ya no le quedan documentos. Devuelve si la borró |
| `moveDocument(documentId, folderId?)` | **F4:** solo `folder_id` y `updated_at_ms` |
| `folderDocumentRefs(folderId?)` | **F3:** todos los `(id, created_at_ms)` de una carpeta, o de «General» con `null`. Sirve para borrar documento a documento, para el recuento final y para convertir una carpeta en sueltos en el selector (D-072) |
| `libraryPage(text, statuses, limit, folder)` / `libraryCount(…)` | **F5:** el filtro de F08 más el de la carpeta. `LibraryEntry.folderName` sale de un `LEFT JOIN folders` |
| `addDocument(NewDocument(folderId))` | **F6:** el documento se registra en su carpeta |
| `vectorSearch(query, k, scope)` / `textSearch(…, scope)` | **F7:**<br>• `SearchScope.All`: el SQL de hoy, sin cambiar ni una letra (SC-003);<br>• `Only`: el modo en streaming dentro de una CTE `MATERIALIZED`, con `JOIN`/`WHERE` (carpetas, `general` → `folder_id IS NULL`, y documentos) y `ORDER BY … LIMIT k` (R3). El escaneo se hace una vez (`EXPLAIN QUERY PLAN`). Las consultas con `folders` usan alias (L6) |
| `loadChunks(ids, searchableOnly, scope)` / `neighbor(…, scope)` | **F7:** el mismo filtro |
| `chatLibraryState(scope)` | **F8:** los recuentos de F11, dentro del ámbito |

## 2. Casos de uso

| Caso de uso | Qué hace |
|---|---|
| `ObserveFoldersUseCase` | `Flow<FolderList>`, que se relee con cada cambio del índice |
| `CreateFolderUseCase(name)` | `FolderNames.check`, después `createFolder` → `FolderResult` |
| `RenameFolderUseCase(id, name)` | Igual, con `renameFolder` |
| `DeleteFolderUseCase(id, FolderDeletion)` | **F3**, R4. Con `DELETE_DOCUMENTS`, `LibraryRepository.delete` por cada documento y después `deleteFolder(id, moveDocuments = false)`. Devuelve los que fallaron |
| `MoveDocumentUseCase(documentId, folderId?)` | **F4** |
| `ResolveScopeUseCase(ChatScope)` | **S1:** carpetas existentes y documentos con `(id, created_at)` iguales. Si no queda nada, `WholeLibrary`. Devuelve `ResolvedScope` |
| `ObserveLastScopeUseCase` / `SaveLastScopeUseCase` | **S3** |
| `RetrieveContextUseCase(question, previous, scope)` | Convierte `ResolvedScope` en `SearchScope` (S2) |
| `ObserveChatAvailabilityUseCase(scope)` | **F8** y `ChatAvailability.NothingInScope` (K9) |

## 3. Sesión del chat y `chat.db`

- **S1. Resolver:** al abrir una conversación, al empezar «Nuevo chat» con el último ámbito, y justo antes de cada pregunta. Los documentos se comparan por `(id, created_at_ms)` (D-065).
- **S2. Pregunta y reintento:** `ChatSession.ask(conversationId, question, scope)`. `retry(turnId)` usa el ámbito resuelto de su conversación (D-072).
  - Guarda el ámbito en `conversation_scope`, de forma atómica con la creación de la conversación si es nueva.
  - Guarda `last_scope`.
  - Recupera con el `SearchScope` de ese momento, y una respuesta en curso no cambia de ámbito (FR-019).
- **S2b. Disponibilidad:** la puerta de `ask`/`retry` y la `partial_coverage` del turno se calculan dentro del ámbito. La precedencia es la de R8: primero los estados globales y después `NothingReady`/`NothingInScope`.
- **S2c. Ámbito ajustado:** si al resolver se cae algo, el ámbito resuelto se reescribe en `conversation_scope` (y en `last_scope` si era el último), para que el aviso salga una vez.
- **S3. Aplicar con el selector:** si la conversación existe, `ChatSession.setScope(conversationId, scope)` y `last_scope`. Si el chat aún no tiene preguntas, solo `last_scope` y el `SavedStateHandle`.
- **S4. Conversaciones antiguas:** sin filas, «Toda la biblioteca». Borrar una conversación borra su ámbito por cascada, y «Borrar historial» borra todos los ámbitos y también `last_scope`. `setScope` no cambia `updated_at`, así que el orden de Historial no se mueve.
- **S5. Registros:** solo ids, recuentos y tiempos (R12).

## 4. Inyección (Koin)

- **`domainModule`:** los casos de uso nuevos con `factoryOf`.
- **`featureModule`:** `FoldersViewModel`, `ScopeSelectorViewModel`, y los constructores ampliados de `LibraryViewModel`, `DocumentViewModel`, `IngestViewModel` y `ChatViewModel`.
- **Pruebas:** `AppModulesTest` comprueba el grafo, y `NavigationTest` construye los ViewModels con fakes.

## 5. Cambios en código existente

| Fichero | Cambio |
|---|---|
| `assets/schema.sql`, `data/rag/RagSchema.kt`, `domain/rag/RagIndexConfig.kt` | v4 (R1) |
| `assets/chat_schema.sql`, `data/chat/ChatSchema.kt`, `SqliteChatRepository` | v2 (R7) |
| `data/rag/DefaultRagIndex.kt` | F1–F8 |
| `data/retrieval/HybridRetriever.kt` | Pasa el ámbito a las búsquedas y a la selección |
| `domain/retrieval/RetrievalModels.kt`, `RetrieveContextUseCase.kt` | `SearchScope` |
| `data/chat/DefaultChatSession.kt`, `domain/chat/*` | S1–S4 y la disponibilidad |
| `data/ingest/DocumentImporter.kt`, `DefaultImportQueueRepository.kt` | Carpeta de destino (F6) |
| `data/library/DefaultLibraryRepository.kt` | Carpetas, mover y la página por carpeta |
| `offline/tests/test_appconfig.py` | `schema_version == 4` |
| `architecture/LayeringTest` | `feature/folders` y `feature/chat/scope` sin `data.`, SQLite ni `File` |
