# Informe de cierre — F17 Carpetas y ámbito del chat

> **Estado:** borrador (T054). Falta la fase del teléfono (T055–T056) y el cierre (T057).

**Rama:** `017-folders-chat-scope`, creada desde `62f6ff2`.
**Fecha:** 2026-11-04 y 2026-11-05.
**Dispositivos:**
- emuladores `Pixel_10` (API 37) y `MicroRAG_API33` (API 33), y `MicroRAG_API32` (API 32) para el recorrido;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1), sin dependencias nuevas;
- esquemas: índice `rag.db` v3 → **v4** y `chat.db` v1 → **v2**;
- lint avisa de las dos versiones más nuevas de siempre, LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0. No se actualizan en F17.

## 1. Qué se ha implementado

**Datos** (research R1, R7):
- **`rag.db` v4:**
  - tabla `folders` con `AUTOINCREMENT` y `name_norm` único;
  - `documents.folder_id`, con `NULL` para «General» y `ON DELETE SET NULL`;
  - índice `idx_documents_folder`.

  La migración 3 → 4 deja todos los documentos en «General». Si falla, el fichero v3 queda intacto.
- **`chat.db` v2:**
  - `conversation_scope`: una fila por carpeta, «General» o documento, con su `created_at_ms`;
  - `last_scope`, con la misma forma.

  Las conversaciones de antes quedan con «Toda la biblioteca». Borrar una conversación borra su ámbito en cascada, y «Borrar historial» borra también el último ámbito.

**Búsqueda acotada** (R3, puerta T011):
- **Con un ámbito**, las búsquedas usan el modo en streaming de `vector_full_scan` (sin `k`) dentro de una CTE `MATERIALIZED`, con el filtro del ámbito y `ORDER BY … LIMIT k`. El FTS hace lo mismo con todas las coincidencias.
- **Con «Toda la biblioteca»**, el SQL de F10 no cambia ni una letra.
- **Puerta T011** en API 37 y API 33:
  - las dos formas del streaming existen en la 1.1.2: `vector_full_scan` sin `k` y `vector_full_scan_stream`, con 297 de 297 filas;
  - el top-k acotado coincide con el calculado a mano;
  - cada búsqueda escanea una sola vez (`EXPLAIN QUERY PLAN`).
- La carga de candidatos, los vecinos y los recuentos del chat (`chatLibraryState`) respetan el ámbito.

**Biblioteca** (K1–K4):
- **Raíz:** las carpetas por nombre plegado, con su recuento, «General» al final si tiene documentos, y «Nueva carpeta». La búsqueda de la raíz mira en todas las carpetas y cada fila dice su carpeta. Con la biblioteca vacía, el estado vacío y debajo las carpetas que haya.
- **Carpeta:** pantalla propia sin barra inferior, con su búsqueda («Buscar en Legal…»), los filtros de siempre, «＋» para importar a ella y «⋯» con «Renombrar carpeta» y «Eliminar carpeta». «General» no tiene «⋯». Carpeta vacía: «Esta carpeta está vacía» con «Importar a Legal».
- **Diálogos:**
  - nombre (K3), con el foco en el campo y los avisos de vacío, duplicado, reservado y 40 caracteres;
  - eliminar (K4): mover a «General» (por defecto) o eliminar también los documentos, con un segundo aviso y «Eliminando…».

  Borrar los documentos va uno a uno por la única vía de borrado; si alguno falla, la carpeta se queda con él y se avisa.

**Mover e importar** (K5, K6):
- **Detalle:** el dato «Carpeta» y «Mover a carpeta» en el «⋯», con su hoja. Solo cambia la carpeta.
- **Ingesta:**
  - la fila «Carpeta de destino», que empieza en la carpeta desde la que se abrió;
  - cada intento guarda su destino, también al reintentar;
  - si el destino se borra entretanto, el documento va a «General» y la pantalla lo dice;
  - cada fila de la cola dice su carpeta.
- **«Nueva carpeta…»** desde la hoja crea la carpeta y la aplica al momento (wireframe §4).

