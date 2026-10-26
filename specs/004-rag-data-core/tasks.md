---

description: "Lista de tareas de F04 — Núcleo de datos RAG"
---

# Tasks: Núcleo de datos RAG (F04)

**Input**: Documentos de diseño en `/specs/004-rag-data-core/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **Obligatorios** por la constitución (VI: pruebas instrumentadas de SQLite, del normalizador frente al golden y de la compatibilidad de embeddings) y por los criterios SC-001 a SC-008. En cada fase, las tareas de test van **antes** de la implementación: se escriben primero y deben fallar antes de implementar.

**Organización**: por historia de usuario de la spec:
- US1 — Índice local fiable (P1);
- US2 — Búsquedas básicas sobre el índice (P1);
- US3 — El mismo normalizador en el móvil y en el escritorio (P1);
- US4 — Vectores compatibles entre el escritorio y el móvil (P1);
- US5 — Protección del espacio vectorial (P2).

**Entorno**:
- Todos los comandos Gradle requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Teléfono de referencia: `adb -s P31281001066`. **Nunca `connected*AndroidTest`**: instalar los dos APK y usar `am instrument` (quickstart §3).
- Emulador: `Pixel_10` (`emulator-5554`).
- Python: siempre `offline/.venv/bin/python -I` (contracts/offline-golden.md).

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US5).
- Las rutas son relativas a la raíz del repositorio. Abreviaturas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/` y `…/androidTest/` usan el mismo paquete bajo `app/src/test/java/` y `app/src/androidTest/java/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencias verificadas, empaquetado de la extensión y verificación temprana de que `vector.so` carga en los dos dispositivos.

- [X] T001 Añadir a `gradle/libs.versions.toml` (research R1):
  - versiones `sqliteBundled = "2.7.1"` y `sqliteVector = "1.1.2"`;
  - librerías `androidx-sqlite-bundled` (`androidx.sqlite:sqlite-bundled`) y `sqlite-vector` (`ai.sqlite:vector`).
- [X] T002 En `app/build.gradle.kts`:
  - `implementation` de las dos librerías;
  - `packaging { jniLibs { useLegacyPackaging = true } }`, con un comentario que remita a research R2;
  - añadir `../offline/golden` a los *assets* del source set `androidTest` (research R6), con la sintaxis de AGP 9.3 verificada en su documentación.

  Después:
  1. ejecutar `./gradlew :app:dependencies --configuration debugRuntimeClasspath` y anotar en research R1 las versiones resueltas de `androidx.sqlite:sqlite`, `sqlite-framework` y `room-runtime`;
  2. comprobar `assembleDebug` y `lintDebug` en verde;
  3. ejecutar los tests JVM de WorkManager (`ModelDownloadWorkerTest` y `DefaultModelDownloadControllerTest`) con `androidx.sqlite` 2.7.1 (riesgo R10-1). Si fallan, parar y fijar la línea compatible antes de seguir.
- [X] T003 **Verificación temprana** (plan, riesgo 1): crear `…/androidTest/data/rag/VectorExtensionLoadTest.kt`. Comprueba:
  - `File(applicationInfo.nativeLibraryDir, "vector.so")` existe;
  - con `BundledSQLiteDriver().addExtension(<esa ruta>, "sqlite3_vector_init")`, una base temporal en `cacheDir` abre y `SELECT vector_version()` devuelve `1.1.2`;
  - `SELECT sqlite_compileoption_used('ENABLE_FTS5')` devuelve 1.

  Ejecutarla en el emulador y en el teléfono (quickstart §3). **Si falla en el teléfono:** aplicar el plan B de research R2 y avisar al usuario antes de seguir.
- [X] T004 [P] Crear el esqueleto de `offline/` (contracts/offline-golden.md):
  - `offline/requirements.txt` con una sola línea, `litert-lm-api==0.18.1`;
  - `offline/README.md` inicial (propósito y entorno; se completa en T047);
  - `offline/tests/__init__.py` vacío.

  Comprobar que `.gitignore` ya cubre `offline/.venv/`, `offline/build/`, `offline/models/` y `__pycache__/`. Crear el entorno y el enlace al modelo como en quickstart §1.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tipos y contrato de dominio, normalizador, configuración esperada, esquema, conexión y la apertura de `DefaultRagIndex`. Lo usan todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase.

### Tests (escribir primero)

- [X] T005 [P] `…/test/rag/text/SpanishTextTest.kt`:
  - ejemplos del informe: `clientes → client`, `cliente → client`, `motores → motor`, `luces → luz`, `R-1234 → r-1234`;
  - minúsculas y eliminación de marcas tras NFD (`canción → cancion`, `año → ano`, `pingüino → pinguin`);
  - fuera las palabras vacías y los tokens de un carácter;
  - el stemmer solo actúa con 5 o más caracteres y sin dígitos, incluida la regla `eses`;
  - los separadores `- _ . /` unen tokens solo entre caracteres alfanuméricos (`v1.2/a`, `a-`, `-a`);
  - `ftsQuery`:
    - términos distintos, entrecomillados y unidos con ` OR `;
    - `"` escapada como `""`;
    - como mucho 16 términos;
    - solo palabras vacías da cadena vacía;
  - `SpanishText.VERSION == 1`.
