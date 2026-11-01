---

description: "Lista de tareas de F11 — Chat con fuentes"
---

# Tasks: Chat con fuentes (F11)

**Input**: Documentos de diseño en `/specs/011-chat-sources/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-014). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario. El orden no es el de prioridad, por dos motivos del plan:
- **US9** (revisión de la plantilla, P3) va **al principio**. Es trabajo de escritorio y fija la plantilla que usará el golden (riesgo 2).
- **US8** (primer token en el teléfono, P3) se divide en dos:
  - la prueba se escribe y se comprueba en el emulador;
  - se ejecuta en el teléfono de referencia en la **última fase**.

Historias de la spec:
- US1 — Preguntar y recibir una respuesta con sus fuentes (P1);
- US2 — Saber cuándo los documentos no tienen la respuesta (P1);
- US3 — Detener, copiar y reintentar (P1);
- US4 — Preguntas de seguimiento (P2);
- US5 — Conversación guardada en el teléfono (P2);
- US6 — Chat bloqueado o limitado, con la salida a la vista (P2);
- US7 — Un chat fiel al PNG y accesible (P2);
- US8 — Primer token dentro del presupuesto en el teléfono (P3);
- US9 — Revisión de la plantilla del prompt (P3).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emulador `Pixel_10`: `emulator-5554`;
  - teléfono de referencia: `adb -s P31281001066`, **solo en la última fase**.

  `adb` está en `$HOME/Library/Android/sdk/platform-tools/adb`. **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument`.
- **En el teléfono:**
  - nunca se borran ni se vuelven a descargar los modelos;
  - los registros se filtran a la app;
  - las capturas recortan la barra de estado;
  - ningún documento del usuario aparece en capturas ni en registros;
  - los ficheros de prueba van a una carpeta propia que se borra al terminar;
  - nada de listados de otras apps.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US9).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/sharedTest/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup (parámetro compartido)

**Purpose**: el parámetro que comparten la app y el escritorio.

- [X] T001 Añadir `llm_max_num_tokens` (data-model §1, contrato `eval-answer.md` §3):
  - `"llm_max_num_tokens": 4096` en `offline/config/rag_parameters.json`, y `"D-054"` en su lista `decisions`;
  - `offline/eval/generate.py` lo lee del JSON en lugar de `MAX_NUM_TOKENS`;
  - `const val LLM_MAX_NUM_TOKENS = 4096` en `…/microrag/domain/rag/RagParameters.kt`;
  - la clave en `…/test/…/domain/rag/RagParametersTest.kt` (`kotlinValues`).

  Las pruebas de Python y `RagParametersTest` siguen en verde.

**Checkpoint**: el parámetro coincide en los dos lados.

---

## Phase 2: User Story 9 - Revisión de la plantilla del prompt (Priority: P3) — escritorio, adelantada

**Goal**: decidir con cifras si una variante de la plantilla mejora las respuestas (D-035, D-053), antes de generar el golden.

**Independent Test**: `run_eval.py template-review` da la tabla de las variantes, y la decisión queda registrada con sus cifras.

- [X] T002 [P] [US9] Pruebas de Python (contrato `eval-answer.md` G12):
  - `offline/tests/test_prompt.py`: `v3` y `v4` llevan sus reglas tras «No uses conocimiento propio…», con el texto exacto de research R4, y el resto es igual a «informe»;
  - `offline/tests/test_runs.py`: la huella incluye `prompt_template` cuando no es «informe»;
  - `offline/tests/test_report.py`: la sección «Revisión de la plantilla (D-035, F11)» con su tabla, a partir de un `template_review.json` de ejemplo;
  - la criba, con el ejemplo de «informe» (68,9 % de correctas, 96,4 % de negativas y 76,4 % de citas de la regla): pasa con ≥ 69,9 %, ≥ 85 % y ≥ 74,4 %, y no pasa con una sola cifra por debajo.
- [X] T003 [US9] Añadir `v3` y `v4` a `offline/eval/prompt.py` (`TEMPLATES` y sus reglas, research R4). T002 (prompt) pasa.
- [X] T004 [US9] Implementar `template-review` (contrato `eval-answer.md` §2):
  - en `offline/eval/pipeline.py`, una función que, por variante:
    1. genera con la configuración elegida y `prompt_template=<variante>`;
    2. comprueba que los bloques coinciden 426/426 con la ejecución de referencia (G8);
    3. calcula las métricas automáticas y las respuestas cambiadas;
    4. aplica la criba frente a «informe» (R4);
    5. escribe las filas de revisión de las variantes que la pasan;
  - el comando en `offline/run_eval.py` (`variants=`, por defecto `v3,v4`);
  - la sección en `offline/eval/report.py`;
  - una fila en la tabla de comandos de `offline/README.md`.

  T002 pasa.
- [X] T005 [US9] Ejecutar `run_eval.py template-review` (≈ 11 min).
  - Si una variante pasa la criba: revisión manual de sus respuestas cambiadas (método D-033, G10) en `offline/reports/template-review.csv`, y las métricas manuales en `template_review.json`.
  - Regenerar `offline/reports/eval-report.md` con `run_eval.py report`.
- [X] T006 [US9] Registrar la decisión, con una entrada nueva en `docs/roadmap/DECISIONS.md` y la tabla (G11).
  - **Si se adopta una variante:**
    - `prompt_template` cambia en el JSON y en `RagParameters` (con `RagParametersTest` en verde);
    - se avisa al usuario con las cifras y se sigue.
  - **Si no:** se anota en la ficha de F14 de `FEATURES.md` que la plantilla sigue abierta como palanca (D-053).

**Checkpoint**: plantilla activa decidida y registrada.

---

## Phase 3: Golden de respuestas (escritorio)

**Purpose**: el golden que usan las pruebas de paridad de la app (research R2, R3).

