---

description: "Lista de tareas de F13 — Historial de chats"
---

# Tasks: Historial de chats (F13)

**Input**: Documentos de diseño en `/specs/013-chat-history/`: [plan.md](plan.md), [spec.md](spec.md), [wireframe.md](wireframe.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-011). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario, en orden de prioridad. Las pruebas en el teléfono van en la **última fase** (petición del usuario).

Historias de la spec:
- US1 — Ver mis conversaciones guardadas (P1);
- US2 — Abrir y continuar una conversación (P1);
- US3 — Borrar una conversación desde el historial (P1);
- US4 — Empezar un chat nuevo desde el historial (P2);
- US5 — Una lista siempre al día (P2);
- US6 — Pantalla aprobada, accesible y traducida (P2);
- US7 — Almacenamiento medido (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Python:** siempre `offline/.venv/bin/python -I`. F13 no cambia el escritorio; solo se comprueba que sigue igual.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37), sin ventana. Se arrancan cuando hacen falta y se cierran al terminar la fase;
  - teléfono de referencia `adb -s P31281001066`, **solo en la última fase**.

  `adb` está en `$HOME/Library/Android/sdk/platform-tools/adb`.
  - En los emuladores vale `connectedDebugAndroidTest`, que desinstala la app al acabar.
  - En el teléfono, **nunca**: se instalan los dos APK y se usa `am instrument`, con la salida a un fichero y la pantalla despierta (`KEYCODE_WAKEUP` cada 60 s).
- **En el teléfono:**
  - nunca se borran ni se vuelven a descargar los modelos;
  - **«Borrar historial» no se confirma sin permiso expreso del usuario**;
  - la conversación del usuario no se abre, no se captura y no se borra;
  - **Historial no se captura ni se vuelca mientras liste la conversación del usuario** (research R12): solo se comprueban etiquetas propias de la app;
  - la pestaña Chat abre la última conversación: antes de preguntar, «⋯» → «Nuevo chat», y se comprueba sin capturar;
  - los volcados de UI van a `/data/local/tmp` y se borran al momento; nunca `/sdcard/ui.xml`;
  - las capturas recortan la barra de estado (unos 130 px en vertical y 110 px en horizontal);
  - los registros se filtran a la app;
  - los ajustes se restauran.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US7).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup

**Purpose**: los textos y la regla de capas que necesitan todas las historias.

- [X] T001 [P] Textos `app/src/main/res/values/strings_history.xml` y `app/src/main/res/values-en/strings_history.xml`, con todas las claves de `history-ui.md` §4. `StringsParityTest` pasa con ellos.
- [X] T002 [P] Regla nueva en `…/test/…/architecture/LayeringTest.kt` (U1): `feature/history` no importa `data.`, `androidx.sqlite`, `androidx.work`, LiteRT, `java.io.File` ni `android.app.LocaleManager`. Falla hasta que existe `feature/history`, y pasa con T016.

---

## Phase 2: Foundational (datos, sesión, casos de uso y componentes)

**Purpose**: la lista sobre `chat.db`, la sesión con `openConversation` y `deletions`, los casos de uso y las piezas del design system. **Bloquea US1–US7.**

### Pruebas (antes, y deben fallar)

- [X] T003 [P] Ampliar `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt` (research R1, R2; Q1–Q5):
  - **orden:** `updated_at DESC`; con la misma fecha, va primero el id mayor;
  - **qué entra:** una conversación sin preguntas (creada sin turno) no aparece;
  - **título:** es la pregunta de `ordinal` más bajo, cortada a 300 caracteres;
  - **recuento:** `questions` es el número de turnos, y `total` coincide con `conversationCount()`;
  - **tandas:** `limit` 50 y 100 sobre 120 conversaciones, sin duplicados ni huecos aunque entre las dos lecturas se pregunte en una antigua;
  - **`listChanges`:** el flujo emite tras `insertTurn`, `deleteConversation` y `deleteAll`, y no tras `createConversation`, `updateTurn`, `replaceSources`, `resetForRetry` ni `markInterrupted`;
  - **borrar una:** las demás conversaciones, con sus turnos y fuentes, quedan intactas (SC-005);
  - **página y total** se leen juntos: nunca `hasMore` con todas las filas cargadas;
  - **`lastConversationId()`** ignora una conversación sin preguntas, aunque sea la más reciente (Q1);
  - **registro:** `history page rows=… total=… ms=…`, solo cifras (`CapturingLogger`, Q13);
  - **fichero no disponible** (versión más nueva): el flujo falla con `ChatStorageException`, nunca con una excepción de SQLite (Q5).

  Y en `…/androidTest/…/data/rag/RagIndexWriteTest.kt`: borrar el documento de id más alto e importar otro le da el mismo id (confirma la premisa de Q15).
