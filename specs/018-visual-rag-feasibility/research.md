# Research: Viabilidad del RAG visual (F18)

Decisiones técnicas del plan, con lo que se comprobó para tomarlas. Cada sección sigue el formato Decisión · Motivo · Alternativas.

Comprobaciones hechas el 2026-11-05 en el ordenador de desarrollo (macOS, Apple Silicon):
- `javap` sobre el AAR `litertlm-android-0.18.1`;
- código del wheel `litert-lm-api` 0.18.1;
- cabeceras C de LiteRT-LM en la etiqueta `v0.18.1` (`c/engine.h` y `c/conversation.h`);
- `ModelInfo` de los ficheros fijados;
- sondas con una página real del corpus (`boe-comercio.pdf`, página 1).

---

## R1. API de visión de LiteRT-LM 0.18.1

**Decisión:** se usa la API de visión que ya trae 0.18.1, sin cambiar de versión.

**En Android** (verificado con `javap` en `classes.jar`):

| Pieza | Firma |
|---|---|
| LLM | `EngineConfig(modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir, activationDataType)` |
| Mensaje con imágenes | `Contents.of(vararg Content)`, con `Content.ImageBytes(ByteArray)` y `Content.Text`; `Conversation.sendMessageAsync(Contents, …)` |
| Presupuesto visual de Gemma | `ExperimentalFlags.visualTokenBudget: Integer?`. Es un singleton global: se fija y se restaura bajo la cola del LLM |
| Cifras de la generación | `Conversation.getBenchmarkInfo()` con `ExperimentalFlags.enableBenchmark`, como en F02 (R4) |
| Embedder | `EmbeddingEngineConfig(modelPath, backend, visionBackend, audioBackend, cacheDir, modelFd, maxInputLength, visionTokensPerImage, activationDataType)` |
| Embedding de imagen | `computeEmbedding(listOf(InputData.Image(bytes)), EmbeddingOptions(normalize, insertSpecialTokens, outputSize, visionTokensPerImage))` |
| Metadatos | `ModelInfo.from(path)`: `inputModalities()`, `maxVisionTokenBudget()`, `visionSignatureSelection()`, `supportedBackends(Modality)` |

**En Python** (código del wheel):
- `Engine(model_path, backend, max_num_images, vision_backend, enable_benchmark, …)` y `EmbeddingEngine(model_path, backend, vision_backend, vision_tokens_per_image, …)`;
- `Content.ImageFile`, `Content.ImageBytes` y `Contents` en mensajes y embeddings;
- `Conversation.get_benchmark_info()`, con `last_prefill_token_count`.
- **El presupuesto visual de Gemma no está expuesto en Python**, pero sí en la librería nativa:
  - `litert_lm_conversation_optional_args_set_visual_token_budget(LiteRtLmConversationOptionalArgs*, int)` (`c/conversation.h`, «Added in version 0.1.0»);
  - `litert_lm_engine_settings_set_max_vision_tokens_per_image(LiteRtLmEngineSettings*, int)` (`c/engine.h`).

  El harness envuelve `Conversation._create_optional_args` y llama a la primera con `ctypes`. Va en un único módulo (`offline/eval/vision_runtime.py`) con su prueba.

**Comprobado en el escritorio:** con la misma página y la misma pregunta, el prefill da 101 tokens con 70, 164 con 140 y 304 con 280. Sin fijarlo, también da 304: **el presupuesto por defecto es 280**.

**Alternativas:**
- subir a LiteRT-LM 0.19.0, que lint lleva avisando desde F13: cambiaría el runtime de la app y obligaría a repetir los golden. Queda para F14, como hasta ahora;
- tocar el wheel instalado: no es reproducible.

---

## R2. Lo que declaran los ficheros

**Decisión:** se mide con el fichero de Gemma de la app (D-023) y con la 440M fijada en F02. El teléfono vuelve a leer sus metadatos (FR-004), por si difieren del escritorio.

| Fichero | Imágenes | Firmas de visión | Presupuesto máximo | Backends de texto | Backend de visión |
|---|---|---|---|---|---|
| `gemma-4-E2B-it.litertlm` | Sí | 70, 140, 280 | 280 | CPU, GPU | CPU |
| `gemma-4-E2B-it-gpu.litertlm` | No | — | — | GPU | — |
| `embeddinggemma-2-text-vision-440m.litertlm` | Sí | 70, 140 | 140 | CPU, GPU | CPU |
| `embeddinggemma-2-text-270m.litertlm` | Declara visión, pero sin firmas | — | 140 | CPU, GPU | CPU, GPU |

