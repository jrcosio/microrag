# Especificación de feature: Historial de chats (F13)

**Feature Branch**: `013-chat-history`

**Created**: 2026-11-02

**Status**: Draft

**Input**: Descripción del usuario: «F13 — Historial de chats. Feature de producto sin mockup (D-007, G13): la pantalla de lista se propone con componentes `Lisdata*`, y el wireframe se aprueba antes de `/speckit-plan`. El usuario ve la lista de sus conversaciones guardadas en el teléfono y puede abrirlas, continuarlas y borrarlas. Todo funciona sin red.» Ver `docs/roadmap/FEATURES.md` (F13).

**Fuentes**:
- **Sin mockup.** No hay PNG del historial. La pantalla se propone con el design system y el usuario la aprueba antes de implementarla (constitución VII, D-004, D-007):
  - `docs/disegn_app/screens/04-chat.md`: historial local, sin llamadas externas y sin prompts en los registros;
  - `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/04-chat.png`: el icono «⋯» de la barra del chat, punto de entrada (G13).
- `docs/disegn_app/docs/`:
  - `00-design-system.md`: tokens, 48 dp, contraste, letra dinámica y modo oscuro;
  - `01-navigation.md`: ruta `chat/{conversationId?}`, barra inferior solo en las pestañas, conservar la conversación activa al recrear la pantalla;
  - `03-components.md`: filas con su semántica agrupada, un solo sistema de componentes y vistas previas en claro, oscuro, texto largo y letra grande;
  - `04-qa-checklist.md`: conversaciones locales y accesibilidad.
- `docs/roadmap/INVENTARIO.md`:
  - ficha P04 (chat): estado «fuente borrada: Fuente ya no disponible», y su menú «⋯»;
  - G13: el menú «⋯» del chat tiene «Nuevo chat», «Historial» (F13) y «Borrar conversación».
- `docs/RAG_Movil/informe-rag-movil-v2.md`, generación: una conversación nueva del modelo por pregunta, con la continuidad dada por la pregunta y la respuesta anteriores, truncadas, en la plantilla (L762–795).
- Constitución v1.1.1:
  - I: sin red; datos en almacenamiento privado sin copia de seguridad; registros sin contenido privado;
  - III: el prompt y la recuperación solo cambian con una evaluación documentada;
  - V: eliminar exige confirmación;
  - VI: pruebas de los ViewModels y de la capa de datos;
  - VII: lo no maquetado se propone y se aprueba antes de implementarlo; accesibilidad y textos en recursos.
- DECISIONS:
  - D-004: los estados sin maqueta se proponen y se aprueban antes;
  - D-007: el historial de chats es la única función sin mockup que entra en el roadmap; exportar queda en el backlog;
  - D-047: «Nuevo chat» de Inicio abre una conversación nueva;
  - D-053: conversaciones guardadas sin copia de seguridad; la pestaña Chat abre la última; «Historial» llega al «⋯» con F13;
  - D-054: una sola respuesta en curso en toda la app; «Nuevo chat» y borrar la cancelan; las conversaciones viven en un fichero propio;
  - D-055: el turno anterior de una conversación (también tras una negativa) entra en el prompt; orden para elegir la conversación que abre el chat;
  - D-058 y D-059: «Borrar historial» de Ajustes (F12) detiene la respuesta en curso, borra todo y deja el chat vacío; una pregunta en una conversación ya borrada empieza otra.
- FEATURES: F13 (criterios 1–5 y riesgos), F11 y F12.
- Lo ya construido:
  - F11: conversaciones guardadas con sus preguntas, respuestas, estados y fuentes; la pestaña Chat abre la última; menú «⋯» con «Nuevo chat» y «Borrar conversación»; fila de fuente «Fuente ya no disponible»;
  - F12: «Borrar historial de chat» en Ajustes, que avisa a la pestaña Chat;
  - F08: la Biblioteca carga su lista por tandas al desplazarse.

