# Contrato: pantalla Historial y cambios en el chat (F13)

Wireframe aprobado: [wireframe.md](../wireframe.md). Las propuestas H1–H11 están en la spec. En el analyze cambiaron H5 (sin «Reintentar») y la lectura de TalkBack de las fichas inertes de H7 (research R7, D-065). Solo tokens y componentes `Lisdata*` (constitución VII).

## 1. Historial (`feature/history`)

| Zona | Componente | Contenido |
|---|---|---|
| Barra superior | `LisdataTopBar(title, onBack, actions)` | «Historial». Acción: `IconButton` con `LisdataIcon(Plus)`, descrita como «Nuevo chat», como el «＋» de la Biblioteca |
| Lista | `LazyColumn` con `LoadMoreNearTheEnd` | Las secciones en orden. Cada sección es un `LisdataSectionHeader` y sus filas `LisdataConversationRow`, con clave por id |
| Aviso | `SnackbarHost` de M3, abajo | «Conversación borrada» o «No se pudo borrar la conversación.», consumidos al llegar |
| Diálogo | `LisdataConfirmationDialog` | §3 |

Sin barra inferior: no es raíz de pestaña (N6).

**Estados** (FR-007):

| Estado | Qué se ve |
|---|---|
| `Loading` | 4 `LisdataSkeletonRow`, leídas como «Cargando» |
| `Empty` | `LisdataEmptyState(icono Chat, «Aún no hay conversaciones», «Las conversaciones que tengas en el chat aparecerán aquí.», acción «Nuevo chat»)`, `compact` en horizontal |
| `Error` | `LisdataErrorState(«No se pueden abrir tus conversaciones guardadas.», onRetry = null)`, sin botones (D-065). No se ofrece abrir ni borrar |
| `Sections` | La lista. Al acercarse al final con `hasMore`, `onLoadMore` |

## 2. Fila de conversación (`LisdataConversationRow`, nueva en `Rows.kt`)

```kotlin
@Composable
fun LisdataConversationRow(
    title: String,
    meta: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
    modifier: Modifier = Modifier,
    chipText: String? = null,
    accessibilityText: String? = null,
    onClickLabel: String? = null,
)
```

- **`LisdataCard(onClick = onOpen, onClickLabel)`**, con una fila:
  - una columna: el título (`titleMedium`, `textPrimary`, `maxLines = 2`, `Ellipsis`), y debajo el `meta` (`bodyMedium`, `textSecondary`) con el chip;
  - la papelera, arriba a la derecha.
- **Chip** (`LisdataStatusChip(chipText, StatusKind.Info)`): al lado del `meta` si cabe sin partir palabras, y si no, debajo. Es la regla `TextsWithChip` de F12.
- **Papelera:** `IconButton` de 48 dp con `LisdataIcon(Trash, contentDescription = deleteLabel, tint = textSecondary)`. Sin chevron.
- **Semántica:**
  - la tarjeta lleva la acción `onClickLabel`;
  - `clearAndSetSemantics { contentDescription = accessibilityText }` va solo sobre la columna de textos, no sobre la fila entera, para no tapar la papelera;
  - la papelera, un nodo aparte.
- **Vistas previas y catálogo de depuración:** título corto y largo, con y sin chip, claro y oscuro, y letra al 200 %.

**Textos de la fila** (`feature/history/HistoryTexts.kt`, research R3):

| Grupo | `meta` | Fecha en la frase de TalkBack |
|---|---|---|
| Hoy | «10:42 · 3 preguntas» | «Hoy, 10:42» |
| Ayer | «18:05 · 1 pregunta» | «Ayer, 18:05» |
| Últimos 7 días | «lunes · 2 preguntas» | «lunes, 18:05» |
| Mes | «12 oct. · 5 preguntas» | «12 de octubre de 2026» |

- **Frase de la fila:** «título. fecha. N preguntas.», más « Abierta.» si es la del chat. No se añade un punto tras un título que ya acaba en «?», «!», «.» o «…»: «¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas.».
- **Acción:** «Abrir conversación».
- **Papelera:** «Borrar conversación: título».

## 3. Diálogo de borrado (H6)

- **Textos:**
  - título: `chat_delete_title`, «¿Borrar esta conversación?»;
  - cuerpo: `chat_delete_body`, «Se eliminarán sus preguntas y respuestas de este teléfono.»;
  - si la conversación pendiente es la de la respuesta en curso (Q9), se añade en un párrafo `settings_clear_body_answering`, «La respuesta en curso se detendrá.»;
  - botones: «Cancelar», con el foco por defecto, y `chat_delete_confirm`, «Borrar», en rojo (`destructive = true`).
- **El id pendiente** va en el `SavedStateHandle` (`history_confirm_delete`).
- **Al confirmar:** se cierra el diálogo, se llama a `DeleteConversationUseCase` y sale el aviso de §1.

## 4. Textos nuevos (`strings_history.xml`, español e inglés)

