# Research: Chat con fuentes (F11)

Fecha: 2026-10-29. Todas las decisiones parten de:
- el código actual (F01–F10);
- la spec aclarada (D-053), con las propuestas de UI C1–C15 aprobadas;
- la referencia de escritorio de F05 (`offline/eval/`).

No hay dependencias nuevas.

**Orden de trabajo:** como en F10, las pruebas en el teléfono de referencia van en la última fase. El resto va antes:
- el código;
- las pruebas JVM;
- las pruebas instrumentadas en el emulador;
- el trabajo en el escritorio.

## R1. Dónde vive cada pieza

**Decisión:** cinco paquetes con una responsabilidad cada uno (constitución II).

| Paquete | Contenido | Por qué ahí |
|---|---|---|
| `rag/answer` | `PromptBuilder` (port de `prompt.py`) y `AnswerReading` (citas, normalización, negativa y orden de las fuentes citadas; port de `judge.py`) | Algoritmos puros, como `rag/retrieval`. Se prueban en la JVM contra los casos y el golden del escritorio (R2, R3) |
| `domain/chat` | Modelos (conversación, turno, fuente, disponibilidad), interfaces `ChatRepository`, `AnswerEngine` y `ChatSession`, y los casos de uso | El contrato que ve la pantalla. No importa LiteRT, SQLite ni `data` |
| `data/chat` | `SqliteChatRepository` (`chat.db`), `EngineAnswerEngine` (Gemma con el coordinador) y `DefaultChatSession` (orquestación en el ámbito de la app) | Usan `RagConnection`, `EngineCoordinator`, `EngineFactory`, `ModelFileRepository` y `RagIndex`, como `data/retrieval` |
| `core/designsystem` | Cambios en `LisdataAnswerCard` (contenido con viñetas y citas, notas y texto de fallo), `LisdataSourceCitation` (`onClickLabel`), chip de fuentes tocable y `LisdataOverflowMenu` | Un único sistema de componentes (constitución VII) |
| `feature/chat` | `ChatRoute`, `ChatScreen`, `ChatViewModel`, `ChatUiState`, `ChatActions`, `ChatPreviewData` y `AnswerFormatter` (texto bruto a contenido con viñetas y fichas renumeradas, y texto para copiar) | El patrón de F08 y F09. El formato es presentación: produce el modelo del design system |

**Alternativas descartadas:**
- **La orquestación en el `ChatViewModel`:** la respuesta tiene que seguir al cambiar de pestaña (FR-011) y no depender de la vida de la pantalla.
- **La orquestación en `domain`:** necesita el motor, el ámbito de la app y la base de datos.

## R2. Port exacto de la plantilla (`offline/eval/prompt.py`)

**Decisión:** port literal a `rag/answer/PromptBuilder`, con todas las plantillas del harness (`informe`, `v2` y las variantes de R4). La activa la fija `RagParameters.PROMPT_TEMPLATE`.

Detalles que el port respeta (verificados en `prompt.py`):

**Cabecera:**
- las reglas, unidas con `\n`;
- las reglas extra de la variante, detrás de «No uses conocimiento propio…»;
- una línea vacía.

**Turno anterior**, solo si existe:
- «CONVERSACIÓN PREVIA (solo como contexto de la pregunta):», `Usuario: ` + los primeros 300 **puntos de código** de la pregunta y `Asistente: ` + los primeros 500 de la respuesta, y una línea vacía;
- sin elipsis y sin `strip`;
- en Kotlin se corta por puntos de código (`codePointCount` y `offsetByCodePoints`), no con `String.take`, que cuenta unidades UTF-16.

**Fuentes:**
- `FUENTES:` y, por cada bloque, `[n] ` + `nombre › ruta` (U+203A con un espacio a cada lado), uniendo solo las partes que no son nulas;
- después ` (p. N)` si hay página, el texto con `strip()` de Python y una línea vacía;
- una ruta vacía o la página 0 se escriben, porque solo se omite el nulo.

**Final:**
- `"\n".join(líneas) + "\nPREGUNTA: " + pregunta.strip()`, sin salto de línea al final;
- sin bloques no hay línea en blanco antes de `PREGUNTA:`.

El `strip()` es el `PyText.strip` de F06, el mismo que usa `FollowUpRule`.

