# Research — F02 Viabilidad on-device

**Fecha:** 2026-10-25
**Rama:** `002-ondevice-feasibility`

Todo lo de este documento está verificado contra el AAR publicado (inspeccionado con `javap`), el código de la etiqueta `v0.18.1` en GitHub y la API de Hugging Face. No se supone ninguna firma ni versión (constitución II).

## R1 — LiteRT-LM en Android

| Dato | Valor verificado | Fuente |
|---|---|---|
| Coordenadas | `com.google.ai.edge.litertlm:litertlm-android:0.18.1` (Google Maven) | `maven-metadata.xml` y POM |
| Dependencias transitivas | `gson` 2.14.0, `kotlin-reflect` 2.4.0, `kotlinx-coroutines-android` 1.11.0 | POM. Compatibles con F01 (Kotlin 2.4.21, coroutines 1.11.0) |
| minSdk del AAR | 24 | `AndroidManifest.xml` del AAR. El proyecto usa 31 |
| Librería nativa | `jni/arm64-v8a/liblitertlm_jni.so` (22,2 MB) y `jni/x86_64/…` (26,4 MB) | Contenido del AAR. El release solo empaqueta arm64 (P8) |
| Permisos del AAR | Ninguno | Manifiesto del AAR |
| GPU | Requiere `libOpenCL.so` del fabricante, presente en el teléfono (`/vendor/lib64/libOpenCL.so`, Adreno 642L). Se declaran `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` y `libvndksupport.so` (informe, Estructura del proyecto) | adb + informe |

**API verificada** (paquete `com.google.ai.edge.litertlm`):

- **Backends:** `Backend.CPU(threadCount?: Int, numOfThreads?: Int)` y `Backend.GPU()`.
- **LLM:**
  - `Engine(EngineConfig(modelPath, backend, visionBackend?, audioBackend?, maxNumTokens?, maxNumImages?, cacheDir?, activationDataType?))`;
  - `initialize()`, `isInitialized()`, `close()`, `createConversation(ConversationConfig)`.
- **Conversación:**
  - `sendMessageAsync(String, …): Flow<Message>` (streaming);
  - `cancelProcess()`;
  - `getBenchmarkInfo(): BenchmarkInfo`;
  - `getTokenCount(): Int`;
  - `close()`.
- **Configuración de conversación:** `ConversationConfig(systemInstruction?, initialMessages, tools, samplerConfig?, …, maxOutputToken: Int?, thinkingConfig?, enableResponseFormat, enableSpeculativeDecoding: Boolean?, chatTemplate?)`.
- **`BenchmarkInfo`:** `initTimeInSecond`, `timeToFirstTokenInSecond`, `lastPrefillTokenCount`, `lastDecodeTokenCount`, `lastPrefillTokensPerSecond`, `lastDecodeTokensPerSecond`. Se activa con `ExperimentalFlags.enableBenchmark = true`.
- **Embeddings:**
  - `EmbeddingEngine(EmbeddingEngineConfig(modelPath?, backend?, visionBackend?, audioBackend?, cacheDir?, modelFd?, maxInputLength?, visionTokensPerImage?, activationDataType?))`;
  - `initialize()`, `computeEmbedding(List<InputData>, EmbeddingOptions): EmbeddingResponse` (`embedding: FloatArray`), `close()`.
  - `EmbeddingOptions(normalize?, insertSpecialTokens?, outputSize?, visionTokensPerImage?)`.
- **Metadatos:** `ModelInfo.from(path)` da `maxContextTokens()`, `supportedBackends(Modality)`, `modelType` y `minRuntimeVersion()`.
- **Benchmark sintético integrado:** `benchmark(modelPath, backend, prefillTokens, decodeTokens, …)`. Se usa como contraste opcional, no como medida principal.

**Decisión:** dependencia `litertlm-android:0.18.1` en el source set `main`. Los adaptadores del runtime son código de producto que reutilizarán F04, F07, F10 y F11. Solo la herramienta de diagnóstico vive en `debug`.

## R2 — Ficheros de modelo fijados a un commit

