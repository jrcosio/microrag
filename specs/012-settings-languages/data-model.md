# Data model: Ajustes e Idiomas (F12)

Fecha: 2026-11-01. Fuentes:
- la spec aclarada (D-058), con las propuestas S1–S11 aprobadas;
- research R1–R16;
- los tipos existentes de F03 (modelos y Preparación) y F11 (chat), que se reutilizan sin cambios salvo donde se indica.

## 1. Preferencias de idioma (`domain/settings`)

```kotlin
/** Language of the app's texts (D-029): Spanish unless the user picks English. */
enum class AppLanguage(val tag: String) { SPANISH("es"), ENGLISH("en") }

/** Language of the chat's answers (D-058): Spanish by default; AUTOMATIC follows the question. */
enum class AnswerLanguage(val key: String) { SPANISH("es"), AUTOMATIC("auto") }

/** What Idiomas shows; [readFailed] when the answer language could not be read and the default is used (S8). */
data class LanguageSettings(
    val app: AppLanguage,
    val answer: AnswerLanguage,
    val readFailed: Boolean = false,
)

sealed interface SaveResult {
    /** [appLanguageChanged]: the screen must be recreated (Android 12) or will be (Android 13+). */
    data class Saved(val appLanguageChanged: Boolean) : SaveResult
    data object Unchanged : SaveResult
    data object Failed : SaveResult
}
```

**Dónde se guarda cada valor** (R2, R3; constitución I):

| Valor | Android 13+ | Android 12 | Por defecto |
|---|---|---|---|
| Idioma de la app | `LocaleManager.applicationLocales`, que guarda el sistema | `noBackupFilesDir/settings/app_language`, una línea `es` o `en` | `SPANISH`, si está vacío o falta |
| Idioma de las respuestas | DataStore `noBackupFilesDir/datastore/settings.preferences_pb`, clave `answer_language` (`es` o `auto`) | igual | `SPANISH`, si falta, es desconocido o falla la lectura |

**Interfaces:**

```kotlin
/** The answer language on disk (data/settings, R3). */
interface LanguageSettingsStore {
    /** Never fails: a read error emits the default with readFailed = true. */
    val answerLanguage: Flow<Pair<AnswerLanguage, Boolean>>
    /** False when the write failed; the previous value stays. */
    suspend fun saveAnswerLanguage(language: AnswerLanguage): Boolean
}

/** The app language on the platform (core/locale, R2). */
interface AppLanguageController {
    fun current(): AppLanguage
    /** False when it could not be stored (Android 12 file write). */
    suspend fun apply(language: AppLanguage): Boolean
    /** True below Android 13: the screen calls recreate() after applying. */
    val recreatesItself: Boolean
}

/** One-shot notices the app shell announces after a recreation (R4, S9). */
class SettingsNotices { val saved: MutableStateFlow<Boolean> }
```

## 2. Modelos en Ajustes (`domain/settings`)

```kotlin
/** A model row and its sheet (R11): catalog data plus the Preparation's real state. */
data class ModelOverview(
    val id: ModelId,                 // GEMMA4_E2B or EG2_270M (ModelCatalog.required)
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,              // published hash, the one the file was verified with (D-011)
    val dimensions: Int?,            // RagParameters.DIMS (256) for EG2_270M; null for Gemma
    val status: ModelSetupStatus,    // the same as the Preparation (F03); Ready carries the backend
)
```

- `ObserveModelOverviewUseCase(): Flow<List<ModelOverview>>`, en el orden de la Preparación: respuestas y después embeddings.
- **Chip (S1):**
  - `Ready` muestra «Local», salvo `GEMMA4_E2B` con `backend == CPU`, que muestra «Modo CPU, más lento»;
  - el resto, como en la Preparación.
- **Ficha (S2):**
  - el SHA-256 se muestra con sus 12 primeros caracteres y «…»;
  - el procesador sale de `Ready.backend`, o es «—»;
  - «Completar configuración» aparece si `status !is Ready`.

## 3. Chat (cambios sobre F11)

**`ChatSession`** (`domain/chat`), dos miembros nuevos:

```kotlin
/** Bumped by every deleteAll(); the chat screen drops the conversation it shows (R10). */
val historyCleared: StateFlow<Long>

/** Stops the answer in progress, then deletes every conversation (FR-012). Throws ChatStorageException. */
suspend fun deleteAll()
```

**`ChatRepository`**, dos funciones nuevas:

```kotlin
/** Conversations with at least one turn (S6: «No hay conversaciones guardadas.»). */
suspend fun conversationCount(): Int

suspend fun conversationExists(id: Long): Boolean
```