- [X] T007 Crear `offline/tests/test_answer_golden.py` (G5) y `offline/gen_answer_golden.py` (G1–G4, data-model §7). Después:
  - generar `offline/golden/answer_golden.jsonl` con la plantilla activa (T006);
  - comprobar que `--check` no cambia nada, dos veces;
  - añadir la sección del golden en `offline/README.md`.
- [X] T008 [P] Crear `…/sharedTest/…/rag/answer/AnswerGoldenFiles.kt`: lector de `answer_golden.jsonl` para la JVM (`../offline/golden`) y para las instrumentadas (assets), como `RetrievalGoldenFiles` (G6).

**Checkpoint**: el golden existe, es determinista y se lee desde las dos clases de pruebas.

---

## Phase 4: Foundational (bloquea todas las historias de la app)

**Purpose**: los contratos de dominio, la base de datos del chat, la disponibilidad y los cambios en piezas existentes (conexión, índice y avisos de memoria).

- [X] T009 Crear el dominio del chat (data-model §2, contrato `chat-api.md`):
  - `…/microrag/domain/chat/ChatModels.kt`: `Conversation`, `TurnState`, `TurnFailure`, `ChatTurn`, `AnswerSource`, `PromptBlockRef`, `SourceAvailability`, `ActiveAnswer`, `ChatAvailability`, `ChatStorageState` y `ChatLimits`;
  - `ChatRepository.kt` (con `TurnUpdate` y `storageState`), `AnswerEngine.kt` (con `AnswerEvent`) y `ChatSession.kt`;
  - `ChatLibraryState` en `…/microrag/domain/rag/RagModels.kt` y `chatLibraryState()` en `RagIndex.kt`.
- [X] T010 Crear `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt` sobre SQLite real, con un fichero temporal en `cacheDir`:
  - esquema v1 y `schema_version`;
  - crear una conversación, insertar turnos con su orden, actualizarlos, reemplazar sus fuentes y prepararlos para reintentar;
  - `conversation(id)` emite tras cada escritura;
  - `lastConversationId` es la de `updated_at` mayor;
  - borrar en cascada y `deleteAll`;
  - `markInterrupted` solo cambia `retrieving` y `generating`;
  - un fichero con una versión superior, o dañado, da `storageState = Unavailable`, y no se escribe ni se borra;
  - las fuentes sobreviven aunque no exista el documento.
- [X] T011 Implementar la base de datos del chat (research R9, data-model §3):
  - `RagConnection.open(dbFile, extensionPath: String?)` en `…/microrag/data/rag/RagConnection.kt`. Sin extensión, no carga ni comprueba `vector.so`; `DefaultRagIndex` sigue igual;
  - `chat/` en `…/microrag/data/models/AppDirs.kt` (`ensureAll`);
  - `app/src/main/assets/chat_schema.sql`;
  - `…/microrag/data/chat/ChatSchema.kt` (carga y migraciones, como `RagSchema`);
  - `…/microrag/data/chat/SqliteChatRepository.kt`, en `AppDispatchers.database`, con `storageState`.

  T010 pasa en el emulador, y siguen en verde las instrumentadas de F04–F10.
- [X] T012 Crear `…/androidTest/…/data/rag/RagIndexChatStateTest.kt` (research R11). Con documentos en cada estado (pendiente, analizado, embebiendo, en pausa, indexado e incluido, indexado y excluido, con error, fuera del índice y una sombra), `chatLibraryState()` da:
  - `documents` sin la sombra;
  - `searchable` = indexados e incluidos;
  - `pendingIncluded` = incluidos en pendiente, analizado, embebiendo o en pausa.
- [X] T013 Implementar `chatLibraryState()` en `…/microrag/data/rag/DefaultRagIndex.kt` (una consulta agregada) y en `…/test/…/testing/FakeRagIndex.kt`. T012 pasa en el emulador.
- [X] T014 [P] Ampliar `…/test/…/rag/runtime/EngineMemoryCallbacksTest.kt`. Con `TRIM_MEMORY_UI_HIDDEN` o superior, y con `onLowMemory`:
  - los `MemoryTrimListener` se avisan antes de `releaseUnheld()`;
  - un listener que falla no impide liberar;
  - con niveles menores no se avisa.
- [X] T015 Implementar `…/microrag/rag/runtime/MemoryTrimListener.kt` y los avisos en `EngineMemoryCallbacks.kt`; el registro en `…/microrag/MicroRagApplication.kt` toma los listeners de Koin. T014 pasa.
- [X] T016 Crear `…/test/…/domain/chat/ObserveChatAvailabilityUseCaseTest.kt` (research R11):
  - la precedencia completa, con `StorageUnavailable` primero;
  - `IndexUnavailable` por `open()` y por excepción, y el reintento vuelve a comprobar;
  - `Ready` con `cpuMode` y `partialCoverage`;
  - error y fuera del índice no son cobertura parcial;
  - nada antes de la primera comprobación.
- [X] T017 Implementar `ObserveChatAvailabilityUseCase` en `…/microrag/domain/chat/ChatUseCases.kt`. T016 pasa. El registro en `…/microrag/domain/di/DomainModule.kt` va con T031, que trae `ChatRepository` y `AnswerEngine` a Koin: sin ellos, `AppModulesTest` fallaría (como `RetrieveContextUseCase` en F10).
- [X] T018 Crear `…/test/…/testing/ChatFakes.kt`:
  - `InMemoryChatRepository`, con la semántica del contrato y `storageState` programable;
  - `FakeAnswerEngine`: tokens programables, retardo virtual, fallo y registro de cancelación;
  - `FakeContextRetriever`: resultados programables, con candidatos para la clasificación de bloques, y registro de peticiones;
  - un flujo de disponibilidad programable para la sesión.

**Checkpoint**: base de datos, consulta del índice, disponibilidad y avisos de memoria listos. Las pruebas de F04–F10 siguen en verde.

---

## Phase 5: User Story 1 - Preguntar y recibir una respuesta con sus fuentes (Priority: P1) 🎯 MVP