- [X] T004 [P] Ampliar `…/test/…/data/chat/DefaultChatSessionTest.kt` y `…/test/…/testing/ChatFakes.kt` (research R5; Q6–Q8, Q10):
  - **`openConversation(otra)`** con una respuesta en curso: queda `CANCELLED` con su texto parcial y sus citas válidas, y `openConversation` vuelve cuando ya está guardada;
  - **`openConversation(la misma)`:** la respuesta sigue;
  - **Q14:** tras `openConversation`, `startNewConversation` o `cancel`, un `ask` o un `retry` inmediatos esperan a que la respuesta anterior termine, y la nueva no se ve «interrumpida» ni pierde su `active`;
  - **`delete`:**
    - un `ask` lanzado a la vez no empieza entre la parada y el borrado;
    - para y espera solo si la respuesta es de esa conversación;
    - `deletions` emite el id tras un borrado confirmado, y no tras un fallo;
    - `deleteAll` no emite en `deletions`;
    - cada borrado escribe `chat delete outcome=ok|failed` (Q13);
    - con tiempo virtual, la respuesta en curso se detiene en menos de 1 s (SC-005);
  - **SC-004:** preguntar en una conversación antigua, que no es la última, construye el mismo prompt que con esa conversación abierta como la última: el mismo turno anterior y el mismo texto que recibe el motor.
- [X] T005 [P] `…/test/…/domain/chat/HistoryUseCasesTest.kt` (Q5, Q7, Q9):
  - `ObserveConversationsUseCase`:
    - pasa de `Loading` a `Page`;
    - normaliza los títulos (`\s+` → « », `trim`);
    - un fallo del flujo da `Unavailable`;
    - un nuevo límite vuelve a leer;
  - `ObserveAnswerInProgressUseCase`: el id solo en `RETRIEVING` y `GENERATING`;
  - `DeleteConversationUseCase`: `false` con `ChatStorageException`, sin lanzar;
  - `ConversationExistsUseCase`: `true` si no se puede leer (Q16).

  Y ampliar `…/test/…/domain/chat/ResolveSourcesUseCaseTest.kt` (Q15): un documento con `created_at_ms` posterior a la hora del turno es `DOCUMENT_GONE`; uno anterior sigue `AVAILABLE` o `FRAGMENT_GONE` como en F11.
- [X] T006 [P] `…/test/…/core/designsystem/component/ConversationRowTest.kt` (Robolectric, `@GraphicsMode(NATIVE)`, calificadores `es-w390dp-h844dp`; research R10, U3, U4, U6):
  - dos nodos: la tarjeta, con la frase y la acción «Abrir conversación», y la papelera, con «Borrar conversación: título»; la frase va solo sobre la columna de textos y no tapa la papelera;
  - la papelera mide 48 dp;
  - el título va en 2 líneas como mucho;
  - el chip va al lado del `meta` a 390 dp, y debajo con la letra al 200 % sin partir palabras;
  - tocar la tarjeta abre y tocar la papelera borra.

### Implementación

- [X] T007 Modelos y repositorio:
  - `ConversationSummary`, `ConversationPage`, `HistoryLimits` y `HistoryContent` en `…/microrag/domain/chat/ChatModels.kt`;
  - `conversations(limit)` en `…/microrag/domain/chat/ChatRepository.kt`;
  - la consulta de research R1, con el total en el mismo bloque y los fallos como `ChatStorageException`, `listChanges` y `lastConversationId()` con `EXISTS`, en `…/microrag/data/chat/SqliteChatRepository.kt`;
  - lo mismo en `InMemoryChatRepository` (`ChatFakes.kt`).

  T003 pasa en el emulador `Pixel_10`.
