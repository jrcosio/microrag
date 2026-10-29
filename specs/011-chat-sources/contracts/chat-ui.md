# Contrato: pantalla del chat (F11)

Manda el PNG `04-chat.png`; los estados sin maqueta son las propuestas C1–C17 aprobadas (spec, «Assumptions»). Ruta `Chat(conversationId: Long? = null, newConversation: Boolean = false)`, pestaña Chat.

## 1. Estructura (de arriba abajo, como el PNG)

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar` | Atrás (G08), título «Chat con documentos», acción «⋯» (`LisdataOverflowMenu`). En `subtitle`: punto `success` y «Gemma 4 · Procesamiento local»; en modo CPU, punto `warning` y «Gemma 4 · Modo CPU, más lento» (C9) |
| Mensajes | `LazyColumn` (estado en la ruta) | Por turno: `LisdataChatBubble` (pregunta) y `LisdataAnswerCard` (respuesta). Si hay fuentes: `LisdataSectionHeader` «Fuentes consultadas» y una `LisdataSourceCitation` por fuente |
| Aviso | `LisdataInfoCard` | Solo con turnos y la disponibilidad distinta de `Ready` (§2), justo encima del compositor |
| Compositor | `LisdataChatComposer` | «Pregunta a tus documentos...», enviar ↔ detener (G12). Hasta 1.000 caracteres. Sobre el teclado con `imePadding()`; la barra inferior se oculta con el teclado (G39) |
| Barra inferior | Del shell (F01) | Chat seleccionado |

**Sin turnos** (conversación vacía), la zona de mensajes muestra el estado que corresponda (§2). En `Ready` sin turnos queda vacía, como el PNG: solo el compositor.

## 2. Disponibilidad (research R11)

| Disponibilidad | Sin turnos | Con turnos | Compositor | Precarga de Gemma |
|---|---|---|---|---|
| `null` (comprobando) | nada | conversación | desactivado | no |
| `StorageUnavailable` (C17) | `LisdataErrorState` sin «Reintentar»: «No se pueden abrir tus conversaciones guardadas.» | — (no hay conversación que mostrar) | desactivado | no |
| `ModelsNotReady` (C5) | `LisdataModelMissingBlock`: «Configuración pendiente», «Completa la preparación de los modelos para poder preguntar a tus documentos.», «Completar configuración» → Preparación (D-046) | aviso con el mismo texto y acción | desactivado | no |
| `IndexUnavailable` (C8) | `LisdataErrorState` «No se puede abrir el índice de documentos» + «Reintentar», que vuelve a comprobarlo | aviso con «Reintentar» | desactivado | no |
| `EmptyLibrary` (C3) | `LisdataEmptyState` «Aún no hay documentos», «Importa documentos para poder preguntarles.», «Importar documentos» → Ingesta | aviso con la acción | desactivado | no |
| `WaitingForIndexing` (C14) | `LisdataEmptyState` «Se están indexando tus documentos», «Podrás preguntar cuando termine la indexación o si la pausas.», «Ver indexación» → Estado de indexación | aviso con el mismo texto y acción | desactivado | no |
| `NothingReady` (C4) | `LisdataEmptyState` «Aún no hay documentos listos para preguntar», «Ver indexación» | aviso con la acción | desactivado | no |
| `Ready` | vacío (PNG) | conversación | activo si la pregunta no está en blanco | sí, al entrar y en cada `ON_START` |

- Las fuentes de la conversación se pueden abrir en todas las situaciones.
- «Reintentar» de una respuesta solo está activo en `Ready` y sin otra respuesta en curso (A10).
- Al cambiar la disponibilidad, la pantalla cambia sola, sin salir (FR-030).

## 3. Tarjeta de respuesta por estado del turno

| Turno | `AnswerState` | Estado visible | Cuerpo | Pie |
|---|---|---|---|---|
| `RETRIEVING` | `Streaming` | «Buscando en documentos...» | vacío | sin copiar; sin chip |
| `GENERATING` | `Streaming` | «Generando respuesta...» (desde que el modelo recibe el prompt) | texto con viñetas y fichas tocables, según llega | sin copiar; sin chip ni lista de fuentes hasta terminar |
| `COMPLETED` | `Completed` | — | texto formateado | «Copiar respuesta» + chip «N fuentes» (si N ≥ 1) |
| `COMPLETED` + negativa (C1) | `Completed` | — | «No encuentro información suficiente en tus documentos» | «Copiar respuesta»; sin chip ni fuentes |
| `COMPLETED` sin citas válidas (C2) | `Completed` | — | texto + nota «Esta respuesta no cita ninguna fuente de tus documentos.» | «Copiar respuesta»; sin chip |
| `CANCELLED` | `Cancelled` | «Generación cancelada» (F01) | texto parcial | «Copiar respuesta» si hay texto; chip y fuentes si hay citas válidas |
| `FAILED(GENERATION)` (C7) | `Failed` | «No se pudo generar la respuesta» (F01) | texto parcial si lo hay | «Reintentar» |
| `FAILED(INDEX)` (C8) | `Failed` | «No se puede abrir el índice de documentos» | — | «Reintentar» |
| `INTERRUPTED` (C13) | `Failed` | «La respuesta se interrumpió» | texto parcial | «Reintentar»; chip y fuentes si hay citas válidas |

- **Cobertura parcial** (C6): se añade la nota «Algunos documentos aún no están indexados: esta respuesta solo usa los que ya están listos.» bajo el cuerpo de cualquier respuesta terminada cuyo turno la tenga.
- **Sin guardar** (C16): la nota «No se ha podido guardar esta respuesta en el teléfono.» bajo la respuesta que no se pudo guardar.
- **Chip «N fuentes»:** lleva a la primera fila de «Fuentes consultadas».

## 4. Cambios en el design system

| Componente | Cambio |
|---|---|
| `model/AnswerContent.kt` (nuevo) | data-model §6 |
| `LisdataAnswerCard` | `content: AnswerContent` en lugar de `body: String`. Nuevos parámetros: `onCitation: (Int) -> Unit`, `notes: List<String> = emptyList()`, `failureText: String? = null` (por defecto, el texto de F01) y `copiedLabel: String? = null` («Respuesta copiada» en lugar de «Copiar respuesta»). Viñetas con punto `brandBlue` (o el número del `marker`). Negritas. Fichas de cita según research R3. El chip de fuentes pasa a ser tocable y sin punto, como el PNG |
| `LisdataSourceCitation` | Nuevo `lastFragment: Int?` para «Fragmentos M–N» (C11). `onClickLabel` «Abrir fuente». Frase para TalkBack: «Fuente N: documento, página P, fragmento M» |
| `LisdataCountChip` | `onClick` opcional, con objetivo de 48 dp |
| `LisdataOverflowMenu` (nuevo, `component/Menus.kt`) | Botón «⋯» con `action_more_options` («Más opciones») y `DropdownMenu` con `items: List<Pair<String, () -> Unit>>`. El menú de F08 (`DocumentScreen.MoreMenu`) pasa a usarlo, y `document_more` se retira si queda sin uso |
| `ChatPreviews.kt` | Previsualizaciones con el contenido nuevo: viñetas, fichas, negritas, notas y los tres textos de fallo |

`CatalogAccessibilityTest` (debug) cubre el componente con su contenido nuevo.

## 5. Textos nuevos (`strings_chat.xml`, español e inglés)

| Clave | Español | Inglés |
|---|---|---|
| `chat_title` | Chat con documentos | Chat with documents |
| `chat_subtitle_local` | Gemma 4 · Procesamiento local | Gemma 4 · On-device processing |
| `chat_subtitle_cpu` | Gemma 4 · Modo CPU, más lento | Gemma 4 · CPU mode, slower |
| `chat_composer_placeholder` | Pregunta a tus documentos... | Ask your documents... |
| `chat_searching` | Buscando en documentos... | Searching your documents... |
| `chat_generating` | Generando respuesta... | Generating the answer... |
| `chat_sources_header` | Fuentes consultadas | Sources consulted |
| `chat_sources_count` (plurals) | %1$d fuente / %1$d fuentes | %1$d source / %1$d sources |
| `chat_refusal` | No encuentro información suficiente en tus documentos | I can't find enough information in your documents |
| `chat_no_citations` | Esta respuesta no cita ninguna fuente de tus documentos. | This answer doesn't cite any of your documents. |
| `chat_partial_coverage` | Algunos documentos aún no están indexados: esta respuesta solo usa los que ya están listos. | Some documents aren't indexed yet: this answer only uses the ones that are ready. |
| `chat_index_failed` | No se puede abrir el índice de documentos | The document index can't be opened |
| `chat_interrupted` | La respuesta se interrumpió | The answer was interrupted |
| `chat_copied` | Respuesta copiada | Answer copied |
| `chat_models_title` | Configuración pendiente | Setup pending |
| `chat_models_body` | Completa la preparación de los modelos para poder preguntar a tus documentos. | Finish preparing the models to ask your documents. |
| `chat_models_action` | Completar configuración | Complete setup |
| `chat_empty_title` | Aún no hay documentos | No documents yet |
| `chat_empty_body` | Importa documentos para poder preguntarles. | Import documents to ask about them. |
| `chat_empty_action` | Importar documentos | Import documents |
| `chat_nothing_ready` | Aún no hay documentos listos para preguntar | No documents are ready to ask about yet |
| `chat_waiting_title` | Se están indexando tus documentos | Your documents are being indexed |
| `chat_waiting_body` | Podrás preguntar cuando termine la indexación o si la pausas. | You can ask when indexing finishes or if you pause it. |
| `chat_view_indexing` | Ver indexación | View indexing |
| `chat_new` | Nuevo chat | New chat |
| `chat_delete` | Borrar conversación | Delete conversation |
| `chat_delete_title` | ¿Borrar esta conversación? | Delete this conversation? |
| `chat_delete_body` | Se eliminarán sus preguntas y respuestas de este teléfono. | Its questions and answers will be removed from this phone. |
| `chat_delete_confirm` | Borrar | Delete |
| `chat_open_source` | Abrir fuente | Open source |
| `chat_source_sentence` | Fuente %1$d: %2$s | Source %1$d: %2$s |
| `chat_you_sentence` | Tú: %1$s | You: %1$s |
| `chat_assistant_sentence` | MicroRAG: %1$s | MicroRAG: %1$s |
| `chat_save_failed` (C16) | No se ha podido guardar esta respuesta en el teléfono. | This answer couldn't be saved on this phone. |
| `chat_storage_unavailable` (C17) | No se pueden abrir tus conversaciones guardadas. | Your saved conversations can't be opened. |
| `chat_copy_source` | [%1$d] %2$s, página %3$d | [%1$d] %2$s, page %3$d |
| `chat_copy_source_no_page` | [%1$d] %2$s | [%1$d] %2$s |
| `chat_clip_label` | Respuesta de MicroRAG | MicroRAG answer |
| `chat_announce_searching` | Buscando en documentos | Searching your documents |
| `chat_announce_generating` | Generando respuesta | Generating the answer |
| `chat_announce_completed` | Respuesta completa | Answer complete |
| `chat_announce_cancelled` | Generación cancelada | Generation cancelled |
| `chat_announce_interrupted` | La respuesta se interrumpió | The answer was interrupted |
| `chat_announce_failed` | No se pudo generar la respuesta | The answer couldn't be generated |
| `chat_announce_deleted` | Conversación borrada | Conversation deleted |

**En `strings.xml`** (los usa el design system), en los dos idiomas:

| Clave | Español | Inglés |
|---|---|---|
| `citation_fragments` | Fragmentos %1$d–%2$d | Fragments %1$d–%2$d |
| `action_more_options` | Más opciones | More options |

**Reutilizados de F01:**
- `assistant_name` («MicroRAG», D-014);
- `action_copy_answer`, `action_send`, `action_stop`, `action_cancel` y `action_retry`;
- `source_unavailable`, `citation_page`, `citation_fragment`, `answer_cancelled` y `answer_failed`.

## 6. Accesibilidad e interacción (U1–U12)

- **U1. Capas.** `feature/chat` no importa `com.lisdatasolutions.microrag.data.`, `androidx.sqlite`, `androidx.work`, `com.google.ai.edge.litertlm` ni `java.io.File` (`LayeringTest`).
- **U2. Avisos.** Una región en vivo (`liveRegion = Polite`, `stateDescription`) anuncia cada cambio de estado de la respuesta una sola vez, con los textos `chat_announce_*`: buscando, generando, completa, cancelada, interrumpida y fallo. También anuncia «Conversación borrada». Nunca lee el texto según llega (FR-039).
- **U3. Frases.**
  - La burbuja se lee como «Tú: pregunta».
  - La tarjeta se lee como «MicroRAG: respuesta», y las fichas como enlaces con «Fuente N: documento, página P».
  - Cada fila de fuente se lee como una frase (§4).
- **U4. Objetivos.** Botones, filas, chip, «⋯» y compositor miden al menos 48 dp. Las fichas dentro del texto se rigen por la excepción «inline» (research R3).
- **U5. Fuente al 200 % y horizontal.** Nada se corta; todo se alcanza desplazándose; el compositor queda visible con el teclado (SC-009).
- **U6. Teclado.** `imePadding()` en la pantalla. La barra inferior se oculta con el teclado (G39). La acción del teclado es salto de línea, como el componente de F01; se envía con el botón.
- **U7. Desplazamiento.**
  - Al enviar, la lista va al final.
  - Durante el streaming, sigue el final solo si el usuario estaba al final.
  - Al volver de una fuente, la posición se conserva (SC-006).
- **U8. Doble toque.** Abrir una fuente, «Nuevo chat» o una acción de aviso solo navega con la entrada `RESUMED`, como en Inicio.
- **U9. «⋯».**
  - «Nuevo chat» y «Borrar conversación».
  - «Borrar» solo con una conversación guardada.
  - La confirmación es `LisdataConfirmationDialog`, destructiva, con el foco en «Cancelar» (C15).
- **U10. Copiar** (research R14). El contenido es el de C10. En la API 31–32, el pie muestra «Respuesta copiada» durante 2 s y lo anuncia.
- **U11. Rotación y tema.** La pregunta escrita, el menú y el diálogo sobreviven; la respuesta en curso sigue (vive en la sesión).
- **U12. Tokens y textos.** Solo tokens de `LisdataTheme` y textos de recursos (`DesignSystemConformanceTest`).

## 7. Navegación y detalle

- **Resolución de la conversación** (research R13), en este orden: el id guardado en el `SavedStateHandle`; el `conversationId` de la ruta; una vacía si `newConversation`; la última; o una vacía.
- **Pestaña Chat (`Chat()`):** por esa regla, la última conversación.
- **«Nuevo chat» en Inicio:** `Chat(newConversation = true)`.
- **«Nuevo chat» en «⋯»:** lo resuelve el ViewModel sin navegar, y cancela la respuesta en curso (research R6).
- **Fuente →** `Document(documentId, chunkId)`. Si el fragmento ya no existe, el detalle muestra el aviso de F08 (D7).
- **Detalle de F08** (research R13): el fragmento citado se desplaza a la vista una vez por apertura, en vertical y en horizontal.
- **Vuelta del detalle:** si `LIBRARY_MESSAGE` llega a la entrada del chat, el chat lo lee y lo borra, sin mostrar nada.

## 8. Pruebas de la pantalla

| Prueba | Qué comprueba |
|---|---|
| `ChatViewModelTest` | Resolución de la ruta; `SavedStateHandle`; enviar, detener, reintentar, nuevo chat y borrar; avisos una vez por cambio; precarga solo en `Ready`; copiar (contenido C10) |
| `AnswerFormatterTest` | Viñetas, numeradas, negritas, almohadillas, comillas invertidas, renumeración, `[1, 3]`, fuera de rango, streaming estable y texto para copiar |
| `ChatScreenTest` (Robolectric, `es-w390dp-h844dp`) | Estructura del PNG; cada fila de §2 y §3; el chip lleva a las fuentes; fila disponible frente a no disponible; «⋯» y diálogo; fuente al 200 % y horizontal (`es-w844dp-h390dp`) sin cortes; restauración del desplazamiento; un caso en inglés (`en-w390dp-h844dp`) con todos los textos en inglés |
| `NavigationTest` (ampliada) | «Nuevo chat» → `newConversation = true`; la pestaña → la última; fuente → detalle → Atrás conserva conversación y posición; la barra inferior se oculta con el teclado |
| `DocumentScreenTest` (ampliada) | El fragmento citado visible sin desplazarse, en vertical y en horizontal |
| `ChatScreenAccessibilityTest` (`androidTest`, emulador) | Todos los estados de `ChatPreviewData` en claro y en oscuro: ATF, objetivos de 48 dp, horizontal real y fuente al 200 % |
| `ChatScreenLayoutTest` (`androidTest`, solo A063) | Con la barra inferior real: el compositor visible con el teclado |
| `LayeringTest` | U1 |