**Paridad** (SC-002):
- se portan los casos de `offline/tests/test_prompt.py`;
- además, un golden nuevo, `offline/golden/answer_golden.jsonl` (data-model §7), con las 426 preguntas vivas de F05:
  - entradas: pregunta, turno anterior y bloques con nombre, ruta, página y texto;
  - salida esperada: el prompt (longitud y SHA-256), con el texto entero en 10 filas para que una diferencia se pueda leer.

**Reconstruible sin generar nada** (verificado):
- bloques: `offline/build/runs/<retrieval de 06ef519f>/retrieval.jsonl`, que guarda el texto entero de cada bloque;
- respuestas anteriores: `06ef519f…/answers.jsonl`;
- preguntas: `offline/testset/questions.jsonl`.

Las 50 preguntas de seguimiento tienen su respuesta anterior.

**Alternativas descartadas:**
- **Guardar el prompt entero de las 426:** ≈ 3 MB; con la huella y 10 textos completos basta para detectar y leer una diferencia.
- **Recomponer los bloques en Kotlin desde los fragmentos:** dependería del índice de referencia; el golden lleva el texto ya fusionado.

## R3. Citas, negativa y texto en pantalla

**Decisión: port literal de `judge.py`** a `rag/answer/AnswerReading`.
- **`citations(text)`:** la regex `\[(\d+(?:\s*,\s*\d+)*)\]`; devuelve todos los números en orden, con repetidos.
- **`norm(text)`:**
  1. quita los separadores de miles (`(?<=\d)[.,\s](?=\d{3}\b)`);
  2. pasa a minúsculas;
  3. normaliza en NFD y quita los acentos (categoría `Mn`);
  4. cambia cada racha de `[^\w]+` por un espacio y colapsa los espacios.
- **`isRefusal(text)`:** `norm(text) == norm(REFUSAL)`. Una negativa con texto añadido no lo es (`test_judge.py` L37).
- **Semántica Unicode como en Python:** `\d`, `\w`, `\s` y `\b` Unicode (`Pattern.UNICODE_CHARACTER_CLASS`); minúsculas con `Locale.ROOT`; NFD con `java.text.Normalizer`; `Mn` con `Character.getType == NON_SPACING_MARK`.
- **Paridad** (SC-003):
  - se portan los casos de `offline/tests/test_judge.py`;
  - el mismo golden lleva, para las 426 respuestas de F05, las citas leídas, si es negativa y el `norm` esperado.

**Decisión: `AnswerReading.citedOrder(text, blockCount)`** da los números de bloque válidos en el orden de su primera cita, sin repetidos. La sesión lo usa para guardar las fuentes y su posición.

**Decisión: `AnswerFormatter`** (puro, `feature/chat`) convierte el texto bruto del modelo en lo que se muestra, con las citas de `AnswerReading`.
1. **Citas válidas:** las `[n]` con 1 ≤ n ≤ número de bloques del prompt. Las demás se quitan del texto (FR-021).
2. **Renumeración** (D-053):
   - la primera fuente citada es la 1, la segunda distinta la 2, y así;
   - una fuente citada varias veces conserva su número;
   - una marca `[1, 3]` da dos fichas.

   La lista de fuentes sigue ese orden (FR-019).
3. **Formato mínimo** (FR-022):
   - una línea que empieza por `- `, `* ` o `• ` es una viñeta;
   - `N. ` es una viñeta numerada, que conserva su número;
   - `**texto**` va en negrita;
   - una línea que empieza por `#` es un párrafo en negrita, sin las almohadillas;
   - se quitan las comillas invertidas;
   - el resto, tal cual.
4. **Durante el streaming** se formatea el texto recibido hasta ese momento. La renumeración es estable porque el orden de la primera cita solo crece.

El resultado es un modelo del design system (`AnswerContent`, data-model §6): clases de datos sin Compose, que el componente pinta. La sesión y el formateador comparten la misma lectura de citas, así que la lista guardada y las fichas coinciden siempre.

**Decisión: fichas de cita.**
- Son `LinkAnnotation.Clickable` dentro del texto, con el estilo de `LisdataCountChip`: `labelMedium`, `blue800` sobre `surfaceInfo`.
- El fondo redondeado se pinta con `drawBehind` a partir de `TextLayoutResult.getPathForRange`.
- **Accesibilidad:** cada enlace lleva la frase «Fuente N: documento, página P», y TalkBack los ofrece en su menú de enlaces.
- La regla de 48 dp no se aplica a los enlaces dentro de un texto (excepción «inline» de WCAG 2.5.8). La fila de la lista es la vía de 48 dp a la misma fuente.
- **Verificado:** Compose UI 1.12.1 (BOM 2026.09.00) trae `LinkAnnotation.Clickable` en `ui-text`.

