# Contrato — Invariantes de plataforma (actualiza el de F01)

F03 introduce el único acceso a red de la app (constitución I). Este contrato sustituye a la invariante P4 de F01 («ningún permiso de red, normal ni peligroso») por una **lista cerrada**.

## P4' — Permisos solicitados (lista cerrada)

`ManifestInvariantsTest` comprueba que `requestedPermissions` es **exactamente** este conjunto: ni uno más ni uno menos.

| Permiso | Origen | Motivo |
|---|---|---|
| `android.permission.INTERNET` | app | Descarga de modelos (D-011) |
| `android.permission.ACCESS_NETWORK_STATE` | app y WorkManager | Restricción de red y «Esperando conexión» |
| `android.permission.POST_NOTIFICATIONS` | app | Progreso de la descarga (G30) |
| `android.permission.RUN_USER_INITIATED_JOBS` | app | UIDT en API ≥ 34 |
| `android.permission.FOREGROUND_SERVICE` | WorkManager | Worker largo en API 31–33 |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | app | Tipo `dataSync` del `SystemForegroundService` |
| `android.permission.RECEIVE_BOOT_COMPLETED` | WorkManager | Jobs persistidos tras reiniciar |
| `android.permission.WAKE_LOCK` | WorkManager | Ejecución del worker |
| `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | De firma (F01) |

Fuera de la lista, cualquier permiso de almacenamiento (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`) hace fallar el test. La importación usa SAF.

## Componentes declarados

| Componente | Atributos |
|---|---|
| `data.download.ModelDownloadJobService` | `android:permission="android.permission.BIND_JOB_SERVICE"`, `exported="false"` |
| `data.download.DownloadActionReceiver` | `exported="false"` |
| `androidx.work.impl.foreground.SystemForegroundService` | `tools:node="merge"`, `android:foregroundServiceType="dataSync"` |

## Invariantes que se mantienen de F01 y F02

- `allowBackup="false"` y `dataExtractionRules` lo excluyen todo (P2).
- No hay actividades para enlaces externos (P5).
- `uses-native-library` de `libOpenCL.so` y `libvndksupport.so` con `required="false"` (F02).

## Confinamiento de red

- **`NetworkConfinementTest` (JVM):** solo `com.lisdatasolutions.microrag.data.download` importa APIs de red (research R9).
- **`NoTrafficInstrumentedTest`:** sin descargas pendientes, el UID de la app no envía ni recibe bytes al recorrer las pantallas (SC-008).
- **Hosts de descarga:** `huggingface.co` y `*.hf.co`, solo `https`. La lista está fijada en código y no se configura en release.

## Almacenamiento

| Contenido | Ruta |
|---|---|
| Modelos verificados | `noBackupFilesDir/models/` |
| Descargas parciales | `noBackupFilesDir/models/downloads/` |
| Registro de verificación | `noBackupFilesDir/models/verification.json` |
| Preferencias del onboarding | `noBackupFilesDir/datastore/onboarding.preferences_pb` |
| Base interna de WorkManager | `databases/` (gestionada por la librería, excluida de copias por `dataExtractionRules`) |
| Caché de kernels de LiteRT-LM | `cacheDir/litertlm/` (F02) |
