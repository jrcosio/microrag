# Especificación de feature: Núcleo de datos RAG (F04)

**Feature Branch**: `004-rag-data-core`

**Created**: 2026-10-26

**Status**: Draft

**Input**: Descripción del usuario: «F04 — Núcleo de datos RAG. Feature técnica sin UI de producto: construir la capa de datos local del RAG sobre la que trabajarán la ingesta, la recuperación y el chat, y garantizar que el móvil y el pipeline offline de escritorio producen exactamente los mismos textos normalizados y vectores compatibles.» Ver `docs/roadmap/FEATURES.md` (F04).

**Fuentes**:
- `docs/RAG_Movil/informe-rag-movil-v2.md`:
  - Esquema SQLite;
  - Capa de datos en Kotlin;
  - Búsqueda léxica en español;
  - Motor vectorial;
  - Pipeline offline (Python);
  - Test de compatibilidad offline ↔ móvil;
  - Puntos a verificar al empezar.
- Constitución v1.1.0, principios II, III (no negociable), IV, V y VI.
- DECISIONS D-002 (ingesta en el dispositivo; Python solo evalúa), D-011, D-012 (`litert-lm-api`), D-024 (consulta en CPU), D-025 (EmbeddingGemma 2 270M) y D-028.
- INVENTARIO G27 (esquema frente a UI) y G28 (exclusión en la búsqueda vectorial, que se resuelve en F10).
- Lo ya construido:
  - F02: prefijos de embeddings, truncado a 256D con L2 y motor de embeddings sobre EmbeddingGemma 2;
  - F03: carpetas privadas (`index/`) y modelos verificados.

**Verificado al redactar** (2026-10-26):
- SQLite-Vector 1.1.2: Apache-2.0. El AAR solo contiene la biblioteca nativa, para ARM64, ARMv7 y x86_64, con mínimo Android 8 (API 26).
- SQLite bundled 2.7.1: estable.
- `litert-lm-api` 0.18.1: publica ruedas para macOS ARM64, Linux y Windows.

## User Scenarios & Testing *(mandatory)*

Los actores son las features que vienen después y quien desarrolla. La ingesta (F06/F07), la recuperación (F10) y el chat (F11) usan esta capa sin conocer su implementación. Quien desarrolla mantiene la compatibilidad entre el móvil y el escritorio.

### User Story 1 - Índice local fiable (Priority: P1)

La app crea o abre su índice local en el almacenamiento privado, con el esquema completo. Permite:
- registrar documentos;
- guardar sus fragmentos con texto, texto normalizado y vector;
- borrar un documento con todo lo suyo.

Repetir una escritura no duplica nada, y una escritura interrumpida no deja datos a medias.

**Why this priority**: es la base de la ingesta y de la búsqueda. Sin ella no hay RAG. La constitución III fija que SQLite es la fuente de verdad.

**Independent Test**:
1. En el emulador y en el teléfono, crear el índice, registrar un documento y guardar un lote de fragmentos.
2. Repetir el mismo lote: no hay duplicados.
3. Borrar el documento: no quedan fragmentos ni entradas en el índice léxico.

**Acceptance Scenarios**:

1. **Given** una instalación sin índice, **When** la app lo abre por primera vez, **Then** se crean todas las tablas, el índice léxico y sus disparadores, y se escriben los metadatos del espacio vectorial.
2. **Given** un documento registrado, **When** se guarda un lote de fragmentos, **Then** todos quedan guardados en una única transacción y aparecen en el índice léxico.
3. **Given** el mismo lote guardado otra vez (reintento tras una interrupción), **When** se guarda, **Then** no hay duplicados y el número de fragmentos no cambia.
4. **Given** un documento con fragmentos, **When** se borra, **Then** desaparecen sus fragmentos y sus entradas del índice léxico.
5. **Given** un lote que falla a mitad, por ejemplo con un vector de dimensión incorrecta, **When** se guarda, **Then** no queda guardado ningún fragmento de ese lote.
6. **Given** un índice ya existente, **When** la app se reinicia, **Then** abre el mismo índice con sus datos y vuelve a activar la búsqueda vectorial en la conexión.

---

### User Story 2 - Búsquedas básicas sobre el índice (Priority: P1)

