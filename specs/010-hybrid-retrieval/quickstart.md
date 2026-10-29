# Quickstart — validar F10

Requisitos:
- **JBR de Android Studio:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Entorno `offline/.venv`** de F04/F05, con el índice `offline/build/index/ref.db` y la caché `offline/build/query_embeddings.json` de F05.
- **Emulador `Pixel_10`** (API 37). Las pruebas con modelos se saltan si el emulador no los tiene instalados.
- **Teléfono de referencia** (A063, API 35) con los modelos instalados y verificados. **Solo en la última fase** (§6), por petición del usuario.
- **Variables:** `ADB=$HOME/Library/Android/sdk/platform-tools/adb`, `EMU`, `PHONE` y `PKG=com.lisdatasolutions.microrag`, como en F06/F07.

## 1. Escritorio: golden y revisión del orden

```bash
offline/.venv/bin/python -I offline/gen_retrieval_golden.py --check
offline/.venv/bin/python -I offline/run_eval.py order-review
offline/.venv/bin/python -I offline/run_eval.py report
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:**
- **El generador:** no cambia `git diff` (regla G1).
- **`order-review`:**
  - D-034 da Recall@bloques 0,889 sobre 370 preguntas;
  - ninguna variante llega a +2 puntos (research R12);
  - `eval-report.md` tiene la sección «Revisión del orden (D-035, F10)».
- **Pruebas Python:** en verde, incluidas las nuevas de `trigram_query`, `rerank`, `FUSED_LIMIT` y la forma de los golden (G5).

## 2. Build, lint y pruebas JVM

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest
```

**Esperado:**
- **`FollowUpRuleTest`, `ReciprocalRankFusionTest` y `ContextSelectorTest`:** los casos de `test_retrieval.py` y `test_selector.py` con el mismo resultado, más los propios (SC-001).
- **`RetrievalGoldenTest`:** las 426 filas de `retrieval_golden.jsonl` (seguimiento, fusión y bloques con D-034 y con la configuración del informe).
- **`QueryOverflowTest`:** primero se recorta la anterior y después la actual.
- **`HybridRetrieverTest`** (con `FakeRagIndex` buscable y `FakeTextEmbedder`):
  - orden de las comprobaciones y estados sin búsqueda sin cargar motor;
  - fallo del embedding;
  - fallo del índice;
  - candidatos que dejan de ser buscables;
  - dos llamadas simultáneas;
  - registros sin texto (SC-006, SC-008).
- **`EngineQueryEmbedderTest`:** regla «listo» con `refresh()` una vez; clave CPU; carga bajo demanda.
- **`RetrievalDuringIngestTest`:** con un `EngineCoordinator` real y la ingesta reteniendo el motor de CPU con un embedder que bloquea, la recuperación termina sin esperar al final del bloque (FR-020).
- **`EngineCoordinatorTest` ampliado:**
  - la consulta comparte el motor retenido sin esperar al final del bloque;
  - el cierre espera a todos los usuarios;
  - la retención sobrevive al usuario que termina;
  - el LLM sigue exclusivo (regla Q13).
- **`RagParametersTest`** con `fused_limit`.
- **`RetrievalDiagnosticViewModelTest` y `RetrievalDiagnosticScreenTest`** (Robolectric, `testDebug`): todos los estados.
- **`DebugModulesTest`:** grafo completo con el módulo nuevo.
- **`LayeringTest`:** nada de `src/main` ni de `src/release` referencia `debug.retrieval` (V1).

## 3. Pruebas instrumentadas en el emulador

