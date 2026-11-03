# Research: Historial de chats (F13)

Fecha: 2026-11-02. Todas las decisiones parten de:
- el código actual (F01–F12);
- la spec aclarada (D-063), con el wireframe y las propuestas de UI H1–H11 aprobados;
- el guardado del chat de F11 (`chat.db`, research R9 de F11) y el borrado del historial de F12 (research R10 de F12).

**No hay dependencias nuevas** (R13). **No cambia el esquema de `chat.db`** (R1).

**Orden de trabajo** (petición del usuario, como en F10–F12): las pruebas en el teléfono de referencia van en la última fase. Antes van el código, las pruebas JVM y las instrumentadas en el emulador.

## R1. La lista sobre `chat.db`, sin cambio de esquema

**Problema.** No hay consulta de lista ni título guardado. El título es la primera pregunta (FR-005), que nunca cambia: las preguntas no se borran una a una y reintentar conserva el orden (`ordinal`).

**Decisión:** una consulta nueva en `SqliteChatRepository`, sobre el esquema v1:

```sql
SELECT c.id, c.updated_at,
       (SELECT substr(t.question, 1, 300) FROM turns t WHERE t.conversation_id = c.id ORDER BY t.ordinal LIMIT 1),
       (SELECT count(*) FROM turns t WHERE t.conversation_id = c.id)
FROM conversations c
WHERE EXISTS (SELECT 1 FROM turns t WHERE t.conversation_id = c.id)
ORDER BY c.updated_at DESC, c.id DESC
LIMIT ?
```

- **Orden** (FR-004): `updated_at` es la fecha de la última pregunta (`insertTurn` lo actualiza). Con la misma fecha, el `id` mayor es el creado después. El índice `conversations_updated` ya cubre el orden, y `UNIQUE(conversation_id, ordinal)` cubre las tres subconsultas.
- **Solo las conversaciones con preguntas:** el `EXISTS` descarta las que se crearon y no llegaron a guardar su primera pregunta (C16 de F11).
- **Título:**
  - los primeros 300 caracteres de la primera pregunta, que bastan para 2 líneas incluso en horizontal (unos 100 caracteres por línea a 840 dp);
  - el caso de uso normaliza espacios y saltos de línea (`\s+` → « ») y quita los extremos (FR-005);
  - el corte con «…» lo hace la pantalla (`maxLines = 2`).
- **Total:** el mismo recuento que `conversationCount()` (F12), que cuenta las mismas conversaciones que el `EXISTS`. Va en el mismo bloque de lectura que la página, así que los dos ven la misma base de datos (analyze AN-14).
- **Fallos:** si `chat.db` no está disponible (`read` devuelve null) o la lectura lanza, `conversations()` falla con `ChatStorageException` (analyze AN-07). Nunca llega a la pantalla una excepción de SQLite.
- **Registro:** cada lectura escribe `history page rows=… total=… ms=…`, solo cifras (Q13).
- **`lastConversationId()`** (F11) pasa a exigir el mismo `EXISTS` (analyze AN-15): la pestaña Chat nunca abre una conversación sin preguntas que Historial no lista.
- **Tandas:** el patrón de la Biblioteca (F08 research R2):
  - el límite crece de 50 en 50 y se guarda en el `SavedStateHandle`;
  - la consulta vuelve a leer el límite entero;
  - así no hay duplicados ni huecos cuando la lista cambia entre tandas (FR-006).

**Alternativas descartadas:**
- **Columna `title` y migración a v2:** guarda un dato que ya está en `turns`, y obliga a migrar ficheros del usuario sin ganar nada medible.
- **Paging 3:** una dependencia nueva para lo que el patrón de la Biblioteca ya resuelve.
- **Paginación por cursor** (`updated_at < ?`): con conversaciones que suben al preguntar, el cursor se desplaza y aparecen duplicados.

## R2. Cuándo se vuelve a leer la lista

**Problema.** El contador `changes` del repositorio sube con cada escritura, también con el guardado de cada segundo de una respuesta. Una lista colgada de él se volvería a leer una vez por segundo mientras se genera.

**Decisión:** un segundo contador, `listChanges`. Solo sube en las escrituras que cambian la lista:
- `insertTurn`: hay una conversación nueva o una que sube;
- `deleteConversation` y `deleteAll`.

