# Especificación de feature: Carpetas y ámbito del chat (F17)

**Feature Branch**: `017-folders-chat-scope`

**Created**: 2026-11-04

**Status**: Draft

**Input**: Descripción del usuario: «F17 — Carpetas y ámbito del chat (docs/roadmap/FEATURES.md §F17, D-066, G46). Carpetas de un solo nivel para organizar los documentos (p. ej. «Legal», «Motores»): crear, renombrar y eliminar; cada documento está en una carpeta o en «Sin carpeta», donde quedan los actuales; mover un documento de carpeta; elegir la carpeta de destino al importar. Biblioteca por carpetas con su recuento y, dentro, la lista actual con búsqueda y filtros. Chat con ámbito por conversación: una ficha encima del compositor y un selector de carpetas completas y documentos sueltos de carpetas distintas; se guarda con la conversación y se conserva al reabrirla desde Historial; «Incluir en RAG» sigue siendo la exclusión global. La recuperación filtra por el ámbito en las búsquedas vectorial y FTS. Sin mockup: la UI se propone (wireframe con componentes Lisdata*) y se aprueba antes del plan.» Ver `docs/roadmap/FEATURES.md` (F17).

**Fuentes**:
- **Sin mockup para lo nuevo.** Los PNG no tienen carpetas ni ámbito. La UI se propone con el design system y el usuario la aprueba antes de implementarla (constitución VII, D-004, G46). Los dibujos están en [wireframe.md](wireframe.md). Como base:
  - `06-biblioteca.png` y `screens/06-library.md`: búsqueda, filtros Todos/Indexados/Pendientes, lista paginada e interruptor «Incluir en RAG» («controlar qué fuentes participan en el RAG»; «En corpus grandes, paginar documentos y usar consultas SQL filtradas»);
  - `07-detalle-documento.png` y `screens/07-document-detail.md`: el «⋯» del detalle, con «Quitar del índice» (G17);
  - `05-ingesta.png` y `screens/05-ingestion.md`: selector de archivos y cola;
  - `04-chat.png` y `screens/04-chat.md`: compositor y «Recuperar fragmentos locales según documentos incluidos».
- `docs/disegn_app/docs/`: `00-design-system.md` (tokens, 48 dp, letra dinámica, modo oscuro), `01-navigation.md` (rutas y barra inferior) y `03-components.md` (un solo sistema de componentes).
- `docs/roadmap/INVENTARIO.md`: fichas P04–P07, G16 (filtros), G17 (acciones del detalle), G28 (exclusión en la búsqueda) y G46 (carpetas y ámbito, abierto).
- Constitución v1.1.1:
  - I: sin red; los nombres de carpetas y documentos son contenido del usuario y no van a los registros;
  - III: la recuperación (RRF, candidatos, contexto) solo cambia con una evaluación; F17 solo filtra lo que entra;
  - IV: consultas agregadas y paginadas; nada de base de datos en el hilo principal;
  - V: un documento excluido nunca se busca; eliminar exige confirmación y limpieza coherente;
  - VI: pruebas de datos, migraciones y ViewModels;
  - VII: lo no maquetado se propone y se aprueba antes.
- DECISIONS:
  - D-040: la búsqueda filtra `status = 'done' AND included = 1` pidiendo top-(k + m), con m los fragmentos no buscables (G28);
  - D-053 y D-054: conversaciones guardadas; la pestaña Chat abre la última; una sola respuesta en curso;
  - D-063 a D-065: Historial, y los identificadores de documentos reutilizados (un documento creado después no cuenta como fuente de una respuesta anterior);
  - D-066: carpetas de un nivel, una por documento, «Sin carpeta» para los actuales; ámbito por conversación; «Incluir en RAG» sigue como exclusión global.
- FEATURES: F17 (criterios 1–8 y riesgos), F08, F10, F11 y F13.