**Alternativas descartadas:**
- **`InlineTextContent` con un chip:** cada ficha sería un nodo tocable de menos de 48 dp, y las pruebas de accesibilidad (ATF) lo marcarían. Además, su hueco en `em` se descuadra con la fuente al 200 %.
- **Fichas fuera del texto:** separan la cita de su afirmación (D-053).
- **Un parser de Markdown completo:** el modelo solo produce listas y negritas, y una librería sería una dependencia nueva.

## R4. Revisión de la plantilla (D-035, D-053)

**Hechos (F05):**
- **Generar las 426 respuestas en el escritorio** cuesta ≈ 324 s con E2B en la GPU (`offline/build/ablations.json`).
- **Regla automática:** coincide con el juicio manual en el 89,4 % de los casos (`eval-report.md` L146).
- **Fallos con el fragmento relevante en el contexto** (recalculados de `manual-judgement.csv`):
  - 11 negativas indebidas, 7 de ellas en preguntas de seguimiento;
  - 9 respuestas incorrectas, que confunden plazos o artículos vecinos;
  - 24 parciales, que omiten una parte de una respuesta con varias.
- **La variante `v2` ya se probó:** 0 puntos de mejora en respuestas correctas, y la negativa bajó al 91,1 %.

**Decisión: dos variantes nuevas.** Se fijan antes de ejecutar y no se ajustan con los resultados, para no sobreajustar a las 426 preguntas.

- **`v3` (completa y exacta):** la plantilla «informe» con dos reglas más tras «No uses conocimiento propio…»:
  ```
  - Si la PREGUNTA pide varias cosas, responde a todas las que estén en las FUENTES.
  - Copia cifras, plazos, importes y números de artículo tal como aparecen en la fuente que citas.
  ```
- **`v4` (v3 + seguimiento):** `v3` y una regla más:
  ```
  - Si hay CONVERSACIÓN PREVIA, la PREGUNTA la continúa: búscala en las FUENTES con ese contexto antes de responder con la frase anterior.
  ```

**Método** (comando `run_eval.py template-review`):
1. Genera las 426 respuestas de cada variante con la misma recuperación que la configuración elegida (`06ef519f`). Los seguimientos usan la respuesta anterior generada en la misma ejecución.
2. **Criba automática.** Una variante pasa a la revisión manual solo si, con la regla automática:
   - sube ≥ 1 punto las respuestas correctas;
   - mantiene negativas ≥ 85 %;
   - mantiene citas ≥ 90 %.
3. **Revisión manual (método D-033):** el asistente juzga solo las respuestas que cambian frente a «informe», con los mismos criterios del CSV de F05. Las que no cambian heredan su veredicto. Los veredictos van a `offline/reports/template-review.csv`.
4. **Adopción** (D-053):
   - respuestas correctas ≥ 80,4 % en el juicio manual (78,4 % + 2 puntos);
   - negativas ≥ 85 %;
   - citas ≥ 90 %.

   Si las dos variantes cumplen, gana la de más correctas, y a igualdad la más corta.
5. **Registro:** la sección «Revisión de la plantilla (D-035, F11)» del informe de evaluación y una decisión nueva.

**Si se adopta una variante:**
- `prompt_template` cambia en `rag_parameters.json` y en `RagParameters`;
- se regenera el golden de R2 con sus respuestas.

Va antes del port: el `PromptBuilder` porta todas las plantillas, pero el golden usa la activa.

**Alternativas descartadas:**
- **Más variantes o ajustar el texto con los resultados:** sobreajuste a las mismas preguntas.
- **Revisar a mano las 426 respuestas de cada variante:** la criba automática y las respuestas que no cambian bastan para el veredicto (método D-033).
- **Probar en el teléfono:** decidido en clarify (D-053).

## R5. Generación con Gemma (`EngineAnswerEngine`)

