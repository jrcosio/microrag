# Research: Ajustes e Idiomas (F12)

Fecha: 2026-11-01. Todas las decisiones parten de:
- el código actual (F01–F11);
- la spec aclarada (D-058), con las propuestas de UI S1–S11 aprobadas;
- el harness de escritorio de F05 y F11 (`offline/eval/`).

**No hay dependencias nuevas** (R16).

**Orden de trabajo** (petición del usuario, como en F10 y F11): las pruebas en el teléfono de referencia van en la última fase. Antes van:
- el trabajo en el escritorio, que decide si se ofrece «Automático» y el texto de la tarjeta;
- el código;
- las pruebas JVM;
- las pruebas instrumentadas en el emulador.

## R1. Dónde vive cada pieza

**Decisión:** el reparto por capas de F08–F11 (constitución II).

| Paquete | Contenido | Por qué ahí |
|---|---|---|
| `domain/settings` | `AnswerLanguage`, `AppLanguage`, `LanguageSettings`, interfaces `LanguageSettingsStore` y `AppLanguageController`, y los casos de uso de idiomas, modelos y borrado del historial | El contrato que ven las pantallas. No importa DataStore ni la plataforma |
| `data/settings` | `DataStoreLanguageSettingsStore` (idioma de las respuestas) | Como las otras tiendas DataStore (`data/onboarding`, `data/indexing`) |
| `core/locale` | `AppLocale` ampliado y `PlatformAppLanguageController` (idioma de la app) | Ya contiene `AppLocale` (D-029). Lo usan la `Application`, la actividad y las notificaciones, antes de que exista Koin |
| `rag/answer` | `PromptLanguage.AUTOMATIC` y la negativa en inglés | Algoritmos puros, con paridad con el escritorio (R5, R6) |
| `data/chat` | `deleteAll` en la sesión, y el idioma de las respuestas en el prompt | La orquestación del chat ya vive ahí (F11) |
| `feature/settings` | `SettingsRoute`/`Screen`/`ViewModel`/`UiState`, la ficha de modelo y `LanguagesRoute`/`Screen`/`ViewModel`/`UiState` | El patrón de F08–F11 |
| `core/designsystem` | El trailing «chip + chevron» de `LisdataSettingsRow`, la fila de dato de la ficha y el grupo de opciones | Un único sistema de componentes (constitución VII) |

**Textos de estado de los modelos:** `feature/settings` reutiliza `chipText()`, `statusKind()`, `detailText()` y `bytesText()` de `feature/onboarding/setup/SetupTexts.kt`. Ya hay importaciones así entre features (`document` → `ingest`, `home` → `indexing`), y la misma fila dice lo mismo en la Preparación y en Ajustes.

## R2. Idioma de la app: mecanismo propio, sin AppCompat

**Problema.** Hoy `AppLocale.resolve()` solo lee `LocaleManager` (API 33+). En Android 12 (minSdk 31) no hay dónde guardar la elección, y nadie llama a `setApplicationLocales`. El contexto de la `Application` se envuelve una vez por proceso, así que las notificaciones seguirían en el idioma anterior hasta reiniciar.

**Decisión:** `PlatformAppLanguageController` en `core/locale`, con una fuente de verdad por versión.

- **Android 13+ (API 33+):**
  - la fuente es `LocaleManager.applicationLocales`, que guarda el sistema;
  - guardar llama a `setApplicationLocales(LocaleList.forLanguageTags(tag))`, con `es` o `en` explícitos;
  - el sistema recrea las actividades;
  - queda coherente en los dos sentidos con el ajuste de idioma por app del sistema (FR-020).
- **Android 12 (API 31–32):**
  - la fuente es el fichero `noBackupFilesDir/settings/app_language`, una línea con `es` o `en`;
  - se lee una sola vez, en `MicroRagApplication.attachBaseContext`, antes de instalar StrictMode, y queda en memoria en `AppLocale`;
  - guardar escribe el fichero fuera del hilo principal (temporal y renombrado atómico), actualiza la memoria y pide a la pantalla `activity.recreate()`.