**Verificado al redactar** (2026-11-04):
- **No hay carpetas, etiquetas ni ámbito** en el código ni en los documentos.
- **Visibilidad en la búsqueda:** se decide en un único punto, «indexado e incluido», en las dos búsquedas (por significado y por palabras), en la carga de candidatos y en los vecinos. Para que el resultado sea exacto, cada búsqueda pide tantos candidatos de más como fragmentos no visibles haya.
- **Petición de búsqueda:** solo lleva la pregunta y la anterior; no hay ningún filtro por documento.
- **Conversación guardada:** no guarda ninguna configuración. Se guardan sus preguntas, respuestas y fuentes (con el documento y el fragmento citados).
- **Biblioteca:** barra superior con «＋» (importar), titular «Tus documentos», búsqueda por nombre, filtros y la lista por tandas de 50, con el interruptor «Incluir en RAG» en cada fila.
- **Detalle:** el «⋯» solo tiene «Quitar del índice»; las acciones son «Reindexar documento» y «Eliminar».
- **Ingesta:** el selector de archivos del sistema y una cola. El documento se registra al elegirlo, antes de indexar, sin ningún destino.
- **Chat:**
  - el compositor va abajo y encima se muestra un aviso cuando hace falta (por ejemplo, «cobertura parcial» si hay documentos sin indexar);
  - estados del chat vacío: biblioteca vacía, nada indexado, esperando a la indexación, modelos sin preparar y error.
- **Inicio:** recuentos de documentos y fragmentos de toda la biblioteca, y el acceso «Documentos» a la Biblioteca.
- **Componentes disponibles:**
  - filas de ajustes y de documento, filas de radio, interruptores, fichas de filtro;
  - hoja inferior, diálogo de confirmación con hueco para contenido, menú «⋯»;
  - cabecera de sección, estados vacío, error y carga;
  - campo de búsqueda.

  No hay campo de texto para nombres, casillas con tres estados ni filas desplegables.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **Usuario:** ordena sus documentos por temas y pregunta solo sobre los que le interesan en cada conversación.

### User Story 1 - Ordenar los documentos en carpetas (Priority: P1)

En la Biblioteca, el usuario ve sus carpetas con el número de documentos de cada una, más «Sin carpeta» para los que no tienen. Crea la carpeta «Legal», le cambia el nombre si se equivoca y la elimina cuando ya no la quiere. Al entrar en una carpeta ve sus documentos con la búsqueda y los filtros de siempre.

**Why this priority**: es la mitad del encargo (D-066) y la base del ámbito del chat.

**Independent Test**: con una biblioteca de prueba:
- crear dos carpetas, renombrar una y eliminar otra;
- comprobar los recuentos de cada carpeta;
- entrar en cada una y ver solo sus documentos, con búsqueda y filtros;
- al actualizar desde una versión sin carpetas, todos los documentos están en «Sin carpeta».

**Acceptance Scenarios**:

1. **Given** la app actualizada desde una versión sin carpetas, **When** el usuario abre la Biblioteca, **Then** ve «Sin carpeta» con todos sus documentos y ninguna otra carpeta.
2. **Given** la Biblioteca, **When** el usuario toca «Nueva carpeta», escribe «Legal» y la crea, **Then** «Legal» aparece en la lista, con 0 documentos.
3. **Given** un nombre vacío o igual a otro (sin importar mayúsculas ni tildes), **When** el usuario intenta crear o renombrar, **Then** la app no lo acepta y dice por qué.
4. **Given** una carpeta, **When** el usuario la renombra, **Then** el nombre nuevo aparece en la Biblioteca, en el detalle de sus documentos y en el ámbito de los chats que la usan.
5. **Given** una carpeta con documentos, **When** el usuario la elimina y confirma, **Then** la carpeta desaparece y sus documentos pasan a «Sin carpeta», sin borrarse ninguno (K4).
6. **Given** una carpeta, **When** el usuario entra, **Then** ve solo sus documentos, con la búsqueda, los filtros Todos/Indexados/Pendientes, la carga por tandas y el interruptor «Incluir en RAG», como en la Biblioteca de hoy.
7. **Given** la Biblioteca, **When** el usuario escribe en la búsqueda sin entrar en ninguna carpeta, **Then** encuentra documentos de todas las carpetas, con el nombre de la carpeta de cada uno.

---

### User Story 2 - Mover documentos y elegir el destino al importar (Priority: P1)

Desde el detalle de un documento, el usuario lo mueve a otra carpeta, o a «Sin carpeta». Al importar elige antes la carpeta de destino, y los documentos que elige se guardan allí.

**Why this priority**: sin mover ni elegir destino, las carpetas solo servirían para lo que se importe después, y lo ya importado quedaría en «Sin carpeta».

**Independent Test**: mover un documento entre tres carpetas y a «Sin carpeta»; importar dos documentos con un destino elegido y comprobar que quedan en él, también si se importan desde dentro de una carpeta.

**Acceptance Scenarios**:

1. **Given** el detalle de un documento, **When** el usuario elige «Mover a carpeta» y una carpeta, **Then** el documento pasa a ella, el detalle lo dice, y los recuentos de las dos carpetas cambian.
2. **Given** «Mover a carpeta», **When** el usuario elige «Nueva carpeta», **Then** puede crearla allí mismo, y el documento pasa a la carpeta nueva.
3. **Given** la Ingesta, **When** el usuario elige «Legal» como destino y después los archivos, **Then** esos documentos quedan en «Legal».
4. **Given** el usuario dentro de la carpeta «Motores», **When** toca «＋» para importar, **Then** el destino ya es «Motores».
5. **Given** un documento que ya estaba en la biblioteca (duplicado), **When** se intenta importar con otro destino, **Then** se avisa como hoy y el documento no cambia de carpeta.
6. **Given** un documento movido de carpeta, **When** termina, **Then** no se reindexa ni cambia su inclusión en las búsquedas.

---

### User Story 3 - Preguntar solo a unas carpetas o documentos (Priority: P1)

En el chat, el usuario ve encima del compositor sobre qué se busca: «Toda la biblioteca» por defecto. Lo toca y elige carpetas completas, documentos sueltos de carpetas distintas, o las dos cosas. Las respuestas siguientes solo usan esos documentos.

**Why this priority**: es el objetivo del encargo («activar una carpeta completa, varias carpetas, documentos sueltos de diferentes carpetas»).

**Independent Test**: con documentos de prueba sobre temas distintos en dos carpetas:
- una pregunta con el ámbito «Legal» solo cita documentos de «Legal»;
- con «Legal» y un documento suelto de «Motores», cita solo esos;
- con «Toda la biblioteca», las respuestas son las mismas que hoy.

**Acceptance Scenarios**:

1. **Given** un chat nuevo, **When** el usuario mira encima del compositor, **Then** ve la ficha «Toda la biblioteca».
2. **Given** la ficha, **When** el usuario la toca, **Then** se abre el selector, con «Toda la biblioteca», las carpetas y, dentro de cada una, sus documentos.
3. **Given** el selector, **When** el usuario marca la carpeta «Legal» y aplica, **Then** la ficha dice «Legal», y las respuestas siguientes solo usan documentos de «Legal».
4. **Given** el selector, **When** el usuario marca «Legal» y, en «Motores», un solo documento, **Then** la ficha dice «Legal + 1 documento», y las respuestas usan los documentos de «Legal» y ese documento.
5. **Given** un ámbito con una carpeta, **When** se importa o se mueve un documento a esa carpeta y termina de indexarse, **Then** entra en el ámbito sin hacer nada más.
6. **Given** un ámbito, **When** el usuario lo cambia a mitad de la conversación, **Then** las respuestas anteriores conservan sus fuentes y las siguientes usan el ámbito nuevo.
7. **Given** un documento con «Incluir en RAG» desactivado, **When** está en el ámbito, **Then** no se busca, como hoy (exclusión global), y el selector lo dice.
8. **Given** «Toda la biblioteca», **When** el usuario pregunta, **Then** la búsqueda y la respuesta son las mismas que sin carpetas.
9. **Given** una respuesta en curso, **When** el usuario abre el selector, **Then** puede cambiar el ámbito, y el cambio se aplica a la pregunta siguiente; la respuesta en curso sigue con el ámbito con el que empezó.

---

### User Story 4 - El ámbito se guarda con la conversación (Priority: P2)

Cada conversación recuerda su ámbito. Al reabrirla desde Historial, al volver a la pestaña Chat o tras cerrar la app, sigue preguntando sobre las mismas carpetas y documentos.

**Why this priority**: «se conserva al reabrirla desde Historial» (D-066). Sin eso, continuar una conversación sobre «Legal» buscaría en toda la biblioteca sin avisar.

**Independent Test**: tres conversaciones con ámbitos distintos; reabrir cada una desde Historial, tras girar y tras cerrar la app: cada una conserva el suyo.

**Acceptance Scenarios**:

1. **Given** una conversación con el ámbito «Legal», **When** el usuario la reabre desde Historial, **Then** la ficha dice «Legal», y sus preguntas siguientes solo usan «Legal».
2. **Given** «Nuevo chat», **When** se abre, **Then** su ámbito es «Toda la biblioteca» (K11).
3. **Given** un ámbito elegido en un chat sin preguntas todavía, **When** el usuario hace la primera pregunta, **Then** la conversación se guarda con ese ámbito.
4. **Given** una conversación de antes de esta versión, **When** se abre, **Then** su ámbito es «Toda la biblioteca».
5. **Given** la app cerrada por el sistema con un chat abierto, **When** el usuario vuelve, **Then** el chat tiene el mismo ámbito.

