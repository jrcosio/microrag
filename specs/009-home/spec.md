# Especificación de feature: Inicio (F09)

**Feature Branch**: `009-home`

**Created**: 2026-10-28

**Status**: Draft

**Input**: Descripción del usuario: «F09 — Inicio. Feature de producto: la pantalla Inicio, que sustituye al marcador actual y retira los accesos provisionales a Ingesta e Indexación de F06 y F07. Acceso inmediato al chat y a la gestión documental, con el estado real de los modelos, de la biblioteca y de la indexación, sin exponer complejidad técnica.» Ver `docs/roadmap/FEATURES.md` (F09).

**Fuentes**:
- Mockup (manda sobre el `.md`): `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/03-home.png` y `docs/disegn_app/screens/03-home.md`.
- `docs/roadmap/INVENTARIO.md`:
  - ficha P03 (textos del PNG frente al MD);
  - G08: flecha Atrás de las pestañas, que vuelve a Inicio;
  - G20: logotipo oficial (el «›» del PNG forma parte del logotipo);
  - G32: marca «MicroRAG» (D-014).
- Constitución v1.1.0, principios:
  - I: sin red; logs sin contenido privado;
  - II: capas y fuera del hilo principal;
  - IV: rendimiento (arranque y consultas);
  - V: veracidad de estado (ningún modelo «listo» sin verificación, ninguna cifra de ejemplo);
  - VII: UI fiel y accesible;
  - VIII: proceso.
- DECISIONS:
  - D-014: marca «MicroRAG»;
  - D-029: español por defecto, inglés secundario;
  - D-039 a D-042: tanda de indexación, sus pausas y sus cifras (F07);
  - D-043 a D-045: Biblioteca, documentos «Fuera del índice» y reindexado con documento sombra (F08).
- Lo construido:
  - F01: navegación con la ruta `home` como raíz de las pestañas, barra inferior y componentes del sistema de diseño (héroe con órbitas, accesos rápidos, tarjeta de estadística, chips de estado, logotipo);
  - F03: estado real de los dos modelos y Preparación inicial (`setup`);
  - F04: índice con documentos, fragmentos y estados;
  - F06: Ingesta (`ingest`);
  - F07: tanda de indexación y Estado de indexación (`index`);
  - F08: Biblioteca (`library`) con el mismo recuento de documentos.

**Verificado al redactar** (2026-10-28):
- **Inicio hoy:** es un marcador de posición con dos accesos provisionales, a la Ingesta (F06 FR-025) y al Estado de indexación (F07 FR-034).
- **Arranque:** la app abre Inicio si el onboarding está completado, sin mirar los modelos. Tras un arranque en frío, el estado de los modelos se lee como «ausente» hasta que algo lo comprueba. La comprobación existente lee el registro de verificación y compara tamaño y fecha de cada fichero, sin recalcular huellas (F03 SC-007).
- **Fichero de modelo borrado o alterado después del onboarding:** F03 lo dejó previsto para Inicio: «Configuración pendiente» con acceso a la Preparación.
- **Recuentos:**
  - el índice sabe contar documentos y fragmentos, pero el recuento general incluye los documentos sombra de un reindexado en curso (D-044) y no se actualiza solo;
  - la Biblioteca cuenta documentos sin sombras y se refresca con cada cambio del índice.
- **Tanda de indexación (F07):** expone si está activa (indexando, esperando o en pausa, con su causa), su progreso en fragmentos y los documentos con error, o si está en reposo.
- **Destinos todavía provisionales:** Chat (F11), Ajustes e Idiomas (F12).

## Clarifications

### Session 2026-10-28

