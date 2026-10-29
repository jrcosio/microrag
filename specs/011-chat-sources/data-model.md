# Data model: Chat con fuentes (F11)

Fecha: 2026-10-29. Fuentes:
- spec aclarada (D-053) y propuestas C1–C15 aprobadas;
- research R1–R17;
- tipos existentes de F03, F07 y F10, que se reutilizan sin cambios salvo donde se indica.

## 1. Parámetros

**Compartidos** (`RagParameters` y `offline/config/rag_parameters.json`; `RagParametersTest` los mantiene iguales):

| Clave JSON | Constante Kotlin | Valor | Uso |
|---|---|---|---|
| `llm_max_num_tokens` (nuevo) | `LLM_MAX_NUM_TOKENS` | 4096 | Contexto del motor de Gemma, en la app y en `offline/eval/generate.py` (que hoy lo tiene fijo como `MAX_NUM_TOKENS`) |
| `prompt_template` | `PROMPT_TEMPLATE` | `"informe"`; cambia solo si R4 adopta una variante | Plantilla activa |
| `prompt_limit_tokens` | `PROMPT_LIMIT_TOKENS` | 2000 | Límite estimado del prompt (R10) |
| `max_output_tokens` | `MAX_OUTPUT_TOKENS` | 512 | Tokens de respuesta |
| `chars_per_token` | `CHARS_PER_TOKEN` | 4.554 | Estimación de tokens |

**Solo de la app** (`domain/chat/ChatLimits`):

| Constante | Valor | Uso |
|---|---|---|
| `MAX_QUESTION_CHARS` | 1000 | Longitud máxima de la pregunta (R10) |
| `CHECKPOINT_MS` | 1000 | Cada cuánto se guarda el texto parcial (R6) |

## 2. Dominio (`domain/chat`)

```kotlin
data class Conversation(val id: Long, val createdAt: Long, val updatedAt: Long)

enum class TurnState { RETRIEVING, GENERATING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

/** Why a turn failed: the index (C8) or anything else (C7). */
enum class TurnFailure { INDEX, GENERATION }

/** One question and its answer, in conversation order. */
data class ChatTurn(
    val id: Long,
    val conversationId: Long,
    val ordinal: Int,                 // 0, 1, 2… within the conversation
    val question: String,
    val answer: String,               // raw model text, with the prompt's [n]; "" until the first token
    val state: TurnState,
    val failure: TurnFailure?,        // only when state == FAILED
    val refusal: Boolean,             // the model answered the refusal sentence (C1), or there were no blocks
    val partialCoverage: Boolean,     // included documents were not indexed when asked (C6)
    val promptBlocks: Int,            // blocks in the prompt: the valid range of [n]
    val sources: List<AnswerSource>,  // cited only, in first-citation order (empty while in progress)
    val createdAt: Long,
    val finishedAt: Long?,
)

/** A cited block, with what it was when the answer was given (R12). */
data class AnswerSource(
    val position: Int,                // 1-based display number (D-053)
    val promptNumber: Int,            // its [n] in the prompt
    val documentId: Long,
    val chunkId: Long,                // first chunk of the block
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    val documentName: String,
    val page: Int?,
)

enum class SourceAvailability { AVAILABLE, FRAGMENT_GONE, DOCUMENT_GONE }

/** The answer being generated, kept in memory between checkpoints (R6). */
data class ActiveAnswer(
    val conversationId: Long,
    val turnId: Long,
    val state: TurnState,             // RETRIEVING or GENERATING
    val text: String,
    val blocks: List<AnswerSource>,   // every prompt block, position = promptNumber, so chips open while streaming
)

sealed interface ChatAvailability {           // R11, in precedence order
    data object ModelsNotReady : ChatAvailability             // C5
    data object IndexUnavailable : ChatAvailability           // C8
    data object EmptyLibrary : ChatAvailability               // C3
    data object WaitingForIndexing : ChatAvailability         // C14
    data object NothingReady : ChatAvailability               // C4
    data class Ready(val cpuMode: Boolean, val partialCoverage: Boolean) : ChatAvailability  // C9, C6
}

object ChatLimits {
    const val MAX_QUESTION_CHARS = 1000
    const val CHECKPOINT_MS = 1000L
}
```

**Estados de un turno:**
- `RETRIEVING` → `GENERATING` → `COMPLETED`;
- desde cualquiera de los dos primeros:
  - `CANCELLED`: detener, app oculta o nueva conversación;
  - `FAILED`: el motor o el índice fallan, o la respuesta llega vacía;
  - `INTERRUPTED`: el proceso murió, y se marca al arrancar la sesión.
