# Research: Indexación visual, modo «Multimodal» opcional (F19)

**Fecha:** 2026-11-05. **Spec:** [spec.md](spec.md). **Decisiones:** D-078 y D-079; constitución 1.2.0.

Cada apartado sigue el formato Decisión · Motivo · Alternativas. Lo «verificado» se comprobó en el código de `main` (commit `af2b836`) o en F18.

---

## R1. El modo multimodal es una máquina de estados persistida

**Decisión:**
- **Almacén:** un DataStore propio, `multimodal.preferences_pb` en `noBackupFilesDir/datastore/`, como los de Tema y la preparación de modelos. Guarda:
  - `stage`: `OFF`, `DOWNLOADING`, `VALIDATING`, `SWITCHING` u `ON`;
  - `error`, el motivo del último fallo, o nada;
  - `default_mode`, por defecto `BOTH` (D-079).
- **Controlador:** `MultimodalController`, un singleton de Koin con ámbito de aplicación, como los reconciliadores de F03 y F07:
  - avanza las etapas según el estado de la 440M y el resultado de la validación;
  - al arrancar la app, retoma la etapa guardada.
- **Transiciones:**
  - `OFF → DOWNLOADING` al confirmar el aviso;
  - `DOWNLOADING → VALIDATING` con la 440M verificada y preparada;
  - `VALIDATING → SWITCHING` si la identidad cumple; si no, `OFF` con error y la 440M borrada;
  - `SWITCHING → ON` cuando el índice ha cambiado de modelo y la 270M se ha borrado;
  - `ON → OFF` al desactivar, sin borrar nada (D-079);
  - `OFF → ON` directo si el índice ya está en la 440M (reactivar).
- **Cancelar** en `DOWNLOADING` borra la descarga a medias y vuelve a `OFF`.

**Motivo:**
- la activación dura minutos y cruza la descarga en segundo plano: tiene que sobrevivir a un cierre de la app;
- un almacén propio no toca los de F03 y F16;
- `OFF → ON` sin pasos es la reactivación inmediata de D-079.

**Alternativas:**
- un `WorkManager` para toda la activación: la validación y el cambio duran segundos y necesitan el índice abierto;
- deducirlo todo del estado de los ficheros: no distingue «descargando para activar» de «descarga cancelada».

## R2. El embedder de texto activo lo dice el índice

**Decisión:**
- **Fuente de verdad:** `rag_meta.embedding_model_sha256`, como pide la constitución 1.2.0 (III).
- **`RagIndexConfig`:** pasa de una huella fija a un conjunto de **embedders de texto aceptados**, la 270M y la 440M. `IndexCompatibilityChecker` acepta cualquiera de las dos en esa clave; las demás claves no cambian.
- **`RagIndex.textEmbedder`:** un `StateFlow<ModelId>` con el modelo del fichero abierto. Lo leen:
  - `ChunkEmbeddingSession`, al indexar;
  - `EngineQueryEmbedder`, al consultar;
  - las filas de modelos de Ajustes;
  - `ModelRequirements` (R5).
- **Un índice nuevo** se crea con el embedder verificado que haya: la 270M en una instalación nueva, o la 440M si la 270M ya se borró.

**Motivo:**
- hoy la 270M está fijada en cinco sitios (verificado), y cualquiera que se quede atrás embebería con el modelo equivocado;
- con una sola fuente, el cambio es una transacción.

**Alternativas:**
- un ajuste aparte con el modelo activo: puede desalinearse del índice tras un corte;
- reindexar: es justo lo que D-078 evita.

## R3. Validación de identidad en el dispositivo

**Decisión:**
- **Muestra:** 20 textos fijos en `app/src/main/assets/embedding/identity_texts.jsonl`. Salen de los 50 de `embedding_texts.jsonl` de F18 (androidTest), con párrafos y consultas en español.
- **Comparación:**
  - cada texto se embebe como documento y como consulta, con la 270M y con la 440M, en CPU y en secuencia, liberando cada engine;
  - se compara el vector final de 256D normalizado, el que se guarda y el que se busca;
  - pasa si el coseno mínimo es ≥ 0,9999 (constitución 1.2.0).
- **Coste:** unos 80 embeddings a unos 120–900 ms; en total, unos 30–40 s en el A063.
- **Resultado:** se registra en el log con la cifra y sin el texto. Un fallo deja el error `IDENTITY_MISMATCH`.

**Motivo:**
- F18 midió una identidad de 1,00000 en el A063, pero la regla es comprobarlo en cada dispositivo antes de cambiar;
- comparar el vector final es lo que importa a la búsqueda.

**Alternativas:**
- vectores de referencia guardados del escritorio: dependen del backend y de la versión del runtime;
- comparar todo el índice: son minutos y no aporta más que una muestra fija.

