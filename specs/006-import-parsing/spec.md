# Especificación de feature: Importación y parsing (F06)

**Feature Branch**: `006-import-parsing`

**Created**: 2026-10-26

**Status**: Draft

**Input**: Descripción del usuario: «F06 — Importación y parsing. Feature de producto: la pantalla Importar documentos y el trabajo en segundo plano que extrae y trocea los documentos importados, sin embeddings ni escritura de fragmentos (eso es F07).» Ver `docs/roadmap/FEATURES.md` (F06).

**Fuentes**:
- Mockup: `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/05-ingesta.png` (manda sobre el `.md`) y `docs/disegn_app/screens/05-ingestion.md`.
- `docs/roadmap/INVENTARIO.md`:
  - ficha P05;
  - G14: sin «Arrastra»;
  - G15: el chevron de la fila;
  - G22: copia privada y duplicados;
  - G27: columnas de `documents` (D-030);
  - G38: mantenimiento del extractor de PDF.
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Parsing y chunking (Extracción, Estrategia, Chunker);
  - Esquema SQLite (`documents`);
  - Ingesta en el dispositivo;
  - Decisión previa: de dónde sale el corpus.
- Constitución v1.1.0, principios:
  - I: sin red; datos en `noBackupFilesDir`;
  - II: la UI no toca código nativo;
  - IV: trabajo fuera del hilo principal;
  - V: veracidad de estado;
  - VI: calidad;
  - VII: UI fiel y accesible;
  - VIII: proceso.
- DECISIONS:
  - D-002: ingesta en el dispositivo;
  - D-017: minSdk 31;
  - D-022: teléfono de referencia con Android 15;
  - D-029: español por defecto, inglés secundario;
  - D-030: esquema ampliado;
  - D-034: parámetros de troceado;
  - D-036: entradas de embedding largas, que aplica F07.
- Lo construido:
  - F01: `LisdataFilePickerCard`, `LisdataQueueRow` y la ruta `ingest`;
  - F04: índice `RagIndex` con `documents`, `DocumentStatus` y `AppDirs.documents`;
  - F05: `offline/golden/chunker_golden.jsonl`, `offline/eval/chunker.py`, `offline/eval/extract.py` y `RagParameters`.

**Verificado al redactar** (2026-10-26). Opciones para extraer texto de PDF (G38):
- **API de plataforma** `android.graphics.pdf`, basada en PDFium y actualizada por el sistema:
  - `PdfRenderer.Page.getTextContents()` desde API 35;
  - `PdfRendererPreV.Page.getTextContents()` en Android 12–14 con la extensión S ≥ 13. Fuente: `api-versions.xml` del SDK 37.1 (`sdks="31:13,33:13,34:13,35:13,0:35"`);
  - `LoadParams` admite contraseña;
  - no añade dependencias.
- **`com.tom-roush:pdfbox-android`:**
  - 2.0.27.0, Apache-2.0, última publicación el 2023-01-02;
  - AAR de 3,25 MB, más BouncyCastle 1.72 (`bcprov`, `bcpkix`, `bcutil`).
- **`androidx.pdf`:**
  - 1.0.0-beta01, del 2026-08-26; minSdk 28, sin bibliotecas nativas propias;
  - la extracción va en un servicio en otro proceso;
  - arrastra `pdf-viewer`.

## Clarifications

### Session 2026-10-26

- Q: ¿Con qué se extrae el texto de los PDF en el teléfono (G38)? → A: con la API de PDF del sistema: `PdfRenderer` en Android 15+ y `PdfRendererPreV` en Android 12–14 con la extensión S ≥ 13. No se añade pdfbox-android ni ninguna biblioteca de PDF. En un dispositivo sin esa capacidad, los PDF se rechazan con un mensaje claro y TXT y Markdown siguen funcionando.
- Q: ¿Qué límites se aplican a cada archivo importado? → A: 100 MB por archivo y 2.000 páginas por PDF. Por encima, error «Demasiado grande».
- Q: ¿Qué documentos muestra la Cola de importación? → A: todos los documentos aún no indexados (listos, en proceso, preparados y con error), también los de sesiones anteriores, hasta que se indexan y pasan a la Biblioteca o el usuario los quita.
- Q: ¿Qué se hace con un PDF protegido con contraseña de apertura? → A: se rechaza como «Archivo cifrado». No se pide ni se guarda ninguna contraseña.

