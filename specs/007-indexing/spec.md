# Especificación de feature: Indexación (F07)

**Feature Branch**: `007-indexing`

**Created**: 2026-10-26

**Status**: Draft

**Input**: Descripción del usuario: «F07 — Indexación. Feature de producto: el trabajo en segundo plano que convierte los documentos importados en F06 en fragmentos buscables (extraer, trocear, embeber con EmbeddingGemma 2 y persistir en el índice de F04), y la pantalla Estado de indexación, que sustituye al placeholder actual.» Ver `docs/roadmap/FEATURES.md` (F07).

**Fuentes**:
- Mockup: `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/08-indexacion.png` (manda sobre el `.md`) y `docs/disegn_app/screens/08-indexing.md`.
- `docs/roadmap/INVENTARIO.md`:
  - ficha P08;
  - G21: sección de recursos y «Solo mientras carga»;
  - G29: etapas del PNG frente a lotes intercalados.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Ingesta en el dispositivo (`IngestWorker`, lotes de 64, reanudación);
  - Memoria y ciclo de vida (solo el engine de embedding durante la ingesta);
  - Capa de datos en Kotlin (`insertChunks`);
  - Embeddings con EmbeddingGemma 2 (prefijos, cabecera contextual, truncado, backend);
  - Evaluación (ritmo de embedding en el dispositivo).
- Constitución v1.1.0, principios:
  - I: sin red; datos en `noBackupFilesDir`; logs sin contenido privado;
  - III: RAG según el informe (prefijos, 256D con L2, cabecera contextual);
  - IV: rendimiento (fuera del hilo principal, nunca dos engines en GPU, liberar el LLM durante la ingesta);
  - V: veracidad de estado (progreso real; el índice nunca se publica a medias);
  - VI: calidad;
  - VII: UI fiel y accesible;
  - VIII: proceso.
- DECISIONS:
  - D-022: teléfono de referencia;
  - D-024: embeddings de la ingesta en GPU, con mezcla CPU/GPU permitida;
  - D-025: EmbeddingGemma 2 de 270M;
  - D-028: trabajo largo en segundo plano (F03);
  - D-029: español por defecto, inglés secundario;
  - D-030: INT8 aplazado a F14;
  - D-034: parámetros del troceado y de la cabecera contextual;
  - D-036: entradas de embedding demasiado largas;
  - D-037 y D-038: importación y su diseño (F06).
- Lo construido:
  - F02: coordinador de engines, embedder de texto y su matemática (truncado y normalización);
  - F03: modelos descargados y verificados; canal de notificaciones (G30);
  - F04: índice con inserción de fragmentos idempotente por documento y ordinal, y estados de documento;
  - F05: parámetros decididos (`RagParameters`);
  - F06: importación, cola de Ingesta, extracción, troceado y trabajo en segundo plano que hoy deja cada documento «Preparado para indexar» (`specs/006-import-parsing/report.md`).

**Verificado al redactar** (2026-10-26):
- **Ritmo de embedding** en el teléfono de referencia (F02, D-024), con un fragmento de ≈ 350 tokens: unos 320–355 ms en GPU y unos 880 ms en CPU (p50). El coseno entre vectores de CPU y de GPU es como mínimo 0,993.
- **Tamaño real:** el PDF de 200 páginas de F06 da 430 fragmentos. Con esos ritmos, son unos 2,5 min de embeddings en GPU y unos 6,3 min en CPU. Un PDF de 2.000 páginas (el límite de F06) da 4.300 fragmentos, unos 25 min en GPU.
- **Trabajo largo en segundo plano** (F03, research R3, fuentes oficiales de Android):
  - Android 15 limita un servicio en primer plano de tipo `dataSync` a 6 h de cada 24;
  - en Android 16 los workers largos pueden agotar la cuota de jobs;
  - los *user-initiated data transfer jobs* exigen una restricción de red, así que no sirven para un trabajo que no usa la red.
- **Observación del cierre de F06** (`report.md` §7): tras matar el proceso a mitad de un documento, el sistema relanzó el trabajo, pero este siguió sin su job del sistema y Android congeló el proceso hasta que se abrió la app.
- **Búsquedas de F04:** hoy no filtran por estado del documento; devuelven fragmentos de cualquier documento guardado.
- **D-036** está decidida pero no implementada en la app: hoy un fragmento demasiado largo para el modelo daría error.

