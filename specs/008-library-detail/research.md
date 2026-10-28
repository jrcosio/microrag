# Research: Biblioteca y detalle de documento (F08)

Fecha: 2026-10-28. Todas las decisiones parten del código actual (F04–F07) y de las fuentes citadas. No hay dependencias nuevas.

## R1. Esquema v3: dos columnas nuevas, sin reconstruir tablas

**Decisión:** migración `from = 2` con cuatro sentencias:

```sql
ALTER TABLE documents ADD COLUMN name_norm TEXT;
ALTER TABLE documents ADD COLUMN replaces_id INTEGER REFERENCES documents(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at_ms, id);
CREATE INDEX IF NOT EXISTS idx_documents_replaces ON documents(replaces_id);
```

El cuarto índice se añadió al implementar (T003): sin él, cada borrado de un documento recorre `documents` buscando sombras para la cascada, y los dos `EXISTS` de cada fila de la Biblioteca (L1) también. Con 1.000 filas y la lista entera cargada, serían un millón de filas por consulta.

`schema.sql` describe ya la v3 para los índices nuevos, y `RagIndexConfig.SCHEMA_VERSION` pasa a 3.

- **`name_norm`:** el nombre plegado para buscar (R3). Al abrir un índice migrado, se rellena en Kotlin para las filas que lo tienen a NULL, en una transacción e idempotente. Las migraciones solo admiten SQL, y SQLite no sabe quitar acentos.
- **`replaces_id`:** une un documento sombra con el documento que reindexa (R5). Con `ON DELETE CASCADE`, borrar el original borra la sombra y, en cascada, sus fragmentos.

**Verificado** (sqlite.org/lang_altertable, 2026-10-28):
- `ADD COLUMN` admite una columna con `REFERENCES` si su valor por defecto es NULL («If foreign key constraints are enabled and a column with a REFERENCES clause is added, the column must have a default value of NULL»).
- Cambiar un `CHECK` exige reconstruir la tabla (el procedimiento de 12 pasos), con `chunks` apuntando a `documents`. Por eso no se toca el `CHECK` de `status` (R4).
- La app activa `PRAGMA foreign_keys = ON` en cada conexión (`RagConnection`), así que la cascada funciona.

**`offline/`:** lee `schema.sql` y `SCHEMA_VERSION` del código de la app. `test_appconfig` pasa a esperar la v3, y los índices de evaluación se reconstruyen, como en F06. Las columnas nuevas quedan a NULL en el escritorio, porque la evaluación no las usa.

**Alternativas descartadas:**
- **Buscar en memoria:** cargar todos los documentos y filtrar en Kotlin. Funciona con 1.000, pero no pagina en SQL, como piden el `.md` y FEATURES.
- **Marcar la sombra en `content_hash` o en `uri`:** sin cascada al borrar y frágil.
- **Reconstruir `documents`** para añadir `out_of_index` al `CHECK`: riesgo innecesario.

## R2. Lista paginada sin Paging 3

**Decisión:** paginación por bloques de 50 con un límite que crece:
- el ViewModel pide `limit = 50` y, al llegar al final de la lista, `limit + 50`;
- el repositorio vuelve a consultar con el límite actual cada vez que cambia el índice (`documentChanges`), la búsqueda o el filtro;
- consulta:

  ```sql
  SELECT … FROM documents d
  WHERE d.replaces_id IS NULL AND <filtro> AND <búsqueda>
  ORDER BY d.created_at_ms DESC, d.id DESC
  LIMIT ?
  ```

- un `COUNT(*)` con los mismos filtros, para «N DOCUMENTOS» y para saber si quedan más.

**Motivo:**
- las filas de `documents` son ligeras y nunca tocan `chunks` (FR-001);
- 1.000 filas se leen en milisegundos, y releer las cargadas al cambiar algo es más simple y robusto que invalidar páginas;
- la lista se mantiene coherente con borrados e indexados sin lógica de reconciliación.

T022 mide en el teléfono la primera página, cada bloque siguiente y la lista completa con 1.000 documentos sintéticos (SC-001).

**Alternativas descartadas:**
- **Paging 3** (`androidx.paging`): dependencia nueva para un problema que una consulta con `LIMIT` resuelve. Habría que verificar su versión y su integración con el driver de SQLite sin Room.
- **Keyset puro:** más eficiente con 100.000 filas, pero complica la reactividad, y no es la escala del MVP.

## R3. Búsqueda por nombre sin mayúsculas ni acentos

**Decisión:** `DocumentNames.fold(name)`, una función pura:
1. `java.text.Normalizer.normalize(name, NFD)`;
2. quita las marcas combinantes (`\p{Mn}`);
3. pasa a minúsculas con `Locale.ROOT`.

