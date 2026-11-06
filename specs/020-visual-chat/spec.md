# Especificación de feature: Chat con páginas, modo «Multimodal» opcional (F20)

**Feature Branch**: `020-visual-chat`

**Created**: 2026-11-05

**Status**: Draft

**Input**: Descripción del usuario: «F20 — Chat con páginas, segunda mitad del modo «Multimodal» opcional (docs/roadmap/FEATURES.md §F20, D-066, D-076, D-078, D-082). Feature de producto sin mockup (propuesta de UI G47 aprobada antes del plan, con 04-chat.png y 07-detalle-documento.png como base). Con el modo apagado, o con documentos de solo texto, el chat se comporta exactamente como hoy. Con el modo activo: la recuperación de texto sigue como hoy (D-034) y las páginas salen de su propia lista (el vector de la consulta, con la 440M, frente a los de página), sin fusionar las tres listas con RRF, respetando el ámbito de F17 y el filtro de G28; el contexto son los bloques de texto de D-034 sin tocar más 1 página con el detalle que elige el usuario, sin regla de duplicados. Ajustes → «Detalle de las páginas»: Rápido, Equilibrado o Detallado (1 página a 70, 140 o 280 tokens visuales; unos 10, 11,5 y 14,5 s de primer token en el A063), solo visible con el modo activo. Prompt multimodal con la plantilla v5-visual de F18 (texto e imágenes numeradas [n]); Gemma con el codificador de visión en CPU y una ventana de 8.192 tokens solo en este modo. Citas a página: cada [n] corresponde a un documento y una página reales, un [n] fuera de rango se descarta; la ficha abre el detalle con la imagen de la página; «Fuentes consultadas» muestra la página; Historial restaura las citas a página (chat.db v3) y, si la página ya no existe, muestra «Fuente ya no disponible». Las imágenes de página tienen descripción para TalkBack (documento y página). Diagnóstico de búsqueda en debug con las tres listas. Prompts, respuestas e imágenes nunca en los logs de release; nunca dos engines en GPU y el codificador de visión se libera con el LLM. El primer token con cada detalle se mide en el teléfono como cifra informativa, sin regla de sí o no (D-078). Riesgos: primer token de 10–15 s, cifras deformadas por fp16 en GPU (F14), citas inconsistentes sobre imágenes y la memoria (RSS de unos 3,9 GB con la visión cargada). El A063 ya está en la 440M con el modo activado (D-082).» Ver `docs/roadmap/FEATURES.md` (F20).

**Fuentes**:
- **Sin mockup para lo nuevo.** Los PNG no tienen páginas en el chat ni visor de página. La UI se propone con el design system y el usuario la aprueba antes del plan (constitución VII, G47). Como base:
  - `04-chat.png` y `screens/04-chat.md`: «Fuentes consultadas» con archivo y página, «Fuente ya no disponible», y que una cita abre la ficha y la ubicación;
  - `07-detalle-documento.png` y `screens/07-document-detail.md`, para la página citada;
  - `09-configuracion.png` y el bloque «Modo multimodal» de F19, para «Detalle de las páginas».
- DECISIONS:
  - D-078: el modo opcional, sin regla de duplicados, el detalle elegible y sin pruebas comparativas;
  - D-076: las páginas salen de su propia lista, no de la RRF de tres listas;
  - D-077: las cifras de F18 y los hallazgos de fp16;
  - D-034: los bloques de texto del contexto;
  - D-053: la numeración de las fuentes que se muestran;
  - D-070 y D-071: las carpetas y el ámbito del chat;
  - D-079 a D-082: las decisiones y el resultado de F19.
- F18:
  - `specs/018-visual-rag-feasibility/benchmarks.md`: primer token con 1 página, p95 de 9,8 s a 70 tokens, 11,5 s a 140 y 14,5 s a 280; RSS pico de 3,5–3,9 GB; la visión de Gemma solo en CPU;
  - la plantilla v5-visual (`offline/eval/visual_prompt.py`): la v4 con una regla más («Algunas FUENTES son imágenes de páginas: léelas y cítalas igual que las demás») y cada página como su etiqueta («[n] Documento, página p») seguida de su imagen.
