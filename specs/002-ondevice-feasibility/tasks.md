---

description: "Lista de tareas de F02 — Viabilidad on-device (fase 0)"
---

# Tasks: Viabilidad on-device (F02, fase 0)

**Input**: Documentos de diseño en `/specs/002-ondevice-feasibility/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **Obligatorios** por la constitución (VI) y por los criterios de la spec (SC-001, SC-005, SC-006, SC-007). En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec:
- US1 — Modelos verificados (P1);
- US2 — Embeddings (P1);
- US3 — Generación (P1);
- US4 — Informe (P2);
- US5 — Memoria y GPU (P2).

La medición definitiva en el teléfono y las decisiones van en la fase final, cuando todo el código está cerrado.

**Entorno**:
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Teléfono de referencia: `adb -s P31281001066`.
- Emulador para los tests sin modelos: `Pixel_10`.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Las rutas son relativas a la raíz del repositorio. Abreviaturas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/debug/` = `app/src/debug/java/com/lisdatasolutions/microrag/debug/`;
  - `…/test/`, `…/testDebug/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/`, `app/src/testDebug/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: añadir LiteRT-LM y tener los modelos en el ordenador y en el teléfono.

- [X] T001 Añadir a `gradle/libs.versions.toml`:
  - `litertlm = "0.18.1"`;
  - la librería `litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }`.
- [X] T002 Añadir `implementation(libs.litertlm.android)` a `app/build.gradle.kts`.
  - Ejecutar `./gradlew :app:dependencies --configuration debugRuntimeClasspath` y anotar en `specs/002-ondevice-feasibility/research.md` (R1, «Resueltas en el build») las versiones resueltas de `gson`, `kotlin-reflect` y `kotlinx-coroutines-android`.
  - Si `kotlin-reflect` resuelve por debajo de `libs.versions.kotlin`, añadir una restricción `constraints { implementation("org.jetbrains.kotlin:kotlin-reflect:<kotlin>") }` que lo alinee.
- [X] T003 Declarar dentro de `<application>` en `app/src/main/AndroidManifest.xml`:
  ```xml
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  ```
  Añadir un comentario de por qué (research R1). Comprobar que `./gradlew :app:assembleDebug :app:lintDebug` sigue en verde.
- [X] T004 [P] Descargar los 5 ficheros a `$HOME/MicroRAG-models` según `specs/002-ondevice-feasibility/contracts/provisioning.md` §1 (en segundo plano, ≈ 5,6 GB, fuera del repositorio).
  - Comprobar `shasum -a 256` contra research R2.
  - Si alguna huella no coincide, parar e informar.
- [X] T005 Depende de T003 y T004:
  1. Instalar la app de debug en el teléfono (`./gradlew :app:installDebug`) y abrirla una vez, para que el sistema cree `Android/data/<paquete>` con el propietario correcto. **Hallazgo:** `dev-models` también la tiene que crear la app; si la crea adb, la app no puede leerla (research R7). Se resolvió moviendo los ficheros a una carpeta creada por la app.
  2. Prueba de escritura en `dev-models` (quickstart §3).
  3. `adb push` de los 5 ficheros a `/sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models/` del teléfono P31281001066.
  4. Si la prueba de escritura falla, usar el plan B de `contracts/provisioning.md` y registrarlo en research R7.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**:
- catálogo, matemáticas, interfaces y adaptadores de LiteRT-LM;
- coordinador de motores;
- inyección de dependencias del debug;
- infraestructura de medición.

Todas las historias dependen de esta fase.

**⚠️ CRITICAL**: ninguna historia empieza hasta cerrar esta fase, incluida la prueba de humo T026 en el teléfono.

### Tests (escribir primero)

- [X] T006 [P] `…/test/domain/model/ModelCatalogTest.kt`:
  - 5 entradas, 2 `LLM` y 3 `EMBEDDING`;
  - `id` y `fileName` únicos;
  - `sha256` de 64 hex en minúscula y `revision` de 40 hex;
  - tamaños y huellas idénticos a research R2.
- [X] T007 [P] `…/test/rag/runtime/EmbeddingMathTest.kt`:
  - truncado de 768 a 256 con norma 1 ± 1e-5;
  - el truncado conserva la dirección de los 256 primeros componentes;
  - vector nulo o con menos de 256 componentes → error;
  - coseno igual a 1 para vectores idénticos y 0 para ortogonales.
- [X] T008 [P] `…/test/rag/runtime/EmbeddingPrefixesTest.kt`:
  - constantes exactas, con el espacio final (research R3);
  - `query("  hola ")` → `"task: search query | text: hola"`;
  - `document(content, header)` → `"task: search result | text: <header>\n<content>"` con los espacios recortados;
  - con cabecera vacía no hay salto inicial.
- [X] T009 [P] `…/test/rag/runtime/RuntimeInfoTest.kt`: `RuntimeInfo.LITERT_LM_VERSION` coincide con el valor de `litertlm` en `gradle/libs.versions.toml` (ruta relativa al módulo: `../gradle/libs.versions.toml`).
- [X] T010 [P] `…/test/rag/runtime/EngineCoordinatorTest.kt`, con un `FakeEngine` que registra su cierre y su backend:
  1. pedir un motor de GPU B con otro de GPU A cargado cierra A **antes** de ejecutar la fábrica de B;
  2. `loaded` nunca contiene más de un motor de GPU, observado con un collector durante una secuencia de cargas;
  3. los motores de CPU coexisten;
  4. `release` espera a que termine el `block` en curso;
  5. si se cancela el `block`, la cancelación se propaga y el motor sigue cargado hasta `release`;
  6. si falla la fábrica, no queda ninguna entrada;
  7. `releaseAll` cierra todos los motores y deja `loaded` vacío.
- [X] T011 [P] `…/test/data/models/Sha256Test.kt`:
  - huella de vectores conocidos (cadena vacía, `"abc"`);
  - fichero de 3 MB por streaming igual a `MessageDigest` en un solo bloque;
  - `onBytes` monótono que termina en el tamaño total.
- [X] T012 [P] `…/test/architecture/LayeringTest.kt`: recorre `app/src/main/java` y `app/src/debug/java`. Solo los ficheros bajo `rag/runtime/litert/` pueden contener `import com.google.ai.edge.litertlm` (constitución II).
- [X] T013 [P] `…/testDebug/debug/feasibility/PercentilesTest.kt`:
  - método nearest-rank: con 30 muestras, p50 es la 15.ª y p95 la 29.ª ordenadas;
  - lista vacía → error;
  - una sola muestra → p50 = p95.

### Implementación

- [X] T014 [P] Crear en `…/microrag/domain/model/`:
  - `ComputeBackend.kt`;
  - `ModelFile.kt` (`ModelId`, `ModelFamily`, `ModelFile`);
  - `ModelCatalog.kt`, con los 5 ficheros de research R2;
  - `ModelFileState.kt`, con los estados de data-model §2, incluido `Unverified`.
- [X] T015 [P] Crear `EmbeddingPrefixes.kt`, `EmbeddingMath.kt` y `RuntimeInfo.kt` en `…/microrag/rag/runtime/`, según `contracts/runtime-api.md`.
- [X] T016 [P] Crear `…/microrag/data/models/Sha256.kt`: streaming con un buffer de 1 MB, hex en minúscula y callback `onBytes`.
- [X] T017 Crear las interfaces en `…/microrag/rag/runtime/`, según `contracts/runtime-api.md`:
  - `ManagedEngine.kt`;
  - `TextEmbedder.kt`;
  - `TextGenerator.kt` (`GenerationEvent`, `GenerationStats`, `GenerationConfig`);
  - `EngineFactory.kt` (`ModelMetadata`).
- [X] T018 Crear `…/microrag/rag/runtime/EngineCoordinator.kt`:
  - un `Mutex` de carga y otro de uso por motor;
  - `loaded: StateFlow<Set<EngineKey>>`;
  - registrar con `AppLogger`, tag `MicroRAG`, `engine load <modelId> <backend>` y `engine release <modelId> <backend>`, sin contenido (quickstart §7).

  Hacer pasar T010.
- [X] T019 Crear `…/microrag/rag/runtime/litert/LiteRtTextEmbedder.kt`:
  - `EmbeddingEngineConfig(modelPath, backend, cacheDir = cacheDir/litertlm, maxInputLength = 512)`;
  - `initialize()` en `AppDispatchers.embeddings`;
  - `embedQuery` y `embedDocument` con `EmbeddingPrefixes`, llamando a `computeEmbedding(listOf(InputData.Text(…)), EmbeddingOptions(normalize = false, outputSize = null))`, y luego `EmbeddingMath.truncateAndNormalize(256)`;
  - `rawDimensions` según la última salida;
  - `close()`.
- [X] T020 Crear `…/microrag/rag/runtime/litert/LiteRtTextGenerator.kt`. Antes de escribirlo, comprobar con `javap` en el AAR cómo se extrae el texto de `Message` y la firma completa de `sendMessageAsync`:
  - `Engine(EngineConfig(modelPath, backend, maxNumTokens, cacheDir))` e `initialize()` en `AppDispatchers.llm`;
  - `generate()` crea una `Conversation` nueva con:
    - `ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.0, 0)` si es greedy;
    - `maxOutputToken`;
    - `enableSpeculativeDecoding = false)`;
  - emite `Token` por cada delta y `Finished(stats)` con `BenchmarkInfo` (si está activo) y el TTFT de reloj de pared;
  - cancelar la corrutina llama a `cancelProcess()` y cierra la conversación;
  - el texto nunca se registra en logs.
- [X] T021 Crear `…/microrag/rag/runtime/litert/LiteRtEngineFactory.kt`:
  - correspondencia de backends `CPU → Backend.CPU()` y `GPU → Backend.GPU()`;
  - `metadata()` mediante `ModelInfo.from(path)`, con `maxContextTokens()` y `supportedBackends(Modality.TEXT)`;
  - `clearCompiledCache()` borra `cacheDir/litertlm`;
  - `benchmarkStatsEnabled` ↔ `ExperimentalFlags.enableBenchmark`.

  Antes de escribirlo, comprobar con `javap` en el AAR los nombres exactos de `Modality` y `ModelInfo` (research R1).
- [X] T022 Crear `…/microrag/rag/di/RuntimeModule.kt` (`single { EngineCoordinator }`, `single<EngineFactory> { LiteRtEngineFactory }`) y añadirlo a `…/microrag/core/di/AppModules.kt`.
  - Ajustar `extraTypes` en `…/test/core/di/AppModulesTest.kt` si `verify()` lo pide.
  - Hacer pasar T006–T012 con `./gradlew :app:testDebugUnitTest`.
- [X] T023 Inyección de dependencias del debug:
  - `…/debug/DebugModules.kt` (`val debugModules: List<Module>`);
  - stub `app/src/release/java/com/lisdatasolutions/microrag/debug/DebugModules.kt`, que devuelve `emptyList()`;
  - `…/microrag/MicroRagApplication.kt` carga `appModules + debugModules`;
  - test `…/testDebug/debug/DebugModulesTest.kt` con `verify()` del grafo completo.
- [X] T024 [P] Crear la infraestructura de medición en `…/debug/feasibility/`:
  - `Percentiles.kt`; hacer pasar T013.
  - `MeasurementProtocol.kt`, con los valores por defecto de data-model §3.
  - `ThermalMonitor.kt`:
    - interfaz `ThermalSource` e implementación con `PowerManager`;
    - `awaitCool(max = 5 min)`.
  - `MemoryProbe.kt`: `resetPeak()` escribe `5` en `/proc/self/clear_refs` y devuelve si es posible; `peakRssMb()` lee `VmHWM`.
  - `DeviceSnapshotCollector.kt`: `Build`, `MemoryInfo`, `ACTION_BATTERY_CHANGED` y versiones.
- [X] T025 Crear el modelo de informe `…/debug/feasibility/report/FeasibilityReport.kt`, `@Serializable` según data-model §3–5:
  - `ScenarioResult` con los estados `COMPLETED`, `CANCELLED`, `FAILED` y `NOT_AVAILABLE`;
  - métricas de embeddings, generación y compatibilidad;
  - `DeviceSnapshot` y `exitCriteria`.
- [X] T026 Prueba de humo en el teléfono (riesgo 1 del plan):
  1. Crear la anotación `…/androidTest/debug/feasibility/RequiresModels.kt`.
  2. Crear `…/androidTest/rag/runtime/LiteRtSmokeTest.kt` (`@RequiresModels`):
     - localiza `embeddinggemma-2-text-270m.litertlm` en `dev-models` o en `no_backup/models`;
     - lo copia a `cacheDir/smoke/` calculando su SHA-256 en streaming con `Sha256` y lo compara con el catálogo (no se carga nada sin verificar);
     - lo carga en CPU con `LiteRtEngineFactory` desde esa copia, que se borra al terminar;
     - embebe una consulta y comprueba 256 dimensiones y norma 1;
     - ejecuta `metadata()`.
  3. Ejecutarlo en P31281001066 con `am instrument -e class …LiteRtSmokeTest` (nunca `connected*AndroidTest` en el teléfono: desinstala la app; plan, riesgo 4).

  **Si falla la carga nativa o la API no coincide con research R1: PARAR e informar al usuario.**

**Checkpoint**: runtime funcionando en el teléfono con un modelo real y tests JVM en verde.

---

## Phase 3: User Story 1 — Modelos verificados en el teléfono antes de usarlos (Priority: P1) 🎯 MVP

**Goal**:
- importar los ficheros de `dev-models` con verificación SHA-256 en streaming a `noBackupFilesDir/models`;
- mostrar el estado de cada fichero;
- impedir cargar cualquier fichero que no esté verificado.

**Independent Test**: quickstart §4. Los 5 ficheros quedan «Verificado». Un fichero truncado aparece como «Huella incorrecta» y no queda en `models/`.

### Tests for User Story 1 ⚠️

- [X] T027 [P] [US1] `…/test/data/models/DefaultModelFileRepositoryTest.kt`. Directorios temporales, catálogo inyectado con huellas de ficheros pequeños y `freeBytes` falso. Casos:
  - `installVerified` correcto: queda el fichero final, no queda `.part` y la fuente se borra si se pide;
  - huella distinta: se borra el `.part`, se conserva la fuente y el estado es `Mismatch`;
  - sin espacio: `Failed`, sin copiar;
  - `refresh`: presente → `Unverified` y ausente → `Missing`;
  - `verify` de un fichero íntegro → `Verified`;
  - `verify` de un fichero corrupto → `Mismatch` y el fichero se borra;
  - `pathOf` solo devuelve ruta en `Verified`;
  - el progreso de `Verifying` es monótono y termina en el 100 %.
- [ ] T028 [P] [US1] `…/testDebug/debug/feasibility/DevModelImporterTest.kt`:
  - `pendingCopies` solo incluye nombres del catálogo e ignora otros ficheros;
  - `importAll` llama a `installVerified(…, deleteSourceOnSuccess = true)` por cada copia pendiente.
- [ ] T029 [P] [US1] `…/testDebug/debug/feasibility/FeasibilityViewModelModelsTest.kt` (repositorio e importador falsos):
  - las filas combinan el estado y la copia pendiente;
  - al abrir se ejecuta `refresh` y después `verify` de cada `Unverified`;
  - la acción Importar actualiza las filas;
  - un modelo que no está en `Verified` deja sus pruebas deshabilitadas en el `UiState`.
- [ ] T030 [P] [US1] `…/androidTest/data/models/ModelInstallInstrumentedTest.kt`. En el dispositivo, sobre `noBackupFilesDir` real, con un fichero de 1 MB y un catálogo de prueba:
  - instalación correcta;
  - instalación con huella distinta;
  - el fichero instalado queda bajo `context.noBackupFilesDir/models`.

  En el teléfono se ejecuta con `am instrument` (quickstart §1).

### Implementation for User Story 1

- [X] T031 [US1] Crear `…/microrag/data/models/ModelFileRepository.kt` y `DefaultModelFileRepository.kt`, según `contracts/runtime-api.md`:
  - copia a `.part` con SHA-256 en streaming y `rename` atómico;
  - `StatFs` (tamaño más 64 MB);
  - E/S en `AppDispatchers.io`.

  Registrarlo en `…/microrag/data/di/DataModule.kt` con `modelsDir = noBackupFilesDir/models`. Hacer pasar T027.
- [ ] T032 [US1] Crear `…/debug/feasibility/DevModelImporter.kt` (`devDir = getExternalFilesDir(null)/dev-models`) y registrarlo en `…/debug/DebugModules.kt`. Hacer pasar T028.
- [ ] T033 [P] [US1] Crear `app/src/debug/res/values/strings_feasibility.xml`, con `translatable="false"`. Incluye los textos de la pantalla:
  - título «Viabilidad on-device»;
  - estados de modelo;
  - acciones Importar, Verificar de nuevo y Cancelar;
  - secciones;
  - avisos térmicos;
  - errores.
- [ ] T034 [US1] Crear `…/debug/feasibility/FeasibilityUiState.kt` y `FeasibilityViewModel.kt`, con la sección de modelos y la verificación automática al abrir. Registrar el ViewModel en `DebugModules.kt`. Hacer pasar T029.
- [ ] T035 [US1] Crear `…/debug/feasibility/FeasibilityScreen.kt`, con la sección «Modelos»:
  - una fila por fichero con nombre, tamaño, huella abreviada y chip de estado con texto (no solo color);
  - `LisdataLinearProgress` mientras verifica;
  - botones Importar y Verificar de nuevo;
  - estado vacío con la instrucción de copia;
  - previews con estados falsos.

  Usa solo componentes `Lisdata*` y tokens.
- [ ] T036 [US1] Añadir `DebugEntry("Viabilidad on-device") { onBack -> FeasibilityScreen(onBack) }` en `…/debug/DebugEntries.kt`, con `koinViewModel()`.
- [ ] T037 [US1] Validación en el teléfono (quickstart §4):
  - importar los 5 ficheros → «Verificado» y `dev-models` vacío;
  - prueba negativa con un fichero truncado → «Huella incorrecta» y nada en `no_backup/models`;
  - volver a copiar el fichero correcto.

  Ejecutar T030 en el teléfono con `am instrument` (quickstart §1).

**Checkpoint**: US1 operativa. Los 5 modelos verificados en el teléfono.

---

## Phase 4: User Story 2 — Medir el embedding de consultas en el teléfono (Priority: P1)

**Goal**: para cada variante × backend, medir:
- la inicialización en frío y con caché;
- la consulta y el fragmento (p50/p95);
- las dimensiones y la norma;
- el contexto máximo;
- la memoria y el estado térmico;
- la compatibilidad CPU↔GPU.

**Independent Test**: lanzar «Embeddings» y «Compatibilidad» desde la pantalla. Aparecen las 6 combinaciones y las 3 compatibilidades, o el motivo de las que no se pueden ejecutar.

### Tests for User Story 2 ⚠️

- [ ] T038 [P] [US2] `…/testDebug/debug/feasibility/FeasibilityTextsTest.kt` (Robolectric, con los assets de debug):
  - `texts_es.json` tiene 10 consultas, 10 fragmentos y 1 fragmento de medida, todos no vacíos;
  - el fragmento de medida tiene entre 1.000 y 2.500 caracteres (≈ 350 tokens; el tamaño real lo confirma el teléfono).
- [ ] T039 [P] [US2] `…/testDebug/debug/feasibility/FeasibilitySuiteEmbeddingTest.kt`, con fábrica, embedders, reloj (`nanoTime` inyectado), térmica y memoria falsos. Comprueba:
  - orden por combinación: `metadata` → `clearCompiledCache` → inicialización en frío → liberar → inicialización con caché → 3+30 consultas → 3+30 fragmentos → liberar;
  - si `metadata.supportedBackends` no incluye el backend → `NotAvailable` sin llamar a la fábrica;
  - un modelo que no queda `Verified` tras la revalidación inicial nunca llega a la fábrica (SC-001);
  - percentiles calculados con el reloj falso;
  - la comprobación de dimensiones (256) y norma, que pasa a `Failed` si no se cumple;
  - un fallo de GPU queda `NotAvailable(motivo)` y se continúa con la siguiente combinación;
  - la compatibilidad solo se ejecuta si CPU y GPU están `Completed`, con el coseno mínimo y el medio, y `compatible` solo si el mínimo es ≥ 0,99;
  - con estado térmico SEVERE se pide confirmación; si se rechaza, el resultado es `Cancelled("thermal")`;
  - la cancelación entre repeticiones da `Cancelled(fase)` y conserva lo ya medido.

### Implementation for User Story 2

- [ ] T040 [P] [US2] Crear `app/src/debug/assets/feasibility/texts_es.json`:
  - 10 consultas en español sobre el contenido del informe RAG;
  - 10 fragmentos de 150–350 tokens;
  - 1 fragmento de medida de ≈ 350 tokens.

  Texto sintético a partir de `docs/RAG_Movil/informe-rag-movil-v2.md` (FR-019).
- [ ] T041 [US2] Crear `…/debug/feasibility/FeasibilityTexts.kt`, que carga los assets con kotlinx.serialization. Hacer pasar T038.
- [ ] T042 [US2] Crear `…/debug/feasibility/FeasibilitySuite.kt`. El constructor recibe `EngineCoordinator`, `EngineFactory`, `ModelFileRepository`, `FeasibilityTexts`, `ThermalSource`, `MemoryProbe`, `DeviceSnapshotCollector`, `AppDispatchers`, `AppLogger` y `nanoTime: () -> Long`, para que T039 pueda inyectar falsos.
  - esqueleto de sesión:
    - `progress: StateFlow<SuiteProgress>` (fase, combinación y repetición i/N reales);
    - revalidación SHA-256 inicial;
    - espera térmica por bloque, con callback de confirmación;
    - acumulación de resultados;
    - cancelación;
    - `releaseAll` al final.
  - bloque de embeddings y bloque de compatibilidad, con un `withEngine` por fase (plan, «Diseño de ejecución»).

  Registrarlo en `DebugModules.kt`. Hacer pasar T039.
- [ ] T043 [US2] Ampliar el ViewModel y la pantalla con la sección «Embeddings»:
  - botones Embeddings y Compatibilidad, deshabilitados si no hay ninguna variante verificada;
  - progreso real;
  - Cancelar;
  - por combinación: inicialización en frío y con caché, consulta y fragmento p50/p95, dimensiones (bruta → 256), norma, contexto máximo, memoria pico y estado térmico inicial → final;
  - compatibilidad: coseno mínimo y medio, y si es compatible;
  - diálogo térmico con `LisdataConfirmationDialog`.
- [ ] T044 [US2] Ejecución exploratoria en el teléfono de los bloques de embeddings y compatibilidad desde la UI. Revisar que las cifras sean coherentes (no se registran todavía; la medición definitiva va en T066). Anotar cualquier problema de GPU por variante.

**Checkpoint**: US2 operativa.

---

## Phase 5: User Story 3 — Medir la generación de respuestas de Gemma 4 E2B (Priority: P1)

**Goal**: para cada fichero de Gemma, medir en GPU (con CPU como plan B):
- la inicialización en frío y con caché;
- el TTFT;
- el prefill y el decode;
- la memoria;
- los tokens reales del prompt;
- la muestra de salida.

**Independent Test**: lanzar «Generación». Aparecen las cifras de los 2 ficheros y una respuesta legible. Cancelar detiene la generación en menos de 1 s.

### Tests for User Story 3 ⚠️

- [ ] T045 [P] [US3] `…/testDebug/debug/feasibility/FeasibilitySuiteGenerationTest.kt`, con un generador falso:
  - se prueba primero en GPU;
  - si la inicialización en GPU falla → se repite en CPU con `fallbackFromGpu = true` y el motivo;
  - si la generación en GPU falla → mismo plan B;
  - si el plan B en CPU también falla → `Failed(motivo)` y la suite continúa con el siguiente fichero;
  - 1 generación de calentamiento y 3 medidas con `maxOutputTokens = 128`;
  - las `GenerationStats` se trasladan a `GenerationMetrics`;
  - al cancelar durante la generación se llama a `cancel()` y el resultado es `Cancelled`;
  - un `LogSink` falso nunca recibe ninguna subcadena del prompt ni de la salida (FR-019);
  - `sampleOutput` se guarda en el resultado.
- [ ] T046 [P] [US3] Ampliar `…/testDebug/debug/feasibility/FeasibilityTextsTest.kt`: `prompt_es.txt` no está vacío, contiene las fuentes `[1]`…`[6]`, la instrucción de la plantilla del informe y una PREGUNTA.

### Implementation for User Story 3

- [ ] T047 [P] [US3] Crear `app/src/debug/assets/feasibility/prompt_es.txt` (research R9):
  - plantilla del informe («Responde a la PREGUNTA usando únicamente las FUENTES…»);
  - 6 fuentes numeradas con fragmentos de `docs/RAG_Movil/informe-rag-movil-v2.md`;
  - una pregunta;
  - objetivo de ≈ 2.000 tokens (≈ 8.000 caracteres en español como estimación inicial).
- [ ] T048 [US3] Añadir el bloque de generación a `…/debug/feasibility/FeasibilitySuite.kt`:
  - `GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 128, greedy = true)`;
  - inicialización en frío y con caché;
  - 1 + 3 generaciones;
  - plan B en CPU;
  - un `withEngine` por fase.

  Hacer pasar T045 y T046.
- [ ] T049 [US3] Ampliar el ViewModel y la pantalla con la sección «Generación», por fichero y backend:
  - inicialización;
  - tokens del prompt;
  - TTFT (benchmark y reloj de pared);
  - prefill y decode en tok/s;
  - memoria;
  - estado térmico;
  - plan B;
  - la muestra de salida en un bloque desplegable.

  Botón Generación y Cancelar.
- [ ] T050 [US3] Ejecución exploratoria en el teléfono:
  - comprobar `lastPrefillTokenCount`. Si se desvía más de ±10 % de 2.000, ajustar `prompt_es.txt` y repetir;
  - verificar que Cancelar surte efecto en menos de 1 s (cronometrado en el log de debug).

**Checkpoint**: US3 operativa.

---

## Phase 6: User Story 4 — Informe de viabilidad y decisiones (Priority: P2)

**Goal**: guardar el informe JSON y Markdown con fecha y hora en `filesDir/reports`, recuperable por adb, con los criterios de salida, y poder repetir la batería sin intervención.

**Independent Test**: tras una sesión, `adb exec-out run-as … cat files/reports/<ts>.md` devuelve el informe completo con dispositivo, versiones y combinaciones.

### Tests for User Story 4 ⚠️

- [ ] T051 [P] [US4] `…/testDebug/debug/feasibility/report/ReportWriterTest.kt`:
  - el JSON hace ida y vuelta (`encode` → `decode`) con un informe completo;
  - el Markdown contiene las 7 secciones de `contracts/report-format.md`;
  - `exitCriteria`:
    - `bestQueryP95CpuMs` es el mínimo sobre las combinaciones `Completed` en CPU;
    - `queryUnder150msCpu` se calcula a partir de él;
    - `ttftMeasured` es verdadero si hay alguna generación `Completed` con TTFT;
  - un informe con resultados `Cancelled` o `NotAvailable` muestra el estado y el motivo;
  - el nombre del fichero es `feasibility-yyyyMMdd-HHmmss`.

### Implementation for User Story 4

- [ ] T052 [US4] Crear `…/debug/feasibility/report/ReportWriter.kt`: JSON con kotlinx.serialization y Markdown con tablas, en `filesDir/reports/`, en `AppDispatchers.io`. Hacer pasar T051.
- [ ] T053 [US4] La suite reescribe el informe (mismo nombre de sesión) tras cada resultado y al terminar o cancelarse, con los resultados parciales y sus estados. Así una muerte del proceso solo pierde la medición en curso. Añadir el bloque «Batería completa», que ejecuta embeddings → compatibilidad → generación.
- [ ] T054 [US4] Ampliar el ViewModel y la pantalla con la sección «Resumen»:
  - criterios de salida (TTFT medido; mejor p95 de consulta en CPU; cumple < 150 ms sí/no);
  - ruta del último informe; al abrir la pantalla se lee el más reciente de `filesDir/reports`, para que sobreviva a una muerte del proceso;
  - botón «Batería completa».
- [ ] T055 [US4] Crear `…/androidTest/debug/feasibility/FeasibilityBenchmarkTest.kt` (`@LargeTest @RequiresModels`):
  - `assumeTrue` si los 5 modelos no están `Verified` tras `refresh` + `verify`;
  - ejecuta la batería completa con la suite de Koin;
  - comprueba que se ha escrito el informe y que tiene `schemaVersion = 1`.

  Ejecutable con `am instrument` (quickstart §5, opción B).
- [ ] T056 [US4] Validación de la recuperación: con una sesión corta (un bloque), recuperar el `.md` y el `.json` con `adb exec-out run-as` (quickstart §6) y comprobar que tienen el formato del contrato.

**Checkpoint**: US4 operativa.

---

## Phase 7: User Story 5 — Uso seguro de memoria y GPU (Priority: P2)

**Goal**:
- liberar los motores cuando la app pasa a segundo plano (`onTrimMemory ≥ UI_HIDDEN`) y al salir del diagnóstico;
- cancelar la prueba en curso;
- mantener la pantalla encendida mientras se ejecuta;
- un único motor en GPU, ya garantizado por T018.

**Independent Test**: con una prueba en curso, pulsar Inicio → la prueba queda «Cancelada» y `loaded` vacío. El log muestra `engine release … GPU` antes de cada `engine load … GPU`.

### Tests for User Story 5 ⚠️

- [ ] T057 [P] [US5] `…/test/rag/runtime/EngineMemoryCallbacksTest.kt`:
  - `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` y los niveles superiores llaman a `releaseAll()`;
  - los niveles inferiores (`RUNNING_*`) no;
  - `onLowMemory` llama a `releaseAll()`.
- [ ] T058 [P] [US5] `…/testDebug/debug/feasibility/FeasibilityViewModelLifecycleTest.kt`:
  - `onStop()` cancela la suite, espera a que termine (`cancelAndJoin`) y después llama a `releaseAll()`;
  - el resultado en curso queda `Cancelled`;
  - `isRunning` controla `keepScreenOn`.

### Implementation for User Story 5

- [ ] T059 [US5] Crear `…/microrag/rag/runtime/EngineMemoryCallbacks.kt` (`ComponentCallbacks2`, con su propio `CoroutineScope(SupervisorJob() + Dispatchers.Default)`) y registrarlo en `…/microrag/MicroRagApplication.kt` con `registerComponentCallbacks`. Hacer pasar T057.
- [ ] T060 [US5] En `…/debug/feasibility/FeasibilityScreen.kt`:
  - `LifecycleEventEffect(ON_STOP) { viewModel.onStop() }`;
  - `LocalView.current.keepScreenOn = state.isRunning`;
  - al salir (`onBack`) se llama a `releaseAll()`.

  Hacer pasar T058.
- [ ] T061 [US5] Validación en el teléfono (quickstart §7):
  - Inicio durante una prueba de GPU → «Cancelada» al volver;
  - `adb logcat -s MicroRAG` muestra el orden release → load de GPU;
  - `adb logcat -b crash` vacío.

**Checkpoint**: las 5 historias están operativas.

---

## Phase 8: Polish, medición definitiva y cierre

**Purpose**:
- accesibilidad de la herramienta y verificación de la release;
- la medición definitiva con cifras;
- decisiones y cierre de la feature.

- [ ] T062 [P] Crear `…/androidTest/debug/feasibility/FeasibilityScreenAccessibilityTest.kt`: ATF (`enableAccessibilityChecks()`) y objetivos táctiles ≥ 48 dp sobre `FeasibilityScreen`, con `UiState` falsos en estos estados:
  - sin modelos;
  - verificando;
  - con resultados;
  - ejecutando;
  - error.

  Ejecutarlo en el emulador `Pixel_10`.
- [ ] T063 Puertas de calidad:
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest :app:testReleaseUnitTest`;
  - `:app:connectedDebugAndroidTest` en el emulador (los `@RequiresModels` deben salir *skipped*);
  - en el teléfono, `am instrument` con `-e notAnnotation …RequiresModels` (quickstart §1), nunca `connected*AndroidTest`.

  Todo en verde.