`name_norm` se guarda al crear el documento, también la sombra. La consulta usa `d.name_norm LIKE ? ESCAPE '\'` con `%texto%`, con el texto plegado igual y con `%`, `_` y `\` escapados (FR-003).

**No se usa `SpanishText`:** el normalizador del FTS quita sufijos («archivo anual» → «archiv anual»). Para buscar un nombre, el usuario espera subcadenas literales.

## R4. «Fuera del índice» reutiliza `cancelled`

**Decisión:** el estado de D-043 se guarda como `cancelled`, un valor que el `CHECK` del esquema ya admite y que nadie usa (D-030). En el dominio se llama `DocumentStatus.OUT_OF_INDEX`; el enum pasa de `CANCELLED` a `OUT_OF_INDEX("cancelled")`, con la explicación en su KDoc.

**Verificado en el código:**
- la cola de Ingesta solo lista `pending`, `parsed`, `embedding` y `error` (`QUEUE_STATUSES`);
- la tanda y «Iniciar indexación» solo toman `pending`, `parsed` y `embedding` (`UNFINISHED`, en el runner y en el repositorio);
- un documento `cancelled` ya queda fuera de la cola y de toda tanda, como pide D-043, sin tocar F06 ni F07;
- las búsquedas lo excluyen porque no es `done`.

## R5. Reindexar con un documento sombra y sustitución atómica

**Decisión.** Según el estado del documento:
- **Indexado (`done`):** `reindex(id)` crea una **sombra**: una fila nueva de `documents` con el mismo nombre, tipo, tamaño y copia privada, `replaces_id = id` y `content_hash = <hash>#reindex` (único y que no choca con la detección de duplicados de F06).
  - La sombra entra en la tanda de F07 como cualquier documento: se trocea, se embebe, sobrevive a muertes y pausas, y aparece en el Estado de indexación.
  - Al cerrarla, el runner llama a `RagIndex.publishReindex(shadowId)` en lugar de pasarla a `done`. Esa llamada hace en **una transacción**:
    1. borrar los fragmentos del original (el trigger limpia el FTS);
    2. `UPDATE chunks SET document_id = original WHERE document_id = shadow`;
    3. actualizar el original (`chunk_count`, `page_count`, `skipped_pages`, `updated_at_ms`; sigue `done` y conserva `included`);
    4. borrar la sombra;
    5. marcar `quant_dirty`.
- **Con error reintentable:** el reintento de F07 (`IndexingRepository.retry`).
- **Fuera del índice:** vuelve a `pending`, entra en la tanda y se encola.

**Por qué funciona:**
- **Visibilidad:** la sombra no es `done`, así que las búsquedas no la ven y la cuentan en *m* (D-040). El original sigue visible hasta el `COMMIT`, sin mezcla ni hueco (SC-006).
- **Mover filas:** los fragmentos mantienen su `id`, así que el FTS (`rowid`) y los vectores no se recalculan. El trigger `chunks_au` solo salta si cambian `title_norm` o `body_norm`.
- **Reanudación:** se hace sobre la sombra, con la lógica de F07 tal cual (`INSERT OR IGNORE` por `(document_id, ordinal)`).
- **Si falla, se pausa o se queda sin espacio:** el original queda intacto. La sombra con error aparece en el Estado de indexación (P6) y en el detalle del original («No se pudo reindexar»), con «Reintentar» y «Descartar».

**Coherencia:**
- la cola de Ingesta y la Biblioteca ocultan las sombras (`replaces_id IS NULL`);
- borrar el original borra la sombra en cascada y saca las dos de la tanda;
- «Quitar del índice» borra también la sombra;
- un segundo «Reindexar» con una sombra viva no hace nada (la acción se muestra como «Reindexando…»).

**Espacio:** durante el reindexado conviven las dos versiones del documento. El runner ya comprueba el espacio libre antes de cada lote y pausa con «Sin espacio» sin publicar (D-040), así que el original nunca se pierde.

**Alternativas descartadas:**
- **Ordinales negativos en el mismo documento:** obliga a cambiar las búsquedas, los recuentos y `neighbor` para ocultarlos.
- **Borrar y volver a indexar:** el documento desaparece de las respuestas durante minutos (contra FEATURES F08 criterio 6).
- **Tabla de preparación aparte:** duplica la lógica de inserción y reanudación de F07.

## R6. Eliminar y quitar del índice: atómicos y coherentes con F06 y F07

**Eliminar.** Lo hace `DeleteDocumentUseCase`, que también usa «Quitar de la cola» de la Ingesta:
1. `RagIndex.deleteDocument(id)`, en una transacción:
   - borra la fila; la cascada borra la sombra, los fragmentos, los vectores y, por trigger, el FTS;
   - marca `quant_dirty` (hoy no lo hace, verificado);
2. `runStore.edit { without(id, shadowId) }`;
3. borra la copia privada, bajo el `fileLock` de F06.

Si el proceso muere tras el paso 1, la copia queda huérfana y la borra la limpieza de huérfanos de F06. Esa limpieza se ejecuta hoy al abrir la Ingesta; pasa a ejecutarse también la primera vez que se observa la Biblioteca (FR-022).