## User Scenarios & Testing *(mandatory)*

**Actores:**
- **El usuario:** elige documentos de su teléfono para consultarlos después.
- **F07:** hereda los documentos registrados y el trabajo en segundo plano, y le añade embeddings e inserción.
- **F08:** muestra los documentos ya indexados.

### User Story 1 - Elegir archivos y verlos en la cola (Priority: P1)

El usuario abre Importar documentos, pulsa «Seleccionar archivos» y elige uno o varios PDF, TXT o Markdown con el selector del sistema. Cada archivo aparece en la Cola de importación con su nombre, su tamaño y su estado. La app guarda su propia copia, así que el documento sigue disponible aunque el original se mueva o se borre.

**Why this priority**: es la puerta de entrada de todo el RAG. Sin documentos registrados no hay nada que indexar ni consultar.

**Independent Test**: elegir 3 archivos válidos (un PDF con texto, un TXT y un MD). Los 3 aparecen en la cola como «Listo para procesar» con su tamaño real, y cada uno queda registrado como documento pendiente con su copia privada y su huella.

**Acceptance Scenarios**:

1. **Given** la pantalla Importar documentos, **When** el usuario pulsa «Seleccionar archivos», **Then** se abre el selector del sistema con selección múltiple, filtrado a PDF, TXT y Markdown, y la app no pide ningún permiso de almacenamiento.
2. **Given** el usuario elige 3 archivos válidos, **When** vuelve a la app, **Then**:
   - cada archivo aparece en la cola con un estado de importación;
   - al terminar la copia y la validación pasa a «Listo para procesar», con el tamaño en formato local (p. ej. «2,4 MB»);
   - el contador muestra «3 archivos».
3. **Given** un archivo importado, **When** se borra el original del almacenamiento del teléfono, **Then** el documento sigue en la cola y se puede procesar, porque la app trabaja con su copia privada.
4. **Given** el usuario cancela el selector sin elegir nada, **When** vuelve a la app, **Then** la cola no cambia y no aparece ningún error.

---

### User Story 2 - Errores por archivo que no bloquean al resto (Priority: P1)

Si un archivo no se puede importar, solo ese archivo muestra el error, con un texto claro. El resto sigue su curso. Desde la fila del error el usuario puede reintentar o quitarlo de la cola.

**Why this priority**: los PDF reales fallan a menudo (cifrados, escaneados, dañados). Un fallo no puede arruinar una importación de varios archivos ni dejar estados engañosos.

**Independent Test**: elegir a la vez un PDF con texto, un PDF cifrado, un PDF escaneado, un archivo dañado, un archivo de otro formato y un duplicado de un documento ya importado. Cada uno acaba en su estado correcto y el PDF con texto queda listo.

**Acceptance Scenarios**:

1. **Given** una selección con un archivo cifrado, uno dañado, uno sin texto, uno de formato no admitido y uno demasiado grande, **When** termina la importación, **Then**:
   - cada uno muestra su error específico;
   - los archivos válidos de la misma selección quedan «Listo para procesar».
2. **Given** un archivo con el mismo contenido que un documento ya importado (en la cola o en la biblioteca), **When** se importa, **Then** se identifica como duplicado («Ya está en tu biblioteca») y no se crea un segundo documento ni una segunda copia.
3. **Given** el almacenamiento libre no basta para la copia, **When** se importa el archivo, **Then** ese archivo muestra «Sin espacio» y no queda ninguna copia a medias.
4. **Given** una fila con un error que se puede reintentar (sin espacio, error de lectura, acceso perdido), **When** el usuario pulsa Reintentar, **Then** la importación de ese archivo se repite. Si la app ya no puede leer el original, lo explica y propone volver a seleccionarlo.
5. **Given** una fila en la cola, **When** el usuario pulsa Quitar de la cola, **Then**:
   - el documento desaparece de la cola;
   - se borra su copia privada y su registro;
   - si se estaba procesando, su procesamiento se detiene.