**Goal**: una pregunta se responde en streaming con fichas y fuentes reales, que abren el fragmento resaltado y visible.

**Independent Test**: con los fakes, enviar una pregunta: pasa por buscando, generando y completada; las fuentes son exactamente las citadas; tocar una abre el detalle con el fragmento visible, y Atrás conserva la posición.

### Tests for User Story 1

- [X] T019 [P] [US1] Crear `…/test/…/rag/answer/PromptBuilderTest.kt` con los casos de `offline/tests/test_prompt.py` (R2). Además:
  - recorte por puntos de código, con un emoji en el límite;
  - sin bloques, sin línea en blanco antes de `PREGUNTA:`;
  - ruta o página nulas omitidas;
  - ruta vacía y página 0 escritas;
  - `strip()` de Python;
  - el idioma por defecto (`SPANISH`) da el texto del escritorio (FR-014);
  - `estimatedTokens`.
- [X] T020 [P] [US1] Crear `…/test/…/rag/answer/AnswerReadingTest.kt` con los casos de `offline/tests/test_judge.py` (R3). Además:
  - `[1, 2][3]`, dígitos Unicode, separadores de miles y acentos;
  - negativa con comillas o puntuación distinta, frente a negativa con texto añadido;
  - `citedOrder` con repetidos y fuera de rango.
- [X] T021 [P] [US1] Crear `…/test/…/rag/answer/AnswerGoldenTest.kt` (SC-002, SC-003). En las 426 filas:
  - prompt con la misma longitud en puntos de código y la misma huella, y el mismo texto en las 10 filas que lo llevan;
  - `citations`, `cited_order`, `refusal` y `norm` iguales.

  Un fallo informa del id y de la primera diferencia.
- [X] T022 [P] [US1] Crear `…/test/…/data/chat/EngineAnswerEngineTest.kt` (R5). Con `InMemoryModelFileRepository`, una fábrica falsa y el coordinador real:
  - **`ready()`:** solo con Gemma `Verified` y preparada; refresca una vez si la ruta es nula;
  - **clave y config:** `EngineKey(GEMMA4_E2B, backend preparado)`, `GenerationConfig(4096, 512, greedy)` e inicialización en la fábrica;
  - **`warmUp`:** carga sin generar, y el motor queda cargado;
  - **`generate`:** reemite los tokens y el final; si se cancela el colector, el motor se detiene y queda cargado; un fallo del motor se propaga.
- [X] T023 [P] [US1] Crear `…/test/…/data/chat/DefaultChatSessionTest.kt`, camino principal (contrato A1, A2, A7, la rama con citas de A8 y A14), con `ChatFakes`, el coordinador real y tiempo virtual:
  - antes de la primera pregunta no existe la conversación (FR-029); la primera la crea y devuelve su id;
  - el turno guarda la marca de cobertura parcial del momento;
  - el turno pasa por `RETRIEVING` → `GENERATING` (al entregar el prompt) → `COMPLETED`;
  - `active` lleva el texto según llega y los bloques del prompt;
  - se guarda como mucho cada segundo y al terminar;
  - las fuentes salen de `citedOrder`, con posición desde 1 y los datos del bloque (primer fragmento y rango);
  - una segunda `ask` con otra en curso devuelve `null`;
  - sin `Ready` devuelve `null` y no carga nada;
  - **registro (SC-010):** una línea por respuesta, sin ninguno de los textos marcados de la pregunta, los bloques ni la respuesta.
- [X] T024 [P] [US1] Crear `…/test/…/feature/chat/AnswerFormatterTest.kt` (R3):
  - párrafos, viñetas `- `, `* ` y `• `, numeradas, negritas, `#` y comillas invertidas;
  - renumeración por primera cita, `[1, 3]` como dos fichas, repetidas con su número y fuera de rango quitadas;
  - la misma numeración con los bloques del prompt (streaming) que con las fuentes guardadas;
  - streaming estable (prefijos del mismo texto);
  - `copyText` según C10, con y sin página, y en una negativa el texto mostrado.
- [X] T025 [P] [US1] Crear `…/test/…/feature/chat/ChatViewModelTest.kt`, camino principal:
  - enviar con la pregunta escrita: el compositor se vacía y pasa a detener;
  - los turnos combinan lo guardado y lo activo;
  - el id de la conversación nueva se guarda en `SavedStateHandle`;
  - abrir una fuente emite el evento con `documentId` y `chunkId`;
  - los avisos salen una vez por cambio de estado.
- [X] T026 [P] [US1] Crear `…/test/…/feature/chat/ChatScreenTest.kt` (Robolectric, `es-w390dp-h844dp`), camino principal (contrato `chat-ui.md` §1 y §3):
  - la estructura del PNG, con «⋯»;
  - una respuesta completa con viñetas, fichas, «Copiar respuesta», chip «2 fuentes» y dos filas «Página P · Fragmento M»;
  - el chip lleva a la primera fila;
  - una ficha y su fila abren la misma fuente;
  - durante el streaming, «Buscando en documentos...» y después «Generando respuesta...», con fichas en el texto y sin chip ni lista.
- [X] T027 [P] [US1] Ampliar `…/test/…/feature/document/DocumentScreenTest.kt`: con una cita, el fragmento resaltado se ve sin desplazarse en vertical y en horizontal, y no vuelve a desplazarse tras una recreación (research R13).

### Implementation for User Story 1

