# Especificación de feature: Indexación visual, como modo «Multimodal» opcional (F19)

**Feature Branch**: `019-visual-indexing`

**Created**: 2026-11-05

**Status**: Draft

**Input**: Descripción del usuario: «F19 — Indexación visual, como modo «Multimodal» opcional (docs/roadmap/FEATURES.md §F19, D-066, D-078). Feature de producto sin mockup (propuesta de UI G47 aprobada antes del plan). Un interruptor «Modo multimodal» en Ajustes, desactivado por defecto: desactivado, la app se comporta exactamente como hoy (solo texto, la 270M). Al activarlo: aviso de la descarga de 369 MB y de respuestas más lentas (10–15 s en el A063); descarga y verificación de EmbeddingGemma 2 de 440M con la descarga de F03; validación del índice actual por la identidad de los vectores de texto con la 270M (1,00000 en F18), sin reindexar; la 440M pasa a embeber también el texto y la 270M se borra solo después de validar; el texto sigue buscable durante el cambio. Con el modo activo, cada PDF se importa como Texto, Visual o Ambos (valor por defecto en Ajustes); JPG y PNG siempre en Visual; los PDF escaneados se admiten en Visual. Ingesta visual: cada página se renderiza con PdfRenderer a 768 px en JPEG calidad 85 en noBackupFilesDir y se embebe con la 440M a 140 tokens por imagen en CPU (unos 3,2 s por página), vectores de página de 256D; checkpoint por página, progreso por páginas, pausas térmica y por espacio, publicación atómica como en F07. Datos: rag.db v5 con la tabla de páginas y su vector, documents.index_mode y el espacio de páginas en rag_meta. Biblioteca y detalle muestran el modo, permiten reindexar en otro modo, y eliminar o quitar del índice borra también las imágenes de página. Reutiliza los adaptadores del diagnóstico de F18 (embedder de imagen de LiteRtVisionEngines y DebugPageRasterizer), llevados a main. Enmienda de la constitución antes del plan: III (página citable, 440M, 256D para páginas), IV (primer token propio de las respuestas con páginas) y Restricciones. Sin ablaciones comparativas ni regla de sí o no (D-078). El chat con páginas es F20.» Ver `docs/roadmap/FEATURES.md` (F19).

**Fuentes**:
- **Sin mockup para lo nuevo.** Los PNG no tienen modo multimodal ni modo de indexación. La UI se propone con el design system y el usuario la aprueba antes del plan (constitución VII, G47). Como base:
  - `09-configuracion.png` y `screens/09-settings.md`, para el interruptor y el modo por defecto;
  - `05-ingesta.png`, `06-biblioteca.png`, `07-detalle-documento.png` y `08-indexacion.png`.
- DECISIONS:
  - D-078: el modo opcional, la 440M al activarlo, el detalle elegible y la ausencia de pruebas comparativas;
  - D-077 y D-076: las cifras y los parámetros de F18;
  - D-025: la 270M de solo texto, que con el modo activo sustituye la 440M;
  - D-024: la consulta en CPU;
  - D-034 y D-040: el índice de texto y los filtros de búsqueda;
  - D-070 y D-071: las carpetas y el ámbito.
- F18:
  - `specs/018-visual-rag-feasibility/benchmarks.md`: la 440M, con golden 0,99299 en GPU e identidad 1,00000; 1,3 s por página a 70 tokens y 3,2 s a 140, solo en CPU; la conversión en 28–67 ms por página en JPEG;
  - `report.md`.
- Constitución v1.1.1. Antes del plan se enmienda (D-078):
  - III: la página como unidad citable, la 440M como embedder del modo multimodal y los vectores de página de 256D;
  - IV: el primer token propio de las respuestas con páginas;
  - Restricciones: la 440M opcional.
- FEATURES: F19 (objetivo, criterios 1–9 y riesgos) y F20, que usa lo que deja F19.

**Verificado al redactar** (2026-11-05):
- **Ajustes:**
  - tiene tres secciones: «Inteligencia artificial», con Gemma y la 270M fijas; «Preferencias», con Idiomas, Tema e «Índice y rendimiento»; y «Privacidad y datos»;
  - la ficha de un modelo es de solo lectura;
  - las preferencias se guardan en almacenes privados sin copia de seguridad. Un fallo al guardar se avisa una vez y se mantiene el valor anterior (F12, F16).
