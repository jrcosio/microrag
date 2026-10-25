# Implementation Plan: Viabilidad on-device (F02, fase 0)

**Branch**: `002-ondevice-feasibility` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-ondevice-feasibility/spec.md`

## Summary

F02 demuestra en el Nothing Phone (1) que Gemma 4 E2B y EmbeddingGemma 2 funcionan con LiteRT-LM 0.18.1, y mide su rendimiento real antes de construir descarga, base de datos o ingesta.

La feature tiene dos capas:

1. **Runtime on-device reutilizable** (source set `main`):
   - catálogo de los 5 ficheros fijados a commit y SHA-256 (research R2);
   - instalación con verificación en streaming en `noBackupFilesDir/models`;
   - interfaces `TextEmbedder` y `TextGenerator` con adaptadores de LiteRT-LM aislados en `rag.runtime.litert`;
   - truncado a 256D con L2 en código propio;
   - `EngineCoordinator`: un solo motor en GPU y liberación segura en `onTrimMemory`.

   F03, F04, F07, F10 y F11 lo reutilizan.
2. **Herramienta de diagnóstico** (solo source set `debug`), con dos formas de lanzarla: la entrada «Viabilidad on-device» del lanzador de debug y un test instrumentado `@RequiresModels`. Hace lo siguiente:
   - importa los modelos copiados por adb;
   - ejecuta la batería de embeddings (3 variantes × CPU/GPU, consulta y fragmento), compatibilidad CPU↔GPU y generación (2 ficheros de Gemma en GPU, con CPU como plan B);
   - registra estado térmico, memoria y dispositivo;
   - escribe un informe JSON y Markdown recuperable por adb.

Las cifras resultantes se registran en `benchmarks.md` y en DECISIONS (G35, G36 y backend de consulta).

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios respecto a F01).

**Primary Dependencies** (verificadas el 2026-10-25, research R1):
- **Nueva:** `com.google.ai.edge.litertlm:litertlm-android:0.18.1` (Google Maven, Apache-2.0).
  - minSdk del AAR: 24.
  - Sin permisos.
  - Sin reglas de ProGuard de consumidor; la release tiene `optimization.enable = false`, así que no aplica por ahora.
  - Nativo: `jni/arm64-v8a/liblitertlm_jni.so` (22,2 MB) y `x86_64` (26,4 MB). Es la única `.so`; la GPU usa el `libOpenCL.so` del fabricante.
- **Transitivas del AAR:** `gson` 2.14.0, `kotlin-reflect` 2.4.0 y `kotlinx-coroutines-android` 1.11.0.
  - `kotlin-reflect` se alinea con Kotlin 2.4.21 si Gradle no lo resuelve solo. Se comprueba con `:app:dependencies`.
- **Ya existentes (F01):** Compose BOM 2026.09.00, lifecycle 2.11.0, Koin 4.2.2, kotlinx-serialization-json 1.12.0 (informe JSON) y coroutines 1.11.0.

**Storage**: ficheros, sin base de datos.

| Contenido | Ubicación |
|---|---|
| Modelos verificados | `noBackupFilesDir/models/` |
| Copia de desarrollo temporal (solo debug) | `getExternalFilesDir(null)/dev-models/` |
| Caché de kernels de LiteRT-LM | `cacheDir/litertlm/` |
| Informes | `filesDir/reports/` |

**Testing**:
- JUnit 4 y Truth en la JVM (`src/test` y `src/testDebug`): matemáticas, catálogo, SHA-256, repositorio, coordinador con motores falsos, percentiles, informe, suite con fábrica falsa y ViewModel.
- Robolectric donde haga falta `Context`.
- Instrumentados (`src/androidTest`):
  - instalación y verificación con un fichero pequeño;
  - invariantes del manifiesto;
  - accesibilidad de la pantalla de diagnóstico (ATF);
  - batería completa `@RequiresModels`, solo con modelos (teléfono).

**Target Platform**:
- Medición en el Nothing Phone (1): A063, SM7325, Adreno 642L, 12 GB, Android 15 (API 35), arm64.
- Emulador `Pixel_10` (API 37) solo para tests sin modelos.
- minSdk 31 y targetSdk 37, como en `app/build.gradle.kts`.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**: son cifras a **medir**, no a cumplir por construcción. Los criterios de salida de la fase 0 son:
- TTFT medido con un prompt de ≈ 2.000 tokens;
- embedding de consulta < 150 ms p95 en CPU en al menos una variante (SC-004).

Referencias de las fichas, que no son umbrales:
- EmbeddingGemma 2 en S26 Ultra: 27 ms en CPU;
- Gemma 4 E2B en GPU: TTFT de 0,3 s con 1.024 tokens.

**Constraints**:
- Cero red y ningún permiso nuevo (FR-004).
- Diagnóstico ausente en release (FR-001, SC-007).
- Nunca dos motores en GPU (FR-012).
- Inferencia solo en `AppDispatchers.llm` y `AppDispatchers.embeddings` (FR-014).
- Cancelación en menos de 1 s (FR-011).
- Ningún texto de prompt o respuesta en logs (FR-019).
- La UI y los ViewModels no importan `com.google.ai.edge.litertlm.*` (constitución II).

**Scale/Scope**:
- 5 ficheros de modelo (≈ 5,63 GB).
- 6 combinaciones de embeddings, 3 de compatibilidad y 2 a 4 de generación.
- 1 pantalla de diagnóstico.
- Sesión completa estimada en menos de 45 min, sin contar las esperas térmicas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F02 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos: el AAR no declara ninguno y se verifica en el manifiesto fusionado y con `apkanalyzer`. Los modelos se instalan en `noBackupFilesDir/models` solo tras verificar su SHA-256. El prompt de prueba es sintético y su texto nunca va a logs. **Excepción justificada** (Complexity Tracking): la copia temporal en `dev-models` (almacenamiento externo de la app) existe solo en debug, contiene modelos públicos y se borra tras importar. No es importación de documentos, así que no aplica SAF | ✅ |
| II. Stack y arquitectura | Versiones, coordenadas y `.so` verificados en el AAR (R1). `rag.runtime.litert` es el único paquete que importa LiteRT-LM. El diagnóstico sigue Screen → ViewModel → `FeasibilitySuite` (caso de uso) → interfaces de runtime y repositorio. DI con Koin; el módulo de debug se carga desde el source set `debug` y la release usa un stub vacío | ✅ |
| III. RAG según el informe | Prefijos exactos de LiteRT-LM como constantes compartidas (`EmbeddingPrefixes`). Salida completa y truncado a 256D con L2 propio (R3). F02 no crea `rag_meta`; eso llega en F04 | ✅ |
| IV. Rendimiento y recursos | Colas dedicadas de F01. `EngineCoordinator` con un solo motor en GPU. Liberación con `onTrimMemory ≥ UI_HIDDEN` y al salir del diagnóstico. La medición de F02 decide el backend de la consulta (constitución: «CPU salvo medición contraria») | ✅ |
| V. Veracidad del estado | Solo se carga lo que está `Verified`, y se revalida antes de cargar. El progreso de verificación es real (bytes). El informe solo contiene medidas; las cifras de los contratos son ilustrativas y están marcadas. Un fichero que no coincide nunca queda en `models/` | ✅ |
| VI. Calidad y pruebas | Tests JVM de todas las piezas puras. Tests instrumentados de instalación, manifiesto y accesibilidad. Batería `@RequiresModels` en el teléfono. Las cifras van a `benchmarks.md` y sustituyen a los presupuestos. La compatibilidad frente a `embedding_golden` es de F04; F02 mide CPU↔GPU (FR-009b) | ✅ |
| VII. UI fiel y accesible | Herramienta interna: componentes `Lisdata*` y tokens, textos en recursos de debug (`translatable="false"`), estados vacío, carga, error y contenido, objetivos táctiles de 48 dp, progreso anunciado por TalkBack. No se presenta como UI de producto (FR-020) | ✅ |
| VIII. Idioma y proceso | Documentación en español y código en inglés. Ciclo SDD con parada tras analyze. Decisiones G35, G36 y backend en DECISIONS (FR-018) | ✅ |

**Re-check post-diseño (Phase 1):** sin violaciones nuevas.
- Los contratos mantienen la frontera de capas: `EngineFactory` es una interfaz en `rag.runtime` y `ExperimentalFlags` queda encapsulado en `LiteRtEngineFactory`.
- La importación de desarrollo vive solo en `debug` (`DevModelImporter`). La instalación verificada de `main` (`installVerified`) es la misma que usará la descarga de F03.

## Project Structure

### Documentation (this feature)

```text
specs/002-ondevice-feasibility/
├── spec.md
├── plan.md              # este fichero
├── research.md          # R1–R13
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── runtime-api.md   # TextEmbedder, TextGenerator, EngineFactory, EngineCoordinator, ModelFileRepository
│   ├── report-format.md # informe JSON + Markdown
│   └── provisioning.md  # descarga en el ordenador + adb + importación
├── checklists/requirements.md
├── tasks.md             # /speckit-tasks
└── benchmarks.md        # al cerrar: informe real copiado del teléfono
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                      # + litertlm = "0.18.1"
app/build.gradle.kts                           # + implementation(libs.litertlm.android)
app/src/main/AndroidManifest.xml               # + <uses-native-library libOpenCL.so / libvndksupport.so required=false>

