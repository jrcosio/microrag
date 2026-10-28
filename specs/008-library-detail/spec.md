# Especificación de feature: Biblioteca y detalle de documento (F08)

**Feature Branch**: `008-library-detail`

**Created**: 2026-10-28

**Status**: Draft

**Input**: Descripción del usuario: «F08 — Biblioteca y detalle de documento. Feature de producto: la pantalla Biblioteca, que sustituye al placeholder actual, y el Detalle del documento, con `chunkId` opcional cuando se llega desde una cita.» Ver `docs/roadmap/FEATURES.md` (F08).

**Fuentes**:
- Mockups (mandan sobre los `.md`):
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/06-biblioteca.png` y `docs/disegn_app/screens/06-library.md`;
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/07-detalle-documento.png` y `docs/disegn_app/screens/07-document-detail.md`.
- `docs/roadmap/INVENTARIO.md`:
  - fichas P06 y P07;
  - G08: flecha Atrás en las pestañas;
  - G16: filtros de la Biblioteca;
  - G17: acciones del detalle («⋯» → «Quitar del índice»);
  - G22: copia privada de los importados;
  - G28: exclusión en las búsquedas.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Esquema SQLite (`ON DELETE CASCADE`);
  - Capa de datos en Kotlin (`loadChunks`);
  - Ingesta en el dispositivo (reindexado);
  - Privacidad y copias de seguridad (borrado).
- Constitución v1.1.0, principios:
  - I: sin red; datos en `noBackupFilesDir`; logs sin contenido privado;
  - II: capas y fuera del hilo principal;
  - III: el índice nunca mezcla ni publica a medias;
  - V: veracidad de estado (metadatos reales, nunca una página ficticia);
  - VI: calidad;
  - VII: UI fiel y accesible;
  - VIII: proceso.
- DECISIONS:
  - D-029: español por defecto, inglés secundario;
  - D-030: INT8 aplazado a F14 (`quant_dirty`);
  - D-037 y D-038: importación y copia privada (F06);
  - D-039 a D-042: indexación (F07).
- Lo construido:
  - F01: navegación con las rutas `library` y `document/{documentId}` (con `chunkId` opcional), y los componentes del sistema de diseño;
  - F04: índice con documentos, fragmentos, búsqueda por texto completo y estados;
  - F06: importación con copia privada, cola de Ingesta y «Quitar de la cola»;
  - F07: tanda de indexación, «Reintentar», pantalla de Estado de indexación y búsquedas que solo devuelven documentos indexados e incluidos.

**Verificado al redactar** (2026-10-28):
- **Borrado en el índice:**
  - borrar un documento borra en cascada sus fragmentos, con sus vectores (van en la misma fila) y, por un trigger, sus entradas de texto completo;
  - ese borrado no marca hoy el índice INT8 como desactualizado (`quant_dirty`), y sí lo hace el borrado de solo los fragmentos;
  - ninguno de los dos borra la copia privada del archivo. Hoy solo lo hace «Quitar de la cola» de la Ingesta, que además saca el documento de la tanda de F07.
- **Datos del documento:** el índice guarda nombre, tipo, tamaño, número de páginas, número de fragmentos, estado, causa de error, inclusión y fecha de importación. No guarda una versión normalizada del nombre para buscar.
- **Estados:** `pending`, `parsed`, `embedding`, `done` y `error` están en uso. `paused` y `cancelled` existen en el esquema sin uso (D-030).
- **Búsquedas (F07, D-040):** solo devuelven fragmentos de documentos `done` e incluidos. Cambiar la inclusión surte efecto en la siguiente búsqueda.
- **Navegación:** la ruta del detalle ya admite un `chunkId` opcional; la Biblioteca es hoy un placeholder.
- **Teléfono de referencia:** quedan 8 documentos de prueba indexados de F07 (hasta 4.300 fragmentos cada uno) que hoy no se pueden borrar desde la app.

## Clarifications

### Session 2026-10-28