---

### User Story 5 - Ámbitos que ya no existen o que no tienen nada (Priority: P2)

Si una carpeta o un documento del ámbito se borra, el ámbito se ajusta y la app lo dice. Si en el ámbito no queda nada indexado, el chat lo explica y ofrece cambiarlo.

**Why this priority**: criterio 5 de F17. Una pregunta sobre un ámbito vacío daría siempre la negativa sin explicar por qué.

**Independent Test**: borrar una carpeta y un documento que están en el ámbito de una conversación; dejar un ámbito con documentos solo pendientes; abrir la conversación en cada caso.

**Acceptance Scenarios**:

1. **Given** una conversación con «Legal» y «Motores», **When** se elimina «Motores», **Then** al abrirla la ficha dice «Legal», con un aviso de que una carpeta del ámbito ya no existe.
2. **Given** un ámbito de documentos sueltos, **When** se eliminan todos, **Then** el ámbito vuelve a «Toda la biblioteca», y la app lo dice.
3. **Given** un ámbito cuyos documentos están todos sin indexar o excluidos, **When** el usuario abre el chat, **Then** ve «En este ámbito no hay documentos listos para buscar» con «Cambiar ámbito», y no puede preguntar hasta cambiarlo o hasta que se indexen.
4. **Given** un ámbito con algunos documentos aún sin indexar, **When** el usuario pregunta, **Then** la respuesta usa los indexados, y el aviso de cobertura parcial se refiere al ámbito, no a toda la biblioteca.
5. **Given** un documento borrado que se importa de nuevo y recibe el mismo identificador interno, **When** se abre una conversación cuyo ámbito tenía el borrado, **Then** el documento nuevo no entra en ese ámbito (D-065).

---

### User Story 6 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

Las pantallas nuevas y las cambiadas siguen el wireframe aprobado, funcionan con TalkBack, con la letra al 200 %, en horizontal y en los dos temas, y tienen sus textos en español y en inglés.

**Why this priority**: criterios 1 y 8 de F17 y constitución VII.

**Independent Test**:
- wireframe y propuestas K aprobados antes de `/speckit-plan`;
- comprobaciones automáticas de accesibilidad en la Biblioteca, la carpeta, los diálogos, el detalle, la Ingesta, el selector y el chat;
- capturas del teléfono frente al wireframe.

**Acceptance Scenarios**:

1. **Given** TalkBack en la Biblioteca, **When** el usuario recorre las carpetas, **Then** cada una se lee como una frase («Legal, 12 documentos») con su acción.
2. **Given** TalkBack en el chat, **When** el usuario llega a la ficha, **Then** la oye como «Buscar en: Legal», con la acción de cambiarlo.
3. **Given** TalkBack en el selector, **When** el usuario recorre carpetas y documentos, **Then** cada casilla dice su estado: marcada, sin marcar o, en una carpeta, «algunos documentos».
4. **Given** la letra al 200 % o el teléfono en horizontal, **When** se abren la Biblioteca, la carpeta, el selector y el chat, **Then** nada se corta, y la ficha del ámbito se acorta con «…» sin tapar el compositor.
5. **Given** la app en inglés, **When** se usan las carpetas y el ámbito, **Then** todos los textos salen en inglés, y los nombres de carpeta tal como los escribió el usuario.

---

### User Story 7 - La búsqueda acotada sigue siendo rápida (Priority: P3)

Quien desarrolla mide la búsqueda con un ámbito pequeño sobre una biblioteca grande y deja las cifras en el informe.

**Why this priority**: es el riesgo principal de F17. Con un ámbito pequeño, la búsqueda exacta tendría que pedir casi todos los fragmentos como candidatos.

**Independent Test**: con 10.000 fragmentos de prueba y un ámbito de unos 1.000, el informe recoge el p50 y el p95 de la búsqueda completa en el teléfono de referencia, frente a los de «Toda la biblioteca».

**Acceptance Scenarios**:

1. **Given** la biblioteca de prueba, **When** se mide, **Then** el informe recoge las cifras con «Toda la biblioteca», con un ámbito de una carpeta y con uno de documentos sueltos.

---

### Edge Cases