- [X] T006 [P] `…/test/domain/rag/RagIndexConfigTest.kt`:
  - `RagIndexConfig.current()` lleva `schemaVersion = 1`, el SHA-256 de `ModelCatalog.byId(EG2_270M)`, `dims = EmbeddingMath.DIMENSIONS`, los prefijos de `EmbeddingPrefixes` (con el espacio final) y `SpanishText.VERSION`;
  - `toMeta()` devuelve exactamente las 7 claves de data-model §2, con `quant_dirty = "1"`.
- [X] T007 [P] `…/test/domain/rag/IndexCompatibilityCheckerTest.kt` (research R7):
  - metadatos iguales a la configuración: `Compatible`;
  - modelo, dimensiones, prefijo de documento (incluido un prefijo sin el espacio final), prefijo de consulta o versión del normalizador distintos: `Incompatible(campo, esperado, encontrado)`;
  - clave ausente: `Incompatible("missing:<clave>", …)`;
  - `schema_version` no numérica o mayor que la de la app: `Incompatible`;
  - `schema_version` menor con una cadena completa de migraciones: `NeedsMigration(from)`. Si falta un eslabón: `Incompatible`;
  - `quant_dirty` no se valida.
- [X] T008 [P] `…/test/data/rag/RagSchemaTest.kt`. Lee `src/main/assets/schema.sql`:
  - la división da 9 sentencias, en este orden: 1 `PRAGMA`, 3 `CREATE TABLE`, 1 `CREATE INDEX`, 1 `CREATE VIRTUAL TABLE` y 3 `CREATE TRIGGER`, con los cuerpos `BEGIN … END` enteros;
  - los valores SQL de `DocumentStatus` coinciden exactamente con el `CHECK` de `status`;
  - `RagMigrations.all` está vacía y, en general, ordenada por `from` sin repetidos.
- [X] T009 [P] `…/test/data/rag/VectorBlobsTest.kt`:
  - ida y vuelta `FloatArray` ↔ BLOB sin pérdida;
  - orden *little-endian* (`1.0f` → `00 00 80 3F`);
  - 256 componentes ocupan 1.024 bytes;
  - un BLOB con un tamaño que no es múltiplo de 4 se rechaza.

### Implementación

- [X] T010 [P] Crear `…/microrag/domain/rag/RagModels.kt` y `…/microrag/domain/rag/RagIndex.kt`:
  - tipos de data-model §3, con `DocumentStatus.sqlValue`;
  - la interfaz `RagIndex`, `IndexUnavailableException` y `DuplicateDocumentException`, según contracts/rag-index-api.md.
- [X] T011 [P] Crear `…/microrag/rag/text/SpanishText.kt`:
  - el código del informe (§Búsqueda léxica en español), literal;
  - más `const val VERSION = 1` y un KDoc que remita al golden y a la regla de subir la versión.
- [X] T012 Crear `…/microrag/domain/rag/RagIndexConfig.kt` e `…/microrag/domain/rag/IndexCompatibilityChecker.kt`. Depende de T010 y T011:
  - `RagIndexConfig` con `current()` y `toMeta()`;
  - `IndexCompatibilityChecker.check(config, meta, migrations)` como función pura, que devuelve `CompatibilityDecision`: `Compatible`, `NeedsMigration(from)` o `Incompatible(field, expected, found)`.
- [X] T013 [P] Crear `app/src/main/assets/schema.sql` y `…/microrag/data/rag/RagSchema.kt`:
  - el esquema de data-model §1, con los tres triggers copiados literalmente del informe (§Esquema SQLite);
  - `RagSchema`: lectura desde `AssetManager` y división en sentencias (data-model §1);
  - `RagMigration(from: Int, statements: List<String>)` y `RagMigrations.all = emptyList()`, con un KDoc que explique la estrategia (research R7).
