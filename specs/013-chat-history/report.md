# Informe de cierre — F13 Historial de chats

> **Estado:** implementada; pendiente de la revisión de cierre del usuario.

**Rama:** `013-chat-history`, creada desde `a78c488`.
**Fecha:** 2026-11-02 y 2026-11-03.
**Dispositivos:**
- emuladores `MicroRAG_API33` (API 33) y `Pixel_10` (API 37);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1);
- sin dependencias nuevas (research R13);
- esquemas sin cambios: índice v3 y `chat.db` v1;
- lint avisa de dos versiones más nuevas, LiteRT-LM 0.19.0 (como en F11 y F12) y SQLite-Vector 1.2.0 (nuevo aviso). No se actualizan en F13.

## 1. Qué se ha implementado

**Historial** (wireframe H1–H11, D-063 a D-065):
- **Acceso:** «Historial» en el menú «⋯» del chat, entre «Nuevo chat» y «Borrar conversación», siempre visible. Es una pantalla encima del chat, sin barra inferior.
- **Lista:**
  - las conversaciones con preguntas, de la de actividad más reciente a la más antigua;
  - grupos «Hoy», «Ayer», «Últimos 7 días» y uno por mes;
  - título de la primera pregunta, fecha según el grupo y número de preguntas;
  - chip «Abierta» en la del chat;
  - por tandas de 50 al desplazarse.
- **Estados:** carga (4 filas esqueleto), vacío con «Nuevo chat», y error de C17 sin botones (D-065).
- **Abrir:** Historial devuelve la conversación elegida a la entrada del chat, que la atiende una sola vez, también tras girar el teléfono (research R4). La conversación abierta empieza en su última respuesta, y se sigue preguntando en ella con su turno anterior (Q10).
- **Borrar:** papelera en cada fila, con la confirmación de F11 y, si esa conversación se está respondiendo, «La respuesta en curso se detendrá.». Avisos «Conversación borrada» y «No se pudo borrar la conversación.». Si era la del chat, el chat queda vacío.
- **«＋» y «Nuevo chat»:** dejan la pestaña Chat vacía, y detienen una respuesta en curso como «Nuevo chat» del chat.

**Chat y sesión:**
- **Sesión** (research R5):
  - `openConversation` detiene, y espera, la respuesta de otra conversación;
  - `delete` va bajo el cerrojo de la sesión y avisa del id borrado (`deletions`);
  - el chat que muestra esa conversación vuelve a un chat vacío, también si se restaura tras la muerte del proceso (Q16).
- **Fichas `[n]` de documentos borrados** (research R7): en gris y sin enlace, también mientras se genera la respuesta. TalkBack lee su número, y la frase «Fuente ya no disponible» la da su fila de fuentes (H7 corregida, D-065).
- **`chat.db`** (research R1, R2): una consulta para la página y su total, sin cambiar el esquema, que solo se vuelve a leer con una pregunta o un borrado, no con los guardados de cada segundo de una respuesta. La pestaña Chat ya no abre nunca una conversación sin preguntas.

**Arreglos de F11 salidos del analyze (D-065):**
- **Una respuesta parada termina antes de empezar otra** (Q14): preguntar justo después de «Nuevo chat», de detener o de abrir otra conversación ya no deja la respuesta nueva como «interrumpida».
- **Identificadores reutilizados** (Q15): SQLite da el id de un documento borrado al siguiente que se importa. Un documento creado después de una respuesta ya no cuenta como su fuente, así que la cita vieja sigue en «Fuente ya no disponible» y no abre otro documento. `RagIndexWriteTest` confirma la reutilización del id en el emulador.
- **Un borrado fallido ya no cierra la app:** `DeleteConversationUseCase` devuelve `false` en lugar de lanzar, y el chat lo anuncia.

**Design system:**
- `LisdataConversationRow`, con vistas previas y entrada en el catálogo de depuración;
- `LisdataAnswerCard(unavailableCitations)`;
- `LoadMoreNearTheEnd` sale de la Biblioteca a `Lists.kt`, sin cambiar su comportamiento.