**Consecuencias:**
- **No existen los 560 tokens por imagen** que suponía el roadmap. Para leer letra pequeña, la alternativa que se evalúa es partir la página en trozos (R11).
- **La visión solo corre en CPU**, tanto en Gemma como en la 440M. En el teléfono, Gemma tendrá el texto en GPU y la visión en CPU dentro del mismo engine, así que no hay un segundo engine en GPU. El embedding de páginas va entero en CPU.
- Los metadatos de visión de la 270M no se usan: su nombre y su ficha dicen «solo texto», y no tiene firmas de visión.

**Alternativas:** buscar otro fichero de Gemma con más presupuesto. Sería un cambio de modelo con descarga nueva, fuera de F18 (spec, Assumptions).

---

## R3. Dónde vive el código del teléfono

**Decisión:**
- **Código:** todo en el conjunto de fuentes `debug`, nada en `main`.
  - El diagnóstico va en `debug/feasibility/visual/`. Solo usa interfaces propias, sin LiteRT-LM ni la API de PDF.
  - Los adaptadores van en los paquetes que exige la constitución II, dentro de `src/debug/java`:
    - `rag/runtime/litert/LiteRtVisionEngines.kt`: Gemma con visión, el embedder de imágenes y `ModelInfo`;
    - `data/ingest/DebugPageRasterizer.kt`: `PdfRenderer` e `ImageDecoder`.
- **`LayeringTest`:**
  - sus dos reglas de paquete (LiteRT-LM y la API de PDF) admiten también la ruta equivalente en `src/debug/java`;
  - una regla nueva comprueba que ni `main` ni `release` nombran `debug.feasibility.visual`, como ya se hace con `debug.retrieval`.
- **Engines:** pasan por el `EngineCoordinator` de `main`, con su `EngineKey(model, backend)`, así que la regla de un solo engine en GPU se cumple igual que en F02.
  - El coordinador guarda un engine por clave. La 440M de texto y la de imágenes, los dos presupuestos de la 440M, y Gemma con y sin visión comparten clave.
  - Por eso **cada escenario usa una sola configuración de engine**, y la suite llama a `releaseAll()` antes y después de cada uno. Una prueba JVM lo comprueba (hallazgo M6 del analyze).
- **Acceso:** una entrada nueva en `DebugEntries` («Viabilidad visual») y una prueba instrumentada que lanza los bloques sin tocar la pantalla (R15).

**Motivo:** hoy `LayeringTest` solo admite LiteRT-LM en `src/main/java/…/rag/runtime/litert` y la API de PDF en `src/main/java/…/data/ingest`, también para el código de debug. Ampliarlo a los mismos paquetes de debug mantiene la regla (el SDK sigue detrás de sus adaptadores) sin meter en release código que solo usa el diagnóstico (FR-001, FR-031).

**Alternativas:**
- poner los adaptadores en `main` sin usarlos: llegarían a release;
- una excepción por fichero en `LayeringTest`: más frágil que la regla por paquete.

---

## R4. Medida de la generación con páginas

**Decisión:**
- **Protocolo:** el de F02 para la generación: 1 de calentamiento y 3 medidas, espera térmica de R6, y `maxOutputTokens = 64`, porque las respuestas de lectura son cortas.
- **Etapa A, para descartar** (FR-005; analyze, D-075), con 3 repeticiones por combinación:
  - **solo páginas:** 1, 2 y 3 páginas × 70, 140 y 280 tokens por página, y 1, 2 y 3 páginas partidas en 2 trozos a 280 por trozo;
  - **mezcla:** el contexto de texto de D-034 más las mismas 12 variantes de páginas;
  - **base:** el contexto de texto solo, sin imagen.

  En total son 25 combinaciones. Con ellas la regla sabe qué candidatas pueden caber en 8 s.
- **Etapa B, la que decide:** cada configuración candidata (como mucho 3, elegidas por la regla con la calidad del escritorio y la etapa A) con 20 preguntas distintas de `contexts.json`. Va una en frío y las demás seguidas, con la espera térmica antes de empezar. El p95 de esas 20 es el que decide, como los 50 de D-057.
- **Por combinación se mide:**
  - la carga en frío con `visionBackend`, frente a la carga sin él (la de F02);
  - el primer token del primer fragmento recibido;
  - los tokens y la velocidad del prefill (`BenchmarkInfo`) y la decodificación;
  - el pico de memoria (`VmHWM`, R5 de F02);
  - el texto generado, que va al informe del dispositivo y nunca a logcat.
