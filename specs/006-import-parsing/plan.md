# Implementation Plan: Importación y parsing (F06)

**Branch**: `006-import-parsing` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-import-parsing/spec.md`

## Summary

F06 entrega la pantalla Importar documentos (PNG 05) y la primera mitad de la ingesta, la que no necesita modelos:

- **Importación:**
  - selección múltiple con el selector del sistema;
  - copia privada en `noBackupFilesDir/documents/` con SHA-256, y validación por archivo;
  - registro como documento `pending` en el índice de F04.

  Los intentos fallidos se muestran en la cola con su motivo y acciones.
- **Cola:** combina tres fuentes, todas observables:
  - los intentos en memoria;
  - los documentos no indexados del índice;
  - el trabajo en segundo plano.

  Sobrevive a la recreación de la Activity, y los documentos sobreviven a la muerte del proceso.
- **Procesamiento:** «Iniciar indexación» encola un único trabajo de WorkManager. Este extrae y trocea cada documento en streaming y lo deja `parsed` con su número de fragmentos y de páginas sin texto. F07 le añadirá embeddings e inserción.
- **Extracción y troceado:**
  - **PDF:** con la API del sistema (`PdfRendererPreV`, D-037), página a página;
  - **TXT y Markdown:** port exacto de `offline/eval/extract.py`;
  - **`Chunker`:** el del informe con los parámetros de `RagParameters`.

  Dos golden compartidos con el escritorio garantizan que la app hace lo mismo que la evaluación de F05:
  - el de troceado (F05);
  - uno nuevo de extracción.

**Investigación previa** (research):
- **APIs de PDF** verificadas en las fuentes del SDK 36.1 y en `api-versions.xml` del SDK 37.1 (R2, R3).
- **Cifrado RC4 de pypdf:** probado sin dependencias nuevas (R1).
- **Corpus de F05:** sin BOM ni `\r`, así que el cambio de lectura en Python no altera su extracción (R8).

## Technical Context

**Language/Version**:
- Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios).
- Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva (research R1). Se usan:
- `activity-compose` 1.13.0, para `OpenMultipleDocuments`;
- `work-runtime-ktx` 2.12.0;
- Compose BOM 2026.09.00, para `ModalBottomSheet` de M3;
- `androidx.annotation` 1.9.1, transitiva, para `@RequiresExtension`;
- Koin 4.2.2 y kotlinx-serialization 1.12.0 (pruebas);
- la API de plataforma `android.graphics.pdf`: `PdfRendererPreV` y `PdfPageTextContent`, con la extensión S ≥ 13.

En Python, `pypdf==6.20.1` (ya fijada) para los fixtures.

**Storage**:
- **Copias:** `noBackupFilesDir/documents/<sha256>.<ext>`.
- **Temporales:** `documents/.import-<uuid>.tmp`.
- **Índice:** `noBackupFilesDir/index/rag.db`, que pasa al esquema v2 con `documents.skipped_pages` (D-038).
- **Intentos de importación:** en memoria, en el *scope* de aplicación.
- **Trabajo:** WorkManager, que persiste por sí mismo.

**Testing**:
- **JVM:**
  - `PyText`, `TextDecoding` y los extractores;
  - golden de extracción y de troceado;
  - equivalencia del streaming;
  - `IngestRunner` con fakes;
  - `DocumentImporter` con Robolectric (`ShadowContentResolver`) y fakes de PDF;
  - `DefaultImportQueueRepository`, con `work-testing`;
  - `IngestViewModel`;
  - `RagIndexConfig` v2 y `LayeringTest`, con la regla nueva.
- **Instrumentadas** (emulador `Pixel_10` API 37 y teléfono API 35):
  - golden de extracción y de troceado en ART;
  - `PlatformPdfOpener` con los fixtures;
  - migración v1 → v2;
  - flujo completo con WorkManager real;
  - pantalla y accesibilidad;
  - modo estricto;
  - mediciones;
  - comparación con el corpus.
- **Python:** `unittest` del golden, de los fixtures y de `decode_text`.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia Nothing Phone (1), API 35. Emulador `Pixel_10`, API 37. AVD `MicroRAG_API33`, API 33, para el camino sin soporte de PDF si su extensión S es menor que 13 (R3).

**Project Type**: mobile-app, módulo único `:app` (D-009), más `offline/`.

**Performance Goals**:
- SC-004: un PDF de 10 MB está «Listo para procesar» en menos de 3 s.
- SC-005: 200 páginas se extraen y se trocean en menos de 30 s, con el PSS creciendo como mucho 100 MB.

Las dos se miden en el teléfono de referencia.

**Constraints**:
- Sin red y sin permisos de almacenamiento (constitución I).
- E/S en `AppDispatchers.io` e índice en `AppDispatchers.database`, nunca en el hilo principal (IV).
- Memoria acotada con documentos de hasta 100 MB y 2.000 páginas (R10).
- Logs sin nombres, rutas ni texto (I).
- La UI no importa APIs de PDF, SQLite ni WorkManager (II).

**Scale/Scope**:
- 1 pantalla y 1 hoja.
- 1 repositorio y 5 casos de uso.
- 1 worker.
- 3 extractores y el `Chunker`.
- 1 golden nuevo (unos 70 casos) y 8 fixtures PDF.
- 1 migración de esquema.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F06 | Estado |
|---|---|---|
| I. On-device y privacidad | Selector del sistema sin permisos de almacenamiento; `ManifestInvariantsTest` mantiene la lista cerrada. Las copias van en `noBackupFilesDir/documents`, sin red; `NetworkConfinementTest` sigue vigente. Los logs llevan solo ids y códigos (I10). No se guardan concesiones de URI (R4) | ✅ |
| II. Stack y arquitectura | Screen → ViewModel → casos de uso → `ImportQueueRepository` → `data/ingest`. `android.graphics.pdf` solo en `data/ingest`, con regla nueva en `LayeringTest`. La extracción y el troceado son Kotlin puro (`rag/ingest`). Versiones verificadas (R1). WorkManager para el trabajo largo y Koin | ✅ |
| III. RAG según el informe | El `Chunker` del informe con los parámetros de D-034, comprobado con el golden de F05. Extracción por página y ruta de encabezados como en el informe. El espacio vectorial no cambia; el esquema pasa a v2 con una columna (D-038) y su migración | ✅ |
| IV. Rendimiento | Streaming por página y por párrafo (R10). Un solo trabajo secuencial. Índice en su dispatcher de un hilo. Mediciones SC-004/SC-005 en el teléfono. No usa los modelos | ✅ |
| V. Veracidad del estado | Los estados salen del índice y de WorkManager. Hay progreso real por páginas y bytes, e indeterminado cuando no hay total. Ningún documento registrado sin copia completa (I1). Quitar un documento pide confirmación y borra el registro y la copia | ✅ |
| VI. Calidad y pruebas | Pruebas unitarias de parsers, chunker, repositorio y ViewModel. Golden compartidos en la JVM y en el dispositivo. Migración instrumentada. Flujo con WorkManager real. Cifras medidas | ✅ |
| VII. UI fiel y accesible | PNG 05 con G14 y G15. Los estados sin mockup son propuestas P1–P6 que se aprueban en la parada tras analyze, antes de implementarlas. Tokens, `Lisdata*`, `strings.xml` en español e inglés, 48 dp, TalkBack y fuente al 200 % | ✅ (P1–P6 pendientes de aprobación) |
| VIII. Proceso | Documentación en español y código en inglés. D-037 (clarify) y D-038 (plan). Parada tras analyze | ✅ |

**Re-check post-diseño:** sin violaciones.
- El esquema v2 amplía el del informe como ya hizo D-030. No es una excepción constitucional: el principio III protege el espacio vectorial y la trazabilidad, que no cambian.

## Project Structure

### Documentation (this feature)

```text
specs/006-import-parsing/
├── spec.md
├── plan.md                     # este fichero
├── research.md                 # R1–R13
├── data-model.md               # esquema v2, ficheros, tipos, estados y golden
├── quickstart.md
├── contracts/
│   ├── ingest-api.md           # repositorio, casos de uso, RagIndex, worker; reglas I1–I12
│   ├── ingest-ui.md            # pantalla, filas, hoja (G15), propuestas P1–P6; reglas U1–U6
│   └── extraction-golden.md    # golden de extracción, fixtures PDF; reglas X1–X4
├── checklists/requirements.md
└── tasks.md                    # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/assets/schema.sql                       # v2: documents.skipped_pages

