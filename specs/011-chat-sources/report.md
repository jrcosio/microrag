# Informe de cierre — F11 Chat con fuentes

> **Estado:** borrador, en la fase del teléfono. Hechos T066 (instrumentadas) y T068 (teclado real). **T067:** la primera medida da un p95 de 5,2 s; el usuario pidió medir otra vez con el teléfono cargado y frío. **Pendiente:** esa medida, el recorrido (T069) y el cierre (T070).

**Rama:** `011-chat-sources`.
**Fecha:** 2026-10-30.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35).

**Versiones:**
- app 0.1.0 (versionCode 1);
- LiteRT-LM 0.18.1 y SQLite-Vector 1.1.2;
- sin dependencias nuevas;
- esquema del índice v3, sin cambios;
- `chat.db` nuevo, esquema v1.

## 1. Qué se ha implementado

- **Plantilla del prompt (D-035, D-056):** revisión en el escritorio con dos variantes fijadas antes de evaluar. Se adopta `v4` (81,1 % de respuestas correctas frente al 78,4 % de «informe»). La app y el escritorio la comparten por `prompt_template`.
- **Port exacto del escritorio** (`rag/answer`):
  - `PromptBuilder` (`prompt.py`), con todas las plantillas y el idioma como parámetro (español por defecto);
  - `AnswerReading` (`judge.py`): citas `[n]` y `[1, 3]`, marcas, normalización, negativa y orden de primera cita.
  - El golden de respuestas (`answer_golden.jsonl`, 426 preguntas) los comprueba carácter a carácter.
- **Sesión de chat** (`DefaultChatSession`, reglas A1–A15):
  - una respuesta a la vez en toda la app, en el ámbito de la aplicación, así que sigue al cambiar de pestaña;
  - estados `RETRIEVING` → `GENERATING` → `COMPLETED`, `CANCELLED`, `FAILED` o `INTERRUPTED`, guardados cada segundo;
  - turno anterior como el escritorio (C12), y límite del prompt de 2.000 tokens estimados: primero cae el bloque peor clasificado, después el turno anterior y nunca el último bloque;
  - sin bloques, la negativa sin llamar al modelo;
  - una línea de registro por respuesta, solo con cifras.
- **Motor** (`EngineAnswerEngine`): Gemma 4 E2B con el backend preparado en F03 y la configuración del escritorio (4.096 tokens, 512 de salida, codicioso). Precarga solo con el chat `Ready`.
- **Convivencia con la GPU:**
  - con una tanda de indexación en marcha, el chat espera (C14) y no carga Gemma;
  - una respuesta en curso termina antes de que la tanda cargue su motor;
  - con la app oculta o con poca memoria, la respuesta queda interrumpida (C13) y después se libera Gemma (`MemoryTrimListener`).
- **Persistencia** (`chat.db` en `noBackupFilesDir/chat`):
  - conversaciones, turnos y fuentes;
  - un turno a medias queda interrumpido al arrancar;
  - un fichero dañado o más nuevo no se toca (C17);
  - un fallo al escribir deja la respuesta en memoria (C16), y la siguiente pregunta vuelve a intentar guardarla.
- **Pantalla** (PNG 04, contrato `chat-ui.md`):
  - burbujas, tarjeta de «MicroRAG» con viñetas y fichas `[n]` tocables, chip «N fuentes» y «FUENTES CONSULTADAS»;
  - «⋯» con «Nuevo chat» y «Borrar conversación»;
  - compositor que pasa a detener, y estados de disponibilidad con y sin conversación;
  - la ruta `Chat(conversationId, newConversation)`: la pestaña abre la última conversación, y «Nuevo chat» de Inicio una vacía.
- **Fuentes:** cada fila abre el detalle en su primer fragmento. Una fuente se resuelve contra el índice: documento borrado, fila inerte; fragmento que ya no está, aviso de F08.
- **Detalle del documento (F08):**
  - se desplaza una vez hasta el fragmento citado, también en horizontal;
  - su «⋯» pasa al componente común `LisdataOverflowMenu`.

## 2. Build, lint y tests