- F19: `specs/019-visual-indexing/spec.md` y `report.md`. Las páginas a 768 px en JPEG 85, sus vectores de 256D y el espacio de páginas en `rag_meta`.
- **Constitución 1.2.0:** ya cubre F20 y no hace falta enmienda. III admite citas a una página real, y IV da a las respuestas con páginas un primer token propio, medido y avisado, con la visión en CPU.
- FEATURES: F20 (objetivo, criterios 1–7 y riesgos).

**Verificado al redactar** (2026-11-05):
- **Recuperación:**
  - la consulta se embebe en CPU con el embedder de texto del índice, que con el modo activo es la 440M;
  - la búsqueda es híbrida, vector y FTS5, fusionada con RRF (k = 60, 50 + 50 candidatos, 30 al selector);
  - el selector de D-034 da como mucho 4 bloques y 3 por documento, con un presupuesto de 1.719 tokens estimados y sin vecinos;
  - el ámbito de F17 (carpetas, «General» o documentos) y el filtro de buscables (indexado e incluido) se aplican a las dos búsquedas;
  - **nada del chat lee hoy las páginas** (FR-024 de F19). Los documentos solo con páginas se cuentan aparte y no cuentan como buscables.
- **Respuesta:**
  - la plantilla es la v4: un único mensaje con las reglas, las fuentes numeradas `[n] documento › encabezados (p. N)`, la negativa explícita y el turno anterior recortado;
  - el prompt de texto tiene un límite de 2.000 tokens estimados. Si se pasa, cae primero el bloque peor clasificado y luego el turno anterior;
  - Gemma tiene una ventana de 4.096 tokens y como mucho 512 de salida, en GPU, sin visión. Sin bloques, se guarda la negativa sin llamar a Gemma;
  - nunca hay dos engines en GPU. Gemma se libera durante la indexación y al pasar la app a segundo plano.
- **Citas:**
  - solo se guardan los `[n]` citados de verdad y dentro de rango; un `[n]` fuera de rango se quita del texto;
  - «Fuentes consultadas» muestra una tarjeta por fuente con el nombre, la página y los fragmentos;
  - pulsarla abre el detalle del documento con el fragmento citado resaltado.
- **Historial (`chat.db` v2):**
  - cada fuente guarda el documento, el fragmento, su rango, el nombre y la página, sin ninguna columna de imagen ni de tipo;
  - si el documento ya no existe, la tarjeta dice «Fuente ya no disponible» y no se puede pulsar;
  - si el fragmento ya no existe, el detalle se abre y avisa «El fragmento citado ya no está en este documento».
- **Ajustes:** el bloque «Modo multimodal» tiene el interruptor y, con el modo activo, «Modo al importar PDF». No hay nada del detalle de las páginas, y su almacén no tiene ese campo.
- **Diagnóstico de F18 (solo en debug):** genera con Gemma y con imágenes (visión en CPU, ventana de 8.192 tokens, presupuesto de tokens por imagen).
- **Diagnóstico de búsqueda (solo en debug):** muestra los tiempos, los bloques y una tabla de candidatos fusionados, sin las listas por separado ni las páginas.
- **Primer token de extremo a extremo:** `ChatBenchmarkTest` lo mide en el teléfono con las 50 preguntas de paridad, y lo informa sin fallar.
- **Registros:** en release no se escribe nada. Las pruebas comprueban que el chat, la recuperación y el índice registran solo cifras.

## Clarifications

### Session 2026-11-05

- Q: Con el modo activo, ¿cuándo entra una página en la respuesta? → A: Siempre que haya páginas en el ámbito, la mejor de la lista, sin umbral de parecido. Para respuestas rápidas sobre texto, la persona acota el ámbito a carpetas sin páginas (FR-004).
- Q: ¿Qué detalle de las páginas viene por defecto? → A: Equilibrado, 1 página a 140 tokens, los mismos con que se indexan las páginas (FR-012).
- Q: ¿Cómo se ve y se amplía la página citada en el detalle? → A: La página a lo ancho en lugar del fragmento resaltado; al tocarla se abre a pantalla completa, con pellizco y doble toque para ampliar y un botón para cerrar (FR-016).
- Q: ¿Apruebas el wireframe? → A: Sí, tal como está en [wireframe.md](wireframe.md) (FR-024, G47). Sin enmienda de la constitución: la 1.2.0 ya cubre F20.

### Session 2026-11-05 (analyze)