- [X] T014 [P] Crear `…/microrag/data/rag/VectorBlobs.kt`, con `FloatArray.toLeBlob()` y `ByteArray.toFloatArrayLe()`.
- [X] T015 Crear `…/microrag/data/rag/RagConnection.kt` (research R8). Depende de T013:
  - `open(dbFile, extensionPath, statements)`:
    1. `BundledSQLiteDriver().addExtension(extensionPath, "sqlite3_vector_init")`;
    2. abrir con lectura, escritura y creación;
    3. pragmas `journal_mode=WAL`, `synchronous=NORMAL`, `temp_store=MEMORY` y `foreign_keys=ON`;
    4. el esquema;
    5. `vector_init('chunks','embedding','dimension=256,type=FLOAT32,distance=COSINE,normalized=1')`.

    Cada fallo se convierte en `RagOpenException(reason)`, con un motivo sin rutas de usuario ni contenido.
  - Ayudas para ejecutar y consultar con parámetros.
  - `transaction { }`: `BEGIN IMMEDIATE`, después `COMMIT`, o `ROLLBACK` ante cualquier error.
  - `close()`.
- [X] T016 Crear `…/microrag/data/rag/DefaultRagIndex.kt`. Depende de T010, T012, T014 y T015. Su constructor sigue el contrato (§Configuración e inyección), más `migrations: List<RagMigration> = RagMigrations.all` y un reloj inyectable. En esta fase incluye:
  - `open()` idempotente y perezoso (`ensureOpen()` desde cada operación), siempre en el dispatcher de base de datos;
  - crea la carpeta del fichero si falta;
  - lee el esquema de *assets* dentro de la apertura, nunca en el hilo de quien llama;
  - con `rag_meta` vacío, escribe `config.toMeta()` en una transacción;
  - si no está vacío, usa `IndexCompatibilityChecker`: `Compatible` publica el estado, e `Incompatible` lo publica sin tocar nada;
  - `compatibility` como `StateFlow`, que empieza en `Closed`;
  - la guarda `requireCompatible()`, que lanza `IndexUnavailableException(state)` antes de tocar la base (A2);
  - `meta()`;
  - `close()`, que vuelve a `Closed` (la siguiente operación reabre);
  - los logs solo llevan ids, recuentos y tiempos (A10).
- [X] T017 Crear `…/androidTest/testing/TempRagIndex.kt`. Es el ayudante de las pruebas instrumentadas:
  - un `DefaultRagIndex` sobre `cacheDir/rag-test-<uuid>.db`, con el esquema real de *assets*, la extensión real, `RagIndexConfig.current()`, un dispatcher de un solo hilo y un `AppLogger` que captura los mensajes;
  - `raw()`: una conexión independiente con la extensión, para inspeccionar o alterar el fichero;
  - limpieza de `.db`, `-wal` y `-shm`;
  - fábricas `unitVector(seed)` y `chunk(ordinal, content, heading, vector)`.

**Checkpoint**: `testDebugUnitTest` en verde con T005–T009. Ya se puede abrir un índice nuevo.

---

## Phase 3: User Story 1 — Índice local fiable (Priority: P1) 🎯 MVP

**Goal**: crear o abrir el índice, registrar documentos, guardar lotes de fragmentos atómicos e idempotentes y borrar en cascada.

**Independent Test**: en el emulador y en el teléfono, crear el índice, registrar un documento y guardar un lote; repetirlo sin duplicados; borrar el documento sin dejar fragmentos ni entradas léxicas.

### Tests (escribir primero)

- [X] T018 [US1] `…/androidTest/data/rag/RagIndexWriteTest.kt`, con `TempRagIndex`:
  - **Escenario 1:** índice nuevo, `compatibility == Compatible`. `sqlite_master` contiene las 3 tablas, el índice, `chunks_fts` y los 3 triggers. `rag_meta` tiene las 7 claves con los valores esperados.
  - **Documentos:**
    - `addDocument` devuelve el id, con `source = 'user'`, `pending`, `included = true` y las fechas;
    - `updateStatus` y `setIncluded` actualizan `updated_at_ms` (reloj inyectado);
    - `document(id)` de un id inexistente devuelve `null`;
    - un segundo `addDocument` con el mismo `content_hash` lanza `DuplicateDocumentException(idExistente)` y no cambia nada (A11).
  - **Escenario 2:** un lote de 5 fragmentos devuelve 5. `counts()` da `chunks = ftsRows = 5`. Se comprueban `title_norm = normalize("<nombre> › <ruta>")` y `body_norm = normalize(content)`.
  - **Escenario 3 (SC-004):** repetir el lote devuelve 0, y los recuentos no cambian.
  - **Validación (A3):**
    - un vector de 255 componentes, un vector no unitario (norma 2), ordinales repetidos dentro del lote o un documento inexistente dan `IllegalArgumentException`;
    - no se inserta nada.
  - **Escenario 5 (A4):**
    1. con `raw()`, crear un trigger `BEFORE INSERT ON chunks` que hace `RAISE(ABORT)` en el ordinal 3;
    2. guardar el lote: falla y deja 0 fragmentos de ese lote;
    3. borrar el trigger y repetir: se guardan todos.
  - **FR-009:** poner `quant_dirty = 0` con `raw()`; tras un lote, vuelve a valer `1`.
  - **Triggers:**
    - tras insertar, `MATCH` encuentra el fragmento;
    - un `UPDATE` de `body_norm` con `raw()` se refleja en el FTS;
    - `INSERT INTO chunks_fts(chunks_fts, rank) VALUES('integrity-check', 1)` no falla tras cada operación.
  - **Escenario 4 (SC-005, A8):** tras `deleteDocument`, 0 fragmentos y 0 filas en `chunks_fts_docsize` del documento, y `ftsRows == chunks`.
  - **Escenario 6:** `close()` y reabrir. Los datos siguen y el estado es `Compatible`.
  - **Transacción sin confirmar:** con `raw()`, `BEGIN IMMEDIATE`, un `INSERT` y cerrar sin `COMMIT`. Al reabrir no aparece nada.