app/src/main/java/com/lisdatasolutions/microrag/
├── MicroRagApplication.kt                     # + debugModules, + ComponentCallbacks2 → coordinator.releaseAll()
├── core/di/AppModules.kt                      # + runtimeModule
├── domain/model/
│   ├── ComputeBackend.kt                      # CPU | GPU
│   ├── ModelFile.kt                           # ModelId, ModelFamily, ModelFile
│   ├── ModelCatalog.kt                        # 5 ficheros (R2)
│   └── ModelFileState.kt
├── data/
│   ├── di/DataModule.kt                       # + ModelFileRepository
│   └── models/
│       ├── Sha256.kt
│       ├── ModelFileRepository.kt
│       └── DefaultModelFileRepository.kt      # noBackupFilesDir/models, .part + rename atómico, StatFs
└── rag/
    ├── di/RuntimeModule.kt                    # EngineCoordinator, EngineFactory
    └── runtime/
        ├── ManagedEngine.kt
        ├── TextEmbedder.kt
        ├── TextGenerator.kt                   # + GenerationEvent, GenerationStats, GenerationConfig
        ├── EngineFactory.kt                   # + ModelMetadata
        ├── EngineCoordinator.kt
        ├── EngineMemoryCallbacks.kt           # ComponentCallbacks2 → releaseAll()
        ├── EmbeddingMath.kt                   # truncate+L2, l2Norm, cosine
        ├── EmbeddingPrefixes.kt
        ├── RuntimeInfo.kt                     # LITERT_LM_VERSION
        └── litert/                            # ÚNICO paquete que importa com.google.ai.edge.litertlm
            ├── LiteRtEngineFactory.kt
            ├── LiteRtTextEmbedder.kt
            └── LiteRtTextGenerator.kt

