# Data model — F06 Importación y parsing

## 1. Esquema v2 (D-038, research R11)

`app/src/main/assets/schema.sql` pasa a la versión 2. El único cambio es una columna en `documents`:

```sql
CREATE TABLE IF NOT EXISTS documents (
    ...
    local_path      TEXT,
    skipped_pages   INTEGER,            -- v2: páginas de PDF sin texto omitidas; NULL en TXT y MD
    created_at_ms   INTEGER NOT NULL,
    updated_at_ms   INTEGER NOT NULL
);
```

- **Migración** `RagMigration(from = 1, statements = ["ALTER TABLE documents ADD COLUMN skipped_pages INTEGER"])`, la primera de `RagMigrations.all`.
- **`RagIndexConfig.SCHEMA_VERSION`:** pasa a `2`, y con ello `rag_meta.schema_version` a `"2"`.
- **Mismo espacio vectorial:** la versión del normalizador, los prefijos, la dimensión y el modelo no cambian.

### Uso de `documents` en F06

| Columna | Valor en F06 |
|---|---|
| `source` | `'user'` |
| `uri` | URI del selector, solo informativa. Nunca se vuelve a abrir (G22) |
| `display_name` | `OpenableColumns.DISPLAY_NAME`. Si falta, el último segmento de la URI |
| `mime_type` | `application/pdf`, `text/plain` o `text/markdown`, según el formato detectado |
| `content_hash` | SHA-256 en hexadecimal minúsculo de los bytes copiados |
| `status` | `pending` al registrar. `parsed` tras trocear. `error` si falla el procesamiento |
| `chunk_count` | Número de fragmentos del troceado (solo con `parsed`) |
| `error` | Código de error de procesamiento (§3), o NULL |
| `size_bytes` | Bytes copiados |
| `page_count` | Páginas del PDF (al importar). NULL en TXT y MD |
| `local_path` | Ruta absoluta de la copia: `<noBackupFilesDir>/documents/<sha256>.<ext>` |
| `skipped_pages` | Páginas sin texto (solo PDF, al trocear) |

## 2. Ficheros (`AppDirs.documents`)

| Fichero | Contenido | Vida |
|---|---|---|
| `documents/<sha256>.pdf\|txt\|md` | Copia privada, idéntica al original | Mientras exista el documento. «Quitar» o el borrado de F08 lo eliminan |
| `documents/.import-<uuid>.tmp` | Copia en curso | Se borra al terminar, al fallar o en la limpieza de huérfanos |

**Limpieza de huérfanos** (`DocumentFileStore.cleanOrphans`), una vez por proceso, antes de la primera emisión de la cola. Borra:
- los `*.tmp`;
- los ficheros de `documents/` que no son el `local_path` de ningún documento.

## 3. Tipos de dominio (`domain/ingest`)

```kotlin
enum class DocumentFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"), TXT("text/plain", "txt"), MARKDOWN("text/markdown", "md")
}

/** Why a file did not become a document, or why its processing failed. [code] goes to documents.error. */
enum class IngestError(val code: String, val retryable: Boolean) {
    UNSUPPORTED_FORMAT("unsupported_format", false),
    DAMAGED("damaged", false),
    ENCRYPTED("encrypted", false),
    TOO_LARGE("too_large", false),
    NO_SPACE("no_space", true),
    ACCESS_LOST("access_lost", true),
    READ_FAILED("read_failed", true),
    DUPLICATE("duplicate", false),
    PDF_UNAVAILABLE("pdf_unavailable", false),
    NO_TEXT("no_text", false),
    EXTRACTION_FAILED("extraction_failed", true),
    INDEX_UNAVAILABLE("index_unavailable", true),
}

object ImportLimits {
    const val MAX_FILE_BYTES = 100L * 1_000_000      // 100 MB decimales, como el tamaño que muestra el sistema (D-037)
    const val MAX_PDF_PAGES = 2_000
    const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024
}
```

`MAX_FILE_BYTES` usa MB decimales para coincidir con lo que el sistema muestra («100 MB»). Un archivo de exactamente 100.000.000 bytes se acepta (caso borde de la spec).

### Elementos de la cola