### Implementación

- [X] T019 [US1] En `…/microrag/data/rag/DefaultRagIndex.kt`:
  - `addDocument`, con `DuplicateDocumentException` ante un `content_hash` existente (A11);
  - `document`, `updateStatus` (`error` se escribe siempre y `chunkCount` solo si no es null), `setIncluded` y `deleteDocument`;
  - `counts()`, con `ftsRows` contado en `chunks_fts_docsize`, que es el índice real (en una tabla FTS5 de contenido externo, `count(*)` sobre `chunks_fts` lee `chunks`).
- [X] T020 [US1] En `…/microrag/data/rag/DefaultRagIndex.kt`, `insertChunks`:
  - valida antes de la transacción (A3): 256 componentes, norma unitaria, documento existente y ordinales distintos;
  - `INSERT OR IGNORE … vector_as_f32(?)` dentro de `transaction { }`, con `UPDATE rag_meta SET value = '1' WHERE key = 'quant_dirty'` en la misma transacción (A4);
  - `title_norm` y `body_norm` se calculan dentro, como en data-model §4;
  - devuelve el número de filas insertadas (`changes()`).
- [X] T021 [US1] Registrar `single<RagIndex>` en `…/microrag/data/di/DataModule.kt`, según el contrato (`AppDirs.index/"rag.db"`, `nativeLibraryDir + "/vector.so"`, `RagIndexConfig.current()`, `AppDispatchers.database`). Actualizar `extraTypes` en `…/test/core/di/AppModulesTest.kt` si hace falta, y ejecutar `testDebugUnitTest`.
- [X] T022 [US1] Ejecutar `RagIndexWriteTest` y `VectorExtensionLoadTest` en el emulador y en el teléfono (quickstart §3).

**Checkpoint**: US1 completa. El índice guarda y borra datos de forma fiable en los dos dispositivos.

---

## Phase 4: User Story 2 — Búsquedas básicas sobre el índice (Priority: P1)

**Goal**: búsqueda vectorial exacta top-k, búsqueda léxica BM25 3/1, carga por ids y vecino.

**Independent Test**: con fragmentos de vectores y textos conocidos, la búsqueda vectorial devuelve primero el del vector idéntico, la léxica devuelve primero el del título, y la carga y el vecino devuelven los datos exactos.

### Tests (escribir primero)

- [X] T023 [US2] `…/androidTest/data/rag/RagIndexSearchTest.kt`, con `TempRagIndex`:
  - **Escenario 1 (SC-003):**
    - con 20 fragmentos de vectores aleatorios con semilla, la consulta con el vector de cada uno lo devuelve el primero, en el 100 % de los casos;
    - como mucho `k` resultados, con la distancia en orden ascendente;
    - un vector de consulta de 255 componentes o no unitario da `IllegalArgumentException`.
  - **Escenario 2 (A6):** un término en el título de un fragmento y en el cuerpo de otro (con la misma longitud de cuerpo): el del título va primero.
  - **Escenario 3:** «de la que» devuelve una lista vacía sin error.
  - **Escenario 4:** `R-1234 "manual"` no da error de sintaxis y encuentra el fragmento con `r-1234`.
  - **Escenario 5 (A7):** `loadChunks` devuelve el contenido original (sin cabecera), la ruta, la página, `tokenEstimate` y `documentName`. Los ids inexistentes no aparecen.
  - **Escenario 6:** `neighbor(doc, n + 1)` devuelve el fragmento, o `null` si no existe.
  - **Índice vacío (FR-013):** las dos búsquedas devuelven listas vacías.
  - **US1, escenario 6:** tras `close()` y reabrir, la búsqueda vectorial sigue funcionando, porque `vector_init` se ejecuta en cada conexión.
  - **A10:** ningún mensaje del logger capturado contiene el texto de un fragmento, el de la consulta ni el nombre de un documento.