**Chat** (K7–K10):
- **Ficha** «Buscar en: …» encima del compositor, con los resúmenes de FR-014. Abre la hoja «Buscar en»:
  - «Toda la biblioteca», o carpetas enteras y documentos sueltos, con casillas de tres estados;
  - se despliega por tandas;
  - los excluidos aparecen en gris con «No incluido en RAG», y los pendientes con «Pendiente de indexar»;
  - desmarcar un documento de una carpeta marcada entera deja los demás como sueltos (D-072).
- **Sesión:**
  - cada pregunta resuelve el ámbito guardado contra lo que existe, con la guarda de D-065 para los documentos;
  - lo guarda con la conversación y como último ámbito;
  - busca solo en él, y una respuesta en curso conserva el ámbito con el que empezó (FR-019);
  - el reintento usa el ámbito de su conversación.
- **Disponibilidad:**
  - se calcula dentro del ámbito, con la precedencia de R8;
  - nuevo estado K9, «En este ámbito no hay documentos listos para buscar», con «Cambiar ámbito»;
  - avisos K10 una sola vez, porque el ámbito ajustado se reescribe.
- **«Nuevo chat»** empieza con el último ámbito usado, y Historial devuelve cada conversación con el suyo.

**Sistema de diseño:** `LisdataTextField`, `LisdataCheckRow`, `LisdataExpandableRow` y `LisdataScopeChip`, con vistas previas y entradas en el catálogo.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.128 de 1.128 (104 más que en F16) |
| Lint (`lintDebug`) | 0 errores. 2 avisos de versiones más nuevas (LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0) |
| Escritorio (Python) | 181 pruebas OK, con `schema_version == 4` |
| `Pixel_10` (API 37), instrumentadas | 909 pruebas (94 más que en F16), en 21,1 min. Un fallo de F17, corregido: `RagIndexWriteTest` no conocía la tabla `folders`. Repetición con los APK finales abajo |
| `MicroRAG_API33` (API 33), instrumentadas | 909 pruebas, en 22,1 min: los 4 fallos previos de Atrás desde la raíz, y el mismo `RagIndexWriteTest` |
| Repetición con los APK finales, en los dos emuladores | 349 de 349: `RagIndexWriteTest` y la accesibilidad de la Biblioteca, el detalle, la Ingesta y el chat |
| Teléfono A063 | *pendiente (T055)* |

**Pruebas nuevas o ampliadas:**
- **JVM:**
  - nuevas: `FolderNamesTest`, `FolderUseCasesTest`, `FoldersViewModelTest`, `FolderDialogsTest`, `FolderChoiceSheetTest`, `ChatSchemaTest`, `ScopeUseCasesTest`, `ScopeSelectorViewModelTest` y `ScopeSheetTest`;
  - ampliadas: `RagSchemaTest`, `RagIndexConfigTest`, `LibraryViewModelTest`, `LibraryScreenTest`, `DocumentViewModelTest`, `DocumentScreenTest`, `IngestViewModelTest`, `IngestScreenTest`, `DocumentImporterTest`, `DefaultImportQueueRepositoryTest`, `DefaultLibraryRepositoryTest`, `HybridRetrieverTest`, `DefaultChatSessionTest`, `ObserveChatAvailabilityUseCaseTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest`, `LayeringTest`, `AppModulesTest` y `DebugModulesTest`.
- **Instrumentadas:**
  - nuevas: `ScopedSearchTest` (puerta R3), `RagIndexFoldersTest`, `RagIndexChatStateTest`, `FolderNameDialogTest` y `FolderSheetsAccessibilityTest`;
  - ampliadas: `RagIndexMigrationTest` (3 → 4), `SqliteChatRepositoryTest` (1 → 2), `LibraryScreenAccessibilityTest`, `LibraryBenchmarkTest` y `RetrievalBenchmarkTest`.

## 3. Validación por criterio