- [X] T028 [US1] Implementar `…/microrag/rag/answer/PromptBuilder.kt` con todas las plantillas del harness y `PromptLanguage` (R2). T019 pasa.
- [X] T029 [US1] Implementar `…/microrag/rag/answer/AnswerReading.kt` (R3). T020 y T021 pasan.
- [X] T030 [US1] Implementar `…/microrag/data/chat/EngineAnswerEngine.kt` (R5, contrato). T022 pasa.
- [X] T031 [US1] Implementar `…/microrag/data/chat/DefaultChatSession.kt`, camino principal (R6, A1, A2, A7, la rama con citas de A8 y A14), y los casos de uso `ObserveConversationUseCase` y `AskQuestionUseCase` en `ChatUseCases.kt`. Registrar en Koin:
  - `DomainModule`: los casos de uso;
  - `DataModule`: `ChatRepository`, `AnswerEngine` y `ChatSession`, también como `MemoryTrimListener`.

  T023 pasa y `AppModulesTest` sigue en verde.
  **Nota de la implementación:** la sesión se escribió completa en T031, por ser una sola clase coherente. `DefaultChatSessionTest` ya cubre las reglas A1–A15: las partes de sesión de T039, T042, T045, T048 y T053 están hechas y probadas. En esas tareas solo queda lo que toca al ViewModel y a la pantalla.
- [X] T032 [US1] Cambios en el design system (contrato `chat-ui.md` §4):
  - `…/microrag/core/designsystem/model/AnswerContent.kt`;
  - en `component/Chat.kt`, `LisdataAnswerCard` con `content`, `onCitation`, `notes`, `failureText` y `copiedLabel`: viñetas `brandBlue`, negritas y fichas como `LinkAnnotation.Clickable` con fondo redondeado desde la disposición del texto (R3). El chip de fuentes es tocable y sin punto;
  - `LisdataCountChip` con `onClick` y 48 dp (`component/Chips.kt`);
  - `LisdataSourceCitation` con `lastFragment`, `onClickLabel` y la frase para TalkBack (`component/Rows.kt`);
  - `LisdataOverflowMenu` en `component/Menus.kt`, con `action_more_options`; el menú de `…/microrag/feature/document/DocumentScreen.kt` (F08) pasa a usarlo, y `DocumentScreenTest.theMoreMenu_offersRemoveFromIndex` sigue en verde;
  - `ChatPreviews.kt` actualizado; los usos existentes compilan.
- [X] T033 [US1] Implementar `…/microrag/feature/chat/AnswerFormatter.kt`. T024 pasa.
- [X] T034 [P] [US1] Crear `app/src/main/res/values/strings_chat.xml` y `values-en/strings_chat.xml` con todas las claves de `chat-ui.md` §5. Añadir `citation_fragments` y `action_more_options` a `strings.xml` en los dos idiomas, y retirar `document_more` si queda sin uso.
- [X] T035 [US1] Ruta y navegación (research R13):
  - `Chat(conversationId, newConversation)` en `…/microrag/core/navigation/Destinations.kt`, con su KDoc;
  - `openNewChat()` → `Chat(newConversation = true)` en `NavigationActions.kt`;
  - `ChatRoute` en `MicroRagNavHost.kt`, en lugar de `PendingDestination`: abrir una fuente navega a `Document(documentId, chunkId)` solo con la entrada `RESUMED`, y `LIBRARY_MESSAGE` se lee y se borra;
  - `…/debug/DebugRouting.kt`;
  - en `…/test/…/core/navigation/NavigationTest.kt`, el módulo Koin falso del chat; `newChat_…` comprueba `newConversation = true`, y una prueba nueva hace fuente → detalle → Atrás.
- [X] T036 [US1] Implementar la pantalla, camino principal:
  - `…/microrag/feature/chat/ChatUiState.kt`, `ChatViewModel.kt`, `ChatScreen.kt` (con `ChatActions`) y `ChatRoute.kt`;
  - `ChatPreviewData.kt` con la respuesta completa del PNG;
  - el `ChatViewModel` en `…/microrag/feature/di/FeatureModule.kt`;
  - `LazyColumn` con su estado en la ruta, y el seguimiento del final (U7);
  - región en vivo de los avisos (U2).

  T025, T026 y T035 pasan.
- [X] T037 [US1] Desplazar el detalle al fragmento citado en `…/microrag/feature/document/DocumentScreen.kt` (`BringIntoViewRequester` en el texto resaltado, `LaunchedEffect` y marca `rememberSaveable`). T027 pasa, y el resto de `DocumentScreenTest` sigue en verde.
- [X] T038 [US1] Añadir a `…/test/…/architecture/LayeringTest.kt` la regla U1 para `feature/chat`, con la comprobación de que existen ficheros.

**Notas de la implementación de US1:**
- **Nombres de ficheros.** Los datos de muestra están en `feature/chat/ChatPreviews.kt` (objeto `ChatPreviewData`), no en `ChatPreviewData.kt`: `DesignSystemConformanceTest` solo admite textos fijos en ficheros `*Previews.kt`, como `DocumentPreviews.kt`. `ChatRoute` está en `ChatScreen.kt`, como las rutas de las demás pantallas.
- **Pie de la tarjeta** (`LisdataAnswerCard`), según FR-024, T043 y el contrato `chat-ui.md` §3:
  - «Copiar respuesta» solo está activo en una respuesta terminada o cancelada con texto; en las demás se ve desactivado, como en F01;
  - «Reintentar» y el chip de fuentes pueden salir juntos, como pide `INTERRUPTED` (C13);
  - el pie pasa a `FlowRow`, para que sus tres elementos bajen de línea en pantallas estrechas o con la fuente al 200 %.
- **Enviar** lee la pregunta tal como se escribió, no la del último estado combinado. Así, un toque justo después de escribir la envía entera. Lo encontró `ChatViewModelTest`.
- **Pruebas:** `FakeChatSession` y `ChatTestGraph` en `testing/ChatFakes.kt`, y `chatViewModel()` en `feature/chat/ChatTestViewModels.kt`, que comparten `ChatViewModelTest` y `NavigationTest`.

**Checkpoint**: MVP. Una pregunta se responde con fuentes reales en la JVM, y la navegación a la fuente funciona.

---

## Phase 6: User Story 2 - Saber cuándo los documentos no tienen la respuesta (Priority: P1)

**Goal**: negativa explícita, sin fuentes y sin inventar (C1, C2).

**Independent Test**: una búsqueda sin bloques da la negativa sin llamar al modelo; la frase del modelo da el texto del MD; una respuesta sin citas válidas lleva su nota.