- **Modelos:**
  - la app solo descarga los modelos obligatorios, la 270M y Gemma, desde la preparación del primer arranque;
  - la descarga se reanuda, comprueba el espacio con un margen de 500 MB, reintenta y verifica el SHA-256;
  - **ningún modelo se puede borrar hoy**, y una descarga a medias de un modelo no obligatorio se borra como huérfana;
  - la 440M ya está en el catálogo, con su huella fijada.
- **Índice:**
  - guarda la huella del modelo de embeddings, la dimensión y los prefijos. Si no coinciden con los de la app, no se abre;
  - es la versión 4;
  - las migraciones solo pueden añadir columnas a `documents`, índices o tablas nuevas.
- **La 270M está fijada** en la indexación, en la consulta, en los datos del índice y en las listas de Ajustes y de la preparación.
- **Importación:**
  - solo acepta PDF, TXT y Markdown, hasta 100 MB y 2.000 páginas;
  - un PDF sin texto termina en error «Sin texto» («Sin texto extraíble (¿documento escaneado?)»);
  - los duplicados se detectan por su huella.
- **Indexación:**
  - se reanuda por fragmento;
  - el progreso va en fragmentos, con el tiempo restante;
  - pausa por temperatura y por espacio (64 MiB), y por un modelo que falta;
  - un documento solo pasa a indexado cuando están todos sus fragmentos;
  - reindexar usa una copia en sombra que sustituye a la anterior de una vez;
  - el LLM no está cargado mientras se indexa.
- **Biblioteca y detalle:** «Reindexar documento», «Quitar del índice» y «Eliminar», que borra la copia privada, los fragmentos y los vectores.
- **Componentes disponibles:**
  - fila con interruptor, filas de radio y de ajustes, diálogo de confirmación con contenido;
  - ficha de estado, progreso, hoja inferior y avisos.
- **Diagnóstico de F18 (solo en debug):** embedder de imagen sobre la 440M y conversión de páginas e imágenes (en blanco, con la orientación EXIF y en JPEG).

## Clarifications

### Session 2026-11-05

- Q: ¿Qué pasa con las páginas indexadas y con la 440M al desactivar el modo? → A: Se conserva todo. Las páginas y sus vectores se quedan sin usarse, y la 440M sigue embebiendo el texto. Volver a activarlo es inmediato, sin descargar ni reindexar (FR-007).
- Q: Al activar el modo, ¿qué se hace con los PDF que ya estaban indexados en Texto? → A: Nada automático. Cada documento se pasa a Visual o Ambos desde su detalle, cuando el usuario quiera (FR-008b).
- Q: ¿Con qué modo sale por defecto la importación de un PDF con el modo activo? → A: Ambos, texto más páginas (FR-022).
- Q: ¿Dónde va el interruptor en Ajustes? → A: En «Inteligencia artificial», junto a los modelos. Debajo van el modo por defecto y, en F20, el detalle de las páginas (FR-001).
- Q: ¿Apruebas el wireframe? → A: Sí, tal como está en [wireframe.md](wireframe.md) (FR-027, G47).
- Q: ¿Apruebas la enmienda de la constitución 1.1.1 → 1.2.0? → A: Sí, tal como está en [constitution-amendment.md](constitution-amendment.md). Se aplica antes del plan.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Activar el modo multimodal (Priority: P1)

Una persona quiere que la app pueda leer escaneados, fotos de documentos e imágenes. En Ajustes activa «Modo multimodal». La app le explica antes qué supone:
- descargar 369 MB;
- indexar más despacio, unos 3 s por página;
- respuestas más lentas cuando entran páginas en el chat (F20).

Ella confirma, y la app descarga, verifica y deja el modo listo. Su biblioteca de texto sigue funcionando en todo momento.

**Why this priority**: sin activar el modo no hay nada visual. Es también lo que más riesgo tiene: cambia el modelo de embeddings de un índice con datos del usuario.

