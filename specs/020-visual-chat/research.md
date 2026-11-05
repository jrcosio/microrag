# Research: Chat con páginas, modo «Multimodal» opcional (F20)

**Fecha:** 2026-11-05. **Spec:** [spec.md](spec.md). **Decisiones:** D-076, D-078 y D-083; constitución 1.2.0, sin enmienda.

Cada apartado sigue el formato Decisión · Motivo · Alternativas. Lo «verificado» se comprobó en el código de `main` (commit `4bb1d3f`) o en F18 y F19.

---

## R1. Cuándo se usan las páginas

**Decisión:** una respuesta usa páginas solo si se cumplen las tres condiciones:
- el modo está en `ON`, según `MultimodalStore.state.stage`;
- el espacio de páginas del índice coincide con el de la app (`RagIndex.pagesUsable`, de F19);
- la búsqueda de páginas devuelve al menos una del ámbito.

La sesión de chat lo decide **al empezar cada respuesta**, junto con el ámbito (FR-019 de F17). Un cambio de modo a mitad de una respuesta vale para la siguiente.

**Motivo:**
- FR-001 y FR-005: con el modo apagado o sin páginas, todo es como hoy;
- leer el modo al empezar evita que una respuesta cambie de plantilla a medias.

**Alternativas:**
- leer el modo en el retriever: mezclaría el dominio del modo con el de la búsqueda;
- usar páginas con el modo apagado si existen: lo prohíbe FR-007 de F19.

## R2. La búsqueda de páginas

**Decisión:**
- **Método nuevo:** `RagIndex.pageSearch(query, k, scope)`, con el mismo vector de la consulta que la búsqueda de fragmentos.
- **SQL:** `vector_full_scan('pages', 'embedding', ?)` materializado, unido a `documents` con `SEARCHABLE` (indexado e incluido) y la condición del ámbito de F17. Ordena por distancia y aplica `LIMIT k`.
  - Es el mismo esquema que la búsqueda con ámbito de F17 (barrido completo sin k, para que el filtro vea todas las filas). Las páginas son pocas frente a los fragmentos.
- **`k = PAGE_CANDIDATES = 5`:**
  - solo entra la primera (D-078);
  - las demás son para el diagnóstico de debug.
- **Dónde se busca:** `HybridRetriever` lanza la búsqueda de páginas después del embedding, en paralelo con la de vectores de texto, solo si la petición lo pide (`RetrievalRequest.withPages`). Su tiempo va en `RetrievalTimings.pagesMs`.
- **Sin fusión:** la lista de páginas no entra en la RRF (D-076).

**Verificado:**
- la tabla `pages` tiene su `vector_init` en cada conexión (F19, R8), con 256D y `normalized=1`;
- F18 midió una cota de 53 ms para la lista de páginas en el A063.

**Alternativas:**
- un `k + m` sin ámbito, como la búsqueda de texto de toda la biblioteca: las páginas ocultas (excluidas o sin indexar) no tienen recuento propio, y el barrido completo es barato;
- buscar las páginas con la consulta embebida otra vez: el vector ya está, y es el mismo espacio (FEATURES §F20).

## R3. El resultado de la recuperación con páginas

**Decisión:**
- **`RetrievalOutcome.Blocks`** gana `pages: List<ScoredPage>`, las candidatas en orden de distancia. Puede tener `blocks` vacío si hay páginas.
- **`NoResults`** solo cuando no hay ni bloques ni páginas.
- **`ScoredPage`:** `documentId`, `page`, `distance` y, ya cargados, el nombre del documento y el nombre del fichero de la imagen.
- **`RetrievalResult.lists`:** las listas de vectores y de palabras (ids y puestos), siempre. Son 100 números como mucho, y el diagnóstico de debug las necesita por separado (FR-022).

**Motivo:**
- FR-006: una pregunta sin bloques pero con una página se responde con la página;
- un único resultado mantiene a la sesión de chat como dueña de la decisión.

