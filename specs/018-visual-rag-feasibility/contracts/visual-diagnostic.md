# Contrato: diagnóstico visual en el teléfono (solo debug)

Todo vive en el conjunto de fuentes `debug` (R3). Nada de este contrato existe en `main` ni en `release`.

## 1. Interfaces del diagnóstico

Están en `debug/feasibility/visual/` y no importan LiteRT-LM ni la API de PDF.

```kotlin
/** Gemma with images. Implemented in rag/runtime/litert (debug). */
interface VisionGenerator : ManagedEngine {
    /** Streams the answer to [images] (encoded bytes, in order) followed by [prompt]. */
    suspend fun generate(images: List<ByteArray>, prompt: String, visualTokenBudget: Int?, maxOutputTokens: Int): VisionGeneration
}

data class VisionGeneration(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
    val prefillTokens: Int?,       // BenchmarkInfo, when enabled
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
)

/** EmbeddingGemma 2 with images. */
interface ImageEmbedder : ManagedEngine {
    /** Full raw vector (768), not normalized; truncation and L2 are done with EmbeddingMath. */
    suspend fun embedImage(bytes: ByteArray, visionTokensPerImage: Int): FloatArray
}

/** What a model file declares (FR-004). */
data class VisionModelInfo(
    val text: Boolean, val vision: Boolean, val maxVisionTokenBudget: Int, val visionSignatures: List<Int>,
    val textBackends: List<ComputeBackend>, val visionBackends: List<ComputeBackend>,
)

interface VisionEngineFactory {
    fun generator(file: ModelFile, path: File, backend: ComputeBackend, maxNumImages: Int): VisionGenerator
    fun imageEmbedder(file: ModelFile, path: File, backend: ComputeBackend, visionTokensPerImage: Int): ImageEmbedder
    fun modelInfo(path: File): VisionModelInfo
}

/** PdfRenderer and ImageDecoder. Implemented in data/ingest (debug). */
interface PageRasterizer {
    fun pageCount(pdf: File): Int
    /** Renders [page] (0-based) on white, scaled to [longSidePx], encoded as [format] at [quality]. */
    fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage
    /** Decodes a JPG or PNG upright (EXIF), on white, scaled to [longSidePx]. */
    fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage
}

enum class ImageFormat { WEBP, JPEG, PNG }
data class EncodedImage(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)
```

- **Generación:** `VisionGenerator.generate` fija `ExperimentalFlags.visualTokenBudget` y lo restaura en `finally`, en la cola del LLM. Con `null`, deja el valor por defecto del fichero.
- **Motor:** la implementación crea `EngineConfig(path, backend = GPU, visionBackend = CPU, audioBackend = null, maxNumTokens = 4096, maxNumImages = …, cacheDir)`.
- **Embedder:** `EmbeddingEngineConfig(path, backend = CPU, visionBackend = CPU, …, visionTokensPerImage = …)`, con `EmbeddingOptions(normalize = false, visionTokensPerImage = …)`.
- **Errores:** como en `FeasibilitySuite.scenario` de F02, un error en GPU queda como `NOT_AVAILABLE` y cualquier otro como `FAILED`. Una combinación que el fichero no admite, por ejemplo un presupuesto mayor que su máximo, se marca `NOT_SUPPORTED` sin cargar nada.
- **Coordinación:** cada engine se usa con `EngineCoordinator.withEngine(EngineKey(model, backend)) { … }`, así que nunca hay dos en GPU (FR-016).

## 2. Suite

```kotlin
enum class VisualBlock { GEMMA_INFO, GEMMA_PAGES, READING, EMBED_TEXT, EMBED_IMAGE, PARITY, RASTER, IMAGE_DECODE, PROJECTION }

class VisualSuite(/* coordinator, engines, visionEngines, rasterizer, models, deviceSet, thermal, memory, device, sink, dispatchers */) {
    suspend fun run(blocks: Set<VisualBlock>, confirmHot: suspend (ThermalLevel) -> Boolean): VisualReport
}
```