- **I1:** Gemma se carga con la visión solo cuando entra una página. Sin páginas, la respuesta es la de hoy en prompt, motor, espera y memoria (FR-001, FR-005, FR-009). Lo decidió el usuario.
- **§10 del wireframe:** los textos de las imágenes sueltas, la carga de la vista previa y del visor, y el anuncio «Leyendo 1 página». Aprobado por el usuario.
- **I5:** una página que ya no está, en un documento que sigue, se puede pulsar y abre el documento con su aviso (FR-018), como un fragmento que ya no está. Solo un documento borrado dice «Fuente ya no disponible».

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Preguntar sobre un escaneado o una imagen (Priority: P1)

Con el modo activo, una persona ha importado un PDF escaneado y la foto de un cartel, que solo tienen páginas. Pregunta por un dato que está en el cartel. La app busca entre las páginas, Gemma lee la mejor y responde citándola. En «Fuentes consultadas» aparece la página, y pulsarla la abre.

**Why this priority**: es lo que el texto no puede hacer y la razón del modo (D-078). Sin ello, los documentos solo con páginas que indexa F19 no sirven de nada.

**Independent Test**: con el modo activo y un documento solo con páginas, preguntar por un dato de una de ellas. Comprobar que la respuesta cita esa página y que la cita lleva a un documento y una página reales.

**Acceptance Scenarios**:

1. **Given** el modo activo y una biblioteca con documentos solo con páginas, **When** la persona abre el chat, **Then** el chat está disponible: esos documentos cuentan como buscables.
2. **Given** una pregunta sin bloques de texto que respondan pero con una página relacionada, **When** se responde, **Then** Gemma recibe esa página y la respuesta puede citarla.
3. **Given** que la respuesta cita la página, **When** termina, **Then** «Fuentes consultadas» muestra el documento, el número de página y una miniatura de la página.
4. **Given** que la página no contiene la respuesta, **When** Gemma no la encuentra, **Then** la respuesta es la negativa explícita, sin citas.

---

### User Story 2 - Preguntar con texto y páginas a la vez (Priority: P1)

En una biblioteca con documentos en Ambos, la persona pregunta por un gráfico de un informe. La app reúne los bloques de texto de siempre y, además, la mejor página. Antes del primer token, el chat avisa de que está leyendo una página y de que tardará más. La respuesta cita a la vez fragmentos y la página.

**Why this priority**: es el caso normal del modo. Un gráfico o una tabla se entienden mejor con la página que con su texto extraído.

**Independent Test**: preguntar por un dato que está en el texto y en una página de un documento en Ambos. Comprobar que entran los mismos bloques de texto que con el modo apagado más una página, y que las citas de los dos tipos llevan a fuentes reales.

**Acceptance Scenarios**:

1. **Given** el modo activo y páginas en el ámbito, **When** se responde, **Then** entran los mismos bloques de texto que con el modo apagado (D-034) y, además, la página mejor clasificada de la lista de páginas, con el detalle elegido.
2. **Given** que la página es del mismo documento que un bloque de texto, **When** entran los dos, **Then** se mantienen los dos: no hay regla de duplicados.
3. **Given** una respuesta con página, **When** el chat espera al primer token, **Then** se ve que está leyendo una página, con la espera aproximada del detalle elegido. Se puede cancelar como hoy.
4. **Given** una cita a un número que no existe, **When** se muestra la respuesta, **Then** se descarta, como hoy.

---

### User Story 3 - Sin modo o sin páginas, todo igual (Priority: P1)

Quien no activa el modo, o lo tiene activo con documentos solo de texto, usa el chat exactamente como hoy: mismo prompt, mismas fuentes, misma espera.

**Why this priority**: es la garantía del modo opcional (D-078). Romper el chat de texto anularía todo lo anterior.

**Independent Test**: con el modo apagado, y con el modo activo y un ámbito sin páginas, comparar el prompt, las fuentes y las cifras del harness y del chat con las de F19.

**Acceptance Scenarios**:

1. **Given** el modo apagado, **When** se pregunta, **Then** el prompt y las fuentes son los de hoy, aunque haya documentos con páginas guardadas.
2. **Given** el modo activo y un ámbito sin páginas indexadas, **When** se pregunta, **Then** el prompt es el de hoy y no entra ninguna página.
3. **Given** el modo apagado y documentos solo con páginas, **When** se abre el chat, **Then** esos documentos siguen sin contar como buscables, como en F19.