- Q: ¿En qué estado queda un documento tras «Quitar del índice»? → A: en un estado propio, «Fuera del índice». No aparece en la cola de Ingesta ni entra en ninguna tanda. Se ve en la Biblioteca, en el filtro Pendientes, con su chip, y solo vuelve a indexarse con «Reindexar» desde su detalle.
- Q: Al abrir el detalle desde una cita, ¿qué muestra la vista previa? → A: el fragmento citado entero y resaltado, con la última frase del fragmento anterior y la primera del siguiente sin resaltar, como en el PNG 07. El chip de página es el del fragmento citado.
- Q: Sin cita, ¿la vista previa muestra solo el primer fragmento o es navegable? → A: solo el texto real del primer fragmento, con su página. No hay navegación entre fragmentos.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **El usuario:** ha importado e indexado documentos y quiere encontrarlos, decidir cuáles usa el asistente y mantener su biblioteca.
- **F09 (Inicio)** y **F11 (Chat):** enlazan con la Biblioteca y con el detalle; F11 abre el detalle desde una cita, con el fragmento citado.
- **F07 (Indexación):** procesa los documentos que se reindexan.

### User Story 1 - Encontrar cualquier documento y ver su estado (Priority: P1)

El usuario abre la pestaña Biblioteca y ve todos sus documentos importados, indexados o no, con su tipo, su número de fragmentos o su estado. Puede buscar por nombre y filtrar entre Todos, Indexados y Pendientes. Al abrir un documento y volver, la búsqueda y el filtro siguen como estaban.

**Why this priority**: sin la Biblioteca, el usuario no tiene forma de ver ni gestionar lo que ha importado. Es la puerta a todo lo demás de esta feature.

**Independent Test**: con documentos en todos los estados, la lista los muestra todos con su estado real. Buscar «manual» y filtrar «Indexados» deja solo los indexados cuyo nombre contiene «manual», sin distinguir mayúsculas ni acentos. Abrir uno, volver y rotar conserva búsqueda, filtro y posición. Con 1.000 documentos sintéticos, la lista se recorre con fluidez y nunca lee fragmentos.

**Acceptance Scenarios**:

1. **Given** documentos importados en cualquier estado, **When** el usuario abre la Biblioteca, **Then** ve todos, del más reciente al más antiguo, cada uno con su tipo y su número de fragmentos si está indexado, o su estado si no lo está.
2. **Given** la Biblioteca, **When** el usuario escribe parte de un nombre, **Then** la lista muestra solo los documentos cuyo nombre lo contiene, sin distinguir mayúsculas ni acentos, y la cabecera cuenta los resultados.
3. **Given** la Biblioteca, **When** el usuario elige «Indexados» o «Pendientes», **Then** la lista muestra solo los indexados, o solo los que no lo están (también los que tienen error, con su chip), y se combina con la búsqueda.
4. **Given** una búsqueda y un filtro activos, **When** el usuario abre un documento y vuelve, o gira el teléfono, **Then** la búsqueda, el filtro y la posición en la lista se conservan.
5. **Given** que no hay documentos, **When** se abre la Biblioteca, **Then** muestra «Aún no tienes documentos» con «Importar documentos», que abre la Ingesta. **Given** una búsqueda o un filtro sin resultados, **Then** muestra «No hay coincidencias» con «Limpiar filtros».
6. **Given** la Biblioteca, **When** el usuario pulsa «+», **Then** se abre la Ingesta.

---

### User Story 2 - Decidir qué documentos usa el asistente (Priority: P1)

Cada documento indexado tiene un interruptor «Incluir en RAG» en la lista y «Incluir en respuestas» en el detalle. Apagarlo hace que el asistente deje de usar ese documento en las respuestas siguientes, sin borrar nada. Encenderlo lo devuelve.

**Why this priority**: es el control principal sobre las fuentes del RAG, y la Biblioteca existe para darlo (PNG 06). Sin él, la única forma de apartar un documento sería borrarlo.

**Independent Test**: apagar el interruptor de un documento indexado; una búsqueda que antes lo devolvía ya no devuelve ninguno de sus fragmentos, y sus fragmentos siguen guardados. Encenderlo; vuelve a aparecer. El cambio se mantiene al cerrar la app. En un documento sin indexar, el interruptor está deshabilitado y explica por qué.

