# Contrato del runtime on-device — F02

- **Paquetes:**
  - `com.lisdatasolutions.microrag.rag.runtime`: interfaces, `EngineCoordinator`, `EmbeddingMath` y `EmbeddingPrefixes` (source set `main`);
  - `…rag.runtime.litert`: adaptadores de LiteRT-LM (`main`). Es el **único** paquete que importa `com.google.ai.edge.litertlm.*`;
  - `…domain.model`: `ModelId`, `ModelFile`, `ModelCatalog`, `ModelFileState` y `ComputeBackend`;
  - `…data.models`: ficheros y verificación.
- **Regla de capas:** la UI y los ViewModels **no** importan `com.google.ai.edge.litertlm.*` (constitución II). Solo acceden a las interfaces de este contrato.
- **Estabilidad:** F04, F07, F10 y F11 reutilizan estas interfaces. Si cambian, el cambio se documenta en el plan de la feature que lo haga.

## Embeddings

```text
interface TextEmbedder : ManagedEngine {
  val model: ModelId
  suspend fun initialize()                    // en AppDispatchers.embeddings
  suspend fun embedQuery(text: String): FloatArray        // "task: search query | text: " + text.trim()
  suspend fun embedDocument(content: String, header: String): FloatArray
      // "task: search result | text: " + (header.trim() + "\n" + content.trim()).trim()
  val rawDimensions: Int?                     // dimensión de salida del modelo (768)
  val declaredMaxContextTokens: Int?          // ModelInfo.maxContextTokens()
}
```

**Garantías:**
- Devuelve **256** componentes con norma L2 = 1 ± 1e-5 (truncado y renormalización en `EmbeddingMath`, código propio).
- Configuración: `maxInputLength = 512` y `EmbeddingOptions(normalize = false, outputSize = null)`.
- Todo se ejecuta en `AppDispatchers.embeddings`.
- Prefijos: constantes `EmbeddingPrefixes.DOCUMENT` y `EmbeddingPrefixes.QUERY` (se guardarán en `rag_meta` en F04).

## Generación

```text
interface TextGenerator : ManagedEngine {
  val model: ModelId
  suspend fun initialize()                    // en AppDispatchers.llm
  fun generate(prompt: String, maxOutputTokens: Int): Flow<GenerationEvent>
  fun cancel()                                // Conversation.cancelProcess()
}

sealed interface GenerationEvent {
  data class Token(val textDelta: String)
  data class Finished(val stats: GenerationStats)     // de BenchmarkInfo + reloj de pared
  data class Failed(val reason: String)
}
```

**Garantías:**
- Usa una conversación nueva por llamada (informe, Generación).
- Muestreo greedy en el diagnóstico.
- `maxNumTokens = 4096`.
- `cacheDir = cacheDir/litertlm`.
- El texto nunca se escribe en logs.

## Fábrica de motores

```text
interface EngineFactory {
  fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder      // sin inicializar
  fun generator(file: ModelFile, path: File, backend: ComputeBackend,
                config: GenerationConfig): TextGenerator                                 // sin inicializar
  fun metadata(path: File): ModelMetadata          // maxContextTokens, supportedBackends (ModelInfo)
  fun clearCompiledCache()                         // borra cacheDir/litertlm (inicialización en frío)
  var benchmarkStatsEnabled: Boolean               // ExperimentalFlags.enableBenchmark
}
data class GenerationConfig(val maxNumTokens: Int, val maxOutputTokens: Int, val greedy: Boolean)
data class ModelMetadata(val maxContextTokens: Int?, val supportedBackends: List<ComputeBackend>)
data class GenerationStats(
  val initSeconds: Double?, val ttftSeconds: Double?, val ttftWallMs: Long,
  val prefillTokens: Int?, val prefillTokensPerSecond: Double?,
  val decodeTokens: Int?, val decodeTokensPerSecond: Double?,
)
```

- Implementación: `LiteRtEngineFactory` en `rag.runtime.litert`.
- Los campos de `GenerationStats` que vienen de `BenchmarkInfo` son nulos si `benchmarkStatsEnabled = false`. Solo el diagnóstico de debug lo activa.

## Coordinación

```text
class EngineCoordinator(logger: AppLogger) {
  suspend fun <T : ManagedEngine, R> withEngine(
      key: EngineKey,
      factory: suspend () -> T,          // crea e inicializa (en su dispatcher)
      block: suspend (T) -> R,
  ): R
  suspend fun release(key: EngineKey)
  suspend fun releaseAll()               // salida del diagnóstico / onTrimMemory ≥ UI_HIDDEN
  val loaded: StateFlow<Set<EngineKey>>  // observable para tests y UI
}
interface ManagedEngine : AutoCloseable { val backend: ComputeBackend }
data class EngineKey(val model: ModelId, val backend: ComputeBackend)
```

**Reglas:**
- **Carga:** un `Mutex` de carga protege el mapa de motores. Si `key.backend == GPU` y hay otro motor de GPU cargado, se cierra **antes** de llamar a `factory` (FR-012).
- **Uso:** cada motor tiene su propio `Mutex` de uso, que `block` mantiene mientras se ejecuta. `release` y `releaseAll` esperan a que termine el uso en curso antes de cerrar. Así nunca se cierra un motor nativo mientras infiere.
- **Cancelación:** quien llama cancela su corrutina antes de liberar (el diagnóstico lo hace en `ON_STOP`). `block` responde a la cancelación en menos de 1 s (FR-011).
- **Restricción:** `withEngine` no se anida.
- **Invariante comprobada por test:** `loaded.value.count { it.backend == GPU } ≤ 1` en todo momento, incluido el intervalo entre cerrar uno y crear otro.

## Ficheros de modelo (source set `main`, reutilizado por F03)

```text
object ModelCatalog { val all: List<ModelFile>; fun byId(id: ModelId): ModelFile }

interface ModelFileRepository {
  val states: StateFlow<Map<ModelId, ModelFileState>>
  suspend fun refresh()                                    // escanea noBackupFilesDir/models
  suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean): ModelFileState
      // copia a models/<fileName>.part calculando SHA-256 en streaming;
      // si coincide → rename atómico a models/<fileName>; si no → borra .part (Mismatch)
  suspend fun verify(id: ModelId): ModelFileState          // re-hash antes de cargar
  fun pathOf(id: ModelId): File?                           // solo si Verified
}

object Sha256 { fun of(input: InputStream, onBytes: (Long) -> Unit = {}): String }
```

- Ubicación de los modelos: `noBackupFilesDir/models/`.
- Antes de copiar se comprueba el espacio libre (`StatFs`, tamaño del catálogo + 64 MB). Si falta, `Failed("no space")`.
- Todo el E/S se ejecuta en `AppDispatchers.io`.

## Importación de desarrollo (source set `debug`, solo F02)

```text
class DevModelImporter(context: Context, repository: ModelFileRepository) {
  val devDir: File                                          // getExternalFilesDir(null)/dev-models
  fun pendingCopies(): Map<ModelId, Long>                   // ficheros del catálogo presentes en dev-models
  suspend fun importAll(): Map<ModelId, ModelFileState>     // installVerified(..., deleteSourceOnSuccess = true)
}
```

La release no contiene `DevModelImporter` ni lee `dev-models`.
