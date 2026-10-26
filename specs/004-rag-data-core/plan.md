# Implementation Plan: Núcleo de datos RAG (F04)

**Branch**: `004-rag-data-core` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-rag-data-core/spec.md`

## Summary

F04 construye la capa de datos local del RAG y la garantía de compatibilidad entre el móvil y el escritorio:

- **Índice:** `schema.sql` del informe con la ampliación G27, en `assets/`. Una base SQLite bundled en `noBackupFilesDir/index/rag.db`, con SQLite-Vector cargada en cada conexión.
- **`RagIndex`:** interfaz de dominio con documentos, lotes de fragmentos idempotentes, búsqueda vectorial exacta, búsqueda léxica BM25 ponderada, carga por id y vecino. Va en una única conexión en el dispatcher de base de datos.
- **Validación de `rag_meta`:** bloquea lecturas y escrituras si el espacio vectorial o el normalizador no coinciden. La estrategia de migraciones queda documentada (la lista empieza vacía).
- **Normalizador:** `SpanishText` v1, idéntico al del informe, y su port en Python con la biblioteca estándar.
- **Golden:**
  - del normalizador (≥ 300 pares), comprobado en la JVM, en el dispositivo y en Python;
  - de embeddings (50 textos), generado en el Mac con `litert-lm-api` 0.18.1 y comprobado en el teléfono en CPU y GPU (D-030).

**Investigación previa** (research):
- **Carga de la extensión:** comprobada empíricamente. Con `useLegacyPackaging = true`, `vector.so` se extrae a `nativeLibraryDir` aunque no lleve el prefijo `lib` (R2).
- **API de SQLite-Vector 1.1.2:** verificada (R3).
- **API Python de embeddings:** verificada en el Mac (R5).

## Technical Context

**Language/Version**:
- Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios).
- Python 3.12 en `offline/`.

**Primary Dependencies** (verificadas el 2026-10-26, research R1):
- **Nuevas en la app:**
  - `androidx.sqlite:sqlite-bundled:2.7.1`;
  - `ai.sqlite:vector:1.1.2` (Apache-2.0).
- **Offline:** `litert-lm-api==0.18.1`, la única dependencia de Python.
- **Existentes:** LiteRT-LM 0.18.1, Koin 4.2.2, kotlinx-serialization 1.12.0 (lectura de los golden en las pruebas) y coroutines 1.11.0.

**Storage**:
- **Índice:** `noBackupFilesDir/index/rag.db`, con WAL (`AppDirs.index` de F03).
- **Esquema:** `assets/schema.sql`.
- **Golden:** en `offline/golden/`; las pruebas instrumentadas los leen como *assets* de `androidTest`.

**Testing**:
- **JVM:**
  - `SpanishText`;
  - golden del normalizador;
  - `IndexCompatibilityChecker`;
  - división de `schema.sql`;
  - BLOB float32 LE;
  - configuración esperada.
- **Instrumentadas** (SQLite real con la extensión, constitución VI):
  - carga y `vector_version`;
  - inserción y búsqueda vectorial;
  - FTS ponderado y escapado;
  - triggers y cascada;
  - idempotencia y *rollback*;
  - bloqueo por `rag_meta` y migraciones;
  - golden del normalizador en el dispositivo;
  - modo estricto;
  - medición con 10k fragmentos.
- **Teléfono, `@RequiresModels`:** golden de embeddings en CPU y GPU.
- **Python:** `unittest`.

**Target Platform**: Android, minSdk 31 y targetSdk 37. Teléfono de referencia Nothing Phone (1), API 35 (ARM64); emulador `Pixel_10`, API 37 (ARM64).

**Project Type**: mobile-app, módulo único `:app` (D-009), más la herramienta Python `offline/`.

**Performance Goals**: SC-008 mide el p50 y el p95 de las búsquedas vectorial y léxica con 10.000 fragmentos en el teléfono. Es informativo; el umbral de 20 ms decide INT8 en F14.

**Constraints**:
- Base de datos fuera del hilo principal, con un solo escritor (constitución IV).
- Nunca se busca en un índice incompatible (constitución III).
- Los textos de documentos y consultas no se registran en los logs (constitución I).
- Ninguna dependencia de red.
- La UI no importa SQLite (constitución II).

**Scale/Scope**:
- 1 esquema, con 4 tablas y 3 triggers.
- 1 interfaz `RagIndex` con 15 operaciones.
- 2 golden: ≥ 300 pares y 50 vectores.
- Corpus objetivo de 10k a 100k fragmentos (constitución IV), que se mide en F14.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F04 | Estado |
|---|---|---|
| I. On-device y privacidad | El índice vive en `noBackupFilesDir/index` y queda excluido de copias por las reglas de F01. Sin red. Los logs llevan solo ids y recuentos (A10). El pipeline Python se ejecuta en el escritorio y no distribuye índices (D-002) | ✅ |
| II. Stack y arquitectura | `RagIndex` es una interfaz de dominio, con la implementación en `data/rag`. La UI no importa SQLite; `NetworkConfinementTest` y `LayeringTest` siguen vigentes. Las versiones y el `.so` están verificados (R1 a R3). Koin para la inyección | ✅ |
| III. RAG según el informe | Esquema, triggers, BM25 3/1, `vector_init` en cada conexión, normalizador idéntico al del informe con golden en los dos lados, `rag_meta` validado al abrir, prefijos de F02 y 256D con L2 | ✅ (G27 ampliado, D-030) |
| IV. Rendimiento | Una conexión en `AppDispatchers.database` (un solo hilo y un solo escritor). Modo estricto en debug. Lotes en transacción. Medición con 10k fragmentos (SC-008) | ✅ |
| V. Veracidad del estado | Un índice incompatible o ilegible no devuelve resultados y expone el motivo. Lotes atómicos: nunca un documento a medias por un lote roto | ✅ |
| VI. Calidad y pruebas | Pruebas instrumentadas obligatorias de SQLite (con SQLite-Vector, FTS5, triggers y migraciones), del normalizador frente al golden y de la compatibilidad de embeddings ≥ 0,99, en CPU y GPU. Las pruebas unitarias del repositorio (`DefaultRagIndex`) son instrumentadas, sobre un fichero temporal: SQLite-Vector solo existe como nativa de Android. Su lógica pura (compatibilidad, esquema, BLOB, configuración) se prueba en la JVM | ✅ |
| VII. UI | Sin UI (D-030) | N/A |
| VIII. Proceso | Documentación en español y código en inglés. D-030. Parada tras analyze | ✅ |

**Re-check post-diseño:** sin violaciones.
- `useLegacyPackaging` es una configuración de build justificada (R2), no una excepción constitucional.
- `offline/` es la herramienta prevista por la constitución («Pipeline Python offline: solo para evaluación, ajuste y generación de golden»).

## Project Structure

### Documentation (this feature)

```text
specs/004-rag-data-core/
├── spec.md
├── plan.md                    # este fichero
├── research.md                # R1–R10
├── data-model.md              # schema.sql, rag_meta, tipos y golden
├── quickstart.md
├── contracts/
│   ├── rag-index-api.md       # RagIndex, reglas A1–A10
│   └── offline-golden.md      # entorno Python, ficheros y reglas O1–O5
├── checklists/requirements.md
└── tasks.md                   # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                        # + sqliteBundled 2.7.1, sqliteVector 1.1.2
app/build.gradle.kts                             # + dependencias; packaging.jniLibs.useLegacyPackaging = true;
                                                 #   sourceSets.androidTest.assets += ../offline/golden
