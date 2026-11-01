---

description: "Lista de tareas de F12 — Ajustes e Idiomas"
---

# Tasks: Ajustes e Idiomas (F12)

**Input**: Documentos de diseño en `/specs/012-settings-languages/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: **obligatorios** (constitución VI; SC-001 a SC-013). En cada fase, las pruebas van **antes** de la implementación y deben fallar primero.

**Organización**: por historia de usuario. El orden no sigue la prioridad, por el plan:
- **US9** (evaluación de «Automático») y **US8** (medida entre idiomas) van **al principio**. Son trabajo de escritorio y deciden qué se construye en la app: si se ofrece «Automático» y el texto de la tarjeta.
- Las pruebas en el teléfono van en la **última fase** (petición del usuario).

Historias de la spec:
- US1 — Ver el estado real de los modelos (P1);
- US2 — Elegir el idioma de la app (P1);
- US3 — Elegir el idioma de las respuestas (P1);
- US4 — Borrar el historial de chat (P1);
- US5 — Moverse desde Ajustes (P2);
- US6 — Preferencias que no se pierden (P2);
- US7 — Pantallas fieles al PNG, accesibles y traducidas (P2);
- US8 — Búsqueda multilingüe medida (P3);
- US9 — Evaluación de «Automático» antes de ofrecerlo (P2).

**Entorno** (quickstart):
- **Gradle:** todos los comandos requieren `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y `PATH="$JAVA_HOME/bin:$PATH"`.
- **Python:** siempre `offline/.venv/bin/python -I`.
- **Dispositivos:**
  - emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37);
  - teléfono de referencia `adb -s P31281001066`, **solo en la última fase**.

  `adb` está en `$HOME/Library/Android/sdk/platform-tools/adb`. **Nunca `connected*AndroidTest`**: se instalan los dos APK y se usa `am instrument`, con la salida a un fichero.
- **En el teléfono:**
  - nunca se borran ni se vuelven a descargar los modelos;
  - **«Borrar historial» no se confirma sin permiso expreso del usuario**, porque hay una conversación suya;
  - no se abre ni se captura su conversación ni su documento;
  - los volcados de UI van a `/data/local/tmp`; nunca a `/sdcard/ui.xml`;
  - las capturas recortan la barra de estado;
  - los registros se filtran a la app;
  - los ajustes se restauran.
- **Cambios ajenos en el árbol:** nunca entran en un commit. Solo `git add -- <rutas exactas>`, y se revisa `git diff --cached --stat` antes de cada commit.
- **Juicio manual:** por lotes. Se informa al usuario del avance cada poco.