**`DefaultChatSession`:**
- recibe `answerLanguage: () -> Flow<AnswerLanguage>` y lo lee al empezar cada respuesta (R5);
- `ask(id, …)` crea una conversación nueva si `id` ya no existe (R10).

**Casos de uso nuevos:**
- `CountConversationsUseCase`;
- `ClearChatHistoryUseCase`;
- `ObserveHistoryClearedUseCase`.

`chat.db` no cambia de esquema: sigue en v1.

## 4. Prompt (`rag/answer`)

```kotlin
enum class PromptLanguage(internal val inPrompt: String) {
    SPANISH("español"),
    AUTOMATIC("el idioma de la PREGUNTA"),
}

const val REFUSAL_EN = "I can't find that information in the available documents."
```

- **`AnswerLanguage` a `PromptLanguage`:** `SPANISH` → `SPANISH` y `AUTOMATIC` → `AUTOMATIC`. La traducción la hace `data/chat`.
- **`AnswerReading.isRefusal`:** acepta `REFUSAL` o `REFUSAL_EN` tras `norm`, igual que `judge.is_refusal`.

## 5. Estado de las pantallas (`feature/settings`)

```kotlin
data class SettingsUiState(
    val models: List<ModelRowUi> = emptyList(),   // empty until the first emission: the rows show no state yet
    val appLanguage: AppLanguage = AppLanguage.SPANISH,   // subtitle of «Idiomas» (S3)
    val openSheet: ModelId? = null,               // SavedStateHandle
    val confirmClear: ConfirmClear? = null,       // SavedStateHandle
    val clearing: Boolean = false,
    val message: SettingsMessage? = null,         // shown once in the snackbar and announced
)

data class ConfirmClear(val stopsAnswer: Boolean)   // S6: adds «La respuesta en curso se detendrá.»

enum class SettingsMessage { HISTORY_CLEARED, NO_CONVERSATIONS, CLEAR_FAILED }   // snackbar + TalkBack

data class LanguagesUiState(
    val saved: LanguageSettings? = null,          // null while loading
    val selectedApp: AppLanguage? = null,         // SavedStateHandle with its starting value; follows a system change if untouched (P19)
    val selectedAnswer: AnswerLanguage? = null,   // SavedStateHandle; starts as saved.answer
    val saving: Boolean = false,
    val saveFailed: Boolean = false,              // S8
    val automaticOffered: Boolean = true,         // false only if the evaluation did not pass (FR-024)
)

sealed interface LanguagesEvent { data class Close(val recreate: Boolean) : LanguagesEvent }
```

`automaticOffered` es una constante de la app (`LanguageOptions.AUTOMATIC_OFFERED`), que fija la decisión de la evaluación (R7). No es una preferencia del usuario.

## 6. Escritorio (`offline/`)

**`EvalConfig`:**
- campo nuevo `answer_language`, con valor por defecto `"es"`;
- está en `LATE_FIELDS`: con `"es"` se omite del JSON de la huella, así que las ejecuciones anteriores conservan la suya.

**`prompt.py`:**
- `LANGUAGES = {"es": "español", "auto": "el idioma de la PREGUNTA"}`;
- `build_prompt(…, language="es")`.

**`judge.py`:**
- `REFUSAL_EN`;
- `is_refusal` acepta las dos frases.

**Corpus en inglés** (`offline/corpus/en/`):
- `manifest.json` con los 13 campos de `corpus.FIELDS` y la licencia `CC BY-SA 4.0`;
- `docs/`;
- `SOURCES.md`, generado.

**Conjuntos** (`offline/testset/crosslang/`), con los mismos campos que `questions.jsonl` más `source_id`, el id de la pregunta española de origen o null:

| Fichero | Documentos | Preguntas | Composición |
|---|---|---|---|
| `es-docs.en.jsonl` | español (`ref`) | inglés | 72: muestra de F10 (40 + 10), las 7 anteriores con respuesta y 15 sin respuesta (las 2 anteriores y 13 con semilla) |
| `en-docs.en.jsonl` | inglés (`en`) | inglés | 40 con respuesta, 10 seguimientos y 15 sin respuesta |
| `en-docs.es.jsonl` | inglés (`en`) | español | las mismas, traducidas |

**Índice:** `build/index/en.db`, con los parámetros de troceado de `rag_parameters.json` (R8).

**Informes:**
- `reports/language-review.csv`, con las columnas de R7;
- `reports/crosslang-review.csv`, con las columnas de R9;
- `build/language-review/result.json` y `build/crosslang-review/result.json`: cifras por combinación, `meets_rule` y `meets_thresholds`.