---

### User Story 3 - Extraer y trocear en segundo plano (Priority: P1)

Con documentos listos en la cola, el usuario pulsa «Iniciar indexación». La app encola el trabajo en segundo plano y abre la pantalla de estado de indexación. Para cada documento, el trabajo:
- extrae el texto conservando páginas y encabezados;
- lo trocea con el algoritmo y los parámetros decididos en F05;
- deja el documento preparado para que F07 calcule los embeddings.

**Why this priority**: es la mitad de la ingesta que no depende de los modelos. Su calidad (párrafos, encabezados, páginas, fragmentos) limita la de todo lo demás.

**Independent Test**:
- Pulsar «Iniciar indexación» con un PDF de varias páginas, un TXT y un MD en la cola. Los tres pasan por Extrayendo y Fragmentando y quedan preparados, cada uno con su número de fragmentos.
- Los fragmentos de un caso de referencia coinciden al carácter con los del troceado del escritorio.

**Acceptance Scenarios**:

1. **Given** documentos «Listo para procesar», **When** el usuario pulsa «Iniciar indexación», **Then**:
   - se encola un único trabajo en segundo plano;
   - la app navega a `index`;
   - al volver a Importar documentos, cada fila muestra Extrayendo, Fragmentando o su resultado.
2. **Given** un PDF de texto con varias páginas, **When** se extrae, **Then**:
   - cada bloque lleva su número de página;
   - las líneas se unen en párrafos;
   - se quitan los números de página sueltos;
   - se unen las palabras partidas con guion a final de línea.
3. **Given** un Markdown con encabezados `#`, **When** se extrae, **Then** cada bloque lleva la ruta de encabezados vigente, p. ej. «Instalación › Requisitos».
4. **Given** un PDF en el que algunas páginas no tienen texto extraíble, **When** se extrae, **Then**:
   - esas páginas se omiten;
   - el resultado del documento indica cuántas eran.
5. **Given** un PDF en el que ninguna página tiene texto, **When** se extrae, **Then** el documento termina en error «Sin texto extraíble (¿documento escaneado?)» sin afectar a los demás.
6. **Given** el troceado de un documento, **When** termina, **Then**:
   - los fragmentos tienen `ordinal` consecutivo desde 0;
   - un encabezado nuevo siempre abre fragmento;
   - ningún párrafo supera el máximo, salvo una frase sin puntuación más larga que él. Un fragmento puede superarlo solo por el solape arrastrado (el último párrafo corto del anterior), como en el algoritmo del informe y en el golden de F05. Los excesos en el embedding los cubre D-036;
   - se repite el último párrafo del fragmento anterior cuando es corto (solape).

---

### User Story 4 - La cola sobrevive a todo (Priority: P2)

La cola y el procesamiento sobreviven a:
- girar el teléfono;
- salir de la app;
- que el sistema mate el proceso.

Al volver, el usuario ve el mismo estado, y el trabajo continúa o se completa sin duplicar nada.

**Why this priority**: las importaciones de PDF grandes tardan, y el usuario no debe tener que vigilarlas. Es un criterio explícito del mockup y del roadmap.

**Independent Test**: importar varios archivos y matar el proceso a mitad de la copia y, en otra prueba, a mitad del troceado. Al reabrir, la cola muestra el estado correcto y cada documento termina una sola vez.

**Acceptance Scenarios**:

1. **Given** una cola con documentos en distintos estados, **When** se recrea la Activity (giro, tema, idioma), **Then** la cola se muestra igual y no se repite ninguna importación.
2. **Given** el proceso muere a mitad de la copia de un archivo, **When** se reabre la app, **Then** no queda ninguna copia a medias: el archivo aparece con un error que se puede reintentar o no aparece, y nunca como «Listo» con una copia incompleta.
3. **Given** el proceso muere durante el procesamiento, **When** el sistema reanuda el trabajo, **Then** el documento interrumpido se vuelve a procesar desde el principio, con el mismo resultado, y los ya preparados no se repiten.

---