- **Carpetas sin documentos:** aparecen con «0 documentos» y se pueden elegir como destino, pero no se ofrecen en el selector del chat, porque no aportan nada.
- **«Sin carpeta» vacía:** no se muestra en la Biblioteca ni en el selector.
- **Muchas carpetas:** la lista de la Biblioteca y la del selector se desplazan y cargan sin esperar a contar todos los documentos de una vez.
- **Nombres:** se recortan los espacios de los extremos. No se acepta uno vacío, uno de más de 40 caracteres ni «Sin carpeta», que está reservado. «legal» y «Legál» cuentan como el mismo nombre que «Legal».
- **Renombrar a lo mismo, con otras mayúsculas:** se acepta, y queda escrito como lo puso el usuario.
- **Eliminar una carpeta en el ámbito de una respuesta en curso:** la respuesta sigue con el ámbito con el que empezó; la pregunta siguiente usa el ámbito ya ajustado.
- **Mover un documento fuera de una carpeta del ámbito:** sale del ámbito, salvo que estuviera también elegido como documento suelto.
- **Un documento elegido suelto y además su carpeta entera:** cuenta una vez. La ficha lo resume por carpetas.
- **Reindexar un documento:** conserva su carpeta. La copia temporal de un reindexado no aparece como documento en ninguna carpeta.
- **Quitar del índice un documento del ámbito:** sigue en su carpeta y en el ámbito, pero no se busca hasta que se reindexe (como hoy con «Fuera del índice»).
- **Eliminar un documento:** desaparece de su carpeta y de todos los ámbitos.
- **Biblioteca vacía:** la Biblioteca muestra el estado vacío de hoy, y «Nueva carpeta» sigue disponible.
- **Ámbito con documentos de una carpeta borrada y vuelta a crear con el mismo nombre:** es otra carpeta; la del ámbito ya no existe (US5).
- **Cambio de idioma de la app:** «Toda la biblioteca», «Sin carpeta» y los recuentos cambian de idioma, pero los nombres de carpeta no.
- **Doble toque en «Crear», «Aplicar» o «Mover»:** se hace una sola vez.
- **Fallo al guardar** (crear, renombrar, eliminar, mover o guardar el ámbito): nada cambia, y un aviso lo dice.
- **Biblioteca ilegible** (índice no disponible): la Biblioteca y el selector muestran el error de hoy, y el chat el estado de índice no disponible.

## Requirements *(mandatory)*

### Functional Requirements

**Carpetas**

- **FR-001**: La app MUST permitir carpetas de un solo nivel. Cada documento MUST estar en una carpeta o en ninguna. «Sin carpeta» MUST reunir los documentos sin carpeta.
- **FR-002**: Al actualizar desde una versión sin carpetas, todos los documentos existentes MUST quedar en «Sin carpeta», sin perder ningún dato ni cambiar su estado ni su inclusión.
- **FR-003**: El usuario MUST poder crear una carpeta con un nombre de 1 a 40 caracteres, sin espacios en los extremos, que no coincida con otra ni con «Sin carpeta» sin distinguir mayúsculas ni tildes. Un nombre no válido MUST explicarse sin cerrar el diálogo.
- **FR-004**: El usuario MUST poder renombrar una carpeta, con las mismas reglas. El nombre nuevo MUST verse en todos los sitios donde aparece.
- **FR-005**: El usuario MUST poder eliminar una carpeta con confirmación. Sus documentos MUST pasar a «Sin carpeta», y ningún documento MUST borrarse (K4). La carpeta MUST salir de todos los ámbitos.

**Biblioteca**

- **FR-006**: La Biblioteca MUST mostrar las carpetas con el número de documentos de cada una, en orden alfabético, y después «Sin carpeta» si tiene documentos. Los recuentos MUST salir de consultas agregadas, sin cargar las listas (constitución IV).
- **FR-007**: Al entrar en una carpeta, o en «Sin carpeta», MUST verse solo sus documentos, con la búsqueda por nombre, los filtros Todos/Indexados/Pendientes, la carga por tandas, el interruptor «Incluir en RAG» y los estados de vacío, sin resultados, carga y error de F08.
- **FR-008**: La búsqueda de la Biblioteca fuera de una carpeta MUST encontrar documentos de todas, y cada resultado MUST decir su carpeta.
- **FR-009**: La búsqueda, el filtro y la posición de desplazamiento de cada carpeta MUST conservarse al volver del detalle y al girar el teléfono, como en F08.

**Mover e importar**

