# Informe de cierre — F02 Viabilidad on-device (fase 0)

**Fecha:** 2026-10-26

**Rama:** `002-ondevice-feasibility`

**Estado:** cerrada; aprobada por el usuario el 2026-10-26.

**G44:** resuelto con la opción (b) en D-026 (constitución v1.1.0, ver §5).

## 1. Qué se ha implementado

**Runtime on-device (source set `main`, reutilizable desde F03)**
- **LiteRT-LM:** `com.google.ai.edge.litertlm:litertlm-android:0.18.1`, verificado en el AAR (`liblitertlm_jni.so`, sin permisos). `kotlin-reflect` se alinea con Kotlin 2.4.21.
- **Librerías nativas:** `libOpenCL.so` y `libvndksupport.so` declaradas como `uses-native-library` opcionales.
- **Catálogo:** `ModelCatalog` con los 5 ficheros fijados a commit, tamaño y SHA-256.
- **Estados de fichero:** `Missing`, `Unverified`, `Verifying`, `Verified`, `Mismatch` y `Failed`.
- **Instalación verificada:** `DefaultModelFileRepository` instala en `noBackupFilesDir/models`.
  - Copia a `.part` mientras calcula el SHA-256 y renombra de forma atómica.
  - Comprueba el espacio con `StatFs` y admite cancelación.
  - Un fichero con huella incorrecta nunca queda instalado.
- **Interfaces:** `TextEmbedder` y `TextGenerator`, más `EngineFactory` y adaptadores de LiteRT-LM aislados en `rag.runtime.litert`. `LayeringTest` lo comprueba.
- **Embeddings:** `EmbeddingPrefixes` con los prefijos exactos y `EmbeddingMath` (truncado a 256D y L2 en código propio).
- **Coordinación:** `EngineCoordinator`, con un único motor en GPU y sin cerrar nunca un motor mientras se usa.
- **Memoria:** `EngineMemoryCallbacks` libera los motores con `onTrimMemory ≥ UI_HIDDEN`.
- **Generación:** `GenerationConfig` con muestreo greedy, límite de salida y precisión de activaciones (R14).

**Diagnóstico (solo debug)**
- **Entrada:** «MicroRAG Debug → Viabilidad on-device».
- **Modelos:**
  - importación por adb desde `dev-models` (la carpeta la crea la app, R7);
  - verificación al abrir, con progreso real;
  - estados con texto, no solo con color.
- **`FeasibilitySuite`:** la usan la pantalla y el test instrumentado. Mide:
  - embeddings (consulta y fragmento de ≈ 350 tokens);
  - compatibilidad CPU↔GPU;
  - generación (por defecto y FP32, con CPU como plan B).

  Además:
  - espera térmica y confirmación si el teléfono está SEVERE o peor;
  - memoria pico (`VmHWM`);
  - cancelación y liberación al pasar a segundo plano;
  - pantalla encendida mientras mide.
- **Informe:** JSON y Markdown en `filesDir/reports`, reescrito tras cada resultado y recuperable por adb.
- **Test `FeasibilityBenchmarkTest`:** batería sin intervención, lanzada con `am instrument`.
- **Investigaciones:** `GemmaGpuFileExperimentTest` (R14) y `EmbeddingVariantsExperimentTest` (R15).

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ sin incidencias |
| Tests JVM (`testDebugUnitTest`) | ✅ 90/90 (F01: 23 → F02: 90) |
| Instrumentados en el emulador `Pixel_10` | ✅ 99: 95 pasan y 4 se omiten (`@RequiresModels` sin modelos) |
| Instrumentados en el teléfono (`am instrument`, sin `@RequiresModels`) | ✅ 95/95 |
| `@RequiresModels` en el teléfono | ✅ prueba de humo, batería definitiva y 2 investigaciones |
| Release | ✅ sin diagnóstico ni permisos de red; nativos opcionales; solo arm64. Detalle en `checklists/release-verification.md` |

Validaciones manuales en el teléfono:
- **US1:** importación de 5,6 GB en ≈ 20 s; una copia truncada se rechaza («Huella incorrecta») sin dejar `.part`.
- **US3, cancelación:** 154 ms durante la decodificación y 920 ms durante el prefill.
- **US5, segundo plano:** con una prueba en GPU en curso, pulsar Inicio la cancela y libera el motor, sin cierres en `logcat -b crash`.

## 3. Diferencias frente al mockup

