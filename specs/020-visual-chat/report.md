# Informe de cierre — F20 Chat con páginas (modo «Multimodal» opcional)

> **Estado:** fase del teléfono hecha (T058); falta la revisión de cierre del usuario (T059).

**Rama:** `020-visual-chat`, creada desde `4bb1d3f`.
**Fecha:** 2026-11-05 (teléfono, 2026-11-06).
**Dispositivos:**
- emuladores `Pixel_10` (API 37) y `MicroRAG_API33` (API 33);
- teléfono de referencia Nothing Phone (1) A063, Android 15 (API 35), en la última fase.

**Versiones:**
- app 0.1.0 (versionCode 1), sin dependencias nuevas;
- `chat.db` v2 → **v3**; `rag.db` sigue en la v5 de F19;
- constitución 1.2.0, sin enmienda (D-083);
- lint avisa de las dos versiones más nuevas de siempre, LiteRT-LM 0.19.0 y SQLite-Vector 1.2.0. No se actualizan en F20.

## 1. Qué se ha implementado

**Recuperación y contexto** (research R1–R4):
- **Cuándo hay páginas:** solo con el modo en `ON`, el espacio de páginas del índice compatible y alguna página en el ámbito. La sesión lo lee al empezar cada respuesta, con el detalle.
- **Búsqueda de páginas:** `RagIndex.pageSearch`, un barrido completo de `pages` con el mismo vector de la consulta, filtrado por ámbito (F17) y por buscables, con 5 candidatas. Corre en paralelo con la búsqueda vectorial de texto y no entra en la RRF (D-076).
- **Contexto:** los bloques de D-034 sin tocar, con el límite de 2.000 tokens aplicado a la v4 como siempre, más la primera página, la `[k + 1]`. Sin regla de duplicados (D-078). Sin bloques pero con página, se responde con la página. Sin nada, la negativa sin llamar a Gemma.
- **Plantilla v5-visual:** `PromptBuilder.buildVisual`, el port exacto de `visual_prompt.build_parts`, comprobado con una golden del escritorio de 12 casos (`visual_prompt_golden.jsonl`).
- **La imagen:** el JPEG guardado por F19. Si falta o no empieza por `FF D8`, la página se quita y la respuesta sigue con la v4.

**Motor** (research R5, R6; D-085):
- Gemma con la visión en CPU, 8.192 tokens y 1 imagen, **solo para un prompt con página**, con su propia clave (`VISION_GENERATOR`). Sin página, la Gemma de hoy. La clave sale del tipo de prompt.
- El coordinador nunca deja dos Gemma cargadas, tampoco en CPU, y el codificador de visión se libera con Gemma.
- La precarga usa la Gemma con visión si el modo está activo y hay documentos buscables con páginas.
- El generador de F18 pasa a `LiteRtTextGenerator`; el diagnóstico de F18 lo usa con un adaptador.

**Citas y Historial** (research R8–R10):
- Las fuentes llevan su tipo: fragmento, página o imagen suelta. `chat.db` v3 añade `sources.kind` y `turns.page_tokens`. Las conversaciones de antes se abren igual.
- En el Historial, una página que ya no está es `PAGE_GONE`: se puede pulsar y abre el documento con el aviso. Un documento borrado sigue diciendo «Fuente ya no disponible». Con el modo apagado, las páginas guardadas se siguen viendo.

**Pantallas** (wireframe §1–§10):
- **Ajustes:** «Detalle de las páginas · Equilibrado · unos 8,5 s» bajo «Modo al importar PDF», con su pantalla de tres opciones y sus esperas. Las esperas se escriben como el idioma escribe los números.
- **Chat:** «Leyendo 1 página · unos 8,5 s…» hasta el primer token, con TalkBack anunciando «Leyendo 1 página». Las fuentes de página llevan miniatura (`LisdataPageThumbnail`) y dicen «Página 4 · Imagen de la página», o «Imagen» una imagen suelta. La marca `[n]` y la tarjeta abren la página.
- **Detalle:** la página citada sustituye al fragmento resaltado, con su chip, un anillo mientras carga y «Toca para ampliar».
- **Visor a pantalla completa:** zoom de 1× a 4× con pellizco, doble toque y arrastre sin salirse de los bordes, y las acciones «Ampliar», «Reducir» y «Restablecer» para TalkBack.
- **Notas de F19:** con el modo activo, un documento solo con páginas ya es «Disponible para consultas RAG».
- **Diagnóstico de búsqueda (debug):** las listas de vectores y de palabras por separado, la fusionada y, con el modo activo, las páginas y la que entra.