- **Coste del codificador de visión:** el primer token con la imagen menos el del mismo prompt sin ella. Se informa junto a `BenchmarkInfo`.
- **De extremo a extremo** (D-026, FR-025): una cifra **compuesta**, que se marca como tal en `benchmarks.md`. Es la suma de:
  - **206,5 ms:** el p95 de la recuperación completa de F17 en el A063, con toda la biblioteca y 10.000 fragmentos. Ya incluye el embedding de la consulta y las dos búsquedas;
  - **53,0 ms:** cota de la tercera lista, la de páginas. Es el p95 de la búsqueda vectorial de F17 sobre 10.000 fragmentos; una biblioteca tiene menos páginas que fragmentos;
  - la diferencia entre el p95 de la consulta con la 440M y con la 270M (R5);
  - el p95 del primer token de la etapa B.

  Sumar los p95 es conservador y no necesita montar el chat completo. Las dos constantes van en la regla congelada (R12).
- **Texto generado:** va solo al JSON del informe, nunca al Markdown ni a logcat (hallazgo M4).

**Motivo:** reutiliza el protocolo y las herramientas de F02, que ya están probados en el teléfono, y da el número que pide la regla de D-073 sin construir F20.

**Alternativas:**
- medir con la app de producto: haría falta F20;
- medir solo la generación, sin la consulta ni la búsqueda: no es lo que pide D-026.

---

## R5. La 440M en el teléfono

**Decisión:**
- **Texto:** con el adaptador de `main` (`LiteRtTextEmbedder`), que no cambia; solo cambia el fichero. Se miden la consulta y el fragmento de unos 350 tokens en CPU, con el protocolo de F02 (3 + 30), y al lado la 270M.
- **Golden e identidad**, en una prueba instrumentada (`EmbeddingVariantGoldenTest`), porque los golden viven en los assets de las pruebas.
  - Antes de medir, comprueba el SHA-256 de la 270M y de la 440M contra `ModelCatalog` (hallazgo M2). Es la comprobación de integridad que la prueba de hoy hace con la huella del golden.
  - Escribe sus cifras en `filesDir/reports/variant-golden-<ts>.json`, para que lleguen a la regla y a `benchmarks.md` (hallazgo M3).
  - Mide:
    - **golden:** cada vector de la 440M frente al `embedding_golden.jsonl`, que se generó con la 270M, en CPU y en GPU, con coseno ≥ 0,99 por par. La prueba de hoy comprueba que la huella del fichero coincide con la del golden; la nueva prueba lo omite a propósito, porque mide precisamente otro fichero;
    - **identidad:** los mismos textos con la 270M y con la 440M en el teléfono, con un coseno mínimo ≥ 0,9999 (SC-002).
- **Imágenes:** con el adaptador nuevo de debug, en CPU, con `visionTokensPerImage` fijado en la configuración y en las opciones (70 o 140).
  - Se guarda el vector completo de 768 y se informan 256 y 512 tras truncar y normalizar con `EmbeddingMath`.
  - La GPU se pide y queda como «no disponible» si el runtime la rechaza (FR-011).

**Motivo:**
- la 270M y la 440M comparten el codificador de texto (R15 de F02, D-025). En el escritorio ya dieron coseno 1,0 en 20 textos;
- F18 lo confirma en el teléfono y con los 50 textos del golden, que es lo que necesita F19 para migrar sin reindexar.

**Alternativas:** regenerar el golden con la 440M. Perdería justo la comparación con el índice actual.

---

## R6. Convertir páginas e imágenes en el teléfono

**Decisión:**
- **PDF:**
  - `android.graphics.pdf.PdfRenderer`, disponible desde API 21, así que funciona en API 31 sin la extensión S 13 que usa la extracción de texto;
  - cada página se pinta en un `Bitmap` `ARGB_8888` rellenado de blanco antes de `render(…, RENDER_MODE_FOR_DISPLAY)`, escalada a la resolución candidata por el lado largo.
- **Codificación:** `Bitmap.compress` en `WEBP_LOSSY` (API 30+) y en `JPEG`, con la calidad fijada. El lado del escritorio usa la misma calidad (R7).
- **Imágenes:** `ImageDecoder`, disponible desde API 28.
  - Aplica la orientación EXIF.
  - Escala con `setTargetSize` en `OnHeaderDecodedListener`.
  - Usa `ALLOCATOR_SOFTWARE`, para poder codificar después.
  - Las transparencias se componen sobre blanco.
- **Resoluciones candidatas:** 768, 1.024 y 1.536 px en el lado largo. Calidad: 85 en WebP y en JPEG.
- **Medidas:**
  - tiempo por página o imagen (p50 y p95, con el protocolo de embeddings: 3 + 30 por resolución);
  - peso codificado;
  - pico de memoria;
  - estado térmico.

