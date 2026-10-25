# Verificación de release — F01

**Fecha:** 2026-10-25
**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk` (sin R8; la minificación llega en F15)
**Herramientas:** `apkanalyzer` (cmdline-tools/latest) y emulador `Pixel_10` (AVD, API 37, arm64-v8a)

## Invariantes de plataforma (`contracts/platform-invariants.md`)

| ID | Comprobación | Resultado | Evidencia |
|---|---|---|---|
| P1 | min/target SDK | ✅ `minSdk 31`, `targetSdk 36` (compileSdk 37.1, D-020) | `apkanalyzer manifest min-sdk` / `target-sdk` |
| P2 | Backup desactivado | ✅ `android:allowBackup="false"` | Manifiesto de release + `ManifestInvariantsTest.p2` |
| P3 | Sin backup ni transferencia D2D | ✅ `dataExtractionRules` excluye todos los dominios; `bmgr backupnow` → «Backup is not allowed» | `res/xml/data_extraction_rules.xml` + emulador |
| P4 | Permisos | ✅ Solo `com.lisdatasolutions.microrag.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (nivel firma, AndroidX) | `apkanalyzer manifest permissions` + `ManifestInvariantsTest.p4` |
| P5 | Sin enlaces profundos | ✅ Ningún `VIEW`/`BROWSABLE` en el manifiesto de release; ninguna actividad resuelve URIs `https`, `http`, `microrag`, `content` ni `file` | `apkanalyzer manifest print` + `ManifestInvariantsTest.p5` |
| P6 | Sin herramientas de debug | ✅ El paquete `…microrag.debug` del dex de release solo contiene el *stub* `DebugRouting` (3 métodos); no hay `catalog`, `routes`, `DebugLauncherActivity` ni `DebugEntries`, y el manifiesto de release no declara `DebugLauncherActivity` | `apkanalyzer dex packages` |
| P7 | Sin logs de depuración | ✅ `AppLogger` sujeto a `BuildConfig.DEBUG` | `AppLoggerTest` |
| P8 | ABIs | ✅ El APK de release solo contiene `lib/arm64-v8a/` (`libandroidx.graphics.path.so`); sin `x86_64` ni `armeabi-v7a` | `unzip -l` |
| P9 | Sin color dinámico | ✅ | `DesignSystemConformanceTest.dynamicColor_isNeverUsed` |
| P10 | Nombre de la app | ✅ «MicroRAG» | `ManifestInvariantsTest.p10` |

Tamaño del APK de release sin minificar: 28,9 MB. Incluye la fuente variable Google Sans (4,8 MB). La reducción con R8 y el posible subconjunto de la fuente se evalúan en F15.

## Arranque en frío (T061, SC-009)

| Dato | Valor |
|---|---|
| Build | release (sin R8), firmado con la clave de debug solo para instalarlo en el emulador |
| Dispositivo | Emulador `Pixel_10` (AVD), Android API 37, arm64-v8a, sobre un host Apple Silicon |
| Método | `adb shell am force-stop` y luego `am start -W` (`LaunchState: COLD`), 5 mediciones tras una ejecución de calentamiento |
| `TotalTime` (ms) | 181 · 199 · 179 · 211 · 181 |
| **Mediana** | **181 ms** (objetivo < 1000 ms) ✅ |

> Un emulador sobre un host de escritorio no representa un teléfono real. La medición se repite en el teléfono de referencia en F02 (SC-009).