- Q: En «TU BIBLIOTECA», ¿qué cuentan las dos cifras? → A: documentos, todos los importados en cualquier estado (el mismo número que la Biblioteca en «Todos»); fragmentos, los de los documentos indexados, incluidos o excluidos. En ningún caso cuentan los documentos sombra de un reindexado.
- Q: ¿Dónde va la tarjeta de indexación y cuándo se muestra? → A: entre el héroe y los cuatro accesos, para que se vea sin desplazar. Se muestra mientras haya una tanda activa (indexando, esperando o en pausa, con la causa) y desaparece al terminar, aunque queden documentos con error; esos errores se ven en la Ingesta, la Biblioteca y el Estado de indexación.
- Q: Con la IA local no lista, ¿qué hace «Nuevo chat»? → A: sigue activo y abre el chat; en F11, el chat explicará lo que falta con un acceso a la Preparación (MD 03). Importar y los demás accesos también siguen activos.
- Q: Con documentos importados pero ninguno indexado y sin tanda en marcha, ¿avisa Inicio? → A: no; el resumen muestra solo las cifras reales (por ejemplo, «3 documentos · 0 fragmentos»). Los pendientes se ven en la Ingesta y en el filtro Pendientes de la Biblioteca.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **El usuario:** ya completó el onboarding y abre la app para preguntar a sus documentos o para gestionarlos.
- **F03 (Modelos):** decide si la IA local está lista y ofrece la Preparación cuando no lo está.
- **F07 (Indexación)** y **F08 (Biblioteca):** aportan el progreso de la tanda y el recuento de la biblioteca.
- **F11 (Chat)** y **F12 (Ajustes e Idiomas):** sustituirán los destinos provisionales que hoy abren «Nuevo chat», Ajustes e Idiomas.

### User Story 1 - Empezar a preguntar o a gestionar con un toque (Priority: P1)

Al abrir la app, el usuario ve Inicio con el héroe «Una respuesta. Todas tus fuentes.» y su botón «Nuevo chat», y debajo cuatro accesos: Importar, Documentos, Índice local e Idiomas. Cada uno lleva a su pantalla con un solo toque. El icono de la cabecera abre Ajustes.

**Why this priority**: es el propósito de la pantalla (MD 03: «acceso inmediato al chat y a la gestión documental») y sustituye a los accesos provisionales.

**Independent Test**: desde Inicio, tocar «Nuevo chat», cada acceso y el icono de Ajustes. Cada uno abre su destino al primer toque, y Atrás vuelve a Inicio. Atrás desde Inicio cierra la app. Los accesos provisionales ya no existen.

**Acceptance Scenarios**:

1. **Given** Inicio, **When** el usuario pulsa «Nuevo chat», **Then** se abre el chat con una conversación nueva (destino provisional hasta F11), con una sola interacción.
2. **Given** Inicio, **When** el usuario pulsa Importar, Documentos, Índice local o Idiomas, **Then** se abren respectivamente la Ingesta, la Biblioteca, el Estado de indexación y los Idiomas (destino provisional hasta F12).
3. **Given** Inicio, **When** el usuario pulsa el icono de Ajustes de la cabecera, **Then** se abren los Ajustes (destino provisional hasta F12), igual que con la pestaña Ajustes.
4. **Given** cualquiera de esos destinos, **When** el usuario pulsa Atrás, **Then** vuelve a Inicio, sin duplicar Inicio en la pila.
5. **Given** Inicio con el onboarding completado, **When** el usuario pulsa Atrás, **Then** la app se cierra (F03 FR-003).

---

### User Story 2 - Saber si la IA local está lista (Priority: P1)

Bajo la cabecera, un chip dice el estado real de la IA local: «IA local disponible» cuando los dos modelos están verificados y preparados; si no, «Configuración pendiente», con un acceso a la Preparación para resolverlo. Nunca dice «disponible» antes de comprobarlo.

**Why this priority**: es el principal indicador de si el asistente puede funcionar, y la constitución V prohíbe afirmar un modelo listo sin comprobación real.