**Alternativas:**
- un `RetrievalOutcome.Pages` aparte: obligaría a tratar dos casos que acaban en la misma plantilla;
- calcular las listas solo en debug: complicaría el retriever para ahorrar casi nada.

## R4. El contexto y la plantilla v5-visual

**Decisión:**
- **Bloques:** los de D-034, sin tocar, con el mismo límite de 2.000 tokens estimados **sobre el texto**. Si se pasa, caen como hoy: primero el peor bloque y después el turno anterior.
- **Página:** la primera de la lista, después de los bloques, con el número `[k + 1]`.
- **Etiqueta:**
  - un PDF: «Nombre, página p»;
  - una imagen suelta: solo «Nombre».
- **Plantilla `v5-visual`:** el port exacto de `offline/eval/visual_prompt.py` en `PromptBuilder.buildVisual(question, blocks, page, previous)`. Devuelve una lista de partes, `PromptPart.Text` y `PromptPart.Image`:
  - las reglas de la v4 y, después, `PAGE_RULE` («- Algunas FUENTES son imágenes de páginas: léelas y cítalas igual que las demás.»);
  - después, la conversación previa, como en la v4;
  - las fuentes de texto como hoy;
  - la página: su etiqueta, un salto de línea, su imagen y un salto de línea;
  - por último, la pregunta;
  - las partes de texto seguidas se juntan.
- **Sin página,** la plantilla es la v4 de `PromptBuilder.build`, sin cambios (FR-007).
- **Paridad con el escritorio:**
  - `offline/gen_visual_prompt_golden.py` escribe `offline/golden/visual_prompt_golden.jsonl`, con unos 12 casos: con y sin turno anterior, con 0 a 4 bloques, una página de PDF y una imagen suelta;
  - `VisualPromptGoldenTest` (JVM) compara las partes, como `AnswerGoldenTest` con la v4.

**Motivo:**
- D-078 elimina la regla de duplicados: la página se suma a los bloques;
- la plantilla es la que evaluó F18;
- la golden evita que el port se separe del escritorio.

**Alternativas:**
- poner la página primero: F18 la evaluó al final, y los bloques conservan sus números de la v4;
- contar los tokens visuales dentro del límite de 2.000: cambiaría los bloques que entran, en contra de D-078 (bloques «sin tocar»).

## R5. Gemma con visión en el modo activo

**Decisión:**
- **Con el modo `ON`,** Gemma se crea siempre con visión, haya página o no en la respuesta:
  - `visionBackend = CPU`;
  - `maxNumTokens = LLM_VISUAL_MAX_NUM_TOKENS = 8192`;
  - `maxNumImages = 1`;
  - el backend de texto, el que preparó F03.
- **Con el modo apagado,** Gemma se crea como hoy: 4.096 tokens y sin visión.
- **`EngineKey`:** la clave de Gemma con visión lleva `EngineKind.VISION_GENERATOR`, así que nunca se confunde con la de texto. Cargar una en GPU cierra la otra (la regla de siempre del coordinador).
- **`GenerationConfig`** gana `visionBackend: ComputeBackend?` y `maxNumImages: Int`, ambos sin valor por defecto en el modo apagado.
- **`TextGenerator.generate(prompt: GenerationPrompt)`:**
  - `GenerationPrompt.Text(String)`, que es lo de hoy;
  - `GenerationPrompt.Parts(parts, visualTokens)`, que se envía con `sendMessageAsync(Contents.of(...))`, con `Content.Text` y `Content.ImageBytes`.
- **Presupuesto de tokens por imagen:** `ExperimentalFlags.visualTokenBudget`, que es de todo el proceso, se fija antes de enviar y se restaura en un `finally`. Va dentro del uso exclusivo del LLM que da el coordinador, como en F18.
- **Liberación:**
  - el codificador de visión vive dentro del `Engine` de Gemma, así que `close()` lo libera con él (FR-010);
  - `releaseGenerators()` ya cierra todo lo de la familia LLM, sea del tipo que sea;
  - `onTrimMemory` también.

