# Contrato: recuperación de contexto (F10)

Capas (constitución II):
- **quien llama:** el diagnóstico de depuración ahora, y el chat en F11;
- `RetrieveContextUseCase` (`domain/retrieval`);
- `ContextRetriever` (interfaz);
- `HybridRetriever` (`data/retrieval`), que usa:
  - `RagIndex` (F04);
  - `QueryEmbedder` (`data/retrieval`), con `EngineCoordinator`, `EngineFactory` y `ModelFileRepository`;
  - los algoritmos puros de `rag/retrieval`.

Quien llama no importa SQLite, LiteRT ni nada de `data/`.

## Dominio

```kotlin
// domain/retrieval
interface ContextRetriever {
    /** Never throws but CancellationException: every failure is an outcome (FR-016). */
    suspend fun retrieve(request: RetrievalRequest): RetrievalResult
}

class RetrieveContextUseCase(private val retriever: ContextRetriever) {
    suspend operator fun invoke(question: String, previousQuestion: String? = null): RetrievalResult =
        retriever.retrieve(RetrievalRequest(question, previousQuestion))
}
```

Tipos en data-model §2–§3.

## Algoritmos puros (`rag/retrieval`)

```kotlin
object FollowUpRule {
    val CONNECTORS = listOf("y ", "¿y ", "pero ", "entonces", "¿y si", "y si ")
    const val MAX_TOKENS = 8
    fun isFollowUp(question: String): Boolean
    fun searchText(question: String, previous: String?, enabled: Boolean = RagParameters.FOLLOW_UP_RULE): String
}

object ReciprocalRankFusion {
    /** Every id of [lists], best first: score desc, then best single rank, then smaller id (R2). */
    fun fuse(lists: List<List<Long>>, k: Int = RagParameters.RRF_K): List<FusedCandidate>
}

class ContextSelector(
    private val maxBlocks: Int = RagParameters.MAX_BLOCKS,
    private val maxPerDocument: Int = RagParameters.MAX_PER_DOCUMENT,
    private val budgetTokens: Int = RagParameters.BUDGET_TOKENS,
    private val expandTop: Int = RagParameters.EXPAND_TOP,
) {
    /** [ranked] in fusion order; [neighbor] loads the chunk at (document, ordinal) or null. */
    suspend fun select(ranked: List<ChunkView>, neighbor: suspend (Long, Int) -> ChunkView?): List<ContextBlock>

    companion object {
        fun mergeWithoutOverlap(a: String, b: String): String
    }
}
```

## Datos (`data/retrieval`)

```kotlin
class QueryEmbedder(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val models: ModelFileRepository,
) {
    /** Verified and prepared (F03 rule), after one refresh() without hashing when the path is unknown (R4). */
    suspend fun ready(): Boolean

    /** CPU engine (D-024), loaded on demand and shared with the ingest's (R5). Throws on any engine failure. */
    suspend fun embed(question: String, previous: String?): QueryEmbedding

    /** Whether the CPU query engine is loaded now (for RetrievalTimings.coldEngine). */
    val loaded: Boolean
}

class HybridRetriever(
    private val index: RagIndex,
    private val embedder: QueryEmbedder,
    private val logger: AppLogger,
    private val clock: () -> Long = System::nanoTime,
) : ContextRetriever
```

## Cambios en contratos existentes

```kotlin
interface RagIndex {
    // … F04–F09 …
    /** With [searchableOnly], only chunks of documents that are done and included (F07 rule N7, F10 R7). */
    suspend fun loadChunks(ids: Collection<Long>, searchableOnly: Boolean = false): Map<Long, ChunkView>
    suspend fun neighbor(documentId: Long, ordinal: Int, searchableOnly: Boolean = false): ChunkView?
}

interface TextEmbedder : ManagedEngine {
    // … F02–F07 …
    /**
     * Embeds the search text of [question] (with [previous] first, when given) with the query prefix.
     * When the model rejects it as too long, cuts [previous] first and then [question] with the D-036
     * rule (QueryOverflow, R4). Throws EmbeddingInputTooLongException after EmbeddingOverflow.MAX_ATTEMPTS
     * attempts and EmbeddingEngineException on any other failure.
     */
    suspend fun embedQueryFitting(question: String, previous: String?): QueryEmbedding =
        QueryEmbedding(embedQuery(FollowUpRule.join(previous, question)), cuts = 0)
}

class EngineCoordinator {
    // withEngine, withHeldEngine, release, releaseAll, releaseUnheld, releaseGenerators: same signatures.
    // Embedding engines can be used by several blocks at once (R5); the LLM stays exclusive.
}
```