### User Story 5 - Pantalla fiel al mockup y accesible (Priority: P2)

La pantalla Importar documentos reproduce el PNG 05 con los ajustes aprobados, en claro y en oscuro, en español y en inglés. Todos sus estados se pueden usar con TalkBack y con letra grande.

**Why this priority**: constitución VII. Es la primera pantalla de producto del flujo de documentos.

**Independent Test**: comparar capturas de cada estado (vacío, importando, con errores, procesando) con el PNG y pasar las comprobaciones automáticas de accesibilidad.

**Acceptance Scenarios**:

1. **Given** la cola vacía, **When** se abre la pantalla, **Then**:
   - se ven la top bar «Importar documentos» con Atrás;
   - el eyebrow «INGESTA LOCAL», el título «Amplía tu biblioteca» y el subtítulo «Selecciona archivos de tu dispositivo.»;
   - la tarjeta de selección con «Selecciona archivos», «Todo se procesa en tu móvil» y el botón «Seleccionar archivos»;
   - la línea «Formatos: PDF · TXT · Markdown»;
   - un estado vacío de la cola;
   - «Iniciar indexación» deshabilitado.
2. **Given** filas en la cola, **When** el usuario pulsa el chevron de una fila, **Then** se abre su detalle (G15), con el estado, el error si lo hay y las acciones que admite.
3. **Given** TalkBack activo, **When** el usuario recorre la cola, **Then** cada fila se anuncia con nombre, tamaño y estado, y cada acción tiene su etiqueta.

---

### Edge Cases

- **Formatos:**
  - un archivo con extensión `.pdf` que no es un PDF se trata como dañado;
  - un `.md` que el selector entrega con un tipo genérico se reconoce por su extensión;
  - un `.txt` vacío, o con solo espacios, termina en «Sin texto».
- **Selección:**
  - el mismo archivo dos veces en la misma selección: el segundo es duplicado;
  - dos archivos distintos con el mismo nombre: los dos se importan, porque la identidad es el contenido y no el nombre.
- **Codificación:** un TXT o MD que no es UTF-8 válido se lee con la codificación de respaldo que fije el plan y nunca muestra caracteres de sustitución en masa. Un BOM UTF-8 se ignora.
- **Tamaño:** un PDF muy grande se procesa página a página, sin cargar el documento entero en memoria.
- **Protección:** un PDF con restricciones de permisos (sin contraseña de apertura) se importa con normalidad; uno con contraseña de apertura es «Archivo cifrado».
- **Dispositivo sin extracción de PDF:** en Android 12–14 sin la actualización del sistema necesaria, cada PDF elegido termina en «Este dispositivo no puede leer PDF» y los TXT y MD de la misma selección se importan con normalidad.
- **Límites exactos:** un archivo de exactamente 100 MB o un PDF de exactamente 2.000 páginas se aceptan; uno más, no.
- **Índice incompatible:** si el índice local está bloqueado por incompatible (F04), la importación no registra nada y la pantalla explica que la biblioteca no está disponible.
- **Ritmo de uso:**
  - el usuario puede seguir importando mientras se procesa;
  - los nuevos documentos esperan «Listo para procesar» hasta la siguiente pulsación de «Iniciar indexación».
- **Extracción irrecuperable:** un documento cuya extracción falla (dañado a mitad, error interno) termina en error con Reintentar y no detiene el resto de la cola.

## Requirements *(mandatory)*

### Functional Requirements

**Selección e importación**

- **FR-001**: «Seleccionar archivos» MUST abrir el selector de documentos del sistema con selección múltiple y filtro de PDF, TXT y Markdown. La app MUST NOT pedir permisos de almacenamiento amplios ni usar la red (constitución I).
- **FR-002**: Por cada archivo elegido, la app MUST:
  - copiarlo a su almacenamiento privado excluido de copias de seguridad (G22);
  - calcular su SHA-256 durante la copia;
  - validarlo;
  - registrarlo como documento pendiente (`pending`) en el índice local. Se guardan el nombre visible, la huella, el tipo, el tamaño, el número de páginas si es PDF y la ruta de la copia.

  La copia se escribe primero en un fichero temporal y solo se da por buena al terminar, de modo que nunca quede una copia incompleta registrada.