---

### User Story 4 - Elegir el detalle de las páginas (Priority: P2)

En Ajustes, debajo del modo multimodal, la persona elige «Detalle de las páginas»: Rápido, Equilibrado o Detallado. Cada opción dice cuánto tarda aproximadamente la respuesta.

**Why this priority**: el detalle cambia la espera de unos 6,5 a 14,5 s, y D-078 deja que lo elija el usuario.

**Independent Test**: cambiar el detalle y comprobar que se guarda, que la siguiente respuesta con página usa ese detalle y que la opción solo se ve con el modo activo.

**Acceptance Scenarios**:

1. **Given** el modo activo, **When** la persona abre Ajustes, **Then** ve «Detalle de las páginas» con el valor actual. Con el modo apagado, no se ve.
2. **Given** la elección, **When** se abre, **Then** cada opción dice su espera aproximada, medida en el teléfono de referencia.
3. **Given** que elige otro detalle, **When** se guarda, **Then** la siguiente respuesta con página lo usa. Si guardar falla, se avisa y se mantiene el anterior.

---

### User Story 5 - Ver la página citada (Priority: P2)

La persona pulsa la página en «Fuentes consultadas». Se abre el detalle del documento con la imagen de esa página, que puede ampliar para leer una tabla. Al volver, sigue en la conversación.

**Why this priority**: sin ver la página, una cita a página no se puede comprobar (constitución III).

**Independent Test**: desde una respuesta con una página citada, abrirla, ampliarla y volver al chat.

**Acceptance Scenarios**:

1. **Given** una cita a página, **When** se pulsa, **Then** se abre el detalle del documento con la imagen de esa página y su número.
2. **Given** la imagen, **When** la persona la toca, **Then** se abre a pantalla completa y puede ampliarla con pellizco o doble toque para leer la página.
3. **Given** el detalle abierto desde el chat, **When** vuelve atrás, **Then** sigue en la conversación, como con las citas de texto.

---

### User Story 6 - Recuperar del Historial una respuesta con páginas (Priority: P2)

La persona abre una conversación antigua del Historial. Las citas a página siguen ahí, con su miniatura. Si el documento se ha borrado, la cita dice «Fuente ya no disponible».

**Why this priority**: el Historial ya restaura las citas de texto (F12). Una cita a página que se pierde al reabrir sería una cita no trazable.

**Independent Test**: crear una respuesta con página, cerrar la app, reabrir la conversación desde el Historial y comprobar la cita. Después, borrar el documento o reindexarlo en Texto y comprobar su estado.

**Acceptance Scenarios**:

1. **Given** una conversación con citas a página, **When** se reabre desde el Historial, **Then** las citas salen igual, en el mismo orden y con su miniatura.
2. **Given** que el documento citado se ha eliminado, **When** se reabre, **Then** la cita dice «Fuente ya no disponible» y no se puede pulsar.
3. **Given** que el documento sigue pero la página ya no está (reindexado en Texto), **When** se pulsa la cita, **Then** se abre el documento y avisa de que la página citada ya no está.
4. **Given** el modo apagado después de usarlo, **When** se reabre una conversación con citas a página, **Then** siguen viéndose y abriéndose mientras la página exista: F19 conserva las páginas.

---

### User Story 7 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

Los cambios en el chat, el detalle y Ajustes siguen el design system, se aprueban antes del plan, funcionan con TalkBack y con la letra al 200 %, y están en español y en inglés.

**Why this priority**: es una regla del proyecto para todo lo que no tiene mockup (constitución VII).

**Independent Test**: revisar el wireframe aprobado frente a las pantallas, y recorrerlas con TalkBack, al 200 % y en los dos idiomas.

**Acceptance Scenarios**:

1. **Given** el wireframe aprobado, **When** se comparan las pantallas, **Then** coinciden, y las diferencias quedan anotadas.
2. **Given** TalkBack, **When** llega a una miniatura o a la imagen de una página, **Then** anuncia el documento y el número de página.
3. **Given** la letra al 200 %, **When** se recorren el chat, el detalle y Ajustes, **Then** nada se corta.

---

### User Story 8 - Diagnóstico de búsqueda con las páginas (Priority: P3)