`createConversation` no lo sube, porque una conversación sin preguntas no se lista. Tampoco `updateTurn`, `replaceSources`, `resetForRetry` ni `markInterrupted`. El flujo de la lista añade `distinctUntilChanged()`.

Con eso, durante una respuesta la lista no se vuelve a leer, no parpadea y no pierde la posición (FR-008).

**Alternativa descartada:** colgar la lista de `changes` con `distinctUntilChanged()`. Funciona, pero hace una consulta por segundo en el único hilo de la base de datos mientras se genera.

## R3. Grupos y fechas

**Decisión:** `java.time` (disponible desde API 26). El `Clock` inyectable da solo el instante. La zona se lee con `ZoneId.systemDefault()` en cada `onStart()`, y la misma zona sirve para los grupos y para los textos (analyze AN-05): un `Clock.systemDefaultZone()` fija la zona al crearse y no vería un cambio.
- **Agrupación** (`feature/history/HistoryGrouping.kt`, función pura):
  - con `LocalDate` en la zona del teléfono, se compara la fecha de la última actividad con «hoy»:
    - 0 días: `TODAY`;
    - 1 día: `YESTERDAY`;
    - de 2 a 6 días: `LAST_7_DAYS`;
    - si no, `Month(year, month)`;
  - una fecha posterior a hoy (reloj atrasado) va en `TODAY`, como dice la spec.
  - Por días de calendario, no por horas: así el cambio de hora no lo afecta.
- **«Hoy» se recalcula** cuando la pantalla vuelve a verse (`ON_START`), y también la zona horaria. Así un cambio de día o de zona con Historial abierto se ve al volver (casos límite de la spec).
- **Formatos**, todos en el idioma de la app (`LocalConfiguration.current.locales[0]`), como `importedText()`:
  - hora: patrón `Hm` o `hm`, según el formato de 24 h del sistema (`DateFormat.is24HourFormat`);
  - día de la semana: `EEEE` (es: «lunes»; en: "Monday");
  - día y mes: `dMMM` («12 oct.», "Oct 12"), sin el año, que ya lleva la cabecera;
  - cabecera del mes: `MMMMy` («octubre de 2026», "October 2026"). `LisdataSectionHeader` la pasa a mayúsculas;
  - para TalkBack, formas completas:
    - «Hoy, 10:42», «Ayer, 18:05» y «lunes, 18:05»;
    - en los grupos de mes, `dMMMMy` («12 de octubre de 2026»).
- **Grupo partido entre tandas:** la agrupación se calcula sobre todas las filas cargadas, así que cada cabecera sale una vez.

**Alternativa descartada:** `DateUtils.getRelativeTimeSpanString`. Da «hace 3 días» y no los grupos aprobados, y su idioma sigue al del sistema en lugar del de la app.

## R4. Abrir una conversación en el chat

**Problema** (R13 de F11):
- la pestaña Chat ya está en la pila, bajo Historial;
- navegar a `Chat(conversationId = id)` con `launchSingleTop` reutilizaría la entrada sin refrescar sus argumentos;
- además, el id guardado en su `SavedStateHandle` gana a la ruta.

**Decisión:** Historial devuelve un resultado a la entrada del chat, con el patrón de `LIBRARY_MESSAGE` (F08 R8):
- **Ruta:** `History(openConversationId: Long? = null)`, encima del chat y sin barra inferior.
  - El chat pasa la conversación que muestra, para el chip «Abierta» (H3).
  - El chat no cambia mientras Historial está encima, así que el valor no se queda desfasado.
- **Elegir:** tocar una fila o «＋» escribe `CHAT_REQUEST` en `previousBackStackEntry.savedStateHandle`, con `open:<id>` o `new`, y cierra Historial (`popBackStack`).
- **Recibir:**
  - `composable<Chat>` lee la clave y se la pasa a `ChatRoute` como `request`;
  - `ChatRoute` llama a `onOpenConversation(id)` o a `onNewChat()` del ViewModel y borra la clave (`onRequestHandled`);
  - así la petición se atiende una sola vez, también si se gira el teléfono (U8).