**Acceptance Scenarios**:

1. **Given** un documento indexado e incluido, **When** el usuario apaga su interruptor, **Then** el cambio se guarda, las búsquedas siguientes no devuelven sus fragmentos y no se borra nada.
2. **Given** un documento excluido, **When** el usuario enciende su interruptor, **Then** sus fragmentos vuelven a aparecer en las búsquedas siguientes.
3. **Given** un documento no indexado (pendiente, en indexación o con error), **When** se muestra, **Then** su interruptor está deshabilitado y TalkBack explica que se podrá incluir cuando esté indexado.
4. **Given** el mismo documento, **When** se cambia la inclusión en la lista o en el detalle, **Then** las dos pantallas muestran el mismo valor.
5. **Given** un documento excluido, **When** se muestra en la lista o en el detalle, **Then** su estado de exclusión se ve y TalkBack lo anuncia.

---

### User Story 3 - Ver el detalle real de un documento, también desde una cita (Priority: P1)

Al abrir un documento, el usuario ve su nombre, su estado, sus páginas, sus fragmentos, su tamaño, su fecha de importación y una vista previa de su contenido con la página. Si llega desde una cita del chat, la vista previa muestra el fragmento citado, resaltado.

**Why this priority**: da trazabilidad: el usuario comprueba de dónde sale una respuesta. Las citas de F11 dependen de este destino.

**Independent Test**: abrir un PDF indexado: las cifras coinciden con las del índice y la vista previa muestra el texto real con su página. Abrir un TXT: no hay chip de página. Abrir el detalle con el id de un fragmento: muestra ese fragmento resaltado. Abrir un documento que ya no existe: aviso y vuelta a la Biblioteca, sin cierre.

**Acceptance Scenarios**:

1. **Given** un documento indexado, **When** se abre su detalle, **Then** muestra su nombre completo, el chip «Indexado» y la rejilla con páginas, fragmentos, tamaño y fecha de importación reales.
2. **Given** un documento indexado, **When** se abre sin cita, **Then** la vista previa muestra el texto real de su primer fragmento con el chip de su página. Si el fragmento no tiene página (TXT, MD), no hay chip.
3. **Given** un fragmento citado, **When** se abre el detalle desde la cita, **Then** la vista previa muestra ese fragmento entero y resaltado, con la última frase del fragmento anterior y la primera del siguiente sin resaltar (si existen), y el chip de la página del fragmento citado.
4. **Given** una cita a un fragmento que ya no existe (el documento se reindexó o se quitó del índice), **When** se abre el detalle, **Then** muestra la vista previa normal y un aviso de que el fragmento citado ya no está.
5. **Given** un documento no indexado, **When** se abre su detalle, **Then** muestra su estado real («Pendiente», «Indexando» con su progreso o «Error» con la causa), sin vista previa de fragmentos, y la acción que corresponde.
6. **Given** un documento que ya no existe, **When** se intenta abrir su detalle, **Then** la app muestra un aviso y vuelve a la Biblioteca, sin cerrarse.

---

### User Story 4 - Mantener la biblioteca: eliminar, quitar del índice y reindexar (Priority: P2)

Desde el detalle, el usuario puede eliminar un documento por completo, quitarlo del índice conservando el archivo, o reindexarlo. Ninguna de esas acciones deja el índice a medias: el asistente nunca usa fragmentos de una versión incompleta.

**Why this priority**: completa el ciclo de vida del documento y libera espacio, pero el uso diario (buscar, incluir, consultar) no depende de ello.

**Independent Test**: eliminar un documento indexado tras confirmar; no queda ni la copia, ni fragmentos, ni vectores, ni entradas de texto, ni metadatos. Quitar del índice otro; sus fragmentos desaparecen y la copia queda; reindexarlo lo deja como antes. Reindexar un documento indexado mientras se busca: las búsquedas siguen devolviendo su versión anterior hasta que la nueva está completa.

**Acceptance Scenarios**:

1. **Given** un documento, **When** el usuario pulsa «Eliminar», **Then** un diálogo explica exactamente qué se borra (la copia guardada por la app, el texto, los fragmentos, los vectores y los metadatos) y que el archivo original del teléfono no cambia.
2. **Given** el diálogo de eliminar, **When** el usuario confirma, **Then** se borra todo eso, el documento desaparece de la Biblioteca, de la Ingesta, de la tanda y de las búsquedas, y la app vuelve a la Biblioteca. **When** cancela, **Then** no cambia nada.
3. **Given** un documento indexado, **When** el usuario elige «⋯» → «Quitar del índice» y confirma, **Then** se borran sus fragmentos y vectores, se conservan la copia y los metadatos, y el documento queda «Fuera del índice»: no aparece en la cola de Ingesta, no entra en ninguna tanda y solo vuelve a indexarse con «Reindexar».
4. **Given** un documento indexado, **When** el usuario pulsa «Reindexar documento», **Then** se procesa otra vez en la tanda de indexación (F07) y, mientras tanto, las búsquedas siguen usando su versión anterior. Al terminar, la nueva la sustituye de una vez.
5. **Given** un reindexado en curso, **When** falla, se pausa o el proceso muere, **Then** la versión anterior sigue intacta y en uso.
6. **Given** un documento que se está indexando, **When** el usuario lo elimina o lo quita del índice, **Then** sale de la tanda sin romperla, y el resto de documentos sigue.

---

### User Story 5 - Pantallas fieles, accesibles y coherentes con el resto de la app (Priority: P2)

La Biblioteca y el Detalle reproducen los PNG 06 y 07 y cubren los estados del `.md` que los PNG no maquetan. Lo que cambia aquí se refleja en la Ingesta, en el Estado de indexación y en las búsquedas.

**Why this priority**: la fidelidad y la accesibilidad son obligatorias (constitución VII), pero dependen de que las funciones existan.

**Independent Test**: comparar capturas del teléfono con los PNG 06 y 07. Recorrer todos los estados con las comprobaciones automáticas de accesibilidad, en claro y en oscuro, con la fuente al 200 %. Con TalkBack, cada fila anuncia nombre, estado e inclusión.

**Acceptance Scenarios**:

1. **Given** documentos indexados, **When** se abre la Biblioteca, **Then** coincide con el PNG 06: «Biblioteca» con «+», «Tus documentos», «Gestiona las fuentes de tu IA local.», el buscador, los tres filtros, «N DOCUMENTOS» con «Incluir en RAG», las filas con su interruptor y la tarjeta «Excluir un archivo evita que se use en futuras respuestas. No lo elimina.».
2. **Given** un documento indexado, **When** se abre su detalle, **Then** coincide con el PNG 07: «Detalle del documento» con «⋯», el héroe oscuro con el nombre y «Indexado», la rejilla 2×2, «Incluir en respuestas», «VISTA PREVIA DEL CONTENIDO» y las acciones «Reindexar documento» y «Eliminar».
3. **Given** cada estado (carga, vacío, sin resultados, error, documento con error, excluido, no indexado, en indexación, sin vista previa, no encontrado), **When** se muestra, **Then** tiene un texto claro y la acción que corresponde.
4. **Given** TalkBack, **When** se recorre la Biblioteca, **Then** cada fila se lee como una frase con nombre, estado e inclusión, y el interruptor anuncia su efecto.
5. **Given** la fuente al 200 % y el modo oscuro, **When** se muestra cualquier estado, **Then** nada se corta ni se solapa y el contraste cumple WCAG AA.

---

### Edge Cases

