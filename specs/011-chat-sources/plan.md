# Implementation Plan: Chat con fuentes (F11)

**Branch**: `011-chat-sources` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-chat-sources/spec.md`

## Summary

F11 sustituye el marcador de la pestaña Chat por el chat con fuentes del PNG 04. La pregunta pasa por la recuperación de F10. El prompt es el del escritorio y Gemma 4 E2B responde en streaming con citas que siempre apuntan a fragmentos reales.

- **Algoritmos** (`rag/answer`): port exacto de la plantilla (`prompt.py`) y de la lectura de citas y negativa (`judge.py`). La paridad se comprueba en la JVM con los casos de Python y con un golden de las 426 preguntas de F05.
- **Orquestación** (`data/chat`): `DefaultChatSession` en el ámbito de la app, con una sola respuesta en curso. Pasos:
  1. comprueba la disponibilidad;
  2. recupera;
  3. construye el prompt dentro del límite;
  4. genera con `EngineAnswerEngine` (Gemma con el coordinador);
  5. guarda el texto cada segundo y al terminar;
  6. lee las citas y la negativa, y cierra el turno.
- **Persistencia:** `chat.db` propio en `noBackupFilesDir/chat`, con conversaciones, turnos y fuentes. Al arrancar marca como interrumpidas las respuestas que quedaron a medias.
- **Convivencia** (D-053): con una tanda de indexación en marcha, el chat espera y no carga Gemma. Si la tanda empieza durante una respuesta, la ingesta espera a que termine. Con la app oculta, la respuesta queda interrumpida, con «Reintentar», y se libera Gemma (analyze).
- **Pantalla** (`feature/chat`): fiel al PNG, con fichas de cita tocables dentro del texto, viñetas, «Fuentes consultadas», «⋯», copiar y detener. Incluye los estados C1–C17. El detalle de F08 se desplaza al fragmento citado.
- **D-035:** el harness de escritorio evalúa dos variantes de la plantilla (`v3` y `v4`). Se adopta una solo si sube 2 puntos las respuestas correctas sin bajar los umbrales de negativa y citas.
- **Medida:** primer token en el teléfono de referencia con el corpus de evaluación en un índice temporal. **Las pruebas en el teléfono van en la última fase.**

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios). Python 3.12 en `offline/`.

**Primary Dependencies**: ninguna nueva. Versiones verificadas en `gradle/libs.versions.toml` y en la caché de Gradle (research R17):
- Compose BOM 2026.09.00, con UI y foundation 1.12.1 y Material 3 1.4.0. Se usan por primera vez `LocalClipboard`, `ClipEntry`, `LinkAnnotation.Clickable` y `BringIntoViewRequester`, comprobados en los AAR;
- Navigation 2.10.2, Lifecycle 2.11.0, coroutines 1.11.0 y Koin 4.2.2;
- `litertlm-android` 0.18.1 (Gemma con `TextGenerator`);
- `androidx.sqlite:sqlite-bundled` 2.7.1 y `ai.sqlite:vector` 1.1.2;
- en el escritorio, `litert-lm-api` 0.18.1.

**Storage**:
- `chat.db` nuevo (esquema v1, data-model §3);
- `rag.db` sin cambio de esquema (v3), solo con una consulta agregada nueva;
- un golden nuevo, `offline/golden/answer_golden.jsonl`;
- un parámetro compartido nuevo, `llm_max_num_tokens`.

**Testing**:
- **JVM:**
  - algoritmos con golden: `PromptBuilderTest`, `AnswerReadingTest` y `AnswerGoldenTest`;
  - sesión y motor: `DefaultChatSessionTest`, `ChatDuringIndexingTest` y `EngineAnswerEngineTest`;
  - casos de uso: disponibilidad y fuentes;
  - pantalla: ViewModel, formateador y pantalla con Robolectric;
  - ampliaciones: `NavigationTest`, `DocumentScreenTest`, `EngineMemoryCallbacksTest`, `RagParametersTest`, `AppModulesTest` y `LayeringTest`.
- **Python:**
  - prompt con `v3` y `v4`;
  - huella de las ejecuciones;
  - sección del informe;
  - forma del golden.
- **Instrumentadas en el emulador:**
  - `SqliteChatRepositoryTest` y `RagIndexChatStateTest`;
  - `ChatScreenAccessibilityTest`;
  - las de F01–F10.
- **Con modelos:** `ChatBenchmarkTest`. En el emulador solo si Gemma está preparada, en CPU y sin umbrales. En el teléfono de referencia, en la última fase.

**Target Platform**: Android, minSdk 31 y targetSdk 37.
- Teléfono de referencia A063 (API 35), en la última fase.
- Emulador `Pixel_10` (API 37).

**Project Type**: mobile-app, módulo único `:app` (D-009). Herramienta de escritorio en `offline/`.

**Performance Goals**:
- **SC-004:** p95 del primer token ≤ 5 s de extremo a extremo, con Gemma cargada y los bloques de D-034.
- **Estimación:**
  - F02 midió 4,53 s en el motor y 4,75 s de reloj con 1.986 tokens (D-026), es decir, ≈ 450 tokens/s de prefill;
  - la configuración elegida da prompts de 1.267 tokens de mediana y 1.630 en el p95 (F05, tokenizador real);
  - con la recuperación (≈ 0,2 s, D-052), el p95 queda en ≈ 4,2 s y la mediana en ≈ 3,4 s;
  - el peor caso, un prompt de ≈ 2.000 tokens, ronda los 5 s.
- **Detener:** en menos de 1 s (SC-005).
- **Sin bloques:** negativa en menos de 1 s (SC-012).

**Constraints**:
- **Sin red** (I).
- **Gemma** con el backend que preparó F03, en el hilo `llm`; `chat.db` en `database`.
- **Nunca dos motores en la GPU.** Gemma se libera durante la ingesta y al ocultarse la app (IV).
- **Registros** solo con cifras, y nada en release (I).
- **Capas:** `feature/chat` no importa `data`, SQLite, WorkManager, LiteRT ni `File` (II).
- **UI:** el PNG manda; C1–C17 aprobadas; solo tokens y textos de recursos en español e inglés (VII).

**Scale/Scope**:
- 2 algoritmos puros portados, con su golden;
- 1 sesión, 1 motor, 1 repositorio y 10 casos de uso;
- 1 base de datos nueva y 1 consulta agregada en el índice;
- 1 pantalla con 7 disponibilidades y 9 estados de respuesta;
- 5 cambios en el design system;
- 2 cambios en el detalle de F08 (desplazamiento y menú compartido);
- 1 comando, 2 variantes y 1 golden en el harness.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F11 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos (`NetworkConfinementTest`). `chat.db` en `noBackupFilesDir` con `allowBackup=false`. Registros solo con cifras, comprobados con textos marcados (SC-010), y nada en release. Las pruebas usan índices temporales y el corpus público. El recorrido en el teléfono se acuerda antes con el usuario (R15) | ✅ |
| II. Stack y arquitectura | `ChatScreen → ChatViewModel → casos de uso → ChatSession/ChatRepository/AnswerEngine → data/chat`. LiteRT solo en `rag/runtime/litert`; `androidx.sqlite` solo en `data/rag` (`data/chat` usa `RagConnection`). Regla nueva para `feature/chat` (U1). Sin dependencias nuevas; versiones verificadas y registradas | ✅ |
| III. RAG según el informe | Fuentes numeradas, citas `[n]` y negativa explícita con la plantilla del informe (D-034), o con la variante que adopte la evaluación documentada (R4). Toda cita mostrada es un bloque real del contexto, con su `documentId` y su `chunkId`. Las citas fuera de rango se descartan (SC-001) | ✅ |
| IV. Rendimiento | Inferencia y base de datos fuera del hilo principal, en sus hilos dedicados. `chat.db` tiene una sola conexión y usa el mismo hilo que el índice, así que nunca hay dos escrituras a la vez (ver Complexity Tracking). Nunca dos motores en la GPU, con la puerta de la tanda para preguntar, precargar y reintentar (R7, A2) y el coordinador. Gemma se libera durante la ingesta y al ocultarse la app (R8). Primer token medido (SC-004) | ✅ |
| V. Veracidad del estado | La disponibilidad sale del estado real: modelos verificados, tanda y biblioteca. Nada se promete antes de comprobarlo. Las fuentes se resuelven contra el índice. No hay textos ni cifras de ejemplo fuera de previews y pruebas | ✅ |
| VI. Calidad y pruebas | Pruebas unitarias del constructor de prompt, obligatorias, con paridad con Python (casos y golden). ViewModel con fakes. Instrumentadas de `chat.db` y de la consulta agregada. Medida en el teléfono con dispositivo y versiones | ✅ |
| VII. UI fiel y accesible | PNG 04, con «MicroRAG» (D-014) y las propuestas C1–C17 y sus textos aprobados (clarify y analyze). Componentes `Lisdata*` y tokens. Accesibilidad: TalkBack, 48 dp, horizontal y fuente al 200 % (U2–U6). Diferencias con el PNG en el informe de cierre | ✅ |
| VIII. Proceso | Documentación en español y código en inglés. D-053 (clarify), D-054 (plan) y D-055 (analyze). Parada tras analyze. Pruebas en el teléfono al final | ✅ |

**Re-check post-diseño:** sin violaciones.
- La puerta de la tanda (R7) y la interrupción al ocultarse la app (R8) aplican la constitución IV tal como está; D-053 descartó enmendarla.
- Las fichas de cita usan la excepción «inline» de los objetivos táctiles (R3). La fila de la lista es la vía de 48 dp a cada fuente, y las pruebas ATF lo comprueban.

## Project Structure

### Documentation (this feature)

```text
specs/011-chat-sources/
├── spec.md
├── plan.md                        # este fichero
├── research.md                    # R1–R17
├── data-model.md                  # parámetros, dominio, chat.db, estado de la pantalla, contenido, golden
├── quickstart.md
├── contracts/
│   ├── chat-api.md                # algoritmos, dominio, sesión (A1–A15), motor, repositorio, cambios
│   ├── chat-ui.md                 # pantalla, estados, design system, textos, accesibilidad (U1–U12)
│   └── eval-answer.md             # golden y revisión de la plantilla (G1–G12)
├── checklists/requirements.md
└── tasks.md                       # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── rag/answer/
│   ├── PromptBuilder.kt                        # R2
│   └── AnswerReading.kt                        # R3
├── rag/runtime/
│   ├── MemoryTrimListener.kt                   # R8
│   └── EngineMemoryCallbacks.kt                # avisa a los listeners antes de liberar
├── domain/chat/
│   ├── ChatModels.kt                           # data-model §2
│   ├── ChatRepository.kt, AnswerEngine.kt, ChatSession.kt
│   └── ChatUseCases.kt                         # los 10 casos de uso
├── domain/rag/{RagIndex, RagModels, RagParameters}.kt   # chatLibraryState, ChatLibraryState, LLM_MAX_NUM_TOKENS
├── MicroRagApplication.kt                      # registra los MemoryTrimListener de Koin
├── domain/di/DomainModule.kt
├── data/rag/{RagConnection, DefaultRagIndex}.kt          # open sin extensión; consulta agregada
├── data/models/AppDirs.kt                      # chat/
├── data/chat/
│   ├── ChatSchema.kt                           # carga de chat_schema.sql y migraciones
│   ├── SqliteChatRepository.kt                 # R9
│   ├── EngineAnswerEngine.kt                   # R5
│   └── DefaultChatSession.kt                   # R6–R8, R10
├── data/di/DataModule.kt
├── core/designsystem/
│   ├── model/AnswerContent.kt                  # data-model §6
│   └── component/{Chat, Rows, Chips, Menus, ChatPreviews}.kt   # tarjeta, fila, chip, LisdataOverflowMenu
├── core/navigation/{Destinations, NavigationActions, MicroRagNavHost}.kt   # ruta y destino del chat
├── feature/chat/
│   ├── ChatRoute.kt, ChatScreen.kt (con ChatActions), ChatViewModel.kt, ChatUiState.kt
│   ├── AnswerFormatter.kt                      # R3
│   └── ChatPreviewData.kt
├── feature/document/DocumentScreen.kt          # desplazar al fragmento citado; menú con LisdataOverflowMenu
└── feature/di/FeatureModule.kt
app/src/main/assets/chat_schema.sql
app/src/main/res/values{,-en}/strings_chat.xml  # + citation_fragments
app/src/debug/java/com/lisdatasolutions/microrag/debug/DebugRouting.kt   # newConversation

