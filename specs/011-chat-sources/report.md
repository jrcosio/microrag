# Informe de cierre — F11 Chat con fuentes

> **Estado:** implementada; pendiente de la revisión de cierre.
> - T001–T070 hechas.
> - **Primer token:** no cumple los 5 s y queda como excepción documentada (D-057).
> - **Fallos de la fase del teléfono:** tres, corregidos (§6).

**Rama:** `011-chat-sources`.
**Fecha:** 2026-10-30; fase del teléfono del 2026-10-30 al 2026-11-01.
**Dispositivos:**
- emulador `Pixel_10`, API 37, ARM64;
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35).

**Versiones:**
- app 0.1.0 (versionCode 1);
- LiteRT-LM 0.18.1 y SQLite-Vector 1.1.2. Durante el cierre, lint avisó de que ya existe LiteRT-LM 0.19.0. No se ha actualizado: hay que verificarla en Maven y decidirlo aparte;
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
| Pruebas Python (`unittest`) | ✅ 152/152 (F10: 131), con `within_limit` |
| `gen_answer_golden.py --check` | ✅ al día |
| `assembleDebug`, `assembleRelease` | ✅ |
| `lintDebug`, `lintRelease` | ✅ 0 errores; 1 aviso nuevo y ajeno a F11: LiteRT-LM 0.19.0 disponible (`GradleDependency`) |
| Tests JVM | ✅ 818/818 (F10: 657) |
| `apkanalyzer dex packages` del APK de release | ✅ de `microrag.debug` solo están los 3 stubs vacíos de `src/release` (`DebugModules`, `DebugRouting` y `DebugStrictMode`); nada de `catalog`, `feasibility`, `retrieval` ni `routes` |
| `ChatScreenAccessibilityTest` en el emulador | ✅ 156/156 |
| `CatalogAccessibilityTest` en el emulador | ✅ 78/78 |
| Instrumentados en el emulador (sin `@RequiresModels` ni `@RequiresNetwork`) | ✅ 657/657 (F10: 490), en 18 min; repetida con los APK finales tras el arreglo de T064: 657/657 |
| Pruebas con modelos en el emulador | ✅ `RetrievalParityTest` sigue igual con `CorpusIndex` (0,837 frente a 0,837 del escritorio); `ChatBenchmarkTest` se salta, porque el emulador no tiene Gemma |
| Instrumentados en el teléfono, con los APK finales | ✅ **660/660** en 18 min (con `AnswerGoldenInstrumentedTest`) |
| Instrumentados en el teléfono, primera vez | ✅ 657/657 en 16,5 min. Una primera ejecución perdió la salida por la conexión adb; en ella falló una vez `CatalogAccessibilityTest` (control de menos de 48 dp). No se reproduce: sola, 78/78, y en la batería completa, en verde |
| `AnswerGoldenInstrumentedTest` (nueva, golden de respuestas en el dispositivo) | ✅ 3/3 en el teléfono: 426 prompts por SHA-256, lectura de citas y fichas |
| `ChatScreenLayoutTest` en el teléfono | ✅ con el teclado real y la barra inferior |

## 3. Validación por historia y criterio

### Historias

| Historia | Evidencia | Resultado |
|---|---|---|
| **US1** Preguntar y recibir una respuesta con fuentes | `DefaultChatSessionTest`, `EngineAnswerEngineTest`, `AnswerGoldenTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest`, `DocumentScreenTest` | ✅ JVM y recorrido en el teléfono (§4): respuesta real con 3 fuentes, ficha → fragmento resaltado → Atrás |
| **US2** Negativa | `DefaultChatSessionTest`, `ChatViewModelTest`, `ChatScreenTest` | ✅ JVM y teléfono: la negativa del modelo sale con el texto del MD, sin fuentes |
| **US3** Detener, copiar y reintentar | ídem | ✅ JVM y teléfono: detener conserva el texto parcial; reintentar completa la respuesta interrumpida |
| **US4** Seguimiento | `DefaultChatSessionTest`: las 50 preguntas de seguimiento del golden, pasando por la sesión, dan el prompt del escritorio | ✅ JVM y teléfono (`follow_up=true` en el registro) |
| **US5** Conversación guardada | `SqliteChatRepositoryTest` (emulador), `DefaultChatSessionTest`, `ResolveSourcesUseCaseTest`, `ChatViewModelTest`, `ChatScreenTest`, `NavigationTest` | ✅ |
| **US6** Chat bloqueado o limitado | `ObserveChatAvailabilityUseCaseTest`, `ChatDuringIndexingTest`, `ChatViewModelTest`, `ChatScreenTest` | ✅ JVM y teléfono: con una tanda en marcha, «Se están indexando tus documentos» y el compositor desactivado |
| **US7** Fiel al PNG y accesible | `ChatScreenTest`, `ChatScreenAccessibilityTest`, `CatalogAccessibilityTest`, `ChatScreenLayoutTest` | ✅ emulador y teléfono (teclado real en el A063) |
| **US8** Primer token | `ChatBenchmarkTest` | ⚠️ medido y por encima de 5 s: excepción documentada (D-057, §4) |
| **US9** Revisión de la plantilla | `template-review`, D-056 | ✅ |

