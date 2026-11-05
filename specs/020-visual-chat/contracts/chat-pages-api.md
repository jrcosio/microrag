# Contrato: recuperación, prompt, motor y fuentes con páginas (F20)

**Capas** (constitución II):
- `feature/chat`, `feature/document` y `feature/settings`, →
- los casos de uso (`domain/chat`, `domain/retrieval`, `domain/multimodal` y `domain/library`), →
- `RagIndex`, `ChatRepository`, `MultimodalStore`, `PageImageRepository` y `AnswerEngine`, →
- los adaptadores de `data/*` y `rag/runtime/litert`.

Los tipos están en el data-model, §3.

## 1. Índice (`RagIndex`, `DefaultRagIndex`)

| Método | Regla |
|---|---|
| `pageSearch(query, k, scope)` | **P1:** el barrido completo de `pages` con el vector unitario `query`, unido a `documents` con `SEARCHABLE` y el ámbito. La sombra de un reindexado no es buscable, así que salen las páginas de la versión publicada. Ordena por distancia ascendente y devuelve como mucho `k`. Con `pagesUsable = false` devuelve una lista vacía. Cada `ScoredPage` lleva el nombre del documento, el fichero y si es una imagen suelta (R2) |
| `existingPages(keys)` | **P2:** los pares (documento, página) de `keys` que siguen en `pages`, en una consulta (R10) |
| `pageFile(documentId, page)` | **P3:** el `file_name` de esa página, o null |
| `chatLibraryState(scope)` | **P4:** `visualOnly` ya se cuenta aparte (F19, P9). Gana `withPages`, los documentos buscables con alguna página (R5) |

## 2. Recuperación (`HybridRetriever`)

| Regla | |
|---|---|
| **Q1** | Con `withPages = false`, todo es como hoy: misma búsqueda, mismos bloques, mismo registro y `pages` vacío |
| **Q2** | Con `withPages = true`, `pageSearch` corre después del embedding, en paralelo con `vectorSearch`, con el mismo vector (en un seguimiento, también con la pregunta anterior) y el mismo ámbito. Su tiempo va en `pagesMs` |
| **Q3** | La lista de páginas no entra en la RRF ni en el selector (D-076) |
| **Q4** | `NoResults` solo si no hay bloques **ni** páginas. Con páginas y sin bloques, el resultado es `Blocks(blocks = [], pages = …)` |
| **Q5** | `lists` lleva siempre los ids de la búsqueda de vectores (con su distancia) y de la de palabras, por puesto |

## 3. Prompt (`PromptBuilder`)

| Método | Regla |
|---|---|
| `build(question, blocks, previous, template)` | **B1:** la v4 sin cambios (`AnswerGoldenTest`) |
| `buildVisual(question, blocks, page, previous)` | **B2:** las partes de la v5-visual (R4), iguales a las de `visual_prompt.build_parts` para cada caso de `visual_prompt_golden.jsonl` (`VisualPromptGoldenTest`). La página lleva el número `blocks.size + 1` y la etiqueta «Nombre, página p», o solo «Nombre» si es una imagen |
| `estimatedTokens(text)` | **B3:** sin cambios. El límite de 2.000 se aplica siempre al prompt de la v4 (S4); los tokens de la imagen, la regla de la página y su etiqueta no cuentan |

## 4. Sesión de chat (`DefaultChatSession`)

| Regla | |
|---|---|
| **S1** | Al empezar una respuesta, lee el modo (`stage == ON`), `pagesUsable` y el detalle, y con eso fija `withPages` y los tokens. Valen para toda la respuesta (R1) |
| **S2** | Sin páginas en el resultado, el prompt es `AnswerPrompt.Text` con la v4, igual que hoy, byte a byte (FR-001, FR-005, SC-001) |
| **S3** | Con páginas, carga el JPEG de la primera (`PageImageRepository`). Si falta o no empieza por `FF D8`, sigue como S2 y lo anota en el registro (`page_dropped=1`) |
| **S4** | Con la imagen, primero corre el bucle de hoy del límite sobre el prompt de la v4. Con los bloques y el turno que quedan, el prompt es `AnswerPrompt.Visual(buildVisual(…), detail.visualTokens)` (analyze A1) |
| **S5** | Sin bloques y sin página utilizable, guarda la negativa sin llamar a Gemma, como hoy |
| **S6** | Con página, `ActiveAnswer.pageTokens` es el detalle desde que empieza a generar, y el turno guarda `page_tokens` |
| **S7** | Las fuentes citadas se calculan con `AnswerReading.citedOrder(text, refs.size)`. Los refs son los bloques más la página, con su `kind` (`PAGE`, o `IMAGE` si `ScoredPage.image`). Un `[n]` fuera de rango se descarta (FR-014) |
| **S8** | La línea `chat answer` gana `page`, `page_tokens`, `page_dropped` y `pages_ms`, solo con números (R18) |

