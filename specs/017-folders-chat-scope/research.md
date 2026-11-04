# Research: Carpetas y ámbito del chat (F17)

**Fecha:** 2026-11-04
**Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)

Cada punto sigue el formato **Decisión / Motivo / Alternativas**. Lo verificado lleva su fuente; lo que se verifica durante la implementación tiene su tarea.

## R1 — Carpetas en `rag.db` (esquema v4)

- **Decisión:**

  ```sql
  CREATE TABLE IF NOT EXISTS folders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      name_norm TEXT NOT NULL UNIQUE,
      created_at_ms INTEGER NOT NULL,
      updated_at_ms INTEGER NOT NULL
  );
  -- documents gana:
  folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL
  CREATE INDEX IF NOT EXISTS idx_documents_folder ON documents(folder_id, created_at_ms, id);
  ```

  - `name_norm = DocumentNames.fold(name.trim())`, el mismo plegado de mayúsculas y tildes que la búsqueda por nombre de F08. La unicidad la garantiza la base (`UNIQUE`), y el dominio traduce la violación a «Ya hay una carpeta con ese nombre».
  - La migración 3 → 4 tiene tres sentencias: `CREATE TABLE folders`, `ALTER TABLE documents ADD COLUMN folder_id …` y el índice. Todos los documentos quedan con `folder_id = NULL`, es decir, en «Sin carpeta» (FR-002).
  - Pasan a la versión 4: `RagIndexConfig.SCHEMA_VERSION = 4` y `schema.sql`.
- **Verificado en el código:**
  - `RagMigrations.all` y `DefaultRagIndex.migrate` aplican cada paso en una transacción y dejan el fichero intacto si alguno falla;
  - `RagSchemaTest.everyMigration_endsWhereSchemaSqlIs` solo admite hoy `ALTER TABLE documents ADD COLUMN` y `CREATE INDEX`, así que se amplía a `CREATE TABLE IF NOT EXISTS folders`;
  - `offline/eval/index.py` ejecuta `schema.sql` e inserta documentos con columnas explícitas; `folder_id` es opcional, así que no hay que tocarlo;
  - `offline/tests/test_appconfig.py` comprueba `schema_version == 3` y pasa a 4.
- **Motivo:**
  - `AUTOINCREMENT` impide que una carpeta borrada preste su id a otra, así que un ámbito guardado nunca apunta a una carpeta distinta (US5, edge case de la carpeta recreada);
  - `ON DELETE SET NULL` es la red de seguridad, aunque el borrado mueve los documentos de forma explícita (R4);
  - el índice `(folder_id, created_at_ms, id)` sirve a la paginación por carpeta y a los recuentos.
- **Alternativas descartadas:**
  - una tabla de relación documento–carpeta: D-066 fija una carpeta por documento;
  - guardar la carpeta solo en `chat.db`: la Biblioteca y la recuperación la necesitan en `rag.db`.

## R2 — Reindexado y copias temporales

- **Verificado:**
  - `publishReindex` conserva la fila del documento original y le pasa los fragmentos de la copia (`DefaultRagIndex.kt:188`), así que la carpeta se mantiene sin hacer nada;
  - la copia (`replaces_id`) nunca sale en la Biblioteca.
- **Decisión:** la copia temporal se crea con `folder_id = NULL` y no cuenta en ningún recuento (`replaces_id IS NULL` en los agregados).

## R3 — Búsqueda acotada exacta: el modo en streaming de SQLite-Vector

- **Decisión:**
  - **Con «Toda la biblioteca»,** la búsqueda sigue exactamente como hoy: `vector_full_scan(…, k + m)` y FTS con `LIMIT k + m` (FR-017, SC-003).
  - **Con un ámbito,** se usa el modo en streaming de `vector_full_scan`, que es el mismo escaneo sin `k`:

    ```sql
    SELECT v.rowid, v.distance FROM vector_full_scan('chunks', 'embedding', ?) v
      JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id
     WHERE d.status = 'done' AND d.included = 1 AND (d.folder_id IN (…) OR d.id IN (…))
     ORDER BY v.distance LIMIT ?
    ```

    Y en FTS, `chunks_fts MATCH ?` con el mismo `JOIN`/`WHERE` y `ORDER BY bm25(chunks_fts, 3.0, 1.0) LIMIT ?`.
  - La carga de candidatos (`loadChunks`) y los vecinos (`neighbor`) reciben el ámbito y aplican el mismo filtro.