**Motivo:** `PdfRenderer` es el motor (pdfium) que usará F19. Las tres resoluciones cubren desde la entrada probable del codificador hasta los trozos de una página partida (R11).

**Alternativas:**
- `PdfRendererPreV`: exige la extensión S 13, y el modo visual tiene que servir también sin ella (D-066);
- PNG sin pérdida: pesa varias veces más y no lo necesita el codificador.

---

## R7. Rasterizar en el escritorio

**Decisión:**
- **Rasterizador:** `pypdfium2==5.14.0`, del 2026-10-04 y la última estable. Trae su propia compilación de PDFium.
- **Imágenes y fotos simuladas:** `pillow==12.3.0`, del 2026-07-01. Se prefiere a la 13.0.0 (2026-10-16), que es una versión mayor reciente.
- **Parámetros:** la resolución (por el lado largo), el formato y la calidad, los mismos que en el teléfono (R6).
- **Caché:** las páginas van a `offline/build/visual/pages/<sha>/<px>/<page>.png`, y su versión codificada se guarda aparte.

**Motivo:** pypdfium2 es de la misma familia de motor que `PdfRenderer`. Aun así, Android usa su propia copia de pdfium, así que la diferencia de rasterizado se mide (R13) en lugar de suponerla.

**Alternativas:**
- pdf2image o Poppler: otro motor, más lejos del teléfono;
- `sips` de macOS: no es reproducible fuera de macOS.

---

## R8. Corpus visual

**Decisión:** 18 documentos y 15 imágenes públicas, más unas 10 fotos simuladas. En total, unas 314 páginas, con las licencias comprobadas en el aviso legal de cada fuente (investigación del 2026-11-05).

**Carpeta:** `offline/corpus_visual/`, fuera de `offline/corpus/`. `app/build.gradle.kts` añade `offline/corpus` a los assets de las pruebas instrumentadas, para la paridad de F10, y el corpus visual, de hasta 40 MB, inflaría el APK de pruebas (hallazgo M11).

**Fuentes aptas** (modalidad general del RD 1495/2011 o licencia abierta; admiten copias derivadas en un repositorio de evaluación con atribución):

| Fuente | Condiciones | Atribución |
|---|---|---|
| INE | CC BY 4.0, salvo indicación en contra. No se puede desnaturalizar la información ni sugerir patrocinio. El pie de la web dice CC BY-SA 4.0, al contrario que el aviso legal, así que se anota | «Fuente: Sitio web del INE: www.ine.es», con la fecha de actualización |
| AEAT | Reutilización comercial y no comercial, con modificación y sin desnaturalizar | La fuente y la fecha de la última actualización |
| AEMET (solo `www.aemet.es`) | Ley 37/2007, manteniendo el logotipo y la fecha | «Información elaborada por la Agencia Estatal de Meteorología» |
| BOE, incluida la Gazeta histórica | Licencia tipo del 27-06-2024. Hay que identificar los cambios y no sugerir carácter oficial | «Basado en datos de la Agencia Estatal Boletín Oficial del Estado», con enlace |
| BNE / BDH | Obras de dominio público: CC BY 4.0 o equivalente | Fondos de la Biblioteca Nacional de España |
| Wikimedia Commons | Licencia de cada archivo: CC0, dominio público, CC BY o CC BY-SA. Con CC BY-SA, la copia reescalada mantiene la licencia | Autor, título, licencia y enlace, en `SOURCES.md` |

**Fuentes descartadas:**
- el Banco de España («Reservados todos los derechos»; solo admite la reproducción fiel);
- Arcimís de AEMET y la Biblioteca Jurídica Digital del BOE (CC BY-NC-ND);
- y las fuentes sin licencia de reutilización clara: datos universitarios (CC BY-NC), Biblioteca Virtual de Prensa Histórica, Renfe y Metro.

**Lista:** se recortan las páginas largas con `fetch.pages`, como en F05.

| Categoría | Documentos | Páginas |
|---|---|---|
| Tablas | AEAT, informe mensual de recaudación de 09/2026 (39); INE, Anuario 2020, Demografía (28); BOE, Orden HAC/1425/2025 de módulos, recortada a sus anexos (40 de 83); INE, notas de prensa de la EPA del 3T/2026 (6), del IPC de 09/2026 (5) y de Coyuntura Turística Hotelera de 09/2026 (4); BOE, fiestas laborales de 2026 (5) | 127 |
| Gráficos y mapas | INE, «España en cifras 2026» (60); AEMET, resúmenes anuales de 2025 (16) y 2024 (17) y mensuales de 09/2026 (18) y 07/2026 (17); INE, notas de la ECP del 2T/2026 (6) y de la ECV 2025 (5) | 139 |
| Escaneados | Gaceta de Madrid de 10-12-1931 (Constitución), de 01-04-1900 (Presupuestos, con tablas), de 07-12-1900 (cotizaciones) y de 07-03-1797. Doce páginas de cada una | 48 |
| Imágenes sueltas | 15 de Commons: mapas climáticos del IGN, pirámides de población, climograma, tabla periódica, horarios, billete de tren, portadas de la Gaceta, paneles de sendero y un cartel de precios | 15 |
| Fotos simuladas | Unas 10 páginas del corpus convertidas en foto (R9, D-073) | ≈ 10 |