## 2. Build, lint y tests

| Prueba | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | 1.355 de 1.355 (70 más que al cerrar F19), con la prueba del visor de T058 |
| Lint (`lintDebug`) | 0 errores. Los 2 avisos de versiones de siempre |
| Escritorio (Python) | 288 pruebas OK, con la golden de la v5-visual al día |
| Regresión del texto (`run_eval.py ablations` y `order-review`) | Mismas cifras que en F19, sin contar los segundos |
| `Pixel_10` (API 37), instrumentadas | 1.029 (31 más que en F19), en 22,4 min, sin `@RequiresNetwork`. Dos fallos de pruebas que no esperaban F20 (la versión 3 de `chat.db` y el recuento `withPages`), corregidos y repetidos en verde. Las que necesitan Gemma (`GemmaVisionSmokeTest`, `VisualChatBenchmarkTest`) se saltan: el emulador no la tiene |
| `MicroRAG_API33` (API 33), instrumentadas | 1.029, en 23,1 min: los 4 fallos previos de Atrás desde la raíz y los mismos dos de `Pixel_10`, corregidos y repetidos en verde |
| A063 (API 35), instrumentadas | 1.027, sin `@RequiresNetwork` ni las suites pesadas de F18. **Primera pasada (3 h 7 min):** 314 bien, 11 saltadas como siempre, y 702 fallos de pantalla: el teléfono se bloqueó con el PIN durante los benchmarks largos, que no tocan la pantalla («No compose hierarchies»). **Repetidas** con el teléfono desbloqueado y la pantalla encendida por USB, las 23 clases que fallaron (19 min): 708 de 710. Las 2 que quedaban (`NoTrafficInstrumentedTest` y `OnboardingNavigationTest`) son de F19: `ReadyModelsRule` no preparaba la 440M que pide la app con el modo activo. Corregida, 10 de 10 en el A063 y en `Pixel_10`. `GemmaVisionSmokeTest` y `VisualChatBenchmarkTest` pasan |
| Release | Ver `checklists/release-verification.md`: los mismos 9 permisos, sin los diagnósticos y con el chat con páginas en `main` |

## 3. Validación por criterio

| Criterio | Resultado |
|---|---|
| SC-001: sin modo o sin páginas, nada cambia | ✅ `ablations.json` y `order_review.json` idénticos a los de F19. `DefaultChatSessionTest`: el prompt es `AnswerPrompt.Text` idéntico con el modo apagado, con el modo activo sin páginas y con `pagesUsable = false`; el motor usa la clave de hoy (`EngineAnswerEngineTest`) |
| SC-002: ninguna cita sin fuente real | ✅ `DefaultChatSessionTest` (`[n]` fuera de rango, tipos de fuente), `ResolveSourcesUseCaseTest` (`PAGE_GONE`, `DOCUMENT_GONE`) y `RagIndexPageSearchTest` |
| SC-003: primer token por detalle, medido | ✅ En el A063 con 20 preguntas por variante: p95 de 6,7 s, 8,4 s y 14,4 s, y 5,0 s sin página (§4). Los textos de la app pasan a esas cifras (D-086) |
| SC-004: un escaneado y una imagen, respondidos con su página | ✅ En el A063 (§5): un PDF escaneado de 2 páginas y un cartel en JPG, en Visual en una carpeta de prueba. Cada respuesta cita su página («Página 1 · Imagen de la página» y «Imagen»), y la cita abre el detalle y el visor |
| SC-005: el Historial restaura las citas a página | ✅ `SqliteChatRepositoryTest` (migración 2 → 3 y fuentes de página), `ResolveSourcesUseCaseTest` y las pantallas |
| SC-006: nunca dos engines en GPU; la visión se libera con Gemma | ✅ `EngineCoordinatorTest` (nunca dos Gemma, en GPU ni en CPU) y `EngineAnswerEngineTest` (las liberaciones) |
| SC-007: nada privado en los registros | ✅ Las pruebas «SECRETO» del chat con página, del retriever y de las imágenes |
| SC-008: wireframe, TalkBack, 200 % y textos | ✅ Wireframe aprobado (D-083, D-085). `VisualChatAccessibilityTest`: 20 de 20 en `Pixel_10` y en API 33 (ATF, 48 dp, grupo de radios y 200 %, en claro y en oscuro). `StringsParityTest` en verde |

