# Implementation Plan: Historial de chats (F13)

**Branch**: `013-chat-history` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md) | **Wireframe**: [wireframe.md](wireframe.md)

**Input**: Feature specification from `/specs/013-chat-history/spec.md`

## Summary

F13 añade la pantalla Historial: la lista de las conversaciones guardadas por F11, desde la que se abren, se continúan y se borran. Sigue el wireframe aprobado, con H1–H11 y D-063.

- **Lista** (`feature/history`):
  - una consulta nueva sobre `chat.db`, sin cambio de esquema: título de la primera pregunta, última actividad y número de preguntas (research R1);
  - por tandas de 50, como la Biblioteca;
  - grupos «Hoy», «Ayer», «Últimos 7 días» y por mes, calculados con `java.time` (R3);
  - la lista solo se vuelve a leer cuando cambia: no con los guardados de cada segundo de una respuesta (R2).
- **Abrir:**
  - Historial devuelve la petición a la entrada del chat, como el aviso del detalle a la Biblioteca (R4);
  - abrir otra conversación detiene la respuesta en curso, como «Nuevo chat» (R5);
  - continuar usa el prompt de F11 sin cambios.
- **Borrar:**
  - papelera en cada fila, con la confirmación de F11;
  - la sesión borra bajo su cerrojo y avisa al chat, que vuelve a un chat vacío si era la suya (R5, R6);
  - `DeleteConversationUseCase` deja de lanzar.
- **Chat:**
  - «Historial» en «⋯»;
  - las fichas `[n]` de documentos borrados se ven en gris y dejan de abrir nada (R7);
  - una conversación abierta empieza en su última respuesta (R8).
- **Medida:** el tamaño de 1.000 conversaciones y el tiempo de apertura, con un fichero de prueba aparte (R9).
- **Pruebas en el teléfono, en la última fase** (petición del usuario), sin capturar Historial mientras liste la conversación del usuario (R12).

## Technical Context

**Language/Version**: Kotlin 2.4.21 y Java 17 como bytecode target, con JBR 21 (sin cambios).

**Primary Dependencies**: ninguna nueva (research R13):
- Compose BOM 2026.09.00, navigation 2.10.2, lifecycle 2.11.0, Koin 4.2.2 y LiteRT-LM 0.18.1, sin cambios;
- `java.time` de la plataforma, disponible con minSdk 31.

**Storage**:
- `chat.db` sigue en el esquema v1, con una consulta nueva y un contador de cambios de la lista;
- la medida usa `cacheDir/history-benchmark/chat.db`, que se borra al terminar.

**Testing**:
- **JVM:**
  - agrupación, textos de fecha (Robolectric, español e inglés, 12 y 24 h), ViewModel y pantalla de Historial;
  - ampliadas: sesión, ViewModel y pantalla del chat, `NavigationTest`, `LayeringTest` y `AppModulesTest`;
  - sin cambios: `StringsParityTest` y `AnswerGoldenTest`.
- **Instrumentadas en el emulador:**
  - `SqliteChatRepositoryTest` ampliada;
  - `HistoryScreenAccessibilityTest` (ATF);
  - `HistoryBenchmarkTest`;
  - `SystemBackTest`;
  - las de F01–F12.
- **Teléfono de referencia:** batería, medida y recorrido acordado, en la última fase.

**Target Platform**: Android, minSdk 31 y targetSdk 37.
- Emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37).
- Teléfono de referencia A063 (API 35), al final.

**Project Type**: mobile-app, módulo único `:app` (D-009).

**Performance Goals**:
- con 1.000 conversaciones, primeras filas en menos de 1 s en el teléfono de referencia (SC-001);
- la lista no se vuelve a leer durante una respuesta (R2);
- borrar detiene una respuesta en curso en menos de 1 s (SC-005).

**Constraints**:
- **Sin red** (I). Conversaciones en `noBackupFilesDir`, y nada se borra solo (I, FR-022).
- **Capas:** `feature/history` no importa `data`, SQLite, WorkManager, LiteRT, `File` ni `LocaleManager` (II, U1).
- **Prompt y recuperación** sin cambios (III).
- **Borrar** exige confirmación (V).
- **UI:** el wireframe y H1–H11 aprobados (VII).