**Verificado al redactar** (2026-11-02):
- **Qué se guarda de cada conversación:** su fecha de creación y su fecha de última actividad, que cambia solo al hacer una pregunta. No hay título guardado. La primera pregunta de cada conversación se conserva siempre, porque las preguntas no se borran una a una y reintentar no cambia su orden.
- **Lista:** no existe ninguna forma de listar las conversaciones. Sí existen contarlas (solo las que tienen preguntas), abrir una por su identificador, borrar una y borrar todas.
- **Continuar:** al preguntar en una conversación existente, la app ya usa su último turno completado como contexto (D-055). No hace falta nada nuevo en el prompt ni en la recuperación.
- **Abrir una conversación concreta en el chat:** hoy el chat solo abre la última, una nueva («Nuevo chat») o la que recuerda tras girar. Volver a la pestaña Chat con otra conversación no basta, porque el chat conserva la que tenía (R13 de F11).
- **Respuesta en curso:** hay una sola en toda la app. Si se preguntara en otra conversación mientras dura, hoy la pregunta no se enviaría y no se avisaría.
- **Borrar una conversación** desde el chat la elimina con sus mensajes y fuentes, pero solo el propio chat se entera. «Borrar historial» de Ajustes sí avisa a la pestaña Chat.
- **Fuentes de documentos borrados:** la lista «Fuentes consultadas» ya muestra «Fuente ya no disponible» y no abre nada. Las fichas `[n]` del texto, en cambio, siguen intentando abrir el documento, y el aviso del detalle se descarta sin mostrarse.
- **Actualizaciones:** las conversaciones se guardan cada segundo mientras se genera una respuesta. Una lista que se refrescara con cada guardado se consultaría una vez por segundo durante cada respuesta.
- **Fechas:** la app solo sabe mostrar fechas como «12 oct.» (con el año si no es el actual). No hay «Hoy» ni «Ayer».
- **Componentes disponibles:**
  - barra superior con Atrás y acciones; menú «⋯»;
  - fila de tarjeta con título, línea de datos y chevron (la de la cola de importación);
  - cabecera de sección;
  - estados vacío, error y fila esqueleto de carga;
  - diálogo de confirmación.

  No hay deslizar para borrar, pulsación larga, menú por fila ni «Deshacer».

## Clarifications

### Session 2026-11-02

- Q: ¿En qué orden y con qué agrupación se muestran las conversaciones en el Historial? → A: Por última actividad: arriba la de la pregunta más reciente, y una conversación continuada sube. Con grupos por fecha: «Hoy», «Ayer», «Últimos 7 días» y, después, uno por mes y año.
- Q: ¿Cómo se borra una conversación desde la lista del Historial? → A: Con un botón de papelera visible en cada fila, de 48 dp, que abre la confirmación de F11 («¿Borrar esta conversación?»). Tocar el resto de la fila abre la conversación. No hay deslizar, pulsación larga ni modo de selección, y se borra de una en una.
- Q: Si hay una respuesta generándose en una conversación y se abre otra desde el Historial, ¿qué pasa con esa respuesta? → A: Se detiene, como con «Nuevo chat» (D-054): queda «Generación cancelada» con su texto parcial en su conversación, y la otra se abre lista para preguntar.
- Q: ¿Las conversaciones guardadas se borran alguna vez solas para que el historial no crezca sin límite? → A: Nunca solas: solo por una acción del usuario, como en F11. Se mide en el teléfono de referencia cuánto ocupan 1.000 conversaciones de 5 preguntas (estimación previa: unos 5–10 MB, frente a varios GB de los modelos) y las cifras van al informe de cierre. Si alguna vez hiciera falta un límite, se decide con ellas en otra feature.
- Q: ¿Se aprueban la spec, el wireframe y las propuestas H1–H11? → A: Sí (revisión del clarify, 2026-11-02).

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Usuario:** vuelve a conversaciones anteriores, las continúa y borra las que no quiere.

### User Story 1 - Ver mis conversaciones guardadas (Priority: P1)

En el chat, el usuario abre «⋯» y toca «Historial». Ve sus conversaciones guardadas en el teléfono, ordenadas por fecha. Cada una lleva un título sacado de su primera pregunta y su fecha. Con muchas conversaciones, la lista va cargando más al desplazarse.

**Why this priority**: es el criterio 2 de F13. F11 guarda todas las conversaciones, pero hoy solo se puede volver a la última.

**Independent Test**: con 0, 1 y más de 50 conversaciones guardadas:
- «Historial» está en el menú «⋯»;
- la lista muestra todas las conversaciones con preguntas, y ninguna vacía;
- cada fila lleva el título y la fecha correctos;
- desplazarse hasta el final las muestra todas, sin duplicados ni huecos.

**Acceptance Scenarios**:

1. **Given** el chat, **When** el usuario abre «⋯», **Then** ve «Historial», además de «Nuevo chat» y, si hay una conversación abierta, «Borrar conversación».
2. **Given** conversaciones guardadas, **When** el usuario toca «Historial», **Then** ve una fila por conversación con al menos una pregunta, de la de actividad más reciente a la más antigua, bajo los grupos «Hoy», «Ayer», «Últimos 7 días» y uno por mes y año (FR-004).
3. **Given** una conversación cuya primera pregunta es «¿Cuál es el plazo de devolución?», **When** aparece en la lista, **Then** su título es «¿Cuál es el plazo de devolución?».
4. **Given** más conversaciones de las que caben en una tanda, **When** el usuario se desplaza hasta el final, **Then** se cargan las siguientes sin saltos, duplicados ni huecos.
5. **Given** ninguna conversación guardada, **When** el usuario abre Historial, **Then** ve el estado vacío, con «Nuevo chat».
6. **Given** conversaciones guardadas ilegibles (C17 de F11), **When** el usuario abre Historial, **Then** ve el aviso de error y no se borra nada.