### Criterios de éxito

| Criterio | Resultado |
|---|---|
| SC-001 fuentes solo del contexto | ✅ `AnswerReadingTest`, `AnswerFormatterTest` y el golden: números fuera de rango, repetidos y formatos raros |
| SC-002 prompt idéntico al del escritorio | ✅ 426/426 por SHA-256 en la JVM y en el teléfono, y las 50 de seguimiento también a través de la sesión |
| SC-003 lectura de citas y negativa como el escritorio | ✅ 426/426 en la JVM y en el teléfono |
| SC-004 primer token p95 ≤ 5 s | ⚠️ no se cumple: p95 de 5.205 ms y, en frío, de 5.826 ms. Excepción documentada (D-057); las palancas pasan a F14 |
| SC-005 detener en < 1 s con el texto parcial | ✅ JVM, durante buscando y generando; en el teléfono, «Generación cancelada» con el texto parcial |
| SC-006 conversación y posición tras una cita | ✅ JVM (`NavigationTest`, `ChatScreenTest`); el fragmento citado se ve sin desplazarse, en vertical y en horizontal (`DocumentScreenTest`). En el teléfono, la ficha abre el detalle con el fragmento resaltado en pantalla, y Atrás vuelve al chat con las mismas posiciones |
| SC-007 recuperación tras matar el proceso | ✅ JVM y `SqliteChatRepositoryTest`. En el teléfono, `am force-stop` a mitad de una respuesta: al volver, las respuestas anteriores siguen y la cortada sale con su texto parcial, «La respuesta se interrumpió» y «Reintentar» |
| SC-008 avisos una vez y accesibilidad | ✅ `ChatViewModelTest`, `ChatScreenTest` y ATF en todos los estados |
| SC-009 compositor visible con el teclado | ✅ con menos alto (JVM) y con el teclado real en el teléfono, en vertical, en horizontal y con la letra al 200 % (§4) |
| SC-010 registros sin texto y sin red | ✅ `DefaultChatSessionTest`, `NetworkConfinementTest`, `AppLoggerTest`. En el teléfono: 174 líneas de la app en el recorrido, solo cifras e ids; el contador de red de su uid sigue sin tráfico desde el arranque |
| SC-011 nunca dos motores en la GPU; Gemma se libera con la UI oculta | ✅ `ChatDuringIndexingTest`, con el coordinador real. En el teléfono: Gemma se carga después de liberar EG2 y se libera al ocultar la app |
| SC-012 negativa sin bloques en < 1 s, sin el modelo | ✅ JVM |
| SC-013 captura igual al PNG | ✅ con las diferencias de §5 (captura del teléfono en claro) |
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

**Segunda medida,** con el teléfono cargando (del 74 % al 82 %), con una línea por pregunta:

| Tramo | Mediana | p95 | Por encima de 5 s |
|---|---:|---:|---:|
| 30 primeras (frío, 33–40 °C) | 4.419 ms | 5.826 ms | 12 |
| 20 últimas (42 °C, estado térmico «moderado») | 5.574 ms | 6.671 ms | 13 |
| Las 50 | 4.969 ms | 6.638 ms | 25 |

| Otras cifras de la segunda medida | Valor |
|---|---:|
| Primer token en frío (Gemma sin cargar) | 15.296 ms |
| Búsqueda, mediana y p95 | 239 y 369 ms |
| Velocidad de generación estimada, mediana (frío / caliente) | 9,4 / 7,8 tokens/s |
| Tiempo total, mediana y p95 | 11.587 y 24.954 ms |
| Pico de memoria (RSS) | 4.758 MB |
| Negativas y respuestas sin cita | 4 y 1 de 50 |