| Bloque | Qué mide | Necesita |
|---|---|---|
| `GEMMA_INFO` | `VisionModelInfo` de Gemma y de la 440M (FR-004) | Ficheros verificados |
| `GEMMA_PAGES` | Matriz de R4: 1–3 páginas × 70/140/280, trozos, y texto de D-034 + 1 página. Más el mismo prompt sin imagen, como base | Gemma, conjunto del dispositivo |
| `READING` | Las páginas de lectura con su pregunta; guarda las respuestas (FR-006) | Gemma, `reading.json` |
| `EMBED_TEXT` | Consulta y fragmento con la 440M y con la 270M en CPU, 3 + 30 (FR-010) | Las dos variantes |
| `EMBED_IMAGE` | Página a 70 y 140 tokens en CPU, y GPU pedida, 3 + 30; dimensión y norma a 256 y 512 (FR-011) | 440M, conjunto del dispositivo |
| `PARITY` | Vectores de los 20 bytes del conjunto, y de las 10 páginas rasterizadas con `PdfRenderer` (FR-012) | 440M, conjunto del dispositivo |
| `RASTER` | Páginas de PDF de texto y escaneados a 768, 1.024 y 1.536 px, en WebP y JPEG, vertical y apaisado (FR-013) | PDF del conjunto |
| `IMAGE_DECODE` | JPG y PNG grandes, con transparencia y con EXIF, a las tres resoluciones (FR-014) | Imágenes del conjunto |
| `PROJECTION` | 50 páginas seguidas: rasterizado, codificación y embedding, con su estado térmico; proyección a 200 (R14) | 440M, PDF del conjunto |

**Reglas de la suite** (las de F02):
- `releaseAll()` antes y después de cada escenario;
- espera térmica antes de cada bloque;
- se pide confirmación si el estado es «severo» o peor;
- cancelar marca `CANCELLED` y libera;
- informe reescrito tras cada resultado;
- sin texto de prompts, respuestas ni páginas en `AppLogger`.

## 3. Conjunto del dispositivo

```kotlin
class DeviceSetLoader(dir: File) {
    /** Verifies every file against manifest.json; files that do not match are reported and never used. */
    fun load(): DeviceSet
}
```

- **Carpeta:** `getExternalFilesDir(null)/dev-visual/`, que la app crea, como `dev-models` (R7 de F02).
- **Contenido:** lo copia `adb push` desde `offline/build/visual/device-set/`.

## 4. Pantalla y prueba instrumentada

- **`VisualFeasibilityScreen`:**
  - entrada «Viabilidad visual» en `DebugEntries`, con componentes `Lisdata*` y textos en `strings_feasibility.xml` (debug);
  - un botón por bloque, el estado del conjunto del dispositivo y de los modelos, el progreso y «Cancelar»;
  - al pasar a segundo plano, cancela y libera (`ON_STOP`), como F02.
- **`VisualFeasibilityBenchmarkTest`** (`androidTest`, `@RequiresModels`):
  - toma la suite de Koin y lee `-e blocks GEMMA_PAGES,EMBED_IMAGE,…`;
  - responde `confirmHot = { false }`;
  - escribe el informe y registra en logcat solo una línea de cifras por escenario, con la etiqueta `MicroRAG`: `visual-benchmark kind=… params=… p50=… p95=… ttft=…`.

## 5. Prueba de variantes de embedding

**`EmbeddingVariantGoldenTest`** (`androidTest`, `@RequiresModels`):
- **golden:** la 440M frente a `embedding_golden.jsonl`, en CPU y en GPU (omitida en el emulador), con coseno ≥ 0,99 por par. No comprueba la huella del golden, porque mide otro fichero a propósito (R5);
- **identidad:** los mismos textos con la 270M y con la 440M en CPU; informa el mínimo y el medio, y exige un mínimo ≥ 0,9999 (SC-002).

## 6. Capas (`LayeringTest`)

- **LiteRT-LM:** admitido en `src/main/java/…/rag/runtime/litert` y en `src/debug/java/…/rag/runtime/litert`.
- **`android.graphics.pdf`:** admitido en `src/main/java/…/data/ingest` y en `src/debug/java/…/data/ingest`.
- **Regla nueva:** ningún fichero de `src/main` ni de `src/release` contiene `debug.feasibility.visual`, y la pantalla existe en debug.
