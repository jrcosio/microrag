# Contrato — API de descarga, verificación y onboarding

Interfaces internas entre capas: UI → ViewModel → caso de uso → repositorio. Las firmas son orientativas; los nombres y la semántica son vinculantes.

## Dominio (`domain/`)

```kotlin
// domain/model
enum class OnboardingStage { NOT_STARTED, STARTED, COMPLETED }
enum class NetworkKind { WIFI, MOBILE, NONE }
sealed interface ModelSetupStatus { /* data-model §2 */ }

// domain/onboarding
interface OnboardingStore {
    val stage: Flow<OnboardingStage>
    val downloadRequested: Flow<Set<ModelId>>
    val downloadPaused: Flow<Boolean>
    val downloadConfirmed: Flow<Boolean>
    suspend fun setStage(stage: OnboardingStage)
    suspend fun requestDownload(ids: Set<ModelId>)   // añade a downloadRequested; pone confirmed = true y paused = false
    suspend fun setPaused(paused: Boolean)
    suspend fun clearRequest(id: ModelId)            // al quedar el modelo Ready
}

interface DeviceResources {
    fun totalRamBytes(): Long
    fun freeBytes(): Long            // StatFs(noBackupFilesDir)
    fun currentNetwork(): NetworkKind
    val hasInternet: Flow<Boolean>
}

interface ModelDownloadController {
    val progress: StateFlow<DownloadProgress>
    fun schedule(pendingBytes: Long)  // programa UIDT (API ≥ 34) o WorkManager (31–33). Solo con la app visible
    fun cancel()                      // cancela el job; el parcial se conserva
    fun isScheduledOrRunning(): Boolean
}
```

**Casos de uso** (`domain/onboarding/`), cada uno con un único `operator fun invoke`:

| Caso de uso | Qué hace |
|---|---|
| `ResolveStartDestinationUseCase` | `stage` → `StartDestination` (R10) |
| `StartOnboardingUseCase` | `stage = STARTED` |
| `ObserveModelSetupUseCase` | Combina ficheros, progreso, preparación, preferencias y red, y devuelve `Flow<Map<ModelId, ModelSetupStatus>>` |
| `BuildDownloadConfirmationUseCase` | Modelos pendientes, total, espacio libre y el que quedará, red y aviso de RAM |
| `StartModelDownloadUseCase` | Comprueba el espacio (FR-016), `requestDownload(pendientes)` y `schedule(bytesPendientes)`. Devuelve `Started` o `NoSpace(needed)` |
| `PauseModelDownloadUseCase` / `ResumeModelDownloadUseCase` | `setPaused` + `cancel` / `schedule` |
| `RetryModelUseCase` | Reintenta un `Failed` (vuelve a programar) o un `Incompatible` (vuelve a abrir la confirmación) |
| `ReconcileDownloadsUseCase` | Al hacerse visible la Preparación: `refresh()`. Si hay pedidos sin pausa y sin job, `schedule`. Verifica los `Unverified` completos |
| `PrepareModelsUseCase` | Ejecuta la prueba de compatibilidad (R8) en los `Verified` sin backend, de uno en uno, en un scope de aplicación |
| `ImportModelFileUseCase` | Abre el `Uri` con `ContentResolver` y llama a `installFromStream`. Si la huella no corresponde, el resultado es `Incompatible` («El archivo no corresponde a …») |
| `CompleteOnboardingUseCase` | Solo si los dos modelos están `Ready`: `stage = COMPLETED` |

## Datos: descarga (`data/download/`, el único paquete con acceso a red)

```kotlin
class ModelDownloader(
    private val allowedHosts: HostAllowlist = HostAllowlist.HuggingFace, // huggingface.co, *.hf.co
    private val requireHttps: Boolean = true,                             // solo los tests lo desactivan
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    /** Descarga [file] en [partial] reanudando desde partial.length(). Lanza CancellationException al cancelar. */
    suspend fun download(file: ModelFile, partial: File, onProgress: (bytesDone: Long) -> Unit): DownloadResult
}
sealed interface DownloadResult { data object Complete; data class Error(val error: DownloadError) }
```

**Protocolo** (research R2):

| Paso | Regla |
|---|---|
| Petición | `GET file.downloadUrl`, con `Range: bytes=<n>-` si `n > 0`. Cabeceras: `Accept-Encoding: identity` y `User-Agent: MicroRAG/<versionName>` |
| 3xx | `Location` absoluta o relativa. Debe ser `https` (si no, `InsecureScheme`) y su host debe estar en la lista (si no, `ForbiddenHost`). Máximo 5 saltos |
| 206 | `Content-Range: bytes <n>-<end>/<total>`, con el inicio igual a `n` y el total igual a `file.sizeBytes`. Si no, `RangeMismatch`: se trunca el parcial y se repite desde 0 una sola vez |
| 200 | Se trunca el parcial y se escribe desde 0. `Content-Length`, si existe, debe ser igual a `sizeBytes` |
| 416 | Si `partial.length() == sizeBytes`, `Complete`. Si no, se trunca y se repite desde 0 |
| Otros 4xx/5xx | `Http(code)` |
| Final | `partial.length() == sizeBytes`, o `Network("short read")` |
| Escritura | En modo *append*, con `sync()` cada 64 MiB y al salir, también al cancelar |