- **Sin elección:** español (D-029). Si `LocaleManager` está vacío o el fichero no existe, el idioma del sistema no cuenta.
- **Paso de Android 12 a 13:** al arrancar en API 33+, si `LocaleManager` está vacío y existe el fichero, se aplica su valor una vez y se borra el fichero.
- **Notificaciones:** `IndexingNotifications` y `DownloadNotifications` usan `AppLocale.localized(context)` cada vez que construyen una notificación, en lugar del contexto de la app envuelto al arrancar. Así siguen el cambio sin reiniciar el proceso. Los nombres de los canales se actualizan la próxima vez que se crean.
- **La actividad recreada** conserva los ViewModels, la pila de navegación y la respuesta en curso, que vive en el ámbito de la app (F11 R6). Nada reindexa.

**Alternativas descartadas:**
- **AppCompat 1.8.0** (`AppCompatDelegate.setApplicationLocales` con `autoStoreLocales`):
  - exige `AppCompatActivity` y un tema AppCompat (hoy `android:Theme.Material.Light.NoActionBar`);
  - suma una dependencia;
  - guarda su propio fichero fuera de `noBackupFilesDir`.
- **Guardar el idioma de la app en DataStore:** habría que leerlo de forma síncrona en `attachBaseContext` (`runBlocking`), en el hilo principal y antes de que la app pinte.
- **Ofrecer el selector solo en Android 13+:** incumple FR-020.

## R3. Idioma de las respuestas: DataStore con manejo de errores

**Decisión:** `DataStoreLanguageSettingsStore` sobre `noBackupFilesDir/datastore/settings.preferences_pb`, con la clave `answer_language` (`es` o `auto`). Se registra como `single` en `DataModule`, con el patrón `create(noBackupDir, scope)` de las otras tiendas.

- **Lectura** (FR-028):
  - `data.catch { IOException }` emite los valores por defecto y marca `readFailed = true`;
  - `ReplaceFileCorruptionHandler` sustituye un fichero dañado por uno vacío y deja un aviso en el registro, solo con el tipo de fallo;
  - un valor desconocido se lee como `SPANISH`.
- **Escritura:** `edit` dentro de `try/catch (IOException)` devuelve un resultado. Si falla, el valor anterior sigue en el fichero, porque DataStore escribe de forma atómica.
- **Por defecto:** `SPANISH` (D-058).

**DataStore se queda en 1.2.1** (R16): la 1.3.0 es del 2026-10-21 y no aporta nada que F12 necesite. La API de `Preferences` es la misma.

## R4. Guardar en Idiomas

**Decisión:** `SaveLanguageSettingsUseCase(app, answer)` guarda primero lo que puede fallar sin efectos visibles:
1. **Idioma de las respuestas**, si ha cambiado: se escribe en DataStore. Si falla, se devuelve el error y no cambia nada.
2. **Idioma de la app**, si ha cambiado: se aplica (R2). Si falla, lo que solo puede pasar en Android 12 al escribir el fichero, se repone el idioma de las respuestas anterior y se devuelve el error.
3. **Resultado:** `Saved(appLanguageChanged)`.

**En la pantalla** (S7–S9):
- mientras guarda, el botón dice «Guardando…» y no responde a toques; el estado vive en el ViewModel y un segundo toque no hace nada;
- si todo va bien, el ViewModel emite `Close(recreate)`:
  - la pantalla vuelve atrás (`popBackStack`), a Ajustes o a Inicio;
  - en Android 12, si ha cambiado el idioma de la app, llama además a `activity.recreate()`;
  - en Android 13+ la recreación la hace el sistema. Si llega antes de volver atrás, el evento sigue en el canal del ViewModel, que sobrevive a la recreación, y la pantalla nueva vuelve atrás;
- si no hay cambios, vuelve atrás sin escribir nada;
- si falla, aparece un aviso de error en Idiomas, se conservan los valores anteriores y el usuario puede volver a pulsar.

**Aviso «Preferencias guardadas» en el idioma nuevo (S9):**
- `SaveLanguageSettingsUseCase` deja una nota en `SettingsNotices`, un `MutableStateFlow` en el ámbito de la app;
- el armazón (`MicroRagAppShell`) la consume después de la recreación y la anuncia con una región en vivo educada, con el patrón de F08;
- el texto se lee de los recursos ya recreados.

## R5. El idioma de las respuestas en el prompt

**Decisión:**
- `PromptLanguage` pasa a ser `SPANISH("español")` y `AUTOMATIC("el idioma de la PREGUNTA")`. Con `AUTOMATIC`, la única línea que cambia es «- Responde en el idioma de la PREGUNTA, de forma clara y breve.»
  - «PREGUNTA» va en mayúsculas, igual que las etiquetas y las reglas de `v3` y `v4`.
  - La redacción queda fijada antes de evaluar (R7).