- **FR-010**: El detalle de un documento MUST mostrar su carpeta y ofrecer «Mover a carpeta», con la lista de carpetas, «Sin carpeta» y «Nueva carpeta». Mover MUST NOT reindexar ni cambiar la inclusión ni el estado del documento.
- **FR-011**: La Ingesta MUST mostrar la carpeta de destino y permitir cambiarla antes de elegir archivos, con las mismas opciones. Los documentos elegidos MUST registrarse en el destino vigente al elegirlos. Por defecto, el destino MUST ser la carpeta desde la que se abrió la Ingesta, o «Sin carpeta».
- **FR-012**: Un duplicado MUST avisarse como hoy y MUST NOT cambiar de carpeta.

**Ámbito del chat**

- **FR-013**: Cada conversación MUST tener un ámbito: «Toda la biblioteca», o un conjunto de carpetas completas más un conjunto de documentos sueltos, de cualquier carpeta.
- **FR-014**: El chat MUST mostrar el ámbito en una ficha encima del compositor, que abre el selector (K7, K8). La ficha MUST resumirlo («Toda la biblioteca», «Legal», «Legal y Motores», «Legal + 2 documentos», «3 documentos») y acortarse con «…» si no cabe.
- **FR-015**: El selector MUST ofrecer «Toda la biblioteca» y, por carpetas, marcar una carpeta entera o documentos sueltos de ella. Una carpeta con solo algunos documentos marcados MUST verse como tal. Aplicar con nada marcado MUST NOT ser posible. Los documentos excluidos de las búsquedas MUST verse con «No incluido en RAG» y MUST NOT poder marcarse.
- **FR-016**: Una carpeta del ámbito MUST incluir los documentos que estén en ella en el momento de cada pregunta, también los que lleguen después.
- **FR-017**: La recuperación MUST usar solo documentos indexados, incluidos y dentro del ámbito, en la búsqueda por significado, en la búsqueda por palabras y en la carga de candidatos y vecinos. Con «Toda la biblioteca» MUST dar exactamente el mismo resultado que sin carpetas. La recuperación (fusión, candidatos, contexto y prompt) MUST NOT cambiar en lo demás (constitución III).
- **FR-018**: El resultado de una búsqueda acotada MUST ser exacto: ningún documento del ámbito MUST quedarse fuera porque otros, de fuera, ocupen los candidatos.
- **FR-019**: Cambiar el ámbito MUST aplicarse a las preguntas siguientes de esa conversación. Las respuestas anteriores MUST conservar sus fuentes. Una respuesta en curso MUST terminar con el ámbito con el que empezó.
- **FR-020**: El ámbito MUST guardarse con la conversación en cuanto tiene su primera pregunta, y MUST restaurarse al abrirla desde Historial, al volver a la pestaña Chat, al girar y tras cerrar la app. «Nuevo chat» MUST empezar con «Toda la biblioteca» (K11). Las conversaciones anteriores a esta versión MUST tener «Toda la biblioteca».
- **FR-021**: Si una carpeta o un documento del ámbito ya no existe, MUST salir del ámbito y la app MUST avisarlo al abrir la conversación. Si no queda nada, el ámbito MUST volver a «Toda la biblioteca», con un aviso. Un documento creado después de guardarse el ámbito MUST NOT contar como el documento borrado que tenía su mismo identificador interno (D-065).
- **FR-022**: Si el ámbito no tiene documentos indexados e incluidos, el chat MUST decirlo con «Cambiar ámbito» y MUST NOT dejar preguntar. La «cobertura parcial» y los demás estados del chat MUST calcularse dentro del ámbito.

**Textos, accesibilidad, privacidad, rendimiento y fidelidad**

- **FR-023**: Todos los textos nuevos MUST estar en español y en inglés, con las mismas claves. Los nombres de carpeta MUST mostrarse tal como los escribió el usuario.
- **FR-024**: Las pantallas, diálogos, hojas y la ficha MUST:
  - tener objetivos de 48 dp;
  - leer cada carpeta como una frase con su recuento y su acción;
  - anunciar la ficha con el ámbito y su acción;
  - dar a cada casilla del selector su estado, también «algunos documentos»;
  - anunciar los avisos y los errores;
  - funcionar con la letra al 200 %, en horizontal y en los dos temas.