| Clave | Español | Inglés |
|---|---|---|
| `history_title` | Historial | History |
| `history_today` | Hoy | Today |
| `history_yesterday` | Ayer | Yesterday |
| `history_last_7_days` | Últimos 7 días | Last 7 days |
| `history_questions` (plural) | %1$d pregunta / %1$d preguntas | %1$d question / %1$d questions |
| `history_meta` | %1$s · %2$s | %1$s · %2$s |
| `history_open_chip` | Abierta | Open |
| `history_row_sentence` | %1$s %2$s. %3$s. | %1$s %2$s. %3$s. |
| `history_row_sentence_open` | %1$s %2$s. %3$s. Abierta. | %1$s %2$s. %3$s. Open. |
| `history_today_at` | Hoy, %1$s | Today, %1$s |
| `history_yesterday_at` | Ayer, %1$s | Yesterday, %1$s |
| `history_weekday_at` | %1$s, %2$s | %1$s, %2$s |
| `history_open_action` | Abrir conversación | Open conversation |
| `history_delete_label` | Borrar conversación: %1$s | Delete conversation: %1$s |
| `history_empty_title` | Aún no hay conversaciones | No conversations yet |
| `history_empty_body` | Las conversaciones que tengas en el chat aparecerán aquí. | Your chat conversations will appear here. |
| `history_delete_failed` | No se pudo borrar la conversación. | Couldn't delete the conversation. |

**Se reutilizan:**
- `chat_new` («Nuevo chat»), para la acción «＋» y el botón del estado vacío (FR-023);
- `chat_delete_title`, `chat_delete_body`, `chat_delete_confirm` y `chat_announce_deleted`;
- `settings_clear_body_answering`;
- `chat_storage_unavailable`;
- `action_cancel`, `action_back` y `state_loading`.

**Plantillas:** en `history_row_sentence*`, `%1$s` es el título ya terminado en signo: el código le añade «.» solo si no acaba en «?», «!», «.» o «…».

**Chat:** el anuncio `DELETE_FAILED` usa `history_delete_failed`.

**Menú del chat:** «Historial» usa `history_title`.

## 5. Navegación

| Desde | Acción | Destino |
|---|---|---|
| Chat, «⋯» | «Historial» | `History(openConversationId = conversación del chat)`, solo desde `RESUMED` y con `launchSingleTop` |
| Historial | Atrás (flecha o gesto) | `popBackStack()`: el chat, sin cambios |
| Historial | Tocar una fila | `CHAT_REQUEST = "open:<id>"` en la entrada anterior y `popBackStack()` |
| Historial | «＋» o «Nuevo chat» del estado vacío | `CHAT_REQUEST = "new"` y `popBackStack()` |
| Chat (entrada) | `CHAT_REQUEST` presente | `onOpenConversation(id)` o `onNewChat()`, y se borra la clave |
| Chat con una conversación abierta desde Historial | Atrás | Inicio (G08), como siempre |

Ruta de depuración: `--es com.lisdatasolutions.microrag.debug.EXTRA_ROUTE history`.

## 6. Accesibilidad e interacción (U1–U10)

- **U1. Capas:** `feature/history` no importa `data.`, `androidx.sqlite`, `androidx.work`, LiteRT, `java.io.File` ni `android.app.LocaleManager`. Es una regla nueva en `LayeringTest`.
- **U2. Encabezados:** las cabeceras de grupo usan `LisdataSectionHeader`, con semántica de encabezado. El título de la barra también es encabezado (F01).
- **U3. Frases:** cada fila se lee como una frase (§2), y la papelera aparte. Las fechas de TalkBack son las largas de §2.
- **U4. Objetivos táctiles de 48 dp:** tarjeta, papelera, «＋», Atrás y botones de estado.
- **U5. Avisos:** el borrado y su fallo se anuncian una vez, por el `SnackbarHost`. El chat anuncia `DELETE_FAILED` por su región en vivo (U2 de F11).
- **U6. Letra al 200 % y horizontal:**
  - el título sigue en 2 líneas como mucho y las filas crecen en alto;
  - el chip baja bajo el `meta` si no cabe;
  - nada se corta, y todo se alcanza desplazando.
- **U7. Modo oscuro:** solo tokens.
- **U8. Una sola vez:**
  - las acciones navegan solo en `RESUMED`;
  - `CHAT_REQUEST` se atiende una vez, también tras girar;
  - un doble toque en la papelera abre un solo diálogo.
- **U9. Giro y tema:** el diálogo (id en `SavedStateHandle`), la posición de la lista, el límite de la tanda y el menú «⋯» del chat sobreviven.
- **U10. Pruebas:**
  - ATF (`enableAccessibilityChecks`) en Historial con contenido, vacío y error, y en el diálogo, en claro y en oscuro, al 200 % y en horizontal;
  - Robolectric para U2–U6 y U8.

**Fichas de documentos borrados (research R7):**
- se dibujan con el número en `textSecondaryOnContainer` sobre `neutralTile`, el par del chip «En cola» que ya comprueba `ContrastTest`, sin enlace;
- TalkBack lee su número sin acción;
- la frase «Fuente N: documento, Fuente ya no disponible» está en su fila de fuentes.

## 7. Diferencias con el wireframe (para el informe de cierre)

- **H7, TalkBack de las fichas inertes:** se lee su número, sin acción, en lugar de «Fuente N: Fuente ya no disponible» (research R7). Aprobado en el analyze (D-065).
- **H5, error:** sin «Reintentar» (D-065).
- Las demás que salgan en la implementación se anotan aquí.