- **Qué pesa:** en frío, el primer token ≈ 0,27 s + 3,1 s por cada 1.000 tokens estimados del prompt (r = 0,85). Casi todo es el prefill de Gemma; los seguimientos son los más lentos (mediana de 5,1 s).
- **Límite de 1.500 tokens,** evaluado en el escritorio como D-056 (`run_eval.py limit-review`):
  - cambian 65 prompts y 49 respuestas, juzgadas a mano;
  - respuestas correctas 80,5 % (`v4` da 81,1 %), negativas 91,1 % y citas 93,8 %;
  - en el teléfono, el p95 en frío bajaría solo a unos 5,3 s.
- **Decisión (D-057):** el usuario mantiene el límite de 2.000 tokens y deja el p95 como excepción documentada. Las palancas pasan a F14. `ChatBenchmarkTest` informa `sc004=met|exceeded` sin fallar.

### Recorrido del chat en el teléfono (T069)

Hecho como acordó el usuario:
- **Documentos:** tres documentos públicos del corpus (Ley de Arrendamientos Urbanos, Ley de Propiedad Horizontal y Ley de trabajo a distancia) en una carpeta propia, importados con el selector.
- **Ajustes temporales:** giro, letra al 200 % y modo claro, restaurados al final.
- **Privacidad:**
  - capturas sin la barra de estado;
  - cada pantalla del chat se volcaba primero con un filtro que no imprime nada si aparece una fuente que no es de prueba;
  - los volcados van a `/data/local/tmp` y se borran al momento.

| Paso | Resultado |
|---|---|
| Chat con una tanda en marcha | «Se están indexando tus documentos», con «Ver indexación» y el compositor desactivado (C14) |
| Primera pregunta (derechos del teletrabajo) | «Generando respuesta…» con «Detener»; respuesta con 3 fuentes. Búsqueda 0,79 s (embedding en frío) y primer token 6,9 s, el primero tras cargar Gemma |
| Ficha `[1]` → detalle → Atrás | El fragmento citado sale resaltado en pantalla. Atrás vuelve al chat en la misma posición |
| Copiar respuesta | Copia; en Android 13+ confirma el sistema, como prevé el contrato |
| Seguimiento («Y quién paga los gastos…») | `follow_up=true`, 1 fuente, primer token 4,3 s |
| Detener a mitad | Texto parcial con viñetas y «Generación cancelada»; `outcome=cancelled` |
| Pregunta sin respuesta (ajedrez) en un chat nuevo | La negativa del modelo sale con el texto del MD, sin fuentes |
| `am force-stop` a mitad de una respuesta | Al volver: turnos anteriores intactos, la respuesta cortada con su texto parcial, «La respuesta se interrumpió» y «Reintentar». Reintentar la completa con 1 fuente |
| App oculta (Inicio) | Se liberan Gemma y el modelo de consulta; al volver al chat, Gemma se carga otra vez |
| Horizontal | Disposición correcta. Con el teclado flotante de Gboard (no ocupa insets), la barra inferior se oculta y el compositor sigue visible |
| Letra al 200 % | Vertical: título en dos líneas y «Bibliot…» en la barra inferior (pendiente de F15). Horizontal: la lista solo deja ver una línea de la conversación |
| Modo oscuro | Correcto |
| «⋯» → Borrar conversación | Diálogo de confirmación; borra la conversación |

- **Fallo encontrado y corregido:** si el chat está abierto cuando termina la tanda, Gemma no se precargaba, y la primera respuesta pagaba la carga en frío.
  - Corregido en `3782824`, con su prueba.
  - Comprobado en el teléfono: Gemma se carga 6,5 s después de terminar la tanda, sin salir del chat.
- **Conversación del usuario:** la pestaña Chat abrió una conversación guardada del usuario, con su documento. No se volcó ni se capturó, y el filtro la detectó.
  - El recorrido usó «Nuevo chat».
  - Las dos conversaciones de prueba se borraron con «Borrar conversación», y la del usuario sigue intacta.