- **FR-025**: Los nombres de carpetas y documentos y el contenido del ámbito MUST NOT aparecer en los registros: solo identificadores y cifras (constitución I). Todo funciona sin red.
- **FR-026**: Con 1.000 documentos en 20 carpetas, la Biblioteca y las carpetas MUST seguir siendo fluidas, y la búsqueda con un ámbito pequeño MUST medirse en el teléfono de referencia y quedar en el informe (US7). Si su p95 supera el de F10 (200 ms), MUST quedar anotado para F14.
- **FR-027**: Los cambios en los datos guardados MUST migrar los ficheros existentes sin perder nada. Si una migración falla, el fichero MUST quedar como estaba y la app MUST avisarlo como hoy. El harness de escritorio MUST seguir construyendo sus índices.
- **FR-028**: La UI nueva MUST seguir el wireframe y las propuestas K aprobadas por el usuario antes de `/speckit-plan`. Las diferencias MUST reportarse al cierre (constitución VII).

### Key Entities

- **Carpeta:** un grupo de documentos con un nombre que escribe el usuario, único sin distinguir mayúsculas ni tildes, y su fecha de creación. No contiene otras carpetas. Su identificador no se reutiliza aunque se borre.
- **Documento** (de F06–F08): gana una carpeta, que puede no tener («Sin carpeta»). Mover no cambia nada más.
- **Ámbito de una conversación:** «Toda la biblioteca», o carpetas completas más documentos sueltos. Se guarda con la conversación y se ajusta cuando lo que contiene deja de existir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tras actualizar, el 100 % de los documentos existentes están en «Sin carpeta» con su estado e inclusión intactos, y el 100 % de las conversaciones existentes tienen «Toda la biblioteca».
- **SC-002**: En las pruebas de búsqueda, 0 resultados de documentos fuera del ámbito, excluidos o sin indexar, y 0 documentos del ámbito que falten cuando deberían estar entre los primeros.
- **SC-003**: Con «Toda la biblioteca», la búsqueda devuelve exactamente los mismos bloques que sin carpetas en el 100 % de las preguntas del conjunto de comprobación.
- **SC-004**: Con 10.000 fragmentos y un ámbito de unos 1.000, el informe recoge el p50 y el p95 de la búsqueda completa en el teléfono de referencia, frente a «Toda la biblioteca». El objetivo es no superar el p95 de F10 (200 ms); si se supera, queda anotado para F14.
- **SC-005**: El 100 % de las conversaciones reabiertas desde Historial, tras girar y tras cerrar la app conservan su ámbito.
- **SC-006**: Eliminar una carpeta no borra ningún documento: el número de documentos y de fragmentos indexados no cambia, en el 100 % de las pruebas.
- **SC-007**: Con 1.000 documentos en 20 carpetas, la Biblioteca muestra sus carpetas en menos de 1 s en el teléfono de referencia.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en todas las pantallas, diálogos y hojas nuevas o cambiadas, en claro y en oscuro, con la letra al 200 % y en horizontal.
- **SC-009**: Los textos en español y en inglés tienen las mismas claves (100 % de paridad).
- **SC-010**: Los registros de una sesión de prueba no contienen nombres de carpetas ni de documentos, y la app no hace ninguna llamada de red.
- **SC-011**: Las capturas del teléfono coinciden con el wireframe aprobado en estructura, textos y componentes, salvo las diferencias documentadas en el informe de cierre.

## Assumptions

