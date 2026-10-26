# Data model — F04 Núcleo de datos RAG

## 1. `schema.sql` (versión 1)

El esquema está en `app/src/main/assets/schema.sql`. Es el del informe, ampliado según G27 (D-030). Sirve igual para la app y para el Python offline (F05).

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS rag_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id              INTEGER PRIMARY KEY,
    source          TEXT NOT NULL CHECK (source IN ('base','user')),
    uri             TEXT,
    display_name    TEXT NOT NULL,
    mime_type       TEXT,
    content_hash    TEXT NOT NULL UNIQUE,
    status          TEXT NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','parsed','embedding','done','error','paused','cancelled')),
    chunk_count     INTEGER,
    error           TEXT,
    included        INTEGER NOT NULL DEFAULT 1 CHECK (included IN (0,1)),
    size_bytes      INTEGER,
    page_count      INTEGER,
    local_path      TEXT,
    created_at_ms   INTEGER NOT NULL,
    updated_at_ms   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chunks (
    id              INTEGER PRIMARY KEY,
    document_id     INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    ordinal         INTEGER NOT NULL,
    heading_path    TEXT,
    page            INTEGER,
    content         TEXT NOT NULL,
    title_norm      TEXT NOT NULL,
    body_norm       TEXT NOT NULL,
    token_estimate  INTEGER NOT NULL,
    embedding       BLOB NOT NULL,
    UNIQUE (document_id, ordinal)
);

CREATE INDEX IF NOT EXISTS idx_chunks_doc ON chunks(document_id, ordinal);

CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
    title_norm,
    body_norm,
    content = 'chunks',
    content_rowid = 'id',
    tokenize = 'unicode61 remove_diacritics 2'
);

-- Triggers chunks_ai / chunks_ad / chunks_au, exactamente como en el informe §Esquema SQLite.
```

**Cambios frente al informe:**
- Columnas `included`, `size_bytes`, `page_count` y `local_path`, más los estados `paused` y `cancelled` (G27).
- La app siempre escribe `source = 'user'` (D-002). Se mantiene `'base'` en el `CHECK` por compatibilidad con el esquema compartido.

**División en sentencias:** la app parte el fichero por `;` seguido de salto de línea y de `CREATE` o `PRAGMA`, como en el informe. Así los `BEGIN … END;` de los triggers quedan enteros. Un test comprueba que salen todas las sentencias esperadas.

## 2. `rag_meta`

| Clave | Valor en la v1 | Validación al abrir |
|---|---|---|
| `schema_version` | `1` | Menor: migrar. Igual: aceptar. Mayor: incompatible |
| `embedding_model_sha256` | SHA-256 de `embeddinggemma-2-text-270m.litertlm` (`ModelCatalog`) | Igual o incompatible |
| `embedding_dims` | `256` (`EmbeddingMath.DIMENSIONS`) | Igual o incompatible |
| `embedding_doc_prefix` | `task: search result \| text: ` (`EmbeddingPrefixes.DOCUMENT`) | Igual, con el espacio final, o incompatible |
| `embedding_query_prefix` | `task: search query \| text: ` (`EmbeddingPrefixes.QUERY`) | Igual o incompatible |
| `normalizer_version` | `1` (`SpanishText.VERSION`) | Igual o incompatible |
| `quant_dirty` | `1` al crear y tras cada lote | No se valida (D-030: INT8 en F14) |

Un índice **nuevo** es un fichero sin tablas propias. SQLite-Vector crea `_sqliteai_vector` en cuanto se carga, así que esa tabla no cuenta (research R3). Se escriben todas las claves en la misma transacción que crea el esquema. Uno **existente** al que le falte alguna clave es incompatible (campo `missing:<clave>`).

## 3. Tipos de dominio (`domain/rag`)

```text
enum DocumentStatus { PENDING, PARSED, EMBEDDING, DONE, ERROR, PAUSED, CANCELLED }   // ↔ 'pending'… en SQL

NewDocument(displayName, contentHash, mimeType?, uri?, sizeBytes?, pageCount?, localPath?)
StoredDocument(id, displayName, contentHash, mimeType?, uri?, status, chunkCount?, error?, included,
               sizeBytes?, pageCount?, localPath?, createdAtMs, updatedAtMs)

ChunkInput(ordinal, headingPath?, page?, content, tokenEstimate, embedding: FloatArray /* 256, unitario */)
ChunkView(id, documentId, ordinal, headingPath?, page?, content, tokenEstimate, documentName)
ScoredChunk(id, distance)        // distancia de coseno de SQLite-Vector: menor = más parecido
IndexCounts(documents, chunks, ftsRows)   // ftsRows: filas de chunks_fts_docsize (contract A8)

sealed IndexCompatibility:
    Closed                       // aún no se ha abierto
    Compatible
    Incompatible(field, expected, found)
    Unreadable(reason)           // fichero corrupto o extensión que no carga
```

**Configuración esperada** (`RagIndexConfig`):
- `schemaVersion = 1`;
- `embeddingModelSha256 = ModelCatalog.byId(EG2_270M).sha256`;
- `dims = 256`;
- `docPrefix` y `queryPrefix` de `EmbeddingPrefixes`;
- `normalizerVersion = SpanishText.VERSION`.

`IndexCompatibilityChecker.check(config, meta: Map<String,String>, migrations)` es una función pura que da la decisión: `Compatible`, `NeedsMigration(from)` o `Incompatible`.

## 4. Columnas derivadas

| Columna | Valor |
|---|---|
| `title_norm` | `SpanishText.normalize(listOf(displayName, headingPath).filter { !it.isNullOrBlank() }.joinToString(" › "))` |
| `body_norm` | `SpanishText.normalize(content)` |
| `embedding` | `vector_as_f32(blob)`, con el BLOB float32 little-endian de 256 componentes (`toLeBlob`) |
| `updated_at_ms` | Momento de cada cambio de estado o de inclusión |

Las calcula el repositorio, nunca quien llama (research R8).

## 5. Ficheros golden (`offline/golden/`)

| Fichero | Formato por línea | Origen |
|---|---|---|
| `normalizer_inputs.txt` | Un texto de entrada por línea; las líneas que empiezan por `#` son comentarios | Escrito a mano, más una expansión sistemática en el generador |
| `normalizer_golden.jsonl` | `{"in": "...", "out": "..."}` | `gen_normalizer_golden.py` (salidas del port en Python) |
| `embedding_texts.jsonl` | `{"id": "q01", "kind": "query", "text": "..."}` o `{"id": "d01", "kind": "doc", "header": "Doc › Sección", "text": "..."}` | Escrito a mano |
| `embedding_golden.jsonl` | Primera línea, `{"meta": {"model_file", "model_sha256", "litert_lm_api", "max_input_length", "dims"}}`, sin fecha para que regenerar no cambie el fichero (la fecha la da git); después, `{"id", "kind", "input": "<texto exacto con prefijo>", "v256": [256 floats]}` | `gen_embedding_golden.py` |