**Textos:** `strings_history.xml`, en español y en inglés, con 17 textos nuevos. Se reutilizan los de F11 y F12 que decían lo mismo.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 988 de 988 |
| Lint (`lintDebug`) | 0 errores. 2 avisos de versiones más nuevas (LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0) |
| Escritorio (Python) | 181 pruebas OK, y el golden de respuestas al día |
| `Pixel_10` (API 37), instrumentadas | 775 pruebas: 768 pasan, 7 omitidas por sus propias condiciones (`assume`), 0 fallos |
| `MicroRAG_API33` (API 33), instrumentadas | 775 pruebas: 752 pasan, 19 omitidas por sus propias condiciones, 4 fallos previos (Atrás desde la raíz) |
| Teléfono A063, instrumentadas | OK (775 pruebas), en 22,4 min, con la pantalla despierta |

**Los 4 fallos de API 33 no son de F13.** Son los mismos de F12: `SystemBackTest` y tres casos de `OnboardingNavigationTest` esperan que Atrás desde la raíz cierre la actividad (`DESTROYED`), y en API 33 queda en `CREATED`. En `Pixel_10` pasan.

**Pruebas nuevas o ampliadas:**
- **JVM:**
  - `HistoryGroupingTest`, `HistoryTextsTest`, `HistoryViewModelTest`, `HistoryScreenTest` y `HistoryUseCasesTest`;
  - `ConversationRowTest` y `AnswerChipLineTest`;
  - ampliadas: `DefaultChatSessionTest`, `ResolveSourcesUseCaseTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest` (y `NavigationRestorationTest`) y `LayeringTest`.
- **Instrumentadas:** `SqliteChatRepositoryTest` y `RagIndexWriteTest` ampliadas, `HistoryScreenAccessibilityTest` (30 combinaciones) y `HistoryBenchmarkTest`.

## 3. Validación por criterio

| Criterio de FEATURES | Cómo se ha comprobado |
|---|---|
| 1. Wireframe aprobado antes de `/speckit-plan` | Aprobado el 2026-11-02 (D-063). En el analyze cambiaron H5 y H7, con aprobación del usuario (D-065) |
| 2. Lista paginada por fecha, con el título de la primera pregunta | `SqliteChatRepositoryTest` (orden, empates, título, tandas sin duplicados), `HistoryGroupingTest`, recorrido en el emulador |
| 3. Abrir restaura mensajes y citas; las de documentos borrados dicen «Fuente ya no disponible» | `ChatViewModelTest` (todos los estados, fichas inertes), `ChatScreenTest`, `NavigationTest`, recorrido en el emulador |
| 4. Borrar pide confirmación; «Borrar historial» de Ajustes vacía la lista | `HistoryViewModelTest`, `HistoryScreenTest`, `DefaultChatSessionTest`, recorrido en el emulador |
| 5. «Nuevo chat» crea una conversación nueva | `NavigationTest` («＋» y botón del vacío), `HistoryViewModelTest` |

## 4. Cifras medidas

**Medida con 1.000 conversaciones de 5 preguntas** (`HistoryBenchmarkTest`; fichero de prueba aparte, borrado al terminar):

| Dispositivo | Tamaño | Primera tanda p50 / p95 | Lista entera | Primera fila dibujada |
|---|---:|---:|---:|---:|
| `Pixel_10` (API 37) | 7,6 MB (7.740 KB) | 0,5 / 3,4 ms | 2 ms | 239 ms |
| A063 (API 35) | 7,6 MB (7.740 KB) | 2,1 / 14,4 ms | 15 ms | 655 ms |

La estimación previa de D-063 era de 5–10 MB. **SC-001 se cumple en el teléfono:** la primera fila se dibuja en 655 ms, por debajo de 1 s. El fichero de la medida se borró al terminar.

### Recorrido en `Pixel_10` (T041)

Con un historial de prueba de 5 conversaciones en las fechas del wireframe. Sus fuentes nombran documentos que no existen, y el emulador no tiene modelos, así que no se puede preguntar.