---

### User Story 2 - Abrir y continuar una conversación (Priority: P1)

El usuario toca una conversación de la lista y vuelve al chat con ella: sus preguntas, sus respuestas con su estado y sus fuentes, tal como quedaron. Sigue preguntando, y la respuesta tiene en cuenta la pregunta y la respuesta anteriores, igual que en F11. Si un documento citado se borró, su fuente dice «Fuente ya no disponible» y no abre nada.

**Why this priority**: es el criterio 3 de F13 y el objetivo de la feature: «abrir, continuar y borrar».

**Independent Test**: con una conversación de varios turnos (completados, una negativa, uno cancelado, uno interrumpido y uno fallido) y uno de sus documentos citados borrado:
- abrirla muestra todo igual que antes;
- la fuente del documento borrado no abre nada, ni en la lista de fuentes ni en su ficha `[n]`;
- una pregunta nueva se guarda en esa conversación y usa el turno anterior.

**Acceptance Scenarios**:

1. **Given** la lista, **When** el usuario toca una conversación, **Then** el historial se cierra y la pestaña Chat la muestra completa, en el punto de la última respuesta, como al abrir la última en F11.
2. **Given** una conversación abierta desde el historial, **When** el usuario pregunta, **Then** la pregunta y su respuesta se añaden a esa conversación, con su último turno completado como contexto (D-055).
3. **Given** una fuente cuyo documento se borró, **When** se muestra, **Then** su fila dice «Fuente ya no disponible» y no abre nada, y su ficha `[n]` del texto tampoco abre nada.
4. **Given** una fuente cuyo documento existe pero cuyo fragmento ya no, **When** el usuario la toca, **Then** se abre el documento con el aviso de F08, como en F11.
5. **Given** una conversación abierta desde el historial, **When** el usuario gira el teléfono, cambia de pestaña o cambia el idioma de la app, **Then** el chat sigue en esa conversación.
6. **Given** una respuesta en curso en otra conversación, **When** el usuario abre una conversación del historial, **Then** esa respuesta se detiene y queda «Generación cancelada» con su texto parcial, y la conversación abierta acepta preguntas (FR-013).
7. **Given** una respuesta en curso, **When** el usuario abre desde el historial esa misma conversación, **Then** la respuesta sigue y el chat la muestra generándose.
8. **Given** los modelos sin preparar o una indexación en marcha, **When** el usuario abre una conversación del historial, **Then** la ve completa, y preguntar sigue las mismas reglas que el chat de F11 (C5, C14).
9. **Given** una conversación abierta desde el historial, **When** el usuario pulsa Atrás, **Then** el chat se comporta como siempre: lleva a Inicio (G08).

---

### User Story 3 - Borrar una conversación desde el historial (Priority: P1)

En la lista, el usuario borra una conversación que ya no quiere. La app le pide confirmación y la elimina del teléfono con sus mensajes y fuentes. Las demás conversaciones, sus documentos y el índice no cambian.

**Why this priority**: es el criterio 4 de F13 y una acción destructiva sobre datos del usuario (constitución V).

**Independent Test**: con varias conversaciones, una de ellas abierta en la pestaña Chat y otra con una respuesta en curso:
- cancelar no borra nada;
- confirmar borra solo esa conversación;
- si era la abierta en el chat, el chat queda vacío y su siguiente pregunta se guarda;
- si era la que se estaba respondiendo, la respuesta se detiene antes.

**Acceptance Scenarios**:

1. **Given** la lista, **When** el usuario toca la papelera de una fila, **Then** aparece la confirmación «¿Borrar esta conversación?» (FR-015), y «Cancelar» no borra nada.
2. **Given** la confirmación, **When** el usuario confirma, **Then** esa conversación desaparece de la lista y del teléfono con sus mensajes y fuentes, y se anuncia «Conversación borrada».
3. **Given** la conversación borrada era la abierta en la pestaña Chat, **When** el usuario vuelve al chat, **Then** ve un chat vacío, y su siguiente pregunta empieza una conversación nueva que se guarda con normalidad.
4. **Given** la conversación borrada tenía una respuesta en curso, **When** el usuario confirma, **Then** la respuesta se detiene primero y no queda nada guardado de esa conversación.
5. **Given** un borrado confirmado, **When** el usuario abre la Biblioteca, **Then** sus documentos siguen ahí, y las demás conversaciones siguen en el historial.
6. **Given** un fallo al borrar, **When** ocurre, **Then** un aviso lo dice y la conversación sigue entera en la lista.