- **FR-003**: La validación MUST detectar, cada uno con su error:
  - formato no admitido;
  - archivo dañado o ilegible;
  - PDF con contraseña de apertura («cifrado»);
  - archivo por encima del límite de tamaño o de páginas;
  - espacio libre insuficiente;
  - acceso perdido al original durante la copia;
  - duplicado;
  - PDF en un dispositivo cuyo sistema no ofrece extracción de texto de PDF.

  Un error MUST afectar solo a su archivo. Un PDF con contraseña de apertura MUST rechazarse como «Archivo cifrado», sin pedir ni guardar contraseñas.
- **FR-004**: Un archivo cuyo SHA-256 ya existe en el índice (en la cola o en la biblioteca), o que se repite en la misma selección, MUST mostrarse como duplicado («Ya está en tu biblioteca»), sin crear documento ni copia.
- **FR-005**: Los límites son 100 MB por archivo y 2.000 páginas por PDF, constantes y registrados en DECISIONS. Un archivo por encima de ellos MUST terminar en «Demasiado grande». Si el tamaño se conoce de antemano, MUST rechazarse antes de copiarlo.

**Cola de importación**

- **FR-006**: La cola MUST mostrar todos los documentos aún no indexados, también los de sesiones anteriores, hasta que se indexan (pasan a la Biblioteca, F07/F08) o el usuario los quita. Para cada uno MUST mostrar:
  - nombre;
  - tamaño;
  - estado: Importando, Listo para procesar, En cola, Extrayendo, Fragmentando, Preparado para indexar (con su número de fragmentos), o Error con su motivo.

  El estado MUST salir del estado persistido y del progreso real del trabajo, nunca de valores inventados (constitución V). Un documento con el troceado terminado se muestra como «Preparado para indexar» hasta que F07 lo indexa.
- **FR-007**: La cabecera de la cola MUST mostrar el número real de documentos que contiene («N archivos»).
- **FR-008**: Los documentos de la cola MUST persistir en el índice local y reconstruirse desde él al recrear la Activity, al reabrir la app y tras la muerte del proceso. Los archivos que aún se están copiando, o que fallaron antes de registrarse, MUST sobrevivir a la recreación de la Activity. Tras la muerte del proceso pueden desaparecer, sin dejar copias a medias (US4-2, D-038).
- **FR-009**: Cada fila MUST permitir, según su estado:
  - Reintentar, en los errores recuperables;
  - Quitar de la cola, en cualquier estado no indexado.

  Quitar un documento MUST pedir confirmación (constitución V), borrar el registro y la copia privada, y detener su procesamiento si estaba en curso. Quitar un archivo que no llegó a registrarse no pide confirmación.
- **FR-010**: El chevron de cada fila MUST abrir su detalle (G15), con el estado, el motivo del error y las acciones disponibles.

**Procesamiento en segundo plano**

- **FR-011**: «Iniciar indexación» MUST estar habilitado solo si hay al menos un documento «Listo para procesar». Al pulsarlo MUST:
  - encolar un único trabajo en segundo plano, que no se duplica aunque se pulse varias veces;
  - navegar a la pantalla de estado de indexación (`index`), que hasta F07 es un placeholder.
- **FR-012**: El trabajo MUST procesar los documentos pendientes de uno en uno, en orden de importación. Por cada documento:
  1. extrae el texto en bloques (ruta de encabezados, texto, página);
  2. lo trocea;
  3. guarda el número de fragmentos y deja el documento en `parsed`.

  MUST ejecutarse fuera del hilo principal y sobrevivir a que la app pase a segundo plano.
- **FR-013**: El trabajo MUST ser reanudable e idempotente: tras una interrupción, un documento a medias se vuelve a procesar desde el principio con el mismo resultado, y los ya preparados no se repiten.
- **FR-014**: Un fallo en un documento MUST dejar ese documento en error con su motivo y continuar con el siguiente.

**Extracción**