| Criterio | Resultado |
|---|---|
| SC-001: actualizar no pierde nada y todo queda en «General» | ✅ `RagIndexMigrationTest` 3 → 4 con documentos, fragmentos e inclusiones variadas; `SqliteChatRepositoryTest` 1 → 2 |
| SC-002: nada fuera del ámbito, excluido o sin indexar | ✅ `ScopedSearchTest`: 0 resultados fuera, en vectorial y en FTS, con k = 5 y k = 50 |
| SC-003: «Toda la biblioteca» como antes | ✅ el SQL de F10 sin cambios; `ScopedSearchTest` y `RagIndexVisibilityTest` |
| SC-004: búsqueda acotada en el teléfono | *pendiente (T055)* |
| SC-005: el ámbito se conserva desde Historial, al girar y al cerrar | ✅ en JVM (`ChatViewModelTest`, `SqliteChatRepositoryTest`); en API 32, «Nuevo chat» tras cerrar la app sale con el último ámbito |
| SC-006: eliminar con «Mover» no borra nada; con «Eliminar», exactamente la carpeta | ✅ `RagIndexFoldersTest` y `FolderUseCasesTest` |
| SC-007: 1.000 documentos en 20 carpetas, carpetas en menos de 1 s | ✅ en emuladores, p95 de 0,32 ms (API 37) y 0,49 ms (API 33); *teléfono pendiente* |
| SC-008: accesibilidad, 200 %, horizontal, claro y oscuro | ✅ ATF en los dos emuladores: Biblioteca (raíz, búsqueda y carpeta), detalle, Ingesta con destino, chat con la ficha y K9, diálogos y hojas (`FolderSheetsAccessibilityTest`) y el catálogo; recorrido en oscuro, al 200 % y en horizontal |

## 4. Cifras medidas

**Biblioteca por carpetas** (`LibraryBenchmarkTest`, T050): 1.000 documentos en 20 carpetas y en «General».

| Medida | `Pixel_10` (API 37) | `MicroRAG_API33` | A063 |
|---|---|---|---|
| Lista de carpetas | p50 0,28 ms · p95 0,32 ms | p50 0,34 ms · p95 0,49 ms | *pendiente* |
| Primera tanda de una carpeta, con su recuento | p50 0,29 ms · p95 0,38 ms | p50 0,32 ms · p95 0,41 ms | *pendiente* |

Las cifras de F08 (página y tandas de toda la biblioteca) no cambian: primera página con recuento en p95 1,29 ms en API 37 con 50.000 fragmentos.

**Búsqueda acotada** (`RetrievalBenchmarkTest`, T049): necesita el modelo de embeddings, así que solo se ejecuta en el teléfono (T055). Mide la búsqueda completa con «Toda la biblioteca», con una carpeta de 1.000 fragmentos y con 5 documentos sueltos sobre 10.000 fragmentos, y compara el streaming con top-(k + m). *Pendiente.*

### Recorrido en el emulador (T053)

En `MicroRAG_API32` con documentos de prueba TXT, y el inglés en `Pixel_10`. El emulador no tiene los modelos, así que el chat muestra «Configuración pendiente» y no responde: las respuestas por ámbito quedan para el teléfono.

1. **Biblioteca vacía:** el estado vacío y debajo «Nueva carpeta» (D-072).
2. **Carpetas:** «Legal» y «Motores» creadas. «general» da «Ese nombre está reservado» y «LEGAL», «Ya hay una carpeta con ese nombre». El foco empieza en el campo.
3. **Importar a una carpeta:** desde «Legal», la Ingesta sale con «Carpeta de destino: Legal». Dos documentos van a «Legal»; con el destino cambiado a «General» en la hoja («Elegir»), otros dos van a «General». Cada fila dice su carpeta.
4. **Mover:** desde el detalle, «Mover a carpeta» lleva un documento a «Motores» y a «Legal», con «Movido a «…»» y el dato «Carpeta» al día.
5. **Eliminar:** «Motores» con «Mover su documento a «General»», que vuelve a «General», y «Legal» con «Eliminar también sus 2 documentos», con el segundo aviso; las dos con «Carpeta eliminada».
6. **Chat:** la ficha «Buscar en: Toda la biblioteca»; la hoja con «Contratos» desplegada, su documento «Pendiente de indexar», marcada y aplicada: «Buscar en: Contratos». Tras cerrar la app, «Nuevo chat» sale con «Contratos».
7. **Oscuro y 200 %:** Biblioteca y chat sin cortes. **Horizontal:** la ficha sobre el compositor y la hoja desplazable con sus botones.
8. **Inglés** (`Pixel_10`): «Library», «FOLDERS», «New folder», «Rename folder», «Import into Legal», «Destination folder», «Search in: Whole library».
9. **Limpieza:** carpetas, documentos y ficheros de prueba borrados en los dos emuladores, y el idioma por app de `Pixel_10` vacío, como estaba.

