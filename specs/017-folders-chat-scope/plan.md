# Implementation Plan: Carpetas y ámbito del chat (F17)

**Branch**: `017-folders-chat-scope` | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-folders-chat-scope/spec.md`

## Summary

F17 añade carpetas de un nivel a la Biblioteca y un ámbito por conversación al chat (D-066, D-070).

- **Datos:**
  - `rag.db` v4: tabla `folders` con ids que no se reutilizan, y `documents.folder_id`. Los documentos actuales quedan en «Sin carpeta» (R1);
  - `chat.db` v2: `conversation_scope` y `last_scope`, con la guarda de ids reutilizados de D-065 (R7).
- **Biblioteca:**
  - la raíz muestra las carpetas con sus recuentos y busca en todas;
  - cada carpeta reutiliza la lista de F08 con un filtro de carpeta (R5);
  - crear, renombrar y eliminar, con la opción de mover los documentos a «Sin carpeta» o borrarlos por el camino de F08 (R4).
- **Mover e importar:** «Mover a carpeta» en el detalle y destino en la Ingesta, con una hoja común (R6).
- **Chat:**
  - ficha «Buscar en: …» y selector de carpetas y documentos sueltos, con los excluidos en gris (R8);
  - el ámbito va con cada conversación, y «Nuevo chat» usa el último;
  - lo que deja de existir sale del ámbito con aviso.
- **Recuperación:**
  - con «Toda la biblioteca», exactamente lo de hoy;
  - con un ámbito, el **modo en streaming** de `vector_full_scan` y una consulta FTS con `JOIN`/`WHERE` y `ORDER BY … LIMIT k`, que es exacto sin inflar los candidatos (R3). Se verifica antes de construir encima (puerta T009) y se mide (R11).
- **Design system:** `LisdataTextField`, `LisdataCheckRow` (tres estados), `LisdataExpandableRow` y `LisdataScopeChip` (R9).
- **Pruebas en el teléfono, en la última fase.**

## Technical Context

**Language/Version**: Kotlin 2.4.21, con Java 17 como bytecode target y JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva (R14).
- SQLite-Vector se queda en 1.1.2, porque el modo en streaming ya está;
- `TriStateCheckbox` está en Material 3 del BOM 2026.09.00.

**Storage**:
- `rag.db` v4 (`folders`, `documents.folder_id` y un índice);
- `chat.db` v2 (`conversation_scope` y `last_scope`).

**Testing**:
- **JVM:** dominio, datos, ViewModels y pantallas con Robolectric; `NavigationTest`, `StringsParityTest`, `LayeringTest`, `AppModulesTest` y `DesignSystemConformanceTest`.
- **Instrumentadas:** migraciones de las dos bases, carpetas, `ScopedSearchTest` (puerta R3), visibilidad con ámbito, ATF y `RetrievalBenchmarkTest` con ámbitos.
- **Python:** las pruebas del harness, con la versión 4.
- **Teléfono:** batería, medidas y recorrido, al final.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Emuladores `MicroRAG_API33` y `Pixel_10`, y teléfono A063 (API 35) al final.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- recuperación acotada con p95 ≤ 200 ms en el teléfono, o anotado para F14 (FR-026, SC-004);
- carpetas en menos de 1 s con 1.000 documentos (SC-007);
- con «Toda la biblioteca», ninguna regresión (el mismo SQL).

**Constraints**:
- **Sin red;** los registros no llevan nombres (I).
- **Capas:** `feature/*` no importa `data`, SQLite ni `File` (II).
- **Recuperación:** solo el filtro; fusión, candidatos, contexto y prompt iguales (III).
- **Consultas** agregadas y paginadas fuera del hilo principal (IV).
- **Borrado** con confirmación y limpieza coherente; un excluido nunca se busca (V).
- **UI:** el wireframe aprobado (VII).

**Scale/Scope**:
- 2 pantallas (raíz y carpeta), 2 diálogos, 2 hojas, 1 ficha y 1 estado vacío nuevo del chat;
- 4 piezas nuevas del design system;
- 2 migraciones;
- unos 12 casos de uso;
- unos 40 textos nuevos en español e inglés.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F17 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos. Carpetas y ámbitos viven en `rag.db` y `chat.db`, en almacenamiento privado sin copia de seguridad. Los registros llevan solo ids, recuentos y tiempos (R12). En el teléfono solo se usan carpetas y documentos de prueba | ✅ |
| II. Stack y arquitectura | `Screen → ViewModel → casos de uso → RagIndex / LibraryRepository / ChatRepository / ChatSession`. `LayeringTest` cubre `feature/folders` y `feature/chat/scope`. El modo en streaming de SQLite-Vector se verifica con una prueba antes de usarlo (R3, T009). Sin dependencias nuevas | ✅ |
| III. RAG según el informe | El ámbito solo filtra qué fragmentos son elegibles. RRF (k = 60), 50 + 50 candidatos, `ContextSelector` y prompt no cambian. Con «Toda la biblioteca», el SQL es el de hoy (SC-003, prueba de paridad). La búsqueda acotada es exacta (FR-018) | ✅ |
| IV. Rendimiento | Recuentos agregados y listas por tandas. Todo el acceso a base de datos en el dispatcher de SQLite. La búsqueda acotada se mide en el teléfono (R11) | ✅ |
| V. Veracidad del estado | Eliminar una carpeta pide confirmación, con un segundo aviso si borra documentos, y usa la limpieza de F08. Mover no toca el índice. Lo que deja de existir sale del ámbito con aviso. Los excluidos se ven, pero no se buscan | ✅ |
| VI. Calidad y pruebas | Migraciones instrumentadas en las dos bases, la puerta de R3, visibilidad, ViewModels con fakes y ATF | ✅ |
| VII. UI fiel y accesible | Wireframe K1–K14 aprobado (D-070). Componentes `Lisdata*` y tokens, con cuatro piezas nuevas con vistas previas y catálogo. U1–U6. Textos ES/EN con paridad | ✅ |
| VIII. Proceso | D-070 (clarify) y D-071 (plan). Ciclo automático pedido por el usuario. La parada tras analyze se hace si el analyze deja decisiones. Teléfono al final, con el recorrido acordado antes | ✅ |

**Re-check post-diseño:** sin violaciones. El modo en streaming es el mismo escaneo exacto de F04, y la puerta T009 lo comprueba antes de depender de él.

## Project Structure

### Documentation (this feature)

```text
specs/017-folders-chat-scope/
├── spec.md
├── wireframe.md                    # aprobado (D-070)
├── plan.md                         # este fichero
├── research.md                     # R1–R14
├── data-model.md                   # rag.db v4, chat.db v2, dominio y estado de pantallas
├── quickstart.md
├── contracts/
│   ├── folders-scope-api.md        # índice F1–F8, casos de uso, sesión S1–S5, inyección y cambios
│   └── folders-scope-ui.md         # Biblioteca, detalle, Ingesta, chat, textos y U1–U6
├── checklists/requirements.md
└── tasks.md                        # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/assets/{schema.sql, chat_schema.sql}                  # v4 y v2
app/src/main/java/com/lisdatasolutions/microrag/
├── core/designsystem/component/{Inputs,Rows,Chips,…}.kt           # LisdataTextField, LisdataCheckRow, LisdataExpandableRow, LisdataScopeChip
├── core/navigation/{Destinations,MicroRagNavHost}.kt             # LibraryFolder, Ingest(folderId)
├── data/rag/{DefaultRagIndex,RagSchema}.kt                       # F1–F8 y la migración 3 → 4
├── data/chat/{ChatSchema,SqliteChatRepository,DefaultChatSession}.kt  # v2 y S1–S4
├── data/library/DefaultLibraryRepository.kt
├── data/ingest/{DocumentImporter,DefaultImportQueueRepository}.kt
├── data/retrieval/HybridRetriever.kt
├── domain/library/{FolderModels,FolderNames,FolderUseCases}.kt   # nuevos
├── domain/chat/{ChatScope,ScopeUseCases}.kt                      # nuevos
├── domain/retrieval/{RetrievalModels,RetrieveContextUseCase}.kt
├── feature/folders/FolderChoiceSheet.kt                          # nuevo
├── feature/library/{LibraryScreen,LibraryViewModel,FoldersSection,FoldersViewModel,FolderDialogs}.kt
├── feature/document/{DocumentScreen,DocumentViewModel}.kt
├── feature/ingest/{IngestScreen,IngestViewModel}.kt
└── feature/chat/{ChatScreen,ChatViewModel}.kt, feature/chat/scope/{ScopeSheet,ScopeSelectorViewModel}.kt
app/src/test/…, app/src/androidTest/…                             # pruebas nuevas y ampliadas
offline/tests/test_appconfig.py                                   # schema_version 4
```

**Structure Decision**: módulo único `:app` con los paquetes por capa de D-009. La hoja de carpetas va en `feature/folders` porque la comparten el detalle y la Ingesta, y el selector del ámbito en `feature/chat/scope`, con su propio ViewModel, para no cargar más el del chat.

## Complexity Tracking

Sin violaciones que justificar.
