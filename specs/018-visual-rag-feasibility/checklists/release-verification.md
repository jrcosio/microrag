# Verificación de release — F18

**Fecha:** 2026-11-05

**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk` (48,2 MB), sin R8; la minificación llega en F15.

**Herramientas:** `apkanalyzer` (cmdline-tools/latest, con el JBR de Android Studio como `JAVA_HOME`) y `git diff`.

## Invariantes (constitución I y II, FR-001, SC-009)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Diagnóstico visual ausente en release | ✅ 0 coincidencias de `feasibility.visual`, `LiteRtVision` y `DebugPageRasterizer`, y ninguna clase con `feasibility`. El paquete `…microrag.debug` solo contiene los *stubs* `DebugRouting` y `DebugStrictMode` de siempre | `apkanalyzer dex packages` (281.501 líneas) |
| Permisos | ✅ Los 9 de la lista cerrada P4' de F03: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RUN_USER_INITIATED_JOBS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` y el de firma `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `apkanalyzer manifest permissions` |
| Manifiesto igual que en F17 | ✅ F18 no cambia nada de `app/src/main`, `app/src/release`, `app/build.gradle.kts`, `gradle/` ni de los `settings`/`build.gradle.kts` de la raíz frente a `main` (`6b89467`): lo nuevo de la app está en `src/debug` (15 ficheros), `src/testDebug` (5), `src/androidTest` (3 pruebas y 3 imágenes) y `src/test` (`LayeringTest`) | `git diff main --stat -- app/src/main app/src/release app/build.gradle.kts gradle/ build.gradle.kts settings.gradle.kts` vacío |
| Backup y librerías nativas | ✅ `android:allowBackup="false"` y las dos `uses-native-library` opcionales | `apkanalyzer manifest print` |
| min/target SDK | ✅ 31 / 37 | `apkanalyzer manifest min-sdk/target-sdk` |
| Red confinada | ✅ `NetworkConfinementTest` en verde, dentro de `testDebugUnitTest` (1.162 de 1.162) | JVM |
| Invariantes del manifiesto | ✅ `ManifestInvariantsTest` en verde en `Pixel_10` (API 37) y `MicroRAG_API33`, dentro de la batería de T052 | Instrumentadas |