**No aplica.** F02 no tiene UI de producto. La herramienta de diagnóstico es interna (FR-020) y usa solo componentes `Lisdata*`, tokens y textos en recursos de debug. Su accesibilidad la cubre `FeasibilityScreenAccessibilityTest`: 5 estados × claro/oscuro, ATF y objetivos táctiles de 48 dp en toda la lista.

## 4. Cifras medidas

Fuente: [`benchmarks.md`](benchmarks.md) y [`benchmarks.json`](benchmarks.json). Nothing Phone (1), Android 15, LiteRT-LM 0.18.1, enchufado, estado térmico NONE durante toda la sesión. 2026-10-26.

**Embeddings (EmbeddingGemma 2):**

| Variante | CPU: consulta p50/p95 | CPU: fragmento p50 | GPU: consulta p95 | GPU: fragmento p50 | Init frío / caché CPU | Init frío / caché GPU |
|---|---:|---:|---:|---:|---:|---:|
| 270M | 119,1 / **120,3 ms** | 874 ms | 141,1 ms | 355 ms | 782 / 271 ms | 6.818 / 1.152 ms |
| 440M | 119,8 / 122,6 ms | 903 ms | 140,7 ms | 327 ms | 817 / 304 ms | 6.164 / 1.060 ms |
| 740M | 119,8 / 124,8 ms | 886 ms | 124,0 ms | 320 ms | 825 / 309 ms | 6.186 / 1.069 ms |

Datos comunes:
- vectores de 768 → 256 dimensiones, norma 1,000000;
- contexto máximo declarado de 8.192 tokens;
- memoria pico de 550–660 MB.

**Compatibilidad CPU↔GPU (20 textos):** coseno mínimo 0,99321 y medio 0,99744. Compatible en las tres variantes.

**Generación (Gemma 4 E2B en GPU, prompt de 1.986 tokens y 128 de salida):** ver D-023. Con el fichero genérico y la precisión por defecto:
- TTFT de **4,53 s** (4,75 s de reloj de pared);
- prefill de 450 tok/s y decode de 8,8 tok/s;
- inicialización de 16,9 s en frío y 5,4 s con caché;
- memoria pico de 2,48 GB.

**Criterios de salida de la fase 0:**
- TTFT medido ✅;
- consulta < 150 ms p95 en CPU ✅ (120,3 ms).

**Arranque en frío de la release:** 680 ms en el teléfono y 205 ms en el emulador (181 ms en F01).

## 5. Decisiones y hallazgos

- **D-023 (G35):** se usa el fichero **genérico** de Gemma en GPU. El fichero `-gpu` genera texto incoherente en la Adreno 642L con fp16, su precisión por defecto. Con FP32 funciona, pero es casi 3 veces más lento (R14).
- **D-024:** consulta en **CPU**. La ingesta puede usar la **GPU**, que es unas 2,5 veces más rápida, y se pueden mezclar vectores de ambos backends (coseno ≥ 0,99, con poco margen).
- **D-025 (G36):** se usa la variante **270M**. Las tres variantes dan vectores de texto idénticos (coseno 1,0, R15). F05 deja de comparar variantes.
- **G44, resuelto (D-026):** el TTFT de 4,5 s con unos 2.000 tokens superaba el presupuesto de 3 s de la constitución (IV). El usuario eligió enmendar el presupuesto con la cifra medida: la constitución v1.1.0 fija ≤ 5 s de extremo a extremo con ≈ 2.000 tokens. F05 elige el número de bloques y F14 evalúa palancas para bajarlo.

  Decode de 8,8 tok/s.
- **Otros hallazgos:**
  - La carpeta `dev-models` la tiene que crear la app; si la crea adb, la app no puede leerla (R7).
  - AGP 9 no genera tests unitarios de release.
  - El prompt sintético se amplió de 1.523 a 1.986 tokens (R9).

## 6. Desviaciones respecto al plan

- **Generación medida también con FP32 en GPU** (R14). Se añadió tras descubrir el problema de fp16; la spec (FR-010) está actualizada.
- **Dos tests de investigación instrumentados** para fundamentar G35 y G36. No eran tareas del plan.
- **Fragmentos de compatibilidad más cortos de lo planificado:** ≈ 120–160 tokens en lugar de 150–350. No afecta a la comparación CPU↔GPU.
- **`ModelFileState.Unverified`:** añadido durante la generación de tareas, para los ficheros presentes sin verificar en la sesión.

## 7. Estado en el roadmap

F02 queda ✅ cerrada en `docs/roadmap/FEATURES.md`. G35, G36 y G44 quedan resueltos en INVENTARIO.
