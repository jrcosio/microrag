# Data model: Historial de chats (F13)

Fecha: 2026-11-02. Parte del modelo de F11 (`specs/011-chat-sources/data-model.md`) y de los cambios de F12 (`specs/012-settings-languages/data-model.md` §3).

**`chat.db` no cambia de esquema** (v1, research R1). Todo lo nuevo se calcula a partir de `conversations` y `turns`.

## 1. Entradas del historial (`domain/chat`)

```kotlin
/** One row of the history (FR-005): computed from the conversation, never edited. */
data class ConversationSummary(
    val id: Long,
    /** The first question, with whitespace runs as single spaces; at most 300 characters (research R1). */
    val title: String,
    /** `conversations.updated_at`: the time of the last question (FR-004). */
    val lastActivityAt: Long,
    /** Turns of the conversation, at least 1. */
    val questions: Int,
)

/** The first [entries] of [total], by last activity (research R1). */
data class ConversationPage(val entries: List<ConversationSummary>, val total: Int) {
    val hasMore: Boolean get() = entries.size < total
}

object HistoryLimits {
    /** Rows per block, as the Library (research R1). */
    const val PAGE = 50
}
```

**Reglas:**
- Solo entran las conversaciones con al menos una pregunta (`EXISTS` en `turns`).
- **Orden:** `updated_at DESC, id DESC`.
- `total` es el mismo recuento que `conversationCount()` (F12).
- El título sale de la pregunta con el `ordinal` más bajo, que es estable. La normalización la hace `ObserveConversationsUseCase`, no SQLite.

## 2. Repositorio (`ChatRepository`, cambios)

| Miembro | Nuevo o cambiado | Contrato |
|---|---|---|
| `conversations(limit: Int): Flow<ConversationPage>` | nuevo | Lee la página y el total en el mismo bloque, al suscribirse y cada vez que sube `listChanges`, con `distinctUntilChanged()`. Si `chat.db` no está disponible o la lectura falla, el flujo falla con `ChatStorageException` (C17) |
| `lastConversationId()` | cambiado | Solo entre las conversaciones con preguntas (`EXISTS`, analyze AN-15) |
| `listChanges` (privado de `SqliteChatRepository`) | nuevo | Sube tras `insertTurn`, `deleteConversation` y `deleteAll` confirmados. No sube con `createConversation`, `updateTurn`, `replaceSources`, `resetForRetry` ni `markInterrupted` (research R2) |

`InMemoryChatRepository` (fake de pruebas) implementa lo mismo.

## 3. Sesión del chat (`ChatSession`, cambios)

| Miembro | Nuevo o cambiado | Contrato |
|---|---|---|
| `suspend openConversation(conversationId: Long)` | nuevo | Bajo el cerrojo. Si la respuesta en curso es de otra conversación, la detiene como «Nuevo chat» (`CANCELLED`) y espera a que termine. Con la misma, no hace nada (FR-013, research R5) |
| `delete(conversationId: Long)` | cambiado | Bajo el cerrojo: para y espera solo si la respuesta en curso es de esa conversación, borra y emite el id en `deletions`. Lanza `ChatStorageException` si el borrado falla, sin emitir |
| `deletions: SharedFlow<Long>` | nuevo | Un id por conversación borrada con `delete`, emitido fuera del cerrojo, con búfer de 8 y sin repetir el último valor. `deleteAll` sigue avisando por `historyCleared` (F12) |
| `ask`, `retry` | cambiado | Bajo el cerrojo, esperan a un trabajo que se está parando antes de empezar otro (analyze AN-04) |
| `finish()` (privado) | cambiado | Solo limpia `current` si sigue siendo su propia respuesta (analyze AN-04) |

## 4. Casos de uso (`domain/chat`)

