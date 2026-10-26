# Data model — F03 Modelos y primer arranque

Tipos de dominio en `domain/model` (sin Android), persistencia en `data/` y estado de UI en `feature/onboarding`. Los nombres son de código (inglés); las descripciones, en español.

## 1. Modelos requeridos

`ModelCatalog.required: List<ModelFile>` contiene, en orden de descarga (FR-014):

| `ModelId` | Fichero | Bytes | Papel en la UI |
|---|---|---:|---|
| `EG2_270M` | `embeddinggemma-2-text-270m.litertlm` | 164.626.432 | «EmbeddingGemma 2 · Búsqueda semántica» |
| `GEMMA4_E2B` | `gemma-4-E2B-it.litertlm` | 2.588.147.712 | «Gemma 4 · Modelo de respuestas» |

Los otros tres ficheros del catálogo (Gemma `-gpu`, EmbeddingGemma 2 de 440M y 740M) solo los usa el diagnóstico de debug (FR-030).

Cada `ModelFile` gana un campo `downloadUrl`, derivado de `repository`, `revision` y `fileName`: `https://huggingface.co/<repository>/resolve/<revision>/<fileName>`.

## 2. Estado de un modelo en la Preparación

`ModelSetupStatus` (sealed interface, `domain/model`). Para cada modelo requerido se calcula con una función pura, `ModelSetupStatusResolver.resolve(...)`, a partir de:
- el `ModelFileState` del repositorio de ficheros (F02, ampliado en §4);
- el `DownloadProgress` del runner (§3);
- el `PreparationState` de la prueba de compatibilidad (§5);
- las preferencias de descarga (§6);
- la conectividad (`hasInternet: Boolean`).

| Estado | Datos | Condición, en orden de prioridad | Chip (FR-007) | `StatusKind` |
|---|---|---|---|---|
| `Ready` | `backend`, `sizeBytes` | `Verified` y preparado con backend | «Listo» | `Success` |
| `Preparing` | — | `Verified` sin backend, o prueba en curso | «Preparando» | `Info` |
| `Incompatible` | `reason` | La prueba falló en todos los backends, o la importación no corresponde | «Incompatible» | `Error` |
| `Verifying` | `bytesDone`, `totalBytes` | `ModelFileState.Verifying` | «Verificando» | `Info` |
| `Importing` | `bytesDone`, `totalBytes` | Importación SAF en curso | «Importando N %» | `Info` |
| `NoSpace` | `neededBytes` | El runner terminó con `NoSpace` | «Sin espacio» | `Warning` |
| `Failed` | `reason` | `Mismatch`, `Failed` o 5 fallos de descarga seguidos | «Error» | `Error` |
| `Paused` | `bytesDone`, `totalBytes` | Pedido y `download_paused` | «En pausa» | `Warning` |
| `WaitingForNetwork` | `bytesDone`, `totalBytes` | Pedido, sin pausa y sin red | «Esperando conexión» | `Warning` |
| `Downloading` | `bytesDone`, `totalBytes` | El runner descarga este modelo | «Descargando N %» | `Info` |
| `Queued` | — | Pedido, sin pausa, a la espera de otro modelo | «En cola» | `Neutral` |
| `Unconfigured` | `partialBytes` | Ningún caso anterior | «Por configurar» | `Warning` |

**Invariantes**, cubiertas por tests (SC-001):
- `Ready` exige `ModelFileState.Verified` **y** `preparedBackend != null` en el registro. No hay ninguna otra forma de llegar a `Ready`.
- El porcentaje es `floor(bytesDone * 100 / totalBytes)` y no supera 100. `bytesDone` es el tamaño real del fichero parcial o de la copia.

**Acción al tocar la fila** (`SetupRowAction`, derivada del estado): `OpenConfirmation` (`Unconfigured`, `Incompatible`), `Pause` (`Downloading`, `Queued`, `WaitingForNetwork`), `Resume` (`Paused`), `Retry` (`Failed`), `OpenStorageSettings` (`NoSpace`) o ninguna.

## 3. Descarga

```text
DownloadProgress(           // StateFlow de proceso publicado por ModelDownloadRunner
    active: ModelId?,       // modelo que se está descargando; null si no hay ninguno
    bytesDone: Long,
    totalBytes: Long,
    outcome: DownloadOutcome?, // resultado del último intento terminado
)

sealed DownloadOutcome:
    Completed(id)               // descargado, verificado y movido a models/
    NoSpace(id, neededBytes)
    Failed(id, reason: DownloadError)
    Cancelled(id)               // pausa o cancelación del job; el parcial se conserva

sealed DownloadError:
    Http(code), Network(message), ForbiddenHost(host), InsecureScheme,
    RangeMismatch, HashMismatch, Io(message)
```

**Ficheros:**
- parcial: `noBackupFilesDir/models/downloads/<fileName>.download`;
- final: `noBackupFilesDir/models/<fileName>`.

**Transiciones de un modelo en el runner:**

```text
Unconfigured ──confirmar──▶ Queued ──turno──▶ Downloading ──completo──▶ Verifying ──hash ok──▶ Preparing ──prueba ok──▶ Ready
                              ▲                  │  │                      │ hash ko                 │ prueba ko
                              │                  │  └─sin red─▶ WaitingForNetwork ─red─┘             ▼
                              └──reanudar── Paused ◀──detener─┘          Failed ◀─────┘          Incompatible
                                                 Downloading ──sin espacio──▶ NoSpace ──espacio liberado + reintentar──▶ Queued
```