## Clarifications

### Session 2026-10-26

- Q: ¿Con qué procesador se calculan los embeddings al indexar? → A: con la GPU, como propone D-024. Si la GPU no carga el engine, se usa la CPU y se registra. Se pueden mezclar vectores de CPU y de GPU en el índice. El usuario no elige el backend.
- Q: ¿Cómo funciona «Pausar indexación» y hay un «Cancelar»? → A: un solo botón pausa y reanuda toda la tanda, como en el PNG 08. No hay «Cancelar» global; para cancelar un documento concreto se usa «Quitar de la cola» de la Ingesta.
- Q: ¿Cuándo se pausa sola la indexación y cuándo sigue? → A: se pausa si el sistema informa una temperatura grave o superior, y sigue sola al bajar a moderada o menos. La batería no pausa la indexación. «Solo mientras carga» queda en el backlog (G21).
- Q: ¿Se limita o se avisa según el volumen de una tanda? → A: no hay límite ni aviso previo. Tras el primer lote, la pantalla muestra el tiempo restante estimado con el ritmo medido en el teléfono, marcado como aproximado.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **El usuario:** ha importado documentos en F06 y quiere poder consultarlos.
- **F08:** muestra los documentos indexados en la Biblioteca.
- **F10:** busca solo en los documentos indexados.

### User Story 1 - Indexar los documentos importados de principio a fin (Priority: P1)

Con documentos en la cola de Ingesta, el usuario pulsa «Iniciar indexación». Sin hacer nada más, cada documento se extrae, se trocea, se embebe y se guarda en el índice, y acaba **indexado** con todos sus fragmentos. Un documento solo se puede encontrar en una búsqueda cuando está indexado entero.

**Why this priority**: es lo que convierte la app en un RAG. Sin fragmentos con vector no hay búsqueda ni chat con fuentes.

**Independent Test**: indexar un PDF de varias páginas, un TXT y un MD. Los tres acaban indexados. Cada uno tiene tantos fragmentos guardados como su troceado, con ordinales consecutivos desde 0, cada uno con su vector, su página y su ruta de encabezados. Una búsqueda hecha mientras se indexaba no devolvió ningún fragmento de un documento a medias.

**Acceptance Scenarios**:

1. **Given** documentos «Listo para procesar» o «Preparado para indexar» en la cola, **When** el usuario pulsa «Iniciar indexación», **Then** cada uno se procesa sin intervención y acaba indexado o con un error concreto.
2. **Given** un documento indexado, **When** se comparan sus fragmentos guardados con su troceado, **Then** coinciden en número, orden, texto, página y ruta de encabezados, y cada fragmento tiene un vector de 256 componentes con norma 1.
3. **Given** un fragmento, **When** se calcula su vector, **Then** el texto embebido lleva el prefijo de documento y la cabecera contextual «título › ruta de encabezados», y el texto guardado no la lleva (constitución III).
4. **Given** un fragmento demasiado largo para el modelo, **When** se embebe, **Then** se recorta solo el texto embebido al 90 % por un espacio y se reintenta (D-036). El texto guardado no cambia.
5. **Given** un documento a medio indexar, **When** se busca en el índice, **Then** no aparece ninguno de sus fragmentos. Aparecen cuando el documento termina.
6. **Given** un fragmento que el modelo no puede embeber ni tras los recortes, **When** falla, **Then** el documento acaba en error con su causa, sin fragmentos visibles en las búsquedas, y el resto de documentos sigue.

---

### User Story 2 - La indexación sobrevive a todo (Priority: P1)

La indexación de muchos fragmentos puede durar minutos u horas. El usuario puede salir de la app, bloquear el teléfono o cambiar de app, y el trabajo sigue con una notificación de progreso. Si el sistema mata el proceso o el teléfono se reinicia, el trabajo continúa donde se quedó, sin repetir ni duplicar lo ya guardado.

**Why this priority**: es el criterio de salida de la fase 3 del informe. Una indexación que se pierde o duplica fragmentos deja el índice inservible.

**Independent Test**: indexar el PDF de 200 páginas. Matar el proceso en varios momentos (durante la extracción, a mitad de los embeddings y al cerrar el documento) sin abrir la app después. El trabajo continúa solo y el documento acaba indexado con los mismos 430 fragmentos que sin interrupción, sin ordinales repetidos ni huecos.