**Scale/Scope**:
- 1 pantalla, 1 diálogo y 1 componente nuevo (`LisdataConversationRow`);
- 1 utilidad movida (`LoadMoreNearTheEnd`);
- 1 consulta y 1 contador en `chat.db`;
- 2 miembros nuevos y 1 cambiado en la sesión;
- 4 casos de uso nuevos y 1 cambiado;
- unos 17 textos nuevos en español e inglés;
- 1 prueba de medida.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple F13 | Estado |
|---|---|---|
| I. On-device y privacidad | Sin red ni permisos nuevos (`NetworkConfinementTest`). Las conversaciones siguen en `noBackupFilesDir/chat`, con `allowBackup=false`, y nada se borra solo. Los registros solo llevan ids y cifras (Q13). La medida usa su propio fichero. En el teléfono, Historial no se captura mientras liste la conversación del usuario (R12) | ✅ |
| II. Stack y arquitectura | `HistoryScreen → HistoryViewModel → casos de uso → ChatRepository/ChatSession`. SQLite solo en `data/chat`. Regla nueva de `LayeringTest` para `feature/history`. Sin dependencias nuevas ni Room ni Paging (R1, R13) | ✅ |
| III. RAG según el informe | Continuar es el `ask()` de F11: el mismo turno anterior y el mismo prompt (Q10, SC-004). El golden de 426 casos no cambia. `rag/answer` y `rag/retrieval` no se tocan | ✅ |
| IV. Rendimiento | Una consulta indexada por tanda, en el hilo de la base de datos. No se vuelve a leer durante las respuestas (R2). Sin motores nuevos; abrir o borrar detienen la respuesta sin tocar la GPU | ✅ |
| V. Veracidad del estado | Borrar pide confirmación y dice si detiene una respuesta. Un borrado fallido lo dice y no quita nada (R6). Las fuentes de documentos borrados nunca abren nada (R7) | ✅ |
| VI. Calidad y pruebas | ViewModels con fakes. Agrupación pura con `Clock`. Consulta probada en el emulador. ATF. Medida reproducible (R9, R11) | ✅ |
| VII. UI fiel y accesible | Wireframe y H1–H11 aprobados. Componentes `Lisdata*` y tokens. U1–U10. Textos en español e inglés con paridad. La corrección de H7 (R7) se aprueba en la parada del analyze. Diferencias en el informe de cierre | ✅ |
| VIII. Proceso | Documentación en español y código en inglés. D-063 (clarify) y D-064 (plan). Parada tras analyze. Pruebas en el teléfono al final | ✅ |

**Re-check post-diseño:** sin violaciones.
- La única desviación de lo aprobado es la frase de TalkBack de las fichas inertes (R7). Va como corrección para aprobar, no como excepción.

## Project Structure

### Documentation (this feature)

```text
specs/013-chat-history/
├── spec.md
├── wireframe.md                   # aprobado (H1–H11)
├── plan.md                        # este fichero
├── research.md                    # R1–R13
├── data-model.md                  # entradas, página, sesión, casos de uso, grupos, pantalla
├── quickstart.md
├── contracts/
│   ├── history-api.md             # casos de uso, reglas Q1–Q13, inyección y cambios
│   └── history-ui.md              # pantalla, fila, diálogo, textos, navegación, U1–U10
├── checklists/requirements.md
└── tasks.md                       # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/lisdatasolutions/microrag/
├── domain/chat/
│   ├── ChatModels.kt                           # ConversationSummary, ConversationPage, HistoryLimits, HistoryContent
│   ├── ChatRepository.kt, ChatSession.kt       # conversations(limit); openConversation, deletions
│   └── ChatUseCases.kt                         # 4 nuevos; DeleteConversationUseCase con Boolean
├── data/chat/
│   ├── SqliteChatRepository.kt                 # consulta de R1, listChanges
│   └── DefaultChatSession.kt                   # openConversation, delete bajo el cerrojo, deletions
├── feature/history/
│   ├── HistoryRoute.kt, HistoryScreen.kt, HistoryViewModel.kt, HistoryUiState.kt
│   ├── HistoryGrouping.kt, HistoryTexts.kt
│   └── HistoryPreviewData.kt
├── feature/chat/{ChatViewModel, ChatUiState, ChatScreen}.kt   # R6–R8, Q11, Q12
├── core/designsystem/component/
│   ├── Rows.kt                                 # LisdataConversationRow
│   ├── Chat.kt                                 # unavailableCitations
│   └── Lists.kt                                # LoadMoreNearTheEnd (desde LibraryScreen)
├── feature/library/LibraryScreen.kt            # usa Lists.kt
├── core/navigation/{Destinations, MicroRagNavHost}.kt         # History, CHAT_REQUEST
├── domain/di/DomainModule.kt, feature/di/FeatureModule.kt
app/src/main/res/values{,-en}/strings_history.xml
app/src/debug/java/com/lisdatasolutions/microrag/debug/
├── DebugRouting.kt                             # ruta history
└── catalog/CatalogEntries.kt                   # LisdataConversationRow y fichas inertes

app/src/test/java/com/lisdatasolutions/microrag/
├── feature/history/{HistoryGroupingTest, HistoryTextsTest, HistoryViewModelTest, HistoryScreenTest}.kt
├── testing/ChatFakes.kt                        # conversations, openConversation, deletions
└── (ampliadas) DefaultChatSessionTest, ChatViewModelTest, ChatScreenTest, NavigationTest, LayeringTest, AppModulesTest
app/src/androidTest/java/com/lisdatasolutions/microrag/
├── data/chat/{SqliteChatRepositoryTest (ampliada), HistoryBenchmarkTest}.kt
├── feature/history/HistoryScreenAccessibilityTest.kt
└── SystemBackTest.kt                           # Atrás desde Historial
```

