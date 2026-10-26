# Contrato — Importación, cola y procesamiento

Capas (constitución II):
- `feature/ingest` usa los casos de uso de `domain/ingest`;
- la implementación vive en `data/ingest`;
- la extracción y el troceado, en `rag/ingest`, en Kotlin puro.

Los tipos están en data-model §3.

## Dominio (`domain/ingest`)

```kotlin
interface ImportQueueRepository {
    /** Queue as the user sees it (research R6). Cold flow; the first collection cleans orphan files. */
    val queue: Flow<ImportQueue>

    /** Starts importing [uris] (strings from the system picker) one by one, in the application scope. */
    fun import(uris: List<String>)

    /** Retries a failed attempt (same URI) or puts a failed document back in the processing queue. */
    suspend fun retry(key: QueueKey)

    /** Cancels an import, or deletes a non-indexed document and its private copy. */
    suspend fun remove(key: QueueKey)

    /** Enqueues every Ready document for processing; returns how many were enqueued. */
    suspend fun startProcessing(): Int
}
```

Casos de uso (`IngestUseCases.kt`, como en F03): `ObserveImportQueueUseCase`, `ImportDocumentsUseCase`, `RetryQueueItemUseCase`, `RemoveQueueItemUseCase` y `StartIndexingUseCase`. Son finos: delegan en el repositorio y son la única puerta del ViewModel.

## Ampliación de `RagIndex` (contrato de F04)

```kotlin
/** Documents in [statuses], oldest first (created_at_ms, id). */
suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument>

/** Id of the document with this content hash, if any (duplicate check before renaming, research R5). */
suspend fun documentIdByHash(contentHash: String): Long?

/** Increments after every committed write that changes `documents`: add, status, inclusion, delete, chunk insert. */
val documentChanges: StateFlow<Long>

suspend fun updateStatus(id: Long, status: DocumentStatus, error: String? = null, chunkCount: Int? = null,
                         skippedPages: Int? = null)
```

- `StoredDocument` gana `skippedPages: Int?`.
- `updateStatus` mantiene `COALESCE` para `chunk_count` y lo extiende a `skipped_pages`.
- Las operaciones nuevas siguen las reglas A1–A10 de F04: `IndexUnavailableException` si el índice no es `Compatible`, y dispatcher de base de datos.

## Datos (`data/ingest`)

| Clase | Responsabilidad |
|---|---|
| `DefaultImportQueueRepository` | Combina los intentos en memoria, `RagIndex.documents` (recargado con `documentChanges`) y `WorkManager.getWorkInfosForUniqueWorkFlow("ingest")` |
| `DocumentImporter` | Los pasos 1–9 de research R5 para un archivo; devuelve `Imported(id)` o `Rejected(IngestError)` |
| `SafDocumentSource` | Nombre visible, tamaño y `openInputStream` de una URI (`ContentResolver`), siempre en `AppDispatchers.io` |
| `DocumentFileStore` | Rutas, temporales, renombrado, borrado y `cleanOrphans` en `AppDirs.documents` |
| `PlatformPdfOpener`, `PdfSupport` | `PdfRendererPreV` (`@RequiresExtension(S, 13)`) → `PdfPages`; comprobación de la extensión (research R2–R3) |
| `IngestScheduler` | Encolado único con `APPEND_OR_REPLACE`, etiquetas e `inputData` (research R7) |
| `IngestWorker` | `CoroutineWorker` que delega en `IngestRunner` y publica el progreso |
| `IngestRunner` | Por documento: extraer, trocear, contar y actualizar el estado (research R7). Se prueba en la JVM con fakes |

## Extracción y troceado (`rag/ingest`, sin Android)

| Elemento | Responsabilidad |
|---|---|
| `PyText` | Semántica Python de espacios, líneas y dígitos (research R8) |
| `TextDecoding` | Validación UTF-8 en streaming, respaldo `windows-1252` y traducción de saltos de línea (R8) |
| `MarkdownExtractor`, `PlainTextExtractor` | Port de `extract_markdown` y `extract_text`. Emiten `Sequence<RawBlock>`, con partición por párrafo por encima de `MAX_BLOCK_CHARS` (R10) |
| `PdfExtractor` | Por página: `pySplitLines` y `pdfParagraphs`. Si hay párrafos, emite un bloque con su página; si no, cuenta la página como omitida |
| `DocumentExtractor` | Elige el extractor por `DocumentFormat` y devuelve los bloques más un `ExtractionSummary` |
| `Chunker` | Port del informe con estado (`add`/`finish`) y `chunk(List<RawBlock>)` para el golden (R9) |

## Trabajo en segundo plano

| Elemento | Valor |
|---|---|
| Nombre único | `ingest` |
| Política | `ExistingWorkPolicy.APPEND_OR_REPLACE` |
| `inputData` | `doc_ids: LongArray`, en orden de creación |
| Etiquetas | `ingest`, y `ingest-doc:<id>` por documento |
| Progreso | `doc` (Long), `stage` (`extracting` o `chunking`), `page` y `pages` (Int, o -1 si no aplica). Como mucho cada 250 ms |
| Resultado | Siempre `success`. Los errores van por documento. Una parada del sistema deja `pending` y WorkManager lo relanza |
| Restricciones | Ninguna: el usuario lo ha pedido y no usa red |

## Reglas

| # | Regla |
|---|---|
| I1 | Nunca se registra un documento cuya copia no esté completa en su ruta final (R5: renombrar y después registrar) |
| I2 | La identidad de un documento es su SHA-256. El duplicado se detecta con `documentIdByHash` antes de renombrar: no crea ni registro ni copia, y nunca toca la copia del documento existente |
| I3 | Los errores son por archivo o por documento. Un fallo nunca detiene la importación ni el procesamiento de los demás |
| I4 | La importación es secuencial, en el orden de selección. El procesamiento es secuencial, en orden de creación |
| I5 | Toda E/S de ficheros y de `ContentResolver` va en `AppDispatchers.io`. Todo el índice va en `AppDispatchers.database` (F04). Nada en el hilo principal (SC-009) |
| I6 | El worker es idempotente: reprocesar un `pending` da el mismo `chunk_count` y el mismo `skipped_pages` |
| I7 | `remove`: en un intento, cancela la copia y borra el temporal. En un documento, `deleteDocument` y después el fichero (un fichero sin registro lo recoge `cleanOrphans`). La confirmación de un documento la pide la UI antes de llamar (ingest-ui, constitución V) |
| I8 | `retry`: en un intento con error reintentable, repite la importación desde la URI; si ya no se puede leer, `ACCESS_LOST`. En un documento con error reintentable, `updateStatus(PENDING)` y se encola solo su id |
| I9 | `startProcessing` encola solo los `Ready` (pending sin etiqueta activa). Sin ninguno, no encola nada y devuelve 0 |
| I10 | Los logs llevan solo ids, códigos de error y recuentos; nunca nombres, rutas ni texto (constitución I) |
| I11 | Si el índice no está `Compatible`, `import` rechaza con `INDEX_UNAVAILABLE` sin copiar, y `queue` emite `indexAvailable = false` |
| I12 | Si no hay soporte de PDF, un PDF se rechaza con `PDF_UNAVAILABLE` antes de copiarlo, y `queue` emite `pdfSupported = false` |