**Acceptance Scenarios**:

1. **Given** una indexación en curso, **When** el usuario sale de la app o bloquea el teléfono, **Then** el trabajo sigue y una notificación muestra el progreso real. Tocarla abre el Estado de indexación.
2. **Given** una indexación en curso, **When** el sistema mata el proceso, **Then** el trabajo se reanuda solo, sin abrir la app, y continúa por el primer fragmento no guardado.
3. **Given** una reanudación, **When** termina el documento, **Then** tiene exactamente los fragmentos de su troceado, una sola vez cada uno.
4. **Given** el teléfono se reinicia con la indexación a medias, **When** se desbloquea, **Then** el trabajo continúa sin abrir la app.
5. **Given** el usuario fuerza la detención de la app desde Ajustes del sistema, **When** vuelve a abrirla, **Then** el trabajo pendiente se vuelve a programar solo.
6. **Given** el sistema agota el tiempo que permite a un trabajo largo, **When** ocurre, **Then** la indexación se pausa con esa causa visible y continúa cuando el sistema lo permita o al abrir la app, sin perder lo hecho.

---

### User Story 3 - Ver el progreso real (Priority: P1)

El usuario abre Estado de indexación y ve, como en el PNG 08: el porcentaje completado en un anillo, «N / M fragmentos», las etapas del documento actual, el nombre del documento que se procesa y el botón para pausar. Todas las cifras son reales.

**Why this priority**: con trabajos de minutos u horas, el usuario necesita saber que avanza y cuánto falta. Una cifra inventada rompe la confianza (constitución V).

**Independent Test**: con 3 documentos en marcha, el anillo y «N / M fragmentos» crecen a medida que se guardan fragmentos. N coincide con los fragmentos guardados (con una diferencia de un lote como mucho), y M con la suma de los troceados. Al cerrar y reabrir la app, la pantalla muestra el mismo progreso.

**Acceptance Scenarios**:

1. **Given** una indexación en curso con el total conocido, **When** se abre la pantalla, **Then** muestra el porcentaje y «N / M fragmentos» de toda la tanda, la etapa actual y el documento que se procesa.
2. **Given** que el total aún no se conoce, **When** se abre la pantalla, **Then** muestra «Procesando...» con una barra indeterminada y ningún porcentaje.
3. **Given** el primer lote guardado, **When** el ritmo medido en este teléfono es estable, **Then** la pantalla muestra el tiempo restante estimado, marcado como aproximado. Si no hay medida fiable, no se muestra.
4. **Given** que no hay nada que indexar, **When** se abre la pantalla, **Then** muestra «Todo actualizado».
5. **Given** la pantalla abierta, **When** la app se cierra y se vuelve a abrir, **Then** el progreso mostrado es el real del momento, no se reinicia a 0.

---

### User Story 4 - Pausar, reanudar, reintentar y pausas automáticas (Priority: P2)

El usuario puede pausar la indexación y reanudarla después; lo ya guardado no se repite. Si un documento falla, puede reintentarlo. La app también se pausa sola cuando el teléfono se calienta demasiado o se queda sin espacio, y lo explica.

**Why this priority**: da control sobre un trabajo largo que consume batería y calor, y evita que un fallo puntual obligue a empezar de cero.

**Independent Test**: pausar a mitad de un documento; no se calcula ningún vector más. Reanudar; continúa por el primer fragmento no guardado. Simular temperatura grave: se pausa y la pantalla explica por qué; al bajar, continúa. Simular falta de espacio: se detiene sin dejar nada a medias y avisa.

**Acceptance Scenarios**:

1. **Given** una indexación en curso, **When** el usuario pulsa «Pausar indexación», **Then** el trabajo se detiene tras terminar el fragmento en curso, sin perder fragmentos guardados, y la pantalla muestra «En pausa» con «Reanudar».
2. **Given** la indexación en pausa, **When** el usuario pulsa «Reanudar», **Then** continúa por el primer fragmento no guardado, sin recalcular los guardados. La pausa sobrevive al cierre de la app.
3. **Given** un documento con error que se puede reintentar, **When** el usuario pulsa «Reintentar», **Then** ese documento vuelve a la tanda y se procesa.
4. **Given** el sistema informa una temperatura grave o superior durante la indexación, **When** ocurre, **Then** el trabajo se pausa con la causa visible, y continúa solo cuando la temperatura baja a moderada o menos.
5. **Given** el almacenamiento libre no basta para seguir escribiendo, **When** ocurre, **Then** el trabajo se detiene de forma segura, el documento en curso no se publica a medias y la pantalla avisa y orienta al usuario.
6. **Given** el modelo de embeddings no está instalado o no pasa la verificación, **When** se inicia la indexación, **Then** los documentos quedan esperando, la pantalla lo explica y ofrece ir a la preparación de modelos.

---

### User Story 5 - Pantalla fiel al mockup, accesible y coherente con la Ingesta (Priority: P2)

La pantalla Estado de indexación reproduce el PNG 08 y cubre los estados del `.md` que el PNG no maqueta. La cola de Ingesta de F06 muestra los nuevos estados, y los documentos indexados salen de ella.

**Why this priority**: la fidelidad y la accesibilidad son obligatorias (constitución VII), pero dependen de que el trabajo funcione.

**Independent Test**: comparar capturas del teléfono con el PNG 08. Recorrer todos los estados con las comprobaciones de accesibilidad automáticas, en claro y en oscuro, con la fuente al 200 %. En la cola de Ingesta, un documento en embeddings muestra su progreso y desaparece al quedar indexado.

**Acceptance Scenarios**:

1. **Given** una indexación en curso, **When** se abre la pantalla, **Then** coincide con el PNG 08: hero oscuro «PROCESAMIENTO LOCAL» con anillo y «N / M fragmentos», cabecera «ETAPAS DEL PROCESO» con 4 etapas, tarjeta «Procesando ahora» y botón «Pausar indexación».
2. **Given** cada estado (sin tareas, en curso, total desconocido, en pausa por el usuario, pausa térmica, sin espacio, error, modelo ausente, índice no disponible), **When** se muestra, **Then** tiene un texto claro y la acción que corresponde.
3. **Given** la cola de Ingesta, **When** un documento está calculando embeddings, **Then** su fila muestra «Indexando» con su progreso, y al quedar indexado sale de la cola.
4. **Given** TalkBack activo, **When** se recorre la pantalla, **Then** el anillo anuncia el porcentaje y los fragmentos, cada etapa anuncia su nombre y su estado, y los cambios de estado importantes se anuncian sin mover el foco.
5. **Given** la fuente al 200 % y el modo oscuro, **When** se muestra cualquier estado, **Then** nada se corta ni se solapa y el contraste cumple WCAG AA.

---

### Edge Cases

- **Quitar de la cola un documento a medio indexar** (acción de F06): su procesamiento se detiene, y se borran sus fragmentos ya guardados, su registro y su copia.
- **El documento desaparece o cambia durante el trabajo** (copia privada borrada): acaba en error y el resto sigue.
- **El troceado de la reanudación no coincide con lo guardado** (por ejemplo, otra versión de la app con otros parámetros): el documento no se mezcla; se vuelve a indexar desde cero.
- **La GPU no carga el engine o no da vectores fiables en este teléfono:** se usa la CPU y se registra en el log y en el informe. En un emulador, donde la GPU de LiteRT se bloquea al cargar (F04), se usa directamente la CPU.
- **El engine de embedding falla a mitad:** se recarga una vez; si la GPU vuelve a fallar, se pasa a la CPU. Si aun así falla, ese documento acaba en error «No se pudo indexar» y la tanda sigue con los demás.
- **El chat (F11, futuro) necesita el LLM mientras se indexa:** nunca hay dos engines en GPU a la vez; el coordinador decide (constitución IV). En F07 no hay chat; se deja el contrato listo.
- **Un documento sin fragmentos tras trocear** (texto vacío): queda en error «Sin texto», como en F06.
- **El índice no es compatible o no se abre:** no se procesa nada y la pantalla lo explica, como la Ingesta de F06 (P5).
- **Una actualización de la app durante la indexación:** el trabajo continúa con la nueva versión desde el primer fragmento no guardado.
- **El usuario niega las notificaciones:** el trabajo sigue igual; solo falta la notificación.
- **Documentos importados durante una indexación en curso:** se pueden añadir a la tanda con «Iniciar indexación» sin reiniciar lo que va en marcha. Se trocean enseguida, para que el total siga siendo conocido.
- **Muchos documentos pequeños:** el progreso global sigue siendo real y la pantalla no parpadea al cambiar de documento.
- **Ahorro de batería o modo reposo:** el trabajo respeta lo que el sistema permita; si el sistema lo detiene, se reanuda como en US2.

