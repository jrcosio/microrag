# Verificación de release — F03

**Fecha:** 2026-10-26

**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk`, sin R8 (llega en F15).

**Herramientas:** `apkanalyzer` (cmdline-tools/latest) y `unzip`.

## Invariantes (constitución I, contracts/platform-invariants.md)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| **Permisos: lista cerrada P4'** | ✅ Exactamente estos 9: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RUN_USER_INITIATED_JOBS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` y el de firma `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Ninguno de almacenamiento | `apkanalyzer manifest permissions`; `ManifestInvariantsTest` |
| Servicio de descarga | ✅ `ModelDownloadJobService`: `exported="false"`, `permission="BIND_JOB_SERVICE"` | `apkanalyzer manifest print` |
| Receptor «Detener» | ✅ `DownloadActionReceiver`: `exported="false"` | ídem |
| Servicio en primer plano de WorkManager | ✅ `SystemForegroundService`: `foregroundServiceType="dataSync"` (`0x1`) | ídem |
| Inicialización de WorkManager | ✅ Sin `WorkManagerInitializer` en `InitializationProvider`: se inicializa bajo demanda | ídem |
| Componentes exportados de librerías | ✅ Ninguno abierto. `SystemJobService` exige `BIND_JOB_SERVICE` (solo el sistema puede enlazarlo); `DiagnosticsReceiver` y `ProfileInstallReceiver` exigen `DUMP` (permiso de firma o privilegiado) | ídem |
| Backup | ✅ `allowBackup="false"` y `dataExtractionRules` lo excluyen todo (también las bases de WorkManager y Room) | ídem |
| Idiomas | ✅ `localeConfig` con `es` y `en` (D-029). App Bundle sin división por idioma, para que el inglés esté siempre disponible | ídem y `bundle.language.enableSplit = false` |
| Diagnóstico ausente en release | ✅ El paquete `…microrag.debug` solo contiene los stubs `DebugRouting` y `DebugModulesKt` (F01/F02) | `apkanalyzer dex packages` |
| Confinamiento de red | ✅ Solo `data.download` importa APIs de red | `NetworkConfinementTest` (JVM) |
| ABIs | ✅ Solo `lib/arm64-v8a/`. `liblitertlm_jni.so` y `libandroidx.graphics.path.so` (F02), más **`libdatastore_shared_counter.so`** (7,6 KB), nueva con DataStore 1.2: contador para el modo multiproceso, que no se usa | `unzip -l` |

## Tamaño

| Build | APK release sin minificar |
|---|---:|
| F01 | 28,9 MB |
| F02 | 54,3 MB |
| F03 | 57,4 MB (+3,1 MB) |

La diferencia procede de WorkManager (con Room y SQLite de AndroidX), DataStore (con Okio) y el código de F03. R8 se evalúa en F15.