app/src/test/java/com/lisdatasolutions/microrag/
├── rag/answer/{PromptBuilderTest, AnswerReadingTest, AnswerGoldenTest}.kt
├── data/chat/{DefaultChatSessionTest, ChatDuringIndexingTest, EngineAnswerEngineTest}.kt
├── domain/chat/{ObserveChatAvailabilityUseCaseTest, ResolveSourcesUseCaseTest}.kt
├── feature/chat/{ChatViewModelTest, AnswerFormatterTest, ChatScreenTest}.kt
├── testing/ChatFakes.kt                        # repositorio en memoria, motor, recuperación y disponibilidad programables
├── testing/FakeRagIndex.kt                     # chatLibraryState
└── (ampliadas) NavigationTest, DocumentScreenTest, EngineMemoryCallbacksTest, RagParametersTest, AppModulesTest, LayeringTest
app/src/sharedTest/java/com/lisdatasolutions/microrag/rag/answer/AnswerGoldenFiles.kt
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/chat/SqliteChatRepositoryTest.kt
├── data/rag/RagIndexChatStateTest.kt
├── feature/chat/{ChatScreenAccessibilityTest, ChatScreenLayoutTest}.kt
├── testing/CorpusIndex.kt                      # indexación del corpus, extraída de RetrievalParityTest
└── data/chat/ChatBenchmarkTest.kt              # @RequiresModels