- **Un toque, una acción:** las acciones de Historial solo navegan con la entrada en `RESUMED`, como Inicio y el chat. Un doble toque no abre dos veces.
- **Atrás desde Historial:** `popBackStack`, sin resultado; el chat sigue igual (FR-002).
- **Ruta de depuración `history`** (analyze AN-06): `MicroRagAppShell` abre primero la pestaña Chat y después `History` encima. Así la petición siempre tiene a quién llegar.

**Alternativas descartadas:**
- `navigate(Chat(id)) { popUpTo<Chat> { inclusive = true } }`: crea otro ViewModel del chat. Perdería el borrador de la pregunta y la posición, y rompería «una sola instancia por pestaña» (N1).
- Una petición guardada en la sesión del chat: mezcla navegación y orquestación de respuestas.

## R5. La sesión del chat: abrir y borrar

**Decisión:** dos cambios en `ChatSession`/`DefaultChatSession`.
1. **`openConversation(id)`, suspendida y bajo el cerrojo** (FR-013). Si la respuesta en curso es de otra conversación, la detiene como «Nuevo chat» (`stop(CANCELLED)`) y **espera** a que termine de guardarse. Queda «Generación cancelada» con su texto parcial y sus citas válidas, porque `finishStopped` ya lo guarda así. Si es de la misma conversación, no hace nada.
2. **`delete(id)`, bajo el cerrojo de la sesión**, como `deleteAll` (P9 de F12):
   - para y espera solo si la respuesta en curso es de esa conversación;
   - después borra;
   - al terminar, emite el id en `deletions: SharedFlow<Long>`.

   Así ninguna pregunta empieza entre la parada y el borrado.

   `deletions` tiene un búfer (`MutableSharedFlow(extraBufferCapacity = 8)`), y el id se emite después de soltar el cerrojo (analyze AN-10). Cada borrado escribe `chat delete outcome=ok|failed` (Q13).
3. **Una respuesta parada termina antes de empezar otra** (analyze AN-04, arreglo de F11).
   - Tras `cancel()`, el trabajo deja de estar activo enseguida, pero sigue guardando su final.
   - Hoy, `ask` y `retry` podían empezar otra respuesta en ese momento. Además, el `finish()` de la vieja borraba `current`, el de la nueva, y la nueva se veía «interrumpida» mientras se generaba.
   - **Decisión:**
     - `ask` y `retry`, bajo el cerrojo, esperan (`join`) a un trabajo que se está parando, en lugar de empezar a la vez;
     - `finish()` solo limpia `current` si sigue siendo su propia respuesta.
   - Vale también para «Nuevo chat» y «Detener» de F11.

**El chat reacciona a `deletions`:** si el id es el de su conversación, vuelve a un chat vacío (`setConversation(null)`) y cierra su diálogo de borrado (FR-018). Esto cubre el borrado desde Historial, desde el propio chat y desde cualquier otro sitio.

**Por qué `SharedFlow` y no un contador como `historyCleared`:** hace falta el id. Normalmente el ViewModel del chat existe mientras Historial está abierto, porque Historial solo se abre desde él, y se suscribe en `init`.

**Tras la muerte del proceso con Historial abierto** (analyze AN-03), solo se recrea Historial. El ViewModel del chat no existe cuando se borra, y el aviso se pierde. Por eso, además:
- el chat comprueba al restaurarse (`ConversationExistsUseCase`) que su conversación guardada sigue existiendo, y si no, vuelve a un chat vacío;
- si no se puede leer, la deja como está: la pregunta siguiente ya crea otra si hace falta (F12 P10).

## R6. Borrar desde Historial, y sus fallos

**Decisión:**
- **Confirmación** (H6): el id pendiente va en el `SavedStateHandle` (`history_confirm_delete`), así que el diálogo sobrevive al giro (U9).
  - La línea «La respuesta en curso se detendrá.» sale si la respuesta en curso es de esa conversación (`ObserveAnswerInProgressUseCase`).
  - Si la conversación desaparece con el diálogo abierto, el diálogo se cierra.