**Independent Test**: con los dos modelos verificados, Inicio muestra «IA local disponible». Borrar o alterar un fichero de modelo y volver a Inicio: muestra «Configuración pendiente». Su acceso abre la Preparación, y al terminarla y volver, Inicio muestra «IA local disponible» sin reiniciar la app. Tras un arranque en frío, nunca se ve «disponible» antes de que termine la comprobación.

**Acceptance Scenarios**:

1. **Given** los dos modelos verificados y preparados, **When** se abre Inicio, **Then** se muestra el chip «IA local disponible».
2. **Given** un modelo ausente, alterado o sin preparar, **When** se abre Inicio, **Then** se muestra «Configuración pendiente» con una acción que abre la Preparación.
3. **Given** la Preparación abierta desde Inicio, **When** el usuario deja los modelos listos y vuelve, **Then** Inicio muestra «IA local disponible» y Atrás sigue cerrando la app desde Inicio.
4. **Given** un arranque en frío, **When** Inicio aparece, **Then** el estado se comprueba sin recalcular huellas, y mientras dura no se muestra «IA local disponible».
5. **Given** la app en segundo plano mientras un fichero de modelo desaparece, **When** el usuario vuelve a Inicio, **Then** el estado se vuelve a comprobar y muestra «Configuración pendiente».

---

### User Story 3 - Ver de un vistazo cuánto hay en la biblioteca (Priority: P1)

La tarjeta «TU BIBLIOTECA» muestra el número real de documentos y de fragmentos («24 documentos · 12.400 fragmentos») y se actualiza sola cuando se importa, se indexa, se elimina o se quita del índice un documento. Con la biblioteca vacía, Inicio lo dice y ofrece importar el primer documento. Si el resumen no se puede cargar, los accesos siguen funcionando y se ofrece reintentar.

**Why this priority**: es el resumen que el PNG 03 maqueta, y el MD exige contadores reales y un Inicio útil con la biblioteca vacía.

**Independent Test**: con documentos en varios estados, las cifras coinciden con la Biblioteca y con el índice. Importar, indexar, eliminar o quitar del índice cambia las cifras sin recargar a mano. Con 0 documentos se ve «Tu biblioteca está vacía» con «Importar primer documento», y ninguna cifra. Con el índice no disponible se ve el error con «Reintentar» y los accesos siguen activos.

**Acceptance Scenarios**:

1. **Given** documentos en la biblioteca, **When** se abre Inicio, **Then** «TU BIBLIOTECA» muestra el número de documentos (el mismo que la Biblioteca en «Todos») y el de fragmentos de los documentos indexados, con separador de miles y plural correcto.
2. **Given** Inicio abierto, **When** se importa, se termina de indexar, se elimina o se quita del índice un documento, **Then** las cifras se actualizan sin acción del usuario.
3. **Given** un reindexado en curso, **When** se muestra el resumen, **Then** el documento cuenta una sola vez, con los fragmentos de su versión vigente, hasta que la nueva la sustituye.
4. **Given** 0 documentos, **When** se abre Inicio, **Then** se muestra «Tu biblioteca está vacía» con «Importar primer documento», que abre la Ingesta, y ninguna cifra.
5. **Given** que el resumen aún se está cargando, **When** se muestra Inicio, **Then** la tarjeta muestra un estado de carga, nunca cifras inventadas, y el resto de Inicio ya es usable.
6. **Given** el índice no disponible (incompatible o ilegible) o un fallo al cargar el resumen, **When** se muestra Inicio, **Then** la tarjeta muestra el error con «Reintentar», que solo vuelve a cargar el resumen, y «Nuevo chat» y los accesos siguen funcionando.

---

### User Story 4 - Seguir una indexación en curso desde Inicio (Priority: P2)

Mientras hay una tanda de indexación activa, Inicio muestra una tarjeta pequeña con su estado y su progreso reales. Al tocarla, se abre el Estado de indexación. Cuando la tanda termina, la tarjeta desaparece.

**Why this priority**: el MD la pide para no tener que entrar en el Índice para saber si se está indexando, pero Inicio es útil sin ella.

