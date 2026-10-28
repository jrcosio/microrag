# Contrato de UI: Biblioteca (PNG 06) y Detalle del documento (PNG 07)

Textos en `strings_library.xml` (es y en, D-029). Componentes `Lisdata*` de F01 y tokens; ningún color literal.

## Biblioteca (`library`, PNG 06)

| # | Elemento | Componente | Español | Inglés |
|---|---|---|---|---|
| 1 | Top bar | `LisdataTopBar` con Atrás (G08 → Inicio) y acción `ic_plus` («Importar documentos») | «Biblioteca» | «Library» |
| 2 | Título | `headlineLarge` | «Tus documentos» | «Your documents» |
| 3 | Texto | `bodyLarge`, `textSecondary` | «Gestiona las fuentes de tu IA local.» | «Manage your local AI's sources.» |
| 4 | Buscador | `LisdataSearchField` | «Buscar documentos…» | «Search documents...» |
| 5 | Filtros | `LisdataFilterChip` × 3 | «Todos», «Indexados», «Pendientes» | «All», «Indexed», «Pending» |
| 6 | Cabecera | `LisdataSectionHeader` con el texto de la derecha | «%d DOCUMENTOS» (plural) y «Incluir en RAG» | «%d DOCUMENTS» and «Include in RAG» |
| 7 | Filas | `LisdataDocumentRow` (R10) | «PDF · 860 fragmentos», «PDF · Pendiente», «PDF · Indexando», «PDF · Fuera del índice», chip «Error de indexación» | «PDF · 860 chunks»… |
| 8 | Tarjeta final | `LisdataInfoCard(ic_info)` | «Excluir un archivo evita que se use en futuras respuestas. No lo elimina.» | «Excluding a file keeps it out of future answers. It does not delete it.» |
| 9 | Barra inferior | `LisdataBottomBar`, Biblioteca seleccionada | — | — |

**Desplazamiento:** del 2 al 8 se desplaza; las filas se cargan por bloques de 50 al acercarse al final (R2).

## Propuestas para los estados sin mockup (se aprueban en la parada tras analyze)

- **B1 — Carga:** 4 `LisdataSkeletonRow` bajo los filtros, anunciadas como «Cargando».
- **B2 — Vacío:** `LisdataEmptyState(ic_folder)`, «Aún no tienes documentos» / «Importa PDF, TXT o Markdown para consultarlos sin conexión.» y `LisdataGradientButton` «Importar documentos» → Ingesta. Sin buscador ni filtros.
- **B3 — Sin resultados:** bajo los filtros, `LisdataEmptyState(ic_search)` «No hay coincidencias» / «Prueba con otro nombre o filtro.» y `LisdataOutlinedButton` «Limpiar filtros», que vacía la búsqueda y vuelve a «Todos».
- **B4 — Error y biblioteca no disponible:**
  - un error de lectura: `LisdataErrorState` con «Reintentar»;
  - un índice incompatible: el mismo estado sin «Reintentar», con el texto de F06 P5 («La biblioteca no está disponible»).
- **B5 — Fila de un documento no indexado:**
  - el interruptor aparece deshabilitado;
  - TalkBack añade «Se podrá incluir cuando esté indexado».
  - Con error, además, el chip `StatusKind.Error` «Error de indexación».
- **B6 — Fila excluida:** el interruptor apagado, sin otro cambio visual (como el PNG); TalkBack dice «excluido de las respuestas».
- **B7 — Reindexando:** el metadato termina en «· Reindexando», en `brandBlue`. Si la sombra falló, el chip `StatusKind.Error` «Reindexado fallido».
- **B8 — Documento desaparecido:** *snackbar* «Este documento ya no existe» al volver del detalle (R8).

## Detalle (`document/{documentId}?chunkId`, PNG 07)

| # | Elemento | Componente | Español |
|---|---|---|---|
| 1 | Top bar | `LisdataTopBar` con Atrás y acción `ic_more_horizontal` (menú «Quitar del índice», solo en `done`) | «Detalle del documento» |
| 2 | Héroe | `LisdataHeroCard` oscuro, sin eyebrow: tile `onDarkTile` con `ic_file_text`, nombre completo en `headlineMedium` y chip de estado (R10) arriba a la derecha | «Indexado» |
| 3 | Rejilla 2×2 | `LisdataStatCard` × 4; «FRAGMENTOS» con `emphasis = true` | «PÁGINAS», «FRAGMENTOS», «TAMAÑO», «IMPORTADO» |
| 4 | Inclusión | `LisdataSwitchRow`. El subtítulo depende del estado (cierre, D-045) | «Incluir en respuestas» / «Disponible para consultas RAG»; excluido: «No se usa en las respuestas»; sin indexar: «Se podrá incluir cuando esté indexado» |
| 5 | Cabecera | `LisdataSectionHeader` | «VISTA PREVIA DEL CONTENIDO» |
| 6 | Vista previa | `LisdataCard`: chip de página (`LisdataStatusChip(StatusKind.Info)`, «Página %d») y texto. Sin cita, el texto se recorta a 3 líneas con «Ver más» / «Ver menos» (cierre, D-045). Con cita, entera: «before» y «after» en `bodyLarge` y el fragmento citado sobre `citationHighlight` en `blue800` y negrita | «Página 12» |
| 7 | Acciones | `LisdataGradientButton` «Reindexar documento» y `LisdataOutlinedButton` destructivo con `ic_trash` «Eliminar», en una fila | — |