- **Verificado:**
  - `vector.so` 1.1.2 contiene el módulo `vector_full_scan_stream` (`strings`);
  - la API publicada de SQLite-Vector dice: «When [k is] omitted, the module operates in streaming mode … rows are returned progressively as they are scanned, enabling standard SQL clauses such as `WHERE` and `LIMIT`», con un ejemplo de `JOIN` + `WHERE` + `LIMIT`.
- **Se verifica antes de construir encima** (puerta T009, constitución II): una prueba instrumentada sobre un índice temporal comprueba que el modo existe en 1.1.2 y que el top-k filtrado coincide con el calculado a mano (todas las distancias, filtrar y ordenar). Si no coincide, se para y se consulta.
- **Motivo:**
  - **Exactitud** (FR-018): el `WHERE` actúa sobre todas las filas, sin depender de cuántos candidatos se piden;
  - **Coste:** la búsqueda exacta ya calcula todas las distancias, y solo añade el `JOIN` y el filtro de N filas. Se mide (US7, R11);
  - con el método de hoy, top-(k + m), un ámbito pequeño hace que m se acerque a N, y devolvería casi todas las filas igual, pero ordenadas antes.
- **Alternativas descartadas:**
  - top-(k + m) también con ámbito: funciona, pero la complejidad es la misma, sin ventaja;
  - una tabla de vectores por carpeta: duplica datos y complica mover documentos;
  - filtrar después de un top-k fijo: no es exacto.

## R4 — Eliminar una carpeta

- **Decisión** (D-070):
  - **«Mover a Sin carpeta»:** en una transacción, `UPDATE documents SET folder_id = NULL WHERE folder_id = ?` y `DELETE FROM folders WHERE id = ?`;
  - **«Eliminar también los documentos»:** `LibraryRepository.delete(documentId)` documento a documento, por el mismo camino de F08 (copia, fragmentos, vectores, FTS por trigger, la tanda de indexación y su copia temporal). Después se borra la carpeta, solo si ya no le quedan documentos. Si alguno falla, la carpeta se queda con los que no se pudieron borrar y se avisa (FR-005).
- **Verificado:**
  - `DefaultLibraryRepository.delete` quita el documento de la tanda (`runStore.edit { it.without(…) }`), borra su copia temporal en cascada y deja la copia privada para la limpieza de huérfanos si no puede borrarla;
  - no detiene ninguna respuesta del chat. Las fuentes de una respuesta sobre un documento borrado se resuelven como «Fuente ya no disponible» (F13).
- **Corrección de la spec:** el caso límite «Eliminar una carpeta con sus documentos mientras una respuesta los usa» decía que la respuesta se detiene. Lo que pasa hoy al eliminar un documento es que la respuesta sigue y sus fuentes quedan como «Fuente ya no disponible», y F17 hace lo mismo (D-071).

## R5 — Biblioteca: raíz con carpetas y lista por carpeta

- **Decisión:**
  - **Raíz** (pestaña `Library`): titular, búsqueda y carpetas (K1). Sin texto en la búsqueda se ven las carpetas; con texto, los documentos de todas, con la carpeta en su línea de datos.
  - **Carpeta** (ruta nueva `LibraryFolder(folderId: Long?)`, con `null` para «Sin carpeta»): la lista de hoy, acotada a esa carpeta (K2).
  - **El mismo `LibraryViewModel`** sirve para las dos listas, con un `LibraryFolderFilter` (`All`, `Unfiled` o `Folder(id)`) que viene de la ruta. La raíz usa `All` para los resultados de la búsqueda.
  - **`FoldersViewModel`** lleva la lista de carpetas y las acciones de crear, renombrar y eliminar.
- **Consultas:**
  - `libraryPage(text, statuses, limit, folder)`, con `AND d.folder_id = ?`, `IS NULL` o nada;
  - `folders()`: `SELECT f.id, f.name, COUNT(d.id) FROM folders f LEFT JOIN documents d ON d.folder_id = f.id AND d.replaces_id IS NULL GROUP BY f.id ORDER BY f.name_norm`, más el recuento de los de `folder_id IS NULL`;
  - `LibraryEntry` gana `folderName` para la línea de datos de los resultados.
- **Orden:** por `name_norm`, el nombre sin mayúsculas ni tildes, que en español y en inglés da el orden alfabético esperado. Así no hace falta un colador por idioma en SQL.
- **Motivo:** reutiliza la lista de F08 con sus estados, la paginación y la conservación de la búsqueda (FR-007, FR-009).

## R6 — Mover un documento y destino al importar