## 4. Repositorio de ficheros de modelo (ampliación de F02)

`ModelFileRepository` añade:

| Miembro | Comportamiento |
|---|---|
| `suspend fun adopt(id, file: File): ModelFileState` | Hash en el sitio con progreso (`Verifying`). Si coincide, `renameTo(models/<fileName>)` y actualiza el registro; si no, borra `file` y devuelve `Mismatch` |
| `suspend fun installFromStream(id, input: InputStream, onBytes): ModelFileState` | Copia a `.part` mientras calcula el hash (lógica de `installVerified`) y renombra si coincide. La usa la importación SAF |
| `suspend fun markPrepared(id, backend: ComputeBackend)` | Guarda el backend comprobado en el registro |
| `fun preparedBackend(id): ComputeBackend?` | Backend del registro, solo si el fichero sigue `Verified` |
| `refresh()` | Usa el registro para el chequeo barato (§7). Sigue borrando los `.part` sueltos de `models/`, pero no toca `models/downloads/` |

`ModelFileState` no cambia. «Preparado» vive en el registro y no en el estado del fichero, así que la batería de F02 sigue igual.

## 5. Prueba de compatibilidad

```text
sealed PreparationState: Idle | Running(id) | Done(id, backend) | Incompatible(id, reason)

ModelCompatibilityChecker.check(file: ModelFile, path: File): CompatibilityResult
sealed CompatibilityResult: Compatible(backend: ComputeBackend) | Incompatible(reason: String)
```

Reglas en research R8. Con `Compatible`, el caso de uso llama a `markPrepared`.

## 6. Preferencias del onboarding (DataStore)

`OnboardingStore` (interfaz en `domain`, implementación en `data/onboarding`):

| Propiedad | Tipo | Por defecto |
|---|---|---|
| `stage: Flow<OnboardingStage>` | `NOT_STARTED`, `STARTED` o `COMPLETED` | `NOT_STARTED` |
| `downloadRequested: Flow<Set<ModelId>>` | Conjunto | vacío |
| `downloadPaused: Flow<Boolean>` | — | `false` |
| `downloadConfirmed: Flow<Boolean>` | — | `false` |

**Transiciones de `stage`:**
- `NOT_STARTED → STARTED` al pulsar «Comenzar»;
- `STARTED → COMPLETED` al pulsar «Continuar» con todos los requisitos cumplidos.

`COMPLETED` no vuelve atrás en F03. Reiniciar el onboarding queda para F12.

## 7. Registro de verificación

```json
{
  "version": 1,
  "entries": [
    {
      "fileName": "gemma-4-E2B-it.litertlm",
      "sha256": "181938…a63c",
      "sizeBytes": 2588147712,
      "lastModified": 1792990000000,
      "verifiedAt": "2026-10-26T10:00:00Z",
      "preparedBackend": "GPU",
      "preparedAt": "2026-10-26T10:01:00Z"
    }
  ]
}
```

- **Fichero:** `noBackupFilesDir/models/verification.json`.
- **Escritura:** atómica (`verification.json.tmp` + `rename`).
- **Entrada inválida:** si el SHA-256 de una entrada no es el del catálogo, la entrada se ignora (el catálogo manda).

## 8. Estado de la pantalla de Preparación

```text
SetupUiState(
    step: Int,                         // 1..3, SetupStepResolver (FR-006)
    rows: List<ModelRowUi>,            // en el orden del PNG: Gemma 4 y después EmbeddingGemma 2
    storage: StorageStatus,            // Offline | NoSpace(neededBytes) | Preparing (domain/model/SetupStep.kt)
    continueEnabled: Boolean,
    continueBlocker: ContinueBlocker?, // motivo visible cuando continueEnabled = false (FR-008)
    notificationsDenied: Boolean,      // aviso FR-010
    confirmation: ConfirmationUi?,     // diálogo abierto, o null
)

ModelRowUi(id, name, purpose, icon, status: ModelSetupStatus, action: SetupRowAction)

ConfirmationUi(
    models: List<Pair<ModelId, Long>>, // pendientes y tamaño
    totalBytes: Long,
    freeBytes: Long,
    freeAfterBytes: Long,
    network: NetworkKind,              // WIFI | MOBILE | NONE
    lowRam: Boolean,
)

sealed ContinueBlocker:
    ModelsNotConfigured      // «Configura los dos modelos para continuar»
    InProgress               // «Esperando a que termine la descarga»
    NeedsAttention           // «Revisa los modelos marcados para continuar» (Failed, Incompatible o Paused)
    NoSpace                  // «Libera espacio para continuar»
```

**Paso (FR-006):**
- 3 si los dos modelos están `Ready`;
- 2 si alguno está en `Queued`, `Downloading`, `Importing`, `Paused`, `WaitingForNetwork`, `Verifying`, `Preparing`, `Failed`, `NoSpace` o `Incompatible`;
- 1 en cualquier otro caso: algún modelo está `Unconfigured` y no hay nada en marcha.

**Eventos del ViewModel:** `onRowClick(id)`, `onConfirmDownload()`, `onPickFile(id)`, `onFilePicked(id, uri)`, `onDismissConfirmation()`, `onContinue()`, `onNotificationPermissionResult(granted)`, `onResumeVisible()` (reconciliación y preparación pendiente).

## 9. Bienvenida y arranque

- **`WelcomeScreen`:** sin estado. Su único evento es `onStart` (`stage = STARTED` y navegar a `Setup`).
- **`StartDestination`:** `Welcome`, `SetupOverWelcome` o `Home` (research R10).