**Independent Test**: en una instalación con documentos indexados, activar el modo y comprobar que se descarga y verifica el modelo, que el índice cambia de modelo sin reindexar y que las búsquedas dan lo mismo antes, durante y después.

**Acceptance Scenarios**:

1. **Given** el modo desactivado, **When** la persona abre Ajustes, **Then** ve el interruptor «Modo multimodal» apagado, y la app funciona y se descarga exactamente como hoy.
2. **Given** que lo activa, **When** aparece el aviso, **Then** se explican la descarga, el espacio, la indexación más lenta y las respuestas más lentas. Si cancela, el modo sigue apagado y no se descarga nada.
3. **Given** que confirma, **When** la descarga avanza, **Then**:
   - se ve el progreso real;
   - se puede pausar y reanudar;
   - un corte de red se reanuda donde iba;
   - sin espacio, se explica cuánto falta.
4. **Given** el modelo descargado y verificado, **When** la app lo valida con los vectores de texto actuales, **Then** el modo queda activo, la 270M se borra y las búsquedas siguen dando lo mismo.
5. **Given** que la validación no coincide, **When** termina, **Then**:
   - el modo no se activa;
   - la 270M se queda;
   - la 440M descargada se borra;
   - la app explica que este modelo no es compatible en el dispositivo.

---

### User Story 2 - Importar un PDF como imágenes de página (Priority: P1)

Con el modo activo, la persona importa un PDF con tablas y gráficos y elige «Ambos». La app guarda su texto como hoy y, además, cada página como imagen con su vector. El progreso dice cuántas páginas lleva, y el documento queda indexado en los dos modos.

**Why this priority**: es lo que el modo añade. Sin ello, F20 no tiene páginas que usar.

**Independent Test**: importar un PDF de varias páginas en Visual y en Ambos, y comprobar sus páginas, sus imágenes privadas, sus vectores y el progreso por páginas.

**Acceptance Scenarios**:

1. **Given** el modo activo, **When** la persona abre la Ingesta, **Then** elige el modo de indexación (Texto, Visual o Ambos) antes de elegir los archivos. Por defecto sale el que marca Ajustes.
2. **Given** un PDF en Visual, **When** se indexa, **Then** cada página tiene su imagen privada y su vector. El progreso va en páginas («12 / 40 páginas»), con el tiempo restante.
3. **Given** un PDF en Ambos, **When** se indexa, **Then** tiene sus fragmentos de texto como hoy y, además, sus páginas.
4. **Given** el modo desactivado, **When** la persona importa, **Then** todo va en Texto, como hoy, y no se ve ningún selector nuevo.

---

### User Story 3 - Escaneados e imágenes sueltas (Priority: P1)

La persona importa un PDF escaneado, que hoy termina en «Sin texto», y la foto de un cartel en JPG. Con el modo activo, los dos se indexan como páginas.

**Why this priority**: es el contenido que el texto no alcanza, y la razón del modo (D-078).

**Independent Test**: importar un escaneado y un JPG o un PNG con el modo activo, y comprobar que quedan indexados como páginas. Con el modo apagado, el escaneado sigue en «Sin texto» y la imagen se rechaza con su explicación.

**Acceptance Scenarios**:

1. **Given** el modo activo, **When** se importa un PDF escaneado en Visual o en Ambos, **Then** se indexan sus páginas. En Ambos, que no tenga texto no es un error: queda solo con páginas.
2. **Given** el modo activo, **When** se importa un JPG o un PNG, **Then** se indexa siempre en Visual, como una página, con la orientación de la foto aplicada.
3. **Given** el modo desactivado, **When** se importa un JPG o un PNG, **Then** se rechaza explicando que las imágenes necesitan el modo multimodal.
4. **Given** un escaneado en Texto, **When** se indexa, **Then** sigue terminando en «Sin texto», y la explicación ofrece reindexarlo en Visual.

---

### User Story 4 - Ver y cambiar el modo de un documento (Priority: P2)

En la Biblioteca y en el detalle se ve en qué modo está cada documento. Desde el detalle, la persona lo reindexa en otro modo, y mientras tanto la versión anterior sigue respondiendo.

**Why this priority**: sin ello no se puede pasar a Visual lo que ya estaba importado, ni corregir una elección.