- [ ] T064 Verificación de la release con `apkanalyzer` (quickstart §2), registrada en `specs/002-ondevice-feasibility/checklists/release-verification.md`:
  - sin clases `debug.feasibility` ni `DevModelImporter`;
  - sin `INTERNET`;
  - `lib/arm64-v8a/liblitertlm_jni.so` sin `x86_64`;
  - `uses-native-library` con `required=false`;
  - tamaño del APK frente a F01.
- [ ] T065 Comprobar el arranque en frío de la release en el emulador (mediana de 5, como en F01) para descartar una regresión por la nueva dependencia. Se registra en el mismo checklist.
- [ ] T066 **Medición definitiva** en el teléfono P31281001066 (quickstart §5, opción B con `am instrument`):
  - batería completa con la app recién instalada;
  - la caché de kernels se borra en cada inicialización en frío;
  - registrar si el teléfono estaba enchufado.
- [ ] T067 Recuperar el informe (quickstart §6) en `specs/002-ondevice-feasibility/benchmarks.md` y `benchmarks.json`. Revisar que estén las 6 + 3 + 2 (o más) combinaciones y los criterios de salida (SC-002, SC-003, SC-009).
- [ ] T068 **Si ninguna variante cumple < 150 ms p95 en CPU (SC-004): PARAR y decidir con el usuario** antes de seguir con T069.
- [ ] T069 Registrar en `docs/roadmap/DECISIONS.md` (D-023 en adelante), con las cifras de `benchmarks.md`:
  - fichero de Gemma (G35);
  - backend del embedding de consulta;
  - variantes de EmbeddingGemma 2 candidatas para F05 (G36);
  - si consultas e ingesta pueden usar backends distintos (compatibilidad CPU↔GPU).

  Actualizar el estado de G35 y G36 en `docs/roadmap/INVENTARIO.md`.