app/src/main/java/com/lisdatasolutions/microrag/
├── domain/ingest/
│   ├── IngestModels.kt                              # DocumentFormat, IngestError, ImportLimits, QueueKey, QueueItem,
│   │                                                #   QueueItemState, ImportQueue, IngestProgress
│   ├── ImportQueueRepository.kt
│   └── IngestUseCases.kt                            # Observe, Import, Retry, Remove, StartIndexing
├── domain/rag/{RagIndex, RagModels, RagIndexConfig}.kt   # documents(), documentChanges, skippedPages; SCHEMA_VERSION 2
├── rag/ingest/
│   ├── PyText.kt                                    # isPySpace, pySplitLines, strip, split, isPyDigit (R8)
│   ├── PyDigits.kt                                  # dígitos no Nd, tabla generada desde unicodedata
│   ├── TextDecoding.kt                              # UTF-8 estricto en streaming, windows-1252, saltos de línea
│   ├── Extraction.kt                                # RawBlock, ExtractionSummary, ExtractionLimits, PdfPages, PdfOpener, excepciones
│   ├── MarkdownExtractor.kt, PlainTextExtractor.kt
│   ├── PdfExtractor.kt                              # pdfParagraphs + recorrido por páginas
│   ├── DocumentExtractor.kt
│   └── Chunker.kt                                   # port del informe, con estado
├── data/rag/{DefaultRagIndex, RagSchema}.kt         # operaciones nuevas; migración 1 → 2
├── data/ingest/
│   ├── DefaultImportQueueRepository.kt
│   ├── DocumentImporter.kt
│   ├── SafDocumentSource.kt
│   ├── DocumentFileStore.kt
│   ├── PlatformPdfOpener.kt                         # + PdfSupport
│   ├── IngestScheduler.kt                           # interfaz y WorkManagerIngestScheduler
│   ├── IngestWorker.kt
│   └── IngestRunner.kt
├── data/di/DataModule.kt, domain/di/DomainModule.kt, feature/di/FeatureModule.kt
├── feature/ingest/
│   ├── IngestScreen.kt                              # IngestRoute + IngestScreen (sin estado)
│   ├── IngestViewModel.kt, IngestUiState.kt
│   ├── QueueItemSheet.kt                            # G15
│   ├── IngestTexts.kt                               # estados y motivos → strings
│   └── IngestPreviews.kt                            # previews etiquetadas con datos de ejemplo
└── core/navigation/{MicroRagNavHost, PendingDestination}.kt   # Ingest real; acción provisional en Inicio (P6)
app/src/main/res/values{,-en}/strings_ingest.xml

