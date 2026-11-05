# Implementation Plan: Viabilidad del RAG visual (F18)

**Branch**: `018-visual-rag-feasibility` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-visual-rag-feasibility/spec.md`

## Summary

F18 decide, con cifras, si se construyen F19 y F20 (D-066, D-073). No cambia el producto (FR-031).

- **En el teléfono:** un diagnóstico nuevo, solo en debug, amplía el de F02 (R3, R4).
  - **Gemma con visión:** primer token con 1–3 páginas a 70, 140 y 280 tokens, y con páginas partidas en trozos; carga del codificador, memoria y lectura de 10–15 páginas.
  - **EmbeddingGemma 2 de 440M:** golden e identidad con la 270M; consulta, fragmento y página a 70 y 140 tokens (R5).
  - **`PdfRenderer` e `ImageDecoder`:** tiempo, peso y memoria a 768, 1.024 y 1.536 px, y la proyección de un PDF de 200 páginas (R6, R14).
- **En el escritorio:** el harness de F05 gana un corpus visual aparte, un conjunto de unas 100 preguntas con relevancia por página, y las ablaciones (R8–R11).
  - **Recuperación:** «Texto», «Visual» y «Ambos», con RRF de tres listas por página.
  - **Respuesta:** bloques de texto, páginas y mezcla, con la regla de duplicados y la plantilla v5-visual.
  - **Regla:** queda congelada en git antes de evaluar (R12).
- **Paridad:** un conjunto fijo de imágenes y páginas se embebe en los dos lados, con un umbral de coseno ≥ 0,99 (R13).
- **Salida:**
  - `benchmarks.md`;
  - `visual-eval-report.md`;
  - las decisiones en DECISIONS (parámetros, 440M y sí o no);
  - si sale el sí, la propuesta de enmienda de la constitución.

**Lo comprobado antes de planificar** (R1, R2):
- la API de visión de LiteRT-LM 0.18.1 está entera en Android y en Python. En Python, el presupuesto visual de Gemma se fija por `ctypes`, y se ha visto que cambia los tokens del prefill (101, 164 y 304);
- el fichero de Gemma admite como mucho 280 tokens por imagen, y la visión solo corre en CPU;
- en el escritorio, la 440M embebe una página en 0,16–0,36 s, y Gemma da el primer token con 1 página en 0,35–0,93 s.

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 de bytecode y JBR 21, sin cambios. Python 3.12 en `offline/`.

**Primary Dependencies**:
- **Android:** ninguna nueva. LiteRT-LM 0.18.1 ya trae la API de visión (R1). `PdfRenderer` e `ImageDecoder` son del sistema.
- **Python:** `pypdfium2==5.14.0` y `pillow==12.3.0` (R7). `litert-lm-api` se queda en 0.18.1.

**Storage**:
- **App:** ningún cambio en `rag.db` ni en `chat.db`. El diagnóstico escribe su informe en `filesDir/reports` y lee `files/dev-visual`.
- **Escritorio:**
  - `offline/corpus_visual/`, en git, con un máximo de unos 40 MB, fuera de los assets de las pruebas;
  - `offline/testset/visual/`;
  - `offline/config/visual_rules.json`;
  - `offline/build/visual/`, fuera de git.

**Testing**:
- **JVM:** la suite visual con fakes, el informe, el cargador del conjunto del dispositivo y `LayeringTest`.
- **Instrumentadas:**
  - `DebugPageRasterizerTest`, sin modelos;
  - `VisualFeasibilityBenchmarkTest` y `EmbeddingVariantGoldenTest`, con `@RequiresModels`.
- **Python:** 12 ficheros de prueba nuevos o ampliados (contrato del harness §6).
- **Teléfono:** al final, con permiso.

**Target Platform**:
- **App:** Android, minSdk 31 y targetSdk 37.
- **Emuladores:** `Pixel_10` y `MicroRAG_API33`, sin modelos.
- **Teléfono:** A063 (API 35), para todas las medidas.
- **Escritorio:** macOS en Apple Silicon, para la calidad.

**Project Type**: mobile-app, con el módulo único `:app` (D-009), más el harness de Python.

**Performance Goals** (solo se miden; F18 no optimiza):
- **440M:** consulta en CPU con p95 < 150 ms (SC-003).
- **Primer token con páginas:** p95 ≤ 8 s de extremo a extremo, con al menos 1 página (D-073).
- **Proyección de un PDF de 200 páginas:** se informa.

**Constraints**:
- **Privacidad:** sin red, y sin documentos del usuario (I).
- **Capas:** el SDK y la API de PDF, solo en sus paquetes (II, R3).
- **RAG:** los parámetros de la app no cambian (III).
- **GPU:** nunca dos engines a la vez. La visión, en CPU (IV).
- **Veracidad:** el conjunto del dispositivo se verifica por SHA-256 (V).
- **Diagnóstico:** solo en debug (VIII y flujo de calidad).
- **Regla:** congelada antes de evaluar (FR-025).

**Scale/Scope**:
- **Escritorio:** un corpus visual de 18 PDF y 15 imágenes, con ≥ 300 páginas; ≥ 100 preguntas; ablaciones de unas 2.000 respuestas; y unas 200 respuestas de revisión decisiva más las que cambien en F05.
- **Teléfono:** unas 75 combinaciones: 25 de la etapa A, 24 de conversión, 15 de decodificación y el resto de embeddings. La etapa B suma hasta 3 × 20 generaciones.
- **Código nuevo:**
  - en el teléfono: 1 pantalla de diagnóstico, 2 adaptadores de debug y 9 bloques;
  - en el escritorio: 12 módulos de Python.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F18 | Estado |
|---|---|---|
| I. On-device y privacidad | La app no hace ninguna llamada de red. Los modelos y el conjunto del dispositivo llegan por `adb`, verificados por SHA-256. Solo se usa el corpus público. El texto de prompts, respuestas y páginas va solo al informe privado, nunca a logcat. Las descargas del corpus son herramientas del escritorio, como en F05 | ✅ |
| II. Stack y arquitectura | El diagnóstico depende de interfaces (`VisionEngineFactory`, `PageRasterizer`). LiteRT-LM solo en `rag/runtime/litert` y `android.graphics.pdf` solo en `data/ingest`, ahora también en sus equivalentes de `src/debug/java` (R3). Versiones verificadas en el AAR, en el wheel, en las cabeceras C y en PyPI (R1, R7) | ✅ con la ampliación de `LayeringTest` (Complexity Tracking) |
| III. RAG según el informe | Ningún parámetro de la app cambia. Las ablaciones son la «evaluación documentada» que la constitución exige antes de cambiar nada. La enmienda (página citable, 440M y dimensión) solo se propone, y se aplica en F19 con `/speckit-constitution` (D-066) | ✅ |
| IV. Rendimiento y recursos | Engines por `EngineCoordinator`: la visión en CPU, y Gemma con el texto en GPU dentro del mismo engine (R2). Una configuración de engine por escenario. Colas dedicadas, cancelación y liberación al pasar a segundo plano, como F02. F18 no cambia el presupuesto de 5 s: si sale el sí, la propuesta de enmienda incluye un presupuesto propio para las respuestas con páginas (FR-029) | ✅ |
| V. Veracidad del estado | Nada se usa sin verificar su huella. Las combinaciones no admitidas o no disponibles se registran como tales. No hay cifras inventadas: las proyecciones se marcan como proyecciones | ✅ |
| VI. Calidad y pruebas | Golden de la 440M en el dispositivo, golden visual teléfono ↔ escritorio, pruebas JVM, instrumentadas y de Python. `benchmarks.md` con cifras medidas | ✅ |
| VII. UI fiel y accesible | Sin UI de producto. El diagnóstico usa componentes `Lisdata*` y textos en recursos de debug, como F02 | ✅ |
| VIII. Proceso | D-073 (clarify), D-074 (plan) y D-075 (analyze). Ciclo automático pedido por el usuario, con las paradas tras el clarify y tras el analyze (hecha: el usuario decidió la pérdida en texto, el juez y el primer token), en el teléfono y en el cierre. Las decisiones de parámetros y el sí o no, en DECISIONS | ✅ |

**Re-check post-diseño:** sin violaciones. La única excepción es la ampliación de `LayeringTest` (abajo), que mantiene el principio: el SDK sigue en sus paquetes, y el código del diagnóstico no llega a release.

## Project Structure

### Documentation (this feature)

```text
specs/018-visual-rag-feasibility/
├── spec.md
├── plan.md                         # este fichero
├── research.md                     # R1–R16
├── data-model.md                   # corpus, conjunto de prueba, índices, conjunto del dispositivo e informe
├── quickstart.md
├── contracts/
│   ├── visual-diagnostic.md        # interfaces, suite, conjunto del dispositivo, pantalla, pruebas y capas
│   └── visual-harness.md           # dependencias, módulos, subcomandos, reglas, informe y pruebas
├── checklists/requirements.md
├── benchmarks.md                   # cifras del teléfono (implement)
├── report.md                       # informe de cierre (implement)
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/debug/java/com/lisdatasolutions/microrag/
├── debug/feasibility/visual/                       # nuevo
│   ├── VisualContracts.kt                          # VisionGenerator, ImageEmbedder, VisionEngineFactory, PageRasterizer, VisionModelInfo
│   ├── VisualSuite.kt                              # bloques GEMMA_INFO … PROJECTION
│   ├── VisualProtocol.kt                           # matriz, repeticiones, resoluciones y calidades
│   ├── DeviceSetLoader.kt                          # dev-visual + manifest.json (SHA-256)
│   ├── report/{VisualReport,VisualReportWriter}.kt
│   ├── VisualFeasibilityViewModel.kt, VisualFeasibilityUiState.kt, VisualFeasibilityScreen.kt
│   └── VisualFeasibilityModule.kt                  # Koin
├── debug/DebugEntries.kt, debug/DebugModules.kt     # entrada «Viabilidad visual» y módulo
├── rag/runtime/litert/LiteRtVisionEngines.kt       # nuevo: Gemma con visión, embedder de imágenes y ModelInfo
└── data/ingest/DebugPageRasterizer.kt              # nuevo: PdfRenderer + ImageDecoder
app/src/debug/res/values/strings_feasibility.xml     # textos del diagnóstico visual
app/src/testDebug/java/…/debug/feasibility/visual/   # VisualSuiteTest, VisualReportTest, DeviceSetLoaderTest, VisualFeasibilityViewModelTest
app/src/test/java/…/architecture/LayeringTest.kt     # reglas de debug (R3)
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── debug/feasibility/visual/VisualFeasibilityBenchmarkTest.kt
├── data/ingest/DebugPageRasterizerTest.kt
└── rag/runtime/EmbeddingVariantGoldenTest.kt