## 4. Cifras medidas

`VisualChatBenchmarkTest` en el A063, con 20 preguntas del corpus de evaluación por variante. Mide de `ask()` al primer token, con la búsqueda incluida, y cancela cada respuesta en su primer token. Las páginas son 12 tablas generadas e indexadas en Visual con la 440M. Gemma de texto está en GPU; la visión, en CPU.

| Variante | Primer token, p50 | p95 | Lo que dice la app |
|---|---|---|---|
| Modo activo, sin página (la Gemma de hoy) | 4.044 ms | 5.029 ms | — |
| Rápido (70 tokens) | 5.386 ms | 6.653 ms | unos 6,5 s |
| Equilibrado (140) | 7.654 ms | 8.392 ms | unos 8,5 s |
| Detallado (280) | 14.050 ms | 14.430 ms | unos 14,5 s |

- **La primera respuesta con página después de una sin ella** recarga Gemma con la visión: 14.180 ms hasta el primer token, unos 8,8 s más que una con Rápido ya cargada.
- **Pico de RSS:** 4.620 MB, con la 440M, Gemma y el codificador de visión cargados. F18 había medido 3,5–3,9 GB.
- **Frente a F18,** Rápido y Equilibrado salen bastante más rápidos (F18 midió un p95 de 9,8 s y 11,5 s, con la regla de duplicados y otras preguntas). Detallado queda igual. Los textos se corrigen con las cifras nuevas (D-086).
- **Sin página,** el p95 es de 5,0 s, frente a los 5,2 s que midió F11 (D-057). La nota «Las respuestas sin páginas no cambian» se mantiene.
- **Indexación visual,** de paso (`VisualIngestBenchmarkTest` de F19): 200 páginas a 14,1 por minuto, con 111 KB por página, igual que en F19.

## 5. Recorrido

Los emuladores no tienen Gemma, y activar el modo en ellos supone descargar la 440M. El recorrido real del chat con páginas se hace en el teléfono (T058), que ya tiene el modo activado (D-082).

En `Pixel_10`, con el modo apagado, Ajustes no muestra «Detalle de las páginas», y el resto queda como en F19. Las pantallas nuevas se recorren con las pruebas de pantalla y con `VisualChatAccessibilityTest`, que las dibuja en el dispositivo con imágenes reales, en claro, en oscuro y al 200 %.

**En el A063 (SC-004, 2026-11-06),** con dos ficheros generados para la prueba, copiados a `Download` y borrados al acabar. Solo se buscaron las etiquetas de la prueba, y no se abrieron ni capturaron los documentos ni las conversaciones del usuario.
1. **Importar:** desde la Ingesta, una carpeta nueva «Prueba F20» y los PDF en Visual. Un escaneado de 2 páginas sin capa de texto (el horario de una biblioteca) y un cartel en JPG (unas fiestas) se indexan solos, sin fallos.
2. **Chat nuevo,** con el ámbito en «Prueba F20». Mientras espera sale «Leyendo 1 página · unos 8,5 s…».
   - «¿A qué hora cierra la biblioteca entre semana?» → «La biblioteca municipal cierra a las 20:30 en lunes a viernes [1]», con la fuente «prueba-f20-escaneado.pdf · Página 1 · Imagen de la página». Primer token en 8,5 s, en la primera respuesta tras abrir la app, con el embedder en frío.
   - En un chat nuevo, «¿Cuándo es el concierto principal de las fiestas?» → «El concierto principal de las fiestas es el domingo 16 a las 22:30 [1]», con la fuente «prueba-f20-cartel.jpg · Imagen». Primer token en 5,1 s.
3. **La cita** abre el detalle con la página («Página 1 de prueba-f20-escaneado.pdf» e «Imagen prueba-f20-cartel.jpg») y «Toca para ampliar». El visor se titula «prueba-f20-escaneado.pdf · Página 1» o solo «prueba-f20-cartel.jpg», amplía con doble toque y se cierra con «✕».
4. **Al borrar los documentos,** sus fuentes pasan a «Fuente ya no disponible». Después se borraron las dos conversaciones y la carpeta.