## 5. Revisión del inglés (T048)

- **Corregidos:**
  - `library_folder_import`: «Import to %s» → «Import into %s»;
  - `folder_error_too_long`: «40 characters at most» → «Up to 40 characters»;
  - `ingest_destination_missing`: «It was imported into General.» → «Imports go to General.». Dice lo que pasa a partir de ese momento; el español no cambia.
- **`StringsParityTest`** pasa.

## 6. Diferencias frente a los PNG y al wireframe

- **PNG 04 (chat):** la ficha del ámbito ocupa unos 48 dp encima del compositor, y el compositor cede su margen superior. En vertical, la pregunta de la muestra de PNG 04 queda justo encima del borde, porque el chat abre en la última respuesta. Con poco alto y con conversación (horizontal, T064), la ficha va al final de la lista, como los avisos.
- **PNG 06 (Biblioteca):** su lista vive ahora dentro de una carpeta, con el nombre arriba y sin el titular. La raíz es la de K1.
- **PNG 07 (detalle):** el dato «Carpeta» va debajo de las acciones, para que PNG 07 siga mostrándolas sin desplazar.
- **Wireframe §6:** el chevron de la ficha y de las carpetas es el `ChevronRight` girado, sin iconos nuevos.

## 7. Desviaciones respecto al plan

- **Ubicación de tipos:** `SearchScope`, `Folder`, `FolderList`, `LibraryFolderFilter`, `DocumentRef` y `FolderRef` viven en `domain/rag` y no en `domain/library` o `domain/chat`, para que `domain/rag` no dependa de las capas que lo usan. `ChatScope` y `ResolvedScope` siguen en `domain/chat`.
- **`FoldersViewModel`** está en `feature/folders` y no en `feature/library`: lo usan la Biblioteca, el detalle y la Ingesta para «Nueva carpeta…».
- **`FolderResult.Invalid`** lleva el motivo, y existe `FolderDeletionResult` con los documentos que fallaron.
- **`Folder` y `FolderList`** llevan también los documentos incluidos, para que el selector sepa qué carpeta no se puede marcar.
- **El diálogo de nombre (K3)** se prueba en el dispositivo (`FolderNameDialogTest`): en Robolectric, un `OutlinedTextField` dentro de un `AlertDialog` nunca llega al reposo, y en el dispositivo funciona. Los demás diálogos se prueban en la JVM.
- **El destino borrado (M3):** el aviso sale en la fila de destino de la Ingesta, que vuelve a «General»; el documento que se registra con una carpeta que ya no existe queda en «General» sin error.
- **La cola** dice la carpeta de cada fila, también «General», como pide el wireframe §5.

## 8. Fallos encontrados y corregidos

- **Los avisos de una sola vez se cancelaban** (recorrido): al consumir el mensaje se cancelaba el `showSnackbar` que lo mostraba, y «Movido a «…»» no llegaba a verse. Los avisos de carpeta y de ámbito tenían el mismo fallo. Ahora siguen una copia, como los de la Biblioteca de F08, con `LisdataSnackbarOnce`, y una prueba lo cubre.
- **TalkBack no decía la carpeta** de las filas de la cola ni de la búsqueda de la raíz (recorrido). Ahora la dice.
- **La hoja «Buscar en» sin carpetas** mostraba el encabezado «CARPETAS» vacío. Ya no lo muestra.
- **`RagIndexWriteTest`** esperaba los objetos del esquema v3.

- **Grafo de Koin de la navegación de prueba** sin `ScopeSelectorViewModel`: el chat se quedaba sin llegar al reposo en Robolectric en lugar de fallar. Se añadió la definición.
- **Una carpeta recién creada como destino** podía volver a «General», porque la lista de carpetas aún no la tenía. La comprobación se hace ahora solo al llegar una lista nueva.
- **El foco del diálogo de nombre** no llegaba al campo, porque se pedía antes de que existiera la ventana del diálogo. Se pide desde su contenido.

## 9. Pendiente

- T055–T056: fase del teléfono, con el recorrido acordado antes con el usuario.
- T057: cierre.