En la build de depuración, el diagnóstico de búsqueda muestra las tres listas, vector, palabras y páginas, y qué entra en el contexto, para entender por qué una página entra o no.

**Why this priority**: ayuda a revisar las citas sobre imágenes, uno de los riesgos, pero no lo ve el usuario.

**Independent Test**: en debug, buscar una consulta y comprobar las tres listas y el contexto elegido.

**Acceptance Scenarios**:

1. **Given** el diagnóstico en debug con el modo activo, **When** se busca, **Then** se ven la lista de vectores, la de palabras, la fusionada, la de páginas y el contexto final.
2. **Given** la build de release, **When** se inspecciona, **Then** el diagnóstico no está.

---

### Edge Cases

- **La imagen de una página falta en el disco o no se puede leer:** la respuesta sigue sin esa página, solo con texto. Si tampoco hay texto, se da la negativa. El registro solo cuenta el caso.
- **El espacio de páginas del índice no coincide con el de la app:** no se buscan páginas y el texto sigue igual (constitución III).
- **Documentos indexándose:** solo entran páginas de documentos indexados e incluidos. Un documento que se reindexa sigue usando sus páginas anteriores hasta que se publica la versión nueva.
- **Documentos excluidos de las respuestas o fuera del ámbito:** sus páginas no entran.
- **El modo se apaga a mitad de una conversación:** las respuestas siguientes no usan páginas, y las citas anteriores se mantienen.
- **Pregunta de seguimiento:** entra el turno anterior como hoy, sin su página. La página se elige de nuevo para la pregunta nueva, con la misma consulta que el texto: en un seguimiento, también la pregunta anterior (la regla de F10).
- **Gemma cita la página con un número que no existe, o confunde la página del documento con el `[n]`:** se descarta lo que no exista. Solo se muestra una cita si su `[n]` corresponde a una fuente real.
- **Gemma no cita la página que recibió:** no se muestra. Solo aparecen las fuentes citadas, como hoy.
- **Cancelar mientras Gemma lee la página:** se detiene como hoy, en cuanto el motor lo permite, y la respuesta queda cancelada.
- **La app pasa a segundo plano a mitad de la respuesta:** se interrumpe y se libera Gemma con su visión, como hoy.
- **Memoria:** con la visión cargada, el pico de memoria sube (3,5–3,9 GB en F18). La app no debe cerrarse por memoria en el teléfono de referencia; se mide.
- **Imagen suelta (JPG o PNG):** su cita dice el nombre del documento, sin número de página, porque solo tiene una.
- **Un documento solo con páginas en un ámbito con documentos de texto:** compiten por igual. La página entra si es la mejor de la lista de páginas.
- **Mismo documento en texto y en página:** pueden entrar a la vez un bloque y su página, sin regla de duplicados (D-078).
- **Consulta mientras corre una indexación:** el chat espera como hoy («Esperando a la indexación»).

## Requirements *(mandatory)*

### Functional Requirements

**Recuperación y contexto**

- **FR-001**: Con el modo apagado, el chat MUST comportarse exactamente como sin esta feature: misma recuperación, mismo prompt, mismas fuentes y misma disponibilidad. Las páginas guardadas MUST NOT usarse.
- **FR-002**: Con el modo activo, la recuperación de texto MUST seguir igual (D-034). Además, las páginas MUST buscarse en su propia lista: el mismo vector de la consulta frente a los vectores de página. Esa lista MUST NOT fusionarse con la de texto (D-076).
- **FR-003**: La lista de páginas MUST respetar el ámbito de la conversación (F17) y el filtro de buscables: solo páginas de documentos indexados e incluidos.
- **FR-004**: Con el modo activo y alguna página en el ámbito, el contexto MUST ser los bloques de texto de D-034, sin tocar, más **1 página**, la primera de la lista de páginas, con el detalle elegido. La página MUST entrar siempre que haya alguna en el ámbito, sin umbral de parecido. MUST NOT quitarse ningún bloque por estar en esa página (D-078).
- **FR-005**: Sin ninguna página en el ámbito, o si el espacio de páginas del índice no coincide, el contexto y el prompt MUST ser los de hoy.
- **FR-006**: Con el modo activo, los documentos solo con páginas MUST contar como buscables para la disponibilidad del chat. Una pregunta sin bloques de texto pero con una página MUST responderse con la página. Sin bloques ni páginas, MUST darse la negativa sin llamar a Gemma, como hoy.
- **FR-006b**: Con el modo activo, el detalle de un documento solo con páginas MUST dejar de decir que no se usa en el chat, y su interruptor «Incluir en respuestas» MUST decir lo mismo que el de un documento de texto. Con el modo apagado, MUST decir que se usará al activar el modo (wireframe §7).

