# Contrato: lista, apertura y borrado de conversaciones (F13)

Capas (constitución II): `feature/history` y `feature/chat` → casos de uso (`domain/chat`) → `ChatRepository` y `ChatSession`. Los tipos están en el data-model §1–§4.

## 1. Casos de uso

| Caso de uso | Nuevo o cambiado | Qué hace |
|---|---|---|
| `ObserveConversationsUseCase` | nuevo | `(limit: Flow<Int>) -> Flow<HistoryContent>` (Q1–Q4) |
| `OpenConversationUseCase` | nuevo | `session.openConversation(id)` (Q6) |
| `ObserveConversationDeletionsUseCase` | nuevo | `session.deletions` (Q8) |
| `ObserveAnswerInProgressUseCase` | nuevo | `Flow<Long?>` con la conversación de la respuesta en curso (Q9) |
| `DeleteConversationUseCase` | cambiado | Devuelve `Boolean` y nunca lanza (Q7) |
| `ConversationExistsUseCase` | nuevo | `repository.conversationExists(id)`; `true` si no se puede leer (Q16) |
| `ResolveSourcesUseCase` | cambiado | Recibe `List<CitedSource>`; aplica Q15 |

Se reutilizan:
- `StartNewConversationUseCase` (F11), en el chat, cuando la petición de Historial es `new`;
- `ObserveHistoryClearedUseCase` (F12), sin cambios, en el chat.

Historial no usa los casos de uso de recuento ni de borrado total de F12.

## 2. Reglas

**Lista**
- **Q1. Qué entra:** las conversaciones con al menos una pregunta, y solo esas. Una conversación sin preguntas no aparece nunca, ni siquiera como fila vacía.
- **Q2. Orden:** `updated_at DESC, id DESC`. `updated_at` es la hora de la última pregunta, así que preguntar en una conversación la sube la primera.
- **Q3. Página:**
  - `conversations(limit)` devuelve las `limit` primeras y el total;
  - el límite empieza en 50 y crece de 50 en 50 (`onLoadMore`), siempre desde el principio;
  - no hay duplicados ni huecos aunque la lista cambie entre dos tandas;
  - `hasMore = entries.size < total`.
- **Q4. Cuándo se vuelve a leer:**
  - tras `insertTurn`, `deleteConversation` y `deleteAll` confirmados (`listChanges`);
  - nunca por los guardados de cada segundo de una respuesta;
  - el flujo no emite si la página no cambia (`distinctUntilChanged`).
- **Q5. Fallo:**
  - si `chat.db` no está disponible (C17), o una lectura falla, `conversations()` falla con `ChatStorageException`. Nunca deja pasar una excepción de SQLite;
  - el caso de uso emite `Unavailable` y no se borra nada;
  - no hay «Reintentar» (D-065): el fichero no se vuelve a abrir hasta reiniciar la app. Volver a la pantalla vuelve a suscribirse;
  - el título se normaliza en el caso de uso: `\s+` → « » y `trim()`.

**Abrir y borrar**
- **Q6. Abrir** (`openConversation`, suspendida, bajo el cerrojo):
  - si la respuesta en curso es de otra conversación, se detiene con `CANCELLED`, conserva su texto parcial y sus citas válidas (A9 de F11), y la sesión espera a que termine de guardarse;
  - si es de la misma, sigue;
  - no lee ni escribe nada más en `chat.db`.
- **Q7. Borrar una** (`delete`):
  - bajo el cerrojo de la sesión;
  - si la respuesta en curso es de esa conversación, la detiene (`CANCELLED`) y espera a que termine;
  - después ejecuta `DELETE FROM conversations WHERE id = ?`, con cascada a sus turnos y fuentes;
  - las demás conversaciones, los documentos y el índice no cambian;
  - si `chat.db` lo rechaza, `DeleteConversationUseCase` devuelve `false` y la conversación sigue con todas sus preguntas; una respuesta suya que se detuvo queda `CANCELLED`;
  - escribe `chat delete outcome=ok|failed` en el registro.
- **Q8. Aviso de borrado:**
  - tras un borrado confirmado, `deletions` emite el id, después de soltar el cerrojo y con un búfer de 8;
  - el `ChatViewModel` que muestra esa conversación vuelve a un chat vacío y cierra su diálogo de borrado;
  - `deleteAll` no emite aquí, porque sigue avisando por `historyCleared` (P11 de F12).
- **Q9. Respuesta en curso:** `ObserveAnswerInProgressUseCase` emite el `conversationId` mientras `active.state` es `RETRIEVING` o `GENERATING`, y `null` en otro caso. Historial lo usa para la línea extra del diálogo (H6).
- **Q10. Continuar:**
  - preguntar en una conversación abierta desde Historial es `ask(id, pregunta)` de F11, sin cambios;
  - el turno anterior es el último completado (D-055);
  - el prompt es el mismo que con esa conversación abierta como la última (SC-004).

**Chat**
- **Q11. Petición de Historial:** `ChatViewModel.onOpenConversation(id)` hace tres cosas:
  - llama a `OpenConversationUseCase(id)`;
  - guarda la conversación en su `SavedStateHandle` (`chat_conversation`, `chat_resolved = true`);
  - cierra el diálogo de borrado.

  `onNewChat()` sigue como en F11. El borrador de la pregunta se conserva en los dos casos.
