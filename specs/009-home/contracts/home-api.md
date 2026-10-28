# Contrato: datos y casos de uso de Inicio (F09)

Capas (constitución II): `feature/home` → casos de uso (`domain/onboarding`, `domain/library`, `domain/indexing`) → `ModelFileRepository` (F03), `LibraryRepository` (F08) e `IndexingRepository` (F07) → `RagIndex` (F04). La UI no importa SQLite, WorkManager, LiteRT ni `java.io.File`, ni nada de `data/`.

## Casos de uso

```kotlin
// domain/onboarding: nuevos (R1)
class ObserveLocalAiUseCase(private val repository: ModelFileRepository) {
    /** refresh() without hashing, then READY or SETUP_PENDING whenever the files or backends change. */
    operator fun invoke(): Flow<LocalAiStatus>
}

class RefreshLocalAiUseCase(private val repository: ModelFileRepository) {
    /** Back to the foreground (R5): rescans without hashing; ObserveLocalAiUseCase emits the change. */
    suspend operator fun invoke()
}

// domain/library: nuevo (R2)
class ObserveLibrarySummaryUseCase(private val repository: LibraryRepository) {
    operator fun invoke(): Flow<SummaryResult> = repository.summary()
}

// domain/indexing: existente (F07)
class ObserveIndexingUseCase(...)   // Flow<IndexingState>
```

## Cambios en repositorios e índice

```kotlin
interface LibraryRepository {
    // … F08 …
    /** Counts of the whole library, again on every index change (R2). */
    fun summary(): Flow<SummaryResult>
}

interface RagIndex {
    // … F04–F08 …
    /** All documents without shadows, and the chunks of the done ones (R2). One query on `documents`. */
    suspend fun librarySummary(): LibrarySummary
}
```

`CompleteOnboardingUseCase` pasa a usar `ModelCatalog.allReady(...)`, sin cambio de comportamiento (data-model §2).

## Reglas

- **H1 — Nunca «disponible» sin comprobar:** el ViewModel no muestra chip hasta la primera emisión de `ObserveLocalAiUseCase`, que siempre llega después de un `refresh()`.
- **H2 — Sin huellas:** Inicio nunca llama a `verify`, `installVerified`, `adopt`, `ReconcileDownloadsUseCase` ni `PrepareModelsUseCase` (F03 SC-007).
- **H3 — Misma regla que el onboarding:** «IA local disponible» ⇔ `ModelCatalog.allReady(states, preparedBackends)`.
- **H4 — Cifras sin listas:** el resumen sale de una consulta agregada sobre `documents`; nunca lee `chunks` ni carga documentos (FR-013).
- **H5 — Cifras coherentes:** `documents` = `libraryCount("", todos los estados)`. `indexedChunks` = filas de `chunks` de los documentos `done` sin sombra (SC-004).
- **H6 — Reactividad:** el resumen se recalcula con cada `documentChanges`; la tarjeta, con cada emisión de `IndexingRepository.state`; el estado de la IA, con `states` y `preparedBackends`.
- **H7 — Errores aislados:** un fallo del resumen solo afecta a su tarjeta; «Reintentar» solo vuelve a suscribir el resumen (FR-016).
- **H8 — Fuera del hilo principal:** el índice en `AppDispatchers.database` y `refresh()` en `io`, como ya hacen.

## Cambios en F01, F03, F06 y F07

- **F01 (sistema de diseño):**
  - `LisdataHeroCard(decorationIcon: Int? = null)`: icono en el centro de las órbitas, oculto a TalkBack (R6);
  - `LisdataProgressCard(onClick: (() -> Unit)? = null, onClickLabel: String? = null)`: la tarjeta entera tocable (R3).
- **F03:**
  - `SetupRoute.onCompleted` vuelve a Inicio sin duplicarlo (R4);
  - `CompleteOnboardingUseCase` usa `allReady`.
- **F06 y F07:** se retiran los accesos provisionales de Inicio y sus textos (R4).