**Chips de estado** (`LisdataStatusChip`, `onDark = true` en el héroe): «Indexado» `Ready`, «Pendiente» `Queued`, «Indexando» `Processing`, «Error» `Error`, «Fuera del índice» y «Excluido» `Info`.

**Formatos:**
- **TAMAÑO:** con el formateador de Android, como F06 («2,4 MB»).
- **IMPORTADO:** fecha corta del idioma («12 oct.»).
- **PÁGINAS:** en TXT y MD, «—», leída como «sin páginas».
- **FRAGMENTOS:** sin indexar, «—», leída como «sin fragmentos».

## Propuestas del detalle (se aprueban en la parada tras analyze)

- **D1 — Pendiente:** chip «Pendiente». En lugar de la vista previa, `LisdataInfoCard`: «Este documento aún no está indexado. Se indexará al pulsar Iniciar indexación en la Ingesta.» Acciones: solo «Eliminar».
- **D2 — Indexando:**
  - chip «Indexando»;
  - una `LisdataProgressCard` con «%1$s de %2$s fragmentos» y su barra;
  - el enlace «Ver estado de la indexación» → `Index`.

  Acciones: solo «Eliminar».
- **D3 — Error:** chip `Error`, con la causa y la frase de la Ingesta en `LisdataInfoCard(ic_warning)`. Acciones: «Reintentar» (solo si se puede) y «Eliminar».
- **D4 — Fuera del índice:** chip «Fuera del índice». En la vista previa, `LisdataInfoCard`: «Quitaste este documento del índice. Su archivo sigue guardado en la app.» Acciones: «Reindexar documento» y «Eliminar». El interruptor está deshabilitado.
- **D5 — Reindexando:** bajo la rejilla, una `LisdataProgressCard` con «Reindexando · %1$s de %2$s fragmentos», el texto «Mientras tanto, las respuestas usan la versión anterior» y el enlace a `Index`. Si la tanda está en pausa, se añade su causa con los textos de F07 («En pausa», «Pausa por temperatura», «Sin espacio», «Falta el modelo de embeddings»). «Reindexar documento» deshabilitado con el texto «Reindexando…».
- **D6 — Reindexado fallido:** `LisdataInfoCard(ic_warning)`: «No se pudo reindexar: %s. Las respuestas siguen usando la versión anterior.» Acciones: «Reintentar» (si se puede) y «Descartar».
- **D7 — Cita perdida:** sobre la vista previa normal, `LisdataInfoCard(ic_info)`: «El fragmento citado ya no está en este documento.»
- **D8 — Diálogos:**
  - **Eliminar:** `LisdataConfirmationDialog` destructivo:
    - título «¿Eliminar «%s»?»;
    - texto «Se borrarán la copia guardada por la app, su texto, sus fragmentos y vectores, y sus datos. El archivo original de tu teléfono no cambia.»;
    - botón «Eliminar».

    Al confirmar, el botón muestra «Eliminando…» hasta terminar, y se vuelve a la Biblioteca.
  - **Quitar del índice:**
    - título «¿Quitar del índice?»;
    - texto «Se borrarán sus fragmentos y vectores, y dejará de usarse en las respuestas. El archivo se conserva para reindexarlo cuando quieras.»;
    - botón «Quitar».
- **D10 — Falta la copia:** `LisdataInfoCard(ic_warning)`: «No se encuentra el archivo guardado por la app, así que no se puede reindexar. Puedes eliminarlo e importarlo de nuevo.» Acciones: solo «Eliminar». Si el documento está indexado, sigue funcionando en las respuestas.
- **D11 — Biblioteca no disponible:** la top bar y `LisdataErrorState` sin «Reintentar», con el texto de F06 P5. Sin acciones que escriban.
- **D9 — Carga:** la top bar y un `LisdataProgressRing` indeterminado pequeño, como F07 P12.

## Accesibilidad (FR-026, FR-027)

- **A1:** cada fila de la Biblioteca se lee como una frase: «%1$s, %2$s, %3$s», con el nombre, el estado o los fragmentos, y «incluido» o «excluido». El interruptor anuncia «Incluir en respuestas» y su estado.
- **A2:** las celdas de la rejilla se leen como «Páginas, 48», etc.
- **A3:** el resaltado de una cita se lee «Fragmento citado: …».
- **A4:** objetivos táctiles de 48 dp, incluidos los chips de filtro (≈ 27 dp de alto, con 48 de área).
- **A5:** contraste AA en claro y en oscuro. En oscuro, `citationHighlight` es `#0E4A66` (F01).
- **A6:** con la fuente al 200 %, los nombres se parten en la rejilla y el héroe, los botones de acción pasan a apilarse si no caben, y nada se solapa.
- **A7:** los cambios de estado se anuncian sin mover el foco (`liveRegion = Polite`): «Documento eliminado», «Quitado del índice», «Reindexando», «Excluido de las respuestas».

## Navegación

- **Biblioteca:**
  - Atrás → Inicio (G08);
  - «+» → `Ingest`;
  - fila → `Document(id)`;
  - «Importar documentos» (B2) → `Ingest`.
- **Detalle:**
  - Atrás → el origen;
  - desaparecido → el origen, con el aviso (R8);
  - «Ver estado de la indexación» → `Index`.
- **Desde una cita (F11):** `Document(id, chunkId)`. F08 se prueba con la ruta de depuración y en `NavigationTest`.
