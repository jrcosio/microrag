# Modelo de datos: Recuperación híbrida (F10)

F10 no cambia el esquema del índice (v3, F08). Añade:
- tipos en memoria;
- un parámetro compartido;
- dos ficheros golden;
- un cambio en el estado interno del coordinador de motores.

## 1. Parámetros (`RagParameters` y `offline/config/rag_parameters.json`)

| Campo JSON | Kotlin | Valor | Origen |
|---|---|---|---|
| `candidates` | `CANDIDATES` | 50 | D-034 |
| `rrf_k` | `RRF_K` | 60 | D-034 |
| `follow_up_rule` | `FOLLOW_UP_RULE` | true | D-034 |
| `fused_limit` | `FUSED_LIMIT` | 30 | **nuevo** (R2): el `[:30]` del harness |
| `max_blocks` | `MAX_BLOCKS` | 4 | D-034 |
| `max_per_document` | `MAX_PER_DOCUMENT` | 3 | D-034 |
| `budget_tokens` | `BUDGET_TOKENS` | 1719 | D-034 |
| `expand_top` | `EXPAND_TOP` | 0 | D-034 |

`RagParametersTest` sigue comparando los dos ficheros campo a campo. `offline/eval/retrieval.py` toma `FUSED_LIMIT` del JSON.

## 2. Petición y texto de búsqueda (`domain/retrieval`)

```kotlin
data class RetrievalRequest(
    val question: String,
    /** Given by the caller (F11); the retriever keeps no conversation (FR-009). */
    val previousQuestion: String? = null,
)
```

- **Pregunta vacía:** `question.isBlank()`.
- **Seguimiento:** `FollowUpRule.isFollowUp(question)` (R2).
- **Texto de búsqueda:**
  - con `FOLLOW_UP_RULE`, una anterior no vacía y un seguimiento: `"$previousQuestion $question"`;
  - en otro caso, `question`.
- **Texto embebido:** el texto de búsqueda con la regla `QueryOverflow` si el modelo lo rechaza por largo (R4). La búsqueda léxica recibe siempre el texto de búsqueda completo.

## 3. Resultado (`domain/retrieval`)

```kotlin
data class RetrievalResult(
    val searchText: String,
    val followUp: Boolean,
    /** Cuts applied to the embedded text (R4); 0 when it fitted. */
    val embeddingCuts: Int,
    val timings: RetrievalTimings,
    val outcome: RetrievalOutcome,
)

sealed interface RetrievalOutcome {
    data class Blocks(val blocks: List<ContextBlock>, val candidates: List<CandidateScore>) : RetrievalOutcome
    data object NoResults : RetrievalOutcome
    data object EmptyQuestion : RetrievalOutcome
    data class IndexUnavailable(val state: IndexCompatibility) : RetrievalOutcome
    data object ModelsNotReady : RetrievalOutcome
    data object EmbeddingFailed : RetrievalOutcome
}

data class ContextBlock(
    val documentId: Long,
    val documentName: String,
    val headingPath: String?,   // of the first chunk
    val page: Int?,             // of the first chunk
    val firstOrdinal: Int,
    val lastOrdinal: Int,
    val chunkIds: List<Long>,   // in ordinal order
    val text: String,           // original content, merged without the repeated paragraph; no contextual header
    val tokens: Int,            // sum of the chunks' token estimates
)

data class CandidateScore(
    val chunkId: Long,
    val fusedRank: Int,         // 1-based, in the fusion order
    val rrfScore: Double,
    val vectorRank: Int?,       // 1-based; null when not in the vector list
    val vectorDistance: Double?,
    val textRank: Int?,         // 1-based; null when not in the text list
    val selected: Boolean,      // its chunk is in a block
)

data class RetrievalTimings(
    val embeddingMs: Double,    // includes loading the engine when coldEngine
    val vectorMs: Double,
    val textMs: Double,
    val selectionMs: Double,    // fusion, loading the candidates and selection
    val totalMs: Double,        // wall clock; less than the sum, the text search overlaps the embedding
    val coldEngine: Boolean,
)
```

**Reglas de validación:**
- `Blocks.blocks` no está vacía, tiene como mucho `MAX_BLOCKS` bloques y va ordenada por (`documentId`, `firstOrdinal`).
- Ningún documento aporta más de `MAX_PER_DOCUMENT` fragmentos.
- La suma de `tokens` es ≤ `BUDGET_TOKENS`.
- `candidates` son los `FUSED_LIMIT` primeros de la fusión (o menos).
- **Estados sin búsqueda:**
  - en `EmptyQuestion`, `IndexUnavailable` y `ModelsNotReady`, los tiempos de los pasos que no se ejecutaron valen 0;
  - en `EmptyQuestion`, `searchText` es la pregunta tal cual y `followUp` es `false`.