- **Escritorio:** `build_prompt(…, language="es")`, con `LANGUAGES = {"es": "español", "auto": "el idioma de la PREGUNTA"}`.
- **Paridad:** casos literales compartidos en `test_prompt.py` y `PromptBuilderTest`.
  - El golden de 426 casos no cambia: es español, y SC-004 exige que siga idéntico.
  - Si se adopta «Automático», `gen_answer_golden.py` no cambia de forma.
- **En la sesión:** `DefaultChatSession` recibe `answerLanguage: () -> Flow<AnswerLanguage>` y lo lee con `first()` al empezar cada respuesta, también al reintentar. Así, un cambio solo afecta a la pregunta siguiente (FR-022), y un fallo de lectura da español (R3).

## R6. La negativa en español y en inglés

**Decisión:**
- `REFUSAL_EN = "I can't find that information in the available documents."` se fija antes de evaluar. Es la traducción literal del centinela.
- `judge.is_refusal` y `AnswerReading.isRefusal` aceptan las dos frases, con la misma `norm`.
- El prompt sigue pidiendo la frase exacta en español: solo cambia la línea del idioma (D-058).
- Para el español no cambia nada: ninguna respuesta del golden coincide con la frase inglesa, y `AnswerGoldenTest` y `--check` siguen pasando.
- **En la UI:** una negativa en cualquiera de los dos idiomas se muestra con el texto de C1 en el idioma de la app, sin fuentes (FR-026).
- **Variantes no previstas:** una negativa en inglés con otras palabras se lee como respuesta sin citas (C2 de F11). La evaluación la cuenta como negativa fallida, y el juicio manual lo anota.

## R7. Evaluación de «Automático» con preguntas en español (`language-review`)

**Decisión:** un comando nuevo, `run_eval.py language-review`, con el patrón de `limit-review`.

- **Configuración:**
  - la elegida (D-034), con `v4`;
  - `answer_language` es un campo nuevo de `EvalConfig` con valor por defecto `es`, y está en `LATE_FIELDS`, así que las huellas de las ejecuciones anteriores no cambian.
- **Base:** las filas manuales de `v4` mezcladas, como en `limit-review`: F05 más la revisión de la plantilla. Son 300 de 370 correctas (81,1 %), 51 de 56 negativas (91,1 %) y 559 de 593 citas (94,3 %).
- **Generación:**
  - se regeneran las 426 preguntas con `answer_language=auto`, unos 6 minutos;
  - los bloques tienen que ser idénticos a los de `v4` (comprobación G8);
  - no se reconstruye `ref`, porque su huella invalidaría la ejecución de `v4` (`a8c1902c`).
- **Revisión:**
  - las respuestas que cambian van a `reports/language-review.csv`;
  - columnas: `id, tipo, pregunta, respuesta_esperada, datos_clave, fuentes, respuesta_v4, manual_v4, respuesta_auto, regla, regla_citas, manual, manual_citas, comentario`;
  - las que no cambian heredan el juicio de `v4`.
- **Juicio:** método D-033, como en D-056 y D-057. Lo hace el asistente, por lotes y con el veredicto de `v4` a la vista. Se esperan unas 220 respuestas que cambian: una línea distinta cambió el 52 % de las respuestas en la revisión de la plantilla.
- **Regla** (FR-024, fijada antes de evaluar):
  - correctas ≥ 300 de 370;
  - negativas ≥ 51 de 56, que además cumple el umbral de 85 %;
  - citas ≥ 94,3 %.
- **Salida:** `build/language-review/result.json` y la sección del informe (R9).
- **Adopción de «Automático»:** se cumple esta regla y, además, las preguntas en inglés sobre los documentos en español (ES·EN de R9, con «Automático») cumplen los umbrales del informe. Sus respuestas correctas no pueden quedar más de 10 puntos por debajo de las mismas 57 preguntas en español con `v4`, según los juicios ya hechos (analyze, D-060). Así se aplican las dos partes de FR-024.

## R8. Preguntas en inglés y corpus en inglés

**Decisión:** ficheros aparte, sin tocar `questions.jsonl`, el corpus español ni `ref`. Tocarlos cambiaría las huellas y los 426 casos que comprueban el golden y las pruebas.