Hugging Face `litert-community`, consultado el 2026-10-25. Todos los repositorios son **públicos, sin gate y Apache-2.0**.

| Id | Repositorio @ commit | Fichero | Bytes | SHA-256 |
|---|---|---|---:|---|
| `gemma4-e2b` | `gemma-4-E2B-it-litert-lm` @ `1caa527c1e88b55f046fcd8d2b4265606680663d` | `gemma-4-E2B-it.litertlm` | 2 588 147 712 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| `gemma4-e2b-gpu` | ídem | `gemma-4-E2B-it-gpu.litertlm` | 2 008 432 640 | `a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5` |
| `eg2-740m` | `embeddinggemma-2-740m-litert-lm` @ `24d962e906c7d332c6428e71c9676855024569e2` | `embeddinggemma-2-740m.litertlm` | 484 622 336 | `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c` |
| `eg2-440m` | `embeddinggemma-2-text-vision-440m-litert-lm` @ `e301f74d5551b0c2641bd5cb4652a76239d5c5f8` | `embeddinggemma-2-text-vision-440m.litertlm` | 387 710 976 | `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0` |
| `eg2-270m` | `embeddinggemma-2-text-270m-litert-lm` @ `9be6e8b90982095dc05c2bd162e4b954ee4dbac7` | `embeddinggemma-2-text-270m.litertlm` | 164 626 432 | `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb` |

Total: 5 633 540 096 bytes (≈ 5,63 GB).

**Notas:**
- Los repositorios publican variantes NPU compiladas para Qualcomm SM8550, SM8650, SM8750, SM8850 y QCS8275, y para Tensor G5/G6 e Intel/MediaTek. **No hay variante para el SM7325** del Nothing Phone (1), así que el NPU queda fuera de F02.
- Existe `gemma-4-E2B-it-web.litertlm`, para web, que no aplica.
- La ficha de Gemma 4 recomienda AICore (Gemini Nano) para producción en dispositivos compatibles. Queda anotado (ya figura en el INVENTARIO), sin efecto en F02.

## R3 — Embeddings: prefijos, firmas de entrada y normalización

- **Prefijos** (documentación oficial «Embedding models», actualizada el 2026-10-06):
  - documento: `task: search result | text: `;
  - consulta: `task: search query | text: `.

  La doc indica recortar los espacios antes de añadir el prefijo. El ejemplo oficial (`'task: search query | text: Top places…'`) confirma el espacio tras `text:`. Coinciden con el informe.
- **Firmas de texto:** la ficha del modelo indica 128, 256, 512, 1024, 2048 y 8192 tokens. `EmbeddingEngineConfig.maxInputLength` hace que el motor «seleccione automáticamente las firmas hasta esa longitud» (`embedding_engine_settings.h`, v0.18.1).
  - **Decisión:** `maxInputLength = 512`, que cubre consultas (firma de 128) y fragmentos del chunker de ~300–400 tokens con cabecera y prefijo (firma de 512).
  - La latencia publicada (S26 Ultra: 27 ms en CPU) corresponde a la firma de 128.
- **Salida:** dimensiones de 128, 256, 512 o 768 (Matryoshka).
  - **Decisión (informe, Truncación):** pedir la salida completa (`outputSize = null`, `normalize = false`) y truncar a 256 con renormalización L2 **en código propio**. Así el resultado coincide bit a bit con el pipeline offline de F04/F05.
  - Se comprueba que la longitud es 256 y la norma 1 ± 1e-5.
- **Longitud máxima declarada:** `ModelInfo.from(path).maxContextTokens()`. Se contrasta con la ficha (8192).
- **Métrica añadida en el plan:** además de la latencia de consulta, se mide la de un **fragmento de documento de ≈ 350 tokens** (firma de 512). Cuesta un bucle más y anticipa el ritmo de ingesta (chunks/s) de F07 (informe, Ingesta). Se refleja en la spec como FR-007b.

## R4 — Medición de la generación

