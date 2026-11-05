# Contrato: modo multimodal, cambio de embedder e indexación por páginas (F19)

**Capas** (constitución II): `feature/settings`, `feature/ingest`, `feature/library` y `feature/document` → casos de uso (`domain/multimodal`, `domain/ingest`, `domain/library`, `domain/onboarding`) → `RagIndex`, `MultimodalStore`, `ModelFileRepository`, `IngestRunner` y los adaptadores de `rag/runtime/litert` y `data/ingest`.

Los tipos están en el data-model, §4.

## 1. Índice (`RagIndex`, `DefaultRagIndex`)

| Método | Regla |
|---|---|
| `textEmbedder: StateFlow<ModelId>` | **E1:** el modelo de `rag_meta.embedding_model_sha256` del fichero abierto, la 270M o la 440M. Es la única fuente para quien embebe texto (R2) |
| `switchTextEmbedder(to: ModelId): Boolean` | **E2:** en una transacción, comprueba que `embedding_model_sha256` sigue siendo el de origen y lo cambia al de `to`. Devuelve `false` si ya estaba en `to`. No toca fragmentos ni vectores (R4) |
| `pagesUsable: StateFlow<Boolean>` | **E3:** las claves `page_*` de `rag_meta` coinciden con `PageSpace`, o no hay ninguna todavía |
| `storedPages(documentId): Set<Int>` | **P1:** los números de página ya guardados, para reanudar (R9) |
| `insertPage(documentId, PageInput)` | **P2:** una página por transacción. Con la primera del índice, escribe también las claves `page_*`. Lanza `IndexStorageFullException` como `insertChunks` |
| `pageFiles(documentId): List<String>` | **P3:** los `file_name` de un documento, para borrar sus ficheros después de la transacción (R11) |
| `deleteDocument(id)` / `removeFromIndex(id)` | **P4:** devuelven, además, los `file_name` de las páginas que han borrado. `removeFromIndex` borra también sus páginas |
| `publishReindex(shadowId)` | **P5:** también mueve las páginas de la sombra al original y borra las del original. Devuelve los ficheros que hay que borrar |
| `allPageFiles(): Set<String>` | **P6:** para el barrido de huérfanos al arrancar |
| `addDocument(NewDocument(indexMode))` | **P7:** el documento se registra con su modo. Una imagen, siempre `VISUAL`, con `page_count = 1` |
| `setIndexMode(id, mode)` | **P8:** solo para una sombra recién creada |
| `chatLibraryState(scope)` | **P9:** cuenta como buscable un documento con fragmentos. Uno solo Visual cuenta aparte, en `visualOnly` (R13) |

## 2. Modo multimodal (`domain/multimodal`)

| Pieza | Contrato |
|---|---|
| `MultimodalStore` | **M1:** `state: Flow<StoredMultimodal>` y `write(transform): Boolean`. Un fichero corrupto vuelve a los valores por defecto |
| `MultimodalController` | **M2:** una sola instancia, con ámbito de aplicación. `start()` se llama desde `Application.onCreate` y retoma la etapa guardada (R1). Avanza `DOWNLOADING → VALIDATING → SWITCHING → ON` según el estado de la 440M, la validación y el cambio. Un fallo vuelve a `OFF` con su error y borra la 440M, salvo que el índice ya esté en ella |
| `ObserveMultimodalUseCase` | `Flow<MultimodalState>`, que combina la etapa, el error, el modo por defecto y el progreso de la descarga de la 440M |
| `EnableMultimodalUseCase` | **M3:** si el índice ya está en la 440M y la tiene verificada, pasa a `ON` al momento (reactivar, D-079). Si no, comprueba el espacio (369 MB más 500 MB de margen), pasa a `DOWNLOADING` y pide la descarga de `[EG2_440M]` |
| `DisableMultimodalUseCase` | **M4:** pasa a `OFF`. En `DOWNLOADING`, cancela la descarga y borra su fichero a medias. No borra páginas ni modelos (D-079) |
| `Pause/Resume/CancelMultimodalDownloadUseCase` | Los de F03, sobre `[EG2_440M]` |
| `SetDefaultImportModeUseCase(mode): Boolean` | Guarda `default_mode`. Si falla, `false` |
| `ValidateIdentityUseCase` | **M5:** la muestra de R3, con los dos modelos en CPU y uno detrás de otro. Devuelve `IdentityCheck`; pasa con `minCosine ≥ 0,9999` |
| `ModelRequirements` | **M6:** `required(): Flow<List<ModelId>>` con el embedder de texto del índice, Gemma y, si la etapa está en marcha, la 440M (R5). Lo usan el runner de descarga, `deleteOrphanPartials`, `allReady`, la preparación, Inicio y Ajustes |

## 3. Modelos (`ModelFileRepository`)

| Método | Regla |
|---|---|
| `delete(id): Boolean` | **D1:** nuevo. Borra el fichero, su registro de `verification.json` y su backend preparado, y publica `Missing`. Antes de borrar, el llamante libera sus engines con `EngineCoordinator.release(model)` |

## 4. Ingesta

| Pieza | Contrato |
|---|---|
| `DocumentImporter` | **I1:** acepta `jpg`, `jpeg` y `png` solo si la etapa es `ON`; si no, `IMAGE_NEEDS_MULTIMODAL`. Cada PDF se registra con el modo de la selección (`ImportRequest.indexMode`, que solo vale con `ON`). Sin el modo activo, todo va en `TEXT` |
| `IngestRunner` | **I2:**<br>• por modo: `TEXT` como hoy; `VISUAL` se salta las fases de texto (`chunk_count = 0`); `BOTH` hace el texto y después las páginas. En `BOTH`, `NO_TEXT` no es un error;<br>• fase de páginas: para cada página que falta, convertir, escribir el fichero, embeber, truncar a 256 y normalizar, e insertar (R9);<br>• `done` con los fragmentos y las páginas completos;<br>• pausas por calor, espacio (64 MiB + 250 KB por página) y modelo, como hoy;<br>• respeta `MODEL_SWITCH` |
| `PageEmbeddingSession` | **I3:** mantiene un `ImageEmbedder` de la 440M en CPU durante la fase de páginas y lo libera al terminar. Nunca a la vez que el embedder de texto |
| `PageRasterizer` (`rag/ingest`) | **I4:** `pageCount(pdf)`, `renderPage(pdf, page, longSidePx, JPEG, 85)` y `scaleImage(file, longSidePx, JPEG, 85)`, siempre sobre blanco y con la orientación EXIF. Es bloqueante y se llama en `io` |
| `ImageEmbedder` (`rag/runtime`) | **I5:** `embedImage(bytes): FloatArray`, el vector crudo de 768. El llamante usa `EmbeddingMath.truncateAndNormalize` |
| `PageFileStore` (`data/ingest`) | **I6:** `write(name, bytes)` con un fichero `.part` que después se renombra, y `delete(names)` en `noBackupFilesDir/pages/` |
| Barrido de huérfanos | **I7:** al arrancar, borra los ficheros de `pages/` que no estén en `allPageFiles()` |

## 5. Lo que no cambia

- El SQL de las búsquedas por texto, la fusión, los candidatos, el contexto y el prompt (constitución III, SC-001).
- La preparación del primer arranque de una instalación nueva: la 270M y Gemma (FR-008).
- La regla de nunca dos engines en GPU. La visión de la 440M siempre va en CPU.