| Caso de uso | Nuevo o cambiado | Qué hace |
|---|---|---|
| `ObserveConversationsUseCase(limit: Flow<Int>)` | nuevo | `Flow<HistoryContent>`: `Loading`, `Unavailable` o `Page(ConversationPage)`. Normaliza los títulos. Convierte el fallo del flujo en `Unavailable` sin propagarlo |
| `OpenConversationUseCase(id)` | nuevo | `session.openConversation(id)` |
| `ObserveConversationDeletionsUseCase()` | nuevo | `session.deletions` |
| `ObserveAnswerInProgressUseCase()` | nuevo | `Flow<Long?>`: la conversación de la respuesta en curso mientras está en `RETRIEVING` o `GENERATING`; si no, `null` |
| `DeleteConversationUseCase(id)` | cambiado | Devuelve `Boolean` y no lanza: `false` si `chat.db` lo rechaza (research R6) |
| `ConversationExistsUseCase(id)` | nuevo | `repository.conversationExists(id)`; `true` si no se puede leer, para no vaciar el chat por un fallo (analyze AN-03) |
| `ResolveSourcesUseCase` | cambiado | Recibe `List<CitedSource>` (la fuente y la hora de su turno). Un documento creado después del turno es `DOCUMENT_GONE` (analyze AN-01, research R7) |

```kotlin
/** A cited block and the time its answer was given; a document created later is another one (research R7). */
data class CitedSource(val source: AnswerSource, val citedAt: Long)

sealed interface HistoryContent {
    data object Loading : HistoryContent
    /** C17: the saved conversations cannot be read; nothing is deleted. */
    data object Unavailable : HistoryContent
    data class Page(val page: ConversationPage) : HistoryContent
}
```

## 5. Grupos por fecha (`feature/history`)

```kotlin
sealed interface HistoryGroup {
    data object Today : HistoryGroup
    data object Yesterday : HistoryGroup
    /** 2 to 6 calendar days before today. */
    data object Last7Days : HistoryGroup
    data class Month(val year: Int, val month: java.time.Month) : HistoryGroup
}

/** Pure (research R3): entries keep their order; a group appears once, before its first entry. */
fun group(entries: List<ConversationSummary>, today: LocalDate, zone: ZoneId): List<HistorySection>

data class HistorySection(val group: HistoryGroup, val entries: List<ConversationSummary>)
```

- La diferencia se cuenta en días de calendario en `zone`. Una fecha posterior a hoy va en `Today`.
- Los grupos de mes van del más reciente al más antiguo, como sus filas.

## 6. Estado de la pantalla (`feature/history`)

```kotlin
data class HistoryUiState(
    val content: HistoryScreenContent = HistoryScreenContent.Loading,
    /** The conversation the chat shows, for «Abierta» (route argument, research R4). */
    val openConversationId: Long? = null,
    /** Id waiting for confirmation (H6); kept in the SavedStateHandle. */
    val confirmDeleteId: Long? = null,
    /** The pending one has an answer in progress: the dialog adds «La respuesta en curso se detendrá.». */
    val confirmDeleteAnswering: Boolean = false,
)

sealed interface HistoryScreenContent {
    data object Loading : HistoryScreenContent
    data object Empty : HistoryScreenContent
    data object Error : HistoryScreenContent
    data class Sections(val sections: List<HistorySection>, val hasMore: Boolean, val loaded: Int) : HistoryScreenContent
}

sealed interface HistoryEvent {
    data class Open(val conversationId: Long) : HistoryEvent
    data object NewChat : HistoryEvent
    data object Deleted : HistoryEvent
    data object DeleteFailed : HistoryEvent
}
```

**`SavedStateHandle` de Historial:**
- `history_limit`: el límite de la tanda, de 50 en 50;
- `history_confirm_delete`: el id pendiente.

**Hoy y zona:** `HistoryViewModel` recibe un `Clock` solo para el instante. `onStart()` vuelve a leer el instante y `ZoneId.systemDefault()`, y esa zona sirve para los grupos y para los textos (research R3).

## 7. Chat (cambios sobre F11 y F12)

- **`TurnUi.chipTargets: List<SourceTarget?>`:** `null` si su fuente es `DOCUMENT_GONE`, también en las fichas de una respuesta en curso o sin guardar (research R7).
- **`ChatAnnouncement.DELETE_FAILED`:** «No se pudo borrar la conversación.», con el texto `history_delete_failed` (research R6).
- **`ChatViewModel`:**
  - `onOpenConversation(id)`;
  - suscripción a `deletions` en `init`;
  - `onConfirmDelete()` con el resultado `Boolean`;
  - al restaurar una conversación guardada, `ConversationExistsUseCase` (research R5);
  - claves nuevas: ninguna.
- **Navegación:**
  - `History(openConversationId: Long? = null)`;
  - clave `CHAT_REQUEST` en el `SavedStateHandle` de la entrada del chat: `open:<id>` o `new` (research R4).