```kotlin
class ModelDownloadRunner(downloader, repository: ModelFileRepository, store: OnboardingStore,
                          resources: DeviceResources, dirs: ModelDirs, clock, logger) {
    val progress: StateFlow<DownloadProgress>
    /** Procesa la cola (EG2 y después Gemma) de los modelos pedidos que no están Verified. */
    suspend fun runQueue(): DownloadOutcome?   // null si no quedaba nada
}
```

**Reglas de `runQueue`:**
1. Para cada modelo pedido en orden de catálogo que no esté `Verified`:
   1. Comprueba el espacio: bytes pendientes + 500 MB. Si no hay, `NoSpace`.
   2. Descarga, con hasta 5 intentos y espera de 2, 4, 8, 16 y 32 s. El contador vuelve a cero si un intento avanza ≥ 1 MiB.
   3. Llama a `repository.adopt(id, partial)`. Si devuelve `Mismatch`, el resultado es `Failed(HashMismatch)` y el parcial ya está borrado.
2. Un resultado que no sea `Completed` corta la cola y se devuelve.
3. Ningún log incluye URL firmadas ni rutas de usuario. Solo se registran el `ModelId`, códigos HTTP y bytes.

**Planificadores** (`data/download/`):

| Clase | API | Responsabilidad |
|---|---|---|
| `ModelDownloadJobService : JobService` | ≥ 34 | `onStartJob` → `setNotification(…, JOB_END_NOTIFICATION_POLICY_REMOVE)` y lanza `runner.runQueue()` en un scope. Al terminar, `jobFinished(params, wantsReschedule = false)`. `onStopJob` cancela el scope y devuelve `true` (reprogramar), salvo con `STOP_REASON_CANCELLED_BY_APP` |
| `ModelDownloadWorker : CoroutineWorker` | 31–33 | `setForeground(ForegroundInfo(id, notif, FOREGROUND_SERVICE_TYPE_DATA_SYNC))`, tolerando `ForegroundServiceStartNotAllowedException`. Después `runQueue()`. Con `Failed` por red, `Result.retry()`; con el resto, `Result.success()` |
| `DefaultModelDownloadController` | todas | Elige la ruta por `Build.VERSION.SDK_INT`. Job id fijo `JOB_ID_MODEL_DOWNLOAD = 3001` y trabajo único `model-download` |
| `DownloadNotifications` | todas | Canal `model_downloads` (importancia baja). Notificación en curso con el modelo, `N %`, «X MB de Y» y la acción «Detener». Se actualiza como mucho una vez por segundo |
| `DownloadActionReceiver` | todas | `exported=false`. Recibe la acción «Detener» y ejecuta `PauseModelDownloadUseCase` |

## Datos: ficheros y registro (`data/models/`)

Ampliación de `ModelFileRepository` en data-model §4. `VerificationRecordStore` lee y escribe `models/verification.json` (data-model §7).

## Runtime (`rag/runtime/`)

```kotlin
class ModelCompatibilityChecker(factory: EngineFactory, coordinator: EngineCoordinator, dispatchers: AppDispatchers) {
    suspend fun check(file: ModelFile, path: File): CompatibilityResult
}
```

Reglas en research R8. No importa LiteRT-LM (`LayeringTest`).

## Garantías verificables por tests

| Garantía | Test |
|---|---|
| Nunca `Ready` sin `Verified` y backend preparado | `ModelSetupStatusResolverTest`, exhaustivo sobre las combinaciones (SC-001) |
| Reanudación con `Range` correcto y truncado ante 200 o un `Content-Range` incorrecto | `ModelDownloaderTest` (MockWebServer) |
| Redirección a un host no permitido o a `http` | `ModelDownloaderTest` → `ForbiddenHost` / `InsecureScheme` |
| Un fichero con 1 byte alterado nunca se adopta | `DefaultModelFileRepositoryTest.adopt_mismatch_deletes` |
| Espacio insuficiente antes de empezar y entre modelos | `ModelDownloadRunnerTest` |
| 5 reintentos con espera creciente y reinicio del contador al avanzar | `ModelDownloadRunnerTest`, en tiempo virtual |
| Chequeo barato con registro; con otro `lastModified`, `Unverified` | `DefaultModelFileRepositoryTest` |
| GPU → CPU → `Incompatible` | `ModelCompatibilityCheckerTest`, con una fábrica falsa |
| Solo `data/download/` usa red | `NetworkConfinementTest` |