- **`DeleteConversationUseCase` devuelve `Boolean` y no lanza**, como `ClearChatHistoryUseCase`.
  - Hoy lanza `ChatStorageException`, y el chat no lo captura: un fallo de borrado cerraría la app.
  - El chat también lo usa: si falla, no vacía la conversación y anuncia «No se pudo borrar la conversación.» (anuncio nuevo `DELETE_FAILED`, con el texto `history_delete_failed`). Es un arreglo pequeño sobre F11.
  - Si el borrado falla después de detener una respuesta, la conversación sigue con todas sus preguntas, y esa respuesta queda «Generación cancelada» (FR-019).
- **Avisos** en Historial, con el `SnackbarHost` de M3, como la Biblioteca:
  - «Conversación borrada» (`chat_announce_deleted`);
  - «No se pudo borrar la conversación.».

  El aviso se consume al llegar, para que no se repita al girar.
- **Documentos, índice y demás conversaciones intactos:** el borrado es `DELETE FROM conversations WHERE id = ?`, con cascada a `turns` y `sources` (F11). Una instrumentada lo comprueba (SC-005).

## R7. Fichas `[n]` de documentos borrados

**Problema.** `TurnUi.chipTargets` lleva el destino de cada ficha sin mirar la disponibilidad. Una ficha de un documento borrado navega al detalle, que se cierra, y el aviso se descarta (`MicroRagNavHost`).

**Decisión:**
- **Datos:** `chipTargets: List<SourceTarget?>`, con `null` cuando la fuente es `DOCUMENT_GONE`, según la misma disponibilidad que las filas (`ResolveSourcesUseCase`).
  - Mientras se genera, las fichas llevan su bloque del prompt, como en F11.
  - Con el documento presente y el fragmento borrado, la ficha sigue abriendo el documento (FR-011).
  - Mientras se genera, o en una respuesta sin guardar (C16), las fichas usan los bloques del prompt. También se resuelven, con la misma regla (analyze AN-19).
- **Design system:** `LisdataAnswerCard(unavailableCitations: Set<Int> = emptySet())`. `AnswerText` dibuja esas fichas:
  - sin enlace;
  - con el número en `textSecondaryOnContainer` y el fondo `neutralTile`, el gris del chip «En cola». `ContrastTest` ya comprueba ese par en claro y en oscuro (analyze AN-25);
  - en lugar del `surfaceInfo` y el `blue800` de las fichas tocables.
- **TalkBack: corrección a H7, aprobada por el usuario en el analyze (2026-11-03, D-065).**
  - Las fichas son parte del texto de la respuesta. TalkBack lee su número, y las tocables las ofrece como enlaces.
  - Compose no permite poner a una parte del texto una frase propia sin quitar los enlaces de las demás.
  - Por eso, la ficha inerte deja de ser un enlace: se lee su número y no tiene acción.
  - La frase «Fuente N: documento, Fuente ya no disponible» la sigue diciendo su fila de «Fuentes consultadas», como en F11.

  H7 decía que la ficha se leería «Fuente N: Fuente ya no disponible». Esta es la diferencia.
- **Identificadores reutilizados** (analyze AN-01, arreglo de F11):
  - `documents.id` y `chunks.id` son `INTEGER PRIMARY KEY` sin `AUTOINCREMENT`, y el borrado es físico;
  - si se borra el documento de id más alto y se importa otro, SQLite le da el mismo id, y también a sus fragmentos;
  - hoy, `ResolveSourcesUseCase` daría la cita vieja por disponible, y abriría **otro** documento con el nombre antiguo.
  - **Decisión:** `ResolveSourcesUseCase` recibe, con cada fuente, la hora de su turno. Un documento creado después del turno (`created_at_ms` mayor que `ChatTurn.createdAt`) cuenta como `DOCUMENT_GONE`. No cambia ningún esquema.
  - Una instrumentada sobre `rag.db` confirma la reutilización del id, y una prueba JVM la regla.

**Alternativas descartadas:**
- una `contentDescription` para toda la respuesta con las frases de las fichas: taparía los enlaces de las fichas tocables;
- `VerbatimTtsAnnotation`: deletrea el texto.

## R8. El chat al abrir otra conversación

