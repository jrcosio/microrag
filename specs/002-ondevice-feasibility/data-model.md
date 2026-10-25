# Data model — F02 Viabilidad on-device

F02 no usa base de datos. Sus entidades viven en memoria y se serializan en el informe JSON (`filesDir/reports`).

Los nombres en código son en inglés y la documentación en español (constitución VIII).

## 1. `ModelFile`: catálogo de ficheros esperados

Va en el paquete `domain.model`. Es un catálogo inmutable que F03 reutilizará para la descarga.

| Campo | Tipo | Ejemplo | Regla |
|---|---|---|---|
| `id` | `ModelId` (enum) | `GEMMA4_E2B_GPU` | Único |
| `family` | `ModelFamily` | `LLM` / `EMBEDDING` | — |
| `displayName` | `String` | «Gemma 4 E2B (GPU)» | — |
| `fileName` | `String` | `gemma-4-E2B-it-gpu.litertlm` | Único |
| `sizeBytes` | `Long` | `2008432640` | > 0 |
| `sha256` | `String` | `a53a59…5cf5` | 64 caracteres hex en minúscula |
| `repository` | `String` | `litert-community/gemma-4-E2B-it-litert-lm` | — |
| `revision` | `String` | `1caa527c…663d` | Commit de 40 hex |

Valores: los cinco de research R2.

## 2. `ModelFileState`: estado local de cada fichero

Va en el paquete `domain.model`, en el source set `main`, y F03 lo reutiliza.

```text
Missing                                 (no está en noBackupFilesDir/models)
Unverified(path)                        (presente, sin verificar en esta sesión; no cargable)
Verifying(progress: ProgressValue)      (cálculo de SHA-256; progreso real en bytes)
Verified(path)                          (huella correcta; único estado cargable)
Mismatch(expected, actual)              (huella incorrecta; no se carga)
Failed(reason)                          (error de E/S, sin espacio…)
```

Transiciones:
- `refresh()`: fichero presente → `Unverified`; ausente → `Missing`. No calcula huellas.
- `Missing → Verifying → Verified | Mismatch | Failed`: al instalar desde un fichero fuente. En F02 la fuente es la copia de desarrollo; en F03 será la descarga.
- `Unverified | Verified → Verifying → Verified | Mismatch`: al verificar. El diagnóstico verifica automáticamente los `Unverified` al abrirse (US1, escenario 1) y la suite revalida todo antes de medir (FR-005).
- `Mismatch`: el fichero instalado se borra en el acto. El estado se mantiene para mostrarlo hasta el siguiente `refresh()`, que lo deja en `Missing`. Nunca se carga un fichero que no esté en `Verified`.
- `Failed`: no queda ningún `.part`; el siguiente `refresh()` refleja lo que haya en disco.

**Vista de diagnóstico (solo debug):** `DevModelRow(file: ModelFile, state: ModelFileState, devCopyBytes: Long?)`. `devCopyBytes ≠ null` indica que hay una copia en `dev-models` pendiente de importar. Se muestra como «Pendiente de importar» cuando el estado es `Missing`.

## 3. Escenarios y resultados

### `BenchmarkScenario`

| Campo | Tipo | Notas |
|---|---|---|
| `kind` | `EMBEDDING` / `GENERATION` / `COMPATIBILITY` | — |
| `model` | `ModelId` | — |
| `backend` | `CPU` / `GPU` | `COMPATIBILITY` usa ambos |
| `protocol` | `MeasurementProtocol` | Repeticiones de calentamiento y medidas, tokens de salida… |

**Protocolo por defecto** (spec, Assumptions):
- embeddings: 3 de calentamiento y 30 medidas, sobre la consulta y sobre el fragmento de ≈ 350 tokens;
- generación: 1 de calentamiento y 3 medidas, `maxOutputTokens = 128`;
- compatibilidad: 20 textos (10 consultas y 10 fragmentos).

### `ScenarioResult`

Cada resultado es uno de cuatro tipos:

```text
Completed(metrics) | Cancelled(atPhase) | Failed(reason) | NotAvailable(reason)
```

Todos llevan además los campos comunes: `scenario`, `startedAt`, `durationMs`, `thermalStart`, `thermalEnd`, `charging`, `peakRssMb`, `peakRssResetSupported`.

### `EmbeddingMetrics`

| Campo | Unidad |
|---|---|
| `initColdMs` (caché borrada) / `initWarmMs` | ms |
| `queryP50Ms` / `queryP95Ms` | ms (firma de 128) |
| `documentP50Ms` / `documentP95Ms` | ms (fragmento de ≈ 350 tokens) |
| `outputDims` (tras truncar) | Tiene que ser 256 |
| `rawDims` (salida del modelo) | Esperado: 768 |
| `l2Norm` | Tiene que ser 1 ± 1e-5 |
| `declaredMaxContextTokens` | Tokens (`ModelInfo`) |
| `supportedBackends` | Lista |

### `GenerationMetrics`

| Campo | Unidad |
|---|---|
| `initColdMs` / `initWarmMs` | ms (reloj de pared) |
| `benchmarkInitS` | s (`BenchmarkInfo`) |
| `promptTokens` | Tokens (`lastPrefillTokenCount`) |
| `ttftMs` (`BenchmarkInfo`) / `ttftWallMs` (primer `Message`) | ms |
| `prefillTokensPerSecond` / `decodeTokensPerSecond` | tok/s |
| `decodeTokens` | Tokens |
| `sampleOutput` | Texto. Solo en el informe y en pantalla, nunca en logs |
| `fallbackFromGpu` | `Boolean` (plan B en CPU) más el motivo |

### `CompatibilityMetrics`

| Campo | Notas |
|---|---|
| `textCount` | 20 |
| `cosineMin` / `cosineMean` | CPU frente a GPU sobre el mismo texto |
| `compatible` | `cosineMin ≥ 0.99` |

## 4. `DeviceSnapshot`

| Campo | Fuente |
|---|---|
| `manufacturer`, `model`, `socManufacturer`, `socModel` | `Build` |
| `androidRelease`, `sdkInt` | `Build.VERSION` |
| `totalRamMb` | `ActivityManager.MemoryInfo` |
| `thermalStatus`, `thermalHeadroom` | `PowerManager` |
| `charging`, `batteryLevel`, `batteryTempC` | `ACTION_BATTERY_CHANGED` |
| `appVersion`, `liteRtLmVersion` | `BuildConfig.VERSION_NAME` y constante `LiteRtLmInfo.VERSION` (debe coincidir con `libs.versions.toml`; lo comprueba un test) |

## 5. `FeasibilityReport`

| Campo | Notas |
|---|---|
| `schemaVersion` | `1` |
| `createdAt` | ISO-8601 |
| `device` | `DeviceSnapshot` al inicio de la sesión |
| `models` | Lista de `ModelFile` con su estado final |
| `results` | Lista de `ScenarioResult` |
| `exitCriteria` | `ttftMeasured: Boolean`, `bestQueryP95CpuMs`, `queryUnder150msCpu: Boolean` |

Serialización JSON y Markdown: ver [contracts/report-format.md](contracts/report-format.md).

## 6. Estado del motor (`EngineCoordinator`)

```text
Idle → Loading(kind, backend) → Ready(kind, backend) → Releasing → Idle
```

Invariante: como mucho hay un motor en `Loading/Ready` con `backend = GPU`. Antes de pasar otro motor de GPU a `Loading`, el anterior tiene que llegar a `Idle`.