**Independent Test**: reindexar un documento de Texto a Ambos y de Ambos a Texto, comprobando que la versión anterior sigue disponible hasta el cambio y que no quedan páginas huérfanas.

**Acceptance Scenarios**:

1. **Given** un documento indexado, **When** la persona lo ve en la Biblioteca o en el detalle, **Then**:
   - se ve su modo;
   - si es Visual o Ambos, también su número de páginas;
   - TalkBack anuncia las dos cosas.
2. **Given** el modo activo, **When** elige «Reindexar» en el detalle, **Then** puede escoger el modo, y la versión anterior sigue en las búsquedas hasta que la nueva está completa.
3. **Given** un documento con páginas, **When** se elimina o se quita del índice, **Then** se borran también sus imágenes de página y sus vectores.

---

### User Story 5 - Indexar mucho sin vigilarlo (Priority: P2)

La persona importa un PDF de 200 páginas en Visual y deja el teléfono. La indexación sigue en segundo plano, se pausa si el teléfono se calienta o se queda sin espacio, y si la app se cierra continúa donde iba sin repetir páginas.

**Why this priority**: 200 páginas son unos 11 minutos con este teléfono. Sin reanudación ni pausas, el modo no es usable con documentos reales.

**Independent Test**: matar la app a mitad de un PDF largo en Visual y comprobar que, al relanzarla, sigue sin duplicar páginas. Provocar la falta de espacio y comprobar la pausa y su aviso.

**Acceptance Scenarios**:

1. **Given** un PDF largo en Visual, **When** la app se cierra a mitad, **Then** al relanzarla continúa por la siguiente página, sin duplicar ninguna.
2. **Given** el espacio por debajo del margen, **When** la indexación va a guardar más páginas, **Then** se pausa, se avisa con el espacio que falta, y continúa al liberar espacio.
3. **Given** el teléfono muy caliente, **When** ocurre, **Then** la indexación se pausa y sigue cuando se enfría, como en F07.

---

### User Story 6 - Desactivar el modo (Priority: P3)

La persona prueba el modo y decide apagarlo. La app vuelve a comportarse como antes: importa solo en Texto, y los documentos con páginas lo indican sin perder sus datos de texto.

**Why this priority**: es lo que hace del modo una opción de verdad.

**Independent Test**: desactivar el modo con documentos en Visual y en Ambos, y comprobar qué queda en la Biblioteca, en la importación y en la búsqueda de texto.

**Acceptance Scenarios**:

1. **Given** el modo activo con documentos visuales, **When** la persona lo desactiva, **Then** un aviso dice que las páginas y la 440M se conservan sin usarse, y la búsqueda de texto sigue igual.
2. **Given** el modo desactivado, **When** importa, **Then** vuelve a ser solo Texto. Las imágenes se rechazan con su explicación.
3. **Given** el modo desactivado tras haberlo usado, **When** la persona lo vuelve a activar, **Then** queda activo al momento, sin descargar ni reindexar, con las mismas páginas que tenía.

---

### User Story 7 - Pantallas aprobadas, accesibles y traducidas (Priority: P2)

Las pantallas nuevas y los cambios en Ajustes, Ingesta, Biblioteca y detalle siguen el design system, se aprueban antes del plan, funcionan con TalkBack y con la letra al 200 %, y están en español y en inglés.

**Why this priority**: es una regla del proyecto para todo lo que no tiene mockup (constitución VII).

**Independent Test**: revisar el wireframe aprobado frente a las pantallas, y recorrerlas con TalkBack, al 200 % y en los dos idiomas.

**Acceptance Scenarios**:

1. **Given** el wireframe aprobado, **When** se comparan las pantallas, **Then** coinciden, y las diferencias quedan anotadas.
2. **Given** TalkBack y la letra al 200 %, **When** se recorren Ajustes, la Ingesta, la Biblioteca y el detalle, **Then** todo se anuncia y nada se corta.

---

### Edge Cases

