# Verificación de release — F20

**Fecha:** 2026-11-05

**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk` (48,8 MB), sin R8; la minificación llega en F15.

**Herramientas:** `apkanalyzer` (cmdline-tools/latest, con el JBR de Android Studio como `JAVA_HOME`) y `git diff`.

## Invariantes (constitución I y II)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Diagnósticos ausentes en release | ✅ Ninguna clase con `feasibility`, `RetrievalDiagnostic`, `LiteRtVisionEngine`, `MainVisionGenerator` ni `DebugPageRasterizer`. El paquete `…microrag.debug` solo contiene los *stubs* `DebugRouting`, `DebugStrictMode` y `DebugModulesKt` de siempre | `apkanalyzer dex packages` (286.176 líneas) |
| El chat con páginas está en `main` | ✅ `LiteRtTextGenerator` (con la visión), `DefaultPageImageRepository`, `PageImageLoader`, `PageViewerScreenKt`, `ZoomState` y `PageDetailViewModel` en el APK de release | `apkanalyzer dex packages` |
| Pruebas fuera del APK | ✅ Ni `VisualChatBenchmarkTest` ni `GemmaVisionSmokeTest` | `apkanalyzer dex packages` |
| Permisos | ✅ Los 9 de la lista cerrada P4' de F03, sin ninguno nuevo: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RUN_USER_INITIATED_JOBS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` y el de firma `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `apkanalyzer manifest permissions` |
| Manifiesto y build sin cambios | ✅ F20 no cambia `AndroidManifest.xml`, `app/src/release`, `app/build.gradle.kts`, `gradle/` ni los `settings`/`build.gradle.kts` de la raíz frente a `main` (`4bb1d3f`). Sin dependencias nuevas | `git diff main --stat -- app/src/main/AndroidManifest.xml app/src/release app/build.gradle.kts gradle/ build.gradle.kts settings.gradle.kts` vacío |
| min/target SDK | ✅ 31 / 37 | `apkanalyzer manifest min-sdk/target-sdk` |
| Red | ✅ F20 no descarga nada. `NetworkConfinementTest` en verde, dentro de `testDebugUnitTest` | JVM |
| Registros sin contenido | ✅ El chat, la recuperación y las imágenes registran solo cifras (pruebas «SECRETO»), y el logger de release no escribe | JVM |