**Lo que se vio en el teléfono:**
- **El visor ampliado tapaba la barra superior,** con el título y el «✕» (§8). Se corrigió y se comprobó en el teléfono.
- **La regla de seguimiento de F10 arrastra la página anterior.** En la misma conversación, «¿Cuándo es el concierto principal de las fiestas?» tiene menos de 8 palabras normalizadas. Se buscó junto con la pregunta anterior, entró otra vez la página de la biblioteca, y Gemma respondió bien «No encuentro información suficiente en tus documentos». No es una regresión: la regla es la de siempre, pero con páginas se nota más, porque entra una sola. Va a F14 (§9).

## 6. Diferencias frente al wireframe

- **§5, pista de la vista previa:** «Toca para ampliar» va sin el icono «⤢», que no está entre los iconos del design system.
- **§8, diagnóstico de debug:** los bloques del contexto salen antes de las listas, como hasta ahora, y no al final.
- **§2, opciones del detalle:** la descripción y la espera van en el mismo subtítulo, en un solo texto.

## 7. Desviaciones respecto al plan

- **Gemma con visión solo cuando entra una página** (D-085), y no siempre que el modo está activo como decía el primer plan (R5).
- **Precarga:** la decide `WarmUpAnswerEngineUseCase` con `ChatLibraryState.withPages`.
- **Las fuentes de imagen suelta** tienen su propio tipo (`image`), y no se deduce del documento al mostrarlas (analyze U2).
- **`PageFileStore`** no tenía `read`: se lee con `file(name)`.
- **`PageExistsUseCase`** decide en el detalle si la página citada sigue, en lugar de leer la imagen.
- **El benchmark** cancela cada respuesta en su primer token, que es lo que mide.

## 8. Fallos encontrados y corregidos

- `DesignSystemConformanceTest` vio `Text("…` en `PromptBuilder`: un `PromptPart.Text` con un literal. Se pasa por una variable.
- `NavigationTest` se quedaba en bucle porque al grafo de Koin de la prueba le faltaba `PageImageLoader`.
- Con los gráficos «legacy» de Robolectric no se puede crear un `ImageBitmap`: las pruebas que lo necesitan usan `@GraphicsMode(NATIVE)`.
- La prueba del límite de tokens con página no comprobaba nada: ahora compara las líneas de fuentes de la v4 con las de la v5-visual.

**En la fase del teléfono (T058):**
- **El visor ampliado tapaba la barra superior** con el título y el «✕», porque la caja de la página no recortaba. Ahora recorta (`66b8a6c`). La prueba nueva amplía una página roja a 4× y comprueba que la barra del título no tiene ningún píxel rojo y que «Cerrar» cierra. Comprobado también en el A063.
- **`ReadyModelsRule` (de F19) preparaba siempre la 270M y Gemma.** Con el modo activo, la app pide la 440M, y «Continuar» no terminaba la Preparación, así que `NoTrafficInstrumentedTest` y `OnboardingNavigationTest` fallaban en el A063. Ahora prepara lo que pide `ModelRequirements` en ese momento (`3dc1812`). No había salido antes porque la batería de F19 se pasó antes de activar el modo, y los emuladores lo tienen apagado.
- **`ChatViewModelTest` tenía escrita a mano la espera antigua (115)** y se corrigió con las cifras nuevas (D-086, `be4a728`).
- **La batería se quedó a medias porque el teléfono se bloqueó** durante los benchmarks largos. Las repeticiones mantienen la pantalla encendida con `svc power stayon usb`, y al acabar se vuelve a 0.

## 9. Pendiente

- **F14:**
  - el primer token con páginas (6,5–14,5 s medidos en F20) y las palancas para bajarlo; la recarga de Gemma con visión (14,2 s la primera respuesta con página tras una sin ella);
  - la regla de seguimiento de F10 con páginas (§5): una pregunta corta sobre otro documento arrastra la página de la anterior. Por ejemplo, buscar las páginas solo con la pregunta nueva;
  - el pico de RSS de 4,6 GB con la visión cargada;
  - las cifras deformadas con fp16;
  - cargar el motor de imagen una vez por run (F19).
