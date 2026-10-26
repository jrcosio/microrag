# Data model — F07 Indexación

## 1. Índice: sin cambio de esquema

El esquema sigue en v2 (D-038). F07 usa columnas y estados que ya existen desde F04.

### Uso de `documents` en F07

| Columna | Uso |
|---|---|
| `status` | `pending → parsed → embedding → done`, o `error` (§4). `paused` y `cancelled` (D-030) no se usan: la pausa es de la tanda (§2), no del documento (D-040) |
| `chunk_count` | La fija la fase 1 al trocear (como F06). Es el total del documento para el progreso y para comprobar el cierre |
| `error` | Código de `IngestError`, incluidos los nuevos de §3 |
| `included` | 1 al registrarse (F04). F07 no lo cambia; las búsquedas lo filtran (R7) |
| `display_name` | Origen del título de la cabecera contextual (`DocumentTitle`, R11) |

### Uso de `chunks` en F07

| Columna | Valor |
|---|---|
| `document_id`, `ordinal` | Únicos juntos. Ordinales 0..n-1, consecutivos y en el orden del `Chunker` |
| `heading_path`, `page`, `content` | Del `Chunk`, sin cabecera contextual |
| `title_norm` | `SpanishText.normalize(DocumentTitle.of(display_name) › heading_path)` (cambio de R11) |
| `body_norm` | `SpanishText.normalize(content)` |
| `token_estimate` | `ceil(longitud UTF-16 / RagParameters.CHARS_PER_TOKEN)`, como `offline/eval/chunker.py` |
| `embedding` | 256 `float32` LE con norma 1 |

**Visibilidad (R7):** un fragmento es buscable si y solo si su documento tiene `status = 'done'` e `included = 1`.

## 2. Tanda de indexación (`IngestRunStore`, DataStore)

Fichero: `noBackupFilesDir/datastore/ingest_run.preferences_pb`.

| Clave | Tipo | Significado |
|---|---|---|
| `document_ids` | `String` (ids separados por comas, en orden) | Documentos que el usuario mandó indexar y que pertenecen a la tanda actual |
| `pause_cause` | `String?` | `user`, `no_space` o `model_missing`. Si existe, la tanda está pausada y no debe programarse trabajo |
| `gpu_check` | `String?` | Resultado de la validación de la GPU (research R4): `<sha256 del modelo>|<versionCode>|ok` o `…|cpu`. Se repite si cambia el modelo o la app |

**Reglas:**
- **Altas:** «Iniciar indexación» y «Reintentar» añaden ids. «Iniciar indexación» o «Reanudar» con la tanda pausada la reanudan.
- **Bajas:** «Quitar de la cola» borra el id.
- **Vaciado:** la tanda se vacía cuando ninguno de sus documentos está `pending`, `parsed` ni `embedding`.
- **Pausas transitorias:** la temperatura y el límite del sistema no se guardan aquí (R8).

## 3. Tipos de dominio

### `domain/ingest` (ampliación de F06)

```kotlin
enum class IngestError(...) {
    // F06 sin cambios, más:
    EMBEDDING_FAILED("embedding_failed", true),   // el engine falló dos veces (R4) o un texto no cupo tras 12 recortes (R6)
    INCOMPLETE("incomplete", true),               // al cerrar faltan fragmentos (R9)
}

sealed interface QueueItemState {
    // F06 sin cambios, más:
    /** Calculating embeddings: [done] stored chunks of [total]. */
    data class Indexing(val done: Int, val total: Int) : QueueItemState
    /** In a paused run (by the user or automatically). */
    data class Paused(val done: Int?, val total: Int?) : QueueItemState
}
```

`IngestProgress` gana:
- las etapas `EMBEDDING` y `CLOSING` (con `EXTRACTING` y `CHUNKING` de F06);
- `chunksDone` y `chunksTotal`;
- `chunksPerSecond: Double?` (null hasta el primer lote o si no es estable);
- `backend: ComputeBackend`;
- `waiting: WaitReason?`, aparte de la etapa, que solo puede ser `THERMAL`. Así la pantalla sigue mostrando la etapa real del documento durante la espera.

### `domain/indexing` (nuevo)

```kotlin
/** What the Index screen shows (contracts/index-ui.md). */
sealed interface IndexingState {
    data object Loading : IndexingState
    data object IndexUnavailable : IndexingState
    /** Nothing to index; [failed] lists the documents in error, if any. */
    data class Idle(val failed: List<FailedDocument>) : IndexingState
    data class Active(
        val progress: RunProgress,
        val current: CurrentDocument?,        // null between documents or before the first one
        val queuedDocuments: Int,             // documents of the run still to start, excluding the current one
        val pause: PauseReason?,              // null while running
        val failed: List<FailedDocument>,
    ) : IndexingState
}

/** [total] is null while some document of the run is still pending (research R8). */
data class RunProgress(val done: Int, val total: Int?, val remainingSeconds: Long?)

data class CurrentDocument(val id: Long, val displayName: String, val stage: Stage) {
    enum class Stage { EXTRACTING, CHUNKING, EMBEDDING, CLOSING }
}

enum class PauseReason { USER, THERMAL, NO_SPACE, MODEL_MISSING, SYSTEM_LIMIT }

data class FailedDocument(val id: Long, val displayName: String, val error: IngestError) {
    val canRetry: Boolean get() = error.retryable
}
```