- **FR-015**: PDF:
  - se extrae página a página, sin cargar el documento entero en memoria;
  - cada bloque lleva su número de página (desde 1);
  - las líneas de una página se unen en párrafos con la misma regla que el extractor del escritorio de F05 (`pdf_paragraphs`): línea en blanco, viñeta o fin de frase seguido de mayúscula abren párrafo; se unen los guiones de fin de línea y se quitan los números de página sueltos.
- **FR-016**: El texto de los PDF MUST extraerse con la capacidad de PDF del propio sistema, sin bibliotecas de PDF añadidas al APK. En un dispositivo cuyo sistema no la ofrece, los PDF MUST rechazarse con el error «Este dispositivo no puede leer PDF», con la indicación de actualizar el sistema, y TXT y Markdown MUST funcionar igual.
- **FR-017**: Las páginas de un PDF sin texto extraíble MUST omitirse y contarse. Un PDF sin ninguna página con texto MUST terminar en error «Sin texto extraíble». El reconocimiento óptico de caracteres queda fuera del MVP.
- **FR-018**: Markdown: los encabezados `#` a `######` MUST formar la ruta de encabezados, que se reinicia por nivel. El cuerpo de cada sección MUST conservar los párrafos separados por líneas en blanco. Una sección muy larga puede entregarse en varios bloques consecutivos con la misma ruta, siempre que el troceado resultante sea idéntico al de un único bloque.
- **FR-019**: TXT MUST producir bloques sin ruta, con los párrafos separados por líneas en blanco: uno solo, como el extractor del escritorio, o varios consecutivos si el texto es muy largo, con un troceado idéntico al de un único bloque.
- **FR-020**: La extracción de TXT y Markdown, y la unión de líneas en párrafos de PDF, MUST producir lo mismo que el extractor del escritorio de F05 (`offline/eval/extract.py`). Lo garantiza un golden compartido generado desde Python. Así, la evaluación de F05 representa lo que hace la app.

**Troceado**

- **FR-021**: El troceado MUST implementar el `Chunker` del informe con los parámetros de `RagParameters`: objetivo de 1.594 caracteres, máximo de 2.113 y longitudes medidas como en el escritorio. MUST reproducir al carácter los 27 casos y 162 fragmentos de `offline/golden/chunker_golden.jsonl`.
- **FR-022**: El troceado MUST cumplir las reglas del informe:
  - párrafos enteros; solo se parte por frases un párrafo que supera el máximo. Un fragmento puede superar el máximo por el solape arrastrado (ver US3-6);
  - un cambio de encabezado abre fragmento;
  - el último párrafo se repite en el fragmento siguiente si es corto (solape);
  - cada fragmento lleva su página de inicio y su ruta;
  - el `ordinal` es consecutivo desde 0.

**Pantalla**

- **FR-023**: La pantalla MUST reproducir el PNG 05 con los ajustes aprobados:
  - G14: «Selecciona archivos» en lugar de «Arrastra o selecciona archivos»;
  - G15: detalle de la fila;
  - estado vacío de la cola y estados de error, que el PNG no maqueta;
  - textos en `strings.xml` en español y en inglés (D-029);
  - claro y oscuro.
- **FR-024**: Accesibilidad (constitución VII):
  - áreas táctiles de al menos 48 dp;
  - etiquetas para TalkBack en cada fila, con nombre, tamaño y estado, y en cada acción;
  - contraste AA;
  - la pantalla sigue siendo usable con el tamaño de letra al 200 %.
- **FR-025**: Hasta que exista Inicio (F09) o Biblioteca (F08), MUST haber un acceso provisional a Importar documentos desde la pantalla provisional de Inicio, identificado como tal y que se retira cuando llegue el definitivo.

### Key Entities *(include if feature involves data)*

- **Documento importado:** el registro de `documents` de F04, con:
  - nombre visible, huella SHA-256 (única), tipo, tamaño, páginas y ruta de la copia privada;
  - estado: `pending`, `parsed` o `error`; F07 añade `embedding`, `done`, `paused` y `cancelled`;
  - número de fragmentos y motivo del error.