**Preguntas en inglés sobre los documentos en español** (`testset/crosslang/es-docs.en.jsonl`):
- traducción al inglés de:
  - la muestra de F10 (40 con respuesta y 10 seguimientos);
  - las 9 preguntas anteriores de esos seguimientos que no están en la muestra: 7 con respuesta y 2 sin respuesta;
  - 15 sin respuesta en total: esas 2 y 13 más de las 56, elegidas con la semilla fija `20261101`;
- son 72 preguntas, 57 de ellas con respuesta. Con 15 sin respuesta, cada una vale 6,7 puntos, así que el umbral del 85 % tolera 2 fallos;
- se conservan el `id` de origen, `evidence`, `relevant_ref` y `expected_answer`, en español. La lectura de citas funciona igual;
- la traducción la hace el asistente y se revisa a mano. Los términos legales se traducen por su sentido, sin calcar las siglas.

**Corpus en inglés** (`offline/corpus/en/`):
- 3 libros de en.wikibooks con `revid` fijo, bajo CC BY-SA 4.0, la misma licencia que los 9 libros de es.wikibooks ya aceptados (`corpus.LICENSES`). Se eligen en la implementación con dos criterios: temas distintos y un tamaño parecido al de los libros españoles;
- `corpus.py` gana el parámetro del sitio (hoy `es.wikibooks.org` está fijo) y un manifiesto aparte (`corpus/en/manifest.json`), con su `SOURCES.md` generado;
- el índice `build/index/en.db` usa los parámetros de troceado de `rag_parameters.json` (1594, 2113 y 4,554), como la app, y no recalcula la proporción del tokenizador. Si la recalculara, cambiaría la caché de `chars_per_token.json`.

**Preguntas sobre los documentos en inglés:**
- se redactan en inglés, con el método de F05 (fragmentos de muestra, evidencia literal y datos clave):
  - 40 con respuesta;
  - 10 seguimientos;
  - 15 sin respuesta.
- Fichero: `testset/crosslang/en-docs.en.jsonl`.
- La traducción al español de esas mismas preguntas va en `testset/crosslang/en-docs.es.jsonl`, con la misma evidencia en inglés.
- `testset.validate()` se llama con mínimos más bajos, porque ya admite los mínimos como parámetros.

## R9. Medida entre idiomas (`crosslang-review`)

**Decisión:** el comando `run_eval.py crosslang-review` genera las combinaciones y escribe `reports/crosslang-review.csv`. Todas las respuestas se juzgan a mano, porque no hay juicios heredados.

| Combinación | Documentos | Preguntas | Idioma de las respuestas | Para qué |
|---|---|---|---|---|
| ES·ES | español (`ref`) | español | Español | Referencia: la ejecución de `v4`, ya juzgada |
| ES·ES auto | español | español | Automático | R7 |
| ES·EN | español | inglés | Automático | Evaluación de «Automático» (FR-024) y medida entre idiomas (FR-033) |
| EN·EN | inglés (`en`) | inglés | Automático | FR-033 |
| EN·ES | inglés | español | Español | FR-033 |

- **Si «Automático» no se adopta**, las combinaciones con preguntas en inglés se repiten con «Español», que es lo que vería el usuario. Antes se para y decide el usuario (FR-024).
- **Esfuerzo:** 72 + 65 + 65 = 202 respuestas a juzgar, además de las unas 220 de R7.
- **Columnas del CSV:** `combinacion, id, id_origen, tipo, pregunta, respuesta_esperada, datos_clave, fuentes, respuesta, regla, regla_citas, manual, manual_citas, comentario`.
- **Cifras:** las mismas de `report.manual_metrics` por combinación: correctas, negativas, citas y negativas indebidas. También el acierto de la búsqueda (bloques relevantes en el contexto), para separar los fallos de búsqueda de los de generación.
- **Regla de la tarjeta** (FR-032, fijada antes de medir; D-060):
  - si ES·EN, EN·EN y EN·ES cumplen los umbrales del informe (negativas ≥ 85 % y citas ≥ 90 %) y, además, las respuestas correctas de ES·EN no quedan más de 10 puntos por debajo de sus preguntas de origen con `v4`, ni las de EN·ES más de 10 por debajo de EN·EN, se queda el texto del PNG;
  - si no, se propone un texto con las cifras, y se para para que el usuario lo apruebe (S10).
- **Registro:** sección «Idiomas (F12)» en `reports/eval-report.md`, y una decisión nueva en DECISIONS que cierra el pendiente «Documentos en otro idioma». Las mejoras de la búsqueda que pidan las cifras, por ejemplo normalizar según el idioma del documento, van a F14.
- **Lo que se mide tal cual es:**
  - la regla de seguimiento de F10 tiene conectores en español; con preguntas en inglés solo actúa la regla de las 8 palabras;
  - la búsqueda por palabras usa palabras vacías y raíces del español también sobre el texto en inglés.