app/src/main/assets/schema.sql                   # data-model §1

app/src/main/java/com/lisdatasolutions/microrag/
├── domain/rag/
│   ├── RagIndex.kt                              # interfaz e IndexUnavailableException
│   ├── RagModels.kt                             # DocumentStatus, NewDocument, StoredDocument, ChunkInput, ChunkView,
│   │                                            #   ScoredChunk, IndexCounts, IndexCompatibility
│   ├── RagIndexConfig.kt                        # configuración esperada (modelo, 256, prefijos, normalizador, esquema)
│   └── IndexCompatibilityChecker.kt             # función pura (R7)
├── rag/text/SpanishText.kt                      # normalizador v1 (informe), VERSION = 1
├── data/rag/
│   ├── RagSchema.kt                             # carga y división de schema.sql; RagMigrations
│   ├── VectorBlobs.kt                           # FloatArray ↔ BLOB float32 LE
│   ├── RagConnection.kt                         # apertura (driver, extensión, pragmas, esquema, vector_init), transacciones
│   └── DefaultRagIndex.kt                       # RagIndex sobre RagConnection en AppDispatchers.database
├── data/di/DataModule.kt                        # + single<RagIndex>
└── MicroRagApplication.kt                       # + modo estricto de debug (hook)
app/src/debug/java/.../debug/DebugStrictMode.kt  # ThreadPolicy detectDiskReads/Writes + penaltyLog
app/src/release/java/.../debug/DebugStrictMode.kt # stub sin efecto