**Hechos verificados:**
- `EngineCoordinator.withEngine` no llama a `initialize()`; lo hace la fábrica: `factory.generator(...).initializeOrClose { it.initialize() }`, como `ModelCompatibilityChecker`.
- Cargar sin generar es un bloque vacío (`withEngine(key, create) { }`), que deja el motor cargado (`EngineCoordinatorTest` L96).
- El LLM es exclusivo y nunca se retiene.
- Cargar un motor de GPU espera a que nadie use otro de GPU y lo cierra.
- `LiteRtTextGenerator.generate`:
  - abre una conversación nueva por llamada;
  - si se cancela, llama a `cancelProcess()` y relanza;
  - si se cancela antes del primer token, no emite nada.

**Decisión:**
- **Clave:** `EngineKey(GEMMA4_E2B, preparedBackends[GEMMA4_E2B])`: GPU o CPU, la que preparó F03.
- **Configuración:** `GenerationConfig(maxNumTokens = RagParameters.LLM_MAX_NUM_TOKENS (4096, nuevo parámetro compartido), maxOutputTokens = RagParameters.MAX_OUTPUT_TOKENS (512), greedy = true)`. Es la del escritorio (`generate.py`: `max_num_tokens=4096`, muestreo voraz).
- **`ready()`:** el modelo está `Verified` y preparado, con un `refresh()` si `pathOf` es nulo, como `EngineQueryEmbedder.ready()`.
- **`warmUp()`:** `withEngine(key, create) { }`. Se llama al abrir el chat si está disponible (R7). Una precarga cancelada no deja el motor a medias: la inicialización nativa termina dentro del bloqueo de carga.
- **`generate(prompt)`:** `withEngine(key, create) { it.generate(prompt) }`, reemitiendo los `Token` y el `Finished`. El motor sigue cargado al terminar.
- **Cancelación:** se cancela la corrutina que recoge el flujo, sin `cancel()` directo. La lectura de `LiteRtTextGenerator` garantiza que para el motor y que el texto parcial queda donde lo guarde la sesión.
- **Fallos:**
  - una excepción del motor (incluida la GPU que falla durante la respuesta) es `FAILED` con «Reintentar»;
  - no se cambia de backend en silencio (FR-032);
  - una respuesta vacía también es `FAILED` (caso límite de la spec).

**Alternativas descartadas:**
- **Retener el LLM (`withHeldEngine`):** el coordinador lo prohíbe, y la constitución IV quiere liberarlo al ocultarse la UI.
- **Un motor por conversación:** gastaría ≈ 2,5 GB más.

## R6. Orquestación de una pregunta (`DefaultChatSession`)

**Decisión:** `DefaultChatSession` es un `single` de Koin que corre en el `ApplicationScope`. Solo hay una respuesta en curso en toda la app, porque solo cabe un LLM.

`ask(conversationId?, question)` sigue estos pasos:
1. **Comprueba** la disponibilidad (R11) y que la pregunta no esté vacía ni pase de 1.000 caracteres (R10). Si no se cumple, no hace nada; la pantalla nunca llega a pedirlo.
2. **Crea la conversación** si es la primera pregunta y devuelve su id enseguida. La pantalla lo guarda en su `SavedStateHandle`.
3. **Inserta el turno** con la pregunta, el estado `RETRIEVING` y la marca de cobertura parcial del momento (R11).
4. **Recuperación:** `RetrieveContextUseCase(question, previousQuestion)` según R10.
   - **`Blocks`:** sigue.
   - **`NoResults`:** turno `COMPLETED` como negativa, sin llamar al modelo (FR-008, SC-012).
   - **`IndexUnavailable`:** `FAILED` con la causa «índice» (C8).
   - **`ModelsNotReady`, `EmbeddingFailed` y `EmptyQuestion`:** `FAILED` con la causa «generación» (C7).
5. **Prompt:** `PromptBuilder` con los bloques, el turno anterior (R10) y la plantilla activa. Si pasa del límite estimado, se quitan bloques del final (R10).
6. **Generación:** pasa a `GENERATING`. El texto se acumula en memoria (`activeAnswer`, un `StateFlow`) y se guarda en `chat.db` como mucho una vez por segundo, y siempre al terminar.
7. **Cierre:**
   - `AnswerReading` decide si es negativa y qué números cita;
   - se guardan las fuentes citadas: bloque, documento, primer fragmento, rango, nombre, página y orden de primera cita;
   - el estado final es `COMPLETED`, `CANCELLED` (con su texto y sus fuentes) o `FAILED`.