- [X] T039 [P] [US2] Ampliar `DefaultChatSessionTest` (A5, A8, SC-012):
  - `NoResults` da `COMPLETED` con `refusal = true` y `PromptBuilder.REFUSAL` como respuesta en bruto, sin llamar al motor, en menos de 1 s de tiempo virtual;
  - la frase de negativa, también con comillas, da `refusal = true` y sin fuentes;
  - la negativa con texto añadido no lo es;
  - una respuesta sin citas válidas termina `COMPLETED` sin fuentes.
- [X] T040 [P] [US2] Ampliar `ChatViewModelTest` y `ChatScreenTest`:
  - la negativa muestra «No encuentro información suficiente en tus documentos», sin chip ni fuentes (C1), y copiarla da ese texto;
  - una respuesta sin citas lleva la nota de C2;
  - con la pregunta en blanco, enviar no está activo.
- [X] T041 [US2] Implementar la negativa y la nota en la sesión, en el ViewModel (`TurnUi.content` y `notes`) y en la pantalla. T039 y T040 pasan.

**Checkpoint**: la negativa funciona como el escritorio y como el MD.

---

## Phase 7: User Story 3 - Detener, copiar y reintentar (Priority: P1)

**Goal**: detener conserva el texto parcial; copiar da el contenido de C10; los fallos ofrecen «Reintentar» (C7, C8).

**Independent Test**: detener durante buscando y durante generando; copiar una respuesta; un fallo del motor y otro del índice con su reintento.

- [X] T042 [P] [US3] Ampliar `DefaultChatSessionTest` (A5, A9, A10, SC-005):
  - **`cancel()`:**
    - deja `CANCELLED` con el texto recibido y sus fuentes válidas, en menos de 1 s, durante buscando y durante generando;
    - el motor se detiene y queda cargado;
  - **fallos:**
    - del motor, `FAILED(GENERATION)` con el texto parcial guardado;
    - `IndexUnavailable`, `FAILED(INDEX)`;
    - respuesta vacía, `FAILED`;
  - **`retry`:**
    - solo desde `FAILED` e `INTERRUPTED`; repite la pregunta en el mismo turno con el turno anterior a él;
    - da `false` con otra respuesta en curso o sin `Ready`.
- [X] T043 [P] [US3] Ampliar `ChatViewModelTest` y `ChatScreenTest`:
  - detener vuelve a enviar;
  - «Generación cancelada» con el texto parcial;
  - C7 y C8 con «Reintentar», desactivado si la disponibilidad no es `Ready` o hay otra respuesta en curso;
  - «Copiar respuesta» desactivado durante el streaming;
  - copiar da el texto de C10;
  - en la API 31–32 se ve «Respuesta copiada» durante 2 s y se anuncia (U10);
  - en la API 33 o superior no hay confirmación propia.
- [X] T044 [US3] Implementar detener, reintentar y fallos en la sesión, con `CancelAnswerUseCase` y `RetryAnswerUseCase`, y copiar en la pantalla (`LocalClipboard` con `ClipEntry`, R14). T042 y T043 pasan.

**Nota de la implementación de US3:** «Reintentar» se ve en todo turno guardado `FAILED` o `INTERRUPTED` (`TurnUi.retryable`), y se desactiva mientras no puede empezar (`canRetry`), como pide T043. Antes se ocultaba. La tarjeta recibe `retryEnabled`. En la API 31–32, copiar también deja el aviso `COPIED` («Respuesta copiada») en la región en vivo durante 2 s (U10).

**Checkpoint**: las tres historias P1 están completas sin el teléfono.

---

## Phase 8: User Story 4 - Preguntas de seguimiento (Priority: P2)

**Goal**: el turno anterior según C12 (como el escritorio), y el límite del prompt (R10).

**Independent Test**: una segunda pregunta usa el turno anterior; una pregunta larga con un turno anterior largo deja fuera bloques sin cortar ninguno.

- [X] T045 [P] [US4] Ampliar `DefaultChatSessionTest` (A4, A6):
  - **completado, también si fue la negativa:** la búsqueda recibe la pregunta anterior; el prompt, la pregunta y la respuesta en bruto;
  - **cancelado, fallido o interrumpido:** nada;
  - **conversación nueva:** sin turno anterior;
  - **límite:**
    - con una pregunta de 1.000 caracteres y un turno anterior largo, se quita primero el bloque peor clasificado, después el turno anterior, y nunca el último bloque (`dropped_blocks` en el registro);
    - los bloques que quedan conservan su orden;
  - **paridad:** las 50 preguntas de seguimiento del golden dan el mismo prompt pasando el turno anterior por la sesión.
- [X] T046 [US4] Implementar el turno anterior y el límite del prompt en `DefaultChatSession` (R10). T045 pasa.
- [X] T047 [P] [US4] El compositor limita la pregunta a `ChatLimits.MAX_QUESTION_CHARS` en `String.length` y corta lo que se pega sin partir un emoji. Prueba en `ChatViewModelTest`.

**Checkpoint**: el seguimiento funciona como el escritorio.

---

## Phase 9: User Story 5 - Conversación guardada en el teléfono (Priority: P2)

**Goal**: conversaciones guardadas, la pestaña abre la última, «⋯» con nuevo chat y borrar, y fuentes no disponibles (C11, C13, C15–C17).

**Independent Test**: tras cerrar la app, la pestaña Chat abre la última conversación; «Nuevo chat» abre una vacía; borrar pide confirmación; una fuente borrada no abre nada; un turno a medias pasa a interrumpido.

- [X] T048 [P] [US5] Ampliar `DefaultChatSessionTest` (A12, A13, A15):
  - al crearse la sesión, `markInterrupted` se llama una vez antes de leer;
  - `startNewConversation()` y `delete()` cancelan la respuesta en curso;
  - borrar elimina en cascada;
  - una escritura que falla deja la respuesta en `active` con `saveFailed`;
  - con `storageState = Unavailable`, la disponibilidad es `StorageUnavailable` y `ask` no hace nada.