**Detalles:**
- **Escaneados sin capa de texto:** los cuatro números de la Gazeta llevan una capa OCR (ABBYY). Se rasterizan y se reconstruyen como PDF solo de imagen, una transformación que el BOE admite al indicar el cambio. Así son como los escaneados que no se pueden importar en la app de hoy (F06). La transformación queda en `transformations`.
- **PDF de AEMET:** están cifrados con contraseña de propietario. pypdfium2 los abre, y en el teléfono se comprueba con `PdfRenderer` (bloque `RASTER`). Si no los abre, se registra y se sustituyen por los resúmenes del INE.
- **Atención en dos imágenes:**
  - el horario de Renfe de 1981 lleva CC BY 2.0, declarada por quien lo subió a Commons;
  - el cartel de precios parece de fuera de España.

  Si hay dudas al revisarlas, se cambian por las alternativas de la lista: paneles de sendero o una página de la Gaceta de 1935.
- **Tamaño:** los PDF vectoriales del INE, la AEAT y el BOE pesan poco. Los escaneados, unos 300 KB por página. El total queda por debajo de los 40 MB del modelo de datos.
- **URL y licencias de cada fichero:** van al manifiesto y a `SOURCES.md`. La lista de partida, con alternativas, está en la investigación del 2026-11-05.

**Motivo:**
- cubre los cuatro tipos de D-066 con contenido real en español;
- las cifras clave de las tablas y los gráficos solo están en la imagen o en su estructura;
- las licencias permiten guardar copias en git, como el corpus de F05.

**Alternativas:**
- usar solo los PDF del corpus de F05: tienen pocas tablas (`tabla` tiene 0 preguntas en F05) y ningún gráfico ni escaneado;
- un corpus en inglés: la app responde en español (D-061).

---

## R9. Conjunto de prueba visual

**Decisión:**
- **Ficheros:**
  - `offline/testset/visual/questions.jsonl`, con las preguntas;
  - `offline/testset/visual/review.csv`, con los veredictos de D-033.
- **Campos:**
  - `id`, `type`, `question`, `doc`, `pages`, `expected_answer`, `key_facts`, `review` y `review_note`;
  - `pages` es una lista de `[doc_sha, page]`, con la página empezando en 1. Una imagen suelta es la página 1 de su documento.
- **Tipos:** `tabla`, `grafico`, `escaneado`, `imagen`, `mixta`, `control` y `sin_respuesta`.
- **Reparto objetivo,** con al menos 100 aceptadas y al menos el 60 % visuales (SC-006):

  | Tipo | Preguntas |
  |---|---|
  | Tabla | 20 |
  | Gráfico | 15 |
  | Escaneado | 15 |
  | Imagen (unas 8 de ellas, fotos simuladas) | 15 |
  | Mixta | 5 |
  | Control de texto | 20 |
  | Sin respuesta | 10 |

- **Redacción:** agentes separados redactan a partir de las imágenes de página, con la página delante. La pregunta no copia frases literales, y los `key_facts` son las palabras y cifras que debe contener la respuesta, como en F05.
- **Revisión** (D-033, D-073): la sesión principal mira cada página. Criterios:
  - pregunta realista y no ambigua;
  - respuesta deducible de esa página;
  - `key_facts` presentes en ella;
  - y, en las sin respuesta, comprobación en el corpus de que no la tienen.
- **Validación** (reglas V1–V8 en `visual_testset.py`, como T1–T9 de F05): campos y tipos, páginas que existen, una sola versión por documento, mínimos por tipo, y `key_facts` vacíos solo en las sin respuesta.
- **Congelado:** el conjunto se fija con su SHA-256 antes de ejecutar las ablaciones (R12) y no cambia después.

**Motivo:**
- la relevancia de F05 sale de la evidencia literal en el texto extraído, que no existe en escaneados ni imágenes;
- la página es la unidad que citará F20;
- el juez por reglas de F05 sirve igual si los `key_facts` están en la página.

**Alternativas:**
- relevancia por fragmento: no existe en escaneados ni imágenes;
- un juez con un modelo grande: F05 se hizo con reglas y muestra manual, y así se puede comparar.