**Otras acciones:**
- **`cancel()`:** cancela la tarea de la respuesta. El turno pasa a `CANCELLED` con lo recibido hasta entonces, y detener tarda menos de 1 s (SC-005).
- **`retry(turnId)`:** repite la pregunta del turno en el mismo turno, con el turno anterior a él.
- **Empezar otra conversación con una respuesta en curso** («Nuevo chat» en Inicio o en «⋯»): esa respuesta se cancela y queda «cancelada» con su texto parcial. Así nunca hay dos.
- **Borrar** la conversación de la respuesta en curso la cancela primero (FR-004).

**Registro:** una sola línea por respuesta, solo con cifras, y nada en release porque el registro está desactivado (FR-043). Lleva el resultado, si es seguimiento, los bloques del prompt, las fuentes citadas, si es negativa, los tokens estimados, el backend, los recortes y los tiempos:

```
chat answer outcome=Completed follow_up=false blocks=4 cited=2 refusal=false prompt_tokens_est=1834 backend=GPU dropped_blocks=0 retrieval_ms=… ttft_ms=… total_ms=… decode_tok_s=…
```

## R7. Convivencia con la indexación (D-053)

**Hechos verificados:**
- `ObserveIndexingUseCase` da `IndexingState.Active(…, pause)`. «En marcha» equivale a `Active` con `pause == null`; incluye la tanda encolada que aún no corre.
- La ingesta llama a `releaseGenerators()` al empezar cada sesión de embeddings (`ChunkEmbeddingSession` L60). Esa llamada **espera** a que termine el bloque del LLM en curso, sin cancelarlo (`closeLocked` con `NonCancellable` sobre el bloqueo de uso).

**Decisión:**
- **La puerta la ponen la sesión y la pantalla:** con la tanda en marcha, la disponibilidad es `WaitingForIndexing` (C14), `ask()` no hace nada y no hay precarga.
- **Si la tanda empieza o se reanuda durante una respuesta,** `releaseGenerators()` espera a que termine y después cierra Gemma (FR-030). La sesión no empieza otra porque la puerta ya está cerrada.
- **Al pausarse o terminar la tanda,** la disponibilidad cambia sola y la pantalla precarga Gemma (FR-030).
- **Carrera:** si el usuario envía justo cuando la tanda pasa a `Active`, la respuesta empieza. La ingesta espera a que termine porque `releaseGenerators()` no cancela; nunca hay dos motores en la GPU. Se prueba en la JVM con el coordinador real y motores falsos (SC-011).

**Alternativas descartadas:** pausar la tanda, o seguir en CPU (D-053).

## R8. Ciclo de vida: app oculta y muerte del proceso

**Hechos verificados:**
- `EngineMemoryCallbacks` llama a `releaseUnheld()` con `TRIM_MEMORY_UI_HIDDEN` o superior, y con `onLowMemory`.
- Esa llamada espera a que termine el bloque del LLM. Una respuesta en curso seguiría en segundo plano hasta 512 tokens, ≈ 58 s a 8,8 tokens/s.

**Decisión: la app oculta cancela la respuesta.**
- Nueva interfaz `MemoryTrimListener` en `rag/runtime`, que implementa la sesión de chat.
- `EngineMemoryCallbacks` avisa a sus listeners antes de liberar.
- La sesión cancela la respuesta en curso, que queda `CANCELLED` con su texto parcial (caso límite de la spec), y después se libera Gemma (constitución IV).
- Al volver a la app no se reanuda nada. «Reintentar» no aplica a una cancelada; el usuario puede volver a preguntar.

**Decisión: muerte del proceso.**
- Al crearse, la sesión marca como `INTERRUPTED` los turnos que quedaron en `RETRIEVING` o `GENERATING` (C13, FR-028, SC-007), en una sola sentencia sobre `chat.db`. Un proceso nuevo no tiene ninguna respuesta en curso.
- El texto parcial es el último guardado, perdiendo como mucho ≈ 1 s (R6).

**Alternativas descartadas:**
- **Dejar terminar la respuesta en segundo plano:** retiene la GPU y la batería sin que nadie la vea, y el sistema puede congelar el proceso a mitad.
- **Un servicio en primer plano para terminarla:** desproporcionado para una respuesta de segundos.

## R9. Persistencia (`chat.db`)

**Decisión:** un fichero SQLite propio, `noBackupFilesDir/chat/chat.db` (nuevo `AppDirs.chat`), con la misma conexión empaquetada que el índice.