**Transiciones:** no hay estado persistente. Cada llamada recorre el flujo de research R6 y termina en un único `RetrievalOutcome`.

## 4. Candidato fusionado (`rag/retrieval`)

```kotlin
data class FusedCandidate(val id: Long, val score: Double, val bestRank: Int, val ranks: List<Int?>)
```

`ranks[i]` es el rango en la lista `i` (vector, léxica), o `null`. El orden es el de R2. `HybridRetriever` lo convierte en `CandidateScore` añadiendo la distancia vectorial y la marca de elegido.

## 5. Cambios en interfaces existentes

| Interfaz | Cambio | Regla |
|---|---|---|
| `RagIndex.loadChunks(ids, searchableOnly = false)` | Con `true`, solo fragmentos de documentos `done` e incluidos | R7, FR-015 |
| `RagIndex.neighbor(documentId, ordinal, searchableOnly = false)` | Ídem | R7 |
| `TextEmbedder.embedQueryFitting(question, previous): QueryEmbedding` | Nuevo; por defecto une y llama a `embedQuery`. `LiteRtTextEmbedder` recorta con `QueryOverflow` | R4 |
| `EngineCoordinator` | Uso compartido de los motores de embeddings; recuentos de usuarios y de retenciones | R5 |

```kotlin
class QueryEmbedding(val vector: FloatArray, val cuts: Int)
```

**Estado de una entrada del coordinador:**

| Campo | Antes | Ahora |
|---|---|---|
| Uso | `useLock: Mutex` (exclusivo) | LLM: `Mutex`. Embeddings: `users`, un contador atómico (≥ 0). Sube en `acquire`, bajo el `loadLock`; baja al terminar el bloque **sin** tomar el `loadLock` y avisa por `changes` |
| Retención | `held: Boolean` | `holds: Int`: la ingesta suma 1 al empezar y resta 1 al terminar |
| En uso | `useLock.isLocked` | LLM: `isLocked`. Embeddings: `users > 0` |

**Cierre** (`closeLocked`), como hoy:
- se ejecuta con el `loadLock` tomado y espera a que la entrada no esté en uso (`users == 0`, o el `Mutex` libre en el LLM); después la cierra y la quita;
- por eso un bloque que termina nunca toma el `loadLock` para bajar `users`: si lo hiciera, se bloquearía con el cierre que le espera;
- mientras dura el cierre nadie entra en `acquire`, que necesita el `loadLock`. Los usuarios nuevos cargan otra entrada después.

## 6. `offline/golden/retrieval_golden.jsonl`

- **Contenido:** una línea JSON por pregunta viva del conjunto (`review` distinto de `discarded`), ordenadas por `id`.
- **Generador:** `offline/gen_retrieval_golden.py` (`--check` lo valida sin escribir).

```json
{
  "id": "q0001",
  "question": "…", "previous": null,
  "follow_up": false, "search_text": "…",
  "vector": [812, 815, …],          // 50 ids, desktop index `ref`
  "fts": [815, 3, …],               // up to 50 ids
  "fused": [815, 812, …],           // first 30 of the fusion
  "chunks": {"815": [4, 3, 361, 816], …},   // id → [document_id, ordinal, token_estimate, next_id|null], fused + their next
  "blocks": {
    "d034":   [[4, 3, 3, 361, [815]], …],   // [document_id, first, last, tokens, chunk_ids]
    "report": [[4, 3, 4, 702, [815, 816]], …]
  }
}
```

**Configuraciones:**
- `d034`: (4, 3, 1719, 0);
- `report`: (6, 3, 2200, 2), en el orden (bloques, por documento, presupuesto, vecinos).

## 7. `offline/golden/retrieval_parity.jsonl`

- **Contenido:** 50 líneas, una por pregunta de la muestra fija (R11), ordenadas por `id`.
- **Selección:** semilla `20261029`; 40 respondibles y 10 de seguimiento entre las 370 puntuables.

```json
{
  "id": "q0123", "type": "answerable",
  "question": "…", "previous": null,
  "doc": "boe-lau", "file": "boe-lau.md",
  "evidence": "…",
  "desktop_hit": 1
}
```

El título de cada documento se lee de `manifest.json` (assets de `androidTest`, `offline/corpus`).

## 8. Resultado de la paridad (registro, no fichero)

```
retrieval-parity model=A063 sdk=35 questions=50 not_extracted=N phone=0.xxx desktop=0.xxx diff=±0.0xx changed=q0012:1→0,q0340:0→1
```

Solo ids y cifras (R14).