---

## R10. Recuperación por página

**Decisión:**
- **Índice de texto del corpus visual:** la misma construcción de F05 con los parámetros de D-034, en su propia base (`build/index/visual-text.db`). El corpus de texto de F05 no se toca (FR-024).
- **De fragmento a página:**
  - cada fragmento se asigna a las páginas que cubre su texto, buscando sus caracteres en los bloques por página de `build/extract`;
  - no se usa `chunks.page`, que solo guarda la página donde empezó el búfer y queda desfasada tras el solape (hallazgo del mapa del harness).
- **Listas:**
  - **vector de fragmentos y FTS de fragmentos:** las de hoy, 50 + 50 candidatos. Cada lista se convierte en lista de páginas, con la mejor posición de sus fragmentos;
  - **vector de páginas:** la consulta con el prefijo de consulta y la 440M, contra los vectores de página truncados a 256 o 512 (`build/index/visual-pages-<px>-<tokens>.db`, con SQLite-Vector como en F05), 50 candidatos.
- **Fusión:**
  - «Texto»: RRF (k = 60) de las dos listas de fragmentos, llevadas a páginas;
  - «Visual»: la lista de páginas sola;
  - «Ambos»: RRF (k = 60) de las tres.
  - **D-076:** la RRF de las tres listas deja los escaneados con un Recall@5 de 0 y las imágenes en 0,25, porque una página sin texto solo está en una lista. Las páginas de la respuesta salen de «Visual», y la de tres listas queda como hallazgo.
- **Métricas por página y por tipo de pregunta:** Recall@1, @3, @5 y @10, MRR y nDCG@10.

**Motivo:** la página es la unidad común de las tres listas. La fusión es la RRF de la constitución III, con una lista más, como en las variantes de trigramas de F10.

**Alternativas:**
- fusionar en espacios de id distintos: no se puede comparar;
- mantener fragmentos y páginas separados hasta el final: no da una sola lista para la regla de duplicados.

---

## R11. Respuestas con páginas

**Decisión:**
- **Variantes de respuesta,** sobre las mismas preguntas:
  - **«Texto»:** el contexto de D-034 (4 bloques, como mucho 3 por documento, 1.719 tokens) con la plantilla v4;
  - **«Páginas»:** las *P* primeras páginas de «Visual» (*P* = 1, 2 o 3; D-076) como imágenes, con un presupuesto *B* de 70, 140 o 280 tokens. Variante de **trozos:** cada página partida en 2 mitades con un 10 % de solape, solo a 280 por trozo, como en el teléfono (hallazgo H1);
  - **«Mezcla»:** los bloques de texto de D-034 más *P* páginas, con la **regla de duplicados:** si una página entra como imagen, sus fragmentos salen del texto, y los huecos los ocupan los siguientes bloques.
- **Plantilla v5-visual** (solo se evalúa, la app no cambia):
  - la v4, con cada fuente numerada `[n]`;
  - una fuente de página va como su etiqueta («[n] Documento, página p») seguida de su imagen en el mismo mensaje (`Contents` intercalado);
  - las instrucciones de citar y de negarse no cambian.
- **Juez automático:** el de F05 por reglas, que recorre la malla y elige los parámetros.
  - Una cita `[n]` es correcta si su fuente es una página relevante, o un bloque de texto cuyas páginas incluyen una relevante.
  - Una muestra manual de 30 respuestas por variante principal, revisada según D-033, comprueba el juez. Las variantes principales son «Texto» y la mejor de «Páginas» y de «Mezcla» según el juez.
- **Revisión decisiva** (D-075): las cifras de la regla salen de una revisión manual, hecha por el asistente según D-033, de las dos variantes que deciden: «Texto» y la configuración elegida. Son unas 200 respuestas del conjunto visual, como hizo D-035 con F05.
- **Pérdida en texto** (D-075): la configuración elegida también responde las 323 preguntas respondibles de F05.
  - Sus 6 PDF ganan páginas rasterizadas y su índice de páginas; los documentos MD y TXT siguen solo con texto.
  - Las respuestas idénticas a las de «Texto» conservan su veredicto de `offline/reports/manual-judgement.csv`, como `template_review.merged_rows` de F11. Solo se juzgan a mano las que cambian.
- **Coste:** se informan los tokens de texto, los visuales (*P* × *B* × trozos) y el total. El límite de 2.000 tokens estimados de D-034 sigue valiendo para la parte de texto. La parte visual la limita el primer token (R12).

**Motivo:** son las tres preguntas de F18: si leer la página da mejores respuestas que su texto, cuántas páginas caben y con qué presupuesto, y si mezclar empeora el texto. La plantilla v5 es la mínima que hace citable una página.