## R4. El cambio de modelo, atómico y reanudable

**Decisión:** en `SWITCHING`:
1. Se pausa la ingesta con una causa nueva, `RunPauseCause.MODEL_SWITCH`, y se espera a que suelte el embedder.
2. `RagIndex.switchTextEmbedder(EG2_440M)` actualiza `embedding_model_sha256` en una transacción, después de comprobar que el fichero sigue en la 270M.
3. Se liberan los engines de la 270M (`EngineCoordinator.release`).
4. La 270M se borra con `ModelFileRepository.delete(id)`, que es nuevo: borra el fichero, su registro en `verification.json` y su backend preparado.
5. Se reanuda la ingesta y la etapa pasa a `ON`.

**Reconciliación al arrancar en `SWITCHING`:**
- si el índice ya está en la 440M, se termina el paso 4;
- si no, se repite desde el paso 1, porque la validación es determinista y la 270M sigue ahí.

**Motivo:**
- la búsqueda siempre ve un índice coherente, porque los vectores son los mismos con los dos modelos;
- un corte solo puede dejar la 270M sin borrar, y eso se arregla al arrancar.

**Alternativas:**
- borrar la 270M antes de cambiar el índice: un corte dejaría un índice sin su modelo;
- cambiar sin pausar la ingesta: un documento a medias mezclaría modelos en vuelo. Los vectores son idénticos, pero la regla es validar antes de escribir con otro modelo.

## R5. Los modelos obligatorios dependen del embedder activo

**Decisión:**
- **`ModelRequirements`** (dominio) sustituye a `ModelCatalog.required` como lista viva:
  - `[embedder de texto del índice (la 270M si no hay índice), Gemma]`;
  - más la 440M mientras la etapa sea `DOWNLOADING`, `VALIDATING` o `SWITCHING`.
- **Lo usan:**
  - el runner de descarga y `deleteOrphanPartials`, para que la descarga a medias de la 440M no se borre;
  - `ModelCatalog.allReady`, la preparación del primer arranque, el chip de Inicio y las filas de Ajustes.
- **El primer arranque** sigue con la 270M (FR-008), porque una instalación nueva no tiene índice en la 440M.

**Motivo:**
- tras el cambio, la 270M no existe. Si siguiera siendo obligatoria, el chip y el chat dirían que faltan modelos;
- hoy la lista está fijada en el runner, en la preparación y en Ajustes (verificado).

**Alternativas:** añadir la 440M a `required` para todos, que es la opción que D-078 descartó.

## R6. La descarga de la 440M reutiliza la de F03

**Decisión:**
- `StartModelDownloadUseCase` y sus hermanos (pausar, reanudar, reintentar y cancelar) pasan a aceptar una lista de modelos, en vez de recorrer siempre `required`;
- la activación pide `[EG2_440M]`, con el mismo programador: UIDT en API 34+, WorkManager en API 31–33;
- se reutilizan la reanudación con `Range`, los 5 reintentos, el margen de espacio de 500 MB, la verificación del SHA-256 y la preparación del backend (`ModelCompatibilityChecker`);
- el progreso sale del `DownloadProgress` de hoy y se muestra en la fila de Ajustes (wireframe §2).

**Motivo:** es el camino probado de F03 y cumple la constitución I: un solo origen fijado y la huella verificada.

**Alternativas:** una descarga propia, que duplicaría la reanudación, las notificaciones y la verificación.

## R7. Los adaptadores de imagen pasan de debug a main

**Decisión:**
- **Interfaces:**
  - `ImageEmbedder` (`embedImage(bytes): FloatArray`, el vector crudo de 768) pasa a `rag/runtime`;
  - `PageRasterizer` (`pageCount`, `renderPage` y `scaleImage`) pasa a `rag/ingest`, con `EncodedImage` y `ImageFormat`.
- **Implementaciones:**
  - `LiteRtImageEmbedder`, en `rag/runtime/litert`: la 440M con el texto y la visión en CPU y `visionTokensPerImage`;
  - `AndroidPageRasterizer`, en `data/ingest`: `PdfRenderer` para las páginas e `ImageDecoder` (con EXIF y sobre blanco) para las imágenes.

  Las dos son el código de F18, sin cambios de comportamiento.
- **El diagnóstico de F18** usa las versiones de `main`. `VisualContracts` conserva `VisionGenerator`, que es de F20.
- **`LayeringTest`:** el SDK sigue solo en `rag/runtime/litert`, y `PdfRenderer` e `ImageDecoder` solo en `data/ingest`.

**Motivo:** ya se midieron en el A063 (F18), y reescribirlos repetiría ese trabajo.

**Alternativas:** dejarlos en debug y copiarlos, lo que daría dos versiones que derivan.

## R8. Esquema `rag.db` v5