Con fragmentos guardados, la capa de datos permite:
- encontrar los *k* más parecidos a un vector de consulta (búsqueda exacta);
- encontrar los *k* mejores por texto, dando más peso al título y a la ruta de encabezados que al cuerpo;
- cargar fragmentos por identificador y el fragmento vecino de uno dado.

**Why this priority**: son los bloques con los que F10 construirá la búsqueda híbrida, y F11 las citas y los vecinos.

**Independent Test**:
1. Guardar fragmentos con vectores y textos conocidos.
2. Comprobar que la búsqueda vectorial devuelve primero el fragmento con el vector idéntico al de la consulta.
3. Comprobar que la búsqueda léxica devuelve primero el fragmento cuyo título contiene el término.
4. Comprobar que la carga por identificadores y la del vecino devuelven los datos exactos.

**Acceptance Scenarios**:

1. **Given** fragmentos con vectores conocidos, **When** se busca con el vector de uno de ellos, **Then** ese fragmento aparece el primero y el resultado tiene como mucho *k* elementos, ordenados por similitud.
2. **Given** un término que aparece en el título de un fragmento y en el cuerpo de otro, **When** se busca por texto, **Then** el del título queda por delante.
3. **Given** una consulta en la que todos los términos son palabras vacías («de la que»), **When** se busca por texto, **Then** el resultado está vacío y no hay error.
4. **Given** una consulta con comillas, guiones o códigos («R-1234 "manual"»), **When** se busca por texto, **Then** la consulta se escapa correctamente: no hay error de sintaxis y el código se encuentra intacto.
5. **Given** identificadores de fragmentos, **When** se cargan, **Then** se obtienen el texto original (sin la cabecera contextual), la ruta de encabezados, la página, la estimación de tokens y el nombre del documento.
6. **Given** el fragmento de ordinal *n* de un documento, **When** se pide su vecino *n + 1*, **Then** se obtiene ese fragmento, o nada si no existe.

---

### User Story 3 - El mismo normalizador en el móvil y en el escritorio (Priority: P1)

El texto que se indexa y el que se busca pasan por el mismo normalizador español:
- minúsculas, sin tildes y sin palabras vacías;
- un stemmer ligero de plural y género;
- los tokens con dígitos quedan intactos.

El normalizador existe dos veces, en la app y en el pipeline offline, y ambas versiones producen exactamente la misma salida para un conjunto de referencia de al menos 300 casos.

**Why this priority**: si los dos lados normalizan distinto, la búsqueda léxica falla en silencio (constitución III y VI).

**Independent Test**:
1. Ejecutar el conjunto de referencia en la app, en la JVM y en el dispositivo, y en el pipeline offline.
2. Los tres coinciden al 100 %.
3. Los ejemplos del informe dan el resultado documentado.

**Acceptance Scenarios**:

1. **Given** los ejemplos del informe, **When** se normalizan, **Then**:
   - «clientes» → «client»;
   - «cliente» → «client»;
   - «motores» → «motor»;
   - «luces» → «luz»;
   - «R-1234» → «r-1234», intacto.
2. **Given** el conjunto de referencia (≥ 300 pares entrada → salida), **When** lo procesan la app (JVM y dispositivo) y el pipeline offline, **Then** las tres salidas coinciden exactamente en todos los pares.
3. **Given** un cambio en el normalizador, **When** se hace sin subir su versión, **Then** las pruebas de referencia fallan.

---

### User Story 4 - Vectores compatibles entre el escritorio y el móvil (Priority: P1)

Un conjunto de 50 textos de referencia, de consulta y de documento (con prefijos y cabecera contextual), se embebe en el escritorio con el mismo fichero de modelo y se guarda como referencia. En el teléfono, la app los vuelve a embeber y cada par debe parecerse con un coseno ≥ 0,99.

**Why this priority**: es la garantía de que F05 evalúa en el escritorio lo mismo que el móvil hará. La constitución VI lo exige en cada teléfono de prueba y en cada cambio del motor de inferencia.

**Independent Test**:
1. Generar la referencia en el escritorio.
2. Ejecutar la prueba instrumentada en el teléfono de referencia.
3. Los 50 pares superan 0,99.

**Acceptance Scenarios**:

1. **Given** la referencia generada en el escritorio, **When** el teléfono embebe los mismos textos con el mismo modelo, prefijos, cabecera y truncado, **Then** cada par tiene un coseno ≥ 0,99 y se informan el mínimo y la media.
2. **Given** que algún par queda por debajo de 0,99, **When** termina la prueba, **Then** falla e indica qué textos han fallado.
3. **Given** un modelo de embeddings distinto del verificado, **When** se ejecuta la prueba, **Then** no se compara: la prueba indica que el modelo no coincide.

---

### User Story 5 - Protección del espacio vectorial (Priority: P2)

Cada índice guarda con qué se construyó:
- huella del modelo de embeddings;
- dimensiones;
- prefijos;
- versión del normalizador;
- versión del esquema;
- estado de la cuantización.

Al abrirlo, la app compara esos datos con su propia configuración. Si no coinciden, no busca: expone que el índice es incompatible, para que otras features ofrezcan reconstruirlo.

**Why this priority**: evita resultados basura sin aviso tras actualizar el modelo o el normalizador (constitución III). No bloquea las historias P1, que trabajan con un índice recién creado.

**Independent Test**:
1. Crear un índice.
2. Alterar uno de sus metadatos (modelo, dimensiones, prefijo o versión del normalizador).
3. Abrirlo: las búsquedas quedan bloqueadas, y el motivo es consultable.

**Acceptance Scenarios**:

1. **Given** un índice creado con la configuración actual, **When** se abre, **Then** se marca como compatible y las búsquedas funcionan.
2. **Given** un índice con otra huella de modelo, otras dimensiones, otro prefijo u otra versión del normalizador, **When** se abre, **Then** se marca como incompatible, con el campo que no coincide, y toda búsqueda se rechaza sin consultar la base.
3. **Given** un índice con una versión de esquema anterior y una migración definida, **When** se abre, **Then** se migra antes de usarlo. Si es posterior a la que entiende la app, o no hay migración, se marca como incompatible sin tocar los datos.
4. **Given** un índice incompatible, **When** otra feature lo consulta, **Then** puede saber el motivo, pero nunca obtiene resultados de búsqueda.

---

### Edge Cases

- **Fichero de índice corrupto o que no es una base de datos:** la apertura falla con un error claro y la app no se cierra. Qué se hace después (recrear el índice) lo decide la feature que lo usa (F07/F12).
- **Extensión vectorial que no carga** (biblioteca ausente o arquitectura no soportada): la apertura falla con un motivo explícito. No se cae a una búsqueda sin vectores en silencio.
- **Vector con una dimensión distinta de 256 al guardar:** se rechaza el lote entero.
- **Texto con caracteres fuera del latín**, emojis o combinaciones Unicode raras: el normalizador no falla y el resultado es el mismo en los dos lados (el conjunto de referencia incluye casos así).
- **Consulta léxica muy larga:** se limita a 16 términos distintos, como en el informe.
- **Muerte del proceso a mitad de una transacción:** al reabrir, el lote no aparece y la base sigue íntegra.
- **Disco lleno al escribir:** la transacción se deshace y el error llega a quien escribe. No queda nada a medias.
- **Borrado de un documento en el que se está buscando:** las escrituras van en un único escritor serializado y una búsqueda nunca ve un documento a medio borrar.
- **Índice vacío:** las búsquedas devuelven listas vacías sin error.

## Requirements *(mandatory)*

### Functional Requirements

**Esquema y almacenamiento**

- **FR-001**: Existe un único esquema compartido por la app y el pipeline offline, según el informe:
  - metadatos (`rag_meta`);
  - documentos;
  - fragmentos con su vector, únicos por (documento, ordinal);
  - índice léxico sobre título normalizado y cuerpo normalizado, sincronizado por disparadores al insertar, borrar y modificar;
  - borrado en cascada de documento a fragmentos.
- **FR-002**: Los documentos MUST incluir además (G27): si están incluidos en las búsquedas (sí por defecto), tamaño en bytes, número de páginas (si se conoce), ruta de la copia privada, y los estados pausado y cancelado junto a los del informe (pendiente, procesado, embebiendo, terminado y error). El origen se mantiene y vale siempre «usuario» (D-002).
- **FR-003**: El índice MUST vivir en la carpeta privada `index/` de la app, excluida de copias de seguridad (constitución I). Su fichero no se expone a otras apps.
- **FR-004**: Cada apertura de la base MUST activar la búsqueda vectorial sobre la columna de vectores (256 dimensiones, coseno, vectores normalizados) y configurar el registro por adelanto y las claves foráneas. Un fallo al activar la búsqueda vectorial MUST impedir usar el índice y explicar el motivo.
- **FR-005**: Todo acceso a la base MUST ocurrir fuera del hilo principal, en la cola dedicada de base de datos, con un único escritor (constitución IV). En depuración, el modo estricto MUST detectar cualquier acceso a disco desde el hilo principal.