---

### User Story 4 - Empezar un chat nuevo desde el historial (Priority: P2)

Desde el historial, el usuario toca «Nuevo chat» y vuelve a un chat vacío. La conversación anterior sigue guardada en la lista.

**Why this priority**: es el criterio 5 de F13. Desde el historial es natural empezar otra conversación sin volver primero al chat.

**Independent Test**: desde el historial, «Nuevo chat» abre el chat vacío; la nueva conversación aparece en la lista al hacer su primera pregunta, y no antes.

**Acceptance Scenarios**:

1. **Given** Historial, **When** el usuario toca «Nuevo chat», **Then** el historial se cierra y la pestaña Chat queda vacía, lista para preguntar.
2. **Given** una respuesta en curso, **When** el usuario toca «Nuevo chat» en el historial, **Then** se detiene como con «Nuevo chat» del chat (D-054).
3. **Given** un chat nuevo sin preguntas, **When** el usuario vuelve a Historial, **Then** no aparece ninguna conversación vacía. Al hacer la primera pregunta, la conversación aparece en la lista.

---

### User Story 5 - Una lista siempre al día (Priority: P2)

La lista refleja lo que pasa en el resto de la app: una pregunta nueva, una conversación borrada desde el chat o el historial borrado entero desde Ajustes.

**Why this priority**: el criterio 4 de F13 pide que «Borrar historial» de Ajustes vacíe la lista. Una lista desfasada mostraría conversaciones que ya no existen o abriría conversaciones borradas.

**Independent Test**: con Historial abierto en la pila del chat, borrar todo desde Ajustes, borrar una conversación desde el chat y hacer preguntas: la lista cambia en cada caso, sin parpadeos durante una respuesta.

**Acceptance Scenarios**:

1. **Given** Historial abierto, **When** el usuario va a Ajustes, confirma «Borrar historial de chat» y vuelve al chat, **Then** el historial está vacío.
2. **Given** una conversación borrada con «⋯» → «Borrar conversación» en el chat, **When** el usuario abre Historial, **Then** ya no aparece.
3. **Given** una pregunta nueva en una conversación, **When** el usuario abre Historial, **Then** la conversación aparece la primera, en «Hoy».
4. **Given** una respuesta generándose, **When** Historial se muestra, **Then** la lista no parpadea ni pierde la posición de desplazamiento.

---

### User Story 6 - Pantalla aprobada, accesible y traducida (Priority: P2)

La pantalla del historial sigue el wireframe aprobado por el usuario, funciona con TalkBack, con la letra al 200 %, en horizontal y en modo oscuro, y tiene sus textos en español y en inglés.

**Why this priority**: criterio 1 de F13 (wireframe aprobado antes del plan) y constitución VII.

**Independent Test**:
- el wireframe y las propuestas H tienen la aprobación del usuario antes de `/speckit-plan`;
- las comprobaciones automáticas de accesibilidad pasan;
- las capturas del teléfono coinciden con el wireframe aprobado.

**Acceptance Scenarios**:

1. **Given** TalkBack, **When** el usuario recorre la lista, **Then** cada fila se lee como una frase (título, fecha y lo que hace al tocarla), y los grupos de fecha se leen como encabezados.
2. **Given** la letra al 200 % o el teléfono en horizontal, **When** se abre Historial, **Then** nada se corta y todo se alcanza desplazando.
3. **Given** la app en inglés, **When** se abre Historial, **Then** todos sus textos y fechas salen en inglés.
4. **Given** el diálogo de borrado o el menú «⋯» abiertos, **When** se gira el teléfono o cambia el tema, **Then** siguen abiertos.

---

### User Story 7 - Almacenamiento medido (Priority: P3)

Quien desarrolla mide cuánto ocupan las conversaciones guardadas y cuánto tarda en abrirse el historial con un historial grande, y deja las cifras en el informe de cierre.

**Why this priority**: es el riesgo «crecimiento del almacenamiento» de F13. Hoy no hay cifras, y nada se borra salvo por una acción del usuario (FR-026 de F11).

**Independent Test**: las cifras de tamaño y de apertura con un historial grande de prueba quedan en el informe de cierre, en el teléfono de referencia.

**Acceptance Scenarios**:

1. **Given** un historial de prueba grande (SC-010), **When** se mide, **Then** el informe recoge el tamaño en disco y el tiempo hasta ver las primeras filas.

---

### Edge Cases