### Implementación

- [X] T024 [US2] En `…/microrag/data/rag/DefaultRagIndex.kt`:
  - `vectorSearch` (A5): `vector_full_scan` con el BLOB LE, y rechazo de vectores que no tengan 256 componentes o no sean unitarios;
  - `textSearch` (A6): `SpanishText.ftsQuery`; si sale vacía, devuelve una lista vacía sin consultar;
  - `loadChunks` (A7): `JOIN` con `documents`, en bloques de como mucho 500 ids;
  - `neighbor`.
- [X] T025 [US2] Ejecutar `RagIndexSearchTest` en el emulador y en el teléfono (SC-003).

**Checkpoint**: US1 y US2 completas. F10 ya tiene sus bloques de búsqueda.

---

## Phase 5: User Story 3 — El mismo normalizador en el móvil y en el escritorio (Priority: P1)

**Goal**: port exacto de `SpanishText` v1 en Python y un golden de al menos 300 pares que coincide al 100 % en la JVM, en el dispositivo y en Python.

**Independent Test**: el golden pasa en los tres entornos, y los ejemplos del informe dan su resultado documentado.

### Tests (escribir primero)

- [X] T026 [P] [US3] `offline/tests/test_normalizer.py` (`unittest`):
  - ejemplos del informe y los casos de T005, escritos a mano;
  - las sutilezas de research R4: longitudes en UTF-16, `Nd` solo del plano básico, separadores;
  - coincidencia exacta con `golden/normalizer_golden.jsonl`;
  - el generador es determinista: dos ejecuciones sobre un directorio temporal dan los mismos bytes;
  - SHA-256 del golden fijado para `VERSION = 1` (escenario 3).
- [X] T027 [P] [US3] `…/test/rag/text/NormalizerGoldenTest.kt` (JVM):
  - lee `../offline/golden/normalizer_golden.jsonl` desde el módulo;
  - al menos 300 pares;
  - 100 % de coincidencia exacta con `SpanishText.normalize`, mostrando los 20 primeros fallos;
  - el SHA-256 del fichero coincide con el fijado para `SpanishText.VERSION` (un mapa versión → huella). Si cambia el golden sin subir la versión, la prueba falla (escenario 3).
- [X] T028 [P] [US3] `…/androidTest/rag/text/NormalizerGoldenInstrumentedTest.kt`: el mismo criterio, leyendo el fichero de los *assets* del APK de prueba (`InstrumentationRegistry.getInstrumentation().context.assets`), con la ICU de Android.

### Implementación

- [X] T029 [US3] Crear `offline/normalizer.py` (regla O1, research R4):
  - solo biblioteca estándar;
  - `VERSION = 1`, la misma lista de palabras vacías y el mismo stemmer;
  - `tokens`, `normalize` y `fts_query`.
- [X] T030 [P] [US3] Crear `offline/golden/normalizer_inputs.txt` con al menos 300 entradas escritas a mano, con comentarios `#` por categoría, que cubran FR-019:
  - ejemplos del informe;
  - plural y género;
  - tildes, diéresis, `ñ` y `ç`;
  - palabras vacías;
  - códigos con dígitos y separadores;
  - mayúsculas, incluidas la sigma final griega e `İ`;
  - puntuación;
  - Unicode no latino y emojis;
  - párrafos completos.

  Sin caracteres posteriores a Unicode 15.0 (research R4).
- [X] T031 [US3] Crear `offline/gen_normalizer_golden.py` (regla O2): orden estable, sin duplicados, y falla si salen menos de 300 pares. Generar `offline/golden/normalizer_golden.jsonl` y comprobar que volver a ejecutarlo no cambia `git diff`. Fijar la huella en T026 y T027.
- [X] T032 [US3] Ejecutar las pruebas Python, `NormalizerGoldenTest` y `NormalizerGoldenInstrumentedTest` en el emulador y en el teléfono (SC-001). **Si hay diferencias:** investigarlas antes de tocar el golden (riesgo R10-3).

**Checkpoint**: el normalizador es idéntico en los tres entornos.

---

## Phase 6: User Story 4 — Vectores compatibles entre el escritorio y el móvil (Priority: P1)

**Goal**: golden de 50 embeddings generado en el Mac con `litert-lm-api` 0.18.1 y comprobado en el teléfono con un coseno ≥ 0,99 en CPU y en GPU.

**Independent Test**: generar el golden en el Mac y ejecutar `EmbeddingGoldenTest` en el teléfono; los 50 pares superan 0,99 en los dos backends.

### Tests (escribir primero)

