# Contrato: API del chat (F11)

Capas (constitución II):

```
ChatScreen → ChatViewModel → casos de uso (domain/chat)
  → ChatSession / ChatRepository / AnswerEngine (interfaces de domain/chat)
  → DefaultChatSession, SqliteChatRepository, EngineAnswerEngine (data/chat)
  → RetrieveContextUseCase (F10), RagIndex (F04), EngineCoordinator + EngineFactory (rag/runtime),
    ModelFileRepository (F03), y los algoritmos puros de rag/answer
```

`feature/chat` no importa `data.`, SQLite, WorkManager, LiteRT ni `java.io.File`. `LayeringTest` lo comprueba con una regla nueva (U1).

## Algoritmos puros (`rag/answer`)

```kotlin
object PromptBuilder {
    const val REFUSAL = "No encuentro esa información en los documentos disponibles."
    const val PREVIOUS_QUESTION_CHARS = 300   // code points
    const val PREVIOUS_ANSWER_CHARS = 500     // code points
    val TEMPLATES: List<String>               // "informe", "v2", "v3", "v4" (research R4)

    data class Block(val documentName: String?, val headingPath: String?, val page: Int?, val text: String)

    /** Exactly `offline/eval/prompt.py` build_prompt (research R2); SPANISH gives the desktop text. */
    fun build(
        question: String,
        blocks: List<Block>,
        previous: Pair<String, String>?,
        template: String = RagParameters.PROMPT_TEMPLATE,
        language: PromptLanguage = PromptLanguage.SPANISH,
    ): String

    /** ⌈code points / CHARS_PER_TOKEN⌉ (research R10). */
    fun estimatedTokens(prompt: String): Int
}

/** Response language of the prompt (FR-014, G25); F12 adds the others. */
enum class PromptLanguage { SPANISH }

object AnswerReading {
    /** Every [n] and [n, m] number, in order, repeats kept (`judge.citations`). */
    fun citations(answer: String): List<Int>
    /** `judge.norm`, with Python's Unicode classes (research R3). */
    fun norm(text: String): String
    /** `norm(answer) == norm(REFUSAL)`. */
    fun isRefusal(answer: String): Boolean
    /** Valid numbers (1..blockCount) in first-citation order, without repeats (D-053). */
    fun citedOrder(answer: String, blockCount: Int): List<Int>
}
```

## Dominio (`domain/chat`)

Tipos en data-model §2.

```kotlin
interface ChatRepository {
    fun conversation(id: Long): Flow<List<ChatTurn>>           // empty when it does not exist
    suspend fun lastConversationId(): Long?                    // highest updated_at
    suspend fun createConversation(now: Long): Long
    suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn
    suspend fun updateTurn(turnId: Long, update: TurnUpdate)   // state, answer, failure, refusal, promptBlocks, finishedAt
    suspend fun replaceSources(turnId: Long, sources: List<AnswerSource>)
    suspend fun resetForRetry(turnId: Long)                    // RETRIEVING, answer "", no sources, no failure
    suspend fun markInterrupted(now: Long): Int                // research R8
    suspend fun deleteConversation(id: Long)
    suspend fun deleteAll()                                    // for F12; no UI in F11
    val storageState: StateFlow<ChatStorageState>             // Ok or Unavailable (A15, C17)
}

interface AnswerEngine {
    /** Gemma Verified and prepared (F03 rule); refreshes once when the path is unknown. */
    suspend fun ready(): Boolean
    /** The prepared backend, or null when not ready; CPU drives C9. */
    fun preparedBackend(): Flow<ComputeBackend?>
    /** Loads Gemma without generating (research R5). */
    suspend fun warmUp()
    /** Streams the answer; cancelling the collector stops the engine. */
    fun generate(prompt: String): Flow<AnswerEvent>
}

sealed interface AnswerEvent {
    data class Token(val delta: String) : AnswerEvent
    data class Finished(val ttftMs: Double, val totalMs: Double, val decodeTokensPerSecond: Double?) : AnswerEvent
}

interface ChatSession {
    val active: StateFlow<ActiveAnswer?>
    /** Starts an answer; returns the conversation id (new when [conversationId] is null), or null when the chat is not Ready. */
    suspend fun ask(conversationId: Long?, question: String): Long?
    fun cancel()
    /** False when the turn is not FAILED/INTERRUPTED, another answer runs or the chat is not Ready (A10). */
    suspend fun retry(turnId: Long): Boolean
    /** Cancels any answer in progress (new conversation, research R6). */
    fun startNewConversation()
    suspend fun delete(conversationId: Long)
}
```

**Casos de uso:** `factoryOf` en `DomainModule`, cada uno una llamada fina.