- **`RagConnection.open(dbFile, extensionPath: String?)`:** con `null` no carga SQLite-Vector. Mantiene los mismos `PRAGMA` (`synchronous = NORMAL`, `temp_store = MEMORY`, `foreign_keys = ON`), WAL y `transaction`.
- `data/chat` usa `RagConnection` sin importar `androidx.sqlite`, así que `LayeringTest` no cambia.
- **Hilo:** `AppDispatchers.database`, el mismo hilo de un solo hilo que el índice (constitución IV). Durante una tanda no hay respuestas (R7), así que el chat no compite con sus escrituras.
- **Esquema v1** (data-model §3):
  - tablas `conversations`, `turns`, `sources` y `chat_meta` (`schema_version`);
  - borrado en cascada de los turnos y de sus fuentes.
- **Un turno es una fila** con la pregunta y la respuesta. La spec habla de mensajes de usuario y de asistente; un turno da los dos, con su orden. La respuesta se guarda en bruto, con sus `[n]` del prompt; la renumeración se calcula al mostrarla (R3).
- **Migraciones:** el mismo patrón que `RagMigrations` (lista de pasos `from → statements`), hoy vacía.
- **Cambio de interfaz:** `ChatRepository` expone flujos, porque la pantalla observa la conversación. Cada escritura confirmada sube un contador de cambios, como `documentChanges` del índice.
- **Para F12 y F13:**
  - `conversations` lleva `created_at` y `updated_at`;
  - el título se deriva de la primera pregunta (F13);
  - `deleteAll()` queda en la interfaz para F12, sin UI en F11.

**Alternativas descartadas:**
- **Tablas en `rag.db` (esquema v4):** un índice incompatible o reconstruido (F04 A2) bloquearía o borraría las conversaciones, y cada escritura del chat movería `documentChanges`.
- **Room:** dependencia nueva, y el proyecto ya tiene su capa SQLite.
- **DataStore o JSON:** no sirven para consultar y paginar el historial de F13.

## R10. Turno anterior, seguimiento y límite del prompt

**Decisión: turno anterior (C12)**, sobre el último turno de la conversación antes del que se pregunta.
- **Completado y no negativa:**
  - la recuperación recibe su pregunta;
  - el prompt recibe su pregunta y su respuesta en bruto, como el escritorio, que pasa la respuesta generada.
- **Completado y negativa:** la recuperación recibe su pregunta y el prompt no lleva turno anterior.
- **Cancelado, fallido o interrumpido:** sin turno anterior.

**Decisión: límite del prompt** (FR-015).
- **Estimación:** tokens estimados = ⌈caracteres / `CHARS_PER_TOKEN`⌉ (4,554), la misma estimación que el presupuesto de bloques.
- **Si pasa de `PROMPT_LIMIT_TOKENS`** (2.000), se quita el último bloque y se reconstruye, hasta caber o quedarse sin bloques. Si no queda ninguno, se responde con la negativa (FR-008).
- **Frecuencia:** el presupuesto de 1.719 tokens de D-034 ya deja sitio a la cabecera, 4 cabeceras de bloque y una pregunta de ≈ 40 tokens (`pipeline.budget_tokens`). Solo se recorta con preguntas largas o turnos anteriores largos. Los recortes se registran (`dropped_blocks`).

**Decisión: pregunta de 1.000 caracteres como máximo** (constante `ChatLimits.MAX_QUESTION_CHARS`).
- Son ≈ 220 tokens.
- El compositor no admite más: un texto pegado se corta en ese límite.
- El embedding de la consulta ya recorta su propia entrada larga (F10, regla de D-036).

## R11. Disponibilidad del chat

**Decisión:** `ObserveChatAvailabilityUseCase` combina tres fuentes y da la primera situación que aplica, en este orden.

**Fuentes:**
- los modelos: `ObserveLocalAiUseCase` y el backend preparado de Gemma;
- la tanda: `ObserveIndexingUseCase`;
- la biblioteca: una consulta nueva de `RagIndex`.

**Situaciones:**
1. `ModelsNotReady`: falta un modelo, no está verificado o no está preparado (C5).
2. `IndexUnavailable`: el índice no se puede abrir (C8, con «Reintentar»).
3. `EmptyLibrary`: no hay documentos (C3).
4. `WaitingForIndexing`: hay una tanda en marcha (C14).
5. `NothingReady`: hay documentos, pero ninguno indexado e incluido (C4).
6. `Ready(cpuMode, partialCoverage)`:
   - `cpuMode` si Gemma está preparada en CPU (C9);
   - `partialCoverage` si hay documentos incluidos que no están en `done` ni en error (C6).