## Requirements *(mandatory)*

### Functional Requirements

**Procesamiento por documento**

- **FR-001**: Al iniciar la indexación, la app MUST procesar todos los documentos no indexados que el usuario haya mandado indexar (los listos y los preparados de F06, y los que estaban a medias), hasta dejar cada uno indexado o con un error concreto.
- **FR-002**: Para cada documento, la app MUST volver a extraer y trocear su copia privada con el mismo algoritmo y los mismos parámetros que F06, porque F06 no guarda los fragmentos y el troceado es determinista.
- **FR-003**: Cada fragmento MUST guardarse con su texto original, su ordinal, su página, su ruta de encabezados, su estimación de tokens (con la densidad de F05) y su vector.
- **FR-004**: El texto embebido MUST ser el prefijo de documento seguido de la cabecera contextual «título › ruta de encabezados» y del texto del fragmento. El título es el nombre del documento (ver Assumptions). El texto guardado no lleva la cabecera.
- **FR-005**: El vector MUST tener 256 componentes y norma L2 1, tras truncar la salida del modelo (constitución III).
- **FR-006**: Si el modelo rechaza un texto por largo, la app MUST recortar solo el texto embebido al 90 % de su longitud, por un espacio, y reintentar hasta que lo acepte (D-036). Si no lo acepta, el documento acaba en error.
- **FR-007**: Los fragmentos MUST guardarse en lotes de 64 por transacción. Un lote se guarda entero o no se guarda.
- **FR-008**: Al reanudar, la app MUST saltarse los fragmentos ya guardados de ese documento, sin recalcular su vector, y guardar cada fragmento una sola vez.
- **FR-009**: Si el troceado de la reanudación no coincide con los fragmentos ya guardados (número o texto), la app MUST descartar lo guardado de ese documento y volver a indexarlo desde cero.

**Publicación**

- **FR-010**: Un documento MUST pasar a indexado solo cuando todos sus fragmentos están guardados.
- **FR-011**: Las búsquedas del índice MUST devolver solo fragmentos de documentos indexados e incluidos en la búsqueda. Un documento a medias, con error o en pausa nunca aparece.

**Motores y recursos**

- **FR-012**: Durante la indexación MUST estar cargado solo el engine de embedding; nunca el LLM a la vez en la GPU (constitución IV). El engine MUST liberarse al terminar o al pausar.
- **FR-013**: Los embeddings de la ingesta MUST calcularse en la GPU (D-024). Si la GPU no carga el engine, la app MUST usar la CPU y registrarlo. El usuario no elige el backend.
- **FR-014**: Los embeddings MUST calcularse en serie sobre un engine ya cargado, nunca varios a la vez (informe, Embeddings).
- **FR-015**: Antes de calcular embeddings, la app MUST comprobar que el modelo de embeddings está instalado y verificado (F03). Si no, los documentos quedan esperando (troceados, si ya lo estaban) y la pantalla lo explica.

**Ejecución en segundo plano**

- **FR-016**: La indexación MUST continuar con la app cerrada o el teléfono bloqueado, con una notificación de progreso real que abre el Estado de indexación.
- **FR-017**: Si el proceso muere, la indexación MUST reanudarse sola, sin abrir la app, en cuanto el sistema lo permita. El trabajo no puede quedarse parado indefinidamente en un proceso congelado (observación de F06).
- **FR-018**: Al abrir la app, si hay documentos a medias sin ningún trabajo programado (por ejemplo, tras forzar la detención), la app MUST volver a programarlos.
- **FR-019**: Si el sistema detiene el trabajo por sus límites (tiempo en primer plano, cuotas, restricciones), la app MUST mostrarlo como pausa automática «Esperando al sistema» mientras no avanza, y continuar cuando el sistema lo permita o al abrir la app, sin perder lo hecho.
- **FR-020**: Nada del procesamiento MUST ejecutarse en el hilo principal (constitución IV).

**Progreso**

