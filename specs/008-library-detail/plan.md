# Implementation Plan: Biblioteca y detalle de documento (F08)

**Branch**: `008-library-detail` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-library-detail/spec.md`

## Summary

F08 entrega la pestaña Biblioteca (PNG 06) y el Detalle del documento (PNG 07). El usuario encuentra cualquier documento importado, decide cuáles usa el asistente y lo mantiene: reindexar, quitar del índice o eliminar, sin dejar nunca el índice a medias.

- **Biblioteca:**
  - lista paginada por bloques de 50, del más reciente al más antiguo;
  - búsqueda por nombre sin mayúsculas ni acentos, sobre una columna plegada;
  - filtros Todos, Indexados y Pendientes (G16);
  - interruptor de inclusión;
  - estados B1–B8.

  Nunca lee la tabla de fragmentos.
- **Detalle:** cifras reales, inclusión, y vista previa del primer fragmento o del fragmento citado, resaltado y con las frases vecinas (D-043). Acciones y estados D1–D11.
- **Reindexar sin huecos:** un documento sombra se indexa con la tanda de F07 y, al terminar, sustituye los fragmentos del original en una sola transacción. Mientras tanto, las respuestas usan la versión anterior (R5).
- **Quitar del índice:** estado «Fuera del índice», guardado como el `cancelled` que el esquema ya reservaba (R4). Ni la cola de Ingesta ni las tandas lo tocan.
- **Eliminar:** un solo camino de borrado para la Biblioteca y la Ingesta. Atómico en el índice, marca `quant_dirty` y borra la copia privada; si falla, la limpieza de huérfanos la recoge (R6).
- **Esquema v3:** `name_norm` y `replaces_id`, añadidas con `ALTER TABLE` (verificado) y una migración desde la v2 (R1).

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios). Python 3.12 en `offline/` (solo la versión del esquema).

**Primary Dependencies**: ninguna nueva (comprobado en `gradle/libs.versions.toml`). Se usan:
- Compose BOM 2026.09.00 y Navigation 2.10.2 (`SavedStateHandle`, rutas tipadas);
- `androidx.sqlite:sqlite-bundled` 2.7.1 y `ai.sqlite:vector` 1.1.2;
- `datastore-preferences` 1.2.1 (la tanda de F07);
- `work-runtime-ktx` 2.12.0;
- Koin 4.2.2;
- de la plataforma, `java.text.Normalizer` y `java.text.BreakIterator`.

Paging 3 se descartó (R2).

**Storage**: `noBackupFilesDir/index/rag.db`, **esquema v3** (R1), con migración desde la v2. Copias privadas en `noBackupFilesDir/documents/` (F06).

**Testing**:
- **JVM:**
  - `DocumentNames`;
  - repositorio de la Biblioteca con `FakeRagIndex` y la tanda en memoria;
  - ViewModels y pantallas (Robolectric);
  - runner con sombras;
  - cola de Ingesta sin sombras;
  - `RagSchema` (migración `from = 2`);
  - `LayeringTest`.
- **Instrumentadas** (emulador API 37, AVD API 33 y teléfono):
  - migración v2 → v3 sobre SQLite real;
  - consultas de la Biblioteca;
  - reindexado con búsquedas durante el proceso;
  - modo estricto;
  - accesibilidad de todos los estados;
  - mediciones (R11).

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia A063 (API 35). Emulador `Pixel_10` (API 37) y AVD API 33.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- **SC-001:** primera página en 1 s o menos con 1.000 documentos.
- **SC-009:** borrar 4.300 fragmentos sin bloquear la UI; se mide y se informa si pasa de 5 s.
- **Búsquedas durante un reindexado:** cuestan lo mismo que con un documento a medias de F07 (*m* = fragmentos de la sombra; D-042).

**Constraints**:
- Sin red (I).
- Índice en `AppDispatchers.database` y archivos en `io` (II, IV).
- El índice nunca muestra una versión a medias ni mezcla dos (III, V).
- Nunca una página ni una cifra inventada (V).
- Logs sin nombres, rutas ni texto (I).
- La UI no importa SQLite, WorkManager ni `java.io.File` (II).

**Scale/Scope**:
- 2 pantallas nuevas (Biblioteca y Detalle).
- 1 repositorio nuevo y 8 casos de uso.
- 8 operaciones nuevas o ampliadas de `RagIndex`.
- 1 migración de esquema.
- Cambios puntuales en el runner y la cola de Ingesta de F06 y F07.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F08 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red. Datos en `noBackupFilesDir`. Eliminar borra la copia privada (y, si falla, lo recoge la limpieza de huérfanos). Logs con ids, recuentos, duraciones y códigos (L11) | ✅ |
| II. Stack y arquitectura | Screen → ViewModel → casos de uso → `LibraryRepository` → `data/library` → `RagIndex` y la tanda. Sin dependencias nuevas. `LayeringTest` cubre los paquetes nuevos | ✅ |
| III. RAG según el informe | Borrado en cascada con el FTS por trigger (informe, Esquema SQLite). Reindexado que sustituye el índice de forma segura (FEATURES F08). `quant_dirty` en cada borrado (D-030). La sombra se indexa con la misma tanda, prefijos y cabecera de F07 | ✅ |
| IV. Rendimiento | Lista paginada sin fragmentos (L1), consultas e índice ordenado (R1, R2). Borrados en el dispatcher de la base de datos. Mediciones en el teléfono (R11) | ✅ |
| V. Veracidad del estado | Cifras del índice; «—» para lo que no existe. El chip de página solo si hay página. Ninguna mezcla de versiones al reindexar (R5). Previews con datos de ejemplo etiquetados | ✅ |
| VI. Calidad y pruebas | Unitarias e instrumentadas de cada regla L1–L12. Migración sobre SQLite real. Reindexado con muerte del proceso en el teléfono | ✅ |
| VII. UI fiel y accesible | PNG 06 y 07 literales. Los estados sin mockup son las propuestas B1–B8 y D1–D11, que se aprueban en la parada tras analyze. `Lisdata*`, tokens, `strings.xml` en es y en, 48 dp, TalkBack y fuente al 200 % | ✅ (B1–B8 y D1–D11 pendientes de aprobación) |
| VIII. Proceso | Documentación en español y código en inglés. D-043 (clarify) y D-044 (plan). Parada tras analyze | ✅ |

**Re-check post-diseño:** sin violaciones.
- **La migración de esquema** usa el mecanismo de F04 (R7 de F04), como hizo F06.
- **Reutilizar `cancelled`** no cambia el esquema: era un valor admitido sin uso (D-030). Se renombra solo en el dominio.

## Project Structure

### Documentation (this feature)

```text
specs/008-library-detail/
├── spec.md
├── plan.md                      # este fichero
├── research.md                  # R1–R11
├── data-model.md                # esquema v3, estados, tipos, operaciones del índice, logs
├── quickstart.md
├── contracts/
│   ├── library-api.md           # repositorio, casos de uso y reglas L1–L12; cambios en F06 y F07
│   └── library-ui.md            # PNG 06 y 07, propuestas B1–B8 y D1–D11, accesibilidad A1–A7, navegación
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/assets/schema.sql                        # v3: name_norm, replaces_id, idx_documents_created
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/rag/
│   ├── RagIndex.kt                                   # libraryPage, libraryCount, addReindexShadow, shadowOf, publishReindex, removeFromIndex
│   ├── RagModels.kt                                  # OUT_OF_INDEX("cancelled"); StoredDocument.replacesId
│   ├── RagIndexConfig.kt                             # SCHEMA_VERSION = 3
│   └── DocumentNames.kt                              # fold (R3)
├── domain/library/
│   ├── LibraryModels.kt                              # LibraryQuery, LibraryPage, LibraryDocument, DocumentDetail, DocumentPreview…
│   ├── LibraryRepository.kt
│   └── LibraryUseCases.kt                            # 8 casos de uso
├── data/rag/
│   ├── DefaultRagIndex.kt                            # operaciones nuevas, name_norm al insertar y al abrir
│   └── RagSchema.kt                                  # RagMigration(from = 2)
├── data/library/
│   ├── DefaultLibraryRepository.kt
│   └── PreviewText.kt                                # frases vecinas (R7)
├── data/ingest/
│   ├── IngestRunner.kt                               # publishReindex para las sombras (L7)
│   ├── DefaultImportQueueRepository.kt               # cola sin sombras; remove → DeleteDocumentUseCase
│   └── DocumentFiles.kt                              # limpieza de huérfanos compartida (R6)
├── data/di/DataModule.kt, domain/di/DomainModule.kt, feature/di/FeatureModule.kt
├── feature/library/
│   ├── LibraryScreen.kt                              # LibraryRoute + LibraryScreen (sin estado)
│   ├── LibraryViewModel.kt, LibraryUiState.kt, LibraryTexts.kt
│   └── LibraryPreviews.kt
├── feature/document/
│   ├── DocumentScreen.kt                             # DocumentRoute + DocumentScreen
│   ├── DocumentViewModel.kt, DocumentUiState.kt, DocumentTexts.kt
│   └── DocumentPreviews.kt
└── core/navigation/{MicroRagNavHost, NavigationActions}.kt   # Library y Document reales; aviso de desaparecido (R8)
app/src/debug/java/com/lisdatasolutions/microrag/debug/DebugRouting.kt   # EXTRA_CHUNK_ID
app/src/main/res/values{,-en}/strings_library.xml