**Consulta nueva:** `RagIndex.chatLibraryState()`, en una sola consulta agregada sobre `documents`, sin documentos sombra. Devuelve:
- `documents`: el total;
- `searchable`: los `done` e incluidos;
- `pendingIncluded`: incluidos, sin sombra, en `pending`, `parsed`, `embedding` o `paused`. No cuentan `error` ni `cancelled` («Fuera del índice», D-043), porque los dejó así un fallo o el usuario.

Se observa con `documentChanges`, como `LibraryRepository.summary()`.

**Detalles:**
- **Presentación:** con turnos en la conversación, las situaciones 1–5 se muestran como un aviso sobre el compositor desactivado y la conversación sigue visible. Sin turnos, ocupan la pantalla como estado vacío (contrato de UI).
- **Precarga:** solo en `Ready`. Se repite al volver a la pantalla (`ON_START`), porque Gemma se libera al ocultarse la app.
- **Error de los documentos:** no cuenta como cobertura parcial. El usuario ya lo ve en la Biblioteca, y la respuesta no lo puede arreglar.

## R12. Fuentes y su disponibilidad

**Decisión: cada fuente guarda los datos de su bloque al responder:**
- `document_id`;
- `chunk_id` del **primer** fragmento del bloque (D-043: las citas apuntan a fragmentos enteros);
- `first_ordinal` y `last_ordinal`;
- nombre del documento;
- página.

**Fila:** «Página P · Fragmento M», con M = `first_ordinal + 1`. Si el bloque tiene varios fragmentos, «Página P · Fragmentos M–N» (C11).

**Disponibilidad al mostrar** (`ResolveSourcesUseCase`, sobre `RagIndex`, observando `documentChanges`):
- **Documento borrado:** «Fuente ya no disponible», fila inerte (FR-027).
- **El documento existe pero el fragmento no** (reindexado o fuera del índice): la fila abre el documento con ese `chunkId`. F08 ya muestra el primer fragmento con su aviso «El fragmento citado ya no está en este documento.» (D7).
- **Índice no disponible:** las filas se muestran como disponibles; el detalle gestiona su propio error.

El documento excluido de la búsqueda sigue abriéndose: excluir no borra (constitución V).

## R13. Navegación, desplazamiento y el detalle

**Decisión: la ruta distingue «Nuevo chat» de la pestaña.**
- `Chat(conversationId: Long? = null, newConversation: Boolean = false)`.
- `openNewChat()` navega a `Chat(newConversation = true)` y la pestaña sigue en `Chat()`.
- **El ViewModel resuelve:**
  - con un id, abre esa conversación;
  - con `newConversation`, una vacía;
  - si no, la última (la de la pregunta más reciente) o una vacía.

  La conversación activa se guarda en su `SavedStateHandle`, y sobrevive a la recreación y a la muerte del proceso.
- **«Nuevo chat» desde «⋯»:** lo resuelve el ViewModel, sin navegar. Volver a navegar al mismo destino reutilizaría la entrada sin refrescar sus argumentos.
- Se actualizan `DebugRouting` y la KDoc de `Chat`. La prueba existente `newChat_opensTheChatTabOnANewConversation_notTheLastOne` pasa a comprobar `newConversation = true`.

**Decisión: desplazamiento.**
- La lista es un `LazyColumn` con su `rememberLazyListState` en la ruta. Como la entrada del chat queda bajo la del documento, al volver conserva su posición (SC-006).
- Durante el streaming, la lista sigue el final si el usuario estaba al final, y no se mueve si se había desplazado hacia arriba.

**Decisión: el detalle se desplaza al fragmento citado** (FR-005). Es el único cambio en F08.
- `PreviewCard` pone un `BringIntoViewRequester` en el texto resaltado.
- Un `LaunchedEffect` lo pide en cuanto la vista previa resaltada está cargada.
- Una marca `rememberSaveable` evita repetirlo tras rotar o volver.

**Decisión: mensaje del detalle al volver.** `leave()` del detalle escribe `LIBRARY_MESSAGE` en la entrada anterior.
- Si es el chat, este la lee y la borra.
- La cita a un documento borrado ya se ve como «Fuente ya no disponible», así que el chat no muestra otro aviso.

