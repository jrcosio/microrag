# Implementation Plan: Rendimiento en dispositivo y decisión INT8 (F14)

**Branch**: `014-ondevice-performance` | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-ondevice-performance/spec.md`

## Summary

F14 sustituye los presupuestos del informe por cifras medidas en el A063 y toma con ellas las decisiones pendientes desde F10, F11, F18 y F20. Trae cambios en la app (INT8, palancas con interruptor) y bancos de medida.

- **Runtimes:** LiteRT-LM y `litert-lm-api` 0.19.1 y SQLite-Vector 1.2.0, al empezar (R1, D-088). Si pasan las goldens en el teléfono, hay enmienda PATCH 1.2.1 de la constitución.
- **INT8 en la app** (US2):
  - preselección INT8 de 100 candidatos y reordenación exacta en Kotlin (R3);
  - estado en `rag_meta` (`quant_dirty`), precarga en la conexión;
  - un `QuantizedIndexMaintainer` que reconstruye en segundo plano tras la ingesta, el borrado y el reindexado, y precarga al arrancar;
  - la exacta mientras no está al día.
- **Bancos en el teléfono** (US1, US3, US6, US7), en pruebas instrumentadas con un kit común:
  - condiciones, JSON en `filesDir/reports` y tablas con `device_bench.py` (R4);
  - búsqueda con 10k, 50k y 100k fragmentos construidos en el escritorio con textos y vectores reales (R2);
  - generación con 4, 6 y 8 bloques, extremo a extremo y sesión sostenida (R5);
  - páginas, la 440M frente a la 270M, y E4B (R10, R11).
- **Palancas con interruptor** (US4, US5, US6), adoptadas o no con las reglas de D-088 tras la fase del teléfono:
  - reutilizar el prefijo del prompt con `clone()` (R6);
  - decodificación especulativa MTP (R7);
  - FP32 o una instrucción `v4-cifras` (R8);
  - estimador de tokens por clases de caracteres (R9);
  - precarga del motor con visión al cambiar de ámbito, y regla de seguimiento con páginas (R10);
  - bloques (R13).
- **Escritorio:** bancos, conjunto de cifras, `token-review`, `visual-followup-review`, variantes de `crosslang-review` y la repetición de `order-review` (R2, R8–R13).
- **Salida:**
  - `benchmarks.md`, el informe de cierre y la nota en el informe del RAG móvil;
  - las decisiones en DECISIONS, y la excepción D-057 cerrada o actualizada.

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 de bytecode y JBR 21, sin cambios. Python 3.12 en `offline/`.

**Primary Dependencies**:
- **Android:** `litertlm-android` 0.18.1 → **0.19.1**; `ai.sqlite:vector` 1.1.2 → **1.2.0** (R1). Ninguna nueva.
- **Python:** `litert-lm-api` 0.18.1 → **0.19.1**; `snowballstemmer` (nueva, puro Python, solo para la variante inglesa de `crosslang-review`, R12).

**Storage**:
- **`rag.db`:**
  - sigue en v5;
  - SQLite-Vector añade la tabla `vector0_chunks_embedding` (y `vector0_pages_embedding` si las páginas usan INT8), reconstruible;
  - `rag_meta` gana `pages_quant_dirty` solo si R10 lo pide;
  - sin migración: las claves que falten se leen como «desfasado».
- **`chat.db`:** sin cambios.
- **Bancos:**
  - `offline/build/bench/` (escritorio, fuera de git);
  - `getExternalFilesDir("dev-bench")` y `cacheDir/bench/` (teléfono), borrados al terminar.
- **Informes:** `filesDir/reports/f14-*.json`, copiados a `offline/build/device/`, fuera de git.

**Testing**:
- **JVM:**
  - el estimador y su paridad con Python;
  - el reparto del prompt en prefijo y resto (prefijo + resto = `build()`);
  - `QuantizedIndexMaintainer` con fakes (disparadores, coalescencia, fallos);
  - la selección INT8/exacta en `DefaultRagIndex` con un fake de conexión;
  - `HybridRetriever` con la regla de seguimiento de páginas adoptada;
  - `ChatViewModel` con la precarga al cambiar de ámbito;
  - `LayeringTest`.
- **Instrumentadas sin modelos** (emuladores y teléfono): `RagIndexInt8Test`, con SQLite-Vector real:
  - cuantizar, buscar y reordenar;
  - desfasar con cada operación;
  - reabrir y precargar;
  - fallo a mitad;
  - ámbito y exclusiones;
  - tablas `vector0_*` y `tableNames()`.
- **Instrumentadas con modelos** (`@RequiresModels`, solo en el teléfono): los bancos de US1, US3, US6 y US7, `PrefixCacheGoldenTest` (respuestas greedy idénticas con y sin prefijo reutilizado) y las goldens que ya existen.
- **Python:** pruebas de los módulos nuevos (banco, cifras, estimador, seguimiento con páginas y normalizador inglés).
- **Teléfono:** al final, con permiso (R15).

**Target Platform**:
- **App:** Android, minSdk 31 y targetSdk 37.
- **Emuladores:** `Pixel_10`, `MicroRAG_API33` y `MicroRAG_API32`, sin modelos.
- **Teléfono:** A063 (API 35), para todas las medidas.
- **Escritorio:** macOS en Apple Silicon, para la calidad y los bancos.

**Project Type**: mobile-app, con el módulo único `:app` (D-009), más el harness de Python.

**Performance Goals**:
- **Vectorial de fragmentos con INT8 a 10k:** p95 ≤ 20 ms, con Recall@20 ≥ 0,97 frente a F32 (SC-002).
- **Recuperación híbrida a 10k:** p95 ≤ 200 ms o decisión (SC-003).
- **Primer token de texto en la sesión sostenida:** p95 ≤ 5 s o la excepción D-057 actualizada (SC-004).
- **Consulta:** < 150 ms con la 270M y con la 440M.
- **Páginas:** el criterio de 20 ms.
- **El resto:** se mide e informa.

**Constraints**:
- **Privacidad:** sin red en la app. Los bancos y los modelos de prueba llegan por `adb`, verificados por SHA-256. Nada del usuario se abre, se vuelca ni se modifica (I, FR-003).
- **Capas:** LiteRT-LM solo en `rag/runtime/litert`, y SQLite-Vector solo en `data/rag` (II).
- **RAG:** los parámetros de D-034 solo cambian por una palanca adoptada con su evaluación (III).
- **GPU:** nunca dos engines en GPU; la visión, en CPU; una sola conexión de escritura (IV).
- **Veracidad:** INT8 nunca a medias ni desfasado (V).
- **Bancos:** solo en `androidTest` y en `offline/` (FR-036).

**Scale/Scope**:
- **Teléfono:**
  - bancos de 10k, 50k y 100k fragmentos, y de 1k, 5k y 10k páginas;
  - 2 vueltas de 370 preguntas por variante de búsqueda;
  - unas 6 variantes de generación con 20 preguntas;
  - 3 sesiones sostenidas de 25 minutos (base, final y páginas) y 1 con reposo;
  - 60 preguntas de cifras con 2 precisiones;
  - E4B y la 270M.
- **Escritorio:** 100k embeddings (una vez, con caché), unas 40 parejas de seguimiento, 60 preguntas de cifras, y las revisiones manuales de las respuestas que cambian.
- **Código nuevo:**
  - en la app: el mantenedor INT8, la ruta INT8 de `DefaultRagIndex`, el prefijo reutilizado y los interruptores de generación, el estimador nuevo, y la precarga por ámbito y la regla de páginas si se adoptan;
  - en `androidTest`: el kit y 7 bancos;
  - en `offline/`: unos 6 módulos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F14 | Estado |
|---|---|---|
| I. On-device y privacidad | La app no hace ninguna llamada de red. Bancos y modelos de prueba llegan por `adb` a carpetas de depuración, verificados por SHA-256, y se borran al terminar. Los bancos viven en bases aparte (`cacheDir/bench`), nunca en el `rag.db` del usuario. Los informes llevan preguntas y respuestas del corpus público, nunca datos del usuario, y nada de texto va a logcat | ✅ |
| II. Stack y arquitectura | INT8 detrás de `RagIndex` (dominio) y en `data/rag`. El mantenedor depende de interfaces. Prefijo y especulativa, dentro de `rag/runtime/litert`. Versiones verificadas en los AAR, en las fuentes y en PyPI (R1). `embedding_golden` y las goldens en cada cambio de LiteRT-LM | ✅ con la enmienda PATCH de las versiones (Complexity Tracking) |
| III. RAG según el informe | INT8 es un índice reconstruible (III) y la reordenación exacta conserva el orden F32 dentro del Recall@20 ≥ 0,97. Las palancas que cambian el contexto o el prompt (estimador, bloques, `v4-cifras`, rol de sistema) pasan por el harness con la regla de D-088 antes de adoptarse, y quedan en DECISIONS | ✅ |
| IV. Rendimiento y recursos | Es la feature que mide y aplica IV: INT8 + preload porque el p95 pasa de 20 ms (D-052); el primer token frente a 5 s en sesión sostenida; la consulta frente a 150 ms. Reconstrucción y precarga en el hilo único de la base; nunca dos engines en GPU (la precarga por ámbito libera antes de cargar) | ✅ |
| V. Veracidad del estado | INT8 solo con `quant_dirty = 0` y precargado en la conexión; un fallo deja el índice desfasado y la búsqueda en la exacta. Las medidas que no se pueden hacer se registran como «no disponible», «no cabe» o «sin espacio», nunca con cifras inventadas | ✅ |
| VI. Calidad y pruebas | Pruebas de INT8 con SQLite-Vector real, goldens de respuesta con y sin prefijo, paridad del estimador, `benchmarks.md` con cifras medidas que sustituyen los presupuestos | ✅ |
| VII. UI fiel y accesible | Sin UI nueva. Si una palanca cambia una espera anunciada, solo se ajusta el texto existente a la cifra medida | ✅ |
| VIII. Proceso | D-088 (clarify), D-089 (plan) y D-090 (analyze). Ciclo automático pedido por el usuario, con paradas tras el clarify (hecha), tras el analyze si hay algo que decidir (la enmienda PATCH y el criterio de MTP, R1 y R7), antes del teléfono y en el cierre | ✅ |

**Re-check post-diseño:** sin violaciones. La enmienda de las versiones es la vía que pide la gobernanza, no una excepción: se aplica con `/speckit-constitution` si las goldens pasan.

## Project Structure

### Documentation (this feature)

```text
specs/014-ondevice-performance/
├── spec.md
├── plan.md                          # este fichero
├── research.md                      # R1–R16
├── data-model.md                    # estado INT8, bancos, medidas, palancas y conjuntos nuevos
├── quickstart.md
├── contracts/
│   ├── int8-index.md                # RagIndex, SQL, mantenedor, disparadores y pruebas
│   ├── generation-levers.md         # GenerationConfig, prefijo, especulativa, precisión, estimador y precarga
│   ├── device-benchmarks.md         # kit, bancos, JSON, carpetas dev-* y limpieza
│   └── harness-f14.md               # subcomandos, conjuntos, reglas y pruebas de Python
├── checklists/requirements.md
├── benchmarks.md                    # cifras del teléfono (implement)
├── report.md                        # informe de cierre (implement)
└── tasks.md                         # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                                   # litertlm 0.19.1, sqliteVector 1.2.0
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/rag/{RagIndex,RagIndexConfig,RagParameters}.kt  # estado INT8, claves, INT8_*, estimador, interruptores
├── data/rag/
│   ├── DefaultRagIndex.kt                                  # ruta INT8 + reordenación, rebuild, preload
│   ├── RagConnection.kt                                    # vector_quantize*, lectura de blobs
│   └── QuantizedIndexMaintainer.kt                         # nuevo
├── data/ingest/IngestRunner.kt, data/library/DefaultLibraryRepository.kt,
│   data/ingest/DefaultImportQueueRepository.kt             # disparadores
├── data/retrieval/HybridRetriever.kt                       # regla de páginas (si se adopta)
├── data/chat/{EngineAnswerEngine,DefaultChatSession}.kt     # prefijo, interruptores, desglose de tiempos
├── rag/answer/PromptBuilder.kt                             # prefijo y resto, estimador, v4-cifras
├── rag/runtime/TextGenerator.kt                            # GenerationConfig: prefixCache, speculative
├── rag/runtime/litert/LiteRtTextGenerator.kt               # clone(), Session, SpeculativeDecodingConfig
├── feature/chat/ChatViewModel.kt                           # precarga por ámbito (si se adopta)
├── MicroRagApplication.kt                                  # precarga INT8 al arrancar
└── data/di/DataModule.kt                                   # Koin
app/src/test/java/…                                          # pruebas JVM (Testing)
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── testing/bench/{DeviceConditions,BenchReport,BenchBank,DevFiles}.kt
├── data/rag/RagIndexInt8Test.kt
├── data/retrieval/SearchScaleBenchmarkTest.kt, PageSearchBenchmarkTest.kt
├── data/chat/{GenerationBenchmarkTest,SustainedChatBenchmarkTest,NumericFidelityBenchmarkTest,
│             PrefixCacheGoldenTest}.kt, VisualChatBenchmarkTest.kt (modo sostenido)
├── rag/runtime/EmbedderComparisonBenchmarkTest.kt
└── testing/bench/BenchCleanupTest.kt