- [X] T008 Sesión, en `…/microrag/domain/chat/ChatSession.kt` y `…/microrag/data/chat/DefaultChatSession.kt`, y en `FakeChatSession`:
  - `openConversation`, suspendida, bajo el cerrojo, con espera;
  - `delete` bajo el cerrojo, con su registro, y `deletions` con búfer, emitido fuera del cerrojo;
  - Q14 en `ask`, `retry` y `finish()`.

  T004 pasa, y las pruebas de F11 y F12 de la sesión siguen pasando sin cambios.
- [X] T009 Casos de uso:
  - `ObserveConversationsUseCase`, `OpenConversationUseCase`, `ObserveConversationDeletionsUseCase`, `ObserveAnswerInProgressUseCase` y `ConversationExistsUseCase` en `…/microrag/domain/chat/ChatUseCases.kt`, registrados en `…/microrag/domain/di/DomainModule.kt`;
  - `DeleteConversationUseCase` devuelve `Boolean`;
  - `ResolveSourcesUseCase` recibe `CitedSource` y aplica Q15; `ChatViewModel` le pasa la hora de cada turno;
  - `ChatViewModel.onConfirmDelete` se adapta sin cambiar aún de comportamiento (el fallo llega en T030).

  T005 pasa.
- [X] T010 [P] Mover `LoadMoreNearTheEnd` de `…/microrag/feature/library/LibraryScreen.kt` a `…/microrag/core/designsystem/component/Lists.kt`, con la firma `LoadMoreNearTheEnd(listState, loaded: Int, hasMore: Boolean, onLoadMore)` y sin cambiar su comportamiento. Las pruebas de la Biblioteca siguen pasando sin tocarlas.
- [X] T011 [P] `LisdataConversationRow` en `…/microrag/core/designsystem/component/Rows.kt` (`history-ui.md` §2):
  - con sus vistas previas (título corto y largo, con y sin chip, claro y oscuro, 200 %);
  - con su entrada en `…/debug/…/catalog/CatalogEntries.kt`.

  T006 pasa.

**Checkpoint**: datos, sesión y componentes listos.

---

## Phase 3: User Story 1 - Ver mis conversaciones guardadas (Priority: P1) 🎯 MVP

**Goal**: «⋯» → «Historial» muestra las conversaciones agrupadas por fecha, con título, fecha y número de preguntas, por tandas.

**Independent Test**: con 0, 1 y más de 50 conversaciones, la lista sale completa, en orden, sin vacías y sin duplicados.

### Pruebas

- [X] T012 [P] [US1] `…/test/…/feature/history/HistoryGroupingTest.kt` (research R3, data-model §5):
  - límites de «Hoy», «Ayer», «Últimos 7 días» (2 a 6 días) y del mes;
  - medianoche exacta;
  - fecha futura en «Hoy»;
  - zonas `Europe/Madrid` con cambio de hora, `UTC` y `Pacific/Kiritimati`;
  - cada grupo aparece una vez y en orden, y los vacíos no salen.
- [X] T013 [P] [US1] `…/test/…/feature/history/HistoryTextsTest.kt` (Robolectric; `history-ui.md` §2):
  - `meta` y fecha de TalkBack de cada grupo, en español y en inglés, y en 12 y 24 h;
  - cabecera del mes («octubre de 2026», "October 2026");
  - plural de «pregunta»;
  - frase de la fila con y sin «Abierta», con el texto exacto del wireframe: sin punto tras un título que acaba en «?», «!», «.» o «…».
- [X] T014 [P] [US1] `…/test/…/feature/history/HistoryViewModelTest.kt` (lista):
  - `Loading` → `Sections`, `Empty` o `Error`;
  - «Reintentar» vuelve a leer;
  - `onLoadMore` suma 50 solo con `hasMore` y la tanda completa; el límite sobrevive en el `SavedStateHandle`;
  - `onStart()` recalcula los grupos con el instante del `Clock` y la zona de `ZoneId.systemDefault()`: un cambio de la zona por defecto entre dos `onStart()` mueve las filas de grupo;
  - `openConversationId` de la ruta llega al estado;
  - los registros solo llevan cifras (Q13).