- [X] T049 [P] [US5] Crear `…/test/…/domain/chat/ResolveSourcesUseCaseTest.kt` (R12):
  - documento borrado, `DOCUMENT_GONE`;
  - fragmento ausente con el documento presente, `FRAGMENT_GONE`;
  - presente, `AVAILABLE`;
  - excluido de la búsqueda, `AVAILABLE`;
  - índice no disponible: todas disponibles;
  - se recalcula con los cambios del índice.
- [X] T050 [P] [US5] Ampliar `ChatViewModelTest`, `ChatScreenTest` y `NavigationTest`:
  - **resolución de la conversación** (R13), en este orden: el id guardado, el id de la ruta, `newConversation`, la última, o vacía; una entrada de «Nuevo chat» en la que ya se preguntó vuelve a su conversación al recrearse;
  - **«⋯»:** «Nuevo chat» sin navegar y «Borrar conversación» con el diálogo de C15 (foco en «Cancelar», «Conversación borrada» anunciado);
  - **«Borrar»** solo con conversación guardada;
  - **fuentes:** una fila «Fuente ya no disponible» es inerte; un bloque de varios fragmentos dice «Fragmentos M–N» (C11);
  - **C13:** un turno interrumpido con «La respuesta se interrumpió» y «Reintentar»;
  - **C16 y C17:** la nota de respuesta sin guardar y el estado de conversaciones ilegibles;
  - **pestaña:** abre la última conversación tras recrear la actividad;
  - **FR-011:** una respuesta en curso sigue al cambiar de pestaña y volver, porque el ViewModel la recupera de la sesión.
- [X] T051 [US5] Implementar la persistencia en la pantalla:
  - resolución de la conversación, acciones de «⋯» y diálogo de borrado;
  - `ResolveSourcesUseCase`, `LastConversationUseCase`, `StartNewConversationUseCase` y `DeleteConversationUseCase`;
  - estados C11, C13, C15, C16 y C17.

  T048, T049 y T050 pasan.

**Nota de la implementación de US5:** una conversación que no se pudo guardar (C16) lleva el id `UNSAVED` solo en memoria. La pantalla la trata como no guardada, así que «⋯» no ofrece borrarla, y la sesión vuelve a intentar crearla con la pregunta siguiente. `ResolveSourcesUseCaseTest` (T049) se escribió con su caso de uso, antes de la pantalla.

**Checkpoint**: las conversaciones se guardan y se recuperan; el historial de F13 tiene su base.

---

## Phase 10: User Story 6 - Chat bloqueado o limitado (Priority: P2)

**Goal**: la disponibilidad en la pantalla, la espera de la indexación (R7) y la app oculta (R8), con los estados C3–C6, C8, C9 y C14.

**Independent Test**: cada disponibilidad muestra su estado con y sin turnos; con una tanda en marcha, el chat no carga Gemma y la ingesta espera a la respuesta en curso; con la app oculta, la respuesta queda interrumpida.

- [X] T052 [P] [US6] Crear `…/test/…/data/chat/ChatDuringIndexingTest.kt` (R7, SC-011). Con el coordinador real y motores falsos de GPU:
  - con la tanda en marcha, `ask`, `retry` y `warmUp` no cargan nada;
  - una respuesta en curso cuando la ingesta llama a `releaseGenerators()` termina, y solo después se cierra Gemma y carga el motor de la ingesta;
  - nunca hay dos motores de GPU cargados;
  - al pausarse la tanda, el chat vuelve a estar disponible.
- [X] T053 [P] [US6] Ampliar `DefaultChatSessionTest` (A11): con `TRIM_MEMORY_UI_HIDDEN` (listener) y con `onLowMemory`, la respuesta queda `INTERRUPTED` con su texto y sus citas válidas, y después se libera Gemma.
- [X] T054 [P] [US6] Ampliar `ChatViewModelTest` y `ChatScreenTest` (contrato `chat-ui.md` §2):
  - cada disponibilidad, con y sin turnos: texto, acción, compositor y precarga solo en `Ready` (al entrar y en `ON_START`);
  - el «Reintentar» de C8 en pantalla vuelve a comprobar el índice;
  - C6 bajo la respuesta;
  - subtítulo en modo CPU (C9);
  - las acciones abren la Preparación, la Ingesta y el Estado de indexación;
  - al pasar a `Ready` sin salir, el compositor se activa.
- [X] T055 [US6] Implementar `WarmUpAnswerEngineUseCase`, el listener de memoria en la sesión (A11), la precarga y los estados de disponibilidad en la pantalla. T052–T054 pasan.

**Notas de la implementación de US6:**
- `ChatDuringIndexingTest` simula la tanda con las mismas llamadas al coordinador que `ChunkEmbeddingSession.use`: soltar los generadores, retener el codificador y soltarlo al final. Montar la sesión de indexación real pediría su almacén de ejecuciones y la comprobación de la GPU, que no aportan nada aquí.
- El caso de T053 está en ese mismo fichero, porque necesita el coordinador y Gemma reales: con la app oculta y con poca memoria, la respuesta queda interrumpida y después se cierra Gemma.
- Para seguir el contrato §2:
  - `LisdataErrorState` admite `onRetry = null` (C17, sin «Reintentar»);
  - `LisdataEmptyState` admite `body = null` (C4, solo título y «Ver indexación»).

**Checkpoint**: el chat nunca carga Gemma con una tanda en marcha, y explica siempre lo que falta.

---

## Phase 11: User Story 7 - Un chat fiel al PNG y accesible (Priority: P2)

**Goal**: fidelidad al PNG 04 y accesibilidad completa (U2–U12).

**Independent Test**: comprobaciones automáticas de accesibilidad en todos los estados, en claro y en oscuro, horizontal y fuente al 200 %; avisos una vez por estado; el compositor visible con el teclado.

