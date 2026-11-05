# Implementation Plan: Chat con páginas, modo «Multimodal» opcional (F20)

**Branch**: `020-visual-chat` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-visual-chat/spec.md`

## Summary

F20 completa el modo «Multimodal» (D-078): con el modo activo, el chat responde también con páginas. Con el modo apagado, o sin páginas en el ámbito, nada cambia.

- **Recuperación** (R1–R3):
  - los bloques de texto, como hoy (D-034);
  - además, una lista de páginas con el mismo vector de la consulta, filtrada por ámbito y por buscables, sin RRF (D-076).
- **Contexto y prompt** (R4):
  - los bloques sin tocar más la primera página, siempre que haya alguna en el ámbito (D-083);
  - la plantilla v5-visual de F18, portada a `PromptBuilder.buildVisual`, con una golden del escritorio.
- **Motor** (R5–R7):
  - con el modo activo, Gemma se carga con la visión en CPU, una ventana de 8.192 tokens y 1 imagen;
  - el generador de F18 pasa de debug a `LiteRtTextGenerator`;
  - la imagen es el JPEG de F19.
- **Citas a página** (R8–R10):
  - fuentes con tipo;
  - `chat.db` v3;
  - `PAGE_GONE` en el Historial.
- **UI** (R12–R16), según el wireframe aprobado:
  - «Detalle de las páginas» con Equilibrado por defecto;
  - «Leyendo 1 página · unos 11,5 s…»;
  - miniaturas en «Fuentes consultadas»;
  - la página en el detalle y el visor con zoom;
  - las notas de F19 al día.
- **Debug** (R17): el diagnóstico de búsqueda con las tres listas.
- **Sin pruebas comparativas** (D-078). El primer token con cada detalle se mide en el teléfono (R19).

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 como bytecode target y JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva.
- LiteRT-LM 0.18.1 ya tiene `EngineConfig.visionBackend`, `maxNumImages`, `Contents.of`, `Content.ImageBytes` y `ExperimentalFlags.visualTokenBudget` (verificado en el diagnóstico de F18);
- el zoom se hace con `detectTransformGestures` y `graphicsLayer` de Compose, sin librería.

**Storage**:
- `chat.db` v3: `sources.kind` y `turns.page_tokens`;
- `multimodal.preferences_pb` + `page_detail`;
- `rag.db` sigue en la v5 y solo se lee.

**Testing**:
- **JVM:** la golden v5-visual, la sesión de chat, el retriever, la disponibilidad, las fuentes, `ZoomState`, los ViewModels y las pantallas con Robolectric, más las pruebas de capas, textos y navegación.
- **Instrumentadas:**
  - la migración 2 → 3, `pageSearch`, el repositorio del chat y el coordinador;
  - ATF;
  - Gemma con visión (`@RequiresModels`).
- **Python:** la golden v5-visual.
- **Teléfono:** al final y con permiso.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Emuladores `Pixel_10` y `MicroRAG_API33`, y teléfono A063 (API 35) al final.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals** (informativas, D-078):
- primer token con 1 página en el A063 de unos 10, 11,5 y 14,5 s según el detalle (F18);
- `pageSearch` en torno a los 53 ms de F18;
- con el modo apagado, ninguna regresión.

**Constraints**:
- **Red:** ninguna (I).
- **Capas:** el SDK solo en `rag/runtime/litert`, y la UI lee las imágenes a través de `PageImageRepository` (II).
- **Texto sin cambios:** la búsqueda, el selector, la v4 y el límite de 2.000 tokens no cambian. La página se suma (III).
- **Recursos:** nunca dos engines en GPU; la visión en CPU y liberada con Gemma (IV).
- **Estado verdadero:** citas solo a páginas reales, y las que ya no están lo dicen (V).
- **UI:** el wireframe aprobado (VII).

**Scale/Scope**:
- 2 pantallas nuevas («Detalle de las páginas» y el visor) y cambios en Ajustes, el chat, el detalle y el diagnóstico de debug;
- 1 migración de `chat.db` y 1 clave nueva del almacén;
- unos 6 casos de uso o reglas tocados;
- 1 adaptador llevado de debug a main;
- unos 25 textos nuevos en español e inglés.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Constitución **1.2.0**, sin enmienda (D-083).

| Principio | Cómo lo cumple F20 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red. Las imágenes se leen de `noBackupFilesDir/pages/`. Los registros solo llevan cifras: páginas, tokens, tiempos y si faltó la imagen (R18). Nada de prompts, respuestas, imágenes, nombres ni rutas en release (FR-021) | ✅ |
| II. Stack y arquitectura | `Screen → ViewModel → casos de uso → RagIndex / ChatRepository / MultimodalStore / PageImageRepository / AnswerEngine`. El SDK, solo en `LiteRtTextGenerator`, y `LayeringTest` lo vigila. Sin dependencias nuevas | ✅ |
| III. RAG según el informe (1.2.0) | La búsqueda de texto, la RRF, el selector de D-034 y la v4 no cambian. La página sale de su propia lista (D-076) y entra solo si el espacio de páginas coincide. Las citas son a un `chunkId` o a una página real, comprobadas con `existingPages`. Los parámetros nuevos (1 página, 8.192 tokens y la plantilla) vienen de D-078 y F18 | ✅ |
| IV. Rendimiento (1.2.0) | La visión en CPU, una sola clave de Gemma por modo y nunca dos en GPU. El codificador se libera con Gemma. El primer token con páginas se mide con cada detalle y se avisa en Ajustes y en el chat | ✅ |
| V. Veracidad del estado | «Leyendo 1 página» con la espera medida. `PAGE_GONE` y `DOCUMENT_GONE` dicen lo que pasa. Una imagen que falta no se inventa: la respuesta sigue sin ella | ✅ |
| VI. Calidad y pruebas | La golden v5-visual, la migración instrumentada, `pageSearch` con ámbito, el coordinador, los ViewModels con fakes y ATF (R21) | ✅ |
| VII. UI fiel y accesible | Wireframe G47 aprobado (D-083). Una pieza nueva (el visor) y un hueco nuevo en `LisdataSourceCitation`, los dos en el wireframe. TalkBack con documento y página, acciones de zoom, letra al 200 % y textos ES/EN con paridad | ✅ |
| VIII. Proceso | D-083 (clarify) y D-084 (plan). Ciclo automático; el teléfono, al final y con permiso | ✅ |

**Re-check post-diseño:** sin violaciones.
- El hueco `leading` de `LisdataSourceCitation` mantiene el sistema de componentes único.
- `chunkId = 0` para las páginas va siempre con su `kind`, así que una cita nunca apunta a un fragmento inexistente.

## Project Structure

### Documentation (this feature)

```text
specs/020-visual-chat/
├── spec.md
├── wireframe.md                    # aprobado (D-083)
├── plan.md                         # este fichero
├── research.md                     # R1–R21
├── data-model.md                   # chat.db v3, page_detail y tipos
├── quickstart.md
├── contracts/
│   ├── chat-pages-api.md           # índice P1–P4, recuperación Q1–Q5, prompt B1–B3, sesión S1–S8,
│   │                               # motor G1–G6, fuentes H1–H4, modo M1–M3 e imágenes I1–I2
│   └── chat-pages-ui.md            # Ajustes, chat, detalle, visor y accesibilidad (U1–U18)
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/assets/chat_schema.sql                                # v3
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/rag/{RagIndex,RagModels,RagParameters}.kt               # pageSearch, existingPages, pageFile, ScoredPage
├── domain/retrieval/RetrievalModels.kt                            # withPages, pages, lists, pagesMs
├── domain/chat/{ChatModels,ChatUseCases}.kt                       # SourceKind, PAGE_GONE, AnswerPrompt, disponibilidad
├── domain/multimodal/{MultimodalModels,MultimodalUseCases}.kt     # PageDetail, SetPageDetailUseCase
├── domain/library/PageImageRepository.kt                          # nuevo
├── data/rag/DefaultRagIndex.kt                                    # SQL de pages
├── data/retrieval/HybridRetriever.kt                              # lista de páginas
├── data/chat/{DefaultChatSession,EngineAnswerEngine,ChatSchema,SqliteChatRepository}.kt
├── data/multimodal/DataStoreMultimodalStore.kt                    # page_detail
├── data/library/DefaultPageImageRepository.kt                     # nuevo
├── rag/answer/{PromptBuilder,AnswerReading}.kt                    # buildVisual
├── rag/runtime/{TextGenerator,ManagedEngine}.kt                   # GenerationPrompt, VISION_GENERATOR
├── rag/runtime/litert/LiteRtTextGenerator.kt                      # visión, desde el diagnóstico de F18
├── core/designsystem/component/Rows.kt                            # LisdataSourceCitation.leading
├── core/navigation/{Destinations,MicroRagNavHost}.kt              # Document.page, PageViewer, PageDetailRoute
├── feature/settings/{MultimodalSection,PageDetailScreen,PageDetailViewModel}.kt
├── feature/chat/{ChatScreen,ChatViewModel,ChatUiState,PageThumbnail}.kt
└── feature/document/{DocumentScreen,DocumentViewModel,DocumentUiState,PageImageLoader,PageViewerScreen,ZoomState}.kt
app/src/main/res/values{,-en}/strings_visual_chat.xml
app/src/debug/…/rag/runtime/litert/LiteRtVisionEngines.kt          # sin el generador; VisualSuite usa el de main
app/src/debug/…/debug/retrieval/                                   # las tres listas
app/src/debug/…/debug/DebugRouting.kt                              # EXTRA_PAGE
app/src/test/…, app/src/androidTest/…                              # R21
offline/gen_visual_prompt_golden.py, offline/golden/visual_prompt_golden.jsonl
offline/tests/test_visual_prompt_golden.py
```

**Structure Decision**: módulo único `:app` con los paquetes por capa de D-009.
- El visor y el cargador de imágenes van en `feature/document`, y el chat los reutiliza, como la hoja de carpetas de F17.
- «Detalle de las páginas» va en `feature/settings`, junto a «Modo al importar PDF».

## Complexity Tracking

Sin violaciones que justificar.