| Paso | Resultado |
|---|---|
| Historial (ruta de depuración) | Igual que el wireframe §2: «HOY», «AYER», «ÚLTIMOS 7 DÍAS» («viernes») y «OCTUBRE DE 2026» |
| Atrás | Vuelve al chat, con la última conversación |
| «⋯» del chat | «Nuevo chat», «Historial», «Borrar conversación», en ese orden |
| «Historial» desde el chat | «Abierta» en la conversación del chat |
| Tocar la conversación de octubre | El chat la muestra; al volver a Historial, «Abierta» está en ella |
| Fichas y fuentes de documentos borrados | Pastillas grises sin enlace; «Fuente ya no disponible» en las dos filas |
| Papelera → «Cancelar» | No borra nada |
| Papelera de la abierta → «Borrar» | Desaparece el grupo de octubre, aviso «Conversación borrada». Atrás: el chat queda vacío, con «⋯» sin «Borrar conversación» |
| «＋» de Historial | Vuelve al chat vacío |
| «Borrar historial de chat» en Ajustes | «Historial borrado». Historial se abre vacío: «Aún no hay conversaciones» con «Nuevo chat» |
| Inglés | «History», «TODAY», «10:42 AM», «Friday», «Oct 12», «No conversations yet» |
| Modo oscuro | Legible, con los tokens de siempre |
| Letra al 200 % | Títulos en 2 líneas con «…», papeleras de 48 dp; el chip «Abierta» baja bajo la fecha |
| Horizontal, con letra normal y al 200 % | La lista a todo el ancho, desplazable |

El emulador quedó con letra 1.0, giro automático, modo claro y la app sin idioma propio (`[]`).

**`MicroRAG_API33`:** el mismo historial de prueba, abierto desde «⋯» del chat: igual que en `Pixel_10`, con «Abierta» en la conversación del chat.

Al terminar se desinstaló la app de los dos emuladores, lo que borró su historial de prueba, y se cerraron.

### Teléfono A063 (T045)

Recorrido acordado con el usuario el 2026-11-03: completo, borrando también la conversación de prueba de F12, con cambios temporales de letra, giro y modo. Dos documentos públicos de prueba («Wikibooks Git.txt» y «Wikibooks Basic Book Design.txt») en una carpeta propia, y preguntas marcadas «Prueba F13». **El Historial no se capturó ni se volcó** mientras listaba la conversación del usuario: solo se leyeron las etiquetas de la app y las filas de prueba, y las demás se contaron.

| Paso | Resultado |
|---|---|
| Importar e indexar los dos documentos | 70 y 49 fragmentos en GPU. Recibieron los ids 3 y 4, los de los documentos de prueba de F12 ya borrados: el caso real de Q15 |
| Dos conversaciones nuevas («Nuevo chat», comprobado vacío) | Respondidas, con 1 cita cada una |
| «⋯» → Historial | «HOY» con las dos, «Abierta» en la del chat; 2 filas ajenas ocultas (la de F12 y la del usuario) |
| Abrir la de Git desde Historial y preguntar | La respuesta lleva su turno anterior (`follow_up=true`, 4 bloques); la conversación sube con «2 preguntas» |
| Papelera → «Cancelar», y después «Borrar» | Nada se borra; luego desaparece, con «Conversación borrada» y `chat delete outcome=ok` |
| «＋» | El chat queda vacío |
| Borrar «Wikibooks Git.txt» y volver a abrir su conversación | Sus dos fuentes, «Fuente ya no disponible»; la ficha «1» en gris |
| Modo claro, letra al 200 % y horizontal | El Historial completo, con su fila de prueba y la papelera |
| Conversación de prueba de F12 | Su título no coincidía al pie de la letra (el teclado cambia mayúsculas al escribir); se identificó por parecido (0,99) sin leer ningún título y se borró desde Historial |
| Red | 0 bytes de la app (uid 10628), antes y después |
| Registro de la app | Solo `history page`, `chat delete`, `library delete` y `rag document`, con cifras; ninguna mención de los documentos ni de las preguntas |