- **Fuente principal:** `ExperimentalFlags.enableBenchmark = true` (global y experimental; solo se activa en el diagnóstico de debug) y luego `Conversation.getBenchmarkInfo()` tras cada generación.
- **Contraste propio:**
  - reloj de pared alrededor de `Engine.initialize()`;
  - tiempo entre `sendMessageAsync` y el primer `Message` emitido;
  - número de tokens del prompt según `lastPrefillTokenCount`.
- **Configuración:**
  - `EngineConfig.maxNumTokens = 4096` (prompt de ≈ 2.100 tokens más 128 de salida, con margen);
  - `ConversationConfig.maxOutputToken = 128`;
  - muestreo greedy (`SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)`) para que las ejecuciones sean reproducibles;
  - decodificación especulativa desactivada: se mide la base. Queda como palanca para F14.
- **Ficha de Gemma 4** (S26 Ultra, GPU): prefill 3 808 tok/s, decode 52 tok/s, TTFT 0,3 s con 1 024 tokens. Sirve de referencia, no de umbral.
- **Caché de GPU:** `EngineConfig.cacheDir = cacheDir/litertlm`. La primera inicialización compila kernels; las siguientes usan la caché. Se informan **las dos**:
  - «inicialización en frío», con la caché borrada;
  - «inicialización con caché».

## R5 — Memoria

- **Pico de memoria residente del proceso:** `VmHWM` de `/proc/self/status`, que se reinicia antes de cada prueba escribiendo `5` en `/proc/self/clear_refs` (permitido sobre el propio proceso). Si el reinicio no está disponible, se informa el pico desde el arranque del proceso y se indica.
- **Complementos:** `Debug.getNativeHeapAllocatedSize()` y `VmRSS` tras la carga.
- **Limitación:** la memoria de GPU no se puede medir desde la app; las fichas tampoco la incluyen. Se documenta.

## R6 — Estado térmico, batería y dispositivo

- **Estado térmico:** `PowerManager.currentThermalStatus` (NONE…SHUTDOWN) y `getThermalHeadroom(10)`, que da un margen numérico (API 30+).
- **Carga:** broadcast fijo `ACTION_BATTERY_CHANGED` (`EXTRA_PLUGGED`, `EXTRA_LEVEL`, `EXTRA_TEMPERATURE`).
- **Dispositivo:** `Build.MANUFACTURER/MODEL/SOC_MANUFACTURER/SOC_MODEL` (API 31+), `Build.VERSION.RELEASE/SDK_INT` y `ActivityManager.MemoryInfo.totalMem`.
- **Política:** antes de cada bloque se espera (con un tope de 5 minutos) a que el estado térmico sea NONE o LIGHT. Si es SEVERE o peor, se avisa y se pide confirmación (FR-015).

## R7 — Aprovisionamiento de los modelos en el teléfono

- **Decisión:**
  1. `adb push` de los ficheros al **directorio externo propio de la app**: `/sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models/`. El shell de adb puede escribir ahí en Android 11+.
  2. El diagnóstico ofrece «Importar modelos de desarrollo»: **copia con verificación SHA-256 en streaming** a `noBackupFilesDir/models/` (constitución I) y borra la copia externa si la huella coincide.
  3. La verificación se repite al cargar.
- **Alternativa:** `adb push` a `/data/local/tmp` más `run-as … cp` a `no_backup/`. Depende de SELinux y del fabricante; queda como plan B en el quickstart.
- **Riesgo:** algunas capas de fabricante restringen `Android/data` al shell. Se comprueba en la implementación con un fichero pequeño antes de copiar 5,6 GB.

## R8 — Coordinación de motores y ciclo de vida

- **`EngineCoordinator`** (paquete `rag.runtime`) controla con un `Mutex` los motores cargados.
  - Al pedir un motor en GPU, cierra antes cualquier otro motor de GPU (FR-012).
  - Los motores de CPU pueden coexistir, pero el diagnóstico los ejecuta en secuencia.
- **Liberación:**
  - `Application.registerComponentCallbacks` llama a `coordinator.releaseAll()` en `onTrimMemory(level ≥ TRIM_MEMORY_UI_HIDDEN)`;
  - la pantalla de diagnóstico lo llama también al salir (FR-013).