**Commits**: atómicos, en inglés, uno por tarea o grupo lógico (constitución VIII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: ejecutable en paralelo (ficheros distintos, sin dependencias pendientes).
- **[Story]**: historia a la que pertenece (US1–US9).
- Abreviaturas de rutas:
  - `…/microrag/` = `app/src/main/java/com/lisdatasolutions/microrag/`;
  - `…/test/`, `…/androidTest/` y `…/debug/` usan el mismo paquete bajo `app/src/<conjunto>/java/`.

---

## Phase 1: Setup (idioma del prompt en el escritorio)

**Purpose**: el idioma de las respuestas y la negativa en inglés en el harness, sin cambiar nada de lo ya medido (contrato `eval-language.md`, L1–L4).

- [X] T001 [P] Pruebas de Python:
  - `offline/tests/test_prompt.py`:
    - `build_prompt(…, language="es")` es idéntico al de hoy;
    - con `language="auto"`, la única línea distinta es «- Responde en el idioma de la PREGUNTA, de forma clara y breve.»;
    - un idioma desconocido da error.
  - `offline/tests/test_judge.py`:
    - `is_refusal` acepta `REFUSAL` y `REFUSAL_EN` (con mayúsculas, puntuación y comillas distintas);
    - no acepta la frase seguida de más texto ni otras redacciones.
  - `offline/tests/test_runs.py`:
    - `answer_language="es"` no entra en el JSON y conserva las huellas de `informe` y de `v4`;
    - `"auto"` cambia la huella;
    - un valor desconocido se rechaza.
- [X] T002 Implementar L2–L4:
  - `LANGUAGES` y el parámetro `language` en `offline/eval/prompt.py`;
  - `REFUSAL_EN` e `is_refusal` en `offline/eval/judge.py`;
  - `answer_language` en `EvalConfig` y en `LATE_FIELDS` de `offline/eval/runs.py`;
  - `offline/eval/pipeline.py` pasa el idioma a `build_prompt`.

  T001 pasa, y la batería entera sigue en verde.

**Checkpoint**: el harness genera con «Automático» sin tocar las cifras ni las huellas existentes.

---

## Phase 2: User Story 9 - Evaluación de «Automático» (Priority: P2) — escritorio, adelantada

**Goal**: decidir con cifras si se ofrece «Automático» (FR-024, D-058), antes de construirlo en la app.

**Independent Test**: `language-review` y la combinación ES·EN dan sus cifras, y la decisión queda en DECISIONS.

- [X] T003 [P] [US9] Pruebas de Python, nuevo `offline/tests/test_language_review.py` (contrato L5–L6), con datos de ejemplo:
  - las respuestas iguales a `v4` heredan su juicio;
  - las que cambian van al CSV con sus columnas;
  - los juicios del CSV se conservan al regenerar;
  - una fila sin juicio deja el resultado pendiente;
  - la regla: 300 de 370, 51 de 56 y 94,3 %, que se cumple justo en el límite y falla con una sola cifra por debajo;
  - los bloques distintos de `v4` paran la revisión.
- [X] T004 [US9] Implementar `language-review`:
  - en `offline/eval/pipeline.py`, con el patrón de `limit_review`;
  - el comando en `offline/run_eval.py`;
  - una fila en la tabla de comandos de `offline/README.md`.

  T003 pasa.
- [X] T005 [US9] Ejecutar `run_eval.py language-review` (≈ 6 min) y juzgar a mano las respuestas que cambian, en `offline/reports/language-review.csv`:
  - método D-033, por lotes, con el juicio de `v4` a la vista;
  - informe de avance al usuario tras cada lote.
- [X] T006 [P] [US9] Pruebas de Python, nuevo `offline/tests/test_crosslang.py` (L7, L11–L13):
  - forma de `testset/crosslang/*.jsonl`: `source_id`, `previous` dentro del fichero y tipos;
  - la selección de las 15 sin respuesta con la semilla `20261101` es determinista;
  - cada combinación lleva su índice, su fichero y su idioma de respuesta, y `english_answers` cambia solo las combinaciones con preguntas en inglés;
  - las cifras se calculan por combinación, con el acierto de la búsqueda;
  - la referencia de correctas (D-060): ES·EN frente a sus preguntas de origen con `v4` y EN·ES frente a EN·EN; la regla de 10 puntos se cumple justo en el límite y falla por debajo.
- [X] T007 [US9] Crear `offline/testset/crosslang/es-docs.en.jsonl` (L7):
  - 72 preguntas traducidas al inglés: la muestra de F10, las 7 anteriores con respuesta y 15 sin respuesta (las 2 anteriores y 13 con la semilla);
  - revisión a mano de las traducciones.

  Ampliar `offline/eval/testset.py` para leer y validar conjuntos `crosslang`, y `validate-testset set=crosslang` en `offline/run_eval.py`.
- [X] T008 [US9] Implementar `crosslang-review` en `offline/eval/pipeline.py` y `offline/run_eval.py`, con las combinaciones de L11. Al principio solo está ES·EN, porque `en` aún no existe. Añadir sus filas, y la de `validate-testset set=crosslang`, a la tabla de `offline/README.md`. T006 pasa.
- [X] T009 [US9] Ejecutar `crosslang-review english_answers=es` para ES·EN y juzgar a mano sus 72 respuestas en `offline/reports/crosslang-review.csv` (L12). Con `auto`, las 72 respuestas salieron en español, y por eso «Automático» no se adopta (D-061); la medida sigue con las respuestas en español que dará la app. Resultado de ES·EN:
  - 29 de 57 correctas (50,9 %), frente al 82,5 % de sus preguntas de origen con `v4`: 31,6 puntos por debajo, así que no cumple L14;
  - 15 de 15 negativas y el 96,1 % de citas;
  - 16 negativas indebidas, 12 de ellas en preguntas que en español se respondían bien. La búsqueda acierta el bloque esperado en el 57,9 % de las preguntas;
  - de las 28 respuestas que no son correctas, 21 son fallos de búsqueda (sin bloque relevante en el contexto) y 7 de generación.
- [X] T010 [US9] **Decisión sobre «Automático»** (L6, más los umbrales de ES·EN y sus correctas a no más de 10 puntos de sus preguntas de origen con `v4`). Resultado: no se adopta; lo decidió el usuario con las cifras (D-061):
  - con las preguntas en español, 1 negativa y 1,4 puntos de citas por debajo de `v4`;
  - con las preguntas en inglés, las 72 respuestas en español.

  Idiomas muestra «Español» como única opción de respuesta (S12), y el prompt no cambia.

**Checkpoint**: «Automático» decidido y registrado.

---

## Phase 3: User Story 8 - Medida entre idiomas (Priority: P3) — escritorio, adelantada

**Goal**: medir los documentos en inglés con preguntas en los dos idiomas, cerrar el pendiente «Documentos en otro idioma» y fijar el texto de la tarjeta (FR-032, FR-033).

**Independent Test**: las cifras de ES·EN, EN·EN y EN·ES quedan en DECISIONS, y el texto de la tarjeta cumple la regla de L14.

- [X] T011 [P] [US8] Ampliar `offline/tests/test_corpus.py` (L8):
  - sitio de Wikibooks como parámetro, con `es` por defecto;
  - el manifiesto `corpus/en` con sus 13 campos y la licencia CC BY-SA 4.0;
  - `SOURCES.md` generado.

  Ampliar también `offline/tests/test_index.py` (L9): el índice `en` usa los parámetros de troceado de `rag_parameters.json` y no toca `chars_per_token.json`.
- [X] T012 [US8] Implementar el corpus y el índice `en` en `offline/eval/corpus.py`, `offline/eval/builds.py` y `offline/run_eval.py` (`fetch-corpus corpus=en`, `sources corpus=en`, `build en`), con sus filas en `offline/README.md`. T011 pasa.
- [X] T013 [US8] Elegir los 3 libros de en.wikibooks (temas distintos, tamaño parecido a los españoles y `revid` fijo) y escribirlos en `offline/corpus/en/manifest.json`. Después:
  - `fetch-corpus corpus=en --record`;
  - `sources corpus=en`;
  - `build en`.

  Resultado: Git (23 subpáginas), Basic Book Design (31) y Knots (71), sin la versión para imprimir; 229 KB de texto, 184 fragmentos y la caché `build/chars_per_token.json` sin tocar.
- [X] T014 [US8] Redactar `offline/testset/crosslang/en-docs.en.jsonl` con el método de F05 (L10: 40 con respuesta, 10 seguimientos y 15 sin respuesta) y su traducción `en-docs.es.jsonl`. `validate-testset set=crosslang` pasa. Resultado: 13, 13 y 14 preguntas con respuesta por libro, sobre una muestra fija de 16 fragmentos por libro (semilla `20261101`); las 15 sin respuesta se comprobaron contra el texto de los tres libros.
- [X] T015 [US8] Ejecutar `crosslang-review english_answers=es` para EN·EN y EN·ES (respuestas en español, D-061), y juzgar a mano sus unas 130 respuestas (L12). Resultado:
  - EN·EN: 40 de 50 correctas (80,0 %), 15 de 15 negativas, el 95,5 % de citas y la búsqueda acierta en el 90 %; cumple los umbrales;
  - EN·ES: 32 de 50 correctas (64,0 %), 16 puntos por debajo de EN·EN; 13 de 15 negativas, el 90,8 % de citas y la búsqueda acierta en el 78 %; no cumple L14;
  - 2 de las 65 respuestas de EN·EN salieron en inglés, o casi, aunque el prompt pide español.
- [X] T016 [US8] Registrar:
  - la sección «Idiomas (F12)» en `offline/eval/report.py`, con su prueba en `offline/tests/test_report.py`, y regenerar `offline/reports/eval-report.md`;
  - una entrada nueva en `docs/roadmap/DECISIONS.md` con las cifras por combinación, que **cierra el pendiente «Documentos en otro idioma»**; las mejoras que pidan las cifras van a la ficha de F14 en `docs/roadmap/FEATURES.md`;
  - el texto de la tarjeta (L14). **Si tiene que cambiar, se para** para que el usuario apruebe el texto propuesto (S10).

  Resultado (D-062): ES·EN y EN·ES no cumplen L14. El usuario aprobó el texto «Consulta documentos en español o en inglés. Funciona mejor si preguntas en el idioma del documento.» El pendiente queda cerrado y la palanca, en la ficha de F14.

**Checkpoint**: medida registrada, pendiente cerrado y texto de la tarjeta decidido.

---

## Phase 4: Foundational (bloquea las historias de la app)

**Purpose**: la paridad del prompt y de la negativa, el dominio de ajustes, los fakes, el design system y los textos.

- [X] T017 [P] Pruebas JVM de la negativa (P17): `…/test/…/rag/answer/AnswerReadingTest.kt`, con los mismos casos que `test_judge.py` para `REFUSAL_EN`. El prompt no cambia (D-061): ni `PromptBuilderTest` ni `AnswerGoldenTest` (426 casos) se tocan.
- [X] T018 Implementar `REFUSAL_EN` en `…/microrag/rag/answer/PromptBuilder.kt`, junto a `REFUSAL`, e `isRefusal` con las dos frases en `…/microrag/rag/answer/AnswerReading.kt`. `PromptLanguage` se queda solo con `SPANISH`. T017 pasa.
- [X] T019 Crear el dominio de ajustes (data-model §1–§2, contrato `settings-api.md`) en `…/microrag/domain/settings/`:
  - `SettingsModels.kt`: `AppLanguage`, `LanguageSettings` (idioma de la app y `readFailed`), `SaveResult` y `ModelOverview`;
  - `AppLanguageController.kt` y `SettingsNotices.kt`.

  Sin idioma de las respuestas ni tienda de preferencias (D-061).
- [X] T020 [P] Crear `…/test/…/testing/SettingsFakes.kt`:
  - un controlador de idioma con `recreatesItself`, fallo al aplicar y fallo de lectura programables;
  - una vista de modelos programable.
- [X] T021 [P] Design system (contrato `settings-ui.md` §3):
  - `SettingsTrailing.ChipAndChevron` y `onClickLabel` en `LisdataSettingsRow`;
  - `LisdataFactRow` en `…/microrag/core/designsystem/component/Rows.kt`;
  - el tile del hero, si hace falta, en `Cards.kt`;
  - entradas en `…/debug/…/debug/catalog/CatalogEntries.kt`.

  `CatalogAccessibilityTest` las cubre.
- [X] T022 [P] Textos: `app/src/main/res/values/strings_settings.xml` y `app/src/main/res/values-en/strings_settings.xml`, con todas las claves de `settings-ui.md` §4. Los nombres de idioma van con `translatable="false"`.
- [X] T023 [P] Regla nueva en `…/test/…/architecture/LayeringTest.kt` (U1): `feature/settings` no importa `data.`, `androidx.sqlite`, `androidx.work`, LiteRT, `java.io.File` ni `android.app.LocaleManager`.

**Checkpoint**: tipos, fakes, componentes y textos listos; el prompt y la negativa, con paridad con el escritorio.

---

## Phase 5: User Story 1 - Ver el estado real de los modelos (Priority: P1) 🎯 MVP

**Goal**: Ajustes sustituye al marcador, con los dos modelos en su estado real y su ficha (FR-001–FR-008).

**Independent Test**: con los modelos en cada estado, las filas y la ficha muestran lo que dice la Preparación, y «Local» solo aparece listo (SC-001, SC-002).

### Tests for User Story 1

- [X] T024 [P] [US1] `…/test/…/domain/settings/ObserveModelOverviewUseCaseTest.kt`:
  - el orden y los datos del catálogo;
  - `dimensions` solo en `EG2_270M`;
  - el estado es el de `ObserveModelSetupUseCase`.
- [X] T025 [P] [US1] `…/test/…/feature/settings/SettingsViewModelTest.kt` (parte de modelos):
  - refresca al entrar;
  - sin chip hasta la primera lectura;
  - las filas cambian sin salir cuando cambia el estado de un modelo (FR-005), y al refrescar deja de decir «Local» si falta el fichero;
  - P14: `Ready(GPU)` es «Local», Gemma en CPU es «Modo CPU, más lento» y los demás estados van como en la Preparación;
  - la ficha abierta sobrevive en `SavedStateHandle`.
- [X] T026 [P] [US1] `…/test/…/feature/settings/SettingsScreenTest.kt` (Robolectric):
  - la estructura del PNG 09, con secciones y filas;
  - los chips;
  - la ficha con sus seis filas, el SHA-256 abreviado y su descripción, y «—» en el procesador;
  - un modelo sin verificar muestra su estado real en la ficha, nunca «Local» (FR-007);
  - «Completar configuración» solo si no está listo;
  - U4: un solo nodo por fila de modelo, con la acción «Ver detalles».

### Implementation for User Story 1

- [X] T027 [US1] Implementar `ObserveModelOverviewUseCase` en `…/microrag/domain/settings/SettingsUseCases.kt` y registrarlo en `…/microrag/domain/di/DomainModule.kt`. T024 pasa.
- [X] T028 [US1] Crear `…/microrag/feature/settings/`:
  - `SettingsUiState.kt` y `SettingsViewModel.kt` (modelos);
  - `SettingsScreen.kt`, con la estructura completa del PNG 09; las acciones de las demás filas son callbacks;
  - `ModelSheet.kt`;
  - `SettingsRoute.kt`;
  - `SettingsPreviewData.kt`.

  Registrar el ViewModel en `…/microrag/feature/di/FeatureModule.kt`.
- [X] T029 [US1] Sustituir el marcador de `Settings` en `…/microrag/core/navigation/MicroRagNavHost.kt`:
  - Atrás va a Inicio;
  - «Completar configuración» abre `Setup`.

  Actualizar `…/androidTest/…/SystemBackTest.kt` y `…/test/…/core/navigation/NavigationTest.kt`, que dejan de esperar el marcador «F12» en Ajustes. T025 y T026 pasan.

**Checkpoint**: Ajustes con los modelos reales y su ficha. MVP validable en la JVM y en el emulador.

---

## Phase 6: User Story 4 - Borrar el historial de chat (Priority: P1)

**Goal**: «Borrar historial de chat» con confirmación, a través de la sesión del chat (FR-012–FR-014, R10).

**Independent Test**: con conversaciones, una de ellas abierta y una respuesta en curso, al confirmar no queda ninguna, el índice no cambia y la siguiente pregunta se guarda (SC-006).

### Tests for User Story 4

- [X] T030 [P] [US4] Ampliar `…/androidTest/…/data/chat/SqliteChatRepositoryTest.kt`:
  - `conversationCount`: cuenta solo las conversaciones con turnos;
  - `conversationExists`;
  - `deleteAll` borra conversaciones, turnos y fuentes, y no toca un `rag.db` de prueba.
- [X] T031 [P] [US4] Ampliar `…/test/…/data/chat/DefaultChatSessionTest.kt` y `…/test/…/testing/ChatFakes.kt`:
  - P9: con una respuesta en curso, `deleteAll` la detiene, espera y borra; `current` queda en `null` y `historyCleared` sube 1;
  - una `ChatStorageException` no sube el contador;
  - ninguna pregunta empieza entre la parada y el borrado;
  - P10: `ask` con un id borrado crea una conversación y la respuesta se guarda.
- [X] T032 [P] [US4] Ampliar `…/test/…/feature/chat/ChatViewModelTest.kt` (P11): tras `historyCleared`, el chat queda vacío y se cierra el diálogo de borrar; el valor inicial no hace nada.
- [X] T033 [P] [US4] Ampliar `SettingsViewModelTest` y `SettingsScreenTest`:
  - P12: con 0 conversaciones, aviso sin diálogo;
  - el diálogo, con y sin la frase de la respuesta en curso;
  - al confirmar, aviso de éxito o de fallo y su anuncio;
  - cancelar no borra;
  - el diálogo sobrevive a un giro.

### Implementation for User Story 4

- [X] T034 [US4] Implementar `conversationCount` y `conversationExists` en `…/microrag/domain/chat/ChatRepository.kt` y `…/microrag/data/chat/SqliteChatRepository.kt`. T030 pasa en el emulador.
- [X] T035 [US4] Implementar:
  - `deleteAll` y `historyCleared` en `…/microrag/domain/chat/ChatSession.kt` y `…/microrag/data/chat/DefaultChatSession.kt`;
  - P10 en `ask`;
  - `CountConversationsUseCase`, `ClearChatHistoryUseCase` y `ObserveHistoryClearedUseCase` en `…/microrag/domain/chat/ChatUseCases.kt`, con DI.

  T031 pasa.
- [X] T036 [US4] Implementar P11 en `…/microrag/feature/chat/ChatViewModel.kt`. T032 pasa.
- [X] T037 [US4] Implementar en Ajustes el diálogo, los avisos (`SnackbarHost`) y su anuncio (S6). T033 pasa.

**Checkpoint**: borrar el historial funciona de extremo a extremo, y el chat lo sigue.

---

## Phase 7: User Story 2 - Elegir el idioma de la app (Priority: P1)

**Goal**: el idioma de la app se elige y se aplica en todas las versiones, sin AppCompat (FR-018–FR-021, R2).

**Independent Test**: con Robolectric en `sdk = 32` y `sdk = 36`, aplicar inglés y volver a español: el idioma se guarda, se aplica y las notificaciones lo siguen (SC-003).

### Tests for User Story 2

- [X] T038 [P] [US2] `…/test/…/core/locale/AppLocaleTest.kt` (Robolectric):
  - P1: español por defecto, aunque el sistema esté en inglés;
  - en `sdk = 32`: el fichero se lee en `init`, y ausente o ilegible da español;
  - en `sdk = 36`: `LocaleManager`;
  - P4: el paso de Android 12 a 13 aplica el fichero una vez y lo borra;
  - `localized(context)` sigue al idioma vigente.
- [X] T039 [P] [US2] `…/test/…/core/locale/PlatformAppLanguageControllerTest.kt`:
  - en `sdk = 32`: `apply` escribe de forma atómica, actualiza la memoria y devuelve `false` si falla, sin cambiar nada; `recreatesItself = false`;
  - en `sdk = 36`: `setApplicationLocales` con `es` o `en`; `current()` sigue al sistema; `recreatesItself = true`.
- [X] T040 [P] [US2] Ampliar `…/test/…/data/indexing/IndexingNotificationsTest.kt` y crear `…/test/…/data/download/DownloadNotificationsTest.kt` (P5): tras cambiar el idioma, la notificación siguiente sale en el idioma nuevo sin reiniciar.

### Implementation for User Story 2

- [X] T041 [US2] Ampliar `…/microrag/core/locale/AppLocale.kt` (`init`, `localized`, fichero) y crear `…/microrag/core/locale/PlatformAppLanguageController.kt`. Registrarlo en `…/microrag/core/di/CoreModule.kt`, junto con `SettingsNotices`. T038 y T039 pasan.
- [X] T042 [US2] `…/microrag/MicroRagApplication.kt`: `AppLocale.init(base)` antes de `wrap`, y la migración de P4 en `onCreate`.
- [X] T043 [US2] `…/microrag/data/indexing/IndexingNotifications.kt` y `…/microrag/data/download/DownloadNotifications.kt` con `AppLocale.localized(context)`. T040 pasa.

**Checkpoint**: el idioma de la app se guarda y se aplica en las dos rutas de versión.

---

## Phase 8: User Story 3 - Elegir el idioma de las respuestas, y la pantalla Idiomas (Priority: P1)

**Goal**: Idiomas sustituye al marcador. Guarda los dos idiomas, y el chat aplica el de las respuestas al prompt (FR-015–FR-017, FR-022–FR-026).

**Independent Test**: Idiomas guarda el idioma de la app y muestra «Español» como única opción de respuesta. El prompt sigue siendo el del escritorio (SC-004, SC-005).

### Tests for User Story 3

- [X] T044 [P] [US3] **Retirada (D-061):** no hay tienda del idioma de las respuestas.
- [X] T045 [P] [US3] `…/test/…/domain/settings/SaveLanguageSettingsUseCaseTest.kt` (P6):
  - sin cambios, `Unchanged` y ninguna escritura;
  - si aplicar el idioma de la app falla, `Failed` y nada cambia;
  - `SettingsNotices.saved` solo cuando va bien.
- [X] T046 [P] [US3] **Retirada (D-061):** el prompt no cambia. La negativa en inglés como negativa (C1) se cubre en T017 y en `DefaultChatSessionTest`, que ya usa `AnswerReading`.
- [X] T047 [P] [US3] `…/test/…/feature/settings/LanguagesViewModelTest.kt`:
  - al abrir, marca lo guardado;
  - la selección sobrevive en `SavedStateHandle`;
  - «Guardando…», su anuncio (U7) y el segundo toque;
  - `Close(recreate)` según el controlador, y sin cambios vuelve atrás sin escribir;
  - la sección de respuestas tiene una sola opción, «Español», marcada (S12);
  - P19: tras una recreación con otro idioma del sistema, la selección sigue al actual si el usuario no la había tocado, y se conserva si la había tocado.
- [X] T048 [P] [US3] `…/test/…/feature/settings/LanguagesScreenTest.kt` (Robolectric):
  - la estructura del PNG 10 (eyebrow, título, hero, dos grupos, tarjeta y botón), con una sola opción en «Idioma de las respuestas» (S12);
  - U3: grupos de opciones con rol, estado y posición («1 de 1» en el de las respuestas);
  - con la app en inglés, las opciones de S11.

### Implementation for User Story 3

- [X] T049 [US3] **Retirada (D-061):** sin tienda del idioma de las respuestas.
- [X] T050 [US3] Implementar `ObserveLanguageSettingsUseCase` y `SaveLanguageSettingsUseCase` (solo el idioma de la app) en `…/microrag/domain/settings/SettingsUseCases.kt`, con DI. T045 pasa.
- [X] T051 [US3] **Retirada (D-061):** `DefaultChatSession` no cambia de prompt.
- [X] T052 [US3] Crear `LanguagesUiState.kt`, `LanguagesViewModel.kt`, `LanguagesScreen.kt` y `LanguagesRoute.kt` en `…/microrag/feature/settings/`:
  - `recreate()` en Android 12 tras volver atrás;
  - el texto de la tarjeta, el que decidió T016.

  Sustituir el marcador de `Languages` en `MicroRagNavHost` y registrar el ViewModel. Actualizar `NavigationTest` y cualquier prueba que aún espere el marcador «F12». T047 y T048 pasan.
- [X] T053 [US3] Anuncio de P7: `…/microrag/core/navigation/MicroRagAppShell.kt` consume `SettingsNotices.saved` y lo anuncia con una región en vivo. Prueba Robolectric en `…/test/…/core/navigation/` con recreación.

**Checkpoint**: Idiomas guarda el idioma de la app y dice en qué idioma responde el chat.

---

## Phase 9: User Story 6 - Preferencias que no se pierden (Priority: P2)

**Goal**: los fallos al leer y al guardar se ven y no dejan nada a medias (FR-027, FR-028, S8).

**Independent Test**: con fallos programados, Idiomas avisa y conserva los valores anteriores, y el resto de la app funciona (SC-007).

- [X] T054 [P] [US6] Ampliar `LanguagesViewModelTest` y `LanguagesScreenTest`:
  - con `readFailed`, la tarjeta de aviso y los valores por defecto marcados;
  - un fallo al guardar da el aviso de error, que se anuncia una vez; el botón vuelve a estar activo y los valores no cambian; reintentar funciona;
  - la app sigue usable.
- [X] T055 [US6] Implementar la tarjeta de aviso de lectura y el aviso de error al guardar en `LanguagesScreen`. T054 pasa.
- [X] T056 [US6] Prueba instrumentada `…/androidTest/…/core/locale/PlatformAppLanguageControllerTest.kt` en el emulador `MicroRAG_API33`: aplicar English y volver a Español con el `LocaleManager` real; `current()` los lee. Deja el idioma como estaba.

**Checkpoint**: preferencias robustas ante fallos.

---

## Phase 10: User Story 5 - Moverse desde Ajustes (Priority: P2)

**Goal**: todas las entradas de Ajustes y el acceso «Idiomas» de Inicio llevan a su sitio (FR-009–FR-011, FR-016).

**Independent Test**: recorrer las entradas con Atrás en cada pantalla (`NavigationTest`).

- [X] T057 [P] [US5] Ampliar `NavigationTest`:
  - Ajustes → Idiomas → Guardar o Atrás → Ajustes;
  - Inicio → Idiomas → Inicio;
  - Ajustes → Índice → Atrás → Ajustes;
  - ficha → Preparación;
  - Atrás de Ajustes → Inicio;
  - nunca dos Idiomas en una pila.

  Ampliar `SettingsScreenTest`:
  - el subtítulo de «Idiomas» es el idioma de la app;
  - «Procesamiento 100% local» no tiene acción y lleva la descripción de S5 (U5).
- [X] T058 [US5] Conectar las acciones de «Idiomas» y de «Índice y rendimiento» en `MicroRagNavHost`, con el subtítulo de `AppLanguageController.current()` en `SettingsViewModel`. T057 pasa.

**Checkpoint**: navegación completa.

---

## Phase 11: User Story 7 - Pantallas fieles al PNG, accesibles y traducidas (Priority: P2)

**Goal**: accesibilidad U2–U10, paridad de textos y revisión del inglés (FR-029, FR-030, SC-008, SC-009, SC-011).

**Independent Test**: ATF en verde en todos los estados y configuraciones; `StringsParityTest` en verde; las correcciones del inglés, listadas.

- [X] T059 [P] [US7] `…/test/…/resources/StringsParityTest.kt`:
  - los mismos `string` y `plurals` traducibles en `values` y `values-en`;
  - los mismos marcadores de formato en cada par;
  - los `translatable="false"`, solo en `values`.
- [X] T060 [P] [US7] `…/androidTest/…/feature/settings/SettingsScreenAccessibilityTest.kt` y `LanguagesScreenAccessibilityTest.kt`:
  - ATF en Ajustes, la ficha, el diálogo e Idiomas, con los estados de S1, S6 y S8;
  - en claro y en oscuro, con la letra al 200 % y en horizontal.
- [X] T061 [US7] Ajustar las pantallas hasta que T059 y T060 pasen en el emulador. Si ATF marca algo que contradice el PNG o una propuesta aprobada, **se para y se consulta**. Resultado: las 72 combinaciones pasan en `MicroRAG_API33` sin cambiar las pantallas; solo se ajustó la prueba para medir sobre la raíz de la actividad cuando hay una ficha o un diálogo abiertos.
- [X] T062 [US7] Revisión del inglés de todos los textos de `app/src/main/res/values-en/` (R13), en un commit propio, con la lista de correcciones para el informe de cierre. Resultado: 451 textos revisados y 12 corregidos:
  - citas: «Fragment»/«Fragments» pasan a «Chunk»/«Chunks», como el resto de la app;
  - «Open source» pasa a «Open the source», que no se confunde con código abierto;
  - «What do you want to explore today?» pasa a «What would you like to explore today?»;
  - indexación: las dos pausas dicen «Indexing will resume…» en lugar de «It will go on…», y «It is needed to index» pasa a «It is needed for indexing»;
  - importación: «Only text-based PDFs, TXT and Markdown files are supported.»;
  - borrar documento: comillas inglesas “…” en lugar de «…»;
  - modelos: «Download failed (…)», «Free space: … (… will be left)» y «No connection: the download will start once you are online».

**Checkpoint**: accesibilidad y textos completos.

---

## Phase 12: Polish sin teléfono

- [ ] T063 Pasar toda la batería:
  - Python (`unittest discover -s offline/tests`) y `gen_answer_golden.py --check`;
  - `./gradlew :app:testDebugUnitTest :app:lintDebug`, sin errores nuevos;
  - `NetworkConfinementTest` sigue en verde, y los registros de una sesión de Ajustes e Idiomas solo llevan tipos de fallo, nunca valores (P18, SC-010).
- [ ] T064 Batería instrumentada completa en `Pixel_10` y en `MicroRAG_API33`.
- [ ] T065 Recorrido en el emulador (quickstart §3):
  - API 33 y API 37;
  - cambio de idioma de ida y vuelta, con la pestaña Chat y una notificación de indexación;
  - ajuste del sistema;
  - una pregunta en inglés, que responde en español (D-061), si Gemma está preparada allí;
  - borrar el historial;
  - letra al 200 %, horizontal y modo oscuro;
  - capturas.
- [ ] T066 Borrador de `specs/012-settings-languages/report.md`: cifras de US8 y US9, diferencias con los PNG, correcciones del inglés y resultados. Avance de tareas en este fichero.

---

## Phase 13: Última fase — teléfono de referencia y cierre

- [ ] T067 Batería instrumentada completa en el A063 con los APK finales.
- [ ] T068 Recorrido acordado con el usuario antes de empezar (quickstart §4):
  - capturas de Ajustes y de Idiomas para compararlas con los PNG;
  - cambio de idioma de ida y vuelta;
  - ficha de modelo;
  - el diálogo de borrar se abre y se **cancela**;
  - si el usuario lo aprueba, con documentos públicos de prueba en una carpeta propia (como T069 de F11), que se borran al terminar:
    - una pregunta en inglés, que responde en español con sus fuentes (D-061);
    - un cambio de idioma de la app con una respuesta en curso, que sigue sin reiniciarse;
  - sin tráfico de red de la app.

  El teléfono queda con su idioma y sus ajustes.
- [ ] T069 Cerrar `report.md` y actualizar `docs/roadmap/FEATURES.md` a «implementada; pendiente de la revisión de cierre». **Parada para la revisión de cierre.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (fase 1):** sin dependencias.
- **US9 (fase 2):** depende de la fase 1. **T010 puede parar el trabajo.**
- **US8 (fase 3):** depende de T008 (comando) y de T010 (idioma de respuesta de las preguntas en inglés). **T016 puede parar** si cambia el texto de la tarjeta.
- **Foundational (fase 4):**
  - T017 y T018 dependen de T002, por la paridad con los mismos casos;
  - T019 no depende ya de T010: «Automático» no se ofrece (D-061);
  - el resto, sin dependencias.

  **Bloquea US1–US7.**
- **US1 (fase 5):** depende de la fase 4. Es el MVP.
- **US4 (fase 6):** depende de US1, por la pantalla de Ajustes.
- **US2 (fase 7):** depende de la fase 4. Puede ir en paralelo con US1 y US4.
- **US3 (fase 8):** depende de US2 (la pantalla usa el controlador) y de T016 (texto de la tarjeta).
- **US6 (fase 9):** depende de US3.
- **US5 (fase 10):** depende de US1 y US3.
- **US7 (fase 11):** depende de US1–US6.
- **Polish (fase 12):** depende de todo lo anterior.
- **Última fase (13):** depende de todo y de que el teléfono esté disponible.

### Within Each User Story

- Las pruebas primero, y deben fallar.
- Dominio y datos antes que el ViewModel; el ViewModel antes que la pantalla.
- **Paradas de consulta:**
  - T010 (hecha: «Automático» no se adopta, D-061);
  - T016, si cambia el texto de la tarjeta;
  - T061, si ATF contradice el PNG;
  - T068: el recorrido se acuerda antes con el usuario.

### Parallel Opportunities

- **Escritorio frente a app:** mientras se juzga a mano (T005, T009 y T015), la base de la app avanza: T019–T023 y US2.
- **Fase 4:** T020–T023 en paralelo.
- **US1:** T024–T026.
- **US4:** T030–T033.
- **US2:** T038–T040.
- **US3:** T045, T047 y T048.

## Parallel Example: User Story 4

```text
T030 SqliteChatRepositoryTest   T031 DefaultChatSessionTest   T032 ChatViewModelTest   T033 SettingsViewModelTest/SettingsScreenTest
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1–3 en el escritorio, con «Automático» y la tarjeta decididos.
2. Fase 4: paridad, dominio, componentes y textos.
3. US1: Ajustes con los modelos reales y su ficha. **Parar y validar** en la JVM y en el emulador.

### Incremental Delivery

1. **US4:** borrar el historial.
2. **US2 y US3:** idiomas, con lo que las historias P1 quedan completas.
3. **US6 y US5:** fallos de las preferencias y navegación completa.
4. **US7:** accesibilidad y textos.
5. **Polish sin teléfono.** Se avisa al usuario.
6. **Última fase en el teléfono:** recorrido acordado y cierre.