`FollowUpRule.join(previous, question)` es la unión con un espacio. `searchText` decide si se une; la unión es la misma.

## Reglas

- **Q1 — Orden de las comprobaciones:**
  1. pregunta vacía;
  2. índice (`open()` compatible);
  3. modelo listo;
  4. búsquedas.

  Un estado sin búsqueda nunca carga el motor (FR-017, FR-018).
- **Q2 — Paridad con el escritorio:**
  - seguimiento, texto de búsqueda, fusión y selección idénticos a `offline/eval/retrieval.py` y `selector.py`;
  - lo comprueban los casos portados (SC-001) y `retrieval_golden.jsonl` (R3).
- **Q3 — Parámetros:** todos los valores numéricos salen de `RagParameters` (FR-006).
- **Q4 — Solo contenido buscable:**
  - las búsquedas filtran `done` e incluido (D-040);
  - la carga de candidatos y vecinos usa `searchableOnly = true` (FR-015).
- **Q5 — Embedding en CPU:**
  - la consulta usa siempre `EngineKey(EG2_270M, CPU)` (D-024);
  - nunca carga un motor en GPU;
  - nunca se retiene (`withEngine`, no `withHeldEngine`), así que `releaseUnheld` lo libera con la UI oculta o con poca memoria (FR-021).
- **Q6 — Sin esperar a la tanda:**
  - una recuperación durante la indexación espera, como mucho, al fragmento que se está embebiendo (si comparten motor o hilo) y al lote de escritura en curso;
  - nunca espera al final de la tanda (FR-020, R5).
- **Q7 — Léxica en paralelo:**
  - la búsqueda léxica empieza a la vez que el embedding;
  - la vectorial, cuando hay vector;
  - el resultado no depende de este orden.
- **Q8 — Recorte de la consulta:**
  - solo se recorta el texto embebido, primero la pregunta anterior (R4);
  - la léxica usa el texto de búsqueda completo;
  - `embeddingCuts` lo informa.
- **Q9 — Sin excepciones hacia quien llama:**
  - todo fallo es un `RetrievalOutcome` (FR-016): `IndexUnavailableException` u otro fallo del índice → `IndexUnavailable`; fallo del motor → `EmbeddingFailed`;
  - `CancellationException` se propaga.
- **Q10 — Sin estado:** `HybridRetriever` no guarda nada entre llamadas. Dos llamadas simultáneas no se mezclan (FR-022).
- **Q11 — Registros sin texto:**
  - una línea `retrieval …` por llamada, con resultado, recuentos y tiempos (research R8);
  - nunca la pregunta, el texto de búsqueda, el texto de los fragmentos ni nombres (FR-030).
- **Q12 — Sin red:** nada de este contrato usa red (FR-029; `NetworkConfinementTest` sigue vigente).
- **Q13 — Motores compartidos** (`EngineCoordinator`, R5):
  - **embeddings:** varios bloques pueden usar la misma entrada a la vez;
  - **LLM:** exclusivo;
  - **cierre:** nunca mientras haya usuarios;
  - **retención:** una entrada retenida por la ingesta sigue retenida aunque termine otro usuario;
  - **GPU:** nunca dos motores en GPU;
  - **`loadLock`:** nadie espera a otro bloque con él tomado.
- **Q14 — Citas reales:**
  - cada bloque lleva los ids de los fragmentos que cubre, y su documento, ordinales, ruta y página salen de esos fragmentos;
  - el texto es el contenido guardado, sin cabecera contextual (constitución III).