- **Primera pregunta cancelada, fallida o interrumpida:** la conversación sigue en la lista con esa pregunta como título.
- **Pregunta con saltos de línea o espacios de más:** el título los muestra como espacios simples. Si no cabe, se corta con «…».
- **Títulos repetidos:** dos conversaciones con la misma primera pregunta se distinguen por su fecha.
- **Conversación sin preguntas** (un «Nuevo chat» sin usar): no aparece.
- **Abrir una conversación sin preguntar:** su fecha de última actividad no cambia. Tras cerrar la app, la pestaña Chat abre la de última actividad más reciente (F11).
- **Volver del Historial con Atrás, sin abrir nada, con una respuesta en curso:** la respuesta sigue; solo abrir otra conversación o «Nuevo chat» la detienen.
- **Tocar dos veces seguidas una fila o su papelera:** se abre una sola vez, o aparece un solo diálogo.
- **Borrar desde la lista la conversación abierta en el chat con una respuesta en curso:** se detiene primero, se borra, y el chat queda vacío.
- **Preguntar en una conversación borrada por otra vía** (por ejemplo, desde Ajustes con el chat en otra pestaña): la pregunta empieza una conversación nueva y no se pierde (F12).
- **«Borrar historial» de Ajustes con Historial abierto en la pila del chat:** al volver, la lista está vacía.
- **Fuente de un documento que se borró y se volvió a importar:** sigue como «Fuente ya no disponible», porque la cita apunta al documento original.
- **Cambio de idioma de la app con Historial abierto:** los textos y las fechas cambian de idioma, y la lista y su posición se conservan.
- **Cambio de día con Historial abierto, o cambio de zona horaria:** al volver a la pantalla, las fechas y los grupos se recalculan con la fecha y la zona actuales. Las conversaciones de «Hoy» pasan a «Ayer».
- **Fecha del teléfono atrasada** (una última actividad posterior a hoy): la conversación va en «Hoy», arriba.
- **Grupo partido entre dos tandas:** su cabecera aparece una sola vez, y las filas de la tanda siguiente se añaden debajo.
- **Miles de conversaciones:** Historial abre sin esperar a leerlas todas (SC-001).
- **Conversaciones guardadas ilegibles:** Historial muestra el aviso de C17 y no ofrece borrar ni abrir nada. «Borrar historial» de Ajustes sigue disponible.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso y navegación**

- **FR-001**: El menú «⋯» del chat MUST ofrecer «Historial» siempre, también con el chat vacío o sin conversaciones guardadas, junto a «Nuevo chat» y, con una conversación abierta, «Borrar conversación» (G13, D-053).
- **FR-002**: Historial MUST ser una pantalla propia sobre el chat, sin barra inferior. Su barra superior MUST llevar Atrás, que vuelve al chat sin cambiar la conversación que tenía.
- **FR-003**: Abrir una conversación desde Historial MUST cerrar Historial y mostrarla en la pestaña Chat. Desde ahí, Atrás MUST comportarse como en el chat (G08).

**Lista**

- **FR-004**: Historial MUST listar todas las conversaciones guardadas con al menos una pregunta, y solo esas, por última actividad: primero la de la pregunta más reciente. Una conversación en la que se pregunta de nuevo MUST pasar arriba. Las conversaciones MUST ir agrupadas por la fecha de su última actividad:
  - «Hoy»;
  - «Ayer»;
  - «Últimos 7 días»: de 2 a 6 días antes de hoy;
  - después, un grupo por mes y año («Octubre de 2026»).

  Los grupos vacíos no se muestran. Con la misma fecha, va primero la conversación creada después.
- **FR-005**: Cada fila MUST mostrar un título derivado de la primera pregunta de la conversación, con los espacios y saltos de línea normalizados y cortado si no cabe, y su fecha. Las conversaciones no tienen otro título y no se renombran.
- **FR-006**: La lista MUST cargarse por tandas. Abrir Historial no espera a leer todas las conversaciones. Al acercarse al final se cargan las siguientes, sin duplicados ni huecos, aunque la lista cambie mientras tanto.
- **FR-007**: Historial MUST tener los estados de carga, vacío (con «Nuevo chat»), error (conversaciones ilegibles, C17 de F11, sin borrar nada) y contenido.
- **FR-008**: La lista MUST actualizarse sola cuando se hace una pregunta, cuando se borra una conversación desde el chat y cuando se borra el historial desde Ajustes. Durante una respuesta en curso MUST NOT parpadear ni perder la posición de desplazamiento.
- **FR-009**: Las fechas MUST mostrarse en el idioma de la app y con la zona horaria actual del teléfono.

**Abrir y continuar**