| Caso de uso | Contenido |
|---|---|
| `ObserveConversationUseCase(id)` | Combina `ChatRepository.conversation(id)` con `ChatSession.active`; el texto en memoria manda sobre el guardado del turno activo |
| `ObserveChatAvailabilityUseCase()` | R11, con el estado de `chat.db` (C17) |
| `ResolveSourcesUseCase(sources)` | `Flow<Map<Long, SourceAvailability>>` por `chunkId`, observando los cambios del índice (R12) |
| `AskQuestionUseCase` | `ChatSession.ask` |
| `CancelAnswerUseCase` | `ChatSession.cancel` |
| `RetryAnswerUseCase` | `ChatSession.retry` |
| `StartNewConversationUseCase` | `ChatSession.startNewConversation` |
| `DeleteConversationUseCase` | `ChatSession.delete` |
| `LastConversationUseCase` | `ChatRepository.lastConversationId` |
| `WarmUpAnswerEngineUseCase` | `AnswerEngine.warmUp`, solo si la disponibilidad es `Ready` |

## Reglas de la sesión (A1–A15)

- **A1. Una sola respuesta en curso en la app.** `ask` o `retry` con otra en curso devuelven `null` o `false` y no hacen nada. La pantalla nunca lo pide, porque muestra detener y desactiva «Reintentar».
- **A2. Puerta de disponibilidad.** `ask` y `retry` leen la disponibilidad en el momento (la sesión recibe el flujo de `ObserveChatAvailabilityUseCase`). Si no es `Ready`, no hacen nada. Con una tanda en marcha nunca se carga Gemma (R7, FR-030).
- **A3. Pregunta.** Sin `trim` en lo que se guarda. Una pregunta en blanco, o de más de `MAX_QUESTION_CHARS` en `String.length`, devuelve `null` sin fallar.
- **A4. Turno anterior** (research R10, C12 cambiada en el analyze): el último turno de la conversación, antes del que se pregunta.
  - **Completado, también si fue la negativa:** su pregunta para la búsqueda, y su pregunta y su respuesta en bruto para el prompt, como el escritorio.
  - **Otro estado:** nada.
- **A5. Recuperación:** se traduce a estados según research R6, paso 4.
  - `NoResults` da una negativa **sin llamar al modelo** (FR-008), con `PromptBuilder.REFUSAL` como respuesta en bruto.
  - `IndexUnavailable` da `FAILED(INDEX)`.
  - El resto de fallos da `FAILED(GENERATION)`.
- **A6. Límite del prompt** (FR-015, research R10). Mientras `estimatedTokens > PROMPT_LIMIT_TOKENS`:
  1. se quita el bloque peor clasificado (el mejor rango fusionado de sus fragmentos), mientras quede más de uno;
  2. después, el turno anterior.

  El último bloque nunca se quita y el texto de un bloque nunca se corta. Los recortes se registran.
- **A7. Generación.**
  - El estado pasa a `GENERATING` al entregar el prompt al motor. Hasta entonces sigue en `RETRIEVING`, también mientras Gemma se carga.
  - El texto se acumula en `active`.
  - Se guarda como mucho cada `CHECKPOINT_MS` y siempre al terminar, al cancelarse, al interrumpirse o al fallar.
- **A8. Cierre.**
  - Si el texto es vacío o solo espacios, `FAILED(GENERATION)`.
  - Si `AnswerReading.isRefusal`, `COMPLETED` con `refusal = true` y sin fuentes.
  - Si no, `COMPLETED` con las fuentes de `citedOrder` (`position` desde 1).
  - Sin citas válidas, `COMPLETED` sin fuentes; la pantalla pone la nota C2.
- **A9. Cancelar** (solo el usuario: detener, nueva conversación o borrar).
  - El turno pasa a `CANCELLED` con el texto recibido.
  - Sus citas válidas se guardan como en A8, sin decidir la negativa.
  - Debe tardar menos de 1 s (SC-005).
- **A10. Reintentar.** Solo desde `FAILED` o `INTERRUPTED`, y con A1 y A2. Repite la misma pregunta en el mismo turno, con el turno anterior a él.
- **A11. App oculta o memoria baja** (research R8). La sesión es un `MemoryTrimListener`.
  - Con `TRIM_MEMORY_UI_HIDDEN` o superior, o con `onLowMemory`, detiene la respuesta. El turno queda `INTERRUPTED` con su texto y sus citas válidas (C13) antes de que se libere Gemma.
  - `EngineMemoryCallbacks` avisa a sus listeners antes de `releaseUnheld()`.
- **A12. Arranque de la sesión.** `markInterrupted(now)` se llama una vez, antes de la primera lectura del repositorio (R8).
- **A13. Borrar.** Si la conversación tiene la respuesta en curso, la cancela (A9), y después la borra en cascada.
- **A14. Registro.** Una línea por respuesta con solo cifras (research R6). Nunca el texto de la pregunta, el prompt, la respuesta, los fragmentos ni los nombres.
- **A15. Fallos de `chat.db`.**
  - Si una escritura falla (sin espacio), la respuesta sigue en `active` con `saveFailed = true` y la nota C16, hasta la siguiente pregunta.
  - Si el fichero no se puede abrir, o declara una versión superior a la conocida, la disponibilidad es `StorageUnavailable` (C17). Nunca se borra ni se reescribe el fichero.