**Independent Test**: iniciar una tanda desde la Ingesta y volver a Inicio. La tarjeta aparece con «N de M fragmentos», las mismas cifras que el Estado de indexación. Pausar la tanda muestra la causa de la pausa. Tocar la tarjeta abre el Estado de indexación. Al terminar la tanda, la tarjeta desaparece y las cifras del resumen se actualizan.

**Acceptance Scenarios**:

1. **Given** una tanda activa, **When** se abre Inicio, **Then** se muestra la tarjeta con el progreso de la tanda en fragmentos, el mismo que el Estado de indexación y la notificación (D-042).
2. **Given** la tanda en pausa (por el usuario, por temperatura, sin espacio, sin modelo o por el sistema), **When** se muestra la tarjeta, **Then** indica que está en pausa y por qué.
3. **Given** la tarjeta, **When** el usuario la toca, **Then** se abre el Estado de indexación.
4. **Given** que la tanda termina o se cancela, **When** Inicio está abierto, **Then** la tarjeta desaparece sola.
5. **Given** que no hay tanda activa, **When** se abre Inicio, **Then** no se muestra la tarjeta.

---

### User Story 5 - Un Inicio fiel al PNG y accesible (Priority: P2)

Inicio reproduce el PNG 03: cabecera con el logotipo y el icono de Ajustes, chip de estado, saludo, héroe con órbitas, rejilla 2×2 de accesos, tarjeta «TU BIBLIOTECA» y barra inferior con Inicio seleccionado. Además cubre los estados que el PNG no maqueta, y se puede usar con TalkBack, con la fuente al 200 %, en oscuro y en horizontal.

**Why this priority**: la fidelidad y la accesibilidad son obligatorias (constitución VII), pero dependen de que el contenido exista.

**Independent Test**: comparar una captura del teléfono con el PNG 03. Recorrer todos los estados con las comprobaciones automáticas de accesibilidad, en claro y en oscuro, con la fuente al 200 % y en horizontal. Con TalkBack, el chip, el resumen, cada acceso y la tarjeta de indexación se leen como frases completas.

**Acceptance Scenarios**:

1. **Given** los modelos listos y documentos indexados, **When** se abre Inicio, **Then** coincide con el PNG 03:
   - el logotipo y el icono de Ajustes;
   - «IA local disponible»;
   - «¿Qué quieres explorar hoy?»;
   - el héroe con «PREGUNTA A TU BIBLIOTECA», «Una respuesta. Todas tus fuentes.» y «Nuevo chat →»;
   - los accesos «Importar / Añadir archivos», «Documentos / Ver biblioteca», «Índice local / Estado del RAG» e «Idiomas / Preferencias»;
   - «TU BIBLIOTECA» con documentos y fragmentos;
   - la barra inferior con Inicio seleccionado.
2. **Given** TalkBack, **When** se recorre Inicio, **Then**:
   - el chip anuncia el estado de la IA;
   - el resumen se lee como una frase («24 documentos y 12.400 fragmentos»);
   - cada acceso se anuncia como un botón con su título y su descripción;
   - las órbitas decorativas no se anuncian.
3. **Given** la fuente al 200 %, el modo oscuro o el teléfono en horizontal, **When** se muestra cualquier estado, **Then** nada se corta ni se solapa, todo se alcanza desplazando la pantalla y el contraste cumple WCAG AA.

---

### Edge Cases