app/src/test/java/com/lisdatasolutions/microrag/
├── rag/ingest/{PyTextTest, TextDecodingTest, MarkdownExtractorTest, PdfParagraphsTest, ChunkerTest,
│               ChunkerGoldenTest, ExtractionGoldenTest, StreamingEquivalenceTest, DocumentExtractorTest}.kt
├── data/ingest/{IngestRunnerTest, DocumentImporterTest, DocumentFileStoreTest, DefaultImportQueueRepositoryTest}.kt
├── feature/ingest/IngestViewModelTest.kt
├── domain/rag/RagIndexConfigTest.kt                 # v2
└── architecture/LayeringTest.kt                     # + android.graphics.pdf solo en data/ingest
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── rag/ingest/{ExtractionGoldenInstrumentedTest, ChunkerGoldenInstrumentedTest}.kt
├── data/ingest/{PlatformPdfOpenerTest, IngestFlowTest, IngestStrictModeTest, IngestBenchmarkTest,
│               CorpusPdfExtractionTest}.kt
├── data/rag/RagIndexMigrationTest.kt
└── feature/ingest/IngestScreenTest.kt
app/src/androidTest/assets/pdf/*.pdf                 # fixtures (R12)

offline/
├── eval/extract.py                                  # + decode_text
├── gen_extraction_golden.py, gen_pdf_fixtures.py
├── run_eval.py                                      # + device-extraction
├── tests/{test_extraction_golden, test_pdf_fixtures}.py; test_appconfig (schema v2)
└── golden/extraction_golden.jsonl
```

**Structure Decision**: módulo único (D-009).
- **`rag/ingest`:** la extracción y el troceado, sin Android, porque F07 los reutiliza al indexar. Se prueban en la JVM y en ART con los golden.
- **`data/ingest`:** todo lo que toca el sistema: `ContentResolver`, ficheros, PDF de plataforma y WorkManager.
- **`domain/ingest`:** el contrato que ve la UI.

## Diseño de ejecución

1. **Seleccionar.** El usuario elige archivos y la pantalla pasa las URIs a `ImportDocumentsUseCase`. El repositorio crea un `ImportAttempt` por URI (`Importing`) y los procesa de uno en uno en el *scope* de aplicación (R5):
   1. metadatos;
   2. límites;
   3. soporte de PDF;
   4. copia con huella;
   5. validación;
   6. renombrado;
   7. `addDocument`.

   Si sale bien, el intento desaparece y el documento aparece como `Ready` al emitir `documentChanges`. Si no, queda `ImportFailed(error)`.
2. **Observar.** `queue` combina los intentos, `RagIndex.documents({pending, parsed, error})` (recargado con `documentChanges`), las etiquetas activas y el progreso de `ingest`. La primera suscripción del proceso ejecuta `cleanOrphans`.
3. **Procesar.** «Iniciar indexación»:
   1. `startProcessing()` encola los `Ready` con sus etiquetas;
   2. se navega a `Index`;
   3. `IngestWorker` → `IngestRunner`, por cada id: comprueba que sigue `pending`; extrae en streaming publicando el progreso; trocea con el `Chunker` contando fragmentos; `updateStatus(PARSED, chunks, skippedPages)` o `ERROR(code)`.
4. **Reintentar y quitar:** reglas I7 e I8.
5. **Migración.** Al abrir un índice v1 (los del teléfono y el emulador de F04), `IndexCompatibilityChecker` devuelve `NeedsMigration(1)`. Se aplica `ALTER TABLE` en una transacción, se revalida y se publica `Compatible`.

## Riesgos y verificaciones tempranas

1. **Extracción de PDF en los dispositivos** (R2, R3). La primera tarea tras los fixtures es `PlatformPdfOpenerTest` en el emulador y en el teléfono. Fija:
   - qué saltos de línea da PDFium;
   - que el cifrado da `SecurityException` y el escaneado, texto vacío;
   - el valor de `build.version.extensions.s` en los tres dispositivos.

   Si PDFium no separa las líneas como espera `pdf_paragraphs`, se para y se consulta antes de seguir: la unión de párrafos en la app ya no sería la del escritorio.
2. **Port de la semántica de Python** (R8). El golden se escribe antes que los extractores (TDD). Cualquier diferencia de tablas Unicode entre el JDK y ART la detecta la prueba instrumentada.
3. **`.md` en el selector** (R4). Se comprueba a mano en el teléfono durante el recorrido. Si no se puede elegir, se amplía el filtro y se documenta.
4. **Migración v1 → v2 sobre los índices reales de F04** en el teléfono: `RagIndexMigrationTest` crea un v1 con datos y comprueba que se conservan.
5. **Memoria con 200 páginas** (SC-005). Si se superan 100 MB, se revisa la retención de páginas y bloques antes de cerrar.
6. **Propuestas P1–P6 sin aprobar.** Las tareas de UI de esos estados van después de la parada tras analyze, que es donde se aprueban.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
