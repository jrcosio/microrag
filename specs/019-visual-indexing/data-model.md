# Data model: Indexación visual, modo «Multimodal» opcional (F19)

**Fecha:** 2026-11-05. **Research:** [research.md](research.md) (R1, R2, R8 y R9).

## 1. `rag.db` v5 (R8)

### 1.1 Migración 4 → 5

```sql
ALTER TABLE documents ADD COLUMN index_mode TEXT NOT NULL DEFAULT 'text'
    CHECK (index_mode IN ('text','visual','both'));

CREATE TABLE IF NOT EXISTS pages (
    id           INTEGER PRIMARY KEY,
    document_id  INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page         INTEGER NOT NULL,          -- 1-based
    file_name    TEXT NOT NULL,             -- noBackupFilesDir/pages/<file_name>
    width_px     INTEGER NOT NULL,
    height_px    INTEGER NOT NULL,
    size_bytes   INTEGER NOT NULL,
    embedding    BLOB NOT NULL,             -- 256 float32, L2-normalized
    UNIQUE (document_id, page)
);

CREATE INDEX IF NOT EXISTS idx_pages_doc ON pages(document_id, page);
```

- **`schema.sql`** recoge las tres sentencias en su forma final, y su cabecera pasa a «version 5».
- **`RagSchemaTest`:**
  - `SCHEMA_VERSION == 5`;
  - el recuento de sentencias pasa de 13 a 15;
  - la migración `from = 4` cumple la regla «termina donde `schema.sql`».
- **Documentos existentes:** quedan en `index_mode = 'text'`. Ninguno tiene páginas.

### 1.2 `rag_meta`

| Clave | Valor | Validada al abrir |
|---|---|---|
| `embedding_model_sha256` | la huella de la 270M **o** la de la 440M (R2) | Sí, contra el conjunto de embedders de texto aceptados |
| `page_model_sha256` | la huella de la 440M | No: si no coincide, `pagesUsable = false` |
| `page_dims` | `256` | No (ídem) |
| `page_tokens_per_image` | `140` | No (ídem) |
| `page_px` | `768` | No (ídem) |
| `page_format` | `jpeg-85` | No (ídem) |

- **Las claves de páginas** se escriben en la misma transacción que la primera fila de `pages` que se guarda.
- **`switchTextEmbedder(EG2_440M)`** solo cambia `embedding_model_sha256`, en una transacción que antes comprueba el valor anterior.

### 1.3 Vectores

- **Fragmentos:** sin cambios.
- **Páginas:** `vector_init('pages','embedding','dimension=256,type=FLOAT32,distance=COSINE,normalized=1')`, en cada conexión. F19 solo escribe; la búsqueda por páginas es de F20.

## 2. Ficheros

- **Imágenes de página:** `noBackupFilesDir/pages/<content_hash>-p<página>.jpg`.
  - 768 px por el lado largo, sobre blanco, en JPEG de calidad 85;
  - se escriben como `.part` y se renombran;
  - fuera de las copias de seguridad, como la copia privada de F06.
- **Muestra de identidad:** `app/src/main/assets/embedding/identity_texts.jsonl`, con 20 líneas `{"id", "text"}` (R3).

## 3. Preferencias: `multimodal.preferences_pb` (R1)

| Clave | Tipo | Valores | Por defecto |
|---|---|---|---|
| `stage` | string | `OFF`, `DOWNLOADING`, `VALIDATING`, `SWITCHING`, `ON` | `OFF` |
| `error` | string? | `NO_SPACE`, `DOWNLOAD_FAILED`, `IDENTITY_MISMATCH`, `PREPARE_FAILED` | nada |
| `default_mode` | string | `TEXT`, `VISUAL`, `BOTH` | `BOTH` (D-079) |

- Un fichero corrupto se sustituye por los valores por defecto, como el de Tema.
- `write()` devuelve `false` si falla, y la pantalla avisa con `PreferencesSaveFailure`.

## 4. Dominio

| Tipo | Campos o valores | Notas |
|---|---|---|
| `IndexMode` | `TEXT`, `VISUAL`, `BOTH` | `sqlValue` `text`/`visual`/`both`; JPG y PNG siempre `VISUAL` |
| `MultimodalStage` | `OFF`, `DOWNLOADING`, `VALIDATING`, `SWITCHING`, `ON` | R1 |
| `MultimodalState` | `stage`, `error`, `defaultMode`, `download: DownloadProgress?` | La consume Ajustes |
| `MultimodalError` | `NO_SPACE`, `DOWNLOAD_FAILED`, `IDENTITY_MISMATCH`, `PREPARE_FAILED` | Cada una con su texto |
| `IdentityCheck` | `texts: Int`, `minCosine: Double`, `passed: Boolean` | R3; solo la cifra va al log |
| `StoredPage` | `documentId`, `page`, `fileName`, `widthPx`, `heightPx`, `sizeBytes` | Sin el vector |
| `PageInput` | `page`, `fileName`, `widthPx`, `heightPx`, `sizeBytes`, `vector: FloatArray` | Lo que inserta el runner |
| `PageSpace` | `modelSha256`, `dims`, `tokensPerImage`, `px`, `format` | Constante de la app; se compara con `rag_meta` |
| `StoredDocument` | `+ indexMode: IndexMode`, `+ pagesStored: Int` | `pageCount` ya existe |
| `IngestProgress` | `+ Stage.PAGES`, `+ pagesDone`, `+ pagesTotal`, `+ pagesPerMinute` | R9 |
| `RunPauseCause` | `+ MODEL_SWITCH` | R4 |
| `DocumentFormat` | `+ JPEG`, `+ PNG` | R10 |
| `IngestError` | `+ IMAGE_NEEDS_MULTIMODAL`, `+ IMAGE_UNREADABLE` | R10 |

## 5. Estados de un documento con páginas

```
pending ──parse──▶ parsed ──texto──▶ embedding ──páginas──▶ done
   │                  │                              │
   │  (Visual: sin texto, chunk_count = 0)           └─ pausa por calor, espacio o modelo: sigue en embedding
   └─ error (NO_TEXT solo en Texto; IMAGE_UNREADABLE; …)
```

- **`done` exige** `stored_chunks == chunk_count` y `count(pages) == page_count` (R9).
- **Una copia en sombra** sigue el mismo ciclo y se publica con sus páginas (R11).
- **«Fuera del índice»** borra los fragmentos y las páginas, y deja la copia privada.