**Decisión:**
- **`ChatViewModel.onOpenConversation(id)`:**
  1. `OpenConversationUseCase(id)` (R5);
  2. `setConversation(id)`;
  3. cierra el diálogo de borrado.

  El borrador de la pregunta se conserva, como con «Nuevo chat» del «⋯».
- **Desplazamiento:** en `ChatScreen`, las marcas `seen` y `follow` pasan a depender de la conversación (`rememberSaveable(conversationId)`). Así, la conversación abierta empieza en su última respuesta, aunque en la anterior el usuario se hubiera desplazado hacia arriba (FR-010, H7).
- **Menú «⋯»:** «Nuevo chat», «Historial» y, con una conversación guardada, «Borrar conversación» (G13, FR-001).
  - «Historial» abre `History(openConversationId = state.conversationId)`, solo con el chat en `RESUMED`.
- **El orden para elegir conversación** (R13 de F11, D-055) no cambia. La petición de Historial es un evento sobre una entrada ya resuelta, no una ruta nueva.

## R9. Rendimiento y medidas (SC-001, SC-010)

**Decisión:** una instrumentada nueva, `HistoryBenchmarkTest` (`androidTest/data/chat`). Usa un `chat.db` de prueba aparte, en `cacheDir/history-benchmark/`, que se borra al terminar. Nunca abre el `chat.db` de la app, y tampoco el del usuario en el teléfono.
- **Datos:**
  - 1.000 conversaciones de 5 preguntas, con fechas repartidas en 90 días;
  - preguntas de 80 caracteres y respuestas de 900, cerca de la media de las respuestas de F05;
  - 3 fuentes por respuesta.
- **Mide y escribe en una línea de log** (`history_bench …`, solo cifras):
  - el tamaño del fichero (base de datos y WAL tras un checkpoint);
  - el tiempo de la primera tanda: p50 y p95 de 20 lecturas;
  - el tiempo hasta ver la primera fila dibujada: `HistoryScreen` con el `HistoryViewModel` y el repositorio real, en una regla de Compose, hasta que aparece el título de la primera conversación (SC-001; analyze AN-26);
  - el tiempo de recorrer las 1.000 por tandas.
- **Umbral:** la primera fila dibujada por debajo de 1 s en el teléfono de referencia (SC-001). En el emulador solo se registra.
- **Dónde corre:** con la batería del emulador, y en la fase del teléfono de forma individual. Sus cifras van al informe de cierre.

## R10. Accesibilidad y diseño de la fila

**Decisión:** `LisdataConversationRow`, nueva en `Rows.kt` sobre `LisdataCard` (H3).
- **Dos nodos de accesibilidad:**
  - la tarjeta, con la acción «Abrir conversación». `clearAndSetSemantics` con la frase de H3 va solo sobre la columna de textos, no sobre la fila, para no tapar la papelera (analyze AN-20);
  - la papelera, un `IconButton` de 48 dp con «Borrar conversación: título».
- **El título** va en el estilo del nombre de las filas de documento (`titleMedium`), con `maxLines = 2`.
- **El chip «Abierta»** (`LisdataStatusChip`, `StatusKind.Info`) se coloca con la regla de F12: va al lado de la fecha si cabe entero, y si no, debajo (`TextsWithChip`). Así no se parte ninguna palabra con la letra al 200 % (H10).
- **En horizontal**, a todo el ancho. El estado vacío pasa a `compact`, sin icono, como las demás pantallas.
- **`LoadMoreNearTheEnd`** sale de `LibraryScreen` a `core/designsystem/component/Lists.kt`, sin cambiar su comportamiento, para que lo usen la Biblioteca y el Historial. Su firma deja de depender de la Biblioteca (analyze AN-13): `LoadMoreNearTheEnd(listState, loaded: Int, hasMore: Boolean, onLoadMore)`.
- **Frase de la fila** (analyze AN-11): las partes se unen con «. », pero sin añadir un punto tras un título que ya acaba en «?», «!», «.» o «…». Ejemplo: «¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas.».

## R11. Capas y pruebas