**Verificado:**
- `LiteRtTextGenerator` crea el `EngineConfig` sin visión (F11);
- el diagnóstico de F18 (`LiteRtVisionEngines.kt`, en debug) ya generaba con imágenes con este mismo esquema;
- el fichero de Gemma solo declara la visión en CPU (F18, `report.md`).

**Motivo:**
- una sola configuración por modo evita recargar Gemma (varios segundos) cada vez que una respuesta lleva o no página. Con D-083 (la página entra siempre que haya páginas en el ámbito), lo normal con el modo activo es que la lleve;
- FR-009: con el modo apagado, Gemma no cambia.

**Alternativas:**
- cargar Gemma con visión solo cuando entra una página: recargas al cambiar de ámbito, y dos caminos de memoria;
- un generador separado para la visión: duplicaría el ciclo de vida, la cancelación y las estadísticas.

**Riesgo:** con el modo activo y sin páginas, la ventana de 8.192 tokens y la visión cargada podrían cambiar el primer token o el pico de memoria. Se mide (SC-003).

## R6. El generador de F18 pasa a main

**Decisión:**
- la lógica de generación con imágenes de `LiteRtVisionEngines.kt` (debug) se lleva a `LiteRtTextGenerator` (main), como hizo F19 con el embedder de imagen;
- el diagnóstico de F18 (`VisualSuite`) usa el generador de main a través de `EngineFactory`, con una `GenerationConfig` con visión;
- `VisionGenerator` y su implementación de debug se borran;
- `LayeringTest` sigue vigilando que el SDK solo esté en `rag/runtime/litert`.

**Motivo:** un único adaptador de Gemma, probado por el chat y por el diagnóstico.

**Alternativas:** dejar el de debug, que duplicaría el manejo del presupuesto y la cancelación.

## R7. La imagen que recibe Gemma

**Decisión:**
- es el JPEG de la página guardado por F19 (768 px, calidad 85), leído de `noBackupFilesDir/pages/` con `PageFileStore.read(name)`, en el dispatcher de E/S;
- se comprueba antes de generar que el fichero existe y empieza por la marca JPEG (`FF D8`). Si no, la página se quita y la respuesta sigue sin ella, con la v4 y el recuento en el registro (caso límite de la spec);
- si con la página quitada no queda ningún bloque, la respuesta es la negativa, sin llamar a Gemma.

**Verificado:** F18 vio que LiteRT-LM no decodifica WebP; JPEG sí.

**Alternativas:**
- volver a convertir la página desde el PDF: el PDF puede ya no estar, y la imagen guardada es la que se buscó;
- fallar la respuesta: la spec pide seguir sin la página.

## R8. Las fuentes de tipo página

**Decisión:**
- **Modelo:**
  - `AnswerSource` y `PromptBlockRef` ganan `kind: SourceKind` (`FRAGMENT` o `PAGE`);
  - para una página, `page` es su número, `chunkId` es `0` y los ordinales también son `0`. Los ids de `chunks` empiezan en 1, así que 0 nunca es un fragmento real;
  - todo lo que usa `chunkId` comprueba antes `kind`.
- **Citas:** `AnswerReading.citedOrder(text, refs.size)` ya admite el número `k + 1`, y un `[n]` fuera de rango se descarta igual.
- **Copiar:** la línea de una página es «[n] Nombre, página p», con las cadenas de hoy (`chat_copy_source`). Una imagen suelta usa la variante sin página.

**Motivo:**
- añadir un campo es el cambio más pequeño: los dos tipos se guardan, se ordenan y se numeran igual;
- `chunkId = 0` con su tipo evita reconstruir la tabla `sources` (R9).

**Alternativas:**
- un `AnswerSource` sellado con dos tipos: el cambio toca a todos los que leen fuentes (chat, Historial, copiar, chips) solo para un campo;
- `chunkId` nulo: la columna es `NOT NULL`, y cambiarlo obliga a reconstruir la tabla.

## R9. `chat.db` v3

**Decisión:** la migración 2 → 3, dentro de la transacción de apertura, como la de F17:

```sql
ALTER TABLE sources ADD COLUMN kind TEXT NOT NULL DEFAULT 'chunk' CHECK (kind IN ('chunk','page'));
ALTER TABLE turns ADD COLUMN page_tokens INTEGER;
```

- Las fuentes de antes quedan como `chunk`.
- `turns.page_tokens` es el detalle con que se generó la respuesta (70, 140 o 280), o `NULL` si no llevó página. Sirve para el diagnóstico y las mediciones, y no se enseña.
- `chat_schema.sql` pasa a la v3, con las dos columnas, para las instalaciones nuevas.

**Verificado:** `ChatMigrations` ya aplica una migración por versión dentro de la transacción (F17). SQLite admite `ADD COLUMN` con `NOT NULL`, un valor por defecto y `CHECK`.

**Alternativas:**
- reconstruir `sources` con `chunk_id` nulo: más riesgo, y nada que ganar con R8;
- una tabla `page_sources` aparte: el orden de las citas mezcla los dos tipos.

## R10. Disponibilidad de las citas a página

**Decisión:**
- **`SourceAvailability`** gana `PAGE_GONE`.
- **`ResolveSourcesUseCase`** resuelve las páginas con `RagIndex.existingPages(keys)`, que devuelve qué pares (documento, página) siguen en `pages`:
  - documento borrado, o creado después de la respuesta: `DOCUMENT_GONE`, la regla de siempre;
  - documento presente pero sin esa página: `PAGE_GONE`;
  - si no, `AVAILABLE`.
- **Lo que se ve:**
  - `DOCUMENT_GONE`: «Fuente ya no disponible», sin poder pulsarse, como hoy;
  - `PAGE_GONE`: la tarjeta se puede pulsar, y abre el documento con el aviso «La página citada ya no está en este documento.».
- **Con el modo apagado** (FR-019), las páginas siguen en el índice (F19), así que se resuelven igual.

**Alternativas:** reutilizar `FRAGMENT_GONE`. Su mensaje habla de un fragmento.

## R11. Disponibilidad del chat

**Decisión:** `ObserveChatAvailabilityUseCase` combina también el modo y `pagesUsable`. Con los dos activos, los documentos solo con páginas (`ChatLibraryState.visualOnly`, de F19) cuentan como buscables:

```
searchable = library.searchable + (if (pagesInChat) library.visualOnly else 0)
```

Con el modo apagado, la regla es la de hoy (FR-001).

**Verificado:** `chatLibraryState` ya cuenta `visualOnly` aparte, por ámbito, y nadie lo lee (F19).

## R12. «Detalle de las páginas»

**Decisión:**
- **Modelo:** `PageDetail` en `domain/multimodal`, con los valores `FAST(70)`, `BALANCED(140)` y `DETAILED(280)`.
  - Cada valor lleva su espera aproximada en décimas de segundo: 100, 115 y 145, las cifras de F18.
  - Si la medición de F20 en el teléfono da otras cifras, se cambian aquí y en los textos, con su entrada en DECISIONS.
- **Almacén:** `multimodal.preferences_pb` gana la clave `page_detail`, con `BALANCED` por defecto (D-083). Un fallo al guardar mantiene el valor anterior, como `default_mode`.
- **Pantalla:** `PageDetailScreen` y `PageDetailViewModel`, una copia de la forma de «Modo al importar PDF» (F19). Ruta `PageDetailRoute` desde la fila de Ajustes.
- **En la respuesta:** la sesión lee el detalle al empezar cada respuesta, como el modo (R1).

**Alternativas:** guardar el detalle en las preferencias de la app (F12): es del modo multimodal, y su almacén ya existe.

## R13. El estado «Leyendo 1 página»

**Decisión:**
- **`ActiveAnswer`** gana `pageTokens: Int?`, que es el detalle de la página que entró.
- **`TurnStatus.READING_PAGE`:** mientras `state == GENERATING`, `pageTokens != null` y todavía no hay texto.
- **El texto** es «Leyendo 1 página · unos %s s…», con la espera de `PageDetail`, formateada con la configuración regional (11,5 en español y 11.5 en inglés).
- **TalkBack:** lo anuncia la región viva de hoy de `LisdataAnswerCard`.