app/src/debug/
├── assets/feasibility/
│   ├── prompt_es.txt                          # ≈ 2.000 tokens, 6 fuentes del informe (R9)
│   └── texts_es.json                          # consulta, fragmento de ≈ 350 tokens, 10+10 textos de compatibilidad
├── res/values/strings_feasibility.xml         # translatable="false"
└── java/com/lisdatasolutions/microrag/debug/
    ├── DebugEntries.kt                        # + «Viabilidad on-device»
    ├── DebugModules.kt                        # Koin: DevModelImporter, FeasibilitySuite, ViewModel…
    └── feasibility/
        ├── DevModelImporter.kt
        ├── FeasibilitySuite.kt                # orquestador compartido UI/test
        ├── MeasurementProtocol.kt
        ├── Percentiles.kt                     # nearest-rank
        ├── ThermalMonitor.kt
        ├── MemoryProbe.kt                     # VmHWM + clear_refs
        ├── DeviceSnapshotCollector.kt
        ├── FeasibilityTexts.kt                # carga los assets
        ├── FeasibilityViewModel.kt
        ├── FeasibilityUiState.kt
        ├── FeasibilityScreen.kt               # + previews
        └── report/
            ├── FeasibilityReport.kt           # modelo @Serializable (data-model §3–5)
            └── ReportWriter.kt                # JSON + Markdown
app/src/release/java/com/lisdatasolutions/microrag/debug/DebugModules.kt   # stub: emptyList()

app/src/test/java/com/lisdatasolutions/microrag/
├── domain/model/ModelCatalogTest.kt
├── data/models/{Sha256Test, DefaultModelFileRepositoryTest}.kt
├── rag/runtime/{EmbeddingMathTest, EmbeddingPrefixesTest, EngineCoordinatorTest, RuntimeInfoTest, EngineMemoryCallbacksTest}.kt
├── architecture/LayeringTest.kt               # solo rag/runtime/litert importa LiteRT-LM
└── core/di/AppModulesTest.kt                  # + runtimeModule
app/src/testDebug/java/com/lisdatasolutions/microrag/debug/
├── DebugModulesTest.kt
└── feasibility/
    ├── {PercentilesTest, FeasibilityTextsTest, DevModelImporterTest}.kt
    ├── report/ReportWriterTest.kt
    ├── {FeasibilitySuiteEmbeddingTest, FeasibilitySuiteGenerationTest}.kt   # fábrica falsa
    └── {FeasibilityViewModelModelsTest, FeasibilityViewModelLifecycleTest}.kt
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── ManifestInvariantsTest.kt                  # + uses-native-library required=false
├── data/models/ModelInstallInstrumentedTest.kt
├── rag/runtime/LiteRtSmokeTest.kt             # @RequiresModels, 270M en CPU (riesgo 1)
└── debug/feasibility/
    ├── RequiresModels.kt
    ├── FeasibilityScreenAccessibilityTest.kt
    └── FeasibilityBenchmarkTest.kt            # @LargeTest @RequiresModels