- **Nombres largos:** se cortan con puntos suspensivos en la lista y se ven enteros en el detalle y con TalkBack.
- **Documento borrado en otra pantalla** mientras su detalle está abierto (por ejemplo, desde la Ingesta): el detalle muestra el aviso de no encontrado y vuelve a la Biblioteca.
- **Documento que cambia de estado** mientras se ve (termina de indexarse): la lista y el detalle se actualizan solos.
- **Interruptor pulsado varias veces seguidas:** gana el último valor, sin estados intermedios guardados.
- **Eliminar un documento muy grande** (4.300 fragmentos): la interfaz no se bloquea y muestra que se está borrando hasta terminar.
- **Muerte del proceso a mitad de un borrado o de quitar del índice:** al volver, el documento está entero o borrado del todo, nunca a medias. Si la copia privada no se pudo borrar, se borra después.
- **Reindexar sin el modelo de embeddings verificado:** el documento sigue indexado con su versión anterior y la tanda espera, como en F07 (P7).
- **Reindexar sin espacio suficiente** para tener las dos versiones a la vez: no empieza o se detiene sin tocar la anterior, y avisa.
- **Reindexar un documento cuya copia privada ya no está:** no se puede reindexar; se avisa y se ofrece eliminarlo.
- **Cita a un fragmento de otro documento** o con un id inválido: se trata como un fragmento que ya no existe.
- **Biblioteca no disponible** (índice incompatible o ilegible): estado de error con la explicación de F04, sin acciones que escriban.
- **Búsqueda con caracteres especiales** (comillas, `%`, `_`): se buscan tal cual, como texto.

## Requirements *(mandatory)*

### Functional Requirements

**Biblioteca**

- **FR-001**: La Biblioteca MUST listar todos los documentos importados, en cualquier estado, del más reciente al más antiguo, cargándolos por páginas y sin leer nunca sus fragmentos.
- **FR-002**: Cada fila MUST mostrar el icono del tipo, el nombre (cortado si no cabe) y, debajo, el tipo y los fragmentos si está indexado («PDF · 860 fragmentos»), o su estado si no lo está («Pendiente», «Indexando», «Error de indexación», «Fuera del índice»), más el interruptor de inclusión.
- **FR-003**: La búsqueda por nombre MUST encontrar los documentos cuyo nombre contiene el texto escrito, sin distinguir mayúsculas ni acentos, y tratar los caracteres especiales como texto.
- **FR-004**: Los filtros MUST ser Todos, Indexados (estado indexado) y Pendientes (cualquier otro estado, también error y fuera del índice), y MUST combinarse con la búsqueda (G16).
- **FR-005**: La cabecera MUST contar los documentos que cumplen la búsqueda y el filtro actuales («N DOCUMENTOS»).
- **FR-006**: La búsqueda, el filtro y la posición en la lista MUST conservarse al volver del detalle y al girar el teléfono.
- **FR-007**: «+» MUST abrir la Ingesta; tocar una fila MUST abrir su detalle; la flecha Atrás MUST volver a Inicio (G08).
- **FR-008**: La Biblioteca MUST mostrar los estados: carga (filas esqueleto), vacío («Aún no tienes documentos» con «Importar documentos»), sin resultados («No hay coincidencias» con «Limpiar filtros») y error con «Reintentar».
- **FR-009**: La lista y el detalle MUST reflejar sin recargar a mano los cambios del índice: documentos nuevos, borrados, que terminan de indexarse o que cambian de inclusión.

**Inclusión**

- **FR-010**: El interruptor MUST guardar la inclusión del documento, y las búsquedas siguientes MUST respetarla, sin borrar ni recalcular nada.
- **FR-011**: El interruptor MUST estar deshabilitado en los documentos no indexados, con una explicación accesible.
- **FR-012**: El cambio de inclusión MUST verse igual en la lista y en el detalle, y MUST mantenerse al cerrar la app.

**Detalle**

- **FR-013**: El detalle MUST mostrar el nombre completo, el chip de estado (Indexado, Pendiente, Indexando, Error, Fuera del índice o Excluido) y la rejilla con páginas, fragmentos, tamaño y fecha de importación, todos reales. Si un dato no existe (páginas de un TXT), MUST mostrarse como ausente y nunca inventado.
- **FR-014**: Sin cita, la vista previa MUST mostrar el texto real del primer fragmento del documento, con el chip de su página si la tiene y sin chip si no. No hay navegación entre fragmentos.
- **FR-015**: Con cita, la vista previa MUST mostrar el fragmento citado entero y resaltado, precedido de la última frase del fragmento anterior y seguido de la primera del siguiente, sin resaltar y solo si existen, con el chip de la página del fragmento citado. El resaltado MUST anunciarse con TalkBack. Si el fragmento ya no existe o no es de ese documento, MUST mostrar la vista previa normal y un aviso.
- **FR-016**: Un documento no indexado MUST mostrar su estado real (con el progreso si se está indexando y la causa si tiene error), sin vista previa de fragmentos.
- **FR-017**: Si el documento no existe o desaparece mientras está abierto, la app MUST mostrar un aviso y volver a la Biblioteca, sin cerrarse.