**Etapas del PNG (G29)** para `CurrentDocument`:

| `Stage` | Extracción de texto | Fragmentación | Generación de embeddings | Persistencia vectorial |
|---|---|---|---|---|
| `EXTRACTING` (fase 1) | En curso | Pendiente | Pendiente | Pendiente |
| `CHUNKING` (fase 1) | Completado | En curso | Pendiente | Pendiente |
| `EMBEDDING` (fase 2) | Completado | Completado | En curso | Pendiente |
| `CLOSING` (cierre del documento) | Completado | Completado | Completado | En curso |

En la fase 2 la extracción y el troceado se repiten en *streaming*, pero el documento ya los tiene hechos (`parsed`), así que se muestran completados.

### `domain/rag` (ampliación de F04)

```kotlin
object DocumentTitle {
    /** «Manual técnico.pdf» → «Manual técnico» (research R11). */
    fun of(displayName: String): String
}
```

### `rag/runtime` (ampliación de F02)

```kotlin
/** D-036, exact port of offline/eval/builds.py (lengths in code points, research R6). */
object EmbeddingOverflow {
    const val MAX_ATTEMPTS = 12   // as RETRIES in builds.py
    fun cut(text: String): String
}

/** A document chunk's vector and how many D-036 cuts it needed. */
class DocumentEmbedding(val vector: FloatArray, val cuts: Int)

/** The input still did not fit after MAX_ATTEMPTS. */
class EmbeddingInputTooLongException : RuntimeException()

/** Any other engine failure (research R4: reload once, then CPU, then document error). */
class EmbeddingEngineException(cause: Throwable) : RuntimeException(cause)
```

### `domain/rag` (excepción nueva)

```kotlin
/** The index could not write because the device is out of space (SQLITE_FULL, research R10). */
class IndexStorageFullException : IllegalStateException()
```

## 4. Máquinas de estados

### Documento

```text
pending ──(fase 1)──► parsed ──(fase 2)──► embedding ──(todos guardados)──► done
   │                    │                    │
   └──► error ◄─────────┴────────────────────┘   (código; los reintentables vuelven a pending)
```

- **`error` en la fase 1:** los de F06 (`no_text`, `encrypted`…).
- **`error` en la fase 2:** `embedding_failed`, `incomplete` o `extraction_failed`.
- **Reintentar:** pone `pending`. La fase 1 vuelve a contar los fragmentos; la fase 2 reutiliza los guardados que coincidan (R9).
- **Quitar:** borra el documento con sus fragmentos y su copia, en cualquier estado salvo `done`. Los `done` son de F08.
- **Pausa, temperatura, falta de espacio o muerte del proceso:** el documento se queda en su estado (`parsed` o `embedding`), y la reanudación sigue desde ahí.

### Tanda

```text
vacía ──Iniciar──► en curso ──Pausar / sin espacio / modelo ausente──► pausada ──Reanudar / Iniciar──► en curso
                    │   ▲                                                                              
                    │   └── temperatura normal / el sistema lo permite ◄── pausa automática (temperatura, límite del sistema)
                    └──(sin documentos sin terminar)──► vacía
```

## 5. Progreso del trabajo (`WorkInfo.progress`)

Las claves de F06 (`doc`, `stage`, `page`, `pages`), más:

| Clave | Tipo | Valor |
|---|---|---|
| `stage` | `String` | Nuevos valores: `embedding` y `closing` |
| `waiting` | `String?` | `thermal` durante la espera térmica; ausente si no |
| `chunks_done` | `Int` | Fragmentos guardados del documento actual |
| `chunks_total` | `Int` | `chunk_count` del documento actual |
| `rate` | `Double` | Fragmentos por segundo de los dos últimos lotes, si es estable (R8); `-1` si no |
| `backend` | `String` | `gpu` o `cpu` |

## 6. Notificación

| Elemento | Valor |
|---|---|
| Canal | `indexing`, importancia baja |
| Id | `IndexingNotifications.NOTIFICATION_ID` (fijo, distinto del de las descargas) |
| Texto | «Indexando documentos» y «N de M fragmentos», o la causa de la pausa automática. Sin nombres de archivo |
| Acción | «Pausar», con un `PendingIntent` explícito e inmutable a `IndexingActionReceiver` (no exportado) |
| Al tocarla | `MainActivity` con la acción interna `ACTION_OPEN_INDEXING`, que abre `Index` |

## 7. Causa de pausa en el estado de la pantalla

| Condición (en este orden) | `PauseReason` |
|---|---|
| La tanda tiene `pause_cause` | Esa causa: `USER`, `NO_SPACE` o `MODEL_MISSING` |
| Temperatura ≥ `SEVERE` con la tanda activa (el worker espera, o el sistema lo ha parado) | `THERMAL` |
| Trabajo `ENQUEUED` con `runAttemptCount > 0` y `stopReason` del sistema (`QUOTA`, `TIMEOUT`, `FOREGROUND_SERVICE_TIMEOUT`, `DEVICE_STATE`, `APP_STANDBY`, `BACKGROUND_RESTRICTION` u otro distinto de `CANCELLED_BY_APP`) | `SYSTEM_LIMIT` («Esperando al sistema») |
| Si no | Ninguna (en curso) |