- [X] T015 [P] [US1] `…/test/…/feature/history/HistoryScreenTest.kt` (Robolectric):
  - los cuatro estados de `history-ui.md` §1; el de error, sin botones (D-065);
  - cabeceras como encabezados (U2);
  - frase y acción de cada fila (U3);
  - chip «Abierta» solo en la del chat;
  - con la letra al 200 % y en horizontal, la papelera sigue entera y el título no pasa de 2 líneas;
  - al acercarse al final, pide la tanda siguiente.

### Implementación

- [X] T016 [US1] `…/microrag/feature/history/HistoryGrouping.kt` y `HistoryTexts.kt`. T012, T013 y T002 pasan.
- [X] T017 [US1] `HistoryUiState.kt`, `HistoryViewModel.kt` (lista), `HistoryScreen.kt`, `HistoryRoute.kt` y `HistoryPreviewData.kt` en `…/microrag/feature/history/`, con su registro en `…/microrag/feature/di/FeatureModule.kt` y `AppModulesTest`. T014 y T015 pasan.
- [X] T018 [P] [US1] Navegación, pruebas primero (el regreso con una conversación elegida va en T022):
  - **`NavigationTest`:**
    - «⋯» del chat ofrece «Historial», también con el chat vacío;
    - abre `History(openConversationId)` sin barra inferior;
    - Atrás vuelve al chat sin cambiar su conversación;
    - un doble toque abre un solo Historial;
  - **`ChatScreenTest`:** el orden «Nuevo chat», «Historial», «Borrar conversación»;
  - **Atrás del sistema desde Historial:** en `NavigationTest`, con `Espresso.pressBack()`. No en `SystemBackTest`, que corre en el teléfono de referencia sobre las conversaciones reales: Historial listaría las del usuario (research R12).
- [X] T019 [US1] Navegación:
  - `History` en `…/microrag/core/navigation/Destinations.kt`;
  - `composable<History>` en `MicroRagNavHost.kt`;
  - «Historial» y `onOpenHistory` (solo en `RESUMED`) en `…/microrag/feature/chat/ChatScreen.kt`;
  - la ruta `history` en `…/debug/…/DebugRouting.kt`, y en `…/microrag/core/navigation/MicroRagAppShell.kt` que esa ruta abra antes la pestaña Chat (research R4).

  T018 pasa.

**Checkpoint**: Historial se abre y lista. Se valida en la JVM.

---

## Phase 4: User Story 2 - Abrir y continuar una conversación (Priority: P1)

**Goal**: tocar una fila abre esa conversación en el chat, en su última respuesta. Se puede seguir preguntando. Las fuentes de documentos borrados no abren nada.

**Independent Test**: una conversación con todos los estados de turno y un documento citado borrado se abre igual que se guardó. La ficha `[n]` del documento borrado no navega, y una pregunta nueva se guarda en ella.

### Pruebas

- [X] T020 [P] [US2] Ampliar `…/test/…/feature/chat/ChatViewModelTest.kt` (Q11, Q12):
  - `onOpenConversation(id)`:
    - llama a `OpenConversationUseCase`;
    - muestra esa conversación y la guarda en el `SavedStateHandle`;
    - cierra el diálogo de borrado y conserva el borrador;
    - la pregunta siguiente va a ese id;
  - `chipTargets`:
    - `null` para `DOCUMENT_GONE`;
    - el destino para `FRAGMENT_GONE` y `AVAILABLE`;
    - mientras se genera y en una respuesta sin guardar, también `null` si el documento del bloque se borró;
  - Q16: con una conversación guardada en el `SavedStateHandle` que ya no existe, el chat se restaura vacío; si no se puede leer, la conserva;
  - FR-014: la conversación abierta sigue tras recrear el ViewModel con su `SavedStateHandle`;
  - SC-002: todos los estados de turno se restauran igual.
- [X] T021 [P] [US2] Ampliar `…/test/…/feature/chat/ChatScreenTest.kt`:
  - las fichas de `unavailableCitations` no tienen enlace ni acción y se dibujan con el estilo inerte;
  - al abrir otra conversación, la lista va a su última respuesta aunque en la anterior se hubiera subido (R8).