- **Colas:** inicialización e inferencia de LLM en `AppDispatchers.llm`; embeddings en `AppDispatchers.embeddings` (F01, FR-014).
- **Cancelación:**
  - generación: `Conversation.cancelProcess()` más cancelación de la corrutina;
  - embeddings: se comprueba entre repeticiones.
  - Objetivo: menos de 1 s (FR-011). Una inicialización en curso no se puede interrumpir; se espera a que termine y se libera.

## R9 — Prompt de prueba de unos 2.000 tokens

- **Contenido:** texto sintético en español en `app/src/debug/assets/feasibility/prompt_es.txt`.
  - Sigue la plantilla del informe («Responde a la PREGUNTA usando únicamente las FUENTES…»).
  - Lleva **6 fuentes numeradas** (fragmentos de `docs/RAG_Movil/informe-rag-movil-v2.md`, documentación del propio proyecto) y una pregunta.
  - No son datos de usuario (FR-019).
- **Tokens:** el número real lo da `lastPrefillTokenCount`. Si se desvía más de ±10 % de 2.000, se ajusta el texto.
- **Plantilla de chat:** la aplica `Conversation` con la plantilla del modelo.

## R10 — Informe de resultados

- **Ficheros:** `filesDir/reports/feasibility-<yyyyMMdd-HHmmss>.json` (kotlinx.serialization) y `.md` (tablas legibles).
- **Recuperación:** `adb exec-out run-as com.lisdatasolutions.microrag cat files/reports/<fichero>`; funciona porque la app de debug es `debuggable`.
- **Registro en el repositorio:** el Markdown se copia a `specs/002-ondevice-feasibility/benchmarks.md` y las decisiones van a DECISIONS (FR-018).

## R11 — Ejecución reproducible

Hay **dos formas de lanzar** la batería de pruebas, que comparten el mismo orquestador (`FeasibilitySuite`):

1. **Desde la UI** de «MicroRAG Debug → Viabilidad on-device».
2. **Como test instrumentado** `FeasibilityBenchmarkTest`:
   - anotado con `@LargeTest` y una anotación propia `@RequiresModels`;
   - hace `assumeTrue` si faltan modelos, para no fallar en el emulador;
   - se lanza en el teléfono con `-Pandroid.testInstrumentationRunnerArguments.annotation=…RequiresModels`.

   Permite repetir la sesión completa sin intervención y deja el informe en el dispositivo.

## R12 — Pruebas

| Objetivo | Tipo |
|---|---|
| Truncado, L2 y coseno; percentiles; catálogo de modelos (huellas hex de 64 y nombres únicos); verificador SHA-256 (fichero temporal, progreso, discrepancia); reglas de `EngineCoordinator` con motores falsos; formato del informe (JSON ↔ Markdown); plantilla del prompt | JVM (`app/src/test`) |
| Importación y verificación en un dispositivo real con un fichero pequeño | Instrumentado (emulador/teléfono, sin modelos) |
| Liberación en `onTrimMemory` (`EngineMemoryCallbacks`) | JVM, más comprobación manual en el teléfono |
| Batería completa con modelos | Instrumentado `@RequiresModels` en el teléfono de referencia |
| Release sin diagnóstico y sin permisos de red | `apkanalyzer` (como en F01 P6) |

## R13 — Riesgos

| Riesgo | Mitigación |
|---|---|
| Delegado de GPU no compatible con algún modelo en Adreno 642L | Se registra como «no disponible» con el error; Gemma pasa a CPU como plan B (clarify) |
| Memoria insuficiente con Gemma de 2,6 GB y 12 GB de RAM | Un solo motor a la vez; captura de excepción; se libera todo antes de cargar |
| Estrangulamiento térmico en sesiones largas | Espera térmica entre bloques; se registran el estado y el margen al inicio y al final |
| `ExperimentalFlags` es API experimental | Solo en el diagnóstico de debug; se contrasta con medidas de reloj de pared |
| Acceso de adb a `Android/data` | Plan B con `run-as` (R7) |