- **Q12. Fichas de documentos borrados:**
  - `chipTargets[k]` es `null` si la fuente `k` está en `DOCUMENT_GONE`;
  - en un turno terminado se miran sus fuentes guardadas, y en una respuesta en curso o sin guardar (C16), los bloques del prompt, con la misma resolución;
  - `onCitation` no hace nada con `null`;
  - la pantalla pasa esos números a `LisdataAnswerCard(unavailableCitations = …)`.

**Sesión y fuentes (arreglos de F11)**
- **Q14. Una respuesta cada vez:** `ask` y `retry`, bajo el cerrojo, esperan a que termine un trabajo que se está parando antes de empezar otro. `finish()` solo limpia `current` si sigue siendo su respuesta. Así, abrir o «Nuevo chat» y preguntar al momento no dejan la respuesta nueva como «interrumpida» (analyze AN-04).
- **Q15. Identificadores reutilizados:** una fuente cuyo documento tiene `created_at_ms` posterior a la hora de su turno es `DOCUMENT_GONE`, aunque el id exista. Es otro documento que ha recibido el mismo id (analyze AN-01).
- **Q16. Conversación restaurada:** al restaurar una conversación guardada en su `SavedStateHandle`, el chat comprueba con `ConversationExistsUseCase` que existe. Si no existe, vuelve a un chat vacío; si no se puede leer, la deja (analyze AN-03).

**Registros (constitución I)**
- **Q13:** las líneas nuevas llevan solo cifras e ids:
  - `history page rows=… total=… ms=…`, en `SqliteChatRepository.conversations`;
  - `chat delete outcome=ok|failed`, en `DefaultChatSession.delete`;
  - `history_bench …` (research R9).

  Nunca llevan títulos, preguntas ni respuestas.

## 3. Inyección (Koin)

- **`DomainModule`:**
  - `factoryOf(::ObserveConversationsUseCase)`, `factoryOf(::OpenConversationUseCase)`, `factoryOf(::ObserveConversationDeletionsUseCase)`, `factoryOf(::ObserveAnswerInProgressUseCase)` y `factoryOf(::ConversationExistsUseCase)`;
  - `DeleteConversationUseCase` sin cambios en su declaración.
- **`FeatureModule`:**
  - `viewModel { HistoryViewModel(get(), get(), get(), get()) }`: `SavedStateHandle`, `ObserveConversationsUseCase`, `DeleteConversationUseCase` y `ObserveAnswerInProgressUseCase`. El `Clock` es un parámetro con valor por defecto (`Clock.systemUTC()`, solo para el instante);
  - `ChatViewModel` gana tres dependencias: `OpenConversationUseCase`, `ObserveConversationDeletionsUseCase` y `ConversationExistsUseCase`.
- `AppModulesTest` comprueba el grafo.

## 4. Cambios en código existente

| Fichero | Cambio |
|---|---|
| `domain/chat/ChatModels.kt` | `ConversationSummary`, `ConversationPage`, `HistoryLimits`, `HistoryContent` (data-model §1, §4) |
| `domain/chat/ChatRepository.kt` | `conversations(limit)` |
| `domain/chat/ChatSession.kt` | `openConversation(id)` y `deletions` |
| `domain/chat/ChatUseCases.kt` | Los casos de uso de §1 |
| `data/chat/SqliteChatRepository.kt` | La consulta de research R1, `listChanges` (R2) y `lastConversationId` con `EXISTS` |
| `data/chat/DefaultChatSession.kt` | `openConversation`, `delete` bajo el cerrojo con `deletions`, y Q14 (R5) |
| `domain/chat/ChatUseCases.kt` (`ResolveSourcesUseCase`) | Q15 |
| `core/navigation/MicroRagAppShell.kt` | La ruta de depuración `history` abre antes la pestaña Chat (R4) |
| `feature/chat/ChatViewModel.kt` | Q11, Q8, Q12, Q16 y el resultado de `DeleteConversationUseCase` (R6) |
| `feature/chat/ChatUiState.kt` | `chipTargets: List<SourceTarget?>` y `ChatAnnouncement.DELETE_FAILED` |
| `feature/chat/ChatScreen.kt` | «Historial» en «⋯», `onOpenHistory`, `request`/`onRequestHandled` en `ChatRoute`, `unavailableCitations` y marcas de desplazamiento por conversación (R8) |
| `core/designsystem/component/Chat.kt` | `LisdataAnswerCard(unavailableCitations)` y las fichas inertes de `AnswerText` (R7) |
| `core/designsystem/component/Rows.kt` | `LisdataConversationRow` (R10) |
| `core/designsystem/component/Lists.kt` | `LoadMoreNearTheEnd`, movido desde `LibraryScreen` |
| `core/navigation/Destinations.kt`, `MicroRagNavHost.kt` | `History`, `CHAT_REQUEST` y el `composable<History>` (history-ui §5) |
| `app/src/debug/.../DebugRouting.kt` | Ruta de depuración `history` |
| `test/.../testing/ChatFakes.kt` | `InMemoryChatRepository.conversations`, y `FakeChatSession.openConversation` y `deletions` |