## R10. Borrar el historial

**Decisión:**
- **Contrato:** `ChatSession.deleteAll()` y `ChatSession.historyCleared: StateFlow<Long>`, un contador.
  - Bajo el `Mutex` de la sesión: detiene la respuesta en curso (`CANCELLED`) y espera a su trabajo; después `repository.deleteAll()`, deja `current` en `null` y suma 1 al contador.
  - Con el `Mutex`, ninguna pregunta puede empezar entre la parada y el borrado.
- **Repositorio:**
  - `conversationCount()`: las conversaciones con al menos un turno, para el aviso de S6;
  - `conversationExists(id)`.
- **`DefaultChatSession.ask`:** si el id que recibe ya no existe, crea una conversación nueva en lugar de perder la respuesta como «no guardada» (el problema de la exploración).
- **`ChatViewModel`:** observa `historyCleared` a través de `ObserveHistoryClearedUseCase`, ignora el valor inicial y, cuando cambia, vuelve a un chat vacío (`setConversation(null)`) y cierra un diálogo abierto.
- **Fallo:** `ChatStorageException` (fichero ilegible C17 o sin espacio) da el aviso «No se pudo borrar el historial.». `DELETE FROM conversations` es una sola sentencia con cascada: o se borra todo, o nada (FR-014).
- **Ajustes:**
  - al tocar la fila, `CountConversationsUseCase`: si da 0, aviso; si no, el diálogo, con la frase extra si `session.active` tiene una respuesta en curso;
  - al confirmar, `ClearChatHistoryUseCase`, con el aviso «Historial borrado» y su anuncio.
- Los documentos, el índice y las preferencias no se tocan: `deleteAll` solo usa `chat.db`.

## R11. Modelos en Ajustes

**Decisión:**
- **Estado:** `ObserveModelOverviewUseCase` combina `ObserveModelSetupUseCase` (el mismo estado que la Preparación) con `ModelCatalog.required`. Al entrar en Ajustes llama a `RefreshLocalAiUseCase`, que refresca sin calcular huellas (D-047).
- **Chip (S1):**
  - `Ready` muestra «Local» (`StatusKind.Local`), salvo Gemma en CPU, que muestra «Modo CPU, más lento» (`StatusKind.Warning`, D-027; solo Gemma, como en la Preparación);
  - en los demás estados, `chipText()` y `statusKind()` de la Preparación.
- **Ficha (S2):** `ModelSheet` con:
  - el nombre y el propósito;
  - `fileName`;
  - `bytesText(sizeBytes)`;
  - `sha256.take(12) + "…"`; TalkBack lo lee como «SHA-256, empieza por …»;
  - las dimensiones: `RagParameters.DIMS`, solo en `EG2_270M`;
  - el procesador: `Ready.backend`, o «—» si no está listo;
  - el estado: chip más `detailText()`, que incluye el motivo de un fallo.
- **El hash** es el publicado en el catálogo, el mismo con el que se verificó el fichero (D-011). Si no está verificado, el estado lo dice (FR-007).
- **«Completar configuración»** solo aparece si el modelo no está listo, y navega a `Setup` (D-046).
- Ajustes no tiene acciones propias sobre los modelos (D-058).

## R12. Pantallas y navegación

**Decisión:**
- **Ajustes:** `composable<Settings>` muestra `SettingsRoute`.
  - Atrás va a Inicio, igual que las otras pestañas (G08).
  - «Idiomas» abre `navigate(Languages) { launchSingleTop = true }`.
  - «Índice y rendimiento» abre `navigate(Index) { launchSingleTop = true }`.
  - «Completar configuración» abre `navigate(Setup) { launchSingleTop = true }`.
- **Idiomas:** `composable<Languages>` muestra `LanguagesRoute(onClose = back)`. Desde Inicio sigue como hoy, encima de Inicio.
- **En la pantalla de Ajustes:** la ficha es una `LisdataBottomSheet` y el borrado un `LisdataConfirmationDialog`. Su estado (qué ficha está abierta y si se está confirmando) se guarda en `SavedStateHandle`, como el diálogo del chat.
- **Avisos:** `SnackbarHost` propio de la pantalla, como la Biblioteca, y además anuncio de TalkBack.
- **Pruebas que cambian:** `SystemBackTest` (L36) y `NavigationTest` dejan de esperar el marcador «F12». Se retira el uso de `PendingDestination` para estas rutas, y la ruta de depuración `"settings"`/`"languages"` sigue funcionando.