- [X] T022 [P] [US2] Ampliar `NavigationTest` (research R4, U8):
  - tocar una fila vuelve al chat con esa conversación;
  - `CHAT_REQUEST` se atiende una vez, también si se recrea la actividad con Historial abierto antes de elegir;
  - un doble toque en una fila abre una sola vez;
  - FR-014: la conversación sigue al cambiar de pestaña y volver al chat;
  - FR-003: Atrás desde el chat, tras abrir una conversación desde Historial, lleva a Inicio, con `Espresso.pressBack()`. No en `SystemBackTest`, por la misma razón que en T018;
  - `NavigationRestorationTest`: la petición sobrevive a una recreación con Historial abierto y se atiende una sola vez.

### Implementación

- [X] T023 [US2] Chat:
  - `onOpenConversation` y `chipTargets: List<SourceTarget?>` en `…/microrag/feature/chat/ChatViewModel.kt` y `ChatUiState.kt`;
  - `unavailableCitations` en `LisdataAnswerCard` y las fichas inertes en `AnswerText` (`…/microrag/core/designsystem/component/Chat.kt`), con su entrada en el catálogo;
  - `ChatScreen` pasa las fichas y guarda `seen` y `follow` por conversación.

  T020 y T021 pasan.
- [X] T024 [US2] Petición de Historial:
  - `CHAT_REQUEST` en `MicroRagNavHost.kt`;
  - `request` y `onRequestHandled` en `ChatRoute`;
  - el evento `Open(id)` en `HistoryViewModel` y `HistoryRoute`.

  T022 pasa.

**Checkpoint**: abrir y continuar funcionan, y las historias P1 de lectura están completas.

---

## Phase 5: User Story 3 - Borrar una conversación desde el historial (Priority: P1)

**Goal**: la papelera pide confirmación y borra solo esa conversación. El chat se entera, y un fallo se avisa sin quitar nada.

**Independent Test**: con varias conversaciones, una abierta en el chat y otra con una respuesta en curso: «Cancelar» no borra nada; «Borrar» borra solo la elegida; el chat queda vacío si era la suya; la respuesta se detiene antes.

### Pruebas

- [X] T025 [P] [US3] Ampliar `HistoryViewModelTest` (borrado, research R6):
  - la papelera guarda el id pendiente en el `SavedStateHandle`, que sobrevive a la recreación;
  - `confirmDeleteAnswering` es verdadero solo si esa conversación se está respondiendo;
  - «Cancelar» lo limpia;
  - al confirmar, el evento es `Deleted`, o `DeleteFailed` con la fila intacta;
  - si la conversación pendiente desaparece, el diálogo se cierra.
- [X] T026 [P] [US3] Ampliar `HistoryScreenTest`:
  - el diálogo con los textos de C15, con y sin «La respuesta en curso se detendrá.», y el foco en «Cancelar»;
  - los avisos «Conversación borrada» y «No se pudo borrar la conversación.», uno cada vez;
  - un doble toque en la papelera abre un solo diálogo.
- [X] T027 [P] [US3] Ampliar `ChatViewModelTest` (Q8, R6):
  - `deletions` con el id del chat: chat vacío y diálogo cerrado;
  - con otro id: nada;
  - `onConfirmDelete` fallido: la conversación sigue y se anuncia `DELETE_FAILED`;
  - tras borrar su conversación desde Historial, la pregunta siguiente del chat empieza una conversación nueva (SC-005).

### Implementación

- [X] T028 [US3] Borrado en Historial: `HistoryViewModel`, el diálogo (`LisdataConfirmationDialog`) y el `SnackbarHost` en `HistoryScreen`. T025 y T026 pasan.
- [X] T029 [US3] Chat: la suscripción a `deletions` en `init` de `ChatViewModel`. T027 pasa en su primera parte.
- [X] T030 [US3] Chat: el fallo de borrado, con `ChatAnnouncement.DELETE_FAILED` y el texto `history_delete_failed` en la región en vivo de `ChatScreen`. T027 pasa entera.

**Checkpoint**: las tres historias P1 completas. Se valida en la JVM y en el emulador.

---