- [X] T033 [P] [US4] `offline/tests/test_embedding_text.py`:
  - la entrada de consulta es `QUERY + texto.strip()`;
  - la de documento es `DOCUMENT + "\n".join(cabecera y contenido sin vacíos)`, con y sin cabecera;
  - el truncado da 256 componentes float32 con norma 1 (tolerancia 1e-6);
  - un vector de norma 0 o de menos de 256 componentes da error.
- [X] T034 [P] [US4] `…/test/rag/runtime/EmbeddingGoldenFileTest.kt` (JVM), que lee `../offline/golden/embedding_golden.jsonl`:
  - `meta.model_sha256` es el de `ModelCatalog.byId(EG2_270M)` y `meta.dims == 256`;
  - hay 50 entradas, 25 de consulta y 25 de documento;
  - cada `input` es igual a `EmbeddingPrefixes.query(text)` o a `EmbeddingPrefixes.document(text, header)`;
  - cada `v256` tiene 256 componentes y norma 1 (tolerancia 1e-5).
- [X] T035 [US4] `…/androidTest/rag/runtime/EmbeddingGoldenTest.kt`, con `@RequiresModels`:
  - `assumeTrue` si `no_backup/models/embeddinggemma-2-text-270m.litertlm` no existe;
  - si el SHA-256 del fichero (con `Sha256.of`) no es el de `meta`, la prueba falla con «el modelo no coincide» y no compara (escenario 3);
  - **CPU y después GPU**, con `LiteRtEngineFactory` y un solo motor a la vez (cerrado antes del siguiente): embebe cada texto con `embedQuery` o `embedDocument(text, header)` y calcula el coseno con `v256`;
  - registra por backend `MicroRAG golden backend=<b> min=… mean=… n=50` y los ids que fallan;
  - si la GPU no inicializa, lo registra como «GPU no disponible» y no aplica sus aserciones;
  - falla si algún par queda por debajo de 0,99 en un backend disponible, indicando los ids (escenario 2).

### Implementación

- [X] T036 [P] [US4] Crear `offline/embedding_text.py`:
  - construcción de entradas como `EmbeddingPrefixes`;
  - truncado a 256 con L2 en doble precisión y redondeo a float32 con `struct`, como `EmbeddingMath` (research R5).
- [X] T037 [P] [US4] Crear `offline/golden/embedding_texts.jsonl` con 50 textos:
  - `q01`–`q25` de consulta y `d01`–`d25` de documento con cabecera `Documento › Sección`;
  - sintéticos en español y fragmentos del informe, sin documentos de usuarios;
  - longitudes variadas, siempre por debajo de 512 tokens con el prefijo.
- [X] T038 [US4] Crear `offline/gen_embedding_golden.py` (regla O3):
  - verifica el SHA-256 de `offline/models/embeddinggemma-2-text-270m.litertlm` contra la constante del catálogo;
  - `cache_dir` en `offline/build/litert-cache`, que crea si falta;
  - `Backend.CPU()`, `max_input_length=512` y `EmbeddingOptions(normalize=False)`;
  - escribe la cabecera `meta` y una línea por texto.

  Generar `offline/golden/embedding_golden.jsonl` y comprobar que una segunda ejecución da el mismo fichero.
- [X] T039 [US4] Ejecutar las pruebas Python y `EmbeddingGoldenFileTest`. Después, `EmbeddingGoldenTest` en el teléfono (quickstart §3), y anotar el mínimo y la media de CPU y de GPU para el informe. **Si la GPU queda por debajo de 0,99:** parar e informar al usuario (D-030, D-024).

**Checkpoint**: compatibilidad de embeddings demostrada en el teléfono de referencia (SC-002).

---

## Phase 7: User Story 5 — Protección del espacio vectorial (Priority: P2)

**Goal**: un índice incompatible o ilegible nunca devuelve resultados, expone el motivo y no se modifica; las migraciones por `schema_version` se aplican en orden y en una transacción.

**Independent Test**: crear un índice, alterar un metadato y reabrirlo; las búsquedas quedan bloqueadas y el motivo es consultable.

### Tests (escribir primero)