- **FR-021**: El progreso global MUST ser fragmentos guardados sobre fragmentos totales de la tanda, solo cuando el total se conoce. Si no, MUST mostrarse como indeterminado.
- **FR-022**: El total MUST conocerse pronto: en segundos tras empezar, no al terminar cada documento.
- **FR-023**: Tras el primer lote, la pantalla MUST mostrar el tiempo restante estimado con el ritmo medido en este teléfono, marcado como aproximado, y solo mientras ese ritmo sea estable. Nunca se muestran cifras de ejemplo ni interpoladas (constitución V). No hay límite de volumen ni aviso previo.
- **FR-024**: El progreso MUST restaurarse al reabrir la app y tras una reanudación.

**Control**

- **FR-025**: El usuario MUST poder pausar y reanudar toda la tanda con un solo botón. La pausa MUST hacerse efectiva tras el fragmento en curso y sobrevivir al cierre de la app. No hay «Cancelar» global; un documento concreto se cancela con «Quitar de la cola» (FR-029).
- **FR-026**: El usuario MUST poder reintentar un documento con error que se pueda reintentar, desde el Estado de indexación y desde la cola de Ingesta.
- **FR-027**: Si el sistema informa una temperatura grave o superior, la app MUST pausar la indexación con la causa visible, y reanudarla sola cuando baje a moderada o menos. La batería no pausa la indexación.
- **FR-028**: Si falta espacio para seguir escribiendo, la app MUST detenerse sin publicar el documento en curso, avisar y orientar al usuario. Reanudar MUST ser posible cuando haya espacio.
- **FR-029**: Quitar de la cola un documento a medio indexar MUST detener su procesamiento y borrar sus fragmentos, su registro y su copia.

**Pantalla Estado de indexación (`index`)**

- **FR-030**: La pantalla MUST reproducir el PNG 08 con los componentes del design system: top bar «Estado de indexación» con Atrás, hero oscuro con eyebrow «PROCESAMIENTO LOCAL», anillo de porcentaje con «COMPLETADO» y «N / M fragmentos», cabecera «ETAPAS DEL PROCESO», 4 filas de etapa, tarjeta «Procesando ahora» con el nombre del documento y botón «Pausar indexación».
- **FR-031**: Las etapas MUST ser Extracción de texto, Fragmentación, Generación de embeddings y Persistencia vectorial, cada una con Completado, En curso o Pendiente para el documento actual. Como los embeddings y la escritura van intercalados por lotes, «Persistencia vectorial» corresponde al cierre del documento (G29).
- **FR-032**: La pantalla MUST cubrir los estados del `.md`: sin tareas («Todo actualizado»), en curso, total desconocido («Procesando...»), en pausa por el usuario, pausa automática por temperatura o por límite del sistema, sin espacio, error con el documento y la causa, modelo ausente e índice no disponible. Las propuestas visuales de los estados no maquetados se aprueban en el plan, como en F06.
- **FR-033**: La acción principal MUST cambiar según el estado: «Pausar indexación», «Reanudar» o «Reintentar». Sin tareas, no hay acción.
- **FR-034**: La pantalla MUST abrirse desde la CTA «Iniciar indexación» de la Ingesta, desde la notificación y desde un acceso provisional en Inicio hasta F09.
- **FR-035**: La sección de recursos y «Solo mientras carga» (G21) quedan fuera de esta feature, en el backlog.

**Cola de Ingesta (F06)**

- **FR-036**: La cola MUST mostrar los documentos en embeddings con «Indexando» y su progreso, y en pausa con «En pausa». Los indexados MUST salir de la cola.
- **FR-037**: Los nuevos errores de la indexación MUST mostrarse en la fila y en su hoja de detalle, con Reintentar cuando proceda.

**Accesibilidad, idioma y privacidad**

- **FR-038**: La pantalla y la notificación MUST cumplir constitución VII: áreas táctiles de 48 dp, TalkBack (anillo, etapas y anuncios educados de cambios), fuente al 200 %, claro y oscuro, contraste WCAG AA y textos en `strings.xml` en español e inglés (D-029).
- **FR-039**: Ningún log MUST contener nombres de archivo, texto de los documentos ni rutas (constitución I). La indexación no usa la red.

**Medición**

