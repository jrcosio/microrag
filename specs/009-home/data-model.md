# Data model: Inicio (F09)

Sin cambios de esquema: el índice sigue en la v3 (F08). Inicio solo lee.

## 1. Operación nueva del índice (`RagIndex`, R2)

| Operación | Devuelve | SQL |
|---|---|---|
| `librarySummary()` | `LibrarySummary(documents: Int, indexedChunks: Int)` | `SELECT count(*), COALESCE(SUM(CASE WHEN status = 'done' THEN chunk_count END), 0) FROM documents WHERE replaces_id IS NULL` |

- Se ejecuta en `AppDispatchers.database`, como el resto de `RagIndex`, y lanza `IndexUnavailableException` si el índice no está abierto y compatible.
- `FakeRagIndex` (pruebas JVM) la implementa con la misma regla sobre sus documentos en memoria.

## 2. Tipos de dominio

```kotlin
// domain/rag/RagModels.kt
data class LibrarySummary(val documents: Int, val indexedChunks: Int)

// domain/library/LibraryModels.kt
sealed interface SummaryResult {
    data class Loaded(val summary: LibrarySummary) : SummaryResult
    /** The index is incompatible or unreadable (F04, as LibraryResult.Unavailable). */
    data object Unavailable : SummaryResult
}

// domain/model/LocalAiStatus.kt
enum class LocalAiStatus { READY, SETUP_PENDING }
```

La regla «listo» (R1) pasa a una función de dominio que comparten `CompleteOnboardingUseCase` y `ObserveLocalAiUseCase`:

```kotlin
// domain/model/ModelCatalog.kt (o junto a él)
fun ModelCatalog.allReady(states: Map<ModelId, ModelFileState>, prepared: Map<ModelId, ComputeBackend>): Boolean =
    required.all { states[it.id] is ModelFileState.Verified && it.id in prepared }
```

## 3. Estado de la pantalla (`feature/home`)

```kotlin
data class HomeUiState(
    /** Null while the first check runs: no chip (FR-009). */
    val ai: LocalAiStatus? = null,
    val summary: SummaryState = SummaryState.Loading,
    /** Null when no run is active (D-046). */
    val indexing: IndexingCard? = null,
)

sealed interface SummaryState {
    data object Loading : SummaryState
    /** 0 documents (FR-015). */
    data object Empty : SummaryState
    data class Loaded(val documents: Int, val chunks: Int) : SummaryState
    /** [unavailable]: the index is incompatible or unreadable; otherwise the load failed. */
    data class Error(val unavailable: Boolean) : SummaryState
}

/** An active F07 run (R3): its chunks and, when paused, why. */
data class IndexingCard(val done: Int, val total: Int?, val pause: PauseReason?)
```

**Transiciones del resumen:**

```text
Loading ─▶ Empty | Loaded | Error
Empty ⇄ Loaded            (cada cambio del índice, FR-014)
Error ─(«Reintentar»)─▶ Loading ─▶ …
```

«Reintentar» vuelve a suscribir solo el flujo del resumen; el estado de la IA y la tarjeta no cambian.

**Estado de la IA:**

```text
null ─(primera comprobación)─▶ READY | SETUP_PENDING
READY ⇄ SETUP_PENDING     (al volver a primer plano o cuando la Preparación cambia los ficheros)
```

## 4. Logs (constitución I)

Solo cifras e ids, nunca nombres ni rutas:
- `home ai=READY|SETUP_PENDING in N ms`: la primera comprobación tras abrir Inicio;
- `home summary documents=N chunks=M in N ms`: la primera carga del resumen;
- `home summary failed: <clase de la excepción>`.