offline/
├── requirements.txt                                        # litert-lm-api 0.19.1, snowballstemmer
├── run_eval.py                                             # bench-bank, numeric-*, token-review, visual-followup-review
├── eval/{bench_bank,numeric,token_estimate,english_text,device_bench}.py
├── eval/{retrieval,pipeline,visual_retrieval}.py           # variantes nuevas
├── testset/numeric/{questions.jsonl,review.csv}, testset/visual/followups.jsonl
├── config/{rag_parameters.json,f14_rules.json}             # estimador; reglas fijadas antes de medir
├── reports/eval-report.md                                  # sección «Rendimiento (F14)»
└── tests/test_{bench_bank,numeric,token_estimate,english_text,device_bench,visual_followup}.py

docs/RAG_Movil/informe-rag-movil-v2.md                       # nota que remite a benchmarks.md
docs/roadmap/{DECISIONS.md, FEATURES.md}
.specify/memory/constitution.md                              # 1.2.1 si se adoptan los runtimes
```

**Structure Decision:**
- **INT8:** vive donde ya está el motor vectorial (`data/rag`), detrás de `RagIndex`.
- **Palancas de generación:** en el adaptador de LiteRT-LM, con interruptores en `GenerationConfig` y valores por defecto en `RagParameters`. Así las pruebas miden las dos posiciones y la decisión es un cambio de constante.
- **Bancos:** van en `androidTest`, como los de F10, F11 y F20, y no en una pantalla de diagnóstico, porque son largos y no necesitan interacción (R4).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Enmienda PATCH 1.2.0 → 1.2.1 (versiones del runtime en «Restricciones técnicas») | El usuario decidió probar 0.19.1 al empezar (D-088); `clone()` y la especulativa solo existen ahí (R1, R6, R7) | Quedarse en 0.18.1 deja sin palancas el primer token; cambiar de versión sin enmienda contradiría la constitución |
| La reordenación exacta lee los blobs F32 de 100 candidatos en Kotlin | SQLite-Vector no tiene función escalar de distancia, y `vector_full_scan` no filtra por rowid (R3) | INT8 sin reordenar da un recall por debajo de 0,97 a 100k en el Mac |
| Interruptores de palancas en `GenerationConfig` y `RagParameters` | Las palancas se deciden con las cifras del teléfono, que se miden al final (R15), y las pruebas deben cubrir las dos posiciones | Implementar solo las adoptadas obligaría a otra vuelta de implementación después de medir |