**Prompt y motor**

- **FR-007**: Una respuesta con página MUST usar la plantilla v5-visual de F18:
  - las reglas de la v4 más una regla que dice que algunas fuentes son imágenes de páginas;
  - las fuentes de texto numeradas como hoy, y la página después, con su número `[n]`, su etiqueta («Documento, página p», o solo el nombre para una imagen suelta) y su imagen.

  Una respuesta sin página MUST usar la v4 sin cambios.
- **FR-008**: La imagen que recibe Gemma MUST ser la de la página guardada en la indexación (F19), con el número de tokens del detalle elegido.
- **FR-009**: Con el modo activo, Gemma MUST poder leer imágenes, con el codificador de visión en CPU y una ventana de 8.192 tokens. Con el modo apagado, Gemma MUST cargarse como hoy. El límite del prompt de texto y la salida máxima MUST NOT cambiar.
- **FR-010**: MUST NOT haber nunca dos engines en GPU. El codificador de visión MUST liberarse siempre con Gemma: durante la indexación, al pasar a segundo plano y al cerrar.

**Detalle de las páginas**

- **FR-011**: Con el modo activo, Ajustes MUST tener «Detalle de las páginas», debajo de «Modo al importar PDF», con tres opciones:
  - **Rápido:** 1 página a 70 tokens;
  - **Equilibrado:** 1 página a 140 tokens;
  - **Detallado:** 1 página a 280 tokens.

  Cada opción MUST decir su espera aproximada en el teléfono de referencia: unos 6,5 s, 8,5 s y 14,5 s, medidos por F20 (D-086). Con el modo apagado, MUST NOT verse.
- **FR-012**: El detalle MUST guardarse como las demás preferencias, sin copia de seguridad. Si guardar falla, MUST avisarse y mantenerse el valor anterior. El valor por defecto MUST ser Equilibrado.
- **FR-013**: Mientras se espera el primer token de una respuesta con página, el chat MUST indicar que está leyendo una página, con la espera aproximada del detalle elegido (constitución IV).

**Citas a página**

- **FR-014**: Toda cita a página MUST corresponder a un documento real y a una página real indexada de ese documento. Un `[n]` fuera de rango MUST descartarse, como hoy.
- **FR-015**: «Fuentes consultadas» MUST mostrar cada página citada con el nombre del documento, el número de página y una miniatura de la página, en el orden de las citas, como las fuentes de texto. El contador de fuentes de la respuesta MUST contarla.
- **FR-016**: Pulsar una cita a página MUST abrir el detalle del documento con la imagen de esa página a lo ancho y su número, en lugar del fragmento resaltado. Tocar la imagen MUST abrirla a pantalla completa, con pellizco y doble toque para ampliar y un botón para cerrar. Volver MUST dejar a la persona en la conversación.
- **FR-017**: El Historial MUST guardar y restaurar las citas a página, con el tipo de fuente, el documento, el número de página y el nombre. Las conversaciones existentes MUST seguir abriéndose igual tras la actualización.
- **FR-018**: Si el documento citado ya no existe, la cita MUST decir «Fuente ya no disponible» y no poder pulsarse. Si el documento existe pero la página no, pulsarla MUST abrir el documento y avisar de que la página citada ya no está.
- **FR-019**: Las citas a página guardadas MUST seguir viéndose y abriéndose con el modo apagado, mientras la página exista.

**Calidad**

- **FR-020**: Las miniaturas y las imágenes de página MUST tener una descripción para TalkBack con el documento y el número de página.
- **FR-021**: Los prompts, las respuestas, las imágenes y los nombres de documento MUST NOT aparecer en los registros de release. En debug, el chat MUST registrar solo cifras, también con páginas (constitución I).
- **FR-022**: En debug, el diagnóstico de búsqueda MUST mostrar por separado la lista de vectores, la de palabras, la fusionada y la de páginas, y el contexto final. En release MUST NOT existir.
- **FR-023**: Con el modo apagado, el harness del escritorio MUST dar las mismas cifras que al cerrar F19, y la batería de pruebas MUST pasar igual, salvo las pruebas de F19 que FR-006b sustituye a propósito.
- **FR-024**: Los cambios de pantalla MUST seguir el wireframe aprobado (G47), con TalkBack, la letra al 200 % y los textos en español y en inglés.