**Comprobado en el escritorio** (una página de índice con puntos guía):
- con cualquier presupuesto y con mitades, Gemma no lee el número de página pequeño;
- sí lee la referencia BOE de la cabecera.

Es un primer indicio de que la letra pequeña será el límite. La evaluación lo mide con el conjunto completo.

**Alternativas:**
- más de 3 páginas: el primer token no cabría (D-057);
- reordenar con Gemma: es otra feature (F14).

---

## R12. Regla fijada antes de evaluar

**Decisión:** `offline/config/visual_rules.json` se escribe y se hace commit **antes** de ejecutar las ablaciones, con el SHA-256 del conjunto de prueba congelado. Contiene:

1. **La regla de sí o no de D-073:**
   - en las preguntas visuales, «Ambos» da al menos 15 puntos más de respuestas correctas que «Texto»;
   - en las 323 respondibles de F05, como mucho 2 puntos menos;
   - p95 de extremo a extremo ≤ 8 s con al menos 1 página, con la fórmula compuesta de R4 y sus constantes (206,5 ms y 53,0 ms) y el p95 de la etapa B;
   - y la 440M pasa SC-002 (golden ≥ 0,99 e identidad ≥ 0,9999).

   Las cifras de calidad salen de la revisión decisiva de R11. «Ambos» es la configuración que eligen las reglas del punto 2.
2. **La regla para elegir los parámetros:**
   - **resolución y tokens del embedder:** la combinación con mejor Recall@5 visual en «Visual» (D-076). Si hay otra a menos de 0,02 que cuesta menos (menos píxeles o menos tokens), se elige esa;
   - **dimensión de página:** 256, salvo que 512 dé al menos 0,03 más de Recall@5 visual;
   - **páginas, presupuesto y trozos:** la variante con más respuestas correctas visuales entre las que cumplen el primer token en el teléfono. Si hay otra a menos de 1 punto con menor primer token, se elige esa;
   - **mezcla o solo páginas:** «Ambos» es siempre una variante de la mezcla. En F20, los documentos de solo texto no tienen páginas, así que una respuesta solo con páginas los dejaría sin contexto. «Páginas» queda como ablación que informa del valor de las imágenes, y la pérdida en texto de la mezcla la mide la regla en F05.
3. **El protocolo:** las semillas, las versiones, las huellas, y las 20 preguntas de la etapa B.

**Cómo se hace cumplir** (hallazgo M10):
- los subcomandos de ablación se niegan a correr si `visual_rules.json` difiere de su versión en `HEAD`, o si falta la huella del conjunto;
- cada ejecución guarda el hash git del fichero de la regla.

**Orden de la decisión** (hallazgo M9):
1. `visual-decide --stage retrieval`, con solo las cifras del escritorio, fija la resolución, los tokens y la dimensión, antes de las ablaciones de respuesta;
2. `visual-decide --stage candidates` propone las candidatas para la etapa B del teléfono;
3. `visual-decide` aplica la regla completa con el informe del teléfono y la revisión decisiva.

**Motivo:** D-073 y FR-025 piden fijar la regla antes de ver resultados, como en D-057. El commit con fecha lo hace comprobable.

**Alternativas:** elegir a la vista de las cifras, que rompe FR-025.

---

## R13. Golden visual: el teléfono frente al escritorio

**Decisión:**
- **Conjunto del dispositivo:** `run_eval.py visual-device-set` exporta a `offline/build/visual/device-set/`:
  - 20 imágenes: 10 páginas rasterizadas en el escritorio, 5 imágenes públicas y 5 fotos simuladas, en sus bytes codificados;
  - los 10 PDF de origen de esas páginas;
  - las páginas de la prueba de lectura (FR-006), con su pregunta y sus `key_facts`;
  - `contexts.json`, con 20 preguntas del conjunto visual: el contexto de texto de D-034, las páginas y los trozos de cada una, en el orden de la plantilla v5-visual;
  - **`decode/`**, para `IMAGE_DECODE` (hallazgo M5):
    - las dos fotos de 12 MP del corpus (el panel de sendero y el cartel de precios);
    - un PNG con transparencia;
    - un JPEG girado por EXIF;
    - un JPEG en CMYK, generado con Pillow a partir de una página del corpus;
  - los vectores completos del escritorio, a 70 y a 140 tokens;
  - un `manifest.json` con el SHA-256 de cada fichero.