- **Corte durante el cambio de modelo:** si se corta la luz o se mata la app, el índice sigue abriéndose y buscando, con la 270M o con la 440M, nunca con un estado intermedio. El cambio se reanuda o se deshace.
- **Sin espacio para la descarga:** se dice cuánto falta y el modo no se activa.
- **Descarga pausada, cancelada o fallida:** el modo queda «sin terminar de activar», con «Reanudar» y «Cancelar», y la app sigue con la 270M.
- **Documentos que se indexaban al activar el modo:** el cambio de modelo espera a que acabe la indexación en curso, o la pausa y la reanuda con la 440M; nunca hay vectores de los dos modelos en un mismo documento sin validar. Se elige en el plan.
- **Una indexación en Texto que empezó antes de activar el modo** termina en Texto.
- **PDF protegido con contraseña:** se rechaza como hoy, en cualquier modo.
- **PDF con permisos de propietario restrictivos:** se convierte igual. F18 lo vio funcionar con el PDF de AEMET.
- **PNG con transparencia, JPEG en CMYK o con orientación EXIF:** se convierten sobre blanco, con la orientación correcta (F18).
- **Imagen enorme** (una foto de 12 MP): se reduce a 768 px por el lado largo antes de guardarla.
- **Un JPG o PNG que no se puede decodificar:** se rechaza con su explicación.
- **Mismo archivo importado en otro modo:** sigue siendo un duplicado. Para cambiar el modo se reindexa.
- **PDF de 2.000 páginas en Visual:** se permite. Antes de empezar se avisa del tiempo y del espacio estimados.
- **Documento quitado del índice o eliminado mientras se indexan sus páginas:** su indexación se detiene y no quedan páginas huérfanas.
- **Documentos solo en Visual con el modo desactivado:** conservan sus páginas, y su estado dice que necesitan el modo multimodal. No se reindexan solos.
- **Desactivar y reactivar con descargas o indexaciones en curso:** una indexación visual en curso se detiene al desactivar y se reanuda al reactivar.
- **Hasta F20, el chat no usa las páginas.** Un documento solo en Visual se ve como «Sin texto para el chat» en la disponibilidad del chat, sin contar como buscable por texto.

## Requirements *(mandatory)*

### Functional Requirements

**Modo multimodal y modelo**

- **FR-001**: Ajustes MUST tener un interruptor «Modo multimodal» en la sección «Inteligencia artificial», junto a los modelos, desactivado por defecto. Desactivado, la app MUST comportarse exactamente como sin esta feature: la misma descarga, la misma importación, el mismo índice y las mismas búsquedas.
- **FR-002**: Activar el modo MUST pedir confirmación con un aviso de:
  - la descarga, de 369 MB;
  - el espacio que ocupa cada página;
  - el tiempo de indexación, unos 3 s por página en el teléfono de referencia;
  - que las respuestas con páginas tardarán más (F20).

  Cancelar MUST dejar el modo apagado sin descargar nada.
- **FR-003**: La 440M MUST descargarse con el mismo sistema que los modelos del primer arranque:
  - en segundo plano, con progreso real, pausa y reanudación;
  - comprobando el espacio y verificando la huella.

  Un fallo MUST explicarse, con «Reintentar» y «Cancelar». Mientras tanto, la app MUST seguir funcionando con la 270M.
- **FR-004**: Antes de cambiar de modelo, la app MUST comprobar en el dispositivo que la 440M da los mismos vectores de texto que la 270M sobre una muestra fija (coseno mínimo ≥ 0,9999, la enmienda de la constitución III). Si no coinciden:
  - el modo no se activa;
  - la 440M descargada se borra;
  - la 270M se queda;
  - se explica.
- **FR-005**: Tras la validación, la 440M MUST pasar a embeber el texto de la indexación y de las consultas, y el índice MUST registrar el modelo nuevo **sin reindexar**. La búsqueda de texto MUST dar los mismos resultados antes y después. La 270M MUST borrarse solo después de que el cambio esté guardado.
- **FR-006**: Un cambio de modelo interrumpido MUST reanudarse o deshacerse al relanzar la app. El índice MUST abrirse y buscar en todo momento.
- **FR-007**: Desactivar el modo MUST pedir confirmación, y la importación MUST volver a ser solo Texto. Las páginas indexadas y sus vectores MUST conservarse sin usarse, y la 440M MUST seguir embebiendo el texto: la 270M no vuelve. Reactivar el modo MUST ser inmediato, sin descargar ni reindexar.
- **FR-008**: El primer arranque de una instalación nueva MUST seguir igual, con la 270M y Gemma. El modo solo se activa desde Ajustes.
- **FR-008b**: Activar el modo MUST NOT reindexar nada por sí solo. Los PDF ya indexados en Texto MUST seguir igual hasta que el usuario los reindexe en otro modo desde su detalle.