```

**Structure Decision**: módulo único con paquetes por capa (D-009), como en F01.
- El runtime y el catálogo van en `main`, porque son producto y los reutilizan F03 y siguientes.
- Todo lo de diagnóstico (importación por adb, suite, informe, pantalla y assets) va en `debug`, con stubs de release donde `main` lo referencia. Es el mismo patrón que `DebugRouting` en F01.
- Los tests de clases solo de debug van en `src/testDebug`. Nota de implementación: AGP 9 solo genera tests unitarios para el `testBuildType` (debug), así que no existe `testReleaseUnitTest`; la compilación de release se cubre con `assembleRelease` y `lintRelease`.

## Diseño de ejecución de la batería

1. **Inicio:**
   - `DeviceSnapshot`;
   - revalidación SHA-256 de cada fichero `Verified` (FR-005);
   - `benchmarkStatsEnabled = true`.
2. **Embeddings.** Por cada variante verificada y cada backend (CPU y GPU):
   1. Leer los metadatos (`ModelInfo`). Si el modelo no declara el backend, el resultado es `NotAvailable` sin cargarlo.
   2. Esperar a estado térmico NONE o LIGHT (máximo 5 min; si es SEVERE o peor, se pide confirmación).
   3. Reiniciar `VmHWM`.
   4. Inicialización en frío: `clearCompiledCache()` y luego inicializar. Después, liberar e inicializar con caché.
   5. Consultas: 3 de calentamiento y 30 medidas, rotando las 10 consultas.
   6. Fragmentos: 3 de calentamiento y 30 medidas.
   7. Comprobar dimensiones y norma.
   8. Liberar.

   Si la GPU falla, la combinación queda `NotAvailable(motivo)` y se sigue.
3. **Compatibilidad.** Por cada variante con CPU y GPU `Completed`: 20 textos en CPU, liberar, los mismos 20 en GPU y calcular el coseno mínimo y el medio.
4. **Generación.** Por cada fichero de Gemma, en GPU:
   1. Espera térmica.
   2. Inicialización en frío y con caché.
   3. 1 generación de calentamiento y 3 medidas, con 128 tokens.
   4. Liberar.

   Si falla la carga o la generación, se repite el fichero en CPU como plan B (`fallbackFromGpu = true`).
5. **Cierre:** `exitCriteria`, escritura final de JSON y Markdown y `releaseAll()`. El informe también se reescribe tras cada resultado, para que una muerte del proceso no borre lo ya medido.

**Detalles de la batería:**
- Cada fase usa un único `withEngine`, para que nunca se recargue un motor dentro de un bucle medido.
- La cancelación (botón, `ON_STOP` de la pantalla o fin del test) cancela la corrutina de la suite.
  - La generación llama a `cancelProcess()`.
  - El resultado en curso queda `Cancelled(fase)` y lo ya medido se conserva en el informe.
- La pantalla mantiene `keepScreenOn` mientras hay una ejecución en curso.
- **Inicialización «en frío»:** borra la caché de kernels de LiteRT-LM. La caché de páginas del sistema no se puede vaciar sin root, y así se indica en el informe.

## Riesgos y verificaciones tempranas

Ver research R13. Además:

1. **Primera tarea de implementación tras añadir la dependencia:** compilar e instalar, y cargar EmbeddingGemma 270M en CPU en el teléfono. Si falla la carga de `liblitertlm_jni.so` o la API no coincide con research R1, se para y se replantea antes de construir el resto.
2. **Tamaño del APK release:** crece ≈ 22 MB por `liblitertlm_jni.so` en arm64. Es esperado (el producto la necesita desde F03/F04). Se registra en el informe de cierre.
3. **`ExperimentalFlags` es global y experimental:** se activa solo desde la suite de debug y se contrasta con medidas de reloj de pared (R4).
4. **Tests instrumentados en el teléfono:** `connected*AndroidTest` de Gradle desinstala la app al terminar, lo que borraría los 5,6 GB de modelos importados. En el teléfono, los tests instrumentados se ejecutan con `installDebug` + `installDebugAndroidTest` + `am instrument`, excluyendo `@RequiresModels` salvo cuando se busca la batería (quickstart §1 y §5).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Copia temporal de modelos en almacenamiento externo de la app (`dev-models`), solo en debug | adb no puede escribir directamente en `noBackupFilesDir` de una app sin `run-as`. La copia externa permite una importación verificada desde la propia app | `run-as` con `/data/local/tmp` depende de SELinux y del fabricante; queda como plan B documentado. Descargar en la app adelantaría F03 y metería red en F02 |
| Uso de `ExperimentalFlags.enableBenchmark` (API experimental) | Es la única fuente de TTFT, prefill y decode internos del runtime | Medir solo con reloj de pared no separa prefill de decode. Se usan ambas fuentes |