- **FR-010**: Abrir una conversación MUST restaurar todas sus preguntas y respuestas, en orden, con su estado (completada, negativa, cancelada, interrumpida o fallida), sus notas y sus fuentes, igual que la pestaña Chat de F11.
- **FR-011**: Una fuente cuyo documento se borró MUST mostrar «Fuente ya no disponible» y no abrir nada, tanto en «Fuentes consultadas» como en su ficha `[n]` del texto. Vale para cualquier conversación del chat, no solo para las abiertas desde Historial. Si el documento existe pero el fragmento no, la fuente MUST abrir el documento con el aviso de F08, como en F11.
- **FR-012**: Preguntar en una conversación abierta desde Historial MUST añadir la pregunta a esa conversación y usar su último turno completado como en F11 (D-055), sin cambios en el prompt ni en la recuperación (constitución III). Preguntar MUST seguir las mismas reglas del chat de F11: modelos preparados y sin tanda de indexación en marcha (C5, C14).
- **FR-013**: Con una respuesta en curso en otra conversación, abrir una conversación desde Historial MUST detener esa respuesta como «Nuevo chat» (D-054): queda «Generación cancelada», con su texto parcial y sus citas válidas, en su conversación. La conversación abierta MUST quedar lista para preguntar, y ninguna pregunta MUST perderse sin aviso. Abrir la misma conversación que se está respondiendo MUST NOT detener nada.
- **FR-014**: La conversación abierta desde Historial MUST quedarse como la del chat al girar el teléfono, cambiar de pestaña o cambiar el idioma de la app.

**Borrar**

- **FR-015**: Cada fila de Historial MUST llevar un botón de papelera visible, de 48 dp, con su propia etiqueta para TalkBack, que pide confirmación antes de borrar esa conversación (constitución V). Tocar el resto de la fila MUST abrir la conversación. «Cancelar» MUST NOT borrar nada. Se borra de una en una: no hay deslizar, pulsación larga ni modo de selección.
- **FR-016**: Al confirmar, la app MUST eliminar del teléfono esa conversación con sus mensajes y fuentes, y solo esa. Los documentos, el índice, las preferencias y las demás conversaciones MUST quedar intactos.
- **FR-017**: Si la conversación borrada tiene una respuesta en curso, la respuesta MUST detenerse primero, y MUST NOT quedar nada guardado de esa conversación.
- **FR-018**: Si la conversación borrada era la abierta en la pestaña Chat, el chat MUST quedar vacío y usable: su siguiente pregunta empieza una conversación nueva y se guarda.
- **FR-019**: Al borrar, la app MUST anunciar «Conversación borrada». Si el borrado falla, MUST avisar, y la conversación MUST seguir entera.
- **FR-020**: «Borrar historial de chat» de Ajustes (F12) MUST vaciar la lista, también si Historial estaba abierto en la pila del chat.

**Nuevo chat**

- **FR-021**: Historial MUST ofrecer «Nuevo chat», que cierra Historial y deja la pestaña Chat vacía, con el mismo comportamiento que «Nuevo chat» del chat: detiene la respuesta en curso (D-054). La conversación nueva aparece en la lista con su primera pregunta.

**Almacenamiento**

- **FR-022**: Las conversaciones MUST seguir en el almacenamiento privado del teléfono, sin copia de seguridad (constitución I). Nada MUST borrarse si no es por una acción del usuario (FR-026 de F11): no hay límite de conversaciones, borrado automático ni aviso de tamaño. El tamaño que ocupan y el tiempo de apertura de Historial con un historial grande MUST medirse en el teléfono de referencia y quedar en el informe de cierre (SC-001, SC-010).

**Textos, accesibilidad, privacidad y fidelidad**

- **FR-023**: Todos los textos nuevos MUST estar en español y en inglés en los recursos de la app, con las mismas claves. Los textos que ya dicen lo mismo en F11 o F12 (la confirmación de borrado, «La respuesta en curso se detendrá.», «Conversación borrada», «Nuevo chat», «Reintentar» y el error de C17) MUST reutilizarse.
- **FR-024**: Historial MUST:
  - tener objetivos táctiles de 48 dp;
  - leer cada fila como una sola frase, con su acción;
  - marcar los grupos de fecha, si los hay, como encabezados;
  - anunciar con TalkBack el borrado y sus errores;
  - funcionar con la letra al 200 %, en horizontal y en modo oscuro;
  - conservar el menú y el diálogo abiertos al girar y al cambiar el tema.
- **FR-025**: Historial MUST funcionar sin red. Los registros MUST NOT contener títulos, preguntas ni respuestas; solo identificadores y cifras (constitución I).
- **FR-026**: La UI de Historial MUST seguir el wireframe y las propuestas H aprobadas por el usuario antes de `/speckit-plan`. Las diferencias MUST reportarse al cierre (constitución VII).

### Key Entities

