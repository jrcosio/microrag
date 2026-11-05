# Data model: Chat con páginas, modo «Multimodal» opcional (F20)

**Fecha:** 2026-11-05. **Research:** [research.md](research.md) (R2–R5, R8–R13).

`rag.db` no cambia: sigue en la v5 de F19. F20 solo lee `pages`.

## 1. `chat.db` v3 (R9)

### 1.1 Migración 2 → 3

```sql
ALTER TABLE sources ADD COLUMN kind TEXT NOT NULL DEFAULT 'chunk'
    CHECK (kind IN ('chunk','page'));
ALTER TABLE turns ADD COLUMN page_tokens INTEGER;   -- 70, 140 o 280; NULL sin página
```

- **`ChatSchema.VERSION`** pasa a 3, y `ChatMigrations` gana la migración `from = 2`.
- **`chat_schema.sql`** recoge las dos columnas en su forma final, con la cabecera «schema version 3».
- **Conversaciones existentes:** sus fuentes quedan como `chunk` y sus turnos con `page_tokens = NULL`. Se abren igual (FR-017).

### 1.2 Una fuente de página en `sources`

| Columna | Fragmento (como hoy) | Página |
|---|---|---|
| `kind` | `'chunk'` | `'page'` |
| `position` | número con que se muestra (D-053) | igual |
| `prompt_number` | su `[n]` en el prompt | `k + 1`, tras los `k` bloques |
| `document_id` | documento | documento |
| `chunk_id` | primer fragmento del bloque | `0` (nunca es un id real) |
| `first_ordinal`, `last_ordinal` | rango del bloque | `0`, `0` |
| `document_name` | nombre al responder | igual |
| `page` | página del bloque, o NULL | **número de página citada**, empezando en 1 |

Una imagen suelta (JPG o PNG) es la página 1 de su documento. Su formato se lee del documento al mostrarla, y la UI no enseña el número (wireframe §4).

## 2. `multimodal.preferences_pb` (R12)

Se añade una clave a las de F19 (`stage`, `error`, `default_mode` y `text_embedder`):

| Clave | Valores | Por defecto |
|---|---|---|
| `page_detail` | `FAST`, `BALANCED` o `DETAILED` | `BALANCED` (D-083) |

Un valor desconocido se lee como `BALANCED`. Un fallo al escribir devuelve `false` y conserva el anterior, como `default_mode`.

## 3. Dominio

### 3.1 `domain/multimodal`

```kotlin
enum class PageDetail(val visualTokens: Int, val estimateTenthsOfSecond: Int) {
    FAST(70, 100), BALANCED(140, 115), DETAILED(280, 145)
}
```

`StoredMultimodal` gana `pageDetail: PageDetail = PageDetail.BALANCED`.

### 3.2 `domain/rag` y `domain/retrieval`

```kotlin
data class ScoredPage(
    val documentId: Long,
    val page: Int,                // 1-based
    val distance: Double,
    val documentName: String,
    val fileName: String,         // noBackupFilesDir/pages/<fileName>
    val image: Boolean,           // JPG o PNG suelto: su etiqueta no lleva página
)

data class PageKey(val documentId: Long, val page: Int)

// RagIndex
suspend fun pageSearch(query: FloatArray, k: Int = RagParameters.PAGE_CANDIDATES, scope: SearchScope = SearchScope.All): List<ScoredPage>
suspend fun existingPages(keys: Collection<PageKey>): Set<PageKey>
suspend fun pageFile(documentId: Long, page: Int): String?

// RetrievalRequest
val withPages: Boolean = false

// RetrievalOutcome.Blocks
val pages: List<ScoredPage> = emptyList()   // blocks puede estar vacío si pages no lo está

// RetrievalResult
val lists: RetrievalLists                   // vectores (id, distancia) y palabras (ids), por puesto

// RetrievalTimings
val pagesMs: Double? = null
```

**`RagParameters`** gana:

| Constante | Valor | Origen |
|---|---|---|
| `PAGE_CANDIDATES` | 5 | R2: solo entra la primera; el resto es para el diagnóstico |
| `ANSWER_PAGES` | 1 | D-078 |
| `PROMPT_TEMPLATE_VISUAL` | `"v5-visual"` | F18 R11 |
| `LLM_VISUAL_MAX_NUM_TOKENS` | 8192 | D-078 |
| `LLM_MAX_NUM_IMAGES` | 1 | D-078 |

### 3.3 `domain/chat`

```kotlin
enum class SourceKind { FRAGMENT, PAGE }

// AnswerSource y PromptBlockRef ganan:
val kind: SourceKind = SourceKind.FRAGMENT

enum class SourceAvailability { AVAILABLE, FRAGMENT_GONE, PAGE_GONE, DOCUMENT_GONE }

// ActiveAnswer gana:
val pageTokens: Int? = null      // el detalle de la página que entró, o null

// AnswerEngine
fun generate(prompt: AnswerPrompt): Flow<AnswerEvent>

sealed interface AnswerPrompt {
    data class Text(val text: String) : AnswerPrompt
    data class Visual(val parts: List<PromptPart>, val visualTokens: Int) : AnswerPrompt
}

sealed interface PromptPart {
    data class Text(val text: String) : PromptPart
    data class Image(val jpeg: ByteArray) : PromptPart
}
```

`ChatTurn` gana `pageTokens: Int?`, leído de `turns.page_tokens`.

### 3.4 `rag/runtime`

```kotlin
enum class EngineKind { DEFAULT, IMAGE_EMBEDDER, VISION_GENERATOR }

data class GenerationConfig(
    val maxNumTokens: Int,
    val maxOutputTokens: Int,
    val greedy: Boolean,
    val activationPrecision: ActivationPrecision? = null,
    val visionBackend: ComputeBackend? = null,   // CPU con el modo activo
    val maxNumImages: Int = 0,                   // 1 con el modo activo
)

// TextGenerator
fun generate(prompt: GenerationPrompt): Flow<GenerationEvent>

sealed interface GenerationPrompt {
    data class Text(val text: String) : GenerationPrompt
    data class Parts(val parts: List<PromptPart>, val visualTokens: Int) : GenerationPrompt
}
```

### 3.5 `domain/library`

```kotlin
interface PageImageRepository {
    /** The JPEG of the page as F19 stored it, or null if it is gone. */
    suspend fun image(documentId: Long, page: Int): ByteArray?
}
```

## 4. Navegación

| Ruta | Parámetros | Origen |
|---|---|---|
| `Document` | `documentId`, `chunkId?` y **`page?`** (nuevo) | una cita de fragmento (`chunkId`) o de página (`page`) |
| `PageViewer` (nueva) | `documentId`, `page` | el detalle, al tocar la página |
| `PageDetailRoute` (nueva) | ninguno | Ajustes, fila «Detalle de las páginas» |

## 5. Estados

**Estado de un turno en la UI (`TurnStatus`):** `SEARCHING` → (`READING_PAGE` si `pageTokens != null` y no hay texto) o `GENERATING` → texto. `READING_PAGE` termina con el primer token.

**Vista previa del detalle (`PreviewContent`):** gana `Page(documentId, page, documentName)`. Si la página ya no está, se muestra `Shown` o `None`, como sin cita, con el aviso `document_citation_page_missing`.