**Motivo:** la constitución IV (1.2.0) pide avisar de la espera de las respuestas con páginas.

## R14. Miniaturas e imágenes en la UI

**Decisión:**
- **Dominio:** `PageImageRepository` en `domain/library`, con `suspend fun image(documentId, page): ByteArray?`. La implementación (`data/library`) busca el `file_name` en `pages` y lee el fichero con `PageFileStore`. Devuelve null si falta.
- **UI:**
  - `PageImageLoader` (`feature/document`, y el chat lo reutiliza, como la hoja de carpetas de F17) decodifica con `BitmapFactory` fuera del hilo principal;
  - para las miniaturas usa `inSampleSize` (una de 40 × 56 dp viene de un JPEG de 768 px);
  - guarda hasta 24 miniaturas en una caché LRU;
  - la imagen grande del detalle y del visor no se guarda en caché.
- **Componente:** `LisdataSourceCitation` gana un parámetro opcional `leading: (@Composable () -> Unit)?`. Si es null, muestra el icono de hoy. El chat le pasa `PageThumbnail`.

**Motivo:**
- la constitución II: la UI no lee SQLite ni ficheros del índice, sino que pasa por un repositorio;
- decodificar está permitido en la UI (no es JNI ni inferencia);
- la caché evita decodificar al desplazarse por la conversación.

**Alternativas:**
- una librería de imágenes (Coil): es una dependencia nueva para un solo uso, con imágenes locales y pequeñas;
- decodificar en el repositorio a `ImageBitmap`: Compose no tiene que estar en `data`.

## R15. La página en el detalle y el visor

**Decisión:**
- **Navegación:**
  - `Document(documentId, chunkId, page)` gana `page: Int?`, la página citada;
  - una ruta nueva, `PageViewer(documentId, page)`, para el visor a pantalla completa.
- **`DocumentViewModel`:** con `page`, la vista previa es `PreviewContent.Page(page, available)` en lugar del fragmento. Si la página ya no está, sale el aviso de R10 y la vista previa normal.
- **Visor (`PageViewerScreen`):**
  - `Image` con `graphicsLayer` (escala y desplazamiento);
  - zoom entre 1× y 4×, con `detectTransformGestures` para el pellizco y el arrastre, y `detectTapGestures(onDoubleTap)` para alternar entre 1× y 2×;
  - semántica con `customActions` «Ampliar», «Reducir» y «Restablecer» (pasos de 1×);
  - la página se conserva al girar (`SavedStateHandle`), y el zoom vuelve a 1×.
- **Lógica:** el zoom va en `ZoomState`, pura y con pruebas JVM: límites, doble toque y los desplazamientos permitidos.

**Motivo:**
- D-083 y el wireframe §5–§6;
- una ruta propia da «Atrás», el giro y la vuelta al detalle sin trabajo extra.

**Alternativas:**
- un `Dialog` a pantalla completa: peor con «Atrás» y el giro;
- una librería de zoom: dependencia nueva para un caso sencillo.

## R16. Notas de F19 que cambian (FR-006b)

**Decisión:** en el detalle, con `pagesOnly`:
- **modo activo:**
  - sin la nota `document_visual_only_note`;
  - «Incluir en respuestas» con `document_include_subtitle`, como un documento de texto;
- **modo apagado:**
  - la nota de siempre, `document_pages_need_multimodal`;
  - el subtítulo nuevo `document_include_subtitle_needs_multimodal`, «Se usará al activar el modo multimodal».

Se borran `document_visual_only_note` y `document_include_subtitle_pages_only`, que ya no se usan (lint `UnusedResources`).

## R17. Diagnóstico de búsqueda en debug

**Decisión:**
- `RetrievalDiagnosticScreen` muestra cinco secciones con los datos de `RetrievalResult.lists` y de las páginas: vectores, palabras, fusionada (la tabla de hoy), páginas y contexto;
- busca con páginas si el modo está activo;
- solo muestra ids, puestos y distancias, y sigue solo en `src/debug`.