- **Conversación** (de F11): una serie de preguntas y respuestas guardada en el teléfono, con su fecha de creación y su fecha de última actividad (la de su última pregunta). F13 no añade datos que el usuario escriba.
- **Entrada del historial:** cómo se ve una conversación en la lista: el título derivado de su primera pregunta, su fecha y si es la abierta en el chat. Se calcula a partir de la conversación; no se edita.
- **Tanda:** el número de entradas que se cargan cada vez al desplazarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con 1.000 conversaciones guardadas, Historial muestra sus primeras filas en menos de 1 s en el teléfono de referencia. Desplazarse hasta el final muestra las 1.000, sin duplicados ni huecos.
- **SC-002**: En las pruebas con todos los estados de turno (completado, negativa, cancelado, interrumpido y fallido), el 100 % de las conversaciones abiertas desde Historial muestran las mismas preguntas, respuestas, estados, notas y fuentes que se guardaron.
- **SC-003**: Con documentos citados borrados, el 100 % de sus fuentes muestran «Fuente ya no disponible», y 0 fichas `[n]` o filas de fuente abren un documento borrado.
- **SC-004**: En una conversación abierta desde Historial, el prompt de la pregunta siguiente es idéntico al que construye F11 para esa misma conversación. El golden del prompt (426 casos de F05) no cambia.
- **SC-005**: Tras borrar una conversación desde Historial:
  - hay exactamente una conversación menos;
  - las demás, el número de documentos y el de fragmentos indexados no cambian;
  - una respuesta en curso en ella se detiene en menos de 1 s;
  - si era la abierta en el chat, la siguiente pregunta se guarda en una conversación nueva.
- **SC-006**: Tras «Borrar historial de chat» en Ajustes, Historial está vacío en el 100 % de los casos, también si estaba abierto en la pila del chat.
- **SC-007**: Las comprobaciones automáticas de accesibilidad pasan en Historial y en su diálogo de borrado, en claro y en oscuro, con la letra al 200 % y en horizontal.
- **SC-008**: Los textos en español y en inglés tienen las mismas claves (100 % de paridad).
- **SC-009**: Historial no hace ninguna llamada de red, y los registros de una sesión de prueba no contienen títulos, preguntas ni respuestas.
- **SC-010**: El informe de cierre recoge, en el teléfono de referencia, el tamaño en disco de un historial de prueba de 1.000 conversaciones de 5 preguntas con sus fuentes, y el tiempo de apertura de SC-001.
- **SC-011**: Las capturas del teléfono coinciden con el wireframe aprobado en estructura, textos y componentes, salvo las diferencias documentadas en el informe de cierre.

## Assumptions