## R13. Textos y revisión del inglés

**Decisión:**
- **Textos nuevos:** `res/values/strings_settings.xml` y `res/values-en/strings_settings.xml`, con los textos del PNG y de S1–S11.
- **Nombres de los idiomas:** «Español» y «English» se marcan `translatable="false"`, porque van siempre en su propio idioma.
- **Paridad:** `StringsParityTest` (JVM) lee todos los `strings*.xml` de `values` y `values-en` y comprueba:
  - los mismos nombres de `string` y `plurals` traducibles;
  - los mismos marcadores de formato en cada par.
- **Revisión del inglés:**
  - el asistente revisa los textos de `values-en`, unos 395 y 12 plurales más los nuevos;
  - las correcciones van en un commit propio y su lista en el informe de cierre (FR-029), para que el usuario las revise.

## R14. Accesibilidad

**Decisión:**
- **Grupos de opciones:** cada tarjeta de opciones lleva `Modifier.selectableGroup()`. `LisdataRadioRow` ya usa `selectable` con `Role.RadioButton`, así que TalkBack anuncia el grupo, el rol y «seleccionado».
- **Encabezados:** «INTELIGENCIA ARTIFICIAL», «PREFERENCIAS», «PRIVACIDAD Y DATOS», «IDIOMA DE LA APLICACIÓN» e «IDIOMA DE LAS RESPUESTAS» van con `LisdataSectionHeader`, que tiene semántica de encabezado.
- **Filas de modelo:** se leen como un único elemento: título, nombre, estado y acción («abrir detalles»).
- **«Procesamiento 100% local»:** sin acción y con la descripción de S5.
- **Avisos:** región en vivo educada para «Guardando», «Preferencias guardadas», el error al guardar e «Historial borrado».
- **Pruebas:**
  - ATF en el emulador con `enableAccessibilityChecks` y `tryPerformAccessibilityChecks`, como `ChatScreenAccessibilityTest`, en Ajustes, la ficha, Idiomas y el diálogo, en claro y en oscuro, con la letra al 200 % y en horizontal;
  - Robolectric para la semántica.

## R15. Pruebas y fases

**Decisión:**
- **Android 12 en la JVM:**
  - la ruta de API 31–32 se prueba con Robolectric con `@Config(sdk = [32])`; `android-all` 12.1 ya está en la caché local;
  - la ruta de API 33+ se prueba con `sdk = 36`, el valor de `robolectric.properties`.
- **Emuladores instalados:** `MicroRAG_API33` (API 33) y `Pixel_10` (API 37). No hay imagen de API 31–32, y no se descarga sin permiso del usuario.
- **Teléfono de referencia** (A063, API 35), en la **última fase**:
  - batería instrumentada;
  - recorrido de las dos pantallas y del cambio de idioma;
  - capturas frente a los PNG 09 y 10.
- **Privacidad en el teléfono:**
  - en el teléfono hay una conversación del usuario: **«Borrar historial» nunca se confirma en el teléfono sin su permiso expreso**, porque la borraría. El borrado se prueba en la JVM, en las instrumentadas (con bases temporales) y en el emulador;
  - se aplican las demás reglas de la fase del teléfono de F11: capturas recortadas, volcados en `/data/local/tmp` y ajustes restaurados.
- **Escritorio:** las pruebas Python nuevas van en `offline/tests/` (prompt, juicio, configuración, comandos y conjuntos).

## R16. Versiones

Verificadas el 2026-11-01 en Google Maven (`maven-metadata.xml` y cabeceras de los POM):

| Librería | En el proyecto | Última | Decisión |
|---|---|---|---|
| `androidx.datastore:datastore-preferences` | 1.2.1 | 1.3.0 (2026-10-21) | Se queda en 1.2.1: la misma API de `Preferences` y nada que F12 necesite. El aviso de lint ya lo explica (F03 R1) |
| `androidx.appcompat:appcompat` | — | 1.8.0 (2026-08-12) | No se añade (R2) |

El resto no cambia: Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, navigation 2.10.2, Koin 4.2.2 y LiteRT-LM 0.18.1. La subida de LiteRT-LM a 0.19.0 sigue siendo una decisión aparte.