**Acciones**

- **FR-018**: «Eliminar» MUST pedir confirmación con un diálogo que explique qué se borra y que el archivo original del teléfono no cambia. Al confirmar MUST borrar la copia privada, los fragmentos, sus vectores, sus entradas de texto completo y los metadatos, sacar el documento de la tanda y marcar el índice INT8 como desactualizado (D-030).
- **FR-019**: «⋯» → «Quitar del índice» MUST pedir confirmación y borrar los fragmentos, sus vectores y sus entradas de texto completo, conservando la copia privada y los metadatos. El documento MUST quedar en el estado «Fuera del índice»: fuera de las búsquedas, fuera de la cola de Ingesta y de cualquier tanda (tampoco entra con «Iniciar indexación»), hasta que el usuario pulse «Reindexar».
- **FR-020**: «Reindexar documento» MUST volver a procesar el documento con la tanda de indexación de F07. Mientras dura, las búsquedas MUST seguir usando su versión anterior completa, y la nueva MUST sustituirla de una vez al terminar. Si el reindexado falla, se pausa o se interrumpe, la versión anterior MUST quedar intacta.
- **FR-021**: Eliminar o quitar del índice un documento que está en la tanda de indexación MUST sacarlo de la tanda sin romperla; el resto de documentos MUST seguir.
- **FR-022**: Borrar o quitar del índice MUST ser atómico respecto al índice: tras una muerte del proceso, el documento queda entero o sin fragmentos, nunca a medias. Una copia privada que no se pudo borrar MUST borrarse después.
- **FR-023**: Las acciones que escriben MUST estar deshabilitadas, con su explicación, cuando la biblioteca no está disponible.

**Coherencia con F06 y F07**

- **FR-024**: Lo que se elimine o se quite del índice MUST reflejarse en la cola de Ingesta y en el Estado de indexación. Un documento «Fuera del índice» MUST NOT aparecer en la cola de Ingesta.
- **FR-025**: Los documentos con error MUST ofrecer en el detalle «Reintentar» si su causa lo permite, con la misma regla que la Ingesta y el Estado de indexación.

**Accesibilidad, idioma y privacidad**

- **FR-026**: Cada fila de la lista MUST anunciarse como una frase con nombre, estado e inclusión; el interruptor MUST anunciar su efecto; las cifras de la rejilla MUST leerse con su etiqueta.
- **FR-027**: Las dos pantallas MUST cumplir objetivos táctiles de 48 dp, contraste AA en claro y en oscuro, y la fuente al 200 % sin cortes ni solapes.
- **FR-028**: Todos los textos MUST estar en `strings.xml`, en español e inglés (D-029).
- **FR-029**: Los logs MUST NOT contener nombres de archivo, rutas ni texto de los documentos; ninguna acción MUST usar la red.

### Key Entities