offline/
├── config/rag_parameters.json                  # llm_max_num_tokens (y prompt_template si se adopta)
├── eval/{prompt, generate, pipeline, report}.py  # v3/v4, MAX_NUM_TOKENS del JSON, template-review, sección
├── run_eval.py                                 # template-review
├── gen_answer_golden.py                        # answer_golden.jsonl (--check)
├── golden/answer_golden.jsonl
├── reports/{eval-report.md, template-review.csv}
└── tests/{test_prompt, test_runs, test_report, test_answer_golden}.py
```

**Structure Decision**: módulo único (D-009).
- **`rag/answer`:** los algoritmos puros portados, como `rag/retrieval`.
- **`domain/chat`:** el contrato.
- **`data/chat`:** la orquestación con índice, motor, modelos y base de datos.
- **`feature/chat`:** la pantalla.

## Diseño de ejecución

1. **Abrir el chat.** El ViewModel resuelve la conversación (R13), observa la disponibilidad (R11) y, en `Ready`, precarga Gemma.
2. **Enviar.** `AskQuestionUseCase` → `ChatSession.ask`:
   1. comprueba la disponibilidad;
   2. crea la conversación si hace falta e inserta el turno (`RETRIEVING`);
   3. recupera con el turno anterior (R10);
   4. construye el prompt y quita bloques si pasa del límite;
   5. genera (`GENERATING` con el primer token), guardando cada segundo;
   6. lee la negativa y las citas;
   7. guarda las fuentes y cierra el turno.
3. **Pantalla.** Combina el turno guardado con la respuesta en memoria, formatea (R3) y resuelve la disponibilidad de las fuentes (R12).
4. **Acciones:**
   - detener cancela;
   - reintentar repite en el mismo turno;
   - «Nuevo chat» y borrar cancelan la respuesta en curso;
   - copiar usa el portapapeles (R14);
   - una fuente abre `Document(documentId, chunkId)`, y el detalle se desplaza al fragmento.
5. **Ciclo de vida:**
   - con la app oculta, la sesión cancela y Gemma se libera;
   - con una tanda en marcha, el chat espera;
   - tras la muerte del proceso, los turnos a medias pasan a «interrumpidos».

## Orden de implementación

Las pruebas en el teléfono van al final, como en F10.

1. **Escritorio:**
   - `llm_max_num_tokens` y las variantes `v3` y `v4`;
   - `template-review`, ejecutado, con su sección, su revisión manual si alguna variante pasa la criba y su decisión;
   - después, `gen_answer_golden.py` con la plantilla activa.
2. **Base:**
   - parámetro y dominio;
   - `RagConnection` sin extensión y `chat.db` con su repositorio y pruebas instrumentadas;
   - consulta agregada del índice;
   - `MemoryTrimListener`.
3. **Algoritmos puros y su paridad en la JVM:** `PromptBuilder` y `AnswerReading`, con sus casos y el golden.
4. **Motor y sesión** (US1–US5): `EngineAnswerEngine` y `DefaultChatSession`, con los casos de uso, DI y sus pruebas JVM (A1–A15, convivencia y registro).
5. **Design system:** tarjeta con contenido, fila, chip, `LisdataOverflowMenu` (y el menú de F08 migrado) y previews.
6. **Pantalla del chat** (US1–US7):
   - ruta y navegación;
   - ViewModel, formateador y pantalla con los estados C1–C17;
   - pruebas Robolectric y de navegación;
   - desplazamiento del detalle de F08.
7. **Emulador:**
   - instrumentadas, con la accesibilidad de la pantalla;
   - recorrido de los estados;
   - `ChatBenchmarkTest`, si Gemma está preparada allí.
8. **Última fase, en el teléfono de referencia:**
   - instrumentadas;
   - primer token (SC-004);
   - diseño con teclado;
   - recorrido, acordado con el usuario;
   - cierre.

## Riesgos y verificaciones tempranas

1. **El port diverge del escritorio** (R2, R3). `AnswerGoldenTest` se escribe antes que la sesión. Lo delicado:
   - puntos de código frente a UTF-16;
   - `strip()` de Python;
   - las clases Unicode de `norm`.

   Un fallo se corrige en Kotlin; el harness es la referencia.
2. **Una variante de la plantilla se adopta** (R4). El golden se genera después de la decisión, así que el port no cambia. Se avisa al usuario con las cifras y se sigue: el turno anterior (C12) ya es el del escritorio.
3. **Primer token por encima de 5 s en el teléfono** (SC-004). La estimación con el prompt p95 da ≈ 4,2 s, pero las cifras de F02 son de una sola medida. Si pasa, se para y se consulta (FR-045), por ejemplo con 3 bloques o un presupuesto menor, con su evaluación.
4. **Fichas de cita y accesibilidad** (R3). Si las comprobaciones ATF marcan los enlaces dentro del texto, se para y se consulta antes de cambiar la presentación aprobada (D-053).
5. **Carreras con la ingesta** (R7). La puerta y la espera de `releaseGenerators()` se prueban en la JVM con el coordinador real antes de la pantalla.
6. **Gemma en el emulador.** Probablemente no está preparada, porque la GPU no funciona en el emulador y Gemma en CPU es lenta. El camino completo con modelos se valida en la JVM con fakes y en el teléfono.
7. **El recorrido en el teléfono puede mostrar el documento del usuario** (R15). Se acuerda con el usuario antes de hacerlo.

## Complexity Tracking

| Punto | Por qué | Alternativa más simple descartada |
|---|---|---|
| Segunda base de datos (`chat.db`) frente a «una sola conexión de escritura» (IV) | La regla busca que no haya escrituras simultáneas. `chat.db` es otro fichero con una sola conexión, y usa el mismo hilo único (`AppDispatchers.database`) que el índice: en toda la app hay como mucho una escritura a la vez | Tablas en `rag.db`: un índice incompatible o reconstruido bloquearía o borraría las conversaciones, y cada escritura del chat movería los contadores del índice (R9) |