- **Limitación:** `adb input text` no escribe tildes ni «¿», así que las preguntas van sin ellos.
- **Limpieza:**
  - documentos de prueba borrados desde la app;
  - carpeta y entradas del escáner borradas;
  - ajustes restaurados (`accelerometer_rotation=1`, `user_rotation=0`, `font_scale=1.0`, modo oscuro);
  - el fichero `/sdcard/ui.xml` de otra app, que ya estaba en el teléfono, no se tocó.

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
- **Captura del teléfono frente al PNG** (SC-013, en claro):
  - coinciden la estructura, los textos fijos, los colores y la tipografía: barra superior, subtítulo con el punto verde, burbuja, tarjeta «MICRORAG», pie con «Copiar respuesta» y el chip, «FUENTES CONSULTADAS» con sus filas, compositor y barra inferior;
  - **botón de enviar:** con la caja vacía se ve atenuado (desactivado); en el PNG está azul;
  - **forma de la respuesta:** depende del modelo. Párrafos con fichas en la respuesta de derechos del teletrabajo, y viñetas en la de obligaciones del arrendador y el arrendatario;
  - **filas de fuente:** «Página N · Fragmento M» como en el PNG; sin página en los Markdown, como en F08.

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
12. **`ChatBenchmarkTest` en la fase del teléfono:**
    - escribe una línea `chat-benchmark-q` por pregunta, solo con cifras, y estima la velocidad de generación a partir de la respuesta guardada, porque el motor solo la da con el indicador de diagnóstico;
    - con D-057, informa `sc004=met|exceeded` en lugar de fallar por el p95.
13. **Revisión del límite del prompt** (`run_eval.py limit-review`): porta a Python la regla A6 de la sesión (`within_limit`, con pruebas) y queda en el arnés para F14.

### Fallos encontrados y corregidos

| Fallo | Dónde | Corrección |
|---|---|---|
| Enviar justo después de escribir leía el estado combinado anterior y no enviaba | `ChatViewModelTest` | Enviar lee la pregunta escrita |
| «Copiar respuesta» quedó oculto durante el streaming al leer mal el contrato | Revisión frente a FR-024 y T043 | Vuelve a verse desactivado |
| Una conversación que no se pudo guardar reutilizaba el id 0: «⋯» ofrecía borrarla, y la siguiente pregunta nunca se guardaba | Revisión de C16 | Se trata como no guardada, y la sesión la vuelve a crear |
| Los estados de disponibilidad sin conversación llevaban doble margen | Revisión del contrato §2 | Sin margen propio |
| **La app se cerraba con la primera respuesta de Gemma.** El patrón de citas `(?U)\[…]` compila en la JVM, pero el motor de Android (ICU) rechaza `(?U)`. Ni las pruebas JVM ni el emulador, que no tiene Gemma, leían una respuesta en un dispositivo | Teléfono, `ChatBenchmarkTest` (T067) | `91ad50b`: las citas se leen carácter a carácter con las clases de Python de `PyText`, iguales en los dos motores. `AnswerGoldenInstrumentedTest` pasa el golden de respuestas en el dispositivo |
| `ChatBenchmarkTest` recorría el registro de la prueba mientras la sesión escribía en él (`ConcurrentModificationException`). En el emulador, con 3 preguntas, no saltaba | Teléfono (T067) | `5a810e9`: lee copias |
| Con el chat abierto al terminar una tanda, Gemma no se precargaba: la primera respuesta pagaba la carga en frío (≈ 15 s). La precarga solo se pedía en ON_START | Recorrido en el teléfono (T069) | `3782824`: el chat carga Gemma siempre que está listo en pantalla, entre ON_START y ON_STOP, y nunca oculto |
| En horizontal, el estado sin conversación quedaba casi oculto y el aviso tapaba la conversación | Recorrido en el emulador (T064) | Disposición compacta por debajo de 480 dp de alto, con dos pruebas que reproducen la barra inferior |

## 7. Pendiente

- **Revisión de cierre del usuario.**
- **TalkBack real** en el chat, a cargo del usuario, como en F01, F03 y F06–F10.
- **Emulador:** la última batería completa en el emulador (657/657) es anterior a los tres arreglos de la fase del teléfono. Con los APK finales se repitió en el teléfono (660/660) y en la JVM (818/818).
- **Observaciones para otras features:**
  - con la letra al 200 % en horizontal, la lista del chat deja ver una sola línea, porque la barra inferior de la app ocupa mucho (F15);
  - LiteRT-LM 0.19.0 está disponible (decisión aparte);
  - el primer token en F14 (D-057).