**Importación**

- **FR-009**: Con el modo activo, la Ingesta MUST mostrar el modo de indexación (Texto, Visual o Ambos) y permitir cambiarlo antes de elegir archivos. Por defecto MUST salir el de Ajustes. Los PDF elegidos MUST registrarse con el modo vigente al elegirlos.
- **FR-010**: Con el modo activo, la importación MUST aceptar JPG y PNG, siempre en Visual, con los mismos límites de tamaño y la misma detección de duplicados. Con el modo apagado, MUST rechazarlos explicando que necesitan el modo multimodal.
- **FR-011**: Un PDF sin texto en Visual o en Ambos MUST indexarse por páginas. En Ambos, no tener texto MUST NOT ser un error. En Texto, MUST seguir terminando en «Sin texto», y la explicación MUST ofrecer reindexarlo en Visual si el modo está activo.

**Indexación visual**

- **FR-012**: Cada página MUST convertirse en una imagen de 768 px por el lado largo, sobre blanco, en JPEG de calidad 85, y guardarse en el almacenamiento privado de la app, fuera de las copias de seguridad. Las imágenes sueltas MUST pasar por lo mismo, con su orientación aplicada.
- **FR-013**: Cada página MUST embeberse con la 440M a 140 tokens por imagen, y su vector MUST guardarse a 256 dimensiones, normalizado, en el índice, junto con el documento y el número de página.
- **FR-014**: La indexación visual MUST reanudarse por página: tras un cierre, MUST seguir por la siguiente sin duplicar ninguna.
- **FR-015**: El progreso MUST contarse en páginas para la parte visual y en fragmentos para la de texto, con el tiempo restante.
- **FR-016**: La indexación visual MUST pausarse:
  - por temperatura y por falta de espacio, contando también las imágenes de página;
  - por un modelo que falta, como en F07.

  El LLM MUST NOT estar cargado mientras se indexa, y MUST NOT haber dos engines en GPU.
- **FR-017**: Un documento MUST pasar a indexado solo cuando sus dos partes, según su modo, estén completas. Reindexar en otro modo MUST mantener la versión anterior en las búsquedas hasta que la nueva esté completa, y entonces sustituirla de una vez.
- **FR-018**: Antes de indexar un PDF en Visual o en Ambos, la app MUST comprobar que hay espacio para sus imágenes de página, con su margen. Si no lo hay, MUST avisarse antes de empezar.

**Biblioteca, detalle y Ajustes**

- **FR-019**: La Biblioteca y el detalle MUST mostrar el modo de cada documento (Texto, Visual o Ambos) y, para Visual y Ambos, su número de páginas.
- **FR-020**: Con el modo activo, «Reindexar» en el detalle MUST permitir elegir el modo.
- **FR-021**: Eliminar un documento, quitarlo del índice o reindexarlo en un modo sin páginas MUST borrar sus imágenes de página y sus vectores. Las imágenes huérfanas MUST limpiarse como las copias privadas de F08.
- **FR-022**: Con el modo activo, Ajustes MUST tener, debajo del interruptor, «Modo por defecto al importar» (Texto, Visual o Ambos), con **Ambos** por defecto. MUST guardarse como las demás preferencias. Si guardar falla, se avisa y se mantiene el valor anterior.
- **FR-023**: Con el modo activo, la sección de modelos de Ajustes MUST mostrar la 440M en lugar de la 270M, con su estado y su descarga.
- **FR-024**: Hasta F20, el chat MUST NOT usar las páginas. Un documento solo en Visual MUST aparecer en la disponibilidad del chat como sin texto, sin contar como buscable.

**Calidad**