Como en F06/F07: se instalan los dos APK y se lanza `am instrument`.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
$ADB -s $EMU install -r -t app/build/outputs/apk/debug/app-debug.apk
$ADB -s $EMU install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s $EMU shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
```

**Esperado:**
- **`RagIndexSearchableLoadTest`:** `loadChunks` y `neighbor` con `searchableOnly` sobre SQLite real, con documentos en todos los estados (FR-015).
- **`HybridRetrieverVisibilityTest`:** sobre SQLite-Vector real, con un embedder determinista:
  - con documentos en cada estado (excluido, pendiente, con error, en pausa, fuera del índice y sombra), ningún bloque contiene sus fragmentos (SC-002);
  - con 4.300 fragmentos ocultos, cada búsqueda sigue dando hasta 50 candidatos;
  - con `rag_meta` alterado, «índice no disponible» sin cargar el motor.
- **Las pruebas de F04–F09:** siguen en verde.

### Con modelos en el emulador (CPU, sin umbrales)

```bash
$ADB -s $EMU shell am instrument -w -e annotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels \
  -e class $PKG.data.retrieval.RetrievalBenchmarkTest,$PKG.data.retrieval.RetrievalDuringIndexingTest \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $EMU logcat -d -s MicroRAG | grep -E "retrieval-benchmark|retrieval-during-indexing"
```

**Esperado:** las dos pruebas terminan y registran sus cifras. Los umbrales solo se comprueban en el A063 (§6).

## 4. Diagnóstico en el emulador

1. Abrir «MicroRAG Debug» → «Diagnóstico de búsqueda».
2. Con documentos indexados, buscar una pregunta: se ven el texto de búsqueda, los bloques con sus fragmentos, los candidatos con sus puntuaciones y los tiempos.
3. Buscar «¿Y el aceite?» con una pregunta anterior: «Seguimiento: sí» y el texto unido.
4. Pregunta vacía, «Pregunta vacía».
5. `adb logcat -d -s MicroRAG | grep retrieval`: solo cifras, sin texto de la pregunta.

## 5. Release

```bash
./gradlew :app:assembleRelease
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer dex packages app/build/outputs/apk/release/app-release-unsigned.apk | grep -c "debug.retrieval"
```

**Esperado:** 0 (SC-009). El paquete `…microrag.debug` solo contiene los stubs de F02.

## 6. Última fase: teléfono de referencia

Cuando el teléfono esté disponible. Los modelos del teléfono se usan tal como están: nunca se borran ni se descargan otra vez. Los registros se filtran a la app.

```bash
$ADB -s $PHONE install -r -t app/build/outputs/apk/debug/app-debug.apk
$ADB -s $PHONE install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s $PHONE shell am instrument -w -e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels,com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE shell am instrument -w -e annotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels \
  -e class $PKG.data.retrieval.RetrievalBenchmarkTest,$PKG.data.retrieval.RetrievalDuringIndexingTest,$PKG.data.retrieval.RetrievalParityTest \
  $PKG.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s $PHONE logcat -d -s MicroRAG | grep -E "retrieval-benchmark|retrieval-during-indexing|retrieval-parity"
```

**Esperado:**
- **Instrumentadas sin modelos:** en verde también en el teléfono.
- **`RetrievalBenchmarkTest`** (SC-003, SC-004):
  - total p95 ≤ 200 ms y embedding p95 < 150 ms, con el motor cargado y 10k fragmentos;
  - p50 y p95 de cada paso con y sin 4.300 ocultos;
  - la primera búsqueda con el motor sin cargar.
- **`RetrievalDuringIndexingTest`** (SC-007):
  - p95 ≤ 1 s con la tanda en GPU;
  - cifra informativa con la tanda en CPU.
- **`RetrievalParityTest`** (SC-005):
  - |Recall@bloques del teléfono − escritorio| ≤ 0,05 sobre las mismas preguntas;
  - las no extraídas, aparte.
- **Diagnóstico** (§4) en el teléfono, con su biblioteca real.
  - Las capturas recortan la barra de estado.
  - Ninguna muestra documentos del usuario: se usa una pregunta sin resultados o un documento de prueba en una carpeta propia, que se borra al terminar.
- **INT8:** con el p95 vectorial medido, se presenta al usuario la recomendación para F14 y se registra su decisión (research R13).