**Escritura**

- **FR-006**: El registro, la actualización de estado y el borrado de documentos MUST estar disponibles. El borrado elimina en cascada los fragmentos y sus entradas léxicas.
- **FR-007**: Los fragmentos MUST guardarse por lotes en una transacción: o se guarda el lote entero o nada. Repetir un fragmento ya guardado (mismo documento y ordinal) no lo duplica ni lo modifica.
- **FR-008**: Un lote con algún vector de dimensión distinta de 256 MUST rechazarse entero.
- **FR-009**: Cada lote guardado MUST marcar la cuantización como desactualizada (`quant_dirty = 1`) en los metadatos (informe).

**Lectura y búsqueda**

- **FR-010**: La búsqueda vectorial exacta MUST devolver como mucho *k* identificadores de fragmentos ordenados por similitud de coseno (por defecto *k* = 50).
- **FR-011**: La búsqueda léxica MUST:
  - recibir una consulta ya normalizada con el normalizador;
  - devolver como mucho *k* identificadores (por defecto 50);
  - ordenarlos por relevancia, con peso 3 para el título y la ruta de encabezados y 1 para el cuerpo;
  - devolver una lista vacía si la consulta no tiene términos.
- **FR-012**: La carga por identificadores MUST devolver, de cada fragmento: identificador, documento, ordinal, ruta de encabezados, página, texto original, estimación de tokens y nombre del documento. La búsqueda del vecino por (documento, ordinal) devuelve el fragmento o nada.
- **FR-013**: Las búsquedas sobre un índice vacío MUST devolver listas vacías sin error.

**Metadatos y compatibilidad**

- **FR-014**: Al crear el índice MUST escribirse en `rag_meta`:
  - `schema_version`;
  - `embedding_model_sha256` (la huella del EmbeddingGemma 2 270M del catálogo, D-025);
  - `embedding_dims` = 256;
  - `embedding_doc_prefix` y `embedding_query_prefix` (los de la app, F02);
  - `normalizer_version` = 1;
  - `quant_dirty`.
- **FR-015**: Al abrir el índice MUST compararse esos metadatos con la configuración de la app. Si difieren el modelo, las dimensiones, un prefijo o la versión del normalizador, el índice queda «incompatible», con el motivo, y toda búsqueda MUST rechazarse sin consultar la base.
- **FR-016**: La versión del esquema MUST tener una estrategia de migración documentada:
  - se aplican en orden las migraciones definidas desde la versión del fichero hasta la actual, dentro de una transacción;
  - con una versión mayor que la de la app, o sin migración definida, el índice queda incompatible y no se modifica.

**Normalizador**

- **FR-017**: El normalizador español v1 MUST seguir exactamente el algoritmo del informe:
  - minúsculas y descomposición Unicode sin marcas diacríticas;
  - tokens alfanuméricos unidos por `- _ . /`;
  - fuera las palabras vacías de la lista del informe y los tokens de un carácter;
  - stemmer ligero solo en tokens de 5 o más letras y sin dígitos.
- **FR-018**: La consulta léxica MUST construirse con los tokens normalizados distintos (como mucho 16), cada uno entre comillas con las comillas internas escapadas, unidos con OR.
- **FR-019**: El pipeline offline MUST tener un port exacto del normalizador. El conjunto de referencia (≥ 300 pares) MUST cubrir:
  - los ejemplos del informe;
  - plural y género;
  - tildes y diéresis;
  - palabras vacías;
  - códigos con dígitos y separadores;
  - mayúsculas;
  - puntuación;
  - Unicode no latino y emojis;
  - textos de párrafo completos.

**Compatibilidad de embeddings**