### Key Entities

- **Lista de páginas:** las páginas candidatas a una pregunta, en orden de parecido con la consulta, con su documento, su número y su distancia. Solo de documentos indexados, incluidos y dentro del ámbito.
- **Detalle de las páginas:** Rápido, Equilibrado o Detallado, con 70, 140 o 280 tokens por imagen. Es una preferencia, con Equilibrado por defecto.
- **Fuente de una respuesta:** de tipo **fragmento** (documento, fragmento, rango y página, como hoy) o **página** (documento y número de página). Tiene el número con que se muestra y el `[n]` con que la citó Gemma.
- **Respuesta con página:** el turno de chat cuyo contexto llevó una página. Se guarda qué detalle usó, para el diagnóstico y las mediciones.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con el modo apagado, o con el modo activo y un ámbito sin páginas, nada cambia:
  - el harness da las mismas cifras que al cerrar F19;
  - el prompt y las fuentes son idénticos (prueba automática);
  - la batería de pruebas pasa como al cerrar F19, salvo las pruebas de F19 que FR-006b sustituye a propósito.
- **SC-002**: Ninguna cita sale sin un documento y una página o un fragmento reales, y un `[n]` fuera de rango se descarta (pruebas automáticas).
- **SC-003**: En el teléfono de referencia se mide, como cifra informativa, el primer token de extremo a extremo con cada detalle (p50 y p95, con al menos 20 preguntas), junto con el pico de memoria. También se mide con el modo activo sin páginas. No hay umbral (D-078).
- **SC-004**: En el teléfono de referencia, una pregunta sobre un escaneado o una imagen recibe una respuesta que cita su página, y la cita abre la imagen de esa página.
- **SC-005**: El Historial restaura las citas a página tras cerrar la app. Una conversación anterior a la actualización se abre igual. Un documento borrado o una página que ya no existe muestran su estado (pruebas instrumentadas, con la migración del historial).
- **SC-006**: Nunca hay dos engines en GPU, y el codificador de visión se libera con Gemma (prueba automática).
- **SC-007**: Los registros de release no contienen prompts, respuestas, imágenes ni nombres (pruebas automáticas).
- **SC-008**:
  - el wireframe se aprueba antes del plan;
  - TalkBack anuncia el documento y la página de cada miniatura e imagen;
  - con la letra al 200 % nada se corta;
  - todos los textos nuevos están en español y en inglés.

## Assumptions

- **Parámetros de F18 y D-078:**
  - 1 página por respuesta, de la lista de páginas;
  - 70, 140 o 280 tokens según el detalle;
  - la visión de Gemma en CPU, porque el fichero solo la declara en CPU;
  - una ventana de 8.192 tokens en este modo.

  No se repiten pruebas comparativas ni hay regla de sí o no.
- **Las esperas de cada detalle** (unos 10, 11,5 y 14,5 s) son los p95 que midió F18 en el A063 con 1 página y la regla de duplicados. Sin esa regla entra algo más de texto, así que F20 las vuelve a medir y, si cambian, se corrigen los textos con las cifras nuevas. **Medidas en F20 (T058, D-086):** p95 de 6,7 s, 8,4 s y 14,4 s, así que los textos dicen unos 6,5 s, 8,5 s y 14,5 s.
- **La misma consulta** sirve para fragmentos y páginas: con el modo activo, la 440M embebe las dos cosas en el mismo espacio.
- **El generador con imágenes** del diagnóstico de F18 pasa de debug a `main` como adaptador, como hizo F19 con el embedder de imagen.
- **El A063 ya tiene el modo activado,** con la 440M y sin la 270M (D-082). Las pruebas que necesiten la 270M se preguntan antes.
- **Las cifras deformadas con fp16** (F18, D-077) pueden aparecer también en las respuestas con páginas. Se miran en F14, no aquí.
- **Sin red.** F20 no descarga nada.