- **Wireframe y propuestas de UI (serie H)** (constitución VII: se aprueban antes de `/speckit-plan`, criterio 1 de F13). F13 no tiene PNG. Los dibujos están en [wireframe.md](wireframe.md), con los componentes `Lisdata*` de cada zona. Los textos son propuestas en español; la versión inglesa va en H11:
  - **H1 — Acceso:**
    - «Historial» en el menú «⋯» del chat, entre «Nuevo chat» y «Borrar conversación» (orden de G13), siempre visible;
    - Historial es una pantalla encima del chat, sin barra inferior; Atrás vuelve al chat sin cambiar su conversación.
  - **H2 — Barra superior:** «‹ Historial» con la acción «＋», que TalkBack lee como «Nuevo chat», como el «＋» de la Biblioteca.
    - Cierra Historial y deja el chat vacío (FR-021).
    - No hay encabezado ni texto introductorio: la lista empieza bajo la barra.
  - **H3 — Fila de conversación** (pieza nueva sobre `LisdataCard`):
    - **Título:** la primera pregunta, en 2 líneas como mucho y cortada con «…».
    - **Línea de datos:** la fecha de la última actividad y el número de preguntas, por ejemplo «10:42 · 3 preguntas». La fecha va según el grupo:
      - en «Hoy» y «Ayer», la hora;
      - en «Últimos 7 días», el día de la semana («lunes»);
      - en los grupos de mes, «12 oct.».
    - **Chip «Abierta»:** informativo, solo en la conversación abierta en la pestaña Chat.
    - **Papelera:** a la derecha, de 48 dp y en el color secundario del texto, no en rojo, para no llenar la lista de iconos rojos. El rojo queda para «Borrar» en el diálogo.
    - Sin chevron.
    - Tocar el resto de la tarjeta abre la conversación.
    - **TalkBack:**
      - la fila: «¿Cuál es el plazo de devolución? Hoy, 10:42. 3 preguntas. Abierta.», con la acción «Abrir conversación»;
      - la papelera, aparte: «Borrar conversación: ¿Cuál es el plazo de devolución?».
  - **H4 — Vacío:** icono de chat, «Aún no hay conversaciones», «Las conversaciones que tengas en el chat aparecerán aquí.» y el botón «Nuevo chat».
  - **H5 — Cargando y error:**
    - mientras carga, 4 filas esqueleto;
    - si no se pueden leer las conversaciones, «No se pueden abrir tus conversaciones guardadas.» (C17) con «Reintentar», sin borrar nada y sin ofrecer abrir ni borrar.
  - **H6 — Borrar:**
    - el diálogo de F11 (C15): «¿Borrar esta conversación?», «Se eliminarán sus preguntas y respuestas de este teléfono.», con «Cancelar» (foco por defecto) y «Borrar»;
    - si esa conversación tiene una respuesta en curso, se añade «La respuesta en curso se detendrá.» (S6 de F12);
    - al terminar, el aviso inferior «Conversación borrada», que TalkBack anuncia; sin «Deshacer»;
    - si falla, «No se pudo borrar la conversación.».
  - **H7 — Abrir:**
    - el chat muestra la conversación como al abrir la última en F11, en el punto de la última respuesta;
    - las fuentes de documentos borrados dicen «Fuente ya no disponible» y no abren nada;
    - sus fichas `[n]` del texto se ven en gris, como esa fila, y TalkBack las lee «Fuente N: Fuente ya no disponible», sin acción.
  - **H8 — Respuesta en curso** (clarify, FR-013): abrir otra conversación la detiene sin preguntar, como «Nuevo chat». Queda «Generación cancelada» en su conversación.
  - **H9 — Lista al día:**
    - una pregunta nueva sube su conversación a «Hoy»;
    - borrar desde el chat o «Borrar historial» en Ajustes la quitan de la lista;
    - durante una respuesta, la lista no parpadea.
  - **H10 — Accesibilidad:**
    - objetivos de 48 dp; cabeceras de grupo como encabezados;
    - en horizontal, la misma lista a todo el ancho, y el estado vacío sin icono;
    - con la letra al 200 %, el chip «Abierta» baja bajo la fecha si no cabe a su lado;
    - modo oscuro con los tokens de siempre.
  - **H11 — Textos nuevos en inglés:**
    - «Historial» → "History";
    - «Hoy» → "Today", «Ayer» → "Yesterday", «Últimos 7 días» → "Last 7 days", «Octubre de 2026» → "October 2026";
    - «1 pregunta / N preguntas» → "1 question / N questions";
    - «Abierta» → "Open";
    - «Abrir conversación» → "Open conversation";
    - «Borrar conversación: …» → "Delete conversation: …";
    - «Aún no hay conversaciones» → "No conversations yet";
    - «Las conversaciones que tengas en el chat aparecerán aquí.» → "Your chat conversations will appear here.";
    - «No se pudo borrar la conversación.» → "Couldn't delete the conversation.".

    Los demás textos ya existen en los dos idiomas (FR-023).
- **Título:** es la primera pregunta tal cual, sin resumirla con el modelo. Generar títulos cargaría Gemma solo para eso, contra el uso de recursos de la constitución IV.
- **Fecha de última actividad:** es la de la última pregunta. Abrir una conversación sin preguntar no la cambia.
- **Tandas:** del mismo tamaño que las de la Biblioteca (50), con el mismo comportamiento al desplazarse. El plan confirma el valor.
- **Sin borrado automático** (clarify): nada se borra solo (FR-022). Un límite, si hiciera falta, se decidiría con las cifras de SC-010 en otra feature.
- **Historial grande de prueba** (SC-001, SC-010): conversaciones sintéticas creadas solo para medir, en un almacenamiento de prueba aparte que se borra al terminar. Nunca se mezclan con el historial del usuario.
- **Teléfono del usuario:** tiene una conversación suya y una de prueba de F12. En la fase del teléfono no se abre, no se captura ni se borra la del usuario. La de prueba se borra desde Historial solo con su permiso.
- **Fuera de alcance:**
  - renombrar conversaciones, buscar en el historial, fijar o marcar favoritas, y «Deshacer» tras borrar;
  - borrar varias a la vez (FR-015); borrarlas todas sigue en Ajustes (F12);
  - exportar y restaurar (D-007);
  - títulos generados por el modelo;
  - cambios en el prompt o en la recuperación (constitución III);
  - INT8 y palancas de rendimiento (F14).
- **Dependencias:**
  - F11: conversaciones guardadas, sesión del chat, menú «⋯», fuentes y «Fuente ya no disponible»;
  - F12: «Borrar historial de chat» y su aviso a la pestaña Chat;
  - F08: carga por tandas de la Biblioteca y aviso del detalle cuando falta un fragmento.