## Phase 6: User Story 4 - Empezar un chat nuevo desde el historial (Priority: P2)

**Goal**: «＋» y el «Nuevo chat» del estado vacío dejan la pestaña Chat vacía.

**Independent Test**: desde Historial, «＋» abre un chat vacío y detiene una respuesta en curso. La conversación nueva no aparece en la lista hasta su primera pregunta.

- [X] T031 [P] [US4] Pruebas:
  - **`NavigationTest`:** «＋» y el botón del vacío vuelven al chat vacío por `CHAT_REQUEST = "new"`;
  - **`HistoryViewModelTest`:** el evento `NewChat`;
  - **`ChatViewModelTest`:** la petición `new` llama a `StartNewConversationUseCase` y deja el chat vacío;
  - **`HistoryViewModelTest`, con el repositorio falso:** un chat nuevo sin preguntas no aparece en la lista.
- [X] T032 [US4] Implementar el evento `NewChat` y su paso a `onNewChat()` del chat. T031 pasa.

---

## Phase 7: User Story 5 - Una lista siempre al día (Priority: P2)

**Goal**: la lista refleja las preguntas nuevas y los borrados hechos desde el chat y desde Ajustes, sin parpadear durante una respuesta.

**Independent Test**: con Historial abierto y una respuesta generándose, la lista no cambia. Tras preguntar, borrar desde el chat o «Borrar historial» en Ajustes, sí cambia.

- [X] T033 [P] [US5] Ampliar `HistoryViewModelTest` con `InMemoryChatRepository`:
  - una pregunta en una conversación antigua la sube la primera, a «Hoy»;
  - borrar desde el chat la quita;
  - `deleteAll` deja `Empty` (SC-006);
  - los guardados de una respuesta en curso no producen estados nuevos (Q4).

  La parte de SQLite ya la cubre T003. Lo que T033 encuentre se arregla dentro de la misma tarea.
- [X] T034 [US5] **Retirada (analyze, AN-28):** fusionada con T033.

---

## Phase 8: User Story 6 - Pantalla aprobada, accesible y traducida (Priority: P2)

**Goal**: Historial cumple U1–U10 y el wireframe, en español y en inglés.

**Independent Test**: ATF pasa en todas las combinaciones, y las capturas del emulador coinciden con el wireframe.

- [ ] T035 [P] [US6] `…/androidTest/…/feature/history/HistoryScreenAccessibilityTest.kt` (ATF, `enableAccessibilityChecks`):
  - Historial con contenido, vacío y error, y el diálogo;
  - en claro y en oscuro, con la letra al 100 % y al 200 %, y en vertical y en horizontal.
- [ ] T036 [US6] Ajustar la pantalla hasta que T035 pase en el emulador. Si ATF marca algo que contradice el wireframe o una propuesta aprobada, **se para y se consulta**.
- [ ] T037 [P] [US6] `StringsParityTest` y la revisión del inglés de `strings_history.xml` y de los textos tocados del chat. Las correcciones van al informe de cierre.

---

## Phase 9: User Story 7 - Almacenamiento medido (Priority: P3)

**Goal**: cifras de tamaño y de apertura con un historial grande, en un fichero de prueba aparte.

**Independent Test**: la prueba escribe su línea `history_bench`, borra su fichero y no toca el `chat.db` de la app.

- [ ] T038 [US7] `…/androidTest/…/data/chat/HistoryBenchmarkTest.kt` (research R9):
  - 1.000 conversaciones de 5 preguntas, en `cacheDir/history-benchmark/chat.db`;
  - tamaño, primera tanda (p50 y p95 de 20 lecturas), tiempo hasta ver la primera fila dibujada (`HistoryScreen` con el `HistoryViewModel` real, en una regla de Compose) y recorrido completo;
  - una línea de log solo con cifras;
  - el fichero se borra en `@After`.

  Se ejecuta en `Pixel_10` y las cifras van al borrador del informe.

---

## Phase 10: Polish sin teléfono