app/src/test/java/com/lisdatasolutions/microrag/
├── rag/text/{SpanishTextTest, NormalizerGoldenTest}.kt
├── rag/runtime/EmbeddingGoldenFileTest.kt          # meta, entradas y normas del golden de embeddings
├── domain/rag/{IndexCompatibilityCheckerTest, RagIndexConfigTest}.kt
└── data/rag/{RagSchemaTest, VectorBlobsTest}.kt
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/rag/
│   ├── VectorExtensionLoadTest.kt               # vector_version en el dispositivo (verificación temprana)
│   ├── RagIndexWriteTest.kt                     # esquema, documentos, lotes, idempotencia, rollback, cascada, triggers
│   ├── RagIndexSearchTest.kt                    # vectorial, FTS ponderado y escapado, carga, vecino, vacío
│   ├── RagIndexCompatibilityTest.kt             # rag_meta, bloqueo, migraciones, ilegible
│   ├── RagIndexStrictModeTest.kt                # SC-007
│   └── RagIndexBenchmarkTest.kt                 # SC-008 (10k fragmentos, p50/p95 al log)
├── rag/text/NormalizerGoldenInstrumentedTest.kt
├── rag/runtime/EmbeddingGoldenTest.kt           # @RequiresModels, CPU y GPU
└── testing/TempRagIndex.kt                      # índice temporal en cacheDir y conexión cruda

offline/                                          # contracts/offline-golden.md
├── README.md, requirements.txt
├── normalizer.py, embedding_text.py
├── gen_normalizer_golden.py, gen_embedding_golden.py
├── tests/{__init__.py, test_normalizer.py, test_embedding_text.py}
└── golden/{normalizer_inputs.txt, normalizer_golden.jsonl, embedding_texts.jsonl, embedding_golden.jsonl}
```

**Structure Decision**: módulo único (D-009).
- **Dominio:** el contrato `RagIndex` y la lógica pura de compatibilidad. F10 y F11 dependerán solo de esto.
- **`rag/text`:** el normalizador, sin Android, porque lo usarán F06/F07 (indexar) y F10 (consultar).
- **`data/rag`:** todo lo que toca SQLite.
- **`offline/`:** la herramienta Python en la raíz del repositorio, como indica el informe (§Estructura del proyecto).

## Diseño de ejecución

1. **Primera operación** sobre `RagIndex`. `open()` se llama de forma perezosa, en `AppDispatchers.database`:
   1. crear `index/` si falta;
   2. `BundledSQLiteDriver().addExtension(nativeLibraryDir/vector.so, "sqlite3_vector_init")`;
   3. abrir con lectura, escritura y creación;
   4. pragmas, esquema y `vector_init`;
   5. leer `rag_meta`: si está vacío, escribir las claves en una transacción; si no, `IndexCompatibilityChecker`, migrar si toca y volver a validar;
   6. publicar `compatibility`.
2. **Operaciones:** con un estado distinto de `Compatible` lanzan `IndexUnavailableException` antes de tocar la base. Si no, ejecutan el SQL del contrato.
3. **Errores de apertura:** la extensión que no carga, un fichero que no es una base de datos o `vector_init` que falla dan `Unreadable(motivo)`. Qué hacer con el fichero (recrearlo) lo deciden F07/F12.
4. **Golden de embeddings:**
   1. en el Mac: `gen_embedding_golden.py` → `embedding_golden.jsonl` (en git);
   2. en el teléfono: `EmbeddingGoldenTest` lee los *assets*, construye las entradas con `EmbeddingPrefixes`, comprueba que son iguales a `input`, embebe con `LiteRtEngineFactory` en CPU y después en GPU (con un único motor a la vez) y calcula los cosenos.

## Riesgos y verificaciones tempranas

Ver research R10. Además:

1. **Primera tarea tras añadir las dependencias:** `VectorExtensionLoadTest`, que carga la extensión y ejecuta `vector_version()` en el emulador y en el teléfono. Si falla en el teléfono, se aplica el plan B de R2 antes de seguir.
2. **WorkManager con `androidx.sqlite` 2.7.1:** se ejecutan sus tests JVM justo después de añadir las dependencias.
3. **Golden de embeddings en GPU por debajo de 0,99:** la prueba falla (FR-021). Se documenta, se para y se consulta al usuario antes de cerrar F04. Por D-030 y D-024, ese teléfono indexaría en CPU.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