- **FR-025**: Con el modo desactivado, el harness del escritorio MUST dar las mismas cifras de F05 y F10, y la batería de pruebas MUST pasar como al cerrar F18.
- **FR-026**: Las imágenes de página, los nombres y el contenido de los documentos MUST NOT aparecer en los registros (constitución I).
- **FR-027**: Las pantallas nuevas y los cambios MUST seguir el wireframe aprobado (G47), con TalkBack, la letra al 200 % y los textos en español y en inglés.

### Key Entities

- **Modo multimodal:** apagado, activándose (descargando, validando o cambiando de modelo), activo, o con un error y su motivo.
- **Modo de indexación de un documento:** Texto, Visual o Ambos. Los JPG y PNG son siempre Visual.
- **Página indexada:**
  - el documento y su número de página, empezando en 1;
  - su imagen privada, con su tamaño en píxeles y en bytes;
  - su vector de 256 dimensiones;
  - si ya está completa, para reanudar.
- **Espacio de páginas del índice:** el modelo con su huella, la dimensión, los tokens por imagen, la resolución y el formato. Si no coinciden con los de la app, las páginas no se usan.
- **Cambio de modelo:** la validación de identidad (muestra, coseno mínimo y resultado), el paso de la 270M a la 440M en el índice y el borrado de la 270M.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con el modo desactivado, nada cambia:
  - el harness da las mismas cifras de F05 y F10;
  - no se descarga la 440M;
  - la batería de pruebas pasa como al cerrar F18.
- **SC-002**: En el teléfono de referencia, activar el modo descarga, valida y cambia de modelo sin que la búsqueda de texto falle o cambie de resultado en ningún momento. Lo comprueban pruebas instrumentadas, con un corte a mitad de cada paso, y la prueba en el teléfono.
- **SC-003**: Un PDF de 200 páginas en Visual se indexa sin intervención. Si se mata la app a mitad, al relanzarla continúa sin duplicar páginas. Se miden en el teléfono, como cifras informativas, el ritmo en páginas por minuto y el espacio por página.
- **SC-004**: Un PDF escaneado, un JPG y un PNG se indexan en Visual con el modo activo. Con el modo apagado, el escaneado da «Sin texto» y las imágenes se rechazan con su explicación.
- **SC-005**: Eliminar, quitar del índice y reindexar en otro modo dejan coherentes la copia privada, los fragmentos, las páginas, sus imágenes y los vectores, sin huérfanos (prueba instrumentada).
- **SC-006**: Se comprueba el espacio antes y durante la indexación visual. Sin espacio, la pausa se avisa con la cantidad que falta.
- **SC-007**: El LLM no está cargado durante la indexación, y nunca hay dos engines en GPU (prueba automática).
- **SC-008**:
  - el wireframe se aprueba antes del plan;
  - TalkBack anuncia el modo y las páginas de cada documento;
  - con la letra al 200 % nada se corta;
  - todos los textos nuevos están en español y en inglés.

## Assumptions

- **Parámetros de F18** (D-076, D-078):
  - página a 768 px en JPEG de calidad 85;
  - la 440M a 140 tokens por imagen, con vectores de 256D;
  - el embedder de imagen solo corre en CPU, porque el fichero no declara la visión en GPU.

  No se repiten pruebas comparativas.
- **Identidad de la 440M:** F18 midió en el A063 una identidad de 1,00000 con la 270M sobre 50 textos. La validación del dispositivo usa una muestra fija, empaquetada con la app.
- **La descarga** reutiliza el sistema del primer arranque. La 440M tiene que dejar de tratarse como un modelo solo de diagnóstico, para que su descarga a medias no se borre como huérfana.
- **El ritmo esperado** es de unos 3,2 s por página en CPU, unos 11 minutos para 200 páginas. Cada imagen ocupa unos 50–150 KB, según el contenido.
- **La conversión** usa el conversor de PDF del sistema (disponible en API 31), y las imágenes se decodifican con el decodificador del sistema, como en el diagnóstico de F18.
- **F20** pone las páginas en el chat, las citas a página y el detalle de las páginas. F19 solo indexa y muestra el modo.
- **La constitución** se enmienda antes del plan, con la aprobación del usuario en el clarify (D-078).
- **Sin red,** salvo la descarga de la 440M desde el mismo origen fijado que el resto de modelos (constitución I).