- [X] T040 [US5] `…/androidTest/data/rag/RagIndexCompatibilityTest.kt`, con `TempRagIndex` y `raw()`:
  - **Escenario 1:** índice recién creado, `Compatible`, y las búsquedas funcionan.
  - **Escenario 2 (SC-006):** con `raw()`, cambiar por turnos el SHA del modelo, `embedding_dims`, el prefijo de documento (quitando el espacio final), el de consulta y `normalizer_version`, y borrar una clave. Al reabrir:
    - `Incompatible(field, expected, found)` con el campo correcto;
    - todas las operaciones salvo `compatibility`, `open` y `close` lanzan `IndexUnavailableException`;
    - el SHA-256 de los ficheros `.db` y `-wal` no cambia (A2).
  - **Escenario 3:**
    - `schema_version = 2`: `Incompatible`, fichero intacto;
    - `schema_version = 0` con una migración inyectada desde 0 que crea una tabla marcador: migra, `schema_version = 1` y `Compatible`;
    - `schema_version = 0` sin migración: `Incompatible`, fichero intacto;
    - una migración con SQL erróneo: *rollback*, `Incompatible` y fichero intacto.
  - **Ilegible (A9):**
    - un fichero de bytes aleatorios da `Unreadable(motivo)`, sin cerrar la app y sin borrar el fichero;
    - una ruta de extensión errónea da `Unreadable(motivo)`.
  - **Escenario 4:** el motivo se obtiene de `compatibility.value` y de `IndexUnavailableException.state`.

### Implementación

- [X] T041 [US5] En `…/microrag/data/rag/DefaultRagIndex.kt`, la ruta `NeedsMigration(from)`:
  - aplica las migraciones en orden, con sus sentencias y la nueva `schema_version`, en una única transacción;
  - después vuelve a validar;
  - si una migración falla, hace *rollback* y publica `Incompatible("schema_version", …)`.
- [X] T042 [US5] En `…/microrag/data/rag/DefaultRagIndex.kt` y `RagConnection.kt`: `RagOpenException` (extensión que no carga, fichero que no es una base de datos o `vector_init` que falla) se convierte en `Unreadable(reason)`. Se cierra la conexión parcial y nunca se borra ni se modifica el fichero (lo deciden F07/F12).
- [X] T043 [US5] Ejecutar `RagIndexCompatibilityTest` en el emulador y en el teléfono.

**Checkpoint**: todas las historias completas.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: modo estricto, capas, medición, puertas de calidad, verificación del release y cierre.

- [X] T044 [P] Modo estricto en depuración (research R8):
  - `app/src/debug/java/com/lisdatasolutions/microrag/debug/DebugStrictMode.kt`: `ThreadPolicy` con `detectDiskReads`, `detectDiskWrites` y `penaltyLog`;
  - `app/src/release/java/com/lisdatasolutions/microrag/debug/DebugStrictMode.kt`: stub sin efecto;
  - llamada en `MicroRagApplication.onCreate()`, antes de `startKoin`.

  Arrancar la app en el emulador y anotar en el informe las violaciones ajenas a F04 que aparezcan. No se corrigen aquí.
- [X] T045 [P] `…/androidTest/data/rag/RagIndexStrictModeTest.kt` (SC-007):
  - en el hilo principal, instala una `ThreadPolicy` con `penaltyListener` que acumula las violaciones;
  - desde ese hilo, ejecuta todas las operaciones de `RagIndex`: apertura, documentos, lote, búsquedas, carga, vecino, borrado y recuentos;
  - exige 0 violaciones y restaura la política anterior.
- [X] T046 [P] Ampliar `…/test/architecture/LayeringTest.kt`: solo `…/microrag/data/rag/` importa `androidx.sqlite` (constitución II).
- [X] T047 [P] Completar `offline/README.md`:
  - entorno y enlace del modelo;
  - cómo regenerar los dos golden y cómo comprobarlos;
  - el determinismo;
  - un cambio del normalizador obliga a subir `VERSION` en los dos lados, regenerar y actualizar las huellas fijadas.
- [X] T048 `…/androidTest/data/rag/RagIndexBenchmarkTest.kt` (SC-008, research R9):
  - 10.000 fragmentos sintéticos (vectores unitarios con semilla fija y texto en español generado con una lista fija de palabras), en lotes de 64, registrando el tiempo total de inserción;
  - 100 búsquedas vectoriales (*k* = 50) y 100 léxicas;
  - p50 y p95 con `Percentiles` (source set `debug`), registrados como `MicroRAG benchmark …`.

  Ejecutarla en el teléfono y anotar las cifras.
- [X] T049 Puertas de calidad (quickstart §1–§3):
  - `./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:testDebugUnitTest`;
  - pruebas Python;
  - suite instrumentada completa en el emulador y en el teléfono, sin `RequiresModels` ni `RequiresNetwork`, incluida `NoTrafficInstrumentedTest`;
  - en el teléfono, además, `EmbeddingGoldenTest` y `RagIndexBenchmarkTest` (quickstart §3).

  Todo en verde.
- [X] T050 WorkManager con `androidx.sqlite` 2.7.1 en un dispositivo (riesgo R10-1): arrancar la AVD `MicroRAG_API33` y ejecutar `ModelDownloadSmokeTest` como en F03. Debe acabar en `SUCCESS` por la ruta WorkManager.
- [X] T051 Verificación del release (quickstart §4):
  - `apkanalyzer` muestra `lib/arm64-v8a/vector.so` y `libsqliteJni.so`;
  - `schema.sql` está en `assets/` y los golden no están en el APK;
  - los permisos siguen siendo los 9 de F03;
  - en el teléfono, `vector.so` aparece en `lib/arm64/`;
  - tamaño del APK y tamaño instalado frente a F03 (57,4 MB), con el coste de la extracción de nativas (research R2).