## R18. Registros

**Decisión:** la línea `chat answer` gana `page=0|1`, `page_tokens`, `page_dropped` (la imagen faltaba) y `pages_ms`, y la de la recuperación gana `page_hits`. Nunca textos, nombres, rutas ni bytes.

Las pruebas de hoy (`theLogHasOneLineOfNumbers_andNoText` y `oneLogLinePerRetrieval_withNumbersOnly`) se amplían con una página.

## R19. Medición en el teléfono (SC-003, SC-004)

**Decisión:**
- **`VisualChatBenchmarkTest`** (androidTest, `@RequiresModels`, de primer token):
  - crea un índice temporal con la 440M;
  - indexa en Ambos un PDF generado con `PdfDocument`: 12 páginas con tablas y cifras;
  - lanza 20 preguntas con cada detalle, más 20 con el modo activo y sin páginas (otro ámbito);
  - mide, como `ChatBenchmarkTest`, desde `ask()` hasta el primer token visible;
  - informa el p50 y el p95 del primer token, los tokens del prefill, la decodificación y el pico de RSS, en el registro `visual-chat-benchmark` y en `filesDir/reports/visual-chat-*.json`;
  - no falla por tiempo (D-078).
- **`GemmaVisionSmokeTest`** (`@RequiresModels`): con una página generada que dice un número, Gemma lo lee y cita `[1]`.
- **SC-004:** se comprueba a mano en el teléfono, con un escaneado y una imagen de prueba copiados a `Download` y borrados después, como en F19.

**Verificado:**
- el A063 tiene la 440M y Gemma, y no tiene la 270M (D-082);
- los emuladores no tienen Gemma, así que estas pruebas se saltan allí.

## R20. Harness del escritorio (FR-023)

**Decisión:** con el modo apagado, la app no cambia nada que use el harness. Se repiten `run_eval.py ablations` y `order-review`, como en F19, y se compara que salen idénticos.

`offline/` solo gana el generador de la golden de R4 y su prueba (`test_visual_prompt_golden.py`).

## R21. Pruebas

- **JVM:**
  - `VisualPromptGoldenTest` y `PromptBuilderTest` (v5-visual: orden, números y regla);
  - `DefaultChatSessionTest`:
    - modo apagado, prompt idéntico;
    - modo activo sin páginas, la v4;
    - con página: bloques más la página, la página `[k + 1]` y el estado «Leyendo»;
    - imagen que falta;
    - sin bloques y con página;
    - las fuentes de tipo página;
    - el registro;
  - `HybridRetrieverTest`: páginas solo cuando se piden, el ámbito y las listas;
  - `ObserveChatAvailabilityUseCaseTest`: `visualOnly`;
  - `ResolveSourcesUseCaseTest`: `PAGE_GONE`;
  - `ZoomStateTest`;
  - `PageDetailViewModelTest` y el almacén con `page_detail`;
  - pantallas con Robolectric: la tarjeta con miniatura, la vista previa de página, el visor y sus acciones, Ajustes y las notas de F19;
  - `NavigationTest`, `StringsParityTest` y `LayeringTest`;
  - `EngineAnswerEngineTest`: la clave y la configuración según el modo.
- **Instrumentadas:**
  - `ChatMigrationTest` 2 → 3, con las fuentes de antes como `chunk`;
  - `RagIndexPageSearchTest`: ámbito, buscables, `pagesUsable` y orden;
  - `SqliteChatRepositoryTest` con fuentes de página;
  - el coordinador sin dos engines en GPU con la clave de visión;
  - ATF de las pantallas nuevas (`VisualChatAccessibilityTest`);
  - `GemmaVisionSmokeTest` y `VisualChatBenchmarkTest` (`@RequiresModels`).
- **Python:** `test_visual_prompt_golden.py`.
- **Teléfono:** al final y con permiso. El A063 ya está en la 440M.