**Decisión:** la migración 4 → 5 solo usa sentencias que `RagSchemaTest` admite (verificado):
- `ALTER TABLE documents ADD COLUMN index_mode TEXT NOT NULL DEFAULT 'text'`, con un CHECK `('text','visual','both')`;
- `CREATE TABLE IF NOT EXISTS pages`:
  - `id INTEGER PRIMARY KEY`;
  - `document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE`;
  - `page INTEGER NOT NULL`, empezando en 1;
  - `file_name TEXT NOT NULL`, `width_px INTEGER NOT NULL`, `height_px INTEGER NOT NULL` y `size_bytes INTEGER NOT NULL`;
  - `embedding BLOB NOT NULL`;
  - `UNIQUE (document_id, page)`;
- `CREATE INDEX IF NOT EXISTS idx_pages_doc ON pages(document_id, page)`.

**`documents.page_count`** ya existe. Para un documento Visual o Ambos, es el número de páginas que hay que indexar (1 en una imagen).

**Claves nuevas de `rag_meta`:** `page_model_sha256`, `page_dims`, `page_tokens_per_image`, `page_px` y `page_format`.
- No entran en `VALIDATED`: no impiden abrir el índice;
- se escriben con la primera página guardada;
- si no coinciden con la configuración, `RagIndex.pagesUsable` es `false` y las páginas no se buscan (constitución 1.2.0).

**En cada conexión,** junto al de los fragmentos: `vector_init('pages','embedding','dimension=256,type=FLOAT32,distance=COSINE,normalized=1')`.

**Motivo:**
- una tabla aparte no toca el SQL de los fragmentos, así que la búsqueda de texto no cambia (SC-001);
- la cascada borra las filas con el documento.

**Alternativas:**
- poner las páginas en `chunks`: mezclaría espacios en la misma columna vectorial y en el FTS;
- un fichero de índice aparte: perdería la transacción con el documento.

## R9. Indexación por páginas en el mismo run

**Decisión:**
- **Modo de cada documento:**
  - **Texto:** como hoy;
  - **Visual:** sin fases de texto, con `chunk_count = 0`, y después las páginas;
  - **Ambos:** el texto como hoy y después las páginas. Si el texto da `NO_TEXT`, no es un error: el documento sigue solo con páginas (FR-011).
- **Fase de páginas** en `IngestRunner`, tras el texto, dentro del mismo run, de la misma cola y del mismo trabajo en primer plano. Para cada página que falta:
  1. se convierte a 768 px en JPEG de calidad 85;
  2. se escribe en `noBackupFilesDir/pages/<file_name>` con un nombre temporal y se renombra, para que sea atómico;
  3. se embebe con `PageEmbeddingSession`, que tiene un `LiteRtImageEmbedder` reservado en CPU;
  4. se trunca a 256D y se normaliza;
  5. se inserta la fila.

  Una página por transacción: el checkpoint es la propia fila.
- **Nombre del fichero:** `<content_hash del documento>-p<página>.jpg`. Una copia en sombra tiene su propio `content_hash`, así que no choca.
- **Progreso:** una etapa nueva, `IngestProgress.Stage.PAGES`, con `pagesDone`, `pagesTotal` y el ritmo en páginas por minuto. La cola dice «Indexando páginas · 12 / 40».
- **Pausas:**
  - por temperatura, entre páginas (`TooHot`, como hoy);
  - por espacio, comprobado antes de cada página con el margen de 64 MiB más 250 KB;
  - con el modelo que falta, `MODEL_MISSING`.

  El LLM se libera antes, como hoy (`releaseGenerators`).
- **Publicación:** el documento pasa a `done` cuando sus fragmentos están completos y tiene `page_count` filas en `pages`.
- **Engines:** primero el embedder de texto y después el de imagen, nunca a la vez. La visión siempre va en CPU, así que no hay dos engines en GPU.

**Motivo:**
- reutiliza la reanudación, la cola y las pausas de F07 (verificado);
- la página es la unidad natural de reanudación.

**Alternativas:**
- un trabajo aparte para las páginas: dos colas que coordinar;
- lotes de páginas por transacción: una página tarda unos 3 s, así que el lote no ahorra nada.

## R10. JPG y PNG

**Decisión:**
- `DocumentFormat` gana `JPEG` y `PNG` (extensiones `jpg`, `jpeg` y `png`, y sus MIME);
- `DocumentImporter` los acepta solo con el modo activo. Si no, el error nuevo es `IngestError.IMAGE_NEEDS_MULTIMODAL`;
- se aplican el límite de tamaño, la huella y los duplicados de hoy;
- el documento queda en Visual, con `page_count = 1`;
- una imagen que no se puede decodificar termina en `IngestError.IMAGE_UNREADABLE`.

**Motivo:** con el modo apagado, la app no cambia (FR-001).

## R11. Reindexar, quitar y eliminar con páginas