**Structure Decision**: módulo único (D-009).
- **`domain/chat`:** el contrato de la lista y de la sesión, junto al de F11.
- **`data/chat`:** la consulta y la sesión.
- **`feature/history`:** la pantalla nueva. Sus fechas y grupos son lógica de presentación.
- **`core/designsystem`:** la fila nueva y las fichas inertes, en un único sistema de componentes.

## Diseño de ejecución

1. **Abrir Historial:**
   - «⋯» → «Historial» navega a `History(openConversationId)`;
   - el ViewModel observa `ObserveConversationsUseCase(limit)` con el límite del `SavedStateHandle`;
   - agrupa con la fecha del `Clock` y la vuelve a leer en `onStart()`.
2. **Desplazarse:** `LoadMoreNearTheEnd` pide `onLoadMore()`, que suma 50 al límite si hay más.
3. **Abrir una fila:**
   - el evento `Open(id)` escribe `CHAT_REQUEST = "open:<id>"` en la entrada del chat y cierra Historial;
   - `ChatRoute` llama a `onOpenConversation(id)`: detiene una respuesta de otra conversación, muestra la elegida en su última respuesta y borra la clave.
4. **«＋» o «Nuevo chat» del vacío:** lo mismo con `"new"` y `onNewChat()`.
5. **Borrar:**
   1. la papelera guarda el id pendiente y abre el diálogo, con la línea extra si esa conversación se está respondiendo;
   2. al confirmar, `DeleteConversationUseCase`: la sesión, bajo el cerrojo, para, espera, borra y emite el id en `deletions`;
   3. la lista se vuelve a leer por `listChanges`;
   4. el aviso dice «Conversación borrada», o el fallo;
   5. el chat, si era la suya, queda vacío.
6. **Fichas inertes:** el chat resuelve la disponibilidad de las fuentes como en F11 y pasa a la tarjeta las fichas de documentos borrados.

## Orden de implementación

Las pruebas en el teléfono van al final, como en F10–F12 (petición del usuario).

1. **Datos:**
   - modelos y contrato del repositorio;
   - la consulta, `listChanges` y el fake;
   - `SqliteChatRepositoryTest` ampliada en el emulador.
2. **Sesión y casos de uso:**
   - `openConversation`, `delete` bajo el cerrojo y `deletions`;
   - los casos de uso nuevos y `DeleteConversationUseCase` con `Boolean`;
   - `DefaultChatSessionTest`, con SC-004.
3. **Chat:**
   - `onOpenConversation`, `deletions` y el borrado fallido;
   - fichas inertes y marcas de desplazamiento por conversación;
   - «Historial» en «⋯»;
   - pruebas del ViewModel y la pantalla.
4. **Design system:**
   - `LisdataConversationRow`;
   - `unavailableCitations`;
   - `LoadMoreNearTheEnd` en `Lists.kt`, con la Biblioteca sin cambios de comportamiento;
   - catálogo y vistas previas.
5. **Historial (US1, US3–US5):**
   - agrupación y textos;
   - ViewModel, pantalla y diálogo;
   - navegación y `CHAT_REQUEST`;
   - ruta de depuración;
   - pruebas JVM.
6. **Textos:** `strings_history.xml` en español e inglés, con `StringsParityTest`.
7. **Emulador:**
   - ATF;
   - `HistoryBenchmarkTest`;
   - `SystemBackTest`;
   - baterías en API 33 y API 37;
   - recorrido manual y capturas frente al wireframe.
8. **Última fase, en el teléfono de referencia:**
   - batería;
   - medida;
   - recorrido acordado (sin capturar Historial con la conversación del usuario);
   - red;
   - limpieza y cierre.

## Riesgos y verificaciones tempranas

1. **El resultado al chat se pierde o se repite** (R4). `NavigationTest` cubre abrir, girar con Historial abierto y volver, y el doble toque. La clave se borra al atenderla.
2. **Una respuesta empieza entre la parada y el borrado.** `delete` pasa al cerrojo, como `deleteAll`. Una prueba de la sesión lo fuerza con un `ask` concurrente.
3. **Fechas y zonas.** Las pruebas de agrupación usan zonas fijas (Europa/Madrid con cambio de hora, UTC y una con +14 h) y la medianoche exacta.
4. **SC-001 en el teléfono.** La consulta es indexada. Si la primera tanda pasara de 1 s, se mide sin la subconsulta del recuento y se decide con las cifras.
5. **Privacidad en el teléfono** (R12). Historial lista el título de la conversación del usuario: sin capturas ni volcados de texto allí, y el recorrido se acuerda antes.
6. **Las fichas inertes y TalkBack** (R7). La frase aprobada en H7 no se puede dar dentro del texto. La corrección se presenta en la parada del analyze.
7. **Mover `LoadMoreNearTheEnd`.** Las pruebas de la Biblioteca (tandas) deben seguir pasando sin cambios.

## Complexity Tracking

Sin violaciones de la constitución.
