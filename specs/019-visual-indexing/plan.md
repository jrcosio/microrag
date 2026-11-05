# Implementation Plan: Indexación visual, modo «Multimodal» opcional (F19)

**Branch**: `019-visual-indexing` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-visual-indexing/spec.md`

## Summary

F19 añade el modo «Multimodal», opcional y desactivado por defecto (D-078, D-079). Activarlo deja la app lista para indexar páginas e imágenes, y F20 las usará en el chat.

- **Activación** (R1, R3–R6): una máquina de estados persistida, `OFF → DOWNLOADING → VALIDATING → SWITCHING → ON`:
  - descarga la 440M con el camino de F03;
  - comprueba en el dispositivo que sus vectores de texto son los de la 270M (≥ 0,9999);
  - cambia `rag_meta.embedding_model_sha256` en una transacción;
  - borra la 270M.

  Desactivarlo conserva todo, y reactivarlo es inmediato.
- **Embedder activo** (R2, R5): lo dice el índice. Indexación, consulta, Ajustes y la lista de modelos obligatorios lo leen de ahí. El primer arranque no cambia.
- **Datos** (R8): `rag.db` v5, con `documents.index_mode`, la tabla `pages` (vector de 256D con su propio `vector_init`) y las claves `page_*` de `rag_meta`.
- **Ingesta** (R9–R11):
  - Texto, Visual o Ambos por PDF, con Ambos por defecto, y JPG y PNG siempre en Visual;
  - una fase de páginas en el mismo run: 768 px en JPEG 85 y la 440M a 140 tokens en CPU, reanudable por página y con las pausas de F07;
  - reindexar en otro modo con la copia en sombra, y limpieza de los ficheros de página.
- **Adaptadores** (R7): el embedder de imagen y el conversor de F18 pasan de debug a main.
- **Pantallas** (R12): según el wireframe aprobado; sin piezas nuevas del design system.
- **Sin pruebas comparativas** (D-078). Las cifras del teléfono son informativas.

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 como bytecode target y JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva.
- LiteRT-LM 0.18.1 ya trae `InputData.Image` y `visionTokensPerImage` (F18);
- `PdfRenderer` e `ImageDecoder` están en API 31.

**Storage**:
- `rag.db` v5: `documents.index_mode`, `pages` y las claves `page_*`;
- `multimodal.preferences_pb`;
- `noBackupFilesDir/pages/`, para las imágenes de página.

**Testing**:
- **JVM:** el controlador, los requisitos, el comprobador, el importador, el runner con páginas, los ViewModels y las pantallas con Robolectric, y las pruebas de capas, textos, módulos y navegación.
- **Instrumentadas:** la migración 4 → 5, el cambio de embedder, las páginas y su limpieza, el conversor, el embedder de imagen (`@RequiresModels`) y ATF.
- **Python:** `test_appconfig.py`, con la versión 5.
- **Teléfono:** al final y con permiso.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Emuladores `Pixel_10` y `MicroRAG_API33`, y teléfono A063 (API 35) al final.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals** (informativas, D-078):
- unos 3,2 s por página en CPU en el A063 (F18);
- la activación, en minutos, según la red: descarga, unos 30–40 s de validación y el cambio en menos de 1 s;
- con el modo apagado, ninguna regresión.

**Constraints**:
- **Red:** solo la descarga de la 440M, desde el origen fijado y con la huella verificada (I).
- **Capas:** el SDK solo en `rag/runtime/litert`, y `PdfRenderer` e `ImageDecoder` solo en `data/ingest` (II).
- **Texto sin cambios:** con el modo apagado, o con el modo encendido y solo texto, la búsqueda es la de hoy (III).
- **Recursos:** nunca dos engines en GPU; la visión en CPU; el LLM liberado al indexar (IV).
- **Estado verdadero:** lo que no se usa en el chat lo dice (V).
- **UI:** el wireframe aprobado (VII).

**Scale/Scope**:
- 1 pantalla nueva («Modo al importar PDF»), 2 diálogos, 1 hoja y cambios en Ajustes, la Ingesta, la Biblioteca y el detalle;
- 1 migración y 1 almacén nuevo;
- unos 10 casos de uso;
- 2 adaptadores llevados de debug a main;
- unos 35 textos nuevos en español e inglés.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Constitución **1.2.0** (D-079).

| Principio | Cómo lo cumple F19 | Estado |
|---|---|---|
| I. On-device y privacidad | La única red es la descarga de la 440M, con el sistema de F03 (origen fijado y SHA-256). Las imágenes de página van en `noBackupFilesDir`. Los registros llevan solo ids, recuentos, tiempos y la cifra de identidad (FR-026) | ✅ |
| II. Stack y arquitectura | `Screen → ViewModel → casos de uso → RagIndex / MultimodalStore / ModelFileRepository / IngestRunner`. Los adaptadores van a `rag/runtime/litert` y `data/ingest`, y `LayeringTest` los vigila. Sin dependencias nuevas | ✅ |
| III. RAG según el informe (1.2.0) | El espacio de páginas va en `rag_meta`. La 440M solo sustituye a la 270M tras la validación de identidad (≥ 0,9999), con `embedding_model_sha256` cambiado en una transacción (R3, R4). Las páginas van a 256D con L2. El SQL de texto, la RRF, el contexto y el prompt no cambian | ✅ |
| IV. Rendimiento (1.2.0) | La visión en CPU y nunca dos engines en GPU. El LLM se libera al indexar. El ritmo de páginas y el espacio se miden e informan. El primer token con páginas es de F20 | ✅ |
| V. Veracidad del estado | La activación muestra su etapa real, y un fallo lo explica. Un documento solo Visual dice que todavía no se usa en el chat. Borrar, quitar y reindexar limpian páginas y ficheros (R11) | ✅ |
| VI. Calidad y pruebas | Migración instrumentada, el cambio de embedder con cortes, la reanudación por página, ViewModels con fakes y ATF (R16) | ✅ |
| VII. UI fiel y accesible | Wireframe G47 aprobado (D-079), solo con piezas `Lisdata*`. TalkBack, letra al 200 % y textos ES/EN con paridad | ✅ |
| VIII. Proceso | D-078 (el modo), D-079 (clarify y enmienda 1.2.0) y D-080 (plan). Ciclo automático; el teléfono, al final y con permiso. Borrar la 270M del teléfono se pregunta antes | ✅ |

**Re-check post-diseño:** sin violaciones. El conjunto de embedders aceptados (R2) es la regla de la constitución 1.2.0, no una excepción. Un índice de la 270M solo se abre con la 440M tras la validación, y `rag_meta` siempre dice cuál es.

## Project Structure

### Documentation (this feature)

```text
specs/019-visual-indexing/
├── spec.md
├── wireframe.md                    # aprobado (D-079)
├── constitution-amendment.md       # aplicada: 1.2.0
├── plan.md                         # este fichero
├── research.md                     # R1–R16
├── data-model.md                   # rag.db v5, ficheros, preferencias y dominio
├── quickstart.md
├── contracts/
│   ├── multimodal-api.md           # índice E1–E3 y P1–P9, modo M1–M6, modelos D1 e ingesta I1–I7
│   └── multimodal-ui.md            # Ajustes, Ingesta, Biblioteca, detalle y textos (U1–U14)
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/assets/schema.sql                                    # v5
app/src/main/assets/embedding/identity_texts.jsonl                # muestra de R3
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/multimodal/{MultimodalModels,MultimodalUseCases,ModelRequirements}.kt   # nuevos
├── domain/rag/{RagIndexConfig,IndexCompatibilityChecker,RagIndex,RagModels}.kt    # E1–E3, P1–P9, IndexMode
├── domain/ingest/IngestModels.kt                                 # Stage.PAGES, formatos y errores
├── domain/model/ModelCatalog.kt                                  # allReady con ModelRequirements
├── domain/onboarding/{ModelSetupUseCases,ModelDownloadUseCases}.kt  # listas de modelos
├── data/multimodal/{DataStoreMultimodalStore,DefaultMultimodalController,IdentityValidator}.kt  # nuevos
├── data/rag/{DefaultRagIndex,RagSchema,RagConnection}.kt         # v5, pages, switchTextEmbedder
├── data/models/DefaultModelFileRepository.kt                     # delete(id)
├── data/download/{ModelDownloadRunner,DefaultModelDownloadController}.kt
├── data/indexing/{ChunkEmbeddingSession,PageEmbeddingSession}.kt # embedder activo; sesión de páginas
├── data/ingest/{IngestRunner,DocumentImporter,AndroidPageRasterizer,PageFileStore}.kt
├── data/retrieval/EngineQueryEmbedder.kt                         # embedder activo
├── data/library/DefaultLibraryRepository.kt                      # limpieza de ficheros de página
├── rag/runtime/ImageEmbedder.kt, rag/ingest/PageRasterizer.kt    # interfaces (desde debug)
├── rag/runtime/litert/LiteRtImageEmbedder.kt                     # desde debug
├── core/navigation/{Destinations,MicroRagNavHost}.kt             # ImportModeRoute
├── feature/settings/{SettingsScreen,SettingsViewModel,MultimodalSection,ImportModeScreen,ImportModeViewModel}.kt
├── feature/ingest/{IngestScreen,IngestViewModel,IndexModeSheet}.kt
├── feature/library/LibraryScreen.kt
└── feature/document/{DocumentScreen,DocumentViewModel}.kt
app/src/main/res/values{,-en}/strings_multimodal.xml
app/src/debug/…/debug/feasibility/visual/VisualContracts.kt       # usa las interfaces de main
app/src/test/…, app/src/androidTest/…                             # R16
offline/tests/test_appconfig.py                                   # versión 5 y los dos embedders
```

**Structure Decision**: módulo único `:app` con los paquetes por capa de D-009. El modo vive en `domain/multimodal` y `data/multimodal`. La hoja de modo va en `feature/ingest` y la reutiliza el detalle, como la de carpetas de F17.

## Complexity Tracking

Sin violaciones que justificar.