- `RETRIEVING` → `COMPLETED` con `refusal = true` si la búsqueda no da bloques.
- `FAILED` → `RETRIEVING` con «Reintentar», en el mismo turno.
- `INTERRUPTED` también ofrece «Reintentar» (C13).
- `CANCELLED` es final.

**Nuevo en `domain/rag`:**

```kotlin
/** Aggregate of the library for the chat (R11); shadows left out. */
data class ChatLibraryState(val documents: Int, val searchable: Int, val pendingIncluded: Int)
// RagIndex: suspend fun chatLibraryState(): ChatLibraryState
```

## 3. `chat.db` (esquema v1)

Ruta: `noBackupFilesDir/chat/chat.db` (`AppDirs.chat`). Asset `chat_schema.sql`, cargado como `RagSchema`.

```sql
CREATE TABLE IF NOT EXISTS chat_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);                                     -- schema_version = 1

CREATE TABLE IF NOT EXISTS conversations (
  id          INTEGER PRIMARY KEY,
  created_at  INTEGER NOT NULL,        -- epoch ms
  updated_at  INTEGER NOT NULL         -- last question; orders "the last conversation" (FR-002)
);
CREATE INDEX IF NOT EXISTS conversations_updated ON conversations(updated_at DESC);

CREATE TABLE IF NOT EXISTS turns (
  id               INTEGER PRIMARY KEY,
  conversation_id  INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  ordinal          INTEGER NOT NULL,
  question         TEXT NOT NULL,
  answer           TEXT NOT NULL DEFAULT '',
  state            TEXT NOT NULL CHECK (state IN ('retrieving','generating','completed','cancelled','failed','interrupted')),
  failure          TEXT CHECK (failure IN ('index','generation')),
  refusal          INTEGER NOT NULL DEFAULT 0,
  partial_coverage INTEGER NOT NULL DEFAULT 0,
  prompt_blocks    INTEGER NOT NULL DEFAULT 0,
  created_at       INTEGER NOT NULL,
  finished_at      INTEGER,
  UNIQUE (conversation_id, ordinal)
);

CREATE TABLE IF NOT EXISTS sources (
  turn_id        INTEGER NOT NULL REFERENCES turns(id) ON DELETE CASCADE,
  position       INTEGER NOT NULL,
  prompt_number  INTEGER NOT NULL,
  document_id    INTEGER NOT NULL,      -- no foreign key: rag.db is another file, and the source outlives the document (FR-027)
  chunk_id       INTEGER NOT NULL,
  first_ordinal  INTEGER NOT NULL,
  last_ordinal   INTEGER NOT NULL,
  document_name  TEXT NOT NULL,
  page           INTEGER,
  PRIMARY KEY (turn_id, position)
);
```

**Reglas:**
- **Conversación sin turnos:** no existe. Se crea con la primera pregunta (FR-029).
- **`updated_at`:** cambia al insertar un turno.
- **Recuperación tras la muerte del proceso** (R8):
  ```sql
  UPDATE turns SET state = 'interrupted', finished_at = :now WHERE state IN ('retrieving', 'generating');
  ```
- **Borrado** (FR-004): `DELETE FROM conversations WHERE id = ?` borra en cascada sus turnos y fuentes. `deleteAll()` existe para F12, sin UI.
- **Contador de cambios:** sube tras cada escritura confirmada y alimenta los flujos del repositorio.

## 4. Fuente en pantalla (`feature/chat`)

```kotlin
data class SourceRow(
    val position: Int,
    val documentId: Long,
    val chunkId: Long,
    val citation: CitationUi,          // documentName, page, fragment = firstOrdinal + 1, available
    val lastFragment: Int?,            // lastOrdinal + 1 when the block spans several (C11)
)
```

`CitationUi` (F01) sigue igual. «Fragmentos M–N» se compone en la fila con el nuevo texto `citation_fragments` (contrato de UI).

## 5. Estado de la pantalla (`feature/chat`)

```kotlin
data class ChatUiState(
    val conversationId: Long? = null,
    val availability: ChatAvailability? = null,   // null until the first check: nothing is promised (constitution V)
    val turns: List<TurnUi> = emptyList(),
    val question: String = "",
    val answering: Boolean = false,               // the composer shows stop
    val menuOpen: Boolean = false,
    val confirmDelete: Boolean = false,
    val copiedTurnId: Long? = null,               // «Respuesta copiada» on API 31–32 (R14)
    val announcement: ChatAnnouncement? = null,   // one per state change (FR-039)
)

data class TurnUi(
    val id: Long,
    val question: String,
    val state: AnswerState,                       // F01: Streaming, Completed, Cancelled, Failed
    val status: TurnStatus?,                      // Searching or Generating while Streaming
    val content: AnswerContent,                   // §6; the C1 text when refusal
    val failureText: Int?,                        // C7, C8 or C13 string id
    val notes: List<Int>,                         // C2 and C6 string ids
    val sources: List<SourceRow>,
    val canRetry: Boolean,                        // FAILED or INTERRUPTED
)

enum class ChatAnnouncement { SEARCHING, GENERATING, COMPLETED, CANCELLED, FAILED, DELETED }
```