**Decisión:**
- **Reindexar en otro modo:** la copia en sombra de F08 lleva el modo nuevo, e indexa su texto y sus páginas. `publishReindex`, en la misma transacción:
  - borra las filas de páginas del original y sus ficheros (estos después del commit);
  - mueve las de la sombra (`UPDATE pages SET document_id`), que conservan su nombre.
- **Quitar del índice:** borra las páginas, además de los fragmentos.
- **Eliminar:** borra los ficheros de páginas, después de la cascada.
- **Huérfanos:** al arrancar, un barrido borra los ficheros de `pages/` que ninguna fila nombra, como las copias privadas de F08.
- **La lista de ficheros** se lee antes de borrar, dentro de la misma transacción.

**Motivo:** la versión anterior sigue respondiendo hasta el cambio (FR-017), y no quedan huérfanos (SC-005).

## R12. Pantallas, según el wireframe aprobado

**Decisión** (wireframe §1–§6):
- **Ajustes:**
  - un `LisdataSwitchRow` en «Inteligencia artificial», con el estado de la activación;
  - la fila «Modo al importar PDF» abre la ruta nueva `ImportModeRoute`, que es una pantalla como Tema;
  - los diálogos de activar y desactivar.
- **Ingesta:** la fila «Modo de los PDF» y una hoja con tres `LisdataRadioRow`; la cola, con el progreso en páginas.
- **Biblioteca:** el subtítulo con el modo y las páginas.
- **Detalle:**
  - el modo y las páginas, con `LisdataFactRow`;
  - la hoja de modo en «Reindexar»;
  - «Reindexar como páginas» para un PDF con «Sin texto».
- **ViewModels:** `MultimodalSettingsViewModel` e `ImportModeViewModel`, este con el patrón de Tema (`SavedStateHandle` y `PreferencesSaveFailure`). La Ingesta, la Biblioteca y el detalle amplían los suyos.
- **Textos:** en español y en inglés (`strings_multimodal.xml`), y `StringsParityTest` los vigila.

## R13. El chat, hasta F20

**Decisión:**
- el chat no usa las páginas;
- los recuentos de disponibilidad del chat cuentan solo documentos con fragmentos;
- un documento solo Visual se ve en la Biblioteca y en el detalle con «Solo páginas. Todavía no se usa en el chat».

**Motivo:** el estado tiene que ser verdadero (constitución V).

## R14. Estimaciones de espacio y de tiempo

**Decisión:**
- **Espacio:** 250 KB por página para comprobarlo antes de empezar (FR-018). F18 midió de 54 a 224 KB a 768 px.
- **Tiempo:** 3,2 s por página en el aviso del modo. F18 lo midió a 140 tokens en el A063.
- **Lo que se mide en el teléfono:** el espacio y el ritmo reales, como cifras informativas (SC-003).

## R15. El harness del escritorio

**Decisión:**
- `offline/` lee `schema.sql` y el catálogo de modelos de la app (`test_appconfig.py`). Sus pruebas pasan a la versión 5 y aceptan los dos embedders;
- las cifras de F05 y F10 no cambian, porque el texto sigue igual (FR-025, SC-001). Se comprueba con `run_eval.py ablations` y `order-review`, como en F18 (T034).

## R16. Pruebas

**Decisión:**
- **JVM:**
  - `MultimodalController`, con todas sus transiciones y los cortes en cada etapa;
  - `ModelRequirements`;
  - `IndexCompatibilityChecker` con los dos embedders;
  - el importador con imágenes;
  - `IngestRunner`, con la fase de páginas, un rasterizador y un embedder falsos, la reanudación por página, las pausas y Ambos sin texto;
  - los ViewModels y las pantallas con Robolectric;
  - `LayeringTest`, `StringsParityTest`, `AppModulesTest` y `NavigationTest`.
- **Instrumentadas:**
  - la migración 4 → 5, con datos;
  - `switchTextEmbedder`, transaccional y con un corte simulado;
  - las páginas: insertar, reanudar, publicar la sombra, quitar, eliminar y el barrido de huérfanos;
  - `AndroidPageRasterizerTest`, que hereda la prueba de F18;
  - `LiteRtImageEmbedder` con `@RequiresModels`;
  - accesibilidad con ATF en las pantallas nuevas.
- **Python:** `test_appconfig.py`, con la versión 5.
- **Teléfono, en la última fase y con permiso:**
  - activar el modo de verdad: descarga, validación, cambio y borrado de la 270M;
  - un PDF de 200 páginas en Visual, matando la app a mitad;
  - un escaneado, un JPG y un PNG;
  - la batería completa.

  La 270M del teléfono se borra al activar el modo: antes de la prueba se pregunta al usuario.

**Motivo:** son los criterios SC-001 a SC-008, sin pruebas comparativas (D-078).