app/src/test/java/com/lisdatasolutions/microrag/
├── domain/rag/DocumentNamesTest.kt
├── data/rag/RagSchemaTest.kt                         # ampliado: migración from = 2
├── data/library/{DefaultLibraryRepositoryTest, PreviewTextTest}.kt
├── data/ingest/{IngestRunnerTest, DefaultImportQueueRepositoryTest}.kt   # ampliados
├── feature/library/{LibraryViewModelTest, LibraryScreenTest}.kt
├── feature/document/{DocumentViewModelTest, DocumentScreenTest}.kt
└── architecture/LayeringTest.kt                      # paquetes nuevos
app/src/test/java/com/lisdatasolutions/microrag/testing/FakeRagIndex.kt   # operaciones nuevas
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/rag/{RagIndexMigrationTest (ampliado), RagIndexLibraryTest}.kt
├── data/library/{ReindexFlowTest, LibraryStrictModeTest, LibraryBenchmarkTest, DocumentDeleteBenchmarkTest}.kt
└── feature/{library/LibraryScreenAccessibilityTest, document/DocumentScreenAccessibilityTest}.kt

offline/tests/test_appconfig.py                       # esquema v3
offline/README.md                                     # reconstruir los índices de evaluación
```

**Structure Decision:** módulo único (D-009).
- **`domain/library` y `data/library`:** lo que ven y piden las dos pantallas.
- **`data/rag`:** las operaciones atómicas sobre el índice.
- **`data/ingest`:** el único cambio del trabajo, el cierre de las sombras.

## Diseño de ejecución

1. **Abrir la Biblioteca.**
   1. `LibraryViewModel` lee la consulta del `SavedStateHandle` y llama a `ObserveLibraryUseCase(query)`.
   2. El repositorio combina `documentChanges` con la consulta, ejecuta `libraryPage` y `libraryCount` en `database` y emite `Loaded` o `Unavailable`.
   3. La primera vez, lanza la limpieza de huérfanos.
2. **Buscar o filtrar:** actualiza la consulta (texto plegado, filtro, `limit = 50`) y vuelve a consultar. **Al acercarse al final:** `limit + 50`.
3. **Inclusión:** `setIncluded` → `documentChanges` → la lista y el detalle se actualizan.
4. **Detalle.**
   1. Combina `document(id)`, `shadowOf(id)`, los recuentos guardados y el progreso de F07.
   2. Carga la vista previa una vez y otra cuando cambia el documento.
   3. Si el documento no existe, emite `NotFound` → ruta atrás y aviso (R8).
5. **Eliminar:** diálogo → «Eliminando…» → `DeleteDocumentUseCase` (L4) → vuelve a la Biblioteca.
6. **Quitar del índice:** diálogo → `removeFromIndex` (L5) → el detalle pasa a D4.
7. **Reindexar** (L6). Para un documento `done`:
   1. `addReindexShadow`;
   2. la tanda recibe la sombra, se quita la pausa y se encola el trabajo de F07.
   3. El detalle muestra D5 con el progreso de la sombra.
   4. Al cerrar, el runner llama a `publishReindex`: una transacción con borrar, mover, actualizar y borrar la sombra.
   5. `documentChanges` lleva el detalle a «Indexado».
8. **Cola de Ingesta:** sin sombras (L2). «Quitar de la cola» usa el mismo caso de uso que «Eliminar».

## Riesgos y verificaciones tempranas

1. **Migración v2 → v3 sobre datos reales** (R1). Se implementa primero y se prueba en el teléfono con los documentos de F07 (quickstart §4). **Si falla, el índice quedaría incompatible: se para y se consulta.**
2. **Reindexado atómico** (R5). `ReindexFlowTest` sobre SQLite-Vector real:
   - búsquedas en bucle durante el reindexado: nunca vacías ni mezcladas;
   - muerte del proceso a mitad en el teléfono.

   **Si la sustitución no es atómica, se para y se consulta.**
3. **Coste de borrar 4.300 fragmentos** (R6). Se mide pronto. Si pasa de 5 s, se informa; la UI ya muestra «Eliminando…» y no se bloquea.
4. **Archivo compartido por la sombra.** Una sombra nunca borra la copia (L4). Lo cubren pruebas del repositorio y de la cola.
5. **Propuestas B1–B8 y D1–D11 sin aprobar.** Las tareas de esos estados van después de la parada tras analyze.
6. **Documentos de prueba del teléfono.** Se borran desde la app en el recorrido (quickstart §5.6), nunca el documento del usuario.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
