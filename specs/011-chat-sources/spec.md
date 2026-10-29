# Especificación de feature: Chat con fuentes (F11)

**Feature Branch**: `011-chat-sources`

**Created**: 2026-10-29

**Status**: Draft

**Input**: Descripción del usuario: «F11 — Chat con fuentes. Feature de producto: pantalla `04-chat.png` (manda el PNG) con `screens/04-chat.md` para los estados sin maqueta. El usuario pregunta a sus documentos y recibe en el teléfono, sin red, una respuesta generada con Gemma 4 E2B en streaming, con solo citas reales: cada una apunta a un documento y un fragmento existentes y abre el fragmento resaltado en el detalle del documento.» Ver `docs/roadmap/FEATURES.md` (F11).

**Fuentes**:
- Mockup (manda sobre el `.md`):
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/04-chat.png` y `docs/disegn_app/screens/04-chat.md`;
  - para el destino de las citas: `07-detalle-documento.png` y `07-document-detail.md`.
- `docs/disegn_app/docs/`:
  - `01-navigation.md`: restauración de la conversación activa; Chat sin modelo o sin documentos;
  - `02-architecture.md`: `Citation`, `ChatMessage`, `GenerationState` y `ChatRepository`;
  - `03-components.md`: ninguna cita sin un documento válido.
- `docs/roadmap/INVENTARIO.md`:
  - ficha P04 (textos del PNG frente al MD y frente al informe);
  - G11: citas en la respuesta;
  - G12: cancelar (enviar se convierte en detener);
  - G13: menú «⋯»;
  - G25: idioma de la respuesta;
  - G26: texto de la negativa;
  - G39: teclado;
  - componentes `LisdataChatBubble`, `LisdataAnswerCard`, `LisdataChatComposer`, `LisdataSourceCitation` y `LisdataModelMissingBlock`;
  - flujo 5.3 (cancelar, sin evidencia, cita → detalle → Atrás).
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - resumen ejecutivo: la latencia la domina el prefill del LLM (L23–58);
  - preguntas de seguimiento (L624–626);
  - Generación: modelo y runtime, plantilla de prompt y una conversación por pregunta (L733–797);
  - Memoria y ciclo de vida (L799–811).
- Constitución v1.1.1:
  - I: sin red; conversaciones en almacenamiento privado; logs sin contenido privado;
  - II: la UI no usa el runtime ni la base de datos directamente;
  - III: fuentes numeradas, citas `[n]`, negativa explícita y citas siempre trazables;
  - IV: nunca dos motores en la GPU; el LLM se libera durante la ingesta y al ocultarse la UI; primer token ≤ 5 s;
  - VI: pruebas unitarias del constructor de prompts;
  - VII: UI fiel al PNG, propuestas aprobadas para lo no maquetado, accesibilidad.
- DECISIONS:
  - D-007: el historial es F13;
  - D-014: el asistente se llama «MicroRAG»;
  - D-023: Gemma 4 E2B en GPU;
  - D-026: primer token ≤ 5 s con ≈ 2.000 tokens;
  - D-027: aviso «Modo CPU, más lento»;
  - D-029: español por defecto;
  - D-034: contexto y plantilla «informe»;
  - D-035: F11 trabaja la plantilla;
  - D-039 y D-040: el chat frente a la indexación; el LLM se libera al empezar la ingesta;
  - D-043 y D-045: la cita abre el fragmento entero resaltado;
  - D-046 y D-047: «Nuevo chat» desde Inicio, también sin la IA lista;
  - D-049, D-050 y D-052: la recuperación de F10 y su coste.
- Referencia de escritorio (F05):
  - `offline/eval/prompt.py`: plantilla, negativa y recortes del turno anterior;
  - `offline/eval/judge.py`: lectura de citas `[n]` y detección de la negativa;
  - sus pruebas en `offline/tests/`;
  - `offline/reports/eval-report.md`.
- Lo ya construido:
  - F01: ruta del chat, barra inferior que se oculta con el teclado y componentes del chat;
  - F03: modelos verificados y preparados; Gemma calentada en la GPU;
  - F07: tanda de indexación con pausa y causa;
  - F08: detalle del documento con el fragmento citado resaltado;
  - F09: «Nuevo chat» en Inicio;
  - F10: recuperación híbrida con resultado tipado.

**Verificado al redactar** (2026-10-29):
- **El chat hoy:** la ruta del chat existe, con un identificador de conversación opcional, pero muestra un marcador de posición. «Nuevo chat» (Inicio) abre una conversación nueva y la pestaña Chat restaura la anterior, solo en memoria.
- **Componentes ya hechos** (F01):
  - burbuja del usuario;
  - tarjeta de respuesta con avatar, estados de streaming, cancelado y error, «Copiar respuesta» y chip de fuentes;
  - compositor cuyo botón enviar pasa a detener;
  - fila de fuente con la variante «Fuente ya no disponible»;
  - bloqueo de modelo ausente.

  El cuerpo de la respuesta es texto plano: aún no hay viñetas ni marcas tocables.
- **Generación:** la app ya genera texto en streaming con Gemma. Abre una conversación nueva del runtime por llamada (como pide el informe), la cancelación detiene la generación y se mide el tiempo hasta el primer token. Hoy solo la usan la comprobación de compatibilidad de F03 y el diagnóstico de F02.
- **Motores:**
  - el LLM nunca se retiene y se libera al ocultarse la UI;
  - nunca hay dos motores en la GPU: cargar uno espera a que se suelte el otro;
  - la tanda de indexación libera el LLM al empezar y retiene su motor de embeddings hasta terminar.

  Con una tanda en curso, el chat esperaría hoy al final de la tanda. La constitución IV exige liberar el LLM durante la ingesta.
- **Persistencia:** no existe nada del chat. Solo el índice RAG y las preferencias (tanda de indexación, onboarding).
- **Detalle del documento:** abre un fragmento citado y lo resalta (D-043), pero no se desplaza hasta él; queda debajo de la cabecera, las cifras y el interruptor. Si el fragmento ya no existe, muestra el primero con un aviso (F08, D7).
- **Escritorio (F05, D-034 y D-035):**
  - prompt de 1.512 tokens de mediana y 1.994 en el p95, con 4 bloques;
  - 78,4 % de respuestas correctas, 96,4 % de negativas correctas y 94,2 % de citas correctas;
  - 11 negativas indebidas y 33 respuestas incorrectas o incompletas con el fragmento relevante en el contexto.
- **Primer token** (F02, D-026): 4,53 s en el motor y 4,75 s de reloj con 1.986 tokens en la GPU del teléfono de referencia. Arranque de Gemma con la caché del motor: ≈ 5,4 s. La recuperación de F10 añade ≈ 0,2 s (D-052).
- **Textos en conflicto:**
  - negativa: el informe usa «No encuentro esa información en los documentos disponibles.» y el MD «No encuentro información suficiente en tus documentos» (G26);
  - título y subtítulo: los del PNG («Chat con documentos», «Gemma 4 · Procesamiento local») mandan sobre el MD;
  - nombre del asistente: «MicroRAG» en lugar de «LISDATA» (D-014).

## Clarifications

### Session 2026-10-29

- Q: ¿Cómo se muestran las citas dentro de la respuesta (G11)? → A: Como el PNG (chip «N fuentes» y «FUENTES CONSULTADAS» con solo las fuentes citadas) y, además, cada `[n]` del texto como una ficha pequeña tocable que abre su fragmento. Las fichas se numeran según el orden de la lista (1 = primera fuente listada), no con el número del prompt.
- Q: ¿Qué pasa si el usuario usa el chat mientras se indexan documentos? → A: El chat espera a la tanda. Mientras una tanda está en marcha, el chat explica que podrá preguntar cuando termine, con acceso al Estado de indexación, y no carga Gemma (constitución IV sin cambios). Si la tanda está en pausa, el chat funciona con los documentos ya listos y el aviso de cobertura parcial.
- Q: ¿Qué se guarda de las conversaciones y qué abre la pestaña Chat? → A: Se guardan en el teléfono, sin copia de seguridad, todas las conversaciones con sus mensajes, estados y fuentes. La pestaña Chat abre la última, también tras cerrar la app, y «Nuevo chat» empieza otra. Las anteriores quedan guardadas para el historial de F13.
- Q: ¿Hasta dónde se trabaja la plantilla del prompt en F11 (D-035)? → A: Se evalúa en el harness de escritorio, como D-051 en F10. Se prueban 1 o 2 variantes con las mismas preguntas; se juzgan con la regla automática y se revisan las respuestas que cambian (método D-033). Se adopta una variante solo si sube ≥ 2 puntos las respuestas correctas sin bajar de 85 % en negativas ni de 90 % en citas, con una D nueva. Si no, se queda la plantilla «informe».
- Q: ¿Qué opciones tiene el menú «⋯» (G13)? → A: «Nuevo chat» y «Borrar conversación», con confirmación; tras borrar se abre un chat vacío. «Historial» se añade con F13.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Usuario:** pregunta a sus documentos importados e indexados.
- **Quien desarrolla:** mide el primer token y la calidad en el teléfono de referencia y en el escritorio.

### User Story 1 - Preguntar y recibir una respuesta con sus fuentes (Priority: P1)

El usuario escribe una pregunta y la envía. Ve primero «Buscando en documentos...» y después «Generando respuesta...», con la respuesta apareciendo según se genera. Al terminar, la tarjeta de «MicroRAG» muestra la respuesta, «Copiar respuesta» y el chip «N fuentes». Debajo aparece «FUENTES CONSULTADAS» con una fila por fuente citada: nombre del documento, página y fragmento. Tocar una fuente abre el documento con ese fragmento resaltado y a la vista, y Atrás vuelve al chat en el mismo punto.

**Why this priority**: es el propósito de la pantalla (MD 04: «mostrando siempre la procedencia verificable») y la razón de ser de la app.

**Independent Test**: con una biblioteca de prueba indexada y los modelos preparados, hacer una pregunta cuya respuesta está en un documento:
- se ven los dos estados y la respuesta crece;
- las fuentes listadas son exactamente las citadas en la respuesta, y cada una abre su documento con el fragmento resaltado y visible;
- Atrás devuelve el chat con la misma posición de desplazamiento.

**Acceptance Scenarios**:

1. **Given** documentos indexados y los modelos preparados, **When** el usuario envía una pregunta, **Then** su pregunta aparece como burbuja a la derecha y la tarjeta de respuesta pasa por «Buscando en documentos...» y «Generando respuesta...» antes de mostrar el texto completo.
2. **Given** una respuesta en curso, **When** llegan partes del texto, **Then** se añaden a la tarjeta sin esperar al final, y la vista sigue el texto nuevo salvo que el usuario se haya desplazado hacia arriba.
3. **Given** una respuesta que cita los bloques 1 y 3 del contexto, **When** termina, **Then** el chip dice «2 fuentes» y «FUENTES CONSULTADAS» lista solo esos dos, con su documento, página (si existe) y número de fragmento.
4. **Given** una respuesta con una marca `[7]` y solo 4 bloques en el contexto, **When** termina, **Then** esa marca se descarta y no aparece ninguna fuente para ella.
5. **Given** una fuente listada, **When** el usuario la toca, **Then** se abre el detalle de su documento con el fragmento citado resaltado y visible sin desplazarse.
6. **Given** el detalle abierto desde una cita, **When** el usuario pulsa Atrás, **Then** vuelve al chat con la misma conversación y la misma posición de desplazamiento.
7. **Given** una respuesta con listas, **When** se muestra, **Then** las listas aparecen como viñetas, como en el PNG, y no se ven marcas de formato en crudo (asteriscos, almohadillas).
8. **Given** una respuesta que cita los bloques 3 y 1 del prompt, en ese orden, **When** termina, **Then** sus marcas se ven como fichas «1» (bloque 3) y «2» (bloque 1), y tocar una ficha abre el mismo fragmento que su fila de la lista.

---

### User Story 2 - Saber cuándo los documentos no tienen la respuesta (Priority: P1)

Si los documentos no contienen la respuesta, el usuario ve «No encuentro información suficiente en tus documentos», sin fuentes. No se inventa una respuesta ni se fabrica ninguna cita.

**Why this priority**: la constitución III exige una negativa explícita y prohíbe fabricar citas. Es lo que hace fiable al asistente.

**Independent Test**:
- preguntar algo que no está en la biblioteca de prueba: se muestra el texto de la negativa, sin chip ni lista de fuentes;
- con una biblioteca en la que la búsqueda no devuelve nada, el modelo no llega a ejecutarse y la negativa aparece en menos de 1 s.

**Acceptance Scenarios**:

1. **Given** que el modelo responde con la frase de negativa del prompt, **When** termina, **Then** la tarjeta muestra «No encuentro información suficiente en tus documentos», sin chip de fuentes ni «FUENTES CONSULTADAS».
2. **Given** una pregunta para la que la búsqueda no devuelve ningún bloque, **When** se envía, **Then** no se ejecuta el modelo y se muestra directamente la negativa.
3. **Given** una respuesta que no es la negativa pero no tiene ninguna cita válida, **When** termina, **Then** se muestra el texto sin lista de fuentes y con el aviso propuesto (propuesta C2).
4. **Given** una pregunta vacía o solo con espacios, **When** el usuario intenta enviarla, **Then** el botón enviar no está activo.

---

### User Story 3 - Detener, copiar y reintentar (Priority: P1)

Mientras se genera, el botón enviar se convierte en detener. Detener para la respuesta y conserva el texto ya escrito, marcado como «Generación cancelada». Una respuesta terminada se puede copiar. Si algo falla, la tarjeta lo dice y ofrece «Reintentar».

**Why this priority**: FEATURES F11 (criterio 6) y el MD 04 lo exigen. Sin detener, una respuesta larga bloquea el chat durante decenas de segundos.

**Independent Test**:
- durante una respuesta, pulsar detener: el texto deja de crecer en menos de 1 s, queda el texto parcial con «Generación cancelada» y se puede enviar otra pregunta;
- copiar una respuesta y pegarla en otra app;
- provocar un fallo del motor: la tarjeta muestra el error y «Reintentar» vuelve a lanzar la misma pregunta.

**Acceptance Scenarios**:

1. **Given** una respuesta en «Buscando en documentos...» o «Generando respuesta...», **When** el usuario pulsa detener, **Then** la respuesta se detiene, el texto parcial (si lo hay) se conserva con la marca «Generación cancelada» y el compositor vuelve a enviar.
2. **Given** una respuesta cancelada con citas válidas en su texto parcial, **When** se muestra, **Then** sus fuentes se listan igual que en una respuesta completa.
3. **Given** una respuesta completa, **When** el usuario toca «Copiar respuesta», **Then** el portapapeles contiene la respuesta (contenido según la propuesta C10) y se confirma la copia.
4. **Given** un fallo de la búsqueda o del modelo, **When** ocurre, **Then** la tarjeta muestra el error con «Reintentar», y reintentar repite la misma pregunta con el mismo turno anterior.
5. **Given** una respuesta en curso, **When** el usuario intenta copiarla, **Then** «Copiar respuesta» no está disponible hasta que termina o se cancela.

---

### User Story 4 - Preguntas de seguimiento (Priority: P2)

El usuario puede seguir la conversación con preguntas cortas o encadenadas («¿y para empresas?»). La búsqueda usa la pregunta anterior según la regla de seguimiento de F10, y el prompt incluye la pregunta y la respuesta anteriores, recortadas.

**Why this priority**: es el uso natural de un chat y el informe lo resuelve sin coste de LLM extra, pero una pregunta suelta ya da valor.

**Independent Test**: preguntar «¿Qué plazo hay para devolver un producto?» y después «¿y para empresas?». La segunda respuesta usa fragmentos sobre empresas y devoluciones, y su prompt contiene el turno anterior recortado a 300 y 500 caracteres.

**Acceptance Scenarios**:

1. **Given** una conversación con una pregunta respondida, **When** el usuario envía otra, **Then** la búsqueda recibe la pregunta anterior y aplica la regla de seguimiento de F10.
2. **Given** un turno anterior, **When** se construye el prompt, **Then** incluye «CONVERSACIÓN PREVIA», con la pregunta anterior recortada a 300 caracteres y la respuesta anterior a 500, como el escritorio.
3. **Given** un turno anterior que terminó en negativa, cancelado o con error, **When** se envía la siguiente pregunta, **Then** el turno anterior se usa con la regla de la propuesta C12.
4. **Given** una conversación nueva, **When** se envía la primera pregunta, **Then** no hay turno anterior.

---

### User Story 5 - Conversación guardada en el teléfono (Priority: P2)

Las conversaciones se guardan en el teléfono. Al volver a la pestaña Chat, incluso tras cerrar la app, el usuario encuentra su última conversación con sus respuestas y fuentes. «Nuevo chat» empieza otra. Si una fuente se borró, su fila dice «Fuente ya no disponible» y no abre nada. Las conversaciones anteriores quedan guardadas para el historial de F13.

**Why this priority**: FEATURES F11 pide la persistencia local de los mensajes, y F13 (historial) se apoya en ella. El chat es útil aunque la conversación no sobreviva al cierre.

**Independent Test**:
- hacer dos preguntas, cerrar la app desde recientes y volver a abrir la pestaña Chat: se ven las dos preguntas con sus respuestas, estados y fuentes;
- borrar uno de los documentos citados: su fila pasa a «Fuente ya no disponible»;
- matar el proceso durante una respuesta y volver: el mensaje no sigue «generando».

**Acceptance Scenarios**:

1. **Given** una conversación con mensajes, **When** la app se cierra y se vuelve a abrir la pestaña Chat, **Then** se ve esa conversación con sus mensajes, estados y fuentes.
2. **Given** la conversación guardada, **When** el usuario toca «Nuevo chat» (Inicio o menú «⋯»), **Then** se abre una conversación vacía y la anterior sigue guardada.
3. **Given** una conversación abierta, **When** el usuario elige «Borrar conversación» en «⋯» y lo confirma, **Then** la conversación desaparece del teléfono y se abre un chat vacío. Si cancela, no cambia nada.
4. **Given** una cita a un documento borrado, **When** se muestra, **Then** su fila dice «Fuente ya no disponible» y no abre nada.
5. **Given** una cita a un documento que sigue existiendo pero cuyo fragmento ya no existe (reindexado o fuera del índice), **When** se toca, **Then** se abre el documento con el aviso de F08 «El fragmento citado ya no está en este documento.».
6. **Given** que el proceso muere a mitad de una respuesta, **When** se vuelve a abrir la conversación, **Then** ese mensaje aparece como interrumpido (propuesta C13), nunca como «Generando respuesta...».
7. **Given** una conversación sin ningún mensaje, **When** el usuario sale, **Then** no se guarda.

---

### User Story 6 - Chat bloqueado o limitado, con la salida a la vista (Priority: P2)

Si falta algo para poder responder, el chat lo dice y lleva a donde se arregla:
- modelos ausentes o sin preparar: acceso a la Preparación;
- biblioteca vacía: «Importar documentos»;
- documentos aún sin indexar: acceso al estado de la indexación;
- índice no disponible: error con «Reintentar».

Mientras una tanda de indexación está en marcha, el chat espera a que termine, y lo explica. Con documentos aún sin indexar, la respuesta avisa de que solo usa los ya listos. Si Gemma solo está preparada en CPU, el chat avisa de que irá más lento.

**Why this priority**: el MD 04 y D-046 lo exigen («Nuevo chat» sigue activo sin la IA lista), pero solo afecta a situaciones de borde.

**Independent Test**: recorrer cada situación (modelo borrado, biblioteca vacía, solo documentos pendientes, índice ilegible, tanda en curso y Gemma solo en CPU) y comprobar el texto, la acción y que el compositor está activo solo cuando se puede preguntar.

**Acceptance Scenarios**:

1. **Given** un modelo ausente, sin verificar o sin preparar, **When** se abre el chat, **Then** se ve el bloqueo de modelo ausente con acceso a la Preparación, y el compositor no envía (propuesta C5).
2. **Given** una biblioteca sin documentos, **When** se abre el chat, **Then** se ve el estado vacío con «Importar documentos», que abre la importación (propuesta C3).
3. **Given** documentos importados, pero ninguno indexado e incluido, y ninguna tanda en marcha, **When** se abre el chat, **Then** se ve el aviso de que no hay documentos listos, con acceso al Estado de indexación (propuesta C4).
4. **Given** el índice no disponible, **When** se envía una pregunta, **Then** la tarjeta muestra el error de índice con «Reintentar» (propuesta C8).
5. **Given** documentos incluidos pendientes de indexar o en indexación, **When** termina una respuesta, **Then** lleva el aviso de cobertura parcial (propuesta C6).
6. **Given** Gemma preparada solo en CPU (D-027), **When** se abre el chat, **Then** el subtítulo avisa del modo CPU (propuesta C9).
7. **Given** una tanda de indexación en marcha, **When** se abre el chat, **Then** se ve el aviso de espera con acceso al Estado de indexación, el compositor no envía y Gemma no se carga (propuesta C14).
8. **Given** el aviso de espera, **When** la tanda termina o el usuario la pausa, **Then** el chat queda disponible sin salir de la pantalla.

---

### User Story 7 - Un chat fiel al PNG y accesible (Priority: P2)

La pantalla se ve como el PNG 04 y se puede usar con TalkBack, con la fuente al 200 %, en horizontal y en modo oscuro. El teclado nunca tapa el compositor.

**Why this priority**: la fidelidad y la accesibilidad son obligatorias (constitución VII), pero dependen de que el contenido exista.

**Independent Test**:
- comparar una captura del teléfono con el PNG 04;
- recorrer todos los estados con las comprobaciones automáticas de accesibilidad, en claro y en oscuro, con la fuente al 200 % y en horizontal;
- con TalkBack, cada cambio de estado se anuncia una vez y cada mensaje y fuente se lee como una frase completa;
- abrir el teclado en vertical y en horizontal: el compositor queda encima.

**Acceptance Scenarios**:

1. **Given** la pantalla con una conversación, **When** se compara con el PNG 04, **Then** coinciden la barra superior («Chat con documentos», «⋯», «● Gemma 4 · Procesamiento local»), la burbuja, la tarjeta con «MicroRAG», el pie con «Copiar respuesta» y «N fuentes», «FUENTES CONSULTADAS» con sus filas, el compositor «Pregunta a tus documentos...» y la barra inferior con Chat seleccionado.
2. **Given** TalkBack activo, **When** una respuesta pasa por buscar, generar, terminar, cancelar o fallar, **Then** cada estado se anuncia una sola vez, sin leer cada fragmento del texto según llega.
3. **Given** el teclado abierto, **When** el usuario escribe, **Then** el compositor queda visible encima del teclado y la barra inferior se oculta (G39).
4. **Given** la fuente al 200 % o el teléfono en horizontal, **When** se recorre la pantalla, **Then** no se corta ningún texto, los objetivos táctiles miden al menos 48 dp y todo se alcanza desplazándose.
5. **Given** la app en inglés (D-029), **When** se abre el chat, **Then** todos los textos de la pantalla están en inglés; la respuesta sigue en el idioma del prompt (español por defecto, G25).

---

### User Story 8 - Primer token dentro del presupuesto en el teléfono (Priority: P3)

Quien desarrolla mide, en el teléfono de referencia, el tiempo desde enviar la pregunta hasta ver el primer texto de la respuesta, con preguntas reales sobre un corpus indexado. Si pasa de 5 s, se ajustan los bloques o los tokens con una decisión registrada.

**Why this priority**: es el presupuesto principal de la constitución IV (D-026). Se mide sobre el chat ya construido.

**Independent Test**: una prueba en el teléfono de referencia indexa el corpus de evaluación en un índice temporal, sin tocar la biblioteca del usuario. Envía preguntas de la muestra de F10 por el mismo camino que la UI, con Gemma cargada, y registra solo cifras: primer token, tokens del prompt, velocidad de generación y memoria.

**Acceptance Scenarios**:

1. **Given** Gemma cargada y el corpus indexado, **When** se envían las preguntas de la muestra, **Then** se registran la mediana y el p95 del primer token de extremo a extremo, junto a los tokens del prompt.
2. **Given** el p95 por encima de 5 s, **When** se analiza, **Then** se propone un ajuste de bloques o tokens y se registra la decisión en DECISIONS antes de cerrar.
3. **Given** la primera pregunta con Gemma sin cargar, **When** se mide, **Then** su tiempo se informa aparte.

---

### User Story 9 - Revisión de la plantilla del prompt (Priority: P3)

Quien desarrolla evalúa en el harness de escritorio si 1 o 2 variantes de la plantilla reducen las negativas indebidas y las respuestas incompletas que dejó F05 (D-035), sin empeorar la negativa ni las citas.

**Why this priority**: D-035 lo encarga a F11, pero la plantilla de D-034 ya cumple los umbrales de negativa y citas del informe.

**Independent Test**: la evaluación de escritorio compara la plantilla actual con las variantes sobre las mismas preguntas. Las juzga con la regla automática, revisa las respuestas que cambian (método D-033) y registra la decisión con sus cifras.

**Acceptance Scenarios**:

1. **Given** las variantes evaluadas, **When** ninguna sube ≥ 2 puntos las respuestas correctas sin bajar de 85 % en negativas o de 90 % en citas, **Then** se mantiene la plantilla «informe» y se registra la decisión con sus cifras.
2. **Given** una variante que cumple la regla, **When** se adopta, **Then** el escritorio y la app usan la misma plantilla, con una entrada en DECISIONS.

---

### Edge Cases

- **Pregunta muy larga:** el compositor limita la longitud. El prompt nunca pasa de su límite: si la pregunta y el turno anterior ocupan más, entran menos bloques, y el texto de un bloque nunca se corta.
- **Pregunta enviada antes de que Gemma esté cargada:** la respuesta espera dentro de «Buscando en documentos...» o «Generando respuesta...», sin fallar. El tiempo de carga cuenta en el primer token y se informa aparte.
- **Dos envíos seguidos:** mientras hay una respuesta en curso no se puede enviar otra; el botón es detener.
- **Salir del chat a mitad de una respuesta** (otra pestaña o una cita): la respuesta sigue y se ve al volver. Si la app pasa a segundo plano y el sistema libera el motor, la respuesta queda cancelada con su texto parcial.
- **Rotar o cambiar el tema durante una respuesta:** la respuesta sigue sin reiniciarse.
- **Formato de citas inesperado del modelo** (`[1][2]`, `[1, 3]`, `[ 2 ]`, `[Fuente 1]`): se interpretan los mismos formatos que en el escritorio y el resto se deja como texto. Una cita fuera de rango nunca produce una fuente.
- **Respuesta que solo contiene la negativa con comillas o puntuación distinta:** se reconoce con la misma normalización que el escritorio.
- **Negativa que empieza a escribirse durante el streaming:** al terminar se sustituye por el texto del MD.
- **Una fuente citada varias veces:** aparece una sola vez en la lista.
- **Dos fuentes del mismo documento:** son dos filas, una por fragmento citado.
- **Bloque que abarca varios fragmentos consecutivos:** la fila muestra su rango y abre el primero (propuesta C11).
- **Documento excluido de la búsqueda después de citarlo:** la cita sigue abriendo el documento.
- **Documento borrado mientras se genera una respuesta que lo usa:** al terminar, su fuente aparece como «Fuente ya no disponible».
- **Memoria baja durante una respuesta:** si el sistema obliga a liberar el motor, la respuesta queda cancelada con su texto parcial y se puede reintentar.
- **Fallo del motor en la GPU durante una respuesta:** error con «Reintentar». No se cambia de backend en silencio.
- **Indexación que empieza o se reanuda con el chat abierto:** la respuesta en curso termina primero; después el chat pasa al aviso de espera (FR-030).
- **Tanda en pausa** (por el usuario, la temperatura o el sistema): el chat funciona con los documentos ya listos y la respuesta lleva el aviso de cobertura parcial.
- **Sin espacio en disco al guardar un mensaje:** la respuesta se muestra igualmente y se avisa de que no se ha podido guardar.
- **Respuesta vacía del modelo:** se trata como un fallo con «Reintentar».
- **Respuesta que llega al límite de tokens de salida:** se muestra completa hasta ahí, como respuesta terminada.

## Requirements *(mandatory)*

### Functional Requirements

**Pantalla y navegación**

- **FR-001**: El chat MUST sustituir al marcador actual en la pestaña Chat y en «Nuevo chat», con la estructura del PNG 04:
  - barra superior «Chat con documentos» con Atrás y «⋯»;
  - subtítulo «● Gemma 4 · Procesamiento local»;
  - mensajes;
  - compositor «Pregunta a tus documentos...»;
  - barra inferior con Chat seleccionado.
- **FR-002**: La pestaña Chat MUST abrir la última conversación guardada si existe, también tras cerrar la app, y «Nuevo chat» MUST abrir una conversación nueva (D-047). La última conversación es la de la pregunta más reciente.
- **FR-003**: Las preguntas del usuario MUST mostrarse como burbuja a la derecha. Las respuestas MUST mostrarse como tarjeta a la izquierda con el avatar y el nombre «MicroRAG» (D-014).
- **FR-004**: El menú «⋯» MUST ofrecer «Nuevo chat» y «Borrar conversación» (G13). Borrar MUST pedir confirmación, eliminar la conversación del teléfono con sus mensajes y citas, y abrir un chat vacío. Con una respuesta en curso, borrar MUST detenerla primero. «Historial» se añade con F13. Ninguna opción MUST llevar a una pantalla que no exista.
- **FR-005**: Tocar una fuente disponible MUST abrir el detalle de su documento con el fragmento citado resaltado (D-043) y visible sin desplazarse. Atrás MUST volver al chat con la misma conversación y la misma posición de desplazamiento.

**Flujo de una pregunta**

- **FR-006**: Cada respuesta MUST pasar por estados distintos:
  - buscando («Buscando en documentos...»);
  - generando («Generando respuesta...», con el texto según llega);
  - completada;
  - cancelada (con su texto parcial y la marca «Generación cancelada»);
  - fallida (con «Reintentar»).
- **FR-007**: La búsqueda MUST usar la recuperación de F10 con la pregunta actual y, si existe, la pregunta anterior, para que se aplique su regla de seguimiento.
- **FR-008**: Si la búsqueda no devuelve ningún bloque, el modelo MUST NOT ejecutarse y la respuesta MUST ser la negativa (FR-016).
- **FR-009**: Mientras hay una respuesta en curso, el compositor MUST mostrar detener en lugar de enviar (G12) y MUST NOT aceptar otra pregunta.
- **FR-010**: Cada pregunta MUST generarse en una conversación nueva del motor. La continuidad la dan el turno anterior recortado del prompt (FR-013) y la regla de seguimiento (FR-007).
- **FR-011**: Una respuesta en curso MUST seguir al salir del chat dentro de la app (otra pestaña, una cita) y al rotar. MUST quedar cancelada con su texto parcial si el sistema obliga a liberar el motor.

**Prompt**

- **FR-012**: El prompt MUST construirse exactamente como en el escritorio (F05), con la plantilla vigente (hoy «informe», D-034):
  - las reglas;
  - el turno anterior, si existe;
  - «FUENTES:» con cada bloque numerado desde 1 y su cabecera «[n] nombre › ruta (p. N)», omitiendo las partes que no existen;
  - «PREGUNTA:».

  MUST dar el mismo texto, carácter a carácter, que el escritorio para las mismas entradas.
- **FR-013**: El turno anterior MUST incluir la pregunta anterior recortada a 300 caracteres y la respuesta anterior a 500, con la cabecera «CONVERSACIÓN PREVIA (solo como contexto de la pregunta):».
- **FR-014**: El idioma de la respuesta MUST ser un parámetro del prompt, con español por defecto (G25). El selector es de F12.
- **FR-015**: El prompt MUST NOT superar su límite (≈ 2.000 tokens estimados en el p95, D-034). Si la pregunta y el turno anterior ocupan más de lo previsto, MUST entrar menos bloques. El texto de un bloque MUST NOT cortarse.

**Citas y negativa**

- **FR-016**: Si la respuesta del modelo es la frase de negativa del prompt, con la normalización del escritorio, MUST mostrarse el texto del MD «No encuentro información suficiente en tus documentos», localizado y sin fuentes (G26).
- **FR-017**: Las citas MUST leerse con las mismas reglas que el escritorio: `[n]` y `[n, m]`. Un número fuera del rango de bloques del prompt MUST descartarse.
- **FR-018**: Cada fuente mostrada MUST corresponder a un bloque del contexto de esa respuesta, con su documento y fragmento reales. MUST NOT mostrarse ninguna fuente que no se pueda trazar a un documento y un fragmento que existían al generar la respuesta (constitución III).
- **FR-019**: «FUENTES CONSULTADAS» y el chip «N fuentes» MUST incluir solo las fuentes citadas, una vez cada una, en el orden de su primera cita. Sin citas válidas, MUST NOT aparecer ni la lista ni el chip.
- **FR-020**: Cada fila de fuente MUST mostrar el nombre del documento, la página si existe y el número de fragmento, que es el ordinal más 1 (G11).
- **FR-021**: Cada marca `[n]` válida del texto MUST mostrarse como una ficha pequeña tocable que abre el fragmento de esa fuente (G11). Las fichas MUST numerarse según el orden de «FUENTES CONSULTADAS» (1 = primera fuente listada), no con el número del bloque en el prompt. Una marca descartada (fuera de rango) MUST NOT mostrarse ni como ficha ni como texto.
- **FR-022**: El cuerpo de la respuesta MUST mostrar las listas como viñetas, como el PNG, y MUST NOT mostrar marcas de formato en crudo.

**Acciones**

- **FR-023**: Detener MUST parar la respuesta en menos de 1 s y conservar el texto parcial, marcado como cancelado. Una respuesta cancelada con citas válidas MUST mostrar sus fuentes.
- **FR-024**: «Copiar respuesta» MUST estar disponible en las respuestas completadas y canceladas con texto. MUST copiar el contenido de la propuesta C10 y confirmar la copia.
- **FR-025**: «Reintentar» MUST repetir la misma pregunta con el mismo turno anterior, en el lugar de la respuesta fallida.

**Conversaciones guardadas**

- **FR-026**: Las conversaciones MUST guardarse en el almacenamiento privado de la app, sin copia de seguridad (constitución I). Cada una con sus mensajes, en orden: pregunta, respuesta, estado, fecha y citas (documento, fragmento, nombre del documento, página y número de fragmento). Todas las conversaciones con mensajes MUST conservarse para el historial de F13; F11 no las borra salvo por una acción del usuario.
- **FR-027**: Una cita guardada MUST seguir mostrándose aunque su documento se borre después, como «Fuente ya no disponible», inerte. Si el documento existe pero el fragmento no, MUST abrir el documento con el aviso de F08.
- **FR-028**: Al abrir una conversación, ninguna respuesta MUST quedar en «buscando» ni en «generando». Las que se quedaron así por la muerte del proceso MUST mostrarse como interrumpidas (propuesta C13).
- **FR-029**: Una conversación sin mensajes MUST NOT guardarse.

**Motor y convivencia con la indexación**

- **FR-030**: Mientras una tanda de indexación está en marcha, el chat MUST NOT cargar Gemma ni aceptar preguntas (constitución IV: el LLM se libera durante la ingesta). MUST mostrar que podrá preguntar cuando termine la tanda, con acceso al Estado de indexación (propuesta C14). Al terminar o pausarse la tanda, el chat MUST quedar disponible sin salir de la pantalla. Si una tanda empieza o se reanuda durante una respuesta, la respuesta MUST terminar antes de que la tanda tome los motores (D-039).
- **FR-031**: Gemma MUST empezar a cargarse fuera del hilo principal al abrir el chat, si los modelos están preparados y no hay una tanda en marcha. MUST liberarse al ocultarse la app o con memoria baja (constitución IV).
- **FR-032**: Gemma MUST usar el backend preparado en F03: la GPU, o la CPU con el aviso de modo CPU (D-027, propuesta C9). Si la GPU falla durante una respuesta, MUST mostrarse el error con «Reintentar», sin cambiar de backend en silencio.
- **FR-033**: El chat MUST comprobar, sin cargar motores, que los dos modelos están verificados y preparados antes de permitir preguntar (constitución V).

**Estados de bloqueo y de borde**

- **FR-034**: Con algún modelo ausente, sin verificar o sin preparar, MUST mostrarse el bloqueo de modelo ausente con acceso a la Preparación (D-046), y el compositor MUST NOT enviar (propuesta C5).
- **FR-035**: Con la biblioteca vacía, MUST mostrarse el estado vacío con «Importar documentos», que abre la importación (propuesta C3).
- **FR-036**: Con documentos, pero ninguno indexado e incluido, y sin una tanda en marcha, MUST mostrarse el aviso de que no hay documentos listos, con acceso al Estado de indexación (propuesta C4).
- **FR-037**: Si en el momento de la pregunta hay documentos incluidos pendientes de indexar o en indexación, la respuesta MUST llevar el aviso de cobertura parcial (propuesta C6).
- **FR-038**: Con el índice no disponible, la respuesta MUST mostrar el error de índice con «Reintentar» (propuesta C8).

**Accesibilidad, idioma, privacidad y rendimiento**

- **FR-039**: Cada cambio de estado de una respuesta MUST anunciarse con TalkBack una sola vez, sin leer el texto según llega. Cada mensaje y cada fuente MUST leerse como una frase completa.
- **FR-040**: El compositor MUST quedar visible encima del teclado en vertical y en horizontal, y la barra inferior MUST ocultarse con el teclado (G39).
- **FR-041**: La pantalla MUST cumplir la constitución VII:
  - objetivos táctiles de 48 dp;
  - contraste AA;
  - fuente al 200 % y horizontal sin cortes;
  - modo oscuro;
  - todos los textos en recursos, en español y en inglés.
- **FR-042**: El chat MUST NOT hacer ninguna llamada de red.
- **FR-043**: Los registros MUST NOT contener preguntas, prompts, respuestas, textos de fragmentos ni nombres de documentos. En release no se registra nada. En debug, solo cifras: tiempos, tokens, bloques y estados.
- **FR-044**: La búsqueda, la generación y el guardado MUST ejecutarse fuera del hilo principal.
- **FR-045**: El primer token de extremo a extremo MUST medirse en el teléfono de referencia con los bloques de D-034 (SC-004). Si no se cumple, MUST registrarse en DECISIONS un ajuste de bloques o tokens antes del cierre.
- **FR-046**: La revisión de la plantilla pedida en D-035 MUST hacerse en el harness de escritorio, con 1 o 2 variantes sobre las mismas preguntas que F05. La regla de adopción es: ≥ 2 puntos más de respuestas correctas, negativas ≥ 85 % y citas ≥ 90 %. La decisión MUST registrarse en DECISIONS. Si se adopta una variante, la app y el escritorio MUST usar la misma plantilla (FR-012).

### Key Entities

- **Conversación**: una serie de preguntas y respuestas. Atributos: identificador y fecha de creación. Su título y su lista son de F13.
- **Mensaje**: una pregunta o una respuesta de la conversación, en orden. Atributos:
  - quién lo escribe (usuario o asistente);
  - texto;
  - fecha;
  - estado de la respuesta: buscando, generando, completada, cancelada, fallida o interrumpida;
  - si es una negativa;
  - si la cobertura era parcial.
- **Cita (fuente)**: un bloque del contexto que la respuesta cita. Atributos:
  - número `[n]` en el prompt;
  - documento;
  - primer fragmento y rango;
  - nombre del documento al responder;
  - página, si existe;
  - número de fragmento (ordinal + 1).

  Se resuelve como disponible, documento borrado o fragmento ausente al mostrarla.
- **Bloque de contexto** (F10): documento, fragmentos, nombre, ruta de encabezados, página, texto y tokens. Es la entrada del prompt y el origen de cada cita.
- **Prompt**: plantilla, idioma, turno anterior recortado, bloques numerados y pregunta. No se guarda ni se registra.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ninguna fuente mostrada apunta a un documento o fragmento que no estuviera en el contexto de su respuesta. Comprobado con respuestas de prueba que incluyen números fuera de rango, repetidos y con formatos raros: 0 fuentes inventadas.
- **SC-002**: El prompt de la app es idéntico, carácter a carácter, al del escritorio para todas las preguntas del conjunto de evaluación y sus turnos anteriores.
- **SC-003**: La lectura de citas y la detección de la negativa dan el mismo resultado que el escritorio en todas las respuestas generadas en F05.
- **SC-004**: En el teléfono de referencia, con Gemma cargada y la muestra de preguntas de F10 sobre el corpus de evaluación, el p95 del tiempo de enviar la pregunta a ver el primer texto es ≤ 5 s (constitución IV, D-026). Se informan la mediana, el p95, los tokens del prompt, la velocidad de generación, el pico de memoria y la primera pregunta con Gemma sin cargar.
- **SC-005**: Detener para el texto en menos de 1 s y conserva el texto parcial en el 100 % de las pruebas, también durante «Buscando en documentos...».
- **SC-006**: Tras una cita y Atrás, la conversación y su posición de desplazamiento son las mismas. El fragmento citado se ve sin desplazarse en el detalle, en vertical y en horizontal.
- **SC-007**: Tras cerrar la app desde recientes o matar el proceso, el 100 % de los mensajes y fuentes guardados se recuperan, y ninguno queda «generando».
- **SC-008**: Con TalkBack, cada estado se anuncia exactamente una vez por respuesta. Las comprobaciones automáticas de accesibilidad pasan en todos los estados, en claro y en oscuro, con la fuente al 200 % y en horizontal.
- **SC-009**: Con el teclado abierto, el compositor es visible en el 100 % de las configuraciones probadas: vertical, horizontal y fuente al 200 %.
- **SC-010**: Los registros de una sesión de prueba con textos marcados no contienen ninguno de esos textos ni nombres de documento. El chat no hace ninguna llamada de red.
- **SC-011**: Nunca hay dos motores cargados en la GPU, y Gemma se libera al ocultarse la UI. Comprobado también con una indexación en curso.
- **SC-012**: Una pregunta sin bloques recuperados recibe la negativa en menos de 1 s, sin ejecutar el modelo.
- **SC-013**: Una captura del teléfono coincide con el PNG 04 en estructura, textos, colores y tipografía, salvo las diferencias documentadas en el informe de cierre.
- **SC-014**: La revisión de la plantilla (D-035) queda registrada en DECISIONS con sus cifras.

## Assumptions

- **Propuestas de UI para estados sin PNG** (constitución VII: se aprueban antes de implementar). Los textos son propuestas en español; la versión inglesa se traduce igual:
  - **C1 — Negativa (G26):** la frase del informe es el centinela del modelo. La UI la detecta con la normalización del escritorio y muestra «No encuentro información suficiente en tus documentos», sin chip ni fuentes.
  - **C2 — Respuesta sin citas válidas:** se muestra el texto y, en lugar de las fuentes, «Esta respuesta no cita ninguna fuente de tus documentos.».
  - **C3 — Biblioteca vacía:** estado vacío «Aún no hay documentos», «Importa documentos para poder preguntarles.» y «Importar documentos». Compositor desactivado.
  - **C4 — Sin documentos listos** (sin tanda en marcha): «Aún no hay documentos listos para preguntar» con «Ver indexación». Compositor desactivado.
  - **C5 — Modelos no listos:** el bloqueo de modelo ausente de F01 con acceso a la Preparación (D-046). Compositor desactivado.
  - **C6 — Cobertura parcial:** bajo la respuesta, «Algunos documentos aún no están indexados: esta respuesta solo usa los que ya están listos.».
  - **C7 — Error:** la tarjeta en estado de error con el texto de F01 «No se pudo generar la respuesta» y «Reintentar».
  - **C8 — Índice no disponible:** «No se puede abrir el índice de documentos» con «Reintentar».
  - **C9 — Modo CPU:** el subtítulo pasa a «● Gemma 4 · Modo CPU, más lento», con el punto en color de aviso.
  - **C10 — Copiar:** se copia el texto de la respuesta y, debajo, la lista de fuentes citadas («[n] Documento, página N»). En Android 12 o anterior se confirma con «Respuesta copiada»; desde Android 13 lo confirma el sistema.
  - **C11 — Bloque de varios fragmentos:** la fila dice «Página 12 · Fragmentos 86–87» y abre el primero resaltado.
  - **C12 — Turno anterior:** solo cuenta un turno completado con respuesta que no sea la negativa. Si el último terminó en negativa, se usa solo su pregunta para el seguimiento. Si se canceló o falló, no hay turno anterior.
  - **C13 — Respuesta interrumpida:** tras la muerte del proceso, la respuesta muestra su texto parcial y «La respuesta se interrumpió», con «Reintentar».
  - **C14 — Espera a la indexación:** la conversación sigue visible, y sus fuentes se pueden abrir. Sobre el compositor desactivado aparece el aviso «Se están indexando tus documentos. Podrás preguntar cuando termine la indexación o si la pausas.» con «Ver indexación».
  - **C15 — Borrar conversación:** diálogo «¿Borrar esta conversación?», «Se eliminarán sus preguntas y respuestas de este teléfono.», con «Cancelar» (foco por defecto) y «Borrar».
- **Fuente y fragmento:** una cita apunta al bloque `[n]` del prompt. Si el bloque abarca varios fragmentos consecutivos, abre el primero (D-043: las citas apuntan a fragmentos enteros).
- **Idioma:** el prompt va en español por defecto aunque la app esté en inglés. F12 añade el selector de idioma de la respuesta y «Automático» (G25). Los textos de la pantalla siguen el idioma de la app (D-029).
- **Límite de la pregunta:** el compositor limita la longitud de la pregunta para respetar el límite del prompt. El valor lo fija el plan.
- **Medida del primer token:** igual que la paridad de F10, con el corpus de evaluación en un índice temporal y las preguntas de su muestra, sin tocar la biblioteca del usuario. Las pruebas con el teléfono van al final, como en F10.
- **Fuera de alcance:**
  - lista e historial de conversaciones, títulos y borrar conversaciones desde la lista (F13);
  - selector de idioma de la respuesta y borrar todo el historial (F12);
  - INT8, cambio de modelo y palancas del primer token (F14);
  - adjuntos, voz y edición de preguntas enviadas.
- **Dependencias:** F08 (detalle con resaltado), F10 (recuperación), F03 (modelos preparados), F07 (tanda con pausa y causa) y F09 («Nuevo chat»).