## 5. Motor (`EngineAnswerEngine`, `LiteRtTextGenerator`)

| Regla | |
|---|---|
| **G1** | Un `AnswerPrompt.Text`, con el modo activo o no, usa la clave y la configuración de hoy: `EngineKey(GEMMA, backend)`, 4.096 tokens y sin visión |
| **G2** | Un `AnswerPrompt.Visual` usa `EngineKey(GEMMA, backend, VISION_GENERATOR)`, con `visionBackend = CPU`, 8.192 tokens y `maxNumImages = 1`. La clave sale del tipo de prompt, nunca del modo leído aparte (R5, analyze I3). `warmUp(visual)` precarga la que tocará |
| **G3** | `GenerationPrompt.Parts` se envía con `Contents.of(...)`, con `Content.Text` y `Content.ImageBytes`. El presupuesto de tokens por imagen (`visualTokens`, que puede ser null) se fija en `ExperimentalFlags.visualTokenBudget` antes de enviar y se restaura en un `finally` |
| **G4** | Al cargar una clave de Gemma, el coordinador cierra antes cualquier otro engine de la familia LLM, en GPU o en CPU. Nunca hay dos Gemma cargadas ni dos engines en GPU (SC-006, analyze I2) |
| **G5** | `releaseGenerators()`, `releaseUnheld()` y `onTrimMemory` cierran también la clave con visión. El codificador de visión se libera con ella (FR-010) |
| **G6** | Cancelar llama a `cancelProcess()`, también durante el prefill de la imagen |

## 6. Fuentes (`SqliteChatRepository`, `ResolveSourcesUseCase`)

| Regla | |
|---|---|
| **H1** | `replaceSources` escribe `kind` (`chunk`, `page` o `image`). Una página o una imagen se guarda con `chunk_id = 0`, ordinales `0` y su número en `page` |
| **H2** | `turns()` lee `kind`, y las fuentes de antes de la v3 salen como `FRAGMENT` |
| **H3** | `ResolveSourcesUseCase`, por cada fuente de página: `DOCUMENT_GONE` con la regla de hoy (borrado, o creado después de la respuesta). Si no, `PAGE_GONE` cuando no está en `existingPages`. Si no, `AVAILABLE` |
| **H4** | El modo no interviene en H3: con el modo apagado, una página que sigue en el índice está disponible (FR-019) |

## 7. Modo y detalle (`domain/multimodal`)

| Regla | |
|---|---|
| **M1** | `StoredMultimodal.pageDetail`, con `BALANCED` por defecto y lo desconocido leído como `BALANCED` |
| **M2** | `SetPageDetailUseCase(detail)` devuelve si se pudo guardar. Si falla, el valor anterior se mantiene |
| **M3** | `ObserveChatAvailabilityUseCase`: con `stage == ON` y `pagesUsable`, `searchable` suma `visualOnly` (R11). Si no, es la regla de hoy |
| **M4** | `WarmUpAnswerEngineUseCase`: con `stage == ON`, `pagesUsable` y `withPages > 0`, `warmUp(visual = true)`. Si no, la de hoy (R5) |

## 8. Imágenes de página (`PageImageRepository`)

| Regla | |
|---|---|
| **I1** | `image(documentId, page)` busca el fichero con `pageFile` y lo lee con `PageFileStore.file(name)`, en el dispatcher de E/S. Devuelve null si falta la fila o el fichero |
| **I2** | Nunca registra nombres ni rutas, solo el recuento de lo que falta |