- **Arranque en frío:** hasta que termina la comprobación de los modelos, el chip no afirma nada. La comprobación no recalcula huellas y no retrasa la aparición de Inicio.
- **Modelo verificado pero sin preparar,** o un modelo descargándose de nuevo desde la Preparación: «Configuración pendiente» hasta que los dos estén listos.
- **Preparación abierta desde Inicio:** al terminar o al pulsar Atrás vuelve a Inicio, sin dejar dos Inicio en la pila ni volver a la Bienvenida.
- **Índice no disponible** (incompatible o ilegible, F04): el resumen muestra el error con «Reintentar». La tarjeta de indexación no se muestra. «Nuevo chat» y los accesos siguen activos; cada destino muestra su propio estado.
- **Documentos importados pero ninguno indexado:** el resumen muestra las cifras reales (por ejemplo, «3 documentos · 0 fragmentos»), sin aviso adicional y no el estado vacío.
- **Documentos excluidos o fuera del índice:** cuentan como documentos. Los fragmentos de los excluidos cuentan; los documentos fuera del índice no tienen fragmentos.
- **Reindexado en curso:** el documento sombra no cuenta como documento ni suma fragmentos hasta que sustituye al original (D-044).
- **Cifras grandes:** «1.000.000 fragmentos» cabe con la fuente al 200 % y en pantallas estrechas sin cortarse; si no cabe en línea, se reparte en varias.
- **Singular:** «1 documento», «1 fragmento».
- **Borrar todos los documentos** con Inicio abierto (por ejemplo, desde la Biblioteca): Inicio pasa al estado vacío al volver, sin recargar a mano.
- **Tanda que termina con documentos con error:** la tarjeta desaparece; los errores se ven en la Ingesta, la Biblioteca y el Estado de indexación.
- **Pulsar «Nuevo chat» o un acceso dos veces seguidas:** se abre un solo destino.
- **Rotación con Inicio desplazada:** se conserva la posición de desplazamiento.

## Requirements *(mandatory)*

### Functional Requirements

**Pantalla y navegación**

- **FR-001**: Inicio MUST sustituir al marcador de posición actual y retirar los accesos provisionales a la Ingesta (F06 FR-025) y al Estado de indexación (F07 FR-034).
- **FR-002**: La cabecera MUST mostrar el logotipo oficial a la izquierda (G20) y, a la derecha, el icono de Ajustes, con etiqueta accesible, que abre Ajustes (destino provisional hasta F12).
- **FR-003**: El héroe MUST mostrar «PREGUNTA A TU BIBLIOTECA», «Una respuesta. Todas tus fuentes.», las órbitas decorativas y el botón «Nuevo chat →». El botón MUST abrir el chat con una conversación nueva con una sola interacción (destino provisional hasta F11).
- **FR-004**: La rejilla 2×2 MUST ofrecer cuatro accesos, cada uno con su tarjeta entera tocable:
  - «Importar / Añadir archivos» → Ingesta;
  - «Documentos / Ver biblioteca» → Biblioteca, como la pestaña de la barra inferior;
  - «Índice local / Estado del RAG» → Estado de indexación;
  - «Idiomas / Preferencias» → Idiomas (destino provisional hasta F12).
- **FR-005**: Inicio MUST mostrar la barra inferior con Inicio seleccionado. Atrás desde Inicio MUST cerrar la app (F03 FR-003), y Atrás desde cualquier destino abierto desde Inicio MUST volver a Inicio sin duplicarlo.
- **FR-006**: Pulsar dos veces seguidas «Nuevo chat», un acceso o el icono de Ajustes MUST abrir un solo destino.

**Estado de la IA local**

- **FR-007**: El chip «IA local disponible» MUST mostrarse solo si los dos modelos están verificados y preparados, con la misma regla que completa el onboarding en F03.
- **FR-008**: En cualquier otro caso, Inicio MUST mostrar el chip «Configuración pendiente» y una acción «Completar configuración» que abre la Preparación (`setup`). Al volver con los modelos listos, MUST mostrar «IA local disponible» sin reiniciar la app.
- **FR-009**: El estado MUST comprobarse al abrir Inicio y al volver la app a primer plano, sin recalcular huellas (F03 SC-007). Durante la primera comprobación, Inicio MUST NOT mostrar ningún chip de estado de la IA. En las siguientes MUST mantener el último estado comprobado hasta que termine la nueva comprobación, sin parpadeos (análisis, C1).
- **FR-010**: Un fichero de modelo borrado, alterado o con un tamaño distinto del verificado MUST dar «Configuración pendiente».
- **FR-011**: Con la IA local no lista, «Nuevo chat», los cuatro accesos y la importación MUST seguir disponibles. El chat informará de lo que falta en F11 (MD 03).