| Puerta | Resultado |
|---|---|
| Pruebas Python (`unittest`) | ✅ 147/147 (F10: 131) |
| `gen_answer_golden.py --check` | ✅ al día |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ sin avisos |
| Tests JVM | ✅ 815/815 (F10: 657) |
| `apkanalyzer dex packages` del APK de release | ✅ 0 clases de `microrag.debug` |
| `ChatScreenAccessibilityTest` en el emulador | ✅ 156/156 |
| `CatalogAccessibilityTest` en el emulador | ✅ 78/78 |
| Instrumentados en el emulador (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 657/657 (F10: 490), en 18 min; repetida con los APK finales tras el arreglo de T064: 657/657 |
| Pruebas con modelos en el emulador | ✅ `RetrievalParityTest` sigue igual con `CorpusIndex` (0,837 frente a 0,837 del escritorio); `ChatBenchmarkTest` se salta, porque el emulador no tiene Gemma |
| Instrumentados en el teléfono | ✅ 657/657 en 16,5 min. Una primera ejecución perdió la salida por la conexión adb; en ella falló una vez `CatalogAccessibilityTest` (control de menos de 48 dp). No se reproduce: sola, 78/78, y en la batería completa, en verde |
| `AnswerGoldenInstrumentedTest` (nueva, golden de respuestas en el dispositivo) | ✅ 3/3 en el teléfono: 426 prompts por SHA-256, lectura de citas y fichas |
| `ChatScreenLayoutTest` en el teléfono | ✅ con el teclado real y la barra inferior |

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Preguntar y recibir una respuesta con fuentes | `DefaultChatSessionTest`, `EngineAnswerEngineTest`, `AnswerGoldenTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest`, `DocumentScreenTest` | ✅ JVM. En un dispositivo: _pendiente_ |
| **US2** Negativa | `DefaultChatSessionTest`, `ChatViewModelTest`, `ChatScreenTest` | ✅ JVM |
| **US3** Detener, copiar y reintentar | ídem | ✅ JVM |
| **US4** Seguimiento | `DefaultChatSessionTest`: las 50 preguntas de seguimiento del golden, pasando por la sesión, dan el prompt del escritorio | ✅ JVM |
| **US5** Conversación guardada | `SqliteChatRepositoryTest` (emulador), `DefaultChatSessionTest`, `ResolveSourcesUseCaseTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest` | ✅ |
| **US6** Chat bloqueado o limitado | `ObserveChatAvailabilityUseCaseTest`, `ChatDuringIndexingTest`, `ChatViewModelTest`, `ChatScreenTest` | ✅ JVM |
| **US7** Fiel al PNG y accesible | `ChatScreenTest`, `ChatScreenAccessibilityTest`, `CatalogAccessibilityTest`, `ChatScreenLayoutTest` | ✅ emulador y teléfono (teclado real en el A063) |
| **US8** Primer token | `ChatBenchmarkTest` | ⚠️ primera medida: p95 5.205 ms. Se repite (§4) |
| **US9** Revisión de la plantilla | `template-review`, D-056 | ✅ |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001 fuentes solo del contexto | ✅ `AnswerReadingTest`, `AnswerFormatterTest` y el golden: números fuera de rango, repetidos y formatos raros |
| SC-002 prompt idéntico al del escritorio | ✅ 426/426 por SHA-256 en la JVM y en el teléfono, y las 50 de seguimiento también a través de la sesión |
| SC-003 lectura de citas y negativa como el escritorio | ✅ 426/426 en la JVM y en el teléfono |
| SC-004 primer token p95 ≤ 5 s | ⚠️ primera medida 5.205 ms (+205 ms). Consulta: se mide otra vez con el detalle por pregunta |
| SC-005 detener en < 1 s con el texto parcial | ✅ JVM, durante buscando y generando |
| SC-006 conversación y posición tras una cita | ✅ JVM (`NavigationTest`, `ChatScreenTest`); el fragmento citado se ve sin desplazarse, en vertical y en horizontal (`DocumentScreenTest`) |
| SC-007 recuperación tras matar el proceso | ✅ JVM y `SqliteChatRepositoryTest`. Recorrido: _pendiente del teléfono_, porque sin Gemma el emulador no puede crear conversaciones |
| SC-008 avisos una vez y accesibilidad | ✅ `ChatViewModelTest`, `ChatScreenTest` y ATF en todos los estados |
| SC-009 compositor visible con el teclado | ✅ con menos alto (JVM) y con el teclado real en el teléfono |
| SC-010 registros sin texto y sin red | ✅ `DefaultChatSessionTest`, `NetworkConfinementTest`, `AppLoggerTest` |
| SC-011 nunca dos motores en la GPU; Gemma se libera con la UI oculta | ✅ `ChatDuringIndexingTest`, con el coordinador real |
| SC-012 negativa sin bloques en < 1 s, sin el modelo | ✅ JVM |
| SC-013 captura igual al PNG | _pendiente del teléfono_ |
| SC-014 revisión de la plantilla registrada | ✅ D-056 |

## 4. Cifras medidas

### Revisión de la plantilla (D-056, escritorio, 370 preguntas juzgadas a mano)

| Plantilla | Cambiadas | Correctas | Negativas | Citas | Adoptada |
|---|---:|---:|---:|---:|---|
| informe | — | 78,4 % | 96,4 % | 94,2 % | — |
| v3 | 225 | 80,0 % | 94,6 % | 93,1 % | no: +1,6 puntos |
| **v4** | 220 | **81,1 %** | **91,1 %** | **94,3 %** | **sí** |

### Golden de respuestas

- 426 preguntas, 50 de seguimiento, con la plantilla `v4`; 2,76 MB.
- El prompt más largo tiene 1.801 tokens estimados, por debajo del límite de 2.000.

### Recorrido en el emulador (T064)

Sin Gemma en el emulador, el chat solo puede mostrar el bloqueo de modelos (C5):
- **«Completar configuración»** abre la Preparación, y Atrás vuelve al chat;
- **«⋯»** ofrece solo «Nuevo chat», porque no hay conversación guardada;
- **el compositor** está desactivado;
- **la ruta de «Nuevo chat»** (`new-chat` en el menú de depuración) abre un chat nuevo;
- **el registro filtrado a `MicroRAG`** solo trae cifras (apertura del índice y de `chat.db`).

**Hallazgo en horizontal**, corregido con su prueba:
- con la barra inferior de la app solo quedaban unos 95 dp entre la barra superior y el compositor;
- sin conversación, el bloque C5 solo dejaba ver el borde de su icono;
- con conversación, el aviso tapaba la lista entera, que se quedaba en 0 px.

### Teléfono de referencia

**Primera medida (T067),** con el teléfono enchufado, la batería del 40 % al 32 % y a 36–38 °C; 24 minutos generando sin pausa:

| Cifra | Valor |
|---|---:|
| Preguntas medidas | 50 (10 de seguimiento) |
| Backend de Gemma | GPU |
| Primer token, mediana | 4.168 ms |
| **Primer token, p95** | **5.205 ms** (límite 5.000) |
| Primer token en frío (Gemma sin cargar) | 15.959 ms |
| Tokens estimados del prompt, mediana y p95 | 1.292 y 1.627 |
| Tiempo total, mediana y p95 | 11.085 y 28.340 ms |
| Pico de memoria (RSS) | 3.201 MB |
| Negativas | 4 de 50 |
| Respuestas sin cita | 1 de 50 |

- **Velocidad de generación:** salió vacía. El motor solo la da con el indicador de diagnóstico, que la app nunca activa. La prueba la estima ahora a partir de la respuesta guardada.
- **Exceso pequeño:** con 50 medidas, el p95 es la 48.ª, así que solo 3 preguntas llegan a 5,2 s. Siguiendo FR-045 se paró y se consultó. El usuario pidió medir otra vez con una línea por pregunta y el teléfono cargado y frío antes de decidir.
- _Segunda medida: pendiente._

## 5. Diferencias frente al mockup

- **Asistente:** «MicroRAG» en lugar de «LISDATA» (D-014). TalkBack lo lee «MicroRAG», no las mayúsculas del antetítulo.
- **Fichas `[n]` dentro del texto** (G11, D-053): el PNG no las tiene. Se numeran por orden de primera cita, igual que las filas.
- **Pie de la tarjeta:**
  - «Copiar respuesta» se ve desactivado mientras se genera;
  - «Reintentar» y el chip pueden salir juntos (respuesta interrumpida);
  - si no caben, bajan de línea.
- **Estados sin mockup:** las propuestas C1–C17 aprobadas.
- **Horizontal en un teléfono** (menos de 480 dp de alto), donde la disposición aprobada no cabe:
  - los estados sin conversación pierden el icono grande y parte del margen, así que el título y su acción salen primero;
  - con conversación, el aviso pasa a ser el último elemento de la lista, abierto por su título, en lugar de ir fijo sobre el compositor.

  En vertical no cambia nada.
- _Captura del teléfono frente al PNG: pendiente (SC-013)._

## 6. Desviaciones respecto al plan y fallos encontrados

### Decisiones de implementación

1. **La sesión se escribió entera en T031**, por ser una sola clase. Las tareas de sesión de US2–US6 se cumplieron con sus pruebas.
2. **Nombres de ficheros:**
   - los datos de muestra están en `ChatPreviews.kt`, porque `DesignSystemConformanceTest` solo admite textos fijos en `*Previews.kt`;
   - `ChatRoute` está en `ChatScreen.kt`, como en las demás pantallas.
3. **«Reintentar» desactivado** (no oculto) cuando no puede empezar, como pide T043. La tarjeta recibe `retryEnabled`.
4. **Componentes de estado:** `LisdataErrorState` admite no tener «Reintentar» (C17), y `LisdataEmptyState` no tener cuerpo (C4), como lista el contrato §2.
5. **La lista abre en la última respuesta.** U7 no lo decía. Al volver de una fuente o al recrear, conserva la posición.
6. **Lectura de las fichas (U3):** se leen como enlace con su número, porque `LinkAnnotation` no admite etiqueta propia. La frase completa («Fuente N: documento, página P») está en su fila. ATF no las marca, así que no hizo falta la consulta del riesgo 4.
7. **`ChatDuringIndexingTest`** simula la tanda con las llamadas al coordinador de `ChunkEmbeddingSession.use`. La sesión de indexación real pediría su almacén y la comprobación de GPU, que no aportan nada aquí.
8. **`ChatBenchmarkTest`** lee los modelos instalados en solo lectura (`InstalledModels`). Toma el backend de Gemma del registro de la Preparación, sin calcular hashes ni escribir nada.
9. **Golden:** 2,76 MB, más de lo estimado (≈ 1,6 MB).
10. **Gemma no está en el emulador:** tiene 3,2 GB libres, y el modelo (2,6 GB) más su caché compilada no caben. El camino completo con Gemma (respuesta real, detener, copiar, cita y negativa en pantalla) se ejecuta por primera vez en el teléfono.
11. **Observación previa, no de F11:** con la ruta de depuración y la preparación sin terminar, al girar la pantalla la app vuelve a la Preparación. La ruta de depuración solo se lee al crear la actividad. En release no ocurre; no se ha tocado.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| Enviar justo después de escribir leía el estado combinado anterior y no enviaba | `ChatViewModelTest` | Enviar lee la pregunta escrita |
| «Copiar respuesta» quedó oculto durante el streaming al leer mal el contrato | Revisión frente a FR-024 y T043 | Vuelve a verse desactivado |
| Una conversación que no se pudo guardar reutilizaba el id 0: «⋯» ofrecía borrarla, y la siguiente pregunta nunca se guardaba | Revisión de C16 | Se trata como no guardada, y la sesión la vuelve a crear |
| Los estados de disponibilidad sin conversación llevaban doble margen | Revisión del contrato §2 | Sin margen propio |
| **La app se cerraba con la primera respuesta de Gemma.** El patrón de citas `(?U)\[…]` compila en la JVM, pero el motor de Android (ICU) rechaza `(?U)`. Ni las pruebas JVM ni el emulador, que no tiene Gemma, leían una respuesta en un dispositivo | Teléfono, `ChatBenchmarkTest` (T067) | `91ad50b`: las citas se leen carácter a carácter con las clases de Python de `PyText`, iguales en los dos motores. `AnswerGoldenInstrumentedTest` pasa el golden de respuestas en el dispositivo |
| `ChatBenchmarkTest` recorría el registro de la prueba mientras la sesión escribía en él (`ConcurrentModificationException`). En el emulador, con 3 preguntas, no saltaba | Teléfono (T067) | `5a810e9`: lee copias |
| En horizontal, el estado sin conversación quedaba casi oculto y el aviso tapaba la conversación | Recorrido en el emulador (T064) | Disposición compacta por debajo de 480 dp de alto, con dos pruebas que reproducen la barra inferior |

## 7. Pendiente

- **Teléfono (T066–T070):**
  - instrumentadas;
  - `ChatBenchmarkTest` (SC-004; si el p95 pasa de 5 s, se para y se consulta);
  - `ChatScreenLayoutTest`;
  - recorrido, consultando antes cómo hacerlo, porque la biblioteca tiene un documento del usuario;
  - cierre.