**Limpieza y estado final:**
- se borraron las conversaciones de prueba (las de hoy y la de F12), los dos documentos en la app, y su carpeta y sus entradas de medios;
- en el historial queda solo la conversación del usuario, que no se abrió ni se leyó;
- el teléfono quedó como estaba al empezar: letra 1.0, giro fijo en vertical, modo oscuro y la app en español (`[es]`).

Al empezar, el giro automático estaba desactivado y la app tenía `[es]`; al cerrar F12 se dejaron giro automático y `[]`, así que se respetó el estado que había.

**Dos cosas que no salieron como estaban previstas:**
- **El primer intento de volver a vertical no se aplicó:** el teléfono siguió en horizontal hasta repetir `user-rotation lock 0`. Se comprobó después con `dumpsys window` (`ROTATION_0`).
- **La conversación de F12 no coincidía con su título exacto.** No se adivinó: se comparó por parecido sin mostrar títulos, y solo una fila superó el umbral.

## 5. Revisión del inglés (T037)

Los 17 textos nuevos de `strings_history.xml`, sin correcciones.

## 6. Diferencias frente al wireframe

- **H5, error sin «Reintentar»** (D-065): el botón no podía arreglar un fichero ilegible.
- **H7, TalkBack de las fichas inertes** (D-065): leen su número, sin acción. La frase «Fuente N: …, Fuente ya no disponible» la da su fila de fuentes.
- **Hora:** con el formato del sistema, sin cero delante en español («8:15»). El wireframe dibujaba «08:15».
- **Fecha de los grupos de mes:** «12 oct», sin punto, con el formato del sistema. El wireframe dibujaba «12 oct.».

## 7. Desviaciones respecto al plan

- **Atrás del sistema desde Historial, en `NavigationTest`** (con `Espresso.pressBack()`) y no en `SystemBackTest`: esa prueba corre en el teléfono de referencia sobre las conversaciones reales, y Historial listaría las del usuario (research R12).
- **`ChatRequest`** es un tipo pequeño en `core/navigation` (`open:<id>` o `new`), en lugar de un texto suelto.
- **El chip «Abierta» se coloca con un `FlowRow`,** que da la misma regla que `TextsWithChip` de F12 para un texto de una línea.
- **`ResolveSourcesUseCase` devuelve la disponibilidad por fuente citada,** no por fragmento: el mismo id de fragmento puede ser de dos documentos en dos momentos (Q15).

## 8. Fallos encontrados y corregidos

- **Ficha partida entre dos líneas** (recorrido en `Pixel_10`): una ficha `[n]` que caía en un salto de línea dibujaba su fondo sobre las dos líneas enteras. Pasaba ya con las fichas azules de F11, pero con las grises se veía. Las fichas usan ahora espacios finos que no permiten partir la línea. Nueva prueba `AnswerChipLineTest`, que falla sin el arreglo (`a76f9d0`).
- **Recursos que no seguían la configuración** (lint): `rememberHistoryTexts` leía `LocalContext.current.resources`; ahora `LocalResources.current` (`2fb89b4`).
- **Dos pruebas de F11 con la respuesta anterior al documento** (`ChatViewModelTest`, `NavigationTest`): con Q15, un documento creado después de la respuesta no es su fuente. Las pruebas pasan a guardar la respuesta después de importar el documento.

## 9. Pendiente

- **Nada de F13 queda abierto,** salvo la revisión de cierre del usuario.

**Visto durante las pruebas, fuera de F13** (no se ha tocado):
- **Lint:** SQLite-Vector 1.2.0 está disponible (aviso nuevo de este cierre). Hay que verificarla y decidirlo aparte, como LiteRT-LM 0.19.0.
- **Atrás desde la raíz en API 33:** los 4 fallos previos (§2).
- **Teléfono, una vez:** al volver del Índice tras indexar, el volcado solo mostró la barra inferior, sin el contenido de la pantalla; al tocar la pestaña Chat todo volvió a la normalidad. No se capturó, porque podía ser Inicio o la Biblioteca con el documento del usuario.
- **Teclado del teléfono:** cambia mayúsculas al escribir con `input text` («git» → «Git», «last» → «Last»). Afecta a los recorridos automáticos, no a la app.