**Resumen de la biblioteca**

- **FR-012**: La tarjeta «TU BIBLIOTECA» MUST mostrar dos cifras reales:
  - documentos: todos los de la biblioteca, en cualquier estado, sin los documentos sombra de un reindexado. Es el mismo número que la Biblioteca muestra en «Todos»;
  - fragmentos: los de los documentos indexados, incluidos o excluidos, sin los de los documentos sombra.
- **FR-013**: Las cifras MUST calcularse sin cargar listas de documentos ni de fragmentos, y fuera del hilo principal.
- **FR-014**: Las cifras MUST actualizarse sin acción del usuario cuando cambie el índice: importar, terminar de indexar, eliminar, quitar del índice o publicar un reindexado.
- **FR-015**: Con 0 documentos, Inicio MUST mostrar «Tu biblioteca está vacía» con «Importar primer documento», que abre la Ingesta, en lugar de las cifras.
- **FR-016**: Mientras el resumen se carga, la tarjeta MUST mostrar un estado de carga sin cifras. Si falla o el índice no está disponible, MUST mostrar un error con «Reintentar», que solo vuelve a cargar el resumen. En ambos casos, el resto de Inicio MUST seguir funcionando.
- **FR-017**: Las cifras MUST mostrarse con separador de miles del idioma y con plural correcto.

**Tarjeta de indexación**

- **FR-018**: Con una tanda de indexación activa (indexando, esperando o en pausa), Inicio MUST mostrar, entre el héroe y los accesos, una tarjeta con:
  - el progreso de la tanda en fragmentos («N de M fragmentos»), con las mismas cifras que el Estado de indexación y la notificación (D-042);
  - si está en pausa, la causa (la misma que en el Estado de indexación).
- **FR-019**: Tocar la tarjeta MUST abrir el Estado de indexación.
- **FR-020**: Sin tanda activa, la tarjeta MUST NOT mostrarse, aunque la última tanda dejara documentos con error. MUST aparecer y desaparecer sola cuando la tanda empieza o termina.

**Accesibilidad, idioma, privacidad y rendimiento**

- **FR-021**: Con TalkBack:
  - el chip de estado MUST anunciar el estado de la IA;
  - el resumen MUST leerse como una frase («24 documentos y 12.400 fragmentos»);
  - cada acceso MUST anunciarse como un botón con su título y su descripción;
  - la tarjeta de indexación MUST leerse con su progreso y su acción;
  - las órbitas y los iconos decorativos MUST NOT anunciarse.
- **FR-022**: Inicio MUST cumplir objetivos táctiles de 48 dp, contraste AA en claro y en oscuro, y la fuente al 200 % sin cortes ni solapes, en vertical y en horizontal, con desplazamiento cuando el contenido no quepa.
- **FR-023**: Todos los textos MUST estar en `strings.xml`, en español e inglés (D-029). Los textos maquetados son los del PNG 03 (INVENTARIO P03).
- **FR-024**: Inicio MUST NOT usar la red. Los logs MUST NOT contener nombres de documentos ni rutas, solo cifras.
- **FR-025**: Inicio MUST aparecer sin esperar a la comprobación de los modelos ni al resumen: el saludo, el héroe y los accesos se ven y responden desde el primer momento.

### Key Entities