**Decisión:**
- **Capas:** `feature/history` no importa `data`, SQLite, WorkManager, LiteRT, `File` ni `LocaleManager`. Es una regla nueva en `LayeringTest`.
- **JVM:**
  - `HistoryGroupingTest`: límites de cada grupo, medianoche, fecha futura y zonas;
  - `HistoryTextsTest`, con Robolectric en español e inglés y 12 y 24 h;
  - `HistoryViewModelTest`: tandas, estados, borrado y sus fallos, petición de abrir y de «Nuevo chat», y recálculo de «hoy»;
  - `HistoryScreenTest`: estados, frases de TalkBack, diálogo con y sin la línea extra, 200 % y horizontal;
  - ampliadas:
    - `DefaultChatSessionTest`:
      - `openConversation` y su espera, el cerrojo de `delete`, `deletions` y su registro;
      - abrir o «Nuevo chat» y preguntar o reintentar al momento: la respuesta nueva no se ve interrumpida (AN-04);
      - la continuación con el mismo prompt (SC-004);
      - la parada en menos de 1 s, con tiempo virtual (SC-005);
    - `ResolveSourcesUseCaseTest`: un documento creado después del turno es `DOCUMENT_GONE` (AN-01);
    - `ChatViewModelTest`: petición, `deletions`, comprobación al restaurar (AN-03), borrado fallido, fichas inertes también durante la generación, y la conversación conservada al cambiar de pestaña o de idioma (FR-014);
    - `ChatScreenTest`: «Historial» en el menú y fichas grises sin enlace;
    - `NavigationTest`: «⋯» → Historial → abrir, «＋», Atrás, sin barra inferior;
    - `LayeringTest` y `AppModulesTest`;
  - `StringsParityTest` cubre los textos nuevos sin cambios.
- **Instrumentadas en el emulador:**
  - `SqliteChatRepositoryTest` ampliada: orden, empates, conversaciones sin preguntas, título, total, `listChanges` y borrado sin tocar otras;
  - `HistoryScreenAccessibilityTest` (ATF);
  - `HistoryBenchmarkTest` (R9);
  - `SystemBackTest`, con Atrás desde Historial, y Atrás desde el chat después de abrir una conversación desde Historial (va a Inicio, FR-003);
  - `RagIndexWriteTest` ampliada: borrar el documento de id más alto e importar otro reutiliza el id (AN-01);
  - las de F01–F12.
- **El golden del prompt** (`AnswerGoldenTest`, 426 casos) no cambia: F13 no toca `rag/answer`.

## R12. Fase del teléfono y privacidad

**Problema.** En el teléfono del usuario, Historial mostraría el título de su conversación, que es su primera pregunta.

**Decisión:** las reglas de F11 y F12, y además:
- **Capturas:**
  - las de Historial con contenido se hacen en el emulador, con conversaciones de prueba;
  - en el teléfono, Historial no se captura ni se vuelca mientras liste la conversación del usuario;
  - allí solo se comprueban etiquetas propias de la app (la lista blanca `known`), sin leer títulos.
- **La conversación de prueba de F12** se borra desde Historial solo con el permiso del usuario. La del usuario no se abre, no se captura y no se borra.
- **El recorrido del teléfono se acuerda antes** con el usuario. Para probar abrir, continuar y borrar se usan conversaciones nuevas sobre documentos de prueba, que se borran al final.
- **`HistoryBenchmarkTest`** usa su propio fichero (R9).
- **Red y registros:**
  - 0 bytes para el uid de la app;
  - los registros solo llevan ids y cifras: `history page …` con número de filas y milisegundos, nunca títulos.

## R13. Versiones

**Decisión:** sin dependencias nuevas ni cambios de versión. Lo que F13 usa ya está en el proyecto: Compose BOM 2026.09.00, navigation 2.10.2, lifecycle 2.11.0, Koin 4.2.2 y `java.time` de la plataforma. Las versiones vigentes se verificaron en Google Maven en F12 (research R16 de F12).

El aviso de lint de LiteRT-LM 0.19.0 sigue fuera de alcance. Se decide aparte (memoria de F11).

## R14. Casos de uso que se parecen

`ObserveAnswerInProgressUseCase` (nuevo) y `IsAnswerInProgressUseCase` (F12) no se unifican (analyze AN-16): el de F12 es una lectura puntual al abrir el diálogo de Ajustes, y el nuevo es un flujo con el id de la conversación, que Historial necesita para cada fila pendiente.