- [X] T052 Documentación de cierre:
  - `docs/roadmap/DECISIONS.md`: D-031, la extracción de nativas (`useLegacyPackaging`) para cargar `vector.so`, con su coste medido;
  - `specs/004-rag-data-core/report.md`: resumen, resultados de build, lint y tests, cifras con dispositivo y versiones (golden CPU y GPU, medición con 10k, tamaños; constitución VI), diferencias frente al plan y pendientes. Incluye cómo quedan cubiertos los casos límite sin prueba propia: disco lleno (el *rollback* de A4) y borrado durante una búsqueda (un solo hilo de base de datos);
  - estado de F04 en `docs/roadmap/FEATURES.md`;
  - tareas marcadas `[X]`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1)**: sin dependencias. **T003 decide si se sigue:** si `vector.so` no carga en el teléfono, se aplica el plan B antes de la fase 2.
- **Foundational (fase 2)**: depende de la fase 1. **Bloquea todas las historias.**
- **US1 (fase 3)**: depende de la fase 2.
- **US2 (fase 4)**: depende de US1, porque necesita documentos y fragmentos guardados.
- **US3 (fase 5)**: depende solo de la fase 2 (`SpanishText`). Es independiente de US1 y US2.
- **US4 (fase 6)**: depende solo de la fase 1 (`offline/`) y de F02 (`EmbeddingPrefixes`, `EmbeddingMath` y `LiteRtEngineFactory`). Es independiente del índice.
- **US5 (fase 7)**: depende de US1, porque necesita un índice con datos que alterar.
- **Polish (fase 8)**: depende de todas.

### User Story Dependencies

- **US1 → US2** y **US1 → US5** comparten `DefaultRagIndex.kt`, así que sus tareas de implementación van en secuencia.
- **US3** y **US4** no tocan `DefaultRagIndex.kt` y se pueden hacer en paralelo con US1, US2 y US5.

### Parallel Opportunities

- **Fase 2:** T005–T009 (tests) en paralelo. Después T010, T011, T013 y T014 en paralelo. T012, T015, T016 y T017 van en secuencia.
- **US3 ∥ US4:** T026–T028 y T033–T034 en paralelo. Después, T030 y T036–T037 en paralelo.
- **Polish:** T044, T045, T046 y T047 en paralelo.

---

## Parallel Example: Phase 2

```text
T005 SpanishTextTest        T006 RagIndexConfigTest
T007 IndexCompatibilityCheckerTest
T008 RagSchemaTest          T009 VectorBlobsTest
→ T010 domain/rag   T011 SpanishText   T013 schema.sql + RagSchema   T014 VectorBlobs
→ T012 config y checker → T015 RagConnection → T016 DefaultRagIndex (apertura) → T017 TempRagIndex
```

## Parallel Example: US3 ∥ US4

```text
T026 test_normalizer.py      T027 NormalizerGoldenTest      T028 NormalizerGoldenInstrumentedTest
T033 test_embedding_text.py  T034 EmbeddingGoldenFileTest
→ T029 normalizer.py   T030 normalizer_inputs.txt   T036 embedding_text.py   T037 embedding_texts.jsonl
→ T031 golden del normalizador   T038 golden de embeddings
```

---

## Implementation Strategy

### MVP primero (US1)

1. Fase 1, con la verificación temprana T003 en los dos dispositivos.
2. Fase 2.
3. US1: el índice guarda y borra datos de forma fiable.

### Entrega incremental

1. **US2:** búsquedas. Con US1, es lo que necesitan F06, F07 y F10.
2. **US3:** paridad del normalizador. Puede adelantarse en paralelo.
3. **US4:** golden de embeddings, validado en el teléfono.
4. **US5:** protección y migraciones.
5. **Polish:** modo estricto, capas, medición, puertas, release y cierre.

## Notes

- **Modelos del teléfono:** EmbeddingGemma 2 270M está en `no_backup/models` (F03). En el Mac hay copia en `$HOME/MicroRAG-models`.
- **Índice real:** ninguna prueba toca `no_backup/index/rag.db`; todas usan `TempRagIndex` en `cacheDir`.
- **Logs:** solo ids, recuentos y tiempos, con la etiqueta `MicroRAG`. Nunca textos de documentos ni consultas (A10).
- **Parada:** tras `/speckit-analyze` se espera la revisión del usuario antes de implementar.