- **Elemento de la cola:** la vista de un documento no indexado. Añade a su estado persistido el progreso del trabajo (Extrayendo o Fragmentando) y, mientras se importa, el progreso de la copia.
- **Bloque extraído** (`RawBlock`): ruta de encabezados, texto y página (o ninguna).
- **Fragmento** (`Chunk`): ordinal, ruta de encabezados unida con « › », contenido y página de inicio. En F06 solo se cuentan y se validan; F07 los vuelve a generar y los guarda con su embedding.
- **Trabajo de procesamiento:** único y persistente; procesa los documentos pendientes en orden.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El troceado de la app reproduce el 100 % del golden de F05: los 27 casos y 162 fragmentos, con el mismo contenido, ruta, página y ordinal al carácter.
- **SC-002**: La extracción de TXT y Markdown y la unión de párrafos de PDF coinciden al 100 % con el golden compartido con el extractor del escritorio.
- **SC-003**: Con un juego de archivos de prueba (PDF de texto multipágina, PDF escaneado, PDF cifrado, archivo dañado, otro formato, archivo por encima del límite, duplicado, TXT y MD con encabezados):
  - el 100 % termina en su estado correcto;
  - ningún error impide que los archivos válidos de la misma selección queden listos.
- **SC-004**: En el teléfono de referencia, un PDF de texto de 10 MB aparece «Listo para procesar» en menos de 3 s desde que el usuario vuelve del selector.
- **SC-005**: En el teléfono de referencia, un PDF de texto de 200 páginas se extrae y se trocea en menos de 30 s, sin que la memoria del proceso crezca más de 100 MB.
- **SC-006**: Tras matar el proceso a mitad de la importación y a mitad del procesamiento, el 100 % de los documentos acaba en un estado coherente:
  - ninguna copia incompleta registrada;
  - ningún documento procesado dos veces con resultados distintos;
  - ningún duplicado.
- **SC-007**: La comprobación automática de red no encuentra ninguna conexión nueva, y el manifiesto no declara permisos de almacenamiento amplios.
- **SC-008**: Las comprobaciones automáticas de accesibilidad pasan en todos los estados de la pantalla, en claro y en oscuro. Las capturas de los estados coinciden con el PNG 05 salvo en los ajustes aprobados, que se listan en el informe de cierre.
- **SC-009**: Ningún trabajo de importación ni de extracción se ejecuta en el hilo principal: el modo estricto de depuración no registra violaciones nuevas en la pantalla ni en el trabajo.

## Assumptions

- **Extractor de PDF (G38), decidido en clarify:** la API de PDF del sistema, mantenida con el sistema y sin peso en el APK. El teléfono de referencia tiene Android 15. En Android 12–14 hace falta la extensión S ≥ 13, que llega por las actualizaciones del sistema de Google Play; el plan verifica cómo se comprueba en tiempo de ejecución.
- **Límites y contraseñas, decididos en clarify:** 100 MB por archivo y 2.000 páginas por PDF. Un PDF con contraseña de apertura se rechaza como «Archivo cifrado», como dice el `.md` de la pantalla, sin UI nueva.
- **Codificación:** UTF-8, ignorando el BOM. Si no es UTF-8 válido, se lee con una codificación de respaldo para Europa occidental (Windows-1252), que se fija en el plan.
- **Validación de texto en la importación:** al importar, un PDF se abre para comprobar que es legible, que no pide contraseña y cuántas páginas tiene. La ausencia total de texto se confirma al extraerlo, en el trabajo, y deja el documento en error. No se extrae dos veces el documento entero.
- **Detalle de la fila (G15):** la propuesta del INVENTARIO es una hoja inferior con el estado, el error y las acciones (Quitar o Reintentar).
- **Modelos:** el trabajo de F06 no los necesita. F07 comprobará que el modelo de embeddings está listo antes de calcular embeddings.
- **Fragmentos:** el trabajo no guarda los fragmentos; solo su número. F07 vuelve a trocear al indexar, como prevé el informe al reanudar, porque el troceado es determinista y rápido frente a los embeddings.
- **Tablas y columnas:** la extracción de PDF no reconstruye tablas ni detecta columnas. La diferencia con pypdf, el extractor de la evaluación, se mide en el informe de cierre sobre los PDF del corpus de F05.