## `EngineAnswerEngine` (`data/chat`)

- **Clave:** `EngineKey(GEMMA4_E2B, preparedBackends[GEMMA4_E2B])`.
- **Fábrica:** `factory.generator(file, path, backend, GenerationConfig(LLM_MAX_NUM_TOKENS, MAX_OUTPUT_TOKENS, greedy = true)).initializeOrClose { it.initialize() }`.
- **`warmUp`:** `coordinator.withEngine(key, create) { }`.
- **`generate`:** `coordinator.withEngine(key, create) { engine -> engine.generate(prompt).collect { … } }`, reemitido como `AnswerEvent`. No captura la cancelación.
- **Exclusividad:** nunca llama a `withEngine` dentro de otro bloque. La recuperación (embedding en CPU) termina antes de entrar en el bloque de Gemma.

## `SqliteChatRepository` (`data/chat`)

- **Conexión:** `RagConnection.open(File(AppDirs.chat, "chat.db"), extensionPath = null)`, abierta de forma perezosa en `AppDispatchers.database`. Los `PRAGMA` y el WAL son los del índice.
- **Esquema:** `chat_schema.sql` (data-model §3) y `chat_meta.schema_version = 1`. Las migraciones siguen el patrón de `RagMigrations`, hoy vacías.
- **Una versión más nueva o un fichero dañado:** el repositorio expone `storageState` (`Ok` o `Unavailable`) y no escribe. La disponibilidad pasa a `StorageUnavailable` (C17). No se borra nada (constitución V).
- **Flujos:** un contador de cambios que sube tras cada escritura confirmada.

## Cambios en código existente

| Fichero | Cambio |
|---|---|
| `data/rag/RagConnection.kt` | `open(dbFile, extensionPath: String?)`; sin extensión, no la carga ni comprueba `vector.so` |
| `data/models/AppDirs.kt` | `chat/` dentro de `ensureAll()` |
| `domain/rag/RagIndex.kt` + `data/rag/DefaultRagIndex.kt` | `chatLibraryState()` (data-model §2) |
| `rag/runtime/EngineMemoryCallbacks.kt` | `MemoryTrimListener`s avisados antes de `releaseUnheld()` |
| `domain/rag/RagParameters.kt` + `offline/config/rag_parameters.json` | `LLM_MAX_NUM_TOKENS` / `llm_max_num_tokens` |
| `offline/eval/generate.py` | `MAX_NUM_TOKENS` leído del JSON |
| `core/navigation/Destinations.kt` y `NavigationActions.kt` | `Chat(conversationId, newConversation)` y `openNewChat()` (research R13) |
| `di/*` | `single<ChatRepository>`, `single<AnswerEngine>`, `single<ChatSession>` (además `MemoryTrimListener`), casos de uso y `ChatViewModel` |

## Pruebas JVM del contrato

| Prueba | Qué comprueba |
|---|---|
| `PromptBuilderTest` | Casos de `test_prompt.py`; puntos de código; sin bloques; ruta o página nulas; el idioma por defecto da el texto del escritorio |
| `AnswerReadingTest` | Casos de `test_judge.py`; Unicode; `citedOrder` |
| `AnswerGoldenTest` | Las 426 filas del golden: prompt (longitud y huella, y texto en 10), citas, negativa y `norm` |
| `DefaultChatSessionTest` | Con repositorio en memoria, recuperación y motor falsos, y el coordinador real: A1–A15, disponibilidad (también para reintentar), recortes del prompt, checkpoints, cancelar en < 1 s (tiempo virtual), interrumpir con la app oculta y registro sin texto marcado |
| `ChatDuringIndexingTest` | El coordinador real con motores falsos: la puerta cerrada con la tanda en marcha, para preguntar, precargar y reintentar; y `releaseGenerators()` que espera a la respuesta en curso. Nunca dos GPU |
| `EngineAnswerEngineTest` | Clave y config; precarga; cancelación |
| `ObserveChatAvailabilityUseCaseTest` | Precedencia de R11 |
| `ResolveSourcesUseCaseTest` | Documento borrado, fragmento ausente y disponible |
| `EngineMemoryCallbacksTest` | Ampliada: listeners antes de liberar |
| `RagParametersTest` | La clave nueva |
| `AppModulesTest` | El grafo con el chat |
| `LayeringTest` | Regla U1 |

**Instrumentadas:**
- `SqliteChatRepositoryTest`: esquema, cascada, `markInterrupted`, versión superior y fichero dañado (`storageState`, sin escribir ni borrar), y orden de `lastConversationId`.
- `RagIndexChatStateTest`: `chatLibraryState` con documentos en cada estado y una sombra.