- **Documento**: el importado en F06. Nombre, tipo, tamaño, páginas, fragmentos, estado (pendiente, preparado, indexando, indexado, error y, nuevo en F08, fuera del índice), causa de error, inclusión, fecha de importación y copia privada. F08 lo lista, lo muestra, cambia su inclusión, lo elimina, lo quita del índice y lo reindexa.
- **Fragmento**: trozo de texto indexado de un documento, con su ordinal, su página (si la hay), su ruta de encabezados y su vector. F08 lo muestra en la vista previa y lo borra con su documento.
- **Consulta de la Biblioteca**: texto de búsqueda y filtro (Todos, Indexados, Pendientes). Se conserva al navegar y al girar.
- **Cita**: referencia de F11 a un fragmento concreto de un documento. F08 la recibe al abrir el detalle.
- **Tanda de indexación**: la de F07. F08 añade documentos al reindexar y saca los que se eliminan o se quitan del índice.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con 1.000 documentos, la Biblioteca muestra su primera página en 1 segundo o menos en el teléfono de referencia y se recorre entera sin tirones y sin leer ningún fragmento.
- **SC-002**: En el 100 % de los casos de prueba, la búsqueda y los filtros devuelven exactamente los documentos que cumplen ambos, también con mayúsculas, acentos y caracteres especiales, y su estado se conserva al volver del detalle y al girar.
- **SC-003**: Tras excluir un documento, el 100 % de las búsquedas siguientes no devuelven ningún fragmento suyo, y al incluirlo vuelven a devolverlos. Excluir no borra ningún fragmento.
- **SC-004**: Tras eliminar un documento no queda ningún resto suyo: copia privada, fragmentos, vectores, entradas de texto completo ni metadatos. Desaparece de la Biblioteca, de la Ingesta, de la tanda y de las búsquedas.
- **SC-005**: Tras quitar un documento del índice, quedan 0 fragmentos suyos y su copia privada intacta. Reindexarlo lo deja indexado con los mismos fragmentos (texto, página y encabezados) que tenía.
- **SC-006**: Durante un reindexado, las búsquedas devuelven en todo momento la versión anterior completa o la nueva completa, nunca una mezcla ni ninguna. Tras matar el proceso a mitad, la versión anterior sigue intacta.
- **SC-007**: Abrir el detalle de un documento inexistente o borrado nunca cierra la app: muestra el aviso y vuelve a la Biblioteca en el 100 % de las pruebas.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en todos los estados de las dos pantallas, en claro y en oscuro, con la fuente al 200 %.
- **SC-009**: Eliminar o quitar del índice un documento de 4.300 fragmentos no bloquea la interfaz (sin trabajo de disco en el hilo principal), y su duración se mide y se reporta en el teléfono de referencia.
- **SC-010**: Ninguna acción de la Biblioteca o del detalle hace llamadas de red, y los logs no contienen nombres, rutas ni texto de documentos.

## Assumptions

Valores por defecto adoptados sin pregunta. Las tres decisiones de más impacto se resolvieron en «Clarifications»:

- **Filtros (G16):** sin filtro «Errores»; «Pendientes» incluye todo lo no indexado, también los errores, con su chip.
- **Orden:** del más reciente al más antiguo por fecha de importación. Sin selector de orden en el MVP.
- **Búsqueda:** contiene, sin distinguir mayúsculas ni acentos, sobre el nombre que se muestra (con extensión). Sin búsqueda en el contenido: eso es el chat (F10/F11).
- **Reindexar:** se ofrece en documentos indexados, quitados del índice y con error reintentable. Necesita espacio temporal para tener a la vez las dos versiones de ese documento.
- **Documentos no indexados:** se listan con su estado; el detalle muestra el estado y, en su caso, el progreso, sin vista previa de fragmentos.
- **Celdas sin dato:** si un documento no tiene páginas (TXT, MD), la celda muestra «—» con la etiqueta accesible «sin páginas».
- **Diálogos de confirmación:** «Eliminar» y «Quitar del índice» los piden; incluir o excluir no.
- **Barra inferior:** la Biblioteca la muestra con la pestaña seleccionada; el detalle no, como en los PNG.
- **Documentos de prueba del teléfono:** con esta feature, el usuario podrá borrarlos desde la app; no se borran solos.
- **Fuera de alcance:**
  - Inicio real (F09);
  - búsqueda híbrida, RRF y diagnóstico de búsqueda (F10);
  - chat y citas (F11), que abren este detalle con el fragmento;
  - historial de chats (F13) y lo que pase con citas antiguas de documentos borrados, salvo el aviso de FR-015;
  - INT8 (F14);
  - exportar y restaurar, OCR, edición del nombre y selección múltiple (backlog).