offline/
├── requirements.txt                                # + pypdfium2, pillow
├── run_eval.py                                     # subcomandos visual-*
├── config/visual_rules.json                        # congelado antes de evaluar
├── corpus_visual/{manifest.json, SOURCES.md, docs/}          # fuera de offline/corpus (assets de las pruebas)
├── testset/visual/{questions.jsonl, review.csv}
├── eval/{vision_runtime,raster,photos,visual_corpus,visual_testset,visual_index,visual_retrieval,
│         visual_prompt,visual_judge,visual_parity,visual_rules,visual_report}.py
├── eval/{corpus,models,builds}.py                  # registro del corpus visual y de la 440M
├── reports/{visual-eval-report.md, visual-manual-judgement.csv, visual-text-loss.csv}
└── tests/test_{raster,photos,visual_testset,visual_index,visual_retrieval,visual_prompt,
                visual_judge,visual_parity,vision_runtime,visual_rules}.py, test_appconfig.py

docs/roadmap/{DECISIONS.md, FEATURES.md}             # decisiones de parámetros, 440M, sí o no y estado
```

**Structure Decision:**
- **Teléfono:** el código del diagnóstico vive en `src/debug`, como el de F02. Los adaptadores van en los paquetes que la constitución II reserva al SDK y a la API de PDF (R3).
- **Escritorio:** el harness crece con módulos `visual_*` que reutilizan los de F05 sin cambiar su comportamiento (FR-024).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `LayeringTest` admite LiteRT-LM y `android.graphics.pdf` también en `src/debug/java/…/rag/runtime/litert` y `…/data/ingest` | Los adaptadores de visión y el rasterizador solo los usa el diagnóstico, que no puede llegar a release (FR-001, FR-031) | Ponerlos en `main` los llevaría a release sin uso. Una excepción por fichero es más frágil que la regla por paquete |
| La 440M se evalúa contra un golden generado con la 270M, sin comprobar su huella | Es justo lo que se quiere saber: si sus vectores de texto valen para el índice actual (R5) | Regenerar el golden con la 440M perdería esa comparación |