- **Decisión:**
  - `RagIndex.moveDocument(documentId, folderId?)` hace solo `UPDATE documents SET folder_id = ?, updated_at_ms = ?`. No toca el estado, la inclusión ni los fragmentos (FR-010).
  - **Importar:** `ImportDocumentsUseCase(uris, folderId)` → `DocumentImporter` → `NewDocument.folderId`. La ruta pasa a `Ingest(folderId: Long? = null)`, y la carpeta de destino la guarda el `SavedStateHandle` del `IngestViewModel`.
  - **Cola:** cada fila lee la carpeta de su documento, que ya está registrado.
  - **Duplicados:** se detectan antes de registrar, como hoy, y no cambian de carpeta (FR-012).
- **Hoja de carpetas** (K5, K6): un composable `FolderChoiceSheet` en un paquete nuevo `feature/folders`, que usan el detalle y la Ingesta. Lleva las carpetas como opciones de radio, «Sin carpeta» y «Nueva carpeta…».

## R7 — Ámbito en `chat.db` (esquema v2)

- **Decisión:**

  ```sql
  CREATE TABLE IF NOT EXISTS conversation_scope (
      conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
      kind TEXT NOT NULL CHECK (kind IN ('folder', 'document')),
      ref_id INTEGER NOT NULL,
      ref_created_at_ms INTEGER,           -- solo documentos: la guarda de D-065
      PRIMARY KEY (conversation_id, kind, ref_id)
  );
  CREATE TABLE IF NOT EXISTS last_scope (
      kind TEXT NOT NULL CHECK (kind IN ('folder', 'document')),
      ref_id INTEGER NOT NULL,
      ref_created_at_ms INTEGER,
      PRIMARY KEY (kind, ref_id)
  );
  ```

  - **«Toda la biblioteca»** es no tener filas. Las conversaciones de antes no tienen ninguna, y eso cumple FR-020.
  - **Documentos:** se guarda con su `created_at_ms`. Al resolver el ámbito, un documento con ese id pero creado después no cuenta (D-065, FR-021).
  - **Carpetas:** como sus ids no se reutilizan (R1), no necesitan guarda.
  - **Versión:** `ChatSchema.VERSION = 2`, y `ChatMigrations` pasa a tener su primer paso.
- **Motivo:**
  - los ids de `rag.db` no pueden ser claves ajenas en otro fichero, como ya pasa con `sources`;
  - una fila por elemento deja leer y ajustar el ámbito sin interpretar texto.
- **Alternativas descartadas:**
  - una columna JSON en `conversations`: obliga a migrar la tabla y a interpretar texto;
  - el último ámbito en DataStore: tendría que repetir la guarda de ids en otro sitio.

## R8 — La sesión del chat y el ámbito

- **Decisión:**
  - `ChatSession.ask(conversationId, question, scope)`: al crear la conversación guarda su ámbito; si existía y el ámbito cambió, lo actualiza. En los dos casos guarda `last_scope`. El ámbito con el que empieza la respuesta es el que usa su recuperación (FR-019).
  - `ChatSession.setScope(conversationId, scope)` para aplicarlo con el selector, y `scopeOf(conversationId)`.
  - `RetrievalRequest(question, previousQuestion, scope: ResolvedScope)`.
  - `ResolveScopeUseCase` traduce el ámbito guardado a lo que existe ahora, con la guarda de D-065, y devuelve también lo que cayó para el aviso de K10.
- **ViewModel del chat:**
  - un chat sin preguntas guarda el ámbito en su `SavedStateHandle`, y empieza con el último usado (FR-020b);
  - al abrir una conversación, se resuelve su ámbito y se dan los avisos de K10;
  - la ficha (K7) recibe el resumen del ámbito.
- **Selector** (K8): `ScopeSelectorViewModel` en `feature/chat/scope`. Lee las carpetas con sus recuentos y, al desplegar una, sus documentos por tandas de 50. Guarda la selección en su `SavedStateHandle` y devuelve el ámbito elegido a la ruta del chat.
- **Disponibilidad:** `chatLibraryState(scope)` cuenta dentro del ámbito, y `ChatAvailability` gana `NothingInScope` (K9). La cobertura parcial también se calcula dentro del ámbito.

## R9 — Piezas nuevas del design system

