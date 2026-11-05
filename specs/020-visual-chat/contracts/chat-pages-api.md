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
| `pageSearch(query, k, scope)` | **P1:** el barrido completo de `pages` con el vector unitario `query`, unido a `documents` con `SEARCHABLE` y el ámbito. Ordena por distancia ascendente y devuelve como mucho `k`. Con `pagesUsable = false` devuelve una lista vacía. Cada `ScoredPage` lleva el nombre del documento, el fichero y si es una imagen suelta (R2) |
| `existingPages(keys)` | **P2:** los pares (documento, página) de `keys` que siguen en `pages`, en una consulta (R10) |
| `pageFile(documentId, page)` | **P3:** el `file_name` de esa página, o null |
| `chatLibraryState(scope)` | **P4:** sin cambios. `visualOnly` ya se cuenta aparte (F19, P9) |

## 2. Recuperación (`HybridRetriever`)

| Regla | |
|---|---|
| **Q1** | Con `withPages = false`, todo es como hoy: misma búsqueda, mismos bloques, mismo registro y `pages` vacío |
| **Q2** | Con `withPages = true`, `pageSearch` corre después del embedding, en paralelo con `vectorSearch`, con el mismo vector y el mismo ámbito. Su tiempo va en `pagesMs` |
| **Q3** | La lista de páginas no entra en la RRF ni en el selector (D-076) |
| **Q4** | `NoResults` solo si no hay bloques **ni** páginas. Con páginas y sin bloques, el resultado es `Blocks(blocks = [], pages = …)` |
| **Q5** | `lists` lleva siempre los ids de la búsqueda de vectores (con su distancia) y de la de palabras, por puesto |

## 3. Prompt (`PromptBuilder`)

| Método | Regla |
|---|---|
| `build(question, blocks, previous, template)` | **B1:** la v4 sin cambios (`AnswerGoldenTest`) |
| `buildVisual(question, blocks, page, previous)` | **B2:** las partes de la v5-visual (R4), iguales a las de `visual_prompt.build_parts` para cada caso de `visual_prompt_golden.jsonl` (`VisualPromptGoldenTest`). La página lleva el número `blocks.size + 1` y la etiqueta «Nombre, página p», o solo «Nombre» si es una imagen |
| `estimatedTokens(text)` | **B3:** sin cambios. Con página, se aplica a la suma de las partes de texto; los tokens de la imagen no cuentan para el límite de 2.000 |

## 4. Sesión de chat (`DefaultChatSession`)

| Regla | |
|---|---|
| **S1** | Al empezar una respuesta, lee el modo (`stage == ON`), `pagesUsable` y el detalle, y con eso fija `withPages` y los tokens. Valen para toda la respuesta (R1) |
| **S2** | Sin páginas en el resultado, el prompt es `AnswerPrompt.Text` con la v4, igual que hoy, byte a byte (FR-001, FR-005, SC-001) |
| **S3** | Con páginas, carga el JPEG de la primera (`PageImageRepository`). Si falta o no empieza por `FF D8`, sigue como S2 y lo anota en el registro (`page_dropped=1`) |
| **S4** | Con la imagen, el prompt es `AnswerPrompt.Visual(buildVisual(…), detail.visualTokens)`. Los bloques pasan antes por la regla de hoy del límite sobre el texto |
| **S5** | Sin bloques y sin página utilizable, guarda la negativa sin llamar a Gemma, como hoy |
| **S6** | Con página, `ActiveAnswer.pageTokens` es el detalle desde que empieza a generar, y el turno guarda `page_tokens` |
| **S7** | Las fuentes citadas se calculan con `AnswerReading.citedOrder(text, refs.size)`. Los refs son los bloques más la página, con su `kind`. Un `[n]` fuera de rango se descarta (FR-014) |
| **S8** | La línea `chat answer` gana `page`, `page_tokens`, `page_dropped` y `pages_ms`, solo con números (R18) |

## 5. Motor (`EngineAnswerEngine`, `LiteRtTextGenerator`)

| Regla | |
|---|---|
| **G1** | Con el modo apagado, la clave y la configuración son las de hoy: `EngineKey(GEMMA, backend)`, 4.096 tokens y sin visión |
| **G2** | Con el modo `ON`, la clave es `EngineKey(GEMMA, backend, VISION_GENERATOR)`, con `visionBackend = CPU`, 8.192 tokens y `maxNumImages = 1`. Se usa para todas las respuestas, con página o sin ella (R5) |
| **G3** | `GenerationPrompt.Parts` se envía con `Contents.of(...)`, con `Content.Text` y `Content.ImageBytes`. El presupuesto de tokens por imagen se fija en `ExperimentalFlags.visualTokenBudget` antes de enviar y se restaura en un `finally` |
| **G4** | Al cambiar de modo, la siguiente respuesta carga la otra clave. Por ser de GPU, el coordinador cierra la anterior. Nunca hay dos engines en GPU (SC-006) |
| **G5** | `releaseGenerators()`, `releaseUnheld()` y `onTrimMemory` cierran también la clave con visión. El codificador de visión se libera con ella (FR-010) |
| **G6** | Cancelar llama a `cancelProcess()`, también durante el prefill de la imagen |

## 6. Fuentes (`SqliteChatRepository`, `ResolveSourcesUseCase`)

| Regla | |
|---|---|
| **H1** | `replaceSources` escribe `kind`. Una página se guarda con `chunk_id = 0`, ordinales `0` y su número en `page` |
| **H2** | `turns()` lee `kind`, y las fuentes de antes de la v3 salen como `FRAGMENT` |
| **H3** | `ResolveSourcesUseCase`, por cada fuente de página: `DOCUMENT_GONE` con la regla de hoy (borrado, o creado después de la respuesta). Si no, `PAGE_GONE` cuando no está en `existingPages`. Si no, `AVAILABLE` |
| **H4** | El modo no interviene en H3: con el modo apagado, una página que sigue en el índice está disponible (FR-019) |

## 7. Modo y detalle (`domain/multimodal`)

| Regla | |
|---|---|
| **M1** | `StoredMultimodal.pageDetail`, con `BALANCED` por defecto y lo desconocido leído como `BALANCED` |
| **M2** | `SetPageDetailUseCase(detail)` devuelve si se pudo guardar. Si falla, el valor anterior se mantiene |
| **M3** | `ObserveChatAvailabilityUseCase`: con `stage == ON` y `pagesUsable`, `searchable` suma `visualOnly` (R11). Si no, es la regla de hoy |

## 8. Imágenes de página (`PageImageRepository`)

| Regla | |
|---|---|
| **I1** | `image(documentId, page)` busca el fichero con `pageFile` y lo lee con `PageFileStore`, en el dispatcher de E/S. Devuelve null si falta la fila o el fichero |
| **I2** | Nunca registra nombres ni rutas, solo el recuento de lo que falta |