- **FR-020**: El pipeline offline MUST generar una referencia de 50 textos (consultas y fragmentos de documento con cabecera contextual) embebidos con el mismo fichero de modelo, los mismos prefijos, la misma cabecera y el mismo truncado a 256 con L2 que la app. MUST registrar la huella del modelo usado.
- **FR-021**: Una prueba instrumentada MUST volver a embeber esos textos en el dispositivo (en CPU, D-024) y exigir un coseno ≥ 0,99 por par. MUST informar del mínimo, la media y los textos que fallen.
- **FR-022**: El entorno del pipeline offline MUST ser reproducible: versiones fijadas e instrucciones para regenerar las dos referencias.

### Key Entities *(include if feature involves data)*

- **Metadatos del índice** (`rag_meta`): pares clave-valor que describen el espacio vectorial y el esquema (FR-014).
- **Documento**:
  - origen, siempre «usuario»;
  - nombre visible, tipo, huella del contenido (única);
  - estado (pendiente, procesado, embebiendo, terminado, error, pausado o cancelado);
  - número de fragmentos y error;
  - inclusión en búsquedas, tamaño, páginas y ruta de la copia privada;
  - fechas de creación y actualización.
- **Fragmento**:
  - documento y ordinal (únicos juntos);
  - ruta de encabezados y página;
  - texto original, título normalizado (nombre del documento + ruta) y cuerpo normalizado;
  - estimación de tokens;
  - vector de 256 dimensiones normalizado.
- **Índice léxico**: espejo del título y el cuerpo normalizados de cada fragmento, mantenido por disparadores.
- **Estado de compatibilidad del índice**: compatible, o incompatible con su motivo (campo y valores esperado y encontrado).
- **Referencias de compatibilidad**: pares entrada → salida del normalizador, y textos → vectores de 256 dimensiones con la huella del modelo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los pares del conjunto de referencia del normalizador (≥ 300) coinciden exactamente en la app (JVM y teléfono de referencia) y en el pipeline offline.
- **SC-002**: Los 50 pares de embeddings superan un coseno de 0,99 en el teléfono de referencia. Se informan el mínimo y la media.
- **SC-003**: La búsqueda vectorial funciona en el teléfono de referencia (ARM64) y en el emulador: el fragmento con el vector de la consulta sale el primero en el 100 % de los casos de prueba.
- **SC-004**: Repetir cualquier lote ya guardado deja el número de fragmentos y de entradas léxicas exactamente igual.
- **SC-005**: Tras borrar un documento quedan 0 fragmentos y 0 entradas léxicas suyos.
- **SC-006**: Cualquier discrepancia de modelo, dimensiones, prefijo o versión del normalizador bloquea el 100 % de las búsquedas.
- **SC-007**: En depuración no hay accesos a la base desde el hilo principal (0 violaciones del modo estricto en las pruebas instrumentadas).
- **SC-008**: Con 10.000 fragmentos sintéticos en el teléfono de referencia se miden y registran el p50 y el p95 de la búsqueda vectorial exacta y de la léxica. Es una medida de referencia para F10 y F14, no un umbral de esta feature.

## Assumptions

- **Valores por defecto pendientes de confirmar en clarify:**
  - Ampliación del esquema G27: se aplica tal como la propone el inventario (FR-002).
  - Cuantización INT8 y precarga: se aplazan a F14, donde se decide si hacen falta. En F04 se guarda la marca `quant_dirty`, pero no se cuantiza.
  - Diagnóstico de la base en depuración: no hay pantalla. Las pruebas y los registros cubren la verificación.
- **Versión del esquema:** la primera de la app es la 1. El informe numera su esquema como 2 por su propia historia; aquí la numeración empieza de nuevo.
- **Referencias en el repositorio:** se generan una vez en el escritorio y se guardan en el repositorio. Solo las usan las pruebas y no viajan en el APK de producto.
- **Textos de las referencias:** sintéticos y en español, redactados para el proyecto, más fragmentos del informe. No se usan documentos del usuario; el corpus de evaluación es de F05 (G37).
- **Pipeline offline:** en esta feature solo hace lo necesario para las dos referencias. El índice de evaluación completo, el conjunto de preguntas y las métricas son de F05. No se distribuye ningún índice (D-002).
- **Exclusión de documentos en la búsqueda vectorial (G28):** F04 solo guarda la marca de inclusión; F10 decide cómo filtrar.
- **Lecturas concurrentes:** se usa una conexión para todo, en la cola de base de datos. Una conexión de solo lectura adicional queda para F10 o F14 si las mediciones la justifican.
