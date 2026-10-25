# Verificación de release — F02

**Fecha:** 2026-10-26

**APK:** `app/build/outputs/apk/release/app-release-unsigned.apk`, sin R8; la minificación llega en F15.

**Herramientas:** `apkanalyzer` (cmdline-tools/latest) y `unzip`.

## Invariantes (constitución I y II, FR-001, FR-004, SC-007)

| Comprobación | Resultado | Evidencia |
|---|---|---|
| Diagnóstico ausente en release | ✅ El paquete `…microrag.debug` del dex de release solo contiene los *stubs* `DebugRouting` y `DebugModulesKt`. Ninguna clase contiene `feasibility` ni `DevModelImporter` (0 coincidencias) | `apkanalyzer dex packages` |
| Sin permisos de red | ✅ Único permiso: `com.lisdatasolutions.microrag.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (de firma, AndroidX). El AAR de LiteRT-LM no añade ninguno | `apkanalyzer manifest permissions` |
| Librerías nativas opcionales | ✅ `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` y lo mismo para `libvndksupport.so` | `apkanalyzer manifest print` |
| ABIs | ✅ Solo `lib/arm64-v8a/`: `liblitertlm_jni.so` (22,2 MB) y `libandroidx.graphics.path.so`. Sin `x86_64` | `unzip -l` |
| Backup | ✅ `android:allowBackup="false"` | `apkanalyzer manifest print` |
| min/target SDK | ✅ 31 / 37 | `apkanalyzer manifest min-sdk/target-sdk` |
| Capas | ✅ Solo `rag/runtime/litert` importa LiteRT-LM | `LayeringTest` |

## Tamaño

| Build | APK release sin minificar |
|---|---:|
| F01 | 28,9 MB |
| F02 | 54,3 MB (+25,4 MB) |

La diferencia se explica sobre todo por `liblitertlm_jni.so` (22,2 MB sin comprimir), más `gson`, `kotlin-reflect` y las clases del runtime. Era esperado: el producto necesita el runtime desde F03/F04. R8 se evalúa en F15.

## Puertas de calidad (T063)

| Puerta | Resultado |
|---|---|
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ «No issues found» |
| `testDebugUnitTest` | ✅ 90/90 |
| Tests unitarios de release | No existen en AGP 9 (solo se generan para el `testBuildType`). La compilación de release queda cubierta por `assembleRelease` y `lintRelease` |
| `connectedDebugAndroidTest` (emulador `Pixel_10`) | ✅ 99 tests: 95 pasan y 4 se omiten (`@RequiresModels`, sin modelos), 0 fallos |
| Instrumentados en el teléfono (`am instrument -e notAnnotation …RequiresModels`) | ✅ 95/95 |
| `@RequiresModels` en el teléfono, uno a uno | ✅ `LiteRtSmokeTest`, `FeasibilityBenchmarkTest` (batería definitiva), `GemmaGpuFileExperimentTest`, `EmbeddingVariantsExperimentTest` |

## Arranque en frío (T065; F01 SC-009 pendiente en el teléfono)

**Build:** release sin R8, firmada con la clave de debug solo para instalarla sobre la de debug sin perder los modelos.

**Método:** `am force-stop` y luego `am start -W` (`LaunchState: COLD`); 1 ejecución de calentamiento y 5 medidas.

| Dispositivo | `TotalTime` (ms) | Mediana | Frente a F01 |
|---|---|---:|---|
| Nothing Phone (1), Android 15 | 652 · 664 · 680 · 704 · 716 | **680 ms** ✅ (< 1000) | Primera medida en el teléfono |
| Emulador `Pixel_10` (API 37) | 203 · 219 · 200 · 213 · 205 | **205 ms** ✅ | F01: 181 ms (+24 ms: grafo Koin con el runtime, `EngineMemoryCallbacks` y APK mayor) |

Los motores no se cargan al arrancar. El coste de LiteRT-LM solo aparece al abrir el diagnóstico (o el chat, a partir de F11).