- **FR-040**: La app MUST medir y registrar en el teléfono de referencia el ritmo de embedding (fragmentos por segundo) en CPU y en GPU durante una indexación real, y el tiempo total del PDF de 200 páginas.

### Key Entities *(include if feature involves data)*

- **Documento** (de F04/F06): gana el paso por «calculando embeddings» y el estado final «indexado». Conserva su número de fragmentos y sus páginas sin texto.
- **Fragmento**: texto original, ordinal, página, ruta de encabezados, estimación de tokens y vector de 256 componentes. Pertenece a un documento; su par documento-ordinal es único.
- **Tanda de indexación**: el conjunto de documentos que el usuario mandó indexar y que aún no han terminado. Tiene un total de fragmentos (cuando se conoce), los guardados, el documento y la etapa actuales, y un estado: en curso, en pausa (con su causa) o terminada.
- **Causa de pausa**: el usuario, la temperatura, el límite de tiempo del sistema, la falta de espacio o el modelo ausente.
- **Medida de ritmo**: fragmentos por segundo con el backend usado, para el tiempo restante y para el informe.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el teléfono de referencia, el PDF de 200 páginas (430 fragmentos) pasa de «Iniciar indexación» a indexado sin intervención en **5 minutos o menos** con la GPU. Se registra también el tiempo con la CPU.
- **SC-002**: Tras matar el proceso en al menos 3 momentos distintos (durante el troceado de un PDF de 2.000 páginas, a mitad de los embeddings y entre dos documentos) y no abrir la app, cada documento acaba indexado con exactamente los mismos fragmentos que sin interrupción: 0 duplicados, 0 huecos y el mismo texto en cada ordinal. Una prueba automática interrumpe además en el cierre del documento, que dura milisegundos.
- **SC-003**: Durante una indexación, el 100 % de las búsquedas devuelve solo fragmentos de documentos ya indexados.
- **SC-004**: La cifra «N / M fragmentos» nunca se aleja más de un lote (64) de los fragmentos realmente guardados, y M coincide con la suma de los troceados. Sin total conocido no se muestra ningún porcentaje.
- **SC-005**: Pausar detiene el cálculo de vectores en 5 segundos o menos, y al reanudar se recalcula como mucho 1 fragmento. Tras una muerte del proceso, se recalculan como mucho los del lote que no llegó a guardarse (≤ 63).
- **SC-006**: Con temperatura grave simulada, la indexación se pausa en 5 segundos o menos con la causa visible, y continúa sola al volver a una temperatura normal.
- **SC-007**: En el 100 % de los momentos de una indexación, el LLM no está cargado.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en todos los estados de la pantalla, en claro y en oscuro, con la fuente al 200 %.
- **SC-009**: Se miden y se reportan los fragmentos por segundo en CPU y en GPU en el teléfono de referencia, y la indexación no provoca violaciones de disco en el hilo principal.
- **SC-010**: La indexación no hace ninguna llamada de red y sus logs no contienen nombres de archivo ni texto de los documentos.

## Assumptions

- **Título de la cabecera contextual:** el nombre del documento sin la extensión («Manual técnico» para «Manual técnico.pdf»). Es lo más parecido a los títulos del corpus de F05, que no llevan extensión.
- **Qué se indexa:** al pulsar «Iniciar indexación» se indexan todos los documentos no indexados de la cola, como en F06. No hay selección parcial.
- **Cancelar:** no hay un «Cancelar» global (clarify). Pausar conserva lo hecho, y «Quitar de la cola» (F06) cancela un documento concreto.
- **Modelo:** EmbeddingGemma 2 de 270M (D-025), instalado y verificado en el onboarding de F03.
- **INT8:** sigue aplazado a F14 (D-030). El cierre del documento no reconstruye ningún índice cuantizado.
- **Inclusión en la búsqueda:** todo documento indexado entra incluido; excluirlo es cosa de F08.
- **Batería:** la indexación no exige estar cargando ni se pausa por batería baja (clarify). «Solo mientras carga» queda en el backlog (G21).
- **Mezcla de vectores:** se pueden mezclar vectores de CPU y de GPU en el mismo índice (D-024), por ejemplo cuando la GPU no carga y se usa la CPU.
- **Volumen:** no hay límite de fragmentos por tanda ni aviso previo (clarify); los límites de F06 por archivo (100 MB y 2.000 páginas) siguen vigentes.