## 6. Contenido de una respuesta (`core/designsystem/model/AnswerContent.kt`)

```kotlin
data class AnswerContent(val blocks: List<AnswerBlock>) {
    companion object { val Empty = AnswerContent(emptyList()) }
}

sealed interface AnswerBlock {
    data class Paragraph(val spans: List<AnswerSpan>) : AnswerBlock
    /** [marker] is the list number ("1.") of an ordered item; null draws the dot of the PNG. */
    data class Bullet(val spans: List<AnswerSpan>, val marker: String? = null) : AnswerBlock
}

sealed interface AnswerSpan {
    data class Text(val text: String, val bold: Boolean = false) : AnswerSpan
    /** A tappable chip with the display [number] (D-053); [description] is the sentence for TalkBack. */
    data class Citation(val number: Int, val description: String) : AnswerSpan
}
```

`AnswerFormatter.format(raw, sources, describe)` → `AnswerContent` (R3), y `copyText(raw, sources)` → `String` (C10).

## 7. `offline/golden/answer_golden.jsonl`

Lo genera `offline/gen_answer_golden.py` (con `--check`). Lleva una fila por pregunta viva de F05 (426), ordenada por id, en UTF-8 con `ensure_ascii=False`:

```json
{
  "id": "q0001",
  "template": "informe",
  "question": "¿…?",
  "previous": ["pregunta anterior", "respuesta anterior generada"],
  "blocks": [{"document_name": "…", "heading_path": "… › …", "page": 12, "text": "…"}],
  "prompt_chars": 6412,
  "prompt_sha256": "…",
  "prompt": null,
  "answer": "…",
  "citations": [2, 1, 2],
  "cited_order": [2, 1],
  "refusal": false,
  "norm": "…"
}
```

**Campos:**
- `previous` es `null` si no hay turno anterior.
- `prompt` lleva el texto completo en 10 filas fijas: las 5 primeras, 3 de seguimiento y 2 con algún bloque sin ruta o sin página. En el resto es `null`.
- `prompt_chars` cuenta puntos de código, como `len()` de Python. `prompt_sha256` es la huella del prompt en UTF-8.
- `answer`, `citations`, `refusal` y `norm` salen de la respuesta de F05 (`06ef519f…/answers.jsonl`) y de `judge.py`.
- `cited_order` son los números válidos (1 ≤ n ≤ bloques) en el orden de su primera cita.
- `template` es la plantilla activa. Si R4 adopta una variante, `answer` sale de su ejecución.

Tamaño estimado: ≈ 1,6 MB, sobre todo el texto de los bloques.

## 8. Revisión de la plantilla (escritorio)

**Respuestas:**
- cada variante va a `offline/build/runs/<huella>/answers.jsonl`, con el mismo formato que F05;
- la huella incluye `prompt_template` cuando no es `informe` (`runs.LATE_FIELDS`).

**Revisión manual:** `offline/reports/template-review.csv`, en UTF-8 con BOM como `manual-judgement.csv`. Lleva una fila por respuesta que cambia en una variante que pasa la criba:

```
id,variante,tipo,pregunta,respuesta_esperada,datos_clave,respuesta_informe,respuesta_variante,regla,regla_citas,manual,manual_citas,comentario
```

**Resumen:** `offline/build/template_review.json`, que lee `report.py` para su sección. Por variante:
- respuestas cambiadas;
- regla automática: correctas, negativas y citas;
- si pasa la criba;
- juicio manual: correctas, negativas y citas;
- si se adopta.

## 9. Registros (solo cifras, nunca en release)

**Sesión de chat:** una línea por respuesta (R6).

**Prueba de medida** (`ChatBenchmarkTest`, etiqueta `chat-benchmark`):

```
chat-benchmark model=A063 sdk=35 backend=GPU n=… cold_ttft_ms=… ttft_p50=… ttft_p95=… prompt_tokens_p50=… prompt_tokens_p95=… decode_tok_s_p50=… total_p50=… total_p95=… rss_peak_mb=… refusals=… uncited=…
```