- **Decisión:** en `core/designsystem/component`:
  - **`LisdataTextField`:** un `OutlinedTextField` con los tokens de `LisdataSearchField`, etiqueta, texto de error y límite de caracteres (K3);
  - **`LisdataCheckRow`:** fila con casilla `TriStateCheckbox`, título, subtítulo, estado desactivado y semántica de casilla con «algunos documentos» (K8);
  - **`LisdataExpandableRow`:** la fila de carpeta del selector, con casilla, recuento y un botón de desplegar de 48 dp con `stateDescription` «desplegada» o «plegada»;
  - **`LisdataScopeChip`:** icono, texto con `maxLines = 1` y elipsis, y «▾» (K7).

  Cada una con sus vistas previas `@LisdataPreviews` y su entrada en el catálogo de depuración.
- **Motivo:** no existen. Son las tres piezas nuevas que aprobó el wireframe, más la ficha, que es una variante de las fichas de hoy con su propia semántica.

## R10 — Avisos y estados

- **Decisión:**
  - **Avisos K10:** los emite el `ChatViewModel` al abrir una conversación o un «Nuevo chat» cuyo ámbito cambió, con un `SnackbarHost` y región en vivo, como los avisos de F13.
  - **K9:** un estado vacío nuevo del chat, con «Cambiar ámbito», que abre el selector.
  - **Biblioteca:** «Carpeta eliminada», «Movido a «Motores»», los errores de guardado y «No se pudieron eliminar algunos documentos», en el `SnackbarHost` de cada pantalla.

## R11 — Rendimiento y medida (US7)

- **Decisión:** se amplía `RetrievalBenchmarkTest` (F10) con dos variantes sobre el mismo índice de 10.000 fragmentos sintéticos:
  - un ámbito de una carpeta con unos 1.000 fragmentos;
  - un ámbito de 5 documentos sueltos.

  Mide el p50 y el p95 de la búsqueda completa frente a «Toda la biblioteca», en el emulador y en el teléfono (última fase). El objetivo son 200 ms de p95 (F10). Si se superan, se anota para F14 (FR-026).
- **Además:**
  - `RagIndexLibraryTest` añade 1.000 documentos en 20 carpetas y mide la lista de carpetas (SC-007, objetivo < 1 s);
  - no hay índices nuevos sobre `chunks`: el filtro va por `documents`.

## R12 — Privacidad y registros

- **Decisión:** los registros llevan solo ids, recuentos y tiempos («folder create id=7», «scope docs=3 folders=1»), nunca nombres de carpeta ni de documento (FR-025).
- **Verificación:** una prueba JVM con `RecordingLogger` en los casos de uso de carpetas y en la sesión.

## R13 — Pruebas

- **JVM:**
  - **dominio:**
    - `FolderNamesTest`: plegado, vacío, 40 caracteres y reservados en los dos idiomas;
    - los casos de uso de carpetas, de mover, del ámbito (resolución, guarda de D-065, último ámbito) y de la disponibilidad dentro del ámbito;
  - **datos:**
    - `RagSchemaTest`;
    - `ChatSchema` y la migración v1 → v2 en `SqliteChatRepositoryTest` (Robolectric, si se puede; si no, instrumentada);
  - **ViewModels y pantallas:** Biblioteca raíz, carpeta, diálogos, detalle con «Mover», Ingesta con destino, ficha y selector, y el chat con su ámbito;
  - **resto:** `NavigationTest`, `StringsParityTest`, `LayeringTest` (`feature/folders`), `AppModulesTest` y `DesignSystemConformanceTest`.
- **Instrumentadas:**
  - `RagIndexMigrationTest` 3 → 4, con datos;
  - `RagIndexFoldersTest`: crear, renombrar, unicidad, mover, eliminar con mover y eliminar con borrar;
  - **`ScopedSearchTest` (puerta R3):** el modo en streaming, el top-k acotado frente al calculado a mano, y «Toda la biblioteca» idéntica a hoy (SC-002, SC-003);
  - `HybridRetrieverVisibilityTest`, ampliada con el ámbito;
  - `SqliteChatRepositoryTest`, con la migración 1 → 2 y el ámbito;
  - ATF de las pantallas nuevas y cambiadas, en claro y en oscuro, al 200 % y en horizontal (SC-008);
  - `RetrievalBenchmarkTest`, con ámbitos (R11).
- **Teléfono:** batería, medida y recorrido con carpetas y documentos de prueba, en la última fase.

## R14 — Versiones

- **Decisión:** ninguna dependencia nueva.
  - SQLite-Vector se queda en 1.1.2 (el modo en streaming ya está en esta versión, R3);
  - DataStore, Compose BOM, Navigation, Koin, LiteRT-LM y Robolectric, sin cambios;
  - `TriStateCheckbox` está en Material 3 del BOM actual.