**Decisión: toques una sola vez.** Abrir una fuente se protege con la comprobación `RESUMED` del `MicroRagNavHost`, como en Inicio. Un doble toque abre un solo detalle.

## R14. Copiar

**Decisión:**
- **API:** `LocalClipboard.current.setClipEntry(ClipEntry(ClipData.newPlainText(label, texto)))` desde una corrutina. `LocalClipboardManager` está obsoleto; se verificó que Compose UI 1.12.1 exporta `LocalClipboard` y `ClipEntry`.
- **Contenido (C10):** el texto mostrado, con las fichas como `[1]`, `[2]`, una línea vacía y la lista de fuentes («[1] Manual de procesos.pdf, página 12»).
- **Confirmación:**
  - en Android 12 (API 31–32), el pie de la tarjeta muestra «Respuesta copiada» durante 2 s y se anuncia con TalkBack;
  - desde Android 13 lo confirma el sistema.

## R15. Medidas en el teléfono

**Decisión: `ChatBenchmarkTest`** (`androidTest`, `@RequiresModels`), sobre la sesión real sin pantalla.
- **Índice:** el corpus de evaluación en un índice temporal, con la infraestructura de `RetrievalParityTest` de F10. Nunca toca la biblioteca del usuario.
- **Preguntas:** las 50 de `retrieval_parity.jsonl`, en orden, con los seguimientos usando la respuesta generada en el teléfono.
- **Primer token:** desde `ask()` hasta el primer `Token`. Incluye la recuperación, el prompt y el prefill. Gemma está precargada, y la primera pregunta se mide aparte con Gemma sin cargar.
- **Registro** (solo cifras):
  - mediana y p95 del primer token;
  - tokens estimados del prompt;
  - velocidad de generación;
  - tiempo total;
  - pico de memoria, con `MemoryProbe` de F02 (`VmHWM` de `/proc/self/status`);
  - negativas y respuestas sin cita válida.
- **Umbral:** en el A063 se comprueba p95 ≤ 5.000 ms (SC-004). Si falla, se para y se consulta: FR-045 pide un ajuste de bloques o tokens con una decisión.
- **En el emulador** solo se comprueba que funciona, con 3 preguntas, en CPU y sin umbrales, si Gemma está preparada allí; si no, se salta.

**Recorrido en el teléfono:** su biblioteca tiene un documento del usuario, y una respuesta podría mostrarlo en pantalla. Antes de recorrer el chat en el teléfono se consulta al usuario cómo hacerlo, por ejemplo con un documento de prueba en una carpeta propia que se borra al terminar. Se aplican las reglas de privacidad del teléfono personal: capturas sin la barra de estado, registros filtrados a la app y nada de su documento.

## R16. Privacidad y registros

- **Sin red** (FR-042). `NetworkConfinementTest` sigue vigente; el chat no añade ningún permiso ni cliente.
- **Registros:**
  - la sesión registra solo la línea de R6;
  - ni la pantalla ni el repositorio registran texto;
  - una prueba JVM ejecuta preguntas y respuestas con textos marcados y comprueba que no aparecen en el registro (SC-010);
  - en release, `DefaultAppLogger(isEnabled = false)` no evalúa ni los mensajes.
- **`chat.db`** vive en `noBackupFilesDir`, y `allowBackup="false"` sigue vigente.
- **El portapapeles** solo recibe lo que el usuario copia.

## R17. Versiones (constitución II)

Sin dependencias nuevas. Versiones verificadas en `gradle/libs.versions.toml` y en la caché de Gradle:

| Pieza | Versión |
|---|---|
| Compose BOM | 2026.09.00, con UI y foundation 1.12.1 y Material 3 1.4.0 (POM del BOM) |
| Navigation | 2.10.2 |
| Lifecycle | 2.11.0 |
| Coroutines | 1.11.0 |
| Koin BOM | 4.2.2 |
| LiteRT-LM | `litertlm-android` 0.18.1 |
| SQLite | `androidx.sqlite:sqlite-bundled` 2.7.1 y `ai.sqlite:vector` 1.1.2 |
| Escritorio | `litert-lm-api` 0.18.1 |

**APIs nuevas en el proyecto**, comprobadas en los AAR 1.12.1:
- `LocalClipboard` y `ClipEntry` (`ui`);
- `LinkAnnotation.Clickable` (`ui-text`);
- `BringIntoViewRequester` (`foundation`).