- [X] T056 [P] [US7] Completar `…/microrag/feature/chat/ChatPreviewData.kt` con todos los estados de §2 y §3 del contrato, también con fuentes no disponibles y varios fragmentos, para las previsualizaciones y las pruebas de accesibilidad.
- [X] T057 [P] [US7] Ampliar `ChatScreenTest`:
  - **frases** (U3): «Tú: …», «MicroRAG: …», fichas como enlaces con su frase, y filas;
  - **avisos** una vez por cambio (U2), con los textos `chat_announce_*`, sin leer el texto según llega;
  - **fuente al 200 % y horizontal** (`es-w844dp-h390dp`): sin cortes ni líneas partidas, y todo alcanzable;
  - **desplazamiento** conservado tras recrear;
  - **teclado:** con `imePadding`, el compositor visible;
  - **inglés** (`en-w390dp-h844dp`): todos los textos de la pantalla en inglés.
- [X] T058 [P] [US7] Crear `…/androidTest/…/feature/chat/ChatScreenAccessibilityTest.kt`, parametrizada con `ChatPreviewData` × oscuro, como la de Inicio:
  - ATF;
  - objetivos de 48 dp de los nodos tocables (las fichas dentro del texto no son nodos tocables, R3);
  - horizontal real;
  - fuente al 200 % dentro del ancho.
- [X] T059 [P] [US7] Crear `…/androidTest/…/feature/chat/ChatScreenLayoutTest.kt` (solo A063): con la barra inferior real y el teclado abierto, el compositor queda visible y la barra se oculta (SC-009).
- [X] T060 [US7] Corregir lo que salga de T057–T059 sin cambiar la presentación aprobada. **Si ATF marca las fichas dentro del texto, se para y se consulta** (riesgo 4). Después, `CatalogAccessibilityTest` (debug) cubre `LisdataAnswerCard` con contenido nuevo.

**Notas de la implementación de US7:**
- **Emulador:** `ChatScreenAccessibilityTest` pasa 156 de 156 (26 estados × claro y oscuro × vertical, horizontal y 200 %). `CatalogAccessibilityTest` pasa 78 de 78, con la tarjeta interrumpida con «Reintentar» y chip, y con «Reintentar» desactivado.
- **ATF y las fichas:** ATF no marca las fichas dentro del texto, así que no hizo falta la consulta del riesgo 4.
- **`ChatScreenLayoutTest`** (T059) está escrita, y se salta fuera del A063. Se ejecuta en T068.
- **U3:** la cabecera de la tarjeta se lee «MicroRAG», no las mayúsculas que dibuja el antetítulo.
- **Desviación de U3:** las fichas se leen como enlace con su número, porque `LinkAnnotation` no admite una etiqueta propia. La frase completa está en su fila.
- **Al abrir una conversación**, la lista va a la última respuesta. U7 no lo decía; antes abría arriba. Al volver de una fuente o al recrear, la posición se conserva.

**Checkpoint**: la pantalla es fiel y accesible en el emulador.

---

## Phase 12: User Story 8 - Primer token, preparado en el emulador (Priority: P3)

**Goal**: la prueba de medida, lista para el teléfono.

- [X] T061 [US8] Extraer de `…/androidTest/…/data/retrieval/RetrievalParityTest.kt` la indexación del corpus en un índice temporal a `…/androidTest/…/testing/CorpusIndex.kt`. `RetrievalParityTest` lo usa y sigue igual.
- [X] T062 [US8] Crear `…/androidTest/…/data/chat/ChatBenchmarkTest.kt` (`@RequiresModels`, R15). Con la sesión real sobre un índice temporal con el corpus, y `chat.db` también temporal:
  - cada una de las 50 preguntas de `retrieval_parity.jsonl` en una conversación nueva;
  - para los 10 seguimientos, primero su pregunta anterior (sin medir) y después el seguimiento, que se mide;
  - Gemma precargada; la primera pregunta se mide aparte sin cargar;
  - se registra la línea `chat-benchmark` de data-model §9, con `MemoryProbe` para el pico de memoria;
  - en el A063 se comprueba p95 ≤ 5.000 ms;
  - en el emulador, 3 preguntas sin umbrales si Gemma está preparada; si no, se salta.

  Se borra todo lo temporal. Se comprueba en el emulador.

**Nota de la implementación de US8:**
- `RetrievalParityTest` sigue igual con `CorpusIndex`: en el emulador da 0,837 frente a 0,837 del escritorio.
- `ChatBenchmarkTest` lee los modelos en solo lectura (`InstalledModels`, con el backend de Gemma del registro de la Preparación). En el emulador se salta, porque no tiene Gemma: tiene 3,2 GB libres, y el modelo (2,6 GB) más su caché compilada no caben. El camino completo con Gemma se ejecuta por primera vez en el teléfono (T067).

**Checkpoint**: la medida está lista para la última fase.

---

## Phase 13: Polish sin teléfono

- [X] T063 Puertas de calidad:
  - Python completo y `gen_answer_golden.py --check`;
  - `assembleDebug`, `assembleRelease`, `lintDebug` y `lintRelease`;
  - todas las pruebas JVM, también `NetworkConfinementTest` (sin red, FR-042) y `AppLoggerTest` (nada en release, FR-043);
  - instrumentadas en el emulador (quickstart §3), repetidas con los APK finales.

  Cifras en el informe.
- [X] T064 Recorrido en el emulador (quickstart §4), con capturas sin la barra de estado y el registro filtrado a `MicroRAG`. Lo que salga se corrige con su prueba.
- [X] T065 [P] Documentación:
  - `docs/roadmap/INVENTARIO.md`: P04 implementada;
  - `docs/roadmap/FEATURES.md`: F11 «🟨 en curso (implementación)»;
  - `specs/011-chat-sources/report.md` en borrador, con todas las cifras sin teléfono, las diferencias con el PNG y un hueco para las del teléfono, marcado como pendiente.