- [ ] T039 Pasar toda la batería:
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`, solo con el aviso previo de LiteRT-LM 0.19.0;
  - `offline/.venv/bin/python -I -m unittest discover -s offline/tests`;
  - `offline/.venv/bin/python -I offline/gen_answer_golden.py --check`.
- [ ] T040 Batería instrumentada completa en `Pixel_10` y en `MicroRAG_API33`, con los emuladores sin ventana, que se cierran al terminar. En API 33 siguen las 4 fallos previos de Atrás desde la raíz.
- [ ] T041 Recorrido en el emulador (quickstart §2, pasos 1–7), con conversaciones y documentos de prueba. Capturas frente al wireframe en español e inglés, en claro y en oscuro, al 200 % y en horizontal.
- [ ] T042 Borrador de `specs/013-chat-history/report.md`:
  - resumen;
  - resultados de build, lint y pruebas;
  - cifras de T038;
  - diferencias con el wireframe (`history-ui.md` §7);
  - correcciones del inglés.

  Avance de tareas en este fichero.

---

## Phase 11: Última fase — teléfono de referencia y cierre

- [ ] T043 Batería instrumentada completa en el A063, con los APK finales y la pantalla despierta.
- [ ] T044 `HistoryBenchmarkTest` de forma individual en el A063. Solo se lee su línea `history_bench`. SC-001: la primera fila dibujada por debajo de 1 s.
- [ ] T045 Recorrido **acordado con el usuario antes de empezar** (quickstart §3):
  - conversaciones nuevas sobre documentos de prueba, en una carpeta propia;
  - abrir, continuar, borrar y «＋»;
  - sin capturar ni volcar Historial mientras liste la conversación del usuario;
  - borrar la conversación de prueba de F12 desde Historial, solo con su permiso;
  - red (0 bytes para el uid de la app) y registros solo con ids y cifras;
  - limpieza y teléfono restaurado.
- [ ] T046 Cerrar `report.md` y poner F13 en `docs/roadmap/FEATURES.md` como «implementada; pendiente de la revisión de cierre». **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **Foundational (fase 2):** T007 antes que T008 y T009, por los tipos. T010 y T011 en paralelo con todo. **Bloquea US1–US7.**
- **US1 (fase 3):** depende de la fase 2. Es el MVP.
- **US2 (fase 4):** depende de US1, por la pantalla y la navegación.
- **US3 (fase 5):** depende de US1. Puede ir en paralelo con US2, salvo en `ChatViewModel.kt`: T029 y T030 van después de T023.
- **US4 (fase 6):** depende de T024, por `CHAT_REQUEST`.
- **US5 (fase 7):** depende de US1 y US3.
- **US6 (fase 8):** depende de US1–US5.
- **US7 (fase 9):** depende de la fase 2 y de T017, por el ViewModel. Puede ir en paralelo con US2–US6.
- **Polish (fase 10):** depende de todo lo anterior.
- **Última fase (11):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Datos y sesión antes que los casos de uso; los casos de uso antes que el ViewModel; el ViewModel antes que la pantalla.
- **Paradas de consulta:**
  - la parada del analyze (hecha: correcciones de H5 y H7 aprobadas, D-065);
  - T036, si ATF contradice el wireframe;
  - T045: el recorrido se acuerda antes con el usuario;
  - T046: la revisión de cierre.

### Parallel Opportunities

- **Fase 1:** T001 y T002.
- **Fase 2:** T003–T006 (pruebas), y T010 y T011 (componentes).
- **US1:** T012–T015.
- **US2:** T020–T022.
- **US3:** T025–T027.
- **US7:** T038, mientras avanzan US2–US6.

## Parallel Example: User Story 1

```text
T012 HistoryGroupingTest   T013 HistoryTextsTest   T014 HistoryViewModelTest   T015 HistoryScreenTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1 y 2: textos, datos, sesión, casos de uso y componentes.
2. US1: Historial lista las conversaciones. **Parar y validar** en la JVM y en el emulador.

### Incremental Delivery

1. **US2:** abrir y continuar, con las fichas inertes.
2. **US3:** borrar, con lo que las historias P1 quedan completas.
3. **US4 y US5:** «Nuevo chat» y la lista al día.
4. **US6:** accesibilidad y textos.
5. **US7:** la medida.
6. **Polish sin teléfono.** Se avisa al usuario.
7. **Última fase en el teléfono:** recorrido acordado y cierre.