```kotlin
sealed interface QueueItemState {
    data class Importing(val bytesCopied: Long, val totalBytes: Long?) : QueueItemState   // intento en memoria
    data class ImportFailed(val error: IngestError) : QueueItemState                       // intento en memoria
    data object Ready : QueueItemState                                                     // pending, sin trabajo
    data object Queued : QueueItemState                                                    // pending, en un trabajo activo
    data class Extracting(val page: Int?, val pages: Int?) : QueueItemState                // pending, etapa actual
    data object Chunking : QueueItemState
    data class Prepared(val chunks: Int, val skippedPages: Int?) : QueueItemState          // parsed
    data class Failed(val error: IngestError) : QueueItemState                             // error persistido
}

data class QueueItem(
    val key: QueueKey,                 // Attempt(uuid) o Document(id): estable para la UI y las acciones
    val displayName: String,
    val sizeBytes: Long?,
    val format: DocumentFormat?,
    val state: QueueItemState,
) {
    val canRetry: Boolean   // ImportFailed o Failed con error.retryable
    val canRemove: Boolean  // siempre; en Importing, Quitar cancela la copia (contracts/ingest-api.md I7)
}

data class ImportQueue(
    val items: List<QueueItem>,        // intentos en curso o fallidos primero, después documentos por fecha
    val readyCount: Int,               // estados Ready: habilita «Iniciar indexación»
    val pdfSupported: Boolean,
    val indexAvailable: Boolean,       // false si RagIndex está Incompatible o Unreadable
)
```

### Extracción y troceado (`rag/ingest`, Kotlin puro)

```kotlin
data class RawBlock(val headingPath: List<String>, val text: String, val page: Int?)       // informe
data class Chunk(val ordinal: Int, val headingPath: String, val content: String, val page: Int?)  // informe

data class ExtractionSummary(val blocks: Int, val pages: Int?, val skippedPages: Int?)

interface PdfPages : Closeable { val pageCount: Int; fun pageText(index: Int): String }   // índice 0
fun interface PdfOpener { fun open(file: File): PdfPages }   // lanza PdfEncryptedException / PdfDamagedException
```

### Progreso del trabajo

`IngestProgress(documentId: Long, stage: Stage, page: Int?, pages: Int?)` con `Stage { EXTRACTING, CHUNKING }`. Se transporta en `WorkInfo.progress` con las claves `doc`, `stage`, `page` y `pages`.

## 4. Máquinas de estados

**Intento de importación** (en memoria):

```text
Importing ──ok──────────────► (desaparece: ahora es un documento Ready)
    │
    └─error──► ImportFailed(e) ──Reintentar (e.retryable y URI aún legible)──► Importing
                     │
                     └─Quitar──► (desaparece)
Importing ──Quitar──► cancelación; se borra el temporal ──► (desaparece)
```

**Documento** (persistido; la etapa sale del trabajo):

```text
pending ──«Iniciar indexación»──► pending + etiqueta en trabajo activo (Queued)
   ▲                                   │
   │                                   ├─► Extracting ─► Chunking ─► parsed (Prepared)
   │                                   └─► error(code) (Failed)
   │                                              │
   └────────────── Reintentar (code reintentable) ┘   (vuelve a pending y se encola solo ese id)
cualquiera no indexado ──Quitar──► borrado (registro y copia)
parsed ──(F07)──► embedding ──► done (sale de la cola)
```

Una interrupción del trabajo deja el documento en `pending`. La siguiente ejecución lo reprocesa entero (FR-013).

## 5. Golden de extracción

`offline/golden/extraction_golden.jsonl`, generado por `offline/gen_extraction_golden.py` (contracts/extraction-golden.md). Una línea por caso:

```json
{"case": "md_nested_headings", "kind": "markdown", "input": "...", "blocks": [{"heading_path": ["A", "B"], "text": "...", "page": null}]}
{"case": "txt_crlf", "kind": "text", "input": "...", "blocks": [...]}
{"case": "pdf_page_hyphen", "kind": "pdf_page", "input": "texto de página tal como lo da el extractor", "text": "párrafos unidos con \n\n", "paragraphs": 3}
{"case": "decode_cp1252", "kind": "decode", "input_b64": "...", "text": "..."}
```

Tipos de caso:
- `markdown` y `text` aplican `extract_markdown` / `extract_text` sobre el texto ya decodificado;
- `pdf_page` aplica `pdf_paragraphs(input.splitlines())` y une con `\n\n`;
- `decode` aplica `decode_text` a los bytes.
