# Verificación de release — F19

**Fecha:** 2026-11-05

**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk` (48,6 MB), sin R8; la minificación llega en F15.

**Herramientas:** `apkanalyzer` (cmdline-tools/latest, con el JBR de Android Studio como `JAVA_HOME`) y `git diff`.

## Invariantes (constitución I y II)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Diagnóstico de F18 ausente en release | ✅ Ninguna clase con `feasibility`, `LiteRtVision` ni `DebugPageRasterizer`. El paquete `…microrag.debug` solo contiene los *stubs* `DebugRouting`, `DebugStrictMode` y `DebugModulesKt` de siempre | `apkanalyzer dex packages` (284.742 líneas) |
| El modo multimodal está en `main` | ✅ `DefaultMultimodalController`, `IdentityValidator`, `PageEmbeddingSession`, `AndroidPageRasterizer` y `LiteRtImageEmbedder` en el APK de release, como pide R7 | `apkanalyzer dex packages` |
| Permisos | ✅ Los 9 de la lista cerrada P4' de F03, sin ninguno nuevo: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RUN_USER_INITIATED_JOBS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` y el de firma `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `apkanalyzer manifest permissions` |
| Manifiesto y build sin cambios | ✅ F19 no cambia `AndroidManifest.xml`, `app/src/release`, `app/build.gradle.kts`, `gradle/` ni los `settings`/`build.gradle.kts` de la raíz frente a `main` (`af2b836`). La descarga de la 440M es la de F03, con su mismo origen fijado | `git diff main --stat -- app/src/main/AndroidManifest.xml app/src/release app/build.gradle.kts gradle/ build.gradle.kts settings.gradle.kts` vacío |
| Backup y librerías nativas | ✅ `android:allowBackup="false"` y las dos `uses-native-library` opcionales. Las páginas van en `noBackupFilesDir/pages/` y el almacén del modo en `noBackupFilesDir/datastore/` | `apkanalyzer manifest print` |
| min/target SDK | ✅ 31 / 37 | `apkanalyzer manifest min-sdk/target-sdk` |
| Red confinada | ✅ `NetworkConfinementTest` en verde, dentro de `testDebugUnitTest` | JVM |
| Invariantes del manifiesto | `ManifestInvariantsTest`, dentro de la batería instrumentada de T053 | Instrumentadas |