**Checkpoint**: todo lo que no necesita el teléfono está hecho. **Se avisa al usuario de que F11 espera al teléfono.**

---

## Phase 14: Última fase — teléfono de referencia (US8, US7) y cierre

**Purpose**: las medidas en el A063 y el cierre. Solo cuando el usuario diga que el teléfono está disponible.

- [X] T066 En el teléfono: instalar los dos APK con `install -r` y ejecutar las instrumentadas sin modelos (quickstart §6). Deben quedar en verde.
  - 657/657 en 16,5 min. Una primera ejecución perdió la salida por la conexión adb; en ella falló una vez `CatalogAccessibilityTest` (48 dp). No se reproduce: sola, 78/78, y en la batería completa, en verde.
- [X] T067 [US8] `ChatBenchmarkTest` en el teléfono (SC-004): mediana y p95 del primer token, la primera en frío, tokens del prompt, velocidad, total, pico de memoria, negativas y respuestas sin cita. **Si el p95 pasa de 5 s, se para y se consulta** (FR-045, riesgo 3).
  - Primera ejecución: la app se cerraba al leer la primera respuesta. El patrón de citas usaba `(?U)`, que Android rechaza; se corrigió con `AnswerGoldenInstrumentedTest` (`91ad50b`). La prueba también recorría el registro sin copiarlo (`5a810e9`).
  - Primera medida completa: p50 4.168 ms y **p95 5.205 ms**. Se paró y se consultó; el usuario pidió medir otra vez con una línea por pregunta (`70c48a7`) y el teléfono cargado y frío.
  - Segunda medida: p95 5.826 ms en frío (30 preguntas) y 6.638 ms con las 50, ya con limitación térmica. Segunda consulta: el límite de 1.500 tokens se evaluó en el escritorio (`87898ba`: 80,5 % de correctas) y no se adoptó. El usuario eligió la excepción documentada (D-057); la prueba informa `sc004=` sin fallar (`987afe8`).
- [X] T068 [US7] `ChatScreenLayoutTest` en el teléfono.
  - En verde con el teclado real y la barra inferior.
- [X] T069 Recorrido del chat en el teléfono. **Antes se consulta al usuario cómo hacerlo** (R15), porque su biblioteca tiene un documento propio. Se siguen las reglas de privacidad del teléfono.
  - Hecho con documentos públicos en `Download/MicroRAG-F11` y giro, letra al 200 % y modo claro temporales, restaurados. Detalle en `report.md` §4.
  - Encontró que, con el chat abierto al terminar una tanda, Gemma no se precargaba. Corregido en `3782824` y comprobado en el teléfono.
- [X] T070 Cierre (Definición de terminado):
  - `report.md` completo: cifras del teléfono, dispositivo, versión de Android y versiones de LiteRT-LM, SQLite-Vector y la app (constitución VI); diferencias frente al PNG 04;
  - `FEATURES.md` con F11 pendiente de aprobación;
  - las decisiones del cierre en DECISIONS.

  **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **US9 (fase 2):** depende de T001. Va primero porque fija la plantilla del golden.
- **Golden (fase 3):** depende de la decisión de T006.
- **Foundational (fase 4):** depende de T001. **Bloquea US1–US8.** Puede ir en paralelo con las fases 2 y 3. Dentro de la fase, T010–T018 dependen de los tipos de T009.
- **US1 (fase 5):** depende de las fases 3 y 4. Es el MVP.
- **US2 y US3 (fases 6 y 7):** dependen de US1. Tocan la sesión y la pantalla, así que una detrás de otra.
- **US4 (fase 8):** depende de US1.
- **US5 (fase 9):** depende de US1.
- **US6 (fase 10):** depende de US1 y de la fase 4 (T015, avisos de memoria; T017, disponibilidad).
- **US7 (fase 11):** depende de US1–US6, porque cubre todos los estados.
- **US8 en el emulador (fase 12):** depende de US1, US4 y US6 (la disponibilidad real y la precarga).
- **Polish sin teléfono (fase 13):** depende de todo lo anterior.
- **Última fase (14):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Algoritmos puros antes que la sesión; la sesión antes que la pantalla.
- Las paradas de consulta son T060 (fichas y ATF), T067 (primer token) y T069 (recorrido en el teléfono). En T006 se avisa si se adopta una variante, sin parar.

### Parallel Opportunities

- **Fases 2–4:** la revisión de la plantilla (escritorio) corre en paralelo con la base de la app (T009–T018).
- **Foundational:** T014 en cualquier momento; T010, T012, T016 y T018 en paralelo tras T009.
- **US1:** las pruebas T019–T027; después T028–T030 y T034 en paralelo.
- **US5:** T048, T049 y T050.
- **US6:** T052–T054.
- **US7:** T056–T059.

## Parallel Example: User Story 1

```text
T019 PromptBuilderTest   T020 AnswerReadingTest   T021 AnswerGoldenTest   T022 EngineAnswerEngineTest
T023 DefaultChatSessionTest   T024 AnswerFormatterTest   T025 ChatViewModelTest   T026 ChatScreenTest   T027 DocumentScreenTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1 y fase 2, con la plantilla decidida (T006).
2. Fases 3 y 4: golden, base de datos, índice y disponibilidad.
3. US1: una pregunta responde con fuentes reales en la JVM y abre el fragmento. **Parar y validar** con `AnswerGoldenTest` y `DefaultChatSessionTest`.

### Incremental Delivery

1. **US2 y US3:** negativa, detener, copiar y reintentar. Con ellas, las historias P1 están completas.
2. **US4:** seguimiento.
3. **US5:** conversaciones guardadas.
4. **US6:** disponibilidad en pantalla y convivencia con la indexación.
5. **US7:** fidelidad y accesibilidad.
6. **US8 preparada en el emulador** y **polish sin teléfono**. Se avisa al usuario.
7. **Última fase en el teléfono:** primer token, diseño con teclado, recorrido y cierre.