- **En el teléfono:** el conjunto llega por `adb push` a `files/dev-visual/`, y el diagnóstico lo verifica con el manifiesto antes de usarlo.
  - Gemma recibe las partes en el mismo orden que en el escritorio: etiqueta, imagen y texto, como en la plantilla v5-visual. Así la prueba de lectura compara lo mismo en los dos lados (hallazgo M7).
  - Embebe los mismos bytes y rasteriza las mismas páginas con `PdfRenderer`.
  - Su informe lleva los vectores, en JSON fuera de logcat.
- **De vuelta en el escritorio:** `run_eval.py visual-parity <informe>` calcula:
  - **mismos bytes:** el coseno por imagen, con umbral ≥ 0,99 (SC-005);
  - **rasterizado del teléfono frente al del escritorio:** el coseno informado (FR-012);
  - **lectura:** el juez de F05 sobre las respuestas del teléfono, frente a las del escritorio con las mismas páginas.

**Motivo:** es la «Condición para mezclar vectores» del informe, ahora para imágenes. Sin ella, los parámetros del escritorio podrían no valer en el teléfono.

**Alternativas:** comparar solo píxeles. No dice nada de los vectores, que es lo que usa la búsqueda.

---

## R14. Proyección de un PDF de 200 páginas

**Decisión:**
- **Tanda sostenida:** 50 páginas del corpus (de texto y escaneadas), cada una convertida a la resolución elegida, codificada y embebida en CPU.
- **Cifras:** el p50 y el p95 por página, el estado térmico al principio, a la mitad y al final, y la proyección a 200 páginas (p50 × 200, y p95 × 200 como cota).
- **Sin checkpoint ni publicación:** eso es F19.

**Motivo:** basta para saber si la ingesta visual de F19 tardará minutos o decenas de minutos, y si el calor la frena.

**Alternativas:** 200 páginas reales. Tardaría bastante más y no cambia la decisión.

---

## R15. Pruebas

**Decisión:**
- **JVM** (`testDebugUnitTest`):
  - `VisualSuite` con fakes, siguiendo `SuiteFixture` de F02: matriz de las dos etapas, combinación no admitida, cancelación, liberación de engines, una configuración de engine por escenario, informe y espera térmica;
  - `VisualFeasibilityViewModel`, antes de la pantalla (hallazgo M8);
  - el manifiesto de `dev-visual`;
  - el informe visual;
  - `LayeringTest`, con las reglas de R3.
- **Instrumentadas:**
  - `VisualFeasibilityBenchmarkTest` (`@RequiresModels`), que lanza los bloques con `-e blocks …` y escribe el informe, como `FeasibilityBenchmarkTest`;
  - `EmbeddingVariantGoldenTest` (`@RequiresModels`);
  - `DebugPageRasterizerTest`, sin modelos, con los PDF de fixtures de F06 y una imagen con EXIF;
  - en el emulador, la parte sin modelos de toda la batería.
- **Python** (`offline/tests`):
  - rasterizado;
  - fotos simuladas: deterministas por semilla;
  - conjunto de prueba (V1–V8);
  - de fragmento a página;
  - RRF por página;
  - regla de duplicados;
  - plantilla v5-visual;
  - juez de citas a página;
  - paridad;
  - presupuesto por `ctypes` (con un fake de la librería);
  - regla de R12.
- **Teléfono:** la batería completa, más los bloques visuales y la prueba de variantes, en la última fase y con el permiso del usuario.

**Motivo:** el patrón de F02 y F05 funcionó: la lógica se prueba en la JVM, y el dispositivo solo mide.

---

## R16. Riesgos y salidas

| Riesgo | Señal | Salida |
|---|---|---|
| El primer token con 1 página pasa de 8 s | Matriz de R4 | Decide el usuario: excepción o parar (D-073) |
| La 440M no da los mismos vectores que la 270M | SC-002 | Decide el usuario: reindexar en F19 o mantener la 270M para el texto |
| La letra pequeña no se lee con ningún presupuesto | R11 en el escritorio | La regla decide. Los gráficos, los escaneados con letra grande y las imágenes pueden compensar |
| El presupuesto por `ctypes` deja de funcionar con otro wheel | Prueba de R1 | Se fija la versión. Si falla, solo se evalúa el presupuesto por defecto en el escritorio y los demás se miden en el teléfono |
| El rasterizado del teléfono se aleja del escritorio | R13 | Se documenta. Se eligen los parámetros más estables, y F19 rasteriza siempre en el teléfono |
| Licencias del corpus | R8 | Fuera lo dudoso |
| Memoria con la visión cargada | `VmHWM` de R4 | Se documenta, y F19 libera el engine entre tandas |
| La evaluación tarda mucho en el escritorio | Tiempo por tanda | Las tandas se reanudan, como en F05. Una tanda parcial no cuenta para la decisión |