- [ ] T070 Escribir `specs/002-ondevice-feasibility/report.md` con:
  - el resumen;
  - los resultados de build, lint y tests;
  - las diferencias frente al mockup (no aplica: herramienta interna sin mockup; se indica);
  - las cifras medidas;
  - los riesgos encontrados.

  Actualizar el estado de F02 en `docs/roadmap/FEATURES.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** T001 → T002 → T003. T004 se puede lanzar en paralelo desde el principio, porque es una descarga larga. T005 depende de T004.
- **Foundational (fase 2):** depende de T001–T003. T026 depende además de T005 y bloquea todas las historias.
- **US1 (fase 3):** depende de la fase 2.
- **US2 y US3 (fases 4 y 5):**
  - para la ejecución en el teléfono (T044, T050) dependen de US1, porque necesitan modelos `Verified`;
  - su código y sus tests JVM solo dependen de la fase 2.
- **US4 (fase 6):** depende de US2 y US3 para la batería completa.
- **US5 (fase 7):** depende de la fase 2 y de la pantalla de US1. Puede ir en paralelo a US2–US4, salvo en `FeasibilityScreen.kt`, que se edita en secuencia.
- **Polish (fase 8):** depende de todas las historias. T066 → T067 → T068 → T069 → T070.

### User Story Dependencies

- **US1:** base de todas las ejecuciones con modelos.
- **US2 y US3:** independientes entre sí, salvo `FeasibilitySuite.kt` y la pantalla, que se editan en secuencia.
- **US4:** agrega los resultados de US2 y US3.
- **US5:** transversal.

### Parallel Opportunities

- T004 (descarga) mientras se hace T001–T003 y toda la fase 2 de código.
- Tests de la fase 2: T006–T013 en paralelo.
- Implementación de la fase 2: T014, T015, T016 y T024 en paralelo.
- T027–T030 en paralelo. T033 en paralelo a T031–T032.
- T038 y T039, T040; T045 y T046, T047.
- T057 y T058 en paralelo a las fases 4–6.

---

## Parallel Example: Phase 2

```bash
# Tests primero (ficheros distintos):
T006 ModelCatalogTest   T007 EmbeddingMathTest   T008 EmbeddingPrefixesTest
T009 RuntimeInfoTest    T010 EngineCoordinatorTest  T011 Sha256Test
T012 LayeringTest       T013 PercentilesTest
# Implementación sin dependencias entre sí:
T014 domain/model/*   T015 rag/runtime/{EmbeddingPrefixes,EmbeddingMath,RuntimeInfo}
T016 data/models/Sha256   T024 debug/feasibility/{Percentiles,ThermalMonitor,MemoryProbe,…}
```

---

## Implementation Strategy

### MVP primero (US1)

1. Fases 1 y 2, incluida la prueba de humo T026 en el teléfono. Es el mayor riesgo técnico.
2. US1: modelos verificados en el teléfono. **Parar y validar.**

### Entrega incremental

1. US2: cifras de embeddings (criterio < 150 ms).
2. US3: cifras de generación (TTFT).
3. US4: informe recuperable y batería reproducible.
4. US5: ciclo de vida seguro.
5. Fase 8: medición definitiva, decisiones y cierre.

---

## Notes

- Ninguna cifra se inventa. Las de los contratos son ilustrativas; las de `benchmarks.md` salen del teléfono (constitución V).
- Los modelos nunca se versionan (`*.litertlm` está en `.gitignore`).
- T026 y T068 son paradas obligatorias si fallan.
- Al cerrar F02 hay que hacer push tras la aprobación del usuario (constitución VIII).