**Quitar del índice.** `RagIndex.removeFromIndex(id)`, en una transacción:
1. borra las sombras;
2. borra los fragmentos del documento;
3. pone `status = 'cancelled'`, `chunk_count = NULL`, `error = NULL` y conserva `included`;
4. marca `quant_dirty`.

Después, la tanda pierde el id. Si el proceso muere entre los dos pasos, el id queda en la tanda pero no es `UNFINISHED`, así que nadie lo procesa.

**Durante la indexación:** el runner de F07 ya trata un documento que desaparece o cambia de estado a mitad (`DocumentGone`, «removed midway»). Se añade a sus pruebas el caso de una sombra cuyo original se borra.

**Coste:** borrar 4.300 fragmentos dispara 4.300 borrados en el FTS por trigger. Va en el dispatcher de la base de datos; la UI muestra «Eliminando…» y se mide en el teléfono (SC-009, T043). Si pasa de 5 s, se informa en el cierre.

## R7. Vista previa y cita

**Decisión:**
- **Sin cita:** `RagIndex.neighbor(documentId, 0)`, el primer fragmento, con su página.
- **Con cita:**
  1. `loadChunks([chunkId])`;
  2. si no existe o es de otro documento, vista previa normal y aviso (FR-015);
  3. si existe, `neighbor(doc, ordinal − 1)` y `neighbor(doc, ordinal + 1)`, de los que se toma la última frase del anterior y la primera del siguiente;
  4. las frases se separan con `java.text.BreakIterator.getSentenceInstance(Locale("es"))`, de la plataforma.
- **Resaltado:** `citationHighlight` de F01 (fondo `#DDF4FF`, texto `blue800`), como en el PNG. Con TalkBack se lee como «Fragmento citado: …».
- **Chip de página:** el del fragmento citado. Sin página, sin chip.

**Documentos no indexados:** sin vista previa de fragmentos. Su detalle muestra el estado (R10).

## R8. Documento inexistente

**Decisión:** si el detalle no encuentra el documento al abrirse, o desaparece mientras está abierto:
1. el ViewModel emite `NotFound` una vez;
2. la ruta vuelve a la Biblioteca (o al origen, si se llegó desde una cita y la Biblioteca no está en la pila);
3. deja un resultado en el `SavedStateHandle` de la entrada anterior, que la Biblioteca muestra como *snackbar*: «Este documento ya no existe».

Sin `!!` ni excepciones no capturadas (SC-007).

## R9. Estado de la Biblioteca al navegar y girar

**Decisión:**
- **Búsqueda y filtro:** en el `SavedStateHandle` del `LibraryViewModel`, que vive con la entrada de la Biblioteca mientras el detalle está encima.
- **Posición:** `rememberLazyListState()`, que es *saveable*.
- **Límite de la paginación:** también en el `SavedStateHandle`, para que al girar no vuelva a 50 y salte la posición.

## R10. Estado de cada documento en las pantallas

Del índice, la tanda y el progreso del trabajo (F07):

| Estado | Fila (Biblioteca) | Chip (detalle) | Interruptor |
|---|---|---|---|
| `done` | «PDF · 860 fragmentos» | «Indexado» | Activo |
| `done` excluido | «PDF · 860 fragmentos» y «Excluido» | «Excluido» | Activo, apagado |
| `done` con sombra en curso | «PDF · 860 fragmentos · Reindexando» | «Indexado» y «Reindexando · N de M» | Activo |
| `done` con sombra en error | «PDF · 860 fragmentos» y chip «Reindexado fallido» | «Indexado» y D6 | Activo |
| `pending` o `parsed` | «PDF · Pendiente» | «Pendiente» | Deshabilitado |
| `embedding` | «PDF · Indexando» | «Indexando · N de M» | Deshabilitado |
| `error` | Chip «Error de indexación» | «Error» con la causa (textos de F06 y F07) | Deshabilitado |
| `cancelled` | «PDF · Fuera del índice» | «Fuera del índice» | Deshabilitado |

«N de M» son los fragmentos guardados sobre el troceado, como en la cola de Ingesta.

## R11. Mediciones

- **T022** (`LibraryBenchmarkTest`, instrumentada): 1.000 documentos sintéticos en un índice real. Mide:
  - la primera página y el recuento (p50 y p95);
  - cada bloque siguiente de 50 (p95 ≤ 100 ms);
  - el recorrido completo de 1.000 filas;
  - que no se lee la tabla `chunks`: con un índice sin fragmentos y otro con 50.000, los tiempos no cambian.
- **T043** (`DocumentDeleteBenchmarkTest`): un documento con 4.300 fragmentos sintéticos de 256D. Mide `deleteDocument`, `removeFromIndex` y `publishReindex`.

Las dos se ejecutan en el teléfono y sus cifras van al informe.