- **Wireframe y propuestas de UI (serie K)** (constitución VII: se aprueban antes de `/speckit-plan`, criterio 1 de F17). Los dibujos están en [wireframe.md](wireframe.md). Los textos son propuestas en español; la versión inglesa va en K14.
  - **K1 — Biblioteca por carpetas:**
    - la barra y el titular de hoy, con «＋» para importar;
    - la búsqueda de siempre, que fuera de una carpeta busca en todas;
    - cabecera «CARPETAS» y una fila por carpeta (icono de carpeta, nombre, «12 documentos» y chevron), en orden alfabético;
    - después, «Sin carpeta» si tiene documentos, y una fila «Nueva carpeta»;
    - con texto en la búsqueda, en lugar de las carpetas se ven los documentos encontrados, con su carpeta en la línea de datos.
  - **K2 — Dentro de una carpeta:**
    - barra «‹ Legal», con «＋» (importar a esta carpeta) y «⋯» con «Renombrar carpeta» y «Eliminar carpeta» (no en «Sin carpeta»);
    - la lista de la Biblioteca de hoy, solo con sus documentos: búsqueda, filtros, «Incluir en RAG» y estados.
  - **K3 — Crear y renombrar:** un diálogo con un campo de nombre (pieza nueva del design system) y «Crear» o «Guardar».
    - Avisos bajo el campo: «Escribe un nombre», «Ya hay una carpeta con ese nombre», «Ese nombre está reservado» y «Máximo 40 caracteres».
  - **K4 — Eliminar carpeta:** confirmación «¿Eliminar la carpeta «Legal»?», «Sus 12 documentos pasarán a «Sin carpeta». No se borra ningún documento.», con «Cancelar» (foco por defecto) y «Eliminar».
  - **K5 — Mover a carpeta:**
    - el detalle muestra «Carpeta: Legal» entre sus datos;
    - el «⋯» del detalle gana «Mover a carpeta», que abre una hoja con las carpetas como opciones de radio, «Sin carpeta» y «Nueva carpeta…»;
    - al terminar, el aviso inferior «Movido a «Motores»».
  - **K6 — Destino al importar:**
    - en la Ingesta, sobre el selector de archivos, la fila «Carpeta de destino · Sin carpeta ›», que abre la misma hoja que K5;
    - cada fila de la cola dice su carpeta en la línea de datos.
  - **K7 — Ficha del ámbito:**
    - encima del compositor, una ficha con icono de carpeta y «Toda la biblioteca» o el resumen de FR-014, con «▾»;
    - TalkBack: «Buscar en: Legal», con la acción «Cambiar ámbito».
  - **K8 — Selector del ámbito:** hoja inferior «Buscar en», con:
    - «Toda la biblioteca» arriba, como opción de radio;
    - después, cada carpeta con una casilla de tres estados y un botón para desplegar sus documentos, cada uno con su casilla;
    - los documentos excluidos, en gris, con «No incluido en RAG»; los pendientes, con «Pendiente de indexar»;
    - «Aplicar», deshabilitado si no hay nada marcado, y «Cancelar».

    Las casillas de tres estados y las filas desplegables son piezas nuevas del design system.
  - **K9 — Ámbito sin documentos listos:** el estado vacío del chat «En este ámbito no hay documentos listos para buscar» con el botón «Cambiar ámbito».
  - **K10 — Avisos del ámbito:** al abrir una conversación cuyo ámbito cambió, el aviso inferior «Una carpeta del ámbito ya no existe», «Un documento del ámbito ya no existe» o «El ámbito vuelve a ser toda la biblioteca».
  - **K11 — «Nuevo chat»:** empieza siempre con «Toda la biblioteca».
  - **K12 — Historial:** sin cambios; las filas no muestran el ámbito.
  - **K13 — Accesibilidad:** 48 dp; carpetas como frases; casillas con su estado; en horizontal y con la letra al 200 %, la ficha se acorta con «…» y el selector se desplaza.
  - **K14 — Textos nuevos en inglés:**
    - «Carpetas» → "Folders", «Nueva carpeta» → "New folder", «Sin carpeta» → "No folder";
    - «12 documentos» → "12 documents";
    - «Renombrar carpeta» → "Rename folder", «Eliminar carpeta» → "Delete folder", «Mover a carpeta» → "Move to folder", «Carpeta de destino» → "Destination folder";
    - «Buscar en» → "Search in", «Toda la biblioteca» → "Whole library", «+ 2 documentos» → "+ 2 documents", «Cambiar ámbito» → "Change scope", «Aplicar» → "Apply";
    - «No incluido en RAG» → "Not included in RAG", «Pendiente de indexar» → "Waiting to be indexed";
    - los avisos y los mensajes de validación, con el mismo sentido.
- **Orden de las carpetas:** alfabético, según el idioma de la app; el plan confirma cómo.
- **Exactitud con un ámbito pequeño:** el plan decide cómo se busca sin perder exactitud (FR-018) ni velocidad (FR-026), y lo mide (US7). La fusión, los candidatos por lista, el contexto y el prompt no cambian (constitución III).
- **Teléfono del usuario:** tiene una conversación suya y un documento suyo. Tras la actualización, su documento queda en «Sin carpeta» y su conversación con «Toda la biblioteca». En la fase del teléfono se usan carpetas y documentos de prueba propios, que se borran al terminar. No se abre, captura ni vuelca nada suyo.
- **Fuera de alcance:**
  - carpetas anidadas, varias carpetas por documento y etiquetas (D-066);
  - mover o borrar varios documentos a la vez;
  - colores o iconos por carpeta;
  - mostrar el ámbito en las filas del Historial (K12);
  - recuentos de Inicio por carpeta;
  - el RAG visual (F18–F20) y el rendimiento general (F14).
- **Dependencias:**
  - F08: la Biblioteca, el detalle y sus estados;
  - F06: la Ingesta y la cola;
  - F10: la recuperación y su filtro de visibilidad;
  - F11 y F13: el chat, sus estados, las conversaciones y el Historial.