- **Estado de la IA local:** disponible, configuración pendiente o comprobando. Se deriva del estado real de los dos modelos de F03 (verificado y preparado). Inicio no lo guarda.
- **Resumen de la biblioteca:** número de documentos y número de fragmentos de los documentos indexados, sin sombras. Se recalcula con cada cambio del índice; puede estar cargando, vacío, con cifras o con error.
- **Tanda de indexación:** la de F07. Inicio muestra si está activa, su progreso en fragmentos y la causa de la pausa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con el onboarding completado, la app abre Inicio con el saludo, el héroe y los accesos visibles en menos de 1 s, mediana en frío, en el teléfono de referencia (como F03 SC-007). El estado de la IA y el resumen aparecen en menos de 1 s más.
- **SC-002**: En el 100 % de las pruebas, «Nuevo chat», los cuatro accesos y el icono de Ajustes abren su destino con un solo toque, y Atrás vuelve a Inicio.
- **SC-003**: En el 100 % de las pruebas con un modelo ausente, alterado o sin preparar, Inicio muestra «Configuración pendiente» y nunca «IA local disponible». Con los dos listos, muestra «IA local disponible».
- **SC-004**: En el 100 % de los casos de prueba (documentos en todos los estados, excluidos, fuera del índice y reindexados a medias), las cifras del resumen coinciden con las de la Biblioteca y con el contenido real del índice. Se actualizan en menos de 1 s tras cada cambio.
- **SC-005**: Con 1.000 documentos y 50.000 fragmentos, el cálculo del resumen tarda 50 ms o menos (p95) en el teléfono de referencia, y no lee ninguna lista de documentos ni de fragmentos.
- **SC-006**: Ningún estado de Inicio muestra cifras de ejemplo: con 0 documentos se ve el estado vacío, y mientras se carga, un estado de carga.
- **SC-007**: Con una tanda activa, la tarjeta de indexación muestra las mismas cifras que el Estado de indexación en el 100 % de las comprobaciones, y desaparece al terminar la tanda.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en todos los estados de Inicio, en claro y en oscuro, con la fuente al 200 % y en horizontal.
- **SC-009**: Inicio no hace llamadas de red, y sus logs no contienen nombres de documentos ni rutas.

## Assumptions

Valores por defecto adoptados sin pregunta. Las cuatro decisiones de más impacto se confirmaron en «Clarifications»:

- **Qué cuentan las cifras** (confirmado en clarify):
  - documentos: todos los de la biblioteca, en cualquier estado, como la Biblioteca en «Todos»;
  - fragmentos: los de los documentos indexados (los que el asistente puede usar si están incluidos), incluidos o excluidos.
- **Documentos sin indexar** (confirmado en clarify): el estado vacío solo aparece con 0 documentos. Con documentos pero ninguno indexado se ven las cifras reales, sin aviso ni tarjeta adicional.
- **Tarjeta de indexación** (confirmado en clarify): entre el héroe y los accesos, para que se vea sin desplazar en el teléfono de referencia. Aparece con la tanda activa, también en pausa o esperando; en reposo no se muestra, aunque la última tanda dejara errores.
- **Modelos no listos** (confirmado en clarify): «Nuevo chat» y los accesos siguen activos. El chat (F11) explicará lo que falta, como prevé el MD 03.
- **Acción de «Configuración pendiente»:** un botón de texto «Completar configuración» junto al chip. El chip y el botón son propuestas que siguen el tono del MD; se aprueban con esta spec.
- **Comprobación de los modelos:** al abrir Inicio y al volver a primer plano, leyendo el registro de verificación sin recalcular huellas, como el arranque de F03.
- **Saludo:** fijo, «¿Qué quieres explorar hoy?», sin variar con la hora.
- **Marca:** el logotipo oficial dentro de la app (G20); «MicroRAG» como nombre del launcher (D-014).
- **Cabecera y pestaña Ajustes:** las dos abren los Ajustes.
- **Fuera de alcance:**
  - el chat y las conversaciones (F11), y el historial (F13);
  - Ajustes e Idiomas reales (F12);
  - la recuperación híbrida y su diagnóstico (F10);
  - recursos del dispositivo o estado térmico fuera de la causa de la pausa en la tarjeta de indexación (G21);
  - exportar y restaurar, OCR (backlog).
